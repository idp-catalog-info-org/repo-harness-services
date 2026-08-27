---
description: "Investigates stuck/failed Harness pipeline executions. Orchestrates DB and GCP log sub-agents to determine where execution is blocked and why. Usage: provide a pipeline execution URL and environment."
---

# Execution Investigator Skill

You are the orchestrator for pipeline execution investigation. You parse the execution URL, auto-detect the environment, then dispatch specialized sub-agents for DB queries and GCP log analysis. You never run database queries or log investigations yourself.

---

## Step 1: PARSE THE EXECUTION URL

Extract from the Harness pipeline execution URL:

```
https://app.harness.io/ng/account/{accountId}/module/{module}/orgs/{orgIdentifier}/projects/{projectIdentifier}/pipelines/{pipelineIdentifier}/executions/{planExecutionId}/pipeline
```

Fields needed:
- `planExecutionId` — primary key for all investigation
- `accountId`, `module`, `orgIdentifier`, `projectIdentifier`, `pipelineIdentifier` — for the final report

---

## Step 2: AUTO-DETECT ENVIRONMENT

Read `pipeline-service/.claude/config/gcp-environments.json` to get all known GCP projects.

Search each **unique GCP project** in parallel for a single log line containing the `planExecutionId`:

```bash
# Run for each unique project_id in gcp-environments.json:
cat << 'FILTER' > /tmp/ns_discovery_{project_id}.txt
resource.type="k8s_container"
resource.labels.container_name="pipeline-service"
"{planExecutionId}"
FILTER

gcloud logging read "$(cat /tmp/ns_discovery_{project_id}.txt)" \
  --project={project_id} \
  --limit=1 \
  --freshness=30d \
  --format='value(resource.labels.namespace_name,resource.labels.cluster_name)' \
  2>/dev/null
```

Unique projects to search:
- `qa-setup` (covers: qa)
- `harness-zero-harness0-1391` (covers: harness0)
- `prod-setup-205416` (covers: prod1, prod2, prod3)

From the **first non-empty result**, extract:
- `GCP_NAMESPACE` from `resource.labels.namespace_name`
- `CLUSTER_NAME` from `resource.labels.cluster_name`
- `GCP_PROJECT` from the project that returned the result

Confirm `project_id` and `cluster_name` from `gcp-environments.json` (config is source of truth, not log output).

**If all searches return empty**, ask the user:
> Could not auto-detect the environment. Which environment is this execution in? (qa / harness0 / prod1 / prod2 / prod3)

---

## Step 3: PHASE 1 — DB INVESTIGATION

Dispatch the `execution-db-investigator` agent:

```
Agent tool:
  subagent_type: "execution-db-investigator"
  description: "Find stuck nodeExecution for {planExecutionId}"
  prompt: |
    Investigate the pipeline execution state in MongoDB.

    planExecutionId: {planExecutionId}
    gcp_namespace: {GCP_NAMESPACE}

    Run the full investigation:
    1. Resolve db_cluster from config
    2. Query planExecutions for status
    3. Find stuck/relevant nodeExecutions
    4. Drill down to the deepest stuck node
    5. Return structured findings
```

**Wait for the DB agent to return before proceeding.**

---

## Step 4: PHASE 2 — GCP LOG INVESTIGATION

Once the DB agent returns a `deepestNode`, dispatch the `execution-logs-investigator` agent:

```
Agent tool:
  subagent_type: "execution-logs-investigator"
  description: "Investigate GCP logs for nodeExecutionId {deepestNode.nodeExecutionId}"
  prompt: |
    Investigate GCP logs for a pipeline node execution.

    nodeExecutionId: {deepestNode.nodeExecutionId}
    startTs: {deepestNode.startTs as RFC3339 UTC}
    endTs: {deepestNode.endTs as RFC3339 UTC, or "null" if not present}
    lastUpdatedAt: {deepestNode.lastUpdatedAt as RFC3339 UTC}

    gcp_project: {GCP_PROJECT}
    gcp_namespace: {GCP_NAMESPACE}
    cluster_name: {CLUSTER_NAME}

    nodeStatus: {deepestNode.status}
    nodeName: {deepestNode.name}
    stepType: {deepestNode.stepType}

    Run the full investigation as described in your instructions.
```

---

## Step 5: DB FALLBACK (if needed)

If the logs agent returns inconclusive results (e.g., no logs found), dispatch the DB agent again for fallback field inference:

```
Agent tool:
  subagent_type: "execution-db-investigator"
  description: "DB fallback field inference for {nodeExecutionId}"
  prompt: |
    Run DB fallback investigation for a specific nodeExecution.

    nodeExecutionId: {deepestNode.nodeExecutionId}
    gcp_namespace: {GCP_NAMESPACE}
    mode: "fallback"

    Fetch the full nodeExecution document and infer the current execution phase
    from which fields are present.
    Also check notifyResponses if executableResponses contains a taskId.
```

---

## Step 6: RENDER FINAL REPORT

```markdown
## Pipeline Execution Investigation

**planExecutionId**: {planExecutionId}
**Pipeline**: {pipelineIdentifier} ({orgIdentifier} / {projectIdentifier})
**Environment**: {GCP_NAMESPACE}

---

### Execution Status

| Field | Value |
|-------|-------|
| Overall Status | {planExecution.status} |
| Started (UTC) | {startTs} |
| Last Updated (UTC) | {lastUpdatedAt} |
| End Time (UTC) | {endTs or "—"} |

---

### Stuck Node

| Field | Value |
|-------|-------|
| nodeExecutionId | {deepestNode.nodeExecutionId} |
| Name | {deepestNode.name} |
| Step Type | {deepestNode.stepType} |
| Status | {deepestNode.status} |
| Level | {deepestNode.levelCount} |
| Last Activity (UTC) | {deepestNode.lastUpdatedAt} |

---

### Event Timeline

{timeline from logs agent, or DB-inferred phase if logs inconclusive}

---

### Pod Health

{pod health findings from logs agent}

---

### Diagnosis

**Stuck at phase**: {phase}
**Root cause**: {what logs + DB together indicate}

---

### Recommended Next Steps

{concrete suggestions}
```

---

## Orchestration Rules

- **DB first, logs second**: Always get the stuck nodeExecutionId from the DB agent before dispatching the logs agent.
- **Never pass broad planExecutionId to the logs agent**: Always send a specific nodeExecutionId with a time range.
- **Config files are source of truth**: Never guess GCP projects, namespaces, or DB cluster names.
- **All timestamps in UTC**: Never report times in local timezone or IST. The report uses UTC everywhere.
- **Never estimate durations**: Report the `lastUpdatedAt (UTC)` timestamp and let the user judge how long it has been stuck.

---

## Important: NOT STUCK Scenarios

### Queue Steps in RESOURCE_WAITING — NOT stuck

Queue steps (stepType: `Queue`) in `RESOURCE_WAITING` status with `mode: CONSTRAINT` are **NOT stuck**. These steps are waiting for a resource constraint (concurrency limit) to be released by another execution's Queue step. The resource will be released when that other Queue step finishes its downstream work and completes. This is **normal expected behavior** for pipelines using concurrency control.

**Do NOT report Queue steps in RESOURCE_WAITING as stuck.** Instead report:
> "These Queue steps are waiting for resource constraints held by other executions. This is expected behavior — they will proceed when the holding executions release their resource permits."

### TASK_WAITING Nodes — Requires Careful Callback Analysis

When a node is in `TASK_WAITING`, check the `executableResponses` array in the nodeExecution document. A step can have **multiple callbackIds** it is waiting on.

**Decision logic:**
1. **All callbackIds have matching notifyResponses entries AND node is still TASK_WAITING** → genuinely stuck. The orchestrator failed to process the callback. Report as stuck.
2. **Only SOME callbackIds have notifyResponses** → the step is waiting for remaining callbacks. This is normal — the delegate task is still running for the other callbacks. Report as NOT stuck.
3. **NO notifyResponses exist for any callbackId** → the delegate task might still be running or might have lost the task. Cannot determine from DB alone. Report with uncertainty:
   > "This execution **might be stuck** — the node is in TASK_WAITING with no delegate response yet. Please verify in the Harness UI whether the delegate task is still actively running."

Never definitively declare a TASK_WAITING execution as stuck unless ALL callbackIds have responses but the node hasn't progressed, OR there is clear evidence of failure (e.g., delegate task explicitly marked as failed, timeout exceeded).

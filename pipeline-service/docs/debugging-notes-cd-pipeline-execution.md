# Debugging Notes: CD Pipeline Execution

## Log Search Patterns

### Useful Keywords for Execution Investigation

| What You're Looking For | Search Pattern |
|---|---|
| Execution status changes | `[PMS_GRAPH] Updating Plan Execution with uuid` |
| Stage failure | `STAGE_FAILED` |
| Pipeline failure | `PIPELINE_FAILED` |
| Execution lifecycle end | `Ending Execution` |
| Notification events | `Sending notification for PipelineIdentifier` |
| Delegate task results | `HANDLE_STEP_RESPONSE` |
| Adviser responses | `HANDLE_ADVISER_RESPONSE` |
| Plan creation | `QUEUED_PLAN_CREATION`, `STARTING_PLAN_CREATION` |
| Node execution start | `pipeline_node_start` |
| Resource constraints | `active resource restraint instances` |

### GCP Log Query Templates

**Find all services involved in an execution:**
```
resource.labels.namespace_name="{namespace}" AND "{executionId}"
```

**Find errors for an execution:**
```
resource.labels.namespace_name="{namespace}" AND severity="ERROR" AND "{executionId}"
```

**Find status transitions:**
```
resource.labels.namespace_name="{namespace}" AND "{executionId}" AND "PMS_GRAPH"
```

**Get unique service counts (pipe gcloud output to):**
```bash
| sort | uniq -c | sort -rn
```

### Rate Limiting

GCP Logging API has a quota of **60 read requests per minute per project**. When spawning parallel log search agents, be aware that concurrent queries can exhaust this quota quickly. Space out queries or reduce parallelism if you hit `RESOURCE_EXHAUSTED` errors.

---

## Known Non-Critical Warnings

### `postExecutionRollbackInfos Disparity`

```
postExecutionRollbackInfos Disparity detected between previous planExecution
and planExecutionMetadata for planExecutionId : null
```

- **Severity**: WARNING (non-critical)
- **Services**: Appears in both `pipeline-service` and `orchestration-engine`
- **Frequency**: Can appear 30+ times during a single execution's completion phase
- **Impact**: None — does not affect execution outcome or status transitions
- **Context**: Occurs during post-execution processing when rollback info metadata is being compared
- **Action**: Safe to ignore during debugging. Do not treat as root cause.

### Known Non-Fatal Errors/Noise

#### `replace_configs.sh: export: not a valid identifier`

- **Severity**: NOISE — appears on every pod startup
- **Impact**: None — the export warning does not prevent the service from starting or running
- **Action**: Safe to ignore. Do not treat as root cause for pod restarts or CrashLoopBackOff.

#### High memory utilization (>80%) on pipeline-service pods

- **Severity**: NOISE — expected behavior
- **Impact**: None — pipeline-service starts with `-Xmx${MEMORY} -Xms${MEMORY}` where `MEMORY=4096`, meaning the JVM pre-allocates the full heap at startup. This causes `container_memory_working_set_bytes` to consistently report >80% of the container memory limit.
- **Action**: Safe to ignore. Do not treat as memory pressure or an OOM risk.

---

## Known Pod Restart Causes

### JFR Race Condition (`$POD_NAME` not resolved at JVM launch)

- **Severity**: FATAL — causes JVM to abort with `Error occurred during initialization of VM`
- **Error pattern**:
  ```
  jdk.jfr.internal.dcmd.DCmdException: Could not use /opt/harness/POD_NAME as repository.
  Error occurred during initialization of VM
  ```
- **Root cause**: `replace_configs.sh` substitutes `$POD_NAME` into `JAVA_OPTS` (e.g. `-XX:FlightRecorderOptions=repository=/opt/harness/$POD_NAME`), but on slow pod starts the env var is not yet set when the JVM is launched — leaving the literal string `POD_NAME` in the path.
- **Trigger conditions**: (a) image pull delay (~17s+) during deployment rollout, (b) rapid restarts after a node containerd restart, (c) any startup delay that lets the JVM launch before the Kubernetes downward API injects `$POD_NAME`.
- **Distinguishing feature**: Termination reason in Prometheus is `Error` (not `OOMKilled`). The error appears in ALL failed startup attempts but NOT in successful ones where the image was pre-cached.
- **Self-healing**: Pods typically stabilize on the 3rd restart attempt once the env var race resolves.
- **Fix**: Ensure `$POD_NAME` is resolved (non-empty) before passing JFR flags to the JVM in `replace_configs.sh`. Use a static fallback path or verify the variable before launch.
- **Tracking**: PIPE-32126 (closed without fix). Recurred on 2026-03-10 (node containerd restart) and 2026-03-11 (deployment rollout).
- **Action**: Do NOT treat as OOM or liveness probe failure. Check for this pattern first in any POD_RESTART alert where termination reason is `Error`.

### Redis Subscription Lock Timeout (slow startup → liveness probe kill)

- **Severity**: FATAL (indirectly) — causes startup to exceed liveness probe window
- **Error pattern**:
  ```
  RedisTimeoutException: Unable to acquire subscription lock after 7500ms.
  Try to increase 'subscriptionTimeout', 'subscriptionsPerConnection',
  'subscriptionConnectionPoolSize' parameters.
  ```
- **Root cause**: During deployment rollouts, multiple pods competing for Redis subscription locks can cause lock contention. The 7500ms timeout stalls the initialization pipeline, preventing the app from becoming ready before K8s kills it via liveness probe.
- **Trigger conditions**: (a) deployment rollouts where multiple pods start simultaneously, (b) Redis under load or slow to respond, (c) `subscriptionConnectionPoolSize` too small for the number of concurrent subscribers.
- **Distinguishing feature**: Pod logs show successful JVM startup (JFR recording starts, "Starting Pipeline Service Application" appears) but then "Shutdown hook, entering maintenance..." before the app fully initializes. The `RedisTimeoutException` appears between app startup and the shutdown hook. Termination reason in Prometheus is `Error` (K8s SIGTERM, not OOMKilled).
- **Difference from JFR race condition**: JFR race condition causes JVM abort *before* the application starts. Redis timeout causes the application to stall *during* initialization (after JVM starts successfully). Both show termination reason `Error`.
- **Self-healing**: Second startup attempt usually succeeds because (a) Redis lock contention has cleared, (b) OS page cache is warm, reducing startup time.
- **Fix**: Increase `subscriptionTimeout` (currently 7500ms → consider 15-30s), review `subscriptionsPerConnection` and `subscriptionConnectionPoolSize`. Also increase liveness probe `initialDelaySeconds` as defense-in-depth.
- **First observed**: 2026-04-08 (prod3, deployment rollout).
- **Investigation tip**: When a POD_RESTART alert shows liveness probe timeout (shutdown hook before app ready), ALWAYS search for dependency timeout errors (Redis, Kafka, MongoDB) between "Starting Pipeline Service Application" and "Shutdown hook" — the slow dependency is the root cause, not the probe timeout itself.

---

## Debugging Tips

### Identifying Involved Services

Rather than relying on a hardcoded list of services, use the broad GCP query to discover which services logged for a given execution:

```bash
gcloud logging read 'resource.labels.namespace_name="{namespace}" AND "{executionId}"' \
  --project={project} --limit=200 --format="value(resource.labels.container_name)" \
  --freshness=7d | sort | uniq -c | sort -rn
```

This gives you the actual services involved and their relative log volume, which naturally prioritizes where to look.

### Key Node Execution Types

When tracing the execution tree in logs, look for these step types:

- `PIPELINE_SECTION` — top-level pipeline node
- `STAGES_STEP` — parallel/serial stage container
- `DEPLOYMENT_STAGE_STEP` — individual CD stage
- `NG_SPEC_STEP` — stage specification
- `NG_SECTION_WITH_ROLLBACK_INFO` — execution section with rollback

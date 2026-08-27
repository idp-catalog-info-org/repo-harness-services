---
description: Universal debugging skill for production issues AND development bugs. Iterative, agent-driven investigation with docs, logs, DB queries, and code analysis.
disable-model-invocation: false
---

# Debugger Skill

Debug any issue — production or development. The main agent orchestrates; all heavy I/O (logs, DB queries, code exploration) is delegated to sub-agents.

## Step 1: GATHER CONTEXT FROM USER

Extract from the user:

- **What's the issue?** (stuck, failing, slow, error, unexpected behavior)
- **Entity identifier** (execution ID, deployment ID, release ID, build ID, etc.)
- **Environment** (QA, prod, staging, etc.)
- **Any error messages** they already have

### Determine Investigation Depth

| User Asks | Depth |
|-----------|-------|
| "Is this stuck?" / "What's the status?" | **Status Check** — quick, minimal investigation |
| "Why did this fail?" / "What went wrong?" | **Root Cause Analysis** — full investigation |
| "Fix this bug" / "This keeps happening" | **Root Cause Analysis** — investigate first, then hand off to `/fix-bug` |

---

## Step 2: LOAD DOMAIN KNOWLEDGE

Load docs and environment config **before** spawning investigation agents.

### 2a: Read CLAUDE.md (main thread)

Read only `CLAUDE.md` or `AGENTS.md` in the repo root and service directory. These are compact indexes — they tell you what docs exist, where key code lives, and how things connect. **Do NOT read detailed docs (e.g., `docs/*.md`) in the main thread** — they are too large and will cause context rot.

### 2b: Extract Debugging Context (Explore agent)

Spawn an Explore agent to read the detailed docs and return only what's relevant for the current issue:

```
Task tool call:
  subagent_type: "Explore"
  description: "Extract debugging context for {issue type}"
  prompt: |
    Read the docs/ folder in the service directory. Focus on docs relevant to: {issue description}.

    Extract and return ONLY:
    - Status/state definitions and valid transitions relevant to the issue
    - Common failure modes and their symptoms
    - Key log patterns to search for (exact strings)
    - Key classes and methods involved in this flow
    - Any documented debugging tips for this type of issue

    Keep your response under 2KB. Do not return narrative explanations or full flow descriptions.
```

This keeps the main context lean — you get a small, targeted summary instead of hundreds of lines of flow documentation.

### 2c: Resolve Environment

**ALWAYS** read `.claude/config/gcp-environments.json` to get:
- `project_id` — for GCP log queries
- `namespace` — for log filtering
- `cluster_name` — for kubectl if needed

Never guess environments. The config file is the source of truth.

**Note**: Steps 2b and 2c can run in parallel — the Explore agent and the environment config read are independent.

---

## Step 3: INVESTIGATE (Iterative, Agent-Driven)

**CRITICAL: The main agent orchestrates. All heavy I/O goes to sub-agents.**

Never run `gcloud logging read` or database queries directly in the main agent. Always spawn sub-agents via the Task tool. This keeps the main context clean and enables parallelism.

### Round 1: Parallel First Pass

Spawn **all independent investigations in parallel** (single message, multiple Task tool calls):

#### Agent A: GCP Log Search (Errors)
```
Task tool call:
  subagent_type: "data-investigator"
  description: "Search error logs for {identifier}"
  prompt: |
    ## Hypothesis
    {what you think went wrong based on docs}

    ## Environment
    - project_id: {X}
    - namespace: {Y}

    ## Search Instructions
    1. Search GCP logs: severity="ERROR" AND "{identifier}"
    2. If no errors found, broaden to: severity>="WARNING" AND "{identifier}"

    ## Return Format
    - Error messages with timestamps
    - Affected components/services
    - Stack traces (truncated to key frames)
```

#### Agent B: GCP Log Search (Timeline)
```
Task tool call:
  subagent_type: "data-investigator"
  description: "Build event timeline for {identifier}"
  prompt: |
    ## Hypothesis
    Execution stopped progressing at some state transition.

    ## Environment
    - project_id: {X}
    - namespace: {Y}

    ## Search Instructions
    1. Search GCP logs: "{identifier}" (all severities, ordered asc)
    2. Build chronological timeline of all events

    ## Return Format
    - Ordered timeline of events with timestamps
    - Last known status/state
    - Which component was active last
    - Any gaps in the timeline
```

#### Agent C: Database State Check
```
Task tool call:
  subagent_type: "data-investigator"
  description: "Query DB for entity state of {identifier}"
  prompt: |
    ## Task
    Query the database for the current state of entity {identifier}.

    ## Instructions
    1. First, invoke the Skill tool with skill="harness-db-agent:query" to load database query capabilities.
    2. Then use codepulse commands to run these queries:
       - Find the execution/entity document and its current status
       - Find all child/node records and their statuses
       - Identify any records stuck in non-terminal states

    ## Environment
    Database cluster: {cluster alias based on environment, e.g., prod1-cdng}

    ## Return Format
    - Current entity status and key timestamps (createdAt, startTs, endTs, lastUpdatedAt)
    - List of child/node records with their statuses
    - Any records stuck in non-terminal states and how long they've been stuck
```

### Round 1 Recovery: No Logs Found

If Agents A and B return no relevant logs:

1. **Get timestamps from DB** — Agent C's results should include `startTs`, `lastUpdatedAt`, and other timestamps. Use these to narrow the log search time window.

2. **Discover relevant log patterns from code** — Spawn an Explore agent:
```
Task tool call:
  subagent_type: "Explore"
  description: "Find log patterns for {component}"
  prompt: |
    Search the codebase for log statements (log.info, log.error, log.warn) in the code
    that handles {component/feature}. Look for:
    - Log messages emitted during the relevant execution flow
    - Error handling blocks and their log messages
    - State transition log messages

    Return: A list of exact log message strings/patterns that can be searched in GCP logs.
```

3. **Retry log search** with discovered patterns and DB-derived time window:
```
Task tool call:
  subagent_type: "data-investigator"
  description: "Targeted log search with discovered patterns"
  prompt: |
    ## Search Instructions
    Search GCP logs using these specific patterns discovered from code:
    - Pattern 1: "{exact log string from code}"
    - Pattern 2: "{exact log string from code}"

    ## Time Window (from DB timestamps)
    - Start: {startTs from DB minus 5 minutes}
    - End: {lastUpdatedAt from DB plus 5 minutes}

    ## Environment
    - project_id: {X}
    - namespace: {Y}
```

4. **If still no logs found** — Ask the user for guidance. Present what you know from DB state and code analysis, and ask if they have additional context (different environment, different identifier, related services).

### Round 2: Targeted Follow-Up (If Round 1 Insufficient)

Analyze Round 1 results. If root cause is still unclear, spawn more targeted agents:

#### Option A: Code Exploration
If you need to understand specific behavior or find additional log patterns:
```
Task tool call:
  subagent_type: "Explore"
  description: "Explore {component} behavior"
  prompt: |
    Find log messages, error handling, and state transitions in {component}.
    Specifically look for:
    - How {specific state/error from Round 1} is reached
    - What conditions cause {observed behavior}

    Return: exact log patterns to search for, possible failure modes, relevant code paths.
```
Then spawn new data-investigator agents with the discovered log patterns.

#### Option B: Cross-Service Investigation
If the issue crosses service boundaries:
```
Task tool call:
  subagent_type: "data-investigator"
  description: "Cross-service log search for {related service}"
  prompt: |
    ## Hypothesis
    The failure in {primary service} was caused by {related service}.

    ## Environment
    - project_id: {X}
    - namespace: {Y}

    ## Search Instructions
    Search logs for {related service} using correlated identifiers:
    - Primary ID: {identifier}
    - Related IDs: {any IDs discovered in Round 1}

    ## Return Format
    - Events in the related service that correlate with the primary failure
    - Any errors or timeouts in the related service
```

#### Option C: Deeper DB Queries
If Round 1 DB results revealed specific stuck nodes or missing data:
```
Task tool call:
  subagent_type: "data-investigator"
  description: "Deep DB query for {specific entity}"
  prompt: |
    ## Task
    Run targeted database queries based on Round 1 findings.
    Invoke the Skill tool with skill="harness-db-agent:query" first.

    ## Queries
    - {specific query based on Round 1 findings}
    - {e.g., find related records for stuck node X}
    - {e.g., check outcome/sweeping output documents}

    ## Environment
    Database cluster: {cluster alias}

    ## Return Format
    - Query results summarized (key fields only)
    - Any inconsistencies or missing records found
```

### Round 3: Final Attempt

This is the **last investigation round**. Make it count — spawn the most targeted agents based on everything learned so far.

**After Round 3, stop investigating and report what you have:**
- If root cause is identified → report with full evidence
- If root cause is partially identified → report findings, state what's unclear, and recommend next steps for the user
- If no root cause found → report all evidence gathered, DB state, and suggest manual investigation areas

---

## Step 4: REPORT FINDINGS

### For Status Checks
```
**Status**: {FAILED/RUNNING/STUCK/etc.}
**When**: {timestamp}
**What happened**: {1-2 sentence summary}
**Failing component**: {step/stage/service that failed}
**Error**: {the actual error message}
```

### For Root Cause Analysis
```
## Root Cause

**Issue**: {brief summary}
**Root Cause**: {what failed and why}

**Timeline**:
1. {timestamp} - {event}
2. {timestamp} - {event}
3. {timestamp} - {failure}

**Evidence**:
- DB shows: {entity state from database}
- Logs show: {what happened from logs}
- Docs say: {expected behavior}

**Recommended Actions**:
- Immediate: {quick fix}
- Long-term: {prevention}
```

---

## Step 5: DOCUMENT FINDINGS

At the end of every session, document genuinely new/useful information in `docs/debugging-notes-{topic}.md`:

- New failure modes discovered
- Useful log patterns and DB queries for debugging
- State transitions or flows not previously documented

Only document information that would save time in future investigations.

---

## Fixing Bugs

This skill **does not fix bugs**. It investigates and reports root causes.

If the user wants to fix the bug after investigation, tell them to run `/fix-bug`. The fix-bug skill will use the RCA findings from this session to drive the fix.

---

## Agents Reference

| Agent | When to Spawn | What It Does |
|-------|--------------|--------------|
| `Explore` | Step 2b (domain knowledge) + Round 1 Recovery + Round 2 | Reads docs, searches codebase. Returns condensed summaries, log patterns, code paths |
| `data-investigator` | ALL log searches and DB queries | Runs gcloud, DB queries via harness-db-agent. Returns summarized findings |

**Main agent does:**
- Read docs/config (fast, small output)
- Orchestrate agents (spawn, analyze results, spawn more)
- Report findings to user
- Write documentation

**Main agent does NOT do:**
- Run gcloud commands
- Run database queries
- Read large log output
- Explore code beyond 2-3 quick file reads
- Fix bugs (hand off to `/fix-bug`)

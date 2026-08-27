---
description: Fix bugs based on RCA findings from the analyzer skill. Delegates to bug-fix-proposer for analysis, evaluation, and implementation.
disable-model-invocation: false
---

# Fix Bug Skill

Fix bugs after root cause has been identified. Takes RCA findings (from `/analyzer` or user-provided context) and orchestrates the fix through sub-agents. The main thread never writes code — it only orchestrates.

## Step 1: UNDERSTAND THE ROOT CAUSE

### If coming from /analyzer
The analyzer skill should have produced an RCA report with:
- Root cause description
- Timeline of events
- Evidence (DB state, logs, code paths)
- Recommended actions

Review the RCA and confirm you understand the root cause before proceeding.

### If user provides context directly
Extract from the user:
- **What's the bug?** (expected vs actual behavior)
- **Root cause** (if known — otherwise run `/analyzer` first)
- **Affected code** (files, classes, methods)
- **Severity** (blocking, degraded, cosmetic)

If the root cause is unclear, tell the user to run `/analyzer` first.

---

## Step 2: SPAWN BUG-FIX-PROPOSER

Always delegate to the `bug-fix-proposer` agent. It will:
- Analyze the root cause
- Propose fix approaches
- Determine whether the `evaluator` agent is needed

```
Task tool call:
  subagent_type: "bug-fix-proposer"
  description: "Propose fixes for {bug summary}"
  prompt: |
    ## Root Cause Analysis
    {paste the RCA findings — root cause, evidence, affected components, timeline}

    ## Constraints
    - {any constraints from the user: backwards compatibility, performance, urgency, etc.}

    ## Instructions
    Analyze the root cause and propose fix approaches.
    Include your complexity verdict: does this need the evaluator agent, or can we go straight to implementation?
```

---

## Step 3: FOLLOW THE PROPOSER'S VERDICT

The `bug-fix-proposer` returns a proposal with a `Complexity Verdict`:

### If verdict is "SKIP_EVALUATOR"
The fix is straightforward — one clear approach. Spawn the implementer directly:

```
Task tool call:
  subagent_type: "implementer"
  description: "Implement fix for {bug summary}"
  prompt: |
    ## Selected Approach
    {paste the recommended approach from bug-fix-proposer}

    ## Root Cause
    {brief root cause summary}

    ## Instructions
    1. Implement the fix
    2. Run relevant tests
    3. Add a regression test if one doesn't exist
    4. Commit changes with descriptive message

    ## Constraints
    - Minimal changes — fix the bug, don't refactor
    - All existing tests must pass
    - Follow existing code patterns
```

### If verdict is "NEEDS_EVALUATOR"
Multiple viable approaches exist. Spawn the evaluator first:

```
Task tool call:
  subagent_type: "evaluator"
  description: "Evaluate fix proposals for {bug summary}"
  prompt: |
    ## Bug Summary
    {brief description of the bug and root cause}

    ## Proposals
    {paste the proposals from bug-fix-proposer}

    ## Evaluation Criteria
    - Correctness: Does it fully fix the root cause?
    - Risk: What could go wrong?
    - Complexity: How much code changes?
    - Maintainability: Does it make the code better or worse?

    ## Instructions
    Select the optimal approach. If confidence is LOW, flag for human review.
```

**If evaluator confidence is LOW** — Present all proposals to the user and ask them to choose.

Then spawn the implementer with the selected approach (same format as SKIP_EVALUATOR above).

---

## Step 4: VERIFY

After the implementer completes:

1. **Review what was done** — Read the implementer's summary
2. **Report to user** — Summarize what was fixed, what tests were added, and what to watch for

### Report Format
```
## Fix Summary

**Bug**: {brief description}
**Root Cause**: {what was wrong}
**Fix**: {what was changed and why}

**Files Changed**:
- {file1}: {what changed}
- {file2}: {what changed}

**Tests**:
- Existing tests: {PASS/FAIL}
- New regression test: {added/not needed}

**Risk Assessment**: {low/medium/high — what to watch for}
```

---

## Agents Reference

| Agent | Role | Always Spawned? |
|-------|------|-----------------|
| `bug-fix-proposer` | Analyzes RCA, proposes fixes, decides if evaluator is needed | Yes — always first |
| `evaluator` | Selects optimal fix when multiple viable approaches exist | Only if proposer says so |
| `implementer` | Implements the selected fix, runs tests, commits | Yes — always last |

**Main agent does:**
- Gather RCA context
- Orchestrate agents in sequence
- Follow the proposer's complexity verdict
- Report results to user

**Main agent does NOT do:**
- Write or modify code
- Decide fix complexity (that's the proposer's job)
- Select between approaches (that's the evaluator's job)

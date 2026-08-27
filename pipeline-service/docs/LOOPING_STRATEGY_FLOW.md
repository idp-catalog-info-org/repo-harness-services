# Looping Strategy Flow

Complete guide to looping strategies (Matrix, Repeat, Parallelism) - from YAML config through plan creation to runtime execution.

---

## Quick Overview

```
YAML strategy config → StrategyConfigPlanCreator → Strategy PlanNode (CHILDREN facilitator)
    ↓
StrategyStep.obtainChildrenAfterRbac() → Generate children → SpawnChildrenRequestProcessor
    ↓
Create all child NodeExecutions → Start initial batch (up to maxConcurrency)
    ↓
Child completes → MaxConcurrentChildCallback → Start next child → Repeat until done
    ↓
All children complete → EngineResumeCallback → StrategyStep.handleChildrenResponseInternal()
    → Aggregate statuses → Return final StepResponse
```

---

## Strategy Types

| Type | YAML Key | Children Generated | Metadata Per Child |
|------|----------|-------------------|--------------------|
| **Matrix** | `matrix` | Cartesian product of axes (minus excludes) | `matrixValues: {service: "svc1", env: "dev"}` |
| **Repeat** | `repeat.times` | N identical children | `currentIteration`, `totalIterations` |
| **Repeat Items** | `repeat.items` | One per item | `ForMetadata.value` = current item |
| **Repeat Partitioned** | `repeat.items` + `partitionSize` | One per batch | `ForMetadata.partition` = item batch |
| **Parallelism** | `parallelism` | N identical children | `currentIteration`, `totalIterations` |

### YAML Examples

```yaml
# Matrix: 3 services x 2 envs = 6 combinations (minus excludes)
strategy:
  matrix:
    service: [svc1, svc2, svc3]
    env: [dev, prod]
    exclude:
      - service: svc1
        env: prod
    maxConcurrency: 2

# Repeat over items
strategy:
  repeat:
    items: [item1, item2, item3]
    maxConcurrency: 2

# Repeat N times
strategy:
  repeat:
    times: 5

# Parallelism: run same step N times
strategy:
  parallelism: 3
```

---

## Phase 1: Plan Creation

**Entry:** `StrategyConfigPlanCreator.createPlanForParentNode()` (Line 65)

**File:** `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/plancreator/strategy/config/StrategyConfigPlanCreator.java`

```
YAML Pipeline → PlanCreator Framework → StrategyConfigPlanCreator
    ↓
1. Extract StrategyMetadata from parent (childNodeId, strategyNodeId)  [Lines 67-75]
    ↓
2. Extract maxConcurrency from MatrixConfig or HarnessForConfig       [Lines 76-83]
    ↓
3. Determine StrategyType (MATRIX / LOOP / PARALLELISM)               [Lines 84-90]
    ↓
4. Build StrategyStepParameters                                        [Lines 92-98]
   (childNodeId, strategyConfig, maxConcurrency, strategyType, shouldProceedIfFailed)
    ↓
5. Create PlanNode with:                                               [Lines 106-128]
   - stepType: StrategyStep.STEP_TYPE
   - facilitatorType: CHILDREN (OrchestrationFacilitatorType)
   - group: StepOutcomeGroup.STRATEGY
```

**Result:** A strategy wrapper PlanNode that uses the CHILDREN facilitator. When executed, the orchestration engine calls `StrategyStep.obtainChildrenAfterRbac()`.

### Key Data Structures

| Class | Location | Fields |
|-------|----------|--------|
| `StrategyConfig` | `953-yaml-commons/.../strategy/StrategyConfig.java` | `matrixConfig`, `repeat`, `parallelism`, `when`, `onFailure` |
| `MatrixConfig` | `953-yaml-commons/.../strategy/MatrixConfig.java` | `axes`, `expressionAxes`, `exclude`, `maxConcurrency`, `nodeName` |
| `HarnessForConfig` | `953-yaml-commons/.../strategy/HarnessForConfig.java` | `times`, `items`, `partitionSize`, `start`, `end`, `unit`, `maxConcurrency` |
| `StrategyStepParameters` | `pms-sdk-core/.../steps/matrix/StrategyStepParameters.java` | `childNodeId`, `strategyConfig`, `maxConcurrency`, `strategyType`, `shouldProceedIfFailed` |

---

## Phase 2: Spawning Children

### Step 2A: Generate Children (StrategyStep)

**Entry:** `StrategyStep.obtainChildrenAfterRbac()` (Line 52)

**File:** `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/steps/matrix/StrategyStep.java`

Delegates to strategy-specific services based on type:

| Strategy Type | Service | File | What It Does |
|--------------|---------|------|--------------|
| Matrix | `MatrixConfigService` → `MatrixConfigServiceHelper` | `pms-sdk-core/.../steps/matrix/MatrixConfigServiceHelper.java` | Recursive Cartesian product of axes, applies exclude filters (Line 376) |
| Repeat | `ForLoopStrategyConfigService` | `pms-sdk-core/.../steps/matrix/ForLoopStrategyConfigService.java` | Creates children by times (Line 47), items (Line 81), or partitions (Line 61) |
| Parallelism | `ParallelismStrategyConfigService` | `pms-sdk-core/.../steps/matrix/ParallelismStrategyConfigService.java` | Creates N identical children (Line 32) |

Each service returns a list of `Child` objects, each containing:
- `childNodeId` - the plan node to execute
- `StrategyMetadata` - iteration index, total count, matrix values or repeat item

### Step 2B: Process SpawnChildrenRequest

**Entry:** `SpawnChildrenRequestProcessor.handleEvent()` (Line 95)

**File:** `pipeline-service/modules/orchestration/src/main/java/io/harness/event/handlers/SpawnChildrenRequestProcessor.java`

```
1. Generate UUID for each child                                        [Lines 104-106]
   → These become NodeExecution._id in MongoDB
    ↓
2. Apply concurrency limits via getMaxConcurrencyLimit()               [Line 107, 233-241]
   → actual = min(user maxConcurrency, account-level limit)
    ↓
3. Expand barriers within strategy node                                [Lines 108-110]
   → Duplicate barriers for each child combination
    ↓
4. Filter children (for POST_EXECUTION_ROLLBACK cases)                 [Line 112, 251-292]
    ↓
5. Save ConcurrentChildInstance to nodeExecutionInfo collection         [Lines 122-124]
   → childrenNodeExecutionIds, cursor = maxConcurrency
    ↓
6. Create ALL child NodeExecutions (status = NOT_STARTED)              [Lines 166-200]
   → Start first N children (N = maxConcurrency)
    ↓
7. Register MaxConcurrentChildCallback per child                       [Lines 181-193]
   → Triggered on child completion to start next queued child
    ↓
8. Register EngineResumeCallback for parent                            [Lines 214-217]
   → Waits for ALL children, then resumes strategy node
    ↓
9. Update parent node with ExecutableResponse                          [Lines 220-225]
```

---

## Phase 3: Runtime Execution

### Concurrency Control

**File:** `pipeline-service/modules/orchestration/src/main/java/io/harness/concurrency/MaxConcurrentChildCallback.java`

Uses cursor-based execution tracked in the `nodeExecutionInfo` MongoDB collection:

```
Children: [child1, child2, child3, child4, child5], maxConcurrency: 2

Time 0: Start child1, child2 (cursor = 2)
Time 1: child1 completes → callback → incrementCursor → cursor=3 → start child3
Time 2: child2 completes → callback → incrementCursor → cursor=4 → start child4
Time 3: child3 completes → callback → incrementCursor → cursor=5 → start child5
Time 4: child4 completes → cursor(5) >= size(5) → no more children
Time 5: child5 completes → all done → EngineResumeCallback fires
```

**Callback flow per child completion** (`MaxConcurrentChildCallback.notify()`, Line 67):

1. Aggregate completed child's status
2. Atomically increment cursor via `nodeExecutionInfoService.incrementCursor()`
3. If `cursor >= childrenNodeExecutionIds.size()` → exit (all spawned)
4. Fetch next child NodeExecution
5. Decide skip or start via `shouldSkipNodeExecution()` (Lines 103-127)
6. Start next child or mark SKIPPED

### Skip Logic

A child is skipped when:
- `shouldProceedIfFailed = false` AND a previous child has a broken status (FAILED, ERRORED, etc.)
- Rollback is triggered (FF: `PIE_STEP_GROUP_SKIP_ON_LOOPING_STRATEGY`) and `stopStepsSequence` sweeping output is found

### Status Aggregation

**File:** `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/steps/SdkCoreStepUtils.java`

**Method:** `createStepResponseFromChildrenResponseForStrategy()` (Lines 27-37)

| Condition | Strategy Node Status |
|-----------|---------------------|
| All children SKIPPED | SKIPPED |
| Any child FAILED | FAILED |
| Any child ABORTED | ABORTED |
| All children SUCCEEDED | SUCCEEDED |

When all children complete, `EngineResumeCallback` fires and `StrategyStep.handleChildrenResponseInternal()` (Line 124) returns the aggregated `StepResponse`.

### Two-Level Concurrency

| Level | Source | Description |
|-------|--------|-------------|
| User | YAML `maxConcurrency` field | User-specified limit |
| Account | `pipelineSettingsService.getMaxConcurrencyBasedOnEdition()` | Edition-based limit (Free/Team/Enterprise) |

**Effective limit** = `min(userValue, accountLimit)` — see `SpawnChildrenRequestProcessor.getMaxConcurrencyLimit()` (Lines 233-241)

---

## Expressions

During execution, child nodes access strategy metadata via expressions:

| Expression | Strategy Type | Resolves To |
|------------|--------------|-------------|
| `<+matrix.KEY>` | Matrix | Value for axis KEY in current combination |
| `<+repeat.item>` | Repeat (items) | Current item value |
| `<+repeat.partition>` | Repeat (partitioned) | Current partition list |
| `<+strategy.iteration>` | All | 0-based iteration index |
| `<+strategy.iterations>` | All | Total iteration count |
| `<+strategy.identifierPostFix>` | All | Unique suffix for current child |
| `<+strategy.stage.matrix.KEY>` | Nested | Parent stage's matrix value |

Expressions resolve from `StrategyMetadata` attached to each child's `Ambiance`.

---

## Nested Strategies

Strategies can nest (e.g., matrix within matrix):

```
Strategy Node (Stage) - 3 children (regions)
  ├─ us-east
  │   └─ Strategy Node (StepGroup) - 2 children (services)
  │       ├─ frontend
  │       └─ backend
  ├─ us-west
  │   └─ Strategy Node (StepGroup) - 2 children
  │       ├─ frontend
  │       └─ backend
  └─ eu-west
      └─ Strategy Node (StepGroup) - 2 children
          ├─ frontend
          └─ backend
```

Inner strategies reference outer values via `<+strategy.stage.matrix.region>`.

---

## Key Classes

| Class | File | Responsibility |
|-------|------|----------------|
| `StrategyConfigPlanCreator` | `pms-sdk-core/.../plancreator/strategy/config/` | YAML → Strategy PlanNode |
| `StrategyStep` | `pms-sdk-core/.../steps/matrix/StrategyStep.java` | Orchestrates children, aggregates status |
| `MatrixConfigServiceHelper` | `pms-sdk-core/.../steps/matrix/` | Generates Cartesian product combinations |
| `ForLoopStrategyConfigService` | `pms-sdk-core/.../steps/matrix/` | Generates repeat/for-loop children |
| `ParallelismStrategyConfigService` | `pms-sdk-core/.../steps/matrix/` | Generates parallelism children |
| `SpawnChildrenRequestProcessor` | `modules/orchestration/.../event/handlers/` | Creates NodeExecutions, manages concurrency setup |
| `MaxConcurrentChildCallback` | `modules/orchestration/.../concurrency/` | Cursor-based child start on completion |
| `ChildrenFacilitator` | `modules/orchestration/.../facilitation/facilitator/chilidren/` | Sets CHILDREN execution mode |
| `SdkCoreStepUtils` | `pms-sdk-core/.../steps/SdkCoreStepUtils.java` | Status aggregation from children |

---

## Debugging

### MongoDB Queries

```javascript
// Find all children of a strategy node
db.nodeExecutions.find({planExecutionId: "execId", parentId: "strategyNodeExecId"})

// Check child strategy metadata
db.nodeExecutions.findOne({_id: "childNodeExecId"}, {status: 1, strategyMetadata: 1})

// Check concurrency state (cursor, child statuses)
db.nodeExecutionInfo.findOne({nodeExecutionId: "strategyNodeExecId"})

// Check actual maxConcurrency applied
db.nodeExecutions.findOne({_id: "strategyNodeExecId"}, {"executableResponses.children.maxConcurrency": 1})

// Find callbacks for strategy children
db.waitInstances.find({correlationIds: {$in: ["child1", "child2"]}})
```

### Common Issues

| Issue | Cause | What to Check |
|-------|-------|---------------|
| 0 iterations generated | Empty axes or all combos excluded | Matrix axes values, exclude rules |
| Children stuck NOT_STARTED | `MaxConcurrentChildCallback` not firing | `nodeExecutionInfo.cursor`, `waitInstances` |
| maxConcurrency not respected | Account limit lower than YAML value | `executableResponses.children.maxConcurrency` vs YAML |
| Expression not resolving | Missing metadata or syntax error | `nodeExecutions.strategyMetadata`, use `<+matrix.KEY>` syntax |

### Key Log Messages

```
[SpawnChildrenRequestProcessor] Processing spawn request for parent: {id}
[MaxConcurrentCallback] Starting the execution with id: {childId}
[MaxConcurrentCallback] Ignoring callback - all children traversed for {parentId}
Completed execution for Strategy Step [...]
```

---

## Related Documentation

- **[PIPELINE_EXECUTION_FLOW.md](PIPELINE_EXECUTION_FLOW.md)** - Overall execution flow
- **[POST_EXECUTION_FLOW.md](POST_EXECUTION_FLOW.md)** - Post-execution lifecycle, event publishing, state transitions
- **[PLAN_CREATION_FLOW_ANALYSIS.md](PLAN_CREATION_FLOW_ANALYSIS.md)** - YAML to plan conversion details

# Pipeline Post-Execution Flow Documentation

Complete guide to pipeline execution after `OrchestrationEngine.runNode()` - covering all execution modes, event publishing, state transitions, and notifications.

---

## Table of Contents

1. [Quick Overview](#quick-overview)
2. [Execution Modes](#execution-modes)
3. [Execution Lifecycle](#execution-lifecycle)
4. [Event Publishing](#event-publishing)
5. [State Transitions](#state-transitions)
6. [Observer Pattern](#observer-pattern)
7. [Notification Flow](#notification-flow)
8. [Flow Diagrams](#flow-diagrams)

---

## Quick Overview

```
runNode() → Create NodeExecution (QUEUED)
    ↓
startExecution() → Resolve params, Facilitate, Update to RUNNING, Publish NODE_START
    ↓
Execute based on mode: SYNC | ASYNC | TASK | CHILD | CHILDREN | ...
    ↓
processStepResponse() → Save outcomes, Update status, Fire observers
    ↓
(Optional) processAdviserResponse() → Handle retries/failures
    ↓
endNodeExecution() → Notify parent, Start queued, Complete
```

---

## Execution Modes

**File:** `/pipeline-service/modules/pms-contracts/src/main/proto/io/harness/pms/contracts/execution/execution_mode.proto`

### Mode Definitions

| Mode | Status | Description | Use Case |
|------|--------|-------------|----------|
| **SYNC** | RUNNING | Synchronous execution by SDK | Short-running steps (script, shell) |
| **ASYNC** | ASYNC_WAITING | Asynchronous execution by SDK | Long-running steps (HTTP wait, approval) |
| **TASK** | TASK_WAITING | Delegate task execution | Infrastructure tasks (K8s deploy, Terraform) |
| **TASK_CHAIN** | TASK_WAITING | Sequential delegate tasks | Multi-step delegate operations |
| **CHILD** | RUNNING | Single child node execution | Pipeline stage |
| **CHILDREN** | RUNNING | Parallel child nodes | Parallel stages |
| **CHILD_CHAIN** | RUNNING | Sequential child nodes | Stage strategy (matrix/repeat) |
| **ASYNC_CHAIN** | ASYNC_WAITING | Sequential async activities | Chained async operations |
| **SKIP** | SKIPPED | Node skipped | When/skip conditions |
| **APPROVAL** | APPROVAL_WAITING | Manual approval required | Approval gates |
| **CONSTRAINT** | RESOURCE_WAITING | Resource constraint | Concurrency limits |
| **WAIT_STEP** | WAIT_STEP_RUNNING | Timed wait | Wait step |

**Determined By:** `FacilitationHelper.calculateFacilitatorResponse()` based on node's `FacilitatorObtainments`

---

## Execution Lifecycle

### Phase 1: Node Creation (QUEUED)

**Entry:** `OrchestrationEngine.runNode(ambiance, node, metadata)`

**File:** `/modules/orchestration/src/main/java/io/harness/engine/impl/OrchestrationEngineImpl.java` (Line 64)

```java
public <T extends PmsNodeExecution> T runNode(Ambiance ambiance, Node node, PmsNodeExecutionMetadata metadata) {
  NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(node.getNodeType());
  return (T) strategy.runNode(ambiance, node, metadata);
}
```

**Strategy:** `PlanNodeExecutionStrategy.createNodeExecutionInternal()`

**File:** `/modules/orchestration/src/main/java/io/harness/engine/pms/execution/strategy/plannode/PlanNodeExecutionStrategy.java` (Line 148)

**Actions:**
1. Create `NodeExecution` entity
2. Set initial status: `QUEUED`
3. Set metadata: uuid, nodeId, planExecutionId, stepType, mode, notifyId, parentId
4. Save to MongoDB `nodeExecutions` collection
5. Fire `NodeExecutionCreateObserver`

---

### Phase 2: Execution Start (RUNNING)

**Entry:** `OrchestrationEngine.startNodeExecution(ambiance)`

**Strategy:** `PlanNodeExecutionStrategy.startExecution()`

**File:** `/modules/orchestration/src/main/java/io/harness/engine/pms/execution/strategy/plannode/PlanNodeExecutionStrategy.java` (Line 292)

#### Step 2.1: Resolve Parameters

```java
resolveParameters(ambiance, planNode);
```

- Evaluate expressions in step parameters using `PmsEngineExpressionService`
- Store resolved params in `NodeExecution.resolvedParams` (Kryo serialized)
- Save step inputs to graph service for UI

#### Step 2.2: Pre-Facilitation Checks

```java
PreFacilitationExecutionCheck check = performPreFacilitationChecks(ambiance, planNode);
```

**Checks performed:**
- **Skip Conditions**: Evaluate `node.skipCondition`
- **When Conditions**: Evaluate `node.whenCondition`
- **OPA Policies**: Run policy evaluations (if enabled)
- **Execution Inputs**: Check if inputs required

**If check fails:**
- Node status updated accordingly (SKIPPED/FAILED)
- Execution does not proceed
- Advisers may still run

#### Step 2.3: Facilitation

```java
FacilitatorResponseProto facilitatorResponse = facilitationHelper.calculateFacilitatorResponse(ambiance, planNode);
```

**Facilitators by Mode:**

| Facilitator Class | Execution Mode | File Path |
|-------------------|----------------|-----------|
| `SyncFacilitator` | SYNC | `/facilitation/facilitator/sync/SyncFacilitator.java` |
| `AsyncFacilitator` | ASYNC | `/facilitation/facilitator/async/AsyncFacilitator.java` |
| `TaskFacilitator` | TASK | `/facilitation/facilitator/task/TaskFacilitator.java` |
| `TaskChainFacilitator` | TASK_CHAIN | `/facilitation/facilitator/chain/TaskChainFacilitator.java` |
| `ChildFacilitator` | CHILD | `/facilitation/facilitator/child/ChildFacilitator.java` |
| `ChildrenFacilitator` | CHILDREN | `/facilitation/facilitator/chilidren/ChildrenFacilitator.java` |
| `ChildChainFacilitator` | CHILD_CHAIN | `/facilitation/facilitator/chain/ChildChainFacilitator.java` |
| `AsyncChainFacilitator` | ASYNC_CHAIN | `/facilitation/facilitator/chain/AsyncChainFacilitator.java` |
| `WaitStepFacilitator` | WAIT_STEP | `/facilitation/facilitator/waitStep/WaitStepFacilitator.java` |

**Facilitator Response:**
```java
FacilitatorResponseProto {
  ExecutionMode executionMode;
  Duration initialWait;
  boolean isSuccessful;
  ByteString passThroughData;
}
```

#### Step 2.4: Process Facilitation Response

**File:** `/modules/orchestration/src/main/java/io/harness/engine/pms/start/NodeStartHelper.java`

```java
public void startNode(Ambiance ambiance, FacilitatorResponseProto facilitatorResponse) {
  // 1. Calculate target status from mode
  Status targetStatus = calculateStatusFromMode(facilitatorResponse.getExecutionMode());

  // 2. Update NodeExecution status
  NodeExecution updated = nodeExecutionService.updateStatusWithOps(
      nodeExecutionId, targetStatus, ops -> {
        ops.set(NodeExecutionKeys.timeoutInstanceIds, timeoutInstanceIds);
        ops.set(NodeExecutionKeys.startTs, currentTimeMillis());
      }
  );

  // 3. Fire NodeExecutionStartObserver
  nodeExecutionStartSubject.fireInform(NodeExecutionStartObserver::onNodeStart, nodeExecution);

  // 4. Publish NODE_START event
  eventSender.sendEvent(ambiance, nodeStartEvent, PmsEventCategory.NODE_START, module, true, true);
}
```

**Status Mapping (Line 165-178):**
```java
private Status calculateStatusFromMode(ExecutionMode executionMode) {
  switch (executionMode) {
    case CONSTRAINT:      return Status.RESOURCE_WAITING;
    case APPROVAL:        return Status.APPROVAL_WAITING;
    case WAIT_STEP:       return Status.WAIT_STEP_RUNNING;
    case ASYNC:           return Status.ASYNC_WAITING;
    default:              return Status.RUNNING;
  }
}
```

---

### Phase 3: Execution by Mode

#### 3.1 SYNC Mode

**Flow:**
```
NODE_START event published
    ↓
SDK receives event
    ↓
SDK calls Step.executeSync(ambiance, stepParameters)
    ↓
Step executes synchronously (blocking)
    ↓
Returns StepResponse immediately
    ↓
SDK publishes SDK_RESPONSE_EVENT
    ↓
processStepResponse() called
```

**Characteristics:**
- Executes in same thread
- Blocks until completion
- Quick operations (< 30s)

**Examples:** Shell Script, HTTP, Variable assignment

---

#### 3.2 ASYNC Mode

**Flow:**
```
NODE_START event published
    ↓
NodeExecution status: RUNNING → ASYNC_WAITING
    ↓
SDK receives event
    ↓
SDK calls Step.executeAsync(ambiance, stepParameters, callback)
    ↓
Step starts async operation
    ↓
Step returns AsyncExecutableResponse with callback IDs
    ↓
Later: Async operation completes
    ↓
Callback invoked via WaitNotifyEngine
    ↓
resumeNodeExecution() called with response data
    ↓
processStepResponse() finalizes execution
```

**Characteristics:**
- Non-blocking
- Uses WaitNotifyEngine for callbacks
- Long-running operations

**Examples:** HTTP long-poll, Custom approval, Wait step

**Callback Registration:**
```java
AsyncExecutableResponse response = AsyncExecutableResponse.newBuilder()
    .addCallbackIds(callbackId)  // Unique callback identifier
    .addAllLogKeys(logKeys)
    .build();
```

**Resume Trigger:**
```java
// When async operation completes
waitNotifyEngine.doneWith(callbackId, responseData);
// This triggers:
OrchestrationEngine.resumeNodeExecution(ambiance, response, asyncError);
```

---

#### 3.3 TASK Mode

**Flow:**
```
NODE_START event published
    ↓
NodeExecution status: RUNNING → TASK_WAITING
    ↓
SDK receives event
    ↓
SDK creates DelegateTaskRequest
    ↓
Task queued to delegate service
    ↓
Delegate picks up task
    ↓
Delegate executes task (e.g., K8s deploy, Terraform apply)
    ↓
Delegate publishes task response
    ↓
WaitNotifyEngine receives callback
    ↓
resumeNodeExecution() called with task response
    ↓
processStepResponse() finalizes
```

**Characteristics:**
- Delegates work to external agents
- Scalable for infrastructure operations
- Tasks queued in delegate task service

**Examples:** K8s Deployment, Terraform, Shell Script on remote host

**Delegate Task Structure:**
```java
DelegateTask task = DelegateTask.builder()
    .accountId(accountId)
    .taskType(TaskType.K8S_COMMAND_TASK)
    .taskData(TaskData.builder()
        .parameters(params)
        .timeout(Duration.ofMinutes(10))
        .build())
    .build();
```

---

#### 3.4 CHILD Mode

**Flow:**
```
NODE_START event published
    ↓
SDK receives event → Identifies CHILD mode
    ↓
SDK spawns child plan execution
    ↓
Creates child PlanExecution with parent context
    ↓
Child execution runs independently
    ↓
Parent NodeExecution status: RUNNING (waiting for child)
    ↓
Child completes with status
    ↓
WaitNotifyEngine.doneWith(parent.notifyId, childResponse)
    ↓
Parent resumeNodeExecution() called
    ↓
Parent processStepResponse() with child status
```

**Characteristics:**
- Spawns new execution context
- Parent waits for child completion
- Child has own NodeExecution tree

**Examples:** Pipeline Stage (each stage is a child execution)

**Parent-Child Linking:**
```java
// Parent creates child with notifyId
NodeExecution parent = NodeExecution.builder()
    .notifyId(generateUuid())  // Parent waits on this
    .build();

// Child execution completes
StepResponseNotifyData childResponse = StepResponseNotifyData.builder()
    .status(childStatus)
    .failureInfo(childFailureInfo)
    .build();

waitNotifyEngine.doneWith(parent.getNotifyId(), childResponse);
```

---

#### 3.5 CHILDREN Mode

**Flow:**
```
NODE_START event published
    ↓
SDK receives event → Identifies CHILDREN mode
    ↓
SDK spawns multiple child plan executions in parallel
    ↓
Each child executes independently
    ↓
Parent NodeExecution status: RUNNING (waiting for all children)
    ↓
Children complete (all or first failure)
    ↓
WaitNotifyEngine.progressOn(parent.notifyId, childResponse) [for each child]
    ↓
When termination condition met (all success or first failure):
    ↓
resumeNodeExecution() called with aggregated responses
    ↓
processStepResponse() with combined status
```

**Characteristics:**
- Multiple parallel child executions
- Aggregates child results
- Supports failure strategies (fail-fast, run-all)

**Examples:** Parallel stages, Matrix/Repeat strategies

**Parallel Execution Control:**
```java
// Max parallel limit
int maxConcurrency = strategyMetadata.getMatrixMetadata().getMaxConcurrency();

// Spawn children in batches
for (int i = 0; i < totalChildren; i += maxConcurrency) {
  List<Child> batch = children.subList(i, Math.min(i + maxConcurrency, totalChildren));
  orchestrationEngine.initiateNodes(batch, InitiateMode.CREATE);
}
```

---

#### 3.6 TASK_CHAIN & CHILD_CHAIN Modes

**Sequential Execution Pattern:**

```
Chain item 1 executes
    ↓
Completes → Chain controller invoked
    ↓
Determines next item in chain
    ↓
Chain item 2 executes
    ↓
Repeats until chain exhausted
    ↓
Final response aggregated
```

**Characteristics:**
- Sequential execution of multiple items
- Each item's output feeds to next
- Used for complex multi-step flows

**Examples:**
- **TASK_CHAIN**: Multi-step Terraform (init → plan → apply)
- **CHILD_CHAIN**: Sequential matrix strategy

---

### Phase 4: Response Processing

**Entry:** `OrchestrationEngine.processStepResponse(ambiance, stepResponse)`

**Strategy:** `PlanNodeExecutionStrategy.processStepResponse()`

#### Step 4.1: Save Outcomes

**File:** `/modules/orchestration/src/main/java/io/harness/engine/pms/execution/strategy/helper/EndNodeExecutionHelperImpl.java` (Line 59)

```java
List<StepOutcomeRef> outcomeRefs = handleOutcomes(ambiance, stepResponse.getStepOutcomesList());
```

**Outcomes Processing:**
1. Iterate through `stepResponse.stepOutcomes`
2. Save each to `outcomeInstances` collection
3. Link to nodeExecutionId and scope
4. Return outcome references

**Outcome Structure:**
```java
OutcomeInstance {
  String nodeExecutionId;
  String name;              // e.g., "output", "artifact"
  String outcome;           // JSON serialized outcome data
  String group;             // Scoping group
  List<String> levelRuntimeIdIdx;  // For scoped resolution
}
```

#### Step 4.2: Update Node Status

```java
NodeExecution updated = nodeExecutionService.updateStatusWithOps(
    nodeExecutionId,
    stepResponse.getStatus(),
    ops -> {
      ops.set(NodeExecutionKeys.failureInfo, stepResponse.getFailureInfo());
      ops.set(NodeExecutionKeys.unitProgresses, stepResponse.getUnitProgressList());
      ops.set(NodeExecutionKeys.endTs, currentTimeMillis());
    }
);
```

#### Step 4.3: Fire Observers

```java
nodeStatusUpdateSubject.fireInform(
    NodeStatusUpdateObserver::onNodeStatusUpdate,
    NodeUpdateInfo.builder()
        .nodeExecution(updated)
        .timeoutInstanceIds(timeoutInstanceIds)
        .build()
);
```

**Observer Callbacks:**
- `NodeExecutionOutboxHandler` → Publish audit events
- `NotificationObserver` → Trigger notifications
- `MetricsObserver` → Record metrics

---

### Phase 5: Advising (Conditional)

**Entry:** `OrchestrationEngine.processAdviserResponse(ambiance, adviserResponse)`

**Triggered When:**
- Step execution fails
- Node has configured failure strategies

**Adviser Types & Actions:**

| Adviser | Action | Result |
|---------|--------|--------|
| **RETRY** | Create new retry NodeExecution | New execution with retryId |
| **MARK_SUCCESS** | Override status to SUCCESS | Execution marked successful |
| **IGNORE** | Update status to IGNORE | Failure ignored |
| **INTERVENTION_WAIT** | Status → INTERVENTION_WAITING | Wait for manual action |
| **MANUAL_INTERVENTION** | Status → INTERVENTION_WAITING | Manual approval required |
| **ABORT** | Status → ABORTED | Execution aborted |
| **NEXT_STEP** | Continue normally | Proceed to end |

**Retry Flow:**
```
Step fails
    ↓
RetryAdviser consulted
    ↓
If retries remaining:
    ↓
Create new NodeExecution with:
    - Same nodeId
    - New uuid (runtime ID)
    - retryId = [previous retryIds] + currentId
    - originalNodeExecutionId = original uuid
    ↓
Start new execution
    ↓
Original NodeExecution marked with retryIds
```

---

### Phase 6: Completion

**Entry:** `OrchestrationEngine.endNodeExecution(ambiance)`

**Strategy:** `PlanNodeExecutionStrategy.endNodeExecution()`

#### Step 6.1: Notify Parent

```java
if (isNotEmpty(nodeExecution.getNotifyId())) {
  StepResponseNotifyData responseData = StepResponseNotifyData.builder()
      .nodeUuid(level.getSetupId())
      .status(nodeExecution.getStatus())
      .failureInfo(nodeExecution.getFailureInfo())
      .nodeExecutionId(level.getRuntimeId())
      .build();

  waitNotifyEngine.doneWith(nodeExecution.getNotifyId(), responseData);
}
```

**Parent Notification:**
- If node is a child (has notifyId), parent is notified
- Parent's `resumeNodeExecution()` called with child response
- Parent aggregates child results

#### Step 6.2: Start Queued Executions

```java
startQueuedExecutionIfAny(nodeExecution, ambiance);
```

**Concurrency Control:**
- If node had resource constraints
- Release concurrency slot
- Start next queued node (if any)

#### Step 6.3: Update Plan (if Pipeline Node)

```java
if (isPipelineNode(nodeExecution)) {
  planExecutionService.updateStatus(planExecutionId, nodeExecution.getStatus());
  emitEvent(nodeExecution, PLAN_EXECUTION_STATUS_UPDATE);
}
```

---

## Event Publishing

### Event System Architecture

```
NodeExecution state change
    ↓
┌──────────────────────────────────────┐
│  PmsEventSender (Event Router)       │
│  - Determines consumer preference    │
│  - Routes to Redis or Kafka          │
└──────────┬───────────────────────────┘
           │
    ┌──────┴──────┐
    │             │
  Redis        Kafka
 Producer     Producer
    │             │
    ↓             ↓
SDK Service   SDK Service
Consumers     Consumers
```

### Event Categories & Purposes

**File:** `/modules/orchestration/src/main/java/io/harness/engine/pms/commons/events/PmsEventSender.java`

| Category | Published When | Consumed By | Purpose |
|----------|---------------|-------------|---------|
| `NODE_START` | Node execution starts | SDK services | Trigger step execution |
| `FACILITATOR_EVENT` | Custom facilitation | SDK services | Handle custom execution logic |
| `PROGRESS_EVENT` | Step reports progress | UI/Monitoring | Real-time progress updates |
| `NODE_ADVISE` | Adviser invocation | SDK services | Run adviser logic |
| `NODE_RESUME` | Node resumption | SDK services | Continue async execution |
| `ORCHESTRATION_EVENT` | Status changes | Internal | Orchestration state sync |
| `INTERRUPT_EVENT` | Abort/Pause/Resume | SDK services | Handle interrupts |

### Outbox Events (Audit Trail)

**File:** `/service/src/main/java/io/harness/pms/notification/orchestration/handlers/NodeExecutionOutboxHandler.java`

**Published By:** `NodeExecutionOutboxHandler` (observer)

**Event Types:**

| Event | Triggered When | Payload Highlights |
|-------|---------------|-------------------|
| `PIPELINE_START` | Pipeline begins | triggeredInfo, startTs |
| `PIPELINE_END` | Pipeline completes | status, startTs, endTs |
| `PIPELINE_TIMEOUT` | Pipeline times out | status=EXPIRED |
| `PIPELINE_ABORT` | Pipeline aborted | abortedBy info |
| `STAGE_START` | Stage begins | stageIdentifier, stageType, startTs |
| `STAGE_END` | Stage completes | status, startTs, endTs |
| `STEP_END` | Step completes | stepType, status, failureInfo, logUrl, stepOutputs |

**Destinations:**
1. **Outbox Collection** → Transactional outbox pattern for audit
2. **Kafka Topics** → Data ingestion for analytics

**Kafka Topic Configuration:**
```java
configuration.getPipelineDataIngestionTopicName()  // Pipeline events
configuration.getStageDataIngestionTopicName()     // Stage events
configuration.getStepDataIngestionTopicName()      // Step events
```

**Event Payload (Example - StepEndEvent):**
```java
StepEndEvent {
  String stepExecutionId;
  String stepName;
  String stepIdentifier;
  String stepType;
  Status status;
  Long startTs;
  Long endTs;
  FailureInfo failureInfo;
  List<String> stepOutputs;     // Outcome names
  String logUrl;                // Log stream URL
  boolean isRetried;
  List<String> retryIds;
  String stepInputs;            // Resolved parameters
  String nodeEventType;         // "nodeStart" | "nodeEnd" | "nodeStatusUpdate"
}
```

---

## State Transitions

### Status Lifecycle

```
QUEUED
  ↓
RUNNING
  ↓
┌─────────────────────────────────────────────┐
│ Interim States (Mode-Dependent)             │
│ - TASK_WAITING      (TASK mode)             │
│ - ASYNC_WAITING     (ASYNC mode)            │
│ - RESOURCE_WAITING  (CONSTRAINT mode)       │
│ - APPROVAL_WAITING  (APPROVAL mode)         │
│ - INTERVENTION_WAITING (Manual intervention)│
│ - WAIT_STEP_RUNNING (WAIT_STEP mode)        │
│ - INPUT_WAITING     (Execution input)       │
│ - PAUSED            (Manual pause)          │
└─────────────────────────────────────────────┘
  ↓
Terminal States:
  SUCCESS | FAILED | ABORTED | EXPIRED | SKIPPED | ERRORED | IGNORED
```

### Update Mechanism

**File:** `/modules/orchestration/src/main/java/io/harness/engine/executions/node/service/impl/NodeExecutionServiceImpl.java` (Line 778)

```java
public NodeExecution updateStatusWithOps(String nodeExecutionId, Status status, Consumer<Update> ops) {
  // 1. Determine allowed source statuses for transition
  EnumSet<Status> allowedStatuses = StatusUtils.nodeAllowedStartSet(status);

  // 2. Build query with optimistic locking
  Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId))
      .addCriteria(where(NodeExecutionKeys.status).in(allowedStatuses));

  // 3. Build update operations
  Update updateOps = new Update()
      .set(NodeExecutionKeys.status, status)
      .set(NodeExecutionKeys.lastUpdatedAt, currentTimeMillis());

  if (ops != null) {
    ops.accept(updateOps);
  }

  // 4. Add final status operations
  if (StatusUtils.isFinalStatus(status)) {
    updateOps.set(NodeExecutionKeys.endTs, currentTimeMillis());
    if (status != EXPIRED) {
      updateOps.set(NodeExecutionKeys.timeoutInstanceIds, new ArrayList<>());
    }
  }

  // 5. Execute atomic update
  NodeExecution updated = mongoTemplate.findAndModify(query, updateOps, returnNewOptions, NodeExecution.class);

  // 6. Emit events if successful
  if (updated != null) {
    if (StepCategory.STAGE || StatusUtils.isFinalStatus(status)) {
      emitEvent(updated, NODE_EXECUTION_STATUS_UPDATE);
    }
    orchestrationLogPublisher.onNodeStatusUpdate(updated);
    nodeStatusUpdateSubject.fireInform(NodeStatusUpdateObserver::onNodeStatusUpdate, updated);
  }

  return updated;
}
```

**Transition Guards:**

Invalid transitions return `null` (update fails):
- Can't transition from SUCCESS to RUNNING
- Can't transition from ABORTED to RUNNING
- Can't transition from terminal state to non-terminal (except via retry)

---

## Observer Pattern

### Observer Infrastructure

**File:** `/modules/orchestration/src/main/java/io/harness/engine/executions/node/service/impl/NodeExecutionServiceImpl.java` (Line 147)

```java
@Getter private final Subject<NodeStatusUpdateObserver> nodeStatusUpdateSubject = new Subject<>();
@Getter private final Subject<NodeExecutionStartObserver> nodeExecutionStartSubject = new Subject<>();
@Getter private final Subject<NodeExecutionDeleteObserver> nodeDeleteObserverSubject = new Subject<>();
```

### Observer Implementations

#### 1. NodeExecutionOutboxHandler

**Implements:** `NodeExecutionStartObserver`, `NodeStatusUpdateObserver`

**File:** `/service/src/main/java/io/harness/pms/notification/orchestration/handlers/NodeExecutionOutboxHandler.java`

**On Node Start:**
- Creates `PipelineStartEvent` for pipeline nodes
- Creates `StageStartEvent` for stage nodes
- Publishes to Outbox and Kafka

**On Status Update:**
- For terminal statuses:
  - `PipelineEndEvent` / `StageEndEvent` / `StepEndEvent`
  - Publishes to Outbox (audit) and Kafka (analytics)
- For interrupt statuses (ABORTED, EXPIRED):
  - Additional interrupt events (`PipelineAbortEvent`, `PipelineTimeoutEvent`)

#### 2. NotificationObserver

**Triggers:** User notifications based on configured rules

**Event Mapping:**
- Status.SUCCESS → PIPELINE_SUCCESS / STAGE_SUCCESS
- Status.FAILED → PIPELINE_FAILED / STAGE_FAILED
- Status.INTERVENTION_WAITING → WAITING_FOR_USER_ACTION

#### 3. Metrics Observers

- **PipelineExecutionMetricsObserver** - Pipeline-level metrics
- **StepExecutionMetricsObserver** - Step-level metrics

**Metrics Recorded:**
- Execution duration
- Success/failure rates
- Step type distribution

---

## Notification Flow

### Trigger Points

**File:** `/service/src/main/java/io/harness/pms/notification/helper/NotificationHelper.java`

```
NodeExecution reaches terminal status
    ↓
NodeStatusUpdateObserver.onNodeStatusUpdate()
    ↓
Determine PipelineEventType (e.g., PIPELINE_SUCCESS, STAGE_FAILED)
    ↓
Fetch pipeline YAML notification config
    ↓
Evaluate notification rules
    ↓
Build notification payload from template
    ↓
NotificationClient.sendNotification()
    ↓
Deliver via channels
```

### Event Types

```java
PIPELINE_START
PIPELINE_SUCCESS
PIPELINE_FAILED
PIPELINE_PAUSED
STAGE_START
STAGE_SUCCESS
STAGE_FAILED
STEP_FAILED
WAITING_FOR_USER_ACTION
TRIGGER_FAILED
```

### Notification Configuration

**Pipeline YAML:**
```yaml
notifications:
  - name: "Pipeline Failed Alert"
    enabled: true
    pipelineEvents:
      - type: PipelineFailed
      - type: StageFailed
    notificationMethod:
      type: Slack
      spec:
        webhookUrl: "https://hooks.slack.com/..."
    userGroups:
      - account.admin
```

### Delivery

**NotificationClient** routes to configured channels:
- **Email** - via SMTP service
- **Slack** - via webhook
- **Microsoft Teams** - via webhook
- **PagerDuty** - via API
- **Webhook** - custom endpoint

---

## Flow Diagrams

### Complete Execution Flow

```
[1] runNode()
    ↓
    Create NodeExecution (QUEUED)
    Save to MongoDB
    Fire NodeExecutionCreateObserver
    ↓
[2] startExecution()
    ↓
    ├─ Resolve parameters
    ├─ Pre-facilitation checks
    ├─ Facilitate → Determine execution mode
    ├─ Update status: QUEUED → RUNNING/TASK_WAITING/ASYNC_WAITING/etc.
    ├─ Fire NodeExecutionStartObserver
    │  └─ Publish PipelineStart/StageStart to Outbox & Kafka
    └─ Publish NODE_START event to Redis/Kafka
    ↓
[3] Execute by Mode
    ↓
  ┌─────┼──────────┬────────────┬────────────┐
  │     │          │            │            │
SYNC  ASYNC     TASK       CHILD      CHILDREN
  │     │          │            │            │
  └─────┴──────────┴────────────┴────────────┘
    ↓
  SDK/Delegate executes
  Returns StepResponse
    ↓
[4] processStepResponse()
    ↓
    ├─ Save outcomes to outcomeInstances
    ├─ Update status: RUNNING → SUCCESS/FAILED/etc.
    ├─ Set endTs
    ├─ Fire NodeStatusUpdateObserver
    │  ├─ Publish PipelineEnd/StageEnd/StepEnd to Outbox & Kafka
    │  ├─ Trigger notifications
    │  └─ Record metrics
    └─ Emit ORCHESTRATION_EVENT (NODE_EXECUTION_STATUS_UPDATE)
    ↓
[5] processAdviserResponse() [if failure + strategy]
    ↓
    RETRY: Create new NodeExecution
    MARK_SUCCESS: Override to SUCCESS
    INTERVENTION_WAIT: Wait for manual action
    IGNORE: Update to IGNORE
    ↓
[6] endNodeExecution()
    ↓
    ├─ If child: Notify parent via WaitNotifyEngine
    ├─ Start queued executions (release concurrency)
    └─ If pipeline node: Update PlanExecution status
    ↓
[7] Notification Delivery
    ↓
    Email / Slack / PagerDuty / MS Teams / Webhook
```

### Async Execution Detail

```
[NODE_START] → SDK receives
    ↓
Step.executeAsync() called
    ↓
Step starts async operation (e.g., HTTP long-poll)
    ↓
Returns AsyncExecutableResponse with callbackIds
    ↓
NodeExecution status: RUNNING → ASYNC_WAITING
    ↓
... Time passes ...
    ↓
Async operation completes
    ↓
Callback triggered: waitNotifyEngine.doneWith(callbackId, responseData)
    ↓
resumeNodeExecution(ambiance, response, asyncError)
    ↓
Process response data
    ↓
Build final StepResponse
    ↓
processStepResponse() → Complete execution
```

### Child Execution Detail

```
[Parent NODE_START] → SDK receives
    ↓
SDK identifies CHILD mode
    ↓
Spawn child PlanExecution
    ↓
Parent NodeExecution: notifyId = UUID
Parent status: RUNNING (waiting)
    ↓
Child execution runs (entire lifecycle)
    ↓
Child completes with status (SUCCESS/FAILED)
    ↓
waitNotifyEngine.doneWith(parent.notifyId, childResponse)
    ↓
Parent resumeNodeExecution() triggered
    ↓
Parent processes child response
    ↓
Parent status updated based on child status
    ↓
Parent endNodeExecution()
```

### Children (Parallel) Execution Detail

```
[Parent NODE_START] → SDK receives
    ↓
SDK identifies CHILDREN mode
    ↓
Spawn N child PlanExecutions in parallel
    ↓
Parent NodeExecution: notifyId = UUID
Parent status: RUNNING (waiting for all)
    ↓
Children execute in parallel
    ↓
As each child completes:
  waitNotifyEngine.progressOn(parent.notifyId, childResponse)
    ↓
Termination condition checked:
  - All children success → Parent SUCCESS
  - First child failure (fail-fast) → Parent FAILED
  - All children complete → Aggregate status
    ↓
resumeNodeExecution() with aggregated responses
    ↓
Parent status updated
    ↓
Parent endNodeExecution()
```

---

## Key MongoDB Collections

| Collection | Purpose | Key Fields |
|------------|---------|------------|
| `nodeExecutions` | Runtime execution state | uuid, status, nodeId, planExecutionId, failureInfo, resolvedParams, mode, notifyId, startTs, endTs |
| `planExecutions` | Pipeline execution state | uuid, planId, status, metadata, startTs, endTs |
| `outcomeInstances` | Step outputs/outcomes | nodeExecutionId, name, outcome (JSON), levelRuntimeIdIdx |
| `outbox` | Audit event queue | eventType, resourceScope, eventData, createdAt |

---

## File Reference

| Component | File Path |
|-----------|-----------|
| OrchestrationEngine | `/modules/orchestration/src/main/java/io/harness/engine/impl/OrchestrationEngineImpl.java` |
| NodeExecutionService | `/modules/orchestration/src/main/java/io/harness/engine/executions/node/service/impl/NodeExecutionServiceImpl.java` |
| PlanNodeExecutionStrategy | `/modules/orchestration/src/main/java/io/harness/engine/pms/execution/strategy/plannode/PlanNodeExecutionStrategy.java` |
| NodeStartHelper | `/modules/orchestration/src/main/java/io/harness/engine/pms/start/NodeStartHelper.java` |
| PmsEventSender | `/modules/orchestration/src/main/java/io/harness/engine/pms/commons/events/PmsEventSender.java` |
| NodeExecutionOutboxHandler | `/service/src/main/java/io/harness/pms/notification/orchestration/handlers/NodeExecutionOutboxHandler.java` |
| NotificationHelper | `/service/src/main/java/io/harness/pms/notification/helper/NotificationHelper.java` |
| EndNodeExecutionHelper | `/modules/orchestration/src/main/java/io/harness/engine/pms/execution/strategy/helper/EndNodeExecutionHelperImpl.java` |
| Facilitators | `/modules/orchestration/src/main/java/io/harness/engine/facilitation/facilitator/` |
| Event Classes | `/modules/orchestration/src/main/java/io/harness/engine/pms/audits/events/` |

All paths relative to: `/pipeline-service/`

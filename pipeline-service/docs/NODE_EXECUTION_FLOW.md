# Node Execution Flow

How the orchestration engine executes individual nodes within a pipeline. Covers the complete lifecycle from node initiation through execution strategies, callback mechanisms, adviser processing, and parent notification.

**Prerequisite**: Read [PIPELINE_EXECUTION_FLOW.md](PIPELINE_EXECUTION_FLOW.md) first for the API entry point through plan creation.

---

## Quick Reference

| What | Where |
|------|-------|
| Orchestration engine entry | `modules/orchestration/src/main/java/io/harness/engine/impl/OrchestrationEngineImpl.java` |
| Plan node execution strategy | `modules/orchestration/src/main/java/io/harness/engine/pms/execution/strategy/plannode/PlanNodeExecutionStrategy.java` |
| Abstract node execution strategy | `modules/orchestration/src/main/java/io/harness/engine/pms/execution/strategy/AbstractNodeExecutionStrategy.java` |
| Pre-facilitation checker | `modules/orchestration/src/main/java/io/harness/engine/facilitation/RunPreFacilitationChecker.java` |
| Node start helper | `modules/orchestration/src/main/java/io/harness/engine/pms/start/NodeStartHelper.java` |
| Initiate node helper | `modules/orchestration/src/main/java/io/harness/execution/helpers/InitiateNodeHelper.java` |
| Initiate node handler | `modules/orchestration/src/main/java/io/harness/execution/InitiateNodeHandler.java` |
| Initiate node batch handler | `modules/orchestration/src/main/java/io/harness/execution/InitiateNodeBatchHandler.java` |
| SDK executable processor factory | `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/execution/ExecutableProcessorFactory.java` |
| Node start event handler (SDK) | `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/execution/events/node/start/NodeStartEventHandler.java` |
| Async strategy (SDK) | `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/execution/AsyncStrategy.java` |
| Task strategy (SDK) | `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/execution/TaskStrategy.java` |
| Child strategy (SDK) | `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/execution/ChildStrategy.java` |
| Children strategy (SDK) | `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/execution/ChildrenStrategy.java` |
| Spawn child processor | `modules/orchestration/src/main/java/io/harness/event/handlers/SpawnChildRequestProcessor.java` |
| Spawn children processor | `modules/orchestration/src/main/java/io/harness/event/handlers/SpawnChildrenRequestProcessor.java` |
| Queue task processor | `modules/orchestration/src/main/java/io/harness/event/handlers/QueueTaskRequestProcessor.java` |
| Handle step response processor | `modules/orchestration/src/main/java/io/harness/event/handlers/HandleStepResponseRequestProcessor.java` |
| SDK response handler | `modules/orchestration/src/main/java/io/harness/execution/SdkResponseHandler.java` |
| SDK response processor factory | `modules/orchestration/src/main/java/io/harness/engine/pms/execution/SdkResponseProcessorFactory.java` |
| End node execution helper | `modules/orchestration/src/main/java/io/harness/engine/pms/execution/EndNodeExecutionHelperImpl.java` |
| Node advise helper | `modules/orchestration/src/main/java/io/harness/engine/pms/advise/NodeAdviseHelper.java` |
| Node advise event publisher | `modules/orchestration/src/main/java/io/harness/engine/pms/advise/publisher/NodeAdviseEventPublisherImpl.java` |
| PMS event sender | `modules/orchestration/src/main/java/io/harness/engine/pms/commons/events/PmsEventSender.java` |
| Node advise event handler (SDK) | `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/execution/events/node/advise/NodeAdviseEventHandler.java` |
| Node advise base handler (SDK) | `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/execution/events/node/advise/NodeAdviseBaseHandler.java` |
| Node advise Kafka consumer (SDK) | `clients/pipeline-service/pms-sdk/src/main/java/io/harness/pms/sdk/execution/events/node/advise/NodeAdviseEventKafkaConsumer.java` |
| Kafka consumer base | `kafka-client/src/main/java/io/harness/kafka/consumers/HKafkaConsumer.java` |
| Duplicate record processor | `kafka-client/src/main/java/io/harness/kafka/common/DuplicatedConsumerRecordProcessor.java` |
| Adviser response processor | `modules/orchestration/src/main/java/io/harness/event/handlers/AdviserResponseRequestProcessor.java` |
| SDK response event publisher | `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/response/publishers/SdkResponseEventPublisherImpl.java` |
| SDK node execution service | `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/execution/SdkNodeExecutionServiceImpl.java` |
| Wait/notify engine | `950-wait-engine/src/main/java/io/harness/waiter/WaitNotifyEngine.java` |
| Notify event listener helper | `950-wait-engine/src/main/java/io/harness/waiter/NotifyEventListenerHelper.java` |
| Engine resume callback | `modules/orchestration/src/main/java/io/harness/engine/pms/resume/callback/resume/EngineResumeCallback.java` |
| Async SDK resume callback | `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/execution/AsyncSdkResumeCallback.java` |
| Max concurrent child callback | `modules/orchestration/src/main/java/io/harness/concurrency/MaxConcurrentChildCallback.java` |
| Delegate task executor | `modules/orchestration/src/main/java/io/harness/engine/pms/tasks/NgDelegate2TaskExecutor.java` |
| Facilitation helper | `modules/orchestration/src/main/java/io/harness/engine/facilitation/FacilitationHelper.java` |
| Execution input wait helper | `modules/orchestration/src/main/java/io/harness/engine/execution/WaitForExecutionInputHelper.java` |
| SDK response event proto | `modules/pms-contracts/src/main/proto/io/harness/pms/contracts/execution/events/sdk_response_event.proto` |

---

## 1. NodeExecution Hierarchy

A pipeline execution creates a tree of `NodeExecution` records in MongoDB. Each node in the tree corresponds to a structural element in the pipeline YAML.

```
pipeline                          ← stepCategory: PIPELINE
└── stages                        ← stepCategory: STAGES (wrapper)
    ├── stage (Deploy to Dev)     ← stepCategory: STAGE
    │   └── spec                  ← wrapper node
    │       └── execution         ← wrapper node
    │           └── steps         ← wrapper node
    │               ├── step1     ← stepCategory: STEP (leaf)
    │               └── step2     ← stepCategory: STEP (leaf)
    └── stage (Deploy to Prod)    ← stepCategory: STAGE
        └── ...
```

Additional node types that appear in the hierarchy:

| Node Type | Description | Execution Mode |
|-----------|-------------|----------------|
| `parallel` | Wraps nodes that execute concurrently | CHILDREN |
| `strategy` | Wraps matrix/repeat/parallelism | CHILDREN |
| `stepGroup` | Groups steps together | CHILD |
| `stage` | Pipeline stage | CHILD |
| `step` | Leaf execution unit | SYNC, ASYNC, or TASK |

Each NodeExecution record has:
- `_id` (runtimeId / nodeExecutionId) - unique per execution instance
- `planExecutionId` - links to the parent plan execution
- `parentId` - runtimeId of the parent node
- `notifyId` - set to this node's own runtimeId; parent registers a callback waiting on this ID
- `status` - current execution state
- `stepType` - type identifier (e.g., `K8sRollingDeploy`, `ShellScript`)
- `advisorsProcessed` - critical field for stuck execution detection

---

## 2. Node Initiation

After plan creation, the pipeline node is the first to be initiated. Each subsequent node is initiated by its parent's execution logic.

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `pipeline_initiate_node` | Single node initiation events |
| `pipeline_initiate_node_batch` | Batch node initiation (children) |
| `pipeline_node_start_%s` | Node start events sent to module SDK (format: service name) |
| `pipeline_node_advise_%s` | Advise events sent to module SDK for adviser computation (format: service name) |
| `pipeline_sdk_response` | SDK response events back to orchestration (facilitation, adviser response, resume, error, etc.) |
| `pipeline_sdk_step_response` | Step response and queue task events |
| `pipeline_sdk_spawn` | Child/children spawn events |
| `pipeline_node_resume_%s` | Node resume events |

### InitiateMode

Nodes can be initiated in different modes:

| Mode | Behavior |
|------|----------|
| `CREATE_AND_START` | Create NodeExecution record and immediately start execution |
| `CREATE` | Only create the NodeExecution record (used for children with concurrency limits) |
| `START` | Start execution of an already-created NodeExecution |

### Initiation Flow

```
InitiateNodeHelper.publishEvent()
  → Kafka topic: pipeline_initiate_node
  → InitiateNodeHandler.handleEventWithContext()
      ├─ IF mode == START:
      │   → engine.queueOrStartExecution(ambiance)
      └─ ELSE (CREATE or CREATE_AND_START):
          → engine.initiateNode(ambiance, nodeId, runtimeId, ..., mode)
              → AbstractNodeExecutionStrategy.runNode()
                  ├─ Creates NodeExecution record in MongoDB
                  │   (notifyId = currentRuntimeId, parentId = parentRuntimeId)
                  └─ IF mode != CREATE:
                      → orchestrationEngine.queueOrStartExecution()
```

For batch initiation (`InitiateNodeBatchHandler`):
- `CREATE` mode: calls `engine.initiateNodes()` to create all at once
- `CREATE_AND_START` mode: creates all first with `CREATE`, then publishes individual `START` events

---

## 3. startExecution - Pre-Facilitation

`PlanNodeExecutionStrategy.startExecution()` is the main entry point for node execution. It runs several checks before the node actually begins executing.

```
PlanNodeExecutionStrategy.startExecution(ambiance)
  │
  ├─ 1. resolveParameters()
  │     Resolve step parameters (expressions, references)
  │
  ├─ 2. performPreFacilitationChecks()
  │     → RunPreFacilitationChecker.performCheck()
  │         ├─ Max nesting level check
  │         │   (if exceeded → FAILED, return proceed=false)
  │         └─ When condition evaluation
  │             ├─ V1 pipelines: JEXL + CEL evaluation
  │             └─ V0 pipelines: JEXL only
  │             IF when condition == false → SKIPPED, return proceed=false
  │             IF when condition == true → proceed=true
  │
  ├─ 3. processDependencyGraphExecution()
  │     (only for stages with dependency graph / ORCHESTRATION workflow)
  │
  ├─ 4. waitForExecutionInput()
  │     IF execution input template exists:
  │       → Create ExecutionInputInstance
  │       → Register WaitForExecutionInputCallback
  │       → Set status to INPUT_WAITING
  │       → return (paused until input provided)
  │
  ├─ 5. checkAndRunSecondaryFacilitator()
  │     (OPA policy checks if configured)
  │
  ├─ 6. Check custom facilitator
  │     IF custom facilitator present:
  │       → FacilitateEventPublisher.publishEvent()
  │       → Event sent to module SDK for facilitation
  │       → return (async; response comes back via SDK response event)
  │
  └─ 7. Built-in facilitator (default path)
        → FacilitationHelper.calculateFacilitatorResponse()
        → processFacilitationResponseV2(ambiance, facilitatorResponse, updates)
            → NodeStartHelper.startNode()
```

### NodeStartHelper.startNode()

This is where the node actually starts executing:

1. Register timeouts (from node's timeout obtainments)
2. Update node status based on execution mode:
   - `CONSTRAINT` → `RESOURCE_WAITING`
   - `APPROVAL` → `APPROVAL_WAITING`
   - `WAIT_STEP` → `WAIT_STEP_RUNNING`
   - `ASYNC` → `ASYNC_WAITING`
   - Default → `RUNNING`
3. Fire `NodeExecutionStartObserver`
4. Build `NodeStartEvent` with step parameters, facilitator pass-through data, execution mode
5. Publish event via `eventSender.sendEvent()` with `PmsEventCategory.NODE_START`
   - Topic: `pipeline_node_start_%s` (where `%s` is the module's service name)

---

## 4. SDK-Side Execution

The module's SDK receives the `NodeStartEvent` and routes it to the appropriate execution strategy.

### NodeStartEventHandler

Entry point on the SDK/module side:

```
NodeStartEventHandler.handleEventWithContext(NodeStartEvent)
  → ExecutableProcessorFactory.obtainProcessor(executionMode)
  → processor.handleStart(InvokerPackage)
  → executeStrategy.start(invokerPackage)
```

### ExecutableProcessorFactory Routing

| Execution Mode | Strategy |
|----------------|----------|
| `ASYNC`, `APPROVAL`, `WAIT_STEP`, `CONSTRAINT` | `AsyncStrategy` |
| `SYNC` | `SyncStrategy` |
| `TASK` | `TaskStrategy` |
| `CHILD` | `ChildStrategy` |
| `CHILDREN` | `ChildrenStrategy` |
| `TASK_CHAIN` | `TaskChainStrategy` |
| `CHILD_CHAIN` | `ChildChainStrategy` |
| `ASYNC_CHAIN` | `AsyncChainStrategy` |

---

## 5. Execution Strategies (SDK Side)

### 5.1 AsyncStrategy

Used for steps that perform asynchronous operations (HTTP calls, wait steps, approvals).

**Interface**: `AsyncExecutable<T>`

**Start flow**:
```
AsyncStrategy.start(invokerPackage)
  → step.executeAsync(ambiance, stepParameters, inputPackage, passThroughData)
  → Returns AsyncExecutableResponse with:
      - callbackIds: List<String>   (correlation IDs to wait for)
      - timeout: long               (timeout in milliseconds)
  → AsyncStrategy.handleResponse()
      → Register callbacks via AsyncWaitEngine.waitForAllOn():
          - AsyncSdkResumeCallback   (fires when ALL callbacks received)
          - AsyncSdkSingleCallback   (fires per individual callback, if multiple)
          - AsyncSdkProgressCallback  (fires for progress updates)
      → WaitInstance created in MongoDB with correlationIds
```

**Resume flow** (when all callbacks received):
```
External system → WaitNotifyEngine.doneWith(callbackId, responseData)
  → NotifyResponse saved to MongoDB
  → WaitNotifyEngine checks if all correlationIds satisfied
  → If yes: sendNotification() → Redis publish
  → PmsNotifyEventConsumerRedis → NotifyEventListenerHelper
  → AsyncSdkResumeCallback.notify(responseDataMap)
      → AsyncStrategy.resume(resumePackage)
          → step.handleAsyncResponse(ambiance, stepParameters, responseDataMap)
          → Returns StepResponse
          → sdkNodeExecutionService.handleStepResponse()
              → Publishes HANDLE_STEP_RESPONSE event
```

### 5.2 TaskStrategy

Used for steps that delegate work to Harness delegates (K8s deploy, Terraform, shell scripts).

**Interface**: `TaskExecutable<T>`

**Start flow**:
```
TaskStrategy.start(invokerPackage)
  → step.obtainTaskOptional(ambiance, stepParameters, inputPackage)
  → Returns Optional<TaskRequest>
  → IF empty: resume immediately with empty response
  → IF present: handleResponse()
      → Build QueueTaskRequest with TaskRequest
      → sdkNodeExecutionService.queueTaskRequest()
          → Publishes QUEUE_TASK event to pipeline_sdk_step_response topic
```

**Orchestration handles QUEUE_TASK**:
```
QueueTaskRequestProcessor.handleEvent()
  → NgDelegate2TaskExecutor.queueTask()
      → delegateServiceBlockingStub.submitTaskV2() (gRPC to delegate service)
      → Returns taskId
  → Update NodeExecution with executableResponse and TASK_WAITING status
  → Register EngineResumeCallback via waitNotifyEngine.waitForAllOn(taskId)
```

**Resume flow** (when delegate completes):
```
Delegate completes → WaitNotifyEngine.doneWith(taskId, responseData)
  → EngineResumeCallback.notify()
      → orchestrationEngine.resumeNodeExecution(ambiance, responseDataMap)
      → NodeResumeEventHandler → ExecutableProcessor.handleResume()
          → TaskStrategy.resume(resumePackage)
              → step.handleTaskResult(ambiance, stepParameters, responseDataSupplier)
              → Returns StepResponse
              → Publishes HANDLE_STEP_RESPONSE event
```

### 5.3 ChildStrategy

Used when a node needs to spawn a single child (e.g., stage spawning its spec).

**Interface**: `ChildExecutable<T>`

**Start flow**:
```
ChildStrategy.start(invokerPackage)
  → step.obtainChild(ambiance, stepParameters, inputPackage)
  → Returns ChildExecutableResponse with childNodeId
  → IF skip: resume immediately
  → ELSE: sdkNodeExecutionService.spawnChild()
      → Publishes SPAWN_CHILD event to pipeline_sdk_spawn topic
```

**Orchestration handles SPAWN_CHILD**:
```
SpawnChildRequestProcessor.handleEvent()
  → initiateNodeHelper.publishEvent(ambiance, childNodeId, childInstanceId)
      → Publishes InitiateNodeEvent (mode: CREATE_AND_START)
  → Register EngineResumeCallback waiting on childInstanceId
  → Child creates its NodeExecution with notifyId = childInstanceId
```

**Resume flow** (when child completes):
```
Child node completes → endNodeExecution()
  → waitNotifyEngine.doneWith(notifyId, StepResponseNotifyData)
  → Parent's EngineResumeCallback.notify()
      → orchestrationEngine.resumeNodeExecution(parentAmbiance, responseData)
      → ChildStrategy.resume()
          → step.handleChildResponse(ambiance, stepParameters, responseDataMap)
          → Returns StepResponse
```

### 5.4 ChildrenStrategy

Used when a node needs to spawn multiple children (parallel stages, matrix, strategy).

**Interface**: `ChildrenExecutable<T>`

**Start flow**:
```
ChildrenStrategy.start(invokerPackage)
  → step.obtainChildren(ambiance, stepParameters, inputPackage)
  → Returns ChildrenExecutableResponse with:
      - List<Child>: children to spawn
      - maxConcurrency: max parallel children (0 = unlimited)
  → IF no children: resume immediately
  → ELSE: sdkNodeExecutionService.spawnChildren()
      → Publishes SPAWN_CHILDREN event to pipeline_sdk_spawn topic
```

**Orchestration handles SPAWN_CHILDREN** (`SpawnChildrenRequestProcessor`):

This is the most complex spawn flow. Key differences from SPAWN_CHILD:

1. **All children are created first** (InitiateMode.CREATE), not started
2. **Only children within maxConcurrency limit are started** (InitiateMode.START)
3. **MaxConcurrentChildCallback** is registered for each child to manage the concurrency window

```
SpawnChildrenRequestProcessor.handleEvent()
  │
  ├─ Generate UUIDs for all children upfront
  │
  ├─ Save ConcurrentChildInstance:
  │   { childrenNodeExecutionIds: [...], cursor: maxConcurrency }
  │
  ├─ FOR each child:
  │   ├─ engine.initiateNode(mode: CREATE)        ← Create only, don't start
  │   ├─ IF index < maxConcurrency:
  │   │   → Add to ambianceList (will be started)
  │   └─ Register MaxConcurrentChildCallback(childId)
  │
  ├─ FOR each ambiance in ambianceList:
  │   → initiateNodeHelper.publishEvent(ambiance, mode: START)  ← Now start
  │
  └─ Register EngineResumeCallback waiting on ALL childIds
      → Parent resumes only when ALL children complete
```

**MaxConcurrentChildCallback** (manages concurrency window):
```
MaxConcurrentChildCallback.notify()
  → Increment cursor in ConcurrentChildInstance
  → IF cursor >= total children: return (all done)
  → Get next child at cursor position
  → IF shouldSkip: skip it
  → ELSE: start execution of next child
```

---

## 6. SDK Response Events

All SDK-to-orchestration communication flows through `SdkResponseEventProto` events.

### Event Types and Routing

| Event Type | Published By | Topic | Processor |
|------------|-------------|-------|-----------|
| `HANDLE_STEP_RESPONSE` | Step completion | `pipeline_sdk_step_response` | `HandleStepResponseRequestProcessor` |
| `QUEUE_TASK` | TaskStrategy | `pipeline_sdk_step_response` | `QueueTaskRequestProcessor` |
| `SPAWN_CHILD` | ChildStrategy | `pipeline_sdk_spawn` | `SpawnChildRequestProcessor` |
| `SPAWN_CHILDREN` | ChildrenStrategy | `pipeline_sdk_spawn` | `SpawnChildrenRequestProcessor` |
| `RESUME_NODE_EXECUTION` | Async resume | `pipeline_sdk_response` | `ResumeNodeExecutionRequestProcessor` |
| `HANDLE_FACILITATE_RESPONSE` | Custom facilitator | `pipeline_sdk_response` | `FacilitateResponseRequestProcessor` |
| `HANDLE_ADVISER_RESPONSE` | Adviser SDK | `pipeline_sdk_response` | `AdviserResponseRequestProcessor` |
| `ADD_EXECUTABLE_RESPONSE` | Executable response | `pipeline_sdk_response` | `AddExecutableResponseRequestProcessor` |
| `HANDLE_PROGRESS` | Progress update | `pipeline_sdk_response` | `HandleProgressRequestProcessor` |
| `HANDLE_EVENT_ERROR` | Error handling | `pipeline_sdk_response` | `ErrorEventRequestProcessor` |

### SdkResponseHandler

Entry point on orchestration side for all SDK response events:

```
SdkResponseHandler.handleEventWithContext(event)
  → engine.handleSdkResponseEvent(event)
      → AbstractNodeExecutionStrategy.handleSdkResponseEvent(event)
          → SdkResponseProcessorFactory.getHandler(eventType)
          → handler.handleEvent(event)
```

---

## 7. Step Response Handling (handleStepResponseInternal)

When a step completes (via any strategy), a `HANDLE_STEP_RESPONSE` event is published. This triggers the orchestration-side response handling.

```
HandleStepResponseRequestProcessor.handleEvent()
  → orchestrationEngine.processStepResponse(ambiance, stepResponse)
      → PlanNodeExecutionStrategy.processStepResponse()
          → handleStepResponseInternal(ambiance, stepResponse)
```

### handleStepResponseInternal Flow

```
handleStepResponseInternal(ambiance, stepResponse)
  │
  ├─ Fetch PlanNode and NodeExecution from DB
  ├─ Validate outcome sizes
  ├─ Update planExecution/stage status
  │
  ├─ IF no advisers (empty adviserObtainments on PlanNode):
  │   └─ endNodeExecutionHelper.endNodeExecutionWithNoAdvisers()
  │       ├─ handleOutcomes() - save step outcomes
  │       ├─ finalizeNodeWithStepResponse()
  │       │   → Update status + set advisorsProcessed = true
  │       └─ endNodeExecution() → notify parent
  │
  └─ ELSE (has advisers):
      ├─ endNodeExecutionHelper.handleStepResponsePreAdviser()
      │   ├─ handleOutcomes()
      │   └─ finalizeNodeWithStepResponse()
      │       → Update status, do NOT set advisorsProcessed yet
      │
      ├─ IF null (status update failed):
      │   └─ endNodeExecutionWithUnexpectedFailure()
      │       → Set advisorsProcessed = true (if FF enabled)
      │
      └─ ELSE:
          └─ processOrQueueAdvisingEvent(nodeExecution, planNode, fromStatus)
```

---

## 8. Adviser Flow

Advisers determine what happens after a node finishes (move to next step, retry, mark success, etc.). This is a **cross-service flow**: the orchestration engine (pipeline-service) publishes an advise event to the module SDK (e.g., ng-manager for CD steps), which computes the adviser response and sends it back.

### 8.1 processOrQueueAdvisingEvent (Orchestration Side)

```
AbstractNodeExecutionStrategy.processOrQueueAdvisingEvent(nodeExecution, planNode, fromStatus)
  │
  ├─ IF has custom advisers (NodeAdviserUtils.hasCustomAdviser(planNode)):
  │   → nodeAdviseHelper.queueAdvisingEvent(nodeExecution, planNode, fromStatus)
  │       → NodeAdviseEventPublisherImpl.publishEvent()
  │           ├─ IF stuck monitor v2 enabled:
  │           │   → nodeExecutionService.markNodesProcessing(nodeExecutionId, true)
  │           │     (sets processingEvent = true on NodeExecution)
  │           └─ eventSender.sendEvent(ambiance, adviseEvent, NODE_ADVISE, module, ...)
  │               → Resolves module service name from ambiance (e.g., "cd")
  │               → Looks up PmsSdkInstance from MongoDB for that service
  │               → Gets nodeAdviseEventConsumerConfig → topic name + transport (Kafka or Redis)
  │               → Publishes AdviseEvent proto to Kafka topic: pipeline_node_advise_{service}
  │                 (e.g., pipeline_node_advise_cd)
  │
  └─ ELSE (built-in advisers only):
      → nodeAdviseHelper.getResponseInCaseOfNoCustomAdviser()
          → Compute AdviserResponse synchronously (in-process)
          → handleSdkResponseEvent() with the response (no cross-service hop)
```

**Key files:**
- `modules/orchestration/src/main/java/io/harness/engine/pms/advise/publisher/NodeAdviseEventPublisherImpl.java`
- `modules/orchestration/src/main/java/io/harness/engine/pms/commons/events/PmsEventSender.java`
- `modules/orchestration/src/main/java/io/harness/engine/pms/advise/NodeAdviseHelper.java`

### 8.2 Advise Event Consumption (Module SDK Side)

The module SDK (e.g., ng-manager) has a Kafka consumer listening on the `pipeline_node_advise_{service}` topic.

#### Consumer Chain

```
NodeAdviseEventKafkaConsumer (HKafkaProtoConsumer<AdviseEvent>)
  │  Config: isNoAck=true, consumerMode=UNORDERED
  │  Topic: pipeline_node_advise_{serviceName}  (e.g., pipeline_node_advise_cd)
  │  Consumer group: {serviceName}
  │
  ├─ pollLoop() → consumer.poll()
  │     Fetches batch of ConsumerRecords from Kafka
  │
  ├─ applyFilters() → DuplicatedConsumerRecordProcessor.filter()       ← STEP 1
  │     ├─ For each record: Redis setIfAbsent("{cacheName}:{topic}:{partition}:{offset}", nonce, TTL=10min)
  │     ├─ Verification read: GET the key to confirm nonce ownership
  │     ├─ If our nonce matches → record is NEW (first time seeing it)
  │     ├─ If nonce doesn't match → record is DUPLICATE (filtered out, logged as "Duplicated record found")
  │     └─ On Redis failure → fail-open: return all records
  │
  ├─ runNoAck() (because isNoAck=true)                                  ← STEP 2
  │     ├─ acknowledgeSync()                                            ← STEP 2a: Kafka offset COMMITTED
  │     │     consumer.commitSync() — offset is now advanced past this record
  │     │     THIS IS IRREVERSIBLE: Kafka will not re-deliver this record to the consumer group
  │     │
  │     └─ FOR each record:                                             ← STEP 2b: Handler dispatched
  │           executorService.execute(() -> {
  │             messageHandler.onMessage(record.value(), headers, metricInfo)
  │           })
  │           → FIRE-AND-FORGET: no backpressure, no waiting for completion
  │           → Handler runs on a separate thread pool
  │
  └─ (poll loop continues immediately for next batch)
```

**Critical ordering issue**: In noAck mode, the dedup lock (Redis) and offset commit (Kafka) happen **BEFORE** the handler executes. If the pod crashes between commit and handler completion, the event is permanently lost — no retry from Kafka (offset committed), and no retry from another pod (dedup lock blocks it until TTL expires).

#### Handler Execution

```
NodeAdviseEventMessageListener.handleMessage(message)
  │  (extends PmsAbstractMessageListener — ALWAYS returns true, even on exception)
  │
  → NodeAdviseEventHandler.handleEvent(event, metadataMap, metricInfo)  (PmsBaseEventHandler)
      │
      ├─ gitSyncContext(event) — set up git sync branch context
      ├─ autoLogContext(event) — set up MDC logging context
      ├─ OIDC context setup (if feature flag enabled)
      │
      → handleEventWithContext(AdviseEvent event)
          │
          ├─ handleAdviseEvent(event)                                    ← PURE IN-MEMORY
          │     (default method on NodeAdviseBaseHandler interface)
          │     │
          │     ├─ Extract nodeExecutionId from ambiance
          │     ├─ Get AdviserRegistry (in-memory, Guice-injected)
          │     │
          │     └─ FOR each AdviserObtainment in event.getAdviserObtainmentsList():
          │           ├─ adviser = adviserRegistry.obtain(obtainment.getType())
          │           │     Local lookup — no network call
          │           ├─ Build AdvisingEvent with:
          │           │   ambiance, failureInfo, toStatus, fromStatus,
          │           │   retryIds, adviserParameters (deserialized from obtainment)
          │           ├─ IF adviser.canAdvise(advisingEvent):             ← Local logic
          │           │   adviserResponse = adviser.onAdviseEvent()       ← Local logic
          │           │   IF adviserResponse != null: BREAK
          │           └─ (continue to next obtainment if no match)
          │
          │     Returns: AdviserResponse (e.g., NEXT_STEP, RETRY, etc.) or null
          │
          ├─ IF adviserResponse != null:
          │   → sdkNodeExecutionService.handleAdviserResponse(ambiance, notifyId, adviserResponse)
          │       → SdkResponseEventPublisherImpl.publishEvent()          ← NETWORK CALL
          │           ├─ Builds SdkResponseEventProto:
          │           │   eventType = HANDLE_ADVISER_RESPONSE
          │           │   adviserResponseRequest = { adviserResponse, notifyId }
          │           │   ambiance = event ambiance
          │           └─ Publishes to Kafka topic: pipeline_sdk_response
          │              (or Redis stream: pipeline_sdk_response, depending on config)
          │              → This goes BACK to pipeline-service (orchestration engine)
          │
          ├─ IF adviserResponse == null:
          │   → sdkNodeExecutionService.handleAdviserResponse(..., AdviseType.UNKNOWN)
          │       → Same publish flow as above, with UNKNOWN type
          │
          └─ ON EXCEPTION:
              → log.error("Error while advising execution", ex)
              → IF notifyId is not empty:
              │   sdkNodeExecutionService.handleEventError(ADVISE, ambiance, notifyId, failureInfo)
              │   → Publishes error event back to pipeline-service
              └─ IF notifyId is empty:
                  → log.debug("Nothing will happen") — EVENT IS SILENTLY LOST
```

**Key files:**
- `clients/pipeline-service/pms-sdk/src/main/java/io/harness/pms/sdk/execution/events/node/advise/NodeAdviseEventKafkaConsumer.java`
- `clients/pipeline-service/pms-sdk/src/main/java/io/harness/pms/sdk/execution/events/node/advise/NodeAdviseEventMessageListener.java`
- `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/execution/events/node/advise/NodeAdviseEventHandler.java`
- `clients/pipeline-service/pms-sdk-core/src/main/java/io/harness/pms/sdk/core/execution/events/node/advise/NodeAdviseBaseHandler.java`
- `kafka-client/src/main/java/io/harness/kafka/consumers/HKafkaConsumer.java`
- `kafka-client/src/main/java/io/harness/kafka/common/DuplicatedConsumerRecordProcessor.java`

### 8.3 Adviser Response Processing (Back on Orchestration Side)

The `HANDLE_ADVISER_RESPONSE` event arrives back at pipeline-service on the `pipeline_sdk_response` topic.

```
SdkResponseEventRedisConsumer / SdkResponseKafkaConsumer
  → SdkResponseEventMessageListener
    → SdkResponseHandler.handleEventWithContext(event)
        │
        ├─ IF stuck monitor v2 enabled:
        │   → nodeExecutionService.markNodesProcessing(nodeExecutionId, false)
        │     (clears processingEvent flag)
        │
        → engine.handleSdkResponseEvent(event)
            → AbstractNodeExecutionStrategy.handleSdkResponseEvent()
                → SdkResponseProcessorFactory.getHandler(HANDLE_ADVISER_RESPONSE)
                    → AdviserResponseRequestProcessor.handleEvent(event)
                        → orchestrationEngine.processAdviserResponse(ambiance, adviserResponse)
```

### 8.4 processAdviserResponse

```
processAdviserResponse(ambiance, adviserResponse)
  │
  ├─ IF type == UNKNOWN or null:
  │   ├─ Set advisorsProcessed = true
  │   └─ endNodeExecution(ambiance)
  │
  └─ ELSE (valid adviser type):
      ├─ Update NodeExecution:
      │   - set adviserResponse
      │   - set advisorsProcessed = true    ← CRITICAL
      │
      ├─ IF type != RETRY and type != INTERVENTION_WAIT:
      │   → startQueuedExecutionIfAny()
      │
      └─ AdviserResponseHandler.handleAdvise(nodeExecution, adviserResponse)
```

### 8.5 Adviser Response Handlers

| Adviser Type | Handler | Action |
|-------------|---------|--------|
| `NEXT_STEP` | `NextStepHandler` | Runs the next node in the chain via `engine.runNextNode()` |
| `RETRY` | `RetryAdviserResponseHandler` | Registers RETRY interrupt with wait interval |
| `MARK_SUCCESS` | `MarkSuccessAdviseHandler` | Registers MARK_SUCCESS interrupt |
| `INTERVENTION_WAIT` | `InterventionWaitAdviserResponseHandler` | Sets status to `INTERVENTION_WAITING`, registers timeout |
| `IGNORE_FAILURE` | `IgnoreFailureAdviseHandler` | Registers IGNORE interrupt |
| `END_PLAN` | `EndPlanAdviserResponseHandler` | Registers ABORT interrupt or ends node |

### 8.6 Complete Cross-Service Adviser Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│  PIPELINE-SERVICE (orchestration engine)                                 │
│                                                                          │
│  handleStepResponseInternal()                                            │
│    → processOrQueueAdvisingEvent()                                       │
│      → NodeAdviseEventPublisherImpl.publishEvent()                       │
│        ├─ markNodesProcessing(nodeId, true)                              │
│        └─ PmsEventSender → Kafka topic: pipeline_node_advise_{module}    │
│                                                                          │
└──────────────────────────┬───────────────────────────────────────────────┘
                           │  AdviseEvent proto
                           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  MODULE SDK (e.g., ng-manager for CD steps)                              │
│                                                                          │
│  NodeAdviseEventKafkaConsumer.pollLoop()                                  │
│    │                                                                     │
│    ├─ 1. poll() — fetch records from Kafka                               │
│    │                                                                     │
│    ├─ 2. DuplicatedConsumerRecordProcessor.filter()                      │
│    │      Redis: setIfAbsent(topic:partition:offset, nonce, TTL=10min)   │
│    │      ⚠️  DEDUP LOCK ACQUIRED HERE (before handler runs)             │
│    │                                                                     │
│    ├─ 3. acknowledgeSync() — Kafka offset COMMITTED                      │
│    │      ⚠️  EVENT MARKED AS CONSUMED (before handler runs)             │
│    │                                                                     │
│    └─ 4. executorService.execute(() -> {                                 │
│             NodeAdviseEventHandler.handleEventWithContext(event)          │
│               → handleAdviseEvent() — local adviser lookup + computation │
│               → sdkNodeExecutionService.handleAdviserResponse()          │
│                   → SdkResponseEventPublisherImpl.publishEvent()         │
│           })                                                             │
│           ⚠️  FIRE-AND-FORGET: no backpressure, poll loop continues     │
│                                                                          │
└──────────────────────────┬───────────────────────────────────────────────┘
                           │  SdkResponseEventProto (HANDLE_ADVISER_RESPONSE)
                           │  Kafka topic: pipeline_sdk_response
                           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  PIPELINE-SERVICE (orchestration engine)                                 │
│                                                                          │
│  SdkResponseHandler.handleEventWithContext()                              │
│    → markNodesProcessing(nodeId, false)                                   │
│    → AdviserResponseRequestProcessor.handleEvent()                        │
│      → processAdviserResponse(ambiance, adviserResponse)                  │
│        ├─ set advisorsProcessed = true + adviserResponse on NodeExecution │
│        └─ AdviserResponseHandler.handleAdvise()                           │
│            → e.g., NextStepHandler → engine.runNextNode()                 │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### 8.7 Failure Modes in the Adviser Event Flow

The adviser event flow crosses service boundaries via Kafka, creating several points where events can be lost:

| Failure Point | What Happens | Result |
|--------------|--------------|--------|
| **After dedup lock + offset commit, before handler runs** | Pod crashes (OOM, eviction) before `executorService.execute()` runs the handler | Event permanently lost: Kafka won't re-deliver (offset committed), other pods reject as duplicate (Redis lock exists with TTL) |
| **During handler execution** | Handler thread fails (network error calling `handleAdviserResponse()`) | Exception caught in `NodeAdviseEventHandler.handleEventWithContext()` → `handleEventError()` sends error back. But if pod OOMs mid-exception-handling, error is also lost |
| **Handler completes but response publish fails** | `SdkResponseEventPublisherImpl.publishEvent()` fails (Kafka/Redis down) | Adviser computed but response never reaches pipeline-service. `advisorsProcessed` stays false |
| **Response published but not consumed** | `pipeline_sdk_response` topic consumer on pipeline-service side fails | Response lost. `advisorsProcessed` stays false |
| **Pod under severe memory pressure** | GC stop-the-world pauses prevent handler thread from running, eventual OOM | Same as first scenario: dedup lock + offset committed, but work never done |
| **Dedup TTL expires, rebalance happens** | If pod crash + recovery takes > 10min (dedup TTL), the event may be re-processable, but Kafka offset is already committed past it | Still lost unless consumer is configured to seek back |

**Why noAck mode is dangerous for adviser events**: The `isNoAck=true` configuration in `NodeAdviseEventKafkaConsumer` means offset is committed **synchronously before handler execution** (see `HKafkaConsumer.runNoAck()` line 298). This is a fire-and-forget pattern designed for high throughput where occasional message loss is acceptable. However, for adviser events, a lost message means a permanently stuck execution.

**The stuck execution monitor** (`StuckExecutionDetectionServiceImpl`) is the safety net: it detects nodes with `advisorsProcessed == false` beyond a threshold and can trigger recovery. But this relies on the monitor running and being able to identify the stuck state.

---

## 9. endNodeExecution - Parent Notification

After advisers process (or if there are none), the node concludes and notifies its parent.

```
endNodeExecution(ambiance, nodeExecution, outcomeRefs)
  │
  ├─ Fallback: if advisorsProcessed == false, set it to true (with warning log)
  │
  ├─ IF notifyId is not empty (has a parent):
  │   ├─ Build StepResponseNotifyData:
  │   │   { nodeUuid, status, failureInfo, identifier, nodeExecutionId, adviserResponse }
  │   ├─ startQueuedExecutionIfAny()
  │   └─ waitNotifyEngine.doneWith(notifyId, responseData)
  │       → Parent's registered callback fires
  │       → Parent resumes execution
  │
  └─ ELSE (root node - no parent):
      ├─ startQueuedExecutionIfAny()
      └─ orchestrationEngine.endNodeExecution()
          → Plan execution concludes
```

### NotifyId Mechanism

The `notifyId` is the linking mechanism between parent and child nodes:

1. When a child node is created, `notifyId` is set to the child's own `runtimeId`
2. The parent registers a callback (e.g., `EngineResumeCallback`) waiting on the child's `runtimeId`
3. When the child calls `waitNotifyEngine.doneWith(notifyId, responseData)`, the parent's callback fires
4. The parent resumes with the child's response data

```
Parent creates child:
  notifyId = child.runtimeId
  Parent registers: waitNotifyEngine.waitForAllOn(callback, child.runtimeId)

Child completes:
  waitNotifyEngine.doneWith(child.notifyId, StepResponseNotifyData)
  → Parent's callback.notify(responseData)
  → Parent resumes
```

---

## 10. WaitNotifyEngine Infrastructure

The `WaitNotifyEngine` is the core infrastructure for async callback coordination.

### Key Collections (MongoDB)

| Collection | Purpose |
|------------|---------|
| `waitInstances` | Registered callbacks with correlation IDs |
| `notifyResponses` | Responses from delegates/external systems (TTL: 21 days) |

### Flow

```
Registration:
  waitNotifyEngine.waitForAllOn(publisherName, callback, correlationIds, timeout)
  → Creates WaitInstance in MongoDB:
      { uuid, callback, waitingOnCorrelationIds: [id1, id2, ...] }

Notification:
  waitNotifyEngine.doneWith(correlationId, responseData)
  → Save NotifyResponse to MongoDB
  → Remove correlationId from WaitInstance.waitingOnCorrelationIds
  → IF waitingOnCorrelationIds is empty (all done):
      → sendNotification() → publish to Redis
      → PmsNotifyEventConsumerRedis → NotifyEventListenerHelper
      → processWaitInstanceCallback()
          → Inject dependencies into callback
          → callback.notify(responseDataMap)
          → Delete WaitInstance
```

### Callback Types

| Callback | Used By | Purpose |
|----------|---------|---------|
| `EngineResumeCallback` | Task completion, child completion | Resume parent node execution |
| `AsyncSdkResumeCallback` | Async step completion | Resume async step with all callback responses |
| `AsyncSdkSingleCallback` | Individual async callback | Process individual response (multi-callback) |
| `MaxConcurrentChildCallback` | Children concurrency | Start next child when current one completes |
| `WaitForExecutionInputCallback` | Execution inputs | Resume node when user provides input |

---

## 11. Complete Flow Diagram

```
                        ┌─────────────────────────┐
                        │   InitiateNodeEvent      │
                        │   (Kafka topic)          │
                        └────────┬────────────────┘
                                 │
                        ┌────────▼────────────────┐
                        │  InitiateNodeHandler     │
                        │  CREATE → runNode()      │
                        │  START → queueOrStart()  │
                        └────────┬────────────────┘
                                 │
                        ┌────────▼────────────────┐
                        │  startExecution()        │
                        │  ├─ resolveParameters    │
                        │  ├─ whenCondition check  │
                        │  ├─ executionInput wait   │
                        │  ├─ facilitator check    │
                        │  └─ NodeStartHelper      │
                        └────────┬────────────────┘
                                 │
                    NodeStartEvent (to module SDK)
                                 │
                        ┌────────▼────────────────┐
                        │  NodeStartEventHandler   │
                        │  (SDK side)              │
                        │  → ExecutableProcessor   │
                        │  → Strategy.start()      │
                        └────────┬────────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                   │
      ┌───────▼──────┐  ┌───────▼──────┐   ┌───────▼──────┐
      │ AsyncStrategy │  │ TaskStrategy │   │ Child/ren    │
      │ executeAsync()│  │ obtainTask() │   │ Strategy     │
      │ → callbackIds│  │ → TaskRequest│   │ → childIds   │
      └───────┬──────┘  └───────┬──────┘   └───────┬──────┘
              │                  │                   │
    SDK Response Events (back to orchestration)
              │                  │                   │
      ┌───────▼──────┐  ┌───────▼──────┐   ┌───────▼──────┐
      │ WaitInstance  │  │ QUEUE_TASK   │   │ SPAWN_CHILD  │
      │ (callbackIds)│  │ → delegate   │   │ SPAWN_CHILDREN│
      │ → wait...    │  │ → wait...    │   │ → initiate   │
      └───────┬──────┘  └───────┬──────┘   └───────┬──────┘
              │                  │                   │
         [External/         [Delegate          [Children
          Delegate           completes]          complete]
          notifies]              │                   │
              │                  │                   │
      ┌───────▼──────────────────▼───────────────────▼──────┐
      │              Callback.notify()                       │
      │  (AsyncSdkResumeCallback / EngineResumeCallback)    │
      └───────────────────────┬─────────────────────────────┘
                              │
                     Strategy.resume()
                     → step.handleXxxResponse()
                     → Returns StepResponse
                              │
                     HANDLE_STEP_RESPONSE event
                              │
                    ┌─────────▼───────────┐
                    │ handleStepResponse   │
                    │ Internal()           │
                    ├─ finalize status     │
                    ├─ save outcomes       │
                    └─────────┬───────────┘
                              │
                    ┌─────────▼───────────┐
                    │ Adviser Processing   │
                    │ processOrQueue       │
                    │ AdvisingEvent()      │
                    └─────────┬───────────┘
                              │
                    ┌─────────▼───────────┐
                    │ processAdviser       │
                    │ Response()           │
                    │ ├─ set advisors      │
                    │ │   Processed=true   │
                    │ └─ handler.handle()  │
                    └─────────┬───────────┘
                              │
                    ┌─────────▼───────────┐
                    │ endNodeExecution()   │
                    │ → doneWith(notifyId) │
                    │ → parent resumes    │
                    └─────────────────────┘
```

---

## 12. Debugging Stuck Executions

### Strategy: Find the Lowest Running NodeExecution

When debugging a stuck execution, always start by finding the lowest-level NodeExecution that is in a non-terminal status. This is the node where execution is actually stuck.

### Non-Terminal Statuses

These statuses indicate a node is still "in progress":

| Status | Meaning |
|--------|---------|
| `RUNNING` | Actively executing |
| `QUEUED` | Waiting to be started |
| `ASYNC_WAITING` | Waiting for async callback |
| `TASK_WAITING` | Waiting for delegate task |
| `PAUSING` | In process of pausing |
| `PAUSED` | Paused by user |
| `INTERVENTION_WAITING` | Waiting for manual intervention |
| `APPROVAL_WAITING` | Waiting for approval |
| `RESOURCE_WAITING` | Waiting for resource constraint |
| `WAIT_STEP_RUNNING` | In a wait step |
| `INPUT_WAITING` | Waiting for execution input |
| `QUEUED_STEP_LIMIT_REACHED` | Waiting due to step concurrency limit |

### Terminal Statuses

| Status | Meaning |
|--------|---------|
| `SUCCEEDED` | Completed successfully |
| `FAILED` | Failed |
| `SKIPPED` | Skipped (when condition false) |
| `ABORTED` | Aborted by user or system |
| `EXPIRED` | Timed out |
| `IGNORE_FAILED` | Failed but marked as ignored |
| `DISCONTINUING` | Being discontinued |

### The `advisorsProcessed` Field

This boolean field on NodeExecution is **critical** for stuck execution detection:

- `advisorsProcessed = false` (or unset) → Advisers have NOT yet processed the step response
- `advisorsProcessed = true` → Advisers have processed (or no advisers exist)

**Where it gets set to `true`**:
1. `concludeExecution()` - when no advisers exist (merged with status update)
2. `processAdviserResponse()` - when adviser response arrives (along with setting `adviserResponse`)
3. `endNodeExecution()` - fallback if still false (with warning log)
4. `finalizeNodeWithStepResponse()` - for no-adviser path

**Stuck execution detection logic** (from `StuckExecutionDetectionServiceImpl`):
- Node has executable responses + `advisorsProcessed == false` → **POSSIBLY_NOT_STUCK** (advisers processing)
- Node has executable responses + `advisorsProcessed == true` → **STUCK** (advisers processed but no progress)
- Node has no executable responses → **STUCK** (never received step response)

### Common Stuck Scenarios

| Scenario | Root Cause | How to Detect |
|----------|-----------|---------------|
| Status update failure | MongoDB write failed during `handleStepResponsePreAdviser` | `advisorsProcessed` is false, no `adviserResponse` |
| Adviser response lost | Kafka/Redis event dropped | `advisorsProcessed` is false, step has final status |
| Delegate never returned | Delegate crashed, network partition | Status is `TASK_WAITING`, no NotifyResponse for taskId |
| Async callback missing | External system never notified | Status is `ASYNC_WAITING`, WaitInstance still exists |
| Child never completed | Child node stuck (recurse down) | Parent waiting, child in non-terminal status |
| MaxConcurrency stuck | Cursor not advancing | `ConcurrentChildInstance.cursor` < total, stuck child |

### Debugging Steps

1. **Find the planExecution** - Check overall status and last updated time
2. **Find all nodeExecutions** - Filter by planExecutionId, sort by startTs
3. **Identify non-terminal nodes** - Filter for non-terminal statuses
4. **Find the deepest stuck node** - The leaf-level non-terminal node is where the problem is
5. **Check advisorsProcessed** - If false on a node with final status, adviser flow is stuck
6. **Check WaitInstances** - If node is waiting, check what correlationIds are pending
7. **Check NotifyResponses** - If delegate completed, response should exist
8. **Check GCP logs** - Search by nodeExecutionId for the last activity

### Key MongoDB Queries

```javascript
// 1. Plan execution state
db.planExecutions.findOne(
  { _id: "<planExecutionId>" },
  { _id: 1, status: 1, startTs: 1, endTs: 1, lastUpdatedAt: 1 }
)

// 2. All node executions for a plan
db.nodeExecutions.find(
  { planExecutionId: "<planExecutionId>" },
  { _id: 1, name: 1, stepType: 1, status: 1, startTs: 1, endTs: 1,
    lastUpdatedAt: 1, parentId: 1, notifyId: 1, advisorsProcessed: 1 }
).sort({ startTs: 1 })

// 3. Non-terminal nodes only
db.nodeExecutions.find(
  { planExecutionId: "<planExecutionId>",
    status: { $in: ["RUNNING", "QUEUED", "ASYNC_WAITING", "TASK_WAITING",
                     "INTERVENTION_WAITING", "APPROVAL_WAITING",
                     "RESOURCE_WAITING", "INPUT_WAITING", "WAIT_STEP_RUNNING"] } },
  { _id: 1, name: 1, stepType: 1, status: 1, lastUpdatedAt: 1, advisorsProcessed: 1 }
)

// 4. Check WaitInstance for a node
db.waitInstances.find(
  { "waitingOnCorrelationIds": "<nodeExecutionId_or_taskId>" }
)

// 5. Check NotifyResponse
db.notifyResponses.findOne(
  { _id: "<taskId_or_callbackId>" }
)
```

---

## 13. Feature Flags Affecting Execution Flow

| Feature Flag | Purpose | Impact if Disabled |
|-------------|---------|-------------------|
| `PIPE_FIX_STUCK_EXECUTION_AFTER_TRANSITION_FAILURE` | Handle null from status update in handleStepResponseInternal | Stuck execution if status update fails |
| `PIPE_DISABLE_MERGE_WRITE_ON_END_NODE_EXECUTION` | Skip merging advisorsProcessed with status update | May delay advisorsProcessed being set |
| `PIPE_DISABLE_SKIP_FETCH_ON_END_NODE_EXECUTION` | Force fresh DB fetch in endNodeExecution | Performance impact, but more correct |
| `PIPE_STEP_CONCURRENCY_ENABLED` | Enable step-level concurrency limits | Steps may queue with QUEUED_STEP_LIMIT_REACHED |
| `PIPE_BATCHING_IN_SPAWN_CHILDREN_REQUEST_PROCESSING` | Use batch initiation for children | Different code path for children creation |
| `PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2` | Disable v2 stuck execution monitor | Falls back to v1 monitoring |

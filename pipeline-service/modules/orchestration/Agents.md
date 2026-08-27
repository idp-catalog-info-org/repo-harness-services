# orchestration - Agents.md

## Purpose
Core execution engine for pipeline orchestration. Manages node execution, state machine, facilitators, advisers, and the complete execution lifecycle.

## Quick Navigation

| Looking For | Location |
|-------------|----------|
| Node Execution | `src/main/java/io/harness/engine/executions/node/` |
| Plan Execution | `src/main/java/io/harness/engine/executions/plan/` |
| State Machine | `src/main/java/io/harness/engine/pms/execution/strategy/` |
| Facilitators | `src/main/java/io/harness/engine/facilitation/` |
| Advisers | `src/main/java/io/harness/engine/pms/advise/` |
| Interrupts | `src/main/java/io/harness/engine/interrupts/` |
| Progress Updates | `src/main/java/io/harness/engine/progress/` |
| Expression Engine | `src/main/java/io/harness/engine/expressions/` |
| Event Handlers | `src/main/java/io/harness/engine/observers/` |
| Resume/Callback | `src/main/java/io/harness/engine/pms/resume/` |

## Critical Files

| File | Purpose |
|------|---------|
| `engine/executions/node/NodeExecutionService.java` | Node lifecycle management |
| `engine/executions/plan/PlanExecutionService.java` | Plan-level orchestration |
| `engine/pms/execution/strategy/PlanExecutionStrategy.java` | Execution strategy interface |
| `engine/pms/execution/strategy/NodeExecutionStrategy.java` | Node execution strategy |
| `engine/facilitation/FacilitatorService.java` | Step facilitation |
| `engine/pms/advise/AdviseHandler.java` | Adviser handling |
| `engine/interrupts/InterruptService.java` | Interrupt management |

## Package Map

| Package | What It Does |
|---------|--------------|
| `engine/executions/node/` | Node state management, status updates |
| `engine/executions/plan/` | Plan lifecycle, execution metadata |
| `engine/pms/execution/strategy/` | Execution strategies (plan, node, identity) |
| `engine/pms/execution/handlers/` | Execution event handlers |
| `engine/facilitation/` | Facilitator execution and management |
| `engine/pms/advise/` | Adviser execution (on success, failure, timeout) |
| `engine/pms/resume/` | Resume after async operations |
| `engine/interrupts/` | Abort, pause, expire, mark-success handlers |
| `engine/progress/` | Progress tracking and callbacks |
| `engine/expressions/` | Runtime expression evaluation |
| `engine/observers/` | Event observation and handling |
| `engine/pms/tasks/` | Task execution and tracking |
| `engine/pms/data/` | Data passing between nodes |
| `engine/utils/` | Execution utilities |

## Key Concepts

### Node Execution Lifecycle
```
QUEUED → RUNNING → [ASYNC_WAITING] → SUCCEEDED/FAILED/EXPIRED
                 ↓
          [INTERVENTION_WAITING]
                 ↓
          [APPROVAL_WAITING]
```

### Execution Strategy
- **PlanExecutionStrategy**: Orchestrates entire plan execution
- **NodeExecutionStrategy**: Handles individual node execution
- **IdentityNodeExecutionStrategy**: For identity/passthrough nodes

### Facilitators
Facilitators determine how a step should execute:
| Facilitator | Purpose |
|-------------|---------|
| `SyncFacilitator` | Synchronous execution |
| `AsyncFacilitator` | Asynchronous with callback |
| `TaskFacilitator` | Delegate task execution |
| `ChildFacilitator` | Execute child nodes |
| `ChildChainFacilitator` | Chain of child nodes |

### Advisers
Advisers provide guidance after step completion:
| Adviser | Purpose |
|---------|---------|
| `OnSuccessAdviser` | What to do on success |
| `OnFailAdviser` | What to do on failure |
| `RetryAdviser` | Retry logic |
| `ManualInterventionAdviser` | Pause for manual action |
| `RollbackAdviser` | Trigger rollback |
| `NextStepAdviser` | Proceed to next step |

### Interrupts
| Interrupt Type | Handler |
|----------------|---------|
| Abort | `AbortInterruptHandler` |
| Pause | `PauseInterruptHandler` |
| Resume | `ResumeInterruptHandler` |
| Expire | `ExpireInterruptHandler` |
| Mark Success | `MarkSuccessInterruptHandler` |
| Mark Failed | `MarkFailedInterruptHandler` |
| Mark Expired | `MarkExpiredInterruptHandler` |
| Retry | `RetryInterruptHandler` |

## Key Classes by Responsibility

### Node Execution
| Class | Responsibility |
|-------|----------------|
| `NodeExecutionServiceImpl` | Node state CRUD, status updates |
| `NodeExecutionEventPublisher` | Publish node events |
| `NodeStartHelper` | Initialize node execution |
| `NodeResumeHelper` | Resume paused nodes |

### Plan Execution
| Class | Responsibility |
|-------|----------------|
| `PlanExecutionServiceImpl` | Plan lifecycle management |
| `PlanExecutionMetadataService` | Execution metadata |
| `PlanService` | Plan CRUD operations |

### Expression Evaluation
| Class | Responsibility |
|-------|----------------|
| `EngineExpressionService` | Expression resolution |
| `ExpressionEvaluatorProvider` | Provides evaluators |
| `NodeExecutionsCache` | Cache for expression context |

### Progress & Callbacks
| Class | Responsibility |
|-------|----------------|
| `ProgressService` | Track step progress |
| `EngineResumeCallback` | Handle async callbacks |
| `NodeResumeHelper` | Resume from callbacks |

## Business Logic Patterns

| When You Need To... | Look In... |
|---------------------|------------|
| Start node execution | `engine/executions/node/NodeExecutionService` |
| Handle step completion | `engine/pms/advise/` |
| Process interrupts | `engine/interrupts/` |
| Resume async steps | `engine/pms/resume/` |
| Evaluate expressions | `engine/expressions/` |
| Track progress | `engine/progress/` |
| Handle failures | `engine/pms/advise/handlers/` |
| Manage retries | Look for `RetryAdviser`, `RetryInterruptHandler` |

## Event Flow

```
1. Plan Execution Started
   └── PlanExecutionStrategy.start()
       └── Start root nodes

2. Node Execution
   └── NodeExecutionStrategy.runNode()
       └── Facilitator determines execution mode
           └── Execute step logic
               └── Publish progress

3. Step Completion
   └── Adviser determines next action
       └── NextStepAdviser → continue
       └── OnFailAdviser → handle failure
       └── RollbackAdviser → trigger rollback

4. Async Callback
   └── EngineResumeCallback.notify()
       └── NodeResumeHelper.resume()
           └── Continue execution
```

## Configuration

| Config | Purpose |
|--------|---------|
| `orchestrationPoolConfig` | Thread pool for orchestration |
| `executorPoolConfig` | Thread pool for step execution |
| `interruptConfig` | Interrupt handling settings |
| `progressConfig` | Progress update intervals |

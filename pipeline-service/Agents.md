# Pipeline Service

## Purpose
Pipeline orchestration engine that converts pipeline YAML into execution plans and manages the complete pipeline lifecycle including triggers, approvals, barriers, and notifications.

## Quick Navigation

| Looking For | Location |
|-------------|----------|
| Pipeline CRUD | `service/src/main/java/io/harness/pms/pipeline/` |
| Execution Logic | `service/src/main/java/io/harness/pms/plan/execution/` |
| Plan Creation | `service/src/main/java/io/harness/pms/plan/creation/` |
| Input Sets | `service/src/main/java/io/harness/pms/ngpipeline/inputset/` |
| Triggers | `modules/ng-triggers/` |
| Approvals | `service/src/main/java/io/harness/pms/approval/` |
| Barriers | `service/src/main/java/io/harness/pms/barriers/` |
| Resource Constraints | `service/src/main/java/io/harness/pms/resourceconstraints/` |
| REST APIs | `service/src/main/java/io/harness/pms/pipeline/resources/` |
| Event Handlers | `service/src/main/java/io/harness/pms/event/handlers/` |
| Node Execution | `modules/orchestration/` |
| Step Implementations | `modules/orchestration-steps/` |
| Notifications | `service/src/main/java/io/harness/pms/notification/` |
| Governance/OPA | `service/src/main/java/io/harness/pms/governance/` |
| Git Sync | `service/src/main/java/io/harness/pms/pipeline/gitsync/` |

## Critical Files (Start Here)

| File | Purpose |
|------|---------|
| `service/src/main/java/io/harness/PipelineServiceApplication.java` | Application bootstrap, module registration, filter setup |
| `service/src/main/java/io/harness/pms/pipeline/service/PMSPipelineServiceImpl.java` | Pipeline CRUD operations |
| `service/src/main/java/io/harness/pms/plan/execution/PlanExecutionServiceImpl.java` | Execution lifecycle management |
| `service/src/main/java/io/harness/pms/plan/execution/ExecutionGraphServiceImpl.java` | Builds execution DAG from pipeline |
| `service/src/main/java/io/harness/pms/pipeline/resources/PipelineResourceImpl.java` | Main REST API for pipelines |
| `service/src/main/java/io/harness/pms/ngpipeline/inputset/resources/InputSetResourcePMSImpl.java` | Input set REST API |
| `config/config.yml` | Runtime configuration (ports 12001/12002) |

## Package Map

| Package | What It Does |
|---------|--------------|
| `pms/pipeline/` | Pipeline entity, CRUD, validation, metadata |
| `pms/pipeline/service/` | Pipeline service interfaces and implementations |
| `pms/pipeline/resources/` | REST endpoints for pipeline operations |
| `pms/pipeline/validation/` | Async pipeline validation, pre-flight checks |
| `pms/pipeline/gitsync/` | Git-backed pipeline storage |
| `pms/plan/creation/` | Converts YAML to execution plan |
| `pms/plan/execution/` | Manages execution lifecycle, status updates |
| `pms/ngpipeline/inputset/` | Input set management and merging |
| `pms/approval/` | Approval workflows (Jira, ServiceNow, Custom) |
| `pms/barriers/` | Synchronization barriers between stages |
| `pms/resourceconstraints/` | Resource-based execution throttling |
| `pms/triggers/` | Pipeline trigger management |
| `pms/event/` | Event processing and handlers |
| `pms/notification/` | Execution notifications |
| `pms/governance/` | OPA policy enforcement |
| `pms/preflight/` | Pre-execution validation |
| `pms/template/` | Template reference resolution |

## Key Classes by Responsibility

### Pipeline Management
| Class | Responsibility |
|-------|----------------|
| `PMSPipelineServiceImpl` | Create, update, delete, get pipelines |
| `PMSPipelineServiceHelper` | Pipeline operations helper |
| `PipelineMetadataServiceImpl` | Pipeline metadata tracking |
| `PMSYamlSchemaServiceImpl` | YAML schema generation |

### Execution Engine
| Class | Responsibility |
|-------|----------------|
| `PlanExecutionServiceImpl` | Start, interrupt, retry executions |
| `ExecutionGraphServiceImpl` | Build execution graph from plan |
| `ExpressionEvaluatorServiceImpl` | Runtime expression evaluation |
| `ExecutionDetailsService` | Query execution details |

### Input Sets
| Class | Responsibility |
|-------|----------------|
| `PMSInputSetServiceImpl` | Input set CRUD |
| `InputSetMergeHelper` | Merge multiple input sets |
| `InputSetValidationHelper` | Validate input set against pipeline |

### Approvals & Barriers
| Class | Responsibility |
|-------|----------------|
| `ApprovalServiceImpl` | Approval workflow management |
| `JiraApprovalHelperServiceImpl` | Jira-based approvals |
| `ServiceNowApprovalHelperServiceImpl` | ServiceNow-based approvals |
| `BarrierServiceImpl` | Stage synchronization barriers |
| `ResourceRestraintServiceImpl` | Resource-based throttling |

### Event Handling
| Class | Responsibility |
|-------|----------------|
| `OrchestrationExecutionPmsEventHandlerRegistrar` | Registers all event handlers |
| `PipelineEventNotificationHandler` | Pipeline completion notifications |
| `StageStartNotificationHandler` | Stage start notifications |

## Business Logic Patterns

| When You Need To... | Look In... |
|---------------------|------------|
| Validate a pipeline | `pms/pipeline/validation/` |
| Create execution plan from YAML | `pms/plan/creation/` |
| Track execution status | `pms/plan/execution/PlanExecutionService` |
| Evaluate expressions at runtime | `pms/plan/execution/ExpressionEvaluatorService` |
| Handle pipeline events | `pms/event/handlers/` |
| Send notifications | `pms/notification/` |
| Enforce policies | `pms/governance/OpaService` |
| Manage triggers | `modules/ng-triggers/` |
| Handle approvals | `pms/approval/` |
| Sync with Git | `pms/pipeline/gitsync/` |

## Modules (Separate Codebases)

| Module | Purpose | Has Own Agents.md |
|--------|---------|-------------------|
| `modules/orchestration/` | Core execution engine, node state machine | Yes |
| `modules/orchestration-steps/` | Step implementations (barrier, approval, etc.) | Yes |
| `modules/orchestration-beans/` | Domain models for orchestration | No |
| `modules/orchestration-visualization/` | Execution visualization | No |
| `modules/ng-triggers/` | Trigger system (webhook, scheduled, artifact) | Yes |
| `modules/pms-contracts/` | Protocol buffer contracts | No |
| `modules/retention/` | Data retention policies | No |

## REST API Entry Points

| Resource | Path Pattern | Purpose |
|----------|--------------|---------|
| `PipelineResourceImpl` | `/pipelines` | Pipeline CRUD |
| `InputSetResourcePMSImpl` | `/inputSets` | Input set management |
| `ExecutionDetailsResource` | `/executions` | Execution queries |
| `ExecutionInputResource` | `/executionInputs` | Runtime inputs |
| `ApprovalResourceImpl` | `/approvals` | Approval operations |
| `PMSBarrierResourceImpl` | `/barriers` | Barrier status |
| `PMSResourceConstraintResourceImpl` | `/resourceConstraints` | Constraint queries |
| `OpaResource` | `/governance` | Policy evaluation |

## Configuration

| Config Section | Purpose |
|----------------|---------|
| `mongoConfig` | MongoDB connection |
| `eventsFrameworkConfiguration` | Kafka/Redis events |
| `pmsSdkConfiguration` | PMS SDK settings |
| `gitSdkConfiguration` | Git sync settings |
| `iteratorConfig` | Background job intervals |
| `threadPoolConfig` | Execution thread pools |


## Detailed Documentation

See `docs/` folder for comprehensive guides:

| Document | Description |
|----------|-------------|
| **[PIPELINE_EXECUTION_FLOW.md](docs/PIPELINE_EXECUTION_FLOW.md)** | Complete flow from API entry → Plan creation → Execution start |
| **[NODE_EXECUTION_FLOW.md](docs/NODE_EXECUTION_FLOW.md)** | Node execution orchestration: strategies (async/task/child/children), advisers, callbacks, stuck execution debugging |
| **[POST_EXECUTION_FLOW.md](docs/POST_EXECUTION_FLOW.md)** | Post-execution lifecycle, events, state transitions, notifications |
| **[PLAN_CREATION_FLOW_ANALYSIS.md](docs/PLAN_CREATION_FLOW_ANALYSIS.md)** | Detailed plan creation process and YAML to DAG conversion |
| **[SELECTIVE_STAGE_EXECUTION.md](docs/SELECTIVE_STAGE_EXECUTION.md)** | How selective stage execution works |
## Common Tasks

### Understanding Pipeline Execution

1. **How does a pipeline execute?** → Read `docs/PIPELINE_EXECUTION_FLOW.md`
2. **What happens after execution starts?** → Read `docs/POST_EXECUTION_FLOW.md`
3. **How are events published?** → See `docs/POST_EXECUTION_FLOW.md` sections:
   - Event Publishing (line 602)
   - Observer Pattern (line 770)
4. **How do notifications work?** → See `docs/POST_EXECUTION_FLOW.md#notification-flow` (line 822)

### Debugging Executions

**Trace an execution:**
```javascript
// Get execution ID from API response
// MongoDB queries:
db.planExecutions.findOne({_id: "exec_id"})
db.nodeExecutions.find({planExecutionId: "exec_id"})
db.outcomeInstances.find({nodeExecutionId: "node_exec_id"})
```

**Common issues:**
- **Stuck QUEUED** → Check queue consumer, concurrency limits
- **Access Denied** → RBAC issue (check `@NGAccessControlCheck`)
- **Template Error** → Template resolution failed
- **Draft Error** → Cannot execute draft pipelines

### Working with Code

**Add new step type:**
1. Define step in SDK module
2. Implement facilitator in `modules/orchestration/`
3. Register in SDK
4. See existing steps in `modules/orchestration-steps/`

**Add new event handler:**
1. Implement event handler interface
2. Register in `OrchestrationExecutionPmsEventHandlerRegistrar`
3. See examples in `service/.../pms/event/handlers/`

**Modify execution flow:**
1. Read relevant docs first to understand current flow
2. Check execution mode in `docs/POST_EXECUTION_FLOW.md#execution-modes`
3. Update appropriate facilitator or strategy

---

## Execution Modes

| Mode | Status | Use Case | Details |
|------|--------|----------|---------|
| SYNC | RUNNING | Shell script, HTTP | docs/POST_EXECUTION_FLOW.md:198 |
| ASYNC | ASYNC_WAITING | HTTP long-poll, Wait | docs/POST_EXECUTION_FLOW.md:226 |
| TASK | TASK_WAITING | K8s deploy, Terraform | docs/POST_EXECUTION_FLOW.md:276 |
| CHILD | RUNNING | Pipeline stage | docs/POST_EXECUTION_FLOW.md:324 |
| CHILDREN | RUNNING | Parallel stages, Matrix | docs/POST_EXECUTION_FLOW.md:374 |

See `docs/POST_EXECUTION_FLOW.md#execution-modes` for complete list and details.

## Build & Run

```bash
# Build pipeline-service
make build t=pipeline-service

# Run locally
make run t=pipeline-service

# Run tests
make test f=pipeline-service/service/src/test/java/io/harness/pms/

# Ports
# - API: 12001
# - Admin: 12002
```


## Key Patterns

### Event Publishing
```java
// Publish event
pmsEventSender.sendEvent(ambiance, event, PmsEventCategory.NODE_START, module, true, true);
```

### State Updates
```java
// Update node status atomically
nodeExecutionService.updateStatusWithOps(nodeExecutionId, Status.SUCCESS, ops -> {
    ops.set(NodeExecutionKeys.endTs, currentTimeMillis());
});
```

### Callback Registration (Async)
```java
// Register callback for async operation
AsyncExecutableResponse.newBuilder()
    .addCallbackIds(callbackId)
    .build();

// Later: notify completion
waitNotifyEngine.doneWith(callbackId, responseData);
```

---

## Tips

- **Always read the docs first** - The detailed docs in `docs/` will save you hours
- **Trace executions in MongoDB** - Use execution ID to query collections
- **Follow event flow** - Events drive the entire execution lifecycle
- **Check execution mode** - Different modes have different flows
- **Use observers** - Hook into execution lifecycle via observers
- **Understand async** - Most executions are async, understand callbacks

---

## Getting Help

1. Read relevant documentation in `docs/`
2. Search code for similar implementations
3. Check MongoDB for execution state
4. Trace event flow in logs
5. Review protobuf contracts in `modules/pms-contracts/`

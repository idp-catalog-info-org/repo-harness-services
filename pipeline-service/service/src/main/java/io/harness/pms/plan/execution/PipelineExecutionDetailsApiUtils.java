/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.ABORTED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.ABORTEDBYFREEZE;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.APPROVALREJECTED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.APPROVALWAITING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.APPROVAL_REJECTED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.APPROVAL_WAITING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.ASYNCWAITING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.DISCONTINUING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.ERRORED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.EXPIRED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.FAILED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.FREEZE_FAILED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.IGNOREFAILED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.INPUTWAITING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.INTERVENTIONWAITING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.INTERVENTION_WAITING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.NOTSTARTED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.NOT_STARTED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.NO_OP;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.PASSEDWITHWARNING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.PAUSED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.PAUSING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.QUEUED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.QUEUEDEXECUTIONCONCURRENCYREACHED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.QUEUEDGLOBALINFRACAPACITYREACHED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.QUEUEDLICENSELIMITREACHED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.QUEUED_PLAN_CREATION;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.RESOURCEWAITING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.RUNNING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.SKIPPED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.SUCCEEDED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.SUCCESS;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.SUSPENDED;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.TASKWAITING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.TIMEDWAITING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.WAITING;
import static io.harness.spec.server.pipeline.v1.model.ExecutionStatus.WAITSTEPRUNNING;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateInfo;
import io.harness.beans.ExecutionGraph;
import io.harness.beans.ExecutionNode;
import io.harness.beans.strategy.RepresentationStrategy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.FailureInfoDTO;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceResponseDTO;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.PolicyMetadata;
import io.harness.interrupts.InterruptConfig;
import io.harness.interrupts.InterruptEffectDTO;
import io.harness.interrupts.ManualIssuer;
import io.harness.interrupts.RetryInterruptConfig;
import io.harness.interrupts.TriggerIssuer;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionErrorInfo;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.plan.BuildInfo;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.RerunInfo;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.EdgeLayoutListDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionSummaryDTO;
import io.harness.spec.server.pipeline.v1.model.AbortedBy;
import io.harness.spec.server.pipeline.v1.model.AdviserIssuer;
import io.harness.spec.server.pipeline.v1.model.AsyncChainExecutableResponse;
import io.harness.spec.server.pipeline.v1.model.AsyncExecutableResponse;
import io.harness.spec.server.pipeline.v1.model.Child;
import io.harness.spec.server.pipeline.v1.model.ChildChainExecutableResponse;
import io.harness.spec.server.pipeline.v1.model.ChildExecutableResponse;
import io.harness.spec.server.pipeline.v1.model.ChildExecutionDetails;
import io.harness.spec.server.pipeline.v1.model.ChildrenExecutableResponse;
import io.harness.spec.server.pipeline.v1.model.DynamicExecutionDetailsResponseBody;
import io.harness.spec.server.pipeline.v1.model.ExecutionNodeAdjacencyList;
import io.harness.spec.server.pipeline.v1.model.ExecutionStatus;
import io.harness.spec.server.pipeline.v1.model.ExecutionTriggerInfo;
import io.harness.spec.server.pipeline.v1.model.ExpressionBlock;
import io.harness.spec.server.pipeline.v1.model.FailureInfo;
import io.harness.spec.server.pipeline.v1.model.ForMetadata;
import io.harness.spec.server.pipeline.v1.model.GitDetails;
import io.harness.spec.server.pipeline.v1.model.GovernanceMetadata;
import io.harness.spec.server.pipeline.v1.model.IssuedBy;
import io.harness.spec.server.pipeline.v1.model.OneOfExecutionNodeExecutableResponsesItems;
import io.harness.spec.server.pipeline.v1.model.ParentStageInfo;
import io.harness.spec.server.pipeline.v1.model.PipelineExecutionDetailsResponseBody;
import io.harness.spec.server.pipeline.v1.model.PolicySetMetadata;
import io.harness.spec.server.pipeline.v1.model.SkipInfo;
import io.harness.spec.server.pipeline.v1.model.SkipTaskExecutableResponse;
import io.harness.spec.server.pipeline.v1.model.SyncExecutableResponse;
import io.harness.spec.server.pipeline.v1.model.Tag;
import io.harness.spec.server.pipeline.v1.model.TaskChainExecutableResponse;
import io.harness.spec.server.pipeline.v1.model.TaskExecutableResponse;
import io.harness.spec.server.pipeline.v1.model.TimeoutIssuer;
import io.harness.spec.server.pipeline.v1.model.TriggerType;
import io.harness.yaml.core.NGLabel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class PipelineExecutionDetailsApiUtils {
  public PipelineExecutionDetailsResponseBody toPipelineExecutionDetailsResponseBody(
      PipelineExecutionDetailDTO pipelineExecutionDetailDTO) {
    PipelineExecutionDetailsResponseBody pipelineExecutionDetailsResponseBody =
        new PipelineExecutionDetailsResponseBody();
    pipelineExecutionDetailsResponseBody.setPipelineExecutionSummary(
        toPipelineExecutionSummaryDTO(pipelineExecutionDetailDTO.getPipelineExecutionSummary()));
    pipelineExecutionDetailsResponseBody.setExecutionGraph(
        toExecutionGraph(pipelineExecutionDetailDTO.getExecutionGraph()));
    pipelineExecutionDetailsResponseBody.setChildGraph(
        toChildExecutionDetails(pipelineExecutionDetailDTO.getChildGraph()));
    pipelineExecutionDetailsResponseBody.setRollbackGraph(
        toChildExecutionDetails(pipelineExecutionDetailDTO.getRollbackGraph()));
    return pipelineExecutionDetailsResponseBody;
  }

  public DynamicExecutionDetailsResponseBody toDynamicExecutionDetailsResponseBody(
      DynamicExecutionInstanceResponseDTO instanceDTO) {
    DynamicExecutionDetailsResponseBody dynamicExecutionDetailsResponseBody = new DynamicExecutionDetailsResponseBody();
    dynamicExecutionDetailsResponseBody.setYaml(instanceDTO.getYaml());
    return dynamicExecutionDetailsResponseBody;
  }
  ChildExecutionDetails toChildExecutionDetails(ChildExecutionDetailDTO childExecutionDetailDTO) {
    if (childExecutionDetailDTO == null) {
      return null;
    }
    ChildExecutionDetails childExecutionDetails = new ChildExecutionDetails();
    childExecutionDetails.setExecutionGraph(toExecutionGraph(childExecutionDetailDTO.getExecutionGraph()));
    childExecutionDetails.setPipelineExecutionSummary(
        toPipelineExecutionSummaryDTO(childExecutionDetailDTO.getPipelineExecutionSummary()));
    return childExecutionDetails;
  }
  io.harness.spec.server.pipeline.v1.model.ExecutionGraph toExecutionGraph(ExecutionGraph executionGraph) {
    if (executionGraph == null) {
      return null;
    }
    io.harness.spec.server.pipeline.v1.model.ExecutionGraph executionGraph1 =
        new io.harness.spec.server.pipeline.v1.model.ExecutionGraph();
    executionGraph1.setExecutionMetadata(executionGraph.getExecutionMetadata());
    executionGraph1.setNodeAdjacencyListMap(toNodeAdjacencyListMapV1(executionGraph.getNodeAdjacencyListMap()));
    executionGraph1.setNodeMap(toNodeMapV1(executionGraph.getNodeMap()));
    executionGraph1.setRootNodeId(executionGraph.getRootNodeId());
    executionGraph1.setRepresentationStrategy(toRepresentationStrategyV1(executionGraph.getRepresentationStrategy()));
    return executionGraph1;
  }

  Map<String, io.harness.spec.server.pipeline.v1.model.ExecutionNode> toNodeMapV1(Map<String, ExecutionNode> map) {
    Map<String, io.harness.spec.server.pipeline.v1.model.ExecutionNode> map1 = new HashMap<>();
    for (Map.Entry<String, ExecutionNode> entry : map.entrySet()) {
      String key = entry.getKey();
      ExecutionNode value = entry.getValue();
      io.harness.spec.server.pipeline.v1.model.ExecutionNode valueV1 = toExecutionNodeV1(value);
      map1.put(key, valueV1);
    }
    return map1;
  }

  io.harness.spec.server.pipeline.v1.model.ExecutionNode toExecutionNodeV1(ExecutionNode executionNode) {
    io.harness.spec.server.pipeline.v1.model.ExecutionNode executionNode1 =
        new io.harness.spec.server.pipeline.v1.model.ExecutionNode();
    executionNode1.setUuid(executionNode.getUuid());
    executionNode1.setUnitProgresses(executionNode.getUnitProgresses()
                                         .stream()
                                         .map(PipelineExecutionDetailsApiUtils::toUnitProgressV1)
                                         .collect(Collectors.toList()));

    executionNode1.setStrategyMetadata(toStrategyMetadataV1(executionNode.getStrategyMetadata()));
    executionNode1.setStepType(executionNode.getStepType());
    executionNode1.setStepParameters(executionNode.getStepParameters());
    executionNode1.setStepDetails(executionNode.getStepDetails());
    executionNode1.setStatus(toExecutionStatusV1(executionNode.getStatus()));
    executionNode1.setRetryNodeMetadata(executionNode.getRetryNodeMetadata());
    executionNode1.setStartTs(executionNode.getStartTs());
    executionNode1.setSkipInfo(toSkipInfoV1(executionNode.getSkipInfo()));
    executionNode1.setSetupId(executionNode.getSetupId());
    executionNode1.setOutcomes(executionNode.getOutcomes());
    executionNode1.setNodeRunInfo(toNodeRunInfoV1(executionNode.getNodeRunInfo()));
    executionNode1.setName(executionNode.getName());
    executionNode1.setLogBaseKey(executionNode.getLogBaseKey());
    executionNode1.setInterruptHistories(executionNode.getInterruptHistories()
                                             .stream()
                                             .map(PipelineExecutionDetailsApiUtils::toInterruptEffectDTOV1)
                                             .collect(Collectors.toList()));
    executionNode1.setIdentifier(executionNode.getIdentifier());
    executionNode1.setFailureInfo(toFailureInfoDTOV1(executionNode.getFailureInfo()));
    executionNode1.setExecutionInputConfigured(executionNode.getExecutionInputConfigured());
    executionNode1.setExecutableResponses(executionNode.getExecutableResponses()
                                              .stream()
                                              .map(PipelineExecutionDetailsApiUtils::toExecutableResponseV1)
                                              .collect(Collectors.toList()));
    executionNode1.setEndTs(executionNode.getEndTs());
    executionNode1.setDelegateInfoList(executionNode.getDelegateInfoList()
                                           .stream()
                                           .map(PipelineExecutionDetailsApiUtils::toDelegateInfoV1)
                                           .collect(Collectors.toList()));
    executionNode1.setBaseFqn(executionNode.getBaseFqn());
    return executionNode1;
  }

  ExecutionStatus toExecutionStatusV1(Status status) {
    switch (status) {
      case SKIPPED:
        return SKIPPED;
      case RUNNING:
        return RUNNING;
      case QUEUED:
      case STARTING_QUEUED_STEP:
      case QUEUED_STEP_LIMIT_REACHED:
        return QUEUED;
      case EXPIRED:
        return EXPIRED;
      case QUEUED_EXECUTION_CONCURRENCY_REACHED:
        return QUEUEDEXECUTIONCONCURRENCYREACHED;
      case FAILED:
        return FAILED;
      case QUEUED_LICENSE_LIMIT_REACHED:
        return QUEUEDLICENSELIMITREACHED;
      case QUEUED_GLOBAL_INFRA_CAPACITY_REACHED:
        return QUEUEDGLOBALINFRACAPACITYREACHED;
      case QUEUED_PLAN_CREATION:
        return QUEUED_PLAN_CREATION;
      case STARTING_PLAN_CREATION:
        return QUEUED;
      case APPROVAL_REJECTED:
        return APPROVAL_REJECTED;
      case INTERVENTION_WAITING:
        return INTERVENTION_WAITING;
      case APPROVAL_WAITING:
        return APPROVAL_WAITING;
      case PAUSED:
        return PAUSED;
      case ABORTED:
        return ABORTED;
      case ERRORED:
        return ERRORED;
      case PAUSING:
        return PAUSING;
      case SUSPENDED:
        return SUSPENDED;
      case DISCONTINUING:
        return DISCONTINUING;
      case WAIT_STEP_RUNNING:
        return WAITSTEPRUNNING;
      case NO_OP:
        return NO_OP;
      case SUCCEEDED:
        return SUCCEEDED;
      case TASK_WAITING:
        return TASKWAITING;
      case FREEZE_FAILED:
        return FREEZE_FAILED;
      case ASYNC_WAITING:
        return ASYNCWAITING;
      case IGNORE_FAILED:
        return IGNOREFAILED;
      case PASSED_WITH_WARNING:
        return PASSEDWITHWARNING;
      case INPUT_WAITING:
        return INPUTWAITING;
      case TIMED_WAITING:
        return TIMEDWAITING;
      case RESOURCE_WAITING:
        return RESOURCEWAITING;
      default:
        throw new InvalidRequestException(String.format("Invalid status %s", status));
    }
  }

  OneOfExecutionNodeExecutableResponsesItems toExecutableResponseV1(ExecutableResponse executableResponse) {
    if (executableResponse.hasAsync()) {
      AsyncExecutableResponse asyncExecutableResponse = new AsyncExecutableResponse();
      io.harness.pms.contracts.execution.AsyncExecutableResponse asyncExecutableResponse1 =
          executableResponse.getAsync();
      asyncExecutableResponse.setStatus(toExecutionStatusV1(asyncExecutableResponse1.getStatus()));
      asyncExecutableResponse.setCallbackIds(asyncExecutableResponse1.getCallbackIdsList());
      asyncExecutableResponse.setLogKeys(asyncExecutableResponse1.getLogKeysList());
      asyncExecutableResponse.setTimeout(asyncExecutableResponse1.getTimeout());
      asyncExecutableResponse.setUnits(asyncExecutableResponse1.getUnitsList());
      return asyncExecutableResponse;
    } else if (executableResponse.hasAsyncChain()) {
      AsyncChainExecutableResponse asyncChainExecutableResponse = new AsyncChainExecutableResponse();
      io.harness.pms.contracts.execution.AsyncChainExecutableResponse asyncChainExecutableResponse1 =
          executableResponse.getAsyncChain();
      asyncChainExecutableResponse.setChainEnd(asyncChainExecutableResponse1.getChainEnd());
      asyncChainExecutableResponse.setUnits(asyncChainExecutableResponse1.getUnitsList());
      asyncChainExecutableResponse.setTimeout(asyncChainExecutableResponse1.getTimeout());
      asyncChainExecutableResponse.setPassThroughData(asyncChainExecutableResponse1.getPassThroughData().toByteArray());
      asyncChainExecutableResponse.setLogKeys(asyncChainExecutableResponse1.getLogKeysList());
      asyncChainExecutableResponse.setStatus(toExecutionStatusV1(asyncChainExecutableResponse1.getStatus()));
      asyncChainExecutableResponse.setCallbackId(asyncChainExecutableResponse1.getCallbackId());
      return asyncChainExecutableResponse;
    } else if (executableResponse.hasChild()) {
      ChildExecutableResponse childExecutableResponse = new ChildExecutableResponse();
      io.harness.pms.contracts.execution.ChildExecutableResponse childExecutableResponse1 =
          executableResponse.getChild();
      childExecutableResponse.setLogKeys(childExecutableResponse1.getLogKeysList());
      childExecutableResponse.setUnits(childExecutableResponse1.getUnitsList());
      childExecutableResponse.setChildNodeId(childExecutableResponse1.getChildNodeId());
      return childExecutableResponse;
    } else if (executableResponse.hasChildren()) {
      ChildrenExecutableResponse childrenExecutableResponse = new ChildrenExecutableResponse();
      io.harness.pms.contracts.execution.ChildrenExecutableResponse childrenExecutableResponse1 =
          executableResponse.getChildren();
      childrenExecutableResponse.setChildren(childrenExecutableResponse1.getChildrenList()
                                                 .stream()
                                                 .map(PipelineExecutionDetailsApiUtils::toChildV1)
                                                 .collect(Collectors.toList()));
      childrenExecutableResponse.setShouldProceedIfFailed(childrenExecutableResponse1.getShouldProceedIfFailed());
      childrenExecutableResponse.setMaxConcurrency(childrenExecutableResponse1.getChildrenCount());
      childrenExecutableResponse.setUnits(childrenExecutableResponse1.getUnitsList());
      childrenExecutableResponse.setLogKeys(childrenExecutableResponse1.getLogKeysList());
      return childrenExecutableResponse;
    } else if (executableResponse.hasChildChain()) {
      ChildChainExecutableResponse childChainExecutableResponse = new ChildChainExecutableResponse();
      io.harness.pms.contracts.execution.ChildChainExecutableResponse childChainExecutableResponse1 =
          executableResponse.getChildChain();
      childChainExecutableResponse.setPassThroughData(childChainExecutableResponse1.getPassThroughData().toByteArray());
      childChainExecutableResponse.setSuspend(childChainExecutableResponse1.getSuspend());
      childChainExecutableResponse.setLastLink(childChainExecutableResponse1.getLastLink());
      childChainExecutableResponse.setPreviousChildId(childChainExecutableResponse1.getPreviousChildId());
      childChainExecutableResponse.setNextChildId(childChainExecutableResponse1.getNextChildId());
      return childChainExecutableResponse;
    } else if (executableResponse.hasSkipTask()) {
      SkipTaskExecutableResponse skipTaskExecutableResponse = new SkipTaskExecutableResponse();
      io.harness.pms.contracts.execution.SkipTaskExecutableResponse skipTaskExecutableResponse1 =
          executableResponse.getSkipTask();
      skipTaskExecutableResponse.setMessage(skipTaskExecutableResponse1.getMessage());
      return skipTaskExecutableResponse;
    } else if (executableResponse.hasTaskChain()) {
      TaskChainExecutableResponse taskChainExecutableResponse = new TaskChainExecutableResponse();
      io.harness.pms.contracts.execution.TaskChainExecutableResponse taskChainExecutableResponse1 =
          executableResponse.getTaskChain();
      taskChainExecutableResponse.setChainEnd(taskChainExecutableResponse1.getChainEnd());
      taskChainExecutableResponse.setUnits(taskChainExecutableResponse1.getUnitsList());
      taskChainExecutableResponse.setTaskName(taskChainExecutableResponse1.getTaskName());
      taskChainExecutableResponse.setTaskId(taskChainExecutableResponse1.getTaskId());
      taskChainExecutableResponse.setTaskCategory(
          toTaskChainCategoryV1(taskChainExecutableResponse1.getTaskCategory()));
      taskChainExecutableResponse.setPassThroughData(taskChainExecutableResponse1.getPassThroughData().toByteArray());
      taskChainExecutableResponse.setLogKeys(taskChainExecutableResponse1.getLogKeysList());
      return taskChainExecutableResponse;
    } else if (executableResponse.hasTask()) {
      TaskExecutableResponse taskExecutableResponse = new TaskExecutableResponse();
      io.harness.pms.contracts.execution.TaskExecutableResponse taskExecutableResponse1 = executableResponse.getTask();
      taskExecutableResponse.setTaskId(taskExecutableResponse1.getTaskId());
      taskExecutableResponse.setUnits(taskExecutableResponse1.getUnitsList());
      taskExecutableResponse.setTaskCategory(toTaskCategoryV1(taskExecutableResponse1.getTaskCategory()));
      taskExecutableResponse.setLogKeys(taskExecutableResponse1.getLogKeysList());
      taskExecutableResponse.setTaskName(taskExecutableResponse1.getTaskName());
      return taskExecutableResponse;
    } else {
      SyncExecutableResponse syncExecutableResponse = new SyncExecutableResponse();
      io.harness.pms.contracts.execution.SyncExecutableResponse syncExecutableResponse1 = executableResponse.getSync();
      syncExecutableResponse.setLogKeys(syncExecutableResponse1.getLogKeysList());
      syncExecutableResponse.setUnits(syncExecutableResponse1.getUnitsList());
      return syncExecutableResponse;
    }
  }

  Child toChildV1(io.harness.pms.contracts.execution.ChildrenExecutableResponse.Child child) {
    Child child1 = new Child();
    child1.setChildNodeId(child.getChildNodeId());
    child1.setStrategyMetadata(toStrategyMetadataV1(child.getStrategyMetadata()));
    return child1;
  }

  TaskExecutableResponse.TaskCategoryEnum toTaskCategoryV1(TaskCategory taskCategory) {
    switch (taskCategory) {
      case DELEGATE_TASK_V2:
        return TaskExecutableResponse.TaskCategoryEnum.DELEGATE_TASK_V2;
      case DELEGATE_TASK_V1:
        return TaskExecutableResponse.TaskCategoryEnum.DELEGATE_TASK_V1;
      case UNRECOGNIZED:
        return TaskExecutableResponse.TaskCategoryEnum.UNKNOWN_CATEGORY;
      default:
        throw new InvalidRequestException(String.format("Invalid task category %s", taskCategory));
    }
  }

  TaskChainExecutableResponse.TaskCategoryEnum toTaskChainCategoryV1(TaskCategory taskCategory) {
    switch (taskCategory) {
      case DELEGATE_TASK_V2:
        return TaskChainExecutableResponse.TaskCategoryEnum.DELEGATE_TASK_V2;
      case DELEGATE_TASK_V1:
        return TaskChainExecutableResponse.TaskCategoryEnum.DELEGATE_TASK_V1;
      case UNRECOGNIZED:
        return TaskChainExecutableResponse.TaskCategoryEnum.UNKNOWN_CATEGORY;
      default:
        throw new InvalidRequestException(String.format("Invalid task category %s", taskCategory));
    }
  }

  io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO toInterruptEffectDTOV1(
      InterruptEffectDTO interruptEffectDTO) {
    io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO interruptEffectDTO1 =
        new io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO();
    interruptEffectDTO1.setInterruptId(interruptEffectDTO.getInterruptId());
    interruptEffectDTO1.setInterruptConfig(toInterruptConfigV1(interruptEffectDTO.getInterruptConfig()));
    interruptEffectDTO1.setInterruptType(toInterruptTypeV1(interruptEffectDTO.getInterruptType()));
    interruptEffectDTO1.setTookEffectAt(interruptEffectDTO.getTookEffectAt());
    return interruptEffectDTO1;
  }

  io.harness.spec.server.pipeline.v1.model.InterruptConfig toInterruptConfigV1(InterruptConfig interruptConfig) {
    io.harness.spec.server.pipeline.v1.model.InterruptConfig interruptConfig1 =
        new io.harness.spec.server.pipeline.v1.model.InterruptConfig();
    interruptConfig1.setRetryInterruptConfig(toRetryInterruptConfigV1(interruptConfig.getRetryInterruptConfig()));
    interruptConfig1.setIssuedBy(toIssuedByV1(interruptConfig.getIssuedBy()));
    return interruptConfig1;
  }

  IssuedBy toIssuedByV1(io.harness.interrupts.IssuedBy issuedBy) {
    IssuedBy issuedBy1 = new IssuedBy();
    issuedBy1.setIssueTime(issuedBy.getIssueTime());
    issuedBy1.setAdviserIssuer(toAdviserIssuerV1(issuedBy.getAdviserIssuer()));
    issuedBy1.setManualIssuer(toManualIssuerV1(issuedBy.getManualIssuer()));
    issuedBy1.setTriggerIssuer(toTriggerIssuerV1(issuedBy.getTriggerIssuer()));
    issuedBy1.setTimeoutIssuer(toTimeoutIssuerV1(issuedBy.getTimeoutIssuer()));
    return issuedBy1;
  }

  io.harness.spec.server.pipeline.v1.model.ManualIssuer toManualIssuerV1(ManualIssuer manualIssuer) {
    io.harness.spec.server.pipeline.v1.model.ManualIssuer manualIssuer1 =
        new io.harness.spec.server.pipeline.v1.model.ManualIssuer();
    manualIssuer1.setIdentifier(manualIssuer.getIdentifier());
    manualIssuer1.setType(manualIssuer.getType());
    manualIssuer1.setEmailId(manualIssuer.getEmail_id());
    manualIssuer1.setUserId(manualIssuer.getUser_id());
    return manualIssuer1;
  }

  io.harness.spec.server.pipeline.v1.model.TriggerIssuer toTriggerIssuerV1(TriggerIssuer triggerIssuer) {
    io.harness.spec.server.pipeline.v1.model.TriggerIssuer triggerIssuer1 =
        new io.harness.spec.server.pipeline.v1.model.TriggerIssuer();
    triggerIssuer1.setTriggerRef(triggerIssuer.getTriggerRef());
    triggerIssuer1.setAbortPrevConcurrentExecution(triggerIssuer.isAbortPrevConcurrentExecution());
    return triggerIssuer1;
  }

  AdviserIssuer toAdviserIssuerV1(io.harness.interrupts.AdviserIssuer adviserIssuer) {
    AdviserIssuer adviserIssuer1 = new AdviserIssuer();
    adviserIssuer1.setAdviseType(toAdviseTypeV1(adviserIssuer.getAdviseType()));
    return adviserIssuer1;
  }

  TimeoutIssuer toTimeoutIssuerV1(io.harness.interrupts.TimeoutIssuer timeoutIssuer) {
    TimeoutIssuer timeoutIssuer1 = new TimeoutIssuer();
    timeoutIssuer1.setTimeoutInstanceId(timeoutIssuer.getTimeoutInstanceId());
    return timeoutIssuer1;
  }

  AdviserIssuer.AdviseTypeEnum toAdviseTypeV1(AdviseType adviseType) {
    switch (adviseType) {
      case PROCEED_WITH_DEFAULT:
        return AdviserIssuer.AdviseTypeEnum.PROCEED_WITH_DEFAULT;
      case MARK_SUCCESS:
        return AdviserIssuer.AdviseTypeEnum.MARK_SUCCESS;
      case NEXT_STEP:
        return AdviserIssuer.AdviseTypeEnum.NEXT_STEP;
      case RETRY:
        return AdviserIssuer.AdviseTypeEnum.RETRY;
      case UNKNOWN:
        return AdviserIssuer.AdviseTypeEnum.UNKNOWN;
      case IGNORE_FAILURE:
        return AdviserIssuer.AdviseTypeEnum.IGNORE_FAILURE;
      case END_PLAN:
        return AdviserIssuer.AdviseTypeEnum.END_PLAN;
      case INTERVENTION_WAIT:
        return AdviserIssuer.AdviseTypeEnum.INTERVENTION_WAIT;
      case MARK_AS_FAILURE:
        return AdviserIssuer.AdviseTypeEnum.MARK_AS_FAILURE;
      default:
        throw new InvalidRequestException(String.format("Invalid advise type %s", adviseType));
    }
  }

  io.harness.spec.server.pipeline.v1.model.RetryInterruptConfig toRetryInterruptConfigV1(
      RetryInterruptConfig retryInterruptConfig) {
    io.harness.spec.server.pipeline.v1.model.RetryInterruptConfig retryInterruptConfig1 =
        new io.harness.spec.server.pipeline.v1.model.RetryInterruptConfig();
    retryInterruptConfig1.setRetryId(retryInterruptConfig.getRetryId());
    return retryInterruptConfig1;
  }

  io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum toInterruptTypeV1(
      InterruptType interruptType) {
    switch (interruptType) {
      case UNKNOWN:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.UNKNOWN;
      case ABORT_ALL:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.ABORT_ALL;
      case ABORT:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.ABORT;
      case PAUSE:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.PAUSE;
      case RETRY:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.RETRY;
      case IGNORE:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.IGNORE;
      case RESUME:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.RESUME;
      case NEXT_STEP:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.NEXT_STEP;
      case PAUSE_ALL:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.PAUSE_ALL;
      case EXPIRE_ALL:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.EXPIRE_ALL;
      case RESUME_ALL:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.RESUME_ALL;
      case MARK_FAILED:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.MARK_FAILED;
      case MARK_EXPIRED:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.MARK_EXPIRED;
      case END_EXECUTION:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.END_EXECUTION;
      case MARK_SUCCESS:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.MARK_SUCCESS;
      case CUSTOM_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.CUSTOM_FAILURE;
      case PROCEED_WITH_DEFAULT:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.PROCEED_WITH_DEFAULT;
      case USER_MARKED_FAIL_ALL:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum.USER_MARKED_FAIL_ALL;
      case WAITING_FOR_MANUAL_INTERVENTION:
        return io.harness.spec.server.pipeline.v1.model.InterruptEffectDTO.InterruptTypeEnum
            .WAITING_FOR_MANUAL_INTERVENTION;
      default:
        throw new InvalidRequestException(String.format("Invalid interrupt type %s", interruptType));
    }
  }

  io.harness.spec.server.pipeline.v1.model.DelegateInfo toDelegateInfoV1(DelegateInfo delegateInfo) {
    io.harness.spec.server.pipeline.v1.model.DelegateInfo delegateInfo1 =
        new io.harness.spec.server.pipeline.v1.model.DelegateInfo();
    delegateInfo1.setId(delegateInfo.getId());
    delegateInfo1.setName(delegateInfo.getName());
    delegateInfo1.setTaskId(delegateInfo.getTaskId());
    delegateInfo1.setTaskName(delegateInfo.getTaskName());
    return delegateInfo1;
  }

  io.harness.spec.server.pipeline.v1.model.StrategyMetadata toStrategyMetadataV1(StrategyMetadata strategyMetadata) {
    if (strategyMetadata == null) {
      return null;
    }
    io.harness.spec.server.pipeline.v1.model.StrategyMetadata strategyMetadata1 =
        new io.harness.spec.server.pipeline.v1.model.StrategyMetadata();
    if (strategyMetadata.hasForMetadata()) {
      toForMetadataV1(strategyMetadata.getForMetadata());
    } else if (strategyMetadata.hasMatrixMetadata()) {
      toMatrixMetadataV1(strategyMetadata.getMatrixMetadata());
    }
    strategyMetadata1.setCurrentIteration(strategyMetadata.getCurrentIteration());
    strategyMetadata1.setIdentifierPostFix(strategyMetadata1.getIdentifierPostFix());
    strategyMetadata1.setTotalIterations(strategyMetadata.getTotalIterations());
    return strategyMetadata1;
  }

  ForMetadata toForMetadataV1(io.harness.pms.contracts.execution.ForMetadata forMetadata) {
    ForMetadata forMetadata1 = new ForMetadata();
    forMetadata1.setPartition(forMetadata.getPartitionList());
    forMetadata1.setValue(forMetadata.getValue());
    return forMetadata1;
  }

  io.harness.spec.server.pipeline.v1.model.MatrixMetadata toMatrixMetadataV1(MatrixMetadata matrixMetadata) {
    io.harness.spec.server.pipeline.v1.model.MatrixMetadata matrixMetadata1 =
        new io.harness.spec.server.pipeline.v1.model.MatrixMetadata();
    matrixMetadata1.setMatrixCombination(matrixMetadata.getMatrixCombinationList());
    matrixMetadata1.setMatrixValues(matrixMetadata.getMatrixValues());
    matrixMetadata1.setMatrixKeysToSkipInName(matrixMetadata.getMatrixKeysToSkipInNameList());
    matrixMetadata1.setSubType(matrixMetadata.getSubType());
    matrixMetadata1.setNodeName(matrixMetadata.getNodeName());
    return matrixMetadata1;
  }

  io.harness.spec.server.pipeline.v1.model.UnitProgress toUnitProgressV1(UnitProgress unitProgress) {
    io.harness.spec.server.pipeline.v1.model.UnitProgress unitProgress1 =
        new io.harness.spec.server.pipeline.v1.model.UnitProgress();
    unitProgress1.setUnitName(unitProgress.getUnitName());
    unitProgress1.setStatus(toUnitStatusV1(unitProgress.getStatus()));
    unitProgress1.setEndTime(unitProgress.getEndTime());
    unitProgress1.setStartTime(unitProgress.getStartTime());
    return unitProgress1;
  }

  io.harness.spec.server.pipeline.v1.model.UnitProgress.StatusEnum toUnitStatusV1(UnitStatus unitStatus) {
    switch (unitStatus) {
      case EXPIRED:
        return io.harness.spec.server.pipeline.v1.model.UnitProgress.StatusEnum.EXPIRED;
      case SUCCESS:
        return io.harness.spec.server.pipeline.v1.model.UnitProgress.StatusEnum.SUCCESS;
      case UNKNOWN:
        return io.harness.spec.server.pipeline.v1.model.UnitProgress.StatusEnum.UNKNOWN;
      case QUEUED:
        return io.harness.spec.server.pipeline.v1.model.UnitProgress.StatusEnum.QUEUED;
      case RUNNING:
        return io.harness.spec.server.pipeline.v1.model.UnitProgress.StatusEnum.RUNNING;
      case SKIPPED:
        return io.harness.spec.server.pipeline.v1.model.UnitProgress.StatusEnum.SKIPPED;
      case FAILURE:
        return io.harness.spec.server.pipeline.v1.model.UnitProgress.StatusEnum.FAILURE;
      default:
        throw new InvalidRequestException(String.format("Invalid unit status %s", unitStatus));
    }
  }

  SkipInfo toSkipInfoV1(io.harness.pms.contracts.execution.skip.SkipInfo skipInfo) {
    SkipInfo skipInfo1 = new SkipInfo();
    if (skipInfo == null) {
      return null;
    }
    skipInfo1.setSkipCondition(skipInfo.getSkipCondition());
    skipInfo1.setEvaluatedCondition(skipInfo.getEvaluatedCondition());
    return skipInfo1;
  }

  io.harness.spec.server.pipeline.v1.model.NodeRunInfo toNodeRunInfoV1(NodeRunInfo nodeRunInfo) {
    if (nodeRunInfo == null) {
      return null;
    }
    io.harness.spec.server.pipeline.v1.model.NodeRunInfo nodeRunInfo1 =
        new io.harness.spec.server.pipeline.v1.model.NodeRunInfo();
    nodeRunInfo1.setEvaluatedCondition(nodeRunInfo.getEvaluatedCondition());
    nodeRunInfo1.setExpressions(nodeRunInfo.getExpressionsList()
                                    .stream()
                                    .map(PipelineExecutionDetailsApiUtils::toExpressionBlockV1)
                                    .collect(Collectors.toList()));
    nodeRunInfo1.setWhenCondition(nodeRunInfo.getWhenCondition());
    return nodeRunInfo1;
  }

  ExpressionBlock toExpressionBlockV1(io.harness.pms.contracts.execution.run.ExpressionBlock expressionBlock) {
    ExpressionBlock expressionBlock1 = new ExpressionBlock();
    expressionBlock1.setExpression(expressionBlock.getExpression());
    expressionBlock1.setExpressionValue(expressionBlock.getExpressionValue());
    expressionBlock1.setCount(expressionBlock.getCount());
    return expressionBlock1;
  }

  Map<String, ExecutionNodeAdjacencyList> toNodeAdjacencyListMapV1(
      Map<String, io.harness.beans.ExecutionNodeAdjacencyList> map) {
    Map<String, ExecutionNodeAdjacencyList> map1 = new HashMap<>();
    for (Map.Entry<String, io.harness.beans.ExecutionNodeAdjacencyList> entry : map.entrySet()) {
      String key = entry.getKey();
      io.harness.beans.ExecutionNodeAdjacencyList value = entry.getValue();
      ExecutionNodeAdjacencyList valueV1 = toExecutionNodeAdjacencyListV1(value);
      map1.put(key, valueV1);
    }
    return map1;
  }

  ExecutionNodeAdjacencyList toExecutionNodeAdjacencyListV1(
      io.harness.beans.ExecutionNodeAdjacencyList executionNodeAdjacencyList) {
    ExecutionNodeAdjacencyList executionNodeAdjacencyList1 = new ExecutionNodeAdjacencyList();
    executionNodeAdjacencyList1.setChildren(executionNodeAdjacencyList.getChildren());
    executionNodeAdjacencyList1.setNextIds(executionNodeAdjacencyList.getNextIds());
    return executionNodeAdjacencyList1;
  }

  io.harness.spec.server.pipeline.v1.model.ExecutionGraph.RepresentationStrategyEnum toRepresentationStrategyV1(
      RepresentationStrategy representationStrategy) {
    switch (representationStrategy) {
      case CAMELCASE:
        return io.harness.spec.server.pipeline.v1.model.ExecutionGraph.RepresentationStrategyEnum.CAMEL_CASE;
      default:
        throw new InvalidRequestException(
            String.format("Invalid execution representation strategy %s", representationStrategy));
    }
  }

  io.harness.spec.server.pipeline.v1.model.PipelineExecutionSummaryDTO toPipelineExecutionSummaryDTO(
      PipelineExecutionSummaryDTO pipelineExecutionSummaryDTO) {
    io.harness.spec.server.pipeline.v1.model.PipelineExecutionSummaryDTO pipelineExecutionSummaryDTO1 =
        new io.harness.spec.server.pipeline.v1.model.PipelineExecutionSummaryDTO();
    pipelineExecutionSummaryDTO1.setRunningStagesCount(pipelineExecutionSummaryDTO.getRunningStagesCount());
    pipelineExecutionSummaryDTO1.setRunSequence(pipelineExecutionSummaryDTO.getRunSequence());
    pipelineExecutionSummaryDTO1.setPipelineId(pipelineExecutionSummaryDTO.getPipelineIdentifier());
    pipelineExecutionSummaryDTO1.setExecutionMode(toExecutionModeEnum(pipelineExecutionSummaryDTO.getExecutionMode()));
    pipelineExecutionSummaryDTO1.setAllowStageExecutions(pipelineExecutionSummaryDTO.isAllowStageExecutions());
    pipelineExecutionSummaryDTO1.setYamlVersion(pipelineExecutionSummaryDTO.getYamlVersion());
    pipelineExecutionSummaryDTO1.setTags(pipelineExecutionSummaryDTO.getTags()
                                             .stream()
                                             .map(PipelineExecutionDetailsApiUtils::toTagV1)
                                             .collect(Collectors.toList()));
    pipelineExecutionSummaryDTO1.setTotalStagesCount(pipelineExecutionSummaryDTO.getTotalStagesCount());
    pipelineExecutionSummaryDTO1.setSuccessfulStagesCount(pipelineExecutionSummaryDTO.getSuccessfulStagesCount());
    pipelineExecutionSummaryDTO1.setStoreType(toStoreTypeEnum(pipelineExecutionSummaryDTO.getStoreType()));
    pipelineExecutionSummaryDTO1.setStatus(toExecutionStatusV1(pipelineExecutionSummaryDTO.getStatus()));
    pipelineExecutionSummaryDTO1.setStartTs(pipelineExecutionSummaryDTO.getStartTs());
    pipelineExecutionSummaryDTO1.setStartingNodeId(pipelineExecutionSummaryDTO.getStartingNodeId());
    pipelineExecutionSummaryDTO1.setStagesExecutedNames(pipelineExecutionSummaryDTO.getStagesExecutedNames());
    pipelineExecutionSummaryDTO1.setShowRetryHistory(pipelineExecutionSummaryDTO.isShowRetryHistory());
    pipelineExecutionSummaryDTO1.setShouldUseSimplifiedKey(pipelineExecutionSummaryDTO.isShouldUseSimplifiedKey());
    pipelineExecutionSummaryDTO1.setProjectId(pipelineExecutionSummaryDTO.getProjectIdentifier());
    pipelineExecutionSummaryDTO1.setPlanExecutionId(pipelineExecutionSummaryDTO.getPlanExecutionId());
    pipelineExecutionSummaryDTO1.setParentStageInfo(
        toParentStageInfoV1(pipelineExecutionSummaryDTO.getParentStageInfo()));
    pipelineExecutionSummaryDTO1.setOrgId(pipelineExecutionSummaryDTO.getOrgIdentifier());
    pipelineExecutionSummaryDTO1.setNotesExistForPlanExecutionId(
        pipelineExecutionSummaryDTO.isNotesExistForPlanExecutionId());
    pipelineExecutionSummaryDTO1.setName(pipelineExecutionSummaryDTO.getName());
    pipelineExecutionSummaryDTO1.setModules(pipelineExecutionSummaryDTO.getModules());
    pipelineExecutionSummaryDTO1.setModuleInfo(pipelineExecutionSummaryDTO.getModuleInfo());
    pipelineExecutionSummaryDTO1.setLayoutNodeMap(toLayoutNodeMapV1(pipelineExecutionSummaryDTO.getLayoutNodeMap()));
    pipelineExecutionSummaryDTO1.setLabels(pipelineExecutionSummaryDTO.getLabels()
                                               .stream()
                                               .map(PipelineExecutionDetailsApiUtils::toTagV1)
                                               .collect(Collectors.toList()));
    pipelineExecutionSummaryDTO1.setIsStagesExecution(pipelineExecutionSummaryDTO.isStagesExecution());
    pipelineExecutionSummaryDTO1.setIsDynamicExecution(pipelineExecutionSummaryDTO.isDynamicExecution());
    pipelineExecutionSummaryDTO1.setGovernanceMetadata(
        toGovernanceMetadataV1(pipelineExecutionSummaryDTO.getGovernanceMetadata()));
    pipelineExecutionSummaryDTO1.setGitDetails(toGitDetailsV1(pipelineExecutionSummaryDTO.getGitDetails()));
    pipelineExecutionSummaryDTO1.setFailureInfo(toFailureInfoDTOV1(pipelineExecutionSummaryDTO.getFailureInfo()));
    pipelineExecutionSummaryDTO1.setFailedStagesCount(pipelineExecutionSummaryDTO.getFailedStagesCount());
    pipelineExecutionSummaryDTO1.setExecutionTriggerInfo(
        toExecutionTriggerInfoV1(pipelineExecutionSummaryDTO.getExecutionTriggerInfo()));
    pipelineExecutionSummaryDTO1.setExecutionInputConfigured(pipelineExecutionSummaryDTO.getExecutionInputConfigured());
    pipelineExecutionSummaryDTO1.setExecutionErrorInfo(
        toExecutionErrorInfoV1(pipelineExecutionSummaryDTO.getExecutionErrorInfo()));
    pipelineExecutionSummaryDTO1.setEndTs(pipelineExecutionSummaryDTO.getEndTs());
    pipelineExecutionSummaryDTO1.setCreatedAt(pipelineExecutionSummaryDTO.getCreatedAt());
    pipelineExecutionSummaryDTO1.setConnectorRef(pipelineExecutionSummaryDTO.getConnectorRef());
    pipelineExecutionSummaryDTO1.setCanRetry(pipelineExecutionSummaryDTO.getCanRetry());
    pipelineExecutionSummaryDTO1.setAbortedBy(toAbortedByV1(pipelineExecutionSummaryDTO.getAbortedBy()));
    return pipelineExecutionSummaryDTO1;
  }

  GovernanceMetadata toGovernanceMetadataV1(io.harness.governance.GovernanceMetadata governanceMetadata) {
    GovernanceMetadata governanceMetadata1 = new GovernanceMetadata();
    governanceMetadata1.setMessage(governanceMetadata.getMessage());
    governanceMetadata1.setIdentifier(governanceMetadata.getId());
    governanceMetadata1.setStatus(governanceMetadata.getStatus());
    governanceMetadata1.setPolicySetMetadata(governanceMetadata.getDetailsList()
                                                 .stream()
                                                 .map(PipelineExecutionDetailsApiUtils::toPolicySetMetadataV1)
                                                 .collect(Collectors.toList()));
    governanceMetadata1.setAction(governanceMetadata.getAction());
    governanceMetadata1.setAccountIdentifier(governanceMetadata.getAccountId());
    governanceMetadata1.setCreated(governanceMetadata.getCreated());
    governanceMetadata1.setDeny(governanceMetadata.getDeny());
    governanceMetadata1.setEntity(governanceMetadata.getEntity());
    governanceMetadata1.setOrgIdentifier(governanceMetadata.getOrgId());
    governanceMetadata1.setProjectIdentifier(governanceMetadata.getProjectId());
    governanceMetadata1.setType(governanceMetadata.getType());
    governanceMetadata1.setTimeStamp((int) governanceMetadata.getTimestamp());
    return governanceMetadata1;
  }

  Map<String, io.harness.spec.server.pipeline.v1.model.GraphLayoutNodeDTO> toLayoutNodeMapV1(
      Map<String, GraphLayoutNodeDTO> map) {
    Map<String, io.harness.spec.server.pipeline.v1.model.GraphLayoutNodeDTO> map1 = new HashMap<>();
    for (Map.Entry<String, GraphLayoutNodeDTO> entry : map.entrySet()) {
      String key = entry.getKey();
      GraphLayoutNodeDTO value = entry.getValue();
      io.harness.spec.server.pipeline.v1.model.GraphLayoutNodeDTO valueV1 = toGraphLayoutNodeDTOV1(value);
      map1.put(key, valueV1);
    }
    return map1;
  }

  io.harness.spec.server.pipeline.v1.model.GraphLayoutNodeDTO toGraphLayoutNodeDTOV1(
      GraphLayoutNodeDTO graphLayoutNodeDTO) {
    io.harness.spec.server.pipeline.v1.model.GraphLayoutNodeDTO graphLayoutNodeDTO1 =
        new io.harness.spec.server.pipeline.v1.model.GraphLayoutNodeDTO();
    graphLayoutNodeDTO1.setEdgeLayoutList(toEdgeLayoutListDTOV1(graphLayoutNodeDTO.getEdgeLayoutList()));
    graphLayoutNodeDTO1.setStrategyMetadata(toStrategyMetadataV1(graphLayoutNodeDTO.getStrategyMetadata()));
    graphLayoutNodeDTO1.setStepDetails(graphLayoutNodeDTO.getStepDetails());
    graphLayoutNodeDTO1.setStatus(toExecutionStatusV1(graphLayoutNodeDTO.getStatus()));
    graphLayoutNodeDTO1.setStartTs(graphLayoutNodeDTO.getStartTs());
    graphLayoutNodeDTO1.setSkipInfo(toSkipInfoV1(graphLayoutNodeDTO.getSkipInfo()));
    graphLayoutNodeDTO1.setNodeUuid(graphLayoutNodeDTO.getNodeUuid());
    graphLayoutNodeDTO1.setNodeType(graphLayoutNodeDTO.getNodeType());
    graphLayoutNodeDTO1.setNodeRunInfo(toNodeRunInfoV1(graphLayoutNodeDTO.getNodeRunInfo()));
    graphLayoutNodeDTO1.setNodeIdentifier(graphLayoutNodeDTO.getNodeIdentifier());
    graphLayoutNodeDTO1.setNodeGroup(graphLayoutNodeDTO.getNodeGroup());
    graphLayoutNodeDTO1.setNodeExecutionId(graphLayoutNodeDTO.getNodeExecutionId());
    graphLayoutNodeDTO1.setName(graphLayoutNodeDTO.getName());
    graphLayoutNodeDTO1.setModuleInfo(graphLayoutNodeDTO.getModuleInfo());
    graphLayoutNodeDTO1.setIsRollbackStageNode(graphLayoutNodeDTO.getIsRollbackStageNode());
    graphLayoutNodeDTO1.setHidden(graphLayoutNodeDTO.getHidden());
    graphLayoutNodeDTO1.setFailureInfoDto(toFailureInfoDTOV1(graphLayoutNodeDTO.getFailureInfoDTO()));
    graphLayoutNodeDTO1.setExecutionInputConfigured(graphLayoutNodeDTO.getExecutionInputConfigured());
    graphLayoutNodeDTO1.setEndTs(graphLayoutNodeDTO.getEndTs());
    graphLayoutNodeDTO1.setBarrierFound(graphLayoutNodeDTO.getBarrierFound());
    return graphLayoutNodeDTO1;
  }

  io.harness.spec.server.pipeline.v1.model.EdgeLayoutListDTO toEdgeLayoutListDTOV1(
      EdgeLayoutListDTO edgeLayoutListDTO) {
    io.harness.spec.server.pipeline.v1.model.EdgeLayoutListDTO edgeLayoutListDTO1 =
        new io.harness.spec.server.pipeline.v1.model.EdgeLayoutListDTO();
    edgeLayoutListDTO1.setNextIds(edgeLayoutListDTO.getNextIds());
    edgeLayoutListDTO1.setCurrentNodeChildren(edgeLayoutListDTO.getCurrentNodeChildren());
    return edgeLayoutListDTO1;
  }

  PolicySetMetadata toPolicySetMetadataV1(io.harness.governance.PolicySetMetadata policySetMetadata) {
    PolicySetMetadata policySetMetadata1 = new PolicySetMetadata();
    policySetMetadata1.setPolicyMetadata(policySetMetadata.getPolicyMetadataList()
                                             .stream()
                                             .map(PipelineExecutionDetailsApiUtils::toPolicyMetadataV1)
                                             .collect(Collectors.toList()));
    policySetMetadata1.setPolicySet(policySetMetadata.getPolicySetId());
    policySetMetadata1.setPolicySetIdentifier(policySetMetadata.getPolicySetId());
    policySetMetadata1.setCreated(policySetMetadata.getCreated());
    policySetMetadata1.setStatus(policySetMetadata.getStatus());
    policySetMetadata1.setProjectIdentifier(policySetMetadata.getProjectId());
    policySetMetadata1.setOrgIdentifier(policySetMetadata.getOrgId());
    policySetMetadata1.setIdentifier(policySetMetadata.getIdentifier());
    policySetMetadata1.setDeny(policySetMetadata.getDeny());
    policySetMetadata1.setAccountIdentifier(policySetMetadata.getAccountId());
    return policySetMetadata1;
  }

  io.harness.spec.server.pipeline.v1.model.PolicyMetadata toPolicyMetadataV1(PolicyMetadata policyMetadata) {
    io.harness.spec.server.pipeline.v1.model.PolicyMetadata policyMetadata1 =
        new io.harness.spec.server.pipeline.v1.model.PolicyMetadata();
    policyMetadata1.setAccountIdentifier(policyMetadata.getAccountId());
    policyMetadata1.setCreated(policyMetadata.getCreated());
    policyMetadata1.setIdentifier(policyMetadata.getIdentifier());
    policyMetadata1.setPolicyIdentifier(policyMetadata.getPolicyId());
    policyMetadata1.setUpdated(policyMetadata.getUpdated());
    policyMetadata1.setStatus(policyMetadata.getStatus());
    policyMetadata1.setSeverity(policyMetadata.getSeverity());
    policyMetadata1.setProjectIdentifier(policyMetadata.getProjectId());
    policyMetadata1.setPolicyName(policyMetadata.getPolicyName());
    policyMetadata1.setOrgIdentifier(policyMetadata.getOrgId());
    policyMetadata1.setError(policyMetadata.getError());
    policyMetadata1.setDenyMessages(policyMetadata.getDenyMessagesList());
    return policyMetadata1;
  }

  public ExecutionStatus toExecutionStatusV1(io.harness.pms.execution.ExecutionStatus executionStatus) {
    switch (executionStatus) {
      case WAITSTEPRUNNING:
        return WAITSTEPRUNNING;
      case RESOURCEWAITING:
        return RESOURCEWAITING;
      case APPROVALWAITING:
        return APPROVALWAITING;
      case ABORTEDBYFREEZE:
        return ABORTEDBYFREEZE;
      case DISCONTINUING:
        return DISCONTINUING;
      case TIMEDWAITING:
        return TIMEDWAITING;
      case INPUTWAITING:
        return INPUTWAITING;
      case IGNOREFAILED:
        return IGNOREFAILED;
      case PASSED_WITH_WARNING:
        return PASSEDWITHWARNING;
      case ASYNCWAITING:
        return ASYNCWAITING;
      case TASKWAITING:
        return TASKWAITING;
      case NOT_STARTED:
        return NOT_STARTED;
      case NOTSTARTED:
        return NOTSTARTED;
      case SUSPENDED:
        return SUSPENDED;
      case WAITING:
        return WAITING;
      case SKIPPED:
        return SKIPPED;
      case RUNNING:
        return RUNNING;
      case PAUSING:
        return PAUSING;
      case EXPIRED:
        return EXPIRED;
      case ERRORED:
        return ERRORED;
      case ABORTED:
        return ABORTED;
      case QUEUED:
      case STARTING_PLAN_CREATION:
        return QUEUED;
      case PAUSED:
        return PAUSED;
      case APPROVAL_WAITING:
        return APPROVAL_WAITING;
      case INTERVENTION_WAITING:
        return INTERVENTION_WAITING;
      case SUCCESS:
        return SUCCESS;
      case FAILED:
        return FAILED;
      case APPROVALREJECTED:
        return APPROVALREJECTED;
      case APPROVAL_REJECTED:
        return APPROVAL_REJECTED;
      case INTERVENTIONWAITING:
        return INTERVENTIONWAITING;
      case QUEUED_LICENSE_LIMIT_REACHED:
        return QUEUEDLICENSELIMITREACHED;
      case QUEUED_EXECUTION_CONCURRENCY_REACHED:
        return QUEUEDEXECUTIONCONCURRENCYREACHED;
      case QUEUED_GLOBAL_INFRA_CAPACITY_REACHED:
        return QUEUEDGLOBALINFRACAPACITYREACHED;
      case QUEUED_PLAN_CREATION:
        return QUEUED_PLAN_CREATION;
      default:
        throw new InvalidRequestException(String.format("Invalid execution status %s", executionStatus));
    }
  }

  io.harness.spec.server.pipeline.v1.model.PipelineExecutionSummaryDTO.StoreTypeEnum toStoreTypeEnum(
      StoreType storeType) {
    switch (storeType) {
      case INLINE:
        return io.harness.spec.server.pipeline.v1.model.PipelineExecutionSummaryDTO.StoreTypeEnum.INLINE;
      case REMOTE:
        return io.harness.spec.server.pipeline.v1.model.PipelineExecutionSummaryDTO.StoreTypeEnum.REMOTE;
      default:
        throw new InvalidRequestException(String.format("Invalid store type %s", storeType));
    }
  }

  io.harness.spec.server.pipeline.v1.model.PipelineExecutionSummaryDTO.ExecutionModeEnum toExecutionModeEnum(
      ExecutionMode executionMode) {
    switch (executionMode) {
      case NORMAL:
        return io.harness.spec.server.pipeline.v1.model.PipelineExecutionSummaryDTO.ExecutionModeEnum.NORMAL;
      case UNRECOGNIZED:
        return io.harness.spec.server.pipeline.v1.model.PipelineExecutionSummaryDTO.ExecutionModeEnum.UNRECOGNIZED;
      case UNDEFINED_MODE:
        return io.harness.spec.server.pipeline.v1.model.PipelineExecutionSummaryDTO.ExecutionModeEnum.UNDEFINED_MODE;
      case PIPELINE_ROLLBACK:
        return io.harness.spec.server.pipeline.v1.model.PipelineExecutionSummaryDTO.ExecutionModeEnum.PIPELINE_ROLLBACK;
      case POST_EXECUTION_ROLLBACK:
        return io.harness.spec.server.pipeline.v1.model.PipelineExecutionSummaryDTO.ExecutionModeEnum
            .POST_EXECUTION_ROLLBACK;
      default:
        throw new InvalidRequestException(String.format("Invalid execution mode %s", executionMode));
    }
  }

  Tag toTagV1(NGTag tag) {
    Tag tag1 = new Tag();
    tag1.setKey(tag.getKey());
    tag1.setValue(tag.getValue());
    return tag1;
  }

  Tag toTagV1(NGLabel tag) {
    Tag tag1 = new Tag();
    tag1.setKey(tag.getKey());
    tag1.setValue(tag.getValue());
    return tag1;
  }

  AbortedBy toAbortedByV1(io.harness.abort.AbortedBy abortedBy) {
    if (abortedBy == null) {
      return null;
    }
    AbortedBy abortedBy1 = new AbortedBy();
    abortedBy1.setCreatedAt(abortedBy.getCreatedAt());
    abortedBy1.setEmail(abortedBy.getEmail());
    abortedBy1.setUserName(abortedBy.getUserName());
    return abortedBy1;
  }

  GitDetails toGitDetailsV1(EntityGitDetails gitDetails) {
    if (gitDetails == null) {
      return null;
    }
    GitDetails gitDetails1 = new GitDetails();
    gitDetails1.setBranchName(gitDetails.getBranch());
    gitDetails1.setCommitId(gitDetails.getCommitId());
    gitDetails1.setFilePath(gitDetails.getFilePath());
    gitDetails1.setFileUrl(gitDetails.getFileUrl());
    gitDetails1.setRepoName(gitDetails.getRepoName());
    gitDetails1.setObjectId(gitDetails.getObjectId());
    gitDetails1.setRepoUrl(gitDetails.getRepoUrl());
    return gitDetails1;
  }

  io.harness.spec.server.pipeline.v1.model.ExecutionErrorInfo toExecutionErrorInfoV1(
      ExecutionErrorInfo executionErrorInfo) {
    if (executionErrorInfo == null) {
      return null;
    }
    io.harness.spec.server.pipeline.v1.model.ExecutionErrorInfo executionErrorInfo1 =
        new io.harness.spec.server.pipeline.v1.model.ExecutionErrorInfo();
    executionErrorInfo1.setMessage(executionErrorInfo.getMessage());
    return executionErrorInfo1;
  }

  ParentStageInfo toParentStageInfoV1(PipelineStageInfo pipelineStageInfo) {
    if (pipelineStageInfo == null) {
      return null;
    }
    ParentStageInfo parentStageInfo = new ParentStageInfo();
    parentStageInfo.setExecutionId(pipelineStageInfo.getExecutionId());
    parentStageInfo.setStageNodeId(pipelineStageInfo.getStageNodeId());
    parentStageInfo.setOrgId(pipelineStageInfo.getOrgId());
    parentStageInfo.setName(pipelineStageInfo.getPipelineName());
    parentStageInfo.setIdentifier(pipelineStageInfo.getIdentifier());
    parentStageInfo.setProjectId(pipelineStageInfo.getProjectId());
    parentStageInfo.setRunSequence(pipelineStageInfo.getRunSequence());
    parentStageInfo.setHasParentPipeline(pipelineStageInfo.getHasParentPipeline());
    return parentStageInfo;
  }

  ExecutionTriggerInfo toExecutionTriggerInfoV1(
      io.harness.pms.contracts.plan.ExecutionTriggerInfo executionTriggerInfo) {
    ExecutionTriggerInfo executionTriggerInfo1 = new ExecutionTriggerInfo();
    executionTriggerInfo1.setBuildInfo(toBuildInfoV1(executionTriggerInfo.getBuildInfo()));
    executionTriggerInfo1.setTriggeredBy(executionTriggerInfo1.getTriggeredBy());
    executionTriggerInfo1.setRerunInfo(toRerunInfoV1(executionTriggerInfo.getRerunInfo()));
    executionTriggerInfo1.setIsRerun(executionTriggerInfo.getIsRerun());
    executionTriggerInfo1.setTriggerType(toTriggerTypeV1(executionTriggerInfo.getTriggerType()));
    return executionTriggerInfo1;
  }

  TriggerType toTriggerTypeV1(io.harness.pms.contracts.plan.TriggerType triggerType) {
    switch (triggerType) {
      case ARTIFACT:
        return TriggerType.ARTIFACT;
      case MANIFEST:
        return TriggerType.MANIFEST;
      case WEBHOOK:
        return TriggerType.WEBHOOK;
      case NOOP:
        return TriggerType.NOOP;
      case MANUAL:
        return TriggerType.MANUAL;
      case SCHEDULER_CRON:
        return TriggerType.SCHEDULER_CRON;
      case WEBHOOK_CUSTOM:
        return TriggerType.WEBHOOK_CUSTOM;
      default:
        throw new InvalidRequestException(String.format("Invalid trigger type %s", triggerType));
    }
  }

  io.harness.spec.server.pipeline.v1.model.RerunInfo toRerunInfoV1(RerunInfo rerunInfo) {
    io.harness.spec.server.pipeline.v1.model.RerunInfo rerunInfo1 =
        new io.harness.spec.server.pipeline.v1.model.RerunInfo();
    rerunInfo1.setPrevExecutionId(rerunInfo.getPrevExecutionId());
    rerunInfo1.setRootExecutionId(rerunInfo.getRootExecutionId());
    rerunInfo1.setPrevTriggerType(toTriggerTypeV1(rerunInfo.getPrevTriggerType()));
    rerunInfo1.setRootTriggerType(toTriggerTypeV1(rerunInfo.getRootTriggerType()));
    return rerunInfo1;
  }

  io.harness.spec.server.pipeline.v1.model.BuildInfo toBuildInfoV1(BuildInfo buildInfo) {
    io.harness.spec.server.pipeline.v1.model.BuildInfo buildInfo1 =
        new io.harness.spec.server.pipeline.v1.model.BuildInfo();
    buildInfo1.setBuild(buildInfo.getBuild());
    buildInfo1.setImagePath(buildInfo.getImagePath());
    return buildInfo1;
  }

  public FailureInfo toFailureInfoDTOV1(FailureInfoDTO failureInfoDTO) {
    FailureInfo failureInfo = new FailureInfo();
    if (failureInfoDTO == null) {
      return null;
    }
    failureInfo.setMessage(failureInfoDTO.getMessage());
    if (EmptyPredicate.isNotEmpty(failureInfoDTO.getFailureTypeList())) {
      failureInfo.setFailureTypeList(failureInfoDTO.getFailureTypeList()
                                         .stream()
                                         .map(PipelineExecutionDetailsApiUtils::toFailureTypeV1)
                                         .collect(Collectors.toList()));
    } else {
      failureInfo.setFailureTypeList(Collections.emptyList());
    }
    if (EmptyPredicate.isNotEmpty(failureInfoDTO.getResponseMessages())) {
      failureInfo.setResponseMessages(failureInfoDTO.getResponseMessages()
                                          .stream()
                                          .map(PipelineExecutionDetailsApiUtils::toResponseMessageV1)
                                          .collect(Collectors.toList()));
    } else {
      failureInfo.setResponseMessages(Collections.emptyList());
    }
    return failureInfo;
  }

  io.harness.spec.server.pipeline.v1.model.ResponseMessage toResponseMessageV1(ResponseMessage responseMessage) {
    io.harness.spec.server.pipeline.v1.model.ResponseMessage responseMessage1 =
        new io.harness.spec.server.pipeline.v1.model.ResponseMessage();
    responseMessage1.setMessage(responseMessage.getMessage());
    responseMessage1.setCode(toCodeV1(responseMessage.getCode()));
    responseMessage1.setException(responseMessage.getException());
    responseMessage1.setLevel(toLevelV1(responseMessage.getLevel()));
    responseMessage1.setFailureTypes(responseMessage.getFailureTypes()
                                         .stream()
                                         .map(PipelineExecutionDetailsApiUtils::toFailureTypeV1)
                                         .collect(Collectors.toList()));
    return responseMessage1;
  }

  io.harness.spec.server.pipeline.v1.model.ResponseMessage.LevelEnum toLevelV1(Level level) {
    switch (level) {
      case INFO:
        return io.harness.spec.server.pipeline.v1.model.ResponseMessage.LevelEnum.INFO;
      case ERROR:
        return io.harness.spec.server.pipeline.v1.model.ResponseMessage.LevelEnum.ERROR;
      default:
        throw new InvalidRequestException(String.format("Invalid level %s", level));
    }
  }

  public io.harness.spec.server.pipeline.v1.model.ErrorCode toCodeV1(ErrorCode errorCode) {
    switch (errorCode) {
      case DEFAULT_ERROR_CODE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DEFAULT_ERROR_CODE;
      case INVALID_ARGUMENT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_ARGUMENT;
      case INVALID_EMAIL:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_EMAIL;
      case DOMAIN_NOT_ALLOWED_TO_REGISTER:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DOMAIN_NOT_ALLOWED_TO_REGISTER;
      case COMMNITY_EDITION_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.COMMNITY_EDITION_NOT_FOUND;
      case DEPLOY_MODE_IS_NOT_ON_PREM:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DEPLOY_MODE_IS_NOT_ON_PREM;
      case USER_ALREADY_REGISTERED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_ALREADY_REGISTERED;
      case USER_INVITATION_DOES_NOT_EXIST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_INVITATION_DOES_NOT_EXIST;
      case USER_DOES_NOT_EXIST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_DOES_NOT_EXIST;
      case USER_INVITE_OPERATION_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_INVITE_OPERATION_FAILED;
      case USER_DISABLED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_DISABLED;
      case ACCOUNT_DOES_NOT_EXIST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ACCOUNT_DOES_NOT_EXIST;
      case INACTIVE_ACCOUNT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INACTIVE_ACCOUNT;
      case ACCOUNT_MIGRATED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ACCOUNT_MIGRATED;
      case ACCOUNT_MIGRATED_TO_NEXT_GEN:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ACCOUNT_MIGRATED_TO_NEXT_GEN;
      case USER_DOMAIN_NOT_ALLOWED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_DOMAIN_NOT_ALLOWED;
      case MAX_FAILED_ATTEMPT_COUNT_EXCEEDED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.MAX_FAILED_ATTEMPT_COUNT_EXCEEDED;
      case RESOURCE_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.RESOURCE_NOT_FOUND;
      case INVALID_FORMAT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_FORMAT;
      case ROLE_DOES_NOT_EXIST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ROLE_DOES_NOT_EXIST;
      case EMAIL_NOT_VERIFIED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.EMAIL_NOT_VERIFIED;
      case EMAIL_VERIFICATION_TOKEN_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.EMAIL_VERIFICATION_TOKEN_NOT_FOUND;
      case INVALID_TOKEN:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_TOKEN;
      case REVOKED_TOKEN:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.REVOKED_TOKEN;
      case INVALID_CAPTCHA_TOKEN:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_CAPTCHA_TOKEN;
      case NOT_ACCOUNT_MGR_NOR_HAS_ALL_APP_ACCESS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NOT_ACCOUNT_MGR_NOR_HAS_ALL_APP_ACCESS;
      case EXPIRED_TOKEN:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.EXPIRED_TOKEN;
      case INVALID_AGENT_MTLS_AUTHORITY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_AGENT_MTLS_AUTHORITY;
      case TOKEN_ALREADY_REFRESHED_ONCE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TOKEN_ALREADY_REFRESHED_ONCE;
      case ACCESS_DENIED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ACCESS_DENIED;
      case NG_ACCESS_DENIED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NG_ACCESS_DENIED;
      case INVALID_CREDENTIAL:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_CREDENTIAL;
      case INVALID_CREDENTIALS_THIRD_PARTY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_CREDENTIALS_THIRD_PARTY;
      case INVALID_KEY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_KEY;
      case INVALID_CONNECTOR_TYPE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_CONNECTOR_TYPE;
      case INVALID_KEYPATH:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_KEYPATH;
      case INVALID_VARIABLE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_VARIABLE;
      case UNKNOWN_HOST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNKNOWN_HOST;
      case UNREACHABLE_HOST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNREACHABLE_HOST;
      case INVALID_PORT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_PORT;
      case SSH_SESSION_TIMEOUT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SSH_SESSION_TIMEOUT;
      case ALGORITHM_NEGOTIATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ALGORITHM_NEGOTIATION_ERROR;
      case SOCKET_CONNECTION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SOCKET_CONNECTION_ERROR;
      case CONNECTION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.CONNECTION_ERROR;
      case SOCKET_CONNECTION_TIMEOUT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SOCKET_CONNECTION_TIMEOUT;
      case WINRM_COMMAND_EXECUTION_TIMEOUT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.WINRM_COMMAND_EXECUTION_TIMEOUT;
      case CONNECTION_TIMEOUT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.CONNECTION_TIMEOUT;
      case SSH_CONNECTION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SSH_CONNECTION_ERROR;
      case USER_GROUP_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_GROUP_ERROR;
      case INVALID_EXECUTION_ID:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_EXECUTION_ID;
      case ERROR_IN_GETTING_CHANNEL_STREAMS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ERROR_IN_GETTING_CHANNEL_STREAMS;
      case UNEXPECTED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNEXPECTED;
      case UNKNOWN_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNKNOWN_ERROR;
      case UNKNOWN_EXECUTOR_TYPE_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNKNOWN_EXECUTOR_TYPE_ERROR;
      case DUPLICATE_STATE_NAMES:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DUPLICATE_STATE_NAMES;
      case TRANSITION_NOT_LINKED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TRANSITION_NOT_LINKED;
      case TRANSITION_TO_INCORRECT_STATE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TRANSITION_TO_INCORRECT_STATE;
      case TRANSITION_TYPE_NULL:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TRANSITION_TYPE_NULL;
      case STATES_WITH_DUP_TRANSITIONS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.STATES_WITH_DUP_TRANSITIONS;
      case BARRIERS_NOT_RUNNING_CONCURRENTLY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.BARRIERS_NOT_RUNNING_CONCURRENTLY;
      case NON_FORK_STATES:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NON_FORK_STATES;
      case NON_REPEAT_STATES:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NON_REPEAT_STATES;
      case INITIAL_STATE_NOT_DEFINED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INITIAL_STATE_NOT_DEFINED;
      case FILE_INTEGRITY_CHECK_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.FILE_INTEGRITY_CHECK_FAILED;
      case INVALID_URL:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_URL;
      case FILE_DOWNLOAD_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.FILE_DOWNLOAD_FAILED;
      case PLATFORM_SOFTWARE_DELETE_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PLATFORM_SOFTWARE_DELETE_ERROR;
      case INVALID_CSV_FILE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_CSV_FILE;
      case INVALID_REQUEST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_REQUEST;
      case SCHEMA_VALIDATION_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCHEMA_VALIDATION_FAILED;
      case FILTER_CREATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.FILTER_CREATION_ERROR;
      case INVALID_YAML_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_YAML_ERROR;
      case PLAN_CREATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PLAN_CREATION_ERROR;
      case INVALID_INFRA_STATE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_INFRA_STATE;
      case PIPELINE_ALREADY_TRIGGERED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PIPELINE_ALREADY_TRIGGERED;
      case NON_EXISTING_PIPELINE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NON_EXISTING_PIPELINE;
      case DUPLICATE_COMMAND_NAMES:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DUPLICATE_COMMAND_NAMES;
      case INVALID_PIPELINE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_PIPELINE;
      case COMMAND_DOES_NOT_EXIST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.COMMAND_DOES_NOT_EXIST;
      case DUPLICATE_ARTIFACTSTREAM_NAMES:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DUPLICATE_ARTIFACTSTREAM_NAMES;
      case DUPLICATE_HOST_NAMES:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DUPLICATE_HOST_NAMES;
      case STATE_NOT_FOR_TYPE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.STATE_NOT_FOR_TYPE;
      case STATE_MACHINE_ISSUE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.STATE_MACHINE_ISSUE;
      case STATE_DISCONTINUE_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.STATE_DISCONTINUE_FAILED;
      case STATE_PAUSE_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.STATE_PAUSE_FAILED;
      case PAUSE_ALL_ALREADY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PAUSE_ALL_ALREADY;
      case RESUME_ALL_ALREADY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.RESUME_ALL_ALREADY;
      case ROLLBACK_ALREADY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ROLLBACK_ALREADY;
      case ABORT_ALL_ALREADY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ABORT_ALL_ALREADY;
      case EXPIRE_ALL_ALREADY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.EXPIRE_ALL_ALREADY;
      case RETRY_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.RETRY_FAILED;
      case UNKNOWN_ARTIFACT_TYPE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNKNOWN_ARTIFACT_TYPE;
      case UNKNOWN_STAGE_ELEMENT_WRAPPER_TYPE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNKNOWN_STAGE_ELEMENT_WRAPPER_TYPE;
      case INIT_TIMEOUT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INIT_TIMEOUT;
      case LICENSE_EXPIRED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.LICENSE_EXPIRED;
      case NOT_LICENSED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NOT_LICENSED;
      case REQUEST_TIMEOUT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.REQUEST_TIMEOUT;
      case SCM_REQUEST_TIMEOUT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_REQUEST_TIMEOUT;
      case WORKFLOW_ALREADY_TRIGGERED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.WORKFLOW_ALREADY_TRIGGERED;
      case JENKINS_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.JENKINS_ERROR;
      case INVALID_ARTIFACT_SOURCE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_ARTIFACT_SOURCE;
      case INVALID_ARTIFACT_SERVER:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_ARTIFACT_SERVER;
      case INVALID_CLOUD_PROVIDER:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_CLOUD_PROVIDER;
      case UPDATE_NOT_ALLOWED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UPDATE_NOT_ALLOWED;
      case DELETE_NOT_ALLOWED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DELETE_NOT_ALLOWED;
      case APPDYNAMICS_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.APPDYNAMICS_CONFIGURATION_ERROR;
      case APM_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.APM_CONFIGURATION_ERROR;
      case SPLUNK_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SPLUNK_CONFIGURATION_ERROR;
      case ELK_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ELK_CONFIGURATION_ERROR;
      case LOGZ_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.LOGZ_CONFIGURATION_ERROR;
      case SUMO_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SUMO_CONFIGURATION_ERROR;
      case INSTANA_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INSTANA_CONFIGURATION_ERROR;
      case APPDYNAMICS_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.APPDYNAMICS_ERROR;
      case STACKDRIVER_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.STACKDRIVER_ERROR;
      case STACKDRIVER_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.STACKDRIVER_CONFIGURATION_ERROR;
      case NEWRELIC_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NEWRELIC_CONFIGURATION_ERROR;
      case NEWRELIC_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NEWRELIC_ERROR;
      case DYNA_TRACE_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DYNA_TRACE_CONFIGURATION_ERROR;
      case DYNA_TRACE_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DYNA_TRACE_ERROR;
      case CLOUDWATCH_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.CLOUDWATCH_ERROR;
      case CLOUDWATCH_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.CLOUDWATCH_CONFIGURATION_ERROR;
      case PROMETHEUS_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PROMETHEUS_CONFIGURATION_ERROR;
      case DATA_DOG_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DATA_DOG_CONFIGURATION_ERROR;
      case SERVICE_GUARD_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SERVICE_GUARD_CONFIGURATION_ERROR;
      case ENCRYPTION_NOT_CONFIGURED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ENCRYPTION_NOT_CONFIGURED;
      case UNAVAILABLE_DELEGATES:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNAVAILABLE_DELEGATES;
      case WORKFLOW_EXECUTION_IN_PROGRESS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.WORKFLOW_EXECUTION_IN_PROGRESS;
      case PIPELINE_EXECUTION_IN_PROGRESS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PIPELINE_EXECUTION_IN_PROGRESS;
      case AWS_ACCESS_DENIED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_ACCESS_DENIED;
      case AWS_CLUSTER_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_CLUSTER_NOT_FOUND;
      case AWS_SERVICE_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_SERVICE_NOT_FOUND;
      case IMAGE_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.IMAGE_NOT_FOUND;
      case ILLEGAL_ARGUMENT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ILLEGAL_ARGUMENT;
      case IMAGE_TAG_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.IMAGE_TAG_NOT_FOUND;
      case DELEGATE_NOT_AVAILABLE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DELEGATE_NOT_AVAILABLE;
      case INVALID_YAML_PAYLOAD:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_YAML_PAYLOAD;
      case AUTHENTICATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AUTHENTICATION_ERROR;
      case AUTHORIZATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AUTHORIZATION_ERROR;
      case UNRECOGNIZED_YAML_FIELDS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNRECOGNIZED_YAML_FIELDS;
      case COULD_NOT_MAP_BEFORE_YAML:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.COULD_NOT_MAP_BEFORE_YAML;
      case MISSING_BEFORE_YAML:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.MISSING_BEFORE_YAML;
      case MISSING_YAML:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.MISSING_YAML;
      case NON_EMPTY_DELETIONS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NON_EMPTY_DELETIONS;
      case GENERAL_YAML_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GENERAL_YAML_ERROR;
      case GENERAL_YAML_INFO:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GENERAL_YAML_INFO;
      case YAML_GIT_SYNC_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.YAML_GIT_SYNC_ERROR;
      case GIT_CONNECTION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GIT_CONNECTION_ERROR;
      case GIT_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GIT_ERROR;
      case ARTIFACT_SERVER_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ARTIFACT_SERVER_ERROR;
      case ENCRYPT_DECRYPT_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ENCRYPT_DECRYPT_ERROR;
      case SECRET_MANAGEMENT_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SECRET_MANAGEMENT_ERROR;
      case SECRET_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SECRET_NOT_FOUND;
      case KMS_OPERATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.KMS_OPERATION_ERROR;
      case GCP_KMS_OPERATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GCP_KMS_OPERATION_ERROR;
      case VAULT_OPERATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.VAULT_OPERATION_ERROR;
      case AWS_SECRETS_MANAGER_OPERATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_SECRETS_MANAGER_OPERATION_ERROR;
      case AZURE_KEY_VAULT_OPERATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AZURE_KEY_VAULT_OPERATION_ERROR;
      case AZURE_KEY_VAULT_INTERRUPT_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AZURE_KEY_VAULT_INTERRUPT_ERROR;
      case UNSUPPORTED_OPERATION_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION;
      case FEATURE_UNAVAILABLE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.FEATURE_UNAVAILABLE;
      case GENERAL_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GENERAL_ERROR;
      case BASELINE_CONFIGURATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.BASELINE_CONFIGURATION_ERROR;
      case SAML_IDP_CONFIGURATION_NOT_AVAILABLE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SAML_IDP_CONFIGURATION_NOT_AVAILABLE;
      case INVALID_AUTHENTICATION_MECHANISM:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_AUTHENTICATION_MECHANISM;
      case INVALID_SAML_CONFIGURATION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_SAML_CONFIGURATION;
      case INVALID_OAUTH_CONFIGURATION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_OAUTH_CONFIGURATION;
      case INVALID_LDAP_CONFIGURATION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_LDAP_CONFIGURATION;
      case USER_GROUP_SYNC_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_GROUP_SYNC_FAILURE;
      case USER_GROUP_ALREADY_EXIST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_GROUP_ALREADY_EXIST;
      case INVALID_TWO_FACTOR_AUTHENTICATION_CONFIGURATION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_TWO_FACTOR_AUTHENTICATION_CONFIGURATION;
      case EXPLANATION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.EXPLANATION;
      case HINT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.HINT;
      case NOT_WHITELISTED_IP:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NOT_WHITELISTED_IP;
      case INVALID_TOTP_TOKEN:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_TOTP_TOKEN;
      case EMAIL_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.EMAIL_FAILED;
      case SSL_HANDSHAKE_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SSL_HANDSHAKE_FAILED;
      case NO_APPS_ASSIGNED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NO_APPS_ASSIGNED;
      case INVALID_INFRA_CONFIGURATION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_INFRA_CONFIGURATION;
      case TEMPLATES_LINKED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TEMPLATES_LINKED;
      case USER_HAS_NO_PERMISSIONS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_HAS_NO_PERMISSIONS;
      case USER_NOT_AUTHORIZED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_NOT_AUTHORIZED;
      case USER_ALREADY_PRESENT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_ALREADY_PRESENT;
      case EMAIL_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.EMAIL_ERROR;
      case INVALID_USAGE_RESTRICTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_USAGE_RESTRICTION;
      case USAGE_RESTRICTION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USAGE_RESTRICTION_ERROR;
      case STATE_EXECUTION_INSTANCE_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.STATE_EXECUTION_INSTANCE_NOT_FOUND;
      case DELEGATE_TASK_RETRY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DELEGATE_TASK_RETRY;
      case KUBERNETES_API_TASK_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.KUBERNETES_API_TASK_EXCEPTION;
      case KUBERNETES_TASK_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.KUBERNETES_TASK_EXCEPTION;
      case KUBERNETES_YAML_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.KUBERNETES_YAML_ERROR;
      case SAVE_FILE_INTO_AWS_STORAGE_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SAVE_FILE_INTO_AWS_STORAGE_FAILED;
      case READ_FILE_FROM_AWS_STORAGE_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.READ_FILE_FROM_AWS_STORAGE_FAILED;
      case SAVE_FILE_INTO_GCP_STORAGE_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SAVE_FILE_INTO_GCP_STORAGE_FAILED;
      case READ_FILE_FROM_GCP_STORAGE_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.READ_FILE_FROM_GCP_STORAGE_FAILED;
      case FILE_NOT_FOUND_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.FILE_NOT_FOUND_ERROR;
      case USAGE_LIMITS_EXCEEDED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USAGE_LIMITS_EXCEEDED;
      case EVENT_PUBLISH_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.EVENT_PUBLISH_FAILED;
      case CUSTOM_APPROVAL_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.CUSTOM_APPROVAL_ERROR;
      case JIRA_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.JIRA_ERROR;
      case EXPRESSION_EVALUATION_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.EXPRESSION_EVALUATION_FAILED;
      case KUBERNETES_VALUES_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.KUBERNETES_VALUES_ERROR;
      case KUBERNETES_CLUSTER_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.KUBERNETES_CLUSTER_ERROR;
      case INCORRECT_SIGN_IN_MECHANISM:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INCORRECT_SIGN_IN_MECHANISM;
      case OAUTH_LOGIN_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.OAUTH_LOGIN_FAILED;
      case INVALID_TERRAFORM_TARGETS_REQUEST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_TERRAFORM_TARGETS_REQUEST;
      case TERRAFORM_EXECUTION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TERRAFORM_EXECUTION_ERROR;
      case FILE_READ_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.FILE_READ_FAILED;
      case FILE_SIZE_EXCEEDS_LIMIT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.FILE_SIZE_EXCEEDS_LIMIT;
      case CLUSTER_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.CLUSTER_NOT_FOUND;
      case MARKETPLACE_TOKEN_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.MARKETPLACE_TOKEN_NOT_FOUND;
      case INVALID_MARKETPLACE_TOKEN:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_MARKETPLACE_TOKEN;
      case INVALID_TICKETING_SERVER:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_TICKETING_SERVER;
      case SERVICENOW_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SERVICENOW_ERROR;
      case PASSWORD_EXPIRED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PASSWORD_EXPIRED;
      case USER_LOCKED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_LOCKED;
      case PASSWORD_STRENGTH_CHECK_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PASSWORD_STRENGTH_CHECK_FAILED;
      case ACCOUNT_DISABLED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ACCOUNT_DISABLED;
      case INVALID_ACCOUNT_PERMISSION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_ACCOUNT_PERMISSION;
      case PAGERDUTY_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PAGERDUTY_ERROR;
      case HEALTH_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.HEALTH_ERROR;
      case SAML_TEST_SUCCESS_MECHANISM_NOT_ENABLED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SAML_TEST_SUCCESS_MECHANISM_NOT_ENABLED;
      case DOMAIN_WHITELIST_FILTER_CHECK_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DOMAIN_WHITELIST_FILTER_CHECK_FAILED;
      case INVALID_DASHBOARD_UPDATE_REQUEST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_DASHBOARD_UPDATE_REQUEST;
      case DUPLICATE_FIELD:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DUPLICATE_FIELD;
      case INVALID_AZURE_VAULT_CONFIGURATION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_AZURE_VAULT_CONFIGURATION;
      case USER_NOT_AUTHORIZED_DUE_TO_USAGE_RESTRICTIONS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_NOT_AUTHORIZED_DUE_TO_USAGE_RESTRICTIONS;
      case INVALID_ROLLBACK:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_ROLLBACK;
      case DATA_COLLECTION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DATA_COLLECTION_ERROR;
      case SUMO_DATA_COLLECTION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SUMO_DATA_COLLECTION_ERROR;
      case DEPLOYMENT_GOVERNANCE_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DEPLOYMENT_GOVERNANCE_ERROR;
      case BATCH_PROCESSING_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.BATCH_PROCESSING_ERROR;
      case GRAPHQL_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GRAPHQL_ERROR;
      case FILE_CREATE_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.FILE_CREATE_ERROR;
      case ILLEGAL_STATE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ILLEGAL_STATE;
      case GIT_DIFF_COMMIT_NOT_IN_ORDER:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GIT_DIFF_COMMIT_NOT_IN_ORDER;
      case FAILED_TO_ACQUIRE_PERSISTENT_LOCK:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.FAILED_TO_ACQUIRE_PERSISTENT_LOCK;
      case FAILED_TO_ACQUIRE_NON_PERSISTENT_LOCK:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.FAILED_TO_ACQUIRE_NON_PERSISTENT_LOCK;
      case POD_NOT_FOUND_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.POD_NOT_FOUND_ERROR;
      case COMMAND_EXECUTION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.COMMAND_EXECUTION_ERROR;
      case REGISTRY_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.REGISTRY_EXCEPTION;
      case ENGINE_INTERRUPT_PROCESSING_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ENGINE_INTERRUPT_PROCESSING_EXCEPTION;
      case ENGINE_IO_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ENGINE_IO_EXCEPTION;
      case ENGINE_OUTCOME_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ENGINE_OUTCOME_EXCEPTION;
      case ENGINE_SWEEPING_OUTPUT_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ENGINE_SWEEPING_OUTPUT_EXCEPTION;
      case CACHE_NOT_FOUND_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.CACHE_NOT_FOUND_EXCEPTION;
      case ENGINE_ENTITY_UPDATE_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ENGINE_ENTITY_UPDATE_EXCEPTION;
      case SHELL_EXECUTION_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SHELL_EXECUTION_EXCEPTION;
      case TEMPLATE_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TEMPLATE_NOT_FOUND;
      case AZURE_SERVICE_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AZURE_SERVICE_EXCEPTION;
      case AZURE_CLIENT_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AZURE_CLIENT_EXCEPTION;
      case GIT_UNSEEN_REMOTE_HEAD_COMMIT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GIT_UNSEEN_REMOTE_HEAD_COMMIT;
      case TIMEOUT_ENGINE_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TIMEOUT_ENGINE_EXCEPTION;
      case NO_AVAILABLE_DELEGATES:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NO_AVAILABLE_DELEGATES;
      case NO_GLOBAL_DELEGATE_ACCOUNT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NO_GLOBAL_DELEGATE_ACCOUNT;
      case NO_INSTALLED_DELEGATES:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NO_INSTALLED_DELEGATES;
      case DUPLICATE_DELEGATE_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DUPLICATE_DELEGATE_EXCEPTION;
      case GCP_MARKETPLACE_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GCP_MARKETPLACE_EXCEPTION;
      case MISSING_DEFAULT_GOOGLE_CREDENTIALS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.MISSING_DEFAULT_GOOGLE_CREDENTIALS;
      case INCORRECT_DEFAULT_GOOGLE_CREDENTIALS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INCORRECT_DEFAULT_GOOGLE_CREDENTIALS;
      case OPTIMISTIC_LOCKING_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.OPTIMISTIC_LOCKING_EXCEPTION;
      case NG_PIPELINE_EXECUTION_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NG_PIPELINE_EXECUTION_EXCEPTION;
      case NG_PIPELINE_CREATE_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NG_PIPELINE_CREATE_EXCEPTION;
      case RESOURCE_NOT_FOUND_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.RESOURCE_NOT_FOUND_EXCEPTION;
      case PMS_INITIALIZE_SDK_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PMS_INITIALIZE_SDK_EXCEPTION;
      case UNEXPECTED_SNIPPET_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNEXPECTED_SNIPPET_EXCEPTION;
      case UNEXPECTED_SCHEMA_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNEXPECTED_SCHEMA_EXCEPTION;
      case CONNECTOR_VALIDATION_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.CONNECTOR_VALIDATION_EXCEPTION;
      case TIMESCALE_NOT_AVAILABLE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TIMESCALE_NOT_AVAILABLE;
      case MIGRATION_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.MIGRATION_EXCEPTION;
      case REQUEST_PROCESSING_INTERRUPTED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.REQUEST_PROCESSING_INTERRUPTED;
      case SECRET_MANAGER_ID_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SECRET_MANAGER_ID_NOT_FOUND;
      case GCP_SECRET_MANAGER_OPERATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GCP_SECRET_MANAGER_OPERATION_ERROR;
      case GCP_SECRET_OPERATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GCP_SECRET_OPERATION_ERROR;
      case GIT_OPERATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GIT_OPERATION_ERROR;
      case TASK_FAILURE_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TASK_FAILURE_ERROR;
      case INSTANCE_STATS_PROCESS_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INSTANCE_STATS_PROCESS_ERROR;
      case INSTANCE_STATS_MIGRATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INSTANCE_STATS_MIGRATION_ERROR;
      case DEPLOYMENT_MIGRATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DEPLOYMENT_MIGRATION_ERROR;
      case CG_LICENSE_USAGE_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.CG_LICENSE_USAGE_ERROR;
      case INSTANCE_STATS_AGGREGATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INSTANCE_STATS_AGGREGATION_ERROR;
      case UNRESOLVED_EXPRESSIONS_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNRESOLVED_EXPRESSIONS_ERROR;
      case UNRESOLVED_EXPRESSIONS_WITH_CONTEXT_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNRESOLVED_EXPRESSIONS_WITH_CONTEXT_ERROR;
      case KRYO_HANDLER_NOT_FOUND_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.KRYO_HANDLER_NOT_FOUND_ERROR;
      case DELEGATE_ERROR_HANDLER_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DELEGATE_ERROR_HANDLER_EXCEPTION;
      case DELEGATE_SERVICE_DRIVER_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DELEGATE_SERVICE_DRIVER_EXCEPTION;
      case DELEGATE_INSTALLATION_COMMAND_NOT_SUPPORTED_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DELEGATE_INSTALLATION_COMMAND_NOT_SUPPORTED_EXCEPTION;
      case UNEXPECTED_TYPE_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.UNEXPECTED_TYPE_ERROR;
      case EXCEPTION_HANDLER_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.EXCEPTION_HANDLER_NOT_FOUND;
      case CONNECTOR_NOT_FOUND_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.CONNECTOR_NOT_FOUND_EXCEPTION;
      case GCP_SERVER_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GCP_SERVER_ERROR;
      case HTTP_RESPONSE_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.HTTP_RESPONSE_EXCEPTION;
      case SCM_NOT_FOUND_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_NOT_FOUND_ERROR;
      case SCM_CONFLICT_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_CONFLICT_ERROR;
      case SCM_CONFLICT_ERROR_V2:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_CONFLICT_ERROR_V2;
      case SCM_UNPROCESSABLE_ENTITY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_UNPROCESSABLE_ENTITY;
      case PROCESS_EXECUTION_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PROCESS_EXECUTION_EXCEPTION;
      case SCM_UNAUTHORIZED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_UNAUTHORIZED;
      case SCM_BAD_REQUEST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_BAD_REQUEST;
      case SCM_INTERNAL_SERVER_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_INTERNAL_SERVER_ERROR;
      case DATA:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DATA;
      case CONTEXT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.CONTEXT;
      case PR_CREATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PR_CREATION_ERROR;
      case URL_NOT_REACHABLE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.URL_NOT_REACHABLE;
      case URL_NOT_PROVIDED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.URL_NOT_PROVIDED;
      case ENGINE_EXPRESSION_EVALUATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ENGINE_EXPRESSION_EVALUATION_ERROR;
      case ENGINE_FUNCTOR_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ENGINE_FUNCTOR_ERROR;
      case JIRA_CLIENT_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.JIRA_CLIENT_ERROR;
      case SCM_NOT_MODIFIED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_NOT_MODIFIED;
      case APPROVAL_STEP_NG_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.APPROVAL_STEP_NG_ERROR;
      case BUCKET_SERVER_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.BUCKET_SERVER_ERROR;
      case GIT_SYNC_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GIT_SYNC_ERROR;
      case TEMPLATE_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TEMPLATE_EXCEPTION;
      case TEMPLATE_ALREADY_EXISTS_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TEMPLATE_ALREADY_EXISTS_EXCEPTION;
      case ENTITY_REFERENCE_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ENTITY_REFERENCE_EXCEPTION;
      case ACTIVE_SERVICE_INSTANCES_PRESENT_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ACTIVE_SERVICE_INSTANCES_PRESENT_EXCEPTION;
      case INVALID_INPUT_SET:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_INPUT_SET;
      case INVALID_OVERLAY_INPUT_SET:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_OVERLAY_INPUT_SET;
      case RESOURCE_ALREADY_EXISTS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.RESOURCE_ALREADY_EXISTS;
      case INVALID_JSON_PAYLOAD:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_JSON_PAYLOAD;
      case POLICY_EVALUATION_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.POLICY_EVALUATION_FAILURE;
      case POLICY_SET_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.POLICY_SET_ERROR;
      case INVALID_ARTIFACTORY_REGISTRY_REQUEST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_ARTIFACTORY_REGISTRY_REQUEST;
      case INVALID_NEXUS_REGISTRY_REQUEST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_NEXUS_REGISTRY_REQUEST;
      case ENTITY_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ENTITY_NOT_FOUND;
      case INVALID_AZURE_CONTAINER_REGISTRY_REQUEST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_AZURE_CONTAINER_REGISTRY_REQUEST;
      case AZURE_AUTHENTICATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AZURE_AUTHENTICATION_ERROR;
      case AZURE_CONFIG_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AZURE_CONFIG_ERROR;
      case DATA_PROCESSING_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DATA_PROCESSING_ERROR;
      case INVALID_AZURE_AKS_REQUEST:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_AZURE_AKS_REQUEST;
      case AWS_IAM_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_IAM_ERROR;
      case AWS_CF_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_CF_ERROR;
      case AWS_INSTANCE_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_INSTANCE_ERROR;
      case AWS_VPC_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_VPC_ERROR;
      case AWS_TAG_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_TAG_ERROR;
      case AWS_ASG_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_ASG_ERROR;
      case AWS_LOAD_BALANCER_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_LOAD_BALANCER_ERROR;
      case SCM_INTERNAL_SERVER_ERROR_V2:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_INTERNAL_SERVER_ERROR_V2;
      case SCM_UNAUTHORIZED_ERROR_V2:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_UNAUTHORIZED_ERROR_V2;
      case TOO_MANY_REQUESTS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TOO_MANY_REQUESTS;
      case SCM_RATE_LIMIT_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_RATE_LIMIT_ERROR;
      case INVALID_IDENTIFIER_REF:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_IDENTIFIER_REF;
      case SPOTINST_NULL_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SPOTINST_NULL_ERROR;
      case SPOTNIST_REST_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SPOTNIST_REST_EXCEPTION;
      case SCM_UNEXPECTED_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_UNEXPECTED_ERROR;
      case DUPLICATE_FILE_IMPORT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DUPLICATE_FILE_IMPORT;
      case AZURE_APP_SERVICES_TASK_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AZURE_APP_SERVICES_TASK_EXCEPTION;
      case AZURE_ARM_TASK_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AZURE_ARM_TASK_EXCEPTION;
      case AZURE_BP_TASK_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AZURE_BP_TASK_EXCEPTION;
      case MEDIA_NOT_SUPPORTED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.MEDIA_NOT_SUPPORTED;
      case AWS_ECS_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_ECS_ERROR;
      case AWS_APPLICATION_AUTO_SCALING:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_APPLICATION_AUTO_SCALING;
      case AWS_ECS_SERVICE_NOT_ACTIVE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_ECS_SERVICE_NOT_ACTIVE;
      case AWS_ECS_CLIENT_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_ECS_CLIENT_ERROR;
      case AWS_STS_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_STS_ERROR;
      case FREEZE_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.FREEZE_EXCEPTION;
      case MISSING_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.MISSING_EXCEPTION;
      case DELEGATE_TASK_EXPIRED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DELEGATE_TASK_EXPIRED;
      case DELEGATE_TASK_VALIDATION_FAILED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DELEGATE_TASK_VALIDATION_FAILED;
      case MONGO_EXECUTION_TIMEOUT_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.MONGO_EXECUTION_TIMEOUT_EXCEPTION;
      case DELEGATE_NOT_REGISTERED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.DELEGATE_NOT_REGISTERED;
      case TERRAFORM_VAULT_SECRET_CLEANUP_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TERRAFORM_VAULT_SECRET_CLEANUP_FAILURE;
      case APPROVAL_REJECTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.APPROVAL_REJECTION;
      case TERRAGRUNT_EXECUTION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TERRAGRUNT_EXECUTION_ERROR;
      case ADFS_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ADFS_ERROR;
      case TERRAFORM_CLOUD_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TERRAFORM_CLOUD_ERROR;
      case CLUSTER_CREDENTIALS_NOT_FOUND:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.CLUSTER_CREDENTIALS_NOT_FOUND;
      case SCM_FORBIDDEN:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_FORBIDDEN;
      case AWS_EKS_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.AWS_EKS_ERROR;
      case OPA_POLICY_EVALUATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.OPA_POLICY_EVALUATION_ERROR;
      case USER_MARKED_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.USER_MARKED_FAILURE;
      case SSH_RETRY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SSH_RETRY;
      case HTTP_CLIENT_ERROR_RESPONSE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.HTTP_CLIENT_ERROR_RESPONSE;
      case HTTP_INTERNAL_SERVER_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.HTTP_INTERNAL_SERVER_ERROR;
      case HTTP_BAD_GATEWAY:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.HTTP_BAD_GATEWAY;
      case HTTP_SERVICE_UNAVAILABLE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.HTTP_SERVICE_UNAVAILABLE;
      case HTTP_GATEWAY_TIMEOUT:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.HTTP_GATEWAY_TIMEOUT;
      case HTTP_SERVER_ERROR_RESPONSE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.HTTP_SERVER_ERROR_RESPONSE;
      case PIPELINE_UPDATE_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PIPELINE_UPDATE_EXCEPTION;
      case SERVICENOW_REFRESH_TOKEN_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SERVICENOW_REFRESH_TOKEN_ERROR;
      case PARAMETER_FIELD_CAST_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.PARAMETER_FIELD_CAST_ERROR;
      case ABORT_ALL_ALREADY_NG:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ABORT_ALL_ALREADY_NG;
      case WEBHOOK_EXCEPTION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.WEBHOOK_EXCEPTION;
      case INVALID_OIDC_CONFIGURATION:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_OIDC_CONFIGURATION;
      case INVALID_CREDENTIALS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_CREDENTIALS;
      case INVALID_OR_PRIVATE_REPO:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INVALID_OR_PRIVATE_REPO;
      case SCM_API_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_API_ERROR;
      case BARRIER_FAILED_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.BARRIER_FAILED_ERROR;
      case INTERNAL_SERVER_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.INTERNAL_SERVER_ERROR;
      case ELASTICSEARCH_NOT_AVAILABLE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.ELASTICSEARCH_NOT_AVAILABLE;
      case OBJECT_STORE_NOT_AVAILABLE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.OBJECT_STORE_NOT_AVAILABLE;
      case SCM_FAILED_DEPENDENCY_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.SCM_FAILED_DEPENDENCY_ERROR;
      case NO_ELIGIBLE_RUNNERS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NO_ELIGIBLE_RUNNERS;
      case NO_AVAILABLE_RUNNERS:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.NO_AVAILABLE_RUNNERS;
      case RUNNER_DISCONNECTED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.RUNNER_DISCONNECTED;
      case TRANSACTION_ABORTED:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.TRANSACTION_ABORTED;
      case EVENT_LISTENER_STEP_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.EVENT_LISTENER_STEP_FAILURE;
      case GITX_OAUTH_NOT_SET:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.GITX_OAUTH_NOT_SET;
      case COULD_NOT_PROCESS_ERROR:
        return io.harness.spec.server.pipeline.v1.model.ErrorCode.COULD_NOT_PROCESS_ERROR;
      default:
        throw new InvalidRequestException(String.format("Invalid error code %s", errorCode));
    }
  }

  io.harness.spec.server.pipeline.v1.model.FailureType toFailureTypeV1(FailureType failureType) {
    switch (failureType) {
      case EXPIRED:
        return io.harness.spec.server.pipeline.v1.model.FailureType.EXPIRED;
      case CONNECTIVITY:
        return io.harness.spec.server.pipeline.v1.model.FailureType.CONNECTIVITY;
      case TIMEOUT_ERROR:
        return io.harness.spec.server.pipeline.v1.model.FailureType.TIMEOUT_ERROR;
      case AUTHENTICATION:
        return io.harness.spec.server.pipeline.v1.model.FailureType.AUTHENTICATION;
      case DELEGATE_RESTART:
        return io.harness.spec.server.pipeline.v1.model.FailureType.DELEGATE_RESTART;
      case APPLICATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.FailureType.APPLICATION_ERROR;
      case APPROVAL_REJECTION:
        return io.harness.spec.server.pipeline.v1.model.FailureType.APPROVAL_REJECTION;
      case AUTHORIZATION_ERROR:
        return io.harness.spec.server.pipeline.v1.model.FailureType.AUTHORIZATION_ERROR;
      case USER_MARKED_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.FailureType.USER_MARKED_FAILURE;
      case VERIFICATION_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.FailureType.VERIFICATION_FAILURE;
      case DELEGATE_PROVISIONING:
        return io.harness.spec.server.pipeline.v1.model.FailureType.DELEGATE_PROVISIONING;
      case POLICY_EVALUATION_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.FailureType.POLICY_EVALUATION_FAILURE;
      case INPUT_TIMEOUT_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.FailureType.INPUT_TIMEOUT_FAILURE;
      case INFRASTRUCTURE_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.FailureType.INFRASTRUCTURE_FAILURE;
      case PLUGIN_IMAGE_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.FailureType.PLUGIN_IMAGE_FAILURE;
      case RESOURCE_LIMITS_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.FailureType.RESOURCE_LIMITS_FAILURE;
      case CONFIGURATION_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.FailureType.CONFIGURATION_FAILURE;
      case RETRYABLE_TRANSIENT_FAILURE:
        return io.harness.spec.server.pipeline.v1.model.FailureType.RETRYABLE_TRANSIENT_FAILURE;
      default:
        throw new InvalidRequestException(String.format("Invalid failure type %s", failureType));
    }
  }
}

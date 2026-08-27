/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.facilitation.facilitator.CoreFacilitator;
import io.harness.engine.facilitation.facilitator.FacilitatorMetadata;
import io.harness.engine.facilitation.facilitator.async.AsyncFacilitator;
import io.harness.engine.facilitation.facilitator.chain.AsyncChainFacilitator;
import io.harness.engine.facilitation.facilitator.chain.ChildChainFacilitator;
import io.harness.engine.facilitation.facilitator.chain.TaskChainFacilitator;
import io.harness.engine.facilitation.facilitator.child.ChildFacilitator;
import io.harness.engine.facilitation.facilitator.chilidren.ChildrenFacilitator;
import io.harness.engine.facilitation.facilitator.secondary.PreStepCheckFacilitator;
import io.harness.engine.facilitation.facilitator.sync.SyncFacilitator;
import io.harness.engine.facilitation.facilitator.task.TaskFacilitator;
import io.harness.engine.facilitation.facilitator.waitStep.WaitStepFacilitator;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = HarnessModuleComponent.CDS_PIPELINE)
@Slf4j
public class FacilitationHelper {
  @Inject Injector injector;
  @Inject NodeExecutionService nodeExecutionService;

  public boolean customFacilitatorPresent(PlanNode node) {
    if (isEmpty(node.getFacilitatorObtainments())) {
      return true;
    }

    // We are merging primary and secondary facilitator list and then checking if there is custom facilitator present
    // apart from these
    List<String> mergedPrimarySecondaryFacilitatorList =
        Stream
            .concat(OrchestrationFacilitatorType.ALL_PRIMARY_FACILITATOR_TYPES.stream(),
                OrchestrationFacilitatorType.ALL_SECONDARY_FACILITATOR_TYPES.stream())
            .toList();

    return !node.getFacilitatorObtainments()
                .stream()
                .map(fo -> fo.getType().getType())
                .allMatch(mergedPrimarySecondaryFacilitatorList::contains);
  }

  public FacilitatorResponseProto calculateFacilitatorResponse(Ambiance ambiance, PlanNode planNode) {
    FacilitatorResponseProto facilitatorResponse = null;
    for (FacilitatorObtainment obtainment : planNode.getFacilitatorObtainments()) {
      CoreFacilitator facilitator = getFacilitatorFromType(obtainment.getType());
      facilitatorResponse = facilitator.facilitate(ambiance, obtainment.getParameters().toByteArray());
      if (facilitatorResponse != null) {
        break;
      }
    }
    if (facilitatorResponse == null) {
      throw new InvalidRequestException("Cannot Determine Execution mode as facilitator Response is null");
    }
    return facilitatorResponse;
  }

  public void checkAndRunSecondaryFacilitator(Ambiance ambiance, PlanNode planNode) {
    for (FacilitatorObtainment obtainment : planNode.getFacilitatorObtainments()) {
      if (OrchestrationFacilitatorType.ALL_SECONDARY_FACILITATOR_TYPES.contains(obtainment.getType().getType())) {
        CoreFacilitator facilitator = getFacilitatorFromType(obtainment.getType());
        if (!facilitator.isPrimaryFacilitator()) {
          // Fetching required fields from nodeExecution collection and then building facilitatorMetadata object for
          // passing to secondary facilitator
          NodeExecution nodeExecution =
              nodeExecutionService.getWithFieldsIncluded(AmbianceUtils.obtainCurrentRuntimeId(ambiance),
                  NodeProjectionUtils.fieldsForPreStepCheckPolicyEvaluation);
          FacilitatorMetadata facilitatorMetadata = FacilitatorMetadata.builder()
                                                        .name(nodeExecution.getName())
                                                        .mode(nodeExecution.getMode())
                                                        .resolvedParams(nodeExecution.getResolvedParams())
                                                        .build();

          // ignoring response of secondary facilitator. If response needs to be consumed, please use primary
          // facilitator. Secondary should never consume response
          facilitator.facilitateWithMetadata(ambiance, null, facilitatorMetadata);
        }
      }
    }
  }

  public CoreFacilitator getFacilitatorFromType(FacilitatorType type) {
    String fType = type.getType();
    switch (fType) {
      case OrchestrationFacilitatorType.ASYNC:
        return injector.getInstance(AsyncFacilitator.class);
      case OrchestrationFacilitatorType.SYNC:
        return injector.getInstance(SyncFacilitator.class);
      case OrchestrationFacilitatorType.TASK:
        return injector.getInstance(TaskFacilitator.class);
      case OrchestrationFacilitatorType.TASK_CHAIN:
        return injector.getInstance(TaskChainFacilitator.class);
      case OrchestrationFacilitatorType.CHILD:
        return injector.getInstance(ChildFacilitator.class);
      case OrchestrationFacilitatorType.CHILD_CHAIN:
        return injector.getInstance(ChildChainFacilitator.class);
      case OrchestrationFacilitatorType.CHILDREN:
        return injector.getInstance(ChildrenFacilitator.class);
      case OrchestrationFacilitatorType.WAIT_STEP:
        return injector.getInstance(WaitStepFacilitator.class);
      case OrchestrationFacilitatorType.ASYNC_CHAIN:
        return injector.getInstance(AsyncChainFacilitator.class);
      case OrchestrationFacilitatorType.PRE_STEP_CHECK:
        return injector.getInstance(PreStepCheckFacilitator.class);
      default:
        throw new InvalidRequestException("Core facilitator Type not found");
    }
  }

  public ExecutionMode getExecutionMode(List<FacilitatorObtainment> facilitatorObtainments) {
    if (isEmpty(facilitatorObtainments)) {
      log.error("Cannot Determine Execution mode as facilitator Obtainments is empty");
      return null;
    }
    return getExecutionModeFromFacilitatorType(facilitatorObtainments.get(0).getType());
  }

  public ExecutionMode getExecutionModeFromFacilitatorType(FacilitatorType facilitatorType) {
    String fType = facilitatorType.getType();
    switch (fType) {
      case OrchestrationFacilitatorType.ASYNC:
        return ExecutionMode.ASYNC;
      case OrchestrationFacilitatorType.SYNC:
        return ExecutionMode.SYNC;
      case OrchestrationFacilitatorType.TASK:
        return ExecutionMode.TASK;
      case OrchestrationFacilitatorType.TASK_CHAIN:
        return ExecutionMode.TASK_CHAIN;
      case OrchestrationFacilitatorType.CHILD:
        return ExecutionMode.CHILD;
      case OrchestrationFacilitatorType.CHILD_CHAIN:
        return ExecutionMode.CHILD_CHAIN;
      case OrchestrationFacilitatorType.CHILDREN:
        return ExecutionMode.CHILDREN;
      case OrchestrationFacilitatorType.WAIT_STEP:
        return ExecutionMode.WAIT_STEP;
      case OrchestrationFacilitatorType.ASYNC_CHAIN:
        return ExecutionMode.ASYNC_CHAIN;
      default:
        log.info(String.format(
            "Execution Mode not found for Facilitator Type %s. This must be a Custom Facilitator", fType));
        return null;
    }
  }
}

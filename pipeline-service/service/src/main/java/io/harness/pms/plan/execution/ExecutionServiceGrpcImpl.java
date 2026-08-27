/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0
 * license that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.plan.Plan;
import io.harness.plan.Plan.PlanBuilder;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.plan.AppendPlanNodesRequest;
import io.harness.pms.contracts.plan.AppendPlanNodesResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionServiceGrpc;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.plan.PlanSaveRequest;
import io.harness.pms.contracts.plan.PlanSaveResponse;
import io.harness.pms.contracts.plan.StartExecutionRequest;
import io.harness.pms.contracts.plan.StartExecutionResponse;
import io.harness.pms.data.NGWorkflowType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class ExecutionServiceGrpcImpl extends ExecutionServiceGrpc.ExecutionServiceImplBase {
  @Inject private OrchestrationService orchestrationService;
  @Inject private PlanService planService;

  @Override
  public void appendPlanNodes(
      AppendPlanNodesRequest request, StreamObserver<AppendPlanNodesResponse> responseObserver) {
    try {
      log.info("Received request to append nodes to plan: {}", request.getPlanId());

      // Extract data from the request
      String planId = request.getPlanId();
      List<PlanNodeProto> planNodeProtos = request.getPlanNodesList();
      String accountId = request.getAccountId();

      // Convert PlanNodeProto to PlanNode objects
      List<PlanNode> planNodes = planNodeProtos.stream()
                                     .map(proto -> PlanNode.fromPlanNodeProto(proto, accountId))
                                     .collect(Collectors.toList());

      // Append nodes to the plan
      Plan updatedPlan = planService.appendNodes(planId, new ArrayList<>(planNodes));

      // Build and send the response
      AppendPlanNodesResponse response = AppendPlanNodesResponse.newBuilder().setPlanId(updatedPlan.getUuid()).build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();

      log.info("Successfully appended {} nodes to plan: {}", planNodes.size(), planId);
    } catch (Exception e) {
      log.error("Error appending nodes to plan", e);
      responseObserver.onError(e);
    }
  }

  @Override
  public void savePlan(PlanSaveRequest request, StreamObserver<PlanSaveResponse> responseObserver) {
    Plan plan = buildPlan(request.getPlanNodesList(), request.getAccountId(), request.getStartingNodeId());
    Plan savedPlan = planService.save(plan);

    // Build the response with the saved plan ID
    PlanSaveResponse response = PlanSaveResponse.newBuilder().setPlanId(savedPlan.getUuid()).build();

    // Complete the RPC call
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void startExecution(StartExecutionRequest request, StreamObserver<StartExecutionResponse> responseObserver) {
    try {
      // Fetch the plan using the planId from the request
      String planId = request.getPlanId();
      Plan plan = planService.fetchPlan(planId);

      if (plan == null) {
        throw new IllegalArgumentException("Plan not found with ID: " + planId);
      }

      // Extract metadata from the request
      ExecutionMetadata executionMetadata = request.getMetadata();
      Map<String, String> setupAbstractions = new HashMap<>(request.getSetupAbstractionsMap());
      // If we are using this to trigger execution then it should have no dependency on Pipeline
      setupAbstractions.put(SetupAbstractionKeys.workflowType, NGWorkflowType.ORCHESTRATION.name());

      // Create PlanExecutionMetadata from ExecutionMetadata
      PlanExecutionMetadata planExecutionMetadata =
          PlanExecutionMetadata.builder()
              .planExecutionId(executionMetadata.getExecutionUuid())
              // Use SetupAbstractionUtils to fetch identifiers from setupAbstractions
              .accountIdentifier(SetupAbstractionUtils.getAccountId(setupAbstractions))
              // Generate a token for expression evaluation
              .expressionFunctorToken((long) (Math.random() * 1000000))
              .build();

      // Create PlanExecutionMetadataWithContext with the necessary fields
      PlanExecutionMetadataWithContext metadataWithContext = PlanExecutionMetadataWithContext.builder()
                                                                 .planExecutionMetadata(planExecutionMetadata)
                                                                 .isRetry(false) // Default value
                                                                 .runAllStages(true) // Default value
                                                                 .build();

      // Call the orchestrationService to execute the plan
      PlanExecution planExecution =
          orchestrationService.executePlan(plan, setupAbstractions, executionMetadata, metadataWithContext);

      // Build the response with the execution ID
      StartExecutionResponse response =
          StartExecutionResponse.newBuilder().setExecutionId(planExecution.getUuid()).build();

      // Complete the RPC call
      responseObserver.onNext(response);
      responseObserver.onCompleted();

      log.info("Started execution with ID: {} for plan: {}", planExecution.getUuid(), planId);
    } catch (Exception e) {
      log.error("Error starting execution for plan", e);
      responseObserver.onError(e);
    }
  }

  private Plan buildPlan(List<PlanNodeProto> planNodeProtoList, String accountId, String startingNodeId) {
    PlanBuilder planBuilder = Plan.builder();
    for (PlanNodeProto planNodeProto : planNodeProtoList) {
      planBuilder.planNode(PlanNode.fromPlanNodeProto(planNodeProto, accountId));
    }
    if (isNotEmpty(startingNodeId)) {
      planBuilder.startingNodeId(startingNodeId);
    }
    return planBuilder.build();
  }
}

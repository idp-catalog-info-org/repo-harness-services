/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.GovernanceServiceHelper;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.metrics.service.api.MetricService;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.EvaluationDetailsResponse;
import io.harness.opaclient.model.OPAEvaluationStatus;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.opaclient.model.PolicySetData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.security.PmsSecurityContextNoSideEffectsGuard;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.opa.OPAEvaluationAggregatorStepParameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
public class OPAEvaluationAggregatorStep implements SyncExecutable<StepBaseParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR)
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private OpaServiceClient opaServiceClient;
  @Inject private OPAEvaluationStepHelper opaEvaluationStepHelper;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject private MetricService metricService;

  private static final String LOG_UNIT = "Execute";
  private static final String OPA_INFRA_POLICIES_EVALUATED_TOTAL = "opa_infra_policies_evaluated_total";

  private static final String POLICY_SET_STATUS_PENDING = "pending";

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>(Collections.singleton("Execute")));
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    OPAEvaluationAggregatorStepParameters spec = (OPAEvaluationAggregatorStepParameters) stepParameters.getSpec();

    // Initialize log streaming - exactly like PolicyStep
    NGLogCallback logCallback = getLogCallback(ambiance, true);

    logCallback.saveExecutionLog("Starting OPA Evaluation Aggregator Step", LogLevel.INFO);
    logCallback.saveExecutionLog(String.format("Step Identifier: %s", stepParameters.getIdentifier()), LogLevel.INFO);

    String evaluationId = spec.getEvaluationId() != null ? spec.getEvaluationId().getValue() : null;
    if (isEmpty(evaluationId)) {
      String planExecutionId = ambiance.getPlanExecutionId();
      try {
        evaluationId = opaEvaluationStepHelper.fetchEvaluationIdFromPlanExecutionId(ambiance, planExecutionId);
        if (isEmpty(evaluationId)) {
          String errorMsg = String.format("Failed to fetch Evaluation ID from planExecutionId: %s", planExecutionId);
          log.error(errorMsg);
          logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
          FailureData failureData = FailureData.newBuilder()
                                        .setCode(ErrorCode.INVALID_REQUEST.name())
                                        .setLevel(Level.ERROR.name())
                                        .setMessage(errorMsg)
                                        .addFailureTypes(FailureType.UNKNOWN_FAILURE)
                                        .build();
          FailureInfo failureInfo =
              FailureInfo.newBuilder().addFailureData(failureData).setErrorMessage(errorMsg).build();
          return StepResponse.builder().status(Status.FAILED).failureInfo(failureInfo).build();
        }
        logCallback.saveExecutionLog(
            String.format("Fetched evaluationId: %s from planExecutionId", evaluationId), LogLevel.INFO);
      } catch (Exception ex) {
        String errorMsg = String.format(
            "Failed to fetch Evaluation ID from planExecutionId %s: %s", planExecutionId, ex.getMessage());
        log.error(errorMsg, ex);
        logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
        FailureData failureData = FailureData.newBuilder()
                                      .setCode(ErrorCode.INTERNAL_SERVER_ERROR.name())
                                      .setLevel(Level.ERROR.name())
                                      .setMessage(errorMsg)
                                      .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                      .build();
        FailureInfo failureInfo =
            FailureInfo.newBuilder().addFailureData(failureData).setErrorMessage(errorMsg).build();
        return StepResponse.builder().status(Status.FAILED).failureInfo(failureInfo).build();
      }
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String planExecutionId = ambiance.getPlanExecutionId();

    logCallback.saveExecutionLog(
        String.format("Fetching evaluation results for evaluationId: %s", evaluationId), LogLevel.INFO);

    EvaluationDetailsResponse response;
    try (PmsSecurityContextNoSideEffectsGuard ignore = new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
      JsonNode context = JsonNodeFactory.instance.objectNode();
      response =
          SafeHttpCall.executeWithErrorMessage(opaServiceClient.getEvaluationRecords(evaluationId, accountId, context));

      if (response == null || isEmpty(response.getEvaluations())) {
        String errorMsg = "No evaluation records found in response for evaluationId: " + evaluationId;
        log.error("OPAEvaluationAggregatorStep: " + errorMsg);
        logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
        FailureData failureData = FailureData.newBuilder()
                                      .setCode(ErrorCode.INVALID_REQUEST.name())
                                      .setLevel(Level.ERROR.name())
                                      .setMessage(errorMsg)
                                      .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                      .build();
        FailureInfo failureInfo =
            FailureInfo.newBuilder().addFailureData(failureData).setErrorMessage(errorMsg).build();
        return StepResponse.builder().status(Status.FAILED).failureInfo(failureInfo).build();
      }

      logCallback.saveExecutionLog(String.format("Successfully fetched evaluation response with %d evaluations",
                                       response.getEvaluations().size()),
          LogLevel.INFO);

    } catch (Exception ex) {
      String errorMsg = String.format("Failed to fetch evaluation results: %s", ex.getMessage());
      log.error("OPAEvaluationAggregatorStep: " + errorMsg, ex);
      logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      FailureData failureData = FailureData.newBuilder()
                                    .setCode(ErrorCode.HTTP_RESPONSE_EXCEPTION.name())
                                    .setLevel(Level.ERROR.name())
                                    .setMessage(errorMsg)
                                    .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                    .build();
      FailureInfo failureInfo = FailureInfo.newBuilder().addFailureData(failureData).setErrorMessage(errorMsg).build();
      return StepResponse.builder().status(Status.FAILED).failureInfo(failureInfo).build();
    }

    if (isEmpty(response.getEvaluations())) {
      String errorMsg = "No evaluation records found for evaluation ID: " + evaluationId;
      log.error("OPAEvaluationAggregatorStep: " + errorMsg);
      logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      FailureData failureData = FailureData.newBuilder()
                                    .setCode(ErrorCode.INVALID_REQUEST.name())
                                    .setLevel(Level.ERROR.name())
                                    .setMessage(errorMsg)
                                    .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                    .build();
      FailureInfo failureInfo = FailureInfo.newBuilder().addFailureData(failureData).setErrorMessage(errorMsg).build();
      return StepResponse.builder().status(Status.FAILED).failureInfo(failureInfo).build();
    }

    // Get the first evaluation (aggregate endpoint returns single evaluation)
    OpaEvaluationResponseHolder evaluationResponse = response.getEvaluations().get(0);
    if (evaluationResponse == null || isEmpty(evaluationResponse.getDetails())) {
      String errorMsg = "Evaluation response has no policy set details for evaluationId: " + evaluationId;
      log.error("OPAEvaluationAggregatorStep: " + errorMsg);
      logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      FailureData failureData = FailureData.newBuilder()
                                    .setCode(ErrorCode.INVALID_REQUEST.name())
                                    .setLevel(Level.ERROR.name())
                                    .setMessage(errorMsg)
                                    .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                    .build();
      FailureInfo failureInfo = FailureInfo.newBuilder().addFailureData(failureData).setErrorMessage(errorMsg).build();
      return StepResponse.builder().status(Status.FAILED).failureInfo(failureInfo).build();
    }

    logCallback.saveExecutionLog(
        String.format("Processing %d policy sets from evaluation", evaluationResponse.getDetails().size()),
        LogLevel.INFO);

    // Convert evaluation response to governance metadata
    GovernanceMetadata infraGovernanceMetadata = GovernanceServiceHelper.mapResponseToMetadata(evaluationResponse);

    // Determine overall status from infra policy sets
    // Policy-mgmt returns only: "error", "warning", "pass", and "pending" (for infrastructure policy sets)
    boolean hasFailedPolicySets = evaluationResponse.getDetails().stream().anyMatch(detail
        -> detail != null && detail.getStatus() != null
            && ("error".equalsIgnoreCase(detail.getStatus()) || "pending".equalsIgnoreCase(detail.getStatus())));

    if (hasFailedPolicySets) {
      String failedPolicySets =
          evaluationResponse.getDetails()
              .stream()
              .filter(detail
                  -> detail != null && detail.getStatus() != null
                      && ("error".equalsIgnoreCase(detail.getStatus())
                          || "pending".equalsIgnoreCase(detail.getStatus())))
              .map(detail
                  -> String.format("%s: %s", detail.getName() != null ? detail.getName() : detail.getIdentifier(),
                      detail.getStatus()))
              .collect(Collectors.joining("; "));

      String errorMsg = String.format("Policy evaluation failed: %s", failedPolicySets);
      log.error("OPAEvaluationAggregatorStep: " + errorMsg);
      logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);

      // Still update governance metadata even if failed, so UI can show the results
      // Metrics will be recorded inside updateGovernanceMetadata to avoid duplicate DB fetch
      updateGovernanceMetadata(ambiance, planExecutionId, infraGovernanceMetadata, logCallback, evaluationResponse);

      FailureData failureData = FailureData.newBuilder()
                                    .setCode(ErrorCode.POLICY_EVALUATION_FAILURE.name())
                                    .setLevel(Level.ERROR.name())
                                    .setMessage(errorMsg)
                                    .addFailureTypes(FailureType.POLICY_EVALUATION_FAILURE)
                                    .build();
      FailureInfo failureInfo = FailureInfo.newBuilder().addFailureData(failureData).setErrorMessage(errorMsg).build();
      return StepResponse.builder().status(Status.FAILED).failureInfo(failureInfo).build();
    }

    // Update governance metadata with infra policy set results
    // Metrics will be recorded inside updateGovernanceMetadata to avoid duplicate DB fetch
    updateGovernanceMetadata(ambiance, planExecutionId, infraGovernanceMetadata, logCallback, evaluationResponse);

    logCallback.saveExecutionLog("All policy set evaluations passed successfully", LogLevel.INFO);
    // No need to close the client explicitly, since if command execution status is terminal, save execution log
    // automatically closes the connection.
    logCallback.saveExecutionLog(
        "OPA Evaluation Aggregator Step completed successfully", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  private NGLogCallback getLogCallback(Ambiance ambiance, boolean shouldOpenStream) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, LOG_UNIT, shouldOpenStream);
  }

  /**
   * Updates PipelineExecutionSummaryEntity governance metadata by merging infra policy set results.
   * Filters infra policy sets to only include those with "pending" status in current governance metadata,
   * then merges them into existing governance metadata, replacing entries with same identifier.
   * Also records metrics for infrastructure policy sets to avoid duplicate DB fetch.
   *
   */
  private void updateGovernanceMetadata(Ambiance ambiance, String planExecutionId,
      GovernanceMetadata infraGovernanceMetadata, NGLogCallback logCallback,
      OpaEvaluationResponseHolder evaluationResponse) {
    try {
      log.info(
          "OPAEvaluationAggregatorStep: Starting governance metadata update for planExecutionId: {}", planExecutionId);

      // Get current execution summary to check existing governance metadata
      String accountId = AmbianceUtils.getAccountId(ambiance);
      PipelineExecutionSummaryEntity currentSummary =
          pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
              accountId, planExecutionId, Set.of(PlanExecutionSummaryKeys.governanceMetadata));

      if (currentSummary == null) {
        log.warn("OPAEvaluationAggregatorStep: Execution summary not found for planExecutionId: {}", planExecutionId);
        logCallback.saveExecutionLog(
            String.format("Warning: Execution summary not found, skipping governance metadata update"), LogLevel.WARN);
        return;
      }

      GovernanceMetadata currentGovernanceMetadata = currentSummary.getGovernanceMetadata();
      log.info("OPAEvaluationAggregatorStep: Found execution summary, governance metadata exists: {}",
          currentGovernanceMetadata != null);

      // Filter infra policy sets to only include those with "pending" status in current governance metadata
      List<PolicySetMetadata> filteredInfraPolicySets = new ArrayList<>();
      if (currentGovernanceMetadata != null && isNotEmpty(currentGovernanceMetadata.getDetailsList())) {
        // Build a set of infrastructure policy set identifiers (those with "pending" status)
        Set<String> infraPolicySetIdentifiers = new HashSet<>();
        for (PolicySetMetadata existingPolicySet : currentGovernanceMetadata.getDetailsList()) {
          if (existingPolicySet != null && isNotEmpty(existingPolicySet.getIdentifier())
              && "pending".equalsIgnoreCase(existingPolicySet.getStatus())) {
            infraPolicySetIdentifiers.add(existingPolicySet.getIdentifier());
          }
        }

        // Filter infra policy sets to only include those with "pending" status
        for (PolicySetMetadata infraPolicySet : infraGovernanceMetadata.getDetailsList()) {
          if (infraPolicySet != null && isNotEmpty(infraPolicySet.getIdentifier())
              && infraPolicySetIdentifiers.contains(infraPolicySet.getIdentifier())) {
            filteredInfraPolicySets.add(infraPolicySet);
          }
        }

        logCallback.saveExecutionLog(
            String.format("Filtered to %d infrastructure policy sets out of %d total policy sets "
                    + "(infrastructure policy sets with pending status: %d)",
                filteredInfraPolicySets.size(), infraGovernanceMetadata.getDetailsList().size(),
                infraPolicySetIdentifiers.size()),
            LogLevel.INFO);

        // Record metrics for infrastructure policy sets (after filtering, before updating metadata)
        // This avoids duplicate DB fetch since we already have governance metadata here
        if (evaluationResponse != null && !isEmpty(evaluationResponse.getDetails())) {
          recordMetricsForFilteredPolicySets(ambiance, evaluationResponse, infraPolicySetIdentifiers);
        }
      } else {
        logCallback.saveExecutionLog(
            "No existing governance metadata found, skipping infrastructure policy set filtering", LogLevel.INFO);
      }

      GovernanceMetadata.Builder mergedMetadataBuilder;

      if (currentGovernanceMetadata == null) {
        // If no existing metadata, use filtered infra policy sets (or empty if none)
        mergedMetadataBuilder = infraGovernanceMetadata.toBuilder().clearDetails();
        mergedMetadataBuilder.addAllDetails(filteredInfraPolicySets);
        logCallback.saveExecutionLog(String.format("No existing governance metadata, using %d infra policy set results",
                                         filteredInfraPolicySets.size()),
            LogLevel.INFO);
      } else {
        // Merge: Start with existing metadata, then add/update filtered infra policy sets
        mergedMetadataBuilder = currentGovernanceMetadata.toBuilder().clearDetails();

        // Build a map of filtered infra policy set identifiers for quick lookup
        Map<String, PolicySetMetadata> filteredInfraPolicySetMap = new HashMap<>();
        for (PolicySetMetadata infraPolicySet : filteredInfraPolicySets) {
          filteredInfraPolicySetMap.put(infraPolicySet.getIdentifier(), infraPolicySet);
        }

        // Add existing policy sets (SAAS ones), but skip if filtered infra policy set with same identifier exists
        for (PolicySetMetadata existingPolicySet : currentGovernanceMetadata.getDetailsList()) {
          if (!filteredInfraPolicySetMap.containsKey(existingPolicySet.getIdentifier())) {
            mergedMetadataBuilder.addDetails(existingPolicySet);
          }
        }

        // Add filtered infra policy sets (these will replace any existing ones with same identifier)
        mergedMetadataBuilder.addAllDetails(filteredInfraPolicySets);

        logCallback.saveExecutionLog(
            String.format("Merged %d infrastructure policy sets into existing governance metadata",
                filteredInfraPolicySets.size()),
            LogLevel.INFO);
      }

      // Compute overall status from ALL merged policy sets (SAAS + infrastructure)
      String overallStatus = computeOverallStatusFromAllPolicySets(mergedMetadataBuilder.getDetailsList());
      mergedMetadataBuilder.setStatus(overallStatus);
      // Set deny flag only if status is error
      if ("error".equalsIgnoreCase(overallStatus)) {
        mergedMetadataBuilder.setDeny(true);
      } else {
        mergedMetadataBuilder.setDeny(false);
      }

      GovernanceMetadata mergedMetadata = mergedMetadataBuilder.build();

      log.info(
          "OPAEvaluationAggregatorStep: Merged metadata has {} policy sets. Updating PipelineExecutionSummaryEntity...",
          mergedMetadata.getDetailsList().size());

      // Update PipelineExecutionSummaryEntity with merged governance metadata
      org.springframework.data.mongodb.core.query.Update summaryUpdate =
          new org.springframework.data.mongodb.core.query.Update();
      summaryUpdate.set(PlanExecutionSummaryKeys.governanceMetadata, mergedMetadata);
      PipelineExecutionSummaryEntity updatedSummary = pmsExecutionSummaryService.update(planExecutionId, summaryUpdate);

      if (updatedSummary != null) {
        log.info("OPAEvaluationAggregatorStep: Successfully updated PipelineExecutionSummaryEntity governance "
                + "metadata. Updated has {} policy sets",
            updatedSummary.getGovernanceMetadata() != null
                ? updatedSummary.getGovernanceMetadata().getDetailsList().size()
                : 0);
        logCallback.saveExecutionLog("Updated governance metadata in execution summary", LogLevel.INFO);
      } else {
        String errorMsg = String.format("Failed to update governance metadata: PipelineExecutionSummaryEntity update "
                + "returned null for planExecutionId: %s",
            planExecutionId);
        log.error("OPAEvaluationAggregatorStep: " + errorMsg);
        logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR);
        return;
      }

      logCallback.saveExecutionLog(
          String.format("Successfully updated governance metadata with %d total policy sets (existing: %d, infra: %d)",
              mergedMetadata.getDetailsList().size(),
              currentGovernanceMetadata != null ? currentGovernanceMetadata.getDetailsList().size() : 0,
              filteredInfraPolicySets.size()),
          LogLevel.INFO);

    } catch (Exception ex) {
      String errorMsg = String.format("Failed to update governance metadata: %s", ex.getMessage());
      log.error("OPAEvaluationAggregatorStep: " + errorMsg, ex);
      logCallback.saveExecutionLog(errorMsg, LogLevel.ERROR);
      // Don't fail the step if metadata update fails, just log the error
    }
  }

  /**
   * Records metrics for filtered infrastructure policy sets.
   * This is called from updateGovernanceMetadata() after filtering to avoid duplicate DB fetch.
   * Records metrics for each infrastructure policy set evaluation with their status.
   *
   * @param ambiance Pipeline execution ambiance (for extracting infra_type)
   * @param evaluationResponse The evaluation response holder (contains account/org/project at evaluation level)
   * @param infraPolicySetIdentifiers Set of infrastructure policy set identifiers (already filtered)
   */
  private void recordMetricsForFilteredPolicySets(
      Ambiance ambiance, OpaEvaluationResponseHolder evaluationResponse, Set<String> infraPolicySetIdentifiers) {
    if (evaluationResponse == null || isEmpty(evaluationResponse.getDetails()) || isEmpty(infraPolicySetIdentifiers)) {
      return;
    }

    try {
      String evaluationAccountId = evaluationResponse.getAccount_id();

      // Record metrics only for infrastructure policy sets
      // Each policy set can have its own infrastructure type, so we fetch it individually
      int recordedCount = 0;
      for (OpaPolicySetEvaluationResponse detail : evaluationResponse.getDetails()) {
        if (detail == null || isEmpty(detail.getIdentifier())) {
          continue;
        }

        // Only record metrics for infrastructure policy sets
        if (!infraPolicySetIdentifiers.contains(detail.getIdentifier())) {
          continue;
        }

        String policySetId = detail.getIdentifier();
        String policySetAccountId = isNotEmpty(detail.getAccount_id())
            ? detail.getAccount_id()
            : (isNotEmpty(evaluationAccountId) ? evaluationAccountId : "");
        String status = detail.getStatus() != null ? detail.getStatus() : "";

        // Fetch infra type for this specific policy set (each policy set can have different infra type)
        String infraType = extractInfraTypeForPolicySet(ambiance, detail);

        // Skip this policy set if infra type cannot be resolved
        if (isEmpty(infraType)) {
          log.debug("OPAEvaluationAggregatorStep: Skipping metric recording for policy set {} - infra type could not "
                  + "be resolved",
              policySetId);
          continue;
        }

        // Build metric context map with only account_id, infra_type, and status to avoid cardinality explosion
        ImmutableMap<String, String> metricContextMap = ImmutableMap.<String, String>builder()
                                                            .put("account_id", policySetAccountId)
                                                            .put("infra_type", infraType)
                                                            .put("status", status)
                                                            .build();

        try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
          metricService.incCounter(OPA_INFRA_POLICIES_EVALUATED_TOTAL);
          recordedCount++;
          log.info("OPAEvaluationAggregatorStep: Recorded metric - policySetId: {}, status: {}, "
                  + "accountId: {}, infraType: {}",
              policySetId, status, policySetAccountId, infraType);
        } catch (Exception ex) {
          log.error("OPAEvaluationAggregatorStep: Failed to record metric for policy set {}: {}", policySetId,
              ex.getMessage(), ex);
        }
      }

      log.info("OPAEvaluationAggregatorStep: Recorded metrics for {} infrastructure policy sets out of {} total",
          recordedCount, evaluationResponse.getDetails().size());
    } catch (Exception ex) {
      log.warn("OPAEvaluationAggregatorStep: Failed to record policy set evaluation metrics: {}", ex.getMessage(), ex);
      // Don't fail metadata update if metrics recording fails
    }
  }

  /**
   * Computes overall governance metadata status from all policy sets (SAAS + infrastructure).
   * Status priority order: ERROR > WARNING > PENDING > PASS
   * Only four statuses are possible from policy-mgmt: pass, error, warning, pending
   *
   * @param allPolicySets List of all policy sets (SAAS + infrastructure)
   * @return Overall status string ("error", "warning", "pending", or "pass")
   */
  private String computeOverallStatusFromAllPolicySets(List<PolicySetMetadata> allPolicySets) {
    if (isEmpty(allPolicySets)) {
      return OPAEvaluationStatus.PASS.getValue();
    }

    OPAEvaluationStatus highestPriorityStatus = OPAEvaluationStatus.PASS;

    for (PolicySetMetadata policySet : allPolicySets) {
      if (policySet == null || isEmpty(policySet.getStatus())) {
        continue;
      }

      OPAEvaluationStatus status = OPAEvaluationStatus.fromString(policySet.getStatus());
      if (status != null) {
        highestPriorityStatus = OPAEvaluationStatus.getHigherPriority(highestPriorityStatus, status);
      }
    }

    return highestPriorityStatus.getValue();
  }

  /**
   * Extracts infrastructure type from a specific policy set's data.
   * Since the aggregator step runs at the stage level (outside step groups), it cannot access
   * STAGE_INFRA_DETAILS sweeping output which is stored at the step group level.
   * Instead, we fetch the infrastructure type from the policy set's data.
   * Each policy set can have its own infrastructure type, so this method is called for each policy set.
   *
   * @param ambiance Pipeline execution ambiance
   * @param policySetEvaluationResponse Policy set evaluation response containing policy set identifier and scope
   * @return Infrastructure type string ("KubernetesDirect" or "VM"), or empty string if unable to determine
   */
  private String extractInfraTypeForPolicySet(
      Ambiance ambiance, OpaPolicySetEvaluationResponse policySetEvaluationResponse) {
    if (ambiance == null || policySetEvaluationResponse == null
        || isEmpty(policySetEvaluationResponse.getIdentifier())) {
      log.warn("OPAEvaluationAggregatorStep: Cannot extract infra type - ambiance or policySetEvaluationResponse is "
          + "null/empty");
      return "";
    }

    try {
      String policySetId = policySetEvaluationResponse.getIdentifier();
      String policySetOrgId = policySetEvaluationResponse.getOrg_id();
      String policySetProjectId = policySetEvaluationResponse.getProject_id();

      // Fetch policy set data to get infra_type
      PolicySetData policySetData =
          opaEvaluationStepHelper.fetchPolicySet(ambiance, policySetId, policySetOrgId, policySetProjectId);

      if (policySetData == null || isEmpty(policySetData.getInfra_type())) {
        log.warn("OPAEvaluationAggregatorStep: Policy set {} has no infra_type", policySetId);
        return "";
      }

      // Use infra_type directly (no mapping needed - "KubernetesDirect" or "VM" are fine for metrics)
      String infraType = policySetData.getInfra_type();
      if (isEmpty(infraType)) {
        log.warn("OPAEvaluationAggregatorStep: Policy set {} has empty infra_type", policySetId);
        return "";
      }
      return infraType;
    } catch (Exception ex) {
      log.warn("OPAEvaluationAggregatorStep: Failed to extract infra type for policy set {}: {}",
          policySetEvaluationResponse.getIdentifier(), ex.getMessage(), ex);
      return "";
    }
  }
}

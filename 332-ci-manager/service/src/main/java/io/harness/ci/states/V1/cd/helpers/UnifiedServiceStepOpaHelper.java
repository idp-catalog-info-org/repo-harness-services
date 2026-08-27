/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.cd.beans.outcomes.ArtifactsOutcome;
import io.harness.cd.beans.outcomes.ConfigFilesOutcome;
import io.harness.cd.beans.outcomes.ManifestsOutcome;
import io.harness.cd.beans.outcomes.UnifiedServiceOutcome;
import io.harness.cd.opa.UnifiedServiceOpaEvaluationContext;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.UnexpectedException;
import io.harness.opaclient.OpaServiceClientHelper;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.security.PmsSecurityContextNoSideEffectsGuard;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.ExpressionResolverHelper;
import io.harness.utils.PolicyEvalUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Helper class for UnifiedServiceStep OPA integration (V1)
 * This class provides methods to evaluate OPA policies for unified service steps at runtime
 * using v1-specific outcomes and context
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class UnifiedServiceStepOpaHelper {
  @Inject private OpaServiceClientHelper opaServiceClientHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;

  /**
   * Check and call OPA for unified service runtime context
   * Uses v1-specific UnifiedServiceOpaEvaluationContext
   */
  public void checkAndCallOpaForServiceRuntimeContext(
      Ambiance ambiance, List<StepResponse.StepOutcome> stepOutcomes, StepResponse stepResponse) {
    if (!Status.SUCCEEDED.equals(stepResponse.getStatus())) {
      return;
    }

    if (!ciExecutionServiceConfig.isEnableOpaEvaluation()) {
      return;
    }

    // The OPA client is NON_PRIVILEGED, so its outbound JWT takes the principal from this thread's
    // context, and the orchestration thread that handles the step response carries none.
    try (PmsSecurityContextNoSideEffectsGuard ignore = new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
      evaluateServicePolicies(ambiance, stepOutcomes);
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new UnexpectedException("Failed to restore security context after policy evaluation", ex);
    }
  }

  private void evaluateServicePolicies(Ambiance ambiance, List<StepResponse.StepOutcome> stepOutcomes) {
    String accountId = AmbianceUtils.getAccountId(ambiance);

    if (!opaServiceClientHelper.shouldEvaluatePolicyWithRetry(accountId, AmbianceUtils.getOrgIdentifier(ambiance),
            AmbianceUtils.getProjectIdentifier(ambiance), OpaConstants.OPA_EVALUATION_TYPE_SERVICE,
            OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN, HarnessYamlVersion.V1)) {
      return;
    }

    UnifiedServiceOpaEvaluationContext.Builder builder = UnifiedServiceOpaEvaluationContext.builder();

    getOpaPayloadFromStepOutcomes(stepOutcomes, builder);
    builder.serviceVariables(resolveServiceVariables(ambiance));

    UnifiedServiceOpaEvaluationContext context = builder.build();

    // PIPE-31258 fix: If service outcome not found in step outcomes (e.g., when V0 ngOutcomes is used),
    // fall back to ngOutcomes first, then to sweeping output where it was originally saved
    if (context.getService() == null) {
      UnifiedServiceOutcome serviceOutcomeFromNgOutcomes =
          serviceStepSweepingOutputHelper.resolveServiceOutcomeFromNgOutcomes(ambiance);
      if (serviceOutcomeFromNgOutcomes != null) {
        builder.service(serviceOutcomeFromNgOutcomes);
        context = builder.build();
      } else {
        log.debug("Service not found in ngOutcomes, attempting to fetch from sweeping output");
        OptionalSweepingOutput serviceMetadataOutput =
            serviceStepSweepingOutputHelper.fetchServiceMetadataOutput(ambiance);
        if (serviceMetadataOutput.isFound()) {
          UnifiedServiceOutcome serviceOutcome = (UnifiedServiceOutcome) serviceMetadataOutput.getOutput();
          builder.service(serviceOutcome);
          context = builder.build();
        }
      }
    }

    String serviceIdentifier = context.getService() != null ? context.getService().getIdentifier() : "";

    if (StringUtils.isEmpty(serviceIdentifier)) {
      throw new UnexpectedException("Service identifier is empty");
    }

    try {
      String jsonString = RecastOrchestrationUtils.toSimpleJson(context);
      JsonNode jsonNode = JsonPipelineUtils.readTree(jsonString);

      OpaEvaluationResponseHolder response =
          opaServiceClientHelper.evaluateWithCredentialsWithRetry(OpaConstants.OPA_EVALUATION_TYPE_SERVICE, accountId,
              AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
              OpaConstants.OPA_EVALUATION_ACTION_SERVICE_RUN, serviceIdentifier,
              PolicyEvalUtils.getEntityMetadataString(
                  serviceIdentifier, AmbianceUtils.getPipelineExecutionIdentifier(ambiance)),
              ambiance.getMetadata().getPrincipalInfo().getPrincipal(),
              String.valueOf(ambiance.getMetadata().getPrincipalInfo().getPrincipalType()), HarnessYamlVersion.V1,
              jsonNode);

      if (response != null && OpaConstants.OPA_STATUS_ERROR.equals(response.getStatus())) {
        String errorMessage = PolicyEvalUtils.buildDetailedPolicyEvaluationFailureMessage(response);
        throw new PolicyEvaluationFailureException(errorMessage);
      }
    } catch (PolicyEvaluationFailureException e) {
      log.error("Policy evaluation failed for unified service OPA runtime context evaluation", e);
      throw e;
    } catch (Exception e) {
      log.error("Failed to process JSON for unified service OPA evaluation", e);
      throw new UnexpectedException(
          String.format("Failed to process JSON for unified service OPA evaluation: %s", e.getMessage()), e);
    }
  }

  /**
   * Build v1 OPA payload from step outcomes
   * Extracts v1-specific service outcomes: UnifiedServiceOutcome, ManifestsOutcome, ArtifactsOutcome, etc.
   */
  @VisibleForTesting
  void getOpaPayloadFromStepOutcomes(
      List<StepResponse.StepOutcome> stepOutcomes, UnifiedServiceOpaEvaluationContext.Builder builder) {
    for (StepResponse.StepOutcome stepOutcome : stepOutcomes) {
      String stepOutcomeName = stepOutcome.getName();

      Object outcome = stepOutcome.getOutcome();

      if (OutcomeExpressionConstants.SERVICE.equals(stepOutcomeName) && outcome instanceof UnifiedServiceOutcome) {
        builder.service((UnifiedServiceOutcome) outcome);
      } else if (OutcomeExpressionConstants.MANIFESTS.equals(stepOutcomeName) && outcome instanceof ManifestsOutcome) {
        builder.manifests((ManifestsOutcome) outcome);
      } else if (OutcomeExpressionConstants.ARTIFACTS.equals(stepOutcomeName) && outcome instanceof ArtifactsOutcome) {
        builder.artifacts((ArtifactsOutcome) outcome);
      } else if (OutcomeExpressionConstants.CONFIG_FILES.equals(stepOutcomeName)
          && outcome instanceof ConfigFilesOutcome) {
        builder.configFiles((ConfigFilesOutcome) outcome);
      }
    }
  }

  /**
   * Resolve service variables from sweeping output
   * Returns a map of variable names to their resolved string values
   */
  @VisibleForTesting
  public Map<String, String> resolveServiceVariables(Ambiance ambiance) {
    Map<String, Object> variables = new LinkedHashMap<>();

    OptionalSweepingOutput optionalSweepingOutput = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(YAMLFieldNameConstants.SERVICE_VARIABLES));

    if (optionalSweepingOutput.isFound()) {
      VariablesSweepingOutput variablesSweepingOutput = (VariablesSweepingOutput) optionalSweepingOutput.getOutput();

      if (EmptyPredicate.isNotEmpty(variablesSweepingOutput)) {
        variables.putAll(variablesSweepingOutput);
      }
    }

    return ExpressionResolverHelper.convertExpressionParameterFieldMapToString(variables);
  }
}

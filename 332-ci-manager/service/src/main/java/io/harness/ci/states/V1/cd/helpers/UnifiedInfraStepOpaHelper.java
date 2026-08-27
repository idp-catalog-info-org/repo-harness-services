/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ci.states.V1.cd.helpers;
import static io.harness.cd.beans.outcomes.InfraStepOutcome.InfraStepOutcomeKeys;
import static io.harness.unified.service.NGOutcomes.INFRA_V0_OUTCOME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.cd.beans.outcomes.InfraStepOutcome;
import io.harness.cd.opa.UnifiedInfraOpaEvaluationContext;
import io.harness.ci.config.CIExecutionServiceConfig;
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
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.PolicyEvalUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
/**
 * Helper class for UnifiedCDInfraStep OPA integration
 * This class provides methods to evaluate OPA policies for unified infrastructure steps at runtime
 * using v1-specific outcomes and context
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class UnifiedInfraStepOpaHelper {
  @Inject private OpaServiceClientHelper opaServiceClientHelper;
  @Inject private UnifiedServiceStepOpaHelper unifiedServiceStepOpaHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  /**
   * Check and call OPA for unified infrastructure runtime context
   * Uses v1-specific UnifiedInfraOpaEvaluationContext
   */
  public void checkAndCallOpaForInfrastructureRuntimeContext(
      Ambiance ambiance, InfraStepOutcome infraStepOutcome, StepResponse stepResponse) {
    if (infraStepOutcome == null) {
      OptionalSweepingOutput infraStepOutput =
          sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(INFRA_V0_OUTCOME));
      if (infraStepOutput.isFound() && infraStepOutput.getOutput() != null) {
        infraStepOutcome = (InfraStepOutcome) infraStepOutput.getOutput();
      } else {
        log.warn("Infrastructure outcome not found in sweeping output, skipping OPA evaluation");
        return;
      }
    }
    if (!Status.SUCCEEDED.equals(stepResponse.getStatus())) {
      return;
    }
    if (!ciExecutionServiceConfig.isEnableOpaEvaluation()) {
      return;
    }
    // The OPA client is NON_PRIVILEGED, so its outbound JWT takes the principal from this thread's
    // context, and the orchestration thread that handles the step response carries none.
    try (PmsSecurityContextNoSideEffectsGuard ignore = new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
      evaluateInfrastructurePolicies(ambiance, infraStepOutcome);
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new UnexpectedException("Failed to restore security context after policy evaluation", ex);
    }
  }

  private void evaluateInfrastructurePolicies(Ambiance ambiance, InfraStepOutcome infraStepOutcome) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (!opaServiceClientHelper.shouldEvaluatePolicyWithRetry(accountId, AmbianceUtils.getOrgIdentifier(ambiance),
            AmbianceUtils.getProjectIdentifier(ambiance), OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE,
            OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN, HarnessYamlVersion.V1)) {
      return;
    }
    String infrastructureIdentifier = (String) infraStepOutcome.get(InfraStepOutcomeKeys.identifier);
    if (StringUtils.isEmpty(infrastructureIdentifier)) {
      throw new UnexpectedException("Infrastructure identifier is empty");
    }
    UnifiedInfraOpaEvaluationContext context =
        UnifiedInfraOpaEvaluationContext.builder()
            .infrastructure(infraStepOutcome)
            .environment((EnvironmentOutcome) infraStepOutcome.get(InfraStepOutcomeKeys.environment))
            .variables(unifiedServiceStepOpaHelper.resolveServiceVariables(ambiance))
            .build();
    try {
      String jsonString = RecastOrchestrationUtils.toSimpleJson(context);
      JsonNode jsonNode = JsonPipelineUtils.readTree(jsonString);
      OpaEvaluationResponseHolder response =
          opaServiceClientHelper.evaluateWithCredentialsWithRetry(OpaConstants.OPA_EVALUATION_TYPE_INFRASTRUCTURE,
              accountId, AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
              OpaConstants.OPA_EVALUATION_ACTION_INFRA_ENV_RUN, infrastructureIdentifier,
              PolicyEvalUtils.getEntityMetadataString(
                  infrastructureIdentifier, AmbianceUtils.getPipelineExecutionIdentifier(ambiance)),
              ambiance.getMetadata().getPrincipalInfo().getPrincipal(),
              String.valueOf(ambiance.getMetadata().getPrincipalInfo().getPrincipalType()), HarnessYamlVersion.V1,
              jsonNode);
      if (response != null && OpaConstants.OPA_STATUS_ERROR.equals(response.getStatus())) {
        String errorMessage = PolicyEvalUtils.buildDetailedPolicyEvaluationFailureMessage(response);
        throw new PolicyEvaluationFailureException(errorMessage);
      }
    } catch (PolicyEvaluationFailureException e) {
      log.error("Policy evaluation failed for unified infrastructure OPA runtime context evaluation", e);
      throw e;
    } catch (Exception e) {
      log.error("Failed to process JSON for unified infrastructure OPA evaluation", e);
      throw new UnexpectedException(
          String.format("Failed to process JSON for unified infrastructure OPA evaluation: %s", e.getMessage()), e);
    }
  }
}
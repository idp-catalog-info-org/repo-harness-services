/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.preStepCheckObserver;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.facilitation.facilitator.FacilitatorMetadata;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.engine.observers.PreStepCheckObserver;
import io.harness.exception.UnexpectedException;
import io.harness.opaclient.OpaServiceClientHelper;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.security.PmsSecurityContextNoSideEffectsGuard;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.PolicyEvalUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PreStepCheckPolicyEvaluationHandler implements PreStepCheckObserver {
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject OpaServiceClientHelper opaServiceClientHelper;

  /**
   * This is handler for preStepCheckObserver and is responsible for calling OPA service and
   * updating evaluationIds in planExecutionMetadata collection
   *
   * @param ambiance
   * @param facilitatorMetadata
   */
  @Override
  public void onPreStepCheck(Ambiance ambiance, FacilitatorMetadata facilitatorMetadata) {
    String jsonString = RecastOrchestrationUtils.toSimpleJson(facilitatorMetadata.getResolvedParams());
    JsonNode jsonNode = JsonPipelineUtils.readTree(jsonString);

    // The OPA client is NON_PRIVILEGED, so its outbound JWT takes the principal from this
    // thread's context, and pre-step-check observer threads carry none.
    OpaEvaluationResponseHolder opaEvaluationResponseHolder;
    try (PmsSecurityContextNoSideEffectsGuard ignore = new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
      opaEvaluationResponseHolder = opaServiceClientHelper.evaluateWithCredentialsWithRetry(
          OpaConstants.OPA_EVALUATION_TYPE_PIPELINE, AmbianceUtils.getAccountId(ambiance),
          AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
          OpaConstants.OPA_EVALUATION_ACTION_STEP_START, AmbianceUtils.obtainStepIdentifier(ambiance),
          PolicyEvalUtils.getEntityMetadataString(
              facilitatorMetadata.getName(), AmbianceUtils.getPipelineExecutionIdentifier(ambiance)),
          "", "", AmbianceUtils.getPipelineVersion(ambiance), jsonNode);
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new UnexpectedException("Failed to restore security context after policy evaluation", ex);
    }

    planExecutionMetadataService.updateEvaluatedPolicyIds(
        ambiance.getPlanExecutionId(), ImmutableList.of(Integer.parseInt(opaEvaluationResponseHolder.getId())));

    if (OpaConstants.OPA_STATUS_ERROR.equals(opaEvaluationResponseHolder.getStatus())) {
      String errorMessage = PolicyEvalUtils.buildPolicyEvaluationFailureMessage(opaEvaluationResponseHolder);
      throw new PolicyEvaluationFailureException(errorMessage);
    }
  }
}
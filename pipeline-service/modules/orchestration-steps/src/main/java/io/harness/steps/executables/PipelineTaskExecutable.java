/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.executables;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.steps.DelegateSelectorContextGuard.setDelegateSelectorsInOIDCContext;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.oidc.helper.OIDCContextHelper;
import io.harness.opaclient.OpaServiceClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.security.PmsSecurityContextEventGuard;
import io.harness.steps.executable.TaskExecutableWithCapabilities;
import io.harness.steps.workloadidentity.StepIdentityHelper;
import io.harness.tasks.ResponseData;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;
import io.harness.telemetry.helpers.StepsInstrumentationHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PolicyEvalUtils;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

// Task Executable with RBAC, Rollback and postTaskValidation
@OwnedBy(PIPELINE)
@Slf4j
public abstract class PipelineTaskExecutable<R extends ResponseData> extends TaskExecutableWithCapabilities<R> {
  @Inject OpaServiceClient opaServiceClient;
  @Inject private StepsInstrumentationHelper stepsInstrumentationHelper;
  @Inject private OIDCContextHelper oidcContextHelper;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  // Optional so non-pipeline-service contexts (e.g. SDK-only modules) can run without the identity wiring.
  @Inject(optional = true) private StepIdentityHelper stepIdentityHelper;

  @SneakyThrows
  @Override
  public TaskRequest obtainTask(Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    if (pmsFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_OIDC_AWS_SESSION_TAGS)) {
      setDelegateSelectorsInOIDCContext(stepParameters, oidcContextHelper);
    }
    try (PmsSecurityContextEventGuard ignored = new PmsSecurityContextEventGuard(ambiance)) {
      validateResources(ambiance, stepParameters);
      return this.obtainTaskAfterRbac(ambiance, stepParameters, inputPackage);
    }
  }

  // evaluating policies added in advanced section of the steps and updating status and failure info in the step
  // response
  public StepResponse postTaskValidate(
      Ambiance ambiance, StepBaseParameters stepParameters, StepResponse stepResponse) {
    handleTelemetryEventDTO(ambiance, stepParameters);
    if (Status.SUCCEEDED.equals(stepResponse.getStatus())) {
      return PolicyEvalUtils.evalPolicies(ambiance, stepParameters, stepResponse, opaServiceClient);
    }
    return stepResponse;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {}

  private void handleTelemetryEventDTO(Ambiance ambiance, StepBaseParameters stepParameters) {
    try {
      StepExecutionTelemetryEventDTO telemetryEventDTO = getStepExecutionTelemetryEventDTO(ambiance, stepParameters);
      if (telemetryEventDTO != null) {
        stepsInstrumentationHelper.publishStepEvent(ambiance, telemetryEventDTO);
      }
    } catch (Exception ex) {
      log.error(String.format("Failed to publish Telemetry event for - [%s]", this.getClass()), ex);
    }
  }

  protected StepExecutionTelemetryEventDTO getStepExecutionTelemetryEventDTO(
      Ambiance ambiance, StepBaseParameters stepParameters) {
    return null;
  }

  /**
   * Returns identity name → OIDC ID_TOKEN for all identities on this ambiance's context.
   * Never null; returns empty when the FF is off, HarnessID is unconfigured, or no identity resolves (§4.8).
   */
  protected Map<String, String> resolveIdentityTokens(Ambiance ambiance) {
    return stepIdentityHelper != null ? stepIdentityHelper.resolveIdentityTokens(ambiance) : Collections.emptyMap();
  }
}

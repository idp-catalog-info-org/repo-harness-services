/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.pipeline.steps;

import static io.harness.idp.onboarding.utils.Constants.BACKSTAGE_LOCATION_URL_TYPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.execution.states.AbstractStepExecutable;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.idp.onboarding.service.OnboardingService;
import io.harness.idp.steps.Constants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepResponse;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class IdpRegisterCatalogStep extends AbstractStepExecutable {
  @Inject OnboardingService onboardingService;
  public static final StepType STEP_TYPE = Constants.REGISTER_CATALOG_STEP_TYPE;
  public static final String CATALOG_URL_KEY = "catalogInfoUrl";
  public static final String ACCOUNT_ID_KEY = "accountId";

  protected boolean shouldPublishArtifact(StepStatus stepStatus) {
    return true;
  }

  protected boolean shouldPublishOutcome(StepStatus stepStatus) {
    return true;
  }

  @Override
  protected void modifyStepStatus(Ambiance ambiance, StepStatus stepStatus, String stepIdentifier) {
    StepMapOutput stepOutput = (StepMapOutput) stepStatus.getOutput();

    if (stepOutput != null && stepOutput.getMap() != null && stepOutput.getMap().containsKey(CATALOG_URL_KEY)) {
      onboardingService.registerLocationInBackstage(ambiance.getSetupAbstractionsMap().get(ACCOUNT_ID_KEY),
          BACKSTAGE_LOCATION_URL_TYPE, List.of(stepOutput.getMap().get(CATALOG_URL_KEY)));
    }
  }

  @Override
  protected StepResponse.StepOutcome getOutputVariableStepOutcome(
      Ambiance ambiance, Map<String, String> outputVariables) {
    StepResponse.StepOutcome stepOutcome = super.getOutputVariableStepOutcome(ambiance, outputVariables);
    String accountId = ambiance.getSetupAbstractionsMap().get(ACCOUNT_ID_KEY);
    log.info("Output Variables in account - {} for register catalog step - {}", accountId, outputVariables);
    if (outputVariables.containsKey(CATALOG_URL_KEY)) {
      onboardingService.registerLocationInBackstage(
          accountId, BACKSTAGE_LOCATION_URL_TYPE, List.of(outputVariables.get(CATALOG_URL_KEY)));
    }
    return stepOutcome;
  }
}

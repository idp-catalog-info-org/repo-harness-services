/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.ssca;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.execution.states.AbstractStepExecutable;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.ssca.beans.SscaConstants;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@OwnedBy(HarnessTeam.SSCA)
public class SscaArtifactSigningStep extends AbstractStepExecutable {
  private static final Logger log = LoggerFactory.getLogger(SscaArtifactSigningStep.class);
  public static final StepType STEP_TYPE = SscaConstants.SSCA_ARTIFACT_SIGNING_STEP_TYPE;

  @Override
  protected void modifyStepStatus(Ambiance ambiance, StepStatus stepStatus, String stepIdentifier) {}
}

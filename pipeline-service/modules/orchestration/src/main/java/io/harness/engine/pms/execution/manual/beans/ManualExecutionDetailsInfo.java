/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.manual.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.sdk.core.steps.executables.StepDetailsInfo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@RecasterAlias("io.harness.engine.pms.execution.manual.beans.ManualExecutionDetailsInfo")
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
public class ManualExecutionDetailsInfo implements StepDetailsInfo {
  // Below are required to show the duration of the wait in UI
  long startTs;
  long endTs;
}

/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.mapper;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.pms.execution.manual.beans.ManualExecutionAction;
import io.harness.pms.plan.execution.beans.request.ManualExecutionActionDto;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
public class ManualExecutionActionMapper {
  public ManualExecutionAction mapWaitStepAction(ManualExecutionActionDto manualExecutionActionDto) {
    if (manualExecutionActionDto == null) {
      throw new IllegalArgumentException("ManualExecutionActionDto cannot be null");
    }
    return switch (manualExecutionActionDto) {
          case MARK_AS_FAIL -> ManualExecutionAction.MARK_AS_FAIL;
          case MARK_AS_RESUME -> ManualExecutionAction.MARK_AS_RESUME;
      };
  }
}

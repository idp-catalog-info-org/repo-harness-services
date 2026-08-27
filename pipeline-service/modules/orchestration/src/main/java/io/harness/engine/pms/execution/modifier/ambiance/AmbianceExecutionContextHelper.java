/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.engine.pms.execution.modifier.ambiance;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.execution.NodeExecution.NodeExecutionBuilder;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class AmbianceExecutionContextHelper {
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;

  public void setAmbianceAndExecutionContextValues(Ambiance finalAmbiance, NodeExecutionBuilder nodeExecutionBuilder) {
    Ambiance newAmbiance = finalAmbiance;
    nodeExecutionBuilder.executionContext(AmbianceUtils.getExecutionContextFromAmbiance(finalAmbiance));
    if (pmsFeatureFlagService.isEnabled(
            AmbianceUtils.getAccountId(finalAmbiance), FeatureName.PIPE_REMOVE_AMBIANCE_POPULATION_IN_NODE_EXECUTION)) {
      newAmbiance = Ambiance.newBuilder()
                        .setPlanExecutionId(finalAmbiance.getPlanExecutionId())
                        .putSetupAbstractions(SetupAbstractionKeys.accountId, AmbianceUtils.getAccountId(finalAmbiance))
                        .build();
    }
    nodeExecutionBuilder.ambiance(newAmbiance);
  }
}

/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.plugin;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.sdk.core.plugin.PluginInfoProvider;
import io.harness.steps.StepSpecTypeConstants;

import com.google.inject.Singleton;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_APPROVALS, HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class ApprovalPluginInfoProvider implements PluginInfoProvider {
  @Override
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    // HarnessApproval is not a containerized step; skip plugin container creation entirely.
    return PluginCreationResponseWrapper.newBuilder().setShouldSkip(true).build();
  }

  @Override
  public boolean isSupported(String stepType) {
    return StepSpecTypeConstants.HARNESS_APPROVAL.equals(stepType);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.utils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.steps.resourcerestraint.HoldingScope;
import io.harness.steps.resourcerestraint.IResourceRestraintSpecParameters;

import lombok.experimental.UtilityClass;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = HarnessModuleComponent.CDS_COMMON_STEPS)
@UtilityClass
public class ResourceRestraintUtils {
  public String getReleaseEntityId(Ambiance ambiance, HoldingScope scope) {
    switch (scope) {
      case PLAN:
      case PIPELINE:
        return ambiance.getPlanExecutionId();
      case STAGE:
        return AmbianceUtils.getStageRuntimeIdAmbiance(ambiance);
      default:
        throw new IllegalStateException(String.format("HoldingScope [%s] is not supported", scope));
    }
  }

  public IResourceRestraintSpecParameters getIResourceRestraintSpecParameters(StepBaseParameters stepBaseParameters) {
    return (IResourceRestraintSpecParameters) stepBaseParameters.getSpec();
  }
}

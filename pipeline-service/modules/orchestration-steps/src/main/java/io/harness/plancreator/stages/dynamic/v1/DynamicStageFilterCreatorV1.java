/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages.dynamic.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.filters.v1.GenericStageFilterJsonCreatorV3;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import java.util.Collections;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class DynamicStageFilterCreatorV1 extends GenericStageFilterJsonCreatorV3<DynamicStageNodeV1> {
  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton(YAMLFieldNameConstants.DYNAMIC_STAGE_V1);
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, DynamicStageNodeV1 stageNode) {
    return null;
  }

  @Override
  public Class<DynamicStageNodeV1> getFieldClass() {
    return DynamicStageNodeV1.class;
  }
}

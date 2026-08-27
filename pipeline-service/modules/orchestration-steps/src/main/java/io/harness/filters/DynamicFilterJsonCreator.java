/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filters;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.stages.dynamic.DynamicStageNode;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Collections;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class DynamicFilterJsonCreator extends GenericStageFilterJsonCreatorV2<DynamicStageNode> {
  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton(StepSpecTypeConstants.DYNAMIC_STAGE);
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, DynamicStageNode stageNode) {
    return null;
  }

  @Override
  public Class<DynamicStageNode> getFieldClass() {
    return DynamicStageNode.class;
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, DynamicStageNode stageNode) {
    return FilterCreationResponse.builder().build();
  }
}

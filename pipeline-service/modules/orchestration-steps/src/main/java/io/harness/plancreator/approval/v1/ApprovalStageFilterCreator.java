/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.approval.v1;

import io.harness.filters.v1.GenericStageFilterJsonCreatorV3;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.approval.stage.v1.ApprovalStageNodeV1;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApprovalStageFilterCreator extends GenericStageFilterJsonCreatorV3<ApprovalStageNodeV1> {
  @Override
  public Set<String> getSupportedStageTypes() {
    return new HashSet<>(List.of(YAMLFieldNameConstants.APPROVAL_V1));
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, ApprovalStageNodeV1 stageNode) {
    return null;
  }

  @Override
  public Class<ApprovalStageNodeV1> getFieldClass() {
    return ApprovalStageNodeV1.class;
  }
}

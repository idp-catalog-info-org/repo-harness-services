/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages.dynamic.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.stages.stage.v1.AbstractStageNodeV1;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@JsonTypeName(YAMLFieldNameConstants.DYNAMIC_STAGE_V1)
@OwnedBy(PIPELINE)
public class DynamicStageNodeV1 extends AbstractStageNodeV1 {
  String type = YAMLFieldNameConstants.DYNAMIC_STAGE_V1;

  @JsonProperty(YAMLFieldNameConstants.DYNAMIC_STAGE_V1) DynamicStageConfigV1 dynamicStageConfig;
}

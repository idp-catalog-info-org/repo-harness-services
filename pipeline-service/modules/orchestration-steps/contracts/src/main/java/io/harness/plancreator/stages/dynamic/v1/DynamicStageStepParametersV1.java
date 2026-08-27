/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages.dynamic.v1;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.HarnessYamlVersion;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@TypeAlias("dynamicStageStepParametersV1")
@RecasterAlias("io.harness.plancreator.stages.dynamic.v1.DynamicStageStepParametersV1")
public class DynamicStageStepParametersV1 implements SpecParameters {
  String source;

  @JsonProperty("source-config") DynamicSourceConfigV1 sourceConfig;

  String childNodeId;

  @Override
  public String getVersion() {
    return HarnessYamlVersion.V1;
  }
}

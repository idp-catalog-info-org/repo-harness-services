/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cimanager.stages.V1;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.GitCloneStepInfoV1;
import io.harness.beans.steps.v1.BuildIntelligenceV1;
import io.harness.beans.steps.v1.CachingV1;
import io.harness.beans.yaml.extended.platform.V1.Arch;
import io.harness.beans.yaml.extended.platform.V1.OS;
import io.harness.beans.yaml.extended.platform.V1.PlatformV1;
import io.harness.beans.yaml.extended.runtime.V1.RuntimeV1;
import io.harness.beans.yaml.extended.volumes.V1.CIVolumeV1;
import io.harness.plancreator.stages.stage.v1.AbstractStageNodeV1;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.v1.NGVariableV1Wrapper;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName("ci")
@TypeAlias("UnifiedStageNodeV1")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.stages.V1.UnifiedStageNodeV1")
public class UnifiedStageNodeV1 extends AbstractStageNodeV1 {
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> needs;
  @JsonProperty("runs-on") String runsOn;
  List<JsonNode> steps;
  List<JsonNode> rollback;
  @JsonDeserialize(using = RuntimeV1Deserializer.class) RuntimeV1 runtime;
  GitCloneStepInfoV1 clone;
  PlatformV1 platform;
  ParameterField<Object> service;
  ParameterField<Object> environment;
  // IaCM entities
  ParameterField<String> workspace;
  @JsonProperty("remote-execution") ParameterField<String> remoteExecution;
  @JsonProperty("tofu-module") ParameterField<String> tofuModule;
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) ParameterField<List<String>> playbooks;
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) ParameterField<List<String>> inventories;

  ParameterField<Map<String, ParameterField<JsonNode>>> env;
  NGVariableV1Wrapper inputs;
  @JsonProperty("cache") CachingV1 cacheIntelligence;
  @JsonProperty("build-intelligence") BuildIntelligenceV1 buildIntelligence;
  ParameterField<List<CIVolumeV1>> volumes;
  public RuntimeV1 getRuntime() {
    if (this.runtime == null) {
      this.runtime = RuntimeV1.builder().cloud(RuntimeV1.CloudRuntimeSpec.builder().build()).build();
    }
    return this.runtime;
  }

  public PlatformV1 getPlatform() {
    if (this.platform == null) {
      this.platform = PlatformV1.builder()
                          .os(ParameterField.createValueField(OS.LINUX))
                          .arch(ParameterField.createValueField(Arch.AMD_64))
                          .build();
    }
    return this.platform;
  }
}

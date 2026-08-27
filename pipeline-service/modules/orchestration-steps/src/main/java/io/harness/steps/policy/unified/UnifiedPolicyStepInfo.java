/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.steps.policy.unified;

import static io.harness.annotations.dev.ProductModule.CDS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.policy.PolicyStepConstants;
import io.harness.steps.policy.PolicyStepSpecParameters;
import io.harness.steps.policy.custom.CustomPolicyStepSpec;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@CodePulse(module = CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnifiedPolicyStepInfo {
  @NotNull
  @JsonProperty(YAMLFieldNameConstants.POLICY_SETS)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  ParameterField<List<String>> policySets;
  @NotNull @JsonProperty(YAMLFieldNameConstants.POLICY_PAYLOAD) ParameterField<String> payload;

  public SpecParameters getSpecParameters() {
    CustomPolicyStepSpec customSpec = CustomPolicyStepSpec.builder().payload(payload).build();
    return PolicyStepSpecParameters.builder()
        .policySets(policySets)
        .type(PolicyStepConstants.CUSTOM_POLICY_STEP_TYPE)
        .policySpec(customSpec)
        .build();
  }
}
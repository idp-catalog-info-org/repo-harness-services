/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import io.harness.annotation.RecasterAlias;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@RecasterAlias("io.harness.ci.states.V1.cd.UnifiedInfraStepParameters")
public class UnifiedCDInfraStepParameters implements StepParameters {
  private ParameterField<String> serviceRef;
  private ParameterField<String> environmentRef;
  private ParameterField<String> infraId;
  private ParameterField<Map<String, Object>> infraInputs;
  ParameterField<Map<String, ParameterField<JsonNode>>> envVars;
  private ParameterField<String> envBranchRef;
  private ParameterField<String> envGroupRef;
}

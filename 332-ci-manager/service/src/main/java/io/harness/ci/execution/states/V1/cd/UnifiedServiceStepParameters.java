
/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_GITOPS})
@Data
@Builder
@RecasterAlias("io.harness.ci.states.V1.cd.UnifiedServiceStepParameters")
public class UnifiedServiceStepParameters implements StepParameters {
  private ParameterField<String> serviceRef;
  private ParameterField<Map<String, Object>> serviceInputs;
  private ParameterField<Map<String, Object>> infraInputs;
  private ParameterField<Map<String, Object>> envOverridesInputs;
  private ParameterField<Map<String, Object>> svcOverridesInputs;
  private ParameterField<String> environmentRef;
  private ParameterField<String> infraId;
  private List<String> childrenNodeIds;
  private ParameterField<String> branch;
  ParameterField<Map<String, ParameterField<JsonNode>>> envVars;
  private ParameterField<String> envBranchRef;
  private ParameterField<String> envGroupRef;
  // Optional swimlane hint declared on the stage service node. Validation-only: compared against the resolved service
  // type at runtime and never persisted into outcomes.
  private String serviceType;
}
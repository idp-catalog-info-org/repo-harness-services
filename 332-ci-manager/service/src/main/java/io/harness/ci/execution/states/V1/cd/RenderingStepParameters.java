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
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
@Value
@Builder
@RecasterAlias("io.harness.ci.states.V1.cd.RenderingStepParameters")
public class RenderingStepParameters implements StepParameters {
  List<String> addOnFiles;
  boolean fetch;
  String stepId;
  String id; // nothing is happening with image
  String name;
  // Pipeline + stage env vars propagated at plan creation so the fetch/render plugin task inherits them.
  ParameterField<Map<String, ParameterField<JsonNode>>> envVars;
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YAMLFieldNameConstants.INPUTS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.pms.yaml.YamlUtils;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineInputsUtils {
  String getInputsForPipeline(String inputsYaml, Map<String, Object> inputs) {
    String inputSetPipelineYaml = null;
    if (isNotEmpty(inputsYaml) && isNotEmpty(inputs)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Do not include both inputs and inputs_yaml parameters in the same request",
          "Please choose either the inputs parameter to provide input data in a structured format for v1 pipelines, or "
              + "the inputs_yaml parameter to provide input data in YAML format works for both v0 and v1. Do not "
              + "include "
              + "both parameters in the same request.",
          new InvalidRequestException(
              "Please choose either the inputs parameter to provide input data in a structured format for v1 "
              + "pipelines, or the inputs_yaml parameter to provide input data in YAML format works for both v0 and "
              + "v1. Do not include both parameters in the same request."));
    } else if (isNotEmpty(inputsYaml)) {
      inputSetPipelineYaml = inputsYaml;
    } else if (isNotEmpty(inputs)) {
      inputSetPipelineYaml = YamlUtils.writeYamlString(Map.of(INPUTS, inputs));
    }
    return inputSetPipelineYaml;
  }
}

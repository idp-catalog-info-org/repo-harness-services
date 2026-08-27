/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputs.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.ngpipeline.inputs.beans.entity.InputEntity;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.spec.server.pipeline.v1.model.InputsResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class PMSInputsElementMapper {
  public InputsResponseBody inputsResponseDTOPMS(
      Map<String, InputEntity> inputEntityMap, InputSetTemplateResponseDTOPMS response, String resolvedPipelineYaml) {
    InputsResponseBody inputsResponseBody = new InputsResponseBody();
    inputsResponseBody.setInputs(inputEntityMap);

    inputsResponseBody.setResolvedYaml(resolvedPipelineYaml);
    if (response != null) {
      inputsResponseBody.setTemplateYaml(
          response.getInputSetTemplateYaml() != null ? response.getInputSetTemplateYaml() : "");
      inputsResponseBody.setHasInputSets(Boolean.TRUE.equals(response.getHasInputSets()));
      inputsResponseBody.setModules(
          response.getModules() != null ? response.getModules().stream().toList() : new ArrayList());
      inputsResponseBody.setReplacedExpressions(
          response.getReplacedExpressions() != null ? response.getReplacedExpressions() : new ArrayList<>());
      inputsResponseBody.setReplacedExpressionsPerStage(response.getReplacedExpressionsPerStage() != null
              ? response.getReplacedExpressionsPerStage()
              : new HashMap<>());
    }
    return inputsResponseBody;
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputs.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.ngpipeline.inputs.beans.entity.InputEntity;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PMSInputsServiceImpl implements PMSInputsService {
  @Inject private ObjectMapper objectMapper;

  @Override
  public Optional<Map<String, InputEntity>> get(String pipelineYaml) {
    YamlField pipelineField = getPipelineField(pipelineYaml);
    Map<String, InputEntity> inputEntitiesMap = new HashMap<>();
    YamlField inputsField = pipelineField.getNode()
                                .getField(YAMLFieldNameConstants.PIPELINE)
                                .getNode()
                                .getField(YAMLFieldNameConstants.INPUTS);
    if (inputsField != null) {
      inputEntitiesMap = objectMapper.convertValue(inputsField.getNode().getCurrJsonNode(), new TypeReference<>() {});
    }
    return Optional.of(inputEntitiesMap);
  }

  private YamlField getPipelineField(String pipelineYaml) {
    try {
      return YamlUtils.readTree(pipelineYaml);
    } catch (IOException ex) {
      String message = "Invalid yaml during getInputs request";
      log.error(message, ex);
      throw new InvalidRequestException(message);
    }
  }
}

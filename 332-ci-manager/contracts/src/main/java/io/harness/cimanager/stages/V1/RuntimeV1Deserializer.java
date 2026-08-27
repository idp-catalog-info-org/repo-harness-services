/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cimanager.stages.V1;

import io.harness.beans.yaml.extended.runtime.V1.RuntimeV1;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class RuntimeV1Deserializer extends JsonDeserializer<RuntimeV1> {
  @Override
  public RuntimeV1 deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    ObjectMapper objectMapper = (ObjectMapper) jp.getCodec();
    if (jp.isExpectedStartObjectToken()) {
      return objectMapper.readValue(jp, RuntimeV1.class);
    }
    String text = jp.getText();
    if ("cloud".equals(text)) {
      return RuntimeV1.builder().cloud(RuntimeV1.CloudRuntimeSpec.builder().build()).build();
    }
    if ("shell".equals(text)) {
      return RuntimeV1.builder().shell(RuntimeV1.ShellRuntimeSpec.builder().build()).build();
    }
    if ("vm".equals(text)) {
      return RuntimeV1.builder().vm(RuntimeV1.VMRuntimeSpec.builder().build()).build();
    }
    if ("k8".equals(text)) {
      return RuntimeV1.builder().kubernetes(RuntimeV1.K8RuntimeSpec.builder().build()).build();
    }
    return null;
  }
}

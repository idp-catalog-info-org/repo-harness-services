/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.governance;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ID;
import static io.harness.pms.yaml.YAMLFieldNameConstants.WITH;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Represents infrastructure data from the deploy-to field in environment YAML.
 * This class encapsulates both the infrastructure ID and any additional configuration.
 */
@OwnedBy(CI)
@Data
@Builder
public class InfraData {
  private String id;
  private Map<String, Object> inputs;

  public static InfraData fromJsonNode(JsonNode infraNode) {
    if (infraNode == null) {
      return null;
    }

    // Case 1: infraNode is a text node
    if (infraNode.isTextual()) {
      return InfraData.builder().id(infraNode.asText()).build();
    }

    // Case 2: infraNode is an object with id field
    JsonNode idNode = infraNode.get(ID);
    if (idNode == null) {
      return null;
    }

    String infraId = idNode.asText();
    InfraDataBuilder infraDataBuilder = InfraData.builder().id(infraId);
    JsonNode withNode = infraNode.get(WITH);
    if (withNode != null && withNode.isObject()) {
      Map<String, Object> inputs = new HashMap<>();
      withNode.fields().forEachRemaining(entry -> inputs.put(entry.getKey(), entry.getValue()));
      infraDataBuilder.inputs(inputs);
    }
    return infraDataBuilder.build();
  }
}

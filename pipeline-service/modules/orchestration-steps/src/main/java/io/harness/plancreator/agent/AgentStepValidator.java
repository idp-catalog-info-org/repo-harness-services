/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.agent;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class AgentStepValidator {
  public void validateAgentName(JsonNode specNode, String stepIdentifier) {
    JsonNode agentNameNode = specNode.path("agentName");
    if (agentNameNode.isMissingNode() || agentNameNode.isNull() || agentNameNode.asText().isBlank()) {
      throw new InvalidRequestException(String.format("Agent step '%s' requires agentName to be set", stepIdentifier));
    }
  }

  public boolean isInsideContainerizedStepGroup(String stepGroupInfraType) {
    return stepGroupInfraType != null && !stepGroupInfraType.isEmpty();
  }
}

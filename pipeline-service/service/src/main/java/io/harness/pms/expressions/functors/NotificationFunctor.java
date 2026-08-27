/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;

@OwnedBy(PIPELINE)
public class NotificationFunctor implements LateBindingValue {
  Map<String, Object> resolutionMetadata;
  private final Ambiance ambiance;
  private final NodeExecutionService nodeExecutionService;

  @Inject
  public NotificationFunctor(
      Ambiance ambiance, Map<String, Object> resolutionMetadata, NodeExecutionService nodeExecutionService) {
    this.resolutionMetadata = resolutionMetadata;
    this.nodeExecutionService = nodeExecutionService;
    this.ambiance = ambiance;
  }

  @Override
  public Object bind() {
    if (EmptyPredicate.isEmpty(resolutionMetadata)) {
      return null;
    }
    // Get the current execution context
    if (ambiance == null) {
      return resolutionMetadata;
    }
    // Get the current node execution
    NodeExecution nodeExecution = null;
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    if (EmptyPredicate.isEmpty(nodeExecutionId)) {
      String planExecutionId = AmbianceUtils.getPipelineExecutionIdentifier(ambiance);
      Optional<NodeExecution> nodeExec = nodeExecutionService.getPipelineNodeExecutionWithProjections(
          planExecutionId, NodeProjectionUtils.withFailureInfo);
      if (nodeExec.isPresent()) {
        nodeExecution = nodeExec.get();
      }
    } else {
      nodeExecution = nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withFailureInfo);
    }

    if (nodeExecution != null) {
      // Get the error message based on the node type
      String errorMessage = getErrorMessage(nodeExecution);
      if (errorMessage != null) {
        resolutionMetadata.put("errorMessage", errorMessage);
      }
    }

    return resolutionMetadata;
  }

  private String getErrorMessage(NodeExecution nodeExecution) {
    if (nodeExecution.getFailureInfo() != null
        && EmptyPredicate.isNotEmpty(nodeExecution.getFailureInfo().getFailureDataList())) {
      boolean isNotificationTemplateFallbackEnabled =
          AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_NOTIFICATION_TEMPLATE_FALLBACK.name());
      if (!isNotificationTemplateFallbackEnabled) {
        return nodeExecution.getFailureInfo()
            .getFailureDataList()
            .stream()
            .filter(failureData -> failureData != null && EmptyPredicate.isNotEmpty(failureData.getMessage()))
            .map(failureData -> failureData.getMessage().trim())
            .filter(EmptyPredicate::isNotEmpty)
            .distinct()
            .collect(java.util.stream.Collectors.joining(", "));
      }
      String failureInfo =
          nodeExecution.getFailureInfo()
              .getFailureDataList()
              .stream()
              .filter(failureData -> failureData != null && EmptyPredicate.isNotEmpty(failureData.getMessage()))
              .map(failureData -> failureData.getMessage().trim())
              .filter(EmptyPredicate::isNotEmpty)
              .distinct()
              .collect(java.util.stream.Collectors.joining(", "));
      try {
        return NG_DEFAULT_OBJECT_MAPPER.writeValueAsString(failureInfo);
      } catch (JsonProcessingException e) {
        throw new InvalidRequestException("Failed to serialize failure info", e);
      }
    }
    return null;
  }
}

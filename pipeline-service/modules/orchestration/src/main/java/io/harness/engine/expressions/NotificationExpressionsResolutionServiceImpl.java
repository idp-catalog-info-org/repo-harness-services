/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import static io.harness.beans.constants.JsonConstants.RESOLVE_OBJECTS_VIA_JSON_SELECT;
import static io.harness.notification.NotificationConstants.NOTIFICATION_DURATION;
import static io.harness.notification.NotificationConstants.NOTIFICATION_DURATION_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_EVENT_DETAILS_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_EVENT_TYPE_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_IMAGE_STATUS;
import static io.harness.notification.NotificationConstants.NOTIFICATION_IMAGE_STATUS_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_NODE_END_DATE;
import static io.harness.notification.NotificationConstants.NOTIFICATION_NODE_END_DATE_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_NODE_START_DATE;
import static io.harness.notification.NotificationConstants.NOTIFICATION_NODE_START_DATE_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_NODE_STATUS;
import static io.harness.notification.NotificationConstants.NOTIFICATION_NODE_STATUS_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_THEME_COLOR;
import static io.harness.notification.NotificationConstants.NOTIFICATION_THEME_COLOR_EXPRESSION_KEY;
import static io.harness.pms.yaml.YAMLFieldNameConstants.TRUE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.VARIABLES;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.expression.common.ExpressionMode;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;
import io.harness.ngtriggers.service.TriggerFailureNotificationDetailsService;
import io.harness.notification.NotificationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.notificationbodyresolution.NotificationBodyResolutionInterface;
import io.harness.pms.pipeline.NotificationBodyResolutionRequest;
import io.harness.pms.pipeline.NotificationBodyResolutionResponse;
import io.harness.security.annotations.InternalApi;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class NotificationExpressionsResolutionServiceImpl implements NotificationBodyResolutionInterface {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private TriggerFailureNotificationDetailsService triggerFailureNotificationDetailsService;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Override
  @InternalApi
  public ResponseDTO<NotificationBodyResolutionResponse> resolveNotificationBody(
      @NotNull String accountIdentifier, NotificationBodyResolutionRequest notificationBodyResolutionRequest) {
    //    checkAccessPermissions();
    String nodeExecutionId = notificationBodyResolutionRequest.getResolutionMetadata().getOrDefault(
        NotificationConstants.NODE_EXECUTION_ID_KEY, "");
    String planExecutionId = notificationBodyResolutionRequest.getResolutionMetadata().getOrDefault(
        NotificationConstants.PLAN_EXECUTION_ID_KEY, "");
    String triggerFailureNotificationDetailsUuid =
        notificationBodyResolutionRequest.getResolutionMetadata().getOrDefault(
            NotificationConstants.TRIGGER_NOTIFICATION_DETAILS_ID_KEY, "");
    String eventType =
        notificationBodyResolutionRequest.getResolutionMetadata().getOrDefault(NotificationConstants.EVENT_TYPE, "");

    try (AutoLogContext ignore = new AutoLogContext(Map.of(NotificationConstants.NODE_EXECUTION_ID_KEY, nodeExecutionId,
                                                        NotificationConstants.ACCOUNT_IDENTIFIER, accountIdentifier,
                                                        NotificationConstants.PLAN_EXECUTION_ID_KEY, planExecutionId,
                                                        NotificationConstants.EVENT_TYPE, eventType),
             AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      Ambiance ambiance = null;
      TriggerNotificationData triggerNotificationData = null;
      if (EmptyPredicate.isNotEmpty(nodeExecutionId)) {
        ambiance = nodeExecutionService.getAmbiance(
            nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withAmbiance));
      } else if (EmptyPredicate.isNotEmpty(planExecutionId)) {
        Optional<NodeExecution> optional = nodeExecutionService.getPipelineNodeExecutionWithProjections(
            planExecutionId, NodeProjectionUtils.withAmbiance);

        if (optional.isEmpty()) {
          throw new EntityNotFoundException(
              "Could not find pipeline nodeExecution for resolving the secrets for planExecutionId: "
              + planExecutionId);
        }
        ambiance = nodeExecutionService.getAmbiance(optional.get());
      } else if (EmptyPredicate.isNotEmpty(triggerFailureNotificationDetailsUuid)) {
        triggerNotificationData =
            triggerFailureNotificationDetailsService.findById(triggerFailureNotificationDetailsUuid);
        if (triggerNotificationData == null) {
          throw new EntityNotFoundException("Could not find trigger notification data for resolving expressions"
              + triggerFailureNotificationDetailsUuid);
        }
      } else {
        throw new InvalidRequestException(
            "Either nodeExecutionId or planExecutionId shall be passed in request to resolve notification body");
      }
      Map<String, Object> contextMap =
          createContextMapForResolution(accountIdentifier, notificationBodyResolutionRequest);
      String resolvedBody = null;
      if (triggerNotificationData != null) {
        TriggerExpressionEvaluator triggerExpressionEvaluator =
            new TriggerExpressionEvaluator(triggerNotificationData, contextMap);
        Object resolvedObject = triggerExpressionEvaluator.resolve(
            notificationBodyResolutionRequest.getBody(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
        resolvedBody = (String) resolvedObject;
      } else {
        resolvedBody = (String) pmsEngineExpressionService.resolve(ambiance,
            notificationBodyResolutionRequest.getBody(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED, contextMap);
      }
      if (resolvedBody != null) {
        resolvedBody = resolvedBody.replaceAll("\"null\"", "\"\"");
        // TAB characters can be introduced via expression resolution (e.g., from error messages)
        resolvedBody = YamlPipelineUtils.sanitiseYamlForParsing(resolvedBody);
      }
      return ResponseDTO.newResponse(new NotificationBodyResolutionResponse(resolvedBody));
    }
  }

  private Map<String, Object> createContextMapForResolution(
      String accountIdentifier, NotificationBodyResolutionRequest notificationBodyResolutionRequest) {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put(NOTIFICATION_EVENT_TYPE_EXPRESSION_KEY,
        notificationBodyResolutionRequest.getResolutionMetadata().getOrDefault(NotificationConstants.EVENT_TYPE, ""));
    contextMap.put(NOTIFICATION_EVENT_DETAILS_EXPRESSION_KEY,
        notificationBodyResolutionRequest.getResolutionMetadata().getOrDefault(
            NotificationConstants.EVENT_DETAILS, ""));
    if (pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.CDS_RESOLVE_OBJECTS_VIA_JSON_SELECT)) {
      contextMap.put(RESOLVE_OBJECTS_VIA_JSON_SELECT, TRUE);
    }
    if (!notificationBodyResolutionRequest.getResolutionMetadata().isEmpty()) {
      contextMap.put(NOTIFICATION_NODE_START_DATE_EXPRESSION_KEY,
          notificationBodyResolutionRequest.getResolutionMetadata().get(NOTIFICATION_NODE_START_DATE));
      contextMap.put(NOTIFICATION_DURATION_EXPRESSION_KEY,
          notificationBodyResolutionRequest.getResolutionMetadata().get(NOTIFICATION_DURATION));
      contextMap.put(NOTIFICATION_NODE_END_DATE_EXPRESSION_KEY,
          notificationBodyResolutionRequest.getResolutionMetadata().get(NOTIFICATION_NODE_END_DATE));
      contextMap.put(NOTIFICATION_THEME_COLOR_EXPRESSION_KEY,
          notificationBodyResolutionRequest.getResolutionMetadata().get(NOTIFICATION_THEME_COLOR));
      contextMap.put(NOTIFICATION_IMAGE_STATUS_EXPRESSION_KEY,
          notificationBodyResolutionRequest.getResolutionMetadata().get(NOTIFICATION_IMAGE_STATUS));
      contextMap.put(NOTIFICATION_NODE_STATUS_EXPRESSION_KEY,
          notificationBodyResolutionRequest.getResolutionMetadata().get(NOTIFICATION_NODE_STATUS));
    }
    if (EmptyPredicate.isNotEmpty(notificationBodyResolutionRequest.getEnvironmentVariables())) {
      contextMap.put(VARIABLES, notificationBodyResolutionRequest.getEnvironmentVariables());
    }
    return contextMap;
  }
}

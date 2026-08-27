/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.beans.FeatureName;
import io.harness.beans.constants.JsonConstants;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.expression.common.ExpressionMode;
import io.harness.notification.NotificationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.pipeline.NotificationBodyResolutionRequest;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NotificationExpressionsResolutionServiceImplTest extends OrchestrationTestBase {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @InjectMocks NotificationExpressionsResolutionServiceImpl expressionsResolutionService;
  String notificationBody = "notification body";
  String accountIdentifier = "accountId";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testResolveWhenNodeExecutionIdProvided() {
    setPrincipalContext();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().build()).build();
    ExecutionContext executionContext = ExecutionContext.newBuilder().addLevels(Level.newBuilder().build()).build();
    NodeExecution returnedNodeExecution =
        NodeExecution.builder().ambiance(ambiance).executionContext(executionContext).build();
    String nodeExecutionId = "nodeExecutionId";
    doReturn(returnedNodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withAmbiance);
    doReturn(ambiance).when(nodeExecutionService).getAmbiance(returnedNodeExecution);
    Map<String, String> testResolutionMetadata = new HashMap<>();
    testResolutionMetadata.put(NotificationConstants.NODE_EXECUTION_ID_KEY, nodeExecutionId);
    testResolutionMetadata.put("START_DATE", "2023-01-01T10:00:00Z");
    testResolutionMetadata.put("END_DATE", "2023-01-01T11:00:00Z");
    testResolutionMetadata.put("DURATION_READABLE", "1 hour");
    testResolutionMetadata.put("COLOR", "#28a745");
    testResolutionMetadata.put("IMAGE_STATUS", "success");
    testResolutionMetadata.put("NODE_STATUS", "success");
    testResolutionMetadata.put(NotificationConstants.EVENT_TYPE, "PIPELINE_SUCCESS");
    testResolutionMetadata.put(NotificationConstants.EVENT_DETAILS, "PIPELINE_SUCCESS with details");

    expressionsResolutionService.resolveNotificationBody(accountIdentifier,
        NotificationBodyResolutionRequest.builder()
            .resolutionMetadata(testResolutionMetadata)
            .body(notificationBody)
            .build());

    Map<String, Object> expectedContextMap = new HashMap<>();
    expectedContextMap.put(NotificationConstants.NOTIFICATION_EVENT_TYPE_EXPRESSION_KEY, "PIPELINE_SUCCESS");
    expectedContextMap.put(
        NotificationConstants.NOTIFICATION_EVENT_DETAILS_EXPRESSION_KEY, "PIPELINE_SUCCESS with details");
    expectedContextMap.put("startDate", "2023-01-01T10:00:00Z");
    expectedContextMap.put("endDate", "2023-01-01T11:00:00Z");
    expectedContextMap.put("duration", "1 hour");
    expectedContextMap.put("themeColor", "#28a745");
    expectedContextMap.put("imageStatus", "success");
    expectedContextMap.put("nodeStatus", "success");

    verify(pmsEngineExpressionService, times(1))
        .resolve(ambiance, notificationBody, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, expectedContextMap);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testResolveWhenPlanExecutionIdProvided() {
    setPrincipalContext();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().setNodeType("PIPELINE").build()).build();
    ExecutionContext executionContext =
        ExecutionContext.newBuilder().addLevels(Level.newBuilder().setNodeType("PIPELINE").build()).build();
    NodeExecution returnedNodeExecution =
        NodeExecution.builder().ambiance(ambiance).executionContext(executionContext).build();
    String planExecutionId = "planExecutionId";
    doReturn(Optional.of(returnedNodeExecution))
        .when(nodeExecutionService)
        .getPipelineNodeExecutionWithProjections(planExecutionId, NodeProjectionUtils.withAmbiance);
    doReturn(ambiance).when(nodeExecutionService).getAmbiance(returnedNodeExecution);
    Map<String, String> testResolutionMetadata = new HashMap<>();
    testResolutionMetadata.put(NotificationConstants.PLAN_EXECUTION_ID_KEY, planExecutionId);
    testResolutionMetadata.put("START_DATE", "2023-01-01T09:00:00Z");
    testResolutionMetadata.put("END_DATE", "2023-01-01T09:30:00Z");
    testResolutionMetadata.put("DURATION_READABLE", "30 minutes");
    testResolutionMetadata.put("COLOR", "#dc3545");
    testResolutionMetadata.put("IMAGE_STATUS", "failed");
    testResolutionMetadata.put("NODE_STATUS", "failed");
    testResolutionMetadata.put(NotificationConstants.EVENT_TYPE, "PIPELINE_FAILED");
    testResolutionMetadata.put(NotificationConstants.EVENT_DETAILS, "PIPELINE_FAILED with details");

    expressionsResolutionService.resolveNotificationBody(accountIdentifier,
        NotificationBodyResolutionRequest.builder()
            .resolutionMetadata(testResolutionMetadata)
            .body(notificationBody)
            .build());

    Map<String, Object> expectedContextMap = new HashMap<>();
    expectedContextMap.put(NotificationConstants.NOTIFICATION_EVENT_TYPE_EXPRESSION_KEY, "PIPELINE_FAILED");
    expectedContextMap.put(
        NotificationConstants.NOTIFICATION_EVENT_DETAILS_EXPRESSION_KEY, "PIPELINE_FAILED with details");
    expectedContextMap.put("startDate", "2023-01-01T09:00:00Z");
    expectedContextMap.put("endDate", "2023-01-01T09:30:00Z");
    expectedContextMap.put("duration", "30 minutes");
    expectedContextMap.put("themeColor", "#dc3545");
    expectedContextMap.put("imageStatus", "failed");
    expectedContextMap.put("nodeStatus", "failed");

    verify(pmsEngineExpressionService, times(1))
        .resolve(ambiance, notificationBody, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, expectedContextMap);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testResolveWhenExecutionIdsNotProvided() {
    setPrincipalContext();
    assertThatThrownBy(()
                           -> expressionsResolutionService.resolveNotificationBody(accountIdentifier,
                               NotificationBodyResolutionRequest.builder()
                                   .resolutionMetadata(Collections.emptyMap())
                                   .body(notificationBody)
                                   .build()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testResolveNotificationBody_ContextMapContainsJsonSelectKey_WhenFFEnabled() {
    setPrincipalContext();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().build()).build();
    ExecutionContext executionContext = ExecutionContext.newBuilder().addLevels(Level.newBuilder().build()).build();
    NodeExecution returnedNodeExecution =
        NodeExecution.builder().ambiance(ambiance).executionContext(executionContext).build();
    String nodeExecutionId = "nodeExecutionId";
    doReturn(returnedNodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withAmbiance);
    doReturn(ambiance).when(nodeExecutionService).getAmbiance(returnedNodeExecution);
    when(pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.CDS_RESOLVE_OBJECTS_VIA_JSON_SELECT))
        .thenReturn(true);

    Map<String, String> testResolutionMetadata = new HashMap<>();
    testResolutionMetadata.put(NotificationConstants.NODE_EXECUTION_ID_KEY, nodeExecutionId);
    testResolutionMetadata.put(NotificationConstants.EVENT_TYPE, "PIPELINE_SUCCESS");

    expressionsResolutionService.resolveNotificationBody(accountIdentifier,
        NotificationBodyResolutionRequest.builder()
            .resolutionMetadata(testResolutionMetadata)
            .body(notificationBody)
            .build());

    ArgumentCaptor<Map> contextMapCaptor = ArgumentCaptor.forClass(Map.class);
    verify(pmsEngineExpressionService, times(1))
        .resolve(eq(ambiance), eq(notificationBody), eq(ExpressionMode.RETURN_NULL_IF_UNRESOLVED),
            contextMapCaptor.capture());
    Map<String, Object> capturedContextMap = contextMapCaptor.getValue();
    assertThat(capturedContextMap).containsKey(JsonConstants.RESOLVE_OBJECTS_VIA_JSON_SELECT);
    assertThat(capturedContextMap.get(JsonConstants.RESOLVE_OBJECTS_VIA_JSON_SELECT)).isEqualTo("true");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testResolveNotificationBody_ContextMapDoesNotContainJsonSelectKey_WhenFFDisabled() {
    setPrincipalContext();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(Level.newBuilder().build()).build();
    ExecutionContext executionContext = ExecutionContext.newBuilder().addLevels(Level.newBuilder().build()).build();
    NodeExecution returnedNodeExecution =
        NodeExecution.builder().ambiance(ambiance).executionContext(executionContext).build();
    String nodeExecutionId = "nodeExecutionId";
    doReturn(returnedNodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withAmbiance);
    doReturn(ambiance).when(nodeExecutionService).getAmbiance(returnedNodeExecution);
    when(pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.CDS_RESOLVE_OBJECTS_VIA_JSON_SELECT))
        .thenReturn(false);

    Map<String, String> testResolutionMetadata = new HashMap<>();
    testResolutionMetadata.put(NotificationConstants.NODE_EXECUTION_ID_KEY, nodeExecutionId);
    testResolutionMetadata.put(NotificationConstants.EVENT_TYPE, "PIPELINE_SUCCESS");

    expressionsResolutionService.resolveNotificationBody(accountIdentifier,
        NotificationBodyResolutionRequest.builder()
            .resolutionMetadata(testResolutionMetadata)
            .body(notificationBody)
            .build());

    ArgumentCaptor<Map> contextMapCaptor = ArgumentCaptor.forClass(Map.class);
    verify(pmsEngineExpressionService, times(1))
        .resolve(eq(ambiance), eq(notificationBody), eq(ExpressionMode.RETURN_NULL_IF_UNRESOLVED),
            contextMapCaptor.capture());
    Map<String, Object> capturedContextMap = contextMapCaptor.getValue();
    assertThat(capturedContextMap).doesNotContainKey(JsonConstants.RESOLVE_OBJECTS_VIA_JSON_SELECT);
  }

  private void setPrincipalContext() {
    Principal initialPrincipal = new ServicePrincipal("serviceName");
    SecurityContextBuilder.setContext(initialPrincipal);
    SourcePrincipalContextBuilder.setSourcePrincipal(initialPrincipal);
  }
}

/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.common.HttpUtils;
import io.harness.idp.common.PipelineTriggerUtils;
import io.harness.idp.configmanager.entities.CustomPluginInfoEntity;
import io.harness.idp.configmanager.entities.PluginInfoEntity;
import io.harness.idp.configmanager.repositories.PluginInfoRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.plugin.config.CustomPluginsConfig;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CustomPluginStatus;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;
import io.harness.spec.server.idp.v1.model.PluginInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CustomPluginServiceImplTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_PLUGIN_ID = "my_custom_plugin_l116Sh";
  private static final String TEST_NAMESPACE = "ns1";
  private static final String X_API_KEY = "dummy";
  private static final String TEST_ENV = "qa";
  private static final String TEST_PIPELINE_URL = "pipelineUrl";
  private static final String TEST_STATUS_API_URL = "https://api.harness.io/pipeline/status";
  private static final String TEST_EXECUTION_URL =
      "https://api.harness.io/pipeline/execution/{planExecutionId}/{stageNodeId}";
  private static final String TEST_LOG_URL = "https://api.harness.io/logs/{key}";
  private static final String TEST_PLAN_EXECUTION_ID = "plan-exec-123";
  private static final String TEST_STAGE_NODE_ID = "stage-node-456";
  private static final String TEST_LOG_KEY = "log-key-789";
  AutoCloseable openMocks;
  @Mock private NamespaceService namespaceService;
  @Mock private CustomPluginsConfig customPluginsConfig;
  @Mock private PluginInfoRepository pluginInfoRepository;
  private CustomPluginServiceImpl customPluginService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    customPluginService =
        new CustomPluginServiceImpl(customPluginsConfig, TEST_ENV, X_API_KEY, namespaceService, pluginInfoRepository);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testTriggerBuildPipeline() {
    NamespaceInfo namespaceInfo = mockNamespaceInfo();
    PluginInfoEntity pluginInfoEntity = CustomPluginInfoEntity.builder().build();

    when(customPluginsConfig.getTriggerPipelineUrl()).thenReturn(TEST_PIPELINE_URL);
    when(namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(namespaceInfo);
    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             TEST_PLUGIN_ID, TEST_ACCOUNT_IDENTIFIER, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.of(pluginInfoEntity));
    when(customPluginsConfig.getTriggerPipelineUrl()).thenReturn(TEST_PIPELINE_URL);

    try (MockedStatic<PipelineTriggerUtils> mockRestUtils = Mockito.mockStatic(PipelineTriggerUtils.class)) {
      customPluginService.triggerBuildPipeline(TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID);
      mockRestUtils.verify(()
                               -> PipelineTriggerUtils.trigger(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE),
                                   eq(TEST_ENV), eq(TEST_PIPELINE_URL), eq(""), any(), eq(X_API_KEY)));
    }
    verify(pluginInfoRepository)
        .update(eq(TEST_PLUGIN_ID), eq(TEST_ACCOUNT_IDENTIFIER), any(CustomPluginInfoEntity.class));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testTriggerBuildPipelineForADeletedPlugin() {
    NamespaceInfo namespaceInfo = mockNamespaceInfo();

    when(customPluginsConfig.getTriggerPipelineUrl()).thenReturn(TEST_PIPELINE_URL);
    when(namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(namespaceInfo);
    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             TEST_PLUGIN_ID, TEST_ACCOUNT_IDENTIFIER, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.empty());

    try (MockedStatic<PipelineTriggerUtils> mockRestUtils = Mockito.mockStatic(PipelineTriggerUtils.class)) {
      customPluginService.triggerBuildPipeline(TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID);
      mockRestUtils.verify(()
                               -> PipelineTriggerUtils.trigger(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE),
                                   eq(TEST_ENV), eq(TEST_PIPELINE_URL), eq(""), any(), eq(X_API_KEY)));
    }
    verify(pluginInfoRepository, never())
        .update(eq(TEST_PLUGIN_ID), eq(TEST_ACCOUNT_IDENTIFIER), any(CustomPluginInfoEntity.class));
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetCustomPluginStatus_PluginNotFound() {
    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             TEST_PLUGIN_ID, TEST_ACCOUNT_IDENTIFIER, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.empty());

    customPluginService.getCustomPluginStatus(TEST_PLUGIN_ID, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetCustomPluginStatus_SuccessfulStatus() {
    CustomPluginInfoEntity pluginEntity = CustomPluginInfoEntity.builder().build();
    pluginEntity.setStatusApiUrl(TEST_STATUS_API_URL);

    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             TEST_PLUGIN_ID, TEST_ACCOUNT_IDENTIFIER, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.of(pluginEntity));

    Map<String, Object> webhookDetails = new HashMap<>();
    webhookDetails.put("pipelineExecutionId", TEST_PLAN_EXECUTION_ID);

    Map<String, Object> executionSummary = new HashMap<>();
    executionSummary.put("status", "Success");
    executionSummary.put("startingNodeId", TEST_STAGE_NODE_ID);

    Map<String, Object> responseObject = new HashMap<>();
    responseObject.put("webhookProcessingDetails", webhookDetails);
    responseObject.put("pipelineExecutionSummary", executionSummary);

    String responseJson = "{\"data\":{\"webhookProcessingDetails\":{},\"pipelineExecutionSummary\":{}}}";

    try (MockedStatic<HttpUtils> httpUtilsMock = Mockito.mockStatic(HttpUtils.class);
         MockedStatic<GsonUtils> gsonUtilsMock = Mockito.mockStatic(GsonUtils.class);
         MockedStatic<CommonUtils> commonUtilsMock = Mockito.mockStatic(CommonUtils.class)) {
      httpUtilsMock.when(() -> HttpUtils.buildRequest(eq(TEST_STATUS_API_URL), any(), any(), any()))
          .thenReturn(Mockito.mock(Request.class));
      httpUtilsMock.when(() -> HttpUtils.executeRequest(any(Request.class), any())).thenReturn(responseJson);

      gsonUtilsMock.when(() -> GsonUtils.convertJsonStringToObject(eq(responseJson), eq(Map.class)))
          .thenReturn(responseObject);

      commonUtilsMock.when(() -> CommonUtils.findObjectByName(responseObject, "webhookProcessingDetails"))
          .thenReturn(webhookDetails);
      commonUtilsMock.when(() -> CommonUtils.findObjectByName(responseObject, "pipelineExecutionSummary"))
          .thenReturn(executionSummary);

      CustomPluginStatus status = customPluginService.getCustomPluginStatus(TEST_PLUGIN_ID, TEST_ACCOUNT_IDENTIFIER);

      assertNotNull(status);
      assertEquals("Success", status.getStatus());
    }
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetCustomPluginStatus_FailedStatusWithErrorDetails() {
    CustomPluginInfoEntity pluginEntity = CustomPluginInfoEntity.builder().build();
    pluginEntity.setStatusApiUrl(TEST_STATUS_API_URL);

    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             TEST_PLUGIN_ID, TEST_ACCOUNT_IDENTIFIER, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.of(pluginEntity));
    when(customPluginsConfig.getPipelineExecutionUrl()).thenReturn(TEST_EXECUTION_URL);

    Map<String, Object> webhookDetails = new HashMap<>();
    webhookDetails.put("pipelineExecutionId", TEST_PLAN_EXECUTION_ID);

    Map<String, Object> executionSummary = new HashMap<>();
    executionSummary.put("status", "Failed");
    executionSummary.put("startingNodeId", TEST_STAGE_NODE_ID);

    Map<String, Object> statusResponseObject = new HashMap<>();
    statusResponseObject.put("webhookProcessingDetails", webhookDetails);
    statusResponseObject.put("pipelineExecutionSummary", executionSummary);

    Map<String, Object> executableResponse = new HashMap<>();
    Map<String, Object> asyncData = new HashMap<>();
    asyncData.put("logKeys", List.of(TEST_LOG_KEY));
    executableResponse.put("task", asyncData);

    Map<String, Object> nodeValue = new HashMap<>();
    nodeValue.put("identifier", "Compile");
    nodeValue.put("status", "Failed");
    nodeValue.put("message", "Build compilation failed");
    nodeValue.put("executableResponses", List.of(executableResponse));
    nodeValue.put("logBaseKey", "base-log-key");

    Map<String, Object> nodeMap = new HashMap<>();
    nodeMap.put("node1", nodeValue);

    Map<String, Object> executionResponseObject = new HashMap<>();
    executionResponseObject.put("nodeMap", nodeMap);

    String statusResponseJson = "{\"status\":\"Failed\"}";
    String executionResponseJson = "{\"nodeMap\":{}}";

    try (MockedStatic<HttpUtils> httpUtilsMock = Mockito.mockStatic(HttpUtils.class);
         MockedStatic<GsonUtils> gsonUtilsMock = Mockito.mockStatic(GsonUtils.class);
         MockedStatic<CommonUtils> commonUtilsMock = Mockito.mockStatic(CommonUtils.class)) {
      httpUtilsMock.when(() -> HttpUtils.buildRequest(eq(TEST_STATUS_API_URL), any(), any(), any()))
          .thenReturn(Mockito.mock(Request.class));
      httpUtilsMock.when(() -> HttpUtils.buildRequest(any(String.class), any(), any(), any()))
          .thenReturn(Mockito.mock(Request.class));
      httpUtilsMock.when(() -> HttpUtils.executeRequest(any(Request.class), any()))
          .thenReturn(statusResponseJson, executionResponseJson);

      gsonUtilsMock.when(() -> GsonUtils.convertJsonStringToObject(eq(statusResponseJson), eq(Map.class)))
          .thenReturn(statusResponseObject);
      gsonUtilsMock.when(() -> GsonUtils.convertJsonStringToObject(eq(executionResponseJson), eq(Map.class)))
          .thenReturn(executionResponseObject);

      commonUtilsMock.when(() -> CommonUtils.findObjectByName(statusResponseObject, "webhookProcessingDetails"))
          .thenReturn(webhookDetails);
      commonUtilsMock.when(() -> CommonUtils.findObjectByName(statusResponseObject, "pipelineExecutionSummary"))
          .thenReturn(executionSummary);
      commonUtilsMock.when(() -> CommonUtils.findObjectByName(executionResponseObject, "nodeMap")).thenReturn(nodeMap);
      commonUtilsMock.when(() -> CommonUtils.findObjectByName(nodeValue, "message"))
          .thenReturn("Build compilation failed");

      CustomPluginStatus status = customPluginService.getCustomPluginStatus(TEST_PLUGIN_ID, TEST_ACCOUNT_IDENTIFIER);

      assertNotNull(status);
      assertEquals("Failed", status.getStatus());
      assertEquals("Build compilation failed", status.getError());
      assertEquals(TEST_LOG_KEY, status.getKey());
    }
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetCustomPluginStatus_FailedStatusWithoutLogKeys() {
    CustomPluginInfoEntity pluginEntity = CustomPluginInfoEntity.builder().build();
    pluginEntity.setStatusApiUrl(TEST_STATUS_API_URL);

    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             TEST_PLUGIN_ID, TEST_ACCOUNT_IDENTIFIER, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.of(pluginEntity));
    when(customPluginsConfig.getPipelineExecutionUrl()).thenReturn(TEST_EXECUTION_URL);

    Map<String, Object> webhookDetails = new HashMap<>();
    webhookDetails.put("pipelineExecutionId", TEST_PLAN_EXECUTION_ID);

    Map<String, Object> executionSummary = new HashMap<>();
    executionSummary.put("status", "Failed");
    executionSummary.put("startingNodeId", TEST_STAGE_NODE_ID);

    Map<String, Object> statusResponseObject = new HashMap<>();
    statusResponseObject.put("webhookProcessingDetails", webhookDetails);
    statusResponseObject.put("pipelineExecutionSummary", executionSummary);

    Map<String, Object> nodeValue = new HashMap<>();
    nodeValue.put("identifier", "Build_and_Push");
    nodeValue.put("status", "Failed");
    nodeValue.put("message", "Build failed");
    nodeValue.put("executableResponses", List.of());
    nodeValue.put("logBaseKey", "fallback-log-key");

    Map<String, Object> nodeMap = new HashMap<>();
    nodeMap.put("node1", nodeValue);

    Map<String, Object> executionResponseObject = new HashMap<>();
    executionResponseObject.put("nodeMap", nodeMap);

    String statusResponseJson = "{\"status\":\"Failed\"}";
    String executionResponseJson = "{\"nodeMap\":{}}";

    try (MockedStatic<HttpUtils> httpUtilsMock = Mockito.mockStatic(HttpUtils.class);
         MockedStatic<GsonUtils> gsonUtilsMock = Mockito.mockStatic(GsonUtils.class);
         MockedStatic<CommonUtils> commonUtilsMock = Mockito.mockStatic(CommonUtils.class)) {
      httpUtilsMock.when(() -> HttpUtils.buildRequest(eq(TEST_STATUS_API_URL), any(), any(), any()))
          .thenReturn(Mockito.mock(Request.class));
      httpUtilsMock.when(() -> HttpUtils.buildRequest(any(String.class), any(), any(), any()))
          .thenReturn(Mockito.mock(Request.class));
      httpUtilsMock.when(() -> HttpUtils.executeRequest(any(Request.class), any()))
          .thenReturn(statusResponseJson, executionResponseJson);

      gsonUtilsMock.when(() -> GsonUtils.convertJsonStringToObject(eq(statusResponseJson), eq(Map.class)))
          .thenReturn(statusResponseObject);
      gsonUtilsMock.when(() -> GsonUtils.convertJsonStringToObject(eq(executionResponseJson), eq(Map.class)))
          .thenReturn(executionResponseObject);

      commonUtilsMock.when(() -> CommonUtils.findObjectByName(statusResponseObject, "webhookProcessingDetails"))
          .thenReturn(webhookDetails);
      commonUtilsMock.when(() -> CommonUtils.findObjectByName(statusResponseObject, "pipelineExecutionSummary"))
          .thenReturn(executionSummary);
      commonUtilsMock.when(() -> CommonUtils.findObjectByName(executionResponseObject, "nodeMap")).thenReturn(nodeMap);
      commonUtilsMock.when(() -> CommonUtils.findObjectByName(nodeValue, "message")).thenReturn("Build failed");

      CustomPluginStatus status = customPluginService.getCustomPluginStatus(TEST_PLUGIN_ID, TEST_ACCOUNT_IDENTIFIER);

      assertNotNull(status);
      assertEquals("Failed", status.getStatus());
      assertEquals("Build failed", status.getError());
      assertEquals("fallback-log-key", status.getKey());
    }
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetCustomPluginStatus_FailedStatusWithException() {
    CustomPluginInfoEntity pluginEntity = CustomPluginInfoEntity.builder().build();
    pluginEntity.setStatusApiUrl(TEST_STATUS_API_URL);

    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             TEST_PLUGIN_ID, TEST_ACCOUNT_IDENTIFIER, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.of(pluginEntity));
    when(customPluginsConfig.getPipelineExecutionUrl()).thenReturn(TEST_EXECUTION_URL);

    Map<String, Object> webhookDetails = new HashMap<>();
    webhookDetails.put("pipelineExecutionId", TEST_PLAN_EXECUTION_ID);

    Map<String, Object> executionSummary = new HashMap<>();
    executionSummary.put("status", "Failed");
    executionSummary.put("startingNodeId", TEST_STAGE_NODE_ID);

    Map<String, Object> statusResponseObject = new HashMap<>();
    statusResponseObject.put("webhookProcessingDetails", webhookDetails);
    statusResponseObject.put("pipelineExecutionSummary", executionSummary);

    String statusResponseJson = "{\"status\":\"Failed\"}";

    try (MockedStatic<HttpUtils> httpUtilsMock = Mockito.mockStatic(HttpUtils.class);
         MockedStatic<GsonUtils> gsonUtilsMock = Mockito.mockStatic(GsonUtils.class);
         MockedStatic<CommonUtils> commonUtilsMock = Mockito.mockStatic(CommonUtils.class)) {
      httpUtilsMock.when(() -> HttpUtils.buildRequest(eq(TEST_STATUS_API_URL), any(), any(), any()))
          .thenReturn(Mockito.mock(Request.class));
      httpUtilsMock.when(() -> HttpUtils.buildRequest(any(String.class), any(), any(), any()))
          .thenReturn(Mockito.mock(Request.class));
      httpUtilsMock.when(() -> HttpUtils.executeRequest(any(Request.class), any()))
          .thenReturn(statusResponseJson)
          .thenThrow(new RuntimeException("Network error"));

      gsonUtilsMock.when(() -> GsonUtils.convertJsonStringToObject(eq(statusResponseJson), eq(Map.class)))
          .thenReturn(statusResponseObject);

      commonUtilsMock.when(() -> CommonUtils.findObjectByName(statusResponseObject, "webhookProcessingDetails"))
          .thenReturn(webhookDetails);
      commonUtilsMock.when(() -> CommonUtils.findObjectByName(statusResponseObject, "pipelineExecutionSummary"))
          .thenReturn(executionSummary);

      CustomPluginStatus status = customPluginService.getCustomPluginStatus(TEST_PLUGIN_ID, TEST_ACCOUNT_IDENTIFIER);

      assertNotNull(status);
      assertEquals("Failed", status.getStatus());
    }
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetCustomPluginStatusLogs_Success() {
    String expectedLogs = "Log line 1\nLog line 2\nError at line 3";

    when(customPluginsConfig.getPipelineExecutionLogUrl()).thenReturn(TEST_LOG_URL);

    try (MockedStatic<HttpUtils> httpUtilsMock = Mockito.mockStatic(HttpUtils.class)) {
      httpUtilsMock.when(() -> HttpUtils.buildRequest(any(String.class), any(), any(), any()))
          .thenReturn(Mockito.mock(Request.class));
      httpUtilsMock.when(() -> HttpUtils.executeRequest(any(Request.class), any())).thenReturn(expectedLogs);

      String logSummary = customPluginService.getCustomPluginStatusLogs(
          TEST_ACCOUNT_IDENTIFIER, "org-id", "project-id", "pipeline-id", TEST_LOG_KEY);

      assertNotNull(logSummary);
      assertEquals(expectedLogs, logSummary);
    }
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetCustomPluginStatusLogs_Exception() {
    when(customPluginsConfig.getPipelineExecutionLogUrl()).thenReturn(TEST_LOG_URL);

    try (MockedStatic<HttpUtils> httpUtilsMock = Mockito.mockStatic(HttpUtils.class)) {
      httpUtilsMock.when(() -> HttpUtils.buildRequest(any(String.class), any(), any(), any()))
          .thenReturn(Mockito.mock(Request.class));
      httpUtilsMock.when(() -> HttpUtils.executeRequest(any(Request.class), any()))
          .thenThrow(new RuntimeException("Log service unavailable"));

      String logSummary = customPluginService.getCustomPluginStatusLogs(
          TEST_ACCOUNT_IDENTIFIER, "org-id", "project-id", "pipeline-id", TEST_LOG_KEY);

      assertNotNull(logSummary);
      assertEquals("", logSummary);
    }
  }

  private NamespaceInfo mockNamespaceInfo() {
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(TEST_NAMESPACE);
    return namespaceInfo;
  }
}

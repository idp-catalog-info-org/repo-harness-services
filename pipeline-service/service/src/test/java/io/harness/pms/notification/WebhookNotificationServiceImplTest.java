/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.SHUBHENDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.cdstage.remote.CDNGStageSummaryResourceClient;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.node.service.impl.NodeExecutionServiceImpl;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.impl.PlanExecutionMetadataServiceImpl;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.notification.PipelineEventType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class WebhookNotificationServiceImplTest extends CategoryTest {
  private static String ACCOUNT_ID = "accountId";
  private static String ORG_ID = "orgId";
  private static String PROJECT_ID = "projectId";

  private static String PLAN_EXECUTION_ID = "planExecutionId";

  CDNGStageSummaryResourceClient cdngStageSummaryResourceClient;
  WebhookNotificationService webhookNotificationService;
  PlanExecutionMetadataService planExecutionMetadataService;
  PmsFeatureFlagHelper pmsFeatureFlagHelper;
  NodeExecutionService nodeExecutionService;
  PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  ScopeResolutionHelper scopeResolutionHelper;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    cdngStageSummaryResourceClient = mock(CDNGStageSummaryResourceClient.class, RETURNS_DEEP_STUBS);
    planExecutionMetadataService = mock(PlanExecutionMetadataServiceImpl.class, RETURNS_DEEP_STUBS);
    pmsFeatureFlagHelper = mock(PmsFeatureFlagHelper.class, RETURNS_DEEP_STUBS);
    nodeExecutionService = mock(NodeExecutionServiceImpl.class, RETURNS_DEEP_STUBS);
    scopeResolutionHelper = mock(ScopeResolutionHelper.class);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId("parentUniqueId")
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    when(scopeResolutionHelper.getScopeInfo(any(), any())).thenReturn(scopeInfo);
    pmsPipelineTemplateHelper = mock(PMSPipelineTemplateHelper.class, RETURNS_DEEP_STUBS);
    webhookNotificationService =
        new WebhookNotificationServiceImpl(cdngStageSummaryResourceClient, planExecutionMetadataService,
            pmsFeatureFlagHelper, nodeExecutionService, pmsPipelineTemplateHelper, scopeResolutionHelper);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetModuleInfo() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(ACCOUNT_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .build();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .addLevels(Level.newBuilder().setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE)))
            .build();
    ModuleInfo moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.PIPELINE_START);
    assertThat(moduleInfo).isNotNull();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetModuleInfoForStageLevelStageSuccess() throws IOException {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(ACCOUNT_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .build();
    String runtimeID = UUIDGenerator.generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setRuntimeId(runtimeID).setStepType(
                                StepType.newBuilder().setStepCategory(StepCategory.STAGE)))
                            .build();
    Response<ResponseDTO<Map<String, CDStageSummaryResponseDTO>>> restResponse =
        Response.success(ResponseDTO.newResponse(
            Map.of(runtimeID, CDStageSummaryResponseDTO.builder().service("s1").artifactDisplayName("sahil").build())));
    Call<ResponseDTO<Map<String, CDStageSummaryResponseDTO>>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(restResponse);
    Mockito
        .when(cdngStageSummaryResourceClient.listStageExecutionFormattedSummary(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, Lists.newArrayList(runtimeID), false))
        .thenReturn(responseDTOCall);
    ModuleInfo moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STAGE_SUCCESS);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("s1"));
    Mockito.verify(cdngStageSummaryResourceClient)
        .listStageExecutionFormattedSummary(ACCOUNT_ID, ORG_ID, PROJECT_ID, Lists.newArrayList(runtimeID), false);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetModuleInfoForRollbackStageLevelStageSuccess() throws IOException {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(ACCOUNT_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .build();
    String runtimeID = UUIDGenerator.generateUuid();
    String originalStageExecutionIdForRollbackMode = UUIDGenerator.generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .putAllSetupAbstractions(Map.of("accountId", ACCOUNT_ID))
            .addLevels(Level.newBuilder()
                           .setRuntimeId(runtimeID)
                           .setNodeType("IDENTITY_PLAN_NODE")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE)))
            .setOriginalStageExecutionIdForRollbackMode(originalStageExecutionIdForRollbackMode)
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.PIPELINE_ROLLBACK).build())
            .build();
    Response<ResponseDTO<Map<String, CDStageSummaryResponseDTO>>> restResponse =
        Response.success(ResponseDTO.newResponse(Map.of(originalStageExecutionIdForRollbackMode,
            CDStageSummaryResponseDTO.builder().service("s1").artifactDisplayName("sahil").build())));
    Call<ResponseDTO<Map<String, CDStageSummaryResponseDTO>>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(restResponse);
    Mockito.when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.CDS_USE_PARENT_NODE_TO_GET_MODULE_INFO))
        .thenReturn(true);
    Mockito
        .when(cdngStageSummaryResourceClient.listStageExecutionFormattedSummary(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, Lists.newArrayList(originalStageExecutionIdForRollbackMode), false))
        .thenReturn(responseDTOCall);
    ModuleInfo moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STAGE_SUCCESS);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("s1"));
    Mockito.verify(cdngStageSummaryResourceClient)
        .listStageExecutionFormattedSummary(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, Lists.newArrayList(originalStageExecutionIdForRollbackMode), false);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetModuleInfoForStageLevelStageStart() throws IOException {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                                .accountId(ACCOUNT_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .build();
    String runtimeID = UUIDGenerator.generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setIdentifier("stage1").setRuntimeId(runtimeID).setStepType(
                                StepType.newBuilder().setStepCategory(StepCategory.STAGE)))
                            .build();
    Response<ResponseDTO<Map<String, CDStageSummaryResponseDTO>>> restResponse = Response.success(
        ResponseDTO.newResponse(Map.of("stage1", CDStageSummaryResponseDTO.builder().service("s1").build())));
    Call<ResponseDTO<Map<String, CDStageSummaryResponseDTO>>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(restResponse);
    Mockito
        .when(cdngStageSummaryResourceClient.listStagePlanCreationFormattedSummary(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, PLAN_EXECUTION_ID, Lists.newArrayList("stage1"), false))
        .thenReturn(responseDTOCall);
    ModuleInfo moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STAGE_START);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("s1"));
    Mockito.verify(cdngStageSummaryResourceClient)
        .listStagePlanCreationFormattedSummary(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, PLAN_EXECUTION_ID, Lists.newArrayList("stage1"), false);
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testGetModuleInfoForVerifyStepWithProjectLevelTemplateMonitoredService() throws IOException {
    String runtimeID = UUIDGenerator.generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setIdentifier("CDS").setRuntimeId(runtimeID).setStepType(
                                StepType.newBuilder().setStepCategory(StepCategory.STAGE)))
                            .addLevels(Level.newBuilder()
                                           .setIdentifier(YAMLFieldNameConstants.VERIFY_STEP)
                                           .setRuntimeId(runtimeID)
                                           .setStepType(StepType.newBuilder()
                                                            .setStepCategory(StepCategory.STEP)
                                                            .setType(YAMLFieldNameConstants.VERIFY_STEP)
                                                            .build()))
                            .build();
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                                .accountId(ACCOUNT_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .build();
    String nodeExecutionId = generateUuid();
    String resolvedParams = readFile("moduleInfoVerifyStepTemplateMSFixedServiceEnv.json");
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .nodeId(generateUuid())
                                      .resolvedParams(PmsStepParameters.parse(resolvedParams))
                                      .build();
    doReturn(nodeExecution).when(nodeExecutionService).get((String) any());
    String templateYaml = readFile("moduleInfoVerifyStepTemplateMSFixedServiceEnvTemplate.yaml");
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder().yaml(templateYaml).build();
    doReturn(templateResponseDTO)
        .when(pmsPipelineTemplateHelper)
        .getTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    ModuleInfo moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STEP_FAILED);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("notifysvc"));
    assertThat(moduleInfo.getEnvironments()).isEqualTo(Lists.newArrayList("notifyenv"));
    resolvedParams = readFile("moduleInfoVerifyStepTemplateMSFixedServiceRuntimeEnv.json");
    nodeExecution = NodeExecution.builder()
                        .uuid(nodeExecutionId)
                        .ambiance(ambiance)
                        .nodeId(generateUuid())
                        .resolvedParams(PmsStepParameters.parse(resolvedParams))
                        .build();
    doReturn(nodeExecution).when(nodeExecutionService).get((String) any());
    templateYaml = readFile("moduleInfoVerifyStepTemplateMSFixedServiceRuntimeEnvTemplate.yaml");
    templateResponseDTO = TemplateResponseDTO.builder().yaml(templateYaml).build();
    doReturn(templateResponseDTO)
        .when(pmsPipelineTemplateHelper)
        .getTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STEP_FAILED);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("notifysvc"));
    assertThat(moduleInfo.getEnvironments()).isEqualTo(Lists.newArrayList("notifyenv"));
    resolvedParams = readFile("moduleInfoVerifyStepTemplateMSRuntimeServiceFixedEnv.json");
    nodeExecution = NodeExecution.builder()
                        .uuid(nodeExecutionId)
                        .ambiance(ambiance)
                        .nodeId(generateUuid())
                        .resolvedParams(PmsStepParameters.parse(resolvedParams))
                        .build();
    doReturn(nodeExecution).when(nodeExecutionService).get((String) any());
    templateYaml = readFile("moduleInfoVerifyStepTemplateMSRuntimeServiceFixedEnvTemplate.yaml");
    templateResponseDTO = TemplateResponseDTO.builder().yaml(templateYaml).build();
    doReturn(templateResponseDTO)
        .when(pmsPipelineTemplateHelper)
        .getTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STEP_FAILED);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("notifysvc"));
    assertThat(moduleInfo.getEnvironments()).isEqualTo(Lists.newArrayList("notifyenv"));
    resolvedParams = readFile("moduleInfoVerifyStepTemplateMSRuntimeServiceEnv.json");
    nodeExecution = NodeExecution.builder()
                        .uuid(nodeExecutionId)
                        .ambiance(ambiance)
                        .nodeId(generateUuid())
                        .resolvedParams(PmsStepParameters.parse(resolvedParams))
                        .build();
    doReturn(nodeExecution).when(nodeExecutionService).get((String) any());
    templateYaml = readFile("moduleInfoVerifyStepTemplateMSRuntimeServiceEnvTemplate.yaml");
    templateResponseDTO = TemplateResponseDTO.builder().yaml(templateYaml).build();
    doReturn(templateResponseDTO)
        .when(pmsPipelineTemplateHelper)
        .getTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STEP_FAILED);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("notifysvc"));
    assertThat(moduleInfo.getEnvironments()).isEqualTo(Lists.newArrayList("notifyenv"));
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testGetModuleInfoForVerifyStepWithOrgLevelTemplateMonitoredService() throws IOException {
    String runtimeID = UUIDGenerator.generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setIdentifier("CDS").setRuntimeId(runtimeID).setStepType(
                                StepType.newBuilder().setStepCategory(StepCategory.STAGE)))
                            .addLevels(Level.newBuilder()
                                           .setIdentifier(YAMLFieldNameConstants.VERIFY_STEP)
                                           .setRuntimeId(runtimeID)
                                           .setStepType(StepType.newBuilder()
                                                            .setStepCategory(StepCategory.STEP)
                                                            .setType(YAMLFieldNameConstants.VERIFY_STEP)
                                                            .build()))
                            .build();
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                                .accountId(ACCOUNT_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .build();
    String nodeExecutionId = generateUuid();
    String resolvedParams = readFile("moduleInfoVerifyStepOrgTemplateMSFixedServiceEnv.json");
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .nodeId(generateUuid())
                                      .resolvedParams(PmsStepParameters.parse(resolvedParams))
                                      .build();
    doReturn(nodeExecution).when(nodeExecutionService).get((String) any());
    String templateYaml = readFile("moduleInfoVerifyStepOrgTemplateMSFixedServiceEnvTemplate.yaml");
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder().yaml(templateYaml).build();
    doReturn(templateResponseDTO)
        .when(pmsPipelineTemplateHelper)
        .getTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    ModuleInfo moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STEP_FAILED);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("org.og_service"));
    assertThat(moduleInfo.getEnvironments()).isEqualTo(Lists.newArrayList("org.og_env"));
    resolvedParams = readFile("moduleInfoVerifyStepOrgTemplateMSRuntimeServiceEnv.json");
    nodeExecution = NodeExecution.builder()
                        .uuid(nodeExecutionId)
                        .ambiance(ambiance)
                        .nodeId(generateUuid())
                        .resolvedParams(PmsStepParameters.parse(resolvedParams))
                        .build();
    doReturn(nodeExecution).when(nodeExecutionService).get((String) any());
    templateYaml = readFile("moduleInfoVerifyStepOrgTemplateMSRuntimeServiceEnvTemplate.yaml");
    templateResponseDTO = TemplateResponseDTO.builder().yaml(templateYaml).build();
    doReturn(templateResponseDTO)
        .when(pmsPipelineTemplateHelper)
        .getTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STEP_FAILED);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("expression_svc"));
    assertThat(moduleInfo.getEnvironments()).isEqualTo(Lists.newArrayList("notifyenv"));
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testGetModuleInfoForVerifyStepWithAccountLevelTemplateMonitoredService() throws IOException {
    String runtimeID = UUIDGenerator.generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setIdentifier("CDS").setRuntimeId(runtimeID).setStepType(
                                StepType.newBuilder().setStepCategory(StepCategory.STAGE)))
                            .addLevels(Level.newBuilder()
                                           .setIdentifier(YAMLFieldNameConstants.VERIFY_STEP)
                                           .setRuntimeId(runtimeID)
                                           .setStepType(StepType.newBuilder()
                                                            .setStepCategory(StepCategory.STEP)
                                                            .setType(YAMLFieldNameConstants.VERIFY_STEP)
                                                            .build()))
                            .build();
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                                .accountId(ACCOUNT_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .build();
    String nodeExecutionId = generateUuid();
    String resolvedParams = readFile("moduleInfoVerifyStepAccountTemplateMSFixedServiceEnv.json");
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .nodeId(generateUuid())
                                      .resolvedParams(PmsStepParameters.parse(resolvedParams))
                                      .build();
    doReturn(nodeExecution).when(nodeExecutionService).get((String) any());
    String templateYaml = readFile("moduleInfoVerifyStepAccountTemplateMSFixedServiceEnvTemplate.yaml");
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder().yaml(templateYaml).build();
    doReturn(templateResponseDTO)
        .when(pmsPipelineTemplateHelper)
        .getTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    ModuleInfo moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STEP_FAILED);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("account.acc_service"));
    assertThat(moduleInfo.getEnvironments()).isEqualTo(Lists.newArrayList("account.acc_env"));
    resolvedParams = readFile("moduleInfoVerifyStepAccountTemplateMSRuntimeServiceEnv.json");
    nodeExecution = NodeExecution.builder()
                        .uuid(nodeExecutionId)
                        .ambiance(ambiance)
                        .nodeId(generateUuid())
                        .resolvedParams(PmsStepParameters.parse(resolvedParams))
                        .build();
    doReturn(nodeExecution).when(nodeExecutionService).get((String) any());
    templateYaml = readFile("moduleInfoVerifyStepAccountTemplateMSRuntimeServiceEnvTemplate.yaml");
    templateResponseDTO = TemplateResponseDTO.builder().yaml(templateYaml).build();
    doReturn(templateResponseDTO)
        .when(pmsPipelineTemplateHelper)
        .getTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STEP_FAILED);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("expression_svc"));
    assertThat(moduleInfo.getEnvironments()).isEqualTo(Lists.newArrayList("notifyenv"));
  }

  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testGetModuleInfoForVerifyStepWithDefaultMonitoredService() throws IOException {
    String resolvedParamsJson = readFile("moduleInfoVerifyStepDefaultMSResolvedParams.json");
    String runtimeID = UUIDGenerator.generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setIdentifier("CDS").setRuntimeId(runtimeID).setStepType(
                                StepType.newBuilder().setStepCategory(StepCategory.STAGE)))
                            .addLevels(Level.newBuilder()
                                           .setIdentifier(YAMLFieldNameConstants.VERIFY_STEP)
                                           .setRuntimeId(runtimeID)
                                           .setStepType(StepType.newBuilder()
                                                            .setStepCategory(StepCategory.STEP)
                                                            .setType(YAMLFieldNameConstants.VERIFY_STEP)
                                                            .build()))
                            .build();
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                                .accountId(ACCOUNT_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .build();
    String nodeExecutionId = generateUuid();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .nodeId(generateUuid())
                                      .resolvedParams(PmsStepParameters.parse(resolvedParamsJson))
                                      .build();
    doReturn(nodeExecution).when(nodeExecutionService).get((String) any());
    ModuleInfo moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STEP_FAILED);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("notifysvc"));
    assertThat(moduleInfo.getEnvironments()).isEqualTo(Lists.newArrayList("notifyenv"));
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }
}

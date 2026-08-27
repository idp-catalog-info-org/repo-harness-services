/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.account.settings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.licensing.Edition.DEVOPS_ESSENTIALS;
import static io.harness.licensing.Edition.ENTERPRISE;
import static io.harness.licensing.Edition.ESSENTIALS;
import static io.harness.licensing.Edition.FREE;
import static io.harness.licensing.Edition.TEAM;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.account.overrides.EditionBasedAccountConfigStrategyFactory;
import io.harness.account.overrides.EnterpriseAccountConfigStrategy;
import io.harness.account.overrides.EssentialsAccountConfigStrategy;
import io.harness.account.overrides.FreeAccountConfigStrategy;
import io.harness.account.overrides.TeamAccountConfigStrategy;
import io.harness.account.settings.response.PlanExecutionSettingResponse;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.config.OrchestrationRestrictionConfiguration;
import io.harness.config.PlanExecutionRestrictionConfig;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PriorityConcurrentExecutionsMetadata;
import io.harness.execution.PriorityProjects;
import io.harness.execution.PriorityType;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.accountoverrides.ExpressionCallType;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)

public class PipelineSettingsServiceImplTest extends OrchestrationTestBase {
  public static final String ACCOUNT_ID = "ACCOUNT_ID";
  public static final String PIPELINE_IDENTIFIER = "PIPELINE_IDENTIFIER";
  @Mock NgLicenseHttpClient ngLicenseHttpClient;

  @Mock OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration;

  @Mock PlanExecutionService planExecutionService;

  @InjectMocks PipelineSettingsServiceImpl pipelineSettingsService;

  @Mock PmsFeatureFlagService featureFlagService;

  @Mock NGSettingsClient ngSettingsClient;

  @Mock PipelineRetentionService pipelineRetentionService;

  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;

  @Mock Call<ResponseDTO<SettingValueResponseDTO>> settingsRequest;

  @Mock Call<ResponseDTO<List<ModuleLicenseDTO>>> licenseRequest;

  @Mock EditionBasedAccountConfigStrategyFactory editionBasedAccountConfigStrategyFactory;

  @Mock TeamAccountConfigStrategy teamAccountConfigStrategy;

  @Mock FreeAccountConfigStrategy freeAccountConfigStrategy;

  @Mock EnterpriseAccountConfigStrategy enterpriseAccountConfigStrategy;

  @Mock EssentialsAccountConfigStrategy essentialsAccountConfigStrategy;

  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetEdition() throws ExecutionException {
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();

    // Case 1: Only FREE edition
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    Edition edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(FREE);

    // Case 2: TEAM edition should take priority over FREE
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(TEAM);

    // Case 3: ENTERPRISE should take priority over TEAM
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(ENTERPRISE);

    // **NEW CASES FOR DEVOPS ESSENTIALS**

    // Case 4: Only DEVOPS_ESSENTIALS (should not be FREE)
    moduleLicenseDTOS.clear();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(DEVOPS_ESSENTIALS).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(DEVOPS_ESSENTIALS);

    // Case 5: DEVOPS_ESSENTIALS + FREE → should return DEVOPS_ESSENTIALS
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(DEVOPS_ESSENTIALS);

    // Case 6: DEVOPS_ESSENTIALS + TEAM → should return TEAM (higher priority)
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(TEAM);

    // Case 7: DEVOPS_ESSENTIALS + ENTERPRISE → should return ENTERPRISE (highest priority)
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(ENTERPRISE);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionNotFree() {
    // edition == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    doReturn(3L).when(planExecutionService).countRunningExecutionsForGivenPipelineInAccount(any());
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.FREE)).thenReturn(freeAccountConfigStrategy);
    when(freeAccountConfigStrategy.getPipelineLevelMaxConcurrency("ACCOUNT_ID", null)).thenReturn(Long.MAX_VALUE);

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isFalse();
    assertThat(planExecutionSettingResponse.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetMaxExpressionMongoCallsReturnsOverrideWhenPresent() {
    // A per-account override always wins, regardless of edition or config toggles.
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    try {
      List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
      moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(FREE).build());
      mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
      when(editionBasedAccountConfigStrategyFactory.getStrategy(FREE)).thenReturn(freeAccountConfigStrategy);
      when(freeAccountConfigStrategy.getMaxExpressionCalls("ACCOUNT_ID", ExpressionCallType.MONGO)).thenReturn(42);
      assertEquals(42, pipelineSettingsService.getMaxExpressionCalls("ACCOUNT_ID", ExpressionCallType.MONGO));
    } finally {
      mockRestStatic.close();
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetMaxExpressionMongoCallsUnboundedWhenRestrictionDisabled() {
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    try {
      List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
      moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(FREE).build());
      mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
      when(editionBasedAccountConfigStrategyFactory.getStrategy(FREE)).thenReturn(freeAccountConfigStrategy);
      when(freeAccountConfigStrategy.getMaxExpressionCalls("ACCOUNT_ID", ExpressionCallType.MONGO))
          .thenReturn(Integer.MAX_VALUE);

      // Restriction toggle off for the account's edition -> unbounded (no enforcement).
      assertEquals(
          Integer.MAX_VALUE, pipelineSettingsService.getMaxExpressionCalls("ACCOUNT_ID", ExpressionCallType.MONGO));
    } finally {
      mockRestStatic.close();
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetMaxExpressionMongoCallsUsesEditionValueWhenRestrictionEnabled() {
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    try {
      List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
      moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ENTERPRISE).build());
      mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
      when(editionBasedAccountConfigStrategyFactory.getStrategy(ENTERPRISE))
          .thenReturn(enterpriseAccountConfigStrategy);
      when(enterpriseAccountConfigStrategy.getMaxExpressionCalls("ACCOUNT_ID", ExpressionCallType.MONGO))
          .thenReturn(2000);

      assertEquals(2000, pipelineSettingsService.getMaxExpressionCalls("ACCOUNT_ID", ExpressionCallType.MONGO));
    } finally {
      mockRestStatic.close();
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetPriorityExecutionPreferences() {
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<SettingResponseDTO> concurrencySettings = new ArrayList<>();
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder().identifier("pipeline_execution_concurrency_priority_limit").value("200").build())
            .build());
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder().identifier("pipeline_execution_concurrency_priority_type").value("High").build())
            .build());
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(SettingDTO.builder()
                         .identifier("pipeline_execution_concurrency_priority_projects")
                         .value("[\n  {\n    \"fqn\": \"default.test\",\n    \"uniqueId\": \"12345\"\n  }\n]")
                         .build())
            .build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(concurrencySettings);
    when(featureFlagService.isEnabled("ACCOUNT_ID", FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY.name()))
        .thenReturn(true);
    List<PriorityProjects> projects = new ArrayList<>();
    projects.add(PriorityProjects.builder().fqn("default.test").uniqueId("12345").build());

    PriorityConcurrentExecutionsMetadata expectedResults = PriorityConcurrentExecutionsMetadata.builder()
                                                               .priorityProjectsList(projects)
                                                               .priorityType("High")
                                                               .priorityConcurrentExecutionsLimit(200)
                                                               .build();

    PriorityConcurrentExecutionsMetadata priorityConcurrentExecutionsMetadata =
        pipelineSettingsService.getPriorityExecutionPreferences("ACCOUNT_ID");
    assertThat(priorityConcurrentExecutionsMetadata.getPriorityProjectsList().size()).isEqualTo(1);
    assertThat(expectedResults.getPriorityProjectsList().get(0).getFqn()).isEqualTo("default.test");
    assertThat(expectedResults.getPriorityProjectsList().get(0).getUniqueId()).isEqualTo("12345");
    assertThat(expectedResults.getPriorityType()).isEqualTo("High");
    assertThat(expectedResults.getPriorityConcurrentExecutionsLimit()).isEqualTo(200);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetPriorityExecutionPreferencesWithEmptyPriorityList() {
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<SettingResponseDTO> concurrencySettings = new ArrayList<>();
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder().identifier("pipeline_execution_concurrency_priority_limit").value("200").build())
            .build());
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder().identifier("pipeline_execution_concurrency_priority_type").value("High").build())
            .build());
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder().identifier("pipeline_execution_concurrency_priority_projects").value(null).build())
            .build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(concurrencySettings);
    when(featureFlagService.isEnabled("ACCOUNT_ID", FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY.name()))
        .thenReturn(true);
    List<PriorityProjects> projects = new ArrayList<>();

    PriorityConcurrentExecutionsMetadata expectedResults = PriorityConcurrentExecutionsMetadata.builder()
                                                               .priorityProjectsList(projects)
                                                               .priorityType("High")
                                                               .priorityConcurrentExecutionsLimit(200)
                                                               .build();

    PriorityConcurrentExecutionsMetadata priorityConcurrentExecutionsMetadata =
        pipelineSettingsService.getPriorityExecutionPreferences("ACCOUNT_ID");
    assertThat(priorityConcurrentExecutionsMetadata.getPriorityProjectsList().size()).isEqualTo(0);
    assertThat(expectedResults.getPriorityType()).isEqualTo("High");
    assertThat(expectedResults.getPriorityConcurrentExecutionsLimit()).isEqualTo(200);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionFree() {
    // editon == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    // runningExecutionsForGivenPipeline >= maxCount == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    doReturn(0L).when(planExecutionService).countRunningExecutionsForGivenPipelineInAccount("ACCOUNT_ID");

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.FREE)).thenReturn(freeAccountConfigStrategy);
    when(freeAccountConfigStrategy.getPipelineLevelMaxConcurrency("ACCOUNT_ID", null)).thenReturn(1L);

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
    assertThat(planExecutionSettingResponse.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionNotTeam() throws UnsupportedEncodingException {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForTeam() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.TEAM)).thenReturn(teamAccountConfigStrategy);
    when(teamAccountConfigStrategy.getPipelineLevelMaxConcurrency("ACCOUNT_ID", null)).thenReturn(Long.MAX_VALUE);

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID");

    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isFalse();
    assertThat(planExecutionSettingResponse.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionTeam() {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForTeam() == True
    // runningExecutionsForGivenPipeline >= maxCount == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    doReturn(0L).when(planExecutionService).countRunningExecutionsForGivenPipelineInAccount("ACCOUNT_ID");

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.TEAM)).thenReturn(teamAccountConfigStrategy);
    when(teamAccountConfigStrategy.getPipelineLevelMaxConcurrency("ACCOUNT_ID", null)).thenReturn(2L);

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
    assertThat(planExecutionSettingResponse.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionNotEnterprise() throws UnsupportedEncodingException {
    // editon == ENTERPRISE && orchestrationRestrictionConfiguration.isUseRestrictionForEnterprise() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getPipelineLevelMaxConcurrency("ACCOUNT_ID", null)).thenReturn(Long.MAX_VALUE);

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID");

    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isFalse();
    assertThat(planExecutionSettingResponse.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionEnterpriseGreaterThanMaxCount() throws UnsupportedEncodingException {
    // edition == ENTERPRISE && orchestrationRestrictionConfiguration.isUseRestrictionForENTERPRISE() == True
    // runningExecutionsForGivenPipeline >= maxCount == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    doReturn(100L).when(planExecutionService).countRunningExecutionsForGivenPipelineInAccount("ACCOUNT_ID");

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getPipelineLevelMaxConcurrency("ACCOUNT_ID", null)).thenReturn(3L);

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isTrue();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
    assertThat(planExecutionSettingResponse.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionEnterprise() {
    // edition == ENTERPRISE && orchestrationRestrictionConfiguration.isUseRestrictionForEnterprise() == True &&
    // runningExecutionsForGivenPipeline >= maxCount == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    doReturn(0L).when(planExecutionService).countRunningExecutionsForGivenPipelineInAccount("ACCOUNT_ID");

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getPipelineLevelMaxConcurrency("ACCOUNT_ID", null)).thenReturn(3L);

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
    assertThat(planExecutionSettingResponse.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountForFree() {
    // editon == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.FREE)).thenReturn(freeAccountConfigStrategy);
    when(freeAccountConfigStrategy.getMaxPipelineCreationLimit("ACCOUNT_ID")).thenReturn(1);
    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountForNotFree() {
    // edition != FREE || orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.FREE)).thenReturn(freeAccountConfigStrategy);
    when(freeAccountConfigStrategy.getMaxPipelineCreationLimit("ACCOUNT_ID")).thenReturn(Integer.MAX_VALUE);
    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountForTeam() {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.TEAM)).thenReturn(teamAccountConfigStrategy);
    when(teamAccountConfigStrategy.getMaxPipelineCreationLimit("ACCOUNT_ID")).thenReturn(2);

    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(2L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountForNotTeam() {
    // edition != TEAM || orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.TEAM)).thenReturn(teamAccountConfigStrategy);
    when(teamAccountConfigStrategy.getMaxPipelineCreationLimit("ACCOUNT_ID")).thenReturn(Integer.MAX_VALUE);
    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountForEnterprise() {
    // edition == ENTERPRISE || orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getMaxPipelineCreationLimit("ACCOUNT_ID")).thenReturn(3);

    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(3L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountForNotEnterprise() {
    // edition != ENTERPRISE || orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getMaxPipelineCreationLimit("ACCOUNT_ID")).thenReturn(Integer.MAX_VALUE);
    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionFree() {
    // edition == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getMaxConcurrencyRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.FREE)).thenReturn(freeAccountConfigStrategy);
    when(freeAccountConfigStrategy.getStepOrStageMaxConcurrency()).thenReturn(3);
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.FREE)).thenReturn(freeAccountConfigStrategy);
    when(freeAccountConfigStrategy.getMaxParallelismStopRestriction()).thenReturn(5L);

    when(freeAccountConfigStrategy.getStepOrStageMaxConcurrency(ACCOUNT_ID)).thenReturn(10);
    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 4);
    assertThat(count).isEqualTo(10);

    // to test: return config value. config value > child count, then it should return config value
    when(freeAccountConfigStrategy.getStepOrStageMaxConcurrency()).thenReturn(6);
    count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 4);
    assertThat(count).isEqualTo(6);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionNotFree() {
    // edition == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.FREE)).thenReturn(freeAccountConfigStrategy);
    when(freeAccountConfigStrategy.getMaxParallelismStopRestriction()).thenReturn(Long.MAX_VALUE);

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.FREE)).thenReturn(freeAccountConfigStrategy);
    when(freeAccountConfigStrategy.getStepOrStageMaxConcurrency()).thenReturn(20);

    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 1);
    assertThat(count).isEqualTo(20);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionFreeException() {
    // edition == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    // && childCount > orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getFree() ==
    // True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.FREE)).thenReturn(freeAccountConfigStrategy);
    when(freeAccountConfigStrategy.getMaxParallelismStopRestriction()).thenReturn(1L);

    assertThatThrownBy(() -> pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 4L))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "You are attempting to run more than 1 concurrent stages or steps, which exceeds the current limit. Please "
            + "upgrade your plan to Team/Enterprise (Paid) or reduce concurrent steps or stages. For more details on "
            + "concurrency limits, visit: "
            + "https://developer.harness.io/docs/platform/pipelines/pipeline-settings/#fixed-pipeline-settings");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionTeam() {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.TEAM)).thenReturn(teamAccountConfigStrategy);
    when(teamAccountConfigStrategy.getStepOrStageMaxConcurrency()).thenReturn(2);
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.TEAM)).thenReturn(teamAccountConfigStrategy);
    when(teamAccountConfigStrategy.getMaxParallelismStopRestriction()).thenReturn(2L);

    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 1L);
    assertThat(count).isEqualTo(2L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionNotTeam() {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.TEAM)).thenReturn(teamAccountConfigStrategy);
    when(teamAccountConfigStrategy.getMaxParallelismStopRestriction()).thenReturn(Long.MAX_VALUE);

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.TEAM)).thenReturn(teamAccountConfigStrategy);
    when(teamAccountConfigStrategy.getStepOrStageMaxConcurrency()).thenReturn(50);

    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 2L);
    assertThat(count).isEqualTo(50L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionTeamException() {
    // edition == TEAM && orchestrationRestrictionConfiguration.isUseRestrictionForTeam() == True
    // && childCount > orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getFree() ==
    // True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.TEAM)).thenReturn(teamAccountConfigStrategy);
    when(teamAccountConfigStrategy.getMaxParallelismStopRestriction()).thenReturn(2L);
    assertThatThrownBy(() -> pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 4L))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "You are attempting to run more than 2 concurrent stages or steps, which exceeds the current limit. Please "
            + "upgrade your plan to Team/Enterprise (Paid) or reduce concurrent steps or stages. For more details on "
            + "concurrency limits, visit: "
            + "https://developer.harness.io/docs/platform/pipelines/pipeline-settings/#fixed-pipeline-settings");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionEnterprise() {
    // edition == ENTERPRISE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getMaxConcurrencyRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getStepOrStageMaxConcurrency()).thenReturn(3);
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getMaxParallelismStopRestriction()).thenReturn(3L);

    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 2L);
    assertThat(count).isEqualTo(3L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionNotEnterprise() {
    // edition == ENTERPRISE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getMaxParallelismStopRestriction()).thenReturn(Long.MAX_VALUE);

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getStepOrStageMaxConcurrency()).thenReturn(100);

    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 3L);
    assertThat(count).isEqualTo(100L);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionEnterpriseException() {
    // edition == ENTERPRISE && orchestrationRestrictionConfiguration.isUseRestrictionForEnterprise() == True
    // && childCount > orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getEnterprise() ==
    // True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getMaxParallelismStopRestriction()).thenReturn(3L);

    assertThatThrownBy(() -> pipelineSettingsService.getMaxConcurrencyBasedOnEdition(ACCOUNT_ID, 4L))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "You are attempting to run more than 3 concurrent stages or steps, which exceeds the current limit. To "
            + "learn more about this limitation, please contact Harness Support or visit: "
            + "https://developer.harness.io/docs/platform/pipelines/pipeline-settings/#fixed-pipeline-settings");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetMaxStepConcurrency_returnsLimitWithoutThrowing() {
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);

    when(editionBasedAccountConfigStrategyFactory.getStrategy(ENTERPRISE)).thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getStepOrStageMaxConcurrency(ACCOUNT_ID)).thenReturn(256);

    int result = pipelineSettingsService.getMaxStepConcurrency(ACCOUNT_ID);
    assertThat(result).isEqualTo(256);
    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionForDisabledFF() {
    // edition == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    when(featureFlagService.isEnabled("ACCOUNT_ID", FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name()))
        .thenReturn(false);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    doReturn(3L).when(planExecutionService).countRunningExecutionsForGivenPipelineInAccount(any());
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.FREE)).thenReturn(freeAccountConfigStrategy);
    when(freeAccountConfigStrategy.getPipelineLevelMaxConcurrency("ACCOUNT_ID", null)).thenReturn(Long.MAX_VALUE);

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isFalse();
    assertThat(planExecutionSettingResponse.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionForEnabledFF() {
    // edition == FREE && orchestrationRestrictionConfiguration.isUseRestrictionForFree() == False
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    when(featureFlagService.isEnabled("ACCOUNT_ID", FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name()))
        .thenReturn(true);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig =
        PlanExecutionRestrictionConfig.builder().free(1).team(2).enterprise(3).devops_essentials(4).build();
    doReturn(3L).when(planExecutionService).countRunningExecutionsForGivenPipelineInAccount(any());
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(Edition.FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(ngLicenseHttpClient.getModuleLicenses(anyString())))
        .thenReturn(moduleLicenseDTOS);
    mockRestStatic
        .when(()
                  -> ngSettingsClient.getSetting(
                      NGPipelineSettingsConstant.CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS.getName(), "ACCOUNT_ID", null,
                      null))
        .thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("600").valueType(SettingValueType.STRING).build();
    mockRestStatic
        .when(()
                  -> NGRestUtils.getResponse(ngSettingsClient.getSetting(
                      NGPipelineSettingsConstant.CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS.getName(), "ACCOUNT_ID", null,
                      null)))
        .thenReturn(settingValueResponseDTO);
    doReturn(false).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
    assertThat(planExecutionSettingResponse.isPriorityExecutionLimitReached()).isFalse();
    doReturn(1000L).when(planExecutionService).countRunningExecutionsForGivenPipelineInAccount(any());
    planExecutionSettingResponse = pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isTrue();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
    assertThat(planExecutionSettingResponse.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testConcurrencyWithFFEnabled() throws Exception {
    String accountId = "testAccount";
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value(String.valueOf(10)).valueType(SettingValueType.NUMBER).build();
    when(featureFlagService.isEnabled(accountId, FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name()))
        .thenReturn(true);
    when(ngSettingsClient.getSetting(eq(NGPipelineSettingsConstant.CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS.getName()),
             eq(accountId), any(), any()))
        .thenReturn(settingsRequest);
    when(settingsRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    long result = pipelineSettingsService.getMaxConcurrency(accountId);

    assertEquals(10, result);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testConcurrencyWithFFEnabledAndExceptionOccurs() {
    String accountId = "testAccount";
    when(featureFlagService.isEnabled(accountId, FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name()))
        .thenReturn(true);
    when(ngSettingsClient.getSetting(anyString(), eq(accountId), any(), any()))
        .thenThrow(new RuntimeException("Failed to fetch setting"));

    long result = pipelineSettingsService.getMaxConcurrency(accountId);

    assertEquals(Long.MAX_VALUE, result);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testConcurrencyWithFFDisabledNEditionFreeWithRestrictionEnabled() throws IOException {
    String accountId = "testAccount";
    List<ModuleLicenseDTO> moduleLicenseDTOList =
        new ArrayList<>(Collections.singletonList(CDModuleLicenseDTO.builder().edition(FREE).build()));
    when(ngLicenseHttpClient.getModuleLicenses(accountId)).thenReturn(licenseRequest);
    when(licenseRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(moduleLicenseDTOList)));
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForFree();
    doReturn(PlanExecutionRestrictionConfig.builder().free(5L).build())
        .when(orchestrationRestrictionConfiguration)
        .getPlanExecutionRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.FREE)).thenReturn(freeAccountConfigStrategy);
    when(freeAccountConfigStrategy.getPipelineLevelMaxConcurrency(accountId, null)).thenReturn(5L);

    long result = pipelineSettingsService.getMaxConcurrency(accountId);

    assertEquals(5, result);

    when(planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId)).thenReturn(3L);

    result = pipelineSettingsService.getCurrentExecutionCount(accountId);

    assertEquals(3L, result);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testConcurrencyWithFFDisabledNEditionEnterpriseWithRestrictionEnabled() throws IOException {
    String accountId = "testAccount";
    List<ModuleLicenseDTO> moduleLicenseDTOList =
        new ArrayList<>(Collections.singletonList(CDModuleLicenseDTO.builder().edition(ENTERPRISE).build()));
    when(ngLicenseHttpClient.getModuleLicenses(accountId)).thenReturn(licenseRequest);
    when(licenseRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(moduleLicenseDTOList)));
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForEnterprise();
    doReturn(PlanExecutionRestrictionConfig.builder().enterprise(500L).build())
        .when(orchestrationRestrictionConfiguration)
        .getPlanExecutionRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getPipelineLevelMaxConcurrency(accountId, null)).thenReturn(500L);
    long result = pipelineSettingsService.getMaxConcurrency(accountId);

    assertEquals(500, result);

    when(planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId)).thenReturn(50L);

    result = pipelineSettingsService.getCurrentExecutionCount(accountId);

    assertEquals(50L, result);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testConcurrencyWithFFDisabledNEditionTeamWithRestrictionEnabled() throws IOException {
    String accountId = "testAccount";
    List<ModuleLicenseDTO> moduleLicenseDTOList =
        new ArrayList<>(Collections.singletonList(CDModuleLicenseDTO.builder().edition(TEAM).build()));
    when(ngLicenseHttpClient.getModuleLicenses(accountId)).thenReturn(licenseRequest);
    when(licenseRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(moduleLicenseDTOList)));
    doReturn(true).when(orchestrationRestrictionConfiguration).isUseRestrictionForTeam();
    doReturn(PlanExecutionRestrictionConfig.builder().team(100L).build())
        .when(orchestrationRestrictionConfiguration)
        .getPlanExecutionRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.TEAM)).thenReturn(teamAccountConfigStrategy);
    when(teamAccountConfigStrategy.getPipelineLevelMaxConcurrency(accountId, null)).thenReturn(100L);

    long result = pipelineSettingsService.getMaxConcurrency(accountId);

    assertEquals(100, result);

    when(planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId)).thenReturn(50L);

    result = pipelineSettingsService.getCurrentExecutionCount(accountId);

    assertEquals(50L, result);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetCurrentExecutionCountFeatureFlagEnabled() {
    String accountId = "accountId1";
    String pipelineId = "pipelineId1";
    when(featureFlagService.isEnabled(accountId, FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name()))
        .thenReturn(true);
    when(planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId)).thenReturn(5L);

    long result = pipelineSettingsService.getCurrentExecutionCount(accountId);

    assertEquals(5L, result);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecution_MaxConcurrencyExceeded() throws IOException {
    String accountId = "test-account";
    PriorityType priorityType = PriorityType.HIGH;

    when(featureFlagService.isEnabled(accountId, FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name()))
        .thenReturn(true);

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("3").valueType(SettingValueType.NUMBER).build();

    when(ngSettingsClient.getSetting(
             NGPipelineSettingsConstant.CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS.getName(), accountId, null, null))
        .thenReturn(settingsRequest);
    when(settingsRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    List<ModuleLicenseDTO> moduleLicenseDTOS =
        Collections.singletonList(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    when(ngLicenseHttpClient.getModuleLicenses(accountId)).thenReturn(licenseRequest);
    when(licenseRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(moduleLicenseDTOS)));

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getPipelineLevelMaxConcurrency(eq(accountId), any())).thenReturn(3L);

    when(planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId)).thenReturn(5L);

    PlanExecutionSettingResponse response = pipelineSettingsService.shouldQueuePlanExecution(accountId, priorityType);

    assertThat(response.isShouldQueue()).isTrue();
    assertThat(response.isUseNewFlow()).isTrue();
    assertThat(response.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecution_DefaultConcurrencyCase() throws IOException {
    String accountId = "test-account";
    PriorityType priorityType = PriorityType.HIGH;

    when(featureFlagService.isEnabled(accountId, FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name()))
        .thenReturn(false);

    List<ModuleLicenseDTO> moduleLicenseDTOS =
        Collections.singletonList(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    when(ngLicenseHttpClient.getModuleLicenses(accountId)).thenReturn(licenseRequest);
    when(licenseRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(moduleLicenseDTOS)));

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getPipelineLevelMaxConcurrency(eq(accountId), any()))
        .thenReturn(Long.MAX_VALUE);

    when(planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId)).thenReturn(-1L);

    PlanExecutionSettingResponse response = pipelineSettingsService.shouldQueuePlanExecution(accountId, priorityType);

    assertThat(response.isShouldQueue()).isFalse();
    assertThat(response.isUseNewFlow()).isFalse();
    assertThat(response.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecution_NoPriorityProjects() throws IOException {
    String accountId = "test-account";
    PriorityType priorityType = PriorityType.HIGH;

    when(featureFlagService.isEnabled(accountId, FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name()))
        .thenReturn(true);

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("3").valueType(SettingValueType.NUMBER).build();

    when(ngSettingsClient.getSetting(
             NGPipelineSettingsConstant.CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS.getName(), accountId, null, null))
        .thenReturn(settingsRequest);
    when(settingsRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    List<ModuleLicenseDTO> moduleLicenseDTOS =
        Collections.singletonList(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    when(ngLicenseHttpClient.getModuleLicenses(accountId)).thenReturn(licenseRequest);
    when(licenseRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(moduleLicenseDTOS)));

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getPipelineLevelMaxConcurrency(eq(accountId), any())).thenReturn(3L);

    when(planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId)).thenReturn(2L);

    // Mock the settings request for priority preferences
    SettingValueResponseDTO prioritySettingsDTO = SettingValueResponseDTO.builder()
                                                      .value("{}") // Empty priority settings
                                                      .valueType(SettingValueType.STRING)
                                                      .build();
    when(ngSettingsClient.getSetting(
             NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PREFERENCES.getName(), accountId, null, null))
        .thenReturn(settingsRequest);
    when(settingsRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(prioritySettingsDTO)));

    PlanExecutionSettingResponse response = pipelineSettingsService.shouldQueuePlanExecution(accountId, priorityType);

    assertThat(response.isShouldQueue()).isFalse();
    assertThat(response.isUseNewFlow()).isTrue();
    assertThat(response.isPriorityExecutionLimitReached()).isFalse();
    verify(planExecutionService).countRunningExecutionsForGivenPipelineInAccount(accountId);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecution_MatchingHighPriorityExecution() throws IOException {
    String accountId = "test-account";
    PriorityType priorityType = PriorityType.HIGH;

    when(featureFlagService.isEnabled(accountId, FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name()))
        .thenReturn(true);
    when(featureFlagService.isEnabled(accountId, FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY.name()))
        .thenReturn(true);

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("5").valueType(SettingValueType.NUMBER).build();

    when(ngSettingsClient.getSetting(
             NGPipelineSettingsConstant.CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS.getName(), accountId, null, null))
        .thenReturn(settingsRequest);
    when(settingsRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    List<ModuleLicenseDTO> moduleLicenseDTOS =
        Collections.singletonList(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    when(ngLicenseHttpClient.getModuleLicenses(accountId)).thenReturn(licenseRequest);
    when(licenseRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(moduleLicenseDTOS)));

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getPipelineLevelMaxConcurrency(eq(accountId), any()))
        .thenReturn(5L); // maxAccountConcurrency = 5

    // Mock the settings request for priority preferences
    List<SettingResponseDTO> prioritySettings = new ArrayList<>();

    // Add priority type setting
    SettingResponseDTO priorityTypeSetting =
        SettingResponseDTO.builder()
            .setting(SettingDTO.builder()
                         .identifier(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_TYPE.getName())
                         .value("High")
                         .build())
            .build();
    prioritySettings.add(priorityTypeSetting);

    // Add priority limit setting
    SettingResponseDTO priorityLimitSetting =
        SettingResponseDTO.builder()
            .setting(SettingDTO.builder()
                         .identifier(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_LIMIT.getName())
                         .value("4")
                         .build())
            .build();
    prioritySettings.add(priorityLimitSetting);

    // Add priority projects setting
    SettingResponseDTO priorityProjectsSetting =
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder()
                    .identifier(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_PROJECTS.getName())
                    .value("[\n  {\n    \"fqn\": \"default/test\",\n    \"uniqueId\": \"12345\"\n  }\n]")
                    .build())
            .build();
    prioritySettings.add(priorityProjectsSetting);

    Call<ResponseDTO<List<SettingResponseDTO>>> listSettingsRequest = mock(Call.class);
    when(ngSettingsClient.listSettings(eq(accountId), eq(null), eq(null), eq(SettingCategory.PMS),
             eq(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PREFERENCES.getName())))
        .thenReturn(listSettingsRequest);
    when(listSettingsRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(prioritySettings)));

    // Set total executions to 5 (4 high priority, 1 low priority)
    when(planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId)).thenReturn(4L);

    // Set high priority executions to 4
    // Since this is a matching high priority execution and we're at the limit (4) but low has limit, it should not
    // queue
    when(planExecutionService.countRunningExecutionsForGivenPriorityInAccount(accountId, priorityType)).thenReturn(4L);

    PlanExecutionSettingResponse response = pipelineSettingsService.shouldQueuePlanExecution(accountId, priorityType);

    // Should not queue because:
    // 1. High priority is defined (isHighPriorityDefined = true)
    // 2. Current execution is high priority (matching)
    // 3. highPriorityAllowance = Math.min(priorityExecutionsLimit, maxAccountConcurrency) = Math.min(4, 5) = 4
    assertThat(response.isShouldQueue()).isFalse();
    assertThat(response.isUseNewFlow()).isTrue();
    assertThat(response.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecution_MatchingLowPriorityExecution() throws IOException {
    String accountId = "test-account";
    PriorityType priorityType = PriorityType.LOW;

    when(featureFlagService.isEnabled(accountId, FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name()))
        .thenReturn(true);
    when(featureFlagService.isEnabled(accountId, FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY.name()))
        .thenReturn(true);

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("5").valueType(SettingValueType.NUMBER).build();

    when(ngSettingsClient.getSetting(
             NGPipelineSettingsConstant.CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS.getName(), accountId, null, null))
        .thenReturn(settingsRequest);
    when(settingsRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    List<ModuleLicenseDTO> moduleLicenseDTOS =
        Collections.singletonList(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    when(ngLicenseHttpClient.getModuleLicenses(accountId)).thenReturn(licenseRequest);
    when(licenseRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(moduleLicenseDTOS)));

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getPipelineLevelMaxConcurrency(eq(accountId), any()))
        .thenReturn(5L); // maxAccountConcurrency = 5

    // Mock the settings request for priority preferences
    List<SettingResponseDTO> prioritySettings = new ArrayList<>();

    // Add priority type setting
    SettingResponseDTO priorityTypeSetting =
        SettingResponseDTO.builder()
            .setting(SettingDTO.builder()
                         .identifier(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_TYPE.getName())
                         .value("Low")
                         .build())
            .build();
    prioritySettings.add(priorityTypeSetting);

    // Add priority limit setting
    SettingResponseDTO priorityLimitSetting =
        SettingResponseDTO.builder()
            .setting(SettingDTO.builder()
                         .identifier(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_LIMIT.getName())
                         .value("3")
                         .build())
            .build();
    prioritySettings.add(priorityLimitSetting);

    // Add priority projects setting
    SettingResponseDTO priorityProjectsSetting =
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder()
                    .identifier(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_PROJECTS.getName())
                    .value("[\n  {\n    \"fqn\": \"default/test\",\n    \"uniqueId\": \"12345\"\n  }\n]")
                    .build())
            .build();
    prioritySettings.add(priorityProjectsSetting);

    Call<ResponseDTO<List<SettingResponseDTO>>> listSettingsRequest = mock(Call.class);
    when(ngSettingsClient.listSettings(eq(accountId), eq(null), eq(null), eq(SettingCategory.PMS),
             eq(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PREFERENCES.getName())))
        .thenReturn(listSettingsRequest);
    when(listSettingsRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(prioritySettings)));

    // Set total executions to 4 (3 low priority, 1 high priority)
    when(planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId)).thenReturn(4L);

    // Set low priority executions to 3
    // Since this is a matching low priority execution and we're at the limit (3), it should queue
    when(planExecutionService.countRunningExecutionsForGivenPriorityInAccount(accountId, priorityType)).thenReturn(3L);

    PlanExecutionSettingResponse response = pipelineSettingsService.shouldQueuePlanExecution(accountId, priorityType);

    // Should queue because:
    // 1. Low priority is defined (isHighPriorityDefined = false)
    // 2. Current execution is low priority (matching)
    // 3. lowPriorityAllowance = Math.min(priorityExecutionsLimit, maxAccountConcurrency) = Math.min(3, 5) = 3
    // 4. currentExecutionsForPriority = 3 >= lowPriorityAllowance (3)
    // 5. shouldQueue = currentExecutionsForPriority >= lowPriorityAllowance = true
    assertThat(response.isShouldQueue()).isTrue();
    assertThat(response.isUseNewFlow()).isTrue();
    assertThat(response.isPriorityExecutionLimitReached()).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecution_NonMatchingHighPriorityExecution() throws IOException {
    String accountId = "test-account";
    PriorityType priorityType = PriorityType.HIGH;

    when(featureFlagService.isEnabled(accountId, FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name()))
        .thenReturn(true);
    when(featureFlagService.isEnabled(accountId, FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY.name()))
        .thenReturn(true);

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("5").valueType(SettingValueType.NUMBER).build();

    when(ngSettingsClient.getSetting(
             NGPipelineSettingsConstant.CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS.getName(), accountId, null, null))
        .thenReturn(settingsRequest);
    when(settingsRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    List<ModuleLicenseDTO> moduleLicenseDTOS =
        Collections.singletonList(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    when(ngLicenseHttpClient.getModuleLicenses(accountId)).thenReturn(licenseRequest);
    when(licenseRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(moduleLicenseDTOS)));

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getPipelineLevelMaxConcurrency(eq(accountId), any()))
        .thenReturn(5L); // maxAccountConcurrency = 5

    // Mock the settings request for priority preferences
    List<SettingResponseDTO> prioritySettings = new ArrayList<>();

    // Add priority type setting
    SettingResponseDTO priorityTypeSetting =
        SettingResponseDTO.builder()
            .setting(SettingDTO.builder()
                         .identifier(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_TYPE.getName())
                         .value("Low")
                         .build())
            .build();
    prioritySettings.add(priorityTypeSetting);

    // Add priority limit setting
    SettingResponseDTO priorityLimitSetting =
        SettingResponseDTO.builder()
            .setting(SettingDTO.builder()
                         .identifier(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_LIMIT.getName())
                         .value("3")
                         .build())
            .build();
    prioritySettings.add(priorityLimitSetting);

    // Add priority projects setting
    SettingResponseDTO priorityProjectsSetting =
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder()
                    .identifier(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_PROJECTS.getName())
                    .value("[\n  {\n    \"fqn\": \"default/test\",\n    \"uniqueId\": \"12345\"\n  }\n]")
                    .build())
            .build();
    prioritySettings.add(priorityProjectsSetting);

    Call<ResponseDTO<List<SettingResponseDTO>>> listSettingsRequest = mock(Call.class);
    when(ngSettingsClient.listSettings(eq(accountId), eq(null), eq(null), eq(SettingCategory.PMS),
             eq(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PREFERENCES.getName())))
        .thenReturn(listSettingsRequest);
    when(listSettingsRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(prioritySettings)));

    // Set total executions to 3 (all low priority)
    when(planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId)).thenReturn(3L);

    // Set low priority executions to 3
    // This means otherPriorityExecutions = 0, which is <= highPriorityAllowance (5-3=2)
    when(planExecutionService.countRunningExecutionsForGivenPriorityInAccount(accountId, priorityType)).thenReturn(0L);

    PlanExecutionSettingResponse response = pipelineSettingsService.shouldQueuePlanExecution(accountId, priorityType);

    // Should not queue because:
    // 1. Low priority is defined (isHighPriorityDefined = false)
    // 2. Current execution is high priority (non-matching)
    // 3. lowPriorityAllowance = Math.min(priorityExecutionsLimit, maxAccountConcurrency) = Math.min(3, 5) = 3
    // 4. highPriorityAllowance = Math.max(maxAccountConcurrency - lowPriorityAllowance, 0) = Math.max(5 - 3, 0) = 2
    // 5. currentExecutionsForPriority = 0 < lowPriorityAllowance (3)
    // 6. otherPriorityExecutions = 3 > highPriorityAllowance (2)
    // 7. shouldQueue = currentExecutionsForPriority >= lowPriorityAllowance && highPriorityAllowance <=
    // otherPriorityExecutions = false
    assertThat(response.isShouldQueue()).isFalse();
    assertThat(response.isUseNewFlow()).isTrue();
    assertThat(response.isPriorityExecutionLimitReached()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecution_NonMatchingLowPriorityExecution() throws IOException {
    String accountId = "test-account";
    PriorityType priorityType = PriorityType.LOW;

    when(featureFlagService.isEnabled(accountId, FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name()))
        .thenReturn(true);

    when(featureFlagService.isEnabled(accountId, FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY.name()))
        .thenReturn(true);

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("5").valueType(SettingValueType.NUMBER).build();

    when(ngSettingsClient.getSetting(
             NGPipelineSettingsConstant.CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS.getName(), accountId, null, null))
        .thenReturn(settingsRequest);
    when(settingsRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));

    List<ModuleLicenseDTO> moduleLicenseDTOS =
        Collections.singletonList(CDModuleLicenseDTO.builder().edition(Edition.ENTERPRISE).build());
    when(ngLicenseHttpClient.getModuleLicenses(accountId)).thenReturn(licenseRequest);
    when(licenseRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(moduleLicenseDTOS)));

    when(editionBasedAccountConfigStrategyFactory.getStrategy(Edition.ENTERPRISE))
        .thenReturn(enterpriseAccountConfigStrategy);
    when(enterpriseAccountConfigStrategy.getPipelineLevelMaxConcurrency(eq(accountId), any()))
        .thenReturn(5L); // maxAccountConcurrency = 5

    // Mock the settings request for priority preferences
    List<SettingResponseDTO> prioritySettings = new ArrayList<>();

    // Add priority type setting
    SettingResponseDTO priorityTypeSetting =
        SettingResponseDTO.builder()
            .setting(SettingDTO.builder()
                         .identifier(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_TYPE.getName())
                         .value("High")
                         .build())
            .build();
    prioritySettings.add(priorityTypeSetting);

    // Add priority limit setting
    SettingResponseDTO priorityLimitSetting =
        SettingResponseDTO.builder()
            .setting(SettingDTO.builder()
                         .identifier(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_LIMIT.getName())
                         .value("4")
                         .build())
            .build();
    prioritySettings.add(priorityLimitSetting);

    // Add priority projects setting
    SettingResponseDTO priorityProjectsSetting =
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder()
                    .identifier(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_PROJECTS.getName())
                    .value("[\n  {\n    \"fqn\": \"default/test\",\n    \"uniqueId\": \"12345\"\n  }\n]")
                    .build())
            .build();
    prioritySettings.add(priorityProjectsSetting);

    Call<ResponseDTO<List<SettingResponseDTO>>> listSettingsRequest = mock(Call.class);
    when(ngSettingsClient.listSettings(eq(accountId), eq(null), eq(null), eq(SettingCategory.PMS),
             eq(NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PREFERENCES.getName())))
        .thenReturn(listSettingsRequest);
    when(listSettingsRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(prioritySettings)));

    // Set total executions to 4 (all high priority)
    when(planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId)).thenReturn(4L);

    // Set High priority executions to 3
    // This means otherPriorityExecutions = 1, which is >= lowPriorityAllowance (5-4=1)
    when(planExecutionService.countRunningExecutionsForGivenPriorityInAccount(accountId, priorityType)).thenReturn(3L);

    PlanExecutionSettingResponse response = pipelineSettingsService.shouldQueuePlanExecution(accountId, priorityType);

    // Should queue because:
    // 1. High priority is defined (isHighPriorityDefined = true because definedPriority equals "HIGH")
    // 2. Current execution is low priority (non-matching)
    // 3. lowPriorityAllowance = Math.max(maxAccountConcurrency - priorityExecutionsLimit, 0) = Math.max(5 - 4, 0) = 1
    // 4. otherPriorityExecutions = currentExecutionRunningInAccount - currentExecutionsForPriority = 4 - 0 = 4
    // 5. shouldQueue = otherPriorityExecutions >= lowPriorityAllowance = 4 >= 1 = true
    assertThat(response.isShouldQueue()).isTrue();
    assertThat(response.isUseNewFlow()).isTrue();
    assertThat(response.isPriorityExecutionLimitReached()).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeterminePriorityTypeWithFeatureFlagDisabled() {
    PriorityType result = pipelineSettingsService.getPriorityTypeOfCurrentExecution(accountId, orgId, projectId, false);
    assertThat(result).isEqualTo(PriorityType.NORMAL);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeterminePriorityTypeWithEmptyPriorityList() {
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<SettingResponseDTO> concurrencySettings = new ArrayList<>();
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder().identifier("pipeline_execution_concurrency_priority_limit").value("200").build())
            .build());
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder().identifier("pipeline_execution_concurrency_priority_type").value("High").build())
            .build());
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder().identifier("pipeline_execution_concurrency_priority_projects").value("[]").build())
            .build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(concurrencySettings);

    PriorityType result = pipelineSettingsService.getPriorityTypeOfCurrentExecution(accountId, orgId, projectId, true);
    assertThat(result).isEqualTo(PriorityType.NORMAL);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeterminePriorityTypeWithHighPriorityProject() {
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<SettingResponseDTO> concurrencySettings = new ArrayList<>();
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder().identifier("pipeline_execution_concurrency_priority_limit").value("200").build())
            .build());
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder().identifier("pipeline_execution_concurrency_priority_type").value("High").build())
            .build());
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(SettingDTO.builder()
                         .identifier("pipeline_execution_concurrency_priority_projects")
                         .value("[\n  {\n    \"fqn\": \"orgId/projectId\",\n    \"uniqueId\": \"12345\"\n  }\n]")
                         .build())
            .build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(concurrencySettings);
    when(featureFlagService.isEnabled(accountId, FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY.name()))
        .thenReturn(true);
    PriorityType result = pipelineSettingsService.getPriorityTypeOfCurrentExecution(accountId, orgId, projectId, true);
    assertThat(result).isEqualTo(PriorityType.HIGH);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeterminePriorityTypeWithLowPriorityProject() {
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<SettingResponseDTO> concurrencySettings = new ArrayList<>();
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder().identifier("pipeline_execution_concurrency_priority_limit").value("200").build())
            .build());
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(
                SettingDTO.builder().identifier("pipeline_execution_concurrency_priority_type").value("Low").build())
            .build());
    concurrencySettings.add(
        SettingResponseDTO.builder()
            .setting(SettingDTO.builder()
                         .identifier("pipeline_execution_concurrency_priority_projects")
                         .value("[\n  {\n    \"fqn\": \"orgId/projectId\",\n    \"uniqueId\": \"12345\"\n  }\n]")
                         .build())
            .build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(concurrencySettings);
    when(featureFlagService.isEnabled(accountId, FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY.name()))
        .thenReturn(true);

    PriorityType result = pipelineSettingsService.getPriorityTypeOfCurrentExecution(accountId, orgId, projectId, true);
    assertThat(result).isEqualTo(PriorityType.LOW);
  }

  // ==================== ESSENTIALS Edition Tests ====================

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetEditionWithEssentials() throws ExecutionException {
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();

    // Case 1: Only ESSENTIALS edition
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ESSENTIALS).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    Edition edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(ESSENTIALS);

    // Case 2: ESSENTIALS + FREE → should return ESSENTIALS (higher priority)
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(FREE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(ESSENTIALS);

    // Case 3: ESSENTIALS + DEVOPS_ESSENTIALS → should return ESSENTIALS (higher priority)
    moduleLicenseDTOS.clear();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ESSENTIALS).build());
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(DEVOPS_ESSENTIALS).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(ESSENTIALS);

    // Case 4: ESSENTIALS + TEAM → should return ESSENTIALS (higher priority)
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(TEAM).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(ESSENTIALS);

    // Case 5: ESSENTIALS + ENTERPRISE → should return ENTERPRISE (highest priority)
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ENTERPRISE).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    edition = pipelineSettingsService.getEdition("ACCOUNT_ID");
    assertThat(edition).isEqualTo(ENTERPRISE);

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionEssentials() {
    // edition == ESSENTIALS && orchestrationRestrictionConfiguration.isUseRestrictionForEssentials() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = PlanExecutionRestrictionConfig.builder()
                                                                        .free(1)
                                                                        .team(2)
                                                                        .enterprise(3)
                                                                        .devops_essentials(4)
                                                                        .essentials(5)
                                                                        .build();
    doReturn(0L).when(planExecutionService).countRunningExecutionsForGivenPipelineInAccount("ACCOUNT_ID");
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ESSENTIALS).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getPlanExecutionRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(ESSENTIALS)).thenReturn(essentialsAccountConfigStrategy);
    when(essentialsAccountConfigStrategy.getPipelineLevelMaxConcurrency("ACCOUNT_ID", null)).thenReturn(5L);

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isFalse();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
    assertThat(planExecutionSettingResponse.isPriorityExecutionLimitReached()).isFalse();

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testShouldQueuePlanExecutionEssentialsLimitReached() {
    // edition == ESSENTIALS && running executions >= max concurrency
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = PlanExecutionRestrictionConfig.builder()
                                                                        .free(1)
                                                                        .team(2)
                                                                        .enterprise(3)
                                                                        .devops_essentials(4)
                                                                        .essentials(5)
                                                                        .build();
    doReturn(100L).when(planExecutionService).countRunningExecutionsForGivenPipelineInAccount("ACCOUNT_ID");
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ESSENTIALS).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    when(editionBasedAccountConfigStrategyFactory.getStrategy(ESSENTIALS)).thenReturn(essentialsAccountConfigStrategy);
    when(essentialsAccountConfigStrategy.getPipelineLevelMaxConcurrency("ACCOUNT_ID", null)).thenReturn(5L);

    PlanExecutionSettingResponse planExecutionSettingResponse =
        pipelineSettingsService.shouldQueuePlanExecution("ACCOUNT_ID");
    assertThat(planExecutionSettingResponse.isShouldQueue()).isTrue();
    assertThat(planExecutionSettingResponse.isUseNewFlow()).isTrue();
    assertThat(planExecutionSettingResponse.isPriorityExecutionLimitReached()).isFalse();

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionEssentials() {
    // edition == ESSENTIALS && orchestrationRestrictionConfiguration.isUseRestrictionForEssentials() == True
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = PlanExecutionRestrictionConfig.builder()
                                                                        .free(1)
                                                                        .team(2)
                                                                        .enterprise(3)
                                                                        .devops_essentials(4)
                                                                        .essentials(5)
                                                                        .build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ESSENTIALS).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    doReturn(planExecutionRestrictionConfig).when(orchestrationRestrictionConfiguration).getMaxConcurrencyRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(ESSENTIALS)).thenReturn(essentialsAccountConfigStrategy);
    when(essentialsAccountConfigStrategy.getStepOrStageMaxConcurrency()).thenReturn(5);
    when(editionBasedAccountConfigStrategyFactory.getStrategy(ESSENTIALS)).thenReturn(essentialsAccountConfigStrategy);
    when(essentialsAccountConfigStrategy.getMaxParallelismStopRestriction()).thenReturn(10L);

    when(essentialsAccountConfigStrategy.getStepOrStageMaxConcurrency(ACCOUNT_ID)).thenReturn(8);
    int count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 6);
    assertThat(count).isEqualTo(8);

    // to test: return config value. config value > child count, then it should return config value
    when(essentialsAccountConfigStrategy.getStepOrStageMaxConcurrency()).thenReturn(10);
    count = pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 4);
    assertThat(count).isEqualTo(10);

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyBasedOnEditionEssentialsException() {
    // edition == ESSENTIALS && childCount > maxParallelismStopRestriction → should throw exception
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = PlanExecutionRestrictionConfig.builder()
                                                                        .free(1)
                                                                        .team(2)
                                                                        .enterprise(3)
                                                                        .devops_essentials(4)
                                                                        .essentials(5)
                                                                        .build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ESSENTIALS).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getTotalParallelismStopRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(ESSENTIALS)).thenReturn(essentialsAccountConfigStrategy);
    when(essentialsAccountConfigStrategy.getMaxParallelismStopRestriction()).thenReturn(2L);

    assertThatThrownBy(() -> pipelineSettingsService.getMaxConcurrencyBasedOnEdition("ACCOUNT_ID", 4L))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("You are attempting to run more than 2 concurrent stages or steps");

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetMaxPipelineCreationCountEssentials() {
    // edition == ESSENTIALS
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    PlanExecutionRestrictionConfig planExecutionRestrictionConfig = PlanExecutionRestrictionConfig.builder()
                                                                        .free(1)
                                                                        .team(2)
                                                                        .enterprise(3)
                                                                        .devops_essentials(4)
                                                                        .essentials(100)
                                                                        .build();
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ESSENTIALS).build());
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(moduleLicenseDTOS);
    doReturn(planExecutionRestrictionConfig)
        .when(orchestrationRestrictionConfiguration)
        .getPipelineCreationRestriction();
    when(editionBasedAccountConfigStrategyFactory.getStrategy(ESSENTIALS)).thenReturn(essentialsAccountConfigStrategy);
    when(essentialsAccountConfigStrategy.getMaxPipelineCreationLimit("ACCOUNT_ID")).thenReturn(100);

    long count = pipelineSettingsService.getMaxPipelineCreationCount("ACCOUNT_ID");
    assertThat(count).isEqualTo(100L);

    mockRestStatic.close();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetMaxConcurrencyEssentials() throws IOException {
    // edition == ESSENTIALS with restriction enabled
    String testAccountId = "testAccountEssentials";
    List<ModuleLicenseDTO> moduleLicenseDTOS = new ArrayList<>();
    moduleLicenseDTOS.add(CDModuleLicenseDTO.builder().edition(ESSENTIALS).build());

    when(ngLicenseHttpClient.getModuleLicenses(testAccountId)).thenReturn(licenseRequest);
    when(licenseRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(moduleLicenseDTOS)));

    when(editionBasedAccountConfigStrategyFactory.getStrategy(ESSENTIALS)).thenReturn(essentialsAccountConfigStrategy);
    when(essentialsAccountConfigStrategy.getPipelineLevelMaxConcurrency(eq(testAccountId), any())).thenReturn(500L);

    long result = pipelineSettingsService.getMaxConcurrency(testAccountId);
    assertEquals(500, result);
  }

  // --- getMaxLeafStepConcurrency ------------------------------------------------------------

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetMaxLeafStepConcurrency_mongoOverridePresent_returnsOverride() throws IllegalAccessException {
    FieldUtils.writeField(pipelineSettingsService, "defaultMaxLeafStepConcurrency", 5000, true);
    when(pipelineRetentionService.getMaxLeafStepConcurrency(ACCOUNT_ID)).thenReturn(Optional.of(123));

    int result = pipelineSettingsService.getMaxLeafStepConcurrency(ACCOUNT_ID);
    assertThat(result).isEqualTo(123);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetMaxLeafStepConcurrency_noOverride_returnsConfigDefault() throws IllegalAccessException {
    FieldUtils.writeField(pipelineSettingsService, "defaultMaxLeafStepConcurrency", 5000, true);
    when(pipelineRetentionService.getMaxLeafStepConcurrency(ACCOUNT_ID)).thenReturn(Optional.empty());

    int result = pipelineSettingsService.getMaxLeafStepConcurrency(ACCOUNT_ID);
    assertThat(result).isEqualTo(5000);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetMaxLeafStepConcurrency_retentionServiceThrows_returnsConfigDefault()
      throws IllegalAccessException {
    FieldUtils.writeField(pipelineSettingsService, "defaultMaxLeafStepConcurrency", 5000, true);
    when(pipelineRetentionService.getMaxLeafStepConcurrency(ACCOUNT_ID)).thenThrow(new RuntimeException("boom"));

    int result = pipelineSettingsService.getMaxLeafStepConcurrency(ACCOUNT_ID);
    assertThat(result).isEqualTo(5000);
  }
}

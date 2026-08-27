/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.PipelineServiceTestBase;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.exception.HintException;
import io.harness.filter.service.FilterService;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.pms.filter.creation.service.FilterCreatorMergeService;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.governance.ExpansionRequestsExtractor;
import io.harness.pms.governance.JsonExpander;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

public class PipelineServiceFormCriteriaTest extends PipelineServiceTestBase {
  @Mock private PMSPipelineServiceHelper pmsPipelineServiceHelperMocked;
  @Mock private OutboxServiceImpl outboxService;
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private PipelineMetadataService pipelineMetadataService;

  @Mock private PipelineSettingsService pipelineSettingsService;
  @Mock GitXSettingsHelper gitXSettingsHelper;
  @InjectMocks private PMSPipelineServiceImpl pmsPipelineService;
  @Inject private PMSPipelineRepository pmsPipelineRepository;

  @InjectMocks PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock FilterService filterService;
  @Mock FilterCreatorMergeService filterCreatorMergeService;
  @Mock private PmsGitSyncHelper gitSyncHelper;
  @Mock private ExpansionRequestsExtractor expansionRequestsExtractor;
  @Mock private JsonExpander jsonExpander;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock private ProjectClient projectClient;

  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock private PipelineRetentionService pipelineRetentionService;
  @Mock private PipelineOpaStatusHandler pipelineOpaStatusHandler;

  private final String accountId = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "myPipeline";

  PipelineEntity pipelineEntity;
  PipelineEntity updatedPipelineEntity;
  OutboxEvent outboxEvent = OutboxEvent.builder().build();

  @Before
  public void setup() {
    String yaml = "yaml: pipeline";
    pipelineEntity = PipelineEntity.builder()
                         .accountId(accountId)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .identifier(PIPELINE_IDENTIFIER)
                         .name(PIPELINE_IDENTIFIER)
                         .parentUniqueId(PROJ_IDENTIFIER)
                         .yaml(yaml)
                         .harnessVersion(HarnessYamlVersion.V0)
                         .stageCount(1)
                         .stageName("qaStage")
                         .version(null)
                         .deleted(false)
                         .createdAt(System.currentTimeMillis())
                         .lastUpdatedAt(System.currentTimeMillis())
                         .build();

    updatedPipelineEntity = pipelineEntity.withStageCount(1).withStageNames(Collections.singletonList("qaStage"));
    doReturn(Optional.empty()).when(scopeResolutionHelper).getScopeInfoOptional(anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testFormCriteriaWithActualData() throws IOException {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    on(pmsPipelineService).set("pmsFeatureFlagHelper", pmsFeatureFlagHelper);
    doReturn(outboxEvent).when(outboxService).save(any());
    doReturn(updatedPipelineEntity)
        .when(pmsPipelineServiceHelperMocked)
        .updatePipelineInfo(eq(pipelineEntity), eq(HarnessYamlVersion.V0), any(), anyBoolean());
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pmsPipelineServiceHelperMocked)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    doReturn(TemplateMergeResponseDTO.builder().build())
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(any(), anyBoolean(), anyBoolean());
    doNothing().when(gitXSettingsHelper).enforceGitExperienceIfApplicable(any(), any(), any());

    // Mock telemetry to avoid side effects
    doNothing().when(pmsPipelineServiceHelperMocked).sendPipelineSaveTelemetryEvent(any(), any(), any(), anyBoolean());
    doNothing()
        .when(pmsPipelineServiceHelperMocked)
        .sendTemplatesUsedInPipelinesTelemetryEvent(any(), any(), any(), anyBoolean());

    // Inject the mocked helper
    on(pmsPipelineService).set("pmsPipelineServiceHelper", pmsPipelineServiceHelperMocked);

    // Mock NGRestUtils.getResponse to return a Map for scope resolution calls
    // This is the KEY fix - the code expects a Map<String, Optional<ScopeInfo>>, not a Call object
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenAnswer(invocation -> {
      // For scope-related calls, return Map<String, Optional<ScopeInfo>>
      java.util.Map<String, Optional<io.harness.beans.ScopeInfo>> scopeInfoMap = new java.util.HashMap<>();
      io.harness.beans.ScopeInfo scopeInfo = io.harness.beans.ScopeInfo.builder()
                                                 .accountIdentifier(accountId)
                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                 .scopeType(io.harness.beans.ScopeLevel.PROJECT)
                                                 .uniqueId("test-unique-id")
                                                 .build();
      scopeInfoMap.put(PROJ_IDENTIFIER, Optional.of(scopeInfo));
      // checkForMetadataAndSaveIfAbsent resolves the scope by parentUniqueId ("test-unique-id"), so make it
      // resolvable under that key as well.
      scopeInfoMap.put("test-unique-id", Optional.of(scopeInfo));
      return scopeInfoMap;
    });

    io.harness.beans.ScopeInfo scopeInfo = io.harness.beans.ScopeInfo.builder()
                                               .accountIdentifier(accountId)
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .scopeType(io.harness.beans.ScopeLevel.PROJECT)
                                               .uniqueId("test-unique-id")
                                               .build();
    pmsPipelineService.validateAndCreatePipeline(pipelineEntity, true, scopeInfo, true);

    Criteria criteria = pmsPipelineServiceHelper.formCriteria(
        accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "cd", "my", null, false);

    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.createdAt));

    List<PipelineEntity> list =
        pmsPipelineService.list(criteria, pageable, accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false)
            .getContent();

    assertThat(list.size()).isEqualTo(1);
    PipelineEntity queriedPipelineEntity = list.get(0);
    assertThat(queriedPipelineEntity.getAccountId()).isEqualTo(updatedPipelineEntity.getAccountId());
    assertThat(queriedPipelineEntity.getOrgIdentifier()).isEqualTo(updatedPipelineEntity.getOrgIdentifier());
    assertThat(queriedPipelineEntity.getIdentifier()).isEqualTo(updatedPipelineEntity.getIdentifier());
    assertThat(queriedPipelineEntity.getName()).isEqualTo(updatedPipelineEntity.getName());
    assertThat(queriedPipelineEntity.getYaml()).isEqualTo(updatedPipelineEntity.getYaml());
    assertThat(queriedPipelineEntity.getStageCount()).isEqualTo(updatedPipelineEntity.getStageCount());
    assertThat(queriedPipelineEntity.getStageNames()).isEqualTo(updatedPipelineEntity.getStageNames());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testFormCriteriaInvalidModuleType() throws IOException {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    on(pmsPipelineService).set("pmsFeatureFlagHelper", pmsFeatureFlagHelper);
    doReturn(outboxEvent).when(outboxService).save(any());
    doReturn(updatedPipelineEntity)
        .when(pmsPipelineServiceHelperMocked)
        .updatePipelineInfo(eq(pipelineEntity), eq(HarnessYamlVersion.V0), any(), anyBoolean());
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pmsPipelineServiceHelperMocked)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    doReturn(TemplateMergeResponseDTO.builder().build())
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(any(), any(), anyBoolean(), anyBoolean());

    // This test only validates that formCriteria throws HintException for invalid module type
    // The validation happens in formCriteria itself, no pipeline creation needed
    final Throwable ex = catchThrowable(()
                                            -> pmsPipelineServiceHelper.formCriteria(accountId, ORG_IDENTIFIER,
                                                PROJ_IDENTIFIER, null, null, false, "cn", "my", null, false));
    assertThat(ex).isInstanceOf(HintException.class);
    assertThat(ex.getMessage()).isEqualTo("Invalid module type [cn]");
  }
}

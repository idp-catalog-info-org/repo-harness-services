/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.account.services.AccountClient;
import io.harness.account.settings.service.impl.NoopPipelineSettingServiceImpl;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.environment.remote.EnvironmentResourceClient;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.helper.PipelineTemplateReferenceHelper;
import io.harness.pms.pipeline.service.intfc.PipelineCRUDResult;
import io.harness.pms.pipeline.service.intfc.PipelineGetResult;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.helper.PipelineAsyncValidationHelper;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.pipeline.validation.service.intfc.PipelineValidationService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.rule.Owner;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.unified.service.NgServiceResourceClient;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.validator.InvalidYamlException;

import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class PMSPipelineServiceImplSimplifiedGitExpTest extends CategoryTest {
  PMSPipelineServiceImpl pipelineService;
  @Mock private PMSPipelineServiceHelper pipelineServiceHelper;
  @Mock private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Mock private PMSPipelineRepository pipelineRepository;
  @Mock private EntitySetupUsageClient entitySetupUsageClient;
  @Mock private PipelineAsyncValidationService pipelineAsyncValidationService;
  @Mock private PipelineValidationService pipelineValidationService;
  @Mock private ProjectClient projectClient;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private GitXSettingsHelper gitXSettingsHelper;
  @Mock private OrganizationClient organizationClient;
  @Mock private AccountClient accountClient;
  @Mock NGSettingsClient settingsClient;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock PMSYamlSchemaService yamlSchemaService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock PipelineRetentionService pipelineRetentionService;
  @Mock TemplateResourceClient templateResourceClient;
  @Mock NgServiceResourceClient ngServiceResourceClient;
  @Mock EnvironmentResourceClient environmentResourceClient;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock private PipelineEnforcementService pipelineEnforcementService;
  @Mock PipelineTemplateReferenceHelper pipelineTemplateReferenceHelper;
  @Mock PipelineOpaStatusHandler pipelineOpaStatusHandler;

  String accountIdentifier = "acc";
  String orgIdentifier = "org";
  String projectIdentifier = "proj";
  String pipelineId = "pipeline";
  String pipelineYaml = "pipeline: yaml";
  ScopeInfo scopeInfo = ScopeInfo.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .uniqueId("projUniqueId")
                            .build();
  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    pipelineService =
        new PMSPipelineServiceImpl(pipelineRepository, null, pipelineServiceHelper, pmsPipelineTemplateHelper,
            pipelineTemplateReferenceHelper, null, null, gitSyncSdkService, null, pmsFeatureFlagHelper,
            new NoopPipelineSettingServiceImpl(), entitySetupUsageClient, pipelineAsyncValidationService,
            pipelineValidationService, projectClient, organizationClient, pmsFeatureFlagService, gitXSettingsHelper,
            accountClient, settingsClient, gitAwareEntityHelper, accessControlClient, yamlSchemaService,
            pipelineRetentionService, templateResourceClient, ngServiceResourceClient, environmentResourceClient,
            scopeResolutionHelper, pipelineEnforcementService, pipelineOpaStatusHandler);
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier);
    doReturn(TemplateMergeResponseDTO.builder().build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(any(), anyBoolean(), anyBoolean());
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    doReturn(false).when(pmsFeatureFlagService).isEnabled(anyString(), (FeatureName) any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipeline() throws IOException {
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .name(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .parentUniqueId(projectIdentifier)
                                        .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToSave.withStageCount(0);
    PipelineEntity pipelineEntitySaved = pipelineToSaveWithUpdatedInfo.withVersion(0L);
    doReturn(pipelineToSaveWithUpdatedInfo)
        .when(pipelineServiceHelper)
        .updatePipelineInfo(eq(pipelineToSave), eq(HarnessYamlVersion.V0), any(), anyBoolean());
    doReturn(Optional.empty()).when(scopeResolutionHelper).getScopeInfoOptional(anyString(), anyString(), anyString());

    doReturn(pipelineEntitySaved).when(pipelineRepository).save(eq(pipelineToSaveWithUpdatedInfo), any(), anyBoolean());
    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
      aStatic.when(() -> NGRestUtils.getResponse(eq(projectClient.getProject(any(), any(), any())), any()))
          .thenReturn(projDTOCall);

      PipelineEntity pipelineEntity =
          pipelineService.validateAndCreatePipeline(pipelineToSave, false, scopeInfo, true).getPipelineEntity();
      assertThat(pipelineEntity).isEqualTo(pipelineEntitySaved);
      verify(pipelineServiceHelper, times(1))
          .sendPipelineSaveTelemetryEvent(eq(pipelineEntitySaved), eq("creating new pipeline"), any(), anyBoolean());
      verify(pipelineAsyncValidationService, times(1))
          .createRecordForSuccessfulSyncValidation(eq(pipelineEntitySaved), eq(null),
              eq(GovernanceMetadata.newBuilder().build()), eq(Action.CRUD), anyBoolean());
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipelineWith__DEFAULT__InGitContext() throws IOException {
    setupGitContext(GitEntityInfo.builder().branch(GitAwareContextHelper.DEFAULT).build());
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .name(pipelineId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .parentUniqueId(projectIdentifier)
                                        .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToSave.withStageCount(0);
    PipelineEntity pipelineEntitySaved = pipelineToSaveWithUpdatedInfo.withVersion(0L);
    doReturn(pipelineToSaveWithUpdatedInfo)
        .when(pipelineServiceHelper)
        .updatePipelineInfo(eq(pipelineToSave), eq(HarnessYamlVersion.V0), any(), anyBoolean());
    doReturn(pipelineEntitySaved).when(pipelineRepository).save(eq(pipelineToSaveWithUpdatedInfo), any(), anyBoolean());
    doReturn(Optional.empty()).when(scopeResolutionHelper).getScopeInfoOptional(anyString(), anyString(), anyString());

    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
      aStatic.when(() -> NGRestUtils.getResponse(eq(projectClient.getProject(any(), any(), any())), any()))
          .thenReturn(projDTOCall);

      PipelineEntity pipelineEntity =
          pipelineService.validateAndCreatePipeline(pipelineToSave, false, scopeInfo, true).getPipelineEntity();
      assertThat(pipelineEntity).isEqualTo(pipelineEntitySaved);
      verify(pipelineServiceHelper, times(1))
          .sendPipelineSaveTelemetryEvent(eq(pipelineEntitySaved), eq("creating new pipeline"), any(), anyBoolean());
      verify(pipelineAsyncValidationService, times(1))
          .createRecordForSuccessfulSyncValidation(eq(pipelineEntitySaved), eq(""),
              eq(GovernanceMetadata.newBuilder().build()), eq(Action.CRUD), anyBoolean());
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipelineWithGovernanceDeny() throws IOException {
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .name(pipelineId)
                                        .yaml(pipelineYaml)
                                        .parentUniqueId(projectIdentifier)
                                        .build();
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(pipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(
            eq(pipelineToSave), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
      aStatic.when(() -> NGRestUtils.getResponse(eq(projectClient.getProject(any(), any(), any())), any()))
          .thenReturn(projDTOCall);
      PipelineCRUDResult pipelineCRUDResult =
          pipelineService.validateAndCreatePipeline(pipelineToSave, true, scopeInfo, true);
      assertThat(pipelineCRUDResult.getPipelineEntity()).isNull();
      assertThat(pipelineCRUDResult.getGovernanceMetadata().getDeny()).isTrue();
      verify(pipelineServiceHelper, times(0)).updatePipelineInfo(any(), eq(HarnessYamlVersion.V0), any(), anyBoolean());
      verify(pipelineRepository, times(0)).saveForOldGitSync(any(), any(), anyBoolean());
      verify(pipelineRepository, times(0)).save(any(), any(), anyBoolean());
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipelineWithSchemaErrors() {
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .name(pipelineId)
                                        .yaml(pipelineYaml)
                                        .parentUniqueId(projectIdentifier)
                                        .build();
    doThrow(new InvalidYamlException("msg", null, pipelineYaml))
        .when(pipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(
            eq(pipelineToSave), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
      aStatic.when(() -> NGRestUtils.getResponse(eq(projectClient.getProject(any(), any(), any())), any()))
          .thenReturn(projDTOCall);
      assertThatThrownBy(() -> pipelineService.validateAndCreatePipeline(pipelineToSave, true, scopeInfo, true))
          .isInstanceOf(InvalidYamlException.class)
          .hasMessage("msg");
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipelineWithHintException() throws IOException {
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .name(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .parentUniqueId(projectIdentifier)
                                        .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToSave.withStageCount(0);
    doReturn(pipelineToSaveWithUpdatedInfo)
        .when(pipelineServiceHelper)
        .updatePipelineInfo(eq(pipelineToSave), eq(HarnessYamlVersion.V0), any(), anyBoolean());
    doThrow(new HintException("this is a hint"))
        .when(pipelineRepository)
        .save(eq(pipelineToSaveWithUpdatedInfo), any(), anyBoolean());
    doReturn(Optional.empty()).when(scopeResolutionHelper).getScopeInfoOptional(anyString(), anyString(), anyString());

    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
      aStatic.when(() -> NGRestUtils.getResponse(eq(projectClient.getProject(any(), any(), any())), any()))
          .thenReturn(projDTOCall);

      assertThatThrownBy(() -> pipelineService.validateAndCreatePipeline(pipelineToSave, true, scopeInfo, true))
          .isInstanceOf(HintException.class)
          .hasMessage("this is a hint");
      verify(pipelineServiceHelper, times(0)).sendPipelineSaveTelemetryEvent(any(), any(), any(), anyBoolean());
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInlinePipeline() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.INLINE)
                                        .build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false, scopeInfo,
            true);
    try (MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class)) {
      Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
      aStatic.when(() -> NGRestUtils.getResponse(eq(projectClient.getProject(any(), any(), any())), any()))
          .thenReturn(projDTOCall);
      Optional<PipelineEntity> optionalPipelineEntity = pipelineService.getAndValidatePipeline(
          accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false, false, false, scopeInfo, true, false);
      assertThat(optionalPipelineEntity.isPresent()).isTrue();
      assertThat(optionalPipelineEntity.get()).isEqualTo(pipelineEntity);
      verify(pipelineServiceHelper, times(0))
          .resolveTemplatesAndValidatePipelineEntity(any(), anyBoolean(), eq(false), any(), anyBoolean());
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetRemotePipeline() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false, scopeInfo,
            true);
    Optional<PipelineEntity> optionalPipelineEntity = pipelineService.getAndValidatePipeline(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false, false, false, scopeInfo, true, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(pipelineEntity);
    verify(pipelineServiceHelper, times(1))
        .resolveTemplatesAndValidatePipelineEntity(any(), anyBoolean(), eq(false), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetRemotePipelineWithNoData() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false, scopeInfo,
            true);
    assertThatThrownBy(()
                           -> pipelineService.getAndValidatePipeline(accountIdentifier, orgIdentifier,
                               projectIdentifier, pipelineId, false, false, false, scopeInfo, true, false))
        .isInstanceOf(InvalidYamlException.class);
    verify(pipelineServiceHelper, times(0))
        .resolveTemplatesAndValidatePipelineEntity(any(), anyBoolean(), eq(false), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineAndAsyncValidationId() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .harnessVersion("0")
                                        .yaml(pipelineYaml)
                                        .build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineRepository)
        .find(
            accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, true, scopeInfo, true);

    String fqn = PipelineAsyncValidationHelper.buildFQN(pipelineEntity, "", true);
    doReturn(Optional.of(PipelineValidationEvent.builder().uuid("validationUuid").build()))
        .when(pipelineAsyncValidationService)
        .getLatestEventByFQNAndAction(fqn, Action.CRUD);
    PipelineGetResult pipelineGetResult = pipelineService.getPipelineAndAsyncValidationId(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false, true, scopeInfo, true);
    assertThat(pipelineGetResult.getPipelineEntity().isPresent()).isTrue();
    assertThat(pipelineGetResult.getPipelineEntity().get()).isEqualTo(pipelineEntity);
    assertThat(pipelineGetResult.getAsyncValidationUUID()).isEqualTo("validationUuid");
    verify(pipelineValidationService, times(1))
        .validateYamlWithUnresolvedTemplates(accountIdentifier, orgIdentifier, projectIdentifier, pipelineYaml, "0");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineAndAsyncValidationIdWithSchemaError() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .harnessVersion("0")
                                        .yaml(pipelineYaml)
                                        .build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineRepository)
        .find(
            accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, true, scopeInfo, true);
    doThrow(new InvalidYamlException("msg", null, pipelineYaml))
        .when(pipelineValidationService)
        .validateYamlWithUnresolvedTemplates(accountIdentifier, orgIdentifier, projectIdentifier, pipelineYaml, "0");
    assertThatThrownBy(()
                           -> pipelineService.getPipelineAndAsyncValidationId(accountIdentifier, orgIdentifier,
                               projectIdentifier, pipelineId, false, true, scopeInfo, true))
        .isInstanceOf(InvalidYamlException.class);
    verify(pipelineValidationService, times(1))
        .validateYamlWithUnresolvedTemplates(accountIdentifier, orgIdentifier, projectIdentifier, pipelineYaml, "0");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineAndAsyncValidationIdWhenLoadingFromGit() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .harnessVersion("0")
                                        .storeType(StoreType.REMOTE)
                                        .yaml(pipelineYaml)
                                        .build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false, scopeInfo,
            true);

    doReturn(PipelineValidationEvent.builder().uuid("validationUuid").build())
        .when(pipelineAsyncValidationService)
        .startEvent(pipelineEntity, null, Action.CRUD, false, scopeInfo, true);
    PipelineGetResult pipelineGetResult = pipelineService.getPipelineAndAsyncValidationId(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false, false, scopeInfo, true);
    assertThat(pipelineGetResult.getPipelineEntity().isPresent()).isTrue();
    assertThat(pipelineGetResult.getPipelineEntity().get()).isEqualTo(pipelineEntity);
    assertThat(pipelineGetResult.getAsyncValidationUUID()).isEqualTo("validationUuid");
    verify(pipelineValidationService, times(1))
        .validateYamlWithUnresolvedTemplates(accountIdentifier, orgIdentifier, projectIdentifier, pipelineYaml, "0");
    verify(pipelineAsyncValidationService, times(0)).getLatestEventByFQNAndAction(any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetNonExistentPipeline() {
    doReturn(Optional.empty())
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false, scopeInfo,
            true);
    assertThatThrownBy(()
                           -> pipelineService.getAndValidatePipeline(accountIdentifier, orgIdentifier,
                               projectIdentifier, pipelineId, false, false, false, scopeInfo, true, false))
        .isInstanceOf(EntityNotFoundException.class);
    verify(pipelineServiceHelper, times(0))
        .resolveTemplatesAndValidatePipelineEntity(any(), anyBoolean(), eq(false), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipeline() throws IOException {
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name(pipelineId)
                                          .yaml(pipelineYaml)
                                          .harnessVersion(HarnessYamlVersion.V0)
                                          .parentUniqueId(projectIdentifier)
                                          .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToUpdate.withStageCount(0);
    doReturn(pipelineToSaveWithUpdatedInfo)
        .when(pipelineServiceHelper)
        .updatePipelineInfo(pipelineToUpdate, HarnessYamlVersion.V0, scopeInfo, true);

    PipelineEntity pipelineEntityUpdated = pipelineToSaveWithUpdatedInfo.withVersion(0L);
    doReturn(pipelineEntityUpdated)
        .when(pipelineRepository)
        .updatePipelineYaml(pipelineToSaveWithUpdatedInfo, false, scopeInfo, true);

    PipelineEntity pipelineEntity =
        pipelineService.validateAndUpdatePipeline(pipelineToUpdate, null, true, false, scopeInfo, true)
            .getPipelineEntity();
    assertThat(pipelineEntity).isEqualTo(pipelineEntityUpdated);
    verify(pipelineServiceHelper, times(1))
        .sendPipelineSaveTelemetryEvent(pipelineEntityUpdated, "updating existing pipeline", scopeInfo, true);
    verify(pipelineAsyncValidationService, times(1))
        .createRecordForSuccessfulSyncValidation(
            pipelineEntityUpdated, null, GovernanceMetadata.newBuilder().build(), Action.CRUD, true);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineWith__DEFAULT__InGitContext() throws IOException {
    setupGitContext(GitEntityInfo.builder().branch(GitAwareContextHelper.DEFAULT).build());
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name(pipelineId)
                                          .yaml(pipelineYaml)
                                          .harnessVersion(HarnessYamlVersion.V0)
                                          .parentUniqueId(projectIdentifier)
                                          .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToUpdate.withStageCount(0);
    doReturn(pipelineToSaveWithUpdatedInfo)
        .when(pipelineServiceHelper)
        .updatePipelineInfo(pipelineToUpdate, HarnessYamlVersion.V0, scopeInfo, true);

    PipelineEntity pipelineEntityUpdated = pipelineToSaveWithUpdatedInfo.withVersion(0L);
    doReturn(pipelineEntityUpdated)
        .when(pipelineRepository)
        .updatePipelineYaml(pipelineToSaveWithUpdatedInfo, false, scopeInfo, true);

    PipelineEntity pipelineEntity =
        pipelineService.validateAndUpdatePipeline(pipelineToUpdate, null, true, false, scopeInfo, true)
            .getPipelineEntity();
    assertThat(pipelineEntity).isEqualTo(pipelineEntityUpdated);
    verify(pipelineServiceHelper, times(1))
        .sendPipelineSaveTelemetryEvent(pipelineEntityUpdated, "updating existing pipeline", scopeInfo, true);
    verify(pipelineAsyncValidationService, times(1))
        .createRecordForSuccessfulSyncValidation(
            pipelineEntityUpdated, "", GovernanceMetadata.newBuilder().build(), Action.CRUD, true);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineWithGovernanceDeny() throws IOException {
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name(pipelineId)
                                          .yaml(pipelineYaml)
                                          .parentUniqueId(projectIdentifier)
                                          .build();
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(pipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(
            eq(pipelineToUpdate), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    PipelineCRUDResult pipelineCRUDResult =
        pipelineService.validateAndUpdatePipeline(pipelineToUpdate, null, true, false, scopeInfo, true);
    assertThat(pipelineCRUDResult.getPipelineEntity()).isNull();
    assertThat(pipelineCRUDResult.getGovernanceMetadata().getDeny()).isTrue();
    verify(pipelineServiceHelper, times(0)).updatePipelineInfo(any(), eq(HarnessYamlVersion.V0));
    verify(pipelineRepository, times(0)).updatePipelineYaml(any(), anyBoolean(), any(), anyBoolean());
    verify(pipelineRepository, times(0)).updatePipelineYamlForOldGitSync(any(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineWithSchemaErrors() {
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .name(pipelineId)
                                          .yaml(pipelineYaml)
                                          .parentUniqueId(projectIdentifier)
                                          .build();
    doThrow(new InvalidYamlException("msg", null, pipelineYaml))
        .when(pipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(
            eq(pipelineToUpdate), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    assertThatThrownBy(
        () -> pipelineService.validateAndUpdatePipeline(pipelineToUpdate, null, true, false, scopeInfo, true))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("msg");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeletePipeline() {
    doReturn(getResponseDTOCall(false)).when(entitySetupUsageClient).isEntityReferenced(any(), any(), any());
    MockedStatic<CGRestUtils> cgStatic = Mockito.mockStatic(CGRestUtils.class);
    cgStatic.when(() -> CGRestUtils.getResponse(any())).thenReturn(false);
    PipelineEntity pipelineMetadata = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .storeType(StoreType.INLINE)
                                          .build();
    doReturn(Optional.of(pipelineMetadata))
        .when(pipelineRepository)
        .find(
            accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, true, false, false, scopeInfo, true);
    boolean delete =
        pipelineService.delete(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, null, scopeInfo, true);
    assertThat(delete).isTrue();

    doThrow(new InvalidRequestException("anything actually")).when(pipelineRepository).delete(scopeInfo, pipelineId);

    assertThatThrownBy(()
                           -> pipelineService.delete(
                               accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, null, scopeInfo, true))
        .isInstanceOf(InvalidRequestException.class);
  }

  private Call<ResponseDTO<Boolean>> getResponseDTOCall(boolean setValue) {
    Call<ResponseDTO<Boolean>> request = mock(Call.class);
    try {
      when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(setValue)));
    } catch (IOException ex) {
    }
    return request;
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }
}

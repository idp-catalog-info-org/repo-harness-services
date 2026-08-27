/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PipelineCRUDResult;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.project.remote.ProjectClient;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;
import io.harness.yaml.validator.beans.YamlValidationResponseDTO;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.util.CloseableIterator;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Verifies that PMSPipelineServiceImpl correctly wires PipelineOpaStatusHandler.handleUiApiSave()
 * after successful create and update operations.
 */
@OwnedBy(PIPELINE)
public class PMSPipelineServiceImplOpaWiringTest extends CategoryTest {
  private static final String ACCOUNT_ID = "acc";
  private static final String ORG_ID = "org";
  private static final String PROJ_ID = "proj";
  private static final String PIPELINE_ID = "myPipeline";

  @Mock private PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock private PipelineGovernanceService pipelineGovernanceService;
  @Mock private PipelineAsyncValidationService pipelineAsyncValidationService;
  @Mock private PipelineOpaStatusHandler pipelineOpaStatusHandler;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private PMSPipelineRepository pmsPipelineRepository;
  @Mock private PipelineSettingsService pipelineSettingsService;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Mock private GitXSettingsHelper gitXSettingsHelper;
  @Mock private ProjectClient projectClient;
  @Mock private OrganizationClient organizationClient;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;

  @InjectMocks private PMSPipelineServiceImpl pmsPipelineService;

  private PipelineEntity baseEntity;
  private ScopeInfo scopeInfo;
  private GovernanceMetadata allowGm;
  private GovernanceMetadata denyGm;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.openMocks(this);

    baseEntity = PipelineEntity.builder()
                     .accountId(ACCOUNT_ID)
                     .orgIdentifier(ORG_ID)
                     .projectIdentifier(PROJ_ID)
                     .identifier(PIPELINE_ID)
                     .name(PIPELINE_ID)
                     .yaml("pipeline:\n  identifier: myPipeline\n  name: myPipeline\n")
                     .harnessVersion(HarnessYamlVersion.V0)
                     .parentUniqueId("test-unique-id")
                     .build();

    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_ID)
                    .projectIdentifier(PROJ_ID)
                    .uniqueId("test-unique-id")
                    .build();

    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(anyString(), anyString());

    allowGm = GovernanceMetadata.newBuilder().setDeny(false).build();
    denyGm = GovernanceMetadata.newBuilder().setDeny(true).build();

    doNothing().when(pmsPipelineServiceHelper).sendPipelineSaveTelemetryEvent(any(), any(), any(), anyBoolean());
    doNothing()
        .when(pmsPipelineServiceHelper)
        .sendTemplatesUsedInPipelinesTelemetryEvent(any(), any(), any(), anyBoolean());

    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(anyString(), anyString(), anyString());

    doReturn(true).when(pipelineSettingsService).isPipelineCreationWithinLimit(anyString(), any(long.class));

    doReturn(0L).when(pmsPipelineRepository).countAllPipelinesInAccount(ACCOUNT_ID);

    Call<ResponseDTO<Optional<ProjectResponse>>> projectCall = Mockito.mock(Call.class);
    doReturn(projectCall).when(projectClient).getProject(anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(Optional.of(ProjectResponse.builder().build()))))
        .when(projectCall)
        .execute();

    Call<ResponseDTO<Optional<OrganizationResponse>>> orgCall = Mockito.mock(Call.class);
    doReturn(orgCall).when(organizationClient).getOrganization(anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(Optional.of(OrganizationResponse.builder().build()))))
        .when(orgCall)
        .execute();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void validateAndCreatePipeline_governanceDenied_doesNotCallHandleSave() {
    doReturn(denyGm)
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), anyBoolean());

    PipelineCRUDResult result = pmsPipelineService.validateAndCreatePipeline(baseEntity, false, scopeInfo, false);

    assertThat(result.getGovernanceMetadata().getDeny()).isTrue();
    verify(pipelineOpaStatusHandler, never()).handleUiApiSave(any(), anyString(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void validateAndCreatePipeline_success_callsHandleSaveWithCreatedEntityAndNullCommitId() throws Exception {
    PipelineEntity savedEntity = baseEntity.toBuilder().uuid("saved-uuid").build();

    doReturn(allowGm)
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), anyBoolean());
    doReturn(baseEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(any(), anyString(), any(), anyBoolean());
    doReturn(savedEntity).when(pmsPipelineRepository).save(any(), any(), anyBoolean());
    doReturn(null)
        .when(pipelineAsyncValidationService)
        .createRecordForSuccessfulSyncValidation(any(), anyString(), any(), any(), anyBoolean());

    pmsPipelineService.validateAndCreatePipeline(baseEntity, false, scopeInfo, false);

    ArgumentCaptor<PipelineEntity> entityCaptor = ArgumentCaptor.forClass(PipelineEntity.class);
    ArgumentCaptor<String> accountCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<GovernanceMetadata> gmCaptor = ArgumentCaptor.forClass(GovernanceMetadata.class);
    verify(pipelineOpaStatusHandler, times(1))
        .handleUiApiSave(entityCaptor.capture(), accountCaptor.capture(), gmCaptor.capture(), isNull());

    assertThat(entityCaptor.getValue().getUuid()).isEqualTo("saved-uuid");
    assertThat(accountCaptor.getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(gmCaptor.getValue().getDeny()).isFalse();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void validateAndUpdatePipeline_governanceDenied_doesNotCallHandleSave() {
    doReturn(denyGm)
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), anyBoolean());

    PipelineCRUDResult result =
        pmsPipelineService.validateAndUpdatePipeline(baseEntity, ChangeType.MODIFY, false, false, scopeInfo, false);

    assertThat(result.getGovernanceMetadata().getDeny()).isTrue();
    verify(pipelineOpaStatusHandler, never()).handleUiApiSave(any(), anyString(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void validateAndUpdatePipeline_success_callsHandleSaveWithUpdatedEntityAndNullCommitId() throws Exception {
    PipelineEntity updatedEntity = baseEntity.toBuilder().uuid("updated-uuid").version(2L).build();

    doReturn(allowGm)
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), anyBoolean());
    doReturn(baseEntity).when(pmsPipelineServiceHelper).updatePipelineInfo(any(), anyString(), any(), anyBoolean());
    doReturn(updatedEntity).when(pmsPipelineRepository).updatePipelineYaml(any(), anyBoolean(), any(), anyBoolean());
    doReturn(null)
        .when(pipelineAsyncValidationService)
        .createRecordForSuccessfulSyncValidation(any(), anyString(), any(), any(), anyBoolean());

    pmsPipelineService.validateAndUpdatePipeline(baseEntity, ChangeType.MODIFY, false, false, scopeInfo, false);

    ArgumentCaptor<PipelineEntity> entityCaptor = ArgumentCaptor.forClass(PipelineEntity.class);
    ArgumentCaptor<String> accountCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<GovernanceMetadata> gmCaptor = ArgumentCaptor.forClass(GovernanceMetadata.class);
    verify(pipelineOpaStatusHandler, times(1))
        .handleUiApiSave(entityCaptor.capture(), accountCaptor.capture(), gmCaptor.capture(), isNull());

    assertThat(entityCaptor.getValue().getUuid()).isEqualTo("updated-uuid");
    assertThat(accountCaptor.getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(gmCaptor.getValue().getDeny()).isFalse();
  }

  // ---------- Flow 2 (MODIFIED / validatePipelineYaml) webhook OPA persist tests ----------

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void validatePipelineYaml_callsHandleSaveWithCorrectArgs() {
    PipelineEntity remoteEntity = PipelineEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJ_ID)
                                      .identifier(PIPELINE_ID)
                                      .parentUniqueId("parentId")
                                      .storeType(StoreType.REMOTE)
                                      .harnessVersion(HarnessYamlVersion.V0)
                                      .build();
    Stream<PipelineEntity> stream = createCloseableIterator(List.of(remoteEntity).iterator()).stream();

    GovernanceMetadata gm = GovernanceMetadata.newBuilder().setDeny(false).setStatus("pass").build();
    YamlValidationRequestDTO requestDTO = YamlValidationRequestDTO.builder()
                                              .repoName("repo")
                                              .branch("feature/test")
                                              .filePath("filePath")
                                              .isDefaultBranch(false)
                                              .commitId("abc123")
                                              .build();

    doReturn(gm)
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    doReturn(stream).when(pmsPipelineServiceHelper).fetchAllPipelinesByFilePathAndRepo(any(), any(), any());

    pmsPipelineService.validatePipelineYaml(ACCOUNT_ID, requestDTO);

    ArgumentCaptor<PipelineEntity> entityCaptor = ArgumentCaptor.forClass(PipelineEntity.class);
    ArgumentCaptor<String> commitCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<GovernanceMetadata> gmCaptor = ArgumentCaptor.forClass(GovernanceMetadata.class);
    verify(pipelineOpaStatusHandler, times(1))
        .handleWebhookSave(entityCaptor.capture(), eq(ACCOUNT_ID), gmCaptor.capture(), commitCaptor.capture());

    assertThat(entityCaptor.getValue().getBranch()).isEqualTo("feature/test");
    assertThat(commitCaptor.getValue()).isEqualTo("abc123");
    assertThat(gmCaptor.getValue().getDeny()).isFalse();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void validatePipelineYaml_handleSaveThrows_failOpenReturnsResult() {
    PipelineEntity remoteEntity = PipelineEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJ_ID)
                                      .identifier(PIPELINE_ID)
                                      .parentUniqueId("parentId")
                                      .storeType(StoreType.REMOTE)
                                      .harnessVersion(HarnessYamlVersion.V0)
                                      .build();
    Stream<PipelineEntity> stream = createCloseableIterator(List.of(remoteEntity).iterator()).stream();

    YamlValidationRequestDTO requestDTO = YamlValidationRequestDTO.builder()
                                              .repoName("repo")
                                              .branch("main")
                                              .filePath("filePath")
                                              .isDefaultBranch(false)
                                              .commitId("commit1")
                                              .build();

    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    doReturn(stream).when(pmsPipelineServiceHelper).fetchAllPipelinesByFilePathAndRepo(any(), any(), any());
    doThrow(new RuntimeException("DB down"))
        .when(pipelineOpaStatusHandler)
        .handleWebhookSave(any(), any(), any(), any());

    List<YamlValidationResponseDTO> result = pmsPipelineService.validatePipelineYaml(ACCOUNT_ID, requestDTO);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getIsValid()).isTrue();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void validatePipelineYaml_noSecondOpaEvaluation_reusesGovernanceMetadata() {
    PipelineEntity remoteEntity = PipelineEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJ_ID)
                                      .identifier(PIPELINE_ID)
                                      .parentUniqueId("parentId")
                                      .storeType(StoreType.REMOTE)
                                      .harnessVersion(HarnessYamlVersion.V0)
                                      .build();
    Stream<PipelineEntity> stream = createCloseableIterator(List.of(remoteEntity).iterator()).stream();

    GovernanceMetadata gm = GovernanceMetadata.newBuilder().setDeny(false).setId("eval-abc").build();
    YamlValidationRequestDTO requestDTO = YamlValidationRequestDTO.builder()
                                              .repoName("repo")
                                              .branch("main")
                                              .filePath("filePath")
                                              .isDefaultBranch(false)
                                              .commitId("commit1")
                                              .build();

    doReturn(gm)
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    doReturn(stream).when(pmsPipelineServiceHelper).fetchAllPipelinesByFilePathAndRepo(any(), any(), any());

    pmsPipelineService.validatePipelineYaml(ACCOUNT_ID, requestDTO);

    verify(pmsPipelineServiceHelper, times(1))
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    ArgumentCaptor<GovernanceMetadata> gmCaptor = ArgumentCaptor.forClass(GovernanceMetadata.class);
    verify(pipelineOpaStatusHandler, times(1)).handleWebhookSave(any(), any(), gmCaptor.capture(), any());
    assertThat(gmCaptor.getValue().getId()).isEqualTo("eval-abc");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void validatePipelineYaml_validationFails_skipsPersistAndStillReturnsError() {
    PipelineEntity remoteEntity = PipelineEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJ_ID)
                                      .identifier(PIPELINE_ID)
                                      .parentUniqueId("parentId")
                                      .storeType(StoreType.REMOTE)
                                      .harnessVersion(HarnessYamlVersion.V0)
                                      .build();
    Stream<PipelineEntity> stream = createCloseableIterator(List.of(remoteEntity).iterator()).stream();

    YamlValidationRequestDTO requestDTO = YamlValidationRequestDTO.builder()
                                              .repoName("repo")
                                              .branch("main")
                                              .filePath("filePath")
                                              .isDefaultBranch(false)
                                              .commitId("commit1")
                                              .build();

    doThrow(new RuntimeException("template resolution failed"))
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    doReturn(stream).when(pmsPipelineServiceHelper).fetchAllPipelinesByFilePathAndRepo(any(), any(), any());

    List<YamlValidationResponseDTO> result = pmsPipelineService.validatePipelineYaml(ACCOUNT_ID, requestDTO);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getIsValid()).isFalse();
    verify(pipelineOpaStatusHandler, never()).handleWebhookSave(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void validatePipelineYaml_governanceDenies_marksFileInvalidAndPersistsStatus() {
    PipelineEntity remoteEntity = PipelineEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJ_ID)
                                      .identifier(PIPELINE_ID)
                                      .parentUniqueId("parentId")
                                      .storeType(StoreType.REMOTE)
                                      .harnessVersion(HarnessYamlVersion.V0)
                                      .build();
    Stream<PipelineEntity> stream = createCloseableIterator(List.of(remoteEntity).iterator()).stream();

    GovernanceMetadata gm =
        GovernanceMetadata.newBuilder()
            .setDeny(true)
            .addDetails(PolicySetMetadata.newBuilder().setIdentifier("policy1").setDeny(true).build())
            .addDetails(PolicySetMetadata.newBuilder().setIdentifier("policy2").setDeny(true).build())
            .addDetails(PolicySetMetadata.newBuilder().setIdentifier("policy3").setDeny(false).build())
            .build();
    YamlValidationRequestDTO requestDTO = YamlValidationRequestDTO.builder()
                                              .repoName("repo")
                                              .branch("main")
                                              .filePath("filePath")
                                              .isDefaultBranch(false)
                                              .commitId("commit1")
                                              .build();

    doReturn(gm)
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    doReturn(stream).when(pmsPipelineServiceHelper).fetchAllPipelinesByFilePathAndRepo(any(), any(), any());

    List<YamlValidationResponseDTO> result = pmsPipelineService.validatePipelineYaml(ACCOUNT_ID, requestDTO);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getIsValid()).isFalse();
    assertThat(result.get(0).getValidationErrorMetadata().getErrorMessage())
        .isEqualTo("Pipeline does not follow the Policies in these Policy Sets: policy1, policy2");
    assertThat(result.get(0).getValidationErrorMetadata().getExplanation())
        .contains("does not comply with the OPA governance policies");
    assertThat(result.get(0).getValidationErrorMetadata().getHint()).contains("denying policy sets");
    verify(pipelineOpaStatusHandler, times(1)).handleWebhookSave(any(), eq(ACCOUNT_ID), eq(gm), eq("commit1"));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void validatePipelineYaml_governanceDeniesWithKillSwitchOn_keepsFileValid() {
    PipelineEntity remoteEntity = PipelineEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJ_ID)
                                      .identifier(PIPELINE_ID)
                                      .parentUniqueId("parentId")
                                      .storeType(StoreType.REMOTE)
                                      .harnessVersion(HarnessYamlVersion.V0)
                                      .build();
    Stream<PipelineEntity> stream = createCloseableIterator(List.of(remoteEntity).iterator()).stream();

    GovernanceMetadata gm =
        GovernanceMetadata.newBuilder()
            .setDeny(true)
            .addDetails(PolicySetMetadata.newBuilder().setIdentifier("policy1").setDeny(true).build())
            .build();
    YamlValidationRequestDTO requestDTO = YamlValidationRequestDTO.builder()
                                              .repoName("repo")
                                              .branch("main")
                                              .filePath("filePath")
                                              .isDefaultBranch(false)
                                              .commitId("commit1")
                                              .build();

    doReturn(gm)
        .when(pmsPipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), eq(false));
    doReturn(stream).when(pmsPipelineServiceHelper).fetchAllPipelinesByFilePathAndRepo(any(), any(), any());
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled(ACCOUNT_ID, FeatureName.PIPE_DISABLE_OPA_GOVERNANCE_IN_WEBHOOK_VALIDATION);

    List<YamlValidationResponseDTO> result = pmsPipelineService.validatePipelineYaml(ACCOUNT_ID, requestDTO);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getIsValid()).isTrue();
    verify(pipelineOpaStatusHandler, times(1)).handleWebhookSave(any(), eq(ACCOUNT_ID), eq(gm), eq("commit1"));
  }

  // ---------- Helper ----------

  private <T> CloseableIterator<T> createCloseableIterator(Iterator<T> iterator) {
    return new CloseableIterator<T>() {
      @Override
      public void close() {}

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return iterator.next();
      }
    };
  }
}

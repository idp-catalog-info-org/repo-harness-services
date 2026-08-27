/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.handler;

import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.ngexception.beans.yamlschema.NodeErrorInfo;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.TemplateValidationResponseDTO;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationParams;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.pipeline.validation.service.intfc.PipelineValidationService;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.validator.InvalidYamlException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class PipelineAsyncValidationHandlerTest extends CategoryTest {
  PipelineAsyncValidationHandler pipelineAsyncValidationHandler;
  @Mock PipelineAsyncValidationService validationService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock PipelineGovernanceService pipelineGovernanceService;
  @Mock PipelineRefreshService pipelineRefreshService;
  @Mock PipelineValidationService pipelineValidationService;

  PipelineEntity pipelineEntity;
  PipelineValidationEvent pipelineValidationEvent;

  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PipelineOpaStatusHandler pipelineOpaStatusHandler;
  @Mock ScopeInfo scopeInfo;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    pipelineEntity = PipelineEntity.builder()
                         .accountId("acc")
                         .orgIdentifier("org")
                         .projectIdentifier("proj")
                         .identifier("pipeline")
                         .build();
    pipelineValidationEvent = PipelineValidationEvent.builder()
                                  .uuid("abc123")
                                  .params(ValidationParams.builder().pipelineEntity(pipelineEntity).build())
                                  .build();
    pipelineAsyncValidationHandler = new PipelineAsyncValidationHandler(pipelineValidationEvent, false, scopeInfo,
        false, validationService, pipelineTemplateHelper, pipelineGovernanceService, pipelineRefreshService,
        pipelineValidationService, pmsFeatureFlagService, pipelineOpaStatusHandler);
    when(
        pmsFeatureFlagService.isEnabled(pipelineEntity.getAccountId(), FeatureName.PIE_VALIDATE_SCHEMA_IN_VALIDATE_API))
        .thenReturn(false);
    when(scopeInfo.getOrgIdentifier()).thenReturn(pipelineEntity.getOrgIdentifier());
    when(scopeInfo.getProjectIdentifier()).thenReturn(pipelineEntity.getProjectIdentifier());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSuccessfulRun() {
    TemplateMergeResponseDTO templateMergeResponse =
        TemplateMergeResponseDTO.builder().mergedPipelineYamlWithTemplateRef("yaml").build();
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder().validYaml(true).build();
    doReturn(validateTemplateInputsResponseDTO)
        .when(pipelineRefreshService)
        .validateTemplateInputsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), "false", scopeInfo, false);
    doReturn(templateMergeResponse)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, scopeInfo, true, false);
    doReturn(new HashSet<>(Arrays.asList("CD", "CI")))
        .when(pipelineTemplateHelper)
        .getTemplatesModuleInfo(templateMergeResponse);

    MockedStatic<GitAwareContextHelper> gitAwareContextHelperMockedStatic = mockStatic(GitAwareContextHelper.class);
    gitAwareContextHelperMockedStatic.when(GitAwareContextHelper::getBranchInRequestOrFromSCMGitMetadata)
        .thenReturn("branch");
    doReturn(io.harness.governance.GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineGovernanceService)
        .validateGovernanceRules("acc", "org", "proj", "branch", pipelineEntity, "yaml");
    pipelineAsyncValidationHandler.run();
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.IN_PROGRESS, ValidationResult.builder().build());
    verify(pipelineRefreshService, times(1))
        .validateTemplateInputsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), "false", scopeInfo, false);
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.IN_PROGRESS,
            ValidationResult.builder()
                .templateValidationResponse(TemplateValidationResponseDTO.builder().validYaml(true).build())
                .build());
    assertThat(pipelineEntity.getTemplateModules()).containsExactly("CD", "CI");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluatePoliciesAndUpdateResultForFailure() {
    MockedStatic<GitAwareContextHelper> gitAwareContextHelperMockedStatic = mockStatic(GitAwareContextHelper.class);
    gitAwareContextHelperMockedStatic.when(GitAwareContextHelper::getBranchInRequestOrFromSCMGitMetadata)
        .thenReturn("branch");
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(pipelineGovernanceService)
        .validateGovernanceRules("acc", "org", "proj", "branch", pipelineEntity, "yaml");
    pipelineAsyncValidationHandler.evaluatePoliciesAndUpdateResult(pipelineEntity,
        TemplateMergeResponseDTO.builder().mergedPipelineYamlWithTemplateRef("yaml").build(),
        ValidationResult.builder().build());
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.FAILURE,
            ValidationResult.builder()
                .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(true).build())
                .build());
    verify(validationService, times(0))
        .updateEvent("abc123", ValidationStatus.SUCCESS,
            ValidationResult.builder()
                .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(true).build())
                .build());
    verify(pipelineRefreshService, times(0))
        .validateTemplateInputsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), "false", null, false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluatePoliciesAndUpdateResultForSuccess() {
    MockedStatic<GitAwareContextHelper> gitAwareContextHelperMockedStatic = mockStatic(GitAwareContextHelper.class);
    gitAwareContextHelperMockedStatic.when(GitAwareContextHelper::getBranchInRequestOrFromSCMGitMetadata)
        .thenReturn("branch");
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineGovernanceService)
        .validateGovernanceRules("acc", "org", "proj", "branch", pipelineEntity, "yaml");
    pipelineAsyncValidationHandler.evaluatePoliciesAndUpdateResult(pipelineEntity,
        TemplateMergeResponseDTO.builder().mergedPipelineYamlWithTemplateRef("yaml").build(),
        ValidationResult.builder().build());
    verify(validationService, times(0))
        .updateEvent("abc123", ValidationStatus.FAILURE,
            ValidationResult.builder()
                .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                .build());
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.SUCCESS,
            ValidationResult.builder()
                .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                .build());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testReconcileForFailure() {
    doReturn(ValidateTemplateInputsResponseDTO.builder().validYaml(false).build())
        .when(pipelineRefreshService)
        .validateTemplateInputsInPipeline("acc", "org", "proj", "pipeline", "false", scopeInfo, false);
    pipelineAsyncValidationHandler.run();
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.IN_PROGRESS, ValidationResult.builder().build());
    verify(pipelineRefreshService, times(1))
        .validateTemplateInputsInPipeline(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), "false", scopeInfo, false);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testCheckForReconcile() {
    String newOrg = "newOrg";
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(pipelineEntity.getAccountIdentifier())
                              .orgIdentifier(newOrg)
                              .projectIdentifier(pipelineEntity.getProjectIdentifier())
                              .uniqueId("xyz")
                              .build();
    PipelineAsyncValidationHandler pipelineAsyncValidationHandlerWithScopeInfo =
        new PipelineAsyncValidationHandler(pipelineValidationEvent, false, scopeInfo, true, validationService,
            pipelineTemplateHelper, pipelineGovernanceService, pipelineRefreshService, pipelineValidationService,
            pmsFeatureFlagService, pipelineOpaStatusHandler);
    pipelineAsyncValidationHandlerWithScopeInfo.checkForReconcile(pipelineEntity);
    verify(pipelineRefreshService, times(1))
        .validateTemplateInputsInPipeline(pipelineEntity.getAccountId(), newOrg, pipelineEntity.getProjectIdentifier(),
            pipelineEntity.getIdentifier(), "false", scopeInfo, true);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testValidateTemplatesAndUpdateResultInvalidYamlException() {
    YamlSchemaErrorWrapperDTO yamlSchemaErrorWrapperDTO =
        YamlSchemaErrorWrapperDTO.builder()
            .schemaErrors(Collections.singletonList(YamlSchemaErrorDTO.builder()
                                                        .message("errorMessage")
                                                        .fqn("$.inputSet")
                                                        .messageWithFQN("errorMessage $.inputSet")
                                                        .stageInfo(NodeErrorInfo.builder().identifier("stage1").build())
                                                        .stepInfo(NodeErrorInfo.builder().identifier("step1").build())
                                                        .hintMessage("trySomething")
                                                        .build()))
            .build();
    when(pipelineTemplateHelper.resolveTemplateRefsInPipeline(any(), any(), anyBoolean(), anyBoolean()))
        .thenThrow(new InvalidYamlException("errorMessage", yamlSchemaErrorWrapperDTO, "yaml"));
    when(
        pmsFeatureFlagService.isEnabled(pipelineEntity.getAccountId(), FeatureName.PIE_VALIDATE_SCHEMA_IN_VALIDATE_API))
        .thenReturn(true);

    Pair<ValidationResult, TemplateMergeResponseDTO> result =
        pipelineAsyncValidationHandler.validateTemplatesAndUpdateResult(pipelineEntity);

    assertThat(result.getLeft().getTemplateValidationResponse().isValidYaml()).isFalse();
    assertThat(result.getLeft().getTemplateValidationResponse().getExceptionMessage()).contains("errorMessage");
    verify(validationService, times(1))
        .updateEvent("abc123", ValidationStatus.FAILURE,
            ValidationResult.builder()
                .templateValidationResponse(TemplateValidationResponseDTO.builder()
                                                .validYaml(false)
                                                .exceptionMessage("errorMessage $.inputSet\n")
                                                .build())
                .build());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testPersistOpaOnSaveStatus_remoteEntityWithGm_callsHandleSaveOnce() {
    String commitId = "abc123commit";
    PipelineEntity remoteEntity = PipelineEntity.builder()
                                      .accountId("acc")
                                      .orgIdentifier("org")
                                      .projectIdentifier("proj")
                                      .identifier("pipeline")
                                      .storeType(StoreType.REMOTE)
                                      .build();
    PipelineValidationEvent event =
        PipelineValidationEvent.builder()
            .uuid("evt1")
            .params(ValidationParams.builder().pipelineEntity(remoteEntity).commitId(commitId).build())
            .build();
    PipelineAsyncValidationHandler handler = new PipelineAsyncValidationHandler(event, false, scopeInfo, false,
        validationService, pipelineTemplateHelper, pipelineGovernanceService, pipelineRefreshService,
        pipelineValidationService, pmsFeatureFlagService, pipelineOpaStatusHandler);

    GovernanceMetadata gm = GovernanceMetadata.newBuilder().setDeny(false).build();

    MockedStatic<GitAwareContextHelper> gitMock = mockStatic(GitAwareContextHelper.class);
    gitMock.when(() -> GitAwareContextHelper.isRemoteEntity(remoteEntity)).thenReturn(true);

    handler.persistOpaOnSaveStatus(remoteEntity, gm);

    verify(pipelineOpaStatusHandler, times(1)).handleUiApiSave(remoteEntity, "acc", gm, commitId);
    gitMock.close();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testPersistOpaOnSaveStatus_inlineEntity_doesNotCallHandleSave() {
    PipelineEntity inlineEntity = PipelineEntity.builder()
                                      .accountId("acc")
                                      .orgIdentifier("org")
                                      .projectIdentifier("proj")
                                      .identifier("pipeline")
                                      .storeType(StoreType.INLINE)
                                      .build();
    PipelineValidationEvent event =
        PipelineValidationEvent.builder()
            .uuid("evt2")
            .params(ValidationParams.builder().pipelineEntity(inlineEntity).commitId("commit1").build())
            .build();
    PipelineAsyncValidationHandler handler = new PipelineAsyncValidationHandler(event, false, scopeInfo, false,
        validationService, pipelineTemplateHelper, pipelineGovernanceService, pipelineRefreshService,
        pipelineValidationService, pmsFeatureFlagService, pipelineOpaStatusHandler);

    GovernanceMetadata gm = GovernanceMetadata.newBuilder().setDeny(false).build();

    MockedStatic<GitAwareContextHelper> gitMock = mockStatic(GitAwareContextHelper.class);
    gitMock.when(() -> GitAwareContextHelper.isRemoteEntity(inlineEntity)).thenReturn(false);

    handler.persistOpaOnSaveStatus(inlineEntity, gm);

    verify(pipelineOpaStatusHandler, never()).handleUiApiSave(any(), any(), any(), any());
    gitMock.close();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testPersistOpaOnSaveStatus_nullGm_doesNotCallHandleSave() {
    PipelineEntity remoteEntity = PipelineEntity.builder()
                                      .accountId("acc")
                                      .orgIdentifier("org")
                                      .projectIdentifier("proj")
                                      .identifier("pipeline")
                                      .storeType(StoreType.REMOTE)
                                      .build();
    PipelineValidationEvent event =
        PipelineValidationEvent.builder()
            .uuid("evt3")
            .params(ValidationParams.builder().pipelineEntity(remoteEntity).commitId("commit1").build())
            .build();
    PipelineAsyncValidationHandler handler = new PipelineAsyncValidationHandler(event, false, scopeInfo, false,
        validationService, pipelineTemplateHelper, pipelineGovernanceService, pipelineRefreshService,
        pipelineValidationService, pmsFeatureFlagService, pipelineOpaStatusHandler);

    handler.persistOpaOnSaveStatus(remoteEntity, null);

    verify(pipelineOpaStatusHandler, never()).handleUiApiSave(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testPersistOpaOnSaveStatus_handleSaveThrows_validationStillSucceeds() {
    String commitId = "abc123commit";
    PipelineEntity remoteEntity = PipelineEntity.builder()
                                      .accountId("acc")
                                      .orgIdentifier("org")
                                      .projectIdentifier("proj")
                                      .identifier("pipeline")
                                      .storeType(StoreType.REMOTE)
                                      .build();
    PipelineValidationEvent event =
        PipelineValidationEvent.builder()
            .uuid("evt4")
            .params(ValidationParams.builder().pipelineEntity(remoteEntity).commitId(commitId).build())
            .build();
    PipelineAsyncValidationHandler handler = new PipelineAsyncValidationHandler(event, false, scopeInfo, false,
        validationService, pipelineTemplateHelper, pipelineGovernanceService, pipelineRefreshService,
        pipelineValidationService, pmsFeatureFlagService, pipelineOpaStatusHandler);

    GovernanceMetadata gm = GovernanceMetadata.newBuilder().setDeny(false).build();

    MockedStatic<GitAwareContextHelper> gitMock = mockStatic(GitAwareContextHelper.class);
    gitMock.when(() -> GitAwareContextHelper.isRemoteEntity(remoteEntity)).thenReturn(true);
    doThrow(new RuntimeException("DB failure"))
        .when(pipelineOpaStatusHandler)
        .handleWebhookSave(any(), any(), any(), any());

    handler.persistOpaOnSaveStatus(remoteEntity, gm);

    verify(pipelineOpaStatusHandler, times(1)).handleUiApiSave(remoteEntity, "acc", gm, commitId);
    gitMock.close();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testEnrichWithOpaEvalData_remoteEntity_cleanStatus_capturesTimestampAndCommitId() {
    String commitId = "abc123commit";
    PipelineEntity remoteEntity = PipelineEntity.builder()
                                      .accountId("acc")
                                      .orgIdentifier("org")
                                      .projectIdentifier("proj")
                                      .identifier("pipeline")
                                      .storeType(StoreType.REMOTE)
                                      .build();
    PipelineValidationEvent event =
        PipelineValidationEvent.builder()
            .uuid("evt5")
            .params(ValidationParams.builder().pipelineEntity(remoteEntity).commitId(commitId).build())
            .build();
    PipelineAsyncValidationHandler handler = new PipelineAsyncValidationHandler(event, false, scopeInfo, false,
        validationService, pipelineTemplateHelper, pipelineGovernanceService, pipelineRefreshService,
        pipelineValidationService, pmsFeatureFlagService, pipelineOpaStatusHandler);

    GovernanceMetadata gm = GovernanceMetadata.newBuilder().setDeny(false).build();
    ValidationResult inputResult = ValidationResult.builder().governanceMetadata(gm).build();

    MockedStatic<GitAwareContextHelper> gitMock = mockStatic(GitAwareContextHelper.class);
    gitMock.when(() -> GitAwareContextHelper.isRemoteEntity(remoteEntity)).thenReturn(true);

    long beforeTs = System.currentTimeMillis();
    ValidationResult enriched = handler.enrichWithOpaEvalData(remoteEntity, gm, inputResult);
    long afterTs = System.currentTimeMillis();

    assertThat(enriched.getOpaEvaluatedAt()).isNotNull();
    assertThat(enriched.getOpaEvaluatedAt()).isBetween(beforeTs, afterTs);
    assertThat(enriched.getOpaLastValidCommitId()).isEqualTo(commitId);
    gitMock.close();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testEnrichWithOpaEvalData_remoteEntity_errorStatus_queriesDbForLastValid() {
    String commitId = "abc123commit";
    String priorValidCommit = "prior_valid_commit_sha";
    PipelineEntity remoteEntity = PipelineEntity.builder()
                                      .accountId("acc")
                                      .orgIdentifier("org")
                                      .projectIdentifier("proj")
                                      .identifier("pipeline")
                                      .storeType(StoreType.REMOTE)
                                      .build();
    PipelineValidationEvent event =
        PipelineValidationEvent.builder()
            .uuid("evt6")
            .params(ValidationParams.builder().pipelineEntity(remoteEntity).commitId(commitId).build())
            .build();
    PipelineAsyncValidationHandler handler = new PipelineAsyncValidationHandler(event, false, scopeInfo, false,
        validationService, pipelineTemplateHelper, pipelineGovernanceService, pipelineRefreshService,
        pipelineValidationService, pmsFeatureFlagService, pipelineOpaStatusHandler);

    GovernanceMetadata gm = GovernanceMetadata.newBuilder().setDeny(true).build();
    ValidationResult inputResult = ValidationResult.builder().governanceMetadata(gm).build();

    MockedStatic<GitAwareContextHelper> gitMock = mockStatic(GitAwareContextHelper.class);
    gitMock.when(() -> GitAwareContextHelper.isRemoteEntity(remoteEntity)).thenReturn(true);

    io.harness.opa.gitx.OpaOnSaveStatusDTO storedRecord =
        io.harness.opa.gitx.OpaOnSaveStatusDTO.builder().lastValidCommitId(priorValidCommit).build();
    when(pipelineOpaStatusHandler.get(remoteEntity, "acc", commitId)).thenReturn(java.util.Optional.of(storedRecord));

    ValidationResult enriched = handler.enrichWithOpaEvalData(remoteEntity, gm, inputResult);

    assertThat(enriched.getOpaEvaluatedAt()).isNotNull();
    assertThat(enriched.getOpaLastValidCommitId()).isEqualTo(priorValidCommit);
    gitMock.close();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testEnrichWithOpaEvalData_inlineEntity_returnsUnchanged() {
    PipelineEntity inlineEntity = PipelineEntity.builder()
                                      .accountId("acc")
                                      .orgIdentifier("org")
                                      .projectIdentifier("proj")
                                      .identifier("pipeline")
                                      .storeType(StoreType.INLINE)
                                      .build();
    PipelineValidationEvent event =
        PipelineValidationEvent.builder()
            .uuid("evt7")
            .params(ValidationParams.builder().pipelineEntity(inlineEntity).commitId("commit1").build())
            .build();
    PipelineAsyncValidationHandler handler = new PipelineAsyncValidationHandler(event, false, scopeInfo, false,
        validationService, pipelineTemplateHelper, pipelineGovernanceService, pipelineRefreshService,
        pipelineValidationService, pmsFeatureFlagService, pipelineOpaStatusHandler);

    GovernanceMetadata gm = GovernanceMetadata.newBuilder().setDeny(false).build();
    ValidationResult inputResult = ValidationResult.builder().governanceMetadata(gm).build();

    MockedStatic<GitAwareContextHelper> gitMock = mockStatic(GitAwareContextHelper.class);
    gitMock.when(() -> GitAwareContextHelper.isRemoteEntity(inlineEntity)).thenReturn(false);

    ValidationResult enriched = handler.enrichWithOpaEvalData(inlineEntity, gm, inputResult);

    assertThat(enriched.getOpaEvaluatedAt()).isNull();
    assertThat(enriched.getOpaLastValidCommitId()).isNull();
    gitMock.close();
  }
}

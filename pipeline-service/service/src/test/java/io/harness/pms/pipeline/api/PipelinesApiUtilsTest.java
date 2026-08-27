/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.pipeline.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.pipeline.api.PipelinesApiUtils.buildMoveConfigOperationDTO;
import static io.harness.pms.pipeline.api.PipelinesApiUtils.getPipelineSorting;
import static io.harness.pms.pipeline.api.PipelinesApiUtils.resolveValidateOpaEnrichment;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.publicaccess.dto.PublicAccessResponse;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.NodeErrorInfo;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.CacheState;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.PolicySetMetadata;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.opa.gitx.OpaGitxStatus;
import io.harness.opa.gitx.OpaOnSaveStatusDTO;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.RecentExecutionInfoDTO;
import io.harness.pms.pipeline.TemplateValidationResponseDTO;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.rule.Owner;
import io.harness.spec.server.commons.v1.model.GovernanceMetadata;
import io.harness.spec.server.commons.v1.model.GovernanceStatus;
import io.harness.spec.server.commons.v1.model.PolicySet;
import io.harness.spec.server.pipeline.v1.model.CacheResponseMetadataDTO;
import io.harness.spec.server.pipeline.v1.model.GitDetails;
import io.harness.spec.server.pipeline.v1.model.GitMoveDetails;
import io.harness.spec.server.pipeline.v1.model.MoveConfigOperationType;
import io.harness.spec.server.pipeline.v1.model.PipelineGetResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineListResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineListResponseBody.StoreTypeEnum;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationResponseBody;
import io.harness.spec.server.pipeline.v1.model.RecentExecutionInfo;
import io.harness.spec.server.pipeline.v1.model.YAMLSchemaErrorWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelinesApiUtilsTest extends CategoryTest {
  String identifier = randomAlphabetic(10);
  String name = randomAlphabetic(10);

  Long lastUpdatedAt = 987654L;

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetGitDetails() {
    EntityGitDetails entityGitDetails = EntityGitDetails.builder()
                                            .objectId("objectId")
                                            .branch("branch")
                                            .commitId("commitId")
                                            .filePath("filePath")
                                            .fileUrl("fileUrl")
                                            .repoUrl("repoUrl")
                                            .repoName("repoName")
                                            .build();
    GitDetails gitDetails = PipelinesApiUtils.getGitDetails(entityGitDetails);
    assertEquals("objectId", gitDetails.getObjectId());
    assertEquals("branch", gitDetails.getBranchName());
    assertEquals("commitId", gitDetails.getCommitId());
    assertEquals("filePath", gitDetails.getFilePath());
    assertEquals("fileUrl", gitDetails.getFileUrl());
    assertEquals("repoUrl", gitDetails.getRepoUrl());
    assertEquals("repoName", gitDetails.getRepoName());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetYAMLSchemaWrapper() {
    YamlSchemaErrorWrapperDTO yamlSchemaErrorWrapperDTO =
        YamlSchemaErrorWrapperDTO.builder()
            .schemaErrors(Collections.singletonList(YamlSchemaErrorDTO.builder()
                                                        .message("errorMessage")
                                                        .fqn("$.inputSet")
                                                        .stageInfo(NodeErrorInfo.builder().identifier("stage1").build())
                                                        .stepInfo(NodeErrorInfo.builder().identifier("step1").build())
                                                        .hintMessage("trySomething")
                                                        .build()))
            .build();
    List<YAMLSchemaErrorWrapper> yamlSchemaErrorWrappers =
        PipelinesApiUtils.getListYAMLErrorWrapper(yamlSchemaErrorWrapperDTO);
    assertEquals(1, yamlSchemaErrorWrappers.size());
    YAMLSchemaErrorWrapper yamlSchemaErrorWrapper = yamlSchemaErrorWrappers.get(0);
    assertEquals("errorMessage", yamlSchemaErrorWrapper.getMessage());
    assertEquals("$.inputSet", yamlSchemaErrorWrapper.getFqn());
    assertEquals("stage1", yamlSchemaErrorWrapper.getStageInfo().getIdentifier());
    assertEquals("step1", yamlSchemaErrorWrapper.getStepInfo().getIdentifier());
    assertEquals("trySomething", yamlSchemaErrorWrapper.getHintMessage());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetResponseBody() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .yaml("yaml")
                                        .identifier(identifier)
                                        .orgIdentifier("org")
                                        .createdAt(123456L)
                                        .lastUpdatedAt(987654L)
                                        .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("account")
                              .orgIdentifier("org1")
                              .uniqueId("unique-id")
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .build();
    PipelineGetResponseBody responseBody = PipelinesApiUtils.getGetResponseBody(pipelineEntity, scopeInfo, true);
    assertEquals("yaml", responseBody.getPipelineYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals("org1", responseBody.getOrg());
    assertEquals(123456L, responseBody.getCreated().longValue());
    assertEquals(987654L, responseBody.getUpdated().longValue());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetFilterProperties() {
    List<String> tags = new ArrayList<>();
    tags.add("key:value");
    tags.add("key2");
    PipelineFilterPropertiesDto pipelineFilterPropertiesDto =
        PipelinesApiUtils.getFilterProperties(Collections.singletonList("pipelineId"), "name", null, tags,
            Collections.singletonList("service"), Collections.singletonList("envs"), "deploymentType", "repo");
    assertEquals(pipelineFilterPropertiesDto.getPipelineIdentifiers().get(0), "pipelineId");
    assertEquals(pipelineFilterPropertiesDto.getName(), "name");
    assertEquals(
        pipelineFilterPropertiesDto.getPipelineTags().get(0), NGTag.builder().key("key").value("value").build());
    assertEquals(pipelineFilterPropertiesDto.getTags().get("key2"), null);

    PipelineFilterPropertiesDto pipelineFilterPropertiesDto2 =
        PipelinesApiUtils.getFilterProperties(Collections.emptyList(), null, null, Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), null, null);
    assertEquals(pipelineFilterPropertiesDto2, null);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetPipelines() {
    PMSPipelineSummaryResponseDTO pmsPipelineSummaryResponseDTO = PMSPipelineSummaryResponseDTO.builder()
                                                                      .identifier(identifier)
                                                                      .name(name)
                                                                      .createdAt(123456L)
                                                                      .lastUpdatedAt(987654L)
                                                                      .storeType(StoreType.INLINE)
                                                                      .yamlVersion("1")
                                                                      .build();
    PipelineListResponseBody listResponseBody = PipelinesApiUtils.getPipelines(pmsPipelineSummaryResponseDTO);
    assertEquals(listResponseBody.getCreated().longValue(), 123456L);
    assertEquals(listResponseBody.getUpdated().longValue(), 987654L);
    assertEquals(listResponseBody.getIdentifier(), identifier);
    assertEquals(listResponseBody.getName(), name);
    assertEquals(listResponseBody.getYamlVersion(), "1");
    assertEquals(listResponseBody.getStoreType(), StoreTypeEnum.INLINE);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetPipelinesWithRecentExecutionFailureInfo() {
    RecentExecutionInfoDTO recentExecutionInfoDTO =
        RecentExecutionInfoDTO.builder().planExecutionId("exec1").status(ExecutionStatus.FAILED).build();
    PMSPipelineSummaryResponseDTO pmsPipelineSummaryResponseDTO =
        PMSPipelineSummaryResponseDTO.builder()
            .identifier(identifier)
            .recentExecutionsInfo(Collections.singletonList(recentExecutionInfoDTO))
            .build();
    FailureInfo failureInfo = FailureInfo.newBuilder().setErrorMessage("deploy failed").build();
    PipelineMetadataV2 pipelineMetadataV2 =
        PipelineMetadataV2.builder()
            .recentExecutionInfoList(Collections.singletonList(io.harness.pms.pipeline.RecentExecutionInfo.builder()
                                                                   .planExecutionId("exec1")
                                                                   .failureInfo(failureInfo)
                                                                   .build()))
            .build();
    PipelineListResponseBody listResponseBody =
        PipelinesApiUtils.getPipelines(pmsPipelineSummaryResponseDTO, pipelineMetadataV2);
    RecentExecutionInfo recentExecutionInfo = listResponseBody.getRecentExecutionInfo().get(0);
    assertThat(recentExecutionInfo.getExecutionId()).isEqualTo("exec1");
    assertThat(recentExecutionInfo.getFailureInfo().getMessage()).isEqualTo("deploy failed");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetPipelinesWithFailureInfoHavingNoFailureTypeListDoesNotThrow() {
    RecentExecutionInfoDTO recentExecutionInfoDTO =
        RecentExecutionInfoDTO.builder().planExecutionId("exec1").status(ExecutionStatus.FAILED).build();
    PMSPipelineSummaryResponseDTO pmsPipelineSummaryResponseDTO =
        PMSPipelineSummaryResponseDTO.builder()
            .identifier(identifier)
            .recentExecutionsInfo(Collections.singletonList(recentExecutionInfoDTO))
            .build();
    FailureInfo failureInfo = FailureInfo.newBuilder().setErrorMessage("deploy failed").build();
    PipelineMetadataV2 pipelineMetadataV2 =
        PipelineMetadataV2.builder()
            .recentExecutionInfoList(Collections.singletonList(io.harness.pms.pipeline.RecentExecutionInfo.builder()
                                                                   .planExecutionId("exec1")
                                                                   .failureInfo(failureInfo)
                                                                   .build()))
            .build();

    PipelineListResponseBody listResponseBody =
        PipelinesApiUtils.getPipelines(pmsPipelineSummaryResponseDTO, pipelineMetadataV2);

    assertThat(listResponseBody.getRecentExecutionInfo().get(0).getFailureInfo().getMessage())
        .isEqualTo("deploy failed");
    assertThat(listResponseBody.getRecentExecutionInfo().get(0).getFailureInfo().getFailureTypeList()).isEmpty();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildPipelineValidationUUIDResponseBody() {
    PipelineValidationEvent event = PipelineValidationEvent.builder().uuid("abc1").build();
    assertThat(PipelinesApiUtils.buildPipelineValidationUUIDResponseBody(event).getUuid()).isEqualTo("abc1");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildPipelineValidationResponseBody() {
    PipelineValidationEvent event =
        PipelineValidationEvent.builder()
            .status(ValidationStatus.IN_PROGRESS)
            .result(ValidationResult.builder()
                        .templateValidationResponse(
                            TemplateValidationResponseDTO.builder().validYaml(true).exceptionMessage("message").build())
                        .build())
            .build();
    PipelineValidationResponseBody responseBody = PipelinesApiUtils.buildPipelineValidationResponseBody(event);
    assertThat(responseBody.getStatus()).isEqualTo("IN_PROGRESS");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetCacheResponseMetadataDTO() {
    io.harness.pms.pipeline.CacheResponseMetadataDTO cacheResponseMetadataDTO =
        io.harness.pms.pipeline.CacheResponseMetadataDTO.builder()
            .cacheState(CacheState.VALID_CACHE)
            .ttlLeft(234523)
            .lastUpdatedAt(1234567890L)
            .isSyncEnabled(true)
            .build();
    CacheResponseMetadataDTO cacheMetadataResponse =
        PipelinesApiUtils.getCacheResponseMetadataDTO(cacheResponseMetadataDTO);
    assertEquals(CacheResponseMetadataDTO.CacheStateEnum.VALID_CACHE, cacheMetadataResponse.getCacheState());
    assertThat(cacheMetadataResponse.isIsSyncEnabled()).isTrue();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetResponseBodyWithCacheResponseMetadata() {
    CacheResponse cacheResponse = CacheResponse.builder()
                                      .cacheState(CacheState.VALID_CACHE)
                                      .lastUpdatedAt(lastUpdatedAt)
                                      .isSyncEnabled(true)
                                      .build();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").cacheResponse(cacheResponse).build());

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .yaml("yaml")
                                        .identifier(identifier)
                                        .orgIdentifier("org")

                                        .storeType(StoreType.REMOTE)
                                        .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("account")
                              .orgIdentifier("org")
                              .uniqueId("unique-id")
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .build();
    PipelineGetResponseBody responseBody = PipelinesApiUtils.getGetResponseBody(pipelineEntity, scopeInfo, true);
    assertEquals("yaml", responseBody.getPipelineYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals("org", responseBody.getOrg());
    assertEquals(
        CacheResponseMetadataDTO.CacheStateEnum.VALID_CACHE, responseBody.getCacheResponseMetadata().getCacheState());
    assertEquals(lastUpdatedAt, responseBody.getCacheResponseMetadata().getLastUpdatedAt());
    assertThat(responseBody.getCacheResponseMetadata().isIsSyncEnabled()).isTrue();
  }
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildGovernanceMetadata() {
    io.harness.governance.GovernanceMetadata proto =
        io.harness.governance.GovernanceMetadata.newBuilder()
            .setDeny(false)
            .setStatus("pass")
            .addDetails(
                PolicySetMetadata.newBuilder().setIdentifier("id").setPolicySetName("name").setStatus("pass").build())
            .build();
    GovernanceMetadata governanceMetadata = PipelinesApiUtils.buildGovernanceMetadataFromProto(proto);
    assertThat(governanceMetadata.getStatus()).isEqualTo(GovernanceStatus.PASS);
    assertThat(governanceMetadata.isDeny()).isFalse();
    assertThat(governanceMetadata.getMessage()).isNullOrEmpty();
    List<PolicySet> policySets = governanceMetadata.getPolicySets();
    assertThat(policySets).hasSize(1);
    PolicySet policySet = policySets.get(0);
    assertThat(policySet.getStatus()).isEqualTo(GovernanceStatus.PASS);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testPublicAccessResponseWithNoError() {
    PublicAccessResponse response = PublicAccessResponse.builder().isPublic(true).errorMessage(null).build();
    io.harness.spec.server.pipeline.v1.model.PublicAccessResponse publicAccessResponse =
        PipelinesApiUtils.toPublicAccessResponse(response);
    assertThat(publicAccessResponse.isIsPublic()).isTrue();
    assertThat(publicAccessResponse.getError()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testPublicAccessResponseWithError() {
    PublicAccessResponse response =
        PublicAccessResponse.builder().isPublic(false).errorMessage("Error Message").build();
    io.harness.spec.server.pipeline.v1.model.PublicAccessResponse publicAccessResponse =
        PipelinesApiUtils.toPublicAccessResponse(response);
    assertThat(publicAccessResponse.isIsPublic()).isFalse();
    assertThat(publicAccessResponse.getError()).isNotEmpty();
    assertThat(publicAccessResponse.getError()).isEqualTo("Error Message");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetPipelineSortingWithNullField() {
    assertThat(getPipelineSorting(null, null)).isNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetPipelineSortingWithOrderButNoField() {
    assertThatThrownBy(() -> getPipelineSorting(null, "DESC"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Order of sorting provided without Sort field");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetPipelineSortingByName() {
    assertThat(getPipelineSorting("name", "ASC")).containsExactly("name,ASC");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetPipelineSortingByUpdatedMapsToLastUpdatedAt() {
    assertThat(getPipelineSorting("updated", "DESC")).containsExactly("lastUpdatedAt,DESC");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetPipelineSortingByLastExecutedMapsToExecutionSummaryInfo() {
    assertThat(getPipelineSorting("last_executed", "DESC"))
        .containsExactly(PipelineEntityKeys.lastExecutedAt + ",DESC");
    assertThat(getPipelineSorting("last_executed", "ASC")).containsExactly(PipelineEntityKeys.lastExecutedAt + ",ASC");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetPipelineSortingDefaultsToDescWhenOrderMissing() {
    assertThat(getPipelineSorting("last_executed", null)).containsExactly(PipelineEntityKeys.lastExecutedAt + ",DESC");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetPipelineSortingRejectsUnknownField() {
    assertThatThrownBy(() -> getPipelineSorting("createdAt", "DESC"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("name / updated / last_executed");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetPipelineSortingRejectsInvalidOrder() {
    assertThatThrownBy(() -> getPipelineSorting("last_executed", "RANDOM"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("ASC / DESC");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetSortingDoesNotAcceptLastExecuted() {
    // getSorting() (used by input sets) must continue to reject `last_executed` because
    // InputSetEntity does not have an executionSummaryInfo.lastExecutionTs field.
    assertThatThrownBy(() -> PipelinesApiUtils.getSorting("last_executed", "DESC"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("name / updated");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testBuildMoveConfigOperationDTOMapsHarnessCodeRepo() {
    GitMoveDetails gitMoveDetails = new GitMoveDetails();
    gitMoveDetails.setRepoName("account.pr-tests");
    gitMoveDetails.setBranchName("master");
    gitMoveDetails.setFilePath(".harness/pipelines/test.yaml");
    gitMoveDetails.setIsHarnessCodeRepo(true);

    MoveConfigOperationDTO result =
        buildMoveConfigOperationDTO(gitMoveDetails, MoveConfigOperationType.INLINE_TO_REMOTE);

    assertThat(result.getIsHarnessCodeRepo()).isTrue();
    assertThat(result.getRepoName()).isEqualTo("account.pr-tests");
    assertThat(result.getBranch()).isEqualTo("master");
    assertThat(result.getFilePath()).isEqualTo(".harness/pipelines/test.yaml");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveValidateOpaEnrichment_usesExplicitCommitSha_cleanStatus() {
    String commitSha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    String blobObjectId = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    long storedEvaluatedAt = 1700000000000L;
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .identifier("pipe1")
                                .storeType(StoreType.REMOTE)
                                .objectIdOfYaml(blobObjectId)
                                .build();
    io.harness.governance.GovernanceMetadata gm =
        io.harness.governance.GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineOpaStatusHandler handler = mock(PipelineOpaStatusHandler.class);
    OpaOnSaveStatusDTO storedRecord = OpaOnSaveStatusDTO.builder()
                                          .evaluatedAt(storedEvaluatedAt)
                                          .lastValidCommitId(commitSha)
                                          .status(OpaGitxStatus.SUCCESS)
                                          .build();
    when(handler.get(any(), eq("acc"), eq(commitSha))).thenReturn(java.util.Optional.of(storedRecord));

    java.util.Optional<PipelinesApiUtils.OpaOnSaveEnrichmentResult> result =
        resolveValidateOpaEnrichment(entity, "acc", gm, handler, commitSha, null, null);

    assertThat(result).isPresent();
    OpaOnSaveStatusDTO dto = result.get().getOpaStatus();
    assertThat(dto.getEvaluatedAtCommitId()).isEqualTo(commitSha);
    assertThat(dto.getEvaluatedAtCommitId()).isNotEqualTo(blobObjectId);
    assertThat(dto.getLastValidCommitId()).isEqualTo(commitSha);
    assertThat(dto.getStatus()).isEqualTo(OpaGitxStatus.SUCCESS);
    assertThat(dto.getEvaluatedAt()).isEqualTo(storedEvaluatedAt);
    assertThat(result.get().getCurrentCommitId()).isEqualTo(commitSha);
    verify(handler, never()).handleUiApiSave(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveValidateOpaEnrichment_stableEvaluatedAt_acrossPolls() {
    String commitSha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    long storedEvaluatedAt = 1700000000000L;
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .identifier("pipe1")
                                .storeType(StoreType.REMOTE)
                                .build();
    io.harness.governance.GovernanceMetadata gm =
        io.harness.governance.GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineOpaStatusHandler handler = mock(PipelineOpaStatusHandler.class);
    OpaOnSaveStatusDTO storedRecord = OpaOnSaveStatusDTO.builder()
                                          .evaluatedAt(storedEvaluatedAt)
                                          .lastValidCommitId(commitSha)
                                          .status(OpaGitxStatus.SUCCESS)
                                          .build();
    when(handler.get(any(), eq("acc"), eq(commitSha))).thenReturn(java.util.Optional.of(storedRecord));

    java.util.Optional<PipelinesApiUtils.OpaOnSaveEnrichmentResult> result1 =
        resolveValidateOpaEnrichment(entity, "acc", gm, handler, commitSha, null, null);
    java.util.Optional<PipelinesApiUtils.OpaOnSaveEnrichmentResult> result2 =
        resolveValidateOpaEnrichment(entity, "acc", gm, handler, commitSha, null, null);

    assertThat(result1).isPresent();
    assertThat(result2).isPresent();
    assertThat(result1.get().getOpaStatus().getEvaluatedAt()).isEqualTo(result2.get().getOpaStatus().getEvaluatedAt());
    assertThat(result1.get().getOpaStatus().getEvaluatedAt()).isEqualTo(storedEvaluatedAt);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveValidateOpaEnrichment_errorStatus_usesDbLastValidCommitId() {
    String commitSha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    String blobObjectId = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    String dbLastValid = "f0e1d2c3b4a5f0e1d2c3b4a5f0e1d2c3b4a5f0e1";
    long storedEvaluatedAt = 1700000000000L;
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .identifier("pipe1")
                                .storeType(StoreType.REMOTE)
                                .objectIdOfYaml(blobObjectId)
                                .build();
    io.harness.governance.GovernanceMetadata gm =
        io.harness.governance.GovernanceMetadata.newBuilder().setDeny(true).build();
    PipelineOpaStatusHandler handler = mock(PipelineOpaStatusHandler.class);
    OpaOnSaveStatusDTO existingRecord = OpaOnSaveStatusDTO.builder()
                                            .lastValidCommitId(dbLastValid)
                                            .status(OpaGitxStatus.SUCCESS)
                                            .evaluatedAt(storedEvaluatedAt)
                                            .build();
    when(handler.get(any(), eq("acc"), eq(commitSha))).thenReturn(java.util.Optional.of(existingRecord));

    java.util.Optional<PipelinesApiUtils.OpaOnSaveEnrichmentResult> result =
        resolveValidateOpaEnrichment(entity, "acc", gm, handler, commitSha, null, null);

    assertThat(result).isPresent();
    OpaOnSaveStatusDTO dto = result.get().getOpaStatus();
    assertThat(dto.getEvaluatedAtCommitId()).isEqualTo(commitSha);
    assertThat(dto.getEvaluatedAtCommitId()).isNotEqualTo(blobObjectId);
    assertThat(dto.getLastValidCommitId()).isEqualTo(dbLastValid);
    assertThat(dto.getStatus()).isEqualTo(OpaGitxStatus.ERROR);
    assertThat(dto.getEvaluatedAt()).isEqualTo(storedEvaluatedAt);
    verify(handler, never()).handleUiApiSave(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveValidateOpaEnrichment_errorStatus_noDbRecord_lastValidIsNull() {
    String commitSha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    String blobObjectId = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .identifier("pipe1")
                                .storeType(StoreType.REMOTE)
                                .objectIdOfYaml(blobObjectId)
                                .build();
    io.harness.governance.GovernanceMetadata gm =
        io.harness.governance.GovernanceMetadata.newBuilder().setDeny(true).build();
    PipelineOpaStatusHandler handler = mock(PipelineOpaStatusHandler.class);
    when(handler.get(any(), eq("acc"), eq(commitSha))).thenReturn(java.util.Optional.empty());

    java.util.Optional<PipelinesApiUtils.OpaOnSaveEnrichmentResult> result =
        resolveValidateOpaEnrichment(entity, "acc", gm, handler, commitSha, null, null);

    assertThat(result).isPresent();
    OpaOnSaveStatusDTO dto = result.get().getOpaStatus();
    assertThat(dto.getEvaluatedAtCommitId()).isEqualTo(commitSha);
    assertThat(dto.getEvaluatedAtCommitId()).isNotEqualTo(blobObjectId);
    assertThat(dto.getLastValidCommitId()).isNull();
    assertThat(dto.getStatus()).isEqualTo(OpaGitxStatus.ERROR);
    assertThat(dto.getEvaluatedAt()).isNull();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveValidateOpaEnrichment_inlineEntity_returnsEmpty() {
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .identifier("pipe1")
                                .storeType(StoreType.INLINE)
                                .build();
    io.harness.governance.GovernanceMetadata gm =
        io.harness.governance.GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineOpaStatusHandler handler = mock(PipelineOpaStatusHandler.class);

    java.util.Optional<PipelinesApiUtils.OpaOnSaveEnrichmentResult> result =
        resolveValidateOpaEnrichment(entity, "acc", gm, handler, "a1b2c3d4", null, null);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveValidateOpaEnrichment_nullGovernanceMetadata_returnsEmpty() {
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .identifier("pipe1")
                                .storeType(StoreType.REMOTE)
                                .build();
    PipelineOpaStatusHandler handler = mock(PipelineOpaStatusHandler.class);

    java.util.Optional<PipelinesApiUtils.OpaOnSaveEnrichmentResult> result =
        resolveValidateOpaEnrichment(entity, "acc", null, handler, "a1b2c3d4", null, null);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveValidateOpaEnrichment_nullCommitId_degradesGracefully() {
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .identifier("pipe1")
                                .storeType(StoreType.REMOTE)
                                .objectIdOfYaml("deadbeef")
                                .build();
    io.harness.governance.GovernanceMetadata gm =
        io.harness.governance.GovernanceMetadata.newBuilder().setDeny(true).build();
    PipelineOpaStatusHandler handler = mock(PipelineOpaStatusHandler.class);
    when(handler.get(any(), eq("acc"), eq((String) null))).thenReturn(java.util.Optional.empty());

    java.util.Optional<PipelinesApiUtils.OpaOnSaveEnrichmentResult> result =
        resolveValidateOpaEnrichment(entity, "acc", gm, handler, null, null, null);

    assertThat(result).isPresent();
    OpaOnSaveStatusDTO dto = result.get().getOpaStatus();
    assertThat(dto.getEvaluatedAtCommitId()).isNull();
    assertThat(dto.getLastValidCommitId()).isNull();
    assertThat(dto.getStatus()).isEqualTo(OpaGitxStatus.ERROR);
    assertThat(dto.getEvaluatedAt()).isNull();
    verify(handler, never()).handleUiApiSave(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveValidateOpaEnrichment_preCapturedFields_noGitThreadLocal_success() {
    String commitSha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    long capturedEvalTime = 1700000000000L;
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .identifier("pipe1")
                                .storeType(StoreType.REMOTE)
                                .build();
    io.harness.governance.GovernanceMetadata gm =
        io.harness.governance.GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineOpaStatusHandler handler = mock(PipelineOpaStatusHandler.class);

    java.util.Optional<PipelinesApiUtils.OpaOnSaveEnrichmentResult> result =
        resolveValidateOpaEnrichment(entity, "acc", gm, handler, commitSha, capturedEvalTime, commitSha);

    assertThat(result).isPresent();
    OpaOnSaveStatusDTO dto = result.get().getOpaStatus();
    assertThat(dto.getEvaluatedAt()).isEqualTo(capturedEvalTime);
    assertThat(dto.getLastValidCommitId()).isEqualTo(commitSha);
    assertThat(dto.getStatus()).isEqualTo(OpaGitxStatus.SUCCESS);
    assertThat(dto.getEvaluatedAtCommitId()).isEqualTo(commitSha);
    verify(handler, never()).get(any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveValidateOpaEnrichment_preCapturedFields_errorStatus_usesPreCapturedLastValid() {
    String commitSha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    String priorCleanCommit = "f0e1d2c3b4a5f0e1d2c3b4a5f0e1d2c3b4a5f0e1";
    long capturedEvalTime = 1700000000000L;
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .identifier("pipe1")
                                .storeType(StoreType.REMOTE)
                                .build();
    io.harness.governance.GovernanceMetadata gm =
        io.harness.governance.GovernanceMetadata.newBuilder().setDeny(true).build();
    PipelineOpaStatusHandler handler = mock(PipelineOpaStatusHandler.class);

    java.util.Optional<PipelinesApiUtils.OpaOnSaveEnrichmentResult> result =
        resolveValidateOpaEnrichment(entity, "acc", gm, handler, commitSha, capturedEvalTime, priorCleanCommit);

    assertThat(result).isPresent();
    OpaOnSaveStatusDTO dto = result.get().getOpaStatus();
    assertThat(dto.getEvaluatedAt()).isEqualTo(capturedEvalTime);
    assertThat(dto.getLastValidCommitId()).isEqualTo(priorCleanCommit);
    assertThat(dto.getStatus()).isEqualTo(OpaGitxStatus.ERROR);
    verify(handler, never()).get(any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveValidateOpaEnrichment_preCapturedFields_stableAcrossRepeatedPolls() {
    String commitSha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    long capturedEvalTime = 1700000000000L;
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId("acc")
                                .orgIdentifier("org")
                                .projectIdentifier("proj")
                                .identifier("pipe1")
                                .storeType(StoreType.REMOTE)
                                .build();
    io.harness.governance.GovernanceMetadata gm =
        io.harness.governance.GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineOpaStatusHandler handler = mock(PipelineOpaStatusHandler.class);

    java.util.Optional<PipelinesApiUtils.OpaOnSaveEnrichmentResult> result1 =
        resolveValidateOpaEnrichment(entity, "acc", gm, handler, commitSha, capturedEvalTime, commitSha);
    java.util.Optional<PipelinesApiUtils.OpaOnSaveEnrichmentResult> result2 =
        resolveValidateOpaEnrichment(entity, "acc", gm, handler, commitSha, capturedEvalTime, commitSha);

    assertThat(result1).isPresent();
    assertThat(result2).isPresent();
    assertThat(result1.get().getOpaStatus().getEvaluatedAt()).isEqualTo(result2.get().getOpaStatus().getEvaluatedAt());
    assertThat(result1.get().getOpaStatus().getEvaluatedAt()).isEqualTo(capturedEvalTime);
    verify(handler, never()).get(any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToV1OpaOnSaveStatus_nullEvaluatedAt_noNPE() {
    OpaOnSaveStatusDTO dto = OpaOnSaveStatusDTO.builder()
                                 .status(OpaGitxStatus.SUCCESS)
                                 .evaluatedAtCommitId("abc123")
                                 .lastValidCommitId("abc123")
                                 .evaluatedAt(null)
                                 .build();
    io.harness.spec.server.pipeline.v1.model.OpaOnSaveStatus v1 = PipelinesApiUtils.toV1OpaOnSaveStatus(dto, "abc123");
    assertThat(v1.getEvaluatedAt()).isNull();
    assertThat(v1.getEvaluatedAtCommitId()).isEqualTo("abc123");
  }
}

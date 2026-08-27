/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.JATIN;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.publicaccess.PublicAccessClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionNode;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.dto.OrchestrationAdjacencyListDTO;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.JsonSchemaValidationException;
import io.harness.exception.UnavailableFeatureException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.execution.NodeExecution;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitaware.helper.GitxRefreshMetrics;
import io.harness.gitaware.helper.PipelineMoveConfigRequestDTO;
import io.harness.gitsync.GitMetadataUpdateRequestInfoDTO;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.scm.beans.ScmClearCacheResponse;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.helpers.PipelineCloneHelper;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.ClonePipelineDTO;
import io.harness.pms.pipeline.ExpandedPipelineJsonDTO;
import io.harness.pms.pipeline.ForceImportPipelineRequestDTO;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.MoveConfigOperationType;
import io.harness.pms.pipeline.MoveConfigResponse;
import io.harness.pms.pipeline.PMSGitUpdateResponseDTO;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PMSPipelineRemoteRepoInfo;
import io.harness.pms.pipeline.PMSPipelineRemoteRepoListResponse;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.PipelineValidationResponseDTO;
import io.harness.pms.pipeline.RemotePipelinesResponseDTO;
import io.harness.pms.pipeline.SourceIdentifierConfig;
import io.harness.pms.pipeline.StepCategory;
import io.harness.pms.pipeline.StepPalleteFilterWrapper;
import io.harness.pms.pipeline.TemplateValidationResponseDTO;
import io.harness.pms.pipeline.TemplatesResolvedPipelineResponseDTO;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.pms.pipeline.mappers.NodeExecutionToExecutioNodeMapper;
import io.harness.pms.pipeline.mappers.dto.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.helper.PipelinePublicAccessHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.intfc.PipelineCRUDResult;
import io.harness.pms.pipeline.service.intfc.PipelineGetResult;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationParams;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.template.PipelineRefreshResource;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.pms.variables.VariableCreatorMergeService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationUUIDResponseBody;
import io.harness.steps.template.TemplateStepNode;
import io.harness.steps.template.stage.TemplateStageNode;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public class PipelineResourceTest extends CategoryTest {
  PipelineResourceImpl pipelineResource;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock SchemaFetcher schemaFetcher;
  @Mock YamlSchemaValidator yamlSchemaValidator;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock NodeExecutionToExecutioNodeMapper nodeExecutionToExecutioNodeMapper;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock VariableCreatorMergeService variableCreatorMergeService;
  @Mock PipelineCloneHelper pipelineCloneHelper;
  @Mock PmsFeatureFlagHelper featureFlagHelper;
  @Mock PipelineMetadataService pipelineMetadataService;
  @Mock PipelineAsyncValidationService pipelineAsyncValidationService;
  @Mock PipelineRefreshService pipelineRefreshService;
  @Mock AccessControlClient accessControlClient;
  @Mock PublicAccessClient publicAccessClient;
  @Mock PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock PipelinePublicAccessHelper pipelinePublicAccessHelper;
  @Mock PipelineOpaStatusHandler pipelineOpaStatusHandler;
  @Mock io.harness.gitaware.helper.GitAwareEntityHelper gitAwareEntityHelper;
  @Mock GitxRefreshMetrics gitxRefreshMetrics;
  @InjectMocks PipelineRefreshResource pipelineRefreshResource;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "basichttpFail";
  private final String PIPELINE_NAME = "basichttpFail";
  private final String STAGE = "qaStage";
  private final String CONNECTOR_REF = "connectorRef";
  private final String REPO = "repo";
  private String yaml;
  private String simplifiedYaml;
  private String simplifiedYamlWithoutName;

  PipelineEntity entity;
  PipelineEntity remoteEntity;
  PipelineEntity simplifiedEntity;
  PipelineEntity entityWithVersion;
  PipelineEntity simplifiedEntityWithVersion;
  PipelineExecutionSummaryEntity executionSummaryEntity;
  OrchestrationGraphDTO orchestrationGraph;
  EntityGitDetails entityGitDetails;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    when(gitxRefreshMetrics.executeWithMetrics(any(), any()))
        .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
    pipelineResource =
        new PipelineResourceImpl(pmsPipelineService, pmsPipelineServiceHelper, schemaFetcher, yamlSchemaValidator,
            nodeExecutionService, nodeExecutionToExecutioNodeMapper, pipelineTemplateHelper, featureFlagHelper,
            variableCreatorMergeService, pipelineCloneHelper, pipelineMetadataService, pipelineAsyncValidationService,
            accessControlClient, publicAccessClient, pipelineSplitPermissionsHelper, scopeResolutionHelper,
            pipelinePublicAccessHelper, pipelineOpaStatusHandler, gitAwareEntityHelper, gitxRefreshMetrics);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "failure-strategy.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    entity = PipelineEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(PIPELINE_IDENTIFIER)
                 .name(PIPELINE_IDENTIFIER)
                 .yaml(yaml)
                 .isDraft(false)
                 .allowStageExecutions(false)
                 .build();
    remoteEntity = PipelineEntity.builder()
                       .accountId(ACCOUNT_ID)
                       .orgIdentifier(ORG_IDENTIFIER)
                       .projectIdentifier(PROJ_IDENTIFIER)
                       .identifier(PIPELINE_IDENTIFIER)
                       .name(PIPELINE_IDENTIFIER)
                       .yaml(yaml)
                       .connectorRef(CONNECTOR_REF)
                       .repo(REPO)
                       .isDraft(false)
                       .allowStageExecutions(false)
                       .build();

    filename = "simplified-pipeline.yaml";
    simplifiedYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    simplifiedYamlWithoutName = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("pipeline-without-name-v1.yaml")), StandardCharsets.UTF_8);
    simplifiedEntity = PipelineEntity.builder()
                           .accountId(ACCOUNT_ID)
                           .orgIdentifier(ORG_IDENTIFIER)
                           .projectIdentifier(PROJ_IDENTIFIER)
                           .identifier(PIPELINE_IDENTIFIER)
                           .name(PIPELINE_IDENTIFIER)
                           .yaml(simplifiedYaml)
                           .isDraft(false)
                           .harnessVersion(HarnessYamlVersion.V1)
                           .build();

    entityGitDetails = EntityGitDetails.builder()
                           .branch("branch")
                           .repoIdentifier("repo")
                           .filePath("file.yaml")
                           .rootFolder("root/.harness/")
                           .build();

    entityWithVersion = PipelineEntity.builder()
                            .accountId(ACCOUNT_ID)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .identifier(PIPELINE_IDENTIFIER)
                            .name(PIPELINE_IDENTIFIER)
                            .yaml(yaml)
                            .stageCount(1)
                            .stageName(STAGE)
                            .version(1L)
                            .allowStageExecutions(false)
                            .build();

    simplifiedEntityWithVersion = PipelineEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .identifier(PIPELINE_IDENTIFIER)
                                      .name(PIPELINE_IDENTIFIER)
                                      .yaml(simplifiedYaml)
                                      .isDraft(false)
                                      .harnessVersion(HarnessYamlVersion.V1)
                                      .version(1L)
                                      .build();

    String PLAN_EXECUTION_ID = "planId";
    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(ACCOUNT_ID)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .projectIdentifier(PROJ_IDENTIFIER)
                                 .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                 .planExecutionId(PLAN_EXECUTION_ID)
                                 .name(PLAN_EXECUTION_ID)
                                 .runSequence(0)
                                 .entityGitDetails(entityGitDetails)
                                 .build();

    String STAGE_NODE_ID = "stageNodeId";
    orchestrationGraph = OrchestrationGraphDTO.builder()
                             .planExecutionId(PLAN_EXECUTION_ID)
                             .rootNodeIds(Collections.singletonList(STAGE_NODE_ID))
                             .adjacencyList(OrchestrationAdjacencyListDTO.builder()
                                                .graphVertexMap(Collections.emptyMap())
                                                .adjacencyMap(Collections.emptyMap())
                                                .build())
                             .build();

    doReturn(ScmClearCacheResponse.builder().status(true).failedFilePaths(Collections.emptyList()).build())
        .when(gitAwareEntityHelper)
        .clearCache(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipeline() {
    doReturn(false).when(featureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(entityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                 .build())
        .when(pmsPipelineService)
        .validateAndCreatePipeline(any(PipelineEntity.class), anyBoolean(), any(), anyBoolean());
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(entity, BOOLEAN_FALSE_VALUE);
    ResponseDTO<String> identifier = pipelineResource.createPipeline(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, PIPELINE_NAME, null, null, null, null, yaml, null);
    assertThat(identifier.getData()).isNotEmpty();
    assertThat(identifier.getData()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetPipelineAttributes() {
    List<String> pipelineIds = List.of("id1", "id2");
    Criteria criteria = Criteria.where("key").is("val");
    doReturn(criteria)
        .when(pmsPipelineServiceHelper)
        .formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null,
            PipelineFilterPropertiesDto.builder().pipelineIdentifiers(pipelineIds).build(), false, null, null, null,
            false);
    doReturn(false).when(pmsPipelineServiceHelper).isParentIdQueryingEnabled(ACCOUNT_ID);
    doReturn(new PageImpl<>(List.of(PipelineEntity.builder()
                                        .identifier("id1")
                                        .tags(Collections.singletonList(NGTag.builder().key("T1").value("V1").build()))
                                        .build(),
                 PipelineEntity.builder().identifier("id2").tags(Collections.emptyList()).build())))
        .when(pmsPipelineService)
        .list(
            criteria, Pageable.ofSize(20).withPage(0), ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false, null, false);

    ResponseDTO<List<Map<String, String>>> responseDTO =
        pipelineResource.getPipelineAttributes(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, pipelineIds, 0, 20, null);
    assertEquals(responseDTO.getData().get(0).get("tags"), "T1:V1");
    assertEquals(responseDTO.getData().get(1).get("tags"), "");

    assertThatThrownBy(()
                           -> pipelineResource.getPipelineAttributes(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, pipelineIds, 0, 1, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The pipeline identifiers count 2 can not be more than the page size 1.");
    assertThatThrownBy(
        () -> pipelineResource.getPipelineAttributes(null, ORG_IDENTIFIER, PROJ_IDENTIFIER, pipelineIds, 0, 20, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
        () -> pipelineResource.getPipelineAttributes(ACCOUNT_ID, null, PROJ_IDENTIFIER, pipelineIds, 0, 20, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
        () -> pipelineResource.getPipelineAttributes(ACCOUNT_ID, ORG_IDENTIFIER, null, pipelineIds, 0, 20, null))
        .isInstanceOf(NullPointerException.class);

    doReturn(new PageImpl<>(List.of(PipelineEntity.builder()
                                        .identifier("id1")
                                        .tags(Collections.singletonList(NGTag.builder().key("T1").value("V1").build()))
                                        .build())))
        .when(pmsPipelineService)
        .list(
            criteria, Pageable.ofSize(20).withPage(0), ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false, null, false);
    responseDTO =
        pipelineResource.getPipelineAttributes(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, pipelineIds, 0, 20, null);
    assertEquals(responseDTO.getData().get(0).get("tags"), "T1:V1");
    assertThat(responseDTO.getData().get(1).isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreatePipelineV2() {
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(entityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(true).build())
                 .build())
        .when(pmsPipelineService)
        .validateAndCreatePipeline(any(PipelineEntity.class), anyBoolean(), any(), anyBoolean());
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.createPipelineV2(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, null, null, yaml, false, null);
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNotNull();
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipelineV2WithSuccess() {
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(entityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                 .build())
        .when(pmsPipelineService)
        .validateAndCreatePipeline(any(PipelineEntity.class), anyBoolean(), any(), anyBoolean());
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.createPipelineV2(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, null, PIPELINE_NAME, null, null, null, null, null, yaml, false, null);
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNotNull();
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isFalse();
    assertThat(responseDTO.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testClonePipelineWithAccessFailure() {
    ClonePipelineDTO dummy = ClonePipelineDTO.builder().build();
    doThrow(new AccessDeniedException("denied", null)).when(pipelineCloneHelper).checkAccess(dummy, ACCOUNT_ID);
    assertThatThrownBy(() -> pipelineResource.clonePipeline(ACCOUNT_ID, null, null, dummy))
        .hasMessage("denied")
        .isInstanceOf(AccessDeniedException.class);
    verify(pmsPipelineService, times(0)).validateAndClonePipeline(any(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testClonePipeline() {
    ClonePipelineDTO dummy = ClonePipelineDTO.builder()
                                 .sourceConfig(SourceIdentifierConfig.builder()
                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                   .projectIdentifier(PROJ_IDENTIFIER)
                                                   .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                   .build())
                                 .build();
    PipelineSaveResponse dummyResponse = PipelineSaveResponse.builder().identifier("id").build();
    doReturn(null).when(scopeResolutionHelper).getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(dummyResponse).when(pmsPipelineService).validateAndClonePipeline(dummy, ACCOUNT_ID, null, false);
    ResponseDTO<PipelineSaveResponse> response = pipelineResource.clonePipeline(ACCOUNT_ID, null, null, dummy);
    assertThat(response.getData()).isEqualTo(dummyResponse);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipeline() {
    doReturn(PipelineGetResult.builder().pipelineEntity(Optional.of(entityWithVersion)).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false,
            false, false, null, false, true);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO).when(pipelineTemplateHelper).resolveTemplateRefsInPipeline(any(), any(), any());
    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, false, false, BOOLEAN_FALSE_VALUE, null);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(responseDTO.getData().getYamlSchemaErrorWrapper()).isNull();
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNull();
    assertThat(responseDTO.getData().getResolvedTemplatesPipelineYaml()).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineWithAsyncValidations() {
    doReturn(PipelineGetResult.builder()
                 .pipelineEntity(Optional.of(entityWithVersion))
                 .asyncValidationUUID("asyncUuid")
                 .build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false,
            false, true, null, false, true);
    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, false, true, BOOLEAN_FALSE_VALUE, null);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(responseDTO.getData().getYamlSchemaErrorWrapper()).isNull();
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNull();
    assertThat(responseDTO.getData().getValidationUuid()).isEqualTo("asyncUuid");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetPipelineFromFallbackBranch() {
    PipelineEntity pipelineEntityCreatedInNonDefaultBranch = PipelineEntity.builder()
                                                                 .accountId(ACCOUNT_ID)
                                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                                 .identifier(PIPELINE_IDENTIFIER)
                                                                 .name(PIPELINE_IDENTIFIER)
                                                                 .yaml(yaml)
                                                                 .stageCount(1)
                                                                 .stageName(STAGE)
                                                                 .version(1L)
                                                                 .allowStageExecutions(false)
                                                                 .build();
    doReturn(PipelineGetResult.builder().pipelineEntity(Optional.of(pipelineEntityCreatedInNonDefaultBranch)).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, true,
            false, false, null, false, true);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO).when(pipelineTemplateHelper).resolveTemplateRefsInPipeline(any(), any(), any());
    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, true, false, BOOLEAN_FALSE_VALUE, null);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(responseDTO.getData().getYamlSchemaErrorWrapper()).isNull();
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNull();
    assertThat(responseDTO.getData().getResolvedTemplatesPipelineYaml()).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetPipelineLoadFromCache() {
    PipelineEntity pipelineEntityCreatedInNonDefaultBranch = PipelineEntity.builder()
                                                                 .accountId(ACCOUNT_ID)
                                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                                 .identifier(PIPELINE_IDENTIFIER)
                                                                 .name(PIPELINE_IDENTIFIER)
                                                                 .yaml(yaml)
                                                                 .stageCount(1)
                                                                 .stageName(STAGE)
                                                                 .version(1L)
                                                                 .allowStageExecutions(false)
                                                                 .build();

    doReturn(PipelineGetResult.builder().pipelineEntity(Optional.of(pipelineEntityCreatedInNonDefaultBranch)).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false,
            true, false, null, false, true);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO).when(pipelineTemplateHelper).resolveTemplateRefsInPipeline(any(), any(), any());
    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, false, false, "true", null);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(responseDTO.getData().getYamlSchemaErrorWrapper()).isNull();
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNull();
    assertThat(responseDTO.getData().getResolvedTemplatesPipelineYaml()).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshAndGetPipeline_DelegatesToServiceAndFetchesFresh() {
    doReturn(PipelineGetResult.builder().pipelineEntity(Optional.of(entityWithVersion)).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false,
            false, true, null, false, true);

    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.refreshAndGetPipeline(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "main", null);

    verify(pmsPipelineService, times(1))
        .refreshGitFileCache(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "main", null);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshAndGetPipeline_ServiceExceptionPropagates_FeatureFlagDisabled() {
    doThrow(new UnavailableFeatureException("Cache refresh for pipeline is not enabled. PIPE_GITX_FORCE_REFRESH"))
        .when(pmsPipelineService)
        .refreshGitFileCache(any(), any(), any(), any(), any(), any());
    assertThatThrownBy(()
                           -> pipelineResource.refreshAndGetPipeline(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "main", null))
        .isInstanceOf(UnavailableFeatureException.class)
        .hasMessageContaining("PIPE_GITX_FORCE_REFRESH");
    verify(pmsPipelineService, never())
        .getAndValidatePipeline(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshAndGetPipeline_ServiceExceptionPropagates_InlineEntity() {
    doThrow(new InvalidRequestException("Cache refresh applies only to remote Git-backed pipelines."))
        .when(pmsPipelineService)
        .refreshGitFileCache(any(), any(), any(), any(), eq("main"), any());

    assertThatThrownBy(()
                           -> pipelineResource.refreshAndGetPipeline(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "main", null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("remote Git-backed pipelines");

    verify(pmsPipelineService, never())
        .getAndValidatePipeline(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshAndGetPipeline_ServiceExceptionPropagates_MissingBranch() {
    doThrow(new InvalidRequestException("A valid git branch is required to refresh cache for pipeline"))
        .when(pmsPipelineService)
        .refreshGitFileCache(any(), any(), any(), any(), eq(null), any());

    assertThatThrownBy(()
                           -> pipelineResource.refreshAndGetPipeline(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("A valid git branch is required");

    verify(pmsPipelineService, never())
        .getAndValidatePipeline(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineWithUnresolvedTemplates() {
    doReturn(PipelineGetResult.builder().pipelineEntity(Optional.of(entityWithVersion)).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false,
            false, false, null, false, true);
    doThrow(new InvalidRequestException("random exception"))
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(any(), any(), any());
    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, false, false, BOOLEAN_FALSE_VALUE, null);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(responseDTO.getData().getYamlSchemaErrorWrapper()).isNull();
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNull();
    assertThat(responseDTO.getData().getResolvedTemplatesPipelineYaml()).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineWithInvalidYAML() {
    YamlSchemaErrorWrapperDTO errorWrapper =
        YamlSchemaErrorWrapperDTO.builder()
            .schemaErrors(Collections.singletonList(YamlSchemaErrorDTO.builder().fqn("fqn").message("msg").build()))
            .build();
    doThrow(new InvalidYamlException("errorMsg", null, errorWrapper, yaml))
        .when(pmsPipelineService)
        .getAndValidatePipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false,
            false, false, null, false, true);

    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, false, false, BOOLEAN_FALSE_VALUE, null);
    PMSPipelineResponseDTO data = responseDTO.getData();
    assertThat(data.getEntityValidityDetails().isValid()).isFalse();
    assertThat(data.getEntityValidityDetails().getInvalidYaml()).isEqualTo(yaml);
    assertThat(data.getYamlPipeline()).isEqualTo(yaml);
    assertThat(data.getYamlSchemaErrorWrapper()).isEqualTo(errorWrapper);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineWithGovernanceErrors() {
    GovernanceMetadata governanceMetadata =
        GovernanceMetadata.newBuilder().setDeny(true).setAccountId(ACCOUNT_ID).build();
    doThrow(new PolicyEvaluationFailureException("errorMsg", governanceMetadata, yaml))
        .when(pmsPipelineService)
        .getAndValidatePipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false,
            false, false, null, false, true);

    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, false, false, BOOLEAN_FALSE_VALUE, null);
    PMSPipelineResponseDTO data = responseDTO.getData();
    assertThat(data.getEntityValidityDetails().isValid()).isTrue();
    assertThat(data.getEntityValidityDetails().getInvalidYaml()).isEqualTo(yaml);
    assertThat(data.getYamlPipeline()).isEqualTo(yaml);
    assertThat(data.getGovernanceMetadata()).isEqualTo(governanceMetadata);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetPipelineWithInvalidPipelineId() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";

    doReturn(PipelineGetResult.builder().pipelineEntity(Optional.empty()).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, false, false,
            false, false, false, null, false, true);

    assertThatThrownBy(()
                           -> pipelineResource.getPipelineByIdentifier(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               incorrectPipelineIdentifier, null, true, false, false, BOOLEAN_FALSE_VALUE, null))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage(String.format(
            "Pipeline with the given ID: %s does not exist or has been deleted", incorrectPipelineIdentifier));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineWithWrongIdentifier() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml, BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0);
    assertThatThrownBy(()
                           -> pipelineResource.updatePipeline(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               incorrectPipelineIdentifier, null, null, null, null, yaml, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Expected Pipeline identifier in YAML to be [notTheIdentifierWeNeed], but was [basichttpFail]");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipeline() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(entityWithVersion).build();
    doReturn(pipelineCRUDResult)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(
            any(PipelineEntity.class), any(ChangeType.class), anyBoolean(), anyBoolean(), any(), anyBoolean());
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml, BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0);
    ResponseDTO<String> responseDTO = pipelineResource.updatePipeline(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PIPELINE_IDENTIFIER, PIPELINE_NAME, null, null, null, yaml, null);
    assertThat(responseDTO.getData()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testUpdatePipelineV2() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(true).build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(entityWithVersion).build();
    doReturn(pipelineCRUDResult)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(
            any(PipelineEntity.class), any(ChangeType.class), anyBoolean(), anyBoolean(), any(), anyBoolean());
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.updatePipelineV2(null, ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, PIPELINE_NAME, null, null, null, null, yaml, false, null);
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineV2WithSuccess() {
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(entityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                 .build())
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(
            any(PipelineEntity.class), any(ChangeType.class), anyBoolean(), anyBoolean(), any(), anyBoolean());
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.updatePipelineV2(null, ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null, null, yaml, false, null);
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNotNull();
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isFalse();
    assertThat(responseDTO.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  @Ignore("Ignored till Schema validation is behind FF")
  public void testUpdatePipelineWithSchemaErrors() {
    doThrow(JsonSchemaValidationException.class)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(any(PipelineEntity.class), any(ChangeType.class), anyBoolean(), anyBoolean(),
            any(ScopeInfo.class), anyBoolean());
    assertThatThrownBy(()
                           -> pipelineResource.updatePipeline(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, null, null, null, null, yaml, null))
        .isInstanceOf(JsonSchemaValidationException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeletePipeline() {
    doReturn(true)
        .when(pmsPipelineService)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false);
    ResponseDTO<Boolean> deleteResponse = pipelineResource.deletePipeline(
        null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null);
    assertThat(deleteResponse.getData()).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineSummary() {
    doReturn(Optional.of(entityWithVersion))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false,
            GitXCacheMapper.parseLoadFromCacheHeaderParam("false"), null, false);
    ResponseDTO<PMSPipelineSummaryResponseDTO> pipelineSummary = pipelineResource.getPipelineSummary(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, false, false, "false", null);
    assertThat(pipelineSummary.getData().getName()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(pipelineSummary.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(pipelineSummary.getData().getDescription()).isNull();
    assertThat(pipelineSummary.getData().getTags()).isEmpty();
    assertThat(pipelineSummary.getData().getVersion()).isEqualTo(1L);
    assertThat(pipelineSummary.getData().getNumOfStages()).isEqualTo(1L);
    assertThat(pipelineSummary.getData().getStageNames().get(0)).isEqualTo(STAGE);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetPipelineSummaryInvalidPipelineId() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";

    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, false, true, false,
            false, null, false);

    assertThatThrownBy(()
                           -> pipelineResource.getPipelineSummary(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               incorrectPipelineIdentifier, null, false, false, "false", null))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage(String.format(
            "Pipeline with the given ID: %s does not exist or has been deleted", incorrectPipelineIdentifier));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetListOfPipelinesInvalidSizeGT200() {
    doReturn(false)
        .when(featureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.PIPE_DISABLE_PIPELINE_LIST_PAGE_SIZE_LIMIT);
    assertThatThrownBy(()
                           -> pipelineResource.getListOfPipelines(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, 0, 201,
                               null, null, null, null, null, null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Please verify pipelines list parameters for page and size, page should be >= 0 and size should be > 0 "
            + "and <=200");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetListOfPipelinesInvalidSizeZero() {
    doReturn(false)
        .when(featureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.PIPE_DISABLE_PIPELINE_LIST_PAGE_SIZE_LIMIT);
    assertThatThrownBy(()
                           -> pipelineResource.getListOfPipelines(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, 0, 0,
                               null, null, null, null, null, null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Please verify pipelines list parameters for page and size, page should be >= 0 and size should be > 0 "
            + "and <=200");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetListOfPipelinesSkipsPageSizeValidationWhenInvertedFFEnabled() {
    doReturn(true)
        .when(featureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.PIPE_DISABLE_PIPELINE_LIST_PAGE_SIZE_LIMIT);
    Pageable pageable = PageRequest.of(0, 201, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.createdAt));
    Page<PipelineEntity> pipelineEntities = new PageImpl<>(Collections.singletonList(entityWithVersion), pageable, 1);
    doReturn(pipelineEntities)
        .when(pmsPipelineService)
        .list(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    doReturn(Collections.emptyMap())
        .when(pipelineMetadataService)
        .getMetadataForGivenPipelineIds(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, Collections.singletonList(PIPELINE_IDENTIFIER), null, false);
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());

    List<PMSPipelineSummaryResponseDTO> content = pipelineResource
                                                      .getListOfPipelines(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                                          0, 201, null, null, null, null, null, null, null, null)
                                                      .getData()
                                                      .getContent();
    assertThat(content).isNotEmpty();
    assertThat(content.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetListOfPipelines() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.createdAt));
    Page<PipelineEntity> pipelineEntities = new PageImpl<>(Collections.singletonList(entityWithVersion), pageable, 1);
    doReturn(pipelineEntities)
        .when(pmsPipelineService)
        .list(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    doReturn(Collections.emptyMap())
        .when(pipelineMetadataService)
        .getMetadataForGivenPipelineIds(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, Collections.singletonList(PIPELINE_IDENTIFIER), null, false);
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());
    List<PMSPipelineSummaryResponseDTO> content = pipelineResource
                                                      .getListOfPipelines(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                                          0, 25, null, null, null, null, null, null, null, null)
                                                      .getData()
                                                      .getContent();
    assertThat(content).isNotEmpty();
    assertThat(content.size()).isEqualTo(1);

    PMSPipelineSummaryResponseDTO responseDTO = content.get(0);
    assertThat(responseDTO.getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(responseDTO.getName()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(responseDTO.getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getNumOfStages()).isEqualTo(1L);
    assertThat(responseDTO.getStageNames().get(0)).isEqualTo(STAGE);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetListOfPipelinesPatternSyntax() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.createdAt));
    Page<PipelineEntity> pipelineEntities = new PageImpl<>(Collections.singletonList(entityWithVersion), pageable, 1);
    doReturn(pipelineEntities)
        .when(pmsPipelineService)
        .list(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    doReturn(Collections.emptyMap())
        .when(pipelineMetadataService)
        .getMetadataForGivenPipelineIds(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, Collections.singletonList(PIPELINE_IDENTIFIER), null, false);
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());
    List<PMSPipelineSummaryResponseDTO> content = pipelineResource
                                                      .getListOfPipelines(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                                          0, 25, null, "{[]", null, null, null, null, null, null)
                                                      .getData()
                                                      .getContent();
    assertThat(content.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetExpandedPipelineJson() {
    doReturn("look, a JSON")
        .when(pmsPipelineService)
        .fetchExpandedPipelineJSON(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, false);
    ResponseDTO<ExpandedPipelineJsonDTO> expandedPipelineJson = pipelineResource.getExpandedPipelineJson(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null);
    assertThat(expandedPipelineJson.getData().getExpandedJson()).isEqualTo("look, a JSON");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStepsV2() {
    StepCategory dummy = StepCategory.builder().name("dummy").build();
    StepPalleteFilterWrapper dummyRequest =
        StepPalleteFilterWrapper.builder().stepPalleteModuleInfos(Collections.emptyList()).build();
    doReturn(dummy).when(pmsPipelineService).getStepsV2(ACCOUNT_ID, dummyRequest);
    ResponseDTO<StepCategory> response = pipelineResource.getStepsV2(ACCOUNT_ID, dummyRequest);
    assertThat(response.getData()).isEqualTo(dummy);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testImportPipelineFromGit() {
    GitImportInfoDTO gitImportInfoDTO = GitImportInfoDTO.builder().branch("br").build();
    PipelineImportRequestDTO pipelineImportRequestDTO = PipelineImportRequestDTO.builder().build();
    doReturn(PipelineEntity.builder().identifier(PIPELINE_IDENTIFIER).build())
        .when(pmsPipelineService)
        .importPipelineFromRemote(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            pipelineImportRequestDTO, gitImportInfoDTO.getIsForceImport(), null, false);
    ResponseDTO<PipelineSaveResponse> importPipelineFromGit = pipelineResource.importPipelineFromGit(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, gitImportInfoDTO, pipelineImportRequestDTO, null);
    assertThat(importPipelineFromGit.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidatePipelineByYAML() {
    pipelineResource.validatePipelineByYAML(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml, null);
    verify(pmsPipelineServiceHelper, times(1))
        .resolveTemplatesAndValidatePipeline(
            PMSPipelineDtoMapper.toPipelineEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml, null, false), true,
            false, null, false, false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidatePipelineByIdentifier() {
    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getAndValidatePipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, null, false, false);
    assertThatThrownBy(()
                           -> pipelineResource.validatePipelineByIdentifier(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null))
        .isInstanceOf(EntityNotFoundException.class);

    doReturn(Optional.of(entity))
        .when(pmsPipelineService)
        .getAndValidatePipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, null, false, false);
    pipelineResource.validatePipelineByIdentifier(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    verify(pmsPipelineServiceHelper, times(0))
        .resolveTemplatesAndValidatePipeline(entity, false, false, null, false, false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetTemplateResolvedPipelineYaml() {
    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getPipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false, null, false);
    assertThatThrownBy(()
                           -> pipelineResource.getTemplateResolvedPipelineYaml(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "false", null))
        .isInstanceOf(EntityNotFoundException.class);

    doReturn(Optional.of(entity))
        .when(pmsPipelineService)
        .getPipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, true, null, false);

    String extraYaml = yaml + "extra";
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(extraYaml).build();
    doReturn(templateMergeResponseDTO).when(pipelineTemplateHelper).resolveTemplateRefsInPipeline(entity, null, "true");
    ResponseDTO<TemplatesResolvedPipelineResponseDTO> templateResolvedPipelineYaml =
        pipelineResource.getTemplateResolvedPipelineYaml(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "true", null);
    assertThat(templateResolvedPipelineYaml.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(templateResolvedPipelineYaml.getData().getResolvedTemplatesPipelineYaml()).isEqualTo(extraYaml);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTemplateResolvedPipelineYamlWithGitSyncedPipeline() {
    doReturn(Optional.of(remoteEntity))
        .when(pmsPipelineService)
        .getPipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, true, null, false);

    String extraYaml = yaml + "extra";
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(extraYaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(remoteEntity, null, "true");
    ResponseDTO<TemplatesResolvedPipelineResponseDTO> templateResolvedPipelineYaml =
        pipelineResource.getTemplateResolvedPipelineYaml(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "true", null);
    assertThat(templateResolvedPipelineYaml.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(templateResolvedPipelineYaml.getData().getResolvedTemplatesPipelineYaml()).isEqualTo(extraYaml);
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    assertThat(gitEntityInfo.getParentEntityAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(gitEntityInfo.getParentEntityOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(gitEntityInfo.getParentEntityProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(gitEntityInfo.getParentEntityConnectorRef()).isEqualTo(CONNECTOR_REF);
    assertThat(gitEntityInfo.getParentEntityRepoName()).isEqualTo(REPO);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDummyTemplateMethods() {
    assertThat(pipelineResource.getTemplateStageNode().getData().getClass()).isEqualTo(TemplateStageNode.class);
    assertThat(pipelineResource.getTemplateStepNode().getData().getClass()).isEqualTo(TemplateStepNode.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetExecutionNode() {
    NodeExecution dummyNodeExecution = NodeExecution.builder().name("dummy").build();
    doReturn(dummyNodeExecution).when(nodeExecutionService).get("id");
    ExecutionNode dummyExecutionNode = ExecutionNode.builder().name("dummy").build();
    doReturn(dummyExecutionNode)
        .when(nodeExecutionToExecutioNodeMapper)
        .mapNodeExecutionToExecutionNode(dummyNodeExecution);
    assertThat(pipelineResource.getExecutionNode(null, null, null, null)).isNull();
    ExecutionNode executionNode = pipelineResource.getExecutionNode(null, null, null, "id").getData();
    assertThat(executionNode).isEqualTo(dummyExecutionNode);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCreateSimplifiedPipeline() {
    doReturn(HarnessYamlVersion.V1).when(pmsPipelineService).pipelineVersion(ACCOUNT_ID, simplifiedYaml);
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(simplifiedEntityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                 .build())
        .when(pmsPipelineService)
        .validateAndCreatePipeline(any(), anyBoolean(), any(), anyBoolean());
    when(featureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.CDS_SAVE_PIPELINE_OPA_RESPONSE_CODE_CHANGE))
        .thenReturn(false);
    ResponseDTO<PipelineSaveResponse> response = pipelineResource.createPipelineV2(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, PIPELINE_NAME, null, null, null, null, null, simplifiedYaml, false, null);
    assertThat(response.getData().getIdentifier()).isNotEmpty();
    assertThat(response.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCreateSimplifiedPipelineWithoutYamlName() {
    doReturn(HarnessYamlVersion.V1).when(pmsPipelineService).pipelineVersion(ACCOUNT_ID, simplifiedYamlWithoutName);
    simplifiedEntityWithVersion.setYaml(simplifiedYamlWithoutName);
    simplifiedEntity.setYaml(simplifiedYamlWithoutName);
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(simplifiedEntityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                 .build())
        .when(pmsPipelineService)
        .validateAndCreatePipeline(any(PipelineEntity.class), anyBoolean(), any(), anyBoolean());
    ResponseDTO<PipelineSaveResponse> response =
        pipelineResource.createPipelineV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            PIPELINE_NAME, null, null, null, null, null, simplifiedYamlWithoutName, false, null);
    assertThat(response.getData().getIdentifier()).isNotEmpty();
    assertThat(response.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateSimplifiedPipeline() {
    doReturn(HarnessYamlVersion.V1).when(pmsPipelineService).pipelineVersion(ACCOUNT_ID, simplifiedYaml);
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineCRUDResult pipelineCRUDResult = PipelineCRUDResult.builder()
                                                .governanceMetadata(governanceMetadata)
                                                .pipelineEntity(simplifiedEntityWithVersion)
                                                .build();
    doReturn(pipelineCRUDResult)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(
            any(PipelineEntity.class), any(ChangeType.class), anyBoolean(), anyBoolean(), any(), anyBoolean());
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.updatePipelineV2(null, ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, PIPELINE_NAME, null, null, null, null, simplifiedYaml, false, null);
    assertThat(responseDTO.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateSimplifiedPipelineWithoutYamlName() {
    doReturn(HarnessYamlVersion.V1).when(pmsPipelineService).pipelineVersion(ACCOUNT_ID, simplifiedYamlWithoutName);
    simplifiedEntityWithVersion.setYaml(simplifiedYamlWithoutName);
    simplifiedEntity.setYaml(simplifiedYamlWithoutName);
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineCRUDResult pipelineCRUDResult = PipelineCRUDResult.builder()
                                                .governanceMetadata(governanceMetadata)
                                                .pipelineEntity(simplifiedEntityWithVersion)
                                                .build();
    doReturn(pipelineCRUDResult)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(
            any(PipelineEntity.class), any(ChangeType.class), anyBoolean(), anyBoolean(), any(), anyBoolean());
    ResponseDTO<PipelineSaveResponse> responseDTO =
        pipelineResource.updatePipelineV2(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            PIPELINE_NAME, null, null, null, null, simplifiedYamlWithoutName, false, null);
    assertThat(responseDTO.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetListRepos() {
    List<String> repos = new ArrayList<>();
    repos.add("testRepo");
    repos.add("testRepo2");

    PMSPipelineListRepoResponse repoResponse = PMSPipelineListRepoResponse.builder().repositories(repos).build();
    doReturn(repoResponse)
        .when(pmsPipelineService)
        .getListOfRepos(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, false);

    PMSPipelineListRepoResponse pmsPipelineListRepoResponse =
        pmsPipelineService.getListOfRepos(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, false);
    assertEquals(pmsPipelineListRepoResponse, repoResponse);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testForceImportPipelineChecksCreatePermission() {
    ForceImportPipelineRequestDTO requestDTO = ForceImportPipelineRequestDTO.builder()
                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                   .projectIdentifier(PROJ_IDENTIFIER)
                                                   .identifier(PIPELINE_IDENTIFIER)
                                                   .connectorRef(CONNECTOR_REF)
                                                   .repoName(REPO)
                                                   .branch("main")
                                                   .filePath("pipeline.yaml")
                                                   .build();

    pipelineResource.forceImportPipeline(ACCOUNT_ID, requestDTO);

    verify(pipelineSplitPermissionsHelper)
        .checkForPipelineRBACSplitAccessPermissions(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, false,
            PipelineRbacPermissions.PIPELINE_EDIT, Arrays.asList(PipelineRbacPermissions.PIPELINE_CREATE));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMoveConfigs() {
    MoveConfigOperationDTO moveConfigOperationDTO =
        MoveConfigOperationDTO.builder().moveConfigOperationType(MoveConfigOperationType.INLINE_TO_REMOTE).build();
    doReturn(PipelineCRUDResult.builder().pipelineEntity(entityWithVersion).build())
        .when(pmsPipelineService)
        .moveConfig(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, moveConfigOperationDTO, null, false);

    PipelineMoveConfigRequestDTO pipelineMoveConfigRequestDTO =
        PipelineMoveConfigRequestDTO.builder()
            .pipelineIdentifier(PIPELINE_IDENTIFIER)
            .isNewBranch(false)
            .moveConfigOperationType(io.harness.gitaware.helper.MoveConfigOperationType.INLINE_TO_REMOTE)
            .build();

    ResponseDTO<MoveConfigResponse> responseDTO = pipelineResource.moveConfig(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, pipelineMoveConfigRequestDTO, null);

    assertEquals(responseDTO.getData().getPipelineIdentifier(), PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetValidateResult() {
    doReturn(
        Optional.of(
            PipelineValidationEvent.builder()
                .status(ValidationStatus.SUCCESS)
                .result(ValidationResult.builder()
                            .templateValidationResponse(
                                TemplateValidationResponseDTO.builder().validYaml(true).exceptionMessage("").build())
                            .build())
                .params(ValidationParams.builder()
                            .pipelineEntity(PipelineEntity.builder().identifier(PIPELINE_IDENTIFIER).build())
                            .build())
                .startTs(1L)
                .endTs(2L)
                .build()))
        .when(pipelineAsyncValidationService)
        .getEventByUuid("uuid1");
    ResponseDTO.newResponse(pipelineRefreshResource
                                .validateTemplateInputs(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                    PIPELINE_IDENTIFIER, "false", null, null)
                                .getData());
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder().validYaml(true).build();
    when(pipelineRefreshService.validateTemplateInputsInPipeline(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "false", null, false))
        .thenReturn(validateTemplateInputsResponseDTO);

    ResponseDTO<PipelineValidationResponseDTO> responseDTO =
        pipelineResource.getPipelineValidateResult(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "uuid1");
    assertThat(responseDTO).isNotNull();
  }

  @Test
  @Owner(developers = JATIN)
  @Category(UnitTests.class)
  public void testGetValidateResultForNullPipelineValidationEventParams() {
    doReturn(
        Optional.of(
            PipelineValidationEvent.builder()
                .status(ValidationStatus.SUCCESS)
                .result(ValidationResult.builder()
                            .templateValidationResponse(
                                TemplateValidationResponseDTO.builder().validYaml(true).exceptionMessage("").build())
                            .build())
                .startTs(1L)
                .endTs(2L)
                .build()))
        .when(pipelineAsyncValidationService)
        .getEventByUuid("uuid1");
    ResponseDTO.newResponse(pipelineRefreshResource
                                .validateTemplateInputs(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                    PIPELINE_IDENTIFIER, "false", null, null)
                                .getData());
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder().validYaml(true).build();
    when(pipelineRefreshService.validateTemplateInputsInPipeline(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "false", null, false))
        .thenReturn(validateTemplateInputsResponseDTO);

    assertThatThrownBy(
        () -> pipelineResource.getPipelineValidateResult(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "uuid1"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("PipelineIdentifier could not be found, Please perform async validation again");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testUpdateGitMetadataForPipeline() {
    GitMetadataUpdateRequestInfoDTO gitMetadataUpdateRequestInfo = GitMetadataUpdateRequestInfoDTO.builder()
                                                                       .connectorRef("newConnectorRef")
                                                                       .filePath("newFilePath")
                                                                       .repoName("repoName")
                                                                       .build();
    PipelineEntity entityWithStoreType = entity.withStoreType(StoreType.INLINE);
    doReturn(PIPELINE_IDENTIFIER)
        .when(pmsPipelineService)
        .updateGitMetadata(any(), any(), any(), any(), any(), any(), anyBoolean());
    doReturn(entityWithStoreType)
        .when(pmsPipelineService)
        .getPipelineMetadata(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, true, null, false);
    ResponseDTO<PMSGitUpdateResponseDTO> response = pipelineResource.updateGitMetadataForPipeline(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, gitMetadataUpdateRequestInfo, null);
    assertEquals(PIPELINE_IDENTIFIER, response.getData().getIdentifier());
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testClonePipelineV2() {
    ClonePipelineDTO dummy = ClonePipelineDTO.builder().build();
    PipelineSaveResponse dummyResponse = PipelineSaveResponse.builder().identifier("id").build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId("xyz")
                              .build();
    doReturn(dummyResponse).when(pmsPipelineService).validateAndClonePipeline(dummy, ACCOUNT_ID, scopeInfo, true);
    doReturn(true).when(pmsPipelineServiceHelper).isParentIdQueryingEnabled(ACCOUNT_ID);
    ResponseDTO<PipelineSaveResponse> response =
        pipelineResource.clonePipelineV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, dummy, scopeInfo);
    assertThat(response.getData()).isEqualTo(dummyResponse);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testStartPipelineValidationEventPassesScopeInfoToAsyncValidation() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId("xyz")
                              .build();
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier(PIPELINE_IDENTIFIER)
                                        .parentUniqueId("xyz")
                                        .build();
    doReturn(true).when(pmsPipelineServiceHelper).isParentIdQueryingEnabled(ACCOUNT_ID);
    doReturn(Optional.of(pipelineEntity))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false,
            scopeInfo, true);
    doReturn(PipelineValidationEvent.builder().uuid("validationUuid").build())
        .when(pipelineAsyncValidationService)
        .startEvent(pipelineEntity, "branch", Action.CRUD, false, scopeInfo, true);

    ResponseDTO<PipelineValidationUUIDResponseBody> response =
        pipelineResource.startPipelineValidationEvent(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            GitEntityFindInfoDTO.builder().branch("branch").build(), null, scopeInfo);

    assertThat(response.getData().getUuid()).isEqualTo("validationUuid");
    verify(pipelineAsyncValidationService).startEvent(pipelineEntity, "branch", Action.CRUD, false, scopeInfo, true);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteRepoListMapsServiceResponseAndComputesTotal() {
    Scope repoAScope = Scope.of(ACCOUNT_ID, "orgA", "projA", "uid-projA");
    Map<String, Scope> repoAFilePaths = new HashMap<>();
    repoAFilePaths.put(".harness/build.yaml", repoAScope);
    repoAFilePaths.put(".harness/deploy.yaml", repoAScope);
    PMSPipelineRemoteRepoInfo repoA =
        PMSPipelineRemoteRepoInfo.builder()
            .repoName("harness-core")
            .repoURL("https://github.com/wings-software/harness-core")
            .count(5L)
            .filePathsByOwningScope(repoAFilePaths)
            .connectorRefs(new HashSet<>(Arrays.asList(ACCOUNT_ID + "/orgA/projA/githubMain")))
            .build();
    Scope repoBScope = Scope.of(ACCOUNT_ID, null, null, ACCOUNT_ID);
    Map<String, Scope> repoBFilePaths = new HashMap<>();
    repoBFilePaths.put("pipelines/argocd-sync.yaml", repoBScope);
    PMSPipelineRemoteRepoInfo repoB = PMSPipelineRemoteRepoInfo.builder()
                                          .repoName("gitops-config")
                                          .repoURL("https://github.com/wings-software/gitops-config")
                                          .count(2L)
                                          .filePathsByOwningScope(repoBFilePaths)
                                          .connectorRefs(new HashSet<>(Arrays.asList(ACCOUNT_ID + "/accountGithub")))
                                          .build();
    PMSPipelineRemoteRepoListResponse serviceResponse =
        PMSPipelineRemoteRepoListResponse.builder().repositories(Arrays.asList(repoA, repoB)).build();
    when(pmsPipelineService.getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, null, null, 0, 20))
        .thenReturn(serviceResponse);

    ResponseDTO<RemotePipelinesResponseDTO> result =
        pipelineResource.getRemotePipelineMetadata(ACCOUNT_ID, null, null, null, 0, 20, null);

    verify(pmsPipelineService, times(1)).getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, null, null, 0, 20);
    assertThat(result.getData().getTotalPipelines()).isEqualTo(7L);
    assertThat(result.getData().getRepositories()).hasSize(2);
    assertThat(result.getData().getRepositories().get(0).getRepoName()).isEqualTo("harness-core");
    assertThat(result.getData().getRepositories().get(0).getCount()).isEqualTo(5L);
    assertThat(result.getData().getRepositories().get(0).getFilePathsByOwningScope().keySet())
        .containsExactlyInAnyOrder(".harness/build.yaml", ".harness/deploy.yaml");
    assertThat(result.getData().getRepositories().get(0).getFilePathsByOwningScope().get(".harness/build.yaml"))
        .isEqualTo(repoAScope);
    assertThat(result.getData().getRepositories().get(0).getConnectorRefs())
        .containsExactly(ACCOUNT_ID + "/orgA/projA/githubMain");
    assertThat(result.getData().getRepositories().get(1).getRepoName()).isEqualTo("gitops-config");
    assertThat(result.getData().getRepositories().get(1).getCount()).isEqualTo(2L);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteRepoListPassesRepoNameFilterThrough() {
    PMSPipelineRemoteRepoListResponse serviceResponse =
        PMSPipelineRemoteRepoListResponse.builder().repositories(Collections.emptyList()).build();
    when(pmsPipelineService.getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, "harness-core", null, 0, 20))
        .thenReturn(serviceResponse);

    ResponseDTO<RemotePipelinesResponseDTO> result =
        pipelineResource.getRemotePipelineMetadata(ACCOUNT_ID, null, null, "harness-core", 0, 20, null);

    verify(pmsPipelineService, times(1))
        .getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, "harness-core", null, 0, 20);
    assertThat(result.getData().getTotalPipelines()).isEqualTo(0L);
    assertThat(result.getData().getRepositories()).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteRepoListHandlesNullRepositoriesFromService() {
    PMSPipelineRemoteRepoListResponse serviceResponse =
        PMSPipelineRemoteRepoListResponse.builder().repositories(null).build();
    when(pmsPipelineService.getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, null, null, 0, 20))
        .thenReturn(serviceResponse);

    ResponseDTO<RemotePipelinesResponseDTO> result =
        pipelineResource.getRemotePipelineMetadata(ACCOUNT_ID, null, null, null, 0, 20, null);

    assertThat(result.getData()).isNotNull();
    assertThat(result.getData().getTotalPipelines()).isEqualTo(0L);
    assertThat(result.getData().getRepositories()).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemotePipelineMetadataPropagatesServiceException() {
    when(pmsPipelineService.getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, null, null, 0, 20))
        .thenThrow(new InvalidRequestException("boom"));

    assertThatThrownBy(() -> pipelineResource.getRemotePipelineMetadata(ACCOUNT_ID, null, null, null, 0, 20, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("boom");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemotePipelineMetadataPropagatesScopeIdentifiersToService() {
    String orgId = "myOrg";
    String projectId = "myProject";
    PMSPipelineRemoteRepoListResponse serviceResponse =
        PMSPipelineRemoteRepoListResponse.builder().repositories(Collections.emptyList()).build();
    when(pmsPipelineService.getRemoteRepoListForAGivenScope(ACCOUNT_ID, orgId, projectId, null, null, 0, 20))
        .thenReturn(serviceResponse);

    pipelineResource.getRemotePipelineMetadata(ACCOUNT_ID, orgId, projectId, null, 0, 20, null);

    verify(pmsPipelineService, times(1))
        .getRemoteRepoListForAGivenScope(ACCOUNT_ID, orgId, projectId, null, null, 0, 20);
  }
}

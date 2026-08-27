/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.pipeline.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SHIVAM;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.publicaccess.dto.PublicAccessResponse;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.TemplateValidationResponseDTO;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.helper.PipelinePublicAccessHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.intfc.PipelineCRUDResult;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.NGYamlHelper;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.GitImportInfo;
import io.harness.spec.server.pipeline.v1.model.GitMoveDetails;
import io.harness.spec.server.pipeline.v1.model.GovernanceStatus;
import io.harness.spec.server.pipeline.v1.model.MoveConfigOperationType;
import io.harness.spec.server.pipeline.v1.model.PipelineCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineCreateResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineGetResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineImportRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineImportRequestDTO;
import io.harness.spec.server.pipeline.v1.model.PipelineListResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineMoveConfigRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineMoveConfigResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineSaveResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationUUIDResponseBody;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@OwnedBy(PIPELINE)
public class PipelinesApiImplTest extends CategoryTest {
  PipelinesApiImpl pipelinesApiImpl;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSPipelineServiceHelper pipelineServiceHelper;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock PipelineMetadataService pipelineMetadataService;
  @Mock PipelineAsyncValidationService pipelineAsyncValidationService;
  @Mock private OrganizationClient organizationClient;
  @Mock ProjectClient projectClient;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock PipelinePublicAccessHelper pipelinePublicAccessHelper;
  @Mock PipelineOpaStatusHandler pipelineOpaStatusHandler;

  String identifier = "basichttpFail";
  String name = "basichttpFail";
  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String parentUniqueId = randomAlphabetic(10);
  String branch = randomAlphabetic(10);
  String repo = randomAlphabetic(10);
  String connectorRef = randomAlphabetic(10);
  int page = 0;
  int limit = 1;
  PipelineEntity entity;
  PipelineEntity entityModified;
  private String yaml;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.openMocks(this);
    pipelinesApiImpl = new PipelinesApiImpl(pmsPipelineService, pipelineServiceHelper, pipelineTemplateHelper,
        pipelineMetadataService, pipelineAsyncValidationService, pmsFeatureFlagService, scopeResolutionHelper,
        accessControlClient, pipelineSplitPermissionsHelper, pipelinePublicAccessHelper, pipelineOpaStatusHandler);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "simplified-yaml.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    entity = PipelineEntity.builder()
                 .accountId(account)
                 .orgIdentifier(org)
                 .projectIdentifier(project)
                 .identifier(identifier)
                 .name(name)
                 .yaml(yaml)
                 .isDraft(false)
                 .allowStageExecutions(false)
                 .build();

    entityModified = PipelineEntity.builder()
                         .accountId(account)
                         .orgIdentifier(org)
                         .projectIdentifier(project)
                         .identifier(identifier)
                         .name(name)
                         .yaml(yaml)
                         .stageCount(1)
                         .stageName("qaStage")
                         .allowStageExecutions(false)
                         .build();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineCreate() {
    PipelineCreateRequestBody pipelineRequestBody = new PipelineCreateRequestBody();
    pipelineRequestBody.setPipelineYaml(yaml);
    pipelineRequestBody.setIdentifier(identifier);
    pipelineRequestBody.setName(name);
    pipelineRequestBody.setVersion("1");
    when(pmsPipelineService.validateAndCreatePipeline(any(PipelineEntity.class), eq(false), eq(null), eq(true)))
        .thenReturn(PipelineCRUDResult.builder()
                        .pipelineEntity(entity)
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());
    boolean isPublic = false;
    when(pipelinePublicAccessHelper.markPipelinePublic(account, org, project, identifier, isPublic))
        .thenReturn(setPublicAccessResponse(isPublic));
    Response response = pipelinesApiImpl.createPipeline(pipelineRequestBody, org, project, account, isPublic, false);
    PipelineCreateResponseBody responseBody = (PipelineCreateResponseBody) response.getEntity();
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals(io.harness.spec.server.pipeline.v1.model.PublicAccessResponse.class,
        responseBody.getPublicAccessResponse().getClass());
    var publicAccessResponse =
        (io.harness.spec.server.pipeline.v1.model.PublicAccessResponse) responseBody.getPublicAccessResponse();
    assertThat(publicAccessResponse.isIsPublic()).isFalse();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineCreateFailureScenario() {
    PipelineCreateRequestBody pipelineRequestBody = new PipelineCreateRequestBody();
    pipelineRequestBody.setPipelineYaml(yaml);
    pipelineRequestBody.setIdentifier(identifier);
    pipelineRequestBody.setName(name);
    when(pmsPipelineService.validateAndCreatePipeline(any(), eq(false)))
        .thenReturn(PipelineCRUDResult.builder()
                        .pipelineEntity(entity)
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());

    try (MockedStatic<NGYamlHelper> mockedNGYamlHelper = Mockito.mockStatic(NGYamlHelper.class)) {
      mockedNGYamlHelper.when(() -> NGYamlHelper.detectVersionFromYamlStructure(any())).thenReturn(null);

      assertThatThrownBy(
          () -> pipelinesApiImpl.createPipeline(pipelineRequestBody, org, project, account, false, false))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessage("Required field [pipelineId] is either null or empty in the pipeline yaml");
    }
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineDelete() {
    doReturn(true).when(pmsPipelineService).delete(account, org, project, identifier, null, null, true);
    Response deleteResponse = pipelinesApiImpl.deletePipeline(org, project, identifier, account);
    assertThat(deleteResponse.getStatus()).isEqualTo(204);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineDeleteFail() {
    doReturn(false).when(pmsPipelineService).delete(account, org, project, identifier, null, null, true);
    try {
      pipelinesApiImpl.deletePipeline(org, project, identifier, account);
    } catch (InvalidRequestException e) {
      assertEquals(e.getMessage(), String.format("Pipeline with identifier %s cannot be deleted.", identifier));
    }
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineUpdate() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(entityModified).build();
    doReturn(pipelineCRUDResult)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(
            any(PipelineEntity.class), any(ChangeType.class), anyBoolean(), anyBoolean(), any(), anyBoolean());
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(account, org, project, yaml, BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0);
    PipelineUpdateRequestBody requestBody = new PipelineUpdateRequestBody();
    requestBody.setPipelineYaml(yaml);
    requestBody.setIdentifier(identifier);
    requestBody.setName(name);
    requestBody.setVersion("1");

    boolean isPublic = true;
    when(pipelinePublicAccessHelper.markPipelinePublic(account, org, project, identifier, isPublic))
        .thenReturn(setPublicAccessResponse(isPublic));
    Response response = pipelinesApiImpl.updatePipeline(requestBody, org, project, identifier, account, isPublic);
    PipelineCreateResponseBody responseBody = (PipelineCreateResponseBody) response.getEntity();
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals(io.harness.spec.server.pipeline.v1.model.PublicAccessResponse.class,
        responseBody.getPublicAccessResponse().getClass());
    var publicAccessResponse =
        (io.harness.spec.server.pipeline.v1.model.PublicAccessResponse) responseBody.getPublicAccessResponse();
    assertThat(publicAccessResponse.isIsPublic()).isTrue();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineUpdateFail() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(true).build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(entityModified).build();
    doReturn(pipelineCRUDResult)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(
            any(PipelineEntity.class), any(ChangeType.class), anyBoolean(), anyBoolean(), any(), anyBoolean());
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(account, org, project, yaml, BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0);
    PipelineUpdateRequestBody requestBody = new PipelineUpdateRequestBody();
    requestBody.setPipelineYaml(yaml);
    requestBody.setIdentifier(identifier);
    requestBody.setName(name);
    requestBody.setVersion("1");
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(account)
                              .orgIdentifier(org)
                              .projectIdentifier(project)
                              .uniqueId(parentUniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
    try {
      pipelinesApiImpl.updatePipeline(requestBody, org, project, identifier, account, false);
    } catch (PolicyEvaluationFailureException e) {
      assertEquals(e.getMessage(), "Policy Evaluation Failure");
    }
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineGetNoTemplates() {
    Optional<PipelineEntity> optional = Optional.ofNullable(entity);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(account)
                              .orgIdentifier(org)
                              .projectIdentifier(project)
                              .uniqueId(parentUniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);

    doReturn(optional)
        .when(pmsPipelineService)
        .getPipeline(account, org, project, identifier, false, false, false, false, scopeInfo, true);
    when(pipelinePublicAccessHelper.getPublicContextResponse(account, org, project, identifier))
        .thenReturn(setPublicAccessResponse(false));
    Response response = pipelinesApiImpl.getPipeline(
        org, project, identifier, account, null, false, null, null, BOOLEAN_FALSE_VALUE, false, false);
    PipelineGetResponseBody responseBody = (PipelineGetResponseBody) response.getEntity();
    assertEquals(yaml, responseBody.getPipelineYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals(org, responseBody.getOrg());
    assertEquals(project, responseBody.getProject());
    assertEquals(true, responseBody.isValid().booleanValue());
    assertEquals(io.harness.spec.server.pipeline.v1.model.PublicAccessResponse.class,
        responseBody.getPublicAccessResponse().getClass());
    var publicAccessResponse =
        (io.harness.spec.server.pipeline.v1.model.PublicAccessResponse) responseBody.getPublicAccessResponse();
    assertThat(publicAccessResponse.isIsPublic()).isFalse();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testNPEInPipelineGet() {
    Optional<PipelineEntity> optional = Optional.ofNullable(entity);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(account)
                              .orgIdentifier(org)
                              .projectIdentifier(project)
                              .uniqueId(parentUniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
    doReturn(optional)
        .when(pmsPipelineService)
        .getPipeline(account, org, project, identifier, false, false, false, false, scopeInfo, true);
    when(pipelinePublicAccessHelper.getPublicContextResponse(account, org, project, identifier))
        .thenReturn(setPublicAccessResponse(true));
    Response response = pipelinesApiImpl.getPipeline(
        org, project, identifier, account, null, false, null, null, BOOLEAN_FALSE_VALUE, null, false);
    PipelineGetResponseBody responseBody = (PipelineGetResponseBody) response.getEntity();
    assertEquals(yaml, responseBody.getPipelineYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals(org, responseBody.getOrg());
    assertEquals(project, responseBody.getProject());
    assertEquals(io.harness.spec.server.pipeline.v1.model.PublicAccessResponse.class,
        responseBody.getPublicAccessResponse().getClass());
    var publicAccessResponse =
        (io.harness.spec.server.pipeline.v1.model.PublicAccessResponse) responseBody.getPublicAccessResponse();
    assertThat(publicAccessResponse.isIsPublic()).isTrue();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineGetWithTemplates() {
    Optional<PipelineEntity> optional = Optional.ofNullable(entity);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(account)
                              .orgIdentifier(org)
                              .projectIdentifier(project)
                              .uniqueId(parentUniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
    doReturn(optional)
        .when(pmsPipelineService)
        .getPipeline(account, org, project, identifier, false, false, false, false, scopeInfo, true);
    String extraYaml = yaml + "extra";
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(extraYaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(entity, scopeInfo, BOOLEAN_FALSE_VALUE);
    when(pipelinePublicAccessHelper.getPublicContextResponse(account, org, project, identifier))
        .thenReturn(setPublicAccessResponse(false));
    Response response = pipelinesApiImpl.getPipeline(
        org, project, identifier, account, null, true, null, null, BOOLEAN_FALSE_VALUE, false, false);
    PipelineGetResponseBody responseBody = (PipelineGetResponseBody) response.getEntity();
    assertEquals(extraYaml, responseBody.getTemplateAppliedPipelineYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals(org, responseBody.getOrg());
    assertEquals(project, responseBody.getProject());
    assertEquals(true, responseBody.isValid().booleanValue());
    assertEquals(io.harness.spec.server.pipeline.v1.model.PublicAccessResponse.class,
        responseBody.getPublicAccessResponse().getClass());
    var publicAccessResponse =
        (io.harness.spec.server.pipeline.v1.model.PublicAccessResponse) responseBody.getPublicAccessResponse();
    assertThat(publicAccessResponse.isIsPublic()).isFalse();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineGetFailPolicyEvaluation() {
    Optional<PipelineEntity> optional = Optional.ofNullable(entity);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(account)
                              .orgIdentifier(org)
                              .projectIdentifier(project)
                              .uniqueId(parentUniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);

    doReturn(optional)
        .when(pmsPipelineService)
        .getPipeline(account, org, project, identifier, false, false, false, false, scopeInfo, true);
    doThrow(PolicyEvaluationFailureException.class)
        .when(pmsPipelineService)
        .validatePipeline(account, org, project, identifier, false, false, false, entity, scopeInfo, true);
    when(pipelinePublicAccessHelper.getPublicContextResponse(account, org, project, identifier))
        .thenReturn(setPublicAccessResponse(false));
    PipelineGetResponseBody response =
        (PipelineGetResponseBody) pipelinesApiImpl
            .getPipeline(org, project, identifier, account, null, false, null, null, BOOLEAN_FALSE_VALUE, false, false)
            .getEntity();
    assertEquals(false, response.isValid().booleanValue());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineGetFailInvalidYaml() {
    Optional<PipelineEntity> optional = Optional.ofNullable(entity);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(account)
                              .orgIdentifier(org)
                              .projectIdentifier(project)
                              .uniqueId(parentUniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);

    doReturn(optional)
        .when(pmsPipelineService)
        .getPipeline(account, org, project, identifier, false, false, false, false, scopeInfo, true);
    doThrow(InvalidYamlException.class)
        .when(pmsPipelineService)
        .validatePipeline(account, org, project, identifier, false, false, false, entity, scopeInfo, true);
    when(pipelinePublicAccessHelper.getPublicContextResponse(account, org, project, identifier))
        .thenReturn(setPublicAccessResponse(true));
    PipelineGetResponseBody response =
        (PipelineGetResponseBody) pipelinesApiImpl
            .getPipeline(org, project, identifier, account, null, false, null, null, BOOLEAN_FALSE_VALUE, false, false)
            .getEntity();
    assertEquals(false, response.isValid().booleanValue());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineList() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, PipelineEntityKeys.lastUpdatedAt));
    Page<PipelineEntity> pipelineEntities = new PageImpl<>(Collections.singletonList(entityModified), pageable, 1);
    doReturn(pipelineEntities)
        .when(pmsPipelineService)
        .list(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    doReturn(Collections.emptyMap())
        .when(pipelineMetadataService)
        .getMetadataForGivenPipelineIds(account, org, project, Collections.singletonList(identifier), null, false);
    List<PipelineListResponseBody> content = (List<PipelineListResponseBody>) pipelinesApiImpl
                                                 .listPipelines(org, project, account, 0, 25, null, null, null, null,
                                                     null, null, null, null, null, null, null, null, null)
                                                 .getEntity();
    assertThat(content).isNotEmpty();
    assertThat(content.size()).isEqualTo(1);

    PipelineListResponseBody responseBody = content.get(0);
    assertThat(responseBody.getIdentifier()).isEqualTo(identifier);
    assertThat(responseBody.getName()).isEqualTo(name);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPipelineListForPatternException() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, PipelineEntityKeys.lastUpdatedAt));
    Page<PipelineEntity> pipelineEntities = new PageImpl<>(Collections.singletonList(entityModified), pageable, 1);
    doReturn(pipelineEntities)
        .when(pmsPipelineService)
        .list(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    doReturn(Collections.emptyMap())
        .when(pipelineMetadataService)
        .getMetadataForGivenPipelineIds(account, org, project, Collections.singletonList(identifier), null, false);
    Response response = pipelinesApiImpl.listPipelines(
        org, project, account, 0, 25, "{", null, null, null, null, null, null, null, null, null, null, null, null);
    assertThat(response.getDate()).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testStartPipelineValidationEvent() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(account)
                              .orgIdentifier(org)
                              .projectIdentifier(project)
                              .uniqueId(parentUniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
    doReturn(Optional.of(entity))
        .when(pmsPipelineService)
        .getPipeline(account, org, project, "pipeline", false, false, false, false, scopeInfo, true);
    doReturn(PipelineValidationEvent.builder().uuid("abc1").build())
        .when(pipelineAsyncValidationService)
        .startEvent(entity, null, Action.CRUD, false, scopeInfo, true);
    Response response =
        pipelinesApiImpl.startPipelineValidationEvent(org, project, "pipeline", account, null, null, null, false, null);
    PipelineValidationUUIDResponseBody responseBody = (PipelineValidationUUIDResponseBody) response.getEntity();
    assertThat(responseBody.getUuid()).isEqualTo("abc1");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineValidateResult() {
    doReturn(
        Optional.of(
            PipelineValidationEvent.builder()
                .status(ValidationStatus.IN_PROGRESS)
                .result(ValidationResult.builder()
                            .templateValidationResponse(
                                TemplateValidationResponseDTO.builder().validYaml(true).exceptionMessage("").build())
                            .build())
                .build()))
        .when(pipelineAsyncValidationService)
        .getEventByUuid("uuid1");

    Response response = pipelinesApiImpl.getPipelineValidateResult(null, null, "uuid1", null);
    PipelineValidationResponseBody responseBody = (PipelineValidationResponseBody) response.getEntity();
    assertThat(responseBody.getStatus()).isEqualTo("IN_PROGRESS");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPipelineGetNoTemplatesWithCaching() {
    Optional<PipelineEntity> optional = Optional.ofNullable(entity);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(account)
                              .orgIdentifier(org)
                              .projectIdentifier(project)
                              .uniqueId(parentUniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);

    doReturn(optional)
        .when(pmsPipelineService)
        .getPipeline(account, org, project, identifier, false, false, false, true, scopeInfo, true);
    when(pipelinePublicAccessHelper.getPublicContextResponse(account, org, project, identifier))
        .thenReturn(setPublicAccessResponse(false));
    Response response =
        pipelinesApiImpl.getPipeline(org, project, identifier, account, null, false, null, null, "true", false, false);
    PipelineGetResponseBody responseBody = (PipelineGetResponseBody) response.getEntity();
    assertEquals(yaml, responseBody.getPipelineYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals(org, responseBody.getOrg());
    assertEquals(project, responseBody.getProject());
    assertEquals(io.harness.spec.server.pipeline.v1.model.PublicAccessResponse.class,
        responseBody.getPublicAccessResponse().getClass());
    var publicAccessResponse =
        (io.harness.spec.server.pipeline.v1.model.PublicAccessResponse) responseBody.getPublicAccessResponse();
    assertThat(publicAccessResponse.isIsPublic()).isFalse();
    assertTrue(responseBody.isValid());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMoveConfig() {
    GitMoveDetails gitMoveDetails = new GitMoveDetails();
    gitMoveDetails.setBranchName(branch);
    gitMoveDetails.setRepoName(repo);
    gitMoveDetails.setConnectorRef(connectorRef);
    PipelineMoveConfigRequestBody pipelineMoveConfigRequestBody = new PipelineMoveConfigRequestBody();
    pipelineMoveConfigRequestBody.setGitDetails(gitMoveDetails);
    pipelineMoveConfigRequestBody.setMoveConfigOperationType(MoveConfigOperationType.INLINE_TO_REMOTE);
    pipelineMoveConfigRequestBody.setPipelineIdentifier(identifier);
    doReturn(PipelineCRUDResult.builder().pipelineEntity(entity).build())
        .when(pmsPipelineService)
        .moveConfig(any(), any(), any(), any(), any(), any(), anyBoolean());
    Response response = pipelinesApiImpl.moveConfig(org, project, identifier, pipelineMoveConfigRequestBody, account);
    PipelineMoveConfigResponseBody responseBody = (PipelineMoveConfigResponseBody) response.getEntity();
    assertEquals(identifier, responseBody.getPipelineIdentifier());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPipelineListForInvalidProject() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, PipelineEntityKeys.lastUpdatedAt));
    Page<PipelineEntity> pipelineEntities = new PageImpl<>(Collections.singletonList(entityModified), pageable, 1);
    when(pmsPipelineService.list(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
        .thenThrow(InvalidRequestException.class);
    doReturn(Collections.emptyMap())
        .when(pipelineMetadataService)
        .getMetadataForGivenPipelineIds(account, org, project, Collections.singletonList(identifier), null, false);
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    aStatic.when(() -> NGRestUtils.getResponse(eq(projectClient.getProject(any(), any(), any())), any()))
        .thenThrow(InvalidRequestException.class);
    final Throwable ex = catchThrowable(()
                                            -> pipelinesApiImpl
                                                   .listPipelines(org, project, account, 0, 25, null, null, null, null,
                                                       null, null, null, null, null, null, null, null, null)
                                                   .getEntity());
    assertThat(ex).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPipelineImport() {
    PipelineImportRequestDTO pipelineImportRequestDTO = new PipelineImportRequestDTO();
    GitImportInfo gitImportInfo = new GitImportInfo();
    gitImportInfo.isForceImport(false);
    PipelineImportRequestBody pipelineImportRequestBody = new PipelineImportRequestBody();
    pipelineImportRequestBody.setPipelineImportRequest(pipelineImportRequestDTO);
    pipelineImportRequestBody.setGitImportInfo(gitImportInfo);
    doReturn(PipelineEntity.builder().identifier(identifier).build())
        .when(pmsPipelineService)
        .importPipelineFromRemote(any(), any(), any(), any(), any(), anyBoolean(), any(), anyBoolean());
    Response response =
        pipelinesApiImpl.importPipelineFromGit(org, project, identifier, pipelineImportRequestBody, account);
    PipelineSaveResponseBody responseBody = (PipelineSaveResponseBody) response.getEntity();
    assertEquals(identifier, responseBody.getIdentifier());
    verify(pipelineSplitPermissionsHelper)
        .checkForPipelineRBACSplitAccessPermissions(account, org, project, identifier, false,
            PipelineRbacPermissions.PIPELINE_EDIT, Arrays.asList(PipelineRbacPermissions.PIPELINE_CREATE));
  }

  private PublicAccessResponse setPublicAccessResponse(boolean isPublic) {
    return PublicAccessResponse.builder().isPublic(isPublic).errorMessage(null).build();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testPipelineCreateWithOPAPass() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().pipelineEntity(entity).governanceMetadata(governanceMetadata).build();

    when(pmsPipelineService.validateAndCreatePipeline(any(PipelineEntity.class), eq(false), eq(null), eq(true)))
        .thenReturn(pipelineCRUDResult);

    PipelineCreateRequestBody requestBody = new PipelineCreateRequestBody();
    requestBody.setPipelineYaml(yaml);
    requestBody.setIdentifier(identifier);
    requestBody.setName(name);
    requestBody.setVersion("1");

    boolean isPublic = false;
    when(pipelinePublicAccessHelper.markPipelinePublic(account, org, project, identifier, isPublic))
        .thenReturn(setPublicAccessResponse(isPublic));

    Response response = pipelinesApiImpl.createPipeline(requestBody, org, project, account, isPublic, false);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    PipelineCreateResponseBody responseBody = (PipelineCreateResponseBody) response.getEntity();
    assertThat(responseBody.getIdentifier()).isEqualTo(identifier);
    assertThat(responseBody.getGovernanceMetadata()).isNotNull();
    assertThat(responseBody.getGovernanceMetadata().isDeny()).isFalse();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testPipelineCreateWithOPADeny() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder()
                                                .setDeny(true)
                                                .setStatus("ERROR")
                                                .setMessage("Pipeline does not follow the Policies in the Policy Sets")
                                                .build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().pipelineEntity(entity).governanceMetadata(governanceMetadata).build();

    when(pmsPipelineService.validateAndCreatePipeline(any(PipelineEntity.class), eq(false), eq(null), eq(true)))
        .thenReturn(pipelineCRUDResult);

    PipelineCreateRequestBody requestBody = new PipelineCreateRequestBody();
    requestBody.setPipelineYaml(yaml);
    requestBody.setIdentifier(identifier);
    requestBody.setName(name);
    requestBody.setVersion("1");

    Response response = pipelinesApiImpl.createPipeline(requestBody, org, project, account, false, false);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    PipelineCreateResponseBody responseBody = (PipelineCreateResponseBody) response.getEntity();

    assertThat(responseBody.getGovernanceMetadata()).isNotNull();
    assertThat(responseBody.getGovernanceMetadata().isDeny()).isTrue();
    assertThat(responseBody.getGovernanceMetadata().getStatus()).isEqualTo(GovernanceStatus.ERROR);
    assertThat(responseBody.getGovernanceMetadata().getMessage())
        .isEqualTo("Pipeline does not follow the Policies in the Policy Sets");
    assertThat(responseBody.getIdentifier()).isNull();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testPipelineUpdateWithOPAPass() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(entityModified).build();

    doReturn(pipelineCRUDResult)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(
            any(PipelineEntity.class), any(ChangeType.class), anyBoolean(), anyBoolean(), any(), anyBoolean());

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(account, org, project, yaml, BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0);

    PipelineUpdateRequestBody requestBody = new PipelineUpdateRequestBody();
    requestBody.setPipelineYaml(yaml);
    requestBody.setIdentifier(identifier);
    requestBody.setName(name);
    requestBody.setVersion("1");

    boolean isPublic = false;
    when(pipelinePublicAccessHelper.markPipelinePublic(account, org, project, identifier, isPublic))
        .thenReturn(setPublicAccessResponse(isPublic));

    Response response = pipelinesApiImpl.updatePipeline(requestBody, org, project, identifier, account, isPublic);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    PipelineCreateResponseBody responseBody = (PipelineCreateResponseBody) response.getEntity();
    assertThat(responseBody.getIdentifier()).isEqualTo(identifier);
    assertThat(responseBody.getGovernanceMetadata()).isNotNull();
    assertThat(responseBody.getGovernanceMetadata().isDeny()).isFalse();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testPipelineUpdateWithOPADeny() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder()
                                                .setDeny(true)
                                                .setStatus("ERROR")
                                                .setMessage("Pipeline does not follow the Policies in the Policy Sets")
                                                .build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(entityModified).build();

    doReturn(pipelineCRUDResult)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(
            any(PipelineEntity.class), any(ChangeType.class), anyBoolean(), anyBoolean(), any(), anyBoolean());

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(account, org, project, yaml, BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0);

    PipelineUpdateRequestBody requestBody = new PipelineUpdateRequestBody();
    requestBody.setPipelineYaml(yaml);
    requestBody.setIdentifier(identifier);
    requestBody.setName(name);
    requestBody.setVersion("1");

    Response response = pipelinesApiImpl.updatePipeline(requestBody, org, project, identifier, account, false);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    PipelineCreateResponseBody responseBody = (PipelineCreateResponseBody) response.getEntity();

    assertThat(responseBody.getGovernanceMetadata()).isNotNull();
    assertThat(responseBody.getGovernanceMetadata().isDeny()).isTrue();
    assertThat(responseBody.getGovernanceMetadata().getStatus()).isEqualTo(GovernanceStatus.ERROR);
    assertThat(responseBody.getGovernanceMetadata().getMessage())
        .isEqualTo("Pipeline does not follow the Policies in the Policy Sets");
    assertThat(responseBody.getIdentifier()).isNull();
  }
}
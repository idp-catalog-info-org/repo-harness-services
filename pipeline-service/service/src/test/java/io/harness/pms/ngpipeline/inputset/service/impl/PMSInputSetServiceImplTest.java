/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PROJECT_IDENTIFIER;
import static io.harness.pms.pipeline.MoveConfigOperationType.INLINE_TO_REMOTE;
import static io.harness.pms.pipeline.MoveConfigOperationType.REMOTE_TO_INLINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.ADITYA_RANA;
import static io.harness.rule.OwnerRule.ARYA;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SANDESH_SALUNKHE;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static java.lang.String.format;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.eraro.ErrorCode;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.InputSetReferenceProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmException;
import io.harness.exception.UnavailableFeatureException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmClearCacheResponse;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.inputset.gitsync.dto.InputSetYamlDTO;
import io.harness.pms.inputset.gitsync.dto.InputSetYamlDTOMapper;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.ngpipeline.inputset.api.utils.InputSetsApiUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.BatchInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.ForceImportInputSetYamlOperationDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.helpers.validate.InputSetValidationHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.pipeline.MoveConfigOperationType;
import io.harness.pms.pipeline.PMSInputSetListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.project.remote.ProjectClient;
import io.harness.repositories.inputset.PMSInputSetRepository;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeInfoHelper;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import retrofit2.Call;
import retrofit2.Response;

@PrepareForTest({InputSetValidationHelper.class})
@OwnedBy(PIPELINE)
public class PMSInputSetServiceImplTest extends PipelineServiceTestBase {
  @Mock GitXSettingsHelper gitXSettingsHelper;
  @Spy @InjectMocks PMSInputSetServiceImpl pmsInputSetServiceMock;
  @Mock private PMSInputSetRepository inputSetRepository;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Mock private GitAwareEntityHelper gitAwareEntityHelper;
  @Mock private PMSPipelineService pipelineService;
  @Mock private InputSetsApiUtils inputSetsApiUtils;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock PipelineTelemetryHelper pipelineTelemetryHelper;
  @Mock ScopeInfoHelper scopeInfoHelper;
  @Mock private ProjectClient projectClient;
  @Mock private OrganizationClient organizationClient;
  private PMSInputSetServiceHelper pmsInputSetServiceHelper;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;

  String ACCOUNT_ID = "account_id";
  String ORG_IDENTIFIER = "orgId";
  String PROJ_IDENTIFIER = "projId";
  String UNIQUE_ID = "uniqueId";
  String PIPELINE_IDENTIFIER = "pipeline_identifier";
  String YAML_GIT_CONFIG_REF = "yaml_git_config_ref";
  String BRANCH = "branch";

  String INPUT_SET_IDENTIFIER = "identifier";
  String NAME = "identifier";
  String YAML;
  String YAMLV1;

  InputSetEntity inputSetEntity;
  InputSetEntity inputSet;
  InputSetEntity inputSetEntityV1;
  InputSetEntity remoteInputSetEntity;
  InputSetEntity inlineHcInputSetEntity;

  String OVERLAY_INPUT_SET_IDENTIFIER = "overlay-identifier";
  List<String> inputSetReferences = ImmutableList.of("inputSet2", "inputSet22");
  String OVERLAY_YAML;

  InputSetEntity overlayInputSetEntity;
  PipelineEntity pipelineEntity;
  PipelineEntity inlineHcPipelineEntity;
  PipelineEntity remotePipelineEntity;

  String REPO_NAME = "testRepo";
  String REPO_NAME2 = "testRepo2";
  ScopeInfo scopeInfo;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    YAML = "inputSet:\n"
        + "  identifier: input1\n"
        + "  name: this name\n"
        + "  description: this has a description too\n"
        + "  orgIdentifier: orgId\n"
        + "  projectIdentifier: projId\n"
        + "  tags:\n"
        + "    company: harness\n"
        + "    kind : normal\n"
        + "  pipeline:\n"
        + "    identifier: \"Test_Pipline11\"\n"
        + "    stages:\n"
        + "      - stage:\n"
        + "          identifier: \"qaStage\"\n"
        + "          spec:\n"
        + "            execution:\n"
        + "              steps:\n"
        + "                - step:\n"
        + "                    identifier: \"httpStep1\"\n"
        + "                    spec:\n"
        + "                      url: www.bing.com";
    inputSetEntity = InputSetEntity.builder()
                         .identifier(INPUT_SET_IDENTIFIER)
                         .name(NAME)
                         .yaml(YAML)
                         .inputSetEntityType(InputSetEntityType.INPUT_SET)
                         .yamlGitConfigRef(YAML_GIT_CONFIG_REF)
                         .branch(BRANCH)
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                         .storeType(StoreType.INLINE)
                         .build();
    inputSet = InputSetEntity.builder()
                   .identifier(INPUT_SET_IDENTIFIER)
                   .name(NAME)
                   .yaml(YAML)
                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                   .yamlGitConfigRef(null)
                   .branch(BRANCH)
                   .accountId(ACCOUNT_ID)
                   .orgIdentifier(ORG_IDENTIFIER)
                   .projectIdentifier(PROJ_IDENTIFIER)
                   .pipelineIdentifier(PIPELINE_IDENTIFIER)
                   .storeType(StoreType.INLINE)
                   .build();
    remoteInputSetEntity = inputSetEntity.withStoreType(StoreType.REMOTE);
    inlineHcInputSetEntity = inputSetEntity.withStoreType(StoreType.INLINE_HC);

    OVERLAY_YAML = "overlayInputSet:\n"
        + "  identifier: overlay1\n"
        + "  name : thisName\n"
        + "  tags:\n"
        + "    isOverlaySet : yes it is\n"
        + "  description: this is an overlay input set\n"
        + "  inputSetReferences:\n"
        + "    - inputSet2\n"
        + "    - inputSet22";
    overlayInputSetEntity = InputSetEntity.builder()
                                .identifier(OVERLAY_INPUT_SET_IDENTIFIER)
                                .name(NAME)
                                .yaml(OVERLAY_YAML)
                                .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                .inputSetReferences(inputSetReferences)
                                .storeType(StoreType.INLINE)
                                .build();

    String inputSetV1YamlFileName = "inputSetV1.yaml";
    YAMLV1 = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(inputSetV1YamlFileName)), StandardCharsets.UTF_8);

    inputSetEntityV1 = InputSetEntity.builder()
                           .identifier(INPUT_SET_IDENTIFIER)
                           .name(NAME)
                           .yaml(YAMLV1)
                           .inputSetEntityType(InputSetEntityType.INPUT_SET)
                           .accountId(ACCOUNT_ID)
                           .orgIdentifier(ORG_IDENTIFIER)
                           .projectIdentifier(PROJ_IDENTIFIER)
                           .pipelineIdentifier(PIPELINE_IDENTIFIER)
                           .storeType(StoreType.INLINE)
                           .harnessVersion(HarnessYamlVersion.V1)
                           .build();

    String pipelineYamlFileName = "failure-strategy.yaml";
    String pipelineYaml = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(pipelineYamlFileName)), StandardCharsets.UTF_8);

    pipelineEntity = PipelineEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .identifier(PIPELINE_IDENTIFIER)
                         .name(PIPELINE_IDENTIFIER)
                         .yaml(pipelineYaml)
                         .storeType(StoreType.INLINE)
                         .build();

    inlineHcPipelineEntity = pipelineEntity.withStoreType(StoreType.INLINE_HC);
    remotePipelineEntity = pipelineEntity.withStoreType(StoreType.REMOTE);

    scopeInfo = getScopeInfo();
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    on(pmsInputSetServiceMock).set("inputSetsApiUtils", inputSetsApiUtils);
    on(pmsInputSetServiceMock).set("gitXSettingsHelper", gitXSettingsHelper);
    on(pmsInputSetServiceMock).set("pipelineTelemetryHelper", pipelineTelemetryHelper);
    on(pmsInputSetServiceMock).set("scopeResolutionHelper", scopeResolutionHelper);
    doReturn(ACCOUNT_ID).when(scopeInfoHelper).getAccountIdentifier(any(), any(), any());
    doReturn(ORG_IDENTIFIER).when(scopeInfoHelper).getOrgIdentifier(any(), any(), any());
    doReturn(PROJ_IDENTIFIER).when(scopeInfoHelper).getProjectIdentifier(any(), any(), any());

    on(pmsInputSetServiceMock).set("projectClient", projectClient);
    on(pmsInputSetServiceMock).set("organizationClient", organizationClient);
    pmsInputSetServiceHelper = new PMSInputSetServiceHelper();
    on(pmsInputSetServiceMock).set("pmsInputSetServiceHelper", pmsInputSetServiceHelper);

    Call<ResponseDTO<Optional<ProjectResponse>>> projectCall = mock(Call.class);
    when(projectClient.getProject(anyString(), anyString(), anyString())).thenReturn(projectCall);
    when(projectCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Optional.of(ProjectResponse.builder().build()))));

    Call<ResponseDTO<Optional<OrganizationResponse>>> organizationCall = mock(Call.class);
    when(organizationClient.getOrganization(anyString(), anyString())).thenReturn(organizationCall);
    when(organizationCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Optional.of(OrganizationResponse.builder().build()))));
    when(scopeResolutionHelper.getScopeInfoOptional(any(), any(), any()))
        .thenReturn(Optional.of(ScopeInfo.builder().uniqueId(UNIQUE_ID).build()));

    when(scopeResolutionHelper.getScopeInfo(any(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .scopeType(ScopeLevel.PROJECT)
                        .uniqueId(UNIQUE_ID)
                        .build());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testServiceLayer() {
    MockedStatic<InputSetValidationHelper> mockSettings = mockStatic(InputSetValidationHelper.class);
    List<InputSetEntity> inputSets = ImmutableList.of(inputSetEntity, overlayInputSetEntity);
    doReturn(pipelineEntity)
        .when(pipelineService)
        .getPipelineMetadata(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean());
    doNothing().when(gitXSettingsHelper).enforceGitExperienceIfApplicable(any(), any(), any());
    for (InputSetEntity entity : inputSets) {
      doReturn(entity).when(inputSetRepository).save(eq(entity), any(), anyBoolean());
      InputSetEntity createdInputSet = pmsInputSetServiceMock.create(entity, false, getScopeInfo());
      assertThat(createdInputSet).isNotNull();
      assertThat(createdInputSet.getAccountId()).isEqualTo(entity.getAccountId());
      assertThat(createdInputSet.getOrgIdentifier()).isEqualTo(entity.getOrgIdentifier());
      assertThat(createdInputSet.getProjectIdentifier()).isEqualTo(entity.getProjectIdentifier());
      assertThat(createdInputSet.getIdentifier()).isEqualTo(entity.getIdentifier());
      assertThat(createdInputSet.getName()).isEqualTo(entity.getName());
      assertThat(createdInputSet.getYaml()).isEqualTo(entity.getYaml());

      doReturn(Optional.of(entity))
          .when(inputSetRepository)
          .find(any(ScopeInfo.class), anyString(), eq(entity.getIdentifier()), anyBoolean(), anyBoolean(), anyBoolean(),
              anyBoolean(), anyBoolean());

      Optional<InputSetEntity> getInputSet = pmsInputSetServiceMock.get(
          getScopeInfo(), PIPELINE_IDENTIFIER, entity.getIdentifier(), false, null, null, false, false, false, false);
      assertThat(getInputSet).isPresent();
      assertThat(getInputSet.get().getAccountId()).isEqualTo(createdInputSet.getAccountId());
      assertThat(getInputSet.get().getOrgIdentifier()).isEqualTo(createdInputSet.getOrgIdentifier());
      assertThat(getInputSet.get().getProjectIdentifier()).isEqualTo(createdInputSet.getProjectIdentifier());
      assertThat(getInputSet.get().getIdentifier()).isEqualTo(createdInputSet.getIdentifier());
      assertThat(getInputSet.get().getName()).isEqualTo(createdInputSet.getName());
      assertThat(getInputSet.get().getYaml()).isEqualTo(createdInputSet.getYaml());

      String DESCRIPTION = "Added a description here";
      InputSetEntity updateInputSetEntity = InputSetEntity.builder()
                                                .identifier(entity.getIdentifier())
                                                .name(NAME)
                                                .description(DESCRIPTION)
                                                .yaml(YAML)
                                                .inputSetEntityType(entity.getInputSetEntityType())
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_IDENTIFIER)
                                                .projectIdentifier(PROJ_IDENTIFIER)
                                                .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                .inputSetReferences(entity.getInputSetReferences())
                                                .build();

      doReturn(updateInputSetEntity)
          .when(inputSetRepository)
          .update(eq(updateInputSetEntity), any(ScopeInfo.class), anyBoolean());
      InputSetEntity updatedInputSet = pmsInputSetServiceMock.update(ChangeType.MODIFY, updateInputSetEntity, false);
      assertThat(updatedInputSet.getAccountId()).isEqualTo(updateInputSetEntity.getAccountId());
      assertThat(updatedInputSet.getOrgIdentifier()).isEqualTo(updateInputSetEntity.getOrgIdentifier());
      assertThat(updatedInputSet.getProjectIdentifier()).isEqualTo(updateInputSetEntity.getProjectIdentifier());
      assertThat(updatedInputSet.getIdentifier()).isEqualTo(updateInputSetEntity.getIdentifier());
      assertThat(updatedInputSet.getName()).isEqualTo(updateInputSetEntity.getName());
      assertThat(updatedInputSet.getDescription()).isEqualTo(updateInputSetEntity.getDescription());
      assertThat(updatedInputSet.getYaml()).isEqualTo(updateInputSetEntity.getYaml());

      InputSetEntity incorrectInputSetEntity = InputSetEntity.builder()
                                                   .identifier(entity.getIdentifier())
                                                   .name(NAME)
                                                   .description(DESCRIPTION)
                                                   .yaml(YAML)
                                                   .inputSetEntityType(entity.getInputSetEntityType())
                                                   .accountId("newAccountID")
                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                   .projectIdentifier(PROJ_IDENTIFIER)
                                                   .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                   .inputSetReferences(entity.getInputSetReferences())
                                                   .build();

      ScopeInfo scopeInfo1 = getScopeInfo();
      scopeInfo1.setAccountIdentifier("newAccountID");

      doReturn(null).when(inputSetRepository).update(eq(incorrectInputSetEntity), any(ScopeInfo.class), anyBoolean());
      assertThatThrownBy(() -> pmsInputSetServiceMock.update(ChangeType.MODIFY, incorrectInputSetEntity, false))
          .isInstanceOf(InvalidRequestException.class);

      doReturn(Optional.of(entity))
          .when(inputSetRepository)
          .find(any(), anyString(), eq(entity.getIdentifier()), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
              anyBoolean());
      doNothing().when(inputSetRepository).delete(any(ScopeInfo.class), anyString(), eq(entity.getIdentifier()));
      boolean delete =
          pmsInputSetServiceMock.delete(getScopeInfo(), PIPELINE_IDENTIFIER, entity.getIdentifier(), 1L, false);
      assertThat(delete).isTrue();
    }
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testList() {
    Criteria criteriaFromFilter = PMSInputSetFilterHelper.createCriteriaForGetList(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, InputSetListTypePMS.ALL, "", false, null, false);
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);

    MockedStatic<InputSetValidationHelper> mockSettings = mockStatic(InputSetValidationHelper.class);
    when(inputSetsApiUtils.isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(any())).thenReturn(false);
    doNothing().when(gitXSettingsHelper).enforceGitExperienceIfApplicable(any(), any(), any());
    doReturn(pipelineEntity)
        .when(pipelineService)
        .getPipelineMetadata(
            any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(ScopeInfo.class), anyBoolean());
    Page<InputSetEntity> page = new PageImpl<>(Arrays.asList(inputSetEntity, overlayInputSetEntity));
    doReturn(page).when(inputSetRepository).findAll(any(), any(), any());

    Page<InputSetEntity> list = pmsInputSetServiceMock.list(criteriaFromFilter, pageRequest, getScopeInfo());
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(2);
    assertThat(list.getContent().get(0).getIdentifier()).isEqualTo(inputSetEntity.getIdentifier());
    assertThat(list.getContent().get(1).getIdentifier()).isEqualTo(overlayInputSetEntity.getIdentifier());
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testDeleteInputSetsOnPipelineDeletion() {
    Criteria criteria = new Criteria();
    criteria.and(InputSetEntityKeys.parentUniqueId)
        .is(pipelineEntity.getParentUniqueId())
        .and(InputSetEntityKeys.pipelineIdentifier)
        .is(PIPELINE_IDENTIFIER);
    Query query = new Query(criteria);

    pmsInputSetServiceMock.deleteInputSetsOnPipelineDeletion(pipelineEntity);

    verify(inputSetRepository, times(1)).deleteAllInputSetsWhenPipelineDeleted(query);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testDeleteInputSetsOnPipelineDeletionWhenDeleteFailed() {
    Criteria criteria = new Criteria();
    criteria.and(InputSetEntityKeys.parentUniqueId)
        .is(pipelineEntity.getParentUniqueId())
        .and(InputSetEntityKeys.pipelineIdentifier)
        .is(PIPELINE_IDENTIFIER);
    Query query = new Query(criteria);

    doThrow(new InvalidRequestException("random exception"))
        .when(inputSetRepository)
        .deleteAllInputSetsWhenPipelineDeleted(query);

    assertThatThrownBy(() -> pmsInputSetServiceMock.deleteInputSetsOnPipelineDeletion(pipelineEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("InputSets for Pipeline [%s] under Project[%s], Organization [%s] couldn't be deleted.",
                PIPELINE_IDENTIFIER, PROJ_IDENTIFIER, ORG_IDENTIFIER));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSwitchValidationFlag() {
    on(pmsInputSetServiceMock).set("inputSetRepository", inputSetRepository);
    when(inputSetRepository.update(any(Criteria.class), any(Update.class)))
        .thenReturn(InputSetEntity.builder().build());
    assertTrue(pmsInputSetServiceMock.switchValidationFlag(inputSetEntity, true, false));
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testSwitchValidationFlagWhenYamlGitConfigRefIsNull() {
    on(pmsInputSetServiceMock).set("inputSetRepository", inputSetRepository);
    Criteria criteria = Criteria.where(InputSetEntityKeys.accountId)
                            .is(ACCOUNT_ID)
                            .and(InputSetEntityKeys.orgIdentifier)
                            .is(ORG_IDENTIFIER)
                            .and(InputSetEntityKeys.projectIdentifier)
                            .is(PROJ_IDENTIFIER)
                            .and(InputSetEntityKeys.pipelineIdentifier)
                            .is(PIPELINE_IDENTIFIER)
                            .and(InputSetEntityKeys.identifier)
                            .is(INPUT_SET_IDENTIFIER);
    Update update = new Update();
    update.set(InputSetEntityKeys.isInvalid, false);
    doReturn(inputSetEntity).when(inputSetRepository).update(criteria, update);
    assertTrue(pmsInputSetServiceMock.switchValidationFlag(inputSet, false, false));
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testSwitchValidationFlagWhenYamlGitConfigRefIsNotNull() {
    on(pmsInputSetServiceMock).set("inputSetRepository", inputSetRepository);
    Criteria criteria = Criteria.where(InputSetEntityKeys.accountId)
                            .is(ACCOUNT_ID)
                            .and(InputSetEntityKeys.orgIdentifier)
                            .is(ORG_IDENTIFIER)
                            .and(InputSetEntityKeys.projectIdentifier)
                            .is(PROJ_IDENTIFIER)
                            .and(InputSetEntityKeys.pipelineIdentifier)
                            .is(PIPELINE_IDENTIFIER)
                            .and(InputSetEntityKeys.identifier)
                            .is(INPUT_SET_IDENTIFIER)
                            .and(InputSetEntityKeys.yamlGitConfigRef)
                            .is(YAML_GIT_CONFIG_REF)
                            .and(InputSetEntityKeys.branch)
                            .is(BRANCH);
    Update update = new Update();
    update.set(InputSetEntityKeys.isInvalid, false);
    doReturn(inputSetEntity).when(inputSetRepository).update(criteria, update);
    assertTrue(pmsInputSetServiceMock.switchValidationFlag(inputSetEntity, false, false));
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetListOfReposSuccessNonEmptyReposList() {
    doReturn(List.of("repo1", "repo2", "repo3")).when(inputSetRepository).findAllUniqueInputSetRepos(any());
    PMSInputSetListRepoResponse result = pmsInputSetServiceMock.getListOfRepos(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, scopeInfo, false);
    assertThat(result).isNotNull();
    assertThat(result.getRepositories()).isEqualTo(List.of("repo1", "repo2", "repo3"));
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetListOfReposSuccessEmptyReposList() {
    PMSInputSetListRepoResponse result = pmsInputSetServiceMock.getListOfRepos(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, scopeInfo, false);
    assertThat(result).isNotNull();
    assertThat(result.getRepositories()).isEmpty();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetListOfReposFailure() {
    List<String> repoList = new ArrayList<>();
    for (int i = 0; i <= 1000; i++) {
      repoList.add("Repo" + i);
    }
    doReturn(repoList).when(inputSetRepository).findAllUniqueInputSetRepos(any());
    InternalServerErrorException internalServerErrorException = new InternalServerErrorException(
        String.format("The size of unique repository list is greater than [%d]", 1000));
    try {
      pmsInputSetServiceMock.getListOfRepos(
          ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, scopeInfo, false);
    } catch (InternalServerErrorException ex) {
      assertThat(ex.getMessage()).isEqualTo(internalServerErrorException.getMessage());
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testUpdateGitMetadataSuccessNoUpdates() {
    PMSUpdateGitDetailsParams updateGitDetailsParams = PMSUpdateGitDetailsParams.builder().build();
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    when(inputSetRepository.find(
             getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, true, false, false, false))
        .thenReturn(Optional.of(remoteInputSetEntity));
    when(inputSetRepository.updateEntity(any(), any())).thenReturn(remoteInputSetEntity);
    String result = pmsInputSetServiceMock.updateGitMetadata(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, updateGitDetailsParams, getScopeInfo(), false);
    assertThat(result).isNotNull().isEqualTo(INPUT_SET_IDENTIFIER);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testUpdateGitMetadataForInlineHCEntity() {
    PMSUpdateGitDetailsParams updateGitDetailsParams = PMSUpdateGitDetailsParams.builder().build();
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    when(inputSetRepository.find(
             getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, true, false, false, false))
        .thenReturn(Optional.of(inlineHcInputSetEntity));
    String result = pmsInputSetServiceMock.updateGitMetadata(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, updateGitDetailsParams, null, false);
    assertThat(result).isNotNull().isEqualTo(INPUT_SET_IDENTIFIER);
    verify(inputSetRepository, times(0)).updateEntity(any(), any());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testUpdateGitMetadataSuccessUpdateWithUpdates() {
    PMSUpdateGitDetailsParams updateGitDetailsParams = PMSUpdateGitDetailsParams.builder()
                                                           .connectorRef("connectorRef")
                                                           .repoName("repoName")
                                                           .filePath("filePath")
                                                           .build();
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    when(inputSetRepository.find(
             getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, true, false, false, false))
        .thenReturn(Optional.of(remoteInputSetEntity));
    when(inputSetRepository.updateEntity(any(), any())).thenReturn(remoteInputSetEntity);
    String result = pmsInputSetServiceMock.updateGitMetadata(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, updateGitDetailsParams, null, false);
    assertThat(result).isNotNull().isEqualTo(INPUT_SET_IDENTIFIER);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testUpdateGitMetadataSuccessUpdateReturnsNullInputSetEntity() {
    PMSUpdateGitDetailsParams updateGitDetailsParams = PMSUpdateGitDetailsParams.builder()
                                                           .connectorRef("connectorRef")
                                                           .repoName("repoName")
                                                           .filePath("filePath")
                                                           .build();
    when(inputSetRepository.find(
             getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, true, false, false, false))
        .thenReturn(Optional.of(inputSetEntity));
    when(inputSetRepository.updateEntity(any(), any())).thenReturn(null);
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    EntityNotFoundException entityNotFoundException = new EntityNotFoundException(
        format("InputSet with id [%s] is not present or has been deleted", INPUT_SET_IDENTIFIER));
    try {
      pmsInputSetServiceMock.updateGitMetadata(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
          INPUT_SET_IDENTIFIER, updateGitDetailsParams, getScopeInfo(), false);
    } catch (EntityNotFoundException ex) {
      assertThat(ex.getMessage()).isEqualTo(entityNotFoundException.getMessage());
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testMoveInputSetEntityInlineToRemote() {
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO = InputSetMoveConfigOperationDTO.builder()
                                                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                                        .moveConfigOperationType(INLINE_TO_REMOTE)
                                                                        .build();
    when(inputSetRepository.updateInputSetEntity(any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(inputSetEntity);
    InputSetEntity result = pmsInputSetServiceMock.moveInputSetEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        inputSetMoveConfigOperationDTO, inputSetEntity, getScopeInfo(), false);
    assertThat(result).isEqualTo(inputSetEntity);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testMoveInputSetEntityRemoteToInline() {
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO = InputSetMoveConfigOperationDTO.builder()
                                                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                                        .moveConfigOperationType(REMOTE_TO_INLINE)
                                                                        .build();
    when(inputSetRepository.updateInputSetEntity(any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(inputSetEntity);
    InputSetEntity result = pmsInputSetServiceMock.moveInputSetEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        inputSetMoveConfigOperationDTO, inputSetEntity, getScopeInfo(), false);
    assertThat(result).isEqualTo(inputSetEntity);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testMoveInputSetEntityInvalidOperationType() {
    MoveConfigOperationType invalidMoveConfigOperationType = mock(MoveConfigOperationType.class);
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO = mock(InputSetMoveConfigOperationDTO.class);
    inputSetMoveConfigOperationDTO.setPipelineIdentifier(PIPELINE_IDENTIFIER);
    inputSetMoveConfigOperationDTO.setMoveConfigOperationType(invalidMoveConfigOperationType);
    doReturn(invalidMoveConfigOperationType).when(inputSetMoveConfigOperationDTO).getMoveConfigOperationType();
    doReturn("INVALID_OPERATION").when(invalidMoveConfigOperationType).name();
    InvalidRequestException exception = assertThrows(InvalidRequestException.class,
        ()
            -> pmsInputSetServiceMock.moveInputSetEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                inputSetMoveConfigOperationDTO, inputSetEntity, getScopeInfo(), false));
    assertThat(exception.getMessage()).isEqualTo("Invalid move config operation specified [INVALID_OPERATION].");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testGetMetadataWithoutValidationsWithScopeInfo() {
    boolean deleted = false;
    boolean loadFromFallbackBranch = true;
    boolean getMetadata = true;
    boolean isParentIdQueryingEnabled = false;
    ScopeInfo scopeInfo = getScopeInfo();

    when(inputSetRepository.find(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata,
             loadFromFallbackBranch, false, isParentIdQueryingEnabled))
        .thenReturn(Optional.of(inputSetEntity));
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    Optional<InputSetEntity> result = pmsInputSetServiceMock.getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata,
        scopeInfo, isParentIdQueryingEnabled);

    assertTrue(result.isPresent());
    assertThat(result.get()).isEqualTo(inputSetEntity);

    verify(inputSetRepository, times(1))
        .find(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata, loadFromFallbackBranch,
            false, isParentIdQueryingEnabled);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testGetMetadataWithoutValidationsWithScopeInfoWithParentIdQueryingEnabled() {
    boolean deleted = false;
    boolean loadFromFallbackBranch = true;
    boolean getMetadata = true;
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo = getScopeInfo();

    when(inputSetRepository.find(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata,
             loadFromFallbackBranch, false, isParentIdQueryingEnabled))
        .thenReturn(Optional.of(inputSetEntity));

    Optional<InputSetEntity> result = pmsInputSetServiceMock.getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata,
        scopeInfo, isParentIdQueryingEnabled);

    assertTrue(result.isPresent());
    assertThat(result.get()).isEqualTo(inputSetEntity);

    verify(inputSetRepository, times(1))
        .find(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata, loadFromFallbackBranch,
            false, isParentIdQueryingEnabled);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testGetMetadataWithoutValidationsWithScopeInfoExceptionHandling() {
    boolean deleted = false;
    boolean loadFromFallbackBranch = true;
    boolean getMetadata = true;
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo = getScopeInfo();

    when(inputSetRepository.find(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata,
             loadFromFallbackBranch, false, isParentIdQueryingEnabled))
        .thenThrow(new InvalidRequestException("Scope information not found"));

    InvalidRequestException thrown = assertThrows(InvalidRequestException.class,
        ()
            -> pmsInputSetServiceMock.getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata, scopeInfo,
                isParentIdQueryingEnabled));

    assertThat(thrown.getMessage()).contains("Error while retrieving input set");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testGetMetadataWithoutValidationsWithScopeInfoExplanationExceptionPropagation() {
    boolean deleted = false;
    boolean loadFromFallbackBranch = true;
    boolean getMetadata = true;
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo = getScopeInfo();

    when(inputSetRepository.find(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata,
             loadFromFallbackBranch, false, isParentIdQueryingEnabled))
        .thenThrow(new ExplanationException("Test explanation", new RuntimeException()));

    ExplanationException thrown = assertThrows(ExplanationException.class,
        ()
            -> pmsInputSetServiceMock.getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata, scopeInfo,
                isParentIdQueryingEnabled));

    assertThat(thrown.getMessage()).isEqualTo("Test explanation");
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetMetadataWithoutValidationsSuccess() {
    boolean deleted = false;
    boolean loadFromFallbackBranch = true;
    boolean getMetadata = true;
    boolean loadFromCache = false;
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    when(inputSetRepository.find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata,
             loadFromFallbackBranch, loadFromCache, false))
        .thenReturn(Optional.of(inputSetEntity));
    Optional<InputSetEntity> result =
        pmsInputSetServiceMock.getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata, null, false);
    assertTrue(result.isPresent());
    assertThat(result.get()).isEqualTo(inputSetEntity);
    verify(inputSetRepository, times(1))
        .find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata, loadFromFallbackBranch,
            loadFromCache, false);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetMetadataWithoutValidationsNotFound() {
    boolean deleted = false;
    boolean loadFromFallbackBranch = true;
    boolean getMetadata = true;
    boolean loadFromCache = false;
    when(inputSetRepository.find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata,
             loadFromFallbackBranch, loadFromCache, false))
        .thenReturn(Optional.empty());
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    Optional<InputSetEntity> result = pmsInputSetServiceMock.getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata,
        getScopeInfo(), false);
    assertTrue(result.isEmpty());
    verify(inputSetRepository, times(1))
        .find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata, loadFromFallbackBranch,
            loadFromCache, false);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetMetadataWithoutValidationsRuntimeException() {
    boolean deleted = false;
    boolean loadFromFallbackBranch = true;
    boolean getMetadata = true;
    boolean loadFromCache = false;
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    when(inputSetRepository.find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata,
             loadFromFallbackBranch, loadFromCache, false))
        .thenThrow(new RuntimeException("Test exception"));
    assertThrows(InvalidRequestException.class,
        ()
            -> pmsInputSetServiceMock.getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata, null, false));
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetMetadataWithoutValidationsExplanationException() {
    boolean deleted = false;
    boolean loadFromFallbackBranch = true;
    boolean getMetadata = true;
    boolean loadFromCache = false;
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    when(inputSetRepository.find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata,
             loadFromFallbackBranch, loadFromCache, false))
        .thenThrow(new ExplanationException("Test exception", new RuntimeException("Test exception")));
    assertThrows(ExplanationException.class,
        ()
            -> pmsInputSetServiceMock.getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata, getScopeInfo(),
                false));
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetMetadataWithoutValidationsHintException() {
    boolean deleted = false;
    boolean loadFromFallbackBranch = true;
    boolean getMetadata = true;
    boolean loadFromCache = false;
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    when(inputSetRepository.find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata,
             loadFromFallbackBranch, loadFromCache, false))
        .thenThrow(new HintException("Test exception"));
    assertThrows(HintException.class,
        ()
            -> pmsInputSetServiceMock.getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata, getScopeInfo(),
                false));
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetMetadataWithoutValidationsScmException() {
    boolean deleted = false;
    boolean loadFromFallbackBranch = true;
    boolean getMetadata = true;
    boolean loadFromCache = false;
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    when(inputSetRepository.find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, !deleted, getMetadata,
             loadFromFallbackBranch, loadFromCache, false))
        .thenThrow(new ScmException(ErrorCode.DEFAULT_ERROR_CODE));
    assertThrows(ScmException.class,
        ()
            -> pmsInputSetServiceMock.getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata, getScopeInfo(),
                false));
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetMetadataSuccess() {
    boolean deleted = false;
    boolean loadFromFallbackBranch = false;
    boolean getMetadata = true;
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    doReturn(Optional.of(inputSetEntity))
        .when(pmsInputSetServiceMock)
        .getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata, getScopeInfo(), false);
    InputSetEntity result = pmsInputSetServiceMock.getMetadata(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata, getScopeInfo(), false);
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(inputSetEntity);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetMetadataFailure() {
    boolean deleted = false;
    boolean loadFromFallbackBranch = false;
    boolean getMetadata = true;
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    doReturn(Optional.empty())
        .when(pmsInputSetServiceMock)
        .getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata, null, false);
    assertThrows(InvalidRequestException.class,
        ()
            -> pmsInputSetServiceMock.getMetadata(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                INPUT_SET_IDENTIFIER, deleted, loadFromFallbackBranch, getMetadata, getScopeInfo(), false));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCheckForInputSetsForPipeline() {
    doReturn(true)
        .when(inputSetRepository)
        .existsByAccountIdAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifierAndDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, true, getScopeInfo(), false);
    assertThat(pmsInputSetServiceMock.checkForInputSetsForPipeline(
                   ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, getScopeInfo(), false))
        .isTrue();

    doReturn(false)
        .when(inputSetRepository)
        .existsByAccountIdAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifierAndDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, true, getScopeInfo(), false);
    assertThat(pmsInputSetServiceMock.checkForInputSetsForPipeline(
                   ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, getScopeInfo(), false))
        .isFalse();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  @Deprecated
  public void testImportInputSetFromRemote() {
    String identifier = "input1";
    String name = "this name";
    String description = "this has a description too";
    String pipelineIdentifier = "Test_Pipline11";
    doReturn(YAML).when(gitAwareEntityHelper).importFile(any(), anyBoolean());
    InputSetEntity inBetweenEntity = PMSInputSetElementMapper.toInputSetEntity(ACCOUNT_ID, YAML);
    InputSetImportRequestDTO inputSetImportRequest =
        InputSetImportRequestDTO.builder().inputSetName(name).inputSetDescription(description).build();
    doReturn(inputSetEntity).when(inputSetRepository).saveForImportedYAML(any(), any(), anyBoolean());
    doNothing().when(gitXSettingsHelper).enforceGitExperienceIfApplicable(any(), any(), any());
    doReturn("repoUrl")
        .when(pmsInputSetServiceMock)
        .getRepoUrlAndCheckForFileUniqueness(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, getScopeInfo(), true);
    InputSetEntity savedEntity = pmsInputSetServiceMock.importInputSetFromRemote(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, pipelineIdentifier, identifier, inputSetImportRequest, true, getScopeInfo());
    assertThat(savedEntity).isEqualTo(inputSetEntity);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCreateForOldGitSyncWithFFEnabled() {
    MockedStatic<InputSetValidationHelper> mockSettings = mockStatic(InputSetValidationHelper.class);
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    pmsInputSetServiceMock.create(inputSetEntity, false, null);
    verify(inputSetRepository, times(1))
        .saveForOldGitSync(inputSetEntity, InputSetYamlDTOMapper.toDTO(inputSetEntity), null);
    verify(inputSetRepository, times(0)).save(any(), any(), anyBoolean());
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateWithExceptionsWithFFEnabled() {
    MockedStatic<InputSetValidationHelper> mockSettings = mockStatic(InputSetValidationHelper.class);
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(pipelineEntity)
        .when(pipelineService)
        .getPipelineMetadata(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean());
    doThrow(new DuplicateKeyException("msg")).when(inputSetRepository).save(inputSetEntity, null, true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.create(inputSetEntity, false, null))
        .isInstanceOf(DuplicateFieldException.class);

    doThrow(new ExplanationException("msg", null)).when(inputSetRepository).save(inputSetEntity, null, true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.create(inputSetEntity, false, null))
        .isInstanceOf(ExplanationException.class);
    doThrow(new HintException("msg", null)).when(inputSetRepository).save(inputSetEntity, null, true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.create(inputSetEntity, false, null))
        .isInstanceOf(HintException.class);
    doThrow(new ScmException(ErrorCode.DEFAULT_ERROR_CODE)).when(inputSetRepository).save(inputSetEntity, null, true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.create(inputSetEntity, false, null))
        .isInstanceOf(ScmException.class);

    doThrow(new NullPointerException()).when(inputSetRepository).save(inputSetEntity, null, true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.create(inputSetEntity, false, null))
        .isInstanceOf(InvalidRequestException.class);

    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetForOldGitSync() {
    MockedStatic<InputSetValidationHelper> mockSettings = mockStatic(InputSetValidationHelper.class);
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(Optional.of(inputSetEntity))
        .when(inputSetRepository)
        .findForOldGitSync(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false);
    pmsInputSetServiceMock.get(
        scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false, null, null, false, false, false, false);
    verify(inputSetRepository, times(1))
        .findForOldGitSync(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false);
    verify(inputSetRepository, times(0))
        .find(any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetWithExceptions() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);

    doThrow(new ExplanationException("msg", null))
        .when(inputSetRepository)
        .find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false, false, false, false);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.get(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false,
                               null, null, false, false, false, false))
        .isInstanceOf(ExplanationException.class);
    doThrow(new HintException("msg", null))
        .when(inputSetRepository)
        .find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false, false, false, false);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.get(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false,
                               null, null, false, false, false, false))
        .isInstanceOf(HintException.class);
    doThrow(new ScmException(ErrorCode.DEFAULT_ERROR_CODE))
        .when(inputSetRepository)
        .find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false, false, false, false);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.get(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false,
                               null, null, false, false, false, false))
        .isInstanceOf(ScmException.class);

    doThrow(new NullPointerException())
        .when(inputSetRepository)
        .find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false, false, false, false);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.get(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false,
                               null, null, false, false, false, false))
        .isInstanceOf(InvalidRequestException.class);

    doReturn(Optional.of(inputSetEntity.withStoreType(StoreType.REMOTE)))
        .when(inputSetRepository)
        .find(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false, false, false, false);
    // without mocks this will throw an exception
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.get(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false,
                               null, null, false, false, false, false));
    MockedStatic<InputSetValidationHelper> mockSettings = mockStatic(InputSetValidationHelper.class);
    // no exception with the mock
    pmsInputSetServiceMock.get(
        scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false, null, null, false, false, false, false);
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateForOldGitSync() {
    InputSetYamlDTO inputSetYamlDTO = InputSetYamlDTOMapper.toDTO(inputSetEntity);
    ChangeType c = ChangeType.MODIFY;
    MockedStatic<InputSetValidationHelper> mockSettings = mockStatic(InputSetValidationHelper.class);
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);

    setupGitContext(GitEntityInfo.builder().isNewBranch(true).branch("newBranch").yamlGitConfigId("repo").build());
    doReturn(inputSetEntity)
        .when(inputSetRepository)
        .updateForOldGitSync(inputSetEntity, inputSetYamlDTO, c, getScopeInfo(), true);
    InputSetEntity updateIntoNewBranch = pmsInputSetServiceMock.update(c, inputSetEntity, false, getScopeInfo());
    assertThat(updateIntoNewBranch).isEqualTo(inputSetEntity);
    verify(inputSetRepository, times(0)).findForOldGitSync(any(), any(), any(), anyBoolean(), anyBoolean());

    setupGitContext(GitEntityInfo.builder().isNewBranch(false).branch("branch").yamlGitConfigId("repo").build());

    doReturn(Optional.empty())
        .when(inputSetRepository)
        .findForOldGitSync(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity, false, getScopeInfo()))
        .isInstanceOf(InvalidRequestException.class);

    doReturn(Optional.of(inputSetEntity.withVersion(3L)))
        .when(inputSetRepository)
        .findForOldGitSync(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity.withVersion(10L), false, getScopeInfo()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("is not on the correct version.");

    doReturn(Optional.of(inputSetEntity))
        .when(inputSetRepository)
        .findForOldGitSync(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, true);
    doReturn(inputSetEntity)
        .when(inputSetRepository)
        .updateForOldGitSync(
            inputSetEntity.withIsEntityInvalid(false).withIsInvalid(false), inputSetYamlDTO, c, getScopeInfo(), true);
    InputSetEntity simpleUpdatedEntity = pmsInputSetServiceMock.update(c, inputSetEntity, false, getScopeInfo());
    assertThat(simpleUpdatedEntity).isEqualTo(inputSetEntity);

    verify(inputSetRepository, times(0)).update(any(), any(ScopeInfo.class), anyBoolean());
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateForOldGitSyncWithErrors() {
    InputSetYamlDTO inputSetYamlDTO = InputSetYamlDTOMapper.toDTO(inputSetEntity);
    ChangeType c = ChangeType.MODIFY;
    MockedStatic<InputSetValidationHelper> mockSettings = mockStatic(InputSetValidationHelper.class);
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    setupGitContext(GitEntityInfo.builder().isNewBranch(true).branch("newBranch").yamlGitConfigId("repo").build());

    doReturn(null)
        .when(inputSetRepository)
        .updateForOldGitSync(inputSetEntity, inputSetYamlDTO, c, getScopeInfo(), true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity, false, getScopeInfo()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("could not be updated.");

    doThrow(new ExplanationException("e", null))
        .when(inputSetRepository)
        .updateForOldGitSync(inputSetEntity, inputSetYamlDTO, c, getScopeInfo(), true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity, false, getScopeInfo()))
        .isInstanceOf(ExplanationException.class);
    doThrow(new HintException("e"))
        .when(inputSetRepository)
        .updateForOldGitSync(inputSetEntity, inputSetYamlDTO, c, getScopeInfo(), true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity, false, getScopeInfo()))
        .isInstanceOf(HintException.class);
    doThrow(new ScmException("e", null))
        .when(inputSetRepository)
        .updateForOldGitSync(inputSetEntity, inputSetYamlDTO, c, getScopeInfo(), true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity, false, getScopeInfo()))
        .isInstanceOf(ScmException.class);

    doThrow(new NullPointerException())
        .when(inputSetRepository)
        .updateForOldGitSync(inputSetEntity, inputSetYamlDTO, c, getScopeInfo(), true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity, false, getScopeInfo()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Error while updating input set");

    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeleteWithError() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doThrow(new NullPointerException())
        .when(inputSetRepository)
        .delete(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER);
    assertThatThrownBy(
        () -> pmsInputSetServiceMock.delete(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("could not be deleted.");
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testDeleteInlineHCInputSet() {
    InputSetEntity inlineHCEntity = inputSetEntity.withStoreType(StoreType.INLINE_HC);
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(getScopeInfo());
    when(inputSetRepository.find(
             getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, true, false, false, false))
        .thenReturn(Optional.of(inlineHCEntity));
    doNothing().when(inputSetRepository).delete(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER);
    Boolean data =
        pmsInputSetServiceMock.delete(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, null, false);
    assertTrue(data);
    verify(gitAwareEntityHelper, times(1)).deleteEntityOnGit(any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeleteForOldGitSync() {
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(getScopeInfo());
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);

    doReturn(Optional.empty())
        .when(inputSetRepository)
        .findForOldGitSync(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false);
    assertThatThrownBy(
        () -> pmsInputSetServiceMock.delete(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("doesn't exist.");

    doReturn(Optional.of(inputSetEntity.withVersion(2L)))
        .when(inputSetRepository)
        .findForOldGitSync(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false);
    assertThatThrownBy(
        () -> pmsInputSetServiceMock.delete(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, 9L, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(" is not on the correct version.");

    doReturn(Optional.of(inputSetEntity))
        .when(inputSetRepository)
        .findForOldGitSync(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false);

    InputSetEntity withDeleted = inputSetEntity.withDeleted(true);
    InputSetYamlDTO inputSetYamlDTO = InputSetYamlDTOMapper.toDTO(withDeleted);
    boolean delete =
        pmsInputSetServiceMock.delete(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, null, false);
    assertThat(delete).isTrue();

    doThrow(new NullPointerException())
        .when(inputSetRepository)
        .deleteForOldGitSync(withDeleted, inputSetYamlDTO, getScopeInfo());
    assertThatThrownBy(
        () -> pmsInputSetServiceMock.delete(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("couldn't be deleted");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSyncInputSetWithGit() {
    InputSetReferenceProtoDTO inputSetReferenceProtoDTO =
        InputSetReferenceProtoDTO.newBuilder()
            .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
            .setOrgIdentifier(StringValue.of(ORG_IDENTIFIER))
            .setProjectIdentifier(StringValue.of(PROJ_IDENTIFIER))
            .setPipelineIdentifier(StringValue.of(PIPELINE_IDENTIFIER))
            .setIdentifier(StringValue.of(INPUT_SET_IDENTIFIER))
            .build();
    EntityDetailProtoDTO entityDetailProtoDTO =
        EntityDetailProtoDTO.newBuilder().setInputSetRef(inputSetReferenceProtoDTO).build();
    doReturn(Optional.empty())
        .when(inputSetRepository)
        .findForOldGitSync(ScopeInfo.builder()
                               .accountIdentifier(ACCOUNT_ID)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJ_IDENTIFIER)
                               .scopeType(ScopeLevel.PROJECT)
                               .uniqueId(UNIQUE_ID)
                               .build(),
            PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false);
    assertThatThrownBy(() -> pmsInputSetServiceMock.syncInputSetWithGit(entityDetailProtoDTO))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateGitFilePath() {
    String newFilePath = "folder/.harness/file.yaml";
    doReturn(inputSetEntity.withDescription("after update dummy description"))
        .when(inputSetRepository)
        .update(any(), any(), any(), any(), any());
    InputSetEntity inputSetEntityUpdated = pmsInputSetServiceMock.updateGitFilePath(inputSetEntity, newFilePath);
    assertThat(inputSetEntityUpdated.getDescription()).isEqualTo("after update dummy description");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildInvalidYamlException() {
    InvalidYamlException invalidYamlException =
        pmsInputSetServiceMock.buildInvalidYamlException("error msg from test", "yaml: this");
    assertThat(invalidYamlException.getYaml()).isEqualTo("yaml: this");
    YamlSchemaErrorWrapperDTO metadata = (YamlSchemaErrorWrapperDTO) invalidYamlException.getMetadata();
    List<YamlSchemaErrorDTO> schemaErrors = metadata.getSchemaErrors();
    assertThat(schemaErrors).hasSize(1);
    YamlSchemaErrorDTO yamlSchemaErrorDTO = schemaErrors.get(0);
    assertThat(yamlSchemaErrorDTO.getMessage()).isEqualTo("error msg from test");
    assertThat(yamlSchemaErrorDTO.getFqn()).isEqualTo("$.inputSet");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testListWithCriteria() {
    Criteria randomCriteria = Criteria.where("thisKey").is("thisValue");
    doReturn(Collections.singletonList(InputSetEntity.builder().identifier("thisId").build()))
        .when(inputSetRepository)
        .findAll(randomCriteria);
    List<InputSetEntity> list = pmsInputSetServiceMock.list(randomCriteria);
    assertThat(list).hasSize(1);
    assertThat(list.get(0).getIdentifier()).isEqualTo("thisId");
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetRepoUrlAndCheckForFileUniqueness() {
    String repoUrl = "repoUrl123";
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().filePath("filePath").build();
    MockedStatic<GitAwareContextHelper> utilities = mockStatic(GitAwareContextHelper.class);
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);

    doReturn(repoUrl).when(gitAwareEntityHelper).getRepoUrl(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(true)
        .when(inputSetRepository)
        .checkIfInputSetWithGivenFilePathExists(ACCOUNT_ID, repoUrl, gitEntityInfo.getFilePath());
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.getRepoUrlAndCheckForFileUniqueness(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, null, false))
        .isInstanceOf(DuplicateFileImportException.class);
    assertThat(pmsInputSetServiceMock.getRepoUrlAndCheckForFileUniqueness(
                   ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, true, null, false))
        .isEqualTo(repoUrl);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testImportInputSetsValidationChecks() {
    String importedInputSetYaml = "inputSet:\n"
        + "  identifier: \"inputSet2\"\n"
        + "  pipeline:\n"
        + "    identifier: \"asdfasdfsadfadsfsaf\"\n"
        + "    stages:\n"
        + "    - stage:\n"
        + "        identifier: \"asdfasdf\"\n"
        + "        type: \"Approval\"\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "            - step:\n"
        + "                identifier: \"sdfasdfasfda\"\n"
        + "                type: \"HarnessApproval\"\n"
        + "                spec:\n"
        + "                  approvers:\n"
        + "                    minimumCount: 1\n"
        + "                    userGroups:\n"
        + "                    - \"account.ug3\"\n"
        + "  name: \"inputSet2\"\n"
        + "  orgIdentifier: \"default\"\n"
        + "  projectIdentifier: \"GitX_Remote\"\n";
    String orgIdentifier = "default";
    String projectIdentifier = "GitX_Remote";
    String pipelineIdentifier = "asdfasdfsadfadsfsaf";
    String inputSetIdentifier = "inputSet2";
    InputSetImportRequestDTO requestDTO = InputSetImportRequestDTO.builder()
                                              .inputSetName("inputSet2")
                                              .inputSetDescription("junk value description")
                                              .build();

    Assertions.assertDoesNotThrow(
        ()
            -> pmsInputSetServiceMock.checkAndThrowMismatchInImportedInputSetMetadata(orgIdentifier, projectIdentifier,
                pipelineIdentifier, inputSetIdentifier, requestDTO, importedInputSetYaml));
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCreateInputSetV1WithFFEnabled() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(pipelineEntity)
        .when(pipelineService)
        .getPipelineMetadata(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean());
    MockedStatic<InputSetValidationHelper> mockSettings = mockStatic(InputSetValidationHelper.class);
    doReturn(inputSetEntityV1).when(inputSetRepository).save(inputSetEntityV1, null, true);
    InputSetEntity inputSetEntity = pmsInputSetServiceMock.create(inputSetEntityV1, false, null);
    assertThat(inputSetEntity).isNotNull();
    assertThat(inputSetEntityV1.getYaml()).isEqualTo(YAMLV1);
    assertThat(inputSetEntityV1.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
    mockSettings.close();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateInputSetV1WithFFEnabled() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(inputSetEntityV1).when(inputSetRepository).update(inputSetEntityV1, getScopeInfo(), true);
    InputSetEntity inputSetEntity =
        pmsInputSetServiceMock.update(ChangeType.MODIFY, inputSetEntityV1, false, getScopeInfo());
    assertThat(inputSetEntity).isNotNull();
    assertThat(inputSetEntityV1.getYaml()).isEqualTo(YAMLV1);
    assertThat(inputSetEntityV1.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetV1() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(Optional.of(inputSetEntityV1))
        .when(inputSetRepository)
        .find(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false, false, false, false);
    Optional<InputSetEntity> optionalInputSetEntity = pmsInputSetServiceMock.get(
        scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false, null, null, false, false, false, false);
    assertThat(optionalInputSetEntity.isPresent()).isTrue();
    InputSetEntity inputSetEntity = optionalInputSetEntity.get();
    assertThat(inputSetEntity.getYaml()).isEqualTo(YAMLV1);
    assertThat(inputSetEntity.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = ADITYA_RANA)
  @Category(UnitTests.class)
  public void testInvalidInputSetWithFF() {
    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.PIPE_RESTRICT_INVALID_TEMPLATE_AND_INPUT_SET_YAML_THROW_EXCEPTION.name());
    String originalInputSetYamlFileName = "invalidInputSetTest.yaml";
    String originalInvalidInputSetYaml = readFile(originalInputSetYamlFileName);

    InputSetEntity inputSetEntityV1 = InputSetEntity.builder()
                                          .identifier(INPUT_SET_IDENTIFIER)
                                          .name(NAME)
                                          .yaml(originalInvalidInputSetYaml)
                                          .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                          .yamlGitConfigRef(YAML_GIT_CONFIG_REF)
                                          .branch(BRANCH)
                                          .accountId(ACCOUNT_ID)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJ_IDENTIFIER)
                                          .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                          .storeType(StoreType.REMOTE)
                                          .build();

    doReturn(Optional.of(inputSetEntityV1))
        .when(pmsInputSetServiceMock)
        .getWithoutValidations(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false, false, false, false);

    Optional<InputSetEntity> optionalInputSetEntity = pmsInputSetServiceMock.get(
        scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false, null, null, false, false, false, false);
    assertThat(optionalInputSetEntity.isPresent()).isTrue();
    assertThat(optionalInputSetEntity.get().isEntityInvalid()).isEqualTo(Boolean.TRUE);
  }

  @Test
  @Owner(developers = ADITYA_RANA)
  @Category(UnitTests.class)
  public void testValidInputSetWithFF() {
    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.PIPE_RESTRICT_INVALID_TEMPLATE_AND_INPUT_SET_YAML_THROW_EXCEPTION.name());

    InputSetEntity inputSetEntityV1 = InputSetEntity.builder()
                                          .identifier(INPUT_SET_IDENTIFIER)
                                          .name(NAME)
                                          .yaml(YAML)
                                          .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                          .yamlGitConfigRef(YAML_GIT_CONFIG_REF)
                                          .branch(BRANCH)
                                          .accountId(ACCOUNT_ID)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJ_IDENTIFIER)
                                          .pipelineIdentifier("Test_Pipline11")
                                          .storeType(StoreType.REMOTE)
                                          .build();

    doReturn(Optional.of(inputSetEntityV1))
        .when(pmsInputSetServiceMock)
        .getWithoutValidations(getScopeInfo(), "Test_Pipline11", INPUT_SET_IDENTIFIER, false, false, false, false);

    Optional<InputSetEntity> optionalInputSetEntity = pmsInputSetServiceMock.get(
        scopeInfo, "Test_Pipline11", INPUT_SET_IDENTIFIER, false, null, null, false, false, false, false);
    assertThat(optionalInputSetEntity.isPresent()).isTrue();
    assertThat(optionalInputSetEntity.get().isEntityInvalid()).isEqualTo(Boolean.FALSE);
  }

  private ScopeInfo getScopeInfo(String accountId, String orgId, String projectId) {
    return ScopeInfo.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .uniqueId("unique-id")
        .build();
  }

  private ScopeInfo getScopeInfo() {
    return ScopeInfo.builder()
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJ_IDENTIFIER)
        .uniqueId(UNIQUE_ID)
        .scopeType(ScopeLevel.PROJECT)
        .build();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testDeleteInputSetV1() {
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(getScopeInfo());
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    when(inputSetRepository.find(
             getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, true, false, false, false))
        .thenReturn(Optional.of(inputSetEntity));
    doNothing().when(inputSetRepository).delete(scopeInfo, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER);
    boolean deleted =
        pmsInputSetServiceMock.delete(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, null, false);
    assertThat(deleted).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testImportInputSetV1FromRemote() {
    String identifier = "set1";
    String name = "set1";
    String description = "this has a description too";
    doReturn(YAMLV1).when(gitAwareEntityHelper).importFile(any(), anyBoolean());
    InputSetEntity inBetweenEntity = PMSInputSetElementMapper.toInputSetEntityV1(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, YAMLV1, InputSetEntityType.INPUT_SET);
    InputSetImportRequestDTO inputSetImportRequest = InputSetImportRequestDTO.builder()
                                                         .inputSetName(name)
                                                         .inputSetDescription(description)
                                                         .version(HarnessYamlVersion.V1)
                                                         .build();
    doReturn(inputSetEntityV1).when(inputSetRepository).saveForImportedYAML(any(), any(), anyBoolean());
    doReturn("repoUrl")
        .when(pmsInputSetServiceMock)
        .getRepoUrlAndCheckForFileUniqueness(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, getScopeInfo(), true);
    InputSetEntity savedEntity = pmsInputSetServiceMock.importInputSetFromRemote(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, identifier, inputSetImportRequest, true, getScopeInfo());
    assertThat(savedEntity).isEqualTo(inputSetEntityV1);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMoveConfigInlineToRemote() {
    doReturn("repoUrl").when(gitAwareEntityHelper).getRepoUrl(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO =
        InputSetMoveConfigOperationDTO.builder().moveConfigOperationType(INLINE_TO_REMOTE).build();
    doReturn(inputSetEntity)
        .when(inputSetRepository)
        .updateInputSetEntity(any(), any(), any(), any(), any(), anyBoolean());
    InputSetEntity movedInputSet = pmsInputSetServiceMock.moveInputSetEntity(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, inputSetMoveConfigOperationDTO, inputSetEntity, getScopeInfo(), false);
    assertEquals(movedInputSet.getIdentifier(), INPUT_SET_IDENTIFIER);
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    assertEquals(StoreType.REMOTE, gitEntityInfo.getStoreType());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMoveConfigRemoteToInline() {
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO =
        InputSetMoveConfigOperationDTO.builder().moveConfigOperationType(REMOTE_TO_INLINE).build();
    doReturn(inputSetEntity)
        .when(inputSetRepository)
        .updateInputSetEntity(any(), any(), any(), any(), any(), anyBoolean());
    InputSetEntity movedInputSet = pmsInputSetServiceMock.moveInputSetEntity(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, inputSetMoveConfigOperationDTO, inputSetEntity, getScopeInfo(), false);
    assertEquals(movedInputSet.getIdentifier(), INPUT_SET_IDENTIFIER);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testValidateIndependentInputSetSettingIsOffForSameRepo() {
    InputSetEntity inputSet = InputSetEntity.builder()
                                  .accountId(ACCOUNT_ID)
                                  .orgIdentifier(ORG_IDENTIFIER)
                                  .projectIdentifier(PROJ_IDENTIFIER)
                                  .identifier(INPUT_SET_IDENTIFIER)
                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                  .build();
    when(inputSetsApiUtils.isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(any())).thenReturn(false);
    PipelineEntity pipeline = PipelineEntity.builder().identifier(PIPELINE_IDENTIFIER).repo(REPO_NAME).build();

    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .repoName(REPO_NAME)
                                      .connectorRef("connectorRef")
                                      .isNewBranch(true)
                                      .branch("branch")
                                      .build();
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);

    Assertions.assertDoesNotThrow(() -> pmsInputSetServiceMock.validateInputSetSetting(inputSet, pipeline));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testValidateIndependentInputSetSettingIsOffForDiffRepo() {
    InputSetEntity inputSet = InputSetEntity.builder()
                                  .accountId(ACCOUNT_ID)
                                  .orgIdentifier(ORG_IDENTIFIER)
                                  .projectIdentifier(PROJ_IDENTIFIER)
                                  .identifier(INPUT_SET_IDENTIFIER)
                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                  .build();
    when(inputSetsApiUtils.isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(any())).thenReturn(false);
    PipelineEntity pipeline = PipelineEntity.builder().identifier(PIPELINE_IDENTIFIER).repo(REPO_NAME).build();

    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .repoName(REPO_NAME2)
                                      .connectorRef("connectorRef")
                                      .isNewBranch(true)
                                      .branch("branch")
                                      .storeType(StoreType.REMOTE)
                                      .build();
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);

    assertThrows(HintException.class, () -> pmsInputSetServiceMock.validateInputSetSetting(inputSet, pipeline));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testValidateIndependentInputSetSettingIsOnForDiffRepo() {
    InputSetEntity inputSet = InputSetEntity.builder()
                                  .accountId(ACCOUNT_ID)
                                  .orgIdentifier(ORG_IDENTIFIER)
                                  .projectIdentifier(PROJ_IDENTIFIER)
                                  .identifier(INPUT_SET_IDENTIFIER)
                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                  .build();
    when(inputSetsApiUtils.isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(any())).thenReturn(false);
    PipelineEntity pipeline = PipelineEntity.builder().identifier(PIPELINE_IDENTIFIER).repo(REPO_NAME).build();

    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .repoName(REPO_NAME2)
                                      .connectorRef("connectorRef")
                                      .isNewBranch(true)
                                      .branch("branch")
                                      .build();
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);

    Assertions.assertDoesNotThrow(() -> pmsInputSetServiceMock.validateInputSetSetting(inputSet, pipeline));
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testApplyGitXSettingsIfApplicable() {
    pmsInputSetServiceMock.applyGitXSettingsIfApplicable(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    InOrder inOrder = inOrder(gitXSettingsHelper);
    inOrder.verify(gitXSettingsHelper).setDefaultStoreTypeForEntities(any(), any(), any(), any());
    inOrder.verify(gitXSettingsHelper).setConnectorRefForRemoteEntity(any(), any(), any());
    inOrder.verify(gitXSettingsHelper).setDefaultRepoForRemoteEntity(any(), any(), any());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testValidateRequestForForceImportInputSetForPipelineId() {
    ForceImportInputSetYamlOperationDTO operationDTO = ForceImportInputSetYamlOperationDTO.builder()
                                                           .identifier(INPUT_SET_IDENTIFIER)
                                                           .connectorRef("connectorRef")
                                                           .filePath("filePath")
                                                           .repoName("repoName")
                                                           .build();

    assertThatThrownBy(() -> pmsInputSetServiceMock.validateForceImportRequest(ACCOUNT_ID, operationDTO))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testForceImportInputSetFromRemoteSuccess() {
    String v0Yaml = "inputSet:\n"
        + "  name: identifier\n"
        + "  tags: {}\n"
        + "  identifier: identifier\n"
        + "  orgIdentifier: default\n"
        + "  projectIdentifier: proj1\n"
        + "  pipeline:\n"
        + "    identifier: p1\n"
        + "    stages:\n"
        + "      - stage:\n"
        + "          identifier: s1\n"
        + "          type: Approval\n"
        + "          spec:\n"
        + "            execution:\n"
        + "              steps:\n"
        + "                - step:\n"
        + "                    identifier: ap1\n"
        + "                    type: HarnessApproval\n"
        + "                    spec:\n"
        + "                      approvers:\n"
        + "                        userGroups:\n"
        + "                          - account._account_all_users\n";

    ForceImportInputSetYamlOperationDTO requestDTO = ForceImportInputSetYamlOperationDTO.builder()
                                                         .identifier(INPUT_SET_IDENTIFIER)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJ_IDENTIFIER)
                                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                         .branch("main")
                                                         .filePath(".harness/myPipeline.yaml")
                                                         .connectorRef("githubConnectorRef")
                                                         .repoName("repo")
                                                         .build();

    doReturn("url")
        .when(pmsInputSetServiceMock)
        .getRepoUrlAndCheckForFileUniqueness(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INPUT_SET_IDENTIFIER, true, null, true);
    doReturn(v0Yaml).when(gitAwareEntityHelper).fetchYAMLFromRemote(any(), anyBoolean());
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier(INPUT_SET_IDENTIFIER)
                                        .name("is1")
                                        .yaml(v0Yaml)
                                        .build();

    doReturn(inputSetEntity).when(inputSetRepository).saveForImportedYAML(any(), any(), anyBoolean());

    ArgumentCaptor<InputSetEntity> operationDTOCaptor = ArgumentCaptor.forClass(InputSetEntity.class);

    pmsInputSetServiceMock.forceImportInputSet(ACCOUNT_ID, requestDTO, null);

    verify(inputSetRepository).saveForImportedYAML(operationDTOCaptor.capture(), any(), anyBoolean());
    assertThat(operationDTOCaptor.getValue().getIdentifier()).isEqualTo(INPUT_SET_IDENTIFIER);
    assertThat(operationDTOCaptor.getValue().getRepoURL()).isEqualTo("url");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testForceImportInputSetForMismatchedMetadataParams() {
    String v0Yaml = "inputSet:\n"
        + "  name: mismatchedIdentifier\n"
        + "  tags: {}\n"
        + "  identifier: mismatchedIdentifier\n"
        + "  orgIdentifier: default\n"
        + "  projectIdentifier: proj1\n"
        + "  pipeline:\n"
        + "    identifier: p1\n"
        + "    stages:\n"
        + "      - stage:\n"
        + "          identifier: s1\n"
        + "          type: Approval\n"
        + "          spec:\n"
        + "            execution:\n"
        + "              steps:\n"
        + "                - step:\n"
        + "                    identifier: ap1\n"
        + "                    type: HarnessApproval\n"
        + "                    spec:\n"
        + "                      approvers:\n"
        + "                        userGroups:\n"
        + "                          - account._account_all_users\n";

    ForceImportInputSetYamlOperationDTO requestDTO = ForceImportInputSetYamlOperationDTO.builder()
                                                         .identifier(INPUT_SET_IDENTIFIER)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJ_IDENTIFIER)
                                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                         .branch("main")
                                                         .filePath(".harness/myPipeline.yaml")
                                                         .connectorRef("githubConnectorRef")
                                                         .repoName("repo")
                                                         .build();

    doReturn("url")
        .when(pmsInputSetServiceMock)
        .getRepoUrlAndCheckForFileUniqueness(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INPUT_SET_IDENTIFIER, true, null, true);
    doReturn(v0Yaml).when(gitAwareEntityHelper).fetchYAMLFromRemote(any(), anyBoolean());
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier("mismatchedIdentifier")
                                        .name("is1")
                                        .yaml(v0Yaml)
                                        .build();

    doReturn(inputSetEntity).when(inputSetRepository).saveForImportedYAML(any(), any(), anyBoolean());

    ArgumentCaptor<InputSetEntity> operationDTOCaptor = ArgumentCaptor.forClass(InputSetEntity.class);

    pmsInputSetServiceMock.forceImportInputSet(ACCOUNT_ID, requestDTO, null);

    verify(inputSetRepository).saveForImportedYAML(operationDTOCaptor.capture(), any(), anyBoolean());
    assertThat(operationDTOCaptor.getValue().getIdentifier()).isEqualTo("mismatchedIdentifier");
    assertThat(operationDTOCaptor.getValue().getName()).isEqualTo("mismatchedIdentifier");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testForceImportInputSetWithInvalidIdentifierInYaml() {
    String v0Yaml = "inputSet:\n"
        + "  name: invalidIdentifier\n"
        + "  tags: {}\n"
        + "  identifier: 1invalid-Identifier\n"
        + "  orgIdentifier: default\n"
        + "  projectIdentifier: proj1\n"
        + "  pipeline:\n"
        + "    identifier: p1\n";

    ForceImportInputSetYamlOperationDTO requestDTO = ForceImportInputSetYamlOperationDTO.builder()
                                                         .identifier(INPUT_SET_IDENTIFIER)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJ_IDENTIFIER)
                                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                         .branch("main")
                                                         .filePath(".harness/myPipeline.yaml")
                                                         .connectorRef("githubConnectorRef")
                                                         .repoName("repo")
                                                         .build();

    doReturn("url")
        .when(pmsInputSetServiceMock)
        .getRepoUrlAndCheckForFileUniqueness(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INPUT_SET_IDENTIFIER, true, null, true);
    doReturn(v0Yaml).when(gitAwareEntityHelper).fetchYAMLFromRemote(any(), anyBoolean());

    assertThatThrownBy(() -> pmsInputSetServiceMock.forceImportInputSet(ACCOUNT_ID, requestDTO, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Input Set Identifier must be up to 128 characters, start with a letter");

    verify(inputSetRepository, never()).saveForImportedYAML(any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testUsageOfScopeFromRequestsForForceImport() {
    String v0Yaml = "inputSet:\n"
        + "  name: identifier\n"
        + "  tags: {}\n"
        + "  identifier: identifier\n"
        + "  orgIdentifier: Invalid\n"
        + "  projectIdentifier: Invalid\n"
        + "  pipeline:\n"
        + "    identifier: p1\n"
        + "    stages:\n"
        + "      - stage:\n"
        + "          identifier: s1\n"
        + "          type: Approval\n"
        + "          spec:\n"
        + "            execution:\n"
        + "              steps:\n"
        + "                - step:\n"
        + "                    identifier: ap1\n"
        + "                    type: HarnessApproval\n"
        + "                    spec:\n"
        + "                      approvers:\n"
        + "                        userGroups:\n"
        + "                          - account._account_all_users\n";

    ForceImportInputSetYamlOperationDTO requestDTO = ForceImportInputSetYamlOperationDTO.builder()
                                                         .identifier(INPUT_SET_IDENTIFIER)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJ_IDENTIFIER)
                                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                         .branch("main")
                                                         .filePath(".harness/myPipeline.yaml")
                                                         .connectorRef("githubConnectorRef")
                                                         .repoName("repo")
                                                         .build();

    doReturn("url")
        .when(pmsInputSetServiceMock)
        .getRepoUrlAndCheckForFileUniqueness(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INPUT_SET_IDENTIFIER, true, null, true);
    doReturn(v0Yaml).when(gitAwareEntityHelper).fetchYAMLFromRemote(any(), anyBoolean());
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .identifier(INPUT_SET_IDENTIFIER)
                                        .name("is1")
                                        .yaml(v0Yaml)
                                        .build();

    doReturn(inputSetEntity).when(inputSetRepository).saveForImportedYAML(any(), any(), anyBoolean());

    ArgumentCaptor<InputSetEntity> operationDTOCaptor = ArgumentCaptor.forClass(InputSetEntity.class);

    pmsInputSetServiceMock.forceImportInputSet(ACCOUNT_ID, requestDTO, null);

    verify(inputSetRepository).saveForImportedYAML(operationDTOCaptor.capture(), any(), anyBoolean());
    assertThat(operationDTOCaptor.getValue().getIdentifier()).isEqualTo(INPUT_SET_IDENTIFIER);
    assertThat(operationDTOCaptor.getValue().getRepoURL()).isEqualTo("url");
    assertThat(operationDTOCaptor.getValue().getOrgIdentifier()).isEqualTo("orgId");
    assertThat(operationDTOCaptor.getValue().getProjectIdentifier()).isEqualTo("projId");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testFetchAllInputSetByFilePathAndRepo() {
    on(pmsInputSetServiceHelper).set("inputSetRepository", inputSetRepository);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .pipelineIdentifier("pipeline")
                                        .yaml("yaml")
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    when(inputSetRepository.findAllFromSecondaryDb(any(), any(), any())).thenReturn(Arrays.asList(inputSetEntity));
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    pmsInputSetServiceHelper.fetchAllInputSetByFilePathAndRepo(ACCOUNT_ID, "file", "repo");
    verify(inputSetRepository, times(1)).findAllFromSecondaryDb(criteriaArgumentCaptor.capture(), any(), any());
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Assertions.assertTrue(
        criteriaArgumentCaptor.getValue().getCriteriaObject().get("repo").toString().equals("^repo$"));
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadata() {
    String accountId = ACCOUNT_ID;
    String orgIdentifier = ORG_IDENTIFIER;
    String projectIdentifier = PROJ_IDENTIFIER;
    List<String> pipelineIdentifiers = Arrays.asList("pipeline1", "pipeline2");
    BatchInputSetsRequestDTO request =
        BatchInputSetsRequestDTO.builder().pipelineIdentifiers(pipelineIdentifiers).build();

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier("pipeline1")
                                   .accountId(accountId)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .description("Test input set 1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    InputSetEntity overlayInputSet = InputSetEntity.builder()
                                         .identifier("overlay1")
                                         .name("Overlay Input Set")
                                         .pipelineIdentifier("pipeline1")
                                         .accountId(accountId)
                                         .orgIdentifier(orgIdentifier)
                                         .projectIdentifier(projectIdentifier)
                                         .description("Test overlay input set")
                                         .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                         .build();

    InputSetEntity inputSet3 = InputSetEntity.builder()
                                   .identifier("inputset3")
                                   .name("Input Set 3")
                                   .pipelineIdentifier("pipeline2")
                                   .accountId(accountId)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .description("Test input set 3")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    try (MockedStatic<PMSInputSetFilterHelper> mockedHelper = mockStatic(PMSInputSetFilterHelper.class)) {
      Page<InputSetEntity> mockPage = new PageImpl<>(Arrays.asList(inputSet1, inputSet3));
      when(PMSInputSetFilterHelper.getCriteriaForFindByPipelineIdentifiers(anyString(), anyString(), anyString(),
               any(ScopeInfo.class), any(InputSetEntityType.class), anyBoolean(), anyList(), anyBoolean(), any()))
          .thenReturn(Criteria.where(InputSetEntityKeys.accountId)
                          .is(accountId)
                          .and(InputSetEntityKeys.orgIdentifier)
                          .is(orgIdentifier)
                          .and(InputSetEntityKeys.projectIdentifier)
                          .is(projectIdentifier)
                          .and(InputSetEntityKeys.pipelineIdentifier)
                          .in(pipelineIdentifiers)
                          .and(InputSetEntityKeys.inputSetEntityType)
                          .is(InputSetEntityType.INPUT_SET)
                          .and(InputSetEntityKeys.deleted)
                          .is(false));
      when(inputSetRepository.findAllFromSecondaryDb(
               any(Criteria.class), eq(List.of(InputSetEntityKeys.yaml)), any(Pageable.class), any(ScopeInfo.class)))
          .thenReturn(mockPage);

      Page<InputSetEntity> resultPage = pmsInputSetServiceMock.getBatchInputSetsMetadata(scopeInfo, request);

      assertThat(resultPage).isNotNull();
      assertThat(resultPage.getContent()).isNotNull();
      assertThat(resultPage.getContent().size()).isEqualTo(2);

      List<String> inputSetIds =
          resultPage.getContent().stream().map(InputSetEntity::getIdentifier).collect(Collectors.toList());
      assertThat(inputSetIds).containsExactlyInAnyOrder("inputset1", "inputset3");

      Map<String, String> pipelineMap = resultPage.getContent().stream().collect(
          Collectors.toMap(InputSetEntity::getIdentifier, InputSetEntity::getPipelineIdentifier));
      assertThat(pipelineMap.get("inputset1")).isEqualTo("pipeline1");
      assertThat(pipelineMap.get("inputset3")).isEqualTo("pipeline2");

      InputSetEntity inputSetResponse1 = resultPage.getContent()
                                             .stream()
                                             .filter(entity -> "inputset1".equals(entity.getIdentifier()))
                                             .findFirst()
                                             .orElse(null);
      assertThat(inputSetResponse1).isNotNull();
      assertThat(inputSetResponse1.getName()).isEqualTo("Input Set 1");
      assertThat(inputSetResponse1.getDescription()).isEqualTo("Test input set 1");
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadataWithMixedTypes() {
    List<String> pipelineIdentifiers = Arrays.asList("pipeline1", "pipeline2");
    BatchInputSetsRequestDTO request =
        BatchInputSetsRequestDTO.builder().pipelineIdentifiers(pipelineIdentifiers).build();

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier("pipeline1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    InputSetEntity overlayInputSet = InputSetEntity.builder()
                                         .identifier("overlay1")
                                         .name("Overlay Input Set")
                                         .pipelineIdentifier("pipeline1")
                                         .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                         .build();

    InputSetEntity inputSet3 = InputSetEntity.builder()
                                   .identifier("inputset3")
                                   .name("Input Set 3")
                                   .pipelineIdentifier("pipeline2")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    try (MockedStatic<PMSInputSetFilterHelper> mockedHelper = mockStatic(PMSInputSetFilterHelper.class)) {
      Page<InputSetEntity> mockPage = new PageImpl<>(Arrays.asList(inputSet1, inputSet3));
      when(PMSInputSetFilterHelper.getCriteriaForFindByPipelineIdentifiers(anyString(), anyString(), anyString(),
               any(ScopeInfo.class), any(InputSetEntityType.class), anyBoolean(), anyList(), anyBoolean(), any()))
          .thenReturn(Criteria.where(InputSetEntityKeys.accountId)
                          .is("")
                          .and(InputSetEntityKeys.orgIdentifier)
                          .is("")
                          .and(InputSetEntityKeys.projectIdentifier)
                          .is("")
                          .and(InputSetEntityKeys.pipelineIdentifier)
                          .in(pipelineIdentifiers)
                          .and(InputSetEntityKeys.inputSetEntityType)
                          .is(InputSetEntityType.INPUT_SET)
                          .and(InputSetEntityKeys.deleted)
                          .is(false));
      when(inputSetRepository.findAllFromSecondaryDb(
               any(Criteria.class), eq(List.of(InputSetEntityKeys.yaml)), any(Pageable.class), any(ScopeInfo.class)))
          .thenReturn(mockPage);

      Page<InputSetEntity> resultPage = pmsInputSetServiceMock.getBatchInputSetsMetadata(scopeInfo, request);

      assertThat(resultPage).isNotNull();
      assertThat(resultPage.getContent()).isNotNull();
      assertThat(resultPage.getContent().size()).isEqualTo(2);

      InputSetEntity inputSetEntity1 = resultPage.getContent().get(0);
      assertThat(inputSetEntity1).isNotNull();
      assertThat(inputSetEntity1.getIdentifier()).isEqualTo("inputset1");
      assertThat(inputSetEntity1.getName()).isEqualTo("Input Set 1");
      assertThat(inputSetEntity1.getPipelineIdentifier()).isEqualTo("pipeline1");
      assertThat(inputSetEntity1.getInputSetEntityType()).isEqualTo(InputSetEntityType.INPUT_SET);

      InputSetEntity inputSetEntity2 = resultPage.getContent().get(1);
      assertThat(inputSetEntity2).isNotNull();
      assertThat(inputSetEntity2.getIdentifier()).isEqualTo("inputset3");
      assertThat(inputSetEntity2.getName()).isEqualTo("Input Set 3");
      assertThat(inputSetEntity2.getPipelineIdentifier()).isEqualTo("pipeline2");
      assertThat(inputSetEntity2.getInputSetEntityType()).isEqualTo(InputSetEntityType.INPUT_SET);
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadataWithErrorHandling() {
    String accountId = ACCOUNT_ID;
    String orgIdentifier = ORG_IDENTIFIER;
    String projectIdentifier = PROJ_IDENTIFIER;

    List<String> successPipelineIdentifiers = Collections.singletonList("successPipeline");
    BatchInputSetsRequestDTO successRequest =
        BatchInputSetsRequestDTO.builder().pipelineIdentifiers(successPipelineIdentifiers).build();
    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier("successPipeline")
                                   .accountId(accountId)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .description("Test input set 1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.INLINE)
                                   .build();

    try (MockedStatic<PMSInputSetFilterHelper> mockedHelper = mockStatic(PMSInputSetFilterHelper.class)) {
      Page<InputSetEntity> successPage = new PageImpl<>(Collections.singletonList(inputSet1));
      when(PMSInputSetFilterHelper.getCriteriaForFindByPipelineIdentifiers(anyString(), anyString(), anyString(),
               any(ScopeInfo.class), any(InputSetEntityType.class), anyBoolean(), anyList(), anyBoolean(), any()))
          .thenReturn(Criteria.where(InputSetEntityKeys.accountId)
                          .is(accountId)
                          .and(InputSetEntityKeys.orgIdentifier)
                          .is(orgIdentifier)
                          .and(InputSetEntityKeys.projectIdentifier)
                          .is(projectIdentifier)
                          .and(InputSetEntityKeys.pipelineIdentifier)
                          .in(successPipelineIdentifiers)
                          .and(InputSetEntityKeys.inputSetEntityType)
                          .is(InputSetEntityType.INPUT_SET)
                          .and(InputSetEntityKeys.deleted)
                          .is(false));
      when(inputSetRepository.findAllFromSecondaryDb(
               any(Criteria.class), eq(List.of(InputSetEntityKeys.yaml)), any(Pageable.class), any(ScopeInfo.class)))
          .thenReturn(successPage);

      Page<InputSetEntity> successResultPage =
          pmsInputSetServiceMock.getBatchInputSetsMetadata(scopeInfo, successRequest);

      assertThat(successResultPage).isNotNull();
      assertThat(successResultPage.getContent()).isNotNull();
      assertThat(successResultPage.getContent().size()).isEqualTo(1);

      InputSetEntity successResult = successResultPage.getContent().get(0);
      assertThat(successResult).isNotNull();
      assertThat(successResult.getIdentifier()).isEqualTo("inputset1");

      List<String> emptyPipelineIdentifiers = Collections.singletonList("emptyPipeline");
      BatchInputSetsRequestDTO emptyRequest =
          BatchInputSetsRequestDTO.builder().pipelineIdentifiers(emptyPipelineIdentifiers).build();

      Page<InputSetEntity> emptyPage = new PageImpl<>(Collections.emptyList());
      when(inputSetRepository.findAllFromSecondaryDb(
               any(Criteria.class), eq(List.of(InputSetEntityKeys.yaml)), any(Pageable.class), any(ScopeInfo.class)))
          .thenReturn(emptyPage);

      Page<InputSetEntity> emptyResultPage = pmsInputSetServiceMock.getBatchInputSetsMetadata(scopeInfo, emptyRequest);

      assertThat(emptyResultPage).isNotNull();
      assertThat(emptyResultPage.getContent()).isNotNull();
      assertThat(emptyResultPage.getContent()).isEmpty();
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadataWithSearchTerm() {
    List<String> pipelineIdentifiers = Arrays.asList("pipeline1", "pipeline2");
    String searchTerm = "test";
    BatchInputSetsRequestDTO request = BatchInputSetsRequestDTO.builder()
                                           .pipelineIdentifiers(pipelineIdentifiers)
                                           .page(0)
                                           .size(10)
                                           .searchTerm(searchTerm)
                                           .build();

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("test-inputset1")
                                   .name("Test Input Set 1")
                                   .pipelineIdentifier("pipeline1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    try (MockedStatic<PMSInputSetFilterHelper> mockedHelper = mockStatic(PMSInputSetFilterHelper.class)) {
      Page<InputSetEntity> mockPage = new PageImpl<>(Collections.singletonList(inputSet1));
      when(PMSInputSetFilterHelper.getCriteriaForFindByPipelineIdentifiers(anyString(), anyString(), anyString(),
               any(ScopeInfo.class), any(InputSetEntityType.class), anyBoolean(), anyList(), anyBoolean(),
               eq(searchTerm)))
          .thenReturn(Criteria.where(InputSetEntityKeys.accountId).is(ACCOUNT_ID));
      when(inputSetRepository.findAllFromSecondaryDb(
               any(Criteria.class), eq(List.of(InputSetEntityKeys.yaml)), any(Pageable.class), any(ScopeInfo.class)))
          .thenReturn(mockPage);

      Page<InputSetEntity> resultPage = pmsInputSetServiceMock.getBatchInputSetsMetadata(scopeInfo, request);

      assertThat(resultPage).isNotNull();
      assertThat(resultPage.getContent()).isNotNull();
      assertThat(resultPage.getContent().size()).isEqualTo(1);
      assertThat(resultPage.getContent().get(0).getIdentifier()).isEqualTo("test-inputset1");
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetAllInputSetsMetadataForProject() {
    int page = 0;
    int size = 10;
    String searchTerm = "test";

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("test-inputset1")
                                   .name("Test Input Set 1")
                                   .pipelineIdentifier("pipeline1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    InputSetEntity inputSet2 = InputSetEntity.builder()
                                   .identifier("test-inputset2")
                                   .name("Test Input Set 2")
                                   .pipelineIdentifier("pipeline2")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    try (MockedStatic<PMSInputSetFilterHelper> mockedHelper = mockStatic(PMSInputSetFilterHelper.class)) {
      Page<InputSetEntity> mockPage = new PageImpl<>(Arrays.asList(inputSet1, inputSet2));
      when(PMSInputSetFilterHelper.getCriteriaForAllInputSetsInProject(anyString(), anyString(), anyString(),
               any(ScopeInfo.class), any(InputSetEntityType.class), anyBoolean(), anyBoolean(), eq(searchTerm)))
          .thenReturn(Criteria.where(InputSetEntityKeys.accountId).is(ACCOUNT_ID));
      when(inputSetRepository.findAllFromSecondaryDb(
               any(Criteria.class), eq(List.of(InputSetEntityKeys.yaml)), any(Pageable.class), any(ScopeInfo.class)))
          .thenReturn(mockPage);

      Page<InputSetEntity> resultPage =
          pmsInputSetServiceMock.getAllInputSetsMetadataForProject(scopeInfo, page, size, searchTerm);

      assertThat(resultPage).isNotNull();
      assertThat(resultPage.getContent()).isNotNull();
      assertThat(resultPage.getContent().size()).isEqualTo(2);
      assertThat(resultPage.getContent().get(0).getIdentifier()).isEqualTo("test-inputset1");
      assertThat(resultPage.getContent().get(1).getIdentifier()).isEqualTo("test-inputset2");
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetAllInputSetsMetadataForProjectWithoutSearchTerm() {
    int page = 0;
    int size = 10;

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier("pipeline1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    try (MockedStatic<PMSInputSetFilterHelper> mockedHelper = mockStatic(PMSInputSetFilterHelper.class)) {
      Page<InputSetEntity> mockPage = new PageImpl<>(Collections.singletonList(inputSet1));
      when(PMSInputSetFilterHelper.getCriteriaForAllInputSetsInProject(anyString(), anyString(), anyString(),
               any(ScopeInfo.class), any(InputSetEntityType.class), anyBoolean(), anyBoolean(), eq(null)))
          .thenReturn(Criteria.where(InputSetEntityKeys.accountId).is(ACCOUNT_ID));
      when(inputSetRepository.findAllFromSecondaryDb(
               any(Criteria.class), eq(List.of(InputSetEntityKeys.yaml)), any(Pageable.class), any(ScopeInfo.class)))
          .thenReturn(mockPage);

      Page<InputSetEntity> resultPage =
          pmsInputSetServiceMock.getAllInputSetsMetadataForProject(scopeInfo, page, size, null);

      assertThat(resultPage).isNotNull();
      assertThat(resultPage.getContent()).isNotNull();
      assertThat(resultPage.getContent().size()).isEqualTo(1);
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBulkInputSets() {
    String accountId = ACCOUNT_ID;
    String orgIdentifier = ORG_IDENTIFIER;
    String projectIdentifier = PROJ_IDENTIFIER;
    String pipelineIdentifier = "testPipeline";

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier(pipelineIdentifier)
                                   .accountId(accountId)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .description("Test input set 1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.INLINE)
                                   .build();

    InputSetEntity inputSet2 = InputSetEntity.builder()
                                   .identifier("inputset2")
                                   .name("Input Set 2")
                                   .pipelineIdentifier(pipelineIdentifier)
                                   .accountId(accountId)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .description("Test input set 2")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.INLINE)
                                   .build();

    doReturn(Optional.of(inputSet1))
        .when(pmsInputSetServiceMock)
        .getWithoutValidations(
            eq(scopeInfo), eq(pipelineIdentifier), eq("inputset1"), eq(false), eq(false), eq(false), eq(false));

    doReturn(Optional.of(inputSet2))
        .when(pmsInputSetServiceMock)
        .getWithoutValidations(
            eq(scopeInfo), eq(pipelineIdentifier), eq("inputset2"), eq(false), eq(false), eq(false), eq(false));

    doReturn(Optional.empty())
        .when(pmsInputSetServiceMock)
        .getWithoutValidations(
            eq(scopeInfo), eq(pipelineIdentifier), eq("nonexistent"), eq(false), eq(false), eq(false), eq(false));

    InputSetSummaryResponseDTOPMS response1 = InputSetSummaryResponseDTOPMS.builder()
                                                  .identifier("inputset1")
                                                  .name("Input Set 1")
                                                  .pipelineIdentifier(pipelineIdentifier)
                                                  .description("Test input set 1")
                                                  .build();

    InputSetSummaryResponseDTOPMS response2 = InputSetSummaryResponseDTOPMS.builder()
                                                  .identifier("inputset2")
                                                  .name("Input Set 2")
                                                  .pipelineIdentifier(pipelineIdentifier)
                                                  .description("Test input set 2")
                                                  .build();

    try (MockedStatic<PMSInputSetElementMapper> mockedMapper = mockStatic(PMSInputSetElementMapper.class)) {
      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetSummaryResponseDTOPMS(inputSet1))
          .thenReturn(response1);
      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetSummaryResponseDTOPMS(inputSet2))
          .thenReturn(response2);

      List<String> validIdentifiers = Arrays.asList("inputset1", "inputset2");
      BulkInputSetsRequestDTO validRequest =
          BulkInputSetsRequestDTO.builder().inputSetIdentifiers(validIdentifiers).build();

      BulkInputSetsResponseDTO validServiceResponse =
          BulkInputSetsResponseDTO.builder().inputSets(Arrays.asList(response1, response2)).build();
      doReturn(validServiceResponse)
          .when(pmsInputSetServiceMock)
          .getBulkInputSets(eq(scopeInfo), eq(pipelineIdentifier), eq(validRequest));

      BulkInputSetsResponseDTO validResults =
          pmsInputSetServiceMock.getBulkInputSets(scopeInfo, pipelineIdentifier, validRequest);

      assertThat(validResults).isNotNull();
      assertThat(validResults.getInputSets()).isNotNull();
      assertThat(validResults.getInputSets()).hasSize(2);
      assertThat(validResults.getInputSets().get(0).getIdentifier()).isEqualTo("inputset1");
      assertThat(validResults.getInputSets().get(1).getIdentifier()).isEqualTo("inputset2");

      List<String> mixedIdentifiers = Arrays.asList("inputset1", "nonexistent", "inputset2");
      BulkInputSetsRequestDTO mixedRequest =
          BulkInputSetsRequestDTO.builder().inputSetIdentifiers(mixedIdentifiers).build();

      BulkInputSetsResponseDTO mixedServiceResponse =
          BulkInputSetsResponseDTO.builder().inputSets(Arrays.asList(response1, response2)).build();
      doReturn(mixedServiceResponse)
          .when(pmsInputSetServiceMock)
          .getBulkInputSets(eq(scopeInfo), eq(pipelineIdentifier), eq(mixedRequest));

      BulkInputSetsResponseDTO mixedResults =
          pmsInputSetServiceMock.getBulkInputSets(scopeInfo, pipelineIdentifier, mixedRequest);

      assertThat(mixedResults).isNotNull();
      assertThat(mixedResults.getInputSets()).isNotNull();
      assertThat(mixedResults.getInputSets()).hasSize(2); // Only the two valid ones should be returned
      assertThat(mixedResults.getInputSets().get(0).getIdentifier()).isEqualTo("inputset1");
      assertThat(mixedResults.getInputSets().get(1).getIdentifier()).isEqualTo("inputset2");
    }

    BulkInputSetsRequestDTO emptyRequest =
        BulkInputSetsRequestDTO.builder().inputSetIdentifiers(Collections.emptyList()).build();
    assertThatThrownBy(() -> pmsInputSetServiceMock.getBulkInputSets(scopeInfo, pipelineIdentifier, emptyRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Input set identifiers request cannot be null or empty");

    BulkInputSetsRequestDTO nullRequest = BulkInputSetsRequestDTO.builder().inputSetIdentifiers(null).build();
    assertThatThrownBy(() -> pmsInputSetServiceMock.getBulkInputSets(scopeInfo, pipelineIdentifier, nullRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Input set identifiers request cannot be null or empty");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBulkInputSetsWithNullRequest() {
    String pipelineIdentifier = "testPipeline";

    assertThatThrownBy(() -> pmsInputSetServiceMock.getBulkInputSets(scopeInfo, pipelineIdentifier, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input set identifiers request cannot be null or empty.");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBulkInputSetsWithAllEmptyIdentifiers() {
    String pipelineIdentifier = "testPipeline";

    List<String> emptyIdentifiers = Arrays.asList("", "", null);
    BulkInputSetsRequestDTO request = BulkInputSetsRequestDTO.builder().inputSetIdentifiers(emptyIdentifiers).build();

    assertThatThrownBy(() -> pmsInputSetServiceMock.getBulkInputSets(scopeInfo, pipelineIdentifier, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input set identifiers list cannot be empty.");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBulkInputSetsWithMixedEmptyAndValidIdentifiers() {
    String pipelineIdentifier = "testPipeline";

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier(pipelineIdentifier)
                                   .accountId(ACCOUNT_ID)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .description("Test input set 1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.INLINE)
                                   .build();

    List<String> mixedIdentifiers = Arrays.asList("", "inputset1", null);
    BulkInputSetsRequestDTO request = BulkInputSetsRequestDTO.builder().inputSetIdentifiers(mixedIdentifiers).build();

    try (MockedStatic<PMSInputSetFilterHelper> mockedHelper = mockStatic(PMSInputSetFilterHelper.class);
         MockedStatic<PMSInputSetElementMapper> mockedMapper = mockStatic(PMSInputSetElementMapper.class)) {
      when(PMSInputSetFilterHelper.getCriteriaForFindByInputSetIdentifiers(anyString(), anyString(), anyString(),
               any(ScopeInfo.class), anyBoolean(), anyString(), anyList(), anyBoolean()))
          .thenReturn(Criteria.where(InputSetEntityKeys.accountId).is(ACCOUNT_ID));
      when(inputSetRepository.findAllFromSecondaryDb(any(Criteria.class)))
          .thenReturn(Collections.singletonList(inputSet1));

      InputSetSummaryResponseDTOPMS response1 = InputSetSummaryResponseDTOPMS.builder()
                                                    .identifier("inputset1")
                                                    .name("Input Set 1")
                                                    .pipelineIdentifier(pipelineIdentifier)
                                                    .description("Test input set 1")
                                                    .build();
      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetSummaryResponseDTOPMS(inputSet1))
          .thenReturn(response1);

      BulkInputSetsResponseDTO result = pmsInputSetServiceMock.getBulkInputSets(scopeInfo, pipelineIdentifier, request);

      assertThat(result).isNotNull();
      assertThat(result.getInputSets()).isNotNull();
      assertThat(result.getInputSets()).hasSize(1);
      assertThat(result.getInputSets().get(0).getIdentifier()).isEqualTo("inputset1");
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBulkInputSetsWithValidIdentifiers() {
    String pipelineIdentifier = "testPipeline";

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier(pipelineIdentifier)
                                   .accountId(ACCOUNT_ID)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.INLINE)
                                   .build();

    InputSetEntity inputSet2 = InputSetEntity.builder()
                                   .identifier("inputset2")
                                   .name("Input Set 2")
                                   .pipelineIdentifier(pipelineIdentifier)
                                   .accountId(ACCOUNT_ID)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.INLINE)
                                   .build();

    List<String> validIdentifiers = Arrays.asList("inputset1", "inputset2");
    BulkInputSetsRequestDTO request = BulkInputSetsRequestDTO.builder().inputSetIdentifiers(validIdentifiers).build();

    try (MockedStatic<PMSInputSetFilterHelper> mockedHelper = mockStatic(PMSInputSetFilterHelper.class);
         MockedStatic<PMSInputSetElementMapper> mockedMapper = mockStatic(PMSInputSetElementMapper.class)) {
      when(PMSInputSetFilterHelper.getCriteriaForFindByInputSetIdentifiers(anyString(), anyString(), anyString(),
               any(ScopeInfo.class), anyBoolean(), anyString(), anyList(), anyBoolean()))
          .thenReturn(Criteria.where(InputSetEntityKeys.accountId).is(ACCOUNT_ID));
      when(inputSetRepository.findAllFromSecondaryDb(any(Criteria.class)))
          .thenReturn(Arrays.asList(inputSet1, inputSet2));

      InputSetSummaryResponseDTOPMS response1 = InputSetSummaryResponseDTOPMS.builder()
                                                    .identifier("inputset1")
                                                    .name("Input Set 1")
                                                    .pipelineIdentifier(pipelineIdentifier)
                                                    .build();
      InputSetSummaryResponseDTOPMS response2 = InputSetSummaryResponseDTOPMS.builder()
                                                    .identifier("inputset2")
                                                    .name("Input Set 2")
                                                    .pipelineIdentifier(pipelineIdentifier)
                                                    .build();
      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetSummaryResponseDTOPMS(inputSet1))
          .thenReturn(response1);
      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetSummaryResponseDTOPMS(inputSet2))
          .thenReturn(response2);

      BulkInputSetsResponseDTO result = pmsInputSetServiceMock.getBulkInputSets(scopeInfo, pipelineIdentifier, request);

      assertThat(result).isNotNull();
      assertThat(result.getInputSets()).isNotNull();
      assertThat(result.getInputSets()).hasSize(2);
      assertThat(result.getInputSets().get(0).getIdentifier()).isEqualTo("inputset1");
      assertThat(result.getInputSets().get(1).getIdentifier()).isEqualTo("inputset2");
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBulkInputSetsWithNoMatchingInputSets() {
    String pipelineIdentifier = "testPipeline";

    List<String> validIdentifiers = Arrays.asList("nonexistent1", "nonexistent2");
    BulkInputSetsRequestDTO request = BulkInputSetsRequestDTO.builder().inputSetIdentifiers(validIdentifiers).build();

    try (MockedStatic<PMSInputSetFilterHelper> mockedHelper = mockStatic(PMSInputSetFilterHelper.class)) {
      when(PMSInputSetFilterHelper.getCriteriaForFindByInputSetIdentifiers(anyString(), anyString(), anyString(),
               any(ScopeInfo.class), anyBoolean(), anyString(), anyList(), anyBoolean()))
          .thenReturn(Criteria.where(InputSetEntityKeys.accountId).is(ACCOUNT_ID));
      when(inputSetRepository.findAllFromSecondaryDb(any(Criteria.class))).thenReturn(Collections.emptyList());

      BulkInputSetsResponseDTO result = pmsInputSetServiceMock.getBulkInputSets(scopeInfo, pipelineIdentifier, request);

      assertThat(result).isNotNull();
      assertThat(result.getInputSets()).isNotNull();
      assertThat(result.getInputSets()).isEmpty();
    }
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteRepoListForAGivenScopePassesAllArgsToRepository() {
    java.util.Map<String, io.harness.beans.Scope> filePaths = new java.util.HashMap<>();
    filePaths.put(".harness/inputSet.yaml", io.harness.beans.Scope.of(ACCOUNT_ID, "orgA", "projA", "uid-projA"));
    io.harness.pms.inputset.InputSetRemoteRepoInfo info =
        io.harness.pms.inputset.InputSetRemoteRepoInfo.builder()
            .repoName("harness-core")
            .repoURL("https://github.com/wings-software/harness-core")
            .count(3L)
            .filePathsByOwningScope(filePaths)
            .connectorRefs(new java.util.HashSet<>(java.util.Arrays.asList(ACCOUNT_ID + "/orgA/projA/conn1")))
            .build();
    when(inputSetRepository.findRemoteRepoInfosForGivenScope(ACCOUNT_ID, null, null, null, null, 0, 20))
        .thenReturn(io.harness.repositories.inputset.InputSetRemoteRepoPage.builder()
                        .repositories(java.util.Collections.singletonList(info))
                        .totalRepos(1L)
                        .build());

    io.harness.pms.inputset.InputSetRemoteRepoListResponse result =
        pmsInputSetServiceMock.getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, null, null, 0, 20);

    verify(inputSetRepository, times(1)).findRemoteRepoInfosForGivenScope(ACCOUNT_ID, null, null, null, null, 0, 20);
    assertThat(result.getRepositories()).hasSize(1);
    assertThat(result.getRepositories().get(0)).isEqualTo(info);
    assertThat(result.getTotalRepos()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteRepoListForAGivenScopePassesRepoNameFilter() {
    String repoNameFilter = "harness-core";
    when(inputSetRepository.findRemoteRepoInfosForGivenScope(ACCOUNT_ID, null, null, repoNameFilter, null, 0, 20))
        .thenReturn(io.harness.repositories.inputset.InputSetRemoteRepoPage.builder()
                        .repositories(java.util.Collections.emptyList())
                        .totalRepos(0L)
                        .build());

    io.harness.pms.inputset.InputSetRemoteRepoListResponse result =
        pmsInputSetServiceMock.getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, repoNameFilter, null, 0, 20);

    verify(inputSetRepository, times(1))
        .findRemoteRepoInfosForGivenScope(ACCOUNT_ID, null, null, repoNameFilter, null, 0, 20);
    assertThat(result.getRepositories()).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteRepoListForAGivenScopeReturnsEmptyListWhenRepositoryReturnsEmpty() {
    when(inputSetRepository.findRemoteRepoInfosForGivenScope(ACCOUNT_ID, null, null, null, null, 0, 20))
        .thenReturn(io.harness.repositories.inputset.InputSetRemoteRepoPage.builder()
                        .repositories(java.util.Collections.emptyList())
                        .totalRepos(0L)
                        .build());

    io.harness.pms.inputset.InputSetRemoteRepoListResponse result =
        pmsInputSetServiceMock.getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, null, null, 0, 20);

    assertThat(result).isNotNull();
    assertThat(result.getRepositories()).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteRepoListForAGivenScopeThrowsWhenAccountIdentifierIsEmpty() {
    assertThatThrownBy(
        () -> pmsInputSetServiceMock.getRemoteRepoListForAGivenScope(null, null, null, null, null, 0, 20))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("accountIdentifier is required");
    assertThatThrownBy(
        () -> pmsInputSetServiceMock.getRemoteRepoListForAGivenScope("", null, null, "anyRepo", null, 0, 20))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("accountIdentifier is required");
    verify(inputSetRepository, org.mockito.Mockito.never())
        .findRemoteRepoInfosForGivenScope(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshGitFileCacheForInputSet_throwsWhenFeatureFlagDisabled() {
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIPE_GITX_FORCE_REFRESH);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.refreshGitFileCache(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, "main", scopeInfo))
        .isInstanceOf(UnavailableFeatureException.class)
        .hasMessageContaining("PIPE_GITX_FORCE_REFRESH");
    verify(gitAwareEntityHelper, never()).clearCache(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshGitFileCacheForInputSet_throwsWhenBranchMissing() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIPE_GITX_FORCE_REFRESH);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.refreshGitFileCache(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, null, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("A valid git branch is required");
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.refreshGitFileCache(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, "", scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("A valid git branch is required");
    verify(gitAwareEntityHelper, never()).clearCache(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshGitFileCacheForInputSet_throwsWhenInputSetInline() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIPE_GITX_FORCE_REFRESH);
    doReturn(Optional.of(inputSetEntity.withStoreType(StoreType.INLINE)))
        .when(pmsInputSetServiceMock)
        .getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            INPUT_SET_IDENTIFIER, false, false, true, scopeInfo, true);

    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.refreshGitFileCache(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, "main", scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("remote Git-backed input sets");
    verify(gitAwareEntityHelper, never()).clearCache(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshGitFileCacheForInputSet_remoteEntity_clearsCache() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIPE_GITX_FORCE_REFRESH);
    InputSetEntity remoteSummary = InputSetEntity.builder()
                                       .identifier(INPUT_SET_IDENTIFIER)
                                       .accountId(ACCOUNT_ID)
                                       .orgIdentifier(ORG_IDENTIFIER)
                                       .projectIdentifier(PROJ_IDENTIFIER)
                                       .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                       .storeType(StoreType.REMOTE)
                                       .repo(REPO_NAME)
                                       .connectorRef("connector")
                                       .filePath(".harness/inputset.yaml")
                                       .build();
    doReturn(Optional.of(remoteSummary))
        .when(pmsInputSetServiceMock)
        .getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            INPUT_SET_IDENTIFIER, false, false, true, scopeInfo, true);
    doReturn(ScmClearCacheResponse.builder().status(true).failedFilePaths(Collections.emptyList()).build())
        .when(gitAwareEntityHelper)
        .clearCache(any(), any(), eq("main"), any());

    pmsInputSetServiceMock.refreshGitFileCache(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, "main", scopeInfo);

    verify(gitAwareEntityHelper, times(1))
        .clearCache(eq(remoteSummary), eq(Scope.of(scopeInfo)), eq("main"), eq(EntityType.INPUT_SETS));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshGitFileCacheForInputSet_throwsWhenCacheClearFails() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIPE_GITX_FORCE_REFRESH);
    InputSetEntity remoteSummary = InputSetEntity.builder()
                                       .identifier(INPUT_SET_IDENTIFIER)
                                       .accountId(ACCOUNT_ID)
                                       .orgIdentifier(ORG_IDENTIFIER)
                                       .projectIdentifier(PROJ_IDENTIFIER)
                                       .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                       .storeType(StoreType.REMOTE)
                                       .repo(REPO_NAME)
                                       .connectorRef("connector")
                                       .filePath(".harness/inputset.yaml")
                                       .build();
    doReturn(Optional.of(remoteSummary))
        .when(pmsInputSetServiceMock)
        .getMetadataWithoutValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            INPUT_SET_IDENTIFIER, false, false, true, scopeInfo, true);
    doReturn(ScmClearCacheResponse.builder()
                 .status(false)
                 .failedFilePaths(Collections.singletonList(".harness/inputset.yaml"))
                 .errorMessage("SCM connection failed")
                 .build())
        .when(gitAwareEntityHelper)
        .clearCache(any(), any(), any(), any());

    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.refreshGitFileCache(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, "main", scopeInfo))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessageContaining("Failed to refresh git file cache")
        .hasMessageContaining(".harness/inputset.yaml")
        .hasMessageContaining("SCM connection failed");
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.resources;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SANDESH_SALUNKHE;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnavailableFeatureException;
import io.harness.exception.WingsException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitaware.helper.GitxRefreshMetrics;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.inputset.ForceImportInputSetRequestDTO;
import io.harness.pms.inputset.ForceImportInputSetResponse;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.inputset.MergeInputSetForRerunRequestDTO;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.inputset.RemoteInputSetsResponseDTO;
import io.harness.pms.ngpipeline.inputset.api.utils.InputSetsApiUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.BatchInputSetsAPIRequest;
import io.harness.pms.ngpipeline.inputset.beans.resource.BatchInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsAPIRequest;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsAPIResponse;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.ForceImportInputSetYamlOperationDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetMoveConfigRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetMoveConfigResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.validate.InputSetValidationHelper;
import io.harness.pms.ngpipeline.inputset.helpers.validate.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.pms.pipeline.MoveConfigOperationType;
import io.harness.pms.pipeline.PMSInputSetListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.rbac.InputSetRbacPermissions;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

@OwnedBy(PIPELINE)
@PrepareForTest({InputSetValidationHelper.class})
public class InputSetResourcePMSTest extends PipelineServiceTestBase {
  InputSetResourcePMSImpl inputSetResourcePMSImpl;
  @Mock PMSInputSetService pmsInputSetService;
  @Mock PMSPipelineService pipelineService;
  @Mock ValidateAndMergeHelper validateAndMergeHelper;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock InputSetsApiUtils inputSetsApiUtils;
  @Mock PMSExecutionService executionService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock AccessControlClient accessControlClient;
  @Mock PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  @Mock GitxRefreshMetrics gitxRefreshMetrics;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String PIPELINE_IDENTIFIER = "pipeId";
  private static final String INPUT_SET_ID = "inputSetId";
  private static final String INVALID_INPUT_SET_ID = "invalidInputSetId";
  private static final String OVERLAY_INPUT_SET_ID = "overlayInputSetId";
  private static final String INVALID_OVERLAY_INPUT_SET_ID = "invalidOverlayInputSetId";
  private static final String PARENT_UNIQUE_ID = "projUniqueId";
  private static final ScopeInfo SCOPE_INFO = ScopeInfo.builder()
                                                  .accountIdentifier(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .uniqueId(PARENT_UNIQUE_ID)
                                                  .build();
  private String inputSetYaml;
  private String overlayInputSetYaml;
  private String pipelineYaml;
  private String inputSetYamlV1;

  InputSetEntity inputSetEntity;
  InputSetEntity overlayInputSetEntity;
  PipelineEntity pipelineEntity;

  InputSetEntity inputSetEntityV1;

  List<String> stages =
      Arrays.asList("using", "a", "list", "to", "ensure", "that", "this", "param", "is", "not", "ignored");

  private String readFile(String filename) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read file " + filename, e);
    }
  }

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    when(gitxRefreshMetrics.executeWithMetrics(any(), any()))
        .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get());
    inputSetResourcePMSImpl = new InputSetResourcePMSImpl(pmsInputSetService, pipelineService, gitSyncSdkService,
        validateAndMergeHelper, inputSetsApiUtils, executionService, pmsFeatureFlagService, accessControlClient,
        pmsPipelineServiceHelper, pipelineSplitPermissionsHelper, gitxRefreshMetrics);

    String inputSetFilename = "inputSet1.yml";
    inputSetYaml = readFile(inputSetFilename);
    String overlayInputSetFilename = "overlay1.yml";
    overlayInputSetYaml = readFile(overlayInputSetFilename);
    String pipelineYamlFileName = "pipeline.yml";
    pipelineYaml = readFile(pipelineYamlFileName);

    inputSetEntity = InputSetEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                         .identifier(INPUT_SET_ID)
                         .name(INPUT_SET_ID)
                         .yaml(inputSetYaml)
                         .inputSetEntityType(InputSetEntityType.INPUT_SET)
                         .version(1L)
                         .build();

    String inputSetV1Filename = "inputSetV1.yaml";
    inputSetYamlV1 = readFile(inputSetV1Filename);
    inputSetEntityV1 = InputSetEntity.builder()
                           .accountId(ACCOUNT_ID)
                           .orgIdentifier(ORG_IDENTIFIER)
                           .projectIdentifier(PROJ_IDENTIFIER)
                           .pipelineIdentifier(PIPELINE_IDENTIFIER)
                           .identifier(INPUT_SET_ID)
                           .name(INPUT_SET_ID)
                           .yaml(inputSetYamlV1)
                           .inputSetEntityType(InputSetEntityType.INPUT_SET)
                           .harnessVersion(HarnessYamlVersion.V1)
                           .version(1L)
                           .build();

    overlayInputSetEntity = InputSetEntity.builder()
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                .identifier(OVERLAY_INPUT_SET_ID)
                                .name(OVERLAY_INPUT_SET_ID)
                                .yaml(overlayInputSetYaml)
                                .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                .version(1L)
                                .build();
    pipelineEntity = PipelineEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .identifier(PIPELINE_IDENTIFIER)
                         .yaml(pipelineYaml)
                         .version(1L)
                         .build();
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetInputSet() {
    doReturn(Optional.of(inputSetEntity))
        .when(pmsInputSetService)
        .get(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false, false, false, true);

    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getInputSet(INPUT_SET_ID, ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false", getScopeInfo());

    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getInputSetYaml()).isEqualTo(inputSetYaml);
    getCallAssertions(responseDTO.getData().getName(), INPUT_SET_ID, responseDTO.getData().getIdentifier(),
        responseDTO.getData().getPipelineIdentifier(), responseDTO.getData().getProjectIdentifier(),
        responseDTO.getData().getOrgIdentifier(), responseDTO.getData().getAccountId());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetInputSetWithCaching() {
    doReturn(Optional.of(inputSetEntity))
        .when(pmsInputSetService)
        .get(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false, false, true, true);

    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getInputSet(INPUT_SET_ID, ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "true", getScopeInfo());

    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getInputSetYaml()).isEqualTo(inputSetYaml);
    getCallAssertions(responseDTO.getData().getName(), INPUT_SET_ID, responseDTO.getData().getIdentifier(),
        responseDTO.getData().getPipelineIdentifier(), responseDTO.getData().getProjectIdentifier(),
        responseDTO.getData().getOrgIdentifier(), responseDTO.getData().getAccountId());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInlineAndRemoteInputSet() {
    InputSetEntity inlineInputSetEntity = inputSetEntity.withStoreType(StoreType.INLINE);
    doReturn(Optional.of(inlineInputSetEntity))
        .when(pmsInputSetService)
        .get(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false, false, false, true);

    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getInputSet(INPUT_SET_ID, ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false", getScopeInfo());

    InputSetResponseDTOPMS data = responseDTO.getData();
    assertThat(data.getVersion()).isEqualTo(1L);
    assertThat(data.getInputSetYaml()).isEqualTo(inputSetYaml);
    getCallAssertions(data.getName(), INPUT_SET_ID, data.getIdentifier(), data.getPipelineIdentifier(),
        data.getProjectIdentifier(), data.getOrgIdentifier(), data.getAccountId());
    assertThat(data.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(data.getConnectorRef()).isNull();
    assertThat(data.getEntityValidityDetails().isValid()).isTrue();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").build());
    InputSetEntity remoteInputSetEntity = inputSetEntity.withStoreType(StoreType.REMOTE);
    remoteInputSetEntity.setRepo("repoName");
    remoteInputSetEntity.setConnectorRef("conn");
    doReturn(Optional.of(remoteInputSetEntity))
        .when(pmsInputSetService)
        .get(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false, false, false, true);

    responseDTO = inputSetResourcePMSImpl.getInputSet(INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PIPELINE_IDENTIFIER, null, null, false, null, "false", getScopeInfo());

    data = responseDTO.getData();
    assertThat(data.getVersion()).isEqualTo(1L);
    assertThat(data.getInputSetYaml()).isEqualTo(inputSetYaml);
    getCallAssertions(data.getName(), INPUT_SET_ID, data.getIdentifier(), data.getPipelineIdentifier(),
        data.getProjectIdentifier(), data.getOrgIdentifier(), data.getAccountId());
    assertThat(data.getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(data.getConnectorRef()).isEqualTo("conn");
    EntityGitDetails gitDetails = data.getGitDetails();
    assertThat(gitDetails.getRepoName()).isEqualTo("repoName");
    assertThat(gitDetails.getBranch()).isEqualTo("brName");
    assertThat(data.getEntityValidityDetails().isValid()).isTrue();
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetInlineHCInputSet() {
    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").build());
    InputSetEntity inlineHCInputSetEntity = inputSetEntity.withStoreType(StoreType.INLINE_HC);
    inlineHCInputSetEntity.setRepo("repoName");
    inlineHCInputSetEntity.setConnectorRef(GitSyncConstants.EMPTY);
    doReturn(Optional.of(inlineHCInputSetEntity))
        .when(pmsInputSetService)
        .get(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false, false, false, true);

    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getInputSet(INPUT_SET_ID, ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false", getScopeInfo());

    InputSetResponseDTOPMS data = responseDTO.getData();
    assertThat(data.getVersion()).isEqualTo(1L);
    assertThat(data.getInputSetYaml()).isEqualTo(inputSetYaml);
    getCallAssertions(data.getName(), INPUT_SET_ID, data.getIdentifier(), data.getPipelineIdentifier(),
        data.getProjectIdentifier(), data.getOrgIdentifier(), data.getAccountId());
    assertThat(data.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(data.getConnectorRef()).isEqualTo(GitSyncConstants.EMPTY);
    EntityGitDetails gitDetails = data.getGitDetails();
    assertThat(gitDetails.getRepoName()).isEqualTo("repoName");
    assertThat(gitDetails.getBranch()).isEqualTo("brName");
    assertThat(data.getEntityValidityDetails().isValid()).isTrue();
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetInputSetWithInvalidInputSetId() {
    doReturn(Optional.empty())
        .when(pmsInputSetService)
        .get(SCOPE_INFO, PIPELINE_IDENTIFIER, INVALID_INPUT_SET_ID, false, null, null, false, false, false, true);

    assertThatThrownBy(()
                           -> inputSetResourcePMSImpl.getInputSet(INVALID_INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false", SCOPE_INFO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("InputSet with the given ID: %s does not exist or has been deleted", INVALID_INPUT_SET_ID));
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetOverlayInputSet() {
    doReturn(Optional.of(overlayInputSetEntity))
        .when(pmsInputSetService)
        .get(SCOPE_INFO, PIPELINE_IDENTIFIER, OVERLAY_INPUT_SET_ID, false, null, null, false, false, false, true);

    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO =
        inputSetResourcePMSImpl.getOverlayInputSet(OVERLAY_INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, false, null, "false", SCOPE_INFO);

    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getOverlayInputSetYaml()).isEqualTo(overlayInputSetYaml);
    getCallAssertions(responseDTO.getData().getName(), OVERLAY_INPUT_SET_ID, responseDTO.getData().getIdentifier(),
        responseDTO.getData().getPipelineIdentifier(), responseDTO.getData().getProjectIdentifier(),
        responseDTO.getData().getOrgIdentifier(), responseDTO.getData().getAccountId());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInlineAndRemoteOverlayInputSet() {
    InputSetEntity inlineInputSetEntity = overlayInputSetEntity.withStoreType(StoreType.INLINE);
    doReturn(Optional.of(inlineInputSetEntity))
        .when(pmsInputSetService)
        .get(SCOPE_INFO, PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false, false, false, true);

    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getOverlayInputSet(INPUT_SET_ID,
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false", SCOPE_INFO);

    OverlayInputSetResponseDTOPMS data = responseDTO.getData();
    assertThat(data.getVersion()).isEqualTo(1L);
    assertThat(data.getOverlayInputSetYaml()).isEqualTo(overlayInputSetYaml);
    getCallAssertions(data.getName(), OVERLAY_INPUT_SET_ID, data.getIdentifier(), data.getPipelineIdentifier(),
        data.getProjectIdentifier(), data.getOrgIdentifier(), data.getAccountId());
    assertThat(data.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(data.getConnectorRef()).isNull();
    assertThat(data.getEntityValidityDetails().isValid()).isTrue();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").build());
    InputSetEntity remoteInputSetEntity = overlayInputSetEntity.withStoreType(StoreType.REMOTE);
    remoteInputSetEntity.setRepo("repoName");
    remoteInputSetEntity.setConnectorRef("conn");
    doReturn(Optional.of(remoteInputSetEntity))
        .when(pmsInputSetService)
        .get(SCOPE_INFO, PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false, false, false, true);

    responseDTO = inputSetResourcePMSImpl.getOverlayInputSet(INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PIPELINE_IDENTIFIER, null, null, false, null, "false", SCOPE_INFO);

    data = responseDTO.getData();
    assertThat(data.getVersion()).isEqualTo(1L);
    assertThat(data.getOverlayInputSetYaml()).isEqualTo(overlayInputSetYaml);
    getCallAssertions(data.getName(), OVERLAY_INPUT_SET_ID, data.getIdentifier(), data.getPipelineIdentifier(),
        data.getProjectIdentifier(), data.getOrgIdentifier(), data.getAccountId());
    assertThat(data.getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(data.getConnectorRef()).isEqualTo("conn");
    EntityGitDetails gitDetails = data.getGitDetails();
    assertThat(gitDetails.getRepoName()).isEqualTo("repoName");
    assertThat(gitDetails.getBranch()).isEqualTo("brName");
    assertThat(data.getEntityValidityDetails().isValid()).isTrue();
  }

  private void getCallAssertions(String name, String inputSetId, String identifier, String pipelineIdentifier,
      String projectIdentifier, String orgIdentifier, String accountId) {
    assertThat(name).isEqualTo(inputSetId);
    assertThat(identifier).isEqualTo(inputSetId);
    assertThat(pipelineIdentifier).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(projectIdentifier).isEqualTo(PROJ_IDENTIFIER);
    assertThat(orgIdentifier).isEqualTo(ORG_IDENTIFIER);
    assertThat(accountId).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetOverlayInputSetWithInvalidInputSetId() {
    doReturn(Optional.empty())
        .when(pmsInputSetService)
        .get(SCOPE_INFO, PIPELINE_IDENTIFIER, INVALID_OVERLAY_INPUT_SET_ID, false, null, null, false, false, false,
            true);

    assertThatThrownBy(
        ()
            -> inputSetResourcePMSImpl.getOverlayInputSet(INVALID_OVERLAY_INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER,
                PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false", SCOPE_INFO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "InputSet with the given ID: %s does not exist or has been deleted", INVALID_OVERLAY_INPUT_SET_ID));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreateInputSet() {
    doReturn(ScopeInfo.builder()
                 .accountIdentifier(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .uniqueId("unique-id")
                 .build())
        .when(pmsPipelineServiceHelper)
        .getScopeInfo(any(), any(), any(), any());
    doReturn(inputSetEntity).when(pmsInputSetService).create(any(), anyBoolean(), any());
    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.createInputSet(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null, inputSetYaml, null);
    assertEquals(responseDTO.getData().getInputSetYaml(), inputSetYaml);
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testCreateInlineHCInputSet() {
    InputSetEntity entity = inputSetEntity.withStoreType(StoreType.INLINE_HC);
    doReturn(ScopeInfo.builder()
                 .accountIdentifier(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .uniqueId("unique-id")
                 .build())
        .when(pmsPipelineServiceHelper)
        .getScopeInfo(any(), any(), any(), any());
    doReturn(entity.withVersion(1L)).when(pmsInputSetService).create(any(), anyBoolean(), any());
    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.createInputSet(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null, inputSetYaml, null);
    assertEquals(responseDTO.getData().getInputSetYaml(), inputSetYaml);
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreateOverlayInputSet() {
    doReturn(inputSetEntity).when(pmsInputSetService).create(any(), anyBoolean(), any());
    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.createOverlayInputSet(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, overlayInputSetYaml, getScopeInfo());
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testCreateInlineHCOverlayInputSet() {
    InputSetEntity entity = inputSetEntity.withStoreType(StoreType.INLINE_HC);
    doReturn(entity.withVersion(1L)).when(pmsInputSetService).create(any(), anyBoolean(), any());
    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.createOverlayInputSet(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, overlayInputSetYaml, getScopeInfo());
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateInputSet() {
    doReturn(inputSetEntity).when(pmsInputSetService).update(any(), any(), anyBoolean());
    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.updateInputSet(null, "input1", ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null, inputSetYaml, getScopeInfo());
    assertEquals(responseDTO.getData().getInputSetYaml(), inputSetYaml);
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testUpdateInputSetThrowsWhenPathIdentifierDoesNotMatchYamlIdentifier() {
    assertThatThrownBy(
        ()
            -> inputSetResourcePMSImpl.updateInputSet(null, INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                PIPELINE_IDENTIFIER, null, null, null, null, inputSetYaml, getScopeInfo()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input Set Identifier : " + INPUT_SET_ID
            + " in input set request doesn't match with identifier : input1 given in yaml");
    verify(pmsInputSetService, never()).update(any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateOverlayInputSet() {
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(any(), any(), any(), any());
    doReturn(inputSetEntity).when(pmsInputSetService).update(any(), any(), anyBoolean(), any());
    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.updateOverlayInputSet(null,
        INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, overlayInputSetYaml);
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateOverlayInputSetWithVersionV1PersistsHarnessVersionV1() {
    String overlayYamlV1 = "version: 1\n"
        + "name: overlay1\n"
        + "spec:\n"
        + "  input_sets:\n"
        + "    - inputSet2\n"
        + "    - inputSet22\n";
    ArgumentCaptor<InputSetEntity> entityCaptor = ArgumentCaptor.forClass(InputSetEntity.class);
    InputSetEntity createdEntity = InputSetEntity.builder()
                                       .accountId(ACCOUNT_ID)
                                       .orgIdentifier(ORG_IDENTIFIER)
                                       .projectIdentifier(PROJ_IDENTIFIER)
                                       .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                       .identifier("overlay1")
                                       .name("overlay1")
                                       .yaml(overlayYamlV1)
                                       .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                       .inputSetReferences(Arrays.asList("inputSet2", "inputSet22"))
                                       .harnessVersion(HarnessYamlVersion.V1)
                                       .version(1L)
                                       .build();
    doReturn(createdEntity).when(pmsInputSetService).create(entityCaptor.capture(), anyBoolean(), any());

    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO =
        inputSetResourcePMSImpl.createOverlayInputSet(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            null, HarnessYamlVersion.V1, overlayYamlV1, getScopeInfo());

    InputSetEntity persistedEntity = entityCaptor.getValue();
    assertThat(persistedEntity.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
    assertThat(persistedEntity.getInputSetEntityType()).isEqualTo(InputSetEntityType.OVERLAY_INPUT_SET);
    assertThat(persistedEntity.getIdentifier()).isEqualTo("overlay1");
    assertThat(persistedEntity.getInputSetReferences()).containsExactly("inputSet2", "inputSet22");
    assertEquals(responseDTO.getData().getOverlayInputSetYaml(), overlayYamlV1);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateOverlayInputSetWithoutVersionRemainsV0() {
    ArgumentCaptor<InputSetEntity> entityCaptor = ArgumentCaptor.forClass(InputSetEntity.class);
    doReturn(overlayInputSetEntity).when(pmsInputSetService).create(entityCaptor.capture(), anyBoolean(), any());

    inputSetResourcePMSImpl.createOverlayInputSet(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, overlayInputSetYaml, getScopeInfo());

    InputSetEntity persistedEntity = entityCaptor.getValue();
    assertThat(persistedEntity.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(persistedEntity.getInputSetEntityType()).isEqualTo(InputSetEntityType.OVERLAY_INPUT_SET);
    assertThat(persistedEntity.getIdentifier()).isEqualTo("overlay1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testUpdateOverlayInputSetWithVersionV1PersistsHarnessVersionV1() {
    String overlayYamlV1 = "version: 1\n"
        + "name: overlay1\n"
        + "spec:\n"
        + "  input_sets:\n"
        + "    - inputSet2\n"
        + "    - inputSet22\n";
    ArgumentCaptor<InputSetEntity> entityCaptor = ArgumentCaptor.forClass(InputSetEntity.class);
    InputSetEntity updatedEntity = InputSetEntity.builder()
                                       .accountId(ACCOUNT_ID)
                                       .orgIdentifier(ORG_IDENTIFIER)
                                       .projectIdentifier(PROJ_IDENTIFIER)
                                       .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                       .identifier("overlay1")
                                       .name("overlay1")
                                       .yaml(overlayYamlV1)
                                       .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                       .inputSetReferences(Arrays.asList("inputSet2", "inputSet22"))
                                       .harnessVersion(HarnessYamlVersion.V1)
                                       .version(1L)
                                       .build();
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(any(), any(), any(), any());
    doReturn(updatedEntity).when(pmsInputSetService).update(any(), entityCaptor.capture(), anyBoolean(), any());

    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO =
        inputSetResourcePMSImpl.updateOverlayInputSet(null, "overlay1", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, HarnessYamlVersion.V1, overlayYamlV1);

    InputSetEntity persistedEntity = entityCaptor.getValue();
    assertThat(persistedEntity.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
    assertThat(persistedEntity.getInputSetEntityType()).isEqualTo(InputSetEntityType.OVERLAY_INPUT_SET);
    assertThat(persistedEntity.getIdentifier()).isEqualTo("overlay1");
    assertThat(persistedEntity.getInputSetReferences()).containsExactly("inputSet2", "inputSet22");
    assertEquals(responseDTO.getData().getOverlayInputSetYaml(), overlayYamlV1);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testUpdateOverlayInputSetWithoutVersionRemainsV0() {
    ArgumentCaptor<InputSetEntity> entityCaptor = ArgumentCaptor.forClass(InputSetEntity.class);
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(any(), any(), any(), any());
    doReturn(overlayInputSetEntity).when(pmsInputSetService).update(any(), entityCaptor.capture(), anyBoolean(), any());

    inputSetResourcePMSImpl.updateOverlayInputSet(
        null, "overlay1", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, overlayInputSetYaml);

    InputSetEntity persistedEntity = entityCaptor.getValue();
    assertThat(persistedEntity.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(persistedEntity.getInputSetEntityType()).isEqualTo(InputSetEntityType.OVERLAY_INPUT_SET);
    assertThat(persistedEntity.getIdentifier()).isEqualTo("overlay1");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testDeleteInputSet() {
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(any(), any(), any(), any());
    doReturn(true).when(pmsInputSetService).delete(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_ID, null, true);
    ResponseDTO<Boolean> responseDTO = inputSetResourcePMSImpl.delete(
        null, INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null);
    assertTrue(responseDTO.getData());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testListInputSetsForPipeline() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(PageableExecutionUtils.getPage(Collections.singletonList(inputSetEntity),
                 PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InputSetEntityKeys.createdAt)), () -> 1L))
        .when(pmsInputSetService)
        .list(any(), any(), eq(getScopeInfo()));
    Mockito.mockStatic(InputSetValidationHelper.class);

    ResponseDTO<PageResponse<InputSetSummaryResponseDTOPMS>> responseDTO =
        inputSetResourcePMSImpl.listInputSetsForPipeline(0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, null, null, getScopeInfo());
    assertEquals(responseDTO.getStatus(), Status.SUCCESS);
    assertEquals(responseDTO.getData().getPageIndex(), 0);
    assertEquals(responseDTO.getData().getPageItemCount(), 1);
    assertEquals(responseDTO.getData().getPageSize(), 10);
    assertEquals(responseDTO.getData().getTotalItems(), 1);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetTemplateFromPipeline() {
    doReturn(InputSetTemplateResponseDTOPMS.builder().inputSetTemplateYaml(inputSetYaml).build())
        .when(validateAndMergeHelper)
        .getInputSetTemplateResponseDTO(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            Collections.emptyList(), false, null, true, true);
    ResponseDTO<InputSetTemplateResponseDTOPMS> inputSetTemplateResponseDTO =
        inputSetResourcePMSImpl.getTemplateFromPipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, "false", null);
    assertEquals(inputSetTemplateResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(inputSetTemplateResponseDTO.getData().getInputSetTemplateYaml(), inputSetYaml);

    doReturn(InputSetTemplateResponseDTOPMS.builder().inputSetTemplateYaml(inputSetYaml).build())
        .when(validateAndMergeHelper)
        .getInputSetTemplateResponseDTO(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, stages, false, null, true, true);
    inputSetTemplateResponseDTO = inputSetResourcePMSImpl.getTemplateFromPipeline(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
        InputSetTemplateRequestDTO.builder().stageIdentifiers(stages).build(), "false", null);
    assertEquals(inputSetTemplateResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(inputSetTemplateResponseDTO.getData().getInputSetTemplateYaml(), inputSetYaml);
    verify(validateAndMergeHelper, times(1))
        .getInputSetTemplateResponseDTO(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, stages, false, null, true, true);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetMergeInputSetFromPipelineTemplate() {
    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(eq(getScopeInfo()),
            eq(PIPELINE_IDENTIFIER), eq(Collections.emptyList()), eq(null), eq(null), eq(null), eq(null), anyBoolean(),
            anyBoolean(), eq(null));
    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .mergeInputSetIntoPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, pipelineYaml, null,
            null, null, false, getScopeInfo());
    MergeInputSetRequestDTOPMS inputSetRequestDTOPMS = MergeInputSetRequestDTOPMS.builder()
                                                           .withMergedPipelineYaml(true)
                                                           .inputSetReferences(Collections.emptyList())
                                                           .build();
    ResponseDTO<MergeInputSetResponseDTOPMS> mergeInputSetResponseDTOPMSResponseDTO =
        inputSetResourcePMSImpl.getMergeInputSetFromPipelineTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, null, inputSetRequestDTOPMS, "false", getScopeInfo());
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getData().getCompletePipelineYaml(), pipelineYaml);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getData().getPipelineYaml(), pipelineYaml);

    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(eq(getScopeInfo()),
            eq(PIPELINE_IDENTIFIER), eq(Collections.emptyList()), eq(null), eq(null), eq(stages), eq(null),
            anyBoolean(), anyBoolean(), eq(null));

    MergeInputSetRequestDTOPMS inputSetRequestDTOPMSWithStages = MergeInputSetRequestDTOPMS.builder()
                                                                     .withMergedPipelineYaml(false)
                                                                     .inputSetReferences(Collections.emptyList())
                                                                     .stageIdentifiers(stages)
                                                                     .build();
    mergeInputSetResponseDTOPMSResponseDTO =
        inputSetResourcePMSImpl.getMergeInputSetFromPipelineTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, null, inputSetRequestDTOPMSWithStages, "false", getScopeInfo());
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getData().getPipelineYaml(), pipelineYaml);
    verify(validateAndMergeHelper, times(1))
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(eq(getScopeInfo()),
            eq(PIPELINE_IDENTIFIER), eq(Collections.emptyList()), eq(null), eq(null), eq(stages), eq(null),
            anyBoolean(), anyBoolean(), eq(null));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetMergeInputSetFromPipelineTemplateWithErrors() {
    List<String> inputSetReferences = Arrays.asList("is1", "is2", "ois3");
    InputSetErrorWrapperDTOPMS dummyErrorResponse =
        InputSetErrorWrapperDTOPMS.builder().uuidToErrorResponseMap(Collections.singletonMap("fqn", null)).build();
    doThrow(new InvalidInputSetException("merging error", dummyErrorResponse))
        .when(validateAndMergeHelper)
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(eq(getScopeInfo()),
            eq(PIPELINE_IDENTIFIER), eq(inputSetReferences), eq(null), eq(null), eq(null), eq(null), anyBoolean(),
            anyBoolean(), eq(null));
    MergeInputSetRequestDTOPMS inputSetRequestDTO = MergeInputSetRequestDTOPMS.builder()
                                                        .withMergedPipelineYaml(true)
                                                        .inputSetReferences(inputSetReferences)
                                                        .build();
    ResponseDTO<MergeInputSetResponseDTOPMS> responseDTO =
        inputSetResourcePMSImpl.getMergeInputSetFromPipelineTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, null, inputSetRequestDTO, "false", getScopeInfo());
    MergeInputSetResponseDTOPMS data = responseDTO.getData();
    assertThat(data.isErrorResponse()).isTrue();
    assertThat(data.getInputSetErrorWrapper()).isEqualTo(dummyErrorResponse);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInputSetYAMLDiff() {
    MockedStatic<InputSetValidationHelper> mockSettings = Mockito.mockStatic(InputSetValidationHelper.class);
    when(InputSetValidationHelper.getYAMLDiff(gitSyncSdkService, pmsInputSetService, pipelineService,
             validateAndMergeHelper, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID,
             "branch", "repo", inputSetsApiUtils, null, true, true))
        .thenReturn(
            InputSetYamlDiffDTO.builder().oldYAML("old: yaml").newYAML("new: yaml").yamlDiffPresent(true).build());
    ResponseDTO<InputSetYamlDiffDTO> inputSetYAMLDiff = inputSetResourcePMSImpl.getInputSetYAMLDiff(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, "branch", "repo", null, null);
    assertThat(inputSetYAMLDiff.getData().getOldYAML()).isEqualTo("old: yaml");
    assertThat(inputSetYAMLDiff.getData().getNewYAML()).isEqualTo("new: yaml");
    assertThat(inputSetYAMLDiff.getData().isYamlDiffPresent()).isTrue();
    mockSettings.close();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetInputSetYAMLDiffWithNoDiff() {
    MockedStatic<InputSetValidationHelper> mockSettings = Mockito.mockStatic(InputSetValidationHelper.class);
    when(InputSetValidationHelper.getYAMLDiff(gitSyncSdkService, pmsInputSetService, pipelineService,
             validateAndMergeHelper, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID,
             "branch", "repo", inputSetsApiUtils, null, true, true))
        .thenReturn(InputSetYamlDiffDTO.builder()
                        .oldYAML("string: yaml")
                        .newYAML("string: yaml")
                        .yamlDiffPresent(false)
                        .build());
    ResponseDTO<InputSetYamlDiffDTO> inputSetYAMLDiff = inputSetResourcePMSImpl.getInputSetYAMLDiff(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, "branch", "repo", null, null);
    assertThat(inputSetYAMLDiff.getData().getOldYAML()).isEqualTo("string: yaml");
    assertThat(inputSetYAMLDiff.getData().getNewYAML()).isEqualTo("string: yaml");
    assertThat(inputSetYAMLDiff.getData().isYamlDiffPresent()).isFalse();
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testImportInputSetFromGit() {
    doReturn(inputSetEntity)
        .when(pmsInputSetService)
        .importInputSetFromRemote(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, null, true, null);
    GitImportInfoDTO gitImportInfoDTO = GitImportInfoDTO.builder().isForceImport(true).build();
    ResponseDTO<InputSetImportResponseDTO> inputSetImportResponse = inputSetResourcePMSImpl.importInputSetFromGit(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, gitImportInfoDTO, null, null);
    assertThat(inputSetImportResponse.getData().getIdentifier()).isEqualTo(INPUT_SET_ID);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetV1() {
    doReturn(Optional.of(inputSetEntityV1))
        .when(pmsInputSetService)
        .get(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false, false, false, true);

    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getInputSet(INPUT_SET_ID, ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false", getScopeInfo());

    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getInputSetYaml()).isEqualTo(inputSetYamlV1);
    getCallAssertions(responseDTO.getData().getName(), INPUT_SET_ID, responseDTO.getData().getIdentifier(),
        responseDTO.getData().getPipelineIdentifier(), responseDTO.getData().getProjectIdentifier(),
        responseDTO.getData().getOrgIdentifier(), responseDTO.getData().getAccountId());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCreateInputSetV1() {
    doReturn(inputSetEntityV1).when(pmsInputSetService).create(any(), anyBoolean(), any());
    doReturn(ScopeInfo.builder()
                 .accountIdentifier(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .uniqueId("unique-id")
                 .build())
        .when(pmsPipelineServiceHelper)
        .getScopeInfo(any(), any(), any(), any());
    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.createInputSet(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, HarnessYamlVersion.V1, inputSetYamlV1, null);
    assertEquals(responseDTO.getData().getInputSetYaml(), inputSetYamlV1);
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateInputSetV1() {
    doReturn(inputSetEntityV1).when(pmsInputSetService).update(any(), any(), anyBoolean());
    ResponseDTO<InputSetResponseDTOPMS> responseDTO =
        inputSetResourcePMSImpl.updateInputSet(null, "set1", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, null, HarnessYamlVersion.V1, inputSetYamlV1, getScopeInfo());
    assertEquals(responseDTO.getData().getInputSetYaml(), inputSetYamlV1);
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  private ScopeInfo getScopeInfo() {
    return ScopeInfo.builder()
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJ_IDENTIFIER)
        .uniqueId(PARENT_UNIQUE_ID)
        .build();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testDeleteInputSetV1() {
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(any(), any(), any(), any());
    doReturn(true).when(pmsInputSetService).delete(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_ID, null, true);
    ResponseDTO<Boolean> responseDTO = inputSetResourcePMSImpl.delete(
        null, INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null);
    assertTrue(responseDTO.getData());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMoveConfig() {
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO =
        InputSetMoveConfigOperationDTO.builder()
            .moveConfigOperationType(MoveConfigOperationType.INLINE_TO_REMOTE)
            .build();

    doReturn(inputSetEntity)
        .when(pmsInputSetService)
        .moveConfig(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INPUT_SET_ID, inputSetMoveConfigOperationDTO, SCOPE_INFO);

    InputSetMoveConfigRequestDTO inputSetMoveConfigRequestDTO =
        InputSetMoveConfigRequestDTO.builder()
            .inputSetIdentifier(INPUT_SET_ID)
            .moveConfigOperationType(io.harness.gitaware.helper.MoveConfigOperationType.INLINE_TO_REMOTE)
            .isNewBranch(false)
            .build();

    ResponseDTO<InputSetMoveConfigResponseDTO> movedInputSet = inputSetResourcePMSImpl.moveConfig(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INPUT_SET_ID, inputSetMoveConfigRequestDTO, SCOPE_INFO);

    assertEquals(movedInputSet.getData().getIdentifier(), INPUT_SET_ID);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testInputSetRepoListing() {
    List<String> repos = new ArrayList<>();
    repos.add("testRepo");
    repos.add("testRepo2");

    PMSInputSetListRepoResponse repoResponse = PMSInputSetListRepoResponse.builder().repositories(repos).build();
    doReturn(repoResponse)
        .when(pmsInputSetService)
        .getListOfRepos(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, SCOPE_INFO, false);

    PMSInputSetListRepoResponse pmsPipelineListRepoResponse = pmsInputSetService.getListOfRepos(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, SCOPE_INFO, false);
    assertEquals(pmsPipelineListRepoResponse, repoResponse);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetMergeInputSetForRerun() {
    doReturn("mergedYaml")
        .when(executionService)
        .mergeRuntimeInputIntoPipelineForRerun(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "plan",
            "", "", Collections.emptyList(), null);
    ResponseDTO<MergeInputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getMergeInputSetForRerun(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "", "", null,
        MergeInputSetForRerunRequestDTO.builder()
            .planExecutionId("plan")
            .stageIdentifiers(Collections.emptyList())
            .build(),
        null);
    assertEquals("mergedYaml", responseDTO.getData().getPipelineYaml());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetMergeInputSetFromPipelineForRemoteBranchTemplate() {
    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(eq(getScopeInfo()),
            eq(PIPELINE_IDENTIFIER), eq(Collections.emptyList()), eq("remote"), eq(null), eq(null), eq(null),
            anyBoolean(), anyBoolean(), eq(null));
    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .mergeInputSetIntoPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, pipelineYaml,
            "remote", null, null, false, getScopeInfo());
    MergeInputSetRequestDTOPMS inputSetRequestDTOPMS = MergeInputSetRequestDTOPMS.builder()
                                                           .withMergedPipelineYaml(true)
                                                           .inputSetReferences(Collections.emptyList())
                                                           .build();
    GitEntityFindInfoDTO gitEntityFindInfoDTO = GitEntityFindInfoDTO.builder().branch("remote").build();
    ResponseDTO<MergeInputSetResponseDTOPMS> mergeInputSetResponseDTOPMSResponseDTO =
        inputSetResourcePMSImpl.getMergeInputSetFromPipelineTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, gitEntityFindInfoDTO, inputSetRequestDTOPMS, "false", getScopeInfo());
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getData().getCompletePipelineYaml(), pipelineYaml);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getData().getPipelineYaml(), pipelineYaml);

    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(eq(getScopeInfo()),
            eq(PIPELINE_IDENTIFIER), eq(Collections.emptyList()), eq("remote"), eq(null), eq(stages), eq(null),
            anyBoolean(), anyBoolean(), eq(null));

    MergeInputSetRequestDTOPMS inputSetRequestDTOPMSWithStages = MergeInputSetRequestDTOPMS.builder()
                                                                     .withMergedPipelineYaml(false)
                                                                     .inputSetReferences(Collections.emptyList())
                                                                     .stageIdentifiers(stages)
                                                                     .build();
    mergeInputSetResponseDTOPMSResponseDTO = inputSetResourcePMSImpl.getMergeInputSetFromPipelineTemplate(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, gitEntityFindInfoDTO,
        inputSetRequestDTOPMSWithStages, "false", getScopeInfo());
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getData().getPipelineYaml(), pipelineYaml);
    verify(validateAndMergeHelper, times(1))
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(eq(getScopeInfo()),
            eq(PIPELINE_IDENTIFIER), eq(Collections.emptyList()), eq("remote"), eq(null), eq(stages), eq(null),
            anyBoolean(), anyBoolean(), eq(null));
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testForceImportInputSet() {
    ForceImportInputSetRequestDTO requestDTO = ForceImportInputSetRequestDTO.builder()
                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                   .projectIdentifier(PROJ_IDENTIFIER)
                                                   .identifier(INPUT_SET_ID)
                                                   .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                   .connectorRef("connectorRef")
                                                   .filePath(".harness/is1.yaml")
                                                   .repoName("test-repo")
                                                   .build();

    doReturn(ForceImportInputSetResponse.builder().build())
        .when(pmsInputSetService)
        .forceImportInputSet(any(), any(), any());

    ArgumentCaptor<ForceImportInputSetYamlOperationDTO> operationDTOCaptor =
        ArgumentCaptor.forClass(ForceImportInputSetYamlOperationDTO.class);
    inputSetResourcePMSImpl.forceImportInputSet(ACCOUNT_ID, requestDTO);

    verify(pipelineSplitPermissionsHelper)
        .checkForPipelineRBACSplitAccessPermissions(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            false, PipelineRbacPermissions.PIPELINE_EDIT,
            Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    verify(pmsInputSetService).forceImportInputSet(any(), operationDTOCaptor.capture(), any());
    assertThat(operationDTOCaptor.getValue().getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(operationDTOCaptor.getValue().getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(operationDTOCaptor.getValue().getIdentifier()).isEqualTo(INPUT_SET_ID);
    assertThat(operationDTOCaptor.getValue().getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(operationDTOCaptor.getValue().getConnectorRef()).isEqualTo("connectorRef");
    assertThat(operationDTOCaptor.getValue().getFilePath()).isEqualTo(".harness/is1.yaml");
    assertThat(operationDTOCaptor.getValue().getRepoName()).isEqualTo("test-repo");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testForceImportInputSetWithInputSetRbacEnabled() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, io.harness.beans.FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS))
        .thenReturn(true);
    ForceImportInputSetRequestDTO requestDTO = ForceImportInputSetRequestDTO.builder()
                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                   .projectIdentifier(PROJ_IDENTIFIER)
                                                   .identifier(INPUT_SET_ID)
                                                   .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                   .connectorRef("connectorRef")
                                                   .filePath(".harness/is1.yaml")
                                                   .repoName("test-repo")
                                                   .build();

    doReturn(ForceImportInputSetResponse.builder().build())
        .when(pmsInputSetService)
        .forceImportInputSet(any(), any(), any());

    inputSetResourcePMSImpl.forceImportInputSet(ACCOUNT_ID, requestDTO);

    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("INPUT_SET", PIPELINE_IDENTIFIER + "-" + INPUT_SET_ID),
            InputSetRbacPermissions.INPUTSET_CREATE_AND_EDIT);
    verify(pipelineSplitPermissionsHelper, times(0))
        .checkForPipelineRBACSplitAccessPermissions(any(), any(), any(), any(), anyBoolean(), any(), any());
    verify(pmsInputSetService).forceImportInputSet(any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadata() {
    List<String> pipelineIdentifiers = Arrays.asList("pipeline1", "pipeline2");
    BatchInputSetsAPIRequest apiRequest =
        BatchInputSetsAPIRequest.builder().pipelineIdentifiers(pipelineIdentifiers).build();

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier("pipeline1")
                                   .description("Test input set 1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();
    InputSetEntity inputSet2 = InputSetEntity.builder()
                                   .identifier("inputset2")
                                   .name("Input Set 2")
                                   .pipelineIdentifier("pipeline1")
                                   .description("Test input set 2")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();
    InputSetEntity inputSet3 = InputSetEntity.builder()
                                   .identifier("inputset3")
                                   .name("Input Set 3")
                                   .pipelineIdentifier("pipeline2")
                                   .description("Test input set 3")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    List<InputSetEntity> allInputSets = Arrays.asList(inputSet1, inputSet2, inputSet3);

    ArgumentCaptor<BatchInputSetsRequestDTO> requestDtoCaptor = ArgumentCaptor.forClass(BatchInputSetsRequestDTO.class);

    Page<InputSetEntity> page = new PageImpl<>(allInputSets, PageRequest.of(0, 25), allInputSets.size());

    when(pmsInputSetService.getBatchInputSetsMetadata(eq(getScopeInfo()), requestDtoCaptor.capture())).thenReturn(page);

    try (MockedStatic<PMSInputSetElementMapper> mockedMapper = Mockito.mockStatic(PMSInputSetElementMapper.class)) {
      InputSetListResponseDTO dto1 = InputSetListResponseDTO.builder()
                                         .identifier("inputset1")
                                         .name("Input Set 1")
                                         .pipelineIdentifier("pipeline1")
                                         .description("Test input set 1")
                                         .inputSetType(InputSetEntityType.INPUT_SET)
                                         .build();
      InputSetListResponseDTO dto2 = InputSetListResponseDTO.builder()
                                         .identifier("inputset2")
                                         .name("Input Set 2")
                                         .pipelineIdentifier("pipeline1")
                                         .description("Test input set 2")
                                         .inputSetType(InputSetEntityType.INPUT_SET)
                                         .build();
      InputSetListResponseDTO dto3 = InputSetListResponseDTO.builder()
                                         .identifier("inputset3")
                                         .name("Input Set 3")
                                         .pipelineIdentifier("pipeline2")
                                         .description("Test input set 3")
                                         .inputSetType(InputSetEntityType.INPUT_SET)
                                         .build();

      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetListResponseDTO(inputSet1)).thenReturn(dto1);
      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetListResponseDTO(inputSet2)).thenReturn(dto2);
      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetListResponseDTO(inputSet3)).thenReturn(dto3);

      ResponseDTO<PageResponse<InputSetListResponseDTO>> response = inputSetResourcePMSImpl.getBatchInputSetsMetadata(
          inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(), 0,
          25, null, getScopeInfo(), apiRequest);

      assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
      assertThat(response.getData()).isNotNull();
      assertThat(response.getData().getContent()).isNotNull();
      assertThat(response.getData().getContent().size()).isEqualTo(3);

      List<String> inputSetIds = response.getData()
                                     .getContent()
                                     .stream()
                                     .map(InputSetListResponseDTO::getIdentifier)
                                     .collect(Collectors.toList());
      assertThat(inputSetIds).containsExactlyInAnyOrder("inputset1", "inputset2", "inputset3");

      Map<String, String> pipelineMap = response.getData().getContent().stream().collect(
          Collectors.toMap(InputSetListResponseDTO::getIdentifier, InputSetListResponseDTO::getPipelineIdentifier));
      assertThat(pipelineMap.get("inputset1")).isEqualTo("pipeline1");
      assertThat(pipelineMap.get("inputset2")).isEqualTo("pipeline1");
      assertThat(pipelineMap.get("inputset3")).isEqualTo("pipeline2");

      BatchInputSetsRequestDTO capturedRequestDto = requestDtoCaptor.getValue();
      assertThat(capturedRequestDto.getPipelineIdentifiers()).isEqualTo(pipelineIdentifiers);

      verify(pmsInputSetService).getBatchInputSetsMetadata(eq(getScopeInfo()), any(BatchInputSetsRequestDTO.class));
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadataWithEmptyListFallsBackToRbac() {
    BatchInputSetsAPIRequest emptyListAPIRequest =
        BatchInputSetsAPIRequest.builder().pipelineIdentifiers(Collections.emptyList()).build();

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier("pipeline1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    Page<InputSetEntity> mockPage = new PageImpl<>(Collections.singletonList(inputSet1));

    when(pipelineService.listAllIdentifiers(any())).thenReturn(Arrays.asList("pipeline1"));
    when(pipelineService.getPermittedPipelineIdentifier(any(), any(), any(), any()))
        .thenReturn(Arrays.asList("pipeline1"));
    when(pmsInputSetService.getBatchInputSetsMetadata(any(ScopeInfo.class), any(BatchInputSetsRequestDTO.class)))
        .thenReturn(mockPage);

    try (MockedStatic<PMSInputSetElementMapper> mockedMapper = Mockito.mockStatic(PMSInputSetElementMapper.class)) {
      InputSetListResponseDTO dto1 = InputSetListResponseDTO.builder()
                                         .identifier("inputset1")
                                         .name("Input Set 1")
                                         .pipelineIdentifier("pipeline1")
                                         .inputSetType(InputSetEntityType.INPUT_SET)
                                         .build();
      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetListResponseDTO(inputSet1)).thenReturn(dto1);

      ResponseDTO<PageResponse<InputSetListResponseDTO>> response = inputSetResourcePMSImpl.getBatchInputSetsMetadata(
          inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(), 0,
          25, null, getScopeInfo(), emptyListAPIRequest);

      assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
      assertThat(response.getData().getContent()).hasSize(1);
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadataWithNullRequest() {
    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier("pipeline1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    Page<InputSetEntity> mockPage = new PageImpl<>(Collections.singletonList(inputSet1));

    when(pipelineService.listAllIdentifiers(any())).thenReturn(Arrays.asList("pipeline1"));
    when(pipelineService.getPermittedPipelineIdentifier(any(), any(), any(), any()))
        .thenReturn(Arrays.asList("pipeline1"));
    when(pmsInputSetService.getBatchInputSetsMetadata(any(ScopeInfo.class), any(BatchInputSetsRequestDTO.class)))
        .thenReturn(mockPage);

    try (MockedStatic<PMSInputSetElementMapper> mockedMapper = Mockito.mockStatic(PMSInputSetElementMapper.class)) {
      InputSetListResponseDTO dto1 = InputSetListResponseDTO.builder()
                                         .identifier("inputset1")
                                         .name("Input Set 1")
                                         .pipelineIdentifier("pipeline1")
                                         .inputSetType(InputSetEntityType.INPUT_SET)
                                         .build();
      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetListResponseDTO(inputSet1)).thenReturn(dto1);

      ResponseDTO<PageResponse<InputSetListResponseDTO>> response = inputSetResourcePMSImpl.getBatchInputSetsMetadata(
          inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(), 0,
          25, null, getScopeInfo(), null);

      assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
      assertThat(response.getData().getContent()).hasSize(1);
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadataWithInputSetRbacEnabled() {
    BatchInputSetsAPIRequest nullListAPIRequest = BatchInputSetsAPIRequest.builder().pipelineIdentifiers(null).build();

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier("pipeline1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    InputSetEntity inputSet2 = InputSetEntity.builder()
                                   .identifier("inputset2")
                                   .name("Input Set 2")
                                   .pipelineIdentifier("pipeline2")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    Page<InputSetEntity> mockPage = new PageImpl<>(Arrays.asList(inputSet1, inputSet2));

    when(pmsFeatureFlagService.isEnabled(any(), eq(io.harness.beans.FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)))
        .thenReturn(true);
    when(pmsInputSetService.getAllInputSetsMetadataForProject(any(ScopeInfo.class), eq(0), eq(1000), any()))
        .thenReturn(mockPage);

    AccessCheckResponseDTO accessCheckResponse =
        AccessCheckResponseDTO.builder()
            .accessControlList(Arrays.asList(
                AccessControlDTO.builder().resourceIdentifier("pipeline1-inputset1").permitted(true).build(),
                AccessControlDTO.builder().resourceIdentifier("pipeline2-inputset2").permitted(false).build()))
            .build();
    when(accessControlClient.checkForAccess(any())).thenReturn(accessCheckResponse);

    try (MockedStatic<PMSInputSetElementMapper> mockedMapper = Mockito.mockStatic(PMSInputSetElementMapper.class)) {
      InputSetListResponseDTO dto1 = InputSetListResponseDTO.builder()
                                         .identifier("inputset1")
                                         .name("Input Set 1")
                                         .pipelineIdentifier("pipeline1")
                                         .inputSetType(InputSetEntityType.INPUT_SET)
                                         .build();
      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetListResponseDTO(inputSet1)).thenReturn(dto1);

      ResponseDTO<PageResponse<InputSetListResponseDTO>> response = inputSetResourcePMSImpl.getBatchInputSetsMetadata(
          inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(), 0,
          25, null, getScopeInfo(), nullListAPIRequest);

      assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
      assertThat(response.getData().getContent()).hasSize(1);
      assertThat(response.getData().getContent().get(0).getIdentifier()).isEqualTo("inputset1");
      assertThat(response.getData().getTotalItems()).isEqualTo(1);
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBulkInputSets() {
    String accountId = inputSetEntity.getAccountId();
    String orgIdentifier = inputSetEntity.getOrgIdentifier();
    String projectIdentifier = inputSetEntity.getProjectIdentifier();
    String pipelineIdentifier = "testPipeline";
    List<String> inputSetIdentifiers = Arrays.asList("inputset1", "inputset2");

    BulkInputSetsAPIRequest apiRequest =
        BulkInputSetsAPIRequest.builder().inputSetIdentifiers(inputSetIdentifiers).build();

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

    InputSetSummaryResponseDTOPMS inputSetResponse1 = InputSetSummaryResponseDTOPMS.builder()
                                                          .identifier("inputset1")
                                                          .name("Input Set 1")
                                                          .pipelineIdentifier(pipelineIdentifier)
                                                          .description("Test input set 1")
                                                          .build();

    InputSetSummaryResponseDTOPMS inputSetResponse2 = InputSetSummaryResponseDTOPMS.builder()
                                                          .identifier("inputset2")
                                                          .name("Input Set 2")
                                                          .pipelineIdentifier(pipelineIdentifier)
                                                          .description("Test input set 2")
                                                          .build();

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

    BulkInputSetsResponseDTO serviceResponse =
        BulkInputSetsResponseDTO.builder().inputSets(Arrays.asList(response1, response2)).build();

    ArgumentCaptor<BulkInputSetsRequestDTO> serviceRequestCaptor =
        ArgumentCaptor.forClass(BulkInputSetsRequestDTO.class);
    when(pmsInputSetService.getBulkInputSets(
             any(ScopeInfo.class), eq(pipelineIdentifier), serviceRequestCaptor.capture()))
        .thenReturn(serviceResponse);

    try (MockedStatic<PMSInputSetElementMapper> mockedMapper = Mockito.mockStatic(PMSInputSetElementMapper.class)) {
      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetSummaryResponseDTOPMS(inputSet1))
          .thenReturn(inputSetResponse1);
      mockedMapper.when(() -> PMSInputSetElementMapper.toInputSetSummaryResponseDTOPMS(inputSet2))
          .thenReturn(inputSetResponse2);

      ResponseDTO<BulkInputSetsAPIResponse> response = inputSetResourcePMSImpl.getBulkInputSets(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, getScopeInfo(), apiRequest);

      assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
      assertThat(response.getData()).isNotNull();
      assertThat(response.getData().getInputSets()).isNotNull();
      assertThat(response.getData().getInputSets().size()).isEqualTo(2);
      assertThat(response.getData().getInputSets().get(0).getIdentifier()).isEqualTo("inputset1");
      assertThat(response.getData().getInputSets().get(1).getIdentifier()).isEqualTo("inputset2");

      ArgumentCaptor<ScopeInfo> scopeInfoCaptor = ArgumentCaptor.forClass(ScopeInfo.class);
      verify(pmsInputSetService)
          .getBulkInputSets(scopeInfoCaptor.capture(), eq(pipelineIdentifier), any(BulkInputSetsRequestDTO.class));

      ScopeInfo capturedScopeInfo = scopeInfoCaptor.getValue();
      assertThat(capturedScopeInfo.getAccountIdentifier()).isEqualTo(accountId);
      assertThat(capturedScopeInfo.getOrgIdentifier()).isEqualTo(orgIdentifier);
      assertThat(capturedScopeInfo.getProjectIdentifier()).isEqualTo(projectIdentifier);

      BulkInputSetsRequestDTO capturedServiceRequest = serviceRequestCaptor.getValue();
      assertThat(capturedServiceRequest.getInputSetIdentifiers()).isEqualTo(inputSetIdentifiers);
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBulkInputSetsWithEmptyList() {
    String accountId = inputSetEntity.getAccountId();
    String orgIdentifier = inputSetEntity.getOrgIdentifier();
    String projectIdentifier = inputSetEntity.getProjectIdentifier();
    String pipelineIdentifier = "testPipeline";

    BulkInputSetsAPIRequest emptyRequest =
        BulkInputSetsAPIRequest.builder().inputSetIdentifiers(Collections.emptyList()).build();

    when(pmsInputSetService.getBulkInputSets(any(ScopeInfo.class), eq(pipelineIdentifier),
             argThat(req -> req.getInputSetIdentifiers() != null && req.getInputSetIdentifiers().isEmpty())))
        .thenThrow(new InvalidRequestException("Input set identifiers list cannot be empty."));

    assertThatThrownBy(()
                           -> inputSetResourcePMSImpl.getBulkInputSets(accountId, orgIdentifier, projectIdentifier,
                               pipelineIdentifier, getScopeInfo(), emptyRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input set identifiers list cannot be empty.");

    BulkInputSetsAPIRequest nullRequest = BulkInputSetsAPIRequest.builder().inputSetIdentifiers(null).build();

    when(pmsInputSetService.getBulkInputSets(
             any(ScopeInfo.class), eq(pipelineIdentifier), argThat(req -> req.getInputSetIdentifiers() == null)))
        .thenThrow(new InvalidRequestException("Input set identifiers list cannot be empty."));

    assertThatThrownBy(()
                           -> inputSetResourcePMSImpl.getBulkInputSets(accountId, orgIdentifier, projectIdentifier,
                               pipelineIdentifier, getScopeInfo(), nullRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input set identifiers list cannot be empty.");

    verify(pmsInputSetService, times(2)).getBulkInputSets(any(), any(), any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetMergeInputSetFromPipelineTemplate_WithInputSetBranch() {
    // Given
    String inputSetBranch = "feature-branch";
    MergeInputSetRequestDTOPMS inputSetRequestDTO = MergeInputSetRequestDTOPMS.builder()
                                                        .inputSetReferences(Collections.emptyList())
                                                        .withMergedPipelineYaml(false)
                                                        .inputSetBranchName(inputSetBranch)
                                                        .build();

    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(eq(getScopeInfo()),
            eq(PIPELINE_IDENTIFIER), eq(Collections.emptyList()), eq(null), eq(null), eq(null), eq(null), anyBoolean(),
            anyBoolean(), eq(inputSetBranch));

    // When
    ResponseDTO<MergeInputSetResponseDTOPMS> response =
        inputSetResourcePMSImpl.getMergeInputSetFromPipelineTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, null, inputSetRequestDTO, null, getScopeInfo());

    // Then
    assertEquals(response.getStatus(), Status.SUCCESS);
    assertEquals(response.getData().getPipelineYaml(), pipelineYaml);

    // Verify that inputSetBranch was passed correctly
    verify(validateAndMergeHelper, times(1))
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(eq(getScopeInfo()),
            eq(PIPELINE_IDENTIFIER), eq(Collections.emptyList()), eq(null), eq(null), eq(null), eq(null), anyBoolean(),
            anyBoolean(), eq(inputSetBranch));
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetMergeInputSetFromPipelineTemplate_WithNullInputSetBranch() {
    // Given
    MergeInputSetRequestDTOPMS inputSetRequestDTO = MergeInputSetRequestDTOPMS.builder()
                                                        .inputSetReferences(Collections.emptyList())
                                                        .withMergedPipelineYaml(false)
                                                        .inputSetBranchName(null)
                                                        .build();

    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(eq(getScopeInfo()),
            eq(PIPELINE_IDENTIFIER), eq(Collections.emptyList()), eq(null), eq(null), eq(null), eq(null), anyBoolean(),
            anyBoolean(), eq(null));

    // When
    ResponseDTO<MergeInputSetResponseDTOPMS> response =
        inputSetResourcePMSImpl.getMergeInputSetFromPipelineTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, null, inputSetRequestDTO, null, getScopeInfo());

    // Then
    assertEquals(response.getStatus(), Status.SUCCESS);
    assertEquals(response.getData().getPipelineYaml(), pipelineYaml);

    // Verify that null inputSetBranch was passed correctly
    verify(validateAndMergeHelper, times(1))
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(eq(getScopeInfo()),
            eq(PIPELINE_IDENTIFIER), eq(Collections.emptyList()), eq(null), eq(null), eq(null), eq(null), anyBoolean(),
            anyBoolean(), eq(null));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadataWithEmptyAllPipelineIds() {
    BatchInputSetsAPIRequest nullListAPIRequest = BatchInputSetsAPIRequest.builder().pipelineIdentifiers(null).build();

    when(pipelineService.listAllIdentifiers(any())).thenReturn(Collections.emptyList());

    ResponseDTO<PageResponse<InputSetListResponseDTO>> response = inputSetResourcePMSImpl.getBatchInputSetsMetadata(
        inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(), 0, 25,
        null, getScopeInfo(), nullListAPIRequest);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData().getContent()).isEmpty();
    assertThat(response.getData().getTotalItems()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadataWithEmptyPermittedPipelineIds() {
    BatchInputSetsAPIRequest nullListAPIRequest = BatchInputSetsAPIRequest.builder().pipelineIdentifiers(null).build();

    when(pipelineService.listAllIdentifiers(any())).thenReturn(Arrays.asList("pipeline1", "pipeline2"));
    when(pipelineService.getPermittedPipelineIdentifier(any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());

    ResponseDTO<PageResponse<InputSetListResponseDTO>> response = inputSetResourcePMSImpl.getBatchInputSetsMetadata(
        inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(), 0, 25,
        null, getScopeInfo(), nullListAPIRequest);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData().getContent()).isEmpty();
    assertThat(response.getData().getTotalItems()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadataWithInputSetRbacEnabledPaginationBeyondResults() {
    BatchInputSetsAPIRequest nullListAPIRequest = BatchInputSetsAPIRequest.builder().pipelineIdentifiers(null).build();

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier("pipeline1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    Page<InputSetEntity> mockPage = new PageImpl<>(Collections.singletonList(inputSet1));

    when(pmsFeatureFlagService.isEnabled(any(), eq(io.harness.beans.FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)))
        .thenReturn(true);
    when(pmsInputSetService.getAllInputSetsMetadataForProject(any(ScopeInfo.class), eq(0), eq(1000), any()))
        .thenReturn(mockPage);

    AccessCheckResponseDTO accessCheckResponse =
        AccessCheckResponseDTO.builder()
            .accessControlList(Collections.singletonList(
                AccessControlDTO.builder().resourceIdentifier("pipeline1-inputset1").permitted(true).build()))
            .build();
    when(accessControlClient.checkForAccess(any())).thenReturn(accessCheckResponse);

    ResponseDTO<PageResponse<InputSetListResponseDTO>> response = inputSetResourcePMSImpl.getBatchInputSetsMetadata(
        inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(), 10, 25,
        null, getScopeInfo(), nullListAPIRequest);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadataWithInputSetRbacEnabledEmptyInputSets() {
    BatchInputSetsAPIRequest nullListAPIRequest = BatchInputSetsAPIRequest.builder().pipelineIdentifiers(null).build();

    Page<InputSetEntity> emptyPage = Page.empty();

    when(pmsFeatureFlagService.isEnabled(any(), eq(io.harness.beans.FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)))
        .thenReturn(true);
    when(pmsInputSetService.getAllInputSetsMetadataForProject(any(ScopeInfo.class), eq(0), eq(1000), any()))
        .thenReturn(emptyPage);

    ResponseDTO<PageResponse<InputSetListResponseDTO>> response = inputSetResourcePMSImpl.getBatchInputSetsMetadata(
        inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(), 0, 25,
        null, getScopeInfo(), nullListAPIRequest);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadataWithInputSetRbacEnabledNullAccessControlList() {
    BatchInputSetsAPIRequest nullListAPIRequest = BatchInputSetsAPIRequest.builder().pipelineIdentifiers(null).build();

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier("pipeline1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    Page<InputSetEntity> mockPage = new PageImpl<>(Collections.singletonList(inputSet1));

    when(pmsFeatureFlagService.isEnabled(any(), eq(io.harness.beans.FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)))
        .thenReturn(true);
    when(pmsInputSetService.getAllInputSetsMetadataForProject(any(ScopeInfo.class), eq(0), eq(1000), any()))
        .thenReturn(mockPage);

    when(accessControlClient.checkForAccess(any())).thenReturn(null);

    ResponseDTO<PageResponse<InputSetListResponseDTO>> response = inputSetResourcePMSImpl.getBatchInputSetsMetadata(
        inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(), 0, 25,
        null, getScopeInfo(), nullListAPIRequest);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetBatchInputSetsMetadataWithInputSetRbacEnabledEmptyAccessControlList() {
    BatchInputSetsAPIRequest nullListAPIRequest = BatchInputSetsAPIRequest.builder().pipelineIdentifiers(null).build();

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier("inputset1")
                                   .name("Input Set 1")
                                   .pipelineIdentifier("pipeline1")
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .build();

    Page<InputSetEntity> mockPage = new PageImpl<>(Collections.singletonList(inputSet1));

    when(pmsFeatureFlagService.isEnabled(any(), eq(io.harness.beans.FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)))
        .thenReturn(true);
    when(pmsInputSetService.getAllInputSetsMetadataForProject(any(ScopeInfo.class), eq(0), eq(1000), any()))
        .thenReturn(mockPage);

    AccessCheckResponseDTO accessCheckResponse =
        AccessCheckResponseDTO.builder().accessControlList(Collections.emptyList()).build();
    when(accessControlClient.checkForAccess(any())).thenReturn(accessCheckResponse);

    ResponseDTO<PageResponse<InputSetListResponseDTO>> response = inputSetResourcePMSImpl.getBatchInputSetsMetadata(
        inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(), 0, 25,
        null, getScopeInfo(), nullListAPIRequest);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteInputSetMetadataMapsServiceResponseAndComputesTotal() {
    io.harness.beans.Scope repoAScope = io.harness.beans.Scope.of(ACCOUNT_ID, "orgA", "projA", "uid-projA");
    java.util.Map<String, io.harness.beans.Scope> repoAFilePaths = new java.util.HashMap<>();
    repoAFilePaths.put(".harness/build.yaml", repoAScope);
    repoAFilePaths.put(".harness/deploy.yaml", repoAScope);
    io.harness.pms.inputset.InputSetRemoteRepoInfo repoA =
        io.harness.pms.inputset.InputSetRemoteRepoInfo.builder()
            .repoName("harness-core")
            .repoURL("https://github.com/wings-software/harness-core")
            .count(5L)
            .filePathsByOwningScope(repoAFilePaths)
            .connectorRefs(new java.util.HashSet<>(java.util.Arrays.asList(ACCOUNT_ID + "/orgA/projA/githubMain")))
            .build();
    io.harness.beans.Scope repoBScope = io.harness.beans.Scope.of(ACCOUNT_ID, null, null, ACCOUNT_ID);
    java.util.Map<String, io.harness.beans.Scope> repoBFilePaths = new java.util.HashMap<>();
    repoBFilePaths.put("inputSets/argocd-sync.yaml", repoBScope);
    io.harness.pms.inputset.InputSetRemoteRepoInfo repoB =
        io.harness.pms.inputset.InputSetRemoteRepoInfo.builder()
            .repoName("gitops-config")
            .repoURL("https://github.com/wings-software/gitops-config")
            .count(2L)
            .filePathsByOwningScope(repoBFilePaths)
            .connectorRefs(new java.util.HashSet<>(java.util.Arrays.asList(ACCOUNT_ID + "/accountGithub")))
            .build();
    io.harness.pms.inputset.InputSetRemoteRepoListResponse serviceResponse =
        io.harness.pms.inputset.InputSetRemoteRepoListResponse.builder()
            .repositories(java.util.Arrays.asList(repoA, repoB))
            .build();
    when(pmsInputSetService.getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, null, null, 0, 20))
        .thenReturn(serviceResponse);

    ResponseDTO<RemoteInputSetsResponseDTO> result =
        inputSetResourcePMSImpl.getRemoteInputSetMetadata(ACCOUNT_ID, null, null, null, 0, 20, null);

    verify(pmsInputSetService, times(1)).getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, null, null, 0, 20);
    assertThat(result.getData().getTotalInputSets()).isEqualTo(7L);
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
  public void testGetRemoteInputSetMetadataPassesRepoNameFilterThrough() {
    io.harness.pms.inputset.InputSetRemoteRepoListResponse serviceResponse =
        io.harness.pms.inputset.InputSetRemoteRepoListResponse.builder()
            .repositories(java.util.Collections.emptyList())
            .build();
    when(pmsInputSetService.getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, "harness-core", null, 0, 20))
        .thenReturn(serviceResponse);

    ResponseDTO<RemoteInputSetsResponseDTO> result =
        inputSetResourcePMSImpl.getRemoteInputSetMetadata(ACCOUNT_ID, null, null, "harness-core", 0, 20, null);

    verify(pmsInputSetService, times(1))
        .getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, "harness-core", null, 0, 20);
    assertThat(result.getData().getTotalInputSets()).isEqualTo(0L);
    assertThat(result.getData().getRepositories()).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteInputSetMetadataHandlesNullRepositoriesFromService() {
    io.harness.pms.inputset.InputSetRemoteRepoListResponse serviceResponse =
        io.harness.pms.inputset.InputSetRemoteRepoListResponse.builder().repositories(null).build();
    when(pmsInputSetService.getRemoteRepoListForAGivenScope(ACCOUNT_ID, null, null, null, null, 0, 20))
        .thenReturn(serviceResponse);

    ResponseDTO<RemoteInputSetsResponseDTO> result =
        inputSetResourcePMSImpl.getRemoteInputSetMetadata(ACCOUNT_ID, null, null, null, 0, 20, null);

    assertThat(result.getData()).isNotNull();
    assertThat(result.getData().getTotalInputSets()).isEqualTo(0L);
    assertThat(result.getData().getRepositories()).isEmpty();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshAndGetInputSet_ThrowsWhenFeatureFlagDisabled() {
    doThrow(new UnavailableFeatureException("Cache refresh for input set is not enabled. PIPE_GITX_FORCE_REFRESH"))
        .when(pmsInputSetService)
        .refreshGitFileCache(any(), any(), any(), any(), any(), eq("main"), any());
    assertThatThrownBy(()
                           -> inputSetResourcePMSImpl.refreshAndGetInputSet(INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "main", getScopeInfo()))
        .isInstanceOf(UnavailableFeatureException.class)
        .hasMessageContaining("PIPE_GITX_FORCE_REFRESH");
    verify(pmsInputSetService, never())
        .get(any(), any(), any(), anyBoolean(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshAndGetInputSet_DelegatesToServiceAndFetchesFresh() {
    doReturn(Optional.of(inputSetEntity))
        .when(pmsInputSetService)
        .get(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false, false, false, true);

    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.refreshAndGetInputSet(
        INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "main", getScopeInfo());

    verify(pmsInputSetService, times(1))
        .refreshGitFileCache(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, "main", getScopeInfo());
    assertThat(responseDTO.getData().getInputSetYaml()).isEqualTo(inputSetYaml);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshAndGetInputSet_InlineEntity_ThrowsInvalidRequest() {
    doThrow(new InvalidRequestException("Cache refresh applies only to remote Git-backed input sets."))
        .when(pmsInputSetService)
        .refreshGitFileCache(any(), any(), any(), any(), eq(INPUT_SET_ID), eq("main"), any());

    assertThatThrownBy(()
                           -> inputSetResourcePMSImpl.refreshAndGetInputSet(INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "main", getScopeInfo()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("remote Git-backed input sets");
    verify(pmsInputSetService, never())
        .get(any(), any(), any(), anyBoolean(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshAndGetInputSet_ServiceExceptionPropagates_MissingBranch() {
    doThrow(new InvalidRequestException("A valid git branch is required to refresh cache for input set"))
        .when(pmsInputSetService)
        .refreshGitFileCache(any(), any(), any(), any(), any(), eq(null), any());

    assertThatThrownBy(()
                           -> inputSetResourcePMSImpl.refreshAndGetInputSet(INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, getScopeInfo()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("A valid git branch is required");

    verify(pmsInputSetService, never())
        .get(any(), any(), any(), anyBoolean(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshAndGetInputSet_ThrowsWhenInputSetRbacDenied() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, io.harness.beans.FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS))
        .thenReturn(true);
    doThrow(new AccessDeniedException("Not authorized", EnumSet.noneOf(WingsException.ReportTarget.class)))
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("INPUT_SET", PIPELINE_IDENTIFIER + "-" + INPUT_SET_ID), InputSetRbacPermissions.INPUTSET_VIEW);

    assertThatThrownBy(()
                           -> inputSetResourcePMSImpl.refreshAndGetInputSet(INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "main", getScopeInfo()))
        .isInstanceOf(AccessDeniedException.class);
    verify(pmsInputSetService, never()).refreshGitFileCache(any(), any(), any(), any(), any(), any(), any());
    verify(pmsInputSetService, never())
        .get(any(), any(), any(), anyBoolean(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testRefreshAndGetInputSet_RbacCheckBeforeRefreshWhenFfEnabled() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, io.harness.beans.FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS))
        .thenReturn(true);
    doReturn(Optional.of(inputSetEntity))
        .when(pmsInputSetService)
        .get(getScopeInfo(), PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false, false, false, true);

    inputSetResourcePMSImpl.refreshAndGetInputSet(
        INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "main", getScopeInfo());

    InOrder inOrder = inOrder(accessControlClient, pmsInputSetService);
    inOrder.verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("INPUT_SET", PIPELINE_IDENTIFIER + "-" + INPUT_SET_ID), InputSetRbacPermissions.INPUTSET_VIEW);
    inOrder.verify(pmsInputSetService)
        .refreshGitFileCache(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER),
            eq(INPUT_SET_ID), eq("main"), any());
  }
}

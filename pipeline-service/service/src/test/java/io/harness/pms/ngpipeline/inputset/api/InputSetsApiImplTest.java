/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.ngpipeline.inputset.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.ngpipeline.inputset.api.utils.InputSetsApiUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.helpers.validate.InputSetValidationHelper;
import io.harness.pms.ngpipeline.inputset.helpers.validate.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.resources.InputSetResourcePMSImpl;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.rbac.InputSetRbacPermissions;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.GitImportInfo;
import io.harness.spec.server.pipeline.v1.model.GitMoveDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetImportRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetImportRequestDTO;
import io.harness.spec.server.pipeline.v1.model.InputSetMoveConfigRequestBody;
import io.harness.spec.server.pipeline.v1.model.InputSetMoveConfigResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetResponseBody;
import io.harness.spec.server.pipeline.v1.model.InputSetUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.MergeInputSetRequestBody;
import io.harness.spec.server.pipeline.v1.model.MergeInputSetResponseBody;
import io.harness.spec.server.pipeline.v1.model.MoveConfigOperationType;
import io.harness.spec.server.pipeline.v1.model.OverlayInputSetCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.OverlayInputSetResponseBody;
import io.harness.spec.server.pipeline.v1.model.OverlayInputSetUpdateRequestBody;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

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
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.support.PageableExecutionUtils;

@OwnedBy(PIPELINE)
@PrepareForTest({InputSetValidationHelper.class})
public class InputSetsApiImplTest extends PipelineServiceTestBase {
  InputSetsApiImpl inputSetsApiImpl;
  @Mock PMSInputSetService pmsInputSetService;
  @Mock InputSetsApiUtils inputSetsApiUtils;
  @Mock ValidateAndMergeHelper validateAndMergeHelper;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  @Mock InputSetResourcePMSImpl inputSetResourcePMSImpl;
  @Mock AccessControlClient accessControlClient;

  private static final String account = randomAlphabetic(10);
  private static final String org = randomAlphabetic(10);
  private static final String project = randomAlphabetic(10);
  private static final String pipeline = randomAlphabetic(10);
  private static String branch = randomAlphabetic(10);
  private static String repo = randomAlphabetic(10);
  private static String connectorRef = randomAlphabetic(10);
  private static final String inputSet = "input1";
  private static final String inputSetName = "this name";
  private static final String parentUniqueId = randomAlphabetic(10);
  private static final ScopeInfo scopeInfo = ScopeInfo.builder()
                                                 .accountIdentifier(account)
                                                 .orgIdentifier(org)
                                                 .projectIdentifier(project)
                                                 .uniqueId(parentUniqueId)
                                                 .build();
  private String inputSetYaml;
  private String pipelineYaml;
  private String overlayInputSetYaml;
  InputSetEntity inputSetEntity;
  PipelineEntity pipelineEntity;
  InputSetResponseBody inputSetResponseBody;
  OverlayInputSetResponseDTOPMS overlayInputSetResponseDTOPMS;
  OverlayInputSetResponseBody overlayInputSetResponseBody;
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
    inputSetsApiImpl =
        new InputSetsApiImpl(pmsInputSetService, inputSetsApiUtils, validateAndMergeHelper, pmsFeatureFlagService,
            scopeResolutionHelper, pipelineSplitPermissionsHelper, inputSetResourcePMSImpl, accessControlClient);

    String inputSetFilename = "inputSet1.yml";
    inputSetYaml = readFile(inputSetFilename);
    String pipelineYamlFileName = "pipeline.yml";
    pipelineYaml = readFile(pipelineYamlFileName);
    overlayInputSetYaml = readFile("overlay1.yml");

    inputSetEntity = InputSetEntity.builder()
                         .accountId(account)
                         .orgIdentifier(org)
                         .projectIdentifier(project)
                         .pipelineIdentifier(pipeline)
                         .identifier(inputSet)
                         .name(inputSet)
                         .yaml(inputSetYaml)
                         .inputSetEntityType(InputSetEntityType.INPUT_SET)
                         .build();

    inputSetResponseBody = new InputSetResponseBody();
    inputSetResponseBody.setIdentifier(inputSet);
    inputSetResponseBody.setName(inputSetName);
    inputSetResponseBody.setInputSetYaml(inputSetYaml);
    inputSetResponseBody.setOrg(org);
    inputSetResponseBody.setProject(project);

    overlayInputSetResponseDTOPMS = OverlayInputSetResponseDTOPMS.builder()
                                        .accountId(account)
                                        .orgIdentifier(org)
                                        .projectIdentifier(project)
                                        .pipelineIdentifier(pipeline)
                                        .identifier("overlay1")
                                        .name("thisName")
                                        .overlayInputSetYaml(overlayInputSetYaml)
                                        .build();
    overlayInputSetResponseBody = new OverlayInputSetResponseBody();
    overlayInputSetResponseBody.setIdentifier("overlay1");
    overlayInputSetResponseBody.setName("thisName");
    overlayInputSetResponseBody.setOverlayInputSetYaml(overlayInputSetYaml);

    pipelineEntity = PipelineEntity.builder()
                         .accountId(account)
                         .orgIdentifier(org)
                         .projectIdentifier(project)
                         .identifier(pipeline)
                         .yaml(pipelineYaml)
                         .version(1L)
                         .build();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testCreateInputSet() {
    doReturn(inputSetEntity).when(pmsInputSetService).create(any(), anyBoolean(), any());
    doReturn(inputSetResponseBody).when(inputSetsApiUtils).getInputSetResponse(any(), eq(false), any(), anyBoolean());
    InputSetCreateRequestBody inputSetCreateRequestBody = new InputSetCreateRequestBody();
    inputSetCreateRequestBody.setIdentifier(inputSet);
    inputSetCreateRequestBody.setName(inputSetName);
    inputSetCreateRequestBody.setInputSetYaml(inputSetYaml);
    Response response = inputSetsApiImpl.createInputSet(inputSetCreateRequestBody, pipeline, org, project, account);
    InputSetResponseBody responseBody = (InputSetResponseBody) response.getEntity();
    assertEquals(responseBody.getInputSetYaml(), inputSetYaml);
    assertEquals(responseBody.getName(), inputSetName);
    assertEquals(responseBody.getIdentifier(), inputSet);
    assertEquals(responseBody.getOrg(), org);
    assertEquals(responseBody.getProject(), project);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testCreateInlineHCInputSet() {
    InputSetEntity entity = inputSetEntity.withStoreType(StoreType.INLINE_HC);
    doReturn(entity.withVersion(1L)).when(pmsInputSetService).create(any(), anyBoolean(), any());
    doReturn(inputSetResponseBody).when(inputSetsApiUtils).getInputSetResponse(any(), eq(false), any(), anyBoolean());
    InputSetCreateRequestBody inputSetCreateRequestBody = new InputSetCreateRequestBody();
    inputSetCreateRequestBody.setIdentifier(inputSet);
    inputSetCreateRequestBody.setName(inputSetName);
    inputSetCreateRequestBody.setInputSetYaml(inputSetYaml);
    Response response = inputSetsApiImpl.createInputSet(inputSetCreateRequestBody, pipeline, org, project, account);
    InputSetResponseBody responseBody = (InputSetResponseBody) response.getEntity();
    assertEquals(responseBody.getInputSetYaml(), inputSetYaml);
    assertEquals(responseBody.getName(), inputSetName);
    assertEquals(responseBody.getIdentifier(), inputSet);
    assertEquals(responseBody.getOrg(), org);
    assertEquals(responseBody.getProject(), project);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testDeleteInputSet() {
    doReturn(true).when(pmsInputSetService).delete(null, pipeline, inputSet, null, true);
    Response deleteResponse = inputSetsApiImpl.deleteInputSet(org, project, inputSet, pipeline, account);
    assertThat(deleteResponse.getStatus()).isEqualTo(204);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetInputSet() {
    doReturn(Optional.of(inputSetEntity))
        .when(pmsInputSetService)
        .get(scopeInfo, pipeline, inputSet, false, null, null, true, false, false, true);
    doReturn(inputSetResponseBody).when(inputSetsApiUtils).getInputSetResponse(any(), eq(false), any(), anyBoolean());
    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(any(), any(), any());
    InputSetCreateRequestBody inputSetCreateRequestBody = new InputSetCreateRequestBody();
    inputSetCreateRequestBody.setIdentifier(inputSet);
    inputSetCreateRequestBody.setInputSetYaml(inputSetYaml);

    Response response =
        inputSetsApiImpl.getInputSet(org, project, inputSet, pipeline, account, null, null, null, false, null, "false");
    InputSetResponseBody responseBody = (InputSetResponseBody) response.getEntity();
    assertEquals(responseBody.getInputSetYaml(), inputSetYaml);
    assertEquals(responseBody.getName(), inputSetName);
    assertEquals(responseBody.getIdentifier(), inputSet);
    assertEquals(responseBody.getOrg(), org);
    assertEquals(responseBody.getProject(), project);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetInputSetWithCaching() {
    doReturn(Optional.of(inputSetEntity))
        .when(pmsInputSetService)
        .get(scopeInfo, pipeline, inputSet, false, null, null, true, false, true, true);
    doReturn(inputSetResponseBody).when(inputSetsApiUtils).getInputSetResponse(any(), eq(false), any(), anyBoolean());
    InputSetCreateRequestBody inputSetCreateRequestBody = new InputSetCreateRequestBody();
    inputSetCreateRequestBody.setIdentifier(inputSet);
    inputSetCreateRequestBody.setInputSetYaml(inputSetYaml);
    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(any(), any(), any());
    Response response =
        inputSetsApiImpl.getInputSet(org, project, inputSet, pipeline, account, null, null, null, false, null, "true");
    InputSetResponseBody responseBody = (InputSetResponseBody) response.getEntity();
    assertEquals(responseBody.getInputSetYaml(), inputSetYaml);
    assertEquals(responseBody.getName(), inputSetName);
    assertEquals(responseBody.getIdentifier(), inputSet);
    assertEquals(responseBody.getOrg(), org);
    assertEquals(responseBody.getProject(), project);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testUpdateInputSet() {
    doReturn(inputSetEntity).when(pmsInputSetService).update(any(), any(), anyBoolean(), any());
    doReturn(inputSetResponseBody).when(inputSetsApiUtils).getInputSetResponse(any(), eq(false), any(), anyBoolean());
    InputSetUpdateRequestBody inputSetUpdateRequestBody = new InputSetUpdateRequestBody();
    inputSetUpdateRequestBody.setIdentifier(inputSet);
    inputSetUpdateRequestBody.setName(inputSetName);
    inputSetUpdateRequestBody.setInputSetYaml(inputSetYaml);

    Response response =
        inputSetsApiImpl.updateInputSet(inputSetUpdateRequestBody, pipeline, org, project, inputSet, account);
    InputSetResponseBody responseBody = (InputSetResponseBody) response.getEntity();
    assertEquals(responseBody.getInputSetYaml(), inputSetYaml);
    assertEquals(responseBody.getName(), inputSetName);
    assertEquals(responseBody.getOrg(), org);
    assertEquals(responseBody.getProject(), project);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testMerge() {
    when(validateAndMergeHelper.getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(
             any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenReturn("yaml");
    MergeInputSetRequestBody mergeInputSetRequestBody = new MergeInputSetRequestBody();
    mergeInputSetRequestBody.setInputSetReferences(Arrays.asList("ip1"));
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
    Response response = inputSetsApiImpl.mergedInputSets(
        pipeline, org, project, mergeInputSetRequestBody, account, "false", null, null, null, null, null, null);
    MergeInputSetResponseBody responseBody = (MergeInputSetResponseBody) response.getEntity();
    assertEquals(responseBody.getInputsYamlMerged(), "yaml");
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testListInputSets() {
    doReturn(PageableExecutionUtils.getPage(Collections.singletonList(inputSetEntity),
                 PageRequest.of(0, 10, Sort.by(Direction.DESC, InputSetEntityKeys.createdAt)), () -> 1L))
        .when(pmsInputSetService)
        .list(any(), any(), eq(scopeInfo));
    doReturn(inputSetResponseBody).when(inputSetsApiUtils).getInputSetResponse(any(), eq(true), any(), anyBoolean());
    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(any(), any(), any());
    Mockito.mockStatic(InputSetValidationHelper.class);

    Response response = inputSetsApiImpl.listInputSets(org, project, pipeline, account, 0, 10, null, null, null);
    List<InputSetResponseBody> content = (List<InputSetResponseBody>) response.getEntity();

    assertThat(content).isNotEmpty();
    assertThat(content.size()).isEqualTo(1);
    InputSetResponseBody responseBody = content.get(0);
    assertThat(responseBody.getIdentifier()).isEqualTo(inputSet);
    assertThat(responseBody.getName()).isEqualTo(inputSetName);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMoveConfig() {
    GitMoveDetails gitMoveDetails = new GitMoveDetails();
    gitMoveDetails.setBranchName(branch);
    gitMoveDetails.setRepoName(repo);
    gitMoveDetails.setConnectorRef(connectorRef);
    doReturn(false).when(pmsFeatureFlagService).isEnabled(account, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT);

    InputSetMoveConfigRequestBody inputSetMoveConfigRequestBody = new InputSetMoveConfigRequestBody();
    inputSetMoveConfigRequestBody.setGitDetails(gitMoveDetails);
    inputSetMoveConfigRequestBody.setMoveConfigOperationType(MoveConfigOperationType.INLINE_TO_REMOTE);
    inputSetMoveConfigRequestBody.setPipelineIdentifier(pipeline);
    inputSetMoveConfigRequestBody.setInputSetIdentifier(inputSet);

    doReturn(InputSetEntity.builder().identifier(inputSet).build())
        .when(pmsInputSetService)
        .moveConfig(any(), any(), any(), any(), any(), any());

    Response response =
        inputSetsApiImpl.inputSetsMoveConfig(org, project, inputSet, inputSetMoveConfigRequestBody, account);
    InputSetMoveConfigResponseBody responseBody = (InputSetMoveConfigResponseBody) response.getEntity();
    assertEquals(inputSet, responseBody.getInputSetIdentifier());
    verify(pipelineSplitPermissionsHelper, times(1))
        .checkForPipelineRBACSplitAccessPermissions(eq(account), eq(org), eq(project), eq(null), eq(false),
            eq(PipelineRbacPermissions.PIPELINE_EDIT),
            eq(Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE)));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testInputSetImportFlow() {
    GitImportInfo gitImportInfo = new GitImportInfo();
    gitImportInfo.isForceImport(false);
    InputSetImportRequestBody inputSetImportRequestBody = new InputSetImportRequestBody();
    inputSetImportRequestBody.setInputSetImportRequest(new InputSetImportRequestDTO());
    inputSetImportRequestBody.setGitImportInfo(gitImportInfo);
    doReturn(InputSetEntity.builder().identifier(inputSet).build())
        .when(pmsInputSetService)
        .importInputSetFromRemote(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    Response response =
        inputSetsApiImpl.importInputSetFromGit(pipeline, org, project, inputSet, inputSetImportRequestBody, account);
    InputSetMoveConfigResponseBody responseBody = (InputSetMoveConfigResponseBody) response.getEntity();
    assertEquals(inputSet, responseBody.getInputSetIdentifier());
    verify(pipelineSplitPermissionsHelper)
        .checkForPipelineRBACSplitAccessPermissions(account, org, project, pipeline, false,
            PipelineRbacPermissions.PIPELINE_EDIT,
            Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testInputSetImportFlowWithInputSetRbacEnabled() {
    when(pmsFeatureFlagService.isEnabled(account, FeatureName.PIE_INPUTSET_RBAC_PERMISSIONS)).thenReturn(true);
    GitImportInfo gitImportInfo = new GitImportInfo();
    gitImportInfo.isForceImport(false);
    InputSetImportRequestBody inputSetImportRequestBody = new InputSetImportRequestBody();
    inputSetImportRequestBody.setInputSetImportRequest(new InputSetImportRequestDTO());
    inputSetImportRequestBody.setGitImportInfo(gitImportInfo);
    doReturn(InputSetEntity.builder().identifier(inputSet).build())
        .when(pmsInputSetService)
        .importInputSetFromRemote(any(), any(), any(), any(), any(), any(), anyBoolean(), any());

    Response response =
        inputSetsApiImpl.importInputSetFromGit(pipeline, org, project, inputSet, inputSetImportRequestBody, account);

    InputSetMoveConfigResponseBody responseBody = (InputSetMoveConfigResponseBody) response.getEntity();
    assertEquals(inputSet, responseBody.getInputSetIdentifier());
    verify(accessControlClient)
        .checkForAccessOrThrow(any(), any(), eq(InputSetRbacPermissions.INPUTSET_CREATE_AND_EDIT));
    verify(pipelineSplitPermissionsHelper, times(0))
        .checkForPipelineRBACSplitAccessPermissions(any(), any(), any(), any(), anyBoolean(), any(), any());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateOverlayInputSetPassesVersionOneToResource() {
    OverlayInputSetCreateRequestBody requestBody = new OverlayInputSetCreateRequestBody();
    requestBody.setOverlayInputSetYaml(overlayInputSetYaml);
    requestBody.setVersion(HarnessYamlVersion.V1);
    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(account, org, project);
    doReturn(ResponseDTO.newResponse(overlayInputSetResponseDTOPMS))
        .when(inputSetResourcePMSImpl)
        .createOverlayInputSet(eq(account), eq(org), eq(project), eq(pipeline), any(GitEntityCreateInfoDTO.class),
            eq(HarnessYamlVersion.V1), eq(overlayInputSetYaml), eq(scopeInfo));
    doReturn(overlayInputSetResponseBody)
        .when(inputSetsApiUtils)
        .toOverlayInputSetResponseBody(overlayInputSetResponseDTOPMS);

    Response response = inputSetsApiImpl.createOverlayInputSet(requestBody, pipeline, org, project, account);

    assertThat(response.getStatus()).isEqualTo(201);
    verify(inputSetResourcePMSImpl)
        .createOverlayInputSet(eq(account), eq(org), eq(project), eq(pipeline), any(GitEntityCreateInfoDTO.class),
            eq(HarnessYamlVersion.V1), eq(overlayInputSetYaml), eq(scopeInfo));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateOverlayInputSetPassesV0WhenVersionOmitted() {
    OverlayInputSetCreateRequestBody requestBody = new OverlayInputSetCreateRequestBody();
    requestBody.setOverlayInputSetYaml(overlayInputSetYaml);
    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(account, org, project);
    doReturn(ResponseDTO.newResponse(overlayInputSetResponseDTOPMS))
        .when(inputSetResourcePMSImpl)
        .createOverlayInputSet(eq(account), eq(org), eq(project), eq(pipeline), any(GitEntityCreateInfoDTO.class),
            eq(HarnessYamlVersion.V0), eq(overlayInputSetYaml), eq(scopeInfo));
    doReturn(overlayInputSetResponseBody)
        .when(inputSetsApiUtils)
        .toOverlayInputSetResponseBody(overlayInputSetResponseDTOPMS);

    Response response = inputSetsApiImpl.createOverlayInputSet(requestBody, pipeline, org, project, account);

    assertThat(response.getStatus()).isEqualTo(201);
    verify(inputSetResourcePMSImpl)
        .createOverlayInputSet(eq(account), eq(org), eq(project), eq(pipeline), any(GitEntityCreateInfoDTO.class),
            eq(HarnessYamlVersion.V0), eq(overlayInputSetYaml), eq(scopeInfo));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testUpdateOverlayInputSetPassesVersionOneToResource() {
    OverlayInputSetUpdateRequestBody requestBody = new OverlayInputSetUpdateRequestBody();
    requestBody.setOverlayInputSetYaml(overlayInputSetYaml);
    requestBody.setVersion(HarnessYamlVersion.V1);
    doReturn(ResponseDTO.newResponse(overlayInputSetResponseDTOPMS))
        .when(inputSetResourcePMSImpl)
        .updateOverlayInputSet(isNull(), eq(inputSet), eq(account), eq(org), eq(project), eq(pipeline),
            any(GitEntityUpdateInfoDTO.class), eq(HarnessYamlVersion.V1), eq(overlayInputSetYaml));
    doReturn(overlayInputSetResponseBody)
        .when(inputSetsApiUtils)
        .toOverlayInputSetResponseBody(overlayInputSetResponseDTOPMS);

    Response response =
        inputSetsApiImpl.updateOverlayInputSet(requestBody, pipeline, org, project, inputSet, account, null);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(inputSetResourcePMSImpl)
        .updateOverlayInputSet(isNull(), eq(inputSet), eq(account), eq(org), eq(project), eq(pipeline),
            any(GitEntityUpdateInfoDTO.class), eq(HarnessYamlVersion.V1), eq(overlayInputSetYaml));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testUpdateOverlayInputSetPassesV0WhenVersionOmitted() {
    OverlayInputSetUpdateRequestBody requestBody = new OverlayInputSetUpdateRequestBody();
    requestBody.setOverlayInputSetYaml(overlayInputSetYaml);
    doReturn(ResponseDTO.newResponse(overlayInputSetResponseDTOPMS))
        .when(inputSetResourcePMSImpl)
        .updateOverlayInputSet(isNull(), eq(inputSet), eq(account), eq(org), eq(project), eq(pipeline),
            any(GitEntityUpdateInfoDTO.class), eq(HarnessYamlVersion.V0), eq(overlayInputSetYaml));
    doReturn(overlayInputSetResponseBody)
        .when(inputSetsApiUtils)
        .toOverlayInputSetResponseBody(overlayInputSetResponseDTOPMS);

    Response response =
        inputSetsApiImpl.updateOverlayInputSet(requestBody, pipeline, org, project, inputSet, account, null);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(inputSetResourcePMSImpl)
        .updateOverlayInputSet(isNull(), eq(inputSet), eq(account), eq(org), eq(project), eq(pipeline),
            any(GitEntityUpdateInfoDTO.class), eq(HarnessYamlVersion.V0), eq(overlayInputSetYaml));
  }
}

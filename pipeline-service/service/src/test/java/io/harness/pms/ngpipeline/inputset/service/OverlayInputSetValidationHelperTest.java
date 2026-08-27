/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType.INPUT_SET;
import static io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType.OVERLAY_INPUT_SET;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.manage.GlobalContextManager;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidOverlayInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetErrorsHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.springframework.data.mongodb.core.query.Criteria;

@PrepareForTest({InputSetErrorsHelper.class, PMSInputSetFilterHelper.class})
@OwnedBy(PIPELINE)
public class OverlayInputSetValidationHelperTest extends CategoryTest {
  @Mock PMSInputSetService inputSetService;
  @Mock GitSyncSdkService gitSyncSdkService;
  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pipelineId = "Test_Pipline11";
  String parentUniqueId = "parentUniqueId";
  ScopeInfo scopeInfo = ScopeInfo.builder()
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgId)
                            .projectIdentifier(projectId)
                            .scopeType(ScopeLevel.PROJECT)
                            .uniqueId(parentUniqueId)
                            .build();
  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSetWithNoReferences() {
    String overlayInputSetYamlWithoutReferences = getOverlayInputSetWithAllIds(false);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayInputSetYamlWithoutReferences)
                                        .inputSetEntityType(OVERLAY_INPUT_SET)
                                        .build();
    assertThatThrownBy(
        () -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity, false, null))
        .hasMessage("Input Set References can't be empty");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSetWithoutOrgID() {
    String overlayInputSetYamlWithoutOrgId = getOverlayInputSetYaml(true, false, true, true, false);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayInputSetYamlWithoutOrgId)
                                        .inputSetEntityType(OVERLAY_INPUT_SET)
                                        .build();
    assertThatThrownBy(
        () -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity, false, null))
        .hasMessage("Organization identifier is missing in the YAML. Please give a valid Organization identifier");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSetWithoutProjectID() {
    String overlayInputSetYamlWithoutProjectId = getOverlayInputSetYaml(true, true, false, true, false);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayInputSetYamlWithoutProjectId)
                                        .inputSetEntityType(OVERLAY_INPUT_SET)
                                        .build();
    assertThatThrownBy(
        () -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity, false, null))
        .hasMessage("Project identifier is missing in the YAML. Please give a valid Project identifier");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSetWithoutPipelineID() {
    String overlayInputSetYamlWithoutPipelineId = getOverlayInputSetYaml(true, true, true, false, false);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayInputSetYamlWithoutPipelineId)
                                        .inputSetEntityType(OVERLAY_INPUT_SET)
                                        .build();
    assertThatThrownBy(
        () -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity, false, null))
        .hasMessage("Pipeline identifier is missing in the YAML. Please give a valid Pipeline identifier");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSet() {
    String inputSetFile1 = "inputset1-with-org-proj-id.yaml";
    String inputSetYaml1 = readFile(inputSetFile1);
    String identifier1 = "input1";
    InputSetEntity inputSetEntity1 = InputSetEntity.builder().inputSetEntityType(INPUT_SET).yaml(inputSetYaml1).build();
    doReturn(Optional.of(inputSetEntity1))
        .when(inputSetService)
        .getMetadataWithoutValidations(
            accountId, orgId, projectId, pipelineId, identifier1, false, false, true, null, false);

    String inputSetFile2 = "inputSetWrong1.yml";
    String inputSetYaml2 = readFile(inputSetFile2);
    String identifier2 = "thisInputSetIsWrong";
    InputSetEntity inputSetEntity2 = InputSetEntity.builder().inputSetEntityType(INPUT_SET).yaml(inputSetYaml2).build();
    doReturn(Optional.of(inputSetEntity2))
        .when(inputSetService)
        .getMetadataWithoutValidations(
            accountId, orgId, projectId, pipelineId, identifier2, false, false, true, null, false);

    String overlayInputSetYaml = getOverlayInputSetWithAllIds(true);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayInputSetYaml)
                                        .inputSetEntityType(OVERLAY_INPUT_SET)
                                        .build();
    MockedStatic<InputSetErrorsHelper> mockSettings = Mockito.mockStatic(InputSetErrorsHelper.class);
    when(InputSetErrorsHelper.getInvalidInputSetReferences(any(), any(), any())).thenCallRealMethod();
    when(InputSetErrorsHelper.getErrorMap(any(), any(), any())).thenReturn(null);
    OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity, false, null);
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateNonExistentReferencesInOverlayInputSet() {
    String nonExistentReference = "doesNotExist";
    String overlayYaml = getOverlayInputSetWithNonExistentReference();
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayYaml)
                                        .inputSetEntityType(OVERLAY_INPUT_SET)
                                        .build();
    doReturn(Optional.empty())
        .when(inputSetService)
        .getMetadataWithoutValidations(
            accountId, orgId, projectId, pipelineId, nonExistentReference, false, false, true, null, false);
    assertThatThrownBy(
        () -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity, false, null))
        .isInstanceOf(InvalidOverlayInputSetException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateEmptyReferencesInOverlayInputSet() {
    String emptyReferencesOverlay = "overlayInputSet:\n"
        + "  identifier: a\n"
        + "  orgIdentifier: orgId\n"
        + "  projectIdentifier: projectId\n"
        + "  pipelineIdentifier: Test_Pipline11\n"
        + "  inputSetReferences:\n"
        + "    - \"\"\n"
        + "    - \"\"";

    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(emptyReferencesOverlay)
                                        .inputSetEntityType(OVERLAY_INPUT_SET)
                                        .build();

    assertThatThrownBy(
        () -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity, false, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty Input Set Identifier not allowed in Input Set References");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testForLengthCheckOnOverlayInputSetIdentifiers() {
    String yaml = "overlayInputSet:\n"
        + "  identifier: "
        + "abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijdsalksdajsdnanfnoaniondna12213123034"
        + "r78978987879897jkkljasad";
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(yaml)
                                        .inputSetEntityType(OVERLAY_INPUT_SET)
                                        .build();
    assertThatThrownBy(() -> OverlayInputSetValidationHelper.validateOverlayInputSet(null, inputSetEntity, false, null))
        .hasMessage("Overlay Input Set identifier length cannot be more that 127 characters.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffWithOneInvalidReference() {
    String yaml = getOverlayInputSetWithAllIds(true);
    doReturn(Optional.of(InputSetEntity.builder().inputSetEntityType(INPUT_SET).isInvalid(false).build()))
        .when(inputSetService)
        .getMetadataWithoutValidations(
            accountId, orgId, projectId, pipelineId, "input1", false, false, true, null, false);
    doReturn(Optional.empty())
        .when(inputSetService)
        .getMetadataWithoutValidations(
            accountId, orgId, projectId, pipelineId, "thisInputSetIsWrong", false, false, true, null, false);

    MockedStatic<InputSetErrorsHelper> mockSettings = Mockito.mockStatic(InputSetErrorsHelper.class);
    when(InputSetErrorsHelper.getInvalidInputSetReferences(any(), any(), any())).thenCallRealMethod();
    when(InputSetErrorsHelper.getErrorMap("randomPipelineYaml", null, null)).thenReturn(null);
    InputSetYamlDiffDTO yamlDiffForOverlayInputSet =
        OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(null, inputSetService,
            InputSetEntity.builder()
                .accountId(accountId)
                .orgIdentifier(orgId)
                .projectIdentifier(projectId)
                .pipelineIdentifier(pipelineId)
                .yaml(yaml)
                .inputSetEntityType(OVERLAY_INPUT_SET)
                .build(),
            "randomPipelineYaml", null, false);
    assertThat(yamlDiffForOverlayInputSet.getOldYAML()).isEqualTo(yaml);
    List<String> newReferences =
        InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(yamlDiffForOverlayInputSet.getNewYAML());
    assertThat(newReferences).containsExactly("input1");
    assertThat(yamlDiffForOverlayInputSet.isInputSetEmpty()).isFalse();
    assertThat(yamlDiffForOverlayInputSet.isNoUpdatePossible()).isFalse();
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffWithAllInvalidReferences() {
    String yaml = getOverlayInputSetWithAllIds(true);
    doReturn(Optional.empty())
        .when(inputSetService)
        .getMetadataWithoutValidations(
            accountId, orgId, projectId, pipelineId, "input1", false, false, true, null, false);
    doReturn(Optional.empty())
        .when(inputSetService)
        .getMetadataWithoutValidations(
            accountId, orgId, projectId, pipelineId, "thisInputSetIsWrong", false, false, true, null, false);
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    doReturn(Collections.emptyList()).when(inputSetService).list(any());
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(yaml)
                                        .inputSetEntityType(OVERLAY_INPUT_SET)
                                        .build();

    InputSetYamlDiffDTO yamlDiffForOverlayInputSet = OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(
        gitSyncSdkService, inputSetService, inputSetEntity, null, null, false);
    assertThat(yamlDiffForOverlayInputSet.isInputSetEmpty()).isTrue();
    assertThat(yamlDiffForOverlayInputSet.isNoUpdatePossible()).isTrue();

    doReturn(Collections.singletonList(inputSetEntity)).when(inputSetService).list(any());
    yamlDiffForOverlayInputSet = OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(
        gitSyncSdkService, inputSetService, inputSetEntity, null, null, false);
    assertThat(yamlDiffForOverlayInputSet.isInputSetEmpty()).isTrue();
    assertThat(yamlDiffForOverlayInputSet.isNoUpdatePossible()).isFalse();

    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    setupGitContext(GitEntityInfo.builder().branch("random").yamlGitConfigId("random").build());
    yamlDiffForOverlayInputSet = OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(
        gitSyncSdkService, inputSetService, inputSetEntity, null, null, false);
    assertThat(yamlDiffForOverlayInputSet.isInputSetEmpty()).isTrue();
    assertThat(yamlDiffForOverlayInputSet.isNoUpdatePossible()).isFalse();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSetsForGivenInputSet() throws Exception {
    MockedStatic<PMSInputSetFilterHelper> mockSettings = Mockito.mockStatic(PMSInputSetFilterHelper.class);
    Criteria dummyCriteria = Criteria.where("myKey").is("myValue");
    when(PMSInputSetFilterHelper.createCriteriaForGetListForBranchAndRepo(accountId, orgId, projectId, pipelineId,
             InputSetListTypePMS.OVERLAY_INPUT_SET, scopeInfo.getUniqueId(), false))
        .thenReturn(dummyCriteria);
    String yaml1 = "overlayInputSet:\n"
        + "  inputSetReferences:\n"
        + "  - i1\n";
    InputSetEntity overlay1 = InputSetEntity.builder().yaml(yaml1).isInvalid(true).build();
    String yaml2 = "overlayInputSet:\n"
        + "  inputSetReferences:\n"
        + "  - i2\n";
    InputSetEntity overlay2 = InputSetEntity.builder().yaml(yaml2).isInvalid(true).build();
    String yaml3 = "overlayInputSet:\n"
        + "  inputSetReferences:\n"
        + "  - i3\n";
    InputSetEntity overlay3 = InputSetEntity.builder().yaml(yaml3).isInvalid(false).build();
    doReturn(Arrays.asList(overlay1, overlay2, overlay3)).when(inputSetService).list(dummyCriteria);

    InputSetEntity updatedInputSet = InputSetEntity.builder()
                                         .accountId(accountId)
                                         .orgIdentifier(orgId)
                                         .projectIdentifier(projectId)
                                         .pipelineIdentifier(pipelineId)
                                         .identifier("i1")
                                         .parentUniqueId(parentUniqueId)
                                         .storeType(StoreType.INLINE)
                                         .inputSetEntityType(INPUT_SET)
                                         .build();
    doReturn(Optional.of(updatedInputSet))
        .when(inputSetService)
        .getMetadataWithoutValidations(
            accountId, orgId, projectId, pipelineId, "i1", false, false, true, scopeInfo, false);
    OverlayInputSetValidationHelper.validateOverlayInputSetsForGivenInputSet(
        inputSetService, updatedInputSet, scopeInfo, false);
    verify(inputSetService, times(1)).switchValidationFlag(overlay1, false, false);
    verify(inputSetService, times(0)).switchValidationFlag(overlay2, false, false);
    verify(inputSetService, times(0)).switchValidationFlag(overlay3, false, false);

    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testInvalidateOverlayInputSetsReferringDeletedInputSet() throws Exception {
    MockedStatic<PMSInputSetFilterHelper> mockSettings = Mockito.mockStatic(PMSInputSetFilterHelper.class);
    Criteria dummyCriteria = Criteria.where("myKey").is("myValue");
    when(PMSInputSetFilterHelper.createCriteriaForGetListForBranchAndRepo(
             accountId, orgId, projectId, pipelineId, InputSetListTypePMS.OVERLAY_INPUT_SET, null, false))
        .thenReturn(dummyCriteria);

    String yaml1 = "overlayInputSet:\n"
        + "  inputSetReferences:\n"
        + "  - i1\n"
        + "  - i2\n";
    InputSetEntity overlay1 = InputSetEntity.builder().yaml(yaml1).isInvalid(false).build();
    String yaml2 = "overlayInputSet:\n"
        + "  inputSetReferences:\n"
        + "  - i2\n"
        + "  - i3\n";
    InputSetEntity overlay2 = InputSetEntity.builder().yaml(yaml2).isInvalid(false).build();
    InputSetEntity overlay3 = InputSetEntity.builder().yaml(yaml2).isInvalid(true).build();
    doReturn(Arrays.asList(overlay1, overlay2, overlay3)).when(inputSetService).list(dummyCriteria);
    InputSetEntity deletedInputSet = InputSetEntity.builder()
                                         .accountId(accountId)
                                         .orgIdentifier(orgId)
                                         .projectIdentifier(projectId)
                                         .pipelineIdentifier(pipelineId)
                                         .identifier("i1")
                                         .storeType(StoreType.INLINE)
                                         .inputSetEntityType(INPUT_SET)
                                         .build();

    OverlayInputSetValidationHelper.invalidateOverlayInputSetsReferringDeletedInputSet(
        inputSetService, deletedInputSet, null, false);
    verify(inputSetService, times(1)).switchValidationFlag(overlay1, true, false);
    verify(inputSetService, times(0)).switchValidationFlag(overlay2, true, false);
    verify(inputSetService, times(0)).switchValidationFlag(overlay3, true, false);

    OverlayInputSetValidationHelper.invalidateOverlayInputSetsReferringDeletedInputSet(
        inputSetService, overlay1, scopeInfo, false);
    verify(inputSetService, times(1)).switchValidationFlag(any(), anyBoolean(), anyBoolean());
    mockSettings.close();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testInvalidateOverlayInputSetsReferringDeletedInputSetWithV1Overlay() throws Exception {
    MockedStatic<PMSInputSetFilterHelper> mockSettings = Mockito.mockStatic(PMSInputSetFilterHelper.class);
    Criteria dummyCriteria = Criteria.where("myKey").is("myValue");
    when(PMSInputSetFilterHelper.createCriteriaForGetListForBranchAndRepo(
             accountId, orgId, projectId, pipelineId, InputSetListTypePMS.OVERLAY_INPUT_SET, null, false))
        .thenReturn(dummyCriteria);

    String v0Yaml = "overlayInputSet:\n"
        + "  inputSetReferences:\n"
        + "  - i1\n"
        + "  - i2\n";
    InputSetEntity overlayV0 =
        InputSetEntity.builder().yaml(v0Yaml).isInvalid(false).harnessVersion(HarnessYamlVersion.V0).build();
    String v1Yaml = "version: 1\n"
        + "name: overlayV1\n"
        + "spec:\n"
        + "  input_sets:\n"
        + "    - i1\n"
        + "    - i3\n";
    InputSetEntity overlayV1 = InputSetEntity.builder()
                                   .yaml(v1Yaml)
                                   .isInvalid(false)
                                   .harnessVersion(HarnessYamlVersion.V1)
                                   .storeType(StoreType.INLINE)
                                   .build();
    String v1UnrelatedYaml = "version: 1\n"
        + "name: overlayV1Other\n"
        + "spec:\n"
        + "  input_sets:\n"
        + "    - i2\n"
        + "    - i3\n";
    InputSetEntity overlayV1Unrelated = InputSetEntity.builder()
                                            .yaml(v1UnrelatedYaml)
                                            .isInvalid(false)
                                            .harnessVersion(HarnessYamlVersion.V1)
                                            .storeType(StoreType.INLINE)
                                            .build();
    doReturn(Arrays.asList(overlayV0, overlayV1, overlayV1Unrelated)).when(inputSetService).list(dummyCriteria);
    InputSetEntity deletedInputSet = InputSetEntity.builder()
                                         .accountId(accountId)
                                         .orgIdentifier(orgId)
                                         .projectIdentifier(projectId)
                                         .pipelineIdentifier(pipelineId)
                                         .identifier("i1")
                                         .storeType(StoreType.INLINE)
                                         .inputSetEntityType(INPUT_SET)
                                         .build();

    OverlayInputSetValidationHelper.invalidateOverlayInputSetsReferringDeletedInputSet(
        inputSetService, deletedInputSet, null, false);
    verify(inputSetService, times(1)).switchValidationFlag(overlayV0, true, false);
    verify(inputSetService, times(1)).switchValidationFlag(overlayV1, true, false);
    verify(inputSetService, times(0)).switchValidationFlag(overlayV1Unrelated, true, false);
    mockSettings.close();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSetsForGivenInputSetWithV1Overlay() throws Exception {
    MockedStatic<PMSInputSetFilterHelper> mockSettings = Mockito.mockStatic(PMSInputSetFilterHelper.class);
    Criteria dummyCriteria = Criteria.where("myKey").is("myValue");
    when(PMSInputSetFilterHelper.createCriteriaForGetListForBranchAndRepo(accountId, orgId, projectId, pipelineId,
             InputSetListTypePMS.OVERLAY_INPUT_SET, scopeInfo.getUniqueId(), false))
        .thenReturn(dummyCriteria);

    String v1Yaml = "version: 1\n"
        + "name: overlayV1\n"
        + "spec:\n"
        + "  input_sets:\n"
        + "    - i1\n";
    InputSetEntity overlayV1 = InputSetEntity.builder()
                                   .yaml(v1Yaml)
                                   .isInvalid(true)
                                   .harnessVersion(HarnessYamlVersion.V1)
                                   .storeType(StoreType.INLINE)
                                   .build();
    doReturn(Collections.singletonList(overlayV1)).when(inputSetService).list(dummyCriteria);

    InputSetEntity updatedInputSet = InputSetEntity.builder()
                                         .accountId(accountId)
                                         .orgIdentifier(orgId)
                                         .projectIdentifier(projectId)
                                         .pipelineIdentifier(pipelineId)
                                         .identifier("i1")
                                         .storeType(StoreType.INLINE)
                                         .inputSetEntityType(INPUT_SET)
                                         .build();
    doReturn(Optional.of(updatedInputSet))
        .when(inputSetService)
        .getMetadataWithoutValidations(
            accountId, orgId, projectId, pipelineId, "i1", false, false, true, scopeInfo, false);

    OverlayInputSetValidationHelper.validateOverlayInputSetsForGivenInputSet(
        inputSetService, updatedInputSet, scopeInfo, false);
    verify(inputSetService, times(1)).switchValidationFlag(overlayV1, false, false);
    mockSettings.close();
  }

  private String getOverlayInputSetWithNonExistentReference() {
    return getOverlayInputSetYaml(true, true, true, true, true);
  }

  private String getOverlayInputSetWithAllIds(boolean hasReferences) {
    return getOverlayInputSetYaml(hasReferences, true, true, true, false);
  }

  private String getOverlayInputSetYaml(
      boolean hasReferences, boolean hasOrg, boolean hasProj, boolean hasPipeline, boolean nonExistentReference) {
    String base = "overlayInputSet:\n"
        + "  identifier: overlay1\n"
        + "  name : thisName\n";
    String orgId = "  orgIdentifier: orgId\n";
    String projectId = "  projectIdentifier: projectId\n";
    String pipelineId = "  pipelineIdentifier: Test_Pipline11\n";
    String references = "  inputSetReferences:\n"
        + (nonExistentReference ? "    - doesNotExist"
                                : ("    - input1\n"
                                      + "    - thisInputSetIsWrong"));
    String noReferences = "  inputSetReferences: []\n";

    return base + (hasOrg ? orgId : "") + (hasProj ? projectId : "") + (hasPipeline ? pipelineId : "")
        + (hasReferences ? references : noReferences);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }
}

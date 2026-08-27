/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers.validate;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.BHUMIJ;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.OM;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SANDESH_SALUNKHE;
import static io.harness.rule.OwnerRule.VED;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.manage.GlobalContextManager;
import io.harness.pms.ngpipeline.inputset.api.utils.InputSetsApiUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetSanitizer;
import io.harness.pms.ngpipeline.inputset.service.OverlayInputSetValidationHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

@PrepareForTest({OverlayInputSetValidationHelper.class, InputSetSanitizer.class})
@OwnedBy(PIPELINE)
public class InputSetValidationHelperTest extends CategoryTest {
  @Mock PMSInputSetService inputSetService;
  @Mock PMSPipelineService pipelineService;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock InputSetsApiUtils inputSetsApiUtils;
  @Mock ValidateAndMergeHelper validateAndMergeHelper;

  String identifier = "inputSetId";
  String invalidIdentifier = "\\{example";
  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pipelineId = "Test_Pipline11";
  String parentUniqueId = "parent_unique_id";
  ScopeInfo scopeInfo = ScopeInfo.builder()
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgId)
                            .projectIdentifier(projectId)
                            .uniqueId(parentUniqueId)
                            .build();
  String pipelineYaml;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    String pipelineFile = "pipeline-extensive.yml";
    pipelineYaml = readFile(pipelineFile);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetForInvalidStoreType() {
    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).build());

    assertThatThrownBy(()
                           -> InputSetValidationHelper.checkForPipelineStoreType(
                               PipelineEntity.builder().storeType(StoreType.INLINE).build(), true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input Set storeType: REMOTE does not match with Pipeline storeType: INLINE. Input Set and "
            + "Pipeline both must have same storeType");
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testValidateInputSetForInlineHCStoreTypeForPipelineWithInlineStoreType() {
    setupGitContext(GitEntityInfo.builder().storeType(StoreType.INLINE_HC).build());

    assertThatThrownBy(()
                           -> InputSetValidationHelper.checkForPipelineStoreType(
                               PipelineEntity.builder().storeType(StoreType.INLINE).build(), true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input Set storeType: INLINE_HC does not match with Pipeline storeType: INLINE. Input Set and "
            + "Pipeline both must have same storeType");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCheckForPipelineStoreTypeWithDefaultToInlineDisabledForNullStoreType() {
    // Test case 2: When storeType is null, it should be defaulted to INLINE when flag is false
    setupGitContext(GitEntityInfo.builder().storeType(null).build());

    // Should not throw exception as null is converted to INLINE and pipeline is also INLINE
    InputSetValidationHelper.checkForPipelineStoreType(
        PipelineEntity.builder().storeType(StoreType.INLINE).build(), false);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCheckForPipelineStoreTypeWithDefaultToInlineDisabledForRemoteStoreType() {
    // Test case 3: When storeType is REMOTE, it should NOT be defaulted to INLINE
    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).build());

    // Should throw exception as REMOTE is not converted and pipeline is INLINE
    assertThatThrownBy(()
                           -> InputSetValidationHelper.checkForPipelineStoreType(
                               PipelineEntity.builder().storeType(StoreType.INLINE).build(), false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input Set storeType: REMOTE does not match with Pipeline storeType: INLINE. Input Set and "
            + "Pipeline both must have same storeType");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCheckForPipelineStoreTypeWithDefaultToInlineDisabledForInlineStoreType() {
    // Test case 4: When storeType is INLINE, it should match with INLINE pipeline
    setupGitContext(GitEntityInfo.builder().storeType(StoreType.INLINE).build());

    // Should not throw exception as INLINE matches INLINE
    InputSetValidationHelper.checkForPipelineStoreType(
        PipelineEntity.builder().storeType(StoreType.INLINE).build(), false);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCheckForPipelineStoreTypeWithDefaultToInlineDisabledForRemotePipeline() {
    // Test case 5: When pipeline is REMOTE and input set context is also REMOTE, should match
    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).build());

    // Should not throw exception as REMOTE matches REMOTE
    InputSetValidationHelper.checkForPipelineStoreType(
        PipelineEntity.builder().storeType(StoreType.REMOTE).build(), false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetWithoutIdentifier() {
    doReturn(Optional.of(PipelineEntity.builder().storeType(StoreType.INLINE).build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, false);
    String yaml = "inputSet:\n"
        + "  name: abc";
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(yaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .build();
    assertThatThrownBy(() -> InputSetValidationHelper.validateInputSet(null, inputSetEntity, false, false, false, null))
        .hasMessage("Identifier cannot be empty");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testForLengthCheckOnInputSetIdentifiers() {
    doReturn(Optional.of(PipelineEntity.builder().storeType(StoreType.INLINE).build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, false);
    String yaml = "inputSet:\n"
        + "  identifier: "
        + "abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijhijdsalksdajsdnanfnoaniondna12213123"
        + "034r78978987879897jkklsa";
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(yaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .build();
    assertThatThrownBy(() -> InputSetValidationHelper.validateInputSet(null, inputSetEntity, false, false, false, null))
        .hasMessage("Input Set identifier length cannot be more that 127 characters.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetWithNoOrgAndProjectID() {
    doReturn(Optional.of(PipelineEntity.builder().storeType(StoreType.INLINE).build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, false);

    String inputSetFileWithNoProjOrOrg = "inputSet1.yml";
    String inputSetYamlWithNoProjOrOrg = readFile(inputSetFileWithNoProjOrOrg);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYamlWithNoProjOrOrg)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .build();
    assertThatThrownBy(
        () -> InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, false, false, false, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Organization identifier is missing in the YAML. Please give a valid Organization identifier");

    String inputSetFileWithNoProj = "inputset1-with-org-id.yaml";
    String inputSetYamlWithNoProj = readFile(inputSetFileWithNoProj);
    InputSetEntity inputSetEntity1 = InputSetEntity.builder()
                                         .accountId(accountId)
                                         .orgIdentifier(orgId)
                                         .projectIdentifier(projectId)
                                         .pipelineIdentifier(pipelineId)
                                         .yaml(inputSetYamlWithNoProj)
                                         .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                         .build();
    assertThatThrownBy(
        () -> InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity1, false, false, false, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Project identifier is missing in the YAML. Please give a valid Project identifier");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetWithNoErrors() {
    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).build());
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).storeType(StoreType.REMOTE).build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, false);

    String inputSetFile = "inputset1-with-org-proj-id.yaml";
    String inputSetYaml = readFile(inputSetFile);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .identifier(identifier)
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    // no exception should be thrown
    InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, false, false, false, null);

    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).isNewBranch(true).baseBranch("br").build());
    // no exception should be thrown
    InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, false, false, false, null);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testValidateInputSetWithInvalidIdentifierDisabledFF() {
    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).build());
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).storeType(StoreType.REMOTE).build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, false);

    String inputSetFile = "inputset1-with-org-proj-id.yaml";
    String inputSetYaml = readFile(inputSetFile);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .identifier(invalidIdentifier)
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    // no exception should be thrown
    InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, false, false, false, null);

    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).isNewBranch(true).baseBranch("br").build());
    // no exception should be thrown
    InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, false, false, false, null);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testValidateInputSetWithInvalidIdentifierEnabledFF() {
    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).build());
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).storeType(StoreType.REMOTE).build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, false);

    String inputSetFile = "inputset1-with-org-proj-id.yaml";
    String inputSetYaml = readFile(inputSetFile);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .identifier(invalidIdentifier)
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    // InvalidRequestException should be thrown
    assertThrows(InvalidRequestException.class,
        () -> InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, false, true, false, null));

    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).isNewBranch(true).baseBranch("br").build());
    // InvalidRequestException should be thrown
    assertThrows(InvalidRequestException.class,
        () -> InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, false, true, false, null));
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testValidateInputSetWithNullIdentifierDisabledFF() {
    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).build());
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).storeType(StoreType.REMOTE).build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, false);

    String inputSetFile = "inputset1-with-org-proj-id.yaml";
    String inputSetYaml = readFile(inputSetFile);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    // no exception should be thrown
    InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, false, false, false, null);

    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).isNewBranch(true).baseBranch("br").build());
    // no exception should be thrown
    InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, false, false, false, null);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testValidateInputSetWithNullIdentifierEnabledFF() {
    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).build());
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).storeType(StoreType.REMOTE).build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, false);

    String inputSetFile = "inputset1-with-org-proj-id.yaml";
    String inputSetYaml = readFile(inputSetFile);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    // InvalidRequestException should be thrown
    assertThrows(InvalidRequestException.class,
        () -> InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, false, true, false, null));

    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).isNewBranch(true).baseBranch("br").build());
    // InvalidRequestException should be thrown
    assertThrows(InvalidRequestException.class,
        () -> InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, false, true, false, null));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForNonExistentInputSet() {
    doThrow(new InvalidRequestException("InputSet with the given ID: inputSetId does not exist or has been deleted"))
        .when(inputSetService)
        .getMetadata(accountId, orgId, projectId, pipelineId, "inputSetId", false, false, true, null, false);
    doReturn(false).when(inputSetsApiUtils).isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(accountId);
    assertThatThrownBy(
        ()
            -> InputSetValidationHelper.getYAMLDiff(gitSyncSdkService, inputSetService, null, null, accountId, orgId,
                projectId, pipelineId, "inputSetId", "pipelineBranch", null, inputSetsApiUtils, null, false, false))
        .hasMessageContaining("does not exist or has been deleted")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForOverlayInputSet() {
    MockedStatic<OverlayInputSetValidationHelper> mockSettings =
        Mockito.mockStatic(OverlayInputSetValidationHelper.class);
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    doReturn(Optional.of(PipelineEntity.builder().yaml("pipeline: yaml").build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, scopeInfo, false);
    doReturn(PipelineEntity.builder().yaml("pipeline: yaml").connectorRef("connectorRef").repo("repo").build())
        .when(pipelineService)
        .getPipelineMetadata(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean());
    InputSetEntity overlayEntity =
        InputSetEntity.builder().inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET).build();
    doReturn(Optional.of(overlayEntity))
        .when(inputSetService)
        .getWithoutValidations(scopeInfo, pipelineId, "inputSetId", false, false, false, false);
    doReturn(overlayEntity)
        .when(inputSetService)
        .getMetadata(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean());
    when(OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(
             gitSyncSdkService, inputSetService, overlayEntity, "pipeline: yaml", scopeInfo, false))
        .thenReturn(InputSetYamlDiffDTO.builder().oldYAML("old: yaml").newYAML("new: yaml").build());
    doReturn(false).when(inputSetsApiUtils).isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(accountId);
    InputSetYamlDiffDTO yamlDiffDTO = InputSetValidationHelper.getYAMLDiff(gitSyncSdkService, inputSetService,
        pipelineService, null, accountId, orgId, projectId, pipelineId, "inputSetId", "pipelineBranch", null,
        inputSetsApiUtils, scopeInfo, false, false);
    assertThat(yamlDiffDTO.getOldYAML()).isEqualTo("old: yaml");
    assertThat(yamlDiffDTO.getNewYAML()).isEqualTo("new: yaml");
    mockSettings.close();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForOverlayInputSetInDiffRepoComparedToPipeline() {
    MockedStatic<OverlayInputSetValidationHelper> mockSettings =
        Mockito.mockStatic(OverlayInputSetValidationHelper.class);
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    doReturn(Optional.of(PipelineEntity.builder().yaml("pipeline: yaml").build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, scopeInfo, false);
    doReturn(PipelineEntity.builder().yaml("pipeline: yaml").connectorRef("connectorRef").repo("repo1").build())
        .when(pipelineService)
        .getPipelineMetadata(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean());
    InputSetEntity overlayEntity =
        InputSetEntity.builder().inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET).repo("repo2").build();
    doReturn(overlayEntity)
        .when(inputSetService)
        .getMetadata(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean());
    doReturn(Optional.of(overlayEntity))
        .when(inputSetService)
        .getWithoutValidations(scopeInfo, pipelineId, "inputSetId", false, false, false, false);
    when(OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(
             gitSyncSdkService, inputSetService, overlayEntity, "pipeline: yaml", scopeInfo, false))
        .thenReturn(InputSetYamlDiffDTO.builder().oldYAML("old: yaml").newYAML("new: yaml").build());
    doReturn(true).when(inputSetsApiUtils).isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(accountId);
    InputSetYamlDiffDTO yamlDiffDTO = InputSetValidationHelper.getYAMLDiff(gitSyncSdkService, inputSetService,
        pipelineService, null, accountId, orgId, projectId, pipelineId, "inputSetId", "defaultBranch", null,
        inputSetsApiUtils, scopeInfo, false, false);
    assertThat(yamlDiffDTO.getOldYAML()).isEqualTo("old: yaml");
    assertThat(yamlDiffDTO.getNewYAML()).isEqualTo("new: yaml");
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForRemoteOverlayInputSet() {
    GitAwareContextHelper.updateScmGitMetaData(ScmGitMetaData.builder().branchName("thisBranch").build());

    MockedStatic<OverlayInputSetValidationHelper> mockSettings =
        Mockito.mockStatic(OverlayInputSetValidationHelper.class);
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    doReturn(Optional.of(PipelineEntity.builder().yaml("pipeline: yaml").build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, scopeInfo, false);
    InputSetEntity overlayEntity = InputSetEntity.builder()
                                       .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                       .storeType(StoreType.REMOTE)
                                       .build();
    doReturn(PipelineEntity.builder().yaml("pipeline: yaml").connectorRef("connectorRef").repo("repo").build())
        .when(pipelineService)
        .getPipelineMetadata(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyBoolean());
    doReturn(overlayEntity)
        .when(inputSetService)
        .getMetadata(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean());
    doReturn(Optional.of(overlayEntity))
        .when(inputSetService)
        .getWithoutValidations(scopeInfo, pipelineId, "inputSetId", false, false, false, false);
    doReturn(false).when(inputSetsApiUtils).isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(accountId);
    when(OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(
             gitSyncSdkService, inputSetService, overlayEntity, "pipeline: yaml", scopeInfo, false))
        .thenReturn(InputSetYamlDiffDTO.builder().oldYAML("old: yaml").newYAML("new: yaml").build());
    InputSetYamlDiffDTO yamlDiffDTO = InputSetValidationHelper.getYAMLDiff(gitSyncSdkService, inputSetService,
        pipelineService, null, accountId, orgId, projectId, pipelineId, "inputSetId", "pipelineBranch", null,
        inputSetsApiUtils, scopeInfo, false, false);
    assertThat(yamlDiffDTO.getOldYAML()).isEqualTo("old: yaml");
    assertThat(yamlDiffDTO.getNewYAML()).isEqualTo("new: yaml");
    assertThat(yamlDiffDTO.getGitDetails().getBranch()).isEqualTo("thisBranch");
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForInputSet() {
    MockedStatic<InputSetSanitizer> mockSettings = Mockito.mockStatic(InputSetSanitizer.class);
    when(InputSetSanitizer.sanitizeInputSetAndUpdateInputSetYAML("pipeline: yaml", "input: set"))
        .thenReturn("input: setNew");
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml("input: set")
                                        .build();
    InputSetYamlDiffDTO yamlDiffForInputSet =
        InputSetValidationHelper.getYAMLDiffForInputSet(null, inputSetEntity, "pipeline: yaml");
    assertThat(yamlDiffForInputSet.getOldYAML()).isEqualTo("input: set");
    assertThat(yamlDiffForInputSet.getNewYAML()).isEqualTo("input: setNew");
    assertThat(yamlDiffForInputSet.isInputSetEmpty()).isEqualTo(false);
    assertThat(yamlDiffForInputSet.isNoUpdatePossible()).isEqualTo(false);
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForInputSetWithNoUpdatePossible() {
    MockedStatic<InputSetSanitizer> mockSettings = Mockito.mockStatic(InputSetSanitizer.class);
    when(InputSetSanitizer.sanitizeInputSetAndUpdateInputSetYAML("pipeline: yaml", "input: set")).thenReturn(null);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml("input: set")
                                        .build();
    InputSetYamlDiffDTO yamlDiffForInputSet =
        InputSetValidationHelper.getYAMLDiffForInputSet(validateAndMergeHelper, inputSetEntity, "pipeline: yaml");
    assertThat(yamlDiffForInputSet.isInputSetEmpty()).isEqualTo(true);
    assertThat(yamlDiffForInputSet.isNoUpdatePossible()).isEqualTo(true);
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForInputSetWithUpdatePossible() {
    MockedStatic<InputSetSanitizer> mockSettings = Mockito.mockStatic(InputSetSanitizer.class);
    when(InputSetSanitizer.sanitizeInputSetAndUpdateInputSetYAML("pipeline: yaml", "input: set")).thenReturn(null);
    doReturn("new: template").when(validateAndMergeHelper).getPipelineTemplate("pipeline: yaml", null);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml("input: set")
                                        .build();
    InputSetYamlDiffDTO yamlDiffForInputSet =
        InputSetValidationHelper.getYAMLDiffForInputSet(validateAndMergeHelper, inputSetEntity, "pipeline: yaml");
    assertThat(yamlDiffForInputSet.isInputSetEmpty()).isEqualTo(true);
    assertThat(yamlDiffForInputSet.isNoUpdatePossible()).isEqualTo(false);
    mockSettings.close();
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

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testValidateInputSetV1() {
    String inputSetFile = "inputSetV1.yaml";
    String inputSetYaml = readFile(inputSetFile);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .storeType(StoreType.INLINE)
                                        .harnessVersion(HarnessYamlVersion.V1)
                                        .build();
    // no exception should be thrown
    InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, false, false, false, null);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category({UnitTests.class})
  public void testGetInputSetEntity1() {
    GitAwareContextHelper.updateGitEntityContextWithBranch("branch");
    GitAwareContextHelper.updateScmGitMetaData(ScmGitMetaData.builder().branchName("branch").repoName("repo1").build());
    InputSetEntity inputSetMetadata = InputSetEntity.builder().repo("repo1").connectorRef("connectorRef").build();
    PipelineEntity pipelineMetadata = PipelineEntity.builder().repo("repo1").build();
    assertThatThrownBy(
        ()
            -> InputSetValidationHelper.getInputSetEntity(accountId, orgId, projectId, pipelineId, "branch",
                pipelineMetadata, inputSetMetadata, "inputSetId", inputSetService, false, false, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("InputSet with the given ID: inputSetId does not exist or has been deleted");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category({UnitTests.class})
  public void testGetInputSetEntity2() {
    GitAwareContextHelper.updateGitEntityContextWithBranch("branch");
    InputSetEntity inputSetMetadata = InputSetEntity.builder().repo("repo1").connectorRef("connectorRef").build();
    PipelineEntity pipelineMetadata = PipelineEntity.builder().repo("repo1").build();
    assertThatThrownBy(
        ()
            -> InputSetValidationHelper.getInputSetEntity(accountId, orgId, projectId, pipelineId, "branch1",
                pipelineMetadata, inputSetMetadata, "inputSetId", inputSetService, false, false, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Reconciliation is not allowed for the given input set. Pipeline and InputSet must be present on "
            + "the same branch when they are in the same repository");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category({UnitTests.class})
  public void testGetInputSetEntity4() {
    InputSetEntity inputSetMetadata = InputSetEntity.builder().repo("repo1").connectorRef("connectorRef").build();
    PipelineEntity pipelineMetadata = PipelineEntity.builder().repo("repo2").build();
    assertThatThrownBy(
        ()
            -> InputSetValidationHelper.getInputSetEntity(accountId, orgId, projectId, pipelineId, "branch",
                pipelineMetadata, inputSetMetadata, "inputSetId", inputSetService, false, false, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Reconciliation is not allowed for the given input set. Pipeline and input set must be in same "
            + "repository. Please enable account level default setting : 'Allow different repo for Pipeline "
            + "and InputSets' if its intended to keep pipeline and input set in different repository.");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category({UnitTests.class})
  public void testGetInputSetEntity5() {
    InputSetEntity inputSetMetadata = InputSetEntity.builder().repo("repo1").connectorRef("connectorRef").build();
    PipelineEntity pipelineMetadata = PipelineEntity.builder().repo("repo2").build();
    assertThatThrownBy(
        ()
            -> InputSetValidationHelper.getInputSetEntity(accountId, orgId, projectId, pipelineId, "branch",
                pipelineMetadata, inputSetMetadata, "inputSetId", inputSetService, true, false, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("InputSet with the given ID: inputSetId does not exist or has been deleted");
  }

  // ─── Tests for validateNoRuntimeInputsWrappedInList ───────────────────────

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testValidateNoRuntimeInputsWrappedInList_throwsWhenBranchIsArray() {
    String yaml = "inputSet:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  orgIdentifier: default\n"
        + "  projectIdentifier: proj1\n"
        + "  pipeline:\n"
        + "    identifier: branch_list_bug_repro\n"
        + "    properties:\n"
        + "      ci:\n"
        + "        codebase:\n"
        + "          build:\n"
        + "            type: branch\n"
        + "            spec:\n"
        + "              branch:\n"
        + "                - <+input>\n";

    assertThatThrownBy(() -> InputSetValidationHelper.validateNoRuntimeInputsWrappedInList(yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("<+input>")
        .hasMessageContaining("list");
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testValidateNoRuntimeInputsWrappedInList_passesForScalarBranch() {
    String yaml = "inputSet:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  orgIdentifier: default\n"
        + "  projectIdentifier: proj1\n"
        + "  pipeline:\n"
        + "    identifier: branch_list_bug_repro\n"
        + "    properties:\n"
        + "      ci:\n"
        + "        codebase:\n"
        + "          build:\n"
        + "            type: branch\n"
        + "            spec:\n"
        + "              branch: <+input>\n";

    // no exception expected — valid scalar runtime input
    InputSetValidationHelper.validateNoRuntimeInputsWrappedInList(yaml);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testValidateNoRuntimeInputsWrappedInList_passesForActualListValues() {
    String yaml = "inputSet:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  orgIdentifier: default\n"
        + "  projectIdentifier: proj1\n"
        + "  pipeline:\n"
        + "    identifier: some_pipeline\n"
        + "    stages:\n"
        + "      - stage:\n"
        + "          identifier: Build\n"
        + "          spec:\n"
        + "            execution:\n"
        + "              steps:\n"
        + "                - step:\n"
        + "                    identifier: s1\n"
        + "                    spec:\n"
        + "                      envVariables:\n"
        + "                        - dev\n"
        + "                        - stage\n"
        + "                        - prod\n";

    // no exception — list with actual string values is fine
    InputSetValidationHelper.validateNoRuntimeInputsWrappedInList(yaml);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testValidateNoRuntimeInputsWrappedInList_throwsForPipelineVariableWrappedInList() {
    String yaml = "inputSet:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  orgIdentifier: default\n"
        + "  projectIdentifier: proj1\n"
        + "  pipeline:\n"
        + "    identifier: some_pipeline\n"
        + "    variables:\n"
        + "      - name: myVar\n"
        + "        type: String\n"
        + "        value:\n"
        + "          - <+input>\n";

    assertThatThrownBy(() -> InputSetValidationHelper.validateNoRuntimeInputsWrappedInList(yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("<+input>");
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testValidateNoRuntimeInputsWrappedInList_passesForMissingPipelineBlock() {
    // input set without a pipeline block — should not throw
    String yaml = "inputSet:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  orgIdentifier: default\n"
        + "  projectIdentifier: proj1\n";

    InputSetValidationHelper.validateNoRuntimeInputsWrappedInList(yaml);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testValidateInputSet_throwsWhenInputSetYamlHasBranchAsArray() {
    String yaml = "inputSet:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  orgIdentifier: orgId\n"
        + "  projectIdentifier: projectId\n"
        + "  pipeline:\n"
        + "    identifier: Test_Pipline11\n"
        + "    properties:\n"
        + "      ci:\n"
        + "        codebase:\n"
        + "          build:\n"
        + "            type: branch\n"
        + "            spec:\n"
        + "              branch:\n"
        + "                - <+input>\n";

    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .identifier("test")
                                        .yaml(yaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .storeType(StoreType.INLINE)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();

    assertThatThrownBy(
        () -> InputSetValidationHelper.validateInputSet(inputSetService, inputSetEntity, true, false, false, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("<+input>")
        .hasMessageContaining("list");
  }
}

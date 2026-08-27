/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.mappers;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PMSInputSetElementMapperTest extends CategoryTest {
  private final String PIPELINE_IDENTIFIER = "Test_Pipline11";
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";

  @Mock GitSyncSdkService gitSyncSdkService;

  String inputSetYaml;
  String overlayInputSetYaml;

  String inputSetYamlV1;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    String inputSet = "inputSet1.yml";
    inputSetYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(inputSet)), StandardCharsets.UTF_8);

    String overlayInputSet = "overlaySet1.yml";
    overlayInputSetYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(overlayInputSet)), StandardCharsets.UTF_8);

    String inputSetV1 = "inputSetV1.yaml";
    inputSetYamlV1 =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(inputSetV1)), StandardCharsets.UTF_8);

    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetEntity() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("company", "harness");
    tags.put("kind", "normal");
    List<NGTag> tagsList = TagMapper.convertToList(tags);

    assertThat(entity.getIdentifier()).isEqualTo("input1");
    assertThat(entity.getName()).isEqualTo("this name");
    assertThat(entity.getDescription()).isEqualTo("this has a description too");
    assertThat(entity.getTags()).isEqualTo(tagsList);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetEntityWithEmptyIdentifierAndName() {
    String emptyID = "inputSet:\n"
        + "    name: \"\"\n"
        + "    identifier: \"\"\n"
        + "    orgIdentifier: default\n"
        + "    projectIdentifier: Plain_Old_Project\n";
    assertThatThrownBy(()
                           -> PMSInputSetElementMapper.toInputSetEntity(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, emptyID))
        .hasMessage("Input Set Identifier cannot be empty or a runtime input");

    String emptyName = "inputSet:\n"
        + "    name: \"\"\n"
        + "    identifier: \"id\"\n"
        + "    orgIdentifier: default\n"
        + "    projectIdentifier: Plain_Old_Project\n";
    assertThatThrownBy(()
                           -> PMSInputSetElementMapper.toInputSetEntity(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, emptyName))
        .hasMessage("Input Set Name cannot be empty or a runtime input");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetEntityGitDetails() {
    InputSetEntity oldNonGitSync = InputSetEntity.builder().build();
    EntityGitDetails entityGitDetails0 = PMSInputSetElementMapper.getEntityGitDetails(oldNonGitSync);
    assertThat(entityGitDetails0).isEqualTo(EntityGitDetails.builder().build());

    InputSetEntity oldGitSync = InputSetEntity.builder().yamlGitConfigRef("repo").branch("branch1").build();
    EntityGitDetails entityGitDetails1 = PMSInputSetElementMapper.getEntityGitDetails(oldGitSync);
    assertThat(entityGitDetails1).isNotNull();
    assertThat(entityGitDetails1.getRepoIdentifier()).isEqualTo("repo");
    assertThat(entityGitDetails1.getBranch()).isEqualTo("branch1");

    InputSetEntity inline = InputSetEntity.builder().storeType(StoreType.INLINE).build();
    EntityGitDetails entityGitDetails2 = PMSInputSetElementMapper.getEntityGitDetails(inline);
    assertThat(entityGitDetails2).isNull();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").build());

    InputSetEntity remote = InputSetEntity.builder().storeType(StoreType.REMOTE).build();
    EntityGitDetails entityGitDetails3 = PMSInputSetElementMapper.getEntityGitDetails(remote);
    assertThat(entityGitDetails3).isNotNull();
    assertThat(entityGitDetails3.getBranch()).isEqualTo("brName");
    assertThat(entityGitDetails3.getRepoName()).isEqualTo("repoName");
    assertThat(entityGitDetails3.getRepoIdentifier()).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetEntityForOverlay() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("isOverlaySet", "yes it is");
    List<NGTag> tagsList = TagMapper.convertToList(tags);
    List<String> references = new ArrayList<>();
    references.add("inputSet2");
    references.add("inputSet22");

    assertThat(entity.getIdentifier()).isEqualTo("overlay1");
    assertThat(entity.getName()).isEqualTo("thisName");
    assertThat(entity.getDescription()).isEqualTo("this is an overlay input set");
    assertThat(entity.getTags()).isEqualTo(tagsList);
    assertThat(entity.getInputSetReferences()).isEqualTo(references);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetResponseDTOPMS() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml);
    InputSetResponseDTOPMS inputSetResponseDTOPMS =
        PMSInputSetElementMapper.toInputSetResponseDTOPMS(entity, null, false);
    assertThat(inputSetResponseDTOPMS.isErrorResponse()).isFalse();
    assertThat(inputSetResponseDTOPMS.getInputSetErrorWrapper()).isNull();

    assertThat(inputSetResponseDTOPMS.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(inputSetResponseDTOPMS.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("company", "harness");
    tags.put("kind", "normal");
    assertThat(inputSetResponseDTOPMS.getIdentifier()).isEqualTo("input1");
    assertThat(inputSetResponseDTOPMS.getName()).isEqualTo("this name");
    assertThat(inputSetResponseDTOPMS.getDescription()).isEqualTo("this has a description too");
    assertThat(inputSetResponseDTOPMS.getTags()).isEqualTo(tags);
    assertThat(inputSetResponseDTOPMS.getIsInlineHCEntity()).isFalse();
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testToInlineHCInputSetResponseDTOPMS() {
    Map<String, String> expectedTags = new LinkedHashMap<>();
    expectedTags.put("company", "harness");
    expectedTags.put("kind", "normal");

    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml);
    entity.setStoreType(StoreType.INLINE_HC);
    InputSetResponseDTOPMS inputSetResponseDTOPMS =
        PMSInputSetElementMapper.toInputSetResponseDTOPMS(entity, null, false);

    assertThat(inputSetResponseDTOPMS.isErrorResponse()).isFalse();
    assertThat(inputSetResponseDTOPMS.getInputSetErrorWrapper()).isNull();
    assertThat(inputSetResponseDTOPMS.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(inputSetResponseDTOPMS.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(inputSetResponseDTOPMS.getIsInlineHCEntity()).isTrue();
    assertThat(inputSetResponseDTOPMS.getIdentifier()).isEqualTo("input1");
    assertThat(inputSetResponseDTOPMS.getName()).isEqualTo("this name");
    assertThat(inputSetResponseDTOPMS.getDescription()).isEqualTo("this has a description too");
    assertThat(inputSetResponseDTOPMS.getTags()).isEqualTo(expectedTags);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToOverlayInputSetResponseDTOPMS() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml);
    OverlayInputSetResponseDTOPMS inputSetResponseDTOPMS =
        PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(entity, null, false);
    assertThat(inputSetResponseDTOPMS.isErrorResponse()).isFalse();
    assertThat(inputSetResponseDTOPMS.getInvalidInputSetReferences()).isNull();

    assertThat(inputSetResponseDTOPMS.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(inputSetResponseDTOPMS.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("isOverlaySet", "yes it is");
    List<String> references = new ArrayList<>();
    references.add("inputSet2");
    references.add("inputSet22");
    assertThat(inputSetResponseDTOPMS.getIdentifier()).isEqualTo("overlay1");
    assertThat(inputSetResponseDTOPMS.getName()).isEqualTo("thisName");
    assertThat(inputSetResponseDTOPMS.getDescription()).isEqualTo("this is an overlay input set");
    assertThat(inputSetResponseDTOPMS.getTags()).isEqualTo(tags);
    assertThat(inputSetResponseDTOPMS.getInputSetReferences()).isEqualTo(references);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testToInlineHCOverlayInputSetResponseDTOPMS() {
    Map<String, String> expectedTags = new LinkedHashMap<>();
    expectedTags.put("isOverlaySet", "yes it is");
    List<String> expectedReferences = new ArrayList<>();
    expectedReferences.add("inputSet2");
    expectedReferences.add("inputSet22");

    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml);
    entity.setStoreType(StoreType.INLINE_HC);
    OverlayInputSetResponseDTOPMS inputSetResponseDTOPMS =
        PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(entity, null, false);

    assertThat(inputSetResponseDTOPMS.isErrorResponse()).isFalse();
    assertThat(inputSetResponseDTOPMS.getInvalidInputSetReferences()).isNull();
    assertThat(inputSetResponseDTOPMS.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(inputSetResponseDTOPMS.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(inputSetResponseDTOPMS.getIdentifier()).isEqualTo("overlay1");
    assertThat(inputSetResponseDTOPMS.getName()).isEqualTo("thisName");
    assertThat(inputSetResponseDTOPMS.getDescription()).isEqualTo("this is an overlay input set");
    assertThat(inputSetResponseDTOPMS.getTags()).isEqualTo(expectedTags);
    assertThat(inputSetResponseDTOPMS.getInputSetReferences()).isEqualTo(expectedReferences);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetSummaryResponseDTOPMS() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml);

    InputSetSummaryResponseDTOPMS inputSetResponseDTOPMS =
        PMSInputSetElementMapper.toInputSetSummaryResponseDTOPMS(entity);

    assertThat(inputSetResponseDTOPMS.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("company", "harness");
    tags.put("kind", "normal");
    assertThat(inputSetResponseDTOPMS.getIdentifier()).isEqualTo("input1");
    assertThat(inputSetResponseDTOPMS.getName()).isEqualTo("this name");
    assertThat(inputSetResponseDTOPMS.getDescription()).isEqualTo("this has a description too");
    assertThat(inputSetResponseDTOPMS.getTags()).isEqualTo(tags);

    InputSetEntity entity1 = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml);
    OverlayInputSetResponseDTOPMS inputSetResponseDTOPMS1 =
        PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(entity1, null, false);

    assertThat(inputSetResponseDTOPMS1.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    tags = new LinkedHashMap<>();
    tags.put("isOverlaySet", "yes it is");
    assertThat(inputSetResponseDTOPMS1.getIdentifier()).isEqualTo("overlay1");
    assertThat(inputSetResponseDTOPMS1.getName()).isEqualTo("thisName");
    assertThat(inputSetResponseDTOPMS1.getDescription()).isEqualTo("this is an overlay input set");
    assertThat(inputSetResponseDTOPMS1.getTags()).isEqualTo(tags);
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testToInlineHCSummaryResponseDTOPMS() {
    Map<String, String> expectedTags = new LinkedHashMap<>();
    expectedTags.put("company", "harness");
    expectedTags.put("kind", "normal");

    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml);
    entity.setStoreType(StoreType.INLINE_HC);
    InputSetSummaryResponseDTOPMS inputSetResponseDTOPMS =
        PMSInputSetElementMapper.toInputSetSummaryResponseDTOPMS(entity);

    assertThat(inputSetResponseDTOPMS.getIdentifier()).isEqualTo("input1");
    assertThat(inputSetResponseDTOPMS.getName()).isEqualTo("this name");
    assertThat(inputSetResponseDTOPMS.getDescription()).isEqualTo("this has a description too");
    assertThat(inputSetResponseDTOPMS.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(inputSetResponseDTOPMS.getIsInlineHCEntity()).isEqualTo(true);
    assertThat(inputSetResponseDTOPMS.getTags()).isEqualTo(expectedTags);

    expectedTags = new LinkedHashMap<>();
    expectedTags.put("isOverlaySet", "yes it is");

    InputSetEntity entity1 = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml);
    entity1.setStoreType(StoreType.INLINE_HC);
    OverlayInputSetResponseDTOPMS inputSetResponseDTOPMS1 =
        PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(entity1, null, false);

    assertThat(inputSetResponseDTOPMS1.getIdentifier()).isEqualTo("overlay1");
    assertThat(inputSetResponseDTOPMS1.getName()).isEqualTo("thisName");
    assertThat(inputSetResponseDTOPMS1.getDescription()).isEqualTo("this is an overlay input set");
    assertThat(inputSetResponseDTOPMS1.getTags()).isEqualTo(expectedTags);
    assertThat(inputSetResponseDTOPMS1.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS1.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(inputSetResponseDTOPMS1.getIsInlineHCEntity()).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetResponseDTOPMSWithErrors() {
    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").build());
    InputSetEntity entity =
        PMSInputSetElementMapper
            .toInputSetEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml)
            .withStoreType(StoreType.REMOTE);
    InputSetErrorWrapperDTOPMS dummyErrorResponse =
        InputSetErrorWrapperDTOPMS.builder().uuidToErrorResponseMap(Collections.singletonMap("fqn", null)).build();
    InputSetResponseDTOPMS inputSetResponseDTO =
        PMSInputSetElementMapper.toInputSetResponseDTOPMSWithErrors(entity, dummyErrorResponse, null, false);
    assertThat(inputSetResponseDTO.isErrorResponse()).isTrue();
    assertThat(inputSetResponseDTO.getInputSetErrorWrapper()).isEqualTo(dummyErrorResponse);

    assertThat(inputSetResponseDTO.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(inputSetResponseDTO.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(inputSetResponseDTO.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(inputSetResponseDTO.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("company", "harness");
    tags.put("kind", "normal");
    assertThat(inputSetResponseDTO.getIdentifier()).isEqualTo("input1");
    assertThat(inputSetResponseDTO.getName()).isEqualTo("this name");
    assertThat(inputSetResponseDTO.getDescription()).isEqualTo("this has a description too");
    assertThat(inputSetResponseDTO.getTags()).isEqualTo(tags);
    assertThat(inputSetResponseDTO.getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(inputSetResponseDTO.getIsInlineHCEntity()).isFalse();

    EntityValidityDetails entityValidityDetails = inputSetResponseDTO.getEntityValidityDetails();
    assertThat(entityValidityDetails.isValid()).isFalse();
    assertThat(entityValidityDetails.getInvalidYaml()).isEqualTo(inputSetYaml);

    EntityGitDetails gitDetails = inputSetResponseDTO.getGitDetails();
    assertThat(gitDetails.getRepoName()).isEqualTo("repoName");
    assertThat(gitDetails.getRepoIdentifier()).isNull();
    assertThat(gitDetails.getBranch()).isEqualTo("brName");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testToInputSetEntityByYaml() {
    InputSetEntity inputSetEntity = PMSInputSetElementMapper.toInputSetEntity("accountId", inputSetYaml);
    assertEquals(inputSetEntity.getAccountId(), "accountId");
    assertEquals(inputSetEntity.getPipelineIdentifier(), "Test_Pipline11");
    assertEquals(inputSetEntity.getIdentifier(), "input1");
    inputSetEntity = PMSInputSetElementMapper.toInputSetEntity("accountId", overlayInputSetYaml);
    assertEquals(inputSetEntity.getAccountId(), "accountId");
    assertEquals(inputSetEntity.getIdentifier(), "overlay1");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testToInputSetEntityV1() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityV1(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYamlV1, InputSetEntityType.INPUT_SET);

    assertThat(entity.getIdentifier()).isEqualTo("set1");
    assertThat(entity.getName()).isEqualTo("set1");
    assertThat(entity.getInputSetEntityType()).isEqualTo(InputSetEntityType.INPUT_SET);
    assertThat(entity.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testToInputSetEntityV1WithEmptyName() {
    String emptyName = "version: 1\n"
        + "name: \"\"\n"
        + "inputs:\n"
        + "    image: \"\"\n"
        + "    repo: default\n";
    assertThatThrownBy(()
                           -> PMSInputSetElementMapper.toInputSetEntityV1(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, emptyName, InputSetEntityType.INPUT_SET))
        .hasMessage("Input Set name cannot be empty or a runtime input");
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testToInputSetResponseDTOWithScopeInfo() {
    String newOrg = "newOrg";
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(ACCOUNT_ID)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(newOrg)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId("xyz")
                              .build();
    InputSetResponseDTOPMS result = PMSInputSetElementMapper.toInputSetResponseDTOPMS(inputSetEntity, scopeInfo);
    assertEquals(newOrg, result.getOrgIdentifier());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testToInputSetEntityForOverlayFromVersionV1() {
    String overlayYamlV1 = "version: 1\n"
        + "name: overlay1\n"
        + "spec:\n"
        + "  input_sets:\n"
        + "    - inputSet2\n"
        + "    - inputSet22\n";
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlayFromVersion(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayYamlV1, HarnessYamlVersion.V1);

    assertThat(entity.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
    assertThat(entity.getInputSetEntityType()).isEqualTo(InputSetEntityType.OVERLAY_INPUT_SET);
    assertThat(entity.getName()).isEqualTo("overlay1");
    assertThat(entity.getIdentifier()).isEqualTo("overlay1");
    assertThat(entity.getInputSetReferences()).containsExactly("inputSet2", "inputSet22");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testToInputSetEntityForOverlayFromVersionNullPreservesV0() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlayFromVersion(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml, null);
    InputSetEntity expectedV0 = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml);

    assertThat(entity.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(entity.getInputSetEntityType()).isEqualTo(InputSetEntityType.OVERLAY_INPUT_SET);
    assertThat(entity.getIdentifier()).isEqualTo(expectedV0.getIdentifier());
    assertThat(entity.getName()).isEqualTo(expectedV0.getName());
    assertThat(entity.getInputSetReferences()).isEqualTo(expectedV0.getInputSetReferences());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testToInputSetEntityForOverlayFromVersionEmptyPreservesV0() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlayFromVersion(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml, "");

    assertThat(entity.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(entity.getInputSetEntityType()).isEqualTo(InputSetEntityType.OVERLAY_INPUT_SET);
    assertThat(entity.getIdentifier()).isEqualTo("overlay1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testToInputSetEntityForOverlayFromVersionZeroPreservesV0() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlayFromVersion(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml, HarnessYamlVersion.V0);

    assertThat(entity.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(entity.getInputSetEntityType()).isEqualTo(InputSetEntityType.OVERLAY_INPUT_SET);
    assertThat(entity.getIdentifier()).isEqualTo("overlay1");
    assertThat(entity.getInputSetReferences()).containsExactly("inputSet2", "inputSet22");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testToInputSetEntityForOverlayFromVersionUnsupportedThrows() {
    assertThatThrownBy(()
                           -> PMSInputSetElementMapper.toInputSetEntityForOverlayFromVersion(ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml, "2"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("version[2] not supported");
  }
}

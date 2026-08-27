/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers.validate;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BHUMIJ;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.dto.InputSetMetadataDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class ValidateAndMergeHelperTest extends PipelineServiceTestBase {
  @InjectMocks ValidateAndMergeHelper validateAndMergeHelper;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSInputSetService pmsInputSetService;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @InjectMocks StagesExpressionExtractor stagesExpressionExtractor;

  private static final String accountId = "accountId";
  private static final String orgId = "orgId";
  private static final String projectId = "projectId";
  private static final String pipelineId = "Test_Pipline11";
  private static final String parentUniqueId = "parentUniqueId";

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Before
  public void setUp() throws IOException {
    doReturn(getScopeInfo()).when(scopeResolutionHelper).getScopeInfo(any(), any(), any());
    Reflect.on(validateAndMergeHelper).set("stagesExpressionExtractor", stagesExpressionExtractor);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineTemplate() {
    String pipelineStart = "pipeline:\n"
        + "  stages:\n";
    String stage1 = "    - stage:\n"
        + "        identifier: s1\n"
        + "        myField: <+input>\n";
    String stage2 = "    - stage:\n"
        + "        identifier: s2\n"
        + "        myField: <+input>\n";
    String pipelineYaml = pipelineStart + stage1 + stage2;

    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).build();
    doReturn(Optional.of(pipelineEntity))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, false);
    String pipelineTemplate = validateAndMergeHelper.getPipelineTemplate(accountId, orgId, projectId, pipelineId, null);
    assertThat(pipelineTemplate).isEqualTo(pipelineYaml);

    String s1Template = validateAndMergeHelper.getPipelineTemplate(
        accountId, orgId, projectId, pipelineId, Collections.singletonList("s1"));
    assertThat(s1Template).isEqualTo(pipelineStart + stage1);

    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, false);
    assertThatThrownBy(() -> validateAndMergeHelper.getPipelineTemplate(accountId, orgId, projectId, pipelineId, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(format("Pipeline [%s] under Project[%s], Organization [%s] doesn't exist or has been deleted.",
            pipelineId, projectId, orgId));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInputSetTemplateResponseDTOWithNoRuntime() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountId)
                              .orgIdentifier(orgId)
                              .projectIdentifier(projectId)
                              .uniqueId("someRandomId")
                              .build();
    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, scopeInfo, true);
    assertThatThrownBy(()
                           -> validateAndMergeHelper.getInputSetTemplateResponseDTO(
                               accountId, orgId, projectId, pipelineId, null, false, scopeInfo, true, true))
        .isInstanceOf(InvalidRequestException.class);
    String pipelineYamlWithNoRuntime = getPipelineYamlWithNoRuntime();
    PipelineEntity pipelineEntityWithNoRuntime =
        PipelineEntity.builder().yaml(pipelineYamlWithNoRuntime).filters(Collections.singletonMap("pms", null)).build();
    doReturn(Optional.of(pipelineEntityWithNoRuntime))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, "no_runtime", false, false, false, false, scopeInfo, true);
    doReturn(false)
        .when(pmsInputSetService)
        .checkForInputSetsForPipeline(accountId, orgId, projectId, "no_runtime", scopeInfo, true);
    InputSetTemplateResponseDTOPMS responseWithNoRuntime = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "no_runtime", null, false, scopeInfo, true, true);
    assertThat(responseWithNoRuntime.getHasInputSets()).isFalse();
    assertThat(responseWithNoRuntime.getModules()).containsExactly("pms");
    assertThat(responseWithNoRuntime.getReplacedExpressions()).isNull();
    assertThat(responseWithNoRuntime.getReplacedExpressionsPerStage()).isNull();
    assertThat(responseWithNoRuntime.getInputSetTemplateYaml()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInputSetTemplateResponseDTOWithRuntime() {
    String pipelineYamlWithRuntime = getPipelineYamlWithRuntime();
    PipelineEntity pipelineEntityWithRuntime =
        PipelineEntity.builder().yaml(pipelineYamlWithRuntime).filters(Collections.singletonMap("pms", null)).build();
    doReturn(Optional.of(pipelineEntityWithRuntime))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, "has_runtime", false, false, false, false, null, false);
    doReturn(true)
        .when(pmsInputSetService)
        .checkForInputSetsForPipeline(accountId, orgId, projectId, "has_runtime", null, false);
    InputSetTemplateResponseDTOPMS responseWithNoRuntime = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "has_runtime", null, false, null, false, false);
    assertThat(responseWithNoRuntime.getHasInputSets()).isTrue();
    assertThat(responseWithNoRuntime.getModules()).containsExactly("pms");
    assertThat(responseWithNoRuntime.getReplacedExpressions()).isNull();
    assertThat(responseWithNoRuntime.getReplacedExpressionsPerStage()).isNull();
    assertThat(responseWithNoRuntime.getInputSetTemplateYaml()).isEqualTo(getRuntimeTemplate());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetInputSetTemplateResponseDTOWithRuntimeWithCaching() {
    String pipelineYamlWithRuntime = getPipelineYamlWithRuntime();
    PipelineEntity pipelineEntityWithRuntime =
        PipelineEntity.builder().yaml(pipelineYamlWithRuntime).filters(Collections.singletonMap("pms", null)).build();
    doReturn(Optional.of(pipelineEntityWithRuntime))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, "has_runtime", false, false, false, true, null, false);
    doReturn(true)
        .when(pmsInputSetService)
        .checkForInputSetsForPipeline(accountId, orgId, projectId, "has_runtime", null, false);
    InputSetTemplateResponseDTOPMS responseWithNoRuntime = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "has_runtime", null, true, null, false, false);
    assertThat(responseWithNoRuntime.getHasInputSets()).isTrue();
    assertThat(responseWithNoRuntime.getModules()).containsExactly("pms");
    assertThat(responseWithNoRuntime.getReplacedExpressions()).isNull();
    assertThat(responseWithNoRuntime.getReplacedExpressionsPerStage()).isNull();
    assertThat(responseWithNoRuntime.getInputSetTemplateYaml()).isEqualTo(getRuntimeTemplate());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInputSetTemplateResponseDTOWithDefaultValuesForVariablse() {
    String pipelineYamlWithRuntime = "pipeline:\n"
        + "  variables:\n"
        + "    - name: varName\n"
        + "      type: String\n"
        + "      default: num\n"
        + "      value: <+input>\n";
    PipelineEntity pipelineEntityWithRuntime =
        PipelineEntity.builder().yaml(pipelineYamlWithRuntime).filters(Collections.singletonMap("pms", null)).build();
    doReturn(Optional.of(pipelineEntityWithRuntime))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, "has_runtime", false, false, false, false, null, false);
    doReturn(true)
        .when(pmsInputSetService)
        .checkForInputSetsForPipeline(accountId, orgId, projectId, "has_runtime", null, false);
    InputSetTemplateResponseDTOPMS response = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "has_runtime", null, false, null, false, false);
    assertThat(response.getHasInputSets()).isTrue();
    assertThat(response.getModules()).containsExactly("pms");
    assertThat(response.getReplacedExpressions()).isNull();
    assertThat(response.getReplacedExpressionsPerStage()).isNull();
    assertThat(response.getInputSetTemplateYaml()).isEqualTo(pipelineYamlWithRuntime);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetTemplateResponseDTOWithSelectedStagesWithoutCodebaseProperties() {
    String pipelineYamlWithRuntime = readFile("pipeline-yaml-multiple-stages.yaml");
    PipelineEntity pipelineEntityWithRuntime = PipelineEntity.builder().yaml(pipelineYamlWithRuntime).build();
    doReturn(Optional.of(pipelineEntityWithRuntime))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, "has_runtime", false, false, false, false, null, false);
    doReturn(true)
        .when(pmsInputSetService)
        .checkForInputSetsForPipeline(accountId, orgId, projectId, "has_runtime", null, false);
    List<String> selectedStages = new ArrayList<>();
    selectedStages.add("customstage");
    selectedStages.add("cistage2");
    InputSetTemplateResponseDTOPMS responseWithRuntime = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "has_runtime", selectedStages, false, null, false, false);
    assertThat(responseWithRuntime.getHasInputSets()).isTrue();
    assertThat(responseWithRuntime.getInputSetTemplateYaml()).isEqualTo(getRuntimeTemplateWithoutProperties());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetTemplateResponseDTOWithSelectedStagesWithCodebaseProperties() {
    String pipelineYamlWithRuntime = readFile("pipeline-yaml-multiple-stages.yaml");
    PipelineEntity pipelineEntityWithRuntime = PipelineEntity.builder().yaml(pipelineYamlWithRuntime).build();
    doReturn(Optional.of(pipelineEntityWithRuntime))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, "has_runtime", false, false, false, false, null, false);
    doReturn(true)
        .when(pmsInputSetService)
        .checkForInputSetsForPipeline(accountId, orgId, projectId, "has_runtime", null, false);
    InputSetTemplateResponseDTOPMS responseWithRuntime = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "has_runtime", Collections.singletonList("cistage1"), false, null, false, false);
    assertThat(responseWithRuntime.getHasInputSets()).isTrue();
    assertThat(responseWithRuntime.getInputSetTemplateYaml()).isEqualTo(getRuntimeTemplateWithProperties());
    List<String> selectedStages = new ArrayList<>();
    selectedStages.add("cistage1");
    selectedStages.add("cistage2");
    responseWithRuntime = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "has_runtime", selectedStages, false, null, false, false);
    assertThat(responseWithRuntime.getHasInputSets()).isTrue();
    assertThat(responseWithRuntime.getInputSetTemplateYaml()).isEqualTo(getRuntimeTemplateWithProperties());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetTemplateResponseDTOWithSelectedStagesWithCodebasePropertiesAndNoRuntimeInput() {
    String pipelineYamlWithRuntime = readFile("pipeline-yaml-multiple-stages-no-runtime.yaml");
    PipelineEntity pipelineEntityWithRuntime = PipelineEntity.builder().yaml(pipelineYamlWithRuntime).build();
    doReturn(Optional.of(pipelineEntityWithRuntime))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, "has_runtime", false, false, false, false, null, false);
    doReturn(true)
        .when(pmsInputSetService)
        .checkForInputSetsForPipeline(accountId, orgId, projectId, "has_runtime", null, false);
    InputSetTemplateResponseDTOPMS responseWithRuntime = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "has_runtime", Collections.singletonList("cistage1"), false, null, false, false);
    String expectedResponse = "pipeline:\n"
        + "  identifier: temppipeline\n"
        + "  properties:\n"
        + "    ci:\n"
        + "      codebase:\n"
        + "        repoName: <+input>\n"
        + "        build: <+input>\n";
    assertThat(responseWithRuntime.getHasInputSets()).isTrue();
    assertThat(responseWithRuntime.getInputSetTemplateYaml()).isEqualTo(expectedResponse);
    List<String> selectedStages = new ArrayList<>();
    selectedStages.add("cistage1");
    selectedStages.add("cistage2");
    responseWithRuntime = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "has_runtime", selectedStages, false, null, false, false);
    assertThat(responseWithRuntime.getHasInputSets()).isTrue();
    assertThat(responseWithRuntime.getInputSetTemplateYaml()).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetTemplateResponseDTOWithSelectedStagesWithoutCodebasePropertiesAndNoRuntimeInput() {
    String pipelineYamlWithRuntime = readFile("pipeline-yaml-multiple-stages-no-runtime.yaml");
    PipelineEntity pipelineEntityWithRuntime = PipelineEntity.builder().yaml(pipelineYamlWithRuntime).build();
    doReturn(Optional.of(pipelineEntityWithRuntime))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, "has_runtime", false, false, false, false, null, false);
    doReturn(true)
        .when(pmsInputSetService)
        .checkForInputSetsForPipeline(accountId, orgId, projectId, "has_runtime", null, false);
    InputSetTemplateResponseDTOPMS responseWithRuntime = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "has_runtime", Collections.singletonList("cistage1"), false, null, false, false);
    String expectedResponse = "pipeline:\n"
        + "  identifier: temppipeline\n"
        + "  properties:\n"
        + "    ci:\n"
        + "      codebase:\n"
        + "        repoName: <+input>\n"
        + "        build: <+input>\n";
    assertThat(responseWithRuntime.getHasInputSets()).isTrue();
    assertThat(responseWithRuntime.getInputSetTemplateYaml()).isEqualTo(expectedResponse);
    List<String> selectedStages = new ArrayList<>();
    selectedStages.add("customstage");
    selectedStages.add("cistage2");
    responseWithRuntime = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "has_runtime", selectedStages, false, null, false, false);
    expectedResponse = "pipeline:\n  identifier: temppipeline\n";
    assertThat(responseWithRuntime.getHasInputSets()).isTrue();
    assertThat(responseWithRuntime.getInputSetTemplateYaml()).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetTemplateResponseDTOWithSelectedStagesWithoutCodebasePropertiesAndPipelineTemplate() {
    String pipelineTemplateYaml = readFile("pipeline-template.yml");
    PipelineEntity pipelineEntityWithRuntime = PipelineEntity.builder().yaml(pipelineTemplateYaml).build();
    doReturn(Optional.of(pipelineEntityWithRuntime))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, "has_runtime", false, false, false, false, null, false);
    String mergedTemplateYaml = readFile("merged-pipeline-template.yml");
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedTemplateYaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(
            accountId, orgId, projectId, pipelineTemplateYaml, "false", HarnessYamlVersion.V0);
    doReturn(true)
        .when(pmsInputSetService)
        .checkForInputSetsForPipeline(accountId, orgId, projectId, "has_runtime", null, false);
    InputSetTemplateResponseDTOPMS responseWithRuntime = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "has_runtime", Collections.singletonList("Stage1"), false, null, false, false);
    String expectedResponse = "pipeline:\n"
        + "  identifier: temppipeline\n"
        + "  template:\n"
        + "    templateInputs:\n"
        + "      properties:\n"
        + "        ci:\n"
        + "          codebase:\n"
        + "            repoName: <+input>\n"
        + "            build: <+input>\n";
    assertThat(responseWithRuntime.getHasInputSets()).isTrue();
    assertThat(responseWithRuntime.getInputSetTemplateYaml()).isEqualTo(expectedResponse);
    List<String> selectedStages = new ArrayList<>();
    selectedStages.add("stage2");
    responseWithRuntime = validateAndMergeHelper.getInputSetTemplateResponseDTO(
        accountId, orgId, projectId, "has_runtime", selectedStages, false, null, false, false);
    expectedResponse = "pipeline:\n  identifier: temppipeline\n";
    assertThat(responseWithRuntime.getHasInputSets()).isTrue();
    assertThat(responseWithRuntime.getInputSetTemplateYaml()).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetMergeInputSetFromPipelineTemplate() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(accountId, orgId, projectId, getScopeInfo());
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      key: <+input>\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      key1: <+input>\n"
        + "      key2: <+input>\n"
        + "      key3: <+input>";
    PipelineEntity pipeline = PipelineEntity.builder().yaml(pipelineYaml).storeType(StoreType.REMOTE).build();
    doReturn(Optional.of(pipeline))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, getScopeInfo(), true);

    String yamlForS1 = "inputSet:\n"
        + "  pipeline:\n"
        + "    stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        key: s1Value1";
    InputSetEntity forS1 = InputSetEntity.builder()
                               .yaml(yamlForS1)
                               .inputSetEntityType(InputSetEntityType.INPUT_SET)
                               .storeType(StoreType.REMOTE)
                               .build();
    doReturn(Optional.of(forS1))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, "forS1", false, false, false, true);

    String yamlForS1AndS2 = "inputSet:\n"
        + "  pipeline:\n"
        + "    stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        key: s1Value2\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        key1: s2Value1\n"
        + "        key2: s2Value2\n"
        + "        key3: s2Value3";
    InputSetEntity forS1AndS2 = InputSetEntity.builder()
                                    .yaml(yamlForS1AndS2)
                                    .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                    .storeType(StoreType.REMOTE)
                                    .build();
    doReturn(Optional.of(forS1AndS2))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, "forS1AndS2", false, false, false, true);

    String yamlForS2 = "inputSet:\n"
        + "  pipeline:\n"
        + "    stages:\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        key1: s2Value2FromForS2\n";
    InputSetEntity forS2 = InputSetEntity.builder()
                               .yaml(yamlForS2)
                               .inputSetEntityType(InputSetEntityType.INPUT_SET)
                               .storeType(StoreType.REMOTE)
                               .build();
    doReturn(Optional.of(forS2))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, "forS2", false, false, false, true);

    JsonNode mergedInputSet = validateAndMergeHelper.getMergeInputSetFromPipelineTemplateWithJsonNode(pipelineId,
        Arrays.asList("forS1", "forS1AndS2", "forS2"), null, null, Collections.singletonList("s2"), false,
        getScopeInfo());
    assertThat(mergedInputSet)
        .isEqualTo(YamlUtils.readAsJsonNode("pipeline:\n"
            + "  stages:\n"
            + "    - stage:\n"
            + "        identifier: s2\n"
            + "        key1: s2Value2FromForS2\n"
            + "        key2: s2Value2\n"
            + "        key3: s2Value3\n"));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetMergeInputSetV1() {
    String inputSetId1 = "inputSet1";
    String inputSetId2 = "inputSet2";
    String inputSetId3 = "inputSet3";
    String inputSetId4 = "inputSet4";
    String overlayId = "overlayId";
    String pipelineYaml = "stages:\n"
        + "  - name: custom\n"
        + "    spec:\n"
        + "      type: Http\n"
        + "      spec:\n"
        + "        url: google.com\n";

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier(inputSetId1)
                                   .yaml("spec:\n"
                                       + "  image: alpine\n")
                                   .harnessVersion(HarnessYamlVersion.V1)
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.INLINE)
                                   .build();

    InputSetEntity inputSet2 = InputSetEntity.builder()
                                   .identifier(inputSetId1)
                                   .yaml("spec:\n"
                                       + "  method: POST\n")
                                   .harnessVersion(HarnessYamlVersion.V1)
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.INLINE)
                                   .build();
    InputSetEntity inputSet3 = InputSetEntity.builder()
                                   .identifier(inputSetId3)
                                   .yaml("spec:\n"
                                       + "  url: google.com\n")
                                   .harnessVersion(HarnessYamlVersion.V1)
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.INLINE)
                                   .build();
    InputSetEntity inputSet4 = InputSetEntity.builder()
                                   .identifier(inputSetId4)
                                   .yaml("spec:\n"
                                       + "  timeout: 10h\n")
                                   .harnessVersion(HarnessYamlVersion.V1)
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.INLINE)
                                   .build();

    InputSetEntity overlay = InputSetEntity.builder()
                                 .identifier(overlayId)
                                 .yaml("inputset:\n"
                                     + "  input_sets:\n"
                                     + "    - inputSet3\n"
                                     + "    - inputSet4")
                                 .harnessVersion(HarnessYamlVersion.V1)
                                 .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                 .storeType(StoreType.INLINE)
                                 .build();

    PipelineEntity pipeline = PipelineEntity.builder()
                                  .harnessVersion(HarnessYamlVersion.V1)
                                  .yaml(pipelineYaml)
                                  .storeType(StoreType.INLINE)
                                  .build();
    doReturn(Optional.of(pipeline))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, getScopeInfo(), true);

    doReturn(Optional.of(inputSet1))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, inputSetId1, false, false, false, true);
    doReturn(Optional.of(inputSet2))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, inputSetId2, false, false, false, true);
    doReturn(Optional.of(inputSet3))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, inputSetId3, false, false, false, true);
    doReturn(Optional.of(inputSet4))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, inputSetId4, false, false, false, true);
    doReturn(Optional.of(overlay))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, overlayId, false, false, false, true);
    List<JsonNode> sanitizedInputSet = new ArrayList<>();
    sanitizedInputSet.add(YamlUtils.readAsJsonNode(inputSet1.getYaml()).get(YAMLFieldNameConstants.SPEC));
    sanitizedInputSet.add(YamlUtils.readAsJsonNode(inputSet2.getYaml()).get(YAMLFieldNameConstants.SPEC));
    sanitizedInputSet.add(YamlUtils.readAsJsonNode(inputSet3.getYaml()).get(YAMLFieldNameConstants.SPEC));
    sanitizedInputSet.add(YamlUtils.readAsJsonNode(inputSet4.getYaml()).get(YAMLFieldNameConstants.SPEC));
    doReturn(sanitizedInputSet).when(pmsInputSetService).getSanitizedInputsFromInputSetV1(any());
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(accountId, orgId, projectId, getScopeInfo());
    JsonNode mergedInputSets = validateAndMergeHelper.getMergeInputSetFromPipelineTemplateWithJsonNode(
        pipelineId, Arrays.asList(inputSetId1, inputSetId2, overlayId), null, null, null, false, getScopeInfo());
    assertThat(mergedInputSets)
        .isEqualTo(YamlUtils.readAsJsonNode("image: alpine\n"
            + "method: POST\n"
            + "url: google.com\n"
            + "timeout: 10h\n"));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetMergeInputSetV1WithFlatInputAndStageIdentifiersIsNoOp() {
    // For V1 pipelines whose runtime inputs are pipeline-level (flat),
    // there is no `pipeline.stages` structure to filter on.
    // The merge API should return the merged inputs unchanged when stageIdentifiers are passed.
    String inputSetId1 = "inputSet1";
    String pipelineYaml = "pipeline:\n"
        + "  inputs:\n"
        + "    image:\n"
        + "      type: string\n"
        + "  stages:\n"
        + "    - id: stage_a\n"
        + "      runtime: shell\n"
        + "    - id: stage_b\n"
        + "      runtime: shell\n";

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier(inputSetId1)
                                   .yaml("spec:\n"
                                       + "  image: alpine\n")
                                   .harnessVersion(HarnessYamlVersion.V1)
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.INLINE)
                                   .build();

    PipelineEntity pipeline = PipelineEntity.builder()
                                  .harnessVersion(HarnessYamlVersion.V1)
                                  .yaml(pipelineYaml)
                                  .storeType(StoreType.INLINE)
                                  .build();
    doReturn(Optional.of(pipeline))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, getScopeInfo(), true);
    doReturn(Optional.of(inputSet1))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, inputSetId1, false, false, false, true);

    List<JsonNode> sanitizedInputSet = new ArrayList<>();
    sanitizedInputSet.add(YamlUtils.readAsJsonNode(inputSet1.getYaml()).get(YAMLFieldNameConstants.SPEC));
    doReturn(sanitizedInputSet).when(pmsInputSetService).getSanitizedInputsFromInputSetV1(any());
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(accountId, orgId, projectId, getScopeInfo());

    JsonNode merged = validateAndMergeHelper.getMergeInputSetFromPipelineTemplateWithJsonNode(pipelineId,
        Collections.singletonList(inputSetId1), null, null, Collections.singletonList("stage_a"), false,
        getScopeInfo());

    // Flat inputs are returned unchanged (no `pipeline.stages` to filter).
    assertThat(merged).isEqualTo(YamlUtils.readAsJsonNode("image: alpine\n"));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetMergeInputSetV1WithStageShapedInputAndStageIdentifiersFilters() {
    // For V1 input sets that are pipeline-shaped (have pipeline.stages), the merge API must
    // return only the selected stages when stageIdentifiers are provided.
    String inputSetId1 = "inputSet1";
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: stage_a\n"
        + "      runtime: shell\n"
        + "    - id: stage_b\n"
        + "      runtime: shell\n";

    String inputSetYaml = "spec:\n"
        + "  pipeline:\n"
        + "    stages:\n"
        + "      - id: stage_a\n"
        + "        with:\n"
        + "          image: alpine\n"
        + "      - id: stage_b\n"
        + "        with:\n"
        + "          image: ubuntu\n";

    InputSetEntity inputSet1 = InputSetEntity.builder()
                                   .identifier(inputSetId1)
                                   .yaml(inputSetYaml)
                                   .harnessVersion(HarnessYamlVersion.V1)
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.INLINE)
                                   .build();

    PipelineEntity pipeline = PipelineEntity.builder()
                                  .harnessVersion(HarnessYamlVersion.V1)
                                  .yaml(pipelineYaml)
                                  .storeType(StoreType.INLINE)
                                  .build();
    doReturn(Optional.of(pipeline))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, getScopeInfo(), true);
    doReturn(Optional.of(inputSet1))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, inputSetId1, false, false, false, true);

    List<JsonNode> sanitizedInputSet = new ArrayList<>();
    sanitizedInputSet.add(YamlUtils.readAsJsonNode(inputSet1.getYaml()).get(YAMLFieldNameConstants.SPEC));
    doReturn(sanitizedInputSet).when(pmsInputSetService).getSanitizedInputsFromInputSetV1(any());
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(accountId, orgId, projectId, getScopeInfo());

    JsonNode merged = validateAndMergeHelper.getMergeInputSetFromPipelineTemplateWithJsonNode(pipelineId,
        Collections.singletonList(inputSetId1), null, null, Collections.singletonList("stage_a"), false,
        getScopeInfo());

    JsonNode expected = YamlUtils.readAsJsonNode("pipeline:\n"
        + "  stages:\n"
        + "    - id: stage_a\n"
        + "      with:\n"
        + "        image: alpine\n");
    assertThat(merged).isEqualTo(expected);
  }

  private ScopeInfo getScopeInfo() {
    return ScopeInfo.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .uniqueId(parentUniqueId)
        .build();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetMergeInputSetFromPipelineTemplateWhenPipelineIsRemoteAndInputSetIsInline() {
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      key: <+input>\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      key1: <+input>";
    PipelineEntity pipeline = PipelineEntity.builder().yaml(pipelineYaml).storeType(StoreType.REMOTE).build();
    doReturn(Optional.of(pipeline))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, getScopeInfo(), true);

    String yamlForS1 = "inputSet:\n"
        + "  pipeline:\n"
        + "    stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        key: s1Value1";
    InputSetEntity forS1 = InputSetEntity.builder()
                               .identifier("s1")
                               .yaml(yamlForS1)
                               .inputSetEntityType(InputSetEntityType.INPUT_SET)
                               .storeType(StoreType.INLINE)
                               .build();
    doReturn(Optional.of(forS1))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, "forS1", false, false, false, true);
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(accountId, orgId, projectId, getScopeInfo());
    assertThatThrownBy(()
                           -> validateAndMergeHelper.getMergeInputSetFromPipelineTemplateWithJsonNode(pipelineId,
                               List.of("forS1"), null, null, Collections.singletonList("s2"), false, getScopeInfo()))
        .isInstanceOf(WingsException.class)
        .hasMessage("Please move the input-set from inline to remote.");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetMergeInputSetFromPipelineTemplateWhenPipelineIsRemoteAndOverlaidInputSetIsInline() {
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      key: <+input>\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      key1: <+input>";
    PipelineEntity pipeline = PipelineEntity.builder().yaml(pipelineYaml).storeType(StoreType.REMOTE).build();
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(accountId, orgId, projectId, getScopeInfo());
    doReturn(Optional.of(pipeline))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, getScopeInfo(), true);

    String yamlForS1 = "inputSet:\n"
        + "  pipeline:\n"
        + "    stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        key: s1Value1";

    InputSetEntity forS1 = InputSetEntity.builder()
                               .identifier("forS1")
                               .yaml(yamlForS1)
                               .inputSetEntityType(InputSetEntityType.INPUT_SET)
                               .storeType(StoreType.INLINE)
                               .build();

    doReturn(Optional.of(forS1))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, "forS1", false, false, false, true);

    String overlayYaml = "overlayInputSet:\n"
        + "  identifier: overlaidIS1\n"
        + "  pipelineIdentifier: " + pipelineId + "\n"
        + "  inputSetReferences:\n"
        + "    - forS1\n";
    InputSetEntity overlaidIS = InputSetEntity.builder()
                                    .identifier("overlaidIS1")
                                    .yaml(overlayYaml)
                                    .harnessVersion(HarnessYamlVersion.V0)
                                    .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                    .storeType(StoreType.REMOTE)
                                    .build();
    doReturn(Optional.of(overlaidIS))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, "overlaidIS1", false, false, false, true);

    assertThatThrownBy(
        ()
            -> validateAndMergeHelper.getMergeInputSetFromPipelineTemplateWithJsonNode(
                pipelineId, List.of("overlaidIS1"), null, null, Collections.singletonList("s2"), false, getScopeInfo()))
        .isInstanceOf(WingsException.class)
        .hasMessage("Please move the input-set from inline to remote.");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testCheckAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferentWhenStoreTypeIsNull() {
    PipelineEntity pipeline = PipelineEntity.builder().build();
    InputSetEntity inputSet = InputSetEntity.builder().build();

    assertDoesNotThrow(
        ()
            -> validateAndMergeHelper.checkAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferent(
                pipeline, inputSet));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testCheckAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferentWhenStoreTypesAreSame() {
    PipelineEntity pipeline = PipelineEntity.builder().storeType(StoreType.REMOTE).build();
    InputSetEntity inputSet = InputSetEntity.builder().storeType(StoreType.REMOTE).build();
    assertDoesNotThrow(
        ()
            -> validateAndMergeHelper.checkAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferent(
                pipeline, inputSet));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testCheckAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferentWhenStoreTypesAreDifferent() {
    PipelineEntity pipeline = PipelineEntity.builder().storeType(StoreType.INLINE).build();
    InputSetEntity inputSet = InputSetEntity.builder().storeType(StoreType.REMOTE).build();
    assertThatThrownBy(
        ()
            -> validateAndMergeHelper.checkAndThrowExceptionWhenPipelineAndInputSetStoreTypesAreDifferent(
                pipeline, inputSet))
        .isInstanceOf(WingsException.class)
        .hasMessage("Please move the input-set from inline to remote.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetMergedYamlFromInputSetReferencesAndRuntimeInputYaml() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(accountId, orgId, projectId, getScopeInfo());
    String base = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      field1: <+input>\n"
        + "      field2: <+input>\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      field1: <+input>\n"
        + "      field2: <+input>\n"
        + "  - stage:\n"
        + "      identifier: s3\n"
        + "      field1: <+input>\n"
        + "      field2: <+input>\n";
    doReturn(Optional.of(PipelineEntity.builder().yaml(base).build()))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, getScopeInfo(), true);
    String lastRuntimeS1S2 = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      field1: lastRuntimeYaml\n"
        + "      field2: lastRuntimeYaml\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      field1: lastRuntimeYaml\n"
        + "      field2: lastRuntimeYaml\n";
    JsonNode merged1 = validateAndMergeHelper.getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(
        getScopeInfo(), pipelineId, null, null, null, Collections.singletonList("s1"),
        YamlUtils.readAsJsonNode(lastRuntimeS1S2), false, false, false, false, null);
    String expectedMerged1 = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        field1: lastRuntimeYaml\n"
        + "        field2: lastRuntimeYaml\n";
    assertThat(merged1).isEqualTo(YamlUtils.readAsJsonNode(expectedMerged1));

    String lastRuntimeS2 = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      field1: <+input>\n"
        + "      field2: lastRuntimeS2\n";
    JsonNode merged2 = validateAndMergeHelper.getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(
        getScopeInfo(), pipelineId, null, null, null, Collections.singletonList("s2"),
        YamlUtils.readAsJsonNode(lastRuntimeS2), false, false, false, false, null);
    String expectedMerged2 = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        field1: <+input>\n"
        + "        field2: lastRuntimeS2\n";
    assertThat(merged2).isEqualTo(YamlUtils.readAsJsonNode(expectedMerged2));

    JsonNode merged3 = validateAndMergeHelper.getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(
        getScopeInfo(), pipelineId, null, null, null, Collections.singletonList("s3"),
        YamlUtils.readAsJsonNode(lastRuntimeS1S2), false, false, false, false, null);
    String expectedMerged3 = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s3\n"
        + "        field1: <+input>\n"
        + "        field2: <+input>\n";
    assertThat(merged3).isEqualTo(YamlUtils.readAsJsonNode(expectedMerged3));

    doReturn(
        Optional.of(
            InputSetEntity.builder().yaml(lastRuntimeS1S2).inputSetEntityType(InputSetEntityType.INPUT_SET).build()))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, "is1", false, false, false, false);
    JsonNode merged4 = validateAndMergeHelper.getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(
        getScopeInfo(), pipelineId, Collections.singletonList("is1"), null, null, Collections.singletonList("s2"),
        YamlUtils.readAsJsonNode(lastRuntimeS2), false, false, false, false, null);
    String expectedMerged4 = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        field1: lastRuntimeYaml\n"
        + "        field2: lastRuntimeS2\n";
    assertThat(merged4).isEqualTo(YamlUtils.readAsJsonNode(expectedMerged4));

    JsonNode merged5 = validateAndMergeHelper.getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(
        getScopeInfo(), pipelineId, Collections.singletonList("is1"), null, null, Collections.singletonList("s1"),
        YamlUtils.readAsJsonNode(lastRuntimeS2), false, false, false, false, null);
    String expectedMerged5 = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        field1: lastRuntimeYaml\n"
        + "        field2: lastRuntimeYaml\n";
    assertThat(merged5).isEqualTo(YamlUtils.readAsJsonNode(expectedMerged5));

    JsonNode merged6 = validateAndMergeHelper.getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(
        getScopeInfo(), pipelineId, Collections.singletonList("is1"), null, null, Collections.singletonList("s3"),
        YamlUtils.readAsJsonNode(lastRuntimeS2), false, false, false, false, null);
    String expectedMerged6 = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s3\n"
        + "        field1: <+input>\n"
        + "        field2: <+input>\n";
    assertThat(merged6).isEqualTo(YamlUtils.readAsJsonNode(expectedMerged6));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithCaching() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    String base = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      field1: <+input>\n"
        + "      field2: <+input>\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      field1: <+input>\n"
        + "      field2: <+input>\n"
        + "  - stage:\n"
        + "      identifier: s3\n"
        + "      field1: <+input>\n"
        + "      field2: <+input>\n";
    doReturn(Optional.of(PipelineEntity.builder().yaml(base).build()))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, true, getScopeInfo(), true);
    String lastRuntimeS1S2 = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      field1: lastRuntimeYaml\n"
        + "      field2: lastRuntimeYaml\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      field1: lastRuntimeYaml\n"
        + "      field2: lastRuntimeYaml\n";
    JsonNode merged1 = validateAndMergeHelper.getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(
        getScopeInfo(), pipelineId, null, null, null, Collections.singletonList("s1"),
        YamlUtils.readAsJsonNode(lastRuntimeS1S2), false, true, false, false, null);
    String expectedMerged1 = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        field1: lastRuntimeYaml\n"
        + "        field2: lastRuntimeYaml\n";
    assertThat(merged1).isEqualTo(YamlUtils.readAsJsonNode(expectedMerged1));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithNoInputSetIdentifiers() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    String base = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      field1: lastRuntimeYaml\n"
        + "      field2: lastRuntimeYaml\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      field1: lastRuntimeYaml\n"
        + "      field2: lastRuntimeYaml\n"
        + "  - stage:\n"
        + "      identifier: s3\n"
        + "      field1: lastRuntimeYaml\n"
        + "      field2: lastRuntimeYaml\n";
    doReturn(Optional.of(PipelineEntity.builder().yaml(base).build()))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, true, getScopeInfo(), true);
    JsonNode merged1 = validateAndMergeHelper.getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(
        getScopeInfo(), pipelineId, null, null, null, Collections.emptyList(), null, false, true, false, false, null);
    assertThat(merged1).isEqualTo(JsonUtils.readTree("{}"));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues() {
    String base = "pipeline:\n"
        + "  variables:\n"
        + "  - name: v1\n"
        + "    type: String\n"
        + "    default: num\n"
        + "    value: <+input>\n"
        + "  - name: v2\n"
        + "    type: String\n"
        + "    default: num\n"
        + "    value: <+input>\n"
        + "  - name: v3\n"
        + "    type: String\n"
        + "    default: num\n"
        + "    value: this one should not be in the template\n";
    String runtime = "pipeline:\n"
        + "  variables:\n"
        + "  - name: v2\n"
        + "    type: String\n"
        + "    default: num\n"
        + "    value: v2val\n";
    doReturn(Optional.of(PipelineEntity.builder().yaml(base).build()))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, getScopeInfo(), true);
    String merged = validateAndMergeHelper.getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(
        getScopeInfo(), pipelineId, null, null, null, null, runtime, false, false, null);
    String expected = "pipeline:\n"
        + "  variables:\n"
        + "    - name: v1\n"
        + "      type: String\n"
        + "      default: num\n"
        + "      value: <+input>\n"
        + "    - name: v2\n"
        + "      type: String\n"
        + "      default: num\n"
        + "      value: v2val\n";
    assertThat(merged).isEqualTo(expected);
  }

  // PIPE-32995: overlay references must be read from YAML, not from the stale DB field
  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testGetInputSetMetadataDTO_V0OverlayUsesYamlReferencesNotDBField() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(accountId, orgId, projectId, getScopeInfo());

    String pipelineYaml = "pipeline:\n"
        + "  identifier: " + pipelineId + "\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      key: <+input>\n";
    PipelineEntity pipeline = PipelineEntity.builder()
                                  .yaml(pipelineYaml)
                                  .harnessVersion(HarnessYamlVersion.V0)
                                  .storeType(StoreType.REMOTE)
                                  .build();
    doReturn(Optional.of(pipeline))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, getScopeInfo(), true);

    // The overlay YAML references "freshInputSet", but its DB field (inputSetReferences) still holds "staleInputSet"
    String overlayYaml = "overlayInputSet:\n"
        + "  identifier: myOverlay\n"
        + "  orgIdentifier: " + orgId + "\n"
        + "  projectIdentifier: " + projectId + "\n"
        + "  pipelineIdentifier: " + pipelineId + "\n"
        + "  inputSetReferences:\n"
        + "    - freshInputSet\n";
    InputSetEntity overlayEntity = InputSetEntity.builder()
                                       .identifier("myOverlay")
                                       .yaml(overlayYaml)
                                       .harnessVersion(HarnessYamlVersion.V0)
                                       .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                       .storeType(StoreType.REMOTE)
                                       // stale DB field intentionally different from YAML
                                       .inputSetReferences(Collections.singletonList("staleInputSet"))
                                       .build();
    doReturn(Optional.of(overlayEntity))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, "myOverlay", false, false, false, false);

    String freshInputSetYaml = "inputSet:\n"
        + "  identifier: freshInputSet\n"
        + "  pipeline:\n"
        + "    identifier: " + pipelineId + "\n"
        + "    stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        key: freshValue\n";
    InputSetEntity freshInputSet = InputSetEntity.builder()
                                       .identifier("freshInputSet")
                                       .yaml(freshInputSetYaml)
                                       .harnessVersion(HarnessYamlVersion.V0)
                                       .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                       .storeType(StoreType.REMOTE)
                                       .build();
    doReturn(Optional.of(freshInputSet))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, "freshInputSet", false, false, false, false);

    InputSetMetadataDTO result = validateAndMergeHelper.getInputSetMetadataDTO(getScopeInfo(), pipelineId,
        Collections.singletonList("myOverlay"), null, null, null, false, false, false, null);

    // freshInputSet's YAML node must appear in the list; staleInputSet must NOT be looked up
    assertThat(result.getInputSetJsonNodeList()).hasSize(1);
    assertThat(result.getInputSetJsonNodeList().get(0)).isEqualTo(YamlUtils.readAsJsonNode(freshInputSetYaml));
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testGetInputSetMetadataDTO_V0OverlayWithRemoteStoreYamlReferencesAreRespected() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    doReturn(getScopeInfo()).when(pmsPipelineServiceHelper).getScopeInfo(accountId, orgId, projectId, getScopeInfo());

    String pipelineYaml = "pipeline:\n"
        + "  identifier: " + pipelineId + "\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      key: <+input>\n";
    PipelineEntity pipeline = PipelineEntity.builder()
                                  .yaml(pipelineYaml)
                                  .harnessVersion(HarnessYamlVersion.V0)
                                  .storeType(StoreType.REMOTE)
                                  .build();
    doReturn(Optional.of(pipeline))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, getScopeInfo(), true);

    // Overlay YAML references two input sets; DB field is empty (simulates first-time remote fetch gap)
    String overlayYaml = "overlayInputSet:\n"
        + "  identifier: myOverlay\n"
        + "  orgIdentifier: " + orgId + "\n"
        + "  projectIdentifier: " + projectId + "\n"
        + "  pipelineIdentifier: " + pipelineId + "\n"
        + "  inputSetReferences:\n"
        + "    - inputSetA\n"
        + "    - inputSetB\n";
    InputSetEntity overlayEntity = InputSetEntity.builder()
                                       .identifier("myOverlay")
                                       .yaml(overlayYaml)
                                       .harnessVersion(HarnessYamlVersion.V0)
                                       .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                       .storeType(StoreType.REMOTE)
                                       .inputSetReferences(Collections.emptyList())
                                       .build();
    doReturn(Optional.of(overlayEntity))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, "myOverlay", false, false, false, false);

    String inputSetAYaml = "inputSet:\n"
        + "  identifier: inputSetA\n"
        + "  pipeline:\n"
        + "    identifier: " + pipelineId + "\n"
        + "    stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        key: valueA\n";
    String inputSetBYaml = "inputSet:\n"
        + "  identifier: inputSetB\n"
        + "  pipeline:\n"
        + "    identifier: " + pipelineId + "\n"
        + "    stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        key: valueB\n";
    InputSetEntity inputSetA = InputSetEntity.builder()
                                   .identifier("inputSetA")
                                   .yaml(inputSetAYaml)
                                   .harnessVersion(HarnessYamlVersion.V0)
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.REMOTE)
                                   .build();
    InputSetEntity inputSetB = InputSetEntity.builder()
                                   .identifier("inputSetB")
                                   .yaml(inputSetBYaml)
                                   .harnessVersion(HarnessYamlVersion.V0)
                                   .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                   .storeType(StoreType.REMOTE)
                                   .build();
    doReturn(Optional.of(inputSetA))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, "inputSetA", false, false, false, false);
    doReturn(Optional.of(inputSetB))
        .when(pmsInputSetService)
        .getWithoutValidations(getScopeInfo(), pipelineId, "inputSetB", false, false, false, false);

    InputSetMetadataDTO result = validateAndMergeHelper.getInputSetMetadataDTO(getScopeInfo(), pipelineId,
        Collections.singletonList("myOverlay"), null, null, null, false, false, false, null);

    assertThat(result.getInputSetJsonNodeList()).hasSize(2);
    assertThat(result.getInputSetJsonNodeList())
        .containsExactlyInAnyOrder(YamlUtils.readAsJsonNode(inputSetAYaml), YamlUtils.readAsJsonNode(inputSetBYaml));
  }

  private String getPipelineYamlWithNoRuntime() {
    return "pipeline:\n"
        + "  name: no runtime\n"
        + "  identifier: no_runtime\n"
        + "  projectIdentifier: namantest\n"
        + "  orgIdentifier: default\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      name: a1\n"
        + "      identifier: a1\n"
        + "      type: Approval\n"
        + "      spec:\n"
        + "        execution:\n"
        + "          steps:\n"
        + "          - step:\n"
        + "              name: Approval\n"
        + "              identifier: approval\n"
        + "              type: HarnessApproval\n"
        + "              timeout: 1d\n"
        + "              spec:\n"
        + "                approvalMessage: Please review\n"
        + "                includePipelineExecutionHistory: true\n"
        + "                approvers:\n"
        + "                  minimumCount: 1\n"
        + "                  disallowPipelineExecutor: false\n"
        + "                  userGroups:\n"
        + "                  - account.Dashboards\n";
  }

  private String getPipelineYamlWithRuntime() {
    return "pipeline:\n"
        + "  name: has runtime\n"
        + "  identifier: has_runtime\n"
        + "  projectIdentifier: namantest\n"
        + "  orgIdentifier: default\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      name: a1\n"
        + "      identifier: a1\n"
        + "      type: Approval\n"
        + "      spec:\n"
        + "        execution:\n"
        + "          steps:\n"
        + "          - step:\n"
        + "              name: Approval\n"
        + "              identifier: approval\n"
        + "              type: HarnessApproval\n"
        + "              timeout: 1d\n"
        + "              spec:\n"
        + "                approvalMessage: <+input>\n"
        + "                includePipelineExecutionHistory: true\n"
        + "                approvers:\n"
        + "                  minimumCount: 1\n"
        + "                  disallowPipelineExecutor: false\n"
        + "                  userGroups:\n"
        + "                  - account.Dashboards\n";
  }

  private String getRuntimeTemplate() {
    return "pipeline:\n"
        + "  identifier: has_runtime\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: a1\n"
        + "        type: Approval\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: approval\n"
        + "                  type: HarnessApproval\n"
        + "                  spec:\n"
        + "                    approvalMessage: <+input>\n";
  }

  private String getRuntimeTemplateWithoutProperties() {
    return "pipeline:\n"
        + "  identifier: temppipeline\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: customstage\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: ShellScript_1\n"
        + "                  type: ShellScript\n"
        + "                  spec:\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: <+input>\n";
  }

  private String getRuntimeTemplateWithProperties() {
    return "pipeline:\n"
        + "  identifier: temppipeline\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: cistage1\n"
        + "        type: CI\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: Run_1\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    command: <+input>\n"
        + "  properties:\n"
        + "    ci:\n"
        + "      codebase:\n"
        + "        repoName: <+input>\n"
        + "        build: <+input>\n";
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetInputSetMetadataDTO_WithInputSetBranch() {
    // Given
    String pipelineYaml = "pipeline:\n  identifier: test\n  stages:\n    - stage:\n        identifier: stage1\n        "
        + "variables:\n        - name: var1\n          value: <+input>";
    String inputSetBranch = "feature-branch";

    PipelineEntity pipelineEntity =
        PipelineEntity.builder().yaml(pipelineYaml).harnessVersion(HarnessYamlVersion.V0).build();

    doReturn(Optional.of(pipelineEntity))
        .when(pmsPipelineService)
        .getPipeline(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            any(ScopeInfo.class), anyBoolean());

    // When
    InputSetMetadataDTO result = validateAndMergeHelper.getInputSetMetadataDTO(
        getScopeInfo(), "pipelineId", Collections.emptyList(), "main", null, null, true, false, false, inputSetBranch);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getPipelineTemplate()).isNotNull();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetInputSetMetadataDTO_WithNullInputSetBranch() {
    // Given
    String pipelineYaml = "pipeline:\n  identifier: test\n  stages:\n    - stage:\n        identifier: stage1\n        "
        + "variables:\n        - name: var1\n          value: <+input>";

    PipelineEntity pipelineEntity =
        PipelineEntity.builder().yaml(pipelineYaml).harnessVersion(HarnessYamlVersion.V0).build();

    doReturn(Optional.of(pipelineEntity))
        .when(pmsPipelineService)
        .getPipeline(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            any(ScopeInfo.class), anyBoolean());

    // When - null inputSetBranch should work without issues
    InputSetMetadataDTO result = validateAndMergeHelper.getInputSetMetadataDTO(
        getScopeInfo(), "pipelineId", Collections.emptyList(), "main", null, null, true, false, false, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getPipelineTemplate()).isNotNull();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues_WithInputSetBranch() {
    // Given
    String pipelineYaml = "pipeline:\n  identifier: test\n  stages:\n    - stage:\n        identifier: stage1\n        "
        + "variables:\n        - name: var1\n          value: <+input>";
    String inputSetBranch = "feature-branch";

    PipelineEntity pipelineEntity =
        PipelineEntity.builder().yaml(pipelineYaml).harnessVersion(HarnessYamlVersion.V0).build();

    doReturn(Optional.of(pipelineEntity))
        .when(pmsPipelineService)
        .getPipeline(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
            any(ScopeInfo.class), anyBoolean());

    // When
    String result = validateAndMergeHelper.getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(
        getScopeInfo(), "pipelineId", Collections.emptyList(), "main", null, null, null, false, false, inputSetBranch);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).contains("pipeline:");
  }
}

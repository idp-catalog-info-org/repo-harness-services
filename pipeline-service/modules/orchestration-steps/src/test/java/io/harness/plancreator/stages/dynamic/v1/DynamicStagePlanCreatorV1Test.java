/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages.dynamic.v1;

import static io.harness.rule.OwnerRule.KUSHAL_DASARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.common.v1.StageElementParametersV1;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class DynamicStagePlanCreatorV1Test extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks private DynamicStagePlanCreatorV1 dynamicStagePlanCreatorV1;

  private PlanCreationContext planCreationContext;
  private YamlField dynamicStageYamlField;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    URL resource = classLoader.getResource("dynamic_stage_v1_git_store.json");
    assertThat(resource).as("test resource 'dynamic_stage_v1_git_store.json' not found on classpath").isNotNull();
    String json = Resources.toString(resource, Charsets.UTF_8);

    String jsonWithUuid = YamlUtils.injectUuid(json);
    YamlField pipelineYamlField = YamlUtils.readTree(jsonWithUuid);
    YamlNode stageNode =
        pipelineYamlField.getNode().getField("spec").getNode().getField("stages").getNode().asArray().get(0);
    dynamicStageYamlField = new YamlField(stageNode);

    planCreationContext =
        PlanCreationContext.builder().currentField(dynamicStageYamlField).globalContext(Collections.emptyMap()).build();

    doReturn(new byte[0]).when(kryoSerializer).asBytes(any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = dynamicStagePlanCreatorV1.getSupportedTypes();
    assertThat(supportedTypes).hasSize(1);
    assertThat(supportedTypes).containsKey(YAMLFieldNameConstants.STAGE);
    assertThat(supportedTypes.get(YAMLFieldNameConstants.STAGE)).contains(YAMLFieldNameConstants.DYNAMIC_STAGE_V1);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions() {
    Set<String> versions = dynamicStagePlanCreatorV1.getSupportedYamlVersions();
    assertThat(versions).containsExactly(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertThat(dynamicStagePlanCreatorV1.getStepType()).isEqualTo(StepSpecTypeConstants.DYNAMIC_STAGE_V1_TYPE);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetFieldObjectWithGitSourceConfig() throws IOException {
    DynamicStageNodeV1 stageNode = dynamicStagePlanCreatorV1.getFieldObject(dynamicStageYamlField);

    assertThat(stageNode).isNotNull();
    assertThat(stageNode.getId()).isEqualTo("dynamicStage");
    assertThat(stageNode.getName()).isEqualTo("dynamicStage");
    assertThat(stageNode.getType()).isEqualTo(YAMLFieldNameConstants.DYNAMIC_STAGE_V1);
    assertThat(stageNode.getDynamicStageConfig()).isNotNull();
    assertThat(stageNode.getDynamicStageConfig().getSourceConfig()).isNotNull();
    assertThat(stageNode.getDynamicStageConfig().getSourceConfig().getUses()).isEqualTo("git");
    assertThat(stageNode.getDynamicStageConfig().getSourceConfig().getWith()).isNotNull();
    assertThat(stageNode.getDynamicStageConfig().getSourceConfig().getWith().getConnector()).isEqualTo("account.git");
    assertThat(stageNode.getDynamicStageConfig().getSourceConfig().getWith().getPath())
        .isEqualTo(".harness/pipeline.yaml");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetFieldObjectWithInlineSource() throws IOException {
    String v1DynamicStageYaml = "{\n"
        + "  \"id\": \"dynamicStage\",\n"
        + "  \"name\": \"Dynamic Stage\",\n"
        + "  \"dynamic\": {\n"
        + "    \"source\": \"dGVzdCB5YW1s\"\n"
        + "  }\n"
        + "}";

    YamlField yamlField = YamlUtils.readTree(v1DynamicStageYaml);
    DynamicStageNodeV1 stageNode = dynamicStagePlanCreatorV1.getFieldObject(yamlField);

    assertThat(stageNode).isNotNull();
    assertThat(stageNode.getId()).isEqualTo("dynamicStage");
    assertThat(stageNode.getName()).isEqualTo("Dynamic Stage");
    assertThat(stageNode.getType()).isEqualTo(YAMLFieldNameConstants.DYNAMIC_STAGE_V1);
    assertThat(stageNode.getDynamicStageConfig()).isNotNull();
    assertThat(stageNode.getDynamicStageConfig().getSource()).isEqualTo("dGVzdCB5YW1s");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodesWithGitSourceConfig() throws IOException {
    DynamicStageNodeV1 stageNode = dynamicStagePlanCreatorV1.getFieldObject(dynamicStageYamlField);
    String stageUuid = dynamicStageYamlField.getNode().getUuid();
    stageNode.setUuid(stageUuid);

    LinkedHashMap<String, PlanCreationResponse> response =
        dynamicStagePlanCreatorV1.createPlanForChildrenNodes(planCreationContext, stageNode);

    assertThat(response).isNotNull();
    assertThat(stageUuid).isNotNull();
    assertThat(response).hasSize(1);
    assertThat(response).containsKey(stageUuid);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetStageParametersWithGitSourceConfig() {
    DynamicGitSourceWithV1 with = DynamicGitSourceWithV1.builder()
                                      .connector("account.git")
                                      .path(".harness/pipeline.yaml")
                                      .branch("main")
                                      .repo("repo")
                                      .build();

    DynamicSourceConfigV1 sourceConfig = DynamicSourceConfigV1.builder().uses("git").with(with).build();

    DynamicStageConfigV1 config = DynamicStageConfigV1.builder().sourceConfig(sourceConfig).build();

    DynamicStageNodeV1 stageNode = new DynamicStageNodeV1(config);
    stageNode.setUuid("test-uuid");
    stageNode.setName("testStage");
    stageNode.setId("testStage");

    StageElementParametersV1 stageParameters =
        dynamicStagePlanCreatorV1.getStageParameters(planCreationContext, stageNode, Collections.emptyList());

    assertThat(stageParameters).isNotNull();
    assertThat(stageParameters.getSpec()).isNotNull();
    assertThat(stageParameters.getSpec()).isInstanceOf(DynamicStageStepParametersV1.class);
    assertThat(stageParameters.getType()).isEqualTo(YAMLFieldNameConstants.DYNAMIC_STAGE_V1);

    DynamicStageStepParametersV1 dynamicSpec = (DynamicStageStepParametersV1) stageParameters.getSpec();
    assertThat(dynamicSpec.getSourceConfig()).isNotNull();
    assertThat(dynamicSpec.getSourceConfig().getUses()).isEqualTo("git");
    assertThat(dynamicSpec.getSourceConfig().getWith()).isNotNull();
    assertThat(dynamicSpec.getSourceConfig().getWith().getConnector()).isEqualTo("account.git");
    assertThat(dynamicSpec.getSourceConfig().getWith().getPath()).isEqualTo(".harness/pipeline.yaml");
    assertThat(dynamicSpec.getSource()).isNull();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetStageParametersWithInlineSource() {
    String inlineSource = "dGVzdCB5YW1s";
    DynamicStageConfigV1 config = DynamicStageConfigV1.builder().source(inlineSource).build();

    DynamicStageNodeV1 stageNode = new DynamicStageNodeV1(config);
    stageNode.setUuid("test-uuid");
    stageNode.setName("testStage");
    stageNode.setId("testStage");

    StageElementParametersV1 stageParameters =
        dynamicStagePlanCreatorV1.getStageParameters(planCreationContext, stageNode, Collections.emptyList());

    assertThat(stageParameters).isNotNull();
    assertThat(stageParameters.getSpec()).isNotNull();
    assertThat(stageParameters.getSpec()).isInstanceOf(DynamicStageStepParametersV1.class);

    DynamicStageStepParametersV1 dynamicSpec = (DynamicStageStepParametersV1) stageParameters.getSpec();
    assertThat(dynamicSpec.getSource()).isEqualTo(inlineSource);
    assertThat(dynamicSpec.getSourceConfig()).isNull();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_WithEmbeddedStagesField() throws IOException {
    String jsonWithStages = "{\n"
        + "  \"id\": \"dynamicStage\",\n"
        + "  \"name\": \"dynamicStage\",\n"
        + "  \"type\": \"__dynamic__\",\n"
        + "  \"dynamic\": {\n"
        + "    \"source\": \"dGVzdCB5YW1s\"\n"
        + "  },\n"
        + "  \"stages\": [\n"
        + "    {\n"
        + "      \"id\": \"innerStage1\",\n"
        + "      \"name\": \"Inner Stage 1\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    String jsonWithUuid = YamlUtils.injectUuid(jsonWithStages);
    YamlField yamlField = YamlUtils.readTree(jsonWithUuid);
    DynamicStageNodeV1 stageNode = dynamicStagePlanCreatorV1.getFieldObject(yamlField);
    stageNode.setUuid(yamlField.getNode().getUuid());

    PlanCreationContext ctx =
        PlanCreationContext.builder().currentField(yamlField).globalContext(Collections.emptyMap()).build();

    doReturn(new byte[0]).when(kryoSerializer).asBytes(any());

    LinkedHashMap<String, PlanCreationResponse> response =
        dynamicStagePlanCreatorV1.createPlanForChildrenNodes(ctx, stageNode);

    assertThat(response).isNotNull();
    assertThat(response).hasSize(1);

    PlanCreationResponse planCreationResponse = response.values().iterator().next();
    assertThat(planCreationResponse.getDependencies().getDependenciesMap()).isNotEmpty();
    assertThat(planCreationResponse.getDependencies().getDependencyMetadataMap()).isNotEmpty();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetStageParameters_SetsChildNodeId_WhenStagesPresent() throws IOException {
    String jsonWithStages = "{\n"
        + "  \"id\": \"dynamicStage\",\n"
        + "  \"name\": \"dynamicStage\",\n"
        + "  \"type\": \"__dynamic__\",\n"
        + "  \"dynamic\": {\n"
        + "    \"source\": \"dGVzdCB5YW1s\"\n"
        + "  },\n"
        + "  \"stages\": [\n"
        + "    {\n"
        + "      \"id\": \"innerStage1\",\n"
        + "      \"name\": \"Inner Stage 1\"\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    String jsonWithUuid = YamlUtils.injectUuid(jsonWithStages);
    YamlField yamlField = YamlUtils.readTree(jsonWithUuid);
    DynamicStageNodeV1 stageNode = dynamicStagePlanCreatorV1.getFieldObject(yamlField);
    stageNode.setUuid(yamlField.getNode().getUuid());

    PlanCreationContext ctx =
        PlanCreationContext.builder().currentField(yamlField).globalContext(Collections.emptyMap()).build();

    StageElementParametersV1 stageParameters =
        dynamicStagePlanCreatorV1.getStageParameters(ctx, stageNode, Collections.emptyList());

    assertThat(stageParameters).isNotNull();
    assertThat(stageParameters.getSpec()).isInstanceOf(DynamicStageStepParametersV1.class);

    DynamicStageStepParametersV1 dynamicSpec = (DynamicStageStepParametersV1) stageParameters.getSpec();
    assertThat(dynamicSpec.getChildNodeId()).isNotNull();

    String expectedChildNodeId = yamlField.getNode().getField(YAMLFieldNameConstants.STAGES).getNode().getUuid();
    assertThat(dynamicSpec.getChildNodeId()).isEqualTo(expectedChildNodeId);
  }
}

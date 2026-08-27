/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages.dynamic;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
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
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class DynamicStagePlanCreatorTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks private DynamicStagePlanCreator dynamicStagePlanCreator;

  private PlanCreationContext planCreationContext;
  private YamlField dynamicStageYamlField;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    URL resource = classLoader.getResource("dynamic_stage_git_store.json");
    String json = Resources.toString(resource, Charsets.UTF_8);

    YamlField pipelineYamlField = YamlUtils.readTree(json);
    YamlNode stageNode = pipelineYamlField.getNode()
                             .getField("pipeline")
                             .getNode()
                             .getField("stages")
                             .getNode()
                             .asArray()
                             .get(0)
                             .getField("stage")
                             .getNode();
    dynamicStageYamlField = new YamlField(stageNode);

    planCreationContext =
        PlanCreationContext.builder().currentField(dynamicStageYamlField).globalContext(Collections.emptyMap()).build();

    // Mock KryoSerializer behavior
    doReturn(new byte[0]).when(kryoSerializer).asBytes(any());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(dynamicStagePlanCreator.getFieldClass()).isEqualTo(DynamicStageNode.class);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, java.util.Set<String>> supportedTypes = dynamicStagePlanCreator.getSupportedTypes();
    assertThat(supportedTypes).hasSize(1);
    assertThat(supportedTypes).containsKey(YAMLFieldNameConstants.STAGE);
    assertThat(supportedTypes.get(YAMLFieldNameConstants.STAGE)).contains(StepSpecTypeConstants.DYNAMIC_STAGE);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithGitSourceConfig() throws IOException {
    DynamicStageNode stageNode = YamlUtils.read(dynamicStageYamlField.getNode().toString(), DynamicStageNode.class);

    PlanCreationResponse response = dynamicStagePlanCreator.createPlanForField(planCreationContext, stageNode);

    assertThat(response).isNotNull();
    assertThat(response.getPlanNode()).isNotNull();

    PlanNode planNode = response.getPlanNode();
    assertThat(planNode.getUuid()).isEqualTo(stageNode.getUuid());
    assertThat(planNode.getName()).isEqualTo(stageNode.getName());
    assertThat(planNode.getIdentifier()).isEqualTo(stageNode.getIdentifier());
    assertThat(planNode.getGroup()).isEqualTo(StepCategory.STAGE.name());

    StepType stepType = planNode.getStepType();
    assertThat(stepType).isEqualTo(StepSpecTypeConstants.DYNAMIC_STAGE_TYPE);

    StepParameters stepParameters = planNode.getStepParameters();
    assertThat(stepParameters).isNotNull();
    assertThat(stepParameters).isInstanceOf(DynamicStageStepParameters.class);

    DynamicStageStepParameters dynamicStepParameters = (DynamicStageStepParameters) stepParameters;
    assertThat(dynamicStepParameters.getSourceConfig()).isNotNull();
    assertThat(dynamicStepParameters.getSourceConfig()).isInstanceOf(GitSourceConfig.class);

    GitSourceConfig gitSourceConfig = (GitSourceConfig) dynamicStepParameters.getSourceConfig();
    assertThat(gitSourceConfig.getSpec()).isNotNull();
    assertThat(gitSourceConfig.getSpec().getConnectorRef()).isNotNull();
    assertThat(gitSourceConfig.getSpec().getFilePath()).isNotNull();
    assertThat(gitSourceConfig.getSpec().getRepoName()).isNotNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithInlineSource() throws IOException {
    // Create a stage node with inline source
    String inlineSourceYaml = "{\n"
        + "  \"stage\": {\n"
        + "    \"name\": \"dynamicStage\",\n"
        + "    \"identifier\": \"dynamicStage\",\n"
        + "    \"type\": \"Dynamic\",\n"
        + "    \"spec\": {\n"
        + "      \"source\": \"dGVzdCB5YW1s\"\n"
        + "    }\n"
        + "  }\n"
        + "}";

    YamlField inlineStageField = YamlUtils.readTree(inlineSourceYaml);
    YamlNode stageNode = inlineStageField.getNode().getField("stage").getNode();
    YamlField stageYamlField = new YamlField(stageNode);

    PlanCreationContext inlineContext =
        PlanCreationContext.builder().currentField(stageYamlField).globalContext(Collections.emptyMap()).build();

    DynamicStageNode dynamicStageNode = YamlUtils.read(stageNode.toString(), DynamicStageNode.class);

    PlanCreationResponse response = dynamicStagePlanCreator.createPlanForField(inlineContext, dynamicStageNode);

    assertThat(response).isNotNull();
    assertThat(response.getPlanNode()).isNotNull();

    StepParameters stepParameters = response.getPlanNode().getStepParameters();
    assertThat(stepParameters).isInstanceOf(DynamicStageStepParameters.class);

    DynamicStageStepParameters dynamicStepParameters = (DynamicStageStepParameters) stepParameters;
    assertThat(dynamicStepParameters.getSource()).isEqualTo("dGVzdCB5YW1s");
    assertThat(dynamicStepParameters.getSourceConfig()).isNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldThrowsExceptionWhenConfigIsNull() {
    DynamicStageNode stageNode = mock(DynamicStageNode.class);
    when(stageNode.getDynamicStageConfig()).thenReturn(null);

    assertThatThrownBy(() -> dynamicStagePlanCreator.createPlanForField(planCreationContext, stageNode))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Dynamic Stage Yaml does not contain spec");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepParameterWithGitSourceConfig() {
    GitConfig gitConfig = GitConfig.builder()
                              .connectorRef(ParameterField.createValueField("account.git"))
                              .filePath(ParameterField.createValueField(".harness/pipeline.yaml"))
                              .branchName(ParameterField.createValueField("main"))
                              .repoName(ParameterField.createValueField("repo"))
                              .build();

    GitSourceConfig gitSourceConfig = GitSourceConfig.builder().spec(gitConfig).build();

    DynamicStageConfig config = DynamicStageConfig.builder().sourceConfig(gitSourceConfig).build();

    // Create DynamicStageNode using setter (no builder available)
    DynamicStageNode stageNode = new DynamicStageNode();
    stageNode.setDynamicStageConfig(config);
    stageNode.setUuid("test-uuid");
    stageNode.setName("testStage");
    stageNode.setIdentifier("testStage");

    PlanCreationResponse response = dynamicStagePlanCreator.createPlanForField(planCreationContext, stageNode);

    DynamicStageStepParameters stepParameters = (DynamicStageStepParameters) response.getPlanNode().getStepParameters();

    assertThat(stepParameters.getSourceConfig()).isNotNull();
    assertThat(stepParameters.getSourceConfig()).isInstanceOf(GitSourceConfig.class);
    assertThat(stepParameters.getSource()).isNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepParameterWithInlineSource() {
    String inlineSource = "dGVzdCB5YW1s";
    DynamicStageConfig config = DynamicStageConfig.builder().source(inlineSource).build();

    // Create DynamicStageNode using setter (no builder available)
    DynamicStageNode stageNode = new DynamicStageNode();
    stageNode.setDynamicStageConfig(config);
    stageNode.setUuid("test-uuid");
    stageNode.setName("testStage");
    stageNode.setIdentifier("testStage");

    PlanCreationResponse response = dynamicStagePlanCreator.createPlanForField(planCreationContext, stageNode);

    DynamicStageStepParameters stepParameters = (DynamicStageStepParameters) response.getPlanNode().getStepParameters();

    assertThat(stepParameters.getSource()).isEqualTo(inlineSource);
    assertThat(stepParameters.getSourceConfig()).isNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldSetsCorrectStepType() throws IOException {
    DynamicStageNode stageNode = YamlUtils.read(dynamicStageYamlField.getNode().toString(), DynamicStageNode.class);

    PlanCreationResponse response = dynamicStagePlanCreator.createPlanForField(planCreationContext, stageNode);

    StepType stepType = response.getPlanNode().getStepType();
    assertThat(stepType).isEqualTo(StepSpecTypeConstants.DYNAMIC_STAGE_TYPE);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldSetsFacilitatorType() throws IOException {
    DynamicStageNode stageNode = YamlUtils.read(dynamicStageYamlField.getNode().toString(), DynamicStageNode.class);

    PlanCreationResponse response = dynamicStagePlanCreator.createPlanForField(planCreationContext, stageNode);

    assertThat(response.getPlanNode().getFacilitatorObtainments()).hasSize(1);
    assertThat(response.getPlanNode().getFacilitatorObtainments().get(0).getType().getType()).isEqualTo("CHILD");
  }
}

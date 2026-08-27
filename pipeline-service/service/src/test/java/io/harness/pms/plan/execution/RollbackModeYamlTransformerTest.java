/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK;
import static io.harness.pms.contracts.plan.ExecutionMode.POST_EXECUTION_ROLLBACK;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.execution.NodeExecution;
import io.harness.execution.dynamic.DynamicExecutionService;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceResponseDTO;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RollbackModeYamlTransformerTest extends CategoryTest {
  RollbackModeYamlTransformer rollbackModeYamlTransformer;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsFeatureFlagService featureFlagService;
  @Mock DynamicExecutionService dynamicExecutionService;
  private static final String ACCOUNT_ID = "accountId";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    rollbackModeYamlTransformer =
        new RollbackModeYamlTransformer(nodeExecutionService, featureFlagService, dynamicExecutionService);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollback() {
    doReturn(Collections.singletonList(NodeExecution.builder().identifier("s1").build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(anySet(), anySet());
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n";
    doReturn(Collections.singletonList(
                 NodeExecution.builder().uuid("uuid").identifier("s1").status(Status.SUCCEEDED).build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());
    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, POST_EXECUTION_ROLLBACK, "ogId", Collections.EMPTY_LIST, HarnessYamlVersion.V0);
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollbackWithInsert() {
    doReturn(Collections.singletonList(NodeExecution.builder().identifier("s1").build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(anySet(), anySet());
    when(featureFlagService.isEnabled(any(), any(FeatureName.class))).thenReturn(true);
    String original = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        - insert:\n"
        + "            name: st1\n"
        + "            identifier: st1\n"
        + "            stages:\n"
        + "              - parallel:\n"
        + "                  - stage:\n"
        + "                      name: s1\n"
        + "                      identifier: s1\n"
        + "                  - stage:\n"
        + "                      name: dsad\n"
        + "                      identifier: dsad\n"
        + "              - stage:\n"
        + "                  name: sa\n"
        + "                  identifier: sa\n"
        + "        - stage:\n"
        + "            name: dfs\n"
        + "            identifier: dfs";
    doReturn(Collections.singletonList(
                 NodeExecution.builder().uuid("uuid").identifier("s1").status(Status.SUCCEEDED).build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());
    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, POST_EXECUTION_ROLLBACK, "ogId", Collections.EMPTY_LIST, HarnessYamlVersion.V0);
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        - insert:\n"
        + "            name: st1\n"
        + "            identifier: st1\n"
        + "            stages:\n"
        + "              - parallel:\n"
        + "                  - stage:\n"
        + "                      name: s1\n"
        + "                      identifier: s1\n"
        + "                  - stage:\n"
        + "                      name: dsad\n"
        + "                      identifier: dsad\n"
        + "        - stage:\n"
        + "            name: dfs\n"
        + "            identifier: dfs\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollbackWithInsertCase2() {
    doReturn(Collections.singletonList(NodeExecution.builder().identifier("s1").build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(anySet(), anySet());
    String original = "pipeline:\n"
        + "  stages:\n"
        + "    - insert:\n"
        + "        identifier: st1\n"
        + "        stages:\n"
        + "          - parallel:\n"
        + "              - stage:\n"
        + "                  identifier: s1\n"
        + "              - stage:\n"
        + "                  identifier: dsad\n"
        + "          - stage:\n"
        + "              identifier: sa";
    doReturn(Collections.singletonList(
                 NodeExecution.builder().uuid("uuid").identifier("s1").status(Status.SUCCEEDED).build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());
    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, POST_EXECUTION_ROLLBACK, "ogId", Collections.EMPTY_LIST, HarnessYamlVersion.V0);
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - insert:\n"
        + "        identifier: st1\n"
        + "        stages:\n"
        + "          - parallel:\n"
        + "              - stage:\n"
        + "                  identifier: s1\n"
        + "              - stage:\n"
        + "                  identifier: dsad\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollbackWithInsertCase3() {
    doReturn(Collections.singletonList(NodeExecution.builder().identifier("s1").build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(anySet(), anySet());
    String original = "pipeline:\n"
        + "  stages:\n"
        + "    - insert:\n"
        + "        identifier: st1\n"
        + "        stages:\n"
        + "          - parallel:\n"
        + "              - stage:\n"
        + "                  identifier: aa\n"
        + "              - stage:\n"
        + "                  identifier: dsad\n"
        + "          - stage:\n"
        + "              identifier: s1\n"
        + "          - stage:\n"
        + "              identifier: sa";
    doReturn(Collections.singletonList(
                 NodeExecution.builder().uuid("uuid").identifier("s1").status(Status.SUCCEEDED).build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());
    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, POST_EXECUTION_ROLLBACK, "ogId", Collections.EMPTY_LIST, HarnessYamlVersion.V0);
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - insert:\n"
        + "        identifier: st1\n"
        + "        stages:\n"
        + "          - stage:\n"
        + "              identifier: s1\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testRollBackForRunningExecution() {
    doReturn(Collections.singletonList(NodeExecution.builder().identifier("s1").build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(anySet(), anySet());
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n";
    doReturn(
        Collections.singletonList(NodeExecution.builder().uuid("uuid").identifier("s1").status(Status.RUNNING).build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());
    assertThatThrownBy(
        ()
            -> rollbackModeYamlTransformer.transformProcessedYaml("accountId", original, POST_EXECUTION_ROLLBACK,
                "ogId", Collections.singletonList("uuid"), HarnessYamlVersion.V0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Stage plan execution [ogId] is still in Progress. Wait for Node Execution [s1] to complete.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPipelineRollback() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n";
    doReturn(Collections.singletonList(RetryStageInfo.builder().identifier("s1").name("s1").build()))
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionIdV2("ogId");
    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, PIPELINE_ROLLBACK, "ogId", Collections.singletonList("uuid"), HarnessYamlVersion.V0);
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPipelineRollbackWithParallelStages() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s1\"\n"
        + "    - stage:\n"
        + "        identifier: \"s2\"\n"
        + "  - stage:\n"
        + "      identifier: \"s3\"\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s4\"\n"
        + "    - stage:\n"
        + "        identifier: \"s5\"\n";
    doReturn(Arrays.asList(RetryStageInfo.builder().identifier("s1").name("s1").build(),
                 RetryStageInfo.builder().identifier("s2").name("s2").build(),
                 RetryStageInfo.builder().identifier("s3").name("s3").build()))
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionIdV2("ogId");
    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, PIPELINE_ROLLBACK, "ogId", Collections.EMPTY_LIST, HarnessYamlVersion.V0);
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s3\n"
        + "    - parallel:\n"
        + "        - stage:\n"
        + "            identifier: s1\n"
        + "        - stage:\n"
        + "            identifier: s2\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPipelineRollbackWithParallelStagesWithInject() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: wqw\n"
        + "    - insert:\n"
        + "        identifier: inject1\n"
        + "        name: inject1\n"
        + "        stages:\n"
        + "          - stage:\n"
        + "              identifier: fdsf1\n"
        + "          - parallel:\n"
        + "              - stage:\n"
        + "                  identifier: dsa\n"
        + "              - stage:\n"
        + "                  identifier: dsadsa\n"
        + "          - stage:\n"
        + "              identifier: fdsf2\n"
        + "          - stage:\n"
        + "              identifier: sda2";
    doReturn(Arrays.asList(RetryStageInfo.builder().identifier("wqw").name("wqw").build(),
                 RetryStageInfo.builder().identifier("fdsf1").name("fdsf1").build(),
                 RetryStageInfo.builder().identifier("dsa").name("dsa").build()))
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionIdV2("ogId");
    when(featureFlagService.isEnabled(any(), any(FeatureName.class))).thenReturn(true);
    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, PIPELINE_ROLLBACK, "ogId", Collections.EMPTY_LIST, HarnessYamlVersion.V0);
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - insert:\n"
        + "        identifier: inject1\n"
        + "        name: inject1\n"
        + "        stages:\n"
        + "          - parallel:\n"
        + "              - stage:\n"
        + "                  identifier: dsa\n"
        + "              - stage:\n"
        + "                  identifier: dsadsa\n"
        + "          - stage:\n"
        + "              identifier: fdsf1\n"
        + "    - stage:\n"
        + "        identifier: wqw\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPipelineRollbackWithOneParallelStageSkipped() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s1\"\n"
        + "    - stage:\n"
        + "        identifier: \"s2\"\n";
    doReturn(List.of(RetryStageInfo.builder().identifier("s2").name("s2").build()))
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionIdV2("ogId");
    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, PIPELINE_ROLLBACK, "ogId", Collections.EMPTY_LIST, HarnessYamlVersion.V0);
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        - stage:\n"
        + "            identifier: s1\n"
        + "        - stage:\n"
        + "            identifier: s2\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleSerialAndParallelStage() throws IOException {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s1\"\n"
        + "    - stage:\n"
        + "        identifier: \"s2\"\n"
        + "  - stage:\n"
        + "        identifier: s3\n";
    YamlField yamlField = YamlUtils.readTree(original);
    JsonNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(original).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unable to transform processed YAML while executing in Rollback Mode");
    }
    ArrayNode stagesList =
        (ArrayNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);

    // Handle Parallel Stage
    ArrayNode reversedStages = stagesList.deepCopy().removeAll();
    rollbackModeYamlTransformer.handleParallelStages(
        yamlField.fromYamlPath("pipeline").fromYamlPath("stages").getNode().asArray().get(0).getCurrJsonNode(),
        Collections.singletonList("s1"), reversedStages, HarnessYamlVersion.V0, false);
    assertThat(reversedStages.size()).isEqualTo(1);
    assertThat(reversedStages.get(0))
        .isEqualTo(pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES).get(0));

    // Handle Serial Stage
    rollbackModeYamlTransformer.handleSerialStage(
        yamlField.fromYamlPath("pipeline").fromYamlPath("stages").getNode().asArray().get(1).getCurrJsonNode(),
        Collections.singletonList("s3"), reversedStages);
    assertThat(reversedStages.size()).isEqualTo(2);
    assertThat(reversedStages.get(1))
        .isEqualTo(pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES).get(1));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlWithParallelStagesForPostExecutionRollback() {
    doReturn(Collections.singletonList(NodeExecution.builder().identifier("s1").build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(anySet(), anySet());
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s2\"\n"
        + "    - stage:\n"
        + "        identifier: \"s3\"\n"
        + "    - stage:\n"
        + "        identifier: \"s4\"\n";
    doReturn(List.of(NodeExecution.builder().uuid("uuid").identifier("s1").status(Status.SUCCEEDED).build(),
                 NodeExecution.builder().uuid("uuid2").identifier("s2").status(Status.SUCCEEDED).build(),
                 NodeExecution.builder().uuid("uuid4").identifier("s4").status(Status.SUCCEEDED).build(),
                 NodeExecution.builder()
                     .uuid("uuid3")
                     .identifier("s3")
                     .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                     .status(Status.RUNNING)
                     .build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());
    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, POST_EXECUTION_ROLLBACK, "ogId", Collections.EMPTY_LIST, HarnessYamlVersion.V0);
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        - stage:\n"
        + "            identifier: s2\n"
        + "        - stage:\n"
        + "            identifier: s4\n"
        + "    - stage:\n"
        + "        identifier: s1\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleParallelStageForPostExecutionRollback() throws IOException {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s1\"\n"
        + "    - stage:\n"
        + "        identifier: \"s2\"\n"
        + "  - stage:\n"
        + "        identifier: s3\n";
    YamlField yamlField = YamlUtils.readTree(original);
    JsonNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(original).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unable to transform processed YAML while executing in Rollback Mode");
    }
    ArrayNode stagesList =
        (ArrayNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);

    ArrayNode reversedStages = stagesList.deepCopy().removeAll();
    rollbackModeYamlTransformer.handleParallelStagesForPostExecutionRollback(
        yamlField.fromYamlPath("pipeline").fromYamlPath("stages").getNode().asArray().get(0).getCurrJsonNode(),
        Collections.singletonList("s1"), reversedStages, false);
    assertThat(reversedStages.size()).isEqualTo(1);
    assertThat(reversedStages.get(0).get(YAMLFieldNameConstants.PARALLEL).size()).isEqualTo(1);
    assertThat(reversedStages.get(0).get(YAMLFieldNameConstants.PARALLEL).get(0))
        .isEqualTo(pipelineNode.get(YAMLFieldNameConstants.PIPELINE)
                       .get(YAMLFieldNameConstants.STAGES)
                       .get(0)
                       .get(YAMLFieldNameConstants.PARALLEL)
                       .get(0));

    reversedStages = stagesList.deepCopy().removeAll();
    rollbackModeYamlTransformer.handleParallelStagesForPostExecutionRollback(
        yamlField.fromYamlPath("pipeline").fromYamlPath("stages").getNode().asArray().get(0).getCurrJsonNode(),
        Collections.singletonList("s3"), reversedStages, false);
    assertThat(reversedStages.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHandleGroupStages() throws IOException {
    // Setup YAML with group stages
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - group:\n"
        + "      name: group1\n"
        + "      identifier: group1\n"
        + "      stages:\n"
        + "      - stage:\n"
        + "          identifier: \"g1s1\"\n"
        + "      - stage:\n"
        + "          identifier: \"g1s2\"\n";

    YamlField yamlField = YamlUtils.readTree(original);
    JsonNode pipelineNode = yamlField.getNode().getCurrJsonNode();
    JsonNode groupNode = pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES).get(0);

    // Test with g1s1 in executed stages
    ArrayNode reversedStages =
        ((ArrayNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES))
            .deepCopy()
            .removeAll();
    List<String> executedStages = Collections.singletonList("g1s1");

    rollbackModeYamlTransformer.handleGroupStages(
        groupNode, executedStages, reversedStages, HarnessYamlVersion.V1, true);

    // Verify results
    assertThat(reversedStages.size()).isEqualTo(1);
    assertThat(reversedStages.get(0).get(YAMLFieldNameConstants.GROUP).get(YAMLFieldNameConstants.IDENTIFIER).asText())
        .isEqualTo("group1");
    assertThat(reversedStages.get(0).get(YAMLFieldNameConstants.GROUP).get(YAMLFieldNameConstants.STAGES).size())
        .isEqualTo(1);
    assertThat(reversedStages.get(0)
                   .get(YAMLFieldNameConstants.GROUP)
                   .get(YAMLFieldNameConstants.STAGES)
                   .get(0)
                   .get(YAMLFieldNameConstants.STAGE)
                   .get(YAMLFieldNameConstants.IDENTIFIER)
                   .asText())
        .isEqualTo("g1s1");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testTransformDagStagesForRollback_LinearChain() throws IOException {
    // S1→S2→S3 (S2 depends_on [S1], S3 depends_on [S2]), all executed
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"S1\"\n"
        + "  - stage:\n"
        + "      identifier: \"S2\"\n"
        + "      dependsOn:\n"
        + "      - \"S1\"\n"
        + "  - stage:\n"
        + "      identifier: \"S3\"\n"
        + "      dependsOn:\n"
        + "      - \"S2\"\n";

    JsonNode pipelineNode = YamlUtils.readTree(original).getNode().getCurrJsonNode();
    ArrayNode stagesList =
        (ArrayNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);

    List<String> executedStages = Arrays.asList("S1", "S2", "S3");
    ArrayNode result = rollbackModeYamlTransformer.transformDagStagesForRollback(stagesList, executedStages);

    // Verify: S1 depends_on [S2], S2 depends_on [S3], S3 has no depends_on
    assertThat(result.size()).isEqualTo(3);
    for (int i = 0; i < result.size(); i++) {
      JsonNode stageNode = result.get(i).get(YAMLFieldNameConstants.STAGE);
      String id = stageNode.get(YAMLFieldNameConstants.IDENTIFIER).asText();
      if ("S1".equals(id)) {
        assertThat(stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)).isTrue();
        assertThat(stageNode.get(YAMLFieldNameConstants.DEPENDS_ON).get(0).asText()).isEqualTo("S2");
      } else if ("S2".equals(id)) {
        assertThat(stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)).isTrue();
        assertThat(stageNode.get(YAMLFieldNameConstants.DEPENDS_ON).get(0).asText()).isEqualTo("S3");
      } else if ("S3".equals(id)) {
        assertThat(stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)).isFalse();
      }
    }
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testTransformDagStagesForRollback_FanOut() throws IOException {
    // S3 depends_on [S1, S2], all executed
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"S1\"\n"
        + "  - stage:\n"
        + "      identifier: \"S2\"\n"
        + "  - stage:\n"
        + "      identifier: \"S3\"\n"
        + "      dependsOn:\n"
        + "      - \"S1\"\n"
        + "      - \"S2\"\n";

    JsonNode pipelineNode = YamlUtils.readTree(original).getNode().getCurrJsonNode();
    ArrayNode stagesList =
        (ArrayNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);

    List<String> executedStages = Arrays.asList("S1", "S2", "S3");
    ArrayNode result = rollbackModeYamlTransformer.transformDagStagesForRollback(stagesList, executedStages);

    // Reversed: S1 depends_on [S3], S2 depends_on [S3], S3 has no depends_on
    assertThat(result.size()).isEqualTo(3);
    for (int i = 0; i < result.size(); i++) {
      JsonNode stageNode = result.get(i).get(YAMLFieldNameConstants.STAGE);
      String id = stageNode.get(YAMLFieldNameConstants.IDENTIFIER).asText();
      if ("S1".equals(id)) {
        assertThat(stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)).isTrue();
        assertThat(stageNode.get(YAMLFieldNameConstants.DEPENDS_ON).get(0).asText()).isEqualTo("S3");
      } else if ("S2".equals(id)) {
        assertThat(stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)).isTrue();
        assertThat(stageNode.get(YAMLFieldNameConstants.DEPENDS_ON).get(0).asText()).isEqualTo("S3");
      } else if ("S3".equals(id)) {
        assertThat(stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)).isFalse();
      }
    }
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testTransformDagStagesForRollback_FilterNonExecuted() throws IOException {
    // S1→S2→S3, only S1 and S2 executed
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"S1\"\n"
        + "  - stage:\n"
        + "      identifier: \"S2\"\n"
        + "      dependsOn:\n"
        + "      - \"S1\"\n"
        + "  - stage:\n"
        + "      identifier: \"S3\"\n"
        + "      dependsOn:\n"
        + "      - \"S2\"\n";

    JsonNode pipelineNode = YamlUtils.readTree(original).getNode().getCurrJsonNode();
    ArrayNode stagesList =
        (ArrayNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);

    List<String> executedStages = Arrays.asList("S1", "S2");
    ArrayNode result = rollbackModeYamlTransformer.transformDagStagesForRollback(stagesList, executedStages);

    // S3 should be excluded, only S1 and S2 remain with reversed deps
    assertThat(result.size()).isEqualTo(2);
    for (int i = 0; i < result.size(); i++) {
      JsonNode stageNode = result.get(i).get(YAMLFieldNameConstants.STAGE);
      String id = stageNode.get(YAMLFieldNameConstants.IDENTIFIER).asText();
      assertThat(id).isIn("S1", "S2");
      if ("S1".equals(id)) {
        assertThat(stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)).isTrue();
        assertThat(stageNode.get(YAMLFieldNameConstants.DEPENDS_ON).get(0).asText()).isEqualTo("S2");
      } else if ("S2".equals(id)) {
        assertThat(stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)).isFalse();
      }
    }
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPipelineRollback_DagPipeline_UsesReversedDependencies() {
    // Product scenario: Pipeline rollback on a DAG pipeline.
    // transformProcessedYamlForPipelineRollbackMode should auto-detect DAG from depends_on in YAML
    // and route to filterProcessedYamlForDagRollback instead of the standard reverse-order transformation.
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"S1\"\n"
        + "  - stage:\n"
        + "      identifier: \"S2\"\n"
        + "      dependsOn:\n"
        + "      - \"S1\"\n";

    // Mock executed stages from original execution
    doReturn(Arrays.asList(RetryStageInfo.builder().identifier("S1").name("S1").build(),
                 RetryStageInfo.builder().identifier("S2").name("S2").build()))
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionIdV2("ogId");

    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, PIPELINE_ROLLBACK, "ogId", Collections.emptyList(), HarnessYamlVersion.V0);

    // DAG rollback reverses dependencies: S1 should now depend on S2
    JsonNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(transformedYaml).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unable to parse result YAML");
    }
    ArrayNode stages = (ArrayNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);
    assertThat(stages.size()).isEqualTo(2);
    for (int i = 0; i < stages.size(); i++) {
      JsonNode stageNode = stages.get(i).get(YAMLFieldNameConstants.STAGE);
      String id = stageNode.get(YAMLFieldNameConstants.IDENTIFIER).asText();
      if ("S1".equals(id)) {
        // S1 was a root node, after reversal it depends on S2
        assertThat(stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)).isTrue();
        assertThat(stageNode.get(YAMLFieldNameConstants.DEPENDS_ON).get(0).asText()).isEqualTo("S2");
      } else if ("S2".equals(id)) {
        // S2 depended on S1, after reversal it becomes a root (no depends_on)
        assertThat(stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)).isFalse();
      }
    }
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollback_DagPipeline_UsesReversedDependencies() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"S1\"\n"
        + "  - stage:\n"
        + "      identifier: \"S2\"\n"
        + "      dependsOn:\n"
        + "      - \"S1\"\n";

    doReturn(Arrays.asList(NodeExecution.builder().uuid("uuid1").identifier("S1").status(Status.SUCCEEDED).build(),
                 NodeExecution.builder().uuid("uuid2").identifier("S2").status(Status.SUCCEEDED).build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)).thenReturn(true);

    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, POST_EXECUTION_ROLLBACK, "ogId", Collections.emptyList(), HarnessYamlVersion.V0, true);

    JsonNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(transformedYaml).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unable to parse result YAML");
    }
    ArrayNode stages = (ArrayNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);
    assertThat(stages.size()).isEqualTo(2);
    for (int i = 0; i < stages.size(); i++) {
      JsonNode stageNode = stages.get(i).get(YAMLFieldNameConstants.STAGE);
      String id = stageNode.get(YAMLFieldNameConstants.IDENTIFIER).asText();
      if ("S1".equals(id)) {
        assertThat(stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)).isTrue();
        assertThat(stageNode.get(YAMLFieldNameConstants.DEPENDS_ON).get(0).asText()).isEqualTo("S2");
      } else if ("S2".equals(id)) {
        assertThat(stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)).isFalse();
      }
    }
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollback_ComplexDagFanOutMerge_InstanceRollback() {
    // Product scenario: fan-out / merge DAG — S1 and S2 run in parallel, S3 waits on both, S4 waits on S3.
    // User triggers post-prod instance rollback from a deploy in S2 (parallel branch, not the reversed DAG root).
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"S1\"\n"
        + "  - stage:\n"
        + "      identifier: \"S2\"\n"
        + "  - stage:\n"
        + "      identifier: \"S3\"\n"
        + "      dependsOn:\n"
        + "      - \"S1\"\n"
        + "      - \"S2\"\n"
        + "  - stage:\n"
        + "      identifier: \"S4\"\n"
        + "      dependsOn:\n"
        + "      - \"S3\"\n";

    doReturn(Arrays.asList(NodeExecution.builder().uuid("uuid1").identifier("S1").status(Status.SUCCEEDED).build(),
                 NodeExecution.builder().uuid("uuid2").identifier("S2").status(Status.SUCCEEDED).build(),
                 NodeExecution.builder().uuid("uuid3").identifier("S3").status(Status.SUCCEEDED).build(),
                 NodeExecution.builder().uuid("uuid4").identifier("S4").status(Status.SUCCEEDED).build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)).thenReturn(true);

    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, POST_EXECUTION_ROLLBACK, "ogId", Collections.emptyList(), HarnessYamlVersion.V0, true);

    JsonNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(transformedYaml).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unable to parse result YAML");
    }
    ArrayNode stages = (ArrayNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);
    assertThat(stages.size()).isEqualTo(4);

    Map<String, JsonNode> stageById = new HashMap<>();
    for (int i = 0; i < stages.size(); i++) {
      JsonNode stageNode = stages.get(i).get(YAMLFieldNameConstants.STAGE);
      stageById.put(stageNode.get(YAMLFieldNameConstants.IDENTIFIER).asText(), stageNode);
    }

    // Reversed graph: S4 is the rollback root, S3 depends on S4, S1/S2 depend on S3.
    assertThat(readDependsOn(stageById.get("S4"))).isEmpty();
    assertThat(readDependsOn(stageById.get("S3"))).containsExactly("S4");
    assertThat(readDependsOn(stageById.get("S1"))).containsExactly("S3");
    assertThat(readDependsOn(stageById.get("S2"))).containsExactly("S3");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollback_DagGatedWhenEnableDagFalse() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"S1\"\n"
        + "  - stage:\n"
        + "      identifier: \"S2\"\n"
        + "      dependsOn:\n"
        + "      - \"S1\"\n";

    doReturn(Arrays.asList(NodeExecution.builder().uuid("uuid1").identifier("S1").status(Status.SUCCEEDED).build(),
                 NodeExecution.builder().uuid("uuid2").identifier("S2").status(Status.SUCCEEDED).build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)).thenReturn(true);

    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, POST_EXECUTION_ROLLBACK, "ogId", Collections.emptyList(), HarnessYamlVersion.V0, false);

    JsonNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(transformedYaml).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unable to parse result YAML");
    }
    ArrayNode stages = (ArrayNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);
    // Sequential post-prod path reverses stage order when DAG is disabled.
    assertThat(stages.get(0).get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText())
        .isEqualTo("S2");
    assertThat(stages.get(1).get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText())
        .isEqualTo("S1");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollback_DagGatedWhenFeatureFlagDisabled() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"S1\"\n"
        + "  - stage:\n"
        + "      identifier: \"S2\"\n"
        + "      dependsOn:\n"
        + "      - \"S1\"\n";

    doReturn(Arrays.asList(NodeExecution.builder().uuid("uuid1").identifier("S1").status(Status.SUCCEEDED).build(),
                 NodeExecution.builder().uuid("uuid2").identifier("S2").status(Status.SUCCEEDED).build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))
        .thenReturn(false);

    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, POST_EXECUTION_ROLLBACK, "ogId", Collections.emptyList(), HarnessYamlVersion.V0, true);

    JsonNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(transformedYaml).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unable to parse result YAML");
    }
    ArrayNode stages = (ArrayNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);
    assertThat(stages.get(0).get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText())
        .isEqualTo("S2");
  }

  private static List<String> readDependsOn(JsonNode stageNode) {
    if (!stageNode.has(YAMLFieldNameConstants.DEPENDS_ON)) {
      return Collections.emptyList();
    }
    List<String> dependencies = new ArrayList<>();
    stageNode.get(YAMLFieldNameConstants.DEPENDS_ON).forEach(dep -> dependencies.add(dep.asText()));
    return dependencies;
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlWithRequiredStageIdentifiersV1YamlGroup() {
    // Test V1 YAML with group stages
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - group:\n"
        + "      name: group1\n"
        + "      identifier: group1\n"
        + "      stages:\n"
        + "      - stage:\n"
        + "          identifier: \"g1s1\"\n"
        + "      - stage:\n"
        + "          identifier: \"g1s2\"\n";

    List<String> requiredStageIds = Collections.singletonList("g1s1");

    String transformedYaml = rollbackModeYamlTransformer.filterProcessedYamlWithRequiredStageIdentifiers(
        "planExecutionId", original, requiredStageIds, HarnessYamlVersion.V1);

    // In the result, we should only have g1s1 inside the group
    assertThat(transformedYaml).contains("g1s1");
    assertThat(transformedYaml).doesNotContain("g1s2");
  }

  // ==================== V1 (Unified) POST_EXECUTION_ROLLBACK regression tests ====================
  // The fixtures below verify Gap A — V1 stage `id`, V1 stage-level `parallel: { stages: [...] }`, and
  // V1 stage-level `group: { stages: [...] }` are correctly filtered for POST_EXECUTION_ROLLBACK.
  // V0 fixtures above MUST continue to pass: that is the no-regression contract.

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollbackMode_V1_SerialStage() {
    // V1 pipeline: bare stage objects identified by `id`. Only one stage was executed; PPR keeps that one.
    String original = "pipeline:\n"
        + "  stages:\n"
        + "    - id: cd1\n"
        + "      name: cd1\n"
        + "    - id: cd2\n"
        + "      name: cd2\n";
    doReturn(Arrays.asList(NodeExecution.builder()
                               .uuid("ne-cd1")
                               .identifier("cd1")
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                               .status(Status.SUCCEEDED)
                               .build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());

    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml("accountId", original,
        POST_EXECUTION_ROLLBACK, "ogId", Collections.singletonList("ne-cd1"), HarnessYamlVersion.V1);

    // Only cd1 is preserved. cd2 was not executed in this PPR call -> dropped.
    assertThat(transformedYaml).contains("cd1");
    assertThat(transformedYaml).doesNotContain("cd2");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollbackMode_V1_ParallelStages_PrunesUnexecuted() {
    // V1 pipeline with stage-level parallel. parallel: { stages: [...] }.
    String original = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        stages:\n"
        + "          - id: p1\n"
        + "            name: p1\n"
        + "          - id: p2\n"
        + "            name: p2\n";
    // p1 executed, p2 was not.
    doReturn(Arrays.asList(NodeExecution.builder()
                               .uuid("ne-p1")
                               .identifier("p1")
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                               .status(Status.SUCCEEDED)
                               .build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());

    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml("accountId", original,
        POST_EXECUTION_ROLLBACK, "ogId", Collections.singletonList("ne-p1"), HarnessYamlVersion.V1);

    // Parallel wrapper kept (V1 shape preserved: parallel.stages), only p1 inside.
    assertThat(transformedYaml).contains("parallel");
    assertThat(transformedYaml).contains("p1");
    assertThat(transformedYaml).doesNotContain("p2");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollbackMode_V1_GroupStages_PrunesUnexecuted() {
    // V1 pipeline with stage-level group. group: { stages: [...] }.
    String original = "pipeline:\n"
        + "  stages:\n"
        + "    - group:\n"
        + "        stages:\n"
        + "          - id: g1\n"
        + "            name: g1\n"
        + "          - id: g2\n"
        + "            name: g2\n";
    // Only g2 executed.
    doReturn(Arrays.asList(NodeExecution.builder()
                               .uuid("ne-g2")
                               .identifier("g2")
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                               .status(Status.SUCCEEDED)
                               .build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());

    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml("accountId", original,
        POST_EXECUTION_ROLLBACK, "ogId", Collections.singletonList("ne-g2"), HarnessYamlVersion.V1);

    // Group wrapper kept, only g2 inside.
    assertThat(transformedYaml).contains("group");
    assertThat(transformedYaml).contains("g2");
    assertThat(transformedYaml).doesNotContain("g1");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollbackMode_V1_GroupWithNoExecutedChild_DropsGroup() {
    // V1 group with only un-executed children -> the whole group wrapper is dropped (prune semantics).
    String original = "pipeline:\n"
        + "  stages:\n"
        + "    - group:\n"
        + "        stages:\n"
        + "          - id: g1\n"
        + "            name: g1\n"
        + "    - id: cd1\n"
        + "      name: cd1\n";
    // Only cd1 executed; g1 inside group not executed.
    doReturn(Arrays.asList(NodeExecution.builder()
                               .uuid("ne-cd1")
                               .identifier("cd1")
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                               .status(Status.SUCCEEDED)
                               .build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());

    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml("accountId", original,
        POST_EXECUTION_ROLLBACK, "ogId", Collections.singletonList("ne-cd1"), HarnessYamlVersion.V1);

    assertThat(transformedYaml).contains("cd1");
    assertThat(transformedYaml).doesNotContain("group");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollbackMode_V0_RegressionFence() {
    // Regression fence: same V0 fixture used by testTransformProcessedYamlForPostExecutionRollbackMode above.
    // V1 changes must not affect V0 output.
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "    - stage:\n"
        + "        identifier: s3\n"
        + "    - stage:\n"
        + "        identifier: s4\n";
    doReturn(Arrays.asList(NodeExecution.builder()
                               .uuid("ne-s1")
                               .identifier("s1")
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                               .status(Status.SUCCEEDED)
                               .build(),
                 NodeExecution.builder()
                     .uuid("ne-s2")
                     .identifier("s2")
                     .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                     .status(Status.SUCCEEDED)
                     .build(),
                 NodeExecution.builder()
                     .uuid("ne-s4")
                     .identifier("s4")
                     .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                     .status(Status.SUCCEEDED)
                     .build(),
                 // s3 still RUNNING in a STRATEGY -> still considered executed
                 NodeExecution.builder()
                     .uuid("ne-s3")
                     .identifier("s3")
                     .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                     .status(Status.RUNNING)
                     .build()))
        .when(nodeExecutionService)
        .fetchStageExecutionsWithProjection(eq("ogId"), anySet());

    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, POST_EXECUTION_ROLLBACK, "ogId", Collections.EMPTY_LIST, HarnessYamlVersion.V0);
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        - stage:\n"
        + "            identifier: s2\n"
        + "        - stage:\n"
        + "            identifier: s4\n"
        + "    - stage:\n"
        + "        identifier: s1\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleDynamicStagesV1_FiltersPPRollback() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "    - id: dynStage\n"
        + "      name: dynStage\n"
        + "      type: dynamic\n";

    String processedYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: s1\n"
        + "      name: s1\n"
        + "    - id: s2\n"
        + "      name: s2\n"
        + "    - id: s3\n"
        + "      name: s3\n";

    doReturn(Optional.of(DynamicExecutionInstanceResponseDTO.builder().processedYaml(processedYaml).build()))
        .when(dynamicExecutionService)
        .getByPlanExecutionIdAndIdentifier("ogId", "dynStage");

    doReturn(Arrays.asList(RetryStageInfo.builder().identifier("s1").name("s1").build(),
                 RetryStageInfo.builder().identifier("s2").name("s2").build()))
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionIdV2("ogId");

    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, PIPELINE_ROLLBACK, "ogId", Collections.emptyList(), HarnessYamlVersion.V1);

    assertThat(transformedYaml).contains("s1");
    assertThat(transformedYaml).contains("s2");
    assertThat(transformedYaml).doesNotContain("s3");

    // Verify reversed order: s2 before s1 (pipeline rollback reverses stage order)
    int s2Index = transformedYaml.indexOf("s2");
    int s1Index = transformedYaml.indexOf("s1");
    assertThat(s2Index).as("s2 should appear before s1 in reversed rollback order").isLessThan(s1Index);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleDynamicStagesV1_EmptyInstance_DropsStage() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "    - id: dynStage\n"
        + "      name: dynStage\n"
        + "      type: dynamic\n"
        + "    - id: s3\n"
        + "      name: s3\n";

    doReturn(Optional.empty()).when(dynamicExecutionService).getByPlanExecutionIdAndIdentifier("ogId", "dynStage");

    doReturn(Arrays.asList(RetryStageInfo.builder().identifier("s3").name("s3").build()))
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionIdV2("ogId");

    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(
        "accountId", original, PIPELINE_ROLLBACK, "ogId", Collections.emptyList(), HarnessYamlVersion.V1);

    assertThat(transformedYaml).contains("s3");
    assertThat(transformedYaml).doesNotContain("dynStage");
  }
}
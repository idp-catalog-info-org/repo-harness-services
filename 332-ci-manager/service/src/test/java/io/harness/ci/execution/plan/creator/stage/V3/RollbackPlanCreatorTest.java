/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage.V3;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.rollback.CombinedRollbackStep;
import io.harness.steps.rollback.RollbackOptionalChildChainStep;
import io.harness.steps.rollback.RollbackOptionalChildChainStepParameters;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RollbackPlanCreatorTest extends CategoryTest {
  private final KryoSerializer kryoSerializer = mock(KryoSerializer.class);
  private final RollbackStepsPMSPlanCreator rollbackStepsPMSPlanCreator = mock(RollbackStepsPMSPlanCreator.class);

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForRollback_whenNoStepsField_shouldReturnEmptyResponse() throws Exception {
    String yaml = "spec:\n"
        + "  execution:\n"
        + "    parallel: true\n";
    YamlField executionField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("spec").getNode().getField("execution");

    PlanCreationResponse response =
        RollbackPlanCreator.createPlanForRollback(executionField, "stageUuid", "stageName", buildInfrastructure(),
            kryoSerializer, new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response).isNotNull();
    assertThat(response.getNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForRollback_whenEmptySteps_shouldReturnEmptyResponse() throws Exception {
    String yaml = "spec:\n"
        + "  execution:\n"
        + "    steps: []\n";
    YamlField executionField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("spec").getNode().getField("execution");

    PlanCreationResponse response =
        RollbackPlanCreator.createPlanForRollback(executionField, "stageUuid", "stageName", buildInfrastructure(),
            kryoSerializer, new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response).isNotNull();
    assertThat(response.getNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForRollback_whenStepsExistNoRollback_shouldReturnCombinedRollbackNode() throws Exception {
    String yaml = "spec:\n"
        + "  execution:\n"
        + "    steps:\n"
        + "      - step:\n"
        + "          name: run1\n"
        + "          type: Run\n"
        + "          spec:\n"
        + "            command: echo hello\n";
    YamlField executionField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("spec").getNode().getField("execution");

    PlanCreationResponse response =
        RollbackPlanCreator.createPlanForRollback(executionField, "stageUuid", "stageName", buildInfrastructure(),
            kryoSerializer, new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response).isNotNull();
    assertThat(response.getNodes()).isNotEmpty();

    String expectedUuid = "stageUuid" + NGCommonUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX;
    PlanNode combinedNode = response.getNodes().get(expectedUuid);
    assertThat(combinedNode).isNotNull();
    assertThat(combinedNode.getStepType()).isEqualTo(CombinedRollbackStep.STEP_TYPE);
    assertThat(combinedNode.getIdentifier()).isEqualTo(YAMLFieldNameConstants.ROLLBACK_STEPS);
    assertThat(combinedNode.getName()).isEqualTo(NGCommonUtilPlanCreationConstants.ROLLBACK_NODE_NAME);

    RollbackOptionalChildChainStepParameters params =
        (RollbackOptionalChildChainStepParameters) combinedNode.getStepParameters();
    assertThat(params.getChildNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForRollback_whenStepsExistNoRollback_shouldPreserveNodeInRollbackMode() throws Exception {
    String yaml = "spec:\n"
        + "  execution:\n"
        + "    steps:\n"
        + "      - step:\n"
        + "          name: run1\n"
        + "          type: Run\n"
        + "          spec:\n"
        + "            command: echo hello\n";
    YamlField executionField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("spec").getNode().getField("execution");

    PlanCreationResponse response =
        RollbackPlanCreator.createPlanForRollback(executionField, "stageUuid", "stageName", buildInfrastructure(),
            kryoSerializer, new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    String expectedUuid = "stageUuid" + NGCommonUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX;
    assertThat(response.getPreservedNodesInRollbackMode()).contains(expectedUuid);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForRollback_whenExecutionRollbackExists_shouldIncludeExecutionRollbackChild()
      throws Exception {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});

    String yaml = "spec:\n"
        + "  execution:\n"
        + "    steps:\n"
        + "      - step:\n"
        + "          name: run1\n"
        + "          type: Run\n"
        + "          spec:\n"
        + "            command: echo hello\n"
        + "    rollback:\n"
        + "      - step:\n"
        + "          name: rollbackStep\n"
        + "          type: Run\n"
        + "          spec:\n"
        + "            command: echo rollback\n";
    YamlField executionField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("spec").getNode().getField("execution");

    PlanCreationResponse response =
        RollbackPlanCreator.createPlanForRollback(executionField, "stageUuid", "stageName", buildInfrastructure(),
            kryoSerializer, new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response).isNotNull();
    assertThat(response.getNodes()).hasSize(2);

    String combinedUuid = "stageUuid" + NGCommonUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX;
    PlanNode combinedNode = response.getNodes().get(combinedUuid);
    assertThat(combinedNode).isNotNull();

    RollbackOptionalChildChainStepParameters params =
        (RollbackOptionalChildChainStepParameters) combinedNode.getStepParameters();
    assertThat(params.getChildNodes()).hasSize(1);

    String stepsUuid = executionField.getNode().getField(YAMLFieldNameConstants.STEPS).getNode().getUuid();
    String expectedExecutionRollbackUuid =
        stepsUuid + NGCommonUtilPlanCreationConstants.ROLLBACK_EXECUTION_NODE_ID_SUFFIX;
    assertThat(params.getChildNodes().get(0).getNodeId()).isEqualTo(expectedExecutionRollbackUuid);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForRollback_whenExecutionRollbackExists_shouldMergeExecutionRollbackNodes()
      throws Exception {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});

    String yaml = "spec:\n"
        + "  execution:\n"
        + "    steps:\n"
        + "      - step:\n"
        + "          name: run1\n"
        + "          type: Run\n"
        + "          spec:\n"
        + "            command: echo hello\n"
        + "    rollback:\n"
        + "      - step:\n"
        + "          name: rollbackStep\n"
        + "          type: Run\n"
        + "          spec:\n"
        + "            command: echo rollback\n";
    YamlField executionField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("spec").getNode().getField("execution");

    PlanCreationResponse response =
        RollbackPlanCreator.createPlanForRollback(executionField, "stageUuid", "stageName", buildInfrastructure(),
            kryoSerializer, new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    boolean hasRollbackChainNode = response.getNodes().values().stream().anyMatch(
        n -> n.getStepType().equals(RollbackOptionalChildChainStep.STEP_TYPE));
    assertThat(hasRollbackChainNode).isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForRollback_whenExecutionRollbackExists_shouldIncludeDependencies() throws Exception {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});

    String yaml = "spec:\n"
        + "  execution:\n"
        + "    steps:\n"
        + "      - step:\n"
        + "          name: run1\n"
        + "          type: Run\n"
        + "          spec:\n"
        + "            command: echo hello\n"
        + "    rollback:\n"
        + "      - step:\n"
        + "          name: rollbackStep\n"
        + "          type: Run\n"
        + "          spec:\n"
        + "            command: echo rollback\n";
    YamlField executionField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("spec").getNode().getField("execution");

    PlanCreationResponse response =
        RollbackPlanCreator.createPlanForRollback(executionField, "stageUuid", "stageName", buildInfrastructure(),
            kryoSerializer, new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response.getDependencies()).isNotNull();
    assertThat(response.getDependencies().getDependenciesMap()).isNotEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForRollback_combinedNodeUuid_shouldBeStageUuidPlusSuffix() throws Exception {
    String yaml = "spec:\n"
        + "  execution:\n"
        + "    steps:\n"
        + "      - step:\n"
        + "          name: run1\n"
        + "          type: Run\n"
        + "          spec:\n"
        + "            command: echo hello\n";
    YamlField executionField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("spec").getNode().getField("execution");

    String stageUuid = "my-stage-uuid-123";
    PlanCreationResponse response =
        RollbackPlanCreator.createPlanForRollback(executionField, stageUuid, "stageName", buildInfrastructure(),
            kryoSerializer, new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    String expectedUuid = stageUuid + NGCommonUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX;
    assertThat(response.getNodes()).containsKey(expectedUuid);
    assertThat(response.getNodes().get(expectedUuid).getUuid()).isEqualTo(expectedUuid);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForRollback_whenNoInfraOrEnvSibling_shouldNotAddInfraChild() throws Exception {
    String yaml = "spec:\n"
        + "  execution:\n"
        + "    steps:\n"
        + "      - step:\n"
        + "          name: run1\n"
        + "          type: Run\n"
        + "          spec:\n"
        + "            command: echo hello\n";
    YamlField executionField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("spec").getNode().getField("execution");

    PlanCreationResponse response =
        RollbackPlanCreator.createPlanForRollback(executionField, "stageUuid", "stageName", buildInfrastructure(),
            kryoSerializer, new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    String combinedUuid = "stageUuid" + NGCommonUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX;
    PlanNode combinedNode = response.getNodes().get(combinedUuid);
    RollbackOptionalChildChainStepParameters params =
        (RollbackOptionalChildChainStepParameters) combinedNode.getStepParameters();
    assertThat(params.getChildNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForRollback_withModuleImplicitNodesInfo_shouldPassThroughToExecutionRollback()
      throws Exception {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {5, 6, 7});

    String yaml = "spec:\n"
        + "  execution:\n"
        + "    steps:\n"
        + "      - step:\n"
        + "          name: run1\n"
        + "          type: Run\n"
        + "          spec:\n"
        + "            command: echo hello\n"
        + "    rollback:\n"
        + "      - step:\n"
        + "          name: rollbackStep\n"
        + "          type: Run\n"
        + "          spec:\n"
        + "            command: echo rollback\n";
    YamlField executionField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("spec").getNode().getField("execution");

    Map<String, Object> moduleInfo = new HashMap<>();
    moduleInfo.put("key1", "value1");

    PlanCreationResponse response =
        RollbackPlanCreator.createPlanForRollback(executionField, "stageUuid", "stageName", buildInfrastructure(),
            kryoSerializer, moduleInfo, rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response.getNodes()).hasSize(2);
    assertThat(response.getDependencies().getDependenciesMap()).isNotEmpty();
  }

  private Infrastructure buildInfrastructure() {
    return K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).build();
  }
}

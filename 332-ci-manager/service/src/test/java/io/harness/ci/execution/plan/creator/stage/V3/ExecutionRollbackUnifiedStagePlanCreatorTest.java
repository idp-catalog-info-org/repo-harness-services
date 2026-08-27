/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage.V3;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.rollback.RollbackOptionalChildChainStep;
import io.harness.steps.rollback.RollbackOptionalChildChainStepParameters;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ExecutionRollbackUnifiedStagePlanCreatorTest extends CategoryTest {
  private final KryoSerializer kryoSerializer = mock(KryoSerializer.class);
  private final RollbackStepsPMSPlanCreator rollbackStepsPMSPlanCreator = mock(RollbackStepsPMSPlanCreator.class);

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateExecutionRollbackPlanNode_whenExecutionFieldIsNull_shouldReturnEmptyResponse() {
    PlanCreationResponse response = ExecutionRollbackUnifiedStagePlanCreator.createExecutionRollbackPlanNode(null,
        "stageUuid", "stageName", "stageIdentifier", buildInfrastructure(), kryoSerializer, new HashMap<>(),
        rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response).isNotNull();
    assertThat(response.getPlanNode()).isNull();
    assertThat(response.getNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateExecutionRollbackPlanNode_whenNoStepsInExecution_shouldReturnEmptyResponse() throws Exception {
    String yaml = "execution:\n"
        + "  steps: []\n";
    YamlNode executionNode = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode();

    PlanCreationResponse response = ExecutionRollbackUnifiedStagePlanCreator.createExecutionRollbackPlanNode(
        executionNode, "stageUuid", "stageName", "stageIdentifier", buildInfrastructure(), kryoSerializer,
        new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response).isNotNull();
    assertThat(response.getPlanNode()).isNull();
    assertThat(response.getNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateExecutionRollbackPlanNode_whenStepsExistButNoRollback_shouldReturnEmptyResponse()
      throws Exception {
    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        name: run1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo hello\n";
    YamlNode executionNode = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode();

    PlanCreationResponse response = ExecutionRollbackUnifiedStagePlanCreator.createExecutionRollbackPlanNode(
        executionNode, "stageUuid", "stageName", "stageIdentifier", buildInfrastructure(), kryoSerializer,
        new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response).isNotNull();
    assertThat(response.getPlanNode()).isNull();
    assertThat(response.getNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateExecutionRollbackPlanNode_whenStageRollbackExists_shouldReturnPlanWithRollbackNode()
      throws Exception {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});

    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        name: run1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo hello\n"
        + "  rollback:\n"
        + "    - step:\n"
        + "        name: rollbackStep\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo rollback\n";
    YamlNode executionNode = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode();

    PlanCreationResponse response = ExecutionRollbackUnifiedStagePlanCreator.createExecutionRollbackPlanNode(
        executionNode, "stageUuid", "stageName", "stageIdentifier", buildInfrastructure(), kryoSerializer,
        new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response).isNotNull();
    assertThat(response.getNodes()).isNotEmpty();
    PlanNode rollbackPlanNode = response.getNodes().values().iterator().next();
    assertThat(rollbackPlanNode.getStepType()).isEqualTo(RollbackOptionalChildChainStep.STEP_TYPE);
    assertThat(rollbackPlanNode.getIdentifier()).isEqualTo("rollbackSteps");

    RollbackOptionalChildChainStepParameters params =
        (RollbackOptionalChildChainStepParameters) rollbackPlanNode.getStepParameters();
    assertThat(params.getChildNodes()).hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateExecutionRollbackPlanNode_whenStageRollbackExists_shouldIncludeDependencies() throws Exception {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});

    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        name: run1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo hello\n"
        + "  rollback:\n"
        + "    - step:\n"
        + "        name: rollbackStep\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo rollback\n";
    YamlNode executionNode = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode();

    PlanCreationResponse response = ExecutionRollbackUnifiedStagePlanCreator.createExecutionRollbackPlanNode(
        executionNode, "stageUuid", "stageName", "stageIdentifier", buildInfrastructure(), kryoSerializer,
        new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response.getDependencies()).isNotNull();
    assertThat(response.getDependencies().getDependenciesMap()).isNotEmpty();
  }

  /**
   * Regression test pinning the PPR-only STAGE_FQN behavior in
   * {@code toDependenciesProtoWithRollbackModeV1}. Three modes are exercised through the same
   * code path:
   *   - NORMAL                  → STAGE_FQN must end with the stage UUID (existing behavior).
   *   - PIPELINE_ROLLBACK       → STAGE_FQN must end with the stage UUID (existing behavior).
   *   - POST_EXECUTION_ROLLBACK → STAGE_FQN must end with the stage YAML identifier (new behavior).
   * This guarantees stage / pipeline / step-group rollback flows are unaffected and only PPR
   * receives the identifier-based STAGE_FQN required to satisfy the additional stageFqn check
   * in {@code RollbackModeExecutionHelper.shouldPreserveNode}.
   */
  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testCreateExecutionRollbackPlanNode_stageFqnIsModeGated_pprUsesIdentifier_othersUseUuid()
      throws Exception {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});
    String stageNodeUuid = "uuid-of-the-stage-node";
    String stageIdentifier = "CD_1__k8s_environment1_k8s_rollback_infra_1";
    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        name: run1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo hello\n"
        + "  rollback:\n"
        + "    - step:\n"
        + "        name: rollbackStep\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo rollback\n";

    // NORMAL → expect UUID-based STAGE_FQN (unchanged behavior)
    assertStageFqnSuffixForMode(yaml, stageNodeUuid, stageIdentifier, ExecutionMode.NORMAL, stageNodeUuid);

    // PIPELINE_ROLLBACK → expect UUID-based STAGE_FQN (unchanged behavior)
    assertStageFqnSuffixForMode(yaml, stageNodeUuid, stageIdentifier, ExecutionMode.PIPELINE_ROLLBACK, stageNodeUuid);

    // POST_EXECUTION_ROLLBACK → expect identifier-based STAGE_FQN (new behavior)
    assertStageFqnSuffixForMode(
        yaml, stageNodeUuid, stageIdentifier, ExecutionMode.POST_EXECUTION_ROLLBACK, stageIdentifier);
  }

  private void assertStageFqnSuffixForMode(String yaml, String stageNodeUuid, String stageIdentifier,
      ExecutionMode executionMode, String expectedFqnSuffix) throws Exception {
    YamlNode executionNode = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode();
    PlanCreationContext ctx = mock(PlanCreationContext.class);
    when(ctx.getExecutionMode()).thenReturn(executionMode);

    PlanCreationResponse response = ExecutionRollbackUnifiedStagePlanCreator.createExecutionRollbackPlanNode(
        executionNode, stageNodeUuid, "stageName", stageIdentifier, buildInfrastructure(), kryoSerializer,
        new HashMap<>(), rollbackStepsPMSPlanCreator, ctx);

    assertThat(response.getDependencies()).isNotNull();
    assertThat(response.getDependencies().getDependencyMetadataMap()).isNotEmpty();
    Dependency dep = response.getDependencies().getDependencyMetadataMap().values().iterator().next();
    assertThat(dep.getParentInfo().getDataMap()).containsKey(PlanCreatorConstants.STAGE_FQN);
    String stageFqn = dep.getParentInfo().getDataMap().get(PlanCreatorConstants.STAGE_FQN).getStringValue();
    assertThat(stageFqn).endsWith("." + expectedFqnSuffix);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateExecutionRollbackPlanNode_whenStageRollbackExists_shouldPreserveNodeInRollbackMode()
      throws Exception {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});

    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        name: run1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo hello\n"
        + "  rollback:\n"
        + "    - step:\n"
        + "        name: rollbackStep\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo rollback\n";
    YamlNode executionNode = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode();

    PlanCreationResponse response = ExecutionRollbackUnifiedStagePlanCreator.createExecutionRollbackPlanNode(
        executionNode, "stageUuid", "stageName", "stageIdentifier", buildInfrastructure(), kryoSerializer,
        new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response.getPreservedNodesInRollbackMode()).isNotEmpty();
    String rollbackNodeUuid = response.getNodes().values().iterator().next().getUuid();
    assertThat(response.getPreservedNodesInRollbackMode()).contains(rollbackNodeUuid);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateExecutionRollbackPlanNode_whenSgRollbackOnly_shouldMergeSgResponse() throws Exception {
    // Use 'id' field which is what collectAllStepGroupRollbackGroups uses via stepNode.getId()
    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - id: sg1\n"
        + "      group:\n"
        + "        name: sg1\n"
        + "        steps:\n"
        + "          - step:\n"
        + "              name: run1\n"
        + "              type: Run\n"
        + "              spec:\n"
        + "                command: echo hi\n"
        + "        rollback:\n"
        + "          - step:\n"
        + "              name: sgRollback\n"
        + "              type: Run\n"
        + "              spec:\n"
        + "                command: echo rollback\n";
    YamlNode executionNode = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode();

    PlanNode sgRollbackPlanNode = PlanNode.builder()
                                      .uuid("sg-rollback-uuid")
                                      .identifier("sgRollback")
                                      .name("SG Rollback")
                                      .stepType(RollbackOptionalChildChainStep.STEP_TYPE)
                                      .build();
    PlanCreationResponse sgResponse = PlanCreationResponse.builder().planNode(sgRollbackPlanNode).build();
    when(rollbackStepsPMSPlanCreator.createSgOnlyRollbackPlan(
             any(), anyString(), anyString(), any(), anyMap(), anyList(), any(YamlField.class)))
        .thenReturn(sgResponse);

    PlanCreationResponse response = ExecutionRollbackUnifiedStagePlanCreator.createExecutionRollbackPlanNode(
        executionNode, "stageUuid", "stageName", "stageIdentifier", buildInfrastructure(), kryoSerializer,
        new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response).isNotNull();
    assertThat(response.getNodes()).isNotEmpty();
    boolean hasChainNode = response.getNodes().values().stream().anyMatch(
        n -> n.getStepType().equals(RollbackOptionalChildChainStep.STEP_TYPE));
    assertThat(hasChainNode).isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateExecutionRollbackPlanNode_whenSgRollbackReturnsNull_shouldReturnEmptyResponse()
      throws Exception {
    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - id: sg1\n"
        + "      group:\n"
        + "        name: sg1\n"
        + "        steps:\n"
        + "          - step:\n"
        + "              name: run1\n"
        + "              type: Run\n"
        + "              spec:\n"
        + "                command: echo hi\n"
        + "        rollback:\n"
        + "          - step:\n"
        + "              name: sgRollback\n"
        + "              type: Run\n"
        + "              spec:\n"
        + "                command: echo rollback\n";
    YamlNode executionNode = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode();

    when(rollbackStepsPMSPlanCreator.createSgOnlyRollbackPlan(
             any(), anyString(), anyString(), any(), anyMap(), anyList(), any(YamlField.class)))
        .thenReturn(null);

    PlanCreationResponse response = ExecutionRollbackUnifiedStagePlanCreator.createExecutionRollbackPlanNode(
        executionNode, "stageUuid", "stageName", "stageIdentifier", buildInfrastructure(), kryoSerializer,
        new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response).isNotNull();
    assertThat(response.getNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateExecutionRollbackPlanNode_whenSgRollbackReturnsNoPlanNode_shouldReturnEmptyResponse()
      throws Exception {
    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - id: sg1\n"
        + "      group:\n"
        + "        name: sg1\n"
        + "        steps:\n"
        + "          - step:\n"
        + "              name: run1\n"
        + "              type: Run\n"
        + "              spec:\n"
        + "                command: echo hi\n"
        + "        rollback:\n"
        + "          - step:\n"
        + "              name: sgRollback\n"
        + "              type: Run\n"
        + "              spec:\n"
        + "                command: echo rollback\n";
    YamlNode executionNode = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode();

    PlanCreationResponse emptyResponse = PlanCreationResponse.builder().build();
    when(rollbackStepsPMSPlanCreator.createSgOnlyRollbackPlan(
             any(), anyString(), anyString(), any(), anyMap(), anyList(), any(YamlField.class)))
        .thenReturn(emptyResponse);

    PlanCreationResponse response = ExecutionRollbackUnifiedStagePlanCreator.createExecutionRollbackPlanNode(
        executionNode, "stageUuid", "stageName", "stageIdentifier", buildInfrastructure(), kryoSerializer,
        new HashMap<>(), rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response).isNotNull();
    assertThat(response.getNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateExecutionRollbackPlanNode_withModuleImplicitNodesInfo_shouldSerializeInDependencies()
      throws Exception {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {10, 20, 30});

    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        name: run1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo hello\n"
        + "  rollback:\n"
        + "    - step:\n"
        + "        name: rollbackStep\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo rollback\n";
    YamlNode executionNode = YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode();

    Map<String, Object> moduleInfo = new HashMap<>();
    moduleInfo.put("someKey", "someValue");

    PlanCreationResponse response = ExecutionRollbackUnifiedStagePlanCreator.createExecutionRollbackPlanNode(
        executionNode, "stageUuid", "stageName", "stageIdentifier", buildInfrastructure(), kryoSerializer, moduleInfo,
        rollbackStepsPMSPlanCreator, mock(PlanCreationContext.class));

    assertThat(response.getNodes()).isNotEmpty();
    assertThat(response.getDependencies().getDependenciesMap()).isNotEmpty();
  }

  private Infrastructure buildInfrastructure() {
    return K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).build();
  }
}

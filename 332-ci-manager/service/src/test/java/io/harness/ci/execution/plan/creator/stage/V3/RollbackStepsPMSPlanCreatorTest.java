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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.execution.plancreator.V1.InitializeStepPlanCreatorV1;
import io.harness.ci.plan.creator.step.v1.PlanCreatorEnvVarHelper;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.rollback.RollbackStepsStep;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.powermock.reflect.Whitebox;

public class RollbackStepsPMSPlanCreatorTest extends CategoryTest {
  private final InitializeStepPlanCreatorV1 initializeStepPlanCreatorV1 = mock(InitializeStepPlanCreatorV1.class);
  private final KryoSerializer kryoSerializer = mock(KryoSerializer.class);
  private final CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
  private final PlanCreatorEnvVarHelper planCreatorEnvVarHelper = mock(PlanCreatorEnvVarHelper.class);

  private RollbackStepsPMSPlanCreator rollbackStepsPMSPlanCreator;

  @Before
  public void setUp() {
    rollbackStepsPMSPlanCreator = new RollbackStepsPMSPlanCreator();
    Whitebox.setInternalState(rollbackStepsPMSPlanCreator, "initializeStepPlanCreatorV1", initializeStepPlanCreatorV1);
    Whitebox.setInternalState(rollbackStepsPMSPlanCreator, "kryoSerializer", kryoSerializer);
    Whitebox.setInternalState(rollbackStepsPMSPlanCreator, "ciPlanCreatorUtils", ciPlanCreatorUtils);
    Whitebox.setInternalState(rollbackStepsPMSPlanCreator, "planCreatorEnvVarHelper", planCreatorEnvVarHelper);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedTypes_shouldReturnRollbackStepsV1() {
    Map<String, Set<String>> supportedTypes = rollbackStepsPMSPlanCreator.getSupportedTypes();
    assertThat(supportedTypes).containsKey(YAMLFieldNameConstants.ROLLBACK_STEPS_V1);
    assertThat(supportedTypes.get(YAMLFieldNameConstants.ROLLBACK_STEPS_V1)).containsExactly(PlanCreatorUtils.ANY_TYPE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions_shouldReturnV1() {
    Set<String> versions = rollbackStepsPMSPlanCreator.getSupportedYamlVersions();
    assertThat(versions).containsExactly(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldObject_shouldReturnSameField() throws Exception {
    YamlField field =
        buildRollbackStepsField("- step:\n    name: r1\n    type: Run\n    spec:\n      command: echo r\n");
    YamlField result = rollbackStepsPMSPlanCreator.getFieldObject(field);
    assertThat(result).isSameAs(field);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForField_whenFieldIsNull_shouldReturnEmptyResponse() {
    PlanCreationContext ctx = buildContext();
    PlanCreationResponse response = rollbackStepsPMSPlanCreator.createPlanForField(ctx, null);
    assertThat(response).isNotNull();
    assertThat(response.getPlanNode()).isNull();
    assertThat(response.getNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForField_whenEmptyArray_shouldReturnEmptyResponse() throws Exception {
    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        name: s1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo hi\n"
        + "  rollback: []\n";
    YamlField rollbackField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode().getField("rollback");

    PlanCreationContext ctx = buildContext();
    PlanCreationResponse response = rollbackStepsPMSPlanCreator.createPlanForField(ctx, rollbackField);
    assertThat(response).isNotNull();
    assertThat(response.getPlanNode()).isNull();
    assertThat(response.getNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForField_withSteps_shouldReturnResponseWithWrapperNode() throws Exception {
    when(ciPlanCreatorUtils.getDeserializedObjectFromDependency(any(), eq("infrastructure")))
        .thenReturn(Optional.of(buildInfrastructure()));
    when(ciPlanCreatorUtils.getModulesImplicitNodesInfo(any())).thenReturn(new HashMap<>());

    PlanNode initPlanNode = PlanNode.builder()
                                .uuid("init-uuid")
                                .identifier("init")
                                .name("Initialize")
                                .stepType(RollbackStepsStep.STEP_TYPE)
                                .build();
    PlanCreationResponse initResponse = PlanCreationResponse.builder().planNode(initPlanNode).build();
    when(initializeStepPlanCreatorV1.createPlan(
             any(), any(), any(), any(), any(), anyList(), anyList(), anyString(), any(), anyMap(), any()))
        .thenReturn(initResponse);

    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        name: s1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo hi\n"
        + "  rollback:\n"
        + "    - step:\n"
        + "        name: r1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo rollback\n";
    YamlField rollbackField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode().getField("rollback");

    PlanCreationContext ctx = buildContext();
    PlanCreationResponse response = rollbackStepsPMSPlanCreator.createPlanForField(ctx, rollbackField);

    assertThat(response).isNotNull();
    assertThat(response.getPlanNode()).isNotNull();
    assertThat(response.getPlanNode().getStepType()).isEqualTo(RollbackStepsStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForField_withSteps_shouldIncludeDependencies() throws Exception {
    when(ciPlanCreatorUtils.getDeserializedObjectFromDependency(any(), eq("infrastructure")))
        .thenReturn(Optional.of(buildInfrastructure()));
    when(ciPlanCreatorUtils.getModulesImplicitNodesInfo(any())).thenReturn(new HashMap<>());

    PlanNode initPlanNode = PlanNode.builder()
                                .uuid("init-uuid")
                                .identifier("init")
                                .name("Initialize")
                                .stepType(RollbackStepsStep.STEP_TYPE)
                                .build();
    PlanCreationResponse initResponse = PlanCreationResponse.builder().planNode(initPlanNode).build();
    when(initializeStepPlanCreatorV1.createPlan(
             any(), any(), any(), any(), any(), anyList(), anyList(), anyString(), any(), anyMap(), any()))
        .thenReturn(initResponse);

    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        name: s1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo hi\n"
        + "  rollback:\n"
        + "    - step:\n"
        + "        name: r1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo rollback\n";
    YamlField rollbackField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode().getField("rollback");

    PlanCreationContext ctx = buildContext();
    PlanCreationResponse response = rollbackStepsPMSPlanCreator.createPlanForField(ctx, rollbackField);

    assertThat(response.getDependencies()).isNotNull();
    assertThat(response.getDependencies().getDependenciesMap()).isNotEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForField_withMultipleSteps_shouldChainDependenciesWithNextId() throws Exception {
    when(ciPlanCreatorUtils.getDeserializedObjectFromDependency(any(), eq("infrastructure")))
        .thenReturn(Optional.of(buildInfrastructure()));
    when(ciPlanCreatorUtils.getModulesImplicitNodesInfo(any())).thenReturn(new HashMap<>());

    PlanNode initPlanNode = PlanNode.builder()
                                .uuid("init-uuid")
                                .identifier("init")
                                .name("Initialize")
                                .stepType(RollbackStepsStep.STEP_TYPE)
                                .build();
    PlanCreationResponse initResponse = PlanCreationResponse.builder().planNode(initPlanNode).build();
    when(initializeStepPlanCreatorV1.createPlan(
             any(), any(), any(), any(), any(), anyList(), anyList(), anyString(), any(), anyMap(), any()))
        .thenReturn(initResponse);

    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        name: s1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo hi\n"
        + "  rollback:\n"
        + "    - step:\n"
        + "        name: r1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo rollback1\n"
        + "    - step:\n"
        + "        name: r2\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo rollback2\n";
    YamlField rollbackField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode().getField("rollback");

    PlanCreationContext ctx = buildContext();
    PlanCreationResponse response = rollbackStepsPMSPlanCreator.createPlanForField(ctx, rollbackField);

    assertThat(response.getDependencies().getDependenciesMap()).hasSize(2);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForField_whenInfraIsEmpty_shouldStillSucceed() throws Exception {
    when(ciPlanCreatorUtils.getDeserializedObjectFromDependency(any(), eq("infrastructure")))
        .thenReturn(Optional.empty());
    when(ciPlanCreatorUtils.getModulesImplicitNodesInfo(any())).thenReturn(new HashMap<>());

    PlanNode initPlanNode = PlanNode.builder()
                                .uuid("init-uuid")
                                .identifier("init")
                                .name("Initialize")
                                .stepType(RollbackStepsStep.STEP_TYPE)
                                .build();
    PlanCreationResponse initResponse = PlanCreationResponse.builder().planNode(initPlanNode).build();
    when(initializeStepPlanCreatorV1.createPlan(
             any(), any(), any(), any(), any(), anyList(), anyList(), anyString(), any(), anyMap(), any()))
        .thenReturn(initResponse);

    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        name: s1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo hi\n"
        + "  rollback:\n"
        + "    - step:\n"
        + "        name: r1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo rollback\n";
    YamlField rollbackField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode().getField("rollback");

    PlanCreationContext ctx = buildContext();
    PlanCreationResponse response = rollbackStepsPMSPlanCreator.createPlanForField(ctx, rollbackField);

    assertThat(response).isNotNull();
    assertThat(response.getPlanNode()).isNotNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateSgOnlyRollbackPlan_withValidGroups_shouldReturnPlanWithWrapperNode() throws Exception {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});
    when(ciPlanCreatorUtils.getModulesImplicitNodesInfo(any())).thenReturn(new HashMap<>());

    PlanNode initPlanNode = PlanNode.builder()
                                .uuid("init-uuid")
                                .identifier("init")
                                .name("Initialize")
                                .stepType(RollbackStepsStep.STEP_TYPE)
                                .build();
    PlanCreationResponse initResponse = PlanCreationResponse.builder().planNode(initPlanNode).build();
    when(initializeStepPlanCreatorV1.createPlan(
             any(), any(), any(), any(), any(), anyList(), anyList(), anyString(), any(), anyMap(), any()))
        .thenReturn(initResponse);

    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - id: sg1\n"
        + "      group:\n"
        + "        name: sg1\n"
        + "        steps:\n"
        + "          - step:\n"
        + "              name: s1\n"
        + "              type: Run\n"
        + "              spec:\n"
        + "                command: echo hi\n"
        + "        rollback:\n"
        + "          - step:\n"
        + "              name: sgr1\n"
        + "              type: Run\n"
        + "              spec:\n"
        + "                command: echo sg-rollback\n";
    YamlField executionStepsField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode().getField("steps");

    java.util.List<java.util.List<io.harness.steps.rollback.RollbackNode>> sgRollbackGroups =
        StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(executionStepsField);

    PlanCreationContext ctx = buildContext();
    PlanCreationResponse response = rollbackStepsPMSPlanCreator.createSgOnlyRollbackPlan(
        ctx, "stageId", "stageName", buildInfrastructure(), new HashMap<>(), sgRollbackGroups, executionStepsField);

    assertThat(response).isNotNull();
    assertThat(response.getPlanNode()).isNotNull();
    assertThat(response.getPlanNode().getStepType()).isEqualTo(RollbackStepsStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateSgOnlyRollbackPlan_shouldPreserveNodesInRollbackMode() throws Exception {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});
    when(ciPlanCreatorUtils.getModulesImplicitNodesInfo(any())).thenReturn(new HashMap<>());

    PlanNode initPlanNode = PlanNode.builder()
                                .uuid("init-uuid")
                                .identifier("init")
                                .name("Initialize")
                                .stepType(RollbackStepsStep.STEP_TYPE)
                                .build();
    PlanCreationResponse initResponse = PlanCreationResponse.builder().planNode(initPlanNode).build();
    when(initializeStepPlanCreatorV1.createPlan(
             any(), any(), any(), any(), any(), anyList(), anyList(), anyString(), any(), anyMap(), any()))
        .thenReturn(initResponse);

    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - id: sg1\n"
        + "      group:\n"
        + "        name: sg1\n"
        + "        steps:\n"
        + "          - step:\n"
        + "              name: s1\n"
        + "              type: Run\n"
        + "              spec:\n"
        + "                command: echo hi\n"
        + "        rollback:\n"
        + "          - step:\n"
        + "              name: sgr1\n"
        + "              type: Run\n"
        + "              spec:\n"
        + "                command: echo sg-rollback\n";
    YamlField executionStepsField =
        YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("execution").getNode().getField("steps");

    java.util.List<java.util.List<io.harness.steps.rollback.RollbackNode>> sgRollbackGroups =
        StepGroupRollbackPlanCreatorUtils.collectAllStepGroupRollbackGroups(executionStepsField);

    PlanCreationContext ctx = buildContext();
    PlanCreationResponse response = rollbackStepsPMSPlanCreator.createSgOnlyRollbackPlan(
        ctx, "stageId", "stageName", buildInfrastructure(), new HashMap<>(), sgRollbackGroups, executionStepsField);

    assertThat(response.getPreservedNodesInRollbackMode()).isNotEmpty();
    assertThat(response.getPreservedNodesInRollbackMode()).contains(response.getPlanNode().getUuid());
  }

  private PlanCreationContext buildContext() {
    Dependency dependency =
        Dependency.newBuilder()
            .setNodeMetadata(
                HarnessStruct.newBuilder()
                    .putData("stageIdentifier", HarnessValue.newBuilder().setStringValue("stageId").build())
                    .putData("stageName", HarnessValue.newBuilder().setStringValue("stageName").build())
                    .build())
            .build();
    return PlanCreationContext.builder().dependency(dependency).build();
  }

  private YamlField buildRollbackStepsField(String stepsYaml) throws Exception {
    String yaml = "execution:\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        name: s1\n"
        + "        type: Run\n"
        + "        spec:\n"
        + "          command: echo hi\n"
        + "  rollback:\n"
        + "    " + stepsYaml.replace("\n", "\n    ") + "\n";
    return YamlUtils.readTree(YamlUtils.injectUuid(yaml))
        .getNode()
        .getField("execution")
        .getNode()
        .getField("rollback");
  }

  private Infrastructure buildInfrastructure() {
    return K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).build();
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plan;

import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.harness.advisers.nextstep.NextStepAdviser;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityPlanNodeTest {
  StepType TEST_STEP_TYPE = StepType.newBuilder().setType("TEST_STEP_PLAN").setStepCategory(StepCategory.STEP).build();
  StepType PMS_IDENTITY = StepType.newBuilder().setType("PMS_IDENTITY").build();

  private static final String UUID = "uuid";
  private static final String NAME = "name";
  private static final String IDENTIFIER = "identifier";
  private static final String GROUP = "group";
  private static final String STEP_TYPE = "stepType";
  private static final String SERVICE_NAME = "serviceName";
  private static final String STAGE_FQN = "stageFqn";
  private static final String WHEN_CONDITION = "whenCondition";
  private static final String ORIGINAL_NODE_EXECUTIONID = "originalNodeExecutionID";
  NodeType nodeType = NodeType.IDENTITY_PLAN_NODE;

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testMapPlanNodeToIdentityNodeFromNodeWithoutUseAdviserObtainmentsFlag() {
    StepType stepType = StepType.newBuilder().setType(STEP_TYPE).build();
    ExecutionMode executionMode = mock(ExecutionMode.class);
    AdviserObtainment adviserObtainment = AdviserObtainment.getDefaultInstance();
    List<AdviserObtainment> adviserObtainmentList = Collections.singletonList(adviserObtainment);
    Map<ExecutionMode, List<AdviserObtainment>> advisorObtainmentsForExecutionMode =
        Collections.singletonMap(executionMode, adviserObtainmentList);
    IdentityPlanNode identityPlanNode = IdentityPlanNode.builder()
                                            .uuid(UUID)
                                            .name(NAME)
                                            .identifier(IDENTIFIER)
                                            .group(GROUP)
                                            .stepType(stepType)
                                            .advisorObtainmentsForExecutionMode(advisorObtainmentsForExecutionMode)
                                            .adviserObtainments(adviserObtainmentList)
                                            .useAdviserObtainments(null)
                                            .isSkipExpressionChain(false)
                                            .serviceName(SERVICE_NAME)
                                            .stageFqn(STAGE_FQN)
                                            .advisorObtainmentsForExecutionMode(advisorObtainmentsForExecutionMode)
                                            .adviserObtainments(adviserObtainmentList)
                                            .whenCondition(WHEN_CONDITION)
                                            .originalNodeExecutionId(ORIGINAL_NODE_EXECUTIONID)
                                            .build();

    IdentityPlanNode expectedResult = IdentityPlanNode.builder()
                                          .uuid(UUID)
                                          .name(NAME)
                                          .identifier(IDENTIFIER)
                                          .group(GROUP)
                                          .stepType(stepType)
                                          .advisorObtainmentsForExecutionMode(advisorObtainmentsForExecutionMode)
                                          .adviserObtainments(adviserObtainmentList)
                                          .useAdviserObtainments(false)
                                          .isSkipExpressionChain(false)
                                          .serviceName(SERVICE_NAME)
                                          .stageFqn(STAGE_FQN)
                                          .advisorObtainmentsForExecutionMode(advisorObtainmentsForExecutionMode)
                                          .adviserObtainments(adviserObtainmentList)
                                          .whenCondition(WHEN_CONDITION)
                                          .originalNodeExecutionId(ORIGINAL_NODE_EXECUTIONID)
                                          .skipGraphType(SkipType.SKIP_NODE)
                                          .build();

    IdentityPlanNode actualResult =
        IdentityPlanNode.mapPlanNodeToIdentityNode(identityPlanNode, stepType, ORIGINAL_NODE_EXECUTIONID, true);

    assertThat(actualResult).isEqualTo(expectedResult);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testMapPlanNodeToIdentityNodeFromNodeWithUseAdviserObtainmentsFlag() {
    StepType stepType = StepType.newBuilder().setType(STEP_TYPE).build();
    ExecutionMode executionMode = mock(ExecutionMode.class);
    AdviserObtainment adviserObtainment = AdviserObtainment.getDefaultInstance();
    List<AdviserObtainment> adviserObtainmentList = Collections.singletonList(adviserObtainment);
    Map<ExecutionMode, List<AdviserObtainment>> advisorObtainmentsForExecutionMode =
        Collections.singletonMap(executionMode, adviserObtainmentList);
    IdentityPlanNode identityPlanNode = IdentityPlanNode.builder()
                                            .uuid(UUID)
                                            .name(NAME)
                                            .identifier(IDENTIFIER)
                                            .group(GROUP)
                                            .stepType(stepType)
                                            .advisorObtainmentsForExecutionMode(advisorObtainmentsForExecutionMode)
                                            .adviserObtainments(adviserObtainmentList)
                                            .useAdviserObtainments(false)
                                            .isSkipExpressionChain(false)
                                            .serviceName(SERVICE_NAME)
                                            .stageFqn(STAGE_FQN)
                                            .advisorObtainmentsForExecutionMode(advisorObtainmentsForExecutionMode)
                                            .adviserObtainments(adviserObtainmentList)
                                            .whenCondition(WHEN_CONDITION)
                                            .originalNodeExecutionId(ORIGINAL_NODE_EXECUTIONID)
                                            .build();

    IdentityPlanNode expectedResult = IdentityPlanNode.builder()
                                          .uuid(UUID)
                                          .name(NAME)
                                          .identifier(IDENTIFIER)
                                          .group(GROUP)
                                          .stepType(stepType)
                                          .advisorObtainmentsForExecutionMode(advisorObtainmentsForExecutionMode)
                                          .adviserObtainments(adviserObtainmentList)
                                          .useAdviserObtainments(false)
                                          .isSkipExpressionChain(false)
                                          .serviceName(SERVICE_NAME)
                                          .stageFqn(STAGE_FQN)
                                          .advisorObtainmentsForExecutionMode(advisorObtainmentsForExecutionMode)
                                          .adviserObtainments(adviserObtainmentList)
                                          .whenCondition(WHEN_CONDITION)
                                          .originalNodeExecutionId(ORIGINAL_NODE_EXECUTIONID)
                                          .skipGraphType(SkipType.SKIP_NODE)
                                          .build();

    IdentityPlanNode actualResult =
        IdentityPlanNode.mapPlanNodeToIdentityNode(identityPlanNode, stepType, ORIGINAL_NODE_EXECUTIONID, true);

    assertThat(actualResult).isEqualTo(expectedResult);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMapPlanNodeToIdentityNode() {
    PlanNode planNode =
        PlanNode.builder()
            .name("Test Node")
            .uuid("uuid")
            .identifier("test")
            .stepType(TEST_STEP_TYPE)
            .advisorObtainmentsForExecutionMode(Map.of(ExecutionMode.PIPELINE_ROLLBACK,
                Collections.singletonList(AdviserObtainment.newBuilder().setType(NextStepAdviser.ADVISER_TYPE).build()),
                ExecutionMode.POST_EXECUTION_ROLLBACK,
                Collections.singletonList(
                    AdviserObtainment.newBuilder().setType(NextStepAdviser.ADVISER_TYPE).build())))
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .skipGraphType(SkipType.NOOP)
            .build();
    IdentityPlanNode identityPlanNodeExpected =
        IdentityPlanNode.builder()
            .uuid("uuid")
            .originalNodeExecutionId("originalNodeExecutionId")
            .identifier("test")
            .name("Test Node")
            .stepType(PMS_IDENTITY)
            .adviserObtainments(
                Collections.singletonList(AdviserObtainment.newBuilder().setType(NextStepAdviser.ADVISER_TYPE).build()))
            .advisorObtainmentsForExecutionMode(planNode.getAdvisorObtainmentsForExecutionMode())
            .skipGraphType(SkipType.NOOP)
            .build();
    IdentityPlanNode identityPlanNodeActual =
        IdentityPlanNode.mapPlanNodeToIdentityNode(planNode, PMS_IDENTITY, "originalNodeExecutionId");
    assertThat(identityPlanNodeExpected).isEqualTo(identityPlanNodeActual);

    IdentityPlanNode identityPlanNodeWithAlwaysSkipGraph =
        IdentityPlanNode.mapPlanNodeToIdentityNode(planNode, PMS_IDENTITY, "originalNodeExecutionId", true);
    identityPlanNodeExpected = IdentityPlanNode.builder()
                                   .uuid("uuid")
                                   .originalNodeExecutionId("originalNodeExecutionId")
                                   .identifier("test")
                                   .name("Test Node")
                                   .stepType(PMS_IDENTITY)
                                   .adviserObtainments(Collections.singletonList(
                                       AdviserObtainment.newBuilder().setType(NextStepAdviser.ADVISER_TYPE).build()))
                                   .advisorObtainmentsForExecutionMode(planNode.getAdvisorObtainmentsForExecutionMode())
                                   .skipGraphType(SkipType.SKIP_NODE)
                                   .build();
    assertThat(identityPlanNodeExpected).isEqualTo(identityPlanNodeWithAlwaysSkipGraph);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testConvertToListOfOGNodeExecIds() {
    IdentityPlanNode identityPlanNode = IdentityPlanNode.builder().build();

    identityPlanNode.convertToListOfOGNodeExecIds("123");

    assertThat(identityPlanNode.getAllOriginalNodeExecutionIds()).hasSize(2);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testMapPlanNodeToIdentityNodeWithSkipAsTrue() {
    IdentityPlanNode identityPlanNode = IdentityPlanNode.builder().build();
    PlanNode node = PlanNode.builder().build();
    StepType stepType = StepType.newBuilder().build();
    IdentityPlanNode identityPlanNode1 = identityPlanNode.mapPlanNodeToIdentityNodeWithSkipAsTrue(
        "newUuid", node, "nodeIdentifier", "nodeName", stepType, "originalNodeExecutionId");
    assertThat(identityPlanNode1.getSkipGraphType()).isEqualTo(SkipType.SKIP_NODE);

    StepType stepType2 = StepType.newBuilder().setType("NG_FORK").setStepCategory(StepCategory.FORK).build();
    PlanNode node2 = PlanNode.builder().stepType(stepType2).build();
    IdentityPlanNode identityPlanNode2 = IdentityPlanNode.mapPlanNodeToIdentityNodeWithSkipAsTrue(
        "newUuid2", node2, "nodeIdentifier2", "nodeName2", stepType2, "originalNodeExecutionId2");
    assertThat(identityPlanNode2.getSkipGraphType()).isEqualTo(SkipType.SKIP_TREE);

    StepType stepType3 = StepType.newBuilder().setType("STRATEGY").setStepCategory(StepCategory.STRATEGY).build();
    PlanNode node3 = PlanNode.builder().stepType(stepType3).build();
    IdentityPlanNode identityPlanNode3 = IdentityPlanNode.mapPlanNodeToIdentityNodeWithSkipAsTrue(
        "newUuid3", node3, "nodeIdentifier3", "nodeName3", stepType3, "originalNodeExecutionId3");
    assertThat(identityPlanNode3.getSkipGraphType()).isEqualTo(SkipType.SKIP_TREE);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetNodeType() {
    IdentityPlanNode identityPlanNode = IdentityPlanNode.builder().build();
    NodeType nodeType = identityPlanNode.getNodeType();
    assertThat(nodeType).isEqualTo(NodeType.IDENTITY_PLAN_NODE);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testIsSkipUnresolvedExpressionsCheck() {
    IdentityPlanNode identityPlanNode = IdentityPlanNode.builder().build();
    boolean result = identityPlanNode.isSkipUnresolvedExpressionsCheck();
    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetStepParameters() {
    IdentityPlanNode identityPlanNode = IdentityPlanNode.builder().build();

    PmsStepParameters result = identityPlanNode.getStepParameters();
    assertThat(result).containsKey("originalNodeExecutionId");
    assertThat(result.size()).isEqualTo(1);
  }
}

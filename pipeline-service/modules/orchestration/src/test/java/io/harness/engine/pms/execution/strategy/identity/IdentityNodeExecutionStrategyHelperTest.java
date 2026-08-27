/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationTestHelper;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.pms.execution.modifier.ambiance.AmbianceExecutionContextHelper;
import io.harness.execution.NodeExecution;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.interrupts.InterruptEffect;
import io.harness.logging.UnitProgress;
import io.harness.plan.IdentityPlanNode;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.RetryInterruptConfig;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityNodeExecutionStrategyHelperTest {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PlanService planService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock AmbianceExecutionContextHelper ambianceExecutionContextHelper;
  @InjectMocks IdentityNodeExecutionStrategyHelper identityNodeExecutionStrategyHelper;

  @Mock NodeExecutionInfoService pmsGraphStepDetailsService;

  @Mock Ambiance ambiance;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetInterruptHistory() {
    List<String> retryIds = List.of("retryId1", "retryId2");
    Map<String, String> retryIdsMap =
        Map.of(retryIds.get(0), "mappedId1", retryIds.get(1), "mappedId2", "retryId3", "mappedId3");

    List<InterruptEffect> interruptHistory = new ArrayList<>();
    interruptHistory.add(
        InterruptEffect.builder()
            .interruptType(InterruptType.RETRY)
            .interruptId("interruptId1")
            .tookEffectAt(100L)
            .interruptConfig(
                InterruptConfig.newBuilder()
                    .setRetryInterruptConfig(RetryInterruptConfig.newBuilder().setRetryId(retryIds.get(0)).build())
                    .build())
            .build());

    interruptHistory.add(
        InterruptEffect.builder()
            .interruptType(InterruptType.RETRY)
            .interruptId("interruptId2")
            .tookEffectAt(150L)
            .interruptConfig(
                InterruptConfig.newBuilder()
                    .setRetryInterruptConfig(RetryInterruptConfig.newBuilder().setRetryId(retryIds.get(1)).build())
                    .build())
            .build());

    interruptHistory.add(InterruptEffect.builder()
                             .interruptType(InterruptType.MARK_SUCCESS)
                             .interruptId("interruptId3")
                             .tookEffectAt(200L)
                             .interruptConfig(InterruptConfig.newBuilder().build())
                             .build());

    List<InterruptEffect> updatedInterruptHistory =
        identityNodeExecutionStrategyHelper.getUpdatedInterruptHistory(interruptHistory, retryIdsMap);
    assertEquals(updatedInterruptHistory.size(), interruptHistory.size());
    for (InterruptEffect interruptEffect : updatedInterruptHistory) {
      Optional<InterruptEffect> optional = interruptHistory.stream()
                                               .filter(o -> o.getInterruptId().equals(interruptEffect.getInterruptId()))
                                               .findFirst();
      assertThat(optional.isPresent()).isTrue();
      InterruptEffect originalInterruptEffect = optional.get();
      assertEquals(originalInterruptEffect.getInterruptType(), interruptEffect.getInterruptType());
      assertEquals(originalInterruptEffect.getTookEffectAt(), interruptEffect.getTookEffectAt());
      if (interruptEffect.getInterruptConfig().hasRetryInterruptConfig()) {
        assertThat(originalInterruptEffect.getInterruptConfig().hasRetryInterruptConfig()).isTrue();
        assertEquals(interruptEffect.getInterruptConfig().getRetryInterruptConfig().getRetryId(),
            retryIdsMap.get(originalInterruptEffect.getInterruptConfig().getRetryInterruptConfig().getRetryId()));
      } else {
        assertEquals(interruptEffect.getInterruptConfig(), originalInterruptEffect.getInterruptConfig());
      }
    }
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCopyNodeExecutionsForRetriedNodes() {
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .ambiance(Ambiance.newBuilder()
                                                    .addLevels(Level.newBuilder().setRuntimeId("runtimeId1").build())
                                                    .addLevels(Level.newBuilder().setRuntimeId("runtimeId2").build())
                                                    .build())
                                      .build();
    String retryId1 = "retryId1";
    String retryId2 = "retryId2";
    List<String> retryIdsList = List.of(retryId1, retryId2);

    List<NodeExecution> retriedNodeExecutions = new ArrayList<>();
    IdentityPlanNode node =
        IdentityPlanNode.builder().uuid("uuid1").identifier("id1").stepType(StepType.newBuilder().build()).build();
    retriedNodeExecutions.add(
        NodeExecution.builder().ambiance(Ambiance.newBuilder().build()).nodeId(node.getUuid()).uuid(retryId1).build());
    retriedNodeExecutions.add(
        NodeExecution.builder().ambiance(Ambiance.newBuilder().build()).nodeId(node.getUuid()).uuid(retryId2).build());
    doReturn(retriedNodeExecutions).when(nodeExecutionService).getAll(any());
    doReturn(nodeExecution.getAmbiance()).when(nodeExecutionService).getAmbiance(nodeExecution);
    doReturn(node).when(planService).fetchNode(any(), eq("uuid1"));

    ArgumentCaptor<List> nodeExecutionArgumentCaptor = ArgumentCaptor.forClass(List.class);

    identityNodeExecutionStrategyHelper.copyNodeExecutionsForRetriedNodes(nodeExecution, retryIdsList);

    verify(nodeExecutionService, times(1)).saveAll(nodeExecutionArgumentCaptor.capture());
    verify(nodeExecutionService, times(1)).updateV2(eq(nodeExecution.getUuid()), any());

    List<NodeExecution> newNodeExecutions = nodeExecutionArgumentCaptor.getValue();
    assertEquals(newNodeExecutions.size(), retryIdsList.size());
    assertThat(retryIdsList.contains(newNodeExecutions.get(0).getOriginalNodeExecutionId())).isTrue();
    assertThat(retryIdsList.contains(newNodeExecutions.get(1).getOriginalNodeExecutionId())).isTrue();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetMappedRetryIdsForNewNodeExecution() {
    List<String> retryIds = List.of("retryId1", "retryId2");
    Map<String, String> retryIdsMap =
        Map.of(retryIds.get(0), "mappedId1", retryIds.get(1), "mappedId2", "retryId3", "mappedId3");

    List<String> mappedIds =
        identityNodeExecutionStrategyHelper.getNewRetryIdsFromOriginalRetryIds(retryIds, retryIdsMap);
    assertEquals(mappedIds.size(), retryIds.size());
    assertThat(mappedIds.contains(retryIdsMap.get(retryIds.get(0)))).isTrue();
    assertThat(mappedIds.contains(retryIdsMap.get(retryIds.get(1)))).isTrue();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetCorrectNodeExecution() {
    List<Level> currLevels = new ArrayList<>();
    Level level1 = Level.newBuilder()
                       .setStrategyMetadata(StrategyMetadata.newBuilder().setCurrentIteration(1).build())
                       .setGroup("blah1")
                       .setNodeType("node1")
                       .setOriginalIdentifier("originalIdentifier")
                       .build();
    Level level2 = Level.newBuilder()
                       .setStrategyMetadata(StrategyMetadata.newBuilder().setCurrentIteration(2).build())
                       .setRuntimeId("runtime")
                       .build();
    currLevels.add(level1);
    currLevels.add(level2);
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .ambiance(Ambiance.newBuilder().addLevels(0, level1).addLevels(level2).setPlanExecutionId("123").build())
            .nextId("123")
            .build();
    List<NodeExecution> nodeExecutionList = Collections.singletonList(nodeExecution);
    try (Stream<NodeExecution> executionsIterator =
             OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator()).stream()) {
      NodeExecution result =
          identityNodeExecutionStrategyHelper.getCorrectNodeExecution(executionsIterator, currLevels);
      assertThat(result.getAmbiance().getLevelsCount()).isEqualTo(2);
      assertThat(result.getAmbiance().getPlanExecutionId()).isEqualTo(nodeExecution.getPlanExecutionId());
      assertThat(result.getAmbiance().getLevels(0).getGroup()).isEqualTo(level1.getGroup());
      assertThat(result.getAmbiance().getLevels(0).getNodeType()).isEqualTo(level1.getNodeType());
      assertThat(result.getAmbiance().getLevels(0).getOriginalIdentifier()).isEqualTo(level1.getOriginalIdentifier());
      assertThat(result.getAmbiance().getLevels(1).getRuntimeId()).isEqualTo(level2.getRuntimeId());
    }
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testCreateNodeExecution() {
    SkipType skipType = SkipType.SKIP_NODE;
    StepType stepType = StepType.newBuilder().build();

    IdentityPlanNode node = IdentityPlanNode.builder()
                                .originalNodeExecutionId("1")
                                .uuid("12")
                                .serviceName("serviceName")
                                .skipGraphType(skipType)
                                .stepType(stepType)
                                .stageFqn("Fqn")
                                .group("group")
                                .isSkipExpressionChain(true)
                                .build();

    String notifyId = "12";
    String parentId = "124";
    String previousId = "123";
    Level level = Level.newBuilder().setRuntimeId("1").build();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(level).build();
    List<UnitProgress> unitProgressList = new ArrayList<>();

    UnitProgress unitProgress = UnitProgress.newBuilder().build();
    unitProgressList.add(unitProgress);

    NodeRunInfo nodeRunInfo = NodeRunInfo.newBuilder().build();
    FailureInfo failureInfo = FailureInfo.newBuilder().build();
    Map<String, Object> progressData = new HashMap<>();
    AdviserResponse adviserResponse = AdviserResponse.newBuilder().build();
    List<String> listOfTimeoutIds = new ArrayList<>();
    List<InterruptEffect> interruptEffectList = new ArrayList<>();
    PmsStepParameters pmsStepParameters = new PmsStepParameters();

    NodeExecution originalExecution = NodeExecution.builder()
                                          .unitProgresses(unitProgressList)
                                          .uuid("12")
                                          .name("blah")
                                          .identifier("hello")
                                          .mode(ExecutionMode.ASYNC)
                                          .nodeRunInfo(nodeRunInfo)
                                          .failureInfo(failureInfo)
                                          .progressData(progressData)
                                          .adviserResponse(adviserResponse)
                                          .adviserTimeoutInstanceIds(listOfTimeoutIds)
                                          .interruptHistories(interruptEffectList)
                                          .resolvedParams(pmsStepParameters)
                                          .resolvedInputs(pmsStepParameters)
                                          .executionInputConfigured(true)
                                          .build();

    doReturn(originalExecution).when(nodeExecutionService).get(node.getOriginalNodeExecutionId());
    doReturn(originalExecution).when(nodeExecutionService).save(any());
    doNothing()
        .when(pmsGraphStepDetailsService)
        .saveNodeExecutionInfoForRetry(eq(ambiance.getPlanExecutionId()), eq(originalExecution.getUuid()), anyString());
    Reflect.on(ambianceExecutionContextHelper).set("pmsFeatureFlagService", pmsFeatureFlagService);
    doCallRealMethod().when(ambianceExecutionContextHelper).setAmbianceAndExecutionContextValues(any(), any());

    NodeExecution actual =
        identityNodeExecutionStrategyHelper.createNodeExecution(ambiance, node, notifyId, parentId, previousId);
    ArgumentCaptor<NodeExecution> mCaptor = ArgumentCaptor.forClass(NodeExecution.class);
    verify(nodeExecutionService).save(mCaptor.capture());

    assertThat(actual).isNotNull();
    assertThat(actual.getNodeId()).isEqualTo(originalExecution.getNodeId());
    assertThat(actual.getMode()).isEqualTo(originalExecution.getMode());
    assertThat(actual.getSkipGraphType()).isEqualTo(originalExecution.getSkipGraphType());
    assertThat(actual.getNodeRunInfo()).isEqualTo(originalExecution.getNodeRunInfo());
    assertThat(actual.getAdviserResponse()).isEqualTo(originalExecution.getAdviserResponse());
    assertThat(actual.getInterruptHistories()).isEqualTo(originalExecution.getInterruptHistories());
    assertThat(mCaptor.getValue().getAmbiance()).isEqualTo(ambiance);
    assertThat(mCaptor.getValue().getExecutionContext())
        .isEqualTo(AmbianceUtils.getExecutionContextFromAmbiance(ambiance));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testCreateNodeExecutionWithStrategyNode() {
    SkipType skipType = SkipType.SKIP_NODE;
    StepType stepType = StepType.newBuilder().build();

    IdentityPlanNode node = IdentityPlanNode.builder()
                                .originalNodeExecutionId("1")
                                .uuid("12")
                                .serviceName("serviceName")
                                .skipGraphType(skipType)
                                .stepType(stepType)
                                .stageFqn("Fqn")
                                .group("group")
                                .isSkipExpressionChain(true)
                                .build();

    String notifyId = "12";
    String parentId = "124";
    String previousId = "123";
    Level level = Level.newBuilder().setRuntimeId("1").build();
    Level level1 =
        Level.newBuilder()
            .setStepType(StepType.newBuilder().setType("STRATEGY").setStepCategory(StepCategory.STRATEGY).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(level).addLevels(level1).build();
    List<UnitProgress> unitProgressList = new ArrayList<>();

    UnitProgress unitProgress = UnitProgress.newBuilder().build();
    unitProgressList.add(unitProgress);

    NodeRunInfo nodeRunInfo = NodeRunInfo.newBuilder().build();
    FailureInfo failureInfo = FailureInfo.newBuilder().setErrorMessage("error").build();
    Map<String, Object> progressData = new HashMap<>();
    AdviserResponse adviserResponse = AdviserResponse.newBuilder().build();
    List<String> listOfTimeoutIds = new ArrayList<>();
    List<InterruptEffect> interruptEffectList = new ArrayList<>();
    PmsStepParameters pmsStepParameters = new PmsStepParameters();

    NodeExecution originalExecution = NodeExecution.builder()
                                          .unitProgresses(unitProgressList)
                                          .uuid("12")
                                          .name("blah")
                                          .identifier("hello")
                                          .mode(ExecutionMode.ASYNC)
                                          .nodeRunInfo(nodeRunInfo)
                                          .progressData(progressData)
                                          .adviserResponse(adviserResponse)
                                          .adviserTimeoutInstanceIds(listOfTimeoutIds)
                                          .interruptHistories(interruptEffectList)
                                          .resolvedParams(pmsStepParameters)
                                          .resolvedInputs(pmsStepParameters)
                                          .executionInputConfigured(true)
                                          .build();

    doReturn(originalExecution).when(nodeExecutionService).get(node.getOriginalNodeExecutionId());
    doReturn(originalExecution).when(nodeExecutionService).save(any());
    doNothing()
        .when(pmsGraphStepDetailsService)
        .saveNodeExecutionInfoForRetry(eq(ambiance.getPlanExecutionId()), eq(originalExecution.getUuid()), anyString());

    NodeExecution actual =
        identityNodeExecutionStrategyHelper.createNodeExecution(ambiance, node, notifyId, parentId, previousId);

    assertThat(actual).isNotNull();
    assertThat(actual.getNodeId()).isEqualTo(originalExecution.getNodeId());
    assertThat(actual.getMode()).isEqualTo(originalExecution.getMode());
    assertThat(actual.getSkipGraphType()).isEqualTo(originalExecution.getSkipGraphType());
    assertThat(actual.getNodeRunInfo()).isEqualTo(originalExecution.getNodeRunInfo());
    assertThat(actual.getAdviserResponse()).isEqualTo(originalExecution.getAdviserResponse());
    assertThat(actual.getInterruptHistories()).isEqualTo(originalExecution.getInterruptHistories());
    assertThat(actual.getFailureInfo()).isEqualTo(null);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.barriers.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.distribution.barrier.Barrier.State.DOWN;
import static io.harness.distribution.barrier.Barrier.State.ENDURE;
import static io.harness.distribution.barrier.Barrier.State.STANDING;
import static io.harness.distribution.barrier.Barrier.State.TIMED_OUT;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.plancreator.steps.barrier.BarrierStepInfo;
import io.harness.plancreator.steps.barrier.BarrierStepNode;
import io.harness.pms.contracts.execution.Status;
import io.harness.repositories.BarrierNodeRepository;
import io.harness.rule.Owner;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionType;
import io.harness.steps.barriers.beans.BarrierSetupInfo;
import io.harness.steps.barriers.beans.StageDetail;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.util.collections.Sets;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierServiceImplTest extends OrchestrationStepsTestBase {
  private static final String BARRIER_IDENTIFIER = "bar1";
  private static final String PLAN_EXECUTION_ID = "test-plan-execution-id";
  @Inject private BarrierNodeRepository barrierNodeRepository;
  @Inject private MongoTemplate mongoTemplate;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @InjectMocks @Inject BarrierServiceImpl barrierService;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldSaveBarrierNode() {
    String uuid = generateUuid();
    String planExecutionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder().uuid(uuid).identifier("identifier").planExecutionId(planExecutionId).build();
    BarrierExecutionInstance savedBarrierExecutionInstance = barrierService.save(barrierExecutionInstance);

    assertThat(savedBarrierExecutionInstance).isNotNull();
    assertThat(savedBarrierExecutionInstance.getUuid()).isEqualTo(uuid);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldUpdatePositionForStage_whenOptimizationFlagEnabled_updatesOnlyTargetedInstance() {
    String planExecutionId = generateUuid();
    String stageSetupId = generateUuid();
    String executionIdTarget = generateUuid();

    BarrierExecutionInstance instance1 =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(generateUuid())
            .planExecutionId(planExecutionId)
            .positionInfo(
                BarrierPositionInfo.builder()
                    .planExecutionId(planExecutionId)
                    .barrierPositionList(ImmutableList.of(BarrierPosition.builder().stageSetupId(stageSetupId).build()))
                    .build())
            .build();

    BarrierExecutionInstance instance2 =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(generateUuid())
            .planExecutionId(planExecutionId)
            .positionInfo(
                BarrierPositionInfo.builder()
                    .planExecutionId(planExecutionId)
                    .barrierPositionList(ImmutableList.of(BarrierPosition.builder().stageSetupId(stageSetupId).build()))
                    .build())
            .build();

    barrierService.save(instance1);
    barrierService.save(instance2);

    barrierService.updatePosition(
        BarrierPositionType.STAGE, stageSetupId, executionIdTarget, null, null, List.of(instance1), true, false);

    List<BarrierExecutionInstance> resultsForStage =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STAGE, stageSetupId);
    assertThat(resultsForStage).isNotNull();
    assertThat(resultsForStage).hasSize(2);

    BarrierExecutionInstance reloaded1 = barrierService.get(instance1.getUuid());
    BarrierExecutionInstance reloaded2 = barrierService.get(instance2.getUuid());

    assertThat(reloaded1.getPositionInfo().getBarrierPositionList()).isNotEmpty();
    assertThat(reloaded1.getPositionInfo().getBarrierPositionList().get(0).getStageRuntimeId())
        .isEqualTo(executionIdTarget);

    assertThat(reloaded2.getPositionInfo().getBarrierPositionList()).isNotEmpty();
    assertThat(reloaded2.getPositionInfo().getBarrierPositionList().get(0).getStageRuntimeId()).isNull();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldUpdatePositionForStep_whenOptimizationFlagEnabled_updatesOptimizedElementOnly() {
    String executionId = generateUuid();
    String planNodeId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance = obtainBarrierExecutionInstance();
    String planExecutionId = barrierExecutionInstance.getPlanExecutionId();

    barrierExecutionInstance.setPositionInfo(BarrierPositionInfo.builder()
                                                 .planExecutionId(planExecutionId)
                                                 .barrierPositionList(List.of(BarrierPosition.builder()
                                                                                  .stepSetupId(planNodeId)
                                                                                  .stepGroupRuntimeId("sgRuntime1")
                                                                                  .stageRuntimeId("stageRuntime1")
                                                                                  .build(),
                                                     BarrierPosition.builder()
                                                         .stepSetupId(planNodeId)
                                                         .stepGroupRuntimeId("sgRuntime2")
                                                         .stageRuntimeId("stageRuntime1")
                                                         .build(),
                                                     BarrierPosition.builder()
                                                         .stepSetupId(planNodeId)
                                                         .stepGroupRuntimeId("sgRuntime1")
                                                         .stageRuntimeId("stageRuntime2")
                                                         .build(),
                                                     BarrierPosition.builder()
                                                         .stepSetupId(planNodeId)
                                                         .stepGroupRuntimeId("sgRuntime2")
                                                         .stageRuntimeId("stageRuntime2")
                                                         .build()))
                                                 .build());
    barrierService.save(barrierExecutionInstance);

    barrierService.updatePosition(BarrierPositionType.STEP, planNodeId, executionId, "stageRuntime1", "sgRuntime1",
        List.of(barrierExecutionInstance), false, false);

    List<BarrierExecutionInstance> result =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, planNodeId);

    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList().size()).isEqualTo(4);
    for (BarrierPosition position : result.get(0).getPositionInfo().getBarrierPositionList()) {
      if ("sgRuntime1".equals(position.getStepGroupRuntimeId())
          && "stageRuntime1".equals(position.getStageRuntimeId())) {
        assertThat(position.getStepRuntimeId()).isEqualTo(executionId);
      } else {
        assertThat(position.getStepRuntimeId()).isNull();
      }
    }
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldGetSavedBarrierNode() {
    String uuid = generateUuid();
    String planExecutionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder().uuid(uuid).identifier("identifier").planExecutionId(planExecutionId).build();
    barrierService.save(barrierExecutionInstance);

    BarrierExecutionInstance savedBarrierExecutionInstance = barrierService.get(barrierExecutionInstance.getUuid());

    assertThat(savedBarrierExecutionInstance).isNotNull();
    assertThat(savedBarrierExecutionInstance.getUuid()).isEqualTo(uuid);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldSaveAllBarrierNode() {
    String identifier = "identifier";
    String planExecutionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance = BarrierExecutionInstance.builder()
                                                            .uuid(generateUuid())
                                                            .identifier(identifier)
                                                            .planExecutionId(planExecutionId)
                                                            .build();
    BarrierExecutionInstance barrierExecutionInstance1 = BarrierExecutionInstance.builder()
                                                             .uuid(generateUuid())
                                                             .identifier(identifier)
                                                             .planExecutionId(planExecutionId)
                                                             .build();
    List<BarrierExecutionInstance> savedBarrierExecutionInstances =
        barrierService.saveAll(ImmutableList.of(barrierExecutionInstance, barrierExecutionInstance1));

    assertThat(savedBarrierExecutionInstances).isNotNull();
    assertThat(savedBarrierExecutionInstances).isNotEmpty();
    assertThat(savedBarrierExecutionInstances)
        .containsExactlyInAnyOrder(barrierExecutionInstance, barrierExecutionInstance1);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldThrowInvalidRequestException() {
    String uuid = generateUuid();
    assertThatThrownBy(() -> barrierService.get(uuid))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Barrier not found for id: " + uuid);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldFindByIdentifier() {
    String identifier = generateUuid();
    String planExecutionId = generateUuid();

    BarrierExecutionInstance bar = BarrierExecutionInstance.builder()
                                       .uuid(generateUuid())
                                       .identifier(identifier)
                                       .planExecutionId(planExecutionId)
                                       .build();
    barrierNodeRepository.save(bar);

    BarrierExecutionInstance barrierExecutionInstance =
        barrierService.findByIdentifierAndPlanExecutionId(identifier, planExecutionId);

    assertThat(barrierExecutionInstance).isNotNull();
    assertThat(barrierExecutionInstance).isEqualTo(bar);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)

  public void testDeleteBarrierInstancesForNonExistentPlanExecution() {
    String identifier = generateUuid();
    String planExecutionId = generateUuid();

    BarrierExecutionInstance bar = BarrierExecutionInstance.builder()
                                       .uuid(generateUuid())
                                       .identifier(identifier)
                                       .planExecutionId(planExecutionId)
                                       .build();
    barrierNodeRepository.save(bar);

    BarrierExecutionInstance barrierExecutionInstance =
        barrierService.findByIdentifierAndPlanExecutionId(identifier, planExecutionId);

    assertThat(barrierExecutionInstance).isNotNull();
    assertThat(barrierExecutionInstance).isEqualTo(bar);

    String toBeDeletedPlanExecution = "PLAN_EXECUTION_TO_BE_DELETED";
    barrierService.deleteAllForGivenPlanExecutionId(Sets.newSet(toBeDeletedPlanExecution));

    barrierExecutionInstance = barrierService.findByIdentifierAndPlanExecutionId(identifier, planExecutionId);

    assertThat(barrierExecutionInstance).isNotNull();
    assertThat(barrierExecutionInstance).isEqualTo(bar);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)

  public void testDeleteBarrierInstancesForPartialPlanExecutionIdsDelete() {
    String identifier1 = generateUuid();
    String planExecutionId1 = generateUuid();

    // bar 1
    BarrierExecutionInstance bar = BarrierExecutionInstance.builder()
                                       .uuid(generateUuid())
                                       .identifier(identifier1)
                                       .planExecutionId(planExecutionId1)
                                       .build();
    barrierNodeRepository.save(bar);

    BarrierExecutionInstance barrierExecutionInstance =
        barrierService.findByIdentifierAndPlanExecutionId(identifier1, planExecutionId1);

    assertThat(barrierExecutionInstance).isNotNull();
    assertThat(barrierExecutionInstance).isEqualTo(bar);

    // bar2
    String identifier2 = generateUuid();
    String planExecutionId2 = generateUuid();
    bar = BarrierExecutionInstance.builder()
              .uuid(generateUuid())
              .identifier(identifier2)
              .planExecutionId(planExecutionId2)
              .build();
    barrierNodeRepository.save(bar);

    barrierExecutionInstance = barrierService.findByIdentifierAndPlanExecutionId(identifier2, planExecutionId2);

    assertThat(barrierExecutionInstance).isNotNull();
    assertThat(barrierExecutionInstance).isEqualTo(bar);

    // bar 3
    String identifier3 = generateUuid();
    String planExecutionId3 = generateUuid();
    bar = BarrierExecutionInstance.builder()
              .uuid(generateUuid())
              .identifier(identifier3)
              .planExecutionId(planExecutionId3)
              .build();
    barrierNodeRepository.save(bar);

    barrierExecutionInstance = barrierService.findByIdentifierAndPlanExecutionId(identifier3, planExecutionId3);
    assertThat(barrierExecutionInstance).isNotNull();

    barrierService.deleteAllForGivenPlanExecutionId(Sets.newSet(planExecutionId2, planExecutionId3));

    barrierExecutionInstance = barrierService.findByIdentifierAndPlanExecutionId(identifier1, planExecutionId1);
    assertThat(barrierExecutionInstance).isNotNull();

    barrierExecutionInstance = barrierService.findByIdentifierAndPlanExecutionId(identifier2, planExecutionId2);
    assertThat(barrierExecutionInstance).isNull();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldUpdateState() {
    String uuid = generateUuid();
    String planExecutionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder().uuid(uuid).identifier("identifier").planExecutionId(planExecutionId).build();
    barrierService.save(barrierExecutionInstance);

    BarrierExecutionInstance savedBarrierExecutionInstance = barrierService.get(barrierExecutionInstance.getUuid());
    assertThat(savedBarrierExecutionInstance).isNotNull();

    barrierService.updateState(savedBarrierExecutionInstance.getUuid(), DOWN);
    BarrierExecutionInstance savedBarrier = barrierService.get(savedBarrierExecutionInstance.getUuid());
    assertThat(savedBarrier).isNotNull();
    assertThat(savedBarrier.getBarrierState()).isEqualTo(DOWN);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldFindByStageIdentifierAndPlanExecutionIdAnsStateIn() {
    String planExecutionId = generateUuid();
    String stageIdentifier = generateUuid();

    List<BarrierExecutionInstance> barrierExecutionInstances = Lists.newArrayList(
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .name(generateUuid())
            .barrierState(STANDING)
            .identifier(generateUuid())
            .planExecutionId(planExecutionId)
            .setupInfo(BarrierSetupInfo.builder()
                           .stages(Sets.newSet(StageDetail.builder().identifier(stageIdentifier).build()))
                           .build())
            .build(),
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .name(generateUuid())
            .barrierState(DOWN)
            .identifier(generateUuid())
            .planExecutionId(planExecutionId)
            .setupInfo(BarrierSetupInfo.builder()
                           .stages(Sets.newSet(StageDetail.builder().identifier(stageIdentifier).build()))
                           .build())
            .build());
    mongoTemplate.insertAll(barrierExecutionInstances);

    List<BarrierExecutionInstance> barrierNodeExecutions =
        barrierService.findByStageIdentifierAndPlanExecutionIdAnsStateIn(
            stageIdentifier, planExecutionId, Sets.newSet(STANDING));

    assertThat(barrierNodeExecutions).isNotNull();
    assertThat(barrierNodeExecutions.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetBarrierSetupInfoList() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String yamlFile = "barriers.yaml";
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(yamlFile)), StandardCharsets.UTF_8);

    List<BarrierSetupInfo> barrierSetupInfoList = barrierService.getBarrierSetupInfoList(yaml);

    assertThat(barrierSetupInfoList.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowIOExceptionWhenGetBarrierSetupInfoList() {
    String incorrectYaml = "pipeline: stages: stage";
    assertThatThrownBy(() -> barrierService.getBarrierSetupInfoList(incorrectYaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Error while extracting yaml");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenGetBarrierSetupInfoList() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String yamlFile = "barriers-incorrect.yaml";
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(yamlFile)), StandardCharsets.UTF_8);

    assertThatThrownBy(() -> barrierService.getBarrierSetupInfoList(yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Barrier Identifier myBarrierId7 was not present in flowControl");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldFindByPlanNodeIdAndPlanExecutionId() {
    String identifier = "identifier";
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(identifier)
            .planExecutionId(planExecutionId)
            .positionInfo(
                BarrierPositionInfo.builder()
                    .planExecutionId(planExecutionId)
                    .barrierPositionList(ImmutableList.of(BarrierPosition.builder().stepSetupId(planNodeId).build()))
                    .build())
            .build();
    barrierService.save(barrierExecutionInstance);

    BarrierExecutionInstance result = barrierService.findByPlanNodeIdAndPlanExecutionId(planNodeId, planExecutionId);

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(barrierExecutionInstance);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldUpdatePositionForStep() {
    String identifier = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String executionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(identifier)
            .planExecutionId(planExecutionId)
            .positionInfo(
                BarrierPositionInfo.builder()
                    .planExecutionId(planExecutionId)
                    .barrierPositionList(ImmutableList.of(BarrierPosition.builder().stepSetupId(planNodeId).build()))
                    .build())
            .build();
    barrierService.save(barrierExecutionInstance);

    barrierService.updatePosition(
        BarrierPositionType.STEP, planNodeId, executionId, null, null, List.of(barrierExecutionInstance), false, false);

    List<BarrierExecutionInstance> result =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, planNodeId);

    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList()).isNotEmpty();
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList().get(0).getStepRuntimeId())
        .isEqualTo(executionId);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)

  public void shouldUpdatePositionWithAdditionalFiltersForStep() {
    // Check if `updatePosition` only updates step runtimeId if we have
    // matching stageExecutionId and stepGroupExecutionId.
    String executionId = generateUuid();
    String planNodeId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance = obtainBarrierExecutionInstance();
    String planExecutionId = barrierExecutionInstance.getPlanExecutionId();
    barrierExecutionInstance.setPositionInfo(BarrierPositionInfo.builder()
                                                 .planExecutionId(planExecutionId)
                                                 .barrierPositionList(List.of(BarrierPosition.builder()
                                                                                  .stepSetupId(planNodeId)
                                                                                  .stepGroupRuntimeId("sgRuntime1")
                                                                                  .stageRuntimeId("stageRuntime1")
                                                                                  .build(),
                                                     BarrierPosition.builder()
                                                         .stepSetupId(planNodeId)
                                                         .stepGroupRuntimeId("sgRuntime2")
                                                         .stageRuntimeId("stageRuntime1")
                                                         .build(),
                                                     BarrierPosition.builder()
                                                         .stepSetupId(planNodeId)
                                                         .stepGroupRuntimeId("sgRuntime1")
                                                         .stageRuntimeId("stageRuntime2")
                                                         .build(),
                                                     BarrierPosition.builder()
                                                         .stepSetupId(planNodeId)
                                                         .stepGroupRuntimeId("sgRuntime2")
                                                         .stageRuntimeId("stageRuntime2")
                                                         .build()))
                                                 .build());
    barrierService.save(barrierExecutionInstance);

    barrierService.updatePosition(BarrierPositionType.STEP, planNodeId, executionId, "stageRuntime1", "sgRuntime1",
        List.of(barrierExecutionInstance), false, false);

    List<BarrierExecutionInstance> result =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, planNodeId);

    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList().size()).isEqualTo(4);
    for (BarrierPosition position : result.get(0).getPositionInfo().getBarrierPositionList()) {
      if (position.getStepGroupRuntimeId().equals("sgRuntime1")
          && position.getStageRuntimeId().equals("stageRuntime1")) {
        assertThat(position.getStepRuntimeId()).isEqualTo(executionId);
      } else {
        assertThat(position.getStepRuntimeId()).isNull();
      }
    }
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldUpdatePositionForStepGroup() {
    String identifier = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String executionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(identifier)
            .planExecutionId(planExecutionId)
            .positionInfo(BarrierPositionInfo.builder()
                              .planExecutionId(planExecutionId)
                              .barrierPositionList(
                                  ImmutableList.of(BarrierPosition.builder().stepGroupSetupId(planNodeId).build()))
                              .build())
            .build();
    barrierService.save(barrierExecutionInstance);

    barrierService.updatePosition(BarrierPositionType.STEP_GROUP, planNodeId, executionId, null, null,
        List.of(barrierExecutionInstance), false, false);

    List<BarrierExecutionInstance> result =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP_GROUP, planNodeId);

    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList()).isNotEmpty();
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList().get(0).getStepGroupRuntimeId())
        .isEqualTo(executionId);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)

  public void shouldUpdatePositionWithAdditionalFiltersForStepGroup() {
    // Check if `updatePosition` only updates stepGroup runtimeId if strategyNodeType is not of type stepGroup.
    String planNodeId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance = obtainBarrierExecutionInstance();
    String planExecutionId = barrierExecutionInstance.getPlanExecutionId();
    barrierExecutionInstance.setPositionInfo(
        BarrierPositionInfo.builder()
            .planExecutionId(planExecutionId)
            .barrierPositionList(List.of(BarrierPosition.builder()
                                             .stepSetupId(planNodeId)
                                             .stepGroupSetupId("sgSetup1")
                                             .strategyNodeType(BarrierPositionType.STEP_GROUP)
                                             .stageSetupId("stageSetup1")
                                             .stageRuntimeId("stageRuntime1")
                                             .build(),
                BarrierPosition.builder()
                    .stepSetupId(planNodeId)
                    .stepGroupSetupId("sgSetup1")
                    .stageSetupId("stageSetup1")
                    .stageRuntimeId("stageRuntime1")
                    .strategyNodeType(BarrierPositionType.STAGE)
                    .build(),
                BarrierPosition.builder()
                    .stepSetupId(planNodeId)
                    .stepGroupSetupId("sgSetup1")
                    .stageSetupId("stageSetup2")
                    .stageRuntimeId("stageRuntime2")
                    .strategyNodeType(BarrierPositionType.STAGE)
                    .build(),
                BarrierPosition.builder().stepSetupId(planNodeId).stepGroupSetupId("sgSetup1").build(),
                BarrierPosition.builder().stepSetupId(planNodeId).stepGroupSetupId("sgSetup2").build(),
                BarrierPosition.builder()
                    .stepSetupId(planNodeId)
                    .stepGroupSetupId("sgSetup2")
                    .strategyNodeType(BarrierPositionType.STAGE)
                    .build()))
            .build());
    barrierService.save(barrierExecutionInstance);

    barrierService.updatePosition(BarrierPositionType.STEP_GROUP, "sgSetup1", "sgRuntime1", "stageRuntime1",
        "sgRuntime1", List.of(barrierExecutionInstance), false, false);

    List<BarrierExecutionInstance> result =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, planNodeId);

    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList().size()).isEqualTo(6);
    int updatedCount = 0;
    for (BarrierPosition position : result.get(0).getPositionInfo().getBarrierPositionList()) {
      if ("sgSetup1".equals(position.getStepGroupSetupId())
          && !BarrierPositionType.STEP_GROUP.equals(position.getStrategyNodeType())
          && "stageRuntime1".equals(position.getStageRuntimeId())) {
        assertThat(position.getStepGroupRuntimeId()).isEqualTo("sgRuntime1");
        updatedCount++;
      } else {
        assertThat(position.getStepGroupRuntimeId()).isNull();
      }
    }
    assertThat(updatedCount).isEqualTo(1);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldUpdatePositionForStage() {
    String identifier = generateUuid();
    String planExecutionId = generateUuid();
    String planNodeId = generateUuid();
    String executionId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(identifier)
            .planExecutionId(planExecutionId)
            .positionInfo(
                BarrierPositionInfo.builder()
                    .planExecutionId(planExecutionId)
                    .barrierPositionList(ImmutableList.of(BarrierPosition.builder().stageSetupId(planNodeId).build()))
                    .build())
            .build();
    barrierService.save(barrierExecutionInstance);

    barrierService.updatePosition(BarrierPositionType.STAGE, planNodeId, executionId, null, null,
        List.of(barrierExecutionInstance), false, false);

    List<BarrierExecutionInstance> result =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STAGE, planNodeId);

    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList()).isNotEmpty();
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList().get(0).getStageRuntimeId())
        .isEqualTo(executionId);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)

  public void shouldUpdatePositionWithAdditionalFiltersForStage() {
    // Check if `updatePosition` only updates stepGroup runtimeId if strategyNodeType is not of type stepGroup.
    String planNodeId = generateUuid();
    BarrierExecutionInstance barrierExecutionInstance = obtainBarrierExecutionInstance();
    String planExecutionId = barrierExecutionInstance.getPlanExecutionId();
    barrierExecutionInstance.setPositionInfo(
        BarrierPositionInfo.builder()
            .planExecutionId(planExecutionId)
            .barrierPositionList(List.of(BarrierPosition.builder()
                                             .stepSetupId(planNodeId)
                                             .stageSetupId("stageSetup1")
                                             .strategyNodeType(BarrierPositionType.STEP_GROUP)
                                             .build(),
                BarrierPosition.builder()
                    .stepSetupId(planNodeId)
                    .stageSetupId("stageSetup1")
                    .strategyNodeType(BarrierPositionType.STAGE)
                    .build(),
                BarrierPosition.builder().stepSetupId(planNodeId).stageSetupId("stageSetup1").build(),
                BarrierPosition.builder().stepSetupId(planNodeId).stageSetupId("stageSetup2").build()))
            .build());
    barrierService.save(barrierExecutionInstance);

    barrierService.updatePosition(BarrierPositionType.STAGE, "stageSetup1", "stageRuntime1", "stageRuntime1",
        "sgRuntime1", List.of(barrierExecutionInstance), false, false);

    List<BarrierExecutionInstance> result =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, planNodeId);

    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getPositionInfo().getBarrierPositionList().size()).isEqualTo(4);
    for (BarrierPosition position : result.get(0).getPositionInfo().getBarrierPositionList()) {
      if ("stageSetup1".equals(position.getStageSetupId())
          && !BarrierPositionType.STEP_GROUP.equals(position.getStrategyNodeType())
          && !BarrierPositionType.STAGE.equals(position.getStrategyNodeType())) {
        assertThat(position.getStageRuntimeId()).isEqualTo("stageRuntime1");
      } else {
        assertThat(position.getStageRuntimeId()).isNull();
      }
    }
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldTestUpdateStanding() {
    BarrierExecutionInstance barrierExecutionInstance = obtainBarrierExecutionInstance();
    barrierService.save(barrierExecutionInstance);

    when(nodeExecutionService.getWithFieldsIncluded(anyString(), any()))
        .thenReturn(NodeExecution.builder().status(Status.SUCCEEDED).build());

    barrierService.update(barrierExecutionInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierExecutionInstance.getUuid());

    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(DOWN);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldTestUpdateEndure() {
    BarrierExecutionInstance barrierExecutionInstance = obtainBarrierExecutionInstance();
    barrierService.save(barrierExecutionInstance);

    when(waitNotifyEngine.doneWith(anyString(), any())).thenReturn("");
    when(planExecutionService.getStatus(anyString())).thenReturn(Status.FAILED);

    barrierService.update(barrierExecutionInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierExecutionInstance.getUuid());

    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(ENDURE);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldTestUpdateTimedOut() {
    BarrierExecutionInstance barrierExecutionInstance = obtainBarrierExecutionInstance();
    barrierService.save(barrierExecutionInstance);

    when(waitNotifyEngine.doneWith(anyString(), any())).thenReturn("");
    when(nodeExecutionService.getWithFieldsIncluded(anyString(), any()))
        .thenReturn(NodeExecution.builder().status(Status.EXPIRED).build());

    barrierService.update(barrierExecutionInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierExecutionInstance.getUuid());

    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(TIMED_OUT);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)

  public void testUpsert() {
    String barrierId = "id1";
    String planExecutionId = "planId";
    BarrierExecutionInstance barrierExecutionInstance1 =
        BarrierExecutionInstance.builder()
            .uuid(barrierId)
            .identifier(barrierId)
            .planExecutionId(planExecutionId)
            .barrierState(STANDING)
            .setupInfo(BarrierSetupInfo.builder()
                           .stages(Set.of(StageDetail.builder().identifier("stage1").build()))
                           .strategySetupIds(Set.of("strategyId1"))
                           .build())
            .positionInfo(
                BarrierPositionInfo.builder()
                    .planExecutionId(planExecutionId)
                    .barrierPositionList(
                        List.of(BarrierPosition.builder().stageSetupId("stageId1").stepSetupId("stepSetupId1").build()))
                    .build())
            .build();
    BarrierExecutionInstance barrierExecutionInstance2 =
        BarrierExecutionInstance.builder()
            .uuid(barrierId)
            .identifier(barrierId)
            .planExecutionId(planExecutionId)
            .barrierState(STANDING)
            .setupInfo(BarrierSetupInfo.builder()
                           .stages(Set.of(StageDetail.builder().identifier("stage2").build()))
                           .strategySetupIds(Set.of("strategyId2"))
                           .build())
            .positionInfo(
                BarrierPositionInfo.builder()
                    .planExecutionId(planExecutionId)
                    .barrierPositionList(
                        List.of(BarrierPosition.builder().stageSetupId("stageId2").stepSetupId("stepSetupId2").build()))
                    .build())
            .build();
    barrierService.upsert(barrierExecutionInstance1);
    List<BarrierExecutionInstance> result =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, "stepSetupId1");
    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    BarrierExecutionInstance barrierExecutionInstance = result.get(0);
    assertThat(barrierExecutionInstance.getPositionInfo().getBarrierPositionList().size()).isEqualTo(1);
    assertThat(barrierExecutionInstance.getSetupInfo().getStages().size()).isEqualTo(1);
    assertThat(barrierExecutionInstance.getSetupInfo()
                   .getStages()
                   .stream()
                   .map(StageDetail::getIdentifier)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("stage1");
    assertThat(barrierExecutionInstance.getSetupInfo().getStrategySetupIds().size()).isEqualTo(1);
    assertThat(barrierExecutionInstance.getSetupInfo().getStrategySetupIds()).containsExactlyInAnyOrder("strategyId1");
    assertThat(barrierExecutionInstance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .map(BarrierPosition::getStageSetupId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("stageId1");
    assertThat(barrierExecutionInstance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .map(BarrierPosition::getStepSetupId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("stepSetupId1");

    barrierService.upsert(barrierExecutionInstance2);
    result = barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, "stepSetupId1");
    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    barrierExecutionInstance = result.get(0);
    assertThat(barrierExecutionInstance.getPositionInfo().getBarrierPositionList().size()).isEqualTo(2);
    assertThat(barrierExecutionInstance.getSetupInfo().getStages().size()).isEqualTo(2);
    assertThat(barrierExecutionInstance.getSetupInfo()
                   .getStages()
                   .stream()
                   .map(StageDetail::getIdentifier)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("stage1", "stage2");
    assertThat(barrierExecutionInstance.getSetupInfo().getStrategySetupIds().size()).isEqualTo(2);
    assertThat(barrierExecutionInstance.getSetupInfo().getStrategySetupIds())
        .containsExactlyInAnyOrder("strategyId1", "strategyId2");
    assertThat(barrierExecutionInstance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .map(BarrierPosition::getStageSetupId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("stageId1", "stageId2");
    assertThat(barrierExecutionInstance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .map(BarrierPosition::getStepSetupId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("stepSetupId1", "stepSetupId2");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)

  public void testUpdateBarrierPositionInfoListAndStrategyConcurrency() {
    String barrierId = "id1";
    String planExecutionId = "planId";
    BarrierPositionInfo initialPositionInfo =
        BarrierPositionInfo.builder()
            .planExecutionId(planExecutionId)
            .barrierPositionList(
                List.of(BarrierPosition.builder().stageSetupId("stageId1").stepSetupId("stepSetupId1").build(),
                    BarrierPosition.builder().stageSetupId("stageId2").stepSetupId("stepSetupId2").build()))
            .build();
    BarrierPositionInfo updatedPositionInfo =
        BarrierPositionInfo.builder()
            .planExecutionId(planExecutionId)
            .barrierPositionList(List.of(BarrierPosition.builder()
                                             .stageSetupId("stageId1")
                                             .stepSetupId("stepSetupId1")
                                             .strategyNodeType(BarrierPositionType.STAGE)
                                             .stageRuntimeId("stageRuntimeId1")
                                             .build(),
                BarrierPosition.builder()
                    .stageSetupId("stageId2")
                    .stepSetupId("stepSetupId2")
                    .strategyNodeType(BarrierPositionType.STAGE)
                    .stageRuntimeId("stageRuntimeId2")
                    .build()))
            .build();
    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder()
            .uuid(barrierId)
            .identifier(barrierId)
            .planExecutionId(planExecutionId)
            .barrierState(STANDING)
            .setupInfo(
                BarrierSetupInfo.builder().stages(Set.of(StageDetail.builder().identifier("stage1").build())).build())
            .positionInfo(initialPositionInfo)
            .build();
    barrierService.upsert(barrierExecutionInstance);
    List<BarrierExecutionInstance> result =
        barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, "stepSetupId1");
    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    BarrierExecutionInstance initialBarrierExecutionInstance = result.get(0);
    assertThat(initialBarrierExecutionInstance.getPositionInfo().getBarrierPositionList().size()).isEqualTo(2);
    assertThat(initialBarrierExecutionInstance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .map(BarrierPosition::getStageSetupId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("stageId1", "stageId2");
    assertThat(initialBarrierExecutionInstance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .map(BarrierPosition::getStepSetupId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("stepSetupId1", "stepSetupId2");
    assertThat(initialBarrierExecutionInstance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .map(BarrierPosition::getStageRuntimeId)
                   .collect(Collectors.toSet()))
        .containsOnlyNulls();
    assertThat(initialBarrierExecutionInstance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .map(BarrierPosition::getStrategyNodeType)
                   .collect(Collectors.toSet()))
        .containsOnlyNulls();

    barrierService.updateBarrierPositionInfoListAndStrategyConcurrency(
        barrierId, planExecutionId, updatedPositionInfo.getBarrierPositionList(), "strategyId", 2);
    result = barrierService.findByPosition(planExecutionId, BarrierPositionType.STEP, "stepSetupId1");
    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    BarrierExecutionInstance updatedBarrierExecutionInstance = result.get(0);
    assertThat(updatedBarrierExecutionInstance.getPositionInfo().getBarrierPositionList().size()).isEqualTo(2);
    assertThat(updatedBarrierExecutionInstance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .map(BarrierPosition::getStageSetupId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("stageId1", "stageId2");
    assertThat(updatedBarrierExecutionInstance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .map(BarrierPosition::getStepSetupId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("stepSetupId1", "stepSetupId2");
    assertThat(updatedBarrierExecutionInstance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .map(BarrierPosition::getStageRuntimeId)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("stageRuntimeId1", "stageRuntimeId2");
    assertThat(updatedBarrierExecutionInstance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .map(BarrierPosition::getStrategyNodeType)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(BarrierPositionType.STAGE);
    assertThat(updatedBarrierExecutionInstance.getSetupInfo().getStrategyConcurrencyMap().keySet())
        .containsExactlyInAnyOrder("strategyId");
    assertThat(updatedBarrierExecutionInstance.getSetupInfo().getStrategyConcurrencyMap().get("strategyId"))
        .isEqualTo(2);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)

  public void testUpsertBarrierExecutionInstance() {
    BarrierStepNode barrierField = new BarrierStepNode();
    barrierField.setUuid("barrierStepId");
    barrierField.setBarrierStepInfo(BarrierStepInfo.builder().identifier("barrierId").name("barrierName").build());

    barrierService.upsertBarrierExecutionInstance(barrierField.getUuid(),
        barrierField.getBarrierStepInfo().getIdentifier(), barrierField.getBarrierStepInfo().getName(), "executionId",
        "stepGroup", "stageId", "stepGroupId", "stepGroupId", List.of("stepGroupId"));
    List<BarrierExecutionInstance> result =
        barrierService.findByPosition("executionId", BarrierPositionType.STEP, "barrierStepId");
    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    BarrierExecutionInstance barrierExecutionInstance = result.get(0);
    assertThat(barrierExecutionInstance.getName()).isEqualTo("barrierName");
    assertThat(barrierExecutionInstance.getIdentifier()).isEqualTo("barrierId");
    assertThat(barrierExecutionInstance.getSetupInfo().getName()).isEqualTo("barrierName");
    assertThat(barrierExecutionInstance.getSetupInfo().getIdentifier()).isEqualTo("barrierId");
    assertThat(barrierExecutionInstance.getSetupInfo().getStages().size()).isEqualTo(1);
    assertThat(barrierExecutionInstance.getSetupInfo()
                   .getStages()
                   .stream()
                   .map(StageDetail::getIdentifier)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("stageId");
    assertThat(barrierExecutionInstance.getSetupInfo().getStrategySetupIds().size()).isEqualTo(1);
    assertThat(barrierExecutionInstance.getSetupInfo().getStrategySetupIds()).containsExactlyInAnyOrder("stepGroupId");
    assertThat(barrierExecutionInstance.getPositionInfo().getBarrierPositionList().size()).isEqualTo(1);
    BarrierPosition barrierPosition = barrierExecutionInstance.getPositionInfo().getBarrierPositionList().get(0);
    assertThat(barrierPosition.getStepSetupId()).isEqualTo("barrierStepId");
    assertThat(barrierPosition.getStepGroupSetupId()).isEqualTo("stepGroupId");
    assertThat(barrierPosition.getStageSetupId()).isEqualTo("stageId");
    assertThat(barrierPosition.getStrategySetupId()).isEqualTo("stepGroupId");
    assertThat(barrierPosition.getStrategyNodeType()).isEqualTo(BarrierPositionType.STEP_GROUP);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateBarrierPositionInfoList() {
    // Given
    String barrierIdentifier = "barrier-" + generateUuid();
    String planExecutionId = "plan-" + generateUuid();
    String stageSetupId = "stage-" + generateUuid();
    String stepGroupSetupId = "stepGroup-" + generateUuid();
    String stepSetupId = "step-" + generateUuid();

    // Create and save a barrier execution instance
    BarrierExecutionInstance initialBarrier =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(barrierIdentifier)
            .planExecutionId(planExecutionId)
            .name("Test Barrier")
            .barrierState(STANDING)
            .positionInfo(BarrierPositionInfo.builder()
                              .planExecutionId(planExecutionId)
                              .barrierPositionList(List.of(BarrierPosition.builder()
                                                               .stageSetupId(stageSetupId)
                                                               .stepGroupSetupId(stepGroupSetupId)
                                                               .stepSetupId(stepSetupId)
                                                               .build()))
                              .build())
            .build();

    barrierService.save(initialBarrier);

    // When - create new barrier positions and update
    String newStageSetupId = "new-stage-" + generateUuid();
    String newStepGroupSetupId = "new-stepGroup-" + generateUuid();
    String newStepSetupId = "new-step-" + generateUuid();

    List<BarrierPosition> updatedPositions = List.of(BarrierPosition.builder()
                                                         .stageSetupId(newStageSetupId)
                                                         .stepGroupSetupId(newStepGroupSetupId)
                                                         .stepSetupId(newStepSetupId)
                                                         .strategyNodeType(BarrierPositionType.STAGE)
                                                         .build());

    barrierService.updateBarrierPositionInfoList(barrierIdentifier, planExecutionId, updatedPositions);

    // Then
    BarrierExecutionInstance updatedBarrier =
        barrierService.findByIdentifierAndPlanExecutionId(barrierIdentifier, planExecutionId);

    assertThat(updatedBarrier).isNotNull();
    assertThat(updatedBarrier.getPositionInfo().getBarrierPositionList()).hasSize(1);

    BarrierPosition position = updatedBarrier.getPositionInfo().getBarrierPositionList().get(0);
    assertThat(position.getStageSetupId()).isEqualTo(newStageSetupId);
    assertThat(position.getStepGroupSetupId()).isEqualTo(newStepGroupSetupId);
    assertThat(position.getStepSetupId()).isEqualTo(newStepSetupId);
    assertThat(position.getStrategyNodeType()).isEqualTo(BarrierPositionType.STAGE);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateBarrierPositionInfoListWithMultiplePositions() {
    // Given
    String barrierIdentifier = "barrier-" + generateUuid();
    String planExecutionId = "plan-" + generateUuid();

    // Create and save a barrier execution instance with one position
    BarrierExecutionInstance initialBarrier =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(barrierIdentifier)
            .planExecutionId(planExecutionId)
            .name("Test Barrier")
            .barrierState(STANDING)
            .positionInfo(
                BarrierPositionInfo.builder()
                    .planExecutionId(planExecutionId)
                    .barrierPositionList(List.of(BarrierPosition.builder().stageSetupId("original-stage").build()))
                    .build())
            .build();

    barrierService.save(initialBarrier);

    // When - update with multiple positions
    List<BarrierPosition> updatedPositions =
        List.of(BarrierPosition.builder().stageSetupId("stage-1").strategyNodeType(BarrierPositionType.STAGE).build(),
            BarrierPosition.builder()
                .stageSetupId("stage-2")
                .stepGroupSetupId("step-group-2")
                .strategyNodeType(BarrierPositionType.STEP_GROUP)
                .build());

    barrierService.updateBarrierPositionInfoList(barrierIdentifier, planExecutionId, updatedPositions);

    // Then
    BarrierExecutionInstance updatedBarrier =
        barrierService.findByIdentifierAndPlanExecutionId(barrierIdentifier, planExecutionId);

    assertThat(updatedBarrier).isNotNull();
    assertThat(updatedBarrier.getPositionInfo().getBarrierPositionList()).hasSize(2);

    // Verify the first position
    BarrierPosition position1 = updatedBarrier.getPositionInfo().getBarrierPositionList().get(0);
    assertThat(position1.getStageSetupId()).isEqualTo("stage-1");
    assertThat(position1.getStrategyNodeType()).isEqualTo(BarrierPositionType.STAGE);

    // Verify the second position
    BarrierPosition position2 = updatedBarrier.getPositionInfo().getBarrierPositionList().get(1);
    assertThat(position2.getStageSetupId()).isEqualTo("stage-2");
    assertThat(position2.getStepGroupSetupId()).isEqualTo("step-group-2");
    assertThat(position2.getStrategyNodeType()).isEqualTo(BarrierPositionType.STEP_GROUP);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpsertBarrierExecutionInstanceWithDummyNode() {
    BarrierStepNode barrierField = new BarrierStepNode();
    barrierField.setUuid("barrierStepId");
    barrierField.setBarrierStepInfo(BarrierStepInfo.builder().identifier("barrierId").name("barrierName").build());

    barrierService.upsertBarrierExecutionInstance(null, barrierField.getBarrierStepInfo().getIdentifier(),
        barrierField.getBarrierStepInfo().getName(), null, null, null, null, null, null, true, "parentExecutionId",
        "parentPipelineStageId");
    BarrierExecutionInstance barrierExecutionInstance = barrierService.findByIdentifierAndPlanExecutionId(
        barrierField.getBarrierStepInfo().getIdentifier(), "parentExecutionId");
    assertThat(barrierExecutionInstance.getName()).isEqualTo("barrierName");
    assertThat(barrierExecutionInstance.getIdentifier()).isEqualTo("barrierId");
    assertThat(barrierExecutionInstance.getSetupInfo().getName()).isEqualTo("barrierName");
    assertThat(barrierExecutionInstance.getSetupInfo().getIdentifier()).isEqualTo("barrierId");
    assertThat(barrierExecutionInstance.getPositionInfo().getBarrierPositionList().size()).isEqualTo(1);
    BarrierPosition barrierPosition = barrierExecutionInstance.getPositionInfo().getBarrierPositionList().get(0);
    assertThat(barrierPosition.getStepSetupId()).isEqualTo(null);
    assertThat(barrierPosition.getStepGroupSetupId()).isEqualTo(null);
    assertThat(barrierPosition.getStageSetupId()).isEqualTo(null);
    assertThat(barrierPosition.getStrategySetupId()).isEqualTo(null);
    assertThat(barrierPosition.getIsDummyPositionForChildPipeline()).isEqualTo(true);
    assertThat(barrierPosition.getParentPipelineStageNodeId()).isEqualTo("parentPipelineStageId");
    assertThat(barrierPosition.getStrategyNodeType()).isEqualTo(null);
  }

  private BarrierExecutionInstance obtainBarrierExecutionInstance() {
    String identifier = generateUuid();
    String planExecutionId = generateUuid();
    String stageSetupId = generateUuid();
    String stepSetupId = generateUuid();
    String stageExecutionId = generateUuid();
    String stepExecutionId = generateUuid();
    return BarrierExecutionInstance.builder()
        .uuid(generateUuid())
        .identifier(identifier)
        .planExecutionId(planExecutionId)
        .barrierState(STANDING)
        .positionInfo(BarrierPositionInfo.builder()
                          .planExecutionId(planExecutionId)
                          .barrierPositionList(List.of(BarrierPosition.builder()
                                                           .stageSetupId(stageSetupId)
                                                           .stageRuntimeId(stageExecutionId)
                                                           .stepSetupId(stepSetupId)
                                                           .stepRuntimeId(stepExecutionId)
                                                           .build()))
                          .build())
        .build();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void shouldAtomicallyRemoveDummyBarrierPosition() {
    // Given
    String parentPipelineStageNodeId = "test-parent-pipeline-stage-node-id";
    String parentPipelineStageNodeId2 = "test-parent-pipeline-stage-node-id-2";

    // Create a barrier position with dummy position for child pipeline
    BarrierPosition dummyPosition1 = BarrierPosition.builder()
                                         .isDummyPositionForChildPipeline(true)
                                         .parentPipelineStageNodeId(parentPipelineStageNodeId)
                                         .build();

    // Create a second dummy position with different parentPipelineStageNodeId
    BarrierPosition dummyPosition2 = BarrierPosition.builder()
                                         .isDummyPositionForChildPipeline(true)
                                         .parentPipelineStageNodeId(parentPipelineStageNodeId2)
                                         .build();

    // Create a non-dummy position
    BarrierPosition regularPosition = BarrierPosition.builder()
                                          .stageSetupId("test-stage-id")
                                          .stepSetupId("test-step-id")
                                          .isDummyPosition(false)
                                          .stepGroupRollback(false)
                                          .stageRuntimeId("test-stage-runtime-id")
                                          .build();

    // Create position info with all positions
    BarrierPositionInfo positionInfo =
        BarrierPositionInfo.builder()
            .planExecutionId(PLAN_EXECUTION_ID)
            .barrierPositionList(Lists.newArrayList(dummyPosition1, dummyPosition2, regularPosition))
            .build();

    // Create barrier instance with the positions
    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(BARRIER_IDENTIFIER)
            .planExecutionId(PLAN_EXECUTION_ID)
            .barrierState(STANDING)
            .positionInfo(positionInfo)
            .setupInfo(BarrierSetupInfo.builder().identifier(BARRIER_IDENTIFIER).build())
            .build();

    // Save the barrier instance to MongoDB
    barrierService.save(barrierInstance);

    // When
    boolean result = barrierService.atomicallyRemoveDummyBarrierPosition(
        BARRIER_IDENTIFIER, PLAN_EXECUTION_ID, parentPipelineStageNodeId);

    // Then
    assertThat(result).isTrue();

    // Verify that the position was removed
    BarrierExecutionInstance updatedInstance =
        barrierService.findByIdentifierAndPlanExecutionId(BARRIER_IDENTIFIER, PLAN_EXECUTION_ID);
    assertThat(updatedInstance).isNotNull();
    assertThat(updatedInstance.getPositionInfo().getBarrierPositionList()).hasSize(2);
    assertThat(updatedInstance.getPositionInfo().getBarrierPositionList().stream().anyMatch(p
                   -> p.getParentPipelineStageNodeId() != null
                       && p.getParentPipelineStageNodeId().equals(parentPipelineStageNodeId)))
        .isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void shouldReturnFalseWhenNoDummyPositionMatches() {
    // Given
    String parentPipelineStageNodeId = "test-parent-pipeline-stage-node-id";
    String nonExistentParentPipelineStageNodeId = "non-existent-id";

    // Create a barrier position with dummy position for child pipeline
    BarrierPosition dummyPosition = BarrierPosition.builder()
                                        .isDummyPositionForChildPipeline(true)
                                        .parentPipelineStageNodeId(parentPipelineStageNodeId)
                                        .build();

    // Create position info
    BarrierPositionInfo positionInfo = BarrierPositionInfo.builder()
                                           .planExecutionId(PLAN_EXECUTION_ID)
                                           .barrierPositionList(Lists.newArrayList(dummyPosition))
                                           .build();

    // Create barrier instance
    BarrierExecutionInstance barrierInstance = BarrierExecutionInstance.builder()
                                                   .uuid(generateUuid())
                                                   .identifier("b1")
                                                   .planExecutionId("PLAN_EXECUTION_ID")
                                                   .barrierState(STANDING)
                                                   .positionInfo(positionInfo)
                                                   .setupInfo(BarrierSetupInfo.builder().identifier("b1").build())
                                                   .build();

    // Save the barrier instance to MongoDB
    barrierService.save(barrierInstance);

    // When - try to remove a position with a non-existent ID

    System.out.println("Document before update: "
        + mongoTemplate.findOne(
            query(Criteria.where("identifier").is("b1").and("planExecutionId").is("PLAN_EXECUTION_ID")),
            BarrierExecutionInstance.class));

    boolean result = barrierService.atomicallyRemoveDummyBarrierPosition(
        "b1", "PLAN_EXECUTION_ID", nonExistentParentPipelineStageNodeId);

    System.out.println("Document after update: "
        + mongoTemplate.findOne(
            query(Criteria.where("identifier").is("b1").and("planExecutionId").is("PLAN_EXECUTION_ID")),
            BarrierExecutionInstance.class));

    // Then
    assertThat(result).isFalse();

    // Verify that no positions were removed
    BarrierExecutionInstance updatedInstance =
        barrierService.findByIdentifierAndPlanExecutionId("b1", "PLAN_EXECUTION_ID");
    assertThat(updatedInstance.getPositionInfo().getBarrierPositionList()).hasSize(1);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldFilterOutDummyPositionsForChildPipeline() {
    // Given: Barrier with one dummy position for child pipeline and one valid position
    String planExecutionId = generateUuid();
    String stageSetupId = generateUuid();
    String stepSetupId = generateUuid();
    String stageRuntimeId = generateUuid();
    String stepRuntimeId = generateUuid();

    BarrierPosition dummyPosition = BarrierPosition.builder()
                                        .isDummyPositionForChildPipeline(true)
                                        .parentPipelineStageNodeId("parent-stage-id")
                                        .stepGroupRollback(false)
                                        .build();

    BarrierPosition validPosition = BarrierPosition.builder()
                                        .stageSetupId(stageSetupId)
                                        .stageRuntimeId(stageRuntimeId)
                                        .stepSetupId(stepSetupId)
                                        .stepRuntimeId(stepRuntimeId)
                                        .isDummyPosition(false)
                                        .stepGroupRollback(false)
                                        .build();

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(BARRIER_IDENTIFIER)
            .planExecutionId(planExecutionId)
            .barrierState(STANDING)
            .positionInfo(BarrierPositionInfo.builder()
                              .planExecutionId(planExecutionId)
                              .barrierPositionList(List.of(dummyPosition, validPosition))
                              .build())
            .build();

    barrierService.save(barrierInstance);

    // When: update() is called (which internally calls buildForcer())
    when(nodeExecutionService.getWithFieldsIncluded(anyString(), any()))
        .thenReturn(NodeExecution.builder().status(Status.SUCCEEDED).build());
    when(planExecutionService.getStatus(anyString())).thenReturn(Status.RUNNING);

    barrierService.update(barrierInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierInstance.getUuid());

    // Then: Barrier should go DOWN (only valid position evaluated)
    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(DOWN);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldFilterOutPositionsWithoutStepRuntimeId() {
    // Given: Barrier with positions missing stepRuntimeId
    String planExecutionId = generateUuid();
    String stageSetupId = generateUuid();
    String stepSetupId = generateUuid();
    String stageRuntimeId = generateUuid();
    String validStepRuntimeId = generateUuid();

    BarrierPosition positionWithoutStepRuntimeId = BarrierPosition.builder()
                                                       .stageSetupId(stageSetupId)
                                                       .stageRuntimeId(stageRuntimeId)
                                                       .stepSetupId(stepSetupId)
                                                       .stepRuntimeId(null) // Missing stepRuntimeId
                                                       .isDummyPosition(false)
                                                       .stepGroupRollback(false)
                                                       .build();

    BarrierPosition validPosition = BarrierPosition.builder()
                                        .stageSetupId(stageSetupId)
                                        .stageRuntimeId(stageRuntimeId)
                                        .stepSetupId(stepSetupId)
                                        .stepRuntimeId(validStepRuntimeId)
                                        .isDummyPosition(false)
                                        .stepGroupRollback(false)
                                        .build();

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(BARRIER_IDENTIFIER)
            .planExecutionId(planExecutionId)
            .barrierState(STANDING)
            .positionInfo(BarrierPositionInfo.builder()
                              .planExecutionId(planExecutionId)
                              .barrierPositionList(List.of(positionWithoutStepRuntimeId, validPosition))
                              .build())
            .build();

    barrierService.save(barrierInstance);

    // When: update() is called
    when(nodeExecutionService.getWithFieldsIncluded(eq(stageRuntimeId), any()))
        .thenReturn(NodeExecution.builder().status(Status.SUCCEEDED).build());
    when(planExecutionService.getStatus(anyString())).thenReturn(Status.RUNNING);

    barrierService.update(barrierInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierInstance.getUuid());

    // Then: Barrier should go DOWN (only valid position with stepRuntimeId evaluated)
    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(DOWN);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldHandleMultipleDummyPositionsForSkippedChainedPipelines() {
    // Given: Barrier with multiple dummy positions from different skipped child pipelines
    String planExecutionId = generateUuid();
    String stageRuntimeId = generateUuid();
    String stepRuntimeId1 = generateUuid();
    String stepRuntimeId2 = generateUuid();

    BarrierPosition dummyPosition1 = BarrierPosition.builder()
                                         .isDummyPositionForChildPipeline(true)
                                         .parentPipelineStageNodeId("skipped-child-1")
                                         .stepGroupRollback(false)
                                         .build();

    BarrierPosition dummyPosition2 = BarrierPosition.builder()
                                         .isDummyPositionForChildPipeline(true)
                                         .parentPipelineStageNodeId("skipped-child-2")
                                         .stepGroupRollback(false)
                                         .build();

    BarrierPosition validPosition1 = BarrierPosition.builder()
                                         .stageSetupId("stage1")
                                         .stageRuntimeId(stageRuntimeId)
                                         .stepSetupId("step1")
                                         .stepRuntimeId(stepRuntimeId1)
                                         .isDummyPosition(false)
                                         .strategyNodeType(BarrierPositionType.STAGE)
                                         .build();

    BarrierPosition validPosition2 = BarrierPosition.builder()
                                         .stageSetupId("stage1")
                                         .stageRuntimeId(stageRuntimeId)
                                         .stepSetupId("step1")
                                         .stepRuntimeId(stepRuntimeId2)
                                         .isDummyPosition(false)
                                         .strategyNodeType(BarrierPositionType.STAGE)
                                         .build();

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(BARRIER_IDENTIFIER)
            .planExecutionId(planExecutionId)
            .barrierState(STANDING)
            .positionInfo(
                BarrierPositionInfo.builder()
                    .planExecutionId(planExecutionId)
                    .barrierPositionList(List.of(dummyPosition1, dummyPosition2, validPosition1, validPosition2))
                    .build())
            .build();

    barrierService.save(barrierInstance);

    // When: update() is called
    when(nodeExecutionService.getWithFieldsIncluded(anyString(), any()))
        .thenReturn(NodeExecution.builder().status(Status.SUCCEEDED).build());
    when(planExecutionService.getStatus(anyString())).thenReturn(Status.RUNNING);

    barrierService.update(barrierInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierInstance.getUuid());

    // Then: Barrier should go DOWN (only 2 valid positions evaluated, 2 dummies ignored)
    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(DOWN);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldKeepStandingWhenAllPositionsAreDummy() {
    // Given: Barrier with only dummy positions (all child pipelines skipped)
    String planExecutionId = generateUuid();

    BarrierPosition dummyPosition1 = BarrierPosition.builder()
                                         .isDummyPositionForChildPipeline(true)
                                         .parentPipelineStageNodeId("skipped-child-1")
                                         .stepGroupRollback(false)
                                         .build();

    BarrierPosition dummyPosition2 = BarrierPosition.builder()
                                         .isDummyPositionForChildPipeline(true)
                                         .parentPipelineStageNodeId("skipped-child-2")
                                         .stepGroupRollback(false)
                                         .build();

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(BARRIER_IDENTIFIER)
            .planExecutionId(planExecutionId)
            .barrierState(STANDING)
            .positionInfo(BarrierPositionInfo.builder()
                              .planExecutionId(planExecutionId)
                              .barrierPositionList(List.of(dummyPosition1, dummyPosition2))
                              .build())
            .build();

    barrierService.save(barrierInstance);

    // When: update() is called
    when(planExecutionService.getStatus(anyString())).thenReturn(Status.RUNNING);

    barrierService.update(barrierInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierInstance.getUuid());

    // Then: Barrier should go STANDING (no expanded positions)
    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(STANDING);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldHandleMixedScenarioWithDummyAndIncompletePositions() {
    // Given: Barrier with mix of dummy positions, positions without stepRuntimeId, and valid positions
    String planExecutionId = generateUuid();
    String stageRuntimeId = generateUuid();
    String validStepRuntimeId = generateUuid();

    BarrierPosition dummyPosition = BarrierPosition.builder()
                                        .isDummyPositionForChildPipeline(true)
                                        .parentPipelineStageNodeId("skipped-child")
                                        .stepGroupRollback(false)
                                        .build();

    BarrierPosition incompletePosition = BarrierPosition.builder()
                                             .stageSetupId("stage1")
                                             .stageRuntimeId(stageRuntimeId)
                                             .stepSetupId("step1")
                                             .stepRuntimeId(null) // Race condition: not yet set
                                             .isDummyPosition(false)
                                             .strategyNodeType(BarrierPositionType.STAGE)
                                             .build();

    BarrierPosition validPosition = BarrierPosition.builder()
                                        .stageSetupId("stage2")
                                        .stageRuntimeId(stageRuntimeId)
                                        .stepSetupId("step2")
                                        .stepRuntimeId(validStepRuntimeId)
                                        .isDummyPosition(false)
                                        .strategyNodeType(BarrierPositionType.STAGE)
                                        .build();

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(BARRIER_IDENTIFIER)
            .planExecutionId(planExecutionId)
            .barrierState(STANDING)
            .positionInfo(BarrierPositionInfo.builder()
                              .planExecutionId(planExecutionId)
                              .barrierPositionList(List.of(dummyPosition, incompletePosition, validPosition))
                              .build())
            .build();

    barrierService.save(barrierInstance);

    // When: update() is called
    when(nodeExecutionService.getWithFieldsIncluded(eq(stageRuntimeId), any()))
        .thenReturn(NodeExecution.builder().status(Status.SUCCEEDED).build());
    when(planExecutionService.getStatus(anyString())).thenReturn(Status.RUNNING);

    barrierService.update(barrierInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierInstance.getUuid());

    // Then: Barrier should go DOWN (only 1 valid position evaluated, dummy and incomplete ignored)
    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(DOWN);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldKeepStandingWhenValidPositionNotYetArrived() {
    // Given: Barrier with dummy position and valid position that hasn't arrived yet
    String planExecutionId = generateUuid();
    String stageRuntimeId = generateUuid();
    String stepRuntimeId = generateUuid();

    BarrierPosition dummyPosition = BarrierPosition.builder()
                                        .isDummyPositionForChildPipeline(true)
                                        .parentPipelineStageNodeId("skipped-child")
                                        .stepGroupRollback(false)
                                        .build();

    BarrierPosition validPosition = BarrierPosition.builder()
                                        .stageSetupId("stage1")
                                        .stageRuntimeId(stageRuntimeId)
                                        .stepSetupId("step1")
                                        .stepRuntimeId(stepRuntimeId)
                                        .isDummyPosition(false)
                                        .build();

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(BARRIER_IDENTIFIER)
            .planExecutionId(planExecutionId)
            .barrierState(STANDING)
            .positionInfo(BarrierPositionInfo.builder()
                              .planExecutionId(planExecutionId)
                              .barrierPositionList(List.of(dummyPosition, validPosition))
                              .build())
            .build();

    barrierService.save(barrierInstance);

    // When: update() is called, but valid position is still RUNNING
    when(nodeExecutionService.getWithFieldsIncluded(eq(stageRuntimeId), any()))
        .thenReturn(NodeExecution.builder().status(Status.RUNNING).build());
    when(nodeExecutionService.getWithFieldsIncluded(eq(stepRuntimeId), any()))
        .thenReturn(NodeExecution.builder().status(Status.RUNNING).build());
    when(planExecutionService.getStatus(anyString())).thenReturn(Status.RUNNING);

    barrierService.update(barrierInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierInstance.getUuid());

    // Then: Barrier should remain STANDING (valid position not yet arrived)
    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(STANDING);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldGoEndureWhenValidPositionFails() {
    // Given: Barrier with dummy position and valid position that fails
    String planExecutionId = generateUuid();
    String stageRuntimeId = generateUuid();
    String stepRuntimeId = generateUuid();

    BarrierPosition dummyPosition = BarrierPosition.builder()
                                        .isDummyPositionForChildPipeline(true)
                                        .parentPipelineStageNodeId("skipped-child")
                                        .stepGroupRollback(false)
                                        .build();

    BarrierPosition validPosition = BarrierPosition.builder()
                                        .stageSetupId("stage1")
                                        .stageRuntimeId(stageRuntimeId)
                                        .stepSetupId("step1")
                                        .stepRuntimeId(stepRuntimeId)
                                        .isDummyPosition(false)
                                        .build();

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(BARRIER_IDENTIFIER)
            .planExecutionId(planExecutionId)
            .barrierState(STANDING)
            .positionInfo(BarrierPositionInfo.builder()
                              .planExecutionId(planExecutionId)
                              .barrierPositionList(List.of(dummyPosition, validPosition))
                              .build())
            .build();

    barrierService.save(barrierInstance);

    // When: update() is called, but valid position FAILED
    when(nodeExecutionService.getWithFieldsIncluded(eq(stageRuntimeId), any()))
        .thenReturn(NodeExecution.builder().status(Status.FAILED).build());
    when(planExecutionService.getStatus(anyString())).thenReturn(Status.RUNNING);

    barrierService.update(barrierInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierInstance.getUuid());

    // Then: Barrier should go ENDURE (position failed before arriving)
    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(ENDURE);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldFilterDummyPositionWithNullStepRuntimeId() {
    // Given: Barrier with dummy position that also has null stepRuntimeId (both conditions to filter)
    String planExecutionId = generateUuid();
    String stageRuntimeId = generateUuid();
    String stepRuntimeId = generateUuid();

    BarrierPosition dummyWithNullStepRuntime = BarrierPosition.builder()
                                                   .isDummyPositionForChildPipeline(true)
                                                   .parentPipelineStageNodeId("skipped-child")
                                                   .stepRuntimeId(null) // Also null
                                                   .stepGroupRollback(false)
                                                   .build();

    BarrierPosition validPosition = BarrierPosition.builder()
                                        .stageSetupId("stage1")
                                        .stageRuntimeId(stageRuntimeId)
                                        .stepSetupId("step1")
                                        .stepRuntimeId(stepRuntimeId)
                                        .isDummyPosition(false)
                                        .build();

    BarrierExecutionInstance barrierInstance =
        BarrierExecutionInstance.builder()
            .uuid(generateUuid())
            .identifier(BARRIER_IDENTIFIER)
            .planExecutionId(planExecutionId)
            .barrierState(STANDING)
            .positionInfo(BarrierPositionInfo.builder()
                              .planExecutionId(planExecutionId)
                              .barrierPositionList(List.of(dummyWithNullStepRuntime, validPosition))
                              .build())
            .build();

    barrierService.save(barrierInstance);

    // When: update() is called
    when(nodeExecutionService.getWithFieldsIncluded(eq(stageRuntimeId), any()))
        .thenReturn(NodeExecution.builder().status(Status.SUCCEEDED).build());
    when(planExecutionService.getStatus(anyString())).thenReturn(Status.RUNNING);

    barrierService.update(barrierInstance);
    BarrierExecutionInstance updated = barrierService.get(barrierInstance.getUuid());

    // Then: Barrier should go DOWN (dummy filtered out by both conditions)
    assertThat(updated).isNotNull();
    assertThat(updated.getBarrierState()).isEqualTo(DOWN);
  }
}

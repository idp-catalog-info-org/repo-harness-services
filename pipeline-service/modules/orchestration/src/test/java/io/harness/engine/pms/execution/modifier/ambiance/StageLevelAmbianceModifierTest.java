/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.pms.execution.modifier.ambiance;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;
import io.harness.rule.Owner;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class StageLevelAmbianceModifierTest extends CategoryTest {
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock PlanExecutionService planExecutionService;
  @InjectMocks StageLevelAmbianceModifier stageLevelAmbianceModifier;
  String accountIdentifier = "accountIdentifier";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    MockedStatic<AmbianceUtils> ambianceUtilsMock;
    ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class, invocation -> invocation.callRealMethod());
    ambianceUtilsMock
        .when(()
                  -> AmbianceUtils.checkIfFeatureFlagEnabled(
                      any(), eq(FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name())))
        .thenReturn(true);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testObtainOriginalStageExecutionIdForRollbackMode() {
    List<PostExecutionRollbackInfo> executionRollbackInfoList = new LinkedList<>();
    executionRollbackInfoList.add(PostExecutionRollbackInfo.newBuilder()
                                      .setPostExecutionRollbackStageId("stageSetupId")
                                      .setOriginalStageExecutionId("stageRuntime1")
                                      .build());
    executionRollbackInfoList.add(
        PostExecutionRollbackInfo.newBuilder()
            .setPostExecutionRollbackStageId("strategySetupId")
            .setOriginalStageExecutionId("stageRuntime2")
            .setRollbackStageStrategyMetadata(StrategyMetadata.newBuilder().setCurrentIteration(2).build())
            .build());
    executionRollbackInfoList.add(
        PostExecutionRollbackInfo.newBuilder()
            .setPostExecutionRollbackStageId("strategySetupId")
            .setOriginalStageExecutionId("stageRuntime3")
            .setRollbackStageStrategyMetadata(StrategyMetadata.newBuilder().setCurrentIteration(1).build())
            .build());
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().build();
    Level stageLevelNoStrategy = Level.newBuilder().setSetupId("stageSetupId").build();
    Ambiance ambianceNoStrategy =
        Ambiance.newBuilder()
            .addLevels(Level.newBuilder()
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).build())
                           .build())
            .setMetadata(executionMetadata)
            .setPlanExecutionId(generateUuid())
            .build();

    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(accountIdentifier, ambianceNoStrategy.getPlanExecutionId(),
            PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    doReturn(Optional.of(PlanExecution.builder().postExecutionRollbackInfos(executionRollbackInfoList).build()))
        .when(planExecutionService)
        .getWithFieldsIncludedOptional(
            ambianceNoStrategy.getPlanExecutionId(), Set.of(PlanExecutionKeys.postExecutionRollbackInfos));

    String runtimeId = stageLevelAmbianceModifier.obtainOriginalStageExecutionIdForRollbackMode(
        ambianceNoStrategy, stageLevelNoStrategy, planExecutionService);
    assertThat(runtimeId).isEqualTo("stageRuntime1");

    Level stageLevelWithStrategy =
        Level.newBuilder()
            .setSetupId("stageSetupId2")
            .setStrategyMetadata(StrategyMetadata.newBuilder().setCurrentIteration(2).build())
            .build();
    Ambiance ambianceWithStrategy =
        Ambiance.newBuilder()
            .addLevels(Level.newBuilder()
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                           .setSetupId("strategySetupId")
                           .build())
            .setMetadata(executionMetadata)
            .setPlanExecutionId(generateUuid())
            .build();
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(accountIdentifier, ambianceWithStrategy.getPlanExecutionId(),
            PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    doReturn(Optional.of(PlanExecution.builder().postExecutionRollbackInfos(executionRollbackInfoList).build()))
        .when(planExecutionService)
        .getWithFieldsIncludedOptional(
            ambianceWithStrategy.getPlanExecutionId(), Set.of(PlanExecutionKeys.postExecutionRollbackInfos));
    runtimeId = stageLevelAmbianceModifier.obtainOriginalStageExecutionIdForRollbackMode(
        ambianceWithStrategy, stageLevelWithStrategy, planExecutionService);
    assertThat(runtimeId).isEqualTo("stageRuntime2");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testModifyAmbiance() {
    Level stageLevelNoStrategy = Level.newBuilder().setSetupId("stageSetupId").setRuntimeId("runtimeId").build();
    Ambiance ambianceNoStrategy =
        Ambiance.newBuilder()
            .addLevels(Level.newBuilder()
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).build())
                           .build())
            .addLevels(stageLevelNoStrategy)
            .setMetadata(ExecutionMetadata.newBuilder().build())
            .setPlanExecutionId(generateUuid())
            .build();
    List<PostExecutionRollbackInfo> executionRollbackInfoList = new LinkedList<>();
    executionRollbackInfoList.add(PostExecutionRollbackInfo.newBuilder()
                                      .setPostExecutionRollbackStageId("stageSetupId")
                                      .setOriginalStageExecutionId("stageRuntime1")
                                      .build());

    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(accountIdentifier, ambianceNoStrategy.getPlanExecutionId(),
            PlanExecutionProjectionConstants.fieldsForPostProdRollback);
    doReturn(Optional.of(PlanExecution.builder().postExecutionRollbackInfos(executionRollbackInfoList).build()))
        .when(planExecutionService)
        .getWithFieldsIncludedOptional(
            ambianceNoStrategy.getPlanExecutionId(), Set.of(PlanExecutionKeys.postExecutionRollbackInfos));

    Ambiance modify = stageLevelAmbianceModifier.modify(ambianceNoStrategy, planExecutionService);
    assertThat(modify.getStageExecutionId()).isEqualTo(stageLevelNoStrategy.getRuntimeId());
    // no Rollback mode
    assertThat(modify.getOriginalStageExecutionIdForRollbackMode()).isEqualTo("");

    Ambiance ambianceWithRollbackNoStrategy =
        ambianceNoStrategy.toBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK).build())
            .build();
    modify = stageLevelAmbianceModifier.modify(ambianceWithRollbackNoStrategy, planExecutionService);
    assertThat(modify.getStageExecutionId()).isEqualTo(stageLevelNoStrategy.getRuntimeId());
    assertThat(modify.getOriginalStageExecutionIdForRollbackMode()).isEqualTo("stageRuntime1");
  }
}
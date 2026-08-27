/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class NodeExecutionContextUtilsTest extends CategoryTest {
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetAccountId() {
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .executionContext(ExecutionContext.newBuilder().putSetupAbstractions("accountId", "ACCOUNT_ID_1").build())
            .ambiance(Ambiance.newBuilder().putSetupAbstractions("accountId", "ACCOUNT_ID_2").build())
            .build();
    assertThat(NodeExecutionContextUtils.getAccountId(nodeExecution1)).isEqualTo("ACCOUNT_ID_1");
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .ambiance(Ambiance.newBuilder().putSetupAbstractions("accountId", "ACCOUNT_ID_2").build())
            .build();
    assertThat(NodeExecutionContextUtils.getAccountId(nodeExecution2)).isEqualTo("ACCOUNT_ID_2");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testObtainCurrentLevel() {
    Level level1 = Level.newBuilder().setIdentifier("_id1").build();
    Level level2 = Level.newBuilder().setIdentifier("_id2").build();
    Level level3 = Level.newBuilder().setIdentifier("_id3").build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .executionContext(ExecutionContext.newBuilder().addLevels(level1).addLevels(level2).build())
            .ambiance(Ambiance.newBuilder().addLevels(level2).addLevels(level3).build())
            .build();
    assertThat(NodeExecutionContextUtils.obtainCurrentLevel(nodeExecution1)).isEqualTo(level2);
    NodeExecution nodeExecution2 =
        NodeExecution.builder().ambiance(Ambiance.newBuilder().addLevels(level2).addLevels(level3).build()).build();
    assertThat(NodeExecutionContextUtils.obtainCurrentLevel(nodeExecution2)).isEqualTo(level3);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetLevelsList() {
    Level level1 = Level.newBuilder().setIdentifier("_id1").build();
    Level level2 = Level.newBuilder().setIdentifier("_id2").build();
    Level level3 = Level.newBuilder().setIdentifier("_id3").build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .executionContext(ExecutionContext.newBuilder().addLevels(level1).addLevels(level2).build())
            .ambiance(Ambiance.newBuilder().addLevels(level2).addLevels(level3).build())
            .build();
    assertThat(NodeExecutionContextUtils.getLevelList(nodeExecution1)).isEqualTo(List.of(level1, level2));
    NodeExecution nodeExecution2 =
        NodeExecution.builder().ambiance(Ambiance.newBuilder().addLevels(level2).addLevels(level3).build()).build();
    assertThat(NodeExecutionContextUtils.getLevelList(nodeExecution2)).isEqualTo(List.of(level2, level3));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetStageLevelFromExecutionContextAndGetStageRuntimeId() {
    Level level1 = Level.newBuilder()
                       .setIdentifier("_id1")
                       .setRuntimeId("runtimeId1")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                       .build();
    Level level2 = Level.newBuilder()
                       .setIdentifier("_id2")
                       .setRuntimeId("runtimeId2")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                       .build();
    Level level3 = Level.newBuilder()
                       .setIdentifier("_id3")
                       .setRuntimeId("runtimeId3")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                       .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .executionContext(ExecutionContext.newBuilder().addLevels(level1).addLevels(level2).build())
            .ambiance(Ambiance.newBuilder().addLevels(level3).build())
            .build();
    assertThat(NodeExecutionContextUtils.getStageLevelFromExecutionContext(nodeExecution1))
        .isEqualTo(Optional.of(level2));
    assertThat(NodeExecutionContextUtils.getStageRuntimeId(nodeExecution1)).isEqualTo("runtimeId2");
    NodeExecution nodeExecution2 =
        NodeExecution.builder().ambiance(Ambiance.newBuilder().addLevels(level3).build()).build();
    assertThat(NodeExecutionContextUtils.getStageLevelFromExecutionContext(nodeExecution2)).isEqualTo(Optional.empty());
    assertThatThrownBy(() -> NodeExecutionContextUtils.getStageRuntimeId(nodeExecution2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Stage not present");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCurrentStepTypeAndObtainStepIdentifier() {
    Level level1 = Level.newBuilder()
                       .setIdentifier("_id1")
                       .setRuntimeId("runtimeId1")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                       .build();
    Level level2 = Level.newBuilder()
                       .setIdentifier("_id2")
                       .setRuntimeId("runtimeId2")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                       .build();
    Level level3 = Level.newBuilder()
                       .setIdentifier("_id3")
                       .setRuntimeId("runtimeId3")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                       .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .executionContext(ExecutionContext.newBuilder().addLevels(level1).addLevels(level2).build())
            .ambiance(Ambiance.newBuilder().addLevels(level3).build())
            .build();
    assertThat(NodeExecutionContextUtils.getCurrentStepType(nodeExecution1))
        .isEqualTo(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build());
    assertThat(NodeExecutionContextUtils.obtainStepIdentifier(nodeExecution1)).isEqualTo("_id2");
    NodeExecution nodeExecution2 =
        NodeExecution.builder().ambiance(Ambiance.newBuilder().addLevels(level3).build()).build();
    assertThat(NodeExecutionContextUtils.getCurrentStepType(nodeExecution2))
        .isEqualTo(StepType.newBuilder().setStepCategory(StepCategory.STEP).build());
    assertThat(NodeExecutionContextUtils.obtainStepIdentifier(nodeExecution2)).isEqualTo("_id3");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetStrategyLevelFromExecutionContext() {
    Level level1 = Level.newBuilder()
                       .setIdentifier("_id1")
                       .setRuntimeId("runtimeId1")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                       .build();
    Level level2 = Level.newBuilder()
                       .setIdentifier("_id1_1")
                       .setRuntimeId("runtimeId2")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                       .build();
    Level level3 = Level.newBuilder()
                       .setIdentifier("_id1_2")
                       .setRuntimeId("runtimeId3")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                       .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .executionContext(ExecutionContext.newBuilder().addLevels(level1).addLevels(level2).build())
            .ambiance(Ambiance.newBuilder().addLevels(level1).addLevels(level3).build())
            .build();
    assertThat(NodeExecutionContextUtils.getStrategyLevelFromExecutionContext(nodeExecution1))
        .isEqualTo(Optional.of(level1));
    NodeExecution nodeExecution2 =
        NodeExecution.builder().ambiance(Ambiance.newBuilder().addLevels(level1).addLevels(level3).build()).build();
    assertThat(NodeExecutionContextUtils.getStrategyLevelFromExecutionContext(nodeExecution2))
        .isEqualTo(Optional.of(level1));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testObtainCurrentSetupIdAndObtainCurrentRuntimeId() {
    Level level1 = Level.newBuilder()
                       .setSetupId("setupId1")
                       .setRuntimeId("runtimeId1")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                       .build();
    Level level2 = Level.newBuilder()
                       .setSetupId("setupId2")
                       .setRuntimeId("runtimeId2")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                       .build();
    Level level3 = Level.newBuilder()
                       .setSetupId("setupId3")
                       .setRuntimeId("runtimeId3")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                       .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .executionContext(ExecutionContext.newBuilder().addLevels(level1).addLevels(level2).build())
            .ambiance(Ambiance.newBuilder().addLevels(level3).build())
            .build();
    assertThat(NodeExecutionContextUtils.obtainCurrentSetupId(nodeExecution1)).isEqualTo("setupId2");
    assertThat(NodeExecutionContextUtils.obtainCurrentRuntimeId(nodeExecution1)).isEqualTo("runtimeId2");
    NodeExecution nodeExecution2 =
        NodeExecution.builder().ambiance(Ambiance.newBuilder().addLevels(level3).build()).build();
    assertThat(NodeExecutionContextUtils.obtainCurrentSetupId(nodeExecution2)).isEqualTo("setupId3");
    assertThat(NodeExecutionContextUtils.obtainCurrentRuntimeId(nodeExecution2)).isEqualTo("runtimeId3");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetStepGroupLevelFromExecutionContext() {
    Level level1 =
        Level.newBuilder()
            .setIdentifier("_id1")
            .setRuntimeId("runtimeId1")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP_GROUP).build())
            .build();
    Level level2 =
        Level.newBuilder()
            .setIdentifier("_id2")
            .setRuntimeId("runtimeId2")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP_GROUP).build())
            .build();
    Level level3 =
        Level.newBuilder()
            .setIdentifier("_id3")
            .setRuntimeId("runtimeId3")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP_GROUP).build())
            .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .executionContext(ExecutionContext.newBuilder().addLevels(level1).addLevels(level2).build())
            .ambiance(Ambiance.newBuilder().addLevels(level3).build())
            .build();
    assertThat(NodeExecutionContextUtils.getStepGroupLevelFromExecutionContext(nodeExecution1))
        .isEqualTo(Optional.of(level2));
    NodeExecution nodeExecution2 =
        NodeExecution.builder().ambiance(Ambiance.newBuilder().addLevels(level1).addLevels(level3).build()).build();
    assertThat(NodeExecutionContextUtils.getStepGroupLevelFromExecutionContext(nodeExecution2))
        .isEqualTo(Optional.of(level3));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetNearestStepGroupLevelWithStrategyFromExecutionContext() {
    Level level1 = Level.newBuilder()
                       .setIdentifier("_id1")
                       .setRuntimeId("runtimeId1")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                       .build();
    Level level2 =
        Level.newBuilder()
            .setIdentifier("_id2")
            .setRuntimeId("runtimeId2")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP_GROUP).build())
            .build();
    Level level3 =
        Level.newBuilder()
            .setIdentifier("_id3")
            .setRuntimeId("runtimeId3")
            .setStepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP_GROUP).build())
            .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .executionContext(ExecutionContext.newBuilder().addLevels(level1).addLevels(level2).build())
            .build();
    assertThat(NodeExecutionContextUtils.getNearestStepGroupLevelWithStrategyFromExecutionContext(nodeExecution1))
        .isEqualTo(Optional.of(level2));
    NodeExecution nodeExecution2 =
        NodeExecution.builder().ambiance(Ambiance.newBuilder().addLevels(level2).addLevels(level3).build()).build();
    assertThat(NodeExecutionContextUtils.getNearestStepGroupLevelWithStrategyFromExecutionContext(nodeExecution2))
        .isEqualTo(Optional.empty());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testObtainParentLevelAndGetParentStepType() {
    Level level1 = Level.newBuilder()
                       .setIdentifier("_id1")
                       .setRuntimeId("runtimeId1")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).build())
                       .build();
    Level level2 = Level.newBuilder()
                       .setIdentifier("_id2")
                       .setRuntimeId("runtimeId2")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                       .build();
    Level level3 = Level.newBuilder()
                       .setIdentifier("_id3")
                       .setRuntimeId("runtimeId3")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                       .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .executionContext(ExecutionContext.newBuilder().addLevels(level1).addLevels(level2).build())
            .ambiance(Ambiance.newBuilder().addLevels(level3).build())
            .build();
    assertThat(NodeExecutionContextUtils.obtainParentLevel(nodeExecution1)).isEqualTo(level1);
    assertThat(NodeExecutionContextUtils.getParentStepType(nodeExecution1)).isEqualTo(level1.getStepType());
    NodeExecution nodeExecution2 =
        NodeExecution.builder().ambiance(Ambiance.newBuilder().addLevels(level3).build()).build();
    assertThat(NodeExecutionContextUtils.obtainParentLevel(nodeExecution2)).isEqualTo(null);
    assertThat(NodeExecutionContextUtils.getParentStepType(nodeExecution2)).isEqualTo(null);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testPrepareLevelRuntimeIdIndices() {
    ExecutionContext executionContext =
        ExecutionContext.newBuilder()
            .addAllLevels(ImmutableList.of(Level.newBuilder().setRuntimeId("pipelineId").build(),
                Level.newBuilder().setRuntimeId("stageId").build(), Level.newBuilder().setRuntimeId("stepId").build()))
            .build();
    assertThat(NodeExecutionContextUtils.prepareLevelRuntimeIdIndices(executionContext)).hasSize(4);
    assertThat(NodeExecutionContextUtils.prepareLevelRuntimeIdIndices(executionContext))
        .containsExactly("", "pipelineId", "pipelineId|stageId", "pipelineId|stageId|stepId");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetProjectIdentifier() {
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .executionContext(
                ExecutionContext.newBuilder().putSetupAbstractions("projectIdentifier", "PROJECT_ID_1").build())
            .ambiance(Ambiance.newBuilder().putSetupAbstractions("projectIdentifier", "PROJECT_ID_2").build())
            .build();
    assertThat(NodeExecutionContextUtils.getProjectIdentifier(nodeExecution1)).isEqualTo("PROJECT_ID_1");
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .ambiance(Ambiance.newBuilder().putSetupAbstractions("projectIdentifier", "PROJECT_ID_2").build())
            .build();
    assertThat(NodeExecutionContextUtils.getProjectIdentifier(nodeExecution2)).isEqualTo("PROJECT_ID_2");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetOrgIdentifier() {
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .executionContext(ExecutionContext.newBuilder().putSetupAbstractions("orgIdentifier", "ORG_ID_1").build())
            .ambiance(Ambiance.newBuilder().putSetupAbstractions("orgIdentifier", "ORG_ID_2").build())
            .build();
    assertThat(NodeExecutionContextUtils.getOrgIdentifier(nodeExecution1)).isEqualTo("ORG_ID_1");
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .ambiance(Ambiance.newBuilder().putSetupAbstractions("orgIdentifier", "ORG_ID_2").build())
            .build();
    assertThat(NodeExecutionContextUtils.getOrgIdentifier(nodeExecution2)).isEqualTo("ORG_ID_2");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetHarnessYamlVersion_FromExecutionContext() {
    // When executionContext has harnessYamlVersion, it should be returned
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .executionContext(ExecutionContext.newBuilder().setHarnessYamlVersion(HarnessYamlVersion.V1).build())
            .build();
    assertThat(NodeExecutionContextUtils.getHarnessYamlVersion(nodeExecution)).isEqualTo(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetHarnessYamlVersion_FallbackToAmbiance() {
    // When executionContext is null, it should fall back to ambiance
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .ambiance(Ambiance.newBuilder()
                          .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
                          .build())
            .build();
    assertThat(NodeExecutionContextUtils.getHarnessYamlVersion(nodeExecution)).isEqualTo(HarnessYamlVersion.V1);
  }
}

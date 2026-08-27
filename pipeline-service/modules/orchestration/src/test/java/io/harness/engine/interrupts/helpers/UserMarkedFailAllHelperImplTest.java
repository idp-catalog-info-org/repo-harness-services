/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SHUBHAM_CHAUDHARY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class UserMarkedFailAllHelperImplTest extends OrchestrationTestBase {
  @Mock private OrchestrationEngine engine;
  @Mock private NodeExecutionService nodeExecutionService;
  @Inject @InjectMocks private UserMarkedFailAllHelperImpl userMarkedFailAllHelper;

  @Test
  @Owner(developers = SHUBHAM_CHAUDHARY)
  @Category(UnitTests.class)
  public void testFailDiscontinuingNode_withUnitProgresses() {
    String nodeExecutionId = generateUuid();
    String interruptId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .unitProgress(UnitProgress.newBuilder().setUnitName("Execute").setStatus(UnitStatus.RUNNING).build())
            .build();

    userMarkedFailAllHelper.failDiscontinuingNode(
        ambiance, nodeExecution, InterruptType.USER_MARKED_FAIL_ALL, interruptId, InterruptConfig.newBuilder().build());

    verify(nodeExecutionService, times(1)).updateV2(eq(nodeExecutionId), any());

    ArgumentCaptor<StepResponseProto> responseCaptor = ArgumentCaptor.forClass(StepResponseProto.class);
    verify(engine, times(1)).processStepResponse(eq(ambiance), responseCaptor.capture());

    StepResponseProto stepResponse = responseCaptor.getValue();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getUnitProgressCount()).isEqualTo(1);
    assertThat(stepResponse.getUnitProgress(0).getUnitName()).isEqualTo("Execute");
    assertThat(stepResponse.getUnitProgress(0).getStatus()).isEqualTo(UnitStatus.FAILURE);
  }

  @Test
  @Owner(developers = SHUBHAM_CHAUDHARY)
  @Category(UnitTests.class)
  public void testFailDiscontinuingNode_emptyProgressDataAndUnitProgresses() {
    String nodeExecutionId = generateUuid();
    String interruptId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();

    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).build();

    userMarkedFailAllHelper.failDiscontinuingNode(
        ambiance, nodeExecution, InterruptType.USER_MARKED_FAIL_ALL, interruptId, InterruptConfig.newBuilder().build());

    ArgumentCaptor<StepResponseProto> responseCaptor = ArgumentCaptor.forClass(StepResponseProto.class);
    verify(engine, times(1)).processStepResponse(eq(ambiance), responseCaptor.capture());

    StepResponseProto stepResponse = responseCaptor.getValue();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getUnitProgressCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHUBHAM_CHAUDHARY)
  @Category(UnitTests.class)
  public void testFailDiscontinuingNode_mergesBothSourcesWithUnitProgressesTakingPrecedence() {
    String nodeExecutionId = generateUuid();
    String interruptId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();

    List<UnitProgress> progressDataUnits =
        Arrays.asList(UnitProgress.newBuilder().setUnitName("SharedUnit").setStatus(UnitStatus.RUNNING).build(),
            UnitProgress.newBuilder().setUnitName("AsyncOnlyUnit").setStatus(UnitStatus.RUNNING).build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(progressDataUnits).build();
    Map<String, Object> progressData = RecastOrchestrationUtils.toMap(unitProgressData);

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .unitProgress(UnitProgress.newBuilder().setUnitName("SharedUnit").setStatus(UnitStatus.SUCCESS).build())
            .progressData(progressData)
            .build();

    userMarkedFailAllHelper.failDiscontinuingNode(
        ambiance, nodeExecution, InterruptType.USER_MARKED_FAIL_ALL, interruptId, InterruptConfig.newBuilder().build());

    ArgumentCaptor<StepResponseProto> responseCaptor = ArgumentCaptor.forClass(StepResponseProto.class);
    verify(engine, times(1)).processStepResponse(eq(ambiance), responseCaptor.capture());

    StepResponseProto stepResponse = responseCaptor.getValue();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getUnitProgressCount()).isEqualTo(2);

    boolean foundShared = false;
    boolean foundAsyncOnly = false;
    for (UnitProgress up : stepResponse.getUnitProgressList()) {
      if ("SharedUnit".equals(up.getUnitName())) {
        assertThat(up.getStatus())
            .as("SharedUnit should use status from unitProgresses (primary source)")
            .isEqualTo(UnitStatus.SUCCESS);
        foundShared = true;
      }
      if ("AsyncOnlyUnit".equals(up.getUnitName())) {
        // AsyncOnlyUnit was RUNNING in progressData, but evaluateUnitProgressesFromProgressData converts
        // all non-final statuses to FAILURE before merging
        assertThat(up.getStatus()).as("AsyncOnlyUnit should be added from progressData").isEqualTo(UnitStatus.FAILURE);
        foundAsyncOnly = true;
      }
    }
    assertThat(foundShared).as("SharedUnit from unitProgresses should be present").isTrue();
    assertThat(foundAsyncOnly).as("AsyncOnlyUnit from progressData should be merged").isTrue();
  }

  @Test
  @Owner(developers = SHUBHAM_CHAUDHARY)
  @Category(UnitTests.class)
  public void testFailDiscontinuingNode_malformedProgressData_fallsBackToUnitProgresses() {
    String nodeExecutionId = generateUuid();
    String interruptId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();

    // Simulate malformed progressData that will cause ClassCastException in evaluateUnitProgressesFromProgressData
    Map<String, Object> malformedProgressData =
        Map.of("unitProgresses", List.of(Map.of("unitName", "BadUnit", "status", "RUNNING")));

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .unitProgress(UnitProgress.newBuilder().setUnitName("Execute").setStatus(UnitStatus.RUNNING).build())
            .progressData(malformedProgressData)
            .build();

    userMarkedFailAllHelper.failDiscontinuingNode(
        ambiance, nodeExecution, InterruptType.USER_MARKED_FAIL_ALL, interruptId, InterruptConfig.newBuilder().build());

    ArgumentCaptor<StepResponseProto> responseCaptor = ArgumentCaptor.forClass(StepResponseProto.class);
    verify(engine, times(1)).processStepResponse(eq(ambiance), responseCaptor.capture());

    StepResponseProto stepResponse = responseCaptor.getValue();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getUnitProgressCount()).isEqualTo(1);
    assertThat(stepResponse.getUnitProgress(0).getUnitName()).isEqualTo("Execute");
    assertThat(stepResponse.getUnitProgress(0).getStatus()).isEqualTo(UnitStatus.FAILURE);
  }
}

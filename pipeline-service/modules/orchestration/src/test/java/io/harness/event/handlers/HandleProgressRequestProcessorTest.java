/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.rule.OwnerRule.SOUMYAJIT;
import static io.harness.rule.OwnerRule.SOUMYO_PURKAYASTHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.interrupts.helpers.InterruptHelper;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.pms.contracts.execution.events.AddStepDetailsInstanceRequest;
import io.harness.pms.contracts.execution.events.HandleProgressRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bson.Document;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.mongodb.core.query.Update;

public class HandleProgressRequestProcessorTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks HandleProgressRequestProcessor handleProgressRequestProcessor;
  @Mock NodeExecutionService nodeExecutionService;

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateHandleEvent() {
    handleProgressRequestProcessor.handleEvent(
        SdkResponseEventProto.newBuilder()
            .setStepDetailsInstanceRequest(
                AddStepDetailsInstanceRequest.newBuilder().setStepDetails("{\"a\":\"b\"}").build())
            .build());
    verify(nodeExecutionService, times(1)).updateV2ForNonFinalStatusNodeExecution(any(), any());
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void shouldRouteTimestampedUnitProgressThroughTimestampFence() {
    List<UnitProgress> unitProgresses =
        List.of(UnitProgress.newBuilder().setUnitName("Apply").setStatus(UnitStatus.SUCCESS).build());
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(unitProgresses).timestamp(123L).build();
    String progressJson = RecastOrchestrationUtils.toJson(unitProgressData);

    handleProgressRequestProcessor.handleEvent(
        SdkResponseEventProto.newBuilder()
            .setProgressRequest(HandleProgressRequest.newBuilder().setProgressJson(progressJson).build())
            .build());

    verify(nodeExecutionService, times(1)).updateWithUnitProgressTimestampFence(any(), eq(123L), any());
    verify(nodeExecutionService, never()).updateV2ForNonFinalStatusNodeExecution(any(), any());
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void shouldKeepBlindSetWhenUnitProgressIsUntimestamped() {
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(List.of()).build();
    String progressJson = RecastOrchestrationUtils.toJson(unitProgressData);

    handleProgressRequestProcessor.handleEvent(
        SdkResponseEventProto.newBuilder()
            .setProgressRequest(HandleProgressRequest.newBuilder().setProgressJson(progressJson).build())
            .build());

    verify(nodeExecutionService, times(1)).updateV2ForNonFinalStatusNodeExecution(any(), any());
    verify(nodeExecutionService, never()).updateWithUnitProgressTimestampFence(any(), anyLong(), any());
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void shouldKeepBlindSetWhenProgressDocIsNotUnitProgressData() {
    handleProgressRequestProcessor.handleEvent(
        SdkResponseEventProto.newBuilder()
            .setProgressRequest(HandleProgressRequest.newBuilder().setProgressJson("{\"foo\":\"bar\"}").build())
            .build());

    verify(nodeExecutionService, times(1)).updateV2ForNonFinalStatusNodeExecution(any(), any());
    verify(nodeExecutionService, never()).updateWithUnitProgressTimestampFence(any(), anyLong(), any());
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void shouldPreserveProgressDataShapeForTimestampedUnitProgress() {
    List<UnitProgress> unitProgresses =
        List.of(UnitProgress.newBuilder().setUnitName("Fetch Files").setStatus(UnitStatus.SUCCESS).build(),
            UnitProgress.newBuilder().setUnitName("Apply").setStatus(UnitStatus.RUNNING).build());
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(unitProgresses).timestamp(123L).build();
    String progressJson = RecastOrchestrationUtils.toJson(unitProgressData);

    final Consumer<Update>[] capturedPayloadOps = new Consumer[1];
    doAnswer(invocation -> {
      capturedPayloadOps[0] = invocation.getArgument(2);
      return null;
    })
        .when(nodeExecutionService)
        .updateWithUnitProgressTimestampFence(any(), eq(123L), any());

    handleProgressRequestProcessor.handleEvent(
        SdkResponseEventProto.newBuilder()
            .setProgressRequest(HandleProgressRequest.newBuilder().setProgressJson(progressJson).build())
            .build());

    Update update = new Update();
    capturedPayloadOps[0].accept(update);
    Document setDoc = update.getUpdateObject().get("$set", Document.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> writtenProgressData = (Map<String, Object>) setDoc.get(NodeExecutionKeys.progressData);

    // Written progressData must keep the full recast-map shape so existing readers keep working.
    NodeExecution nodeExecution = NodeExecution.builder().progressData(writtenProgressData).build();
    List<UnitProgress> roundTripped =
        InterruptHelper.evaluateUnitProgressesFromProgressData(nodeExecution, UnitStatus.EXPIRED);

    assertThat(roundTripped).hasSize(2);
    assertThat(roundTripped.stream().map(UnitProgress::getUnitName)).containsExactly("Fetch Files", "Apply");
    assertThat(roundTripped.stream().map(UnitProgress::getStatus))
        .containsExactly(UnitStatus.SUCCESS, UnitStatus.EXPIRED);
  }
}

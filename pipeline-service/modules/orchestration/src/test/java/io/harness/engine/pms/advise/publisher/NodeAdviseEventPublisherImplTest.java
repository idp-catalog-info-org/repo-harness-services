/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.publisher;

import static io.harness.beans.FeatureName.PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.InterruptEffect;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviseEvent;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.TimeoutIssuer;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)

public class NodeAdviseEventPublisherImplTest extends OrchestrationTestBase {
  @Mock private PmsEventSender eventSender;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PmsFeatureFlagHelper featureFlagHelper;
  @Inject @InjectMocks private NodeAdviseEventPublisherImpl publisher;

  @Before
  public void setup() {
    doNothing().when(eventSender).sendEvent(any(), any(), any(), nullable(String.class), anyBoolean(), anyBoolean());
    doNothing().when(nodeExecutionService).markNodesProcessing(any(), anyBoolean());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @Ignore("prashant: break into multiple tests and fix this")
  public void shouldTestPublishEvent() throws InvalidProtocolBufferException {
    String planExecutionId = generateUuid();
    PlanNode planNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("IDENTIFIER")
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("type").buildPartial()).build())
            .serviceName("serviceName")
            .build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .ambiance(ambiance)
            .notifyId(generateUuid())
            .failureInfo(
                FailureInfo.newBuilder().addFailureData(FailureData.newBuilder().setCode("200").build()).build())
            .interruptHistories(ImmutableList.of(
                InterruptEffect.builder()
                    .interruptType(InterruptType.ABORT)
                    .interruptConfig(
                        InterruptConfig.newBuilder()
                            .setIssuedBy(
                                IssuedBy.newBuilder()
                                    .setTimeoutIssuer(
                                        TimeoutIssuer.newBuilder().setTimeoutInstanceId(generateUuid()).build())
                                    .buildPartial())
                            .build())
                    .build()))
            .status(Status.RUNNING)
            .retryIds(new ArrayList<>())
            .nodeId(planNode.getUuid())
            .build();

    doNothing().when(eventSender).sendEvent(any(), any(), any(), anyString(), anyBoolean(), eq(false));

    publisher.publishEvent(nodeExecution, planNode, Status.ABORTED);

    ArgumentCaptor<AdviseEvent> argumentCaptor = ArgumentCaptor.forClass(AdviseEvent.class);
    verify(eventSender).sendEvent(any(), argumentCaptor.capture(), any(), anyString(), anyBoolean(), eq(false));

    AdviseEvent advisingEvent = argumentCaptor.getValue();
    assertThat(advisingEvent.getNotifyId()).isEqualTo(nodeExecution.getNotifyId());
    assertThat(advisingEvent.getAmbiance()).isEqualTo(nodeExecution.getAmbiance());
    assertThat(advisingEvent.getToStatus()).isEqualTo(nodeExecution.getStatus());
    assertThat(advisingEvent.getRetryIdsList()).isEqualTo(nodeExecution.getRetryIds());

    nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .ambiance(ambiance)
            .failureInfo(
                FailureInfo.newBuilder().addFailureData(FailureData.newBuilder().setCode("200").build()).build())
            .interruptHistories(ImmutableList.of(
                InterruptEffect.builder()
                    .interruptType(InterruptType.ABORT)
                    .interruptConfig(
                        InterruptConfig.newBuilder()
                            .setIssuedBy(
                                IssuedBy.newBuilder()
                                    .setTimeoutIssuer(
                                        TimeoutIssuer.newBuilder().setTimeoutInstanceId(generateUuid()).build())
                                    .buildPartial())
                            .build())
                    .build()))
            .status(Status.RUNNING)
            .retryIds(new ArrayList<>())
            .build();

    publisher.publishEvent(nodeExecution, planNode, Status.ABORTED);
    argumentCaptor = ArgumentCaptor.forClass(AdviseEvent.class);
    verify(eventSender, times(2))
        .sendEvent(any(), argumentCaptor.capture(), any(), anyString(), anyBoolean(), eq(false));
    advisingEvent = argumentCaptor.getValue();

    // nodeExecution.getNotifyId is null, so advisingEvent.getNotifyId will come empty.
    assertThat(advisingEvent.getNotifyId()).isEmpty();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldMarkProcessingTrueWhenV2Enabled() {
    String planExecutionId = generateUuid();
    String accountId = generateUuid();
    String nodeUuid = generateUuid();

    // V2 disabled means the FF is enabled
    doReturn(false).when(featureFlagHelper).isEnabled(accountId, PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2);

    PlanNode planNode =
        PlanNode.builder().uuid(generateUuid()).identifier("IDENTIFIER").serviceName("serviceName").build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                            .build();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeUuid)
                                      .ambiance(ambiance)
                                      .status(Status.RUNNING)
                                      .retryIds(new ArrayList<>())
                                      .build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);

    publisher.publishEvent(nodeExecution, planNode, Status.ABORTED);

    // Verify markNodesProcessing is called with true when v2 is disabled
    verify(nodeExecutionService).markNodesProcessing(Collections.singletonList(nodeUuid), true);
    verify(eventSender).sendEvent(any(), any(), any(), nullable(String.class), anyBoolean(), eq(false));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldMarkProcessingWhenV2Enabled() {
    String planExecutionId = generateUuid();
    String accountId = generateUuid();
    String nodeUuid = generateUuid();

    // V2 enabled means the FF is disabled
    doReturn(false).when(featureFlagHelper).isEnabled(accountId, PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2);

    PlanNode planNode =
        PlanNode.builder().uuid(generateUuid()).identifier("IDENTIFIER").serviceName("serviceName").build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                            .build();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeUuid)
                                      .ambiance(ambiance)
                                      .status(Status.RUNNING)
                                      .retryIds(new ArrayList<>())
                                      .build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);

    publisher.publishEvent(nodeExecution, planNode, Status.ABORTED);

    // Verify markNodesProcessing is NOT called when v2 is enabled
    verify(nodeExecutionService).markNodesProcessing(any(), anyBoolean());
    verify(eventSender).sendEvent(any(), any(), any(), nullable(String.class), anyBoolean(), eq(false));
  }
}

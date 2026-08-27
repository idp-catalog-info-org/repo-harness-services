/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.beans.FeatureName.PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InitiateNodeHandlerTest extends OrchestrationTestBase {
  @Mock OrchestrationEngine engine;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock BlockExecutionMetadataService blockExecutionMetadataService;
  @Inject @InjectMocks private InitiateNodeHandler initiateNodeHandler;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void extractMetricContext() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .build();
    assertThat(initiateNodeHandler.extractMetricContext(new HashMap<>(), event, "RANDOM_STREAM"))
        .isEqualTo(ImmutableMap.<String, String>builder()
                       .put(PmsEventMonitoringConstants.MODULE, "pms")
                       .put(PmsEventMonitoringConstants.EVENT_TYPE, "trigger_node_event")
                       .put(PmsEventMonitoringConstants.STREAM_NAME, "RANDOM_STREAM")
                       .build());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void getMetricPrefix() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .build();
    assertThat(initiateNodeHandler.getEventType(event)).isEqualTo("trigger_node_event");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void extraLogProperties() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .build();
    assertThat(initiateNodeHandler.extraLogProperties(event)).isEqualTo(ImmutableMap.of());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void extractAmbiance() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .build();
    assertThat(initiateNodeHandler.extractAmbiance(event)).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void handleEventWithContextWithMatrixFeatureEnabled() {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId(generateUuid()).putSetupAbstractions("accountId", "accountId").build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .setInitiateMode(InitiateMode.CREATE_AND_START)
                                  .build();
    initiateNodeHandler.handleEventWithContext(event);
    verify(engine).initiateNode(eq(ambiance), eq(event.getNodeId()), eq(event.getRuntimeId()), eq(null), eq(null),
        eq(InitiateMode.CREATE_AND_START));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void handleEventWithContext_startMode_whenStuckMonitorV2Enabled_marksNodesProcessingAndQueuesExecution() {
    String nodeExecutionId = generateUuid();
    String accountId = "accountId";
    Level level = Level.newBuilder().setRuntimeId(nodeExecutionId).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .putSetupAbstractions("accountId", accountId)
                            .addLevels(level)
                            .build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .setInitiateMode(InitiateMode.START)
                                  .build();

    when(blockExecutionMetadataService.validate(ambiance)).thenReturn(false);
    when(pmsFeatureFlagHelper.isEnabled(accountId, PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2)).thenReturn(false);

    initiateNodeHandler.handleEventWithContext(event);

    verify(nodeExecutionService).markNodesProcessing(Collections.singletonList(nodeExecutionId), true);
    verify(engine).queueOrStartExecution(ambiance);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void handleEventWithContext_startMode_whenStuckMonitorV2Disabled_doesNotMarkNodesProcessing() {
    String nodeExecutionId = generateUuid();
    String accountId = "accountId";
    Level level = Level.newBuilder().setRuntimeId(nodeExecutionId).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .putSetupAbstractions("accountId", accountId)
                            .addLevels(level)
                            .build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .setInitiateMode(InitiateMode.START)
                                  .build();

    when(blockExecutionMetadataService.validate(ambiance)).thenReturn(false);
    when(pmsFeatureFlagHelper.isEnabled(accountId, PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2)).thenReturn(true);

    initiateNodeHandler.handleEventWithContext(event);

    verify(nodeExecutionService, never()).markNodesProcessing(anyList(), anyBoolean());
    verify(engine).queueOrStartExecution(ambiance);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void handleEventWithContext_whenBlockValidationFails_returnsEarlyWithoutCallingEngine() {
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId(generateUuid()).putSetupAbstractions("accountId", "accountId").build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(generateUuid())
                                  .setRuntimeId(generateUuid())
                                  .setInitiateMode(InitiateMode.START)
                                  .build();

    when(blockExecutionMetadataService.validate(ambiance)).thenReturn(true);

    initiateNodeHandler.handleEventWithContext(event);

    verify(engine, never()).queueOrStartExecution(any());
    verify(engine, never()).initiateNode(any(), any(), any(), any(), any(), any());
    verify(nodeExecutionService, never()).markNodesProcessing(anyList(), anyBoolean());
  }
}

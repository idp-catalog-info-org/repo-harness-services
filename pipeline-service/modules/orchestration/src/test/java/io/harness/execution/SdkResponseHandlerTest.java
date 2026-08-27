/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.beans.FeatureName.PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
@OwnedBy(HarnessTeam.PIPELINE)
public class SdkResponseHandlerTest extends OrchestrationTestBase {
  String planExecutionId = generateUuid();
  String accountId = generateUuid();
  SdkResponseEventProto event = SdkResponseEventProto.newBuilder()
                                    .setSdkResponseEventType(SdkResponseEventType.ADD_EXECUTABLE_RESPONSE)
                                    .setAmbiance(Ambiance.newBuilder()
                                                     .setPlanExecutionId(planExecutionId)
                                                     .addLevels(Level.newBuilder().setRuntimeId("RID").build())
                                                     .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                                                     .build())
                                    .build();
  @Mock OrchestrationEngine engine;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject @InjectMocks SdkResponseHandler sdkResponseHandler;

  @Before
  public void setup() {
    doNothing().when(engine).handleSdkResponseEvent(any());
    doNothing().when(nodeExecutionService).markNodesProcessing(any(), anyBoolean());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testExtraLogProperties() {
    Map<String, String> map = sdkResponseHandler.extraLogProperties(event);
    assertEquals(map.get("eventType"), SdkResponseEventType.ADD_EXECUTABLE_RESPONSE.name());
    assertEquals(map.get("nodeExecutionId"), "RID");
    assertEquals(map.get("planExecutionId"), planExecutionId);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testExtractAmbiance() {
    Ambiance ambiance = sdkResponseHandler.extractAmbiance(event);
    assertEquals(ambiance.getPlanExecutionId(), planExecutionId);
    assertEquals(ambiance.getLevels(0).getRuntimeId(), "RID");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetMetricPrefix() {
    assertEquals(sdkResponseHandler.getEventType(event), "ADD_EXECUTABLE_RESPONSE");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleEventWithContext() {
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(anyString(), eq(PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2));
    sdkResponseHandler.handleEventWithContext(event);
    ArgumentCaptor<SdkResponseEventProto> mCaptor = ArgumentCaptor.forClass(SdkResponseEventProto.class);
    verify(engine).handleSdkResponseEvent(mCaptor.capture());
    assertEquals(mCaptor.getValue().toByteString(), event.toByteString());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldMarkProcessingFalseBeforeHandlingSdkResponseWhenV2Enabled() {
    // V2 enabled means the FF is disabled
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(accountId, PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2);

    sdkResponseHandler.handleEventWithContext(event);

    // Verify markNodesProcessing is called before handleSdkResponseEvent
    InOrder inOrder = Mockito.inOrder(nodeExecutionService, engine);
    inOrder.verify(nodeExecutionService).markNodesProcessing(Collections.singletonList("RID"), false);
    inOrder.verify(engine).handleSdkResponseEvent(event);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldMarkProcessingFalseAfterHandlingSdkResponseWhenV2Disabled() {
    // V2 disabled means the FF is enabled
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2);

    sdkResponseHandler.handleEventWithContext(event);

    // Verify markNodesProcessing is called after handleSdkResponseEvent
    InOrder inOrder = Mockito.inOrder(nodeExecutionService, engine);
    inOrder.verify(engine).handleSdkResponseEvent(event);
    inOrder.verify(nodeExecutionService).markNodesProcessing(Collections.singletonList("RID"), false);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void shouldFailNodeWhenMarkProcessingThrowsWhenV2Enabled() {
    // V2 enabled means the FF is disabled
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(accountId, PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2);
    // A transient Mongo failure while marking processing (after retries) must fail the node fast rather than leaving
    // it stuck in async-wait until the pipeline timeout (PIPE-35791)
    RuntimeException markException = new RuntimeException("Prematurely reached end of stream");
    doThrow(markException).when(nodeExecutionService).markNodesProcessing(Collections.singletonList("RID"), false);

    sdkResponseHandler.handleEventWithContext(event);

    // The node is failed via handleError and the SDK response event is NOT handled
    verify(nodeExecutionService).markNodesProcessing(Collections.singletonList("RID"), false);
    verify(engine).handleError(event.getAmbiance(), markException);
    verify(engine, never()).handleSdkResponseEvent(event);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldNotMarkProcessingForIgnoreEventsWhenV2Enabled() {
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(accountId, PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2);

    SdkResponseEventProto ignoreEvent = SdkResponseEventProto.newBuilder()
                                            .setSdkResponseEventType(SdkResponseEventType.HANDLE_PROGRESS)
                                            .setAmbiance(event.getAmbiance())
                                            .build();

    sdkResponseHandler.handleEventWithContext(ignoreEvent);

    // Verify markNodesProcessing is NOT called for ignore events
    verify(nodeExecutionService, never()).markNodesProcessing(any(), anyBoolean());
    verify(engine, times(1)).handleSdkResponseEvent(ignoreEvent);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldNotMarkProcessingForIgnoreEventsWhenV2Disabled() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, PIPE_DISABLE_STUCK_EXECUTION_MONITOR_V2);

    SdkResponseEventProto ignoreEvent = SdkResponseEventProto.newBuilder()
                                            .setSdkResponseEventType(SdkResponseEventType.UNKNOWN_EVENT_TYPE)
                                            .setAmbiance(event.getAmbiance())
                                            .build();

    sdkResponseHandler.handleEventWithContext(ignoreEvent);

    // Verify markNodesProcessing is NOT called for ignore events (old flow)
    verify(nodeExecutionService, never()).markNodesProcessing(any(), anyBoolean());
    verify(engine, times(1)).handleSdkResponseEvent(ignoreEvent);
  }
}

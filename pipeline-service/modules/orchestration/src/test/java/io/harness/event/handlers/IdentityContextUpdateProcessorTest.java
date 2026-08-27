/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.IdentityExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.IdentityContextUpdateResponse;
import io.harness.pms.contracts.execution.events.AddExecutableResponseRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.rule.Owner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityContextUpdateProcessorTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @InjectMocks private IdentityContextUpdateProcessor processor;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void verifyMocks() {
    verifyNoMoreInteractions(nodeExecutionService);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testHandleEvent_Success() {
    String nodeExecutionId = generateUuid();
    IdentityExecutionContext newContext = IdentityExecutionContext.newBuilder().build();

    NodeExecution mockExecution =
        NodeExecution.builder().executionContext(ExecutionContext.newBuilder().build()).build();
    when(nodeExecutionService.getWithFieldsIncluded(eq(nodeExecutionId), anySet())).thenReturn(mockExecution);

    processor.handleEvent(buildEvent(nodeExecutionId, newContext));

    verify(nodeExecutionService).getWithFieldsIncluded(eq(nodeExecutionId), anySet());
    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testHandleEvent_NodeNotFound() {
    String nodeExecutionId = generateUuid();

    when(nodeExecutionService.getWithFieldsIncluded(eq(nodeExecutionId), anySet())).thenReturn(null);

    processor.handleEvent(buildEvent(nodeExecutionId, IdentityExecutionContext.newBuilder().build()));

    verify(nodeExecutionService).getWithFieldsIncluded(eq(nodeExecutionId), anySet());
    verify(nodeExecutionService, never()).updateV2(any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testHandleEvent_NoExecutionContext() {
    String nodeExecutionId = generateUuid();
    // NodeExecution exists but has no executionContext
    NodeExecution mockExecution = NodeExecution.builder().build();

    when(nodeExecutionService.getWithFieldsIncluded(eq(nodeExecutionId), anySet())).thenReturn(mockExecution);

    processor.handleEvent(buildEvent(nodeExecutionId, IdentityExecutionContext.newBuilder().build()));

    verify(nodeExecutionService).getWithFieldsIncluded(eq(nodeExecutionId), anySet());
    verify(nodeExecutionService, never()).updateV2(any(), any());
  }

  private SdkResponseEventProto buildEvent(String nodeExecutionId, IdentityExecutionContext newContext) {
    AddExecutableResponseRequest request =
        AddExecutableResponseRequest.newBuilder()
            .setExecutableResponse(
                ExecutableResponse.newBuilder()
                    .setIdentityContextUpdate(
                        IdentityContextUpdateResponse.newBuilder().setUpdatedContext(newContext).build())
                    .build())
            .build();

    return SdkResponseEventProto.newBuilder()
        .setAmbiance(Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build()).build())
        .setAddExecutableResponseRequest(request)
        .setSdkResponseEventType(SdkResponseEventType.IDENTITY_CONTEXT_UPDATE)
        .build();
  }
}

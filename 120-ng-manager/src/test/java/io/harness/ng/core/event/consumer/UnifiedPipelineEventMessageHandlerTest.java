/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.consumer;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.models.UnifiedDeploymentDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.UnifiedDeploymentEvent;
import io.harness.rule.Owner;
import io.harness.service.instancesync.unified.UnifiedInstanceSyncHelper;
import io.harness.service.instancesync.unified.UnifiedInstanceSyncService;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UnifiedPipelineEventMessageHandlerTest extends CategoryTest {
  @Mock private UnifiedInstanceSyncHelper unifiedInstanceSyncHelper;
  @Mock private UnifiedInstanceSyncService unifiedInstanceSyncService;

  private UnifiedPipelineEventMessageHandler handler;

  @Before
  public void setup() {
    handler = new UnifiedPipelineEventMessageHandler(unifiedInstanceSyncHelper, unifiedInstanceSyncService);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testOnMessage_delegatesToServiceViaHelper() {
    UnifiedDeploymentDTO dto = UnifiedDeploymentDTO.builder().ambiance(Ambiance.getDefaultInstance()).build();
    when(unifiedInstanceSyncService.isEnabled(anyString())).thenReturn(true);
    when(unifiedInstanceSyncHelper.createNewDeploymentDTO(any())).thenReturn(dto);

    UnifiedDeploymentEvent event = buildEvent();
    handler.onMessage(event, buildMetadata(), new HashMap<>());

    verify(unifiedInstanceSyncHelper).createNewDeploymentDTO(event);
    verify(unifiedInstanceSyncService).processInstanceSyncForNewDeployment(dto);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testOnMessage_helperThrowsException_doesNotPropagate() {
    when(unifiedInstanceSyncService.isEnabled(anyString())).thenReturn(true);
    when(unifiedInstanceSyncHelper.createNewDeploymentDTO(any())).thenThrow(new RuntimeException("helper failure"));

    handler.onMessage(buildEvent(), buildMetadata(), new HashMap<>());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testOnMessage_serviceThrowsException_doesNotPropagate() {
    UnifiedDeploymentDTO dto = UnifiedDeploymentDTO.builder().ambiance(Ambiance.getDefaultInstance()).build();
    when(unifiedInstanceSyncService.isEnabled(anyString())).thenReturn(true);
    when(unifiedInstanceSyncHelper.createNewDeploymentDTO(any())).thenReturn(dto);
    doThrow(new RuntimeException("sync failure"))
        .when(unifiedInstanceSyncService)
        .processInstanceSyncForNewDeployment(any());

    handler.onMessage(buildEvent(), buildMetadata(), new HashMap<>());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testOnMessage_whenServiceDisabled_doesNotCallHelper() {
    when(unifiedInstanceSyncService.isEnabled(anyString())).thenReturn(false);

    handler.onMessage(buildEvent(), buildMetadata(), new HashMap<>());

    verify(unifiedInstanceSyncHelper, never()).createNewDeploymentDTO(any());
    verify(unifiedInstanceSyncService, never()).processInstanceSyncForNewDeployment(any());
  }

  private UnifiedDeploymentEvent buildEvent() {
    return UnifiedDeploymentEvent.newBuilder()
        .setAmbiance(Ambiance.getDefaultInstance())
        .setStepStatus(Status.SUCCEEDED)
        .build();
  }

  private Map<String, String> buildMetadata() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("accountId", "testAccountId");
    metadata.put("planExecutionId", "testPlanExecId");
    metadata.put("nodeExecutionId", "testNodeExecId");
    return metadata;
  }
}

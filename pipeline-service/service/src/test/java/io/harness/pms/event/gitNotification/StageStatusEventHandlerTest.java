/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.gitNotification;

import static io.harness.rule.OwnerRule.MOHD_FAIZ;

import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.stage.StageStatusEvent;
import io.harness.pms.notification.gitstatus.GitStatusUpdateNotifier;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StageStatusEventHandlerTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private GitStatusUpdateNotifier gitStatusUpdateNotifier;
  @Mock private PmsFeatureFlagService featureFlagService;
  private StageStatusEventHandler stageStatusEventHandler;

  private static final String ACCOUNT_ID = "accountId";
  private static final String NODE_EXECUTION_ID = "nodeExecutionId";
  private AutoCloseable closeable;

  @Before
  public void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
    stageStatusEventHandler = new StageStatusEventHandler();
    on(stageStatusEventHandler).set("nodeExecutionService", nodeExecutionService);
    on(stageStatusEventHandler).set("gitStatusUpdateNotifier", gitStatusUpdateNotifier);
    on(stageStatusEventHandler).set("featureFlagService", featureFlagService);
  }

  @After
  public void releaseMocks() throws Exception {
    closeable.close();
  }

  private StageStatusEvent buildEvent() {
    return StageStatusEvent.newBuilder().setAccountIdentifier(ACCOUNT_ID).setNodeExecutionId(NODE_EXECUTION_ID).build();
  }

  private NodeExecution mockNodeExecutionAndAmbiance() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    Ambiance ambiance = Ambiance.newBuilder().build();
    when(nodeExecutionService.get(NODE_EXECUTION_ID)).thenReturn(nodeExecution);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    return nodeExecution;
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testHandleEvent_WhenBothFlagsDisabled_DoesNothing() {
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(false);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED)).thenReturn(true);

    stageStatusEventHandler.handleEventWithContext(buildEvent());

    verify(nodeExecutionService, never()).get(anyString());
    verify(gitStatusUpdateNotifier, never()).onNodeStatusUpdate(any(), any());
    verify(gitStatusUpdateNotifier, never()).onGitOpsNodeStatusUpdate(any(), any());
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testHandleEvent_WhenOnlySendStatusToGitFlagEnabled_TriggersNodeStatusNotifierOnly() {
    NodeExecution nodeExecution = mockNodeExecutionAndAmbiance();
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(true);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED)).thenReturn(true);

    stageStatusEventHandler.handleEventWithContext(buildEvent());

    verify(gitStatusUpdateNotifier, times(1)).onNodeStatusUpdate(eq(nodeExecution), any());
    verify(gitStatusUpdateNotifier, never()).onGitOpsNodeStatusUpdate(any(), any());
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testHandleEvent_WhenOnlyGitOpsFlagEnabled_TriggersGitOpsNotifierOnly() {
    NodeExecution nodeExecution = mockNodeExecutionAndAmbiance();
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(false);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED))
        .thenReturn(false);

    stageStatusEventHandler.handleEventWithContext(buildEvent());

    verify(gitStatusUpdateNotifier, never()).onNodeStatusUpdate(any(), any());
    verify(gitStatusUpdateNotifier, times(1)).onGitOpsNodeStatusUpdate(eq(nodeExecution), any());
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testHandleEvent_WhenBothFlagsEnabled_TriggersBothNotifiers() {
    NodeExecution nodeExecution = mockNodeExecutionAndAmbiance();
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(true);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED))
        .thenReturn(false);

    stageStatusEventHandler.handleEventWithContext(buildEvent());

    verify(gitStatusUpdateNotifier, times(1)).onNodeStatusUpdate(eq(nodeExecution), any());
    verify(gitStatusUpdateNotifier, times(1)).onGitOpsNodeStatusUpdate(eq(nodeExecution), any());
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testHandleEvent_WhenEventNull_DoesNothing() {
    stageStatusEventHandler.handleEventWithContext(null);

    verify(nodeExecutionService, never()).get(anyString());
    verify(gitStatusUpdateNotifier, never()).onNodeStatusUpdate(any(), any());
    verify(gitStatusUpdateNotifier, never()).onGitOpsNodeStatusUpdate(any(), any());
  }
}

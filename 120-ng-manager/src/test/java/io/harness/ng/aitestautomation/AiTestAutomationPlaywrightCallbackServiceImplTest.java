/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.aitestautomation;

import static io.harness.rule.OwnerRule.SARTHAK_DALMIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightCallbackRequest;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightExecutionData;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.AI)
public class AiTestAutomationPlaywrightCallbackServiceImplTest extends CategoryTest {
  private static final String BUILD_RUN_ID = "build-run-123";
  private static final String BUILD_NAME = "my-playwright-build";
  private static final String BUILD_RUN_URL = "https://app.harness.io/build-run/123";

  @Mock private WaitNotifyEngine waitNotifyEngine;

  private AiTestAutomationPlaywrightCallbackServiceImpl callbackService;

  @Before
  public void setup() {
    callbackService = new AiTestAutomationPlaywrightCallbackServiceImpl(waitNotifyEngine);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionSuccess() {
    AiTestAutomationPlaywrightCallbackRequest request = AiTestAutomationPlaywrightCallbackRequest.builder()
                                                            .status("Pass")
                                                            .buildRunId(BUILD_RUN_ID)
                                                            .buildName(BUILD_NAME)
                                                            .buildRunUrl(BUILD_RUN_URL)
                                                            .message("10 total, 10 passed, 0 failed")
                                                            .build();

    when(waitNotifyEngine.doneWith(eq(BUILD_RUN_ID), any(AiTestAutomationPlaywrightExecutionData.class)))
        .thenReturn("notify-id");

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isTrue();

    ArgumentCaptor<AiTestAutomationPlaywrightExecutionData> captor =
        ArgumentCaptor.forClass(AiTestAutomationPlaywrightExecutionData.class);
    verify(waitNotifyEngine).doneWith(eq(BUILD_RUN_ID), captor.capture());

    AiTestAutomationPlaywrightExecutionData data = captor.getValue();
    assertThat(data.getPhase()).isEqualTo("DONE");
    assertThat(data.isSuccess()).isTrue();
    assertThat(data.isAborted()).isFalse();
    assertThat(data.getBuildName()).isEqualTo(BUILD_NAME);
    assertThat(data.getBuildRunUrl()).isEqualTo(BUILD_RUN_URL);
    assertThat(data.getMessage()).isEqualTo("10 total, 10 passed, 0 failed");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionFailed() {
    AiTestAutomationPlaywrightCallbackRequest request = AiTestAutomationPlaywrightCallbackRequest.builder()
                                                            .status("Fail")
                                                            .buildRunId(BUILD_RUN_ID)
                                                            .buildName(BUILD_NAME)
                                                            .message("10 total, 7 passed, 3 failed")
                                                            .build();

    when(waitNotifyEngine.doneWith(eq(BUILD_RUN_ID), any(AiTestAutomationPlaywrightExecutionData.class)))
        .thenReturn("notify-id");

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isTrue();

    ArgumentCaptor<AiTestAutomationPlaywrightExecutionData> captor =
        ArgumentCaptor.forClass(AiTestAutomationPlaywrightExecutionData.class);
    verify(waitNotifyEngine).doneWith(eq(BUILD_RUN_ID), captor.capture());

    AiTestAutomationPlaywrightExecutionData data = captor.getValue();
    assertThat(data.getPhase()).isEqualTo("FAILED");
    assertThat(data.isSuccess()).isFalse();
    assertThat(data.isAborted()).isFalse();
    assertThat(data.getMessage()).isEqualTo("10 total, 7 passed, 3 failed");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionAborted() {
    AiTestAutomationPlaywrightCallbackRequest request = AiTestAutomationPlaywrightCallbackRequest.builder()
                                                            .status("Abort")
                                                            .buildRunId(BUILD_RUN_ID)
                                                            .buildName(BUILD_NAME)
                                                            .build();

    when(waitNotifyEngine.doneWith(eq(BUILD_RUN_ID), any(AiTestAutomationPlaywrightExecutionData.class)))
        .thenReturn("notify-id");

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isTrue();

    ArgumentCaptor<AiTestAutomationPlaywrightExecutionData> captor =
        ArgumentCaptor.forClass(AiTestAutomationPlaywrightExecutionData.class);
    verify(waitNotifyEngine).doneWith(eq(BUILD_RUN_ID), captor.capture());

    AiTestAutomationPlaywrightExecutionData data = captor.getValue();
    assertThat(data.getPhase()).isEqualTo("ABORTED");
    assertThat(data.isSuccess()).isFalse();
    assertThat(data.isAborted()).isTrue();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionNullBuildRunId() {
    AiTestAutomationPlaywrightCallbackRequest request =
        AiTestAutomationPlaywrightCallbackRequest.builder().status("Pass").buildRunId(null).build();

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isFalse();
    verify(waitNotifyEngine, never()).doneWith(any(), any());
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionNullMessage() {
    AiTestAutomationPlaywrightCallbackRequest request = AiTestAutomationPlaywrightCallbackRequest.builder()
                                                            .status("Pass")
                                                            .buildRunId(BUILD_RUN_ID)
                                                            .buildName(BUILD_NAME)
                                                            .message(null)
                                                            .build();

    when(waitNotifyEngine.doneWith(eq(BUILD_RUN_ID), any(AiTestAutomationPlaywrightExecutionData.class)))
        .thenReturn("notify-id");

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isTrue();

    ArgumentCaptor<AiTestAutomationPlaywrightExecutionData> captor =
        ArgumentCaptor.forClass(AiTestAutomationPlaywrightExecutionData.class);
    verify(waitNotifyEngine).doneWith(eq(BUILD_RUN_ID), captor.capture());

    AiTestAutomationPlaywrightExecutionData data = captor.getValue();
    assertThat(data.getMessage()).isNull();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionWaitNotifyReturnsNull() {
    AiTestAutomationPlaywrightCallbackRequest request = AiTestAutomationPlaywrightCallbackRequest.builder()
                                                            .status("Pass")
                                                            .buildRunId(BUILD_RUN_ID)
                                                            .buildName(BUILD_NAME)
                                                            .build();

    when(waitNotifyEngine.doneWith(eq(BUILD_RUN_ID), any(AiTestAutomationPlaywrightExecutionData.class)))
        .thenReturn(null);

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionExceptionHandled() {
    AiTestAutomationPlaywrightCallbackRequest request = AiTestAutomationPlaywrightCallbackRequest.builder()
                                                            .status("Pass")
                                                            .buildRunId(BUILD_RUN_ID)
                                                            .buildName(BUILD_NAME)
                                                            .build();

    when(waitNotifyEngine.doneWith(eq(BUILD_RUN_ID), any(AiTestAutomationPlaywrightExecutionData.class)))
        .thenThrow(new RuntimeException("WaitNotify error"));

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionWithMessage() {
    AiTestAutomationPlaywrightCallbackRequest request =
        AiTestAutomationPlaywrightCallbackRequest.builder()
            .status("Pass")
            .buildRunId(BUILD_RUN_ID)
            .buildName(BUILD_NAME)
            .message("20 total, 15 passed, 0 failed, 3 skipped, 2 flaky")
            .build();

    when(waitNotifyEngine.doneWith(eq(BUILD_RUN_ID), any(AiTestAutomationPlaywrightExecutionData.class)))
        .thenReturn("notify-id");

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isTrue();

    ArgumentCaptor<AiTestAutomationPlaywrightExecutionData> captor =
        ArgumentCaptor.forClass(AiTestAutomationPlaywrightExecutionData.class);
    verify(waitNotifyEngine).doneWith(eq(BUILD_RUN_ID), captor.capture());

    AiTestAutomationPlaywrightExecutionData data = captor.getValue();
    assertThat(data.getMessage()).isEqualTo("20 total, 15 passed, 0 failed, 3 skipped, 2 flaky");
  }
}

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
import io.harness.aitestautomation.models.AiTestAutomationCallbackRequest;
import io.harness.aitestautomation.models.AiTestExecutionData;
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
public class AiTestAutomationCallbackServiceImplTest extends CategoryTest {
  private static final String JOB_ID = "test-suite-run-123";
  private static final String TEST_SUITE_NAME = "Login Suite";
  private static final String TEST_SUITE_ID = "ts-456";
  private static final String ENV_NAME = "staging";
  private static final String ENV_ID = "env-789";
  private static final String NOTIFY_ID = "notify-id";

  @Mock private WaitNotifyEngine waitNotifyEngine;

  private AiTestAutomationCallbackServiceImpl callbackService;

  @Before
  public void setup() {
    callbackService = new AiTestAutomationCallbackServiceImpl(waitNotifyEngine);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionSuccess() {
    AiTestAutomationCallbackRequest request =
        AiTestAutomationCallbackRequest.builder()
            .status("Pass")
            .testSuite(AiTestAutomationCallbackRequest.TestSuiteInfo.builder()
                           .runId(JOB_ID)
                           .name(TEST_SUITE_NAME)
                           .id(TEST_SUITE_ID)
                           .build())
            .environment(AiTestAutomationCallbackRequest.EnvironmentInfo.builder().name(ENV_NAME).id(ENV_ID).build())
            .testResults(
                AiTestAutomationCallbackRequest.TestResults.builder().total("10").passed("10").failed("0").build())
            .reportLink("https://report.link")
            .detailsUrl("https://details.url")
            .build();

    when(waitNotifyEngine.doneWith(eq(JOB_ID), any(AiTestExecutionData.class))).thenReturn(NOTIFY_ID);

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isTrue();

    ArgumentCaptor<AiTestExecutionData> captor = ArgumentCaptor.forClass(AiTestExecutionData.class);
    verify(waitNotifyEngine).doneWith(eq(JOB_ID), captor.capture());

    AiTestExecutionData data = captor.getValue();
    assertThat(data.getPhase()).isEqualTo("DONE");
    assertThat(data.isSuccess()).isTrue();
    assertThat(data.getExecutionId()).isEqualTo(JOB_ID);
    assertThat(data.getTotalTests()).isEqualTo("10");
    assertThat(data.getPassedTests()).isEqualTo("10");
    assertThat(data.getFailedTests()).isEqualTo("0");
    assertThat(data.getReportUrl()).isEqualTo("https://report.link");
    assertThat(data.getDetailsUrl()).isEqualTo("https://details.url");
    assertThat(data.getTestSuiteName()).isEqualTo(TEST_SUITE_NAME);
    assertThat(data.getTestSuiteId()).isEqualTo(TEST_SUITE_ID);
    assertThat(data.getEnvironmentName()).isEqualTo(ENV_NAME);
    assertThat(data.getEnvironmentId()).isEqualTo(ENV_ID);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionFailed() {
    AiTestAutomationCallbackRequest request =
        AiTestAutomationCallbackRequest.builder()
            .status("Fail")
            .testSuite(AiTestAutomationCallbackRequest.TestSuiteInfo.builder()
                           .runId(JOB_ID)
                           .name(TEST_SUITE_NAME)
                           .id(TEST_SUITE_ID)
                           .build())
            .testResults(
                AiTestAutomationCallbackRequest.TestResults.builder().total("10").passed("7").failed("3").build())
            .build();

    when(waitNotifyEngine.doneWith(eq(JOB_ID), any(AiTestExecutionData.class))).thenReturn(NOTIFY_ID);

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isTrue();

    ArgumentCaptor<AiTestExecutionData> captor = ArgumentCaptor.forClass(AiTestExecutionData.class);
    verify(waitNotifyEngine).doneWith(eq(JOB_ID), captor.capture());

    AiTestExecutionData data = captor.getValue();
    assertThat(data.getPhase()).isEqualTo("FAILED");
    assertThat(data.isSuccess()).isFalse();
    assertThat(data.getFailedTests()).isEqualTo("3");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionNullJobId() {
    AiTestAutomationCallbackRequest request = AiTestAutomationCallbackRequest.builder()
                                                  .status("Pass")
                                                  .testSuite(AiTestAutomationCallbackRequest.TestSuiteInfo.builder()
                                                                 .runId(null)
                                                                 .name(TEST_SUITE_NAME)
                                                                 .id(TEST_SUITE_ID)
                                                                 .build())
                                                  .build();

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isFalse();
    verify(waitNotifyEngine, never()).doneWith(any(), any());
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionNullTestSuite() {
    AiTestAutomationCallbackRequest request =
        AiTestAutomationCallbackRequest.builder().status("Pass").testSuite(null).build();

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isFalse();
    verify(waitNotifyEngine, never()).doneWith(any(), any());
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionNullTestResults() {
    AiTestAutomationCallbackRequest request = AiTestAutomationCallbackRequest.builder()
                                                  .status("Pass")
                                                  .testSuite(AiTestAutomationCallbackRequest.TestSuiteInfo.builder()
                                                                 .runId(JOB_ID)
                                                                 .name(TEST_SUITE_NAME)
                                                                 .id(TEST_SUITE_ID)
                                                                 .build())
                                                  .testResults(null)
                                                  .environment(null)
                                                  .build();

    when(waitNotifyEngine.doneWith(eq(JOB_ID), any(AiTestExecutionData.class))).thenReturn(NOTIFY_ID);

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isTrue();

    ArgumentCaptor<AiTestExecutionData> captor = ArgumentCaptor.forClass(AiTestExecutionData.class);
    verify(waitNotifyEngine).doneWith(eq(JOB_ID), captor.capture());

    AiTestExecutionData data = captor.getValue();
    assertThat(data.getTotalTests()).isNull();
    assertThat(data.getPassedTests()).isNull();
    assertThat(data.getFailedTests()).isNull();
    assertThat(data.getEnvironmentName()).isNull();
    assertThat(data.getEnvironmentId()).isNull();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNotifyCompletionExceptionHandled() {
    AiTestAutomationCallbackRequest request = AiTestAutomationCallbackRequest.builder()
                                                  .status("Pass")
                                                  .testSuite(AiTestAutomationCallbackRequest.TestSuiteInfo.builder()
                                                                 .runId(JOB_ID)
                                                                 .name(TEST_SUITE_NAME)
                                                                 .id(TEST_SUITE_ID)
                                                                 .build())
                                                  .build();

    when(waitNotifyEngine.doneWith(eq(JOB_ID), any(AiTestExecutionData.class)))
        .thenThrow(new RuntimeException("WaitNotify error"));

    boolean result = callbackService.notifyCompletion(request);

    assertThat(result).isFalse();
  }
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.loadtest;

import static io.harness.rule.OwnerRule.UTKARSH_UTKARSH;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.loadtest.LoadTestStepNotifyData;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LoadTestServiceImplTest extends CategoryTest {
  @Mock private WaitNotifyEngine waitNotifyEngine;

  private LoadTestServiceImpl loadTestService;

  private static final String NOTIFY_ID = "test-notify-id";

  @Before
  public void setup() {
    loadTestService = new LoadTestServiceImpl(waitNotifyEngine);
  }

  @Test
  @Owner(developers = UTKARSH_UTKARSH)
  @Category(UnitTests.class)
  public void testNotifyStep_Success() {
    LoadTestStepNotifyData notifyData =
        LoadTestStepNotifyData.builder().status("Finished").runId("run-1").totalRequests(100L).build();

    when(waitNotifyEngine.doneWith(eq(NOTIFY_ID), any(LoadTestStepNotifyData.class))).thenReturn(NOTIFY_ID);

    loadTestService.notifyStep(NOTIFY_ID, notifyData);

    verify(waitNotifyEngine).doneWith(NOTIFY_ID, notifyData);
  }

  @Test
  @Owner(developers = UTKARSH_UTKARSH)
  @Category(UnitTests.class)
  public void testNotifyStep_Exception() {
    LoadTestStepNotifyData notifyData = LoadTestStepNotifyData.builder().status("Finished").build();

    doThrow(new RuntimeException("Test exception"))
        .when(waitNotifyEngine)
        .doneWith(eq(NOTIFY_ID), any(LoadTestStepNotifyData.class));

    assertThatThrownBy(() -> loadTestService.notifyStep(NOTIFY_ID, notifyData))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Test exception");
  }

  @Test
  @Owner(developers = UTKARSH_UTKARSH)
  @Category(UnitTests.class)
  public void testNotifyStep_WithAllMetrics() {
    LoadTestStepNotifyData notifyData = LoadTestStepNotifyData.builder()
                                            .status("Finished")
                                            .runId("run-2")
                                            .totalRequests(1000L)
                                            .totalFailures(5L)
                                            .errorRate(0.5)
                                            .avgResponseMs(120.5)
                                            .p95ResponseMs(250.0)
                                            .p99ResponseMs(500.0)
                                            .totalRPS(50.0)
                                            .currentUsers(10)
                                            .build();

    when(waitNotifyEngine.doneWith(eq(NOTIFY_ID), any(LoadTestStepNotifyData.class))).thenReturn(NOTIFY_ID);

    loadTestService.notifyStep(NOTIFY_ID, notifyData);

    verify(waitNotifyEngine).doneWith(NOTIFY_ID, notifyData);
  }

  @Test
  @Owner(developers = UTKARSH_UTKARSH)
  @Category(UnitTests.class)
  public void testNotifyStep_WithNullStatus() {
    LoadTestStepNotifyData notifyData = LoadTestStepNotifyData.builder().status(null).runId(null).build();

    when(waitNotifyEngine.doneWith(eq(NOTIFY_ID), any(LoadTestStepNotifyData.class))).thenReturn(NOTIFY_ID);

    loadTestService.notifyStep(NOTIFY_ID, notifyData);

    verify(waitNotifyEngine).doneWith(NOTIFY_ID, notifyData);
  }
}

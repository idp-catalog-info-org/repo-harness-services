/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.loadtest;

import static io.harness.rule.OwnerRule.UTKARSH_UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.loadtest.LoadTestStepNotifyData;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LoadTestResourceTest extends CategoryTest {
  @Mock private LoadTestService loadTestService;

  private LoadTestResource loadTestResource;

  private static final String NOTIFY_ID = "test-notify-id";

  @Before
  public void setup() {
    loadTestResource = new LoadTestResource(loadTestService);
  }

  @Test
  @Owner(developers = UTKARSH_UTKARSH)
  @Category(UnitTests.class)
  public void testLoadTestStepNotify_Success() {
    LoadTestStepNotifyData notifyData = LoadTestStepNotifyData.builder().status("Finished").runId("run-1").build();
    LoadTestStepNotifyResponse request =
        LoadTestStepNotifyResponse.builder().notifyId(NOTIFY_ID).data(notifyData).build();

    doNothing().when(loadTestService).notifyStep(anyString(), any(LoadTestStepNotifyData.class));

    ResponseDTO<Boolean> response = loadTestResource.loadTestStepNotify(request);

    assertThat(response.getData()).isTrue();
    verify(loadTestService).notifyStep(NOTIFY_ID, notifyData);
  }

  @Test
  @Owner(developers = UTKARSH_UTKARSH)
  @Category(UnitTests.class)
  public void testLoadTestStepNotify_Exception() {
    LoadTestStepNotifyData notifyData = LoadTestStepNotifyData.builder().status("Finished").build();
    LoadTestStepNotifyResponse request =
        LoadTestStepNotifyResponse.builder().notifyId(NOTIFY_ID).data(notifyData).build();

    doThrow(new RuntimeException("Test exception"))
        .when(loadTestService)
        .notifyStep(anyString(), any(LoadTestStepNotifyData.class));

    ResponseDTO<Boolean> response = loadTestResource.loadTestStepNotify(request);

    assertThat(response.getData()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_UTKARSH)
  @Category(UnitTests.class)
  public void testLoadTestStepNotify_NullRequest() {
    ResponseDTO<Boolean> response = loadTestResource.loadTestStepNotify(null);

    assertThat(response.getData()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_UTKARSH)
  @Category(UnitTests.class)
  public void testLoadTestStepNotify_WithMetrics() {
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
    LoadTestStepNotifyResponse request =
        LoadTestStepNotifyResponse.builder().notifyId(NOTIFY_ID).data(notifyData).build();

    doNothing().when(loadTestService).notifyStep(anyString(), any(LoadTestStepNotifyData.class));

    ResponseDTO<Boolean> response = loadTestResource.loadTestStepNotify(request);

    assertThat(response.getData()).isTrue();
    verify(loadTestService).notifyStep(NOTIFY_ID, notifyData);
  }
}

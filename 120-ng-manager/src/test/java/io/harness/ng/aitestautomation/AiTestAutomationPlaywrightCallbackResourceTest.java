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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightCallbackRequest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.AI)
public class AiTestAutomationPlaywrightCallbackResourceTest extends CategoryTest {
  @Mock private AiTestAutomationPlaywrightCallbackService callbackService;

  private AiTestAutomationPlaywrightCallbackResource resource;

  @Before
  public void setup() {
    resource = new AiTestAutomationPlaywrightCallbackResource(callbackService);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testAiBuildRunNotifySuccess() {
    AiTestAutomationPlaywrightCallbackRequest request = AiTestAutomationPlaywrightCallbackRequest.builder()
                                                            .status("Pass")
                                                            .buildRunId("build-run-123")
                                                            .buildName("my-build")
                                                            .message("10 total, 10 passed, 0 failed")
                                                            .build();

    when(callbackService.notifyCompletion(any(AiTestAutomationPlaywrightCallbackRequest.class))).thenReturn(true);

    ResponseDTO<Boolean> response = resource.aiBuildRunNotify(request);

    assertThat(response.getData()).isTrue();
    verify(callbackService).notifyCompletion(request);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testAiBuildRunNotifyFailure() {
    AiTestAutomationPlaywrightCallbackRequest request = AiTestAutomationPlaywrightCallbackRequest.builder()
                                                            .status("Fail")
                                                            .buildRunId("build-run-456")
                                                            .buildName("failed-build")
                                                            .build();

    when(callbackService.notifyCompletion(any(AiTestAutomationPlaywrightCallbackRequest.class))).thenReturn(false);

    ResponseDTO<Boolean> response = resource.aiBuildRunNotify(request);

    assertThat(response.getData()).isFalse();
    verify(callbackService).notifyCompletion(request);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testAiBuildRunNotifyExceptionHandled() {
    AiTestAutomationPlaywrightCallbackRequest request = AiTestAutomationPlaywrightCallbackRequest.builder()
                                                            .status("Pass")
                                                            .buildRunId("build-run-789")
                                                            .buildName("error-build")
                                                            .build();

    when(callbackService.notifyCompletion(any(AiTestAutomationPlaywrightCallbackRequest.class)))
        .thenThrow(new RuntimeException("Service error"));

    ResponseDTO<Boolean> response = resource.aiBuildRunNotify(request);

    assertThat(response.getData()).isFalse();
  }
}

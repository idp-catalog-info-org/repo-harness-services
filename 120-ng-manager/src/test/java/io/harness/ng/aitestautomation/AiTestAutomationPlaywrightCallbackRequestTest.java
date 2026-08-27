/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.aitestautomation;

import static io.harness.rule.OwnerRule.SARTHAK_DALMIA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightCallbackRequest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.AI)
public class AiTestAutomationPlaywrightCallbackRequestTest extends CategoryTest {
  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testIsSuccessPass() {
    AiTestAutomationPlaywrightCallbackRequest request =
        AiTestAutomationPlaywrightCallbackRequest.builder().status("Pass").build();
    assertThat(request.isSuccess()).isTrue();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testIsSuccessCaseInsensitive() {
    AiTestAutomationPlaywrightCallbackRequest request =
        AiTestAutomationPlaywrightCallbackRequest.builder().status("PASS").build();
    assertThat(request.isSuccess()).isTrue();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testIsSuccessFail() {
    AiTestAutomationPlaywrightCallbackRequest request =
        AiTestAutomationPlaywrightCallbackRequest.builder().status("Fail").build();
    assertThat(request.isSuccess()).isFalse();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testIsSuccessNull() {
    AiTestAutomationPlaywrightCallbackRequest request =
        AiTestAutomationPlaywrightCallbackRequest.builder().status(null).build();
    assertThat(request.isSuccess()).isFalse();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testIsAbortedTrue() {
    AiTestAutomationPlaywrightCallbackRequest request =
        AiTestAutomationPlaywrightCallbackRequest.builder().status("Abort").build();
    assertThat(request.isAborted()).isTrue();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testIsAbortedCaseInsensitive() {
    AiTestAutomationPlaywrightCallbackRequest request =
        AiTestAutomationPlaywrightCallbackRequest.builder().status("ABORT").build();
    assertThat(request.isAborted()).isTrue();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testIsAbortedFalse() {
    AiTestAutomationPlaywrightCallbackRequest request =
        AiTestAutomationPlaywrightCallbackRequest.builder().status("Pass").build();
    assertThat(request.isAborted()).isFalse();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testIsAbortedNull() {
    AiTestAutomationPlaywrightCallbackRequest request =
        AiTestAutomationPlaywrightCallbackRequest.builder().status(null).build();
    assertThat(request.isAborted()).isFalse();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testFullRequestBuilder() {
    AiTestAutomationPlaywrightCallbackRequest request = AiTestAutomationPlaywrightCallbackRequest.builder()
                                                            .status("Pass")
                                                            .buildRunId("run-123")
                                                            .buildName("my-build")
                                                            .buildRunUrl("https://example.com/run")
                                                            .message("5 total, 5 passed, 0 failed")
                                                            .build();

    assertThat(request.getStatus()).isEqualTo("Pass");
    assertThat(request.getBuildRunId()).isEqualTo("run-123");
    assertThat(request.getBuildName()).isEqualTo("my-build");
    assertThat(request.getBuildRunUrl()).isEqualTo("https://example.com/run");
    assertThat(request.getMessage()).isEqualTo("5 total, 5 passed, 0 failed");
  }
}

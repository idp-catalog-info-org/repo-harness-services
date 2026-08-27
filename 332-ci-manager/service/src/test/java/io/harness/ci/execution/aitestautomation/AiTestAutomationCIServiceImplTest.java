/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.aitestautomation;

import static io.harness.rule.OwnerRule.SARTHAK_DALMIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.aitestautomation.models.AiTestAutomationExecutionException;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightParameters;
import io.harness.aitestautomation.models.AiTestRunParameters;
import io.harness.aitestautomation.models.ExecutePlaywrightResponse;
import io.harness.aitestautomation.models.TestSuiteRunResponse;
import io.harness.aitestautomation.service.AiTestAutomationService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.AI)
public class AiTestAutomationCIServiceImplTest extends CategoryTest {
  @Mock private AiTestAutomationService sharedService;

  private AiTestAutomationCIServiceImpl ciService;

  private static final String ACCOUNT_ID = "test-account";
  private static final String AUTH_TOKEN = "test-token";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    ciService = new AiTestAutomationCIServiceImpl();
    injectField("sharedService", sharedService);
  }

  private void injectField(String fieldName, Object value) throws Exception {
    java.lang.reflect.Field field = AiTestAutomationCIServiceImpl.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(ciService, value);
  }

  // ==================== getAuthToken ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testGetAuthTokenDelegatesToSharedService() {
    when(sharedService.getAuthToken(ACCOUNT_ID)).thenReturn(AUTH_TOKEN);

    String result = ciService.getAuthToken(ACCOUNT_ID);

    assertThat(result).isEqualTo(AUTH_TOKEN);
    verify(sharedService).getAuthToken(ACCOUNT_ID);
  }

  // ==================== triggerTestSuiteRun ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerTestSuiteRunInjectsCallbackUrlAndDelegates() {
    injectNgBaseUrl("https://app.harness.io");
    AiTestRunParameters params = AiTestRunParameters.builder().applicationName("app").build();
    TestSuiteRunResponse expected = new TestSuiteRunResponse();
    expected.setTestSuiteRunId("run-123");

    when(sharedService.buildCallbackUrl("https://app.harness.io", "/ci/aiTestAutomationCI/notify"))
        .thenReturn("https://app.harness.io/ci/aiTestAutomationCI/notify");
    when(sharedService.triggerTestSuiteRun(ACCOUNT_ID, AUTH_TOKEN, params)).thenReturn(expected);

    TestSuiteRunResponse result = ciService.triggerTestSuiteRun(ACCOUNT_ID, AUTH_TOKEN, params);

    assertThat(result).isEqualTo(expected);
    assertThat(params.getCallbackUrl()).isEqualTo("https://app.harness.io/ci/aiTestAutomationCI/notify");
    verify(sharedService).triggerTestSuiteRun(ACCOUNT_ID, AUTH_TOKEN, params);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerTestSuiteRunThrowsWhenCallbackUrlBlank() {
    injectNgBaseUrl(null);
    AiTestRunParameters params = AiTestRunParameters.builder().applicationName("app").build();

    when(sharedService.buildCallbackUrl(null, "/ci/aiTestAutomationCI/notify")).thenReturn(null);

    assertThatThrownBy(() -> ciService.triggerTestSuiteRun(ACCOUNT_ID, AUTH_TOKEN, params))
        .isInstanceOf(AiTestAutomationExecutionException.class)
        .hasMessageContaining("ngBaseUrl");

    verify(sharedService, never()).triggerTestSuiteRun(any(), any(), any());
  }

  // ==================== triggerBuildRun ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerBuildRunInjectsCallbackUrlAndDelegates() {
    injectNgBaseUrl("https://app.harness.io");
    AiTestAutomationPlaywrightParameters params = AiTestAutomationPlaywrightParameters.builder().build();
    ExecutePlaywrightResponse expected = new ExecutePlaywrightResponse();
    expected.setBuildRunId("build-run-123");

    when(sharedService.buildCallbackUrl("https://app.harness.io", "/ci/aiTestAutomationCIBuild/notify"))
        .thenReturn("https://app.harness.io/ci/aiTestAutomationCIBuild/notify");
    when(sharedService.triggerBuildRun(ACCOUNT_ID, AUTH_TOKEN, "build-1", "app", params)).thenReturn(expected);

    ExecutePlaywrightResponse result = ciService.triggerBuildRun(ACCOUNT_ID, AUTH_TOKEN, "build-1", "app", params);

    assertThat(result).isEqualTo(expected);
    assertThat(params.getCallbackUrl()).isEqualTo("https://app.harness.io/ci/aiTestAutomationCIBuild/notify");
    verify(sharedService).triggerBuildRun(ACCOUNT_ID, AUTH_TOKEN, "build-1", "app", params);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerBuildRunThrowsWhenCallbackUrlBlank() {
    injectNgBaseUrl(null);
    AiTestAutomationPlaywrightParameters params = AiTestAutomationPlaywrightParameters.builder().build();

    when(sharedService.buildCallbackUrl(null, "/ci/aiTestAutomationCIBuild/notify")).thenReturn(null);

    assertThatThrownBy(() -> ciService.triggerBuildRun(ACCOUNT_ID, AUTH_TOKEN, "build-1", "app", params))
        .isInstanceOf(AiTestAutomationExecutionException.class)
        .hasMessageContaining("ngBaseUrl");

    verify(sharedService, never()).triggerBuildRun(any(), any(), any(), any(), any());
  }

  // ==================== abortBuildRun ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testAbortBuildRunDelegatesToSharedService() {
    ciService.abortBuildRun(ACCOUNT_ID, AUTH_TOKEN, "run-456");

    verify(sharedService).abortBuildRun(ACCOUNT_ID, AUTH_TOKEN, "run-456");
  }

  // ==================== buildCallbackUrl ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testBuildCallbackUrlSimple() {
    injectNgBaseUrl("https://app.harness.io");
    when(sharedService.buildCallbackUrl("https://app.harness.io", "/ci/notify"))
        .thenReturn("https://app.harness.io/ci/notify");

    String result = ciService.buildCallbackUrl("/ci/notify");

    assertThat(result).isEqualTo("https://app.harness.io/ci/notify");
    verify(sharedService).buildCallbackUrl("https://app.harness.io", "/ci/notify");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testBuildCallbackUrlStripsTrailingSlash() {
    injectNgBaseUrl("https://app.harness.io/");
    when(sharedService.buildCallbackUrl("https://app.harness.io", "/ci/notify"))
        .thenReturn("https://app.harness.io/ci/notify");

    String result = ciService.buildCallbackUrl("/ci/notify");

    verify(sharedService).buildCallbackUrl("https://app.harness.io", "/ci/notify");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testBuildCallbackUrlStripsNgSuffix() {
    injectNgBaseUrl("https://app.harness.io/ng");
    when(sharedService.buildCallbackUrl("https://app.harness.io", "/ci/notify"))
        .thenReturn("https://app.harness.io/ci/notify");

    String result = ciService.buildCallbackUrl("/ci/notify");

    verify(sharedService).buildCallbackUrl("https://app.harness.io", "/ci/notify");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testBuildCallbackUrlStripsTrailingSlashAndNgSuffix() {
    injectNgBaseUrl("https://app.harness.io/ng/");
    // After stripping trailing slash: "https://app.harness.io/ng"
    // After stripping /ng: "https://app.harness.io"
    when(sharedService.buildCallbackUrl("https://app.harness.io", "/ci/notify"))
        .thenReturn("https://app.harness.io/ci/notify");

    String result = ciService.buildCallbackUrl("/ci/notify");

    verify(sharedService).buildCallbackUrl("https://app.harness.io", "/ci/notify");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testBuildCallbackUrlNullBaseUrl() {
    injectNgBaseUrl(null);
    when(sharedService.buildCallbackUrl(null, "/ci/notify")).thenReturn(null);

    String result = ciService.buildCallbackUrl("/ci/notify");

    assertThat(result).isNull();
    verify(sharedService).buildCallbackUrl(null, "/ci/notify");
  }

  private void injectNgBaseUrl(String url) {
    try {
      java.lang.reflect.Field field = AiTestAutomationCIServiceImpl.class.getDeclaredField("ngBaseUrl");
      field.setAccessible(true);
      field.set(ciService, url);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

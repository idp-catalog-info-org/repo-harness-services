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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.aitestautomation.models.AiTestAutomationExecutionException;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightExecutionData;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightParameters;
import io.harness.aitestautomation.models.AiTestExecutionData;
import io.harness.aitestautomation.models.AiTestRunParameters;
import io.harness.aitestautomation.models.BuildVariableInput;
import io.harness.aitestautomation.models.BuildVariableValueType;
import io.harness.aitestautomation.models.ExecutePlaywrightResponse;
import io.harness.aitestautomation.models.TestSuiteRunResponse;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.AiTestAutomationCIStepParameters;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogLine;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.AI)
public class AiTestAutomationCIStepTest extends CategoryTest {
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private ILogStreamingStepClient logStreamingStepClient;
  @Mock private AiTestAutomationCIService aiTestAutomationCIService;

  private AiTestAutomationCIStep step;

  private static final String TEST_SUITE_NAME = "test-suite-name";
  private static final String APP_NAME = "app-name";
  private static final String ENV_NAME = "env-name";
  private static final String TEST_SUITE_RUN_ID = "test-run-123";
  private static final String BUILD_ID = "build-123";
  private static final String BUILD_RUN_ID = "build-run-456";
  private static final String ACCOUNT_ID = "test-account-id";
  private static final String AUTH_TOKEN = "test-auth-token";

  @Mock private StepBaseParameters stepParameters;
  @Mock private StepInputPackage inputPackage;
  @Mock private Ambiance ambiance;

  private AiTestAutomationCIStepParameters ciStepParams;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    step = new AiTestAutomationCIStep();
    injectField("aiTestAutomationCIService", aiTestAutomationCIService);
    injectField("logStreamingStepClientFactory", logStreamingStepClientFactory);
    injectField("objectMapper", new ObjectMapper());

    // Default: test suite mode parameters
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .environmentName(ParameterField.createValueField(ENV_NAME))
                       .testSuiteName(ParameterField.createValueField(TEST_SUITE_NAME))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);

    ParameterField<String> timeoutField = mock(ParameterField.class);
    when(timeoutField.getValue()).thenReturn("1d");
    when(stepParameters.getTimeout()).thenReturn(timeoutField);

    // Setup log streaming
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);

    // Setup ambiance
    Map<String, String> absMap = new HashMap<>();
    absMap.put("accountId", ACCOUNT_ID);
    doReturn(absMap).when(ambiance).getSetupAbstractionsMap();
    ExecutionMetadata execMetadata = mock(ExecutionMetadata.class);
    doReturn("test-pipeline").when(execMetadata).getPipelineIdentifier();
    doReturn(execMetadata).when(ambiance).getMetadata();

    // Default service mocks
    doReturn(AUTH_TOKEN).when(aiTestAutomationCIService).getAuthToken(ACCOUNT_ID);
    doReturn(AUTH_TOKEN).when(aiTestAutomationCIService).getCachedOrFreshToken(BUILD_RUN_ID, ACCOUNT_ID);
  }

  private void injectField(String fieldName, Object value) throws Exception {
    java.lang.reflect.Field field = AiTestAutomationCIStep.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(step, value);
  }

  // ==================== getStepParametersClass ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(step.getStepParametersClass()).isEqualTo(StepBaseParameters.class);
  }

  // ==================== isPlaywright ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testIsPlaywrightNullTestType() {
    AiTestAutomationCIStepParameters params = AiTestAutomationCIStepParameters.builder().build();
    assertThat(step.isPlaywright(params)).isFalse();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testIsPlaywrightReturnsTrueForPlaywright() {
    AiTestAutomationCIStepParameters params =
        AiTestAutomationCIStepParameters.builder().testType(ParameterField.createValueField("playwright")).build();
    assertThat(step.isPlaywright(params)).isTrue();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testIsPlaywrightCaseInsensitive() {
    AiTestAutomationCIStepParameters params =
        AiTestAutomationCIStepParameters.builder().testType(ParameterField.createValueField("Playwright")).build();
    assertThat(step.isPlaywright(params)).isTrue();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testIsPlaywrightReturnsFalseForOtherTypes() {
    AiTestAutomationCIStepParameters params = AiTestAutomationCIStepParameters.builder()
                                                  .testType(ParameterField.createValueField("aiTestAutomation"))
                                                  .build();
    assertThat(step.isPlaywright(params)).isFalse();
  }

  // ==================== getStringValue ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testGetStringValueNull() {
    assertThat(step.getStringValue(null)).isNull();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testGetStringValue() {
    ParameterField<String> field = ParameterField.createValueField("hello");
    assertThat(step.getStringValue(field)).isEqualTo("hello");
  }

  // ==================== validateResources ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testValidateResourcesTestSuiteSuccess() {
    step.validateResources(ambiance, stepParameters);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testValidateResourcesMissingApplicationName() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .environmentName(ParameterField.createValueField(ENV_NAME))
                       .testSuiteName(ParameterField.createValueField(TEST_SUITE_NAME))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);

    assertThatThrownBy(() -> step.validateResources(ambiance, stepParameters))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Application name cannot be empty");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testValidateResourcesMissingTestSuiteName() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .environmentName(ParameterField.createValueField(ENV_NAME))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);

    assertThatThrownBy(() -> step.validateResources(ambiance, stepParameters))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Test suite name cannot be empty");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testValidateResourcesMissingEnvironmentName() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testSuiteName(ParameterField.createValueField(TEST_SUITE_NAME))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);

    assertThatThrownBy(() -> step.validateResources(ambiance, stepParameters))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Environment name cannot be empty");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testValidateResourcesPlaywrightSuccess() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);

    step.validateResources(ambiance, stepParameters);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testValidateResourcesPlaywrightMissingBuildId() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);

    assertThatThrownBy(() -> step.validateResources(ambiance, stepParameters))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Build ID cannot be empty");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testValidateResourcesPlaywrightDoesNotRequireTestSuiteOrEnv() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);

    step.validateResources(ambiance, stepParameters);
  }

  // ==================== triggerTest ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerTestSuccess() {
    TestSuiteRunResponse mockResponse = new TestSuiteRunResponse();
    mockResponse.setTestSuiteRunId(TEST_SUITE_RUN_ID);
    mockResponse.setReportUrl("https://example.com/report");

    when(aiTestAutomationCIService.triggerTestSuiteRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), any(AiTestRunParameters.class)))
        .thenReturn(mockResponse);

    NGLogCallback logCallback = mock(NGLogCallback.class);
    String result = step.triggerTest(ambiance, AUTH_TOKEN, ciStepParams, logCallback);

    assertThat(result).isEqualTo(TEST_SUITE_RUN_ID);
    verify(aiTestAutomationCIService)
        .triggerTestSuiteRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), any(AiTestRunParameters.class));
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerTestSetsStepParams() {
    TestSuiteRunResponse mockResponse = new TestSuiteRunResponse();
    mockResponse.setTestSuiteRunId(TEST_SUITE_RUN_ID);

    ArgumentCaptor<AiTestRunParameters> paramsCaptor = ArgumentCaptor.forClass(AiTestRunParameters.class);
    when(aiTestAutomationCIService.triggerTestSuiteRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), any(AiTestRunParameters.class)))
        .thenReturn(mockResponse);

    NGLogCallback logCallback = mock(NGLogCallback.class);
    step.triggerTest(ambiance, AUTH_TOKEN, ciStepParams, logCallback);

    verify(aiTestAutomationCIService).triggerTestSuiteRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().getApplicationName()).isEqualTo(APP_NAME);
    assertThat(paramsCaptor.getValue().getEnvironmentName()).isEqualTo(ENV_NAME);
    assertThat(paramsCaptor.getValue().getTestSuiteName()).isEqualTo(TEST_SUITE_NAME);
    assertThat(paramsCaptor.getValue().isHarnessPipelineStepRequest()).isTrue();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerTestNullAccountIdThrows() {
    Map<String, String> emptyAbsMap = new HashMap<>();
    doReturn(emptyAbsMap).when(ambiance).getSetupAbstractionsMap();

    NGLogCallback logCallback = mock(NGLogCallback.class);
    assertThatThrownBy(() -> step.triggerTest(ambiance, AUTH_TOKEN, ciStepParams, logCallback))
        .isInstanceOf(AiTestAutomationExecutionException.class)
        .hasMessageContaining("Account ID is null or empty");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerTestEmptyRunIdThrows() {
    TestSuiteRunResponse mockResponse = new TestSuiteRunResponse();
    mockResponse.setTestSuiteRunId("");

    when(aiTestAutomationCIService.triggerTestSuiteRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), any(AiTestRunParameters.class)))
        .thenReturn(mockResponse);

    NGLogCallback logCallback = mock(NGLogCallback.class);
    assertThatThrownBy(() -> step.triggerTest(ambiance, AUTH_TOKEN, ciStepParams, logCallback))
        .isInstanceOf(AiTestAutomationExecutionException.class)
        .hasMessageContaining("No test suite run ID returned from API");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerTestWithTunnelName() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .environmentName(ParameterField.createValueField(ENV_NAME))
                       .testSuiteName(ParameterField.createValueField(TEST_SUITE_NAME))
                       .tunnelName(ParameterField.createValueField("my-tunnel"))
                       .build();

    TestSuiteRunResponse mockResponse = new TestSuiteRunResponse();
    mockResponse.setTestSuiteRunId(TEST_SUITE_RUN_ID);

    ArgumentCaptor<AiTestRunParameters> paramsCaptor = ArgumentCaptor.forClass(AiTestRunParameters.class);
    when(aiTestAutomationCIService.triggerTestSuiteRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), any(AiTestRunParameters.class)))
        .thenReturn(mockResponse);

    NGLogCallback logCallback = mock(NGLogCallback.class);
    step.triggerTest(ambiance, AUTH_TOKEN, ciStepParams, logCallback);

    verify(aiTestAutomationCIService).triggerTestSuiteRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), paramsCaptor.capture());
    String paramsJson = paramsCaptor.getValue().getParams();
    assertThat(paramsJson).contains("RELICX_TUNNEL_NAME");
    assertThat(paramsJson).contains("my-tunnel");
    assertThat(paramsJson).contains("RUN_MODE");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerTestWithBlankTunnelNameOmitsTunnelFromParams() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .environmentName(ParameterField.createValueField(ENV_NAME))
                       .testSuiteName(ParameterField.createValueField(TEST_SUITE_NAME))
                       .tunnelName(ParameterField.createValueField("   "))
                       .build();

    TestSuiteRunResponse mockResponse = new TestSuiteRunResponse();
    mockResponse.setTestSuiteRunId(TEST_SUITE_RUN_ID);

    ArgumentCaptor<AiTestRunParameters> paramsCaptor = ArgumentCaptor.forClass(AiTestRunParameters.class);
    when(aiTestAutomationCIService.triggerTestSuiteRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), any(AiTestRunParameters.class)))
        .thenReturn(mockResponse);

    NGLogCallback logCallback = mock(NGLogCallback.class);
    step.triggerTest(ambiance, AUTH_TOKEN, ciStepParams, logCallback);

    verify(aiTestAutomationCIService).triggerTestSuiteRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().getParams()).doesNotContain("RELICX_TUNNEL_NAME");
  }

  // ==================== triggerBuild ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerBuildSuccess() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .build();

    ExecutePlaywrightResponse mockResponse = new ExecutePlaywrightResponse();
    mockResponse.setBuildRunId(BUILD_RUN_ID);
    mockResponse.setHarnessBuildRunUrl("https://app.harness.io/build-run/456");

    when(aiTestAutomationCIService.triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME),
             any(AiTestAutomationPlaywrightParameters.class)))
        .thenReturn(mockResponse);

    NGLogCallback logCallback = mock(NGLogCallback.class);
    String result = step.triggerBuild(ambiance, AUTH_TOKEN, ciStepParams, logCallback);

    assertThat(result).isEqualTo(BUILD_RUN_ID);
    verify(aiTestAutomationCIService)
        .triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME),
            any(AiTestAutomationPlaywrightParameters.class));
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerBuildEmptyRunIdThrows() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .build();

    ExecutePlaywrightResponse mockResponse = new ExecutePlaywrightResponse();
    mockResponse.setBuildRunId("");

    when(aiTestAutomationCIService.triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME),
             any(AiTestAutomationPlaywrightParameters.class)))
        .thenReturn(mockResponse);

    NGLogCallback logCallback = mock(NGLogCallback.class);
    assertThatThrownBy(() -> step.triggerBuild(ambiance, AUTH_TOKEN, ciStepParams, logCallback))
        .isInstanceOf(AiTestAutomationExecutionException.class)
        .hasMessageContaining("No build run ID returned from API");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerBuildPropagatesVariables() {
    BuildVariableInput stringVar = BuildVariableInput.builder()
                                       .id("var-1")
                                       .key("API_BASE_URL")
                                       .value("https://example.test")
                                       .valueType(BuildVariableValueType.STRING)
                                       .build();
    BuildVariableInput secretVar = BuildVariableInput.builder()
                                       .key("AUTH_TOKEN")
                                       .value("account.MY_SECRET")
                                       .valueType(BuildVariableValueType.SECRET)
                                       .build();

    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .variables(ParameterField.createValueField(List.of(stringVar, secretVar)))
                       .build();

    ExecutePlaywrightResponse mockResponse = new ExecutePlaywrightResponse();
    mockResponse.setBuildRunId(BUILD_RUN_ID);

    ArgumentCaptor<AiTestAutomationPlaywrightParameters> paramsCaptor =
        ArgumentCaptor.forClass(AiTestAutomationPlaywrightParameters.class);
    when(aiTestAutomationCIService.triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME),
             any(AiTestAutomationPlaywrightParameters.class)))
        .thenReturn(mockResponse);

    NGLogCallback logCallback = mock(NGLogCallback.class);
    step.triggerBuild(ambiance, AUTH_TOKEN, ciStepParams, logCallback);

    verify(aiTestAutomationCIService)
        .triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME), paramsCaptor.capture());
    AiTestAutomationPlaywrightParameters captured = paramsCaptor.getValue();
    assertThat(captured.getVariables()).hasSize(2);
    assertThat(captured.getVariables().get(0).getKey()).isEqualTo("API_BASE_URL");
    assertThat(captured.getVariables().get(0).getValueType()).isEqualTo(BuildVariableValueType.STRING);
    assertThat(captured.getVariables().get(1).getKey()).isEqualTo("AUTH_TOKEN");
    assertThat(captured.getVariables().get(1).getValueType()).isEqualTo(BuildVariableValueType.SECRET);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerBuildOmitsEmptyVariables() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .variables(ParameterField.createValueField(Collections.emptyList()))
                       .build();

    ExecutePlaywrightResponse mockResponse = new ExecutePlaywrightResponse();
    mockResponse.setBuildRunId(BUILD_RUN_ID);

    ArgumentCaptor<AiTestAutomationPlaywrightParameters> paramsCaptor =
        ArgumentCaptor.forClass(AiTestAutomationPlaywrightParameters.class);
    when(aiTestAutomationCIService.triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME),
             any(AiTestAutomationPlaywrightParameters.class)))
        .thenReturn(mockResponse);

    step.triggerBuild(ambiance, AUTH_TOKEN, ciStepParams, mock(NGLogCallback.class));
    verify(aiTestAutomationCIService)
        .triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME), paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().getVariables()).isNull();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerBuildWithExecutionAliasId() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .executionAliasId(ParameterField.createValueField("alias-123"))
                       .build();

    ExecutePlaywrightResponse mockResponse = new ExecutePlaywrightResponse();
    mockResponse.setBuildRunId(BUILD_RUN_ID);

    ArgumentCaptor<AiTestAutomationPlaywrightParameters> paramsCaptor =
        ArgumentCaptor.forClass(AiTestAutomationPlaywrightParameters.class);
    when(aiTestAutomationCIService.triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME),
             any(AiTestAutomationPlaywrightParameters.class)))
        .thenReturn(mockResponse);

    NGLogCallback logCallback = mock(NGLogCallback.class);
    step.triggerBuild(ambiance, AUTH_TOKEN, ciStepParams, logCallback);

    verify(aiTestAutomationCIService)
        .triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME), paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().getExecutionAliasId()).isEqualTo("alias-123");
    assertThat(paramsCaptor.getValue().isHarnessPipelineStepRequest()).isTrue();
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerBuildWithConfigOverride() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .configOverride(ParameterField.createValueField("{\"key\":\"value\"}"))
                       .build();

    ExecutePlaywrightResponse mockResponse = new ExecutePlaywrightResponse();
    mockResponse.setBuildRunId(BUILD_RUN_ID);

    ArgumentCaptor<AiTestAutomationPlaywrightParameters> paramsCaptor =
        ArgumentCaptor.forClass(AiTestAutomationPlaywrightParameters.class);
    when(aiTestAutomationCIService.triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME),
             any(AiTestAutomationPlaywrightParameters.class)))
        .thenReturn(mockResponse);

    NGLogCallback logCallback = mock(NGLogCallback.class);
    step.triggerBuild(ambiance, AUTH_TOKEN, ciStepParams, logCallback);

    verify(aiTestAutomationCIService)
        .triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME), paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().getConfigOverride()).containsEntry("key", "value");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testTriggerBuildWithTunnelNameInConfigOverride() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .tunnelName(ParameterField.createValueField("my-tunnel"))
                       .build();

    ExecutePlaywrightResponse mockResponse = new ExecutePlaywrightResponse();
    mockResponse.setBuildRunId(BUILD_RUN_ID);

    ArgumentCaptor<AiTestAutomationPlaywrightParameters> paramsCaptor =
        ArgumentCaptor.forClass(AiTestAutomationPlaywrightParameters.class);
    when(aiTestAutomationCIService.triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME),
             any(AiTestAutomationPlaywrightParameters.class)))
        .thenReturn(mockResponse);

    NGLogCallback logCallback = mock(NGLogCallback.class);
    step.triggerBuild(ambiance, AUTH_TOKEN, ciStepParams, logCallback);

    verify(aiTestAutomationCIService)
        .triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME), paramsCaptor.capture());
    assertThat(paramsCaptor.getValue().getConfigOverride()).containsEntry("tunnelName", "my-tunnel");
  }

  // ==================== executeAsyncAfterRbac ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testExecuteAsyncTestSuiteMode() {
    TestSuiteRunResponse mockResponse = new TestSuiteRunResponse();
    mockResponse.setTestSuiteRunId(TEST_SUITE_RUN_ID);

    when(aiTestAutomationCIService.triggerTestSuiteRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), any(AiTestRunParameters.class)))
        .thenReturn(mockResponse);

    AsyncExecutableResponse response = step.executeAsyncAfterRbac(ambiance, stepParameters, inputPackage);

    assertThat(response).isNotNull();
    assertThat(response.getCallbackIdsList()).contains(TEST_SUITE_RUN_ID);
    assertThat(response.getTimeout()).isGreaterThan(0);
    verify(aiTestAutomationCIService).getAuthToken(ACCOUNT_ID);
    verify(aiTestAutomationCIService)
        .triggerTestSuiteRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), any(AiTestRunParameters.class));
    verify(aiTestAutomationCIService, never())
        .triggerBuildRun(any(), any(), any(), any(), any(AiTestAutomationPlaywrightParameters.class));
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testExecuteAsyncPlaywrightMode() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);

    ExecutePlaywrightResponse mockResponse = new ExecutePlaywrightResponse();
    mockResponse.setBuildRunId(BUILD_RUN_ID);

    when(aiTestAutomationCIService.triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME),
             any(AiTestAutomationPlaywrightParameters.class)))
        .thenReturn(mockResponse);

    AsyncExecutableResponse response = step.executeAsyncAfterRbac(ambiance, stepParameters, inputPackage);

    assertThat(response).isNotNull();
    assertThat(response.getCallbackIdsList()).contains(BUILD_RUN_ID);
    verify(aiTestAutomationCIService).getAuthToken(ACCOUNT_ID);
    verify(aiTestAutomationCIService)
        .triggerBuildRun(eq(ACCOUNT_ID), eq(AUTH_TOKEN), eq(BUILD_ID), eq(APP_NAME),
            any(AiTestAutomationPlaywrightParameters.class));
    verify(aiTestAutomationCIService).cacheAuthToken(BUILD_RUN_ID, AUTH_TOKEN);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseEvictsTokenCache() {
    when(stepParameters.getIdentifier()).thenReturn("step-id");

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    AiTestAutomationPlaywrightExecutionData data = AiTestAutomationPlaywrightExecutionData.builder()
                                                       .phase("DONE")
                                                       .executionId(BUILD_RUN_ID)
                                                       .success(true)
                                                       .buildName("my-build")
                                                       .message("5 total, 5 passed, 0 failed")
                                                       .buildRunUrl("https://app.harness.io/build-run/456")
                                                       .build();
    responseDataMap.put(BUILD_RUN_ID, data);

    step.handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);

    verify(aiTestAutomationCIService).evictAuthTokenCache(BUILD_RUN_ID);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testExecuteAsyncWrapsExceptions() {
    doThrow(new RuntimeException("connection refused")).when(aiTestAutomationCIService).getAuthToken(ACCOUNT_ID);

    assertThatThrownBy(() -> step.executeAsyncAfterRbac(ambiance, stepParameters, inputPackage))
        .isInstanceOf(AiTestAutomationExecutionException.class)
        .hasMessageContaining("Failed to execute AI test automation step");

    // log stream must be closed even when executeAsync throws, otherwise the log unit
    // stays open server-side and the UI shows "loading" indefinitely
    verify(logStreamingStepClient).closeStream(eq("test_execution"));
  }

  // ==================== handleAsyncResponseInternal - test suite ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseTestSuiteSuccess() {
    when(stepParameters.getIdentifier()).thenReturn("step-id");

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    AiTestExecutionData data =
        AiTestExecutionData.builder().success(true).executionId(TEST_SUITE_RUN_ID).detailsUrl("https://report").build();
    responseDataMap.put(TEST_SUITE_RUN_ID, data);

    StepResponse response = step.handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(logStreamingStepClient, atLeastOnce()).closeStream(eq("test_execution"));
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseTestSuiteFailure() {
    when(stepParameters.getIdentifier()).thenReturn("step-id");

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    AiTestExecutionData data = AiTestExecutionData.builder().success(false).executionId(TEST_SUITE_RUN_ID).build();
    responseDataMap.put(TEST_SUITE_RUN_ID, data);

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = mockStatic(AmbianceUtils.class)) {
      ambianceUtilsMock.when(() -> AmbianceUtils.getStageIdentifierFromAmbiance(any())).thenReturn("test-stage");

      StepResponse response = step.handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);

      assertThat(response.getStatus()).isEqualTo(Status.FAILED);
      assertThat(response.getFailureInfo().getErrorMessage()).contains("AI Test automation execution failed");
    }
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseTestSuiteWithMetrics() {
    when(stepParameters.getIdentifier()).thenReturn("step-id");

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    AiTestExecutionData data = AiTestExecutionData.builder()
                                   .success(true)
                                   .executionId(TEST_SUITE_RUN_ID)
                                   .totalTests("10")
                                   .passedTests("8")
                                   .failedTests("2")
                                   .build();
    responseDataMap.put(TEST_SUITE_RUN_ID, data);

    StepResponse response = step.handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(logStreamingStepClient, atLeastOnce()).writeLogLine(any(LogLine.class), eq("test_execution"));
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseEmptyResponseMapThrows() {
    Map<String, ResponseData> responseDataMap = new HashMap<>();

    assertThatThrownBy(() -> step.handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap))
        .isInstanceOf(AiTestAutomationExecutionException.class)
        .hasMessageContaining("No response data received");
  }

  // ==================== handleAsyncResponseInternal - playwright ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseBuildSuccess() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);
    when(stepParameters.getIdentifier()).thenReturn("step-id");

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    AiTestAutomationPlaywrightExecutionData data = AiTestAutomationPlaywrightExecutionData.builder()
                                                       .success(true)
                                                       .executionId(BUILD_RUN_ID)
                                                       .buildName("my-build")
                                                       .buildRunUrl("https://app.harness.io/build/456")
                                                       .message("5 total, 5 passed")
                                                       .build();
    responseDataMap.put(BUILD_RUN_ID, data);

    StepResponse response = step.handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseBuildFailed() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);
    when(stepParameters.getIdentifier()).thenReturn("step-id");

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    AiTestAutomationPlaywrightExecutionData data = AiTestAutomationPlaywrightExecutionData.builder()
                                                       .success(false)
                                                       .aborted(false)
                                                       .executionId(BUILD_RUN_ID)
                                                       .buildName("my-build")
                                                       .build();
    responseDataMap.put(BUILD_RUN_ID, data);

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = mockStatic(AmbianceUtils.class)) {
      ambianceUtilsMock.when(() -> AmbianceUtils.getStageIdentifierFromAmbiance(any())).thenReturn("test-stage");

      StepResponse response = step.handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);

      assertThat(response.getStatus()).isEqualTo(Status.FAILED);
      assertThat(response.getFailureInfo().getErrorMessage()).contains("AI build run execution failed");
    }
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseBuildAborted() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);
    when(stepParameters.getIdentifier()).thenReturn("step-id");

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    AiTestAutomationPlaywrightExecutionData data = AiTestAutomationPlaywrightExecutionData.builder()
                                                       .success(false)
                                                       .aborted(true)
                                                       .executionId(BUILD_RUN_ID)
                                                       .buildName("my-build")
                                                       .build();
    responseDataMap.put(BUILD_RUN_ID, data);

    try (MockedStatic<AmbianceUtils> ambianceUtilsMock = mockStatic(AmbianceUtils.class)) {
      ambianceUtilsMock.when(() -> AmbianceUtils.getStageIdentifierFromAmbiance(any())).thenReturn("test-stage");

      StepResponse response = step.handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);

      assertThat(response.getStatus()).isEqualTo(Status.ABORTED);
      assertThat(response.getFailureInfo().getErrorMessage()).contains("aborted");
    }
  }

  // ==================== handleAbort / handleExpire ====================

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleAbortTestSuiteMode() {
    AsyncExecutableResponse executableResponse =
        AsyncExecutableResponse.newBuilder().addCallbackIds(TEST_SUITE_RUN_ID).build();

    step.handleAbort(ambiance, stepParameters, executableResponse, true);

    // Test suite mode doesn't abort via API, just closes logs
    verify(aiTestAutomationCIService, never()).abortBuildRun(any(), any(), any());
    verify(logStreamingStepClient).closeStream(eq("test_execution"));
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleAbortPlaywrightMode() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);

    AsyncExecutableResponse executableResponse =
        AsyncExecutableResponse.newBuilder().addCallbackIds(BUILD_RUN_ID).build();

    step.handleAbort(ambiance, stepParameters, executableResponse, true);

    verify(aiTestAutomationCIService).getCachedOrFreshToken(BUILD_RUN_ID, ACCOUNT_ID);
    verify(aiTestAutomationCIService).abortBuildRun(ACCOUNT_ID, AUTH_TOKEN, BUILD_RUN_ID);
    verify(logStreamingStepClient, atLeastOnce()).closeStream(eq("test_execution"));
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleAbortSwallowsExceptions() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);

    doThrow(new RuntimeException("abort failed")).when(aiTestAutomationCIService).abortBuildRun(any(), any(), any());

    AsyncExecutableResponse executableResponse =
        AsyncExecutableResponse.newBuilder().addCallbackIds(BUILD_RUN_ID).build();

    // Should not throw
    step.handleAbort(ambiance, stepParameters, executableResponse, true);

    verify(logStreamingStepClient, atLeastOnce()).closeStream(eq("test_execution"));
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleExpirePlaywrightMode() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);

    AsyncExecutableResponse executableResponse =
        AsyncExecutableResponse.newBuilder().addCallbackIds(BUILD_RUN_ID).build();

    step.handleExpire(ambiance, stepParameters, executableResponse);

    verify(aiTestAutomationCIService).getCachedOrFreshToken(BUILD_RUN_ID, ACCOUNT_ID);
    verify(aiTestAutomationCIService).abortBuildRun(ACCOUNT_ID, AUTH_TOKEN, BUILD_RUN_ID);
    verify(logStreamingStepClient, atLeastOnce()).closeStream(eq("test_execution"));
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testHandleAbortNullExecutableResponse() {
    ciStepParams = AiTestAutomationCIStepParameters.builder()
                       .applicationName(ParameterField.createValueField(APP_NAME))
                       .testType(ParameterField.createValueField("playwright"))
                       .buildId(ParameterField.createValueField(BUILD_ID))
                       .build();
    when(stepParameters.getSpec()).thenReturn(ciStepParams);

    step.handleAbort(ambiance, stepParameters, null, true);

    verify(aiTestAutomationCIService, never()).abortBuildRun(any(), any(), any());
    verify(logStreamingStepClient).closeStream(eq("test_execution"));
  }
}

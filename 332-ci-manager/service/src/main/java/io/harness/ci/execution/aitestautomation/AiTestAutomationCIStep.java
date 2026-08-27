/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.aitestautomation;

import static io.harness.aitestautomation.constants.AiTestAutomationConstants.DEFAULT_FAST_EXECUTOR_MODE;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.DEFAULT_RUN_MODE;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.ERROR_ACCOUNT_ID_NULL;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.ERROR_TEST_FAILED;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.FAST_EXECUTOR_MODE_FIELD;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.LOG_BUILD_ABORTED;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.LOG_BUILD_FAILED;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.LOG_BUILD_PASSED;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.LOG_BUILD_TIMED_OUT;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.LOG_START_BUILD_RUN;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.LOG_START_TEST;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.LOG_TRIGGERED_BUILD_RUN;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.LOG_TRIGGERED_TEST;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.LOG_TRIGGERED_TEST_REPORT_URL;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.LOG_UNIT_TEST_EXECUTION;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.LOG_VIEW_BUILD_RESULTS;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.RELICX_TUNNEL_NAME_FIELD;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.RUN_MODE_FIELD;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.SECONDS_IN_A_DAY;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.STEP_OUTCOME_NAME;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.TEST_TYPE_PLAYWRIGHT;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.TUNNEL_NAME_FIELD;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.VALIDATION_APP_NAME_EMPTY;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.VALIDATION_BUILD_ID_EMPTY;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.VALIDATION_ENV_NAME_EMPTY;
import static io.harness.aitestautomation.constants.AiTestAutomationConstants.VALIDATION_TEST_SUITE_EMPTY;
import static io.harness.aitestautomation.models.AiTestAutomationPlaywrightParameters.AiTestAutomationPlaywrightParametersBuilder;
import static io.harness.aitestautomation.models.AiTestRunParameters.AiTestRunParametersBuilder;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;

import io.harness.aitestautomation.models.AiTestAutomationExecutionException;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightExecutionData;
import io.harness.aitestautomation.models.AiTestAutomationPlaywrightParameters;
import io.harness.aitestautomation.models.AiTestExecutionData;
import io.harness.aitestautomation.models.AiTestRunParameters;
import io.harness.aitestautomation.models.BuildVariableInput;
import io.harness.aitestautomation.models.ExecutePlaywrightResponse;
import io.harness.aitestautomation.models.TestSuiteRunResponse;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.AiTestAutomationCIStepInfo;
import io.harness.beans.steps.stepinfo.AiTestAutomationCIStepParameters;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.ci.executable.CiAsyncExecutable;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepUtils;
import io.harness.tasks.ResponseData;
import io.harness.utils.TimeoutUtils;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.AI)
@Slf4j
public class AiTestAutomationCIStep extends CiAsyncExecutable {
  public static final StepType STEP_TYPE = AiTestAutomationCIStepInfo.STEP_TYPE;

  @Inject private AiTestAutomationCIService aiTestAutomationCIService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private ObjectMapper objectMapper;
  @Inject private TIServiceUtils tiServiceUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    boolean succeeded = false;
    try {
      AiTestAutomationCIStepParameters params = (AiTestAutomationCIStepParameters) stepParameters.getSpec();
      String applicationName = getStringValue(params.getApplicationName());
      String logUnit = LOG_UNIT_TEST_EXECUTION;
      Long streamTimeout =
          TimeoutUtils.getTimeoutInSecondsFromStringParameterField(stepParameters.getTimeout(), SECONDS_IN_A_DAY);
      NGLogCallback logCallback =
          new NGLogCallback(logStreamingStepClientFactory, ambiance, logUnit, true, streamTimeout);

      String authToken = aiTestAutomationCIService.getAuthToken(AmbianceUtils.getAccountId(ambiance));

      AsyncExecutableResponse response;
      if (isPlaywright(params)) {
        String buildId = getStringValue(params.getBuildId());
        logCallback.saveExecutionLog(String.format(LOG_START_BUILD_RUN, applicationName, buildId), LogLevel.INFO,
            CommandExecutionStatus.RUNNING);
        String callbackId = triggerBuild(ambiance, authToken, params, logCallback);
        aiTestAutomationCIService.cacheAuthToken(callbackId, authToken);
        response = AsyncExecutableResponse.newBuilder()
                       .addCallbackIds(callbackId)
                       .addAllLogKeys(CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(
                           StepUtils.generateLogAbstractions(ambiance), Collections.singletonList(logUnit))))
                       .setTimeout(TimeUnit.SECONDS.toMillis(streamTimeout))
                       .build();
      } else {
        String testSuiteName = getStringValue(params.getTestSuiteName());
        String environmentName = getStringValue(params.getEnvironmentName());
        logCallback.saveExecutionLog(String.format(LOG_START_TEST, applicationName, environmentName, testSuiteName),
            LogLevel.INFO, CommandExecutionStatus.RUNNING);
        String callbackId = triggerTest(ambiance, authToken, params, logCallback);
        response = AsyncExecutableResponse.newBuilder()
                       .addCallbackIds(callbackId)
                       .addAllLogKeys(CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(
                           StepUtils.generateLogAbstractions(ambiance), Collections.singletonList(logUnit))))
                       .setTimeout(TimeUnit.SECONDS.toMillis(streamTimeout))
                       .build();
      }
      succeeded = true;
      return response;
    } catch (Exception e) {
      log.error("Error executing AI test automation CI step", e);
      throw new AiTestAutomationExecutionException("Failed to execute AI test automation step: " + e.getMessage());
    } finally {
      if (!succeeded) {
        closeLogStreams(ambiance);
      }
    }
  }

  @VisibleForTesting
  String getStringValue(ParameterField<String> field) {
    if (field == null) {
      return null;
    }
    return (String) field.fetchFinalValue();
  }

  @VisibleForTesting
  boolean isPlaywright(AiTestAutomationCIStepParameters params) {
    String testType = getStringValue(params.getTestType());
    return TEST_TYPE_PLAYWRIGHT.equalsIgnoreCase(testType);
  }

  private String serializeTunnelParams(String tunnelName) {
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put(RUN_MODE_FIELD, DEFAULT_RUN_MODE);
    paramsMap.put(FAST_EXECUTOR_MODE_FIELD, DEFAULT_FAST_EXECUTOR_MODE);
    paramsMap.put(RELICX_TUNNEL_NAME_FIELD, tunnelName);
    try {
      return objectMapper.writeValueAsString(paramsMap);
    } catch (Exception e) {
      throw new AiTestAutomationExecutionException("Failed to serialize tunnel params: " + e.getMessage());
    }
  }

  @VisibleForTesting
  String triggerTest(
      Ambiance ambiance, String authToken, AiTestAutomationCIStepParameters params, NGLogCallback logCallback) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (StringUtils.isBlank(accountId)) {
      throw new AiTestAutomationExecutionException(ERROR_ACCOUNT_ID_NULL);
    }

    AiTestRunParametersBuilder runParamsBuilder = AiTestRunParameters.builder()
                                                      .applicationName(getStringValue(params.getApplicationName()))
                                                      .environmentName(getStringValue(params.getEnvironmentName()))
                                                      .testSuiteName(getStringValue(params.getTestSuiteName()))
                                                      .isHarnessPipelineStepRequest(true);

    String tunnelName = getStringValue(params.getTunnelName());
    if (StringUtils.isNotBlank(tunnelName)) {
      runParamsBuilder.params(serializeTunnelParams(tunnelName));
    }

    TestSuiteRunResponse response =
        aiTestAutomationCIService.triggerTestSuiteRun(accountId, authToken, runParamsBuilder.build());

    String testSuiteRunId = response.getTestSuiteRunId();
    if (StringUtils.isBlank(testSuiteRunId)) {
      throw new AiTestAutomationExecutionException("No test suite run ID returned from API");
    }

    log.info("Successfully triggered test execution with ID: {}", testSuiteRunId);
    logCallback.saveExecutionLog(
        String.format(LOG_TRIGGERED_TEST, testSuiteRunId), LogLevel.INFO, CommandExecutionStatus.RUNNING);
    logReportUrl(logCallback, response.getReportUrl());
    return testSuiteRunId;
  }

  @VisibleForTesting
  String triggerBuild(
      Ambiance ambiance, String authToken, AiTestAutomationCIStepParameters params, NGLogCallback logCallback) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (StringUtils.isBlank(accountId)) {
      throw new AiTestAutomationExecutionException(ERROR_ACCOUNT_ID_NULL);
    }

    Map<String, Object> configOverrideMap = buildConfigOverrideMap(params);

    AiTestAutomationPlaywrightParametersBuilder runParamsBuilder =
        AiTestAutomationPlaywrightParameters.builder().isHarnessPipelineStepRequest(true);

    String executionAliasId = getStringValue(params.getExecutionAliasId());
    if (StringUtils.isNotBlank(executionAliasId)) {
      runParamsBuilder.executionAliasId(executionAliasId);
    }
    if (!configOverrideMap.isEmpty()) {
      runParamsBuilder.configOverride(configOverrideMap);
    }

    List<BuildVariableInput> variables = ParameterFieldHelper.getParameterFieldValue(params.getVariables());
    if (variables != null && !variables.isEmpty()) {
      runParamsBuilder.variables(variables);
    }

    Map<String, String> tiConfig = buildTiConfig(ambiance, accountId);
    if (tiConfig != null) {
      runParamsBuilder.tiConfig(tiConfig);
    }

    String buildId = getStringValue(params.getBuildId());
    String applicationName = getStringValue(params.getApplicationName());

    ExecutePlaywrightResponse response = aiTestAutomationCIService.triggerBuildRun(
        accountId, authToken, buildId, applicationName, runParamsBuilder.build());

    String buildRunId = response.getBuildRunId();
    if (StringUtils.isBlank(buildRunId)) {
      throw new AiTestAutomationExecutionException("No build run ID returned from API");
    }

    log.info("Successfully triggered build execution with ID: {}", buildRunId);
    logCallback.saveExecutionLog(
        String.format(LOG_TRIGGERED_BUILD_RUN, buildRunId), LogLevel.INFO, CommandExecutionStatus.RUNNING);
    logReportUrl(logCallback, response.getHarnessBuildRunUrl());
    return buildRunId;
  }

  private void logReportUrl(NGLogCallback logCallback, String reportUrl) {
    if (StringUtils.isNotBlank(reportUrl)) {
      logCallback.saveExecutionLog(
          LogHelper.color(String.format(LOG_TRIGGERED_TEST_REPORT_URL, reportUrl), LogColor.Cyan), LogLevel.INFO,
          CommandExecutionStatus.RUNNING);
    }
  }

  private Map<String, Object> buildConfigOverrideMap(AiTestAutomationCIStepParameters params) {
    Map<String, Object> configOverrideMap = new HashMap<>();
    String configOverride = getStringValue(params.getConfigOverride());
    if (StringUtils.isNotBlank(configOverride)) {
      try {
        Map<String, Object> parsed =
            objectMapper.readValue(configOverride, new TypeReference<Map<String, Object>>() {});
        configOverrideMap.putAll(parsed);
      } catch (Exception e) {
        log.warn("Failed to parse configOverride JSON, ignoring: {}", e.getMessage());
      }
    }
    String tunnelName = getStringValue(params.getTunnelName());
    if (StringUtils.isNotBlank(tunnelName)) {
      configOverrideMap.put(TUNNEL_NAME_FIELD, tunnelName);
    }
    return configOverrideMap;
  }

  @VisibleForTesting
  Map<String, String> buildTiConfig(Ambiance ambiance, String accountId) {
    try {
      Map<String, String> tiConfig = new HashMap<>();
      tiConfig.put("tiServiceEndpoint", tiServiceUtils.getTiServiceConfig().getBaseUrl());
      tiConfig.put("accountId", accountId);
      tiConfig.put("orgId", AmbianceUtils.getOrgIdentifier(ambiance));
      tiConfig.put("projectId", AmbianceUtils.getProjectIdentifier(ambiance));
      tiConfig.put("pipelineId", ambiance.getMetadata().getPipelineIdentifier());
      tiConfig.put("buildId", String.valueOf(ambiance.getMetadata().getRunSequence()));
      tiConfig.put("stageId", AmbianceUtils.getStageIdentifierFromAmbiance(ambiance));
      tiConfig.put("stepId", AmbianceUtils.obtainStepIdentifier(ambiance));

      // parentUniqueId is required by TI service for scoped queries (Tests tab)
      String parentUniqueId = AmbianceUtils.getParentUniqueIdentifier(ambiance);
      if (StringUtils.isNotBlank(parentUniqueId)) {
        tiConfig.put("parentUniqueId", parentUniqueId);
      }

      // Resolve repo URL and commit SHA from codebase sweeping output
      try {
        OptionalSweepingOutput codebaseOutput =
            executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
        if (codebaseOutput.isFound()) {
          CodebaseSweepingOutput codebase = (CodebaseSweepingOutput) codebaseOutput.getOutput();
          if (StringUtils.isNotBlank(codebase.getRepoUrl())) {
            tiConfig.put("repoUrl", codebase.getRepoUrl());
          }
          if (StringUtils.isNotBlank(codebase.getCommitSha())) {
            tiConfig.put("commitSha", codebase.getCommitSha());
          }
        }
      } catch (Exception e) {
        log.debug("Could not resolve codebase sweeping output for TI config: {}", e.getMessage());
      }

      return tiConfig;
    } catch (Exception e) {
      log.warn("Failed to generate TI config, TI upload will be skipped: {}", e.getMessage());
      return null;
    }
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    try {
      if (EmptyPredicate.isEmpty(responseDataMap)) {
        throw new AiTestAutomationExecutionException("No response data received from callback");
      }
      ResponseData responseData = responseDataMap.values().iterator().next();
      if (responseData instanceof AiTestAutomationPlaywrightExecutionData) {
        return handleBuildAsyncResponse(
            ambiance, stepParameters, (AiTestAutomationPlaywrightExecutionData) responseData);
      }
      if (responseData instanceof AiTestExecutionData) {
        return handleTestSuiteAsyncResponse(ambiance, stepParameters, (AiTestExecutionData) responseData);
      }
      throw new AiTestAutomationExecutionException(
          "Unexpected response data type: " + responseData.getClass().getSimpleName());
    } finally {
      responseDataMap.keySet().forEach(aiTestAutomationCIService::evictAuthTokenCache);
      closeLogStreams(ambiance);
    }
  }

  private StepResponse handleTestSuiteAsyncResponse(
      Ambiance ambiance, StepBaseParameters stepParameters, AiTestExecutionData data) {
    AiTestAutomationCIStepParameters params = (AiTestAutomationCIStepParameters) stepParameters.getSpec();
    String logUnit = LOG_UNIT_TEST_EXECUTION;
    Long streamTimeout =
        TimeoutUtils.getTimeoutInSecondsFromStringParameterField(stepParameters.getTimeout(), SECONDS_IN_A_DAY);
    NGLogCallback logCallback =
        new NGLogCallback(logStreamingStepClientFactory, ambiance, logUnit, false, streamTimeout);

    String testSuiteName = getStringValue(params.getTestSuiteName());
    logTestResultMetrics(logCallback, data);

    StepResponseBuilder responseBuilder =
        StepResponse.builder().stepOutcome(StepOutcome.builder().outcome(data).name(STEP_OUTCOME_NAME).build());

    if (data.isSuccess()) {
      logCallback.saveExecutionLog(
          LogHelper.color(String.format("Test Suite '%s' PASSED", testSuiteName), LogColor.Green), LogLevel.INFO,
          CommandExecutionStatus.SUCCESS);
      if (StringUtils.isNotBlank(data.getDetailsUrl())) {
        logCallback.saveExecutionLog(LogHelper.color("You can download the results from here:", LogColor.Cyan),
            LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        logCallback.saveExecutionLog(
            LogHelper.color(data.getDetailsUrl(), LogColor.Cyan), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      }
      return responseBuilder.status(Status.SUCCEEDED).build();
    }

    logCallback.saveExecutionLog(LogHelper.color(String.format("Test Suite '%s' FAILED", testSuiteName), LogColor.Red),
        LogLevel.ERROR, CommandExecutionStatus.FAILURE);

    String errorMsg = String.format(ERROR_TEST_FAILED, testSuiteName);
    FailureData.Builder failureData = FailureData.newBuilder()
                                          .setMessage(errorMsg)
                                          .setStepIdentifier(stepParameters.getIdentifier())
                                          .setStageIdentifier(AmbianceUtils.getStageIdentifierFromAmbiance(ambiance));
    FailureInfo failureInfo = FailureInfo.newBuilder().setErrorMessage(errorMsg).addFailureData(failureData).build();
    return responseBuilder.status(Status.FAILED).failureInfo(failureInfo).build();
  }

  private void logTestResultMetrics(NGLogCallback logCallback, AiTestExecutionData data) {
    if (data.getTotalTests() == null) {
      return;
    }
    LogColor metricsColor = LogColor.White;
    if (data.getPassedTests() != null && data.getFailedTests() != null) {
      try {
        int failedTests = Integer.parseInt(data.getFailedTests());
        int passedTests = Integer.parseInt(data.getPassedTests());
        if (failedTests > 0) {
          metricsColor = LogColor.Red;
        } else if (passedTests > 0) {
          metricsColor = LogColor.Green;
        }
      } catch (NumberFormatException e) {
        log.debug("Could not parse test count values as integers", e);
      }
    }
    logCallback.saveExecutionLog(
        LogHelper.color(String.format("Test Results: %s total, %s passed, %s failed", data.getTotalTests(),
                            data.getPassedTests() != null ? data.getPassedTests() : "0",
                            data.getFailedTests() != null ? data.getFailedTests() : "0"),
            metricsColor),
        LogLevel.INFO, CommandExecutionStatus.RUNNING);
  }

  private StepResponse handleBuildAsyncResponse(
      Ambiance ambiance, StepBaseParameters stepParameters, AiTestAutomationPlaywrightExecutionData data) {
    AiTestAutomationCIStepParameters params = (AiTestAutomationCIStepParameters) stepParameters.getSpec();
    String logUnit = LOG_UNIT_TEST_EXECUTION;
    Long streamTimeout =
        TimeoutUtils.getTimeoutInSecondsFromStringParameterField(stepParameters.getTimeout(), SECONDS_IN_A_DAY);
    NGLogCallback logCallback =
        new NGLogCallback(logStreamingStepClientFactory, ambiance, logUnit, false, streamTimeout);

    String buildId = getStringValue(params.getBuildId());
    if (StringUtils.isNotBlank(data.getMessage())) {
      logCallback.saveExecutionLog(data.getMessage(), LogLevel.INFO, CommandExecutionStatus.RUNNING);
    }

    String displayName = data.getBuildName() != null ? data.getBuildName() : buildId;
    StepResponseBuilder responseBuilder =
        StepResponse.builder().stepOutcome(StepOutcome.builder().outcome(data).name(STEP_OUTCOME_NAME).build());

    if (data.isSuccess()) {
      logCallback.saveExecutionLog(LogHelper.color(String.format(LOG_BUILD_PASSED, displayName), LogColor.Green),
          LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      if (StringUtils.isNotBlank(data.getBuildRunUrl())) {
        logCallback.saveExecutionLog(
            LogHelper.color(LOG_VIEW_BUILD_RESULTS, LogColor.Cyan), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        logCallback.saveExecutionLog(
            LogHelper.color(data.getBuildRunUrl(), LogColor.Cyan), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      }
      return responseBuilder.status(Status.SUCCEEDED).build();
    }

    if (data.isAborted()) {
      logCallback.saveExecutionLog(LogHelper.color(String.format(LOG_BUILD_ABORTED, displayName), LogColor.Yellow),
          LogLevel.WARN, CommandExecutionStatus.FAILURE);
      String errorMsg = String.format("AI build run was aborted for build: %s", displayName);
      FailureData.Builder failureData = FailureData.newBuilder()
                                            .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                            .setMessage(errorMsg)
                                            .setStepIdentifier(stepParameters.getIdentifier())
                                            .setStageIdentifier(AmbianceUtils.getStageIdentifierFromAmbiance(ambiance));
      FailureInfo failureInfo = FailureInfo.newBuilder().setErrorMessage(errorMsg).addFailureData(failureData).build();
      return responseBuilder.status(Status.ABORTED).failureInfo(failureInfo).build();
    }

    logCallback.saveExecutionLog(LogHelper.color(String.format(LOG_BUILD_FAILED, displayName), LogColor.Red),
        LogLevel.ERROR, CommandExecutionStatus.FAILURE);
    String errorMsg = String.format("AI build run execution failed for build: %s", displayName);
    FailureData.Builder failureData = FailureData.newBuilder()
                                          .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                          .setMessage(errorMsg)
                                          .setStepIdentifier(stepParameters.getIdentifier())
                                          .setStageIdentifier(AmbianceUtils.getStageIdentifierFromAmbiance(ambiance));
    FailureInfo failureInfo = FailureInfo.newBuilder().setErrorMessage(errorMsg).addFailureData(failureData).build();
    return responseBuilder.status(Status.FAILED).failureInfo(failureInfo).build();
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    NGLogCallback logCallback = null;
    try {
      logCallback = createLogCallback(ambiance, stepParameters);
      AiTestAutomationCIStepParameters params = (AiTestAutomationCIStepParameters) stepParameters.getSpec();
      if (isPlaywright(params) && executableResponse != null && !executableResponse.getCallbackIdsList().isEmpty()) {
        String buildRunId = executableResponse.getCallbackIds(0);
        String accountId = AmbianceUtils.getAccountId(ambiance);
        String authToken = aiTestAutomationCIService.getCachedOrFreshToken(buildRunId, accountId);
        aiTestAutomationCIService.abortBuildRun(accountId, authToken, buildRunId);
        logCallback.saveExecutionLog(LogHelper.color(String.format(LOG_BUILD_ABORTED, buildRunId), LogColor.Yellow),
            LogLevel.WARN, CommandExecutionStatus.FAILURE);
        log.info("Aborted build run {} on pipeline abort", buildRunId);
      }
    } catch (Exception e) {
      if (logCallback != null) {
        logCallback.saveExecutionLog(LogHelper.color("Failed to abort build run: " + e.getMessage(), LogColor.Red),
            LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      }
      log.error("Failed to abort build run on pipeline abort", e);
    } finally {
      evictTokenCache(executableResponse);
      closeLogStreams(ambiance);
    }
  }

  @Override
  public void handleExpire(
      Ambiance ambiance, StepBaseParameters stepParameters, AsyncExecutableResponse executableResponse) {
    NGLogCallback logCallback = null;
    try {
      logCallback = createLogCallback(ambiance, stepParameters);
      AiTestAutomationCIStepParameters params = (AiTestAutomationCIStepParameters) stepParameters.getSpec();
      if (isPlaywright(params) && executableResponse != null && !executableResponse.getCallbackIdsList().isEmpty()) {
        String buildRunId = executableResponse.getCallbackIds(0);
        String accountId = AmbianceUtils.getAccountId(ambiance);
        String authToken = aiTestAutomationCIService.getCachedOrFreshToken(buildRunId, accountId);
        aiTestAutomationCIService.abortBuildRun(accountId, authToken, buildRunId);
        logCallback.saveExecutionLog(LogHelper.color(String.format(LOG_BUILD_TIMED_OUT, buildRunId), LogColor.Yellow),
            LogLevel.WARN, CommandExecutionStatus.FAILURE);
        log.info("Aborted build run {} on step timeout/expire", buildRunId);
      }
    } catch (Exception e) {
      if (logCallback != null) {
        logCallback.saveExecutionLog(
            LogHelper.color("Failed to abort build run on timeout: " + e.getMessage(), LogColor.Red), LogLevel.ERROR,
            CommandExecutionStatus.FAILURE);
      }
      log.error("Failed to abort build run on step expire", e);
    } finally {
      evictTokenCache(executableResponse);
      closeLogStreams(ambiance);
    }
  }

  private NGLogCallback createLogCallback(Ambiance ambiance, StepBaseParameters stepParameters) {
    Long streamTimeout =
        TimeoutUtils.getTimeoutInSecondsFromStringParameterField(stepParameters.getTimeout(), SECONDS_IN_A_DAY);
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, LOG_UNIT_TEST_EXECUTION, false, streamTimeout);
  }

  private void evictTokenCache(AsyncExecutableResponse executableResponse) {
    if (executableResponse != null && !executableResponse.getCallbackIdsList().isEmpty()) {
      aiTestAutomationCIService.evictAuthTokenCache(executableResponse.getCallbackIds(0));
    }
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    AiTestAutomationCIStepParameters params = (AiTestAutomationCIStepParameters) stepParameters.getSpec();
    if (EmptyPredicate.isEmpty(getStringValue(params.getApplicationName()))) {
      throw new InvalidRequestException(VALIDATION_APP_NAME_EMPTY);
    }
    if (isPlaywright(params)) {
      if (EmptyPredicate.isEmpty(getStringValue(params.getBuildId()))) {
        throw new InvalidRequestException(VALIDATION_BUILD_ID_EMPTY);
      }
    } else {
      if (EmptyPredicate.isEmpty(getStringValue(params.getTestSuiteName()))) {
        throw new InvalidRequestException(VALIDATION_TEST_SUITE_EMPTY);
      }
      if (EmptyPredicate.isEmpty(getStringValue(params.getEnvironmentName()))) {
        throw new InvalidRequestException(VALIDATION_ENV_NAME_EMPTY);
      }
    }
  }

  private void closeLogStreams(Ambiance ambiance) {
    try {
      ILogStreamingStepClient logStreamingStepClient =
          logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
      logStreamingStepClient.closeStream(LOG_UNIT_TEST_EXECUTION);
    } catch (Exception e) {
      log.warn("Failed to close log streams: {}", e.getMessage());
    }
  }
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.pipeline.steps;

import static io.harness.delegate.task.http.HttpTaskParametersNg.HttpTaskParametersNgBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.common.NGTimeConversionHelper;
import io.harness.delegate.beans.SerializedResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.delegate.task.http.HttpTaskParametersNg;
import io.harness.encryption.Scope;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.idp.steps.beans.outcome.ActionOutcome;
import io.harness.idp.steps.beans.stepparameters.ActionStepParameters;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.executables.PipelineTaskExecutable;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.TaskType;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ActionStep extends PipelineTaskExecutable<ResponseData> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.IDP_ACTION_STEP_TYPE;
  public static final String COMMAND_UNIT = "Execute";

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private ActionStepHelper helper;
  @Inject private StepHelper stepHelper;
  @Inject private NGFeatureFlagHelperService featureFlagService;

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.openStream(COMMAND_UNIT);

    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

    if (!featureFlagService.isEnabled(accountId, FeatureName.IDP_ENABLE_ACTION_STEP)) {
      String msg = "IdpAction step is disabled for this account. "
          + "Enable the IDP_ENABLE_ACTION_STEP feature flag to use it.";
      logCallback.saveExecutionLog(msg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new AccessDeniedException(msg, WingsException.USER);
    }

    ActionStepParameters params = (ActionStepParameters) stepParameters.getSpec();
    String actionRef = params.getActionRef() != null ? params.getActionRef().getValue() : null;
    String actionVersion = params.getActionVersion() != null ? params.getActionVersion().getValue() : null;
    Map<String, String> inputs = params.getInputs() != null && params.getInputs().getValue() != null
        ? params.getInputs().getValue()
        : Collections.emptyMap();

    if (actionRef == null || actionRef.isEmpty()) {
      throw new InvalidRequestException("actionRef is required on the IdpAction step");
    }

    try {
      JsonNode actionDef =
          helper.fetchActionDefinition(accountId, orgId, projectId, actionRef, actionVersion, logCallback);

      ActionRequestPlan plan = helper.buildRequestPlan(actionDef, inputs, accountId, orgId, projectId, logCallback);

      HttpTaskParametersNgBuilder taskParamsBuilder = HttpTaskParametersNg.builder()
                                                          .url(plan.getUrl())
                                                          .method(plan.getMethod())
                                                          .requestHeader(plan.getHeaders())
                                                          .socketTimeoutMillis(plan.getTimeoutMs())
                                                          .isCertValidationRequired(false)
                                                          .isIgnoreResponseCode(true);
      if (plan.getBody() != null) {
        taskParamsBuilder.body(plan.getBody());
      }

      long stepTimeout = stepParameters.getTimeout() != null && stepParameters.getTimeout().getValue() != null
          ? NGTimeConversionHelper.convertTimeStringToMilliseconds(stepParameters.getTimeout().getValue())
          : 180_000L;

      TaskData taskData = TaskData.builder()
                              .async(true)
                              .timeout(stepTimeout)
                              .taskType(TaskType.HTTP_TASK_NG.name())
                              .parameters(new Object[] {taskParamsBuilder.build()})
                              .build();

      List<String> commandUnits = Collections.singletonList(COMMAND_UNIT);

      return TaskRequestsUtils.prepareTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
          TaskCategory.DELEGATE_TASK_V2, commandUnits, true, null,
          TaskSelectorYaml.toTaskSelector(plan.getDelegateSelectors()), Scope.PROJECT,
          stepHelper.getEnvironmentType(ambiance), false, Collections.emptyList(), false, null);
    } catch (Exception e) {
      log.error("IdpAction step failed during server-side resolution for actionRef [{}]", actionRef, e);
      logCallback.saveExecutionLog(
          String.format("Resolution failed: %s", e.getMessage()), LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      closeLogStream(ambiance);
      throw new InvalidRequestException(
          String.format("Failed to resolve IdpAction for actionRef [%s]: %s", actionRef, e.getMessage()), e);
    }
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
    try {
      ResponseData rawResponse = responseSupplier.get();
      HttpStepResponse httpStepResponse = unwrap(rawResponse);
      if (httpStepResponse == null) {
        logCallback.saveExecutionLog(
            "Did not receive a usable HTTP response from delegate", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
        return StepResponse.builder().status(io.harness.pms.contracts.execution.Status.FAILED).build();
      }

      ActionStepParameters params = (ActionStepParameters) stepParameters.getSpec();
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      String actionRef = params.getActionRef() != null ? params.getActionRef().getValue() : null;
      String actionVersion = params.getActionVersion() != null ? params.getActionVersion().getValue() : null;

      JsonNode actionDef = null;
      if (actionRef != null && !actionRef.isEmpty()) {
        try {
          actionDef = helper.fetchActionDefinition(accountId, orgId, projectId, actionRef, actionVersion, logCallback);
        } catch (Exception ex) {
          logCallback.saveExecutionLog(
              String.format("Could not re-resolve Action [%s] for response handling: %s", actionRef, ex.getMessage()),
              LogLevel.WARN);
        }
      }

      Map<String, Object> outputs;
      try {
        outputs = helper.extractOutputs(actionDef, httpStepResponse.getHttpResponseBody(), logCallback);
      } catch (Exception ex) {
        logCallback.saveExecutionLog(String.format("Output extraction failed: %s", ex.getMessage()), LogLevel.WARN);
        outputs = Collections.emptyMap();
      }

      int httpCode = httpStepResponse.getHttpResponseCode();
      Set<Integer> expected = helper.expectedStatusCodes(actionDef);
      boolean accepted = helper.isStatusCodeAccepted(httpCode, expected);

      boolean suppressBody = helper.shouldSuppressResponseBody(actionDef);
      ActionOutcome outcome = ActionOutcome.builder()
                                  .httpResponseCode(httpCode)
                                  .httpResponseBody(suppressBody ? null : httpStepResponse.getHttpResponseBody())
                                  .outputVariables(outputs)
                                  .build();

      if (!accepted) {
        String msg = expected.isEmpty()
            ? String.format("IdpAction failed: HTTP %d is not a 2xx response", httpCode)
            : String.format("IdpAction failed: HTTP %d is not in expectedStatusCodes %s", httpCode, expected);
        logCallback.saveExecutionLog(msg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
        return StepResponse.builder()
            .status(io.harness.pms.contracts.execution.Status.FAILED)
            .failureInfo(
                io.harness.pms.contracts.execution.failure.FailureInfo.newBuilder().setErrorMessage(msg).build())
            .stepOutcome(StepOutcome.builder().name(YAMLFieldNameConstants.OUTPUT).outcome(outcome).build())
            .build();
      }

      logCallback.saveExecutionLog(
          String.format("IdpAction completed with HTTP %d", httpCode), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

      return StepResponse.builder()
          .status(io.harness.pms.contracts.execution.Status.SUCCEEDED)
          .stepOutcome(StepOutcome.builder().name(YAMLFieldNameConstants.OUTPUT).outcome(outcome).build())
          .build();
    } finally {
      closeLogStream(ambiance);
    }
  }

  private HttpStepResponse unwrap(ResponseData rawResponse) {
    if (rawResponse instanceof HttpStepResponse) {
      return (HttpStepResponse) rawResponse;
    }
    if (rawResponse instanceof SerializedResponseData) {
      Object deserialised =
          referenceFalseKryoSerializer.asInflatedObject(((SerializedResponseData) rawResponse).getData());
      if (deserialised instanceof HttpStepResponse) {
        return (HttpStepResponse) deserialised;
      }
    }
    log.warn("IdpAction step received unexpected response type: {}",
        rawResponse == null ? "null" : rawResponse.getClass().getName());
    return null;
  }

  private void closeLogStream(Ambiance ambiance) {
    try {
      ILogStreamingStepClient client = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
      client.closeStream(COMMAND_UNIT);
    } catch (Exception e) {
      log.warn("Failed to close IdpAction log stream", e);
    }
  }
}

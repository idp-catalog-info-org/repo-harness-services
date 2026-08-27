/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.eventlistener;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.spec.server.ng.v1.model.GitXWebhookEventResponse;
import io.harness.spec.server.ng.v1.model.GitXWebhookResponse;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.eventlistener.beans.EventListenerStepResponseData;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance;
import io.harness.steps.eventlistener.evaluation.EventListenerStepInstanceExpressionEvaluator;
import io.harness.steps.executables.PipelineAsyncExecutable;
import io.harness.tasks.ResponseData;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;
import io.harness.utils.AmbianceScopeResolutionHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.webhook.utils.GitxWebhookUtils;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})

@OwnedBy(CDC)
@Slf4j
public class EventlistenerStep extends PipelineAsyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.EVENT_LISTENER_STEP_TYPE;

  @Inject private EventListenerStepInstanceService eventListenerStepInstanceService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private GitxWebhookUtils gitxWebhookUtils;
  @Inject private AmbianceScopeResolutionHelper scopeResolutionHelper;

  public static final String COMMAND_UNIT = "Execute";
  private static final Long SECONDS_IN_A_DAY = Duration.ofDays(1).get(ChronoUnit.SECONDS);

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(ambiance);
    EventListenerStepInstance eventListenerStepInstance =
        EventListenerStepInstance.fromStepParameters(ambiance, stepParameters, scopeInfo);
    IdentifierRef webhookIdentityRef =
        IdentifierRefHelper.getIdentifierRef(eventListenerStepInstance.getWebhookIdentifier(), scopeInfo);
    GitXWebhookResponse gitXWebhookResponseDTO =
        gitxWebhookUtils.getWebhook(webhookIdentityRef.getAccountIdentifier(), webhookIdentityRef.getOrgIdentifier(),
            webhookIdentityRef.getProjectIdentifier(), webhookIdentityRef.getIdentifier());
    if (gitXWebhookResponseDTO == null) {
      throw new InvalidRequestException(
          "Webhook not found with identifier: " + eventListenerStepInstance.getWebhookIdentifier());
    }
    openLogStream(ambiance, eventListenerStepInstance);
    eventListenerStepInstance = eventListenerStepInstanceService.save(eventListenerStepInstance);
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
    logCallback.saveExecutionLog("Event Listener Step started. Listening for webhook events...");
    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(eventListenerStepInstance.getId())
        .addAllLogKeys(CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(
            StepUtils.generateLogAbstractions(ambiance), Collections.singletonList(COMMAND_UNIT))))
        .build();
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    try {
      EventListenerStepResponseData eventListenerStepResponseData =
          (EventListenerStepResponseData) responseDataMap.values().iterator().next();
      EventListenerStepInstance instance =
          eventListenerStepInstanceService.get(eventListenerStepResponseData.getInstanceId());
      ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(ambiance);
      IdentifierRef webhookIdentityRef =
          IdentifierRefHelper.getIdentifierRef(instance.getWebhookIdentifier(), scopeInfo);
      List<GitXWebhookEventResponse> gitXWebhookEventResponse =
          gitxWebhookUtils.getWebhookEvents(webhookIdentityRef.getAccountIdentifier(),
              webhookIdentityRef.getOrgIdentifier(), webhookIdentityRef.getProjectIdentifier(),
              webhookIdentityRef.getIdentifier(), eventListenerStepResponseData.getEventCorrelationId());
      Map<String, String> ouptputVariablesMap = new HashMap<>();
      if (isEmpty(gitXWebhookEventResponse)) {
        log.error("Skipping processing of Output Variables as no webhook event found");
      } else {
        ouptputVariablesMap = processOutputVariables(instance.getOutputVariables(),
            gitXWebhookEventResponse.get(0).getPayload(), eventListenerStepResponseData.getHeadersConfigs());
      }
      EventListenerStepOutcome outcome = EventListenerStepOutcome.builder()
                                             .eventCorrelationId(eventListenerStepResponseData.getEventCorrelationId())
                                             .webhookIdentifier(instance.getWebhookIdentifier())
                                             .outputVariables(ouptputVariablesMap)
                                             .build();
      return StepResponse.builder()
          .status(instance.getStatus().toFinalExecutionStatus())
          .failureInfo(instance.getFailureInfo())
          .stepOutcome(
              StepResponse.StepOutcome.builder().name(OutputExpressionConstants.OUTPUT).outcome(outcome).build())
          .build();
    } finally {
      closeLogStream(ambiance);
    }
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    eventListenerStepInstanceService.abortByNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    closeLogStream(ambiance);
  }

  @Override
  public void handleExpire(
      Ambiance ambiance, StepBaseParameters stepParameters, AsyncExecutableResponse executableResponse) {
    eventListenerStepInstanceService.expireByNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
    logCallback.saveExecutionLog(LogHelper.color("EventListenerStepInstance instance has expired", LogColor.Red),
        LogLevel.ERROR, CommandExecutionStatus.FAILURE);
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public StepExecutionTelemetryEventDTO getStepExecutionTelemetryEventDTO(
      Ambiance ambiance, StepBaseParameters stepParameters) {
    return StepExecutionTelemetryEventDTO.builder().stepType(STEP_TYPE.getType()).build();
  }

  private void openLogStream(Ambiance ambiance, EventListenerStepInstance eventListenerStepInstance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    Long streamTimeout = getTimeoutInSeconds(eventListenerStepInstance);
    List<String> units = Collections.singletonList(COMMAND_UNIT);

    for (String unit : units) {
      logStreamingStepClient.openStream(unit, streamTimeout);
    }
  }

  private long getTimeoutInSeconds(@NotNull EventListenerStepInstance eventListenerStepInstance) {
    try {
      if (isNull(eventListenerStepInstance) || eventListenerStepInstance.getDeadline() < System.currentTimeMillis()) {
        log.warn("Valid deadline missing in EventListener Step Instance instance, defaulting to 1d");
        return SECONDS_IN_A_DAY;
      }

      return Duration.ofMillis(eventListenerStepInstance.getDeadline() - System.currentTimeMillis())
          .get(ChronoUnit.SECONDS);
    } catch (Exception ex) {
      log.error("Exception occurred while calculating getTimeoutInSeconds, defaulting to 1d", ex);
      return SECONDS_IN_A_DAY;
    }
  }

  private void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeStream(COMMAND_UNIT);
  }

  private Map<String, String> processOutputVariables(
      Map<String, Object> outputVariables, String payload, List<HeaderConfig> headerConfigs) {
    Map<String, String> resolvedOutputVariables = new HashMap<>();
    if (outputVariables == null) {
      return resolvedOutputVariables;
    }
    EventListenerStepInstanceExpressionEvaluator eventListenerStepInstanceExpressionEvaluator =
        new EventListenerStepInstanceExpressionEvaluator(payload, headerConfigs);
    outputVariables.keySet().forEach(name -> {
      ParameterField<?> outputValue = (ParameterField<?>) outputVariables.get(name);

      Object value = outputValue.isExpression() ? eventListenerStepInstanceExpressionEvaluator.evaluateExpression(
                         outputValue.getExpressionValue(), ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
                                                : outputValue.getValue();
      resolvedOutputVariables.put(name, value.toString());
    });
    return resolvedOutputVariables;
  }
}

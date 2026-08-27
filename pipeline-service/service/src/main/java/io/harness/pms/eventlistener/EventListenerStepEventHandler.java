/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.eventlistener;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;

import static java.util.stream.Collectors.toList;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.webhookpayloads.webhookdata.EventHeader;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.EventListenerStepEventAutoLogContext;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.product.ci.scm.proto.EventBridge;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.steps.eventlistener.EventListenerStepInstanceService;
import io.harness.steps.eventlistener.EventlistenerStep;
import io.harness.steps.eventlistener.beans.EventListenerStepInstanceStatus;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance;
import io.harness.steps.eventlistener.evaluation.EventListenerStepInstanceCriteriaEvaluator;
import io.harness.steps.eventlistener.evaluation.EventListenerStepInstanceExpressionEvaluator;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.webhook.utils.WebhookPayloadUtils;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class EventListenerStepEventHandler extends PmsBaseEventHandler<WebhookDTO> {
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private WebhookPayloadUtils webhookPayloadUtils;
  @Inject private EventListenerStepInstanceService eventListenerStepInstanceService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private PersistentLocker persistentLocker;

  private static final String EVENT_LISTENER_STEP_INSTANCE_LOCK = "EVENT_LISTENER_STEP_INSTANCE_LOCK_";
  public static final Duration EVENT_LISTENER_STEP_INSTANCE_LOCK_TIMEOUT = Duration.ofSeconds(200);
  public static final Duration EVENT_LISTENER_STEP_INSTANCE_WAIT_TIMEOUT = Duration.ofSeconds(220);
  private static final String EVENT_TYPE = "event_listener_step";

  public void processEvent(WebhookDTO webhookDTO) {
    SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
    try {
      EventBridge eventBridge = webhookDTO.getParsedResponse().getEventBridge();
      String webhookIdFromEvent = (NGCommonEntityConstants.PROJECT).equals(eventBridge.getScope())
          ? eventBridge.getWebhookId()
          : eventBridge.getScope() + "." + eventBridge.getWebhookId();
      Iterator<EventListenerStepInstance> instanceList =
          eventListenerStepInstanceService.findByWebhookIdAndStatusWaiting(
              webhookDTO.getAccountId(), webhookIdFromEvent);
      List<HeaderConfig> headerConfigs = prepareHeaders(webhookDTO);
      EventListenerStepInstanceExpressionEvaluator eventListenerStepInstanceExpressionEvaluator =
          new EventListenerStepInstanceExpressionEvaluator(webhookDTO.getJsonPayload(), headerConfigs);
      while (instanceList.hasNext()) {
        EventListenerStepInstance instance = instanceList.next();
        checkSuccessAndFailureCriteria(
            instance, webhookDTO, headerConfigs, eventListenerStepInstanceExpressionEvaluator);
      }
    } catch (InvalidRequestException e) {
      log.warn("[EventListener] Invalid Request Exception while processing webhook event with id {} for event listener "
              + "step.",
          webhookDTO.getEventId(), e);
    } catch (Exception e) {
      log.error(
          "[EventListener] Exception while processing webhook event for event with id {} listener step. Please check",
          webhookDTO.getEventId(), e);
    }
  }

  @Override
  protected Ambiance extractAmbiance(WebhookDTO event) {
    return null;
  }

  @Override
  protected String getEventType(WebhookDTO message) {
    return EVENT_TYPE;
  }

  @Override
  protected PmsGitSyncBranchContextGuard gitSyncContext(WebhookDTO event) {
    return null;
  }

  @Override
  protected AutoLogContext autoLogContext(WebhookDTO event) {
    Map<String, String> logContext = new HashMap<>(CollectionUtils.emptyIfNull(extraLogProperties(event)));
    return new AutoLogContext(logContext, AutoLogContext.OverrideBehavior.OVERRIDE_NESTS);
  }

  @Override
  protected void handleEventWithContext(WebhookDTO webhookDTO) {
    try (
        EventListenerStepEventAutoLogContext ignore0 = new EventListenerStepEventAutoLogContext(
            webhookDTO.getEventId(), null, webhookDTO.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      if (EmptyPredicate.isNotEmpty(webhookDTO.getWebhookAllPayloadDataUuid())
          && pmsFeatureFlagHelper.isEnabled(
              webhookDTO.getAccountId(), FeatureName.CDS_STORE_WEBHOOK_PAYLOAD_IN_FILE_STORAGE)) {
        webhookDTO = webhookPayloadUtils.addPayloadData(webhookDTO);
      }
      processEvent(webhookDTO);
    } catch (Exception e) {
      log.error("[EventListener] Exception while processing webhook event for event listener step. Please check", e);
    }
  }

  @Override
  @NonNull
  protected Map<String, String> extraLogProperties(WebhookDTO event) {
    return ImmutableMap.<String, String>builder()
        .put("eventType", EVENT_TYPE)
        .put("accountId", event.getAccountId())
        .put("eventId", event.getEventId())
        .build();
  }

  private void checkSuccessAndFailureCriteria(EventListenerStepInstance instance, WebhookDTO webhookDTO,
      List<HeaderConfig> headerConfigs,
      EventListenerStepInstanceExpressionEvaluator eventListenerStepInstanceExpressionEvaluator) {
    Ambiance ambiance = instance.getAmbiance();
    NGLogCallback logCallback =
        new NGLogCallback(logStreamingStepClientFactory, ambiance, EventlistenerStep.COMMAND_UNIT, false);

    try {
      logCallback.saveExecutionLog(
          String.format("Evaluating success criteria for event id %s...", webhookDTO.getEventId()));
      log.info("[EventListener] Evaluating success criteria for instanceId - {}, eventId - {}", instance.getId(),
          webhookDTO.getEventId());
      boolean successCriteriaResult =
          evaluateCriteria(instance.getSuccessCriteria(), eventListenerStepInstanceExpressionEvaluator);
      if (successCriteriaResult) {
        updateEventListenerStepInstanceAndLog(logCallback,
            String.format("Success criteria has been met for eventId %s", webhookDTO.getEventId()), LogColor.Cyan,
            EventListenerStepInstanceStatus.SUCCEEDED, instance.getId(), webhookDTO.getEventId(), headerConfigs,
            LogLevel.INFO, CommandExecutionStatus.RUNNING, "success criteria");
        return;
      }

      log.info("[EventListener] Success criteria has not been met for instanceId - {}, eventId - {}", instance.getId(),
          webhookDTO.getEventId());
      logCallback.saveExecutionLog(
          String.format("Success criteria has not been met for eventId - %s", webhookDTO.getEventId()));

      if (EmptyPredicate.isNotEmpty(instance.getFailureCriteria())) {
        log.info("[EventListener] Evaluating failure criteria for instanceId - {}, eventId - {}", instance.getId(),
            webhookDTO.getEventId());
        logCallback.saveExecutionLog(
            String.format("Evaluating failure criteria for eventId %s...", webhookDTO.getEventId()));
        boolean failureCriteriaResult =
            evaluateCriteria(instance.getFailureCriteria(), eventListenerStepInstanceExpressionEvaluator);
        if (failureCriteriaResult) {
          updateEventListenerStepInstanceAndLog(logCallback,
              String.format("Failure criteria has been met for eventId %s", webhookDTO.getEventId()), LogColor.Red,
              EventListenerStepInstanceStatus.FAILED, instance.getId(), webhookDTO.getEventId(), headerConfigs,
              LogLevel.INFO, CommandExecutionStatus.RUNNING, "failure criteria");
          return;
        }
        log.info("Failure criteria has not been met for instanceId - {}, eventId - {}", instance.getId(),
            webhookDTO.getEventId());
        logCallback.saveExecutionLog(
            String.format("Failure criteria has not been met for event id %s", webhookDTO.getEventId()));
      }
    } catch (Exception e) {
      log.warn(
          "[EventListener] Exception while checking success/failure criteria, eventId {}", webhookDTO.getEventId(), e);
      updateEventListenerStepInstanceAndLog(logCallback,
          String.format("Exception while checking success/failure criteria, eventId %s", instance.getId()),
          LogColor.Red, EventListenerStepInstanceStatus.RUNTIME_EXCEPTION, instance.getId(), webhookDTO.getEventId(),
          headerConfigs, LogLevel.ERROR, CommandExecutionStatus.FAILURE, "exception");
    }
  }

  private boolean evaluateCriteria(
      String expression, EventListenerStepInstanceExpressionEvaluator eventListenerStepInstanceExpressionEvaluator) {
    return EventListenerStepInstanceCriteriaEvaluator.evaluateJexlCriteria(
        expression, eventListenerStepInstanceExpressionEvaluator);
  }

  private void updateEventListenerStepInstanceAndLog(NGLogCallback logCallback, String logMessage, LogColor logColor,
      EventListenerStepInstanceStatus eventListenerStepInstanceStatus, String eventListenerStepInstanceId,
      String eventCorrelationId, List<HeaderConfig> headerConfigs, LogLevel logLevel,
      CommandExecutionStatus commandExecutionStatus, String criteriaResult) {
    try (AcquiredLock<?> acquiredLock =
             persistentLocker.waitToAcquireLock(EVENT_LISTENER_STEP_INSTANCE_LOCK + eventListenerStepInstanceId,
                 EVENT_LISTENER_STEP_INSTANCE_LOCK_TIMEOUT, EVENT_LISTENER_STEP_INSTANCE_WAIT_TIMEOUT)) {
      if (eventListenerStepInstanceService.finalizeStatus(
              eventListenerStepInstanceId, eventCorrelationId, eventListenerStepInstanceStatus, headerConfigs)
          != null) {
        log.info("[EventListener] {}", logMessage);
        logCallback.saveExecutionLog(LogHelper.color(logMessage, logColor), logLevel, commandExecutionStatus);
      } else {
        log.info("[EventListener] Ignoring the {} for event id {} as it is already in final status for instance id {}",
            criteriaResult, eventCorrelationId, eventListenerStepInstanceId);
        logCallback.saveExecutionLog(
            LogHelper.color(
                String.format("Ignoring the %s for event id %s as it is already in final status for instance id %s",
                    criteriaResult, eventCorrelationId, eventListenerStepInstanceId),
                logColor),
            logLevel, commandExecutionStatus);
      }
    }
  }

  public List<HeaderConfig> prepareHeaders(WebhookDTO webhookDTO) {
    List<EventHeader> headersList = webhookDTO.getHeadersList();
    return headersList.stream()
        .map(eventHeader
            -> HeaderConfig.builder()
                   .key(eventHeader.getKey())
                   .values(new ArrayList<>(eventHeader.getValuesList()))
                   .build())
        .collect(toList());
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.helper;

import static io.harness.beans.constants.JsonConstants.RESOLVE_OBJECTS_VIA_JSON_SELECT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.notification.NotificationConstants.EVENT_DETAILS;
import static io.harness.notification.NotificationConstants.NOTIFICATION_BODY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_CONTENT;
import static io.harness.notification.NotificationConstants.NOTIFICATION_DURATION;
import static io.harness.notification.NotificationConstants.NOTIFICATION_DURATION_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_EVENT_DETAILS_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_EVENT_TYPE_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_IMAGE_STATUS;
import static io.harness.notification.NotificationConstants.NOTIFICATION_IMAGE_STATUS_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_NODE_END_DATE;
import static io.harness.notification.NotificationConstants.NOTIFICATION_NODE_END_DATE_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_NODE_START_DATE;
import static io.harness.notification.NotificationConstants.NOTIFICATION_NODE_START_DATE_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_NODE_STATUS;
import static io.harness.notification.NotificationConstants.NOTIFICATION_NODE_STATUS_EXPRESSION_KEY;
import static io.harness.notification.NotificationConstants.NOTIFICATION_THEME_COLOR;
import static io.harness.notification.NotificationConstants.NOTIFICATION_THEME_COLOR_EXPRESSION_KEY;
import static io.harness.notification.PipelineEventType.PIPELINE_FAILED;
import static io.harness.notification.PipelineEventType.PIPELINE_RESUMED;
import static io.harness.notification.PipelineEventType.PIPELINE_START;
import static io.harness.notification.PipelineEventType.PIPELINE_SUCCESS;
import static io.harness.notification.PipelineEventType.STAGE_FAILED;
import static io.harness.notification.PipelineEventType.STAGE_START;
import static io.harness.notification.PipelineEventType.STAGE_SUCCESS;
import static io.harness.notification.PipelineEventType.TRIGGER_FAILED;
import static io.harness.notification.PipelineEventType.WAITING_FOR_USER_ACTION;
import static io.harness.pms.notification.helper.MultiDeploymentUtils.ENVIRONMENT_REF_EXPRESSION;
import static io.harness.pms.notification.helper.MultiDeploymentUtils.INFRA_IDENTIFIER_EXPRESSION;
import static io.harness.pms.notification.helper.MultiDeploymentUtils.SERVICE_REF_EXPRESSION;
import static io.harness.pms.yaml.YAMLFieldNameConstants.NAME;
import static io.harness.pms.yaml.YAMLFieldNameConstants.NOTIFICATIONS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.NOTIFICATION_RULES_V0;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.TEMPLATE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.TEMPLATE_INPUTS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.TRUE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.VALUE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.VARIABLES;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.common.ExpressionMode;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;
import io.harness.notification.NotificationConstants;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.PipelineEventType;
import io.harness.notification.PipelineEventTypeConstants;
import io.harness.notification.TriggerExecutionInfo;
import io.harness.notification.bean.NotificationChannelWrapper;
import io.harness.notification.bean.NotificationRules;
import io.harness.notification.bean.PipelineEvent;
import io.harness.notification.channelDetails.NotificationChannelType;
import io.harness.notification.channelDetails.PmsEmailChannel;
import io.harness.notification.channelDetails.PmsNotificationChannel;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.entities.NotificationEntity;
import io.harness.notification.entities.NotificationEvent;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.approval.notification.stagemetadata.StageMetadataNotificationHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.notification.NotificationRulesMapper;
import io.harness.pms.notification.PipelineNotificationConstants;
import io.harness.pms.notification.PipelineNotificationEventMeta;
import io.harness.pms.notification.PipelineNotificationUtils;
import io.harness.pms.notification.WebhookNotificationEvent;
import io.harness.pms.notification.WebhookNotificationEvent.WebhookNotificationEventBuilder;
import io.harness.pms.notification.WebhookNotificationServiceImpl;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.pipeline.yaml.UnifiedPipelineYaml;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.security.PmsSecurityContextNoSideEffectsGuard;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.sanitizer.HtmlInputSanitizer;
import io.harness.security.SecurityContextBuilder;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class NotificationHelper {
  @Inject NotificationClient notificationClient;
  @Inject PlanExecutionService planExecutionService;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject PipelineServiceConfiguration pipelineServiceConfiguration;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject PmsEngineExpressionService pmsEngineExpressionService;
  @Inject PMSPipelineService pmsPipelineService;
  @Inject PipelineExpressionHelper pipelineExpressionHelper;
  @Inject HtmlInputSanitizer userNameSanitizer;
  @Inject PMSExecutionService pmsExecutionService;

  @Inject WebhookNotificationServiceImpl webhookNotificationService;
  @Inject NotificationRulesMapper notificationRulesMapper;

  @Inject PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject NotificationEventsHelper notificationEventsHelper;

  @Inject private PersistentLocker persistentLocker;
  @Inject PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Inject StageMetadataNotificationHelper stageMetadataNotificationHelper;

  private static final String NOT_AVAILABLE = "NA";
  private static final String NOTIFY_LOCK = "Notify-";
  private static final String TEMPLATE_IDENTIFIER_KEY = "TEMPLATE_IDENTIFIER";
  private static final String PIPELINE_IDENTIFIER_KEY = "pipelineIdentifier";
  private static final String TEMPLATE_NAME = "pms_%s";
  private static final String RESOLVED_NOTIFICATION_CONTENT_KEY = "resolvedNotificationContent";
  private static final String NOTIFICATION_IDENTIFIER = "notificationIdentifier";
  private static final List<PipelineEventType> ALLOWED_EVENT_TYPE_ON_CNS =
      List.of(PIPELINE_START, PIPELINE_SUCCESS, PIPELINE_FAILED, STAGE_START, STAGE_SUCCESS, STAGE_FAILED,
          TRIGGER_FAILED, WAITING_FOR_USER_ACTION, PIPELINE_RESUMED);
  private static final Set<String> projectionFields =
      Set.of(PlanExecutionSummaryKeys.name, PlanExecutionSummaryKeys.tags, PlanExecutionSummaryKeys.failureInfo,
          PlanExecutionSummaryKeys.executionTriggerInfo, PlanExecutionSummaryKeys.accountId,
          PlanExecutionSummaryKeys.orgIdentifier, PlanExecutionSummaryKeys.projectIdentifier,
          PlanExecutionSummaryKeys.parentUniqueId, PlanExecutionSummaryKeys.planExecutionId,
          PlanExecutionSummaryKeys.moduleInfo, PlanExecutionSummaryKeys.startingNodeId,
          PlanExecutionSummaryKeys.pipelineIdentifier, PlanExecutionSummaryKeys.modules);
  private static final ObjectMapper simpleYamlMapper;
  static {
    simpleYamlMapper = new ObjectMapper(new YAMLFactory()
                                            .disable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
  }

  public boolean shouldNotifyAfterGraphGen(String accountId) {
    return accountId != null && pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIE_NOTIFY_AFTER_GRAPH_UPDATE);
  }

  public boolean shouldNotifyAfterGraphGen(Ambiance ambiance) {
    return shouldNotifyAfterGraphGen(AmbianceUtils.getAccountId(ambiance));
  }

  public Optional<PipelineEventType> getEventTypeForStage(NodeExecution nodeExecution) {
    if (!OrchestrationUtils.isStageNode(nodeExecution)) {
      return Optional.empty();
    }
    // IGNORE_FAILED and PASSED_WITH_WARNING are both treated as Stage SUCCEEDED for notifications.
    if (nodeExecution.getStatus() == Status.SUCCEEDED || nodeExecution.getStatus() == Status.IGNORE_FAILED
        || nodeExecution.getStatus() == Status.PASSED_WITH_WARNING) {
      return Optional.of(STAGE_SUCCESS);
    }
    if (StatusUtils.brokeAndAbortedStatuses().contains(nodeExecution.getStatus())) {
      return Optional.of(STAGE_FAILED);
    }
    return Optional.empty();
  }

  public void sendNotification(
      Ambiance ambiance, PipelineEventType pipelineEventType, NodeExecution nodeExecution, Long updatedAt) {
    sendNotification(ambiance, pipelineEventType, nodeExecution,
        getPlanExecutionMetadata(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId()), updatedAt);
  }

  public void sendCNSNotification(
      Ambiance ambiance, PipelineEventType pipelineEventType, NodeExecution nodeExecution, Long updatedAt) {
    sendCNSNotification(ambiance, pipelineEventType, nodeExecution, updatedAt, nodeExecution.getUuid());
  }

  public void sendCNSNotification(Ambiance ambiance, PipelineEventType pipelineEventType, NodeExecution nodeExecution,
      Long updatedAt, String dedupeNodeExecutionId) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String lockName = NOTIFY_LOCK + planExecutionId + dedupeNodeExecutionId;
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String parentUniqueId = AmbianceUtils.getParentUniqueIdentifier(ambiance);
    String pipelineIdentifier = ambiance.getMetadata().getPipelineIdentifier();

    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLock(lockName, Duration.ofSeconds(10), Duration.ofSeconds(20))) {
      if (lock == null) {
        log.warn(
            String.format("[PMS_NOTIFY_LOCK] Not able to take lock on notifications - %s, returning early.", lockName));
        return;
      }
      if (notificationEventsHelper.isNotificationEventAlreadySent(
              planExecutionId, dedupeNodeExecutionId, pipelineEventType)) {
        return;
      }
      Map<String, String> notificationContent = constructTemplateData(
          ambiance, pipelineEventType, nodeExecution, updatedAt, orgIdentifier, projectIdentifier);
      try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
        sendCentralisedNotificationAndHandleError(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            pipelineEventType, notificationContent, parentUniqueId);
      }
      markNotificationAsSent(planExecutionId, dedupeNodeExecutionId, pipelineEventType);
    }
  }

  public void sendNotification(Ambiance ambiance, PipelineEventType pipelineEventType, NodeExecution nodeExecution,
      PlanExecutionMetadata planExecutionMetadata, Long updatedAt) {
    boolean notifyOnlyMe = Boolean.TRUE.equals(planExecutionMetadata.getNotifyOnlyUser());

    String identifier = getStageIdentifier(nodeExecution);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String parentUniqueId = AmbianceUtils.getParentUniqueIdentifier(ambiance);
    String pipelineIdentifier = ambiance.getMetadata().getPipelineIdentifier();
    Map<String, String> notificationContent =
        constructTemplateData(ambiance, pipelineEventType, nodeExecution, updatedAt, orgIdentifier, projectIdentifier);

    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      sendCentralisedNotificationAndHandleError(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
          pipelineEventType, notificationContent, parentUniqueId);
    }
    if (notifyOnlyMe) {
      if (PipelineEventType.notifyOnlyUserEvents.contains(pipelineEventType)) {
        sendNotificationOnlyToUserWhoTriggeredPipeline(
            ambiance, pipelineEventType, nodeExecution, updatedAt, true, planExecutionMetadata.getYaml());
      }
      return;
    }

    if (!ambiance.getMetadata().getIsNotificationConfigured()) {
      return;
    }

    String yaml = planExecutionMetadata.getYaml();
    log.info("[DEBUG]: [NotificationHelper]: pipeline yaml fetched for processing: {}", yaml);
    if (isEmpty(yaml)) {
      log.error("Empty yaml found in executionMetaData for execution id: {}", ambiance.getPlanExecutionId());
      return;
    }
    String stepBaseFqn = pipelineEventType.isStepLevelEvent() ? computeStepBaseFqnFromAmbiance(ambiance) : null;
    List<NotificationRules> notificationRules = null;
    try (AutoLogContext ignore1 = AmbianceUtils.autoLogContext(ambiance);
         AutoLogContext ignore2 = new NotificationLogContext(pipelineEventType.name())) {
      notificationRules = getNotificationRulesForEvent(yaml, ambiance, pipelineEventType, identifier, stepBaseFqn);
    } catch (IOException exception) {
      log.error("Unable to parse yaml to get notification objects", exception);
    }
    log.info("[DEBUG]: [NotificationHelper]: notification rules extracted for processing: {}", notificationRules);
    if (isEmpty(notificationRules)) {
      return;
    }

    try (AutoLogContext ignore1 = AmbianceUtils.autoLogContext(ambiance);
         AutoLogContext ignore2 = new NotificationLogContext(pipelineEventType.name())) {
      sendNotificationInternal(NotificationContext.builder()
                                   .notificationRulesList(notificationRules)
                                   .pipelineEventType(pipelineEventType)
                                   .identifier(identifier)
                                   .stepBaseFqn(stepBaseFqn)
                                   .accountIdentifier(accountId)
                                   .notificationContent(notificationContent)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .notifyOnlyMe(notifyOnlyMe)
                                   .yaml(yaml)
                                   .build(),
          ambiance, null);

    } catch (Exception ex) {
      log.error(String.format("Exception occurred while sending notification in sendNotificationInternal for account: "
                        + "[%s], org: [%s], project: [%s] and pipeline [%s]",
                    accountId, orgIdentifier, projectIdentifier, pipelineIdentifier),
          ex);
    }
  }

  void sendTriggerFailureNotification(
      NotificationContext notificationContext, Ambiance ambiance, TriggerNotificationData triggerNotificationData) {
    sendNotificationInternal(notificationContext, ambiance, triggerNotificationData);
  }

  // This method should not be called from outside of this class, it is just made default accessible to be unit tested
  @VisibleForTesting
  void sendNotificationInternal(
      NotificationContext notificationContext, Ambiance ambiance, TriggerNotificationData triggerNotificationData) {
    if (notificationContext.getNotificationContent() == null) {
      log.error("Unable to send notification for plan execution id: {} for pipeline : {} because notification content "
              + "is null",
          ambiance.getPlanExecutionId(), ambiance.getMetadata().getPipelineIdentifier());
      return;
    }
    JsonNode rootNode = YamlPipelineUtils.readAsJsonNode(notificationContext.getYaml());
    // The context once set, was taking the resolutionData for all the notification rules. Created a new map for each
    // iteration to avoid state leakage between notification rules.
    Map<String, String> originalNotificationContent = notificationContext.getNotificationContent();

    for (NotificationRules notificationRules : notificationContext.getNotificationRulesList()) {
      if (!notificationRules.isEnabled()) {
        continue;
      }
      Map<String, String> notificationContent = new HashMap<>(originalNotificationContent);

      List<PipelineEvent> pipelineEvents = notificationRules.getPipelineEvents();
      boolean shouldSendNotification = notificationContext.isNotifyOnlyMe()
          || shouldSendNotification(pipelineEvents, notificationContext.getPipelineEventType(),
              notificationContext.getIdentifier(), notificationContext.getStepBaseFqn());
      if (shouldSendNotification) {
        // fetch the notification rules, check if there is a template ref, generate the merged yaml
        if (rootNode != null) {
          String resolvedBody = processNotificationTemplateResolution(rootNode, ambiance,
              notificationContext.getAccountIdentifier(), notificationContext.getOrgIdentifier(),
              notificationContext.getProjectIdentifier(), notificationRules.getName(),
              notificationContext.getPipelineEventType(), triggerNotificationData, notificationContent);
          log.info("[DEBUG]: [NotificationHelper]: Notification content resolved body: {}", resolvedBody);
          if (resolvedBody != null) {
            notificationContent.put(RESOLVED_NOTIFICATION_CONTENT_KEY, resolvedBody);
          }
        }

        NotificationChannelWrapper wrapper = notificationRules.getNotificationChannelWrapper().getValue();
        if (wrapper.getType() != null) {
          String templateId =
              getNotificationTemplate(notificationContext.getPipelineEventType().getLevel(), wrapper.getType());
          NotificationChannel channel = wrapper.getNotificationChannel().toNotificationChannel(
              notificationContext.getAccountIdentifier(), notificationContext.getOrgIdentifier(),
              notificationContext.getProjectIdentifier(), templateId, notificationContent, ambiance);
          log.info("Sending notification via notification-client for plan execution id: {}, content: {}",
              ambiance.getPlanExecutionId(), notificationContent);
          try (AutoLogContext ignore1 = AmbianceUtils.autoLogContext(ambiance);
               AutoLogContext ignore2 = new NotificationLogContext(notificationContext.getPipelineEventType().name())) {
            notificationClient.sendNotificationAsync(channel);
          } catch (Exception ex) {
            log.error("Unable to send notification because of following exception", ex);
          }
        } else {
          log.error("Unable to send notification for plan execution id: {} for pipeline : {} because notification type "
                  + "is null",
              ambiance.getPlanExecutionId(), ambiance.getMetadata().getPipelineIdentifier());
        }
      }
    }
  }

  void sendCentralisedNotificationAndHandleError(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PipelineEventType pipelineEventType, Map<String, String> notificationContent,
      String parentUniqueId) {
    try {
      sendCentralisedNotification(pipelineEventType, accountId, orgIdentifier, projectIdentifier, notificationContent,
          pipelineIdentifier, parentUniqueId);
    } catch (Exception exception) {
      log.error(
          String.format(
              "Error occurred while sending notification to centralised notifications for pipelineIdentifier [%s].",
              pipelineIdentifier),
          exception);
    }
  }

  @VisibleForTesting
  void sendCentralisedNotification(PipelineEventType pipelineEventType, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, String> notificationContent, String pipelineIdentifier,
      String parentUniqueId) {
    try {
      if (!ALLOWED_EVENT_TYPE_ON_CNS.contains(pipelineEventType)) {
        return;
      }
      String notificationTriggerRequestId = generateUuid();
      String eventType = getPipelineEventType(pipelineEventType);

      Map<String, String> templateData = new HashMap<>(notificationContent);
      templateData.put(PIPELINE_IDENTIFIER_KEY, pipelineIdentifier);
      templateData.put(
          TEMPLATE_IDENTIFIER_KEY, String.format(TEMPLATE_NAME, pipelineEventType.getLevel().toLowerCase()));

      NotificationTriggerRequest.Builder notificationTriggerRequestBuilder =
          NotificationTriggerRequest.newBuilder()
              .setId(notificationTriggerRequestId)
              .setAccountId(accountIdentifier)
              .setOrgId(orgIdentifier)
              .setProjectId(projectIdentifier)
              .setEventEntity(NotificationEntity.PIPELINE.name())
              .setEntityIdentifier(pipelineIdentifier)
              .setEvent(eventType)
              .putAllTemplateData(templateData);
      if (isNotEmpty(parentUniqueId)) {
        notificationTriggerRequestBuilder.setParentUniqueId(parentUniqueId);
      }
      log.info(String.format(
          "Sending notification for PipelineIdentifier [%s] for eventType [%s] ", pipelineIdentifier, eventType));
      notificationClient.sendNotificationTrigger(notificationTriggerRequestBuilder.build());
    } catch (Exception exception) {
      log.error(
          String.format("Error occurred while sending notification for PipelineIdentifier [%s] for eventType [%s]",
              pipelineIdentifier, pipelineEventType.getDisplayName()),
          exception);
    }
  }

  private String getPipelineEventType(PipelineEventType pipelineEventType) {
    return switch (pipelineEventType) {
      case PIPELINE_START -> NotificationEvent.PIPELINE_START.name();
      case PIPELINE_SUCCESS -> NotificationEvent.PIPELINE_SUCCESS.name();
      case PIPELINE_FAILED -> NotificationEvent.PIPELINE_FAILED.name();
      case STAGE_START -> NotificationEvent.STAGE_START.name();
      case STAGE_SUCCESS -> NotificationEvent.STAGE_SUCCESS.name();
      case STAGE_FAILED -> NotificationEvent.STAGE_FAILED.name();
      case TRIGGER_FAILED -> NotificationEvent.TRIGGER_FAILED.name();
      case WAITING_FOR_USER_ACTION -> NotificationEvent.WAITING_FOR_USER_ACTION.name();
      case PIPELINE_RESUMED -> NotificationEvent.PIPELINE_RESUMED.name();
      default -> throw new InvalidRequestException("Received unsupported PipelineEventType for sending request to CentralisedNotifications");
    };
  }

  private String getNotificationTemplate(String level, String channelType) {
    return String.format("pms_%s_%s_plain", level.toLowerCase(), channelType.toLowerCase());
  }

  @VisibleForTesting
  boolean shouldSendNotification(
      List<PipelineEvent> pipelineEvents, PipelineEventType pipelineEventType, String identifier, String stepBaseFqn) {
    String pipelineEventTypeLevel = pipelineEventType.getLevel();
    for (PipelineEvent pipelineEvent : pipelineEvents) {
          PipelineEventType thisEventType = pipelineEvent.getType();
          if (thisEventType == PipelineEventType.ALL_EVENTS) {
            return true;
          } else if (thisEventType == pipelineEventType && pipelineEventTypeLevel.equals("Stage")) {
            List<String> stages = pipelineEvent.getForStages();
            if (stages != null && (stages.contains(identifier) || stages.contains(YAMLFieldNameConstants.ALL_STAGES))) {
              return true;
            }
          } else if (thisEventType == pipelineEventType && pipelineEventTypeLevel.equals("Step")) {
            List<String> steps = pipelineEvent.getForSteps();
            if (steps == null || steps.isEmpty() || steps.contains(YAMLFieldNameConstants.ALL_STEPS)) {
              return true;
            }
            if (stepBaseFqn != null && steps.contains(stepBaseFqn)) {
              return true;
            }
          } else if (thisEventType == pipelineEventType) {
            return true;
          }
        }
        return false;
    }

    private List<NotificationRules> getNotificationRulesFromYamlV1(String yaml, Ambiance ambiance) throws IOException {
      UnifiedPipelineYaml unifiedPipelineYaml = YamlUtils.read(yaml, UnifiedPipelineYaml.class);
      List<io.harness.notification.bean.v1.NotificationRules> v1NotificationRules = RecastOrchestrationUtils.fromMap(
          (Map<String, Object>) pmsEngineExpressionService.resolve(
              ambiance, RecastOrchestrationUtils.toMap(unifiedPipelineYaml.getNotificationRules()), true),
          List.class);
      return notificationRulesMapper.toNotificationRulesV0(v1NotificationRules);
    }

    public List<NotificationRules> getNotificationRulesFromYaml(String yaml, Ambiance ambiance) throws IOException {
      if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
        return getNotificationRulesFromYamlV1(yaml, ambiance);
      }
      BasicPipeline basicPipeline = YamlUtils.read(yaml, BasicPipeline.class);
      return RecastOrchestrationUtils.fromMap(
          (Map<String, Object>) pmsEngineExpressionService.resolve(
              ambiance, RecastOrchestrationUtils.toMap(basicPipeline.getNotificationRules()), true),
          List.class);
    }

    @VisibleForTesting
    List<NotificationRules> getNotificationRulesForEvent(String yaml, Ambiance ambiance,
        PipelineEventType pipelineEventType, String identifier, String stepBaseFqn) throws IOException {
      String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
      if (!pmsFeatureFlagHelper.isEnabled(
              accountIdentifier, FeatureName.PIPE_FILTER_NOTIFICATION_RULES_BY_EVENT_TYPE)) {
        return getNotificationRulesFromYaml(yaml, ambiance);
      }

      if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
        return getNotificationRulesFromYamlV1ForEvent(yaml, ambiance, pipelineEventType, identifier, stepBaseFqn);
      }

      BasicPipeline basicPipeline = YamlUtils.read(yaml, BasicPipeline.class);
      List<NotificationRules> eligibleRules = filterEligibleNotificationRules(
          basicPipeline.getNotificationRules(), pipelineEventType, identifier, stepBaseFqn);
      return resolveNotificationRulesIndividually(eligibleRules, ambiance, pipelineEventType);
    }

    private List<NotificationRules> getNotificationRulesFromYamlV1ForEvent(String yaml, Ambiance ambiance,
        PipelineEventType pipelineEventType, String identifier, String stepBaseFqn) throws IOException {
      UnifiedPipelineYaml unifiedPipelineYaml = YamlUtils.read(yaml, UnifiedPipelineYaml.class);
      List<io.harness.notification.bean.v1.NotificationRules> eligibleV1Rules = filterEligibleNotificationRulesV1(
          unifiedPipelineYaml.getNotificationRules(), pipelineEventType, identifier, stepBaseFqn);
      return resolveNotificationRulesV1Individually(eligibleV1Rules, ambiance, pipelineEventType);
    }

    private List<NotificationRules> getUnresolvedNotificationRulesFromYaml(String yaml, Ambiance ambiance)
        throws IOException {
      if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
        UnifiedPipelineYaml unifiedPipelineYaml = YamlUtils.read(yaml, UnifiedPipelineYaml.class);
        return notificationRulesMapper.toNotificationRulesV0(unifiedPipelineYaml.getNotificationRules());
      }
      return YamlUtils.read(yaml, BasicPipeline.class).getNotificationRules();
    }

    private List<NotificationRules> filterEligibleNotificationRules(List<NotificationRules> notificationRules,
        PipelineEventType pipelineEventType, String identifier, String stepBaseFqn) {
      if (isEmpty(notificationRules)) {
        return Collections.emptyList();
      }
      return notificationRules.stream()
          .filter(rule -> isNotificationRuleEligible(rule, pipelineEventType, identifier, stepBaseFqn))
          .collect(Collectors.toList());
    }

    private List<io.harness.notification.bean.v1.NotificationRules> filterEligibleNotificationRulesV1(
        List<io.harness.notification.bean.v1.NotificationRules> notificationRules, PipelineEventType pipelineEventType,
        String identifier, String stepBaseFqn) {
      if (isEmpty(notificationRules)) {
        return Collections.emptyList();
      }
      return notificationRules.stream()
          .filter(rule -> isNotificationRuleEligibleV1(rule, pipelineEventType, identifier, stepBaseFqn))
          .collect(Collectors.toList());
    }

    private boolean isNotificationRuleEligibleV1(io.harness.notification.bean.v1.NotificationRules notificationRule,
        PipelineEventType pipelineEventType, String identifier, String stepBaseFqn) {
      List<NotificationRules> mappedRules =
          notificationRulesMapper.toNotificationRulesV0(Collections.singletonList(notificationRule));
      return isNotEmpty(mappedRules)
          && isNotificationRuleEligible(mappedRules.get(0), pipelineEventType, identifier, stepBaseFqn);
    }

    private boolean isNotificationRuleEligible(NotificationRules notificationRule, PipelineEventType pipelineEventType,
        String identifier, String stepBaseFqn) {
      return notificationRule.isEnabled() && isNotEmpty(notificationRule.getPipelineEvents())
          && shouldSendNotification(notificationRule.getPipelineEvents(), pipelineEventType, identifier, stepBaseFqn);
    }

    private List<NotificationRules> resolveNotificationRulesIndividually(
        List<NotificationRules> notificationRules, Ambiance ambiance, PipelineEventType pipelineEventType) {
      if (isEmpty(notificationRules)) {
        return Collections.emptyList();
      }
      List<NotificationRules> resolvedRules = new ArrayList<>();
      for (NotificationRules notificationRule : notificationRules) {
        try {
          List<NotificationRules> resolvedRule = RecastOrchestrationUtils.fromMap(
              (Map<String, Object>) pmsEngineExpressionService.resolve(
                  ambiance, RecastOrchestrationUtils.toMap(Collections.singletonList(notificationRule)), true),
              List.class);
          if (isNotEmpty(resolvedRule)) {
            resolvedRules.addAll(resolvedRule);
          }
        } catch (Exception exception) {
          log.error("Unable to resolve expressions for notification rule. planExecutionId: {}, pipelineIdentifier: {}, "
                  + "eventType: {}, ruleName: {}",
              ambiance.getPlanExecutionId(), ambiance.getMetadata().getPipelineIdentifier(), pipelineEventType,
              notificationRule.getName(), exception);
        }
      }
      return resolvedRules;
    }

    private List<NotificationRules> resolveNotificationRulesV1Individually(
        List<io.harness.notification.bean.v1.NotificationRules> notificationRules, Ambiance ambiance,
        PipelineEventType pipelineEventType) {
      if (isEmpty(notificationRules)) {
        return Collections.emptyList();
      }
      List<NotificationRules> resolvedRules = new ArrayList<>();
      for (io.harness.notification.bean.v1.NotificationRules notificationRule : notificationRules) {
        try {
          List<io.harness.notification.bean.v1.NotificationRules> resolvedRule = RecastOrchestrationUtils.fromMap(
              (Map<String, Object>) pmsEngineExpressionService.resolve(
                  ambiance, RecastOrchestrationUtils.toMap(Collections.singletonList(notificationRule)), true),
              List.class);
          if (isNotEmpty(resolvedRule)) {
            resolvedRules.addAll(notificationRulesMapper.toNotificationRulesV0(resolvedRule));
          }
        } catch (Exception exception) {
          log.error("Unable to resolve expressions for notification rule. planExecutionId: {}, pipelineIdentifier: {}, "
                  + "eventType: {}, ruleId: {}, ruleName: {}",
              ambiance.getPlanExecutionId(), ambiance.getMetadata().getPipelineIdentifier(), pipelineEventType,
              notificationRule.getId(), notificationRule.getName(), exception);
        }
      }
      return resolvedRules;
    }

    public String generateUrl(Ambiance ambiance, PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
      return pipelineExpressionHelper.generateUrl(ambiance, pipelineExecutionSummaryEntity);
    }

    public String generatePipelineUrl(
        Ambiance ambiance, PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
      return pipelineExpressionHelper.generatePipelineUrl(ambiance, pipelineExecutionSummaryEntity);
    }

    PlanExecutionMetadata getPlanExecutionMetadata(String accountIdentifier, String planExecutionId) {
      Optional<PlanExecutionMetadata> optional =
          planExecutionMetadataService.findByPlanExecutionId(accountIdentifier, planExecutionId);
      if (!optional.isPresent()) {
        throw new InvalidRequestException("PlanExecutionMetadata not found!");
      }
      return optional.get();
    }

    public String obtainYaml(String accountIdentifier, String planExecutionId) {
      Optional<PlanExecutionMetadata> optional =
          Optional.ofNullable(getPlanExecutionMetadata(accountIdentifier, planExecutionId));
      return optional.map(PlanExecutionMetadata::getYaml).orElse(null);
    }

    @VisibleForTesting
    void sendNotificationOnlyToUserWhoTriggeredPipeline(Ambiance ambiance, PipelineEventType pipelineEventType,
        NodeExecution nodeExecution, Long updatedAt, boolean notifyOnlyMe, String yaml) {
      String identifier = nodeExecution != null ? NodeExecutionContextUtils.obtainStepIdentifier(nodeExecution) : "";
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

      NotificationRules notificationRules = createNotificationRules(ambiance, pipelineEventType);

      try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
        Map<String, String> notificationContent = constructTemplateData(
            ambiance, pipelineEventType, nodeExecution, updatedAt, orgIdentifier, projectIdentifier);
        sendNotificationInternal(NotificationContext.builder()
                                     .notificationRulesList(Collections.singletonList(notificationRules))
                                     .pipelineEventType(pipelineEventType)
                                     .identifier(identifier)
                                     .accountIdentifier(accountId)
                                     .notificationContent(notificationContent)
                                     .orgIdentifier(orgIdentifier)
                                     .projectIdentifier(projectIdentifier)
                                     .notifyOnlyMe(notifyOnlyMe)
                                     .yaml(yaml)
                                     .build(),
            ambiance, null);
      } catch (Exception ex) {
        log.error("Exception occurred in sendNotificationInternal", ex);
      }
    }

    @VisibleForTesting
    NotificationRules createNotificationRules(Ambiance ambiance, PipelineEventType pipelineEventType) {
      List<PipelineEvent> pipelineEvents =
          Collections.singletonList(PipelineEvent.builder().type(pipelineEventType).build());

      // Currently Email is HardCoded, other channel types would be supported in future
      String email = AmbianceUtils.getEmail(ambiance);
      List<String> recipientList = Collections.singletonList(email);
      PmsNotificationChannel pmsNotificationChannel =
          PmsEmailChannel.builder().recipients(recipientList).userGroups(Collections.EMPTY_LIST).build();

      NotificationChannelWrapper notificationChannelWrapper = NotificationChannelWrapper.builder()
                                                                  .type(NotificationChannelType.EMAIL)
                                                                  .notificationChannel(pmsNotificationChannel)
                                                                  .build();
      ParameterField<NotificationChannelWrapper> notificationChannelWrapperField =
          ParameterField.createValueField(notificationChannelWrapper);

      return NotificationRules.builder()
          .enabled(true)
          .pipelineEvents(pipelineEvents)
          .notificationChannelWrapper(notificationChannelWrapperField)
          .build();
    }

    @VisibleForTesting
    Map<String, String> constructTemplateData(Ambiance ambiance, PipelineEventType pipelineEventType,
        NodeExecution nodeExecution, Long updatedAt, String orgIdentifier, String projectIdentifier) {
      Status status = null;
      String name = null;
      FailureInfo failureInfo = null;
      Long startTs = null;
      String nodeExecutionId = "";
      if (nodeExecution != null) {
        status = nodeExecution.getStatus();
        name = nodeExecution.getName();
        failureInfo = nodeExecution.getFailureInfo();
        startTs = nodeExecution.getStartTs();
        nodeExecutionId = nodeExecution.getUuid();
      } else if (!pipelineEventType.getLevel().equals(PipelineEventType.PIPELINE_LEVEL)) {
        log.warn("Cannot send notification for planExecutionId {} eventType {}", ambiance.getPlanExecutionId(),
            pipelineEventType);
        return null;
      }
      return constructTemplateData(ambiance, pipelineEventType, updatedAt, orgIdentifier, projectIdentifier, name,
          status, failureInfo, startTs, nodeExecutionId);
    }

    private Map<String, String> constructTemplateData(Ambiance ambiance, PipelineEventType pipelineEventType,
        Long updatedAt, String orgIdentifier, String projectIdentifier, String nodeExecutionName,
        Status nodeExecutionStatus, FailureInfo nodeExecutionFailureInfo, Long nodeExecutionStartTs,
        String nodeExecutionId) {
      Map<String, String> templateData = new HashMap<>();
      String planExecutionId = ambiance.getPlanExecutionId();
      Set<String> projectionKeys = new HashSet<>(projectionFields);

      if (AmbianceUtils.getStageLevelFromAmbiance(ambiance).isPresent()) {
        String stageRuntimeIdKey =
            PlanExecutionSummaryKeys.layoutNodeMap + "." + AmbianceUtils.getStageRuntimeIdAmbiance(ambiance);
        String stageSetupIdKey =
            PlanExecutionSummaryKeys.layoutNodeMap + "." + AmbianceUtils.getStageSetupIdAmbiance(ambiance);
        projectionKeys.add(stageRuntimeIdKey);
        projectionKeys.add(stageSetupIdKey);
      }

      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
          pmsExecutionService.fetchExecutionSummaryFromDb(planExecutionId, projectionKeys);
      String pipelineId = ambiance.getMetadata().getPipelineIdentifier();
      String pipelineName = pipelineExecutionSummaryEntity.getName();

      WebhookNotificationEventBuilder webhookNotificationEvent =
          WebhookNotificationEvent.builder()
              .triggeredBy(getTriggerExecutionInfo(pipelineExecutionSummaryEntity))
              .moduleInfo(
                  webhookNotificationService.getModuleInfo(ambiance, pipelineExecutionSummaryEntity, pipelineEventType))
              .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
              .orgIdentifier(orgIdentifier)
              .projectIdentifier(projectIdentifier)
              .pipelineIdentifier(pipelineId)
              .planExecutionId(planExecutionId)
              .eventType(pipelineEventType);

      String userName;
      Long startTs;
      Long endTs;
      String startDate;
      String endDate;
      String nodeIdentifier = "";
      String stepIdentifier = "";
      String stageIdentifier = "";
      String nodeName = "";
      String stageName = "";
      String imageStatus;
      String themeColor;

      boolean shouldNotifyAfterGraphUpdate = shouldNotifyAfterGraphGen(ambiance);
      String accountId = AmbianceUtils.getAccountId(ambiance);
      boolean changeNotifactionEventMessage =
          pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIE_CHANGE_NOTIFICATION_EVENT);
      String nodeStatus;
      String executionUrl = generateUrl(ambiance, pipelineExecutionSummaryEntity);
      String pipelineUrl = generatePipelineUrl(ambiance, pipelineExecutionSummaryEntity);
      String eventType = pipelineEventType.getDisplayName();
      String eventDetails = eventType;
      if (!pipelineEventType.getLevel().equals(PipelineEventType.PIPELINE_LEVEL)) {
        imageStatus = PipelineNotificationUtils.getStatusForImage(nodeExecutionStatus);
        themeColor = PipelineNotificationUtils.getThemeColor(nodeExecutionStatus);
        nodeStatus = PipelineNotificationUtils.getNodeStatus(
            nodeExecutionStatus, pipelineEventType, changeNotifactionEventMessage);
        userName = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier();
        startTs = nodeExecutionStartTs / 1000;
        endTs = updatedAt / 1000;
        startDate = new Date(startTs * 1000).toString();
        endDate = new Date(endTs * 1000).toString();
        nodeIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);

        if (pipelineEventType.isStepLevelEvent()) {
          stepIdentifier = nodeIdentifier;
          Optional<Level> stageOptional = AmbianceUtils.getStageLevelFromAmbiance(ambiance);
          if (stageOptional.isPresent()) {
            stageIdentifier = stageOptional.get().getIdentifier();
          }
        }
        nodeName = nodeExecutionName;
        if (pipelineEventType.isStageLevelEvent()) {
          stageIdentifier = nodeIdentifier;
          String stageStrategyIdentifier = getStageIdentifierInStrategy(ambiance).orElse(stageIdentifier);
          stageName = nodeExecutionName;
          String stageDetails = getStageEventDetails(
              pipelineEventType, ambiance, stageStrategyIdentifier, orgIdentifier, projectIdentifier, accountId);
          if (!isEmpty(stageDetails)) {
            eventDetails += " " + stageDetails;
          }
        }
      } else {
        PlanExecution planExecution = planExecutionService.getPlanExecutionMetadata(planExecutionId);
        // when a pipeline level notification is to be sent, the node execution will belong to the pipeline in the new
        // flow, which is populated with nodeExecution + graphUpdateInfo
        Status planExecutionStatus = shouldNotifyAfterGraphUpdate ? nodeExecutionStatus : planExecution.getStatus();
        imageStatus = PipelineNotificationUtils.getStatusForImage(planExecutionStatus);
        themeColor = PipelineNotificationUtils.getThemeColor(planExecutionStatus);
        userName = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier();
        nodeStatus = PipelineNotificationUtils.getNodeStatus(
            planExecutionStatus, pipelineEventType, changeNotifactionEventMessage);
        startTs = planExecution.getStartTs() / 1000;
        if (pipelineEventType.getDisplayName().equals(PipelineEventTypeConstants.PIPELINE_START)) {
          endTs = startTs;
        } else if (planExecution.getEndTs() != null) {
          endTs = planExecution.getEndTs() / 1000;
        } else {
          // For in-progress events (e.g., WAITING_FOR_USER_ACTION), use current time
          endTs = updatedAt / 1000;
        }
        startDate = new Date(startTs * 1000).toString();
        endDate = new Date(endTs * 1000).toString();
      }
      templateData.put("USER_NAME", userNameSanitizer.sanitizeInput(userName));
      templateData.put("ORG_IDENTIFIER", orgIdentifier);
      templateData.put("PROJECT_IDENTIFIER", projectIdentifier);
      boolean cleanEventTypeEnabled =
          pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_CLEAN_NOTIFICATION_EVENT_TYPE);
      templateData.put(NotificationConstants.EVENT_TYPE, cleanEventTypeEnabled ? eventType : eventDetails);
      templateData.put(EVENT_DETAILS, eventDetails);
      templateData.put("PIPELINE", pipelineId);
      templateData.put("PIPELINE_STEP", nodeIdentifier);
      // This env variable is for all nodes not just step
      templateData.put("PIPELINE_STEP_NAME", nodeName);
      templateData.put("START_TS_SECS", String.valueOf(startTs));
      templateData.put("END_TS_SECS", String.valueOf(endTs));
      templateData.put(NOTIFICATION_NODE_START_DATE, startDate);
      templateData.put(NOTIFICATION_NODE_END_DATE, endDate);
      templateData.put(NOTIFICATION_DURATION, ApprovalNotificationHandlerImpl.formatDuration((endTs - startTs) * 1000));
      templateData.put("DURATION", String.valueOf(endTs - startTs));
      templateData.put("URL", executionUrl);
      templateData.put("PIPELINE_URL", pipelineUrl);
      templateData.put("OUTER_DIV", PipelineNotificationConstants.OUTER_DIV);
      templateData.put(NOTIFICATION_IMAGE_STATUS, imageStatus);
      templateData.put(NOTIFICATION_THEME_COLOR, themeColor);
      templateData.put(NOTIFICATION_NODE_STATUS, nodeStatus);
      templateData.put(NotificationConstants.NODE_EXECUTION_ID_KEY, nodeExecutionId);
      templateData.put(NotificationConstants.PLAN_EXECUTION_ID_KEY, planExecutionId);
      webhookNotificationEvent.startTime(startDate);
      webhookNotificationEvent.startTs(startTs);

      webhookNotificationEvent.nodeStatus(nodeStatus);
      webhookNotificationEvent.pipelineUrl(pipelineUrl);
      webhookNotificationEvent.executionUrl(executionUrl);

      List<NGTag> resolvedTags = pipelineExecutionSummaryEntity.getTags();
      boolean isTagsContainExpression = false;
      if (resolvedTags != null) {
        for (NGTag tag : resolvedTags) {
          if (NGExpressionUtils.isExpressionField(tag.getKey())
              || NGExpressionUtils.isExpressionField(tag.getValue())) {
            isTagsContainExpression = true;
            break;
          }
        }
      }
      if (isTagsContainExpression) {
        resolvedTags = (List<NGTag>) pmsEngineExpressionService.resolve(
            ambiance, resolvedTags, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      }
      webhookNotificationEvent.tag(resolvedTags);
      webhookNotificationEvent.pipelineName(pipelineName);
      webhookNotificationEvent.stepName(nodeName);
      webhookNotificationEvent.stageName(stageName);

      String errorMessage = getErrorMessage(
          pipelineEventType, nodeExecutionFailureInfo, pipelineExecutionSummaryEntity, shouldNotifyAfterGraphUpdate);

      if (isNotEmpty(errorMessage)) {
        webhookNotificationEvent.errorMessage(errorMessage);
        templateData.put("ERROR_MESSAGE", errorMessage);
      }

      if (!isEmpty(endDate) && !PipelineEventType.startEvents.contains(pipelineEventType)) {
        webhookNotificationEvent.endTime(endDate);
        webhookNotificationEvent.endTs(endTs);
      }
      if (isNotEmpty(stepIdentifier)) {
        webhookNotificationEvent.stepIdentifier(stepIdentifier);
      }
      if (isNotEmpty(stageIdentifier)) {
        webhookNotificationEvent.stageIdentifier(stageIdentifier);
      }
      templateData.put("WEBHOOK_EVENT_DATA", JsonPipelineUtils.getJsonString(webhookNotificationEvent.build()));
      return templateData;
    }

    private String getStageEventDetails(PipelineEventType pipelineEventType, Ambiance ambiance, String stageIdentifier,
        String orgIdentifier, String projectIdentifier, String accountId) {
      boolean isStageSuccess = pipelineEventType.equals(STAGE_SUCCESS);
      Scope scope = Scope.of(accountId, orgIdentifier, projectIdentifier);
      // for rollbacks we use original node to get module info
      boolean useOriginalNodeToGetModuleInfo = PipelineNotificationUtils.shouldUseOriginalNodeToGetModuleInfo(ambiance);
      String stageExecutionId = useOriginalNodeToGetModuleInfo ? ambiance.getOriginalStageExecutionIdForRollbackMode()
                                                               : AmbianceUtils.getStageRuntimeIdAmbiance(ambiance);

      String planExecutionId = useOriginalNodeToGetModuleInfo
          ? ambiance.getMetadata().getOriginalPlanExecutionIdForRollbackMode()
          : ambiance.getPlanExecutionId();
      // if stage is success we use getCdFinishedFormattedSummary instead of getCdStagePlanCreationFormattedSummary
      Map<String, CDStageSummaryResponseDTO> summaryResponseMap = isStageSuccess
          ? stageMetadataNotificationHelper.getCdFinishedFormattedSummary(
                Set.of(Optional.ofNullable(stageExecutionId).orElse("")), scope)
          : stageMetadataNotificationHelper.getCdStagePlanCreationFormattedSummary(
                planExecutionId, Set.of(Optional.ofNullable(stageIdentifier).orElse("")), scope);

      String identifier = isStageSuccess ? stageExecutionId : stageIdentifier;
      if (!summaryResponseMap.containsKey(identifier)) {
        return "";
      }

      CDStageSummaryResponseDTO summary = summaryResponseMap.get(identifier);
      List<String> parts = new ArrayList<>();

      try {
        addServiceDetails(ambiance, summary, parts, isStageSuccess);
        addEnvironmentDetails(ambiance, summary, parts, isStageSuccess);
        addInfrastructureDetails(ambiance, summary, parts, isStageSuccess);
      } catch (Exception e) {
        log.warn("failed to render svc | env | infra values in notification: {}", e.getMessage());
      }

      return parts.isEmpty() ? "" : "| " + String.join(" | ", parts) + " |";
    }

    private void addServiceDetails(
        Ambiance ambiance, CDStageSummaryResponseDTO summary, List<String> parts, boolean isStageSuccess) {
      if (summary.getService() != null) {
        String serviceId = summary.getService();
        if (!isEmpty(serviceId) && !serviceId.equals(NOT_AVAILABLE)) {
          parts.add(isStageSuccess ? serviceId : pmsEngineExpressionService.renderExpression(ambiance, serviceId));
        }
      } else if (summary.getServices() != null) {
        parts.add(pmsEngineExpressionService.renderExpression(ambiance, SERVICE_REF_EXPRESSION));
      }
    }

    private void addEnvironmentDetails(
        Ambiance ambiance, CDStageSummaryResponseDTO summary, List<String> parts, boolean isStageSuccess) {
      if (summary.getEnvironment() != null) {
        String envId = summary.getEnvironment();
        if (!isEmpty(envId) && !envId.equals(NOT_AVAILABLE)) {
          parts.add(isStageSuccess ? envId : pmsEngineExpressionService.renderExpression(ambiance, envId));
        }
      } else if (summary.getEnvironments() != null) {
        parts.add(pmsEngineExpressionService.renderExpression(ambiance, ENVIRONMENT_REF_EXPRESSION));
      }
    }

    private void addInfrastructureDetails(
        Ambiance ambiance, CDStageSummaryResponseDTO summary, List<String> parts, boolean isStageSuccess) {
      if (summary.getInfra() != null) {
        String infraId = summary.getInfra();
        if (!isEmpty(infraId) && !infraId.equals(NOT_AVAILABLE)) {
          parts.add(isStageSuccess ? infraId : pmsEngineExpressionService.renderExpression(ambiance, infraId));
        }
      } else if (summary.getInfras() != null) {
        parts.add(pmsEngineExpressionService.renderExpression(ambiance, INFRA_IDENTIFIER_EXPRESSION));
      }
    }

    static String getErrorMessage(PipelineEventType pipelineEventType, FailureInfo failureInfo,
        PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity, boolean shouldNotifyAfterGraphUpdate) {
      if (PIPELINE_FAILED == pipelineEventType && null != pipelineExecutionSummaryEntity
          && null != pipelineExecutionSummaryEntity.getFailureInfo()
          && isNotEmpty(pipelineExecutionSummaryEntity.getFailureInfo().getMessage())) {
        String errorMessage = pipelineExecutionSummaryEntity.getFailureInfo().getMessage();
        Set<String> failureErrorMessages = Collections.emptySet();
        if (null != pipelineExecutionSummaryEntity.getFailureInfo()
            && isNotEmpty(pipelineExecutionSummaryEntity.getFailureInfo().getResponseMessages())) {
          failureErrorMessages = pipelineExecutionSummaryEntity.getFailureInfo()
                                     .getResponseMessages()
                                     .stream()
                                     .map(ResponseMessage::getMessage)
                                     .filter(EmptyPredicate::isNotEmpty)
                                     .collect(Collectors.toSet());
        }
        return getErrorMessageInternal(errorMessage, failureErrorMessages);
      }
      if (failureInfo == null || isEmpty(failureInfo.getErrorMessage())) {
        return null;
      }

      if (!shouldNotifyAfterGraphUpdate
          || (shouldNotifyAfterGraphUpdate && !PipelineEventType.startEvents.contains(pipelineEventType))) {
        Set<String> failureErrorMessages = Collections.emptySet();
        if (isNotEmpty(failureInfo.getFailureDataList())) {
          failureErrorMessages = failureInfo.getFailureDataList()
                                     .stream()
                                     .map(FailureData::getMessage)
                                     .filter(EmptyPredicate::isNotEmpty)
                                     .collect(Collectors.toSet());
        }
        String errorMessage = failureInfo.getErrorMessage();
        return getErrorMessageInternal(errorMessage, failureErrorMessages);
      }
      return null;
    }

    private static String getErrorMessageInternal(String errorMessage, Set<String> failureErrorMessages) {
      HashSet<String> errorMessages = new LinkedHashSet<>();

      if (errorMessage != null) {
        errorMessages.add(errorMessage);
      }

      if (failureErrorMessages != null && !failureErrorMessages.isEmpty()) {
        errorMessages.addAll(failureErrorMessages);
      }

      return errorMessages.isEmpty()
          ? ""
          : (errorMessages.size() > 1 ? String.join(", ", errorMessages) : errorMessages.iterator().next());
    }

    @VisibleForTesting
    String getStageIdentifier(NodeExecution nodeExecution) {
      if (nodeExecution == null) {
        return "";
      }
      return getStageIdentifierFromNodeExecution(nodeExecution);
    }

    private String getStageIdentifier(Ambiance ambiance, StepType stepType) {
      String identifier = AmbianceUtils.obtainStepIdentifier(ambiance);
      // Returning identifier of strategy level in case of stages wrapped in looping strategy as their own identifiers
      // (stageId_0, stageId_1, etc..) won't match with the actual stage identifier (stageId) mentioned in notification
      // rules
      if (stepType != null && stepType.getStepCategory() == StepCategory.STAGE) {
        Optional<Level> strategyLevelOptional = AmbianceUtils.getStrategyLevelFromAmbiance(ambiance);
        if (strategyLevelOptional.isPresent()) {
          identifier = strategyLevelOptional.get().getIdentifier();
        }
      }
      return identifier;
    }

    private Optional<String> getStageIdentifierInStrategy(Ambiance ambiance) {
      // Returning identifier of strategy level in case of stages wrapped in looping strategy as their own identifiers
      // (stageId_0, stageId_1, etc..) won't match with the actual stage identifier (stageId) mentioned in notification
      Optional<Level> strategyLevelOptional = AmbianceUtils.getStrategyLevelFromAmbiance(ambiance);
      return strategyLevelOptional.map(Level::getIdentifier);
    }

    @VisibleForTesting
    String computeStepBaseFqn(NodeExecution nodeExecution) {
      if (nodeExecution == null) {
        return null;
      }
      Ambiance ambiance = nodeExecutionService.getAmbiance(nodeExecution);
      if (ambiance == null) {
        return null;
      }
      return computeStepBaseFqnFromAmbiance(ambiance);
    }

    @VisibleForTesting
    String computeStepBaseFqnFromAmbiance(Ambiance ambiance) {
      List<Level> levels = ambiance.getLevelsList();
      List<String> fqnParts = new ArrayList<>();

      for (Level level : levels) {
        if (level.getStepType() == null) {
          continue;
        }
        StepCategory category = level.getStepType().getStepCategory();
        // Include V0 step groups (group STEP_GROUP) and V1 groups (type GROUP / AmbianceUtils#hasStepGroup), fork
        // (parallel) levels are skipped so FQNs align with YAML step paths used in notification rules.
        if (category == StepCategory.STAGE || category == StepCategory.INSERT || AmbianceUtils.hasStepGroup(level)
            || (category == StepCategory.STEP && AmbianceUtils.STEP.equals(level.getGroup()))) {
          fqnParts.add(getBaseIdentifier(level));
        }
      }

      return fqnParts.isEmpty() ? null : String.join(".", fqnParts);
    }

    private String getBaseIdentifier(Level level) {
      String identifier = level.getIdentifier();
      String postfix = null;
      if (level.hasStrategyMetadata() && isNotEmpty(level.getStrategyMetadata().getIdentifierPostFix())) {
        postfix = level.getStrategyMetadata().getIdentifierPostFix();
      } else if (level.hasStrategyInfo() && isNotEmpty(level.getStrategyInfo().getIdentifierPostFix())) {
        postfix = level.getStrategyInfo().getIdentifierPostFix();
      }
      if (postfix != null && identifier.endsWith(postfix)) {
        return identifier.substring(0, identifier.length() - postfix.length());
      }
      return identifier;
    }

    private String getStageIdentifierFromNodeExecution(NodeExecution nodeExecution) {
      StepType stepType = nodeExecution.getStepType();
      String identifier = NodeExecutionContextUtils.obtainStepIdentifier(nodeExecution);
      // Returning identifier of strategy level in case of stages wrapped in looping strategy as their own identifiers
      // (stageId_0, stageId_1, etc..) won't match with the actual stage identifier (stageId) mentioned in notification
      // rules
      if (stepType != null && stepType.getStepCategory() == StepCategory.STAGE) {
        Optional<Level> strategyLevelOptional =
            NodeExecutionContextUtils.getStrategyLevelFromExecutionContext(nodeExecution);
        if (strategyLevelOptional.isPresent()) {
          identifier = strategyLevelOptional.get().getIdentifier();
        }
      }
      return identifier;
    }

    private TriggerExecutionInfo getTriggerExecutionInfo(PipelineExecutionSummaryEntity summaryEntity) {
      return TriggerExecutionInfo.builder()
          .triggerType(summaryEntity.getExecutionTriggerInfo().getTriggerType().toString())
          .name(summaryEntity.getExecutionTriggerInfo().getTriggeredBy().getIdentifier())
          .email(summaryEntity.getExecutionTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email"))
          .build();
    }

    private void markNotificationAsSent(
        String planExecutionId, String nodeExecutionId, PipelineEventType pipelineEventType) {
      notificationEventsHelper.markNotificationAsSent(planExecutionId, nodeExecutionId, pipelineEventType);
    }

    public boolean isEventConfiguredForNode(
        NodeExecution nodeExecution, PipelineEventType pipelineEventType, List<NotificationRules> notificationRules) {
      if (isEmpty(notificationRules)) {
        return false;
      }
      String identifier = getStageIdentifier(nodeExecution);
      String stepBaseFqn = pipelineEventType.isStepLevelEvent() ? computeStepBaseFqn(nodeExecution) : null;
      for (NotificationRules notificationRule : notificationRules) {
        if (!notificationRule.isEnabled()) {
          continue;
        }
        boolean isNotificationConfigured =
            shouldSendNotification(notificationRule.getPipelineEvents(), pipelineEventType, identifier, stepBaseFqn);
        if (isNotificationConfigured) {
          return true;
        }
      }
      return false;
    }

    public void sendNotificationEventWithLock(NodeExecution nodeExecution, String planExecutionId,
        PipelineNotificationEventMeta notificationEventMeta, PipelineEventType pipelineEventType) {
      sendNotificationEventWithLock(nodeExecution, planExecutionId, notificationEventMeta, pipelineEventType,
          notificationEventMeta.getNodeExecutionId());
    }

    public void sendNotificationEventWithLock(NodeExecution nodeExecution, String planExecutionId,
        PipelineNotificationEventMeta notificationEventMeta, PipelineEventType pipelineEventType,
        String dedupeNodeExecutionId) {
      String lockName = NOTIFY_LOCK + planExecutionId + dedupeNodeExecutionId;

      try (AcquiredLock<?> lock =
               persistentLocker.waitToAcquireLock(lockName, Duration.ofSeconds(10), Duration.ofSeconds(20))) {
        if (lock == null) {
          log.warn(String.format(
              "[PMS_NOTIFY_LOCK] Not able to take lock on notifications - %s, returning early.", lockName));
          return;
        }
        // We are checking if the notification is already sent once again. There is a possibility that the
        // notification might have been sent by another thread in the time between checking isNotificationAlreadySent()
        // in PipelineEventNotificationHandler and now
        if (notificationEventsHelper.isNotificationEventAlreadySent(
                planExecutionId, dedupeNodeExecutionId, pipelineEventType)) {
          return;
        }
        sendNotification(notificationEventMeta.getAmbiance(), pipelineEventType, nodeExecution,
            notificationEventMeta.getLastUpdatedAt());
        markNotificationAsSent(planExecutionId, dedupeNodeExecutionId, pipelineEventType);
      }
    }

    public List<NotificationRules> getNotificationRules(NodeExecution nodeExecution, String yaml) {
      List<NotificationRules> notificationRules = null;
      Ambiance ambiance = nodeExecutionService.getAmbiance(nodeExecution);
      try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
        if (pmsFeatureFlagHelper.isEnabled(
                AmbianceUtils.getAccountId(ambiance), FeatureName.PIPE_FILTER_NOTIFICATION_RULES_BY_EVENT_TYPE)) {
          notificationRules = getUnresolvedNotificationRulesFromYaml(yaml, ambiance);
        } else {
          notificationRules = getNotificationRulesFromYaml(yaml, ambiance);
        }
      } catch (Exception exception) {
        log.error("Unable to parse yaml to get notification objects", exception);
      }
      return notificationRules;
    }

    public void sendNotification(Ambiance ambiance, PipelineEventType pipelineEventType,
        PipelineNotificationEventMeta notificationEventMeta, List<NotificationRules> notificationRules,
        Long updatedAt) {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      PlanExecutionMetadata planExecutionMetadata =
          getPlanExecutionMetadata(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
      String yaml = planExecutionMetadata.getYaml();

      try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
        Map<String, String> notificationContent = constructTemplateData(ambiance, pipelineEventType, updatedAt, orgId,
            projectId, notificationEventMeta.getName(), notificationEventMeta.getStatus(),
            notificationEventMeta.getFailureInfo(), notificationEventMeta.getStartedAt(),
            notificationEventMeta.getNodeExecutionId());
        String stepFqn = pipelineEventType.isStepLevelEvent()
            ? computeStepBaseFqnFromAmbiance(notificationEventMeta.getAmbiance())
            : null;
        sendNotificationInternal(NotificationContext.builder()
                                     .notificationRulesList(notificationRules)
                                     .pipelineEventType(pipelineEventType)
                                     .identifier(getStageIdentifier(
                                         notificationEventMeta.getAmbiance(), notificationEventMeta.getStepType()))
                                     .stepBaseFqn(stepFqn)
                                     .accountIdentifier(accountId)
                                     .notificationContent(notificationContent)
                                     .orgIdentifier(orgId)
                                     .projectIdentifier(projectId)
                                     .notifyOnlyMe(false)
                                     .yaml(yaml)
                                     .build(),
            ambiance, null);
      } catch (Exception ex) {
        log.error("Exception occurred in sendNotificationInternal", ex);
      }
    }

    private String resolveNotificationTemplate(Ambiance ambiance, String accountIdentifier, String orgIdentifier,
        String projectIdentifier, String notificationTemplateNodeAsString, Map<String, String> inputVariableMap,
        PipelineEventType pipelineEventType, TriggerNotificationData triggerNotificationData, boolean isResolved,
        Map<String, String> notificationMapData) {
      String mergedTemplateYaml = notificationTemplateNodeAsString;
      if (!isResolved) {
        // Inverted FF: When PIPE_DISABLE_NOTIFICATION_SECURITY_CONTEXT_FIX is OFF (default),
        // use PmsSecurityContextNoSideEffectsGuard to temporarily set the SecurityContext from
        // Ambiance before calling template-service, and automatically restore the original context
        // afterward. This prevents "Missing principal in context" errors from access-control.
        if (!pmsFeatureFlagHelper.isEnabled(
                accountIdentifier, FeatureName.PIPE_DISABLE_NOTIFICATION_SECURITY_CONTEXT_FIX)) {
          log.info("[NotificationHelper] resolveNotificationTemplate: SecurityContext principal before guard: [{}], "
                  + "account: [{}], org: [{}], project: [{}], thread: [{}]",
              SecurityContextBuilder.getPrincipal(), accountIdentifier, orgIdentifier, projectIdentifier,
              Thread.currentThread().getName());
          try (PmsSecurityContextNoSideEffectsGuard ignore = new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
            log.info("[NotificationHelper] resolveNotificationTemplate: SecurityContext principal inside guard: [{}], "
                    + "account: [{}], org: [{}], project: [{}], thread: [{}]",
                SecurityContextBuilder.getPrincipal(), accountIdentifier, orgIdentifier, projectIdentifier,
                Thread.currentThread().getName());
            TemplateMergeResponseDTO templateMergeResponseDTO = pipelineTemplateHelper.resolveTemplateRefsInPipeline(
                accountIdentifier, orgIdentifier, projectIdentifier, notificationTemplateNodeAsString, true, false,
                BOOLEAN_FALSE_VALUE, AmbianceUtils.getPipelineVersion(ambiance), true);
            mergedTemplateYaml = templateMergeResponseDTO.getMergedPipelineYaml();
          } catch (RuntimeException ex) {
            // Re-throw unchecked exceptions (e.g. from resolveTemplateRefsInPipeline) to preserve
            // original behavior. The guard's close() still runs via try-with-resources before this.
            log.error(
                "[NotificationHelper] resolveNotificationTemplate: RuntimeException while resolving template refs, "
                    + "account: [{}], org: [{}], project: [{}]",
                accountIdentifier, orgIdentifier, projectIdentifier, ex);
            throw ex;
          } catch (Exception ex) {
            // Only checked exceptions reach here (e.g. from AutoCloseable.close()).
            // The guard's close() just restores context, so this is extremely unlikely.
            log.error("[NotificationHelper] resolveNotificationTemplate: Error closing security context guard, "
                    + "account: [{}], org: [{}], project: [{}]",
                accountIdentifier, orgIdentifier, projectIdentifier, ex);
          }
        } else {
          TemplateMergeResponseDTO templateMergeResponseDTO = pipelineTemplateHelper.resolveTemplateRefsInPipeline(
              accountIdentifier, orgIdentifier, projectIdentifier, notificationTemplateNodeAsString, true, false,
              BOOLEAN_FALSE_VALUE, AmbianceUtils.getPipelineVersion(ambiance), true);
          mergedTemplateYaml = templateMergeResponseDTO.getMergedPipelineYaml();
        }
      }
      // Create the context map
      Map<String, Object> contextMap =
          createContextMapForResolution(accountIdentifier, pipelineEventType, notificationMapData);

      // Add fixed variables in the template to the context map
      if (mergedTemplateYaml != null && !isResolved) {
        contextMap.put(VARIABLES, buildContextMapForVariables(mergedTemplateYaml, inputVariableMap));
      } else {
        contextMap.put(VARIABLES, inputVariableMap);
      }
      // resolve the template
      String resolvedBody = null;
      String content = null;
      // extract the content and pass only the content to expression engine - This avoids the issues related to the yaml
      // parsing issues after the expressions were resolved. Also, this will align the behaviour of resolution from the
      // CNS flow, where the expression engine receives and returns only the content
      try {
        JsonNode templateYamlToJson = convertTemplateMergedYamlToJson(mergedTemplateYaml);
        if (templateYamlToJson != null && templateYamlToJson.get(PIPELINE) != null
            && templateYamlToJson.get(PIPELINE).get(NOTIFICATION_RULES_V0) != null
            && templateYamlToJson.get(PIPELINE).get(NOTIFICATION_RULES_V0).get(NOTIFICATION_BODY) != null
            && templateYamlToJson.get(PIPELINE)
                    .get(NOTIFICATION_RULES_V0)
                    .get(NOTIFICATION_BODY)
                    .get(NOTIFICATION_CONTENT)
                != null) {
          content = templateYamlToJson.get(PIPELINE)
                        .get(NOTIFICATION_RULES_V0)
                        .get(NOTIFICATION_BODY)
                        .get(NOTIFICATION_CONTENT)
                        .asText();
        } else if (templateYamlToJson != null && templateYamlToJson.get(NOTIFICATION_BODY) != null
            && templateYamlToJson.get(NOTIFICATION_BODY).get(NOTIFICATION_CONTENT) != null) {
          content = templateYamlToJson.get(NOTIFICATION_BODY).get(NOTIFICATION_CONTENT).asText();
        }
      } catch (Exception ex) {
        log.error("[NotificationHelper]: Failed to parse or extract notification content from template merged yaml. "
                + "Skipping custom notification content resolution. Exception:",
            ex);
        return resolvedBody;
        // Continue execution - will send notification without custom resolved content
      } catch (Error err) {
        log.error("[NotificationHelper]: Failed to parse or extract notification content from template merged yaml. "
                + "Skipping custom notification content resolution. Error:",
            err);
        // Continue execution - will send notification without custom resolved content
        return resolvedBody;
      }
      if (content == null) {
        return resolvedBody;
      }
      if (TRIGGER_FAILED.equals(pipelineEventType) && triggerNotificationData != null) {
        TriggerExpressionEvaluator triggerExpressionEvaluator =
            new TriggerExpressionEvaluator(triggerNotificationData, contextMap);
        resolvedBody = (String) triggerExpressionEvaluator.resolve(content, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
      } else {
        resolvedBody = (String) pmsEngineExpressionService.resolve(
            ambiance, content, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, contextMap);
      }
      if (resolvedBody != null) {
        resolvedBody = resolvedBody.replaceAll("\"null\"", "\"\"");
        // TAB characters can be introduced via expression resolution (e.g., from error messages)
        resolvedBody = YamlPipelineUtils.sanitiseYamlForParsing(resolvedBody);
      }
      return resolvedBody;
    }

    private static JsonNode convertTemplateMergedYamlToJson(String yamlContent) {
      try {
        return simpleYamlMapper.readTree(yamlContent);
      } catch (Exception e) {
        log.error("Failed to convert YAML to JSON", e);
        return null;
      }
    }

    private Map<String, Object> createContextMapForResolution(
        String accountIdentifier, PipelineEventType pipelineEventType, Map<String, String> notificationMapData) {
      Map<String, Object> contextMap = new HashMap<>();
      contextMap.put(NOTIFICATION_EVENT_TYPE_EXPRESSION_KEY, pipelineEventType.name());
      contextMap.put(NOTIFICATION_EVENT_DETAILS_EXPRESSION_KEY,
          notificationMapData.getOrDefault(EVENT_DETAILS, pipelineEventType.name()));
      if (pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.CDS_RESOLVE_OBJECTS_VIA_JSON_SELECT)) {
        contextMap.put(RESOLVE_OBJECTS_VIA_JSON_SELECT, TRUE);
      }
      if (!notificationMapData.isEmpty()) {
        contextMap.put(NOTIFICATION_NODE_START_DATE_EXPRESSION_KEY,
            notificationMapData.getOrDefault(NOTIFICATION_NODE_START_DATE, ""));
        contextMap.put(
            NOTIFICATION_DURATION_EXPRESSION_KEY, notificationMapData.getOrDefault(NOTIFICATION_DURATION, ""));
        contextMap.put(NOTIFICATION_NODE_END_DATE_EXPRESSION_KEY,
            notificationMapData.getOrDefault(NOTIFICATION_NODE_END_DATE, ""));
        contextMap.put(
            NOTIFICATION_THEME_COLOR_EXPRESSION_KEY, notificationMapData.getOrDefault(NOTIFICATION_THEME_COLOR, ""));
        contextMap.put(
            NOTIFICATION_IMAGE_STATUS_EXPRESSION_KEY, notificationMapData.getOrDefault(NOTIFICATION_IMAGE_STATUS, ""));
        contextMap.put(
            NOTIFICATION_NODE_STATUS_EXPRESSION_KEY, notificationMapData.getOrDefault(NOTIFICATION_NODE_STATUS, ""));
      }
      return contextMap;
    }
    private Map<String, String> buildContextMapForVariables(
        String mergedTemplateYaml, Map<String, String> inputVariableMap) {
      JsonNode mergedTemplateYamlNode = YamlPipelineUtils.readAsJsonNode(mergedTemplateYaml);
      if (mergedTemplateYamlNode != null && mergedTemplateYamlNode.get(PIPELINE) != null
          && mergedTemplateYamlNode.get(PIPELINE).get(NOTIFICATION_RULES_V0) != null
          && mergedTemplateYamlNode.get(PIPELINE).get(NOTIFICATION_RULES_V0).get(VARIABLES) != null) {
        JsonNode variablesNode = mergedTemplateYamlNode.get(PIPELINE).get(NOTIFICATION_RULES_V0).get(VARIABLES);
        variablesNode.forEach(node -> inputVariableMap.put(node.path("name").asText(), node.path("value").asText()));
      }
      return inputVariableMap;
    }

    private String processNotificationTemplateResolution(JsonNode rootNode, Ambiance ambiance, String accountIdentifier,
        String orgIdentifier, String projectIdentifier, String notificationName, PipelineEventType pipelineEventType,
        TriggerNotificationData triggerNotificationData, Map<String, String> notificationMapData) {
      if (rootNode == null || !rootNode.has(PIPELINE)) {
        return null;
      }
      JsonNode pipelineNode = rootNode.path(PIPELINE);
      JsonNode notificationRulesNode;
      JsonNode notificationBodyNode;
      if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
        notificationRulesNode = pipelineNode.path(NOTIFICATIONS);
        notificationBodyNode =
            findNotificationPathByRuleAndName(notificationRulesNode, notificationName, NOTIFICATION_CONTENT);
      } else {
        notificationRulesNode = pipelineNode.path(NOTIFICATION_RULES_V0);
      }
      JsonNode templateNode = findNotificationPathByRuleAndName(notificationRulesNode, notificationName, TEMPLATE);

      // Convert templateNode from v1 to v0 format if it exists
      JsonNode v0TemplateNode = templateNode;
      if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion()) && templateNode != null
          && !templateNode.isNull()) {
        try {
          io.harness.template.yaml.TemplateLinkConfig v0Template =
              notificationRulesMapper.convertTemplateNodeV1ToV0(templateNode);
          if (v0Template != null) {
            // Convert v0Template back to JsonNode and wrap it in the expected structure
            JsonNode convertedTemplateNode = JsonPipelineUtils.asTree(v0Template);
            // Need to wrap the converted template back into the notification rule structure
            // by replacing the template field in the original rule node
            ObjectNode mutableRuleNode = templateNode.deepCopy();
            mutableRuleNode.set(TEMPLATE, convertedTemplateNode);
            v0TemplateNode = mutableRuleNode;
            log.info("[DEBUG]: [NotificationHelper]: Converted template from v1 to v0 format for notification: {}",
                notificationName);
          }
        } catch (Exception e) {
          log.warn("[NotificationHelper]: Failed to convert template from v1 to v0 format, using original", e);
          // Fall back to original templateNode (already set at line 1181: v0TemplateNode = templateNode)
        }
      }

      // This will be not null notificationBodyNode when FF is enabled - v1 behavior needs to be defined
      notificationBodyNode =
          findNotificationPathByRuleAndName(notificationRulesNode, notificationName, NOTIFICATION_BODY);

      log.info("[DEBUG]: [NotificationHelper]: TemplateNode fetched for notification: {}", v0TemplateNode);
      if (v0TemplateNode != null) {
        return resolveTemplate(ambiance, accountIdentifier, orgIdentifier, projectIdentifier, v0TemplateNode,
            pipelineEventType, triggerNotificationData, notificationMapData);
      } else if (notificationBodyNode != null) {
        Map<String, String> inputVariableMap = new HashMap<>();
        if (notificationBodyNode.get(VARIABLES) != null) {
          JsonNode variablesNode = notificationBodyNode.get(VARIABLES);
          variablesNode.forEach(node -> inputVariableMap.put(node.path(NAME).asText(), node.path(VALUE).asText()));
        }
        return resolveNotificationTemplate(ambiance, accountIdentifier, orgIdentifier, projectIdentifier,
            YamlUtils.writeYamlString(notificationBodyNode), inputVariableMap, pipelineEventType,
            triggerNotificationData, true, notificationMapData);
      }
      return null;
    }

    private JsonNode findNotificationPathByRuleAndName(
        JsonNode notificationRulesNode, String notificationName, String fieldName) {
      for (JsonNode node : notificationRulesNode) {
        if (node.has(fieldName) && notificationName.equals(node.path(NAME).asText())) {
          return node;
        }
      }
      return null;
    }

    private String resolveTemplate(Ambiance ambiance, String accountIdentifier, String orgIdentifier,
        String projectIdentifier, JsonNode templateNode, PipelineEventType pipelineEventType,
        TriggerNotificationData triggerNotificationData, Map<String, String> notificationMapData) {
      // Set the 'notificationRules' node into the new ObjectNode under the key 'notificationRules'
      ObjectNode notificationObjectNode =
          YamlPipelineUtils.createEmptyObjectNode().set(NOTIFICATION_RULES_V0, templateNode);

      /*
       * TODO: The following workaround is required due to a known issue.
       * The method `TemplateRefHelper.hasTemplateRefWithCheckDuplicate(yamlVersion, yaml)`
       * does not currently handle arrays for a parent node. This handling is only for child nodes. This logic needs to
       * be updated in the future. For now, we are adding a parent pipeline node on top of the notification node to
       * address this limitation.
       */
      ObjectNode parentPipelineNode = YamlPipelineUtils.createEmptyObjectNode().set(PIPELINE, notificationObjectNode);
      Map<String, String> inputVariableMap = extractVariablesFromNotificationTemplate(templateNode.get(TEMPLATE));
      return resolveNotificationTemplate(ambiance, accountIdentifier, orgIdentifier, projectIdentifier,
          YamlPipelineUtils.writeYamlString(parentPipelineNode), inputVariableMap, pipelineEventType,
          triggerNotificationData, false, notificationMapData);
    }

    private Map<String, String> extractVariablesFromNotificationTemplate(JsonNode templateNode) {
      Map<String, String> resultMap = new HashMap<>();
      if (templateNode.get(TEMPLATE_INPUTS) != null && templateNode.get(TEMPLATE_INPUTS).get(VARIABLES) != null) {
        JsonNode variablesNode = templateNode.path(TEMPLATE_INPUTS).path(VARIABLES);
        variablesNode.forEach(node -> resultMap.put(node.path(NAME).asText(), node.path(VALUE).asText()));
      }
      return resultMap;
    }

    public ArrayList<Map<String, String>> listNotificationRulesWithUnresolvedInputs(String yaml) {
      ArrayList<Map<String, String>> rulesNeedingConfiguration = new ArrayList<>();
      JsonNode notificationRules = extractNotificationNodeFromYaml(yaml);
      if (isEmpty(notificationRules)) {
        return null;
      }
      for (JsonNode rule : notificationRules) {
        JsonNode templateNode = rule.path(TEMPLATE);
        if (templateNode.isMissingNode() || templateNode.path(TEMPLATE_INPUTS).isMissingNode()) {
          continue;
        }
        JsonNode variables = templateNode.path(TEMPLATE_INPUTS).path(VARIABLES);
        if (variables == null || !variables.isArray()) {
          continue;
        }
        Map<String, String> response = notificationRulesNeedConfigureInternal(rule, variables);
        if (isNotEmpty(response)) {
          rulesNeedingConfiguration.add(response);
        }
      }
      return rulesNeedingConfiguration;
    }

    public Boolean validateNotificationRulesWithUnresolvedInputs(String yaml) {
      JsonNode notificationRules = extractNotificationNodeFromYaml(yaml);
      if (isEmpty(notificationRules)) {
        return false;
      }
      boolean isInvalid = false;
      for (JsonNode rule : notificationRules) {
        JsonNode templateNode = rule.path(TEMPLATE);
        if (templateNode.isMissingNode() || templateNode.path(TEMPLATE_INPUTS).isMissingNode()) {
          continue;
        }
        JsonNode variables = templateNode.path(TEMPLATE_INPUTS).path(VARIABLES);
        if (variables == null || !variables.isArray()) {
          continue;
        }
        Map<String, String> response = notificationRulesNeedConfigureInternal(rule, variables);
        if (isNotEmpty(response)) {
          isInvalid = true;
          break;
        }
      }
      return isInvalid;
    }

    private Map<String, String> notificationRulesNeedConfigureInternal(JsonNode rule, JsonNode variables) {
      Map<String, String> response = new HashMap<>();
      boolean hasRuntimeInput =
          StreamSupport.stream(variables.spliterator(), false)
              .anyMatch(var
                  -> var.has(YAMLFieldNameConstants.VALUE)
                      && YAMLFieldNameConstants.RUNTIME_INPUT.equals(var.path(YAMLFieldNameConstants.VALUE).asText()));
      if (hasRuntimeInput) {
        response.put(NOTIFICATION_IDENTIFIER, rule.path(YAMLFieldNameConstants.IDENTIFIER).asText());
      }
      return response;
    }

    private JsonNode extractNotificationNodeFromYaml(String yaml) {
      JsonNode root;
      try {
        root = YamlPipelineUtils.readAsJsonNode(yaml);
      } catch (Exception ex) {
        log.warn("Error in converting yaml to JsonNode: ", ex);
        return null;
      }
      if (root == null || !root.has(PIPELINE)) {
        return null;
      }
      return root.path(PIPELINE).path(NOTIFICATION_RULES_V0);
    }
  }

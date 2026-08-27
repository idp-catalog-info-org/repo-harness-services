/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.notification.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.logging.AutoLogContext;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData.TriggerNotificationDataBuilder;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.service.TriggerFailureNotificationDetailsService;
import io.harness.notification.NotificationConstants;
import io.harness.notification.PipelineEventType;
import io.harness.notification.bean.NotificationRules;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.helpers.TriggerUrlHelper;
import io.harness.pms.notification.PipelineNotificationConstants;
import io.harness.pms.notification.PipelineNotificationUtils;
import io.harness.pms.notification.TriggerWebhookNotificationEvent;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.utils.ExecutionHelperUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class TriggerFailureNotificationHelper {
  @Inject NotificationHelper notificationHelper;
  @Inject PMSPipelineService pmsPipelineService;
  @Inject TriggerUrlHelper triggerUrlHelper;
  @Inject ExecutionHelperUtils executionHelperUtils;
  @Inject PmsFeatureFlagHelper featureFlagHelper;
  @Inject ScopeResolutionHelper scopeResolutionHelper;
  @Inject TriggerFailureNotificationDetailsService triggerFailureNotificationDetailsService;

  public void sendTriggerNotification(TriggerEventHistory triggerEventHistory,
      TriggerEventResponse.FinalStatus finalStatus, TriggerNotificationDataBuilder triggerNotificationDataBuilder) {
    if (TriggerEventResponse.shouldSendFailureNotificationForStatus(finalStatus)
        && featureFlagHelper.isEnabled(
            triggerEventHistory.getAccountId(), FeatureName.PIPE_ENABLE_TRIGGER_FAILED_NOTIFICATION)) {
      sendTriggerNotification(
          buildTriggerNotificationDataWithTriggerHistoryDetails(triggerNotificationDataBuilder, triggerEventHistory));
    }
  }

  private void sendTriggerNotification(TriggerNotificationData triggerNotificationData) {
    try {
      PipelineEntity pipelineEntity;
      try {
        boolean isParentIdQueryingEnabled = true;
        ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(
            triggerNotificationData.getAccountIdentifier(), triggerNotificationData.getParentUniqueId());

        pipelineEntity = pmsPipelineService.getPipelineMetadata(triggerNotificationData.getAccountIdentifier(),
            triggerNotificationData.getOrgIdentifier(), triggerNotificationData.getProjectIdentifier(),
            triggerNotificationData.getPipelineIdentifier(), false, false, scopeInfo, isParentIdQueryingEnabled);
      } catch (Exception e) {
        log.error("Unable to find pipeline entity while sending notification for trigger failure", e);
        return;
      }

      triggerNotificationData.setPipelineName(pipelineEntity.getName());
      triggerFailureNotificationDetailsService.saveRecord(triggerNotificationData);
      Map<String, String> notificationData = constructTemplateData(triggerNotificationData, pipelineEntity);

      // TODO : add DB call to persist triggerFailureNotificationDetailsEntity
      notificationHelper.sendCentralisedNotificationAndHandleError(triggerNotificationData.getAccountIdentifier(),
          triggerNotificationData.getOrgIdentifier(), triggerNotificationData.getProjectIdentifier(),
          triggerNotificationData.getPipelineIdentifier(), PipelineEventType.TRIGGER_FAILED, notificationData,
          triggerNotificationData.getParentUniqueId());

      Ambiance ambiance = createAmbianceForTriggerNotification(triggerNotificationData, pipelineEntity);

      String yaml = pipelineEntity.getYaml();
      List<NotificationRules> notificationRules = null;
      try {
        notificationRules = notificationHelper.getNotificationRulesForEvent(
            yaml, ambiance, PipelineEventType.TRIGGER_FAILED, triggerNotificationData.getPipelineIdentifier(), null);
      } catch (Exception e) {
        log.error("Unable to parse yaml to get notification objects", e);
      }

      if (isEmpty(notificationRules)) {
        return;
      }

      try (AutoLogContext ignore1 = AmbianceUtils.autoLogContext(ambiance);
           AutoLogContext ignore2 = new NotificationLogContext(PipelineEventType.TRIGGER_FAILED.name())) {
        notificationHelper.sendTriggerFailureNotification(
            NotificationContext.builder()
                .notificationRulesList(notificationRules)
                .pipelineEventType(PipelineEventType.TRIGGER_FAILED)
                .identifier(triggerNotificationData.getPipelineIdentifier())
                .accountIdentifier(triggerNotificationData.getAccountIdentifier())
                .notificationContent(notificationData)
                .orgIdentifier(triggerNotificationData.getOrgIdentifier())
                .projectIdentifier(triggerNotificationData.getProjectIdentifier())
                .notifyOnlyMe(false)
                .yaml(yaml)
                .build(),
            ambiance, triggerNotificationData);

      } catch (Exception ex) {
        log.error("Exception occurred in sendNotificationInternal during trigger failure notification", ex);
      }
    } catch (Exception e) {
      log.error("Exception occurred in sendTriggerNotification during trigger failure notification", e);
    }
  }

  private Map<String, String> constructTemplateData(
      TriggerNotificationData triggerNotificationData, PipelineEntity pipelineEntity) {
    String accountIdentifier = triggerNotificationData.getAccountIdentifier();
    String orgIdentifier = triggerNotificationData.getOrgIdentifier();
    String projectIdentifier = triggerNotificationData.getProjectIdentifier();
    String pipelineIdentifier = triggerNotificationData.getPipelineIdentifier();
    String triggerIdentifier = triggerNotificationData.getTriggerIdentifier();

    String pipelineUrl = triggerUrlHelper.generatePipelineStudioUrl(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);

    String triggerUrl = triggerUrlHelper.generateTriggerUrl(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, triggerIdentifier);

    String triggerActivityUrl = triggerUrlHelper.generateTriggerActivityHistoryUrl(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, triggerIdentifier);

    TriggerWebhookNotificationEvent triggerWebhookNotificationEvent =
        TriggerWebhookNotificationEvent.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .pipelineIdentifier(pipelineIdentifier)
            .pipelineName(pipelineEntity.getName())
            .triggerName(triggerNotificationData.getTriggerName())
            .triggerIdentifier(triggerIdentifier)
            .eventType(PipelineEventType.TRIGGER_FAILED)
            .pipelineUrl(pipelineUrl)
            .triggerActivityUrl(triggerActivityUrl)
            .triggerUrl(triggerUrl)
            .startTime(new Date(triggerNotificationData.getTriggerEventCreatedAt()).toString())
            .startTs(triggerNotificationData.getTriggerEventCreatedAt())
            .errorMessage(triggerNotificationData.getErrorMessage())
            .triggerType(triggerNotificationData.getNgTriggerType())
            .triggerSubType(triggerNotificationData.getTriggerSubType())
            .build();

    Map<String, String> templateData = new HashMap<>();
    templateData.put("OUTER_DIV", PipelineNotificationConstants.OUTER_DIV);
    templateData.put("COLOR", PipelineNotificationUtils.getThemeColor(Status.FAILED));
    templateData.put("PIPELINE", pipelineIdentifier);
    templateData.put("PIPELINE_URL", pipelineUrl);
    templateData.put("ORG", orgIdentifier);
    templateData.put("PROJECT", projectIdentifier);
    templateData.put("EVENT_TYPE", PipelineEventType.TRIGGER_FAILED.getDisplayName());
    templateData.put("TRIGGER", triggerIdentifier);
    templateData.put("TRIGGER_URL", triggerUrl);
    templateData.put("TRIGGER_ACTIVITY_URL", triggerActivityUrl);
    templateData.put("ERROR", triggerNotificationData.getErrorMessage());
    templateData.put("START_DATE", triggerWebhookNotificationEvent.getStartTime());
    templateData.put("WEBHOOK_EVENT_DATA", JsonPipelineUtils.getJsonString(triggerWebhookNotificationEvent));
    templateData.put("IMAGE_STATUS", PipelineNotificationUtils.getStatusForImage(Status.FAILED));
    templateData.put(NotificationConstants.TRIGGER_NOTIFICATION_DETAILS_ID_KEY,
        triggerNotificationData.getTriggerFailureNotificationEntityUuid());
    return templateData;
  }

  public Ambiance createAmbianceForTriggerNotification(
      TriggerNotificationData triggerNotificationData, PipelineEntity pipelineEntity) {
    ExecutionMetadata.Builder executionMetadataBuilder =
        ExecutionMetadata.newBuilder()
            .setHarnessVersion(pipelineEntity.getHarnessVersion())
            .setPipelineIdentifier(triggerNotificationData.getPipelineIdentifier());

    executionHelperUtils.updateFeatureFlagsInExecutionMetadataBuilder(
        triggerNotificationData.getAccountIdentifier(), executionMetadataBuilder);

    Map<String, String> abstractions = new HashMap<>();
    abstractions.put(SetupAbstractionKeys.accountId, triggerNotificationData.getAccountIdentifier());
    abstractions.put(SetupAbstractionKeys.orgIdentifier, triggerNotificationData.getOrgIdentifier());
    abstractions.put(SetupAbstractionKeys.projectIdentifier, triggerNotificationData.getProjectIdentifier());
    abstractions.put(SetupAbstractionKeys.pipelineIdentifier, triggerNotificationData.getPipelineIdentifier());

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(abstractions)
        .setMetadata(executionMetadataBuilder.build())
        .setExpressionFunctorToken(0)
        .build();
  }

  public TriggerNotificationData buildTriggerNotificationDataWithTriggerHistoryDetails(
      TriggerNotificationDataBuilder triggerNotificationDataBuilder, TriggerEventHistory triggerEventHistory) {
    triggerNotificationDataBuilder.accountIdentifier(triggerEventHistory.getAccountId())
        .orgIdentifier(triggerEventHistory.getOrgIdentifier())
        .projectIdentifier(triggerEventHistory.getProjectIdentifier())
        .parentUniqueId(triggerEventHistory.getParentUniqueId())
        .triggerName(triggerEventHistory.getTriggerName())
        .triggerIdentifier(triggerEventHistory.getTriggerIdentifier())
        .eventCorrelationId(triggerEventHistory.getEventCorrelationId())
        .pipelineIdentifier(triggerEventHistory.getTargetIdentifier())
        .errorMessage(triggerEventHistory.getMessage())
        .triggerEventCreatedAt(triggerEventHistory.getEventCreatedAt())
        .ngTriggerType(triggerEventHistory.getNgTriggerType())
        .triggerSubType(triggerEventHistory.getTriggerSubType());

    TriggerNotificationData triggerNotificationData = triggerNotificationDataBuilder.build();
    if (isEmpty(triggerNotificationData.getTriggerIdentifier())) {
      triggerNotificationData.setTriggerPayload(TriggerPayload.newBuilder().build());
    }

    if (isEmpty(triggerNotificationData.getHeaderConfigs())) {
      triggerNotificationData.setHeaderConfigs(Collections.emptyList());
    }

    if (isEmpty(triggerNotificationData.getPayload())) {
      triggerNotificationData.setPayload("{}");
    }

    return triggerNotificationData;
  }
}

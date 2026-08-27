/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.constants.Constants.UNRECOGNIZED_WEBHOOK;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.dto.NGProcessWebhookResponseDTO;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails;
import io.harness.ngtriggers.beans.dto.WebhookExecutionDetails;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEventStatus;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.webhook.WebhookAction;
import io.harness.ngtriggers.beans.source.webhook.WebhookEvent;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabTriggerEvent;
import io.harness.ngtriggers.helpers.UrlHelper;
import io.harness.ngtriggers.helpers.WebhookConfigHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.validations.TriggerWebhookValidator;
import io.harness.pms.annotations.PipelineServiceAuthIfHasApiKey;
import io.harness.pms.annotations.PipelineServiceAuthIfHasAuthHeader;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.SizeValidatorUtils;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.HttpHeaders;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class NGTriggerWebhookConfigResourceImpl implements NGTriggerWebhookConfigResource {
  private final NGTriggerService ngTriggerService;
  private final NGTriggerElementMapper ngTriggerElementMapper;
  private final TriggerWebhookValidator triggerWebhookValidator;
  @Inject private UrlHelper urlHelper;
  private final TriggerEventHistoryRepository triggerEventHistoryRepository;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private PipelineSettingsService pipelineSettingsService;
  @Inject private PipelineRetentionService pipelineRetentionService;

  public ResponseDTO<Map<WebhookSourceRepo, List<WebhookEvent>>> getSourceRepoToEvent() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getSourceRepoToEvent());
  }

  public ResponseDTO<Map<String, Map<String, List<String>>>> getGitTriggerEventDetails() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getGitTriggerEventDetails());
  }

  public ResponseDTO<List<WebhookTriggerType>> getWebhookTriggerTypes() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getWebhookTriggerType());
  }

  public ResponseDTO<List<GithubTriggerEvent>> getGithubTriggerEvents() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getGithubTriggerEvents());
  }

  public ResponseDTO<List<GithubPRAction>> getGithubPRActions() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getGithubPRAction());
  }

  public ResponseDTO<List<GithubIssueCommentAction>> getGithubIssueCommentActions() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getGithubIssueCommentAction());
  }

  public ResponseDTO<List<GitlabTriggerEvent>> getGitlabTriggerEvents() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getGitlabTriggerEvents());
  }

  public ResponseDTO<List<GitlabPRAction>> getGitlabTriggerActions() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getGitlabPRAction());
  }

  public ResponseDTO<List<BitbucketTriggerEvent>> getBitbucketTriggerEvents() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getBitbucketTriggerEvents());
  }

  public ResponseDTO<List<BitbucketPRAction>> getBitbucketPRActions() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getBitbucketPRAction());
  }

  public ResponseDTO<List<WebhookAction>> getActionsList(@NotNull WebhookSourceRepo sourceRepo, @NotNull String event) {
    WebhookEvent webhookEvent;
    try {
      webhookEvent = YamlPipelineUtils.read(event, WebhookEvent.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Event: " + event + " is not valid");
    }
    return ResponseDTO.newResponse(WebhookConfigHelper.getActionsList(sourceRepo, webhookEvent));
  }

  public ResponseDTO<String> processWebhookEvent(@NotNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull String eventPayload, HttpHeaders httpHeaders) {
    List<HeaderConfig> headerConfigs = new ArrayList<>();
    httpHeaders.getRequestHeaders().forEach(
        (k, v) -> headerConfigs.add(HeaderConfig.builder().key(k).values(v).build()));

    TriggerWebhookEvent eventEntity = ngTriggerElementMapper
                                          .toNGTriggerWebhookEvent(accountIdentifier, orgIdentifier, projectIdentifier,
                                              eventPayload, headerConfigs, null)
                                          .build();
    if (eventEntity != null) {
      TriggerWebhookEvent newEvent = ngTriggerService.addEventToQueue(eventEntity);
      return ResponseDTO.newResponse(newEvent.getUuid());
    } else {
      return ResponseDTO.newResponse(UNRECOGNIZED_WEBHOOK);
    }
  }
  @PipelineServiceAuthIfHasApiKey
  public ResponseDTO<String> processWebhookEvent(@NotNull String accountIdentifier, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, String pipelineIdentifier, String triggerIdentifier,
      @NotNull String eventPayload, HttpHeaders httpHeaders) {
    validateScopeIdentifiers(accountIdentifier, orgIdentifier, projectIdentifier);
    SizeValidatorUtils.validate(eventPayload, "CUSTOM_WEBHOOK_TRIGGER_PAYLOAD_SIZE");
    recordMaxPayloadSizeForAccount(accountIdentifier, eventPayload);
    List<HeaderConfig> headerConfigs = new ArrayList<>();
    httpHeaders.getRequestHeaders().forEach(
        (k, v) -> headerConfigs.add(HeaderConfig.builder().key(k).values(v).build()));
    ngTriggerService.checkAuthorization(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, headerConfigs);
    TriggerWebhookEvent eventEntity =
        ngTriggerElementMapper
            .toNGTriggerWebhookEventForCustom(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier,
                triggerIdentifier, eventPayload, headerConfigs)
            .build();
    if (eventEntity != null) {
      triggerWebhookValidator.applyValidationsForCustomWebhook(eventEntity, null);
      TriggerWebhookEvent newEvent = ngTriggerService.addEventToQueue(eventEntity);
      saveEventInTriggerEventHistory(accountIdentifier, eventEntity, newEvent.getUuid());
      return ResponseDTO.newResponse(newEvent.getUuid());
    } else {
      return ResponseDTO.newResponse(UNRECOGNIZED_WEBHOOK);
    }
  }
  @PipelineServiceAuthIfHasApiKey
  public ResponseDTO<NGProcessWebhookResponseDTO> processWebhookEventV2(@NotNull String accountIdentifier,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, String pipelineIdentifier,
      String triggerIdentifier, @NotNull String eventPayload, HttpHeaders httpHeaders) {
    validateScopeIdentifiers(accountIdentifier, orgIdentifier, projectIdentifier);
    SizeValidatorUtils.validate(eventPayload, "CUSTOM_WEBHOOK_TRIGGER_PAYLOAD_SIZE");
    recordMaxPayloadSizeForAccount(accountIdentifier, eventPayload);
    List<HeaderConfig> headerConfigs = new ArrayList<>();
    httpHeaders.getRequestHeaders().forEach(
        (k, v) -> headerConfigs.add(HeaderConfig.builder().key(k).values(v).build()));
    ngTriggerService.checkAuthorization(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, headerConfigs);
    TriggerWebhookEvent eventEntity =
        ngTriggerElementMapper
            .toNGTriggerWebhookEventForCustom(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier,
                triggerIdentifier, eventPayload, headerConfigs)
            .build();
    if (eventEntity != null) {
      triggerWebhookValidator.applyValidationsForCustomWebhook(eventEntity, null);
      String uuid = getEventCorrelationId(eventEntity);
      saveEventInTriggerEventHistory(accountIdentifier, eventEntity, uuid);
      return ResponseDTO.newResponse(
          NGProcessWebhookResponseDTO.builder()
              .eventCorrelationId(uuid)
              .apiUrl(urlHelper.buildApiExecutionUrlV2(uuid, accountIdentifier))
              .uiUrl(urlHelper.buildUiUrl(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier))
              .uiSetupUrl(
                  urlHelper.buildUiSetupUrl(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier))
              .build());
    } else {
      return ResponseDTO.newResponse(
          NGProcessWebhookResponseDTO.builder().eventCorrelationId(UNRECOGNIZED_WEBHOOK).build());
    }
  }

  protected String getEventCorrelationId(TriggerWebhookEvent eventEntity) {
    if (pmsFeatureFlagHelper.isEnabled(
            eventEntity.getAccountId(), FeatureName.PIPE_ENABLE_QUEUED_BASED_CUSTOM_TRIGGERS)) {
      TriggerCustomWebhookEvent triggerCustomWebhookEvent =
          ngTriggerElementMapper
              .toNGTriggerCustomWebhookEventForCustomTrigger(eventEntity, TriggerCustomWebhookEventStatus.QUEUED.name())
              .build();
      return ngTriggerService.enqueueTriggerCustomWebhookEvent(triggerCustomWebhookEvent).getUuid();
    } else {
      return ngTriggerService.addEventToQueue(eventEntity).getUuid();
    }
  }

  @VisibleForTesting
  void validateScopeIdentifiers(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (isEmpty(orgIdentifier) || isEmpty(projectIdentifier)) {
      throw new InvalidRequestException(
          "orgIdentifier and projectIdentifier are required query parameters for custom webhook triggers. "
          + "Please provide valid orgIdentifier and projectIdentifier in the webhook URL.");
    }
  }

  public void recordMaxPayloadSizeForAccount(String accountIdentifier, String eventPayload) {
    try {
      long payloadSize = eventPayload.getBytes(StandardCharsets.UTF_8).length;
      if (!pipelineSettingsService.isPayloadSizeWithinLimit(accountIdentifier, payloadSize)) {
        log.warn(
            "[PAYLOAD_SIZE_LIMIT_EXCEEDED]: The File size limit is exceeded for the account {}.", accountIdentifier);
        pipelineRetentionService.updateMaxPayloadSizeLimit(accountIdentifier, payloadSize);
      }
    } catch (Exception e) {
      log.warn(String.format("Can be ignored - Error in overriding the custom webhook payload limit for account id: "
                       + "{%s}, to size: {%d}:",
                   accountIdentifier, eventPayload.length()),
          e);
    }
  }

  @PipelineServiceAuthIfHasApiKey
  public ResponseDTO<NGProcessWebhookResponseDTO> processWebhookEventV3(@NotNull String webhookToken,
      @NotNull String accountIdentifier, @NotNull String orgIdentifier, @NotNull String projectIdentifier,
      String pipelineIdentifier, String triggerIdentifier, @NotNull String eventPayload, HttpHeaders httpHeaders) {
    validateScopeIdentifiers(accountIdentifier, orgIdentifier, projectIdentifier);
    SizeValidatorUtils.validate(eventPayload, "CUSTOM_WEBHOOK_TRIGGER_PAYLOAD_SIZE");
    recordMaxPayloadSizeForAccount(accountIdentifier, eventPayload);
    List<HeaderConfig> headerConfigs = new ArrayList<>();
    httpHeaders.getRequestHeaders().forEach(
        (k, v) -> headerConfigs.add(HeaderConfig.builder().key(k).values(v).build()));
    ngTriggerService.checkAuthorization(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, headerConfigs);
    TriggerWebhookEvent eventEntity =
        ngTriggerElementMapper
            .toNGTriggerWebhookEventForCustom(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier,
                triggerIdentifier, eventPayload, headerConfigs)
            .build();
    if (eventEntity != null) {
      triggerWebhookValidator.applyValidationsForCustomWebhook(eventEntity, webhookToken);
      String uuid = getEventCorrelationId(eventEntity);
      saveEventInTriggerEventHistory(accountIdentifier, eventEntity, uuid);
      return ResponseDTO.newResponse(
          NGProcessWebhookResponseDTO.builder()
              .eventCorrelationId(uuid)
              .apiUrl(urlHelper.buildApiExecutionUrlV2(uuid, accountIdentifier))
              .uiUrl(urlHelper.buildUiUrl(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier))
              .uiSetupUrl(
                  urlHelper.buildUiSetupUrl(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier))
              .build());
    } else {
      return ResponseDTO.newResponse(
          NGProcessWebhookResponseDTO.builder().eventCorrelationId(UNRECOGNIZED_WEBHOOK).build());
    }
  }

  private void saveEventInTriggerEventHistory(String accountIdentifier, TriggerWebhookEvent eventEntity, String uuid) {
    Query query = new Query(Criteria.where(TriggerEventHistoryKeys.accountId)
                                .is(eventEntity.getAccountId())
                                .and(TriggerEventHistoryKeys.eventCorrelationId)
                                .is(uuid));

    triggerEventHistoryRepository.upsert(TriggerEventHistory.builder()
                                             .triggerIdentifier(eventEntity.getTriggerIdentifier())
                                             .uuid(uuid)
                                             .targetIdentifier(eventEntity.getPipelineIdentifier())
                                             .accountId(eventEntity.getAccountId())
                                             .orgIdentifier(eventEntity.getOrgIdentifier())
                                             .projectIdentifier(eventEntity.getProjectIdentifier())
                                             .payload(eventEntity.getPayload())
                                             .eventCreatedAt(eventEntity.getCreatedAt())
                                             .eventCorrelationId(uuid)
                                             .finalStatus(String.valueOf(TriggerEventResponse.FinalStatus.QUEUED))
                                             .ngTriggerType(NGTriggerType.WEBHOOK)
                                             .message("Trigger execution is queued.")
                                             .build(),
        query);
  }

  @PipelineServiceAuthIfHasAuthHeader
  public ResponseDTO<WebhookEventProcessingDetails> fetchWebhookDetails(
      @NotNull String accountIdentifier, @NotNull String eventId) {
    WebhookEventProcessingDetails webhookProcessingDetails =
        ngTriggerService.fetchTriggerEventHistory(accountIdentifier, eventId);
    try (AutoLogContext ignore0 = new NgTriggerAutoLogContext("eventId", eventId,
             webhookProcessingDetails.getTriggerIdentifier(), webhookProcessingDetails.getPipelineIdentifier(),
             webhookProcessingDetails.getProjectIdentifier(), webhookProcessingDetails.getOrgIdentifier(),
             accountIdentifier, AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      ngTriggerService.checkAuthorizationForWebhookDetailsResources(accountIdentifier,
          webhookProcessingDetails.getOrgIdentifier(), webhookProcessingDetails.getProjectIdentifier(),
          webhookProcessingDetails.getPipelineIdentifier(), "triggerProcessingDetails");
    }
    return ResponseDTO.newResponse(webhookProcessingDetails);
  }

  @PipelineServiceAuthIfHasAuthHeader
  public ResponseDTO<WebhookExecutionDetails> fetchWebhookExecutionDetails(
      @NotNull String eventId, @NotNull String accountIdentifier) {
    WebhookEventProcessingDetails webhookProcessingDetails =
        ngTriggerService.fetchTriggerEventHistory(accountIdentifier, eventId);
    if (TriggerEventResponse.FinalStatus.QUEUED.toString().equals(webhookProcessingDetails.getStatus())) {
      throw new InvalidRequestException(
          String.format("Trigger event history doesn't exist for event with eventId %s", eventId));
    }
    Object executionDetails = null;
    String executionUrl = null;
    try (AutoLogContext ignore0 = new NgTriggerAutoLogContext("eventId", eventId,
             webhookProcessingDetails.getTriggerIdentifier(), webhookProcessingDetails.getPipelineIdentifier(),
             webhookProcessingDetails.getProjectIdentifier(), webhookProcessingDetails.getOrgIdentifier(),
             accountIdentifier, AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      ngTriggerService.checkAuthorizationForWebhookDetailsResources(accountIdentifier,
          webhookProcessingDetails.getOrgIdentifier(), webhookProcessingDetails.getProjectIdentifier(),
          webhookProcessingDetails.getPipelineIdentifier(), "triggerExecutionDetails");
      try {
        executionDetails = ngTriggerService.fetchExecutionSummaryV2(webhookProcessingDetails.getPipelineExecutionId(),
            accountIdentifier, webhookProcessingDetails.getOrgIdentifier(),
            webhookProcessingDetails.getProjectIdentifier());
      } catch (Exception e) {
        log.warn(String.format("Unable to find execution details for trigger with eventCorrelationId %s", eventId), e);
      }
      if (executionDetails == null) {
        return ResponseDTO.newResponse(
            WebhookExecutionDetails.builder().webhookProcessingDetails(webhookProcessingDetails).build());
      }
      executionUrl =
          fetchExecutionUrl(eventId, accountIdentifier, executionDetails, executionUrl, webhookProcessingDetails);
    }
    return ResponseDTO.newResponse(WebhookExecutionDetails.builder()
                                       .webhookProcessingDetails(webhookProcessingDetails)
                                       .executionDetails(executionDetails)
                                       .executionUrl(executionUrl)
                                       .build());
  }

  @VisibleForTesting
  String fetchExecutionUrl(String eventId, String accountIdentifier, Object executionDetails, String executionUrl,
      WebhookEventProcessingDetails webhookProcessingDetails) {
    try {
      LinkedHashMap<String, Object> executionDetailsMap =
          (LinkedHashMap<String, Object>) ((LinkedHashMap<String, Object>) executionDetails)
              .get("pipelineExecutionSummary");
      String planExecutionId = (String) executionDetailsMap.get("planExecutionId");
      List<String> modules = (List<String>) executionDetailsMap.get("modules");
      executionUrl = ngTriggerService.fetchExecutionURL(accountIdentifier, webhookProcessingDetails.getOrgIdentifier(),
          webhookProcessingDetails.getProjectIdentifier(), webhookProcessingDetails.getPipelineIdentifier(),
          planExecutionId, modules);
    } catch (Exception e) {
      log.warn(String.format("Unable to find execution url for trigger with eventCorrelationId %s", eventId),
          e.getMessage());
    }
    return executionUrl;
  }

  @PipelineServiceAuthIfHasAuthHeader
  public ResponseDTO<WebhookExecutionDetails> fetchWebhookExecutionDetailsV2(
      @NotNull String eventId, @NotNull String accountIdentifier) {
    WebhookEventProcessingDetails webhookProcessingDetails =
        ngTriggerService.fetchTriggerEventHistory(accountIdentifier, eventId);
    Object executionDetails = null;
    String executionUrl = null;
    try (AutoLogContext ignore0 = new NgTriggerAutoLogContext("eventId", eventId,
             webhookProcessingDetails.getTriggerIdentifier(), webhookProcessingDetails.getPipelineIdentifier(),
             webhookProcessingDetails.getProjectIdentifier(), webhookProcessingDetails.getOrgIdentifier(),
             accountIdentifier, AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      ngTriggerService.checkAuthorizationForWebhookDetailsResources(accountIdentifier,
          webhookProcessingDetails.getOrgIdentifier(), webhookProcessingDetails.getProjectIdentifier(),
          webhookProcessingDetails.getPipelineIdentifier(), "triggerExecutionDetails");

      if (!TriggerEventResponse.FinalStatus.QUEUED.toString().equals(webhookProcessingDetails.getStatus())) {
        try {
          executionDetails = ngTriggerService.fetchExecutionSummaryV2(webhookProcessingDetails.getPipelineExecutionId(),
              accountIdentifier, webhookProcessingDetails.getOrgIdentifier(),
              webhookProcessingDetails.getProjectIdentifier());
        } catch (Exception e) {
          log.warn(
              String.format("Unable to find execution details for trigger with eventCorrelationId %s", eventId), e);
        }
      }

      if (executionDetails == null) {
        return ResponseDTO.newResponse(
            WebhookExecutionDetails.builder().webhookProcessingDetails(webhookProcessingDetails).build());
      }
      executionUrl = fetchExecutionUrl(eventId, accountIdentifier, (LinkedHashMap<String, Object>) executionDetails,
          executionUrl, webhookProcessingDetails);
    }
    return ResponseDTO.newResponse(WebhookExecutionDetails.builder()
                                       .webhookProcessingDetails(webhookProcessingDetails)
                                       .executionDetails(executionDetails)
                                       .executionUrl(executionUrl)
                                       .build());
  }
}

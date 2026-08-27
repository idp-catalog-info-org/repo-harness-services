/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_TRIGGER_FALLBACK_CHANGED_FILES_TO_HEAD_COMMIT_FOR_GITHUB_PUSH_EVENTS;
import static io.harness.beans.FeatureName.PIPE_TRIGGER_MAPPING_V2;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.PR;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_ENABLED_TRIGGER_FOR_ACCOUNT_SOURCE_REPO;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.SKIPPED;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.scm.ParsePayloadResponse;
import io.harness.ngtriggers.beans.scm.ParsePayloadResponse.ParsePayloadResponseBuilder;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.WebhookEventToTriggerMapper;
import io.harness.ngtriggers.eventmapper.filters.TriggerCriteriaFilter;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilterUtils;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.exceptions.TriggerProcessingException;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.helpers.WebhookEventPublisher;
import io.harness.ngtriggers.helpers.filter.TriggerFilterStore;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.utils.ChangedFilesUtils;
import io.harness.ngtriggers.utils.SCMUtils;
import io.harness.ngtriggers.utils.WebhookEventPayloadParser;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.GitProvider;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.service.WebhookParserSCMService;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class GitWebhookEventToTriggerMapper implements WebhookEventToTriggerMapper {
  private final WebhookEventPayloadParser webhookEventPayloadParser;
  private final TriggerFilterStore triggerFilterHelper;
  private final WebhookEventPublisher webhookEventPublisher;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final WebhookParserSCMService webhookParserSCMService;
  private final SCMUtils scmUtils;
  private final MongoTemplate mongoTemplate;
  private final TriggerMapperHelper triggerMapperHelper;
  private final NGTriggerService ngTriggerService;
  private static final String REGEX = "(?:\\[(?:skip ci|ci skip|skip pipeline|pipeline skip)\\]|no_ci)";
  private static final Pattern PATTERN = Pattern.compile(REGEX, Pattern.CASE_INSENSITIVE);
  private static final int BATCH_SIZE = 20;

  public WebhookEventMappingResponse mapWebhookEventToTriggers(TriggerMappingRequestData mappingRequestData) {
    TriggerWebhookEvent triggerWebhookEvent = mappingRequestData.getTriggerWebhookEvent();

    // 1. Parse Payload
    WebhookPayloadData webhookPayloadData = null;
    if (mappingRequestData.getWebhookDTO() == null) {
      ParsePayloadResponse parsePayloadResponse = convertWebhookResponse(triggerWebhookEvent);
      if (parsePayloadResponse.isExceptionOccured()) {
        return WebhookEventMappingResponse.builder()
            .webhookEventResponse(TriggerEventResponseHelper.prepareResponseForScmException(parsePayloadResponse))
            .build();
      }

      webhookPayloadData = parsePayloadResponse.getWebhookPayloadData();
    } else {
      webhookPayloadData = webhookEventPayloadParser.convertWebhookResponse(
          mappingRequestData.getWebhookDTO().getParsedResponse(), triggerWebhookEvent);
    }

    // this is how TI(Test Intelligence) receives its push and pr events today.
    // this is pending to be changed, TI should start consuming events same way as Trigger or Gitsync does.
    // So this can go away
    publishPushAndPrEvent(webhookPayloadData);

    if (includeChangedFiles(webhookPayloadData)) {
      boolean useFallbackExpression =
          pmsFeatureFlagHelper.isEnabled(webhookPayloadData.getOriginalEvent().getAccountId(),
              PIPE_TRIGGER_FALLBACK_CHANGED_FILES_TO_HEAD_COMMIT_FOR_GITHUB_PUSH_EVENTS);
      webhookPayloadData =
          webhookPayloadData.toBuilder()
              .changedFiles(ChangedFilesUtils.getFilesFromPushPayload(webhookPayloadData, useFallbackExpression))
              .build();
    }

    // Generate list of all filters to be applied
    FilterRequestData filterRequestData = FilterRequestData.builder()
                                              .accountId(webhookPayloadData.getOriginalEvent().getAccountId())
                                              .webhookPayloadData(webhookPayloadData)
                                              .build();

    List<TriggerFilter> triggerFilters =
        triggerFilterHelper.getWebhookTriggerFilters(filterRequestData.getWebhookPayloadData());

    if (pmsFeatureFlagHelper.isEnabled(filterRequestData.getAccountId(), PIPE_TRIGGER_MAPPING_V2)) {
      return mapWebhookEventToTriggersV2(triggerWebhookEvent, filterRequestData, triggerFilters);
    }

    WebhookEventMappingResponse optimizedRetrievalResponse =
        retrievePopulateTriggersForFilters(filterRequestData, triggerFilters);
    // if retrievePopulateTriggersForFilters returns a non-null response, means no triggers were found and no need to
    // apply filters
    if (optimizedRetrievalResponse != null) {
      return optimizedRetrievalResponse;
    }
    // Remove criteria filters from the list
    triggerFilters = triggerFilters.stream().filter(it -> !(it instanceof TriggerCriteriaFilter)).toList();

    // Apply filters
    WebhookEventMappingResponse webhookEventMappingResponse;
    webhookEventMappingResponse = triggerMapperHelper.applyFilters(triggerFilters, filterRequestData);

    if (pmsFeatureFlagHelper.isEnabled(
            triggerWebhookEvent.getAccountId(), FeatureName.CDS_SKIP_WEBHOOK_TRIGGER_EXECUTION_ON_SPECIAL_KEYWORDS)
        && !webhookEventMappingResponse.isFailedToFindTrigger()
        && checkIfSkipCiExpressionIsPresent(filterRequestData)) {
      return WebhookEventMappingResponse.builder()
          .webhookEventResponse(
              TriggerEventResponse.builder().finalStatus(SKIPPED).payload(triggerWebhookEvent.getPayload()).build())
          .triggers(webhookEventMappingResponse != null ? webhookEventMappingResponse.getTriggers() : null)
          .failedToFindTrigger(false)
          .build();
    }

    // this condition is included to support expression <+trigger.changedFiles>, it only support PUSH events for
    // Github, Gitlab and HarnessCode providers.
    // TODO: Add support for PR events and support for Bitbucket provider.
    if (includeChangedFiles(webhookPayloadData)) {
      webhookEventMappingResponse =
          webhookEventMappingResponse.toBuilder().changedFiles(webhookPayloadData.getChangedFiles()).build();
    }

    return webhookEventMappingResponse;
  }

  public WebhookEventMappingResponse mapWebhookEventToTriggersV2(TriggerWebhookEvent triggerWebhookEvent,
      FilterRequestData filterRequestData, List<TriggerFilter> triggerFilters) {
    List<TriggerDetails> finalTriggerList;
    Criteria criteria = getCriteriaFromFilters(filterRequestData, triggerFilters);
    Query query = new Query(criteria).cursorBatchSize(BATCH_SIZE);

    // Remove criteria filters from the list
    triggerFilters = triggerFilters.stream().filter(it -> !(it instanceof TriggerCriteriaFilter)).toList();

    WebhookEventMappingResponseBuilder responseBuilder =
        WebhookEventMappingResponse.builder().isCustomTrigger(filterRequestData.isCustomTrigger());

    // will be used to extract the deepest error
    SortedMap<Integer, TriggerEventResponse> errorTreeMap = new TreeMap<>();

    try {
      finalTriggerList = processInBatches(query, triggerFilters, filterRequestData, errorTreeMap);
    } catch (TriggerProcessingException exception) {
      return exception.getWebhookEventMappingResponse();
    }

    return buildFinalResponse(triggerWebhookEvent, filterRequestData, responseBuilder, finalTriggerList, errorTreeMap);
  }

  public WebhookEventMappingResponse retrievePopulateTriggersForFilters(
      FilterRequestData filterRequestData, List<TriggerFilter> triggerFilters) {
    Criteria criteria = getCriteriaFromFilters(filterRequestData, triggerFilters);
    List<NGTriggerEntity> triggersToFilter = ngTriggerService.findTriggersByCriteria(criteria);
    if (isEmpty(triggersToFilter)) {
      TriggerWebhookEvent triggerWebhookEvent = filterRequestData.getWebhookPayloadData().getOriginalEvent();
      String errorMsg = new StringBuilder(256)
                            .append("No enabled trigger found for Account:")
                            .append(triggerWebhookEvent.getAccountId())
                            .append(", SourceRepoType: ")
                            .append(triggerWebhookEvent.getSourceRepoType())
                            .toString();
      log.info(errorMsg);
      return WebhookEventMappingResponse.builder()
          .isCustomTrigger(filterRequestData.isCustomTrigger())
          .failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(
              NO_ENABLED_TRIGGER_FOR_ACCOUNT_SOURCE_REPO, triggerWebhookEvent, null, null, errorMsg, null))
          .build();
    } else {
      filterRequestData.setDetails(triggersToFilter.stream()
                                       .map(entity -> TriggerDetails.builder().ngTriggerEntity(entity).build())
                                       .collect(toList()));
      return null;
    }
  }

  private Criteria applyCriteriaFilters(
      List<TriggerCriteriaFilter> criteriaFilters, FilterRequestData filterRequestData) {
    Criteria criteria = new Criteria();
    criteriaFilters.forEach(filter -> filter.applyCriteria(criteria, filterRequestData));
    return criteria;
  }

  private List<TriggerDetails> processInBatches(Query query, List<TriggerFilter> triggerFilters,
      FilterRequestData filterRequestData, SortedMap<Integer, TriggerEventResponse> errorTreeMap) {
    List<NGTriggerEntity> batch = new ArrayList<>();
    List<TriggerDetails> finalTriggerList = new ArrayList<>();

    try (var stream = mongoTemplate.stream(query, NGTriggerEntity.class)) {
      var iterator = stream.iterator();
      while (iterator.hasNext()) {
        batch.add(iterator.next());
        if (batch.size() == BATCH_SIZE) {
          processBatch(triggerFilters, filterRequestData, batch, finalTriggerList, errorTreeMap);
          batch.clear();
        }
      }

      if (!batch.isEmpty()) {
        processBatch(triggerFilters, filterRequestData, batch, finalTriggerList, errorTreeMap);
        batch.clear();
      }
    }

    return finalTriggerList;
  }

  private void processBatch(List<TriggerFilter> triggerFilters, FilterRequestData filterRequestData,
      List<NGTriggerEntity> batch, List<TriggerDetails> finalTriggerList,
      SortedMap<Integer, TriggerEventResponse> errorTreeMap) {
    TriggerFilter triggerFilterInAction = null;
    List<TriggerDetails> triggerList = TriggerFilterUtils.mapToTriggerDetails(batch);

    try {
      // since we are batching and filtering in memory, we need the deepest index to provide proper meaningful error
      int triggerDepthIndex = 0;
      for (TriggerFilter triggerFilter : triggerFilters) {
        triggerFilterInAction = triggerFilter;
        triggerList = triggerFilter.applyFilterV2(triggerList, filterRequestData);
        if (triggerList.isEmpty()) {
          errorTreeMap.put(triggerDepthIndex, triggerFilter.getFailureResponse(filterRequestData));
          break;
        }
        triggerDepthIndex++;
      }
    } catch (Exception e) {
      log.warn("Exception while evaluating Triggers: ", e);
      throw new TriggerProcessingException(triggerFilterInAction, filterRequestData, e);
    }

    finalTriggerList.addAll(triggerList);
  }

  private WebhookEventMappingResponse buildFinalResponse(TriggerWebhookEvent triggerWebhookEvent,
      FilterRequestData filterRequestData, WebhookEventMappingResponseBuilder responseBuilder,
      List<TriggerDetails> finalTriggerList, SortedMap<Integer, TriggerEventResponse> errorTreeMap) {
    if (finalTriggerList.isEmpty()) {
      var triggerEventResponse = errorTreeMap.get(errorTreeMap.lastKey());
      log.warn(triggerEventResponse.getMessage());
      responseBuilder.failedToFindTrigger(true).webhookEventResponse(triggerEventResponse);
      return responseBuilder.build();
    }

    if (shouldSkipExecution(triggerWebhookEvent, filterRequestData)) {
      return WebhookEventMappingResponse.builder()
          .webhookEventResponse(
              TriggerEventResponse.builder().finalStatus(SKIPPED).payload(triggerWebhookEvent.getPayload()).build())
          .triggers(responseBuilder.build().getTriggers())
          .failedToFindTrigger(false)
          .build();
    }

    responseBuilder.triggers(finalTriggerList);
    responseBuilder.failedToFindTrigger(false);

    return responseBuilder.build();
  }

  private boolean shouldSkipExecution(TriggerWebhookEvent triggerWebhookEvent, FilterRequestData filterRequestData) {
    return pmsFeatureFlagHelper.isEnabled(
               triggerWebhookEvent.getAccountId(), FeatureName.CDS_SKIP_WEBHOOK_TRIGGER_EXECUTION_ON_SPECIAL_KEYWORDS)
        && checkIfSkipCiExpressionIsPresent(filterRequestData);
  }

  @VisibleForTesting
  boolean checkIfSkipCiExpressionIsPresent(FilterRequestData filterRequestData) {
    if (filterRequestData.getWebhookPayloadData() == null
        || filterRequestData.getWebhookPayloadData().getParseWebhookResponse() == null) {
      return false;
    }
    ParseWebhookResponse parseWebhookResponse = filterRequestData.getWebhookPayloadData().getParseWebhookResponse();

    // PR events: skip when the keyword is present in the PR title.
    if (parseWebhookResponse.hasPr() && parseWebhookResponse.getPr().hasPr()) {
      if (checkIfSkipCIIsPresent(getPrTitle(parseWebhookResponse.getPr().getPr()))) {
        return true;
      }
    }

    // Push events, including the push produced when a PR is merged into the target branch. The PR title is
    // not part of this payload, so the keyword has to be found on the tip commit message.
    if (!parseWebhookResponse.hasPush()) {
      return false;
    }
    return containsSkipCiInPushHook(parseWebhookResponse.getPush(), filterRequestData);
  }

  private boolean containsSkipCiInPushHook(PushHook pushHook, FilterRequestData filterRequestData) {
    if (GitProvider.STASH.equals(obtainWebhookSource(filterRequestData))) {
      return containsSkipCiInBitbucketOnPremPush(pushHook, filterRequestData);
    }

    // Prefer the head/merge commit — that is the tip being built, and for PR merges it usually carries
    // the PR title (and thus [skip ci] when present there). Do not scan older commits in the push.
    if (pushHook.hasCommit()) {
      String headCommitMessage = getCommitMessage(pushHook.getCommit(), filterRequestData);
      if (isEmpty(headCommitMessage)) {
        log.info("commit message is empty. Please check the event sent from the webhook.");
      }
      return checkIfSkipCIIsPresent(headCommitMessage);
    }

    // Some payloads omit head and only populate commits; fall back to commits[0] (develop behavior)
    // without scanning the rest of the history.
    if (!pushHook.getCommitsList().isEmpty()) {
      String commitMessage = getCommitMessage(pushHook.getCommits(0), filterRequestData);
      if (isEmpty(commitMessage)) {
        log.info("commit message is empty. Please check the event sent from the webhook.");
      }
      return checkIfSkipCIIsPresent(commitMessage);
    }

    log.info("commit message is empty. Please check the event sent from the webhook.");
    return false;
  }

  /**
   * Push webhooks in Bitbucket on-prem (GitProvider = STASH) do not carry commit messages, so each message
   * has to be fetched through SCM. Only the first commit is resolved (falling back to the head commit) to
   * keep the number of SCM calls per webhook at one — identical to develop.
   */
  private boolean containsSkipCiInBitbucketOnPremPush(PushHook pushHook, FilterRequestData filterRequestData) {
    String commitMessage = null;
    if (!pushHook.getCommitsList().isEmpty()) {
      commitMessage = getCommitMessage(pushHook.getCommits(0), filterRequestData);
    }
    if (isEmpty(commitMessage) && pushHook.hasCommit()) {
      commitMessage = getCommitMessage(pushHook.getCommit(), filterRequestData);
    }
    if (isEmpty(commitMessage)) {
      log.info("commit message is empty. Please check the event sent from the webhook.");
    }
    return checkIfSkipCIIsPresent(commitMessage);
  }

  private String getPrTitle(PullRequest pr) {
    return pr.getTitle().toLowerCase();
  }

  private String getCommitMessage(Commit commit, FilterRequestData filterRequestData) {
    if (EmptyPredicate.isNotEmpty(commit.getMessage())) {
      return commit.getMessage().toLowerCase();
    } else if (GitProvider.STASH.equals(obtainWebhookSource(filterRequestData))) {
      /* Push webhooks in Bitbucket on-prem (GitProvider = STASH) do not contain the commit message.
         So, in this case, we need to fetch the commit message using SCM. */
      return scmUtils.fetchCommitMessage(commit.getSha(), filterRequestData).toLowerCase();
    }
    return null;
  }

  private GitProvider obtainWebhookSource(FilterRequestData filterRequestData) {
    TriggerWebhookEvent originalEvent = filterRequestData.getWebhookPayloadData().getOriginalEvent();
    if (originalEvent != null && originalEvent.getHeaders() != null) {
      return webhookParserSCMService.obtainWebhookSource(originalEvent.getHeaders());
    }
    return null;
  }

  private boolean checkIfSkipCIIsPresent(String prTitleOrCommitMessage) {
    if (prTitleOrCommitMessage != null && PATTERN.matcher(prTitleOrCommitMessage).find()) {
      log.info("Skipping the execution as ci skip or related keyword has been found. Skip message: {}",
          prTitleOrCommitMessage);
      return true;
    }
    return false;
  }

  /**
   * This is temporary, added specifically to support TI use-case.
   * We only publish "PUSH" and "PR" git event.
   * This will become part of common service, where different subscribers can subscribe for
   * eventType, triggerType to receive events.
   * <p>
   * Then this can be removed.
   *
   * @param webhookPayloadData
   */
  @VisibleForTesting
  void publishPushAndPrEvent(WebhookPayloadData webhookPayloadData) {
    try {
      if (webhookPayloadData.getParseWebhookResponse().hasPr()) {
        webhookEventPublisher.publishGitWebhookEvent(webhookPayloadData, PR);
      }
    } catch (Exception e) {
      log.error("Failed to send webhook event {} to events framework: {}",
          webhookPayloadData.getOriginalEvent().getUuid(), e);
    }
  }

  // Add error handling
  @VisibleForTesting
  ParsePayloadResponse convertWebhookResponse(TriggerWebhookEvent triggerWebhookEvent) {
    ParsePayloadResponseBuilder builder = ParsePayloadResponse.builder();
    try {
      WebhookPayloadData webhookPayloadData = webhookEventPayloadParser.parseEvent(triggerWebhookEvent);
      builder.webhookPayloadData(webhookPayloadData).build();
    } catch (Exception e) {
      builder.exceptionOccured(true)
          .exception(e)
          .webhookPayloadData(WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).build())
          .build();
    }

    return builder.build();
  }

  private boolean includeChangedFiles(WebhookPayloadData webhookPayloadData) {
    return webhookPayloadData.getParseWebhookResponse().hasPush();
  }

  private Criteria getCriteriaFromFilters(FilterRequestData filterRequestData, List<TriggerFilter> triggerFilters) {
    List<TriggerCriteriaFilter> criteriaFilters = triggerFilters.stream()
                                                      .filter(TriggerCriteriaFilter.class ::isInstance)
                                                      .map(TriggerCriteriaFilter.class ::cast)
                                                      .toList();

    return applyCriteriaFilters(criteriaFilters, filterRequestData);
  }
}

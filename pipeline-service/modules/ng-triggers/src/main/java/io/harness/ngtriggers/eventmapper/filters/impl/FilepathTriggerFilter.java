/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_ALLOW_MULTIPLE_FILEPATH_CONDITIONS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.utils.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.utils.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.utils.ConnectorType.CODECOMMIT;
import static io.harness.delegate.beans.connector.utils.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.utils.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.utils.ConnectorType.GITLAB;
import static io.harness.delegate.beans.connector.utils.ConnectorType.HARNESS;
import static io.harness.ngtriggers.Constants.BITBUCKET_LOWER_CASE;
import static io.harness.ngtriggers.Constants.CHANGED_FILES;
import static io.harness.ngtriggers.Constants.GITHUB_LOWER_CASE;
import static io.harness.ngtriggers.Constants.GITLAB_LOWER_CASE;
import static io.harness.ngtriggers.Constants.HARNESS_LOWER_CASE;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_FILEPATH_CONDITIONS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.GitConfigDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskResponse;
import io.harness.delegate.task.scm.TriggerCondition;
import io.harness.delegate.task.scm.TriggerFilepathResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ScmPathFilterTaskException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.webhook.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;
import io.harness.ngtriggers.conditionchecker.ConditionEvaluator;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.utils.SCMFilePathEvaluator;
import io.harness.ngtriggers.utils.SCMFilePathEvaluatorFactory;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.utils.ConnectorUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class FilepathTriggerFilter implements TriggerFilter {
  private SCMFilePathEvaluatorFactory scmFilePathEvaluatorFactory;
  private NGTriggerElementMapper ngTriggerElementMapper;
  private ConnectorUtils connectorUtils;
  private HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  private String harnessCodeApiUrl;
  private String harnessCodeGitBaseUrl;
  private String harnessCodeServiceSecret;
  private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private ScopeResolutionHelper scopeResolutionHelper;

  @Inject
  public FilepathTriggerFilter(SCMFilePathEvaluatorFactory scmFilePathEvaluatorFactory,
      NGTriggerElementMapper ngTriggerElementMapper, ConnectorUtils connectorUtils,
      HarnessCodeConnectorUtils harnessCodeConnectorUtils, @Named("harnessCodeApiUrl") String harnessCodeApiUrl,
      @Named("harnessCodeGitBaseUrl") String harnessCodeGitBaseUrl,
      @Named("harnessCodeServiceSecret") String harnessCodeServiceSecret, PmsFeatureFlagHelper pmsFeatureFlagHelper,
      ScopeResolutionHelper scopeResolutionHelper) {
    this.scmFilePathEvaluatorFactory = scmFilePathEvaluatorFactory;
    this.ngTriggerElementMapper = ngTriggerElementMapper;
    this.connectorUtils = connectorUtils;
    this.harnessCodeConnectorUtils = harnessCodeConnectorUtils;
    this.harnessCodeApiUrl = harnessCodeApiUrl;
    this.harnessCodeGitBaseUrl = harnessCodeGitBaseUrl;
    this.harnessCodeServiceSecret = harnessCodeServiceSecret;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
    this.scopeResolutionHelper = scopeResolutionHelper;
  }

  @Override
  public TriggerEventResponse getFailureResponse(FilterRequestData filterRequestData) {
    return TriggerEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_FILEPATH_CONDITIONS,
        filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
        "No trigger matched Path condition after filter evaluation for Account: " + filterRequestData.getAccountId(),
        null);
  }

  @Override
  public List<TriggerDetails> applyFilterV2(
      List<TriggerDetails> triggerDetailsList, FilterRequestData filterRequestData) {
    if (pathFilterEvaluationNotNeeded(filterRequestData)) {
      return triggerDetailsList;
    }

    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap;
    boolean isParentIdQueryingEnabled = true;
    if (isParentIdQueryingEnabled) {
      List<String> parentUniqueIds = triggerDetailsList.stream()
                                         .map(triggerDetails -> triggerDetails.getNgTriggerEntity().getParentUniqueId())
                                         .filter(id -> id != null && !id.isBlank())
                                         .collect(Collectors.toList());
      parentUniqueIdToScopeInfoMap =
          scopeResolutionHelper.getScopeInfos(filterRequestData.getAccountId(), parentUniqueIds);

    } else {
      parentUniqueIdToScopeInfoMap = null;
    }
    triggerDetailsList.forEach(trigger -> {
      NGTriggerConfigV2 ngTriggerConfig = trigger.getNgTriggerConfigV2();
      ScopeInfo scopeInfo = isParentIdQueryingEnabled
          ? parentUniqueIdToScopeInfoMap
                .getOrDefault(trigger.getNgTriggerEntity().getParentUniqueId(), Optional.empty())
                .orElse(null)
          : null;
      if (ngTriggerConfig == null) {
        ngTriggerConfig = ngTriggerElementMapper.toTriggerConfigV2(
            trigger.getNgTriggerEntity(), scopeInfo, isParentIdQueryingEnabled);
      }
      TriggerDetails triggerDetails = TriggerDetails.builder()
                                          .ngTriggerConfigV2(ngTriggerConfig)
                                          .ngTriggerEntity(trigger.getNgTriggerEntity())
                                          .build();
      if (checkTriggerEligibility(filterRequestData, triggerDetails, isParentIdQueryingEnabled ? scopeInfo : null,
              isParentIdQueryingEnabled)) {
        matchedTriggers.add(triggerDetails);
      }
    });

    return matchedTriggers;
  }

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);

    // If not push or PR, return list as is, as path filters does not apply
    if (pathFilterEvaluationNotNeeded(filterRequestData)) {
      return mappingResponseBuilder.failedToFindTrigger(false)
          .parseWebhookResponse(filterRequestData.getWebhookPayloadData().getParseWebhookResponse())
          .triggers(filterRequestData.getDetails())
          .build();
    }

    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    if (pmsFeatureFlagHelper.isEnabled(filterRequestData.getAccountId(), PIPE_ALLOW_MULTIPLE_FILEPATH_CONDITIONS)) {
      List<TriggerDetails> triggerToEvaluate = new ArrayList<>();
      try {
        filterAndCollectTriggersForEvaluation(filterRequestData, matchedTriggers, triggerToEvaluate);
        evaluateTriggerConditionsV2(filterRequestData, matchedTriggers, triggerToEvaluate);
      } catch (ScmPathFilterTaskException e) {
        log.warn(
            "Failed to evaluate multiple filepath conditions for triggers, fallback to single filepath condition flow",
            e);
        matchedTriggers = new ArrayList<>(); // reset
        evaluateTriggerConditions(filterRequestData, matchedTriggers);
      }
    } else {
      evaluateTriggerConditions(filterRequestData, matchedTriggers);
    }

    if (isEmpty(matchedTriggers)) {
      log.info("No trigger matched Path condition after filter evaluation:");
      mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_FILEPATH_CONDITIONS,
              filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
              "No trigger matched Path condition after filter evaluation for Account: "
                  + filterRequestData.getAccountId(),
              null))
          .build();
    } else {
      addDetails(mappingResponseBuilder, filterRequestData, matchedTriggers);
    }
    return mappingResponseBuilder.build();
  }

  void evaluateTriggerConditions(FilterRequestData filterRequestData, List<TriggerDetails> matchedTriggers) {
    Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap = null;
    boolean isParentIdQueryingEnabled = true;
    if (isParentIdQueryingEnabled) {
      List<String> parentUniqueIds = filterRequestData.getDetails()
                                         .stream()
                                         .map(triggerDetails -> triggerDetails.getNgTriggerEntity().getParentUniqueId())
                                         .filter(id -> id != null && !id.isBlank())
                                         .collect(Collectors.toList());
      parentUniqueIdToScopeInfoMap =
          scopeResolutionHelper.getScopeInfos(filterRequestData.getAccountId(), parentUniqueIds);
    }

    for (TriggerDetails trigger : filterRequestData.getDetails()) {
      ScopeInfo scopeInfo = isParentIdQueryingEnabled
          ? parentUniqueIdToScopeInfoMap
                .getOrDefault(trigger.getNgTriggerEntity().getParentUniqueId(), Optional.empty())
                .orElse(null)
          : null;
      TriggerDetails triggerDetails = getTriggerDetailsWithConfigV2(trigger, scopeInfo, isParentIdQueryingEnabled);
      if (checkTriggerEligibility(filterRequestData, triggerDetails, isParentIdQueryingEnabled ? scopeInfo : null,
              isParentIdQueryingEnabled)) {
        matchedTriggers.add(triggerDetails);
      }
    }
  }

  @VisibleForTesting
  boolean pathFilterEvaluationNotNeeded(FilterRequestData filterRequestData) {
    ParseWebhookResponse parseWebhookResponse = filterRequestData.getWebhookPayloadData().getParseWebhookResponse();
    if (parseWebhookResponse == null) {
      return true;
    }

    if (filterRequestData.getWebhookPayloadData().getOriginalEvent().getSourceRepoType().equalsIgnoreCase(
            WebhookTriggerType.AWS_CODECOMMIT.getEntityMetadataName())) {
      return true;
    }

    if (!parseWebhookResponse.hasPr() && !parseWebhookResponse.hasPush() && !parseWebhookResponse.hasComment()) {
      return true;
    }
    return false;
  }

  @VisibleForTesting
  boolean checkTriggerEligibility(FilterRequestData filterRequestData, TriggerDetails triggerDetails,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    try {
      NGTriggerSpecV2 spec = triggerDetails.getNgTriggerConfigV2().getSource().getSpec();
      if (!WebhookTriggerConfigV2.class.isAssignableFrom(spec.getClass())) {
        log.error("Trigger spec is not a WebhookTriggerConfig");
        return false;
      }

      WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) spec;
      List<TriggerEventDataCondition> payloadConditions =
          webhookTriggerConfig.getSpec().fetchPayloadAware().fetchPayloadConditions();

      if (isEmpty(payloadConditions)) {
        return true;
      }

      TriggerEventDataCondition pathCondition =
          payloadConditions.stream()
              .filter(payloadCondition -> CHANGED_FILES.equalsIgnoreCase(payloadCondition.getKey()))
              .findFirst()
              .orElse(null);
      if (pathCondition == null) {
        return true;
      }

      if (shouldEvaluateOnSCM(filterRequestData)) {
        return initiateSCMTaskAndEvaluate(
            filterRequestData, triggerDetails, pathCondition, scopeInfo, isParentIdQueryingEnabled);
      } else {
        return evaluateFromPushPayload(filterRequestData, pathCondition);
      }
    } catch (Exception e) {
      log.warn(getTriggerSkipMessage(triggerDetails.getNgTriggerEntity()), e);
      return false;
    }
  }

  @VisibleForTesting
  boolean evaluateFromPushPayload(FilterRequestData filterRequestData, TriggerEventDataCondition pathCondition) {
    Set<String> payloadFiles = filterRequestData.getWebhookPayloadData().getChangedFiles();
    if (payloadFiles == null) {
      payloadFiles = Collections.emptySet();
    }

    boolean eligible = false;
    for (String pathFetched : payloadFiles) {
      if (ConditionEvaluator.evaluate(pathFetched, pathCondition.getValue(), pathCondition.getOperator().getValue())) {
        eligible = true;
        break;
      }
    }

    return eligible;
  }

  @VisibleForTesting
  boolean initiateSCMTaskAndEvaluate(FilterRequestData filterRequestData, TriggerDetails triggerDetails,
      TriggerEventDataCondition pathCondition, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    ScmPathFilterEvaluationTaskResponse scmPathFilterEvaluationTaskResponse = performScmPathFilterEvaluation(
        triggerDetails.getNgTriggerEntity(), filterRequestData, pathCondition, scopeInfo, isParentIdQueryingEnabled);
    if (scmPathFilterEvaluationTaskResponse == null) {
      log.warn(new StringBuilder(128)
                   .append(getTriggerSkipMessage(triggerDetails.getNgTriggerEntity()))
                   .append(", Null response from Delegate Task: ")
                   .toString());
      return false;
    } else {
      if (isNotEmpty(scmPathFilterEvaluationTaskResponse.getErrorMessage())) {
        log.warn(new StringBuilder(128)
                     .append(getTriggerSkipMessage(triggerDetails.getNgTriggerEntity()))
                     .append(", Error Message from Delegate Task: ")
                     .append(scmPathFilterEvaluationTaskResponse.getErrorMessage())
                     .toString());
      }
      return scmPathFilterEvaluationTaskResponse.isMatched();
    }
  }

  private ScmPathFilterEvaluationTaskResponse performScmPathFilterEvaluation(NGTriggerEntity ngTriggerEntity,
      FilterRequestData filterRequestData, TriggerEventDataCondition pathCondition, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    try {
      WebhookMetadata webhook = ngTriggerEntity.getMetadata().getWebhook();
      ConnectorDetails connectorDetails;
      IdentifierRef ngAccess =
          IdentifierRef.builder()
              .accountIdentifier(ngTriggerEntity.getAccountId())
              .orgIdentifier(
                  isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier())
              .projectIdentifier(
                  isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier())
              .build();

      if (webhook.getGit().getIsHarnessScm() == null || !webhook.getGit().getIsHarnessScm()) {
        connectorDetails = connectorUtils.getConnectorDetails(ngAccess, webhook.getGit().getConnectorIdentifier());
      } else {
        HarnessConnectorDTO connector = harnessCodeConnectorUtils.getDummyHarnessCodeConnectorWithJwtAuth(
            ngTriggerEntity.getAccountId(),
            isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
            isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
            webhook.getGit().getRepoName(), harnessCodeServiceSecret, harnessCodeApiUrl, harnessCodeGitBaseUrl, null);
        ConnectorDTO connectorDTO =
            ConnectorDTO.builder()
                .connectorInfo(
                    ConnectorInfoDTO.builder()
                        .identifier("HARNESS_SCM")
                        .name("HARNESS_SCM")
                        .projectIdentifier(isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier()
                                                                     : ngTriggerEntity.getProjectIdentifier())
                        .orgIdentifier(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier()
                                                                 : ngTriggerEntity.getOrgIdentifier())
                        .accountIdentifier(ngTriggerEntity.getAccountId())
                        .connectorType(HARNESS)
                        .connectorConfig(connector)
                        .build())
                .build();
        connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorDTO);
      }

      ScmConnector scmConnector = getSCMConnector(connectorDetails, webhook);
      if (scmConnector == null) {
        return null;
      }

      boolean executeOnDelegate =
          connectorDetails.getExecuteOnDelegate() == null || connectorDetails.getExecuteOnDelegate();

      SCMFilePathEvaluator scmFilePathEvaluator = scmFilePathEvaluatorFactory.getEvaluator(executeOnDelegate);
      return scmFilePathEvaluator.execute(filterRequestData, pathCondition, connectorDetails, scmConnector);
    } catch (Exception e) {
      log.error(getTriggerSkipMessage(ngTriggerEntity) + ". Failed in executing delegate task", e);
    }

    return null;
  }

  private ScmConnector getSCMConnector(ConnectorDetails connectorDetails, WebhookMetadata webhookMetadata) {
    ScmConnector connector = null;
    ConnectorConfigDTO connectorConfigDTO = connectorDetails.getConnectorConfig();
    switch (connectorDetails.getConnectorType()) {
      case GITHUB:
        connector = (GithubConnectorDTO) connectorConfigDTO;
        break;
      case GITLAB:
        connector = (GitlabConnectorDTO) connectorConfigDTO;
        break;
      case BITBUCKET:
        connector = (BitbucketConnectorDTO) connectorConfigDTO;
        break;
      case AZURE_REPO:
        connector = (AzureRepoConnectorDTO) connectorConfigDTO;
        break;
      case HARNESS:
        connector = (HarnessConnectorDTO) connectorConfigDTO;
        break;
      default:
        break;
    }

    if (connector != null) {
      String completeUrl = connector.getUrl();
      GitConnectionType gitConnectionType = getGitConnectionType(connectorDetails);
      if (isAzureRepoProjectLevel(connector)) {
        completeUrl = GitClientHelper.getCompleteUrlForProjectLevelAzureConnector(
            completeUrl, webhookMetadata.getGit().getRepoName());
      } else if (isNotEmpty(webhookMetadata.getGit().getRepoName())
          && (gitConnectionType == null || gitConnectionType == GitConnectionType.ACCOUNT)) {
        completeUrl = StringUtils.stripEnd(connector.getUrl(), "/") + "/"
            + StringUtils.stripStart(webhookMetadata.getGit().getRepoName(), "/");
      }
      connector.setUrl(completeUrl);
    }

    return connector;
  }

  private GitConnectionType getGitConnectionType(ConnectorDetails gitConnector) {
    if (gitConnector == null) {
      return null;
    }

    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == CODECOMMIT) {
      AwsCodeCommitConnectorDTO gitConfigDTO = (AwsCodeCommitConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrlType() == AwsCodeCommitUrlType.REPO ? GitConnectionType.REPO
                                                                    : GitConnectionType.ACCOUNT;
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType() == AzureRepoConnectionTypeDTO.REPO ? GitConnectionType.REPO
                                                                                 : GitConnectionType.PROJECT;

    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getGitConnectionType();
    } else if (gitConnector.getConnectorType() == HARNESS) {
      return GitConnectionType.REPO;
    } else {
      throw new CIStageExecutionException("Unsupported git connector type" + gitConnector.getConnectorType());
    }
  }

  private boolean isAzureRepoProjectLevel(ScmConnector connector) {
    if (connector instanceof AzureRepoConnectorDTO
        && ((AzureRepoConnectorDTO) connector).getConnectionType() == AzureRepoConnectionTypeDTO.PROJECT) {
      return true;
    }
    return false;
  }

  // Gitlab docs say, payload would contains details about 20 commits.
  // So if there more than or equal to 20 commits, there is a chance, few commits were truncated.
  // So, we go to delegate task.
  @VisibleForTesting
  boolean shouldEvaluateOnSCM(FilterRequestData filterRequestData) {
    if (filterRequestData.getWebhookPayloadData().getParseWebhookResponse().hasPr()) {
      return true;
    } else if (filterRequestData.getWebhookPayloadData().getParseWebhookResponse().hasPush()) {
      String sourceRepoType =
          filterRequestData.getWebhookPayloadData().getOriginalEvent().getSourceRepoType().toLowerCase();
      switch (sourceRepoType) {
        case GITHUB_LOWER_CASE:
          // There are no documented limits on Github's push payload (apart from payload being capped to 25MB).
          // In which case the webhook will not event be fired.
          // ref: https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#push
          // As of 02/01/2023, we verified experimentally that Github's `compareCommits` API is limited to returning
          // 300 files changed, and no pagination is possible, so we should always use the webhook's payload here.
          return false;
        case GITLAB_LOWER_CASE:
        case HARNESS_LOWER_CASE:
          int commitsCount =
              filterRequestData.getWebhookPayloadData().getParseWebhookResponse().getPush().getCommitsCount();
          return commitsCount >= 20;
        case BITBUCKET_LOWER_CASE:
        default:
          return true;
      }
    } else {
      // No Path filter evaluation needed.
      return true;
    }
  }

  private TriggerDetails getTriggerDetailsWithConfigV2(
      TriggerDetails trigger, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerConfigV2 ngTriggerConfig = trigger.getNgTriggerConfigV2();
    if (ngTriggerConfig == null) {
      ngTriggerConfig =
          ngTriggerElementMapper.toTriggerConfigV2(trigger.getNgTriggerEntity(), scopeInfo, isParentIdQueryingEnabled);
    }

    return TriggerDetails.builder()
        .ngTriggerConfigV2(ngTriggerConfig)
        .ngTriggerEntity(trigger.getNgTriggerEntity())
        .build();
  }

  private boolean isWebhookTriggerV2(TriggerDetails trigger) {
    NGTriggerSpecV2 spec = trigger.getNgTriggerConfigV2().getSource().getSpec();
    if (!WebhookTriggerConfigV2.class.isAssignableFrom(spec.getClass())) {
      log.error("Trigger spec is not a WebhookTriggerConfig");
      return false;
    }
    return true;
  }

  private TriggerEventDataCondition findChangedFilesCondition(TriggerDetails triggerDetails) {
    NGTriggerSpecV2 spec = triggerDetails.getNgTriggerConfigV2().getSource().getSpec();
    WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) spec;
    List<TriggerEventDataCondition> payloadConditions =
        webhookTriggerConfig.getSpec().fetchPayloadAware().fetchPayloadConditions();

    if (isEmpty(payloadConditions)) {
      return null;
    }

    return payloadConditions.stream()
        .filter(payloadCondition -> CHANGED_FILES.equalsIgnoreCase(payloadCondition.getKey()))
        .findFirst()
        .orElse(null);
  }

  private void filterAndCollectTriggersForEvaluation(FilterRequestData filterRequestData,
      List<TriggerDetails> matchedTriggers, List<TriggerDetails> triggerToEvaluate) {
    Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap = null;
    boolean isParentIdQueryingEnabled = true;
    if (isParentIdQueryingEnabled) {
      List<String> parentUniqueIds = filterRequestData.getDetails()
                                         .stream()
                                         .map(triggerDetails -> triggerDetails.getNgTriggerEntity().getParentUniqueId())
                                         .filter(id -> id != null && !id.isBlank())
                                         .collect(Collectors.toList());
      parentUniqueIdToScopeInfoMap =
          scopeResolutionHelper.getScopeInfos(filterRequestData.getAccountId(), parentUniqueIds);
    }
    for (TriggerDetails trigger : filterRequestData.getDetails()) {
      ScopeInfo scopeInfo = isParentIdQueryingEnabled
          ? parentUniqueIdToScopeInfoMap
                .getOrDefault(trigger.getNgTriggerEntity().getParentUniqueId(), Optional.empty())
                .orElse(null)
          : null;
      TriggerDetails triggerDetails = getTriggerDetailsWithConfigV2(trigger, scopeInfo, isParentIdQueryingEnabled);
      if (!isWebhookTriggerV2(trigger)) {
        continue;
      }
      TriggerEventDataCondition changedFilesCondition = findChangedFilesCondition(triggerDetails);
      if (changedFilesCondition == null) {
        matchedTriggers.add(triggerDetails);
        continue;
      }
      if (shouldEvaluateOnSCM(filterRequestData)) {
        triggerToEvaluate.add(triggerDetails);
      } else {
        if (evaluateFromPushPayload(filterRequestData, changedFilesCondition)) {
          matchedTriggers.add(triggerDetails);
        }
      }
    }
  }

  @VisibleForTesting
  void evaluateTriggerConditionsV2(FilterRequestData filterRequestData, List<TriggerDetails> matchedTriggers,
      List<TriggerDetails> triggerToEvaluate) {
    List<TriggerDetails> triggersWithUniqueConnectors =
        triggerToEvaluate.stream()
            .filter(distinctByKey(trigger
                -> Optional.ofNullable(trigger)
                       .map(TriggerDetails::getNgTriggerEntity)
                       .map(NGTriggerEntity::getMetadata)
                       .map(NGTriggerMetadata::getWebhook)
                       .map(WebhookMetadata::getGit)
                       .map(GitMetadata::getConnectorIdentifier)
                       .orElse("") // use nullable reference to avoid NPE and default to blank when connector identifier
                                   // is null due to Harness code connector
                ))
            .toList();
    Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap = null;
    boolean isParentIdQueryingEnabled = true;
    if (isParentIdQueryingEnabled) {
      List<String> parentUniqueIds = triggersWithUniqueConnectors.stream()
                                         .map(triggerDetails -> triggerDetails.getNgTriggerEntity().getParentUniqueId())
                                         .filter(id -> id != null && !id.isBlank())
                                         .collect(Collectors.toList());
      // triggerToEvaluate can be empty if the shouldEvaluateForSCM is false
      if (!parentUniqueIds.isEmpty()) {
        parentUniqueIdToScopeInfoMap =
            scopeResolutionHelper.getScopeInfos(filterRequestData.getAccountId(), parentUniqueIds);
      }
    }
    // We iterate over triggers with unique connector identifiers
    for (TriggerDetails trigger : triggersWithUniqueConnectors) {
      List<TriggerFilepathResponse> triggerFilepathResponses =
          performScmPathFilterEvaluation(trigger.getNgTriggerEntity(), filterRequestData, triggerToEvaluate,
              isParentIdQueryingEnabled
                  ? parentUniqueIdToScopeInfoMap
                        .getOrDefault(trigger.getNgTriggerEntity().getParentUniqueId(), Optional.empty())
                        .orElse(null)
                  : null,
              isParentIdQueryingEnabled);
      if (triggerFilepathResponses != null && !triggerFilepathResponses.isEmpty()) {
        processAndCollectValidTriggerResponses(matchedTriggers, triggerFilepathResponses, triggerToEvaluate);
        break;
      }
    }
  }

  private List<TriggerFilepathResponse> performScmPathFilterEvaluation(NGTriggerEntity ngTriggerEntity,
      FilterRequestData filterRequestData, List<TriggerDetails> triggerToEvaluate, ScopeInfo scopeInfoOfTriggerEntity,
      boolean isParentIdQueryingEnabled) {
    try {
      WebhookMetadata webhook = ngTriggerEntity.getMetadata().getWebhook();
      ConnectorDetails connectorDetails =
          getConnectorDetails(ngTriggerEntity, webhook, scopeInfoOfTriggerEntity, isParentIdQueryingEnabled);
      ScmConnector scmConnector = getSCMConnector(connectorDetails, webhook);
      if (scmConnector == null) {
        return null;
      }

      boolean executeOnDelegate =
          connectorDetails.getExecuteOnDelegate() == null || connectorDetails.getExecuteOnDelegate();

      SCMFilePathEvaluator scmFilePathEvaluator = scmFilePathEvaluatorFactory.getEvaluator(executeOnDelegate);
      List<TriggerCondition> triggerConditions =
          triggerToEvaluate.stream()
              .map(triggerDetails -> {
                TriggerEventDataCondition changedFilesCondition = findChangedFilesCondition(triggerDetails);
                if (changedFilesCondition == null) {
                  return null;
                }
                return TriggerCondition.builder()
                    .triggerEntityId(triggerDetails.getNgTriggerEntity().getUuid())
                    .operator(changedFilesCondition.getOperator().getValue())
                    .value(changedFilesCondition.getValue())
                    .build();
              })
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
      TriggerEventDataCondition changedFilesCondition = findChangedFilesCondition(triggerToEvaluate.get(0));
      return scmFilePathEvaluator.execute(
          filterRequestData, changedFilesCondition, triggerConditions, connectorDetails, scmConnector);
    } catch (Exception e) {
      log.error("Failed in executing delegate task", e);
      throw new ScmPathFilterTaskException(e.getMessage(), ErrorCode.DATA_PROCESSING_ERROR, WingsException.SRE);
    }
  }

  private ConnectorDetails getConnectorDetails(NGTriggerEntity ngTriggerEntity, WebhookMetadata webhook,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    ConnectorDetails connectorDetails;
    IdentifierRef ngAccess = IdentifierRef.builder()
                                 .accountIdentifier(ngTriggerEntity.getAccountId())
                                 .orgIdentifier(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier()
                                                                          : ngTriggerEntity.getOrgIdentifier())
                                 .projectIdentifier(isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier()
                                                                              : ngTriggerEntity.getProjectIdentifier())
                                 .build();

    if (webhook.getGit().getIsHarnessScm() == null || !webhook.getGit().getIsHarnessScm()) {
      connectorDetails = connectorUtils.getConnectorDetails(ngAccess, webhook.getGit().getConnectorIdentifier());
    } else {
      HarnessConnectorDTO connector =
          harnessCodeConnectorUtils.getDummyHarnessCodeConnectorWithJwtAuth(ngTriggerEntity.getAccountId(),
              isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
              isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
              webhook.getGit().getRepoName(), harnessCodeServiceSecret, harnessCodeApiUrl, harnessCodeGitBaseUrl, null);
      ConnectorDTO connectorDTO =
          ConnectorDTO.builder()
              .connectorInfo(ConnectorInfoDTO.builder()
                                 .identifier("HARNESS_SCM")
                                 .name("HARNESS_SCM")
                                 .projectIdentifier(isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier()
                                                                              : ngTriggerEntity.getProjectIdentifier())
                                 .orgIdentifier(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier()
                                                                          : ngTriggerEntity.getOrgIdentifier())
                                 .accountIdentifier(ngTriggerEntity.getAccountId())
                                 .connectorType(HARNESS)
                                 .connectorConfig(connector)
                                 .build())
              .build();
      connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorDTO);
    }
    return connectorDetails;
  }

  @VisibleForTesting
  void processAndCollectValidTriggerResponses(List<TriggerDetails> matchedTriggers,
      List<TriggerFilepathResponse> triggerFilepathResponses, List<TriggerDetails> triggerToEvaluate) {
    for (TriggerFilepathResponse triggerFilepathResponse : triggerFilepathResponses) {
      TriggerDetails triggerDetails =
          triggerToEvaluate.stream()
              .filter(trigger
                  -> trigger.getNgTriggerEntity().getUuid().equals(triggerFilepathResponse.getTriggerEntityId()))
              .findFirst()
              .orElse(null);
      if (triggerDetails == null) {
        log.warn(new StringBuilder(128)
                     .append(getTriggerSkipMessage(
                         NGTriggerEntity.builder().uuid(triggerFilepathResponse.getTriggerEntityId()).build()))
                     .append(", unable to find triggerDetails from Delegate Task response: ")
                     .toString());
        continue;
      }
      if (triggerFilepathResponse.getScmPathFilterEvaluationTaskResponse() == null) {
        log.warn(new StringBuilder(128)
                     .append(getTriggerSkipMessage(triggerDetails.getNgTriggerEntity()))
                     .append(", Null response from Delegate Task: ")
                     .toString());
      } else {
        if (isNotEmpty(triggerFilepathResponse.getScmPathFilterEvaluationTaskResponse().getErrorMessage())) {
          log.warn(new StringBuilder(128)
                       .append(getTriggerSkipMessage(triggerDetails.getNgTriggerEntity()))
                       .append(", Error Message from Delegate Task: ")
                       .append(triggerFilepathResponse.getScmPathFilterEvaluationTaskResponse().getErrorMessage())
                       .toString());
        }
        if (triggerFilepathResponse.getScmPathFilterEvaluationTaskResponse().isMatched()) {
          matchedTriggers.add(triggerDetails);
        }
      }
    }
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }
}

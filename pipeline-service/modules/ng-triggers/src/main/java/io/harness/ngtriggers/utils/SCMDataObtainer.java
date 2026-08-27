/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.FeatureName.PIPE_ENABLE_TRIGGER_ISSUE_COMMENT_COMMIT_FETCH;
import static io.harness.beans.FeatureName.PIPE_TRIGGER_FETCH_COMMITS_FROM_CONNECTORS;
import static io.harness.beans.FeatureName.PIPE_TRIGGER_SCM_FETCH_THROW_EXCEPTION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.connector.scm.adapter.AzureRepoToGitMapper.mapToGitConnectionType;
import static io.harness.delegate.beans.connector.utils.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.utils.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.utils.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.utils.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.utils.ConnectorType.GITLAB;
import static io.harness.delegate.beans.connector.utils.ConnectorType.HARNESS;

import static software.wings.beans.TaskType.SCM_GIT_REF_TASK;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.GitConfigDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.delegate.task.scm.GitRefType;
import io.harness.delegate.task.scm.ScmGitRefTaskParams;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.FailedToFetchCommitsException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.TriggerException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.WebhookConfigHelper;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.ListCommitsInPRResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.secrets.SecretDecryptor;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.service.ScmServiceClient;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.ConnectorUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Slf4j
@Singleton
@OwnedBy(CI)
public class SCMDataObtainer implements GitProviderBaseDataObtainer {
  private final TaskExecutionUtils taskExecutionUtils;
  private final ConnectorUtils connectorUtils;
  private final KryoSerializer kryoSerializer;
  private final KryoSerializer referenceFalseKryoSerializer;
  public static final String GIT_URL_SUFFIX = ".git";
  public static final String PATH_SEPARATOR = "/";
  public static final String AZURE_REPO_BASE_URL = "azure.com";
  private static final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private static final int MAX_ATTEMPTS = 3;
  @Inject private SCMGrpc.SCMBlockingStub scmBlockingStub;
  @Inject SecretDecryptor secretDecryptor;
  @Inject ScmServiceClient scmServiceClient;
  @Inject @Named("harnessCodeApiUrl") String harnessCodeApiUrl;
  @Inject @Named("harnessCodeGitBaseUrl") String harnessCodeGitBaseUrl;
  @Inject @Named("harnessCodeServiceSecret") String harnessCodeServiceSecret;
  @Inject HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Inject
  public SCMDataObtainer(TaskExecutionUtils taskExecutionUtils, ConnectorUtils connectorUtils,
      KryoSerializer kryoSerializer, @Named("referenceFalseKryoSerializer") KryoSerializer referenceFalseKryoSerializer,
      PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    this.taskExecutionUtils = taskExecutionUtils;
    this.connectorUtils = connectorUtils;
    this.kryoSerializer = kryoSerializer;
    this.referenceFalseKryoSerializer = referenceFalseKryoSerializer;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
  }

  @Override
  public void acquireProviderData(FilterRequestData filterRequestData, List<TriggerDetails> triggers,
      Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap, boolean isParentIdQueryingEnabled) {
    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();
    ParseWebhookResponse parseWebhookResponse = webhookPayloadData.getParseWebhookResponse();
    if (parseWebhookResponse.hasPr() || parseWebhookResponse.hasComment()) {
      acquirePullRequestCommits(filterRequestData, triggers, parentUniqueIdToScopeInfoMap, isParentIdQueryingEnabled);
    }
  }

  public String getGitURL(GitConnectionType connectionType, String url, String repoName) {
    String gitUrl = retrieveGenericGitConnectorURL(repoName, connectionType, url);

    if (!url.endsWith(GIT_URL_SUFFIX) && !url.contains(AZURE_REPO_BASE_URL)) {
      gitUrl += GIT_URL_SUFFIX;
    }
    return gitUrl;
  }

  public String getGitURL(ConnectorDetails connectorDetails, TriggerDetails triggerDetails) {
    ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 =
        (WebhookTriggerConfigV2) triggerDetails.getNgTriggerConfigV2().getSource().getSpec();
    GitAware gitAware = WebhookConfigHelper.retrieveGitAware(webhookTriggerConfigV2);
    String repoName = gitAware.fetchRepoName();
    return getGitURL(retrieveGitConnectionType(connectorDetails), scmConnector.getUrl(), repoName);
  }

  public String retrieveGenericGitConnectorURL(String repoName, GitConnectionType connectionType, String url) {
    String gitUrl = "";
    if (connectionType == GitConnectionType.REPO) {
      gitUrl = url;
    } else if (connectionType == GitConnectionType.PROJECT) {
      if (isEmpty(repoName)) {
        throw new IllegalArgumentException("Repo name is not set in trigger git connector spec");
      }
      if (url.contains(AZURE_REPO_BASE_URL)) {
        gitUrl = GitClientHelper.getCompleteUrlForProjectLevelAzureConnector(url, repoName);
      }
    } else if (connectionType == GitConnectionType.ACCOUNT) {
      if (isEmpty(repoName)) {
        throw new IllegalArgumentException("Repo name is not set in trigger git connector spec");
      }
      gitUrl = StringUtils.join(
          StringUtils.stripEnd(url, PATH_SEPARATOR), PATH_SEPARATOR, StringUtils.stripStart(repoName, PATH_SEPARATOR));
    } else {
      throw new InvalidArgumentsException(
          format("Invalid connection type for git connector: %s", connectionType.toString()), WingsException.USER);
    }

    return gitUrl;
  }

  public void acquirePullRequestCommits(FilterRequestData filterRequestData, List<TriggerDetails> triggers,
      Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap, boolean isParentIdQueryingEnabled) {
    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();
    ParseWebhookResponse parseWebhookResponse = webhookPayloadData.getParseWebhookResponse();
    if (parseWebhookResponse.hasPr()) {
      updatePayloadForPRWebhook(
          parseWebhookResponse, filterRequestData, triggers, parentUniqueIdToScopeInfoMap, isParentIdQueryingEnabled);
    } else if (parseWebhookResponse.hasComment()
        && pmsFeatureFlagHelper.isEnabled(
            filterRequestData.getAccountId(), PIPE_ENABLE_TRIGGER_ISSUE_COMMENT_COMMIT_FETCH)) {
      updatePayloadForIssueCommentWebhook(
          parseWebhookResponse, filterRequestData, triggers, parentUniqueIdToScopeInfoMap, isParentIdQueryingEnabled);
    }
  }

  private void updatePayloadForPRWebhook(ParseWebhookResponse parseWebhookResponse, FilterRequestData filterRequestData,
      List<TriggerDetails> triggers, Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap,
      boolean isParentIdQueryingEnabled) {
    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();
    PullRequestHook pullRequestHook = parseWebhookResponse.getPr();
    PullRequest pullRequest = pullRequestHook.getPr();

    List<Commit> commitsInPr = fetchCommits(triggers, pullRequest.getNumber(), filterRequestData.getAccountId(),
        parentUniqueIdToScopeInfoMap, isParentIdQueryingEnabled);

    PullRequest updatedPullRequest = pullRequest.toBuilder().addAllCommits(commitsInPr).build();
    PullRequestHook updatedPullRequestHook = pullRequestHook.toBuilder().setPr(updatedPullRequest).build();
    ParseWebhookResponse updatedParseWebhookResponse =
        parseWebhookResponse.toBuilder().setPr(updatedPullRequestHook).build();
    WebhookPayloadData updatedWebhookPayloadData =
        webhookPayloadData.toBuilder().parseWebhookResponse(updatedParseWebhookResponse).build();
    filterRequestData.setWebhookPayloadData(updatedWebhookPayloadData);
  }

  private void updatePayloadForIssueCommentWebhook(ParseWebhookResponse parseWebhookResponse,
      FilterRequestData filterRequestData, List<TriggerDetails> triggers,
      Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap, boolean isParentIdQueryingEnabled) {
    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();
    if (!parseWebhookResponse.hasComment() || parseWebhookResponse.getComment().getIssue() == null) {
      return;
    }
    PullRequest pullRequest = parseWebhookResponse.getComment().getIssue().getPr();
    long prNumber = parseWebhookResponse.getComment().getIssue().getNumber();
    List<Commit> commitsInPr = fetchCommits(
        triggers, prNumber, filterRequestData.getAccountId(), parentUniqueIdToScopeInfoMap, isParentIdQueryingEnabled);

    if (isEmpty(commitsInPr)) {
      log.warn("No commits found for PR number {} in issue comment trigger", prNumber);
      return;
    }

    // Store the fetched commits on the PR within the comment payload so that IssueCommentTriggerFilter
    // can forward them when it converts the comment event into a PullRequestHook.
    PullRequest updatedPullRequest = pullRequest.toBuilder().addAllCommits(commitsInPr).build();
    ParseWebhookResponse updatedParseWebhookResponse =
        parseWebhookResponse.toBuilder()
            .setComment(
                parseWebhookResponse.getComment()
                    .toBuilder()
                    .setIssue(
                        parseWebhookResponse.getComment().getIssue().toBuilder().setPr(updatedPullRequest).build())
                    .build())
            .build();
    WebhookPayloadData updatedWebhookPayloadData =
        webhookPayloadData.toBuilder().parseWebhookResponse(updatedParseWebhookResponse).build();
    filterRequestData.setWebhookPayloadData(updatedWebhookPayloadData);
  }

  private List<Commit> fetchCommits(List<TriggerDetails> triggers, long prNumber, String accountId,
      Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap, boolean isParentIdQueryingEnabled) {
    List<Commit> commitsInPr = new ArrayList<>();
    List<TriggerDetails> triggersToIterate;
    if (pmsFeatureFlagHelper.isEnabled(accountId, PIPE_TRIGGER_FETCH_COMMITS_FROM_CONNECTORS)) {
      triggersToIterate =
          triggers.stream()
              .filter(distinctByKey(
                  trigger -> trigger.getNgTriggerEntity().getMetadata().getWebhook().getGit().getConnectorIdentifier()))
              .toList();
    } else {
      triggersToIterate = triggers;
    }

    for (TriggerDetails triggerDetails : triggersToIterate) {
      try {
        ConnectorDetails connectorDetails;
        NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
        ScopeInfo scopeInfo = isParentIdQueryingEnabled
            ? parentUniqueIdToScopeInfoMap.getOrDefault(ngTriggerEntity.getParentUniqueId(), Optional.empty())
                  .orElse(null)
            : null;
        String orgIdentifier =
            isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier();
        String projectIdentifier =
            isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier();
        IdentifierRef ngAccess = IdentifierRef.builder()
                                     .accountIdentifier(ngTriggerEntity.getAccountId())
                                     .orgIdentifier(orgIdentifier)
                                     .projectIdentifier(projectIdentifier)
                                     .build();
        String connectorIdentifier = ngTriggerEntity.getMetadata().getWebhook().getGit().getConnectorIdentifier();
        Boolean isHarnessScm = Optional.of(ngTriggerEntity)
                                   .map(NGTriggerEntity::getMetadata)
                                   .map(NGTriggerMetadata::getWebhook)
                                   .map(WebhookMetadata::getGit)
                                   .map(GitMetadata::getIsHarnessScm)
                                   .orElse(null);
        if (Boolean.TRUE.equals(isHarnessScm)) {
          HarnessConnectorDTO connector =
              harnessCodeConnectorUtils.getDummyHarnessCodeConnectorWithJwtAuth(ngTriggerEntity.getAccountId(),
                  orgIdentifier, projectIdentifier, ngTriggerEntity.getMetadata().getWebhook().getGit().getRepoName(),
                  harnessCodeServiceSecret, harnessCodeApiUrl, harnessCodeGitBaseUrl, null);
          ConnectorDTO connectorDTO = ConnectorDTO.builder()
                                          .connectorInfo(ConnectorInfoDTO.builder()
                                                             .identifier("HARNESS_SCM")
                                                             .name("HARNESS_SCM")
                                                             .projectIdentifier(projectIdentifier)
                                                             .orgIdentifier(orgIdentifier)
                                                             .accountIdentifier(ngTriggerEntity.getAccountId())
                                                             .connectorType(HARNESS)
                                                             .connectorConfig(connector)
                                                             .build())
                                          .build();
          connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorDTO);
        } else {
          connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorIdentifier);
        }
        commitsInPr.addAll(getCommitsInPr(connectorDetails, triggerDetails, prNumber));
        break;
      } catch (Exception e) {
        log.error("Failed while fetching additional information from git provider for branch webhook event"
                + "Project : " + accountId + ", with Exception" + e.getMessage(),
            e);
        if (!pmsFeatureFlagHelper.isEnabled(accountId, PIPE_TRIGGER_SCM_FETCH_THROW_EXCEPTION)) {
          continue;
        }
        String failureMessage = format("Failed to fetch PR commits using connector [%s]. Reason: %s. "
                + "Verify the connector has API access enabled with permission to read PR commits.",
            triggerDetails.getNgTriggerEntity().getMetadata().getWebhook().getGit().getConnectorIdentifier(),
            e.getMessage());
        throw new FailedToFetchCommitsException(failureMessage, e);
      }
    }
    return commitsInPr;
  }

  private GitConnectionType retrieveGitConnectionType(ConnectorDetails gitConnector) {
    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
      return mapToGitConnectionType(gitConfigDTO.getConnectionType());
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getGitConnectionType();
    } else if (gitConnector.getConnectorType() == HARNESS) {
      return GitConnectionType.REPO;
    } else {
      throw new CIStageExecutionException("scmType " + gitConnector.getConnectorType() + "is not supported");
    }
  }

  List<Commit> getCommitsInPr(ConnectorDetails connectorDetails, TriggerDetails triggerDetails, long number) {
    ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();

    try {
      scmConnector.setUrl(getGitURL(connectorDetails, triggerDetails));
    } catch (Exception ex) {
      log.error("Failed to update url for connector [{}]", connectorDetails.getIdentifier(), ex);
    }

    ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                  .prNumber(number)
                                                  .gitRefType(GitRefType.PULL_REQUEST_COMMITS)
                                                  .encryptedDataDetails(connectorDetails.getEncryptedDataDetails())
                                                  .scmConnector(scmConnector)
                                                  .build();
    boolean executeOnDelegate =
        connectorDetails.getExecuteOnDelegate() == null || connectorDetails.getExecuteOnDelegate();

    if (executeOnDelegate) {
      return fetchPrCommitsViaDelegate(connectorDetails, scmGitRefTaskParams, triggerDetails);
    } else {
      return fetchPrCommitsViaManager(connectorDetails, scmGitRefTaskParams, triggerDetails);
    }
  }

  private List<Commit> fetchPrCommitsViaManager(
      ConnectorDetails connectorDetails, ScmGitRefTaskParams scmGitRefTaskParams, TriggerDetails triggerDetails) {
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        format("[Retrying failed call to fetch codebase metadata: [%s], attempt: {}", connectorDetails.getIdentifier()),
        format(
            "Failed call to fetch codebase metadata: [%s] after retrying {} times", connectorDetails.getIdentifier()));

    decrypt(scmGitRefTaskParams.getScmConnector(), connectorDetails.getEncryptedDataDetails());
    ListCommitsInPRResponse listCommitsInPRResponse =
        Failsafe.with(retryPolicy)
            .get(()
                     -> scmServiceClient.listCommitsInPR(
                         scmGitRefTaskParams.getScmConnector(), scmGitRefTaskParams.getPrNumber(), scmBlockingStub));

    return listCommitsInPRResponse.getCommitsList();
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .abortOn(ConnectorNotFoundException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  private void decrypt(ScmConnector connector, List<EncryptedDataDetail> encryptedDataDetails) {
    final DecryptableEntity decryptableEntity = secretDecryptor.decrypt(
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(connector), encryptedDataDetails);
    GitApiAccessDecryptionHelper.setAPIAccessDecryptableEntity(connector, decryptableEntity);
  }

  private List<Commit> fetchPrCommitsViaDelegate(
      ConnectorDetails connectorDetails, ScmGitRefTaskParams scmGitRefTaskParams, TriggerDetails triggerDetails) {
    if (ScmConnector.class.isAssignableFrom(connectorDetails.getConnectorConfig().getClass())) {
      RetryPolicy<Object> retryPolicy =
          getRetryPolicy(format("[Retrying failed call to fetch codebase metadata: [%s], attempt: {}",
                             connectorDetails.getIdentifier()),
              format("Failed call to fetch codebase metadata: [%s] after retrying {} times",
                  connectorDetails.getIdentifier()));

      ResponseData responseData = Failsafe.with(retryPolicy)
                                      .get(()
                                               -> taskExecutionUtils.executeSyncTask(
                                                   DelegateTaskRequest.builder()
                                                       .accountId(triggerDetails.getNgTriggerEntity().getAccountId())
                                                       .executionTimeout(Duration.ofSeconds(30))
                                                       .taskType(SCM_GIT_REF_TASK.name())
                                                       .taskParameters(scmGitRefTaskParams)
                                                       .build()));

      if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
        BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
        Object object = binaryResponseData.isUsingKryoWithoutReference()
            ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
            : kryoSerializer.asInflatedObject(binaryResponseData.getData());
        if (ScmGitRefTaskResponseData.class.isAssignableFrom(object.getClass())) {
          ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) object;
          try {
            return ListCommitsInPRResponse.parseFrom(scmGitRefTaskResponseData.getListCommitsInPRResponse())
                .getCommitsList();
          } catch (InvalidProtocolBufferException e) {
            throw new TriggerException("Unexpected error occurred while doing scm operation", WingsException.SRE);
          }
        } else if (object instanceof ErrorResponseData) {
          ErrorResponseData errorResponseData = (ErrorResponseData) object;
          throw new TriggerException(
              String.format("Failed to fetch commit details. Reason: %s", errorResponseData.getErrorMessage()),
              WingsException.SRE);
        }
      }
      throw new TriggerException("Failed to fetch commit details", WingsException.SRE);
    }
    return new ArrayList<>();
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }
}

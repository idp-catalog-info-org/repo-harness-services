/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.EXCEPTION_WHILE_PROCESSING;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.FAILED_TO_FETCH_PR_DETAILS;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS;

import static software.wings.beans.TaskType.SCM_GIT_REF_TASK;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.DelegateTaskRequest.DelegateTaskRequestBuilder;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.IssueCommentWebhookEvent;
import io.harness.beans.Repository;
import io.harness.beans.ScopeInfo;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.delegate.beans.gitapi.GitApiFindPRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiRequestType;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse;
import io.harness.delegate.beans.gitapi.GitRepoType;
import io.harness.delegate.task.scm.GitRefType;
import io.harness.delegate.task.scm.ScmGitRefTaskParams;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.TriggerException;
import io.harness.exception.WingsException;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.utils.SCMDataObtainer;
import io.harness.ngtriggers.utils.TaskExecutionUtils;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.FindPRResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequest.Builder;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.Reference;
import io.harness.product.ci.scm.proto.SCMGrpc.SCMBlockingStub;
import io.harness.product.ci.scm.proto.User;
import io.harness.runnercommons.cgi.task.git.RunnerGitRefTaskBuilder;
import io.harness.runnercommons.cgi.utils.UnifiedConditionChecker;
import io.harness.secrets.SecretDecryptor;
import io.harness.serializer.KryoSerializer;
import io.harness.service.ScmServiceClient;
import io.harness.service.WebhookParserSCMService;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.ConnectorUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class GithubIssueCommentTriggerFilter implements TriggerFilter {
  private ConnectorUtils connectorUtils;
  private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private RunnerGitRefTaskBuilder runnerGitRefTaskBuilder;
  private PayloadConditionsTriggerFilter payloadConditionsTriggerFilter;
  private PmsFeatureFlagService pmsFeatureFlagService;
  private SCMBlockingStub scmBlockingStub;
  private SCMDataObtainer scmDataObtainer;
  private ScmServiceClient scmServiceClient;
  private SecretDecryptor secretDecryptor;
  private TaskExecutionUtils taskExecutionUtils;
  private WebhookParserSCMService webhookParserSCMService;
  private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private UnifiedConditionChecker unifiedConditionChecker;
  @Inject private TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  public static final String PATH_SEPARATOR = "/";
  private static final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private static final int MAX_ATTEMPTS = 3;

  @Override
  public TriggerEventResponse getFailureResponse(FilterRequestData filterRequestData) {
    return TriggerEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_PAYLOAD_CONDITIONS,
        filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null, "Failed to fetch PR Details", null);
  }

  @Override
  public List<TriggerDetails> applyFilterV2(
      List<TriggerDetails> triggerDetailsList, FilterRequestData filterRequestData) throws Exception {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);
    Optional<PullRequest> optionalPullRequest = fetchPrDetailsFromGithub(filterRequestData);
    if (optionalPullRequest.isEmpty()) {
      return List.of();
    }
    try {
      PullRequest pullRequest = optionalPullRequest.get();

      // optionalPullRequest here is obtained using FindPRRequest, which doesn't contain commit details.
      // Hence, we are adding it from WebhookPayloadData
      List<Commit> commitsInPr = getPRCommits(filterRequestData);
      if (pullRequest.getCommitsList().size() == 0 && commitsInPr != null && commitsInPr.size() > 0) {
        pullRequest = PullRequest.newBuilder(pullRequest).addAllCommits(commitsInPr).build();
      }

      filterRequestData.setWebhookPayloadData(
          generateUpdateWebhookPayloadDataWithPrHook(filterRequestData, pullRequest, mappingResponseBuilder));
    } catch (Exception e) {
      log.error(String.format("Failed  while deserializing PR details for IssueComment event. Account: %s",
                    filterRequestData.getAccountId()),
          e);
      throw e;
    }

    return payloadConditionsTriggerFilter.applyFilterV2(triggerDetailsList, filterRequestData);
  }

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);
    Optional<PullRequest> optionalPullRequest = fetchPrDetailsFromGithub(filterRequestData);
    if (optionalPullRequest.isEmpty()) {
      return mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(FAILED_TO_FETCH_PR_DETAILS,
              filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null, "Failed to fetch PR Details",
              null))
          .build();
    }

    try {
      PullRequest pullRequest = optionalPullRequest.get();

      // optionalPullRequest here is obtained using FindPRRequest, which doesn't contain commit details.
      // Hence, we are adding it from WebhookPayloadData
      List<Commit> commitsInPr = getPRCommits(filterRequestData);
      if (pullRequest.getCommitsList().size() == 0 && commitsInPr != null && commitsInPr.size() > 0) {
        pullRequest = PullRequest.newBuilder(pullRequest).addAllCommits(commitsInPr).build();
      }

      filterRequestData.setWebhookPayloadData(
          generateUpdateWebhookPayloadDataWithPrHook(filterRequestData, pullRequest, mappingResponseBuilder));
    } catch (Exception e) {
      String errorMsg = new StringBuilder(128)
                            .append("Failed  while deserializing PR details for IssueComment event. ")
                            .append("Account : ")
                            .append(filterRequestData.getAccountId())
                            .append(", with Exception")
                            .append(e.getMessage())
                            .toString();
      log.error(errorMsg);
      return mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(EXCEPTION_WHILE_PROCESSING,
              filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
              "Failed to fetch PR Details: " + e, null))
          .build();
    }

    return payloadConditionsTriggerFilter.applyFilter(filterRequestData);
  }

  private List<Commit> getPRCommits(FilterRequestData filterRequestData) {
    try {
      return filterRequestData.getWebhookPayloadData()
          .getParseWebhookResponse()
          .getComment()
          .getIssue()
          .getPr()
          .getCommitsList();
    } catch (Exception e) {
      log.error("Exception while getting commits list ", e);
      return null;
    }
  }

  private WebhookPayloadData generateUpdateWebhookPayloadDataWithPrHook(FilterRequestData filterRequestData,
      PullRequest pullRequest, WebhookEventMappingResponseBuilder mappingResponseBuilder) throws Exception {
    ParseWebhookResponse originalParseWebhookResponse =
        filterRequestData.getWebhookPayloadData().getParseWebhookResponse();

    PullRequestHook pullRequestHook = PullRequestHook.newBuilder()
                                          .setRepo(originalParseWebhookResponse.getComment().getRepo())
                                          .setSender(originalParseWebhookResponse.getComment().getSender())
                                          .setPr(pullRequest)
                                          .build();

    ParseWebhookResponse newParseWebhookResponse =
        ParseWebhookResponse.newBuilder(originalParseWebhookResponse).setPr(pullRequestHook).build();

    mappingResponseBuilder.parseWebhookResponse(newParseWebhookResponse);
    WebhookPayloadData originalWebhookPayloadData = filterRequestData.getWebhookPayloadData();

    return WebhookPayloadData.builder()
        .repository(originalWebhookPayloadData.getRepository())
        .originalEvent(originalWebhookPayloadData.getOriginalEvent())
        .webhookGitUser(originalWebhookPayloadData.getWebhookGitUser())
        .parseWebhookResponse(newParseWebhookResponse)
        .webhookEvent(webhookParserSCMService.convertPRWebhookEvent(pullRequestHook))
        .build();
  }

  /**
   * Generates a PullRequest proto from the delegate JSON format.
   */
  private PullRequest generateProtoFromDelegateResponseJson(String prJson) {
    JsonNode productNode = JsonPipelineUtils.readTree(prJson);
    Builder builder = PullRequest.newBuilder();
    long prNum = productNode.get("number").longValue();
    builder.setNumber(prNum);
    builder.setTitle(productNode.get("title").textValue());
    builder.setSha(productNode.get("head").get("sha").textValue());
    builder.setRef(new StringBuilder(128).append("refs/pull/").append(prNum).append("/head").toString());

    String headRef = productNode.get("head").get("ref").textValue();
    builder.setSource(headRef);
    String baseRef = productNode.get("base").get("ref").textValue();
    builder.setTarget(baseRef);
    builder.setFork(productNode.get("head").get("repo").get("full_name").textValue());

    builder.setLink(productNode.get("html_url").textValue());
    builder.setClosed(!"open".equalsIgnoreCase(productNode.get("state").textValue()));
    builder.setMerged(productNode.get("merged_at") != null && isNotBlank(productNode.get("merged_at").textValue()));

    builder.setHead(Reference.newBuilder()
                        .setSha(productNode.get("head").get("sha").textValue())
                        .setName(headRef)
                        .setPath(expandRef(headRef))
                        .build());

    builder.setBase(Reference.newBuilder()
                        .setSha(productNode.get("base").get("sha").textValue())
                        .setName(baseRef)
                        .setPath(expandRef(baseRef))
                        .build());

    builder.setAuthor(User.newBuilder()
                          .setLogin(productNode.get("user").get("login").textValue())
                          .setAvatar(productNode.get("user").get("avatar_url").textValue())
                          .build());

    return builder.build();
  }

  private String expandRef(String name) {
    if (name.startsWith("refs/")) {
      return name;
    }

    return "refs/heads/" + name;
  }

  private Optional<PullRequest> fetchPrDetailsFromGithub(FilterRequestData filterRequestData) {
    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();

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

    for (TriggerDetails details : filterRequestData.getDetails()) {
      try {
        String connectorIdentifier =
            details.getNgTriggerEntity().getMetadata().getWebhook().getGit().getConnectorIdentifier();
        ScopeInfo scopeInfo = isParentIdQueryingEnabled
            ? parentUniqueIdToScopeInfoMap
                  .getOrDefault(details.getNgTriggerEntity().getParentUniqueId(), Optional.empty())
                  .orElse(null)
            : null;
        ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(
            IdentifierRef.builder()
                .accountIdentifier(details.getNgTriggerEntity().getAccountId())
                .orgIdentifier(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier()
                                                         : details.getNgTriggerEntity().getOrgIdentifier())
                .projectIdentifier(isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier()
                                                             : details.getNgTriggerEntity().getProjectIdentifier())
                .build(),
            connectorIdentifier);
        boolean executeOnDelegate =
            connectorDetails.getExecuteOnDelegate() == null || connectorDetails.getExecuteOnDelegate();
        // If not executing on delegate, use the manager directly
        if (!executeOnDelegate) {
          return Optional.of(getPrJsonDetailsViaManager(connectorDetails, webhookPayloadData));
        }
        // Check if we should use the unified runner task
        boolean useUnifiedTask = unifiedConditionChecker.shouldUseUnifiedFlow(filterRequestData.getAccountId(), true);

        if (pmsFeatureFlagService.isEnabled(
                filterRequestData.getAccountId(), FeatureName.CDS_NG_USE_SCM_FOR_PR_DETAILS_ON_ISSUE_COMMENT_TRIGGER)
            || useUnifiedTask) {
          // Use unified runner task
          return Optional.of(
              getPullRequestDetailsWithScm(webhookPayloadData, details, connectorDetails, useUnifiedTask));
        }
        // Use old Git API task
        GitApiTaskResponse taskResponse = buildAndFireTask(webhookPayloadData, details, connectorDetails);
        if (taskResponse.getCommandExecutionStatus() == SUCCESS) {
          GitApiFindPRTaskResponse gitApiResult = (GitApiFindPRTaskResponse) taskResponse.getGitApiResult();
          return Optional.of(generateProtoFromDelegateResponseJson(gitApiResult.getPrJson()));
        }
      } catch (Exception e) {
        log.error(new StringBuilder(128)
                      .append("Failed  while deserializing PR details for IssueComment event. ")
                      .append("Account : ")
                      .append(filterRequestData.getAccountId())
                      .append(", with Exception")
                      .append(e.getMessage())
                      .toString(),
            e);
      }
    }
    return Optional.empty();
  }

  private GitApiTaskResponse buildAndFireTask(
      WebhookPayloadData webhookPayloadData, TriggerDetails details, ConnectorDetails connectorDetails) {
    Repository repository = webhookPayloadData.getRepository();

    DelegateTaskRequestBuilder delegateTaskRequestBuilder =
        DelegateTaskRequest.builder()
            .accountId(details.getNgTriggerEntity().getAccountId())
            .executionTimeout(Duration.ofSeconds(30))
            .taskType("GIT_API_TASK")
            .taskParameters(
                GitApiTaskParams.builder()
                    .gitRepoType(GitRepoType.GITHUB)
                    .requestType(GitApiRequestType.FIND_PULL_REQUEST_DETAILS)
                    .connectorDetails(connectorDetails)
                    .prNumber(((IssueCommentWebhookEvent) webhookPayloadData.getWebhookEvent()).getPullRequestNum())
                    .slug(repository.getSlug())
                    .owner(repository.getNamespace())
                    .repo(repository.getName())
                    .build())
            .taskSetupAbstraction(NG, "true");

    String owner = taskSetupAbstractionHelper.getOwner(details.getNgTriggerEntity().getAccountId(),
        connectorDetails.getOrgIdentifier(), connectorDetails.getProjectIdentifier());
    if (isNotEmpty(owner)) {
      delegateTaskRequestBuilder.taskSetupAbstraction(OWNER, owner);
    }
    if (connectorDetails.getOrgIdentifier() != null) {
      delegateTaskRequestBuilder.taskSetupAbstraction("orgIdentifier", connectorDetails.getOrgIdentifier());
    }
    if (connectorDetails.getProjectIdentifier() != null) {
      delegateTaskRequestBuilder.taskSetupAbstraction("projectIdentifier", connectorDetails.getProjectIdentifier());
    }
    if (connectorDetails.getDelegateSelectors() != null) {
      delegateTaskRequestBuilder.taskSelectors(connectorDetails.getDelegateSelectors());
    }

    ResponseData responseData = taskExecutionUtils.executeSyncTask(delegateTaskRequestBuilder.build());

    if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
      BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
      Object object = binaryResponseData.isUsingKryoWithoutReference()
          ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
          : kryoSerializer.asInflatedObject(binaryResponseData.getData());
      return handleTaskResponse(object);
    }
    throw new TriggerException("Failed to fetch PR Details", WingsException.SRE);
  }

  private GitApiTaskResponse handleTaskResponse(Object object) {
    if (object instanceof GitApiTaskResponse gitApiTaskResponse) {
      if (gitApiTaskResponse.getGitApiResult() == null && isNotEmpty(gitApiTaskResponse.getErrorMessage())) {
        throw new TriggerException(
            String.format("Failed to fetch PR Details. Reason: " + gitApiTaskResponse.getErrorMessage()),
            WingsException.SRE);
      }
      return gitApiTaskResponse;
    } else if (object instanceof ErrorResponseData errorResponseData) {
      throw new TriggerException(
          String.format("Failed to fetch PR Details. Reason: {}", errorResponseData.getErrorMessage()),
          WingsException.SRE);
    }
    throw new TriggerException("Failed to fetch PR Details", WingsException.SRE);
  }

  private PullRequest getPullRequestDetailsWithScm(WebhookPayloadData webhookPayloadData, TriggerDetails details,
      ConnectorDetails connectorDetails, boolean useUnifiedTask) {
    ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();
    scmConnector.setUrl(scmDataObtainer.getGitURL(connectorDetails, details));
    ScmGitRefTaskParams scmGitRefTaskParams =
        ScmGitRefTaskParams.builder()
            .prNumber(
                Long.parseLong(((IssueCommentWebhookEvent) webhookPayloadData.getWebhookEvent()).getPullRequestNum()))
            .gitRefType(GitRefType.PULL_REQUEST)
            .encryptedDataDetails(connectorDetails.getEncryptedDataDetails())
            .scmConnector(scmConnector)
            .build();
    DelegateTaskRequestBuilder delegateTaskRequestBuilder = DelegateTaskRequest.builder()
                                                                .accountId(details.getNgTriggerEntity().getAccountId())
                                                                .executionTimeout(Duration.ofSeconds(30))
                                                                .taskType(SCM_GIT_REF_TASK.name())
                                                                .taskParameters(scmGitRefTaskParams)
                                                                .taskSetupAbstraction(NG, "true");

    String owner = taskSetupAbstractionHelper.getOwner(details.getNgTriggerEntity().getAccountId(),
        connectorDetails.getOrgIdentifier(), connectorDetails.getProjectIdentifier());
    if (isNotEmpty(owner)) {
      delegateTaskRequestBuilder.taskSetupAbstraction(OWNER, owner);
    }
    if (connectorDetails.getOrgIdentifier() != null) {
      delegateTaskRequestBuilder.taskSetupAbstraction("orgIdentifier", connectorDetails.getOrgIdentifier());
    }
    if (connectorDetails.getProjectIdentifier() != null) {
      delegateTaskRequestBuilder.taskSetupAbstraction("projectIdentifier", connectorDetails.getProjectIdentifier());
    }
    if (connectorDetails.getDelegateSelectors() != null) {
      delegateTaskRequestBuilder.taskSelectors(connectorDetails.getDelegateSelectors());
    }
    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestBuilder.build();

    Object taskResponse = null;
    if (useUnifiedTask) {
      // Fetch PR details via Unified Runner Task API
      try {
        taskResponse = runnerGitRefTaskBuilder.sendRefTask(scmGitRefTaskParams, delegateTaskRequest,
            connectorDetails.getOrgIdentifier(), connectorDetails.getProjectIdentifier());
      } catch (Exception e) {
        log.error("Failed to fetch PR Details using Unified Task API", e);
      }
    } else {
      // Fetch PR details via regular Delegate task
      ResponseData responseData = taskExecutionUtils.executeSyncTask(delegateTaskRequest);
      if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
        BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
        taskResponse = binaryResponseData.isUsingKryoWithoutReference()
            ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
            : kryoSerializer.asInflatedObject(binaryResponseData.getData());
      }
    }
    return handleSCMTaskResponse(taskResponse);
  }

  private static PullRequest handleSCMTaskResponse(Object taskResponse) {
    if (taskResponse == null) {
      throw new TriggerException("Failed to fetch PR Details. Reason: Null task response", WingsException.SRE);
    }
    if (ScmGitRefTaskResponseData.class.isAssignableFrom(taskResponse.getClass())) {
      ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) taskResponse;
      try {
        return FindPRResponse.parseFrom(scmGitRefTaskResponseData.getFindPRResponse()).getPr();
      } catch (InvalidProtocolBufferException e) {
        throw new TriggerException("Failed to fetch PR Details. Reason: " + e.getMessage(), WingsException.SRE);
      }
    } else if (taskResponse instanceof ErrorResponseData errorResponseData) {
      throw new TriggerException(
          "Failed to fetch PR Details. Reason: " + errorResponseData.getErrorMessage(), WingsException.SRE);
    }
    throw new TriggerException("Failed to fetch PR Details", WingsException.SRE);
  }

  private PullRequest getPrJsonDetailsViaManager(
      ConnectorDetails connectorDetails, WebhookPayloadData webhookPayloadData) {
    ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();
    GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) connectorDetails.getConnectorConfig();
    Repository repository = webhookPayloadData.getRepository();
    scmConnector.setUrl(getGithubUrl(scmConnector.getUrl(), repository.getName(), gitConfigDTO.getConnectionType()));
    final DecryptableEntity decryptableEntity =
        secretDecryptor.decrypt(GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmConnector),
            connectorDetails.getEncryptedDataDetails());
    GitApiAccessDecryptionHelper.setAPIAccessDecryptableEntity(scmConnector, decryptableEntity);
    long prNumber =
        Long.parseLong(((IssueCommentWebhookEvent) webhookPayloadData.getWebhookEvent()).getPullRequestNum());
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        format("[Retrying failed call to fetch codebase metadata: [%s], attempt: {}", connectorDetails.getIdentifier()),
        format(
            "Failed call to fetch codebase metadata: [%s] after retrying {} times", connectorDetails.getIdentifier()));
    FindPRResponse findPRResponse =
        Failsafe.with(retryPolicy).get(() -> scmServiceClient.findPR(scmConnector, prNumber, scmBlockingStub));
    return findPRResponse.getPr();
  }

  private String getGithubUrl(String url, String repo, GitConnectionType gitConnectionType) {
    if (gitConnectionType == GitConnectionType.ACCOUNT && isNotEmpty(repo)) {
      return StringUtils.join(
          StringUtils.stripEnd(url, PATH_SEPARATOR), PATH_SEPARATOR, StringUtils.stripStart(repo, PATH_SEPARATOR));
    }
    return url;
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
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.AWS_CODECOMMIT;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.AZURE;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.BITBUCKET;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.GITHUB;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.GITLAB;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.HARNESS;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.HARNESS_ARTIFACT_REGISTRY;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.WebhookAction;
import io.harness.ngtriggers.beans.source.webhook.WebhookEvent;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.event.AwsCodeCommitTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.action.AzureRepoIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.action.AzureRepoPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.event.AzureRepoTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAction;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.git.PayloadAware;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPullRequestReviewAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubReleaseAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabMRCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.action.HarnessBranchAction;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.action.HarnessPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.action.HarnessTagAction;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.event.HarnessTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.action.ArtifactEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.action.HarArtifactAction;
import io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.event.HarTriggerEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class WebhookConfigHelper {
  public Map<WebhookSourceRepo, List<WebhookEvent>> getSourceRepoToEvent() {
    Map<WebhookSourceRepo, List<WebhookEvent>> map = new HashMap<>();
    map.put(WebhookSourceRepo.GITHUB, new ArrayList<>(WebhookEvent.githubEvents));
    map.put(WebhookSourceRepo.GITLAB, new ArrayList<>(WebhookEvent.gitlabEvents));
    map.put(WebhookSourceRepo.BITBUCKET, new ArrayList<>(WebhookEvent.bitbucketEvents));
    map.put(WebhookSourceRepo.AWS_CODECOMMIT, new ArrayList<>(WebhookEvent.awsCodeCommitEvents));
    map.put(WebhookSourceRepo.HARNESS, new ArrayList<>(WebhookEvent.harnessScmEvents));

    return map;
  }

  public List<GithubTriggerEvent> getGithubTriggerEvents() {
    return Arrays.asList(GithubTriggerEvent.PUSH, GithubTriggerEvent.PULL_REQUEST, GithubTriggerEvent.ISSUE_COMMENT);
  }

  public List<GitlabTriggerEvent> getGitlabTriggerEvents() {
    return Arrays.asList(GitlabTriggerEvent.PUSH, GitlabTriggerEvent.MERGE_REQUEST, GitlabTriggerEvent.MR_COMMENT);
  }

  public List<BitbucketTriggerEvent> getBitbucketTriggerEvents() {
    return Arrays.asList(
        BitbucketTriggerEvent.PUSH, BitbucketTriggerEvent.PULL_REQUEST, BitbucketTriggerEvent.PR_COMMENT);
  }

  public List<WebhookAction> getActionsList(WebhookSourceRepo sourceRepo, WebhookEvent event) {
    if (sourceRepo == WebhookSourceRepo.GITHUB) {
      return new ArrayList<>(WebhookAction.getGithubActionForEvent(event));
    } else if (sourceRepo == WebhookSourceRepo.BITBUCKET) {
      return new ArrayList<>(WebhookAction.getBitbucketActionForEvent(event));
    } else if (sourceRepo == WebhookSourceRepo.GITLAB) {
      return new ArrayList<>(WebhookAction.getGitLabActionForEvent(event));
    } else if (sourceRepo == WebhookSourceRepo.AWS_CODECOMMIT) {
      return new ArrayList<>(WebhookAction.getAwsCodeCommitActionForEvent(event));
    } else if (sourceRepo == WebhookSourceRepo.HARNESS) {
      return new ArrayList<>(WebhookAction.getHarnessScmActionForEvent(event));
    } else {
      return emptyList();
    }
  }

  public GitAware retrieveGitAware(WebhookTriggerConfigV2 webhookTriggerConfig) {
    if (!isGitSpec(webhookTriggerConfig)) {
      return null;
    }

    return webhookTriggerConfig.getSpec().fetchGitAware();
  }

  public PayloadAware retrievePayloadAware(WebhookTriggerConfigV2 webhookTriggerConfig) {
    // [CI-23187] Custom triggers with no conditions have a null inner spec. Guard against null-deref.
    if (webhookTriggerConfig == null || webhookTriggerConfig.getSpec() == null) {
      return null;
    }
    return webhookTriggerConfig.getSpec().fetchPayloadAware();
  }

  public List<TriggerEventDataCondition> retrieveHeaderConditions(WebhookTriggerConfigV2 webhookTriggerConfig) {
    PayloadAware payloadAware = retrievePayloadAware(webhookTriggerConfig);
    if (payloadAware != null) {
      return payloadAware.fetchHeaderConditions();
    }

    return emptyList();
  }

  public boolean isGitSpec(WebhookTriggerConfigV2 webhookTriggerConfig) {
    return webhookTriggerConfig.getType() == GITHUB || webhookTriggerConfig.getType() == GITLAB
        || webhookTriggerConfig.getType() == BITBUCKET || webhookTriggerConfig.getType() == AWS_CODECOMMIT
        || webhookTriggerConfig.getType() == AZURE || webhookTriggerConfig.getType() == HARNESS;
  }

  public static List<AzureRepoPRAction> getAzureRepoPRAction() {
    return Arrays.asList(AzureRepoPRAction.values());
  }

  public static List<GithubPRAction> getGithubPRAction() {
    return Arrays.asList(GithubPRAction.values());
  }

  public static List<GithubPullRequestReviewAction> getGithubPullRequestReviewAction() {
    return Arrays.asList(GithubPullRequestReviewAction.values());
  }

  public static List<HarnessPRAction> getHarnessPRAction() {
    return Arrays.asList(HarnessPRAction.values());
  }

  public static List<HarnessBranchAction> getHarnessBranchAction() {
    return Arrays.asList(HarnessBranchAction.values());
  }

  public static List<HarnessTagAction> getHarnessTagAction() {
    return Arrays.asList(HarnessTagAction.values());
  }

  public static List<GithubIssueCommentAction> getGithubIssueCommentAction() {
    return Arrays.asList(GithubIssueCommentAction.values());
  }

  public static List<GithubReleaseAction> getGithubReleaseAction() {
    return Arrays.asList(GithubReleaseAction.values());
  }

  public static List<AzureRepoIssueCommentAction> getAzureRepoIssueCommentAction() {
    return Arrays.asList(AzureRepoIssueCommentAction.values());
  }

  public static List<GitlabPRAction> getGitlabPRAction() {
    return Arrays.asList(GitlabPRAction.values());
  }

  public static List<GitlabMRCommentAction> getGitlabMRCommentAction() {
    return Arrays.asList(GitlabMRCommentAction.values());
  }

  public static List<BitbucketPRAction> getBitbucketPRAction() {
    return Arrays.asList(BitbucketPRAction.values());
  }

  public static List<BitbucketPRCommentAction> getBitbucketPRCommentAction() {
    return Arrays.asList(BitbucketPRCommentAction.values());
  }

  public static List<WebhookTriggerType> getWebhookTriggerType() {
    return Arrays.asList(WebhookTriggerType.values());
  }

  public static List<HarArtifactAction> getHarArtifactAction() {
    return Arrays.asList(HarArtifactAction.values());
  }

  public static Map<String, Map<String, List<String>>> getGitTriggerEventDetails() {
    Map<String, Map<String, List<String>>> responseMap = new HashMap<>();

    Map azureRepoMap = new HashMap<GitEvent, List<GitAction>>();
    responseMap.put(AZURE.getValue(), azureRepoMap);
    azureRepoMap.put(AzureRepoTriggerEvent.PUSH.getValue(), emptyList());
    azureRepoMap.put(AzureRepoTriggerEvent.PULL_REQUEST.getValue(),
        getAzureRepoPRAction().stream().map(azureRepoPRAction -> azureRepoPRAction.getValue()).collect(toList()));
    azureRepoMap.put(AzureRepoTriggerEvent.ISSUE_COMMENT.getValue(),
        getAzureRepoIssueCommentAction()
            .stream()
            .map(azureRepoIssueCommentAction -> azureRepoIssueCommentAction.getValue())
            .collect(toList()));

    Map githubMap = new HashMap<GitEvent, List<GitAction>>();
    responseMap.put(GITHUB.getValue(), githubMap);
    githubMap.put(GithubTriggerEvent.PUSH.getValue(), emptyList());
    githubMap.put(GithubTriggerEvent.PULL_REQUEST.getValue(),
        getGithubPRAction().stream().map(githubPRAction -> githubPRAction.getValue()).collect(toList()));
    githubMap.put(GithubTriggerEvent.ISSUE_COMMENT.getValue(),
        getGithubIssueCommentAction()
            .stream()
            .map(githubIssueCommentAction -> githubIssueCommentAction.getValue())
            .collect(toList()));
    githubMap.put(GithubTriggerEvent.RELEASE.getValue(),
        getGithubReleaseAction().stream().map(githubReleaseAction -> githubReleaseAction.getValue()).collect(toList()));
    githubMap.put(GithubTriggerEvent.DELETE.getValue(), emptyList());
    githubMap.put(GithubTriggerEvent.CREATE.getValue(), emptyList());
    githubMap.put(GithubTriggerEvent.MERGE_QUEUE.getValue(), emptyList());
    githubMap.put(GithubTriggerEvent.PULL_REQUEST_REVIEW.getValue(),
        getGithubPullRequestReviewAction()
            .stream()
            .map(githubPullRequestReviewAction -> githubPullRequestReviewAction.getValue())
            .collect(toList()));

    Map harnessMap = new HashMap<GitEvent, List<GitAction>>();
    responseMap.put(HARNESS.getValue(), harnessMap);
    harnessMap.put(HarnessTriggerEvent.PUSH.getValue(), emptyList());
    harnessMap.put(HarnessTriggerEvent.MERGE_QUEUE.getValue(), emptyList());
    harnessMap.put(HarnessTriggerEvent.BRANCH.getValue(),
        getHarnessBranchAction().stream().map(HarnessBranchAction::getValue).collect(toList()));
    harnessMap.put(HarnessTriggerEvent.TAG.getValue(),
        getHarnessTagAction().stream().map(HarnessTagAction::getValue).collect(toList()));
    harnessMap.put(HarnessTriggerEvent.PULL_REQUEST.getValue(),
        getHarnessPRAction().stream().map(harnessPRAction -> harnessPRAction.getValue()).collect(toList()));

    Map gitlabMap = new HashMap<GitEvent, List<GitAction>>();
    responseMap.put(GITLAB.getValue(), gitlabMap);
    gitlabMap.put(GitlabTriggerEvent.PUSH.getValue(), emptyList());
    gitlabMap.put(GitlabTriggerEvent.MERGE_REQUEST.getValue(),
        getGitlabPRAction().stream().map(gitlabPRAction -> gitlabPRAction.getValue()).collect(toList()));
    gitlabMap.put(GitlabTriggerEvent.MR_COMMENT.getValue(),
        getGitlabMRCommentAction()
            .stream()
            .map(gitlabMRCommentAction -> gitlabMRCommentAction.getValue())
            .collect(toList()));
    gitlabMap.put(GitlabTriggerEvent.TAG.getValue(), emptyList());
    gitlabMap.put(GitlabTriggerEvent.PIPELINE_HOOK.getValue(), emptyList());

    Map bitbucketMap = new HashMap<GitEvent, List<GitAction>>();
    responseMap.put(BITBUCKET.getValue(), bitbucketMap);
    bitbucketMap.put(BitbucketTriggerEvent.PUSH.getValue(), emptyList());
    bitbucketMap.put(BitbucketTriggerEvent.PULL_REQUEST.getValue(),
        getBitbucketPRAction().stream().map(bitbucketPRAction -> bitbucketPRAction.getValue()).collect(toList()));
    bitbucketMap.put(BitbucketTriggerEvent.PR_COMMENT.getValue(),
        getBitbucketPRCommentAction()
            .stream()
            .map(bitbucketPRCommentAction -> bitbucketPRCommentAction.getValue())
            .collect(toList()));

    Map awsCodeCommitMap = new HashMap<GitEvent, List<GitAction>>();
    responseMap.put(AWS_CODECOMMIT.getValue(), awsCodeCommitMap);
    awsCodeCommitMap.put(AwsCodeCommitTriggerEvent.PUSH.getValue(), emptyList());

    Map harnessArtifactRegistryMap = new HashMap<GitEvent, List<ArtifactEvent>>();
    responseMap.put(HARNESS_ARTIFACT_REGISTRY.getValue(), harnessArtifactRegistryMap);
    harnessArtifactRegistryMap.put(HarTriggerEvent.ARTIFACT.getValue(),
        getHarArtifactAction().stream().map(artifactEvent -> artifactEvent.getValue()).collect(toList()));

    return responseMap;
  }
}

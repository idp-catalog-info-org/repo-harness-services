/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.source.v1.artifact.WebhookTriggerYamlSimplConfig;
import io.harness.ngtriggers.beans.source.v1.webhook.WebhookTriggerYamlSimplType;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.event.AwsCodeCommitEventSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.event.AwsCodeCommitPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.event.AwsCodeCommitTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.action.AzureRepoIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.action.AzureRepoPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.event.AzureRepoEventSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.event.AzureRepoIssueCommentSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.event.AzureRepoPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.event.AzureRepoPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.event.AzureRepoTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketEventSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketPRCommentSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubReleaseAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubEventSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubIssueCommentSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubReleaseSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabMRCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabEventSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabMRCommentSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.action.HarnessPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.event.HarnessEventSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.event.HarnessPRSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.event.HarnessPushSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.event.HarnessTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.AwsCodeCommitSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.AzureRepoSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.BitbucketSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.CustomTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.GithubSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.GitlabSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.HarnessSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.WebhookTriggerSpecV2;

import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.PIPELINE)
public class NGWebhookTriggerElementMapper {
  WebhookTriggerType toWebhookTriggerType(WebhookTriggerYamlSimplType typeEnum) {
    switch (typeEnum) {
      case GITHUB:
        return WebhookTriggerType.GITHUB;
      case GITLAB:
        return WebhookTriggerType.GITLAB;
      case AZURE:
        return WebhookTriggerType.AZURE;
      case BITBUCKET:
        return WebhookTriggerType.BITBUCKET;
      case AWS_CODECOMMIT:
        return WebhookTriggerType.AWS_CODECOMMIT;
      case CUSTOM:
        return WebhookTriggerType.CUSTOM;
      case HARNESS:
        return WebhookTriggerType.HARNESS;
      default:
        throw new InvalidRequestException("Webhook Trigger Type " + typeEnum + " is invalid");
    }
  }

  WebhookTriggerSpecV2 toWebhookTriggerSpec(WebhookTriggerYamlSimplConfig spec) {
    switch (spec.getType()) {
      case GITHUB:
        io.harness.ngtriggers.beans.source.v1.webhook.spec.GithubSpec githubWebhookSpec =
            (io.harness.ngtriggers.beans.source.v1.webhook.spec.GithubSpec) spec.getSpec();
        return GithubSpec.builder()
            .type(toGithubTriggerEvent(githubWebhookSpec.getType()))
            .spec(toGithubEventSpec(githubWebhookSpec))
            .build();
      case GITLAB:
        io.harness.ngtriggers.beans.source.v1.webhook.spec.GitlabSpec gitlabWebhookSpec =
            (io.harness.ngtriggers.beans.source.v1.webhook.spec.GitlabSpec) spec.getSpec();
        return GitlabSpec.builder()
            .type(toGitlabTriggerEvent(gitlabWebhookSpec.getType()))
            .spec(toGitlabEventSpec(gitlabWebhookSpec))
            .build();
      case BITBUCKET:
        io.harness.ngtriggers.beans.source.v1.webhook.spec.BitbucketSpec bitbucketWebhookTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.webhook.spec.BitbucketSpec) spec.getSpec();
        return BitbucketSpec.builder()
            .type(toBitbucketTriggerEvent(bitbucketWebhookTriggerSpec.getType()))
            .spec(toBitbucketEventSpec(bitbucketWebhookTriggerSpec))
            .build();
      case AWS_CODECOMMIT:
        io.harness.ngtriggers.beans.source.v1.webhook.spec.AwsCodeCommitSpec awsCodeCommitTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.webhook.spec.AwsCodeCommitSpec) spec.getSpec();
        return AwsCodeCommitSpec.builder()
            .type(toAwsCodeCommitTriggerEvent(awsCodeCommitTriggerSpec.getType()))
            .spec(toAwsCodeCommitEventSpec(awsCodeCommitTriggerSpec))
            .build();
      case AZURE:
        io.harness.ngtriggers.beans.source.v1.webhook.spec.AzureRepoSpec azureRepoWebhookTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.webhook.spec.AzureRepoSpec) spec.getSpec();
        return AzureRepoSpec.builder()
            .type(toAzureRepoTriggerEvent(azureRepoWebhookTriggerSpec.getType()))
            .spec(toAzureRepoEventSpec(azureRepoWebhookTriggerSpec))
            .build();
      case HARNESS:
        io.harness.ngtriggers.beans.source.v1.webhook.spec.HarnessSpec harnessWebhookTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.webhook.spec.HarnessSpec) spec.getSpec();
        return HarnessSpec.builder()
            .type(toHarnessTriggerEvent(harnessWebhookTriggerSpec.getType()))
            .spec(toHarnessEventSpec(harnessWebhookTriggerSpec))
            .build();
      case CUSTOM:
        io.harness.ngtriggers.beans.source.v1.webhook.spec.CustomTriggerSpec customWebhookSpec =
            (io.harness.ngtriggers.beans.source.v1.webhook.spec.CustomTriggerSpec) spec.getSpec();
        return CustomTriggerSpec.builder()
            .payloadConditions(customWebhookSpec.fetchPayloadConditions())
            .headerConditions(customWebhookSpec.fetchHeaderConditions())
            .jexlCondition(customWebhookSpec.fetchJexlCondition())
            .build();
      default:
        throw new InvalidRequestException("Webhook Trigger Type " + spec.getType() + " is invalid");
    }
  }

  BitbucketEventSpec toBitbucketEventSpec(io.harness.ngtriggers.beans.source.v1.webhook.spec.BitbucketSpec spec) {
    switch (spec.getType()) {
      case PUSH:
        return BitbucketPushSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .build();
      case PR_COMMENT:

        return BitbucketPRCommentSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .actions(spec.fetchGitAware()
                         .fetchActions()
                         .stream()
                         .map(this::toBitbucketPRCommentAction)
                         .collect(Collectors.toList()))
            .build();
      case PULL_REQUEST:
        return BitbucketPRSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .actions(spec.fetchGitAware()
                         .fetchActions()
                         .stream()
                         .map(this::toBitbucketPRAction)
                         .collect(Collectors.toList()))
            .build();
      default:
        throw new InvalidRequestException("Bitbucket Webhook Trigger Event Type " + spec.getType() + " is invalid");
    }
  }

  AwsCodeCommitEventSpec toAwsCodeCommitEventSpec(
      io.harness.ngtriggers.beans.source.v1.webhook.spec.AwsCodeCommitSpec spec) {
    switch (spec.getType()) {
      case PUSH:
        return AwsCodeCommitPushSpec.builder()
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .build();
      default:
        throw new InvalidRequestException(
            "Aws Code Commit Webhook Trigger Event Type " + spec.getType() + " is invalid");
    }
  }

  AzureRepoEventSpec toAzureRepoEventSpec(io.harness.ngtriggers.beans.source.v1.webhook.spec.AzureRepoSpec spec) {
    switch (spec.getType()) {
      case ISSUE_COMMENT:
        return AzureRepoIssueCommentSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .actions(spec.fetchGitAware()
                         .fetchActions()
                         .stream()
                         .map(this::toAzureRepoIssueCommentAction)
                         .collect(Collectors.toList()))
            .build();
      case PUSH:
        return AzureRepoPushSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .build();
      case PULL_REQUEST:
        return AzureRepoPRSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .actions(spec.fetchGitAware()
                         .fetchActions()
                         .stream()
                         .map(this::toAzureRepoPRAction)
                         .collect(Collectors.toList()))
            .build();
      default:
        throw new InvalidRequestException("Azure Repo Webhook Trigger Event Type " + spec.getType() + " is invalid");
    }
  }

  HarnessEventSpec toHarnessEventSpec(io.harness.ngtriggers.beans.source.v1.webhook.spec.HarnessSpec spec) {
    switch (spec.getType()) {
      case PULL_REQUEST:
        return HarnessPRSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .actions(
                spec.fetchGitAware().fetchActions().stream().map(this::toHarnessPRAction).collect(Collectors.toList()))
            .build();
      case PUSH:
        return HarnessPushSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .build();
      default:
        throw new InvalidRequestException("Harness Webhook Trigger Event Type " + spec.getType() + " is invalid");
    }
  }

  GitlabEventSpec toGitlabEventSpec(io.harness.ngtriggers.beans.source.v1.webhook.spec.GitlabSpec spec) {
    switch (spec.getType()) {
      case PUSH:
        return GitlabPushSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .build();
      case MR_COMMENT:
        return GitlabMRCommentSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .actions(spec.fetchGitAware()
                         .fetchActions()
                         .stream()
                         .map(this::toGitlabMRCommentAction)
                         .collect(Collectors.toList()))
            .build();
      case MERGE_REQUEST:
        return GitlabPRSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .actions(
                spec.fetchGitAware().fetchActions().stream().map(this::toGitlabPRAction).collect(Collectors.toList()))
            .build();
      default:
        throw new InvalidRequestException("Gitlab Webhook Trigger Event Type " + spec.getType() + " is invalid");
    }
  }

  GithubEventSpec toGithubEventSpec(io.harness.ngtriggers.beans.source.v1.webhook.spec.GithubSpec spec) {
    switch (spec.getType()) {
      case PULL_REQUEST:
        return GithubPRSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .actions(
                spec.fetchGitAware().fetchActions().stream().map(this::toGithubPRAction).collect(Collectors.toList()))
            .build();
      case PUSH:

        return GithubPushSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .build();
      case RELEASE:
        return GithubReleaseSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .actions(spec.fetchGitAware()
                         .fetchActions()
                         .stream()
                         .map(this::toGithubReleaseAction)
                         .collect(Collectors.toList()))
            .build();
      case ISSUE_COMMENT:
        return GithubIssueCommentSpec.builder()
            .autoAbortPreviousExecutions(spec.fetchGitAware().fetchAutoAbortPreviousExecutions())
            .connectorRef(spec.fetchGitAware().fetchConnectorRef())
            .headerConditions(spec.fetchPayloadAware().fetchHeaderConditions())
            .payloadConditions(spec.fetchPayloadAware().fetchPayloadConditions())
            .jexlCondition(spec.fetchPayloadAware().fetchJexlCondition())
            .repoName(spec.fetchGitAware().fetchRepoName())
            .actions(spec.fetchGitAware()
                         .fetchActions()
                         .stream()
                         .map(this::toGithubIssueCommentAction)
                         .collect(Collectors.toList()))
            .build();
      default:
        throw new InvalidRequestException("Github Webhook Trigger Event Type " + spec.getType() + " is invalid");
    }
  }

  GitlabPRAction toGitlabPRAction(GitAction action) {
    io.harness.ngtriggers.beans.source.v1.webhook.gitlab.action.GitlabPRAction actionsEnum =
        (io.harness.ngtriggers.beans.source.v1.webhook.gitlab.action.GitlabPRAction) action;
    switch (actionsEnum) {
      case REOPEN:
        return GitlabPRAction.REOPEN;
      case CLOSE:
        return GitlabPRAction.CLOSE;
      case OPEN:
        return GitlabPRAction.OPEN;
      case SYNC:
        return GitlabPRAction.SYNC;
      case MERGE:
        return GitlabPRAction.MERGE;
      case UPDATE:
        return GitlabPRAction.UPDATE;
      default:
        throw new InvalidRequestException("Gitlab PR Action " + actionsEnum + " is invalid");
    }
  }

  BitbucketPRCommentAction toBitbucketPRCommentAction(GitAction action) {
    io.harness.ngtriggers.beans.source.v1.webhook.bitbucket.action.BitbucketPRCommentAction actionsEnum =
        (io.harness.ngtriggers.beans.source.v1.webhook.bitbucket.action.BitbucketPRCommentAction) action;
    switch (actionsEnum) {
      case CREATE:
        return BitbucketPRCommentAction.CREATE;
      case EDIT:
        return BitbucketPRCommentAction.EDIT;
      case DELETE:
        return BitbucketPRCommentAction.DELETE;
      default:
        throw new InvalidRequestException("Bitbucket PR Comment Action " + actionsEnum + " is invalid");
    }
  }

  BitbucketPRAction toBitbucketPRAction(GitAction action) {
    io.harness.ngtriggers.beans.source.v1.webhook.bitbucket.action.BitbucketPRAction actionsEnum =
        (io.harness.ngtriggers.beans.source.v1.webhook.bitbucket.action.BitbucketPRAction) action;
    switch (actionsEnum) {
      case CREATE:
        return BitbucketPRAction.CREATE;
      case UPDATE:
        return BitbucketPRAction.UPDATE;
      case MERGE:
        return BitbucketPRAction.MERGE;
      case DECLINE:
        return BitbucketPRAction.DECLINE;
      default:
        throw new InvalidRequestException("Bitbucket PR Action " + actionsEnum + " is invalid");
    }
  }

  AzureRepoPRAction toAzureRepoPRAction(GitAction action) {
    io.harness.ngtriggers.beans.source.v1.webhook.azurerepo.action.AzureRepoPRAction actionsEnum =
        (io.harness.ngtriggers.beans.source.v1.webhook.azurerepo.action.AzureRepoPRAction) action;
    switch (actionsEnum) {
      case CREATE:
        return AzureRepoPRAction.CREATE;
      case MERGE:
        return AzureRepoPRAction.MERGE;
      case UPDATE:
        return AzureRepoPRAction.UPDATE;
      default:
        throw new InvalidRequestException("Azure Repo PR Action " + actionsEnum + " is invalid");
    }
  }

  AzureRepoIssueCommentAction toAzureRepoIssueCommentAction(GitAction action) {
    io.harness.ngtriggers.beans.source.v1.webhook.azurerepo.action.AzureRepoIssueCommentAction actionsEnum =
        (io.harness.ngtriggers.beans.source.v1.webhook.azurerepo.action.AzureRepoIssueCommentAction) action;
    switch (actionsEnum) {
      case CREATE:
        return AzureRepoIssueCommentAction.CREATE;
      case DELETE:
        return AzureRepoIssueCommentAction.DELETE;
      case EDIT:
        return AzureRepoIssueCommentAction.EDIT;
      default:
        throw new InvalidRequestException("Azure Repo Issue comment Action " + actionsEnum + " is invalid");
    }
  }

  HarnessPRAction toHarnessPRAction(GitAction action) {
    io.harness.ngtriggers.beans.source.v1.webhook.harness.action.HarnessPRAction actionsEnum =
        (io.harness.ngtriggers.beans.source.v1.webhook.harness.action.HarnessPRAction) action;
    switch (actionsEnum) {
      case CREATE:
        return HarnessPRAction.CREATE;
      case MERGE:
        return HarnessPRAction.MERGE;
      case REOPEN:
        return HarnessPRAction.REOPEN;
      case COMMENT:
        return HarnessPRAction.COMMENT;
      case UPDATE:
        return HarnessPRAction.UPDATE;
      default:
        throw new InvalidRequestException("Harness PR Action " + actionsEnum + " is invalid");
    }
  }

  GitlabMRCommentAction toGitlabMRCommentAction(GitAction action) {
    io.harness.ngtriggers.beans.source.v1.webhook.gitlab.action.GitlabMRCommentAction actionsEnum =
        (io.harness.ngtriggers.beans.source.v1.webhook.gitlab.action.GitlabMRCommentAction) action;
    switch (actionsEnum) {
      case CREATE:
        return GitlabMRCommentAction.CREATE;
      default:
        throw new InvalidRequestException("Gitlab MR Comment Action " + actionsEnum + " is invalid");
    }
  }

  GithubPRAction toGithubPRAction(GitAction action) {
    io.harness.ngtriggers.beans.source.v1.webhook.github.action.GithubPRAction actionsEnum =
        (io.harness.ngtriggers.beans.source.v1.webhook.github.action.GithubPRAction) action;
    switch (actionsEnum) {
      case EDIT:
        return GithubPRAction.EDIT;
      case OPEN:
        return GithubPRAction.OPEN;
      case CLOSE:
        return GithubPRAction.CLOSE;
      case LABEL:
        return GithubPRAction.LABEL;
      case REOPEN:
        return GithubPRAction.REOPEN;
      case UNLABEL:
        return GithubPRAction.UNLABEL;
      case SYNCHRONIZE:
        return GithubPRAction.SYNCHRONIZE;
      case REVIEWREADY:
        return GithubPRAction.REVIEWREADY;
      default:
        throw new InvalidRequestException("Github PR Action " + actionsEnum + " is invalid");
    }
  }

  GithubReleaseAction toGithubReleaseAction(GitAction action) {
    io.harness.ngtriggers.beans.source.v1.webhook.github.action.GithubReleaseAction actionsEnum =
        (io.harness.ngtriggers.beans.source.v1.webhook.github.action.GithubReleaseAction) action;
    switch (actionsEnum) {
      case RELEASE:
        return GithubReleaseAction.RELEASE;
      case EDIT:
        return GithubReleaseAction.EDIT;
      case CREATE:
        return GithubReleaseAction.CREATE;
      case DELETE:
        return GithubReleaseAction.DELETE;
      case PUBLISH:
        return GithubReleaseAction.PUBLISH;
      case PRERELEASE:
        return GithubReleaseAction.PRERELEASE;
      case UNPUBLISH:
        return GithubReleaseAction.UNPUBLISH;
      default:
        throw new InvalidRequestException("Github Release Action " + actionsEnum + " is invalid");
    }
  }

  GithubIssueCommentAction toGithubIssueCommentAction(GitAction action) {
    io.harness.ngtriggers.beans.source.v1.webhook.github.action.GithubIssueCommentAction actionsEnum =
        (io.harness.ngtriggers.beans.source.v1.webhook.github.action.GithubIssueCommentAction) action;
    switch (actionsEnum) {
      case DELETE:
        return GithubIssueCommentAction.DELETE;
      case CREATE:
        return GithubIssueCommentAction.CREATE;
      case EDIT:
        return GithubIssueCommentAction.EDIT;
      default:
        throw new InvalidRequestException("Github Issue Comment Action " + actionsEnum + " is invalid");
    }
  }

  GithubTriggerEvent toGithubTriggerEvent(
      io.harness.ngtriggers.beans.source.v1.webhook.github.event.GithubTriggerEvent typeEnum) {
    switch (typeEnum) {
      case PULL_REQUEST:
        return GithubTriggerEvent.PULL_REQUEST;
      case PUSH:
        return GithubTriggerEvent.PUSH;
      case RELEASE:
        return GithubTriggerEvent.RELEASE;
      case ISSUE_COMMENT:
        return GithubTriggerEvent.ISSUE_COMMENT;
      default:
        throw new InvalidRequestException("Github Webhook Trigger Event Type " + typeEnum + " is invalid");
    }
  }

  GitlabTriggerEvent toGitlabTriggerEvent(
      io.harness.ngtriggers.beans.source.v1.webhook.gitlab.event.GitlabTriggerEvent typeEnum) {
    switch (typeEnum) {
      case MERGE_REQUEST:
        return GitlabTriggerEvent.MERGE_REQUEST;
      case MR_COMMENT:
        return GitlabTriggerEvent.MR_COMMENT;
      case PUSH:
        return GitlabTriggerEvent.PUSH;
      default:
        throw new InvalidRequestException("Gitlab Webhook Trigger Event Type " + typeEnum + " is invalid");
    }
  }

  HarnessTriggerEvent toHarnessTriggerEvent(
      io.harness.ngtriggers.beans.source.v1.webhook.harness.event.HarnessTriggerEvent typeEnum) {
    switch (typeEnum) {
      case PUSH:
        return HarnessTriggerEvent.PUSH;
      case PULL_REQUEST:
        return HarnessTriggerEvent.PULL_REQUEST;
      default:
        throw new InvalidRequestException("Harness Webhook Trigger Event Type " + typeEnum + " is invalid");
    }
  }

  BitbucketTriggerEvent toBitbucketTriggerEvent(
      io.harness.ngtriggers.beans.source.v1.webhook.bitbucket.event.BitbucketTriggerEvent typeEnum) {
    switch (typeEnum) {
      case PUSH:
        return BitbucketTriggerEvent.PUSH;
      case PULL_REQUEST:
        return BitbucketTriggerEvent.PULL_REQUEST;
      case PR_COMMENT:
        return BitbucketTriggerEvent.PR_COMMENT;
      default:
        throw new InvalidRequestException("Bitbucket Webhook Trigger Event Type " + typeEnum + " is invalid");
    }
  }

  AwsCodeCommitTriggerEvent toAwsCodeCommitTriggerEvent(
      io.harness.ngtriggers.beans.source.v1.webhook.awscodecommit.event.AwsCodeCommitTriggerEvent typeEnum) {
    switch (typeEnum) {
      case PUSH:
        return AwsCodeCommitTriggerEvent.PUSH;
      default:
        throw new InvalidRequestException("Aws Code Commit Webhook Trigger Event Type " + typeEnum + " is invalid");
    }
  }

  AzureRepoTriggerEvent toAzureRepoTriggerEvent(
      io.harness.ngtriggers.beans.source.v1.webhook.azurerepo.event.AzureRepoTriggerEvent typeEnum) {
    switch (typeEnum) {
      case PULL_REQUEST:
        return AzureRepoTriggerEvent.PULL_REQUEST;
      case PUSH:
        return AzureRepoTriggerEvent.PUSH;
      case ISSUE_COMMENT:
        return AzureRepoTriggerEvent.ISSUE_COMMENT;
      default:
        throw new InvalidRequestException("Azure Repo Webhook Trigger Event Type " + typeEnum + " is invalid");
    }
  }
}

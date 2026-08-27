/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook.v2.spec;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.Constants.AWS_CODECOMMIT_REPO;
import static io.harness.ngtriggers.Constants.AZURE_REPO;
import static io.harness.ngtriggers.Constants.BITBUCKET_REPO;
import static io.harness.ngtriggers.Constants.CUSTOM_REPO;
import static io.harness.ngtriggers.Constants.EVENT_RELAY_WEBHOOK;
import static io.harness.ngtriggers.Constants.GITHUB_REPO;
import static io.harness.ngtriggers.Constants.GITLAB_REPO;
import static io.harness.ngtriggers.Constants.HARNESS_ARTIFACT_REGISTRY;
import static io.harness.ngtriggers.Constants.HARNESS_REPO;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.beans.source.webhook.v2.git.PayloadAware;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GithubSpec.class, name = GITHUB_REPO)
  , @JsonSubTypes.Type(value = GitlabSpec.class, name = GITLAB_REPO),
      @JsonSubTypes.Type(value = BitbucketSpec.class, name = BITBUCKET_REPO),
      @JsonSubTypes.Type(value = AwsCodeCommitSpec.class, name = AWS_CODECOMMIT_REPO),
      @JsonSubTypes.Type(value = AzureRepoSpec.class, name = AZURE_REPO),
      @JsonSubTypes.Type(value = HarnessSpec.class, name = HARNESS_REPO),
      @JsonSubTypes.Type(value = CustomTriggerSpec.class, name = CUSTOM_REPO),
      @JsonSubTypes.Type(value = EventBridgeTriggerSpec.class, name = EVENT_RELAY_WEBHOOK),
      @JsonSubTypes.Type(value = HarnessArtifactRegistrySpec.class, name = HARNESS_ARTIFACT_REGISTRY)
})
@OwnedBy(PIPELINE)
public interface WebhookTriggerSpecV2 {
  GitAware fetchGitAware();
  PayloadAware fetchPayloadAware();
}

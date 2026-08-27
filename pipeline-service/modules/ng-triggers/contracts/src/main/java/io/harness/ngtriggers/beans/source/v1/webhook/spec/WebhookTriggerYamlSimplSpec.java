/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.webhook.spec;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.AWS_CODECOMMIT_REPO;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.AZURE_REPO;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.BITBUCKET_REPO;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.CUSTOM_REPO;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.GITHUB_REPO;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.GITLAB_REPO;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.HARNESS_REPO;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.beans.source.webhook.v2.git.PayloadAware;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GithubSpec.class, name = GITHUB_REPO)
  , @JsonSubTypes.Type(value = GitlabSpec.class, name = GITLAB_REPO),
      @JsonSubTypes.Type(value = BitbucketSpec.class, name = BITBUCKET_REPO),
      @JsonSubTypes.Type(value = AwsCodeCommitSpec.class, name = AWS_CODECOMMIT_REPO),
      @JsonSubTypes.Type(value = AzureRepoSpec.class, name = AZURE_REPO),
      @JsonSubTypes.Type(value = HarnessSpec.class, name = HARNESS_REPO),
      @JsonSubTypes.Type(value = CustomTriggerSpec.class, name = CUSTOM_REPO)
})
@OwnedBy(PIPELINE)
public interface WebhookTriggerYamlSimplSpec {
  GitAware fetchGitAware();
  PayloadAware fetchPayloadAware();
}

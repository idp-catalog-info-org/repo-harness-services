/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.webhook;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.AWS_CODECOMMIT_REPO;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.AZURE_REPO;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.BITBUCKET_REPO;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.CUSTOM_REPO;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.GITHUB_REPO;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.GITLAB_REPO;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.HARNESS_REPO;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonProperty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
public enum WebhookTriggerYamlSimplType {
  @JsonProperty(AZURE_REPO) AZURE(AZURE_REPO, "AZURE_REPO"),
  @JsonProperty(GITHUB_REPO) GITHUB(GITHUB_REPO, "GITHUB"),
  @JsonProperty(GITLAB_REPO) GITLAB(GITLAB_REPO, "GITLAB"),
  @JsonProperty(BITBUCKET_REPO) BITBUCKET(BITBUCKET_REPO, "BITBUCKET"),
  @JsonProperty(CUSTOM_REPO) CUSTOM(CUSTOM_REPO, "CUSTOM"),
  @JsonProperty(AWS_CODECOMMIT_REPO) AWS_CODECOMMIT(AWS_CODECOMMIT_REPO, "AWS_CODECOMMIT"),
  @JsonProperty(HARNESS_REPO) HARNESS(HARNESS_REPO, "HARNESS");

  private String value;
  private String entityMetadataName;

  WebhookTriggerYamlSimplType(String value, String entityMetadataName) {
    this.value = value;
    this.entityMetadataName = entityMetadataName;
  }

  public String getValue() {
    return value;
  }

  public String getEntityMetadataName() {
    return entityMetadataName;
  }
}

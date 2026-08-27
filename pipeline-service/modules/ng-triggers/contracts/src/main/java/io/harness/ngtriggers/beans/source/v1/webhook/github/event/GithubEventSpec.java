/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.webhook.github.event;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.DELETE_EVENT_TYPE;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.ISSUE_COMMENT_EVENT_TYPE;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.PR_EVENT_TYPE;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.PUSH_EVENT_TYPE;
import static io.harness.ngtriggers.beans.source.v1.constants.YamlSimplConstants.RELEASE_EVENT_TYPE;

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
  @JsonSubTypes.Type(value = GithubPRSpec.class, name = PR_EVENT_TYPE)
  , @JsonSubTypes.Type(value = GithubPushSpec.class, name = PUSH_EVENT_TYPE),
      @JsonSubTypes.Type(value = GithubIssueCommentSpec.class, name = ISSUE_COMMENT_EVENT_TYPE),
      @JsonSubTypes.Type(value = GithubReleaseSpec.class, name = RELEASE_EVENT_TYPE),
      @JsonSubTypes.Type(value = GithubDeleteSpec.class, name = DELETE_EVENT_TYPE)
})
@OwnedBy(PIPELINE)
public interface GithubEventSpec extends PayloadAware, GitAware {}

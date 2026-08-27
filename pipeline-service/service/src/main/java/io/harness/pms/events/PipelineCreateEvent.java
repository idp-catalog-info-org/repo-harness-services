/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.events;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.event.Event;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.utils.PipelineEventUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PIPELINE)
@Getter
@NoArgsConstructor
public class PipelineCreateEvent implements Event {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private PipelineEntity pipeline;
  private Boolean isForOldGitSync;

  // adding this back so that older records with this field can be read
  private Boolean isFromGit;
  private Boolean isParentIdQueryingEnabled;
  private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  public PipelineCreateEvent(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, PipelineEntity pipeline) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.pipeline = pipeline;
    this.isForOldGitSync = false;
  }

  public PipelineCreateEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      PipelineEntity pipeline, PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.pipeline = pipeline;
    this.isForOldGitSync = false;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
  }

  @Builder
  public PipelineCreateEvent(String orgIdentifier, String accountIdentifier, String projectIdentifier,
      PipelineEntity pipeline, Boolean isForOldGitSync, Boolean isParentIdQueryingEnabled,
      PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    this.orgIdentifier = orgIdentifier;
    this.accountIdentifier = accountIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.pipeline = pipeline;
    this.isForOldGitSync = isForOldGitSync;
    this.isParentIdQueryingEnabled = isParentIdQueryingEnabled;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new ProjectScope(accountIdentifier, orgIdentifier, projectIdentifier, pipeline.getParentUniqueId());
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME,
        PipelineEventUtils.getResourceName(
            pipeline.getIdentifier(), pipeline.getName(), accountIdentifier, pmsFeatureFlagHelper));
    return Resource.builder()
        .identifier(pipeline.getIdentifier())
        .type(ResourceTypeConstants.PIPELINE)
        .labels(labels)
        .uniqueId(pipeline.getUniqueId())
        .build();
  }

  public Boolean getIsForOldGitSync() {
    if (isForOldGitSync == null) {
      return isFromGit != null && isFromGit;
    }
    return isForOldGitSync;
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return PipelineOutboxEvents.PIPELINE_CREATED;
  }
}

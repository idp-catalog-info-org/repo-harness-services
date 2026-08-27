/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.events.delete;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.beans.ScopeInfo;
import io.harness.event.Event;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.pms.events.PipelineOutboxEvents;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.utils.PipelineEventUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PIPELINE)
@Getter
@NoArgsConstructor
public class PipelineDeleteEvent implements Event {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String parentUniqueIdentifier;
  private ScopeInfo scopeInfo;
  private PipelineEntity pipeline;
  private Boolean isForOldGitSync;
  private Boolean isParentIdQueryingEnabled;
  private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  // adding this back so that older records with this field can be read
  private Boolean isFromGit;

  public PipelineDeleteEvent(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, PipelineEntity pipeline) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.pipeline = pipeline;
    this.isForOldGitSync = false;
  }

  public PipelineDeleteEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      PipelineEntity pipeline, PmsFeatureFlagHelper pmsFeatureFlagHelper, String parentUniqueId, ScopeInfo scopeInfo) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.pipeline = pipeline;
    this.isForOldGitSync = false;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
    this.parentUniqueIdentifier = parentUniqueId;
    this.scopeInfo = scopeInfo;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    if (scopeInfo != null) {
      return new ProjectScope(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier(), scopeInfo.getUniqueId());
    } else {
      return new ProjectScope(accountIdentifier, orgIdentifier, projectIdentifier, parentUniqueIdentifier);
    }
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
    return PipelineOutboxEvents.PIPELINE_DELETED;
  }
}

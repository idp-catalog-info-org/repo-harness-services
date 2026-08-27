/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.events;

import io.harness.audit.ResourceTypeConstants;
import io.harness.beans.Scope;
import io.harness.event.shared.MoveConfigEvent;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.utils.PipelineEventUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.HashMap;
import java.util.Map;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PipelineMoveConfigEvent extends MoveConfigEvent {
  private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  public PipelineMoveConfigEvent(
      Scope scope, String identifier, String name, String oldAttributesYaml, String newAttributesYaml) {
    super(scope, identifier, name, oldAttributesYaml, newAttributesYaml);
  }

  public PipelineMoveConfigEvent(Scope scope, String identifier, String name, String oldAttributesYaml,
      String newAttributesYaml, PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    super(scope, identifier, name, oldAttributesYaml, newAttributesYaml);
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
  }

  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME,
        PipelineEventUtils.getResourceName(
            getIdentifier(), getName(), getScope().getAccountIdentifier(), pmsFeatureFlagHelper));
    return Resource.builder().identifier(getIdentifier()).type(ResourceTypeConstants.PIPELINE).labels(labels).build();
  }

  @Override
  public String getEventType() {
    return PipelineOutboxEvents.PIPELINE_MOVED_TO_REMOTE;
  }
}

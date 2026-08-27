/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.catalog.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_ACTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.event.Event;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(IDP)
@Getter
@NoArgsConstructor
public class ActionUpdateEvent implements Event {
  public static final String IDP_ACTION_UPDATED = "IDPActionUpdated";

  private String oldActionJson;
  private String newActionJson;
  private ScopeInfo scopeInfo;
  private String identifier;
  private String version;
  private String uniqueId;

  public ActionUpdateEvent(ScopeInfo scopeInfo, String oldActionJson, String newActionJson, String identifier,
      String version, String uniqueId) {
    this.oldActionJson = oldActionJson;
    this.newActionJson = newActionJson;
    this.scopeInfo = scopeInfo;
    this.identifier = identifier;
    this.version = version;
    this.uniqueId = uniqueId;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return EventsUtils.getResourceScopeForAuditEvents(scopeInfo);
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    String resourceId = identifier + ":" + version;
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, resourceId);
    return Resource.builder().identifier(resourceId).type(IDP_ACTION).uniqueId(uniqueId).labels(labels).build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return IDP_ACTION_UPDATED;
  }
}

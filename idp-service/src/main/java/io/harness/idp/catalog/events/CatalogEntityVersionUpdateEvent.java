/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_VERSION;

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
public class CatalogEntityVersionUpdateEvent implements Event {
  public static final String IDP_CATALOG_ENTITY_VERSION_UPDATED = "IDPCatalogEntityVersionUpdated";

  private String newInlineCatalogEntityVersionYaml;
  private String oldInlineCatalogEntityVersionYaml;
  private ScopeInfo scopeInfo;
  private String kind;
  private String identifier;
  private String version;
  private Boolean oldStable;
  private Boolean newStable;
  private Boolean oldDeprecated;
  private Boolean newDeprecated;

  public CatalogEntityVersionUpdateEvent(ScopeInfo scopeInfo, String newInlineCatalogEntityVersionYaml,
      String oldInlineCatalogEntityVersionYaml, String kind, String identifier, String version, Boolean oldStable,
      Boolean newStable, Boolean oldDeprecated, Boolean newDeprecated) {
    this.newInlineCatalogEntityVersionYaml = newInlineCatalogEntityVersionYaml;
    this.oldInlineCatalogEntityVersionYaml = oldInlineCatalogEntityVersionYaml;
    this.scopeInfo = scopeInfo;
    this.kind = kind;
    this.identifier = identifier;
    this.version = version;
    this.oldStable = oldStable;
    this.newStable = newStable;
    this.oldDeprecated = oldDeprecated;
    this.newDeprecated = newDeprecated;
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
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, EventsUtils.getAuditNameForCatalogEntity(kind, identifier));
    return Resource.builder()
        .identifier(EventsUtils.getAuditIdentifierForCatalogEntityVersion(scopeInfo, kind, identifier, version))
        .type(IDP_CATALOG_VERSION)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return IDP_CATALOG_ENTITY_VERSION_UPDATED;
  }
}

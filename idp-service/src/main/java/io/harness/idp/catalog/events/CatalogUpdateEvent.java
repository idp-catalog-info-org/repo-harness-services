/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.catalog.events;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG;

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
public class CatalogUpdateEvent implements Event {
  public static final String IDP_CATALOG_UPDATED = "IDPCatalogUpdated";

  private String newInlineCatalogEntityYaml;
  private String oldInlineCatalogEntityYaml;
  private ScopeInfo scopeInfo;
  private String kind;
  private String identifier;

  public CatalogUpdateEvent(ScopeInfo scopeInfo, String newInlineCatalogEntityYaml, String oldInlineCatalogEntityYaml,
      String kind, String identifier) {
    this.newInlineCatalogEntityYaml = newInlineCatalogEntityYaml;
    this.oldInlineCatalogEntityYaml = oldInlineCatalogEntityYaml;
    this.scopeInfo = scopeInfo;
    this.kind = kind;
    this.identifier = identifier;
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
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME,
        EventsUtils.getAuditIdentifierForCatalogEntity(scopeInfo, kind, identifier));
    return Resource.builder()
        .identifier(EventsUtils.getAuditIdentifierForCatalogEntity(scopeInfo, kind, identifier))
        .type(IDP_CATALOG)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return IDP_CATALOG_UPDATED;
  }
}

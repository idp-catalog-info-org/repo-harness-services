/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.catalog.events;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.ResourceScope;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class EventsUtils {
  public ResourceScope getResourceScopeForAuditEvents(ScopeInfo scopeInfo) {
    if (scopeInfo.getScopeType().equals(ScopeLevel.PROJECT)) {
      return new ProjectScope(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier(), scopeInfo.getUniqueId());
    } else if (scopeInfo.getScopeType().equals(ScopeLevel.ORGANIZATION)) {
      return new OrgScope(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getUniqueId());
    } else {
      return new AccountScope(scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId());
    }
  }

  public String getAuditNameForCatalogEntity(String kind, String identifier) {
    return kind + ":" + identifier;
  }

  public String getAuditIdentifierForCatalogEntity(ScopeInfo scopeInfo, String kind, String identifier) {
    String scope;
    if (scopeInfo.getScopeType().equals(ScopeLevel.PROJECT)) {
      scope = "account." + scopeInfo.getOrgIdentifier() + "." + scopeInfo.getProjectIdentifier();
    } else if (scopeInfo.getScopeType().equals(ScopeLevel.ORGANIZATION)) {
      scope = "account." + scopeInfo.getOrgIdentifier();
    } else {
      scope = "account";
    }
    return scope + "/" + kind + ":" + identifier;
  }

  public String getAuditIdentifierForCatalogEntityVersion(
      ScopeInfo scopeInfo, String kind, String identifier, String version) {
    return getAuditIdentifierForCatalogEntity(scopeInfo, kind, identifier) + "/" + version;
  }
}

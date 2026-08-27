/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to resolve the correct ScopeInfo for a connector based on its scoped reference.
 * This parses connector references like "account.connectorId", "org.connectorId", or "connectorId"
 * and fetches the appropriate ScopeInfo for that scope level.
 */
@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class ConnectorScopeHelper {
  @Inject private ScopeInfoClient scopeInfoClient;

  /**
   * Gets the ScopeInfo for a connector based on its scoped reference.
   *
   * @param scope The scope context (account/org/project identifiers)
   * @param connectorRef The connector reference (e.g., "account.myConnector", "org.myConnector", "myConnector")
   * @return ScopeInfo for the connector's actual scope
   */
  public ScopeInfo getConnectorScopeInfo(Scope scope, String connectorRef) {
    io.harness.encryption.Scope connectorScope = IdentifierRefHelper.getScopeFromScopedRef(connectorRef);
    if (connectorScope == io.harness.encryption.Scope.ACCOUNT) {
      return NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(scope.getAccountIdentifier(), null, null));
    } else if (connectorScope == io.harness.encryption.Scope.ORG) {
      return NGRestUtils.getResponse(
          scopeInfoClient.getScopeInfo(scope.getAccountIdentifier(), scope.getOrgIdentifier(), null));
    } else {
      // PROJECT scope or no prefix - use full scope
      return NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier()));
    }
  }
}

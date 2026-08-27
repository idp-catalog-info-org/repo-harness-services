/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.entities.ActionStatus;
import io.harness.spec.server.idp.v1.model.ActionUpdateRequest;

import java.util.List;
import org.springframework.data.domain.Page;

@OwnedBy(HarnessTeam.IDP)
public interface ActionService {
  Action createAction(ScopeInfo scopeInfo, Action action);

  Action getAction(ScopeInfo scopeInfo, String identifier, String version);

  Action getPublishedAction(ScopeInfo scopeInfo, String identifier);

  Page<Action> listActions(ScopeInfo scopeInfo, ActionStatus status, String category, String searchTerm, Integer page,
      Integer limit, String sort);

  Action updateAction(ScopeInfo scopeInfo, String identifier, String version, ActionUpdateRequest request);

  Action changeStatus(ScopeInfo scopeInfo, String identifier, String version, ActionStatus targetStatus);

  void deleteAction(ScopeInfo scopeInfo, String identifier, String version);

  List<Action> listActionVersions(ScopeInfo scopeInfo, String identifier);
}

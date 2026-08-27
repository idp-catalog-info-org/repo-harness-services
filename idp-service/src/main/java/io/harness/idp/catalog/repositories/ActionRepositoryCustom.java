/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.entities.ActionStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

@OwnedBy(HarnessTeam.IDP)
public interface ActionRepositoryCustom {
  Page<Action> findAll(String accountIdentifier, List<String> parentUniqueIds, ActionStatus status, String category,
      String searchTerm, Integer page, Integer limit, String sort);

  Optional<Action> findPublishedVersion(String parentUniqueId, String identifier);

  void deprecateCurrentlyPublished(String parentUniqueId, String identifier);

  /**
   * Bulk lookup by exact (parentUniqueId, identifier, version) tuples in a single query.
   * Returns only rows that matched; ordering is not guaranteed. Callers are expected to
   * resolve any tenant-vs-global precedence themselves.
   */
  List<Action> bulkFindByParentUniqueIdIdentifierVersion(Collection<ActionLookupKey> keys);
}

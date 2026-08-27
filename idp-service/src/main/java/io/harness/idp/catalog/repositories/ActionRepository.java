/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.Action;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface ActionRepository extends CrudRepository<Action, String>, ActionRepositoryCustom {
  Optional<Action> findByParentUniqueIdAndIdentifierAndVersion(
      String parentUniqueId, String identifier, String version);

  List<Action> findByParentUniqueIdAndIdentifier(String parentUniqueId, String identifier);

  void deleteByParentUniqueIdAndIdentifierAndVersion(String parentUniqueId, String identifier, String version);
}

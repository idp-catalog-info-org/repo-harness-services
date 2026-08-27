/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntityVersion;

import java.util.Optional;
import org.springframework.data.domain.Page;

@OwnedBy(HarnessTeam.IDP)
public interface CatalogEntityVersionRepositoryCustom {
  Page<CatalogEntityVersion> findByEntityId(
      String entityId, Integer page, Integer limit, String versionSearchTerm, Boolean deprecated);
  Optional<CatalogEntityVersion> getStableVersionForEntity(String entityId);
  CatalogEntityVersion createCatalogEntityVersionAndSyncStable(CatalogEntityVersion catalogEntityVersion);
  void updateCatalogEntityVersionAndSyncStable(CatalogEntityVersion catalogEntityVersion);
  void deleteAllByEntityId(String entityId);
}

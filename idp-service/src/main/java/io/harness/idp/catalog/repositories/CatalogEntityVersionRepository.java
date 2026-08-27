/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntityVersion;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface CatalogEntityVersionRepository
    extends CrudRepository<CatalogEntityVersion, String>, CatalogEntityVersionRepositoryCustom {
  CatalogEntityVersion findByEntityIdAndVersion(String entityId, String version);
  List<CatalogEntityVersion> findAllByEntityIdIn(List<String> entityIds);
  void deleteByEntityIdAndVersion(String entityId, String version);
  boolean existsByEntityIdAndVersionNot(String entityId, String version);
}

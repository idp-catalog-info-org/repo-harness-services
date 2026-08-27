/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.ccp.entities.CatalogCustomPropertyEntity;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface CatalogCustomPropertiesRepository
    extends CrudRepository<CatalogCustomPropertyEntity, String>, CatalogCustomPropertiesRepositoryCustom {
  List<CatalogCustomPropertyEntity> findByAccountIdentifier(String accountIdentifier);
  List<CatalogCustomPropertyEntity> findByAccountIdentifierAndEntityRef(String accountIdentifier, String entityRef);
  List<CatalogCustomPropertyEntity> findByAccountIdentifierAndEntityRefInAndField(
      String accountIdentifier, List<String> entityRef, String field);
  List<CatalogCustomPropertyEntity> findByAccountIdentifierAndEntityRefAndFieldIn(
      String accountIdentifier, String entityRef, List<String> fields);
  List<CatalogCustomPropertyEntity> findByAccountIdentifierAndField(String accountIdentifier, String field);
}

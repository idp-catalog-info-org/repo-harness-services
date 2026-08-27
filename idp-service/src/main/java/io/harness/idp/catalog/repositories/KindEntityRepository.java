/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.KindEntity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface KindEntityRepository extends CrudRepository<KindEntity, String>, KindEntityRepositoryCustom {
  List<KindEntity> findByKindType(String kindType);
  Optional<KindEntity> findByAccountIdentifierAndIdentifier(String accountIdentifier, String identifier);

  @Query(value = "{ 'accountIdentifier': ?0, 'identifier': ?1 }",
      fields = "{'identifier': 1, 'name': 1, 'description': 1, 'accountIdentifier': 1, "
          + "'kindType': 1, 'displayName': 1, 'icon': 1, 'groupingKind': 1}")
  Optional<KindEntity>
  findByAccountIdentifierAndIdentifierWithoutSchema(String accountIdentifier, String identifier);

  @Query(value = "{ 'accountIdentifier': { $in: ?0 } }",
      fields = "{'identifier': 1, 'name': 1, 'description': 1, 'accountIdentifier': 1, "
          + "'kindType': 1, 'displayName': 1, 'icon': 1, 'groupingKind': 1}")
  List<KindEntity>
  findByAccountIdentifierIn(List<String> accountIdentifiers);

  @Query(value = "{ 'accountIdentifier': { $in: ?0 }, 'groupingKind': true }",
      fields = "{'identifier': 1, 'accountIdentifier': 1}")
  List<KindEntity>
  findGroupingKindsByAccountIdentifierIn(List<String> accountIdentifiers);

  List<KindEntity> findAllByAccountIdentifierInAndIdentifierIn(
      List<String> accountIdentifiers, List<String> identifier);
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface BackstageCatalogEntityRepository
    extends CrudRepository<BackstageCatalogEntity, String>, BackstageCatalogEntityRepositoryCustom {
  @Query("{ 'accountIdentifier' : ?0, 'entityUid' : { $regex: '^?1$', $options: 'i' } }")
  Optional<BackstageCatalogEntity> findByAccountIdentifierAndEntityUidIgnoreCase(
      String accountIdentifier, String entityUid);
  @Query("{ 'accountIdentifier' : ?0, 'entityUid' : { $regex: '^?1$', $options: '' } }")
  Optional<BackstageCatalogEntity> findByAccountIdentifierAndEntityUid(String accountIdentifier, String entityUid);
  List<BackstageCatalogEntity> findAllByAccountIdentifier(String accountIdentifier);

  @Aggregation(
      pipeline = {"{ \"$match\": { \"accountIdentifier\": ?0 } }", "{ \"$group\": { \"_id\": \"$entityUid\" } }",
          "{ \"$project\": { \"entityUid\": \"$_id\", \"_id\": 0 } }"})
  List<String>
  findEntityIdentifiersByAccountIdentifier(String accountIdentifier);
  List<BackstageCatalogEntity> findAllByAccountIdentifierAndKind(String accountIdentifier, String kind);
  List<BackstageCatalogEntity> findAllByAccountIdentifierAndKindIn(String accountIdentifier, List<String> kinds);

  @Aggregation(
      pipeline =
          {"{ $group: { _id: { accountIdentifier: \"$accountIdentifier\", entityUid: { $toLower: \"$entityUid\" } }, "
                  + "count: { $sum: 1 }, entities: { $push: \"$$ROOT\" } } }",
              "{ $match: { count: { $gt: 1 } } }", "{ $unwind: \"$entities\" }",
              "{ $sort: { \"_id.accountIdentifier\": 1, \"_id.entityUid\": 1, \"entities.lastUpdatedAt\": -1} }",
              "{ $group: { _id: { accountIdentifier: \"$_id.accountIdentifier\", entityUid: \"$_id.entityUid\" }, "
                  + "duplicates: { $push: \"$entities._id\" } } }",
              "{ $project: { _id: 0, accountIdentifier: \"$_id.accountIdentifier\", entityUid: \"$_id.entityUid\", "
                  + "duplicates: \"$duplicates\" } }"})
  List<BackstageCatalogDuplicateEntry>
  findDuplicateEntities();
}

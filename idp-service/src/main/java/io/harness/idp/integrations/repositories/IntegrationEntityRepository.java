/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.IntegrationEntity.Integration;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface IntegrationEntityRepository
    extends CrudRepository<IntegrationEntity, String>, IntegrationRepositoryCustom {
  Optional<IntegrationEntity> findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
      String accountIdentifier, IntegrationEntity.ParentType parentType, IntegrationEntity.SubType subType,
      String additionalIndexer);
  Optional<IntegrationEntity> findByAccountIdentifierAndIdentifierAndIntegration(
      String accountIdentifier, String identifier, Integration integration);

  @Aggregation(pipeline = {"{ \"$group\": { \"_id\": \"$accountIdentifier\" } }",
                   "{ \"$project\": { \"accountIdentifier\": \"$_id\", \"_id\": 0 } }"})
  List<String>
  findUniqueAccountIdentifiers();

  List<IntegrationEntity> findByIntegration(Integration integration);
  List<IntegrationEntity> findByAccountIdentifierAndIntegrationAndManagedFalse(
      String accountIdentifier, Integration integration);
  List<IntegrationEntity> findByAccountIdentifierAndIntegrationAndManagedTrue(
      String accountIdentifier, Integration integration);
  List<IntegrationEntity> findByAccountIdentifierAndAdditionalIndexer(
      String accountIdentifier, String additionalIndexer);
  List<IntegrationEntity> findByAccountIdentifier(String accountIdentifier);
  List<IntegrationEntity> findByAccountIdentifierAndIntegration(String accountIdentifier, Integration integration);
}

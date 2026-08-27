/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;

import java.util.List;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface AggregationRuleRepository
    extends CrudRepository<AggregationRuleEntity, String>, AggregationRuleRepositoryCustom {
  List<AggregationRuleEntity> findByAccountIdentifier(String accountIdentifier);
  AggregationRuleEntity findByAccountIdentifierAndIdentifier(String accountIdentifier, String identifier);
  long deleteByAccountIdentifierAndIdentifier(String accountIdentifier, String identifier);

  @Query(value = "{ 'accountIdentifier': ?0 }", fields = "{ 'identifier': 1, '_class': 1 }")
  List<AggregationRuleEntity> findIdentifiersByAccountIdentifier(String accountIdentifier);

  @Query(value = "{ 'accountIdentifier': ?0, $or: [ { 'name': { $regex: ?1, $options: 'i' } }, { 'identifier': { "
          + "$regex: ?1, $options: 'i' } } ] }",
      fields = "{ 'identifier': 1, '_class': 1 }")
  List<AggregationRuleEntity>
  findIdentifiersByAccountIdentifierAndSearchTerm(String accountIdentifier, String searchTermRegex);
}

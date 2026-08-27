/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;

import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.IDP)
public interface AggregationRuleRepositoryCustom {
  Page<AggregationRuleEntity> findAll(Criteria criteria, Pageable pageable);
  Page<AggregationRuleEntity> getAggregationRules(
      String accountIdentifier, Pageable pageable, Set<String> permittedIdentifiers);
  Page<AggregationRuleEntity> findByAccountIdentifierAndAggregationTypeAndFieldForAgg(String accountIdentifier,
      AggregationRuleEntity.AggregationType aggregationType, String fieldForAgg, Pageable pageable);
}

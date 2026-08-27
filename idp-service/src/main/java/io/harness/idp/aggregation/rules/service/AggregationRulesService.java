/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetails;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetailsRequest;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetailsResponse;
import io.harness.spec.server.idp.v1.model.AggregationSelectionReviewRequest;
import io.harness.spec.server.idp.v1.model.AggregationSelectionReviewResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.IDP)
public interface AggregationRulesService {
  Page<AggregationRuleEntity> getAggregationRules(String accountIdentifier, Pageable pageable, String searchTerm);
  AggregationRuleDetailsResponse getAggregationRule(String accountIdentifier, String aggregationRuleIdentifier);
  AggregationRuleDetailsResponse createAggregationRule(
      String accountIdentifier, AggregationRuleDetailsRequest aggregationRuleRequest);
  AggregationRuleDetailsResponse updateAggregationRule(
      String accountIdentifier, AggregationRuleDetailsRequest aggregationRuleRequest);
  void deleteAggregationRule(String accountIdentifier, String aggregationRuleIdentifier);
  void triggerComputation(String accountIdentifier, String aggregationRuleIdentifier);
  AggregationSelectionReviewResponse reviewAggregationRuleSelection(
      String accountIdentifier, AggregationSelectionReviewRequest aggregationSelectionReviewRequest);
  void compute(AggregationRuleEntity aggregationRuleEntity);
  void compute(String accountIdentifier, String aggregationRuleIdentifier);
  void compute(String accountIdentifier, AggregationRuleDetails oldAggregationRuleDetails,
      AggregationRuleDetails newAggregationRuleDetails);
  void rename(String accountIdentifier, String aggregationRuleIdentifier, String oldName);
  void deleteRuleFieldsFromHierarchicalEntities(
      String accountIdentifier, AggregationRuleDetails aggregationRuleDetails);
  void triggerAggregationRulesForScorecard(String accountIdentifier, String scorecardIdentifier);
}

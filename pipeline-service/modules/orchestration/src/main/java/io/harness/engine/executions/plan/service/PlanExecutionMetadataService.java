/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan.service;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.RetryStagesMetadata;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(HarnessTeam.PIPELINE)
public interface PlanExecutionMetadataService {
  Optional<PlanExecutionMetadata> findByPlanExecutionId(String accountIdentifier, String planExecutionId);
  PlanExecutionMetadata findByPlanExecutionIdWithFieldsIncluded(
      String accountIdentifier, String planExecutionId, Set<String> fieldsToInclude);

  PlanExecutionMetadata save(PlanExecutionMetadata planExecutionMetadata);

  /**
   * Delete all PlanExecutionMetadata for given planExecutionIds
   * Uses - planExecutionId_idx index
   *
   * @param planExecutionIds
   */
  void deleteMetadataForGivenPlanExecutionIds(Set<String> planExecutionIds);

  /**
   * Updates for all PlanExecutionMetadata for given planExecutionIds
   * Uses - planExecutionId_idx index
   * @param planExecutionId
   */
  void updateTTL(String planExecutionId, Date ttlDate);

  void updatePlanExecutionMetadata(String planExecutionId, Consumer<Update> ops);

  String getNotesForExecution(String accountIdentifier, String planExecutionId);

  RetryStagesMetadata getRetryStagesMetadata(String accountIdentifier, String planExecutionId);

  String updateNotesForExecution(String planExecutionId, String notes);

  /**
   * Fetches PlanExecutionMetadata and uses id Index
   *
   * @param planExecutionId
   * @param fieldsToInclude
   * @return
   */
  PlanExecutionMetadata getWithFieldsIncludedFromSecondary(
      String accountIdentifier, String planExecutionId, Set<String> fieldsToInclude);

  /*
   * Falls back on PlanExecutionMetadata Yaml if unavailable.
   */
  Optional<String> getYaml(String accountIdentifier, String planExecutionId);

  /**
   * This function update policyEvaluationIds in planExecutionMetadata collection
   * @param planExecutionId
   * @param evaluatedPolicyIds
   */
  void updateEvaluatedPolicyIds(String planExecutionId, List<Integer> evaluatedPolicyIds);
}

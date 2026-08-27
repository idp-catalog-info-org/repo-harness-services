/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.planexecution;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.execution.PlanExecution;
import io.harness.monitoring.PlanExecutionCountWithAccountAndTriggerTypeResult;
import io.harness.monitoring.PlanExecutionCountWithAccountResult;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public interface PlanExecutionRepositoryCustom {
  PlanExecution getWithProjectionsWithoutUuid(String planExecutionId, List<String> fieldNames);

  /**
   * Update Plan execution with given find query and updateOperations, it returns new record after update
   * @param query
   * @param updateOps
   * @param upsert
   * @return
   */
  PlanExecution updatePlanExecution(Query query, Update updateOps, boolean upsert);

  /**
   * Update multiple records of Plan execution with given find query and updateOperations
   * @param query
   * @param updateOps
   * @return
   */
  void multiUpdatePlanExecution(Query query, Update updateOps);

  PlanExecution getPlanExecutionWithProjections(String planExecutionId, List<String> excludedFieldNames);

  PlanExecution getPlanExecutionWithProjectionsFromAnalytics(String planExecutionId, Set<String> includedFieldNames);

  PlanExecution getPlanExecutionWithProjectionsFromSecondary(String planExecutionId, Set<String> fieldNames);

  PlanExecution getPlanExecutionWithIncludedProjections(String planExecutionId, List<String> includedFieldNames);

  /**
   * Fetch plan executions from analytics node
   * Query should contain projection fields else it will throw exception and max batch size of iterator is 1k
   * @param query
   * @return
   */
  Stream<PlanExecution> fetchPlanExecutionsFromAnalytics(Query query);

  /**
   * Fetches aggregated active execution count per account from analytics node
   * @return
   */
  List<PlanExecutionCountWithAccountResult> aggregateActiveExecutionsCountPerAccount();

  List<PlanExecutionCountWithAccountAndTriggerTypeResult> aggregateActiveExecutionsCountPerAccountByTriggerType();

  List<String> findAllAccountIdsWithExecutionsFromAnalytics();
}

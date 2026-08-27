/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.dashboard.helper.MeanAndMedian;
import io.harness.pms.dashboard.helper.StatusAndTime;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.Tables;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class PipelineDashboardQueryService {
  @Named("SecondaryDSLContext") @Inject private DSLContext dsl;
  Table<?> from = Tables.PIPELINE_EXECUTION_SUMMARY;
  private final String table = "pipeline_execution_summary";

  private final String CI_TableName = "pipeline_execution_summary_ci";
  private final String CD_TableName = "pipeline_execution_summary_cd";

  public List<StatusAndTime> getPipelineExecutionStatusAndTime(String accountId, String orgId, String projectId,
      String pipelineId, long startInterval, long endInterval, ScopeInfo scopeInfo) {
    try {
      return getPipelineStatusAndTimeFromNewTable(
          accountId, orgId, projectId, pipelineId, startInterval, endInterval, scopeInfo);
    } catch (DataAccessException ex) {
      if (DBUtils.isConnectionError(ex)) {
        throw new InvalidRequestException(
            "Unable to fetch Dashboard data for executions, Please ensure timescale is enabled", ex);
      } else {
        throw new InvalidRequestException("Unable to fetch Dashboard data for executions", ex);
      }
    } catch (Exception ex) {
      throw new InvalidRequestException("Unable to fetch Dashboard data for executions", ex);
    }
  }

  public MeanAndMedian getPipelineExecutionMeanAndMedianDuration(String accountId, String orgId, String projectId,
      String pipelineId, long startInterval, long endInterval, ScopeInfo scopeInfo) {
    try {
      List<MeanAndMedian> meanAndMedians = getPipelineMeanAndMedianFromNewTable(
          accountId, orgId, projectId, pipelineId, startInterval, endInterval, scopeInfo);
      return meanAndMedians.get(0);
    } catch (DataAccessException ex) {
      if (DBUtils.isConnectionError(ex)) {
        throw new InvalidRequestException(
            "Unable to fetch Dashboard data for executions, Please ensure timescale is enabled", ex);
      } else {
        throw new InvalidRequestException("Unable to fetch Dashboard data for executions", ex);
      }
    } catch (Exception ex) {
      throw new InvalidRequestException("Unable to fetch Dashboard data for executions", ex);
    }
  }

  private List<Condition> createConditions(String tableName, String orgId, String pipelineId, String projectId,
      String accountId, long startInterval, long endInterval, ScopeInfo scopeInfo) {
    List<Condition> conditions = new ArrayList<>();
    if (tableName.equals(CD_TableName)) {
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PARENT_UNIQUE_ID.eq(scopeInfo.getUniqueId()));
      if (EmptyPredicate.isNotEmpty(pipelineId)) {
        conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER.eq(pipelineId));
      }
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.ge(startInterval));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lt(endInterval));
    } else if (tableName.equals(CI_TableName)) {
      // TODO: CI Table doesnt have indexes on parent_unique_id yet.
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ORGIDENTIFIER.eq(orgId));
      if (EmptyPredicate.isNotEmpty(pipelineId)) {
        conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.PIPELINEIDENTIFIER.eq(pipelineId));
      }
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.PROJECTIDENTIFIER.eq(projectId));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ACCOUNTID.eq(accountId));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS.ge(startInterval));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS.lt(endInterval));
    } else {
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY.PARENT_UNIQUE_ID.eq(scopeInfo.getUniqueId()));
      if (EmptyPredicate.isNotEmpty(pipelineId)) {
        conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY.PIPELINEIDENTIFIER.eq(pipelineId));
      }
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY.STARTTS.ge(startInterval));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY.STARTTS.lt(endInterval));
    }
    return conditions;
  }

  List<StatusAndTime> getPipelineStatusAndTimeFromNewTable(String accountId, String orgId, String projectId,
      String pipelineId, long startInterval, long endInterval, ScopeInfo scopeInfo) {
    List<Condition> conditions =
        createConditions(table, orgId, pipelineId, projectId, accountId, startInterval, endInterval, scopeInfo);
    return dsl
        .select(new SelectField[] {Tables.PIPELINE_EXECUTION_SUMMARY.STATUS, Tables.PIPELINE_EXECUTION_SUMMARY.STARTTS})
        .from(from)
        .where(conditions)
        .fetch()
        .into(StatusAndTime.class);
  }

  List<MeanAndMedian> getPipelineMeanAndMedianFromNewTable(String accountId, String orgId, String projectId,
      String pipelineId, long startInterval, long endInterval, ScopeInfo scopeInfo) {
    List<Condition> conditions =
        createConditions(table, orgId, pipelineId, projectId, accountId, startInterval, endInterval, scopeInfo);
    conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY.ENDTS.isNotNull());
    conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY.ENDTS.gt(0L));
    return dsl
        .select(new SelectField[] {
            DSL.avg(Tables.PIPELINE_EXECUTION_SUMMARY.ENDTS.sub(Tables.PIPELINE_EXECUTION_SUMMARY.STARTTS)).div(1000),
            DSL.percentileDisc(0.5)
                .withinGroupOrderBy(
                    (Tables.PIPELINE_EXECUTION_SUMMARY.ENDTS).sub(Tables.PIPELINE_EXECUTION_SUMMARY.STARTTS))
                .div(1000)})
        .from(from)
        .where(conditions)
        .fetch()
        .into(MeanAndMedian.class);
  }
}

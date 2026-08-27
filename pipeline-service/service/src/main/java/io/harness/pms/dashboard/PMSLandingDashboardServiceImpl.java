/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.dashboard;

import static io.harness.timescaledb.Tables.PIPELINES;
import static io.harness.timescaledb.Tables.PIPELINE_EXECUTION_SUMMARY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.OrgProjectIdentifier;
import io.harness.pms.dashboards.ExecutionsCount;
import io.harness.pms.dashboards.PipelinesCount;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class PMSLandingDashboardServiceImpl implements PMSLandingDashboardService {
  @Named("SecondaryDSLContext") @Inject private DSLContext dsl;

  @Override
  public PipelinesCount getPipelinesCount(String accountIdentifier, List<OrgProjectIdentifier> orgProjectIdentifiers,
      long startInterval, long endInterval) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return PipelinesCount.builder().build();
    }
    List<String> parentUniqueIds =
        orgProjectIdentifiers.stream().map(OrgProjectIdentifier::getUniqueId).collect(Collectors.toList());

    Integer totalCount = getTotalPipelinesCount(accountIdentifier, parentUniqueIds);
    int trendCount = getNewPipelinesCount(accountIdentifier, startInterval, endInterval, parentUniqueIds)
        - getDeletedPipelinesCount(accountIdentifier, startInterval, endInterval, parentUniqueIds);

    return PipelinesCount.builder().totalCount(totalCount).newCount(trendCount).build();
  }

  @Override
  public ExecutionsCount getExecutionsCount(String accountIdentifier, List<OrgProjectIdentifier> orgProjectIdentifiers,
      long startInterval, long endInterval) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return ExecutionsCount.builder().build();
    }

    List<String> parentUniqueIds =
        orgProjectIdentifiers.stream().map(OrgProjectIdentifier::getUniqueId).collect(Collectors.toList());

    Integer totalCount = getTotalExecutionsCount(accountIdentifier, parentUniqueIds);
    int trendCount = getNewExecutionsCount(accountIdentifier, startInterval, endInterval, parentUniqueIds);
    return ExecutionsCount.builder().totalCount(totalCount).newCount(trendCount).build();
  }

  private Integer getTotalExecutionsCount(String accountIdentifier, List<String> parentUniqueIds) {
    return dsl.select(DSL.count())
        .from(PIPELINE_EXECUTION_SUMMARY)
        .where(PIPELINE_EXECUTION_SUMMARY.ACCOUNTID.eq(accountIdentifier))
        .and(PIPELINE_EXECUTION_SUMMARY.PARENT_UNIQUE_ID.in(parentUniqueIds))
        .fetchInto(Integer.class)
        .get(0);
  }

  private Integer getTotalPipelinesCount(String accountIdentifier, List<String> parentUniqueIds) {
    return dsl.select(DSL.count())
        .from(PIPELINES)
        .where(PIPELINES.ACCOUNT_ID.eq(accountIdentifier))
        .and(PIPELINES.DELETED.eq(false))
        .and(PIPELINES.PARENT_UNIQUE_ID.in(parentUniqueIds))
        .fetchInto(Integer.class)
        .get(0);
  }

  private Integer getNewPipelinesCount(
      String accountIdentifier, long startInterval, long endInterval, List<String> parentUniqueIds) {
    return dsl.select(DSL.count())
        .from(PIPELINES)
        .where(PIPELINES.ACCOUNT_ID.eq(accountIdentifier))
        .and(PIPELINES.CREATED_AT.greaterOrEqual(startInterval))
        .and(PIPELINES.CREATED_AT.lessThan(endInterval))
        .and(PIPELINES.DELETED.eq(false))
        .and(PIPELINES.PARENT_UNIQUE_ID.in(parentUniqueIds))
        .fetchInto(Integer.class)
        .get(0);
  }

  private Integer getNewExecutionsCount(
      String accountIdentifier, long startInterval, long endInterval, List<String> parentUniqueIds) {
    return dsl.select(DSL.count())
        .from(PIPELINE_EXECUTION_SUMMARY)
        .where(PIPELINE_EXECUTION_SUMMARY.STARTTS.greaterOrEqual(startInterval))
        .and(PIPELINE_EXECUTION_SUMMARY.STARTTS.lessThan(endInterval))
        .and(PIPELINE_EXECUTION_SUMMARY.PARENT_UNIQUE_ID.in(parentUniqueIds))
        .fetchInto(Integer.class)
        .get(0);
  }

  private Integer getDeletedPipelinesCount(
      String accountIdentifier, long startInterval, long endInterval, List<String> parentUniqueIds) {
    return dsl.select(DSL.count())
        .from(PIPELINES)
        .where(PIPELINES.ACCOUNT_ID.eq(accountIdentifier))
        .and(PIPELINES.LAST_UPDATED_AT.greaterOrEqual(startInterval))
        .and(PIPELINES.LAST_UPDATED_AT.lessThan(endInterval))
        .and(PIPELINES.DELETED.eq(true))
        .and(PIPELINES.CREATED_AT.lessThan(startInterval))
        .and(PIPELINES.PARENT_UNIQUE_ID.in(parentUniqueIds))
        .fetchInto(Integer.class)
        .get(0);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dashboards.LandingPageDeploymentCount;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.overview.config.DeploymentCountBQConfig;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
public class CDLandingPageServiceImpl implements CDLandingPageService {
  @Inject private BigQueryService bigQueryService;
  @Inject @Named("deploymentCountBQConfig") DeploymentCountBQConfig deploymentCountBQConfig;
  private static final String TOTAL_DEPLOYMENTS_QUERY = "SELECT * FROM `%s`";
  private static final String CG_BASE_TOTAL_DEPLOYMENTS_ENV_VARIABLE = "CG_TOTAL_DEPLOYMENTS";

  public LandingPageDeploymentCount getDeploymentCount() {
    TableResult totalDeploymentsResult = getTableResultFromQuery(getTotalDeploymentsQuery(), bigQueryService.get());
    Optional<Long> totalDeployments = getDeploymentCount(totalDeploymentsResult);

    if (totalDeployments.isEmpty()) {
      return LandingPageDeploymentCount.builder().build();
    }

    int baseCGDeployments = getBaseCGDeployments();
    return LandingPageDeploymentCount.builder().value(baseCGDeployments + totalDeployments.get().intValue()).build();
  }

  private Optional<Long> getDeploymentCount(TableResult totalDeploymentsResult) {
    if (totalDeploymentsResult != null && totalDeploymentsResult.getValues().iterator().hasNext()) {
      FieldValueList row = totalDeploymentsResult.getValues().iterator().next();

      return Optional.of(row.get("deployments").getLongValue());
    }
    return Optional.empty();
  }

  private String getTotalDeploymentsQuery() {
    return String.format(TOTAL_DEPLOYMENTS_QUERY, getTotalDeploymentsTableName());
  }

  private String getTotalDeploymentsTableName() {
    return String.format("%s.%s.%s", deploymentCountBQConfig.getProjectId(), deploymentCountBQConfig.getDataset(),
        deploymentCountBQConfig.getTotalDeploymentsTableName());
  }

  private TableResult getTableResultFromQuery(String query, BigQuery bigQuery) {
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (final InterruptedException e) {
      log.error("Failed to get table result from query {}", queryConfig, e);
      Thread.currentThread().interrupt();
      return null;
    }
    return result;
  }

  private int getBaseCGDeployments() {
    if (EmptyPredicate.isEmpty(System.getenv(CG_BASE_TOTAL_DEPLOYMENTS_ENV_VARIABLE))) {
      return 0;
    }
    return Integer.parseInt(System.getenv(CG_BASE_TOTAL_DEPLOYMENTS_ENV_VARIABLE));
  }
}

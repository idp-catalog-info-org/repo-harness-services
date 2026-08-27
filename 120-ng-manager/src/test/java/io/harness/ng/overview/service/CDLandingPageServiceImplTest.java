/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.rule.OwnerRule.MANISH;

import static com.google.cloud.bigquery.FieldValue.Attribute.PRIMITIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dashboards.LandingPageDeploymentCount;
import io.harness.ng.overview.config.DeploymentCountBQConfig;
import io.harness.rule.Owner;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class CDLandingPageServiceImplTest extends CategoryTest {
  @Mock BigQueryService bigQueryService;
  @InjectMocks @Spy private CDLandingPageServiceImpl cdLandingPageService;
  DeploymentCountBQConfig deploymentCountBQConfig = DeploymentCountBQConfig.builder()
                                                        .totalDeploymentsTableName("tableName")
                                                        .dataset("dataSet")
                                                        .projectId("projectId")
                                                        .build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    on(cdLandingPageService).set("deploymentCountBQConfig", deploymentCountBQConfig);
  }

  @Test
  @Owner(developers = MANISH)
  @Category(UnitTests.class)
  public void testGetDeploymentCount() throws InterruptedException {
    BigQuery bigQuery = mock(BigQuery.class);
    doReturn(bigQuery).when(bigQueryService).get();

    FieldList deploymentCountFieldList =
        FieldList.of(Field.newBuilder("deployments", StandardSQLTypeName.INT64).build());

    List<FieldValue> deploymentCountFieldValue = new ArrayList<>();
    deploymentCountFieldValue.add(FieldValue.of(PRIMITIVE, "5"));

    FieldValueList deploymentCountValueList = FieldValueList.of(deploymentCountFieldValue, deploymentCountFieldList);
    FieldValueList deploymentCountFieldValueList =
        FieldValueList.of(deploymentCountValueList, deploymentCountFieldList);

    Iterable<FieldValueList> deploymentCountFieldValueListIterator = List.of(deploymentCountFieldValueList);

    TableResult totalDeploymentsResult = mock(TableResult.class);
    doReturn(totalDeploymentsResult).when(bigQuery).query(any());
    doReturn(deploymentCountFieldValueListIterator).when(totalDeploymentsResult).getValues();

    LandingPageDeploymentCount landingPageDeploymentCount = cdLandingPageService.getDeploymentCount();
    assertThat(landingPageDeploymentCount.getValue()).isEqualTo(5);
    ArgumentCaptor<QueryJobConfiguration> queryCaptor = ArgumentCaptor.forClass(QueryJobConfiguration.class);
    verify(bigQuery, times(1)).query(queryCaptor.capture());

    List<QueryJobConfiguration> queryJobConfigurations = queryCaptor.getAllValues();
    assertThat(queryJobConfigurations.get(0).getQuery()).isEqualTo("SELECT * FROM `projectId.dataSet.tableName`");
  }
}
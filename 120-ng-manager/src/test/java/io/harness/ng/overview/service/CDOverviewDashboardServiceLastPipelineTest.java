/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.rule.OwnerRule.PARTH_SHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.common.collect.Sets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDC)
public class CDOverviewDashboardServiceLastPipelineTest extends NgManagerTestBase {
  @Mock private TimeScaleDBService timeScaleDBService;
  @InjectMocks @Spy private CDOverviewDashboardServiceImpl cdOverviewDashboardService;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String SERVICE_ID_1 = "service1";
  private static final String SERVICE_ID_2 = "service2";
  private static final String ENV_ID_1 = "env1";
  private static final String ENV_ID_2 = "env2";
  private static final String PIPELINE_1 = "pipeline1";
  private static final String PIPELINE_2 = "pipeline2";
  private static final String PIPELINE_3 = "pipeline3";

  private Connection connection;
  private PreparedStatement statement;
  private ResultSet resultSet;
  private Array mockArray;

  @Before
  public void setUp() throws SQLException {
    MockitoAnnotations.openMocks(this);

    // Setup mock DB connection and unified query statement
    connection = mock(Connection.class);
    statement = mock(PreparedStatement.class);
    resultSet = mock(ResultSet.class);
    mockArray = mock(Array.class);

    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(connection.createArrayOf(anyString(), any())).thenReturn(mockArray);
    when(statement.executeQuery()).thenReturn(resultSet);
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testEmptyResultSet() throws SQLException {
    when(resultSet.next()).thenReturn(false);

    Set<String> serviceIds = Sets.newHashSet(SERVICE_ID_1);
    Set<String> envIds = Sets.newHashSet(ENV_ID_1);

    Map<String, String> result =
        cdOverviewDashboardService.getLastPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, serviceIds, envIds);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testMultipleRowsResultSet() throws SQLException {
    // Mock 3 rows returned from database
    when(resultSet.next()).thenReturn(true, true, true, false);
    when(resultSet.getString("service_id")).thenReturn(SERVICE_ID_1, SERVICE_ID_2, SERVICE_ID_2);
    when(resultSet.getString("env_id")).thenReturn(ENV_ID_1, ENV_ID_1, ENV_ID_2);
    when(resultSet.getString("pipeline_execution_summary_cd_id")).thenReturn(PIPELINE_1, PIPELINE_2, PIPELINE_3);

    Set<String> serviceIds = Sets.newHashSet(SERVICE_ID_1, SERVICE_ID_2);
    Set<String> envIds = Sets.newHashSet(ENV_ID_1, ENV_ID_2);

    Map<String, String> result =
        cdOverviewDashboardService.getLastPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, serviceIds, envIds);

    assertThat(result).hasSize(3);

    assertThat(result).containsKeys(
        SERVICE_ID_1 + "-" + ENV_ID_1, SERVICE_ID_2 + "-" + ENV_ID_1, SERVICE_ID_2 + "-" + ENV_ID_2);

    assertThat(result.get(SERVICE_ID_1 + "-" + ENV_ID_1)).isEqualTo(PIPELINE_1);
    assertThat(result.get(SERVICE_ID_2 + "-" + ENV_ID_1)).isEqualTo(PIPELINE_2);
    assertThat(result.get(SERVICE_ID_2 + "-" + ENV_ID_2)).isEqualTo(PIPELINE_3);
  }
}

/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.CIDashboard;

import static io.harness.rule.OwnerRule.EBTASAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.app.beans.entities.BuildFailureInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.core.ci.dashboard.CIOverviewDashboardServiceImpl;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class CIOverviewDashboardServiceImplTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private Connection mockConnection;
  @Mock private PreparedStatement mockPreparedStatement;
  @Mock private ResultSet mockResultSet;
  @Mock CIFeatureFlagService ciFeatureFlagService;

  @InjectMocks private CIOverviewDashboardServiceImpl service;

  private static final String ACCOUNT_ID = "account123";
  private static final String ORG_ID = "org456";
  private static final String PROJECT_ID = "project789";
  private static final long LIMIT = 10;
  private static final long START_INTERVAL = 0;
  private static final long END_INTERVAL = 10;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testQueryCalculatorBuildFailureInfo_Success() throws SQLException {
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(true, false); // first true, then end
    when(mockResultSet.getString("startts")).thenReturn("1713000000000");
    when(mockResultSet.getString("endts")).thenReturn("1713003600000");
    when(mockResultSet.getString("name")).thenReturn("MyPipeline");
    when(mockResultSet.getString("pipelineidentifier")).thenReturn("pipeline123");
    when(mockResultSet.getString("moduleinfo_branch_name")).thenReturn("main");
    when(mockResultSet.getString("moduleinfo_branch_commit_message")).thenReturn("Initial commit");
    when(mockResultSet.getString("moduleinfo_event")).thenReturn("push");
    when(mockResultSet.getString("moduleinfo_repository")).thenReturn("my-repo");
    when(mockResultSet.getString("planexecutionid")).thenReturn("plan123");
    when(mockResultSet.getString("source_branch")).thenReturn("feature");
    when(mockResultSet.getString("moduleinfo_branch_commit_id")).thenReturn("commit123");
    when(mockResultSet.getString("moduleinfo_author_id")).thenReturn("dev1");
    when(mockResultSet.getString("author_avatar")).thenReturn("http://avatar");
    when(mockResultSet.getString("trigger_type")).thenReturn("WEBHOOK");
    when(mockResultSet.getString("status")).thenReturn("FAILED");
    when(mockResultSet.getString("id")).thenReturn("service123");
    List<BuildFailureInfo> result =
        service.queryCalculatorBuildFailureInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, LIMIT, START_INTERVAL, END_INTERVAL);
    assertEquals(1, result.size());
    assertEquals("FAILED", result.get(0).getStatus());
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testFailedList_DoesNotIncludeIgnoreFailed() throws Exception {
    String accountId = "acc123";
    String orgId = "org456";
    String projectId = "proj789";
    long limit = 10L;

    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    ResultSet mockResultSet = mock(ResultSet.class);

    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
    when(mockStatement.executeQuery()).thenReturn(mockResultSet);
    when(mockResultSet.next()).thenReturn(false);

    CIOverviewDashboardServiceImpl spyClass = Mockito.spy(service); // replace with your instance

    spyClass.queryCalculatorBuildFailureInfo(accountId, orgId, projectId, limit, START_INTERVAL, END_INTERVAL);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockConnection).prepareStatement(queryCaptor.capture());
    String capturedQuery = queryCaptor.getValue();

    assertThat(capturedQuery).contains("FAILED");
    assertThat(capturedQuery).contains("ABORTED");
    assertThat(capturedQuery).contains("EXPIRED");
    assertThat(capturedQuery).contains("ERRORED");

    assertThat(capturedQuery).doesNotContain("IGNOREFAILED");
  }
}

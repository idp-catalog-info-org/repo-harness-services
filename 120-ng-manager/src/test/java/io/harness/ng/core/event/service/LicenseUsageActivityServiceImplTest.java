///*
// * Copyright 2024 Harness Inc. All rights reserved.
// * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// * that can be found in the licenses directory at the root of this repository, also available at
// * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
// */

package io.harness.ng.core.event.service;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.NEELAM;
import static io.harness.rule.OwnerRule.NITIKA;
import static io.harness.rule.OwnerRule.RAGHAV_MURALI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.licenseusage.services.LicenseUsageActivityServiceImpl;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.CreditUsage;
import io.harness.spec.server.ng.v1.model.LicenseUsageActivity;
import io.harness.timescaledb.TimeScaleDBService;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PL)
public class LicenseUsageActivityServiceImplTest extends CategoryTest {
  PreparedStatement statement = mock(PreparedStatement.class);
  @Mock private TimeScaleDBService timeScaleDBService;
  ResultSet resultSet = mock(ResultSet.class);
  Connection connection = mock(Connection.class);

  @InjectMocks @Spy private LicenseUsageActivityServiceImpl service;

  @Before
  public void setUp() throws SQLException {
    MockitoAnnotations.initMocks(this);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(statement.executeUpdate()).thenReturn(1);
    when(connection.prepareStatement(any())).thenReturn(statement);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
  }

  @Test
  @Owner(developers = NITIKA)
  public void testGetLicenseUsageActivity_Successful() throws Exception {
    // Setup

    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getLong("utc_timestamp")).thenReturn(1715238000000L);
    when(resultSet.getString("ci_os_type")).thenReturn("Linux");
    when(resultSet.getInt("used_credits")).thenReturn(100);

    // Execute
    List<LicenseUsageActivity> result = service.getLicenseUsageActivity("account1", "CI", 1609459200, 1612137600,
        Arrays.asList("org1"), Arrays.asList("proj1"), Arrays.asList("pipeline1"), Arrays.asList("resource1"), false);

    // Verify
    assertNotNull(result);
    assertEquals(1, result.size());
    LicenseUsageActivity response = result.get(0);
    assertEquals(1715238000000L, (long) response.getTimestamp());
    assertEquals(100, (int) response.getCredits().get(0).getTotalCredits());
    assertEquals("Linux", response.getCredits().get(0).getCiOsType());

    verify(statement, times(1)).setString(1, "account1");
    verify(statement, times(1)).setString(2, "CI");
    verify(statement, times(1)).setString(3, "org1");
    verify(statement, times(1)).setString(4, "proj1");
    verify(statement, times(1)).setString(5, "pipeline1");
    verify(statement, times(1)).setString(6, "resource1");
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  public void testGetLicenseUsageActivity_Rollup_Successful() throws Exception {
    when(resultSet.next()).thenReturn(true, true, true, true, true, false);
    when(resultSet.getLong("utc_timestamp"))
        .thenReturn(1234567890L, 1234567893L, 1234567890L, 1234567890L, 1234567893L);
    when(resultSet.getString("ci_os_type")).thenReturn("Linux", "Linux", "MacOs", "MacOs", "Windows");
    when(resultSet.getInt("used_credits")).thenReturn(100, 150, 200, 100, 400);

    // Execute
    List<LicenseUsageActivity> result = service.getLicenseUsageActivity("account1", "CI", 1609459200, 1612137600,
        Arrays.asList("org1"), Arrays.asList("proj1"), Arrays.asList("pipeline1"), Arrays.asList("resource1"), true);

    // Verify
    assertNotNull(result);
    assertEquals(1, result.size());
    LicenseUsageActivity licenseUsageActivity = result.get(0);
    assertEquals(950, (long) licenseUsageActivity.getCredits().get(0).getTotalCredits());

    verify(statement, times(1)).setString(1, "account1");
    verify(statement, times(1)).setString(2, "CI");
    verify(statement, times(1)).setString(3, "org1");
    verify(statement, times(1)).setString(4, "proj1");
    verify(statement, times(1)).setString(5, "pipeline1");
    verify(statement, times(1)).setString(6, "resource1");
  }

  @Test
  @Owner(developers = NEELAM)
  public void testGetLicenseUsageActivity_AggregationByTimestampAndCIOsType() throws SQLException {
    // Mocking the ResultSet to return multiple rows for the same timestamp with different ciOsType
    when(resultSet.next()).thenReturn(true, true, true, false);
    when(resultSet.getLong("utc_timestamp"))
        .thenReturn(1627545600000L, 1627545600000L, 1627545600000L); // Same timestamp
    when(resultSet.getString("ci_os_type")).thenReturn("Linux", "Windows", "Linux");
    when(resultSet.getInt("used_credits")).thenReturn(100, 200, 300);

    // Execute
    List<LicenseUsageActivity> result =
        service.getLicenseUsageActivity("account1", "CI", 1627545600000L, 1627632000000L, Arrays.asList("org1"),
            Arrays.asList("proj1"), Arrays.asList("pipeline1"), Arrays.asList("resourceClass1"), false);

    // Verify that two results are aggregated into one activity based on timestamp
    assertNotNull(result);
    assertEquals(1, result.size());

    LicenseUsageActivity activity = result.get(0);
    assertEquals(1627545600000L, activity.getTimestamp().longValue()); // Check timestamp

    List<CreditUsage> credits = activity.getCredits();
    assertEquals(2, credits.size()); // Two ciOsTypes

    // Verify the first OS type credit usage
    CreditUsage linuxUsage = credits.stream().filter(c -> c.getCiOsType().equals("Linux")).findFirst().orElse(null);
    assertNotNull(linuxUsage);
    assertEquals(400, linuxUsage.getTotalCredits().longValue());

    // Verify the second OS type credit usage
    CreditUsage windowsUsage = credits.stream().filter(c -> c.getCiOsType().equals("Windows")).findFirst().orElse(null);
    assertNotNull(windowsUsage);
    assertEquals(200, windowsUsage.getTotalCredits().longValue());

    // Verify no more interactions with the ResultSet
    verify(resultSet, times(3)).getLong("utc_timestamp");
    verify(resultSet, times(3)).getString("ci_os_type");
    verify(resultSet, times(3)).getInt("used_credits");
    verify(resultSet, times(4)).next();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  public void testGetLicenseUsageActivity_nullModuleType_returnsAllModules() throws Exception {
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getLong("utc_timestamp")).thenReturn(1715238000000L);
    when(resultSet.getString("ci_os_type")).thenReturn("Linux");
    when(resultSet.getInt("used_credits")).thenReturn(100);

    List<LicenseUsageActivity> result = service.getLicenseUsageActivity("account1", null, 1609459200, 1612137600,
        Arrays.asList("org1"), Arrays.asList("proj1"), Arrays.asList("pipeline1"), Arrays.asList("resource1"), false);

    assertNotNull(result);
    assertEquals(1, result.size());

    // moduleType is null, so parameter index 2 should be "org1", not "CI"
    verify(statement, times(1)).setString(1, "account1");
    verify(statement, times(1)).setString(2, "org1");
    verify(statement, times(1)).setString(3, "proj1");
    verify(statement, times(1)).setString(4, "pipeline1");
    verify(statement, times(1)).setString(5, "resource1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  public void testExportLicenseUsageActivityData_nullModuleType_returnsAllModules() throws Exception {
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getString("account_identifier")).thenReturn("account1");
    when(resultSet.getString("organization_identifier")).thenReturn("org1");
    when(resultSet.getString("project_identifier")).thenReturn("proj1");
    when(resultSet.getString("pipeline_identifier")).thenReturn("pipeline1");
    when(resultSet.getLong("utc_timestamp")).thenReturn(1715238000000L);
    when(resultSet.getString("stage_identifier")).thenReturn("stage1");
    when(resultSet.getString("ci_os_type")).thenReturn("Linux");
    when(resultSet.getString("ci_resource_class")).thenReturn("resource1");
    when(resultSet.getInt("used_credits")).thenReturn(100);
    when(resultSet.getString("module_type")).thenReturn("CI");

    File result = service.exportLicenseUsageActivityData("account1", null, 1609459200, 1612137600,
        Arrays.asList("org1"), Arrays.asList("proj1"), Arrays.asList("pipeline1"), Arrays.asList("resource1"));

    assertNotNull(result);

    // moduleType is null, so parameter index 2 should be "org1", not a module type
    verify(statement, times(1)).setString(1, "account1");
    verify(statement, times(1)).setString(2, "org1");
    verify(statement, times(1)).setString(3, "proj1");
    verify(statement, times(1)).setString(4, "pipeline1");
    verify(statement, times(1)).setString(5, "resource1");
  }
}
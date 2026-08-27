/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.ResourceTypes.ACCOUNT;
import static io.harness.ci.api.AnnotationUtils.assertParameterCounts;
import static io.harness.cimanager.dashboard.api.CIDashboardOverviewResource.PROJECT_RESOURCE_TYPE;
import static io.harness.cimanager.dashboard.api.CIDashboardOverviewResource.VIEW_PROJECT_PERMISSION;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.CHIRAG_S;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.BuildActiveInfo;
import io.harness.app.beans.entities.BuildFailureInfo;
import io.harness.app.beans.entities.CICreditsResult;
import io.harness.app.beans.entities.CIUsageResult;
import io.harness.app.beans.entities.DashboardBuildExecutionInfo;
import io.harness.app.beans.entities.DashboardBuildRepositoryInfo;
import io.harness.app.beans.entities.DashboardBuildsActiveAndFailedInfo;
import io.harness.app.beans.entities.DashboardBuildsHealthInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.api.CIDashboardOverviewResourceImpl;
import io.harness.core.ci.dashboard.CIOverviewDashboardService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.dashboards.GroupBy;
import io.harness.rule.Owner;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class CIDashboardOverviewResourceImplTest {
  @Mock private CIOverviewDashboardService ciOverviewDashboardService;
  @InjectMocks private CIDashboardOverviewResourceImpl ciDashboardOverviewResource;

  private static final String ACCOUNT_ID = "testAccount";
  private static final String ORG_ID = "testOrg";
  private static final String PROJECT_ID = "testProject";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetBuildHealthAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getBuildHealth method
    Method getBuildHealthMethod = CIDashboardOverviewResourceImpl.class.getDeclaredMethod(
        "getBuildHealth", String.class, String.class, String.class, long.class, long.class);
    assertTrue(getBuildHealthMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(PROJECT_RESOURCE_TYPE, getBuildHealthMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(VIEW_PROJECT_PERMISSION, getBuildHealthMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getBuildHealthMethod, 1, AccountIdentifier.class);
    assertParameterCounts(getBuildHealthMethod, 1, OrgIdentifier.class);
    assertParameterCounts(getBuildHealthMethod, 1, ProjectIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetBuildExecutionAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getBuildExecution method
    Method getBuildExecutionMethod = CIDashboardOverviewResourceImpl.class.getDeclaredMethod(
        "getBuildExecution", String.class, String.class, String.class, GroupBy.class, long.class, long.class);
    assertTrue(getBuildExecutionMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, getBuildExecutionMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(
        VIEW_ACCOUNT_PERMISSION, getBuildExecutionMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getBuildExecutionMethod, 1, AccountIdentifier.class);
    assertParameterCounts(getBuildExecutionMethod, 1, OrgIdentifier.class);
    assertParameterCounts(getBuildExecutionMethod, 1, ProjectIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetRepositoryBuildAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getRepositoryBuild method
    Method getRepositoryBuildMethod = CIDashboardOverviewResourceImpl.class.getDeclaredMethod(
        "getRepositoryBuild", String.class, String.class, String.class, long.class, long.class);
    assertTrue(getRepositoryBuildMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(
        PROJECT_RESOURCE_TYPE, getRepositoryBuildMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(
        VIEW_PROJECT_PERMISSION, getRepositoryBuildMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getRepositoryBuildMethod, 1, AccountIdentifier.class);
    assertParameterCounts(getRepositoryBuildMethod, 1, OrgIdentifier.class);
    assertParameterCounts(getRepositoryBuildMethod, 1, ProjectIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetActiveAndFailedBuildAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getActiveAndFailedBuild method
    Method getActiveAndFailedBuildMethod = CIDashboardOverviewResourceImpl.class.getDeclaredMethod(
        "getActiveAndFailedBuild", String.class, String.class, String.class, long.class, long.class, long.class);
    assertTrue(getActiveAndFailedBuildMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(
        PROJECT_RESOURCE_TYPE, getActiveAndFailedBuildMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(
        VIEW_PROJECT_PERMISSION, getActiveAndFailedBuildMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getActiveAndFailedBuildMethod, 1, AccountIdentifier.class);
    assertParameterCounts(getActiveAndFailedBuildMethod, 1, OrgIdentifier.class);
    assertParameterCounts(getActiveAndFailedBuildMethod, 1, ProjectIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetCIUsageDataAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getCIUsageData method
    Method getCIUsageDataMethod =
        CIDashboardOverviewResourceImpl.class.getDeclaredMethod("getCIUsageData", String.class, long.class);
    assertTrue(getCIUsageDataMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, getCIUsageDataMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(VIEW_ACCOUNT_PERMISSION, getCIUsageDataMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getCIUsageDataMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetCreditsAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getCredits method
    Method getCreditsMethod =
        CIDashboardOverviewResourceImpl.class.getDeclaredMethod("getCredits", String.class, long.class, long.class);
    assertTrue(getCreditsMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, getCreditsMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(VIEW_ACCOUNT_PERMISSION, getCreditsMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getCreditsMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBuildHealth() {
    long startInterval = 1000L;
    long endInterval = 2000L;

    DashboardBuildsHealthInfo healthInfo = DashboardBuildsHealthInfo.builder().build();
    when(ciOverviewDashboardService.getDashBoardBuildHealthInfoWithRate(ACCOUNT_ID, ORG_ID, PROJECT_ID, startInterval,
             endInterval, startInterval - (endInterval - startInterval + 24 * 60 * 60 * 1000)))
        .thenReturn(healthInfo);

    ResponseDTO<DashboardBuildsHealthInfo> response =
        ciDashboardOverviewResource.getBuildHealth(ACCOUNT_ID, ORG_ID, PROJECT_ID, startInterval, endInterval);

    assertEquals(healthInfo, response.getData());
    verify(ciOverviewDashboardService, times(1))
        .getDashBoardBuildHealthInfoWithRate(ACCOUNT_ID, ORG_ID, PROJECT_ID, startInterval, endInterval,
            startInterval - (endInterval - startInterval + 24 * 60 * 60 * 1000));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBuildExecution() {
    long startInterval = 1000L;
    long endInterval = 2000L;
    GroupBy groupBy = GroupBy.DAY;

    DashboardBuildExecutionInfo executionInfo = DashboardBuildExecutionInfo.builder().build();
    when(ciOverviewDashboardService.getBuildExecutionBetweenIntervals(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, groupBy, startInterval, endInterval))
        .thenReturn(executionInfo);

    ResponseDTO<DashboardBuildExecutionInfo> response = ciDashboardOverviewResource.getBuildExecution(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, groupBy, startInterval, endInterval);

    assertEquals(executionInfo, response.getData());
    verify(ciOverviewDashboardService, times(1))
        .getBuildExecutionBetweenIntervals(ACCOUNT_ID, ORG_ID, PROJECT_ID, groupBy, startInterval, endInterval);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetRepositoryBuild() {
    long startInterval = 1000L;
    long endInterval = 2000L;

    DashboardBuildRepositoryInfo repoInfo = DashboardBuildRepositoryInfo.builder().build();
    when(ciOverviewDashboardService.getDashboardBuildRepository(ACCOUNT_ID, ORG_ID, PROJECT_ID, startInterval,
             endInterval, startInterval - (endInterval - startInterval + 24 * 60 * 60 * 1000)))
        .thenReturn(repoInfo);

    ResponseDTO<DashboardBuildRepositoryInfo> response =
        ciDashboardOverviewResource.getRepositoryBuild(ACCOUNT_ID, ORG_ID, PROJECT_ID, startInterval, endInterval);

    assertEquals(repoInfo, response.getData());
    verify(ciOverviewDashboardService, times(1))
        .getDashboardBuildRepository(ACCOUNT_ID, ORG_ID, PROJECT_ID, startInterval, endInterval,
            startInterval - (endInterval - startInterval + 24 * 60 * 60 * 1000));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetActiveAndFailedBuildWithNonZeroInterval() {
    long startInterval = 1000L;
    long endInterval = 2000L;
    long days = 7L;

    List<BuildFailureInfo> failureInfos = Collections.emptyList();
    List<BuildActiveInfo> activeInfos = Collections.emptyList();

    when(ciOverviewDashboardService.getDashboardBuildFailureInfo(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, days, startInterval, endInterval))
        .thenReturn(failureInfos);
    when(ciOverviewDashboardService.getDashboardBuildActiveInfo(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, days, startInterval, endInterval))
        .thenReturn(activeInfos);

    ResponseDTO<DashboardBuildsActiveAndFailedInfo> response = ciDashboardOverviewResource.getActiveAndFailedBuild(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, startInterval, endInterval, days);

    assertNotNull(response.getData());
    assertEquals(failureInfos, response.getData().getFailed());
    assertEquals(activeInfos, response.getData().getActive());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetActiveAndFailedBuildWithZeroInterval() {
    long days = 7L;

    List<BuildFailureInfo> failureInfos = Collections.emptyList();
    List<BuildActiveInfo> activeInfos = Collections.emptyList();

    when(ciOverviewDashboardService.getDashboardBuildFailureInfo(org.mockito.ArgumentMatchers.eq(ACCOUNT_ID),
             org.mockito.ArgumentMatchers.eq(ORG_ID), org.mockito.ArgumentMatchers.eq(PROJECT_ID),
             org.mockito.ArgumentMatchers.eq(days), org.mockito.ArgumentMatchers.anyLong(),
             org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(failureInfos);
    when(ciOverviewDashboardService.getDashboardBuildActiveInfo(org.mockito.ArgumentMatchers.eq(ACCOUNT_ID),
             org.mockito.ArgumentMatchers.eq(ORG_ID), org.mockito.ArgumentMatchers.eq(PROJECT_ID),
             org.mockito.ArgumentMatchers.eq(days), org.mockito.ArgumentMatchers.anyLong(),
             org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(activeInfos);

    ResponseDTO<DashboardBuildsActiveAndFailedInfo> response =
        ciDashboardOverviewResource.getActiveAndFailedBuild(ACCOUNT_ID, ORG_ID, PROJECT_ID, 0, 0, days);

    assertNotNull(response.getData());
    assertEquals(failureInfos, response.getData().getFailed());
    assertEquals(activeInfos, response.getData().getActive());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCIUsageData() {
    long timestamp = 1000L;

    CIUsageResult usageResult = CIUsageResult.builder().build();
    when(ciOverviewDashboardService.getCIUsageResult(ACCOUNT_ID, timestamp)).thenReturn(usageResult);

    ResponseDTO<CIUsageResult> response = ciDashboardOverviewResource.getCIUsageData(ACCOUNT_ID, timestamp);

    assertEquals(usageResult, response.getData());
    verify(ciOverviewDashboardService, times(1)).getCIUsageResult(ACCOUNT_ID, timestamp);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCredits() throws Exception {
    long startInterval = 1000L;
    long endInterval = 2000L;
    long credits = 500L;

    when(ciOverviewDashboardService.getHostedCreditUsage(ACCOUNT_ID, startInterval, endInterval)).thenReturn(credits);

    ResponseDTO<CICreditsResult> response =
        ciDashboardOverviewResource.getCredits(ACCOUNT_ID, startInterval, endInterval);

    assertNotNull(response.getData());
    assertEquals(credits, response.getData().getCredits());
    verify(ciOverviewDashboardService, times(1)).getHostedCreditUsage(ACCOUNT_ID, startInterval, endInterval);
  }
}

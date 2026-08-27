/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.ResourceTypes.ACCOUNT;

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
import io.harness.cimanager.dashboard.api.CIDashboardOverviewResource;
import io.harness.core.ci.dashboard.CIOverviewDashboardService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.dashboards.GroupBy;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class CIDashboardOverviewResourceImpl implements CIDashboardOverviewResource {
  private final CIOverviewDashboardService ciOverviewDashboardService;
  private final long HR_IN_MS = 60 * 60 * 1000;
  private final long DAY_IN_MS = 24 * HR_IN_MS;

  @NGAccessControlCheck(resourceType = PROJECT_RESOURCE_TYPE, permission = VIEW_PROJECT_PERMISSION)
  public ResponseDTO<DashboardBuildsHealthInfo> getBuildHealth(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, long startInterval,
      long endInterval) {
    long previousInterval = startInterval - (endInterval - startInterval + DAY_IN_MS);

    return ResponseDTO.newResponse(ciOverviewDashboardService.getDashBoardBuildHealthInfoWithRate(
        accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, previousInterval));
  }

  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<DashboardBuildExecutionInfo> getBuildExecution(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, GroupBy groupby,
      long startInterval, long endInterval) {
    return ResponseDTO.newResponse(ciOverviewDashboardService.getBuildExecutionBetweenIntervals(
        accountIdentifier, orgIdentifier, projectIdentifier, groupby, startInterval, endInterval));
  }

  @NGAccessControlCheck(resourceType = PROJECT_RESOURCE_TYPE, permission = VIEW_PROJECT_PERMISSION)
  public ResponseDTO<DashboardBuildRepositoryInfo> getRepositoryBuild(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, long startInterval,
      long endInterval) {
    long previousInterval = startInterval - (endInterval - startInterval + DAY_IN_MS);
    return ResponseDTO.newResponse(ciOverviewDashboardService.getDashboardBuildRepository(
        accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, previousInterval));
  }

  @NGAccessControlCheck(resourceType = PROJECT_RESOURCE_TYPE, permission = VIEW_PROJECT_PERMISSION)
  public ResponseDTO<DashboardBuildsActiveAndFailedInfo> getActiveAndFailedBuild(
      @AccountIdentifier String accountIdentifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, long startInterval, long endInterval, long days) {
    if (startInterval == 0 || endInterval == 0) {
      startInterval = System.currentTimeMillis() - (7 * DAY_IN_MS);
      endInterval = System.currentTimeMillis();
    }
    List<BuildFailureInfo> failureInfos = ciOverviewDashboardService.getDashboardBuildFailureInfo(
        accountIdentifier, orgIdentifier, projectIdentifier, days, startInterval, endInterval);
    List<BuildActiveInfo> activeInfos = ciOverviewDashboardService.getDashboardBuildActiveInfo(
        accountIdentifier, orgIdentifier, projectIdentifier, days, startInterval, endInterval);

    return ResponseDTO.newResponse(
        DashboardBuildsActiveAndFailedInfo.builder().failed(failureInfos).active(activeInfos).build());
  }

  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<CIUsageResult> getCIUsageData(@AccountIdentifier String accountIdentifier, long timestamp) {
    return ResponseDTO.newResponse(ciOverviewDashboardService.getCIUsageResult(accountIdentifier, timestamp));
  }

  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<CICreditsResult> getCredits(
      @AccountIdentifier String accountIdentifier, long startInterval, long endInterval) throws Exception {
    long credits = ciOverviewDashboardService.getHostedCreditUsage(accountIdentifier, startInterval, endInterval);
    return ResponseDTO.newResponse(CICreditsResult.builder().credits(credits).build());
  }
}
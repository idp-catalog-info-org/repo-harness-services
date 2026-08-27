/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.licenseusage.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.ng.v1.model.LicenseUsageActivity;

import java.io.File;
import java.util.List;

@OwnedBy(HarnessTeam.PL)
public interface LicenseUsageActivityService {
  /**
   * Given an accountIdentifier, provides the LicenseUsageActivity for the given time range
   * and additional filters if any
   * @param accountIdentifier   AccountIdentifier of the harness user
   * @param moduleType          Type of the module
   * @param startTime           Start Interval for fetching the data
   * @param endTime             End Interval for fetching the data
   * @param orgIdentifiers      List of organizationIds to filter if provided
   * @param projectIdentifiers  List of projectIds to filter if provided
   * @param pipelineIdentifiers List of pipelineIds to filter if provided
   * @param resourceClasses     List of resourceClasses to filter if provided
   * @return                    a Page of LicenseUsageActivityHourlyDTOs
   */
  List<LicenseUsageActivity> getLicenseUsageActivity(String accountIdentifier, String moduleType, long startTime,
      long endTime, List<String> orgIdentifiers, List<String> projectIdentifiers, List<String> pipelineIdentifiers,
      List<String> resourceClasses, boolean rollup);

  File exportLicenseUsageActivityData(String accountIdentifier, String moduleType, long startTime, long endTime,
      List<String> orgIdentifiers, List<String> projectIdentifiers, List<String> pipelineIdentifiers,
      List<String> resourceClasses);
}

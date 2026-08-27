/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import io.harness.ng.core.licenseusage.services.LicenseUsageActivityService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.LicenseUsageDataByAccountApi;
import io.harness.spec.server.ng.v1.model.LicenseUsageActivityFilterPropertiesDTO;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;

@NextGenManagerAuth
@Timed
@ResponseMetered
public class LicenseUsageActivityApiImpl implements LicenseUsageDataByAccountApi {
  @Inject private LicenseUsageActivityService licenseUsageActivityService;
  @Override
  public Response licenseUsageActivity(@Valid LicenseUsageActivityFilterPropertiesDTO licenseUsageActivityFilter,
      @NotNull Long startTime, @NotNull Long endTime, @NotNull Boolean rollup, String accountIdentifier) {
    if (StringUtils.isBlank(accountIdentifier)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Empty accountId is not a valid value").build();
    }
    if (!isValidTimestamp(startTime)) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("startTime is not valid, provide a valid value and retry...")
          .build();
    }
    if (!isValidTimestamp(endTime)) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("endTime is not valid, provide a valid value and retry...")
          .build();
    }
    if (startTime > endTime) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("Provide a valid time range. startTime can not be greater than endTime.")
          .build();
    }

    List<String> orgIdentifiers = licenseUsageActivityFilter.getOrganizationIdentifiers();
    List<String> projectIdentifiers = licenseUsageActivityFilter.getProjectIdentifiers();
    List<String> pipelineIdentifiers = licenseUsageActivityFilter.getPipelineIdentifiers();
    List<String> resourceClasses = licenseUsageActivityFilter.getResourceClasses();

    if (projectIdentifiers != null && orgIdentifiers == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("orgIdentifiers can not be null when the projectIdentifiers filter is provided.")
          .build();
    }
    if (pipelineIdentifiers != null && projectIdentifiers == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("projectIdentifiers can not be null when the pipelineIdentifiers filter is provided.")
          .build();
    }
    if (projectIdentifiers != null && resourceClasses == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("resourceClasses can not be null when the projectIdentifiers filter is provided.")
          .build();
    }

    String moduleType = licenseUsageActivityFilter.getModuleType() != null
        ? licenseUsageActivityFilter.getModuleType().toString()
        : null;

    return Response.status(Response.Status.OK)
        .entity(licenseUsageActivityService.getLicenseUsageActivity(accountIdentifier, moduleType, startTime, endTime,
            licenseUsageActivityFilter.getOrganizationIdentifiers(), licenseUsageActivityFilter.getProjectIdentifiers(),
            licenseUsageActivityFilter.getPipelineIdentifiers(), licenseUsageActivityFilter.getResourceClasses(),
            rollup))
        .build();
  }

  private boolean isValidTimestamp(long timestamp) {
    // Ensure the timestamp is not negative and not too large
    long maxTimestamp = Long.MAX_VALUE - 1_000_000L; // Subtract a million milliseconds for a safety margin
    return timestamp > 0L && timestamp < maxTimestamp;
  }
}

/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import io.harness.eraro.ResponseMessage;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.services.DevopsEssentialsService;
import io.harness.spec.server.ng.v1.AccountLicenseTypeApi;

import com.codahale.metrics.annotation.ResponseMetered;
import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;

@NextGenManagerAuth
@ResponseMetered
public class AccountLicenseTypeApiImpl implements AccountLicenseTypeApi {
  @Inject private DevopsEssentialsService devopsEssentialsService;
  @Override
  public Response getLicenseType(String accountIdentifier) {
    if (StringUtils.isBlank(accountIdentifier)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Empty accountId is not a valid value").build();
    }
    try {
      return Response.status(Response.Status.OK)
          .entity(devopsEssentialsService.getAccountLicenseType(accountIdentifier))
          .build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}

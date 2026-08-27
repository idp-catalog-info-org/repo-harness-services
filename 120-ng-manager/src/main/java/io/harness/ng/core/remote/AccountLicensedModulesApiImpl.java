/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import io.harness.ng.core.licensedmodules.services.LicensedModulesService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.AccountLicensedModulesApi;
import io.harness.spec.server.ng.v1.model.LicensedModules;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;

@NextGenManagerAuth
@Timed
@ResponseMetered
public class AccountLicensedModulesApiImpl implements AccountLicensedModulesApi {
  @Inject private LicensedModulesService licensedModulesService;

  @Override
  public Response getAccountLicensedModules(String harnessAccount) {
    if (StringUtils.isBlank(harnessAccount)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Empty accountId is not a valid value").build();
    }

    LicensedModules licensedModules = licensedModulesService.getLicensedModulesForAccount(harnessAccount);

    return Response.status(Response.Status.OK).entity(licensedModules).build();
  }
}

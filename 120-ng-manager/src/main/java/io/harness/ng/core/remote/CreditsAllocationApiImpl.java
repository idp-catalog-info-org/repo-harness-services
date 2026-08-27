/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import io.harness.credit.services.CreditService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.CreditsAllocationByAccountAndModuleTypeApi;

import com.google.inject.Inject;
import java.io.File;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;

@NextGenManagerAuth
public class CreditsAllocationApiImpl implements CreditsAllocationByAccountAndModuleTypeApi {
  @Inject private CreditService creditService;

  @Override
  public Response creditsAllocationExportData(String accountIdentifier, String moduleType) {
    if (StringUtils.isBlank(accountIdentifier)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Empty accountId is not a valid value").build();
    }

    File file = creditService.getCreditsByAccountIdAndModuleType(accountIdentifier, moduleType);

    if (file == null) {
      return Response.serverError().entity("Error creating CSV file").build();
    }

    // TODO: If the files gets bigger, stream the file as JSON Object / CSV
    return Response.ok(file).build();
  }
}

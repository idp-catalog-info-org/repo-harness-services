/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.resource;

import io.harness.exception.InvalidArgumentsException;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.services.DefaultLicenseServiceImpl;
import io.harness.ngsubscriptions.service.NGSubscriptionsService;
import io.harness.spec.server.ng.v1.ModuleAccessApi;
import io.harness.spec.server.ng.v1.model.HasAnyModuleAccessResponse;
import io.harness.spec.server.ng.v1.model.ModuleType;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ModuleAccessApiImpl implements ModuleAccessApi {
  @Inject NGSubscriptionsService ngSubscriptionsService;
  @Inject DefaultLicenseServiceImpl defaultLicenseService;

  @Override
  public Response filterV1ModuleAccess(@NotNull String userIdentifier, @NotNull String licenseType,
      String accountIdentifier, @Valid List<String> body, String harnessAccount) {
    if (harnessAccount == null || harnessAccount.isEmpty()) {
      throw new InvalidArgumentsException("Missing harness account parameter in request");
    }

    if (!harnessAccount.equals(accountIdentifier)) {
      throw new InvalidArgumentsException(
          "Invalid harness account parameter. Account in path must be same as account in request scope. Request scope account: "
          + harnessAccount + " Account in path: " + accountIdentifier);
    }

    if (body == null || body.isEmpty()) {
      throw new InvalidArgumentsException("Input must contain at least one module type");
    }

    if (!"Dev360".equals(licenseType)) {
      throw new IllegalArgumentException("Only Dev360 license type is supported. Input license type: " + licenseType);
    }

    Set<String> moduleTypeInputSet = new HashSet<>(body);

    Map<ModuleType, Boolean> dev360ModuleAccessForAccountAndUser =
        ngSubscriptionsService.getDev360ModuleAccessForAccountAndUser(
            accountIdentifier, userIdentifier, moduleTypeInputSet);

    return Response.status(Response.Status.OK).entity(dev360ModuleAccessForAccountAndUser).build();
  }

  @Override
  public Response getV1AnyModuleAccess(String accountIdentifier, @NotNull String licenseType, String harnessAccount) {
    if (harnessAccount == null || harnessAccount.isEmpty()) {
      throw new InvalidArgumentsException("Missing harness account parameter in request");
    }

    if (!harnessAccount.equals(accountIdentifier)) {
      throw new InvalidArgumentsException(
          "Invalid harness account parameter. Account in path must be same as account in request scope. Request scope account: "
          + harnessAccount + " Account in path: " + accountIdentifier);
    }

    if (!"Dev360".equals(licenseType)) {
      throw new IllegalArgumentException("Only Dev360 license type is supported. Input license type: " + licenseType);
    }

    List<ModuleLicenseDTO> dev360ModuleLicenses = defaultLicenseService.getDev360ModuleLicenses(accountIdentifier);

    HasAnyModuleAccessResponse hasAnyModuleAccessResponse = new HasAnyModuleAccessResponse();
    hasAnyModuleAccessResponse.setDev360Access(!dev360ModuleLicenses.isEmpty());

    return Response.status(Response.Status.OK).entity(hasAnyModuleAccessResponse).build();
  }
}

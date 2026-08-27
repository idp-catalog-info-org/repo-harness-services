/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.resource;

import io.harness.exception.InvalidArgumentsException;
import io.harness.ngsubscriptions.service.NGSubscriptionsService;
import io.harness.spec.server.ng.v1.UpdateModuleAccessApi;
import io.harness.spec.server.ng.v1.model.V1UpdateModuleAccessBody;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class UpdateModuleAccessApiImpl implements UpdateModuleAccessApi {
  @Inject NGSubscriptionsService subscriptionsService;
  @Override
  public Response putV1UpdateModuleAccess(@Valid V1UpdateModuleAccessBody body, String harnessAccount) {
    if (harnessAccount.isEmpty()) {
      throw new InvalidArgumentsException("Missing account identifier or module type");
    }

    try {
      subscriptionsService.updateModuleAccess(harnessAccount, body.getEntities());
    } catch (Exception ex) {
      log.error(ex.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
    return Response.status(Response.Status.OK).build();
  }
}

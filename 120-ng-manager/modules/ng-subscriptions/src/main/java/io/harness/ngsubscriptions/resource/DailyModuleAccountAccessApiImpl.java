/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.resource;

import io.harness.ngsubscriptions.service.NGSubscriptionsService;
import io.harness.spec.server.ng.v1.DailyModuleAccountAccessApi;
import io.harness.spec.server.ng.v1.model.DailyModuleAccountAccessDTO;
import io.harness.spec.server.ng.v1.model.ModuleType;

import com.google.inject.Inject;
import java.util.Calendar;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class DailyModuleAccountAccessApiImpl implements DailyModuleAccountAccessApi {
  @Inject NGSubscriptionsService subscriptionsService;

  @Override
  public Response getV1DailyModuleAccountAccess(@NotNull Integer year, @NotNull Integer month,
      @NotNull String moduleType, @Valid Object body, String harnessAccount) {
    Calendar calendarIncoming = Calendar.getInstance(); // Get the current date first
    calendarIncoming.set(Calendar.YEAR, year);
    calendarIncoming.set(Calendar.MONTH, month);
    Calendar calendarNow = Calendar.getInstance();
    if (!harnessAccount.isBlank() && !calendarIncoming.after(calendarNow)) {
      List<DailyModuleAccountAccessDTO> result =
          subscriptionsService.getModuleAccountAccessList(harnessAccount, ModuleType.valueOf(moduleType), year, month);
      return Response.status(Response.Status.OK).entity(result).build();
    }
    return Response.status(Response.Status.BAD_REQUEST).build();
  }
}

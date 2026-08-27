/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.security.annotations.InternalApi;
import io.harness.spec.server.ng.v1.IncidentResponseApi;
import io.harness.spec.server.ng.v1.model.SLONotificationDTO;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class SLONotificationApiImpl implements IncidentResponseApi {
  @Inject final IRService irService;

  @InternalApi
  @Override
  public Response sloNotificationTrigger(@Valid SLONotificationDTO sloNotificationDTO, String harnessAccount) {
    irService.sloNotificationTriggerHandler(sloNotificationDTO, harnessAccount);
    return Response.status(Response.Status.OK).build();
  }
}

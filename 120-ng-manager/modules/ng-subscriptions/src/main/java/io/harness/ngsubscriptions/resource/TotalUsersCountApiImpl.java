/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.resource;

import io.harness.exception.InvalidArgumentsException;
import io.harness.ngsubscriptions.service.NGSubscriptionsService;
import io.harness.spec.server.ng.v1.TotalUsersCountApi;
import io.harness.spec.server.ng.v1.model.SubscriptionUsageDTO;

import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class TotalUsersCountApiImpl implements TotalUsersCountApi {
  @Inject NGSubscriptionsService subscriptionsService;

  @Override
  public Response getV1DevSubscriptions(String year, String harnessAccount) {
    if (harnessAccount.isEmpty() || year.isEmpty()) {
      throw new InvalidArgumentsException("Missing account identifier");
    }
    List<SubscriptionUsageDTO> usageDTOList =
        subscriptionsService.getSubscriptions(harnessAccount, Integer.parseInt(year));
    return Response.status(Response.Status.OK).entity(usageDTOList).build();
  }
}

/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.resource;

import io.harness.exception.IllegalArgumentException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ngsubscriptions.service.NGSubscriptionsService;
import io.harness.spec.server.ng.v1.PrincipalsWithAccessApi;
import io.harness.spec.server.ng.v1.model.InlineResponse200;
import io.harness.spec.server.ng.v1.model.ModuleType;
import io.harness.spec.server.ng.v1.model.PrincipalWithAccessFilter;
import io.harness.spec.server.ng.v1.model.PrincipalWithAccessResponse;
import io.harness.spec.server.ng.v1.model.UserWithAccessEntity;
import io.harness.spec.server.ng.v1.model.V1ListPrincipalsWithAccessBody;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PrincipalsWithAccessApiImpl implements PrincipalsWithAccessApi {
  @Inject NGSubscriptionsService subscriptionsService;
  public static final String PRINCIPAL_USER = "USER";
  public static final String PRINCIPAL_SERVICE_ACCOUNT = "SERVICE ACCOUNT";
  @Override
  public Response getV1ListPrincipalsWithAccess(
      @NotNull String moduleType, String harnessAccount, Integer page, @Max(1000L) Integer limit) {
    if (harnessAccount.isEmpty() || moduleType.isEmpty()) {
      throw new InvalidArgumentsException("Missing account identifier or module type");
    }

    try {
      ModuleType type = ModuleType.valueOf(moduleType.toUpperCase());
      PrincipalWithAccessResponse response = subscriptionsService.findPrincipals(harnessAccount, type);

      setPages(response, page, limit);

      InlineResponse200 inlineResponse200 = new InlineResponse200();
      inlineResponse200.setPrincipalsWithAccess(response);
      return Response.status(Response.Status.OK).entity(inlineResponse200).build();
    } catch (IllegalArgumentException ex) {
      throw new InvalidArgumentsException("Module type is incorrect");
    }
  }

  @Override
  public Response postV1ListPrincipalsWithAccess(@NotNull String moduleType, @Valid V1ListPrincipalsWithAccessBody body,
      String harnessAccount, Integer page, @Max(1000L) Integer limit) {
    if (harnessAccount.isEmpty() || moduleType.isEmpty()) {
      throw new InvalidArgumentsException("Missing account identifier or module type");
    }

    try {
      ModuleType type = ModuleType.valueOf(moduleType.toUpperCase());
      if (!validateFilter(body.getFilter())) {
        String msg = String.format(
            "Principal type is not valid. Valid values are (%s) and (%s)", PRINCIPAL_USER, PRINCIPAL_SERVICE_ACCOUNT);
        throw new InvalidArgumentsException(msg);
      }
      PrincipalWithAccessResponse response =
          subscriptionsService.findPrincipalsWithFilter(harnessAccount, type, body.getFilter());

      setPages(response, page, limit);

      InlineResponse200 inlineResponse200 = new InlineResponse200();
      inlineResponse200.setPrincipalsWithAccess(response);
      return Response.status(Response.Status.OK).entity(inlineResponse200).build();
    } catch (IllegalArgumentException ex) {
      throw new InvalidArgumentsException("Module type is incorrect");
    }
  }

  Boolean validateFilter(PrincipalWithAccessFilter filter) {
    if (filter.getPrincipalType() != null) {
      final String principalType = filter.getPrincipalType();
      if (!(principalType.equals(PRINCIPAL_USER) || principalType.equals(PRINCIPAL_SERVICE_ACCOUNT))) {
        return false;
      }
    }
    return true;
  }

  private void setPages(PrincipalWithAccessResponse response, int page, int limit) {
    Collections.sort(response.getUsersWithAccess(), (x, y) -> x.getName().compareTo(y.getName()));
    int offset = page * limit;
    int totalPages = (int) Math.ceil((double) response.getUsersWithAccess().size() / limit);
    if (totalPages == 0) {
      response.setTotalPages(0);
      response.setUsersWithAccess(new ArrayList<>());
      return;
    }
    if (page < 0 || page >= totalPages || limit < 0) {
      throw new InvalidArgumentsException("Invalid page number provided");
    }
    response.setTotalPages(totalPages);
    if (response.getUsersWithAccess().size() > limit) {
      int maxIndex = Math.min(response.getUsersWithAccess().size(), offset + limit);
      List<UserWithAccessEntity> paginatedList = response.getUsersWithAccess().subList(offset, maxIndex);
      response.setUsersWithAccess(paginatedList);
    }
  }
}

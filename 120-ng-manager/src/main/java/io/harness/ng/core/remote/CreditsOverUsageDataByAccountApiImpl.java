/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import io.harness.credit.entities.CreditOverUsageEntity;
import io.harness.credit.services.CreditService;
import io.harness.spec.server.ng.v1.CreditsOverUsageDataByAccountApi;
import io.harness.spec.server.ng.v1.model.CreditOverUsage;

import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class CreditsOverUsageDataByAccountApiImpl implements CreditsOverUsageDataByAccountApi {
  private CreditService creditService;
  @Override
  public Response creditsOverusage(String accountIdentifier) {
    CreditOverUsage creditOverUsage = new CreditOverUsage();
    CreditOverUsageEntity creditOverUsageEntity = creditService.getOverUsageByAccountId(accountIdentifier);
    creditOverUsage.setOverUsageCount(creditOverUsageEntity.getOverUsageCount());
    creditOverUsage.setModuleType(creditOverUsageEntity.getModuleType());
    return Response.status(Response.Status.OK).entity(creditOverUsage).build();
  }
}

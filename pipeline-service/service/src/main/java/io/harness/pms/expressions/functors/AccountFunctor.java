/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.EngineFunctorException;
import io.harness.expression.LateBindingValue;
import io.harness.network.SafeHttpCall;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.serializer.MapperUtils;

@OwnedBy(PIPELINE)
public class AccountFunctor implements LateBindingValue {
  private final AccountClient accountClient;
  private final Ambiance ambiance;

  public AccountFunctor(AccountClient accountClient, Ambiance ambiance) {
    this.accountClient = accountClient;
    this.ambiance = ambiance;
  }

  @Override
  public Object bind() {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (EmptyPredicate.isEmpty(accountId)) {
      return null;
    }

    try {
      if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
        return MapperUtils.toMapViaJsonString(
            SafeHttpCall.execute(accountClient.getAccountDTO(accountId)).getResource());
      } else {
        return SafeHttpCall.execute(accountClient.getAccountDTO(accountId)).getResource();
      }
    } catch (Exception ex) {
      throw new EngineFunctorException(String.format("Invalid account: %s", accountId), ex);
    }
  }
}

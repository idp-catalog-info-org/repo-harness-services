/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.EngineFunctorException;
import io.harness.expression.LateBindingValue;
import io.harness.network.SafeHttpCall;
import io.harness.notification.remote.SmtpConfigClient;
import io.harness.notification.remote.SmtpConfigResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.serializer.MapperUtils;

@OwnedBy(CDP)
public class SMTPFunctor implements LateBindingValue, RuntimeAbstractFunctor {
  private final SmtpConfigClient smtpConfigClient;
  private final Ambiance ambiance;
  private static final String SMTP_FUNCTOR_KEY = "smtp";

  public SMTPFunctor(SmtpConfigClient smtpConfigClient, Ambiance ambiance) {
    this.smtpConfigClient = smtpConfigClient;
    this.ambiance = ambiance;
  }

  @Override
  public Object bind() {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (EmptyPredicate.isEmpty(accountId)) {
      return null;
    }

    try {
      SmtpConfigResponse smtpConfigResponse =
          SafeHttpCall.execute(smtpConfigClient.getSmtpConfig(accountId)).getResource();
      if (smtpConfigResponse == null || smtpConfigResponse.getSmtpConfig() == null) {
        return null;
      }

      // Always return as Map to support .get() operations
      return MapperUtils.toMapViaJsonString(smtpConfigResponse.getSmtpConfig());
    } catch (Exception ex) {
      throw new EngineFunctorException(String.format("Invalid account: %s", accountId), ex);
    }
  }

  @Override
  public boolean supportsKey(String key) {
    return key.equals(SMTP_FUNCTOR_KEY);
  }
}

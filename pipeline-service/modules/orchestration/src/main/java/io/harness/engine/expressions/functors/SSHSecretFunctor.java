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
import io.harness.engine.expressions.secretUtils.SSHSecretAccessor;
import io.harness.exception.EngineFunctorException;
import io.harness.expression.LateBindingMap;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.secrets.remote.SecretNGManagerClient;

@OwnedBy(CDP)
public class SSHSecretFunctor extends LateBindingMap implements RuntimeAbstractFunctor {
  private final SSHSecretAccessor accessor;
  private final Ambiance ambiance;
  private static final String SSH_SECRET_FUNCTOR_KEY = "ssh";

  public SSHSecretFunctor(SecretNGManagerClient secretNGManagerClient, Ambiance ambiance) {
    this.accessor = new SSHSecretAccessor(ambiance, secretNGManagerClient);
    this.ambiance = ambiance;
  }

  @Override
  public Object get(Object key) {
    if (EmptyPredicate.isEmpty(AmbianceUtils.getAccountId(ambiance))) {
      return null;
    }
    try {
      return accessor.get(key);
    } catch (Exception ex) {
      throw new EngineFunctorException(
          String.format("Error retrieving SSH secret for account: %s", AmbianceUtils.getAccountId(ambiance)), ex);
    }
  }

  @Override
  public boolean supportsKey(String key) {
    return key.equals(SSH_SECRET_FUNCTOR_KEY);
  }
}
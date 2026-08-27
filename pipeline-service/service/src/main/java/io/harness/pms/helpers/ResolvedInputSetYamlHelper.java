/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.EngineExpressionSecretUtils;

import lombok.experimental.UtilityClass;

/**
 * Helpers for {@code resolvedUserInputSetYaml}. During expression resolve, secret expressions are
 * rewritten to deferred {@code ${ngSecretManager.obtain(...)}} functors for execution. Before that
 * snapshot is persisted for Inputs / re-run forms, revert it back to portable
 * {@code <+secrets.getValue(...)>} expressions.
 */
@OwnedBy(PIPELINE)
@UtilityClass
public class ResolvedInputSetYamlHelper {
  private static final String NG_SECRET_MANAGER_OBTAIN = "ngSecretManager.obtain";

  /**
   * Converts {@code ${ngSecretManager.obtain("id", token)}} back to
   * {@code <+secrets.getValue('id')>}. No-op when the yaml has no deferred secret functors.
   */
  public String revertSecretExpressions(String yaml) {
    if (isEmpty(yaml) || !yaml.contains(NG_SECRET_MANAGER_OBTAIN)) {
      return yaml;
    }
    return (String) EngineExpressionSecretUtils.revertSecrets(yaml);
  }
}

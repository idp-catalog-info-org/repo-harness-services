/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.STO;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.LateBindingMap;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.stoserviceclient.STOServiceUtils;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(STO)
@Slf4j
public class StoFunctor extends LateBindingMap {
  private static final List<String> STO_PLUGIN_AUDIENCE = List.of("sto-plugin");
  private static final String SERVICE_TOKEN_KEY = "serviceToken";
  private static final String TOKEN_KEY = "token";
  private static final String SERVICE_ENDPOINT_KEY = "serviceEndpoint";

  private final transient Ambiance ambiance;
  private final transient STOServiceUtils stoServiceUtils;

  public StoFunctor(Ambiance ambiance, STOServiceUtils stoServiceUtils) {
    this.ambiance = ambiance;
    this.stoServiceUtils = stoServiceUtils;
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String keyStr)) {
      return null;
    }
    switch (keyStr) {
      case SERVICE_TOKEN_KEY:
      case TOKEN_KEY:
        return fetchToken();
      case SERVICE_ENDPOINT_KEY:
        return stoServiceUtils != null ? stoServiceUtils.getStoServiceConfig().getBaseUrl() : null;
      default:
        return null;
    }
  }

  private String fetchToken() {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    try {
      if (stoServiceUtils == null) {
        log.error("StoFunctor: STOServiceUtils not injected; cannot mint STO service token for account={}", accountId);
        return null;
      }
      log.info("StoFunctor: minting STO service token for account={}", accountId);
      return stoServiceUtils.getSTOServiceToken(accountId, STO_PLUGIN_AUDIENCE);
    } catch (Exception e) {
      log.error("StoFunctor: failed to mint STO service token for account={}", accountId, e);
      return null;
    }
  }

  @Override
  public boolean containsKey(Object key) {
    return true;
  }
}

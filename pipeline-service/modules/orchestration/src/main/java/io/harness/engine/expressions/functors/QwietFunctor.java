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
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.qwietserviceclient.QwietServiceUtils;
import io.harness.qwietserviceclient.dto.QwietTokenData;
import io.harness.sto.beans.entities.QwietServiceConfig;

import com.google.inject.Inject;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(STO)
@Slf4j
public class QwietFunctor extends LateBindingMap implements RuntimeAbstractFunctor {
  // Marked optional because QwietServiceClientModule is only installed when qwietServiceConfig is
  // present in config.yml (see PipelineServiceModule). Without optional=true, injectMembers() in
  // RuntimeFunctorFactory would throw ProvisionException for every v1 pipeline execution whenever
  // the module is absent. The null checks in fetchToken surface a clean runtime.qwiet.error instead
  // of crashing the engine.
  @Inject(optional = true) private QwietServiceUtils qwietServiceUtils;
  @Inject(optional = true) private QwietServiceConfig qwietServiceConfig;

  private final Ambiance ambiance;
  private static final String QWIET_KEY = "qwiet";

  @Builder
  public QwietFunctor(Ambiance ambiance) {
    this.ambiance = ambiance;
  }

  @Override
  public boolean supportsKey(String key) {
    return QWIET_KEY.equals(key);
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String keyStr)) {
      return null;
    }

    switch (keyStr) {
      case "accessToken": {
        FetchResult result = fetchToken();
        return result.getTokenData() != null ? result.getTokenData().getValue() : null;
      }
      case "organizationId": {
        FetchResult result = fetchToken();
        return result.getTokenData() != null ? result.getTokenData().getOrganizationId() : null;
      }
      case "isActiveLicense":
        return String.valueOf(fetchToken().isActiveLicense());
      case "serviceEndpoint":
        return qwietServiceConfig != null ? qwietServiceConfig.getBaseUrl() : null;
      case "error":
        return fetchToken().getError();
      default:
        return null;
    }
  }

  private FetchResult fetchToken() {
    try {
      if (qwietServiceUtils == null) {
        return new FetchResult(
            null, "QwietServiceUtils not injected - QwietServiceClientModule may not be installed", false);
      }
      if (qwietServiceConfig == null) {
        return new FetchResult(null, "QwietServiceConfig not injected - check qwietServiceConfig in config.yml", false);
      }
      String accountId = AmbianceUtils.getAccountId(ambiance);
      log.info("QwietFunctor: fetching token for account={}, baseUrl={}", accountId, qwietServiceConfig.getBaseUrl());
      QwietTokenData tokenData = qwietServiceUtils.getQwietTokenAndOrgIdForAccount(accountId);
      log.info("QwietFunctor: successfully fetched token for account={}", accountId);
      return new FetchResult(tokenData, null, true);
    } catch (Exception e) {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String err = e.getClass().getSimpleName() + ": " + e.getMessage();
      log.error("QwietFunctor: failed to fetch token for account={}, error={}", accountId, err, e);
      return new FetchResult(null, err, false);
    }
  }

  @Override
  public boolean containsKey(Object key) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      return true;
    }
    return super.containsKey(key);
  }

  @Value
  private static class FetchResult {
    QwietTokenData tokenData;
    String error;
    boolean activeLicense;
  }
}

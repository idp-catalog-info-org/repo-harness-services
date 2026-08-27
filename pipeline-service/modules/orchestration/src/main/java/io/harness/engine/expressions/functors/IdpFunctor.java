/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.authorization.AuthorizationServiceHeader.IDP_SERVICE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.LateBindingMap;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.time.Duration;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(IDP)
@Slf4j
public class IdpFunctor extends LateBindingMap implements RuntimeAbstractFunctor {
  @Inject(optional = true) private ServiceTokenGenerator tokenGenerator;
  @Inject(optional = true) private Injector injector;

  private final Ambiance ambiance;

  private static final String IDP_KEY = "idp";
  private static final String SPACE = " ";
  private static final String BASE_URL = "baseUrl";
  private static final String AUTHORIZATION = "authorization";
  private static final String SOURCE_PRINCIPAL = "sourcePrincipal";
  private static final String IS_MANUAL_TRIGGER = "isManualTrigger";
  private static final String ERROR = "error";
  private static final String CONFIG_IDP_BASE_URL = "idpBaseUrl";
  private static final String CONFIG_IDP_SERVICE_SECRET = "idpServiceSecret";
  private static final String TRIGGER_TYPE_MANUAL = "MANUAL";
  private static final String EXTRA_INFO_EMAIL = "email";

  @Builder
  public IdpFunctor(Ambiance ambiance) {
    this.ambiance = ambiance;
  }

  private String getNamedStringOrNull(String name) {
    if (injector == null) {
      return null;
    }
    try {
      return injector.getInstance(Key.get(String.class, Names.named(name)));
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public boolean supportsKey(String key) {
    return IDP_KEY.equals(key);
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String keyStr)) {
      return null;
    }

    try {
      return resolveKey(keyStr);
    } catch (Exception e) {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      log.error("IdpFunctor: failed to resolve key={} for account={}", keyStr, accountId, e);
      if (ERROR.equals(keyStr)) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
      }
      return null;
    }
  }

  private Object resolveKey(String key) {
    switch (key) {
      case BASE_URL:
        return getBaseUrl();
      case AUTHORIZATION:
        return getAuthorization();
      case SOURCE_PRINCIPAL:
        return getSourcePrincipal();
      case IS_MANUAL_TRIGGER:
        return String.valueOf(isManuallyTriggered());
      case ERROR:
        return getError();
      default:
        return null;
    }
  }

  private String getBaseUrl() {
    return getNamedStringOrNull(CONFIG_IDP_BASE_URL);
  }

  private String getAuthorization() {
    if (!isManuallyTriggered()) {
      return null;
    }
    String secret = getNamedStringOrNull(CONFIG_IDP_SERVICE_SECRET);
    if (tokenGenerator == null || secret == null) {
      log.warn("IdpFunctor: cannot generate authorization token - tokenGenerator or idpServiceSecret not injected");
      return null;
    }
    String token = tokenGenerator.getServiceTokenWithDuration(
        secret, Duration.ofHours(1), new ServicePrincipal(IDP_SERVICE.getServiceId()));
    return IDP_SERVICE.getServiceId() + SPACE + token;
  }

  private String getSourcePrincipal() {
    if (!isManuallyTriggered()) {
      return null;
    }
    String secret = getNamedStringOrNull(CONFIG_IDP_SERVICE_SECRET);
    if (tokenGenerator == null || secret == null) {
      log.warn("IdpFunctor: cannot generate source principal token - tokenGenerator or idpServiceSecret not injected");
      return null;
    }
    String uuid = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getUuid();
    String email = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfo().get(EXTRA_INFO_EMAIL);
    String username = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier();
    String accountId = AmbianceUtils.getAccountId(ambiance);

    UserPrincipal userPrincipal = new UserPrincipal(uuid, email, username, accountId);
    String token = tokenGenerator.getServiceTokenWithDuration(secret, Duration.ofHours(1), userPrincipal);
    return IDP_SERVICE.getServiceId() + SPACE + token;
  }

  private boolean isManuallyTriggered() {
    return TRIGGER_TYPE_MANUAL.equals(ambiance.getMetadata().getTriggerInfo().getTriggerType().name());
  }

  private String getError() {
    if (tokenGenerator == null) {
      return "ServiceTokenGenerator not injected - IDP module may not be installed";
    }
    if (getNamedStringOrNull(CONFIG_IDP_SERVICE_SECRET) == null) {
      return "idpServiceSecret not injected - check IDP configuration in config.yml";
    }
    return null;
  }

  @Override
  public boolean containsKey(Object key) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      return true;
    }
    return super.containsKey(key);
  }
}

/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.REGISTRY_VANITY_URL_ENABLED;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_REGISTRY_IMAGE_URL;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_REGISTRY_VANITY_IMAGE_URL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.utils.HarnessRegistryConnectorUtils;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.expression.LateBindingMap;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.security.PmsSecurityContextNoSideEffectsGuard;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.SimpleEncryption;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
public class HarnessRegistryFunctor extends LateBindingMap implements RuntimeAbstractFunctor {
  private static final String REGISTRY_KEY = "har";
  private static final String TOKEN = "token";
  private static final String URL = "url";
  private static final String USERNAME = "username";
  private final Ambiance ambiance;

  @Inject @Named("harnessRegistryClientConfig") private ServiceHttpClientConfig harnessRegistryClientConfig;

  @Inject @Named("harnessRegistryServiceSecret") private String harnessRegistryServiceSecret;
  @Inject private PmsFeatureFlagService featureFlagService;
  @Inject(optional = true) private AccountClient accountClient;

  @Builder
  public HarnessRegistryFunctor(Ambiance ambiance) {
    this.ambiance = ambiance;
  }

  private static final Map<String, Function<HarnessRegistryFunctor, Object>> KEY_HANDLERS =
      ImmutableMap.<String, Function<HarnessRegistryFunctor, Object>>builder()
          .put(USERNAME, HarnessRegistryFunctor::handleUsername)
          .put(TOKEN, HarnessRegistryFunctor::handleToken)
          .put(URL, HarnessRegistryFunctor::handleRegistryURL)
          .build();

  @Override
  public boolean supportsKey(String key) {
    return REGISTRY_KEY.equals(key);
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String functorKey)) {
      return null;
    }

    Function<HarnessRegistryFunctor, Object> handler = KEY_HANDLERS.get(functorKey);
    if (handler != null) {
      return handler.apply(this);
    }

    log.warn("Unknown key '{}' requested from HarnessRegistryFunctor", functorKey);
    return null;
  }

  /**
   * Returns the JWT token for Harness Registry authentication (masked).
   * Usage: <+runtime.har.token>
   */
  private String handleToken() {
    String harnessRegistrySecret = harnessRegistryServiceSecret;
    String registryUrl = handleRegistryURL();

    if (EmptyPredicate.isEmpty(harnessRegistrySecret) || EmptyPredicate.isEmpty(registryUrl)) {
      log.warn("Harness Registry secret or URL is empty");
      return null;
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);

    // Doing this because we need principal context
    try (PmsSecurityContextNoSideEffectsGuard securityContextGuard =
             new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
      String jwtToken = HarnessRegistryConnectorUtils.getHarnessRegistryTokenWithClaims(
          harnessRegistrySecret, registryUrl, HarnessRegistryConnectorUtils.JWT_TOKEN_EXPIRY_IN_HOURS, accountId);
      SimpleEncryption encryption = new SimpleEncryption();
      String encodedValue = EncodingUtils.encodeBase64(encryption.encrypt(jwtToken.getBytes(StandardCharsets.UTF_8)));

      // Return sweepingOutputSecrets expression - will be decrypted and masked by runner
      return "${sweepingOutputSecrets.obtain(\"harnessRegistryToken\",\"" + encodedValue + "\")}";

    } catch (Exception e) {
      log.error("Failed to generate Harness Registry JWT token", e);
      return null;
    }
  }

  /**
   * Returns the username Harness Registry authentication.
   * Usage: <+har.username>
   */
  private String handleUsername() {
    try {
      String username = HarnessRegistryConnectorUtils.DOCKER_USERNAME_INTERNAL;
      SimpleEncryption encryption = new SimpleEncryption();
      String encodedValue = EncodingUtils.encodeBase64(encryption.encrypt(username.getBytes(StandardCharsets.UTF_8)));

      // Return sweepingOutputSecrets expression - will be decrypted and masked by runner
      return "${sweepingOutputSecrets.obtain(\"harnessRegistryUsername\",\"" + encodedValue + "\")}";

    } catch (Exception e) {
      log.error("Failed to encode Harness Registry username", e);
      return null;
    }
  }

  /**
   * Returns the Harness Registry base URL.
   * Usage: <+runtime.har.url>
   */
  private String handleRegistryURL() {
    if (isNull(harnessRegistryClientConfig)) {
      log.warn("harnessRegistryClientConfig is not available");
      return null;
    }
    String vanityUrl = getVanityURL();
    String registryUrl = isNotEmpty(vanityUrl) ? vanityUrl : harnessRegistryClientConfig.getBaseUrl();
    if (registryUrl != null && registryUrl.endsWith("/")) {
      registryUrl = registryUrl.substring(0, registryUrl.length() - 1);
    }
    return registryUrl;
  }

  /**
   * Builds the full repository path for Harness Registry.
   * Usage: <+runtime.har.getRepo("account.myRegistry/my-image")>
   *
   * @param object in string instance which is combined string in format "registryRef/repo"
   *                           e.g., "account.myRegistry/my-image" or "myRegistry/path/to/image"
   * @return Full repository path in format: host/accountId/registryName/repo
   */
  public String getRepo(Object object) {
    if (object == null) {
      return null;
    }
    if (object instanceof String registryRefAndRepo) {
      if (EmptyPredicate.isEmpty(registryRefAndRepo)) {
        log.warn("harness registry and repo is empty");
        return null;
      }

      // Split by first "/" to separate registryRef from repo
      int firstSlashIndex = registryRefAndRepo.indexOf('/');
      if (firstSlashIndex == -1 || firstSlashIndex == 0 || firstSlashIndex == registryRefAndRepo.length() - 1) {
        log.warn("Invalid format for registry and repo: {}. Expected format: registryRef/repo", registryRefAndRepo);
        return registryRefAndRepo;
      }

      String registryRef = registryRefAndRepo.substring(0, firstSlashIndex);
      String repo = registryRefAndRepo.substring(firstSlashIndex + 1);

      if (EmptyPredicate.isEmpty(registryRef) || EmptyPredicate.isEmpty(repo)) {
        log.warn("registryRef or repo is empty after parsing: {}", registryRefAndRepo);
        return registryRefAndRepo;
      }

      String registryBaseUrl = handleRegistryURL();
      if (EmptyPredicate.isEmpty(registryBaseUrl)) {
        log.warn("Registry base URL is not configured");
        return repo;
      }
      URL url;
      try {
        url = new URL(registryBaseUrl);
      } catch (Exception e) {
        throw new InvalidArgumentsException(format("Malformed registryUrl %s for harness registry", registryBaseUrl));
      }

      String accountId = AmbianceUtils.getAccountId(ambiance);
      String registryName = HarnessRegistryConnectorUtils.getRegistryNameFromRef(registryRef);
      String vanityURL = getVanityURL();
      return isNotEmpty(vanityURL)
          ? String.format(HARNESS_REGISTRY_VANITY_IMAGE_URL, url.getHost(), registryName, repo)
          : String.format(HARNESS_REGISTRY_IMAGE_URL, url.getHost(), accountId.toLowerCase(), registryName, repo);
    } else {
      return null;
    }
  }

  private String getVanityURL() {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    boolean isVanityUrlEnabled = featureFlagService.isEnabled(accountId, REGISTRY_VANITY_URL_ENABLED);
    String vanityUrl = null;
    if (!isVanityUrlEnabled) {
      return vanityUrl;
    }
    if (!isNull(accountClient)) {
      try {
        AccountDTO accountDTO = CGRestUtils.getResponse(accountClient.getAccountDTO(accountId));
        vanityUrl = isNull(accountDTO) ? "" : accountDTO.getSubdomainURL();
      } catch (Exception e) {
        log.error("Unable to fetch the account vanity URL for account id: {}", accountId, e);
        return null;
      }
    }
    return vanityUrl;
  }

  @Override
  public boolean containsKey(Object key) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      return true;
    }
    return super.containsKey(key);
  }
}

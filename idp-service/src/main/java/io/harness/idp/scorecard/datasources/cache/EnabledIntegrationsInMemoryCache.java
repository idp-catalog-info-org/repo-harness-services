/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.cache;

import static io.harness.idp.common.Constants.DATADOG_IDENTIFIER;
import static io.harness.idp.common.Constants.DYNATRACE_IDENTIFIER;
import static io.harness.idp.common.Constants.GCP_IDENTIFIER;
import static io.harness.idp.common.Constants.HARNESS_CD_IDENTIFIER;
import static io.harness.idp.common.Constants.SONAR_IDENTIFIER;
import static io.harness.idp.common.Constants.TRACEABLE_IDENTIFIER;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.integrationmanager.IntegrationManagerClientHelper;
import io.harness.clients.integrationmanager.TypesIntegrationConfig;
import io.harness.clients.integrationmanager.TypesIntegrationConfig.EnumIntegrationType;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * In-process cache of enabled Integration Manager configs for the new scorecard datasources.
 * Avoids calling IM on every datasource API. Failed loads are not cached, allowing subsequent
 * requests to retry IM.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class EnabledIntegrationsInMemoryCache {
  private static final long MAX_CACHE_SIZE = 1000;
  private static final long EXPIRE_AFTER_WRITE_MINUTES = 10;

  /**
   * Newer IM-backed datasources gated by integration-config enablement.
   * Legacy datasources (github, bitbucket, pagerduty, kubernetes, etc.) are not listed and always pass.
   */
  public static final Map<String, EnumIntegrationType> DATA_SOURCE_INTEGRATION_TYPES = Map.of(SONAR_IDENTIFIER,
      EnumIntegrationType.SonarQube, DATADOG_IDENTIFIER, EnumIntegrationType.DataDog, DYNATRACE_IDENTIFIER,
      EnumIntegrationType.DynaTrace, GCP_IDENTIFIER, EnumIntegrationType.GCP, HARNESS_CD_IDENTIFIER,
      EnumIntegrationType.HarnessCD, TRACEABLE_IDENTIFIER, EnumIntegrationType.HarnessTraceable);

  /** Derived from {@link #DATA_SOURCE_INTEGRATION_TYPES}; used when querying IM. */
  public static final List<EnumIntegrationType> INTEGRATION_TYPES_TO_CHECK =
      List.copyOf(DATA_SOURCE_INTEGRATION_TYPES.values());

  private final LoadingCache<String, Set<EnumIntegrationType>> cache;

  @Inject
  public EnabledIntegrationsInMemoryCache(IntegrationManagerClientHelper integrationManagerClientHelper) {
    this.cache = CacheBuilder.newBuilder()
                     .maximumSize(MAX_CACHE_SIZE)
                     .expireAfterWrite(EXPIRE_AFTER_WRITE_MINUTES, TimeUnit.MINUTES)
                     .build(new CacheLoader<>() {
                       @NotNull
                       @Override
                       public Set<EnumIntegrationType> load(@NotNull String accountIdentifier) {
                         List<TypesIntegrationConfig> integrationConfigs =
                             getGeneralResponse(integrationManagerClientHelper.listIntegrationConfigs(
                                 accountIdentifier, accountIdentifier, INTEGRATION_TYPES_TO_CHECK, true, true));
                         return integrationConfigs.stream()
                             .filter(TypesIntegrationConfig::isEnabled)
                             .map(TypesIntegrationConfig::getIntegrationType)
                             .collect(Collectors.toSet());
                       }
                     });
  }

  /**
   * Returns enabled integration types for the account from cache (or IM on miss).
   * Empty Optional means IM failed — callers should fail open and not filter. Failed loads are not cached.
   */
  public Optional<Set<EnumIntegrationType>> getEnabledIntegrationTypes(String accountIdentifier) {
    try {
      return Optional.of(cache.get(accountIdentifier));
    } catch (ExecutionException | RuntimeException ex) {
      log.warn("Unable to read enabled integration configs cache for account {}. Datasources will not be filtered. "
              + "Error: {}",
          accountIdentifier, ex.getMessage(), ex);
      return Optional.empty();
    }
  }
}

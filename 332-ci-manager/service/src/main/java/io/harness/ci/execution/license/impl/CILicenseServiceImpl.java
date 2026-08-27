/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.license.impl;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.springdata.PersistenceUtils.getRetryPolicy;

import static java.lang.String.format;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.ci.license.AITCILicenseBypassEvaluator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.summary.dto.CILicenseSummaryDTO;
import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import retrofit2.Response;

@OwnedBy(CI)
@Slf4j
@Singleton
public class CILicenseServiceImpl implements CILicenseService {
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 6;
  @Inject NgLicenseHttpClient ngLicenseHttpClient;
  @Inject AITCILicenseBypassEvaluator aitBypassEvaluator;

  private static final int CACHE_EVICTION_TIME_MINUTES = 60;

  private final LoadingCache<LicenseCacheKey, LicensesWithSummaryDTO> licenseCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(CACHE_EVICTION_TIME_MINUTES, TimeUnit.MINUTES)
          .build(new CacheLoader<LicenseCacheKey, LicensesWithSummaryDTO>() {
            @Override
            public LicensesWithSummaryDTO load(@NotNull final LicenseCacheKey key) {
              return fetchLicenseSummary(key.accountId(), key.moduleType());
            }
          });

  @Override
  public LicensesWithSummaryDTO getLicenseSummary(
      @NotNull String accountId, @NotNull String moduleType, ExecutionPrincipalInfo principalInfo) {
    LicensesWithSummaryDTO license;
    try {
      license = licenseCache.get(new LicenseCacheKey(accountId, moduleType.toUpperCase()));
    } catch (Exception e) {
      log.error(
          "Error getting license summary for account {} and module {}: {}", accountId, moduleType, e.getMessage());
      license = null;
    }
    if (aitBypassEvaluator.shouldBypass(accountId, principalInfo)
        && (license == null || license.getEdition() == Edition.FREE)) {
      log.info("AIT license bypass applied for account {}. Original edition: {}", accountId,
          license != null ? license.getEdition() : "null");
      return CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build();
    }
    return license;
  }

  @Override
  public Boolean hasActiveModuleLicense(String accountId, String moduleType) {
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        format("[Retrying failed call to fetch license summary: accountId=%s, moduleType=%s]", accountId, moduleType),
        format(
            "Failed to fetch license summary for accountId=%s, moduleType=%s after retrying", accountId, moduleType));
    Response<ResponseDTO<AccountLicenseDTO>> response =
        Failsafe.with(retryPolicy).get(() -> ngLicenseHttpClient.getAccountLicensesDTO(accountId).execute());
    ModuleType requiredModuleType = ModuleType.fromString(moduleType);

    AccountLicenseDTO accountLicenseDTO;
    if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
      accountLicenseDTO = response.body().getData();
      Map<ModuleType, List<ModuleLicenseDTO>> allModuleLicenses = accountLicenseDTO.getAllModuleLicenses();
      if (EmptyPredicate.isNotEmpty(allModuleLicenses)
          && EmptyPredicate.isNotEmpty(allModuleLicenses.get(requiredModuleType))) {
        Optional<ModuleLicenseDTO> findResult = allModuleLicenses.get(requiredModuleType)
                                                    .stream()
                                                    .filter(license -> license.getStatus() == LicenseStatus.ACTIVE)
                                                    .findFirst();

        return findResult.isPresent();
      }
    } else {
      log.warn("Error getting license summary for account {} and module {}: {}", accountId, moduleType,
          response.errorBody());
    }

    return false;
  }

  private LicensesWithSummaryDTO fetchLicenseSummary(String accountId, String moduleType) {
    try {
      RetryPolicy<Object> retryPolicy = getRetryPolicy(
          format("[Retrying failed call to fetch license summary: accountId=%s, moduleType=%s]", accountId, moduleType),
          format(
              "Failed to fetch license summary for accountId=%s, moduleType=%s after retrying", accountId, moduleType));

      Response<ResponseDTO<LicensesWithSummaryDTO>> response = Failsafe.with(retryPolicy).get(() -> {
        return ngLicenseHttpClient.getLicenseSummary(accountId, moduleType).execute();
      });

      if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
        return response.body().getData();
      } else {
        log.warn("Error getting license summary for account {} and module {}: {}", accountId, moduleType,
            response.errorBody());
      }
    } catch (Exception e) {
      log.warn("Exception while fetching license summary for account {} and module {}: {}", accountId, moduleType,
          e.getMessage());
    }

    return null;
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .abortOn(ConnectorNotFoundException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}

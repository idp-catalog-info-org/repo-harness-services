/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import io.harness.account.services.AccountClient;
import io.harness.account.settings.service.impl.PipelineSettingsServiceImpl;
import io.harness.data.structure.EmptyPredicate;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.pipeline.PipelineEntityUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.sdk.helper.PmsSdkHelper;
import io.harness.remote.client.CGRestUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PipelineExpressionHelper {
  private static final String EXECUTION_URL_FORMAT =
      "%s/account/%s/%s/orgs/%s/projects/%s/pipelines/%s/executions/%s/pipeline";
  private static final int VANITY_URL_CACHE_EVICTION_TIME_MINUTES = 60;

  @Inject PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject PipelineServiceConfiguration pipelineServiceConfiguration;
  @Inject private AccountClient accountClient;
  @Inject private PmsSdkHelper pmsSdkHelper;
  @Inject private PipelineEntityUtils pipelineEntityUtils;
  @Inject PipelineSettingsServiceImpl pipelineSettingsService;

  // Cache for vanity URLs by account identifier
  private final LoadingCache<String, String> vanityUrlCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(VANITY_URL_CACHE_EVICTION_TIME_MINUTES, TimeUnit.MINUTES)
          .build(new CacheLoader<String, String>() {
            @Override
            public String load(String accountIdentifier) {
              return getVanityUrlFromService(accountIdentifier);
            }
          });

  public String generatePipelineUrl(Ambiance ambiance, PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String moduleName = getModuleName(ambiance, pipelineExecutionSummaryEntity);
    String baseUrl = getBaseUrl(pipelineServiceConfiguration.getPipelineServiceBaseUrl(), getVanityUrl(accountId));
    return String.format("%s/account/%s/%s/orgs/%s/projects/%s/pipelines/%s/pipeline-studio", baseUrl, accountId,
        moduleName, orgId, projectId, ambiance.getMetadata().getPipelineIdentifier());
  }

  @VisibleForTesting
  String getModuleName(Ambiance ambiance) {
    String moduleName = "cd";
    if (!EmptyPredicate.isEmpty(ambiance.getMetadata().getModuleType())) {
      moduleName = ambiance.getMetadata().getModuleType();
    } else {
      String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
          pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(accountIdentifier,
              ambiance.getPlanExecutionId(),
              Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.modules));
      if (pipelineExecutionSummaryEntity != null) {
        moduleName = getModuleName(pipelineExecutionSummaryEntity, moduleName, accountIdentifier);
      }
    }
    return moduleName;
  }

  @VisibleForTesting
  String getModuleName(Ambiance ambiance, PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    String moduleName = "cd";
    if (!EmptyPredicate.isEmpty(ambiance.getMetadata().getModuleType())) {
      moduleName = ambiance.getMetadata().getModuleType();
    } else {
      String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
      if (pipelineExecutionSummaryEntity != null) {
        moduleName = getModuleName(pipelineExecutionSummaryEntity, moduleName, accountIdentifier);
      }
    }
    return moduleName;
  }

  public String generateUrl(Ambiance ambiance, PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String planExecutionId = ambiance.getPlanExecutionId();
    // In case of PipelineRollback, we do not want to expose the new rollback execution to users. So original execution
    // url should be returned.
    if (ambiance.getMetadata().getExecutionMode() == ExecutionMode.PIPELINE_ROLLBACK) {
      planExecutionId = ambiance.getMetadata().getOriginalPlanExecutionIdForRollbackMode();
    }
    String moduleName;
    if (pipelineExecutionSummaryEntity == null) {
      moduleName = getModuleName(ambiance);
    } else {
      moduleName = getModuleName(ambiance, pipelineExecutionSummaryEntity);
    }
    String modulePath = pmsSdkHelper.getModulePath(moduleName);
    if (EmptyPredicate.isEmpty(modulePath)) {
      // PIPE-18322: It is needed for IDP use case where instead of idp we need to append idp-admin.
      modulePath = moduleName;
    }
    String vanityUrl = getVanityUrl(accountId);
    String baseUrl = getBaseUrl(pipelineServiceConfiguration.getPipelineServiceBaseUrl(), vanityUrl);
    return String.format(EXECUTION_URL_FORMAT, baseUrl, accountId, modulePath, orgId, projectId,
        ambiance.getMetadata().getPipelineIdentifier(), planExecutionId);
  }

  public String generateUrl(String accountId, String orgId, String projectId, String pipelineId, String planExecutionId,
      List<String> modules) {
    String vanityUrl = getVanityUrl(accountId);
    String baseUrl = getBaseUrl(pipelineServiceConfiguration.getPipelineServiceBaseUrl(), vanityUrl);
    String moduleName = pipelineEntityUtils.getModuleNameFromPipelineEntity(modules, accountId);
    String modulePath = pmsSdkHelper.getModulePath(moduleName);
    if (EmptyPredicate.isEmpty(modulePath)) {
      // PIPE-18322: For IDP stage use case where instead of idp we need to append idp-admin.
      modulePath = moduleName;
    }
    return String.format(EXECUTION_URL_FORMAT, baseUrl, accountId,
        EmptyPredicate.isEmpty(modulePath) ? "cd" : modulePath, orgId, projectId, pipelineId, planExecutionId);
  }

  /**
   * Computes base URL with vanity URL support for a given account.
   * This is a public method that wraps the internal logic for computing base URLs.
   *
   * @param accountId Account identifier
   * @return Base URL with vanity URL support, or default base URL if vanity URL is not configured
   */
  public String getBaseUrlWithVanitySupport(String accountId) {
    String vanityUrl = getVanityUrl(accountId);
    return getBaseUrl(pipelineServiceConfiguration.getPipelineServiceBaseUrl(), vanityUrl);
  }

  String getModuleName(
      PipelineExecutionSummaryEntity executionSummaryEntity, String defaultValue, String accountIdentifier) {
    List<ModuleLicenseDTO> moduleLicenseDTOS = List.of();
    try {
      moduleLicenseDTOS = pipelineSettingsService.getModuleLicense(accountIdentifier);
    } catch (Exception e) {
      log.warn("exception in getting module licenses", e);
    }
    List<String> modules = executionSummaryEntity.getModules();

    // We are filtering only if the moduleLicenseDTOs list is not empty, it means the call to API would have succeeded,
    // else we continue with old logic
    if (!moduleLicenseDTOS.isEmpty()) {
      Set<String> activeModules = getActiveLicenseModules(moduleLicenseDTOS);
      modules.removeIf(module -> !activeModules.contains(module));
    }

    String moduleName = pipelineEntityUtils.getModuleNameFromPipelineEntity(modules, accountIdentifier);
    return EmptyPredicate.isEmpty(moduleName) ? defaultValue : moduleName;
  }

  @VisibleForTesting
  static String getBaseUrl(String defaultBaseUrl, String vanityUrl) {
    // e.g Prod Default Base URL - 'https://app.harness.io/ng/#'
    if (EmptyPredicate.isEmpty(vanityUrl)) {
      return defaultBaseUrl;
    }
    String newBaseUrl = vanityUrl;
    if (vanityUrl.endsWith("/")) {
      newBaseUrl = vanityUrl.substring(0, vanityUrl.length() - 1);
    }
    try {
      URL url = new URL(defaultBaseUrl);
      String hostUrl = String.format("%s://%s", url.getProtocol(), url.getHost());
      return newBaseUrl + defaultBaseUrl.substring(hostUrl.length());
    } catch (Exception e) {
      log.warn("There was error while generating vanity URL", e);
      return defaultBaseUrl;
    }
  }

  @VisibleForTesting
  String getVanityUrl(String accountIdentifier) {
    try {
      return vanityUrlCache.get(accountIdentifier);
    } catch (Exception e) {
      log.error("Error getting vanity URL from cache for account: " + accountIdentifier, e);
      // Fallback to direct service call if cache fails
      return getVanityUrlFromService(accountIdentifier);
    }
  }

  /**
   * Fetches vanity URL directly from the service (used by cache loader)
   * @param accountIdentifier the account identifier
   * @return the vanity URL for the account
   */
  private String getVanityUrlFromService(String accountIdentifier) {
    try {
      String vanityUrl = CGRestUtils.getResponse(accountClient.getVanityUrl(accountIdentifier));
      // Return empty string instead of null to avoid CacheLoader$InvalidCacheLoadException
      return vanityUrl != null ? vanityUrl : "";
    } catch (Exception e) {
      log.error("Error fetching vanity URL from service for account: " + accountIdentifier, e);
      // Return empty string instead of null to avoid cache exceptions
      return "";
    }
  }

  @VisibleForTesting
  Set<String> getActiveLicenseModules(List<ModuleLicenseDTO> moduleLicenseDTOS) {
    return moduleLicenseDTOS.stream()
        .filter(license -> LicenseStatus.ACTIVE.equals(license.getStatus()))
        .map(license -> license.getModuleType().name().toLowerCase())
        .collect(Collectors.toSet());
  }
}

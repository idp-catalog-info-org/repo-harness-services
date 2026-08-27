/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.ACTIVE_TRIGGERS_GAUGE;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.MAX_ORPHAN_DETAIL_LOGS;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.ORPHAN_SCAN_CACHE_GATE_KEY;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.ORPHAN_TRIGGERS_COUNT_METRIC;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.REASON_PARENT_UNIQUE_ID_MISMATCH;
import static io.harness.pms.events.PmsMonitoringMetricsConstants.REASON_PROJECT_MISSING;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.metrics.service.api.MetricService;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.instrumentation.OrphanScanGroupResult;
import io.harness.ngtriggers.instrumentation.OrphanScanGroupResult.OrphanScanGroupId;
import io.harness.ngtriggers.instrumentation.TriggerCountWithAccountAndTriggerTypeResult;
import io.harness.ngtriggers.service.NGTriggerMonitorService;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.repositories.custom.NGTriggerRepositoryCustom;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class NGTriggerMonitorServiceImpl implements NGTriggerMonitorService {
  private final NGTriggerRepositoryCustom ngTriggerRepositoryCustom;
  private final MetricService metricService;
  private final Cache<String, Integer> metricsCache;
  private final ScopeResolutionHelper scopeResolutionHelper;

  protected static final Set<NGTriggerType> triggerTypes = Set.of(NGTriggerType.MANIFEST, NGTriggerType.ARTIFACT,
      NGTriggerType.SCHEDULED, NGTriggerType.WEBHOOK, NGTriggerType.MULTI_REGION_ARTIFACT);

  @Inject
  public NGTriggerMonitorServiceImpl(NGTriggerRepositoryCustom ngTriggerRepositoryCustom, MetricService metricService,
      @Named("triggersMetricsCache") Cache<String, Integer> metricsCache, ScopeResolutionHelper scopeResolutionHelper) {
    this.ngTriggerRepositoryCustom = ngTriggerRepositoryCustom;
    this.metricService = metricService;
    this.metricsCache = metricsCache;
    this.scopeResolutionHelper = scopeResolutionHelper;
  }

  @Override
  public void registerActiveExecutionsMetrics() {
    boolean alreadyMetricPublished = !metricsCache.putIfAbsent(ACTIVE_TRIGGERS_GAUGE, 1);
    if (!alreadyMetricPublished) {
      populateCountPerAccountIdAndTriggerType();
    }
  }

  @Override
  public void registerOrphanTriggerMetrics() {
    boolean alreadyMetricPublished = !metricsCache.putIfAbsent(ORPHAN_SCAN_CACHE_GATE_KEY, 1);
    if (alreadyMetricPublished) {
      return;
    }
    try {
      scanAndPublishOrphanMetrics();
    } catch (Exception ex) {
      log.error("[TRIGGER_ORPHAN_SCAN_COMPLETE] Failed while publishing orphan trigger metrics", ex);
    }
  }

  private void populateCountPerAccountIdAndTriggerType() {
    var accountResultList = ngTriggerRepositoryCustom.aggregateActiveTriggersCountPerAccountByTriggerType();
    Set<NGTriggerType> foundTriggerTypes = new HashSet<>();
    Map<String, String> metricContextMap = new HashMap<>();
    Set<NGTriggerType> notFoundTriggerTypes;

    for (var accountResult : accountResultList) {
      foundTriggerTypes.clear();
      metricContextMap.put(PmsEventMonitoringConstants.ACCOUNT_ID, accountResult.accountId());
      for (var triggerTypeCount : accountResult.triggerTypeCounts()) {
        foundTriggerTypes.add(NGTriggerType.valueOf(triggerTypeCount.type()));
        metricContextMap.put(PmsEventMonitoringConstants.TRIGGER_TYPE, triggerTypeCount.type());
        populateMetric(triggerTypeCount.count(), metricContextMap, ACTIVE_TRIGGERS_GAUGE);
      }
      notFoundTriggerTypes = Sets.difference(triggerTypes, foundTriggerTypes);
      notFoundTriggerTypes.forEach(notFoundTriggerType -> {
        metricContextMap.put(PmsEventMonitoringConstants.TRIGGER_TYPE, notFoundTriggerType.toString());
        populateMetric(0, metricContextMap, ACTIVE_TRIGGERS_GAUGE);
      });
    }
    populateCountForMissingKeysInCurrentStats(accountResultList);
  }

  private void populateCountForMissingKeysInCurrentStats(
      List<TriggerCountWithAccountAndTriggerTypeResult> accountResultList) {
    Set<String> currentAccountIds = new HashSet<>();
    for (TriggerCountWithAccountAndTriggerTypeResult accountResult : accountResultList) {
      currentAccountIds.add(accountResult.accountId());
    }
    populateZeroCount(currentAccountIds);
  }

  private void populateZeroCount(Set<String> currentKeys) {
    try {
      Set<String> cachedKeys = new HashSet<>(ngTriggerRepositoryCustom.findAllAccountIdsWithTriggers());
      Set<String> zeroCountKeys = Sets.difference(cachedKeys, currentKeys);

      // if no triggers are found for an account that was already cached, we need to mark the count for all status as
      // 0 to keep a consistent state
      Map<String, String> metricContextMap = new HashMap<>();
      for (String key : zeroCountKeys) {
        metricContextMap.put(PmsEventMonitoringConstants.ACCOUNT_ID, key);
        for (NGTriggerType triggerType : triggerTypes) {
          metricContextMap.put(PmsEventMonitoringConstants.TRIGGER_TYPE, triggerType.toString());
          populateMetric(0, metricContextMap, ACTIVE_TRIGGERS_GAUGE);
        }
      }
    } catch (Exception e) {
      log.error("Unable to populate zero count for metric {}", ACTIVE_TRIGGERS_GAUGE);
    }
  }

  void scanAndPublishOrphanMetrics() {
    long now = System.currentTimeMillis();
    Map<String, Integer> orphanCountByAccount = new HashMap<>();
    int orphanDetailLogsEmitted = 0;
    int totalOrphans = 0;
    Set<String> accountsScanned = new HashSet<>();

    List<OrphanScanGroupResult> groups = ngTriggerRepositoryCustom.aggregateForOrphanScan();

    Map<String, Set<String>> projectsByAccountOrg = new HashMap<>();
    for (OrphanScanGroupResult group : groups) {
      OrphanScanGroupId id = group.id();
      if (id == null || isEmpty(id.accountId()) || isEmpty(id.projectIdentifier())) {
        continue;
      }
      accountsScanned.add(id.accountId());
      String accountOrgKey = accountOrgKey(id.accountId(), id.orgIdentifier());
      projectsByAccountOrg.computeIfAbsent(accountOrgKey, k -> new HashSet<>()).add(id.projectIdentifier());
    }

    Map<String, Optional<Map<String, String>>> liveUniqueIdsByAccountOrg = new HashMap<>();
    for (Map.Entry<String, Set<String>> entry : projectsByAccountOrg.entrySet()) {
      String[] accountOrg = splitAccountOrgKey(entry.getKey());
      liveUniqueIdsByAccountOrg.put(
          entry.getKey(), resolveLiveProjectUniqueIds(accountOrg[0], accountOrg[1], entry.getValue()));
    }

    for (OrphanScanGroupResult group : groups) {
      OrphanScanGroupId id = group.id();
      if (id == null || isEmpty(id.accountId()) || isEmpty(id.projectIdentifier())) {
        continue;
      }
      String accountOrgKey = accountOrgKey(id.accountId(), id.orgIdentifier());
      Optional<Map<String, String>> liveUniqueIdByProjectOpt = liveUniqueIdsByAccountOrg.get(accountOrgKey);
      if (liveUniqueIdByProjectOpt == null || liveUniqueIdByProjectOpt.isEmpty()) {
        continue;
      }
      String liveUniqueId = liveUniqueIdByProjectOpt.get().get(id.projectIdentifier());
      String reason = classifyOrphan(id.parentUniqueId(), liveUniqueId);
      if (reason == null) {
        continue;
      }
      int count = (int) group.count();
      totalOrphans += count;
      orphanCountByAccount.merge(id.accountId(), count, Integer::sum);
      if (orphanDetailLogsEmitted < MAX_ORPHAN_DETAIL_LOGS) {
        orphanDetailLogsEmitted++;
        long ageMs = group.sampleCreatedAt() == null ? -1L : now - group.sampleCreatedAt();
        log.warn("[TRIGGER_ORPHAN_DETECTED] accountId={} orgIdentifier={} projectIdentifier={} pipelineIdentifier={} "
                + "triggerIdentifier={} reason={} triggerParentUniqueId={} liveProjectUniqueId={} ageMs={} "
                + "deleted={} count={}",
            id.accountId(), id.orgIdentifier(), id.projectIdentifier(), group.sampleTargetIdentifier(),
            group.sampleIdentifier(), reason, id.parentUniqueId(), liveUniqueId, ageMs, group.sampleDeleted(), count);
      }
    }

    for (Map.Entry<String, Integer> entry : orphanCountByAccount.entrySet()) {
      populateOrphanMetric(entry.getKey(), entry.getValue());
    }

    Set<String> zeroFillAccounts = Sets.difference(accountsScanned, orphanCountByAccount.keySet());
    for (String accountId : zeroFillAccounts) {
      populateOrphanMetric(accountId, 0);
    }

    log.info("[TRIGGER_ORPHAN_SCAN_COMPLETE] accountsScanned={} orphanCount={}", accountsScanned.size(), totalOrphans);
  }

  static String classifyOrphan(String triggerParentUniqueId, String liveProjectUniqueId) {
    if (isEmpty(triggerParentUniqueId)) {
      // Older triggers created before parentUniqueId backfill; nothing to compare against, so don't flag them.
      return null;
    }
    if (liveProjectUniqueId == null) {
      return REASON_PROJECT_MISSING;
    }
    if (!Objects.equals(triggerParentUniqueId, liveProjectUniqueId)) {
      return REASON_PARENT_UNIQUE_ID_MISMATCH;
    }
    return null;
  }

  private Optional<Map<String, String>> resolveLiveProjectUniqueIds(
      String accountId, String orgId, Set<String> projectIdentifiers) {
    try {
      List<ScopeInfo> scopeInfos =
          scopeResolutionHelper.getScopeInfoListForProjects(accountId, orgId, projectIdentifiers);
      Map<String, String> liveUniqueIdByProject = new HashMap<>();
      if (scopeInfos != null) {
        for (ScopeInfo scopeInfo : scopeInfos) {
          if (scopeInfo != null && !isEmpty(scopeInfo.getProjectIdentifier()) && !isEmpty(scopeInfo.getUniqueId())) {
            liveUniqueIdByProject.put(scopeInfo.getProjectIdentifier(), scopeInfo.getUniqueId());
          }
        }
      }
      if (liveUniqueIdByProject.isEmpty()) {
        log.warn("[TRIGGER_ORPHAN_DETECTED] No live project uniqueIds resolved for accountId={} orgIdentifier={}, "
                + "skipping group",
            accountId, orgId);
        return Optional.empty();
      }
      return Optional.of(liveUniqueIdByProject);
    } catch (Exception ex) {
      log.warn("[TRIGGER_ORPHAN_DETECTED] Failed resolving live project uniqueIds for accountId={} orgIdentifier={}, "
              + "skipping group",
          accountId, orgId, ex);
      return Optional.empty();
    }
  }

  private void populateOrphanMetric(String accountId, int count) {
    Map<String, String> metricContextMap = new HashMap<>();
    metricContextMap.put(PmsEventMonitoringConstants.ACCOUNT_ID, accountId);
    populateMetric(count, metricContextMap, ORPHAN_TRIGGERS_COUNT_METRIC);
  }

  private void populateMetric(Integer metricValue, Map<String, String> metricContextMap, String metricKey) {
    try (PmsMetricContextGuard ignore = new PmsMetricContextGuard(metricContextMap)) {
      metricService.recordMetric(metricKey, metricValue);
    }
  }

  private static String accountOrgKey(String accountId, String orgId) {
    return accountId + "|" + (orgId == null ? "" : orgId);
  }

  private static String[] splitAccountOrgKey(String key) {
    int idx = key.indexOf('|');
    if (idx < 0) {
      return new String[] {key, ""};
    }
    return new String[] {key.substring(0, idx), key.substring(idx + 1)};
  }
}

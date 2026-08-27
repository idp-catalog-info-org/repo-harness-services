/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.impl;

import io.harness.metrics.service.api.MetricService;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class TriggerMapperHelper {
  private final MetricService metricService;
  private static final String TRIGGER_FILTER_PROCESS_TIME = "trigger_filter_process_time";
  public WebhookEventMappingResponse applyFilters(
      List<TriggerFilter> triggerFilters, FilterRequestData filterRequestData) {
    WebhookEventMappingResponse webhookEventMappingResponse = null;
    TriggerFilter triggerFilterInAction = null;
    Map<String, String> metricContextMap = new HashMap<>();
    metricContextMap.put(PmsEventMonitoringConstants.ACCOUNT_ID, filterRequestData.getAccountId());

    try {
      for (TriggerFilter triggerFilter : triggerFilters) {
        metricContextMap.put(PmsEventMonitoringConstants.TRIGGER_FILTER_TYPE, triggerFilter.getClass().getSimpleName());
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        triggerFilterInAction = triggerFilter;
        webhookEventMappingResponse = triggerFilter.applyFilter(filterRequestData);
        stopWatch.stop();
        populateMetric(stopWatch.getTotalTimeMillis(), metricContextMap, TRIGGER_FILTER_PROCESS_TIME);
        if (webhookEventMappingResponse.isFailedToFindTrigger()) {
          return webhookEventMappingResponse;
        } else {
          // update with updated filter list for next filter
          filterRequestData.setDetails(webhookEventMappingResponse.getTriggers());
        }
      }
    } catch (Exception e) {
      log.warn("Exception while evaluating Triggers: ", e);
      return triggerFilterInAction.getWebhookResponseForException(filterRequestData, e);
    }
    return webhookEventMappingResponse;
  }

  private void populateMetric(long metricValue, Map<String, String> metricContextMap, String metricKey) {
    try (PmsMetricContextGuard ignore = new PmsMetricContextGuard(metricContextMap)) {
      metricService.recordMetric(metricKey, metricValue);
    }
  }
}

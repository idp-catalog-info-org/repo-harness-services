/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.metrics;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.metrics.DwMetricContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public class IdpServiceDwMetricsPublisher implements MetricsPublisher {
  @Inject private MetricService metricService;
  @Inject private MetricRegistry metricRegistry;
  private static final Double SNAPSHOT_FACTOR = 1.0D / (double) TimeUnit.SECONDS.toNanos(1L);
  private static final Pattern METRIC_NAME_RE = Pattern.compile("[^a-zA-Z0-9:_]");
  private static final String NAMESPACE = getEnvOrDefault("NAMESPACE", "local");
  private static final String CONTAINER_NAME = getEnvOrDefault("CONTAINER_NAME", "idp-service");
  private static final String SERVICE_NAME = getEnvOrDefault("SERVICE_NAME", "idp-service");
  public static final String IDP_SPEC_PACKAGE_PREFIX = "io.harness.spec.server.idp.v1.";
  public static final String IDP_RESOURCES_PACKAGE_PREFIX = "io.harness.idp.";
  private static final MetricFilter meterMetricFilter =
      MetricFilter.startsWith("io.dropwizard.jetty.MutableServletContextHandler");
  private static final MetricFilter idpSpecMetricFilter = MetricFilter.startsWith(IDP_SPEC_PACKAGE_PREFIX);
  private static final MetricFilter idpResourceMetricFilter = MetricFilter.startsWith(IDP_RESOURCES_PACKAGE_PREFIX);
  private static final String MUTABLE_SERVLET_CONTEXT_HANDLER = "MutableServletContextHandler";
  @Inject @Named("enableAPIMetrics") private boolean enableAPIMetrics;

  @Override
  public void recordMetrics() {
    Set<Map.Entry<String, Meter>> meterSet = metricRegistry.getMeters(meterMetricFilter).entrySet();
    meterSet.forEach(entry -> recordMeter(sanitizeMetricName(entry.getKey()), entry.getValue()));

    if (enableAPIMetrics) {
      Set<Map.Entry<String, Meter>> idpSpecMeterSet = metricRegistry.getMeters(idpSpecMetricFilter).entrySet();
      idpSpecMeterSet.forEach(entry -> recordMeter(sanitizeMetricName(entry.getKey()), entry.getValue()));
      Set<Map.Entry<String, Meter>> idpResourceMeterSet = metricRegistry.getMeters(idpResourceMetricFilter).entrySet();
      idpResourceMeterSet.forEach(entry -> recordMeter(sanitizeMetricName(entry.getKey()), entry.getValue()));
    }

    Set<Map.Entry<String, Timer>> timerSet = metricRegistry.getTimers().entrySet();
    timerSet.forEach(entry -> recordTimer(sanitizeMetricName(entry.getKey()), entry.getValue()));
    Set<Map.Entry<String, Counter>> counterSet = metricRegistry.getCounters().entrySet();
    counterSet.forEach(entry -> recordCounter(sanitizeMetricName(entry.getKey()), entry.getValue()));
    Set<Map.Entry<String, Gauge>> gaugeSet = metricRegistry.getGauges().entrySet();
    gaugeSet.forEach(entry -> recordGauge(sanitizeMetricName(entry.getKey()), entry.getValue()));
  }
  private void recordCounter(String metricName, Counter counter) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName, counter.getCount());
    }
  }

  private void recordMeter(String metricName, Meter meter) {
    if (metricName.contains(MUTABLE_SERVLET_CONTEXT_HANDLER)) {
      recordMeterForMutableServletContextHandler(metricName, meter);
    } else if (enableAPIMetrics && checkIfResourceMetrics(metricName, "responses")) {
      recordMeterForResponsesOfResourceMethods(metricName, meter);
    }
  }

  private void recordMeterForResponsesOfResourceMethods(String metricName, Meter meter) {
    String[] s = metricName.split("_");
    String methodName = "";
    String resourceName = "";
    String statusCode = "";
    if (s.length >= 4) {
      statusCode = s[s.length - 2];
      methodName = s[s.length - 3];
      resourceName = s[s.length - 4];
    }
    try (IDPDwMetricContext ignore = new IDPDwMetricContext(methodName, resourceName, CONTAINER_NAME, statusCode)) {
      recordMetric("io_harness_spec_server_idp_v1_responses_count", meter.getCount());
    }
  }

  private void recordMeterForMutableServletContextHandler(String metricName, Meter meter) {
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName + "_count", meter.getCount());
    }
  }

  private void recordTimer(String metricName, Timer timer) {
    if (enableAPIMetrics && checkIfResourceMetrics(metricName, "total")) {
      addTimerMetricsForResources(metricName, timer);
      return;
    }
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName + "_count", timer.getCount());
      recordSnapshot(metricName + "_snapshot", timer.getSnapshot());
    }
  }

  private void addTimerMetricsForResources(String metricName, Timer timer) {
    String[] s = metricName.split("_");
    String methodName = "";
    String resourceName = "";
    if (s.length >= 3) {
      methodName = s[s.length - 2];
      resourceName = s[s.length - 3];
    }
    try (IDPDwMetricContext ignore = new IDPDwMetricContext(methodName, resourceName, CONTAINER_NAME)) {
      String modifiedMetricName = "io_harness_spec_server_idp_v1_total";
      recordMetric(modifiedMetricName + "_count", timer.getCount());
      recordSnapshot(modifiedMetricName + "_snapshot", timer.getSnapshot());
    }
  }

  private void recordSnapshot(String metricName, Snapshot snapshot) {
    recordMetric(metricName + "_mean", snapshot.getMean() * SNAPSHOT_FACTOR);
    recordMetric(metricName + "_95thPercentile", snapshot.get95thPercentile() * SNAPSHOT_FACTOR);
    recordMetric(metricName + "_99thPercentile", snapshot.get99thPercentile() * SNAPSHOT_FACTOR);
    recordMetric(metricName + "_999thPercentile", snapshot.get999thPercentile() * SNAPSHOT_FACTOR);
  }

  private void recordMetric(String name, double value) {
    if (value < 0) {
      log.debug("Skipping negative metric value for {}: {}", name, value);
      return;
    }
    metricService.recordMetric(name, value);
  }

  private static String getEnvOrDefault(String key, String defaultValue) {
    String value = System.getenv(key);
    return value != null && !value.isEmpty() ? value : defaultValue;
  }

  private void recordGauge(String metricName, Gauge gauge) {
    Double value = getGaugeValue(metricName, gauge);
    if (value == null) {
      return;
    }
    try (DwMetricContext ignore = new DwMetricContext(NAMESPACE, CONTAINER_NAME, SERVICE_NAME)) {
      recordMetric(metricName, value);
    } catch (RuntimeException exception) {
      log.debug("Failed to record gauge metric {}", metricName, exception);
    }
  }

  private static String sanitizeMetricName(String dropwizardName) {
    String name = METRIC_NAME_RE.matcher(dropwizardName).replaceAll("_");
    if (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
      name = "_" + name;
    }
    return name;
  }

  private Double getGaugeValue(String metricName, Gauge gauge) {
    try {
      Object obj = gauge.getValue();
      if (obj instanceof Number) {
        return ((Number) obj).doubleValue();
      }
      if (obj instanceof Boolean) {
        return (Boolean) obj ? 1.0D : 0.0D;
      }
      log.debug("Invalid type for Gauge {}: {}", metricName, obj == null ? "null" : obj.getClass().getName());
      return null;
    } catch (RuntimeException exception) {
      log.debug("Failed to extract gauge value for {}", metricName, exception);
      return null;
    }
  }

  private boolean checkIfResourceMetrics(String metricName, String metricNameCheck) {
    return metricName.contains(metricNameCheck);
  }
}

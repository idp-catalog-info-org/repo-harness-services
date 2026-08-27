/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class NextGenManagerDropwizardMetricsPublisherImplTest extends CategoryTest {
  @Mock private MetricRegistry metricRegistry;
  @Mock private MetricService metricService;

  private NextGenManagerDropwizardMetricsPublisherImpl metricsPublisher;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    metricsPublisher = new NextGenManagerDropwizardMetricsPublisherImpl(metricRegistry, metricService);
  }

  @Test
  @Owner(developers = OwnerRule.UNKNOWN)
  @Category(UnitTests.class)
  public void testRecordMetrics_withEmptyRegistry() {
    SortedMap<String, Meter> emptyMeters = new TreeMap<>();
    SortedMap<String, Gauge> emptyGauges = new TreeMap<>();
    SortedMap<String, Timer> emptyTimers = new TreeMap<>();
    SortedMap<String, Counter> emptyCounters = new TreeMap<>();

    when(metricRegistry.getMeters(org.mockito.ArgumentMatchers.any(MetricFilter.class))).thenReturn(emptyMeters);
    when(metricRegistry.getGauges()).thenReturn(emptyGauges);
    when(metricRegistry.getTimers()).thenReturn(emptyTimers);
    when(metricRegistry.getCounters()).thenReturn(emptyCounters);

    metricsPublisher.recordMetrics();
  }

  @Test
  @Owner(developers = OwnerRule.UNKNOWN)
  @Category(UnitTests.class)
  public void testRecordMetrics_withCounter() {
    SortedMap<String, Meter> emptyMeters = new TreeMap<>();
    SortedMap<String, Gauge> emptyGauges = new TreeMap<>();
    SortedMap<String, Timer> emptyTimers = new TreeMap<>();
    SortedMap<String, Counter> counters = new TreeMap<>();

    Counter counter = mock(Counter.class);
    when(counter.getCount()).thenReturn(10L);
    counters.put("test_counter", counter);

    when(metricRegistry.getMeters(org.mockito.ArgumentMatchers.any(MetricFilter.class))).thenReturn(emptyMeters);
    when(metricRegistry.getGauges()).thenReturn(emptyGauges);
    when(metricRegistry.getTimers()).thenReturn(emptyTimers);
    when(metricRegistry.getCounters()).thenReturn(counters);

    metricsPublisher.recordMetrics();

    verify(metricService, atLeastOnce()).recordMetric(anyString(), anyDouble());
  }

  @Test
  @Owner(developers = OwnerRule.UNKNOWN)
  @Category(UnitTests.class)
  public void testRecordMetrics_withGauge() {
    SortedMap<String, Meter> emptyMeters = new TreeMap<>();
    SortedMap<String, Gauge> gauges = new TreeMap<>();
    SortedMap<String, Timer> emptyTimers = new TreeMap<>();
    SortedMap<String, Counter> emptyCounters = new TreeMap<>();

    Gauge<Number> gauge = mock(Gauge.class);
    when(gauge.getValue()).thenReturn(42.0);
    gauges.put("test_gauge", gauge);

    when(metricRegistry.getMeters(org.mockito.ArgumentMatchers.any(MetricFilter.class))).thenReturn(emptyMeters);
    when(metricRegistry.getGauges()).thenReturn(gauges);
    when(metricRegistry.getTimers()).thenReturn(emptyTimers);
    when(metricRegistry.getCounters()).thenReturn(emptyCounters);

    metricsPublisher.recordMetrics();

    verify(metricService, atLeastOnce()).recordMetric(anyString(), anyDouble());
  }

  @Test
  @Owner(developers = OwnerRule.UNKNOWN)
  @Category(UnitTests.class)
  public void testRecordMetrics_withTimer() {
    SortedMap<String, Meter> emptyMeters = new TreeMap<>();
    SortedMap<String, Gauge> emptyGauges = new TreeMap<>();
    SortedMap<String, Timer> timers = new TreeMap<>();
    SortedMap<String, Counter> emptyCounters = new TreeMap<>();

    Timer timer = mock(Timer.class);
    Snapshot snapshot = mock(Snapshot.class);
    when(timer.getCount()).thenReturn(5L);
    when(timer.getSnapshot()).thenReturn(snapshot);
    when(snapshot.get95thPercentile()).thenReturn(100.0);
    when(snapshot.get99thPercentile()).thenReturn(200.0);
    when(snapshot.get999thPercentile()).thenReturn(300.0);
    timers.put("test_timer", timer);

    when(metricRegistry.getMeters(org.mockito.ArgumentMatchers.any(MetricFilter.class))).thenReturn(emptyMeters);
    when(metricRegistry.getGauges()).thenReturn(emptyGauges);
    when(metricRegistry.getTimers()).thenReturn(timers);
    when(metricRegistry.getCounters()).thenReturn(emptyCounters);

    metricsPublisher.recordMetrics();

    verify(metricService, atLeastOnce()).recordMetric(anyString(), anyDouble());
  }

  @Test
  @Owner(developers = OwnerRule.UNKNOWN)
  @Category(UnitTests.class)
  public void testRecordMetrics_withMeter() {
    SortedMap<String, Meter> meters = new TreeMap<>();
    SortedMap<String, Gauge> emptyGauges = new TreeMap<>();
    SortedMap<String, Timer> emptyTimers = new TreeMap<>();
    SortedMap<String, Counter> emptyCounters = new TreeMap<>();

    Meter meter = mock(Meter.class);
    when(meter.getCount()).thenReturn(100L);
    meters.put("io.dropwizard.jetty.MutableServletContextHandler.test", meter);

    when(metricRegistry.getMeters(org.mockito.ArgumentMatchers.any(MetricFilter.class)))
        .thenReturn(meters, new TreeMap<>());
    when(metricRegistry.getGauges()).thenReturn(emptyGauges);
    when(metricRegistry.getTimers()).thenReturn(emptyTimers);
    when(metricRegistry.getCounters()).thenReturn(emptyCounters);

    metricsPublisher.recordMetrics();

    verify(metricService, atLeastOnce()).recordMetric(anyString(), anyDouble());
  }

  @Test
  @Owner(developers = OwnerRule.UNKNOWN)
  @Category(UnitTests.class)
  public void testRecordMetrics_withBooleanGauge() {
    SortedMap<String, Meter> emptyMeters = new TreeMap<>();
    SortedMap<String, Gauge> gauges = new TreeMap<>();
    SortedMap<String, Timer> emptyTimers = new TreeMap<>();
    SortedMap<String, Counter> emptyCounters = new TreeMap<>();

    Gauge<Boolean> gauge = mock(Gauge.class);
    when(gauge.getValue()).thenReturn(true);
    gauges.put("test_boolean_gauge", gauge);

    when(metricRegistry.getMeters(org.mockito.ArgumentMatchers.any(MetricFilter.class))).thenReturn(emptyMeters);
    when(metricRegistry.getGauges()).thenReturn(gauges);
    when(metricRegistry.getTimers()).thenReturn(emptyTimers);
    when(metricRegistry.getCounters()).thenReturn(emptyCounters);

    metricsPublisher.recordMetrics();

    verify(metricService, atLeastOnce()).recordMetric(anyString(), anyDouble());
  }
}

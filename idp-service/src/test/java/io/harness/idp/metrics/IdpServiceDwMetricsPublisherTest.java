/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.metrics;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.rule.Owner;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import java.lang.reflect.Field;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IdpServiceDwMetricsPublisherTest extends CategoryTest {
  AutoCloseable openMocks;
  IdpServiceDwMetricsPublisher publisher;
  @Mock MetricService metricService;
  MetricRegistry metricRegistry;

  @Before
  public void setUp() throws Exception {
    openMocks = MockitoAnnotations.openMocks(this);
    metricRegistry = new MetricRegistry();
    publisher = new IdpServiceDwMetricsPublisher();

    Field metricServiceField = IdpServiceDwMetricsPublisher.class.getDeclaredField("metricService");
    metricServiceField.setAccessible(true);
    metricServiceField.set(publisher, metricService);

    Field metricRegistryField = IdpServiceDwMetricsPublisher.class.getDeclaredField("metricRegistry");
    metricRegistryField.setAccessible(true);
    metricRegistryField.set(publisher, metricRegistry);

    Field enableAPIMetricsField = IdpServiceDwMetricsPublisher.class.getDeclaredField("enableAPIMetrics");
    enableAPIMetricsField.setAccessible(true);
    enableAPIMetricsField.set(publisher, false);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordMetricsWithMeter() {
    metricRegistry.meter("io.dropwizard.jetty.MutableServletContextHandler.requests");

    publisher.recordMetrics();

    verify(metricService, atLeastOnce()).recordMetric(anyString(), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordMetricsWithTimer() {
    Timer timer = metricRegistry.timer("test.timer");
    timer.update(100, java.util.concurrent.TimeUnit.MILLISECONDS);

    publisher.recordMetrics();

    verify(metricService, atLeastOnce()).recordMetric(anyString(), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordMetricsWithCounter() {
    Counter counter = metricRegistry.counter("test.counter");
    counter.inc(5);

    publisher.recordMetrics();

    verify(metricService, times(1)).recordMetric(eq("test_counter"), eq(5.0));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordMetricsWithNumericGauge() {
    metricRegistry.register("test.gauge", (Gauge<Integer>) () -> 42);

    publisher.recordMetrics();

    verify(metricService, times(1)).recordMetric(eq("test_gauge"), eq(42.0));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordMetricsWithNonNumericGauge() {
    metricRegistry.register("test.string.gauge", (Gauge<String>) () -> "not a number");

    publisher.recordMetrics();

    verify(metricService, never()).recordMetric(eq("test_string_gauge"), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testThreadPoolMetricDetection() {
    Counter counter = metricRegistry.counter("idpServiceExecutor_queue_size");
    counter.inc(10);

    publisher.recordMetrics();

    verify(metricService, times(1)).recordMetric(eq("idpServiceExecutor_queue_size"), eq(10.0));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testAPIMetricsRecordingWhenEnabled() throws Exception {
    Field enableAPIMetricsField = IdpServiceDwMetricsPublisher.class.getDeclaredField("enableAPIMetrics");
    enableAPIMetricsField.setAccessible(true);
    enableAPIMetricsField.set(publisher, true);

    metricRegistry.meter("io.harness.spec.server.idp.v1.ResourceName_methodName_responses_200");

    publisher.recordMetrics();

    verify(metricService, atLeastOnce()).recordMetric(eq("io_harness_spec_server_idp_v1_responses_count"), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testAPITimerMetricsRecordingWhenEnabled() throws Exception {
    Field enableAPIMetricsField = IdpServiceDwMetricsPublisher.class.getDeclaredField("enableAPIMetrics");
    enableAPIMetricsField.setAccessible(true);
    enableAPIMetricsField.set(publisher, true);

    Timer timer = metricRegistry.timer("io.harness.spec.server.idp.v1.ResourceName_methodName_total");
    timer.update(50, java.util.concurrent.TimeUnit.MILLISECONDS);

    publisher.recordMetrics();

    verify(metricService, atLeastOnce()).recordMetric(eq("io_harness_spec_server_idp_v1_total_count"), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testAPIResourceMetricsFromIdpPackage() throws Exception {
    Field enableAPIMetricsField = IdpServiceDwMetricsPublisher.class.getDeclaredField("enableAPIMetrics");
    enableAPIMetricsField.setAccessible(true);
    enableAPIMetricsField.set(publisher, true);

    metricRegistry.meter("io.harness.idp.ResourceName_methodName_responses_201");

    publisher.recordMetrics();

    verify(metricService, atLeastOnce()).recordMetric(eq("io_harness_spec_server_idp_v1_responses_count"), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testAPIMetricsNotRecordedWhenDisabled() {
    metricRegistry.meter("io.harness.spec.server.idp.v1.test_resource_method_responses_200");

    publisher.recordMetrics();

    verify(metricService, never()).recordMetric(eq("io_harness_spec_server_idp_v1_responses_count"), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBooleanGaugeRecordsAsOne() {
    metricRegistry.register("test.bool.gauge", (Gauge<Boolean>) () -> true);

    publisher.recordMetrics();

    verify(metricService, times(1)).recordMetric(eq("test_bool_gauge"), eq(1.0));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGaugeExceptionHandledGracefully() {
    metricRegistry.register("test.error.gauge", (Gauge<Integer>) () -> { throw new RuntimeException("gauge error"); });

    publisher.recordMetrics();

    verify(metricService, never()).recordMetric(eq("test_error_gauge"), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testMetricNameSanitization() {
    Counter counter = metricRegistry.counter("test.metric-with-special/chars");
    counter.inc(1);

    publisher.recordMetrics();

    verify(metricService, times(1)).recordMetric(eq("test_metric_with_special_chars"), eq(1.0));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testEmptyMetricRegistry() {
    publisher.recordMetrics();

    verify(metricService, never()).recordMetric(anyString(), anyDouble());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testNegativeValueSkipped() {
    metricRegistry.register("test.gauge", (Gauge<Double>) () -> - 5.0);

    publisher.recordMetrics();

    verify(metricService, never()).recordMetric(eq("test_gauge"), eq(-5.0));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}

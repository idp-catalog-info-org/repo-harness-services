/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener;

import static io.harness.ng.core.event.listener.EntityCRUDStreamListenerMetrics.LABEL_ACTION;
import static io.harness.ng.core.event.listener.EntityCRUDStreamListenerMetrics.LABEL_ENTITY_TYPE;
import static io.harness.ng.core.event.listener.EntityCRUDStreamListenerMetrics.LABEL_STATUS;
import static io.harness.ng.core.event.listener.EntityCRUDStreamListenerMetrics.PROCESS_TIME_METRIC;
import static io.harness.ng.core.event.listener.EntityCRUDStreamListenerMetrics.STATUS_FAILURE;
import static io.harness.ng.core.event.listener.EntityCRUDStreamListenerMetrics.STATUS_SUCCESS;
import static io.harness.rule.OwnerRule.AKSHAT_GOYAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.rule.Owner;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EntityCRUDStreamListenerMetricsTest extends CategoryTest {
  private MetricService metricService;

  @Before
  public void setUp() {
    metricService = mock(MetricService.class);
  }

  @Test
  @Owner(developers = AKSHAT_GOYAL)
  @Category(UnitTests.class)
  public void testExecuteWithMetricsRecordsDurationOnSuccess() {
    AtomicBoolean ran = new AtomicBoolean(false);

    Boolean result = EntityCRUDStreamListenerMetrics.executeWithMetrics("project", "create", metricService, () -> {
      ran.set(true);
      return true;
    });

    assertThat(result).isTrue();
    assertThat(ran.get()).isTrue();
    verify(metricService).recordMetric(eq(PROCESS_TIME_METRIC), anyDouble());
  }

  @Test
  @Owner(developers = AKSHAT_GOYAL)
  @Category(UnitTests.class)
  public void testExecuteWithMetricsRecordsOnException() {
    assertThatThrownBy(()
                           -> EntityCRUDStreamListenerMetrics.executeWithMetrics("organization", "create",
                               metricService, () -> { throw new IllegalStateException("boom"); }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");

    // Duration is still recorded (close() runs via try-with-resources).
    verify(metricService).recordMetric(eq(PROCESS_TIME_METRIC), anyDouble());
  }

  @Test
  @Owner(developers = AKSHAT_GOYAL)
  @Category(UnitTests.class)
  public void testExecuteWithMetricsNullMetricServiceStillRunsOperation() {
    Boolean result = EntityCRUDStreamListenerMetrics.executeWithMetrics("project", "delete", null, () -> Boolean.TRUE);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = AKSHAT_GOYAL)
  @Category(UnitTests.class)
  public void testLabelConstantsForDashboards() {
    EntityCRUDStreamListenerMetrics.executeWithMetrics(null, "", metricService, () -> true);
    verify(metricService).recordMetric(eq(PROCESS_TIME_METRIC), anyDouble());
    assertThat(LABEL_ENTITY_TYPE).isEqualTo("entityType");
    assertThat(LABEL_ACTION).isEqualTo("action");
    assertThat(LABEL_STATUS).isEqualTo("status");
    assertThat(STATUS_SUCCESS).isEqualTo("success");
    assertThat(STATUS_FAILURE).isEqualTo("failure");
  }
}

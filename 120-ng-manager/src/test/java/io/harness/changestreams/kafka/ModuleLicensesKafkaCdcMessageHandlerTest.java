/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changestreams.kafka;

import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.changestreams.eventhandlers.ModuleLicensesChangeEventHandler;
import io.harness.debezium.DebeziumChangeEvent;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.gitops.config.CdcKafkaConfig;
import io.harness.ng.gitops.config.CdcKafkaConsumerConfig;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.GTM)
@RunWith(MockitoJUnitRunner.class)
public class ModuleLicensesKafkaCdcMessageHandlerTest extends CategoryTest {
  @Mock private ModuleLicensesChangeEventHandler eventHandler;
  @Mock private CdcKafkaConfig cdcKafkaConfig;
  @Mock private MetricService metricService;
  @Mock private CdcKafkaConsumerConfig consumerConfig;

  private ModuleLicensesKafkaCdcMessageHandler handler;

  private static final DebeziumChangeEvent CREATE_EVENT = DebeziumChangeEvent.newBuilder()
                                                              .setKey("{\"id\":\"id-1\"}")
                                                              .setValue("{}")
                                                              .setOptype("CREATE")
                                                              .setTimestamp(0L)
                                                              .build();

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    // Use retryBackoffMs=0 so retry tests don't sleep — keeps suite fast.
    handler = new ModuleLicensesKafkaCdcMessageHandler(eventHandler, cdcKafkaConfig, metricService, 0L);
    when(cdcKafkaConfig.getConsumer(ModuleLicensesKafkaConsumer.CONSUMER_CONFIG_KEY))
        .thenReturn(Optional.of(consumerConfig));
    when(consumerConfig.isProcessingEnabled()).thenReturn(true);
  }

  // ── processingEnabled gate ──────────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void handleEvent_processingDisabled_drainsWithoutCallingHandler() {
    when(consumerConfig.isProcessingEnabled()).thenReturn(false);

    boolean result = handler.handleEvent(CREATE_EVENT);

    assertThat(result).isTrue();
    verify(eventHandler, never()).handleEvent(any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void handleEvent_noConsumerConfig_defaultsToDisabled() {
    when(cdcKafkaConfig.getConsumer(ModuleLicensesKafkaConsumer.CONSUMER_CONFIG_KEY)).thenReturn(Optional.empty());

    boolean result = handler.handleEvent(CREATE_EVENT);

    assertThat(result).isTrue();
    verify(eventHandler, never()).handleEvent(any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void handleEvent_nullEvent_skipsWithoutCallingHandler() {
    boolean result = handler.handleEvent(null);

    assertThat(result).isTrue();
    verify(eventHandler, never()).handleEvent(any());
  }

  // ── success path ────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void handleEvent_processingEnabled_delegatesToEventHandlerAndReturnsTrue() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenReturn(true);

    boolean result = handler.handleEvent(CREATE_EVENT);

    assertThat(result).isTrue();
    verify(eventHandler, times(1)).handleEvent(CREATE_EVENT);
    verify(metricService, never()).incCounter(any());
  }

  // ── retry on false return ────────────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void handleEvent_handlerReturnsFalseThenTrue_retriesUntilSuccess() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenReturn(false).thenReturn(true);

    boolean result = handler.handleEvent(CREATE_EVENT);

    assertThat(result).isTrue();
    verify(eventHandler, times(2)).handleEvent(any());
    verify(metricService, never()).incCounter(any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void handleEvent_handlerPersistentlyReturnsFalse_retriesMaxTimesAndEmitsMetric() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenReturn(false);

    boolean result = handler.handleEvent(CREATE_EVENT);

    assertThat(result).isFalse();
    verify(eventHandler, times(ModuleLicensesKafkaCdcMessageHandler.MAX_RETRIES)).handleEvent(any());
    verify(metricService, times(1)).incCounter(eq(ModuleLicensesKafkaCdcMessageHandler.ERROR_METRIC));
  }

  // ── retry on exception ──────────────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void handleEvent_transientExceptionThenSuccess_retriesAndSucceeds() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class)))
        .thenThrow(new RuntimeException("transient"))
        .thenReturn(true);

    boolean result = handler.handleEvent(CREATE_EVENT);

    assertThat(result).isTrue();
    verify(eventHandler, times(2)).handleEvent(any());
    verify(metricService, never()).incCounter(any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void handleEvent_persistentException_retriesMaxTimesAndEmitsMetric() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenThrow(new RuntimeException("permanent"));

    boolean result = handler.handleEvent(CREATE_EVENT);

    assertThat(result).isFalse();
    verify(eventHandler, times(ModuleLicensesKafkaCdcMessageHandler.MAX_RETRIES)).handleEvent(any());
    verify(metricService, times(1)).incCounter(eq(ModuleLicensesKafkaCdcMessageHandler.ERROR_METRIC));
  }

  // ── isProcessingEnabled ─────────────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void isProcessingEnabled_configTrue_returnsTrue() {
    when(consumerConfig.isProcessingEnabled()).thenReturn(true);
    assertThat(handler.isProcessingEnabled()).isTrue();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void isProcessingEnabled_configFalse_returnsFalse() {
    when(consumerConfig.isProcessingEnabled()).thenReturn(false);
    assertThat(handler.isProcessingEnabled()).isFalse();
  }
}

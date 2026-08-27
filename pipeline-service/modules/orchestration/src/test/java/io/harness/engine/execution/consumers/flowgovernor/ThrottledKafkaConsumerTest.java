/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution.consumers.flowgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.LoggerRule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.MessageHandler;
import io.harness.eventsframework.schemas.common.DeadLetter;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.config.KafkaConsumerConfig;
import io.harness.kafka.consumers.HaltAwareRebalanceListener;
import io.harness.kafka.producers.HKafkaProducer;
import io.harness.monitoring.EventMonitoringService;
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.rule.Owner;

import com.google.common.util.concurrent.MoreExecutors;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ThrottledKafkaConsumer}. The consumer is driven with a mocked Kafka
 * {@link Consumer} + a mocked state cache; the ingestion pipeline, worker loop, and mode poller
 * are invoked directly rather than through {@code start()} so we avoid spinning up the executor
 * pools and can assert internal state deterministically.
 */
@OwnedBy(PIPELINE)
public class ThrottledKafkaConsumerTest extends CategoryTest {
  private static final String CONSUMER_KEY = FlowGovernorConsumerKeys.INITIATE_NODE;
  private static final String TOPIC = "initiate_node_event_topic";
  private static final int PARTITION_NUM = 0;
  private static final TopicPartition PARTITION = new TopicPartition(TOPIC, PARTITION_NUM);

  @Mock private KafkaConsumerConfig<InitiateNodeEvent> consumerConfig;
  @Mock private MessageHandler<InitiateNodeEvent> messageHandler;
  @Mock private HKafkaProducer<DeadLetter> dlqReporter;
  @Mock private Consumer<String, InitiateNodeEvent> kafkaConsumer;
  @Mock private ConsumerMaintenanceListener consumerMaintenanceListener;
  @Mock private FlowGovernorStateCache stateCache;

  @Rule public LoggerRule loggerRule = new LoggerRule();

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(consumerConfig.getTopic()).thenReturn(TOPIC);
    when(consumerConfig.getConsumerRecordFilters()).thenReturn(Collections.emptyList());
    when(consumerConfig.getConsumerMode()).thenReturn(KafkaConsumerConfig.ConsumerMode.UNORDERED);
    when(consumerConfig.getConsumerMaintenanceListener()).thenReturn(consumerMaintenanceListener);
    when(consumerConfig.getMessageHandler()).thenReturn(messageHandler);
    when(consumerConfig.isNoAck()).thenReturn(true);
    // Workers hand dispatch to this executor. Direct execution keeps handler invocation on the
    // worker thread so assertions stay synchronous.
    when(consumerConfig.getExecutorService()).thenReturn(MoreExecutors.newDirectExecutorService());
    when(kafkaConsumer.assignment()).thenReturn(Collections.singleton(PARTITION));
  }

  /**
   * Build the throttled consumer under test via the test-only constructor. The mocked
   * {@code Consumer} means the parent's poll loop never contacts a real broker.
   */
  private ThrottledKafkaConsumer<InitiateNodeEvent> build(FlowGovernorConfig config) {
    return new ThrottledKafkaConsumer<>(
        consumerConfig, kafkaConsumer, dlqReporter, new Properties(), CONSUMER_KEY, config, stateCache);
  }

  private FlowGovernorConfig enabledConfig(int normalRps) {
    return new FlowGovernorConfig(true, normalRps, null, ThrottledConsumerConfig.defaults());
  }

  private FlowGovernorConfig enabledConfigWithOverrides(int normalRps, Map<String, Integer> overrides) {
    return new FlowGovernorConfig(true, normalRps, overrides, ThrottledConsumerConfig.defaults());
  }

  // ---- Task #2: governor-off short-circuit ---------------------------------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void governorOff_allocatesNothing_andDelegatesRunInternalToSuper() throws Exception {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(FlowGovernorConfig.disabled());

    // Bit-identical vanilla contract: no queue / worker pool / mode poller / rate limiter allocated.
    assertThat(consumer.governorEnabled).isFalse();
    assertThat(consumer.queue).isNull();
    assertThat(consumer.workerPool).isNull();
    assertThat(consumer.modePoller).isNull();
    assertThat(consumer.rateLimiter).isNull();
    assertThat(consumer.throttleConfig).isNull();

    // runInternal() must go through the super path — which polls Kafka.
    when(kafkaConsumer.poll(any())).thenReturn(ConsumerRecords.empty());
    consumer.runInternal();
    verify(kafkaConsumer, times(1)).poll(any());
    // Throttle machinery must remain absent even after invoking runInternal once.
    assertThat(consumer.pausedByHalt.get()).isFalse();
    assertThat(consumer.pausedByWatermark.get()).isFalse();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void governorOff_createsAtMostOnceRebalanceListener() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(FlowGovernorConfig.disabled());

    ConsumerRebalanceListener listener = consumer.createRebalanceListener();

    assertThat(listener).isNotNull().isNotInstanceOf(HaltAwareRebalanceListener.class);
  }

  // ---- Task #3: mode transitions -------------------------------------------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void modeFlipToHalted_setsPauseFlag_andSubsequentRunInternalPausesAssignment() throws Exception {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));
    when(stateCache.getState())
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.HALTED).version(1L).build());
    when(kafkaConsumer.poll(any())).thenReturn(ConsumerRecords.empty());

    consumer.pollMode();
    consumer.runInternal();

    assertThat(consumer.currentMode.get()).isEqualTo(FlowGovernorState.Mode.HALTED);
    assertThat(consumer.pausedByHalt.get()).isTrue();
    verify(kafkaConsumer, times(1)).pause(Collections.singleton(PARTITION));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void modeFlipFromHaltedToNormal_requestsResume_andRunInternalResumesAssignment() throws Exception {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));

    // First flip to HALTED.
    when(stateCache.getState())
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.HALTED).version(1L).build())
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.NORMAL).version(2L).build());
    consumer.pollMode();
    assertThat(consumer.pausedByHalt.get()).isTrue();

    // Then back to NORMAL — mode poller should request the ingestion thread to resume.
    consumer.pollMode();
    assertThat(consumer.currentMode.get()).isEqualTo(FlowGovernorState.Mode.NORMAL);
    assertThat(consumer.pausedByHalt.get()).isFalse();
    assertThat(consumer.resumeRequested.get()).isTrue();

    // Ingestion thread on next iteration consumes the request and calls resume() on the consumer.
    when(kafkaConsumer.poll(any())).thenReturn(ConsumerRecords.empty());
    consumer.runInternal();
    verify(kafkaConsumer, times(1)).resume(Collections.singleton(PARTITION));
    assertThat(consumer.resumeRequested.get()).isFalse();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void modeFlipToThrottled_dropsRpsToTargetValue() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));
    when(stateCache.getState())
        .thenReturn(
            FlowGovernorState.builder().mode(FlowGovernorState.Mode.THROTTLED).targetRps(7).version(1L).build());

    consumer.pollMode();

    assertThat(consumer.currentMode.get()).isEqualTo(FlowGovernorState.Mode.THROTTLED);
    assertThat(consumer.rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(7);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void modeFlipFromThrottledToNormal_restoresNormalRpsCeiling() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));

    when(stateCache.getState())
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.THROTTLED).targetRps(7).version(1L).build())
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.NORMAL).version(2L).build());

    consumer.pollMode();
    consumer.pollMode();

    assertThat(consumer.currentMode.get()).isEqualTo(FlowGovernorState.Mode.NORMAL);
    assertThat(consumer.rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(1000);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void throttledWithNoRps_fallsBackToSafetyValue() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));
    // THROTTLED but no rps set anywhere.
    when(stateCache.getState())
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.THROTTLED).version(1L).build());

    consumer.pollMode();

    assertThat(consumer.rateLimiter.getRateLimiterConfig().getLimitForPeriod())
        .isEqualTo(ThrottledKafkaConsumer.SAFETY_THROTTLE_FALLBACK_RPS);
  }

  // ---- Task #4: watermark hysteresis ---------------------------------------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void offerRecord_crossingHighWatermark_pausesAssignment() throws Exception {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));
    int highMark = consumer.throttleConfig.highWatermarkThreshold();

    // Simulate a poll that fills the queue up to the high watermark.
    ConsumerRecord<String, InitiateNodeEvent> record = protoRecord(0);
    ConsumerRecords<String, InitiateNodeEvent> batch =
        new ConsumerRecords<>(Map.of(PARTITION, java.util.Collections.nCopies(highMark, record)));
    when(kafkaConsumer.poll(any())).thenReturn(batch);

    consumer.runInternal();

    assertThat(consumer.queue.size()).isGreaterThanOrEqualTo(highMark);
    assertThat(consumer.pausedByWatermark.get()).isTrue();
    verify(kafkaConsumer, times(1)).pause(Collections.singleton(PARTITION));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void maybeResume_drainedBelowLowWatermark_requestsResume() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));
    int lowMark = consumer.throttleConfig.lowWatermarkThreshold();

    // Force the paused-by-watermark state, then drain the queue below the low mark and call
    // maybeResume() directly — same pathway workers take after dequeueing.
    consumer.pausedByWatermark.set(true);
    for (int i = 0; i < lowMark; i++) {
      consumer.queue.offer(protoRecord(i));
    }

    consumer.maybeResume();

    assertThat(consumer.pausedByWatermark.get()).isFalse();
    assertThat(consumer.resumeRequested.get()).isTrue();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void maybeResume_underHalt_doesNotFlipFlags() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));
    consumer.pausedByWatermark.set(true);
    consumer.pausedByHalt.set(true); // HALT wins — resume should not fire even if queue is empty.

    consumer.maybeResume();

    assertThat(consumer.pausedByWatermark.get()).isTrue();
    assertThat(consumer.resumeRequested.get()).isFalse();
  }

  // ---- Task #5: HALT semantics ---------------------------------------------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void workerLoop_underHalt_stillDrainsAlreadyQueuedRecordsThroughHandler() throws Exception {
    // HALT stops intake, not processing. These records' offsets are already committed on the
    // ingestion side, so neither dropping them nor holding them in an in-memory queue that dies
    // with the pod is acceptable — they must reach the handler.
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(10_000));
    consumer.queue.offer(protoRecord(0));
    consumer.queue.offer(protoRecord(1));
    consumer.currentMode.set(FlowGovernorState.Mode.HALTED);

    Thread worker = new Thread(consumer::workerLoop, "test-worker");
    worker.setDaemon(true);
    worker.start();

    awaitCondition(() -> consumer.queue.isEmpty(), 5_000);
    consumer.stopped.set(true);
    worker.join(3_000);

    assertThat(worker.isAlive()).isFalse();
    verify(messageHandler, times(2)).onMessage(any(), anyMap(), anyMap());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void workerLoop_underHalt_stillHonoursTheRateLimit() throws Exception {
    // Draining under HALT must not become an escape hatch from THROTTLE: the residual buffer goes
    // out at the configured rps, not as fast as the workers can spin.
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1));
    for (int i = 0; i < 5; i++) {
      consumer.queue.offer(protoRecord(i));
    }
    consumer.currentMode.set(FlowGovernorState.Mode.HALTED);

    Thread worker = new Thread(consumer::workerLoop, "test-worker");
    worker.setDaemon(true);
    worker.start();

    // At 1 rps a single worker cannot have cleared all 5 within ~1.2s.
    Thread.sleep(1_200);
    consumer.stopped.set(true);
    worker.join(3_000);

    assertThat(consumer.queue).isNotEmpty();
    verify(messageHandler, atMost(3)).onMessage(any(), anyMap(), anyMap());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void runInternal_underHalt_pausesAssignment_andPollContinues() throws Exception {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));
    consumer.pausedByHalt.set(true);
    when(kafkaConsumer.poll(any())).thenReturn(ConsumerRecords.empty());

    consumer.runInternal();
    consumer.runInternal();

    // Both iterations must call pause() (Kafka drops the flag on assignment change; we reapply
    // defensively) and must call poll() to keep max.poll.interval.ms fresh.
    verify(kafkaConsumer, times(2)).pause(Collections.singleton(PARTITION));
    verify(kafkaConsumer, times(2)).poll(any());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shouldRepauseOnAssign_returnsTrue_whenHalted() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));

    assertThat(consumer.shouldRepauseOnAssign()).isFalse();

    consumer.pausedByHalt.set(true);
    assertThat(consumer.shouldRepauseOnAssign()).isTrue();

    consumer.pausedByHalt.set(false);
    consumer.pausedByWatermark.set(true);
    assertThat(consumer.shouldRepauseOnAssign()).isTrue();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void createRebalanceListener_enabled_returnsHaltAwareListener() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));

    ConsumerRebalanceListener listener = consumer.createRebalanceListener();

    assertThat(listener).isInstanceOf(HaltAwareRebalanceListener.class);
  }

  // ---- Task #6: permit loop never abandons a dequeued record ----------------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void acquirePermit_underHalt_stillAcquires() {
    // HALT must NOT abort the permit wait. The caller is already holding a dequeued, committed
    // record; returning false here would mean dropping it.
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(10_000));
    consumer.currentMode.set(FlowGovernorState.Mode.HALTED);

    assertThat(consumer.acquirePermit(protoRecord(0))).isTrue();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void acquirePermit_returnsTrueImmediately_whenPermitAvailable() {
    // Very high NORMAL rps ⇒ every acquire attempt succeeds on the first try.
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(10_000));

    assertThat(consumer.acquirePermit(protoRecord(0))).isTrue();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void acquirePermit_keepsRetryingPastPermitTimeout_ratherThanDropping() throws Exception {
    // 1 rps with the bucket already drained: the first attempt times out after
    // permitAcquireTimeoutMs, and the method must loop rather than give up. Anything else is a
    // silent drop of an already-committed record.
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1));
    assertThat(consumer.acquirePermit(protoRecord(0))).isTrue(); // drains the bucket

    long start = System.nanoTime();
    assertThat(consumer.acquirePermit(protoRecord(1))).isTrue();
    long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

    // Proves it waited across at least one refresh period instead of returning false early.
    assertThat(elapsedMs).isGreaterThan(consumer.throttleConfig.getRateLimiterRefreshPeriodMs() / 2);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void acquirePermit_returnsFalse_whenInterruptedDuringWait() throws Exception {
    // The only sanctioned bail-out: forced shutdown. Everything else retries.
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1));
    consumer.acquirePermit(protoRecord(0)); // drain the bucket so the next call has to wait

    AtomicReference<Boolean> result = new AtomicReference<>();
    Thread worker = new Thread(() -> result.set(consumer.acquirePermit(protoRecord(1))), "test-worker");
    worker.setDaemon(true);
    worker.start();
    Thread.sleep(100);
    worker.interrupt();
    worker.join(5_000);

    assertThat(worker.isAlive()).isFalse();
    assertThat(result.get()).isFalse();
  }

  // ---- Task #7: per-consumer RPS resolution --------------------------------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void perConsumerOverride_wiresInitialRpsFromConfig() {
    Map<String, Integer> overrides = new HashMap<>();
    overrides.put(FlowGovernorConsumerKeys.INITIATE_NODE, 42);
    overrides.put(FlowGovernorConsumerKeys.SDK_STEP_RESPONSE, 999);
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfigWithOverrides(1000, overrides));

    // The rate limiter was constructed with the per-consumer normal RPS.
    assertThat(consumer.rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(42);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void perConsumerOverride_appliedOnThrottledMode() {
    Map<String, Integer> stateOverrides = new HashMap<>();
    stateOverrides.put(FlowGovernorConsumerKeys.INITIATE_NODE, 3);
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));
    when(stateCache.getState())
        .thenReturn(FlowGovernorState.builder()
                        .mode(FlowGovernorState.Mode.THROTTLED)
                        .targetRps(20)
                        .targetRpsByConsumer(stateOverrides)
                        .version(1L)
                        .build());

    consumer.pollMode();

    // Per-consumer override wins over default 20.
    assertThat(consumer.rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(3);
  }

  // ---- Task #8: RPS change without mode flip -------------------------------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void pollMode_throttledUnchanged_appliesLatestRpsFromState() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));

    // First flip to THROTTLED @ 10 rps.
    when(stateCache.getState())
        .thenReturn(
            FlowGovernorState.builder().mode(FlowGovernorState.Mode.THROTTLED).targetRps(10).version(1L).build())
        // Second poll: still THROTTLED but rps bumped to 25.
        .thenReturn(
            FlowGovernorState.builder().mode(FlowGovernorState.Mode.THROTTLED).targetRps(25).version(2L).build());

    consumer.pollMode();
    assertThat(consumer.rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(10);

    consumer.pollMode();
    assertThat(consumer.currentMode.get()).isEqualTo(FlowGovernorState.Mode.THROTTLED);
    assertThat(consumer.rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(25);
  }

  // ---- Task #9: fail-fast constructor guards -------------------------------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void constructor_orderedMode_throwsUnsupported() {
    when(consumerConfig.getConsumerMode()).thenReturn(KafkaConsumerConfig.ConsumerMode.ORDERED);

    assertThatThrownBy(() -> build(enabledConfig(1000)))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("ORDERED consumer mode");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void constructor_isNoAckFalse_throwsUnsupported() {
    when(consumerConfig.isNoAck()).thenReturn(false);

    assertThatThrownBy(() -> build(enabledConfig(1000)))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("isNoAck=true");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void constructor_disabledConfig_skipsFailFastChecks() {
    // Even with an incompatible consumer mode, disabled config must not throw — the vanilla
    // super path handles ORDERED itself.
    when(consumerConfig.getConsumerMode()).thenReturn(KafkaConsumerConfig.ConsumerMode.ORDERED);

    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(FlowGovernorConfig.disabled());

    assertThat(consumer.governorEnabled).isFalse();
  }

  // ---- Metric emission wiring ----------------------------------------------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void pollMode_wiresQueueDepthGauge() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));
    EventMonitoringService monitoring = mock(EventMonitoringService.class);
    consumer.metricEmitter = new FlowGovernorMetricEmitter(monitoring, TOPIC);
    when(stateCache.getState())
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.NORMAL).version(1L).build());

    consumer.pollMode();

    verify(monitoring, atLeastOnce()).sendMetric(eq(FlowGovernorMetrics.QUEUE_DEPTH), anyLong());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void pollMode_throttled_emitsRpsExpectedAndActual() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));
    EventMonitoringService monitoring = mock(EventMonitoringService.class);
    consumer.metricEmitter = new FlowGovernorMetricEmitter(monitoring, TOPIC);
    when(stateCache.getState())
        .thenReturn(
            FlowGovernorState.builder().mode(FlowGovernorState.Mode.THROTTLED).targetRps(10).version(1L).build());

    consumer.pollMode();

    verify(monitoring, times(1)).sendMetric(FlowGovernorMetrics.RPS_EXPECTED, 10L);
    verify(monitoring, atLeastOnce()).sendMetric(eq(FlowGovernorMetrics.RPS_ACTUAL), anyLong());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void pollMode_flipToHalted_emitsPauseResume() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(1000));
    EventMonitoringService monitoring = mock(EventMonitoringService.class);
    consumer.metricEmitter = new FlowGovernorMetricEmitter(monitoring, TOPIC);
    when(stateCache.getState())
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.HALTED).version(1L).build())
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.NORMAL).version(2L).build());

    consumer.pollMode(); // NORMAL → HALTED: 1 pause_resume
    consumer.pollMode(); // HALTED → NORMAL: 1 pause_resume

    verify(monitoring, times(2)).incCounter(FlowGovernorMetrics.PAUSE_RESUME);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void workerLoop_dispatch_bumpsInvokedCounter() throws Exception {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(10_000));
    EventMonitoringService monitoring = mock(EventMonitoringService.class);
    consumer.metricEmitter = new FlowGovernorMetricEmitter(monitoring, TOPIC);
    consumer.queue.offer(protoRecord(0));

    Thread worker = new Thread(consumer::workerLoop, "test-worker");
    worker.setDaemon(true);
    worker.start();
    awaitCondition(() -> consumer.queue.isEmpty(), 2_000);
    consumer.stopped.set(true);
    worker.join(3_000);

    verify(monitoring, atLeastOnce()).incCounter(FlowGovernorMetrics.INVOKED);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void workerLoop_residualDrainUnderHalt_stillBumpsInvokedCounter() throws Exception {
    // Residual records dispatched under HALT are real dispatches and must be counted — otherwise
    // the invoked-rate panel reads zero while the pod is still doing work, which is exactly when an
    // operator is watching it.
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(10_000));
    EventMonitoringService monitoring = mock(EventMonitoringService.class);
    consumer.metricEmitter = new FlowGovernorMetricEmitter(monitoring, TOPIC);
    consumer.queue.offer(protoRecord(0));
    consumer.currentMode.set(FlowGovernorState.Mode.HALTED);

    Thread worker = new Thread(consumer::workerLoop, "test-worker");
    worker.setDaemon(true);
    worker.start();
    awaitCondition(() -> consumer.queue.isEmpty(), 2_000);
    consumer.stopped.set(true);
    worker.join(3_000);

    verify(monitoring, atLeastOnce()).incCounter(FlowGovernorMetrics.INVOKED);
    verify(messageHandler, times(1)).onMessage(any(), anyMap(), anyMap());
  }

  // ---- ingestion backpressure: already-read records are never dropped -------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void offerRecord_queueFull_blocksAndPausesRatherThanDropping() throws Exception {
    // The other loss path: a record already read and already committed, arriving at a full queue.
    // Old behavior was to log a warning and drop it. It must now wait for a slot.
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(10_000));
    for (int i = 0; i < consumer.throttleConfig.getQueueCapacity(); i++) {
      consumer.queue.offer(protoRecord(i));
    }
    ConsumerRecord<String, InitiateNodeEvent> overflow = protoRecord(9_999);

    Thread ingestion = new Thread(() -> consumer.offerRecord(overflow), "test-ingestion");
    ingestion.setDaemon(true);
    ingestion.start();

    // Wait for the backpressure signal rather than assuming it lands inside a fixed sleep: the
    // first offer attempt alone costs offerTimeoutMs, so thread start plus that wait can outrun a
    // small budget on a contended box. Reaching this state is itself proof the offer did not return
    // early — pausedByWatermark is only set once a timed offer has already failed, and the old
    // drop-on-timeout code never set it at all.
    awaitCondition(() -> consumer.pausedByWatermark.get(), 10_000);
    assertThat(consumer.pausedByWatermark.get()).isTrue();
    // And it must have applied backpressure at the broker, not just spun. The flag is flipped by
    // CAS a few statements before pause() reaches the consumer, so this has to wait for the call
    // rather than assume it already happened.
    verify(kafkaConsumer, timeout(10_000).atLeastOnce()).pause(Collections.singleton(PARTITION));

    // Still parked on the full queue — the record is neither dropped nor enqueued.
    assertThat(ingestion.isAlive()).isTrue();
    assertThat(consumer.queue).doesNotContain(overflow);

    // Free exactly one slot; the overflow record must land rather than having been discarded.
    consumer.queue.poll();
    ingestion.join(5_000);
    assertThat(ingestion.isAlive()).isFalse();
    assertThat(consumer.queue).contains(overflow);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void offerRecord_zeroConfiguredOfferTimeout_parksInsteadOfHotSpinning() throws Exception {
    // offerTimeoutMs is operator-tunable, and BlockingQueue.offer(e, 0, unit) returns immediately.
    // Without the MIN_OFFER_WAIT_MS floor the retry loop would burn a core on the ingestion thread
    // for as long as the queue stays full. Measured in thread CPU time, since wall-clock behavior
    // is identical either way.
    ThrottledConsumerConfig zeroOfferTimeout = ThrottledConsumerConfig.defaults();
    zeroOfferTimeout.setOfferTimeoutMs(0L);
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer =
        build(new FlowGovernorConfig(true, 10_000, null, zeroOfferTimeout));
    fillQueue(consumer);

    ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    assumeTrue(threads.isThreadCpuTimeSupported());
    AtomicLong threadId = new AtomicLong();
    Thread ingestion = new Thread(() -> {
      threadId.set(Thread.currentThread().getId());
      consumer.offerRecord(protoRecord(9_999));
    }, "test-ingestion-spin");
    ingestion.setDaemon(true);
    ingestion.start();
    awaitCondition(() -> threadId.get() != 0, 2_000);

    Thread.sleep(500);
    long cpuNanos = threads.getThreadCpuTime(threadId.get());
    ingestion.interrupt();
    ingestion.join(2_000);

    // A hot spin over a 500ms wall wait consumes ~500ms of CPU; a parked thread consumes ~none.
    assertThat(TimeUnit.NANOSECONDS.toMillis(cpuNanos)).isLessThan(100L);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void offerRecord_logsThePauseOncePerEpisodeNotOncePerRecord() throws Exception {
    // A record arriving at an already-paused queue must not re-log the pause.
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(10_000));
    fillQueue(consumer);
    consumer.pausedByWatermark.set(true);

    Thread ingestion = new Thread(() -> consumer.offerRecord(protoRecord(9_999)), "test-ingestion-log");
    ingestion.setDaemon(true);
    ingestion.start();
    Thread.sleep(consumer.throttleConfig.getOfferTimeoutMs() * 3);
    ingestion.interrupt();
    ingestion.join(5_000);

    assertThat(loggerRule.getFormattedMessages().stream().filter(m -> m.contains("full after")).count()).isZero();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void offerRecord_queueWithRoom_enqueuesWithoutPausing() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(10_000));
    ConsumerRecord<String, InitiateNodeEvent> record = protoRecord(0);

    consumer.offerRecord(record);

    assertThat(consumer.queue).containsExactly(record);
    assertThat(consumer.pausedByWatermark.get()).isFalse();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void stop_drainsResidualQueueBeforeSignallingWorkersToExit() throws Exception {
    // A rolling deploy must not silently discard the buffer. Workers stay alive through the drain,
    // so everything already read reaches the handler.
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(10_000));
    when(kafkaConsumer.poll(any())).thenReturn(ConsumerRecords.empty());
    consumer.metricEmitter = new FlowGovernorMetricEmitter(mock(EventMonitoringService.class), TOPIC);
    for (int i = 0; i < 25; i++) {
      consumer.queue.offer(protoRecord(i));
    }
    for (int i = 0; i < consumer.throttleConfig.getWorkers(); i++) {
      consumer.workerPool.submit(consumer::workerLoop);
    }

    consumer.stop();

    assertThat(consumer.queue).isEmpty();
    verify(messageHandler, times(25)).onMessage(any(), anyMap(), anyMap());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shutdownDrain_returnsAsSoonAsTheQueueClears() throws Exception {
    // The budget is a give-up deadline, not a delay: the loop polls queue.isEmpty() and must return
    // the moment it clears. That is what makes a generous SHUTDOWN_DRAIN_TIMEOUT_MS free — a healthy
    // pod is never held open for it. If this ever regresses into a fixed sleep, every deploy pays 30s.
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(10_000));
    fillQueue(consumer);

    Thread drainer = new Thread(() -> {
      try {
        Thread.sleep(200);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      }
      consumer.queue.clear();
    });
    drainer.start();

    long startedAt = System.nanoTime();
    consumer.drainQueueBeforeShutdown();
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    drainer.join();

    assertThat(consumer.queue).isEmpty();
    assertThat(elapsedMs).isGreaterThanOrEqualTo(200L);
    assertThat(elapsedMs).isLessThan(ThrottledKafkaConsumer.SHUTDOWN_DRAIN_TIMEOUT_MS / 2);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shutdownDrain_emptyQueue_returnsImmediately() {
    ThrottledKafkaConsumer<InitiateNodeEvent> consumer = build(enabledConfig(10_000));

    long startedAt = System.nanoTime();
    consumer.drainQueueBeforeShutdown();

    // Nothing to drain means the loop body never runs — not even one 100ms poll tick.
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)).isLessThan(100L);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shutdownBudgets_fitInsideTheTerminationGracePeriodForBothConsumers() {
    // Provenance guard for the two constants. The chart allows 180s
    // (pipeline-service/chart/templates/deployment.yaml terminationGracePeriodSeconds). Dropwizard
    // stops Managed beans serially and there are two governed consumers, so the budget has to cover
    // both — asserting only one consumer's total would leave the real worst case unguarded at double
    // the figure. Overrun the grace period and the pod is SIGKILLed mid-drain, which loses the
    // already-committed buffer the drain exists to protect.
    long governedConsumers = 2L;
    long perConsumerMs = 30_000L // super.stop(): the base class's own consumerThread.awaitTermination
        + ThrottledKafkaConsumer.SHUTDOWN_DRAIN_TIMEOUT_MS + ThrottledKafkaConsumer.WORKER_TERMINATION_TIMEOUT_MS
        + 5_000L; // modePoller.awaitTermination

    assertThat(perConsumerMs * governedConsumers).isLessThan(180_000L);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void workerTerminationBudget_isSmallerThanTheDrainBudget() {
    // The drain runs first, so by the time we wait on workers the queue is empty and at most one
    // in-flight submit per worker remains. A worker still outstanding here is blocked on the same
    // saturated engine pool the drain already waited on, so it does not deserve equal patience.
    assertThat(ThrottledKafkaConsumer.WORKER_TERMINATION_TIMEOUT_MS)
        .isLessThan(ThrottledKafkaConsumer.SHUTDOWN_DRAIN_TIMEOUT_MS);
  }

  // ---- helpers -------------------------------------------------------------------------------

  /** Fills the bounded queue to capacity and returns the resulting depth. */
  private static int fillQueue(ThrottledKafkaConsumer<InitiateNodeEvent> consumer) {
    for (int i = 0; i < consumer.throttleConfig.getQueueCapacity(); i++) {
      consumer.queue.offer(protoRecord(i));
    }
    return consumer.queue.size();
  }

  private static ConsumerRecord<String, InitiateNodeEvent> protoRecord(long offset) {
    return new ConsumerRecord<>(TOPIC, PARTITION_NUM, offset, "k" + offset, InitiateNodeEvent.newBuilder().build());
  }

  private static void awaitCondition(java.util.function.BooleanSupplier condition, long timeoutMs) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }
}

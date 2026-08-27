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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.MessageHandler;
import io.harness.eventsframework.schemas.common.DeadLetter;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.config.KafkaConsumerConfig;
import io.harness.kafka.producers.HKafkaProducer;
import io.harness.monitoring.EventMonitoringService;
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.rule.Owner;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

/**
 * Scenario-level tests for the flow governor, driven by Kafka's own in-memory {@link MockConsumer}
 * rather than a Mockito mock. The distinction matters: a Mockito mock only records that
 * {@code pause()} was called, whereas {@code MockConsumer} actually honors pause in {@code poll()}
 * and fires the rebalance-listener callbacks from {@code rebalance()}. That lets these tests assert
 * observable behavior — "no records reached the handler" — instead of interaction transcripts,
 * which is what {@link ThrottledKafkaConsumerTest} already covers.
 *
 * <p>Scenarios covered (spec Phase 6):
 * <ol>
 *   <li>HALT survives a partition rebalance (Risk #1a).</li>
 *   <li>Mode propagation end-to-end: Redis map → store → Caffeine cache → rate limiter / pause.</li>
 *   <li>Poll liveness under sustained HALT — the ingestion loop keeps polling so the broker never
 *       evicts the member, while zero records are handed to the handler.</li>
 *   <li>THROTTLED throughput accuracy against the rate limiter's own contract.</li>
 * </ol>
 *
 * <p><b>Deliberate coverage gap.</b> {@code MockConsumer} has no consumer-group membership, no
 * heartbeat thread, and no {@code max.poll.interval.ms} timer. It therefore cannot prove that a
 * long HALT (past the 5-minute default poll interval) avoids a real broker-side eviction, nor that
 * a real cooperative rebalance mid-HALT behaves as scenario 1 asserts. Scenario 3 covers the
 * mechanism that makes eviction impossible (the loop keeps polling on a ~300ms cadence); the
 * broker-side confirmation is left to the pre-rollout soak rather than pulled into unit CI via
 * Testcontainers.
 */
@OwnedBy(PIPELINE)
public class ThrottledKafkaConsumerScenarioTest extends CategoryTest {
  private static final String CONSUMER_KEY = FlowGovernorConsumerKeys.INITIATE_NODE;
  private static final String TOPIC = "initiate_node_event_topic";
  private static final int FIRST = 0;
  private static final int SECOND = 1;
  private static final TopicPartition PARTITION_ONE = new TopicPartition(TOPIC, FIRST);
  private static final TopicPartition PARTITION_TWO = new TopicPartition(TOPIC, SECOND);
  private static final Executor DIRECT_EXECUTOR = Runnable::run;
  /** Kafka's own default {@code max.poll.interval.ms}; nothing in this repo overrides it. */
  private static final long MAX_POLL_INTERVAL_MS = 300_000L;

  @Mock private KafkaConsumerConfig<InitiateNodeEvent> consumerConfig;
  @Mock private HKafkaProducer<DeadLetter> dlqReporter;
  @Mock private ConsumerMaintenanceListener consumerMaintenanceListener;
  @Mock private FlowGovernorStateCache stateCache;

  /** Records handed to the message handler, i.e. what actually escaped the governor. */
  private final AtomicInteger handled = new AtomicInteger();
  private CountingConsumer kafkaConsumer;
  private ThrottledKafkaConsumer<InitiateNodeEvent> underTest;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    kafkaConsumer = new CountingConsumer();
    kafkaConsumer.updateBeginningOffsets(Map.of(PARTITION_ONE, 0L, PARTITION_TWO, 0L));

    MessageHandler<InitiateNodeEvent> countingHandler = (message, metadata, metricInfo) -> handled.incrementAndGet();
    when(consumerConfig.getTopic()).thenReturn(TOPIC);
    when(consumerConfig.getConsumerRecordFilters()).thenReturn(Collections.emptyList());
    when(consumerConfig.getConsumerMode()).thenReturn(KafkaConsumerConfig.ConsumerMode.UNORDERED);
    when(consumerConfig.getConsumerMaintenanceListener()).thenReturn(consumerMaintenanceListener);
    when(consumerConfig.getMessageHandler()).thenReturn(countingHandler);
    when(consumerConfig.isNoAck()).thenReturn(true);
    // Workers hand dispatch to the engine's shared executor. Direct execution keeps the handler on
    // the worker thread, so `handled` still measures the rate the workers paced records out at.
    when(consumerConfig.getExecutorService()).thenReturn(MoreExecutors.newDirectExecutorService());
  }

  @After
  public void tearDown() {
    if (underTest == null) {
      return;
    }
    // Tests that spin up the real worker pool must not leak threads into sibling tests.
    underTest.stopped.set(true);
    if (underTest.workerPool != null) {
      underTest.workerPool.shutdownNow();
    }
    if (underTest.modePoller != null) {
      underTest.modePoller.shutdownNow();
    }
  }

  // ---- Scenario 1: HALT survives a rebalance (Risk #1a) ---------------------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void haltAcrossRebalance_newlyAssignedPartitionsComeBackPausedAndStayDry() throws Exception {
    underTest = build(enabledConfig(10_000), stateCache);
    subscribeWithGovernorListener();
    kafkaConsumer.rebalance(List.of(PARTITION_ONE));

    // Operator halts; the ingestion loop applies the pause on its next iteration.
    underTest.pausedByHalt.set(true);
    underTest.runInternal();
    assertThat(kafkaConsumer.paused()).containsExactly(PARTITION_ONE);

    // A partition is added to this member while it is halted. Kafka's pause is per-partition and
    // is NOT carried over to a freshly assigned partition, so without HaltAwareRebalanceListener
    // the consumer would silently start fetching PARTITION_TWO again.
    kafkaConsumer.rebalance(List.of(PARTITION_ONE, PARTITION_TWO));

    assertThat(kafkaConsumer.paused()).containsExactlyInAnyOrder(PARTITION_ONE, PARTITION_TWO);

    // Behavioral proof, not just the flag: records on the newly assigned partition must not be
    // fetched. rebalance() clears MockConsumer's buffer, so these are added afterwards.
    kafkaConsumer.addRecord(recordOn(PARTITION_TWO, 0));
    kafkaConsumer.addRecord(recordOn(PARTITION_TWO, 1));

    underTest.runInternal();

    assertThat(underTest.queue).isEmpty();
    assertThat(handled.get()).isZero();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void rebalanceWhileNotHalted_leavesNewPartitionsFetchable() throws Exception {
    // Negative control for the test above: the re-pause must be conditional on the halt state, or
    // a rebalance during normal operation would wedge the consumer.
    underTest = build(enabledConfig(10_000), stateCache);
    subscribeWithGovernorListener();
    kafkaConsumer.rebalance(List.of(PARTITION_ONE));

    kafkaConsumer.rebalance(List.of(PARTITION_ONE, PARTITION_TWO));
    kafkaConsumer.addRecord(recordOn(PARTITION_TWO, 0));

    assertThat(kafkaConsumer.paused()).isEmpty();

    underTest.runInternal();

    assertThat(underTest.queue).hasSize(1);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void watermarkPauseAcrossRebalance_alsoSurvives() throws Exception {
    // shouldRepauseOnAssign() covers watermark back-pressure too, not just HALT. If it didn't, a
    // rebalance would re-open the firehose into an already-full queue.
    underTest = build(enabledConfig(10_000), stateCache);
    subscribeWithGovernorListener();
    kafkaConsumer.rebalance(List.of(PARTITION_ONE));
    underTest.pausedByWatermark.set(true);
    underTest.runInternal();

    kafkaConsumer.rebalance(List.of(PARTITION_ONE, PARTITION_TWO));

    assertThat(kafkaConsumer.paused()).containsExactlyInAnyOrder(PARTITION_ONE, PARTITION_TWO);
  }

  // ---- Scenario 2: mode propagation, Redis → store → cache → consumer ------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void haltWrittenToRedis_propagatesThroughRealStoreAndCache_andStopsDelivery() throws Exception {
    // Only the Redisson RMap is mocked. FlowGovernorStateStore, FlowGovernorStateCache, pollMode(),
    // and the Kafka pause are all the real thing.
    RMap<String, FlowGovernorState> redisMap =
        redisMapReturning(FlowGovernorState.builder().mode(FlowGovernorState.Mode.HALTED).version(4L).build());
    underTest = build(enabledConfig(10_000), realCacheOver(redisMap));
    subscribeWithGovernorListener();
    kafkaConsumer.rebalance(List.of(PARTITION_ONE));
    kafkaConsumer.addRecord(recordOn(PARTITION_ONE, 0));

    underTest.pollMode();

    assertThat(underTest.currentMode.get()).isEqualTo(FlowGovernorState.Mode.HALTED);
    assertThat(underTest.pausedByHalt.get()).isTrue();

    underTest.runInternal();

    assertThat(kafkaConsumer.paused()).containsExactly(PARTITION_ONE);
    assertThat(handled.get()).isZero();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void throttledRpsWrittenToRedis_propagatesToRateLimiter() {
    // Per-consumer override in the Redis payload must beat the blanket targetRps by the time it
    // reaches the limiter — the whole point of the per-consumer dial.
    RMap<String, FlowGovernorState> redisMap =
        redisMapReturning(FlowGovernorState.builder()
                              .mode(FlowGovernorState.Mode.THROTTLED)
                              .targetRps(50)
                              .targetRpsByConsumer(Map.of(FlowGovernorConsumerKeys.INITIATE_NODE, 6))
                              .version(5L)
                              .build());
    underTest = build(enabledConfig(10_000), realCacheOver(redisMap));

    underTest.pollMode();

    assertThat(underTest.currentMode.get()).isEqualTo(FlowGovernorState.Mode.THROTTLED);
    assertThat(underTest.rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(6);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void modeChangeInRedis_isNotVisibleUntilCacheRefreshWindowElapses() {
    // Pins the documented detection lag: the Caffeine cache only refreshes every
    // REFRESH_INTERVAL (30s), so back-to-back mode polls inside that window read one Redis value.
    // Worst-case operator-visible lag is therefore refresh interval + mode poll interval, and an
    // operator issuing HALT must not expect it to take effect within the same second.
    RMap<String, FlowGovernorState> redisMap = mock(RMap.class);
    when(redisMap.get(FlowGovernorStateStore.STATE_KEY))
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.NORMAL).version(1L).build())
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.HALTED).version(2L).build());
    underTest = build(enabledConfig(10_000), realCacheOver(redisMap));

    underTest.pollMode();
    underTest.pollMode();

    assertThat(underTest.currentMode.get()).isEqualTo(FlowGovernorState.Mode.NORMAL);
    assertThat(underTest.pausedByHalt.get()).isFalse();
    verify(redisMap, times(1)).get(FlowGovernorStateStore.STATE_KEY);
    assertThat(FlowGovernorStateCache.REFRESH_INTERVAL.toSeconds()).isEqualTo(30L);
  }

  // ---- Scenario 3: poll liveness under sustained HALT ----------------------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void sustainedHalt_keepsPollingWellInsideMaxPollInterval_andDeliversNothing() throws Exception {
    // The hazard this guards: pausing a Kafka consumer does NOT stop the max.poll.interval.ms
    // timer. A HALT implemented by simply not polling would get the member evicted from the group
    // after 5 minutes and trigger a rebalance storm. The governor instead keeps polling a paused
    // assignment, which returns empty batches.
    underTest = build(enabledConfig(10_000), stateCache);
    subscribeWithGovernorListener();
    kafkaConsumer.rebalance(List.of(PARTITION_ONE));
    for (int i = 0; i < 25; i++) {
      kafkaConsumer.addRecord(recordOn(PARTITION_ONE, i));
    }
    underTest.pausedByHalt.set(true);
    underTest.currentMode.set(FlowGovernorState.Mode.HALTED);

    int iterations = 5;
    long startNanos = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      underTest.runInternal();
    }
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

    assertThat(kafkaConsumer.pollCount()).isEqualTo(iterations);
    // Worst observed gap between two polls, upper-bounded by total elapsed / iterations. The
    // 300ms idle sleep dominates, leaving three orders of magnitude of headroom.
    long worstCaseGapMs = elapsedMs / iterations;
    assertThat(worstCaseGapMs).isLessThan(MAX_POLL_INTERVAL_MS / 10);
    // Records were available on the partition the whole time and none of them moved.
    assertThat(underTest.queue).isEmpty();
    assertThat(handled.get()).isZero();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void haltThenResume_resumesDeliveryOfBufferedRecords() throws Exception {
    // The other half of the liveness contract: nothing about the halt path leaves the assignment
    // permanently paused. Records buffered broker-side during HALT flow once the mode clears.
    underTest = build(enabledConfig(10_000), stateCache);
    subscribeWithGovernorListener();
    kafkaConsumer.rebalance(List.of(PARTITION_ONE));
    kafkaConsumer.addRecord(recordOn(PARTITION_ONE, 0));
    when(stateCache.getState())
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.HALTED).version(1L).build())
        .thenReturn(FlowGovernorState.builder().mode(FlowGovernorState.Mode.NORMAL).version(2L).build());

    underTest.pollMode();
    underTest.runInternal();
    assertThat(kafkaConsumer.paused()).containsExactly(PARTITION_ONE);
    assertThat(underTest.queue).isEmpty();

    underTest.pollMode();
    underTest.runInternal();

    assertThat(kafkaConsumer.paused()).isEmpty();
    assertThat(underTest.queue).hasSize(1);
  }

  // ---- Scenario 4: THROTTLED throughput accuracy ---------------------------------------------

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void throttledMode_dispatchRateStaysWithinRateLimiterContract() throws Exception {
    int targetRps = 10;
    // Built at targetRps so the limiter's permit budget is already correct. Building high and then
    // calling changeLimitForPeriod() would let the already-granted permits drain as a burst before
    // the next refresh cycle, which measures the transition rather than the steady state.
    underTest = build(enabledConfig(targetRps), stateCache);
    EventMonitoringService monitoring = mock(EventMonitoringService.class);
    underTest.metricEmitter = new FlowGovernorMetricEmitter(monitoring, TOPIC);
    when(stateCache.getState())
        .thenReturn(FlowGovernorState.builder()
                        .mode(FlowGovernorState.Mode.THROTTLED)
                        .targetRps(targetRps)
                        .version(1L)
                        .build());
    // Supply is pre-loaded rather than polled so the measurement isn't confounded by watermark
    // pause/resume; queue capacity (200) is far more than a 10 rps limiter can drain in 2s.
    int supplied = underTest.throttleConfig.getQueueCapacity();
    for (int i = 0; i < supplied; i++) {
      underTest.queue.offer(protoRecord(i));
    }

    underTest.pollMode();
    assertThat(underTest.rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(targetRps);

    long startNanos = System.nanoTime();
    for (int i = 0; i < underTest.throttleConfig.getWorkers(); i++) {
      underTest.workerPool.submit(underTest::workerLoop);
    }
    Thread.sleep(2_000);
    underTest.stopped.set(true);
    double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
    underTest.pollMode();

    int dispatched = handled.get();
    // Upper bound is the limiter's own contract rather than a hand-tuned number: at most one
    // limitForPeriod budget per refresh period, plus one budget for the window already open when
    // the workers started. Wide enough to survive a loaded CI box, tight enough that removing the
    // limiter (200 dispatches in ~0ms) fails it.
    long permitCeiling = (long) targetRps * (long) (Math.ceil(elapsedSeconds) + 1);
    assertThat(dispatched).isLessThanOrEqualTo((int) permitCeiling);
    assertThat(dispatched).isLessThan(supplied);
    // And the throttle must not be so aggressive it stalls: one budget should always get through.
    assertThat(dispatched).isGreaterThanOrEqualTo(targetRps);

    // The rps_actual gauge is derived from the same dispatches, so it must land in the same band.
    ArgumentCaptor<Long> actualRps = ArgumentCaptor.forClass(Long.class);
    verify(monitoring, atLeastOnce()).sendMetric(eq(FlowGovernorMetrics.RPS_ACTUAL), actualRps.capture());
    assertThat(actualRps.getAllValues().get(actualRps.getAllValues().size() - 1)).isLessThanOrEqualTo(permitCeiling);
    verify(monitoring, atLeastOnce()).sendMetric(FlowGovernorMetrics.RPS_EXPECTED, (long) targetRps);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void haltFlipMidThrottle_conservesEveryRecord_andEventuallyDrainsThemAll() throws Exception {
    // The scenario that drove the no-loss requirement: throttled hard so the queue is deep and ~all
    // workers are parked on the permit wait holding a dequeued record, then HALT. Every record is
    // already committed and dedup-marked, so none may vanish — and because the buffer only lives in
    // this pod's heap, "retained" is not good enough either. All 30 must reach the handler.
    int supplied = 30;
    underTest = build(enabledConfig(1), stateCache);
    for (int i = 0; i < supplied; i++) {
      underTest.queue.offer(protoRecord(i));
    }
    for (int i = 0; i < underTest.throttleConfig.getWorkers(); i++) {
      underTest.workerPool.submit(underTest::workerLoop);
    }
    Thread.sleep(300);

    underTest.currentMode.set(FlowGovernorState.Mode.HALTED);
    Thread.sleep(1_000);

    // Mid-flight the count splits three ways: handled, still queued, and held in a worker frame
    // parked on the permit wait (at most one per worker). The invariant is that nothing has
    // vanished from those three buckets — i.e. handled + queued is never short by more than the
    // number of workers that can each be holding one record.
    int inWorkerFrames = supplied - handled.get() - underTest.queue.size();
    assertThat(inWorkerFrames).isBetween(0, underTest.throttleConfig.getWorkers());
    // The drain is still rate-limited — HALT is not a way to bypass THROTTLE.
    assertThat(handled.get()).isLessThan(supplied);

    // Now the assertion that matters: raise the limit the way a resume would and every record —
    // queued or held in a frame — must reach the handler. The mode stays HALTED throughout: intake
    // is stopped, but what we already took is still processed. Under the old drop-on-HALT behavior
    // the ~20 frame-held records were discarded and this never reaches `supplied`.
    underTest.rateLimiter.changeLimitForPeriod(10_000);
    awaitCondition(() -> handled.get() == supplied, 10_000);

    assertThat(underTest.currentMode.get()).isEqualTo(FlowGovernorState.Mode.HALTED);
    assertThat(handled.get()).isEqualTo(supplied);
    assertThat(underTest.queue).isEmpty();
  }

  // ---- helpers -------------------------------------------------------------------------------

  /**
   * Mirrors the single wiring line in {@code HKafkaConsumer.instantiateConsumer()} so the real
   * {@code HaltAwareRebalanceListener} produced by {@code createRebalanceListener()} is the one
   * MockConsumer invokes on rebalance.
   */
  private void subscribeWithGovernorListener() {
    kafkaConsumer.subscribe(Collections.singletonList(TOPIC), underTest.createRebalanceListener());
  }

  private ThrottledKafkaConsumer<InitiateNodeEvent> build(
      FlowGovernorConfig config, @Nullable FlowGovernorStateCache cache) {
    return new ThrottledKafkaConsumer<>(
        consumerConfig, kafkaConsumer, dlqReporter, new Properties(), CONSUMER_KEY, config, cache);
  }

  private static FlowGovernorConfig enabledConfig(int normalRps) {
    return new FlowGovernorConfig(true, normalRps, null, ThrottledConsumerConfig.defaults());
  }

  @SuppressWarnings("unchecked")
  private static RMap<String, FlowGovernorState> redisMapReturning(FlowGovernorState state) {
    RMap<String, FlowGovernorState> map = mock(RMap.class);
    when(map.get(FlowGovernorStateStore.STATE_KEY)).thenReturn(state);
    return map;
  }

  private static FlowGovernorStateCache realCacheOver(RMap<String, FlowGovernorState> redisMap) {
    RedissonClient client = mock(RedissonClient.class);
    when(client.<String, FlowGovernorState>getMap(FlowGovernorStateStore.MAP_NAME)).thenReturn(redisMap);
    return new FlowGovernorStateCache(new FlowGovernorStateStore(client), DIRECT_EXECUTOR);
  }

  private static ConsumerRecord<String, InitiateNodeEvent> recordOn(TopicPartition partition, long offset) {
    return new ConsumerRecord<>(
        partition.topic(), partition.partition(), offset, "k" + offset, InitiateNodeEvent.newBuilder().build());
  }

  private static ConsumerRecord<String, InitiateNodeEvent> protoRecord(long offset) {
    return recordOn(PARTITION_ONE, offset);
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

  /**
   * {@link MockConsumer} plus a poll counter. Needed because the poll-liveness scenario asserts on
   * poll <i>cadence</i>, which MockConsumer doesn't expose.
   */
  private static final class CountingConsumer extends MockConsumer<String, InitiateNodeEvent> {
    private final AtomicLong polls = new AtomicLong();

    private CountingConsumer() {
      super("earliest");
    }

    @Override
    public synchronized ConsumerRecords<String, InitiateNodeEvent> poll(java.time.Duration timeout) {
      polls.incrementAndGet();
      return super.poll(timeout);
    }

    private long pollCount() {
      return polls.get();
    }
  }
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.execution.consumers.flowgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.common.DeadLetter;
import io.harness.kafka.common.HKafkaUtils;
import io.harness.kafka.config.KafkaConsumerConfig;
import io.harness.kafka.consumers.HKafkaProtoConsumer;
import io.harness.kafka.consumers.HaltAwareRebalanceListener;
import io.harness.kafka.logging.KafkaRecordLogContext;
import io.harness.kafka.producers.HKafkaProducer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.Message;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * Flow-governor-aware base class for the nine orchestration Kafka consumers. Extends
 * {@link HKafkaProtoConsumer} directly so each consumer class stays a single file.
 *
 * <p><b>Config off (default)</b>: {@link FlowGovernorConfig#isEnabled()} is false ⇒ no queue,
 * worker pool, mode poller, or rate limiter is allocated. {@link #runInternal()}, {@link #start()},
 * and {@link #stop()} delegate straight to the superclass. Behavior is bit-identical to
 * {@code HKafkaProtoConsumer} today.
 *
 * <p><b>Config on</b>: implements spec §5.1 — a lightweight ingestion thread that only polls Kafka
 * and offers records to a bounded queue, plus a fixed-size worker pool that drains the queue
 * through a Resilience4j rate limiter.
 *
 * <p><b>Workers are pacers, not handler threads.</b> A worker dequeues, takes a permit, then submits
 * {@link #dispatch} to {@code consumerConfig.getExecutorService()} — the same shared engine pool
 * vanilla {@code runNoAck} submits to. Handler concurrency is therefore whatever that pool allows
 * ({@code orchestrationPoolConfig}: 20 core / 100 max / 500 queue, scaling), not our worker count,
 * so enabling the governor does not narrow handler concurrency the way a dedicated handler pool
 * would. {@code workers} sizes only the submit stage; see {@link ThrottledConsumerConfig#getWorkers}.
 *
 * <p><b>HALT semantics — stop intake, drain what we already took</b>: on HALT the ingestion thread
 * pauses all assigned partitions on its next iteration, so no <i>new</i> records are pulled. HALT
 * does <b>not</b> gate the workers: records already sitting in the queue, and records already read
 * in the in-flight poll batch, are still dispatched through the message handler. On the
 * HALT→NORMAL/THROTTLED transition the ingestion thread resumes the assignment and intake picks
 * back up.
 *
 * <p>This is a deliberate choice forced by the ack timing, which is at-most-once (batch commit
 * before enqueue, matching vanilla {@code runNoAck}). Once a record is in the queue its offset is
 * already committed, so Kafka will never redeliver it, and the ingestion-side dedup filter
 * ({@code consumerRecordFilters}) has already marked its offset as seen for its cache TTL — even a
 * manual offset rewind would not replay it. Discarding such a record is permanent data loss, and
 * merely <i>holding</i> it is only a deferral: the queue is in-memory, so a pod restart or rolling
 * deploy mid-HALT would lose the whole buffer. Draining is the only option that actually processes
 * those records.
 *
 * <p>The cost is that HALT is not instantaneous on the processing side: it takes effect after at
 * most {@code queueCapacity} + one poll batch of residual records, drained at the rate limit in
 * force when HALT was applied. From NORMAL that is milliseconds. From a deep THROTTLE it is
 * {@code residual / rps} seconds, which an operator who chose that rps has implicitly accepted —
 * draining faster than the configured rps would defeat the downstream protection THROTTLE exists
 * for. Intake, which is what a flood incident actually needs stopped, halts immediately.
 *
 * <p><b>Rebalance safety</b>: {@link HaltAwareRebalanceListener} re-applies pause on newly
 * assigned partitions when {@link #shouldRepauseOnAssign()} is true. Wired via the
 * {@link #createRebalanceListener()} extension point on the superclass.
 */
@OwnedBy(PIPELINE)
@Slf4j
public class ThrottledKafkaConsumer<T extends Message> extends HKafkaProtoConsumer<T> {
  /**
   * Fallback THROTTLED RPS when Redis has no rps set. Deliberately low so a mis-configured
   * THROTTLE mode still throttles instead of silently reverting to the NORMAL ceiling.
   */
  static final int SAFETY_THROTTLE_FALLBACK_RPS = 5;

  /**
   * How long {@link #stop()} will keep waiting for the residual queue to drain before giving up.
   *
   * <p>This is a give-up deadline, not a delay. {@link #drainQueueBeforeShutdown()} polls
   * {@code queue.isEmpty()} and returns the moment the queue clears, so a healthy shutdown costs one
   * 100ms tick regardless of what this is set to. It is only ever spent when the drain is stuck,
   * which means one thing: the engine pool is saturated and workers are parked in
   * {@code ForceQueuePolicy}'s blocking {@code put}.
   *
   * <p>Fixed rather than derived from {@code queueDepth / rps}, because the rate limiter is not what
   * bounds the drain — {@link #stop()} lifts it before draining, so it never binds. The real bound is
   * engine-pool availability, which is not something we can estimate: it depends on how fast handlers
   * already in the pool complete. A rate-based estimate would read ~6s for every realistic config,
   * which is far too tight for the saturated case that is the only one it would ever apply to.
   *
   * <p>Sized against the deploy budget: {@code terminationGracePeriodSeconds} is 180s in
   * {@code pipeline-service/chart/templates/deployment.yaml}, Dropwizard stops {@code Managed} beans
   * serially, and there are two governed consumers. Per consumer, shutdown spends up to 30s in
   * {@code super.stop()} (the base class's own {@code consumerThread.awaitTermination}), this 30s,
   * {@link #WORKER_TERMINATION_TIMEOUT_MS}, and 5s on the mode poller — 75s each, 150s for both,
   * inside 180s with margin for the components shutting down alongside us.
   */
  static final long SHUTDOWN_DRAIN_TIMEOUT_MS = 30_000L;

  /**
   * How long {@link #stop()} will wait for workers to exit before {@code shutdownNow()} interrupts
   * them.
   *
   * <p>Also a give-up deadline: {@code workerPool.awaitTermination} returns as soon as the last
   * worker exits. That signal is exact — workers check {@code stopped} only between records, so a
   * worker exits only after its current {@code executorService.execute} has returned, meaning
   * termination implies every record any worker was holding reached the engine pool.
   *
   * <p>Smaller than the drain budget because the drain has already run by this point: the queue is
   * empty and what remains is at most one in-flight submit per worker. Anything still outstanding is
   * a worker blocked on the same saturated engine pool the drain already waited on, and more waiting
   * will not fix it.
   */
  static final long WORKER_TERMINATION_TIMEOUT_MS = 10_000L;

  /**
   * Floor on the per-attempt wait in {@link #offerRecord}. {@code offerTimeoutMs} is
   * operator-tunable, and a configured {@code 0} makes the timed {@code offer} return immediately —
   * which would turn the retry loop into a hot spin on the ingestion thread instead of a park.
   */
  static final long MIN_OFFER_WAIT_MS = 10L;

  /**
   * How often {@link #offerRecord} re-logs while backpressure has not cleared. Bounds the log
   * volume (one line per 10s per stalled record) while ensuring a permanently wedged queue is
   * visible rather than showing up only as a Kafka consumer-group eviction 300s later.
   */
  static final long BACKPRESSURE_STALL_LOG_INTERVAL_MS = 10_000L;

  private final String consumerKey;
  // Package-private so unit tests in the same package can assert the governor-off short-circuit
  // without reflection. Not part of the public API.
  final boolean governorEnabled;

  // The next block of fields is only populated when governorEnabled=true. When disabled, all
  // remain null and runInternal() delegates to super so this class behaves like a plain
  // HKafkaProtoConsumer. Visibility is package-private so tests can assert the disabled-path
  // "no allocations" contract.
  //
  // Not final because both the production and @VisibleForTesting constructors need to run a
  // shared initGovernorState() after super(...) — Java forbids chaining ctors when the two
  // supers differ, so a helper method is the least-friction way to keep the allocation logic
  // in one place. Safe: these fields are only written in the ctor and only read from threads
  // that start in start(), which happens strictly after the ctor returns.
  @Nullable ThrottledConsumerConfig throttleConfig;
  @Nullable private FlowGovernorConfig governorConfig;
  @Nullable private FlowGovernorStateCache stateCache;
  @Nullable BlockingQueue<ConsumerRecord<String, T>> queue;
  @Nullable ExecutorService workerPool;
  @Nullable ScheduledExecutorService modePoller;
  @Nullable RateLimiter rateLimiter;
  @Nullable private ScheduledFuture<?> modePollerHandle;
  /**
   * Bound in {@link #start()} once Guice has populated {@code @Inject EventMonitoringService} on
   * the parent. Nullable so pre-start test paths that invoke {@code runInternal}/{@code pollMode}
   * directly still work without a monitoring service.
   */
  @Nullable FlowGovernorMetricEmitter metricEmitter;

  final AtomicReference<FlowGovernorState.Mode> currentMode = new AtomicReference<>(FlowGovernorState.Mode.NORMAL);
  final AtomicBoolean pausedByHalt = new AtomicBoolean(false);
  final AtomicBoolean pausedByWatermark = new AtomicBoolean(false);
  /**
   * Set by the mode poller when a HALT→NORMAL/THROTTLED transition is detected. Consumed by
   * the ingestion thread on its next iteration to resume the assignment. Guards against the
   * mode poller thread calling resume while the ingestion thread is mid-poll.
   */
  final AtomicBoolean resumeRequested = new AtomicBoolean(false);
  private final AtomicBoolean workersStarted = new AtomicBoolean(false);
  final AtomicBoolean stopped = new AtomicBoolean(false);

  public ThrottledKafkaConsumer(KafkaConsumerConfig<T> consumerConfig, HKafkaProducer<DeadLetter> dlqReporter,
      String consumerKey, FlowGovernorConfig governorConfig, @Nullable FlowGovernorStateCache stateCache) {
    super(consumerConfig, dlqReporter);
    this.consumerKey = Objects.requireNonNull(consumerKey);
    this.governorEnabled = initGovernorState(consumerConfig, governorConfig, stateCache);
  }

  /**
   * Test-only constructor: accepts a pre-built {@link Consumer} so unit tests don't have to bring
   * up a real Kafka client. Bypasses the {@code KafkaBaseConfig} → {@code Properties} construction
   * inside {@link HKafkaProtoConsumer} that requires a fully populated base config.
   */
  @VisibleForTesting
  ThrottledKafkaConsumer(KafkaConsumerConfig<T> consumerConfig, Consumer<String, T> consumer,
      HKafkaProducer<DeadLetter> dlqReporter, Properties properties, String consumerKey,
      FlowGovernorConfig governorConfig, @Nullable FlowGovernorStateCache stateCache) {
    super(consumerConfig, consumer, dlqReporter, properties);
    this.consumerKey = Objects.requireNonNull(consumerKey);
    this.governorEnabled = initGovernorState(consumerConfig, governorConfig, stateCache);
  }

  /**
   * Assigns the governor-state fields in one place so both constructors share the same fail-fast
   * checks and allocation logic. Returns the {@code governorEnabled} flag so the caller can assign
   * it to its {@code final} field.
   */
  private boolean initGovernorState(KafkaConsumerConfig<T> consumerConfig, @Nullable FlowGovernorConfig governorConfig,
      @Nullable FlowGovernorStateCache stateCache) {
    // Governor disabled ⇒ act as a plain HKafkaProtoConsumer. No allocations. stateCache may be
    // null on the SDK-only path (non-pipeline services); we don't dereference it here.
    if (governorConfig == null || !governorConfig.isEnabled()) {
      return false;
    }

    // Fail fast on config combinations the throttled path doesn't implement. The nine wired
    // orchestration consumers all use UNORDERED + isNoAck=true; anything else would need
    // additional work to preserve semantics (ordered per-partition ack, batch-durability, etc.)
    if (KafkaConsumerConfig.ConsumerMode.ORDERED.equals(consumerConfig.getConsumerMode())) {
      throw new UnsupportedOperationException(
          "ThrottledKafkaConsumer does not support ORDERED consumer mode (consumer key: " + consumerKey + ").");
    }
    if (!consumerConfig.isNoAck()) {
      throw new UnsupportedOperationException(
          "ThrottledKafkaConsumer requires isNoAck=true (consumer key: " + consumerKey + ").");
    }

    this.governorConfig = governorConfig;
    this.stateCache = Objects.requireNonNull(stateCache);
    this.throttleConfig =
        Objects.requireNonNullElseGet(governorConfig.getThrottledConsumerConfig(), ThrottledConsumerConfig::defaults);

    this.queue = new ArrayBlockingQueue<>(throttleConfig.getQueueCapacity());
    this.workerPool = Executors.newFixedThreadPool(throttleConfig.getWorkers(),
        new ThreadFactoryBuilder()
            .setNameFormat("flow-governor-worker-" + consumerKey + "-%d")
            .setDaemon(true)
            .build());
    this.modePoller =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                                                       .setNameFormat("flow-governor-mode-poll-" + consumerKey + "-%d")
                                                       .setDaemon(true)
                                                       .build());
    this.rateLimiter = buildRateLimiter(consumerKey, governorConfig, throttleConfig);
    return true;
  }

  private static RateLimiter buildRateLimiter(
      String consumerKey, FlowGovernorConfig governorConfig, ThrottledConsumerConfig throttleConfig) {
    int initialRps = governorConfig.resolveNormalRpsFor(consumerKey);
    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofMillis(throttleConfig.getRateLimiterRefreshPeriodMs()))
            .limitForPeriod(Math.max(1, initialRps))
            .timeoutDuration(Duration.ofMillis(throttleConfig.getPermitAcquireTimeoutMs()))
            .build();
    return RateLimiter.of("flow-governor-" + consumerKey, config);
  }

  @Override
  public void start() throws Exception {
    // Start the Kafka poll loop first; if it fails we haven't yet spawned workers or the mode
    // poller so nothing to unwind.
    super.start();
    if (governorEnabled && workersStarted.compareAndSet(false, true)) {
      // Guice has now populated the parent's @Inject EventMonitoringService — safe to wire the
      // metric emitter. If monitoring isn't bound (e.g. SDK-only path), the emitter no-ops.
      this.metricEmitter = new FlowGovernorMetricEmitter(getEventMonitoringService(), topic);
      for (int i = 0; i < throttleConfig.getWorkers(); i++) {
        workerPool.submit(this::workerLoop);
      }
      modePollerHandle = modePoller.scheduleWithFixedDelay(this::pollMode, throttleConfig.getModePollIntervalMs(),
          throttleConfig.getModePollIntervalMs(), TimeUnit.MILLISECONDS);
    }
  }

  /**
   * Shutdown is ordered intake-first, then drain, then workers. Stopping intake before draining is
   * what makes the drain converge — otherwise the ingestion thread keeps committing and enqueueing
   * new records for as long as we wait. Workers are only told to exit ({@code stopped}) after the
   * drain, because they abandon the queue the instant that flag is set and every queued record's
   * offset is already committed.
   */
  @Override
  public void stop() throws Exception {
    if (!governorEnabled) {
      stopped.set(true);
      super.stop();
      return;
    }

    if (modePollerHandle != null) {
      modePollerHandle.cancel(false);
    }
    modePoller.shutdown();

    // Stops the poll loop, wakes the ingestion thread and closes the Kafka consumer. No new
    // records can enter the queue after this returns.
    super.stop();

    // Residual is already committed and bounded by queueCapacity, so meter it at NORMAL rather
    // than the throttled rps: a deploy mid-THROTTLE must not abandon the buffer. Safe because
    // workers only hand off from here, and the engine pool's own queue still applies backpressure.
    rateLimiter.changeLimitForPeriod(Math.max(1, governorConfig.resolveNormalRpsFor(consumerKey)));

    drainQueueBeforeShutdown();

    stopped.set(true);
    workerPool.shutdown();
    try {
      if (!workerPool.awaitTermination(WORKER_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        log.warn("Flow-governor workers for [{}] did not finish in {}ms; forcing shutdown. Any record still held in"
                + " a worker frame will be reported as lost.",
            consumerKey, WORKER_TERMINATION_TIMEOUT_MS);
        workerPool.shutdownNow();
      }
      modePoller.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      workerPool.shutdownNow();
      modePoller.shutdownNow();
    }
    reportAbandonedQueue();
  }

  /**
   * Wait for the workers to finish the records they have already accepted, before signalling them
   * to exit. Every queued record's offset is committed, so anything still queued when the pod dies
   * is unrecoverable; spending the shutdown grace period draining is strictly better than dropping.
   *
   * <p>Whatever is left after the deadline is reported per-record by
   * {@link #reportAbandonedQueue()}.
   */
  @VisibleForTesting
  void drainQueueBeforeShutdown() {
    int outstanding = queue.size();
    if (outstanding > 0) {
      log.info("Draining {} already-committed records for [{}] before shutdown; budget {}ms.", outstanding, consumerKey,
          SHUTDOWN_DRAIN_TIMEOUT_MS);
    }
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_DRAIN_TIMEOUT_MS);
    while (!queue.isEmpty() && System.nanoTime() < deadline) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    if (!queue.isEmpty()) {
      log.warn("Flow-governor queue for [{}] still holds {} records after {}ms shutdown drain.", consumerKey,
          queue.size(), SHUTDOWN_DRAIN_TIMEOUT_MS);
    }
  }

  /**
   * Last-resort accounting for records that were still sitting in the queue when we gave up — i.e.
   * ones no worker ever dequeued, because {@link #drainQueueBeforeShutdown()} ran out of budget.
   * They are already committed, so no other pod will pick them up; logging each one's identifiers is
   * the only way the loss can be reconciled.
   *
   * <p>Must run <em>after</em> the workers have terminated, which is why it is the last statement in
   * {@link #stop()}. Called any earlier, workers would still be racing us to {@code poll()} and we
   * would report records as lost that in fact got dispatched. Records a worker had already dequeued
   * are not our concern here — the worker frame owns them, and reports them itself if it is
   * interrupted while waiting for a permit.
   *
   * <p><b>Why we log instead of asking Kafka to redeliver.</b> Rewinding is not available to us on
   * three counts. {@code acknowledgeSync()} commits the whole polled batch with a bare
   * {@code commitSync()}, so there is no per-record offset bookkeeping to rewind <i>to</i>, and the
   * abandoned set is not contiguous (workers dequeue concurrently, so 5 and 7 can be drained while 6
   * is abandoned) — seeking to the lowest abandoned offset would redeliver already-processed
   * records. The ingestion-side dedup filter has also already claimed each offset in Redis for its
   * cache TTL, so a replay inside that window is discarded as a duplicate; that filter exists
   * precisely because the handlers are not idempotent, so it cannot simply be bypassed. And by the
   * time we get here {@code super.stop()} has closed the consumer, so no seek is even possible.
   *
   * <p>The route that would work is the DLQ: republishing to {@code DLQ-<topic>} yields a fresh
   * offset, sidestepping both the batch-commit problem and the dedup filter, and survives the pod's
   * death rather than dying with this in-memory queue. The base class already carries
   * {@code dlqReporter} and a {@code sendToDLQ} path; it is unwired here — {@code
   * InitiateNodeEventKafkaConsumer} passes {@code null} — so this reporting is the best available
   * accounting today.
   */
  private void reportAbandonedQueue() {
    ConsumerRecord<String, T> abandoned;
    while ((abandoned = queue.poll()) != null) {
      reportLost("abandoned on shutdown", abandoned);
    }
  }

  /**
   * Governor on: run the throttle ingestion pipeline (pause/resume-if-needed → poll → offer →
   * commit). Governor off: delegate to {@link HKafkaProtoConsumer#runInternal()} for
   * bit-identical vanilla behavior.
   */
  @Override
  protected void runInternal() throws InterruptedException {
    if (!governorEnabled) {
      super.runInternal();
      return;
    }

    // Order matters: resume takes precedence over re-pause. Both flags cannot both be set on a
    // healthy transition, but resumeRequested clears the halt-side pause first so a rebalance in
    // between doesn't leave us stuck paused.
    if (resumeRequested.compareAndSet(true, false)) {
      resumeAssignment();
    }
    if (pausedByHalt.get() || pausedByWatermark.get()) {
      pauseAssignment();
    }

    ConsumerRecords<String, T> records = pollThreadSafe();
    if (records.isEmpty()) {
      // Small sleep so we don't tight-loop when paused or idle. Well within max.poll.interval.ms.
      Thread.sleep(Duration.ofMillis(300).toMillis());
      return;
    }

    List<ConsumerRecord<String, T>> processable =
        applyFilters(java.util.stream.StreamSupport.stream(records.spliterator(), false).toList());

    // Batch commit before dispatch — matches vanilla runNoAck at-most-once semantics.
    acknowledgeSync();

    for (ConsumerRecord<String, T> record : processable) {
      offerRecord(record);
    }

    // If offering just pushed us over the high watermark, pause the assignment until workers
    // drain us below the low watermark.
    if (!pausedByHalt.get() && queue.size() >= throttleConfig.highWatermarkThreshold()
        && pausedByWatermark.compareAndSet(false, true)) {
      log.info("Queue for [{}] hit high watermark ({} of {}) — pausing consumer.", consumerKey, queue.size(),
          throttleConfig.getQueueCapacity());
      recordPauseResumeEvent();
      pauseAssignment();
    }
  }

  /**
   * Hand one already-read, already-committed record to the workers. This must not give up: the
   * offset is committed and the dedup filter has marked it seen, so a record dropped here is
   * permanent data loss.
   *
   * <p>Blocks (with backpressure) rather than dropping when the queue is full. The
   * {@code offerTimeoutMs} wait is retained as the interval between pause attempts, not as a
   * deadline after which the record is discarded: on the first unsuccessful wait we pause the
   * assignment so no further records are fetched, then keep waiting for a slot. This terminates
   * because workers drain the queue in every mode including HALT.
   *
   * <p>The retry loop does not need its own sleep — the timed {@code offer} <i>is</i> the wait. It
   * parks on the queue's not-full condition and returns the moment a worker frees a slot, so each
   * iteration costs one park/unpark rather than a spin. The wait is floored at
   * {@link #MIN_OFFER_WAIT_MS} only because {@code offerTimeoutMs} is operator-tunable and a
   * configured {@code 0} would make the timed offer return immediately, turning this into a hot
   * spin on the ingestion thread.
   *
   * <p>Blocking the ingestion thread here is safe with respect to {@code max.poll.interval.ms}
   * (300s default) for any sane rate limit: the wait is bounded by the time to drain one queue
   * slot, i.e. {@code 1/rps} seconds. It is only reported and abandoned on interrupt, which means
   * the pod is shutting down.
   */
  // Package-private for unit tests that drive the ingestion pipeline directly.
  void offerRecord(ConsumerRecord<String, T> record) {
    long waitMs = Math.max(MIN_OFFER_WAIT_MS, throttleConfig.getOfferTimeoutMs());
    boolean pausedForBackpressure = false;
    long waitedMs = 0;
    long nextStallLogMs = BACKPRESSURE_STALL_LOG_INTERVAL_MS;
    try {
      while (true) {
        if (queue.offer(record, waitMs, TimeUnit.MILLISECONDS)) {
          return;
        }
        waitedMs += waitMs;
        if (!pausedForBackpressure) {
          pausedForBackpressure = true;
          // Log inside the CAS: pausedForBackpressure is a per-invocation local, so logging out
          // here emits one line per record instead of one per pause episode.
          if (pausedByWatermark.compareAndSet(false, true)) {
            log.warn("Queue for [{}] full after {}ms offer wait — pausing consumer and applying backpressure"
                    + " until a slot frees up.",
                consumerKey, waitMs);
            recordPauseResumeEvent();
            pauseAssignment();
          }
        } else if (waitedMs >= nextStallLogMs) {
          // Re-log periodically so a queue that never drains is visible. Without this the only
          // signal is the single warn above, and a wedged handler in every worker would sit here
          // silently until Kafka evicts us at max.poll.interval.ms.
          nextStallLogMs += BACKPRESSURE_STALL_LOG_INTERVAL_MS;
          log.warn("Backpressure on [{}] has not cleared after {}ms — queue still full at {} records."
                  + " Workers may be stuck in the message handler.",
              consumerKey, waitedMs, queue.size());
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      reportLost("ingestion thread interrupted while enqueueing", record);
    }
  }

  void workerLoop() {
    while (!stopped.get()) {
      ConsumerRecord<String, T> record;
      try {
        record = queue.poll(200, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      if (record == null) {
        maybeResume();
        continue;
      }

      // Deliberately no HALT check here. Everything in this queue is already committed on the
      // ingestion side (at-most-once), so a record we dequeue and fail to dispatch is lost for
      // good: Kafka will not redeliver it, and the ingestion-side dedup filter has already marked
      // its offset as seen. HALT stops intake at the ingestion thread, which is what a flood
      // incident needs; the residual buffer is drained rather than dropped or held, because
      // holding an in-memory queue only survives until the next pod restart.
      FlowGovernorState.Mode observedMode = currentMode.get();
      if (!acquirePermit(record)) {
        continue;
      }

      // Hand off to the shared engine pool, same executor and call shape as vanilla runNoAck, so
      // handler concurrency scales to that pool's maxPoolSize rather than to our worker count.
      // Workers are pacers only: dequeue, take a permit, submit.
      final ConsumerRecord<String, T> submitted = record;
      try {
        executorService.execute(() -> dispatch(submitted));
      } catch (RejectedExecutionException rex) {
        // ForceQueuePolicy blocks instead of rejecting, so this only fires if a worker was
        // interrupted mid-put by shutdownNow().
        reportLost("engine pool rejected record", submitted);
        continue;
      }
      recordInvoked(observedMode);
      maybeResume();
    }
  }

  private void recordInvoked(FlowGovernorState.Mode observedMode) {
    if (metricEmitter != null) {
      metricEmitter.recordDispatched(observedMode);
    }
  }

  private void recordPauseResumeEvent() {
    if (metricEmitter != null) {
      metricEmitter.recordPauseOrResume();
    }
  }

  /**
   * Wait for a rate-limiter permit for a record the caller has already dequeued. Retries
   * indefinitely on permit timeout, because the record's offset is already committed and returning
   * false would mean dropping it. Returns false only when the worker is shutting down or was
   * interrupted, in which case the record is reported as lost with its identifiers so the loss is
   * reconcilable rather than silent.
   *
   * <p>Note that HALT does not abort the wait. A dequeued record is drained even under HALT — see
   * the class javadoc for why discarding or holding it are both worse.
   */
  boolean acquirePermit(ConsumerRecord<String, T> record) {
    while (true) {
      try {
        RateLimiter.waitForPermission(rateLimiter);
        return true;
      } catch (io.github.resilience4j.ratelimiter.RequestNotPermitted timeout) {
        // Permit not available within timeoutDuration. Loop and try again — this is normal under
        // sustained THROTTLE where limitForPeriod < arrival rate. The record stays in the worker
        // frame (never re-queued) so ordering vs. other workers isn't disturbed. We do not give up
        // on timeout: permits always eventually arrive, and a committed-but-undispatched record is
        // permanent data loss.
        log.debug("Permit acquire timeout for [{}], retrying.", consumerKey);
      } catch (Exception ex) {
        log.warn("Unexpected error acquiring rate-limiter permit for [{}].", consumerKey, ex);
        // Fall through to loop; treat as transient.
      }
      if (Thread.currentThread().isInterrupted()) {
        // Forced shutdown (workerPool.shutdownNow) while we held a record. Nothing left to do but
        // make the loss visible.
        reportLost("worker interrupted while waiting for permit", record);
        return false;
      }
    }
  }

  /**
   * Emit a single, greppable line per record that this pod could not dispatch. Always includes the
   * topic-partition-offset triple so the loss can be reconciled after the fact — the offset is
   * already committed, so nothing else will surface it.
   */
  private void reportLost(String reason, ConsumerRecord<String, T> record) {
    log.error("Dropping already-committed record for [{}] ({}). topic-partition-offset={}-{}-{}", consumerKey, reason,
        record.topic(), record.partition(), record.offset());
  }

  private void dispatch(ConsumerRecord<String, T> record) {
    Map<String, String> headers = HKafkaUtils.fromHeadersToMap(record.headers());
    try (KafkaRecordLogContext logContext = new KafkaRecordLogContext(record, headers)) {
      Map<String, Object> metricInfo = getMetricInfo(record);
      withTracing(record, headers, () -> messageHandler.onMessage(record.value(), headers, metricInfo));
    } catch (Exception e) {
      log.error("Error processing message in throttled consumer [{}], continuing.", consumerKey, e);
    }
  }

  void maybeResume() {
    if (pausedByHalt.get()) {
      return;
    }
    if (pausedByWatermark.get() && queue.size() <= throttleConfig.lowWatermarkThreshold()
        && pausedByWatermark.compareAndSet(true, false)) {
      log.info("Queue for [{}] drained to low watermark ({}) — requesting resume.", consumerKey, queue.size());
      recordPauseResumeEvent();
      resumeRequested.set(true);
    }
  }

  void pollMode() {
    try {
      FlowGovernorState state = stateCache.getState();
      FlowGovernorState.Mode newMode = state.getMode() == null ? FlowGovernorState.Mode.NORMAL : state.getMode();
      FlowGovernorState.Mode previous = currentMode.getAndSet(newMode);
      if (previous == newMode) {
        // Even without a mode flip, THROTTLED RPS may have changed under us.
        if (newMode == FlowGovernorState.Mode.THROTTLED) {
          applyThrottledRps(state);
        }
        emitGauges(newMode);
        return;
      }

      log.info("Flow governor mode change for [{}]: {} -> {}", consumerKey, previous, newMode);
      switch (newMode) {
        case HALTED:
          if (pausedByHalt.compareAndSet(false, true)) {
            recordPauseResumeEvent();
          }
          // Actual pause happens on next poll iteration.
          break;
        case THROTTLED:
        case NORMAL:
        default:
          if (pausedByHalt.compareAndSet(true, false)) {
            // Coming out of HALT — ask the ingestion thread to resume the assignment on its
            // next iteration. We do not call consumer.resume() from this thread because the
            // ingestion thread owns the consumer's synchronized lock during poll().
            recordPauseResumeEvent();
            resumeRequested.set(true);
          }
          if (newMode == FlowGovernorState.Mode.THROTTLED) {
            applyThrottledRps(state);
          } else {
            rateLimiter.changeLimitForPeriod(Math.max(1, governorConfig.resolveNormalRpsFor(consumerKey)));
          }
          break;
      }
      emitGauges(newMode);
    } catch (Exception ex) {
      log.warn("Flow governor mode poll failed for [{}]; retaining previous mode.", consumerKey, ex);
    }
  }

  private void emitGauges(FlowGovernorState.Mode mode) {
    if (metricEmitter == null || queue == null || rateLimiter == null) {
      return;
    }
    metricEmitter.emitGauges(mode, queue.size(), rateLimiter.getRateLimiterConfig().getLimitForPeriod());
  }

  private void applyThrottledRps(FlowGovernorState state) {
    Integer resolved = state.resolveRpsFor(consumerKey);
    int rps;
    if (resolved != null) {
      rps = resolved;
    } else {
      // Operator opted into THROTTLED without setting an rps. Fall back to a deliberately low
      // safety value so THROTTLE never silently becomes a no-op at the NORMAL ceiling.
      log.warn("Flow governor in THROTTLED mode with no RPS for [{}]; using safety fallback {} rps.", consumerKey,
          SAFETY_THROTTLE_FALLBACK_RPS);
      rps = SAFETY_THROTTLE_FALLBACK_RPS;
    }
    rateLimiter.changeLimitForPeriod(Math.max(1, rps));
  }

  private void pauseAssignment() {
    if (consumer == null) {
      return;
    }
    synchronized (consumer) {
      pause(consumer.assignment());
    }
  }

  private void resumeAssignment() {
    if (consumer == null) {
      return;
    }
    synchronized (consumer) {
      resume(consumer.assignment());
    }
  }

  /**
   * Wire the halt-aware rebalance listener when the governor is enabled. On rebalance,
   * {@code HaltAwareRebalanceListener} calls {@link #shouldRepauseOnAssign()} to decide whether
   * newly assigned partitions come back paused.
   */
  @Override
  protected ConsumerRebalanceListener createRebalanceListener() {
    if (!governorEnabled) {
      return super.createRebalanceListener();
    }
    return new HaltAwareRebalanceListener(consumer, getEventMonitoringService(), topic, this::shouldRepauseOnAssign);
  }

  /**
   * Called by {@link HaltAwareRebalanceListener} on {@code onPartitionsAssigned}. Returns true
   * when newly assigned partitions should come back paused (either HALTED or watermark-paused).
   */
  public boolean shouldRepauseOnAssign() {
    return governorEnabled && (pausedByHalt.get() || pausedByWatermark.get());
  }
}

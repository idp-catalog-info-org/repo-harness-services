# Runbook — Pipeline Execution Events Flow Governor

Operator guide for throttling or halting the governed orchestration Kafka consumers during an
incident.

**Owner:** Pipeline (PIPE) · **Jira:** PIPE-36189

---

## 1. What this controls

Two orchestration Kafka consumers are governed:

| Consumer key      | What it drives                            |
|-------------------|-------------------------------------------|
| `initiateNode`    | Node-start events (plan node initiation)  |
| `sdkStepResponse` | Step responses coming back from SDKs      |

Each runs as a `ThrottledKafkaConsumer`: a lightweight ingestion thread that polls Kafka and offers
records to a bounded in-memory queue, plus a worker pool that drains that queue through a rate
limiter and hands each record to the shared orchestration engine pool — the same pool the ungoverned
consumer submits to. The workers pace; the engine pool runs the handlers.

Three modes:

| Mode        | Effect                                                                    |
|-------------|---------------------------------------------------------------------------|
| `NORMAL`    | Rate-limited at `normalRps` (default 10000 — effectively a no-op)         |
| `THROTTLED` | Rate-limited at the operator-supplied rps, globally or per consumer key   |
| `HALTED`    | Intake stops (partitions paused); the residual queue is still drained     |

---

## 2. Two things to internalize before you touch this

### 2.1 The controls are cluster-wide, not per-account

The governor state lives at a single Redis key (`pms-flow-governor-state` / field `orchestration`).
There is no account scoping, by design. **A HALT stops node-start and step-response intake for every
account on the cluster**, not just the account whose pipelines triggered the incident. Treat it as a
cluster-level lever.

### 2.2 HALT is a drain, not a freeze — and not instantaneous

HALT pauses *intake*. It does **not** gate the workers. Records already in the queue, and records
already read in the in-flight poll batch, are still dispatched through the message handler.

Note what "dispatched" means: a worker takes a rate-limiter permit and then *submits* the handler to
the shared orchestration engine pool — the same pool the ungoverned consumer submits to. The rps you
set paces submissions; the handlers themselves run at engine-pool concurrency. So "the drain is
done" means every residual record has been handed to the engine, not that every handler has
finished.

This is forced by the ack timing, which is at-most-once: the batch offset is committed *before*
records are enqueued (matching vanilla `runNoAck`). Once a record is in the queue its offset is
already committed and the ingestion-side dedup filter has marked that offset as seen for its cache
TTL — so Kafka will never redeliver it, and even a manual offset rewind would not replay it.
Discarding such a record is permanent data loss, and merely *holding* it only defers the problem,
because the queue is in-memory and a pod restart mid-HALT would lose the whole buffer. Draining is
the only option that actually processes those records.

Consequences to expect:

- **Processing does not stop the moment you POST /halt.** It stops after at most
  `queueCapacity` (200) + one poll batch of residual records, drained at *the rps in force when you
  halted*. From `NORMAL` that is milliseconds. From a deep THROTTLE it is `residual / rps` seconds
  — at `rps=1` with a full queue, ~200s. That is implicit in the rps you chose; draining faster
  would defeat the downstream protection THROTTLE exists for.
- **Intake stops immediately**, which is what a flood incident actually needs stopped.
- **Detection lag:** consumers learn about a mode change through a Caffeine cache
  (`refreshAfterWrite` 30s) plus a 5s mode poll, so worst case is ~35s before a pod acts on your
  change. Do not conclude the API failed because nothing changed in the first 10 seconds; check the
  `version` in the response body instead (§4).

---

## 3. Applying a change

### 3.1 From the admin portal (preferred)

The admin portal calls manager (`AdminAccountResource` in 400-rest), which forwards to
pipeline-service over a `PRIVILEGED` client. That client mints a `SERVICE` principal, which is what
the pipeline-service endpoints require — there is no way to reach them with a user token or an
account API key.

| Action                | Endpoint (on manager)                                                                |
|-----------------------|--------------------------------------------------------------------------------------|
| Halt                  | `POST /admin/accounts/ng/orchestration-governor/halt`                                |
| Resume throttled      | `POST /admin/accounts/ng/orchestration-governor/resume/throttled?rps=N[&consumer=K]` |
| Resume full (NORMAL)  | `POST /admin/accounts/ng/orchestration-governor/resume/full`                         |
| Read current state    | `GET  /admin/accounts/ng/orchestration-governor/state`                               |

These sit alongside the other admin-portal pipeline actions (`ng/abort-executions`,
`ng/blockExecution`, `ng/pipeline-override-config`) on the same `@AdminPortalAuth` resource, so they
are reachable only with the admin-portal JWT. Note the `/admin/accounts` prefix is inherited from
that resource and is **not** meaningful here — these endpoints take no `accountIdentifier`, because
the governor has no per-account scoping (§2.1).

The human who pushed the button is logged in manager — downstream pipeline-service only ever sees
the manager service principal, so **the manager log is the only place the actual operator is
recoverable**. Grep manager for `Flow governor:` when reconstructing an incident timeline.

### 3.2 Direct to pipeline-service

Same paths without the `/admin` prefix, under `/orchestration/governor`. `SERVICE` principal only;
useful only from another service or with a service token.

### 3.3 Bounds and validation

Validation lives in pipeline-service (not in the manager proxy, deliberately, so the bounds
cannot drift from the code that applies them):

- `rps` must be within `[1, 10000]`.
- `consumer`, if supplied, must be one of `initiateNode` / `sdkStepResponse`. Unknown keys are
  rejected rather than silently accumulating as stale overrides.
- Omitting `consumer` sets the **default** rps for all governed consumers. Supplying it sets a
  **per-consumer override**, which wins over the default. These are distinct operations — omitting
  the parameter is not the same as passing a consumer key.

---

## 4. Confirming your change landed

Every mutating call returns the resulting `FlowGovernorStateDTO`, including a monotonically
increasing `version` plus `updatedBy` / `updatedAt`. Read the response body rather than assuming
success:

- `version` incremented past what you last saw ⇒ your write landed.
- `version` jumped by more than 1, or `updatedBy` is not you ⇒ **another operator is also driving
  this.** Stop and coordinate before issuing more changes; there is no locking.
- A non-2xx from the manager proxy is surfaced, never swallowed. A 403 in particular means the
  `PRIVILEGED` client's `SERVICE` principal was rejected, i.e. the proxy is misconfigured — it does
  **not** mean the halt partially applied.

---

## 5. Metrics

| Metric                                                | Read it for                                  |
|-------------------------------------------------------|----------------------------------------------|
| `pipeline_execution_events_flow_governor_invoked`      | Per-mode invocation counts (label `mode`)    |
| `pipeline_execution_events_throttled_mode_rps_actual`  | Achieved rps — compare against expected      |
| `pipeline_execution_events_throttled_mode_rps_expected`| Configured rps the limiter is aiming at      |
| `pipeline_execution_events_flow_governor_queue_depth`  | Residual buffer; the drain-time input        |
| `pipeline_execution_events_flow_governor_pause_resume` | Pause/resume transitions (watermark + halt)  |

`rps_actual` measures the rate workers *submit* handlers to the engine pool, not the rate handlers
complete. That is the right thing to compare against `rps_expected` — the limiter governs submission
— but it means `rps_actual` tracking `rps_expected` does not by itself prove the engine is keeping
up. For that, look at the engine pool's own saturation.

`queue_depth` is the number to watch during a HALT: the drain is done when it reaches zero. It is
also the leading indicator of backpressure — depth pinned at `queueCapacity` means workers are not
keeping up. That has two distinct causes: the rate limiter is the binding constraint (expected
under THROTTLE — `rps_actual` ≈ `rps_expected`), or the engine pool is saturated and workers are
parked in the blocking submit (`rps_actual` well below `rps_expected`). Only the second is a problem.

---

## 6. Expected log lines

| Line                                                          | Meaning                                                                 |
|---------------------------------------------------------------|-------------------------------------------------------------------------|
| `Queue for [K] full after Nms offer wait — pausing consumer`   | Backpressure engaged. **Not** data loss — the ingestion thread waits.   |
| `Backpressure on [K] has not cleared after Nms`                | Re-logged every 10s. Workers may be wedged in the message handler.      |
| `Queue for [K] drained to low watermark (N) — requesting resume.` | Backpressure cleared; intake resuming.                               |
| `Flow-governor queue for [K] still holds N records after Nms shutdown drain.` | Shutdown ran out of drain budget; the next line names what was lost. |
| `Dropping already-committed record for [K] (reason)`            | **Real, permanent loss of that record.** See §7.                        |

`Dropping already-committed record` is the only log line that indicates actual data loss. It fires
in exactly four situations:

1. `abandoned on shutdown` — the residual queue did not drain inside the shutdown budget.
2. `ingestion thread interrupted while enqueueing` — pod shutting down mid-offer.
3. `worker interrupted while waiting for permit` — pod shutting down mid-rate-limit-wait. Rare,
   because `stop()` lifts the limiter to NORMAL rps before waiting, so permit waits are sub-millisecond.
4. `engine pool rejected record` — the shared engine pool threw `RejectedExecutionException` on
   handoff. The pool's `ForceQueuePolicy` blocks rather than rejects, so in practice this means the
   worker was interrupted mid-enqueue, or the pool was already shut down.

The first three are shutdown- or interrupt-driven. Steady-state THROTTLE and HALT do **not** drop
records. If you see any of them outside a deploy or pod restart, escalate — it means something
interrupted the threads unexpectedly.

---

## 7. Backpressure under sustained THROTTLE

When arrival rate exceeds the configured rps, the queue fills. The consumer then **blocks with
backpressure** rather than discarding the overflow: on the first unsuccessful offer it pauses the
assignment so no further records are fetched, then keeps waiting for a slot. This terminates because
workers drain the queue in every mode, including HALT.

Blocking the ingestion thread is safe against `max.poll.interval.ms` (300s default) for any sane
rps, because the wait is bounded by the time to drain one queue slot (`1/rps` seconds). At `rps=1`
that is 1s per slot — comfortable. Setting a pathological rps is the footgun here, not the blocking.

Note for anyone reading the original design doc: risks #4 ("workers drop queued records on HALT")
and #6 ("the ingestion thread pauses + drops the overflow") describe an earlier design. The shipped
implementation drains and blocks respectively; neither drops in steady state.

---

## 8. Shutdown behavior during a deploy

`stop()` is ordered intake-first, then lift the throttle, then drain, then workers. Stopping intake
before draining is what makes the drain converge; lifting the throttle is what makes it converge
*fast*.

**The drain runs at full pace, not at the throttled rps.** Once intake is stopped, the residual is
already committed and bounded by `queueCapacity`, so there is nothing left to protect the engine
from by pacing it — `stop()` raises the limiter to the consumer's NORMAL rps before waiting. A deploy
mid-HALT or mid-deep-THROTTLE therefore drains the buffer at normal speed instead of at the rps you
set. The engine pool's own bounded queue still supplies backpressure.

There are two waits, both **give-up deadlines rather than delays** — each returns the instant its
condition is met, so a healthy shutdown does not spend them:

| Wait | Returns as soon as | Deadline |
|------|--------------------|----------|
| Queue drain | `queue.isEmpty()` (polled every 100ms) | 30s |
| Worker exit  | `workerPool.awaitTermination` — last worker exits | 10s |

A healthy shutdown therefore costs ~100ms, not 40s. The deadlines are only ever reached when the
drain is genuinely stuck, which means one thing: the engine pool is saturated and workers are
parked in its blocking submit.

The worker-exit signal is exact. Workers check the stop flag only between records, so a worker exits
only after its current submit has returned — termination means every record any worker was holding
reached the engine pool.

Both are fixed rather than derived from the rps, because the rate limiter is not what bounds the
drain — it is lifted immediately before the drain runs, so it never binds. The real bound is
engine-pool availability, which cannot be estimated from anything we control.

Serialized across both governed consumers the worst case is 150s, inside the 180s
`terminationGracePeriodSeconds`.

Blocking inside `stop()` is the only thing that keeps the pod alive for the drain — the JVM exits as
soon as the last `Managed` bean's `stop()` returns, *not* when the grace period expires. So there is
no passive "let it keep draining until we're killed" behavior to rely on.

Note what the drain guarantees: queued records get **submitted to the engine pool**, not that their
handlers **complete**. The drain finishes when the queue empties, at which point handlers may still
be running and will be abandoned at JVM exit. This is exactly the ungoverned consumer's behavior —
it also returns from `stop()` with handlers in flight — so it is a pre-existing property, not
something the governor introduces. It is also unobservable: abandoned handlers are not logged by
either path.

**Still prefer to resume to `NORMAL` and let `queue_depth` reach zero before a planned deploy.** The
loss window is small, but not zero: a SIGKILL or OOM before `stop()` runs at all loses up
to `queueCapacity` (200) committed records with no log line, and that risk scales with queue depth.

---

## 9. Playbooks

### 9.1 Orchestration event flood

1. `GET /state` — record the current `version` so you can detect a concurrent operator.
2. `POST /resume/throttled?rps=<~50% of steady-state>` first. Prefer throttling to halting: it keeps
   pipelines progressing slowly instead of stalling them entirely.
3. Watch `rps_actual` converge on `rps_expected`, and `queue_depth`. Allow ~35s for pods to pick up
   the change.
4. If throttling is not enough, `POST /halt`. Expect intake to stop at once and `queue_depth` to
   fall to zero over `residual / rps` seconds.
5. Recover: step the rps back up (`/resume/throttled` with successively higher values) rather than
   jumping straight to `/resume/full`, so you can watch the downstream absorb it.
6. `POST /resume/full` once healthy. **Do not leave a THROTTLE in place** — it silently caps
   throughput, and the next person to deploy will hit §8.

### 9.2 One consumer is the problem

Use the per-consumer override: `POST /resume/throttled?rps=N&consumer=initiateNode`. This leaves the
other consumer at the default rps. Verify via `targetRpsByConsumer` in the response body — the UI
renders that map, so if the override is missing there it did not apply.

### 9.3 "I halted and events are still being processed"

Working as designed — see §2.2. Check `queue_depth`: while it is above zero, the residual buffer is
still draining. If it is zero and processing continues, the halt has not reached those pods yet
(≤35s), or `flowGovernorConfig.enabled` is false on them, in which case they run the vanilla
consumer path and ignore the governor entirely.

---

## 10. Config reference

`flowGovernorConfig` in `pipeline-service/config/config.yml`:

| Key                            | Default | Notes                                                     |
|--------------------------------|---------|-----------------------------------------------------------|
| `enabled`                      | `false` | When false, the vanilla consumer path runs, bit-identical. |
| `normalRps`                    | `10000` | High on purpose — NORMAL is a no-op until tuned to ~5× p99. |
| `throttledConsumerConfig.workers` | `20`  | Submit-stage parallelism, **not** handler concurrency. Raising it does not widen handler throughput. |
| `queueCapacity`                | `200`   | Bounded buffer; drives residual/drain time, and is the at-risk window on SIGKILL. Independent of `workers`. |
| `offerTimeoutMs`               | `100`   | Interval between pause attempts, **not** a discard deadline. |
| `highWatermarkPercent`         | `80`    | Pause intake above this queue fill.                        |
| `lowWatermarkPercent`          | `30`    | Resume intake below this queue fill.                       |
| `modePollIntervalMs`           | `5000`  | Mode poll; combines with the 30s cache refresh for ~35s lag. |
| `rateLimiterRefreshPeriodMs`   | `1000`  | Rate-limiter window.                                       |
| `permitAcquireTimeoutMs`       | `1000`  | Worker wait for a rate-limiter permit.                      |

`enabled: false` is the kill switch: it disables the governor entirely and reverts to vanilla
consumer behavior. Use it if the governor itself is suspected, not to undo a THROTTLE — for that,
`POST /resume/full`.

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.perpetualtask.entityreference;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.AccountId;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.entityreference.PerpetualTaskBundleRefreshCallback;
import io.harness.perpetualtask.entityreference.PerpetualTaskEntityReference;
import io.harness.perpetualtask.entityreference.PerpetualTaskEntityReferenceRegisterRequest;
import io.harness.perpetualtask.entityreference.PerpetualTaskEntityReferenceService;
import io.harness.perpetualtask.entityreference.PerpetualTaskReferenceEntityType;
import io.harness.perpetualtask.entityreference.PerpetualTaskReferredEntity;
import io.harness.repositories.perpetualtask.PerpetualTaskEntityReferenceRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

/**
 * Generic implementation: persists reference rows (each carrying a self-contained refresh callback) on register, and on
 * entity change resolves the affected perpetual tasks and invokes their stored callback. Holds no consumer-specific
 * knowledge - extraction and refresh live entirely in the consumer-provided callbacks.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class PerpetualTaskEntityReferenceServiceImpl implements PerpetualTaskEntityReferenceService {
  // Soft upper bound on references per task; exceeding it is not an error but is logged so anomalous callers are
  // visible before they cause latency spikes on the insert path.
  private static final int MAX_EXPECTED_REFERENCES_PER_TASK = 50;

  private final PerpetualTaskEntityReferenceRepository referenceRepository;
  private final Injector injector;
  private final DelegateServiceGrpcClient delegateServiceGrpcClient;
  private final ExecutorService refreshExecutor;
  private final RateLimiter refreshRateLimiter;
  private final Retry refreshRetry;

  @Inject
  public PerpetualTaskEntityReferenceServiceImpl(PerpetualTaskEntityReferenceRepository referenceRepository,
      Injector injector, DelegateServiceGrpcClient delegateServiceGrpcClient,
      @Named("perpetual-task-entity-reference-refresh-executor") ExecutorService refreshExecutor) {
    this(referenceRepository, injector, delegateServiceGrpcClient, refreshExecutor, defaultRefreshRateLimiter(),
        defaultRefreshRetry());
  }

  PerpetualTaskEntityReferenceServiceImpl(PerpetualTaskEntityReferenceRepository referenceRepository, Injector injector,
      DelegateServiceGrpcClient delegateServiceGrpcClient, ExecutorService refreshExecutor,
      RateLimiter refreshRateLimiter, Retry refreshRetry) {
    this.referenceRepository = referenceRepository;
    this.injector = injector;
    this.delegateServiceGrpcClient = delegateServiceGrpcClient;
    this.refreshExecutor = refreshExecutor;
    this.refreshRateLimiter = refreshRateLimiter;
    this.refreshRetry = refreshRetry;
  }

  private static RateLimiter defaultRefreshRateLimiter() {
    return RateLimiter.of("PerpetualTaskEntityReferenceRefresh",
        RateLimiterConfig.custom()
            .limitForPeriod(100)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ofMinutes(5))
            .build());
  }

  private static Retry defaultRefreshRetry() {
    return Retry.of("PerpetualTaskEntityReferenceRefresh",
        RetryConfig.custom()
            .retryExceptions(RequestNotPermitted.class)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(10)))
            .maxAttempts(10)
            .build());
  }

  @Override
  public void register(PerpetualTaskEntityReferenceRegisterRequest request) {
    if (request == null || isEmpty(request.getPerpetualTaskId()) || isEmpty(request.getAccountId())) {
      return;
    }
    try {
      referenceRepository.deleteByPerpetualTaskId(request.getPerpetualTaskId());
      if (isEmpty(request.getReferredEntities())) {
        return;
      }
      if (request.getCallback() == null) {
        // Without a callback the rows can never trigger a refresh; persisting them would only generate WARN noise on
        // every future entity-change reverse-lookup. Skip registration entirely (the stale rows were already deleted).
        log.warn("register() called with non-empty referredEntities but null callback for perpetual task {} - skipping "
                + "registration; entity changes will not trigger refreshes",
            request.getPerpetualTaskId());
        return;
      }
      List<PerpetualTaskEntityReference> rows = new ArrayList<>();
      for (PerpetualTaskReferredEntity referredEntity : request.getReferredEntities()) {
        rows.add(PerpetualTaskEntityReference.builder()
                     .accountId(request.getAccountId())
                     .parentUniqueId(request.getParentUniqueId())
                     .perpetualTaskId(request.getPerpetualTaskId())
                     .referredEntityType(referredEntity.getType())
                     .referredEntityRef(referredEntity.getRef())
                     .callback(request.getCallback())
                     .build());
      }
      if (rows.size() > MAX_EXPECTED_REFERENCES_PER_TASK) {
        log.warn("Registering an unusually large number of entity references ({}) for perpetual task {} in account {}",
            rows.size(), request.getPerpetualTaskId(), request.getAccountId());
      }
      referenceRepository.saveAll(rows);
      log.info("Registered {} entity reference(s) for perpetual task {} in account {}", rows.size(),
          request.getPerpetualTaskId(), request.getAccountId());
    } catch (Exception ex) {
      // Best-effort: delete already committed, so on failure here the task is temporarily de-indexed until the next
      // register(). Logged explicitly so responders know the state is recoverable only by re-registration.
      log.error("Failed to save new entity references for perpetual task {} - previously registered references were "
              + "already deleted; the task will not receive entity-change refreshes until it is re-registered",
          request.getPerpetualTaskId(), ex);
    }
  }

  @Override
  public void unregister(String perpetualTaskId) {
    if (isEmpty(perpetualTaskId)) {
      return;
    }
    try {
      referenceRepository.deleteByPerpetualTaskId(perpetualTaskId);
    } catch (Exception ex) {
      log.error("Failed to unregister entity references for perpetual task {}", perpetualTaskId, ex);
    }
  }

  @Override
  public void onEntityUpdated(
      String accountId, PerpetualTaskReferenceEntityType entityType, String entityRef, String actionType) {
    if (isEmpty(accountId) || isEmpty(entityRef) || entityType == null) {
      return;
    }
    List<PerpetualTaskEntityReference> matches =
        referenceRepository.findByAccountIdAndReferredEntityTypeAndReferredEntityRef(accountId, entityType, entityRef);
    if (isEmpty(matches)) {
      return;
    }
    log.info("Queueing rate-limited refresh for perpetual task(s) in account {} due to {} change {}", accountId,
        entityType, entityRef);
    for (PerpetualTaskEntityReference reference : matches) {
      refreshExecutor.submit(()
                                 -> refreshPerpetualTaskWithRateLimit(
                                     accountId, reference.getPerpetualTaskId(), reference.getCallback(), actionType));
    }
  }

  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  private void refreshPerpetualTaskWithRateLimit(
      String accountId, String perpetualTaskId, PerpetualTaskBundleRefreshCallback callback, String actionType) {
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
      Retry
          .decorateCheckedRunnable(refreshRetry,
              ()
                  -> RateLimiter
                         .decorateCheckedRunnable(refreshRateLimiter,
                             () -> refreshPerpetualTask(accountId, perpetualTaskId, callback, actionType))
                         .run())
          .run();
    } catch (RequestNotPermitted ex) {
      log.warn("Timed out waiting for refresh rate limit permit for perpetual task {} in account {}", perpetualTaskId,
          accountId, ex);
    } catch (Throwable ex) {
      log.error("Failed to refresh perpetual task {} in account {}", perpetualTaskId, accountId, ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void refreshPerpetualTask(
      String accountId, String perpetualTaskId, PerpetualTaskBundleRefreshCallback callback, String actionType) {
    if (DELETE_ACTION.equals(actionType)) {
      delegateServiceGrpcClient.deletePerpetualTask(
          AccountId.newBuilder().setId(accountId).build(), PerpetualTaskId.newBuilder().setId(perpetualTaskId).build());
      unregister(perpetualTaskId);
      return;
    }
    if (callback == null) {
      log.warn("No refresh callback stored for perpetual task {} (account {})", perpetualTaskId, accountId);
      return;
    }
    injector.injectMembers(callback);
    callback.refresh();
  }
}

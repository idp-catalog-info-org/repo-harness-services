/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.sanitizer;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ng.privateconnectivity.util.PrivateConnectivityLifecycle.PROVISIONING_STALE_AFTER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityOperationType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityStatus;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;
import io.harness.ng.privateconnectivity.services.PrivateConnectivityService;
import io.harness.repositories.ng.privateconnectivity.PrivateConnectivityConfigRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.lifecycle.Managed;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;

/** Recovers bounded batches of durable, incomplete private-connectivity operations. */
@OwnedBy(CI)
@Singleton
@Slf4j
public class ReleaseReconciler implements Managed {
  private static final long INITIAL_DELAY_SECONDS = 30L;
  // Recovery is a safety net for durable incomplete operations, not provider polling. Immediate
  // work is submitted by the API path; this scan recovers work lost to a restart or executor
  // rejection, while each row's nextRetryAt continues to enforce provider retry backoff.
  private static final long PERIOD_SECONDS = 60L;
  private static final int MAX_CANDIDATES_PER_TICK = 100;
  private static final Set<PrivateConnectivityStatus> ACTIVE_RECOVERY_STATUSES =
      Set.of(PrivateConnectivityStatus.RELEASING, PrivateConnectivityStatus.RECONCILING);

  private final PrivateConnectivityConfigRepository repository;
  private final PrivateConnectivityService privateConnectivityService;
  private final ManagedScheduledExecutorService scheduler;

  @Inject
  public ReleaseReconciler(
      PrivateConnectivityConfigRepository repository, PrivateConnectivityService privateConnectivityService) {
    this.repository = repository;
    this.privateConnectivityService = privateConnectivityService;
    this.scheduler = new ManagedScheduledExecutorService("private-connectivity-release-reconciler");
  }

  @Override
  public void start() {
    scheduler.scheduleWithFixedDelay(() -> {
      try {
        reconcile();
      } catch (Exception e) {
        log.warn("Private Connectivity recovery scan failed", e);
      }
    }, INITIAL_DELAY_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
    log.info("Private Connectivity recovery reconciler started periodSeconds={}", PERIOD_SECONDS);
  }

  @Override
  public void stop() throws Exception {
    scheduler.stop();
  }

  public void reconcile() {
    List<PrivateConnectivityConfig> candidates;
    try {
      long now = System.currentTimeMillis();
      long staleBeforeMs = now - PROVISIONING_STALE_AFTER.toMillis();
      candidates = repository.findRecoverable(now, ACTIVE_RECOVERY_STATUSES, PrivateConnectivityStatus.ERROR,
          PrivateConnectivityOperationType.UPDATE, PrivateConnectivityOperationType.PROVISION,
          PrivateConnectivityStatus.PROVISIONING, staleBeforeMs, PageRequest.of(0, MAX_CANDIDATES_PER_TICK));
    } catch (Exception e) {
      log.warn("Private Connectivity recovery query failed", e);
      return;
    }
    if (candidates.isEmpty()) {
      return;
    }
    candidates.sort(Comparator.comparingInt(ReleaseReconciler::recoveryPriority));
    log.info("Private Connectivity recovery found incomplete bindings count={}", candidates.size());
    for (PrivateConnectivityConfig config : candidates) {
      try {
        if (config.getOperationType() == PrivateConnectivityOperationType.UPDATE
            || config.getStatus() == PrivateConnectivityStatus.RECONCILING) {
          privateConnectivityService.reconcileConfigIfStuck(config.getAccountIdentifier());
        } else {
          // releaseIfStuck re-validates status under the account lock so a hung setup that finished
          // as PROVISIONED after this unlocked query is not torn down (admin release() still can).
          privateConnectivityService.releaseIfStuck(config.getAccountIdentifier());
        }
      } catch (Exception e) {
        log.error("Private Connectivity recovery failed account={}", config.getAccountIdentifier(), e);
      }
    }
  }

  private static int recoveryPriority(PrivateConnectivityConfig config) {
    return switch (config.getStatus()) {
      case RELEASING -> 0;
      case RECONCILING -> 1;
      case ERROR -> 2;
      case PROVISIONING -> 3;
      default -> 4;
    };
  }
}

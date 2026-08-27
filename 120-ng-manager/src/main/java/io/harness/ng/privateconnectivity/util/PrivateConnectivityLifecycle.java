/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.util;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityOperationType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityReleasePhase;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityStatus;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;

/** Shared lifecycle transitions and retry policy for private connectivity operations. */
@OwnedBy(CI)
@UtilityClass
public class PrivateConnectivityLifecycle {
  /** Setup that stays PROVISIONING longer than this is treated as stuck. */
  public static final Duration PROVISIONING_STALE_AFTER = Duration.ofMinutes(15);

  private static final int RETRY_ALERT_THRESHOLD = 5;
  private static final int MAX_BACKOFF_EXPONENT = 7;
  private static final long INITIAL_RETRY_DELAY_SECONDS = 30L;
  private static final long MAX_RETRY_DELAY_SECONDS = 3600L;

  /**
   * Fences the current binding before release. Persisting RELEASING immediately makes the internal
   * enrollment contract unavailable; the release worker then applies deny-all before deleting WIF
   * and revoking join keys in later durable phases.
   */
  public static void beginRelease(PrivateConnectivityConfig config) {
    config.setOperationType(PrivateConnectivityOperationType.RELEASE);
    config.setReleasePhase(PrivateConnectivityReleasePhase.FENCED);
    config.setRetryCount(0);
    config.setNextRetryAt(null);
    config.setStatus(PrivateConnectivityStatus.RELEASING);
  }

  /** Returns the next retry time using exponential backoff, capped at one hour. */
  public static long nextRetryAtMillis(int failures) {
    int exponent = Math.max(0, Math.min(failures - 1, MAX_BACKOFF_EXPONENT));
    long delaySeconds = failures >= RETRY_ALERT_THRESHOLD
        ? MAX_RETRY_DELAY_SECONDS
        : Math.min(MAX_RETRY_DELAY_SECONDS, INITIAL_RETRY_DELAY_SECONDS << exponent);
    return System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(delaySeconds);
  }

  public static boolean requiresIntervention(int failures) {
    return failures >= RETRY_ALERT_THRESHOLD;
  }
}

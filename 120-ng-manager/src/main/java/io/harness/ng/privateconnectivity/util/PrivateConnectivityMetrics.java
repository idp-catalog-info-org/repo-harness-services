/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.util;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityReleasePhase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * Low-cardinality control-plane metrics. Metric names are selected only from code-owned values;
 * account, provider, operation, device and credential identifiers are never embedded in a name.
 *
 * <p>Metrics must never affect a connectivity or cleanup decision, so publisher failures are
 * deliberately isolated from the caller.
 */
@OwnedBy(CI)
@Singleton
@Slf4j
public class PrivateConnectivityMetrics {
  private static final String PREFIX = "private_connectivity_";

  private final MetricService metricService;

  @Inject
  public PrivateConnectivityMetrics(MetricService metricService) {
    this.metricService = metricService;
  }

  public void count(String event) {
    try {
      metricService.incCounter(PREFIX + event);
    } catch (RuntimeException exception) {
      log.debug("Unable to publish Private Connectivity metric event={}", event, exception);
    }
  }

  public void releaseFailure(PrivateConnectivityReleasePhase completedPhase, boolean retryThresholdReached) {
    int failedPhaseIndex = completedPhase == null
        ? 0
        : Math.min(completedPhase.ordinal() + 1, PrivateConnectivityReleasePhase.UNBOUND.ordinal());
    String phase = PrivateConnectivityReleasePhase.values()[failedPhaseIndex].name().toLowerCase(Locale.ROOT);
    count("release_failure_" + phase);
    if (retryThresholdReached) {
      count("release_retry_threshold");
    }
  }
}

/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

/**
 * Redis key derivation for step-concurrency counters. Two shapes: a single cluster counter and
 * one counter per active account.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class StepConcurrencyCounterKey {
  private static final String PREFIX = "step_concurrency:";
  public static final String CLUSTER_KEY = PREFIX + "cluster";
  private static final String ACCOUNT_PREFIX = PREFIX + "account:";

  public static String forAccount(String accountId) {
    return ACCOUNT_PREFIX + accountId;
  }

  public static String forCluster() {
    return CLUSTER_KEY;
  }

  /** Glob pattern matching every per-account counter key, for SCAN-based enumeration. */
  public static String accountKeyPattern() {
    return ACCOUNT_PREFIX + "*";
  }

  /** Inverse of {@link #forAccount(String)} — recovers the accountId from a scanned key. */
  public static String accountIdFromKey(String key) {
    return key.substring(ACCOUNT_PREFIX.length());
  }
}

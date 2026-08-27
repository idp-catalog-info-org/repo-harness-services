/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PlanConcurrencyCounterKey {
  private static final String PREFIX = "plan_concurrency:";
  private static final String ACCOUNT_PREFIX = PREFIX + "account:";
  private static final String PROJECT_PREFIX = PREFIX + "project:";
  private static final String SCOPE_SEPARATOR = "/";

  public static String forAccount(String accountId) {
    return ACCOUNT_PREFIX + accountId;
  }

  public static String forProject(String accountId, String parentUniqueId) {
    return PROJECT_PREFIX + projectScope(accountId, parentUniqueId);
  }

  public static String projectScope(String accountId, String parentUniqueId) {
    return nullToEmpty(accountId) + SCOPE_SEPARATOR + nullToEmpty(parentUniqueId);
  }

  public static String accountKeyPattern() {
    return ACCOUNT_PREFIX + "*";
  }

  public static String projectKeyPattern() {
    return PROJECT_PREFIX + "*";
  }

  /** Glob matching only the project keys of a single account: {@code plan_concurrency:project:<accountId>/*}. */
  public static String projectKeyPatternForAccount(String accountId) {
    return PROJECT_PREFIX + nullToEmpty(accountId) + SCOPE_SEPARATOR + "*";
  }

  /** Extracts the {@code parentUniqueId} from a project key, given its owning {@code accountId}. */
  public static String parentUniqueIdFromProjectKey(String accountId, String key) {
    return key.substring((PROJECT_PREFIX + nullToEmpty(accountId) + SCOPE_SEPARATOR).length());
  }

  public static String accountIdFromKey(String key) {
    return key.substring(ACCOUNT_PREFIX.length());
  }

  public static String projectScopeFromKey(String key) {
    return key.substring(PROJECT_PREFIX.length());
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}

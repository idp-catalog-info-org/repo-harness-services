/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

/**
 * MAX_CONCURRENCY_REACHED: Indicates that the maximum concurrency limit for the pipeline for given account has been
 * reached,
 *
 * MAX_CONCURRENCY_NOT_REACHED: Indicates that the maximum concurrency limit for the pipeline has not been reached and
 * execution will start in sometime
 *
 * PRIORITY_CONCURRENCY_REACHED: Indicates that the maximum concurrency limit for the pipeline for given priority type
 * has been reached.
 *
 * PROJECT_CONCURRENCY_REACHED: Indicates that the maximum concurrency limit for the pipeline for given project has
 * been reached.
 */
public enum QueuedType {
  MAX_CONCURRENCY_REACHED("Max number of concurrent executions reached for the account"),
  MAX_CONCURRENCY_NOT_REACHED("Execution Loading ..."),
  PRIORITY_CONCURRENCY_REACHED("Max number of concurrent executions reached for %s priority type"),
  PROJECT_CONCURRENCY_REACHED("Max number of concurrent executions reached for the project");

  private final String queuedReason;

  QueuedType(String queuedReason) {
    this.queuedReason = queuedReason;
  }

  public String getQueuedReason() {
    return queuedReason;
  }
}

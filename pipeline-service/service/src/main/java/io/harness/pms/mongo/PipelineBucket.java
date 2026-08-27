/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.mongo;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.springdata.QueryBucket;

/**
 * pipeline-service's identity-only Mongo query-budget buckets (PIPE-35957). Each constant is just a stable
 * {@link #value()} key; the actual budget is read from {@code MongoConfig.queryBudget.budgets} (see {@code
 * config.yml}). A query that opts into no bucket keeps the per-service backstop, so enabling the framework never
 * tightens an unreviewed query.
 */
@OwnedBy(PIPELINE)
public enum PipelineBucket implements QueryBucket {
  /** Request/interactive-path reads and single-document writes; ordinary latency-sensitive OLTP work. */
  FAST(Keys.FAST),
  /** Jobs, streams, bulk/multi-document writes, project-wide bulk removals, migrations -- given more room to run. */
  SLOW(Keys.SLOW);

  private final String key;

  PipelineBucket(String key) {
    this.key = key;
  }

  @Override
  public String value() {
    return key;
  }

  /** Compile-time-constant keys so the {@code @QueryBudget}/{@code @Budget} annotations can reference them. */
  public static final class Keys {
    public static final String FAST = "FAST";
    public static final String SLOW = "SLOW";

    private Keys() {}
  }
}

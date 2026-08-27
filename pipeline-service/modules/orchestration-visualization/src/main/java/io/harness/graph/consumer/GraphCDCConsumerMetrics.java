/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.consumer;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

/**
 * Metric name constants for {@link GraphCDCConsumer}.
 *
 * <p>All metrics follow the {@code cdc_pg_*} naming convention and are recorded via
 * {@link io.harness.metrics.service.api.MetricService#recordMetric}.
 *
 * <ul>
 *   <li>{@code cdc_pg_batch_processing_duration_ms} — histogram: parse + DB flush time per poll batch</li>
 *   <li>{@code cdc_pg_records_processed_total} — counter: total records processed, labeled by collection +
 * operation</li> <li>{@code cdc_pg_batch_size} — histogram: number of Kafka records per poll batch</li> <li>{@code
 * cdc_pg_vertices_per_batch} — histogram: unique vertices accumulated per batch</li>
 * </ul>
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class GraphCDCConsumerMetrics {
  /** End-to-end time per Kafka poll batch (parse + DB flush), in milliseconds. */
  public static final String BATCH_PROCESSING_DURATION_MS = "cdc_pg_batch_processing_duration_ms";

  /** Total records processed, labeled by {@code collection} and {@code operation} (create/update). */
  public static final String RECORDS_PROCESSED_TOTAL = "cdc_pg_records_processed_total";

  /** Number of Kafka records received per poll batch. */
  public static final String BATCH_SIZE = "cdc_pg_batch_size";

  /** Unique vertices accumulated per batch (measures accumulation effectiveness). */
  public static final String VERTICES_PER_BATCH = "cdc_pg_vertices_per_batch";
}

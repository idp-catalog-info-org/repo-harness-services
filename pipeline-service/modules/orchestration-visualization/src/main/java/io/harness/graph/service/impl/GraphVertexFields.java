/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.time.OffsetDateTime;
import lombok.experimental.UtilityClass;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.DSL;

/**
 * Constants for graph_vertex table schema.
 * Centralizes all JOOQ field definitions for consistent access across services.
 */
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class GraphVertexFields {
  // Table reference
  public static final Table<?> TABLE = DSL.table("graph_vertex");

  // Primary key and identifiers
  public static final Field<String> NODE_EXECUTION_ID = DSL.field("node_execution_id", String.class);
  public static final Field<String> PLAN_EXECUTION_ID = DSL.field("plan_execution_id", String.class);
  public static final Field<String> ACCOUNT_IDENTIFIER = DSL.field("account_identifier", String.class);
  public static final Field<String> PLAN_NODE_ID = DSL.field("plan_node_id", String.class);

  // Identity columns
  public static final Field<String> IDENTIFIER = DSL.field("identifier", String.class);
  public static final Field<String> NAME = DSL.field("name", String.class);
  public static final Field<String> STEP_TYPE = DSL.field("step_type", String.class);
  public static final Field<String> NODE_GROUP = DSL.field("node_group", String.class);

  // Hierarchy and adjacency
  public static final Field<String> PARENT_ID = DSL.field("parent_id", String.class);
  public static final Field<String> PREVIOUS_ID = DSL.field("previous_id", String.class);
  public static final Field<String> NEXT_ID = DSL.field("next_id", String.class);

  // Status and timing
  public static final Field<String> STATUS = DSL.field("status", String.class);
  public static final Field<Long> START_TS = DSL.field("start_ts", Long.class);
  public static final Field<Long> END_TS = DSL.field("end_ts", Long.class);
  public static final Field<Long> LAST_UPDATED_AT = DSL.field("last_updated_at", Long.class);
  public static final Field<Long> CREATED_AT = DSL.field("created_at", Long.class);

  // Execution details
  public static final Field<String> EXECUTION_MODE = DSL.field("execution_mode", String.class);
  public static final Field<Boolean> EXECUTION_INPUT_CONFIGURED =
      DSL.field("execution_input_configured", Boolean.class);
  public static final Field<String> LOG_BASE_KEY = DSL.field("log_base_key", String.class);
  public static final Field<Long> INITIAL_WAIT_DURATION_MS = DSL.field("initial_wait_duration_ms", Long.class);

  // Step parameters
  public static final Field<JSONB> STEP_PARAMETERS = DSL.field("step_parameters", JSONB.class);
  public static final Field<Integer> STEP_PARAMETERS_VERSION = DSL.field("step_parameters_version", Integer.class);

  // JSONB payload fields
  public static final Field<JSONB> OUTCOME_DOCUMENTS = DSL.field("outcome_documents", JSONB.class);
  public static final Field<JSONB> STEP_DETAILS = DSL.field("step_details", JSONB.class);
  public static final Field<JSONB> FAILURE_INFO = DSL.field("failure_info", JSONB.class);
  public static final Field<JSONB> NODE_RUN_INFO = DSL.field("node_run_info", JSONB.class);
  public static final Field<JSONB> SKIP_INFO = DSL.field("skip_info", JSONB.class);
  public static final Field<JSONB> PROGRESS_DATA = DSL.field("progress_data", JSONB.class);
  public static final Field<JSONB> UNIT_PROGRESSES = DSL.field("unit_progresses", JSONB.class);
  public static final Field<JSONB> EXECUTABLE_RESPONSES = DSL.field("executable_responses", JSONB.class);
  public static final Field<JSONB> INTERRUPT_HISTORIES = DSL.field("interrupt_histories", JSONB.class);
  public static final Field<JSONB> ADVISER_RESPONSE = DSL.field("adviser_response", JSONB.class);

  // Strategy fields
  public static final Field<JSONB> STRATEGY_METADATA = DSL.field("strategy_metadata", JSONB.class);
  public static final Field<String> STRATEGY_TYPE = DSL.field("strategy_type", String.class);
  public static final Field<String> BASE_FQN = DSL.field("base_fqn", String.class);
  public static final Field<JSONB> CURRENT_LEVEL = DSL.field("current_level", JSONB.class);

  // Skip handling
  public static final Field<String> SKIP_TYPE = DSL.field("skip_type", String.class);

  // Retry handling
  public static final Field<String[]> RETRY_IDS = DSL.field("retry_ids", String[].class);
  public static final Field<Boolean> OLD_RETRY = DSL.field("old_retry", Boolean.class);
  public static final Field<JSONB> RETRY_NODE_METADATA = DSL.field("retry_node_metadata", JSONB.class);

  // Module info
  public static final Field<String> MODULE = DSL.field("module", String.class);
  public static final Field<JSONB> MODULE_INFO = DSL.field("module_info", JSONB.class);

  // Derived flags for layout nodes
  public static final Field<Boolean> HAS_BARRIER_CHILD = DSL.field("has_barrier_child", Boolean.class);

  // UI display fields
  public static final Field<Long> CHILDREN_COUNT = DSL.field("children_count", Long.class);

  // CDC document ID tracking for secondary collections
  public static final Field<String> NODE_EXECUTIONS_INFO_ID = DSL.field("node_executions_info_id", String.class);
  public static final Field<String[]> OUTCOME_INSTANCE_IDS = DSL.field("outcome_instance_ids", String[].class);
  public static final Field<String[]> GRAPH_UPDATE_INFO_IDS = DSL.field("graph_update_info_ids", String[].class);

  // TTL
  public static final Field<OffsetDateTime> VALID_UNTIL = DSL.field("valid_until", OffsetDateTime.class);
}

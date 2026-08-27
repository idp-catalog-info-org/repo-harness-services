/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service.impl;

import static io.harness.graph.service.impl.GraphVertexFields.ADVISER_RESPONSE;
import static io.harness.graph.service.impl.GraphVertexFields.BASE_FQN;
import static io.harness.graph.service.impl.GraphVertexFields.CHILDREN_COUNT;
import static io.harness.graph.service.impl.GraphVertexFields.CREATED_AT;
import static io.harness.graph.service.impl.GraphVertexFields.CURRENT_LEVEL;
import static io.harness.graph.service.impl.GraphVertexFields.END_TS;
import static io.harness.graph.service.impl.GraphVertexFields.EXECUTABLE_RESPONSES;
import static io.harness.graph.service.impl.GraphVertexFields.EXECUTION_INPUT_CONFIGURED;
import static io.harness.graph.service.impl.GraphVertexFields.EXECUTION_MODE;
import static io.harness.graph.service.impl.GraphVertexFields.FAILURE_INFO;
import static io.harness.graph.service.impl.GraphVertexFields.IDENTIFIER;
import static io.harness.graph.service.impl.GraphVertexFields.INITIAL_WAIT_DURATION_MS;
import static io.harness.graph.service.impl.GraphVertexFields.INTERRUPT_HISTORIES;
import static io.harness.graph.service.impl.GraphVertexFields.LAST_UPDATED_AT;
import static io.harness.graph.service.impl.GraphVertexFields.LOG_BASE_KEY;
import static io.harness.graph.service.impl.GraphVertexFields.NAME;
import static io.harness.graph.service.impl.GraphVertexFields.NODE_EXECUTION_ID;
import static io.harness.graph.service.impl.GraphVertexFields.NODE_RUN_INFO;
import static io.harness.graph.service.impl.GraphVertexFields.OUTCOME_DOCUMENTS;
import static io.harness.graph.service.impl.GraphVertexFields.PLAN_NODE_ID;
import static io.harness.graph.service.impl.GraphVertexFields.PROGRESS_DATA;
import static io.harness.graph.service.impl.GraphVertexFields.RETRY_IDS;
import static io.harness.graph.service.impl.GraphVertexFields.RETRY_NODE_METADATA;
import static io.harness.graph.service.impl.GraphVertexFields.SKIP_INFO;
import static io.harness.graph.service.impl.GraphVertexFields.SKIP_TYPE;
import static io.harness.graph.service.impl.GraphVertexFields.START_TS;
import static io.harness.graph.service.impl.GraphVertexFields.STATUS;
import static io.harness.graph.service.impl.GraphVertexFields.STEP_DETAILS;
import static io.harness.graph.service.impl.GraphVertexFields.STEP_PARAMETERS;
import static io.harness.graph.service.impl.GraphVertexFields.STEP_PARAMETERS_VERSION;
import static io.harness.graph.service.impl.GraphVertexFields.STEP_TYPE;
import static io.harness.graph.service.impl.GraphVertexFields.STRATEGY_METADATA;
import static io.harness.graph.service.impl.GraphVertexFields.UNIT_PROGRESSES;
import static io.harness.graph.service.impl.JsonbParserUtils.parse;
import static io.harness.graph.service.impl.JsonbParserUtils.parseInterruptHistories;
import static io.harness.graph.service.impl.JsonbParserUtils.parseOutcomeDocuments;
import static io.harness.graph.service.impl.JsonbParserUtils.parseProto;
import static io.harness.graph.service.impl.JsonbParserUtils.parseProtoList;
import static io.harness.graph.service.impl.JsonbParserUtils.parseRetryNodeMetadata;
import static io.harness.graph.service.impl.JsonbParserUtils.parseStepDetails;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.GraphVertex;
import io.harness.beans.GraphVertex.GraphVertexBuilder;
import io.harness.logging.UnitProgress;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.stepparameters.PmsStepParameters;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.JSONB;
import org.jooq.Record;

/**
 * Mapper for converting JOOQ Records to GraphVertex objects.
 * Handles parsing of scalar fields, JSONB fields, and protobuf types.
 */
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class GraphVertexMapper {
  /**
   * Convert a JOOQ Record to a GraphVertex.
   *
   * @param record the database record from graph_vertex table
   * @return the mapped GraphVertex
   */
  public static GraphVertex fromRecord(Record record) {
    // Parse scalar fields
    String statusStr = record.get(STATUS);
    String modeStr = record.get(EXECUTION_MODE);
    String skipTypeStr = record.get(SKIP_TYPE);
    Long initialWaitDurationMs = record.get(INITIAL_WAIT_DURATION_MS);

    // Build GraphVertex from normalized columns
    GraphVertexBuilder builder =
        GraphVertex.builder()
            .uuid(record.get(NODE_EXECUTION_ID))
            .planNodeId(record.get(PLAN_NODE_ID))
            .identifier(record.get(IDENTIFIER))
            .name(record.get(NAME))
            .stepType(record.get(STEP_TYPE))
            .status(statusStr != null ? Status.valueOf(statusStr) : null)
            .createdAt(record.get(CREATED_AT))
            .startTs(record.get(START_TS))
            .endTs(record.get(END_TS))
            .lastUpdatedAt(record.get(LAST_UPDATED_AT))
            .baseFqn(record.get(BASE_FQN))
            .mode(modeStr != null ? ExecutionMode.valueOf(modeStr) : null)
            .skipType(skipTypeStr != null ? SkipType.valueOf(skipTypeStr) : null)
            .executionInputConfigured(record.get(EXECUTION_INPUT_CONFIGURED))
            .logBaseKey(record.get(LOG_BASE_KEY))
            .stepParametersVersion(record.get(STEP_PARAMETERS_VERSION))
            // childrenCount: NULL in DB means "not yet populated by CDC" (pre-migration rows)
            // Map to 0 to match NodeExecution's @Builder.Default behavior
            .childrenCount(record.get(CHILDREN_COUNT) != null ? record.get(CHILDREN_COUNT) : 0L);

    // Parse Duration
    if (initialWaitDurationMs != null) {
      builder.initialWaitDuration(Duration.ofMillis(initialWaitDurationMs));
    }

    // Parse retry_ids array
    String[] retryIdsArray = record.get(RETRY_IDS);
    if (retryIdsArray != null && retryIdsArray.length > 0) {
      builder.retryIds(Arrays.asList(retryIdsArray));
    }

    // Parse retry node metadata (contains protobuf ExecutionTriggerInfo)
    builder.retryNodeMetadata(parseRetryNodeMetadata(record.get(RETRY_NODE_METADATA)));

    // Parse JSONB fields (non-protobuf types use Jackson)
    builder.stepParameters(parse(record.get(STEP_PARAMETERS), PmsStepParameters.class));
    builder.outcomeDocuments(parseOutcomeDocuments(record.get(OUTCOME_DOCUMENTS)));
    builder.stepDetails(parseStepDetails(record.get(STEP_DETAILS)));
    builder.progressData(parse(record.get(PROGRESS_DATA), OrchestrationMap.class));

    // Parse protobuf types using JsonFormat
    builder.failureInfo(parseProto(record.get(FAILURE_INFO), FailureInfo.getDefaultInstance()));
    builder.nodeRunInfo(parseProto(record.get(NODE_RUN_INFO), NodeRunInfo.getDefaultInstance()));
    builder.skipInfo(parseProto(record.get(SKIP_INFO), SkipInfo.getDefaultInstance()));
    builder.unitProgresses(parseProtoList(record.get(UNIT_PROGRESSES), UnitProgress.getDefaultInstance()));
    builder.executableResponses(
        parseProtoList(record.get(EXECUTABLE_RESPONSES), ExecutableResponse.getDefaultInstance()));
    builder.strategyMetadata(parseProto(record.get(STRATEGY_METADATA), StrategyMetadata.getDefaultInstance()));
    builder.currentLevel(parseProto(record.get(CURRENT_LEVEL), Level.getDefaultInstance()));

    // InterruptEffect is a Java class with protobuf fields
    builder.interruptHistories(parseInterruptHistories(record.get(INTERRUPT_HISTORIES)));

    // Extract manualInterventionAvailableActions from adviserResponse
    builder.manualInterventionAvailableActions(extractManualInterventionActions(record.get(ADVISER_RESPONSE)));

    return builder.build();
  }

  /**
   * Extract manual intervention available actions from adviserResponse.
   * Matches legacy NodeExecutionContextUtils.getManualInterventionAvailableActions logic.
   */
  private static List<RepairActionCode> extractManualInterventionActions(JSONB adviserResponseJsonb) {
    if (adviserResponseJsonb == null || adviserResponseJsonb.data() == null || adviserResponseJsonb.data().isEmpty()) {
      return new ArrayList<>();
    }

    try {
      AdviserResponse adviserResponse = parseProto(adviserResponseJsonb, AdviserResponse.getDefaultInstance());
      if (adviserResponse != null && adviserResponse.hasInterventionWaitAdvise()) {
        // Return protobuf RepairActionCode list directly (GraphVertexDTO uses protobuf enum)
        return new ArrayList<>(adviserResponse.getInterventionWaitAdvise().getAvailableActionsList());
      }
    } catch (Exception e) {
      log.debug("Failed to parse adviserResponse for manual intervention actions: {}", e.getMessage());
    }

    return new ArrayList<>();
  }
}

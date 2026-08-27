/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.entity.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.plan.execution.PipelineExecutionSummaryKeys;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;
import io.harness.search.entity.beans.PipelineSearchReadExecutionSummaryDTO.PipelineSearchReadExecutionSummaryDTOKeys;

import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PipelineExecutionElasticSearchConstants {
  public static final String PMS_EXECUTION_ALIAS_6_MONTH_POLICY = "pms-execution-alias-6-month-policy";
  public static final String PMS_EXECUTION_ALIAS_12_MONTH_POLICY = "pms-execution-alias-12-month-policy";
  public static final String PMS_EXECUTION_ALIAS_24_MONTH_POLICY = "pms-execution-alias-24-month-policy";

  public static final String PMS_EXECUTION_ALIAS_6_MONTH_POLICY_FILE_PATH =
      "elasticsearch/pms-execution-alias-6-month-policy.json";
  public static final String PMS_EXECUTION_ALIAS_12_MONTH_POLICY_FILE_PATH =
      "elasticsearch/pms-execution-alias-12-month-policy.json";
  public static final String PMS_EXECUTION_ALIAS_24_MONTH_POLICY_FILE_PATH =
      "elasticsearch/pms-execution-alias-24-month-policy.json";

  public static final String PMS_EXECUTION_ENTITY_MAPPINGS_JSON_FILE_PATH =
      "elasticsearch/pms-execution-entity-mappings-v1.json";
  public static final String PMS_EXECUTION_ALIAS_SETTINGS_JSON_FILE_PATH =
      "elasticsearch/pms-execution-alias-6-month-settings.json";

  public static final String PMS_EXECUTION_ALIAS_6_MONTH_INDEX_TEMPLATE = "pms-execution-alias-6-month-template";

  public static final String PMS_RUNNING_EXECUTIONS_INDEX = "pms-running-executions";
  public static final String PMS_EXECUTION_ALIAS_6_MONTH = "pms-execution-alias-6-month";

  public static final Set<String> ELASTIC_SEARCH_FIELDS_LEAF_FIELDS =
      Set.of(PipelineExecutionSummaryKeys.uuid, PipelineExecutionSummaryKeys.runSequence,
          PipelineExecutionSummaryKeys.accountId, PipelineExecutionSummaryKeys.orgIdentifier,
          PipelineExecutionSummaryKeys.projectIdentifier, PipelineExecutionSummaryKeys.pipelineIdentifier,
          PipelineExecutionSummaryKeys.planExecutionId, PipelineExecutionSummaryKeys.name,
          PipelineExecutionSummaryKeys.status, PipelineExecutionSummaryKeys.startTs, PipelineExecutionSummaryKeys.endTs,
          PipelineExecutionSummaryKeys.modules, PipelineExecutionSummaryKeys.executionMode,
          PipelineExecutionSummaryKeys.createdAt, PipelineExecutionSummaryKeys.tags,
          PipelineExecutionSummaryKeys.labels, PipelineExecutionSummaryKeys.inputSetIdentifiers,
          PipelineExecutionSummaryKeys.notes, PipelineExecutionSummaryKeys.pipelineTimeoutTs);

  public static final Set<String> ELASTIC_SEARCH_PARENT_FIELDS = Set.of(PipelineExecutionSummaryKeys.parentStageInfo,
      PipelineExecutionSummaryKeys.entityGitDetails, PipelineExecutionSummaryKeys.executionTriggerInfo,
      PipelineExecutionSummaryKeys.moduleInfo, PipelineExecutionSummaryKeys.retryExecutionMetadata);

  public static final Set<String> ELASTIC_SEARCH_RACE_CONDITION_FIELDS =
      Set.of(PipelineExecutionSummaryKeys.moduleInfo);

  public static final Set<String> PIPELINE_SEARCH_EXECUTION_SUMMARY_DTO_ALL_FIELDS = Set.of(
      PipelineSearchExecutionSummaryDTOKeys.uuid, PipelineSearchExecutionSummaryDTOKeys.runSequence,
      PipelineSearchExecutionSummaryDTOKeys.accountId, PipelineSearchExecutionSummaryDTOKeys.orgIdentifier,
      PipelineSearchExecutionSummaryDTOKeys.projectIdentifier, PipelineSearchExecutionSummaryDTOKeys.pipelineIdentifier,
      PipelineSearchExecutionSummaryDTOKeys.planExecutionId, PipelineSearchExecutionSummaryDTOKeys.name,
      PipelineSearchExecutionSummaryDTOKeys.status, PipelineSearchExecutionSummaryDTOKeys.tags,
      PipelineSearchExecutionSummaryDTOKeys.labels, PipelineSearchExecutionSummaryDTOKeys.startTs,
      PipelineSearchExecutionSummaryDTOKeys.endTs, PipelineSearchExecutionSummaryDTOKeys.entityGitDetails,
      PipelineSearchExecutionSummaryDTOKeys.modules, PipelineSearchExecutionSummaryDTOKeys.cdModuleInfo,
      PipelineSearchExecutionSummaryDTOKeys.ciModuleInfo, PipelineSearchExecutionSummaryDTOKeys.executionMode,
      PipelineSearchExecutionSummaryDTOKeys.isChildPipeline, PipelineSearchExecutionSummaryDTOKeys.triggerType,
      PipelineSearchExecutionSummaryDTOKeys.triggeredBy, PipelineSearchExecutionSummaryDTOKeys.createdAt,
      PipelineSearchExecutionSummaryDTOKeys.retryExecutionMetadata, PipelineSearchExecutionSummaryDTOKeys.isDeleted,
      PipelineSearchExecutionSummaryDTOKeys.inputSetIdentifiers, PipelineSearchExecutionSummaryDTOKeys.notes,
      PipelineSearchExecutionSummaryDTOKeys.pipelineTimeoutTs);

  public static final Set<String> PIPELINE_SEARCH_READ_EXECUTION_SUMMARY_DTO_ALL_FIELDS =
      Set.of(PipelineSearchReadExecutionSummaryDTOKeys.runSequence, PipelineSearchReadExecutionSummaryDTOKeys.startTs,
          PipelineSearchReadExecutionSummaryDTOKeys.endTs, PipelineSearchReadExecutionSummaryDTOKeys.status,
          PipelineSearchReadExecutionSummaryDTOKeys.planExecutionId);

  public static final int PIPELINE_SEARCH_MAX_BATCH_SIZE = 1000;
}

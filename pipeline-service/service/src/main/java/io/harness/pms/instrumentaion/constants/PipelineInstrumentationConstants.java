/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.instrumentaion.constants;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineInstrumentationConstants {
  public static String PIPELINE_ID = "pipelineIdentifier";
  public static String PIPELINE = "pipeline";
  public static String PIPELINE_VERSION = "pipeline_version";
  public static String TEMPLATE = "template";
  public static String TEMPLATE_INPUTS = "templateInputs";
  public static String PROJECT_IDENTIFIER = "projectId";
  public static String ORG_IDENTIFIER = "orgIdentifier";
  public static String PARENT_UNIQUE_IDENTIFIER = "parentUniqueId";
  public static String ORGANIZATION_ID = "org_id";
  public static String MODULE_NAME = "moduleName";
  public static String FLOW_CONTROL = "flowControl";
  public static String BARRIERS = "barriers";
  public static String REPEAT = "repeat";
  public static String MATRIX = "matrix";
  public static String PARALLELISM = "parallelism";
  public static String PLAN_EXECUTION_ID = "planExecutionId";
  public static String NODE_EXECUTION_ID = "nodeExecutionId";
  public static String INTERRUPT_TYPE = "interruptType";
  public static String NOTIFICATION_RULES = "notificationRules";
  public static String PIPELINE_EXECUTION = "ng_pipeline_execution";
  public static String PIPELINE_NOTIFICATION = "ng_pipeline_notification";
  public static String PIPELINE_WAIT_EVENT = "ng_pipeline_wait";
  public static String WAIT_STEP_ACTION = "waitStepAction";
  public static String EXECUTION_TIME = "execution_time";
  public static String LEVEL = "level";
  public static String STAGES = "stages";
  public static String STAGE = "stage";
  public static String INSERT = "insert";
  public static String SPEC = "spec";
  public static String SERVICES = "services";
  public static String ENVIRONMENTS = "environments";
  public static String EXECUTION = "execution";
  public static String STEPS = "steps";
  public static String STEP = "step";
  public static String STEP_GROUP = "stepGroup";
  public static String STRATEGY = "strategy";
  public static String FAILURE_STRATEGIES = "failureStrategies";
  public static String STAGE_TYPES = "stage_types";
  public static String STEP_TYPES = "step_types";
  public static String FAILED_STEPS = "failed_steps";
  public static String FAILED_STEP_TYPES = "failed_step_types";
  public static String TRIGGER_TYPE = "trigger_type";
  public static String STATUS = "status";
  public static String IS_RERUN = "is_rerun";
  public static String CONDITIONAL_EXECUTION = "conditional_execution";
  public static String STAGE_COUNT = "stage_count";
  public static String STEP_COUNT = "step_count";
  public static String NOTIFICATION_METHODS = "notification_methods";
  public static String NOTIFICATION_RULES_COUNT = "notification_rules_count";
  public static String PIPELINE_INTERRUPT_EVENT = "ng_pipeline_interrupt";
  public static String EVENT_TYPES = "events_types";
  public static String FAILURE_TYPES = "failure_types";
  public static String ERROR_MESSAGES = "error_messages";
  public static String EXCEPTION_MESSAGE = "exception_message";
  public static String ACCOUNT_NAME = "account_name";
  public static String ACCOUNT_ID = "account_id";
  public static String INPUT_SET_SAVE = "input_set_save";
  public static String INPUT_SET_NAME = "input_set_name";
  public static String ORG_ID = "orgId";
  public static String PROJECT_ID = "projectId";
  public static String INPUT_SET_SAVE_ACTION = "action";
  public static String PARALLEL = "parallel";
  public static String TYPE = "type";
  public static String IS_GITX = "is_gitx";
  public static String HAS_STEP_GROUP = "has_step_group";
  public static String HAS_COMMON_STEPS = "has_common_steps";
  public static String HAS_BARRIER = "has_barrier";
  public static String HAS_LOOPING_STRATEGY = "has_looping_strategy";
  public static String HAS_FAILURE_STRATEGY = "has_failure_strategy";
  public static String LOOPING_STRATEGY_TYPES = "looping_strategy_types";
  public static String LOOPING_STRATEGY_LEVELS = "looping_strategy_levels";
  public static String FAILURE_STRATEGY_LEVELS = "failure_strategy_levels";
  public static String HAS_TEMPLATE = "has_template";
  public static String TEMPLATE_IDS = "template_ids";
  public static String STAGES_PROPERTY = "stages";
  public static String HAS_STEPS_INSERT = "has_steps_insert";
  public static String HAS_STAGES_INSERT = "has_stages_insert";
  public static String HARNESS_PIPELINE_ANNOTATIONS_USED = "harness_pipeline_annotations_used";
  public static String STAGE_EXECUTION_ID = "stageExecutionId";
}

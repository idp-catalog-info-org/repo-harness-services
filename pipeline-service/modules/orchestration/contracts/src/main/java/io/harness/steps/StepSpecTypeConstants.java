/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;

@OwnedBy(PIPELINE)
public interface StepSpecTypeConstants {
  String SHELL_SCRIPT = "ShellScript";
  String BARRIER = "Barrier";
  String HTTP = "Http";
  String IDP_ACTION = "IdpAction";
  String CUSTOM_APPROVAL = "CustomApproval";
  String HARNESS_APPROVAL = "HarnessApproval";
  String JIRA_APPROVAL = "JiraApproval";
  String JIRA_CREATE = "JiraCreate";
  String JIRA_UPDATE = "JiraUpdate";
  String RESOURCE_CONSTRAINT = "ResourceConstraint";
  String QUEUE = "Queue";
  String FLAG_CONFIGURATION = "FlagConfiguration";
  String FME_FLAG_UPDATE = "FmeFlagUpdate";
  String FME_FLAG_CREATE = "FmeFlagCreate";
  String FME_SEGMENT_CREATE = "FmeSegmentCreate";
  String FME_FLAG_DEFAULT_ALLOCATION = "FmeFlagDefaultAllocation";
  String FME_FLAG_SET_TARGETS = "FmeFlagSetIndividualTargets";
  String FME_FLAG_ADD_REMOVE_TARGETS = "FmeFlagAddRemoveIndividualTargets";
  String FME_FLAG_ARCHIVE = "FmeFlagArchive";
  String FME_METRIC_CHECK = "FmeMetricCheck";
  String FME_FLAG_DELETE = "FmeFlagDelete";
  String FME_CONFIGURE_FLAG_TREATMENTS = "FmeConfigureFlagTreatments";
  String FME_FLAG_KILL = "FmeFlagKill";
  String FME_FLAG_LIMIT_EXPOSURE = "FmeFlagLimitExposure";
  String FME_FLAG_DEFINITION_INSTRUCTIONS = "FmeFlagDefinitionInstructions";
  String FME_FLAG_PATCH_DEFINITION = "FmeFlagPatchDefinition";
  String FME_FLAG_REALLOCATE_TRAFFIC = "FmeFlagReallocateTraffic";
  String FME_FLAG_RESTORE = "FmeFlagRestore";
  String FME_FLAG_SET_DYNAMIC_CONFIGURATIONS = "FmeFlagSetDynamicConfigurations";
  String FME_FLAG_SET_TARGETING_RULES = "FmeFlagSetTargetingRules";
  String FME_FLAG_SET_TREATMENTS = "FmeFlagSetTreatments";
  String FME_SEGMENT_UPDATE = "FmeSegmentUpdate";
  String FME_SEGMENT_DELETE = "FmeSegmentDelete";
  String FME_FLAGSET_CREATE = "FmeFlagsetCreate";
  String FME_FLAGSET_DELETE = "FmeFlagsetDelete";
  String FME_FLAG_ADD_REMOVE_FLAGSETS = "FmeFlagAddRemoveFlagsets";
  String FME_FLAG_SET_IMPRESSION_TRACKING = "FmeFlagSetImpressionTracking";
  String FME_SEGMENT_ADD_REMOVE_TARGETS = "FmeSegmentAddRemoveTargets";
  String FME_SEGMENT_SET_TARGETING_RULES = "FmeSegmentSetTargetingRules";
  String AISRE_CREATE_INCIDENT = "AISRE_CreateIncident";
  String AISRE_CREATE_ALERT = "AISRE_CreateAlert";
  String SERVICENOW_APPROVAL = "ServiceNowApproval";
  String SERVICENOW_CREATE = "ServiceNowCreate";
  String SERVICENOW_UPDATE = "ServiceNowUpdate";
  String SERVICENOW_IMPORT_SET = "ServiceNowImportSet";
  String APPROVAL_STAGE = "Approval";
  String PIPELINE_STAGE = "Pipeline";
  String DYNAMIC_STAGE = "Dynamic";
  String PIPELINE_ROLLBACK_STAGE = "PipelineRollback";
  String FEATURE_FLAG_STAGE = "FeatureFlag";
  String POLICY_STEP = "Policy";
  String CHANGE_ADVISOR = "ChangeAdvisor";
  String RO_NOTIFY = "RONotify";
  String EMAIL = "Email";
  String WAIT_STEP = "Wait";
  String EVENT_LISTENER = "EventListener";
  String UPLOAD = "FilesUpload";
  String INIT_CONTAINER_STEP = "InitContainer";
  String RUN_CONTAINER_STEP = "RunContainer";
  String INIT_CONTAINER_STEP_V2 = "InitializeContainer";
  //  String INIT_CONTAINER_STEP_V2 = "InitContainer";
  String OPA_EVALUATION = "OPAEvaluation";
  String OPA_EVALUATION_AGGREGATOR = "OPAEvaluationAggregator";

  String APPROVAL_FACILITATOR = "APPROVAL_FACILITATOR";
  String RESOURCE_RESTRAINT_FACILITATOR_TYPE = "RESOURCE_RESTRAINT";

  StepType BARRIER_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.BARRIER).setStepCategory(StepCategory.STEP).build();
  StepType HTTP_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.HTTP).setStepCategory(StepCategory.STEP).build();
  StepType IDP_ACTION_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.IDP_ACTION).setStepCategory(StepCategory.STEP).build();
  StepType FLAG_CONFIGURATION_STEP_TYPE = StepType.newBuilder()
                                              .setType(StepSpecTypeConstants.FLAG_CONFIGURATION)
                                              .setStepCategory(StepCategory.STEP)
                                              .build();
  StepType FME_FLAG_ARCHIVE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.FME_FLAG_ARCHIVE).setStepCategory(StepCategory.STEP).build();
  StepType FME_METRIC_CHECK_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.FME_METRIC_CHECK).setStepCategory(StepCategory.STEP).build();
  StepType FME_FLAG_DELETE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.FME_FLAG_DELETE).setStepCategory(StepCategory.STEP).build();
  StepType FME_FLAG_KILL_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.FME_FLAG_KILL).setStepCategory(StepCategory.STEP).build();
  StepType FME_FLAG_LIMIT_EXPOSURE_STEP_TYPE = StepType.newBuilder()
                                                   .setType(StepSpecTypeConstants.FME_FLAG_LIMIT_EXPOSURE)
                                                   .setStepCategory(StepCategory.STEP)
                                                   .build();
  StepType FME_FLAG_PATCH_DEFINITION_STEP_TYPE = StepType.newBuilder()
                                                     .setType(StepSpecTypeConstants.FME_FLAG_PATCH_DEFINITION)
                                                     .setStepCategory(StepCategory.STEP)
                                                     .build();
  StepType FME_FLAG_REALLOCATE_TRAFFIC_STEP_TYPE = StepType.newBuilder()
                                                       .setType(StepSpecTypeConstants.FME_FLAG_REALLOCATE_TRAFFIC)
                                                       .setStepCategory(StepCategory.STEP)
                                                       .build();
  StepType FME_FLAG_SET_DYNAMIC_CONFIGURATIONS_STEP_TYPE =
      StepType.newBuilder()
          .setType(StepSpecTypeConstants.FME_FLAG_SET_DYNAMIC_CONFIGURATIONS)
          .setStepCategory(StepCategory.STEP)
          .build();
  StepType FME_FLAG_SET_TARGETING_RULES_STEP_TYPE = StepType.newBuilder()
                                                        .setType(StepSpecTypeConstants.FME_FLAG_SET_TARGETING_RULES)
                                                        .setStepCategory(StepCategory.STEP)
                                                        .build();
  StepType FME_FLAG_SET_TREATMENTS_STEP_TYPE = StepType.newBuilder()
                                                   .setType(StepSpecTypeConstants.FME_FLAG_SET_TREATMENTS)
                                                   .setStepCategory(StepCategory.STEP)
                                                   .build();
  StepType FME_SEGMENT_UPDATE_STEP_TYPE = StepType.newBuilder()
                                              .setType(StepSpecTypeConstants.FME_SEGMENT_UPDATE)
                                              .setStepCategory(StepCategory.STEP)
                                              .build();
  StepType FME_SEGMENT_DELETE_STEP_TYPE = StepType.newBuilder()
                                              .setType(StepSpecTypeConstants.FME_SEGMENT_DELETE)
                                              .setStepCategory(StepCategory.STEP)
                                              .build();
  StepType FME_FLAGSET_CREATE_STEP_TYPE = StepType.newBuilder()
                                              .setType(StepSpecTypeConstants.FME_FLAGSET_CREATE)
                                              .setStepCategory(StepCategory.STEP)
                                              .build();
  StepType FME_FLAGSET_DELETE_STEP_TYPE = StepType.newBuilder()
                                              .setType(StepSpecTypeConstants.FME_FLAGSET_DELETE)
                                              .setStepCategory(StepCategory.STEP)
                                              .build();
  StepType FME_FLAG_ADD_REMOVE_FLAGSETS_STEP_TYPE = StepType.newBuilder()
                                                        .setType(StepSpecTypeConstants.FME_FLAG_ADD_REMOVE_FLAGSETS)
                                                        .setStepCategory(StepCategory.STEP)
                                                        .build();
  StepType FME_SEGMENT_ADD_REMOVE_TARGETS_STEP_TYPE = StepType.newBuilder()
                                                          .setType(StepSpecTypeConstants.FME_SEGMENT_ADD_REMOVE_TARGETS)
                                                          .setStepCategory(StepCategory.STEP)
                                                          .build();
  StepType FME_SEGMENT_SET_TARGETING_RULES_STEP_TYPE =
      StepType.newBuilder()
          .setType(StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES)
          .setStepCategory(StepCategory.STEP)
          .build();
  StepType FME_FLAG_SET_IMPRESSION_TRACKING_STEP_TYPE =
      StepType.newBuilder()
          .setType(StepSpecTypeConstants.FME_FLAG_SET_IMPRESSION_TRACKING)
          .setStepCategory(StepCategory.STEP)
          .build();
  StepType FME_FLAG_RESTORE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.FME_FLAG_RESTORE).setStepCategory(StepCategory.STEP).build();
  StepType FME_FLAG_UPDATE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.FME_FLAG_UPDATE).setStepCategory(StepCategory.STEP).build();
  StepType FME_FLAG_CREATE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.FME_FLAG_CREATE).setStepCategory(StepCategory.STEP).build();
  StepType FME_SEGMENT_CREATE_STEP_TYPE = StepType.newBuilder()
                                              .setType(StepSpecTypeConstants.FME_SEGMENT_CREATE)
                                              .setStepCategory(StepCategory.STEP)
                                              .build();
  StepType FME_FLAG_DEFAULT_ALLOCATION_STEP_TYPE = StepType.newBuilder()
                                                       .setType(StepSpecTypeConstants.FME_FLAG_DEFAULT_ALLOCATION)
                                                       .setStepCategory(StepCategory.STEP)
                                                       .build();
  StepType FME_FLAG_SET_TARGETS_STEP_TYPE = StepType.newBuilder()
                                                .setType(StepSpecTypeConstants.FME_FLAG_SET_TARGETS)
                                                .setStepCategory(StepCategory.STEP)
                                                .build();
  StepType FME_FLAG_ADD_REMOVE_TARGETS_STEP_TYPE = StepType.newBuilder()
                                                       .setType(StepSpecTypeConstants.FME_FLAG_ADD_REMOVE_TARGETS)
                                                       .setStepCategory(StepCategory.STEP)
                                                       .build();
  StepType FME_FLAG_DEFINITION_INSTRUCTIONS_STEP_TYPE =
      StepType.newBuilder()
          .setType(StepSpecTypeConstants.FME_FLAG_DEFINITION_INSTRUCTIONS)
          .setStepCategory(StepCategory.STEP)
          .build();
  StepType QUEUE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.QUEUE).setStepCategory(StepCategory.STEP).build();
  StepType RESOURCE_CONSTRAINT_STEP_TYPE = StepType.newBuilder()
                                               .setType(StepSpecTypeConstants.RESOURCE_CONSTRAINT)
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  StepType CUSTOM_APPROVAL_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.CUSTOM_APPROVAL).setStepCategory(StepCategory.STEP).build();
  StepType POLICY_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.POLICY_STEP).setStepCategory(StepCategory.STEP).build();
  StepType CHANGE_ADVISOR_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.CHANGE_ADVISOR).setStepCategory(StepCategory.STEP).build();
  StepType HARNESS_APPROVAL_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.HARNESS_APPROVAL).setStepCategory(StepCategory.STEP).build();
  StepType JIRA_APPROVAL_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.JIRA_APPROVAL).setStepCategory(StepCategory.STEP).build();
  StepType SERVICE_NOW_APPROVAL_STEP_TYPE = StepType.newBuilder()
                                                .setType(StepSpecTypeConstants.SERVICENOW_APPROVAL)
                                                .setStepCategory(StepCategory.STEP)
                                                .build();
  StepType JIRA_CREATE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.JIRA_CREATE).setStepCategory(StepCategory.STEP).build();
  StepType JIRA_UPDATE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.JIRA_UPDATE).setStepCategory(StepCategory.STEP).build();
  StepType SERVICE_NOW_CREATE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.SERVICENOW_CREATE).setStepCategory(StepCategory.STEP).build();
  StepType SERVICE_NOW_UPDATE_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.SERVICENOW_UPDATE).setStepCategory(StepCategory.STEP).build();
  StepType SHELL_SCRIPT_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.SHELL_SCRIPT).setStepCategory(StepCategory.STEP).build();
  StepType RO_NOTIFY_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.RO_NOTIFY).setStepCategory(StepCategory.STEP).build();
  StepType EMAIL_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.EMAIL).setStepCategory(StepCategory.STEP).build();
  StepType WAIT_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.WAIT_STEP).setStepCategory(StepCategory.STEP).build();
  StepType SERVICE_NOW_IMPORT_SET_STEP_TYPE = StepType.newBuilder()
                                                  .setType(StepSpecTypeConstants.SERVICENOW_IMPORT_SET)
                                                  .setStepCategory(StepCategory.STEP)
                                                  .build();

  StepType INIT_CONTAINER_STEP_TYPE = StepType.newBuilder()
                                          .setType(StepSpecTypeConstants.INIT_CONTAINER_STEP)
                                          .setStepCategory(StepCategory.STEP)
                                          .build();

  StepType RUN_CONTAINER_STEP_TYPE = StepType.newBuilder()
                                         .setType(StepSpecTypeConstants.RUN_CONTAINER_STEP)
                                         .setStepCategory(StepCategory.STEP)
                                         .build();

  StepType INIT_CONTAINER_V2_STEP_TYPE = StepType.newBuilder()
                                             .setType(StepSpecTypeConstants.INIT_CONTAINER_STEP_V2)
                                             .setStepCategory(StepCategory.STEP)
                                             .build();
  StepType UPLOAD_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.UPLOAD).setStepCategory(StepCategory.STEP).build();
  StepType EVENT_LISTENER_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.EVENT_LISTENER).setStepCategory(StepCategory.STEP).build();
  StepType OPA_EVALUATION_STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.OPA_EVALUATION).setStepCategory(StepCategory.STEP).build();
  StepType OPA_EVALUATION_AGGREGATOR_STEP_TYPE = StepType.newBuilder()
                                                     .setType(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR)
                                                     .setStepCategory(StepCategory.STEP)
                                                     .build();
  StepType AISRE_CREATE_INCIDENT_STEP_TYPE = StepType.newBuilder()
                                                 .setType(StepSpecTypeConstants.AISRE_CREATE_INCIDENT)
                                                 .setStepCategory(StepCategory.STEP)
                                                 .build();
  StepType AISRE_CREATE_ALERT_STEP_TYPE = StepType.newBuilder()
                                              .setType(StepSpecTypeConstants.AISRE_CREATE_ALERT)
                                              .setStepCategory(StepCategory.STEP)
                                              .build();
  StepType DYNAMIC_STAGE_TYPE =
      StepType.newBuilder().setType(OrchestrationStepTypes.DYNAMIC_STAGE).setStepCategory(StepCategory.STAGE).build();
  StepType DYNAMIC_STAGE_V1_TYPE = StepType.newBuilder()
                                       .setType(OrchestrationStepTypes.DYNAMIC_STAGE_V1)
                                       .setStepCategory(StepCategory.STAGE)
                                       .build();
}

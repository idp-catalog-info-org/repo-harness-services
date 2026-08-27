/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.contracts.steps.YamlVersion;
import io.harness.steps.FolderPathConstants;
import io.harness.steps.StepCategoryConstants;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.changeadvisor.ChangeAdvisorStepConstants;
import io.harness.steps.container.ContainerStepSpecTypeConstants;
import io.harness.steps.policy.PolicyStepConstants;

import com.google.protobuf.ProtocolStringList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class CommonStepInfo {
  private static final String FME_FLAG_FOLDER = "Feature Management and Experimentation";
  private static final String FME_FLAG_LIFECYCLE = "Flag Lifecycle";
  private static final String FME_FLAG_CONFIGURATION = "Flag Configuration";
  private static final String FME_TARGETING_SEGMENTATION = "Targeting & Segmentation";
  private static final String FME_TRAFFIC_EXPOSURE_CONTROL = "Traffic & Exposure Control";
  private static final String FME_TREATMENTS_VARIATIONS = "Treatments & Variations";
  private static final String FME_OPERATIONAL_CONTROL = "Operational Control";

  private static StepMetaData fmeStepMetaData(String category) {
    return StepMetaData.newBuilder().addFolderPaths(FME_FLAG_FOLDER + "/" + category).build();
  }

  private static final String AISRE_FOLDER = "AI SRE";

  private static StepMetaData aisreStepMetaData() {
    return StepMetaData.newBuilder().addFolderPaths(AISRE_FOLDER).build();
  }

  StepInfo aisreCreateIncidentStepInfo = StepInfo.newBuilder()
                                             .setName("Create Incident")
                                             .setType(StepSpecTypeConstants.AISRE_CREATE_INCIDENT_STEP_TYPE.getType())
                                             .setFeatureFlag(FeatureName.AISRE_ENABLE_PIPELINES.name())
                                             .setStepMetaData(aisreStepMetaData())
                                             .build();

  StepInfo aisreCreateAlertStepInfo = StepInfo.newBuilder()
                                          .setName("Create Alert")
                                          .setType(StepSpecTypeConstants.AISRE_CREATE_ALERT_STEP_TYPE.getType())
                                          .setFeatureFlag(FeatureName.AISRE_ENABLE_PIPELINES.name())
                                          .setStepMetaData(aisreStepMetaData())
                                          .build();

  StepInfo fmeFlagCreateStepInfo = StepInfo.newBuilder()
                                       .setName("Flag Create")
                                       .setType(StepSpecTypeConstants.FME_FLAG_CREATE_STEP_TYPE.getType())
                                       .setStepMetaData(fmeStepMetaData(FME_FLAG_LIFECYCLE))
                                       .build();

  StepInfo fmeFlagDefaultAllocationStepInfo =
      StepInfo.newBuilder()
          .setName("Flag Default Allocations")
          .setType(StepSpecTypeConstants.FME_FLAG_DEFAULT_ALLOCATION_STEP_TYPE.getType())
          .setStepMetaData(fmeStepMetaData(FME_TRAFFIC_EXPOSURE_CONTROL))
          .build();
  StepInfo fmeFlagAddRemoveTargetsStepInfo =
      StepInfo.newBuilder()
          .setName("Flag Add/Remove Individual Targets")
          .setType(StepSpecTypeConstants.FME_FLAG_ADD_REMOVE_TARGETS_STEP_TYPE.getType())
          .setStepMetaData(fmeStepMetaData(FME_TARGETING_SEGMENTATION))
          .build();
  StepInfo fmeFlagKillStepInfo = StepInfo.newBuilder()
                                     .setName("Kill Feature Flag")
                                     .setType(StepSpecTypeConstants.FME_FLAG_KILL_STEP_TYPE.getType())
                                     .setStepMetaData(fmeStepMetaData(FME_FLAG_LIFECYCLE))
                                     .build();
  StepInfo fmeFlagRestoreStepInfo = StepInfo.newBuilder()
                                        .setName("Restore Feature Flag")
                                        .setType(StepSpecTypeConstants.FME_FLAG_RESTORE_STEP_TYPE.getType())
                                        .setStepMetaData(fmeStepMetaData(FME_FLAG_LIFECYCLE))
                                        .build();
  StepInfo fmeFlagUpdateStepInfo = StepInfo.newBuilder()
                                       .setName("Flag Update")
                                       .setType(StepSpecTypeConstants.FME_FLAG_UPDATE_STEP_TYPE.getType())
                                       .setStepMetaData(fmeStepMetaData(FME_FLAG_LIFECYCLE))
                                       .build();
  StepInfo fmeFlagArchiveStepInfo = StepInfo.newBuilder()
                                        .setName("Flag Archive")
                                        .setType(StepSpecTypeConstants.FME_FLAG_ARCHIVE_STEP_TYPE.getType())
                                        .setStepMetaData(fmeStepMetaData(FME_FLAG_LIFECYCLE))
                                        .build();

  StepInfo fmeMetricCheckStepInfo = StepInfo.newBuilder()
                                        .setName("FME Metric Check")
                                        .setType(StepSpecTypeConstants.FME_METRIC_CHECK_STEP_TYPE.getType())
                                        .setFeatureFlag(FeatureName.FME_METRIC_CHECK.name())
                                        .setStepMetaData(fmeStepMetaData(FME_OPERATIONAL_CONTROL))
                                        .build();

  StepInfo fmeFlagDeleteStepInfo = StepInfo.newBuilder()
                                       .setName("Flag Delete")
                                       .setType(StepSpecTypeConstants.FME_FLAG_DELETE_STEP_TYPE.getType())
                                       .setStepMetaData(fmeStepMetaData(FME_FLAG_LIFECYCLE))
                                       .build();

  StepInfo fmeFlagLimitExposureStepInfo =
      StepInfo.newBuilder()
          .setName("Flag Limit Exposure")
          .setType(StepSpecTypeConstants.FME_FLAG_LIMIT_EXPOSURE_STEP_TYPE.getType())
          .setStepMetaData(fmeStepMetaData(FME_TRAFFIC_EXPOSURE_CONTROL))
          .build();

  StepInfo fmeFlagPatchDefinitionStepInfo =
      StepInfo.newBuilder()
          .setName("Flag Patch Definition")
          .setType(StepSpecTypeConstants.FME_FLAG_PATCH_DEFINITION_STEP_TYPE.getType())
          .setStepMetaData(fmeStepMetaData(FME_FLAG_CONFIGURATION))
          .build();

  StepInfo fmeFlagDefinitionInstructionsStepInfo =
      StepInfo.newBuilder()
          .setName("Flag Definition Instructions")
          .setType(StepSpecTypeConstants.FME_FLAG_DEFINITION_INSTRUCTIONS_STEP_TYPE.getType())
          .setFeatureFlag(FeatureName.FME_FLAG_DEFINITION_INSTRUCTIONS.name())
          .setStepMetaData(fmeStepMetaData(FME_FLAG_CONFIGURATION))
          .build();

  StepInfo fmeFlagReallocateTrafficStepInfo =
      StepInfo.newBuilder()
          .setName("Flag Reallocate Traffic")
          .setType(StepSpecTypeConstants.FME_FLAG_REALLOCATE_TRAFFIC_STEP_TYPE.getType())
          .setStepMetaData(fmeStepMetaData(FME_TRAFFIC_EXPOSURE_CONTROL))
          .build();

  StepInfo fmeFlagSetDynamicConfigurationsStepInfo =
      StepInfo.newBuilder()
          .setName("Flag Set Dynamic Configurations")
          .setType(StepSpecTypeConstants.FME_FLAG_SET_DYNAMIC_CONFIGURATIONS_STEP_TYPE.getType())
          .setStepMetaData(fmeStepMetaData(FME_FLAG_CONFIGURATION))
          .build();

  StepInfo fmeFlagSetTargetingRulesStepInfo =
      StepInfo.newBuilder()
          .setName("Flag Set Targeting Rules")
          .setType(StepSpecTypeConstants.FME_FLAG_SET_TARGETING_RULES_STEP_TYPE.getType())
          .setStepMetaData(fmeStepMetaData(FME_TARGETING_SEGMENTATION))
          .build();

  StepInfo fmeFlagSetTreatmentsStepInfo =
      StepInfo.newBuilder()
          .setName("Flag Set Treatments")
          .setType(StepSpecTypeConstants.FME_FLAG_SET_TREATMENTS_STEP_TYPE.getType())
          .setStepMetaData(fmeStepMetaData(FME_TREATMENTS_VARIATIONS))
          .build();

  StepInfo fmeFlagSetImpressionTrackingStepInfo =
      StepInfo.newBuilder()
          .setName("Flag Set Impression Tracking")
          .setType(StepSpecTypeConstants.FME_FLAG_SET_IMPRESSION_TRACKING_STEP_TYPE.getType())
          .setStepMetaData(fmeStepMetaData(FME_FLAG_CONFIGURATION))
          .build();

  StepInfo fmeFlagSetTargetsStepInfo = StepInfo.newBuilder()
                                           .setName("Flag Set Individual Targets")
                                           .setType(StepSpecTypeConstants.FME_FLAG_SET_TARGETS_STEP_TYPE.getType())
                                           .setStepMetaData(fmeStepMetaData(FME_TARGETING_SEGMENTATION))
                                           .build();

  StepInfo fmeSegmentCreateStepInfo = StepInfo.newBuilder()
                                          .setName("Segment Create")
                                          .setType(StepSpecTypeConstants.FME_SEGMENT_CREATE_STEP_TYPE.getType())
                                          .setStepMetaData(fmeStepMetaData(FME_TARGETING_SEGMENTATION))
                                          .build();

  StepInfo fmeSegmentUpdateStepInfo = StepInfo.newBuilder()
                                          .setName("Segment Update")
                                          .setType(StepSpecTypeConstants.FME_SEGMENT_UPDATE_STEP_TYPE.getType())
                                          .setStepMetaData(fmeStepMetaData(FME_TARGETING_SEGMENTATION))
                                          .build();

  StepInfo fmeSegmentDeleteStepInfo = StepInfo.newBuilder()
                                          .setName("Segment Delete")
                                          .setType(StepSpecTypeConstants.FME_SEGMENT_DELETE_STEP_TYPE.getType())
                                          .setStepMetaData(fmeStepMetaData(FME_TARGETING_SEGMENTATION))
                                          .build();

  StepInfo fmeFlagsetCreateStepInfo = StepInfo.newBuilder()
                                          .setName("Flagset Create")
                                          .setType(StepSpecTypeConstants.FME_FLAGSET_CREATE_STEP_TYPE.getType())
                                          .setStepMetaData(fmeStepMetaData(FME_TARGETING_SEGMENTATION))
                                          .build();

  StepInfo fmeFlagsetDeleteStepInfo = StepInfo.newBuilder()
                                          .setName("Flagset Delete")
                                          .setType(StepSpecTypeConstants.FME_FLAGSET_DELETE_STEP_TYPE.getType())
                                          .setStepMetaData(fmeStepMetaData(FME_TARGETING_SEGMENTATION))
                                          .build();

  StepInfo fmeFlagAddRemoveFlagsetsStepInfo =
      StepInfo.newBuilder()
          .setName("Feature Flag Add/Remove Flagsets")
          .setType(StepSpecTypeConstants.FME_FLAG_ADD_REMOVE_FLAGSETS_STEP_TYPE.getType())
          .setStepMetaData(fmeStepMetaData(FME_TARGETING_SEGMENTATION))
          .build();

  StepInfo fmeSegmentAddRemoveTargetsStepInfo =
      StepInfo.newBuilder()
          .setName("Segment Add/Remove Targets")
          .setType(StepSpecTypeConstants.FME_SEGMENT_ADD_REMOVE_TARGETS_STEP_TYPE.getType())
          .setStepMetaData(fmeStepMetaData(FME_TARGETING_SEGMENTATION))
          .build();

  StepInfo fmeSegmentSetTargetingRulesStepInfo =
      StepInfo.newBuilder()
          .setName("Segment Set Targeting Rules")
          .setType(StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES_STEP_TYPE.getType())
          .setStepMetaData(fmeStepMetaData(FME_TARGETING_SEGMENTATION))
          .build();

  StepInfo shellScriptStepInfo =
      StepInfo.newBuilder()
          .setName("Shell Script")
          .setType(StepSpecTypeConstants.SHELL_SCRIPT)
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Utilities/Scripted").build())
          .build();

  StepInfo shellScriptStepInfoV1 =
      StepInfo.newBuilder()
          .setName("Shell Script")
          .setType(StepSpecTypeConstantsV1.SHELL_SCRIPT)
          .setStepMetaData(
              StepMetaData.newBuilder().setVersion(YamlVersion.V1).addFolderPaths("Utilities/Scripted").build())
          .build();
  StepInfo httpStepInfo =
      StepInfo.newBuilder()
          .setName("HTTP")
          .setType("Http")
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Utilities/Non-Scripted").build())
          .build();

  StepInfo httpStepInfoV1 =
      StepInfo.newBuilder()
          .setName("HTTP")
          .setType(StepSpecTypeConstantsV1.HTTP)
          .setStepMetaData(
              StepMetaData.newBuilder().setVersion(YamlVersion.V1).addFolderPaths("Utilities/Non-Scripted").build())
          .build();

  StepInfo jiraApprovalStepInfoV1 =
      StepInfo.newBuilder()
          .setName("Jira Approval")
          .setType(StepSpecTypeConstantsV1.JIRA_APPROVAL)
          .setStepMetaData(
              StepMetaData.newBuilder().setVersion(YamlVersion.V1).addFolderPaths("Utilities/Non-Scripted").build())
          .build();

  StepInfo harnessApprovalStepInfoV1 =
      StepInfo.newBuilder()
          .setName("Harness Approval")
          .setType(StepSpecTypeConstantsV1.HARNESS_APPROVAL)
          .setStepMetaData(
              StepMetaData.newBuilder().setVersion(YamlVersion.V1).addFolderPaths("Utilities/Non-Scripted").build())
          .build();

  StepInfo customApprovalStepInfoV1 =
      StepInfo.newBuilder()
          .setName("Custom Approval")
          .setType(StepSpecTypeConstantsV1.CUSTOM_APPROVAL)
          .setStepMetaData(
              StepMetaData.newBuilder().setVersion(YamlVersion.V1).addFolderPaths("Utilities/Non-Scripted").build())
          .build();

  StepInfo serviceNowApprovalStepInfoV1 =
      StepInfo.newBuilder()
          .setName("ServiceNow Approval")
          .setType(StepSpecTypeConstantsV1.SERVICENOW_APPROVAL)
          .setStepMetaData(
              StepMetaData.newBuilder().setVersion(YamlVersion.V1).addFolderPaths("Utilities/Non-Scripted").build())
          .build();

  StepInfo emailStepInfo =
      StepInfo.newBuilder()
          .setName("Email")
          .setType("Email")
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Utilities/Non-Scripted").build())
          .build();
  StepInfo harnessApprovalStepInfo =
      StepInfo.newBuilder()
          .setName("Harness Approval")
          .setType("HarnessApproval")
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory(StepCategoryConstants.PROVISIONER)
                               .addCategory(StepCategoryConstants.APPROVAL)
                               .addFolderPaths(FolderPathConstants.APPROVAL)
                               .build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_HARNESS_UI.name())
          .build();
  StepInfo customApprovalStepInfo =
      StepInfo.newBuilder()
          .setName("Custom Approval")
          .setType("CustomApproval")
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory(StepCategoryConstants.PROVISIONER)
                               .addCategory(StepCategoryConstants.APPROVAL)
                               .addFolderPaths(FolderPathConstants.APPROVAL)
                               .build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_CUSTOM_SCRIPT.name())
          .build();
  StepInfo jiraApprovalStepInfo =
      StepInfo.newBuilder()
          .setName("Jira Approval")
          .setType("JiraApproval")
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory(StepCategoryConstants.PROVISIONER)
                               .addCategory(StepCategoryConstants.APPROVAL)
                               .addFolderPaths(FolderPathConstants.APPROVAL)
                               .build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_JIRA.name())
          .build();
  StepInfo jiraCreateStepInfo =
      StepInfo.newBuilder()
          .setName("Jira Create")
          .setType(StepSpecTypeConstants.JIRA_CREATE)
          .setStepMetaData(StepMetaData.newBuilder().addCategory("Jira").addFolderPaths("Jira").build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_JIRA.name())
          .build();
  StepInfo jiraUpdateStepInfo =
      StepInfo.newBuilder()
          .setName("Jira Update")
          .setType(StepSpecTypeConstants.JIRA_UPDATE)
          .setStepMetaData(StepMetaData.newBuilder().addCategory("Jira").addFolderPaths("Jira").build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_JIRA.name())
          .build();
  StepInfo barrierStepInfo =
      StepInfo.newBuilder()
          .setName("Barrier")
          .setType("Barrier")
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("FlowControl/Barrier").build())
          .build();
  StepInfo queueStepInfo = StepInfo.newBuilder()
                               .setName("Queue")
                               .setType("Queue")
                               .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("FlowControl/Queue").build())
                               .build();
  StepInfo serviceNowApprovalStepInfo =
      StepInfo.newBuilder()
          .setName("ServiceNow Approval")
          .setType(StepSpecTypeConstants.SERVICENOW_APPROVAL)
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory(StepCategoryConstants.PROVISIONER)
                               .addCategory(StepCategoryConstants.APPROVAL)
                               .addFolderPaths(FolderPathConstants.APPROVAL)
                               .build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_SERVICE_NOW.name())
          .build();

  StepInfo policyStepInfo = StepInfo.newBuilder()
                                .setName(PolicyStepConstants.POLICY_STEP_NAME)
                                .setType(StepSpecTypeConstants.POLICY_STEP)
                                .setStepMetaData(StepMetaData.newBuilder()
                                                     .addCategory(PolicyStepConstants.POLICY_STEP_CATEGORY)
                                                     .addFolderPaths(PolicyStepConstants.POLICY_STEP_FOLDER_PATH)
                                                     .build())
                                .build();

  StepInfo changeAdvisorStepInfo =
      StepInfo.newBuilder()
          .setName(ChangeAdvisorStepConstants.CHANGE_ADVISOR_STEP_NAME)
          .setType(StepSpecTypeConstants.CHANGE_ADVISOR)
          .setFeatureFlag(FeatureName.FF_CHANGEADVISOR_ENABLED.name())
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory(ChangeAdvisorStepConstants.CHANGE_ADVISOR_STEP_CATEGORY)
                               .addFolderPaths(ChangeAdvisorStepConstants.CHANGE_ADVISOR_STEP_FOLDER_PATH)
                               .build())
          .build();

  StepInfo serviceNowCreateStepInfo =
      StepInfo.newBuilder()
          .setName("ServiceNow Create")
          .setType(StepSpecTypeConstants.SERVICENOW_CREATE)
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory(StepCategoryConstants.SERVICENOW)
                               .addFolderPaths(FolderPathConstants.SERVICENOW)
                               .build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_SERVICE_NOW.name())
          .build();

  StepInfo serviceNowUpdateStepInfo =
      StepInfo.newBuilder()
          .setName("ServiceNow Update")
          .setType(StepSpecTypeConstants.SERVICENOW_UPDATE)
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory(StepCategoryConstants.SERVICENOW)
                               .addFolderPaths(FolderPathConstants.SERVICENOW)
                               .build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_SERVICE_NOW.name())
          .build();

  StepInfo waitStepInfo =
      StepInfo.newBuilder()
          .setName(StepSpecTypeConstants.WAIT_STEP)
          .setType(StepSpecTypeConstants.WAIT_STEP)
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Utilities/Non-Scripted").build())
          .build();

  StepInfo serviceNowImportSetStepInfo =
      StepInfo.newBuilder()
          .setName("ServiceNow Import Set")
          .setType(StepSpecTypeConstants.SERVICENOW_IMPORT_SET)
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory(StepCategoryConstants.SERVICENOW)
                               .addFolderPaths(FolderPathConstants.SERVICENOW)
                               .build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_SERVICE_NOW.name())
          .build();

  StepInfo containerStepInfo =
      StepInfo.newBuilder()
          .setName("Container Step")
          .setType(ContainerStepSpecTypeConstants.CONTAINER_STEP)
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Utilities/Scripted").build())
          .build();

  StepInfo filesUploadStepInfo =
      StepInfo.newBuilder()
          .setName(StepSpecTypeConstants.UPLOAD)
          .setType(StepSpecTypeConstants.UPLOAD)
          .setFeatureFlag(FeatureName.PIPE_ENABLE_FILE_UPLOAD_AS_RUNTIME_INPUT.name())
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Utilities/Non-Scripted").build())
          .build();

  StepInfo eventListenerStepInfo =
      StepInfo.newBuilder()
          .setName(StepSpecTypeConstants.EVENT_LISTENER)
          .setType(StepSpecTypeConstants.EVENT_LISTENER)
          .setFeatureRestrictionName(FeatureRestrictionName.EVENT_LISTENER.name())
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Utilities/Non-Scripted").build())
          .build();

  StepInfo roNotifyStepInfo =
      StepInfo.newBuilder()
          .setName("RO Notify")
          .setType(StepSpecTypeConstants.RO_NOTIFY)
          .setFeatureFlag(FeatureName.RMG_ENABLE_WEBHOOK_QUEUE_SUPPORT.name())
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Utilities/Non-Scripted").build())
          .build();

  private static final List<StepInfo> COMMON_STEPS = Arrays.asList(
      // AI SRE Steps
      aisreCreateIncidentStepInfo, aisreCreateAlertStepInfo,
      // FME Steps - Flag Lifecycle
      fmeFlagCreateStepInfo, fmeFlagUpdateStepInfo, fmeFlagArchiveStepInfo, fmeFlagRestoreStepInfo,
      fmeFlagDeleteStepInfo, fmeFlagKillStepInfo,
      // FME Steps - Flag Configuration
      fmeFlagPatchDefinitionStepInfo, fmeFlagDefinitionInstructionsStepInfo, fmeFlagSetDynamicConfigurationsStepInfo,
      fmeFlagSetImpressionTrackingStepInfo,
      // FME Steps - Targeting & Segmentation
      fmeFlagSetTargetingRulesStepInfo, fmeFlagSetTargetsStepInfo, fmeFlagAddRemoveTargetsStepInfo,
      fmeSegmentCreateStepInfo, fmeSegmentUpdateStepInfo, fmeSegmentDeleteStepInfo, fmeSegmentAddRemoveTargetsStepInfo,
      fmeSegmentSetTargetingRulesStepInfo, fmeFlagsetCreateStepInfo, fmeFlagsetDeleteStepInfo,
      fmeFlagAddRemoveFlagsetsStepInfo,
      // FME Steps - Traffic & Exposure Control
      fmeFlagDefaultAllocationStepInfo, fmeFlagReallocateTrafficStepInfo, fmeFlagLimitExposureStepInfo,
      // FME Steps - Treatments & Variations
      fmeFlagSetTreatmentsStepInfo,
      // FME Steps - Operational Control
      fmeMetricCheckStepInfo,
      // Other steps
      shellScriptStepInfo, httpStepInfo, harnessApprovalStepInfo, customApprovalStepInfo, jiraApprovalStepInfo,
      jiraCreateStepInfo, jiraUpdateStepInfo, barrierStepInfo, queueStepInfo, serviceNowApprovalStepInfo,
      policyStepInfo, changeAdvisorStepInfo, serviceNowCreateStepInfo, serviceNowUpdateStepInfo, emailStepInfo,
      waitStepInfo, serviceNowImportSetStepInfo, containerStepInfo, shellScriptStepInfoV1, httpStepInfoV1,
      jiraApprovalStepInfoV1, harnessApprovalStepInfoV1, customApprovalStepInfoV1, serviceNowApprovalStepInfoV1,
      filesUploadStepInfo, eventListenerStepInfo, roNotifyStepInfo);

  public static final Set<String> COMMON_STEP_TYPES =
      COMMON_STEPS.stream().map(StepInfo::getType).collect(Collectors.toSet());
  public static List<StepInfo> getCommonSteps(String category) {
    return COMMON_STEPS.stream().filter(getStepInfoPredicate(category)).collect(Collectors.toList());
  }

  @NotNull
  private Predicate<StepInfo> getStepInfoPredicate(String category) {
    return stepInfo -> {
      if (EmptyPredicate.isEmpty(category)) {
        return true;
      }
      ProtocolStringList folderPathsList = stepInfo.getStepMetaData().getFolderPathsList();
      return folderPathsList.stream().anyMatch(path -> path.contains(category));
    };
  }
}

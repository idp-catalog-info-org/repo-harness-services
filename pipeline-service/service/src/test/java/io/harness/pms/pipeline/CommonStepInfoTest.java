/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.MANAVJOT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.contracts.steps.YamlVersion;
import io.harness.rule.Owner;
import io.harness.steps.FolderPathConstants;
import io.harness.steps.StepCategoryConstants;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepSpecTypeConstantsV1;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class CommonStepInfoTest extends CategoryTest {
  StepInfo shellScriptStepInfo =
      StepInfo.newBuilder()
          .setName("Shell Script")
          .setType("ShellScript")
          .setStepMetaData(StepMetaData.newBuilder().addFolderPaths("Utilities/Scripted").build())
          .build();

  StepInfo shellScriptStepInfoV1 =
      StepInfo.newBuilder()
          .setName("Shell Script")
          .setType(StepSpecTypeConstantsV1.SHELL_SCRIPT)
          .setStepMetaData(
              StepMetaData.newBuilder().setVersion(YamlVersion.V1).addFolderPaths("Utilities/Scripted").build())
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

  StepInfo harnessApprovalStepInfo =
      StepInfo.newBuilder()
          .setName("Harness Approval")
          .setType("HarnessApproval")
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory("Provisioner")
                               .addCategory("Approval")
                               .addFolderPaths("Approval")
                               .build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_HARNESS_UI.name())

          .build();
  StepInfo jiraApprovalStepInfo =
      StepInfo.newBuilder()
          .setName("Jira Approval")
          .setType("JiraApproval")
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory("Provisioner")
                               .addCategory("Approval")
                               .addFolderPaths("Approval")
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
  StepInfo serviceNowApprovalStepInfo =
      StepInfo.newBuilder()
          .setName("ServiceNow Approval")
          .setType("ServiceNowApproval")
          .setStepMetaData(StepMetaData.newBuilder()
                               .addCategory("Provisioner")
                               .addCategory("Approval")
                               .addFolderPaths("Approval")
                               .build())
          .setFeatureRestrictionName(FeatureRestrictionName.INTEGRATED_APPROVALS_WITH_SERVICE_NOW.name())
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

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetCommonSteps() {
    List<StepInfo> stepInfos = CommonStepInfo.getCommonSteps("Approval");
    assertTrue(stepInfos.contains(harnessApprovalStepInfo));
    assertTrue(stepInfos.contains(jiraApprovalStepInfo));
    assertTrue(stepInfos.contains(serviceNowApprovalStepInfo));
    assertTrue(stepInfos.contains(customApprovalStepInfo));
    stepInfos = CommonStepInfo.getCommonSteps("");
    assertTrue(stepInfos.contains(httpStepInfo));
    assertTrue(stepInfos.contains(harnessApprovalStepInfo));
    assertTrue(stepInfos.contains(jiraApprovalStepInfo));
    assertTrue(stepInfos.contains(jiraCreateStepInfo));
    assertTrue(stepInfos.contains(jiraUpdateStepInfo));
    assertTrue(stepInfos.contains(barrierStepInfo));
    assertTrue(stepInfos.contains(shellScriptStepInfo));
    assertTrue(stepInfos.contains(serviceNowApprovalStepInfo));
    assertTrue(stepInfos.contains(shellScriptStepInfoV1));
    assertTrue(stepInfos.contains(httpStepInfoV1));
    assertTrue(stepInfos.contains(harnessApprovalStepInfoV1));
    assertTrue(stepInfos.contains(serviceNowApprovalStepInfoV1));
    assertTrue(stepInfos.contains(jiraApprovalStepInfoV1));
    assertTrue(stepInfos.contains(customApprovalStepInfoV1));
    assertTrue(stepInfos.contains(filesUploadStepInfo));
    assertTrue(stepInfos.contains(eventListenerStepInfo));
  }

  @Test
  @Owner(developers = MANAVJOT)
  @Category(UnitTests.class)
  public void testGetCommonStepsTypes() {
    List<StepInfo> stepInfos = CommonStepInfo.getCommonSteps("");
    assertEquals(stepInfos.size(), CommonStepInfo.COMMON_STEP_TYPES.size());
  }
}

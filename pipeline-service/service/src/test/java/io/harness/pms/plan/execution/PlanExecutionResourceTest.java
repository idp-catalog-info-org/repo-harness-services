/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.CDS_PIPELINE_ABORT_RBAC_PERMISSION;
import static io.harness.beans.FeatureName.CI_YAML_VERSIONING;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.MANAS_ASATI;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.engine.executions.retry.RetryGroup;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.engine.pms.execution.manual.beans.ManualExecutionAction;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.resource.CheckPostExecutionRollbackDTO;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.RetryPipelineRequestDTO;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.pms.plan.execution.beans.request.ManualExecutionActionDto;
import io.harness.pms.plan.execution.beans.request.ManualExecutionRequestDto;
import io.harness.pms.plan.execution.beans.response.ManualExecutionResponseDto;
import io.harness.pms.plan.execution.helper.PipelineExecutor;
import io.harness.pms.plan.execution.helper.PlanExecutionResourceImpl;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.preflight.service.intfc.PreflightService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.stages.StageExecutionResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.template.yaml.ref.PipelineTemplateRefInfo;
import io.harness.template.yaml.ref.TemplateRefHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ThreadOperationContextHelper;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PlanExecutionResourceTest extends CategoryTest {
  @InjectMocks PlanExecutionResourceImpl planExecutionResource;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PipelineExecutor pipelineExecutor;
  @Mock PMSExecutionService pmsExecutionService;
  @Mock RetryExecutionHelper retryExecutionHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock PMSPipelineServiceHelper pmsPipelineServiceHelper;

  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PlanExecutionService planExecutionService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PreflightService preflightService;
  @Mock RollbackModeExecutionHelper rollbackModeExecutionHelper;
  @Rule public ExpectedException exceptionRule = ExpectedException.none();
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "p1";
  private final String PLAN_EXECUTION_ID = "planExecutionId";
  private final String NODE_EXECUTION_ID = "nodeExecutionId";

  String yaml = "pipeline:\n"
      + "  identifier: p1\n"
      + "  name: p1\n"
      + "  allowStageExecutions: true\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: qaStage\n"
      + "      type: Approval\n"
      + "      name: qa stage\n"
      + "  - stage:\n"
      + "      identifier: qaStage2\n"
      + "      type: Deployment\n"
      + "      name: qa stage 2";

  String yamlWithPipelineTemplate = "pipeline:\n"
      + "  name: dsfwefw\n"
      + "  identifier: dsfwefw\n"
      + "  tags: {}\n"
      + "  template:\n"
      + "    templateRef: fsdfewe";
  String templateYaml = "template:\n"
      + "  spec:\n"
      + "    stages:\n"
      + "      - stage:\n"
      + "          name: fd\n"
      + "          identifier: fd\n"
      + "          description: \"\"\n"
      + "          type: Custom\n"
      + "    allowStageExecutions: true";

  String yamlWithPipelineTemplateAndInsert = "pipeline:\n"
      + "  name: sdaxx\n"
      + "  identifier: sdaxx\n"
      + "  tags: {}\n"
      + "  template:\n"
      + "    templateRef: aa\n"
      + "    versionLabel: aaa\n"
      + "    templateInputs:\n"
      + "      stages:\n"
      + "        - insert:\n"
      + "            identifier: aa\n"
      + "            stages:\n"
      + "              - stage:\n"
      + "                  name: ni\n"
      + "                  identifier: ni\n"
      + "                  description: \"\"\n"
      + "                  type: Custom\n"
      + "                  spec:\n"
      + "                    execution:\n"
      + "                      steps:\n"
      + "                        - step:\n"
      + "                            type: ShellScript\n"
      + "                            name: ShellScript_1\n"
      + "                            identifier: ShellScript_1\n"
      + "                            spec:\n"
      + "                              shell: Bash\n"
      + "                              executionTarget: {}\n"
      + "                              source:\n"
      + "                                type: Inline\n"
      + "                                spec:\n"
      + "                                  script: echo heyy\n"
      + "                              environmentVariables: []\n"
      + "                              outputVariables: []\n"
      + "                            timeout: 10m\n"
      + "                  tags: {}\n"
      + "  projectIdentifier: test\n"
      + "  orgIdentifier: default\n";
  String templateYamlWithInsert = "template:\n"
      + "  name: aa\n"
      + "  identifier: aa\n"
      + "  versionLabel: aaa\n"
      + "  type: Pipeline\n"
      + "  projectIdentifier: test\n"
      + "  orgIdentifier: default\n"
      + "  tags: {}\n"
      + "  spec:\n"
      + "    stages:\n"
      + "      - insert:\n"
      + "          name: aa\n"
      + "          identifier: aa\n"
      + "          stages: <+input>\n"
      + "      - stage:\n"
      + "          name: xz\n"
      + "          identifier: xz\n"
      + "          description: \"\"\n"
      + "          type: Custom\n"
      + "          spec:\n"
      + "            execution:\n"
      + "              steps:\n"
      + "                - step:\n"
      + "                    type: Wait\n"
      + "                    name: Wait_1\n"
      + "                    identifier: Wait_1\n"
      + "                    spec:\n"
      + "                      duration: 10m\n"
      + "          tags: {}\n"
      + "      - insert:\n"
      + "          name: ab\n"
      + "          identifier: ab\n"
      + "          stages:\n"
      + "            - stage:\n"
      + "                name: last\n"
      + "                identifier: last\n"
      + "                description: \"\"\n"
      + "                type: Custom\n"
      + "                spec:\n"
      + "                  execution:\n"
      + "                    steps:\n"
      + "                      - step:\n"
      + "                          type: Wait\n"
      + "                          name: Wait_1\n"
      + "                          identifier: Wait_1\n"
      + "                          spec:\n"
      + "                            duration: 10m\n"
      + "                tags: {}\n"
      + "    allowStageExecutions: true\n";

  String yamlWithPipelineTemplateAndOverrideTrue = "pipeline:\n"
      + "  name: dsfwefw\n"
      + "  identifier: dsfwefw\n"
      + "  tags: {}\n"
      + "  template:\n"
      + "    templateRef: fsdfewe\n"
      + "    templateOverrides:\n"
      + "      allowStageExecutions: true\n";

  String yamlWithPipelineTemplateAndOverrideFalse = "pipeline:\n"
      + "  name: dsfwefw\n"
      + "  identifier: dsfwefw\n"
      + "  tags: {}\n"
      + "  template:\n"
      + "    templateRef: fsdfewe\n"
      + "    templateOverrides:\n"
      + "      allowStageExecutions: false\n";

  String pipelineTemplateYamlAllowStageFalseWithAllowedOverride = "template:\n"
      + "  name: fsdfewe\n"
      + "  identifier: fsdfewe\n"
      + "  type: Pipeline\n"
      + "  allowedOverrides:\n"
      + "    - AllowStageExecutions\n"
      + "  spec:\n"
      + "    stages:\n"
      + "      - stage:\n"
      + "          name: fd\n"
      + "          identifier: fd\n"
      + "          description: \"\"\n"
      + "          type: Custom\n"
      + "    allowStageExecutions: false";

  String pipelineTemplateYamlAllowStageTrueWithAllowedOverride = "template:\n"
      + "  name: fsdfewe\n"
      + "  identifier: fsdfewe\n"
      + "  type: Pipeline\n"
      + "  allowedOverrides:\n"
      + "    - AllowStageExecutions\n"
      + "  spec:\n"
      + "    stages:\n"
      + "      - stage:\n"
      + "          name: fd\n"
      + "          identifier: fd\n"
      + "          description: \"\"\n"
      + "          type: Custom\n"
      + "    allowStageExecutions: true";

  String pipelineTemplateYamlAllowStageFalseWithoutAllowedOverride = "template:\n"
      + "  name: fsdfewe\n"
      + "  identifier: fsdfewe\n"
      + "  type: Pipeline\n"
      + "  spec:\n"
      + "    stages:\n"
      + "      - stage:\n"
      + "          name: fd\n"
      + "          identifier: fd\n"
      + "          description: \"\"\n"
      + "          type: Custom\n"
      + "    allowStageExecutions: false";

  PipelineEntity entity;
  ScopeInfo scopeInfo;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    entity = PipelineEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(PIPELINE_IDENTIFIER)
                 .name(PIPELINE_IDENTIFIER)
                 .yaml(yaml)
                 .build();
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_IDENTIFIER)
                    .projectIdentifier(PROJ_IDENTIFIER)
                    .uniqueId("resolved-unique-id")
                    .build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStagesExecutionList() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CI_YAML_VERSIONING);
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(scopeInfo);
    doReturn(Optional.of(entity))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false,
            scopeInfo, true);
    ResponseDTO<List<StageExecutionResponse>> stagesExecutionList = planExecutionResource.getStagesExecutionList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "false", null);
    assertThat(stagesExecutionList.getData()).hasSize(2);
    StageExecutionResponse stage0Data = stagesExecutionList.getData().get(0);
    assertThat(stage0Data.getStageIdentifier()).isEqualTo("qaStage");
    assertThat(stage0Data.getStageName()).isEqualTo("qa stage");
    assertThat(stage0Data.getMessage()).isEqualTo("Running an approval stage individually can be redundant");
    assertThat(stage0Data.getStagesRequired()).hasSize(0);
    StageExecutionResponse stage1Data = stagesExecutionList.getData().get(1);
    assertThat(stage1Data.getStageIdentifier()).isEqualTo("qaStage2");
    assertThat(stage1Data.getStageName()).isEqualTo("qa stage 2");
    assertThat(stage1Data.getMessage()).isNull();
    assertThat(stage1Data.getStagesRequired()).hasSize(0);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetStagesExecutionListWithTemplateOptimisationFfOn() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CI_YAML_VERSIONING);
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(scopeInfo);
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder().yaml(templateYaml).build();
    doReturn(templateResponseDTO)
        .when(pipelineTemplateHelper)
        .getTemplate("fsdfewe", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, "false", null);
    doReturn(Optional.of(entity.withYaml(yamlWithPipelineTemplate)))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false,
            scopeInfo, true);
    ResponseDTO<List<StageExecutionResponse>> stagesExecutionList = planExecutionResource.getStagesExecutionList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "false", null);
    assertThat(stagesExecutionList.getData()).hasSize(1);
    StageExecutionResponse stage0Data = stagesExecutionList.getData().get(0);
    assertThat(stage0Data.getStageIdentifier()).isEqualTo("fd");
    assertThat(stage0Data.getStageName()).isEqualTo("fd");
    assertThat(stage0Data.getStagesRequired()).hasSize(0);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetStagesExecutionListWithTemplateOptimisationFfOnAndWithInsert() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CI_YAML_VERSIONING);
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(scopeInfo);
    TemplateResponseDTO templateResponseDTO = TemplateResponseDTO.builder().yaml(templateYamlWithInsert).build();
    doReturn(templateResponseDTO)
        .when(pipelineTemplateHelper)
        .getTemplate("aa", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "aaa", null, "false", null);
    doReturn(Optional.of(entity.withYaml(yamlWithPipelineTemplateAndInsert)))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false,
            scopeInfo, true);
    ResponseDTO<List<StageExecutionResponse>> stagesExecutionList = planExecutionResource.getStagesExecutionList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "false", null);
    assertThat(stagesExecutionList.getData()).hasSize(3);
    StageExecutionResponse stage0Data = stagesExecutionList.getData().get(0);
    assertThat(stage0Data.getStageIdentifier()).isEqualTo("ni");
    assertThat(stage0Data.getStageName()).isEqualTo("ni");
    assertThat(stage0Data.getStagesRequired()).hasSize(0);

    StageExecutionResponse stage1Data = stagesExecutionList.getData().get(1);
    assertThat(stage1Data.getStageIdentifier()).isEqualTo("xz");
    assertThat(stage1Data.getStageName()).isEqualTo("xz");
    assertThat(stage1Data.getStagesRequired()).hasSize(0);

    StageExecutionResponse stage2Data = stagesExecutionList.getData().get(2);
    assertThat(stage2Data.getStageIdentifier()).isEqualTo("last");
    assertThat(stage2Data.getStageName()).isEqualTo("last");
    assertThat(stage2Data.getStagesRequired()).hasSize(0);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetStagesExecutionListWhenFfIsOn() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CI_YAML_VERSIONING);
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(scopeInfo);
    doReturn(Optional.of(entity))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, true,
            scopeInfo, true);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml, "true", HarnessYamlVersion.V0);
    ResponseDTO<List<StageExecutionResponse>> stagesExecutionList = planExecutionResource.getStagesExecutionList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "true", null);
    assertThat(stagesExecutionList.getData()).hasSize(2);
    StageExecutionResponse stage0Data = stagesExecutionList.getData().get(0);
    assertThat(stage0Data.getStageIdentifier()).isEqualTo("qaStage");
    assertThat(stage0Data.getStageName()).isEqualTo("qa stage");
    assertThat(stage0Data.getMessage()).isEqualTo("Running an approval stage individually can be redundant");
    assertThat(stage0Data.getStagesRequired()).hasSize(0);
    StageExecutionResponse stage1Data = stagesExecutionList.getData().get(1);
    assertThat(stage1Data.getStageIdentifier()).isEqualTo("qaStage2");
    assertThat(stage1Data.getStageName()).isEqualTo("qa stage 2");
    assertThat(stage1Data.getMessage()).isNull();
    assertThat(stage1Data.getStagesRequired()).hasSize(0);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void testGetStagesExecutionListHonoursAllowedTemplateOverride() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CI_YAML_VERSIONING);
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, FeatureName.PIPE_TEMPLATE_OVERRIDES);
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(scopeInfo);
    TemplateResponseDTO templateResponseDTO =
        TemplateResponseDTO.builder().yaml(pipelineTemplateYamlAllowStageFalseWithAllowedOverride).build();
    doReturn(templateResponseDTO)
        .when(pipelineTemplateHelper)
        .getTemplate("fsdfewe", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, "false", null);
    doReturn(Optional.of(entity.withYaml(yamlWithPipelineTemplateAndOverrideTrue)))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false,
            scopeInfo, true);
    ResponseDTO<List<StageExecutionResponse>> stagesExecutionList = planExecutionResource.getStagesExecutionList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "false", null);
    // Template default is false, but the caller's allowed override flips it to true, so stages are returned.
    assertThat(stagesExecutionList.getData()).hasSize(1);
    assertThat(stagesExecutionList.getData().get(0).getStageIdentifier()).isEqualTo("fd");
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void testGetStagesExecutionListIgnoresOverrideWhenNotAllowed() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CI_YAML_VERSIONING);
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, FeatureName.PIPE_TEMPLATE_OVERRIDES);
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(scopeInfo);
    TemplateResponseDTO templateResponseDTO =
        TemplateResponseDTO.builder().yaml(pipelineTemplateYamlAllowStageFalseWithoutAllowedOverride).build();
    doReturn(templateResponseDTO)
        .when(pipelineTemplateHelper)
        .getTemplate("fsdfewe", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, "false", null);
    doReturn(Optional.of(entity.withYaml(yamlWithPipelineTemplateAndOverrideTrue)))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false,
            scopeInfo, true);
    ResponseDTO<List<StageExecutionResponse>> stagesExecutionList = planExecutionResource.getStagesExecutionList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "false", null);
    // Override requested but template did not allow it, so the template's own false value is retained.
    assertThat(stagesExecutionList.getData()).isEmpty();
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void testGetStagesExecutionListOverrideCanDisableStageExecutions() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CI_YAML_VERSIONING);
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, FeatureName.PIPE_TEMPLATE_OVERRIDES);
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(scopeInfo);
    TemplateResponseDTO templateResponseDTO =
        TemplateResponseDTO.builder().yaml(pipelineTemplateYamlAllowStageTrueWithAllowedOverride).build();
    doReturn(templateResponseDTO)
        .when(pipelineTemplateHelper)
        .getTemplate("fsdfewe", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, "false", null);
    doReturn(Optional.of(entity.withYaml(yamlWithPipelineTemplateAndOverrideFalse)))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false,
            scopeInfo, true);
    ResponseDTO<List<StageExecutionResponse>> stagesExecutionList = planExecutionResource.getStagesExecutionList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "false", null);
    // Template default is true, but the caller overrides it to false, so no stages are returned.
    assertThat(stagesExecutionList.getData()).isEmpty();
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void testGetStagesExecutionListIgnoresOverrideWhenFfOff() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CI_YAML_VERSIONING);
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, FeatureName.PIPE_TEMPLATE_OVERRIDES);
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(scopeInfo);
    TemplateResponseDTO templateResponseDTO =
        TemplateResponseDTO.builder().yaml(pipelineTemplateYamlAllowStageFalseWithAllowedOverride).build();
    doReturn(templateResponseDTO)
        .when(pipelineTemplateHelper)
        .getTemplate("fsdfewe", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, "false", null);
    doReturn(Optional.of(entity.withYaml(yamlWithPipelineTemplateAndOverrideTrue)))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false,
            scopeInfo, true);
    ResponseDTO<List<StageExecutionResponse>> stagesExecutionList = planExecutionResource.getStagesExecutionList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "false", null);
    // FF disabled, so the override is ignored and the template's false value is retained.
    assertThat(stagesExecutionList.getData()).isEmpty();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRunStagesWithRuntimeInputYaml() {
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build();
    doReturn(planExecutionResponseDto)
        .when(pipelineExecutor)
        .runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            RunStageRequestDTO.builder().build(), false, null, null, false, null);
    ResponseDTO<PlanExecutionResponseDto> dto =
        planExecutionResource.runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            PIPELINE_IDENTIFIER, null, false, RunStageRequestDTO.builder().build(), null, null, false, null);
    assertThat(dto.getData()).isEqualTo(planExecutionResponseDto);
    verify(pipelineExecutor, times(1))
        .runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            RunStageRequestDTO.builder().build(), false, null, null, false, null);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetRetryHistory() {
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, "planExecutionId", false))
        .thenReturn(
            PipelineExecutionSummaryEntity.builder()
                .uuid("uuid")
                .planExecutionId("planExecutionId")
                .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("rootExecutionId").build())
                .build());
    planExecutionResource.getRetryHistory(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "planExecutionId");
    verify(retryExecutionHelper, times(1)).getRetryHistory(ACCOUNT_ID, "rootExecutionId", "planExecutionId");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetLatestExecutionId() {
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, "planExecutionId", false))
        .thenReturn(
            PipelineExecutionSummaryEntity.builder()
                .uuid("uuid")
                .planExecutionId("planExecutionId")
                .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("rootExecutionId").build())
                .build());
    planExecutionResource.getRetryLatestExecutionId(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "planExecutionId");
    verify(retryExecutionHelper, times(1)).getRetryLatestExecutionId(ACCOUNT_ID, "rootExecutionId");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRunPostExecutionRollback() {
    doReturn(PlanExecution.builder().planId("planId123").build())
        .when(pipelineExecutor)
        .startPostExecutionRollback(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "originalPlanId",
            Collections.singletonList("stageNodeExecutionId"), null, false, null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.runPostExecutionRollback(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            "originalPlanId", "stageNodeExecutionId", null, false, null);
    PlanExecutionResponseDto data = response.getData();
    assertThat(data.getPlanExecution().getPlanId()).isEqualTo("planId123");
    // pipelineIdentifier was provided, so no DB lookup is needed and access is checked against it directly.
    verify(planExecutionService, times(0)).getWithFieldsIncluded(anyString(), any());
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunPostExecutionRollbackWithoutPipelineIdentifier() {
    doReturn(PlanExecution.builder()
                 .ambiance(
                     Ambiance.newBuilder()
                         .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_IDENTIFIER).build())
                         .build())
                 .build())
        .when(planExecutionService)
        .getWithFieldsIncluded(eq("originalPlanId"), any());
    doReturn(PlanExecution.builder().planId("planId123").build())
        .when(pipelineExecutor)
        .startPostExecutionRollback(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "originalPlanId",
            Collections.singletonList("stageNodeExecutionId"), null, false, null);
    ResponseDTO<PlanExecutionResponseDto> response = planExecutionResource.runPostExecutionRollback(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, "originalPlanId", "stageNodeExecutionId", null, false, null);
    PlanExecutionResponseDto data = response.getData();
    assertThat(data.getPlanExecution().getPlanId()).isEqualTo("planId123");
    // pipelineIdentifier was not provided, so it is derived from the plan execution and used for the access check.
    verify(planExecutionService).getWithFieldsIncluded(eq("originalPlanId"), any());
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCheckIfPostExecutionRollbackIsAllowedEnforcesExecutePermission() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJ_IDENTIFIER)
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_IDENTIFIER).build())
            .build();
    doReturn(Collections.singletonList(NodeExecution.builder().uuid(NODE_EXECUTION_ID).ambiance(ambiance).build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(eq(new HashSet<>(Collections.singletonList(NODE_EXECUTION_ID))), any());
    doNothing().when(rollbackModeExecutionHelper).checkIfPostExecutionRollbackAllowed(any());

    ResponseDTO<?> response =
        planExecutionResource.checkIfPostExecutionRollbackIsAllowed(Collections.singletonList(NODE_EXECUTION_ID));

    assertThat(response.getData()).isNotNull();
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testCheckIfPostExecutionRollbackIsAllowedSkipsAccessCheckWhenNodesMissing() {
    doReturn(Collections.emptyList())
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(eq(new HashSet<>(Collections.singletonList(NODE_EXECUTION_ID))), any());
    doNothing().when(rollbackModeExecutionHelper).checkIfPostExecutionRollbackAllowed(any());

    ResponseDTO<CheckPostExecutionRollbackDTO> response =
        planExecutionResource.checkIfPostExecutionRollbackIsAllowed(Collections.singletonList(NODE_EXECUTION_ID));

    assertThat(response.getData().getIsAllowed()).isTrue();
    verify(accessControlClient, times(0)).checkForAccessOrThrow(any(), any(), any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetPreflightCheckResponseDelegatesToService() {
    String preflightId = "preflight-id";
    planExecutionResource.getPreflightCheckResponse(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, preflightId, null);

    verify(preflightService).getPreflightCheckResponse(preflightId);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetPipelineYaml() {
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            yaml, false, false, null, null, null, false, false);
    planExecutionResource.runPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        PIPELINE_IDENTIFIER, null, false, false, yaml, null, null, null, false, false);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleStageAndPipelineInterrupt() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(PipelineExecutionSummaryEntity.builder().pipelineIdentifier(PIPELINE_IDENTIFIER).build())
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(ACCOUNT_ID, PLAN_EXECUTION_ID, false);
    doReturn(InterruptDTO.builder()
                 .id("interruptUuid")
                 .planExecutionId(PLAN_EXECUTION_ID)
                 .type(PlanExecutionInterruptType.ABORTALL)
                 .build())
        .when(pmsExecutionService)
        .registerInterrupt(PlanExecutionInterruptType.ABORTALL, PLAN_EXECUTION_ID, null);
    ArgumentCaptor<PlanExecutionInterruptType> interruptTypeArgumentCaptor1 =
        ArgumentCaptor.forClass(PlanExecutionInterruptType.class);
    planExecutionResource.handleInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptTypePipeline.ABORTALL, PLAN_EXECUTION_ID, null);
    verify(pmsExecutionService, times(1)).registerInterrupt(interruptTypeArgumentCaptor1.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor1.getValue()).isEqualTo(PlanExecutionInterruptType.ABORTALL);
    doReturn(InterruptDTO.builder()
                 .id("interruptUuid")
                 .planExecutionId(PLAN_EXECUTION_ID)
                 .type(PlanExecutionInterruptType.ABORTALL)
                 .build())
        .when(pmsExecutionService)
        .registerInterrupt(PlanExecutionInterruptType.ABORTALL, PLAN_EXECUTION_ID, "nodeExecutionId");
    ArgumentCaptor<PlanExecutionInterruptType> interruptTypeArgumentCaptor2 =
        ArgumentCaptor.forClass(PlanExecutionInterruptType.class);
    planExecutionResource.handleStageInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptTypeStage.ABORTALL, PLAN_EXECUTION_ID, "nodeExecutionId", null);
    verify(pmsExecutionService, times(2)).registerInterrupt(interruptTypeArgumentCaptor2.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor2.getValue()).isEqualTo(PlanExecutionInterruptType.ABORTALL);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetPipelineYamlWithoutUserFlowContext() {
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            yaml, false, false, null, null, null, false, false);
    planExecutionResource.runPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        PIPELINE_IDENTIFIER, null, false, false, yaml, null, null, null, false, false);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetPipelineYamlV2() {
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            yaml, true, false, null, null, null, false, false);
    planExecutionResource.runPipelineWithInputSetPipelineYamlV2(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", PIPELINE_IDENTIFIER, null, false, null, yaml, null, null);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetPipelineYamlV2WithoutUserFlowContext() {
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            yaml, true, false, null, null, null, false, false);
    planExecutionResource.runPipelineWithInputSetPipelineYamlV2(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", PIPELINE_IDENTIFIER, null, false, null, yaml, null, null);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRunStagesWithRuntimeInputYamlWithoutUserFlowContext() {
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build();
    doReturn(planExecutionResponseDto)
        .when(pipelineExecutor)
        .runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            RunStageRequestDTO.builder().build(), false, null, null, false, null);
    planExecutionResource.runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        PIPELINE_IDENTIFIER, null, false, RunStageRequestDTO.builder().build(), null, null, false, null);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunStagesWithRuntimeInputYaml() {
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build();
    doReturn(planExecutionResponseDto)
        .when(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", RunStageRequestDTO.builder().build(), false, false, null, false, null);
    ResponseDTO<PlanExecutionResponseDto> response = planExecutionResource.rerunStagesWithRuntimeInputYaml(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", PIPELINE_IDENTIFIER, "originalExecutionId", null, false,
        RunStageRequestDTO.builder().build(), null, false, null);
    assertEquals("someId", response.getData().getPlanExecution().getPlanId());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunStagesWithRuntimeInputYamlWithoutUserFlowContext() {
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build();
    doReturn(planExecutionResponseDto)
        .when(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", RunStageRequestDTO.builder().build(), false, false, null, false, null);
    planExecutionResource.rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        PIPELINE_IDENTIFIER, "originalExecutionId", null, false, RunStageRequestDTO.builder().build(), null, false,
        null);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetPipelineYaml() {
    PlanExecution planExecution = PlanExecution.builder().planId("planId123").build();
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(planExecution).build();
    ResponseDTO<PlanExecutionResponseDto> mockResponse = ResponseDTO.newResponse(planExecutionResponseDto);

    PlanExecutionResourceImpl spy = spy(planExecutionResource);

    doReturn(mockResponse)
        .when(spy)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", "originalExecutionId",
            PIPELINE_IDENTIFIER, null, false, yaml, null, false, false, null);

    ResponseDTO<PlanExecutionResponseDto> response =
        spy.rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml, null, false, false, null);

    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetPipelineYamlWithoutUserFlowContext() {
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, false, false, null, false, null, false, null);
    planExecutionResource.rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml, null, false, false, null);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetPipelineYamlV2() {
    PlanExecution planExecution = PlanExecution.builder().planId("planId123").build();
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(planExecution).build();

    ResponseDTO<PlanExecutionResponseDto> mockResponse = ResponseDTO.newResponse(planExecutionResponseDto);

    PlanExecutionResourceImpl spy = spy(planExecutionResource);

    doReturn(mockResponse)
        .when(spy)
        .rerunPipelineWithInputSetPipelineYamlV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml, null, false, null);

    ResponseDTO<PlanExecutionResponseDto> response =
        spy.rerunPipelineWithInputSetPipelineYamlV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml, null, false, null);

    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetPipelineYamlV2WithoutUserFlowContext() {
    when(pmsPipelineServiceHelper.getScopeInfo(any(), any(), any(), any())).thenReturn(null);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, true, false, null, false, null, false, null);
    planExecutionResource.rerunPipelineWithInputSetPipelineYamlV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml, null, false, null);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDebugStagesWithRuntimeInputYaml() {
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build();
    doReturn(planExecutionResponseDto)
        .when(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", RunStageRequestDTO.builder().build(), false, true, null, false, null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.debugStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            PIPELINE_IDENTIFIER, "originalExecutionId", null, false, RunStageRequestDTO.builder().build(), null);
    assertEquals("someId", response.getData().getPlanExecution().getPlanId());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDebugStagesWithRuntimeInputYamlWithoutUserFlowContext() {
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build();
    doReturn(planExecutionResponseDto)
        .when(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", RunStageRequestDTO.builder().build(), false, true, null, false, null);
    planExecutionResource.debugStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        PIPELINE_IDENTIFIER, "originalExecutionId", null, false, RunStageRequestDTO.builder().build(), null);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDebugPipelineWithInputSetPipelineYaml() {
    when(pmsPipelineServiceHelper.getScopeInfo(any(), any(), any(), any())).thenReturn(null);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, false, true, null, false, null, false, null);
    ResponseDTO<PlanExecutionResponseDto> response = planExecutionResource.debugPipelineWithInputSetPipelineYaml(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", PIPELINE_IDENTIFIER, null, null, false, yaml, false, null);
    assertEquals(Status.SUCCESS, response.getStatus());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDebugPipelineWithInputSetPipelineYamlWithoutUserFlowContext() {
    when(pmsPipelineServiceHelper.getScopeInfo(any(), any(), any(), any())).thenReturn(null);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, false, true, null, false, null, false, null);
    planExecutionResource.debugPipelineWithInputSetPipelineYaml(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", PIPELINE_IDENTIFIER, null, null, false, yaml, false, null);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDebugPipelineWithInputSetPipelineYamlV2() {
    PlanExecution planExecution = PlanExecution.builder().planId("planId123").build();
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(planExecution).build();

    ResponseDTO<PlanExecutionResponseDto> mockResponse = ResponseDTO.newResponse(planExecutionResponseDto);

    PlanExecutionResourceImpl spy = spy(planExecutionResource);
    doReturn(mockResponse)
        .when(spy)
        .debugPipelineWithInputSetPipelineYamlV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml, null);
    ResponseDTO<PlanExecutionResponseDto> response = spy.debugPipelineWithInputSetPipelineYamlV2(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml, null);
    assertEquals(Status.SUCCESS, response.getStatus());
    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDebugPipelineWithInputSetPipelineYamlV2WithoutUserFlowContext() {
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, true, true, null, false, null, false, null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.debugPipelineWithInputSetPipelineYamlV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml, null);
    assertEquals(Status.SUCCESS, response.getStatus());
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetRetryStages() {
    doReturn(
        RetryInfo.builder().isResumable(true).groups(Collections.singletonList(RetryGroup.builder().build())).build())
        .when(retryExecutionHelper)
        .validateRetry(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "PlanExecutionId", null, null);
    ResponseDTO<RetryInfo> response = planExecutionResource.getRetryStages(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "PlanExecutionId", null, null, null);
    assertTrue(response.getData().isResumable());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetRetryStagesWithoutUserFlowContext() {
    doReturn(
        RetryInfo.builder().isResumable(true).groups(Collections.singletonList(RetryGroup.builder().build())).build())
        .when(retryExecutionHelper)
        .validateRetry(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "PlanExecutionId", null, null);
    ResponseDTO<RetryInfo> response = planExecutionResource.getRetryStages(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "PlanExecutionId", null, null, null);
    assertTrue(response.getData().isResumable());
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetIdentifierList() {
    MergeInputSetRequestDTOPMS mergeInputSetRequest = MergeInputSetRequestDTOPMS.builder().build();
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetReferencesList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            mergeInputSetRequest, "main", "repoId", null, false, null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.runPipelineWithInputSetIdentifierList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            PIPELINE_IDENTIFIER, GitEntityFindInfoDTO.builder().branch("main").yamlGitConfigId("repoId").build(), false,
            mergeInputSetRequest, null, false, null);
    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetIdentifierListWithoutUserFlowContext() {
    MergeInputSetRequestDTOPMS mergeInputSetRequest = MergeInputSetRequestDTOPMS.builder().build();
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetReferencesList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            mergeInputSetRequest, "main", "repoId", null, false, null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.runPipelineWithInputSetIdentifierList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            PIPELINE_IDENTIFIER, GitEntityFindInfoDTO.builder().branch("main").yamlGitConfigId("repoId").build(), false,
            mergeInputSetRequest, null, false, null);
    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetIdentifierList() {
    MergeInputSetRequestDTOPMS mergeInputSetRequest = MergeInputSetRequestDTOPMS.builder().build();
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetReferencesList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", mergeInputSetRequest, "main", "repoId", false, null, null);
    ResponseDTO<PlanExecutionResponseDto> response = planExecutionResource.rerunPipelineWithInputSetIdentifierList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", "originalExecutionId", PIPELINE_IDENTIFIER,
        GitEntityFindInfoDTO.builder().branch("main").yamlGitConfigId("repoId").build(), false, mergeInputSetRequest,
        null, null);
    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetIdentifierListWithoutUserFlowContext() {
    MergeInputSetRequestDTOPMS mergeInputSetRequest = MergeInputSetRequestDTOPMS.builder().build();
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetReferencesList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", mergeInputSetRequest, "main", "repoId", false, null, null);
    ResponseDTO<PlanExecutionResponseDto> response = planExecutionResource.rerunPipelineWithInputSetIdentifierList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", "originalExecutionId", PIPELINE_IDENTIFIER,
        GitEntityFindInfoDTO.builder().branch("main").yamlGitConfigId("repoId").build(), false, mergeInputSetRequest,
        null, null);
    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testHandlePipelineInterrupt() {
    doReturn(PipelineExecutionSummaryEntity.builder().pipelineIdentifier(PIPELINE_IDENTIFIER).build())
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(ACCOUNT_ID, PLAN_EXECUTION_ID, false);
    doReturn(InterruptDTO.builder()
                 .id("interruptUuid")
                 .planExecutionId(PLAN_EXECUTION_ID)
                 .type(PlanExecutionInterruptType.ABORTALL)
                 .build())
        .when(pmsExecutionService)
        .registerInterrupt(PlanExecutionInterruptType.ABORTALL, PLAN_EXECUTION_ID, null);
    ArgumentCaptor<PlanExecutionInterruptType> interruptTypeArgumentCaptor =
        ArgumentCaptor.forClass(PlanExecutionInterruptType.class);
    exceptionRule.expect(NGAccessDeniedException.class);

    // feature flag enabled and abort permission missing
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CDS_PIPELINE_ABORT_RBAC_PERMISSION);
    doThrow(NGAccessDeniedException.class)
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_ABORT);
    planExecutionResource.handleInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptTypePipeline.ABORTALL, PLAN_EXECUTION_ID, null);
    verify(pmsExecutionService, times(0)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.ABORTALL);

    // feature flag enabled and have abort permission
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_ABORT);
    planExecutionResource.handleInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptTypePipeline.ABORTALL, PLAN_EXECUTION_ID, null);
    verify(pmsExecutionService, times(1)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.ABORTALL);

    // feature flag disabled and missing Execute permission
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CDS_PIPELINE_ABORT_RBAC_PERMISSION);
    doThrow(NGAccessDeniedException.class)
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    planExecutionResource.handleInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptTypePipeline.ABORTALL, PLAN_EXECUTION_ID, null);
    verify(pmsExecutionService, times(1)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.ABORTALL);

    // feature flag disabled and have execute permission
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CDS_PIPELINE_ABORT_RBAC_PERMISSION);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    planExecutionResource.handleInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptTypePipeline.ABORTALL, PLAN_EXECUTION_ID, null);
    verify(pmsExecutionService, times(2)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.ABORTALL);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testHandleStageInterrupt() {
    doReturn(PipelineExecutionSummaryEntity.builder().pipelineIdentifier(PIPELINE_IDENTIFIER).build())
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(ACCOUNT_ID, PLAN_EXECUTION_ID, false);
    doReturn(InterruptDTO.builder()
                 .id("interruptUuid")
                 .planExecutionId(PLAN_EXECUTION_ID)
                 .type(PlanExecutionInterruptType.ABORTALL)
                 .build())
        .when(pmsExecutionService)
        .registerInterrupt(PlanExecutionInterruptType.ABORTALL, PLAN_EXECUTION_ID, null);
    ArgumentCaptor<PlanExecutionInterruptType> interruptTypeArgumentCaptor =
        ArgumentCaptor.forClass(PlanExecutionInterruptType.class);
    exceptionRule.expect(NGAccessDeniedException.class);

    // feature flag enabled and abort permission missing
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CDS_PIPELINE_ABORT_RBAC_PERMISSION);
    doThrow(NGAccessDeniedException.class)
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_ABORT);
    planExecutionResource.handleStageInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptTypeStage.ABORTALL, PLAN_EXECUTION_ID, "nodeExecutionId", null);
    verify(pmsExecutionService, times(0)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.ABORTALL);

    // feature flag enabled and have abort permission
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_ABORT);
    planExecutionResource.handleStageInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptTypeStage.ABORTALL, PLAN_EXECUTION_ID, "nodeExecutionId", null);
    verify(pmsExecutionService, times(1)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.ABORTALL);

    // feature flag enabled and have execute permission and interrupt type is UserMarkedFailure
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    planExecutionResource.handleStageInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptTypeStage.UserMarkedFailure, PLAN_EXECUTION_ID, "nodeExecutionId", null);
    verify(pmsExecutionService, times(2)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.UserMarkedFailure);

    // feature flag disabled and have execute permission
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CDS_PIPELINE_ABORT_RBAC_PERMISSION);
    planExecutionResource.handleStageInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptTypeStage.ABORTALL, PLAN_EXECUTION_ID, "nodeExecutionId", null);
    verify(pmsExecutionService, times(3)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.ABORTALL);

    // feature flag disabled and missing Execute permission
    doThrow(NGAccessDeniedException.class)
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    planExecutionResource.handleStageInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptTypeStage.ABORTALL, PLAN_EXECUTION_ID, "nodeExecutionId", null);
    verify(pmsExecutionService, times(3)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.ABORTALL);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testHandleManualInterventionInterrupt() {
    doReturn(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_IDENTIFIER).build())
        .when(planExecutionService)
        .getExecutionMetadataFromPlanExecution(PLAN_EXECUTION_ID);
    doReturn(InterruptDTO.builder()
                 .id("interruptUuid")
                 .planExecutionId(PLAN_EXECUTION_ID)
                 .type(PlanExecutionInterruptType.ABORT)
                 .build())
        .when(pmsExecutionService)
        .registerInterrupt(PlanExecutionInterruptType.ABORT, PLAN_EXECUTION_ID, null);
    ArgumentCaptor<PlanExecutionInterruptType> interruptTypeArgumentCaptor =
        ArgumentCaptor.forClass(PlanExecutionInterruptType.class);
    exceptionRule.expect(NGAccessDeniedException.class);

    // feature flag enabled and abort permission missing
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CDS_PIPELINE_ABORT_RBAC_PERMISSION);
    doThrow(NGAccessDeniedException.class)
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_ABORT);
    planExecutionResource.handleManualInterventionInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptType.ABORT, PLAN_EXECUTION_ID, "nodeExecutionId", null);
    verify(pmsExecutionService, times(0)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.ABORT);

    // feature flag enabled and have abort permission
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_ABORT);
    planExecutionResource.handleManualInterventionInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptType.ABORT, PLAN_EXECUTION_ID, "nodeExecutionId", null);
    verify(pmsExecutionService, times(1)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.ABORT);

    // feature flag enabled and have execute permission and interrupt type is UserMarkedFailure
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    planExecutionResource.handleManualInterventionInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptType.UserMarkedFailure, PLAN_EXECUTION_ID, "nodeExecutionId", null);
    verify(pmsExecutionService, times(2)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.UserMarkedFailure);

    // feature flag enabled and have execute permission and interrupt type is MARKASSUCCESS
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    planExecutionResource.handleManualInterventionInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptType.MARKASSUCCESS, PLAN_EXECUTION_ID, "nodeExecutionId", null);
    verify(pmsExecutionService, times(3)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.MARKASSUCCESS);

    // feature flag disabled and have execute permission
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, CDS_PIPELINE_ABORT_RBAC_PERMISSION);
    planExecutionResource.handleManualInterventionInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptType.ABORT, PLAN_EXECUTION_ID, "nodeExecutionId", null);
    verify(pmsExecutionService, times(4)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.ABORT);

    // feature flag disabled and missing Execute permission
    doThrow(NGAccessDeniedException.class)
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", PIPELINE_IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    planExecutionResource.handleManualInterventionInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptType.ABORT, PLAN_EXECUTION_ID, "nodeExecutionId", null);
    verify(pmsExecutionService, times(4)).registerInterrupt(interruptTypeArgumentCaptor.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor.getValue()).isEqualTo(PlanExecutionInterruptType.ABORT);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleManualExecutionWithMarkAsResume() {
    ManualExecutionRequestDto requestDto =
        ManualExecutionRequestDto.builder().action(ManualExecutionActionDto.MARK_AS_RESUME).build();
    doNothing()
        .when(planExecutionService)
        .handleManualExecution(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(NODE_EXECUTION_ID),
            eq(ManualExecutionAction.MARK_AS_RESUME), eq(null));

    ResponseDTO<ManualExecutionResponseDto> response = planExecutionResource.handleManualExecution(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, NODE_EXECUTION_ID, requestDto, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().isStatus()).isTrue();
    verify(planExecutionService)
        .handleManualExecution(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, NODE_EXECUTION_ID, ManualExecutionAction.MARK_AS_RESUME, null);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRetryPipelineWithInputSetPipelineYamlV2() {
    String runtimeInputYaml = "pipeline:\n  stages:\n  - stage:\n      identifier: stage1";
    Map<String, String> expressionValues = new HashMap<>();
    expressionValues.put("expr1", "value1");
    expressionValues.put("expr2", "value2");

    RetryPipelineRequestDTO retryPipelineRequestDTO =
        RetryPipelineRequestDTO.builder().runtimeInputYaml(runtimeInputYaml).expressionValues(expressionValues).build();

    List<String> retryStages = List.of("stage1", "stage2");
    boolean runAllStages = true;

    when(retryExecutionHelper.validateRetry(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, PLAN_EXECUTION_ID, null, null))
        .thenReturn(RetryInfo.builder().isResumable(true).build());
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null)).thenReturn(null);

    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder()
            .planExecution(PlanExecution.builder().uuid(PLAN_EXECUTION_ID).status(RUNNING).build())
            .build();

    when(pipelineExecutor.retryPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
             PIPELINE_IDENTIFIER, null, runtimeInputYaml, PLAN_EXECUTION_ID, retryStages, runAllStages, false, false,
             "", false, null, expressionValues))
        .thenReturn(planExecutionResponseDto);

    ResponseDTO<PlanExecutionResponseDto> response = planExecutionResource.retryPipelineWithInputSetPipelineYamlV2(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, PLAN_EXECUTION_ID, retryStages, runAllStages,
        PIPELINE_IDENTIFIER, retryPipelineRequestDTO, "", false, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getPlanExecution().getUuid()).isEqualTo(PLAN_EXECUTION_ID);

    verify(retryExecutionHelper)
        .validateRetry(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, PLAN_EXECUTION_ID, null, null);
    verify(pipelineExecutor)
        .retryPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
            runtimeInputYaml, PLAN_EXECUTION_ID, retryStages, runAllStages, false, false, "", false, null,
            expressionValues);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRetryPipelineWithInputSetPipelineYamlV2WithNullExpressionValues() {
    String runtimeInputYaml = "pipeline:\n  stages:\n  - stage:\n      identifier: stage1";

    RetryPipelineRequestDTO retryPipelineRequestDTO =
        RetryPipelineRequestDTO.builder().runtimeInputYaml(runtimeInputYaml).expressionValues(null).build();

    List<String> retryStages = List.of("stage1");
    boolean runAllStages = false;

    when(retryExecutionHelper.validateRetry(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, PLAN_EXECUTION_ID, null, null))
        .thenReturn(RetryInfo.builder().isResumable(true).build());
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null)).thenReturn(null);

    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder()
            .planExecution(PlanExecution.builder().uuid(PLAN_EXECUTION_ID).status(RUNNING).build())
            .build();

    when(pipelineExecutor.retryPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
             PIPELINE_IDENTIFIER, null, runtimeInputYaml, PLAN_EXECUTION_ID, retryStages, runAllStages, false, false,
             "", false, null, null))
        .thenReturn(planExecutionResponseDto);

    ResponseDTO<PlanExecutionResponseDto> response = planExecutionResource.retryPipelineWithInputSetPipelineYamlV2(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, PLAN_EXECUTION_ID, retryStages, runAllStages,
        PIPELINE_IDENTIFIER, retryPipelineRequestDTO, "", false, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getPlanExecution().getUuid()).isEqualTo(PLAN_EXECUTION_ID);

    verify(pipelineExecutor)
        .retryPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
            runtimeInputYaml, PLAN_EXECUTION_ID, retryStages, runAllStages, false, false, "", false, null, null);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRetryPipelineWithInputSetPipelineYamlV2WithNullRequestBody() {
    List<String> retryStages = List.of("stage1");
    boolean runAllStages = false;

    when(retryExecutionHelper.validateRetry(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, PLAN_EXECUTION_ID, null, null))
        .thenReturn(RetryInfo.builder().isResumable(true).build());
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null)).thenReturn(null);

    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder()
            .planExecution(PlanExecution.builder().uuid(PLAN_EXECUTION_ID).status(RUNNING).build())
            .build();

    when(pipelineExecutor.retryPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
             PIPELINE_IDENTIFIER, null, null, PLAN_EXECUTION_ID, retryStages, runAllStages, false, false, "", false,
             null, null))
        .thenReturn(planExecutionResponseDto);

    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.retryPipelineWithInputSetPipelineYamlV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null,
            PLAN_EXECUTION_ID, retryStages, runAllStages, PIPELINE_IDENTIFIER, null, "", false, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getPlanExecution().getUuid()).isEqualTo(PLAN_EXECUTION_ID);

    verify(pipelineExecutor)
        .retryPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
            null, PLAN_EXECUTION_ID, retryStages, runAllStages, false, false, "", false, null, null);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetStagesExecutionListThrowsClearMessageWhenYamlExceedsSizeLimit() {
    StringBuilder largeYamlBuilder = new StringBuilder("pipeline:\n  name: test\n  identifier: test\n  stages:\n");
    String padding = "x".repeat(1024);
    while (largeYamlBuilder.length() <= 3 * 1024 * 1024) {
      largeYamlBuilder.append("    - stage:\n        name: s\n        identifier: s\n        value: \"")
          .append(padding)
          .append("\"\n");
    }
    String largeYaml = largeYamlBuilder.toString();
    PipelineEntity pipelineEntity =
        PipelineEntity.builder().yaml(largeYaml).harnessVersion(HarnessYamlVersion.V0).build();
    ScopeInfo resolvedScopeInfo = ScopeInfo.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .uniqueId("resolved-unique-id")
                                      .build();
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(resolvedScopeInfo);
    doReturn(Optional.of(pipelineEntity))
        .when(pmsPipelineService)
        .getPipeline(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(false),
            eq(false), eq(false), eq(false), any(), eq(true));

    try (MockedStatic<TemplateRefHelper> mockedTemplateRefHelper = mockStatic(TemplateRefHelper.class)) {
      mockedTemplateRefHelper.when(() -> TemplateRefHelper.hasPipelineTemplateRef(anyString(), anyString()))
          .thenReturn(PipelineTemplateRefInfo.builder().hasPipelineTemplate(false).build());
      assertThatThrownBy(()
                             -> planExecutionResource.getStagesExecutionList(
                                 ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "false", null))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("Pipeline YAML size exceeds the maximum allowed limit of 3 MB");
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRunPipelineResolvesScopeInfoWhenContextIsNull() {
    ScopeInfo resolvedScopeInfo = ScopeInfo.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .uniqueId("resolved-unique-id")
                                      .build();
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(resolvedScopeInfo);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            null, false, false, yaml, resolvedScopeInfo, null, false, false);
    planExecutionResource.runPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        PIPELINE_IDENTIFIER, null, false, false, yaml, null, null, null, false, false);
    verify(pmsPipelineServiceHelper).getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null);
    verify(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            null, false, false, yaml, resolvedScopeInfo, null, false, false);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRunPipelineV2ResolvesScopeInfoWhenContextIsNull() {
    ScopeInfo resolvedScopeInfo = ScopeInfo.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .uniqueId("resolved-unique-id")
                                      .build();
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(resolvedScopeInfo);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            yaml, false, false, null, resolvedScopeInfo, null, false, false);
    planExecutionResource.runPipelineWithInputSetPipelineYamlV2(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", PIPELINE_IDENTIFIER, null, false, null, yaml, null, null);
    verify(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            yaml, false, false, null, resolvedScopeInfo, null, false, false);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRunStagesResolvesScopeInfoWhenContextIsNull() {
    ScopeInfo resolvedScopeInfo = ScopeInfo.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .uniqueId("resolved-unique-id")
                                      .build();
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(resolvedScopeInfo);
    RunStageRequestDTO runStageRequestDTO = RunStageRequestDTO.builder().build();
    doReturn(PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build())
        .when(pipelineExecutor)
        .runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            runStageRequestDTO, false, null, null, false, resolvedScopeInfo);
    planExecutionResource.runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        PIPELINE_IDENTIFIER, null, false, runStageRequestDTO, null, null, false, null);
    verify(pipelineExecutor)
        .runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            runStageRequestDTO, false, null, null, false, resolvedScopeInfo);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRerunStagesResolvesScopeInfoWhenContextIsNull() {
    ScopeInfo resolvedScopeInfo = ScopeInfo.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .uniqueId("resolved-unique-id")
                                      .build();
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(resolvedScopeInfo);
    RunStageRequestDTO runStageRequestDTO = RunStageRequestDTO.builder().build();
    doReturn(PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build())
        .when(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", runStageRequestDTO, false, false, null, false, resolvedScopeInfo);
    planExecutionResource.rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        PIPELINE_IDENTIFIER, "originalExecutionId", null, false, runStageRequestDTO, null, false, null);
    verify(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", runStageRequestDTO, false, false, null, false, resolvedScopeInfo);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testDebugStagesResolvesScopeInfoWhenContextIsNull() {
    ScopeInfo resolvedScopeInfo = ScopeInfo.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .uniqueId("resolved-unique-id")
                                      .build();
    when(pmsPipelineServiceHelper.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .thenReturn(resolvedScopeInfo);
    RunStageRequestDTO runStageRequestDTO = RunStageRequestDTO.builder().build();
    doReturn(PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build())
        .when(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", runStageRequestDTO, false, true, null, false, resolvedScopeInfo);
    planExecutionResource.debugStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        PIPELINE_IDENTIFIER, "originalExecutionId", null, false, runStageRequestDTO, null);
    verify(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", runStageRequestDTO, false, true, null, false, resolvedScopeInfo);
  }
}

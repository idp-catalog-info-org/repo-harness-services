/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.SHASHANK_JAIN;
import static io.harness.rule.OwnerRule.SOUMYAJIT;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.engine.executions.retry.RetryGroup;
import io.harness.engine.governance.OpaOnSaveStatusErrorDTO;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.opa.gitx.OpaEnforcementResult;
import io.harness.opa.gitx.OpaGitxCoordinates;
import io.harness.opa.gitx.OpaGitxStatus;
import io.harness.opa.gitx.OpaOnSaveStatusDTO;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.helpers.validate.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.pms.plan.execution.helper.ExecutionHelper;
import io.harness.pms.plan.execution.helper.PipelineExecutor;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import retrofit2.Call;
import retrofit2.Response;

@PrepareForTest({UUIDGenerator.class})
@OwnedBy(PIPELINE)
public class PipelineExecutorTest extends CategoryTest {
  @InjectMocks PipelineExecutor pipelineExecutor;
  @Mock ExecutionHelper executionHelper;
  @Mock ValidateAndMergeHelper validateAndMergeHelper;
  @Mock PipelineTelemetryHelper pipelineTelemetryHelper;
  @Mock PlanExecutionService planExecutionService;
  @Mock RollbackModeExecutionHelper rollbackModeExecutionHelper;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;

  @Mock PMSExecutionService pmsExecutionService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock NGSettingsClient ngSettingsClient;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock PMSInputSetService pmsInputSetService;
  @Mock RetryExecutionHelper retryExecutionHelper;
  @Mock PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Mock PrincipalInfoHelper principalInfoHelper;
  @Mock PipelineOpaStatusHandler pipelineOpaStatusHandler;

  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String parentUniqueId = "parentUniqueId";
  ScopeInfo scopeInfo = ScopeInfo.builder()
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgId)
                            .projectIdentifier(projectId)
                            .uniqueId(parentUniqueId)
                            .build();
  String pipelineId = "pipelineId";
  String moduleType = "cd";
  String runtimeInputYaml = "pipeline:\n"
      + "  variables:\n"
      + "  - name: a\n"
      + "    type: String\n"
      + "    value: c";
  List<String> stageIdentifiers = Arrays.asList("a1", "a2", "s1");
  RunStageRequestDTO runStageRequestDTO = RunStageRequestDTO.builder()
                                              .runtimeInputYaml(runtimeInputYaml)
                                              .stageIdentifiers(stageIdentifiers)
                                              .expressionValues(Collections.emptyMap())
                                              .build();
  String originalExecutionId = "originalExecutionId";
  String currentExecutionId = "planId";
  boolean useV2 = false;
  List<String> inputSetReferences = Arrays.asList("i1", "i2", "i3");
  String pipelineBranch = null;
  String pipelineRepoId = null;
  boolean isDebug = false;
  PipelineEntity pipelineEntity = PipelineEntity.builder()
                                      .accountId(accountId)
                                      .orgIdentifier(orgId)
                                      .projectIdentifier(projectId)
                                      .allowStageExecutions(true)
                                      .build();
  ExecutionTriggerInfo executionTriggerInfo =
      ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build();
  ExecutionMetadata metadata = ExecutionMetadata.newBuilder().setTriggerInfo(executionTriggerInfo).build();
  Long expressionFunctorToken = 1234L;
  PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                    .planExecutionId(currentExecutionId)
                                                    .expressionFunctorToken(expressionFunctorToken)
                                                    .build();
  PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
      PlanExecutionMetadataWithContext.builder().runAllStages(true).build();
  ExecArgs execArgs = ExecArgs.builder()
                          .metadata(metadata)
                          .planExecutionMetadataWithContext(
                              planExecutionMetadataWithContext.withPlanExecutionMetadata(planExecutionMetadata))
                          .build();
  PlanExecution planExecution = PlanExecution.builder().expressionFunctorToken(expressionFunctorToken).build();

  // re-run without changing input metadata

  String pipelinYaml = "pipeline:\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        description: <+input>\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        description: <+input>\n"
      + "  fixedInputsOnRerun: true\n";

  PipelineEntity pipelineEntityWithFixedInputsOnRerun = PipelineEntity.builder()
                                                            .accountId(accountId)
                                                            .orgIdentifier(orgId)
                                                            .projectIdentifier(projectId)
                                                            .yaml(pipelinYaml)
                                                            .allowStageExecutions(true)
                                                            .build();
  PlanExecutionMetadata planExecutionMetadataWithFixedInputsOnRerun =
      PlanExecutionMetadata.builder()
          .inputSetYaml(runtimeInputYaml)
          .planExecutionId(currentExecutionId)
          .expressionFunctorToken(expressionFunctorToken)
          .build();
  ExecArgs execArgsWithFixedInputsOnReruns =
      ExecArgs.builder()
          .metadata(metadata)
          .planExecutionMetadataWithContext(
              planExecutionMetadataWithContext.withPlanExecutionMetadata(planExecutionMetadataWithFixedInputsOnRerun))
          .build();

  private MockedStatic<NGRestUtils> restUtilsMockedStatic;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    restUtilsMockedStatic = Mockito.mockStatic(NGRestUtils.class);
    when(pmsFeatureFlagHelper.isEnabled(anyString(), eq(FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name())))
        .thenReturn(false);
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.PIPE_OPA_GITX_ENFORCEMENT));
    doReturn(OpaEnforcementResult.notApplicable())
        .when(pipelineOpaStatusHandler)
        .doOpaOnSaveEvaluation(any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetPipelineYaml() {
    doReturnStatementsForFreshRun(null, false, null);

    PlanExecutionResponseDto planExecutionResponse = pipelineExecutor.runPipelineWithInputSetPipelineYaml(accountId,
        orgId, projectId, pipelineId, moduleType, runtimeInputYaml, useV2, false, null, scopeInfo, null, false, false);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());

    verifyStatementsForFreshRun(null, false, null);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetReferencesList() {
    PlanExecutionResponseDto responseDto = PlanExecutionResponseDto.builder()
                                               .planExecution(planExecution)
                                               .gitDetails(EntityGitDetails.builder().build())
                                               .build();

    doReturn(YamlUtils.readAsJsonNode(runtimeInputYaml))
        .when(validateAndMergeHelper)
        .getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(null, pipelineId, inputSetReferences,
            pipelineBranch, pipelineRepoId, null, null, false, false, false, true, (String) null);

    List<InputSetEntity> inputSetEntities = new ArrayList<>();
    for (int i = 0; i < inputSetReferences.size(); i++) {
      String id = inputSetReferences.get(i);
      InputSetEntityType type = (i == 0) ? InputSetEntityType.OVERLAY_INPUT_SET : InputSetEntityType.INPUT_SET;
      inputSetEntities.add(InputSetEntity.builder().identifier(id).inputSetEntityType(type).build());
    }
    doReturn(inputSetEntities).when(pmsInputSetService).list(any());

    for (String id : inputSetReferences) {
      InputSetEntity entity = InputSetEntity.builder()
                                  .identifier(id)
                                  .inputSetReferences(inputSetReferences.subList(1, inputSetReferences.size()))
                                  .build();
      doReturn(Optional.of(entity))
          .when(pmsInputSetService)
          .getWithoutValidations(any(), anyString(), eq(id), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);
    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();

    PipelineExecutor spyExecutor = spy(pipelineExecutor);

    doReturn(responseDto)
        .when(spyExecutor)
        .startPlanExecution(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any());

    MergeInputSetRequestDTOPMS mergeInputSetRequestDTOPMS =
        MergeInputSetRequestDTOPMS.builder().inputSetReferences(inputSetReferences).build();
    PlanExecutionResponseDto planExecutionResponse =
        spyExecutor.runPipelineWithInputSetReferencesList(accountId, orgId, projectId, pipelineId, moduleType,
            mergeInputSetRequestDTOPMS, pipelineBranch, pipelineRepoId, null, false, null);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());

    verify(validateAndMergeHelper, times(1))
        .getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(null, pipelineId, inputSetReferences,
            pipelineBranch, pipelineRepoId, null, null, false, false, false, true, (String) null);

    verify(spyExecutor, times(1))
        .runPipelineWithInputSetReferencesList(accountId, orgId, projectId, pipelineId, moduleType,
            mergeInputSetRequestDTOPMS, pipelineBranch, pipelineRepoId, null, false, null);
    verify(spyExecutor, times(1))
        .startPlanExecution(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRunStagesWithRuntimeInputYaml() {
    doReturnStatementsForFreshRun(null, false, stageIdentifiers);

    PlanExecutionResponseDto planExecutionResponse = pipelineExecutor.runStagesWithRuntimeInputYaml(
        accountId, orgId, projectId, pipelineId, moduleType, runStageRequestDTO, useV2, null, null, false, scopeInfo);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());

    verifyStatementsForFreshRun(null, false, stageIdentifiers);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRerunStagesWithRuntimeInputYaml() {
    List<String> inputSetIds = Collections.emptyList();
    doReturn(inputSetIds).when(retryExecutionHelper).getInputSetIdForRerunPipeline(accountId, originalExecutionId);
    doReturnStatementsForFreshRun(originalExecutionId, false, stageIdentifiers);

    PlanExecutionResponseDto planExecutionResponse =
        pipelineExecutor.rerunStagesWithRuntimeInputYaml(accountId, orgId, projectId, pipelineId, moduleType,
            originalExecutionId, runStageRequestDTO, useV2, isDebug, null, false, scopeInfo);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());

    verifyStatementsForFreshRun(originalExecutionId, false, stageIdentifiers);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetPipelineYaml() {
    List<String> inputSetIds = Collections.emptyList();
    doReturn(inputSetIds).when(retryExecutionHelper).getInputSetIdForRerunPipeline(accountId, originalExecutionId);
    doReturnStatementsForFreshRun(originalExecutionId, false, null);

    PlanExecutionResponseDto planExecutionResponse =
        pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
            originalExecutionId, runtimeInputYaml, useV2, false, null, false, false, scopeInfo);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());

    verifyStatementsForFreshRun(originalExecutionId, false, null);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetPipelineYaml_ThrowsException() {
    List<String> inputSetIds = Collections.emptyList();
    doReturn(inputSetIds).when(retryExecutionHelper).getInputSetIdForRerunPipeline(accountId, originalExecutionId);
    doReturn(YamlUtils.readAsJsonNode(runtimeInputYaml))
        .when(validateAndMergeHelper)
        .getMergeInputSetFromPipelineTemplateWithJsonNode(
            pipelineId, inputSetReferences, pipelineBranch, pipelineRepoId, null, false, null);

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);
    doThrow(new EntityNotFoundException("Plan execution not found for the id: "))
        .when(executionHelper)
        .buildTriggerInfo(originalExecutionId);
    Assertions
        .assertThatExceptionOfType(
            NestedExceptionUtils
                .hintWithExplanationException("Pipeline executions older than 30 days cannot be re-run.",
                    "Unable to rerun execution.",
                    new EntityNotFoundException("Execution details not found for the id: "))
                .getClass())
        .isThrownBy(
            ()
                -> pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId,
                    moduleType, originalExecutionId, runtimeInputYaml, useV2, false, null, false, false, scopeInfo));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetReferencesList() {
    PlanExecutionResponseDto responseDto = PlanExecutionResponseDto.builder()
                                               .planExecution(planExecution)
                                               .gitDetails(EntityGitDetails.builder().build())
                                               .build();
    doReturn(YamlUtils.readAsJsonNode(runtimeInputYaml))
        .when(validateAndMergeHelper)
        .getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(null, pipelineId, inputSetReferences,
            pipelineBranch, pipelineRepoId, null, null, false, false, false, true, (String) null);

    List<InputSetEntity> inputSetEntities = new ArrayList<>();
    for (int i = 0; i < inputSetReferences.size(); i++) {
      String id = inputSetReferences.get(i);
      InputSetEntityType type = (i == 0) ? InputSetEntityType.OVERLAY_INPUT_SET : InputSetEntityType.INPUT_SET;
      inputSetEntities.add(InputSetEntity.builder().identifier(id).inputSetEntityType(type).build());
    }
    doReturn(inputSetEntities).when(pmsInputSetService).list(any());

    for (String id : inputSetReferences) {
      InputSetEntity entity = InputSetEntity.builder()
                                  .identifier(id)
                                  .inputSetReferences(inputSetReferences.subList(1, inputSetReferences.size()))
                                  .build();
      doReturn(Optional.of(entity))
          .when(pmsInputSetService)
          .getWithoutValidations(any(), anyString(), eq(id), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }
    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);
    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();

    PipelineExecutor spyExecutor = spy(pipelineExecutor);

    doReturn(responseDto)
        .when(spyExecutor)
        .startPlanExecution(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any());

    MergeInputSetRequestDTOPMS mergeInputSetRequestDTOPMS =
        MergeInputSetRequestDTOPMS.builder().inputSetReferences(inputSetReferences).build();
    PlanExecutionResponseDto planExecutionResponse =
        spyExecutor.rerunPipelineWithInputSetReferencesList(accountId, orgId, projectId, pipelineId, moduleType,
            originalExecutionId, mergeInputSetRequestDTOPMS, pipelineBranch, pipelineRepoId, false, null, scopeInfo);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());

    verify(validateAndMergeHelper, times(1))
        .getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(scopeInfo, pipelineId, inputSetReferences,
            pipelineBranch, pipelineRepoId, null, null, false, false, false, true, (String) null);
    verify(spyExecutor, times(1))
        .rerunPipelineWithInputSetReferencesList(accountId, orgId, projectId, pipelineId, moduleType,
            originalExecutionId, mergeInputSetRequestDTOPMS, pipelineBranch, pipelineRepoId, false, null, scopeInfo);
    verify(spyExecutor, times(1))
        .startPlanExecution(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any());
  }

  private void doReturnStatementsForFreshRun(
      String originalExecutionId, boolean addValidateAndMergeHelperDoReturn, List<String> stageIdentifiers) {
    if (addValidateAndMergeHelperDoReturn) {
      doReturn(YamlUtils.readAsJsonNode(runtimeInputYaml))
          .when(validateAndMergeHelper)
          .getMergeInputSetFromPipelineTemplateWithJsonNode(
              pipelineId, inputSetReferences, pipelineBranch, pipelineRepoId, null, false, null);
    }

    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(originalExecutionId);
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      doReturn(execArgs)
          .when(executionHelper)
          .buildExecutionArgs(pipelineEntity, moduleType, Collections.emptyList(), Collections.emptyMap(),
              executionTriggerInfo, originalExecutionId, retryExecutionParameters, false, false, null,
              YamlUtils.readAsJsonNode(runtimeInputYaml), scopeInfo, true, planExecutionMetadataWithContext, false);
    } else {
      doReturn(execArgs)
          .when(executionHelper)
          .buildExecutionArgs(pipelineEntity, moduleType, stageIdentifiers, Collections.emptyMap(),
              executionTriggerInfo, originalExecutionId, retryExecutionParameters, false, false, null,
              YamlUtils.readAsJsonNode(runtimeInputYaml), scopeInfo, true, planExecutionMetadataWithContext, false);
    }

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(accountId, orgId, projectId, metadata,
            planExecutionMetadataWithContext.withPlanExecutionMetadata(planExecutionMetadata), scopeInfo, true,
            isDebug);
  }

  private void verifyStatementsForFreshRun(
      String originalExecutionId, boolean verifyValidateAndMergeHelper, List<String> stageIdentifiers) {
    if (verifyValidateAndMergeHelper) {
      verify(validateAndMergeHelper, times(1))
          .getMergeInputSetFromPipelineTemplateWithJsonNode(
              pipelineId, inputSetReferences, pipelineBranch, pipelineRepoId, null, false, null);
    }

    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    verify(executionHelper, times(1)).fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);
    verify(executionHelper, times(1)).buildTriggerInfo(originalExecutionId);
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      verify(executionHelper, times(1))
          .buildExecutionArgs(pipelineEntity, moduleType, Collections.emptyList(), Collections.emptyMap(),
              executionTriggerInfo, originalExecutionId, retryExecutionParameters, false, false, null,
              YamlUtils.readAsJsonNode(runtimeInputYaml), scopeInfo, true, planExecutionMetadataWithContext, false);
    } else {
      verify(executionHelper, times(1))
          .buildExecutionArgs(pipelineEntity, moduleType, stageIdentifiers, Collections.emptyMap(),
              executionTriggerInfo, originalExecutionId, retryExecutionParameters, false, false, null,
              YamlUtils.readAsJsonNode(runtimeInputYaml), scopeInfo, true, planExecutionMetadataWithContext, false);
    }
    verify(executionHelper, times(1))
        .startExecution(accountId, orgId, projectId, metadata,
            planExecutionMetadataWithContext.withPlanExecutionMetadata(planExecutionMetadata), scopeInfo, true,
            isDebug);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetPipelineYamlWithFixedInputsOnRerun() {
    List<String> inputSetIds = Collections.emptyList();
    doReturn(inputSetIds).when(retryExecutionHelper).getInputSetIdForRerunPipeline(accountId, originalExecutionId);
    String runtimeInputYaml2 = "pipeline:\n"
        + "  variables:\n"
        + "  - name: a\n"
        + "    type: String\n"
        + "    value: RISHI";
    doReturnStatementsForFreshRunWithFixedInputsOnRerun(originalExecutionId, null);
    doReturn(pipelinYaml)
        .when(pipelineTemplateHelper)
        .resolveOnlyPipelineTemplateRefAndMerge(accountId, orgId, projectId, pipelinYaml, null, "false", "0");
    PlanExecutionResponseDto planExecutionResponse =
        pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
            originalExecutionId, runtimeInputYaml2, useV2, isDebug, null, false, false, scopeInfo);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());
    verifyStatementsForRerun(originalExecutionId, null);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testRerunStagesWithRuntimeInputYamlWithFixedInputsOnRerun() {
    List<String> inputSetIds = Collections.emptyList();
    doReturn(inputSetIds).when(retryExecutionHelper).getInputSetIdForRerunPipeline(accountId, originalExecutionId);
    String runtimeInputYaml2 = "pipeline:\n"
        + "  variables:\n"
        + "  - name: a\n"
        + "    type: String\n"
        + "    value: RISHI";
    RunStageRequestDTO runStageRequestDTO = RunStageRequestDTO.builder()
                                                .runtimeInputYaml(runtimeInputYaml2)
                                                .stageIdentifiers(stageIdentifiers)
                                                .expressionValues(Collections.emptyMap())
                                                .build();
    doReturnStatementsForFreshRunWithFixedInputsOnRerun(originalExecutionId, stageIdentifiers);
    doReturn(pipelinYaml)
        .when(pipelineTemplateHelper)
        .resolveOnlyPipelineTemplateRefAndMerge(accountId, orgId, projectId, pipelinYaml, null, "false", "0");
    PlanExecutionResponseDto planExecutionResponse =
        pipelineExecutor.rerunStagesWithRuntimeInputYaml(accountId, orgId, projectId, pipelineId, moduleType,
            originalExecutionId, runStageRequestDTO, useV2, isDebug, null, false, scopeInfo);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());
    verifyStatementsForRerun(originalExecutionId, stageIdentifiers);
  }

  private void doReturnStatementsForFreshRunWithFixedInputsOnRerun(
      String originalExecutionId, List<String> stageIdentifiers) {
    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    doReturn(pipelineEntityWithFixedInputsOnRerun)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);
    doReturn(planExecutionMetadataWithFixedInputsOnRerun)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(eq(accountId), eq(originalExecutionId), any());
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(originalExecutionId);
    if (EmptyPredicate.isNotEmpty(stageIdentifiers)) {
      doReturn(execArgsWithFixedInputsOnReruns)
          .when(executionHelper)
          .buildExecutionArgs(pipelineEntityWithFixedInputsOnRerun, moduleType, stageIdentifiers,
              Collections.emptyMap(), executionTriggerInfo, originalExecutionId, retryExecutionParameters, false, false,
              null, YamlUtils.readAsJsonNode(runtimeInputYaml), scopeInfo, true, planExecutionMetadataWithContext,
              false);
    } else {
      doReturn(execArgsWithFixedInputsOnReruns)
          .when(executionHelper)
          .buildExecutionArgs(pipelineEntityWithFixedInputsOnRerun, moduleType, Collections.emptyList(),
              Collections.emptyMap(), executionTriggerInfo, originalExecutionId, retryExecutionParameters, false, false,
              null, YamlUtils.readAsJsonNode(runtimeInputYaml), scopeInfo, true, planExecutionMetadataWithContext,
              false);
    }
    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(accountId, orgId, projectId, metadata,
            planExecutionMetadataWithContext.withPlanExecutionMetadata(planExecutionMetadataWithFixedInputsOnRerun),
            scopeInfo, true, isDebug);
  }

  private void verifyStatementsForRerun(String originalExecutionId, List<String> stageIdentifiers) {
    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    verify(executionHelper, times(1)).fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);
    verify(executionHelper, times(1)).buildTriggerInfo(originalExecutionId);

    // Asserting that runtimeInputYaml should be same as original execution
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      verify(executionHelper, times(1))
          .buildExecutionArgs(pipelineEntityWithFixedInputsOnRerun, moduleType, Collections.emptyList(),
              Collections.emptyMap(), executionTriggerInfo, originalExecutionId, retryExecutionParameters, false, false,
              null, YamlUtils.readAsJsonNode(runtimeInputYaml), scopeInfo, true, planExecutionMetadataWithContext,
              false);
    } else {
      verify(executionHelper, times(1))
          .buildExecutionArgs(pipelineEntityWithFixedInputsOnRerun, moduleType, stageIdentifiers,
              Collections.emptyMap(), executionTriggerInfo, originalExecutionId, retryExecutionParameters, false, false,
              null, YamlUtils.readAsJsonNode(runtimeInputYaml), scopeInfo, true, planExecutionMetadataWithContext,
              false);
    }
    verify(executionHelper, times(1))
        .startExecution(accountId, orgId, projectId, metadata,
            planExecutionMetadataWithContext.withPlanExecutionMetadata(planExecutionMetadataWithFixedInputsOnRerun),
            scopeInfo, true, isDebug);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testBuildRetryExecutionParameters() {
    // isRetry: false
    RetryExecutionParameters retryExecutionParameters =
        pipelineExecutor.buildRetryExecutionParameters(false, null, null, null);
    assertThat(retryExecutionParameters.isRetry()).isEqualTo(false);

    String processedYaml = "This is a processed Yaml";
    List<String> stagesIdentifier = Arrays.asList("stage1", "stage2");
    List<String> identifierOfSkippedStages = Collections.singletonList("stage1");
    retryExecutionParameters = pipelineExecutor.buildRetryExecutionParameters(
        true, processedYaml, stagesIdentifier, identifierOfSkippedStages);
    assertThat(retryExecutionParameters.isRetry()).isEqualTo(true);
    assertThat(retryExecutionParameters.getRetryStagesIdentifier()).isEqualTo(stagesIdentifier);
    assertThat(retryExecutionParameters.getIdentifierOfSkipStages()).isEqualTo(identifierOfSkippedStages);
    assertThat(retryExecutionParameters.getPreviousProcessedYaml()).isEqualTo(processedYaml);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testDraftExecution() {
    pipelineEntity.setIsDraft(true);
    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);
    assertThatThrownBy(
        ()
            -> pipelineExecutor.runPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
                runtimeInputYaml, useV2, false, null, scopeInfo, null, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("Cannot execute a Draft Pipeline with PipelineID: %s, ProjectID %s", pipelineId, projectId));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testStartPostExecutionRollback() {
    MockedStatic<UUIDGenerator> mockSettings = Mockito.mockStatic(UUIDGenerator.class);
    List<String> stageNodeExecutionIds = Collections.singletonList("stageNodeExecutionId");
    when(UUIDGenerator.generateUuid()).thenReturn(currentExecutionId);
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(null);
    ExecutionMetadata originalExecutionMetadata =
        ExecutionMetadata.newBuilder()
            .setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.WEBHOOK).build())
            .build();
    doReturn(PlanExecution.builder()
                 .createdAt(System.currentTimeMillis() - 300000)
                 .metadata(originalExecutionMetadata)
                 .build())
        .when(planExecutionService)
        .getWithFieldsIncluded(eq(originalExecutionId), any());
    doReturn(metadata)
        .when(rollbackModeExecutionHelper)
        .transformExecutionMetadata(originalExecutionMetadata, currentExecutionId, executionTriggerInfo,
            ExecutionMode.POST_EXECUTION_ROLLBACK, null, stageNodeExecutionIds);
    PlanExecutionMetadata originalPlanExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(originalExecutionId).build();
    doReturn(Optional.of(originalPlanExecutionMetadata))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(accountId, originalExecutionId);
    doReturn(GitSyncBranchContext.builder().build())
        .when(pmsGitSyncHelper)
        .deserializeGitSyncBranchContext(metadata.getGitSyncBranchContext());
    doReturn(planExecutionMetadata)
        .when(rollbackModeExecutionHelper)
        .transformPlanExecutionMetadata(originalPlanExecutionMetadata, currentExecutionId,
            ExecutionMode.POST_EXECUTION_ROLLBACK, stageNodeExecutionIds, null,
            planExecutionMetadataWithContext.withPreviousExecutionId(originalExecutionId));
    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(accountId, orgId, projectId, metadata,
            planExecutionMetadataWithContext.withPlanExecutionMetadata(planExecutionMetadata)
                .withPreviousExecutionId(originalExecutionId),
            null);
    assertThat(pipelineExecutor.startPostExecutionRollback(
                   accountId, orgId, projectId, originalExecutionId, stageNodeExecutionIds, null, false, null))
        .isEqualTo(planExecution);
    verify(rollbackModeExecutionHelper, times(1)).checkIfPostExecutionRollbackAllowed(stageNodeExecutionIds);
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testStartPipelineRollback() {
    MockedStatic<UUIDGenerator> mockSettings = Mockito.mockStatic(UUIDGenerator.class);
    when(UUIDGenerator.generateUuid()).thenReturn(currentExecutionId);
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(null);
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().runAllStages(true).isAsyncPlanCreation(true).build();
    ExecutionMetadata originalExecutionMetadata =
        ExecutionMetadata.newBuilder()
            .setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.WEBHOOK).build())
            .build();
    doReturn(PlanExecution.builder().metadata(originalExecutionMetadata).build())
        .when(planExecutionService)
        .getWithFieldsIncluded(eq(originalExecutionId), any());
    doReturn(metadata)
        .when(rollbackModeExecutionHelper)
        .transformExecutionMetadata(originalExecutionMetadata, currentExecutionId, executionTriggerInfo,
            ExecutionMode.PIPELINE_ROLLBACK, null, Collections.emptyList());
    doReturn(GitSyncBranchContext.builder().build())
        .when(pmsGitSyncHelper)
        .deserializeGitSyncBranchContext(metadata.getGitSyncBranchContext());
    PlanExecutionMetadata originalPlanExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(originalExecutionId).build();
    doReturn(Optional.of(originalPlanExecutionMetadata))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(accountId, originalExecutionId);
    doReturn(planExecutionMetadata)
        .when(rollbackModeExecutionHelper)
        .transformPlanExecutionMetadata(originalPlanExecutionMetadata, currentExecutionId,
            ExecutionMode.PIPELINE_ROLLBACK, Collections.emptyList(), null,
            planExecutionMetadataWithContext.withPreviousExecutionId(originalExecutionId));
    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(accountId, orgId, projectId, metadata,
            planExecutionMetadataWithContext.withPlanExecutionMetadata(planExecutionMetadata)
                .withPreviousExecutionId(originalExecutionId),
            null);
    doReturn(Collections.emptyList()).when(nodeExecutionService).fetchStageExecutions(any());
    assertThat(pipelineExecutor.startPipelineRollback(accountId, orgId, projectId, originalExecutionId, null, null))
        .isEqualTo(planExecution);
    mockSettings.close();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testSetTriggerInfo() {
    ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().build();
    String jsonPayload = "jsonPayload";
    String accountId = "acc";
    String projectId = "pro";
    String orgId = "org";
    String pipelineId = "pipelineId";
    String planExecutionId = currentExecutionId;
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();
    PipelineStageInfo info = PipelineStageInfo.newBuilder()
                                 .setExecutionId(planExecutionId)
                                 .setProjectId(projectId)
                                 .setOrgId(orgId)
                                 .setIdentifier(pipelineId)
                                 .build();

    doReturn(PipelineExecutionSummaryEntity.builder()
                 .executionTriggerInfo(ExecutionTriggerInfo.newBuilder().build())
                 .build())
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(accountId, planExecutionId);

    ExecArgs execArgs =
        ExecArgs.builder()
            .metadata(ExecutionMetadata.newBuilder().build())
            .planExecutionMetadataWithContext(PlanExecutionMetadataWithContext.builder()
                                                  .planExecutionMetadata(PlanExecutionMetadata.builder().build())
                                                  .build())
            .build();
    pipelineExecutor.setTriggerInfo(info, execArgs, accountId, jsonPayload, triggerPayload);

    assertThat(execArgs.getMetadata().getTriggerInfo()).isEqualTo(triggerInfo);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata().getTriggerJsonPayload())
        .isEqualTo(jsonPayload);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata().getTriggerPayload())
        .isEqualTo(triggerPayload);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testSetExpressionFunctorToken() {
    ExecArgs execArgs =
        ExecArgs.builder()
            .metadata(ExecutionMetadata.newBuilder().build())
            .planExecutionMetadataWithContext(PlanExecutionMetadataWithContext.builder()
                                                  .planExecutionMetadata(PlanExecutionMetadata.builder().build())
                                                  .build())
            .build();
    pipelineExecutor.setExpressionFunctorToken(execArgs, expressionFunctorToken);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata().getExpressionFunctorToken())
        .isEqualTo(expressionFunctorToken);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getExpressionFunctorToken())
        .isEqualTo(expressionFunctorToken);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRerunPipelineWithOriginalYaml_OriginalExecutionNotFound() throws IOException {
    // Setup
    boolean useOriginalPipelineYaml = true;
    // Mock settings to allow original YAML rerun
    mockOriginalYamlRerunSettings();

    // Mock finding the original execution metadata - return null to simulate not found
    doReturn(null)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(accountId, originalExecutionId, Collections.emptySet());

    // Execute and verify exception
    assertThatThrownBy(()
                           -> pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId,
                               pipelineId, moduleType, originalExecutionId, null, useV2, isDebug, null,
                               useOriginalPipelineYaml, null, false, scopeInfo))
        .isInstanceOf(HintException.class)
        .hasMessageContaining("Pipeline executions older than 30 days cannot be re-run.");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRerunPipelineWithOriginalYaml_EmptyOriginalYaml() throws IOException {
    // Setup
    boolean useOriginalPipelineYaml = true;
    // Mock settings to allow original YAML rerun
    mockOriginalYamlRerunSettings();

    // Create original metadata with empty YAML
    PlanExecutionMetadata originalMetadata =
        PlanExecutionMetadata.builder().planExecutionId(originalExecutionId).yaml("").build();

    // Mock finding the original execution metadata
    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(accountId, originalExecutionId, Collections.emptySet());

    // Execute and verify exception
    assertThatThrownBy(()
                           -> pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId,
                               pipelineId, moduleType, originalExecutionId, null, useV2, isDebug, null,
                               useOriginalPipelineYaml, null, false, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Original pipeline YAML is empty for execution id: " + originalExecutionId);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRerunPipelineWithOriginalYaml_Success() throws IOException {
    // Mock settings to allow original YAML rerun
    mockOriginalYamlRerunSettings();

    // Mock original execution metadata with YAML
    String originalYaml = "pipeline:\n  stages:\n    - stage:\n        name: Test";
    PlanExecutionMetadata originalMetadata =
        PlanExecutionMetadata.builder().yaml(originalYaml).inputSetYaml(runtimeInputYaml).build();

    // Mock for both calls to planExecutionMetadataService
    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(accountId, originalExecutionId, Collections.emptySet());

    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            eq(accountId), eq(originalExecutionId), eq(Set.of(PlanExecutionMetadataKeys.inputSetYaml)));

    // Create tags for the original execution
    List<NGTag> originalTags = Arrays.asList(
        NGTag.builder().key("env").value("test").build(), NGTag.builder().key("team").value("qa").build());

    // Mock execution summary with tags and other metadata
    PipelineExecutionSummaryEntity summaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pipelineIdentifier(pipelineId)
            .tags(originalTags)
            .entityGitDetails(EntityGitDetails.builder().branch("main").build())
            .build();

    doReturn(summaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(accountId, originalExecutionId, false);

    // Mock pipeline entity with original YAML
    PipelineEntity pipelineEntityWithOriginalYaml = pipelineEntity.withYaml(originalYaml).withTags(originalTags);
    doReturn(pipelineEntityWithOriginalYaml)
        .when(executionHelper)
        .fetchPipelineEntity(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), any());

    // Mock execution trigger info
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(originalExecutionId);

    ExecutionMetadata execMetadata = ExecutionMetadata.newBuilder().build();
    PlanExecutionMetadataWithContext metadataWithContext = PlanExecutionMetadataWithContext.builder()
                                                               .runAllStages(true)
                                                               .isOriginalYamlUsedOnRerun(true)
                                                               .tags(originalTags)
                                                               .planExecutionMetadata(originalMetadata)
                                                               .build();

    ExecArgs mockExecArgs =
        ExecArgs.builder().metadata(execMetadata).planExecutionMetadataWithContext(metadataWithContext).build();

    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    doReturn(mockExecArgs)
        .when(executionHelper)
        .buildExecutionArgs(eq(pipelineEntityWithOriginalYaml), eq(moduleType), eq(Collections.emptyList()),
            eq(Collections.emptyMap()), eq(executionTriggerInfo), eq(originalExecutionId), eq(retryExecutionParameters),
            eq(false), eq(false), isNull(), any(JsonNode.class), eq(scopeInfo), eq(true), any(), eq(false));

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), eq(execMetadata), eq(metadataWithContext),
            eq(scopeInfo), eq(true), eq(isDebug));

    // Execute rerun with useOriginalPipelineYaml=true
    PlanExecutionResponseDto response =
        pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
            originalExecutionId, runtimeInputYaml, useV2, isDebug, null, true, null, false, scopeInfo);

    // Verify response and that original YAML was used
    assertThat(response.getPlanExecution()).isEqualTo(planExecution);
    verify(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(accountId, originalExecutionId, Collections.emptySet());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRerunPipelineWithOriginalYaml() {
    String originalYaml = "pipeline:\n  name: test";

    try {
      mockOriginalYamlRerunSettings();
    } catch (IOException e) {
      Assertions.fail("Failed to mock settings: " + e.getMessage());
    }

    PlanExecutionMetadata originalMetadata =
        PlanExecutionMetadata.builder().yaml(originalYaml).inputSetYaml("inputSet: test").build();
    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(eq(accountId), eq(originalExecutionId), any());

    PipelineExecutionSummaryEntity summaryEntity = PipelineExecutionSummaryEntity.builder()
                                                       .accountId(accountId)
                                                       .orgIdentifier(orgId)
                                                       .projectIdentifier(projectId)
                                                       .pipelineIdentifier(pipelineId)
                                                       .build();

    doReturn(summaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(accountId, originalExecutionId, false);

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml("old yaml")
                                        .build();

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), any());

    ExecutionTriggerInfo executionTriggerInfo = ExecutionTriggerInfo.newBuilder().build();
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(originalExecutionId);

    doReturn(execArgs)
        .when(executionHelper)
        .buildExecutionArgs(any(PipelineEntity.class), eq(moduleType), eq(Collections.emptyList()),
            eq(Collections.emptyMap()), eq(executionTriggerInfo), eq(originalExecutionId), any(), eq(false), eq(false),
            isNull(), any(), eq(scopeInfo), eq(true), any(), eq(false));

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(), any(PlanExecutionMetadataWithContext.class),
            eq(scopeInfo), eq(true), eq(false));

    PlanExecutionResponseDto response =
        pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
            originalExecutionId, runtimeInputYaml, useV2, false, null, true, null, false, scopeInfo);

    assertThat(response.getPlanExecution()).isEqualTo(planExecution);
    verify(planExecutionMetadataService, Mockito.atLeastOnce())
        .findByPlanExecutionIdWithFieldsIncluded(eq(accountId), eq(originalExecutionId), any());
    verify(executionHelper, Mockito.atLeastOnce())
        .buildExecutionArgs(argThat(entity -> entity.getYaml().equals(originalYaml)), eq(moduleType),
            eq(Collections.emptyList()), eq(Collections.emptyMap()), any(), eq(originalExecutionId), any(), eq(false),
            eq(false), isNull(), any(), eq(scopeInfo), eq(true),
            argThat(
                context -> context.getIsOriginalYamlUsedOnRerun() != null && context.getIsOriginalYamlUsedOnRerun()),
            eq(false));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRunPipelineAsChildPipelineWithOriginalYaml() {
    String originalYaml = "pipeline:\n  name: child";
    String childExecutionId = "childExecutionId";

    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIE_PROCESS_ADDITIONAL_BASE_KEYS);
    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled(accountId, FeatureName.PIPE_REVERT_GITX_CHILD_PIPELINE_CONTEXT_ISSUE_FIX);

    try {
      mockOriginalYamlRerunSettings();
    } catch (IOException e) {
      Assertions.fail("Failed to mock settings: " + e.getMessage());
    }

    PlanExecutionMetadata originalMetadata =
        PlanExecutionMetadata.builder().yaml(originalYaml).inputSetYaml("inputSet: test").build();
    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(eq(accountId), eq(childExecutionId), any());

    PipelineExecutionSummaryEntity childSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                            .accountId(accountId)
                                                            .orgIdentifier(orgId)
                                                            .projectIdentifier(projectId)
                                                            .pipelineIdentifier(pipelineId)
                                                            .build();

    doReturn(childSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(eq(accountId), eq(childExecutionId), eq(false));

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml("old yaml")
                                        .isDraft(false)
                                        .build();

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), any());

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), any());

    ExecutionTriggerInfo executionTriggerInfo = ExecutionTriggerInfo.newBuilder().build();
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(childExecutionId);

    ExecutionMetadata childExecMetadata = ExecutionMetadata.newBuilder().build();
    PlanExecutionMetadata childPlanExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext childPlanExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .runAllStages(true)
            .isOriginalYamlUsedOnRerun(true)
            .planExecutionMetadata(childPlanExecutionMetadata)
            .build();

    ExecArgs mockChildExecArgs = ExecArgs.builder()
                                     .metadata(childExecMetadata)
                                     .planExecutionMetadataWithContext(childPlanExecutionMetadataWithContext)
                                     .build();

    doReturn(mockChildExecArgs)
        .when(executionHelper)
        .buildExecutionArgs(any(PipelineEntity.class), eq(moduleType), eq(Collections.emptyList()),
            eq(Collections.emptyMap()), eq(executionTriggerInfo), eq(childExecutionId), any(), eq(false), eq(false),
            isNull(), any(), eq(scopeInfo), eq(true), any(), eq(false));

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(), any(PlanExecutionMetadataWithContext.class),
            eq(scopeInfo), eq(true), eq(false));

    String parentExecutionId = "parentExecutionId";
    PipelineStageInfo parentInfo = PipelineStageInfo.newBuilder()
                                       .setExecutionId(parentExecutionId)
                                       .setOrgId(orgId)
                                       .setProjectId(projectId)
                                       .build();

    PlanExecutionMetadata parentMetadata =
        PlanExecutionMetadata.builder().triggerJsonPayload("{\"trigger\": \"test\"}").build();

    doReturn(parentMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(eq(accountId), eq(parentExecutionId),
            eq(Set.of(PlanExecutionMetadataKeys.triggerPayload, PlanExecutionMetadataKeys.triggerJsonPayload,
                PlanExecutionMetadataKeys.expressionFunctorToken)));

    doReturn(false)
        .when(pmsFeatureFlagHelper)
        .isEnabled(accountId, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());

    ExecutionTriggerInfo parentTriggerInfo =
        ExecutionTriggerInfo.newBuilder()
            .setTriggeredBy(
                TriggeredBy.newBuilder().setIdentifier("Admin").putExtraInfo("email", "admin@harness.io").build())
            .build();

    PipelineExecutionSummaryEntity parentSummary =
        PipelineExecutionSummaryEntity.builder().executionTriggerInfo(parentTriggerInfo).build();

    doReturn(parentSummary)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(eq(accountId), eq(parentExecutionId));

    PlanExecutionResponseDto response =
        pipelineExecutor.runPipelineAsChildPipelineWithJsonNode(accountId, orgId, projectId, pipelineId, moduleType,
            null, false, false, null, parentInfo, false, childExecutionId, true, scopeInfo);

    assertThat(response.getPlanExecution()).isEqualTo(planExecution);
    verify(planExecutionMetadataService, Mockito.atLeastOnce())
        .findByPlanExecutionIdWithFieldsIncluded(eq(accountId), eq(childExecutionId), any());
    verify(executionHelper, Mockito.atLeastOnce())
        .buildExecutionArgs(argThat(entity -> entity.getYaml().equals(originalYaml)), eq(moduleType),
            eq(Collections.emptyList()), eq(Collections.emptyMap()), any(), eq(childExecutionId), any(), eq(false),
            eq(false), isNull(), any(), eq(scopeInfo), eq(true),
            argThat(
                context -> context.getIsOriginalYamlUsedOnRerun() != null && context.getIsOriginalYamlUsedOnRerun()),
            eq(false));

    verify(pmsExecutionService, Mockito.atLeastOnce())
        .getPipelineExecutionSummaryEntity(eq(accountId), eq(parentExecutionId));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRerunPipelineWithOriginalYaml_OriginalYamlRetrieved() {
    String originalYaml = "pipeline:\n  name: test";

    try {
      mockOriginalYamlRerunSettings();
    } catch (IOException e) {
      Assertions.fail("Failed to mock settings: " + e.getMessage());
    }

    PlanExecutionMetadata originalMetadata =
        PlanExecutionMetadata.builder().yaml(originalYaml).inputSetYaml("inputSet: test").build();
    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(eq(accountId), eq(originalExecutionId), any());

    EntityGitDetails gitDetails = EntityGitDetails.builder().branch("feature-branch").repoName("test-repo").build();

    PipelineExecutionSummaryEntity summaryEntity = PipelineExecutionSummaryEntity.builder()
                                                       .accountId(accountId)
                                                       .orgIdentifier(orgId)
                                                       .projectIdentifier(projectId)
                                                       .pipelineIdentifier(pipelineId)
                                                       .entityGitDetails(gitDetails)
                                                       .build();

    doReturn(summaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(accountId, originalExecutionId, false);

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml("old yaml")
                                        .build();

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), any());

    ExecutionTriggerInfo executionTriggerInfo = ExecutionTriggerInfo.newBuilder().build();
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(originalExecutionId);

    doReturn(execArgs)
        .when(executionHelper)
        .buildExecutionArgs(any(PipelineEntity.class), eq(moduleType), eq(Collections.emptyList()),
            eq(Collections.emptyMap()), eq(executionTriggerInfo), eq(originalExecutionId), any(), eq(false), eq(false),
            isNull(), any(), eq(scopeInfo), eq(true), any(), eq(false));

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(), any(PlanExecutionMetadataWithContext.class),
            eq(scopeInfo), eq(true), eq(false));

    PlanExecutionResponseDto response =
        pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
            originalExecutionId, runtimeInputYaml, useV2, false, null, true, null, false, scopeInfo);

    assertThat(response.getPlanExecution()).isEqualTo(planExecution);
    verify(planExecutionMetadataService, Mockito.atLeastOnce())
        .findByPlanExecutionIdWithFieldsIncluded(eq(accountId), eq(originalExecutionId), any());
    verify(executionHelper, Mockito.atLeastOnce())
        .buildExecutionArgs(argThat(entity -> entity.getYaml().equals(originalYaml)), eq(moduleType),
            eq(Collections.emptyList()), eq(Collections.emptyMap()), any(), eq(originalExecutionId), any(), eq(false),
            eq(false), isNull(), any(), eq(scopeInfo), eq(true),
            argThat(
                context -> context.getIsOriginalYamlUsedOnRerun() != null && context.getIsOriginalYamlUsedOnRerun()),
            eq(false));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRerunPipelineWithOriginalYaml_ExecutionNotFound() {
    try {
      mockOriginalYamlRerunSettings();
    } catch (IOException e) {
      Assertions.fail("Failed to mock settings: " + e.getMessage());
    }

    doReturn(null)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(eq(accountId), eq(originalExecutionId), any());

    assertThatThrownBy(
        ()
            -> pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId,
                moduleType, originalExecutionId, runtimeInputYaml, useV2, false, null, true, null, false, scopeInfo))
        .isInstanceOf(HintException.class)
        .hasMessageContaining("Pipeline executions older than 30 days cannot be re-run.");
  }

  private void mockOriginalYamlRerunSettings() throws IOException {
    // Create SettingValueResponseDTO with "true" value
    SettingValueResponseDTO settingValueResponseDTO = SettingValueResponseDTO.builder()
                                                          .value("true")
                                                          .valueType(io.harness.ngsettings.SettingValueType.BOOLEAN)
                                                          .build();

    ResponseDTO<SettingValueResponseDTO> responseDTO = ResponseDTO.newResponse(settingValueResponseDTO);
    Response<ResponseDTO<SettingValueResponseDTO>> response = Response.success(responseDTO);

    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), anyString(), anyString());

    doReturn(response).when(request).execute();

    when(NGRestUtils.getResponse(any())).thenReturn(settingValueResponseDTO);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testInputSetIdentifiersTracking() {
    PipelineExecutor spyExecutor = spy(pipelineExecutor);

    PlanExecutionResponseDto responseDto = PlanExecutionResponseDto.builder()
                                               .planExecution(planExecution)
                                               .gitDetails(EntityGitDetails.builder().build())
                                               .build();

    doReturn(responseDto)
        .when(spyExecutor)
        .startPlanExecution(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any());

    List<String> mixedInputSetReferences = Arrays.asList("normal1", "overlay1", "normal2");

    doReturn(YamlUtils.readAsJsonNode(runtimeInputYaml))
        .when(validateAndMergeHelper)
        .getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(scopeInfo, pipelineId, mixedInputSetReferences,
            pipelineBranch, pipelineRepoId, null, null, false, false, false, true, (String) null);

    List<InputSetEntity> inputSetEntities = new ArrayList<>();
    inputSetEntities.add(
        InputSetEntity.builder().identifier("normal1").inputSetEntityType(InputSetEntityType.INPUT_SET).build());
    inputSetEntities.add(InputSetEntity.builder()
                             .identifier("overlay1")
                             .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                             .inputSetReferences(Arrays.asList("normal3", "normal4"))
                             .build());
    inputSetEntities.add(
        InputSetEntity.builder().identifier("normal2").inputSetEntityType(InputSetEntityType.INPUT_SET).build());
    inputSetEntities.add(
        InputSetEntity.builder().identifier("normal3").inputSetEntityType(InputSetEntityType.INPUT_SET).build());
    inputSetEntities.add(
        InputSetEntity.builder().identifier("normal4").inputSetEntityType(InputSetEntityType.INPUT_SET).build());

    doReturn(inputSetEntities).when(pmsInputSetService).list(any());

    InputSetEntity overlayEntity = InputSetEntity.builder()
                                       .identifier("overlay1")
                                       .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                       .inputSetReferences(Arrays.asList("normal3", "normal4"))
                                       .build();
    doReturn(Optional.of(overlayEntity))
        .when(pmsInputSetService)
        .getWithoutValidations(
            any(), anyString(), eq("overlay1"), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);

    MergeInputSetRequestDTOPMS mergeInputSetRequestDTOPMS =
        MergeInputSetRequestDTOPMS.builder().inputSetReferences(mixedInputSetReferences).build();

    PlanExecutionMetadataWithContext capturedContext = PlanExecutionMetadataWithContext.builder()
                                                           .isRetry(false)
                                                           .identifierOfSkipStages(new ArrayList<>())
                                                           .retryStagesIdentifier(new ArrayList<>())
                                                           .runAllStages(false)
                                                           .build();

    try {
      Method method = PipelineExecutor.class.getDeclaredMethod("resolveAndAssignInputSetsToExecution", String.class,
          String.class, String.class, String.class, List.class, PlanExecutionMetadataWithContext.class, ScopeInfo.class,
          boolean.class);
      method.setAccessible(true);
      method.invoke(spyExecutor, accountId, orgId, projectId, pipelineId, mixedInputSetReferences, capturedContext,
          scopeInfo, false);

      assertThat(capturedContext.getInputSetIdentifiers()).isNotNull();
      assertThat(capturedContext.getInputSetIdentifiers()).hasSize(4);
      assertThat(capturedContext.getInputSetIdentifiers()).contains("normal1", "normal2", "normal3", "normal4");
      assertThat(capturedContext.getInputSetIdentifiers()).doesNotContain("overlay1");

      assertThat(capturedContext.getInputSetIdentifiers()).contains("normal3", "normal4");

    } catch (Exception e) {
      Assertions.fail("Failed to invoke resolveAndAssignInputSetsToExecution: " + e);
    }

    PlanExecutionResponseDto planExecutionResponse =
        spyExecutor.runPipelineWithInputSetReferencesList(accountId, orgId, projectId, pipelineId, moduleType,
            mergeInputSetRequestDTOPMS, pipelineBranch, pipelineRepoId, null, false, null);

    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);

    verify(spyExecutor, times(1))
        .runPipelineWithInputSetReferencesList(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(moduleType),
            eq(mergeInputSetRequestDTOPMS), eq(pipelineBranch), eq(pipelineRepoId), isNull(), eq(false), eq(null));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testResolveAndAssignInputSetsForRemoteOverlayWithStaleReferencesField() {
    // Regression for PIPE-35102. For a REMOTE overlay whose Mongo-cached inputSetReferences field is stale relative to
    // its fresh git yaml (e.g. the yaml on disk was updated in git and the cache was never refreshed), the overlay
    // expansion must use references parsed from the fresh yaml — matching what ValidateAndMergeHelper does on the
    // consume path. Otherwise the "Input Sets Applied" chips drift out of sync with the values actually executed.
    PipelineExecutor spyExecutor = spy(pipelineExecutor);

    List<String> inputSetReferences = Collections.singletonList("overlay1");

    List<InputSetEntity> inputSetEntities = new ArrayList<>();
    inputSetEntities.add(InputSetEntity.builder()
                             .identifier("overlay1")
                             .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                             .storeType(StoreType.REMOTE)
                             .inputSetReferences(Arrays.asList("stale_ref_1", "stale_ref_2"))
                             .build());
    doReturn(inputSetEntities).when(pmsInputSetService).list(any());

    String freshOverlayYaml = "overlayInputSet:\n"
        + "  name: overlay1\n"
        + "  identifier: overlay1\n"
        + "  orgIdentifier: orgId\n"
        + "  projectIdentifier: projectId\n"
        + "  pipelineIdentifier: pipelineId\n"
        + "  inputSetReferences:\n"
        + "    - fresh_ref_1\n"
        + "    - fresh_ref_2\n"
        + "  tags: {}\n";
    InputSetEntity freshOverlayEntity = InputSetEntity.builder()
                                            .identifier("overlay1")
                                            .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                            .storeType(StoreType.REMOTE)
                                            .yaml(freshOverlayYaml)
                                            // stale references field, kept intentionally to prove we don't read it
                                            .inputSetReferences(Arrays.asList("stale_ref_1", "stale_ref_2"))
                                            .build();
    doReturn(Optional.of(freshOverlayEntity))
        .when(pmsInputSetService)
        .getWithoutValidations(
            any(), anyString(), eq("overlay1"), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());

    PlanExecutionMetadataWithContext capturedContext = PlanExecutionMetadataWithContext.builder()
                                                           .isRetry(false)
                                                           .identifierOfSkipStages(new ArrayList<>())
                                                           .retryStagesIdentifier(new ArrayList<>())
                                                           .runAllStages(false)
                                                           .build();

    try {
      Method method = PipelineExecutor.class.getDeclaredMethod("resolveAndAssignInputSetsToExecution", String.class,
          String.class, String.class, String.class, List.class, PlanExecutionMetadataWithContext.class, ScopeInfo.class,
          boolean.class);
      method.setAccessible(true);
      method.invoke(
          spyExecutor, accountId, orgId, projectId, pipelineId, inputSetReferences, capturedContext, scopeInfo, false);

      assertThat(capturedContext.getInputSetIdentifiers()).containsExactly("fresh_ref_1", "fresh_ref_2");
      assertThat(capturedContext.getInputSetIdentifiers()).doesNotContain("stale_ref_1", "stale_ref_2");
    } catch (Exception e) {
      Assertions.fail("Failed to invoke resolveAndAssignInputSetsToExecution: " + e);
    }
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testResolveAndAssignInputSetsForInlineOverlayStillUsesReferencesField() {
    // Complement to the REMOTE-overlay regression above: INLINE overlays must continue to use the persisted
    // inputSetReferences field verbatim (that field is the source of truth when the overlay is not backed by git).
    PipelineExecutor spyExecutor = spy(pipelineExecutor);

    List<String> inputSetReferences = Collections.singletonList("overlay1");

    List<InputSetEntity> inputSetEntities = new ArrayList<>();
    inputSetEntities.add(InputSetEntity.builder()
                             .identifier("overlay1")
                             .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                             .storeType(StoreType.INLINE)
                             .inputSetReferences(Arrays.asList("ref_a", "ref_b"))
                             .build());
    doReturn(inputSetEntities).when(pmsInputSetService).list(any());

    InputSetEntity overlayEntity = InputSetEntity.builder()
                                       .identifier("overlay1")
                                       .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                       .storeType(StoreType.INLINE)
                                       .inputSetReferences(Arrays.asList("ref_a", "ref_b"))
                                       .build();
    doReturn(Optional.of(overlayEntity))
        .when(pmsInputSetService)
        .getWithoutValidations(
            any(), anyString(), eq("overlay1"), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());

    PlanExecutionMetadataWithContext capturedContext = PlanExecutionMetadataWithContext.builder()
                                                           .isRetry(false)
                                                           .identifierOfSkipStages(new ArrayList<>())
                                                           .retryStagesIdentifier(new ArrayList<>())
                                                           .runAllStages(false)
                                                           .build();

    try {
      Method method = PipelineExecutor.class.getDeclaredMethod("resolveAndAssignInputSetsToExecution", String.class,
          String.class, String.class, String.class, List.class, PlanExecutionMetadataWithContext.class, ScopeInfo.class,
          boolean.class);
      method.setAccessible(true);
      method.invoke(
          spyExecutor, accountId, orgId, projectId, pipelineId, inputSetReferences, capturedContext, scopeInfo, false);

      assertThat(capturedContext.getInputSetIdentifiers()).containsExactly("ref_a", "ref_b");
    } catch (Exception e) {
      Assertions.fail("Failed to invoke resolveAndAssignInputSetsToExecution: " + e);
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testEmptyInputSetIdentifiers() {
    PipelineExecutor spyExecutor = spy(pipelineExecutor);

    PlanExecutionResponseDto responseDto = PlanExecutionResponseDto.builder()
                                               .planExecution(planExecution)
                                               .gitDetails(EntityGitDetails.builder().build())
                                               .build();

    doReturn(responseDto)
        .when(spyExecutor)
        .startPlanExecution(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any());

    List<String> emptyInputSetReferences = new ArrayList<>();

    doReturn(YamlUtils.readAsJsonNode(runtimeInputYaml))
        .when(validateAndMergeHelper)
        .getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(null, pipelineId, emptyInputSetReferences,
            pipelineBranch, pipelineRepoId, null, null, false, false, false, true, (String) null);

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);

    MergeInputSetRequestDTOPMS mergeInputSetRequestDTOPMS =
        MergeInputSetRequestDTOPMS.builder().inputSetReferences(emptyInputSetReferences).build();

    PlanExecutionMetadataWithContext capturedContext = PlanExecutionMetadataWithContext.builder()
                                                           .isRetry(false)
                                                           .identifierOfSkipStages(new ArrayList<>())
                                                           .retryStagesIdentifier(new ArrayList<>())
                                                           .runAllStages(false)
                                                           .build();

    try {
      Method method = PipelineExecutor.class.getDeclaredMethod("resolveAndAssignInputSetsToExecution", String.class,
          String.class, String.class, String.class, List.class, PlanExecutionMetadataWithContext.class, ScopeInfo.class,
          boolean.class);
      method.setAccessible(true);
      method.invoke(
          spyExecutor, accountId, orgId, projectId, pipelineId, emptyInputSetReferences, capturedContext, null, false);

      assertThat(capturedContext.getInputSetIdentifiers() == null || capturedContext.getInputSetIdentifiers().isEmpty())
          .isTrue();

    } catch (Exception e) {
      Assertions.fail("Failed to invoke resolveAndAssignInputSetsToExecution: " + e);
    }

    PlanExecutionResponseDto planExecutionResponse =
        spyExecutor.runPipelineWithInputSetReferencesList(accountId, orgId, projectId, pipelineId, moduleType,
            mergeInputSetRequestDTOPMS, pipelineBranch, pipelineRepoId, null, false, null);

    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testInvalidInputSetIdentifiers() {
    PipelineExecutor spyExecutor = spy(pipelineExecutor);

    List<String> invalidInputSetReferences = Arrays.asList("valid1", "invalid1", "valid2");

    doReturn(YamlUtils.readAsJsonNode(runtimeInputYaml))
        .when(validateAndMergeHelper)
        .getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(null, pipelineId, invalidInputSetReferences,
            pipelineBranch, pipelineRepoId, null, null, false, false, false, true, (String) null);

    List<InputSetEntity> inputSetEntities = new ArrayList<>();
    inputSetEntities.add(
        InputSetEntity.builder().identifier("valid1").inputSetEntityType(InputSetEntityType.INPUT_SET).build());
    inputSetEntities.add(
        InputSetEntity.builder().identifier("valid2").inputSetEntityType(InputSetEntityType.INPUT_SET).build());
    doReturn(inputSetEntities).when(pmsInputSetService).list(any());

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);

    MergeInputSetRequestDTOPMS mergeInputSetRequestDTOPMS =
        MergeInputSetRequestDTOPMS.builder().inputSetReferences(invalidInputSetReferences).build();

    PlanExecutionMetadataWithContext capturedContext = PlanExecutionMetadataWithContext.builder()
                                                           .isRetry(false)
                                                           .identifierOfSkipStages(new ArrayList<>())
                                                           .retryStagesIdentifier(new ArrayList<>())
                                                           .runAllStages(false)
                                                           .build();

    try {
      Method method = PipelineExecutor.class.getDeclaredMethod("resolveAndAssignInputSetsToExecution", String.class,
          String.class, String.class, String.class, List.class, PlanExecutionMetadataWithContext.class, ScopeInfo.class,
          boolean.class);
      method.setAccessible(true);

      assertThatThrownBy(() -> {
        try {
          method.invoke(spyExecutor, accountId, orgId, projectId, pipelineId, invalidInputSetReferences,
              capturedContext, null, false);
        } catch (Exception e) {
          if (e instanceof InvocationTargetException) {
            throw((InvocationTargetException) e).getTargetException();
          }
          throw e;
        }
      })
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("The following input set identifiers do not exist for pipeline");

    } catch (Exception e) {
      Assertions.fail("Failed to set up test for invalid input set identifiers: " + e);
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRerunPipelineWithOriginalYaml_PreservesTags() throws IOException {
    mockOriginalYamlRerunSettings();

    String originalYaml = "pipeline:\n  name: test";
    PlanExecutionMetadata originalMetadata =
        PlanExecutionMetadata.builder().yaml(originalYaml).inputSetYaml(runtimeInputYaml).build();

    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(accountId, originalExecutionId, Collections.emptySet());

    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            eq(accountId), eq(originalExecutionId), eq(Set.of(PlanExecutionMetadataKeys.inputSetYaml)));

    List<NGTag> originalTags = Arrays.asList(
        NGTag.builder().key("env").value("test").build(), NGTag.builder().key("team").value("qa").build());

    PipelineExecutionSummaryEntity summaryEntity = PipelineExecutionSummaryEntity.builder()
                                                       .accountId(accountId)
                                                       .orgIdentifier(orgId)
                                                       .projectIdentifier(projectId)
                                                       .pipelineIdentifier(pipelineId)
                                                       .tags(originalTags)
                                                       .build();

    doReturn(summaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(accountId, originalExecutionId, false);

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);

    ArgumentCaptor<PipelineEntity> pipelineEntityCaptor = ArgumentCaptor.forClass(PipelineEntity.class);

    ArgumentCaptor<PlanExecutionMetadataWithContext> metadataContextCaptor =
        ArgumentCaptor.forClass(PlanExecutionMetadataWithContext.class);

    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(originalExecutionId);

    ExecutionMetadata execMetadata = ExecutionMetadata.newBuilder().build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext metadataWithContext = PlanExecutionMetadataWithContext.builder()
                                                               .tags(originalTags)
                                                               .planExecutionMetadata(planExecutionMetadata)
                                                               .build();

    ExecArgs mockExecArgs =
        ExecArgs.builder().metadata(execMetadata).planExecutionMetadataWithContext(metadataWithContext).build();

    doReturn(mockExecArgs)
        .when(executionHelper)
        .buildExecutionArgs(pipelineEntityCaptor.capture(), eq(moduleType), eq(Collections.emptyList()),
            eq(Collections.emptyMap()), eq(executionTriggerInfo), eq(originalExecutionId), any(), eq(false), eq(false),
            isNull(), any(JsonNode.class), eq(scopeInfo), eq(true), metadataContextCaptor.capture(), eq(false));

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(ExecutionMetadata.class),
            any(PlanExecutionMetadataWithContext.class), eq(scopeInfo), eq(true), eq(isDebug));

    pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
        originalExecutionId, runtimeInputYaml, useV2, isDebug, null, true, false, scopeInfo);

    PipelineEntity capturedEntity = pipelineEntityCaptor.getValue();
    assertThat(capturedEntity).isNotNull();
    assertThat(capturedEntity.getTags()).isEqualTo(originalTags);

    PlanExecutionMetadataWithContext capturedContext = metadataContextCaptor.getValue();
    assertThat(capturedContext).isNotNull();
    assertThat(capturedContext.getTags()).isEqualTo(originalTags);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRerunPipelineWithOriginalYaml_EmptyTags() throws IOException {
    mockOriginalYamlRerunSettings();

    String originalYaml = "pipeline:\n  name: test";
    String triggerJson = "{\"trigger\":{\"type\":\"manual\"}}";
    PlanExecutionMetadata originalMetadata = PlanExecutionMetadata.builder()
                                                 .yaml(originalYaml)
                                                 .inputSetYaml(runtimeInputYaml)
                                                 .triggerJsonPayload(triggerJson)
                                                 .triggerPayload(TriggerPayload.newBuilder().build())
                                                 .build();

    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(accountId, originalExecutionId, Collections.emptySet());

    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            eq(accountId), eq(originalExecutionId), eq(Set.of(PlanExecutionMetadataKeys.inputSetYaml)));

    List<NGTag> emptyTags = Collections.emptyList();

    PipelineExecutionSummaryEntity summaryEntity = PipelineExecutionSummaryEntity.builder()
                                                       .accountId(accountId)
                                                       .orgIdentifier(orgId)
                                                       .projectIdentifier(projectId)
                                                       .pipelineIdentifier(pipelineId)
                                                       .tags(emptyTags)
                                                       .build();

    doReturn(summaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(accountId, originalExecutionId, false);

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);

    ArgumentCaptor<PipelineEntity> pipelineEntityCaptor = ArgumentCaptor.forClass(PipelineEntity.class);

    ArgumentCaptor<PlanExecutionMetadataWithContext> metadataContextCaptor =
        ArgumentCaptor.forClass(PlanExecutionMetadataWithContext.class);

    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(originalExecutionId);

    ExecutionMetadata execMetadata = ExecutionMetadata.newBuilder().build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext metadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build();

    ExecArgs mockExecArgs =
        ExecArgs.builder().metadata(execMetadata).planExecutionMetadataWithContext(metadataWithContext).build();

    doReturn(mockExecArgs)
        .when(executionHelper)
        .buildExecutionArgs(pipelineEntityCaptor.capture(), eq(moduleType), eq(Collections.emptyList()),
            eq(Collections.emptyMap()), eq(executionTriggerInfo), eq(originalExecutionId), any(), eq(false), eq(false),
            isNull(), any(JsonNode.class), eq(scopeInfo), eq(true), metadataContextCaptor.capture(), eq(false));

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(ExecutionMetadata.class),
            any(PlanExecutionMetadataWithContext.class), eq(scopeInfo), eq(true), eq(isDebug));

    pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
        originalExecutionId, runtimeInputYaml, useV2, isDebug, null, true, false, scopeInfo);

    PipelineEntity capturedEntity = pipelineEntityCaptor.getValue();
    assertThat(capturedEntity).isNotNull();
    assertThat(capturedEntity.getYaml()).isEqualTo(originalYaml);
    if (capturedEntity.getTags() != null) {
      assertThat(capturedEntity.getTags()).isEmpty();
    }

    PlanExecutionMetadataWithContext capturedContext = metadataContextCaptor.getValue();
    assertThat(capturedContext).isNotNull();
    if (capturedContext.getTags() != null) {
      assertThat(capturedContext.getTags()).isEmpty();
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRunPipelineAsChildPipelineWithJsonNode_PreservesTags() throws IOException {
    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled(accountId, FeatureName.PIPE_REVERT_GITX_CHILD_PIPELINE_CONTEXT_ISSUE_FIX);
    mockOriginalYamlRerunSettings();

    String originalYaml = "pipeline:\n  name: childTest";
    String triggerJson = "{\"trigger\":{\"type\":\"manual\"}}";
    PlanExecutionMetadata originalMetadata = PlanExecutionMetadata.builder()
                                                 .yaml(originalYaml)
                                                 .inputSetYaml(runtimeInputYaml)
                                                 .triggerJsonPayload(triggerJson)
                                                 .build();

    String childExecutionId = "child-execution-id";
    String parentExecutionId = "parent-execution-id";

    PlanExecutionMetadata parentMetadata = PlanExecutionMetadata.builder()
                                               .triggerJsonPayload(triggerJson)
                                               .triggerPayload(TriggerPayload.newBuilder().build())
                                               .expressionFunctorToken(123456L)
                                               .build();

    doReturn(parentMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(eq(accountId), eq(parentExecutionId),
            eq(Set.of(PlanExecutionMetadataKeys.triggerPayload, PlanExecutionMetadataKeys.triggerJsonPayload,
                PlanExecutionMetadataKeys.expressionFunctorToken)));

    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(accountId, childExecutionId, Collections.emptySet());

    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(
            eq(accountId), eq(childExecutionId), eq(Set.of(PlanExecutionMetadataKeys.inputSetYaml)));

    List<NGTag> childTags = Arrays.asList(NGTag.builder().key("child-env").value("dev").build(),
        NGTag.builder().key("child-team").value("dev-team").build());

    PipelineExecutionSummaryEntity childSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                            .accountId(accountId)
                                                            .orgIdentifier(orgId)
                                                            .projectIdentifier(projectId)
                                                            .pipelineIdentifier(pipelineId)
                                                            .tags(childTags)
                                                            .build();

    doReturn(childSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(accountId, childExecutionId, false);

    PipelineEntity childPipelineEntity = PipelineEntity.builder()
                                             .accountId(accountId)
                                             .orgIdentifier(orgId)
                                             .projectIdentifier(projectId)
                                             .identifier(pipelineId)
                                             .yaml(originalYaml)
                                             .build();

    doReturn(childPipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);

    ArgumentCaptor<PipelineEntity> pipelineEntityCaptor = ArgumentCaptor.forClass(PipelineEntity.class);

    ArgumentCaptor<PlanExecutionMetadataWithContext> metadataContextCaptor =
        ArgumentCaptor.forClass(PlanExecutionMetadataWithContext.class);

    ExecutionTriggerInfo childExecutionTriggerInfo = ExecutionTriggerInfo.newBuilder().build();
    doReturn(childExecutionTriggerInfo).when(executionHelper).buildTriggerInfo(childExecutionId);

    ExecutionMetadata childExecMetadata = ExecutionMetadata.newBuilder().build();
    PlanExecutionMetadata childPlanExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext childMetadataWithContext = PlanExecutionMetadataWithContext.builder()
                                                                    .runAllStages(true)
                                                                    .isOriginalYamlUsedOnRerun(true)
                                                                    .planExecutionMetadata(childPlanExecutionMetadata)
                                                                    .build();

    ExecArgs mockChildExecArgs = ExecArgs.builder()
                                     .metadata(childExecMetadata)
                                     .planExecutionMetadataWithContext(childMetadataWithContext)
                                     .build();

    doReturn(mockChildExecArgs)
        .when(executionHelper)
        .buildExecutionArgs(pipelineEntityCaptor.capture(), eq(moduleType), eq(Collections.emptyList()),
            eq(Collections.emptyMap()), eq(childExecutionTriggerInfo), eq(childExecutionId), any(), eq(false),
            eq(false), isNull(), any(JsonNode.class), eq(scopeInfo), eq(true), metadataContextCaptor.capture(),
            eq(false));

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(ExecutionMetadata.class),
            any(PlanExecutionMetadataWithContext.class), eq(scopeInfo), eq(true), eq(isDebug));

    JsonNode runtimeInputJsonNode = YamlUtils.readAsJsonNode(runtimeInputYaml);

    ExecutionTriggerInfo parentTriggerInfo =
        ExecutionTriggerInfo.newBuilder()
            .setTriggeredBy(
                TriggeredBy.newBuilder().setIdentifier("Admin").putExtraInfo("email", "admin@harness.io").build())
            .build();

    PipelineExecutionSummaryEntity parentSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                             .accountId(accountId)
                                                             .orgIdentifier(orgId)
                                                             .projectIdentifier(projectId)
                                                             .pipelineIdentifier(pipelineId)
                                                             .executionTriggerInfo(parentTriggerInfo)
                                                             .build();

    doReturn(parentSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(accountId, parentExecutionId);

    PipelineStageInfo parentInfo = PipelineStageInfo.newBuilder()
                                       .setExecutionId(parentExecutionId)
                                       .setOrgId(orgId)
                                       .setProjectId(projectId)
                                       .build();
    pipelineExecutor.runPipelineAsChildPipelineWithJsonNode(accountId, orgId, projectId, pipelineId, moduleType,
        runtimeInputJsonNode, useV2, false, null, parentInfo, isDebug, childExecutionId, true, scopeInfo);

    PipelineEntity capturedEntity = pipelineEntityCaptor.getValue();
    assertThat(capturedEntity).isNotNull();
    assertThat(capturedEntity.getTags()).isEqualTo(childTags);

    PlanExecutionMetadataWithContext capturedContext = metadataContextCaptor.getValue();
    assertThat(capturedContext).isNotNull();
    assertThat(capturedContext.getTags()).isEqualTo(childTags);
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testBuildTriggerInfo_WithRMGServiceIdentifier_ShouldSetWebhookCustomTriggerType() {
    // Test that when triggered by RMG service, the trigger type is WEBHOOK
    TriggeredBy rmgTriggeredBy =
        TriggeredBy.newBuilder().setIdentifier("ReleaseOrchestration").setUuid("rmg-uuid").build();

    ExecutionTriggerInfo expectedTriggerInfo = ExecutionTriggerInfo.newBuilder()
                                                   .setTriggerType(TriggerType.WEBHOOK_CUSTOM)
                                                   .setTriggeredBy(rmgTriggeredBy)
                                                   .setIsRerun(false)
                                                   .build();

    doReturn(expectedTriggerInfo).when(executionHelper).buildTriggerInfo(null);

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);

    doReturn(execArgs)
        .when(executionHelper)
        .buildExecutionArgs(eq(pipelineEntity), eq(moduleType), eq(Collections.emptyList()), eq(Collections.emptyMap()),
            eq(expectedTriggerInfo), isNull(), any(RetryExecutionParameters.class), eq(false), eq(false), isNull(),
            any(JsonNode.class), eq(scopeInfo), eq(true), any(PlanExecutionMetadataWithContext.class), eq(false));

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(ExecutionMetadata.class),
            any(PlanExecutionMetadataWithContext.class), eq(scopeInfo), eq(true), eq(isDebug));

    PlanExecutionResponseDto response = pipelineExecutor.runPipelineWithInputSetPipelineYaml(accountId, orgId,
        projectId, pipelineId, moduleType, runtimeInputYaml, useV2, false, null, scopeInfo, null, false, false);

    assertThat(response).isNotNull();
    assertThat(response.getPlanExecution()).isEqualTo(planExecution);

    // Verify that buildTriggerInfo was called
    verify(executionHelper, times(1)).buildTriggerInfo(null);
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testBuildTriggerInfo_WithRegularUser_ShouldSetManualTriggerType() {
    // Test that when triggered by a regular user, the trigger type is MANUAL
    TriggeredBy regularUserTriggeredBy = TriggeredBy.newBuilder()
                                             .setIdentifier("john.doe@harness.io")
                                             .setUuid("user-uuid")
                                             .putExtraInfo("email", "john.doe@harness.io")
                                             .build();

    ExecutionTriggerInfo expectedTriggerInfo = ExecutionTriggerInfo.newBuilder()
                                                   .setTriggerType(TriggerType.MANUAL)
                                                   .setTriggeredBy(regularUserTriggeredBy)
                                                   .setIsRerun(false)
                                                   .build();

    doReturn(expectedTriggerInfo).when(executionHelper).buildTriggerInfo(null);

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);

    doReturn(execArgs)
        .when(executionHelper)
        .buildExecutionArgs(eq(pipelineEntity), eq(moduleType), eq(Collections.emptyList()), eq(Collections.emptyMap()),
            eq(expectedTriggerInfo), isNull(), any(RetryExecutionParameters.class), eq(false), eq(false), isNull(),
            any(JsonNode.class), eq(scopeInfo), eq(true), any(PlanExecutionMetadataWithContext.class), eq(false));

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(ExecutionMetadata.class),
            any(PlanExecutionMetadataWithContext.class), eq(scopeInfo), eq(true), eq(isDebug));

    PlanExecutionResponseDto response = pipelineExecutor.runPipelineWithInputSetPipelineYaml(accountId, orgId,
        projectId, pipelineId, moduleType, runtimeInputYaml, useV2, false, null, scopeInfo, null, false, false);

    assertThat(response).isNotNull();
    assertThat(response.getPlanExecution()).isEqualTo(planExecution);

    // Verify that buildTriggerInfo was called
    verify(executionHelper, times(1)).buildTriggerInfo(null);
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testBuildTriggerInfo_WithRMGServiceIdentifier_OnRerun_ShouldSetWebhookCustomTriggerType() {
    // Test that when rerunning a pipeline triggered by RMG service, the trigger type is WEBHOOK
    List<String> inputSetIds = Collections.emptyList();
    doReturn(inputSetIds).when(retryExecutionHelper).getInputSetIdForRerunPipeline(accountId, originalExecutionId);

    TriggeredBy rmgTriggeredBy =
        TriggeredBy.newBuilder().setIdentifier("ReleaseOrchestration").setUuid("rmg-uuid").build();

    ExecutionTriggerInfo rmgTriggerInfo = ExecutionTriggerInfo.newBuilder()
                                              .setTriggerType(TriggerType.WEBHOOK_CUSTOM)
                                              .setTriggeredBy(rmgTriggeredBy)
                                              .setIsRerun(false)
                                              .build();

    doReturn(rmgTriggerInfo).when(executionHelper).buildTriggerInfo(originalExecutionId);

    doReturn(pipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);

    doReturn(execArgs)
        .when(executionHelper)
        .buildExecutionArgs(eq(pipelineEntity), eq(moduleType), eq(Collections.emptyList()), eq(Collections.emptyMap()),
            eq(rmgTriggerInfo), eq(originalExecutionId), any(RetryExecutionParameters.class), eq(false), eq(false),
            isNull(), any(JsonNode.class), eq(scopeInfo), eq(true), any(PlanExecutionMetadataWithContext.class),
            eq(false));

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(ExecutionMetadata.class),
            any(PlanExecutionMetadataWithContext.class), eq(scopeInfo), eq(true), eq(isDebug));

    PlanExecutionResponseDto response =
        pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
            originalExecutionId, runtimeInputYaml, useV2, false, null, false, false, scopeInfo);

    assertThat(response).isNotNull();
    assertThat(response.getPlanExecution()).isEqualTo(planExecution);

    // Verify that buildTriggerInfo was called with original execution ID
    verify(executionHelper, times(1)).buildTriggerInfo(originalExecutionId);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRetryPipelineWithExpressionValues() {
    String previousExecutionId = "prev_exec_id";
    List<String> retryStagesIdentifier = Arrays.asList("stage1", "stage2");
    String inputSetPipelineYaml = "pipeline:\n  variables:\n  - name: var1\n    value: <+input>";
    java.util.Map<String, String> expressionValues = new java.util.HashMap<>();
    expressionValues.put("expr1", "value1");
    expressionValues.put("expr2", "value2");

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(runtimeInputYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();

    RetryGroup retryGroup = RetryGroup.builder().build();

    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().inputSetYaml(inputSetPipelineYaml).build();

    when(executionHelper.fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo))
        .thenReturn(pipelineEntity);
    when(retryExecutionHelper.validateRetryStagesIdentifiersAndGetRetryGroup(
             eq(previousExecutionId), eq(retryStagesIdentifier), eq(pipelineEntity.getHarnessVersion()), anyBoolean()))
        .thenReturn(retryGroup);
    when(planExecutionMetadataService.findByPlanExecutionId(accountId, previousExecutionId))
        .thenReturn(Optional.of(planExecutionMetadata));
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name()))
        .thenReturn(false);
    when(pipelineTemplateHelper.resolveOnlyPipelineTemplateRefAndMerge(
             accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity.getStoreType(), "false", "0"))
        .thenReturn(runtimeInputYaml);
    when(retryExecutionHelper.getInputSetIdForRerunPipeline(accountId, previousExecutionId))
        .thenReturn(Collections.emptyList());

    PlanExecutionMetadataWithContext testMetadataWithContext = PlanExecutionMetadataWithContext.builder().build();
    ExecutionMetadata testExecutionMetadata = ExecutionMetadata.newBuilder().build();
    ExecArgs testExecArgs = ExecArgs.builder()
                                .metadata(testExecutionMetadata)
                                .planExecutionMetadataWithContext(testMetadataWithContext)
                                .build();

    ExecutionTriggerInfo testTriggerInfo = ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build();
    doReturn(testTriggerInfo).when(executionHelper).buildTriggerInfo(null);

    doReturn(testExecArgs)
        .when(executionHelper)
        .buildExecutionArgs(eq(pipelineEntity), isNull(), isNull(), eq(expressionValues), eq(testTriggerInfo),
            eq(previousExecutionId), any(RetryExecutionParameters.class), eq(false), eq(false), eq(""),
            any(JsonNode.class), any(PlanExecutionMetadataWithContext.class), eq(true), eq(scopeInfo));

    PlanExecution testPlanExecution = PlanExecution.builder().uuid("plan_exec_id").build();
    doReturn(testPlanExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), eq(testExecutionMetadata), eq(testMetadataWithContext),
            eq(false), eq(scopeInfo));

    PlanExecutionResponseDto response = pipelineExecutor.retryPipelineWithInputSetPipelineYaml(accountId, orgId,
        projectId, pipelineId, null, inputSetPipelineYaml, previousExecutionId, retryStagesIdentifier, true, false,
        false, "", false, scopeInfo, expressionValues);

    assertThat(response).isNotNull();
    assertThat(response.getPlanExecution()).isEqualTo(testPlanExecution);

    verify(executionHelper)
        .buildExecutionArgs(eq(pipelineEntity), isNull(), isNull(), eq(expressionValues), eq(testTriggerInfo),
            eq(previousExecutionId), any(RetryExecutionParameters.class), eq(false), eq(false), eq(""),
            any(JsonNode.class), any(PlanExecutionMetadataWithContext.class), eq(true), eq(scopeInfo));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRetryPipelineWithNullExpressionValuesFallback() {
    String previousExecutionId = "prev_exec_id";
    List<String> retryStagesIdentifier = Arrays.asList("stage1");
    String inputSetPipelineYaml = "pipeline:\n  variables:\n  - name: var1\n    value: <+input>";

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(runtimeInputYaml)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();

    RetryGroup retryGroup = RetryGroup.builder().build();

    java.util.Map<String, String> previousExpressionValues = new java.util.HashMap<>();
    previousExpressionValues.put("previousExpr", "previousValue");

    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().inputSetYaml(inputSetPipelineYaml).build();

    PlanExecution previousPlanExecution =
        PlanExecution.builder()
            .stagesExecutionMetadata(
                StagesExecutionMetadata.builder().expressionValues(previousExpressionValues).build())
            .build();

    when(executionHelper.fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo))
        .thenReturn(pipelineEntity);
    when(retryExecutionHelper.validateRetryStagesIdentifiersAndGetRetryGroup(
             eq(previousExecutionId), eq(retryStagesIdentifier), eq(pipelineEntity.getHarnessVersion()), anyBoolean()))
        .thenReturn(retryGroup);
    when(planExecutionMetadataService.findByPlanExecutionId(accountId, previousExecutionId))
        .thenReturn(Optional.of(planExecutionMetadata));
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name()))
        .thenReturn(true);
    when(planExecutionService.getWithFieldsIncludedOptional(eq(previousExecutionId), any(Set.class)))
        .thenReturn(Optional.of(previousPlanExecution));
    when(pipelineTemplateHelper.resolveOnlyPipelineTemplateRefAndMerge(
             accountId, orgId, projectId, pipelineEntity.getYaml(), pipelineEntity.getStoreType(), "false", "0"))
        .thenReturn(runtimeInputYaml);
    when(retryExecutionHelper.getInputSetIdForRerunPipeline(accountId, previousExecutionId))
        .thenReturn(Collections.emptyList());

    PlanExecutionMetadataWithContext testMetadataWithContext = PlanExecutionMetadataWithContext.builder().build();
    ExecutionMetadata testExecutionMetadata = ExecutionMetadata.newBuilder().build();
    ExecArgs testExecArgs = ExecArgs.builder()
                                .metadata(testExecutionMetadata)
                                .planExecutionMetadataWithContext(testMetadataWithContext)
                                .build();

    ExecutionTriggerInfo testTriggerInfo = ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build();
    doReturn(testTriggerInfo).when(executionHelper).buildTriggerInfo(null);

    doReturn(testExecArgs)
        .when(executionHelper)
        .buildExecutionArgs(eq(pipelineEntity), isNull(), isNull(), eq(previousExpressionValues), eq(testTriggerInfo),
            eq(previousExecutionId), any(RetryExecutionParameters.class), eq(false), eq(false), eq(""),
            any(JsonNode.class), any(PlanExecutionMetadataWithContext.class), eq(true), eq(scopeInfo));

    PlanExecution testPlanExecution = PlanExecution.builder().uuid("plan_exec_id").build();
    doReturn(testPlanExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), eq(testExecutionMetadata), eq(testMetadataWithContext),
            eq(false), eq(scopeInfo));

    PlanExecutionResponseDto response = pipelineExecutor.retryPipelineWithInputSetPipelineYaml(accountId, orgId,
        projectId, pipelineId, null, inputSetPipelineYaml, previousExecutionId, retryStagesIdentifier, true, false,
        false, "", false, scopeInfo, null);

    assertThat(response).isNotNull();
    assertThat(response.getPlanExecution()).isEqualTo(testPlanExecution);

    verify(executionHelper)
        .buildExecutionArgs(eq(pipelineEntity), isNull(), isNull(), eq(previousExpressionValues), eq(testTriggerInfo),
            eq(previousExecutionId), any(RetryExecutionParameters.class), eq(false), eq(false), eq(""),
            any(JsonNode.class), any(PlanExecutionMetadataWithContext.class), eq(true), eq(scopeInfo));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testStartDynamicExecutionTagsFromDynamicYaml() {
    String dynamicPipelineYaml = "pipeline:\n"
        + "  identifier: testPipeline\n"
        + "  tags:\n"
        + "    env: prod\n"
        + "    version: v2\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n";

    SettingValueResponseDTO settingValueResponseDTO = SettingValueResponseDTO.builder().value("true").build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), isNull(), isNull());
    when(NGRestUtils.getResponse(any())).thenReturn(settingValueResponseDTO);

    PipelineEntity dynamicPipelineEntity =
        PipelineEntity.builder().allowDynamicExecutions(true).accountId(accountId).build();
    doReturn(dynamicPipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(scopeInfo));

    PlanExecution expectedPlanExecution = PlanExecution.builder().uuid("dynamic_exec_id").build();

    ExecutionTriggerInfo dynamicTriggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build();
    doReturn(dynamicTriggerInfo).when(executionHelper).buildTriggerInfo(null);

    doAnswer(invocation -> {
      PlanExecutionMetadataWithContext ctx = invocation.getArgument(13);
      ExecutionMetadata meta = ExecutionMetadata.newBuilder().build();
      return ExecArgs.builder().metadata(meta).planExecutionMetadataWithContext(ctx).build();
    })
        .when(executionHelper)
        .buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(), anyBoolean(),
            anyBoolean(), any(), any(), any(), anyBoolean(), any(PlanExecutionMetadataWithContext.class), anyBoolean());

    ArgumentCaptor<PlanExecutionMetadataWithContext> metadataCaptor =
        ArgumentCaptor.forClass(PlanExecutionMetadataWithContext.class);
    doReturn(expectedPlanExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(ExecutionMetadata.class), metadataCaptor.capture(),
            any(ScopeInfo.class), anyBoolean(), anyBoolean());

    PlanExecutionResponseDto response = pipelineExecutor.startDynamicExecution(
        accountId, orgId, projectId, pipelineId, dynamicPipelineYaml, moduleType, useV2, false, null, scopeInfo, false);

    assertThat(response).isNotNull();
    assertThat(response.getPlanExecution()).isEqualTo(expectedPlanExecution);

    PlanExecutionMetadataWithContext capturedContext = metadataCaptor.getValue();
    assertThat(capturedContext.getIsDynamicExecution()).isTrue();
    assertThat(capturedContext.getTags()).isNotNull();
    assertThat(capturedContext.getTags()).extracting(NGTag::getKey).contains("env", "version");
    assertThat(capturedContext.getTags()).extracting(NGTag::getValue).contains("prod", "v2");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testStartDynamicExecutionTagsEmptyOnMalformedYaml() {
    String malformedYaml = "pipeline: {\n  unclosed bracket";

    SettingValueResponseDTO settingValueResponseDTO = SettingValueResponseDTO.builder().value("true").build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), isNull(), isNull());
    when(NGRestUtils.getResponse(any())).thenReturn(settingValueResponseDTO);

    PipelineEntity dynamicPipelineEntity =
        PipelineEntity.builder().allowDynamicExecutions(true).accountId(accountId).build();
    doReturn(dynamicPipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(scopeInfo));

    PlanExecution expectedPlanExecution = PlanExecution.builder().uuid("dynamic_exec_id").build();

    ExecutionTriggerInfo dynamicTriggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build();
    doReturn(dynamicTriggerInfo).when(executionHelper).buildTriggerInfo(null);

    doAnswer(invocation -> {
      PlanExecutionMetadataWithContext ctx = invocation.getArgument(13);
      ExecutionMetadata meta = ExecutionMetadata.newBuilder().build();
      return ExecArgs.builder().metadata(meta).planExecutionMetadataWithContext(ctx).build();
    })
        .when(executionHelper)
        .buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(), anyBoolean(),
            anyBoolean(), any(), any(), any(), anyBoolean(), any(PlanExecutionMetadataWithContext.class), anyBoolean());

    ArgumentCaptor<PlanExecutionMetadataWithContext> metadataCaptor =
        ArgumentCaptor.forClass(PlanExecutionMetadataWithContext.class);
    doReturn(expectedPlanExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(ExecutionMetadata.class), metadataCaptor.capture(),
            any(ScopeInfo.class), anyBoolean(), anyBoolean());

    PlanExecutionResponseDto response = pipelineExecutor.startDynamicExecution(
        accountId, orgId, projectId, pipelineId, malformedYaml, moduleType, useV2, false, null, scopeInfo, false);

    assertThat(response).isNotNull();
    assertThat(response.getPlanExecution()).isEqualTo(expectedPlanExecution);

    PlanExecutionMetadataWithContext capturedContext = metadataCaptor.getValue();
    assertThat(capturedContext.getIsDynamicExecution()).isTrue();
    assertThat(capturedContext.getTags()).isNotNull();
    assertThat(capturedContext.getTags()).isEmpty();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testStartDynamicExecutionInternalApiBypassesAccountAndPipelineSettings() {
    // Both account-level setting and pipeline-level config are DISABLED — the public path would
    // throw InvalidRequestException. The internal API must skip both checks and proceed.
    String dynamicPipelineYaml = "pipeline:\n"
        + "  identifier: testPipeline\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n";

    // Account-level setting disabled (would normally block the public path).
    SettingValueResponseDTO disabledSetting = SettingValueResponseDTO.builder().value("false").build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), isNull(), isNull());
    when(NGRestUtils.getResponse(any())).thenReturn(disabledSetting);

    // Pipeline-level allowDynamicExecutions also disabled (would normally block the public path).
    PipelineEntity dynamicPipelineEntity =
        PipelineEntity.builder().allowDynamicExecutions(false).accountId(accountId).build();
    doReturn(dynamicPipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(scopeInfo));

    PlanExecution expectedPlanExecution = PlanExecution.builder().uuid("internal_dynamic_exec_id").build();

    ExecutionTriggerInfo dynamicTriggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build();
    doReturn(dynamicTriggerInfo).when(executionHelper).buildTriggerInfo(null);

    doAnswer(invocation -> {
      PlanExecutionMetadataWithContext ctx = invocation.getArgument(13);
      ExecutionMetadata meta = ExecutionMetadata.newBuilder().build();
      return ExecArgs.builder().metadata(meta).planExecutionMetadataWithContext(ctx).build();
    })
        .when(executionHelper)
        .buildExecutionArgs(any(PipelineEntity.class), any(), any(), any(), any(), any(), any(), anyBoolean(),
            anyBoolean(), any(), any(), any(), anyBoolean(), any(PlanExecutionMetadataWithContext.class), anyBoolean());

    doReturn(expectedPlanExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(ExecutionMetadata.class),
            any(PlanExecutionMetadataWithContext.class), any(ScopeInfo.class), anyBoolean(), anyBoolean());

    // isInternalApi = true — execution should proceed despite both checks being disabled.
    PlanExecutionResponseDto response = pipelineExecutor.startDynamicExecution(
        accountId, orgId, projectId, pipelineId, dynamicPipelineYaml, moduleType, useV2, false, null, scopeInfo, true);

    assertThat(response).isNotNull();
    assertThat(response.getPlanExecution()).isEqualTo(expectedPlanExecution);

    // The account-level setting must NOT be queried when isInternalApi=true.
    verify(ngSettingsClient, times(0)).getSetting(anyString(), anyString(), isNull(), isNull());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testStartDynamicExecutionPublicApiThrowsWhenBothSettingsDisabled() {
    // Sanity-check the negative case the internal API is meant to bypass: with isInternalApi=false
    // and both checks disabled, we expect the existing InvalidRequestException.
    String dynamicPipelineYaml = "pipeline:\n"
        + "  identifier: testPipeline\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n";

    SettingValueResponseDTO disabledSetting = SettingValueResponseDTO.builder().value("false").build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), isNull(), isNull());
    when(NGRestUtils.getResponse(any())).thenReturn(disabledSetting);

    PipelineEntity dynamicPipelineEntity =
        PipelineEntity.builder().allowDynamicExecutions(false).accountId(accountId).build();
    doReturn(dynamicPipelineEntity)
        .when(executionHelper)
        .fetchPipelineEntity(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), eq(scopeInfo));

    assertThatThrownBy(()
                           -> pipelineExecutor.startDynamicExecution(accountId, orgId, projectId, pipelineId,
                               dynamicPipelineYaml, moduleType, useV2, false, null, scopeInfo, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Dynamic execution is disabled");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testStartDirectExecution() {
    String pipelineYaml = "pipeline:\n  identifier: testPipeline\n  stages:\n    - stage:\n        identifier: s1";
    String inputsYaml = null;
    String notes = "test notes";

    PlanExecution expectedPlanExecution = PlanExecution.builder().uuid("direct_exec_id").build();
    ExecutionPrincipalInfo newPrincipalInfo =
        ExecutionPrincipalInfo.newBuilder().setPrincipalType(PrincipalType.USER).build();
    doReturn(newPrincipalInfo).when(principalInfoHelper).getPrincipalInfoFromSecurityContext();

    doReturn(expectedPlanExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(ExecutionMetadata.class),
            any(PlanExecutionMetadataWithContext.class), eq(scopeInfo));

    PlanExecutionResponseDto response = pipelineExecutor.startDirectExecution(
        accountId, orgId, projectId, pipelineId, pipelineYaml, inputsYaml, moduleType, useV2, false, notes, scopeInfo);

    assertThat(response).isNotNull();
    assertThat(response.getPlanExecution()).isEqualTo(expectedPlanExecution);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testEnforceOpaGitxAtExecution_BlockedByPolicyDenial() {
    GovernanceMetadata gm = GovernanceMetadata.newBuilder().setDeny(true).setMessage("Policy denied").build();
    OpaGitxCoordinates coords =
        OpaGitxCoordinates.of(accountId, "https://github.com/org/repo", ".harness/pipeline.yaml", "main", "commit1");
    OpaEnforcementResult blockedResult =
        OpaEnforcementResult.evaluated(OpaGitxStatus.ERROR, gm, "Policy denied", "abc123", coords, 1700000000000L);

    PipelineEntity entityWithIds = PipelineEntity.builder()
                                       .accountId(accountId)
                                       .orgIdentifier(orgId)
                                       .projectIdentifier(projectId)
                                       .identifier(pipelineId)
                                       .allowStageExecutions(true)
                                       .build();
    doReturn(entityWithIds)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(null);
    RetryExecutionParameters retryParams = RetryExecutionParameters.builder().isRetry(false).build();
    doReturn(execArgs)
        .when(executionHelper)
        .buildExecutionArgs(entityWithIds, moduleType, Collections.emptyList(), Collections.emptyMap(),
            executionTriggerInfo, null, retryParams, false, false, null, YamlUtils.readAsJsonNode(runtimeInputYaml),
            scopeInfo, true, planExecutionMetadataWithContext, false);

    doReturn(blockedResult).when(pipelineOpaStatusHandler).doOpaOnSaveEvaluation(any(), any(), any());

    assertThatThrownBy(
        ()
            -> pipelineExecutor.runPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
                runtimeInputYaml, useV2, false, null, scopeInfo, null, false, false))
        .isInstanceOf(PolicyEvaluationFailureException.class)
        .satisfies(ex -> {
          PolicyEvaluationFailureException pefe = (PolicyEvaluationFailureException) ex;
          assertThat(pefe.getMetadata()).isInstanceOf(OpaOnSaveStatusErrorDTO.class);
          OpaOnSaveStatusErrorDTO errorDTO = (OpaOnSaveStatusErrorDTO) pefe.getMetadata();
          OpaOnSaveStatusDTO opaStatus = errorDTO.getOpaOnSaveStatusDTO();
          assertThat(opaStatus).isNotNull();
          assertThat(opaStatus.getStatus()).isEqualTo(OpaGitxStatus.ERROR);
          assertThat(opaStatus.getGovernanceMetadata()).isEqualTo(gm);
          assertThat(opaStatus.getLastValidCommitId()).isEqualTo("abc123");
          assertThat(opaStatus.getRepoURL()).isEqualTo("https://github.com/org/repo");
          assertThat(opaStatus.getFilePath()).isEqualTo(".harness/pipeline.yaml");
          assertThat(opaStatus.getEvaluatedAtCommitId()).isEqualTo("commit1");
          assertThat(opaStatus.getEvaluatedAt()).isEqualTo(1700000000000L);
        });
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testEnforceOpaGitxAtExecution_BlockedByOpaUnavailable() {
    OpaGitxCoordinates coords =
        OpaGitxCoordinates.of(accountId, "https://github.com/org/repo", ".harness/pipeline.yaml", "main", "commit2");
    OpaEnforcementResult unavailableResult = OpaEnforcementResult.evaluated(
        OpaGitxStatus.UNKNOWN, null, "OPA unreachable", "def456", coords, 1700000001000L);

    PipelineEntity entityWithIds = PipelineEntity.builder()
                                       .accountId(accountId)
                                       .orgIdentifier(orgId)
                                       .projectIdentifier(projectId)
                                       .identifier(pipelineId)
                                       .allowStageExecutions(true)
                                       .build();
    doReturn(entityWithIds)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(null);
    RetryExecutionParameters retryParams = RetryExecutionParameters.builder().isRetry(false).build();
    doReturn(execArgs)
        .when(executionHelper)
        .buildExecutionArgs(entityWithIds, moduleType, Collections.emptyList(), Collections.emptyMap(),
            executionTriggerInfo, null, retryParams, false, false, null, YamlUtils.readAsJsonNode(runtimeInputYaml),
            scopeInfo, true, planExecutionMetadataWithContext, false);

    doReturn(unavailableResult).when(pipelineOpaStatusHandler).doOpaOnSaveEvaluation(any(), any(), any());

    assertThatThrownBy(
        ()
            -> pipelineExecutor.runPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
                runtimeInputYaml, useV2, false, null, scopeInfo, null, false, false))
        .isInstanceOf(PolicyEvaluationFailureException.class)
        .satisfies(ex -> {
          PolicyEvaluationFailureException pefe = (PolicyEvaluationFailureException) ex;
          assertThat(pefe.getMessage()).contains("OPA policy service is unavailable");
          assertThat(pefe.getMetadata()).isInstanceOf(OpaOnSaveStatusErrorDTO.class);
          OpaOnSaveStatusErrorDTO errorDTO = (OpaOnSaveStatusErrorDTO) pefe.getMetadata();
          OpaOnSaveStatusDTO opaStatus = errorDTO.getOpaOnSaveStatusDTO();
          assertThat(opaStatus).isNotNull();
          assertThat(opaStatus.getStatus()).isEqualTo(OpaGitxStatus.UNKNOWN);
          assertThat(opaStatus.getLastValidCommitId()).isEqualTo("def456");
          assertThat(opaStatus.getGovernanceMetadata()).isNull();
          assertThat(opaStatus.getRepoURL()).isEqualTo("https://github.com/org/repo");
          assertThat(opaStatus.getFilePath()).isEqualTo(".harness/pipeline.yaml");
          assertThat(opaStatus.getEvaluatedAtCommitId()).isEqualTo("commit2");
          assertThat(opaStatus.getEvaluatedAt()).isEqualTo(1700000001000L);
        });
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testEnforceOpaGitxAtExecution_NullScopeInfo_ThrowsException() {
    PipelineEntity entityWithIds = PipelineEntity.builder()
                                       .accountId(accountId)
                                       .orgIdentifier(orgId)
                                       .projectIdentifier(projectId)
                                       .identifier(pipelineId)
                                       .allowStageExecutions(true)
                                       .build();
    ExecArgs args = ExecArgs.builder()
                        .metadata(metadata)
                        .planExecutionMetadataWithContext(
                            planExecutionMetadataWithContext.withPlanExecutionMetadata(planExecutionMetadata))
                        .build();

    assertThatThrownBy(() -> pipelineExecutor.applyOpaOnSaveGate(entityWithIds, args, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unable to evaluate governance policies")
        .hasMessageContaining(pipelineId);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRerunWithOriginalYaml_SkipsOpaOnSaveEnforcement() throws IOException {
    String originalYaml = "pipeline:\n  name: test";
    mockOriginalYamlRerunSettings();

    PlanExecutionMetadata originalMetadata =
        PlanExecutionMetadata.builder().yaml(originalYaml).inputSetYaml("inputSet: test").build();
    doReturn(originalMetadata)
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(eq(accountId), eq(originalExecutionId), any());

    PipelineExecutionSummaryEntity summaryEntity = PipelineExecutionSummaryEntity.builder()
                                                       .accountId(accountId)
                                                       .orgIdentifier(orgId)
                                                       .projectIdentifier(projectId)
                                                       .pipelineIdentifier(pipelineId)
                                                       .build();
    doReturn(summaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(accountId, originalExecutionId, false);

    PipelineEntity entity = PipelineEntity.builder()
                                .accountId(accountId)
                                .orgIdentifier(orgId)
                                .projectIdentifier(projectId)
                                .identifier(pipelineId)
                                .yaml("old yaml")
                                .build();
    doReturn(entity)
        .when(executionHelper)
        .fetchPipelineEntity(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), any());

    ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().build();
    doReturn(triggerInfo).when(executionHelper).buildTriggerInfo(originalExecutionId);

    PlanExecutionMetadataWithContext originalYamlContext =
        PlanExecutionMetadataWithContext.builder().runAllStages(true).isOriginalYamlUsedOnRerun(true).build();
    ExecArgs originalYamlExecArgs =
        ExecArgs.builder()
            .metadata(metadata)
            .planExecutionMetadataWithContext(originalYamlContext.withPlanExecutionMetadata(planExecutionMetadata))
            .build();

    doReturn(originalYamlExecArgs)
        .when(executionHelper)
        .buildExecutionArgs(any(PipelineEntity.class), eq(moduleType), eq(Collections.emptyList()),
            eq(Collections.emptyMap()), eq(triggerInfo), eq(originalExecutionId), any(), eq(false), eq(false), isNull(),
            any(), eq(scopeInfo), eq(true), any(), eq(false));

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(), any(PlanExecutionMetadataWithContext.class),
            eq(scopeInfo), eq(true), eq(false));

    Mockito.reset(pipelineOpaStatusHandler);
    doReturn(OpaEnforcementResult.notApplicable())
        .when(pipelineOpaStatusHandler)
        .doOpaOnSaveEvaluation(any(), any(), any());

    pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
        originalExecutionId, runtimeInputYaml, useV2, false, null, true, null, false, scopeInfo);

    verify(pipelineOpaStatusHandler, times(1)).doOpaOnSaveEvaluation(any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testStandardRerun_StillEnforcesOpaOnSave() {
    List<String> inputSetIds = Collections.emptyList();
    doReturn(inputSetIds).when(retryExecutionHelper).getInputSetIdForRerunPipeline(accountId, originalExecutionId);

    PipelineEntity entityWithIds = PipelineEntity.builder()
                                       .accountId(accountId)
                                       .orgIdentifier(orgId)
                                       .projectIdentifier(projectId)
                                       .identifier(pipelineId)
                                       .allowStageExecutions(true)
                                       .build();
    RetryExecutionParameters retryParams = RetryExecutionParameters.builder().isRetry(false).build();
    doReturn(entityWithIds)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(originalExecutionId);
    doReturn(execArgs)
        .when(executionHelper)
        .buildExecutionArgs(entityWithIds, moduleType, Collections.emptyList(), Collections.emptyMap(),
            executionTriggerInfo, originalExecutionId, retryParams, false, false, null,
            YamlUtils.readAsJsonNode(runtimeInputYaml), scopeInfo, true, planExecutionMetadataWithContext, false);
    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(), any(PlanExecutionMetadataWithContext.class),
            eq(scopeInfo), eq(true), eq(false));

    Mockito.reset(pipelineOpaStatusHandler);
    doReturn(OpaEnforcementResult.notApplicable())
        .when(pipelineOpaStatusHandler)
        .doOpaOnSaveEvaluation(any(), any(), any());

    pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
        originalExecutionId, runtimeInputYaml, useV2, false, null, false, false, scopeInfo);

    verify(pipelineOpaStatusHandler, times(1)).doOpaOnSaveEvaluation(any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testNormalRun_StillEnforcesOpaOnSave() {
    PipelineEntity entityWithIds = PipelineEntity.builder()
                                       .accountId(accountId)
                                       .orgIdentifier(orgId)
                                       .projectIdentifier(projectId)
                                       .identifier(pipelineId)
                                       .allowStageExecutions(true)
                                       .build();
    RetryExecutionParameters retryParams = RetryExecutionParameters.builder().isRetry(false).build();
    doReturn(entityWithIds)
        .when(executionHelper)
        .fetchPipelineEntity(accountId, orgId, projectId, pipelineId, scopeInfo);
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(null);
    doReturn(execArgs)
        .when(executionHelper)
        .buildExecutionArgs(entityWithIds, moduleType, Collections.emptyList(), Collections.emptyMap(),
            executionTriggerInfo, null, retryParams, false, false, null, YamlUtils.readAsJsonNode(runtimeInputYaml),
            scopeInfo, true, planExecutionMetadataWithContext, false);
    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(eq(accountId), eq(orgId), eq(projectId), any(), any(PlanExecutionMetadataWithContext.class),
            eq(scopeInfo), eq(true), eq(false));

    Mockito.reset(pipelineOpaStatusHandler);
    doReturn(OpaEnforcementResult.notApplicable())
        .when(pipelineOpaStatusHandler)
        .doOpaOnSaveEvaluation(any(), any(), any());

    pipelineExecutor.runPipelineWithInputSetPipelineYaml(accountId, orgId, projectId, pipelineId, moduleType,
        runtimeInputYaml, useV2, false, null, scopeInfo, null, false, false);

    verify(pipelineOpaStatusHandler, times(1)).doOpaOnSaveEvaluation(any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testApplyOpaOnSaveGate_FFDisabled_NeverCallsHandler() {
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.PIPE_OPA_GITX_ENFORCEMENT));
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId(accountId)
                                .orgIdentifier(orgId)
                                .projectIdentifier(projectId)
                                .identifier(pipelineId)
                                .build();
    ExecArgs args = ExecArgs.builder()
                        .metadata(metadata)
                        .planExecutionMetadataWithContext(
                            planExecutionMetadataWithContext.withPlanExecutionMetadata(planExecutionMetadata))
                        .build();

    pipelineExecutor.applyOpaOnSaveGate(entity, args, scopeInfo);

    verify(pipelineOpaStatusHandler, Mockito.never()).doOpaOnSaveEvaluation(any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testApplyOpaOnSaveGate_FFDisabledWithMissingScope_IsNoOp() {
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(accountId, FeatureName.PIPE_OPA_GITX_ENFORCEMENT);
    PipelineEntity entity = PipelineEntity.builder().accountId(accountId).identifier(pipelineId).build();

    pipelineExecutor.applyOpaOnSaveGate(entity, execArgs, null);

    verify(pipelineOpaStatusHandler, Mockito.never()).doOpaOnSaveEvaluation(any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testApplyOpaOnSaveGate_FFEnabled_CallsHandler() {
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.PIPE_OPA_GITX_ENFORCEMENT));
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId(accountId)
                                .orgIdentifier(orgId)
                                .projectIdentifier(projectId)
                                .identifier(pipelineId)
                                .build();
    ExecArgs args = ExecArgs.builder()
                        .metadata(metadata)
                        .planExecutionMetadataWithContext(
                            planExecutionMetadataWithContext.withPlanExecutionMetadata(planExecutionMetadata))
                        .build();
    Mockito.reset(pipelineOpaStatusHandler);
    doReturn(OpaEnforcementResult.notApplicable())
        .when(pipelineOpaStatusHandler)
        .doOpaOnSaveEvaluation(any(), any(), any());

    pipelineExecutor.applyOpaOnSaveGate(entity, args, scopeInfo);

    verify(pipelineOpaStatusHandler, times(1)).doOpaOnSaveEvaluation(any(), any(), any());
  }
}

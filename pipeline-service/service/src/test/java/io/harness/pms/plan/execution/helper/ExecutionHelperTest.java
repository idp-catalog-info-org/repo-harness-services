/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION;
import static io.harness.beans.FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.ngsettings.SettingIdentifiers.RUN_RBAC_VALIDATION_BEFORE_EXECUTING_INLINE_PIPELINES;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.pms.contracts.plan.TriggerType.RELEASE_ORCHESTRATION;
import static io.harness.pms.contracts.plan.TriggerType.WEBHOOK;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.KAPIL_GARG;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionGraph;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.engine.utils.OpaPolicyEvaluationHelper;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.execution.PlanCreationRequest;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.expression.RuntimeInputValuesValidatorV1;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.opaclient.model.OpaConstants;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.RerunInfo;
import io.harness.pms.contracts.plan.RetryExecutionInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.pipeline.service.yamlConversion.PipelineYamlConversionEntityService;
import io.harness.pms.pipeline.validation.async.beans.BarrierCycleValidator;
import io.harness.pms.pipelinestage.v1.helper.PipelineStageHelperV1;
import io.harness.pms.plan.creation.lookup.intfc.NodeTypeLookupService;
import io.harness.pms.plan.execution.PlanExecutionUtils;
import io.harness.pms.plan.execution.PmsExecutionSummaryDtoUpdateHelper;
import io.harness.pms.plan.execution.RetryExecutionHelper;
import io.harness.pms.plan.execution.RollbackGraphGenerator;
import io.harness.pms.plan.execution.RollbackModeExecutionHelper;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.ProcessStageExecutionInfoResult;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionDetailDTO;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.rbac.validator.PipelineRbacService;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
@PrepareForTest({PlanExecutionUtils.class, UUIDGenerator.class})
public class ExecutionHelperTest extends CategoryTest {
  @InjectMocks ExecutionHelper executionHelper;
  @Mock PMSPipelineService pmsPipelineService;

  @Mock PipelineMetadataService pipelineMetadataService;

  @Mock PipelineGovernanceService pipelineGovernanceService;
  @Mock TriggeredByHelper triggeredByHelper;
  @Mock PlanExecutionService planExecutionService;
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock PrincipalInfoHelper principalInfoHelper;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock PMSYamlSchemaService pmsYamlSchemaService;
  @Mock PipelineRbacService pipelineRbacServiceImpl;
  @Mock PlanCreatorMergeService planCreatorMergeService;
  @Mock PipelineEnforcementService pipelineEnforcementService;
  @Mock MetricService metricService;
  @Mock PlanCreationQueueRequestHelper planCreationQueueRequestHelper;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock PmsExecutionSummaryRepository pmsExecutionSummaryRespository;
  @Mock PmsFeatureFlagHelper featureFlagService;
  @Mock RollbackModeExecutionHelper rollbackModeExecutionHelper;
  @Mock PlanService planService;
  @Mock NGSettingsClient settingsClient;
  @Mock PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock RetryExecutionHelper retryExecutionHelper;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock private NodeTypeLookupService nodeTypeLookupService;
  @Mock private PMSExecutionService pmsExecutionService;
  @Mock private BlockExecutionMetadataService blockExecutionMetadataService;
  @Mock private RollbackGraphGenerator rollbackGraphGenerator;
  @Mock private PipelineStageHelper pipelineStageHelper;
  @Mock private PipelineStageHelperV1 pipelineStageHelperV1;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PmsExecutionSummaryDtoUpdateHelper pmsExecutionSummaryDtoUpdateHelper;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;

  @Mock AccessControlClient accessControlClient;
  @Mock RuntimeInputValuesValidatorV1 runtimeInputValuesValidatorV1;
  @Mock OpaPolicyEvaluationHelper opaPolicyEvaluationHelper;
  @Mock PipelineYamlConversionEntityService pipelineYamlConversionEntityService;
  @Mock BarrierCycleValidator barrierCycleValidator;

  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pipelineId = "pipelineId";
  String moduleType = "cd";

  String planExecutionId = "planExecutionId";
  String runtimeInputYaml = "pipeline:\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        description: desc\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        description: desc\n";
  String pipelineYaml = "pipeline:\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        description: <+input>\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        description: <+input>\n"
      + "  allowStageExecutions: true\n";
  String pipelineYamlWithExpressions = "pipeline:\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        description: desc\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        description: <+pipeline.stages.s1.description>\n"
      + "  allowStageExecutions: true\n";
  Map<String, String> expressionValues = Collections.singletonMap("<+pipeline.stages.s1.description>", "desc");
  String mergedPipelineYaml = "pipeline:\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        description: desc\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        description: desc\n"
      + "  allowStageExecutions: true\n";

  String mergedPipelineYamlForS2 = "pipeline:\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        description: desc\n"
      + "  allowStageExecutions: true\n";

  String mergedPipelineYamlForS2WithExpression = "pipeline:\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        description: <+pipeline.stages.s1.description>\n"
      + "  allowStageExecutions: true\n";
  String originalExecutionId = "originalExecutionId";
  String generatedExecutionId = "newExecId";
  String pipelineYamlV1;

  PipelineEntity pipelineEntity;
  PipelineEntity pipelineEntityWithExpressions;
  TriggeredBy triggeredBy;
  ExecutionTriggerInfo executionTriggerInfo;
  ExecutionPrincipalInfo executionPrincipalInfo;
  MockedStatic<UUIDGenerator> aStatic;
  PlanExecutionMetadata prevExecutionMetadata;
  PlanExecutionMetadata prevExecutionMetadataWithoutToken;
  MockedStatic<GitAwareContextHelper> gitAwareContextHelperMockedStatic;
  MockedStatic<PlanExecutionUtils> bStatic;

  public ExecutionHelperTest() throws IOException {}

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    pipelineEntity = PipelineEntity.builder()
                         .accountId(accountId)
                         .orgIdentifier(orgId)
                         .projectIdentifier(projectId)
                         .identifier(pipelineId)
                         .yaml(pipelineYaml)
                         .runSequence(394)
                         .build();
    pipelineEntityWithExpressions = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYamlWithExpressions)
                                        .runSequence(394)
                                        .build();
    triggeredBy = TriggeredBy.newBuilder().setUuid("userUuid").setIdentifier("username").build();
    executionTriggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggeredBy(triggeredBy).setTriggerType(MANUAL).setIsRerun(false).build();
    executionPrincipalInfo = ExecutionPrincipalInfo.newBuilder().build();
    doNothing().when(pipelineEnforcementService).validateExecutionEnforcementsBasedOnStage(anyString(), any());
    doNothing().when(metricService).recordMetric(anyString(), anyDouble());
    aStatic = Mockito.mockStatic(UUIDGenerator.class);
    aStatic.when(UUIDGenerator::generateUuid).thenReturn(generatedExecutionId);

    pipelineYamlV1 = readFile("simplified-pipeline.yaml");
    request = Mockito.mock(Call.class);
    prevExecutionMetadata = PlanExecutionMetadata.builder().expressionFunctorToken(1234L).build();
    prevExecutionMetadataWithoutToken = PlanExecutionMetadata.builder().build();
    gitAwareContextHelperMockedStatic = mockStatic(GitAwareContextHelper.class);
    buildExecutionArgsMocks(gitAwareContextHelperMockedStatic);
    bStatic = Mockito.mockStatic(PlanExecutionUtils.class);
    doNothing()
        .when(runtimeInputValuesValidatorV1)
        .validate(any(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
    when(pipelineYamlConversionEntityService.convertV0PipelineYamlToV1(
             anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenAnswer(invocation -> invocation.getArgument(5));
  }

  @After
  public void afterMethod() {
    aStatic.close();
    gitAwareContextHelperMockedStatic.close();
    bStatic.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchPipelineEntity() {
    doReturn(Optional.of(pipelineEntity))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, true);
    PipelineEntity fetchedPipelineEntity =
        executionHelper.fetchPipelineEntity(accountId, orgId, projectId, pipelineId, null);
    assertThat(fetchedPipelineEntity).isEqualTo(fetchedPipelineEntity);

    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, true);
    assertThatThrownBy(() -> executionHelper.fetchPipelineEntity(accountId, orgId, projectId, pipelineId, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline with the given ID: pipelineId does not exist or has been deleted");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildTriggerInfo() {
    doReturn(triggeredBy).when(triggeredByHelper).getFromSecurityContext();

    ExecutionTriggerInfo firstExecutionTriggerInfo = executionHelper.buildTriggerInfo(null);
    assertThat(firstExecutionTriggerInfo.getIsRerun()).isEqualTo(false);
    assertThat(firstExecutionTriggerInfo.getTriggerType()).isEqualTo(MANUAL);
    assertThat(firstExecutionTriggerInfo.getTriggeredBy()).isEqualTo(triggeredBy);
    verify(triggeredByHelper, times(1)).getFromSecurityContext();
    verify(planExecutionService, times(0)).getExecutionMetadataFromPlanExecution(anyString());

    ExecutionMetadata firstExecutionMetadata =
        ExecutionMetadata.newBuilder().setTriggerInfo(firstExecutionTriggerInfo).build();
    doReturn(firstExecutionMetadata)
        .when(planExecutionService)
        .getExecutionMetadataFromPlanExecution(originalExecutionId);

    ExecutionTriggerInfo rerunExecutionTriggerInfo = executionHelper.buildTriggerInfo(originalExecutionId);
    rerunExecutionAssertions(triggeredBy, rerunExecutionTriggerInfo);
    verify(triggeredByHelper, times(2)).getFromSecurityContext();
    verify(planExecutionService, times(1)).getExecutionMetadataFromPlanExecution(originalExecutionId);

    ExecutionMetadata secondExecutionMetadata =
        ExecutionMetadata.newBuilder().setTriggerInfo(rerunExecutionTriggerInfo).build();
    doReturn(secondExecutionMetadata)
        .when(planExecutionService)
        .getExecutionMetadataFromPlanExecution("originalExecutionId2");

    ExecutionTriggerInfo reRerunExecutionTriggerInfo = executionHelper.buildTriggerInfo("originalExecutionId2");
    rerunExecutionAssertions(triggeredBy, reRerunExecutionTriggerInfo);
    verify(triggeredByHelper, times(3)).getFromSecurityContext();
    verify(planExecutionService, times(1)).getExecutionMetadataFromPlanExecution(originalExecutionId);
    verify(planExecutionService, times(1)).getExecutionMetadataFromPlanExecution("originalExecutionId2");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testBuildTriggerInfoForReleaseOrchestration() {
    TriggeredBy releaseOrchestrationTriggeredBy =
        TriggeredBy.newBuilder()
            .setUuid("userUuid")
            .setIdentifier("username")
            .putExtraInfo(TriggeredByHelper.SOURCE_SERVICE, TriggeredByHelper.RMG_SERVICE)
            .build();
    doReturn(releaseOrchestrationTriggeredBy).when(triggeredByHelper).getFromSecurityContext();

    ExecutionTriggerInfo triggerInfo = executionHelper.buildTriggerInfo(null);

    assertThat(triggerInfo.getTriggerType()).isEqualTo(RELEASE_ORCHESTRATION);
    assertThat(triggerInfo.getTriggeredBy()).isEqualTo(releaseOrchestrationTriggeredBy);
  }

  private void rerunExecutionAssertions(TriggeredBy triggeredBy, ExecutionTriggerInfo reRerunExecutionTriggerInfo) {
    assertThat(reRerunExecutionTriggerInfo.getIsRerun()).isEqualTo(true);
    assertThat(reRerunExecutionTriggerInfo.getTriggerType()).isEqualTo(MANUAL);
    assertThat(reRerunExecutionTriggerInfo.getTriggeredBy()).isEqualTo(triggeredBy);
    RerunInfo reRerunInfo = reRerunExecutionTriggerInfo.getRerunInfo();
    assertThat(reRerunInfo.getRootExecutionId()).isEqualTo(originalExecutionId);
    assertThat(reRerunInfo.getRootTriggerType()).isEqualTo(MANUAL);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgs() throws IOException {
    when(opaPolicyEvaluationHelper.shouldEvaluatePolicy(accountId, orgId, projectId, "pipeline", "onstepstart", "0"))
        .thenReturn(true);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0, null);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntity, moduleType, Collections.emptyList(), null,
        executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(), false, false, null,
        YamlUtils.readAsJsonNode(runtimeInputYaml), PlanExecutionMetadataWithContext.builder().build());
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(execArgs.getMetadata().getExecutionMode()).isEqualTo(ExecutionMode.NORMAL);

    PlanExecutionMetadata planExecutionMetadata =
        execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().isStagesExecution())
        .isEqualTo(false);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getProcessedYaml())
        .isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));
    assertThat(planExecutionMetadata.getExpressionFunctorToken()).isNotNull();
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(
            pipelineEntity, mergedPipelineYaml, "branch", OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);

    buildExecutionMetadataVerifications(pipelineEntity);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testBuildExecutionArgs2() throws IOException {
    when(opaPolicyEvaluationHelper.shouldEvaluatePolicy(accountId, orgId, projectId, "pipeline", "onstepstart", "0"))
        .thenReturn(true);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0, null);
    ExecArgs execArgs =
        executionHelper.buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml, Collections.emptyList(), null,
            executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(), false, false,
            PlanExecutionMetadataWithContext.builder().build());
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(execArgs.getMetadata().getExecutionMode()).isEqualTo(ExecutionMode.NORMAL);

    PlanExecutionMetadata planExecutionMetadata =
        execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().isStagesExecution())
        .isEqualTo(false);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getProcessedYaml())
        .isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(
            pipelineEntity, mergedPipelineYaml, "branch", OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);

    buildExecutionMetadataVerifications(pipelineEntity);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsInRetryFlow() throws IOException {
    when(opaPolicyEvaluationHelper.shouldEvaluatePolicy(accountId, orgId, projectId, "pipeline", "onstepstart", "0"))
        .thenReturn(true);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0, null);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntity, moduleType, Collections.emptyList(), null,
        executionTriggerInfo, originalExecutionId, RetryExecutionParameters.builder().isRetry(true).build(), false,
        false, null, YamlUtils.readAsJsonNode(runtimeInputYaml), PlanExecutionMetadataWithContext.builder().build());
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(execArgs.getMetadata().getExecutionMode()).isEqualTo(ExecutionMode.NORMAL);

    PlanExecutionMetadata planExecutionMetadata =
        execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().isStagesExecution())
        .isEqualTo(false);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getProcessedYaml())
        .isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));
    assertThat(planExecutionMetadata.getExpressionFunctorToken()).isEqualTo(1234L);
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(
            pipelineEntity, mergedPipelineYaml, "branch", OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);

    buildExecutionMetadataVerificationsWithRetry(pipelineEntity);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsInRetryFlowWhenPrevMetadataMissingToken() throws IOException {
    when(opaPolicyEvaluationHelper.shouldEvaluatePolicy(accountId, orgId, projectId, "pipeline", "onstepstart", "0"))
        .thenReturn(true);
    // override default mock to have plan without token
    doReturn(Optional.of(prevExecutionMetadataWithoutToken))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(pipelineEntity.getAccountId(), originalExecutionId);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0, null);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntity, moduleType, Collections.emptyList(), null,
        executionTriggerInfo, originalExecutionId, RetryExecutionParameters.builder().isRetry(true).build(), false,
        false, null, YamlUtils.readAsJsonNode(runtimeInputYaml), PlanExecutionMetadataWithContext.builder().build());
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(execArgs.getMetadata().getExecutionMode()).isEqualTo(ExecutionMode.NORMAL);

    PlanExecutionMetadata planExecutionMetadata =
        execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().isStagesExecution())
        .isEqualTo(false);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getProcessedYaml())
        .isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));
    assertThat(planExecutionMetadata.getExpressionFunctorToken()).isNotNull();
    assertThat(planExecutionMetadata.getExpressionFunctorToken()).isNotEqualTo(0L);
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(
            pipelineEntity, mergedPipelineYaml, "branch", OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);

    buildExecutionMetadataVerificationsWithRetry(pipelineEntity);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsForInlinePipeline() throws IOException {
    when(opaPolicyEvaluationHelper.shouldEvaluatePolicy(accountId, orgId, projectId, "pipeline", "onstepstart", "0"))
        .thenReturn(true);
    PipelineEntity inlinePipeline = pipelineEntity.withStoreType(StoreType.INLINE);

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(inlinePipeline.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(inlinePipeline.getAccountId(),
            inlinePipeline.getOrgIdentifier(), inlinePipeline.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0, null);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(inlinePipeline, moduleType, Collections.emptyList(), null,
        executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(), false, false, null,
        YamlUtils.readAsJsonNode(runtimeInputYaml), PlanExecutionMetadataWithContext.builder().build());
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.INLINE);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);

    PlanExecutionMetadata planExecutionMetadata =
        execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().isStagesExecution())
        .isEqualTo(false);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getProcessedYaml())
        .isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(
            inlinePipeline, mergedPipelineYaml, "branch", OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);

    buildExecutionMetadataVerifications(inlinePipeline);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsForRemotePipeline() throws IOException {
    PipelineEntity remotePipeline = pipelineEntity.withStoreType(StoreType.REMOTE).withConnectorRef("conn");

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(remotePipeline.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(remotePipeline.getAccountId(),
            remotePipeline.getOrgIdentifier(), remotePipeline.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0, null);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(remotePipeline, moduleType, Collections.emptyList(), null,
        executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(), false, false, null,
        YamlUtils.readAsJsonNode(runtimeInputYaml), PlanExecutionMetadataWithContext.builder().build());
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.REMOTE);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEqualTo("conn");
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);

    PlanExecutionMetadata planExecutionMetadata =
        execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().isStagesExecution())
        .isEqualTo(false);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getProcessedYaml())
        .isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(
            remotePipeline, mergedPipelineYaml, "branch", OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);

    buildExecutionMetadataVerifications(remotePipeline);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsWithTemplateRef() throws IOException {
    when(opaPolicyEvaluationHelper.shouldEvaluatePolicy(accountId, orgId, projectId, "pipeline", "onstepstart", "0"))
        .thenReturn(true);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYaml).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0, null);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntity, moduleType, Collections.emptyList(), null,
        executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(), false, false, null,
        YamlUtils.readAsJsonNode(runtimeInputYaml), PlanExecutionMetadataWithContext.builder().build());
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);

    PlanExecutionMetadata planExecutionMetadata =
        execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYaml);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().isStagesExecution())
        .isEqualTo(false);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getProcessedYaml())
        .isEqualTo(YamlUtils.injectUuid(mergedPipelineYaml));

    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal(pipelineEntity, null, null, null);
    verify(pmsYamlSchemaService, times(0))
        .validateYamlSchema(accountId, orgId, projectId, YamlUtils.readAsJsonNode(pipelineYaml), "0");
    verify(pmsYamlSchemaService, times(1))
        .validateYamlSchema(accountId, orgId, projectId, YamlUtils.readAsJsonNode(mergedPipelineYaml), "0");
    verify(pipelineRbacServiceImpl, times(1))
        .extractAndValidateStaticallyReferredEntities(
            accountId, orgId, projectId, pipelineId, YamlUtils.readAsJsonNode(mergedPipelineYaml), null, true, "0");
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, pipelineYaml);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString(), anyString());
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(
            pipelineEntity, mergedPipelineYaml, "branch", OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsForRunStage() throws IOException {
    when(opaPolicyEvaluationHelper.shouldEvaluatePolicy(accountId, orgId, projectId, "pipeline", "onstepstart", "0"))
        .thenReturn(true);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(mergedPipelineYamlForS2).build();
    String mergedYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.getYaml(), runtimeInputYaml, true);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), mergedYaml, true, false,
            BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0, null);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntity, moduleType, Collections.singletonList("s2"),
        null, executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(), false, false, null,
        YamlUtils.readAsJsonNode(runtimeInputYaml), PlanExecutionMetadataWithContext.builder().build());
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);

    PlanExecutionMetadata planExecutionMetadata =
        execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(runtimeInputYaml);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYamlForS2);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().isStagesExecution())
        .isEqualTo(true);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().getFullPipelineYaml())
        .isEqualTo(mergedPipelineYaml);
    assertThat(execArgs.getPlanExecutionMetadataWithContext()
                   .getStagesExecutionMetadata()
                   .getStageIdentifierToNameMap()
                   .size())
        .isEqualTo(1);
    assertThat(execArgs.getPlanExecutionMetadataWithContext()
                   .getStagesExecutionMetadata()
                   .getStageIdentifierToNameMap()
                   .keySet())
        .isEqualTo(Set.of("s2"));
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().getStageIdentifiers())
        .isEqualTo(Collections.singletonList("s2"));
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().getExpressionValues())
        .isNull();
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getProcessedYaml())
        .isEqualTo(YamlUtils.injectUuid(mergedPipelineYamlForS2));
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(
            pipelineEntity, mergedPipelineYamlForS2, "branch", OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);

    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1))
        .getGitSyncBranchContextBytesThreadLocal(pipelineEntity, pipelineEntity.getStoreType(), null, null);
    verify(pmsYamlSchemaService, times(1))
        .validateYamlSchema(accountId, orgId, projectId, YamlUtils.readAsJsonNode(mergedPipelineYaml), "0");
    if (pipelineEntity.getStoreType() != StoreType.REMOTE) {
      verify(pipelineRbacServiceImpl, times(1))
          .extractAndValidateStaticallyReferredEntities(
              accountId, orgId, projectId, pipelineId, YamlUtils.readAsJsonNode(mergedPipelineYaml), null, true, "0");
    }
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, pipelineYaml);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString(), anyString());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsForRunStageWithExpressions() throws IOException {
    when(opaPolicyEvaluationHelper.shouldEvaluatePolicy(accountId, orgId, projectId, "pipeline", "onstepstart", "0"))
        .thenReturn(true);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder()
            .mergedPipelineYaml(pipelineYamlWithExpressions)
            .mergedPipelineYamlWithTemplateRef(pipelineYamlWithExpressions)
            .build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntityWithExpressions.getAccountId(),
            pipelineEntityWithExpressions.getOrgIdentifier(), pipelineEntityWithExpressions.getProjectIdentifier(),
            pipelineYamlWithExpressions, true, true, BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0, null);
    ExecArgs execArgs =
        executionHelper.buildExecutionArgs(pipelineEntityWithExpressions, moduleType, Collections.singletonList("s2"),
            expressionValues, executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(),
            false, false, null, null, PlanExecutionMetadataWithContext.builder().build());
    executionMetadataAssertions(execArgs.getMetadata());
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);

    PlanExecutionMetadata planExecutionMetadata =
        execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isEqualTo(null);
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(mergedPipelineYamlForS2WithExpression);
    assertThat(((Map) ((Map) ((Map) execArgs.getPlanExecutionMetadataWithContext().getStageExpressionValuesMap().get(
                                  "pipeline"))
                           .get("stages"))
                       .get("s1"))
                   .get("description"))
        .isEqualTo("desc");
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().isStagesExecution())
        .isEqualTo(true);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().getFullPipelineYaml())
        .isEqualTo(pipelineYamlWithExpressions);
    assertThat(execArgs.getPlanExecutionMetadataWithContext()
                   .getStagesExecutionMetadata()
                   .getStageIdentifierToNameMap()
                   .keySet())
        .isEqualTo(Set.of("s2"));
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().getStageIdentifiers())
        .isEqualTo(Collections.singletonList("s2"));
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().getExpressionValues())
        .isEqualTo(expressionValues);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getProcessedYaml())
        .isEqualTo(YamlUtils.injectUuid(mergedPipelineYamlForS2WithExpression));

    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1))
        .getGitSyncBranchContextBytesThreadLocal(pipelineEntityWithExpressions, null, null, null);
    verify(pmsYamlSchemaService, times(1))
        .validateYamlSchema(accountId, orgId, projectId, YamlUtils.readAsJsonNode(pipelineYamlWithExpressions), "0");
    verify(pipelineRbacServiceImpl, times(1))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId,
            YamlUtils.readAsJsonNode(pipelineYamlWithExpressions), null, true, "0");
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString(), anyString());
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(pipelineEntityWithExpressions, mergedPipelineYamlForS2WithExpression,
            "branch", OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsWithError() {
    // this will throw an NPE in the try block, and the second catch block should be invoked
    assertThatThrownBy(()
                           -> executionHelper.buildExecutionArgs(pipelineEntity, null, null, null, null, null, null,
                               false, false, null, null, PlanExecutionMetadataWithContext.builder().build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to start execution for Pipeline:");
  }

  private void buildExecutionArgsMocks(MockedStatic<GitAwareContextHelper> gitAwareContextHelperMockedStatic)
      throws IOException {
    gitAwareContextHelperMockedStatic.when(GitAwareContextHelper::getBranchInRequestOrFromSCMGitMetadataV2)
        .thenReturn("branch");
    doReturn(executionPrincipalInfo).when(principalInfoHelper).getPrincipalInfoFromSecurityContext();
    doReturn(394).when(pipelineMetadataService).incrementRunSequence(any(), any(), any(), any(), any(), anyBoolean());
    doReturn(null).when(pmsGitSyncHelper).getGitSyncBranchContextBytesThreadLocal(pipelineEntity, null, null, null);
    doReturn(true)
        .when(pmsYamlSchemaService)
        .validateYamlSchema(accountId, orgId, projectId, YamlUtils.readAsJsonNode(mergedPipelineYaml), "0");
    doNothing()
        .when(pipelineRbacServiceImpl)
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, mergedPipelineYaml);
    doReturn(Optional.of(prevExecutionMetadata))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(accountId, originalExecutionId);
    when(pmsExecutionSummaryService.fetchRootRetryExecutionId(accountId, originalExecutionId))
        .thenReturn(originalExecutionId);
    String processedYamlForRetry = YamlUtils.injectUuid(mergedPipelineYaml);
    when(retryExecutionHelper.retryProcessedYaml(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(processedYamlForRetry);
  }

  private void executionMetadataAssertions(ExecutionMetadata metadata) {
    assertThat(metadata.getExecutionUuid()).isEqualTo(generatedExecutionId);
    assertThat(metadata.getTriggerInfo()).isEqualTo(executionTriggerInfo);
    assertThat(metadata.getModuleType()).isEqualTo(moduleType);
    assertThat(metadata.getRunSequence()).isEqualTo(0);
    assertThat(metadata.getPipelineIdentifier()).isEqualTo(pipelineId);
    assertThat(metadata.getPrincipalInfo()).isEqualTo(executionPrincipalInfo);
    assertThat(metadata.getGitSyncBranchContext().size()).isEqualTo(0);
  }

  private void buildExecutionMetadataVerificationsInternal(PipelineEntity pipelineEntity) {
    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1))
        .getGitSyncBranchContextBytesThreadLocal(
            pipelineEntity, pipelineEntity.getStoreType(), null, pipelineEntity.getConnectorRef());
    verify(pmsYamlSchemaService, times(0))
        .validateYamlSchema(accountId, orgId, projectId, YamlUtils.readAsJsonNode(pipelineYaml), "0");
    verify(pmsYamlSchemaService, times(1))
        .validateYamlSchema(accountId, orgId, projectId, YamlUtils.readAsJsonNode(mergedPipelineYaml), "0");
    if (pipelineEntity.getStoreType() != StoreType.REMOTE) {
      verify(pipelineRbacServiceImpl, times(1))
          .extractAndValidateStaticallyReferredEntities(
              accountId, orgId, projectId, pipelineId, YamlUtils.readAsJsonNode(mergedPipelineYaml), null, true, "0");
    }
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, pipelineYaml);
  }

  private void buildExecutionMetadataVerifications(PipelineEntity pipelineEntity) {
    buildExecutionMetadataVerificationsInternal(pipelineEntity);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString(), anyString());
  }

  private void buildExecutionMetadataVerificationsWithRetry(PipelineEntity pipelineEntity) {
    buildExecutionMetadataVerificationsInternal(pipelineEntity);
    verify(planExecutionMetadataService, times(1))
        .findByPlanExecutionId(pipelineEntity.getAccountId(), originalExecutionId);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineYamlAndValidateForRbacCheck() throws IOException {
    String pipelineYaml = "pipeline:\n"
        + "  template:\n"
        + "    templateInputs:\n"
        + "      serviceRef: <+input>\n";
    String mergedRuntimeInputYaml = "pipeline:\n"
        + "  template:\n"
        + "    templateInputs:\n"
        + "      serviceRef: svc_v2\n";
    String resolvedYaml = "pipeline:\n"
        + "  stage:\n"
        + "    serviceConfig:\n"
        + "      serviceRef: svc_v2\n";
    doReturn(TemplateMergeResponseDTO.builder().mergedPipelineYaml(resolvedYaml).build())
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidators(
            any(), any(), any(), any(), anyBoolean(), anyBoolean(), any(), anyString(), any());
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    doReturn(request)
        .when(settingsClient)
        .getSetting(eq(RUN_RBAC_VALIDATION_BEFORE_EXECUTING_INLINE_PIPELINES), eq(pipelineEntity.getAccountId()),
            eq(null), eq(null));
    SettingValueResponseDTO settingValueResponseDTOForFalseValue =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTOForFalseValue))).when(request).execute();
    executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities(
        YamlUtils.readAsJsonNode(mergedRuntimeInputYaml), pipelineEntity, false, null, false);
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(
            accountId, orgId, projectId, pipelineId, YamlUtils.readAsJsonNode(mergedRuntimeInputYaml), null, true, "0");
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, resolvedYaml);

    SettingValueResponseDTO settingValueResponseDTOForTrueValue =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTOForTrueValue))).when(request).execute();
    executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities(
        YamlUtils.readAsJsonNode(mergedRuntimeInputYaml), pipelineEntity, false, null, false);
    verify(pipelineRbacServiceImpl, times(1))
        .extractAndValidateStaticallyReferredEntities(
            accountId, orgId, projectId, pipelineId, YamlUtils.readAsJsonNode(mergedRuntimeInputYaml), null, true, "0");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineYamlAndValidateForPipelineWithAllowedValues() throws IOException {
    String pipelineYamlWithAllowedValues = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        description: <+input>.allowedValues(a, b)\n";
    String runtimeInputYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        description: a\n";
    String mergedYamlWithValidators = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        description: a\n";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .yaml(pipelineYamlWithAllowedValues)
                                        .build();
    TemplateMergeResponseDTO response = executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities(
        YamlUtils.readAsJsonNode(runtimeInputYaml), pipelineEntity, false, null, false);
    assertThat(response.getMergedPipelineYaml()).isEqualTo(mergedYamlWithValidators);
    assertThat(response.getMergedPipelineYamlWithTemplateRef()).isEqualTo(mergedYamlWithValidators);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineYamlAndValidateForInlineAndRemotePipelines() throws IOException {
    PipelineEntity inline = PipelineEntity.builder()
                                .accountId(accountId)
                                .orgIdentifier(orgId)
                                .projectIdentifier(projectId)
                                .identifier(pipelineId)
                                .yaml(mergedPipelineYaml)
                                .runSequence(394)
                                .storeType(StoreType.INLINE)
                                .build();
    executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities(null, inline, false, null, false);

    doThrow(
        new InvalidRequestException(
            "pipelineRbacServiceImpl.extractAndValidateStaticallyReferredEntities(...) was not supposed to be called"))
        .when(pipelineRbacServiceImpl)
        .extractAndValidateStaticallyReferredEntities(anyString(), anyString(), anyString(), anyString(), anyString());
    PipelineEntity remote = PipelineEntity.builder()
                                .accountId(accountId)
                                .orgIdentifier(orgId)
                                .projectIdentifier(projectId)
                                .identifier(pipelineId)
                                .yaml(mergedPipelineYaml)
                                .runSequence(394)
                                .storeType(StoreType.REMOTE)
                                .build();
    executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities(null, remote, false, null, false);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  @Ignore("Will remove this ignore annotation and modify this test when service env changes are done")
  public void testGetPipelineYamlAndValidateParallelAndIndependentStages() {
    String pipelineYaml = readFile("pipelineTest.yaml");
    String inputSetYaml = readFile("inputSetTest.yaml");
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .yaml(pipelineYaml)
                                        .build();
    assertThatThrownBy(()
                           -> executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities(
                               YamlUtils.readAsJsonNode(inputSetYaml), pipelineEntity, false, null, false))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetPipelineYamlAndValidateWhenOPAFFisOff() throws IOException {
    String yamlWithTempRef = "pipeline:\n"
        + "  name: ww\n"
        + "  template:\n"
        + "    templateRef: new_pipeline_template_name\n"
        + "    versionLabel: v1\n"
        + "  tags: {}\n";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(yamlWithTempRef)
                                        .build();
    when(featureFlagService.isEnabled(pipelineEntity.getAccountId(), FeatureName.OPA_PIPELINE_GOVERNANCE))
        .thenReturn(false);
    TemplateMergeResponseDTO templateMergeResponse =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yamlWithTempRef).build();

    doReturn(templateMergeResponse)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipelineAndAppendInputSetValidatorsForExecution(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), yamlWithTempRef, true, false,
            BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0, null);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        executionHelper.getPipelineYamlAndValidateStaticallyReferredEntities(null, pipelineEntity, false, null, false);
    assertThat(templateMergeResponseDTO.getMergedPipelineYaml())
        .isEqualTo(templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testStartExecution() {
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build();
    PlanExecution planExecution = PlanExecution.builder().build();
    doReturn(planExecution)
        .when(planCreationQueueRequestHelper)
        .executePlanCreationRequest(PlanCreationRequest.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .executionMetadata(executionMetadata)
                                        .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                        .scopeInfo(null)
                                        .isParentIdQueryingEnabled(true)
                                        .isDebug(false)
                                        .runSequenceIncrementNeeded(true)
                                        .branchSequenceIncrementNeeded(true)
                                        .build());
    PlanExecution createdPlanExecution = executionHelper.startExecution(
        accountId, orgId, projectId, executionMetadata, planExecutionMetadataWithContext, false, null);
    assertThat(createdPlanExecution).isEqualTo(planExecution);
    verify(planCreationQueueRequestHelper, times(1))
        .executePlanCreationRequest(PlanCreationRequest.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .executionMetadata(executionMetadata)
                                        .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                        .scopeInfo(null)
                                        .isParentIdQueryingEnabled(true)
                                        .isDebug(false)
                                        .runSequenceIncrementNeeded(true)
                                        .branchSequenceIncrementNeeded(true)
                                        .build());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testBuildRetryInfo() {
    // isRetry: false
    RetryExecutionInfo retryExecutionInfo = executionHelper.buildRetryInfo(false, accountId, null);
    assertThat(retryExecutionInfo.getIsRetry()).isEqualTo(false);

    // isRetry: true and originalId: null
    retryExecutionInfo = executionHelper.buildRetryInfo(true, accountId, null);
    assertThat(retryExecutionInfo.getIsRetry()).isEqualTo(false);

    // isRetry: true and originalId: empty
    retryExecutionInfo = executionHelper.buildRetryInfo(true, accountId, "");
    assertThat(retryExecutionInfo.getIsRetry()).isEqualTo(false);

    // isRetry: true
    when(pmsExecutionSummaryService.fetchRootRetryExecutionId(accountId, "originalId")).thenReturn("rootParentId");
    retryExecutionInfo = executionHelper.buildRetryInfo(true, accountId, "originalId");
    assertThat(retryExecutionInfo.getIsRetry()).isEqualTo(true);
    assertThat(retryExecutionInfo.getParentRetryId()).isEqualTo("originalId");
    assertThat(retryExecutionInfo.getRootExecutionId()).isEqualTo("rootParentId");
  }

  private void buildExecutionMetadataVerificationsWithV1Version(PipelineEntity pipelineEntity) {
    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1))
        .getGitSyncBranchContextBytesThreadLocal(pipelineEntity, pipelineEntity.getStoreType(), null, null);
    verify(pmsYamlSchemaService, times(0))
        .validateYamlSchema(accountId, orgId, projectId, YamlUtils.readAsJsonNode(pipelineYamlV1), "0");
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, pipelineYamlV1);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString(), anyString());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsV1Yaml() {
    when(opaPolicyEvaluationHelper.shouldEvaluatePolicy(accountId, orgId, projectId, "pipeline", "onstepstart", "0"))
        .thenReturn(true);
    gitAwareContextHelperMockedStatic.when(GitAwareContextHelper::getBranchInRequestOrFromSCMGitMetadataV2)
        .thenReturn("branch");
    doReturn(executionPrincipalInfo).when(principalInfoHelper).getPrincipalInfoFromSecurityContext();
    pipelineEntity.setYaml(pipelineYamlV1);
    pipelineEntity.setHarnessVersion(HarnessYamlVersion.V1);
    doReturn(pipelineYamlV1).when(pmsPipelineServiceHelper).preProcessPipelineYaml(pipelineYamlV1, false);
    doReturn(pipelineYamlV1).when(pmsPipelineServiceHelper).preProcessPipelineYaml(pipelineYamlV1, true);
    doReturn(pipelineYamlV1).when(pmsPipelineServiceHelper).injectTypeField(pipelineYamlV1);
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntity, moduleType, Collections.emptyList(), null,
        executionTriggerInfo, null, RetryExecutionParameters.builder().isRetry(false).build(), false, false, null, null,
        PlanExecutionMetadataWithContext.builder().build());
    assertThat(execArgs.getMetadata().getExecutionUuid()).isEqualTo(generatedExecutionId);
    assertThat(execArgs.getMetadata().getTriggerInfo()).isEqualTo(executionTriggerInfo);
    assertThat(execArgs.getMetadata().getModuleType()).isEqualTo(moduleType);
    assertThat(execArgs.getMetadata().getPipelineIdentifier()).isEqualTo(pipelineId);
    assertThat(execArgs.getMetadata().getPrincipalInfo()).isEqualTo(executionPrincipalInfo);
    assertThat(execArgs.getMetadata().getGitSyncBranchContext().size()).isEqualTo(0);
    assertThat(execArgs.getMetadata().getPipelineStoreType()).isEqualTo(PipelineStoreType.UNDEFINED);
    assertThat(execArgs.getMetadata().getPipelineConnectorRef()).isEmpty();
    assertThat(execArgs.getMetadata().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);

    PlanExecutionMetadata planExecutionMetadata =
        execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
    assertThat(planExecutionMetadata.getPlanExecutionId()).isEqualTo(generatedExecutionId);
    assertThat(planExecutionMetadata.getInputSetYaml()).isNull();
    assertThat(planExecutionMetadata.getYaml()).isEqualTo(pipelineYamlV1);
    assertThat(execArgs.getPlanExecutionMetadataWithContext().getStagesExecutionMetadata().isStagesExecution())
        .isFalse();
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(
            pipelineEntity, pipelineYamlV1, "branch", OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);
    verify(principalInfoHelper, times(1)).getPrincipalInfoFromSecurityContext();
    verify(pmsGitSyncHelper, times(1))
        .getGitSyncBranchContextBytesThreadLocal(pipelineEntity, pipelineEntity.getStoreType(), null, null);
    verify(pmsYamlSchemaService, times(0))
        .validateYamlSchema(accountId, orgId, projectId, YamlUtils.readAsJsonNode(pipelineYamlV1), "0");
    verify(pipelineRbacServiceImpl, times(0))
        .extractAndValidateStaticallyReferredEntities(accountId, orgId, projectId, pipelineId, pipelineYamlV1);
    verify(planExecutionMetadataService, times(0)).findByPlanExecutionId(anyString(), anyString());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testProcessStageExecutionInfo() throws IOException {
    ProcessStageExecutionInfoResult processStageExecutionInfoResult =
        executionHelper.processStageExecutionInfo(Collections.singletonList("s2"), true, pipelineEntity,
            mergedPipelineYamlForS2, mergedPipelineYamlForS2, null, false);
    assertThat(processStageExecutionInfoResult.getStagesExecutionInfo().isStagesExecution()).isEqualTo(true);
    assertThat(processStageExecutionInfoResult.getStagesExecutionInfo().getFullPipelineYaml())
        .isEqualTo(mergedPipelineYamlForS2);
    assertThat(processStageExecutionInfoResult.getStagesExecutionInfo().getStageIdentifierToNameMap().keySet())
        .isEqualTo(Set.of("s2"));
    assertThat(processStageExecutionInfoResult.getStagesExecutionInfo().getStageIdentifiers())
        .isEqualTo(Collections.singletonList("s2"));
    assertThat(processStageExecutionInfoResult.getStagesExecutionInfo().getExpressionValues()).isNull();
    assertThat(processStageExecutionInfoResult.getFilteredPipelineYamlWithTemplateRef())
        .isEqualTo(mergedPipelineYamlForS2);
  }
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateSettingsInExecutionMetadataBuilder() {
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenReturn(Arrays.asList(
            SettingResponseDTO.builder()
                .setting(SettingDTO.builder()
                             .identifier(NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName())
                             .name("setting1")
                             .value("true")
                             .build())
                .build(),
            SettingResponseDTO.builder()
                .setting(SettingDTO.builder()
                             .identifier(NGPipelineSettingsConstant.DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTANER.getName())
                             .name("setting2")
                             .value("true")
                             .build())
                .build()));

    ExecutionMetadata.Builder builder = ExecutionMetadata.newBuilder();
    executionHelper.updateSettingsInExecutionMetadataBuilder(
        PipelineEntity.builder().accountId(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build(),
        builder, null, false);
    assertThat(builder.build().getSettingToValueMapCount()).isEqualTo(2);
    assertThat(builder.build().getSettingToValueMapOrThrow(
                   NGPipelineSettingsConstant.DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTANER.getName()))
        .isEqualTo("true");
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testShouldRunRbacValidationBeforeExecutingInlinePipelines() throws IOException {
    doReturn(request)
        .when(settingsClient)
        .getSetting(eq(RUN_RBAC_VALIDATION_BEFORE_EXECUTING_INLINE_PIPELINES), eq(pipelineEntity.getAccountId()),
            eq(null), eq(null));

    SettingValueResponseDTO settingValueResponseDTOForFalseValue =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTOForFalseValue))).when(request).execute();
    assertThat(executionHelper.shouldRunRbacValidationBeforeExecutingInlinePipelines(pipelineEntity)).isFalse();

    SettingValueResponseDTO settingValueResponseDTOForTrueValue =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTOForTrueValue))).when(request).execute();
    assertThat(executionHelper.shouldRunRbacValidationBeforeExecutingInlinePipelines(pipelineEntity)).isTrue();

    doThrow(new IOException("Could not find run rbac validation before executing inline pipelines setting"))
        .when(request)
        .execute();
    assertThat(executionHelper.shouldRunRbacValidationBeforeExecutingInlinePipelines(pipelineEntity)).isTrue();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testCheckExecutionRunningOrThrowForGivenPlanExecutionIdForCompletedExecution() {
    doReturn(Status.SUCCEEDED).when(planExecutionService).getStatus(planExecutionId);
    assertThat(executionHelper.shouldDisableNotesUpdate(planExecutionId, accountId)).isFalse();

    when(featureFlagService.isEnabled(
             pipelineEntity.getAccountId(), FeatureName.PIE_DISABLE_NOTES_UPDATE_AFTER_EXECUTION_COMPLETED))
        .thenReturn(true);
    assertThat(executionHelper.shouldDisableNotesUpdate(planExecutionId, accountId)).isTrue();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testCheckExecutionRunningOrThrowForGivenPlanExecutionId() {
    doReturn(Status.RUNNING).when(planExecutionService).getStatus(planExecutionId);
    assertThat(executionHelper.shouldDisableNotesUpdate(planExecutionId, accountId)).isFalse();

    when(featureFlagService.isEnabled(
             pipelineEntity.getAccountId(), FeatureName.PIE_DISABLE_NOTES_UPDATE_AFTER_EXECUTION_COMPLETED))
        .thenReturn(true);
    assertThat(executionHelper.shouldDisableNotesUpdate(planExecutionId, accountId)).isFalse();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCheckIfAsyncPlanCreationForManualTrigger() {
    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder()
            .setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggerType(MANUAL).build())
            .build();

    when(featureFlagService.isEnabled(accountId, PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION)).thenReturn(true);
    when(featureFlagService.isEnabled(accountId, PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS))
        .thenReturn(true);
    assertThat(executionHelper.checkIfAsyncPlanCreation(accountId, executionMetadata.getTriggerInfo(), true)).isTrue();
    when(featureFlagService.isEnabled(accountId, PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION)).thenReturn(false);
    assertThat(executionHelper.checkIfAsyncPlanCreation(accountId, executionMetadata.getTriggerInfo(), true)).isFalse();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testCheckIfAsyncPlanCreationForWebhookTrigger() {
    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder()
            .setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggerType(WEBHOOK).build())
            .build();

    when(featureFlagService.isEnabled(accountId, PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS))
        .thenReturn(true);
    when(featureFlagService.isEnabled(accountId, PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION)).thenReturn(true);
    assertThat(executionHelper.checkIfAsyncPlanCreation(accountId, executionMetadata.getTriggerInfo(), true)).isTrue();
    when(featureFlagService.isEnabled(accountId, PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS))
        .thenReturn(false);
    assertThat(executionHelper.checkIfAsyncPlanCreation(accountId, executionMetadata.getTriggerInfo(), true)).isFalse();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testRemoveTemplateMetadataFromResolvedYamlWithMultipleOccurrences() {
    // Test that both 'template' and 'parent-template-type' fields are removed from V1 pipeline YAML
    String pipelineYamlWithTemplateMetadata = readFile("v1-pipeline-with-template-metadata.yaml");

    // Verify the input has template and parent-template-type fields
    assertThat(pipelineYamlWithTemplateMetadata).contains("template-metadata:");
    assertThat(pipelineYamlWithTemplateMetadata).contains("parent-template-type: template");
    assertThat(pipelineYamlWithTemplateMetadata).contains("uses: rishi_stage_temp");
    assertThat(pipelineYamlWithTemplateMetadata).contains("icon-name: rishi-custom-icon");
    assertThat(pipelineYamlWithTemplateMetadata).contains("description: simple template for testing");
    // Verify name field inside template blocks (v1 template metadata)
    assertThat(pipelineYamlWithTemplateMetadata).contains("name: stage_1");
    assertThat(pipelineYamlWithTemplateMetadata).contains("name: injected_template_1");
    assertThat(pipelineYamlWithTemplateMetadata).contains("name: injected_template_2");

    // Use reflection to test the private method
    String updatedYaml = invokeRemoveTemplateMetadataFromResolvedYaml(pipelineYamlWithTemplateMetadata);

    // Verify template fields are removed (entire template block including uses, storeType, icon-name, description,
    // name)
    assertThat(updatedYaml).doesNotContain("uses:");
    assertThat(updatedYaml).doesNotContain("storeType:");
    assertThat(updatedYaml).doesNotContain("icon-name:");
    assertThat(updatedYaml).doesNotContain("description: simple template for testing");

    // Verify parent-template-type fields are removed (there were 2 in the input)
    assertThat(updatedYaml).doesNotContain("parent-template-type");

    // Verify other fields are preserved (stage-level fields, not inside template)
    assertThat(updatedYaml).contains("id: stage_1");
    assertThat(updatedYaml).contains("id: stage_2");
    assertThat(updatedYaml).contains("id: run_1");
    assertThat(updatedYaml).contains("script: echo Rishikesh");
    assertThat(updatedYaml).contains("script: echo HI");
    assertThat(updatedYaml).contains("runtime: shell");
    // Stage-level name fields should be preserved
    assertThat(updatedYaml).contains("name: stage_1");
    assertThat(updatedYaml).contains("name: stage_2");
    assertThat(updatedYaml).contains("name: run_1");
  }

  private String invokeRemoveTemplateMetadataFromResolvedYaml(String pipelineYaml) {
    try {
      java.lang.reflect.Method method =
          ExecutionHelper.class.getDeclaredMethod("removeTemplateMetadataFromResolvedYaml", String.class);
      method.setAccessible(true);
      return (String) method.invoke(null, pipelineYaml);
    } catch (Exception e) {
      throw new RuntimeException("Failed to invoke removeTemplateMetadataFromResolvedYaml", e);
    }
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testBuildExecutionArgsWithYamlSizeLimitExceeded() throws Exception {
    // Create a YAML string that exceeds 3MB (3145728 code points)
    // Each stage block is approximately 1000 characters, so we need about 3500 stages to exceed 3MB
    StringBuilder largeYamlBuilder = new StringBuilder();
    largeYamlBuilder.append("pipeline:\n");
    largeYamlBuilder.append("  name: Large Pipeline\n");
    largeYamlBuilder.append("  identifier: large_pipeline\n");
    largeYamlBuilder.append("  projectIdentifier: ").append(projectId).append("\n");
    largeYamlBuilder.append("  orgIdentifier: ").append(orgId).append("\n");
    largeYamlBuilder.append("  stages:\n");

    // Create stages to make the YAML exceed 3MB
    for (int i = 0; i < 4000; i++) {
      largeYamlBuilder.append("    - stage:\n");
      largeYamlBuilder.append("        name: Stage_").append(i).append("\n");
      largeYamlBuilder.append("        identifier: stage_").append(i).append("\n");
      largeYamlBuilder.append("        type: Custom\n");
      largeYamlBuilder.append("        spec:\n");
      largeYamlBuilder.append("          execution:\n");
      largeYamlBuilder.append("            steps:\n");
      for (int j = 0; j < 10; j++) {
        largeYamlBuilder.append("              - step:\n");
        largeYamlBuilder.append("                  type: ShellScript\n");
        largeYamlBuilder.append("                  name: Step_").append(j).append("\n");
        largeYamlBuilder.append("                  identifier: step_").append(i).append("_").append(j).append("\n");
        largeYamlBuilder.append("                  spec:\n");
        largeYamlBuilder.append("                    shell: Bash\n");
        largeYamlBuilder.append("                    source:\n");
        largeYamlBuilder.append("                      type: Inline\n");
        largeYamlBuilder.append("                      spec:\n");
        largeYamlBuilder.append("                        script: echo 'Hello World from stage ")
            .append(i)
            .append(" step ")
            .append(j)
            .append("'\n");
      }
    }

    String largeYaml = largeYamlBuilder.toString();

    // Verify the YAML is actually larger than 3MB
    assertThat(largeYaml.length()).isGreaterThan(3145728);

    PipelineEntity largePipelineEntity = PipelineEntity.builder()
                                             .accountId(accountId)
                                             .orgIdentifier(orgId)
                                             .projectIdentifier(projectId)
                                             .identifier(pipelineId)
                                             .name("Large Pipeline")
                                             .yaml(largeYaml)
                                             .harnessVersion(HarnessYamlVersion.V0)
                                             .storeType(StoreType.INLINE)
                                             .build();

    when(featureFlagService.isEnabled(accountId, FeatureName.PIE_PROCESS_ADDITIONAL_BASE_KEYS)).thenReturn(false);

    // Attempt to build execution args with the large YAML
    // This should throw an InvalidRequestException when YamlUtils.read is called for BasicPipeline
    assertThatThrownBy(
        ()
            -> executionHelper.buildExecutionArgs(largePipelineEntity, moduleType, Collections.emptyList(), null,
                executionTriggerInfo, null, null, false, false, null, null, null, false, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("The YAML document exceeds the maximum allowed size limit");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetResponseDTO_WithChildGraph() {
    String stageNodeId = "stageNodeId";
    String stageNodeExecutionId = "stageNodeExecutionId";
    String childStageNodeId = "childStageNodeId";

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projectId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .planExecutionId(planExecutionId)
                                                                .layoutNodeMap(new HashMap<>())
                                                                .build();

    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(null)
        .when(rollbackGraphGenerator)
        .checkAndBuildRollbackGraph(accountId, orgId, projectId, executionSummaryEntity, null, childStageNodeId,
            stageNodeExecutionId, stageNodeId);
    doReturn(false).when(retryExecutionHelper).shouldShowRetryHistory(executionSummaryEntity);
    doReturn(true).when(retryExecutionHelper).isLatestExecution(executionSummaryEntity);
    doReturn(true)
        .when(pipelineStageHelper)
        .validateChildGraphToGenerate(any(), eq(stageNodeId), eq(stageNodeExecutionId));
    doReturn(false).when(pipelineStageHelperV1).validateChildGraphToGenerate(any(), eq(stageNodeId));
    doNothing().when(pmsExecutionService).sendGraphUpdateEvent(executionSummaryEntity);
    doReturn(null).when(pmsExecutionSummaryDtoUpdateHelper).getQueuedReason(executionSummaryEntity);
    doReturn(null).when(nodeExecutionService).getByPlanNodeUuid(stageNodeId, planExecutionId);

    PipelineExecutionDetailDTO result = executionHelper.getResponseDTO(
        stageNodeId, stageNodeExecutionId, childStageNodeId, false, executionSummaryEntity, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getPipelineExecutionSummary()).isNotNull();
    verify(pmsExecutionService, times(1)).sendGraphUpdateEvent(executionSummaryEntity);
    verify(pipelineStageHelper, times(1))
        .validateChildGraphToGenerate(any(), eq(stageNodeId), eq(stageNodeExecutionId));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetResponseDTO_WithChildGraphV1() {
    String stageNodeId = "stageNodeId";
    String stageNodeExecutionId = "stageNodeExecutionId";
    String childStageNodeId = "childStageNodeId";

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projectId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .planExecutionId(planExecutionId)
                                                                .layoutNodeMap(new HashMap<>())
                                                                .build();

    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(null)
        .when(rollbackGraphGenerator)
        .checkAndBuildRollbackGraph(accountId, orgId, projectId, executionSummaryEntity, null, childStageNodeId,
            stageNodeExecutionId, stageNodeId);
    doReturn(false).when(retryExecutionHelper).shouldShowRetryHistory(executionSummaryEntity);
    doReturn(true).when(retryExecutionHelper).isLatestExecution(executionSummaryEntity);
    // First condition returns false, second condition (V1) returns true
    doReturn(false).when(pipelineStageHelper).validateChildGraphToGenerate(any(), eq(stageNodeId), eq(null));
    doReturn(true).when(pipelineStageHelperV1).validateChildGraphToGenerate(any(), eq(stageNodeId));
    doNothing().when(pmsExecutionService).sendGraphUpdateEvent(executionSummaryEntity);
    doReturn(null).when(pmsExecutionSummaryDtoUpdateHelper).getQueuedReason(executionSummaryEntity);
    doReturn(null).when(nodeExecutionService).getByPlanNodeUuid(stageNodeId, planExecutionId);

    PipelineExecutionDetailDTO result = executionHelper.getResponseDTO(
        stageNodeId, stageNodeExecutionId, childStageNodeId, false, executionSummaryEntity, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getPipelineExecutionSummary()).isNotNull();
    verify(pmsExecutionService, times(1)).sendGraphUpdateEvent(executionSummaryEntity);
    verify(pipelineStageHelperV1, times(1)).validateChildGraphToGenerate(any(), eq(stageNodeId));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetResponseDTO_WithRollbackGraphHavingExecutionGraph() {
    String stageNodeId = "stageNodeId";
    String stageNodeExecutionId = "stageNodeExecutionId";
    String childStageNodeId = "childStageNodeId";

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projectId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .planExecutionId(planExecutionId)
                                                                .layoutNodeMap(new HashMap<>())
                                                                .build();

    ChildExecutionDetailDTO rollbackGraph =
        ChildExecutionDetailDTO.builder().executionGraph(ExecutionGraph.builder().build()).build();

    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(rollbackGraph)
        .when(rollbackGraphGenerator)
        .checkAndBuildRollbackGraph(accountId, orgId, projectId, executionSummaryEntity, null, childStageNodeId,
            stageNodeExecutionId, stageNodeId);
    doReturn(false).when(retryExecutionHelper).shouldShowRetryHistory(executionSummaryEntity);
    doReturn(true).when(retryExecutionHelper).isLatestExecution(executionSummaryEntity);
    doReturn(false).when(pipelineStageHelper).validateChildGraphToGenerate(any(), eq(stageNodeId), eq(null));
    doReturn(false).when(pipelineStageHelperV1).validateChildGraphToGenerate(any(), eq(stageNodeId));
    doNothing().when(pmsExecutionService).sendGraphUpdateEvent(executionSummaryEntity);
    doReturn(null).when(pmsExecutionSummaryDtoUpdateHelper).getQueuedReason(executionSummaryEntity);

    PipelineExecutionDetailDTO result = executionHelper.getResponseDTO(
        stageNodeId, stageNodeExecutionId, childStageNodeId, false, executionSummaryEntity, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getPipelineExecutionSummary()).isNotNull();
    assertThat(result.getRollbackGraph()).isNotNull();
    assertThat(result.getRollbackGraph().getExecutionGraph()).isNotNull();
    assertThat(result.getExecutionGraph()).isNull();
    verify(pmsExecutionService, times(1)).sendGraphUpdateEvent(executionSummaryEntity);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetResponseDTO_WithEmptyStageNodeIdAndNoRenderFullBottomGraph() {
    String stageNodeExecutionId = "stageNodeExecutionId";
    String childStageNodeId = "childStageNodeId";

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projectId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .planExecutionId(planExecutionId)
                                                                .layoutNodeMap(new HashMap<>())
                                                                .build();

    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(null)
        .when(rollbackGraphGenerator)
        .checkAndBuildRollbackGraph(
            accountId, orgId, projectId, executionSummaryEntity, null, childStageNodeId, stageNodeExecutionId, null);
    doReturn(false).when(retryExecutionHelper).shouldShowRetryHistory(executionSummaryEntity);
    doReturn(true).when(retryExecutionHelper).isLatestExecution(executionSummaryEntity);
    doReturn(false).when(pipelineStageHelper).validateChildGraphToGenerate(any(), eq(null), eq(null));
    doReturn(false).when(pipelineStageHelperV1).validateChildGraphToGenerate(any(), eq(null));
    doNothing().when(pmsExecutionService).sendGraphUpdateEvent(executionSummaryEntity);
    doReturn(null).when(pmsExecutionSummaryDtoUpdateHelper).getQueuedReason(executionSummaryEntity);

    PipelineExecutionDetailDTO result = executionHelper.getResponseDTO(
        null, stageNodeExecutionId, childStageNodeId, null, executionSummaryEntity, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getPipelineExecutionSummary()).isNotNull();
    assertThat(result.getExecutionGraph()).isNull();
    verify(pmsExecutionService, times(1)).sendGraphUpdateEvent(executionSummaryEntity);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetChildGraph_WithStrategyIterationStepDetails() {
    String accountId = "accountId";
    String stageNodeId = "strategyWrapperNodeId";
    String stageNodeExecutionId = "iterationNodeExecutionId";
    String childStageNodeId = "childStageNodeId";
    String childExecutionId = "childExecutionId";
    String childOrgId = "childOrg";
    String childProjectId = "childProject";

    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder().accountId(accountId).planExecutionId("planExecId").build();

    // Setup layout node map with strategy metadata on iteration node
    Map<String, GraphLayoutNodeDTO> layoutNodeMap = new HashMap<>();
    Map<String, PmsStepDetails> iterationStepDetails = new HashMap<>();
    PmsStepDetails childPipelineDetails = new PmsStepDetails();
    childPipelineDetails.put("orgId", childOrgId);
    childPipelineDetails.put("projectId", childProjectId);
    childPipelineDetails.put("planExecutionId", childExecutionId);
    iterationStepDetails.put("childPipelineExecutionDetails", childPipelineDetails);

    layoutNodeMap.put(stageNodeExecutionId,
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId(stageNodeExecutionId)
            .stepDetails(iterationStepDetails)
            .strategyMetadata(StrategyMetadata.newBuilder().build())
            .nodeType(StepSpecTypeConstants.PIPELINE_STAGE)
            .build());
    layoutNodeMap.put(stageNodeId,
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId(stageNodeId)
            .nodeType(StepSpecTypeConstants.PIPELINE_STAGE)
            .build());
    executionSummaryEntity.setLayoutNodeMap(layoutNodeMap);

    ChildExecutionDetailDTO expectedChildGraph = ChildExecutionDetailDTO.builder().build();
    doReturn(true)
        .when(pipelineStageHelper)
        .validateChildGraphToGenerate(any(), eq(stageNodeId), eq(stageNodeExecutionId));
    when(pipelineStageHelper.getChildGraph(
             accountId, childStageNodeId, null, childExecutionId, childOrgId, childProjectId, null))
        .thenReturn(expectedChildGraph);

    PipelineExecutionDetailDTO result = executionHelper.getResponseDTO(
        stageNodeId, stageNodeExecutionId, childStageNodeId, null, executionSummaryEntity, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getChildGraph()).isEqualTo(expectedChildGraph);
    verify(pipelineStageHelper, times(1))
        .getChildGraph(accountId, childStageNodeId, null, childExecutionId, childOrgId, childProjectId, null);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetChildGraph_WithStrategyIterationStepDetails_NullStepDetails() {
    String accountId = "accountId";
    String stageNodeId = "strategyWrapperNodeId";
    String stageNodeExecutionId = "iterationNodeExecutionId";
    String childStageNodeId = "childStageNodeId";

    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder().accountId(accountId).planExecutionId("planExecId").build();

    Map<String, GraphLayoutNodeDTO> layoutNodeMap = new HashMap<>();
    layoutNodeMap.put(stageNodeExecutionId,
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId(stageNodeExecutionId)
            .stepDetails(null) // Null step details
            .strategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    executionSummaryEntity.setLayoutNodeMap(layoutNodeMap);

    doReturn(true)
        .when(pipelineStageHelper)
        .validateChildGraphToGenerate(any(), eq(stageNodeId), eq(stageNodeExecutionId));

    PipelineExecutionDetailDTO result = executionHelper.getResponseDTO(
        stageNodeId, stageNodeExecutionId, childStageNodeId, null, executionSummaryEntity, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getChildGraph()).isNull();
    verify(pipelineStageHelper, never()).getChildGraph(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetChildGraph_WithStrategyIterationStepDetails_NoStrategyMetadata() {
    String accountId = "accountId";
    String stageNodeId = "normalNodeId";
    String stageNodeExecutionId = "nodeExecutionId";
    String childStageNodeId = "childStageNodeId";
    String childExecutionId = "childExecutionId";
    String childOrgId = "childOrg";
    String childProjectId = "childProject";

    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder().accountId(accountId).planExecutionId("planExecId").build();

    Map<String, GraphLayoutNodeDTO> layoutNodeMap = new HashMap<>();
    Map<String, PmsStepDetails> stepDetails = new HashMap<>();
    PmsStepDetails childPipelineDetails = new PmsStepDetails();
    childPipelineDetails.put("orgId", childOrgId);
    childPipelineDetails.put("projectId", childProjectId);
    childPipelineDetails.put("planExecutionId", childExecutionId);
    stepDetails.put("childPipelineExecutionDetails", childPipelineDetails);

    layoutNodeMap.put(stageNodeExecutionId,
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId(stageNodeExecutionId)
            .stepDetails(stepDetails)
            .strategyMetadata(null) // No strategy metadata
            .build());
    layoutNodeMap.put(
        stageNodeId, GraphLayoutNodeDTO.builder().nodeExecutionId(stageNodeId).stepDetails(stepDetails).build());
    executionSummaryEntity.setLayoutNodeMap(layoutNodeMap);

    ChildExecutionDetailDTO expectedChildGraph = ChildExecutionDetailDTO.builder().build();
    when(pipelineStageHelper.getChildGraph(
             accountId, childStageNodeId, null, childExecutionId, childOrgId, childProjectId, stageNodeExecutionId))
        .thenReturn(expectedChildGraph);
    doReturn(true)
        .when(pipelineStageHelper)
        .validateChildGraphToGenerate(any(), eq(stageNodeId), eq(stageNodeExecutionId));

    PipelineExecutionDetailDTO result = executionHelper.getResponseDTO(
        stageNodeId, stageNodeExecutionId, childStageNodeId, null, executionSummaryEntity, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getChildGraph()).isEqualTo(expectedChildGraph);
    // Should use stageNodeId flow, not stageNodeExecutionId flow
    verify(pipelineStageHelper, times(1))
        .getChildGraph(
            accountId, childStageNodeId, null, childExecutionId, childOrgId, childProjectId, stageNodeExecutionId);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetChildGraph_BothFlows_StrategyFlowTakesPrecedence() {
    String accountId = "accountId";
    String stageNodeId = "strategyWrapperNodeId";
    String stageNodeExecutionId = "iterationNodeExecutionId";
    String childStageNodeId = "childStageNodeId";
    String strategyChildExecutionId = "strategyChildExecutionId";
    String wrapperChildExecutionId = "wrapperChildExecutionId";
    String childOrgId = "childOrg";
    String childProjectId = "childProject";

    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder().accountId(accountId).planExecutionId("planExecId").build();

    Map<String, GraphLayoutNodeDTO> layoutNodeMap = new HashMap<>();

    Map<String, PmsStepDetails> wrapperStepDetails = new HashMap<>();
    PmsStepDetails wrapperChildPipelineDetails = new PmsStepDetails();
    wrapperChildPipelineDetails.put("orgId", childOrgId);
    wrapperChildPipelineDetails.put("projectId", childProjectId);
    wrapperChildPipelineDetails.put("planExecutionId", wrapperChildExecutionId);
    wrapperStepDetails.put("childPipelineExecutionDetails", wrapperChildPipelineDetails);

    Map<String, PmsStepDetails> iterationStepDetails = new HashMap<>();
    PmsStepDetails iterationChildPipelineDetails = new PmsStepDetails();
    iterationChildPipelineDetails.put("orgId", childOrgId);
    iterationChildPipelineDetails.put("projectId", childProjectId);
    iterationChildPipelineDetails.put("planExecutionId", strategyChildExecutionId);
    iterationStepDetails.put("childPipelineExecutionDetails", iterationChildPipelineDetails);

    layoutNodeMap.put(
        stageNodeId, GraphLayoutNodeDTO.builder().nodeExecutionId(stageNodeId).stepDetails(wrapperStepDetails).build());
    layoutNodeMap.put(stageNodeExecutionId,
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId(stageNodeExecutionId)
            .stepDetails(iterationStepDetails)
            .strategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    executionSummaryEntity.setLayoutNodeMap(layoutNodeMap);

    ChildExecutionDetailDTO expectedChildGraph = ChildExecutionDetailDTO.builder().build();
    when(pipelineStageHelper.getChildGraph(
             accountId, childStageNodeId, null, strategyChildExecutionId, childOrgId, childProjectId, null))
        .thenReturn(expectedChildGraph);
    doReturn(true)
        .when(pipelineStageHelper)
        .validateChildGraphToGenerate(any(), eq(stageNodeId), eq(stageNodeExecutionId));

    PipelineExecutionDetailDTO result = executionHelper.getResponseDTO(
        stageNodeId, stageNodeExecutionId, childStageNodeId, null, executionSummaryEntity, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getChildGraph()).isEqualTo(expectedChildGraph);
    // Should use strategy flow with strategyChildExecutionId, NOT wrapperChildExecutionId
    verify(pipelineStageHelper, times(1))
        .getChildGraph(accountId, childStageNodeId, null, strategyChildExecutionId, childOrgId, childProjectId, null);
    verify(pipelineStageHelper, never())
        .getChildGraph(eq(accountId), eq(childStageNodeId), any(), eq(wrapperChildExecutionId), any(), any(), eq(""));
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetChildGraph_WithStrategyIterationStepDetails_WithStrategyInChild() {
    String accountId = "accountId";
    String stageNodeId = "strategyWrapperNodeId";
    String stageNodeExecutionId = "iterationNodeExecutionId";
    String childStageNodeId = "childStageNodeId";
    String childStageNodeExecutionId = "childStageNodeExecutionId";
    String childExecutionId = "childExecutionId";
    String childOrgId = "childOrg";
    String childProjectId = "childProject";

    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder().accountId(accountId).planExecutionId("planExecId").build();

    Map<String, GraphLayoutNodeDTO> layoutNodeMap = new HashMap<>();
    Map<String, PmsStepDetails> iterationStepDetails = new HashMap<>();
    PmsStepDetails childPipelineDetails = new PmsStepDetails();
    childPipelineDetails.put("orgId", childOrgId);
    childPipelineDetails.put("projectId", childProjectId);
    childPipelineDetails.put("planExecutionId", childExecutionId);
    iterationStepDetails.put("childPipelineExecutionDetails", childPipelineDetails);

    layoutNodeMap.put(stageNodeExecutionId,
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId(stageNodeExecutionId)
            .stepDetails(iterationStepDetails)
            .strategyMetadata(StrategyMetadata.newBuilder().build())
            .nodeType(StepSpecTypeConstants.PIPELINE_STAGE)
            .build());
    layoutNodeMap.put(stageNodeId,
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId(stageNodeId)
            .nodeType(StepSpecTypeConstants.PIPELINE_STAGE)
            .build());
    executionSummaryEntity.setLayoutNodeMap(layoutNodeMap);

    ChildExecutionDetailDTO expectedChildGraph = ChildExecutionDetailDTO.builder().build();
    doReturn(true)
        .when(pipelineStageHelper)
        .validateChildGraphToGenerate(any(), eq(stageNodeId), eq(stageNodeExecutionId));
    when(pipelineStageHelper.getChildGraph(accountId, childStageNodeId, null, childExecutionId, childOrgId,
             childProjectId, childStageNodeExecutionId))
        .thenReturn(expectedChildGraph);

    PipelineExecutionDetailDTO result = executionHelper.getResponseDTO(stageNodeId, stageNodeExecutionId,
        childStageNodeId, null, executionSummaryEntity, null, childStageNodeExecutionId);

    assertThat(result).isNotNull();
    assertThat(result.getChildGraph()).isEqualTo(expectedChildGraph);
    verify(pipelineStageHelper, times(1))
        .getChildGraph(
            accountId, childStageNodeId, null, childExecutionId, childOrgId, childProjectId, childStageNodeExecutionId);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetChildGraph_WithStrategyIterationStepDetails_RetryWithStrategyIdentityNodeAndChild() {
    String accountId = "accountId";
    String stageNodeId = "strategyWrapperNodeId";
    String stageNodeExecutionId = "iterationNodeExecutionId";
    String childStageNodeId = "childStageNodeId";
    String childStageNodeExecutionId = "childStageNodeExecutionId";
    String childExecutionId = "childExecutionId";
    String childOrgId = "childOrg";
    String childProjectId = "childProject";

    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder().accountId(accountId).planExecutionId("planExecId").build();

    Map<String, GraphLayoutNodeDTO> layoutNodeMap = new HashMap<>();
    Map<String, PmsStepDetails> iterationStepDetails = new HashMap<>();
    PmsStepDetails childPipelineDetails = new PmsStepDetails();
    childPipelineDetails.put("orgId", childOrgId);
    childPipelineDetails.put("projectId", childProjectId);
    childPipelineDetails.put("planExecutionId", childExecutionId);
    iterationStepDetails.put("childPipelineExecutionDetails", childPipelineDetails);

    layoutNodeMap.put(stageNodeExecutionId,
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId(stageNodeExecutionId)
            .stepDetails(iterationStepDetails)
            .strategyMetadata(StrategyMetadata.newBuilder().build())
            .nodeType(StepSpecTypeConstants.PIPELINE_STAGE)
            .build());
    executionSummaryEntity.setLayoutNodeMap(layoutNodeMap);

    ChildExecutionDetailDTO expectedChildGraph = ChildExecutionDetailDTO.builder().build();
    doReturn(true)
        .when(pipelineStageHelper)
        .validateChildGraphToGenerate(any(), eq(stageNodeId), eq(stageNodeExecutionId));
    when(pipelineStageHelper.getChildGraph(accountId, childStageNodeId, null, childExecutionId, childOrgId,
             childProjectId, childStageNodeExecutionId))
        .thenReturn(expectedChildGraph);

    PipelineExecutionDetailDTO result = executionHelper.getResponseDTO(stageNodeId, stageNodeExecutionId,
        childStageNodeId, null, executionSummaryEntity, null, childStageNodeExecutionId);

    assertThat(result).isNotNull();
    assertThat(result.getChildGraph()).isEqualTo(expectedChildGraph);
    verify(pipelineStageHelper, times(1))
        .getChildGraph(
            accountId, childStageNodeId, null, childExecutionId, childOrgId, childProjectId, childStageNodeExecutionId);
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testStartExecution_FlexEnforcementBlocked() {
    // Setup: Mock the helper to throw AccessDeniedException, as it does when flex enforcement blocks the request.
    doThrow(new AccessDeniedException(
                "Pipeline execution blocked by license enforcement", ErrorCode.ACCESS_DENIED, WingsException.USER))
        .when(pmsPipelineServiceHelper)
        .validateAndThrowFlexEnforcementRules(anyString(), any());

    // Setup: Stub blockExecutionMetadataService to do nothing (it runs before enforcement)
    doReturn(true)
        .when(blockExecutionMetadataService)
        .shouldAllowRun(anyString(), anyString(), anyString(), anyString(), any());

    // Minimal ExecutionMetadata with moduleType="cd"
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setPipelineIdentifier(pipelineId)
                                              .setModuleType("cd")
                                              .setProcessedYamlVersion("0")
                                              .build();

    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build();

    // Execute and verify: Should throw AccessDeniedException (from io.harness.exception)
    assertThatThrownBy(()
                           -> executionHelper.startExecution(accountId, orgId, projectId, executionMetadata,
                               planExecutionMetadataWithContext, null, false, false))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("blocked");

    // Verify pmsPipelineServiceHelper.validateAndThrowFlexEnforcementRules was called with "CD_PIPELINE_EXECUTE"
    verify(pmsPipelineServiceHelper, times(1)).validateAndThrowFlexEnforcementRules(eq("CD_PIPELINE_EXECUTE"), any());
    // Verify blockExecutionMetadataService was called before enforcement
    verify(blockExecutionMetadataService, times(1))
        .shouldAllowRun(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), any());
    // Verify we never reached planCreationQueueRequestHelper
    verify(planCreationQueueRequestHelper, never()).executePlanCreationRequest(any());
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testStartExecution_FlexEnforcementPasses() {
    // Setup: Mock the helper to do nothing (enforcement passes)
    doNothing().when(pmsPipelineServiceHelper).validateAndThrowFlexEnforcementRules(anyString(), any());

    // Stub blockExecutionMetadataService to do nothing
    doReturn(true)
        .when(blockExecutionMetadataService)
        .shouldAllowRun(anyString(), anyString(), anyString(), anyString(), any());

    // Minimal ExecutionMetadata
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setPipelineIdentifier(pipelineId)
                                              .setModuleType("cd")
                                              .setProcessedYamlVersion("0")
                                              .build();

    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build();

    PlanExecution planExecution = PlanExecution.builder().build();

    // Mock planCreationQueueRequestHelper to return a PlanExecution
    doReturn(planExecution).when(planCreationQueueRequestHelper).executePlanCreationRequest(any());

    // Execute
    PlanExecution result = executionHelper.startExecution(
        accountId, orgId, projectId, executionMetadata, planExecutionMetadataWithContext, null, false, false);

    // Verify: pmsPipelineServiceHelper.validateAndThrowFlexEnforcementRules was called once with
    // "CD_PIPELINE_EXECUTE"
    verify(pmsPipelineServiceHelper, times(1)).validateAndThrowFlexEnforcementRules(eq("CD_PIPELINE_EXECUTE"), any());

    // Verify: execution proceeded to planCreationQueueRequestHelper
    verify(planCreationQueueRequestHelper, times(1)).executePlanCreationRequest(any());

    // Verify: result is the expected PlanExecution
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(planExecution);
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testStartExecution_OperationIdDerivedFromModuleType() {
    // Setup: Mock the helper to do nothing
    doNothing().when(pmsPipelineServiceHelper).validateAndThrowFlexEnforcementRules(anyString(), any());

    // Stub blockExecutionMetadataService
    doReturn(true)
        .when(blockExecutionMetadataService)
        .shouldAllowRun(anyString(), anyString(), anyString(), anyString(), any());

    // Mock planCreationQueueRequestHelper
    PlanExecution planExecution = PlanExecution.builder().build();
    doReturn(planExecution).when(planCreationQueueRequestHelper).executePlanCreationRequest(any());

    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build();

    // Use ArgumentCaptor to capture the operationId passed to validateAndThrowFlexEnforcementRules
    org.mockito.ArgumentCaptor<String> operationIdCaptor = org.mockito.ArgumentCaptor.forClass(String.class);

    // Test 1: moduleType="cd" -> "CD_PIPELINE_EXECUTE"
    ExecutionMetadata executionMetadataCd = ExecutionMetadata.newBuilder()
                                                .setPipelineIdentifier(pipelineId)
                                                .setModuleType("cd")
                                                .setProcessedYamlVersion("0")
                                                .build();
    executionHelper.startExecution(
        accountId, orgId, projectId, executionMetadataCd, planExecutionMetadataWithContext, null, false, false);
    verify(pmsPipelineServiceHelper, times(1)).validateAndThrowFlexEnforcementRules(operationIdCaptor.capture(), any());
    assertThat(operationIdCaptor.getValue()).isEqualTo("CD_PIPELINE_EXECUTE");

    // Reset mocks for next test
    Mockito.clearInvocations(pmsPipelineServiceHelper);

    // Test 2: moduleType="ci" -> "CI_PIPELINE_EXECUTE"
    ExecutionMetadata executionMetadataCi = ExecutionMetadata.newBuilder()
                                                .setPipelineIdentifier(pipelineId)
                                                .setModuleType("ci")
                                                .setProcessedYamlVersion("0")
                                                .build();
    executionHelper.startExecution(
        accountId, orgId, projectId, executionMetadataCi, planExecutionMetadataWithContext, null, false, false);
    verify(pmsPipelineServiceHelper, times(1)).validateAndThrowFlexEnforcementRules(operationIdCaptor.capture(), any());
    assertThat(operationIdCaptor.getValue()).isEqualTo("CI_PIPELINE_EXECUTE");

    // Reset mocks for next test
    Mockito.clearInvocations(pmsPipelineServiceHelper);

    // Test 3: moduleType="" (empty) -> "PIPELINE_EXECUTE"
    ExecutionMetadata executionMetadataEmpty = ExecutionMetadata.newBuilder()
                                                   .setPipelineIdentifier(pipelineId)
                                                   .setModuleType("")
                                                   .setProcessedYamlVersion("0")
                                                   .build();
    executionHelper.startExecution(
        accountId, orgId, projectId, executionMetadataEmpty, planExecutionMetadataWithContext, null, false, false);
    verify(pmsPipelineServiceHelper, times(1)).validateAndThrowFlexEnforcementRules(operationIdCaptor.capture(), any());
    assertThat(operationIdCaptor.getValue()).isEqualTo("PIPELINE_EXECUTE");

    // Reset mocks for next test
    Mockito.clearInvocations(pmsPipelineServiceHelper);

    // Test 4: moduleType unset (default empty string in protobuf) -> "PIPELINE_EXECUTE"
    ExecutionMetadata executionMetadataUnset =
        ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).setProcessedYamlVersion("0").build();
    executionHelper.startExecution(
        accountId, orgId, projectId, executionMetadataUnset, planExecutionMetadataWithContext, null, false, false);
    verify(pmsPipelineServiceHelper, times(1)).validateAndThrowFlexEnforcementRules(operationIdCaptor.capture(), any());
    assertThat(operationIdCaptor.getValue()).isEqualTo("PIPELINE_EXECUTE");
  }
}

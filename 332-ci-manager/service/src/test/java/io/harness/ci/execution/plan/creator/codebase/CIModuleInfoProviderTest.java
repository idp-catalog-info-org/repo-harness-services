/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.codebase;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.INITIALIZE_EXECUTION;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_EXECUTION;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_QUEUE_TIME;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;
import static io.harness.rule.OwnerRule.TAPAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.StepExecutionParameters;
import io.harness.beans.execution.PublishedFileArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.outcome.IntegrationStageOutcome;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.beans.sweepingoutputs.InitializeExecutionSweepingOutput;
import io.harness.beans.sweepingoutputs.StageExecutionSweepingOutput;
import io.harness.beans.sweepingoutputs.StageQueueExecutionSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml.HostedVmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.billing.service.CIBillingEventService;
import io.harness.category.element.UnitTests;
import io.harness.cd.beans.moduleinfo.UnifiedPipelineCIInfo;
import io.harness.cd.beans.moduleinfo.UnifiedPipelineExecutionModuleInfo;
import io.harness.cd.beans.moduleinfo.UnifiedStageModuleInfo;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.plan.creator.CIModuleInfoProvider;
import io.harness.ci.execution.plan.creator.PipelineModuleInfoService;
import io.harness.ci.execution.plan.creator.UnifiedModuleInfoHelper;
import io.harness.ci.execution.states.InitializeTaskStep;
import io.harness.ci.execution.states.IntegrationStageStepPMS;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.pipeline.executions.beans.CIBuildAuthor;
import io.harness.ci.pipeline.executions.beans.CIBuildCommit;
import io.harness.ci.plan.creator.execution.CIPipelineModuleInfo;
import io.harness.data.structure.UUIDGenerator;
import io.harness.events.billing.v1.BillingEvent;
import io.harness.eventsframework.api.Producer;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.summary.dto.CILicenseSummaryDTO;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.StepExecutionParametersRepository;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.ssca.execution.orchestration.outcome.PublishedSbomArtifact;
import io.harness.utils.CILicenseUsageUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIModuleInfoProviderTest extends CIExecutionTestBase {
  private CIExecutionPlanTestHelper ciExecutionPlanTestHelper = new CIExecutionPlanTestHelper();

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private StepExecutionParametersRepository stepExecutionParametersRepository;
  @InjectMocks private CIModuleInfoProvider ciModuleInfoProvider;
  @InjectMocks private CILicenseUsageUtils ciLicenseUsageUtils;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private CILicenseService ciLicenseService;
  @Mock private Producer producer;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private PipelineModuleInfoService pipelineModuleInfoService;
  @Mock private CIBillingEventService ciBillingEventService;
  @Mock private UnifiedModuleInfoHelper unifiedModuleInfoHelper;
  @Mock private OutcomeService outcomeService;

  @Before
  public void setUp() {
    on(ciModuleInfoProvider).set("ciLicenseUsageUtils", ciLicenseUsageUtils);
    on(ciModuleInfoProvider).set("ciBillingEventService", ciBillingEventService);
    on(ciModuleInfoProvider).set("unifiedModuleInfoHelper", unifiedModuleInfoHelper);
    on(ciModuleInfoProvider).set("outcomeService", outcomeService);
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfoWithoutResolvedParameters() {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(UUIDGenerator.generateUuid())
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setStepType(IntegrationStageStepPMS.STEP_TYPE)
                   .setIdentifier("stage_1")
                   .build());
    levels.add(Level.newBuilder().setStepType(InitializeTaskStep.STEP_TYPE).build());
    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).serviceName("ci").status(Status.RUNNING).build();
    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .branch("main")
                                    .targetBranch("main")
                                    .sourceBranch("test")
                                    .tag("tag")
                                    .prNumber("1")
                                    .repoUrl("https://github.com/test/repo-name")
                                    .gitUserId("userId")
                                    .gitUser("userId")
                                    .gitUserAvatar("avatar")
                                    .gitUserEmail("email")
                                    .build())
                        .build());
    CILicenseSummaryDTO ciLicenseSummaryDTO =
        CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build();
    when(ciLicenseService.getLicenseSummary(any(), eq(ModuleType.CI.name()), any())).thenReturn(ciLicenseSummaryDTO);
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getRepoName()).isEqualTo("repo-name");
    assertThat(ciPipelineModuleInfo.getPrNumber()).isEqualTo("1");
    assertThat(ciPipelineModuleInfo.getTag()).isEqualTo("tag");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getSourceBranch()).isEqualTo("test");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getTargetBranch()).isEqualTo("main");

    CIBuildAuthor author = ciPipelineModuleInfo.getCiExecutionInfoDTO().getAuthor();
    assertThat(author.getId()).isEqualTo("userId");
    assertThat(author.getName()).isEqualTo("userId");
    assertThat(author.getAvatar()).isEqualTo("avatar");
    assertThat(author.getEmail()).isEqualTo("email");

    assertThat(ciPipelineModuleInfo.getScmDetailsList().size()).isEqualTo(0);
    assertThat(ciPipelineModuleInfo.getInfraDetailsList().size()).isEqualTo(0);
    assertThat(ciPipelineModuleInfo.getImageDetailsList().size()).isEqualTo(0);
    assertThat(ciPipelineModuleInfo.getTiBuildDetailsList().size()).isEqualTo(0);

    assertThat(ciPipelineModuleInfo.getCiLicenseType()).isEqualTo(LicenseType.PAID.toString());
    assertThat(ciPipelineModuleInfo.getCiEditionType()).isEqualTo(Edition.ENTERPRISE.toString());
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfoWithoutResolvedParametersNoAvatar() {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(UUIDGenerator.generateUuid())
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setStepType(IntegrationStageStepPMS.STEP_TYPE)
                   .setIdentifier("stage_1")
                   .build());
    levels.add(Level.newBuilder().setStepType(InitializeTaskStep.STEP_TYPE).build());
    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).build();
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).serviceName("ci").status(Status.RUNNING).build();
    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .branch("main")
                                    .targetBranch("main")
                                    .sourceBranch("test")
                                    .tag("tag")
                                    .prNumber("1")
                                    .repoUrl("https://github.com/test/repo-name")
                                    .gitUserId("userId")
                                    .gitUser("userId")
                                    .gitUserEmail("email")
                                    .build())
                        .build());
    CILicenseSummaryDTO ciLicenseSummaryDTO =
        CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build();
    when(ciLicenseService.getLicenseSummary(any(), eq(ModuleType.CI.name()), any())).thenReturn(ciLicenseSummaryDTO);
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getRepoName()).isEqualTo("repo-name");
    assertThat(ciPipelineModuleInfo.getPrNumber()).isEqualTo("1");
    assertThat(ciPipelineModuleInfo.getTag()).isEqualTo("tag");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getSourceBranch()).isEqualTo("test");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getTargetBranch()).isEqualTo("main");

    CIBuildAuthor author = ciPipelineModuleInfo.getCiExecutionInfoDTO().getAuthor();
    assertThat(author.getId()).isEqualTo("userId");
    assertThat(author.getName()).isEqualTo("userId");
    assertThat(author.getEmail()).isEqualTo("email");

    assertThat(ciPipelineModuleInfo.getScmDetailsList().size()).isEqualTo(0);
    assertThat(ciPipelineModuleInfo.getInfraDetailsList().size()).isEqualTo(0);
    assertThat(ciPipelineModuleInfo.getImageDetailsList().size()).isEqualTo(0);
    assertThat(ciPipelineModuleInfo.getTiBuildDetailsList().size()).isEqualTo(0);

    assertThat(ciPipelineModuleInfo.getCiLicenseType()).isEqualTo(LicenseType.PAID.toString());
    assertThat(ciPipelineModuleInfo.getCiEditionType()).isEqualTo(Edition.ENTERPRISE.toString());
  }
  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfoWithResolvedParameters() {
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .infrastructure(ciExecutionPlanTestHelper.getInfrastructureWithVolume())
            .executionElementConfig(ciExecutionPlanTestHelper.getExecutionElementConfig())
            .ciCodebase(ciExecutionPlanTestHelper.getCICodebaseWithRepoName())
            .build();

    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(getAmbianceWithLevel(Level.newBuilder().setStepType(InitializeTaskStep.STEP_TYPE).build()))
            .serviceName("ci")
            .status(Status.RUNNING)
            .resolvedStepParameters(StepElementParameters.builder().spec(initializeStepInfo).build())
            .build();
    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().build());
    when(connectorUtils.getConnectorDetails(any(), any(), eq(true)))
        .thenReturn(ciExecutionPlanTestHelper.getGitConnector());
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);

    assertThat(ciPipelineModuleInfo.getScmDetailsList().size()).isEqualTo(1);
    assertThat(ciPipelineModuleInfo.getInfraDetailsList().size()).isEqualTo(1);
    assertThat(ciPipelineModuleInfo.getImageDetailsList().size()).isEqualTo(4);
    assertThat(ciPipelineModuleInfo.getTiBuildDetailsList().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfoForAzure() {
    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(getAmbianceWithLevel(Level.newBuilder().setStepType(InitializeTaskStep.STEP_TYPE).build()))
            .serviceName("ci")
            .status(Status.RUNNING)
            .build();

    List<CodebaseSweepingOutput.CodeBaseCommit> commits =
        new ArrayList<>(Arrays.asList(CodebaseSweepingOutput.CodeBaseCommit.builder().id("1").build(),
            CodebaseSweepingOutput.CodeBaseCommit.builder().id("2").build()));
    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .branch("main")
                                    .targetBranch("main")
                                    .sourceBranch("test")
                                    .tag("tag")
                                    .prNumber("1")
                                    .repoUrl("https://dev.azure.com/org/test/_git/test")
                                    .commits(commits)
                                    .build())
                        .build());
    List<CIBuildCommit> ciBuildCommits = new ArrayList<>(
        Arrays.asList(CIBuildCommit.builder().id("1").build(), CIBuildCommit.builder().id("2").build()));
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getRepoName()).isEqualTo("test/_git/test");
    assertThat(ciPipelineModuleInfo.getPrNumber()).isEqualTo("1");
    assertThat(ciPipelineModuleInfo.getTag()).isEqualTo("tag");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getSourceBranch()).isEqualTo("test");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getTargetBranch()).isEqualTo("main");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getCommits()).isEqualTo(ciBuildCommits);
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getAuthor()).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testGetPipelineStageLevelModuleWithParamsFromDB() {
    Ambiance ambiance = getAmbianceWithLevel(
        Level.newBuilder().setStartTs(1111L).setStepType(IntegrationStageStepPMS.STEP_TYPE).build());
    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.MacOS))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();
    StageElementParameters stageElementParameters =
        StageElementParameters.builder()
            .identifier("stageId")
            .name("stageName")
            .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
            .build();

    String jsonString = RecastOrchestrationUtils.toJson(stageElementParameters);
    OrchestrationEvent event = OrchestrationEvent.builder()
                                   .ambiance(ambiance)
                                   .serviceName("ci")
                                   .resolvedStepParameters(null)
                                   .status(Status.RUNNING)
                                   .build();
    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(
            Optional.of(StepExecutionParameters.builder().accountId("accountId").stepParameters(jsonString).build()));

    when(executionSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(1234L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageExecutionSweepingOutput.builder().stageExecutionTime(5671L).build())
                        .build());
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageId()).isEqualTo("stageId");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageName()).isEqualTo("stageName");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getOsArch()).isEqualTo("Amd64");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getOsType()).isEqualTo("MacOS");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageExecutionId()).isEqualTo("stageExecutionId");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getCpuTime()).isEqualTo(4437L);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageBuildTime()).isEqualTo(5671L);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStartTs()).isEqualTo(1111L);
    verify(producer, times(0)).send(any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetPipelineStageLevelModuleInfoForHostedVM() {
    Ambiance ambiance = getAmbianceWithLevel(
        Level.newBuilder().setStartTs(1111L).setStepType(IntegrationStageStepPMS.STEP_TYPE).build());
    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.MacOS))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();
    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(ambiance)
            .serviceName("ci")
            .resolvedStepParameters(
                StageElementParameters.builder()
                    .identifier("stageId")
                    .name("stageName")
                    .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
                    .build())
            .status(Status.RUNNING)
            .build();
    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));

    when(executionSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(1234L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageExecutionSweepingOutput.builder().stageExecutionTime(5671L).build())
                        .build());
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageId()).isEqualTo("stageId");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageName()).isEqualTo("stageName");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getOsArch()).isEqualTo("Amd64");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getOsType()).isEqualTo("MacOS");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageExecutionId()).isEqualTo("stageExecutionId");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getCpuTime()).isEqualTo(4437L);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageBuildTime()).isEqualTo(5671L);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStartTs()).isEqualTo(1111L);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetPipelineStageLevelModuleInfoPopulatesQueueTime() {
    Ambiance ambiance = getAmbianceWithLevel(
        Level.newBuilder().setStartTs(1111L).setStepType(IntegrationStageStepPMS.STEP_TYPE).build());
    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.MacOS))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();
    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(ambiance)
            .serviceName("ci")
            .resolvedStepParameters(
                StageElementParameters.builder()
                    .identifier("stageId")
                    .name("stageName")
                    .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
                    .build())
            .status(Status.RUNNING)
            .build();
    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));

    when(executionSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(1234L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageExecutionSweepingOutput.builder().stageExecutionTime(5671L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_QUEUE_TIME)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageQueueExecutionSweepingOutput.builder().queueTimeMs(4200L).build())
                        .build());
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getQueueTimeMs()).isEqualTo(4200L);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetPipelineStageLevelModuleInfoWithoutResolvedParameter() {
    Ambiance ambiance = getAmbianceWithLevel(Level.newBuilder().setStepType(IntegrationStageStepPMS.STEP_TYPE).build());
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).serviceName("ci").status(Status.RUNNING).build();

    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNull();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testShouldPublishLicenseUsageDetails() {
    Ambiance ambiance = getAmbianceWithLevel(
        Level.newBuilder().setStartTs(1111L).setStepType(IntegrationStageStepPMS.STEP_TYPE).build());
    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.MacOS))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();
    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(ambiance)
            .serviceName("ci")
            .resolvedStepParameters(
                StageElementParameters.builder()
                    .identifier("stageId")
                    .name("stageName")
                    .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
                    .build())
            .status(Status.SUCCEEDED)
            .build();
    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));

    when(executionSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(1234L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageExecutionSweepingOutput.builder().stageExecutionTime(5671L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    doNothing().when(pipelineModuleInfoService).saveStageModuleInfo(any(), any(), any());
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageId()).isEqualTo("stageId");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageName()).isEqualTo("stageName");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getOsArch()).isEqualTo("Amd64");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getOsType()).isEqualTo("MacOS");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageExecutionId()).isEqualTo("stageExecutionId");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getCpuTime()).isEqualTo(4437L);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageBuildTime()).isEqualTo(5671L);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStartTs()).isEqualTo(1111L);
    verify(producer, times(1)).send(any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testShouldNotPublishLicenseUsageDetails() {
    Ambiance ambiance = getAmbianceWithLevel(
        Level.newBuilder().setStartTs(1111L).setStepType(StepType.newBuilder().setType("IDPStage").build()).build());
    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.MacOS))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();
    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(ambiance)
            .serviceName("ci")
            .resolvedStepParameters(
                StageElementParameters.builder()
                    .identifier("stageId")
                    .name("stageName")
                    .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
                    .build())
            .status(Status.SUCCEEDED)
            .build();
    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));

    when(executionSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(1234L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageExecutionSweepingOutput.builder().stageExecutionTime(5671L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageId()).isEqualTo("stageId");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageName()).isEqualTo("stageName");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getOsArch()).isEqualTo("Amd64");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getOsType()).isEqualTo("MacOS");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageExecutionId()).isEqualTo("stageExecutionId");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getCpuTime()).isEqualTo(4437L);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageBuildTime()).isEqualTo(5671L);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStartTs()).isEqualTo(1111L);
    verify(producer, times(0)).send(any());
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testShouldPublishBillingEventForCloudBuild() {
    // Test that billing event IS published for cloud/hosted VM builds
    Ambiance ambiance = getAmbianceWithLevel(Level.newBuilder()
                                                 .setStartTs(1111L)
                                                 .setStepType(IntegrationStageStepPMS.STEP_TYPE)
                                                 .setIdentifier("stageId")
                                                 .build());

    // Create HostedVm infrastructure (cloud build)
    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();

    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(ambiance)
            .serviceName("ci")
            .resolvedStepParameters(
                StageElementParameters.builder()
                    .identifier("stageId")
                    .name("stageName")
                    .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
                    .build())
            .status(Status.SUCCEEDED)
            .build();

    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));
    when(executionSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(1234L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageExecutionSweepingOutput.builder().stageExecutionTime(5671L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    doNothing().when(pipelineModuleInfoService).saveStageModuleInfo(any(), any(), any());

    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);

    // Verify billing event was published for cloud build
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getInfraType()).isEqualTo("HostedVm");
    verify(ciBillingEventService, times(1)).publishBillingEventAsync(any(BillingEvent.class));
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testShouldNotPublishBillingEventForNonCloudBuild() {
    // Test that billing event is NOT published for non-cloud builds (Kubernetes)
    Ambiance ambiance = getAmbianceWithLevel(Level.newBuilder()
                                                 .setStartTs(1111L)
                                                 .setStepType(IntegrationStageStepPMS.STEP_TYPE)
                                                 .setIdentifier("stageId")
                                                 .build());

    // Create Kubernetes infrastructure (non-cloud build)
    Infrastructure infrastructure = K8sDirectInfraYaml.builder()
                                        .spec(K8sDirectInfraYamlSpec.builder()
                                                  .connectorRef(ParameterField.createValueField("k8sConnector"))
                                                  .namespace(ParameterField.createValueField("default"))
                                                  .build())
                                        .build();

    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(ambiance)
            .serviceName("ci")
            .resolvedStepParameters(
                StageElementParameters.builder()
                    .identifier("stageId")
                    .name("stageName")
                    .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
                    .build())
            .status(Status.SUCCEEDED)
            .build();

    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));
    when(executionSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(1234L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageExecutionSweepingOutput.builder().stageExecutionTime(5671L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    doNothing().when(pipelineModuleInfoService).saveStageModuleInfo(any(), any(), any());

    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);

    // Verify billing event was NOT published for non-cloud build
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getInfraType()).isEqualTo("KubernetesDirect");
    verify(ciBillingEventService, times(0)).publishBillingEventAsync(any(BillingEvent.class));
  }

  private Ambiance getAmbianceWithLevel(Level level) {
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Maps.of(
            "accountId", "accountId", "projectIdentifier", "projectIdentifier", "orgIdentifier", "orgIdentifier"))
        .addLevels(level)
        .setStageExecutionId("stageExecutionId")
        .build();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testShouldBuildUnifiedPipelineExecutionModuleInfoForIntegrationStageWithArtifacts() {
    Ambiance ambiance = getAmbianceWithLevel(Level.newBuilder()
                                                 .setStartTs(1111L)
                                                 .setStepType(IntegrationStageStepPMS.STEP_TYPE)
                                                 .setIdentifier("stageId")
                                                 .build());

    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();

    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(ambiance)
            .serviceName("ci")
            .resolvedStepParameters(
                StageElementParameters.builder()
                    .identifier("stageId")
                    .name("stageName")
                    .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
                    .build())
            .status(Status.SUCCEEDED)
            .build();

    // Create IntegrationStageOutcome with artifacts
    PublishedImageArtifact imageArtifact = PublishedImageArtifact.builder()
                                               .imageName("myimage")
                                               .tag("latest")
                                               .url("docker.io/myimage:latest")
                                               .digest("sha256:abc123")
                                               .build();

    PublishedFileArtifact fileArtifact =
        PublishedFileArtifact.builder().name("artifact.jar").url("https://storage.example.com/artifact.jar").build();

    PublishedSbomArtifact sbomArtifact = PublishedSbomArtifact.builder()
                                             .id("sbom-1")
                                             .imageName("myimage")
                                             .tag("latest")
                                             .sbomName("myimage-sbom.json")
                                             .sbomUrl("https://storage.example.com/sbom.json")
                                             .build();

    IntegrationStageOutcome integrationStageOutcome = IntegrationStageOutcome.builder()
                                                          .imageArtifact(imageArtifact)
                                                          .fileArtifact(fileArtifact)
                                                          .sbomArtifact(sbomArtifact)
                                                          .build();

    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));
    when(executionSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(1234L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageExecutionSweepingOutput.builder().stageExecutionTime(5671L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    doNothing().when(pipelineModuleInfoService).saveStageModuleInfo(any(), any(), any());

    // Mock the outcomeService to return IntegrationStageOutcome
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(integrationStageOutcome).build());

    // Mock UnifiedModuleInfoHelper to return proper UnifiedPipelineExecutionModuleInfo
    UnifiedPipelineExecutionModuleInfo expectedModuleInfo =
        UnifiedPipelineExecutionModuleInfo.builder()
            .pipelineCIInfo(UnifiedPipelineCIInfo.builder()
                                .imageArtifact(imageArtifact)
                                .fileArtifact(fileArtifact)
                                .sbomArtifact(sbomArtifact)
                                .build())
            .stageInfo("stageExecutionId",
                UnifiedStageModuleInfo.builder()
                    .ciImageArtifacts(java.util.Set.of(imageArtifact))
                    .ciFileArtifacts(java.util.Set.of(fileArtifact))
                    .ciSbomArtifacts(java.util.Set.of(sbomArtifact))
                    .build())
            .build();
    when(unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(any(), any()))
        .thenReturn(expectedModuleInfo);

    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);

    assertThat(ciPipelineModuleInfo).isNotNull();
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo().getPipelineCIInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo().getPipelineCIInfo().getImageArtifacts())
        .hasSize(1);
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo().getPipelineCIInfo().getFileArtifacts())
        .hasSize(1);
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo().getPipelineCIInfo().getSbomArtifacts())
        .hasSize(1);
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo().getStageInfoMap())
        .containsKey("stageExecutionId");

    verify(unifiedModuleInfoHelper, times(1)).buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(any(), any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testShouldNotBuildUnifiedPipelineExecutionModuleInfoForNonFinalStatus() {
    Ambiance ambiance = getAmbianceWithLevel(Level.newBuilder()
                                                 .setStartTs(1111L)
                                                 .setStepType(IntegrationStageStepPMS.STEP_TYPE)
                                                 .setIdentifier("stageId")
                                                 .build());

    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();

    // Non-final status - RUNNING
    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(ambiance)
            .serviceName("ci")
            .resolvedStepParameters(
                StageElementParameters.builder()
                    .identifier("stageId")
                    .name("stageName")
                    .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
                    .build())
            .status(Status.RUNNING)
            .build();

    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));
    when(executionSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(1234L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageExecutionSweepingOutput.builder().stageExecutionTime(5671L).build())
                        .build());

    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);

    assertThat(ciPipelineModuleInfo).isNotNull();
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNotNull();
    // Should be null since status is not final
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo()).isNull();

    // Verify buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage was NOT called
    verify(unifiedModuleInfoHelper, times(0)).buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(any(), any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testShouldHandleNullIntegrationStageOutcome() {
    Ambiance ambiance = getAmbianceWithLevel(Level.newBuilder()
                                                 .setStartTs(1111L)
                                                 .setStepType(IntegrationStageStepPMS.STEP_TYPE)
                                                 .setIdentifier("stageId")
                                                 .build());

    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();

    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(ambiance)
            .serviceName("ci")
            .resolvedStepParameters(
                StageElementParameters.builder()
                    .identifier("stageId")
                    .name("stageName")
                    .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
                    .build())
            .status(Status.SUCCEEDED)
            .build();

    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));
    when(executionSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(1234L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageExecutionSweepingOutput.builder().stageExecutionTime(5671L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    doNothing().when(pipelineModuleInfoService).saveStageModuleInfo(any(), any(), any());

    // Return null when buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage is called
    // (simulating when IntegrationStageOutcome is not found)
    when(unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(any(), any()))
        .thenReturn(null);

    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);

    assertThat(ciPipelineModuleInfo).isNotNull();
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNotNull();
    // Should be null since helper returned null
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo()).isNull();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testShouldBuildUnifiedPipelineExecutionModuleInfoWithOnlyImageArtifacts() {
    Ambiance ambiance = getAmbianceWithLevel(Level.newBuilder()
                                                 .setStartTs(1111L)
                                                 .setStepType(IntegrationStageStepPMS.STEP_TYPE)
                                                 .setIdentifier("stageId")
                                                 .build());

    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.Linux))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();

    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(ambiance)
            .serviceName("ci")
            .resolvedStepParameters(
                StageElementParameters.builder()
                    .identifier("stageId")
                    .name("stageName")
                    .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
                    .build())
            .status(Status.SUCCEEDED)
            .build();

    // Create IntegrationStageOutcome with only image artifacts
    PublishedImageArtifact imageArtifact1 = PublishedImageArtifact.builder()
                                                .imageName("image1")
                                                .tag("v1.0")
                                                .url("docker.io/image1:v1.0")
                                                .digest("sha256:def456")
                                                .build();

    PublishedImageArtifact imageArtifact2 = PublishedImageArtifact.builder()
                                                .imageName("image2")
                                                .tag("v2.0")
                                                .url("gcr.io/project/image2:v2.0")
                                                .digest("sha256:ghi789")
                                                .build();

    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));
    when(executionSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(1234L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageExecutionSweepingOutput.builder().stageExecutionTime(5671L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    doNothing().when(pipelineModuleInfoService).saveStageModuleInfo(any(), any(), any());

    UnifiedPipelineExecutionModuleInfo expectedModuleInfo =
        UnifiedPipelineExecutionModuleInfo.builder()
            .pipelineCIInfo(
                UnifiedPipelineCIInfo.builder().imageArtifact(imageArtifact1).imageArtifact(imageArtifact2).build())
            .stageInfo("stageExecutionId",
                UnifiedStageModuleInfo.builder()
                    .ciImageArtifacts(java.util.Set.of(imageArtifact1, imageArtifact2))
                    .build())
            .build();
    when(unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(any(), any()))
        .thenReturn(expectedModuleInfo);

    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);

    assertThat(ciPipelineModuleInfo).isNotNull();
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo().getPipelineCIInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo().getPipelineCIInfo().getImageArtifacts())
        .hasSize(2);
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo().getPipelineCIInfo().getFileArtifacts())
        .isNullOrEmpty();
    assertThat(ciPipelineModuleInfo.getUnifiedPipelineExecutionModuleInfo().getPipelineCIInfo().getSbomArtifacts())
        .isNullOrEmpty();
  }
}

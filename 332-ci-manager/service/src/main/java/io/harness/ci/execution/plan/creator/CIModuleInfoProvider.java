/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator;

import static io.harness.beans.FeatureName.CI_USE_UNIQUE_PARENT_ID_FOR_QUERY;
import static io.harness.beans.FeatureName.PL_ENABLE_LICENSE_USAGE_COMPUTE;
import static io.harness.beans.execution.WebhookEvent.Type.BRANCH;
import static io.harness.beans.execution.WebhookEvent.Type.DELETE;
import static io.harness.beans.execution.WebhookEvent.Type.PR;
import static io.harness.beans.execution.WebhookEvent.Type.RELEASE;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.INITIALIZE_EXECUTION;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_EXECUTION;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_QUEUE_TIME;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.CIExecutionConstants.OPTIMIZATION_STATE_DISABLED;
import static io.harness.ci.commonconstants.CIExecutionConstants.OPTIMIZATION_STATE_FULL_RUN;
import static io.harness.ci.commonconstants.CIExecutionConstants.OPTIMIZATION_STATE_OPTIMIZED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.git.GitClientHelper.getGitRepo;
import static io.harness.idp.common.Constants.IDPStageStepPMSType;
import static io.harness.pms.execution.utils.StatusUtils.isFailedStatus;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.StepExecutionParameters;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.DeleteWebhookEvent;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.ReleaseWebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.CIPipelineBaseline;
import io.harness.beans.steps.CIStageBaseline;
import io.harness.beans.steps.CIStepOptimizationState;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.InitializeExecutionSweepingOutput;
import io.harness.beans.sweepingoutputs.StageExecutionSweepingOutput;
import io.harness.beans.sweepingoutputs.StageQueueExecutionSweepingOutput;
import io.harness.beans.yaml.extended.CIResourceClass;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.billing.service.CIBillingEventService;
import io.harness.cd.beans.moduleinfo.UnifiedPipelineExecutionModuleInfo;
import io.harness.ci.commonconstants.CIExecutionConstants;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.execution.plan.creator.execution.CIStageModuleInfo;
import io.harness.ci.execution.states.InitializeTaskStep;
import io.harness.ci.execution.states.IntegrationStageStepPMS;
import io.harness.ci.execution.states.SecurityStageStepPMS;
import io.harness.ci.execution.utils.WebhookTriggerProcessorUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.pipeline.executions.beans.CIBuildAuthor;
import io.harness.ci.pipeline.executions.beans.CIBuildBranchHook;
import io.harness.ci.pipeline.executions.beans.CIBuildCommit;
import io.harness.ci.pipeline.executions.beans.CIBuildPRHook;
import io.harness.ci.pipeline.executions.beans.CIImageDetails;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.ci.pipeline.executions.beans.CIScmDetails;
import io.harness.ci.pipeline.executions.beans.CIStageOptimizationState;
import io.harness.ci.pipeline.executions.beans.CIWebhookInfoDTO;
import io.harness.ci.pipeline.executions.beans.TIBuildDetails;
import io.harness.ci.plan.creator.execution.CIPipelineModuleInfo;
import io.harness.ci.plan.creator.execution.CIPipelineModuleInfo.CIPipelineModuleInfoBuilder;
import io.harness.ci.plan.creator.execution.CIPipelineStageModuleInfo;
import io.harness.ci.plan.creator.execution.CIPipelineStageModuleInfo.CIPipelineStageModuleInfoBuilder;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.events.billing.v1.BillingEvent;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.platform.ArchitectureType;
import io.harness.eventsframework.schemas.platform.BuildInfraType;
import io.harness.eventsframework.schemas.platform.CILicenseUsageData;
import io.harness.eventsframework.schemas.platform.Developer;
import io.harness.eventsframework.schemas.platform.LicenseUsageEvent;
import io.harness.eventsframework.schemas.platform.LicenseUsageEvent.Builder;
import io.harness.eventsframework.schemas.platform.ModuleName;
import io.harness.eventsframework.schemas.platform.OSType;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.steps.common.StageBaseParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.execution.ExecutionSummaryModuleInfoProvider;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;
import io.harness.pms.sdk.execution.beans.StageModuleInfo;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIPipelineBaselineRespository;
import io.harness.repositories.CIStageBaselineRepository;
import io.harness.repositories.CIStageSavingsInfoRepository;
import io.harness.repositories.CIStepOptimizationStateRepository;
import io.harness.repositories.StepExecutionParametersRepository;
import io.harness.serializer.ProtoUtils;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.shared.billing.v1.BillingMetric;
import io.harness.utils.CILicenseUsageUtils;
import io.harness.utils.CIScopeInfoHelper;
import io.harness.utils.DateTimeUtils;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.spec.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.TagBuildSpec;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIModuleInfoProvider implements ExecutionSummaryModuleInfoProvider {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private CILicenseService ciLicenseService;

  @Inject private StepExecutionParametersRepository stepExecutionParametersRepository;
  @Inject private CIStepOptimizationStateRepository ciStepOptimizationStateRepository;
  @Inject private CIPipelineBaselineRespository ciPipelineBaselineRespository;
  @Inject private CIStageBaselineRepository ciStageBaselineRespository;
  @Inject private PipelineModuleInfoService pipelineModuleInfoService;
  @Inject private CIStageSavingsInfoRepository ciStageSavingsInfoRepository;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject(optional = true) private CIBillingEventService ciBillingEventService;
  @Inject(optional = true)
  @Named(EventsFrameworkConstants.LICENSES_USAGE_REDIS_EVENT_CONSUMER)
  private Producer licenseUsageProducer;
  @Inject CILicenseUsageUtils ciLicenseUsageUtils;
  @Inject(optional = true) CIScopeInfoHelper scopeInfoHelper;
  @Inject private UnifiedModuleInfoHelper unifiedModuleInfoHelper;
  @Inject OutcomeService outcomeService;

  private final String IntegrationStageStepPMSType = "IntegrationStageStepPMS";
  private final String IACMIntegrationStageStepPMSType = "IACMIntegrationStageStepPMS";
  private final String SecurityStageStepPMSType = "SecurityStageStepPMS";

  String NULL_STR = "null";

  @Override
  public boolean shouldRun(OrchestrationEvent event) {
    StepType currentStepType = AmbianceUtils.getCurrentStepType(event.getAmbiance());
    return currentStepType != null && isWhitelistedNode(currentStepType);
  }

  @Override
  public PipelineModuleInfo getPipelineLevelModuleInfo(OrchestrationEvent event) {
    StepType currentStepType = AmbianceUtils.getCurrentStepType(event.getAmbiance());
    if (currentStepType != null && Objects.equals(currentStepType.getType(), InitializeTaskStep.STEP_TYPE.getType())) {
      String branch = null;
      String tag = null;
      String prNumber = null;
      String repoName = null;
      String buildType = null;
      String triggerRepoName = null;
      String url = null;
      String licenseType = null;
      String editionType = null;

      List<CIScmDetails> scmDetailsList = new ArrayList<>();
      List<CIInfraDetails> infraDetailsList = new ArrayList<>();
      List<CIImageDetails> imageDetailsList = new ArrayList<>();
      List<TIBuildDetails> tiBuildDetailsList = new ArrayList<>();

      CIBuildAuthor author = null;
      Boolean isPrivateRepo = false;
      List<CIBuildCommit> triggerCommits = null;
      ExecutionTriggerInfo executionTriggerInfo = event.getAmbiance().getMetadata().getTriggerInfo();
      Ambiance ambiance = event.getAmbiance();
      BaseNGAccess baseNGAccess = retrieveBaseNGAccess(ambiance);

      String runTimeId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
      String accountId = AmbianceUtils.getAccountId(ambiance);
      Optional<StepExecutionParameters> stepExecutionParameters =
          stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(accountId, runTimeId);
      StepParameters stepParameters = null;
      if (stepExecutionParameters.isPresent()) {
        try {
          StepExecutionParameters executionParameters = stepExecutionParameters.get();
          stepParameters =
              RecastOrchestrationUtils.fromJson(executionParameters.getStepParameters(), StepParameters.class);
        } catch (Exception ex) {
          log.error("Error in deserialization", ex);
          StepElementParameters stepElementParameters = (StepElementParameters) event.getResolvedStepParameters();
          if (stepElementParameters != null) {
            stepParameters = stepElementParameters;
          }
        }
      } else {
        log.warn("Step Execution Parameters are not present so using resolvedStepParameters from OrchestrationEvent");
        StepElementParameters stepElementParameters = (StepElementParameters) event.getResolvedStepParameters();
        if (stepElementParameters != null) {
          stepParameters = stepElementParameters;
        }
      }

      try {
        if (stepParameters != null) {
          StepBaseParameters stepElementParameters = (StepBaseParameters) stepParameters;

          InitializeStepInfo initializeStepInfo = (InitializeStepInfo) stepElementParameters.getSpec();

          if (initializeStepInfo == null) {
            return null;
          }

          ParameterField<Build> buildParameterField = null;
          if (initializeStepInfo.getCiCodebase() != null) {
            buildParameterField = initializeStepInfo.getCiCodebase().getBuild();

            if (isNotEmpty(initializeStepInfo.getCiCodebase().getRepoName().getValue())) {
              repoName = initializeStepInfo.getCiCodebase().getRepoName().getValue();
            }
            if (StringUtils.isNotBlank(repoName)
                || initializeStepInfo.getCiCodebase().getConnectorRef().getValue() != null) {
              try {
                ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(
                    baseNGAccess, initializeStepInfo.getCiCodebase().getConnectorRef().getValue(), true);
                url =
                    IntegrationStageUtils.getGitURLFromConnector(connectorDetails, initializeStepInfo.getCiCodebase());
                if (isEmpty(repoName) || repoName.equals(NULL_STR)) {
                  repoName = getGitRepo(url);
                }
                CIScmDetails scmDetails = IntegrationStageUtils.getCiScmDetails(connectorUtils, connectorDetails);
                scmDetails.setScmUrl(url);
                scmDetailsList.add(scmDetails);
              } catch (Exception exception) {
                log.warn("Failed to retrieve repo");
              }
            }
          }
          infraDetailsList.add(IntegrationStageUtils.getCiInfraDetails(initializeStepInfo.getInfrastructure()));
          imageDetailsList = IntegrationStageUtils.getCiImageDetails(initializeStepInfo);
          tiBuildDetailsList = IntegrationStageUtils.getTiBuildDetails(initializeStepInfo);

          if (isNotEmpty(url)) {
            if (GitClientHelper.isHTTPProtocol(url)) {
              isPrivateRepo = GitClientHelper.isGitUrlPrivate(url);
            } else {
              isPrivateRepo = true;
            }
          }

          Build build = RunTimeInputHandler.resolveBuild(buildParameterField);
          if (build != null) {
            buildType = build.getType().toString();
          }
          if (build != null && build.getType().equals(BuildType.BRANCH)) {
            branch = (String) ((BranchBuildSpec) build.getSpec()).getBranch().fetchFinalValue();
          }

          if (build != null && build.getType().equals(BuildType.PR)) {
            if (((PRBuildSpec) build.getSpec()).getNumber().isExpression() == false) {
              prNumber = (String) ((PRBuildSpec) build.getSpec()).getNumber().fetchFinalValue();
            }
          }

          if (build != null && build.getType().equals(BuildType.TAG)) {
            tag = (String) ((TagBuildSpec) build.getSpec()).getTag().fetchFinalValue();
          }
        }
      } catch (Exception ex) {
        log.error("Failed to retrieve branch and tag for filtering", ex);
      }
      ExecutionSource executionSource = null;
      try {
        executionSource = getWebhookExecutionSource(event.getAmbiance().getMetadata(), event.getTriggerPayload());
      } catch (Exception ex) {
        log.error("Failed to retrieve branch and tag for filtering", ex);
      }

      try {
        String moduleType = AmbianceUtils.getStageModuleType(ambiance);
        LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(
            baseNGAccess.getAccountIdentifier(), moduleType, ambiance.getMetadata().getPrincipalInfo());

        if (licensesWithSummaryDTO == null) {
          throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
        }

        if (licensesWithSummaryDTO != null && licensesWithSummaryDTO.getLicenseType() != null) {
          licenseType = licensesWithSummaryDTO.getLicenseType() != null
              ? licensesWithSummaryDTO.getLicenseType().toString()
              : null;
          editionType =
              licensesWithSummaryDTO.getEdition() != null ? licensesWithSummaryDTO.getEdition().toString() : null;
        }
      } catch (Exception e) {
        log.error("Failed to retrieve licensing information", e);
      }

      if (executionSource != null && executionTriggerInfo.getTriggerType() == TriggerType.WEBHOOK) {
        WebhookExecutionSource webhookExecutionSource = (WebhookExecutionSource) executionSource;
        CIWebhookInfoDTO ciWebhookInfoDTO = CIModuleInfoMapper.getCIBuildResponseDTO(executionSource);
        OptionalSweepingOutput optionalSweepingOutput =
            executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
        CodebaseSweepingOutput codebaseSweepingOutput = null;
        triggerRepoName = fetchTriggerRepo(webhookExecutionSource);
        if (ciWebhookInfoDTO.getEvent().equals("branch")) {
          triggerCommits = ciWebhookInfoDTO.getBranch().getCommits();
        } else if (ciWebhookInfoDTO.getEvent().equals("PR")) {
          triggerCommits = ciWebhookInfoDTO.getPullRequest().getCommits();
        }
        if (optionalSweepingOutput.isFound()) {
          codebaseSweepingOutput = (CodebaseSweepingOutput) optionalSweepingOutput.getOutput();
          ciWebhookInfoDTO =
              getCiExecutionInfoDTO(codebaseSweepingOutput, ciWebhookInfoDTO.getAuthor(), prNumber, triggerCommits);
        }

        author = ciWebhookInfoDTO.getAuthor();

        if (IntegrationStageUtils.isURLSame(webhookExecutionSource, url) && isNotEmpty(prNumber)) {
          return CIPipelineModuleInfo.builder()
              .triggerRepoName(triggerRepoName)
              .branch(branch)
              .tag(tag)
              .buildType(buildType)
              .prNumber(prNumber)
              .repoName(repoName)
              .ciExecutionInfoDTO(ciWebhookInfoDTO)
              .isPrivateRepo(isPrivateRepo)
              .scmDetailsList(scmDetailsList)
              .infraDetailsList(infraDetailsList)
              .imageDetailsList(imageDetailsList)
              .tiBuildDetailsList(tiBuildDetailsList)
              .ciLicenseType(licenseType)
              .ciEditionType(editionType)
              .build();
        }
      }

      // get codebase sweeping output
      OptionalSweepingOutput optionalSweepingOutput =
          executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
      CodebaseSweepingOutput codebaseSweepingOutput = null;
      if (optionalSweepingOutput.isFound()) {
        codebaseSweepingOutput = (CodebaseSweepingOutput) optionalSweepingOutput.getOutput();
      }
      if (codebaseSweepingOutput != null) {
        log.info("Codebase sweeping output {}", codebaseSweepingOutput);

        if (isEmpty(branch)) {
          branch = codebaseSweepingOutput.getBranch();
        }

        if (isEmpty(prNumber)) {
          prNumber = codebaseSweepingOutput.getPrNumber();
        }

        if (isEmpty(tag)) {
          tag = codebaseSweepingOutput.getTag();
        }

        if (isEmpty(repoName) && isNotEmpty(codebaseSweepingOutput.getRepoUrl())) {
          repoName = getGitRepo(codebaseSweepingOutput.getRepoUrl());
        }

        // This author will be consumed by license. It should only be fulfilled if ID and User both exist.
        // Both fields should be populated by SCM if it's a valid git repo
        if (author == null && isNotEmpty(codebaseSweepingOutput.getGitUserId())
            && isNotEmpty(codebaseSweepingOutput.getGitUser())) {
          author = CIBuildAuthor.builder()
                       .id(codebaseSweepingOutput.getGitUserId())
                       .name(codebaseSweepingOutput.getGitUser())
                       .avatar(Optional.ofNullable(codebaseSweepingOutput.getGitUserAvatar()).orElse(""))
                       .email(Optional.ofNullable(codebaseSweepingOutput.getGitUserEmail()).orElse(""))
                       .build();
        }
      }

      return CIPipelineModuleInfo.builder()
          .branch(branch)
          .triggerRepoName(triggerRepoName)
          .prNumber(prNumber)
          .buildType(buildType)
          .tag(tag)
          .repoName(repoName)
          .ciExecutionInfoDTO(getCiExecutionInfoDTO(codebaseSweepingOutput, author, prNumber, triggerCommits))
          .isPrivateRepo(isPrivateRepo)
          .scmDetailsList(scmDetailsList)
          .infraDetailsList(infraDetailsList)
          .imageDetailsList(imageDetailsList)
          .tiBuildDetailsList(tiBuildDetailsList)
          .ciLicenseType(licenseType)
          .ciEditionType(editionType)
          .build();
    } else if (currentStepType != null
        && (Objects.equals(currentStepType.getType(), IntegrationStageStepPMS.STEP_TYPE.getType())
            || Objects.equals(currentStepType.getType(), IDPStageStepPMSType)
            || Objects.equals(currentStepType.getType(), IACMIntegrationStageStepPMSType)
            || Objects.equals(currentStepType.getType(), SecurityStageStepPMS.STEP_TYPE.getType()))) {
      CIPipelineModuleInfoBuilder builder = CIPipelineModuleInfo.builder();

      CIStageOptimizationState ciStageOptimizationState = null;
      Long baselineMs = null;
      try {
        ciStageOptimizationState = getStageOptimizationState(event, currentStepType.getType());
        baselineMs = getPipelineBaseline(event, currentStepType.getType());
      } catch (Exception ex) {
        log.error("Error while determining stage optimization state and baseline", ex);
      }
      if (ciStageOptimizationState != null) {
        List<CIStageOptimizationState> optimizationStateList = new ArrayList<>();
        optimizationStateList.add(ciStageOptimizationState);
        builder.ciStageOptimizationStateList(optimizationStateList);
      }
      if (baselineMs != null) {
        builder.baselineMs(baselineMs);
      }
      CIPipelineStageModuleInfo ciPipelineStageModuleInfo =
          getCIPipelineStageLevelInfo(event, ciStageOptimizationState, currentStepType.getType());
      publishLicenseUsageDetails(currentStepType, event, ciPipelineStageModuleInfo);
      publishBillingEvent(currentStepType, event, ciPipelineStageModuleInfo);

      // Save all pipeline stages from the service (persists current stage)
      if (StatusUtils.isFinalStatus(event.getStatus())) {
        pipelineModuleInfoService.saveStageModuleInfo(event, ciPipelineStageModuleInfo, currentStepType);
      }

      CIPipelineModuleInfoBuilder resultBuilder = builder.ciPipelineStageModuleInfo(ciPipelineStageModuleInfo);
      if (StatusUtils.isFinalStatus(event.getStatus())) {
        UnifiedPipelineExecutionModuleInfo ciArtifactsModuleInfo =
            unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(event, outcomeService);
        if (ciArtifactsModuleInfo != null) {
          resultBuilder.unifiedPipelineExecutionModuleInfo(ciArtifactsModuleInfo);
        }
      }
      return resultBuilder.build();
    }
    // Handle UnifiedServiceStep for CD info at pipeline level
    else if (unifiedModuleInfoHelper.isUnifiedServiceNodeAndCompleted(currentStepType, event.getStatus())) {
      UnifiedPipelineExecutionModuleInfo cdInfo = null;
      try {
        cdInfo = unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromServiceStep(event);
      } catch (Exception ex) {
        log.warn("Failed to build unified pipeline module info from service step", ex);
      }
      return CIPipelineModuleInfo.builder()
          .unifiedPipelineExecutionModuleInfo(
              cdInfo != null ? cdInfo : UnifiedPipelineExecutionModuleInfo.builder().build())
          .build();
    }
    // Handle UnifiedCDInfraStep for CD info at pipeline level
    else if (unifiedModuleInfoHelper.isUnifiedInfraNodeAndCompleted(currentStepType, event.getStatus())) {
      UnifiedPipelineExecutionModuleInfo cdInfo = null;
      try {
        cdInfo = unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromInfraStep(event);
      } catch (Exception ex) {
        log.warn("Failed to build unified pipeline module info from infra step", ex);
      }
      return CIPipelineModuleInfo.builder()
          .unifiedPipelineExecutionModuleInfo(
              cdInfo != null ? cdInfo : UnifiedPipelineExecutionModuleInfo.builder().build())
          .build();
    }

    return null;
  }

  private CIWebhookInfoDTO getCiExecutionInfoDTO(CodebaseSweepingOutput codebaseSweepingOutput,
      CIBuildAuthor ciBuildAuthor, String prNumber, List<CIBuildCommit> triggerCommits) {
    if (codebaseSweepingOutput == null) {
      return null;
    }

    List<CIBuildCommit> ciBuildCommits = new ArrayList<>();
    if (isNotEmpty(codebaseSweepingOutput.getCommits())) {
      for (CodebaseSweepingOutput.CodeBaseCommit commit : codebaseSweepingOutput.getCommits()) {
        ciBuildCommits.add(CIBuildCommit.builder()
                               .id(commit.getId())
                               .link(commit.getLink())
                               .message(commit.getMessage())
                               .ownerEmail(commit.getOwnerEmail())
                               .ownerId(commit.getOwnerId())
                               .ownerName(commit.getOwnerName())
                               .timeStamp(commit.getTimeStamp() * 1000)
                               .build());
      }
    }

    if (!displayTriggerCommits(ciBuildCommits, triggerCommits)) {
      triggerCommits = null;
    }

    String userSource = ciBuildAuthor != null && isNotEmpty(ciBuildAuthor.getId()) ? CIExecutionConstants.SOURCE_GIT
                                                                                   : CIExecutionConstants.SOURCE_MANUAL;

    if (isNotEmpty(prNumber)) {
      return CIWebhookInfoDTO.builder()
          .event("pullRequest")
          .author(ciBuildAuthor)
          .userSource(userSource)
          .pullRequest(CIBuildPRHook.builder()
                           .id(Long.valueOf(codebaseSweepingOutput.getPrNumber()))
                           .link(codebaseSweepingOutput.getPullRequestLink())
                           .title(codebaseSweepingOutput.getPrTitle())
                           .body(codebaseSweepingOutput.getPullRequestBody())
                           .sourceBranch(codebaseSweepingOutput.getSourceBranch())
                           .targetBranch(codebaseSweepingOutput.getTargetBranch())
                           .state(codebaseSweepingOutput.getState())
                           .commits(ciBuildCommits)
                           .triggerCommits(triggerCommits)
                           .build())
          .build();
    } else {
      return CIWebhookInfoDTO.builder()
          .event("branch")
          .userSource(userSource)
          .author(ciBuildAuthor)
          .branch(CIBuildBranchHook.builder().commits(ciBuildCommits).triggerCommits(triggerCommits).build())
          .build();
    }
  }

  public String fetchTriggerRepo(WebhookExecutionSource webhookExecutionSource) {
    if (webhookExecutionSource.getWebhookEvent().getType() == RELEASE) {
      ReleaseWebhookEvent releaseWebhookEvent = (ReleaseWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (releaseWebhookEvent == null || releaseWebhookEvent.getRepository() == null
          || releaseWebhookEvent.getRepository().getHttpURL() == null) {
        return null;
      }

      return getGitRepo(releaseWebhookEvent.getRepository().getHttpURL());

    } else if (webhookExecutionSource.getWebhookEvent().getType() == PR) {
      PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (prWebhookEvent == null || prWebhookEvent.getRepository() == null
          || prWebhookEvent.getRepository().getHttpURL() == null) {
        return null;
      }

      return getGitRepo(prWebhookEvent.getRepository().getHttpURL());

    } else if (webhookExecutionSource.getWebhookEvent().getType() == BRANCH) {
      BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (branchWebhookEvent == null || branchWebhookEvent.getRepository() == null
          || branchWebhookEvent.getRepository().getHttpURL() == null) {
        return null;
      }

      return getGitRepo(branchWebhookEvent.getRepository().getHttpURL());
    } else if (webhookExecutionSource.getWebhookEvent().getType() == DELETE) {
      DeleteWebhookEvent deleteWebhookEvent = (DeleteWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (deleteWebhookEvent == null || deleteWebhookEvent.getRepository() == null
          || deleteWebhookEvent.getRepository().getHttpURL() == null) {
        return null;
      }
    }

    return null;
  }

  public boolean displayTriggerCommits(List<CIBuildCommit> buildCommits, List<CIBuildCommit> triggerCommits) {
    if (isNotEmpty(triggerCommits) && isNotEmpty(buildCommits)) {
      return !buildCommits.stream()
                  .map(CIBuildCommit::getId)
                  .collect(Collectors.toSet())
                  .containsAll(triggerCommits.stream().map(CIBuildCommit::getId).collect(Collectors.toSet()));
    }

    return true;
  }

  private Long getCIStageTimeSaved(
      OrchestrationEvent event, CIStageOptimizationState ciStageOptimizationState, String stepType, long timeTakenMs) {
    Ambiance ambiance = event.getAmbiance();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String pipelineId = AmbianceUtils.getPipelineIdentifier(ambiance);
    String stageId = AmbianceUtils.getStageIdentifierFromAmbiance(ambiance);
    BaseNGAccess baseNGAccess = retrieveBaseNGAccess(ambiance);
    String orgId = baseNGAccess.getOrgIdentifier();
    String projectId = baseNGAccess.getProjectIdentifier();
    String parentUniqueId = baseNGAccess.getParentUniqueId();

    if (ciStageOptimizationState == null) {
      return null;
    }
    String state = ciStageOptimizationState.getState();
    // Get timesaved and update baseline only if optimization is not disabled
    if (OPTIMIZATION_STATE_DISABLED.equals(state)) {
      return null;
    }

    Long curBaselineMs = getStageBaseline(accountId, orgId, projectId, pipelineId, stageId, stepType, parentUniqueId);
    boolean baselineFound = curBaselineMs != null;
    long timeSavedMs = 0;

    Long newBaselineMs = null;
    if (OPTIMIZATION_STATE_FULL_RUN.equals(state)) {
      // Overwrite stage baseline, timeSavedMs = 0
      newBaselineMs = timeTakenMs;
    } else if (OPTIMIZATION_STATE_OPTIMIZED.equals(state)) {
      // If baseline found:
      //    update timesaved if less than current baseline
      //    update baseline if timesaved greater than current baseline
      // If baseline not found:
      //    overwrite stage baseline, timeSavedMs = 0
      if (baselineFound) {
        if (timeTakenMs <= curBaselineMs) {
          timeSavedMs = curBaselineMs - timeTakenMs;
        } else {
          newBaselineMs = timeTakenMs;
        }
      } else {
        newBaselineMs = timeTakenMs;
      }
    }
    // Upsert new baseline if available
    if (newBaselineMs != null) {
      if (isEmpty(parentUniqueId) && scopeInfoHelper != null) {
        parentUniqueId = scopeInfoHelper.getParentUniqueId(accountId, orgId, projectId);
      }
      ciStageBaselineRespository.upsert(
          accountId, orgId, projectId, pipelineId, stageId, parentUniqueId, newBaselineMs);
    }

    return timeSavedMs;
  }

  private CIPipelineStageModuleInfo getCIPipelineStageLevelInfo(
      OrchestrationEvent event, CIStageOptimizationState ciStageOptimizationState, String stepType) {
    Ambiance ambiance = event.getAmbiance();
    long startTime = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();
    String planExecutionId = ambiance.getPlanExecutionId();
    String stageId;
    String stageName;
    String osType;
    String osArch;
    String resourceClass = "";
    long initialiseBuildTime = 0;
    long totalStageBuildTime = 0;
    double buildMultiplier = 1;
    Long queueTimeMs = null;
    String runTimeId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Optional<StepExecutionParameters> stepExecutionParameters =
        stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(accountId, runTimeId);
    StepParameters stepParameters = null;
    if (stepExecutionParameters.isPresent()) {
      try {
        StepExecutionParameters executionParameters = stepExecutionParameters.get();
        stepParameters =
            RecastOrchestrationUtils.fromJson(executionParameters.getStepParameters(), StepParameters.class);
      } catch (Exception ex) {
        log.error("Error in deserialization", ex);
        stepParameters = event.getResolvedStepParameters();
      }
    } else {
      if (event.getResolvedStepParameters() != null) {
        log.warn("Step Execution Parameters are not present so using resolvedStepParameters from OrchestrationEvent");
        stepParameters = event.getResolvedStepParameters();
      }
    }
    if (stepParameters != null) {
      StageBaseParameters stageElementParameters = (StageBaseParameters) stepParameters;
      if (stageElementParameters != null) {
        stageId = stageElementParameters.getIdentifier();
        stageName = stageElementParameters.getName();
        IntegrationStageStepParametersPMS integrationStageStepParametersPMS =
            (IntegrationStageStepParametersPMS) stageElementParameters.getSpecConfig();
        if (integrationStageStepParametersPMS != null) {
          Infrastructure infrastructure = integrationStageStepParametersPMS.getInfrastructure();
          CIInfraDetails ciInfraDetails = IntegrationStageUtils.getCiInfraDetails(infrastructure);
          osType = ciInfraDetails.getInfraOSType();
          osArch = ciInfraDetails.getInfraArchType();

          OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
              ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION));
          if (optionalSweepingOutput.isFound()) {
            initialiseBuildTime =
                ((InitializeExecutionSweepingOutput) optionalSweepingOutput.getOutput()).getInitialiseExecutionTime();
          }

          optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
              ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION));
          if (optionalSweepingOutput.isFound()) {
            totalStageBuildTime =
                ((StageExecutionSweepingOutput) optionalSweepingOutput.getOutput()).getStageExecutionTime();
          } else if (StatusUtils.isFinalStatus(event.getStatus())) {
            // for aborted pipelines, STAGE_EXECUTION sweeping output would not be present so overriding it here.
            totalStageBuildTime = System.currentTimeMillis() - AmbianceUtils.getCurrentLevelStartTs(ambiance);
          }

          optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
              ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_QUEUE_TIME));
          if (optionalSweepingOutput != null && optionalSweepingOutput.isFound()) {
            queueTimeMs = ((StageQueueExecutionSweepingOutput) optionalSweepingOutput.getOutput()).getQueueTimeMs();
          }

          if (infrastructure.getType() == Infrastructure.Type.HOSTED_VM) {
            resourceClass = CIResourceClass.FLEX.toString();
            optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
                ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS));
            if (optionalSweepingOutput != null && optionalSweepingOutput.isFound()) {
              DliteVmStageInfraDetails dliteVmStageInfraDetails =
                  (DliteVmStageInfraDetails) optionalSweepingOutput.getOutput();
              if (isNotEmpty(dliteVmStageInfraDetails.getResourceClass())) {
                resourceClass = dliteVmStageInfraDetails.getResourceClass();
              }
            }
            buildMultiplier = ciLicenseUsageUtils.getBuilderMultiplier(accountId, resourceClass, osType, osArch);
          }

          // Get codebase info for commit and repo
          String commitId = null;
          String repoName = null;
          String branch = null;
          String sourceBranch = null;
          String tag = null;
          String codebaseBuildType = null;
          String prNumber = null;
          String repoUrl = null;
          String commitMessage = null;
          String prTitle = null;
          try {
            OptionalSweepingOutput codebaseOutput =
                executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
            if (codebaseOutput.isFound()) {
              CodebaseSweepingOutput codebaseSweepingOutput = (CodebaseSweepingOutput) codebaseOutput.getOutput();
              commitId = codebaseSweepingOutput.getCommitSha();
              repoUrl = codebaseSweepingOutput.getRepoUrl();
              if (isNotEmpty(repoUrl)) {
                if (repoUrl.endsWith(".git")) {
                  repoUrl = repoUrl.substring(0, repoUrl.length() - 4);
                }
                repoName = getGitRepo(repoUrl);
              }
              branch = codebaseSweepingOutput.getBranch();
              sourceBranch = codebaseSweepingOutput.getSourceBranch();
              tag = codebaseSweepingOutput.getTag();
              prNumber = codebaseSweepingOutput.getPrNumber();
              commitMessage = codebaseSweepingOutput.getCommitMessage();
              prTitle = codebaseSweepingOutput.getPrTitle();
              if (codebaseSweepingOutput.getBuild() != null) {
                codebaseBuildType = codebaseSweepingOutput.getBuild().getType();
              }
            }
          } catch (Exception ex) {
            log.debug("Unable to retrieve codebase info for commit and repo", ex);
          }

          CIPipelineStageModuleInfoBuilder builder =
              CIPipelineStageModuleInfo.builder()
                  .stageExecutionId(stageExecutionId)
                  .stageId(stageId)
                  .stageName(stageName)
                  .status(event.getStatus() != null ? event.getStatus().name() : null)
                  .infraType(infrastructure.getType().getYamlName())
                  .osType(osType)
                  .osArch(osArch)
                  .cpuTime((totalStageBuildTime > 0 && initialiseBuildTime > 0)
                          ? totalStageBuildTime - initialiseBuildTime
                          : 0)
                  .stageBuildTime(totalStageBuildTime)
                  .startTs(startTime)
                  .buildMultiplier(buildMultiplier)
                  .resourceClass(resourceClass)
                  .commitId(commitId)
                  .repoName(repoName)
                  .queueTimeMs(queueTimeMs)
                  .branch(branch)
                  .sourceBranch(sourceBranch)
                  .tag(tag)
                  .buildType(codebaseBuildType)
                  .prNumber(prNumber)
                  .repoUrl(repoUrl)
                  .commitMessage(commitMessage)
                  .prTitle(prTitle);
          try {
            Long timeSavedMs = getCIStageTimeSaved(event, ciStageOptimizationState, stepType, totalStageBuildTime);
            if (ciStageOptimizationState != null && timeSavedMs != null) {
              String state = ciStageOptimizationState.getState();
              // Set time saved and optimization state in the builder
              builder.timeSaved(timeSavedMs).optimizationState(state);
              ciStageSavingsInfoRepository.upsert(accountId, planExecutionId, stageExecutionId, state, timeSavedMs);
            }
          } catch (Exception ex) {
            log.error("Error while determining stage time saved and stage baseline", ex);
          }
          return builder.build();
        }
      }
    }
    return null;
  }

  @Override
  public StageModuleInfo getStageLevelModuleInfo(OrchestrationEvent event) {
    return CIStageModuleInfo.builder().build();
  }

  private ExecutionSource getWebhookExecutionSource(
      ExecutionMetadata executionMetadata, TriggerPayload triggerPayload) {
    ExecutionTriggerInfo executionTriggerInfo = executionMetadata.getTriggerInfo();
    if (executionTriggerInfo.getTriggerType() == TriggerType.WEBHOOK) {
      if (triggerPayload != null) {
        ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
        return WebhookTriggerProcessorUtils.convertWebhookResponse(parsedPayload);
      } else {
        throw new CIStageExecutionException("Parsed payload is empty for webhook execution");
      }
    }
    return null;
  }

  private boolean isWhitelistedNode(StepType stepType) {
    boolean isUnifiedStep = unifiedModuleInfoHelper.isUnifiedServiceStepType(stepType)
        || unifiedModuleInfoHelper.isUnifiedInfraStepType(stepType);
    return Objects.equals(stepType.getType(), InitializeTaskStep.STEP_TYPE.getType())
        || Objects.equals(stepType.getType(), IntegrationStageStepPMS.STEP_TYPE.getType())
        || Objects.equals(stepType.getType(), IDPStageStepPMSType)
        || Objects.equals(stepType.getType(), SecurityStageStepPMS.STEP_TYPE.getType())
        || Objects.equals(stepType.getType(), IACMIntegrationStageStepPMSType) || isUnifiedStep;
  }

  private BaseNGAccess retrieveBaseNGAccess(Ambiance ambiance) {
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String parentUniqueId = AmbianceUtils.getParentUniqueIdentifier(ambiance);

    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .parentUniqueId(parentUniqueId)
        .build();
  }

  private Long getPipelineBaseline(OrchestrationEvent event, String stepType) {
    if (!Objects.equals(stepType, IntegrationStageStepPMS.STEP_TYPE.getType())) {
      return null;
    }
    Ambiance ambiance = event.getAmbiance();
    BaseNGAccess baseNGAccess = retrieveBaseNGAccess(ambiance);
    String pipelineId = AmbianceUtils.getPipelineIdentifier(ambiance);
    CIPipelineBaseline ciPipelineBaseline = null;
    if (shouldUseParentUniqueIdQuery(baseNGAccess.getParentUniqueId(), baseNGAccess.getAccountIdentifier())) {
      ciPipelineBaseline =
          ciPipelineBaselineRespository.findByParentUniqueIdAndPipelineId(baseNGAccess.getParentUniqueId(), pipelineId);
    } else {
      ciPipelineBaseline = ciPipelineBaselineRespository.findByAccountIdAndOrgIdAndProjectIdAndPipelineId(
          baseNGAccess.getAccountIdentifier(), baseNGAccess.getOrgIdentifier(), baseNGAccess.getProjectIdentifier(),
          pipelineId);
    }
    if (ciPipelineBaseline == null) {
      return null;
    }
    return ciPipelineBaseline.getBaselineMs();
  }

  private Long getStageBaseline(String accountId, String orgId, String projectId, String pipelineId, String stageId,
      String stepType, String parentUniqueId) {
    if (!Objects.equals(stepType, IntegrationStageStepPMS.STEP_TYPE.getType())) {
      return null;
    }
    CIStageBaseline ciStageBaseline = null;
    if (shouldUseParentUniqueIdQuery(parentUniqueId, accountId)) {
      ciStageBaseline =
          ciStageBaselineRespository.findByParentUniqueIdAndPipelineIdAndStageId(parentUniqueId, pipelineId, stageId);
    } else {
      ciStageBaseline = ciStageBaselineRespository.findByAccountIdAndOrgIdAndProjectIdAndPipelineIdAndStageId(
          accountId, orgId, projectId, pipelineId, stageId);
    }
    if (ciStageBaseline == null) {
      return null;
    }
    return ciStageBaseline.getBaselineMs();
  }

  private CIStageOptimizationState getStageOptimizationState(OrchestrationEvent event, String stepType) {
    if (!Objects.equals(stepType, IntegrationStageStepPMS.STEP_TYPE.getType())) {
      return null;
    }
    Ambiance ambiance = event.getAmbiance();
    BaseNGAccess baseNGAccess = retrieveBaseNGAccess(ambiance);
    String pipelineId = AmbianceUtils.getPipelineIdentifier(ambiance);
    String stageId = getStageIdFromEvent(event);
    if (isEmpty(pipelineId) || isEmpty(stageId)) {
      return null;
    }
    // donot set optimization state for failed executions.
    if (isFailedStatus(event.getStatus())) {
      return null;
    }
    String planExecutionId = ambiance.getPlanExecutionId();
    List<CIStepOptimizationState> ciStepOptimizationStateList = new ArrayList<>();
    if (shouldUseParentUniqueIdQuery(baseNGAccess.getParentUniqueId(), baseNGAccess.getAccountIdentifier())) {
      ciStepOptimizationStateList =
          ciStepOptimizationStateRepository.findByParentUniqueIdAndPipelineIdAndStageIdAndPlanExecutionId(
              baseNGAccess.getParentUniqueId(), pipelineId, stageId, planExecutionId);
    } else {
      ciStepOptimizationStateList = ciStepOptimizationStateRepository
                                        .findByAccountIdAndOrgIdAndProjectIdAndPipelineIdAndStageIdAndPlanExecutionId(
                                            baseNGAccess.getAccountIdentifier(), baseNGAccess.getOrgIdentifier(),
                                            baseNGAccess.getProjectIdentifier(), pipelineId, stageId, planExecutionId);
    }
    String state = getOptimizationStateFromSteps(ciStepOptimizationStateList);
    if (isEmpty(state)) {
      return null;
    }
    return CIStageOptimizationState.builder().state(state).identifier(stageId).build();
  }

  private String getOptimizationStateFromSteps(List<CIStepOptimizationState> ciStepOptimizationStateList) {
    if (isEmpty(ciStepOptimizationStateList)) {
      return null;
    }
    String state = null;
    for (CIStepOptimizationState ciStepOptimizationState : ciStepOptimizationStateList) {
      if (OPTIMIZATION_STATE_OPTIMIZED.equals(ciStepOptimizationState.getState())) {
        return OPTIMIZATION_STATE_OPTIMIZED;
      } else if (OPTIMIZATION_STATE_FULL_RUN.equals(ciStepOptimizationState.getState())) {
        state = OPTIMIZATION_STATE_FULL_RUN;
      }
    }
    return state;
  }

  private boolean shouldUseParentUniqueIdQuery(String parentUniqueId, String accountId) {
    return !isEmpty(parentUniqueId) && featureFlagService.isEnabled(CI_USE_UNIQUE_PARENT_ID_FOR_QUERY, accountId);
  }

  private String getStageIdFromEvent(OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();

    String runTimeId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Optional<StepExecutionParameters> stepExecutionParameters =
        stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(accountId, runTimeId);
    StepParameters stepParameters = null;
    if (stepExecutionParameters.isPresent()) {
      try {
        StepExecutionParameters executionParameters = stepExecutionParameters.get();
        stepParameters =
            RecastOrchestrationUtils.fromJson(executionParameters.getStepParameters(), StepParameters.class);
      } catch (Exception ex) {
        log.error("Error in deserialization", ex);
        stepParameters = event.getResolvedStepParameters();
      }
    } else {
      if (event.getResolvedStepParameters() != null) {
        log.warn("Step Execution Parameters are not present so using resolvedStepParameters from OrchestrationEvent");
        stepParameters = event.getResolvedStepParameters();
      }
    }
    if (stepParameters != null) {
      StageBaseParameters stageElementParameters = (StageBaseParameters) stepParameters;
      return stageElementParameters.getIdentifier();
    }
    return null;
  }

  private boolean shouldPublishLicenseUsageDetails(StepType stepType) {
    return Objects.equals(stepType.getType(), IntegrationStageStepPMSType)
        || Objects.equals(stepType.getType(), IACMIntegrationStageStepPMSType)
        || Objects.equals(stepType.getType(), IDPStageStepPMSType)
        || Objects.equals(stepType.getType(), SecurityStageStepPMS.STEP_TYPE.getType());
  }

  private void publishLicenseUsageDetails(
      StepType currentStepType, OrchestrationEvent event, CIPipelineStageModuleInfo stageModuleInfo) {
    if (shouldPublishLicenseUsageDetails(currentStepType) && licenseUsageProducer != null && stageModuleInfo != null
        && StatusUtils.isFinalStatus(event.getStatus())
        && featureFlagService.isEnabled(
            PL_ENABLE_LICENSE_USAGE_COMPUTE, AmbianceUtils.getAccountId(event.getAmbiance()))) {
      try {
        Ambiance ambiance = event.getAmbiance();
        Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
        ArchitectureType architectureType = ciLicenseUsageUtils.getArchitectureType(stageModuleInfo.getOsArch());
        OSType osType = ciLicenseUsageUtils.getOSType(stageModuleInfo.getOsType());
        BuildInfraType buildInfraType = ciLicenseUsageUtils.getBuildInfraType(stageModuleInfo.getInfraType());

        // platform developer info
        Developer platformDeveloper = ciLicenseUsageUtils.getPlatformDeveloper(ambiance);
        Builder licenseUsageEventBuilder =
            LicenseUsageEvent.newBuilder()
                .setAccountIdentifier(AmbianceUtils.getAccountId(ambiance))
                .setProjectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
                .setOrgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
                .setParentUniqueId(emptyIfNull(AmbianceUtils.getParentUniqueIdentifier(ambiance)))
                .setStageIdentifier(level.getIdentifier())
                .setPipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
                .setDeveloper(platformDeveloper)
                .setCreatedAtTimestamp(System.currentTimeMillis());
        List<Developer> developers = ciLicenseUsageUtils.getDevelopers(ambiance);

        CILicenseUsageData licenseUsageData =
            CILicenseUsageData.newBuilder()
                .setArchType(architectureType)
                .setOsType(osType)
                .setBuildInfraType(buildInfraType)
                .setBuildMinutes(DateTimeUtils.roundToNearestMinute(stageModuleInfo.getCpuTime()))
                .setResourceClass(ciLicenseUsageUtils.getResourceClass(stageModuleInfo.getResourceClass()))
                .setLastBuildTimestamp(level.getStartTs())
                .addAllDevelopers(developers)
                .build();
        licenseUsageEventBuilder.setCiLicenseUsageData(licenseUsageData);
        switch (currentStepType.getType()) {
                    case IntegrationStageStepPMSType ->
                            licenseUsageEventBuilder.setModuleName(ModuleName.MODULE_NAME_CI);

                    case IACMIntegrationStageStepPMSType ->
                            licenseUsageEventBuilder.setModuleName(ModuleName.MODULE_NAME_IACM);

                    case IDPStageStepPMSType -> licenseUsageEventBuilder.setModuleName(ModuleName.MODULE_NAME_IDP);

                    case SecurityStageStepPMSType -> licenseUsageEventBuilder.setModuleName(ModuleName.MODULE_NAME_STO);

                    default -> {
                        return;
                    }
                }
                licenseUsageProducer.send(Message.newBuilder()
                        .putAllMetadata(Map.of("accountId", AmbianceUtils.getAccountId(ambiance)))
                        .setData(licenseUsageEventBuilder.build().toByteString())
                        .build());
                log.debug("Successfully published license usage details event");
            } catch (Exception ex) {
                log.warn("Unable to publish message for license usage", ex.getMessage());
            }
        }
    }

  private void publishBillingEvent(
      StepType currentStepType, OrchestrationEvent event, CIPipelineStageModuleInfo stageModuleInfo) {
    // publish for hosted builds
    if (shouldPublishLicenseUsageDetails(currentStepType) && ciBillingEventService != null && stageModuleInfo != null
        && StatusUtils.isFinalStatus(event.getStatus()) && isCloudBuild(stageModuleInfo)) {
      try {
        Ambiance ambiance = event.getAmbiance();
        Level level = AmbianceUtils.obtainCurrentLevel(ambiance);

        // Extract identifiers matching license usage event structure
        String accountId = AmbianceUtils.getAccountId(ambiance);
        String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
        String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
        String pipelineId = AmbianceUtils.getPipelineIdentifier(ambiance);
        String stageIdentifier = level.getIdentifier();
        String stageExecutionId = ambiance.getStageExecutionId();
        String planExecutionId = ambiance.getPlanExecutionId();
        String parentUniqueId = emptyIfNull(AmbianceUtils.getParentUniqueIdentifier(ambiance));

        // rounding off before converting it to harness cost
        long cpuTimeMs = stageModuleInfo.getCpuTime();
        int buildMinutesRounded = DateTimeUtils.roundToNearestMinute(cpuTimeMs);
        double flexPricingMultiplier = ciLicenseUsageUtils.getFlexPricingMultiplier(
            accountId, stageModuleInfo.getResourceClass(), stageModuleInfo.getOsType(), stageModuleInfo.getOsArch());
        double buildMinutes = buildMinutesRounded * flexPricingMultiplier;

        // Event timestamp: when the stage completed
        long eventTimestampMs = System.currentTimeMillis();
        Timestamp eventTimestamp = ProtoUtils.unixMillisToTimestamp(eventTimestampMs);

        // Generate unique idempotency key
        String idempotencyKey = planExecutionId + "_" + stageExecutionId;

        // Build the billing event with proper field mapping
        BillingEvent.Builder billingEventBuilder = BillingEvent.newBuilder()
            .setIdempotencyKey(idempotencyKey)
            .setAccountId(accountId)
            .setMetric(BillingMetric.PLATFORM_HOSTED_BUILD_MINUTES)
            .setValue(buildMinutes)  // HLU consumption value (build minutes)
            .setEventTimestamp(eventTimestamp)
            .setResourceUniqueIdentifier(stageIdentifier != null ? stageIdentifier : "")
            .setResourceParentUniqueIdentifier(parentUniqueId);

                      // Add metadata as tags
                      // Scope identifiers
                      if (orgId != null) {
                        billingEventBuilder.putTags("orgIdentifier", orgId);
                      }
                      if (projectId != null) {
                        billingEventBuilder.putTags("projectIdentifier", projectId);
                      }
                      if (parentUniqueId != null) {
                        billingEventBuilder.putTags("parentUniqueId", parentUniqueId);
                      }

                      // Pipeline and stage identifiers
                      if (pipelineId != null) {
                        billingEventBuilder.putTags("pipelineIdentifier", pipelineId);
                      }
                      if (stageModuleInfo.getStageName() != null) {
                        billingEventBuilder.putTags("stageName", stageModuleInfo.getStageName());
                      }

                      // Execution identifiers
                      if (planExecutionId != null) {
                        billingEventBuilder.putTags("planExecutionId", planExecutionId);
                      }
                      if (stageExecutionId != null) {
                        billingEventBuilder.putTags("stageExecutionId", stageExecutionId);
                      }

                      // Infrastructure details
                      if (stageModuleInfo.getInfraType() != null) {
                        billingEventBuilder.putTags("infraType", stageModuleInfo.getInfraType());
                      }
                      if (stageModuleInfo.getOsType() != null) {
                        billingEventBuilder.putTags("osType", stageModuleInfo.getOsType());
                      }
                      if (stageModuleInfo.getOsArch() != null) {
                        billingEventBuilder.putTags("osArch", stageModuleInfo.getOsArch());
                      }
                      if (stageModuleInfo.getResourceClass() != null) {
                        billingEventBuilder.putTags("resourceClass", stageModuleInfo.getResourceClass());
                      }

                      // Timing and billing details
                      billingEventBuilder.putTags("cpuTimeMs", String.valueOf(cpuTimeMs));
                      billingEventBuilder.putTags(
                          "stageBuildTimeMs", String.valueOf(stageModuleInfo.getStageBuildTime()));
                      billingEventBuilder.putTags("createdAtTimestamp", String.valueOf(eventTimestampMs));

                      // Module type
                      String moduleType = getModuleType(currentStepType);
                      if (moduleType != null) {
                        billingEventBuilder.putTags("moduleType", moduleType);
                      }

                      // Developer info
                      Developer platformDeveloper = ciLicenseUsageUtils.getPlatformDeveloper(ambiance);
                      if (platformDeveloper != null) {
                        if (isNotEmpty(platformDeveloper.getEmail())) {
                          billingEventBuilder.putTags("developerEmail", platformDeveloper.getEmail());
                        }
                        if (isNotEmpty(platformDeveloper.getName())) {
                          billingEventBuilder.putTags("developerName", platformDeveloper.getName());
                        }
                      }

                      BillingEvent billingEvent = billingEventBuilder.build();

                      // Publish the event using CIBillingEventService
                      ciBillingEventService.publishBillingEventAsync(billingEvent);
                      log.debug("Successfully published billing event for accountId: {}, orgId: {}, projectId: {}, "
                              + "pipelineId: {}, "
                              + "stageId: {}, buildMinutes: {}",
                          accountId, orgId, projectId, pipelineId, stageIdentifier, buildMinutes);
        }
        catch (Exception ex) {
          log.warn("Unable to publish billing event", ex);
        }
      }
    }

    private boolean isCloudBuild(CIPipelineStageModuleInfo stageModuleInfo) {
      if (stageModuleInfo == null || stageModuleInfo.getInfraType() == null) {
        return false;
      }
      BuildInfraType buildInfraType = ciLicenseUsageUtils.getBuildInfraType(stageModuleInfo.getInfraType());
      return buildInfraType == BuildInfraType.BUILD_INFRA_TYPE_CLOUD;
    }

    private String getModuleType(StepType currentStepType) {
      String stepType = currentStepType.getType();
      if (IntegrationStageStepPMSType.equals(stepType)) {
        return "CI";
      } else if (IACMIntegrationStageStepPMSType.equals(stepType)) {
        return "IACM";
      } else if (IDPStageStepPMSType.equals(stepType)) {
        return "IDP";
      } else if (SecurityStageStepPMSType.equals(stepType)) {
        return "STO";
      }
      return null;
    }
  }

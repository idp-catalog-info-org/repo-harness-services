/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.step;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.shell.ShellScriptTaskNG.COMMAND_UNIT;
import static io.harness.steps.approval.step.harness.HarnessApprovalUtils.checkForNullOrThrowAutoApproval;
import static io.harness.steps.approval.step.harness.HarnessApprovalUtils.validateTimestampForAutoApproval;

import static java.util.Objects.isNull;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.engine.executions.step.StepExecutionEntityService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.step.HarnessApprovalStepExecutionDetails;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.AsyncTimeoutResponseData;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.approval.ApprovalNotificationHandler;
import io.harness.steps.approval.step.beans.ApprovalServiceAccountDTO;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalUserGroupDTO;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;
import io.harness.steps.approval.step.entities.ApprovalUtils;
import io.harness.steps.approval.step.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.harness.HarnessApprovalBaseOutcome;
import io.harness.steps.approval.step.harness.HarnessApprovalResponseData;
import io.harness.steps.approval.step.harness.HarnessApprovalSpecParameters;
import io.harness.steps.approval.step.harness.HarnessApprovalUtils;
import io.harness.steps.approval.step.harness.beans.ApproverInput;
import io.harness.steps.approval.step.harness.beans.AutoApprovalParams;
import io.harness.steps.approval.step.harness.beans.EmbeddedUserDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalBaseActivityDTO;
import io.harness.steps.approval.step.harness.beans.ScheduledDeadline;
import io.harness.steps.approval.step.harness.outcomes.HarnessApprovalStepOutcome;
import io.harness.steps.executables.PipelineAsyncExecutable;
import io.harness.tasks.ResponseData;
import io.harness.telemetry.helpers.ApprovalInstrumentationHelper;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.utils.TimeStampUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(CDC)
@Slf4j
public class HarnessApprovalStep extends PipelineAsyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE;
  public static final String HARNESS_APPROVAL_STEP_OUTCOME = "Harness_approval_step_outcome";
  public static final String TIMEOUT_DATA = "timeoutData";
  public static final String HARNESS = "Harness";
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private ApprovalInstanceService approvalInstanceService;
  @Inject private ApprovalNotificationHandler approvalNotificationHandler;
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject @Named("DashboardExecutorService") ExecutorService dashboardExecutorService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private StepExecutionEntityService stepExecutionEntityService;
  @Inject ApprovalInstrumentationHelper instrumentationHelper;
  @Inject ScopeResolutionHelper scopeResolutionHelper;

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);

    HarnessApprovalInstance approvalInstance = HarnessApprovalInstance.fromStepParameters(ambiance, stepParameters);
    Long streamTimeout = ApprovalUtils.getTimeoutInSeconds(approvalInstance);
    logStreamingStepClient.openStream(ShellScriptTaskNG.COMMAND_UNIT, streamTimeout);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(
        AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getParentUniqueIdentifier(ambiance));
    instrumentationHelper.sendApprovalEvent(approvalInstance, scopeInfo);
    final List<String> userGroups = approvalInstance.getApprovers().getUserGroups();
    final List<String> serviceAccounts = approvalInstance.getApprovers().getServiceAccounts();

    HarnessApprovalSpecParameters specParameters =
        HarnessApprovalUtils.getHarnessApprovalStepParameters(stepParameters);

    if (specParameters.getAutoApproval() != null) {
      checkForNullOrThrowAutoApproval(specParameters.getAutoApproval());
      validateTimestampForAutoApproval(specParameters);
    }
    final boolean isAnyValidUserGroupPresent = userGroups.stream().anyMatch(EmptyPredicate::isNotEmpty);
    final boolean isAnyValidServiceAccountPresent = serviceAccounts.stream().anyMatch(EmptyPredicate::isNotEmpty);
    if (!(isAnyValidUserGroupPresent || isAnyValidServiceAccountPresent)) {
      throw new InvalidRequestException("All the provided user groups and/or service accounts are empty");
    }

    List<UserGroupDTO> validatedUserGroups = approvalNotificationHandler.getUserGroups(approvalInstance);

    approvalInstance.setValidatedUserGroups(validatedUserGroups);
    approvalInstance.setValidatedApprovalUserGroups(
        validatedUserGroups.stream().map(ApprovalUserGroupDTO::toApprovalUserGroupDTO).collect(Collectors.toList()));

    List<ApprovalServiceAccountDTO> serviceAccountsList = getServiceAccountsList(serviceAccounts, ambiance);
    approvalInstance.setValidatedApprovalServiceAccounts(serviceAccountsList);

    HarnessApprovalInstance savedApprovalInstance =
        (HarnessApprovalInstance) approvalInstanceService.save(approvalInstance);
    executorService.submit(() -> approvalNotificationHandler.sendNotification(savedApprovalInstance, ambiance));

    sweepingOutputService.consume(ambiance, HARNESS_APPROVAL_STEP_OUTCOME,
        HarnessApprovalStepOutcome.builder().approvalInstanceId(approvalInstance.getId()).build(), "");

    AsyncExecutableResponse.Builder asyncExecutableResponseBuilder =
        AsyncExecutableResponse.newBuilder()
            .addCallbackIds(approvalInstance.getId())
            .addAllLogKeys(
                CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(StepUtils.generateLogAbstractions(ambiance),
                    Collections.singletonList(ShellScriptTaskNG.COMMAND_UNIT))));

    if (specParameters.getAutoApproval() != null) {
      asyncExecutableResponseBuilder.setTimeout(getTimeoutForAutoApproval(specParameters.getAutoApproval()));
    }

    return asyncExecutableResponseBuilder.build();
  }

  private List<ApprovalServiceAccountDTO> getServiceAccountsList(List<String> serviceAccounts, Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

    List<ApprovalServiceAccountDTO> serviceAccountDTOS = new ArrayList<>();
    int index;
    for (String scopedServiceAccountIdentifier : serviceAccounts) {
      ApprovalServiceAccountDTO serviceAccount = ApprovalServiceAccountDTO.builder().build();
      if (scopedServiceAccountIdentifier.startsWith(NGCommonEntityConstants.ACCOUNT_SCOPE_PREFIX)) {
        serviceAccount.setAccountIdentifier(accountId);
        index = scopedServiceAccountIdentifier.indexOf('.') + 1;
        serviceAccount.setIdentifier(scopedServiceAccountIdentifier.substring(index));
      } else if (scopedServiceAccountIdentifier.startsWith(NGCommonEntityConstants.ORG_SCOPE_PREFIX)) {
        serviceAccount.setAccountIdentifier(accountId);
        serviceAccount.setOrgIdentifier(orgId);
        index = scopedServiceAccountIdentifier.indexOf('.') + 1;
        serviceAccount.setIdentifier(scopedServiceAccountIdentifier.substring(index));
      } else {
        serviceAccount.setAccountIdentifier(accountId);
        serviceAccount.setOrgIdentifier(orgId);
        serviceAccount.setProjectIdentifier(projectId);
        serviceAccount.setIdentifier(scopedServiceAccountIdentifier);
      }
      serviceAccountDTOS.add(serviceAccount);
    }
    return serviceAccountDTOS;
  }

  private long getTimeoutForAutoApproval(AutoApprovalParams autoApprovalParams) {
    ScheduledDeadline scheduledDeadline = autoApprovalParams.getScheduledDeadline();

    long autoApprovalDuration = TimeStampUtils.getTotalDurationWRTCurrentTimeFromTimeStamp(
        scheduledDeadline.getTime().getValue(), scheduledDeadline.getTimeZone().getValue());

    if (autoApprovalDuration <= 0) {
      throw new InvalidRequestException("Auto approval deadline should be greater than current time");
    }
    return autoApprovalDuration;
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    try {
      if (responseDataMap.get(TIMEOUT_DATA) != null
          && responseDataMap.get(TIMEOUT_DATA) instanceof AsyncTimeoutResponseData) {
        // Auto approve the pipeline in this case, as the step is over schedule time provided for approval

        final OptionalSweepingOutput outputOptional = sweepingOutputService.resolveOptional(
            ambiance, RefObjectUtils.getSweepingOutputRefObject(HARNESS_APPROVAL_STEP_OUTCOME));
        if (!outputOptional.isFound()) {
          log.error(HARNESS_APPROVAL_STEP_OUTCOME + " sweeping output not found. unable to perform auto approval");
          FailureInfo failureInfo = FailureInfo.newBuilder().setErrorMessage("Step timeout occurred").build();
          try {
            stepExecutionEntityService.updateStepExecutionEntity(
                ambiance, failureInfo, null, stepParameters.getName(), Status.FAILED);
          } catch (Exception e) {
            log.error("Error while updating step execution entity for Harness Approval step", e);
          }
          return StepResponse.builder().status(Status.FAILED).failureInfo(failureInfo).build();
        }
        NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
        logCallback.saveExecutionLog(
            "Scheduled deadline for auto approval has reached. Marking the step as approved...");

        HarnessApprovalStepOutcome harnessApprovalStepOutcome = (HarnessApprovalStepOutcome) outputOptional.getOutput();
        String approvalInstanceId = harnessApprovalStepOutcome.getApprovalInstanceId();

        HarnessApprovalInstance instance = handleAutoApprovalForStep(approvalInstanceId, stepParameters);
        executorService.submit(() -> approvalNotificationHandler.sendNotification(instance, ambiance));

        logCallback.saveExecutionLog("Step auto approved.");
        HarnessApprovalBaseOutcome outcome = instance.toHarnessApprovalBaseOutcome();
        try {
          stepExecutionEntityService.updateStepExecutionEntity(ambiance, null,
              createHarnessApprovalStepExecutionDetailsFromHarnessApprovalOutcome(outcome), stepParameters.getName(),
              Status.SUCCEEDED);
        } catch (Exception e) {
          log.error("Error while updating step execution entity for Harness Approval step", e);
        }
        return StepResponse.builder()
            .status(Status.SUCCEEDED)
            .stepOutcome(StepResponse.StepOutcome.builder().name("output").outcome(outcome).build())
            .build();
      }
      HarnessApprovalResponseData responseData =
          (HarnessApprovalResponseData) responseDataMap.values().iterator().next();
      HarnessApprovalInstance instance =
          (HarnessApprovalInstance) approvalInstanceService.get(responseData.getApprovalInstanceId());

      if (ApprovalStatus.APPROVED.equals(instance.getStatus())
          || ApprovalStatus.REJECTED.equals(instance.getStatus())) {
        executorService.submit(() -> approvalNotificationHandler.sendNotification(instance, ambiance));
      }
      HarnessApprovalBaseOutcome outcome = instance.toHarnessApprovalBaseOutcome();
      try {
        stepExecutionEntityService.updateStepExecutionEntity(ambiance, instance.getFailureInfo(),
            createHarnessApprovalStepExecutionDetailsFromHarnessApprovalOutcome(outcome), stepParameters.getName(),
            Status.APPROVAL_WAITING);
      } catch (Exception e) {
        log.error("Error while updating step execution entity for Harness Approval step", e);
      }
      return StepResponse.builder()
          .status(instance.getStatus().toFinalExecutionStatus())
          .failureInfo(instance.getFailureInfo())
          .stepOutcome(StepResponse.StepOutcome.builder().name("output").outcome(outcome).build())
          .build();
    } finally {
      closeLogStream(ambiance);
    }
  }

  private HarnessApprovalStepExecutionDetails createHarnessApprovalStepExecutionDetailsFromHarnessApprovalOutcome(
      HarnessApprovalBaseOutcome outcome) {
    List<HarnessApprovalStepExecutionDetails.HarnessApprovalExecutionActivity> approvalActivities = new ArrayList<>();
    if (outcome != null && outcome.getApprovalActivities() != null) {
      for (HarnessApprovalBaseActivityDTO harnessApprovalActivityDTO : outcome.getApprovalActivities()) {
        String action = harnessApprovalActivityDTO.getAction().toString();
        Map<String, String> approverInputs = Collections.emptyMap();
        if (EmptyPredicate.isNotEmpty(harnessApprovalActivityDTO.getApproverInputs())) {
          approverInputs = harnessApprovalActivityDTO.getApproverInputs().stream().collect(
              Collectors.toMap(ApproverInput::getName, ApproverInput::getValue));
        }
        approvalActivities.add(HarnessApprovalStepExecutionDetails.HarnessApprovalExecutionActivity.builder()
                                   .user(EmbeddedUserDTO.toEmbeddedUser(harnessApprovalActivityDTO.getUser()))
                                   .approvalAction(action)
                                   .approverInputs(approverInputs)
                                   .comments(harnessApprovalActivityDTO.getComments())
                                   .approvedAt(harnessApprovalActivityDTO.getApprovedAt())
                                   .build());
      }
      return HarnessApprovalStepExecutionDetails.builder().approvalActivities(approvalActivities).build();
    }
    return null;
  }

  private HarnessApprovalInstance handleAutoApprovalForStep(
      String approvalInstanceId, StepBaseParameters stepParameters) {
    HarnessApprovalSpecParameters specParameters =
        HarnessApprovalUtils.getHarnessApprovalStepParameters(stepParameters);

    if (isNull(specParameters.getAutoApproval())) {
      throw new InvalidRequestException("Step timed out");
    }
    String comment = "";
    if (ParameterField.isNotNull(specParameters.getAutoApproval().getComments())) {
      comment = specParameters.getAutoApproval().getComments().getValue();
    }

    HarnessApprovalActivityRequestDTO harnessApprovalActivityRequestDTO = HarnessApprovalActivityRequestDTO.builder()
                                                                              .action(HarnessApprovalAction.APPROVE)
                                                                              .comments(comment)
                                                                              .autoApprove(true)
                                                                              .build();

    EmbeddedUser user = EmbeddedUser.builder().name(HARNESS).email(HARNESS).build();
    return approvalInstanceService.addHarnessApprovalActivityV2(
        approvalInstanceId, user, harnessApprovalActivityRequestDTO, false);
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    approvalInstanceService.abortByNodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    closeLogStream(ambiance);
  }

  @Override
  public void handleExpire(
      Ambiance ambiance, StepBaseParameters stepParameters, AsyncExecutableResponse executableResponse) {
    ApprovalUtils.handleApprovalExpiryEvent(ambiance, approvalInstanceService, logStreamingStepClientFactory);
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public StepExecutionTelemetryEventDTO getStepExecutionTelemetryEventDTO(
      Ambiance ambiance, StepBaseParameters stepParameters) {
    return StepExecutionTelemetryEventDTO.builder().stepType(STEP_TYPE.getType()).build();
  }

  private void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.changeadvisor;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.approval.ApprovalNotificationHandler;
import io.harness.steps.approval.step.beans.ApprovalServiceAccountDTO;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.beans.ApprovalUserGroupDTO;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;
import io.harness.steps.approval.step.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.step.HarnessApprovalStep;
import io.harness.steps.changeadvisor.ChangeAdvisorEvaluationHelper.EvaluationResponse;
import io.harness.steps.changeadvisor.ChangeAdvisorEvaluationHelper.EvaluationStatus;
import io.harness.steps.executables.PipelineAsyncExecutable;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CV)
@Slf4j
public class ChangeAdvisorV1Step extends PipelineAsyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstantsV1.CHANGE_ADVISOR_STEP_TYPE;
  public static final String CHANGE_ADVISOR_APPROVAL_OUTCOME = "changeAdvisorApproval";

  @Inject private ChangeAdvisorEvaluationHelper evaluationHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private ApprovalInstanceService approvalInstanceService;
  @Inject private ApprovalNotificationHandler approvalNotificationHandler;
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject private HarnessApprovalStep harnessApprovalStep;

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    ChangeAdvisorStepSpecParameters params = ChangeAdvisorEvaluationHelper.extractParams(stepParameters);
    EvaluationResponse evaluationResponse = evaluationHelper.evaluate(ambiance, params);

    if (evaluationResponse.getStatus() == EvaluationStatus.FEATURE_DISABLED
        || evaluationResponse.getStatus() == EvaluationStatus.CALL_FAILED) {
      return AsyncExecutableResponse.newBuilder().build();
    }

    if (evaluationResponse.getStatus() == EvaluationStatus.COMING_SOON) {
      outcomeService.consume(ambiance, ChangeAdvisorStep.OUTCOME_NAME, evaluationResponse.getComingSoonOutcome(), "");
      return AsyncExecutableResponse.newBuilder().build();
    }

    ChangeAdvisorOutcome outcome = evaluationResponse.getAdvisorOutcome();
    outcomeService.consume(ambiance, ChangeAdvisorStep.OUTCOME_NAME, outcome, "");

    if (!ChangeAdvisorEvaluationHelper.requiresApproval(evaluationResponse.getAdvisory())) {
      return AsyncExecutableResponse.newBuilder().build();
    }

    HarnessApprovalInstance approvalInstance =
        buildApprovalInstance(ambiance, stepParameters, params, evaluationResponse.getAdvisory());
    validateApprovers(approvalInstance);

    List<UserGroupDTO> validatedUserGroups = approvalNotificationHandler.getUserGroups(approvalInstance);
    approvalInstance.setValidatedUserGroups(validatedUserGroups);
    approvalInstance.setValidatedApprovalUserGroups(
        validatedUserGroups.stream().map(ApprovalUserGroupDTO::toApprovalUserGroupDTO).collect(Collectors.toList()));
    approvalInstance.setValidatedApprovalServiceAccounts(
        getServiceAccountsList(approvalInstance.getApprovers().getServiceAccounts(), ambiance));

    HarnessApprovalInstance savedApprovalInstance =
        (HarnessApprovalInstance) approvalInstanceService.save(approvalInstance);
    executorService.submit(() -> approvalNotificationHandler.sendNotification(savedApprovalInstance, ambiance));

    sweepingOutputService.consume(ambiance, CHANGE_ADVISOR_APPROVAL_OUTCOME,
        ChangeAdvisorApprovalStepOutcome.builder().approvalInstanceId(approvalInstance.getId()).build(), "");

    return AsyncExecutableResponse.newBuilder().addCallbackIds(approvalInstance.getId()).build();
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    if (responseDataMap == null || responseDataMap.isEmpty()) {
      return StepResponse.builder().status(Status.SUCCEEDED).build();
    }
    return harnessApprovalStep.handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    harnessApprovalStep.handleAbort(ambiance, stepParameters, executableResponse, userMarked);
  }

  @Override
  public void handleExpire(
      Ambiance ambiance, StepBaseParameters stepParameters, AsyncExecutableResponse executableResponse) {
    harnessApprovalStep.handleExpire(ambiance, stepParameters, executableResponse);
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  private HarnessApprovalInstance buildApprovalInstance(
      Ambiance ambiance, StepBaseParameters stepParameters, ChangeAdvisorStepSpecParameters params, Advisory advisory) {
    String stageIdentifier = "";
    Optional<Level> stageLevel = AmbianceUtils.getStageLevelFromAmbiance(ambiance);
    if (stageLevel.isPresent()) {
      stageIdentifier = stageLevel.get().getIdentifier();
    }

    HarnessApprovalInstance instance = HarnessApprovalInstance.builder()
                                           .approvalMessage(buildApprovalMessage(advisory))
                                           .includePipelineExecutionHistory(false)
                                           .approvalActivities(new ArrayList<>())
                                           .approvers(buildApproversDTO(params))
                                           .approvalKey(stageIdentifier + "#" + stepParameters.getIdentifier())
                                           .build();
    instance.applyStepExecutionContext(ambiance, stepParameters);
    instance.setType(ApprovalType.HARNESS_APPROVAL);
    return instance;
  }

  private static String buildApprovalMessage(Advisory advisory) {
    if (advisory == null) {
      return "ChangeAdvisor gate — approval required";
    }
    String decision = advisory.getDecision() != null ? advisory.getDecision() : "GATE";
    if (advisory.getScore() != null) {
      return String.format("ChangeAdvisor %s — score %s", decision, advisory.getScore());
    }
    return String.format("ChangeAdvisor %s — approval required", decision);
  }

  private static ApproversDTO buildApproversDTO(ChangeAdvisorStepSpecParameters params) {
    if (params == null || params.getApprovers() == null) {
      throw new InvalidRequestException("ChangeAdvisor approvers must be configured when decision is GATE or BLOCK");
    }
    return ApproversDTO.fromApprovers(params.getApprovers());
  }

  private static void validateApprovers(HarnessApprovalInstance approvalInstance) {
    final List<String> userGroups = approvalInstance.getApprovers().getUserGroups();
    final List<String> serviceAccounts = approvalInstance.getApprovers().getServiceAccounts();
    final boolean isAnyValidUserGroupPresent = userGroups.stream().anyMatch(EmptyPredicate::isNotEmpty);
    final boolean isAnyValidServiceAccountPresent = serviceAccounts.stream().anyMatch(EmptyPredicate::isNotEmpty);
    if (!(isAnyValidUserGroupPresent || isAnyValidServiceAccountPresent)) {
      throw new InvalidRequestException("All the provided user groups and/or service accounts are empty");
    }
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
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.custom;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.PL_ENABLE_GRANULAR_CLAIMS_FOR_VAULT;
import static io.harness.steps.approval.step.entities.ApprovalUtils.getCustomApprovalTaskName;
import static io.harness.steps.approval.step.entities.ApprovalUtils.updateTaskId;

import static software.wings.beans.TaskType.SHELL_SCRIPT_TASK_NG;
import static software.wings.beans.TaskType.WIN_RM_SHELL_SCRIPT_TASK_NG;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.constants.OrchestrationPublisherName;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.delegate.task.shell.winrm.WinRmShellScriptTaskNG;
import io.harness.engine.pms.tasks.NgDelegate2TaskExecutor;
import io.harness.engine.pms.tasks.RunnerTaskExecutor;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.iterator.interfaces.PersistenceIterator;
import io.harness.logging.AutoLogContext;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.oidc.helper.OIDCContextHelper;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.approval.step.custom.CustomApprovalHelperService;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;
import io.harness.steps.approval.step.entities.CustomApprovalInstance;
import io.harness.steps.shellscript.ShellScriptHelperService;
import io.harness.steps.shellscript.ShellScriptStepParametersV0;
import io.harness.steps.shellscript.ShellType;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.waiter.NotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Slf4j
public class CustomApprovalHelperServiceImpl implements CustomApprovalHelperService {
  private final NgDelegate2TaskExecutor ngDelegate2TaskExecutor;
  private final KryoSerializer kryoSerializer;
  private final WaitNotifyEngine waitNotifyEngine;
  private final LogStreamingStepClientFactory logStreamingStepClientFactory;
  private final String publisherName;
  private final PmsGitSyncHelper pmsGitSyncHelper;
  private final ShellScriptHelperService shellScriptHelperService;
  private final ApprovalInstanceService approvalInstanceService;
  private final StepHelper stepHelper;
  private final RunnerTaskExecutor runnerTaskExecutor;
  private final OIDCContextHelper oidcContextHelper;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Inject
  public CustomApprovalHelperServiceImpl(NgDelegate2TaskExecutor ngDelegate2TaskExecutor,
      @Named("referenceFalseKryoSerializer") KryoSerializer kryoSerializer, WaitNotifyEngine waitNotifyEngine,
      LogStreamingStepClientFactory logStreamingStepClientFactory,
      @Named(OrchestrationPublisherName.PUBLISHER_NAME) String publisherName, PmsGitSyncHelper pmsGitSyncHelper,
      ShellScriptHelperService shellScriptHelperService, ApprovalInstanceService approvalInstanceService,
      StepHelper stepHelper, RunnerTaskExecutor runnerTaskExecutor, OIDCContextHelper oidcContextHelper,
      PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    this.ngDelegate2TaskExecutor = ngDelegate2TaskExecutor;
    this.kryoSerializer = kryoSerializer;
    this.waitNotifyEngine = waitNotifyEngine;
    this.logStreamingStepClientFactory = logStreamingStepClientFactory;
    this.publisherName = publisherName;
    this.pmsGitSyncHelper = pmsGitSyncHelper;
    this.shellScriptHelperService = shellScriptHelperService;
    this.approvalInstanceService = approvalInstanceService;
    this.stepHelper = stepHelper;
    this.runnerTaskExecutor = runnerTaskExecutor;
    this.oidcContextHelper = oidcContextHelper;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
  }

  @Override
  public void handlePollingEvent(PersistenceIterator<ApprovalInstance> iterator, CustomApprovalInstance instance) {
    try (PmsGitSyncBranchContextGuard ignore1 =
             pmsGitSyncHelper.createGitSyncBranchContextGuard(instance.getAmbiance(), true);
         AutoLogContext ignore2 = instance.autoLogContext()) {
      if (pmsFeatureFlagHelper.isEnabled(instance.getAccountId(), PL_ENABLE_GRANULAR_CLAIMS_FOR_VAULT)) {
        Map<String, String> oidcParamMap = AmbianceUtils.extractOidcContextFields(instance.getAmbiance());
        oidcContextHelper.upsertOIDCContextInThreadLocal(oidcParamMap);
      }
      handlePollingEventInternal(iterator, instance);
    }
  }

  private void handlePollingEventInternal(
      PersistenceIterator<ApprovalInstance> iterator, CustomApprovalInstance instance) {
    Ambiance ambiance = instance.getAmbiance();
    NGLogCallback logCallback = getLogCallback(ambiance, instance);

    try {
      log.info("Polling custom approval instance");
      logCallback.saveExecutionLog("-----");
      logCallback.saveExecutionLog(LogHelper.color(
          "Running custom shell script to check approval/rejection criteria", LogColor.White, LogWeight.Bold));

      String instanceId = instance.getId();
      String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
      String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
      log.info(String.format("Creating parameters for CustomApproval Instance with id : %s", instanceId));

      validateField(instanceId, ApprovalInstanceKeys.id);
      validateField(accountIdentifier, "accountIdentifier");
      validateField(orgIdentifier, "orgIdentifier");
      validateField(projectIdentifier, "projectIdentifier");
      String taskId;
      if (HarnessYamlVersion.isV1(AmbianceUtils.getPipelineVersion(ambiance))) {
        RunStepInfoV1 runStep =
            RecastOrchestrationUtils.fromMap(instance.getRunStepInfoV1Outcome(), RunStepInfoV1.class);
        // Declaring non-primitive long because there are the cases where timeout is not needed as parameter so we will
        // be passing null. In case timeout is null then we will be handling with default timeout handling inside
        // submitTask method.
        Long timeOutInMillis = instance.getScriptTimeout().getValue().getTimeoutInMillis();
        taskId = runnerTaskExecutor.submitTask(
            runStep, ambiance, AmbianceUtils.getStepIdentifierFromAmbiance(ambiance), timeOutInMillis);
        logCallback.saveExecutionLog(String.format("Custom Run Step Approval: %s", taskId));
        NotifyCallback callback = CustomApprovalCallback.builder().approvalInstanceId(instanceId).build();
        waitNotifyEngine.waitForAllOn(publisherName, callback, taskId);
      } else {
        TaskParameters scriptTaskParametersNG = buildShellScriptTaskParametersNG(ambiance, instance);
        log.debug("Queuing Custom Approval delegate task");
        taskId = queueTask(ambiance, instance, scriptTaskParametersNG);
        logCallback.saveExecutionLog(String.format("Custom Shell Script Approval: %s", taskId));
      }
      updateTaskId(instanceId, taskId, approvalInstanceService);

      log.info("Custom Approval Instance queued task with taskId - {}", taskId);
    } catch (Exception ex) {
      logCallback.saveExecutionLog(
          String.format("Error creating task to run the custom shell script: %s", ExceptionUtils.getMessage(ex)),
          LogLevel.WARN);
      log.warn("Error creating task for running the shell script approval while polling", ex);
      resetNextIteration(iterator, instance);
    }
  }

  private NGLogCallback getLogCallback(Ambiance ambiance, CustomApprovalInstance instance) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, ShellScriptTaskNG.COMMAND_UNIT, false);
  }

  private TaskParameters buildShellScriptTaskParametersNG(
      @Nonnull Ambiance ambiance, @Nonnull CustomApprovalInstance customApprovalInstance) {
    ShellScriptStepParametersV0 shellScriptStepParameters = customApprovalInstance.toShellScriptStepParameters();
    return shellScriptHelperService.buildShellScriptTaskParametersNG(ambiance, shellScriptStepParameters);
  }

  private String queueTask(
      Ambiance ambiance, CustomApprovalInstance approvalInstance, TaskParameters shellScriptTaskParametersNG) {
    TaskRequest taskRequest = prepareCustomApprovalTaskRequest(ambiance, approvalInstance, shellScriptTaskParametersNG);
    String taskId =
        ngDelegate2TaskExecutor.queueTask(ambiance.getSetupAbstractionsMap(), taskRequest, Duration.ofSeconds(0));
    NotifyCallback callback = CustomApprovalCallback.builder().approvalInstanceId(approvalInstance.getId()).build();
    waitNotifyEngine.waitForAllOn(publisherName, callback, taskId);
    return taskId;
  }

  private TaskRequest prepareCustomApprovalTaskRequest(
      Ambiance ambiance, CustomApprovalInstance instance, TaskParameters stepParameters) {
    if (ShellType.Bash.equals(instance.getShellType())) {
      return prepareBashCustomApprovalTaskRequest(ambiance, instance, stepParameters);
    } else if (ShellType.PowerShell.equals(instance.getShellType())) {
      return preparePowerShellCustomApprovalTaskRequest(ambiance, instance, stepParameters);
    } else {
      throw new InvalidRequestException(format("Shell %s is not supported", instance.getShellType()));
    }
  }

  private TaskRequest prepareBashCustomApprovalTaskRequest(
      Ambiance ambiance, CustomApprovalInstance instance, TaskParameters stepParameters) {
    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(SHELL_SCRIPT_TASK_NG.name())
            .parameters(new Object[] {stepParameters})
            .timeout(instance.getScriptTimeout().getValue().getTimeoutInMillis()) // --> Here timeout is being set
            .build();
    List<TaskSelector> selectors = TaskSelectorYaml.toTaskSelector(instance.getDelegateSelectors());
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(
            StepUtils.generateLogAbstractions(ambiance), Collections.singletonList(ShellScriptTaskNG.COMMAND_UNIT))),
        null, getCustomApprovalTaskName(instance), selectors, stepHelper.getEnvironmentType(ambiance));
  }

  private TaskRequest preparePowerShellCustomApprovalTaskRequest(
      Ambiance ambiance, CustomApprovalInstance instance, TaskParameters stepParameters) {
    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(WIN_RM_SHELL_SCRIPT_TASK_NG.name())
                            .parameters(new Object[] {stepParameters})
                            .timeout(instance.getScriptTimeout().getValue().getTimeoutInMillis())
                            .build();

    List<TaskSelector> selectors = TaskSelectorYaml.toTaskSelector(instance.getDelegateSelectors());

    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        Arrays.asList(WinRmShellScriptTaskNG.COMMAND_UNIT), getCustomApprovalTaskName(instance), selectors,
        stepHelper.getEnvironmentType(ambiance));
  }

  private void validateField(String name, String value) {
    if (isBlank(value)) {
      throw new InvalidRequestException(format("Field %s can't be empty", name));
    }
  }

  private void resetNextIteration(PersistenceIterator<ApprovalInstance> iterator, CustomApprovalInstance instance) {
    approvalInstanceService.resetNextIterations(instance.getId(), instance.recalculateNextIterations());
    if (iterator != null) {
      iterator.wakeup();
    }
  }
}

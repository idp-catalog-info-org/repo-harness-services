/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.app.beans.entities.CIResourceCleanup.CIResourceCleanupResponseKeys;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponseFromRunner;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.taskrunner.TaskRunnerTaskResponse;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;
import io.harness.persistence.HPersistence;
import io.harness.runnercommons.logging.TransactionalTaskLogContext;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Transient;

@OwnedBy(CI)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class CICleanupTaskNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Inject @Transient private HPersistence persistence;
  @Inject @Transient private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject @Transient private CILogServiceUtils ciLogServiceUtils;
  private String stageExecutionID;
  private String accountID;
  private String planExecutionID;
  private String leLogKey;
  private String memoryMetricsLogKey;

  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    Optional<String> optionalTaskID = response.keySet().stream().findFirst();
    try {
      if (optionalTaskID.isPresent()) {
        Supplier<ResponseData> responseSupplier = response.get(optionalTaskID.get());
        ResponseData responseData = responseSupplier.get();
        responseData = serializedResponseDataHelper.deserialize(responseData);
        if (responseData instanceof TaskRunnerTaskResponse) {
          // END_TRANSACTION ran via task-runner returns the infra-agnostic TaskRunnerTaskResponse.
          // Promote it to a K8s response (only K8s is supported on task-runner today) so the
          // existing instanceof branches read status / delegateMetaInfo correctly and the SUCCESS
          // default at the variable initializer below isn't applied to a failed cleanup.
          // TODO: Generalize when VM/Docker infra is added to task-runner.
          responseData = toK8sTaskExecutionResponseFromRunner((TaskRunnerTaskResponse) responseData);
        }
        CommandExecutionStatus commandExecutionStatus = CommandExecutionStatus.SUCCESS;
        String infraType = null;
        DelegateMetaInfo delegateMetaInfo = null;
        boolean isUnifiedRunner = false;
        if (responseData instanceof K8sTaskExecutionResponse) {
          K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) responseData;
          commandExecutionStatus = k8sTaskExecutionResponse.getCommandExecutionStatus();
          infraType = k8sTaskExecutionResponse.getType().toString();
          delegateMetaInfo = k8sTaskExecutionResponse.getDelegateMetaInfo();
          isUnifiedRunner = k8sTaskExecutionResponse.isFromUnifiedRunner();
        } else if (responseData instanceof VmTaskExecutionResponse) {
          VmTaskExecutionResponse vmTaskExecutionResponse = (VmTaskExecutionResponse) responseData;
          commandExecutionStatus = vmTaskExecutionResponse.getCommandExecutionStatus();
          infraType = vmTaskExecutionResponse.getType().toString();
          delegateMetaInfo = vmTaskExecutionResponse.getDelegateMetaInfo();
          isUnifiedRunner = vmTaskExecutionResponse.isFromUnifiedRunner();
        }
        try (AutoLogContext ignore = new TransactionalTaskLogContext(isUnifiedRunner, optionalTaskID.get(),
                 StageCleanupUtility.CLEANUP, stageExecutionID, infraType, planExecutionID, accountID,
                 commandExecutionStatus != null ? commandExecutionStatus.toString() : null,
                 delegateMetaInfo != null ? delegateMetaInfo.getId() : null)) {
          log.info("Received cleanup response with status {} for stageExecutionId {}, planExecutionID {}, accountID {}",
              commandExecutionStatus, stageExecutionID, planExecutionID, accountID);
        }
        if (commandExecutionStatus == CommandExecutionStatus.SUCCESS) {
          persistence.delete(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)
                                 .filter(CIResourceCleanupResponseKeys.stageExecutionId, stageExecutionID));
        }

        // Close lite engine log stream with snapshot after cleanup response is received
        if (StringUtils.isNotBlank(leLogKey)) {
          try {
            ciLogServiceUtils.closeLogStream(accountID, leLogKey, true, false);
            log.info("Successfully closed lite engine log stream for leLogKey: {}", leLogKey);
          } catch (Exception e) {
            log.error("Failed to close lite engine log stream for leLogKey: {}, stageExecutionId: {}", leLogKey,
                stageExecutionID, e);
          }
        }

        // Close memory metrics log stream with snapshot after cleanup response is received
        if (StringUtils.isNotBlank(memoryMetricsLogKey)) {
          try {
            ciLogServiceUtils.closeLogStream(accountID, memoryMetricsLogKey, true, false);
            log.info("Successfully closed memory metrics log stream for memoryMetricsLogKey: {}", memoryMetricsLogKey);
          } catch (Exception e) {
            log.error("Failed to close memory metrics log stream for memoryMetricsLogKey: {}, stageExecutionId: {}",
                memoryMetricsLogKey, stageExecutionID, e);
          }
        }
      }
    } catch (Exception exception) {
      log.error("Exception occurred during cleanup response consumption for stageExecutionId {}, planExecutionID {}, "
              + "accountID {}. Failure Message: {} and exception {}",
          stageExecutionID, planExecutionID, accountID, exception.getMessage(), exception);
    }
  }

  private static K8sTaskExecutionResponse toK8sTaskExecutionResponseFromRunner(TaskRunnerTaskResponse taskResponse) {
    return K8sTaskExecutionResponseFromRunner.builder()
        .delegateMetaInfo(taskResponse.getDelegateMetaInfo())
        .commandExecutionStatus(taskResponse.getCommandExecutionStatus())
        .errorMessage(taskResponse.getErrorMessage())
        .build()
        .toK8sTaskExecutionResponse();
  }
}

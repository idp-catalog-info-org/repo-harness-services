/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.tasks;

import static software.wings.beans.TaskType.SCM_GIT_REF_TASK;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.delegate.AccountId;
import io.harness.delegate.CancelTaskRequest;
import io.harness.delegate.CancelTaskResponse;
import io.harness.delegate.DelegateServiceGrpc.DelegateServiceBlockingStub;
import io.harness.delegate.RunnerRequest;
import io.harness.delegate.Schedule;
import io.harness.delegate.ScheduleTaskResponse;
import io.harness.delegate.ScheduleTaskServiceGrpc;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.SubmitTaskResponse;
import io.harness.delegate.TaskId;
import io.harness.delegate.TaskMode;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.utils.HTimestamps;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.ng.core.AccountIdContext;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest.RequestCase;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.utils.PmsGrpcClientUtils;
import io.harness.runnercommons.cgi.utils.ScheduleUtils;
import io.harness.runnercommons.cgi.utils.UnifiedConditionChecker;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class NgDelegate2TaskExecutor implements TaskExecutor {
  @Inject private DelegateServiceBlockingStub delegateServiceBlockingStub;
  @Inject private DelegateSyncService delegateSyncService;
  @Inject private DelegateAsyncService delegateAsyncService;
  @Inject private Supplier<DelegateCallbackToken> tokenSupplier;
  @Inject private DelegateServiceGrpcClient delegateServiceGrpcClient;
  @Inject private ScheduleTaskServiceGrpc.ScheduleTaskServiceBlockingStub delegateScheduleTaskBlockingStub;
  @Inject private UnifiedConditionChecker unifiedConditionChecker;
  @Inject(optional = true)
  private io.harness.runnercommons.cgi.task.git.RunnerGitRefTaskBuilder runnerGitRefTaskBuilder;
  @Inject @Named("referenceFalseKryoSerializer") private io.harness.serializer.KryoSerializer kryoSerializer;

  private int deadlineAfterDuration = System.getenv("NG_DELEGATE_TASK_EXECUTOR_DEADLINE_DURATION") != null
      ? Integer.parseInt(System.getenv("NG_DELEGATE_TASK_EXECUTOR_DEADLINE_DURATION"))
      : 300;

  @Override
  public String queueTask(Map<String, String> setupAbstractions, TaskRequest taskRequest, Duration holdFor) {
    TaskRequestValidityCheck check = validateTaskRequest(taskRequest, TaskMode.ASYNC);
    if (!check.isValid()) {
      throw new InvalidRequestException(check.getMessage());
    }
    // We are doing a pipeline level check for unified flow, to minimise the affected scope
    // This is a temporary solution, once unified flow is enabled at account level we can remove this.
    String taskType = taskRequest.getDelegateTaskRequest().getRequest().getDetails().getType().getType();
    String accountId = taskRequest.getDelegateTaskRequest().getRequest().getAccountId().getId();
    boolean isCITask = SCM_GIT_REF_TASK.name().equals(taskType);

    if ("true".equals(setupAbstractions.get(UnifiedConditionChecker.CD_ROUTE_TO_UNIFIED))
        || unifiedConditionChecker.shouldUseUnifiedFlow(accountId, isCITask)) {
      return executeUnifiedTask(taskRequest.getDelegateTaskRequest().getRequest(), holdFor);
    }
    SubmitTaskResponse submitTaskResponse;
    try (ResponseTimeRecorder ignore = new ResponseTimeRecorder(
             "Delegate task queued successfully via NGDelegateTaskExecutor", Arrays.asList(30000L, 60000L))) {
      submitTaskResponse = PmsGrpcClientUtils.retryAndProcessException(supplier
          -> supplier.get().submitTaskV2(buildTaskRequestWithToken(taskRequest.getDelegateTaskRequest().getRequest())),
          () -> delegateServiceBlockingStub.withDeadlineAfter(deadlineAfterDuration, TimeUnit.SECONDS));
    }

    delegateAsyncService.setupTimeoutForTask(submitTaskResponse.getTaskId().getId(),
        Timestamps.toMillis(submitTaskResponse.getTotalExpiry()), currentTimeMillis() + holdFor.toMillis());
    return submitTaskResponse.getTaskId().getId();
  }

  @Override
  public <T extends ResponseData> T executeTask(Map<String, String> setupAbstractions, TaskRequest taskRequest) {
    TaskRequestValidityCheck check = validateTaskRequest(taskRequest, TaskMode.SYNC);
    if (!check.isValid()) {
      throw new InvalidRequestException(check.getMessage());
    }
    SubmitTaskRequest submitTaskRequest = buildTaskRequestWithToken(taskRequest.getDelegateTaskRequest().getRequest());

    SubmitTaskResponse submitTaskResponse;
    try (ResponseTimeRecorder ignore = new ResponseTimeRecorder(
             "Delegate task executed successfully via NGDelegateTaskExecutor", Arrays.asList(30000L, 60000L))) {
      submitTaskResponse = PmsGrpcClientUtils.retryAndProcessException(supplier
          -> supplier.get().submitTaskV2(submitTaskRequest),
          () -> delegateServiceBlockingStub.withDeadlineAfter(deadlineAfterDuration, TimeUnit.SECONDS));
    }

    return delegateSyncService.waitForTask(submitTaskResponse.getTaskId().getId(),
        submitTaskRequest.getDetails().getType().getType(),
        Duration.ofMillis(HTimestamps.toMillis(submitTaskResponse.getTotalExpiry()) - currentTimeMillis()), null);
  }

  private TaskRequestValidityCheck validateTaskRequest(TaskRequest taskRequest, TaskMode validMode) {
    if (taskRequest.getRequestCase() != RequestCase.DELEGATETASKREQUEST) {
      return TaskRequestValidityCheck.builder()
          .valid(false)
          .message("Task Request doesnt contain delegate Task Request")
          .build();
    }
    String message = null;
    SubmitTaskRequest submitTaskRequest = taskRequest.getDelegateTaskRequest().getRequest();
    TaskMode mode = submitTaskRequest.getDetails().getMode();
    boolean valid = mode == validMode;
    if (!valid) {
      message = String.format("DelegateTaskRequest Mode %s Not Supported", mode);
    }
    return TaskRequestValidityCheck.builder().valid(valid).message(message).build();
  }

  @Override
  public void expireTask(Map<String, String> setupAbstractions, String taskId) {
    throw new NotImplementedException("Expire task is not implemented");
  }

  private SubmitTaskRequest buildTaskRequestWithToken(SubmitTaskRequest request) {
    return request.toBuilder().setCallbackToken(tokenSupplier.get()).build();
  }

  @Override
  public boolean abortTask(Map<String, String> setupAbstractions, String taskId) {
    String accountId = setupAbstractions.get(SetupAbstractionKeys.accountId);
    boolean seedMdc = accountId != null && !accountId.isEmpty() && AccountIdContext.getAccountId() == null;
    if (seedMdc) {
      AccountIdContext.setAccountId(accountId);
    }
    try {
      CancelTaskResponse response =
          PmsGrpcClientUtils.retryAndProcessException(delegateServiceBlockingStub::cancelTaskV2,
              CancelTaskRequest.newBuilder()
                  .setAccountId(AccountId.newBuilder().setId(accountId).build())
                  .setTaskId(TaskId.newBuilder().setId(taskId).build())
                  .build());
      return true;
    } catch (Exception ex) {
      log.error("Failed to abort task with taskId: {}, Error : {}", taskId, ex.getMessage());
      return false;
    } finally {
      if (seedMdc) {
        AccountIdContext.clearAccountId();
      }
    }
  }

  @Value
  @Builder
  private static class TaskRequestValidityCheck {
    boolean valid;
    String message;
  }

  private String executeUnifiedTask(SubmitTaskRequest submitTaskRequest, Duration holdFor) {
    RunnerRequest runnerRequest = buildUnifiedRequest(submitTaskRequest);

    ScheduleTaskResponse executeResponse;
    try (ResponseTimeRecorder ignore = new ResponseTimeRecorder(
             "Delegate task queued successfully via NGDelegateTaskExecutor", Arrays.asList(30000L, 60000L))) {
      executeResponse = PmsGrpcClientUtils.retryAndProcessException(supplier
          -> supplier.get().submit(runnerRequest),
          () -> delegateScheduleTaskBlockingStub.withDeadlineAfter(deadlineAfterDuration, TimeUnit.SECONDS));
    }

    delegateAsyncService.setupTimeoutForTask(executeResponse.getTaskId().getId(),
        getTotalExpiryForRunnerTask(submitTaskRequest), currentTimeMillis() + holdFor.toMillis());
    return executeResponse.getTaskId().getId();
  }

  private RunnerRequest buildUnifiedRequest(SubmitTaskRequest submitTaskRequest) {
    var delegateTaskSpec = delegateServiceGrpcClient.buildDelegateTaskSpecForUnifiedTask(submitTaskRequest);

    // Extract owner value from TaskSetupAbstractions map
    String owner = "";
    if (submitTaskRequest.hasSetupAbstractions()
        && submitTaskRequest.getSetupAbstractions().getValuesMap().containsKey("owner")) {
      owner = submitTaskRequest.getSetupAbstractions().getValuesMap().get("owner");
    }

    // Build RunnerDelegateTask with both delegate and runner specs
    io.harness.delegate.RunnerDelegateTask.Builder runnerDelegateTaskBuilder =
        io.harness.delegate.RunnerDelegateTask.newBuilder().setDelegateTask(delegateTaskSpec);

    // Check if this is an SCM task that should run on runner
    String taskType = submitTaskRequest.getDetails().getType().getType();

    if (SCM_GIT_REF_TASK.name().equals(taskType) && runnerGitRefTaskBuilder != null && kryoSerializer != null) {
      try {
        // Extract SCM parameters from task details
        byte[] paramsBytes = submitTaskRequest.getDetails().getKryoParameters().toByteArray();
        Object deserializedObj = kryoSerializer.asInflatedObject(paramsBytes);

        // Handle both cases: Object[] array or direct ScmGitRefTaskParams
        io.harness.delegate.task.scm.ScmGitRefTaskParams scmParams;
        if (deserializedObj instanceof Object[]) {
          scmParams = (io.harness.delegate.task.scm.ScmGitRefTaskParams) ((Object[]) deserializedObj)[0];
        } else {
          scmParams = (io.harness.delegate.task.scm.ScmGitRefTaskParams) deserializedObj;
        }

        // Extract org/project from setup abstractions
        String orgId = submitTaskRequest.getSetupAbstractions().getValuesMap().getOrDefault("orgIdentifier", "");
        String projectId =
            submitTaskRequest.getSetupAbstractions().getValuesMap().getOrDefault("projectIdentifier", "");

        // Build RunnerTask for SCM
        io.harness.delegate.RunnerTask runnerTask = runnerGitRefTaskBuilder.getRunnerTask(
            scmParams, submitTaskRequest.getAccountId().getId(), orgId, projectId);

        // Add RunnerTask to the unified request
        runnerDelegateTaskBuilder.setRunnerTask(runnerTask);

      } catch (Exception e) {
        log.error("Failed to build RunnerTask for SCM_GIT_REF_TASK, will use delegate only", e);
        // Continue with delegate-only execution
      }
    }

    // Build the final RunnerDelegateTask
    io.harness.delegate.RunnerDelegateTask runnerDelegateTask = runnerDelegateTaskBuilder.build();

    RunnerRequest.Builder runnerRequestBuilder =
        RunnerRequest.newBuilder()
            .setAccountId(submitTaskRequest.getAccountId().getId())
            .setSelectionTrackingLogEnabled(true)
            .setRunnerDelegateTask(runnerDelegateTask)
            .setSchedulingConfig(
                Schedule.newBuilder()
                    .setExecutionTimeout(submitTaskRequest.getDetails().getExecutionTimeout())
                    .setRoutingPolicy(ScheduleUtils.getRoutingPolicy(submitTaskRequest.getSelectorsList(), owner))
                    .setCallBackToken(tokenSupplier.get().getToken())
                    .setSync(false)
                    .build());

    // IMPORTANT: Set deprecated task field for backward compatibility
    // Server-side code still checks this field
    if (runnerDelegateTask.hasRunnerTask()) {
      runnerRequestBuilder.setTask(runnerDelegateTask.getRunnerTask());
    }

    return runnerRequestBuilder.build();
  }

  private long getTotalExpiryForRunnerTask(SubmitTaskRequest submitTaskRequest) {
    long expiry = currentTimeMillis() + submitTaskRequest.getDetails().getExecutionTimeout().getSeconds() * 1000;
    if (submitTaskRequest.hasQueueTimeout()) {
      expiry = expiry + Durations.toMillis(submitTaskRequest.getQueueTimeout());
    }
    return expiry;
  }
}

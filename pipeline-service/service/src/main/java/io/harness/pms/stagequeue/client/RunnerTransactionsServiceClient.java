/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.stagequeue.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.ListRunnerTransactionsRequest;
import io.harness.delegate.ListRunnerTransactionsResponse;
import io.harness.delegate.RunnerTransactionPriority;
import io.harness.delegate.RunnerTransactionStatusFilter;
import io.harness.delegate.RunnerTransactionsServiceGrpc.RunnerTransactionsServiceBlockingStub;
import io.harness.delegate.UpdateRunnerTransactionsPriorityRequest;
import io.harness.delegate.UpdateRunnerTransactionsPriorityResponse;
import io.harness.exception.GeneralException;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.StatusRuntimeException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around {@link RunnerTransactionsServiceBlockingStub} so the rest of pipeline-service
 * (Layer 4 domain code) can stay decoupled from the generated proto stub. The stub itself is
 * provided by {@code DelegateServiceDriverGrpcClientModule} on the shared CG-manager channel and
 * carries the {@code runner-transactions-service} call credentials.
 *
 * <p>Each call is bounded by a per-call deadline (so a hung server cannot park the Dropwizard
 * worker thread indefinitely) and instrumented with {@link ResponseTimeRecorder} latency thresholds
 * plus {@link MetricService} counters/timers tagged with {@code accountId} and {@code status}.
 * Retries are intentionally not applied here: these endpoints sit on the synchronous customer
 * request path, so failing fast and surfacing a clean error to the caller is preferable to
 * amplifying load during a backend incident.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RunnerTransactionsServiceClient {
  private static final String LIST_REQUEST_COUNT = "runner_transactions_list_request_count";
  private static final String LIST_DURATION = "runner_transactions_list_duration";
  private static final String UPDATE_PRIORITY_REQUEST_COUNT = "runner_transactions_update_priority_request_count";
  private static final String UPDATE_PRIORITY_DURATION = "runner_transactions_update_priority_duration";
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_FAILURE = "FAILURE";

  private static final int DEFAULT_DEADLINE_SECONDS = 30;
  private static final int DEADLINE_SECONDS =
      Optional.ofNullable(System.getenv("RUNNER_TRANSACTIONS_SERVICE_DEADLINE_DURATION"))
          .map(Integer::parseInt)
          .orElse(DEFAULT_DEADLINE_SECONDS);

  private final RunnerTransactionsServiceBlockingStub stub;
  private final MetricService metricService;

  public ListRunnerTransactionsResponse list(String accountId, String orgId, String projectId,
      RunnerTransactionStatusFilter statusFilter, int page, int limit) {
    ListRunnerTransactionsRequest.Builder request =
        ListRunnerTransactionsRequest.newBuilder().setAccountId(accountId).setPage(page).setLimit(limit);
    if (orgId != null) {
      request.setOrgId(orgId);
    }
    if (projectId != null) {
      request.setProjectId(projectId);
    }
    if (statusFilter != null) {
      request.setStatusFilter(statusFilter);
    }
    ListRunnerTransactionsRequest builtRequest = request.build();

    long start = System.currentTimeMillis();
    boolean success = false;
    try (ResponseTimeRecorder ignore =
             new ResponseTimeRecorder("RunnerTransactionsServiceClient.list completed", Arrays.asList(2000L, 5000L))) {
      ListRunnerTransactionsResponse response =
          stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS).list(builtRequest);
      success = true;
      return response;
    } catch (StatusRuntimeException ex) {
      throw new GeneralException(
          String.format("runner-transactions-service list call failed: %s", ex.getStatus().getDescription()), ex);
    } finally {
      recordCallMetrics(LIST_REQUEST_COUNT, LIST_DURATION, accountId, success, System.currentTimeMillis() - start);
    }
  }

  public UpdateRunnerTransactionsPriorityResponse updatePriority(
      String accountId, List<String> stageRuntimeIds, RunnerTransactionPriority priority) {
    UpdateRunnerTransactionsPriorityRequest.Builder request =
        UpdateRunnerTransactionsPriorityRequest.newBuilder().setAccountId(accountId).setPriority(priority);
    if (stageRuntimeIds != null && !stageRuntimeIds.isEmpty()) {
      request.addAllStageRuntimeIds(stageRuntimeIds);
    }
    UpdateRunnerTransactionsPriorityRequest builtRequest = request.build();

    long start = System.currentTimeMillis();
    boolean success = false;
    try (ResponseTimeRecorder ignore = new ResponseTimeRecorder(
             "RunnerTransactionsServiceClient.updatePriority completed", Arrays.asList(2000L, 5000L))) {
      UpdateRunnerTransactionsPriorityResponse response =
          stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS).updatePriority(builtRequest);
      success = true;
      return response;
    } catch (StatusRuntimeException ex) {
      throw new GeneralException(
          String.format("runner-transactions-service updatePriority call failed: %s", ex.getStatus().getDescription()),
          ex);
    } finally {
      recordCallMetrics(UPDATE_PRIORITY_REQUEST_COUNT, UPDATE_PRIORITY_DURATION, accountId, success,
          System.currentTimeMillis() - start);
    }
  }

  private void recordCallMetrics(
      String countMetric, String durationMetric, String accountId, boolean success, long durationMs) {
    String status = success ? STATUS_SUCCESS : STATUS_FAILURE;
    try (PmsMetricContextGuard ignore = new PmsMetricContextGuard(ImmutableMap.of(
             PmsEventMonitoringConstants.ACCOUNT_ID, accountId, PmsEventMonitoringConstants.STATUS, status))) {
      metricService.incCounter(countMetric);
      metricService.recordMetric(durationMetric, durationMs);
    }
  }
}

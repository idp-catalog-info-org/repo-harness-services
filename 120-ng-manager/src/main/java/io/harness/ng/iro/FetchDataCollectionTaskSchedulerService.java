/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.ff.FeatureFlagService;
import io.harness.ng.iro.config.IRConfig;
import io.harness.service.DelegateGrpcClientWrapper;

import clients.iromanager.beans.IRODataCollectionTaskItem;
import clients.iromanager.beans.IRODataCollectionTaskList;
import clients.iromanager.remote.IROManagerHttpClient;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FetchDataCollectionTaskSchedulerService {
  IROManagerHttpClient httpClient;
  DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private final ScheduledExecutorService fetchDataCollectionTaskScheduler;
  private final IRODataCollectionTaskService iroDataCollectionTaskService;

  private final FeatureFlagService featureFlagService;
  private final IRConfig irConfig;

  @Inject
  public FetchDataCollectionTaskSchedulerService(IROManagerHttpClient httpClient,
      @Named("iroDataCollectionTaskScheduler") ScheduledExecutorService fetchDataCollectionTaskScheduler,
      IRODataCollectionTaskService iroDataCollectionTaskService, FeatureFlagService featureFlagService,
      IRConfig irConfig) {
    this.httpClient = httpClient;
    this.fetchDataCollectionTaskScheduler = fetchDataCollectionTaskScheduler;
    this.iroDataCollectionTaskService = iroDataCollectionTaskService;
    this.featureFlagService = featureFlagService;
    this.irConfig = irConfig;
    fetchDataCollectionTasks();
  }

  private void fetchDataCollectionTasks() {
    if (irConfig.isDctEnabled()) {
      fetchDataCollectionTaskScheduler.scheduleAtFixedRate(this::fetchTasks, irConfig.getDctInitialDelaySeconds(),
          irConfig.getDctSchedulerPeriodSeconds(), TimeUnit.SECONDS);
    }
  }

  private void fetchTasks() {
    int page = 0;
    int limit = irConfig.getDctLimitPerApiCall();
    long maxCount = irConfig.getDctMaxCountPerIteration();
    long initialCount = 0;
    boolean hasMoreData = true;

    while (hasMoreData && initialCount < maxCount) {
      try {
        IRODataCollectionTaskList iroDataCollectionTaskList =
            httpClient.getDataCollectionTasks(page, limit).execute().body();
        List<IRODataCollectionTaskItem> iroDataCollectionTaskItems = iroDataCollectionTaskList.getItems();
        if (isEmpty(iroDataCollectionTaskItems)) {
          hasMoreData = false;
        } else {
          log.info("Received total data collection tasks: {}", iroDataCollectionTaskItems.size());
          for (IRODataCollectionTaskItem iroDataCollectionTaskItem : iroDataCollectionTaskItems) {
            iroDataCollectionTaskService.submitAsyncDataCollectionTask(iroDataCollectionTaskItem.getAccountIdentifier(),
                iroDataCollectionTaskItem.getOrgIdentifier(), iroDataCollectionTaskItem.getProjectIdentifier(),
                iroDataCollectionTaskItem);
          }
          page++;
          initialCount += iroDataCollectionTaskItems.size();
          Thread.sleep(irConfig.getDctSleepMs()); // Sleep for 5 seconds before fetching next iteration
        }
      } catch (Exception e) {
        log.warn("Error occurred while fetching data collection tasks: {}", e.getMessage());
        hasMoreData = false;
      }
    }
  }
}

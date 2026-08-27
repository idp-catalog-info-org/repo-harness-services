/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import software.wings.delegatetasks.cv.IRODataCollectionTaskResult;

import clients.iromanager.remote.IROManagerHttpClient;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IROManagerNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Inject IROManagerHttpClient iroManagerHttpClient;

  String delegateTaskId;

  public IROManagerNotifyCallback(String delegateTaskId) {
    this.delegateTaskId = delegateTaskId;
  }

  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    IRODataCollectionTaskResult iroDataCollectionTaskResult =
        (IRODataCollectionTaskResult) response.get(delegateTaskId).get();
    try {
      iroDataCollectionTaskResult.setDelegateDataCollectionId(delegateTaskId);
      iroManagerHttpClient.saveDataCollectionResult(iroDataCollectionTaskResult).execute();
    } catch (IOException e) {
      log.error("Error while sending IRO dataCollectionResults: %s", e);
    }
  }
}
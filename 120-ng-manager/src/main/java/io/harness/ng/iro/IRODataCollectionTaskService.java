/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.spec.server.ng.v1.model.PrometheusQueryRequest;

import software.wings.delegatetasks.cv.IRODataCollectionTaskResult;

import clients.iromanager.beans.IRODataCollectionTaskItem;
import java.io.IOException;

public interface IRODataCollectionTaskService {
  IRODataCollectionTaskResult getDataCollectionResult(String accountId, String orgIdentifier, String projectIdentifier,
      PrometheusQueryRequest prometheusQueryRequest) throws IOException;

  String submitAsyncDataCollectionTask(String accountId, String orgIdentifier, String projectIdentifier,
      IRODataCollectionTaskItem prometheusQueryRequest);
}

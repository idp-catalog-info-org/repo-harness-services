/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.delegate.task.iro.IRODataCollectionRequest;
import io.harness.delegate.task.iro.IRODataCollectionRequest.IRODataCollectionRequestBuilder;
import io.harness.spec.server.ng.v1.model.PrometheusQueryRequest;

import clients.iromanager.beans.IRODataCollectionTaskItem;
import clients.iromanager.beans.IRODataCollectionTaskMetricInfo;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IROUtils {
  public static IRODataCollectionRequestBuilder convertToIRODataCollectionRequest(
      PrometheusQueryRequest prometheusQueryRequest) {
    Map<String, String> queryMap = new HashMap<>();
    queryMap.put("query", prometheusQueryRequest.getQuery());
    return IRODataCollectionRequest.builder()
        .query(prometheusQueryRequest.getQuery())
        .queryMap(queryMap)
        .startTime(Instant.ofEpochSecond(prometheusQueryRequest.getStartTime()))
        .step(prometheusQueryRequest.getStep())
        .endTime(Instant.ofEpochSecond(prometheusQueryRequest.getEndTime()))
        .tracingId(UUID.randomUUID().toString());
  }

  public static IRODataCollectionRequestBuilder convertToIRODataCollectionRequest(
      IRODataCollectionTaskItem iroDataCollectionTaskItem) {
    Map<String, String> queryMap = new HashMap<>();
    for (IRODataCollectionTaskMetricInfo iroDataCollectionTaskMetricInfo :
        iroDataCollectionTaskItem.getDataInfo().getMetricInfo()) {
      queryMap.put(iroDataCollectionTaskMetricInfo.getName(), iroDataCollectionTaskMetricInfo.getQuery());
    }

    return IRODataCollectionRequest.builder()
        .query(iroDataCollectionTaskItem.getDataInfo().getMetricInfo().get(0).getQuery())
        .startTime(Instant.ofEpochSecond(iroDataCollectionTaskItem.getStartTime()))
        .endTime(Instant.ofEpochSecond(iroDataCollectionTaskItem.getEndTime()))
        .tracingId(UUID.randomUUID().toString())
        .accountId(iroDataCollectionTaskItem.getAccountIdentifier())
        .orgIdentifier(iroDataCollectionTaskItem.getOrgIdentifier())
        .projectIdentifier(iroDataCollectionTaskItem.getProjectIdentifier())
        .dataCollectionTaskId(iroDataCollectionTaskItem.getId())
        .step(iroDataCollectionTaskItem.getStep())
        .queryMap(queryMap);
  }
}
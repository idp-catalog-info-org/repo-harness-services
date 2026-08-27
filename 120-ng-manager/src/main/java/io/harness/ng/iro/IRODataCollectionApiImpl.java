/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.spec.server.ng.v1.PrometheusDataRetrievalApi;
import io.harness.spec.server.ng.v1.model.PrometheusMetricsResponse;
import io.harness.spec.server.ng.v1.model.PrometheusQueryRequest;

import software.wings.delegatetasks.cv.IRODataCollectionTaskResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IRODataCollectionApiImpl implements PrometheusDataRetrievalApi {
  private final IRODataCollectionTaskService dataCollectionTaskService;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Inject
  public IRODataCollectionApiImpl(IRODataCollectionTaskService dataCollectionTaskService) {
    this.dataCollectionTaskService = dataCollectionTaskService;
  }

  @Override
  public Response getSyncDataCollectionResult(@Valid PrometheusQueryRequest prometheusQueryRequest,
      @NotNull String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    IRODataCollectionTaskResult iroDataCollectionTaskResult = null;
    try {
      iroDataCollectionTaskResult = dataCollectionTaskService.getDataCollectionResult(
          accountIdentifier, orgIdentifier, projectIdentifier, prometheusQueryRequest);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    try {
      PrometheusMetricsResponse prometheusMetricsResponse =
          OBJECT_MAPPER.readValue(iroDataCollectionTaskResult.getResponseData(), PrometheusMetricsResponse.class);
      return Response.status(Response.Status.OK).entity(prometheusMetricsResponse).build();
    } catch (JsonProcessingException e) {
      return Response.status(Response.Status.EXPECTATION_FAILED).entity(e).build();
    }
  }
}

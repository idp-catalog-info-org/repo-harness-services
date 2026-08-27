/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.service;

import io.harness.platform.query.service.api.v1.ExecuteQueryResponse;
import io.harness.queryservice.grpc.QueryResultMapper;
import io.harness.queryservice.grpc.QueryServiceClient;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;

public class IdpAnalyticsService {
  private final QueryServiceClient queryServiceClient;

  @Inject
  public IdpAnalyticsService(QueryServiceClient queryServiceClient) {
    this.queryServiceClient = queryServiceClient;
  }

  public List<Map<String, Object>> execute(String hql, String accountId) {
    ExecuteQueryResponse response = queryServiceClient.executeQuery(hql, accountId);
    return QueryResultMapper.toMapList(response.getResult());
  }
}

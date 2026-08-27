/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.inject.Singleton;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BigQueryServiceImpl implements BigQueryService {
  private static final String USER_AGENT_HEADER = "user-agent";
  private static final String USER_AGENT_HEADER_ENVIRONMENT_VARIABLE = "USER_AGENT_HEADER";
  private static final String DEFAULT_USER_AGENT = "default-user-agent";

  @Override
  public BigQuery get() {
    BigQueryOptions.Builder bigQueryOptionsBuilder;
    HeaderProvider headerProvider = getHeaderProvider();

    log.info("Initializing BQ");
    bigQueryOptionsBuilder = BigQueryOptions.newBuilder()
                                 .setProjectId(System.getenv("BIG_QUERY_GCP_PROJECT_ID"))
                                 .setHeaderProvider(headerProvider);

    return bigQueryOptionsBuilder.build().getService();
  }

  private HeaderProvider getHeaderProvider() {
    String userAgent = System.getenv(USER_AGENT_HEADER_ENVIRONMENT_VARIABLE);
    return FixedHeaderProvider.create(
        java.util.Map.of(USER_AGENT_HEADER, Objects.nonNull(userAgent) ? userAgent : DEFAULT_USER_AGENT));
  }
}

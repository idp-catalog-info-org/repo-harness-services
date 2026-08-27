/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.DslClientConfig;
import io.harness.idp.common.OkHttpClientConnectionPoolConfig;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

@OwnedBy(HarnessTeam.IDP)
public class DirectDslClientTestConfiguration extends AbstractModule {
  @Override
  protected void configure() {
    OkHttpClientConnectionPoolConfig connectionPoolConfig = OkHttpClientConnectionPoolConfig.builder()
                                                                .maxIdleConnections(5)
                                                                .keepAliveDuration(5L)
                                                                .timeUnit("MINUTES")
                                                                .build();

    DslClientConfig dslClientConfig =
        DslClientConfig.builder().connectTimeOutSeconds(30).readTimeOutSeconds(30).writeTimeOutSeconds(30).build();

    bind(OkHttpClientConnectionPoolConfig.class)
        .annotatedWith(Names.named("directDslClientHttpClientConnectionPoolConfig"))
        .toInstance(connectionPoolConfig);
    bind(DslClientConfig.class).annotatedWith(Names.named("dslClientConfig")).toInstance(dslClientConfig);
  }
}

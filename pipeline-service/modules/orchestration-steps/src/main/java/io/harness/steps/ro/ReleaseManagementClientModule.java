/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.ro;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Names;

@OwnedBy(HarnessTeam.PIPELINE)
public class ReleaseManagementClientModule extends AbstractModule {
  public static final String RELEASE_MANAGEMENT_EVENT_TYPE = "releaseManagementEventType";
  public static final String RELEASE_MANAGEMENT_MAX_ARTIFACTS_PER_TYPE = "releaseManagementMaxArtifactsPerType";

  private final ServiceHttpClientConfig config;
  private final String serviceSecret;
  private final String clientId;
  private final String eventType;
  private final int maxArtifactsPerType;

  public ReleaseManagementClientModule(ServiceHttpClientConfig config, String serviceSecret, String clientId,
      String eventType, int maxArtifactsPerType) {
    this.config = config;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.eventType = eventType;
    this.maxArtifactsPerType = maxArtifactsPerType;
  }

  @Provides
  @Singleton
  private ReleaseManagementClientFactory releaseManagementClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new ReleaseManagementClientFactory(this.config, this.serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, ClientMode.NON_PRIVILEGED);
  }

  @Override
  protected void configure() {
    this.bind(ReleaseManagementClient.class).toProvider(ReleaseManagementClientFactory.class).in(Scopes.SINGLETON);
    bind(String.class).annotatedWith(Names.named(RELEASE_MANAGEMENT_EVENT_TYPE)).toInstance(eventType);
    bind(Integer.class)
        .annotatedWith(Names.named(RELEASE_MANAGEMENT_MAX_ARTIFACTS_PER_TYPE))
        .toInstance(maxArtifactsPerType);
    bind(ArtifactsResolver.class).in(Singleton.class);
  }
}

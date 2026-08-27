/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.HarnessCacheManager;
import io.harness.code.CodeResourceClientModule;
import io.harness.ngtriggers.TriggerConfiguration;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.resource.NGTriggerEventHistoryResource;
import io.harness.ngtriggers.resource.NGTriggerEventHistoryResourceImpl;
import io.harness.ngtriggers.resource.NGTriggerResource;
import io.harness.ngtriggers.resource.NGTriggerResourceImpl;
import io.harness.ngtriggers.resource.NGTriggerWebhookConfigResource;
import io.harness.ngtriggers.resource.NGTriggerWebhookConfigResourceImpl;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerMonitorService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.service.NGTriggerWebhookRegistrationService;
import io.harness.ngtriggers.service.TriggerFailureNotificationDetailsService;
import io.harness.ngtriggers.service.impl.NGTriggerEventServiceImpl;
import io.harness.ngtriggers.service.impl.NGTriggerMonitorServiceImpl;
import io.harness.ngtriggers.service.impl.NGTriggerServiceImpl;
import io.harness.ngtriggers.service.impl.NGTriggerWebhookRegistrationServiceImpl;
import io.harness.ngtriggers.service.impl.SecretDecryptorViaNg;
import io.harness.ngtriggers.service.impl.TriggerFailureNotificationDetailsServiceImpl;
import io.harness.ngtriggers.utils.AwsCodeCommitDataObtainer;
import io.harness.ngtriggers.utils.GitProviderBaseDataObtainer;
import io.harness.ngtriggers.utils.SCMDataObtainer;
import io.harness.pipeline.remote.PipelineRemoteClientModule;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.runner.cgi.CgiConfigClientModule;
import io.harness.secrets.SecretDecryptor;
import io.harness.version.VersionInfoManager;
import io.harness.webhook.WebhookConfigProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;

@OwnedBy(PIPELINE)
public class NGTriggersModule extends AbstractModule {
  private static final AtomicReference<NGTriggersModule> instanceRef = new AtomicReference<>();
  private TriggerConfiguration triggerConfig;
  private ServiceHttpClientConfig pmsHttpClientConfig;
  private String pipelineServiceSecret;

  private ServiceHttpClientConfig harnessCodeHttpClientConfig;
  private String harnessCodeServiceSecret;
  private String harnessCodeGitBaseUrl;

  private ServiceHttpClientConfig ngManagerClientConfig;
  private String ngManagerServiceSecret;

  public static NGTriggersModule getInstance(TriggerConfiguration triggerConfig,
      ServiceHttpClientConfig pmsHttpClientConfig, String pipelineServiceSecret,
      ServiceHttpClientConfig harnessCodeHttpClientConfig, String harnessCodeGitBaseUrl,
      String harnessCodeServiceSecret, ServiceHttpClientConfig ngManagerClientConfig, String ngManagerServiceSecret) {
    if (instanceRef.get() == null) {
      instanceRef.compareAndSet(null,
          new NGTriggersModule(triggerConfig, pmsHttpClientConfig, pipelineServiceSecret, harnessCodeHttpClientConfig,
              harnessCodeGitBaseUrl, harnessCodeServiceSecret, ngManagerClientConfig, ngManagerServiceSecret));
    }
    return instanceRef.get();
  }

  private NGTriggersModule(TriggerConfiguration triggerConfig, ServiceHttpClientConfig pmsHttpClientConfig,
      String pipelineServiceSecret, ServiceHttpClientConfig harnessCodeHttpClientConfig, String harnessCodeGitBaseUrl,
      String harnessCodeServiceSecret, ServiceHttpClientConfig ngManagerClientConfig, String ngManagerServiceSecret) {
    this.triggerConfig = triggerConfig;
    this.pmsHttpClientConfig = pmsHttpClientConfig;
    this.pipelineServiceSecret = pipelineServiceSecret;
    this.harnessCodeHttpClientConfig = harnessCodeHttpClientConfig;
    this.harnessCodeGitBaseUrl = harnessCodeGitBaseUrl;
    this.harnessCodeServiceSecret = harnessCodeServiceSecret;
    this.ngManagerClientConfig = ngManagerClientConfig;
    this.ngManagerServiceSecret = ngManagerServiceSecret;
  }

  @Override
  protected void configure() {
    MapBinder<String, List<String>> variablesMapBinder =
        MapBinder.newMapBinder(binder(), new TypeLiteral<String>() {}, new TypeLiteral<List<String>>() {});
    // TODO(Harsh): add all trigger expressions in this list
    variablesMapBinder.addBinding("trigger").toInstance(TriggerHelper.getAllTriggerExpressions());

    install(SCMJavaClientModule.getInstance());
    bind(NGTriggerService.class).to(NGTriggerServiceImpl.class);
    bind(NGTriggerEventsService.class).to(NGTriggerEventServiceImpl.class);
    bind(NGTriggerWebhookRegistrationService.class).to(NGTriggerWebhookRegistrationServiceImpl.class);
    bind(NGTriggerEventsService.class).to(NGTriggerEventServiceImpl.class);
    bind(NGTriggerResource.class).to(NGTriggerResourceImpl.class);
    bind(NGTriggerEventHistoryResource.class).to(NGTriggerEventHistoryResourceImpl.class);
    bind(NGTriggerWebhookConfigResource.class).to(NGTriggerWebhookConfigResourceImpl.class);
    bind(SecretDecryptor.class).to(SecretDecryptorViaNg.class);
    bind(NGTriggerMonitorService.class).to(NGTriggerMonitorServiceImpl.class);
    bind(TriggerFailureNotificationDetailsService.class).to(TriggerFailureNotificationDetailsServiceImpl.class);
    bind(WebhookConfigProvider.class).toInstance(new WebhookConfigProvider() {
      @Override
      public String getCustomApiBaseUrl() {
        return triggerConfig.getCustomBaseUrl();
      }

      @Override
      public String getWebhookApiBaseUrl() {
        return triggerConfig.getWebhookBaseUrl();
      }
    });
    install(new PipelineRemoteClientModule(
        ServiceHttpClientConfig.builder().baseUrl(pmsHttpClientConfig.getBaseUrl()).build(), pipelineServiceSecret,
        PIPELINE_SERVICE.toString()));
    install(new CodeResourceClientModule(
        ServiceHttpClientConfig.builder().baseUrl(harnessCodeHttpClientConfig.getBaseUrl()).build(),
        harnessCodeServiceSecret, PIPELINE_SERVICE.toString(), ClientMode.PRIVILEGED));
    install(new CgiConfigClientModule(
        ngManagerClientConfig, ngManagerServiceSecret, PIPELINE_SERVICE.toString(), ClientMode.PRIVILEGED, false));
    MapBinder<String, GitProviderBaseDataObtainer> gitProviderBaseDataObtainerMap =
        MapBinder.newMapBinder(binder(), String.class, GitProviderBaseDataObtainer.class);
    gitProviderBaseDataObtainerMap.addBinding(WebhookSourceRepo.AWS_CODECOMMIT.name())
        .to(AwsCodeCommitDataObtainer.class);
    gitProviderBaseDataObtainerMap.addBinding(WebhookSourceRepo.AZURE_REPO.name()).to(SCMDataObtainer.class);
    gitProviderBaseDataObtainerMap.addBinding(WebhookSourceRepo.GITHUB.name()).to(SCMDataObtainer.class);
    gitProviderBaseDataObtainerMap.addBinding(WebhookSourceRepo.BITBUCKET.name()).to(SCMDataObtainer.class);
    gitProviderBaseDataObtainerMap.addBinding(WebhookSourceRepo.GITLAB.name()).to(SCMDataObtainer.class);
    // todo (abhinav): for advanced webhook details fix this.
    //    gitProviderBaseDataObtainerMap.addBinding(WebhookSourceRepo.HARNESS.name()).to(SCMDataObtainer.class);
  }

  @Provides
  @Named("harnessCodeServiceSecret")
  public String getHarnessCodeServiceSecret() {
    return this.harnessCodeServiceSecret;
  }

  @Provides
  @Named("harnessCodeApiUrl")
  public String getHarnessCodeApiUrl() {
    return this.harnessCodeHttpClientConfig.getBaseUrl();
  }

  @Provides
  @Named("harnessCodeGitBaseUrl")
  public String getHarnessCodeGitBaseUrl() {
    return this.harnessCodeGitBaseUrl;
  }

  @Provides
  @Singleton
  @Named("triggersMetricsCache")
  public Cache<String, Integer> metricsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("triggersMetricsCache", String.class, Integer.class,
        AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 6)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.authorization.AuthorizationServiceHeader.ACCESS_CONTROL_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.CI_MANAGER;
import static io.harness.eventsframework.EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.DEFAULT_READ_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.OBSERVER_EVENT_CHANNEL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELEGATE_ENTITY;
import static io.harness.lock.DistributedLockImplementation.REDIS;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_POLL_CONFIGURATION;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListenerNonVersioned.NG_ORCHESTRATION;

import io.harness.AccessControlClientModule;
import io.harness.SCMJavaClientModule;
import io.harness.account.AccountClientModule;
import io.harness.aitestautomation.client.AiTestAutomationClientModule;
import io.harness.aitestautomation.client.AitGcpFeatureFlagChecker;
import io.harness.aitestautomation.service.AiTestAutomationService;
import io.harness.aitestautomation.service.AiTestAutomationServiceImpl;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.app.impl.CIYamlSchemaServiceImpl;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsClientImpl;
import io.harness.aws.v2.ec2.Ec2V2Client;
import io.harness.aws.v2.ec2.Ec2V2ClientImpl;
import io.harness.aws.v2.ecr.EcrV2Client;
import io.harness.aws.v2.ecr.EcrV2ClientImpl;
import io.harness.beans.FeatureName;
import io.harness.beans.HarnessCodeServiceConfig;
import io.harness.beans.LlmGatewayConfig;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.cache.HarnessCacheManager;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.ci.CIExecutionServiceModule;
import io.harness.ci.api.aitestautomation.AiTestAutomationCIBuildCallbackService;
import io.harness.ci.api.aitestautomation.AiTestAutomationCIBuildCallbackServiceImpl;
import io.harness.ci.api.aitestautomation.AiTestAutomationCICallbackService;
import io.harness.ci.api.aitestautomation.AiTestAutomationCICallbackServiceImpl;
import io.harness.ci.app.intfc.CIYamlSchemaService;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.cache.CICacheManagementService;
import io.harness.ci.cache.CICacheManagementServiceImpl;
import io.harness.ci.cacheserviceclient.CacheServiceClientModule;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.ci.cd.service.EnvironmentEntityServiceImpl;
import io.harness.ci.cd.service.EnvironmentGroupService;
import io.harness.ci.cd.service.EnvironmentGroupServiceImpl;
import io.harness.ci.cd.service.InfrastructureEntityService;
import io.harness.ci.cd.service.InfrastructureEntityServiceImpl;
import io.harness.ci.cd.service.ServiceEntityService;
import io.harness.ci.cd.service.ServiceEntityServiceImpl;
import io.harness.ci.coverage.CoverageServiceConfig;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.enforcement.CIBuildEnforcerImpl;
import io.harness.ci.execution.aitestautomation.AiTestAutomationCIService;
import io.harness.ci.execution.aitestautomation.AiTestAutomationCIServiceImpl;
import io.harness.ci.execution.buildstate.SecretDecryptorViaNg;
import io.harness.ci.execution.execution.DelegateTaskEventListener;
import io.harness.ci.execution.execution.artifactDetails.ArtifactDetailsService;
import io.harness.ci.execution.execution.artifactDetails.ArtifactDetailsServiceImpl;
import io.harness.ci.execution.queue.CICapacityTaskMessageProcessorImpl;
import io.harness.ci.execution.queue.CIInitTaskMessageProcessorImpl;
import io.harness.ci.execution.queue.CITaskMessageProcessor;
import io.harness.ci.execution.states.rollback.StageRollbackDataService;
import io.harness.ci.execution.states.rollback.StageRollbackDataServiceImpl;
import io.harness.ci.execution.validation.CIAccountValidationService;
import io.harness.ci.execution.validation.CIAccountValidationServiceImpl;
import io.harness.ci.execution.validation.CIYAMLSanitizationService;
import io.harness.ci.execution.validation.CIYAMLSanitizationServiceImpl;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.ff.impl.CIFeatureFlagServiceImpl;
import io.harness.ci.licensing.CILicenseUsageImpl;
import io.harness.ci.logserviceclient.CILogServiceClientModule;
import io.harness.ci.permission.PipelinePermissionMapperModule;
import io.harness.ci.plugin.CiPluginStepInfoProvider;
import io.harness.ci.savings.CISavingsService;
import io.harness.ci.savings.CISavingsServiceImpl;
import io.harness.ci.tiserviceclient.TIServiceClientModule;
import io.harness.ci.traceableagenttoken.CITraceableAgentTokenService;
import io.harness.ci.traceableagenttoken.impl.CITraceableAgentTokenServiceImpl;
import io.harness.ci.utils.CIAnnotationsServiceClientModule;
import io.harness.ci.utils.ConnectorSecretExtractor;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.cistatus.service.azurerepo.AzureRepoServiceImpl;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.cistatus.service.bitbucket.BitbucketServiceImpl;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.cistatus.service.gitlab.GitlabServiceImpl;
import io.harness.code.CodeResourceClientModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.core.ci.dashboard.BuildNumberService;
import io.harness.core.ci.dashboard.BuildNumberServiceImpl;
import io.harness.core.ci.dashboard.CIOverviewDashboardService;
import io.harness.core.ci.dashboard.CIOverviewDashboardServiceImpl;
import io.harness.creditcard.CreditCardClientModule;
import io.harness.cvng.client.HealthSourceResourceClientModule;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.envgroup.EnvironmentGroupResourceClientModule;
import io.harness.environment.EnvironmentResourceClientModule;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.redis.GitAwareRedisProducer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.ff.FeatureFlagModule;
import io.harness.filestore.FileStoreClientModule;
import io.harness.fulcio.HarnessFulcioServiceClientModule;
import io.harness.gcp.client.GcpClient;
import io.harness.gcp.impl.GcpClientImpl;
import io.harness.goconvert.GoConvertGrpcClientModule;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.client.AbstractManagerGrpcClientModule;
import io.harness.grpc.client.ManagerGrpcClientModule;
import io.harness.harnessid.client.HarnessIdClientModule;
import io.harness.harnessid.client.HarnessIdServiceConfig;
import io.harness.hsa.beans.HSAServiceConfig;
import io.harness.iacmserviceclient.IACMServiceClientModule;
import io.harness.impl.ScmServiceClientImpl;
import io.harness.infrastructure.InfrastructureResourceClientModule;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.logstreaming.LogStreamingModule;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.logstreaming.NGLogStreamingClientFactory;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.mongo.MongoPersistence;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngsettings.client.remote.NGSettingsClientModule;
import io.harness.oidc.OidcResourceClientModule;
import io.harness.opaclient.OpaClientModule;
import io.harness.outbox.module.TransactionOutboxModule;
import io.harness.overrides.OverrideResourceClientModule;
import io.harness.persistence.HPersistence;
import io.harness.pms.sdk.core.plugin.PluginInfoProvider;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.privateconnectivity.PrivateConnectivityResourceClientModule;
import io.harness.proc.BashProcessExecutor;
import io.harness.proc.ProcessExecutor;
import io.harness.project.ProjectClientModule;
import io.harness.qwietserviceclient.QwietServiceClientModule;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.runner.cgi.CgiConfigClientModule;
import io.harness.runner.plugin.PluginConfigClientModule;
import io.harness.scopeinfoclient.ScopeInfoClientModule;
import io.harness.secrets.SecretDecryptor;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.service.ScmServiceClient;
import io.harness.ssca.SSCAManagerServiceClientModule;
import io.harness.steps.shellscript.ShellScriptHelperServiceImplOld;
import io.harness.steps.shellscript.ShellScriptHelperServiceOld;
import io.harness.stoserviceclient.STOServiceClientModule;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.threading.ScalingThreadPoolExecutor;
import io.harness.threading.ThreadPool;
import io.harness.threading.ThreadPoolConfig;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.TimeScaleDBServiceImpl;
import io.harness.token.TokenClientModule;
import io.harness.transientData.TransientExecutionDataModule;
import io.harness.tunnel.TunnelResourceClientModule;
import io.harness.unified.depoloymentfreeze.NgDeploymentFreezeResourceClientModule;
import io.harness.unified.service.NgServiceResourceClientModule;
import io.harness.user.UserClientModule;
import io.harness.utils.ConnectorSecretProvider;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.AsyncWaitEngineImpl;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.StepSpecType;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.jackson.Jackson;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_COMMON_STEPS, HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class CIManagerServiceModule extends AbstractModule {
  private final CIManagerConfiguration ciManagerConfiguration;
  protected final CIManagerConfigurationOverride configurationOverride;

  public CIManagerServiceModule(
      CIManagerConfiguration ciManagerConfiguration, CIManagerConfigurationOverride configurationOverride) {
    this.ciManagerConfiguration = ciManagerConfiguration;
    this.configurationOverride = configurationOverride;
  }

  @Provides
  @Singleton
  AitGcpFeatureFlagChecker provideAitGcpFeatureFlagChecker(CIFeatureFlagService ciFeatureFlagService) {
    return accountId -> ciFeatureFlagService.isEnabled(FeatureName.AIT_GCP_ENDPOINT_ENABLED, accountId);
  }

  @Provides
  @Singleton
  Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
      DelegateServiceGrpcClient delegateServiceGrpcClient) {
    return (Supplier<DelegateCallbackToken>) Suppliers.memoize(
        () -> getDelegateCallbackToken(delegateServiceGrpcClient, ciManagerConfiguration));
  }

  @Provides
  @Singleton
  @Named("GitAwareEntityHelperExecutorService")
  public ExecutorService gitAwareEntityHelperExecutorService() {
    return ThreadPool.getInstrumentedExecutorService(ciManagerConfiguration.getGitAwareEntityHelperPoolConfig(),
        "GitAwareEntityHelperExecutorService", new MetricRegistry());
  }

  // Final url returned from this fn would be: https://pr.harness.io/ci-delegate-upgrade/ng/#
  @Provides
  @Singleton
  @Named("ngBaseUrl")
  String getNgBaseUrl() {
    String apiUrl = ciManagerConfiguration.getApiUrl();
    if (apiUrl.endsWith("/")) {
      return apiUrl.substring(0, apiUrl.length() - 1);
    }
    return apiUrl;
  }

  @Provides
  @Singleton
  @Named("harnessCodeGitBaseUrl")
  String getHarnessCodeGitBaseUrl() {
    String gitUrl = ciManagerConfiguration.getHarnessCodeGitUrl();
    if (gitUrl.endsWith("/")) {
      return gitUrl.substring(0, gitUrl.length() - 1);
    }
    return gitUrl;
  }

  @Provides
  @Singleton
  CoverageServiceConfig getCoverageConfig() {
    return ciManagerConfiguration.getCoverageServiceConfig();
  }

  private DelegateCallbackToken getDelegateCallbackToken(
      DelegateServiceGrpcClient delegateServiceClient, CIManagerConfiguration appConfig) {
    log.info("Generating Delegate callback token");
    String overrideMongoUri = configurationOverride.getMongoUri();
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix(configurationOverride.getModulePrefix() + "Manager")
                                  .setConnection(overrideMongoUri.isEmpty() ? appConfig.getHarnessCIMongo().getUri()
                                                                            : overrideMongoUri)
                                  .build())
            .setNewCallbackFlow(appConfig.isEnableWaitNotifyEngineOptimisation())
            .build());
    log.info("Delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    CIManagerApplication.configureObjectMapper(objectMapper);
    return objectMapper;
  }

  @Provides
  @Named("yaml-schema-subtypes")
  @Singleton
  public Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes() {
    Set<Class<? extends StepSpecType>> subTypesOfStepSpecType =
        HarnessReflections.get().getSubTypesOf(StepSpecType.class);
    Set<Class<?>> set = new HashSet<>(subTypesOfStepSpecType);

    return ImmutableMap.of(StepSpecType.class, set);
  }

  @Provides
  @Singleton
  public AsyncWaitEngine asyncWaitEngine(WaitNotifyEngine waitNotifyEngine) {
    return new AsyncWaitEngineImpl(waitNotifyEngine, this.configurationOverride.getModulePrefix() + "_orchestration");
  }

  @Provides
  @Singleton
  @Named("queueAsyncWaitEngine")
  public AsyncWaitEngine asyncWaitEngineQueue(WaitNotifyEngine waitNotifyEngine) {
    return new AsyncWaitEngineImpl(waitNotifyEngine, NG_ORCHESTRATION);
  }
  @Provides
  @Singleton
  public TimeLimiter timeLimiter(ExecutorService executorService) {
    return HTimeLimiter.create(executorService);
  }

  @Provides
  @Singleton
  @Named("harnessArtifactRegistryUrl")
  public String harnessArtifactRegistryUrl() {
    String registryUrl = ciManagerConfiguration.getCiExecutionServiceConfig()
                             .getHarnessRegistryConfig()
                             .getHttpClientConfig()
                             .getBaseUrl();
    if (registryUrl.endsWith("/")) {
      return registryUrl.substring(0, registryUrl.length() - 1);
    }
    return registryUrl;
  }

  @Provides
  @Singleton
  LogStreamingServiceConfiguration getLogStreamingServiceConfiguration() {
    LogServiceConfig logConfig = ciManagerConfiguration.getLogServiceConfig();
    return LogStreamingServiceConfiguration.builder()
        .baseUrl(logConfig.getBaseUrl())
        .serviceToken(logConfig.getGlobalToken())
        .build();
  }

  @Provides
  @Singleton
  @Named("logStreamingDelayExecutor")
  public ScheduledExecutorService logStreamingDelayExecutor() {
    ThreadPoolConfig poolConfig = ciManagerConfiguration.getLogStreamingExecutorPoolConfig() != null
        ? ciManagerConfiguration.getLogStreamingExecutorPoolConfig()
        : ThreadPoolConfig.builder().corePoolSize(10).build();
    return new ScheduledThreadPoolExecutor(poolConfig.getCorePoolSize(),
        new ThreadFactoryBuilder().setNameFormat("ci-log-client-pool-%d").setPriority(Thread.NORM_PRIORITY).build());
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return ciManagerConfiguration.getDistributedLockImplementation() == null
        ? REDIS
        : ciManagerConfiguration.getDistributedLockImplementation();
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisConfig() {
    return ciManagerConfiguration.getRedisLockConfig();
  }
  @Provides
  @Singleton
  @Named("ciEventsCache")
  public Cache<String, Integer> sdkEventsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("ciEventsCache", String.class, Integer.class,
        AccessedExpiryPolicy.factoryOf(Duration.THIRTY_MINUTES), versionInfoManager.getVersionInfo().getBuildNo(),
        true);
  }
  @Override
  protected void configure() {
    if (this.configurationOverride.isUsePrimaryVersionController()) {
      install(PrimaryVersionManagerModule.getInstance());
    }
    if (this.configurationOverride.isUseBuildEnforcer()) {
      bind(CIBuildEnforcer.class).to(CIBuildEnforcerImpl.class);
    }
    String serviceId = this.configurationOverride.getServiceHeader().getServiceId();

    bind(ProcessExecutor.class).to(BashProcessExecutor.class);
    bind(CIManagerConfiguration.class).toInstance(ciManagerConfiguration);
    bind(CITaskMessageProcessor.class)
        .annotatedWith(Names.named("ciInitTaskMessageProcessor"))
        .to(CIInitTaskMessageProcessorImpl.class);
    bind(CITaskMessageProcessor.class)
        .annotatedWith(Names.named("ciCapacityTaskMessageProcessor"))
        .to(CICapacityTaskMessageProcessorImpl.class);
    bind(HPersistence.class).to(MongoPersistence.class).in(Singleton.class);
    bind(BuildNumberService.class).to(BuildNumberServiceImpl.class);
    bind(CIYamlSchemaService.class).to(CIYamlSchemaServiceImpl.class).in(Singleton.class);
    bind(CIFeatureFlagService.class).to(CIFeatureFlagServiceImpl.class).in(Singleton.class);
    bind(CILicenseService.class).to(this.configurationOverride.getLicenseClass()).in(Singleton.class);
    bind(CIOverviewDashboardService.class).to(CIOverviewDashboardServiceImpl.class);
    bind(ArtifactDetailsService.class).to(ArtifactDetailsServiceImpl.class);
    bind(CICacheManagementService.class).to(CICacheManagementServiceImpl.class);
    bind(CISavingsService.class).to(CISavingsServiceImpl.class);
    bind(LicenseUsageInterface.class).to(CILicenseUsageImpl.class);
    bind(ScmServiceClient.class).to(ScmServiceClientImpl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(GitlabService.class).to(GitlabServiceImpl.class);
    bind(BitbucketService.class).to(BitbucketServiceImpl.class);
    bind(AzureRepoService.class).to(AzureRepoServiceImpl.class);
    bind(SecretDecryptor.class).to(SecretDecryptorViaNg.class);
    bind(CIYAMLSanitizationService.class).to(CIYAMLSanitizationServiceImpl.class).in(Singleton.class);
    bind(CIAccountValidationService.class).to(CIAccountValidationServiceImpl.class).in(Singleton.class);
    bind(EnvironmentEntityService.class).to(EnvironmentEntityServiceImpl.class);
    bind(EnvironmentGroupService.class).to(EnvironmentGroupServiceImpl.class);
    bind(InfrastructureEntityService.class).to(InfrastructureEntityServiceImpl.class);
    bind(ServiceEntityService.class).to(ServiceEntityServiceImpl.class);
    bind(StageRollbackDataService.class).to(StageRollbackDataServiceImpl.class);
    bind(ShellScriptHelperServiceOld.class).to(ShellScriptHelperServiceImplOld.class);
    bind(ConnectorSecretProvider.class).to(ConnectorSecretExtractor.class);
    bind(CITraceableAgentTokenService.class).to(CITraceableAgentTokenServiceImpl.class).in(Singleton.class);

    // Provide FileStoreClient used by ShellScriptHelperServiceImplOld
    install(new FileStoreClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId));

    // Workload Identity (OIDC-without-connector): install the HarnessID client so the CI Run-step
    // serializer can register workload identities. Lazy providers keep startup safe when unconfigured
    // (isEnabled() stays false until the gRPC target, REST base URL, and service-auth secret are all set).
    install(new HarnessIdClientModule(HarnessIdServiceConfig.builder()
                                          .grpcClientConfig(ciManagerConfiguration.getHarnessIdGrpcClientConfig())
                                          .restClientConfig(ciManagerConfiguration.getHarnessIdRestClientConfig())
                                          .callerServiceName(CI_MANAGER)
                                          .build()));

    // Provide PmsFeatureFlagService used by ShellScriptHelperServiceImplOld
    bind(PmsFeatureFlagService.class).to(PmsFeatureFlagHelper.class);

    install(NgLicenseHttpClientModule.getInstance(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId));
    ServiceHttpClientConfig platformConfigServiceConfig = ciManagerConfiguration.isPlatformConfigServiceEnabled()
        ? ciManagerConfiguration.getPlatformConfigServiceClientConfig()
        : ciManagerConfiguration.getNgManagerClientConfig();
    String platformConfigServiceSecret = ciManagerConfiguration.isPlatformConfigServiceEnabled()
        ? ciManagerConfiguration.getPlatformConfigServiceSecret()
        : ciManagerConfiguration.getNgManagerServiceSecret();
    install(new NGSettingsClientModule(platformConfigServiceConfig, platformConfigServiceSecret, serviceId, false));
    install(new PipelinePermissionMapperModule());
    install(new CgiConfigClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId, ClientMode.PRIVILEGED, false));
    install(new PluginConfigClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId, ClientMode.PRIVILEGED, false));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("ciInitTaskExecutor"))
        .toInstance(new ScalingThreadPoolExecutor(
            ThreadPoolConfig.builder().corePoolSize(10).maxPoolSize(50).idleTime(5).timeUnit(TimeUnit.SECONDS).build(),
            "Init-Task-Handler-%d"));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("ciTelemetryPublisherExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("ci-telemetry-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("stoTelemetryPublisherExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("sto-telemetry-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("pluginMetadataPublishExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("plugin-metadata-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named(this.configurationOverride.getModulePrefix() + "DataDeleteScheduler"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat(this.configurationOverride.getModulePrefix() + "-data-delete-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
    bind(AwsClient.class).to(AwsClientImpl.class);
    bind(Ec2V2Client.class).to(Ec2V2ClientImpl.class);
    bind(EcrV2Client.class).to(EcrV2ClientImpl.class);
    bind(GcpClient.class).to(GcpClientImpl.class);
    Multibinder<PluginInfoProvider> pluginInfoProviderMultibinder =
        Multibinder.newSetBinder(binder(), new TypeLiteral<PluginInfoProvider>() {});
    pluginInfoProviderMultibinder.addBinding().to(CiPluginStepInfoProvider.class);
    registerEventListeners();
    try {
      bind(TimeScaleDBService.class)
          .toConstructor(TimeScaleDBServiceImpl.class.getConstructor(TimeScaleDBConfig.class));
    } catch (NoSuchMethodException e) {
      log.error("TimeScaleDbServiceImpl Initialization Failed in due to missing constructor", e);
    }
    if (ciManagerConfiguration.getEnableDashboardTimescale() != null
        && ciManagerConfiguration.getEnableDashboardTimescale()) {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(ciManagerConfiguration.getTimeScaleDBConfig() != null
                  ? ciManagerConfiguration.getTimeScaleDBConfig()
                  : TimeScaleDBConfig.builder().build());
    } else {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(TimeScaleDBConfig.builder().build());
    }

    // Keeping it to 1 thread to start with. Assuming executor service is used only to
    // serve health checks. If it's being used for other tasks also, max pool size should be increased.
    ThreadFactory threadFactory =
        new ThreadFactoryBuilder()
            .setNameFormat("default-" + this.configurationOverride.getModulePrefix() + "-executor-%d")
            .setPriority(Thread.MIN_PRIORITY)
            .build();
    bind(ExecutorService.class)
        .toInstance(new ScalingThreadPoolExecutor(
            ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(2).idleTime(5).timeUnit(TimeUnit.SECONDS).build(),
            threadFactory));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("async-taskPollExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(
            ciManagerConfiguration.getAsyncDelegateResponseConsumption().getCorePoolSize(),
            new ThreadFactoryBuilder()
                .setNameFormat("async-taskPollExecutor-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("TaskPoll-Thread"));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("resourceCleanupExecutor"))
        .toInstance(
            new ScheduledThreadPoolExecutor(ciManagerConfiguration.getAsyncResourceCleanupPool().getCorePoolSize(),
                new ThreadFactoryBuilder()
                    .setNameFormat("Resource-Cleanup-Thread-%d")
                    .setPriority(Thread.NORM_PRIORITY)
                    .build()));
    install(TransientExecutionDataModule.getInstance());

    install(new CIExecutionServiceModule(
        ciManagerConfiguration.getCiExecutionServiceConfig(), ciManagerConfiguration.getShouldConfigureWithPMS()));
    bind(HarnessCodeServiceConfig.class)
        .toInstance(
            HarnessCodeServiceConfig.builder()
                .serviceSecret(ciManagerConfiguration.getCiExecutionServiceConfig().getGitnessConfig().getJwtSecret())
                .gitUrl(ciManagerConfiguration.getHarnessCodeGitUrl())
                .apiUrl(ciManagerConfiguration.getCiExecutionServiceConfig()
                            .getGitnessConfig()
                            .getHttpClientConfig()
                            .getBaseUrl())
                .build());
    // Harness-managed LLM gateway endpoint (env-specific). Empty when unset -> ConnectorInputsMapper injects nothing.
    bind(LlmGatewayConfig.class)
        .toInstance(LlmGatewayConfig.builder().baseUrl(ciManagerConfiguration.getLlmGatewayBaseUrl()).build());
    install(DelegateServiceDriverModule.getInstance(false, true));
    install(new DelegateServiceDriverGrpcClientModule(ciManagerConfiguration.getManagerServiceSecret(),
        ciManagerConfiguration.getManagerTarget(), ciManagerConfiguration.getManagerAuthority(), true));

    install(new TokenClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId));
    install(PersistentLockModule.getInstance());
    install(new OpaClientModule(ciManagerConfiguration.getOpaClientConfig(),
        ciManagerConfiguration.getPolicyManagerSecret(), serviceId, false));

    install(new AbstractManagerGrpcClientModule() {
      @Override
      public ManagerGrpcClientModule.Config config() {
        return ManagerGrpcClientModule.Config.builder()
            .target(ciManagerConfiguration.getManagerTarget())
            .authority(ciManagerConfiguration.getManagerAuthority())
            .build();
      }

      @Override
      public String application() {
        return serviceId;
      }
    });
    install(new SCMJavaClientModule());
    install(
        AccessControlClientModule.getInstance(ciManagerConfiguration.getAccessControlClientConfiguration(), serviceId));
    install(new EntitySetupUsageClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId));
    ServiceHttpClientConfig secretConnectorServiceConfig = ciManagerConfiguration.isSecretConnectorServiceEnabled()
        ? ciManagerConfiguration.getSecretConnectorServiceClientConfig()
        : ciManagerConfiguration.getNgManagerClientConfig();
    String secretConnectorServiceSecret = ciManagerConfiguration.isSecretConnectorServiceEnabled()
        ? ciManagerConfiguration.getSecretConnectorServiceSecret()
        : ciManagerConfiguration.getNgManagerServiceSecret();
    install(new ConnectorResourceClientModule(
        secretConnectorServiceConfig, secretConnectorServiceSecret, serviceId, ClientMode.PRIVILEGED));
    install(new OidcResourceClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId, ClientMode.PRIVILEGED));
    install(new SecretNGManagerClientModule(secretConnectorServiceConfig, secretConnectorServiceSecret, serviceId));
    install(new CILogServiceClientModule(ciManagerConfiguration.getLogServiceConfig()));
    install(new CIAnnotationsServiceClientModule(ciManagerConfiguration.getAnnotationsConfig()));
    install(UserClientModule.getInstance(
        ciManagerConfiguration.getManagerClientConfig(), ciManagerConfiguration.getManagerServiceSecret(), serviceId));
    install(
        new TransactionOutboxModule(DEFAULT_OUTBOX_POLL_CONFIGURATION, ACCESS_CONTROL_SERVICE.getServiceId(), false));
    ServiceHttpClientConfig rhsConfig = ciManagerConfiguration.isRhsEnabled()
        ? ciManagerConfiguration.getRhsClientConfig()
        : ciManagerConfiguration.getNgManagerClientConfig();
    String rhsSecret = ciManagerConfiguration.isRhsEnabled() ? ciManagerConfiguration.getRhsServiceSecret()
                                                             : ciManagerConfiguration.getNgManagerServiceSecret();
    install(new ProjectClientModule(rhsConfig, rhsSecret, serviceId));
    install(new CreditCardClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId));
    install(new TIServiceClientModule(ciManagerConfiguration.getTiServiceConfig()));
    install(new CacheServiceClientModule(ciManagerConfiguration.getCacheServiceConfig()));
    install(new STOServiceClientModule(ciManagerConfiguration.getStoServiceConfig()));
    install(new QwietServiceClientModule(ciManagerConfiguration.getQwietServiceConfig()));
    install(new SSCAManagerServiceClientModule(ciManagerConfiguration.getSscaServiceConfig().getHttpClientConfig(),
        ciManagerConfiguration.getSscaServiceConfig().getServiceSecret(), serviceId));
    if (ciManagerConfiguration.getHsaServiceConfig() != null) {
      bind(HSAServiceConfig.class).toInstance(ciManagerConfiguration.getHsaServiceConfig());
    }
    install(new HarnessFulcioServiceClientModule(
        ciManagerConfiguration.getHarnessFulcioServiceConfig().getHttpClientConfig(),
        ciManagerConfiguration.getHarnessFulcioServiceConfig().getServiceSecret(), serviceId));
    install(new IACMServiceClientModule(ciManagerConfiguration.getIacmServiceConfig()));
    install(new ScopeInfoClientModule(rhsConfig, rhsSecret, CI_MANAGER.getServiceId()));
    install(new AccountClientModule(ciManagerConfiguration.getManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), this.configurationOverride.getServiceHeader().toString()));
    install(EnforcementClientModule.getInstance(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId,
        ciManagerConfiguration.getEnforcementClientConfiguration()));
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return ciManagerConfiguration.getSegmentConfiguration();
      }
    });
    install(new CICacheRegistrar());
    install(new CodeResourceClientModule(
        ciManagerConfiguration.getCiExecutionServiceConfig().getGitnessConfig().getHttpClientConfig(),
        ciManagerConfiguration.getCiExecutionServiceConfig().getGitnessConfig().getJwtSecret(), serviceId,
        ClientMode.PRIVILEGED));
    install(FeatureFlagModule.getInstance());
    install(new TunnelResourceClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId));
    install(new PrivateConnectivityResourceClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId, ClientMode.PRIVILEGED));
    install(new NgServiceResourceClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId));
    install(new OverrideResourceClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId));
    install(new InfrastructureResourceClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId));
    install(new NgDeploymentFreezeResourceClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId));
    install(new EnvironmentResourceClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId));
    install(new EnvironmentGroupResourceClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), serviceId));
    install(HealthSourceResourceClientModule.getInstance(
        ciManagerConfiguration.getCvngClientConfig(), ciManagerConfiguration.getCvngServiceSecret(), serviceId));
    install(new AiTestAutomationClientModule(ciManagerConfiguration.getAiTestAutomationClientConfig(),
        ciManagerConfiguration.getAitGcpClientConfig(), ciManagerConfiguration.getAiTestAutomationServiceSecret(),
        serviceId));
    bind(AiTestAutomationService.class).to(AiTestAutomationServiceImpl.class).in(Singleton.class);
    bind(AiTestAutomationCIService.class).to(AiTestAutomationCIServiceImpl.class);
    bind(AiTestAutomationCICallbackService.class).to(AiTestAutomationCICallbackServiceImpl.class);
    bind(AiTestAutomationCIBuildCallbackService.class).to(AiTestAutomationCIBuildCallbackServiceImpl.class);

    if (ciManagerConfiguration.getGoConvertConnectionConfig() != null) {
      install(new GoConvertGrpcClientModule(ciManagerConfiguration.getGoConvertConnectionConfig()));
    }

    LogServiceConfig logConfig = ciManagerConfiguration.getLogServiceConfig();
    install(new LogStreamingModule(logConfig.getBaseUrl()));
    bind(LogStreamingServiceRestClient.class)
        .toProvider(NGLogStreamingClientFactory.builder().logStreamingServiceBaseUrl(logConfig.getBaseUrl()).build());
  }

  private void registerEventListeners() {
    final RedisConfig redisConfig = ciManagerConfiguration.getEventsFrameworkConfiguration().getRedisConfig();
    String orchestrationEvent = this.configurationOverride.getOrchestrationEvent();
    String serviceId = this.configurationOverride.getServiceHeader().getServiceId();

    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Consumer.class)
          .annotatedWith(Names.named(OBSERVER_EVENT_CHANNEL))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));

    } else {
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);
      bind(Consumer.class)
          .annotatedWith(Names.named(OBSERVER_EVENT_CHANNEL))
          .toInstance(RedisConsumer.of(OBSERVER_EVENT_CHANNEL, serviceId, redissonClient, DEFAULT_MAX_PROCESSING_TIME,
              DEFAULT_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(ENTITY_CRUD))
          .toInstance(RedisConsumer.of(ENTITY_CRUD, serviceId, redissonClient, DEFAULT_MAX_PROCESSING_TIME,
              DEFAULT_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));

      bind(MessageListener.class)
          .annotatedWith(Names.named(DELEGATE_ENTITY + OBSERVER_EVENT_CHANNEL))
          .to(DelegateTaskEventListener.class);
      bind(MessageListener.class)
          .annotatedWith(Names.named(ACCOUNT_ENTITY + ENTITY_CRUD))
          .to(this.configurationOverride.getAccountEntityListenerClass());

      bind(Producer.class)
          .annotatedWith(Names.named(orchestrationEvent))
          .toInstance(GitAwareRedisProducer.of(
              orchestrationEvent, redissonClient, 5000, serviceId, redisConfig.getEnvNamespace()));

      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.LICENSES_USAGE_REDIS_EVENT_CONSUMER))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.LICENSES_USAGE_REDIS_EVENT_CONSUMER, redissonClient,
              EventsFrameworkConstants.LICENSE_USAGE_EVENT_MAX_TOPIC_SIZE, serviceId, redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(orchestrationEvent))
          .toInstance(RedisConsumer.of(orchestrationEvent, serviceId, redissonClient,
              EventsFrameworkConstants.PLAN_NOTIFY_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
    }
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.app;

import static io.harness.NGConstants.X_API_KEY;
import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.authorization.AuthorizationServiceHeader.DEFAULT;
import static io.harness.authorization.AuthorizationServiceHeader.IDP_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.BACKSTAGE_CATALOG_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.BACKSTAGE_SCAFFOLDER_TASKS_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_APP_CONFIGS_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_ENTITY_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_API_ENDPOINT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_REFERENCED_ENTITIES_SYNC;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_STO_ENRICHMENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CHECKS_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_INTEGRATION_CRUD_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_KIND_PROCESSOR_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_RELATIONSHIP_PROCESSING_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_SCORECARDS_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.MODULE_LICENSES_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.PROJECT_MOVEMENT;
import static io.harness.eventsframework.EventsFrameworkConstants.PROJECT_TOPOLOGY_REBUILD;
import static io.harness.eventsframework.EventsFrameworkConstants.STO_SCAN_COMPLETION_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.USERMEMBERSHIP;
import static io.harness.idp.app.IdpConfiguration.HARNESS_RESOURCE_CLASSES;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;

import io.harness.Microservice;
import io.harness.ModuleType;
import io.harness.PipelineServiceUtilityModule;
import io.harness.SCMGrpcClientModule;
import io.harness.accesscontrol.NGAccessDeniedExceptionMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoFactory;
import io.harness.cache.CacheModule;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.ci.execution.execution.OrchestrationExecutionEventHandlerRegistrar;
import io.harness.ci.execution.plan.creator.CIModuleInfoProvider;
import io.harness.ci.registrars.ExecutionAdvisers;
import io.harness.dropwizard.bundles.files.FileAssetsBundle;
import io.harness.dropwizard.bundles.swagger.SwaggerBundleConfiguration;
import io.harness.dropwizard.bundles.swagger.SwaggerV2Bundle;
import io.harness.enforcement.client.custom.CustomRestrictionInterface;
import io.harness.enforcement.client.resources.EnforcementClientResource;
import io.harness.enforcement.client.servicedependencies.CustomRestrictionRegisterConfiguration;
import io.harness.enforcement.client.servicedependencies.RestrictionUsageRegisterConfiguration;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.ff.FeatureFlagConfig;
import io.harness.ff.FeatureFlagService;
import io.harness.ff.FeatureFlagServiceImpl;
import io.harness.filter.LoopDetectionAndPrevention;
import io.harness.gitsync.GitSdkConfiguration;
import io.harness.gitsync.events.AbstractGitSyncSdkModule;
import io.harness.gitsync.persistance.EntityKeySource;
import io.harness.gitsync.persistance.EntityLookupHelper;
import io.harness.gitsync.persistance.GitSyncSdkServiceImpl;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitsync.sdk.GitSyncEntitiesConfiguration;
import io.harness.gitsync.sdk.GitSyncGrpcClientModule;
import io.harness.gitsync.sdk.GitSyncSdkConfiguration;
import io.harness.grpc.interceptor.GrpcServiceLoopDetectionModule;
import io.harness.health.HealthMonitor;
import io.harness.health.HealthService;
import io.harness.idp.aggregation.rules.iteratorhandler.AggregationRulesComputationHandler;
import io.harness.idp.annotations.IdpServiceAuth;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.idp.backstage.iteratorhandler.ScaffolderTasksSyncHandler;
import io.harness.idp.catalog.iteratorhandler.ApiEndpointRefreshHandler;
import io.harness.idp.catalog.iteratorhandler.CatalogEntitiesVerificationHandler;
import io.harness.idp.catalog.iteratorhandler.HarnessToIDPUserGroupSyncHandler;
import io.harness.idp.catalog.iteratorhandler.IDPToHarnessEntitiesHandler;
import io.harness.idp.catalog.iteratorhandler.RelationshipRetryHandler;
import io.harness.idp.catalog.iteratorhandler.ScopeTopologyCacheRebuildHandler;
import io.harness.idp.configmanager.iteratorhandler.ConfigPurgeHandler;
import io.harness.idp.configmanager.iteratorhandler.MarketPlacePluginsSyncHandler;
import io.harness.idp.envvariable.iteratorhandler.BackstageEnvVariablesSyncHandler;
import io.harness.idp.events.consumers.BulkFieldUpdateEventConsumer;
import io.harness.idp.events.consumers.EntityCrudStreamConsumer;
import io.harness.idp.events.consumers.IDPIntegrationCrudEventRedisConsumer;
import io.harness.idp.events.consumers.IdpCatalogApiEndpointConsumerV3;
import io.harness.idp.events.consumers.IdpCatalogCustomPropertyCaptureEventConsumer;
import io.harness.idp.events.consumers.IdpCatalogEntitiesSyncCaptureEventConsumer;
import io.harness.idp.events.consumers.IdpCatalogReferencedEntitiesSyncConsumerV3;
import io.harness.idp.events.consumers.IdpCatalogStoEnrichmentConsumerV3;
import io.harness.idp.events.consumers.IdpIntegrationCatalogProcessorEventConsumer;
import io.harness.idp.events.consumers.IdpKindProcessorEventConsumer;
import io.harness.idp.events.consumers.IdpModuleLicenseUsageCaptureEventConsumer;
import io.harness.idp.events.consumers.ProjectMovementConsumer;
import io.harness.idp.events.consumers.ProjectTopologyRebuildConsumer;
import io.harness.idp.events.consumers.RelationshipProcessingEventConsumer;
import io.harness.idp.events.consumers.STOScanCompletionEventRedisConsumer;
import io.harness.idp.events.consumers.UserMembershipStreamConsumer;
import io.harness.idp.events.consumers.debezium.AppConfigsRedisEventConsumer;
import io.harness.idp.events.consumers.debezium.BackstageCatalogRedisEventConsumer;
import io.harness.idp.events.consumers.debezium.BackstageScaffolderTasksRedisEventConsumer;
import io.harness.idp.events.consumers.debezium.CatalogRedisEventConsumer;
import io.harness.idp.events.consumers.debezium.ChecksRedisEventConsumer;
import io.harness.idp.events.consumers.debezium.ModuleLicensesRedisEventConsumer;
import io.harness.idp.events.consumers.debezium.ScorecardsRedisEventConsumer;
import io.harness.idp.events.consumers.kafka.IdpEventConsumerController;
import io.harness.idp.governance.beans.Constants;
import io.harness.idp.governance.services.ScorecardExpansionHandler;
import io.harness.idp.iterators.config.IteratorsConfig;
import io.harness.idp.license.enforcement.ActiveDevelopersRestrictionUsageImpl;
import io.harness.idp.license.usage.iteratorhandler.ActiveDevelopersSyncHandler;
import io.harness.idp.license.usage.iteratorhandler.IDPTelemetryRecordsHandler;
import io.harness.idp.license.usage.iteratorhandler.LicenseUsageCountHandler;
import io.harness.idp.license.usage.iteratorhandler.UserBillingMetricJob;
import io.harness.idp.license.usage.resources.IDPLicenseUsageResource;
import io.harness.idp.migration.IdpMigrationProvider;
import io.harness.idp.migration.MigrateEntityScopeForIdpV2Handler;
import io.harness.idp.migration.ModifyDefaultToAccountNamespaceInBackstageForIdpV2Handler;
import io.harness.idp.migration.ModifyEntityIdentifierInDependentsForIdpV2Handler;
import io.harness.idp.migration.ModifyWorkflowFormContextDataForIdpV2Handler;
import io.harness.idp.migration.PopulateQueryableEntityRefInCatalogForIdpV2Handler;
import io.harness.idp.onboarding.iteratorhandler.OnboardingFlowRecordsHandler;
import io.harness.idp.pipeline.IDPCapacityPoller;
import io.harness.idp.pipeline.IDPExecutionPoller;
import io.harness.idp.pipeline.filter.IdpFilterCreationResponseMerger;
import io.harness.idp.pipeline.provider.IdpPipelineServiceInfoProvider;
import io.harness.idp.pipeline.registrar.IDPFacilitatorRegistrar;
import io.harness.idp.pipeline.registrar.IdpStepRegistrar;
import io.harness.idp.provision.jobs.DefaultProvisioningForDevSpaces;
import io.harness.idp.scorecard.scores.iteratorhandler.ScoreComputationHandler;
import io.harness.idp.scorecard.scores.iteratorhandler.StatsComputationHandler;
import io.harness.idp.scorecard.scores.iteratorhandler.StatsComputationSyncHandler;
import io.harness.idp.settings.iteratorhandler.BackstagePermissionsSyncHandler;
import io.harness.idp.user.iteratorhandler.UserSyncHandler;
import io.harness.idp.version.resource.VersionInfoResource;
import io.harness.idp.workflowlibrary.iteratorhandler.WorkflowLibrarySyncHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.kafka.KafkaModule;
import io.harness.licensing.usage.resources.LicenseUsageResource;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.MetricRegistryModule;
import io.harness.metrics.jobs.RecordMetricsJob;
import io.harness.metrics.modules.PrometheusMetricsModule;
import io.harness.metrics.service.api.MetricService;
import io.harness.migration.ng.MigrationProvider;
import io.harness.migration.ng.NGMigrationConfiguration;
import io.harness.migration.ng.NGMigrationSdkInitHelper;
import io.harness.migration.ng.NGMigrationSdkModule;
import io.harness.ng.core.TraceFilter;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.NotAllowedExceptionMapper;
import io.harness.ng.core.exceptionmappers.NotFoundExceptionMapper;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.notification.Team;
import io.harness.notification.module.NotificationClientModule;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.templates.PredefinedTemplate;
import io.harness.opa.serializer.json.serializers.OpaBeansJacksonModule;
import io.harness.outbox.eventpoll.OutboxEventPollService;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.plan.ExpansionRequestType;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PipelineEventConsumerController;
import io.harness.pms.listener.NgOrchestrationNotifyEventListenerNonVersioned;
import io.harness.pms.sdk.PmsSdkInitHelper;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.configuration.PmsSdkConfiguration;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.pms.sdk.core.governance.JsonExpansionHandlerInfo;
import io.harness.pms.sdk.execution.events.facilitators.FacilitatorEventRedisConsumer;
import io.harness.pms.sdk.execution.events.facilitators.FacilitatorEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.interrupts.InterruptEventRedisConsumer;
import io.harness.pms.sdk.execution.events.interrupts.InterruptEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.node.advise.NodeAdviseEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.advise.NodeAdviseRedisConsumerV2;
import io.harness.pms.sdk.execution.events.node.resume.NodeResumeEventConsumerV2;
import io.harness.pms.sdk.execution.events.node.resume.NodeResumeEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.start.NodeStartEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.start.NodeStartEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.orchestrationevent.OrchestrationEventRedisConsumer;
import io.harness.pms.sdk.execution.events.plan.CreatePartialPlanRedisConsumer;
import io.harness.pms.sdk.execution.events.progress.NodeProgressEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.progress.ProgressEventRedisConsumer;
import io.harness.pms.serializer.json.PmsBeansJacksonModule;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.queue.QueueListenerController;
import io.harness.queue.QueuePublisher;
import io.harness.request.RequestContextFilter;
import io.harness.request.RequestLoggingFilter;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.InternalApiAuthFilter;
import io.harness.security.MeshIdentityBootstrap;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.ScopeInfoFilter;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.security.mesh.MeshIdentityConfig;
import io.harness.service.impl.DelegateAsyncServiceImpl;
import io.harness.service.impl.DelegateProgressServiceImpl;
import io.harness.service.impl.DelegateSyncServiceImpl;
import io.harness.sto.STOServiceRestClientModule;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ScalingThreadPoolExecutor;
import io.harness.threading.ThreadPoolConfig;
import io.harness.token.remote.TokenClient;
import io.harness.waiter.NotifierScheduledExecutorService;
import io.harness.waiter.NotifyEvent;
import io.harness.waiter.NotifyQueuePublisherRegister;
import io.harness.waiter.nrcsp.NotifyResponseCleanerFactory;
import io.harness.waiter.nrcsp.NotifyResponseCleanerSpringPersistence;
import io.harness.waiter.nrcsp.NotifyResponseIterator;
import io.harness.yaml.YamlSdkConfiguration;
import io.harness.yaml.YamlSdkInitHelper;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.serializer.HObjectMapper;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import java.lang.annotation.Annotation;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ServerProperties;
import org.springframework.data.mongodb.core.MongoTemplate;
/**
 * The main application - entry point for the entire Wings Application.
 */
@Slf4j
@OwnedBy(IDP)
public class IdpApplication extends Application<IdpConfiguration> {
  private static final SecureRandom random = new SecureRandom();
  private final MetricRegistry metricRegistry = new MetricRegistry();
  private final MetricRegistry threadPoolMetricRegistry = new MetricRegistry();

  /**
   * The entry point of application.
   *
   * @param args the input arguments
   * @throws Exception the exception
   */
  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));

    new IdpApplication().run(args);
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    HObjectMapper.configureObjectMapperForNG(mapper);
    mapper.registerModule(new PmsBeansJacksonModule());
  }

  @Override
  public String getName() {
    return "IDP Service";
  }

  @Override
  public void initialize(Bootstrap<IdpConfiguration> bootstrap) {
    initializeLogging();
    log.info("bootstrapping ...");
    bootstrap.addBundle(new SwaggerV2Bundle<>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(IdpConfiguration appConfig) {
        return getSwaggerConfiguration(appConfig);
      }

      @Override
      protected Collection<Class<?>> getResourceClasses() {
        return HARNESS_RESOURCE_CLASSES;
      }
    });
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addCommand(new ScanClasspathMetadataCommand());
    bootstrap.addCommand(new GenerateOpenApiSpecCommand());

    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    bootstrap.addBundle(new FileAssetsBundle<>("/.well-known"));
    bootstrap.setMetricRegistry(metricRegistry);
    bootstrap.getObjectMapper().registerModule(new OpaBeansJacksonModule());

    log.info("bootstrapping done.");
  }

  @Override
  public void run(final IdpConfiguration configuration, Environment environment) throws Exception {
    log.info("Starting app ...");
    log.info("Entering startup maintenance mode");
    MaintenanceController.forceMaintenance(true);

    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build();
    ExecutorModule.getInstance().setExecutorService(new ScalingThreadPoolExecutor(ThreadPoolConfig.builder()
                                                                                      .corePoolSize(20)
                                                                                      .maxPoolSize(1000)
                                                                                      .idleTime(500L)
                                                                                      .timeUnit(TimeUnit.MILLISECONDS)
                                                                                      .build(),
        threadFactory));

    List<Module> modules = new ArrayList<>();
    modules.add(new IdpModule(configuration));
    modules.add(new CacheModule(configuration.getCacheConfig()));
    PmsSdkConfiguration idpPmsSdkConfiguration = getPmsSdkConfiguration(configuration);
    PrometheusMetricsModule prometheusMetricsModule = new PrometheusMetricsModule();
    LoopDetectionAndPrevention loopDetectionAndPrevention =
        new LoopDetectionAndPrevention(prometheusMetricsModule.metricService);
    modules.add(KafkaModule.getInstance(configuration.getKafkaModuleConfig()));
    modules.add(PmsSdkModule.getInstance(idpPmsSdkConfiguration, threadPoolMetricRegistry, false));
    modules.add(prometheusMetricsModule);
    modules.add(new GrpcServiceLoopDetectionModule(loopDetectionAndPrevention));
    modules.add(PipelineServiceUtilityModule.getInstance());
    modules.add(NGMigrationSdkModule.getInstance());
    modules.add(new SCMGrpcClientModule(configuration.getGitSdkConfiguration().getScmConnectionConfig()));
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(FeatureFlagService.class).to(FeatureFlagServiceImpl.class);
      }
    });
    modules.add(new NotificationClientModule(configuration.getNotificationClientConfiguration()));
    modules.add(new STOServiceRestClientModule(configuration.getStoServiceRestClientConfig()));
    modules.add(new MetricRegistryModule(metricRegistry));
    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return configuration.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return configuration.getFeatureFlagConfig();
      }
    });

    GitSyncSdkConfiguration gitSyncSdkConfiguration = getGitSyncConfiguration(configuration);
    modules.add(new AbstractGitSyncSdkModule() {
      @Override
      public GitSyncSdkConfiguration getGitSyncSdkConfiguration() {
        return gitSyncSdkConfiguration;
      }

      @Override
      protected void configure() {
        install(GitSyncGrpcClientModule.getInstance());
        bind(EntityKeySource.class).to(EntityLookupHelper.class);
        bind(GitSyncSdkService.class).to(GitSyncSdkServiceImpl.class);
      }
    });

    Injector injector = Guice.createInjector(modules);
    registerPMSSDK(configuration, injector, environment);
    registerResources(environment, injector);
    registerHealthChecksManager(environment, injector);
    registerQueueListeners(configuration, injector);
    registerAuthFilters(configuration, environment, injector);
    registerManagedJobs(environment, injector, configuration);
    registerPmsSdkEvents(injector);
    registerExceptionMappers(environment.jersey());
    registerMigrations(injector);
    registerHealthCheck(environment, injector);
    registerYamlSdk(injector);
    registerWaitEnginePublishers(injector);
    registerNotificationTemplates(configuration, injector);
    registerRequestContextFilter(environment);
    registerIterators(
        injector, configuration.getIteratorsConfig(), configuration.isEnableWaitNotifyEngineOptimisation());
    if (configuration.getEnableOpenTelemetry()) {
      registerTraceFilter(environment, injector);
    }
    environment.jersey().register(RequestLoggingFilter.class);
    registerOasResource(configuration, environment, injector);
    environment.jersey().register(injector.getInstance(IdpServiceRequestInterceptor.class));
    environment.jersey().register(injector.getInstance(IdpServiceResponseInterceptor.class));
    // Register IDP access control filter - checks if user has required permission to access IDP
    environment.jersey().register(injector.getInstance(io.harness.idp.security.IdpAccessControlFilter.class));
    environment.jersey().register(injector.getInstance(io.harness.idp.security.IdpAccountAccessFilter.class));
    environment.jersey().register(MultiPartFeature.class);
    initMetrics(injector);
    initializeEnforcementSdk(injector);
    registerScopeInfoFilter(environment, injector);

    log.info("Starting app done");
    log.info("IDP Service is running on JRE: {}", System.getProperty("java.version"));

    MaintenanceController.forceMaintenance(false);
  }

  private void registerManagedJobs(Environment environment, Injector injector, IdpConfiguration idpConfiguration) {
    environment.lifecycle().manage(injector.getInstance(DefaultProvisioningForDevSpaces.class));
    environment.lifecycle().manage(injector.getInstance(PipelineEventConsumerController.class));
    environment.lifecycle().manage(injector.getInstance(OutboxEventPollService.class));
    environment.lifecycle().manage(injector.getInstance(IdpEventConsumerController.class));
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateSyncServiceImpl.class), 0L, 2L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateAsyncServiceImpl.class), 0L, 5L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateProgressServiceImpl.class), 0L, 5L, TimeUnit.SECONDS);
    if (idpConfiguration.getEnableQueue()) {
      environment.lifecycle().manage(injector.getInstance(IDPExecutionPoller.class));
      if (idpConfiguration.getCiExecutionServiceConfig().getGlobalQueueingConfig().getEnableGlobalQueue()) {
        environment.lifecycle().manage(injector.getInstance(IDPCapacityPoller.class));
      }
    }
    if (idpConfiguration.isUserBillingMetricJobEnabled()) {
      environment.lifecycle().manage(injector.getInstance(UserBillingMetricJob.class));
    }
  }

  private void registerQueueListeners(IdpConfiguration configuration, Injector injector) {
    log.info("Initializing queue listeners...");
    IdpEventConsumerController controller = injector.getInstance(IdpEventConsumerController.class);
    controller.register(injector.getInstance(EntityCrudStreamConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(ENTITY_CRUD));
    controller.register(injector.getInstance(UserMembershipStreamConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(USERMEMBERSHIP));
    QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    queueListenerController.register(injector.getInstance(NgOrchestrationNotifyEventListenerNonVersioned.class), 1);
    controller.register(injector.getInstance(IdpModuleLicenseUsageCaptureEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT));
    controller.register(injector.getInstance(IdpCatalogEntitiesSyncCaptureEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT));
    controller.register(injector.getInstance(IdpCatalogReferencedEntitiesSyncConsumerV3.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_CATALOG_ENTITIES_V3_REFERENCED_ENTITIES_SYNC));
    controller.register(injector.getInstance(IdpCatalogStoEnrichmentConsumerV3.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_CATALOG_ENTITIES_V3_STO_ENRICHMENT));
    controller.register(injector.getInstance(IdpCatalogApiEndpointConsumerV3.class),
        configuration.getNumberOfThreadsToUseForConsumers().getOrDefault(IDP_CATALOG_ENTITIES_V3_API_ENDPOINT, 1));
    controller.register(injector.getInstance(ProjectMovementConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(PROJECT_MOVEMENT));
    controller.register(injector.getInstance(ProjectTopologyRebuildConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(PROJECT_TOPOLOGY_REBUILD));
    controller.register(injector.getInstance(IdpCatalogCustomPropertyCaptureEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_CATALOG_CUSTOM_PROPERTY_ENTITY_CAPTURE_EVENT));
    controller.register(injector.getInstance(BackstageCatalogRedisEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(BACKSTAGE_CATALOG_REDIS_EVENT_CONSUMER));
    controller.register(injector.getInstance(BackstageScaffolderTasksRedisEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(BACKSTAGE_SCAFFOLDER_TASKS_REDIS_EVENT_CONSUMER));
    controller.register(injector.getInstance(ScorecardsRedisEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_SCORECARDS_REDIS_EVENT_CONSUMER));
    controller.register(injector.getInstance(AppConfigsRedisEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_APP_CONFIGS_REDIS_EVENT_CONSUMER));
    controller.register(injector.getInstance(ModuleLicensesRedisEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(MODULE_LICENSES_REDIS_EVENT_CONSUMER));
    controller.register(injector.getInstance(ChecksRedisEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_CHECKS_REDIS_EVENT_CONSUMER));
    controller.register(injector.getInstance(CatalogRedisEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_CATALOG_REDIS_EVENT_CONSUMER));
    controller.register(injector.getInstance(STOScanCompletionEventRedisConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(STO_SCAN_COMPLETION_EVENT));
    controller.register(injector.getInstance(IDPIntegrationCrudEventRedisConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_INTEGRATION_CRUD_EVENT));
    controller.register(injector.getInstance(IdpIntegrationCatalogProcessorEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT));
    controller.register(injector.getInstance(RelationshipProcessingEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_RELATIONSHIP_PROCESSING_EVENT));
    controller.register(injector.getInstance(IdpKindProcessorEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_KIND_PROCESSOR_EVENT));
    controller.register(injector.getInstance(BulkFieldUpdateEventConsumer.class),
        configuration.getNumberOfThreadsToUseForConsumers().get(IDP_BULK_FIELD_UPDATE_EVENT));
  }

  private void registerOasResource(IdpConfiguration config, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(config.getOasConfig());
    environment.jersey().register(openApiResource);
  }

  private void registerIterators(
      Injector injector, IteratorsConfig iteratorsConfig, boolean enableWaitNotifyOptimisation) {
    if (iteratorsConfig.getScorecardScoreComputation().isEnabled()) {
      injector.getInstance(ScoreComputationHandler.class)
          .registerIterators(iteratorsConfig.getScorecardScoreComputation());
    }
    injector.getInstance(BackstageEnvVariablesSyncHandler.class)
        .registerIterators(iteratorsConfig.getBackstageEnvVariablesSync());
    injector.getInstance(BackstagePermissionsSyncHandler.class)
        .registerIterators(iteratorsConfig.getBackstagePermissionsSync());
    injector.getInstance(ConfigPurgeHandler.class).registerIterators(iteratorsConfig.getConfigPurge());
    injector.getInstance(LicenseUsageCountHandler.class).registerIterators(iteratorsConfig.getLicenseUsageCount());
    injector.getInstance(ScaffolderTasksSyncHandler.class).registerIterators(iteratorsConfig.getScaffolderTasksSync());
    if (iteratorsConfig.getUserSync().isEnabled()) {
      injector.getInstance(UserSyncHandler.class).registerIterators(iteratorsConfig.getUserSync());
    }
    injector.getInstance(IDPTelemetryRecordsHandler.class).registerIterators(iteratorsConfig.getTelemetryRecords());
    if (iteratorsConfig.getOnboardingFlow().isEnabled()) {
      injector.getInstance(OnboardingFlowRecordsHandler.class).registerIterators(iteratorsConfig.getOnboardingFlow());
    }
    injector.getInstance(StatsComputationSyncHandler.class)
        .registerIterators(iteratorsConfig.getStatsComputationSync());
    injector.getInstance(ActiveDevelopersSyncHandler.class)
        .registerIterators(iteratorsConfig.getActiveDevelopersSync());
    injector.getInstance(MarketPlacePluginsSyncHandler.class)
        .registerIterators(iteratorsConfig.getMarketPlacePluginsSync());
    injector.getInstance(StatsComputationHandler.class)
        .registerIterators(iteratorsConfig.getScorecardStatsComputation());
    if (iteratorsConfig.getIdpToHarnessEntities().isEnabled()) {
      injector.getInstance(IDPToHarnessEntitiesHandler.class)
          .registerIterators(iteratorsConfig.getIdpToHarnessEntities());
    }
    if (iteratorsConfig.getHarnessToIDPUserGroupSync().isEnabled()) {
      injector.getInstance(HarnessToIDPUserGroupSyncHandler.class)
          .registerIterators(iteratorsConfig.getHarnessToIDPUserGroupSync());
    }
    if (iteratorsConfig.getCatalogEntitiesVerification().isEnabled()) {
      injector.getInstance(CatalogEntitiesVerificationHandler.class)
          .registerIterators(iteratorsConfig.getCatalogEntitiesVerification());
    }
    if (iteratorsConfig.getApiEndpointRefresh() != null && iteratorsConfig.getApiEndpointRefresh().isEnabled()) {
      injector.getInstance(ApiEndpointRefreshHandler.class).registerIterators(iteratorsConfig.getApiEndpointRefresh());
    }
    if (iteratorsConfig.getScopeTopologyCacheRebuild() != null
        && iteratorsConfig.getScopeTopologyCacheRebuild().isEnabled()) {
      injector.getInstance(ScopeTopologyCacheRebuildHandler.class)
          .registerIterators(iteratorsConfig.getScopeTopologyCacheRebuild());
    }
    if (iteratorsConfig.getModifyEntityIdentifierInDependentsForIdpV2().isEnabled()) {
      injector.getInstance(ModifyEntityIdentifierInDependentsForIdpV2Handler.class)
          .registerIterators(iteratorsConfig.getModifyEntityIdentifierInDependentsForIdpV2());
    }
    if (iteratorsConfig.getModifyDefaultToAccountNamespaceInBackstageForIdpV2().isEnabled()) {
      injector.getInstance(ModifyDefaultToAccountNamespaceInBackstageForIdpV2Handler.class)
          .registerIterators(iteratorsConfig.getModifyDefaultToAccountNamespaceInBackstageForIdpV2());
    }
    if (iteratorsConfig.getModifyWorkflowFormContextDataForIdpV2().isEnabled()) {
      injector.getInstance(ModifyWorkflowFormContextDataForIdpV2Handler.class)
          .registerIterators(iteratorsConfig.getModifyWorkflowFormContextDataForIdpV2());
    }
    if (iteratorsConfig.getMigrateEntityScopeForIdpV2().isEnabled()) {
      injector.getInstance(MigrateEntityScopeForIdpV2Handler.class)
          .registerIterators(iteratorsConfig.getMigrateEntityScopeForIdpV2());
    }
    if (iteratorsConfig.getAggregationRulesComputation().isEnabled()) {
      injector.getInstance(AggregationRulesComputationHandler.class)
          .registerIterators(iteratorsConfig.getAggregationRulesComputation());
    }
    if (iteratorsConfig.getPopulateQueryableEntityRefInCatalogForIdpV2().isEnabled()) {
      injector.getInstance(PopulateQueryableEntityRefInCatalogForIdpV2Handler.class)
          .registerIterators(iteratorsConfig.getPopulateQueryableEntityRefInCatalogForIdpV2());
    }
    if (iteratorsConfig.getRelationshipRetry() != null && iteratorsConfig.getRelationshipRetry().isEnabled()) {
      injector.getInstance(RelationshipRetryHandler.class).registerIterators(iteratorsConfig.getRelationshipRetry());
    }
    if (iteratorsConfig.getWorkflowLibrarySync() != null && iteratorsConfig.getWorkflowLibrarySync().isEnabled()) {
      injector.getInstance(WorkflowLibrarySyncHandler.class)
          .registerIterators(iteratorsConfig.getWorkflowLibrarySync());
    }

    if (enableWaitNotifyOptimisation) {
      injector.getInstance(NotifyResponseIterator.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name("IDP-NotifyResponseIterator")
                  .poolSize(iteratorsConfig.getNotifyResponseRedisConfig().getThreadPoolSize())
                  .batchSize(iteratorsConfig.getNotifyResponseRedisConfig().getRedisBatchSize())
                  .lockTimeout(iteratorsConfig.getNotifyResponseRedisConfig().getRedisLockTimeout())
                  .interval(Duration.ofSeconds(
                      iteratorsConfig.getNotifyResponseRedisConfig().getThreadPoolIntervalInSeconds()))
                  .threadSleepInterval(iteratorsConfig.getNotifyResponseRedisConfig().getThreadSleepIntervalMs())
                  .build(),
              Duration.ofSeconds(iteratorsConfig.getNotifyResponseRedisConfig().getTargetIntervalInSeconds()));
      NotifyResponseCleanerSpringPersistence cleaner =
          injector.getInstance(NotifyResponseCleanerFactory.class).create("IDP");
      injector.getInstance(NotifierScheduledExecutorService.class)
          .scheduleWithFixedDelay(cleaner, random.nextInt(300), 300L, TimeUnit.SECONDS);
    }
  }

  private void registerRequestContextFilter(Environment environment) {
    environment.jersey().register(new RequestContextFilter());
  }

  private void registerTraceFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(TraceFilter.class));
  }

  private void initMetrics(Injector injector) {
    injector.getInstance(MetricService.class).initializeMetrics();
    injector.getInstance(RecordMetricsJob.class).scheduleMetricsTasks();
  }

  private void registerHealthChecksManager(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("IDP Service", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : HARNESS_RESOURCE_CLASSES) {
      environment.jersey().register(injector.getInstance(resource));
    }
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
    environment.jersey().register(injector.getInstance(LicenseUsageResource.class));
    environment.jersey().register(injector.getInstance(IDPLicenseUsageResource.class));
    environment.jersey().register(injector.getInstance(EnforcementClientResource.class));
    environment.jersey().property(ServerProperties.RESOURCE_VALIDATION_DISABLE, true);
  }

  private void registerAuthFilters(IdpConfiguration config, Environment environment, Injector injector) {
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate =
        (getAuthenticationExemptedRequestsPredicate().negate())
            .and(resourceInfoAndRequest
                -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
                       && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(IdpServiceAuth.class)
                           != null)
                    || (resourceInfoAndRequest.getKey().getResourceClass() != null
                        && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(IdpServiceAuth.class)
                            != null)
                    || (resourceInfoAndRequest.getKey().getResourceMethod() != null
                        && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(
                               IdpServiceAuthIfHasApiKey.class)
                            != null
                        && resourceInfoAndRequest.getValue().getHeaders().get(X_API_KEY) != null)
                    || (resourceInfoAndRequest.getKey().getResourceMethod() != null
                        && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(NextGenManagerAuth.class)
                            != null)
                    || (resourceInfoAndRequest.getKey().getResourceClass() != null
                        && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(NextGenManagerAuth.class)
                            != null));
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(AuthorizationServiceHeader.BEARER.getServiceId(), config.getJwtAuthSecret());
    serviceToSecretMapping.put(
        AuthorizationServiceHeader.IDENTITY_SERVICE.getServiceId(), config.getJwtIdentityServiceSecret());
    serviceToSecretMapping.put(AuthorizationServiceHeader.DEFAULT.getServiceId(), config.getNgManagerServiceSecret());
    serviceToSecretMapping.put(AuthorizationServiceHeader.IDP_UI.getServiceId(), config.getIdpServiceSecret());
    serviceToSecretMapping.put(AuthorizationServiceHeader.IDP_SERVICE.getServiceId(), config.getIdpServiceSecret());
    serviceToSecretMapping.put(
        AuthorizationServiceHeader.ADMIN_PORTAL.getServiceId(), config.getJwtExternalServiceSecret());
    // Mesh identity dispatch: opt-in per config. See MeshIdentityConfig for the rollout matrix.
    MeshIdentityConfig mesh = config.getMeshIdentity();
    NextGenAuthenticationFilter authFilter = MeshIdentityBootstrap.buildFilter(predicate, serviceToSecretMapping,
        injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED"))), mesh);
    environment.jersey().register(authFilter);
    registerInternalApiAuthFilter(config, environment);
  }

  private void registerInternalApiAuthFilter(IdpConfiguration configuration, Environment environment) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getIdpServiceSecret());
    serviceToSecretMapping.put(
        AuthorizationServiceHeader.NG_MANAGER.getServiceId(), configuration.getNgManagerServiceSecret());
    environment.jersey().register(
        new InternalApiAuthFilter(getAuthFilterPredicate(InternalApi.class), null, serviceToSecretMapping));
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthenticationExemptedRequestsPredicate() {
    return getAuthFilterPredicate(PublicApi.class);
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthFilterPredicate(
      Class<? extends Annotation> annotation) {
    return resourceInfoAndRequest
        -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
               && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(annotation) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(annotation) != null);
  }

  private void registerExceptionMappers(JerseyEnvironment jersey) {
    jersey.register(JerseyViolationExceptionMapperV2.class);
    jersey.register(GenericExceptionMapperV2.class);
    jersey.register(new JsonProcessingExceptionMapper(true));
    jersey.register(EarlyEofExceptionMapper.class);
    jersey.register(NGAccessDeniedExceptionMapper.class);
    jersey.register(WingsExceptionMapperV2.class);
    jersey.register(NotFoundExceptionMapper.class);
    jersey.register(NotAllowedExceptionMapper.class);
  }

  private void registerMigrations(Injector injector) {
    NGMigrationConfiguration config = getMigrationSdkConfiguration();
    NGMigrationSdkInitHelper.initialize(injector, config);
  }

  private SwaggerBundleConfiguration getSwaggerConfiguration(IdpConfiguration appConfig) {
    final var defaultSwaggerBundleConfiguration = new SwaggerBundleConfiguration();
    String resourcePackage = String.join(",", getUniquePackages(HARNESS_RESOURCE_CLASSES));
    defaultSwaggerBundleConfiguration.setResourcePackage(resourcePackage);
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost(appConfig.hostname);
    defaultSwaggerBundleConfiguration.setUriPrefix(appConfig.basePathPrefix);
    defaultSwaggerBundleConfiguration.setVersion("1.0");
    defaultSwaggerBundleConfiguration.setTitle("IDP Service API Reference");
    return defaultSwaggerBundleConfiguration;
  }

  private NGMigrationConfiguration getMigrationSdkConfiguration() {
    return NGMigrationConfiguration.builder()
        .microservice(Microservice.IDP)
        .migrationProviderList(new ArrayList<Class<? extends MigrationProvider>>() {
          {
            add(IdpMigrationProvider.class);
            add(io.harness.idp.migration.ootb.IdpOotbMigrationProvider.class);
          }
        })
        .build();
  }

  private PmsSdkConfiguration getPmsSdkConfiguration(IdpConfiguration configuration) {
    boolean remote = configuration.getShouldConfigureWithPMS() != null && configuration.getShouldConfigureWithPMS();

    return PmsSdkConfiguration.builder()
        .streamPerServiceConfiguration(configuration.isStreamPerServiceConfiguration())
        .deploymentMode(remote ? SdkDeployMode.REMOTE : SdkDeployMode.LOCAL)
        .moduleType(ModuleType.IDP)
        .modulePath("idp-admin")
        .grpcServerConfig(configuration.getPmsSdkGrpcServerConfig())
        .pmsGrpcClientConfig(configuration.getPmsGrpcClientConfig())
        .pipelineServiceInfoProviderClass(IdpPipelineServiceInfoProvider.class)
        .filterCreationResponseMerger(new IdpFilterCreationResponseMerger())
        .engineSteps(IdpStepRegistrar.getEngineSteps())
        .engineFacilitators(IDPFacilitatorRegistrar.getEngineFacilitators())
        .engineAdvisers(ExecutionAdvisers.getEngineAdvisers())
        .engineEventHandlersMap(OrchestrationExecutionEventHandlerRegistrar.getEngineEventHandlers())
        .eventsFrameworkConfiguration(configuration.getEventsFrameworkConfiguration())
        .executionPoolConfig(configuration.getPmsSdkExecutionPoolConfig())
        .orchestrationEventPoolConfig(configuration.getPmsSdkOrchestrationEventPoolConfig())
        .orchestrationHandlerPoolConfig(configuration.getPmsSdkOrchestrationHandlerPoolConfig())
        .executionSummaryModuleInfoProviderClass(CIModuleInfoProvider.class)
        .jsonExpansionHandlers(getJsonExpansionHandlers())
        .pipelineSdkRedisEventsConfig(configuration.getPipelineSdkRedisEventsConfig())
        .build();
  }

  private List<JsonExpansionHandlerInfo> getJsonExpansionHandlers() {
    List<JsonExpansionHandlerInfo> jsonExpansionHandlers = new ArrayList<>();
    JsonExpansionInfo scorecardInfo =
        JsonExpansionInfo.newBuilder()
            .setExpansionType(ExpansionRequestType.LOCAL_FQN)
            .setKey(YAMLFieldNameConstants.STAGE)
            .setExpansionKey(Constants.IDP_SCORECARD_EXPANSION_KEY)
            .setStageType(StepType.newBuilder().setType("Deployment").setStepCategory(StepCategory.STAGE).build())
            .build();
    JsonExpansionHandlerInfo scorecardExpansionHandler = JsonExpansionHandlerInfo.builder()
                                                             .jsonExpansionInfo(scorecardInfo)
                                                             .expansionHandler(ScorecardExpansionHandler.class)
                                                             .build();
    jsonExpansionHandlers.add(scorecardExpansionHandler);
    return jsonExpansionHandlers;
  }

  private void registerPMSSDK(IdpConfiguration configuration, Injector injector, Environment environment) {
    PmsSdkConfiguration idpSDKConfig = getPmsSdkConfiguration(configuration);
    if (idpSDKConfig.getDeploymentMode().equals(SdkDeployMode.REMOTE)) {
      try {
        PmsSdkInitHelper.initializeSDKInstance(injector, idpSDKConfig, environment);
      } catch (Exception e) {
        log.error("PMS SDK registration failed", e);
        System.exit(1);
      }
    }
  }

  private static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }

  private void registerPmsSdkEvents(Injector injector) {
    log.info("Initializing pms sdk redis abstract consumers...");
    PipelineEventConsumerController pipelineEventConsumerController =
        injector.getInstance(PipelineEventConsumerController.class);
    pipelineEventConsumerController.register(injector.getInstance(OrchestrationEventRedisConsumer.class), 1);

    pipelineEventConsumerController.register(injector.getInstance(InterruptEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(FacilitatorEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeStartEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(ProgressEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventRedisConsumer.class), 1);

    pipelineEventConsumerController.register(injector.getInstance(InterruptEventRedisConsumerV2.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(FacilitatorEventRedisConsumerV2.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeStartEventRedisConsumerV2.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeProgressEventRedisConsumerV2.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseRedisConsumerV2.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventConsumerV2.class), 1);

    pipelineEventConsumerController.register(injector.getInstance(CreatePartialPlanRedisConsumer.class), 1);
  }

  private void registerWaitEnginePublishers(Injector injector) {
    final QueuePublisher<NotifyEvent> publisher =
        injector.getInstance(Key.get(new TypeLiteral<QueuePublisher<NotifyEvent>>() {}));
    final NotifyQueuePublisherRegister notifyQueuePublisherRegister =
        injector.getInstance(NotifyQueuePublisherRegister.class);
    notifyQueuePublisherRegister.register(
        NG_ORCHESTRATION, payload -> publisher.send(singletonList(NG_ORCHESTRATION), payload));
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("IDP", healthService);
    healthService.registerMonitor((HealthMonitor) injector.getInstance(MongoTemplate.class));
  }

  private void registerYamlSdk(Injector injector) {
    YamlSdkConfiguration yamlSdkConfiguration = YamlSdkConfiguration.builder()
                                                    .requireSchemaInit(true)
                                                    .requireSnippetInit(true)
                                                    .requireValidatorInit(false)
                                                    .build();
    YamlSdkInitHelper.initialize(injector, yamlSdkConfiguration);
  }

  private void registerNotificationTemplates(IdpConfiguration configuration, Injector injector) {
    NotificationClient notificationClient = injector.getInstance(NotificationClient.class);
    List<PredefinedTemplate> templates =
        new ArrayList<>(List.of(PredefinedTemplate.IDP_PLUGIN_REQUESTS_NOTIFICATION_SLACK,
            PredefinedTemplate.IDP_CATALOG_ENTITIES_VERIFICATION_JOB_NOTIFICATION_SLACK));

    if (configuration.getShouldConfigureWithNotification()) {
      for (PredefinedTemplate template : templates) {
        try {
          log.info("Registering {} with NotificationService", template);
          notificationClient.saveNotificationTemplate(Team.IDP, template, true);
        } catch (Exception ex) {
          log.error(
              "Unable to save {} to NotificationService - skipping register notification templates.", template, ex);
        }
      }
    }
  }

  private void initializeEnforcementSdk(Injector injector) {
    RestrictionUsageRegisterConfiguration restrictionUsageRegisterConfiguration =
        RestrictionUsageRegisterConfiguration.builder()
            .restrictionNameClassMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends RestrictionUsageInterface>>builder()
                    .put(FeatureRestrictionName.IDP_ACTIVE_DEVELOPERS, ActiveDevelopersRestrictionUsageImpl.class)
                    .build())
            .build();
    CustomRestrictionRegisterConfiguration customConfig =
        CustomRestrictionRegisterConfiguration.builder()
            .customRestrictionMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends CustomRestrictionInterface>>builder().build())
            .build();

    injector.getInstance(EnforcementSdkRegisterService.class)
        .initialize(restrictionUsageRegisterConfiguration, customConfig);
  }

  private void registerScopeInfoFilter(Environment environment, Injector injector) {
    environment.jersey().getResourceConfig().register(new AbstractBinder() {
      @Override
      protected void configure() {
        bindFactory(ScopeInfoFactory.class).to(ScopeInfo.class);
      }
    });
    environment.jersey().register(
        new ScopeInfoFilter(injector.getInstance(Key.get(ScopeInfoClient.class, Names.named("PRIVILEGED")))));
  }

  private GitSyncSdkConfiguration getGitSyncConfiguration(IdpConfiguration config) {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    configureObjectMapper(objectMapper);
    Set<GitSyncEntitiesConfiguration> gitSyncEntitiesConfigurations = new HashSet<>();
    final GitSdkConfiguration gitSdkConfiguration = config.getGitSdkConfiguration();
    return GitSyncSdkConfiguration.builder()
        .grpcClientConfig(gitSdkConfiguration.getGitManagerGrpcClientConfig())
        .grpcServerConfig(gitSdkConfiguration.getGitSdkGrpcServerConfig())
        .deployMode(GitSyncSdkConfiguration.DeployMode.REMOTE)
        .microservice(Microservice.IDP)
        .scmConnectionConfig(gitSdkConfiguration.getScmConnectionConfig())
        .eventsFrameworkConfiguration(config.getEventsFrameworkConfiguration())
        .serviceHeader(IDP_SERVICE)
        .gitSyncEntitiesConfiguration(gitSyncEntitiesConfigurations)
        .objectMapper(objectMapper)
        .throw424ForGITServerErrors(gitSdkConfiguration.getThrow424ForGITServerErrors())
        .build();
  }
}

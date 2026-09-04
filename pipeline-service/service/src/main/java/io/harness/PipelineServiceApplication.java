/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.authorization.AuthorizationServiceHeader.DEFAULT;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.pipeline.service.PipelineServiceConfiguration.HARNESS_RESOURCE_CLASSES;
import static io.harness.pms.contracts.plan.ExpansionRequestType.KEY;
import static io.harness.waiter.PmsNotifyEventListener.PMS_ORCHESTRATION;

import static com.google.common.collect.ImmutableMap.of;

import io.harness.accesscontrol.NGAccessDeniedExceptionMapper;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoFactory;
import io.harness.cache.CacheModule;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.cleanup.ExecutionRetentionCleanUpOnTTLExpirationService;
import io.harness.cleanup.OrchestrationGraphCacheCleanupService;
import io.harness.config.PipelineServiceConsumersConfig;
import io.harness.config.PipelineServiceIteratorsConfig;
import io.harness.configuration.DeployVariant;
import io.harness.consumers.graph.GraphUpdateKafkaConsumer;
import io.harness.consumers.graph.GraphUpdateRedisConsumer;
import io.harness.controller.PrimaryVersionChangeScheduler;
import io.harness.delay.DelayEventListener;
import io.harness.deleteGraph.GraphDeleteServiceHelper;
import io.harness.dropwizard.bundles.swagger.SwaggerBundleConfiguration;
import io.harness.dropwizard.bundles.swagger.SwaggerV2Bundle;
import io.harness.enforcement.MaxStaticValueRestrictionUsageImpl;
import io.harness.enforcement.client.custom.CustomRestrictionInterface;
import io.harness.enforcement.client.servicedependencies.CustomRestrictionRegisterConfiguration;
import io.harness.enforcement.client.servicedependencies.RestrictionUsageRegisterConfiguration;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.engine.events.NodeExecutionStatusUpdateEventHandler;
import io.harness.engine.executions.concurrency.rebuild.PlanConcurrencyCounterRebuildService;
import io.harness.engine.executions.concurrency.rebuild.StepConcurrencyCounterRebuildService;
import io.harness.engine.executions.gitmetadata.jobs.ExecutionGitMetadataReconciliationIterator;
import io.harness.engine.executions.node.StuckNodeExecutionsMonitor;
import io.harness.engine.executions.node.detection.StuckExecutionDetector;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.node.service.impl.NodeExecutionServiceImpl;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.impl.PlanExecutionServiceImpl;
import io.harness.engine.expressions.constants.OrchestrationConstants;
import io.harness.engine.facilitation.facilitator.secondary.PreStepCheckFacilitator;
import io.harness.engine.interrupts.InterruptMonitor;
import io.harness.engine.interrupts.OrchestrationEndInterruptHandler;
import io.harness.engine.interrupts.helpers.RetryHelper;
import io.harness.engine.pms.execution.strategy.plan.PlanExecutionStrategy;
import io.harness.engine.pms.execution.strategy.plannode.PlanNodeExecutionStrategy;
import io.harness.engine.pms.start.NodeStartHelper;
import io.harness.engine.secrets.ExpressionsObserverFactory;
import io.harness.engine.timeouts.TimeoutInstanceRemover;
import io.harness.event.OrchestrationEndGraphHandler;
import io.harness.event.OrchestrationLogPublisher;
import io.harness.event.OrchestrationStartEventHandler;
import io.harness.event.PipelineResourceRestraintInstanceDeleteObserver;
import io.harness.event.PlanExecutionMetadataDeleteObserver;
import io.harness.event.handlers.SpawnChildrenRequestProcessor;
import io.harness.event.streams.GraphGenerationStreamsModule;
import io.harness.eventPoll.ExecutionOutboxEventPollService;
import io.harness.eventPoll.KafkaOutboxEventPollService;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.exception.GeneralException;
import io.harness.execution.consumers.InitiateNodeBatchEventKafkaConsumer;
import io.harness.execution.consumers.InitiateNodeBatchEventRedisConsumer;
import io.harness.execution.consumers.InitiateNodeEventKafkaConsumer;
import io.harness.execution.consumers.InitiateNodeEventRedisConsumer;
import io.harness.execution.consumers.sdk.response.SdkResponseEventKafkaConsumer;
import io.harness.execution.consumers.sdk.response.SdkResponseEventRedisConsumer;
import io.harness.execution.consumers.sdk.response.SdkResponseSpawnEventKafkaConsumer;
import io.harness.execution.consumers.sdk.response.SdkResponseSpawnEventRedisConsumer;
import io.harness.execution.consumers.sdk.response.SdkStepResponseEventKafkaConsumer;
import io.harness.execution.consumers.sdk.response.SdkStepResponseEventRedisConsumer;
import io.harness.ff.FeatureFlagConfig;
import io.harness.filter.HttpServiceLoopDetectionFilter;
import io.harness.filter.LoopDetectionAndPrevention;
import io.harness.filter.task.AddUniqueIdParentIdToFilterEntitiesJob;
import io.harness.gitsync.GitSdkConfiguration;
import io.harness.gitsync.GitSyncSdkInitHelper;
import io.harness.gitsync.events.AbstractGitSyncSdkModule;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.NoOpGitSyncSdkServiceImpl;
import io.harness.gitsync.persistance.testing.NoOpGitAwarePersistenceImpl;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.gitsync.sdk.GitSyncEntitiesConfiguration;
import io.harness.gitsync.sdk.GitSyncSdkConfiguration;
import io.harness.govern.ProviderModule;
import io.harness.governance.DefaultConnectorRefExpansionHandler;
import io.harness.graph.consumer.GraphCDCConsumer;
import io.harness.graph.stepDetail.NodeExecutionInfoServiceImpl;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.grpc.interceptor.GrpcServiceLoopDetectionModule;
import io.harness.health.HealthMonitor;
import io.harness.health.HealthService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.interfaces.PersistenceIterator;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.metrics.MetricRegistryModule;
import io.harness.metrics.PipelineTelemetryRecordsJob;
import io.harness.metrics.modules.PrometheusMetricsModule;
import io.harness.metrics.observers.PipelineExecutionMetricsObserver;
import io.harness.metrics.observers.StepExecutionMetricsObserver;
import io.harness.migration.ng.MigrationProvider;
import io.harness.migration.ng.NGMigrationConfiguration;
import io.harness.migration.ng.NGMigrationSdkInitHelper;
import io.harness.migration.ng.NGMigrationSdkModule;
import io.harness.ng.DbAliases;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.TraceFilter;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.NotAllowedExceptionMapper;
import io.harness.ng.core.exceptionmappers.NotFoundExceptionMapper;
import io.harness.ng.core.exceptionmappers.NotSupportedExceptionMapper;
import io.harness.ng.core.exceptionmappers.QueryParamExceptionMapper;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.ng.core.filter.ApiResponseFilter;
import io.harness.ng.core.metrics.ProjectEntityMigrationMetricsConfig;
import io.harness.notification.module.NotificationClientModule;
import io.harness.observers.PipelineExecutionSummaryFailureInfoUpdateHandler;
import io.harness.outbox.eventpoll.OutboxEventPollService;
import io.harness.persistence.HPersistence;
import io.harness.persistence.store.Store;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.plancreator.strategy.StrategyConstants;
import io.harness.plancreator.strategy.StrategyMaxConcurrencyRestrictionUsageImpl;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.annotations.PipelineServiceAuthIfHasApiKey;
import io.harness.pms.annotations.PipelineServiceAuthIfHasAuthHeader;
import io.harness.pms.approval.ApprovalInstanceExpirationJob;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.conversion.iterator.ConversionJobIterator;
import io.harness.pms.event.PMSEventConsumerService;
import io.harness.pms.event.bulkReconciliation.PipelineBulkReconciliationStreamConsumer;
import io.harness.pms.event.filter.AsyncFilterCreationStreamConsumer;
import io.harness.pms.event.gitNotification.StageStatusEventKafkaConsumer;
import io.harness.pms.event.handlers.OrchestrationExecutionPmsEventHandlerRegistrar;
import io.harness.pms.event.overviewLandingPage.PipelineExecutionSummaryRedisEventConsumer;
import io.harness.pms.event.overviewLandingPage.PipelineExecutionSummaryRedisEventConsumerSnapshot;
import io.harness.pms.event.overviewLandingPage.kafka.CdcKafkaConfig;
import io.harness.pms.event.overviewLandingPage.kafka.CdcKafkaConstants;
import io.harness.pms.event.overviewLandingPage.kafka.CdcKafkaConsumerConfig;
import io.harness.pms.event.overviewLandingPage.kafka.PipelineExecutionSummaryKafkaConsumer;
import io.harness.pms.event.pollingevent.PollingEventStreamConsumer;
import io.harness.pms.event.preStepCheckObserver.PreStepCheckPolicyEvaluationHandler;
import io.harness.pms.event.systemevents.SystemEventTriggerStreamConsumer;
import io.harness.pms.event.triggerwebhookevent.TriggerExecutionEventStreamConsumer;
import io.harness.pms.event.webhookevent.CustomTriggerWebhookEventQueueProcessor;
import io.harness.pms.event.webhookevent.EventListenerStepEventQueueProcessor;
import io.harness.pms.event.webhookevent.EventListenerStepEventStreamConsumer;
import io.harness.pms.event.webhookevent.WebhookEventQueueProcessor;
import io.harness.pms.event.webhookevent.WebhookEventStreamConsumer;
import io.harness.pms.events.base.PipelineEventConsumerController;
import io.harness.pms.inputset.gitsync.InputSetEntityGitSyncHelper;
import io.harness.pms.inputset.gitsync.dto.InputSetYamlDTO;
import io.harness.pms.instrumentaion.handler.InstrumentationPipelineEndEventHandler;
import io.harness.pms.migration.DatabaseSetupMigrationProvider;
import io.harness.pms.migration.PMSDeleteEntitiesMigrationService;
import io.harness.pms.migration.PipelineCoreMigrationProvider;
import io.harness.pms.migration.PipelineRoleMigrationService;
import io.harness.pms.migration.RolesMigrationService;
import io.harness.pms.migration.TriggersMigrationService;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.migration.BackfillGitConnectorForInputSetsJob;
import io.harness.pms.ngpipeline.inputset.observers.InputSetPipelineObserver;
import io.harness.pms.notification.orchestration.handlers.NodeExecutionOutboxHandler;
import io.harness.pms.notification.orchestration.handlers.NotificationInformHandler;
import io.harness.pms.notification.orchestration.handlers.PipelineEventNotificationHandler;
import io.harness.pms.notification.orchestration.handlers.StageStartNotificationHandler;
import io.harness.pms.notification.orchestration.handlers.StageStatusUpdateNotificationEventHandler;
import io.harness.pms.outbox.PipelineOutboxEventHandler;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.api.PipelinePatchRequestBodyMixin;
import io.harness.pms.pipeline.gitsync.helper.PipelineEntityGitSyncHelper;
import io.harness.pms.pipeline.labels.OrchestrationEndLabelsResolveHandler;
import io.harness.pms.pipeline.observer.entity.BranchSequenceObserver;
import io.harness.pms.pipeline.observer.entity.PipelineEntityCrudObserver;
import io.harness.pms.pipeline.observer.entity.PipelineMetadataObserver;
import io.harness.pms.pipeline.setupusage.PipelineSetupUsageHelper;
import io.harness.pms.pipelinedelete.jobs.PipelineDeleteProcessorIterator;
import io.harness.pms.plan.creation.PipelineServiceFilterCreationResponseMerger;
import io.harness.pms.plan.creation.PipelineServiceInternalInfoProvider;
import io.harness.pms.plan.execution.PmsExecutionServiceInfoProvider;
import io.harness.pms.plan.execution.handlers.ExecutionInfoUpdateEventHandler;
import io.harness.pms.plan.execution.handlers.ExecutionSummaryCreateEventHandler;
import io.harness.pms.plan.execution.handlers.PipelineStatusUpdateEventHandler;
import io.harness.pms.plan.execution.handlers.PipelineTimeoutUpdateHandler;
import io.harness.pms.plan.execution.handlers.PlanStatusEventEmitterHandler;
import io.harness.pms.plan.execution.handlers.SecretResolutionEventHandler;
import io.harness.pms.plan.execution.helper.PlanCreationQueueRequestHelper;
import io.harness.pms.plan.execution.queue.PlanCreationDbQueuePoller;
import io.harness.pms.plan.execution.queue.PlanCreationQueuePoller;
import io.harness.pms.resourcerestraint.reconciliation.job.ResourceRestraintReconciliationService;
import io.harness.pms.sdk.PmsSdkInitHelper;
import io.harness.pms.sdk.PmsSdkInstanceCacheMonitor;
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
import io.harness.pms.sdk.execution.events.progress.NodeProgressEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.progress.ProgressEventRedisConsumer;
import io.harness.pms.serializer.json.PmsBeansJacksonModule;
import io.harness.pms.statusreconciliation.job.PipelineExecutionStatusReconciliationService;
import io.harness.pms.tags.OrchestrationEndTagsResolveHandler;
import io.harness.pms.template.ExecutionTemplateReferenceSummarySaveHandler;
import io.harness.pms.triggers.scheduled.ScheduledTriggerHandler;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionService;
import io.harness.pms.triggers.webhook.service.impl.TriggerWebhookExecutionServiceImpl;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.projectmovement.gcs.ParentUniqueIdMigrationForPipelineExecutionSummaryForGCS;
import io.harness.projectmovement.mongo.AddUniqueIdParentIdToCdcEntitiesJob;
import io.harness.projectmovement.mongo.AddUniqueIdParentIdToEntitiesJob;
import io.harness.projectmovement.mongo.ProjectEntityMigrationMetricsJob;
import io.harness.queue.QueueListenerController;
import io.harness.reconciliation.jobs.ExecutionRetentionReconciliationIterator;
import io.harness.reconciliation.jobs.ExecutionRetentionReconciliationMonitorIterator;
import io.harness.registrars.PipelineServiceFacilitatorRegistrar;
import io.harness.registrars.PipelineServiceStepRegistrar;
import io.harness.request.RequestContextFilter;
import io.harness.resource.VersionInfoResource;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.search.jobs.SearchIndexMigrationIterator;
import io.harness.security.InternalApiAuthFilter;
import io.harness.security.MeshIdentityBootstrap;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.ScopeInfoFilter;
import io.harness.security.annotations.AnnotationsAuth;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.mesh.MeshIdentityConfig;
import io.harness.serializer.PipelineServiceUtilAdviserRegistrar;
import io.harness.serializer.jackson.PipelineServiceJacksonModule;
import io.harness.service.GraphGenerationService;
import io.harness.service.impl.DelegateAsyncServiceImpl;
import io.harness.service.impl.DelegateProgressServiceImpl;
import io.harness.service.impl.DelegateSyncServiceImpl;
import io.harness.service.impl.GraphGenerationServiceImpl;
import io.harness.springdata.HMongoTemplate;
import io.harness.steps.PodCleanupUpdateEventHandler;
import io.harness.steps.approval.step.custom.IrregularApprovalInstanceHandler;
import io.harness.steps.barriers.event.BarrierEventHandler;
import io.harness.steps.barriers.event.BarrierWithinStrategyExpander;
import io.harness.steps.barriers.service.BarrierServiceImpl;
import io.harness.steps.common.NodeExecutionMetadataDeleteObserver;
import io.harness.steps.resourcerestraint.ResourceRestraintInitializer;
import io.harness.steps.resourcerestraint.ResourceRestraintObserver;
import io.harness.steps.resourcerestraint.service.ResourceRestraintPersistenceMonitor;
import io.harness.telemetry.TelemetryReporter;
import io.harness.telemetry.filter.APIAuthTelemetryFilter;
import io.harness.telemetry.filter.APIAuthTelemetryResponseFilter;
import io.harness.telemetry.filter.APIErrorsTelemetrySenderFilter;
import io.harness.telemetry.filter.TerraformTelemetryFilter;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ScalingThreadPoolExecutor;
import io.harness.timeout.engine.TimeoutEngine;
import io.harness.token.remote.TokenClient;
import io.harness.tracing.MongoRedisTracer;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.NotifierScheduledExecutorService;
import io.harness.waiter.NotifyQueuePublisherRegister;
import io.harness.waiter.PmsNotifyEventConsumerRedis;
import io.harness.waiter.PmsNotifyEventPublisher;
import io.harness.waiter.ProgressUpdateService;
import io.harness.waiter.nrcsp.NotifyResponseCleanerFactory;
import io.harness.waiter.nrcsp.NotifyResponseCleanerSpringPersistence;
import io.harness.waiter.nrcsp.NotifyResponseIterator;
import io.harness.waiter.nrcsp.ProgressUpdateIterator;
import io.harness.waiter.persistence.WaitNotifyCollectionNameResolver;

import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.lifecycle.Managed;
import io.serializer.HObjectMapper;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.springframework.data.mongodb.core.MongoTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineServiceApplication extends Application<PipelineServiceConfiguration> {
  private static final SecureRandom random = new SecureRandom();
  private static final String APPLICATION_NAME = "Pipeline Service Application";

  private final MetricRegistry metricRegistry = new MetricRegistry();
  private final MetricRegistry threadPoolMetricRegistry = new MetricRegistry();
  private HarnessMetricRegistry harnessMetricRegistry;

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));

    new PipelineServiceApplication().run(args);
  }

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<PipelineServiceConfiguration> bootstrap) {
    initializeLogging();
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addCommand(new ScanClasspathMetadataCommand());
    bootstrap.addCommand(new GenerateOpenApiSpecCommand());

    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    configureObjectMapper(bootstrap.getObjectMapper());
    bootstrap.addBundle(new SwaggerV2Bundle<>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(PipelineServiceConfiguration appConfig) {
        return appConfig.getSwaggerBundleConfiguration();
      }

      @Override
      protected Collection<Class<?>> getResourceClasses() {
        return HARNESS_RESOURCE_CLASSES;
      }
    });
    bootstrap.setMetricRegistry(metricRegistry);
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    HObjectMapper.configureObjectMapperForNG(mapper);
    mapper.registerModule(new PmsBeansJacksonModule());
    mapper.registerModule(new PipelineServiceJacksonModule());
    PipelinePatchRequestBodyMixin.configure(mapper);
  }

  @Override
  public void run(PipelineServiceConfiguration appConfig, Environment environment) throws Exception {
    log.info("Starting Pipeline Service Application ...");
    MaintenanceController.forceMaintenance(true);

    ExecutorModule.getInstance().setExecutorService(new InstrumentedExecutorService(
        new ScalingThreadPoolExecutor(appConfig.getCommonPoolConfig(), "main-app-pool-%d"), threadPoolMetricRegistry,
        "main"));
    List<Module> modules = new ArrayList<>();
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      PipelineServiceConfiguration configuration() {
        return appConfig;
      }

      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return appConfig.getDbAliases();
      }
    });
    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return appConfig.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return appConfig.getFeatureFlagConfig();
      }
    });
    modules.add(new NotificationClientModule(appConfig.getNotificationClientConfiguration()));
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(MetricRegistry.class).toInstance(metricRegistry);
      }
    });
    modules.add(new MetricRegistryModule(metricRegistry, threadPoolMetricRegistry));
    PrometheusMetricsModule prometheusMetricsModule = new PrometheusMetricsModule();
    LoopDetectionAndPrevention loopDetectionAndPrevention =
        new LoopDetectionAndPrevention(prometheusMetricsModule.metricService);
    modules.add(prometheusMetricsModule);
    modules.add(PipelineServiceModule.getInstance(appConfig, threadPoolMetricRegistry));
    modules.add(NGMigrationSdkModule.getInstance());
    CacheModule cacheModule = new CacheModule(appConfig.getCacheConfig());
    modules.add(cacheModule);
    if (appConfig.isShouldDeployWithGitSync()) {
      GitSyncSdkConfiguration gitSyncSdkConfiguration = getGitSyncConfiguration(appConfig);
      modules.add(new AbstractGitSyncSdkModule() {
        @Override
        public GitSyncSdkConfiguration getGitSyncSdkConfiguration() {
          return gitSyncSdkConfiguration;
        }
      });
    } else {
      modules.add(new SCMGrpcClientModule(appConfig.getGitSdkConfiguration().getScmConnectionConfig()));
      modules.add(new AbstractGitSyncSdkModule() {
        @Override
        protected void configure() {
          bind(GitAwarePersistence.class).to(NoOpGitAwarePersistenceImpl.class);
          bind(GitSyncSdkService.class).to(NoOpGitSyncSdkServiceImpl.class);
        }

        @Override
        public GitSyncSdkConfiguration getGitSyncSdkConfiguration() {
          return null;
        }
      });
    }

    // Pipeline Service Modules
    PmsSdkConfiguration pmsSdkConfiguration = getPmsSdkConfiguration(appConfig);
    modules.add(KafkaModule.getInstance(appConfig.getKafkaModuleConfig()));
    modules.add(PmsSdkModule.getInstance(pmsSdkConfiguration, threadPoolMetricRegistry, false));
    modules.add(PipelineServiceUtilityModule.getInstance());
    modules.add(new GrpcServiceLoopDetectionModule(loopDetectionAndPrevention));

    // Graph Generation Kafka Streams Module (CDC-based graph projection)
    // Always install the module so GraphCDCService is bound; the consumer itself is gated by config.isEnabled().
    modules.add(appConfig.getGraphGenerationStreamsConfig() != null
            ? GraphGenerationStreamsModule.withConfig(appConfig.getGraphGenerationStreamsConfig())
            : GraphGenerationStreamsModule.withDefaults());

    Injector injector = Guice.createInjector(modules);
    registerStores(appConfig, injector);
    registerEventListeners(injector);
    registerWaitEnginePublishers(injector);
    registerScheduledJobs(injector, appConfig);
    registerCorsFilter(appConfig, environment);
    registerResources(environment, injector);
    registerJerseyProviders(environment, injector);
    registerManagedBeans(environment, injector, appConfig);
    registerAuthFilters(appConfig, environment, injector);
    registerAccountAccessFilter(appConfig, environment);
    registerScopeInfoFilter(environment, injector);
    registerAPIAuthTelemetryFilters(appConfig, environment, injector);
    registerApiResponseFilter(environment, injector);
    registerHealthCheck(environment, injector);
    registerObservers(appConfig, injector);
    registerRequestContextFilter(environment);
    registerOasResource(appConfig, environment, injector);
    initializeSdkInstanceCacheSync(injector);
    initializeEnforcementSdk(injector);

    harnessMetricRegistry = injector.getInstance(HarnessMetricRegistry.class);
    registerMongoIterators(appConfig, injector);

    injector.getInstance(PrimaryVersionChangeScheduler.class).registerExecutors();

    if (appConfig.isShouldDeployWithGitSync()) {
      registerGitSyncSdk(appConfig, injector, environment);
    }

    registerCorrelationFilter(environment, injector);

    if (!appConfig.isDisableFreezeNotificationTemplate()) {
      registerNotificationTemplates(injector);
    }
    registerKafkaConsumer(injector, appConfig.getEventsFrameworkConfiguration(), environment);
    registerCdcKafkaConsumers(injector, appConfig, environment);
    registerPmsSdkEvents(appConfig, injector, environment);

    initializeGrpcServer(injector);
    registerPmsSdk(appConfig, injector, environment);
    registerMigrations(injector);

    if (BooleanUtils.isTrue(appConfig.getEnableOpentelemetry())) {
      registerTraceFilter(environment, injector);
    }

    if (BooleanUtils.isTrue(appConfig.getEnableLoopDetection())) {
      registerHttpServiceLoopDetectionFilter(appConfig, environment, loopDetectionAndPrevention);
    }

    log.info("PipelineServiceApplication DEPLOY_VERSION = " + System.getenv().get(DEPLOY_VERSION));
    if (DeployVariant.isCommunity(System.getenv().get(DEPLOY_VERSION))) {
      initializePipelineMonitoring(injector);
    } else {
      log.info("PipelineServiceApplication DEPLOY_VERSION is not COMMUNITY");
    }

    MaintenanceController.forceMaintenance(false);
  }

  private static void registerMongoIterators(PipelineServiceConfiguration appConfig, Injector injector) {
    PipelineServiceIteratorsConfig iteratorsConfig = appConfig.getIteratorsConfig();

    if (WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.ORCHESTRATION_ENGINE)) {
      registerOrchestrationEngineIterators(appConfig, injector, iteratorsConfig);
    }

    if (WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.GRAPH)) {
      registerGraphServiceIterators(injector, iteratorsConfig);
    }
  }

  private static void registerOrchestrationEngineIterators(
      PipelineServiceConfiguration appConfig, Injector injector, PipelineServiceIteratorsConfig iteratorsConfig) {
    if (appConfig.getEnableCustomWebhookRedisBatchModeIterator()) {
      injector.getInstance(TriggerWebhookExecutionServiceImpl.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name("Custom-WebhookEventProcessor")
                  .poolSize(iteratorsConfig.getCustomWebhookRedisModeConfig().getThreadPoolSize())
                  .batchSize(iteratorsConfig.getCustomWebhookRedisModeConfig().getRedisBatchSize())
                  .lockTimeout(iteratorsConfig.getCustomWebhookRedisModeConfig().getRedisLockTimeout())
                  .interval(Duration.ofSeconds(
                      iteratorsConfig.getCustomWebhookRedisModeConfig().getThreadPoolIntervalInSeconds()))
                  .build(),
              Duration.ofSeconds(iteratorsConfig.getCustomWebhookRedisModeConfig().getTargetIntervalInSeconds()));
    } else {
      injector.getInstance(TriggerWebhookExecutionService.class)
          .registerIterators(iteratorsConfig.getTriggerWebhookConfig());
    }
    injector.getInstance(ScheduledTriggerHandler.class).registerIterators(iteratorsConfig.getScheduleTriggerConfig());
    if (PersistenceIterator.ProcessMode.REDIS_BATCH.name().equals(appConfig.getTimeoutIteratorMode())) {
      injector.getInstance(TimeoutEngine.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name("TimeoutEngine")
                  .poolSize(iteratorsConfig.getTimeoutEngineRedisConfig().getThreadPoolSize())
                  .batchSize(iteratorsConfig.getTimeoutEngineRedisConfig().getRedisBatchSize())
                  .lockTimeout(iteratorsConfig.getTimeoutEngineRedisConfig().getRedisLockTimeout())
                  .interval(Duration.ofSeconds(
                      iteratorsConfig.getTimeoutEngineRedisConfig().getThreadPoolIntervalInSeconds()))
                  .build(),
              Duration.ofSeconds(iteratorsConfig.getTimeoutEngineRedisConfig().getTargetIntervalInSeconds()));
    } else {
      injector.getInstance(TimeoutEngine.class)
          .createAndStartIterator(PersistenceIteratorFactory.PumpExecutorOptions.builder()
                                      .name("TimeoutEngine")
                                      .poolSize(iteratorsConfig.getTimeoutEngineConfig().getThreadPoolCount())
                                      .build(),
              Duration.ofSeconds(iteratorsConfig.getTimeoutEngineConfig().getTargetIntervalInSeconds()));
    }

    if (appConfig.isEnableWaitNotifyEngineOptimisation()) {
      injector.getInstance(NotifyResponseIterator.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name(WaitNotifyCollectionNameResolver.qualifyIteratorLockName("PIPELINE-NotifyResponseIterator"))
                  .poolSize(iteratorsConfig.getNotifyResponseRedisConfig().getThreadPoolSize())
                  .batchSize(iteratorsConfig.getNotifyResponseRedisConfig().getRedisBatchSize())
                  .lockTimeout(iteratorsConfig.getNotifyResponseRedisConfig().getRedisLockTimeout())
                  .interval(Duration.ofSeconds(
                      iteratorsConfig.getNotifyResponseRedisConfig().getThreadPoolIntervalInSeconds()))
                  .threadSleepInterval(iteratorsConfig.getNotifyResponseRedisConfig().getThreadSleepIntervalMs())
                  .build(),
              Duration.ofSeconds(iteratorsConfig.getNotifyResponseRedisConfig().getTargetIntervalInSeconds()));
    }

    injector.getInstance(BarrierServiceImpl.class).registerIterators(iteratorsConfig.getBarrierConfig());
    if (iteratorsConfig.getSearchIndexMigrationConfig().isEnabled()) {
      injector.getInstance(SearchIndexMigrationIterator.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name("SearchIndexMigrationIterator")
                  .batchSize(iteratorsConfig.getSearchIndexMigrationConfig().getRedisBatchSize())
                  .lockTimeout(iteratorsConfig.getSearchIndexMigrationConfig().getRedisLockTimeout())
                  .interval(Duration.ofSeconds(
                      iteratorsConfig.getSearchIndexMigrationConfig().getThreadPoolIntervalInSeconds()))
                  .poolSize(iteratorsConfig.getSearchIndexMigrationConfig().getThreadPoolSize())
                  .build(),
              Duration.ofSeconds(iteratorsConfig.getSearchIndexMigrationConfig().getTargetIntervalInSeconds()));
    }
    if (iteratorsConfig.getGraphDeleteConfig().isEnabled()) {
      injector.getInstance(GraphDeleteServiceHelper.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name("GraphDeleteEventProcessor")
                  .batchSize(iteratorsConfig.getGraphDeleteConfig().getRedisBatchSize())
                  .lockTimeout(iteratorsConfig.getGraphDeleteConfig().getRedisLockTimeout())
                  .interval(Duration.ofSeconds(iteratorsConfig.getGraphDeleteConfig().getThreadPoolIntervalInSeconds()))
                  .poolSize(iteratorsConfig.getGraphDeleteConfig().getThreadPoolSize())
                  .build(),
              Duration.ofSeconds(iteratorsConfig.getGraphDeleteConfig().getTargetIntervalInSeconds()));
    }
    if (iteratorsConfig.getExecutionRetentionReconciliationConfig().isEnabled()) {
      injector.getInstance(ExecutionRetentionReconciliationIterator.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name("ExecutionRetentionReconciliationIterator")
                  .batchSize(iteratorsConfig.getExecutionRetentionReconciliationConfig().getRedisBatchSize())
                  .lockTimeout(iteratorsConfig.getExecutionRetentionReconciliationConfig().getRedisLockTimeout())
                  .interval(Duration.ofSeconds(
                      iteratorsConfig.getExecutionRetentionReconciliationConfig().getThreadPoolIntervalInSeconds()))
                  .poolSize(iteratorsConfig.getExecutionRetentionReconciliationConfig().getThreadPoolSize())
                  .build(),
              Duration.ofSeconds(
                  iteratorsConfig.getExecutionRetentionReconciliationConfig().getTargetIntervalInSeconds()));
    }
    injector.getInstance(IrregularApprovalInstanceHandler.class)
        .registerIterators(iteratorsConfig.getApprovalInstanceConfig());
    injector.getInstance(ResourceRestraintPersistenceMonitor.class)
        .registerIterators(iteratorsConfig.getResourceRestraintConfig());
    injector.getInstance(InterruptMonitor.class).registerIterators(iteratorsConfig.getInterruptMonitorConfig());
    if (iteratorsConfig.getStuckNodeExecutionsRedisModeConfig().isEnabled()) {
      injector.getInstance(StuckNodeExecutionsMonitor.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name("StuckNodeExecutionsMonitor")
                  .batchSize(iteratorsConfig.getStuckNodeExecutionsRedisModeConfig().getRedisBatchSize())
                  .lockTimeout(iteratorsConfig.getStuckNodeExecutionsRedisModeConfig().getRedisLockTimeout())
                  .interval(Duration.ofSeconds(
                      iteratorsConfig.getStuckNodeExecutionsRedisModeConfig().getTargetIntervalInSeconds()))
                  .poolSize(iteratorsConfig.getStuckNodeExecutionsRedisModeConfig().getThreadPoolSize())
                  .build(),
              Duration.ofSeconds(iteratorsConfig.getStuckNodeExecutionsRedisModeConfig().getTargetIntervalInSeconds()));
    }
    if (iteratorsConfig.getExecutionRetentionReconciliationMonitor().isEnabled()) {
      injector.getInstance(ExecutionRetentionReconciliationMonitorIterator.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name("ExecutionRetentionReconciliationMonitorIterator")
                  .batchSize(iteratorsConfig.getExecutionRetentionReconciliationMonitor().getRedisBatchSize())
                  .lockTimeout(iteratorsConfig.getExecutionRetentionReconciliationMonitor().getRedisLockTimeout())
                  .interval(Duration.ofSeconds(
                      iteratorsConfig.getExecutionRetentionReconciliationMonitor().getThreadPoolIntervalInSeconds()))
                  .poolSize(iteratorsConfig.getExecutionRetentionReconciliationMonitor().getThreadPoolSize())
                  .build(),
              Duration.ofSeconds(
                  iteratorsConfig.getExecutionRetentionReconciliationMonitor().getTargetIntervalInSeconds()));
    }
    if (iteratorsConfig.getConversionJobConfig() != null && iteratorsConfig.getConversionJobConfig().isEnabled()) {
      injector.getInstance(ConversionJobIterator.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name("ConversionJobIterator")
                  .batchSize(iteratorsConfig.getConversionJobConfig().getRedisBatchSize())
                  .lockTimeout(iteratorsConfig.getConversionJobConfig().getRedisLockTimeout())
                  .interval(
                      Duration.ofSeconds(iteratorsConfig.getConversionJobConfig().getThreadPoolIntervalInSeconds()))
                  .poolSize(iteratorsConfig.getConversionJobConfig().getThreadPoolSize())
                  .build(),
              Duration.ofSeconds(iteratorsConfig.getConversionJobConfig().getTargetIntervalInSeconds()));
    }
  }

  private static void registerGraphServiceIterators(Injector injector, PipelineServiceIteratorsConfig iteratorsConfig) {
    if (iteratorsConfig.getExecutionGitMetadataReconciliationConfig().isEnabled()) {
      injector.getInstance(ExecutionGitMetadataReconciliationIterator.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name("ExecutionGitMetadataReconciliationIterator")
                  .batchSize(iteratorsConfig.getExecutionGitMetadataReconciliationConfig().getRedisBatchSize())
                  .lockTimeout(iteratorsConfig.getExecutionGitMetadataReconciliationConfig().getRedisLockTimeout())
                  .interval(Duration.ofSeconds(
                      iteratorsConfig.getExecutionGitMetadataReconciliationConfig().getThreadPoolIntervalInSeconds()))
                  .poolSize(iteratorsConfig.getExecutionGitMetadataReconciliationConfig().getThreadPoolSize())
                  .build(),
              Duration.ofSeconds(
                  iteratorsConfig.getExecutionGitMetadataReconciliationConfig().getTargetIntervalInSeconds()));
    }

    if (iteratorsConfig.getPipelineDeleteConfig().isEnabled()) {
      injector.getInstance(PipelineDeleteProcessorIterator.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name("PipelineDeleteProcessorIterator")
                  .batchSize(iteratorsConfig.getPipelineDeleteConfig().getRedisBatchSize())
                  .lockTimeout(iteratorsConfig.getPipelineDeleteConfig().getRedisLockTimeout())
                  .interval(
                      Duration.ofSeconds(iteratorsConfig.getPipelineDeleteConfig().getThreadPoolIntervalInSeconds()))
                  .poolSize(iteratorsConfig.getPipelineDeleteConfig().getThreadPoolSize())
                  .build(),
              Duration.ofSeconds(iteratorsConfig.getPipelineDeleteConfig().getTargetIntervalInSeconds()));
    }
  }

  private void registerAccountAccessFilter(PipelineServiceConfiguration appConfig, Environment environment) {
    environment.jersey().register(new PipelineServiceAccountAccessFilter(appConfig.isEnableTenantIsolationFilter()));
  }

  private void registerScopeInfoFilter(Environment environment, Injector injector) {
    environment.jersey().getResourceConfig().register(new AbstractBinder() {
      @Override
      protected void configure() {
        bindFactory(ScopeInfoFactory.class).to(ScopeInfo.class);
      }
    });
    environment.jersey().register(
        new ScopeInfoFilter(injector.getInstance(Key.get(ScopeInfoClient.class, Names.named("PRIVILEGED"))),
            injector.getInstance(PmsFeatureFlagService.class)));
  }

  private void initializeSdkInstanceCacheSync(Injector injector) {
    injector.getInstance(PmsSdkInstanceCacheMonitor.class).scheduleCacheSync();
  }

  private void initializePipelineMonitoring(Injector injector) {
    log.info("Initializing PipelineMonitoring");
    injector.getInstance(PipelineTelemetryRecordsJob.class).scheduleTasks();
  }

  private void initializeGrpcServer(Injector injector) {
    log.info("Initializing gRPC servers...");
    ServiceManager serviceManager = injector.getInstance(ServiceManager.class).startAsync();
    serviceManager.awaitHealthy();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> serviceManager.stopAsync().awaitStopped()));
  }

  private void registerOasResource(PipelineServiceConfiguration appConfig, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(appConfig.getOasConfig());
    environment.jersey().register(openApiResource);
  }

  private void registerStores(PipelineServiceConfiguration configuration, Injector injector) {
    final HPersistence persistence = injector.getInstance(HPersistence.class);
    if (isNotEmpty(configuration.getMongoConfig().getUri())) {
      persistence.register(Store.builder().name(DbAliases.PMS).build(), configuration.getMongoConfig().getUri());
    }
  }

  private static void registerObservers(PipelineServiceConfiguration appConfig, Injector injector) {
    NodeExecutionInfoServiceImpl pmsGraphStepDetailsService =
        (NodeExecutionInfoServiceImpl) injector.getInstance(Key.get(NodeExecutionInfoService.class));
    pmsGraphStepDetailsService.getStepDetailsUpdateObserverSubject().register(
        injector.getInstance(Key.get(OrchestrationLogPublisher.class)));

    // Register Pipeline Outbox Observers
    PipelineOutboxEventHandler pipelineOutboxEventHandler =
        injector.getInstance(Key.get(PipelineOutboxEventHandler.class));
    pipelineOutboxEventHandler.getPipelineActionObserverSubject().register(
        injector.getInstance(Key.get(PipelineSetupUsageHelper.class)));
    pipelineOutboxEventHandler.getPipelineActionObserverSubject().register(
        injector.getInstance(Key.get(PipelineEntityCrudObserver.class)));
    pipelineOutboxEventHandler.getPipelineActionObserverSubject().register(
        injector.getInstance(Key.get(InputSetPipelineObserver.class)));
    // PipelineMetadataObserver is also added so that it is also deleted in sync so that runsequence starts with 0 again
    // if same pipeline gets created
    pipelineOutboxEventHandler.getPipelineActionObserverSubject().register(
        injector.getInstance(Key.get(PipelineMetadataObserver.class)));
    // BranchSequenceObserver cleans up branch sequence data when pipeline is deleted (CI-19987)
    pipelineOutboxEventHandler.getPipelineActionObserverSubject().register(
        injector.getInstance(Key.get(BranchSequenceObserver.class)));

    NodeExecutionServiceImpl nodeExecutionService =
        (NodeExecutionServiceImpl) injector.getInstance(Key.get(NodeExecutionService.class));

    NodeStartHelper nodeStartHelper = injector.getInstance(Key.get(NodeStartHelper.class));

    // NodeStatusUpdateObserver
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(PlanExecutionService.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(StageStatusUpdateNotificationEventHandler.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(BarrierEventHandler.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(NodeExecutionStatusUpdateEventHandler.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(ResourceRestraintObserver.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(TimeoutInstanceRemover.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(PipelineExecutionSummaryFailureInfoUpdateHandler.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(NodeExecutionOutboxHandler.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(PodCleanupUpdateEventHandler.class)));
    nodeExecutionService.getNodeStatusUpdateSubject().register(
        injector.getInstance(Key.get(StepExecutionMetricsObserver.class)));

    // NodeExecutionDeleteObserver
    nodeExecutionService.getNodeDeleteObserverSubject().register(
        injector.getInstance(NodeExecutionMetadataDeleteObserver.class));

    // NodeExecutionStartObserver
    nodeStartHelper.getNodeExecutionStartSubject().register(
        injector.getInstance(Key.get(StageStartNotificationHandler.class)));
    nodeStartHelper.getNodeExecutionStartSubject().register(
        injector.getInstance(Key.get(NodeExecutionOutboxHandler.class)));
    nodeStartHelper.getNodeExecutionStartSubject().register(
        injector.getInstance(Key.get(PipelineTimeoutUpdateHandler.class)));

    PlanStatusEventEmitterHandler planStatusEventEmitterHandler =
        injector.getInstance(Key.get(PlanStatusEventEmitterHandler.class));
    planStatusEventEmitterHandler.getPlanExecutionSubject().register(
        injector.getInstance(Key.get(NotificationInformHandler.class)));

    PlanExecutionServiceImpl planExecutionService =
        (PlanExecutionServiceImpl) injector.getInstance(Key.get(PlanExecutionService.class));
    planExecutionService.getPlanStatusUpdateSubject().register(
        injector.getInstance(Key.get(ExecutionInfoUpdateEventHandler.class)));
    planExecutionService.getPlanStatusUpdateSubject().register(planStatusEventEmitterHandler);
    planExecutionService.getPlanStatusUpdateSubject().register(
        injector.getInstance(Key.get(PipelineStatusUpdateEventHandler.class)));
    planExecutionService.getPlanStatusUpdateSubject().register(
        injector.getInstance(Key.get(OrchestrationLogPublisher.class)));

    // Register PlanExecutionDeleteObserver
    planExecutionService.getPlanExecutionDeleteObserverSubject().register(
        injector.getInstance(Key.get(PlanExecutionMetadataDeleteObserver.class)));
    // Register ResourceRestraintInstanceDeleteObserver
    planExecutionService.getPlanExecutionDeleteObserverSubject().register(
        injector.getInstance(Key.get(PipelineResourceRestraintInstanceDeleteObserver.class)));

    // Register planCreationRequest queue observers
    PlanCreationQueueRequestHelper planCreationQueueRequestHelper =
        injector.getInstance(Key.get(PlanCreationQueueRequestHelper.class));
    planCreationQueueRequestHelper.getOrchestrationStartSubject().register(
        injector.getInstance(Key.get(OrchestrationStartEventHandler.class)));
    planCreationQueueRequestHelper.getOrchestrationStartSubject().register(
        injector.getInstance(Key.get(ExecutionSummaryCreateEventHandler.class)));

    PlanExecutionStrategy planExecutionStrategy = injector.getInstance(Key.get(PlanExecutionStrategy.class));
    // StartObservers
    planExecutionStrategy.getOrchestrationStartSubject().register(
        injector.getInstance(Key.get(ResourceRestraintInitializer.class)));
    planExecutionStrategy.getOrchestrationStartSubject().register(
        injector.getInstance(Key.get(OrchestrationStartEventHandler.class)));
    planExecutionStrategy.getOrchestrationStartSubject().register(
        injector.getInstance(Key.get(ExecutionSummaryCreateEventHandler.class)));
    // End Observers
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(OrchestrationEndGraphHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(OrchestrationEndInterruptHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(NotificationInformHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(InstrumentationPipelineEndEventHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(OrchestrationEndTagsResolveHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(OrchestrationEndLabelsResolveHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(PipelineStatusUpdateEventHandler.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(ResourceRestraintObserver.class)));
    planExecutionStrategy.getOrchestrationEndSubject().register(
        injector.getInstance(Key.get(PipelineExecutionMetricsObserver.class)));

    SpawnChildrenRequestProcessor spawnChildrenRequestProcessor =
        injector.getInstance(Key.get(SpawnChildrenRequestProcessor.class));
    spawnChildrenRequestProcessor.getBarrierWithinStrategyExpander().register(
        injector.getInstance(Key.get(BarrierWithinStrategyExpander.class)));

    PlanNodeExecutionStrategy planNodeExecutionStrategy =
        injector.getInstance(Key.get(PlanNodeExecutionStrategy.class));
    planNodeExecutionStrategy.getNodeExecutionCreateObserverSubject().register(
        injector.getInstance(Key.get(ExecutionTemplateReferenceSummarySaveHandler.class)));

    RetryHelper retryHelper = injector.getInstance(Key.get(RetryHelper.class));
    retryHelper.getNodeExecutionCreateObserverSubject().register(
        injector.getInstance(Key.get(ExecutionTemplateReferenceSummarySaveHandler.class)));

    // Registering SecretResolutionObserver
    ExpressionsObserverFactory expressionsObserverFactory =
        injector.getInstance(Key.get(ExpressionsObserverFactory.class));
    expressionsObserverFactory.getSecretsRuntimeUsagesSubject().register(
        injector.getInstance(Key.get(SecretResolutionEventHandler.class)));

    HMongoTemplate mongoTemplate = (HMongoTemplate) injector.getInstance(MongoTemplate.class);
    mongoTemplate.getTracerSubject().register(injector.getInstance(MongoRedisTracer.class));

    // Graph Update Event Observer
    GraphGenerationServiceImpl graphGenerationService =
        (GraphGenerationServiceImpl) injector.getInstance(Key.get(GraphGenerationService.class));
    graphGenerationService.getGraphUpdateObserverSubject().register(
        injector.getInstance(Key.get(PipelineEventNotificationHandler.class)));

    PreStepCheckFacilitator preStepPolicyEvaluationFacilitator =
        injector.getInstance(Key.get(PreStepCheckFacilitator.class));
    preStepPolicyEvaluationFacilitator.getPreStepCheckSubject().register(
        injector.getInstance(Key.get(PreStepCheckPolicyEvaluationHandler.class)));
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void registerTraceFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(TraceFilter.class));
  }

  private void registerHttpServiceLoopDetectionFilter(PipelineServiceConfiguration appConfig, Environment environment,
      LoopDetectionAndPrevention loopDetectionAndPrevention) {
    log.info("Initializing HttpServiceLoopDetectionFilter with threshold {} and loop prevention {}",
        loopDetectionAndPrevention.getLoopDetectionThreshold(), loopDetectionAndPrevention.isEnableLoopPrevention());

    HttpServiceLoopDetectionFilter filterInstance = new HttpServiceLoopDetectionFilter(loopDetectionAndPrevention);
    environment.servlets()
        .addFilter("HttpServiceLoopDetectionFilter", filterInstance)
        .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
    environment.jersey().register(filterInstance);
  }

  private void registerAuthFilters(PipelineServiceConfiguration config, Environment environment, Injector injector) {
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> authAnnotationsPredicate = resourceInfoAndRequest
        -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
               && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(PipelineServiceAuth.class) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(PipelineServiceAuth.class) != null)
        || shouldCheckAuthForConditionalAuthAnnotations(resourceInfoAndRequest)
        || (resourceInfoAndRequest.getKey().getResourceMethod() != null
            && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(NextGenManagerAuth.class) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(NextGenManagerAuth.class) != null)
        || (resourceInfoAndRequest.getKey().getResourceMethod() != null
            && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(AnnotationsAuth.class) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(AnnotationsAuth.class) != null);
    // Exclude @InternalApi methods so they are handled exclusively by InternalApiAuthFilter (matches ng-manager
    // pattern)
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate =
        authAnnotationsPredicate.and(getAuthFilterPredicate(InternalApi.class).negate());
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(AuthorizationServiceHeader.BEARER.getServiceId(), config.getJwtAuthSecret());
    serviceToSecretMapping.put(
        AuthorizationServiceHeader.IDENTITY_SERVICE.getServiceId(), config.getJwtIdentityServiceSecret());
    serviceToSecretMapping.put(AuthorizationServiceHeader.DEFAULT.getServiceId(), config.getNgManagerServiceSecret());
    serviceToSecretMapping.put(AuthorizationServiceHeader.ANNOTATIONS.getServiceId(), config.getAnnotationsSecret());
    // Mesh identity dispatch: opt-in per config. See MeshIdentityConfig for the rollout matrix.
    MeshIdentityConfig mesh = config.getMeshIdentity();
    NextGenAuthenticationFilter authFilter = MeshIdentityBootstrap.buildFilter(predicate, serviceToSecretMapping,
        injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED"))), mesh);
    environment.jersey().register(authFilter);
    registerInternalApiAuthFilter(config, environment);
  }

  private void registerInternalApiAuthFilter(PipelineServiceConfiguration configuration, Environment environment) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getPipelineServiceSecret());
    environment.jersey().register(
        new InternalApiAuthFilter(getAuthFilterPredicate(InternalApi.class), null, serviceToSecretMapping));
  }

  private boolean shouldCheckAuthForConditionalAuthAnnotations(
      Pair<ResourceInfo, ContainerRequestContext> resourceInfoAndRequest) {
    if (resourceInfoAndRequest.getKey().getResourceMethod() == null) {
      return false;
    }
    boolean shouldCheckAuth = false;
    Method resourceMethod = resourceInfoAndRequest.getKey().getResourceMethod();
    MultivaluedMap<String, String> headers = resourceInfoAndRequest.getValue().getHeaders();
    if (resourceMethod.getAnnotation(PipelineServiceAuthIfHasAuthHeader.class) != null) {
      shouldCheckAuth = headers.get(NextGenAuthenticationFilter.AUTHORIZATION_HEADER) != null
          || headers.get(NextGenAuthenticationFilter.X_API_KEY) != null;
    }
    if (resourceMethod.getAnnotation(PipelineServiceAuthIfHasApiKey.class) != null) {
      shouldCheckAuth = shouldCheckAuth || headers.get(NextGenAuthenticationFilter.X_API_KEY) != null;
    }
    return shouldCheckAuth;
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthFilterPredicate(
      Class<? extends Annotation> annotation) {
    return resourceInfoAndRequest
        -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
               && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(annotation) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(annotation) != null)
        || hasAnnotationOnSuperclassMethod(resourceInfoAndRequest.getKey(), annotation);
  }

  /**
   * Guice creates CGLib subclass proxies (e.g. PipelineExecutionApiImpl$$EnhancerByGuice$$xxx), and
   * the proxy method overrides do NOT carry the original annotations. JAX-RS resolves the resource
   * method against the proxy class, so @InternalApi (declared on the real impl method) is invisible
   * via getResourceMethod().getAnnotation(...). This walks the proxy's superclasses to find the real
   * declaring class and reads the annotation from there.
   */
  private boolean hasAnnotationOnSuperclassMethod(ResourceInfo resourceInfo, Class<? extends Annotation> annotation) {
    Method resourceMethod = resourceInfo.getResourceMethod();
    Class<?> resourceClass = resourceInfo.getResourceClass();
    if (resourceMethod == null || resourceClass == null) {
      return false;
    }
    Class<?> superClass = resourceClass.getSuperclass();
    while (superClass != null && superClass != Object.class) {
      try {
        Method superMethod = superClass.getDeclaredMethod(resourceMethod.getName(), resourceMethod.getParameterTypes());
        if (superMethod.getAnnotation(annotation) != null) {
          return true;
        }
      } catch (NoSuchMethodException ignored) {
        // continue up the hierarchy
      }
      superClass = superClass.getSuperclass();
    }
    return false;
  }

  /**
   * ------------------API auth telemetry -----------------------------------------------
   */
  private void registerAPIAuthTelemetryFilters(
      PipelineServiceConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.getSegmentConfiguration() != null && configuration.getSegmentConfiguration().isEnabled()) {
      registerAPIAuthTelemetryFilter(environment, injector);
      registerTerraformTelemetryFilter(environment, injector);
      registerAPIAuthTelemetryResponseFilter(environment, injector);
      registerAPIErrorsTelemetrySenderFilter(environment, injector);
    }
  }

  private void registerAPIAuthTelemetryFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new APIAuthTelemetryFilter(telemetryReporter));
  }

  private void registerApiResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(ApiResponseFilter.class));
  }

  private void registerTerraformTelemetryFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new TerraformTelemetryFilter(telemetryReporter));
  }

  private void registerAPIAuthTelemetryResponseFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new APIAuthTelemetryResponseFilter(telemetryReporter));
  }

  private void registerAPIErrorsTelemetrySenderFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(
        new APIErrorsTelemetrySenderFilter(telemetryReporter, PIPELINE_SERVICE.getServiceId()));
  }

  /**
   * ------------------Health Check -----------------------------------------------
   */
  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("PMS", healthService);
    healthService.registerMonitor((HealthMonitor) injector.getInstance(MongoTemplate.class));
  }

  /**
   * ------------------Pms Sdk --------------------------------------------------
   */

  private void registerPmsSdk(PipelineServiceConfiguration config, Injector injector, Environment environment) {
    PmsSdkConfiguration sdkConfig = getPmsSdkConfiguration(config);
    try {
      PmsSdkInitHelper.initializeSDKInstance(
          injector, sdkConfig, WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.ORCHESTRATION_ENGINE), environment);
    } catch (Exception ex) {
      throw new GeneralException("Failed to start pipeline service because pms sdk registration failed", ex);
    }
  }

  private PmsSdkConfiguration getPmsSdkConfiguration(PipelineServiceConfiguration config) {
    return PmsSdkConfiguration.builder()
        .skipSdkMongoRegistration(config.isSkipSdkMongoRegistration())
        .streamPerServiceConfiguration(config.isStreamPerServiceConfiguration())
        .deploymentMode(config.isUseRemoteGrpcForPipeline() ? SdkDeployMode.REMOTE_PIPELINE_SERVICE
                                                            : SdkDeployMode.REMOTE_IN_PROCESS)
        .pmsGrpcClientConfig(config.getPmsGrpcClientConfig())
        .grpcServerConfig(config.getPmsSdkGrpcServerConfig())
        .moduleType(ModuleType.PMS)
        .pipelineServiceInfoProviderClass(PipelineServiceInternalInfoProvider.class)
        .filterCreationResponseMerger(new PipelineServiceFilterCreationResponseMerger())
        .engineSteps(PipelineServiceStepRegistrar.getEngineSteps())
        .engineFacilitators(PipelineServiceFacilitatorRegistrar.getEngineFacilitators())
        .engineAdvisers(PipelineServiceUtilAdviserRegistrar.getEngineAdvisers())
        .staticAliases(getStaticAliases())
        .jsonExpansionHandlers(getJsonExpansionHandlers())
        .engineEventHandlersMap(OrchestrationExecutionPmsEventHandlerRegistrar.getEngineEventHandlers())
        .executionSummaryModuleInfoProviderClass(PmsExecutionServiceInfoProvider.class)
        .eventsFrameworkConfiguration(config.getEventsFrameworkConfiguration())
        .executionPoolConfig(config.getPmsSdkExecutionPoolConfig())
        .orchestrationEventPoolConfig(config.getPmsSdkOrchestrationEventPoolConfig())
        .planCreatorServiceInternalConfig(config.getPmsPlanCreatorServicePoolConfig())
        .pipelineSdkRedisEventsConfig(config.getPipelineSdkRedisEventsConfig())
        .triggerActivationPoolConfig(config.getPmsSdkTriggerActivationPoolConfig())
        .orchestrationHandlerPoolConfig(config.getPmsSdkOrchestrationHandlerPoolConfig())
        .build();
  }

  @VisibleForTesting
  public Map<String, String> getStaticAliases() {
    Map<String, String> aliases = new HashMap<>();
    aliases.put(OrchestrationConstants.STAGE_SUCCESS,
        "<+stage.currentStatus> =~ [\"SUCCEEDED\", \"IGNORE_FAILED\", \"PASSED_WITH_WARNING\"]");
    aliases.put(OrchestrationConstants.STAGE_FAILURE,
        "<+stage.currentStatus> =~ [\"FAILED\", \"ERRORED\", \"EXPIRED\", \"APPROVAL_REJECTED\"]");
    aliases.put(OrchestrationConstants.PIPELINE_FAILURE,
        "<+pipeline.currentStatus> =~ [\"FAILED\", \"ERRORED\", \"EXPIRED\", \"APPROVAL_REJECTED\"]");
    aliases.put(OrchestrationConstants.PIPELINE_SUCCESS,
        "<+pipeline.currentStatus> =~ [\"SUCCEEDED\", \"IGNORE_FAILED\", \"PASSED_WITH_WARNING\"]");
    // DAG dependency status aliases. Resolved by NodeExecutionMap.fetchDependencyStatus against the
    // current scope's NodeExecution, so the same resolver code will serve step-level DAG once it lands
    // (at which point a matching OnStepDependantsSuccess alias pointing to <+step.allDependantsSucceeded>
    // is all that's needed).
    aliases.put(OrchestrationConstants.ALL_DEPENDANTS_SUCCESS, "<+stage.allDependantsSucceeded>");
    aliases.put(OrchestrationConstants.ANY_DEPENDANT_FAILURE, "<+stage.anyDependantFailed>");
    aliases.put(OrchestrationConstants.ROLLBACK_MODE_EXECUTION,
        "<+<+ambiance.metadata.executionMode>.toString()> =~ [\"POST_EXECUTION_ROLLBACK\", \"PIPELINE_ROLLBACK\"]");
    aliases.put(OrchestrationConstants.ALWAYS, "true");
    aliases.put(StrategyConstants.MATRIX, "strategy.matrix");
    aliases.put(StrategyConstants.REPEAT, "strategy.repeat");
    return aliases;
  }

  private List<JsonExpansionHandlerInfo> getJsonExpansionHandlers() {
    List<JsonExpansionHandlerInfo> jsonExpansionHandlers = new ArrayList<>();
    JsonExpansionInfo connectorRefExpansionInfo =
        JsonExpansionInfo.newBuilder().setKey(YAMLFieldNameConstants.CONNECTOR_REF).setExpansionType(KEY).build();
    JsonExpansionHandlerInfo connectorRefExpansionHandlerInfo =
        JsonExpansionHandlerInfo.builder()
            .jsonExpansionInfo(connectorRefExpansionInfo)
            .expansionHandler(DefaultConnectorRefExpansionHandler.class)
            .build();
    jsonExpansionHandlers.add(connectorRefExpansionHandlerInfo);
    return jsonExpansionHandlers;
  }

  private void registerEventListeners(Injector injector) {
    QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    queueListenerController.register(injector.getInstance(DelayEventListener.class), 1);
  }

  private void registerWaitEnginePublishers(Injector injector) {
    final NotifyQueuePublisherRegister notifyQueuePublisherRegister =
        injector.getInstance(NotifyQueuePublisherRegister.class);
    notifyQueuePublisherRegister.register(PMS_ORCHESTRATION, injector.getInstance(PmsNotifyEventPublisher.class));
  }

  private void registerPmsSdkEvents(
      PipelineServiceConfiguration appConfig, Injector injector, Environment environment) {
    var pipelineServiceConsumersConfig = appConfig.getPipelineServiceConsumersConfig();
    var eventsFrameworkConfiguration = appConfig.getEventsFrameworkConfiguration();

    log.info("Initializing pms sdk redis abstract consumers...");
    PipelineEventConsumerController pipelineEventConsumerController =
        injector.getInstance(PipelineEventConsumerController.class);

    if (WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.GRAPH)) {
      pipelineEventConsumerController.register(injector.getInstance(GraphUpdateRedisConsumer.class),
          pipelineServiceConsumersConfig.getGraphUpdate().getThreads());
      if (eventsFrameworkConfiguration.isShouldUseKafka()) {
        ConsumerMaintenanceListener listener = injector.getInstance(ConsumerMaintenanceListener.class);
        injector.getInstance(MaintenanceController.class).register(listener);
        // Sync state after registration to avoid race condition
        listener.syncMaintenanceState();
        environment.lifecycle().manage(injector.getInstance(GraphUpdateKafkaConsumer.class));
      }
    }

    if (!WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.ORCHESTRATION_ENGINE)) {
      return;
    }
    pipelineEventConsumerController.register(injector.getInstance(WebhookEventStreamConsumer.class),
        pipelineServiceConsumersConfig.getWebhookEvent().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(EventListenerStepEventStreamConsumer.class),
        pipelineServiceConsumersConfig.getWebhookEvent().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(SystemEventTriggerStreamConsumer.class),
        pipelineServiceConsumersConfig.getSystemEventTriggerEvent().getThreads());

    if (!eventsFrameworkConfiguration.isShouldUseKafka()) {
      log.info("Initializing pms sdk redis orchestration consumers...");
    } else {
      log.info("Kafka is enabled; registering Redis orchestration consumers for per-account Redis fallback routing");
    }
    registerOrchestrationRedisConsumers(injector, pipelineServiceConsumersConfig, pipelineEventConsumerController);
    registerPipelineExecutionSummaryRedisConsumers(
        injector, pipelineServiceConsumersConfig, pipelineEventConsumerController);

    pipelineEventConsumerController.register(injector.getInstance(PollingEventStreamConsumer.class),
        pipelineServiceConsumersConfig.getPollingEvent().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(TriggerExecutionEventStreamConsumer.class),
        pipelineServiceConsumersConfig.getTriggerExecutionEvent().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(AsyncFilterCreationStreamConsumer.class),
        pipelineServiceConsumersConfig.getAsyncFilterCreationEvent().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(PipelineBulkReconciliationStreamConsumer.class),
        pipelineServiceConsumersConfig.getAsyncFilterCreationEvent().getThreads());
  }

  private void registerKafkaUnsupportedOrchestrationRedisConsumers(Injector injector,
      PipelineServiceConsumersConfig pipelineServiceConsumersConfig,
      PipelineEventConsumerController pipelineEventConsumerController) {
    log.info("Registering Redis consumers for orchestration categories not yet supported on Kafka");
    pipelineEventConsumerController.register(injector.getInstance(OrchestrationEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getOrchestrationEvent().getThreads());
  }

  private void registerOrchestrationRedisConsumers(Injector injector,
      PipelineServiceConsumersConfig pipelineServiceConsumersConfig,
      PipelineEventConsumerController pipelineEventConsumerController) {
    registerKafkaUnsupportedOrchestrationRedisConsumers(
        injector, pipelineServiceConsumersConfig, pipelineEventConsumerController);

    pipelineEventConsumerController.register(injector.getInstance(InterruptEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getInterrupt().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(FacilitatorEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getFacilitatorEvent().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(NodeStartEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getNodeStart().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(ProgressEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getProgress().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getAdvise().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getResume().getThreads());

    pipelineEventConsumerController.register(injector.getInstance(InterruptEventRedisConsumerV2.class),
        pipelineServiceConsumersConfig.getInterrupt().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(FacilitatorEventRedisConsumerV2.class),
        pipelineServiceConsumersConfig.getFacilitatorEvent().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(NodeStartEventRedisConsumerV2.class),
        pipelineServiceConsumersConfig.getNodeStart().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(NodeProgressEventRedisConsumerV2.class),
        pipelineServiceConsumersConfig.getProgress().getThreads());
    pipelineEventConsumerController.register(
        injector.getInstance(NodeAdviseRedisConsumerV2.class), pipelineServiceConsumersConfig.getAdvise().getThreads());
    pipelineEventConsumerController.register(
        injector.getInstance(NodeResumeEventConsumerV2.class), pipelineServiceConsumersConfig.getResume().getThreads());

    pipelineEventConsumerController.register(injector.getInstance(SdkResponseEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getSdkResponse().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(SdkResponseSpawnEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getSdkResponseSpawnEvent().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(SdkStepResponseEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getSdkStepResponseEvent().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(PmsNotifyEventConsumerRedis.class),
        pipelineServiceConsumersConfig.getPmsNotify().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(InitiateNodeEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getInitiateNode().getThreads());
    pipelineEventConsumerController.register(injector.getInstance(InitiateNodeBatchEventRedisConsumer.class),
        pipelineServiceConsumersConfig.getInitiateNode().getThreads());
  }

  private void registerPipelineExecutionSummaryRedisConsumers(Injector injector,
      PipelineServiceConsumersConfig pipelineServiceConsumersConfig,
      PipelineEventConsumerController pipelineEventConsumerController) {
    pipelineEventConsumerController.register(injector.getInstance(PipelineExecutionSummaryRedisEventConsumer.class),
        pipelineServiceConsumersConfig.getPipelineExecutionEvent().getThreads());
    pipelineEventConsumerController.register(
        injector.getInstance(PipelineExecutionSummaryRedisEventConsumerSnapshot.class),
        pipelineServiceConsumersConfig.getPipelineExecutionEventSnapshot().getThreads());
  }

  /**
   * -----------------------------Git sync --------------------------------------
   */
  private void registerGitSyncSdk(PipelineServiceConfiguration config, Injector injector, Environment environment) {
    GitSyncSdkConfiguration sdkConfig = getGitSyncConfiguration(config);
    try {
      GitSyncSdkInitHelper.initGitSyncSdk(injector, environment, sdkConfig);
    } catch (Exception ex) {
      throw new GeneralException("Failed to start pipeline service because git sync registration failed", ex);
    }
  }

  private GitSyncSdkConfiguration getGitSyncConfiguration(PipelineServiceConfiguration config) {
    final Supplier<List<EntityType>> sortOrder = () -> PMSGitEntityOrderComparator.sortOrder;
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    configureObjectMapper(objectMapper);
    Set<GitSyncEntitiesConfiguration> gitSyncEntitiesConfigurations = new HashSet<>();
    gitSyncEntitiesConfigurations.add(GitSyncEntitiesConfiguration.builder()
                                          .yamlClass(PipelineConfig.class)
                                          .entityClass(PipelineEntity.class)
                                          .entityType(EntityType.PIPELINES)
                                          .entityHelperClass(PipelineEntityGitSyncHelper.class)
                                          .build());
    gitSyncEntitiesConfigurations.add(GitSyncEntitiesConfiguration.builder()
                                          .yamlClass(InputSetYamlDTO.class)
                                          .entityClass(InputSetEntity.class)
                                          .entityType(EntityType.INPUT_SETS)
                                          .entityHelperClass(InputSetEntityGitSyncHelper.class)
                                          .build());
    final GitSdkConfiguration gitSdkConfiguration = config.getGitSdkConfiguration();
    return GitSyncSdkConfiguration.builder()
        .gitSyncSortOrder(sortOrder)
        .grpcClientConfig(gitSdkConfiguration.getGitManagerGrpcClientConfig())
        .grpcServerConfig(gitSdkConfiguration.getGitSdkGrpcServerConfig())
        .deployMode(GitSyncSdkConfiguration.DeployMode.REMOTE)
        .microservice(Microservice.PMS)
        .scmConnectionConfig(gitSdkConfiguration.getScmConnectionConfig())
        .eventsFrameworkConfiguration(config.getEventsFrameworkConfiguration())
        .serviceHeader(PIPELINE_SERVICE)
        .gitSyncEntitiesConfiguration(gitSyncEntitiesConfigurations)
        .gitSyncEntitySortComparator(PMSGitEntityOrderComparator.class)
        .objectMapper(objectMapper)
        .throw424ForGITServerErrors(gitSdkConfiguration.getThrow424ForGITServerErrors())
        .build();
  }

  private void registerScheduledJobs(Injector injector, PipelineServiceConfiguration appConfig) {
    if (!WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.ORCHESTRATION_ENGINE)) {
      return;
    }
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("syncTaskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateSyncServiceImpl.class), 0L,
            appConfig.getDelegatePollingConfig().getSyncDelay(), TimeUnit.MILLISECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("asyncTaskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateAsyncServiceImpl.class), 0L,
            appConfig.getDelegatePollingConfig().getAsyncDelay(), TimeUnit.MILLISECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("progressTaskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateProgressServiceImpl.class), 0L,
            appConfig.getDelegatePollingConfig().getProgressDelay(), TimeUnit.MILLISECONDS);
    if (appConfig.getIteratorsConfig().getProgressUpdateIteratorConfig().isEnabled()) {
      injector.getInstance(ProgressUpdateIterator.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name(WaitNotifyCollectionNameResolver.qualifyIteratorLockName("PIPELINE-ProgressUpdateIterator"))
                  .poolSize(appConfig.getIteratorsConfig().getProgressUpdateIteratorConfig().getThreadPoolSize())
                  .batchSize(appConfig.getIteratorsConfig().getProgressUpdateIteratorConfig().getRedisBatchSize())
                  .lockTimeout(appConfig.getIteratorsConfig().getProgressUpdateIteratorConfig().getRedisLockTimeout())
                  .interval(Duration.ofSeconds(appConfig.getIteratorsConfig()
                                                   .getProgressUpdateIteratorConfig()
                                                   .getThreadPoolIntervalInSeconds()))
                  .build(),
              Duration.ofSeconds(
                  appConfig.getIteratorsConfig().getProgressUpdateIteratorConfig().getTargetIntervalInSeconds()));
    } else {
      injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("progressUpdateServiceExecutor")))
          .scheduleWithFixedDelay(injector.getInstance(ProgressUpdateService.class), 0L,
              appConfig.getDelegatePollingConfig().getProgressDelay(), TimeUnit.MILLISECONDS);
    }
    NotifyResponseCleanerSpringPersistence cleaner =
        injector.getInstance(NotifyResponseCleanerFactory.class).create("PIPELINE");
    injector.getInstance(NotifierScheduledExecutorService.class)
        .scheduleWithFixedDelay(cleaner, random.nextInt(300), 300L, TimeUnit.SECONDS);
  }

  private void registerManagedBeans(
      Environment environment, Injector injector, PipelineServiceConfiguration appConfig) {
    if (WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.GRAPH)) {
      environment.lifecycle().manage(injector.getInstance(PMSEventConsumerService.class));
    }
    environment.lifecycle().manage(injector.getInstance(QueueListenerController.class));
    environment.lifecycle().manage(injector.getInstance(ApprovalInstanceExpirationJob.class));
    environment.lifecycle().manage(injector.getInstance(OutboxEventPollService.class));
    environment.lifecycle().manage(injector.getInstance(ExecutionOutboxEventPollService.class));
    environment.lifecycle().manage(injector.getInstance(KafkaOutboxEventPollService.class));
    environment.lifecycle().manage(injector.getInstance(AddUniqueIdParentIdToEntitiesJob.class));
    environment.lifecycle().manage(injector.getInstance(AddUniqueIdParentIdToCdcEntitiesJob.class));
    environment.lifecycle().manage(injector.getInstance(PipelineEventConsumerController.class));
    environment.lifecycle().manage(injector.getInstance(RolesMigrationService.class));
    registerFlowGovernorCacheLifecycle(environment, injector);
    if (appConfig.getDataRetentionConfig().isCleanUpEnabled()) {
      environment.lifecycle().manage(injector.getInstance(ExecutionRetentionCleanUpOnTTLExpirationService.class));
    }
    if (appConfig.getOrchestrationGraphCacheCleanupConfig() != null
        && appConfig.getOrchestrationGraphCacheCleanupConfig().isEnabled()) {
      environment.lifecycle().manage(injector.getInstance(OrchestrationGraphCacheCleanupService.class));
    }
    if (appConfig.isUseQueueServiceForWebhookTriggers()) {
      environment.lifecycle().manage(injector.getInstance(WebhookEventQueueProcessor.class));
      environment.lifecycle().manage(injector.getInstance(EventListenerStepEventQueueProcessor.class));
    }
    if (appConfig.isUseQueueServiceForPlanCreation()) {
      environment.lifecycle().manage(injector.getInstance(PlanCreationQueuePoller.class));
    }
    if (Boolean.TRUE.equals(appConfig.getUseDbQueueForPlanCreation())) {
      // Postgres FIFO consumer. The producer publishes new queued executions only here once
      // useDbQueueForPlanCreation=true; run alongside the hsqs poller above during the overlap.
      environment.lifecycle().manage(injector.getInstance(PlanCreationDbQueuePoller.class));
    }
    if (appConfig.isUseQueueServiceForCustomWebhookTriggers()) {
      environment.lifecycle().manage(injector.getInstance(CustomTriggerWebhookEventQueueProcessor.class));
    }

    if (appConfig.getGcsMigrationConfig().isEnable()) {
      environment.lifecycle().manage(
          injector.getInstance(ParentUniqueIdMigrationForPipelineExecutionSummaryForGCS.class));
    }
    environment.lifecycle().manage(injector.getInstance(PipelineRoleMigrationService.class));
    ProjectEntityMigrationMetricsConfig metricsConfig =
        appConfig.getProjectMovementProjectEntityMigrationMetricsConfig();
    if (metricsConfig != null && metricsConfig.isEnabled()) {
      log.info("Registering project entity migration metrics collector with initial delay: {} minutes, frequency: {} "
              + "minutes",
          metricsConfig.getInitialDelayMinutes(), metricsConfig.getFrequencyMinutes());
      ProjectEntityMigrationMetricsJob metricsJob = injector.getInstance(ProjectEntityMigrationMetricsJob.class);
      metricsJob.configure(metricsConfig);
      environment.lifecycle().manage(metricsJob);
      log.info("Project Entity migration metrics collector registered successfully in pipeline-service");
    } else {
      log.info("Project Entity migration metrics collector is disabled or not configured in pipeline-service");
    }

    if (appConfig.getExecutionStatusReconciliationConfig().isEnabled()) {
      environment.lifecycle().manage(injector.getInstance(PipelineExecutionStatusReconciliationService.class));
    }
    if (appConfig.getResourceRestraintReconciliationConfig().isEnabled()) {
      environment.lifecycle().manage(injector.getInstance(ResourceRestraintReconciliationService.class));
    }
    environment.lifecycle().manage(injector.getInstance(PMSDeleteEntitiesMigrationService.class));
    environment.lifecycle().manage(injector.getInstance(StuckExecutionDetector.class));
    if (appConfig.getPipelineExecutionCounterRebuildJobEnabled() == null
        || appConfig.getPipelineExecutionCounterRebuildJobEnabled()) {
      environment.lifecycle().manage(injector.getInstance(StepConcurrencyCounterRebuildService.class));
    }
    if (appConfig.getPlanConcurrencyRebuildJobEnabled() == null || appConfig.getPlanConcurrencyRebuildJobEnabled()) {
      environment.lifecycle().manage(injector.getInstance(PlanConcurrencyCounterRebuildService.class));
    }
    // Do not remove as it's used for MaintenanceController for shutdown mode
    environment.lifecycle().manage(injector.getInstance(MaintenanceController.class));
    environment.lifecycle().manage(injector.getInstance(TriggersMigrationService.class));
    environment.lifecycle().manage(injector.getInstance(AddUniqueIdParentIdToFilterEntitiesJob.class));
    environment.lifecycle().manage(injector.getInstance(BackfillGitConnectorForInputSetsJob.class));

    // Graph Generation Kafka Streams Application (CDC-based graph projection)
    if (appConfig.getGraphGenerationStreamsConfig() != null && appConfig.getGraphGenerationStreamsConfig().isEnabled()
        && WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.GRAPH)) {
      environment.lifecycle().manage(injector.getInstance(GraphCDCConsumer.class));
    }
  }

  /**
   * Registers a {@link Managed} adapter that shuts the flow-governor cache's daemon refresh
   * thread down cleanly on graceful stop. The cache is bound unconditionally in
   * {@code PipelineServiceModule}; when {@code flowGovernorConfig.enabled=false} the cache is
   * never read (see {@code ThrottledKafkaConsumer#runInternal} short-circuit), so shutdown is a
   * cheap no-op on that path.
   */
  private void registerFlowGovernorCacheLifecycle(Environment environment, Injector injector) {
    io.harness.engine.execution.consumers.flowgovernor.FlowGovernorStateCache cache =
        injector.getInstance(io.harness.engine.execution.consumers.flowgovernor.FlowGovernorStateCache.class);
    environment.lifecycle().manage(new io.dropwizard.lifecycle.Managed() {
      @Override
      public void start() {}
      @Override
      public void stop() {
        cache.shutdown();
      }
    });
  }

  private void registerCorsFilter(PipelineServiceConfiguration appConfig, Environment environment) {
    FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    String allowedOrigins = String.join(",", appConfig.getAllowedOrigins());
    cors.setInitParameters(of("allowedOrigins", allowedOrigins, "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Authorization,X-api-key", "allowedMethods",
        "OPTIONS,GET,PUT,POST,DELETE,HEAD", "preflightMaxAge", "86400"));
    cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : HARNESS_RESOURCE_CLASSES) {
      environment.jersey().register(injector.getInstance(resource));
    }
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
  }

  private void registerJerseyProviders(Environment environment, Injector injector) {
    environment.jersey().register(JerseyViolationExceptionMapperV2.class);
    environment.jersey().register(GenericExceptionMapperV2.class);
    environment.jersey().register(new JsonProcessingExceptionMapper(true));
    environment.jersey().register(EarlyEofExceptionMapper.class);
    environment.jersey().register(NGAccessDeniedExceptionMapper.class);
    environment.jersey().register(WingsExceptionMapperV2.class);
    environment.jersey().register(NotFoundExceptionMapper.class);
    environment.jersey().register(NotAllowedExceptionMapper.class);
    environment.jersey().register(QueryParamExceptionMapper.class);
    environment.jersey().register(MultiPartFeature.class);
    environment.jersey().register(NotSupportedExceptionMapper.class);
    //    environment.jersey().register(injector.getInstance(CharsetResponseFilter.class));
    //    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
    //    environment.jersey().register(injector.getInstance(EtagFilter.class));
  }

  private void registerNotificationTemplates(Injector injector) {
    ExecutorService executorService =
        injector.getInstance(Key.get(ExecutorService.class, Names.named("templateRegistrationExecutorService")));
    executorService.submit(injector.getInstance(NotificationTemplateRegistrar.class));
  }

  private void registerKafkaConsumer(
      Injector injector, EventsFrameworkConfiguration eventsFrameworkConfiguration, Environment environment) {
    if (!WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.ORCHESTRATION_ENGINE)) {
      return;
    }
    log.info("Initializing Kafka for Pipeline Service");
    ConsumerMaintenanceListener listener = injector.getInstance(ConsumerMaintenanceListener.class);
    injector.getInstance(MaintenanceController.class).register(listener);
    // Sync state after registration to avoid race condition
    listener.syncMaintenanceState();
    if (eventsFrameworkConfiguration.isShouldStartUsingKafkaConsumers()) {
      environment.lifecycle().manage(injector.getInstance(StageStatusEventKafkaConsumer.class));
    }

    if (eventsFrameworkConfiguration.isShouldUseKafka()) {
      environment.lifecycle().manage(injector.getInstance(InitiateNodeEventKafkaConsumer.class));
      environment.lifecycle().manage(injector.getInstance(SdkResponseEventKafkaConsumer.class));
      environment.lifecycle().manage(injector.getInstance(SdkResponseSpawnEventKafkaConsumer.class));
      environment.lifecycle().manage(injector.getInstance(SdkStepResponseEventKafkaConsumer.class));
      environment.lifecycle().manage(injector.getInstance(InitiateNodeBatchEventKafkaConsumer.class));
    }
  }

  /**
   * Registers CDC Kafka consumers as Dropwizard {@link Managed} beans when
   * {@code cdcKafkaConfig.enabled} is true. Runs only on orchestration-engine workloads,
   * matching the scope of the corresponding Redis/Debezium consumers (see
   * {@link #registerPmsSdkEvents}).
   *
   * <p>Each CDC consumer extends {@code HKafkaConsumer}, which owns its own poll-loop thread
   * and maintenance gating; Dropwizard's lifecycle manager invokes {@code start()} on boot
   * and {@code stop()} on graceful shutdown. {@link ConsumerMaintenanceListener} is already
   * registered with {@link io.harness.maintenance.MaintenanceController} by
   * {@link #registerKafkaConsumer} for this workload, so it is NOT re-registered here.
   *
   * <p>Whether a consumer actively processes (vs. drains) is further gated at runtime by a
   * global Harness feature flag inside the message handler.
   */
  private void registerCdcKafkaConsumers(
      Injector injector, PipelineServiceConfiguration appConfig, Environment environment) {
    if (!WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.ORCHESTRATION_ENGINE)) {
      return;
    }
    CdcKafkaConfig cdcKafkaConfig = appConfig.getCdcKafkaConfig();
    if (cdcKafkaConfig == null || !cdcKafkaConfig.isEnabled()) {
      log.info("CDC Kafka infrastructure disabled (cdcKafkaConfig.enabled=false); skipping Kafka CDC consumer startup");
      return;
    }

    try {
      environment.lifecycle().manage(injector.getInstance(PipelineExecutionSummaryKafkaConsumer.class));
      String planExecutionsSummaryTopic = cdcKafkaConfig.getConsumer(CdcKafkaConfig.PLAN_EXECUTIONS_SUMMARY_CONSUMER)
                                              .map(CdcKafkaConsumerConfig::getTopic)
                                              .orElse("unknown");
      log.info("Registered CDC Kafka consumer: PipelineExecutionSummaryKafkaConsumer "
              + "(topic={}, group={}, processing gated by runtime FF)",
          planExecutionsSummaryTopic, CdcKafkaConstants.PLAN_EXECUTIONS_SUMMARY_CONSUMER_GROUP);
    } catch (Exception ex) {
      log.error("Failed to register planExecutionsSummary Kafka CDC consumer: {}", ex.getMessage(), ex);
    }
  }

  private void registerRequestContextFilter(Environment environment) {
    environment.jersey().register(new RequestContextFilter());
  }

  private void registerMigrations(Injector injector) {
    if (!WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.GRAPH)) {
      return;
    }
    NGMigrationConfiguration config = getMigrationSdkConfiguration();
    NGMigrationSdkInitHelper.initialize(injector, config);
  }

  private NGMigrationConfiguration getMigrationSdkConfiguration() {
    return NGMigrationConfiguration.builder()
        .microservice(Microservice.PMS)
        .migrationProviderList(new ArrayList<Class<? extends MigrationProvider>>() {
          { add(DatabaseSetupMigrationProvider.class); }
          { add(PipelineCoreMigrationProvider.class); } // Add all migration provider classes here
        })
        .build();
  }

  private void initializeEnforcementSdk(Injector injector) {
    RestrictionUsageRegisterConfiguration restrictionUsageRegisterConfiguration =
        RestrictionUsageRegisterConfiguration.builder()
            .restrictionNameClassMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends RestrictionUsageInterface>>builder()
                    .put(FeatureRestrictionName.STRATEGY_MAX_CONCURRENT,
                        StrategyMaxConcurrencyRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_PIPELINE_TIMEOUT_SECONDS, MaxStaticValueRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_STAGE_TIMEOUT_SECONDS, MaxStaticValueRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_STEP_TIMEOUT_SECONDS, MaxStaticValueRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS,
                        MaxStaticValueRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MAX_PARALLEL_STEP_IN_A_PIPELINE,
                        MaxStaticValueRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.PIPELINE_EXECUTION_DATA_RETENTION_DAYS,
                        MaxStaticValueRestrictionUsageImpl.class)
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
}

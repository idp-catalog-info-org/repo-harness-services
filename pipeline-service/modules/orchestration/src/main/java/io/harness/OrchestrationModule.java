/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.constants.OrchestrationPublisherName.PERSISTENCE_LAYER;
import static io.harness.constants.OrchestrationPublisherName.PUBLISHER_NAME;

import static java.util.Arrays.asList;

import io.harness.account.AccountClientModule;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.account.settings.service.impl.NoopPipelineSettingServiceImpl;
import io.harness.account.settings.service.impl.PipelineSettingsServiceImpl;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cache.HarnessCacheManager;
import io.harness.config.OrchestrationModuleConfig;
import io.harness.config.OrchestrationRestrictionConfiguration;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.dataretention.PipelineRetentionServiceImpl;
import io.harness.delay.AbstractOrchestrationDelayModule;
import io.harness.engine.GovernanceService;
import io.harness.engine.GovernanceServiceImpl;
import io.harness.engine.NoopTaskExecutor;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.OrchestrationService;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.execution.ExecutionInputServiceImpl;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataServiceImpl;
import io.harness.engine.executions.concurrency.counter.PlanConcurrencyCounterService;
import io.harness.engine.executions.concurrency.counter.PlanConcurrencyCounterServiceImpl;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterService;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterServiceImpl;
import io.harness.engine.executions.concurrency.planqueue.PlanCreationDbQueueService;
import io.harness.engine.executions.concurrency.planqueue.PlanCreationDbQueueServiceImpl;
import io.harness.engine.executions.concurrency.queue.StepConcurrencyQueueService;
import io.harness.engine.executions.concurrency.queue.StepConcurrencyQueueServiceImpl;
import io.harness.engine.executions.concurrency.rebuild.StepConcurrencyCounterRebuildService;
import io.harness.engine.executions.gitmetadata.service.ExecutionGitMetadataReconciliationEntityService;
import io.harness.engine.executions.gitmetadata.service.PipelineExecutionGitMetadataService;
import io.harness.engine.executions.gitmetadata.service.impl.ExecutionGitMetadataReconciliationEntityServiceImpl;
import io.harness.engine.executions.gitmetadata.service.impl.PipelineExecutionGitMetadataServiceImpl;
import io.harness.engine.executions.node.detection.StuckExecutionDetectionService;
import io.harness.engine.executions.node.detection.StuckExecutionDetectionServiceImpl;
import io.harness.engine.executions.node.detection.StuckExecutionDetector;
import io.harness.engine.executions.node.service.NodeExecutionBackfillService;
import io.harness.engine.executions.node.service.NodeExecutionMonitorService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.node.service.impl.NodeExecutionBackfillServiceImpl;
import io.harness.engine.executions.node.service.impl.NodeExecutionMonitorServiceImpl;
import io.harness.engine.executions.node.service.impl.NodeExecutionServiceImpl;
import io.harness.engine.executions.plan.service.DagExecutionService;
import io.harness.engine.executions.plan.service.PlanCreationQueueRequestService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionMonitorService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.executions.plan.service.impl.DagExecutionServiceImpl;
import io.harness.engine.executions.plan.service.impl.PlanCreationQueueRequestServiceImpl;
import io.harness.engine.executions.plan.service.impl.PlanExecutionMetadataServiceImpl;
import io.harness.engine.executions.plan.service.impl.PlanExecutionMonitorServiceImpl;
import io.harness.engine.executions.plan.service.impl.PlanExecutionServiceImpl;
import io.harness.engine.executions.plan.service.impl.PlanServiceImpl;
import io.harness.engine.executions.stage.StageExecutionEntityService;
import io.harness.engine.executions.stage.StageExecutionEntityServiceImpl;
import io.harness.engine.executions.step.StepExecutionEntityService;
import io.harness.engine.executions.step.StepExecutionEntityServiceImpl;
import io.harness.engine.expressions.EngineExpressionServiceImpl;
import io.harness.engine.expressions.provider.ExpressionEvaluatorProvider;
import io.harness.engine.expressions.usages.service.ExecutionExpressionUsageService;
import io.harness.engine.expressions.usages.service.ExpressionUsageService;
import io.harness.engine.expressions.usages.service.impl.ExecutionExpressionUsageServiceImpl;
import io.harness.engine.expressions.usages.service.impl.ExpressionUsageServiceImpl;
import io.harness.engine.facilitation.facilitator.publisher.FacilitateEventPublisher;
import io.harness.engine.facilitation.facilitator.publisher.FacilitateEventPublisherImpl;
import io.harness.engine.impl.OrchestrationEngineImpl;
import io.harness.engine.impl.OrchestrationServiceImpl;
import io.harness.engine.interrupts.handlers.publisher.InterruptEventPublisher;
import io.harness.engine.interrupts.handlers.publisher.InterruptEventPublisherImpl;
import io.harness.engine.interrupts.helpers.AbortHelperImpl;
import io.harness.engine.interrupts.helpers.ExpiryHelperImpl;
import io.harness.engine.interrupts.helpers.UserMarkedFailAllHelperImpl;
import io.harness.engine.interrupts.helpers.intfc.AbortHelper;
import io.harness.engine.interrupts.helpers.intfc.ExpiryHelper;
import io.harness.engine.interrupts.helpers.intfc.UserMarkedFailAllHelper;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.engine.interrupts.service.impl.InterruptServiceImpl;
import io.harness.engine.pms.advise.publisher.NodeAdviseEventPublisher;
import io.harness.engine.pms.advise.publisher.NodeAdviseEventPublisherImpl;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.pms.data.expression.impl.PmsEngineExpressionServiceImpl;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.data.outcome.impl.PmsOutcomeServiceImpl;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.engine.pms.data.sweepingoutput.impl.PmsSweepingOutputServiceImpl;
import io.harness.engine.pms.execution.strategy.helper.EndNodeExecutionHelperImpl;
import io.harness.engine.pms.execution.strategy.helper.intfc.EndNodeExecutionHelper;
import io.harness.engine.pms.resume.publisher.NodeResumeEventPublisher;
import io.harness.engine.pms.resume.publisher.impl.NodeResumeEventPublisherImpl;
import io.harness.engine.pms.tasks.NgDelegate2TaskExecutor;
import io.harness.engine.pms.tasks.TaskExecutor;
import io.harness.engine.progress.publisher.ProgressEventPublisher;
import io.harness.engine.progress.publisher.ProgressEventPublisherImpl;
import io.harness.event.OrchestrationLogConfiguration;
import io.harness.exception.exceptionmanager.ExceptionModule;
import io.harness.execution.dynamic.DynamicExecutionService;
import io.harness.execution.dynamic.DynamicExecutionServiceImpl;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.execution.expansion.PlanExpansionServiceImpl;
import io.harness.expression.EngineExpressionService;
import io.harness.govern.ServersModule;
import io.harness.graph.stepDetail.NodeExecutionInfoServiceImpl;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.pms.NoopFeatureFlagServiceImpl;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.queue.TimerScheduledExecutorService;
import io.harness.serializer.KryoSerializer;
import io.harness.testing.TestExecution;
import io.harness.threading.ThreadPool;
import io.harness.threading.ThreadPoolConfig;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.AsyncWaitEngineImpl;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.waiter.WaiterConfiguration;
import io.harness.waiter.WaiterConfiguration.PersistenceLayer;
import io.harness.waiter.module.AbstractWaiterModule;

import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import java.io.Closeable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationModule extends AbstractModule implements ServersModule {
  private static OrchestrationModule instance;
  private final OrchestrationModuleConfig config;
  private final MetricRegistry threadPoolMetricRegistry;

  public static OrchestrationModule getInstance(OrchestrationModuleConfig orchestrationModuleConfig) {
    if (instance == null) {
      instance = new OrchestrationModule(orchestrationModuleConfig);
    }
    return instance;
  }

  public static OrchestrationModule getInstance(
      OrchestrationModuleConfig orchestrationModuleConfig, MetricRegistry threadPoolMetricRegistry) {
    if (instance == null) {
      instance = new OrchestrationModule(orchestrationModuleConfig, threadPoolMetricRegistry);
    }
    return instance;
  }

  private OrchestrationModule(OrchestrationModuleConfig config) {
    this.config = config;
    this.threadPoolMetricRegistry = new MetricRegistry();
  }

  private OrchestrationModule(OrchestrationModuleConfig config, MetricRegistry threadPoolMetricRegistry) {
    this.config = config;
    this.threadPoolMetricRegistry = threadPoolMetricRegistry;
  }

  @Override
  protected void configure() {
    install(ExceptionModule.getInstance());
    install(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder().persistenceLayer(PersistenceLayer.SPRING).build();
      }
    });
    install(new AbstractOrchestrationDelayModule() {
      @Override
      public boolean forNG() {
        return true;
      }
    });
    install(OrchestrationBeansModule.getInstance());
    if (!config.isUseFeatureFlagService()) {
      bind(PmsFeatureFlagService.class).to(NoopFeatureFlagServiceImpl.class);
      bind(PipelineSettingsService.class).to(NoopPipelineSettingServiceImpl.class).in(Singleton.class);

    } else {
      install(new AccountClientModule(
          config.getAccountServiceHttpClientConfig(), config.getAccountServiceSecret(), config.getAccountClientId()));
      // ng-license dependencies
      install(NgLicenseHttpClientModule.getInstance(
          config.getLicenseClientConfig(), config.getLicenseClientServiceSecret(), config.getAccountClientId()));
      bind(PmsFeatureFlagService.class).to(PmsFeatureFlagHelper.class);
      bind(PipelineSettingsService.class).to(PipelineSettingsServiceImpl.class).in(Singleton.class);
    }
    bind(PlanExpansionService.class).to(PlanExpansionServiceImpl.class).in(Singleton.class);

    bind(NodeExecutionService.class).to(NodeExecutionServiceImpl.class).in(Singleton.class);
    bind(NodeExecutionBackfillService.class).to(NodeExecutionBackfillServiceImpl.class).in(Singleton.class);
    bind(StuckExecutionDetectionService.class).to(StuckExecutionDetectionServiceImpl.class).in(Singleton.class);
    if (config.isStuckExecutionDetectorEnabled()) {
      bind(StuckExecutionDetector.class).asEagerSingleton(); // Start scheduled detector on app startup
    }
    bind(PlanExecutionService.class).to(PlanExecutionServiceImpl.class).in(Singleton.class);
    bind(PlanCreationQueueRequestService.class).to(PlanCreationQueueRequestServiceImpl.class).in(Singleton.class);
    bind(DagExecutionService.class).to(DagExecutionServiceImpl.class).in(Singleton.class);

    bind(BlockExecutionMetadataService.class).to(BlockExecutionMetadataServiceImpl.class).in(Singleton.class);
    bind(StepConcurrencyCounterService.class).to(StepConcurrencyCounterServiceImpl.class).in(Singleton.class);
    bind(PlanConcurrencyCounterService.class).to(PlanConcurrencyCounterServiceImpl.class).in(Singleton.class);
    bind(StepConcurrencyQueueService.class).to(StepConcurrencyQueueServiceImpl.class).in(Singleton.class);
    bind(PlanCreationDbQueueService.class).to(PlanCreationDbQueueServiceImpl.class).in(Singleton.class);
    if (config.isPipelineExecutionCounterRebuildJobEnabled()) {
      bind(StepConcurrencyCounterRebuildService.class).asEagerSingleton();
    }
    bind(PlanExecutionMonitorService.class).to(PlanExecutionMonitorServiceImpl.class).in(Singleton.class);
    bind(NodeExecutionMonitorService.class).to(NodeExecutionMonitorServiceImpl.class).in(Singleton.class);
    bind(NodeExecutionInfoService.class).to(NodeExecutionInfoServiceImpl.class);
    bind(ExecutionInputService.class).to(ExecutionInputServiceImpl.class);
    bind(DynamicExecutionService.class).to(DynamicExecutionServiceImpl.class);
    bind(StepExecutionEntityService.class).to(StepExecutionEntityServiceImpl.class);
    bind(ExpressionUsageService.class).to(ExpressionUsageServiceImpl.class).in(Singleton.class);
    bind(ExecutionExpressionUsageService.class).to(ExecutionExpressionUsageServiceImpl.class).in(Singleton.class);
    bind(StageExecutionEntityService.class).to(StageExecutionEntityServiceImpl.class);
    bind(PlanService.class).to(PlanServiceImpl.class).in(Singleton.class);

    bind(InterruptService.class).to(InterruptServiceImpl.class).in(Singleton.class);
    bind(OrchestrationService.class).to(OrchestrationServiceImpl.class).in(Singleton.class);
    bind(OrchestrationEngine.class).to(OrchestrationEngineImpl.class).in(Singleton.class);
    bind(EndNodeExecutionHelper.class).to(EndNodeExecutionHelperImpl.class).in(Singleton.class);
    bind(AbortHelper.class).to(AbortHelperImpl.class).in(Singleton.class);
    bind(ExpiryHelper.class).to(ExpiryHelperImpl.class).in(Singleton.class);
    bind(UserMarkedFailAllHelper.class).to(UserMarkedFailAllHelperImpl.class).in(Singleton.class);
    bind(PlanExecutionMetadataService.class).to(PlanExecutionMetadataServiceImpl.class).in(Singleton.class);
    bind(GovernanceService.class).to(GovernanceServiceImpl.class).in(Singleton.class);
    bind(PipelineExecutionGitMetadataService.class)
        .to(PipelineExecutionGitMetadataServiceImpl.class)
        .in(Singleton.class);

    bind(ExecutionGitMetadataReconciliationEntityService.class)
        .to(ExecutionGitMetadataReconciliationEntityServiceImpl.class)
        .in(Singleton.class);
    MapBinder<TaskCategory, TaskExecutor> taskExecutorMap =
        MapBinder.newMapBinder(binder(), TaskCategory.class, TaskExecutor.class);
    taskExecutorMap.addBinding(TaskCategory.UNKNOWN_CATEGORY).to(NoopTaskExecutor.class);
    taskExecutorMap.addBinding(TaskCategory.DELEGATE_TASK_V2).to(NgDelegate2TaskExecutor.class);

    // PMS Services
    bind(PmsSweepingOutputService.class).to(PmsSweepingOutputServiceImpl.class).in(Singleton.class);
    bind(PmsOutcomeService.class).to(PmsOutcomeServiceImpl.class).in(Singleton.class);
    bind(PmsEngineExpressionService.class).to(PmsEngineExpressionServiceImpl.class).in(Singleton.class);

    if (!config.isWithPMS()) {
      bind(EngineExpressionService.class).to(EngineExpressionServiceImpl.class);
    }

    MapBinder<String, TestExecution> testExecutionMapBinder =
        MapBinder.newMapBinder(binder(), String.class, TestExecution.class);
    Provider<KryoSerializer> kryoSerializerProvider = getProvider(Key.get(KryoSerializer.class));
    testExecutionMapBinder.addBinding("Callback Kryo Registration")
        .toInstance(() -> OrchestrationComponentTester.testKryoRegistration(kryoSerializerProvider));

    install(new OrchestrationEventsFrameworkModule(config.getEventsFrameworkConfiguration()));
    bind(InterruptEventPublisher.class).to(InterruptEventPublisherImpl.class);
    bind(FacilitateEventPublisher.class).to(FacilitateEventPublisherImpl.class).in(Singleton.class);
    bind(ProgressEventPublisher.class).to(ProgressEventPublisherImpl.class).in(Singleton.class);
    bind(NodeAdviseEventPublisher.class).to(NodeAdviseEventPublisherImpl.class).in(Singleton.class);
    bind(NodeResumeEventPublisher.class).to(NodeResumeEventPublisherImpl.class).in(Singleton.class);
    bind(PipelineRetentionService.class).to(PipelineRetentionServiceImpl.class).in(Singleton.class);
  }

  @Provides
  @Named(PERSISTENCE_LAYER)
  PersistenceLayer usedPersistenceLayer() {
    return PersistenceLayer.SPRING;
  }

  @Provides
  @Singleton
  @Named("pipelineExecutionClusterStepConcurrencyLimit")
  Long pipelineExecutionClusterStepConcurrencyLimit() {
    Long limit = config.getPipelineExecutionClusterStepConcurrencyLimit();
    return limit == null ? Long.MAX_VALUE : limit;
  }

  @Provides
  @Singleton
  @Named("pipelineExecutionDefaultMaxLeafStepConcurrency")
  Integer pipelineExecutionDefaultMaxLeafStepConcurrency() {
    Integer value = config.getPipelineExecutionDefaultMaxLeafStepConcurrency();
    return value == null ? 5000 : value;
  }

  @Provides
  @Singleton
  @Named("stepConcurrencyCounterMutationEnabled")
  boolean stepConcurrencyCounterMutationEnabled() {
    return config.isStepConcurrencyCounterMutationEnabled();
  }

  @Provides
  @Singleton
  @Named("stepConcurrencyQueueStoreEnabled")
  boolean stepConcurrencyQueueStoreEnabled() {
    return config.isStepConcurrencyQueueStoreEnabled();
  }

  @Provides
  @Singleton
  @Named("stepConcurrencyGateMode")
  String stepConcurrencyGateMode() {
    return config.getStepConcurrencyGateMode();
  }

  @Provides
  @Singleton
  @Named("pipelineExecutionCounterRebuildJobEnabled")
  boolean pipelineExecutionCounterRebuildJobEnabled() {
    return config.isPipelineExecutionCounterRebuildJobEnabled();
  }

  @Provides
  @Singleton
  @Named("useDbQueueForPlanCreation")
  boolean useDbQueueForPlanCreation() {
    return config.isUseDbQueueForPlanCreation();
  }

  @Provides
  @Singleton
  @Named("planCreationDbQueueBatchSize")
  int planCreationDbQueueBatchSize() {
    return config.getPlanCreationDbQueueBatchSize();
  }

  @Provides
  @Singleton
  @Named("planConcurrencyCounterMutationEnabled")
  boolean planConcurrencyCounterMutationEnabled() {
    return config.isPlanConcurrencyCounterMutationEnabled();
  }

  @Provides
  @Singleton
  @Named("planConcurrencyGateMode")
  String planConcurrencyGateMode() {
    return config.getPlanConcurrencyGateMode();
  }

  @Provides
  @Singleton
  @Named("planConcurrencyRebuildJobEnabled")
  boolean planConcurrencyRebuildJobEnabled() {
    return config.isPlanConcurrencyRebuildJobEnabled();
  }

  @Provides
  @Singleton
  @Named("EngineExecutorService")
  public ExecutorService engineExecutionServiceThreadPool() {
    return ThreadPool.getInstrumentedExecutorService(
        config.getOrchestrationPoolConfig(), "EngineExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("CiSecretResolutionExecutorService")
  public ExecutorService ciSecretResolutionExecutionServiceThreadPool() {
    return ThreadPool.getInstrumentedExecutorService(
        config.getCiSecretResolutionPoolConfig(), "CiSecretResolutionExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("ExpressionUsageExecutorService")
  public ExecutorService expressionUsageExecutionServiceThreadPool() {
    return ThreadPool.getInstrumentedExecutorService(ThreadPoolConfig.builder()
                                                         .corePoolSize(2)
                                                         .maxPoolSize(20)
                                                         .idleTime(30)
                                                         .timeUnit(TimeUnit.SECONDS)
                                                         .queueSize(100)
                                                         .build(),
        "ExpressionUsageExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("ObserverExecutorService")
  public ExecutorService nodeExecutionObserverServiceThreadPool() {
    return ThreadPool.getInstrumentedExecutorService(
        config.getObserverThreadPoolConfig(), "ObserverExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("SdkResponseExecutorService")
  public ExecutorService sdkResponseProcessThreadPool() {
    return ThreadPool.getInstrumentedExecutorService(
        config.getSdkResponseThreadPoolConfig(), "SdkResponseExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  public ExpressionEvaluatorProvider expressionEvaluatorProvider() {
    return config.getExpressionEvaluatorProvider();
  }

  @Provides
  @Named(PUBLISHER_NAME)
  public String publisherName() {
    return config.getPublisherName();
  }

  @Provides
  @Singleton
  public AsyncWaitEngine asyncWaitEngine(
      WaitNotifyEngine waitNotifyEngine, @Named(PUBLISHER_NAME) String publisherName) {
    return new AsyncWaitEngineImpl(waitNotifyEngine, publisherName);
  }

  @Override
  public List<Closeable> servers(Injector injector) {
    return asList(() -> injector.getInstance(TimerScheduledExecutorService.class).shutdownNow());
  }

  @Provides
  @Singleton
  public OrchestrationModuleConfig orchestrationModuleConfig() {
    return config;
  }

  @Provides
  @Singleton
  @Named("orchestrationLogCache")
  public Cache<String, Long> orchestrationLogCache(HarnessCacheManager harnessCacheManager,
      VersionInfoManager versionInfoManager, OrchestrationLogCacheListener orchestrationLogCacheListener) {
    Cache<String, Long> cache = harnessCacheManager.getCache("orchestrationLogCache", String.class, Long.class,
        AccessedExpiryPolicy.factoryOf(
            new Duration(TimeUnit.MINUTES, this.config.getOrchestrationLogConfiguration().getCacheExpiryTimeMinutes())),
        versionInfoManager.getVersionInfo().getBuildNo());
    if (this.config.getOrchestrationLogConfiguration().isShouldUseExpiredListener()) {
      OrchestrationLogCacheRegistrar.registerExpiredListener(cache, orchestrationLogCacheListener,
          this.config.getOrchestrationLogConfiguration().isShouldRetryExpiredListenerRegistration());
    }
    return cache;
  }

  @Provides
  @Singleton
  @Named("pmsMetricsCache")
  public Cache<String, Integer> metricsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("pmsMetricsCache", String.class, Integer.class,
        AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 1)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named("pmsMetricsLoadingCache")
  public LoadingCache<String, Set<String>> metricsLoadingCache() {
    return CacheBuilder.newBuilder().build(new CacheLoader<String, Set<String>>() {
      @Override
      public Set<String> load(String key) throws Exception {
        return new HashSet<>();
      }
    });
  }

  @Provides
  @Singleton
  public OrchestrationLogConfiguration orchestrationLogConfiguration() {
    return config.getOrchestrationLogConfiguration();
  }

  @Provides
  @Singleton
  public OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration() {
    return config.getOrchestrationRestrictionConfiguration();
  }
}

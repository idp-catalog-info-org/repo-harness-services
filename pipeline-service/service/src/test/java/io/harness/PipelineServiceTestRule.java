/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.mockito.Mockito.mock;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.account.services.AccountClient;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.callback.DelegateCallbackToken;
import io.harness.config.OrchestrationModuleConfig;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.delegate.ScheduleTaskServiceGrpc;
import io.harness.engine.executions.node.config.StuckNodeExecutionsMarkingConfig;
import io.harness.engine.expressions.provider.impl.AmbianceExpressionEvaluatorProvider;
import io.harness.entity.ProjectMovementMigrationCheckModule;
import io.harness.factory.ClosingFactory;
import io.harness.ff.FeatureFlagService;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.NoOpGitSyncSdkServiceImpl;
import io.harness.gitsync.persistance.testing.NoOpGitAwarePersistenceImpl;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.kafka.config.KafkaProducerConfig;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.DbAliases;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.oas.OASModule;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.opaclient.OpaServiceClient;
import io.harness.organization.remote.OrganizationClient;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.api.impl.OutboxDaoImpl;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.persistence.HPersistence;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.configuration.PmsSdkConfiguration;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.NoOpPipelineServiceInfoDecorator;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.NoOpPipelineServiceInfoProvider;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.PipelineServiceInfoDecorator;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.PipelineServiceInfoProvider;
import io.harness.project.remote.ProjectClient;
import io.harness.repositories.outbox.OutboxEventRepository;
import io.harness.rule.Cache;
import io.harness.rule.InjectorRuleMixin;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.PipelineServiceModuleRegistrars;
import io.harness.serializer.PrimaryVersionManagerRegistrars;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.springdata.HTransactionTemplate;
import io.harness.telemetry.TelemetryReporter;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;
import io.harness.user.remote.UserClient;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import io.grpc.ServerInterceptor;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.serializer.HObjectMapper;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
public class PipelineServiceTestRule implements InjectorRuleMixin, MethodRule, MongoRuleMixin {
  ClosingFactory closingFactory;

  public PipelineServiceTestRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());

    List<Module> modules = new ArrayList<>();
    modules.add(KryoModule.getInstance());
    modules.add(new ProjectMovementMigrationCheckModule(DbAliases.PMS));
    modules.add(new OASModule() {
      @Override
      public Collection<Class<?>> getResourceClasses() {
        return PipelineServiceConfiguration.getResourceClasses();
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @KafkaModule.General
      @Singleton
      Optional<HKafkaProtoProducer> provideKafkaGCPProducer() {
        return Optional.of(new HKafkaProtoProducer(KafkaProducerConfig.builder()
                                                       .producerEnabled(false)
                                                       .kafkaBaseConfig(KafkaBaseConfig.builder().build())
                                                       .build()));
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      public StuckNodeExecutionsMarkingConfig stuckNodeExecutionsMonitorConfig() {
        return new StuckNodeExecutionsMarkingConfig(true, 5);
      }

      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(PipelineServiceModuleRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(PipelineServiceModuleRegistrars.morphiaRegistrars)
            .addAll(PrimaryVersionManagerRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      TransactionTemplate getTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
        return new HTransactionTemplate(mongoTransactionManager, false);
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return MongoConfig.builder().build();
      }

      @Provides
      @Singleton
      OutboxService getOutboxService(OutboxEventRepository outboxEventRepository) {
        return new OutboxServiceImpl(new OutboxDaoImpl(outboxEventRepository), HObjectMapper.NG_DEFAULT_OBJECT_MAPPER);
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(PipelineServiceModuleRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(PipelineServiceModuleRegistrars.springConverters)
            .build();
      }

      @Provides
      @Named("disableDeserialization")
      @Singleton
      public boolean getSerializationForDelegate() {
        return false;
      }

      @Provides
      @Singleton
      @Named("publishAdviserEventForCustomAdvisers")
      public Boolean getPublishAdviserEventForCustomAdvisers() {
        return true;
      }

      @Provides
      @Singleton
      @Named("throw424ForGITServerErrors")
      public Boolean getThrow424ForGITServerErrors() {
        return false;
      }

      @Provides
      @Singleton
      ScopeInfoClient getScopeInfoClient() {
        return mock(ScopeInfoClient.class);
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(new TypeLiteral<Supplier<DelegateCallbackToken>>() {
        }).toInstance(Suppliers.ofInstance(DelegateCallbackToken.newBuilder().build()));
        bind(AccessControlClient.class).toInstance(mock(AccessControlClient.class));
        bind(DelegateServiceGrpcClient.class).toInstance(mock(DelegateServiceGrpcClient.class));
        bind(DelegateSyncService.class).toInstance(mock(DelegateSyncService.class));
        bind(PipelineMetadataService.class).toInstance(mock(PipelineMetadataService.class));
        bind(DelegateAsyncService.class).toInstance(mock(DelegateAsyncService.class));
        bind(UserClient.class).toInstance(mock(UserClient.class));
        bind(OpaServiceClient.class).toInstance(mock(OpaServiceClient.class));
        bind(new TypeLiteral<DelegateServiceGrpc.DelegateServiceBlockingStub>() {
        }).toInstance(DelegateServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName(generateUuid()).build()));
        bind(new TypeLiteral<ScheduleTaskServiceGrpc.ScheduleTaskServiceBlockingStub>() {
        }).toInstance(ScheduleTaskServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName(generateUuid()).build()));
        bind(GitAwarePersistence.class).to(NoOpGitAwarePersistenceImpl.class);
        bind(GitSyncSdkService.class).to(NoOpGitSyncSdkServiceImpl.class);
        bind(PersistentLocker.class).toInstance(mock(PersistentLocker.class));
        bind(HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub.class)
            .toInstance(HarnessToGitPushInfoServiceGrpc.newBlockingStub(
                InProcessChannelBuilder.forName(generateUuid()).build()));
        bind(PMSPipelineService.class).toInstance(mock(PMSPipelineService.class));
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(FeatureFlagService.class).toInstance(mock(FeatureFlagService.class));
        bind(PipelineGovernanceService.class).toInstance(mock(PipelineGovernanceService.class));
        bind(PipelineEnforcementService.class).toInstance(mock(PipelineEnforcementService.class));
        bind(TemplateResourceClient.class).toInstance(mock(TemplateResourceClient.class));
        bind(NGSettingsClient.class).toInstance(mock(NGSettingsClient.class));
        bind(PipelineTelemetryHelper.class).toInstance(mock(PipelineTelemetryHelper.class));
        bind(AccountService.class).toInstance(mock(AccountService.class));
        bind(Executor.class).annotatedWith(Names.named("TelemetrySenderExecutor")).toInstance(mock(Executor.class));
        bind(TelemetryReporter.class).toInstance(mock(TelemetryReporter.class));
        bind(ObjectStoreClient.class).toInstance(mock(ObjectStoreClient.class));
        bind(ExecutionRetentionService.class).toInstance(mock(ExecutionRetentionService.class));
        bind(ProjectClient.class).annotatedWith(Names.named("PRIVILEGED")).toInstance(mock(ProjectClient.class));
        bind(OrganizationClient.class)
            .annotatedWith(Names.named("PRIVILEGED"))
            .toInstance(mock(OrganizationClient.class));
        Multibinder.newSetBinder(binder(), ServerInterceptor.class);
        bind(PipelineServiceInfoDecorator.class).to(NoOpPipelineServiceInfoDecorator.class);
        bind(PipelineServiceInfoProvider.class).to(NoOpPipelineServiceInfoProvider.class);
        bindConstant().annotatedWith(Names.named("skipSdkMongoRegistration")).to(false);

        // Mock cacheRedissonClient used by the step-concurrency counter service. Tests don't
        // exercise a real Redis; the atomic long is also mocked so any incidental hook call is
        // a no-op.
        RedissonClient mockRedisson = mock(RedissonClient.class);
        RAtomicLong mockAtomicLong = mock(RAtomicLong.class);
        org.mockito.Mockito.when(mockRedisson.getAtomicLong(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(mockAtomicLong);
        bind(RedissonClient.class).annotatedWith(Names.named("cacheRedissonClient")).toInstance(mockRedisson);
        // Mock the jOOQ DSLContext used by StepConcurrencyQueueServiceImpl so the injector can
        // resolve it in tests. Real Postgres is not exercised here.
        bind(org.jooq.DSLContext.class)
            .annotatedWith(Names.named("PipelineServiceDSLContext"))
            .toInstance(mock(org.jooq.DSLContext.class));
      }
    });

    CacheConfigBuilder cacheConfigBuilder =
        CacheConfig.builder().disabledCaches(new HashSet<>()).cacheNamespace("harness-cache");
    if (annotations.stream().anyMatch(annotation -> annotation instanceof Cache)) {
      cacheConfigBuilder.cacheBackend(CAFFEINE);
    } else {
      cacheConfigBuilder.cacheBackend(NOOP);
    }
    CacheModule cacheModule = new CacheModule(cacheConfigBuilder.build());
    modules.add(cacheModule);
    modules.add(PrimaryVersionManagerModule.getInstance());
    modules.add(TimeModule.getInstance());
    modules.add(TestMongoModule.getInstance());
    modules.add(new PipelinePersistenceTestModule());
    //    modules.add(new SpringPersistenceTestModule());
    modules.add(
        OrchestrationModule.getInstance(OrchestrationModuleConfig.builder()
                                            .serviceName("PIPELINE_TEST")
                                            .expressionEvaluatorProvider(new AmbianceExpressionEvaluatorProvider())
                                            .build()));

    modules.add(mongoTypeModule(annotations));

    PmsSdkConfiguration sdkConfig = PmsSdkConfiguration.builder()
                                        .moduleType(ModuleType.PMS)
                                        .deploymentMode(SdkDeployMode.REMOTE_IN_PROCESS)
                                        .engineEventHandlersMap(ImmutableMap.of())
                                        .build();
    modules.add(PmsSdkModule.getInstance(sdkConfig));
    return modules;
  }

  @Override
  public void initialize(Injector injector, List<Module> modules) {
    for (Module module : modules) {
      if (module instanceof ServersModule) {
        for (Closeable server : ((ServersModule) module).servers(injector)) {
          closingFactory.addServer(server);
        }
      }
    }
  }

  @Override
  public Statement apply(Statement base, FrameworkMethod method, Object target) {
    return applyInjector(log, base, method, target);
  }
}

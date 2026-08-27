/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.mockito.Mockito.mock;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HarnessCodeServiceConfig;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.callback.DelegateCallbackToken;
import io.harness.config.OrchestrationModuleConfig;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.delay.DelayEventListener;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.delegate.ScheduleTaskServiceGrpc;
import io.harness.engine.executions.node.config.StuckNodeExecutionsMarkingConfig;
import io.harness.engine.expressions.provider.impl.AmbianceExpressionEvaluatorProvider;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.ff.FeatureFlagService;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.kafka.config.KafkaProducerConfig;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.logstreaming.LogStreamingClient;
import io.harness.logstreaming.LogStreamingClientFactory;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.logstreaming.NGLogStreamingClientFactory;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.opaclient.OpaServiceClient;
import io.harness.persistence.HPersistence;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.configuration.PmsSdkConfiguration;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.NoOpPipelineServiceInfoDecorator;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.NoOpPipelineServiceInfoProvider;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.PipelineServiceInfoDecorator;
import io.harness.pms.sdk.core.plan.creation.creators.pipeline.PipelineServiceInfoProvider;
import io.harness.pms.serializer.json.PmsBeansJacksonModule;
import io.harness.queue.QueueController;
import io.harness.queue.QueueListenerController;
import io.harness.redis.RedisConfig;
import io.harness.rule.Cache;
import io.harness.rule.InjectorRuleMixin;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.OrchestrationBeansRegistrars;
import io.harness.serializer.OrchestrationStepsModuleRegistrars;
import io.harness.serializer.kryo.ConnectorNextGenKryoRegistrar;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.steps.executable.LogBaseUrlProvider;
import io.harness.telemetry.TelemetryReporter;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;
import io.harness.user.remote.UserClient;
import io.harness.version.VersionModule;
import io.harness.waiter.NotifierScheduledExecutorService;
import io.harness.waiter.NotifyResponseCleaner;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.jackson.Jackson;
import io.grpc.ServerInterceptor;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.serializer.HObjectMapper;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.core.convert.converter.Converter;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_DASHBOARD})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationStepsRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  ClosingFactory closingFactory;
  private static final String logStreamingBaseURL = "ORCHESTRATION_STEPS_TEST_BASE_URL";
  public OrchestrationStepsRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());

    List<Module> modules = new ArrayList<>();
    modules.add(new ClosingFactoryModule(closingFactory));
    modules.add(KryoModule.getInstance());
    modules.add(YamlSdkModule.getInstance());
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
      Set<Class<? extends KryoRegistrar>> registrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(OrchestrationStepsModuleRegistrars.kryoRegistrars)
            .add(ConnectorNextGenKryoRegistrar.class)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(OrchestrationStepsModuleRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(OrchestrationBeansRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return MongoConfig.builder().build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(OrchestrationStepsModuleRegistrars.springConverters)
            .build();
      }

      @Provides
      @Singleton
      @Named("logStreamingDelayExecutor")
      public ScheduledExecutorService logStreamingDelayExecutor() {
        return Mockito.mock(ScheduledExecutorService.class);
      }

      @Provides
      @Singleton
      List<YamlSchemaRootClass> yamlSchemaRootClass() {
        return ImmutableList.<YamlSchemaRootClass>builder().build();
      }

      @Provides
      @Named("yaml-schema-mapper")
      @Singleton
      public ObjectMapper getYamlSchemaObjectMapper() {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        HObjectMapper.configureObjectMapperForNG(objectMapper);
        objectMapper.registerModule(new PmsBeansJacksonModule());
        return objectMapper;
      }

      @Provides
      @Named("disableDeserialization")
      @Singleton
      public boolean getSerializationForDelegate() {
        return false;
      }

      @Provides
      @Singleton
      public LogStreamingServiceConfiguration getLogStreamingServiceConfiguration() {
        return LogStreamingServiceConfiguration.builder().baseUrl(logStreamingBaseURL).build();
      }

      @Provides
      @Singleton
      public LogBaseUrlProvider logBaseUrlProvider(LogStreamingServiceConfiguration cfg) {
        final String base = cfg != null && cfg.getBaseUrl() != null ? cfg.getBaseUrl() : "http://localhost:8079";
        return ambiance -> base;
      }

      @Provides
      @Singleton
      @Named("publishAdviserEventForCustomAdvisers")
      public Boolean getPublishAdviserEventForCustomAdvisers() {
        return true;
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      DistributedLockImplementation distributedLockImplementation() {
        return DistributedLockImplementation.NOOP;
      }

      @Provides
      @Named("lock")
      @Singleton
      RedisConfig redisConfig() {
        return RedisConfig.builder().build();
      }

      // Mock the cacheRedissonClient used by the step-concurrency counter service. Tests don't
      // exercise a real Redis; the atomic long is also mocked so any incidental hook call is a
      // no-op.
      @Provides
      @Named("cacheRedissonClient")
      @Singleton
      RedissonClient cacheRedissonClient() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RAtomicLong atomicLong = mock(RAtomicLong.class);
        Mockito.when(redissonClient.getAtomicLong(org.mockito.ArgumentMatchers.anyString())).thenReturn(atomicLong);
        return redissonClient;
      }
    });
    modules.add(PersistentLockModule.getInstance());

    modules.add(mongoTypeModule(annotations));

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(new TypeLiteral<Supplier<DelegateCallbackToken>>() {
        }).toInstance(Suppliers.ofInstance(DelegateCallbackToken.newBuilder().build()));
        bind(AccessControlClient.class).toInstance(mock(AccessControlClient.class));
        bind(DelegateServiceGrpcClient.class).toInstance(mock(DelegateServiceGrpcClient.class));
        bind(DelegateSyncService.class).toInstance(mock(DelegateSyncService.class));
        bind(DelegateAsyncService.class).toInstance(mock(DelegateAsyncService.class));
        bind(UserClient.class).toInstance(mock(UserClient.class));
        bind(OpaServiceClient.class).toInstance(mock(OpaServiceClient.class));
        bind(TelemetryReporter.class).toInstance(mock(TelemetryReporter.class));
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(FeatureFlagService.class).toInstance(mock(FeatureFlagService.class));
        bind(ObjectStoreClient.class).toInstance(mock(ObjectStoreClient.class));
        bind(ExecutionRetentionService.class).toInstance(mock(ExecutionRetentionService.class));
        bind(new TypeLiteral<DelegateServiceGrpc.DelegateServiceBlockingStub>() {
        }).toInstance(DelegateServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName(generateUuid()).build()));
        bind(new TypeLiteral<ScheduleTaskServiceGrpc.ScheduleTaskServiceBlockingStub>() {
        }).toInstance(ScheduleTaskServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName(generateUuid()).build()));
        bind(ScopeInfoClient.class).toInstance(mock(ScopeInfoClient.class));
        // Mock the jOOQ DSLContext used by StepConcurrencyQueueServiceImpl so the injector can
        // resolve it in tests. Real Postgres is not exercised here.
        bind(org.jooq.DSLContext.class)
            .annotatedWith(com.google.inject.name.Names.named("PipelineServiceDSLContext"))
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
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(QueueController.class).toInstance(new QueueController() {
          @Override
          public boolean isPrimary() {
            return true;
          }

          @Override
          public boolean isNotPrimary() {
            return false;
          }
        });
      }
    });

    modules.add(VersionModule.getInstance());
    modules.add(TestMongoModule.getInstance());
    modules.add(TimeModule.getInstance());
    modules.add(new OrchestrationStepsPersistenceTestModule());
    modules.add(
        OrchestrationModule.getInstance(OrchestrationModuleConfig.builder()
                                            .serviceName("ORCHESTRATION_STEPS_TEST")
                                            .expressionEvaluatorProvider(new AmbianceExpressionEvaluatorProvider())
                                            .build()));
    PmsSdkConfiguration sdkConfig = PmsSdkConfiguration.builder()
                                        .deploymentMode(SdkDeployMode.REMOTE_IN_PROCESS)
                                        .moduleType(ModuleType.PMS)
                                        .build();
    modules.add(PmsSdkModule.getInstance(sdkConfig));

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(LogStreamingServiceRestClient.class)
            .toProvider(NGLogStreamingClientFactory.builder().logStreamingServiceBaseUrl(logStreamingBaseURL).build());
        bind(LogStreamingClient.class)
            .toProvider(new LogStreamingClientFactory(logStreamingBaseURL, "", "", false, false));
        Multibinder.newSetBinder(binder(), ServerInterceptor.class);
        bind(PipelineServiceInfoDecorator.class).to(NoOpPipelineServiceInfoDecorator.class);
        bind(PipelineServiceInfoProvider.class).to(NoOpPipelineServiceInfoProvider.class);
        bindConstant().annotatedWith(com.google.inject.name.Names.named("skipSdkMongoRegistration")).to(false);
      }
    });
    modules.add(OrchestrationStepsModule.getInstance(null, HarnessCodeServiceConfig.builder().build()));
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

    final QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    queueListenerController.register(injector.getInstance(DelayEventListener.class), 1);

    injector.getInstance(NotifierScheduledExecutorService.class)
        .scheduleWithFixedDelay(injector.getInstance(NotifyResponseCleaner.class), 0L, 1000L, TimeUnit.MILLISECONDS);
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    return applyInjector(log, statement, frameworkMethod, target);
  }
}

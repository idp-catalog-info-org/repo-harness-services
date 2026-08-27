/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.server;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.config.ModuleSpecificInfo;
import io.harness.engine.interrupts.InterruptGrpcService;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.contracts.expression.RemoteFunctorServiceGrpc;
import io.harness.pms.contracts.governance.JsonExpansionServiceGrpc;
import io.harness.pms.contracts.inputmetadata.InputsMetadataServiceGrpc;
import io.harness.pms.contracts.plan.PlanCreationServiceGrpc;
import io.harness.pms.contracts.plan.PlanCreationServiceGrpc.PlanCreationServiceBlockingStub;
import io.harness.pms.contracts.plan.PluginInfoProviderServiceGrpc;
import io.harness.pms.plan.execution.ExecutionServiceGrpcImpl;
import io.harness.pms.plan.execution.data.service.expressions.EngineExpressionGrpcServiceImpl;
import io.harness.pms.plan.execution.data.service.outcome.OutcomeServiceGrpcServerImpl;
import io.harness.pms.plan.execution.data.service.outputs.SweepingOutputServiceImpl;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.sdk.service.execution.PmsExecutionGrpcService;
import io.harness.pms.template.EntityReferenceGrpcService;
import io.harness.pms.template.VariablesServiceImpl;
import io.harness.threading.ThreadPool;
import io.harness.threading.ThreadPoolConfig;

import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.services.HealthStatusManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineServiceGrpcModule extends AbstractModule {
  private static PipelineServiceGrpcModule instance;
  private MetricRegistry metricRegistry;
  private final boolean useRemoteForPipelineGrpc;
  public static PipelineServiceGrpcModule getInstance(boolean useRemoteForPipelineGrpc, MetricRegistry metricRegistry) {
    if (instance == null) {
      instance = new PipelineServiceGrpcModule(useRemoteForPipelineGrpc, metricRegistry);
    }
    return instance;
  }

  public PipelineServiceGrpcModule(boolean useRemoteForPipelineGrpc, MetricRegistry metricRegistry) {
    this.metricRegistry = metricRegistry;
    this.useRemoteForPipelineGrpc = useRemoteForPipelineGrpc;
  }

  @Override
  protected void configure() {
    Multibinder<ServerInterceptor> serverInterceptorMultibinder =
        Multibinder.newSetBinder(binder(), ServerInterceptor.class);
    serverInterceptorMultibinder.addBinding().to(PipelineServiceGrpcErrorHandler.class);
    serverInterceptorMultibinder.addBinding().to(AccountIdServerInterceptor.class);

    Multibinder<Service> serviceBinder = Multibinder.newSetBinder(binder(), Service.class);
    serviceBinder.addBinding().to(Key.get(Service.class, Names.named("pms-grpc-service")));
    if (!useRemoteForPipelineGrpc) {
      serviceBinder.addBinding().to(Key.get(Service.class, Names.named("pms-grpc-internal-service")));
    }
  }

  @Provides
  @Singleton
  public ServiceManager serviceManager(Set<Service> services) {
    return new ServiceManager(services);
  }

  @Provides
  @Singleton
  public Map<ModuleType, PlanCreationServiceBlockingStub> grpcClients(PipelineServiceConfiguration configuration)
      throws SSLException {
    Map<ModuleType, PlanCreationServiceBlockingStub> map = new HashMap<>();
    if (!useRemoteForPipelineGrpc) {
      map.put(ModuleType.PMS,
          PlanCreationServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName("pmsSdkInternal").build()));
    }
    for (Map.Entry<String, GrpcClientConfig> entry : configuration.getGrpcClientConfigs().entrySet()) {
      if (!useRemoteForPipelineGrpc && ModuleType.fromString(entry.getKey()).equals(ModuleType.PMS)) {
        continue;
      }
      map.put(ModuleType.fromString(entry.getKey()),
          PlanCreationServiceGrpc.newBlockingStub(entry.getValue().getChannelBuilder().build()));
    }
    return map;
  }

  @Provides
  @Singleton
  public Map<String, Executor> grpcPlanCreationExecutors(PipelineServiceConfiguration configuration) {
    Map<String, Executor> map = new HashMap<>();
    Map<String, ModuleSpecificInfo> moduleSpecificInfoMap = configuration.getModuleSpecificInfoMap();
    for (Map.Entry<String, ModuleSpecificInfo> entry : moduleSpecificInfoMap.entrySet()) {
      String moduleName = entry.getKey();
      ThreadPoolConfig moduleThreadPoolConfig = entry.getValue().getPlanCreatorMergeServicePoolConfig();
      if (moduleThreadPoolConfig != null) {
        map.put(moduleName, getModulePlanCreatorMergeExecutorService(moduleName, moduleThreadPoolConfig));
      }
    }
    return map;
  }

  private Executor getModulePlanCreatorMergeExecutorService(
      String moduleName, ThreadPoolConfig moduleThreadPoolConfig) {
    return ThreadPool.getInstrumentedExecutorService(
        moduleThreadPoolConfig, moduleName + "PlanMergeCreatorExecutorService", metricRegistry);
  }

  @Provides
  @Singleton
  public Map<ModuleType, RemoteFunctorServiceGrpc.RemoteFunctorServiceBlockingStub> getExpressionExecutionClients(
      PipelineServiceConfiguration configuration) throws SSLException {
    Map<ModuleType, RemoteFunctorServiceGrpc.RemoteFunctorServiceBlockingStub> map = new HashMap<>();

    for (Map.Entry<String, GrpcClientConfig> entry : configuration.getGrpcClientConfigs().entrySet()) {
      map.put(ModuleType.fromString(entry.getKey()),
          RemoteFunctorServiceGrpc.newBlockingStub(entry.getValue().getChannelBuilder().build()));
    }

    return map;
  }

  @Provides
  @Singleton
  public Map<ModuleType, JsonExpansionServiceGrpc.JsonExpansionServiceBlockingStub> getJsonExpansionHandlerClients(
      PipelineServiceConfiguration configuration) throws SSLException {
    Map<ModuleType, JsonExpansionServiceGrpc.JsonExpansionServiceBlockingStub> map = new HashMap<>();

    for (Map.Entry<String, GrpcClientConfig> entry : configuration.getGrpcClientConfigs().entrySet()) {
      if (!useRemoteForPipelineGrpc && ModuleType.fromString(entry.getKey()).equals(ModuleType.PMS)) {
        continue;
      }
      map.put(ModuleType.fromString(entry.getKey()),
          JsonExpansionServiceGrpc.newBlockingStub(entry.getValue().getChannelBuilder().build()));
    }
    if (!useRemoteForPipelineGrpc) {
      map.put(ModuleType.PMS,
          JsonExpansionServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName("pmsSdkInternal").build()));
    }
    return map;
  }

  @Provides
  @Singleton
  public Map<ModuleType, InputsMetadataServiceGrpc.InputsMetadataServiceBlockingStub> getInputsMetadataHandlerClients(
      PipelineServiceConfiguration configuration) throws SSLException {
    Map<ModuleType, InputsMetadataServiceGrpc.InputsMetadataServiceBlockingStub> map = new HashMap<>();

    for (Map.Entry<String, GrpcClientConfig> entry : configuration.getGrpcClientConfigs().entrySet()) {
      if (!useRemoteForPipelineGrpc && ModuleType.fromString(entry.getKey()).equals(ModuleType.PMS)) {
        continue;
      }
      map.put(ModuleType.fromString(entry.getKey()),
          InputsMetadataServiceGrpc.newBlockingStub(entry.getValue().getChannelBuilder().build()));
    }
    if (!useRemoteForPipelineGrpc) {
      map.put(ModuleType.PMS,
          InputsMetadataServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName("pmsSdkInternal").build()));
    }
    return map;
  }

  @Provides
  @Singleton
  public Map<ModuleType, PluginInfoProviderServiceGrpc.PluginInfoProviderServiceBlockingStub>
  getPluginInfoProviderClients(PipelineServiceConfiguration configuration) throws SSLException {
    Map<ModuleType, PluginInfoProviderServiceGrpc.PluginInfoProviderServiceBlockingStub> map = new HashMap<>();
    for (Map.Entry<String, GrpcClientConfig> entry : configuration.getGrpcClientConfigs().entrySet()) {
      if (!useRemoteForPipelineGrpc && ModuleType.fromString(entry.getKey()).equals(ModuleType.PMS)) {
        continue;
      }
      map.put(ModuleType.fromString(entry.getKey()),
          PluginInfoProviderServiceGrpc.newBlockingStub(entry.getValue().getChannelBuilder().build()));
    }
    if (!useRemoteForPipelineGrpc) {
      map.put(ModuleType.PMS,
          PluginInfoProviderServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName("pmsSdkInternal").build()));
    }
    return map;
  }

  @Provides
  @Singleton
  @Named("pms-grpc-service")
  public Service pmsGrpcService(PipelineServiceConfiguration configuration, HealthStatusManager healthStatusManager,
      Set<BindableService> services, Set<ServerInterceptor> serverInterceptors) {
    return new GrpcServer(configuration.getGrpcServerConfig().getConnectors().get(0), services, serverInterceptors,
        healthStatusManager, metricRegistry);
  }

  @Provides
  @Singleton
  @Named("pms-grpc-internal-service")
  public Service pmsGrpcInternalService(HealthStatusManager healthStatusManager, Set<BindableService> services,
      Set<ServerInterceptor> serverInterceptors) {
    return new GrpcInProcessServer(
        ModuleType.PMS.name().toLowerCase(), services, serverInterceptors, healthStatusManager);
  }

  @Provides
  @VisibleForTesting
  Set<BindableService> bindableServices(HealthStatusManager healthStatusManager,
      PmsSdkInstanceService pmsSdkInstanceService, PmsExecutionGrpcService pmsExecutionGrpcService,
      SweepingOutputServiceImpl sweepingOutputService, OutcomeServiceGrpcServerImpl outcomeServiceGrpcServer,
      EngineExpressionGrpcServiceImpl engineExpressionGrpcService, InterruptGrpcService interruptGrpcService,
      EntityReferenceGrpcService entityReferenceService, VariablesServiceImpl variablesService,
      ExecutionServiceGrpcImpl executionServiceGrpcService) {
    Set<BindableService> services = new HashSet<>();
    services.add(healthStatusManager.getHealthService());
    services.add(pmsSdkInstanceService);
    services.add(pmsExecutionGrpcService);
    services.add(sweepingOutputService);
    services.add(outcomeServiceGrpcServer);
    services.add(engineExpressionGrpcService);
    services.add(interruptGrpcService);
    services.add(entityReferenceService);
    services.add(variablesService);
    services.add(executionServiceGrpcService);
    return services;
  }
}

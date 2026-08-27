/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci;

import io.harness.CIBeansModule;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.steps.nodes.ActionStepNode;
import io.harness.beans.steps.nodes.ArtifactoryUploadNode;
import io.harness.beans.steps.nodes.BackgroundStepNode;
import io.harness.beans.steps.nodes.BitriseStepNode;
import io.harness.beans.steps.nodes.BuildAndPushACRNode;
import io.harness.beans.steps.nodes.BuildAndPushDockerNode;
import io.harness.beans.steps.nodes.BuildAndPushECRNode;
import io.harness.beans.steps.nodes.BuildAndPushGARNode;
import io.harness.beans.steps.nodes.BuildAndPushGCRNode;
import io.harness.beans.steps.nodes.GCSUploadNode;
import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.nodes.HarUploadNode;
import io.harness.beans.steps.nodes.InitializeStepNode;
import io.harness.beans.steps.nodes.PluginStepNode;
import io.harness.beans.steps.nodes.RestoreCacheAzureNode;
import io.harness.beans.steps.nodes.RestoreCacheGCSNode;
import io.harness.beans.steps.nodes.RestoreCacheS3Node;
import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.beans.steps.nodes.RunTestStepNode;
import io.harness.beans.steps.nodes.RunTestStepV2Node;
import io.harness.beans.steps.nodes.S3UploadNode;
import io.harness.beans.steps.nodes.SaveCacheAzureNode;
import io.harness.beans.steps.nodes.SaveCacheGCSNode;
import io.harness.beans.steps.nodes.SaveCacheS3Node;
import io.harness.beans.steps.nodes.SecurityNode;
import io.harness.beans.steps.stepinfo.AiVerifyStepInfo;
import io.harness.beans.steps.stepinfo.PluginCompatibleStep;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestStepV2Info;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIPluginDefaults;
import io.harness.ci.config.ExecutionLimits;
import io.harness.ci.execution.execution.CIBuildImageVmConfigServiceImpl;
import io.harness.ci.execution.execution.CIExecutionConfigServiceImpl;
import io.harness.ci.execution.execution.GitBuildStatusUtilityImpl;
import io.harness.ci.execution.execution.intfc.CIBuildImageVmConfigService;
import io.harness.ci.execution.execution.intfc.CIExecutionConfigService;
import io.harness.ci.execution.execution.intfc.GitBuildStatusUtility;
import io.harness.ci.execution.integrationstage.vm.VmInitializeUtilsImpl;
import io.harness.ci.execution.integrationstage.vm.intfc.VmInitializeUtils;
import io.harness.ci.execution.serializer.AiVerifyStepProtobufSerializer;
import io.harness.ci.execution.serializer.PluginCompatibleStepSerializer;
import io.harness.ci.execution.serializer.PluginStepProtobufSerializer;
import io.harness.ci.execution.serializer.RunStepProtobufSerializer;
import io.harness.ci.execution.serializer.RunTestStepV2ProtobufSerializer;
import io.harness.ci.execution.serializer.RunTestsStepProtobufSerializer;
import io.harness.ci.execution.utils.validation.ValidationUtilsImpl;
import io.harness.ci.execution.utils.validation.intfc.ValidationUtils;
import io.harness.ci.metrics.ExecutionMetricsService;
import io.harness.ci.metrics.ExecutionMetricsServiceImpl;
import io.harness.ci.serializer.ProtobufStepSerializer;
import io.harness.exception.exceptionmanager.exceptionhandler.CILiteEngineExceptionHandler;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.iacm.execution.PluginSettingUtils;
import io.harness.plugin.service.BasePluginCompatibleSerializer;
import io.harness.plugin.service.K8InitializeServiceImpl;
import io.harness.plugin.service.K8sInitializeService;
import io.harness.plugin.service.PluginService;
import io.harness.steps.workloadidentity.WorkloadIdentityTokenService;
import io.harness.steps.workloadidentity.WorkloadIdentityTokenServiceImpl;
import io.harness.threading.ScalingThreadPoolExecutor;
import io.harness.threading.ThreadPoolConfig;
import io.harness.waiter.WaiterConfiguration;
import io.harness.waiter.module.AbstractWaiterModule;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_COMMON_STEPS, HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.CI)
public class CIExecutionServiceModule extends AbstractModule {
  private CIExecutionServiceConfig ciExecutionServiceConfig;
  private final Boolean withPMS;

  public static Set<Class<?>> ciStepsMovedToNewSchema = new HashSet() {
    {
      add(RunStepNode.class);
      add(BackgroundStepNode.class);
      add(RunTestStepNode.class);
      add(GCSUploadNode.class);
      add(S3UploadNode.class);
      add(BuildAndPushDockerNode.class);
      add(BuildAndPushECRNode.class);
      add(BuildAndPushGCRNode.class);
      add(BuildAndPushACRNode.class);
      add(BuildAndPushGARNode.class);
      add(SaveCacheS3Node.class);
      add(SaveCacheGCSNode.class);
      add(SaveCacheAzureNode.class);
      add(RestoreCacheGCSNode.class);
      add(RestoreCacheS3Node.class);
      add(RestoreCacheAzureNode.class);
      add(PluginStepNode.class);
      add(SecurityNode.class);
      add(ArtifactoryUploadNode.class);
      add(HarUploadNode.class);
      add(GitCloneStepNode.class);
      add(InitializeStepNode.class);
      add(ActionStepNode.class);
      add(BitriseStepNode.class);
      add(RunTestStepV2Node.class);
    }
  };

  @Inject
  public CIExecutionServiceModule(CIExecutionServiceConfig ciExecutionServiceConfig, Boolean withPMS) {
    this.ciExecutionServiceConfig = ciExecutionServiceConfig;
    this.withPMS = withPMS;
  }

  @Provides
  public ExecutionLimits ExecutionLimits(CIExecutionServiceConfig ciExecutionServiceConfig) {
    return ciExecutionServiceConfig.getExecutionLimits();
  }

  @Override
  protected void configure() {
    if (ciExecutionServiceConfig.isApplyCIPluginDefaults()) {
      this.ciExecutionServiceConfig = CIPluginDefaults.withAllDefaults(ciExecutionServiceConfig);
    }
    bind(ExecutionMetricsService.class).to(ExecutionMetricsServiceImpl.class);
    install(CIBeansModule.getInstance());
    install(new io.harness.hsqs.client.HsqsServiceClientModule(
        ciExecutionServiceConfig.getQueueServiceClientConfig(), AuthorizationServiceHeader.BEARER.getServiceId()));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("ciRatelimitHandlerExecutor"))
        .toInstance(new ScalingThreadPoolExecutor(
            ThreadPoolConfig.builder().corePoolSize(20).maxPoolSize(300).idleTime(5).timeUnit(TimeUnit.SECONDS).build(),
            "RateLimt-Handler-%d"));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("ciEventHandlerExecutor"))
        .toInstance(new ScalingThreadPoolExecutor(
            ThreadPoolConfig.builder().corePoolSize(20).maxPoolSize(300).idleTime(5).timeUnit(TimeUnit.SECONDS).build(),
            "Event-Handler-%d"));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("ciBackgroundTaskExecutor"))
        .toInstance(new ScalingThreadPoolExecutor(
            ThreadPoolConfig.builder().corePoolSize(20).maxPoolSize(200).idleTime(5).timeUnit(TimeUnit.SECONDS).build(),
            "Background-Task-Handler-%d"));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("ciDataDeletionExecutor"))
        .toInstance(new ScalingThreadPoolExecutor(
            ThreadPoolConfig.builder().corePoolSize(0).maxPoolSize(10).idleTime(5).timeUnit(TimeUnit.SECONDS).build(),
            "Data-Deletion-%d"));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("ciSecretResolutionExecutor"))
        .toInstance(new ScalingThreadPoolExecutor(
            ThreadPoolConfig.builder().corePoolSize(20).maxPoolSize(100).idleTime(5).timeUnit(TimeUnit.SECONDS).build(),
            "Secret-Resolution-%d"));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("ciBillingEventExecutor"))
        .toInstance(new ScalingThreadPoolExecutor(
            ThreadPoolConfig.builder().corePoolSize(20).maxPoolSize(100).idleTime(5).timeUnit(TimeUnit.SECONDS).build(),
            "Billing-Event-%d"));
    this.bind(CIExecutionServiceConfig.class).toInstance(this.ciExecutionServiceConfig);
    bind(new TypeLiteral<ProtobufStepSerializer<RunStepInfo>>() {}).toInstance(new RunStepProtobufSerializer());
    bind(new TypeLiteral<ProtobufStepSerializer<AiVerifyStepInfo>>() {
    }).toInstance(new AiVerifyStepProtobufSerializer());
    bind(new TypeLiteral<ProtobufStepSerializer<RunTestStepV2Info>>() {
    }).toInstance(new RunTestStepV2ProtobufSerializer());
    bind(new TypeLiteral<ProtobufStepSerializer<PluginStepInfo>>() {}).toInstance(new PluginStepProtobufSerializer());
    bind(new TypeLiteral<ProtobufStepSerializer<RunTestsStepInfo>>() {
    }).toInstance(new RunTestsStepProtobufSerializer());
    bind(new TypeLiteral<ProtobufStepSerializer<PluginCompatibleStep>>() {
    }).toInstance(new PluginCompatibleStepSerializer());
    MapBinder<Class<? extends Exception>, ExceptionHandler> exceptionHandlerMapBinder = MapBinder.newMapBinder(
        binder(), new TypeLiteral<Class<? extends Exception>>() {}, new TypeLiteral<ExceptionHandler>() {});
    CILiteEngineExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(CILiteEngineExceptionHandler.class));
    install(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder()
            .versioningDisabled(true)
            .persistenceLayer(WaiterConfiguration.PersistenceLayer.SPRING)
            .build();
      }
    });

    bind(PluginService.class).to(PluginSettingUtils.class);
    bind(BasePluginCompatibleSerializer.class).to(PluginCompatibleStepSerializer.class);
    bind(K8sInitializeService.class).to(K8InitializeServiceImpl.class);
    bind(GitBuildStatusUtility.class).to(GitBuildStatusUtilityImpl.class);
    bind(CIExecutionConfigService.class).to(CIExecutionConfigServiceImpl.class);
    bind(CIBuildImageVmConfigService.class).to(CIBuildImageVmConfigServiceImpl.class);
    bind(VmInitializeUtils.class).to(VmInitializeUtilsImpl.class);
    bind(ValidationUtils.class).to(ValidationUtilsImpl.class);
    bind(WorkloadIdentityTokenService.class).to(WorkloadIdentityTokenServiceImpl.class);
  }
}

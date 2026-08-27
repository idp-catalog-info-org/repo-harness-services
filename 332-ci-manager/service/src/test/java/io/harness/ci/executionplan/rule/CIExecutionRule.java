/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.executionplan.rule;

import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.mockito.Mockito.mock;

import io.harness.ModuleType;
import io.harness.SCMGrpcClientModule;
import io.harness.ScmConnectionConfig;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsClientImpl;
import io.harness.aws.v2.ec2.Ec2V2Client;
import io.harness.aws.v2.ec2.Ec2V2ClientImpl;
import io.harness.aws.v2.ecr.EcrV2Client;
import io.harness.aws.v2.ecr.EcrV2ClientImpl;
import io.harness.beans.HarnessCodeServiceConfig;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.callback.DelegateCallbackToken;
import io.harness.cf.CfClientModule;
import io.harness.ci.CIExecutionServiceModule;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIStepConfig;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.config.VmImageConfig;
import io.harness.ci.coverage.CoverageServiceConfig;
import io.harness.ci.execution.buildstate.SecretDecryptorViaNg;
import io.harness.ci.execution.execution.OrchestrationExecutionEventHandlerRegistrar;
import io.harness.ci.ff.CIFeatureFlagNoopServiceImpl;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.license.CILicenseNoopServiceImpl;
import io.harness.ci.permission.PipelinePermissionMapperModule;
import io.harness.ci.registrars.ExecutionAdvisers;
import io.harness.ci.registrars.ExecutionRegistrar;
import io.harness.ci.testmodule.CIExecutionTestModule;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.cistatus.service.azurerepo.AzureRepoServiceImpl;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.cistatus.service.bitbucket.BitbucketServiceImpl;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.cistatus.service.gitlab.GitlabServiceImpl;
import io.harness.code.CodeResourceClient;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.delegate.ScheduleTaskServiceGrpc;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.exception.exceptionmanager.exceptionhandler.CILiteEngineExceptionHandler;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.ff.FeatureFlagService;
import io.harness.ff.FeatureFlagServiceImpl;
import io.harness.fulcio.beans.HarnessFulcioServiceConfig;
import io.harness.fulcio.remote.HarnessFulcioServiceClient;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.iacmserviceclient.IACMServiceClient;
import io.harness.iacmserviceclient.IACMServiceClientFactory;
import io.harness.impl.ScmServiceClientImpl;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.kafka.config.KafkaProducerConfig;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.persistence.HPersistence;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.configuration.PmsSdkConfiguration;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.queue.QueueController;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Cache;
import io.harness.rule.InjectorRuleMixin;
import io.harness.runner.cgi.CgiConfigClient;
import io.harness.secrets.SecretDecryptor;
import io.harness.service.ScmServiceClient;
import io.harness.springdata.SpringPersistenceTestModule;
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.ssca.remote.SSCAManagerServiceClient;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;
import io.harness.token.remote.TokenClient;
import io.harness.tunnel.TunnelResourceClient;

import com.google.common.base.Suppliers;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.grpc.inprocess.InProcessChannelBuilder;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * Initiates mongo connection and register classes for running UTs
 */

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIExecutionRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  ClosingFactory closingFactory;
  @Rule public CIExecutionTestModule testRule = new CIExecutionTestModule();
  public CIExecutionRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());

    List<Module> modules = new ArrayList<>();
    modules.add(new ClosingFactoryModule(closingFactory));
    modules.add(mongoTypeModule(annotations));
    modules.add(new CIExecutionTestModule());
    modules.add(CfClientModule.getInstance());
    modules.add(new EntitySetupUsageClientModule(
        ServiceHttpClientConfig.builder().baseUrl("http://localhost:7457/").build(), "test_secret", "Service"));
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
      }
    });
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(CIFeatureFlagService.class).to(CIFeatureFlagNoopServiceImpl.class);
        bind(FeatureFlagService.class).to(FeatureFlagServiceImpl.class);
      }
    });
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(CILicenseService.class).to(CILicenseNoopServiceImpl.class);
      }
    });
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(AccountClient.class).annotatedWith(Names.named("PRIVILEGED")).toInstance(mock(AccountClient.class));
        bind(CodeResourceClient.class).toInstance(mock(CodeResourceClient.class));
        bind(TunnelResourceClient.class).toInstance(mock(TunnelResourceClient.class));
        bind(CgiConfigClient.class).toInstance(mock(CgiConfigClient.class));
        bind(io.harness.aitestautomation.service.AiTestAutomationService.class)
            .toInstance(mock(io.harness.aitestautomation.service.AiTestAutomationService.class));
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ScmServiceClient.class).to(ScmServiceClientImpl.class);
        bind(GithubService.class).to(GithubServiceImpl.class);
        bind(GitlabService.class).to(GitlabServiceImpl.class);
        bind(BitbucketService.class).to(BitbucketServiceImpl.class);
        bind(AzureRepoService.class).to(AzureRepoServiceImpl.class);
        bind(SecretDecryptor.class).to(SecretDecryptorViaNg.class);
        bind(AwsClient.class).to(AwsClientImpl.class);
        bind(Ec2V2Client.class).to(Ec2V2ClientImpl.class);
        bind(EcrV2Client.class).to(EcrV2ClientImpl.class);
        bind(IACMServiceConfig.class)
            .toInstance(
                IACMServiceConfig.builder().baseUrl("http://localhost:4000").globalToken("api/v1/token").build());
        bind(SSCAServiceConfig.class)
            .toInstance(
                SSCAServiceConfig.builder()
                    .httpClientConfig(ServiceHttpClientConfig.builder().baseUrl("http://localhost:8186").build())
                    .build());
        bind(HarnessFulcioServiceConfig.class)
            .toInstance(
                HarnessFulcioServiceConfig.builder()
                    .httpClientConfig(ServiceHttpClientConfig.builder().baseUrl("http://localhost:9000").build())
                    .build());
        bind(IACMServiceClient.class).toProvider(IACMServiceClientFactory.class).in(Scopes.SINGLETON);
        bind(SSCAManagerServiceClient.class).toInstance(mock(SSCAManagerServiceClient.class));
        bind(HarnessFulcioServiceClient.class).toInstance(mock(HarnessFulcioServiceClient.class));
        bind(NGSettingsClient.class).toInstance(mock(NGSettingsClient.class));
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

    modules.add(TestMongoModule.getInstance());
    modules.add(new SpringPersistenceTestModule());

    VmImageConfig vmImageConfig = VmImageConfig.builder()
                                      .gitClone("vm-gitClone")
                                      .artifactoryUpload("vm-artifactoryUpload")
                                      .s3Upload("vm-s3Upload")
                                      .gcsUpload("vm-gcsUpload")
                                      .buildAndPushDockerRegistry("vm-buildAndPushDockerRegistry")
                                      .buildAndPushECR("vm-buildAndPushECR")
                                      .buildAndPushGCR("vm-buildAndPushGCR")
                                      .buildAndPushGAR("vm-buildAndPushGAR")
                                      .cacheAzure("vm-cacheAzure")
                                      .cacheGCS("vm-cacheGCS")
                                      .cacheS3("vm-cacheS3")
                                      .cache("vm-cache")
                                      .security("vm-security")
                                      .cacheProxy("vm-cacheProxy")
                                      .build();

    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .gitCloneConfig(StepImageConfig.builder().image("gc:1.2.3").build())
            .buildAndPushDockerRegistryConfig(StepImageConfig.builder().image("bpdr:1.2.3").build())
            .buildAndPushACRConfig(StepImageConfig.builder().image("bpacr:1.2.3").build())
            .buildAndPushECRConfig(StepImageConfig.builder().image("bpecr:1.2.3").build())
            .buildAndPushGCRConfig(StepImageConfig.builder().image("bpgcr:1.2.3").build())
            .buildAndPushGARConfig(StepImageConfig.builder().image("bpgar:1.2.3").build())
            .buildAndPushBuildxDockerRegistryConfig(StepImageConfig.builder().image("bpdr:1.2.3").build())
            .buildAndPushBuildxACRConfig(StepImageConfig.builder().image("bpacr:1.2.3").build())
            .buildAndPushBuildxECRConfig(StepImageConfig.builder().image("bpecr:1.2.3").build())
            .buildAndPushBuildxGCRConfig(StepImageConfig.builder().image("bpgcr:1.2.3").build())
            .buildAndPushBuildxGARConfig(StepImageConfig.builder().image("bpgar:1.2.3").build())
            .gcsUploadConfig(StepImageConfig.builder().image("gcsupload:1.2.3").build())
            .s3UploadConfig(StepImageConfig.builder().image("s3upload:1.2.3").build())
            .artifactoryUploadConfig(StepImageConfig.builder().image("art:1.2.3").build())
            .harUploadConfig(StepImageConfig.builder().image("har:1.2.3").build())
            .securityConfig(StepImageConfig.builder().image("sc:1.2.3").build())
            .cacheGCSConfig(StepImageConfig.builder().image("cachegcs:1.2.3").build())
            .cacheS3Config(StepImageConfig.builder().image("caches3:1.2.3").build())
            .cacheAzureConfig(StepImageConfig.builder().image("cacheAzure3:1.2.3").build())
            .cacheConfig(StepImageConfig.builder().image("cache:1.2.3").build())
            .gcsUploadConfig(StepImageConfig.builder().image("gcsUpload:1.2.3").build())
            .sscaOrchestrationConfig(StepImageConfig.builder().image("sscaorchestrate:0.0.1").build())
            .sscaCdxgenOrchestrationConfig(StepImageConfig.builder().image("sscaCdxgenOrchestration:0.0.1").build())
            .sscaArtifactSigningConfig(StepImageConfig.builder().image("sscaArtifactSigning:0.0.1").build())
            .sscaArtifactVerificationConfig(StepImageConfig.builder().image("sscaArtifactVerification:0.0.1").build())
            .sscaEnforcementConfig(StepImageConfig.builder().image("sscaEnforcement:0.0.1").build())
            .slsaVerificationConfig(StepImageConfig.builder().image("slsaVerification:0.0.1").build())
            .sscaComplianceConfig(StepImageConfig.builder().image("sscaCompliance:0.0.1").build())
            .sscaPrAttestationConfig(StepImageConfig.builder().image("sscaPrAttestation:0.0.1").build())
            .sscaJunitAttestationConfig(StepImageConfig.builder().image("sscaJunitAttestation:0.0.1").build())
            .sscaAibomOrchestrationConfig(StepImageConfig.builder().image("sscaAibomOrchestration:latest").build())
            .enforceAttestationConfig(StepImageConfig.builder().image("enforceAttestation:0.0.1").build())
            .deployAttestationConfig(StepImageConfig.builder().image("deployAttestation:0.0.1").build())
            .provenanceConfig(StepImageConfig.builder().image("provenance:0.0.1").build())
            .iacmTerraformConfig(StepImageConfig.builder().image("harness_terraform:dev").build())
            .iacmTerragruntConfig(StepImageConfig.builder().image("harness_terraform:dev").build())
            .iacmAwsCdkConfig(StepImageConfig.builder().image("harness_terraform:dev").build())
            .iacmAnsible(StepImageConfig.builder().image("harness_ansible:dev").build())
            .iacmOpenTofuConfig(StepImageConfig.builder().image("harness_terraform:dev").build())
            .iacmCheckovConfig(StepImageConfig.builder().image("harness_checkov:dev").build())
            .iacmTFComplianceConfig(StepImageConfig.builder().image("harness_tf_compliance:dev").build())
            .iacmTFLintConfig(StepImageConfig.builder().image("harness_tf_lint:dev").build())
            .iacmTFSecConfig(StepImageConfig.builder().image("harness_tf_sec:dev").build())
            .iacmModuleTestConfig(StepImageConfig.builder().image("harness_terraform:dev").build())
            .iacmBlastRadiusAgentConfig(StepImageConfig.builder().image("harness_blast_radius_agent:dev").build())
            .cookieCutter(StepImageConfig.builder().image("cookiecutter:latest").build())
            .createRepo(StepImageConfig.builder().image("createrepo:latest").build())
            .directPush(StepImageConfig.builder().image("directpush:latest").build())
            .registerCatalog(StepImageConfig.builder().image("registerCatalog:latest").build())
            .createCatalog(StepImageConfig.builder().image("createcatalog:latest").build())
            .slackNotify(StepImageConfig.builder().image("slacknotify:latest").build())
            .createOrganisation(StepImageConfig.builder().image("createorganisation:latest").build())
            .createProject(StepImageConfig.builder().image("createproject:latest").build())
            .createResource(StepImageConfig.builder().image("createresource:latest").build())
            .updateCatalogProperty(StepImageConfig.builder().image("updatecatalogproperty:latest").build())
            .buildkitConfig(StepImageConfig.builder().image("harness/buildkit:1.0.19").build())
            .vmImageConfig(vmImageConfig)
            .cacheProxyConfig(StepImageConfig.builder()
                                  .image("harness/harness-cache-server:1.7.8")
                                  .entrypoint(Collections.singletonList("/app/cache-proxy"))
                                  .build())
            .build();

    modules.add(new CIExecutionServiceModule(CIExecutionServiceConfig.builder()
                                                 .addonImageTag("v1.4-alpha")
                                                 .defaultCPULimit(200)
                                                 .defaultInternalImageConnector("account.harnessimage")
                                                 .defaultMemoryLimit(200)
                                                 .delegateServiceEndpointVariableValue("delegate-service:8080")
                                                 .liteEngineImageTag("v1.4-alpha")
                                                 .addonImage("harness/ci-addon:1.4.0")
                                                 .addonImageRootless("harness/ci-addon:rootless-1.4.0")
                                                 .liteEngineImage("harness/ci-lite-engine:1.4.0")
                                                 .liteEngineImageRootless("harness/ci-lite-engine:rootless-1.4.0")
                                                 .pvcDefaultStorageSize(25600)
                                                 .stepConfig(ciStepConfig)
                                                 .queueServiceClientConfig(QueueServiceClientConfig.builder().build())
                                                 .build(),
        false));

    modules.add(new ProviderModule() {
      @Provides
      @Named("PRIVILEGED")
      @Singleton
      AccessControlClient accessControlClient() {
        return mock(AccessControlClient.class);
      }
    });
    modules.add(new SCMGrpcClientModule(ScmConnectionConfig.builder().url("dummyurl").build()));

    modules.add(TimeModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      DistributedLockImplementation distributedLockImplementation() {
        return DistributedLockImplementation.NOOP;
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return MongoConfig.builder().build();
      }

      @Provides
      @Named("lock")
      @Singleton
      RedisConfig redisConfig() {
        return RedisConfig.builder().build();
      }

      @Provides
      @Singleton
      @Named("harnessCodeGitBaseUrl")
      String getHarnessCodeGitBaseUrl() {
        return "http://localhost:3000/git";
      }

      @Provides
      @Singleton
      @Named("base")
      public String base() {
        return "app";
      }

      @Provides
      @Singleton
      @Named("harnessArtifactRegistryUrl")
      public String harnessArtifactRegistryUrl() {
        return "http://localhost:3000/";
      }

      @Provides
      @Singleton
      public CoverageServiceConfig coverageConfig() {
        return CoverageServiceConfig.builder().build();
      }

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
    modules.add(PersistentLockModule.getInstance());
    modules.add(new PipelinePermissionMapperModule());
    modules.add(new ProviderModule() {
      @Provides
      @Named("PRIVILEGED")
      @Singleton
      TokenClient tokenClient() {
        return mock(TokenClient.class);
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        MapBinder<Class<? extends Exception>, ExceptionHandler> exceptionHandlerMapBinder = MapBinder.newMapBinder(
            binder(), new TypeLiteral<Class<? extends Exception>>() {}, new TypeLiteral<ExceptionHandler>() {});
        CILiteEngineExceptionHandler.exceptions().forEach(
            exception -> exceptionHandlerMapBinder.addBinding(exception).to(CILiteEngineExceptionHandler.class));

        bind(new TypeLiteral<Supplier<DelegateCallbackToken>>() {
        }).toInstance(Suppliers.ofInstance(DelegateCallbackToken.newBuilder().build()));

        bind(new TypeLiteral<DelegateServiceGrpc.DelegateServiceBlockingStub>() {
        }).toInstance(DelegateServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName(generateUuid()).build()));
        bind(new TypeLiteral<ScheduleTaskServiceGrpc.ScheduleTaskServiceBlockingStub>() {
        }).toInstance(ScheduleTaskServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName(generateUuid()).build()));
        bind(new TypeLiteral<io.harness.delegate.task.ScheduleTaskServiceGrpc.ScheduleTaskServiceBlockingStub>() {})
            .toInstance(io.harness.delegate.task.ScheduleTaskServiceGrpc.newBlockingStub(
                InProcessChannelBuilder.forName(generateUuid()).build()));
        bind(String.class).annotatedWith(Names.named("ngBaseUrl")).to(String.class);
        bind(HarnessCodeServiceConfig.class).toInstance(HarnessCodeServiceConfig.builder().build());
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Named("disableDeserialization")
      @Singleton
      public boolean getSerializationForDelegate() {
        return false;
      }
    });
    modules.add(PmsSdkModule.getInstance(getPmsSdkConfiguration()));
    return modules;
  }

  private PmsSdkConfiguration getPmsSdkConfiguration() {
    return PmsSdkConfiguration.builder()
        .deploymentMode(SdkDeployMode.LOCAL)
        .moduleType(ModuleType.CI)
        .engineSteps(ExecutionRegistrar.getEngineSteps())
        .engineAdvisers(ExecutionAdvisers.getEngineAdvisers())
        .engineEventHandlersMap(OrchestrationExecutionEventHandlerRegistrar.getEngineEventHandlers())
        .build();
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
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    return applyInjector(log, statement, frameworkMethod, target);
  }
}

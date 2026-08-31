/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rule;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;

import static org.mockito.Mockito.mock;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.account.services.AccountClient;
import io.harness.callback.DelegateCallbackToken;
import io.harness.config.NgSecretPersistenceTestConfig;
import io.harness.configuration.CgiTaskConfig;
import io.harness.connector.NGConnectorSecretManagerServiceImpl;
import io.harness.connector.oidc.handler.DefaultOidcHandler;
import io.harness.connector.oidc.metrics.ConnectorOidcMetrics;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.delegate.ScheduleTaskServiceGrpc;
import io.harness.encryptors.Encryptors;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.clients.LocalEncryptor;
import io.harness.enforcement.EnforcementModule;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.client.annotation.interceptor.FeatureRestrictionCheckInterceptor;
import io.harness.enforcement.client.servicedependencies.CustomRestrictionRegisterConfiguration;
import io.harness.enforcement.client.servicedependencies.EnforcementClient;
import io.harness.enforcement.client.servicedependencies.EnforcementClientConfiguration;
import io.harness.enforcement.client.servicedependencies.RestrictionUsageRegisterConfiguration;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.services.impl.EnforcementClientServiceImpl;
import io.harness.enforcement.client.services.impl.EnforcementSdkRegisterServiceImpl;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.engine.expressions.VariableFunctorProcessor;
import io.harness.eventsframework.api.Producer;
import io.harness.factory.ClosingFactory;
import io.harness.ff.FeatureFlagService;
import io.harness.file.FileServiceConfiguration;
import io.harness.govern.ProviderMethodInterceptor;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.licensing.services.LicenseService;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.activityhistory.service.NGActivityService;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.modules.SecretManagementModule;
import io.harness.ng.core.remote.licenserestriction.SecretRestrictionUsageImpl;
import io.harness.ng.core.services.ProjectScopeService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ngcertificates.services.NgCertificateService;
import io.harness.ngmanager.NgConnectorManagerClientService;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.opa.OpaService;
import io.harness.outbox.module.TransactionOutboxModule;
import io.harness.persistence.HPersistence;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.secretmanagerclient.remote.SecretManagerClient;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrarsV2;
import io.harness.serializer.NextGenRegistrars;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.springdata.SpringPersistenceModule;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;

import software.wings.service.intfc.FileService;

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
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.springframework.core.convert.converter.Converter;

@Slf4j
public class NgSecretTestRule implements MethodRule, InjectorRuleMixinNew, MongoRuleMixin {
  ClosingFactory closingFactory;
  static final Injector[] injector = {null};

  public NgSecretTestRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    List<Module> modules = new ArrayList<>();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(new TypeLiteral<Map<String, CgiTaskConfig>>() {})
            .annotatedWith(Names.named("cgiTaskConfig"))
            .toInstance(Map.of("testKey", CgiTaskConfig.builder().build()));
      }
    });
    modules.add(new SecretManagementModule());
    modules.add(mongoTypeModule(annotations));
    modules.add(TestMongoModule.getInstance());
    modules.add(new SpringPersistenceModule() {
      @Override
      protected Class<?>[] getConfigClasses() {
        return new Class<?>[] {NgSecretPersistenceTestConfig.class};
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      public FileServiceConfiguration getFileServiceConfiguration() {
        return mock(FileServiceConfiguration.class);
      }
    });
    modules.add(EnforcementModule.getInstance());
    modules.add(new TransactionOutboxModule(null, NG_MANAGER.getServiceId(), false));
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(AccessControlClient.class).toInstance(mock(AccessControlClient.class));
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(AccountClient.class).annotatedWith(Names.named("PRIVILEGED")).toInstance(mock(AccountClient.class));
        bind(ConnectorService.class)
            .annotatedWith(Names.named("connectorDecoratorService"))
            .toInstance(mock(ConnectorService.class));
        bind(ConnectorService.class)
            .annotatedWith(Names.named("defaultConnectorService"))
            .toInstance(mock(ConnectorService.class));
        bind(Producer.class).annotatedWith(Names.named("entity_crud")).toInstance(mock(Producer.class));
        bind(Producer.class).annotatedWith(Names.named("setup_usage")).toInstance(mock(Producer.class));
        bind(NGActivityService.class).toInstance(mock(NGActivityService.class));
        bind(EntitySetupUsageService.class).toInstance(mock(EntitySetupUsageService.class));
        bind(ProjectService.class).toInstance(mock(ProjectService.class));
        bind(ProjectScopeService.class).toInstance(mock(ProjectScopeService.class));
        bind(ScopeInfoService.class).toInstance(mock(ScopeInfoService.class));
        bind(ScopeInfoClient.class).toInstance(mock(ScopeInfoClient.class));
        bind(OpaService.class).toInstance(mock(OpaService.class));
        bind(NgCertificateService.class).toInstance(mock(NgCertificateService.class));
        bind(NGSettingsClient.class).toInstance(mock(NGSettingsClient.class));
        bind(SecretManagerClient.class).toInstance(mock(SecretManagerClient.class));
        bind(DelegateAsyncService.class).toInstance(mock(DelegateAsyncService.class));
        bind(DelegateSyncService.class).toInstance(mock(DelegateSyncService.class));
        bind(TemplateResourceClient.class).toInstance(mock(TemplateResourceClient.class));
        bind(BooleanSupplier.class)
            .annotatedWith(Names.named("driver-installed-in-ng-service"))
            .toInstance(mock(BooleanSupplier.class));
        bind(FileService.class).toInstance(mock(FileService.class));
        bind(DelegateServiceGrpc.DelegateServiceBlockingStub.class)
            .toInstance(mock(DelegateServiceGrpc.DelegateServiceBlockingStub.class));
        bind(ScheduleTaskServiceGrpc.ScheduleTaskServiceBlockingStub.class)
            .toInstance(mock(ScheduleTaskServiceGrpc.ScheduleTaskServiceBlockingStub.class));
        bind(io.harness.delegate.task.ScheduleTaskServiceGrpc.ScheduleTaskServiceBlockingStub.class)
            .toInstance(mock(io.harness.delegate.task.ScheduleTaskServiceGrpc.ScheduleTaskServiceBlockingStub.class));
        bind(FeatureFlagService.class).toInstance(mock(FeatureFlagService.class));
        bind(SecretManagerClientService.class).toInstance(mock(SecretManagerClientService.class));
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(new TypeLiteral<Supplier<DelegateCallbackToken>>() {
        }).toInstance(Suppliers.ofInstance(DelegateCallbackToken.newBuilder().build()));
        bind(Boolean.class).annotatedWith(Names.named("disableDeserialization")).toInstance(false);

        bind(LicenseService.class).toInstance(mock(LicenseService.class));

        bind(EnforcementClientConfiguration.class).toInstance(mock(EnforcementClientConfiguration.class));
        bind(EnforcementClientService.class).to(EnforcementClientServiceImpl.class);
        bind(EnforcementClient.class).toInstance(mock(EnforcementClient.class));
        ProviderMethodInterceptor featureCheck =
            new ProviderMethodInterceptor(getProvider(FeatureRestrictionCheckInterceptor.class));
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(FeatureRestrictionCheck.class), featureCheck);
        bind(EnforcementSdkRegisterService.class).to(EnforcementSdkRegisterServiceImpl.class);
        bind(VariableFunctorProcessor.class).toInstance(mock(VariableFunctorProcessor.class));
        bind(NGConnectorSecretManagerService.class).to(NGConnectorSecretManagerServiceImpl.class);
        bind(DefaultOidcHandler.class).toInstance(mock(DefaultOidcHandler.class));
        bind(ConnectorOidcMetrics.class).toInstance(mock(ConnectorOidcMetrics.class));
        bind(NgConnectorManagerClientService.class).toInstance(mock(NgConnectorManagerClientService.class));
        // Bind encryptors
        bind(KmsEncryptor.class)
            .annotatedWith(Names.named(Encryptors.LOCAL_ENCRYPTOR.getName()))
            .to(LocalEncryptor.class);
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return NextGenRegistrars.kryoRegistrars;
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return NextGenRegistrars.morphiaRegistrars;
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(ManagerRegistrarsV2.morphiaConverters)
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
            .addAll(NextGenRegistrars.springConvertors)
            .build();
      }

      @Provides
      @Named("batch-secrets")
      @Singleton
      ExecutorService batchSecretsExecutorService() {
        return mock(ExecutorService.class);
      }
    });
    return modules;
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    return applyInjector(log, statement, frameworkMethod, target, injector);
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
    injector.getInstance(EnforcementSdkRegisterService.class)
        .initialize(RestrictionUsageRegisterConfiguration.builder()
                        .restrictionNameClassMap(
                            ImmutableMap.<FeatureRestrictionName, Class<? extends RestrictionUsageInterface>>builder()
                                .put(FeatureRestrictionName.MULTIPLE_SECRETS, SecretRestrictionUsageImpl.class)
                                .build())
                        .build(),
            CustomRestrictionRegisterConfiguration.builder().build());
  }
}

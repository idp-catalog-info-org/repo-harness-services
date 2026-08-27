/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.rule;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;

import static org.mockito.Mockito.mock;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.account.services.AccountClient;
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
import io.harness.factory.ClosingFactory;
import io.harness.ff.FeatureFlagService;
import io.harness.govern.ProviderMethodInterceptor;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.licensing.services.LicenseService;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.config.NgVariablePersistenceTestConfig;
import io.harness.ng.core.remote.licenserestriction.VariableRestrictionUsageImpl;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.variable.services.VariableService;
import io.harness.ng.core.variable.services.impl.VariableServiceImpl;
import io.harness.ng.opa.entities.variable.VariableOpaService;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.outbox.module.TransactionOutboxModule;
import io.harness.persistence.HPersistence;
import io.harness.rule.InjectorRuleMixinNew;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.NGVariableRegistrars;
import io.harness.serializer.PersistenceRegistrars;
import io.harness.springdata.SpringPersistenceModule;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.springframework.core.convert.converter.Converter;

@Slf4j
public class NGVariableTestRule implements MethodRule, InjectorRuleMixinNew, MongoRuleMixin {
  ClosingFactory closingFactory;
  static final Injector[] injector = {null};

  public NGVariableTestRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    List<Module> modules = new ArrayList<>();
    modules.add(mongoTypeModule(annotations));
    modules.add(TestMongoModule.getInstance());
    modules.add(new SpringPersistenceModule() {
      @Override
      protected Class<?>[] getConfigClasses() {
        return new Class<?>[] {NgVariablePersistenceTestConfig.class};
      }
    });
    modules.add(new TransactionOutboxModule(null, NG_MANAGER.getServiceId(), false));
    modules.add(EnforcementModule.getInstance());
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(AccessControlClient.class).toInstance(mock(AccessControlClient.class));
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(AccountClient.class).annotatedWith(Names.named("PRIVILEGED")).toInstance(mock(AccountClient.class));
        bind(VariableService.class).to(VariableServiceImpl.class);
        bind(ProjectService.class).toInstance(mock(ProjectService.class));
        bind(OrganizationService.class).toInstance(mock(OrganizationService.class));
        bind(ScopeInfoService.class).toInstance(mock(ScopeInfoService.class));
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(EnforcementClientConfiguration.class).toInstance(mock(EnforcementClientConfiguration.class));
        bind(EnforcementClientService.class).to(EnforcementClientServiceImpl.class);
        bind(EnforcementClient.class).toInstance(mock(EnforcementClient.class));
        bind(VariableOpaService.class).toInstance(mock(VariableOpaService.class));
        bind(FeatureFlagService.class).toInstance(mock(FeatureFlagService.class));
        bind(NGSettingsClient.class).toInstance(mock(NGSettingsClient.class));
        ProviderMethodInterceptor featureCheck =
            new ProviderMethodInterceptor(getProvider(FeatureRestrictionCheckInterceptor.class));
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(FeatureRestrictionCheck.class), featureCheck);
        bind(EnforcementSdkRegisterService.class).to(EnforcementSdkRegisterServiceImpl.class);
        bind(LicenseService.class).toInstance(mock(LicenseService.class));
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder().build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(NGVariableRegistrars.morphiaRegistrars)
            .build();
      }
      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(PersistenceRegistrars.morphiaConverters)
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
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
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
                                .put(FeatureRestrictionName.MULTIPLE_VARIABLES, VariableRestrictionUsageImpl.class)
                                .build())
                        .build(),
            CustomRestrictionRegisterConfiguration.builder().build());
  }
}
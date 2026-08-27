/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rule;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;

import static org.mockito.Mockito.mock;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.config.NgPersistenceOrgTestConfig;
import io.harness.config.NgPersistenceProjectTestConfig;
import io.harness.connector.services.ConnectorScopeService;
import io.harness.connector.services.ConnectorService;
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
import io.harness.entity.ProjectMovementMigrationCheckModule;
import io.harness.eventsframework.api.Producer;
import io.harness.factory.ClosingFactory;
import io.harness.favorites.services.FavoritesScopeService;
import io.harness.favorites.services.FavoritesService;
import io.harness.ff.FeatureFlagService;
import io.harness.fme.client.AccountFeatureFlagEvaluator;
import io.harness.gitsync.branching.GitBranchingHelper;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.govern.ProviderMethodInterceptor;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.licensing.services.LicenseService;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.DbAliases;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.api.DefaultUserGroupScopeService;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.api.SecretScopeService;
import io.harness.ng.core.entitysetupusage.impl.EntitySetupUsageServiceImpl;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageScopeService;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.impl.OrganizationServiceImpl;
import io.harness.ng.core.impl.ProjectServiceImpl;
import io.harness.ng.core.licenserestriction.OrgRestrictionsUsageImpl;
import io.harness.ng.core.licenserestriction.ProjectRestrictionsUsageImpl;
import io.harness.ng.core.migration.NGSecretManagerMigration;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectScopeService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.service.NgUserScopeService;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.ng.userprofile.entities.AwsCodeCommitSCM.AwsCodeCommitSCMMapper;
import io.harness.ng.userprofile.entities.AzureRepoSCM.AzureRepoSCMMapper;
import io.harness.ng.userprofile.entities.BitbucketSCM.BitbucketSCMMapper;
import io.harness.ng.userprofile.entities.GithubSCM.GithubSCMMapper;
import io.harness.ng.userprofile.entities.GitlabSCM.GitlabSCMMapper;
import io.harness.ng.userprofile.entities.SourceCodeManager.SourceCodeManagerMapper;
import io.harness.ngmanager.NgConnectorManagerClient;
import io.harness.ngmanager.TunnelService;
import io.harness.oas.OASModule;
import io.harness.outbox.module.TransactionOutboxModule;
import io.harness.persistence.HPersistence;
import io.harness.pms.serializer.json.PmsBeansJacksonModule;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrarsV2;
import io.harness.serializer.NextGenRegistrars;
import io.harness.springdata.SpringPersistenceModule;
import io.harness.telemetry.TelemetryReporter;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.jackson.Jackson;
import io.serializer.HObjectMapper;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.springframework.core.convert.converter.Converter;

@OwnedBy(PL)
@Slf4j
public class NgManagerRule implements MethodRule, InjectorRuleMixinNew, MongoRuleMixin {
  ClosingFactory closingFactory;
  static final Injector[] injector = {null};

  public NgManagerRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());
    List<Module> modules = new ArrayList<>();
    modules.add(mongoTypeModule(annotations));
    modules.add(TestMongoModule.getInstance());
    modules.add(new SpringPersistenceModule() {
      @Override
      protected Class<?>[] getConfigClasses() {
        return new Class<?>[] {NgPersistenceProjectTestConfig.class, NgPersistenceOrgTestConfig.class};
      }
    });
    modules.add(KryoModule.getInstance());
    modules.add(YamlSdkModule.getInstance());
    modules.add(new ProjectMovementMigrationCheckModule(DbAliases.NG_MANAGER));
    modules.add(new TransactionOutboxModule(null, NG_MANAGER.getServiceId(), false));
    modules.add(new OASModule() {
      @Override
      public Collection<Class<?>> getResourceClasses() {
        return new HashSet<>(NextGenConfiguration.getResourceClasses());
      }
    });
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(GitBranchingHelper.class).toInstance(mock(GitBranchingHelper.class));
        bind(ProjectService.class).to(ProjectServiceImpl.class);
        bind(ProjectScopeService.class).to(ProjectServiceImpl.class);
        bind(OrganizationService.class).to(OrganizationServiceImpl.class);
        bind(AccountFeatureFlagEvaluator.class).toInstance(mock(AccountFeatureFlagEvaluator.class));
        bind(FeatureFlagService.class).toInstance(mock(FeatureFlagService.class));
        bind(EnforcementClientService.class).to(EnforcementClientServiceImpl.class);
        bind(EnforcementClient.class).toInstance(mock(EnforcementClient.class));
        ProviderMethodInterceptor featureCheck =
            new ProviderMethodInterceptor(getProvider(FeatureRestrictionCheckInterceptor.class));
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(FeatureRestrictionCheck.class), featureCheck);
        bind(EnforcementSdkRegisterService.class).to(EnforcementSdkRegisterServiceImpl.class);
        bind(LicenseService.class).toInstance(mock(LicenseService.class));
        bind(EnforcementClientConfiguration.class).toInstance(mock(EnforcementClientConfiguration.class));
        bind(AccessControlClient.class).toInstance(mock(AccessControlClient.class));
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(AccountClient.class).annotatedWith(Names.named("PRIVILEGED")).toInstance(mock(AccountClient.class));
        bind(ConnectorService.class)
            .annotatedWith(Names.named("defaultConnectorService"))
            .toInstance(mock(ConnectorService.class));
        bind(ConnectorService.class)
            .annotatedWith(Names.named("connectorDecoratorService"))
            .toInstance(mock(ConnectorService.class));
        bind(Producer.class).annotatedWith(Names.named("entity_crud")).toInstance(mock(Producer.class));
        bind(FavoritesService.class).toInstance(mock(FavoritesService.class));
        bind(FavoritesScopeService.class).toInstance(mock(FavoritesScopeService.class));
        bind(SecretScopeService.class).toInstance(mock(SecretScopeService.class));
        bind(EntitySetupUsageScopeService.class).toInstance(mock(EntitySetupUsageScopeService.class));
        bind(ConnectorScopeService.class)
            .annotatedWith(Names.named("defaultConnectorService"))
            .toInstance(mock(ConnectorScopeService.class));
        bind(ConnectorScopeService.class)
            .annotatedWith(Names.named("defaultConnectorScopeService"))
            .toInstance(mock(ConnectorScopeService.class));
        bind(YamlGitConfigService.class).toInstance(mock(YamlGitConfigService.class));
        bind(DefaultUserGroupService.class).toInstance(mock(DefaultUserGroupService.class));
        bind(DefaultUserGroupScopeService.class).toInstance(mock(DefaultUserGroupScopeService.class));
        bind(NGEncryptedDataService.class).toInstance(mock(NGEncryptedDataService.class));
        bind(NGSecretManagerService.class).toInstance(mock(NGSecretManagerService.class));
        bind(SecretCrudService.class).toInstance(mock(SecretCrudService.class));
        bind(NgConnectorManagerClient.class).toInstance(mock(NgConnectorManagerClient.class));
        bind(TelemetryReporter.class).toInstance(mock(TelemetryReporter.class));
        bind(MetricService.class).toInstance(mock(MetricService.class));
        bind(TunnelService.class).toInstance(mock(TunnelService.class));
        bind(NgUserService.class).toInstance(mock(NgUserService.class));
        bind(NgUserScopeService.class).toInstance(mock(NgUserScopeService.class));
        bind(NGSecretManagerMigration.class).toInstance(mock(NGSecretManagerMigration.class));
        bind(VariableFunctorProcessor.class).toInstance(mock(VariableFunctorProcessor.class));
        bind(ScopeInfoService.class).toInstance(mock(ScopeInfoService.class));
        bind(EntitySetupUsageService.class).toInstance(mock(EntitySetupUsageServiceImpl.class));
        MapBinder<SCMType, SourceCodeManagerMapper> sourceCodeManagerMapBinder =
            MapBinder.newMapBinder(binder(), SCMType.class, SourceCodeManagerMapper.class);
        sourceCodeManagerMapBinder.addBinding(SCMType.BITBUCKET).to(BitbucketSCMMapper.class);
        sourceCodeManagerMapBinder.addBinding(SCMType.GITLAB).to(GitlabSCMMapper.class);
        sourceCodeManagerMapBinder.addBinding(SCMType.GITHUB).to(GithubSCMMapper.class);
        sourceCodeManagerMapBinder.addBinding(SCMType.AWS_CODE_COMMIT).to(AwsCodeCommitSCMMapper.class);
        sourceCodeManagerMapBinder.addBinding(SCMType.AZURE_REPO).to(AzureRepoSCMMapper.class);
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
      List<YamlSchemaRootClass> yamlSchemaRootClass() {
        return ImmutableList.<YamlSchemaRootClass>builder().addAll(NextGenRegistrars.yamlSchemaRegistrars).build();
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
      @Named("projectScopeInfoDataCache")
      @Singleton
      public Cache<String, ScopeInfo> provideProjectScopeInfoDataCache() {
        return mock(Cache.class);
      }

      @Provides
      @Named("scopeInfoUniqueIdCache")
      @Singleton
      public Cache<String, ScopeInfo> provideScopeInfoUniqueIdCache() {
        return mock(Cache.class);
      }

      @Provides
      @Named("orgScopeInfoDataCache")
      @Singleton
      public Cache<String, ScopeInfo> provideOrgScopeInfoDataCache() {
        return mock(Cache.class);
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
                                .put(FeatureRestrictionName.MULTIPLE_PROJECTS, ProjectRestrictionsUsageImpl.class)
                                .put(FeatureRestrictionName.MULTIPLE_ORGANIZATIONS, OrgRestrictionsUsageImpl.class)
                                .build())
                        .build(),
            CustomRestrictionRegisterConfiguration.builder().build());
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    return applyInjector(log, statement, frameworkMethod, target, injector);
  }
}

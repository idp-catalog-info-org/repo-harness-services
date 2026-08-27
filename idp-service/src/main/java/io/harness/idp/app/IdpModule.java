/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.app;

import static io.harness.audit.ResourceTypeConstants.IDP_ACTION;
import static io.harness.audit.ResourceTypeConstants.IDP_AGGREGATION_RULE;
import static io.harness.audit.ResourceTypeConstants.IDP_ALLOW_LIST;
import static io.harness.audit.ResourceTypeConstants.IDP_APP_CONFIGS;
import static io.harness.audit.ResourceTypeConstants.IDP_BACKSTAGE_CATALOG_ENTITY;
import static io.harness.audit.ResourceTypeConstants.IDP_BACKSTAGE_SCAFFOLDER_TASK;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_CONNECTOR;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_CUSTOM_PROPERTIES;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_DECORATOR;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_INTEGRATIONS;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_TABLE;
import static io.harness.audit.ResourceTypeConstants.IDP_CATALOG_VERSION;
import static io.harness.audit.ResourceTypeConstants.IDP_CHECKS;
import static io.harness.audit.ResourceTypeConstants.IDP_CONFIG_ENV_VARIABLES;
import static io.harness.audit.ResourceTypeConstants.IDP_ENTITY_LINK;
import static io.harness.audit.ResourceTypeConstants.IDP_ENVIRONMENT;
import static io.harness.audit.ResourceTypeConstants.IDP_ENVIRONMENT_BLUEPRINT;
import static io.harness.audit.ResourceTypeConstants.IDP_GIT_INTEGRATIONS;
import static io.harness.audit.ResourceTypeConstants.IDP_GROUPS;
import static io.harness.audit.ResourceTypeConstants.IDP_HOMEPAGE_LAYOUT;
import static io.harness.audit.ResourceTypeConstants.IDP_KIND;
import static io.harness.audit.ResourceTypeConstants.IDP_LAYOUT;
import static io.harness.audit.ResourceTypeConstants.IDP_OAUTH_CONFIG;
import static io.harness.audit.ResourceTypeConstants.IDP_PERMISSIONS;
import static io.harness.audit.ResourceTypeConstants.IDP_PERSONA_VIEW;
import static io.harness.audit.ResourceTypeConstants.IDP_PLUGINS;
import static io.harness.audit.ResourceTypeConstants.IDP_PROXY_HOST;
import static io.harness.audit.ResourceTypeConstants.IDP_SCORECARDS;
import static io.harness.audit.ResourceTypeConstants.IDP_TEAM;
import static io.harness.audit.ResourceTypeConstants.IDP_WORKFLOW;
import static io.harness.authorization.AuthorizationServiceHeader.IDP_SERVICE;
import static io.harness.ci.execution.utils.HostedVmSecretResolver.SECRET_CACHE_KEY;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.USERMEMBERSHIP;
import static io.harness.idp.provision.ProvisionConstants.PROVISION_MODULE_CONFIG;
import static io.harness.lock.DistributedLockImplementation.REDIS;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_POLL_CONFIGURATION;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import io.harness.AccessControlClientModule;
import io.harness.accesscontrol.AccessControlAdminClientModule;
import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsClientImpl;
import io.harness.beans.HarnessCodeServiceConfig;
import io.harness.beans.LlmGatewayConfig;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.cache.NoOpCache;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.cdstage.CDStageConfigResourceClientModule;
import io.harness.ci.CIExecutionServiceModule;
import io.harness.ci.beans.entities.EncryptedDataDetails;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.ci.cacheserviceclient.CacheServiceClientModule;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.coverage.CoverageServiceConfig;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.execution.buildstate.SecretDecryptorViaNg;
import io.harness.ci.execution.queue.CICapacityTaskMessageProcessorImpl;
import io.harness.ci.execution.queue.CIInitTaskMessageProcessorImpl;
import io.harness.ci.execution.queue.CITaskMessageProcessor;
import io.harness.ci.execution.serializer.PluginCompatibleStepSerializer;
import io.harness.ci.execution.validation.CIAccountValidationService;
import io.harness.ci.execution.validation.CIAccountValidationServiceImpl;
import io.harness.ci.execution.validation.CIYAMLSanitizationService;
import io.harness.ci.execution.validation.CIYAMLSanitizationServiceImpl;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.ff.impl.CIFeatureFlagServiceImpl;
import io.harness.ci.license.impl.CILicenseServiceImpl;
import io.harness.ci.logserviceclient.CILogServiceClientModule;
import io.harness.ci.permission.PipelinePermissionMapperModule;
import io.harness.ci.tiserviceclient.TIServiceClientModule;
import io.harness.ci.traceableagenttoken.CITraceableAgentTokenService;
import io.harness.ci.traceableagenttoken.impl.CITraceableAgentTokenServiceImpl;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.cistatus.service.azurerepo.AzureRepoServiceImpl;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.cistatus.service.bitbucket.BitbucketServiceImpl;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.cistatus.service.gitlab.GitlabServiceImpl;
import io.harness.clients.BackstageResourceClientModule;
import io.harness.clients.IdpAgentClientModule;
import io.harness.clients.POServerClientModule;
import io.harness.clients.POServerConfig;
import io.harness.clients.integrationmanager.IntegrationManagerClientModule;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.helper.DecryptionHelperViaManager;
import io.harness.core.ci.dashboard.CIOverviewDashboardService;
import io.harness.core.ci.dashboard.CIOverviewDashboardServiceImpl;
import io.harness.creditcard.CreditCardClientModule;
import io.harness.dashboard.DashboardResourceClientModule;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.enforcement.client.servicedependencies.EnforcementClientConfiguration;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.exception.ExplanationException;
import io.harness.exception.exceptionmanager.ExceptionModule;
import io.harness.favorites.FavoritesClientModule;
import io.harness.file.NGFileServiceModule;
import io.harness.filestore.FileStoreClientModule;
import io.harness.gcp.client.GcpClient;
import io.harness.gcp.impl.GcpClientImpl;
import io.harness.git.GitClientV2;
import io.harness.git.GitClientV2Impl;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.harnessid.client.HarnessIdClientModule;
import io.harness.harnessid.client.HarnessIdServiceConfig;
import io.harness.iacm.execution.PluginSettingUtils;
import io.harness.iacmserviceclient.IACMServiceClientModule;
import io.harness.idp.aggregation.rules.resources.AggregationRulesApiImpl;
import io.harness.idp.aggregation.rules.service.AggregationRulesService;
import io.harness.idp.aggregation.rules.service.AggregationRulesServiceImpl;
import io.harness.idp.allowlist.resources.AllowListApiImpl;
import io.harness.idp.allowlist.services.AllowListService;
import io.harness.idp.allowlist.services.AllowListServiceImpl;
import io.harness.idp.audittrails.eventhandlers.AggregationRuleEventHandler;
import io.harness.idp.audittrails.eventhandlers.AllowListEventHandler;
import io.harness.idp.audittrails.eventhandlers.AppConfigEventHandler;
import io.harness.idp.audittrails.eventhandlers.BackstageCatalogEntityEventHandler;
import io.harness.idp.audittrails.eventhandlers.BackstageScaffolderTaskEventHandler;
import io.harness.idp.audittrails.eventhandlers.BackstageSecretEnvEventHandler;
import io.harness.idp.audittrails.eventhandlers.CatalogConnectorEventHandler;
import io.harness.idp.audittrails.eventhandlers.CatalogCustomPropertiesEventHandler;
import io.harness.idp.audittrails.eventhandlers.CatalogIntegrationsEventHandler;
import io.harness.idp.audittrails.eventhandlers.CheckEventHandler;
import io.harness.idp.audittrails.eventhandlers.GitIntegrationsEventHandler;
import io.harness.idp.audittrails.eventhandlers.GroupEventHandler;
import io.harness.idp.audittrails.eventhandlers.HomePageLayoutEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPActionEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPCatalogDecoratorEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPCatalogEntityVersionEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPCatalogEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPCatalogTableEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPEntityLinkEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPEnvironmentBlueprintEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPEnvironmentEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPKindEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPNextGenOutboxEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPTeamEventHandler;
import io.harness.idp.audittrails.eventhandlers.IDPWorkflowEventHandler;
import io.harness.idp.audittrails.eventhandlers.LayoutEventHandler;
import io.harness.idp.audittrails.eventhandlers.OAuthConfigEventHandler;
import io.harness.idp.audittrails.eventhandlers.PermissionsEventHandler;
import io.harness.idp.audittrails.eventhandlers.PersonaViewEventHandler;
import io.harness.idp.audittrails.eventhandlers.PluginsEventHandler;
import io.harness.idp.audittrails.eventhandlers.ProxyHostDetailsEventHandler;
import io.harness.idp.audittrails.eventhandlers.ScorecardEventHandler;
import io.harness.idp.backstage.resources.BackstageHarnessApiImpl;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.backstage.service.impl.BackstageServiceImpl;
import io.harness.idp.catalog.config.CatalogContentConfig;
import io.harness.idp.catalog.graph.fetcher.CatalogEntityGraphFetcher;
import io.harness.idp.catalog.graph.fetcher.MongoCatalogEntityGraphFetcher;
import io.harness.idp.catalog.graph.strategy.CatalogEntityBfsTraversalStrategy;
import io.harness.idp.catalog.graph.strategy.GraphTraversalStrategy;
import io.harness.idp.catalog.opa.IdpEntityOpaService;
import io.harness.idp.catalog.opa.IdpEntityOpaServiceImpl;
import io.harness.idp.catalog.resources.ActionsApiImpl;
import io.harness.idp.catalog.resources.EntitiesApiImpl;
import io.harness.idp.catalog.resources.EntitiesV2ApiImpl;
import io.harness.idp.catalog.resources.EntityLinksApiImpl;
import io.harness.idp.catalog.resources.KindsApiImpl;
import io.harness.idp.catalog.service.ActionService;
import io.harness.idp.catalog.service.ActionServiceImpl;
import io.harness.idp.catalog.service.ApiEndpointSyncService;
import io.harness.idp.catalog.service.ApiEndpointSyncServiceImpl;
import io.harness.idp.catalog.service.BulkEntityFieldUpdateService;
import io.harness.idp.catalog.service.BulkEntityFieldUpdateServiceImpl;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.service.CatalogServiceImpl;
import io.harness.idp.catalog.service.CatalogTableService;
import io.harness.idp.catalog.service.CatalogTableServiceImpl;
import io.harness.idp.catalog.service.CatalogVersionService;
import io.harness.idp.catalog.service.CatalogVersionServiceImpl;
import io.harness.idp.catalog.service.EntityLinkService;
import io.harness.idp.catalog.service.EntityLinkServiceImpl;
import io.harness.idp.catalog.service.KindService;
import io.harness.idp.catalog.service.KindServiceImpl;
import io.harness.idp.catalog.strategy.BottomUpTeamHierarchyAclStrategy;
import io.harness.idp.catalog.strategy.TeamHierarchyAclStrategy;
import io.harness.idp.ccp.cache.SchemaCache;
import io.harness.idp.ccp.cache.SchemaInMemoryCache;
import io.harness.idp.ccp.resources.CatalogCustomPropertiesApiImpl;
import io.harness.idp.ccp.service.CatalogCustomPropertiesService;
import io.harness.idp.ccp.service.CatalogCustomPropertiesServiceImpl;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.common.DslClientConfig;
import io.harness.idp.common.GcpStorageUtil;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.common.IdpAppConfig;
import io.harness.idp.common.OkHttpClientConnectionPoolConfig;
import io.harness.idp.common.S3StorageUtil;
import io.harness.idp.common.accountdetails.AccountsDetailsCache;
import io.harness.idp.common.accountdetails.cache.memory.AccountsDetailsInMemoryCache;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.common.delegateselectors.cache.memory.DelegateSelectorsInMemoryCache;
import io.harness.idp.common.delegateselectors.cache.redis.DelegateSelectorsRedisCache;
import io.harness.idp.common.encryption.IdpContentEncryptionConfig;
import io.harness.idp.config.CdcKafkaConfig;
import io.harness.idp.configmanager.mappers.CustomPluginDetailedInfoMapper;
import io.harness.idp.configmanager.mappers.DefaultPluginDetailedInfoMapper;
import io.harness.idp.configmanager.mappers.PluginDetailedInfoMapper;
import io.harness.idp.configmanager.resource.AppConfigApiImpl;
import io.harness.idp.configmanager.resource.AuthInfoApiImpl;
import io.harness.idp.configmanager.resource.CustomPluginsApiImpl;
import io.harness.idp.configmanager.resource.CustomPluginsV2ApiImpl;
import io.harness.idp.configmanager.resource.CustomPluginsV2FileUploadApi;
import io.harness.idp.configmanager.resource.CustomPluginsV2FileUploadApiImpl;
import io.harness.idp.configmanager.resource.MergedPluginsConfigApiImpl;
import io.harness.idp.configmanager.resource.PluginFileUploadApi;
import io.harness.idp.configmanager.resource.PluginFileUploadApiImpl;
import io.harness.idp.configmanager.resource.PluginInfoApiImpl;
import io.harness.idp.configmanager.resource.PluginInfoV2ApiImpl;
import io.harness.idp.configmanager.service.AuthInfoService;
import io.harness.idp.configmanager.service.AuthInfoServiceImpl;
import io.harness.idp.configmanager.service.ConfigEnvVariablesService;
import io.harness.idp.configmanager.service.ConfigEnvVariablesServiceImpl;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.service.ConfigManagerServiceImpl;
import io.harness.idp.configmanager.service.CustomPluginService;
import io.harness.idp.configmanager.service.CustomPluginServiceImpl;
import io.harness.idp.configmanager.service.CustomPluginsV2Service;
import io.harness.idp.configmanager.service.CustomPluginsV2ServiceImpl;
import io.harness.idp.configmanager.service.PluginInfoService;
import io.harness.idp.configmanager.service.PluginInfoServiceImpl;
import io.harness.idp.configmanager.service.PluginsProxyInfoService;
import io.harness.idp.configmanager.service.PluginsProxyInfoServiceImpl;
import io.harness.idp.dataplatform.ConfigServiceUdpEventDerivationConfigReader;
import io.harness.idp.dataplatform.CustomKindUdpPublisher;
import io.harness.idp.dataplatform.CustomKindUdpPublisherImpl;
import io.harness.idp.dataplatform.DefaultKindToEventDerivationConfigMapper;
import io.harness.idp.dataplatform.DefaultKindToUpgradePackMapper;
import io.harness.idp.dataplatform.DefaultTypeIngestionRequestAssembler;
import io.harness.idp.dataplatform.EventDerivationConfigYamlParser;
import io.harness.idp.dataplatform.KafkaTypeIngestionKafkaClient;
import io.harness.idp.dataplatform.KindToEventDerivationConfigMapper;
import io.harness.idp.dataplatform.KindToObjectTypeMapper;
import io.harness.idp.dataplatform.SchemaServiceUdpEntityTypeReader;
import io.harness.idp.dataplatform.TypeIngestionKafkaClient;
import io.harness.idp.dataplatform.TypeIngestionRequestAssembler;
import io.harness.idp.dataplatform.UdpEntityTypeReader;
import io.harness.idp.dataplatform.UdpEventDerivationConfigReader;
import io.harness.idp.dataplatform.UdpEventDerivationConstants;
import io.harness.idp.dataplatform.UdpTypeIngestionConfig;
import io.harness.idp.envvariable.beans.entity.BackstageEnvConfigVariableEntity.BackstageEnvConfigVariableMapper;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableMapper;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity.BackstageEnvVariableMapper;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableType;
import io.harness.idp.envvariable.resources.BackstageEnvVariableApiImpl;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.envvariable.service.BackstageEnvVariableServiceImpl;
import io.harness.idp.events.EventsFrameworkModule;
import io.harness.idp.events.eventlisteners.eventhandler.EntityCrudStreamListener;
import io.harness.idp.events.eventlisteners.eventhandler.UserMembershipStreamListener;
import io.harness.idp.gitintegration.processor.factory.ConnectorProcessorFactory;
import io.harness.idp.gitintegration.resources.ConnectorInfoApiImpl;
import io.harness.idp.gitintegration.service.GitIntegrationService;
import io.harness.idp.gitintegration.service.GitIntegrationServiceImpl;
import io.harness.idp.groups.resource.GroupsApiImpl;
import io.harness.idp.groups.service.GroupsService;
import io.harness.idp.groups.service.GroupsServiceImpl;
import io.harness.idp.harnessid.HarnessIdTokenService;
import io.harness.idp.harnessid.HarnessIdTokenServiceImpl;
import io.harness.idp.health.resources.HealthResource;
import io.harness.idp.health.service.HealthResourceImpl;
import io.harness.idp.homepage.config.HomePageCardIconConfig;
import io.harness.idp.homepage.entities.CardEntity.CardMapper;
import io.harness.idp.homepage.entities.ComparisonByHierarchyCardEntity.ComparisonByHierarchyCardMapper;
import io.harness.idp.homepage.entities.CustomLinkCardEntity.CustomLinkCardMapper;
import io.harness.idp.homepage.entities.EntityDistributionOwnershipCardEntity.EntityDistributionOwnershipCardMapper;
import io.harness.idp.homepage.entities.GithubCardEntity.GithubCardMapper;
import io.harness.idp.homepage.entities.HarnessCodeCardEntity.HarnessCodeCardMapper;
import io.harness.idp.homepage.entities.IncidentTrendCardEntity.IncidentTrendCardMapper;
import io.harness.idp.homepage.entities.IncidentsCardEntity.IncidentsCardMapper;
import io.harness.idp.homepage.entities.IntegrationsCardEntity.IntegrationsCardMapper;
import io.harness.idp.homepage.entities.JiraCardEntity.JiraCardMapper;
import io.harness.idp.homepage.entities.LearnMoreCardEntity.LearnMoreCardMapper;
import io.harness.idp.homepage.entities.MarkdownCardEntity.MarkdownCardMapper;
import io.harness.idp.homepage.entities.OwnedEntitiesCardEntity.OwnedEntitiesCardMapper;
import io.harness.idp.homepage.entities.PagerDutyCardEntity.PagerDutyCardMapper;
import io.harness.idp.homepage.entities.RecentBuildsCardEntity.RecentBuildsCardMapper;
import io.harness.idp.homepage.entities.RecentDeploymentsCardEntity.RecentDeploymentsCardMapper;
import io.harness.idp.homepage.entities.RecentlyVisitedCardEntity.RecentlyVisitedCardMapper;
import io.harness.idp.homepage.entities.ScorecardComplianceCardEntity.ScorecardComplianceCardMapper;
import io.harness.idp.homepage.entities.SecurityFindingsCardEntity.SecurityFindingsCardMapper;
import io.harness.idp.homepage.entities.SelfServiceCardEntity.SelfServiceCardMapper;
import io.harness.idp.homepage.entities.StarredEntitiesCardEntity.StarredEntitiesCardMapper;
import io.harness.idp.homepage.entities.StoCardEntity.StoCardMapper;
import io.harness.idp.homepage.entities.TopFailingChecksCardEntity.TopFailingChecksCardMapper;
import io.harness.idp.homepage.entities.TopVisitedCardEntity.TopVisitedCardMapper;
import io.harness.idp.homepage.entities.VideoCardEntity.VideoCardMapper;
import io.harness.idp.homepage.entities.WorkflowRunsCardEntity.WorkflowRunsCardMapper;
import io.harness.idp.homepage.entities.WorkflowsEnvironmentsCardEntity.WorkflowsEnvironmentsCardMapper;
import io.harness.idp.homepage.resource.HomePageLayoutApiImpl;
import io.harness.idp.homepage.resource.IconUploadApi;
import io.harness.idp.homepage.resource.IconUploadApiImpl;
import io.harness.idp.homepage.service.CardService;
import io.harness.idp.homepage.service.CardServiceImpl;
import io.harness.idp.homepage.service.HomePageLayoutService;
import io.harness.idp.homepage.service.HomePageLayoutServiceImpl;
import io.harness.idp.icons.resource.IconApiImpl;
import io.harness.idp.icons.service.IconService;
import io.harness.idp.icons.service.IconServiceImpl;
import io.harness.idp.integrations.resources.IntegrationsApiImpl;
import io.harness.idp.integrations.service.IntegrationService;
import io.harness.idp.integrations.service.IntegrationServiceImpl;
import io.harness.idp.iterators.config.IteratorsConfig;
import io.harness.idp.k8s.client.K8sApiClient;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.layout.resources.LayoutsV3ApiImpl;
import io.harness.idp.layout.resources.LayoutsV4ApiImpl;
import io.harness.idp.license.usage.resources.LicenseUsageResourceApiImpl;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.idp.license.usage.service.impl.IDPLicenseUsageImpl;
import io.harness.idp.license.usage.service.impl.IDPModuleLicenseUsageImpl;
import io.harness.idp.namespace.resource.AccountInfoApiImpl;
import io.harness.idp.namespace.resource.NamespaceApiImpl;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.namespace.service.NamespaceServiceImpl;
import io.harness.idp.onboarding.config.OnboardingModuleConfig;
import io.harness.idp.onboarding.config.OnboardingModuleV2Config;
import io.harness.idp.onboarding.resources.OnboardingResourceApiImpl;
import io.harness.idp.onboarding.resources.OnboardingV2ApiImpl;
import io.harness.idp.onboarding.service.OnboardingService;
import io.harness.idp.onboarding.service.OnboardingServiceV2;
import io.harness.idp.onboarding.service.impl.OnboardingServiceImpl;
import io.harness.idp.onboarding.service.impl.OnboardingServiceV2Impl;
import io.harness.idp.personaview.cards.ComparisonByHierarchyCardService;
import io.harness.idp.personaview.cards.ComparisonByHierarchyCardServiceImpl;
import io.harness.idp.personaview.cards.ScorecardComplianceCache;
import io.harness.idp.personaview.mappers.PersonaViewMapper;
import io.harness.idp.personaview.resource.PersonaViewsApiImpl;
import io.harness.idp.personaview.service.PersonaViewService;
import io.harness.idp.personaview.service.PersonaViewServiceImpl;
import io.harness.idp.pipeline.IDPBuildEnforcerImpl;
import io.harness.idp.plugin.config.CustomPluginsConfig;
import io.harness.idp.provision.ProvisionModuleConfig;
import io.harness.idp.provision.resource.ProvisionApiImpl;
import io.harness.idp.provision.service.ProvisionService;
import io.harness.idp.provision.service.ProvisionServiceImpl;
import io.harness.idp.proxy.agent.resource.IdpAgentProxyApiImpl;
import io.harness.idp.proxy.config.ProxyAllowListConfig;
import io.harness.idp.proxy.delegate.DelegateProxyApi;
import io.harness.idp.proxy.delegate.DelegateProxyApiImpl;
import io.harness.idp.proxy.delegate.DelegateProxyV2Api;
import io.harness.idp.proxy.delegate.DelegateProxyV2ApiImpl;
import io.harness.idp.proxy.environments.resource.EnvironmentProxyApiImpl;
import io.harness.idp.proxy.environments.service.EnvironmentProxyService;
import io.harness.idp.proxy.environments.service.EnvironmentProxyServiceImpl;
import io.harness.idp.proxy.external.resource.ExternalProxyApi;
import io.harness.idp.proxy.external.resource.ExternalProxyApiImpl;
import io.harness.idp.proxy.external.service.ExternalProxyService;
import io.harness.idp.proxy.external.service.ExternalProxyServiceImpl;
import io.harness.idp.proxy.layout.resource.LayoutProxyApiImpl;
import io.harness.idp.proxy.layout.resource.LayoutProxyV2ApiImpl;
import io.harness.idp.proxy.layout.service.LayoutService;
import io.harness.idp.proxy.layout.service.LayoutServiceImpl;
import io.harness.idp.proxy.services.ProxyApi;
import io.harness.idp.proxy.services.ProxyApiImpl;
import io.harness.idp.proxy.workflow.resource.WorkflowProxyApiImpl;
import io.harness.idp.scorecard.checks.resources.ChecksApiImpl;
import io.harness.idp.scorecard.checks.service.CheckService;
import io.harness.idp.scorecard.checks.service.CheckServiceImpl;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datapoints.service.DataPointServiceImpl;
import io.harness.idp.scorecard.datapointsdata.resource.HarnessDataPointsApiImpl;
import io.harness.idp.scorecard.datapointsdata.resource.KubernetesDataPointsApiImpl;
import io.harness.idp.scorecard.datapointsdata.resource.ScmDataPointsApiImpl;
import io.harness.idp.scorecard.datapointsdata.service.DataPointDataValueService;
import io.harness.idp.scorecard.datapointsdata.service.DataPointDataValueServiceImpl;
import io.harness.idp.scorecard.datasourcelocations.service.DataSourceLocationService;
import io.harness.idp.scorecard.datasourcelocations.service.DataSourceLocationServiceImpl;
import io.harness.idp.scorecard.datasources.cache.EnabledIntegrationsInMemoryCache;
import io.harness.idp.scorecard.datasources.resources.DataSourceApiImpl;
import io.harness.idp.scorecard.datasources.service.DataSourceService;
import io.harness.idp.scorecard.datasources.service.DataSourceServiceImpl;
import io.harness.idp.scorecard.scorecards.resources.ScorecardsApiImpl;
import io.harness.idp.scorecard.scorecards.service.ApplicabilityEngine;
import io.harness.idp.scorecard.scorecards.service.ApplicabilityEngineImpl;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scorecards.service.ScorecardServiceImpl;
import io.harness.idp.scorecard.scorecards.service.TierGroupScorecardUsageCheckerImpl;
import io.harness.idp.scorecard.scores.cache.FailureSummaryCache;
import io.harness.idp.scorecard.scores.cache.FailureSummaryService;
import io.harness.idp.scorecard.scores.resources.ScoreApiImpl;
import io.harness.idp.scorecard.scores.resources.ScoresV2ApiImpl;
import io.harness.idp.scorecard.scores.service.AsyncScoreComputationService;
import io.harness.idp.scorecard.scores.service.AsyncScoreComputationServiceImpl;
import io.harness.idp.scorecard.scores.service.ScoreComputerService;
import io.harness.idp.scorecard.scores.service.ScoreComputerServiceImpl;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.idp.scorecard.scores.service.ScoreServiceImpl;
import io.harness.idp.scorecard.scores.service.StatsComputeService;
import io.harness.idp.scorecard.scores.service.StatsComputeServiceImpl;
import io.harness.idp.scorecard.tiergroups.config.DefaultTierGroupConfig;
import io.harness.idp.scorecard.tiergroups.config.TierIconConfig;
import io.harness.idp.scorecard.tiergroups.resources.TierGroupsApiImpl;
import io.harness.idp.scorecard.tiergroups.resources.TierIconUploadApi;
import io.harness.idp.scorecard.tiergroups.resources.TierIconUploadApiImpl;
import io.harness.idp.scorecard.tiergroups.service.TierGroupScorecardUsageChecker;
import io.harness.idp.scorecard.tiergroups.service.TierGroupService;
import io.harness.idp.scorecard.tiergroups.service.TierGroupServiceImpl;
import io.harness.idp.serializer.IdpServiceRegistrars;
import io.harness.idp.settings.resources.BackstagePermissionsApiImpl;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.idp.settings.service.BackstagePermissionsServiceImpl;
import io.harness.idp.status.k8s.HealthCheck;
import io.harness.idp.status.k8s.PodHealthCheck;
import io.harness.idp.status.resources.StatusInfoApiImpl;
import io.harness.idp.status.resources.StatusInfoV2ApiImpl;
import io.harness.idp.status.service.StatusInfoService;
import io.harness.idp.status.service.StatusInfoServiceImpl;
import io.harness.idp.workflowlibrary.config.WorkflowLibraryConfig;
import io.harness.idp.workflowlibrary.resources.WorkflowLibraryApiImpl;
import io.harness.idp.workflowlibrary.service.WorkflowLibraryService;
import io.harness.idp.workflowlibrary.service.WorkflowLibraryServiceImpl;
import io.harness.idp.workflowlibrary.service.WorkflowLibrarySyncService;
import io.harness.idp.workflowlibrary.service.WorkflowLibrarySyncServiceImpl;
import io.harness.impl.ScmServiceClientImpl;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.logstreaming.LogStreamingModule;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.logstreaming.NGLogStreamingClientFactory;
import io.harness.manage.ManagedExecutorService;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngmanager.NgConnectorManagerClientModule;
import io.harness.ngsettings.client.remote.NGSettingsClientModule;
import io.harness.oidc.OidcResourceClientModule;
import io.harness.opa.OpaService;
import io.harness.opa.OpaServiceImpl;
import io.harness.opaclient.OpaClientModule;
import io.harness.organization.OrganizationClientModule;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.outbox.module.TransactionOutboxModule;
import io.harness.packages.HarnessPackages;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.pipeline.dashboards.PMSDashboardResourceClientModule;
import io.harness.pipeline.remote.PipelineRemoteClientModule;
import io.harness.platform.config.service.api.v1.ConfigServiceGrpc;
import io.harness.platform.schema.service.api.v1.SchemaServiceGrpc;
import io.harness.plugin.service.BasePluginCompatibleSerializer;
import io.harness.plugin.service.PluginService;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.privateconnectivity.PrivateConnectivityResourceClientModule;
import io.harness.project.ProjectClientModule;
import io.harness.queryservice.QueryServiceClientModule;
import io.harness.queue.QueueController;
import io.harness.qwietserviceclient.QwietServiceClientModule;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.resourcegroupclient.ResourceGroupClientModule;
import io.harness.runner.plugin.PluginConfigClientModule;
import io.harness.scopeinfoclient.ScopeInfoClientModule;
import io.harness.secrets.SecretDecryptor;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.service.ScmServiceClient;
import io.harness.service.ServiceResourceClientModule;
import io.harness.spec.server.idp.v1.*;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.PluginInfo;
import io.harness.ssca.SSCAManagerServiceClientModule;
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.steps.executable.LogBaseUrlProvider;
import io.harness.steps.shellscript.ShellScriptHelperServiceImplOld;
import io.harness.steps.shellscript.ShellScriptHelperServiceOld;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.stoserviceclient.STOServiceClientModule;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.threading.ScalingThreadPoolExecutor;
import io.harness.threading.ThreadPool;
import io.harness.threading.ThreadPoolConfig;
import io.harness.time.TimeModule;
import io.harness.timescaledb.JooqModule;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.TimeScaleDBServiceImpl;
import io.harness.timescaledb.metrics.HExecuteListener;
import io.harness.token.TokenClientModule;
import io.harness.tunnel.TunnelResourceClientModule;
import io.harness.user.UserClientModule;
import io.harness.usergroups.UserGroupClientModule;
import io.harness.userng.UserNGClientModule;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.variable.VariableClientModule;
import io.harness.version.VersionModule;
import io.harness.waiter.AsyncWaitEngineImpl;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.jooq.ExecuteListener;
import org.redisson.api.RedissonClient;
import org.reflections.Reflections;
import org.springframework.core.convert.converter.Converter;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpModule extends AbstractModule {
  public static final String REDIS_CACHE = "redis";
  public static final String IN_MEMORY = "in-memory";
  private final IdpConfiguration appConfig;
  public IdpModule(IdpConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    registerRequiredBindings();
    install(VersionModule.getInstance());
    install(new IdpPersistenceModule());
    install(new AbstractMongoModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(IdpServiceRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(IdpServiceRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder().build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
      }

      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }

      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return appConfig.getDbAliases();
      }

      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder()
            .put(DelegateSyncTaskResponse.class, "idp_delegateSyncTaskResponses")
            .put(DelegateAsyncTaskResponse.class, "idp_delegateAsyncTaskResponses")
            .put(DelegateTaskProgressResponse.class, "idp_delegateTaskProgressResponses")
            .build();
      }
    });
    install(new EventsFrameworkModule(
        appConfig.getEventsFrameworkConfiguration(), appConfig.getDebeziumConsumersConfigs()));
    install(new AbstractModule() {
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
    ServiceHttpClientConfig scsClientConfig = appConfig.getSecretsConnectorsServiceHttpClientConfig();
    String scsServiceSecret = appConfig.getSecretsConnectorsServiceSecret();
    install(new SecretNGManagerClientModule(scsClientConfig, scsServiceSecret, IDP_SERVICE.getServiceId()));
    install(new DashboardResourceClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId(), ClientMode.PRIVILEGED));
    install(new TIServiceClientModule(appConfig.getTiServiceConfig()));
    install(new ConnectorResourceClientModule(
        scsClientConfig, scsServiceSecret, IDP_SERVICE.getServiceId(), ClientMode.PRIVILEGED));
    install(new OidcResourceClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId(), ClientMode.PRIVILEGED));
    install(new PrivateConnectivityResourceClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId(), ClientMode.PRIVILEGED));
    install(new PMSDashboardResourceClientModule(appConfig.getPipelineServiceConfiguration(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new TokenClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(AccessControlClientModule.getInstance(
        appConfig.getAccessControlClientConfiguration(), IDP_SERVICE.getServiceId()));
    install(
        new NgConnectorManagerClientModule(appConfig.getManagerClientConfig(), appConfig.getManagerServiceSecret()));
    install(new AccountClientModule(
        appConfig.getManagerClientConfig(), appConfig.getManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    ServiceHttpClientConfig rhsConfig =
        appConfig.isRhsEnabled() ? appConfig.getRhsClientConfig() : appConfig.getNgManagerServiceHttpClientConfig();
    String rhsSecret =
        appConfig.isRhsEnabled() ? appConfig.getRhsServiceSecret() : appConfig.getNgManagerServiceSecret();
    install(new OrganizationClientModule(rhsConfig, rhsSecret, IDP_SERVICE.getServiceId()));
    install(new ProjectClientModule(rhsConfig, rhsSecret, IDP_SERVICE.getServiceId()));
    install(new ServiceResourceClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    ServiceHttpClientConfig platformConfigServiceConfig = appConfig.isPlatformConfigServiceEnabled()
        ? appConfig.getPlatformConfigServiceClientConfig()
        : appConfig.getNgManagerServiceHttpClientConfig();
    String platformConfigServiceSecret = appConfig.isPlatformConfigServiceEnabled()
        ? appConfig.getPlatformConfigServiceSecret()
        : appConfig.getNgManagerServiceSecret();
    install(new NGSettingsClientModule(
        platformConfigServiceConfig, platformConfigServiceSecret, IDP_SERVICE.getServiceId(), false));
    install(new PipelineRemoteClientModule(
        appConfig.getPipelineServiceConfiguration(), appConfig.getPipelineServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new FavoritesClientModule(
        platformConfigServiceConfig, platformConfigServiceSecret, IDP_SERVICE.getServiceId()));
    install(new TransactionOutboxModule(DEFAULT_OUTBOX_POLL_CONFIGURATION, IDP_SERVICE.getServiceId(), false));
    install(new BackstageResourceClientModule());
    install(new IdpAgentClientModule());
    install(DelegateServiceDriverModule.getInstance(false, false));
    install(new QueryServiceClientModule(appConfig.getQueryServiceConfig(), IDP_SERVICE.getServiceId()));
    install(ExceptionModule.getInstance());
    //    install(new AbstractWaiterModule() {
    //      @Override
    //      public WaiterConfiguration waiterConfiguration() {
    //        return
    //        WaiterConfiguration.builder().persistenceLayer(WaiterConfiguration.PersistenceLayer.SPRING).build();
    //      }
    //    });
    install(new DelegateServiceDriverGrpcClientModule(
        appConfig.getManagerServiceSecret(), appConfig.getManagerTarget(), appConfig.getManagerAuthority(), true));
    install(new AuditClientModule(appConfig.getAuditClientConfig(), appConfig.getNgManagerServiceSecret(),
        IDP_SERVICE.getServiceId(), appConfig.isEnableAudit()));
    install(new CreditCardClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(NGFileServiceModule.getInstance(appConfig.getFileServiceConfiguration().getFileStorageMode(),
        appConfig.getFileServiceConfiguration().getClusterName()));

    bind(IdpConfiguration.class).toInstance(appConfig);
    install(PersistentLockModule.getInstance());
    install(TimeModule.getInstance());
    install(new CILogServiceClientModule(appConfig.getLogServiceConfig()));
    // Log streaming bindings required by IdpAction (PipelineTaskExecutable -> TaskExecutableWithCapabilities)
    // and by LogStreamingStepClientFactory. Use the dedicated logStreamingServiceConfig block; the
    // CI logServiceConfig is a different service with a different token and is not interchangeable.
    LogStreamingServiceConfiguration logStreamingServiceConfig = appConfig.getLogStreamingServiceConfig();
    String logStreamingBaseUrl = logStreamingServiceConfig != null && logStreamingServiceConfig.getBaseUrl() != null
        ? logStreamingServiceConfig.getBaseUrl()
        : "";
    install(new LogStreamingModule(logStreamingBaseUrl));
    bind(LogStreamingServiceRestClient.class)
        .toProvider(NGLogStreamingClientFactory.builder().logStreamingServiceBaseUrl(logStreamingBaseUrl).build());
    bind(LogBaseUrlProvider.class).toInstance(ambiance -> logStreamingBaseUrl);
    install(NgLicenseHttpClientModule.getInstance(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new SSCAManagerServiceClientModule(appConfig.getSscaServiceConfig().getHttpClientConfig(),
        appConfig.getSscaServiceConfig().getServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new STOServiceClientModule(appConfig.getStoServiceConfig()));
    install(UserClientModule.getInstance(
        appConfig.getManagerClientConfig(), appConfig.getManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new UserNGClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new UserGroupClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new OpaClientModule(
        appConfig.getOpaClientConfig(), appConfig.getPolicyManagerSecret(), IDP_SERVICE.getServiceId(), false));
    install(new QwietServiceClientModule(appConfig.getQwietServiceConfig()));
    install(new PipelinePermissionMapperModule());
    install(
        new CIExecutionServiceModule(appConfig.getCiExecutionServiceConfig(), appConfig.getShouldConfigureWithPMS()));
    install(new IACMServiceClientModule(appConfig.getIacmServiceConfig()));
    install(new POServerClientModule(appConfig.getPoServerConfig()));
    install(new CacheServiceClientModule(appConfig.getCacheServiceConfig()));
    install(EnforcementClientModule.getInstance(appConfig.getNgManagerServiceHttpClientConfig(), // Licencing
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId(),
        appConfig.getEnforcementClientConfiguration()));
    install(new CDStageConfigResourceClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new TunnelResourceClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new AccessControlAdminClientModule(
        appConfig.getAccessControlAdminClientConfiguration(), IDP_SERVICE.getServiceId()));
    install(new ResourceGroupClientModule(appConfig.getResourceGroupClientConfig().getServiceConfig(),
        appConfig.getResourceGroupClientConfig().getSecret(), IDP_SERVICE.getServiceId()));
    install(new VariableClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new ScopeInfoClientModule(rhsConfig, rhsSecret, IDP_SERVICE.getServiceId()));
    install(new EntitySetupUsageClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    install(new IntegrationManagerClientModule(appConfig.getIntegrationManagerClientConfig(),
        appConfig.getIntegrationManagerSecret(), IDP_SERVICE.getServiceId()));
    // HarnessID client for signed user token generation
    HarnessIdServiceConfig harnessIdYamlConfig = appConfig.getHarnessIdClientConfig();
    install(new HarnessIdClientModule(
        HarnessIdServiceConfig.builder()
            .grpcClientConfig(harnessIdYamlConfig != null ? harnessIdYamlConfig.getGrpcClientConfig() : null)
            .restClientConfig(harnessIdYamlConfig != null ? harnessIdYamlConfig.getRestClientConfig() : null)
            .callerServiceName(IDP_SERVICE)
            .build()));
    install(new PluginConfigClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId(), ClientMode.PRIVILEGED, false));
    // Keeping it to 1 thread to start with. Assuming executor service is used only to
    // serve health checks. If it's being used for other tasks also, max pool size should be increased.
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
                                      .setNameFormat("default-idp-service-executor-%d")
                                      .setPriority(Thread.MIN_PRIORITY)
                                      .build();
    bind(ExecutorService.class)
        .annotatedWith(Names.named("idpServiceExecutor"))
        .toInstance(new ScalingThreadPoolExecutor(
            ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(2).idleTime(5).timeUnit(TimeUnit.SECONDS).build(),
            threadFactory));

    registerOutboxEventHandlers();
    bind(OutboxEventHandler.class).to(IDPNextGenOutboxEventHandler.class);
    bind(HPersistence.class).to(MongoPersistence.class).in(Singleton.class);
    bind(ConfigManagerService.class).to(ConfigManagerServiceImpl.class);
    bind(BackstageEnvVariableService.class).to(BackstageEnvVariableServiceImpl.class);
    bind(StatusInfoService.class).to(StatusInfoServiceImpl.class);
    bind(BackstagePermissionsService.class).to(BackstagePermissionsServiceImpl.class);
    bind(NamespaceService.class).to(NamespaceServiceImpl.class);
    bind(GitIntegrationService.class).to(GitIntegrationServiceImpl.class);
    bind(EnvironmentProxyService.class).to(EnvironmentProxyServiceImpl.class);
    bind(OpaService.class).to(OpaServiceImpl.class);
    bind(IdpEntityOpaService.class).to(IdpEntityOpaServiceImpl.class);
    bind(BackstageEnvVariableApi.class).to(BackstageEnvVariableApiImpl.class);
    bind(StatusInfoApi.class).to(StatusInfoApiImpl.class);
    bind(StatusInfoV2Api.class).to(StatusInfoV2ApiImpl.class);
    bind(BackstagePermissionsApi.class).to(BackstagePermissionsApiImpl.class);
    bind(K8sClient.class).to(K8sApiClient.class);
    bind(HealthCheck.class).to(PodHealthCheck.class);
    bind(MessageListener.class).annotatedWith(Names.named(ENTITY_CRUD)).to(EntityCrudStreamListener.class);
    bind(MessageListener.class).annotatedWith(Names.named(USERMEMBERSHIP)).to(UserMembershipStreamListener.class);
    bind(ConnectorProcessorFactory.class);
    bind(NamespaceApi.class).to(NamespaceApiImpl.class);
    bind(AppConfigApi.class).to(AppConfigApiImpl.class);
    bind(AccountInfoApi.class).to(AccountInfoApiImpl.class);
    bind(ProvisionApi.class).to(ProvisionApiImpl.class);
    bind(ProvisionService.class).to(ProvisionServiceImpl.class);
    bind(OnboardingResourceApi.class).to(OnboardingResourceApiImpl.class);
    bind(OnboardingV2Api.class).to(OnboardingV2ApiImpl.class);
    bind(OnboardingService.class).to(OnboardingServiceImpl.class);
    bind(OnboardingServiceV2.class).to(OnboardingServiceV2Impl.class);
    bind(GitClientV2.class).to(GitClientV2Impl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(LayoutProxyApi.class).to(LayoutProxyApiImpl.class);
    bind(LayoutProxyV2Api.class).to(LayoutProxyV2ApiImpl.class);
    bind(WorkflowProxyV2Api.class).to(WorkflowProxyApiImpl.class);
    bind(IdpAgentProxyApi.class).to(IdpAgentProxyApiImpl.class);
    bind(ProxyApi.class).to(ProxyApiImpl.class);
    bind(ExternalProxyApi.class).to(ExternalProxyApiImpl.class);
    bind(ExternalProxyService.class).to(ExternalProxyServiceImpl.class);
    bind(HarnessIdTokenService.class).to(HarnessIdTokenServiceImpl.class);
    bind(PluginInfoApi.class).to(PluginInfoApiImpl.class);
    bind(PluginInfoV2Api.class).to(PluginInfoV2ApiImpl.class);
    bind(PluginFileUploadApi.class).to(PluginFileUploadApiImpl.class);
    bind(DelegateProxyApi.class).to(DelegateProxyApiImpl.class);
    bind(DelegateProxyV2Api.class).to(DelegateProxyV2ApiImpl.class);
    bind(PluginInfoService.class).to(PluginInfoServiceImpl.class);
    bind(CustomPluginService.class).to(CustomPluginServiceImpl.class);
    bind(CustomPluginsV2Service.class).to(CustomPluginsV2ServiceImpl.class);
    bind(CustomPluginsV2Api.class).to(CustomPluginsV2ApiImpl.class);
    bind(CustomPluginsV2FileUploadApi.class).to(CustomPluginsV2FileUploadApiImpl.class);
    bind(ConnectorInfoApi.class).to(ConnectorInfoApiImpl.class);
    bind(MergedPluginsConfigApi.class).to(MergedPluginsConfigApiImpl.class);
    bind(ConfigEnvVariablesService.class).to(ConfigEnvVariablesServiceImpl.class);
    bind(AuthInfoApi.class).to(AuthInfoApiImpl.class);
    bind(AuthInfoService.class).to(AuthInfoServiceImpl.class);
    bind(AllowListApi.class).to(AllowListApiImpl.class);
    bind(AllowListService.class).to(AllowListServiceImpl.class);
    bind(PluginsProxyInfoService.class).to(PluginsProxyInfoServiceImpl.class);
    bind(WorkflowLibraryService.class).to(WorkflowLibraryServiceImpl.class);
    bind(WorkflowLibrarySyncService.class).to(WorkflowLibrarySyncServiceImpl.class);
    bind(ScorecardsApi.class).to(ScorecardsApiImpl.class);
    bind(ScorecardService.class).to(ScorecardServiceImpl.class);
    bind(TierGroupsApi.class).to(TierGroupsApiImpl.class);
    bind(TierIconUploadApi.class).to(TierIconUploadApiImpl.class);
    bind(TierGroupService.class).to(TierGroupServiceImpl.class);
    bind(TierGroupScorecardUsageChecker.class).to(TierGroupScorecardUsageCheckerImpl.class);
    bind(ChecksApi.class).to(ChecksApiImpl.class);
    bind(CheckService.class).to(CheckServiceImpl.class);
    bind(ScoresApi.class).to(ScoreApiImpl.class);
    bind(ScoresV2Api.class).to(ScoresV2ApiImpl.class);
    bind(DataSourceApi.class).to(DataSourceApiImpl.class);
    bind(DataSourceService.class).to(DataSourceServiceImpl.class);
    bind(EnabledIntegrationsInMemoryCache.class).in(Singleton.class);
    bind(DataPointService.class).to(DataPointServiceImpl.class);
    bind(DataSourceLocationService.class).to(DataSourceLocationServiceImpl.class);
    bind(ScoreService.class).to(ScoreServiceImpl.class);
    bind(ApplicabilityEngine.class).to(ApplicabilityEngineImpl.class);
    bind(ScoreComputerService.class).to(ScoreComputerServiceImpl.class);
    bind(StatsComputeService.class).to(StatsComputeServiceImpl.class);
    bind(AsyncScoreComputationService.class).to(AsyncScoreComputationServiceImpl.class);
    bind(DataPointService.class).to(DataPointServiceImpl.class);
    bind(LayoutService.class).to(LayoutServiceImpl.class);
    bind(HarnessDataPointsApi.class).to(HarnessDataPointsApiImpl.class);
    bind(KubernetesDataPointsApi.class).to(KubernetesDataPointsApiImpl.class);
    bind(AggregationRulesApi.class).to(AggregationRulesApiImpl.class);
    bind(DataPointDataValueService.class).to(DataPointDataValueServiceImpl.class);
    bind(ScmDataPointsApi.class).to(ScmDataPointsApiImpl.class);
    bind(IntegrationsApi.class).to(IntegrationsApiImpl.class);
    bind(EnvironmentProxyApi.class).to(EnvironmentProxyApiImpl.class);
    bind(CIOverviewDashboardService.class).to(CIOverviewDashboardServiceImpl.class);
    bind(ExecutorService.class)
        .annotatedWith(Names.named("DefaultDevSpaceEnvProvisioner"))
        .toInstance(new ManagedExecutorService(Executors.newSingleThreadExecutor()));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("ScoreComputer"))
        .toInstance(new ManagedExecutorService(Executors.newFixedThreadPool(Integer.parseInt(appConfig.getCpu())
                * Integer.parseInt(appConfig.getScoreComputerThreadsPerCoreForIterator()),
            new ThreadFactoryBuilder().setNameFormat("score-computer-%d").build())));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("UserScoreComputer"))
        .toInstance(new ManagedExecutorService(Executors.newFixedThreadPool(
            Integer.parseInt(appConfig.getCpu()) * Integer.parseInt(appConfig.getScoreComputerThreadsPerCoreForUser()),
            new ThreadFactoryBuilder().setNameFormat("user-score-computer-%d").build())));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("K8sFailoverSync"))
        .toInstance(new ManagedExecutorService(
            Executors.newFixedThreadPool(appConfig.getIdpAppConfig().getFailoverSync().getThreadCount(),
                new ThreadFactoryBuilder().setNameFormat("k8s-failover-sync-%d").build())));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("HarnessCDCatalogIntegrationSync"))
        .toInstance(new ScalingThreadPoolExecutor(ThreadPoolConfig.builder()
                                                      .corePoolSize(5)
                                                      .maxPoolSize(10)
                                                      .idleTime(30)
                                                      .timeUnit(TimeUnit.SECONDS)
                                                      .build()));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("AggregationRuleComputeExecutor"))
        .toInstance(new ManagedExecutorService(Executors.newFixedThreadPool(Integer.parseInt(appConfig.getCpu())
                * Integer.parseInt(appConfig.getAggregationRuleComputeThreadsPerCore()),
            new ThreadFactoryBuilder().setNameFormat("aggregation-rule-compute-%d").build())));
    bind(HealthResource.class).to(HealthResourceImpl.class);
    bind(CILicenseService.class).to(CILicenseServiceImpl.class).in(Singleton.class);
    bind(CIFeatureFlagService.class).to(CIFeatureFlagServiceImpl.class).in(Singleton.class);
    bind(CIAccountValidationService.class).to(CIAccountValidationServiceImpl.class).in(Singleton.class);
    bind(CITraceableAgentTokenService.class).to(CITraceableAgentTokenServiceImpl.class).in(Singleton.class);
    bind(SecretDecryptor.class).to(SecretDecryptorViaNg.class); // same?
    bind(AwsClient.class).to(AwsClientImpl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(AzureRepoService.class).to(AzureRepoServiceImpl.class);
    bind(BitbucketService.class).to(BitbucketServiceImpl.class);
    bind(GitlabService.class).to(GitlabServiceImpl.class);
    bind(ScmServiceClient.class).to(ScmServiceClientImpl.class);
    bind(CIBuildEnforcer.class).to(IDPBuildEnforcerImpl.class);
    bind(CITaskMessageProcessor.class)
        .annotatedWith(Names.named("ciInitTaskMessageProcessor"))
        .to(CIInitTaskMessageProcessorImpl.class);
    bind(CITaskMessageProcessor.class)
        .annotatedWith(Names.named("ciCapacityTaskMessageProcessor"))
        .to(CICapacityTaskMessageProcessorImpl.class);
    ThreadFactory idpthreadFactory = new ThreadFactoryBuilder().setNameFormat("IDP-Init-Task-Handler-%d").build();
    bind(ExecutorService.class)
        .annotatedWith(Names.named("ciInitTaskExecutor"))
        .toInstance(new ScalingThreadPoolExecutor(
            ThreadPoolConfig.builder().corePoolSize(10).maxPoolSize(50).idleTime(5).timeUnit(TimeUnit.SECONDS).build(),
            idpthreadFactory));
    bind(GroupsApi.class).to(GroupsApiImpl.class);
    bind(GroupsService.class).to(GroupsServiceImpl.class);
    bind(IconsApi.class).to(IconApiImpl.class);
    bind(CardService.class).to(CardServiceImpl.class);
    bind(HomePageLayoutApi.class).to(HomePageLayoutApiImpl.class);
    bind(HomePageLayoutService.class).to(HomePageLayoutServiceImpl.class);
    bind(PersonaViewMapper.class);
    bind(PersonaViewService.class).to(PersonaViewServiceImpl.class);
    bind(ComparisonByHierarchyCardService.class).to(ComparisonByHierarchyCardServiceImpl.class);
    bind(ScorecardComplianceCache.class).in(Singleton.class);
    bind(PersonaViewsApi.class).to(PersonaViewsApiImpl.class);
    bind(IconService.class).to(IconServiceImpl.class);
    bind(IconUploadApi.class).to(IconUploadApiImpl.class);
    bind(AccountsDetailsCache.class).to(AccountsDetailsInMemoryCache.class);
    bind(ShellScriptHelperServiceOld.class).to(ShellScriptHelperServiceImplOld.class);

    install(new FileStoreClientModule(appConfig.getNgManagerServiceHttpClientConfig(),
        appConfig.getNgManagerServiceSecret(), IDP_SERVICE.getServiceId()));
    bind(PmsFeatureFlagService.class).to(PmsFeatureFlagHelper.class);

    bind(EntitiesApi.class).to(EntitiesApiImpl.class);
    bind(ActionsApi.class).to(ActionsApiImpl.class);
    bind(EntitiesV2Api.class).to(EntitiesV2ApiImpl.class);
    bind(KindsApi.class).to(KindsApiImpl.class);
    bind(LayoutsV3Api.class).to(LayoutsV3ApiImpl.class);
    bind(LayoutsV4Api.class).to(LayoutsV4ApiImpl.class);
    bind(WorkflowLibraryApi.class).to(WorkflowLibraryApiImpl.class);
    bind(EntityLinksApi.class).to(EntityLinksApiImpl.class);
    bind(EntityLinkService.class).to(EntityLinkServiceImpl.class);

    bind(CatalogEntityGraphFetcher.class).to(MongoCatalogEntityGraphFetcher.class);
    bind(GraphTraversalStrategy.class).to(CatalogEntityBfsTraversalStrategy.class);
    bind(io.harness.idp.catalog.graph.filter.GraphRbacFilter.class)
        .to(io.harness.idp.catalog.graph.filter.BulkGraphRbacFilter.class);
    bind(io.harness.spec.server.idp.v1.GraphApi.class).to(io.harness.idp.catalog.graph.resources.GraphApiImpl.class);
    bind(io.harness.spec.server.idp.v1.TeamsApi.class).to(io.harness.idp.catalog.resources.TeamsApiImpl.class);
    bind(TeamHierarchyAclStrategy.class).to(BottomUpTeamHierarchyAclStrategy.class);

    if (appConfig.getDelegateSelectorsCacheMode().equals(IN_MEMORY)) {
      bind(DelegateSelectorsCache.class).to(DelegateSelectorsInMemoryCache.class);
    } else if (appConfig.getDelegateSelectorsCacheMode().equals(REDIS_CACHE)) {
      bind(DelegateSelectorsCache.class).to(DelegateSelectorsRedisCache.class);
    }

    MapBinder<BackstageEnvVariableType, BackstageEnvVariableMapper> backstageEnvVariableMapBinder =
        MapBinder.newMapBinder(binder(), BackstageEnvVariableType.class, BackstageEnvVariableMapper.class);
    backstageEnvVariableMapBinder.addBinding(BackstageEnvVariableType.CONFIG)
        .to(BackstageEnvConfigVariableMapper.class);
    backstageEnvVariableMapBinder.addBinding(BackstageEnvVariableType.SECRET)
        .to(BackstageEnvSecretVariableMapper.class);

    // MapBinder for Cards Type for HomePage Layout
    MapBinder<Card.TypeEnum, CardMapper> cardTypeCardMapperMapBinder =
        MapBinder.newMapBinder(binder(), Card.TypeEnum.class, CardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.LEARN_MORE).to(LearnMoreCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.TOP_VISITED).to(TopVisitedCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.RECENTLY_VISITED).to(RecentlyVisitedCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.STARRED_ENTITIES).to(StarredEntitiesCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.MARKDOWN).to(MarkdownCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.VIDEO).to(VideoCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.CUSTOM_LINK).to(CustomLinkCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.SELF_SERVICE).to(SelfServiceCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.GITHUB).to(GithubCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.JIRA).to(JiraCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.HARNESS_CODE).to(HarnessCodeCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.PAGER_DUTY).to(PagerDutyCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.OWNED_ENTITIES).to(OwnedEntitiesCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.WORKFLOW_RUNS).to(WorkflowRunsCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.INCIDENTS).to(IncidentsCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.RECENT_BUILDS).to(RecentBuildsCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.RECENT_DEPLOYMENTS).to(RecentDeploymentsCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.ENTITY_DISTRIBUTION_OWNERSHIP)
        .to(EntityDistributionOwnershipCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.SCORECARD_COMPLIANCE).to(ScorecardComplianceCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.TOP_FAILING_CHECKS).to(TopFailingChecksCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.WORKFLOWS_ENVIRONMENTS)
        .to(WorkflowsEnvironmentsCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.INTEGRATIONS).to(IntegrationsCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.INCIDENT_TREND).to(IncidentTrendCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.SECURITY_FINDINGS).to(SecurityFindingsCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.STO).to(StoCardMapper.class);
    cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.COMPARISON_BY_HIERARCHY)
        .to(ComparisonByHierarchyCardMapper.class);

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("TaskPoll-Thread"));

    bind(PluginService.class).to(PluginSettingUtils.class);
    bind(HarnessCodeServiceConfig.class)
        .toInstance(HarnessCodeServiceConfig.builder()
                        .serviceSecret(appConfig.getHarnessCodeRepoConfig().getServiceClientSharedSecret())
                        .gitUrl(appConfig.getHarnessCodeRepoConfig().getGitBaseUrl())
                        .apiUrl(appConfig.getHarnessCodeRepoConfig().getApiUrl())
                        .build());
    bind(LlmGatewayConfig.class)
        .toInstance(LlmGatewayConfig.builder().baseUrl(appConfig.getLlmGatewayBaseUrl()).build());
    bind(BasePluginCompatibleSerializer.class).to(PluginCompatibleStepSerializer.class);
    bind(CIYAMLSanitizationService.class).to(CIYAMLSanitizationServiceImpl.class).in(Singleton.class);
    //    bind(HsqsClient.class).toProvider(HsqsServiceHttpClientFactory.class).in(Scopes.SINGLETON);

    bind(LicenseUsageInterface.class).to(IDPLicenseUsageImpl.class);
    bind(FailureSummaryService.class).to(FailureSummaryCache.class);
    bind(IDPModuleLicenseUsage.class).to(IDPModuleLicenseUsageImpl.class);
    bind(LicenseUsageResourceApi.class).to(LicenseUsageResourceApiImpl.class);
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return appConfig.getSegmentConfiguration();
      }
    });
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("idpTelemetryPublisherExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("idp-telemetry-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
    if (appConfig.getEnableDashboardTimescale() != null && appConfig.getEnableDashboardTimescale()) {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(appConfig.getTimeScaleDBConfig() != null ? appConfig.getTimeScaleDBConfig()
                                                               : TimeScaleDBConfig.builder().build());
    } else {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(TimeScaleDBConfig.builder().build());
    }
    try {
      bind(TimeScaleDBService.class)
          .toConstructor(TimeScaleDBServiceImpl.class.getConstructor(TimeScaleDBConfig.class));
    } catch (NoSuchMethodException e) {
      log.error("TimeScaleDbServiceImpl Initialization Failed in due to missing constructor", e);
    }
    install(JooqModule.getInstance());

    MapBinder<PluginInfo.PluginTypeEnum, PluginDetailedInfoMapper> pluginInfoMapBinder =
        MapBinder.newMapBinder(binder(), PluginInfo.PluginTypeEnum.class, PluginDetailedInfoMapper.class);
    pluginInfoMapBinder.addBinding(PluginInfo.PluginTypeEnum.DEFAULT).to(DefaultPluginDetailedInfoMapper.class);
    pluginInfoMapBinder.addBinding(PluginInfo.PluginTypeEnum.CUSTOM).to(CustomPluginDetailedInfoMapper.class);

    bind(BackstageHarnessApi.class).to(BackstageHarnessApiImpl.class);
    bind(BackstageService.class).to(BackstageServiceImpl.class);
    bind(CustomPluginsApi.class).to(CustomPluginsApiImpl.class);
    bind(CatalogCustomPropertiesApi.class).to(CatalogCustomPropertiesApiImpl.class);
    bind(CatalogCustomPropertiesService.class).to(CatalogCustomPropertiesServiceImpl.class);
    bind(IntegrationService.class).to(IntegrationServiceImpl.class);
    bind(CatalogService.class).to(CatalogServiceImpl.class);
    bind(BulkEntityFieldUpdateService.class).to(BulkEntityFieldUpdateServiceImpl.class);
    bind(ApiEndpointSyncService.class).to(ApiEndpointSyncServiceImpl.class);
    bind(ActionService.class).to(ActionServiceImpl.class);
    bind(KindService.class).to(KindServiceImpl.class);
    bind(io.harness.idp.layout.service.LayoutService.class).to(io.harness.idp.layout.service.LayoutServiceImpl.class);
    bind(CatalogVersionService.class).to(CatalogVersionServiceImpl.class);
    bind(CatalogTableService.class).to(CatalogTableServiceImpl.class);
    bind(CustomKindUdpPublisher.class).to(CustomKindUdpPublisherImpl.class);
    UdpTypeIngestionConfig udpConfig = appConfig.getUdpTypeIngestionConfig() != null
        ? appConfig.getUdpTypeIngestionConfig()
        : new UdpTypeIngestionConfig();
    bind(ExecutorService.class)
        .annotatedWith(Names.named("UdpPublisher"))
        .toInstance(new ManagedExecutorService(Executors.newFixedThreadPool(udpConfig.getPublisherThreadPoolSize(),
            new ThreadFactoryBuilder().setNameFormat("udp-publisher-%d").build())));
    bind(KindToObjectTypeMapper.class).to(DefaultKindToUpgradePackMapper.class);
    bind(KindToEventDerivationConfigMapper.class).to(DefaultKindToEventDerivationConfigMapper.class);
    bind(TypeIngestionRequestAssembler.class).to(DefaultTypeIngestionRequestAssembler.class);
    bind(TypeIngestionKafkaClient.class).to(KafkaTypeIngestionKafkaClient.class);
    bind(UdpEntityTypeReader.class).to(SchemaServiceUdpEntityTypeReader.class);
    bind(UdpEventDerivationConfigReader.class).to(ConfigServiceUdpEventDerivationConfigReader.class);
    bind(EventDerivationConfigYamlParser.class).in(com.google.inject.Scopes.SINGLETON);
    bind(DecryptionHelper.class).to(DecryptionHelperViaManager.class);
    bind(SchemaCache.class).to(SchemaInMemoryCache.class);
    bind(AggregationRulesService.class).to(AggregationRulesServiceImpl.class);

    final RedisConfig redisConfig = appConfig.getEventsFrameworkConfiguration().getRedisConfig();
    RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);

    bind(Producer.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.LICENSES_USAGE_REDIS_EVENT_CONSUMER))
        .toInstance(RedisProducer.of(EventsFrameworkConstants.LICENSES_USAGE_REDIS_EVENT_CONSUMER, redissonClient,
            EventsFrameworkConstants.LICENSE_USAGE_EVENT_MAX_TOPIC_SIZE, IDP_SERVICE.getServiceId(),
            redisConfig.getEnvNamespace()));

    try {
      bind(new TypeLiteral<DataStore<StoredCredential>>() {
      }).toInstance(StoredCredential.getDefaultDataStore(new MemoryDataStoreFactory()));
    } catch (IOException e) {
      String msg =
          "Could not initialise GKE access token memory cache. This should not never happen with memory data store.";
      throw new ExplanationException(msg, e);
    }
    bind(Clock.class).toInstance(Clock.systemUTC());
    bind(GcpClient.class).to(GcpClientImpl.class);
  }

  private void registerOutboxEventHandlers() {
    MapBinder<String, OutboxEventHandler> outboxEventHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, OutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_APP_CONFIGS).to(AppConfigEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_CONFIG_ENV_VARIABLES).to(BackstageSecretEnvEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_PROXY_HOST).to(ProxyHostDetailsEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_CATALOG_CONNECTOR).to(CatalogConnectorEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_SCORECARDS).to(ScorecardEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_CHECKS).to(CheckEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_ALLOW_LIST).to(AllowListEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_OAUTH_CONFIG).to(OAuthConfigEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_LAYOUT).to(LayoutEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_BACKSTAGE_CATALOG_ENTITY).to(BackstageCatalogEntityEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_PERMISSIONS).to(PermissionsEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_PLUGINS).to(PluginsEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_BACKSTAGE_SCAFFOLDER_TASK).to(BackstageScaffolderTaskEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_CATALOG_CUSTOM_PROPERTIES).to(CatalogCustomPropertiesEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_GIT_INTEGRATIONS).to(GitIntegrationsEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_CATALOG_INTEGRATIONS).to(CatalogIntegrationsEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_HOMEPAGE_LAYOUT).to(HomePageLayoutEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_PERSONA_VIEW).to(PersonaViewEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_GROUPS).to(GroupEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_CATALOG).to(IDPCatalogEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_CATALOG_VERSION).to(IDPCatalogEntityVersionEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_CATALOG_DECORATOR).to(IDPCatalogDecoratorEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_WORKFLOW).to(IDPWorkflowEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_ACTION).to(IDPActionEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_ENTITY_LINK).to(IDPEntityLinkEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_CATALOG_TABLE).to(IDPCatalogTableEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_ENVIRONMENT).to(IDPEnvironmentEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_ENVIRONMENT_BLUEPRINT).to(IDPEnvironmentBlueprintEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_AGGREGATION_RULE).to(AggregationRuleEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_KIND).to(IDPKindEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IDP_TEAM).to(IDPTeamEventHandler.class);
  }

  @Provides
  @Singleton
  public MongoConfig mongoConfig() {
    return appConfig.getMongoConfig();
  }

  @Provides
  @Singleton
  @Named("mongoReplacementConfig")
  public HashMap<String, String> mongoReplacementConfig() {
    return appConfig.getMongoReplacementConfig();
  }

  @Provides
  @Singleton
  public boolean isOpaConnectivityEnabled() {
    return appConfig.isOpaConnectivityEnabled();
  }

  @Provides
  @Singleton
  @Named("workflowLibraryConfig")
  public WorkflowLibraryConfig workflowLibraryConfig() {
    return appConfig.getWorkflowLibraryConfig();
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisConfig() {
    return appConfig.getRedisLockConfig();
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return appConfig.getDistributedLockImplementation() == null ? REDIS : appConfig.getDistributedLockImplementation();
  }

  @Provides
  @Singleton
  @Named("onboardingModuleConfig")
  public OnboardingModuleConfig onboardingModuleConfig() {
    return this.appConfig.getOnboardingModuleConfig();
  }

  @Provides
  @Singleton
  @Named("onboardingModuleV2Config")
  public OnboardingModuleV2Config onboardingModuleV2Config() {
    return this.appConfig.getOnboardingModuleV2Config();
  }

  @Provides
  @Singleton
  @Named("backstageEntitiesFetchLimit")
  public String backstageEntitiesFetchLimit() {
    return this.appConfig.getBackstageEntitiesFetchLimit();
  }

  @Provides
  @Singleton
  @Named(PROVISION_MODULE_CONFIG)
  public ProvisionModuleConfig provisionModuleConfig() {
    return this.appConfig.getProvisionModuleConfig();
  }

  @Provides
  @Singleton
  @Named("backstageServiceSecret")
  public String backstageServiceSecret() {
    return this.appConfig.getBackstageServiceSecret();
  }

  @Provides
  @Singleton
  @Named("ngManagerServiceHttpClientConfig")
  public ServiceHttpClientConfig ngManagerServiceHttpClientConfig() {
    return this.appConfig.getNgManagerServiceHttpClientConfig();
  }
  @Provides
  @Singleton
  @Named("ngManagerServiceSecret")
  public String ngManagerServiceSecret() {
    return this.appConfig.getNgManagerServiceSecret();
  }

  @Provides
  @Singleton
  @Named("managerClientConfig")
  public ServiceHttpClientConfig managerClientConfig() {
    return this.appConfig.getManagerClientConfig();
  }

  @Provides
  @Singleton
  @Named("managerServiceSecret")
  public String managerServiceSecret() {
    return this.appConfig.getManagerServiceSecret();
  }

  @Provides
  @Singleton
  @Named("env")
  public String env() {
    return this.appConfig.getEnv();
  }

  @Provides
  @Singleton
  @Named("base")
  public String base() {
    return this.appConfig.getBase();
  }

  @Provides
  @Singleton
  @Named("proxyEndPointEnv")
  public String proxyEndPointEnv() {
    return this.appConfig.getProxyEndPointEnv();
  }

  @Provides
  @Singleton
  @Named("devSpaceDefaultBackstageNamespace")
  public String devSpaceDefaultBackstageNamespace() {
    return this.appConfig.getDevSpaceDefaultBackstageNamespace();
  }

  @Provides
  @Singleton
  @Named("devSpaceDefaultAccountId")
  public String devSpaceDefaultAccountId() {
    return this.appConfig.getDevSpaceDefaultAccountId();
  }

  @Provides
  @Singleton
  Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
      DelegateServiceGrpcClient delegateServiceGrpcClient) {
    return Suppliers.memoize(() -> getDelegateCallbackToken(delegateServiceGrpcClient));
  }
  @Provides
  @Singleton
  @Named("backstageAppBaseUrl")
  public String appBaseUrl() {
    return this.appConfig.getBackstageAppBaseUrl();
  }
  @Provides
  @Singleton
  @Named("backstagePostgresHost")
  public String postgresHost() {
    return this.appConfig.getBackstagePostgresHost();
  }

  @Provides
  @Singleton
  @Named("idpEncryptionSecret")
  public String idpEncryptionSecret() {
    return this.appConfig.getIdpEncryptionSecret();
  }

  private DelegateCallbackToken getDelegateCallbackToken(DelegateServiceGrpcClient delegateServiceClient) {
    log.info("Generating Delegate callback token");
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("idp")
                                  .setConnection(appConfig.getMongoConfig().getUri())
                                  .build())
            .setNewCallbackFlow(appConfig.isEnableWaitNotifyEngineOptimisation())
            .build());
    log.info("delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  @Provides
  @Singleton
  @Named("logServiceConfig")
  public LogServiceConfig logServiceConfig() {
    return this.appConfig.getLogServiceConfig();
  }

  @Provides
  @Singleton
  LogStreamingServiceConfiguration logStreamingServiceConfiguration() {
    // Bind the typed logStreamingServiceConfig directly so callers see the operator-supplied
    // baseUrl, serviceToken, and delayToClosePrefixLogStreamInSeconds. Defaults from the config
    // class apply when no value is set.
    LogStreamingServiceConfiguration cfg = appConfig.getLogStreamingServiceConfig();
    return cfg != null ? cfg : LogStreamingServiceConfiguration.builder().baseUrl("").build();
  }

  @Provides
  @Singleton
  @Named("logStreamingDelayExecutor")
  public ScheduledExecutorService logStreamingDelayExecutor() {
    ThreadPoolConfig poolConfig = ThreadPoolConfig.builder().corePoolSize(10).build();
    return new ScheduledThreadPoolExecutor(poolConfig.getCorePoolSize(),
        new ThreadFactoryBuilder().setNameFormat("idp-log-client-pool-%d").setPriority(Thread.NORM_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("sscaServiceConfig")
  public SSCAServiceConfig sscaServiceConfig() {
    return this.appConfig.getSscaServiceConfig();
  }

  @Provides
  @Singleton
  @Named("stoServiceConfig")
  public STOServiceConfig stoServiceConfig() {
    return this.appConfig.getStoServiceConfig();
  }

  @Provides
  @Singleton
  @Named("ngBaseUrl")
  String getNgBaseUrl() {
    String apiUrl = appConfig.getApiUrl();
    if (apiUrl.endsWith("/")) {
      return apiUrl.substring(0, apiUrl.length() - 1);
    }
    return apiUrl;
  }

  @Provides
  @Named(SECRET_CACHE_KEY)
  Cache<String, EncryptedDataDetails> getSecretTokenCache() {
    return new NoOpCache<>();
  }

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    IdpApplication.configureObjectMapper(objectMapper);
    return objectMapper;
  }
  @Provides
  @Singleton
  List<YamlSchemaRootClass> yamlSchemaRootClasses() {
    return ImmutableList.<YamlSchemaRootClass>builder().build();
  }

  @Provides
  @Singleton
  @Named("GitAwareEntityHelperExecutorService")
  public ExecutorService gitAwareEntityHelperExecutorService() {
    return ThreadPool.getInstrumentedExecutorService(this.appConfig.getGitAwareEntityHelperPoolConfig(),
        "GitAwareEntityHelperExecutorService", new MetricRegistry());
  }

  @Provides
  @Singleton
  @Named("ScopeProjectValidator")
  public ExecutorService scopeProjectValidatorExecutorService() {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 20, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(16),
        new ThreadFactoryBuilder().setNameFormat("scope-project-validator-%d").build(),
        new ThreadPoolExecutor.CallerRunsPolicy());
    return new ManagedExecutorService(executor);
  }

  @Provides
  @Singleton
  @Named("EntitiesGroupExecutor")
  public ExecutorService entitiesGroupExecutorService() {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(3, 10, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(30),
        new ThreadFactoryBuilder().setNameFormat("entities-group-executor-%d").build(),
        new ThreadPoolExecutor.CallerRunsPolicy());
    return new ManagedExecutorService(executor);
  }

  @Provides
  @Singleton
  @Named("ScorecardSummaryExecutor")
  public ExecutorService scorecardSummaryExecutorService() {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 20, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(64),
        new ThreadFactoryBuilder().setNameFormat("scorecard-summary-%d").build(),
        new ThreadPoolExecutor.CallerRunsPolicy());
    return new ManagedExecutorService(executor);
  }

  @Provides
  @Singleton
  @Named("RbacBatchExecutor")
  public ExecutorService rbacBatchExecutorService(MetricRegistry metricRegistry) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 16, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(32),
        new ThreadFactoryBuilder().setNameFormat("rbac-batch-executor-%d").build(),
        new ThreadPoolExecutor.CallerRunsPolicy());
    metricRegistry.register(
        MetricRegistry.name("RbacBatchExecutor", "tasks.queued.count"), (Gauge<Integer>) executor.getQueue()::size);
    return new ManagedExecutorService(new InstrumentedExecutorService(executor, metricRegistry, "RbacBatchExecutor"));
  }

  @Provides
  @Named("yaml-schema-subtypes")
  @Singleton
  public Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes() {
    Reflections reflections = new Reflections(HarnessPackages.IO_HARNESS);

    Set<Class<? extends StepSpecType>> subTypesOfStepSpecType = reflections.getSubTypesOf(StepSpecType.class);
    Set<Class<?>> set = new HashSet<>(subTypesOfStepSpecType);

    return ImmutableMap.of(StepSpecType.class, set);
  }

  @Provides
  @Singleton
  @Named("pmsSdkExecutionPoolConfig")
  public ThreadPoolConfig pmsSdkExecutionPoolConfig() {
    return this.appConfig.getPmsSdkExecutionPoolConfig();
  }

  @Provides
  @Singleton
  @Named("pmsSdkOrchestrationEventPoolConfig")
  public ThreadPoolConfig pmsSdkOrchestrationEventPoolConfig() {
    return this.appConfig.getPmsSdkOrchestrationEventPoolConfig();
  }

  @Provides
  @Singleton
  @Named("pmsPlanCreatorServicePoolConfig")
  public ThreadPoolConfig pmsPlanCreatorServicePoolConfig() {
    return this.appConfig.getPmsPlanCreatorServicePoolConfig();
  }

  @Provides
  @Singleton
  @Named("opaClientConfig")
  public ServiceHttpClientConfig opaClientConfig() {
    return this.appConfig.getOpaClientConfig();
  }

  @Provides
  @Singleton
  @Named("policyManagerSecret")
  public String policyManagerSecret() {
    return this.appConfig.getPolicyManagerSecret();
  }

  @Provides
  @Singleton
  @Named("ciExecutionServiceConfig")
  public CIExecutionServiceConfig ciExecutionServiceConfig() {
    return this.appConfig.getCiExecutionServiceConfig();
  }

  @Provides
  @Singleton
  @Named("segmentConfiguration")
  public SegmentConfiguration segmentConfiguration() {
    return this.appConfig.getSegmentConfiguration();
  }

  @Provides
  @Singleton
  @Named("iacmServiceConfig")
  public IACMServiceConfig iacmServiceConfig() {
    return this.appConfig.getIacmServiceConfig();
  }

  @Provides
  @Singleton
  @Named("poServerConfig")
  public POServerConfig poServerConfig() {
    return this.appConfig.getPoServerConfig();
  }

  @Provides
  @Singleton
  @Named("enforcementClientConfiguration")
  public EnforcementClientConfiguration enforcementClientConfiguration() {
    return this.appConfig.getEnforcementClientConfiguration();
  }

  @Provides
  @Singleton
  public AsyncWaitEngine asyncWaitEngine(WaitNotifyEngine waitNotifyEngine) {
    return new AsyncWaitEngineImpl(waitNotifyEngine, NG_ORCHESTRATION);
  }

  @Provides
  @Singleton
  @Named("backstageHttpClientConfig")
  public ServiceHttpClientConfig backstageHttpClientConfig() {
    return this.appConfig.getBackstageHttpClientConfig();
  }

  @Provides
  @Singleton
  @Named("proxyAllowList")
  public ProxyAllowListConfig proxyAllowList() {
    return this.appConfig.getProxyAllowList();
  }

  @Provides
  @Singleton
  public CdcKafkaConfig getCdcKafkaConfig() {
    return Optional.ofNullable(this.appConfig)
        .map(IdpConfiguration::getCdcKafkaConfig)
        .orElse(CdcKafkaConfig.defaultConfig());
  }

  @Provides
  @Singleton
  @Named("notificationConfigs")
  public HashMap<String, String> notificationConfigs() {
    return this.appConfig.getNotificationConfigs();
  }

  @Provides
  @Singleton
  @Named("pipelineServiceClientConfigs")
  public ServiceHttpClientConfig pipelineServiceConfiguration() {
    return this.appConfig.getPipelineServiceConfiguration();
  }

  @Provides
  @Singleton
  @Named("tiServiceConfig")
  public TIServiceConfig tiServiceConfig() {
    return this.appConfig.getTiServiceConfig();
  }

  @Provides
  @Singleton
  @Named("iteratorsConfig")
  public IteratorsConfig iteratorsConfig() {
    return this.appConfig.getIteratorsConfig();
  }

  @Provides
  @Singleton
  @Named("idpServiceSecret")
  public String idpServiceSecret() {
    return this.appConfig.getIdpServiceSecret();
  }

  @Provides
  @Singleton
  @Named("idpAutomationGitHubToken")
  public String idpAutomationGitHubToken() {
    return this.appConfig.getIdpAutomationGitHubToken();
  }

  @Provides
  @Singleton
  @Named("idpAutomationXApiKey")
  public String idpAutomationXApiKey() {
    return this.appConfig.getIdpAutomationXApiKey();
  }

  @Provides
  @Singleton
  @Named("auditClientConfig")
  public ServiceHttpClientConfig auditClientConfig() {
    return this.appConfig.getAuditClientConfig();
  }

  @Provides
  @Singleton
  @Named("enableAudit")
  public Boolean enableAudit() {
    return this.appConfig.isEnableAudit();
  }

  @Provides
  @Singleton
  @Named("internalAccounts")
  public List<String> internalAccounts() {
    return this.appConfig.getInternalAccounts();
  }

  @Provides
  @Singleton
  @Named("enableMetrics")
  public Boolean enableMetrics() {
    return this.appConfig.isEnableMetrics();
  }

  @Provides
  @Singleton
  @Named("enableAPIMetrics")
  public Boolean enableAPIMetrics() {
    return this.appConfig.isEnableAPIMetrics();
  }

  @Provides
  @Singleton
  @Named("harnessCodeGitBaseUrl")
  String getHarnessCodeGitBaseUrl() {
    String gitUrl = this.appConfig.getHarnessCodeGitUrl();
    if (gitUrl.endsWith("/")) {
      return gitUrl.substring(0, gitUrl.length() - 1);
    }
    return gitUrl;
  }

  @Provides
  @Singleton
  @Named("allowedKindsForCatalogSync")
  public List<String> allowedKindsForCatalogSync() {
    return this.appConfig.getAllowedKindsForCatalogSync();
  }

  @Provides
  @Singleton
  @Named("allowedKindsForAudit")
  public List<String> allowedKindsForAudit() {
    return this.appConfig.getAllowedKindsForAudit();
  }

  @Provides
  @Singleton
  @Named("customPlugins")
  public CustomPluginsConfig customPluginsConfig() {
    return this.appConfig.getCustomPluginsConfig();
  }

  @Provides
  @Singleton
  public CatalogContentConfig catalogContentConfig() {
    CatalogContentConfig base = this.appConfig.getCatalogContentConfig();
    return CatalogContentConfig.builder().bucketName(base.getBucketName()).env(this.appConfig.getEnv()).build();
  }

  @Provides
  @Singleton
  @Named("contentEncryption")
  public IdpContentEncryptionConfig contentEncryptionConfig() {
    return this.appConfig.getContentEncryptionConfig();
  }

  @Provides
  @Singleton
  @Named("PSQLExecuteListener")
  ExecuteListener executeListener() {
    return HExecuteListener.getInstance();
  }

  @Provides
  @Singleton
  @Named("backstageHttpClientConnectionPoolConfig")
  public OkHttpClientConnectionPoolConfig backstageHttpClientConnectionPoolConfig() {
    return this.appConfig.getOkHttpClientConnectionPoolConfigs().get("backstageHttpClient");
  }

  @Provides
  @Singleton
  @Named("directDslClientHttpClientConnectionPoolConfig")
  public OkHttpClientConnectionPoolConfig directDslClientHttpClientConnectionPoolConfig() {
    return this.appConfig.getOkHttpClientConnectionPoolConfigs().get("directDslClientHttpClient");
  }

  @Provides
  @Singleton
  @Named("proxyApiManagerHttpClientConnectionPoolConfig")
  public OkHttpClientConnectionPoolConfig proxyApiManagerHttpClientConnectionPoolConfig() {
    return this.appConfig.getOkHttpClientConnectionPoolConfigs().get("proxyApiManagerHttpClient");
  }

  @Provides
  @Singleton
  @Named("proxyApiNgManagerHttpClientConnectionPoolConfig")
  public OkHttpClientConnectionPoolConfig proxyApiNgManagerHttpClientConnectionPoolConfig() {
    return this.appConfig.getOkHttpClientConnectionPoolConfigs().get("proxyApiNgManagerHttpClient");
  }

  @Provides
  @Singleton
  @Named("idpAppConfig")
  public IdpAppConfig idpAppConfig() {
    return this.appConfig.getIdpAppConfig();
  }

  @Provides
  @Singleton
  public UdpTypeIngestionConfig udpTypeIngestionConfig() {
    return this.appConfig.getUdpTypeIngestionConfig() != null ? this.appConfig.getUdpTypeIngestionConfig()
                                                              : new UdpTypeIngestionConfig();
  }

  @Provides
  @Singleton
  @Nullable
  public SchemaServiceGrpc.SchemaServiceBlockingV2Stub schemaServiceBlockingV2Stub(
      UdpTypeIngestionConfig udpTypeIngestionConfig) {
    io.harness.grpc.client.GrpcClientConfig grpcClientConfig =
        udpTypeIngestionConfig.getSchemaServiceGrpcClientConfig();
    if (grpcClientConfig == null || grpcClientConfig.getTarget() == null || grpcClientConfig.getTarget().isEmpty()) {
      log.info("{} module schema grpc stub disabled configPresent={}", UdpEventDerivationConstants.LOG_PREFIX,
          grpcClientConfig != null);
      return null;
    }
    log.info("{} module schema grpc stub enabled target={} authority={}", UdpEventDerivationConstants.LOG_PREFIX,
        grpcClientConfig.getTarget(), grpcClientConfig.getAuthority());
    return SchemaServiceGrpc.newBlockingV2Stub(grpcClientConfig.getChannelBuilder().build());
  }

  @Provides
  @Singleton
  @Nullable
  public ConfigServiceGrpc.ConfigServiceBlockingV2Stub configServiceBlockingV2Stub(
      UdpTypeIngestionConfig udpTypeIngestionConfig) {
    io.harness.grpc.client.GrpcClientConfig grpcClientConfig =
        udpTypeIngestionConfig.getConfigServiceGrpcClientConfig();
    if (grpcClientConfig == null || grpcClientConfig.getTarget() == null || grpcClientConfig.getTarget().isEmpty()) {
      log.info("{} module config grpc stub disabled configPresent={}", UdpEventDerivationConstants.LOG_PREFIX,
          grpcClientConfig != null);
      return null;
    }
    log.info("{} module config grpc stub enabled target={} authority={}", UdpEventDerivationConstants.LOG_PREFIX,
        grpcClientConfig.getTarget(), grpcClientConfig.getAuthority());
    return ConfigServiceGrpc.newBlockingV2Stub(grpcClientConfig.getChannelBuilder().build());
  }

  @Provides
  @Singleton
  @Named("dslClientConfig")
  public DslClientConfig dslClientConfig() {
    return this.appConfig.getDslClientConfig();
  }

  @Provides
  @Singleton
  @Named("deploymentType")
  public String deploymentType() {
    return this.appConfig.getDeploymentType();
  }

  @Provides
  @Singleton
  @Named("deploymentNamespace")
  public String deploymentNamespace() {
    return this.appConfig.getDeploymentNamespace();
  }

  @Provides
  @Singleton
  @Named("dynamicConfigResolution")
  public boolean dynamicConfigResolution() {
    return this.appConfig.isDynamicConfigResolution();
  }

  @Provides
  @Singleton
  @Named("harnessCodeRepoConfig")
  public HarnessCodeRepoConfig harnessCodeRepoConfig() {
    return this.appConfig.getHarnessCodeRepoConfig();
  }

  @Provides
  @Singleton
  @Named("homePageCardIconConfig")
  public HomePageCardIconConfig homePageCardIconConfig() {
    return this.appConfig.getHomePageCardIconConfig();
  }

  @Provides
  @Singleton
  @Named("defaultTierGroupConfig")
  public DefaultTierGroupConfig defaultTierGroupConfig() {
    return this.appConfig.getDefaultTierGroupConfig();
  }

  @Provides
  @Singleton
  @Named("tierIconConfig")
  public TierIconConfig tierIconConfig() {
    return this.appConfig.getTierIconConfig();
  }

  @Provides
  @Singleton
  CloudStorageUtil cloudStorageUtil(@Named("homePageCardIconConfig") HomePageCardIconConfig config) {
    if ("S3".equalsIgnoreCase(config.getStorageType())) {
      return new S3StorageUtil(config.getS3Region());
    }
    return new GcpStorageUtil();
  }

  @Provides
  @Singleton
  @Named("harnessArtifactRegistryUrl")
  public String harnessArtifactRegistryUrl() {
    String registryUrl =
        this.appConfig.getCiExecutionServiceConfig().getHarnessRegistryConfig().getHttpClientConfig().getBaseUrl();
    if (registryUrl.endsWith("/")) {
      return registryUrl.substring(0, registryUrl.length() - 1);
    }
    return registryUrl;
  }

  @Provides
  @Singleton
  @Named("enableQueue")
  public boolean enableQueue() {
    return this.appConfig.getEnableQueue();
  }

  @Provides
  @Singleton
  @Named("gcsForTechDocsDelegate")
  public boolean gcsForTechDocsDelegate() {
    return this.appConfig.isGcsForTechDocsDelegate();
  }

  @Provides
  @Singleton
  @Named("harnessCiCdAnnotationsServiceUrl")
  public String harnessCiCdAnnotationsServiceUrl() {
    return this.appConfig.getHarnessCiCdAnnotationsServiceUrl();
  }

  @Provides
  @Singleton
  @Named("idpAgentHttpClientConfig")
  public ServiceHttpClientConfig idpAgentHttpClientConfig() {
    return this.appConfig.getIdpAgentHttpClientConfig();
  }

  @Provides
  @Singleton
  @Named("idpAgentHttpClientConnectionPoolConfig")
  public OkHttpClientConnectionPoolConfig idpAgentHttpClientConnectionPoolConfig() {
    return this.appConfig.getOkHttpClientConnectionPoolConfigs().get("idpAgentHttpClient");
  }

  @Provides
  @Singleton
  public CoverageServiceConfig coverageServiceConfig() {
    return this.appConfig.getCoverageServiceConfig();
  }

  @Provides
  @Singleton
  @Named("integrationManagerClientConfig")
  public ServiceHttpClientConfig integrationManagerClientConfig() {
    return this.appConfig.getIntegrationManagerClientConfig();
  }

  @Provides
  @Singleton
  @Named("integrationManagerSecret")
  public String integrationManagerSecret() {
    return this.appConfig.getIntegrationManagerSecret();
  }

  @Provides
  @Singleton
  @Named("integrationManagerIdpMappingId")
  public String integrationManagerIdpMappingId() {
    return this.appConfig.getIntegrationManagerIdpMappingId();
  }

  @Provides
  @Singleton
  @Named("integrationsHarnessCiCdAnnotationsServiceUrl")
  public String integrationsHarnessCiCdAnnotationsServiceUrl() {
    return this.appConfig.getIntegrationsHarnessCiCdAnnotationsServiceUrl();
  }
}

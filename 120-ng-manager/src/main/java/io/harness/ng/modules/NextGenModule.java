/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.modules;

import static io.harness.NGConstants.NG_LDAP_EXECUTOR;
import static io.harness.audit.ResourceTypeConstants.API_KEY;
import static io.harness.audit.ResourceTypeConstants.BANNER;
import static io.harness.audit.ResourceTypeConstants.BRANDING_ASSET;
import static io.harness.audit.ResourceTypeConstants.BRANDING_SETTINGS;
import static io.harness.audit.ResourceTypeConstants.CERTIFICATE;
import static io.harness.audit.ResourceTypeConstants.CONNECTOR;
import static io.harness.audit.ResourceTypeConstants.DELEGATE_CONFIGURATION;
import static io.harness.audit.ResourceTypeConstants.DEPLOYMENT_FREEZE;
import static io.harness.audit.ResourceTypeConstants.ENVIRONMENT;
import static io.harness.audit.ResourceTypeConstants.ENVIRONMENT_GROUP;
import static io.harness.audit.ResourceTypeConstants.EULA;
import static io.harness.audit.ResourceTypeConstants.FILE;
import static io.harness.audit.ResourceTypeConstants.GITX_WEBHOOK;
import static io.harness.audit.ResourceTypeConstants.IP_ALLOWLIST_CONFIG;
import static io.harness.audit.ResourceTypeConstants.MODULE_LICENSE;
import static io.harness.audit.ResourceTypeConstants.ORGANIZATION;
import static io.harness.audit.ResourceTypeConstants.PIPELINE;
import static io.harness.audit.ResourceTypeConstants.PROJECT;
import static io.harness.audit.ResourceTypeConstants.SECRET;
import static io.harness.audit.ResourceTypeConstants.SERVICE;
import static io.harness.audit.ResourceTypeConstants.SERVICE_ACCOUNT;
import static io.harness.audit.ResourceTypeConstants.SETTING;
import static io.harness.audit.ResourceTypeConstants.TOKEN;
import static io.harness.audit.ResourceTypeConstants.USER;
import static io.harness.audit.ResourceTypeConstants.VARIABLE;
import static io.harness.authorization.AuthorizationServiceHeader.CHAOS_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.IRO_MANAGER_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.LOAD_TEST_MANAGER_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.MONITORING_MANAGER_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.authorization.AuthorizationServiceHeader.SERVICE_DISCOVERY_SERVICE;
import static io.harness.enforcement.beans.event.PlatformLimitThresholdEvent.PLATFORM_LIMIT;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.INSTANCE_STATS;
import static io.harness.eventsframework.EventsFrameworkConstants.SETUP_USAGE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.AZURE_ARM_CONFIG_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CD_ACCOUNT_EXECUTION_METADATA;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CLOUDFORMATION_CONFIG_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CONNECTOR_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DEFAULT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DEPLOYMENT_ACCOUNTS;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DEPLOYMENT_SUMMARY_NG;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DRIFT_DETECTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENVIRONMENT_GROUP_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.INSTANCE_DEPLOYMENT_INFO;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.INSTANCE_NG;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.INSTANCE_SYNC;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SECRET_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SERVICEACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.TEMPLATE_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.TERRAFORM_CONFIG_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.TERRAGRUNT_CONFIG_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_SCOPE_RECONCILIATION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.VARIABLE_ENTITY;
import static io.harness.lock.DistributedLockImplementation.REDIS;
import static io.harness.ng.core.api.utils.JWTTokenFlowAuthFilterUtils.JWT_TOKEN_PUBLIC_KEYS_JSON_DATA_CACHE_KEY;
import static io.harness.ng.core.api.utils.JWTTokenFlowAuthFilterUtils.JWT_TOKEN_SCIM_SETTINGS_DATA_CACHE_KEY;
import static io.harness.ng.core.api.utils.JWTTokenFlowAuthFilterUtils.JWT_TOKEN_SERVICE_ACCOUNT_DATA_CACHE_KEY;
import static io.harness.ng.core.services.ScopeInfoService.SCOPE_INFO_UNIQUE_ID_CACHE_KEY;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import static java.lang.Boolean.TRUE;

import io.harness.AccessControlClientModule;
import io.harness.CertificateModule;
import io.harness.FreezeOutboxEventHandler;
import io.harness.GitopsModule;
import io.harness.Microservice;
import io.harness.NgIteratorsConfig;
import io.harness.PluginConfiguration;
import io.harness.PluginModule;
import io.harness.accesscontrol.AccessControlAdminClientConfiguration;
import io.harness.accesscontrol.AccessControlAdminClientModule;
import io.harness.accesscontrol.migration.AccessControlMigrationModule;
import io.harness.account.AbstractAccountModule;
import io.harness.account.AccountClientModule;
import io.harness.account.AccountConfig;
import io.harness.accountresourceng.AccountResourceNGClientModule;
import io.harness.aitestautomation.client.AiTestAutomationClientModule;
import io.harness.aitestautomation.client.AitGcpFeatureFlagChecker;
import io.harness.aitestautomation.service.AiTestAutomationService;
import io.harness.aitestautomation.service.AiTestAutomationServiceImpl;
import io.harness.alloydb.AlloyDBConfig;
import io.harness.alloydb.AlloyDBService;
import io.harness.alloydb.AlloyDBServiceImpl;
import io.harness.annotations.AutoCleanupConfig;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.aws.retrypolicy.AwsSdkDefaultBackOffStrategyConfiguration;
import io.harness.beans.FeatureName;
import io.harness.beans.HarnessCodeServiceConfig;
import io.harness.beans.ScopeInfo;
import io.harness.branding.outbox.branding.handler.BrandingEventHandler;
import io.harness.branding.outbox.brandingasset.handler.BrandingAssetEventHandler;
import io.harness.cache.HarnessCacheManager;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.ccm.license.remote.CeLicenseClientModule;
import io.harness.cd.CDEventInterceptor;
import io.harness.cd.license.CdLicenseUsageCgModule;
import io.harness.cdng.NGModule;
import io.harness.cdng.NGModuleConfig;
import io.harness.cdng.bamboo.BambooBuildStepHelperService;
import io.harness.cdng.bamboo.BambooBuildStepHelperServiceImpl;
import io.harness.cdng.customDeployment.eventlistener.CustomDeploymentEntityCRUDStreamEventListener;
import io.harness.cdng.fileservice.FileServiceClient;
import io.harness.cdng.fileservice.FileServiceClientFactory;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStepHelperService;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStepHelperServiceImpl;
import io.harness.cdng.oidc.CDEventInterceptorDirectImpl;
import io.harness.cdng.stage.resources.CDNGStageSummaryResource;
import io.harness.cdng.stage.resources.CDNGStageSummaryResourceImpl;
import io.harness.ci.buildstate.SecretDecryptorViaNg;
import io.harness.client.DelegateSelectionLogHttpClientModule;
import io.harness.clients.IdpResourceClientModule;
import io.harness.code.CodeResourceClientModule;
import io.harness.configuration.CgiTaskConfig;
import io.harness.configuration.TaskBinaryConfig;
import io.harness.connector.ConnectorModule;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.connector.events.ConnectorEventHandler;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.helper.DecryptionHelperViaManager;
import io.harness.connector.oidc.ConnectorOidcModule;
import io.harness.cvng.client.HealthSourceResourceClientModule;
import io.harness.delay.DelayEvent;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.driftdetection.listener.DriftDetectionEntityCrudStreamListener;
import io.harness.ds.remote.AuthDSEventHandler;
import io.harness.ds.remote.DSEventConstants;
import io.harness.ds.remote.DirectoryServiceResourceClientModule;
import io.harness.enforcement.EnforcementModule;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.enforcement.outbox.PlatformLimitThresholdEventHandler;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.environment.EnvironmentResourceClientModule;
import io.harness.eula.outbox.EulaEventHandler;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.exception.GeneralException;
import io.harness.exception.exceptionmanager.ExceptionModule;
import io.harness.exception.exceptionmanager.exceptionhandler.CCMConnectorExceptionHandler;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.ff.FeatureFlagModule;
import io.harness.file.NGFileServiceModule;
import io.harness.filestore.NgFileStoreModule;
import io.harness.filestore.events.listener.FileEntityCRUDStreamListener;
import io.harness.filestore.outbox.FileEventHandler;
import io.harness.filter.FilterType;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.fme.FmeClientModule;
import io.harness.freeze.service.FreezeCRUDService;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.freeze.service.FreezeSchemaService;
import io.harness.freeze.service.FrozenExecutionService;
import io.harness.freeze.service.impl.FreezeCRUDServiceImpl;
import io.harness.freeze.service.impl.FreezeEvaluateServiceImpl;
import io.harness.freeze.service.impl.FreezeSchemaServiceImpl;
import io.harness.freeze.service.impl.FrozenExecutionServiceImpl;
import io.harness.gitops.GitopsResourceClientModule;
import io.harness.gitsync.GitSyncConfigClientModule;
import io.harness.gitsync.GitSyncModule;
import io.harness.gitsync.common.events.FullSyncMessageListener;
import io.harness.gitsync.common.events.GitSyncProjectCleanup;
import io.harness.gitsync.common.impl.GitSyncConnectorServiceImpl;
import io.harness.gitsync.common.service.GitSyncConnectorService;
import io.harness.gitsync.configurations.GitServiceConfiguration;
import io.harness.gitsync.constants.GitSyncModuleConstants;
import io.harness.gitsync.core.webhook.createbranchevent.GitBranchHookEventStreamListener;
import io.harness.gitsync.core.webhook.pushevent.GitPushEventStreamListener;
import io.harness.gitsync.gitxwebhooks.listener.GitXWebhookBranchEventListener;
import io.harness.gitsync.gitxwebhooks.listener.GitXWebhookPullRequestEventListener;
import io.harness.gitsync.gitxwebhooks.listener.GitXWebhookPushEventListener;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitBranchDeleteObserver;
import io.harness.govern.ProviderModule;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.harclient.HarnessArtifactRegistryClientModule;
import io.harness.harnessid.client.HarnessIdClientModule;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.iacmserviceclient.IACMServiceClientModule;
import io.harness.infrastructure.InfrastructureResourceClientModule;
import io.harness.kafka.KafkaModule;
import io.harness.licensing.event.listener.ModuleLicenseEventListener;
import io.harness.licensing.module.LicenseModule;
import io.harness.licensing.outbox.ModuleLicenseOutboxEventHandler;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.licensing.services.DefaultLicenseServiceImpl;
import io.harness.licensing.services.LicenseScopeService;
import io.harness.listeners.InstanceSyncEntityCrudStreamListener;
import io.harness.loadtest.client.remote.LoadTestClientModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.logstreaming.NGLogStreamingClientFactory;
import io.harness.manage.ManagedExecutorService;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.migration.timescale.NGNoopTimeScaleMigration;
import io.harness.modules.DevOpsEssentialsModule;
import io.harness.modules.ModulesClientModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.monitoredservice.MonitoredServiceResourceClientModule;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.BaseUrls;
import io.harness.ng.accesscontrol.user.AggregateUserService;
import io.harness.ng.accesscontrol.user.AggregateUserServiceImpl;
import io.harness.ng.aitestautomation.AiTestAutomationCallbackService;
import io.harness.ng.aitestautomation.AiTestAutomationCallbackServiceImpl;
import io.harness.ng.aitestautomation.AiTestAutomationPlaywrightCallbackService;
import io.harness.ng.aitestautomation.AiTestAutomationPlaywrightCallbackServiceImpl;
import io.harness.ng.authenticationsettings.AuthenticationSettingsModule;
import io.harness.ng.chaos.AbstractChaosModule;
import io.harness.ng.code.impl.HarnessCodeServiceImpl;
import io.harness.ng.code.services.HarnessCodeService;
import io.harness.ng.config.AutoProvisionLicenseConfig;
import io.harness.ng.config.CiDefaultEntityConfiguration;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.config.ScopedPermissionsBackfillConfig;
import io.harness.ng.config.ServiceUniqueIdBackfillConfig;
import io.harness.ng.config.SmpConfig;
import io.harness.ng.config.TokenExpirationConfig;
import io.harness.ng.config.TokenExpiryAlertIteratorConfig;
import io.harness.ng.core.AccountOrgProjectHelper;
import io.harness.ng.core.AccountOrgProjectHelperImpl;
import io.harness.ng.core.ScopeInfoModule;
import io.harness.ng.core.agent.client.AgentNgManagerCgManagerClientModule;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.DefaultUserGroupScopeService;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.api.NGModulesService;
import io.harness.ng.core.api.PublicKeyRevoker;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.api.cache.JwtTokenPublicKeysJsonData;
import io.harness.ng.core.api.cache.JwtTokenScimAccountSettingsData;
import io.harness.ng.core.api.cache.JwtTokenServiceAccountData;
import io.harness.ng.core.api.impl.ApiKeyServiceImpl;
import io.harness.ng.core.api.impl.CodeApiPublicKeyRevoker;
import io.harness.ng.core.api.impl.DefaultUserGroupServiceImpl;
import io.harness.ng.core.api.impl.NGModulesServiceImpl;
import io.harness.ng.core.api.impl.PublicKeyRevokerFactory;
import io.harness.ng.core.api.impl.TokenServiceImpl;
import io.harness.ng.core.api.impl.UserGroupServiceImpl;
import io.harness.ng.core.api.opa.UserGroupOpaService;
import io.harness.ng.core.beans.GlobalTemplatesConfig;
import io.harness.ng.core.beans.InstanceSyncPerpetualTaskConfig;
import io.harness.ng.core.delegate.client.DelegateNgManagerCgManagerClientModule;
import io.harness.ng.core.encryptors.EncryptorBindingsModule;
import io.harness.ng.core.entityactivity.event.EntityActivityCrudEventMessageListener;
import io.harness.ng.core.entitymetadata.TemplateMetadataBranchDeleteObserver;
import io.harness.ng.core.entitysetupusage.EntitySetupUsageModule;
import io.harness.ng.core.entitysetupusage.event.SetupUsageChangeEventMessageListener;
import io.harness.ng.core.entitysetupusage.event.SetupUsageChangeEventMessageProcessor;
import io.harness.ng.core.event.MessageListener;
import io.harness.ng.core.event.MessageProcessor;
import io.harness.ng.core.event.VariableEntityCRUDStreamListener;
import io.harness.ng.core.event.listener.AccountExecutionMetadataCRUDStreamListener;
import io.harness.ng.core.event.listener.AccountSetupListener;
import io.harness.ng.core.event.listener.ApiKeyEventListener;
import io.harness.ng.core.event.listener.AzureARMConfigEntityCRUDStreamListener;
import io.harness.ng.core.event.listener.CloudformationConfigEntityCRUDStreamListener;
import io.harness.ng.core.event.listener.ConnectorEntityCRUDStreamListener;
import io.harness.ng.core.event.listener.DeploymentAccountsCRUDStreamListener;
import io.harness.ng.core.event.listener.DeploymentSummaryNGCRUDStreamListener;
import io.harness.ng.core.event.listener.EntityCleanupStreamListener;
import io.harness.ng.core.event.listener.EnvironmentGroupEntityCrudStreamListener;
import io.harness.ng.core.event.listener.ExecutionRetentionCleanupListener;
import io.harness.ng.core.event.listener.FilterEventListener;
import io.harness.ng.core.event.listener.FreezeEventListener;
import io.harness.ng.core.event.listener.InstanceDeploymentInfoCRUDStreamListener;
import io.harness.ng.core.event.listener.InstanceNGCRUDStreamListener;
import io.harness.ng.core.event.listener.PerpetualTaskEntityReferenceCRUDStreamListener;
import io.harness.ng.core.event.listener.PollingDocumentEventListener;
import io.harness.ng.core.event.listener.ProjectEntityCRUDStreamListener;
import io.harness.ng.core.event.listener.SecretEntityCRUDStreamListener;
import io.harness.ng.core.event.listener.ServiceAccountEntityCRUDStreamListener;
import io.harness.ng.core.event.listener.SettingsEventListener;
import io.harness.ng.core.event.listener.TerraformConfigEntityCRUDStreamListener;
import io.harness.ng.core.event.listener.TerragruntConfigEntityCRUDStreamListener;
import io.harness.ng.core.event.listener.UserGroupEntityCRUDStreamListener;
import io.harness.ng.core.event.listener.UserMembershipReconciliationMessageProcessor;
import io.harness.ng.core.event.listener.UserMembershipStreamListener;
import io.harness.ng.core.event.listener.ZoomConnectorCRUDStreamListener;
import io.harness.ng.core.event.listener.gitops.AgentCrudStreamListener;
import io.harness.ng.core.event.listener.gitops.ClusterCrudStreamListener;
import io.harness.ng.core.event.modulelicense.ModuleLicenseStreamListener;
import io.harness.ng.core.helpers.NgManagerLogBaseUrlProvider;
import io.harness.ng.core.impl.OrganizationServiceImpl;
import io.harness.ng.core.impl.ProjectServiceImpl;
import io.harness.ng.core.licenseusage.event.LicenseUsageEventMessageListener;
import io.harness.ng.core.migration.DeDuplicateUserGroupsConfig;
import io.harness.ng.core.migration.OrphanUserGroupsCleanupConfig;
import io.harness.ng.core.migration.customdeployment.CustomDeploymentMetadataMigrationService;
import io.harness.ng.core.migration.customdeployment.CustomDeploymentMetadataMigrationServiceImpl;
import io.harness.ng.core.modules.CoreModule;
import io.harness.ng.core.modules.DelegateServiceModule;
import io.harness.ng.core.modules.InviteModule;
import io.harness.ng.core.modules.NGAggregateModule;
import io.harness.ng.core.modules.NGProjectOrgModule;
import io.harness.ng.core.modules.SecretManagementModule;
import io.harness.ng.core.onboarding.OnboardingModule;
import io.harness.ng.core.opa.environment.EnvironmentOpaService;
import io.harness.ng.core.opa.environment.EnvironmentOpaServiceImpl;
import io.harness.ng.core.opa.gitx.EnvironmentOpaStatusHandler;
import io.harness.ng.core.opa.gitx.EnvironmentOpaStatusRepository;
import io.harness.ng.core.opa.gitx.InfrastructureOpaStatusHandler;
import io.harness.ng.core.opa.gitx.InfrastructureOpaStatusRepository;
import io.harness.ng.core.opa.gitx.ServiceOpaStatusHandler;
import io.harness.ng.core.opa.gitx.ServiceOpaStatusRepository;
import io.harness.ng.core.opa.gitx.ServiceOverrideOpaStatusHandler;
import io.harness.ng.core.opa.gitx.ServiceOverrideOpaStatusRepository;
import io.harness.ng.core.opa.infrastructure.InfrastructureOpaService;
import io.harness.ng.core.opa.infrastructure.InfrastructureOpaServiceImpl;
import io.harness.ng.core.opa.override.OverrideOpaService;
import io.harness.ng.core.opa.override.OverrideOpaServiceImpl;
import io.harness.ng.core.opa.service.ServiceOpaService;
import io.harness.ng.core.opa.service.ServiceOpaServiceImpl;
import io.harness.ng.core.outbox.ApiKeyEventHandler;
import io.harness.ng.core.outbox.DelegateProfileEventHandler;
import io.harness.ng.core.outbox.EnvironmentGroupOutboxEventHandler;
import io.harness.ng.core.outbox.EnvironmentOutboxEventHandler;
import io.harness.ng.core.outbox.GitXWebhookOutboxEventHandler;
import io.harness.ng.core.outbox.IPAllowlistConfigEventHandler;
import io.harness.ng.core.outbox.NextGenOutboxEventHandler;
import io.harness.ng.core.outbox.NgCertificateEventHandler;
import io.harness.ng.core.outbox.OrganizationEventHandler;
import io.harness.ng.core.outbox.ProjectEventHandler;
import io.harness.ng.core.outbox.SecretEventHandler;
import io.harness.ng.core.outbox.ServiceAccountEventHandler;
import io.harness.ng.core.outbox.ServiceOutBoxEventHandler;
import io.harness.ng.core.outbox.TokenEventHandler;
import io.harness.ng.core.outbox.UserEventHandler;
import io.harness.ng.core.outbox.UserGroupEventHandler;
import io.harness.ng.core.outbox.VariableEventHandler;
import io.harness.ng.core.perpetualtask.entityreference.PerpetualTaskEntityReferenceServiceImpl;
import io.harness.ng.core.refresh.service.EntityRefreshService;
import io.harness.ng.core.refresh.service.EntityRefreshServiceImpl;
import io.harness.ng.core.services.OrganizationScopeService;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectScopeService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.smtp.NgSMTPSettingsHttpClientModule;
import io.harness.ng.core.smtp.SmtpNgService;
import io.harness.ng.core.smtp.SmtpNgServiceImpl;
import io.harness.ng.core.user.service.LastAdminCheckService;
import io.harness.ng.core.user.service.NgUserScopeService;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.core.user.service.impl.LastAdminCheckServiceImpl;
import io.harness.ng.core.user.service.impl.NgUserServiceImpl;
import io.harness.ng.core.user.service.impl.UserEntityCrudStreamListener;
import io.harness.ng.core.utils.CDGitXService;
import io.harness.ng.core.utils.CDGitXServiceImpl;
import io.harness.ng.eventsframework.EventsFrameworkModule;
import io.harness.ng.feedback.services.FeedbackService;
import io.harness.ng.feedback.services.impls.FeedbackServiceImpl;
import io.harness.ng.filter.SecretFilterPropertiesMapper;
import io.harness.ng.gitops.config.CdcKafkaConfig;
import io.harness.ng.gitops.service.GitOpsExpressionService;
import io.harness.ng.gitops.service.GitOpsExpressionServiceImpl;
import io.harness.ng.iro.AbstractIROManagerModule;
import io.harness.ng.iro.IRODataCollectionTaskService;
import io.harness.ng.iro.IRODataCollectionTaskServiceImpl;
import io.harness.ng.iro.config.IRConfig;
import io.harness.ng.loadtest.LoadTestModule;
import io.harness.ng.moduleversioninfo.ModuleVersionInfoServiceImpl;
import io.harness.ng.moduleversioninfo.service.ModuleVersionInfoService;
import io.harness.ng.monitoringmanager.AbstractMonitoringManagerModule;
import io.harness.ng.opa.entities.apiKey.ApiKeyOpaService;
import io.harness.ng.opa.entities.apiKey.ApiKeyOpaServiceImpl;
import io.harness.ng.opa.entities.serviceaccount.ServiceAccountOpaService;
import io.harness.ng.opa.entities.serviceaccount.ServiceAccountOpaServiceImpl;
import io.harness.ng.opa.entities.token.TokenOpaService;
import io.harness.ng.opa.entities.token.TokenOpaServiceImpl;
import io.harness.ng.opa.entities.usergroup.UserGroupOpaServiceImpl;
import io.harness.ng.opa.entities.variable.VariableOpaService;
import io.harness.ng.opa.entities.variable.VariableOpaServiceImpl;
import io.harness.ng.overview.config.DeploymentCountBQConfig;
import io.harness.ng.overview.service.BigQueryService;
import io.harness.ng.overview.service.BigQueryServiceImpl;
import io.harness.ng.overview.service.CDLandingDashboardService;
import io.harness.ng.overview.service.CDLandingDashboardServiceImpl;
import io.harness.ng.overview.service.CDLandingPageService;
import io.harness.ng.overview.service.CDLandingPageServiceImpl;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.ng.overview.service.CDOverviewDashboardServiceImpl;
import io.harness.ng.privateconnectivity.PrivateConnectivityModule;
import io.harness.ng.rollback.PostProdRollbackService;
import io.harness.ng.rollback.PostProdRollbackServiceImpl;
import io.harness.ng.scim.NGScimGroupServiceImpl;
import io.harness.ng.scim.NGScimUserServiceImpl;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.ng.serviceaccounts.service.impl.ServiceAccountServiceImpl;
import io.harness.ng.servicediscovery.AbstractServiceDiscoveryModule;
import io.harness.ng.sto.StoModule;
import io.harness.ng.support.client.CannyConfig;
import io.harness.ng.tunnel.services.impl.TunnelServiceImpl;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.ng.userprofile.entities.AwsCodeCommitSCM.AwsCodeCommitSCMMapper;
import io.harness.ng.userprofile.entities.AzureRepoSCM.AzureRepoSCMMapper;
import io.harness.ng.userprofile.entities.BitbucketSCM.BitbucketSCMMapper;
import io.harness.ng.userprofile.entities.GithubSCM.GithubSCMMapper;
import io.harness.ng.userprofile.entities.GitlabSCM.GitlabSCMMapper;
import io.harness.ng.userprofile.entities.SourceCodeManager.SourceCodeManagerMapper;
import io.harness.ng.userprofile.event.SourceCodeManagerEventListener;
import io.harness.ng.userprofile.services.api.SourceCodeManagerService;
import io.harness.ng.userprofile.services.api.UserInfoService;
import io.harness.ng.userprofile.services.impl.SourceCodeManagerServiceImpl;
import io.harness.ng.userprofile.services.impl.UserInfoServiceImpl;
import io.harness.ng.userprovider.UserPrincipalUserProvider;
import io.harness.ng.validator.service.NGHostValidationServiceImpl;
import io.harness.ng.validator.service.api.NGHostValidationService;
import io.harness.ng.webhook.WebhookSecretsConfig;
import io.harness.ng.webhook.services.api.RegistryWebhookEventService;
import io.harness.ng.webhook.services.api.WebhookEventProcessingService;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.ng.webhook.services.api.WebhookService;
import io.harness.ng.webhook.services.impl.HarnessRegistryWebhookEventServiceImpl;
import io.harness.ng.webhook.services.impl.WebhookEventProcessingServiceImpl;
import io.harness.ng.webhook.services.impl.WebhookServiceImpl;
import io.harness.ngbanners.outbox.handler.NgBannerEventHandler;
import io.harness.ngmanager.NgConnectorManagerClientModule;
import io.harness.ngmanager.TunnelService;
import io.harness.ngsettings.client.remote.NGSettingsClientModule;
import io.harness.ngsettings.outbox.SettingEventHandler;
import io.harness.notification.module.NotificationClientModule;
import io.harness.oidc.OidcResourceClientModule;
import io.harness.opa.OpaService;
import io.harness.opa.OpaServiceImpl;
import io.harness.opaclient.OpaClientModule;
import io.harness.organization.OrganizationClientModule;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.outbox.module.TransactionOutboxModule;
import io.harness.overrides.OverrideResourceClientModule;
import io.harness.perpetualtask.entityreference.PerpetualTaskEntityReferenceService;
import io.harness.persistence.UserProvider;
import io.harness.pipeline.remote.PipelineRemoteClientModule;
import io.harness.pipeline.triggers.TriggersClientModule;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.polling.client.ConnectorPollingService;
import io.harness.polling.service.impl.PollingPerpetualTaskServiceImpl;
import io.harness.polling.service.impl.PollingServiceImpl;
import io.harness.polling.service.impl.ScheduledPollingTaskInfoServiceImpl;
import io.harness.polling.service.impl.artifact.ArtifactPollingScheduledTaskManager;
import io.harness.polling.service.intfc.PollingPerpetualTaskService;
import io.harness.polling.service.intfc.PollingScheduledTaskService;
import io.harness.polling.service.intfc.PollingService;
import io.harness.polling.service.intfc.ScheduledPollingTaskInfoService;
import io.harness.project.ProjectClientModule;
import io.harness.publishing.BillingEventPublisher;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.CEAwsServiceEndpointConfig;
import io.harness.remote.CEAwsSetupConfig;
import io.harness.remote.CEAzureSetupConfig;
import io.harness.remote.CEGcpSetupConfig;
import io.harness.remote.CEProxyConfig;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.resourcegroupclient.ResourceGroupClientModule;
import io.harness.runner.cgi.CgiConfigClientModule;
import io.harness.runner.plugin.PluginConfigClientModule;
import io.harness.scim.service.ScimGroupService;
import io.harness.scim.service.ScimUserService;
import io.harness.scopeinfoclient.ScopeInfoClientModule;
import io.harness.secretmanagerclient.SecretManagementClientModule;
import io.harness.secrets.SecretDecryptor;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrarsV2;
import io.harness.serializer.NGLdapServiceRegistrars;
import io.harness.serializer.NextGenRegistrars;
import io.harness.serializer.WaitEngineRegistrars;
import io.harness.serializer.kryo.KryoConverterFactory;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.service.InstanceModule;
import io.harness.service.ServiceResourceClientModule;
import io.harness.service.stats.usagemetrics.eventconsumer.InstanceStatsEventListener;
import io.harness.services.DevopsEssentialsService;
import io.harness.services.DevopsEssentialsServiceImpl;
import io.harness.signup.clients.ClearBitClientConfig;
import io.harness.signup.services.impl.SignupModule;
import io.harness.sso.SSOSettingsClientModule;
import io.harness.steps.executable.LogBaseUrlProvider;
import io.harness.subscription.SubscriptionModule;
import io.harness.task.response.module.TaskResponseClientModule;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.CdTelemetryEventListener;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.template.TemplateResourceClientModule;
import io.harness.threading.ScalingThreadPoolExecutor;
import io.harness.threading.ThreadPoolConfig;
import io.harness.time.TimeModule;
import io.harness.timescaledb.JooqModule;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.TimeScaleDBServiceImpl;
import io.harness.timescaledb.TimescalePersistence;
import io.harness.timescaledb.metrics.HExecuteListener;
import io.harness.timescaledb.retention.RetentionManager;
import io.harness.timescaledb.retention.RetentionManagerImpl;
import io.harness.token.TokenClientModule;
import io.harness.tracing.AbstractPersistenceTracerModule;
import io.harness.transientData.TransientExecutionDataModule;
import io.harness.user.UserClientModule;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.version.VersionInfoManager;
import io.harness.version.VersionModule;
import io.harness.waiter.AsyncWaitEngineImpl;
import io.harness.waiter.NotifyResponse;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.waiter.WaiterConfiguration;
import io.harness.waiter.misc.ProgressUpdate;
import io.harness.waiter.misc.WaitInstance;
import io.harness.waiter.module.AbstractWaiterModule;
import io.harness.waiter.persistence.WaitNotifyCollectionNameResolver;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.zendesk.ZendeskManagerClientModule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.annotations.Entity;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.jooq.DSLContext;
import org.jooq.ExecuteListener;
import org.springframework.core.convert.converter.Converter;
import ru.vyarus.guice.validator.ValidationModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_TRIGGERS, HarnessModuleComponent.CDS_K8S})
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NextGenModule extends AbstractModule {
  private final NextGenConfiguration appConfig;
  public NextGenModule(NextGenConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    return ImmutableMap.<Class, String>builder()
        .put(DelegateSyncTaskResponse.class, "ngManager_delegateSyncTaskResponses")
        .put(DelegateAsyncTaskResponse.class, "ngManager_delegateAsyncTaskResponses")
        .put(DelegateTaskProgressResponse.class, "ngManager_delegateTaskProgressResponses")
        .put(NotifyResponse.class,
            WaitNotifyCollectionNameResolver.resolveCollectionName(
                WaitNotifyCollectionNameResolver.NOTIFY_RESPONSES_COLLECTION))
        .put(WaitInstance.class,
            WaitNotifyCollectionNameResolver.resolveCollectionName(
                WaitNotifyCollectionNameResolver.WAIT_INSTANCES_COLLECTION))
        .put(ProgressUpdate.class,
            WaitNotifyCollectionNameResolver.resolveCollectionName(
                WaitNotifyCollectionNameResolver.PROGRESS_UPDATE_COLLECTION))
        .put(DelayEvent.class,
            WaitNotifyCollectionNameResolver.resolveCollectionName(
                WaitNotifyCollectionNameResolver.DELAY_QUEUE_COLLECTION))
        .build();
  }

  @Provides
  @Singleton
  public AitGcpFeatureFlagChecker provideAitGcpFeatureFlagChecker(PmsFeatureFlagService pmsFeatureFlagService) {
    return accountId -> pmsFeatureFlagService.isEnabled(accountId, FeatureName.AIT_GCP_ENDPOINT_ENABLED);
  }

  @Provides
  @Singleton
  public boolean provideOpaConnectivityEnabled() {
    return appConfig.isOpaConnectivityEnabled();
  }

  @Provides
  @Singleton
  @Named("dailyAccountUsersJobInitialDelayInMinutes")
  public long provideDailyAccountUsersJobInitialDelayInMinutes() {
    return appConfig.getDailyAccountUsersJobInitialDelayInMinutes();
  }

  @Provides
  @Singleton
  @Named("totalAccountUsersJobInitialDelayInMinutes")
  public long provideTotalAccountUsersJobInitialDelayInMinutes() {
    return appConfig.getTotalAccountUsersJobInitialDelayInMinutes();
  }

  @Provides
  @Singleton
  @Named("PSQLExecuteListener")
  ExecuteListener executeListener() {
    return HExecuteListener.getInstance();
  }

  @Provides
  @Singleton
  @Named(DSEventConstants.SEND_EVENT_TO_DS)
  public boolean isSendEventToDS() {
    return appConfig.isSendEventToDS();
  }

  @Provides
  @Singleton
  @Named("freezeTemplateRegistrationExecutorService")
  public ExecutorService templateRegistrationExecutionServiceThreadPool() {
    ThreadFactory threadFactory =
        new ThreadFactoryBuilder().setNameFormat("FreezeTemplateRegistrationService-%d").build();
    return new ScalingThreadPoolExecutor(
        ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(1).idleTime(10).timeUnit(TimeUnit.SECONDS).build(),
        threadFactory);
  }

  @Provides
  @Singleton
  @Named("chaosTemplateRegistrationExecutorService")
  public ExecutorService chaosTemplateRegistrationExecutionServiceThreadPool() {
    ThreadFactory threadFactory =
        new ThreadFactoryBuilder().setNameFormat("ChaosTemplateRegistrationService-%d").build();
    return new ScalingThreadPoolExecutor(
        ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(1).idleTime(10).timeUnit(TimeUnit.SECONDS).build(),
        threadFactory);
  }

  @Provides
  @Singleton
  public CdcKafkaConfig cdcKafkaConfig(NextGenConfiguration configuration) {
    return configuration.getCdcKafkaConfig();
  }

  /**
   * Dedicated executor for utilization_snapshot CDC consumer.
   * {@code UNORDERED + isNoAck=false} mode ({@code runAck} path in {@link io.harness.kafka.consumers.HKafkaConsumer}).
   *
   * <p>Single thread (size=1) is intentional: {@code runAck} submits all records from a poll
   * batch as individual {@code CompletableFuture} tasks to this pool. With 1 thread the tasks
   * execute sequentially in submission order, preserving per-partition event ordering and
   * eliminating any risk of concurrent writes for the same document id. The poll-loop thread
   * blocks in {@code allOf.get()} without holding the Kafka consumer monitor, so there is
   * zero lock contention — this is the fix for the ORDERED-mode livelock where the poll
   * thread held the monitor for 1000ms while the executor was trying to call {@code resume()}.
   *
   * <p>Throughput: 1 thread × (1/3ms per record) ≈ 333 records/s per partition, which is
   * sufficient to drain the largest observed surge (175k records) in under 3 minutes.
   */
  @Provides
  @Singleton
  @Named(io.harness.ng.gitops.config.CdcKafkaConstants.UTILIZATION_SNAPSHOT_EXECUTOR)
  public ExecutorService cdcKafkaUtilizationSnapshotExecutor() {
    return Executors.newFixedThreadPool(
        1, new ThreadFactoryBuilder().setNameFormat("cdc-kafka-utilization-snapshot-%d").setDaemon(true).build());
  }

  /**
   * Dedicated executor for applications CDC consumer.
   * {@code UNORDERED + isNoAck=false} mode ({@code runAck} path in {@link io.harness.kafka.consumers.HKafkaConsumer}).
   *
   * <p>Single thread (size=1) is intentional: {@code runAck} submits all records from a poll
   * batch as individual {@code CompletableFuture} tasks to this pool. With 1 thread the tasks
   * execute sequentially in submission order, preserving per-partition event ordering and
   * eliminating any risk of concurrent writes for the same document id. The poll-loop thread
   * blocks in {@code allOf.get()} without holding the Kafka consumer monitor, so there is
   * zero lock contention — this is the fix for the ORDERED-mode livelock where the poll
   * thread held the monitor for 1000ms while the executor was trying to call {@code resume()}.
   *
   * <p>Throughput: 1 thread × (1/3ms per record) ≈ 333 records/s per partition, which is
   * sufficient to drain the largest observed surge (175k records) in under 3 minutes.
   */
  @Provides
  @Singleton
  @Named(io.harness.ng.gitops.config.CdcKafkaConstants.APPLICATIONS_EXECUTOR)
  public ExecutorService cdcKafkaApplicationsExecutor() {
    return Executors.newFixedThreadPool(
        1, new ThreadFactoryBuilder().setNameFormat("cdc-kafka-applications-%d").setDaemon(true).build());
  }

  @Provides
  @Singleton
  @Named("irTemplateRegistrationExecutorService")
  public ExecutorService irTemplateRegistrationExecutionServiceThreadPool() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("IRTemplateRegistrationService-%d").build();
    return new ScalingThreadPoolExecutor(
        ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(1).idleTime(10).timeUnit(TimeUnit.SECONDS).build(),
        threadFactory);
  }

  @Provides
  @Singleton
  @Named("stoTemplateRegistrationExecutorService")
  public ExecutorService stoTemplateRegistrationExecutionServiceThreadPool() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("StoTemplateRegistrationService-%d").build();
    return new ScalingThreadPoolExecutor(
        ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(1).idleTime(10).timeUnit(TimeUnit.SECONDS).build(),
        threadFactory);
  }

  @Provides
  @Singleton
  @Named("harTemplateRegistrationExecutorService")
  public ExecutorService harTemplateRegistrationExecutionServiceThreadPool() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("HarTemplateRegistrationService-%d").build();
    return new ScalingThreadPoolExecutor(
        ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(1).idleTime(10).timeUnit(TimeUnit.SECONDS).build(),
        threadFactory);
  }

  @Provides
  @Singleton
  @Named("aitTemplateRegistrationExecutorService")
  public ExecutorService aitTemplateRegistrationExecutionServiceThreadPool() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("AitTemplateRegistrationService-%d").build();
    return new ScalingThreadPoolExecutor(
        ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(1).idleTime(10).timeUnit(TimeUnit.SECONDS).build(),
        threadFactory);
  }

  @Provides
  @Singleton
  @Named("serviceAccountTemplateRegistrationExecutorService")
  public ExecutorService serviceAccountTemplateRegistrationExecutionServiceThreadPool() {
    ThreadFactory threadFactory =
        new ThreadFactoryBuilder().setNameFormat("ServiceAccountTemplateRegistrationService-%d").build();
    return new ScalingThreadPoolExecutor(
        ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(1).idleTime(10).timeUnit(TimeUnit.SECONDS).build(),
        threadFactory);
  }

  @Provides
  @Singleton
  @Named("GitAwareEntityHelperExecutorService")
  public ExecutorService gitAwareEntityHelperExecutorService() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("GitAwareEntityHelperService-%d").build();
    return new ScalingThreadPoolExecutor(
        ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(10).idleTime(10).timeUnit(TimeUnit.SECONDS).build(),
        threadFactory);
  }

  @Provides
  @Singleton
  @Named("DashboardExecutorService")
  public ExecutorService DashboardExecutorServiceThreadPool() {
    return new ScalingThreadPoolExecutor(appConfig.getDashboardExecutorServiceConfig(), "DashboardExecutorService-%d");
  }

  @Provides
  @Singleton
  @Named("cdBillingEventExecutor")
  public ExecutorService cdBillingEventExecutorServiceThreadPool() {
    ThreadPoolConfig threadPoolConfig =
        ThreadPoolConfig.builder().corePoolSize(20).maxPoolSize(100).idleTime(5).timeUnit(TimeUnit.SECONDS).build();
    return new ScalingThreadPoolExecutor(threadPoolConfig, "CD-Billing-Event-%d");
  }

  @Provides
  @Singleton
  CiDefaultEntityConfiguration getCiDefaultConfiguration() {
    return appConfig.getCiDefaultEntityConfiguration();
  }

  @Provides
  @Singleton
  LogStreamingServiceConfiguration getLogStreamingServiceConfiguration() {
    return appConfig.getLogStreamingServiceConfig();
  }

  @Provides
  @Named("cannyApiConfiguration")
  @Singleton
  CannyConfig getCannyConfig() {
    return appConfig.getCannyConfig();
  }

  @Provides
  @Named("clearbitApiConfig")
  @Singleton
  ClearBitClientConfig getClearBitclientConfig() {
    return appConfig.getClearBitClientConfig();
  }

  @Provides
  @Singleton
  Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
      DelegateServiceGrpcClient delegateServiceGrpcClient) {
    return Suppliers.memoize(() -> getDelegateCallbackToken(delegateServiceGrpcClient, appConfig));
  }

  @Provides
  @Singleton
  private FileServiceClientFactory fileServiceClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new FileServiceClientFactory(appConfig.getManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), new ServiceTokenGenerator(),
        kryoConverterFactory, NG_MANAGER.getServiceId());
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return appConfig.getDistributedLockImplementation() == null ? REDIS : appConfig.getDistributedLockImplementation();
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisLockConfig() {
    return appConfig.getRedisLockConfig();
  }

  @Provides
  @Singleton
  NgIteratorsConfig ngIteratorsConfig() {
    return appConfig.getNgIteratorsConfig();
  }

  @Provides
  @Singleton
  public SmpConfig smpConfig() {
    return appConfig.getSmpConfig();
  }

  private DelegateCallbackToken getDelegateCallbackToken(
      DelegateServiceGrpcClient delegateServiceClient, NextGenConfiguration appConfig) {
    log.info("Generating Delegate callback token");
    MongoDatabase.Builder mongoDatabaseBuilder = MongoDatabase.newBuilder()
                                                     .setCollectionNamePrefix("ngManager")
                                                     .setConnection(appConfig.getMongoConfig().getUri());
    String waitNotifyCollectionPrefix = WaitNotifyCollectionNameResolver.getCollectionPrefix();
    if (waitNotifyCollectionPrefix != null) {
      mongoDatabaseBuilder.setWaitNotifyCollectionPrefix(waitNotifyCollectionPrefix);
    }
    final DelegateCallbackToken delegateCallbackToken =
        delegateServiceClient.registerCallback(DelegateCallback.newBuilder()
                                                   .setMongoDatabase(mongoDatabaseBuilder.build())
                                                   .setNewCallbackFlow(appConfig.isEnableWaitNotifyEngineOptimisation())
                                                   .build());
    log.info("delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    NextGenApplication.configureObjectMapper(objectMapper);
    return objectMapper;
  }

  @Provides
  @Named("yaml-schema-subtypes")
  @Singleton
  public Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes() {
    Set<Class<? extends StepSpecType>> subTypesOfStepSpecType =
        HarnessReflections.get().getSubTypesOf(StepSpecType.class);
    Set<Class<?>> set = new HashSet<>(subTypesOfStepSpecType);
    return ImmutableMap.of(StepSpecType.class, set);
  }

  @Provides
  @Singleton
  @Named("GitSyncGrpcClientConfigs")
  public Map<Microservice, GrpcClientConfig> grpcClientConfigs() {
    return appConfig.getGitGrpcClientConfigs();
  }

  @Provides
  @Singleton
  CEProxyConfig ceProxyConfig() {
    return this.appConfig.getCeProxyConfig();
  }

  @Provides
  @Singleton
  CEAwsServiceEndpointConfig ceAwsServiceEndpointConfig() {
    return this.appConfig.getCeAwsServiceEndpointConfig();
  }

  @Provides
  @Singleton
  CEAwsSetupConfig ceAwsSetupConfig() {
    return this.appConfig.getCeAwsSetupConfig();
  }

  @Provides
  @Singleton
  CEAzureSetupConfig ceAzureSetupConfig() {
    return this.appConfig.getCeAzureSetupConfig();
  }

  @Provides
  @Singleton
  CEGcpSetupConfig ceGcpSetupConfig() {
    return this.appConfig.getCeGcpSetupConfig();
  }

  @Provides
  @Singleton
  AwsSdkDefaultBackOffStrategyConfiguration awsSdkDefaultBackOffStrategyConfiguration() {
    return appConfig.getAwsSdkDefaultBackOffStrategyConfiguration() != null
        ? appConfig.getAwsSdkDefaultBackOffStrategyConfiguration()
        : AwsSdkDefaultBackOffStrategyConfiguration.builder().build();
  }

  @Provides
  @Singleton
  public AsyncWaitEngine asyncWaitEngine(WaitNotifyEngine waitNotifyEngine) {
    return new AsyncWaitEngineImpl(waitNotifyEngine, NG_ORCHESTRATION);
  }

  @Provides
  @Singleton
  @Named("cdTsDbRetentionPeriodMonths")
  public String cdTsDbRetentionPeriodMonths() {
    return String.format(io.harness.ng.modules.constants.ModuleConstants.RETENTION_PERIOD_FORMAT,
        this.appConfig.getCdTsDbRetentionPeriodMonths());
  }

  @Provides
  @Singleton
  @Named(JWT_TOKEN_PUBLIC_KEYS_JSON_DATA_CACHE_KEY)
  Cache<String, JwtTokenPublicKeysJsonData> getJwtTokenValidationJwtConsumerCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache(JWT_TOKEN_PUBLIC_KEYS_JSON_DATA_CACHE_KEY, String.class,
        JwtTokenPublicKeysJsonData.class, AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.DAYS, 5)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named(JWT_TOKEN_SERVICE_ACCOUNT_DATA_CACHE_KEY)
  Cache<String, JwtTokenServiceAccountData> getJwtTokenServiceAccountCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache(JWT_TOKEN_SERVICE_ACCOUNT_DATA_CACHE_KEY, String.class,
        JwtTokenServiceAccountData.class, AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.DAYS, 5)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named(JWT_TOKEN_SCIM_SETTINGS_DATA_CACHE_KEY)
  Cache<String, JwtTokenScimAccountSettingsData> getJwtTokenScimSettingsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache(JWT_TOKEN_SCIM_SETTINGS_DATA_CACHE_KEY, String.class,
        JwtTokenScimAccountSettingsData.class, CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 2)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named("envPermissionMigrationCache")
  Cache<String, Boolean> getEnvPermissionMigrationCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("envPermissionMigrationCache", String.class, Boolean.class,
        CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.DAYS, 30)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named("ngldapSyncCache")
  Cache<String, Long> getNgldapSyncCache(HarnessCacheManager harnessCacheManager) {
    return harnessCacheManager.getCache(
        "ngldapSyncCache", String.class, Long.class, CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 10)));
  }

  @Provides
  @Singleton
  @Named("gitServiceConfiguration")
  public GitServiceConfiguration getGitServiceConfiguration() {
    return this.appConfig.getGitServiceConfiguration();
  }

  @Provides
  @Singleton
  @Named("harnessRegistryServiceClientConfig")
  public ServiceHttpClientConfig getHarnessRegistryServiceConfiguration() {
    return this.appConfig.getHarnessRegistryServiceClientConfig();
  }

  @Provides
  @Singleton
  @Named("harnessRegistryServiceSecret")
  public String getHarnessRegistryServiceSecret() {
    return this.appConfig.getNextGenConfig().getHarnessRegistryServiceSecret();
  }

  @Provides
  @Singleton
  InstanceSyncPerpetualTaskConfig getInstanceSyncPerpetualTaskConfig() {
    return this.appConfig.getInstanceSyncPerpetualTaskConfig();
  }

  @Provides
  @Singleton
  @Named("ngServiceSecret")
  public String getNgManagerServiceSecret() {
    return this.appConfig.getNextGenConfig().getNgManagerServiceSecret();
  }

  @Provides
  @Singleton
  @Named("logStreamingDelayExecutor")
  public ScheduledExecutorService logStreamingDelayExecutor() {
    ThreadPoolConfig threadPoolConfig = appConfig != null && appConfig.getLogStreamingServiceConfig() != null
            && appConfig.getLogStreamingServiceConfig().getThreadPoolConfig() != null
        ? appConfig.getLogStreamingServiceConfig().getThreadPoolConfig()
        : ThreadPoolConfig.builder().corePoolSize(10).build();
    return new ScheduledThreadPoolExecutor(threadPoolConfig.getCorePoolSize(),
        new ThreadFactoryBuilder().setNameFormat("log-client-pool-%d").setPriority(Thread.NORM_PRIORITY).build());
  }

  // Backs the @Named("OpaGitxStatusExecutor") executor used by the CD OPA onSave status handlers for async upserts.
  private ThreadPoolExecutor opaGitxStatusThreadPool() {
    ThreadPoolConfig threadPoolConfig = appConfig != null && appConfig.getOpaGitxStatusPoolConfig() != null
        ? appConfig.getOpaGitxStatusPoolConfig()
        : ThreadPoolConfig.builder().corePoolSize(4).maxPoolSize(8).idleTime(60).timeUnit(TimeUnit.SECONDS).build();

    return new ScalingThreadPoolExecutor(threadPoolConfig, "opa-gitx-status-pool");
  }

  // this should be used with a managed executor service
  private ThreadPoolExecutor serviceGitXThreadPool() {
    ThreadPoolConfig threadPoolConfig = appConfig != null && appConfig.getServiceGitXThreadConfig() != null
            && appConfig.getServiceGitXThreadConfig().getThreadPoolConfig() != null
        ? appConfig.getServiceGitXThreadConfig().getThreadPoolConfig()
        : ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(10).idleTime(30).timeUnit(TimeUnit.SECONDS).build();

    return new ScalingThreadPoolExecutor(threadPoolConfig, "service-gitx-pool");
  }

  private ThreadPoolExecutor environmentGitXThreadPool() {
    ThreadPoolConfig threadPoolConfig = appConfig != null && appConfig.getEnvironmentGitXThreadConfig() != null
            && appConfig.getEnvironmentGitXThreadConfig().getThreadPoolConfig() != null
        ? appConfig.getEnvironmentGitXThreadConfig().getThreadPoolConfig()
        : ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(10).idleTime(30).timeUnit(TimeUnit.SECONDS).build();

    return new ScalingThreadPoolExecutor(threadPoolConfig, "environment-gitx-pool");
  }

  private ThreadPoolExecutor deploymentStagePlanCreationInfoThreadPoolConfiguration() {
    ThreadPoolConfig threadPoolConfig = appConfig != null
            && appConfig.getDeploymentStagePlanCreationInfoThreadPoolConfiguration() != null
            && appConfig.getDeploymentStagePlanCreationInfoThreadPoolConfiguration().getThreadPoolConfig() != null
        ? appConfig.getDeploymentStagePlanCreationInfoThreadPoolConfiguration().getThreadPoolConfig()
        : ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(10).idleTime(30).timeUnit(TimeUnit.SECONDS).build();

    return new ScalingThreadPoolExecutor(threadPoolConfig, "deployment-stage-plan-creation-info-pool");
  }

  private ThreadPoolExecutor gitOpsStepExecutorServiceThreadPoolConfiguration() {
    return new ScalingThreadPoolExecutor(
        appConfig.getGitOpsStepExecutorServiceConfig(), "getGitOpsStepExecutorService");
  }

  private ThreadPoolExecutor entityCleanupThreadPoolConfiguration() {
    ThreadPoolConfig threadPoolConfig = appConfig != null && appConfig.getEntityCleanupConfiguration() != null
            && appConfig.getEntityCleanupConfiguration().getThreadPoolConfig() != null
        ? appConfig.getEntityCleanupConfiguration().getThreadPoolConfig()
        : ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(5).idleTime(10).timeUnit(TimeUnit.SECONDS).build();

    return new ScalingThreadPoolExecutor(threadPoolConfig, "entity-clean-up-pool");
  }

  private ThreadPoolExecutor perpetualTaskEntityReferenceRefreshThreadPoolConfiguration() {
    return new ScalingThreadPoolExecutor(
        ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(10).idleTime(30).timeUnit(TimeUnit.SECONDS).build(),
        "perpetual-task-entity-reference-refresh-pool");
  }

  private ExecutorService ngLdapGroupSyncThreadPoolConfiguration() {
    ThreadPoolConfig threadPoolConfig = appConfig != null && appConfig.getLdapGroupSyncPoolConfig() != null
        ? appConfig.getLdapGroupSyncPoolConfig()
        : ThreadPoolConfig.builder().corePoolSize(5).maxPoolSize(20).idleTime(60).timeUnit(TimeUnit.SECONDS).build();

    return new ScalingThreadPoolExecutor(threadPoolConfig, "ng-ldap-sync-pool");
  }

  private ThreadPoolExecutor batchSecretsExecutorServiceThreadPoolConfiguration() {
    return new ScalingThreadPoolExecutor(
        appConfig.getBatchSecretsExecutorServiceConfig(), "batchSecretsExecutorService");
  }

  private ThreadPoolExecutor gitXWebhookEventProcessorThreadPoolConfiguration() {
    ThreadPoolConfig threadPoolConfig =
        appConfig != null && appConfig.getGitXWebhookEventProcessorThreadPoolConfig() != null
        ? appConfig.getGitXWebhookEventProcessorThreadPoolConfig()
        : ThreadPoolConfig.builder().corePoolSize(5).maxPoolSize(50).idleTime(30).timeUnit(TimeUnit.SECONDS).build();

    return new ScalingThreadPoolExecutor(threadPoolConfig, "GitxWebhookEventProcessorThread");
  }

  @Provides
  @Singleton
  @Named("webhookBranchHookEventHsqsDequeueConfig")
  public HsqsDequeueConfig getWebhookBranchHookEventHsqsDequeueConfig() {
    return appConfig.getWebhookBranchHookEventHsqsDequeueConfig();
  }

  @Provides
  @Singleton
  @Named("globalTemplatesConfig")
  public GlobalTemplatesConfig getGlobalTemplatesConfig() {
    return this.appConfig.getGlobalTemplatesConfig();
  }

  @Provides
  @Singleton
  @Named("gitXWebhookEventQueueConfig")
  public HsqsDequeueConfig getGitXWebhookEventQueueConfig() {
    return appConfig.getGitXWebhookEventQueueConfig();
  }

  @Provides
  @Singleton
  @Named("gitXWebhookEventValidationQueueConfig")
  public HsqsDequeueConfig getGitXWebhookEventValidationQueueConfig() {
    return appConfig.getGitXWebhookEventValidationQueueConfig();
  }

  @Provides
  @Singleton
  @Named("webhookPushEventHsqsDequeueConfig")
  public HsqsDequeueConfig getWebhookPushEventHsqsDequeueConfig() {
    return appConfig.getWebhookPushEventHsqsDequeueConfig();
  }

  @Provides
  @Singleton
  @Named("webhookGitXPushEventQueueConfig")
  public HsqsDequeueConfig getWebhookGitXPushEventQueueConfig() {
    return appConfig.getWebhookGitXPushEventQueueConfig();
  }

  @Provides
  @Singleton
  @Named("deDuplicateUserGroupsJobConfig")
  public DeDuplicateUserGroupsConfig getDeDuplicateUserGroupsJobConfig() {
    return appConfig.getDeDuplicateUserGroupsJobConfig();
  }

  @Provides
  @Singleton
  @Named("orphanUserGroupsCleanupJobConfig")
  public OrphanUserGroupsCleanupConfig getOrphanUserGroupsCleanupJobConfig() {
    return appConfig.getOrphanUserGroupsCleanupJobConfig();
  }

  @Provides
  @Singleton
  @Named("serviceUniqueIdBackfillJobConfig")
  public ServiceUniqueIdBackfillConfig getServiceUniqueIdBackfillJobConfig() {
    return appConfig.getServiceUniqueIdBackfillJobConfig();
  }

  @Provides
  @Singleton
  @Named("serviceStepMaxTimeout")
  public String getServiceStepMaxTimeout() {
    return appConfig.getServiceStepMaxTimeout();
  }

  @Provides
  @Singleton
  @Named("cdLicensingV2CacheExpiryInHours")
  public long getCdLicensingV2CacheExpiryInHours() {
    return appConfig.getCdLicensingV2CacheExpiryInHours();
  }

  @Provides
  @Singleton
  @Named("deploymentCountBQConfig")
  public DeploymentCountBQConfig deploymentCountBQConfig() {
    return appConfig.getDeploymentCountBQConfig();
  }

  @Provides
  @Singleton
  @Named("infraStepMaxTimeout")
  public String getInfraStepMaxTimeout() {
    return appConfig.getInfraStepMaxTimeout();
  }

  @Provides
  @Singleton
  @Named("tokenExpirationConfig")
  public TokenExpirationConfig getTokenExpirationConfig() {
    return appConfig.getTokenExpirationConfig();
  }

  @Provides
  @Singleton
  @Named("scopedPermissionsBackfillConfig")
  public ScopedPermissionsBackfillConfig getScopedPermissionsBackfillConfig() {
    return appConfig.getScopedPermissionsBackfillConfig();
  }

  @Provides
  @Singleton
  @Named("tokenExpiryAlertIteratorConfig")
  public TokenExpiryAlertIteratorConfig getTokenExpiryAlertIteratorConfig() {
    return appConfig.getTokenExpiryAlertIteratorConfig();
  }

  @Override
  protected void configure() {
    install(VersionModule.getInstance());
    install(new io.harness.hsqs.client.HsqsServiceClientModule(
        appConfig.getQueueServiceClientConfig(), AuthorizationServiceHeader.BEARER.getServiceId()));
    install(PrimaryVersionManagerModule.getInstance());
    install(new NGSettingModule(appConfig));
    install(new AbstractPersistenceTracerModule() {
      @Override
      protected EventsFrameworkConfiguration eventsFrameworkConfiguration() {
        return appConfig.getEventsFrameworkConfiguration();
      }

      @Override
      protected String serviceIdProvider() {
        return NG_MANAGER.getServiceId();
      }
    });

    ServiceHttpClientConfig rhsConfig =
        appConfig.isRhsEnabled() ? appConfig.getRhsClientConfig() : appConfig.getNgManagerClientConfig();
    String rhsSecret = appConfig.isRhsEnabled() ? appConfig.getRhsServiceSecret()
                                                : appConfig.getNextGenConfig().getNgManagerServiceSecret();
    install(new OrganizationClientModule(rhsConfig, rhsSecret, NG_MANAGER.getServiceId()));
    install(new ProjectClientModule(rhsConfig, rhsSecret, NG_MANAGER.getServiceId()));

    // Bind CDEventInterceptor for OIDC context enrichment in CD stages
    // Uses direct DB access since ng-manager has access to StageExecutionInfoService
    bind(CDEventInterceptor.class).to(CDEventInterceptorDirectImpl.class);

    install(new AccessControlMigrationModule());
    install(DelegateServiceDriverModule.getInstance(false, true));
    install(TimeModule.getInstance());
    bind(NextGenConfiguration.class).toInstance(appConfig);

    install(new ProviderModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return appConfig.getMongoConfig();
      }
    });

    install(new OnboardingModule());
    bind(CDOverviewDashboardService.class).to(CDOverviewDashboardServiceImpl.class);
    bind(CDLandingDashboardService.class).to(CDLandingDashboardServiceImpl.class);
    bind(CDLandingPageService.class).to(CDLandingPageServiceImpl.class);
    bind(GitSyncConnectorService.class).to(GitSyncConnectorServiceImpl.class);
    bind(BigQueryService.class).to(BigQueryServiceImpl.class);
    bind(io.harness.ng.core.aiagent.imports.AiAgentImportService.class)
        .to(io.harness.ng.core.aiagent.imports.AiAgentImportServiceImpl.class);
    bind(NGNoopTimeScaleMigration.class).toInstance(new NGNoopTimeScaleMigration() {
      // no extra behavior, just a concrete instance of the abstract class
    });

    try {
      bind(AlloyDBService.class).toConstructor(AlloyDBServiceImpl.class.getConstructor(AlloyDBConfig.class));
      bind(DevopsEssentialsService.class).to(DevopsEssentialsServiceImpl.class);
    } catch (NoSuchMethodException e) {
      log.error("AlloyDBServiceImpl Initialization Failed due to missing constructor", e);
    }
    bind(AlloyDBConfig.class)
        .annotatedWith(Names.named("AlloyDBConfig"))
        .toInstance(
            appConfig.getAlloyDBConfig() != null ? appConfig.getAlloyDBConfig() : AlloyDBConfig.builder().build());

    try {
      bind(TimeScaleDBService.class)
          .toConstructor(TimeScaleDBServiceImpl.class.getConstructor(TimeScaleDBConfig.class));
      bind(TimescalePersistence.class)
          .toConstructor(TimescalePersistence.class.getConstructor(TimeScaleDBService.class, DSLContext.class));
      bind(RetentionManager.class).to(RetentionManagerImpl.class);
    } catch (NoSuchMethodException e) {
      log.error("TimeScaleDbServiceImpl Initialization Failed in due to missing constructor", e);
    }

    if (appConfig.getEnableDashboardTimescale() != null && appConfig.getEnableDashboardTimescale()) {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(appConfig.getTimeScaleDBConfig() != null ? appConfig.getTimeScaleDBConfig()
                                                               : TimeScaleDBConfig.builder().build());

      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("SecondaryTimeScaleDBConfig"))
          .toInstance(appConfig.getSecondaryTimeScaleDBConfig() != null ? appConfig.getSecondaryTimeScaleDBConfig()
                                                                        : TimeScaleDBConfig.builder().build());
    } else {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(TimeScaleDBConfig.builder().build());

      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("SecondaryTimeScaleDBConfig"))
          .toInstance(TimeScaleDBConfig.builder().build());
    }

    bind(TimeScaleDBService.class)
        .annotatedWith(Names.named("SecondaryTimeScaleDBService"))
        .toProvider(new Provider<>() {
          @Inject @Named("SecondaryTimeScaleDBConfig") TimeScaleDBConfig timeScaleDBConfig;

          @Override
          public TimeScaleDBService get() {
            return new TimeScaleDBServiceImpl(timeScaleDBConfig);
          }
        })
        .in(Singleton.class);
    bind(ScheduledThreadPoolExecutor.class)
        .annotatedWith(Names.named("HourlyDailyLicenseUsageScheduler-Worker"))
        .toInstance(new ScheduledThreadPoolExecutor(
            5, new ThreadFactoryBuilder().setNameFormat("hourly-daily-licenseUsageScheduler-worker-%d").build()));
    bind(ScheduledThreadPoolExecutor.class)
        .annotatedWith(Names.named("MonthlyYearlyLicenseUsageScheduler-Worker"))
        .toInstance(new ScheduledThreadPoolExecutor(
            2, new ThreadFactoryBuilder().setNameFormat("monthly-yearly-licenseUsageScheduler-worker-%d").build()));
    bind(int.class)
        .annotatedWith(Names.named("permissionCheckBatchSizeForConnectorListing"))
        .toInstance(appConfig.getPermissionCheckBatchSizeForConnectorListing());

    /*
    [secondary-db]: To use another DB, uncomment this and add @Named("primaryMongoConfig") to the above one

    install(new ProviderModule() {
       @Provides
       @Singleton
       @Named("secondaryMongoConfig")
       MongoConfig mongoConfig() {
         return appConfig.getSecondaryMongoConfig();
       }
     });*/
    bind(FileServiceClient.class).toProvider(FileServiceClientFactory.class).in(Scopes.SINGLETON);
    bind(LogStreamingServiceRestClient.class)
        .toProvider(NGLogStreamingClientFactory.builder()
                        .logStreamingServiceBaseUrl(appConfig.getLogStreamingServiceConfig().getBaseUrl())
                        .build());
    bind(LogBaseUrlProvider.class).to(NgManagerLogBaseUrlProvider.class).in(Scopes.SINGLETON);
    bind(WebhookEventService.class).to(WebhookServiceImpl.class);
    bind(ScimUserService.class).to(NGScimUserServiceImpl.class);
    bind(ScimGroupService.class).to(NGScimGroupServiceImpl.class);
    bind(ModuleVersionInfoService.class).to(ModuleVersionInfoServiceImpl.class);
    bind(PmsFeatureFlagService.class).to(PmsFeatureFlagHelper.class);

    install(new NGCloudCreditsModule(appConfig));
    install(new ValidationModule(getValidatorFactory()));
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new UserPrincipalUserProvider();
      }
    });
    install(new NextGenPersistenceModule());
    install(new CoreModule());
    install(UserClientModule.getInstance(this.appConfig.getManagerClientConfig(),
        this.appConfig.getNextGenConfig().getManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new ZendeskManagerClientModule(this.appConfig.getManagerClientConfig(),
        this.appConfig.getNextGenConfig().getManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new InviteModule(appConfig.isNgAuthUIEnabled()));
    install(new SignupModule(this.appConfig.getManagerClientConfig(),
        this.appConfig.getNextGenConfig().getManagerServiceSecret(), NG_MANAGER.getServiceId(),
        appConfig.getSignupNotificationConfiguration(), appConfig.getAccessControlClientConfiguration(),
        appConfig.getSignupDomainDenylistConfiguration(), appConfig.getLicenseManagerClientConfig()));
    install(GitopsModule.getInstance());
    install(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder().persistenceLayer(WaiterConfiguration.PersistenceLayer.SPRING).build();
      }
    });
    install(GitSyncModule.getInstance(getGitServiceConfiguration()));
    install(new GitSyncConfigClientModule(appConfig.getNgManagerClientConfig(),
        appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(NgLicenseHttpClientModule.getInstance(appConfig.getNgManagerClientConfig(),
        appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new CdLicenseUsageCgModule(appConfig.getManagerClientConfig(),
        appConfig.getNextGenConfig().getManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(JooqModule.getInstance());
    install(new AccountResourceNGClientModule(appConfig.getManagerClientConfig(),
        appConfig.getNextGenConfig().getManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new ScopeInfoModule());
    install(new NGAggregateModule());
    install(new DelegateServiceModule());
    install(NGModule.getInstance(NGModuleConfig.builder()
                                     .dbOpsServiceClientConfig(appConfig.getDbOpsServiceClientConfig())
                                     .aiMLOpsServiceClientConfig(appConfig.getAiMLOpsServiceClientConfig())
                                     .nextGenConfig(appConfig.getNextGenConfig())
                                     .build()));
    install(PluginModule.getInstance(
        PluginConfiguration.builder()
            .pluginExecutionConfig(appConfig.getPluginExecutionConfig())
            .harnessCodeServiceConfig(HarnessCodeServiceConfig.builder()
                                          .serviceSecret(appConfig.getNextGenConfig().getHarnessCodeServiceSecret())
                                          .gitUrl(appConfig.getBaseUrls().getHarnessCodeGitUrl())
                                          .apiUrl(appConfig.getBaseUrls().getHarnessCodeExternalUrl())
                                          .build())
            .build()));
    install(ExceptionModule.getInstance());
    install(new EventsFrameworkModule(
        this.appConfig.getEventsFrameworkConfiguration(), this.appConfig.getDebeziumConsumersConfigs()));
    install(new SecretManagementModule());
    install(new AccountClientModule(appConfig.getManagerClientConfig(),
        appConfig.getNextGenConfig().getManagerServiceSecret(), NG_MANAGER.toString()));
    install(new PipelineRemoteClientModule(
        ServiceHttpClientConfig.builder().baseUrl(appConfig.getPipelineServiceClientConfig().getBaseUrl()).build(),
        appConfig.getNextGenConfig().getPipelineServiceSecret(), NG_MANAGER.toString()));
    install(new IdpResourceClientModule(
        ServiceHttpClientConfig.builder()
            .readTimeOutSeconds(this.appConfig.getIdpServiceClientConfig().getReadTimeOutSeconds())
            .connectTimeOutSeconds(this.appConfig.getIdpServiceClientConfig().getConnectTimeOutSeconds())
            .baseUrl(this.appConfig.getIdpServiceClientConfig().getBaseUrl())
            .build(),
        this.appConfig.getNextGenConfig().getIdpServiceSecret(), NG_MANAGER.getServiceId(), ClientMode.PRIVILEGED));
    install(new TemplateResourceClientModule(appConfig.getTemplateServiceClientConfig(),
        appConfig.getNextGenConfig().getTemplateServiceSecret(), NG_MANAGER.toString(), true));
    install(new ConnectorResourceClientModule(appConfig.getNgManagerClientConfig(),
        appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId(), ClientMode.PRIVILEGED));
    install(new OidcResourceClientModule(appConfig.getNgManagerClientConfig(),
        appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId(), ClientMode.PRIVILEGED));
    install(new SecretManagementClientModule(this.appConfig.getManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new SecretNGManagerClientModule(this.appConfig.getNgManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new DelegateServiceDriverGrpcClientModule(this.appConfig.getNextGenConfig().getManagerServiceSecret(),
        this.appConfig.getGrpcClientConfig().getTarget(), this.appConfig.getGrpcClientConfig().getAuthority(), true));
    install(new TaskResponseClientModule(
        this.appConfig.getNextGenConfig().getManagerServiceSecret(), this.appConfig.getGrpcClientConfig()));
    install(new EntitySetupUsageClientModule(this.appConfig.getNgManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new ModulesClientModule(this.appConfig.getManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(YamlSdkModule.getInstance());
    install(new AuditClientModule(this.appConfig.getAuditClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId(),
        this.appConfig.isEnableAudit()));
    install(new NotificationClientModule(appConfig.getNotificationClientConfiguration()));
    install(new InstanceModule());
    install(new TokenClientModule(this.appConfig.getNgManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new OpaClientModule(
        appConfig.getOpaClientConfig(), appConfig.getPolicyManagerSecret(), NG_MANAGER.getServiceId(), true));
    install(EnforcementModule.getInstance());
    install(new DelegateSelectionLogHttpClientModule(this.appConfig.getManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(EnforcementClientModule.getInstance(appConfig.getNgManagerClientConfig(),
        appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId(),
        appConfig.getEnforcementClientConfiguration()));
    install(new AuthenticationSettingsModule(
        this.appConfig.getManagerClientConfig(), this.appConfig.getNextGenConfig().getManagerServiceSecret()));
    install(ConnectorModule.getInstance(
        appConfig.getNextGenConfig(), appConfig.getCeNextGenClientConfig(), appConfig.getConnectorTestConfig()));
    install(new ConnectorOidcModule());
    install(new HarnessIdClientModule(appConfig.getHarnessIdClientConfig()));
    install(new NgConnectorManagerClientModule(
        appConfig.getManagerClientConfig(), appConfig.getNextGenConfig().getManagerServiceSecret()));
    install(new DelegateNgManagerCgManagerClientModule(appConfig.getManagerClientConfig(),
        appConfig.getNextGenConfig().getManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new AgentNgManagerCgManagerClientModule(appConfig.getManagerClientConfig(),
        appConfig.getNextGenConfig().getManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new TriggersClientModule(
        ServiceHttpClientConfig.builder().baseUrl(appConfig.getPipelineServiceClientConfig().getBaseUrl()).build(),
        appConfig.getNextGenConfig().getPipelineServiceSecret(), NG_MANAGER.toString()));
    install(new ServiceResourceClientModule(appConfig.getNgManagerClientConfig(),
        appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new EnvironmentResourceClientModule(appConfig.getNgManagerClientConfig(),
        appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new InfrastructureResourceClientModule(appConfig.getNgManagerClientConfig(),
        appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new OverrideResourceClientModule(appConfig.getNgManagerClientConfig(),
        appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(new HarnessArtifactRegistryClientModule(appConfig.getHarnessRegistryServiceClientConfig(),
        appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId(), ClientMode.PRIVILEGED));
    install(new CodeResourceClientModule(
        ServiceHttpClientConfig.builder().baseUrl(appConfig.getBaseUrls().getScmServiceBaseUrl()).build(),
        appConfig.getNextGenConfig().getHarnessCodeServiceSecret(), NG_MANAGER.getServiceId(), ClientMode.PRIVILEGED));
    install(new DirectoryServiceResourceClientModule(
        ServiceHttpClientConfig.builder().baseUrl(appConfig.getBaseUrls().getDirectoryServiceUrl()).build(),
        appConfig.getNextGenConfig().getDirectoryServiceSecret(), NG_MANAGER.getServiceId()));
    install(new AiTestAutomationClientModule(appConfig.getAiTestAutomationClientConfig(),
        appConfig.getAitGcpClientConfig(), appConfig.getNextGenConfig().getRelicxSecret(), NG_MANAGER.getServiceId()));
    bind(AiTestAutomationService.class).to(AiTestAutomationServiceImpl.class).in(Singleton.class);
    install(HealthSourceResourceClientModule.getInstance(appConfig.getCvngClientConfig(),
        appConfig.getNextGenConfig().getCvngServiceSecret(), NG_MANAGER.getServiceId()));
    install(new MonitoredServiceResourceClientModule(appConfig.getCvngClientConfig(),
        appConfig.getNextGenConfig().getCvngServiceSecret(), NG_MANAGER.getServiceId()));
    bind(String.class)
        .annotatedWith(Names.named("aiVerifyAgentsServiceUrl"))
        .toInstance(appConfig.getBaseUrls() != null && appConfig.getBaseUrls().getAiVerifyAgentsServiceUrl() != null
                ? appConfig.getBaseUrls().getAiVerifyAgentsServiceUrl()
                : "");
    bind(AiTestAutomationCallbackService.class).to(AiTestAutomationCallbackServiceImpl.class);
    bind(AiTestAutomationPlaywrightCallbackService.class).to(AiTestAutomationPlaywrightCallbackServiceImpl.class);
    bind(FreezeCRUDService.class).to(FreezeCRUDServiceImpl.class);
    bind(FreezeEvaluateService.class).to(FreezeEvaluateServiceImpl.class);
    bind(FreezeSchemaService.class).to(FreezeSchemaServiceImpl.class);
    bind(FrozenExecutionService.class).to(FrozenExecutionServiceImpl.class);
    bind(CDNGStageSummaryResource.class).to(CDNGStageSummaryResourceImpl.class);
    bind(ApiKeyOpaService.class).to(ApiKeyOpaServiceImpl.class);
    bind(VariableOpaService.class).to(VariableOpaServiceImpl.class);
    bind(TunnelService.class).to(TunnelServiceImpl.class);
    install(new PrivateConnectivityModule());
    bind(ServiceAccountOpaService.class).to(ServiceAccountOpaServiceImpl.class);
    bind(TokenOpaService.class).to(TokenOpaServiceImpl.class);
    bind(UserGroupOpaService.class).to(UserGroupOpaServiceImpl.class);
    bind(HarnessCodeService.class).to(HarnessCodeServiceImpl.class);
    bind(GitOpsExpressionService.class).to(GitOpsExpressionServiceImpl.class);

    MapBinder<String, FilterPropertiesMapper> filterPropertiesMapper =
        MapBinder.newMapBinder(binder(), String.class, FilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.SECRET.toString()).to(SecretFilterPropertiesMapper.class);

    install(FeatureFlagModule.getInstance());

    install(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(NextGenRegistrars.kryoRegistrars)
            .addAll(NGLdapServiceRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(NextGenRegistrars.morphiaRegistrars)
            .addAll(NGLdapServiceRegistrars.morphiaRegistrars)
            .build();
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
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(ManagerRegistrarsV2.springConverters)
            .addAll(NextGenRegistrars.springConvertors)
            .addAll(WaitEngineRegistrars.springConverters)
            .build();
      }

      @Provides
      @Singleton
      List<YamlSchemaRootClass> yamlSchemaRootClasses() {
        return ImmutableList.<YamlSchemaRootClass>builder().addAll(NextGenRegistrars.yamlSchemaRegistrars).build();
      }

      @Provides
      @Singleton
      @Named("scmServiceBaseUrl")
      String getScmServiceBaseUrl() {
        return getBaseUrls().getScmServiceBaseUrl();
      }

      @Provides
      @Singleton
      @Named("harnessCodeGitUrl")
      String getHarnessCodeGitUrl() {
        return getBaseUrls().getHarnessCodeGitUrl();
      }
      @Provides
      @Singleton
      @Named("harnessCodeGitBaseUrl")
      String getHarnessCodeGitBaseUrl() {
        // Use harnessCodeGitUrl as base URL
        return getBaseUrls().getHarnessCodeGitUrl();
      }
      @Provides
      @Singleton
      BaseUrls getBaseUrls() {
        return appConfig.getBaseUrls();
      }

      @Provides
      @Singleton
      @Named("cgiTaskConfig")
      Map<String, CgiTaskConfig> getCGITaskConfig() {
        List<CgiTaskConfig> cgiTaskConfigList = loadCGITaskConfigFromFile(appConfig.getCgiTaskConfigPath());

        // Convert list to map with 'type' as the key
        return cgiTaskConfigList.stream().collect(Collectors.toMap(CgiTaskConfig::getType, config -> config));
      }

      @Provides
      @Singleton
      @Named("taskBinaryConfig")
      Map<String, TaskBinaryConfig> getTaskBinaryConfig() {
        List<TaskBinaryConfig> taskBinaryConfigList = loadTaskBinaryConfigFromFile(appConfig.getTaskBinaryConfigPath());

        return taskBinaryConfigList.stream().collect(Collectors.toMap(TaskBinaryConfig::getName, config -> config));
      }

      @Provides
      @Singleton
      WebhookSecretsConfig getWebhookSecretsConfig() {
        return appConfig.getWebhookSecretsConfig();
      }
    });
    install(new NGLdapModule(appConfig));
    install(new NGOidcModule(appConfig));
    install(new DevOpsEssentialsModule(appConfig.getDevOpsEssentialsConfigPath(), appConfig.getStripeConfig()));
    install(new NgVariableModule(appConfig));
    install(new NGIpAllowlistModule(appConfig));
    install(new NGSubscriptionsModule(appConfig));
    install(new NGFavoriteModule(appConfig));
    install(CertificateModule.getInstance());
    install(new NgBannerModule());
    install(new BrandingModule());
    install(new OidcProviderModule(appConfig));
    install(new EulaModule(appConfig));
    install(new GitXWebhookModule(appConfig));
    install(EntitySetupUsageModule.getInstance());
    install(PersistentLockModule.getInstance());
    install(new TransactionOutboxModule(
        appConfig.getOutboxPollConfig(), NG_MANAGER.getServiceId(), appConfig.isExportMetricsToStackDriver()));
    install(new ResourceGroupClientModule(appConfig.getResourceGroupClientConfig().getServiceConfig(),
        appConfig.getResourceGroupClientConfig().getSecret(), NG_MANAGER.getServiceId()));
    install(NGFileServiceModule.getInstance(appConfig.getFileServiceConfiguration().getFileStorageMode(),
        appConfig.getFileServiceConfiguration().getClusterName(),
        appConfig.getFileServiceConfiguration().getAwsRegion(),
        appConfig.getFileServiceConfiguration().getBucketsTtlDays()));
    install(new ScopeInfoClientModule(rhsConfig, rhsSecret, NG_MANAGER.getServiceId()));
    install(NgFileStoreModule.getInstance());
    install(new GitopsResourceClientModule(appConfig.getGitopsResourceClientConfig(), NG_MANAGER.getServiceId()));
    install(TransientExecutionDataModule.getInstance());
    if (TRUE.equals(appConfig.getAccessControlAdminClientConfiguration().getMockAccessControlService())) {
      AccessControlAdminClientConfiguration accessControlAdminClientConfiguration =
          AccessControlAdminClientConfiguration.builder()
              .accessControlServiceConfig(appConfig.getNgManagerClientConfig())
              .accessControlServiceSecret(appConfig.getNextGenConfig().getNgManagerServiceSecret())
              .build();
      install(new AccessControlAdminClientModule(accessControlAdminClientConfiguration, NG_MANAGER.getServiceId()));
    } else {
      install(new AccessControlAdminClientModule(
          appConfig.getAccessControlAdminClientConfiguration(), NG_MANAGER.getServiceId()));
    }
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return appConfig.getSegmentConfiguration();
      }
    });

    install(new AbstractAccountModule() {
      @Override
      public AccountConfig accountConfiguration() {
        return appConfig.getAccountConfig();
      }
    });
    install(new AbstractChaosModule() {
      @Override
      public ServiceHttpClientConfig chaosClientConfig() {
        return appConfig.getChaosServiceClientConfig();
      }

      @Override
      public String serviceSecret() {
        return appConfig.getNextGenConfig().getChaosServiceSecret();
      }

      @Override
      public String clientId() {
        return CHAOS_SERVICE.name();
      }
    });

    install(new LoadTestModule());
    if (appConfig.getLoadTestServiceClientConfig() != null) {
      install(new LoadTestClientModule(appConfig.getLoadTestServiceClientConfig(),
          appConfig.getNextGenConfig().getLoadTestManagerServiceSecret(), LOAD_TEST_MANAGER_SERVICE.name()));
    }

    install(new StoModule());

    install(new AbstractServiceDiscoveryModule() {
      @Override
      public ServiceHttpClientConfig serviceDiscoveryClientConfig() {
        return appConfig.getServiceDiscoveryServiceClientConfig();
      }

      @Override
      public String serviceSecret() {
        return appConfig.getNextGenConfig().getServiceDiscoveryServiceSecret();
      }

      @Override
      public String clientId() {
        return SERVICE_DISCOVERY_SERVICE.name();
      }
    });

    install(new AbstractMonitoringManagerModule() {
      @Override
      public ServiceHttpClientConfig MonitoringManagerClientConfig() {
        return appConfig.getMonitoringManagerServiceClientConfig();
      }

      @Override
      public String serviceSecret() {
        return appConfig.getNextGenConfig().getMonitoringManagerServiceSecret();
      }

      @Override
      public String clientId() {
        return MONITORING_MANAGER_SERVICE.name();
      }
    });

    install(new AbstractIROManagerModule() {
      @Override
      public ServiceHttpClientConfig iroManagerClientConfig() {
        return appConfig.getIroManagerServiceClientConfig();
      }

      @Override
      public IRConfig irConfig() {
        return appConfig.getIrConfig();
      }

      @Override
      public String serviceSecret() {
        return appConfig.getNextGenConfig().getIroManagerServiceSecret();
      }

      @Override
      public String clientId() {
        return IRO_MANAGER_SERVICE.name();
      }
    });
    install(LicenseModule.getInstance());
    install(SubscriptionModule.createInstance(appConfig.getSubscriptionConfig()));
    bind(AggregateUserService.class).to(AggregateUserServiceImpl.class);
    registerOutboxEventHandlers();
    bind(OutboxEventHandler.class).to(NextGenOutboxEventHandler.class);
    install(NGProjectOrgModule.getInstance());
    install(new FmeClientModule(appConfig.getFmeClientConfig(), NG_MANAGER.getServiceId()));
    bind(NGModulesService.class).to(NGModulesServiceImpl.class);
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("TaskPoll-Thread"));
    // to be used for jobs that run at a very low frequency eg. once in few hours
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("lowFrequencyScheduler"))
        .toInstance(new ManagedScheduledExecutorService("low-frequency-scheduler"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("OrganizationBillingScheduler"))
        .toInstance(new ManagedScheduledExecutorService("OrganizationBilling-Thread"));

    bind(LastAdminCheckService.class).to(LastAdminCheckServiceImpl.class);
    bind(NgUserService.class).to(NgUserServiceImpl.class);
    bind(NgUserScopeService.class).to(NgUserServiceImpl.class);
    bind(AccountOrgProjectHelper.class).to(AccountOrgProjectHelperImpl.class);
    bind(UserGroupService.class).to(UserGroupServiceImpl.class);
    bind(DefaultUserGroupService.class).to(DefaultUserGroupServiceImpl.class);
    bind(DefaultUserGroupScopeService.class).to(DefaultUserGroupServiceImpl.class);
    bind(UserInfoService.class).to(UserInfoServiceImpl.class);
    bind(WebhookService.class).to(WebhookServiceImpl.class);
    bind(WebhookEventProcessingService.class).to(WebhookEventProcessingServiceImpl.class);
    bind(NGHostValidationService.class).to(NGHostValidationServiceImpl.class);
    bind(BillingEventPublisher.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(USER_ENTITY + ENTITY_CRUD))
        .to(UserEntityCrudStreamListener.class);
    bind(MessageProcessor.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.SETUP_USAGE_ENTITY))
        .to(SetupUsageChangeEventMessageProcessor.class);
    install(AccessControlClientModule.getInstance(
        appConfig.getAccessControlClientConfiguration(), NG_MANAGER.getServiceId(), true));
    install(CeLicenseClientModule.getInstance(appConfig.getManagerClientConfig(),
        appConfig.getNextGenConfig().getManagerServiceSecret(), NG_MANAGER.getServiceId()));
    bind(DecryptionHelper.class).to(DecryptionHelperViaManager.class);
    bind(SecretDecryptor.class).to(SecretDecryptorViaNg.class);
    install(new NgSMTPSettingsHttpClientModule(
        this.appConfig.getManagerClientConfig(), this.appConfig.getNextGenConfig().getManagerServiceSecret()));
    install(new NGSettingsClientModule(this.appConfig.getNgManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId(), true));
    install(new CgiConfigClientModule(this.appConfig.getNgManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId(), ClientMode.PRIVILEGED,
        false));
    install(new PluginConfigClientModule(this.appConfig.getNgManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId(), ClientMode.PRIVILEGED,
        false));
    bind(SourceCodeManagerService.class).to(SourceCodeManagerServiceImpl.class);
    install(new SSOSettingsClientModule(this.appConfig.getNgManagerClientConfig(),
        this.appConfig.getNextGenConfig().getNgManagerServiceSecret(), NG_MANAGER.getServiceId()));
    install(KafkaModule.getInstance(appConfig.getKafkaModuleConfig()));
    bind(SmtpNgService.class).to(SmtpNgServiceImpl.class);
    bind(ApiKeyService.class).to(ApiKeyServiceImpl.class);
    bind(TokenService.class).to(TokenServiceImpl.class);

    // Git branch deletion observers
    Multibinder<GitBranchDeleteObserver> branchDeleteObserverMultibinder =
        Multibinder.newSetBinder(binder(), GitBranchDeleteObserver.class);
    branchDeleteObserverMultibinder.addBinding().to(TemplateMetadataBranchDeleteObserver.class);

    // Public key revocation bindings
    Multibinder<PublicKeyRevoker> publicKeyRevokerMultibinder =
        Multibinder.newSetBinder(binder(), PublicKeyRevoker.class);
    publicKeyRevokerMultibinder.addBinding().to(CodeApiPublicKeyRevoker.class);
    bind(PublicKeyRevokerFactory.class).in(Singleton.class);

    bind(FeedbackService.class).to(FeedbackServiceImpl.class);
    bind(PollingService.class).to(PollingServiceImpl.class);
    bind(ConnectorPollingService.class).to(PollingServiceImpl.class);
    bind(PollingPerpetualTaskService.class).to(PollingPerpetualTaskServiceImpl.class);
    bind(PollingScheduledTaskService.class).to(ArtifactPollingScheduledTaskManager.class);
    bind(ScheduledPollingTaskInfoService.class).to(ScheduledPollingTaskInfoServiceImpl.class);
    bind(PerpetualTaskEntityReferenceService.class).to(PerpetualTaskEntityReferenceServiceImpl.class);
    bind(JenkinsBuildStepHelperService.class).to(JenkinsBuildStepHelperServiceImpl.class);
    bind(BambooBuildStepHelperService.class).to(BambooBuildStepHelperServiceImpl.class);
    bind(EntityRefreshService.class).to(EntityRefreshServiceImpl.class);
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("ngTelemetryPublisherExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("ng-telemetry-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));

    bind(CDGitXService.class).to(CDGitXServiceImpl.class).in(Singleton.class);

    bind(ExecutorService.class)
        .annotatedWith(Names.named("service-gitx-executor"))
        .toInstance(new ManagedExecutorService(serviceGitXThreadPool()));

    bind(Executor.class)
        .annotatedWith(Names.named("OpaGitxStatusExecutor"))
        .toInstance(new ManagedExecutorService(opaGitxStatusThreadPool()));

    bind(ExecutorService.class)
        .annotatedWith(Names.named("environment-gitx-executor"))
        .toInstance(new ManagedExecutorService(environmentGitXThreadPool()));

    bind(ExecutorService.class)
        .annotatedWith(Names.named("deployment-stage-plan-creation-info-executor"))
        .toInstance(new ManagedExecutorService(deploymentStagePlanCreationInfoThreadPoolConfiguration()));

    bind(ExecutorService.class)
        .annotatedWith(Names.named("entity-clean-up-executor"))
        .toInstance(new ManagedExecutorService(entityCleanupThreadPoolConfiguration()));

    bind(ExecutorService.class)
        .annotatedWith(Names.named("perpetual-task-entity-reference-refresh-executor"))
        .toInstance(new ManagedExecutorService(perpetualTaskEntityReferenceRefreshThreadPoolConfiguration()));

    bind(ExecutorService.class)
        .annotatedWith(Names.named("git-ops-step-executor"))
        .toInstance(new ManagedExecutorService(gitOpsStepExecutorServiceThreadPoolConfiguration()));

    bind(ExecutorService.class)
        .annotatedWith(Names.named("batch-secrets"))
        .toInstance(new ManagedExecutorService(batchSecretsExecutorServiceThreadPoolConfiguration()));

    bind(ExecutorService.class)
        .annotatedWith(Names.named(NG_LDAP_EXECUTOR))
        .toInstance(new ManagedExecutorService(ngLdapGroupSyncThreadPoolConfiguration()));

    bind(ExecutorService.class)
        .annotatedWith(Names.named(GitSyncModuleConstants.GITX_WEBHOOK_EVENT_PROCESSOR_EXECUTOR_NAME))
        .toInstance(new ManagedExecutorService(gitXWebhookEventProcessorThreadPoolConfiguration()));

    MapBinder<SCMType, SourceCodeManagerMapper> sourceCodeManagerMapBinder =
        MapBinder.newMapBinder(binder(), SCMType.class, SourceCodeManagerMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.BITBUCKET).to(BitbucketSCMMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.GITHUB).to(GithubSCMMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.GITLAB).to(GitlabSCMMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.AWS_CODE_COMMIT).to(AwsCodeCommitSCMMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.AZURE_REPO).to(AzureRepoSCMMapper.class);

    install(new IACMServiceClientModule(appConfig.getIacmClientConfig()));

    bind(ServiceAccountService.class).to(ServiceAccountServiceImpl.class);
    bind(OpaService.class).to(OpaServiceImpl.class);
    bind(Clock.class).toInstance(Clock.systemUTC());
    bind(PostProdRollbackService.class).to(PostProdRollbackServiceImpl.class);
    bind(IRODataCollectionTaskService.class).to(IRODataCollectionTaskServiceImpl.class);
    bind(ServiceOpaService.class).to(ServiceOpaServiceImpl.class);
    bind(EnvironmentOpaService.class).to(EnvironmentOpaServiceImpl.class);
    bind(InfrastructureOpaService.class).to(InfrastructureOpaServiceImpl.class);
    bind(OverrideOpaService.class).to(OverrideOpaServiceImpl.class);
    bind(ServiceOpaStatusRepository.class);
    bind(EnvironmentOpaStatusRepository.class);
    bind(InfrastructureOpaStatusRepository.class);
    bind(ServiceOverrideOpaStatusRepository.class);
    bind(ServiceOpaStatusHandler.class);
    bind(EnvironmentOpaStatusHandler.class);
    bind(InfrastructureOpaStatusHandler.class);
    bind(ServiceOverrideOpaStatusHandler.class);
    bind(RegistryWebhookEventService.class).to(HarnessRegistryWebhookEventServiceImpl.class);

    bind(LicenseScopeService.class).to(DefaultLicenseServiceImpl.class);
    bind(OrganizationScopeService.class).to(OrganizationServiceImpl.class);
    bind(ProjectScopeService.class).to(ProjectServiceImpl.class);

    registerEventsFrameworkMessageListeners(appConfig);
    install(EncryptorBindingsModule.getInstance());

    bindExceptionHandlers();
  }

  private void bindExceptionHandlers() {
    MapBinder<Class<? extends Exception>, ExceptionHandler> exceptionHandlerMapBinder = MapBinder.newMapBinder(
        binder(), new TypeLiteral<Class<? extends Exception>>() {}, new TypeLiteral<ExceptionHandler>() {});
    CCMConnectorExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(CCMConnectorExceptionHandler.class));
  }

  private void registerOutboxEventHandlers() {
    MapBinder<String, OutboxEventHandler> outboxEventHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, OutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(ORGANIZATION).to(OrganizationEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(PROJECT).to(ProjectEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(ResourceTypeConstants.USER_GROUP).to(UserGroupEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(SECRET).to(SecretEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(USER).to(UserEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(DELEGATE_CONFIGURATION).to(DelegateProfileEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(SERVICE_ACCOUNT).to(ServiceAccountEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(CONNECTOR).to(ConnectorEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(SERVICE).to(ServiceOutBoxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(ENVIRONMENT).to(EnvironmentOutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(ENVIRONMENT_GROUP).to(EnvironmentGroupOutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(FILE).to(FileEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(API_KEY).to(ApiKeyEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(TOKEN).to(TokenEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(VARIABLE).to(VariableEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(SETTING).to(SettingEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(DEPLOYMENT_FREEZE).to(FreezeOutboxEventHandler.class);
    // To support freeze bypass audit event that are tied to pipeline execution
    outboxEventHandlerMapBinder.addBinding(PIPELINE).to(FreezeOutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(IP_ALLOWLIST_CONFIG).to(IPAllowlistConfigEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(EULA).to(EulaEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(MODULE_LICENSE).to(ModuleLicenseOutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(CERTIFICATE).to(NgCertificateEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(BANNER).to(NgBannerEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(BRANDING_ASSET).to(BrandingAssetEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(BRANDING_SETTINGS).to(BrandingEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(GITX_WEBHOOK).to(GitXWebhookOutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(PLATFORM_LIMIT).to(PlatformLimitThresholdEventHandler.class);
    // Syncs NG auth settings changes (currently OIDC providers) to Directory Service
    outboxEventHandlerMapBinder.addBinding(DSEventConstants.AUTH_DS).to(AuthDSEventHandler.class);
  }

  private void registerEventsFrameworkMessageListeners(NextGenConfiguration appConfig) {
    registerDefaultEntityCleanupMessageListener(appConfig);
    bind(MessageListener.class).annotatedWith(Names.named(ACCOUNT_ENTITY + ENTITY_CRUD)).to(AccountSetupListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(PROJECT_ENTITY + ENTITY_CRUD))
        .to(ProjectEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(CONNECTOR_ENTITY + ENTITY_CRUD))
        .to(ConnectorEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(CONNECTOR_ENTITY + "_ZOOM" + ENTITY_CRUD))
        .to(ZoomConnectorCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(ENVIRONMENT_GROUP_ENTITY + ENTITY_CRUD))
        .to(EnvironmentGroupEntityCrudStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(SECRET_ENTITY + ENTITY_CRUD))
        .to(SecretEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(SERVICEACCOUNT_ENTITY + ENTITY_CRUD))
        .to(ServiceAccountEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(TERRAFORM_CONFIG_ENTITY + ENTITY_CRUD))
        .to(TerraformConfigEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(TERRAGRUNT_CONFIG_ENTITY + ENTITY_CRUD))
        .to(TerragruntConfigEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(CLOUDFORMATION_CONFIG_ENTITY + ENTITY_CRUD))
        .to(CloudformationConfigEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(AZURE_ARM_CONFIG_ENTITY + ENTITY_CRUD))
        .to(AzureARMConfigEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(VARIABLE_ENTITY + ENTITY_CRUD))
        .to(VariableEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(TEMPLATE_ENTITY + ENTITY_CRUD))
        .to(CustomDeploymentEntityCRUDStreamEventListener.class);
    bind(CustomDeploymentMetadataMigrationService.class).to(CustomDeploymentMetadataMigrationServiceImpl.class);
    bind(MessageListener.class).annotatedWith(Names.named(INSTANCE_STATS)).to(InstanceStatsEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.USER_GROUP + ENTITY_CRUD))
        .to(UserGroupEntityCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.FREEZE_CONFIG + ENTITY_CRUD))
        .to(FreezeEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.FILTER + ENTITY_CRUD))
        .to(FilterEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.LICENSE_MODULES + ENTITY_CRUD))
        .to(ModuleLicenseEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.CD_TELEMETRY + ENTITY_CRUD))
        .to(CdTelemetryEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.SCM + ENTITY_CRUD))
        .to(SourceCodeManagerEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.SETTINGS + ENTITY_CRUD))
        .to(SettingsEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.API_KEY_ENTITY + ENTITY_CRUD))
        .to(ApiKeyEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.POLLING_DOCUMENT + ENTITY_CRUD))
        .to(PollingDocumentEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.PERPETUAL_TASK_ENTITY_REFERENCE + ENTITY_CRUD))
        .to(PerpetualTaskEntityReferenceCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.GITOPS_CLUSTER_ENTITY + ENTITY_CRUD))
        .to(ClusterCrudStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.GITOPS_AGENT_ENTITY + ENTITY_CRUD))
        .to(AgentCrudStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkMetadataConstants.FILE_ENTITY + ENTITY_CRUD))
        .to(FileEntityCRUDStreamListener.class);

    bind(MessageListener.class)
        .annotatedWith(Names.named(USER_SCOPE_RECONCILIATION))
        .to(UserMembershipReconciliationMessageProcessor.class);

    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.USERMEMBERSHIP))
        .to(UserMembershipStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.MODULE_LICENSE))
        .to(ModuleLicenseStreamListener.class);

    bind(MessageListener.class).annotatedWith(Names.named(SETUP_USAGE)).to(SetupUsageChangeEventMessageListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_ACTIVITY))
        .to(EntityActivityCrudEventMessageListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.LICENSES_USAGE_REDIS_EVENT_CONSUMER))
        .to(LicenseUsageEventMessageListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.EXECUTION_RETENTION_CLEANUP_EVENT))
        .to(ExecutionRetentionCleanupListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.GIT_PUSH_EVENT_STREAM))
        .to(GitPushEventStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.GITX_WEBHOOK_PUSH_EVENT_STREAM))
        .to(GitXWebhookPushEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.GIT_PR_EVENT_STREAM))
        .to(GitXWebhookPullRequestEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.GIT_BRANCH_HOOK_EVENT_STREAM))
        .to(GitBranchHookEventStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.GITX_WEBHOOK_BRANCH_HOOK_EVENT_STREAM))
        .to(GitXWebhookBranchEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.GIT_FULL_SYNC_STREAM))
        .to(FullSyncMessageListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.GIT_SYNC_ENTITY_STREAM + ENTITY_CRUD))
        .to(GitSyncProjectCleanup.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(CD_ACCOUNT_EXECUTION_METADATA + ENTITY_CRUD))
        .to(AccountExecutionMetadataCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(DEPLOYMENT_ACCOUNTS + ENTITY_CRUD))
        .to(DeploymentAccountsCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(DEPLOYMENT_SUMMARY_NG + ENTITY_CRUD))
        .to(DeploymentSummaryNGCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(INSTANCE_DEPLOYMENT_INFO + ENTITY_CRUD))
        .to(InstanceDeploymentInfoCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(INSTANCE_NG + ENTITY_CRUD))
        .to(InstanceNGCRUDStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(INSTANCE_SYNC + ENTITY_CRUD))
        .to(InstanceSyncEntityCrudStreamListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(DRIFT_DETECTION + ENTITY_CRUD))
        .to(DriftDetectionEntityCrudStreamListener.class);
  }

  private void registerDefaultEntityCleanupMessageListener(NextGenConfiguration appConfig) {
    Set<Class<?>> entitiesToCleanUp = new HashSet<>();
    Map<Class<?>, List<AutoCleanupConfig>> entityToCleanupConfigsMap = new HashMap<>();
    Set<Class> entities = getClassesFromMorphiaRegistrarsWithEntityAnnotation(NextGenRegistrars.morphiaRegistrars);
    entities.forEach(entityClass -> {
      AutoCleanupConfig[] autoCleanupConfigs =
          (AutoCleanupConfig[]) entityClass.getAnnotationsByType(AutoCleanupConfig.class);
      List<AutoCleanupConfig> autoCleanupsListConfig = new ArrayList<>();
      for (AutoCleanupConfig autoCleanupConfig : autoCleanupConfigs) {
        if (autoCleanupConfig != null && autoCleanupConfig.processDeleteEvents()) {
          entitiesToCleanUp.add(entityClass);
          autoCleanupsListConfig.add(autoCleanupConfig);
          entityToCleanupConfigsMap.put(entityClass, autoCleanupsListConfig);
        }
      }
    });

    bind(new TypeLiteral<Set<Class<?>>>() {})
        .annotatedWith(Names.named("entities-to-clean-up"))
        .toInstance(entitiesToCleanUp);

    bind(new TypeLiteral<Map<Class<?>, List<AutoCleanupConfig>>>() {})
        .annotatedWith(Names.named("entity-to-clean-up-configs-map"))
        .toInstance(entityToCleanupConfigsMap);

    // Example of binding a constant directly
    bind(Integer.class)
        .annotatedWith(Names.named("entity-clean-up-batch-size"))
        .toInstance(
            appConfig.getEntityCleanupConfiguration().getBatchSize()); // Or any other way you determine this value

    bind(MessageListener.class)
        .annotatedWith(Names.named(DEFAULT_ENTITY + ENTITY_CRUD))
        .to(EntityCleanupStreamListener.class);
  }

  Set<Class> getClassesFromMorphiaRegistrarsWithEntityAnnotation(Set<Class<? extends MorphiaRegistrar>> registrars) {
    Set<Class> classes = ConcurrentHashMap.newKeySet();
    registrars.forEach(registrar -> {
      try {
        Constructor<?> constructor = registrar.getConstructor();
        final MorphiaRegistrar morphiaRegistrar = (MorphiaRegistrar) constructor.newInstance();
        morphiaRegistrar.registerClasses(classes);
      } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
        throw new GeneralException("Failed initializing morphia", e);
      }
    });
    return classes.stream().filter(clazz -> hasEntityAnnotation(clazz)).collect(Collectors.toSet());
  }

  private boolean hasEntityAnnotation(Class mc) {
    return mc.getAnnotation(Entity.class) != null;
  }

  private ValidatorFactory getValidatorFactory() {
    return Validation.byDefaultProvider()
        .configure()
        .parameterNameProvider(new ReflectionParameterNameProvider())
        .buildValidatorFactory();
  }

  @Provides
  @Singleton
  @Named(OrganizationService.ORG_SCOPE_INFO_DATA_CACHE_KEY)
  Cache<String, ScopeInfo> getOrgScopeInfoDataCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache(OrganizationService.ORG_SCOPE_INFO_DATA_CACHE_KEY, String.class,
        ScopeInfo.class, CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 1)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named(ProjectService.PROJECT_SCOPE_INFO_DATA_CACHE_KEY)
  Cache<String, ScopeInfo> getProjectScopeInfoDataCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache(ProjectService.PROJECT_SCOPE_INFO_DATA_CACHE_KEY, String.class, ScopeInfo.class,
        CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 1)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named(SCOPE_INFO_UNIQUE_ID_CACHE_KEY)
  Cache<String, ScopeInfo> getScopeInfoUniqueIdCache(HarnessCacheManager harnessCacheManager) {
    return harnessCacheManager.getCache(SCOPE_INFO_UNIQUE_ID_CACHE_KEY, String.class, ScopeInfo.class,
        CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 1)));
  }

  @Provides
  @Singleton
  AutoProvisionLicenseConfig getAutoProvisionLicenseConfig(NextGenConfiguration appConfig) {
    AutoProvisionLicenseConfig config = appConfig.getAutoProvisionLicenseConfig();
    return config != null ? config : new AutoProvisionLicenseConfig(false, null);
  }

  @Provides
  @Singleton
  @Named("customDeploymentMetadataMigrationExecutor")
  public ExecutorService customDeploymentMetadataMigrationExecutor() {
    ThreadFactory threadFactory =
        new ThreadFactoryBuilder().setNameFormat("CustomDeploymentMetadataMigration-%d").build();
    return new ScalingThreadPoolExecutor(
        ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(1).idleTime(30).timeUnit(TimeUnit.SECONDS).build(),
        threadFactory);
  }

  private List<CgiTaskConfig> loadCGITaskConfigFromFile(String cgiConfigFilePath) {
    /*
     * If "cgiConfigFilePath" is empty then default to the local bazel path where the
     * config file will be present else use the path provided to fetch the config file.
     * It will be empty for local environment and non-empty for container environment.
     */
    String cgiConfigPath;
    if ((cgiConfigFilePath != null) && !(cgiConfigFilePath.isEmpty())) {
      cgiConfigPath = cgiConfigFilePath;
    } else {
      cgiConfigPath = System.getProperty("user.dir") + "/120-ng-manager";
    }
    String cgiConfigFile = cgiConfigPath + "/cgi-config.yaml";
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    try {
      byte[] yamlData = Files.readAllBytes(Paths.get(cgiConfigFile));
      // Read the YAML file into a Map
      return objectMapper.readValue(
          yamlData, objectMapper.getTypeFactory().constructCollectionType(List.class, CgiTaskConfig.class));
    } catch (IOException e) {
      log.error("Failed to load CGI Task Config from file: {}", cgiConfigFilePath, e);
    }
    return Collections.emptyList();
  }

  private List<TaskBinaryConfig> loadTaskBinaryConfigFromFile(String taskBinaryConfigFilePath) {
    String configPath;
    if ((taskBinaryConfigFilePath != null) && !(taskBinaryConfigFilePath.isEmpty())) {
      configPath = taskBinaryConfigFilePath;
    } else {
      configPath = System.getProperty("user.dir") + "/120-ng-manager";
    }
    String binaryConfigFile = configPath + "/task-binary-config.yaml";
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    try {
      byte[] yamlData = Files.readAllBytes(Paths.get(binaryConfigFile));
      return objectMapper.readValue(
          yamlData, objectMapper.getTypeFactory().constructCollectionType(List.class, TaskBinaryConfig.class));
    } catch (IOException e) {
      log.error("Failed to load Task Binary Config from file: {}", taskBinaryConfigFilePath, e);
    }
    return Collections.emptyList();
  }
}

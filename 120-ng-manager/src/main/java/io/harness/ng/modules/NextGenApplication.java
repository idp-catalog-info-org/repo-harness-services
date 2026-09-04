/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.modules;

import static io.harness.NGCommonEntityConstants.CONFIG_FILE_FUNCTOR;
import static io.harness.NGCommonEntityConstants.FILE_STORE_FUNCTOR;
import static io.harness.NGCommonEntityConstants.INSTANCE_FUNCTOR;
import static io.harness.accesscontrol.filter.NGScopeAccessCheckFilter.bypassAdminPortalAuth;
import static io.harness.accesscontrol.filter.NGScopeAccessCheckFilter.bypassInterMsvcRequests;
import static io.harness.accesscontrol.filter.NGScopeAccessCheckFilter.bypassInternalApi;
import static io.harness.accesscontrol.filter.NGScopeAccessCheckFilter.bypassPaths;
import static io.harness.accesscontrol.filter.NGScopeAccessCheckFilter.bypassPublicApi;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.ADMIN_PORTAL;
import static io.harness.authorization.AuthorizationServiceHeader.APPSEC_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.BEARER;
import static io.harness.authorization.AuthorizationServiceHeader.CV_NEXT_GEN;
import static io.harness.authorization.AuthorizationServiceHeader.DEFAULT;
import static io.harness.authorization.AuthorizationServiceHeader.GITOPS;
import static io.harness.authorization.AuthorizationServiceHeader.HARNESS_STATUSPAGE;
import static io.harness.authorization.AuthorizationServiceHeader.IDENTITY_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.authorization.AuthorizationServiceHeader.PLATFORM_CONFIG_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.RELICX;
import static io.harness.authorization.AuthorizationServiceHeader.RESOURCE_HIERARCHY_SERVICE;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.ng.config.NextGenConfiguration.HARNESS_RESOURCE_CLASSES;
import static io.harness.pms.contracts.plan.ExpansionRequestType.KEY;
import static io.harness.pms.expressions.functors.KubernetesReleaseFunctor.KUBERNETES_RELEASE_FUNCTOR_NAME;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import static com.codahale.metrics.servlets.AdminServlet.DEFAULT_HEALTHCHECK_URI;
import static com.google.common.collect.ImmutableMap.of;

import io.harness.EntityType;
import io.harness.Microservice;
import io.harness.ModuleType;
import io.harness.NgIteratorsConfig;
import io.harness.PipelineServiceUtilityModule;
import io.harness.SCMGrpcClientModule;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedExceptionMapper;
import io.harness.accesscontrol.filter.NGScopeAccessCheckFilter;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoFactory;
import io.harness.cache.CacheModule;
import io.harness.cdng.creator.CDNGModuleInfoProvider;
import io.harness.cdng.creator.CDNGPlanCreatorProvider;
import io.harness.cdng.creator.filters.CDNGFilterCreationResponseMerger;
import io.harness.cdng.driftdetection.DriftDetectionListenerRegistrar;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.beans.EnvironmentGroupWrapperConfig;
import io.harness.cdng.gitSync.EnvironmentGroupEntityGitSyncHelper;
import io.harness.cdng.gitops.rolloutstep.GitOpsRolloutInstanceHandler;
import io.harness.cdng.licenserestriction.ServiceRestrictionsUsageImpl;
import io.harness.cdng.migration.CDMigrationProvider;
import io.harness.cdng.orchestration.NgStepRegistrar;
import io.harness.cdng.pipeline.executions.events.CdngOrchestrationEventRedisConsumer;
import io.harness.cdng.pipeline.executions.events.CdngOrchestrationExecutionEventHandlerRegistrar;
import io.harness.cdng.pipeline.executions.events.backfill.CdBackfillOrchestrationEventRedisConsumer;
import io.harness.cdng.pipeline.executions.events.backfill.CdOrchestrationBackfillModule;
import io.harness.cdng.provision.terraform.functor.TerraformHumanReadablePlanFunctor;
import io.harness.cdng.provision.terraform.functor.TerraformPlanJsonFunctor;
import io.harness.cdng.provision.terraformcloud.functor.TerraformCloudPlanJsonFunctor;
import io.harness.cdng.provision.terraformcloud.functor.TerraformCloudPolicyChecksJsonFunctor;
import io.harness.cdng.usage.common.jobs.CDLicenseDailyReportIteratorHandler;
import io.harness.cdng.usage.common.task.CDLicenseDailyReportTask;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.changestreams.controllers.PlgEventConsumerController;
import io.harness.changestreams.kafka.ModuleLicensesKafkaConsumer;
import io.harness.changestreams.redisconsumers.ModuleLicensesRedisEventConsumer;
import io.harness.circuitbreaker.utils.CircuitBreakerRegistrationUtils;
import io.harness.configuration.DeployMode;
import io.harness.configuration.DeployVariant;
import io.harness.connector.ConnectorRestrictionUsageImpl;
import io.harness.connector.SecretManagerRestrictionUsageImpl;
import io.harness.controller.PrimaryVersionChangeScheduler;
import io.harness.credit.schedular.CICreditExpiryIteratorHandler;
import io.harness.credit.schedular.ProvisionMonthlyCICreditsHandler;
import io.harness.credit.schedular.SendProvisionedCICreditsToSegmentHandler;
import io.harness.delay.DelayEventListener;
import io.harness.dropwizard.bundles.swagger.SwaggerBundleConfiguration;
import io.harness.dropwizard.bundles.swagger.SwaggerV2Bundle;
import io.harness.enforcement.client.custom.CustomRestrictionInterface;
import io.harness.enforcement.client.example.ExampleCustomImpl;
import io.harness.enforcement.client.example.ExampleRateLimitUsageImpl;
import io.harness.enforcement.client.example.ExampleStaticLimitUsageImpl;
import io.harness.enforcement.client.servicedependencies.CustomRestrictionRegisterConfiguration;
import io.harness.enforcement.client.servicedependencies.RestrictionUsageRegisterConfiguration;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.executions.DeploymentRestrictionUsageImpl;
import io.harness.enforcement.executions.DeploymentsPerMonthRestrictionUsageImpl;
import io.harness.enforcement.executions.InitialDeploymentRestrictionUsageImpl;
import io.harness.enforcement.services.FeatureRestrictionLoader;
import io.harness.events.base.GitOpsEventConsumerController;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.exception.MongoExecutionTimeoutExceptionMapper;
import io.harness.ff.FeatureFlagConfig;
import io.harness.file.FileServiceConfiguration;
import io.harness.filter.HttpServiceLoopDetectionFilter;
import io.harness.filter.LoopDetectionAndPrevention;
import io.harness.filter.task.AddUniqueIdParentIdToFilterEntitiesJob;
import io.harness.freeze.FreezeNotificationTemplateRegistrar;
import io.harness.gitsync.AbstractGitSyncModule;
import io.harness.gitsync.GitSdkConfiguration;
import io.harness.gitsync.GitSyncSdkInitHelper;
import io.harness.gitsync.core.fullsync.GitFullSyncEntityIterator;
import io.harness.gitsync.core.runnable.GitChangeSetRunnable;
import io.harness.gitsync.core.webhook.GitSyncEventConsumerService;
import io.harness.gitsync.core.webhook.createbranchevent.WebhookBranchHookEventQueueProcessor;
import io.harness.gitsync.core.webhook.pushevent.WebhookPushEventQueueProcessor;
import io.harness.gitsync.events.AbstractGitSyncSdkModule;
import io.harness.gitsync.gitxwebhooks.listener.GitXWebhookEventValidationQueueProcessor;
import io.harness.gitsync.gitxwebhooks.listener.GitXWebhookPRQueueProcessor;
import io.harness.gitsync.gitxwebhooks.listener.GitXWebhookQueueProcessor;
import io.harness.gitsync.gitxwebhooks.listener.WebhookGitXPushEventQueueProcessor;
import io.harness.gitsync.gitxwebhooks.observer.GitXWebhookEventObserverImpl;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventServiceImpl;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookPullRequestEventServiceImpl;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventService;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookPullRequestEventService;
import io.harness.gitsync.migration.GitSyncMigrationProvider;
import io.harness.gitsync.migration.GitXWebhookRepoUrlMigrationProvider;
import io.harness.gitsync.sdk.GitSyncEntitiesConfiguration;
import io.harness.gitsync.sdk.GitSyncSdkConfiguration;
import io.harness.gitsync.server.GitSyncGrpcModule;
import io.harness.gitsync.server.GitSyncServiceConfiguration;
import io.harness.govern.ProviderModule;
import io.harness.governance.DefaultConnectorRefExpansionHandler;
import io.harness.grpc.interceptor.GrpcServiceLoopDetectionModule;
import io.harness.health.HealthService;
import io.harness.health.HealthServlet;
import io.harness.iterator.InstanceSyncTaskHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.module.InstanceSyncIteratorModule;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.ldap.handler.NGLDAPGroupScheduledHandler;
import io.harness.licensing.beans.modules.SMPEncLicenseDTO;
import io.harness.licensing.migrations.LicenseManagerMigrationProvider;
import io.harness.licensing.services.LicenseService;
import io.harness.logstreaming.LogStreamingModule;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.MetricRegistryModule;
import io.harness.metrics.jobs.RecordMetricsJob;
import io.harness.metrics.modules.PrometheusMetricsModule;
import io.harness.metrics.service.api.MetricService;
import io.harness.migration.ng.MigrationProvider;
import io.harness.migration.ng.NGMigrationConfiguration;
import io.harness.migration.ng.NGMigrationSdkInitHelper;
import io.harness.migration.ng.NGMigrationSdkModule;
import io.harness.migrations.InstanceMigrationProvider;
import io.harness.migrations.timescale.job.PopulateUniqueIdAndParentUniqueIdInNgInstanceStatsJob;
import io.harness.modules.LicenseManagerApplication;
import io.harness.ng.CoreGitEntityOrderComparator;
import io.harness.ng.GenerateOpenApiSpecCommand;
import io.harness.ng.InspectCommand;
import io.harness.ng.ScanClasspathMetadataCommand;
import io.harness.ng.ait.AitNotificationTemplateRegistrar;
import io.harness.ng.chaos.ChaosNotificationTemplateRegistrar;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.DefaultUserGroupsCreationJob;
import io.harness.ng.core.EtagFilter;
import io.harness.ng.core.TraceFilter;
import io.harness.ng.core.event.consumer.UnifiedPipelineEventConsumer;
import io.harness.ng.core.event.consumer.UnifiedPipelineEventConsumerModule;
import io.harness.ng.core.event.service.LicenseUsageHourlyDailyHandler;
import io.harness.ng.core.event.service.LicenseUsageMonthlyYearlyHandler;
import io.harness.ng.core.event.service.LicenseUsageScheduler;
import io.harness.ng.core.event.service.NGEventConsumerService;
import io.harness.ng.core.exceptionmappers.BadRequestExceptionMapper;
import io.harness.ng.core.exceptionmappers.FileNotFoundExceptionMapper;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.NotFoundExceptionMapper;
import io.harness.ng.core.exceptionmappers.NotSupportedExceptionMapper;
import io.harness.ng.core.exceptionmappers.OptimisticLockingFailureExceptionMapper;
import io.harness.ng.core.exceptionmappers.QueryParamExceptionMapper;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.ng.core.filter.ApiResponseFilter;
import io.harness.ng.core.handler.freezeHandlers.NgDeploymentFreezeActivationHandler;
import io.harness.ng.core.iterator.TokenExpiryAlertIterator;
import io.harness.ng.core.licenserestriction.OrgRestrictionsUsageImpl;
import io.harness.ng.core.licenserestriction.ProjectRestrictionsUsageImpl;
import io.harness.ng.core.metrics.ProjectEntityMigrationMetricsConfig;
import io.harness.ng.core.metrics.ProjectMovementTimescaleDbMigrationMetricsConfig;
import io.harness.ng.core.migration.AddUniqueIdParentIdToCdcEntitiesJob;
import io.harness.ng.core.migration.AddUniqueIdParentIdToEntitiesJob;
import io.harness.ng.core.migration.DeDuplicateUserGroupsJob;
import io.harness.ng.core.migration.FixConnectorScopeForPipelineSetupUsageJob;
import io.harness.ng.core.migration.NGBeanMigrationProvider;
import io.harness.ng.core.migration.OrphanUserGroupsCleanupJob;
import io.harness.ng.core.migration.ParentUniqueIdMigrationProvider;
import io.harness.ng.core.migration.ProjectEntityMigrationMetricsJob;
import io.harness.ng.core.migration.ProjectMigrationProvider;
import io.harness.ng.core.migration.ProjectMovementTimescaleDbMigrationMetricsJob;
import io.harness.ng.core.migration.ServiceUniqueIdBackfillJob;
import io.harness.ng.core.migration.UniqueIdParentIdMigrationProvider;
import io.harness.ng.core.migration.UserGroupMigrationProvider;
import io.harness.ng.core.migration.timescale.AddParentUniqueIdForTimescaleTableJob;
import io.harness.ng.core.remote.UserGroupRestrictionUsageImpl;
import io.harness.ng.core.remote.UsersRestrictionUsageImpl;
import io.harness.ng.core.remote.licenserestriction.ApiKeyRestrictionsUsageImpl;
import io.harness.ng.core.remote.licenserestriction.ApiTokenRestrictionUsageImpl;
import io.harness.ng.core.remote.licenserestriction.CloudCostK8sConnectorRestrictionsUsageImpl;
import io.harness.ng.core.remote.licenserestriction.ResourceGroupRestrictionUsageImpl;
import io.harness.ng.core.remote.licenserestriction.RoleRestrictionUsageImpl;
import io.harness.ng.core.remote.licenserestriction.SecretRestrictionUsageImpl;
import io.harness.ng.core.remote.licenserestriction.ServiceAccountRestrictionUsageImpl;
import io.harness.ng.core.remote.licenserestriction.VariableRestrictionUsageImpl;
import io.harness.ng.core.scheduler.NGLdapMigrationScheduler;
import io.harness.ng.core.scheduler.OrganizationBillingMetricJob;
import io.harness.ng.core.scheduler.ScopedPermissionsBackfillService;
import io.harness.ng.core.scheduler.SendAccountStatisticsToSegmentTask;
import io.harness.ng.core.scheduler.TokenExpirationService;
import io.harness.ng.core.user.exception.mapper.InvalidUserRemoveRequestExceptionMapper;
import io.harness.ng.core.variable.expressions.functors.VariableFunctor;
import io.harness.ng.gitops.changestreams.GitOpsUtilizationSnapshotRedisEventConsumer;
import io.harness.ng.gitops.changestreams.GitopsApplicationsRedisEventConsumer;
import io.harness.ng.gitops.config.CdcKafkaConfig;
import io.harness.ng.gitops.config.CdcKafkaConstants;
import io.harness.ng.gitops.config.CdcKafkaConsumerConfig;
import io.harness.ng.gitops.kafka.GitOpsUtilizationSnapshotKafkaConsumer;
import io.harness.ng.gitops.kafka.GitopsApplicationsKafkaConsumer;
import io.harness.ng.har.HarNotificationTemplateRegistrar;
import io.harness.ng.iro.IRNotificationTemplateRegistrar;
import io.harness.ng.migration.ApiKeyAndTokenScopeSchemaMigrationProvider;
import io.harness.ng.migration.DatabaseSetupMigrationProvider;
import io.harness.ng.migration.DelegateMigrationProvider;
import io.harness.ng.migration.FmeFeatureFlagRoleMigrationService;
import io.harness.ng.migration.NGCoreMigrationProvider;
import io.harness.ng.migration.SecretsCreateEditRoleMigrationService;
import io.harness.ng.migration.SourceCodeManagerMigrationProvider;
import io.harness.ng.migration.UniqueIdParentUniqueIdMigrationProvider;
import io.harness.ng.migration.UserGroupsManageRoleMigrationJob;
import io.harness.ng.migration.UserMembershipMigrationProvider;
import io.harness.ng.migration.UserMetadataMigrationProvider;
import io.harness.ng.moduleversioninfo.runnable.ModuleVersionsMaintenanceTask;
import io.harness.ng.oauth.BitbucketSCMOAuthTokenRefresher;
import io.harness.ng.oauth.BitbucketServerSCMOAuthTokenRefresher;
import io.harness.ng.oauth.ConfluenceOAuthTokenRefresher;
import io.harness.ng.oauth.GitlabConnectorOAuthTokenRefresher;
import io.harness.ng.oauth.GitlabOnPremSCMOAuthTokenRefresher;
import io.harness.ng.oauth.GitlabSCMOAuthTokenRefresher;
import io.harness.ng.oauth.GoogleChatOAuthTokenRefresher;
import io.harness.ng.oauth.MsTeamsOAuthTokenRefresher;
import io.harness.ng.oauth.SlackOAuthTokenRefresher;
import io.harness.ng.oauth.ZoomOAuthTokenRefresher;
import io.harness.ng.overview.eventGenerator.DeploymentEventGenerator;
import io.harness.ng.privateconnectivity.PrivateConnectivityExecutorLifecycle;
import io.harness.ng.privateconnectivity.resources.PrivateConnectivityConflictExceptionMapper;
import io.harness.ng.privateconnectivity.sanitizer.ReleaseReconciler;
import io.harness.ng.serviceaccounts.notifications.ServiceAccountNotificationTemplateRegistrar;
import io.harness.ng.smp.resources.SMPVersionResource;
import io.harness.ng.sto.StoNotificationTemplateRegistrar;
import io.harness.ng.webhook.services.api.WebhookEventProcessingService;
import io.harness.ngsettings.settings.SettingsCreationJob;
import io.harness.ngsettings.settings.UserSettingsCreationJob;
import io.harness.ngsubscriptions.service.jobs.DailyAccountUsersEntityUpdateJob;
import io.harness.ngsubscriptions.service.jobs.TotalAccountUsersEntityUpdateJob;
import io.harness.observer.NoOpRemoteObserverInformerImpl;
import io.harness.observer.RemoteObserver;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.consumer.AbstractRemoteObserverModule;
import io.harness.outbox.eventpoll.OutboxEventPollService;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.plan.ExpansionRequestType;
import io.harness.pms.contracts.plan.InputsMetadataInfo;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PipelineEventConsumerController;
import io.harness.pms.expressions.functors.ConfigFileFunctor;
import io.harness.pms.expressions.functors.ConnectorFunctor;
import io.harness.pms.expressions.functors.DockerConfigJsonFunctor;
import io.harness.pms.expressions.functors.FileStoreFunctor;
import io.harness.pms.expressions.functors.ImagePullSecretFunctor;
import io.harness.pms.expressions.functors.InstanceFunctor;
import io.harness.pms.expressions.functors.KubernetesReleaseFunctor;
import io.harness.pms.governance.EnvironmentExpansionHandler;
import io.harness.pms.governance.EnvironmentGroupExpandedHandler;
import io.harness.pms.governance.EnvironmentRefExpansionHandler;
import io.harness.pms.governance.MultiEnvironmentExpansionHandler;
import io.harness.pms.governance.ServiceRefExpansionHandler;
import io.harness.pms.inputmetadata.ServiceInputsMetadataHandler;
import io.harness.pms.kafkaconsumer.PipelineExecutionSummaryCDKafkaConsumer;
import io.harness.pms.listener.NgOrchestrationNotifyEventListener;
import io.harness.pms.redisConsumer.PipelineExecutionSummaryCDRedisEventConsumer;
import io.harness.pms.sdk.PmsSdkInitHelper;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.configuration.PmsSdkConfiguration;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.core.execution.expression.sdk.SdkFunctor;
import io.harness.pms.sdk.core.governance.JsonExpansionHandlerInfo;
import io.harness.pms.sdk.core.handler.InputsMetadataHandlerInfo;
import io.harness.pms.sdk.execution.events.facilitators.FacilitatorEventRedisConsumer;
import io.harness.pms.sdk.execution.events.facilitators.FacilitatorEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.interrupts.InterruptEventRedisConsumer;
import io.harness.pms.sdk.execution.events.interrupts.InterruptEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.node.advise.NodeAdviseEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.advise.NodeAdviseRedisConsumerV2;
import io.harness.pms.sdk.execution.events.node.resume.NodeResumeEventConsumerV2;
import io.harness.pms.sdk.execution.events.node.resume.NodeResumeEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.start.NodeStartEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.start.NodeStartEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.plan.CreatePartialPlanRedisConsumer;
import io.harness.pms.sdk.execution.events.progress.NodeProgressEventRedisConsumerV2;
import io.harness.pms.sdk.execution.events.progress.ProgressEventRedisConsumer;
import io.harness.pms.serializer.json.PmsBeansJacksonModule;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.polling.service.impl.PollingPerpetualTaskManager;
import io.harness.polling.service.impl.PollingServiceImpl;
import io.harness.polling.service.impl.artifact.ArtifactPollingScheduledTaskManager;
import io.harness.polling.service.intfc.PollingService;
import io.harness.queue.QueueListenerController;
import io.harness.queue.QueuePublisher;
import io.harness.registrars.CDServiceAdviserRegistrar;
import io.harness.request.RequestContextFilter;
import io.harness.resilience.utils.BulkheadRegistrationUtils;
import io.harness.resource.VersionInfoResource;
import io.harness.runnable.InstanceAccountInfoRunnable;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.secret.ConfigSecretUtils;
import io.harness.secretmanager.handler.NGVaultSecretManagerRenewalHandler;
import io.harness.security.InternalApiAuthFilter;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.OIDCContextFilter;
import io.harness.security.ScopeInfoFilter;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.PublicApi;
import io.harness.security.mesh.MeshIdentityConfig;
import io.harness.service.deploymentevent.DeploymentEventListenerRegistrar;
import io.harness.service.impl.DelegateAsyncServiceImpl;
import io.harness.service.impl.DelegateProgressServiceImpl;
import io.harness.service.impl.DelegateSyncServiceImpl;
import io.harness.service.instancesyncperpetualtask.InvalidInstanceSyncPerpetualTaskHandler;
import io.harness.service.stats.billing.CDBillingMetricJob;
import io.harness.service.stats.statscollector.InstanceStatsIteratorHandler;
import io.harness.springdata.HMongoTemplate;
import io.harness.telemetry.NGTelemetryRecordsJob;
import io.harness.telemetry.TelemetryReporter;
import io.harness.telemetry.filter.APIAuthTelemetryFilter;
import io.harness.telemetry.filter.APIAuthTelemetryResponseFilter;
import io.harness.telemetry.filter.APIErrorsTelemetrySenderFilter;
import io.harness.telemetry.filter.TerraformTelemetryFilter;
import io.harness.telemetry.service.CdTelemetryRecordsJob;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ScalingThreadPoolExecutor;
import io.harness.threading.ThreadPoolConfig;
import io.harness.timescale.CDRetentionHandlerNG;
import io.harness.token.remote.TokenClient;
import io.harness.tracing.MongoRedisTracer;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.NotifierScheduledExecutorService;
import io.harness.waiter.NotifyEvent;
import io.harness.waiter.NotifyQueuePublisherRegister;
import io.harness.waiter.NotifyResponseCleaner;
import io.harness.waiter.ProgressUpdateService;
import io.harness.waiter.nrcsp.NotifyResponseCleanerFactory;
import io.harness.waiter.nrcsp.NotifyResponseCleanerSpringPersistence;
import io.harness.waiter.nrcsp.NotifyResponseIterator;
import io.harness.waiter.persistence.WaitNotifyCollectionNameResolver;
import io.harness.yaml.YamlSdkConfiguration;
import io.harness.yaml.YamlSdkInitHelper;

import software.wings.app.CharsetResponseFilter;
import software.wings.jersey.KryoFeature;
import software.wings.service.impl.FileJobsService;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.serializer.HObjectMapper;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import java.lang.annotation.Annotation;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.model.Resource;
import org.springframework.data.mongodb.core.MongoTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PL)
@Slf4j
public class NextGenApplication extends Application<NextGenConfiguration> {
  private static final SecureRandom random = new SecureRandom();
  private static final String APPLICATION_NAME = "CD NextGen Application";

  private final MetricRegistry metricRegistry = new MetricRegistry();
  private final MetricRegistry threadPoolMetricRegistry = new MetricRegistry();

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.warn("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new NextGenApplication().run(args);
  }

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<NextGenConfiguration> bootstrap) {
    initializeLogging();
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addCommand(new ScanClasspathMetadataCommand());
    bootstrap.addCommand(new GenerateOpenApiSpecCommand());
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    configureObjectMapper(bootstrap.getObjectMapper());
    bootstrap.addBundle(new SwaggerV2Bundle<>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(final NextGenConfiguration appConfig) {
        return appConfig.getSwaggerBundleConfiguration();
      }

      @Override
      protected Collection<Class<?>> getResourceClasses() {
        return HARNESS_RESOURCE_CLASSES;
      }
    });
    bootstrap.setMetricRegistry(metricRegistry);
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    HObjectMapper.configureObjectMapperForNG(mapper);
    mapper.registerModule(new PmsBeansJacksonModule());
  }

  @Override
  public void run(NextGenConfiguration appConfig, Environment environment) throws Exception {
    log.info("Entering startup maintenance mode");
    MaintenanceController.forceMaintenance(true);
    environment.lifecycle().addServerLifecycleListener(server -> {
      log.info("Leaving startup maintenance mode");
      MaintenanceController.forceMaintenance(false);
    });

    log.info("Starting Next Gen Application ...");

    ConfigSecretUtils.resolveSecrets(appConfig.getSecretsConfiguration(), appConfig);

    int corePoolSize = Runtime.getRuntime().availableProcessors();
    int maxPoolSize = Runtime.getRuntime().availableProcessors() * 20;
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build();
    ExecutorModule.getInstance().setExecutorService(
        new ScalingThreadPoolExecutor(ThreadPoolConfig.builder()
                                          .corePoolSize(corePoolSize)
                                          .maxPoolSize(maxPoolSize)
                                          .idleTime(appConfig.getCommonPoolConfig().getIdleTime())
                                          .timeUnit(appConfig.getCommonPoolConfig().getTimeUnit())
                                          .build(),
            threadFactory));
    MaintenanceController.forceMaintenance(true);
    CircuitBreakerRegistrationUtils.register("ng-manager", appConfig.getCircuitBreakerConfiguration());
    BulkheadRegistrationUtils.register("ng-manager", appConfig.getBulkheadConfiguration());
    List<Module> modules = new ArrayList<>();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(MetricRegistry.class).toInstance(metricRegistry);
      }
    });
    modules.add(new MetricRegistryModule(metricRegistry, threadPoolMetricRegistry));
    PrometheusMetricsModule prometheusMetricsModule = new PrometheusMetricsModule();
    modules.add(prometheusMetricsModule);
    modules.add(new NextGenModule(appConfig));
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      public GitSyncServiceConfiguration gitSyncServiceConfiguration() {
        return GitSyncServiceConfiguration.builder().grpcServerConfig(appConfig.getGitSyncGrpcServerConfig()).build();
      }

      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return appConfig.getDbAliases();
      }

      @Provides
      @Singleton
      public FileServiceConfiguration getFileServiceConfiguration() {
        return appConfig.getFileServiceConfiguration();
      }
    });
    modules.add(NGMigrationSdkModule.getInstance());
    modules.add(new LogStreamingModule(appConfig.getLogStreamingServiceConfig().getBaseUrl()));
    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return appConfig.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return appConfig.getFeatureFlagConfig();
      }
    });
    if (appConfig.getShouldDeployWithGitSync()) {
      modules.add(GitSyncGrpcModule.getInstance());
      GitSyncSdkConfiguration gitSyncSdkConfiguration = getGitSyncConfiguration(appConfig);
      modules.add(new AbstractGitSyncSdkModule() {
        @Override
        public GitSyncSdkConfiguration getGitSyncSdkConfiguration() {
          return gitSyncSdkConfiguration;
        }
      });
      modules.add(new AbstractGitSyncModule() {
        @Override
        public EventsFrameworkConfiguration getEventsFrameworkConfiguration() {
          return appConfig.getEventsFrameworkConfiguration();
        }
      });
    } else {
      modules.add(new SCMGrpcClientModule(appConfig.getGitSdkConfiguration().getScmConnectionConfig()));
    }
    modules.add(new AbstractRemoteObserverModule() {
      @Override
      public boolean noOpProducer() {
        return true;
      }

      @Override
      public Set<RemoteObserver> observers() {
        return Collections.emptySet();
      }

      @Override
      public Class<? extends RemoteObserverInformer> getRemoteObserverImpl() {
        return NoOpRemoteObserverInformerImpl.class;
      }
    });
    if (appConfig.getInstanceSyncIteratorConfig() != null) {
      var iteratorConfig = appConfig.getInstanceSyncIteratorConfig();
      if (iteratorConfig.isEnabled()) {
        modules.add(InstanceSyncIteratorModule.getInstance(iteratorConfig));
      }
    }

    // Pipeline Service Modules
    PmsSdkConfiguration pmsSdkConfiguration = getPmsSdkConfiguration(appConfig);
    modules.add(PmsSdkModule.getInstance(pmsSdkConfiguration, threadPoolMetricRegistry, false));
    modules.add(PipelineServiceUtilityModule.getInstance());

    CacheModule cacheModule = new CacheModule(appConfig.getCacheConfig());
    modules.add(cacheModule);
    LoopDetectionAndPrevention loopDetectionAndPrevention =
        new LoopDetectionAndPrevention(prometheusMetricsModule.metricService);
    modules.add(new GrpcServiceLoopDetectionModule(loopDetectionAndPrevention));
    modules.add(KafkaModule.getInstance(appConfig.getKafkaModuleConfig()));
    modules.add(UnifiedPipelineEventConsumerModule.getInstance());
    modules.add(CdOrchestrationBackfillModule.getInstance(appConfig.getBackfillOrchestrationEventHandlerPoolConfig()));

    Injector injector = Guice.createInjector(modules);

    // Will create collections and Indexes
    injector.getInstance(HPersistence.class);
    registerCorsFilter(appConfig, environment);
    registerResources(appConfig, environment, injector);
    registerJerseyProviders(environment, injector);
    registerJerseyFeatures(environment);
    registerCharsetResponseFilter(environment, injector);
    registerApiResponseFilter(environment, injector);
    registerCorrelationFilter(environment, injector);
    registerEtagFilter(environment, injector);
    registerScheduleJobs(injector, appConfig.isEnableWaitNotifyEngineOptimisation());
    registerWaitEnginePublishers(injector);
    registerAPIAuthTelemetryFilters(appConfig, environment, injector);
    registerAuthFilters(appConfig, environment, injector);
    registerScopeAccessCheckFilter(appConfig, environment, injector);
    registerRequestContextFilter(environment);
    registerPipelineSDK(appConfig, injector, environment);
    registerYamlSdk(injector);
    registerLicenseUsageScheduler(injector);
    registerHealthCheck(environment, injector);
    registerIterators(appConfig.getNgIteratorsConfig(), injector, appConfig);
    if (appConfig.getTokenExpiryAlertIteratorConfig() != null
        && appConfig.getTokenExpiryAlertIteratorConfig().isEnabled()) {
      injector.getInstance(TokenExpiryAlertIterator.class).registerIterator();
    }
    registerJobs(injector);
    registerQueueListeners(injector);
    InstanceSyncIteratorModule.getInstance(appConfig.getInstanceSyncIteratorConfig()).registerQueueListeners(injector);
    InstanceSyncIteratorModule.getInstance(appConfig.getInstanceSyncIteratorConfig())
        .registerWaitEnginePublishers(injector);
    if (!appConfig.isDisableFreezeNotificationTemplate()) {
      registerNotificationTemplates(injector);
    }
    if (!appConfig.isDisableChaosNotificationTemplate()) {
      registerChaosNotificationTemplates(injector);
    }

    if (!appConfig.isDisableIRNotificationTemplate()) {
      registerIRNotificationTemplates(injector);
    }

    if (!appConfig.isDisableStoNotificationTemplate()) {
      registerStoNotificationTemplates(injector);
    }

    if (!appConfig.isDisableHarNotificationTemplate()) {
      registerHarNotificationTemplates(injector);
    }

    if (!appConfig.isDisableAitNotificationTemplate()) {
      registerAitNotificationTemplates(injector);
    }

    if (!appConfig.isDisableServiceAccountNotificationTemplate()) {
      registerServiceAccountNotificationTemplates(injector);
    }

    registerPmsSdkEvents(appConfig, injector);
    registerDebeziumEvents(appConfig, injector);
    initializeMonitoring(injector);
    registerObservers(injector);
    registerOasResource(appConfig, environment, injector);
    registerManagedBeans(environment, injector, appConfig);
    registerGitOpsKafkaConsumers(injector, appConfig, environment);
    registerGtmKafkaConsumers(injector, appConfig, environment);
    registerPipelineExecutionSummaryCDKafkaConsumer(injector, appConfig, environment);
    environment.jersey().getResourceConfig().register(new AbstractBinder() {
      @Override
      protected void configure() {
        bindFactory(ScopeInfoFactory.class).to(ScopeInfo.class);
      }
    });
    environment.jersey().register(
        new ScopeInfoFilter(injector.getInstance(Key.get(ScopeInfoClient.class, Names.named("PRIVILEGED"))),
            injector.getInstance(PmsFeatureFlagService.class)));
    environment.jersey().register(new OIDCContextFilter());
    initializeEnforcementService(injector, appConfig);
    initializeEnforcementSdk(injector);
    initializeCdMonitoring(appConfig, injector);
    SettingsCreationJob settingsCreationJob = injector.getInstance(SettingsCreationJob.class);
    settingsCreationJob.run();

    UserSettingsCreationJob userSettingsCreationJob = injector.getInstance(UserSettingsCreationJob.class);
    userSettingsCreationJob.run();

    if (appConfig.getShouldDeployWithGitSync()) {
      intializeGitSync(injector);
      GitSyncSdkInitHelper.initGitSyncSdk(injector, environment, getGitSyncConfiguration(appConfig));
    }
    registerMigrations(injector);
    injector.getInstance(CDRetentionHandlerNG.class).configureRetentionPolicy();

    if (BooleanUtils.isTrue(appConfig.getEnableOpentelemetry())) {
      registerTraceFilter(environment, injector);
    }

    if (loopDetectionAndPrevention.shouldLoopBeDetected()) {
      registerHttpServiceLoopDetectionFilter(appConfig, environment, loopDetectionAndPrevention);
    }

    log.info("NextGenApplication DEPLOY_VERSION = " + System.getenv().get(DEPLOY_VERSION));
    if (DeployVariant.isCommunity(System.getenv().get(DEPLOY_VERSION))) {
      initializeNGMonitoring(appConfig, injector);
    } else {
      log.info("NextGenApplication DEPLOY_VERSION is not COMMUNITY");
    }
    if (shouldCheckForSMPLicense()) {
      log.info("Applying smp license");
      LicenseService licenseService = injector.getInstance(LicenseService.class);
      String license = System.getenv("SMP_LICENSE");
      SMPEncLicenseDTO encLicenseDTO = SMPEncLicenseDTO.builder().encryptedLicense(license).decrypt(true).build();
      licenseService.applySMPLicense(encLicenseDTO);
    }
    // Start DevOps Essentials Processing
    // TODO - this will be moved to a separate License Manager Service
    if (appConfig.isEnableLicenseManager()) {
      LicenseManagerApplication licenseManagerApplication = injector.getInstance(LicenseManagerApplication.class);
      String jobName = "License-Manager-Application";
      ScheduledThreadPoolExecutor executor =
          new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(jobName).build());
      long initialDelay = 10;
      executor.schedule(licenseManagerApplication::startDevOpsEssentials, initialDelay, TimeUnit.SECONDS);
    }
  }

  // ToDo-SMP: enable for future releases only for now (add condition on release tag)
  private boolean shouldCheckForSMPLicense() {
    return DeployMode.isOnPrem(System.getenv(DeployMode.DEPLOY_MODE))
        && Boolean.parseBoolean(System.getenv("ENABLE_SMP_LICENSING"));
  }

  private void registerNotificationTemplates(Injector injector) {
    ExecutorService executorService =
        injector.getInstance(Key.get(ExecutorService.class, Names.named("freezeTemplateRegistrationExecutorService")));
    executorService.submit(injector.getInstance(FreezeNotificationTemplateRegistrar.class));
  }

  private void registerChaosNotificationTemplates(Injector injector) {
    ExecutorService executorService =
        injector.getInstance(Key.get(ExecutorService.class, Names.named("chaosTemplateRegistrationExecutorService")));
    executorService.submit(injector.getInstance(ChaosNotificationTemplateRegistrar.class));
  }

  private void registerIRNotificationTemplates(Injector injector) {
    ExecutorService executorService =
        injector.getInstance(Key.get(ExecutorService.class, Names.named("irTemplateRegistrationExecutorService")));
    executorService.submit(injector.getInstance(IRNotificationTemplateRegistrar.class));
  }

  private void registerStoNotificationTemplates(Injector injector) {
    ExecutorService executorService =
        injector.getInstance(Key.get(ExecutorService.class, Names.named("stoTemplateRegistrationExecutorService")));
    executorService.submit(injector.getInstance(StoNotificationTemplateRegistrar.class));
  }

  private void registerHarNotificationTemplates(Injector injector) {
    ExecutorService executorService =
        injector.getInstance(Key.get(ExecutorService.class, Names.named("harTemplateRegistrationExecutorService")));
    executorService.submit(injector.getInstance(HarNotificationTemplateRegistrar.class));
  }

  private void registerAitNotificationTemplates(Injector injector) {
    ExecutorService executorService =
        injector.getInstance(Key.get(ExecutorService.class, Names.named("aitTemplateRegistrationExecutorService")));
    executorService.submit(injector.getInstance(AitNotificationTemplateRegistrar.class));
  }

  private void registerServiceAccountNotificationTemplates(Injector injector) {
    ExecutorService executorService = injector.getInstance(
        Key.get(ExecutorService.class, Names.named("serviceAccountTemplateRegistrationExecutorService")));
    executorService.submit(injector.getInstance(ServiceAccountNotificationTemplateRegistrar.class));
  }

  private void initializeNGMonitoring(NextGenConfiguration appConfig, Injector injector) {
    log.info("Initializing NGMonitoring");
    injector.getInstance(NGTelemetryRecordsJob.class).scheduleTasks();
  }

  private void initializeCdMonitoring(NextGenConfiguration appConfig, Injector injector) {
    log.info("Initializing Cd Monitoring");
    CdTelemetryRecordsJob cdTelemetryRecordsJob = injector.getInstance(CdTelemetryRecordsJob.class);
    cdTelemetryRecordsJob.scheduleTasks();
    Runtime.getRuntime().addShutdownHook(new Thread(cdTelemetryRecordsJob::flushAllTelemetry));
  }

  private void registerOasResource(NextGenConfiguration appConfig, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(appConfig.getOasConfig());
    environment.jersey().register(openApiResource);
  }

  private void registerHttpServiceLoopDetectionFilter(
      NextGenConfiguration appConfig, Environment environment, LoopDetectionAndPrevention loopDetectionAndPrevention) {
    log.info("Initializing HttpServiceLoopDetectionFilter with threshold {} and loop prevention {}",
        loopDetectionAndPrevention.getLoopDetectionThreshold(), loopDetectionAndPrevention.isEnableLoopPrevention());
    HttpServiceLoopDetectionFilter filterInstance = new HttpServiceLoopDetectionFilter(loopDetectionAndPrevention);
    environment.servlets()
        .addFilter("HttpServiceLoopDetectionFilter", filterInstance)
        .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
    environment.jersey().register(filterInstance);
  }

  private void registerQueueListeners(Injector injector) {
    log.info("Initializing queue listeners...");
    QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    queueListenerController.register(injector.getInstance(DelayEventListener.class), 1);
    queueListenerController.register(injector.getInstance(NgOrchestrationNotifyEventListener.class), 1);
  }

  private static void registerObservers(Injector injector) {
    // register Polling Framework Observers
    PollingServiceImpl pollingService = (PollingServiceImpl) injector.getInstance(Key.get(PollingService.class));
    pollingService.getSubject().register(injector.getInstance(Key.get(PollingPerpetualTaskManager.class)));
    pollingService.getSubject().register(injector.getInstance(Key.get(ArtifactPollingScheduledTaskManager.class)));

    GitXWebhookEventServiceImpl gitXWebhookEventService =
        (GitXWebhookEventServiceImpl) injector.getInstance(Key.get(GitXWebhookEventService.class));
    gitXWebhookEventService.getGitXWebhookEventUpdateSubject().register(
        injector.getInstance(Key.get(GitXWebhookEventObserverImpl.class)));

    GitXWebhookPullRequestEventServiceImpl gitXWebhookPullRequestEventService =
        (GitXWebhookPullRequestEventServiceImpl) injector.getInstance(
            Key.get(GitXWebhookPullRequestEventService.class));
    gitXWebhookPullRequestEventService.getGitXWebhookEventUpdateSubject().register(
        injector.getInstance(Key.get(GitXWebhookEventObserverImpl.class)));

    HMongoTemplate mongoTemplate = (HMongoTemplate) injector.getInstance(MongoTemplate.class);
    mongoTemplate.getTracerSubject().register(injector.getInstance(MongoRedisTracer.class));
  }

  private void registerMigrations(Injector injector) {
    NGMigrationConfiguration config = getMigrationSdkConfiguration();
    NGMigrationSdkInitHelper.initialize(injector, config);
  }

  private NGMigrationConfiguration getMigrationSdkConfiguration() {
    return NGMigrationConfiguration.builder()
        .microservice(Microservice.CORE)
        .migrationProviderList(new ArrayList<Class<? extends MigrationProvider>>() {
          { add(DatabaseSetupMigrationProvider.class); }

          { add(NGBeanMigrationProvider.class); }

          { add(ProjectMigrationProvider.class); }

          { add(UniqueIdParentIdMigrationProvider.class); }

          { add(ParentUniqueIdMigrationProvider.class); }

          { add(UniqueIdParentUniqueIdMigrationProvider.class); }

          { add(NGCoreMigrationProvider.class); } // Add all migration provider classes here

          { add(UserMembershipMigrationProvider.class); }

          { add(InstanceMigrationProvider.class); }

          { add(UserMetadataMigrationProvider.class); }

          { add(LicenseManagerMigrationProvider.class); }

          { add(SourceCodeManagerMigrationProvider.class); }

          { add(GitSyncMigrationProvider.class); }

          { add(DelegateMigrationProvider.class); }

          { add(UserGroupMigrationProvider.class); }

          { add(CDMigrationProvider.class); }

          { add(GitXWebhookRepoUrlMigrationProvider.class); }

          { add(ApiKeyAndTokenScopeSchemaMigrationProvider.class); }
        })
        .build();
  }

  private void initializeMonitoring(Injector injector) {
    injector.getInstance(MetricService.class).initializeMetrics();
    injector.getInstance(RecordMetricsJob.class).scheduleMetricsTasks();
  }

  private GitSyncSdkConfiguration getGitSyncConfiguration(NextGenConfiguration config) {
    final Supplier<List<EntityType>> sortOrder = () -> CoreGitEntityOrderComparator.sortOrder;
    ObjectMapper ngObjectMapper = new ObjectMapper(new YAMLFactory());
    configureObjectMapper(ngObjectMapper);
    Set<GitSyncEntitiesConfiguration> gitSyncEntitiesConfigurations = new HashSet<>();

    gitSyncEntitiesConfigurations.add(GitSyncEntitiesConfiguration.builder()
                                          .entityType(EntityType.ENVIRONMENT_GROUP)
                                          .yamlClass(EnvironmentGroupWrapperConfig.class)
                                          .entityClass(EnvironmentGroupEntity.class)
                                          .entityHelperClass(EnvironmentGroupEntityGitSyncHelper.class)
                                          .build());
    final GitSdkConfiguration gitSdkConfiguration = config.getGitSdkConfiguration();
    return GitSyncSdkConfiguration.builder()
        .gitSyncSortOrder(sortOrder)
        .grpcClientConfig(gitSdkConfiguration.getGitManagerGrpcClientConfig())
        // In process server so server config not required.
        //        .grpcServerConfig(config.getGitSyncGrpcServerConfig())
        .deployMode(GitSyncSdkConfiguration.DeployMode.IN_PROCESS)
        .microservice(Microservice.CORE)
        .scmConnectionConfig(gitSdkConfiguration.getScmConnectionConfig())
        .eventsFrameworkConfiguration(config.getEventsFrameworkConfiguration())
        .serviceHeader(NG_MANAGER)
        .gitSyncEntitiesConfiguration(gitSyncEntitiesConfigurations)
        .gitSyncEntitySortComparator(CoreGitEntityOrderComparator.class)
        .objectMapper(ngObjectMapper)
        .build();
  }

  private void registerRequestContextFilter(Environment environment) {
    environment.jersey().register(new RequestContextFilter());
    environment.jersey().register(new JsonProcessingExceptionMapper(true));
  }

  private void intializeGitSync(Injector injector) {
    log.info("Initializing gRPC server for git sync...");
    ServiceManager serviceManager =
        injector.getInstance(Key.get(ServiceManager.class, Names.named("git-sync"))).startAsync();
    serviceManager.awaitHealthy();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> serviceManager.stopAsync().awaitStopped()));
    log.info("Git Sync SDK registration complete.");
  }

  public void registerIterators(
      NgIteratorsConfig ngIteratorsConfig, Injector injector, NextGenConfiguration appConfig) {
    injector.getInstance(NGVaultSecretManagerRenewalHandler.class)
        .registerIterators(ngIteratorsConfig.getNgVaultSecretManagerRenewalIteratorConfig().getThreadPoolSize());
    injector.getInstance(WebhookEventProcessingService.class)
        .registerIterators(ngIteratorsConfig.getWebhookEventProcessingServiceIteratorConfig().getThreadPoolSize());
    injector.getInstance(InstanceStatsIteratorHandler.class).registerIterators();
    injector.getInstance(GitFullSyncEntityIterator.class)
        .registerIterators(ngIteratorsConfig.getGitFullSyncEntityIteratorConfig().getThreadPoolSize());
    injector.getInstance(NgDeploymentFreezeActivationHandler.class).registerIterators(5);
    injector.getInstance(GitOpsRolloutInstanceHandler.class)
        .registerIterators(ngIteratorsConfig.getGitOpsRolloutInstanceIteratorConfig());
    injector.getInstance(GitlabConnectorOAuthTokenRefresher.class)
        .registerIterators(ngIteratorsConfig.getOauthTokenRefreshIteratorConfig().getThreadPoolSize());
    injector.getInstance(GitlabSCMOAuthTokenRefresher.class)
        .registerIterators(ngIteratorsConfig.getOauthTokenRefreshIteratorConfig().getThreadPoolSize());
    injector.getInstance(GitlabOnPremSCMOAuthTokenRefresher.class)
        .registerIterators(ngIteratorsConfig.getOauthTokenRefreshIteratorConfig().getThreadPoolSize());
    injector.getInstance(BitbucketSCMOAuthTokenRefresher.class)
        .registerIterators(ngIteratorsConfig.getOauthTokenRefreshIteratorConfig().getThreadPoolSize());
    injector.getInstance(BitbucketServerSCMOAuthTokenRefresher.class)
        .registerIterators(ngIteratorsConfig.getOauthTokenRefreshIteratorConfig().getThreadPoolSize());
    injector.getInstance(CDLicenseDailyReportIteratorHandler.class)
        .registerIterator(ngIteratorsConfig.getCdLicenseDailyReportIteratorConfig());
    injector.getInstance(InvalidInstanceSyncPerpetualTaskHandler.class).registerIterators(2);
    injector.getInstance(CICreditExpiryIteratorHandler.class).registerIterator(2);
    injector.getInstance(SendProvisionedCICreditsToSegmentHandler.class).registerIterator(2);
    injector.getInstance(ProvisionMonthlyCICreditsHandler.class).registerIterators(2);
    injector.getInstance(NGLDAPGroupScheduledHandler.class).registerIterators(5);
    injector.getInstance(ZoomOAuthTokenRefresher.class)
        .registerIterators(ngIteratorsConfig.getOauthTokenRefreshIteratorConfig().getThreadPoolSize());
    injector.getInstance(MsTeamsOAuthTokenRefresher.class)
        .registerIterators(ngIteratorsConfig.getOauthTokenRefreshIteratorConfig().getThreadPoolSize());
    injector.getInstance(ConfluenceOAuthTokenRefresher.class)
        .registerIterators(ngIteratorsConfig.getOauthTokenRefreshIteratorConfig().getThreadPoolSize());
    injector.getInstance(SlackOAuthTokenRefresher.class)
        .registerIterators(ngIteratorsConfig.getOauthTokenRefreshIteratorConfig().getThreadPoolSize());
    injector.getInstance(GoogleChatOAuthTokenRefresher.class)
        .registerIterators(ngIteratorsConfig.getOauthTokenRefreshIteratorConfig().getThreadPoolSize());
    if (appConfig.isEnableWaitNotifyEngineOptimisation()) {
      injector.getInstance(NotifyResponseIterator.class)
          .createAndStartRedisBatchIterator(
              PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
                  .name(WaitNotifyCollectionNameResolver.qualifyIteratorLockName("NG-Manager-NotifyResponseIterator"))
                  .poolSize(appConfig.getNotifyResponseRedisConfig().getThreadPoolSize())
                  .batchSize(appConfig.getNotifyResponseRedisConfig().getRedisBatchSize())
                  .lockTimeout(appConfig.getNotifyResponseRedisConfig().getRedisLockTimeout())
                  .interval(
                      Duration.ofSeconds(appConfig.getNotifyResponseRedisConfig().getThreadPoolIntervalInSeconds()))
                  .threadSleepInterval(appConfig.getNotifyResponseRedisConfig().getThreadSleepIntervalMs())
                  .build(),
              Duration.ofSeconds(appConfig.getNotifyResponseRedisConfig().getTargetIntervalInSeconds()));
    }

    if (appConfig.getInstanceSyncIteratorConfig() != null) {
      var instanceSyncIteratorConfig = appConfig.getInstanceSyncIteratorConfig();
      if (instanceSyncIteratorConfig.isEnabled()) {
        injector.getInstance(InstanceSyncTaskHandler.class).registerIterators(instanceSyncIteratorConfig);
      }
    }
  }

  public void registerJobs(Injector injector) {
    injector.getInstance(PrimaryVersionChangeScheduler.class).registerExecutors();
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("Next Gen Manager", healthService);
    environment.getAdminContext().addServlet(HealthServlet.class, DEFAULT_HEALTHCHECK_URI + "/liveness");
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void createConsumerThreadsToListenToEvents(
      Environment environment, Injector injector, NextGenConfiguration appConfig) {
    environment.lifecycle().manage(injector.getInstance(NGEventConsumerService.class));
    environment.lifecycle().manage(injector.getInstance(GitSyncEventConsumerService.class));
    environment.lifecycle().manage(injector.getInstance(PipelineEventConsumerController.class));
    environment.lifecycle().manage(injector.getInstance(PlgEventConsumerController.class));
    registerKafkaConsumers(environment, injector, appConfig.getEventsFrameworkConfiguration());
  }

  private void registerKafkaConsumers(
      Environment environment, Injector injector, EventsFrameworkConfiguration eventsFrameworkConfiguration) {
    if (eventsFrameworkConfiguration == null || !eventsFrameworkConfiguration.isShouldUseKafka()) {
      log.info("Kafka is not enabled, skipping Kafka consumer registration for NG Manager");
      return;
    }

    log.info("Initializing Kafka consumers for NG Manager");
    ConsumerMaintenanceListener listener = injector.getInstance(ConsumerMaintenanceListener.class);
    injector.getInstance(MaintenanceController.class).register(listener);
    // Sync state after registration to avoid race condition
    listener.syncMaintenanceState();

    environment.lifecycle().manage(injector.getInstance(UnifiedPipelineEventConsumer.class));

    log.info("Successfully registered Kafka consumers for NG Manager");
  }

  private void registerPmsSdkEvents(NextGenConfiguration appConfig, Injector injector) {
    log.info("Initializing sdk redis abstract consumers...");
    PipelineEventConsumerController pipelineEventConsumerController =
        injector.getInstance(PipelineEventConsumerController.class);
    pipelineEventConsumerController.register(injector.getInstance(InterruptEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(CdngOrchestrationEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(CdBackfillOrchestrationEventRedisConsumer.class), 1);

    // V1 will be removed once the change has reached prod
    pipelineEventConsumerController.register(injector.getInstance(FacilitatorEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeStartEventRedisConsumer.class), 2);
    pipelineEventConsumerController.register(injector.getInstance(ProgressEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseEventRedisConsumer.class), 2);
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventRedisConsumer.class), 2);

    pipelineEventConsumerController.register(injector.getInstance(FacilitatorEventRedisConsumerV2.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeStartEventRedisConsumerV2.class), 2);
    pipelineEventConsumerController.register(injector.getInstance(NodeProgressEventRedisConsumerV2.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseRedisConsumerV2.class), 2);
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventConsumerV2.class), 2);
    pipelineEventConsumerController.register(injector.getInstance(InterruptEventRedisConsumerV2.class), 1);

    pipelineEventConsumerController.register(injector.getInstance(CreatePartialPlanRedisConsumer.class), 2);
    pipelineEventConsumerController.register(injector.getInstance(PipelineExecutionSummaryCDRedisEventConsumer.class),
        appConfig.getDebeziumConsumersConfigs().getPlanExecutionsSummaryStreaming().getThreads());
  }

  private void registerLicenseUsageScheduler(Injector injector) {
    LicenseUsageScheduler licenseUsageScheduler = injector.getInstance(LicenseUsageScheduler.class);
    licenseUsageScheduler.initialize(10, 2, injector.getInstance(LicenseUsageHourlyDailyHandler.class),
        injector.getInstance(LicenseUsageMonthlyYearlyHandler.class));
  }

  private void registerDebeziumEvents(NextGenConfiguration appConfig, Injector injector) {
    log.info("Initializing sdk redis abstract consumers for PLG...");
    PlgEventConsumerController plgEventConsumerController = injector.getInstance(PlgEventConsumerController.class);
    plgEventConsumerController.register(injector.getInstance(ModuleLicensesRedisEventConsumer.class),
        appConfig.getDebeziumConsumersConfigs().getModuleLicensesStreaming().getThreads());
    log.info("Initializing sdk redis abstract consumers for GitOps...");
    GitOpsEventConsumerController gitOpsEventConsumerController =
        injector.getInstance(GitOpsEventConsumerController.class);
    gitOpsEventConsumerController.register(injector.getInstance(GitOpsUtilizationSnapshotRedisEventConsumer.class),
        appConfig.getDebeziumConsumersConfigs().getGitOpsUtilizationSnapshotStreaming().getThreads());
    gitOpsEventConsumerController.register(injector.getInstance(GitopsApplicationsRedisEventConsumer.class),
        appConfig.getDebeziumConsumersConfigs().getGitOpsApplicationsStreaming().getThreads());
  }

  private void registerYamlSdk(Injector injector) {
    YamlSdkConfiguration yamlSdkConfiguration = YamlSdkConfiguration.builder()
                                                    .requireSchemaInit(true)
                                                    .requireSnippetInit(true)
                                                    .requireValidatorInit(true)
                                                    .build();
    YamlSdkInitHelper.initialize(injector, yamlSdkConfiguration);
  }

  public void registerPipelineSDK(NextGenConfiguration appConfig, Injector injector, Environment environment) {
    PmsSdkConfiguration sdkConfig = getPmsSdkConfiguration(appConfig);
    if (sdkConfig.getDeploymentMode().equals(SdkDeployMode.REMOTE)) {
      try {
        PmsSdkInitHelper.initializeSDKInstance(injector, sdkConfig, environment);
      } catch (Exception e) {
        log.error("Failed To register pipeline sdk", e);
        System.exit(1);
      }
    }
  }

  private PmsSdkConfiguration getPmsSdkConfiguration(NextGenConfiguration appConfig) {
    boolean remote = false;
    if (appConfig.getShouldConfigureWithPMS() != null && appConfig.getShouldConfigureWithPMS()) {
      remote = true;
    }

    return PmsSdkConfiguration.builder()
        .deploymentMode(remote ? SdkDeployMode.REMOTE : SdkDeployMode.LOCAL)
        .streamPerServiceConfiguration(appConfig.isStreamPerServiceConfiguration())
        .skipSdkMongoRegistration(appConfig.isSkipSdkMongoRegistration())
        .moduleType(ModuleType.CD)
        .grpcServerConfig(appConfig.getPmsSdkGrpcServerConfig())
        .pmsGrpcClientConfig(appConfig.getPmsGrpcClientConfig())
        .pipelineServiceInfoProviderClass(CDNGPlanCreatorProvider.class)
        .staticAliases(getStaticAliases())
        .sdkFunctors(getSdkFunctors())
        .jsonExpansionHandlers(getJsonExpansionHandlers())
        .inputsMetadataHandlers(getInputsMetadataHandlers())
        .filterCreationResponseMerger(new CDNGFilterCreationResponseMerger())
        .engineSteps(NgStepRegistrar.getEngineSteps())
        .engineAdvisers(CDServiceAdviserRegistrar.getEngineAdvisers())
        .executionSummaryModuleInfoProviderClass(CDNGModuleInfoProvider.class)
        .eventsFrameworkConfiguration(appConfig.getEventsFrameworkConfiguration())
        .engineEventHandlersMap(getOrchestrationEventHandlers())
        .executionPoolConfig(appConfig.getPmsSdkExecutionPoolConfig())
        .orchestrationEventPoolConfig(appConfig.getPmsSdkOrchestrationEventPoolConfig())
        .orchestrationHandlerPoolConfig(appConfig.getPmsSdkOrchestrationHandlerPoolConfig())
        .planCreatorServiceInternalConfig(appConfig.getPmsPlanCreatorServicePoolConfig())
        .pipelineSdkRedisEventsConfig(appConfig.getPipelineSdkRedisEventsConfig())
        .backfillOrchestrationEventPoolConfig(appConfig.getPmsSdkBackfillOrchestrationEventPoolConfig())
        .build();
  }

  private List<InputsMetadataHandlerInfo> getInputsMetadataHandlers() {
    List<InputsMetadataHandlerInfo> inputsMetadataHandlers = new ArrayList<>();
    InputsMetadataInfo servicesInfo = InputsMetadataInfo.newBuilder()
                                          .setEntityInputsKey(YAMLFieldNameConstants.SERVICE_INPUTS)
                                          .setEntityKey(YAMLFieldNameConstants.SERVICE_REF)
                                          .build();
    InputsMetadataHandlerInfo serviceHandlerInfo = InputsMetadataHandlerInfo.builder()
                                                       .inputsMetadataInfo(servicesInfo)
                                                       .inputsMetadataHandler(ServiceInputsMetadataHandler.class)
                                                       .build();
    inputsMetadataHandlers.add(serviceHandlerInfo);
    return inputsMetadataHandlers;
  }

  private Map<String, String> getStaticAliases() {
    Map<String, String> aliases = new HashMap<>();
    aliases.put("serviceConfig", "stage.spec.serviceConfig");
    aliases.put("serviceDefinition", "stage.spec.serviceConfig.serviceDefinition");
    aliases.put("artifact", "artifacts.primary");
    aliases.put("infra", "stage.spec.infrastructure.output");
    aliases.put("INFRA_KEY", "stage.spec.infrastructure.output.infrastructureKey");
    aliases.put("INFRA_KEY_SHORT_ID", "stage.spec.infrastructure.output.infrastructureKeyShort");
    aliases.put("OnRollbackModeExecution",
        "(<+ambiance.metadata.executionMode> == \"POST_EXECUTION_ROLLBACK\") || (<+ambiance.metadata.executionMode> == "
            + "\"PIPELINE_ROLLBACK\")");
    return aliases;
  }

  private Map<String, Class<? extends SdkFunctor>> getSdkFunctors() {
    Map<String, Class<? extends SdkFunctor>> sdkFunctorMap = new HashMap<>();
    sdkFunctorMap.put(ImagePullSecretFunctor.IMAGE_PULL_SECRET, ImagePullSecretFunctor.class);
    sdkFunctorMap.put(ConnectorFunctor.CONNECTOR_KEY, ConnectorFunctor.class);
    sdkFunctorMap.put(DockerConfigJsonFunctor.DOCKER_CONFIG_JSON, DockerConfigJsonFunctor.class);
    sdkFunctorMap.put(VariableFunctor.VARIABLE, VariableFunctor.class);
    sdkFunctorMap.put(TerraformPlanJsonFunctor.TERRAFORM_PLAN_JSON, TerraformPlanJsonFunctor.class);
    sdkFunctorMap.put(
        TerraformHumanReadablePlanFunctor.TERRAFORM_HUMAN_READABLE_PLAN, TerraformHumanReadablePlanFunctor.class);
    sdkFunctorMap.put(
        TerraformCloudPolicyChecksJsonFunctor.TFC_POLICY_CHECKS_JSON, TerraformCloudPolicyChecksJsonFunctor.class);
    sdkFunctorMap.put(TerraformCloudPlanJsonFunctor.TERRAFORM_CLOUD_PLAN_JSON, TerraformCloudPlanJsonFunctor.class);
    sdkFunctorMap.put(INSTANCE_FUNCTOR, InstanceFunctor.class);
    sdkFunctorMap.put(CONFIG_FILE_FUNCTOR, ConfigFileFunctor.class);
    sdkFunctorMap.put(FILE_STORE_FUNCTOR, FileStoreFunctor.class);
    sdkFunctorMap.put(KUBERNETES_RELEASE_FUNCTOR_NAME, KubernetesReleaseFunctor.class);
    return sdkFunctorMap;
  }

  private List<JsonExpansionHandlerInfo> getJsonExpansionHandlers() {
    List<JsonExpansionHandlerInfo> jsonExpansionHandlers = new ArrayList<>();
    JsonExpansionInfo connRefInfo =
        JsonExpansionInfo.newBuilder().setKey(YAMLFieldNameConstants.CONNECTOR_REF).setExpansionType(KEY).build();
    JsonExpansionHandlerInfo connRefHandlerInfo = JsonExpansionHandlerInfo.builder()
                                                      .jsonExpansionInfo(connRefInfo)
                                                      .expansionHandler(DefaultConnectorRefExpansionHandler.class)
                                                      .build();
    JsonExpansionInfo serviceRefInfo =
        JsonExpansionInfo.newBuilder().setKey(YamlTypes.SERVICE_REF).setExpansionType(KEY).build();
    JsonExpansionHandlerInfo serviceRefHandlerInfo = JsonExpansionHandlerInfo.builder()
                                                         .jsonExpansionInfo(serviceRefInfo)
                                                         .expansionHandler(ServiceRefExpansionHandler.class)
                                                         .build();

    JsonExpansionInfo envRefInfo =
        JsonExpansionInfo.newBuilder()
            .setKey("stage/spec/infrastructure/environmentRef")
            .setExpansionType(ExpansionRequestType.LOCAL_FQN)
            .setStageType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("Deployment").build())
            .build();
    JsonExpansionHandlerInfo envRefHandlerInfo = JsonExpansionHandlerInfo.builder()
                                                     .jsonExpansionInfo(envRefInfo)
                                                     .expansionHandler(EnvironmentRefExpansionHandler.class)
                                                     .build();

    JsonExpansionInfo envInfo =
        JsonExpansionInfo.newBuilder()
            .setKey("stage/spec/environment")
            .setExpansionType(ExpansionRequestType.LOCAL_FQN)
            .setStageType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("Deployment").build())
            .build();
    JsonExpansionHandlerInfo envHandlerInfo = JsonExpansionHandlerInfo.builder()
                                                  .jsonExpansionInfo(envInfo)
                                                  .expansionHandler(EnvironmentExpansionHandler.class)
                                                  .build();

    JsonExpansionInfo multiEnvironmentInfo =
        JsonExpansionInfo.newBuilder()
            .setKey("stage/spec/environments")
            .setExpansionType(ExpansionRequestType.LOCAL_FQN)
            .setStageType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("Deployment").build())
            .build();
    JsonExpansionHandlerInfo multiEnvironmentHandlerInfo = JsonExpansionHandlerInfo.builder()
                                                               .jsonExpansionInfo(multiEnvironmentInfo)
                                                               .expansionHandler(MultiEnvironmentExpansionHandler.class)
                                                               .build();

    JsonExpansionInfo environmentGroupInfo =
        JsonExpansionInfo.newBuilder()
            .setKey("stage/spec/environmentGroup")
            .setExpansionType(ExpansionRequestType.LOCAL_FQN)
            .setStageType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("Deployment").build())
            .build();
    JsonExpansionHandlerInfo environmentGroupHandlerInfo = JsonExpansionHandlerInfo.builder()
                                                               .jsonExpansionInfo(environmentGroupInfo)
                                                               .expansionHandler(EnvironmentGroupExpandedHandler.class)
                                                               .build();

    jsonExpansionHandlers.add(connRefHandlerInfo);
    jsonExpansionHandlers.add(serviceRefHandlerInfo);
    jsonExpansionHandlers.add(envRefHandlerInfo);
    jsonExpansionHandlers.add(envHandlerInfo);
    jsonExpansionHandlers.add(multiEnvironmentHandlerInfo);
    jsonExpansionHandlers.add(environmentGroupHandlerInfo);
    return jsonExpansionHandlers;
  }

  private Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> getOrchestrationEventHandlers() {
    Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> orchestrationEventTypeSetHashMap =
        new HashMap<>();
    List<Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>>> orchestrationEventHandlersList =
        new ArrayList<>(Arrays.asList(CdngOrchestrationExecutionEventHandlerRegistrar.getEngineEventHandlers(),
            DeploymentEventListenerRegistrar.getEngineEventHandlers(),
            DriftDetectionListenerRegistrar.getEngineEventHandlers(),
            DeploymentEventGenerator.getEngineEventHandlers()));
    orchestrationEventHandlersList.forEach(
        orchestrationEventHandlers -> mergeEventHandlers(orchestrationEventTypeSetHashMap, orchestrationEventHandlers));
    return orchestrationEventTypeSetHashMap;
  }

  private void mergeEventHandlers(
      Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> finalHandlers,
      Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> handlers) {
    for (Map.Entry<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> entry :
        handlers.entrySet()) {
      if (finalHandlers.containsKey(entry.getKey())) {
        Set<Class<? extends OrchestrationEventHandler>> existing = finalHandlers.get(entry.getKey());
        existing.addAll(entry.getValue());
        finalHandlers.put(entry.getKey(), existing);
      } else {
        finalHandlers.put(entry.getKey(), entry.getValue());
      }
    }
  }

  private void registerManagedBeans(Environment environment, Injector injector, NextGenConfiguration appConfig) {
    environment.lifecycle().manage(injector.getInstance(QueueListenerController.class));
    environment.lifecycle().manage(injector.getInstance(NotifierScheduledExecutorService.class));
    environment.lifecycle().manage(injector.getInstance(OutboxEventPollService.class));
    // Register worker pools before their schedulers so Dropwizard's reverse stop order prevents
    // new reconciliation submissions before the pools begin their bounded shutdown.
    environment.lifecycle().manage(injector.getInstance(PrivateConnectivityExecutorLifecycle.class));
    environment.lifecycle().manage(injector.getInstance(ReleaseReconciler.class));
    if (appConfig.isEnableDefaultUserGroupsCreationJob()) {
      environment.lifecycle().manage(injector.getInstance(DefaultUserGroupsCreationJob.class));
    }
    environment.lifecycle().manage(injector.getInstance(AddUniqueIdParentIdToEntitiesJob.class));
    environment.lifecycle().manage(injector.getInstance(AddUniqueIdParentIdToCdcEntitiesJob.class));
    environment.lifecycle().manage(injector.getInstance(FixConnectorScopeForPipelineSetupUsageJob.class));
    environment.lifecycle().manage(injector.getInstance(AddUniqueIdParentIdToFilterEntitiesJob.class));
    environment.lifecycle().manage(injector.getInstance(TotalAccountUsersEntityUpdateJob.class));
    environment.lifecycle().manage(injector.getInstance(DailyAccountUsersEntityUpdateJob.class));

    // Register OrganizationBillingMetricJob if enabled via config
    if (appConfig.isEnableOrganizationBillingJob()) {
      environment.lifecycle().manage(injector.getInstance(OrganizationBillingMetricJob.class));
    }

    // Register CDBillingMetricJob if enabled via config (CD/GitOps service usage metrics)
    if (appConfig.isEnableCDBillingJob()) {
      environment.lifecycle().manage(injector.getInstance(CDBillingMetricJob.class));
    }

    environment.lifecycle().manage(injector.getInstance(TokenExpirationService.class));
    environment.lifecycle().manage(injector.getInstance(ScopedPermissionsBackfillService.class));
    environment.lifecycle().manage(injector.getInstance(PopulateUniqueIdAndParentUniqueIdInNgInstanceStatsJob.class));
    environment.lifecycle().manage(injector.getInstance(SecretsCreateEditRoleMigrationService.class));
    environment.lifecycle().manage(injector.getInstance(UserGroupsManageRoleMigrationJob.class));
    environment.lifecycle().manage(injector.getInstance(FmeFeatureFlagRoleMigrationService.class));
    environment.lifecycle().manage(injector.getInstance(DeDuplicateUserGroupsJob.class));
    environment.lifecycle().manage(injector.getInstance(OrphanUserGroupsCleanupJob.class));
    environment.lifecycle().manage(injector.getInstance(ServiceUniqueIdBackfillJob.class));
    environment.lifecycle().manage(injector.getInstance(AddParentUniqueIdForTimescaleTableJob.class));

    // Register entity migration metrics collector if enabled in configuration
    ProjectEntityMigrationMetricsConfig metricsConfig =
        appConfig.getProjectMovementProjectEntityMigrationMetricsConfig();
    if (metricsConfig != null && metricsConfig.isEnabled()) {
      log.info("Registering project entity migration metrics collector with initial delay: {} minutes, frequency: {} "
              + "minutes",
          metricsConfig.getInitialDelayMinutes(), metricsConfig.getFrequencyMinutes());
      ProjectEntityMigrationMetricsJob metricsJob = injector.getInstance(ProjectEntityMigrationMetricsJob.class);
      metricsJob.configure(metricsConfig);
      environment.lifecycle().manage(metricsJob);
      log.info("Project Entity migration metrics collector registered successfully");
    } else {
      log.info("Project Entity migration metrics collector is disabled or not configured");
    }

    // Register project movement timescale db migration metrics collector if enabled in configuration
    ProjectMovementTimescaleDbMigrationMetricsConfig timescaleMetricsConfig =
        appConfig.getProjectMovementTimescaleDbMigrationMetricsConfig();
    if (timescaleMetricsConfig != null && timescaleMetricsConfig.isEnabled()) {
      log.info("Registering project movement timescale db migration metrics collector with initial delay: {} minutes, "
              + "frequency: {} "
              + "minutes",
          timescaleMetricsConfig.getInitialDelayMinutes(), timescaleMetricsConfig.getFrequencyMinutes());
      ProjectMovementTimescaleDbMigrationMetricsJob timescaleMetricsJob =
          injector.getInstance(ProjectMovementTimescaleDbMigrationMetricsJob.class);
      timescaleMetricsJob.configure(timescaleMetricsConfig);
      environment.lifecycle().manage(timescaleMetricsJob);
      log.info("Project Movement TimeScaleDB migration metrics collector registered successfully");
    } else {
      log.info("Project Movement TimeScaleDB migration metrics collector is disabled or not configured");
    }

    // Do not remove as it's used for MaintenanceController for shutdown mode
    environment.lifecycle().manage(injector.getInstance(MaintenanceController.class));
    environment.lifecycle().manage(injector.getInstance(FileJobsService.class));
    if (appConfig.isUseQueueServiceForWebhookTriggers()) {
      environment.lifecycle().manage(injector.getInstance(WebhookBranchHookEventQueueProcessor.class));
      environment.lifecycle().manage(injector.getInstance(WebhookPushEventQueueProcessor.class));
      environment.lifecycle().manage(injector.getInstance(WebhookGitXPushEventQueueProcessor.class));
    }
    if (appConfig.isUseQueueServiceForGitXWebhook()) {
      environment.lifecycle().manage(injector.getInstance(GitXWebhookQueueProcessor.class));
      environment.lifecycle().manage(injector.getInstance(GitXWebhookPRQueueProcessor.class));
      environment.lifecycle().manage(injector.getInstance(GitXWebhookEventValidationQueueProcessor.class));
    }
    createConsumerThreadsToListenToEvents(environment, injector, appConfig);
  }

  /**
   * Registers CDC Kafka consumers as Dropwizard {@link Managed} beans when
   * {@code cdcKafkaConfig.enabled} is true.
   *
   * <p>Each CDC consumer extends {@code HKafkaConsumer}, which owns its own poll-loop thread
   * and maintenance gating; Dropwizard's lifecycle manager invokes {@code start()} on boot
   * and {@code stop()} on graceful shutdown. {@link ConsumerMaintenanceListener} is already
   * registered with {@link io.harness.maintenance.MaintenanceController} during application
   * initialization, so it is NOT re-registered here.
   *
   * <p>Whether a consumer actively processes (vs. drains) is further gated at runtime by a
   * global Harness feature flag inside the message handler.
   */
  private void registerGitOpsKafkaConsumers(
      Injector injector, NextGenConfiguration appConfig, Environment environment) {
    CdcKafkaConfig cdcKafkaConfig = appConfig.getCdcKafkaConfig();
    if (cdcKafkaConfig == null || !cdcKafkaConfig.isEnabled()) {
      log.info("[CDC-Kafka][GitOps] CDC Kafka infrastructure disabled (cdcKafkaConfig.enabled=false); skipping Kafka "
          + "CDC consumer startup");
      return;
    }

    log.info("[CDC-Kafka][GitOps] Initializing CDC Kafka consumers for GitOps");
    ConsumerMaintenanceListener listener = injector.getInstance(ConsumerMaintenanceListener.class);
    injector.getInstance(MaintenanceController.class).register(listener);
    // Sync state after registration to avoid race condition
    listener.syncMaintenanceState();

    try {
      environment.lifecycle().manage(injector.getInstance(GitOpsUtilizationSnapshotKafkaConsumer.class));
      log.info("[CDC-Kafka][GitOps] Registered CDC Kafka consumer: GitOpsUtilizationSnapshotKafkaConsumer "
              + "(topic={}, group={}, processing gated by runtime FF)",
          CdcKafkaConstants.UTILIZATION_SNAPSHOT_TOPIC, CdcKafkaConstants.UTILIZATION_SNAPSHOT_CONSUMER_GROUP);
    } catch (Exception ex) {
      log.error("[CDC-Kafka][GitOps] Failed to register GitOps utilization snapshot Kafka CDC consumer: {}",
          ex.getMessage(), ex);
    }

    try {
      environment.lifecycle().manage(injector.getInstance(GitopsApplicationsKafkaConsumer.class));
      log.info("[CDC-Kafka][GitOps] Registered CDC Kafka consumer: GitopsApplicationsKafkaConsumer "
              + "(topic={}, group={}, processing gated by runtime FF)",
          CdcKafkaConstants.APPLICATIONS_TOPIC, CdcKafkaConstants.APPLICATIONS_CONSUMER_GROUP);
    } catch (Exception ex) {
      log.error(
          "[CDC-Kafka][GitOps] Failed to register GitOps applications Kafka CDC consumer: {}", ex.getMessage(), ex);
    }
  }

  /**
   * Registers GTM-owned CDC Kafka consumers. Gated entirely by per-consumer config flags
   * inside {@code cdcKafka.consumers} — completely independent of GitOps's
   * {@code cdcKafkaConfig.enabled} flag.
   */
  private void registerGtmKafkaConsumers(Injector injector, NextGenConfiguration appConfig, Environment environment) {
    CdcKafkaConfig cdcKafkaConfig = appConfig.getCdcKafkaConfig();
    if (cdcKafkaConfig == null) {
      return;
    }
    boolean moduleLicensesEnabled = cdcKafkaConfig.getConsumer(ModuleLicensesKafkaConsumer.CONSUMER_CONFIG_KEY)
                                        .map(CdcKafkaConsumerConfig::isKafkaConsumerEnabled)
                                        .orElse(false);
    if (moduleLicensesEnabled) {
      try {
        environment.lifecycle().manage(injector.getInstance(ModuleLicensesKafkaConsumer.class));
        log.info("Registered CDC Kafka consumer: ModuleLicensesKafkaConsumer "
                + "(topic={}, group={}, processing gated by config processingEnabled)",
            ModuleLicensesKafkaConsumer.CONSUMER_CONFIG_KEY, ModuleLicensesKafkaConsumer.CONSUMER_GROUP);
      } catch (Exception ex) {
        log.error("Failed to register GTM moduleLicenses Kafka CDC consumer: {}", ex.getMessage(), ex);
      }
    } else {
      log.info("GTM moduleLicenses Kafka CDC consumer disabled "
          + "(cdcKafka.consumers.moduleLicenses.kafkaConsumerEnabled=false); skipping registration");
    }
  }

  /**
   * Registers PIPELINE-team CDC Kafka consumer for {@code pmsMongo.pms-harness.planExecutionsSummary}.
   * Gated by per-consumer config flag {@code cdcKafka.consumers[planExecutionsSummaryCD].kafkaConsumerEnabled}
   * — independent of GitOps's {@code cdcKafkaConfig.enabled} flag.
   */
  private void registerPipelineExecutionSummaryCDKafkaConsumer(
      Injector injector, NextGenConfiguration appConfig, Environment environment) {
    CdcKafkaConfig cdcKafkaConfig = appConfig.getCdcKafkaConfig();
    if (cdcKafkaConfig == null) {
      return;
    }
    boolean pipelineExecSummaryEnabled =
        cdcKafkaConfig.getConsumer(PipelineExecutionSummaryCDKafkaConsumer.CONSUMER_CONFIG_KEY)
            .map(CdcKafkaConsumerConfig::isKafkaConsumerEnabled)
            .orElse(false);
    if (pipelineExecSummaryEnabled) {
      try {
        environment.lifecycle().manage(injector.getInstance(PipelineExecutionSummaryCDKafkaConsumer.class));
        log.info("Registered CDC Kafka consumer: PipelineExecutionSummaryCDKafkaConsumer "
                + "(topic={}, group={}, processing gated by config processingEnabled)",
            PipelineExecutionSummaryCDKafkaConsumer.CONSUMER_CONFIG_KEY,
            PipelineExecutionSummaryCDKafkaConsumer.CONSUMER_GROUP);
      } catch (Exception ex) {
        log.error("Failed to register PIPELINE planExecutionsSummaryCD Kafka CDC consumer: {}", ex.getMessage(), ex);
      }
    } else {
      log.info("PIPELINE planExecutionsSummaryCD Kafka CDC consumer disabled "
          + "(cdcKafka.consumers.planExecutionsSummaryCD.kafkaConsumerEnabled=false); skipping registration");
    }
  }

  private void registerCorsFilter(NextGenConfiguration appConfig, Environment environment) {
    FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    String allowedOrigins = String.join(",", appConfig.getAllowedOrigins());
    cors.setInitParameters(of("allowedOrigins", allowedOrigins, "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Authorization,X-api-key", "allowedMethods",
        "OPTIONS,GET,PUT,POST,DELETE,HEAD", "preflightMaxAge", "86400"));
    cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  private void registerResources(NextGenConfiguration appConfig, Environment environment, Injector injector) {
    try {
      for (Class<?> resource : HARNESS_RESOURCE_CLASSES) {
        if (Resource.isAcceptable(resource)) {
          environment.jersey().register(injector.getInstance(resource));
        }
      }
      environment.jersey().register(injector.getInstance(VersionInfoResource.class));
      environment.jersey().register(injector.getInstance(SMPVersionResource.class));

      environment.jersey().property(
          ServerProperties.RESOURCE_VALIDATION_DISABLE, appConfig.isDisableResourceValidation());
    } catch (Exception e) {
      log.error("Failed to register resources. Classes are: {}", Iterables.toString(HARNESS_RESOURCE_CLASSES));
      throw e;
    }
  }

  private void registerJerseyProviders(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(KryoFeature.class));
    environment.jersey().register(JerseyViolationExceptionMapperV2.class);
    environment.jersey().register(OptimisticLockingFailureExceptionMapper.class);
    environment.jersey().register(NotFoundExceptionMapper.class);
    environment.jersey().register(InvalidUserRemoveRequestExceptionMapper.class);
    environment.jersey().register(NGAccessDeniedExceptionMapper.class);
    environment.jersey().register(WingsExceptionMapperV2.class);
    environment.jersey().register(GenericExceptionMapperV2.class);
    environment.jersey().register(QueryParamExceptionMapper.class);
    environment.jersey().register(NotSupportedExceptionMapper.class);
    environment.jersey().register(MongoExecutionTimeoutExceptionMapper.class);
    environment.jersey().register(BadRequestExceptionMapper.class);
    environment.jersey().register(PrivateConnectivityConflictExceptionMapper.class);
    environment.jersey().register(FileNotFoundExceptionMapper.class);
  }

  private void registerJerseyFeatures(Environment environment) {
    environment.jersey().register(MultiPartFeature.class);
  }

  private void registerCharsetResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CharsetResponseFilter.class));
  }

  private void registerApiResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(ApiResponseFilter.class));
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void registerTraceFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(TraceFilter.class));
  }

  private void registerEtagFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(EtagFilter.class));
  }

  private void registerWaitEnginePublishers(Injector injector) {
    final QueuePublisher<NotifyEvent> publisher =
        injector.getInstance(Key.get(new TypeLiteral<QueuePublisher<NotifyEvent>>() {}));
    final NotifyQueuePublisherRegister notifyQueuePublisherRegister =
        injector.getInstance(NotifyQueuePublisherRegister.class);
    notifyQueuePublisherRegister.register(
        NG_ORCHESTRATION, payload -> publisher.send(Arrays.asList(NG_ORCHESTRATION), payload));
  }

  private void registerScheduleJobs(Injector injector, boolean isEnableWaitNotifyOptimization) {
    log.info("Initializing scheduled jobs...");
    if (isEnableWaitNotifyOptimization) {
      NotifyResponseCleanerSpringPersistence cleaner =
          injector.getInstance(NotifyResponseCleanerFactory.class).create("NG-Manager");
      injector.getInstance(NotifierScheduledExecutorService.class)
          .scheduleWithFixedDelay(cleaner, random.nextInt(300), 300L, TimeUnit.SECONDS);
    } else {
      injector.getInstance(NotifierScheduledExecutorService.class)
          .scheduleWithFixedDelay(
              injector.getInstance(NotifyResponseCleaner.class), random.nextInt(300), 300L, TimeUnit.SECONDS);
    }

    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("gitChangeSet")))
        .scheduleWithFixedDelay(
            injector.getInstance(GitChangeSetRunnable.class), random.nextInt(4), 4L, TimeUnit.SECONDS);

    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateSyncServiceImpl.class), 0L, 2L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateAsyncServiceImpl.class), 0L, 5L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(DelegateProgressServiceImpl.class), 0L, 5L, TimeUnit.SECONDS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("taskPollExecutor")))
        .scheduleWithFixedDelay(injector.getInstance(ProgressUpdateService.class), 0L, 5L, TimeUnit.SECONDS);

    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("lowFrequencyScheduler")))
        .scheduleWithFixedDelay(injector.getInstance(InstanceAccountInfoRunnable.class), 0, 6, TimeUnit.HOURS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("lowFrequencyScheduler")))
        .scheduleWithFixedDelay(injector.getInstance(ModuleVersionsMaintenanceTask.class), 0, 3, TimeUnit.HOURS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("lowFrequencyScheduler")))
        .scheduleWithFixedDelay(injector.getInstance(CDLicenseDailyReportTask.class), 0, 3, TimeUnit.HOURS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("lowFrequencyScheduler")))
        .scheduleWithFixedDelay(injector.getInstance(SendAccountStatisticsToSegmentTask.class), 0, 24, TimeUnit.HOURS);
    injector.getInstance(Key.get(ScheduledExecutorService.class, Names.named("lowFrequencyScheduler")))
        .scheduleWithFixedDelay(injector.getInstance(NGLdapMigrationScheduler.class), 0, 5, TimeUnit.MINUTES);
  }

  private void registerAuthFilters(NextGenConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.isEnableAuth()) {
      registerNextGenAuthFilter(
          configuration, environment, injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED"))));
      registerInternalApiAuthFilter(configuration, environment);
    }
  }

  private void registerAPIAuthTelemetryFilters(
      NextGenConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.getSegmentConfiguration() != null && configuration.getSegmentConfiguration().isEnabled()) {
      registerAPIAuthTelemetryFilter(environment, injector);
      registerTerraformTelemetryFilter(environment, injector);
      registerAPIAuthTelemetryResponseFilter(environment, injector);
      registerAPIErrorsTelemetrySenderFilter(environment, injector);
    }
  }

  private void registerScopeAccessCheckFilter(
      NextGenConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.isScopeAccessCheckEnabled()) {
      AccessControlClient accessControlClient = injector.getInstance(AccessControlClient.class);
      environment.jersey().register(new NGScopeAccessCheckFilter(
          Arrays.asList(bypassInterMsvcRequests(), bypassPublicApi(), bypassInternalApi(), bypassAdminPortalAuth(),
              bypassPaths(Arrays.asList("/version", "/swagger", "/swagger.json"))),
          accessControlClient));
    }
  }

  private void registerNextGenAuthFilter(
      NextGenConfiguration configuration, Environment environment, TokenClient tokenClient) {
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate =
        (getAuthenticationExemptedRequestsPredicate().negate())
            .and((getAuthFilterPredicate(InternalApi.class)).negate());
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(BEARER.getServiceId(), configuration.getNextGenConfig().getJwtAuthSecret());
    serviceToSecretMapping.put(
        IDENTITY_SERVICE.getServiceId(), configuration.getNextGenConfig().getJwtIdentityServiceSecret());
    serviceToSecretMapping.put(RELICX.getServiceId(), configuration.getNextGenConfig().getRelicxSecret());
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getNextGenConfig().getNgManagerServiceSecret());
    serviceToSecretMapping.put(ADMIN_PORTAL.getServiceId(), configuration.getNextGenConfig().getJwtDataHandlerSecret());
    serviceToSecretMapping.put(GITOPS.getServiceId(), configuration.getNextGenConfig().getGitOpsServiceSecret());
    serviceToSecretMapping.put(RESOURCE_HIERARCHY_SERVICE.getServiceId(),
        configuration.getNextGenConfig().getResourceHierarchyServiceSecret());
    serviceToSecretMapping.put(
        PLATFORM_CONFIG_SERVICE.getServiceId(), configuration.getNextGenConfig().getPlatformConfigServiceSecret());
    serviceToSecretMapping.put(
        APPSEC_SERVICE.getServiceId(), configuration.getNextGenConfig().getAppsecServiceSecret());
    serviceToSecretMapping.put(CV_NEXT_GEN.getServiceId(), configuration.getNextGenConfig().getCvngServiceSecret());

    // Mesh identity dispatch: opt-in per config. See MeshIdentityConfig for the rollout matrix.
    MeshIdentityConfig mesh = configuration.getMeshIdentity();
    NextGenAuthenticationFilter authFilter =
        io.harness.security.MeshIdentityBootstrap.buildFilter(predicate, serviceToSecretMapping, tokenClient, mesh);
    environment.jersey().register(authFilter);
  }

  private void registerAPIAuthTelemetryFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new APIAuthTelemetryFilter(telemetryReporter));
  }

  private void registerTerraformTelemetryFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new TerraformTelemetryFilter(telemetryReporter));
  }

  private void registerAPIAuthTelemetryResponseFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new APIAuthTelemetryResponseFilter(telemetryReporter));
  }

  private void registerAPIErrorsTelemetrySenderFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new APIErrorsTelemetrySenderFilter(telemetryReporter, NG_MANAGER.getServiceId()));
  }

  private void registerInternalApiAuthFilter(NextGenConfiguration configuration, Environment environment) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getNextGenConfig().getNgManagerServiceSecret());
    serviceToSecretMapping.put(RESOURCE_HIERARCHY_SERVICE.getServiceId(),
        configuration.getNextGenConfig().getResourceHierarchyServiceSecret());
    serviceToSecretMapping.put(
        PLATFORM_CONFIG_SERVICE.getServiceId(), configuration.getNextGenConfig().getPlatformConfigServiceSecret());
    serviceToSecretMapping.put(
        HARNESS_STATUSPAGE.getServiceId(), configuration.getNextGenConfig().getHarnessStatuspageServiceSecret());
    environment.jersey().register(
        new InternalApiAuthFilter(getAuthFilterPredicate(InternalApi.class), null, serviceToSecretMapping));
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthenticationExemptedRequestsPredicate() {
    return getAuthFilterPredicate(PublicApi.class)
        .or(resourceInfoAndRequest
            -> resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().equals("/version")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/swagger")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/swagger.json")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/openapi.json")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith(
                    "/swagger.yaml"));
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthFilterPredicate(
      Class<? extends Annotation> annotation) {
    return resourceInfoAndRequest
        -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
               && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(annotation) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(annotation) != null);
  }

  private void initializeEnforcementService(Injector injector, NextGenConfiguration configuration) {
    injector.getInstance(FeatureRestrictionLoader.class).run(configuration);
  }

  private void initializeEnforcementSdk(Injector injector) {
    RestrictionUsageRegisterConfiguration restrictionUsageRegisterConfiguration =
        RestrictionUsageRegisterConfiguration.builder()
            .restrictionNameClassMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends RestrictionUsageInterface>>builder()
                    .put(FeatureRestrictionName.TEST2, ExampleStaticLimitUsageImpl.class)
                    .put(FeatureRestrictionName.TEST3, ExampleRateLimitUsageImpl.class)
                    .put(FeatureRestrictionName.TEST6, ExampleRateLimitUsageImpl.class)
                    .put(FeatureRestrictionName.TEST7, ExampleStaticLimitUsageImpl.class)
                    .put(FeatureRestrictionName.MULTIPLE_API_KEYS, ApiKeyRestrictionsUsageImpl.class)
                    .put(FeatureRestrictionName.MULTIPLE_API_TOKENS, ApiTokenRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MULTIPLE_PROJECTS, ProjectRestrictionsUsageImpl.class)
                    .put(FeatureRestrictionName.MULTIPLE_ORGANIZATIONS, OrgRestrictionsUsageImpl.class)
                    .put(FeatureRestrictionName.MULTIPLE_SECRETS, SecretRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MULTIPLE_USER_GROUPS, UserGroupRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MULTIPLE_USERS, UsersRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MULTIPLE_SERVICE_ACCOUNTS, ServiceAccountRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MULTIPLE_VARIABLES, VariableRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.MULTIPLE_CONNECTORS, ConnectorRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.SERVICES, ServiceRestrictionsUsageImpl.class)
                    .put(FeatureRestrictionName.CCM_K8S_CLUSTERS, CloudCostK8sConnectorRestrictionsUsageImpl.class)
                    .put(FeatureRestrictionName.DEPLOYMENTS_PER_MONTH, DeploymentsPerMonthRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.INITIAL_DEPLOYMENTS, InitialDeploymentRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.SECRET_MANAGERS, SecretManagerRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.CUSTOM_ROLES, RoleRestrictionUsageImpl.class)
                    .put(FeatureRestrictionName.CUSTOM_RESOURCE_GROUPS, ResourceGroupRestrictionUsageImpl.class)
                    .build())
            .build();
    CustomRestrictionRegisterConfiguration customConfig =
        CustomRestrictionRegisterConfiguration.builder()
            .customRestrictionMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends CustomRestrictionInterface>>builder()
                    .put(FeatureRestrictionName.TEST4, ExampleCustomImpl.class)
                    .put(FeatureRestrictionName.DEPLOYMENTS, DeploymentRestrictionUsageImpl.class)
                    .build())
            .build();

    injector.getInstance(EnforcementSdkRegisterService.class)
        .initialize(restrictionUsageRegisterConfiguration, customConfig);
  }
}

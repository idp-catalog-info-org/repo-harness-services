/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.authorization.AuthorizationServiceHeader.BEARER;
import static io.harness.authorization.AuthorizationServiceHeader.MANAGER;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SETTINGS;
import static io.harness.lock.DistributedLockImplementation.REDIS;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_POLL_CONFIGURATION;

import io.harness.accesscontrol.AccessControlAdminClientConfiguration;
import io.harness.accesscontrol.AccessControlAdminClientModule;
import io.harness.accesscontrol.publicaccess.PublicAccessClientModule;
import io.harness.agent.convert.V1ToV0StepGroupConverter;
import io.harness.agent.expansion.AgentTemplateExpansionService;
import io.harness.aisre.AiSreClientModule;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.api.ExecutionOutboxDao;
import io.harness.api.KafkaOutboxDao;
import io.harness.api.impl.ExecutionOutboxDaoImpl;
import io.harness.api.impl.ExecutionOutboxServiceImpl;
import io.harness.api.impl.KafkaOutboxDaoImpl;
import io.harness.api.impl.KafkaOutboxServiceImpl;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.beans.HarnessCodeServiceConfig;
import io.harness.beans.entities.AnnotationsConfig;
import io.harness.cache.HarnessCacheManager;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.cd.CDEventInterceptor;
import io.harness.cdstage.CDEventInterceptorImpl;
import io.harness.cdstage.CDNGStageSummaryResourceClientModule;
import io.harness.ci.CiServiceResourceClientModule;
import io.harness.ci.execution.execution.GitBuildStatusUtilityImpl;
import io.harness.ci.execution.integrationstage.utils.HarnessTokenUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.ff.impl.CIFeatureFlagServiceImpl;
import io.harness.ci.metrics.ExecutionMetricsService;
import io.harness.ci.metrics.ExecutionMetricsServiceImpl;
import io.harness.ci.permission.PipelinePermissionMapperModule;
import io.harness.ci.utils.CIAnnotationsServiceClientModule;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.cistatus.service.azurerepo.AzureRepoServiceImpl;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.cistatus.service.bitbucket.BitbucketServiceImpl;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.cistatus.service.gitlab.GitlabServiceImpl;
import io.harness.cleanup.config.OrchestrationGraphCacheCleanupConfig;
import io.harness.client.DelegateSelectionLogHttpClientModule;
import io.harness.config.ModuleSpecificInfo;
import io.harness.config.OrchestrationModuleConfig;
import io.harness.config.PipelineServiceIteratorsConfig;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.cvng.client.HealthSourceResourceClientModule;
import io.harness.datastructures.DistributedBackend;
import io.harness.datastructures.EphemeralServiceModule;
import io.harness.delay.DelayEvent;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorConfig;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorStateCache;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorStateStore;
import io.harness.engine.executions.node.config.StuckNodeExecutionsMarkingConfig;
import io.harness.engine.expressions.NotificationExpressionsResolutionServiceImpl;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.environment.EnvironmentResourceClientModule;
import io.harness.eventPoll.KafkaOutboxEventHandler;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.ff.FeatureFlagModule;
import io.harness.filestore.FileStoreClientModule;
import io.harness.filter.FilterType;
import io.harness.filter.FiltersModule;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.fme.FMEClientModule;
import io.harness.gitsync.GitSyncManagerClientModule;
import io.harness.goconvert.GoConvertGrpcClientModule;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.server.PipelineServiceGrpcModule;
import io.harness.harnessid.client.HarnessIdClientModule;
import io.harness.harnessid.client.HarnessIdServiceConfig;
import io.harness.hsqs.client.HsqsServiceClientModule;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.licensing.enforcement.client.FlexEnforcementClientModule;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.logstreaming.LogStreamingModule;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.logstreaming.NGLogStreamingClientFactory;
import io.harness.manage.ManagedExecutorService;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.monitoring.PipelineDeleteCleanupMonitorService;
import io.harness.monitoring.PipelineDeleteCleanupMonitorServiceImpl;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngmanager.NgConnectorManagerClientModule;
import io.harness.ngsettings.client.remote.NGSettingsClientModule;
import io.harness.ngtriggers.outbox.TriggerOutboxEventHandler;
import io.harness.notification.modules.SmtpConfigClientModule;
import io.harness.notify.NotifyResource;
import io.harness.notify.NotifyResourceImpl;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.objectstore.ObjectStoreClientFactory;
import io.harness.oidc.OidcResourceClientModule;
import io.harness.opa.gitx.OpaGitxStatusRepository;
import io.harness.opaclient.OpaClientModule;
import io.harness.organization.OrganizationClientModule;
import io.harness.outbox.OutboxPollConfiguration;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.module.TransactionOutboxModule;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.plancreator.steps.pluginstep.ContainerStepV2PluginProvider;
import io.harness.pms.approval.ApprovalResourceService;
import io.harness.pms.approval.ApprovalResourceServiceImpl;
import io.harness.pms.approval.api.ApprovalsApiImpl;
import io.harness.pms.approval.custom.CustomApprovalHelperServiceImpl;
import io.harness.pms.approval.jira.JiraApprovalHelperServiceImpl;
import io.harness.pms.approval.notification.stagemetadata.StageMetadataNotificationHelper;
import io.harness.pms.approval.notification.stagemetadata.StageMetadataNotificationHelperImpl;
import io.harness.pms.approval.resources.ApprovalResource;
import io.harness.pms.approval.resources.ApprovalResourceImpl;
import io.harness.pms.approval.servicenow.ServiceNowApprovalHelperServiceImpl;
import io.harness.pms.barriers.resources.PMSBarrierResource;
import io.harness.pms.barriers.resources.PMSBarrierResourceImpl;
import io.harness.pms.barriers.service.PMSBarrierService;
import io.harness.pms.barriers.service.PMSBarrierServiceImpl;
import io.harness.pms.conversion.helper.PipelineConversionApiImpl;
import io.harness.pms.conversion.service.ConversionJobService;
import io.harness.pms.conversion.service.ConversionJobServiceImpl;
import io.harness.pms.dashboard.PMSLandingDashboardResource;
import io.harness.pms.dashboard.PMSLandingDashboardResourceImpl;
import io.harness.pms.dashboard.PMSLandingDashboardService;
import io.harness.pms.dashboard.PMSLandingDashboardServiceImpl;
import io.harness.pms.dashboard.PipelineDashboardOverviewResource;
import io.harness.pms.dashboard.PipelineDashboardOverviewResourceImpl;
import io.harness.pms.dashboard.PipelineDashboardOverviewResourceV2;
import io.harness.pms.dashboard.PipelineDashboardOverviewResourceV2Impl;
import io.harness.pms.dataretention.PipelineRetentionResource;
import io.harness.pms.dataretention.PipelineRetentionResourceImpl;
import io.harness.pms.event.entitycrud.AccountEntityCrudStreamListener;
import io.harness.pms.event.entitycrud.PipelineEntityCRUDStreamListener;
import io.harness.pms.event.entitycrud.PipelineSettingCRUDStreamListener;
import io.harness.pms.event.entitycrud.ProjectEntityCrudStreamListener;
import io.harness.pms.event.overviewLandingPage.kafka.CdcKafkaConfig;
import io.harness.pms.event.overviewLandingPage.kafka.CdcKafkaConsumerConfig;
import io.harness.pms.event.pollingevent.PollingEventStreamListener;
import io.harness.pms.event.triggerwebhookevent.TriggerExecutionEventStreamListener;
import io.harness.pms.events.base.PmsMessageListener;
import io.harness.pms.expressions.PMSExpressionEvaluatorProvider;
import io.harness.pms.health.HealthResource;
import io.harness.pms.health.HealthResourceImpl;
import io.harness.pms.helpers.PipelineServiceLogBaseUrlProvider;
import io.harness.pms.helpers.PipelineServiceLogServiceUrlProvider;
import io.harness.pms.inputfile.InputFileResource;
import io.harness.pms.inputset.mappers.InputSetFilterPropertiesMapper;
import io.harness.pms.jira.JiraStepHelperServiceImpl;
import io.harness.pms.migration.PipelineAccessControlMigrationModule;
import io.harness.pms.ngpipeline.inputs.api.InputsApiImpl;
import io.harness.pms.ngpipeline.inputs.service.PMSInputsService;
import io.harness.pms.ngpipeline.inputs.service.PMSInputsServiceImpl;
import io.harness.pms.ngpipeline.inputset.api.InputSetsApiImpl;
import io.harness.pms.ngpipeline.inputset.resources.InputSetInlineHcMigrationResource;
import io.harness.pms.ngpipeline.inputset.resources.InputSetInlineHcMigrationResourceImpl;
import io.harness.pms.ngpipeline.inputset.resources.InputSetResourcePMS;
import io.harness.pms.ngpipeline.inputset.resources.InputSetResourcePMSImpl;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetInlineHcMigrationService;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.ngpipeline.inputset.service.impl.PMSInputSetInlineHcMigrationServiceImpl;
import io.harness.pms.ngpipeline.inputset.service.impl.PMSInputSetServiceImpl;
import io.harness.pms.notification.WebhookNotificationService;
import io.harness.pms.notification.WebhookNotificationServiceImpl;
import io.harness.pms.notification.gitstatus.GitStatusUpdateNotifier;
import io.harness.pms.notification.gitstatus.GitStatusUpdateNotifierImpl;
import io.harness.pms.notification.helper.ApprovalNotificationHandlerImpl;
import io.harness.pms.notificationbodyresolution.NotificationBodyResolutionInterface;
import io.harness.pms.opa.gitx.pipeline.PipelineEntityOpaStatusRepository;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.opa.service.PMSOpaService;
import io.harness.pms.opa.service.PMSOpaServiceImpl;
import io.harness.pms.orchestrationgovernor.OrchestrationGovernorResource;
import io.harness.pms.orchestrationgovernor.OrchestrationGovernorResourceImpl;
import io.harness.pms.outbox.KafkaOutboxEventHandlerImpl;
import io.harness.pms.outbox.PMSOutboxEventHandler;
import io.harness.pms.outbox.PipelineOutboxEventHandler;
import io.harness.pms.pipeline.BranchSequenceResource;
import io.harness.pms.pipeline.InlineHcRollbackResource;
import io.harness.pms.pipeline.PipelineAdminResource;
import io.harness.pms.pipeline.PipelineAnnotationsResource;
import io.harness.pms.pipeline.PipelineInlineHcMigrationResource;
import io.harness.pms.pipeline.PipelineResource;
import io.harness.pms.pipeline.PipelineSdkPrioritySupport;
import io.harness.pms.pipeline.agent.AgentTemplateExpansionServiceImpl;
import io.harness.pms.pipeline.annotations.AnnotationFileService;
import io.harness.pms.pipeline.annotations.AnnotationFileServiceImpl;
import io.harness.pms.pipeline.annotations.PipelineAnnotationsService;
import io.harness.pms.pipeline.annotations.PipelineAnnotationsServiceImpl;
import io.harness.pms.pipeline.api.PipelinesApiImpl;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceServiceImpl;
import io.harness.pms.pipeline.mappers.PipelineFilterPropertiesMapper;
import io.harness.pms.pipeline.resource.BranchSequenceResourceImpl;
import io.harness.pms.pipeline.resource.InlineHcRollbackResourceImpl;
import io.harness.pms.pipeline.resource.InputFileResourceImpl;
import io.harness.pms.pipeline.resource.PipelineAdminResourceImpl;
import io.harness.pms.pipeline.resource.PipelineAnnotationsResourceImpl;
import io.harness.pms.pipeline.resource.PipelineInlineHcMigrationResourceImpl;
import io.harness.pms.pipeline.resource.PipelineResourceImpl;
import io.harness.pms.pipeline.service.BranchSequenceService;
import io.harness.pms.pipeline.service.BranchSequenceServiceImpl;
import io.harness.pms.pipeline.service.InlineHcRollbackServiceImpl;
import io.harness.pms.pipeline.service.InputFileService;
import io.harness.pms.pipeline.service.InputFileServiceImpl;
import io.harness.pms.pipeline.service.PMSPipelineInlineHcMigrationServiceImpl;
import io.harness.pms.pipeline.service.PMSPipelineServiceImpl;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.PMSYamlSchemaServiceImpl;
import io.harness.pms.pipeline.service.PipelineAdminResourceService;
import io.harness.pms.pipeline.service.PipelineAdminResourceServiceImpl;
import io.harness.pms.pipeline.service.PipelineDashboardService;
import io.harness.pms.pipeline.service.PipelineDashboardServiceImpl;
import io.harness.pms.pipeline.service.PipelineEnforcementServiceImpl;
import io.harness.pms.pipeline.service.PipelineMetadataServiceImpl;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.pipeline.service.intfc.InlineHcRollbackService;
import io.harness.pms.pipeline.service.intfc.PMSPipelineInlineHcMigrationService;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.pipeline.service.yamlConversion.PipelineYamlConversionEntityService;
import io.harness.pms.pipeline.service.yamlConversion.PipelineYamlConversionEntityServiceImpl;
import io.harness.pms.pipeline.service.yamlschema.approval.ApprovalYamlSchemaService;
import io.harness.pms.pipeline.service.yamlschema.approval.ApprovalYamlSchemaServiceImpl;
import io.harness.pms.pipeline.service.yamlschema.cache.PartialSchemaDTOWrapperValue;
import io.harness.pms.pipeline.service.yamlschema.cache.YamlSchemaDetailsWrapperValue;
import io.harness.pms.pipeline.service.yamlschema.featureflag.FeatureFlagYamlService;
import io.harness.pms.pipeline.service.yamlschema.featureflag.FeatureFlagYamlServiceImpl;
import io.harness.pms.pipeline.service.yamlschema.pipelinestage.PipelineStageYamlSchemaService;
import io.harness.pms.pipeline.service.yamlschema.pipelinestage.PipelineStageYamlSchemaServiceImpl;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.pipeline.validation.async.service.impl.PipelineAsyncValidationServiceImpl;
import io.harness.pms.pipeline.validation.service.PipelineValidationServiceImpl;
import io.harness.pms.pipeline.validation.service.intfc.PipelineValidationService;
import io.harness.pms.plan.creation.lookup.NodeTypeLookupServiceImpl;
import io.harness.pms.plan.creation.lookup.intfc.NodeTypeLookupService;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticRule;
import io.harness.pms.plan.execution.dryrun.semantic.rules.CloneCodebaseSanityRule;
import io.harness.pms.plan.execution.dryrun.semantic.rules.CloudCiDelegateConnectorRule;
import io.harness.pms.plan.execution.dryrun.semantic.rules.ConnectorTypeRule;
import io.harness.pms.plan.execution.dryrun.semantic.rules.ReferencedEntitiesExistRule;
import io.harness.pms.plan.execution.helper.PlanExecutionResourceImpl;
import io.harness.pms.plan.execution.mapper.PipelineExecutionFilterPropertiesMapper;
import io.harness.pms.plan.execution.mapper.QueuedPipelineFilterPropertiesMapper;
import io.harness.pms.plan.execution.resources.PlanExecutionResource;
import io.harness.pms.plan.execution.service.ExecutionGraphService;
import io.harness.pms.plan.execution.service.ExecutionGraphServiceImpl;
import io.harness.pms.plan.execution.service.ExpressionEvaluatorService;
import io.harness.pms.plan.execution.service.ExpressionEvaluatorServiceImpl;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryServiceImpl;
import io.harness.pms.plan.execution.service.QueuedPipelineService;
import io.harness.pms.plan.execution.service.impl.PMSExecutionServiceImpl;
import io.harness.pms.plan.execution.service.impl.QueuedPipelineServiceImpl;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.plugin.ContainerStepV2PluginProviderImpl;
import io.harness.pms.preflight.service.PreflightServiceImpl;
import io.harness.pms.preflight.service.intfc.PreflightService;
import io.harness.pms.rbac.validator.PipelineRbacService;
import io.harness.pms.rbac.validator.PipelineRbacServiceImpl;
import io.harness.pms.resourceconstraints.resources.PMSResourceConstraintResource;
import io.harness.pms.resourceconstraints.resources.PMSResourceConstraintResourceImpl;
import io.harness.pms.resourceconstraints.service.PMSResourceConstraintService;
import io.harness.pms.resourceconstraints.service.PMSResourceConstraintServiceImpl;
import io.harness.pms.resourcerestraint.reconciliation.config.ResourceRestraintReconciliationConfig;
import io.harness.pms.schema.PmsYamlSchemaResource;
import io.harness.pms.schema.PmsYamlSchemaResourceImpl;
import io.harness.pms.sdk.core.plugin.PluginInfoProvider;
import io.harness.pms.sdk.helper.PipelineSdkPrioritySupportImpl;
import io.harness.pms.servicenow.ServiceNowStepHelperServiceImpl;
import io.harness.pms.statusreconciliation.config.ExecutionStatusReconciliationConfig;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.pms.template.service.PipelineRefreshServiceImpl;
import io.harness.pms.triggers.webhook.service.TriggerCustomWebhookExecutionService;
import io.harness.pms.triggers.webhook.service.TriggerWebhookEventExecutionService;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionService;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionServiceV2;
import io.harness.pms.triggers.webhook.service.impl.TriggerCustomWebhookExecutionServiceImpl;
import io.harness.pms.triggers.webhook.service.impl.TriggerWebhookEventExecutionServiceImpl;
import io.harness.pms.triggers.webhook.service.impl.TriggerWebhookExecutionServiceImpl;
import io.harness.pms.triggers.webhook.service.impl.TriggerWebhookExecutionServiceImplV2;
import io.harness.pms.wait.WaitStepResource;
import io.harness.pms.wait.WaitStepResourceImpl;
import io.harness.polling.client.PollResourceClientModule;
import io.harness.postgres.PostgresDBConfig;
import io.harness.postgres.PostgresDBService;
import io.harness.postgres.PostgresDBServiceImpl;
import io.harness.project.ProjectClientModule;
import io.harness.qwietserviceclient.QwietServiceClientModule;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.resourcegroupclient.ResourceGroupClientModule;
import io.harness.runner.plugin.PluginConfigClientModule;
import io.harness.scopeinfoclient.ScopeInfoClientModule;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.secretusage.SecretRuntimeUsageService;
import io.harness.secretusage.SecretRuntimeUsageServiceImpl;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.PipelineServiceModuleRegistrars;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.service.LogServiceUrlProvider;
import io.harness.serviceaccount.ServiceAccountClientModule;
import io.harness.spec.server.pipeline.v1.ApprovalsApi;
import io.harness.spec.server.pipeline.v1.InputSetsApi;
import io.harness.spec.server.pipeline.v1.InputsApi;
import io.harness.spec.server.pipeline.v1.PipelineConversionApi;
import io.harness.spec.server.pipeline.v1.PipelinesApi;
import io.harness.ssca.SSCAManagerServiceClientModule;
import io.harness.steps.PodCleanUpModule;
import io.harness.steps.approval.ApprovalNotificationHandler;
import io.harness.steps.approval.plugin.ApprovalPluginInfoProvider;
import io.harness.steps.approval.step.custom.CustomApprovalHelperService;
import io.harness.steps.approval.step.entities.JiraApprovalHelperService;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalHelperService;
import io.harness.steps.executable.LogBaseUrlProvider;
import io.harness.steps.jira.JiraStepHelperService;
import io.harness.steps.opa.plugin.OPAEvaluationPluginInfoProvider;
import io.harness.steps.servicenow.ServiceNowStepHelperService;
import io.harness.steps.shellscript.ShellScriptHelperService;
import io.harness.steps.shellscript.ShellScriptHelperServiceImpl;
import io.harness.steps.shellscript.ShellScriptHelperServiceImplOld;
import io.harness.steps.shellscript.ShellScriptHelperServiceOld;
import io.harness.steps.wait.WaitStepService;
import io.harness.steps.wait.WaitStepServiceImpl;
import io.harness.steps.workloadidentity.WorkloadIdentityTokenService;
import io.harness.steps.workloadidentity.WorkloadIdentityTokenServiceImpl;
import io.harness.stoserviceclient.STOServiceClientModule;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.template.TemplateResourceClientModule;
import io.harness.threading.ScalingThreadPoolExecutor;
import io.harness.threading.ThreadPool;
import io.harness.threading.ThreadPoolConfig;
import io.harness.time.TimeModule;
import io.harness.timescaledb.JooqModuleV1;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.TimeScaleDBServiceImpl;
import io.harness.timescaledb.metrics.HExecuteListener;
import io.harness.token.TokenClientModule;
import io.harness.tracing.AbstractPersistenceTracerModule;
import io.harness.unified.service.NgServiceResourceClientModule;
import io.harness.user.UserClientModule;
import io.harness.usergroups.UserGroupClientModule;
import io.harness.userng.UserNGClientModule;
import io.harness.variable.VariableClientModule;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.NotifyResponse;
import io.harness.waiter.misc.ProgressUpdate;
import io.harness.waiter.misc.WaitInstance;
import io.harness.waiter.persistence.WaitNotifyCollectionNameResolver;
import io.harness.webhook.WebhookEventClientModule;
import io.harness.webhook.remote.GitxWebhooksClientModule;
import io.harness.webhook.remote.GitxWebhooksEventsClientModule;
import io.harness.webhook.remote.NGWebhookClientModule;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.schema.client.YamlSchemaClientModule;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.jackson.Jackson;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import lombok.extern.slf4j.Slf4j;
import org.jooq.ExecuteListener;
import org.redisson.api.RedissonClient;
import org.springframework.core.convert.converter.Converter;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_TRIGGERS, HarnessModuleComponent.CDS_PIPELINE,
        HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(PIPELINE)
@Slf4j
public class PipelineServiceModule extends AbstractModule {
  private final PipelineServiceConfiguration configuration;
  private final MetricRegistry threadPoolMetricRegistry;
  private static PipelineServiceModule instance;
  // TODO: Take this from application.

  private PipelineServiceModule(PipelineServiceConfiguration configuration, MetricRegistry threadPoolMetricRegistry) {
    this.configuration = configuration;
    this.threadPoolMetricRegistry = threadPoolMetricRegistry;
  }

  public static PipelineServiceModule getInstance(
      PipelineServiceConfiguration appConfig, MetricRegistry threadPoolMetricRegistry) {
    if (instance == null) {
      instance = new PipelineServiceModule(appConfig, threadPoolMetricRegistry);
    }
    return instance;
  }

  @Override
  protected void configure() {
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    install(new AbstractPersistenceTracerModule() {
      @Override
      protected EventsFrameworkConfiguration eventsFrameworkConfiguration() {
        return configuration.getEventsFrameworkConfiguration();
      }

      @Override
      protected String serviceIdProvider() {
        return PIPELINE_SERVICE.getServiceId();
      }
    });
    install(
        PipelineServiceGrpcModule.getInstance(configuration.isUseRemoteGrpcForPipeline(), threadPoolMetricRegistry));
    install(new PipelinePersistenceModule());
    install(DelegateServiceDriverModule.getInstance(true, true));
    install(OrchestrationModule.getInstance(
        OrchestrationModuleConfig.builder()
            .serviceName("PIPELINE")
            .expressionEvaluatorProvider(new PMSExpressionEvaluatorProvider())
            .withPMS(false)
            .isPipelineService(true)
            .orchestrationPoolConfig(configuration.getOrchestrationPoolConfig())
            .sdkResponseThreadPoolConfig(configuration.getSdkResponseEventPoolConfig())
            .observerThreadPoolConfig(configuration.getNodeExecutionObserverPoolConfig())
            .ciSecretResolutionPoolConfig(configuration.getCiSecretResolutionPoolConfig())
            .eventsFrameworkConfiguration(configuration.getEventsFrameworkConfiguration())
            .accountClientId(PIPELINE_SERVICE.getServiceId())
            .accountServiceHttpClientConfig(configuration.getManagerClientConfig())
            .accountServiceSecret(configuration.getManagerServiceSecret())
            .useFeatureFlagService(true)
            .orchestrationRedisEventsConfig(configuration.getOrchestrationRedisEventsConfig())
            .orchestrationLogConfiguration(configuration.getOrchestrationLogConfiguration())
            .orchestrationRestrictionConfiguration(configuration.getOrchestrationRestrictionConfiguration())
            .licenseClientServiceSecret(configuration.getNgManagerServiceSecret())
            .licenseClientConfig(configuration.getNgManagerServiceHttpClientConfig())
            .licenseClientId(PIPELINE_SERVICE.getServiceId())
            .expandedJsonLockConfig(configuration.getExpandedJsonLockConfig())
            .stuckExecutionDetectorEnabled(configuration.isStuckExecutionDetectorEnabled())
            .pipelineExecutionClusterStepConcurrencyLimit(
                configuration.getPipelineExecutionClusterStepConcurrencyLimit())
            .pipelineExecutionDefaultMaxLeafStepConcurrency(
                configuration.getPipelineExecutionDefaultMaxLeafStepConcurrency())
            .streamPerServiceConfiguration(configuration.isStreamPerServiceConfiguration())
            .stepConcurrencyCounterMutationEnabled(configuration.getStepConcurrencyCounterMutationEnabled() == null
                || configuration.getStepConcurrencyCounterMutationEnabled())
            .stepConcurrencyQueueStoreEnabled(configuration.getStepConcurrencyQueueStoreEnabled() == null
                || configuration.getStepConcurrencyQueueStoreEnabled())
            .stepConcurrencyGateMode(configuration.getStepConcurrencyGateMode() == null
                    ? "shadow"
                    : configuration.getStepConcurrencyGateMode())
            .pipelineExecutionCounterRebuildJobEnabled(
                configuration.getPipelineExecutionCounterRebuildJobEnabled() == null
                || configuration.getPipelineExecutionCounterRebuildJobEnabled())
            .useDbQueueForPlanCreation(
                configuration.getUseDbQueueForPlanCreation() != null && configuration.getUseDbQueueForPlanCreation())
            .planCreationDbQueueBatchSize(configuration.getPlanCreationDbQueueBatchSize() == null
                    ? 100
                    : configuration.getPlanCreationDbQueueBatchSize())
            .planConcurrencyCounterMutationEnabled(configuration.getPlanConcurrencyCounterMutationEnabled() == null
                || configuration.getPlanConcurrencyCounterMutationEnabled())
            .planConcurrencyGateMode(configuration.getPlanConcurrencyGateMode() == null
                    ? "shadow"
                    : configuration.getPlanConcurrencyGateMode())
            .planConcurrencyRebuildJobEnabled(configuration.getPlanConcurrencyRebuildJobEnabled() == null
                || configuration.getPlanConcurrencyRebuildJobEnabled())
            .build(),
        threadPoolMetricRegistry));
    install(RetentionModule.getInstance(
        RetentionModuleConfig.builder()
            .storeConfig(configuration.getObjectStoreConfig().getStoreConfig())
            .dataRetentionConfig(configuration.getDataRetentionConfig())
            .bucketConfig(configuration.getObjectStoreConfig().getDataRetentionBucketConfig())
            .elasticSearchDBConfig(configuration.getElasticSearchDBConfig())
            .executionRetentionSyncServicePoolConfig(configuration.getExecutionRetentionSyncServicePoolConfig())
            .build(),
        threadPoolMetricRegistry));

    HarnessCodeServiceConfig harnessCodeServiceConfig =
        HarnessCodeServiceConfig.builder()
            .serviceSecret(configuration.getHarnessCodeServiceSecret())
            .apiUrl(configuration.getHarnessCodeServiceClientConfig().getBaseUrl())
            .gitUrl(configuration.getHarnessCodeGitBaseUrl())
            .build();
    install(OrchestrationStepsModule.getInstance(configuration.getOrchestrationStepConfig(), harnessCodeServiceConfig));
    HarnessIdServiceConfig harnessIdYamlConfig = configuration.getHarnessIdClientConfig();
    install(new HarnessIdClientModule(
        HarnessIdServiceConfig.builder()
            .grpcClientConfig(harnessIdYamlConfig != null ? harnessIdYamlConfig.getGrpcClientConfig() : null)
            .restClientConfig(harnessIdYamlConfig != null ? harnessIdYamlConfig.getRestClientConfig() : null)
            .callerServiceName(PIPELINE_SERVICE)
            .build()));
    install(FeatureFlagModule.getInstance());
    install(JooqModuleV1.getInstance(configuration.getTimeScaleDBConfig() != null ? configuration.getTimeScaleDBConfig()
                                                                                  : TimeScaleDBConfig.builder().build(),
        configuration.getSecondaryTimeScaleDBConfig() != null ? configuration.getSecondaryTimeScaleDBConfig()
                                                              : TimeScaleDBConfig.builder().build(),
        configuration.getPostgresDBConfig() != null ? configuration.getPostgresDBConfig()
                                                    : PostgresDBConfig.builder().build()));
    install(OrchestrationVisualizationModule.getInstance(configuration.getEventsFrameworkConfiguration(),
        configuration.getOrchestrationVisualizationThreadPoolConfig(), configuration.getGraphConsumerSleepIntervalMs(),
        threadPoolMetricRegistry));
    install(PodCleanUpModule.getInstance(configuration.getPodCleanUpThreadPoolConfig(), threadPoolMetricRegistry));
    install(PrimaryVersionManagerModule.getInstance());
    install(new DelegateServiceDriverGrpcClientModule(configuration.getManagerServiceSecret(),
        configuration.getManagerTarget(), configuration.getManagerAuthority(), true));
    ServiceHttpClientConfig secretConnectorServiceConfig = configuration.isSecretConnectorServiceEnabled()
        ? configuration.getSecretConnectorServiceClientConfig()
        : configuration.getNgManagerServiceHttpClientConfig();
    String secretConnectorServiceSecret = configuration.isSecretConnectorServiceEnabled()
        ? configuration.getSecretConnectorServiceSecret()
        : configuration.getNgManagerServiceSecret();
    install(new ConnectorResourceClientModule(secretConnectorServiceConfig, secretConnectorServiceSecret,
        PIPELINE_SERVICE.getServiceId(), ClientMode.PRIVILEGED));
    install(new SecretNGManagerClientModule(
        secretConnectorServiceConfig, secretConnectorServiceSecret, PIPELINE_SERVICE.getServiceId()));
    install(new OidcResourceClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.toString(), ClientMode.PRIVILEGED));
    install(new TemplateResourceClientModule(configuration.getTemplateServiceClientConfig(),
        configuration.getTemplateServiceSecret(), PIPELINE_SERVICE.toString(), false));
    install(new GitSyncManagerClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.toString()));
    install(
        new CiServiceResourceClientModule(configuration.getCiServiceClientConfig(), configuration.getCiServiceSecret(),
            PIPELINE_SERVICE.toString(), configuration.isContainerStepConfigureWithCi()));
    if (configuration.getGoConvertConnectionConfig() != null) {
      install(new GoConvertGrpcClientModule(configuration.getGoConvertConnectionConfig()));
    }
    install(HealthSourceResourceClientModule.getInstance(
        configuration.getCvngClientConfig(), configuration.getCvngServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(
        NGTriggersModule.getInstance(configuration.getTriggerConfig(), configuration.getPipelineServiceClientConfig(),
            configuration.getPipelineServiceSecret(), configuration.getHarnessCodeServiceClientConfig(),
            configuration.getHarnessCodeGitBaseUrl(), configuration.getHarnessCodeServiceSecret(),
            configuration.getNgManagerServiceHttpClientConfig(), configuration.getNgManagerServiceSecret()));
    install(PersistentLockModule.getInstance());
    install(EphemeralServiceModule.getInstance());
    install(TimeModule.getInstance());
    install(FiltersModule.getInstance());
    install(YamlSdkModule.getInstance());
    install(AccessControlClientModule.getInstance(
        configuration.getAccessControlClientConfiguration(), PIPELINE_SERVICE.getServiceId()));
    install(new FlexEnforcementClientModule(
        configuration.getFlexEnforcementClientConfig().toBuilder().serviceId(PIPELINE_SERVICE.getServiceId()).build()));
    install(new PublicAccessClientModule(
        configuration.getAccessControlClientConfiguration().getAccessControlServiceConfig(),
        configuration.getAccessControlClientConfiguration().getAccessControlServiceSecret(),
        PIPELINE_SERVICE.toString()));
    install(new PollResourceClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), MANAGER.getServiceId()));

    ServiceHttpClientConfig rhsConfig = configuration.isRhsEnabled()
        ? configuration.getRhsClientConfig()
        : configuration.getNgManagerServiceHttpClientConfig();
    String rhsSecret =
        configuration.isRhsEnabled() ? configuration.getRhsServiceSecret() : configuration.getNgManagerServiceSecret();
    install(new OrganizationClientModule(rhsConfig, rhsSecret, PIPELINE_SERVICE.getServiceId()));
    install(new ProjectClientModule(rhsConfig, rhsSecret, PIPELINE_SERVICE.getServiceId()));
    install(new ResourceGroupClientModule(configuration.getResourceGroupClientConfig().getServiceConfig(),
        configuration.getResourceGroupClientConfig().getSecret(), PIPELINE_SERVICE.getServiceId()));
    install(
        YamlSchemaClientModule.getInstance(configuration.getYamlSchemaClientConfig(), PIPELINE_SERVICE.getServiceId()));
    install(new AccessControlAdminClientModule(
        AccessControlAdminClientConfiguration.builder()
            .mockAccessControlService(false)
            .accessControlServiceSecret(
                configuration.getAccessControlClientConfiguration().getAccessControlServiceSecret())
            .accessControlServiceConfig(
                configuration.getAccessControlClientConfiguration().getAccessControlServiceConfig())
            .build(),
        PIPELINE_SERVICE.getServiceId()));
    install(new UserClientModule(configuration.getManagerClientConfig(), configuration.getManagerServiceSecret(),
        PIPELINE_SERVICE.getServiceId()));
    install(new UserGroupClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new PipelineAccessControlMigrationModule());
    install(new ServiceAccountClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(UserNGClientModule.getInstance(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    ServiceHttpClientConfig platformConfigServiceConfig = configuration.isPlatformConfigServiceEnabled()
        ? configuration.getPlatformConfigServiceClientConfig()
        : configuration.getNgManagerServiceHttpClientConfig();
    String platformConfigServiceSecret = configuration.isPlatformConfigServiceEnabled()
        ? configuration.getPlatformConfigServiceSecret()
        : configuration.getNgManagerServiceSecret();
    install(new NGSettingsClientModule(
        platformConfigServiceConfig, platformConfigServiceSecret, PIPELINE_SERVICE.getServiceId(), false));
    if (configuration.getAnnotationsBaseUrl() != null && configuration.getAnnotationsSecret() != null) {
      AnnotationsConfig annotationsConfig = AnnotationsConfig.builder()
                                                .baseUrl(configuration.getAnnotationsBaseUrl())
                                                .secret(configuration.getAnnotationsSecret())
                                                .build();
      install(new CIAnnotationsServiceClientModule(annotationsConfig));
    }
    install(new NgConnectorManagerClientModule(
        configuration.getManagerClientConfig(), configuration.getManagerServiceSecret()));
    install(new NGWebhookClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new GitxWebhooksClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new GitxWebhooksEventsClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new DelegateSelectionLogHttpClientModule(configuration.getManagerClientConfig(),
        configuration.getManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new PipelineServiceEventsFrameworkModule(configuration.getEventsFrameworkConfiguration(),
        configuration.getPipelineRedisEventsConfig(), configuration.getDebeziumConsumersConfigs(),
        configuration.getEventsFrameworkSnapshotConfiguration(),
        configuration.isShouldUseEventsFrameworkSnapshotDebezium()));
    install(new EntitySetupUsageClientModule(this.configuration.getNgManagerServiceHttpClientConfig(),
        this.configuration.getManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new LogStreamingModule(configuration.getLogStreamingServiceConfig().getBaseUrl()));
    install(new OpaClientModule(configuration.getOpaClientConfig(), configuration.getPolicyManagerSecret(),
        PIPELINE_SERVICE.getServiceId(), false));
    install(
        new AuditClientModule(this.configuration.getAuditClientConfig(), this.configuration.getManagerServiceSecret(),
            PIPELINE_SERVICE.getServiceId(), this.configuration.isEnableAudit()));
    install(new TransactionOutboxModule(DEFAULT_OUTBOX_POLL_CONFIGURATION, PIPELINE_SERVICE.getServiceId(), false));
    install(new TokenClientModule(this.configuration.getNgManagerServiceHttpClientConfig(),
        this.configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new PipelinePermissionMapperModule());
    bind(HarnessTokenUtils.class).in(Scopes.SINGLETON);
    install(new WebhookEventClientModule(this.configuration.getNgManagerServiceHttpClientConfig(),
        this.configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return configuration.getSegmentConfiguration();
      }
    });
    install(new VariableClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new HsqsServiceClientModule(this.configuration.getQueueServiceClientConfig(), BEARER.getServiceId()));
    install(new SSCAManagerServiceClientModule(this.configuration.getSscaServiceConfig().getHttpClientConfig(),
        this.configuration.getSscaServiceConfig().getServiceSecret(), PIPELINE_SERVICE.getServiceId()));

    install(new STOServiceClientModule(this.configuration.getStoServiceConfig()));

    if (configuration.getQwietServiceConfig() != null) {
      install(new QwietServiceClientModule(configuration.getQwietServiceConfig()));
    }

    if (configuration.getIdpBaseUrl() != null) {
      bind(String.class).annotatedWith(Names.named("idpBaseUrl")).toInstance(configuration.getIdpBaseUrl());
    }
    if (configuration.getIdpServiceSecret() != null) {
      bind(String.class).annotatedWith(Names.named("idpServiceSecret")).toInstance(configuration.getIdpServiceSecret());
    }

    install(new FileStoreClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(
        new SmtpConfigClientModule(configuration.getManagerClientConfig(), configuration.getManagerServiceSecret()));
    install(new CDNGStageSummaryResourceClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new ScopeInfoClientModule(rhsConfig, rhsSecret, PIPELINE_SERVICE.getServiceId()));
    install(new NgServiceResourceClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new EnvironmentResourceClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));
    install(new PluginConfigClientModule(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId(), ClientMode.PRIVILEGED, false));

    // Bind CDEventInterceptor for OIDC context enrichment in CD stages
    // Uses CDNGStageSummaryResourceClient (installed via CDNGStageSummaryResourceClientModule above)
    bind(CDEventInterceptor.class).to(CDEventInterceptorImpl.class);

    // Agent step template expansion
    bind(AgentTemplateExpansionService.class).to(AgentTemplateExpansionServiceImpl.class);
    bind(V1ToV0StepGroupConverter.class).in(Singleton.class);

    // Register PluginInfoProvider for Pipeline module steps
    Multibinder<PluginInfoProvider> pluginInfoProviderMultibinder =
        Multibinder.newSetBinder(binder(), new TypeLiteral<PluginInfoProvider>() {});
    pluginInfoProviderMultibinder.addBinding().to(OPAEvaluationPluginInfoProvider.class);
    pluginInfoProviderMultibinder.addBinding().to(ApprovalPluginInfoProvider.class);

    // Register semantic validation rules for the Dry Run API (see io.harness.pms.plan.execution.dryrun.semantic).
    Multibinder<SemanticRule> semanticRuleMultibinder =
        Multibinder.newSetBinder(binder(), new TypeLiteral<SemanticRule>() {});
    semanticRuleMultibinder.addBinding().to(ReferencedEntitiesExistRule.class);
    semanticRuleMultibinder.addBinding().to(ConnectorTypeRule.class);
    semanticRuleMultibinder.addBinding().to(CloneCodebaseSanityRule.class);
    semanticRuleMultibinder.addBinding().to(CloudCiDelegateConnectorRule.class);

    registerOutboxEventHandlers();
    bind(OutboxEventHandler.class).to(PMSOutboxEventHandler.class);
    bind(OutboxService.class).annotatedWith(Names.named("executionOutboxService")).to(ExecutionOutboxServiceImpl.class);
    bind(ExecutionOutboxDao.class).to(ExecutionOutboxDaoImpl.class);
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(ExecutionMetricsService.class).to(ExecutionMetricsServiceImpl.class);
    bind(PipelineDeleteCleanupMonitorService.class).to(PipelineDeleteCleanupMonitorServiceImpl.class);
    bind(PipelineMetadataService.class).to(PipelineMetadataServiceImpl.class);
    bind(OutboxService.class).annotatedWith(Names.named("kafkaOutboxService")).to(KafkaOutboxServiceImpl.class);
    bind(KafkaOutboxDao.class).to(KafkaOutboxDaoImpl.class);
    bind(KafkaOutboxEventHandler.class).to(KafkaOutboxEventHandlerImpl.class);
    bind(PMSPipelineService.class).to(PMSPipelineServiceImpl.class);
    bind(PipelineYamlConversionEntityService.class).to(PipelineYamlConversionEntityServiceImpl.class);
    bind(ConversionJobService.class).to(ConversionJobServiceImpl.class);
    bind(PipelineAnnotationsService.class).to(PipelineAnnotationsServiceImpl.class);
    bind(PipelineAsyncValidationService.class).to(PipelineAsyncValidationServiceImpl.class);
    bind(PmsExecutionSummaryService.class).to(PmsExecutionSummaryServiceImpl.class);
    bind(PipelineGovernanceService.class).to(PipelineGovernanceServiceImpl.class);
    bind(PipelineValidationService.class).to(PipelineValidationServiceImpl.class);
    bind(PipelineSdkPrioritySupport.class).to(PipelineSdkPrioritySupportImpl.class).in(Singleton.class);

    bind(PreflightService.class).to(PreflightServiceImpl.class);
    bind(PipelineRbacService.class).to(PipelineRbacServiceImpl.class);
    bind(PMSInputSetService.class).to(PMSInputSetServiceImpl.class);
    bind(PMSExecutionService.class).to(PMSExecutionServiceImpl.class);
    bind(QueuedPipelineService.class).to(QueuedPipelineServiceImpl.class);
    bind(ExecutionGraphService.class).to(ExecutionGraphServiceImpl.class);
    bind(ExpressionEvaluatorService.class).to(ExpressionEvaluatorServiceImpl.class);
    bind(PMSYamlSchemaService.class).to(PMSYamlSchemaServiceImpl.class);
    bind(ApprovalNotificationHandler.class).to(ApprovalNotificationHandlerImpl.class);
    bind(PMSOpaService.class).to(PMSOpaServiceImpl.class);
    bind(OpaGitxStatusRepository.class).to(PipelineEntityOpaStatusRepository.class).in(Singleton.class);
    bind(PipelineOpaStatusHandler.class).in(Singleton.class);
    bind(WebhookNotificationService.class).to(WebhookNotificationServiceImpl.class);
    bind(ShellScriptHelperServiceOld.class).to(ShellScriptHelperServiceImplOld.class);
    bind(ShellScriptHelperService.class).to(ShellScriptHelperServiceImpl.class);
    bind(WorkloadIdentityTokenService.class).to(WorkloadIdentityTokenServiceImpl.class);
    bind(ApprovalYamlSchemaService.class).to(ApprovalYamlSchemaServiceImpl.class).in(Singleton.class);
    bind(PipelineStageYamlSchemaService.class).to(PipelineStageYamlSchemaServiceImpl.class).in(Singleton.class);
    bind(FeatureFlagYamlService.class).to(FeatureFlagYamlServiceImpl.class).in(Singleton.class);
    bind(PipelineEnforcementService.class).to(PipelineEnforcementServiceImpl.class).in(Singleton.class);

    bind(PipelineRefreshService.class).to(PipelineRefreshServiceImpl.class);
    bind(NodeTypeLookupService.class).to(NodeTypeLookupServiceImpl.class);

    bind(CIFeatureFlagService.class).to(CIFeatureFlagServiceImpl.class);

    // Flow-governor bindings for the 3 engine-side throttled consumers. When
    // flowGovernorConfig.enabled=false the cache is never read (ThrottledKafkaConsumer#runInternal
    // short-circuits) and Caffeine's refreshAfterWrite is lazy — so no Redis calls happen on the
    // disabled path.
    bind(FlowGovernorStateStore.class).in(Singleton.class);
    bind(FlowGovernorStateCache.class).in(Singleton.class);

    // Branch-scoped build sequence ID service (CI-19987)
    bind(BranchSequenceService.class).to(BranchSequenceServiceImpl.class);

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("syncTaskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("SyncTaskPoll-Thread"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("asyncTaskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("AsyncTaskPoll-Thread"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("progressTaskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("ProgressTaskPoll-Thread"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("progressUpdateServiceExecutor"))
        .toInstance(new ManagedScheduledExecutorService("ProgressUpdateServiceExecutor-Thread"));
    bind(TriggerCustomWebhookExecutionService.class).to(TriggerCustomWebhookExecutionServiceImpl.class);
    bind(TriggerWebhookExecutionService.class).to(TriggerWebhookExecutionServiceImpl.class);
    bind(TriggerWebhookExecutionServiceV2.class).to(TriggerWebhookExecutionServiceImplV2.class);
    bind(TriggerWebhookEventExecutionService.class).to(TriggerWebhookEventExecutionServiceImpl.class);
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("telemetryPublisherExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("pipeline-telemetry-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
    bind(StageMetadataNotificationHelper.class).to(StageMetadataNotificationHelperImpl.class);

    MapBinder<String, FilterPropertiesMapper> filterPropertiesMapper =
        MapBinder.newMapBinder(binder(), String.class, FilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.PIPELINESETUP.toString()).to(PipelineFilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.INPUTSET.toString()).to(InputSetFilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.PIPELINEEXECUTION.toString())
        .to(PipelineExecutionFilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.QUEUED_PIPELINE.toString())
        .to(QueuedPipelineFilterPropertiesMapper.class);

    bind(PMSBarrierService.class).to(PMSBarrierServiceImpl.class);
    bind(ApprovalResourceService.class).to(ApprovalResourceServiceImpl.class);
    bind(PipelineResource.class).to(PipelineResourceImpl.class);
    bind(BranchSequenceResource.class).to(BranchSequenceResourceImpl.class);
    bind(PipelineAnnotationsResource.class).to(PipelineAnnotationsResourceImpl.class);
    bind(PipelinesApi.class).to(PipelinesApiImpl.class);
    bind(ApprovalsApi.class).to(ApprovalsApiImpl.class);
    bind(InputSetsApi.class).to(InputSetsApiImpl.class);
    bind(PipelineConversionApi.class).to(PipelineConversionApiImpl.class);
    bind(PipelineDashboardOverviewResource.class).to(PipelineDashboardOverviewResourceImpl.class);
    bind(PipelineDashboardOverviewResourceV2.class).to(PipelineDashboardOverviewResourceV2Impl.class);
    bind(PMSLandingDashboardResource.class).to(PMSLandingDashboardResourceImpl.class);
    bind(ApprovalResource.class).to(ApprovalResourceImpl.class);
    bind(PMSBarrierResource.class).to(PMSBarrierResourceImpl.class);
    bind(HealthResource.class).to(HealthResourceImpl.class);
    bind(CustomApprovalHelperService.class).to(CustomApprovalHelperServiceImpl.class);
    bind(JiraApprovalHelperService.class).to(JiraApprovalHelperServiceImpl.class);
    bind(JiraStepHelperService.class).to(JiraStepHelperServiceImpl.class);
    bind(PMSResourceConstraintService.class).to(PMSResourceConstraintServiceImpl.class);
    bind(PMSLandingDashboardService.class).to(PMSLandingDashboardServiceImpl.class);
    bind(InputSetResourcePMS.class).to(InputSetResourcePMSImpl.class);
    bind(InputsApi.class).to(InputsApiImpl.class);
    bind(PMSInputsService.class).to(PMSInputsServiceImpl.class);
    bind(PlanExecutionResource.class).to(PlanExecutionResourceImpl.class);
    bind(NotifyResource.class).to(NotifyResourceImpl.class);
    bind(WaitStepResource.class).to(WaitStepResourceImpl.class);
    bind(WaitStepService.class).to(WaitStepServiceImpl.class);
    bind(PmsYamlSchemaResource.class).to(PmsYamlSchemaResourceImpl.class);
    bind(PMSResourceConstraintResource.class).to(PMSResourceConstraintResourceImpl.class);
    bind(LogStreamingServiceRestClient.class)
        .toProvider(NGLogStreamingClientFactory.builder()
                        .logStreamingServiceBaseUrl(configuration.getLogStreamingServiceConfig().getBaseUrl())
                        .build());
    bind(LogBaseUrlProvider.class).to(PipelineServiceLogBaseUrlProvider.class).in(Scopes.SINGLETON);
    bind(LogServiceUrlProvider.class).to(PipelineServiceLogServiceUrlProvider.class).in(Scopes.SINGLETON);

    bind(PipelineAdminResourceService.class).to(PipelineAdminResourceServiceImpl.class);
    bind(PipelineDashboardService.class).to(PipelineDashboardServiceImpl.class);
    bind(ServiceNowApprovalHelperService.class).to(ServiceNowApprovalHelperServiceImpl.class);
    bind(ServiceNowStepHelperService.class).to(ServiceNowStepHelperServiceImpl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(ContainerStepV2PluginProvider.class).to(ContainerStepV2PluginProviderImpl.class);
    bind(SecretRuntimeUsageService.class).to(SecretRuntimeUsageServiceImpl.class);
    bind(PipelineRetentionResource.class).to(PipelineRetentionResourceImpl.class);
    bind(PipelineAdminResource.class).to(PipelineAdminResourceImpl.class);
    bind(OrchestrationGovernorResource.class).to(OrchestrationGovernorResourceImpl.class);
    bind(PMSPipelineInlineHcMigrationService.class).to(PMSPipelineInlineHcMigrationServiceImpl.class);
    bind(PipelineInlineHcMigrationResource.class).to(PipelineInlineHcMigrationResourceImpl.class);
    bind(InputFileResource.class).to(InputFileResourceImpl.class);
    bind(InputFileService.class).to(InputFileServiceImpl.class);
    bind(AnnotationFileService.class).to(AnnotationFileServiceImpl.class);
    bind(InputSetInlineHcMigrationResource.class).to(InputSetInlineHcMigrationResourceImpl.class);
    bind(PMSInputSetInlineHcMigrationService.class).to(PMSInputSetInlineHcMigrationServiceImpl.class);
    bind(NotificationBodyResolutionInterface.class).to(NotificationExpressionsResolutionServiceImpl.class);
    bind(InlineHcRollbackResource.class).to(InlineHcRollbackResourceImpl.class);
    bind(InlineHcRollbackService.class).to(InlineHcRollbackServiceImpl.class);
    bind(io.harness.ci.execution.execution.intfc.GitBuildStatusUtility.class).to(GitBuildStatusUtilityImpl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(GitlabService.class).to(GitlabServiceImpl.class);
    bind(BitbucketService.class).to(BitbucketServiceImpl.class);
    bind(AzureRepoService.class).to(AzureRepoServiceImpl.class);
    bind(GitStatusUpdateNotifier.class).to(GitStatusUpdateNotifierImpl.class);
    try {
      bind(TimeScaleDBService.class)
          .toConstructor(TimeScaleDBServiceImpl.class.getConstructor(TimeScaleDBConfig.class));
    } catch (NoSuchMethodException e) {
      log.error("TimeScaleDbServiceImpl Initialization Failed in due to missing constructor", e);
    }

    if (configuration.getEnableDashboardTimescale() != null && configuration.getEnableDashboardTimescale()) {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(configuration.getTimeScaleDBConfig() != null ? configuration.getTimeScaleDBConfig()
                                                                   : TimeScaleDBConfig.builder().build());

      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("SecondaryTimeScaleDBConfig"))
          .toInstance(configuration.getSecondaryTimeScaleDBConfig() != null
                  ? configuration.getSecondaryTimeScaleDBConfig()
                  : TimeScaleDBConfig.builder().build());
    } else {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(TimeScaleDBConfig.builder().build());

      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("SecondaryTimeScaleDBConfig"))
          .toInstance(TimeScaleDBConfig.builder().build());
    }

    try {
      bind(PostgresDBService.class).toConstructor(PostgresDBServiceImpl.class.getConstructor(PostgresDBConfig.class));
    } catch (NoSuchMethodException e) {
      log.error("TimeScaleDbServiceImpl Initialization Failed in due to missing constructor", e);
    }

    if (configuration.getEnablePostgres() != null && configuration.getEnablePostgres()) {
      bind(PostgresDBConfig.class)
          .annotatedWith(Names.named("PostgresDBConfig"))
          .toInstance(configuration.getPostgresDBConfig() != null ? configuration.getPostgresDBConfig()
                                                                  : PostgresDBConfig.builder().build());
    } else {
      bind(PostgresDBConfig.class)
          .annotatedWith(Names.named("PostgresDBConfig"))
          .toInstance(PostgresDBConfig.builder().build());
    }

    install(EnforcementClientModule.getInstance(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId(),
        configuration.getEnforcementClientConfiguration()));

    // ng-license dependencies
    install(NgLicenseHttpClientModule.getInstance(configuration.getNgManagerServiceHttpClientConfig(),
        configuration.getNgManagerServiceSecret(), PIPELINE_SERVICE.getServiceId()));

    // Install FMEResourceClientModule if fmeServiceClientConfig is provided
    if (configuration.getFmeServiceClientConfig() != null) {
      ServiceHttpClientConfig fmeHttpConfig =
          ServiceHttpClientConfig.builder()
              .baseUrl(configuration.getFmeServiceClientConfig().getBaseUrl())
              .connectTimeOutSeconds(configuration.getFmeServiceClientConfig().getConnectTimeOutSeconds())
              .readTimeOutSeconds(configuration.getFmeServiceClientConfig().getReadTimeOutSeconds())
              .build();

      install(new FMEClientModule(fmeHttpConfig, configuration.getFmeServiceClientConfig().getSecret(),
          PIPELINE_SERVICE.getServiceId(), ClientMode.PRIVILEGED, configuration.getFmeServiceClientConfig()));
    }

    // Install AI SRE (transposit) client if aiSreServiceClientConfig is provided. The clientId is the
    // pipeline-service principal name; transposit's incident/alert endpoints allowlist "PipelineService".
    if (configuration.getAiSreServiceClientConfig() != null) {
      ServiceHttpClientConfig aiSreHttpConfig =
          ServiceHttpClientConfig.builder()
              .baseUrl(configuration.getAiSreServiceClientConfig().getBaseUrl())
              .connectTimeOutSeconds(configuration.getAiSreServiceClientConfig().getConnectTimeOutSeconds())
              .readTimeOutSeconds(configuration.getAiSreServiceClientConfig().getReadTimeOutSeconds())
              .build();

      install(new AiSreClientModule(aiSreHttpConfig, configuration.getAiSreServiceClientConfig().getSecret(),
          PIPELINE_SERVICE.getServiceId(), ClientMode.PRIVILEGED, configuration.getAiSreServiceClientConfig()));
    }

    install(
        new io.harness.steps.ro.ReleaseManagementClientModule(configuration.getReleaseManagementServiceClientConfig(),
            configuration.getPipelineServiceSecret(), PIPELINE_SERVICE.getServiceId(),
            configuration.getReleaseManagementEventType(), configuration.getReleaseManagementMaxArtifactsPerType()));

    install(new io.harness.steps.changeadvisor.ChangeAdvisorServiceClientModule(
        configuration.getChangeAdvisorServiceClientConfig(), configuration.getPipelineServiceSecret(),
        PIPELINE_SERVICE.getServiceId()));

    registerEventsFrameworkMessageListeners();
  }

  private void registerOutboxEventHandlers() {
    MapBinder<String, OutboxEventHandler> outboxEventHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, OutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(ResourceTypeConstants.TRIGGER).to(TriggerOutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(ResourceTypeConstants.PIPELINE).to(PipelineOutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(ResourceTypeConstants.INPUT_SET).to(PipelineOutboxEventHandler.class);
  }

  private void registerEventsFrameworkMessageListeners() {
    if (WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.GRAPH)) {
      bind(MessageListener.class)
          .annotatedWith(Names.named(PIPELINE_ENTITY + ENTITY_CRUD))
          .to(PipelineEntityCRUDStreamListener.class);

      bind(MessageListener.class)
          .annotatedWith(Names.named(PROJECT_ENTITY + ENTITY_CRUD))
          .to(ProjectEntityCrudStreamListener.class);

      bind(MessageListener.class)
          .annotatedWith(Names.named(ACCOUNT_ENTITY + ENTITY_CRUD))
          .to(AccountEntityCrudStreamListener.class);
      bind(MessageListener.class)
          .annotatedWith(Names.named(SETTINGS + ENTITY_CRUD))
          .to(PipelineSettingCRUDStreamListener.class);
    }
    if (WorkloadType.isAnyOf(WorkloadType.ALL, WorkloadType.ORCHESTRATION_ENGINE)) {
      bind(PmsMessageListener.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.POLLING_EVENTS_STREAM))
          .to(PollingEventStreamListener.class);

      bind(PmsMessageListener.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM))
          .to(TriggerExecutionEventStreamListener.class);
    }
  }

  @Provides
  @Singleton
  public StuckNodeExecutionsMarkingConfig stuckNodeExecutionsMonitorConfig() {
    return configuration.getStuckNodeExecutionsMarkingConfig();
  }

  @Provides
  @Singleton
  public CdcKafkaConfig cdcKafkaConfig() {
    return configuration.getCdcKafkaConfig() != null ? configuration.getCdcKafkaConfig()
                                                     : CdcKafkaConfig.defaultConfig();
  }

  @Provides
  @Singleton
  @Named("planExecutionsSummaryRedisShortCircuit")
  public boolean planExecutionsSummaryRedisShortCircuit() {
    CdcKafkaConfig cfg = configuration.getCdcKafkaConfig();
    if (cfg == null) {
      return false;
    }
    return cfg.getConsumer(CdcKafkaConfig.PLAN_EXECUTIONS_SUMMARY_CONSUMER)
        .map(CdcKafkaConsumerConfig::isRedisShortCircuit)
        .orElse(false);
  }

  @Provides
  @Singleton
  public OrchestrationGraphCacheCleanupConfig orchestrationGraphCacheCleanupConfig() {
    if (configuration.getOrchestrationGraphCacheCleanupConfig() == null) {
      return OrchestrationGraphCacheCleanupConfig.builder().enabled(false).cleanUpEnabled(false).build();
    }
    return configuration.getOrchestrationGraphCacheCleanupConfig();
  }

  @Provides
  @Named("harnessCodeClientConfig")
  public ServiceHttpClientConfig harnessCodeServiceClientConfig() {
    return configuration.getHarnessCodeServiceClientConfig();
  }

  @Provides
  @Named("preflightConnectorTimeoutSeconds")
  public int preflightConnectorTimeoutSeconds() {
    return configuration.getPreflightConnectorTimeoutSeconds();
  }

  @Provides
  @Singleton
  public Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
    return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
        .addAll(PipelineServiceModuleRegistrars.kryoRegistrars)
        .build();
  }

  @Provides
  @Singleton
  public Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
    return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
        .addAll(PipelineServiceModuleRegistrars.morphiaRegistrars)
        .build();
  }

  @Provides
  @Singleton
  @Named("logStreamingDelayExecutor")
  public ScheduledExecutorService logStreamingDelayExecutor() {
    ThreadPoolConfig threadPoolConfig = configuration != null && configuration.getLogStreamingServiceConfig() != null
            && configuration.getLogStreamingServiceConfig().getThreadPoolConfig() != null
        ? configuration.getLogStreamingServiceConfig().getThreadPoolConfig()
        : ThreadPoolConfig.builder().corePoolSize(10).build();
    return new ScheduledThreadPoolExecutor(threadPoolConfig.getCorePoolSize(),
        new ThreadFactoryBuilder().setNameFormat("log-client-pool-%d").setPriority(Thread.NORM_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("kafkaOutboxEventPollConfig")
  public OutboxPollConfiguration getKafkaOutboxPollConfiguration() {
    OutboxPollConfiguration outboxPollConfiguration =
        configuration.getPipelineOutboxPollConfiguration().getKafkaOutbox();
    outboxPollConfiguration.setLockId("KafkaOutboxEvent");
    return outboxPollConfiguration;
  }

  @Provides
  @Singleton
  public Set<Class<? extends TypeConverter>> morphiaConverters() {
    return ImmutableSet.<Class<? extends TypeConverter>>builder()
        .addAll(PipelineServiceModuleRegistrars.morphiaConverters)
        .build();
  }

  @Provides
  @Singleton
  List<Class<? extends Converter<?, ?>>> springConverters() {
    return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
        .addAll(PipelineServiceModuleRegistrars.springConverters)
        .build();
  }

  @Provides
  @Singleton
  List<YamlSchemaRootClass> yamlSchemaRootClasses() {
    return ImmutableList.<YamlSchemaRootClass>builder().build();
  }

  @Provides
  @Singleton
  @Named("cacheRedissonClient")
  RedissonClient cacheRedissonClient() {
    return RedissonClientFactory.getClient(configuration.getRedisLockConfig());
  }

  @Provides
  @Singleton
  FlowGovernorConfig flowGovernorConfig() {
    FlowGovernorConfig config = configuration.getFlowGovernorConfig();
    return config == null ? FlowGovernorConfig.disabled() : config;
  }

  @Provides
  @Singleton
  DistributedBackend distributedBackend() {
    return DistributedBackend.REDIS;
  }

  @Provides
  @Singleton
  @Named("PSQLExecuteListener")
  ExecuteListener executeListener() {
    return HExecuteListener.getInstance();
  }

  @Provides
  @Singleton
  public MongoConfig mongoConfig(PipelineServiceConfiguration configuration) {
    return configuration.getMongoConfig();
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    return ImmutableMap.<Class, String>builder()
        .put(DelegateSyncTaskResponse.class, "pms_delegateSyncTaskResponses")
        .put(DelegateAsyncTaskResponse.class, "pms_delegateAsyncTaskResponses")
        .put(DelegateTaskProgressResponse.class, "pms_delegateTaskProgressResponses")
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
  Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
      DelegateServiceGrpcClient delegateServiceGrpcClient) {
    return (Supplier<DelegateCallbackToken>) Suppliers.memoize(
        () -> getDelegateCallbackToken(delegateServiceGrpcClient));
  }

  private DelegateCallbackToken getDelegateCallbackToken(DelegateServiceGrpcClient delegateServiceClient) {
    log.info("Generating Delegate callback token");
    MongoDatabase.Builder mongoDatabaseBuilder =
        MongoDatabase.newBuilder().setCollectionNamePrefix("pms").setConnection(
            this.configuration.getMongoConfig().getUri());
    String waitNotifyCollectionPrefix = WaitNotifyCollectionNameResolver.getCollectionPrefix();
    if (waitNotifyCollectionPrefix != null) {
      mongoDatabaseBuilder.setWaitNotifyCollectionPrefix(waitNotifyCollectionPrefix);
    }
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(mongoDatabaseBuilder.build())
            .setNewCallbackFlow(this.configuration.isEnableWaitNotifyEngineOptimisation())
            .build());
    log.info("delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return configuration.getDistributedLockImplementation() == null ? REDIS
                                                                    : configuration.getDistributedLockImplementation();
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisConfig() {
    return configuration.getRedisLockConfig();
  }

  @Provides
  @Singleton
  @Named("templateRegistrationExecutorService")
  public ExecutorService templateRegistrationExecutionServiceThreadPool() {
    return new ScalingThreadPoolExecutor(
        ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(1).idleTime(10).timeUnit(TimeUnit.SECONDS).build(),
        "TemplateRegistrationService-%d");
  }

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();

    PipelineServiceApplication.configureObjectMapper(objectMapper);
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
  public ObjectMapper getYamlSchemaObjectMapperWithoutNamed() {
    return Jackson.newObjectMapper();
  }

  @Provides
  @Singleton
  public LogStreamingServiceConfiguration getLogStreamingServiceConfiguration() {
    return configuration.getLogStreamingServiceConfig();
  }

  @Provides
  @Singleton
  public PipelineServiceIteratorsConfig getIteratorsConfig() {
    return configuration.getIteratorsConfig() == null ? PipelineServiceIteratorsConfig.builder().build()
                                                      : configuration.getIteratorsConfig();
  }

  @Provides
  @Singleton
  @Named("shouldUseInstanceCache")
  public boolean shouldUseInstanceCache() {
    return configuration.isShouldUseInstanceCache();
  }

  @Provides
  @Singleton
  @Named("skipSdkMongoRegistration")
  public boolean skipSdkMongoRegistration() {
    return configuration.isSkipSdkMongoRegistration();
  }

  @Provides
  @Singleton
  @Named("PipelineExecutorService")
  public ExecutorService pipelineExecutorService() {
    return ThreadPool.getInstrumentedExecutorService(
        configuration.getPipelineExecutionPoolConfig(), "PipelineExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("GitAwareEntityHelperExecutorService")
  public ExecutorService gitAwareEntityHelperExecutorService() {
    return ThreadPool.getInstrumentedExecutorService(configuration.getGitAwareEntityHelperPoolConfig(),
        "GitAwareEntityHelperExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("DashboardExecutorService")
  public ExecutorService dashboardExecutorService() {
    return new ScalingThreadPoolExecutor(
        configuration.getDashboardExecutorServiceConfig(), "DashboardExecutorService-%d");
  }

  @Provides
  @Singleton
  @Named("pipelineSdkPriority")
  public Map<String, Integer> pipelineSdkPriority() {
    Map<String, Integer> map = new HashMap<>();
    Map<String, ModuleSpecificInfo> moduleSpecificInfoMap = configuration.getModuleSpecificInfoMap();
    for (Map.Entry<String, ModuleSpecificInfo> entry : moduleSpecificInfoMap.entrySet()) {
      String moduleName = entry.getKey();
      Integer pipelineSdkPriority = entry.getValue().getPipelineSdkPriority();
      if (pipelineSdkPriority != null) {
        map.put(moduleName, pipelineSdkPriority);
      }
    }
    return map;
  }

  @Provides
  @Singleton
  @Named("PlanCreatorMergeExecutorService")
  public Executor planCreatorMergeExecutorService() {
    return ThreadPool.getInstrumentedExecutorService(configuration.getPlanCreatorMergeServicePoolConfig(),
        "PipelineMergeCreatorExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("PlanCreationExecutorService")
  public Executor planCreationExecutorService() {
    return ThreadPool.getInstrumentedExecutorService(configuration.getPlanCreationServicePoolConfig(),
        "PipelinePlanCreationExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("CustomWebhookTriggerExecutorService")
  public Executor customWebhookTriggerExecutorService() {
    return ThreadPool.getInstrumentedExecutorService(configuration.getCustomWebhookTriggerExecutionPoolConfig(),
        "CustomWebhookTriggerExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("VariableCreatorMergeExecutorService")
  public Executor variableCreatorMergeExecutorService() {
    return ThreadPool.getInstrumentedExecutorService(configuration.getVariableCreatorMergeServicePoolConfig(),
        "VariableMergeCreatorExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("FilterCreatorMergeExecutorService")
  public Executor filterCreatorMergeExecutorService() {
    return ThreadPool.getInstrumentedExecutorService(configuration.getFilterCreatorMergeServicePoolConfig(),
        "FilterMergeCreatorExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("webhookEventHsqsDequeueConfig")
  public HsqsDequeueConfig getWebhookEventHsqsDequeueConfig() {
    return configuration.getWebhookEventHsqsDequeueConfig();
  }

  @Provides
  @Singleton
  @Named("planCreationHsqsDequeueConfig")
  public HsqsDequeueConfig getPlanCreationHsqsDequeueConfig() {
    return configuration.getPlanCreationHsqsDequeueConfig();
  }

  @Provides
  @Singleton
  @Named("maxMultiArtifactTriggerSources")
  public Integer getMaxMultiArtifactTriggerSources() {
    return configuration.getMaxMultiArtifactTriggerSources();
  }

  @Provides
  @Singleton
  @Named("YamlSchemaExecutorService")
  public ExecutorService yamlSchemaExecutorService() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("YamlSchemaService-%d").build();
    return new ScalingThreadPoolExecutor(
        ThreadPoolConfig.builder()
            .corePoolSize(configuration.getYamlSchemaExecutorServiceConfig().getCorePoolSize())
            .maxPoolSize(configuration.getYamlSchemaExecutorServiceConfig().getMaxPoolSize())
            .idleTime(configuration.getYamlSchemaExecutorServiceConfig().getIdleTime())
            .timeUnit(configuration.getYamlSchemaExecutorServiceConfig().getTimeUnit())
            .build(),
        threadFactory);
  }

  @Provides
  @Singleton
  @Named("JsonExpansionExecutorService")
  public Executor jsonExpansionExecutorService() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("JsonExpansionExecutorService-%d").build();
    return new ScalingThreadPoolExecutor(ThreadPoolConfig.builder()
                                             .corePoolSize(configuration.getJsonExpansionPoolConfig().getCorePoolSize())
                                             .maxPoolSize(configuration.getJsonExpansionPoolConfig().getMaxPoolSize())
                                             .idleTime(configuration.getJsonExpansionPoolConfig().getIdleTime())
                                             .timeUnit(configuration.getJsonExpansionPoolConfig().getTimeUnit())
                                             .build(),
        threadFactory);
  }

  @Provides
  @Singleton
  @Named("InputsMetadataExecutorService")
  public Executor inputsMetadataExecutorService() {
    return new ScalingThreadPoolExecutor(
        configuration.getInputsMetadataPoolConfig(), "InputsMetadataExecutorService-%d");
  }

  /**
   * To be used for async validations of Pipelines. Because pipeline fetch calls can be frequent, max pool size needs to
   * be high
   */
  @Provides
  @Singleton
  @Named("PipelineAsyncValidationExecutorService")
  public Executor pipelineAsyncValidationExecutorService() {
    return new ManagedExecutorService(new ScalingThreadPoolExecutor(
        configuration.getPipelineAsyncValidationPoolConfig(), "PipelineAsyncValidationExecutorService-%d"));
  }

  @Provides
  @Singleton
  @Named("PipelineYamlConversionExecutorService")
  public Executor pipelineYamlConversionExecutorService() {
    return new ManagedExecutorService(new ScalingThreadPoolExecutor(
        configuration.getPipelineYamlConversionPoolConfig(), "PipelineYamlConversionExecutorService-%d"));
  }

  @Provides
  @Singleton
  @Named("TriggerAuthenticationExecutorService")
  public ExecutorService triggerAuthenticationExecutorService() {
    return new ScalingThreadPoolExecutor(
        configuration.getTriggerAuthenticationPoolConfig(), "TriggerAuthenticationExecutorService-%d");
  }

  @Provides
  @Singleton
  @Named("TelemetrySenderExecutor")
  public Executor telemetrySenderExecutor() {
    ThreadPoolConfig config =
        ThreadPoolConfig.builder().corePoolSize(1).maxPoolSize(2).idleTime(25).timeUnit(TimeUnit.SECONDS).build();
    return new ScalingThreadPoolExecutor(config, "TelemetrySenderExecutor-%d");
  }

  @Provides
  @Singleton
  @Named("pmsEventsCache")
  public Cache<String, Integer> sdkEventsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("pmsEventsCache", String.class, Integer.class,
        AccessedExpiryPolicy.factoryOf(Duration.THIRTY_MINUTES), versionInfoManager.getVersionInfo().getBuildNo(),
        true);
  }

  @Provides
  @Singleton
  @Named("roleMigrationCache")
  public Cache<String, Boolean> roleMigrationCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("roleMigrationCache", String.class, Boolean.class,
        AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.DAYS, 30)),
        versionInfoManager.getVersionInfo().getBuildNo(), true);
  }

  @Provides
  @Singleton
  @Named("pipelineDeleteCleanupMetricsCache")
  public Cache<String, Integer> pipelineDeleteCleanupMetricsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("pipelineDeleteCleanupMetricsCache", String.class, Integer.class,
        AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 10)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named("triggersMigrationCache")
  public Cache<String, Boolean> triggersMigrationCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("triggersMigrationCache", String.class, Boolean.class,
        AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.DAYS, 180)),
        versionInfoManager.getVersionInfo().getBuildNo(), true);
  }

  @Provides
  @Singleton
  @Named("schemaDetailsCache")
  public Cache<SchemaCacheKey, YamlSchemaDetailsWrapperValue> schemaDetailsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("schemaDetailsCache", SchemaCacheKey.class, YamlSchemaDetailsWrapperValue.class,
        CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 7)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named("partialSchemaCache")
  public Cache<SchemaCacheKey, PartialSchemaDTOWrapperValue> partialSchemaCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("partialSchemaCache", SchemaCacheKey.class, PartialSchemaDTOWrapperValue.class,
        CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 1)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }

  @Provides
  @Singleton
  @Named("planCreatorMergeServiceDependencyBatch")
  public Integer getPlanCreatorMergeServiceDependencyBatch() {
    return configuration.getPlanCreatorMergeServiceDependencyBatch();
  }

  @Provides
  @Singleton
  @Named("jsonExpansionRequestBatchSize")
  public Integer getjsonExpansionRequestBatchSize() {
    return configuration.getJsonExpansionBatchSize();
  }

  @Provides
  @Singleton
  @Named("InputsMetadataRequestBatchSize")
  public Integer getInputsMetadataRequestBatchSize() {
    return configuration.getInputsMetadataBatchSize();
  }

  @Provides
  @Singleton
  @Named("InitiateNodeRequestBatchSize")
  public Integer getInitiateNodeRequestBatchSize() {
    return configuration.getInitiateNodeRequestBatchSize();
  }

  @Provides
  @Singleton
  @Named("pipelineSetupUsageCreationExecutorService")
  public ExecutorService pipelineSetupUsageCreationExecutorService() {
    return new ManagedExecutorService(
        new ScalingThreadPoolExecutor(configuration.getPipelineSetupUsageCreationPoolConfig(),
            "PipelineSetupUsageCreationExecutorService", new ThreadPoolExecutor.AbortPolicy()));
  }

  @Provides
  @Singleton
  @Named("publishAdviserEventForCustomAdvisers")
  public Boolean getPublishAdviserEventForCustomAdvisers() {
    return configuration.getPublishAdviserEventForCustomAdvisers();
  }

  @Provides
  @Singleton
  @Named("publishNodeExecutionTimeTakenDetails")
  public Boolean getPublishNodeExecutionTimeTakenDetails() {
    return configuration.getPublishNodeExecutionTimeTakenDetails();
  }

  @Provides
  @Singleton
  @Named("pipelineExecutionDetailsDeleteMaxBatchSize")
  public Integer getPipelineExecutionDetailsDeleteMaxBatchSize() {
    return configuration.getPipelineExecutionDetailsDeleteMaxBatchSize();
  }

  @Provides
  @Singleton
  @Named("pipelineAbortPermissionMigrationCache")
  public Cache<String, Boolean> pipelineAbortPermissionMigrationCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("pipelineAbortPermissionMigrationCache", String.class, Boolean.class,
        AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.DAYS, 30)), true);
  }

  @Provides
  @Singleton
  @Named("pmsDeleteEntitiesMigrationCache")
  public Cache<String, String> pmsDeleteEntitiesMigrationCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("pmsDeleteEntitiesMigrationCache", String.class, String.class,
        AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.DAYS, 30)),
        versionInfoManager.getVersionInfo().getBuildNo(), true);
  }

  /**
   * To be used for Bulk Reconciliation of Pipelines.
   */
  @Provides
  @Singleton
  @Named("BulkReconciliationExecutorService")
  public Executor bulkReconciliationExecutorService() {
    return ThreadPool.getInstrumentedExecutorService(configuration.getPipelineAsyncValidationPoolConfig(),
        "BulkReconciliationExecutorService", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("OpaGitxStatusExecutor")
  public Executor opaGitxStatusExecutor() {
    return ThreadPool.getInstrumentedExecutorService(
        configuration.getOpaGitxStatusPoolConfig(), "OpaGitxStatusExecutor", threadPoolMetricRegistry);
  }

  @Provides
  @Singleton
  @Named("FileInputObjectStoreClient")
  @Inject
  public ObjectStoreClient objectStoreClient(@Nullable MetricService metricService) {
    if (Boolean.parseBoolean(configuration.getObjectStoreConfig().getFileInputBucketConfig().getIsEnabled())) {
      return ObjectStoreClientFactory.getClient(configuration.getObjectStoreConfig().getStoreConfig(),
          configuration.getObjectStoreConfig().getFileInputBucketConfig(), metricService);
    }
    return null;
  }

  @Provides
  @Singleton
  @Named("AnnotationsObjectStoreClient")
  @Inject
  public ObjectStoreClient annotationsObjectStoreClient(@Nullable MetricService metricService) {
    if (configuration.getObjectStoreConfig().getAnnotationsBucketConfig() != null
        && Boolean.parseBoolean(configuration.getObjectStoreConfig().getAnnotationsBucketConfig().getIsEnabled())) {
      return ObjectStoreClientFactory.getClient(configuration.getObjectStoreConfig().getStoreConfig(),
          configuration.getObjectStoreConfig().getAnnotationsBucketConfig(), metricService);
    }
    return null;
  }

  @Provides
  @Singleton
  ExecutionStatusReconciliationConfig executionStatusReconciliationConfig() {
    if (configuration.getExecutionStatusReconciliationConfig() == null) {
      return ExecutionStatusReconciliationConfig.builder().enabled(false).build();
    }
    return configuration.getExecutionStatusReconciliationConfig();
  }

  @Provides
  @Singleton
  ResourceRestraintReconciliationConfig resourceRestraintReconciliationConfig() {
    if (configuration.getResourceRestraintReconciliationConfig() == null) {
      return ResourceRestraintReconciliationConfig.builder().enabled(false).build();
    }
    return configuration.getResourceRestraintReconciliationConfig();
  }

  @Provides
  @Singleton
  @Named("executionOutboxEventPollConfig")
  public OutboxPollConfiguration getOutboxPollConfiguration() {
    OutboxPollConfiguration outboxPollConfiguration =
        configuration.getPipelineOutboxPollConfiguration().getExecutionOutbox();
    outboxPollConfiguration.setLockId("ExecutionOutboxEvent");
    return outboxPollConfiguration;
  }

  @Provides
  @Singleton
  @Named("queueServiceClientConfig")
  public QueueServiceClientConfig getQueueServiceClientConfig() {
    return configuration.getQueueServiceClientConfig();
  }

  @Provides
  @Singleton
  @Named("harnessRegistryClientConfig")
  public ServiceHttpClientConfig getHarnessRegistryClientConfig() {
    return configuration.getHarnessRegistryServiceClientConfig();
  }

  @Provides
  @Singleton
  @Named("harnessRegistryServiceSecret")
  public String getHarnessRegistryServiceSecret() {
    return configuration.getHarnessRegistryServiceSecret();
  }

  @Provides
  @Singleton
  @Named("ngBaseUrl")
  String getNgBaseUrl() {
    String apiUrl = configuration.getNgBaseUrl();
    if (apiUrl.endsWith("/")) {
      return apiUrl.substring(0, apiUrl.length() - 1);
    }
    return apiUrl;
  }

  @Provides
  @Singleton
  @Named("opaEvaluationPluginImage")
  String getOpaEvaluationPluginImage() {
    return configuration.getOpaEvaluationPluginImage();
  }

  @Provides
  @Singleton
  @Named("opaServiceBaseUrl")
  String getOpaServiceBaseUrl() {
    if (configuration.getOpaServerConfig() == null) {
      return null;
    }
    String baseUrl = configuration.getOpaServerConfig().getBaseUrl();
    if (baseUrl == null || baseUrl.isEmpty()) {
      return null;
    }
    // Remove trailing slash if present
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl;
  }

  @Provides
  @Singleton
  @Named("opaServiceSecret")
  String getOpaServiceSecret() {
    if (configuration.getOpaServerConfig() == null) {
      return null;
    }
    return configuration.getOpaServerConfig().getSecret();
  }

  @Provides
  @Singleton
  @Named("WorkloadIdentityTokenExecutor")
  ExecutorService workloadIdentityTokenExecutor() {
    ThreadPoolConfig config = configuration.getWorkloadIdentityTokenPoolConfig() != null
        ? configuration.getWorkloadIdentityTokenPoolConfig()
        : ThreadPoolConfig.builder().corePoolSize(5).maxPoolSize(20).idleTime(30).build();
    return ThreadPool.getInstrumentedExecutorService(config, "workload-identity-token", threadPoolMetricRegistry);
  }
}
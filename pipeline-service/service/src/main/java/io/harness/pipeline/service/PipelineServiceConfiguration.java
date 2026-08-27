/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;

import io.harness.AccessControlClientConfiguration;
import io.harness.OrchestrationStepConfig;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cache.CacheConfig;
import io.harness.cf.CfClientConfig;
import io.harness.cleanup.config.OrchestrationGraphCacheCleanupConfig;
import io.harness.config.DelegatePollingConfig;
import io.harness.config.ModuleSpecificInfo;
import io.harness.config.OrchestrationRedisEventsConfig;
import io.harness.config.OrchestrationRestrictionConfiguration;
import io.harness.config.PipelineRedisEventsConfig;
import io.harness.config.PipelineServiceConsumersConfig;
import io.harness.config.PipelineServiceIteratorsConfig;
import io.harness.dataretention.config.DataRetentionConfig;
import io.harness.dropwizard.bundles.swagger.SwaggerBundleConfiguration;
import io.harness.elasticsearch.ElasticSearchDBConfig;
import io.harness.enforcement.client.servicedependencies.EnforcementClientConfiguration;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorConfig;
import io.harness.engine.executions.node.config.StuckNodeExecutionsMarkingConfig;
import io.harness.event.OrchestrationLogConfiguration;
import io.harness.event.streams.GraphGenerationStreamsConfig;
import io.harness.eventPoll.PipelineOutboxPollConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.ff.FeatureFlagConfig;
import io.harness.gitsync.GitSdkConfiguration;
import io.harness.goconvert.GoConvertConnectionConfig;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.harnessid.client.HarnessIdServiceConfig;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.kafka.KafkaModuleConfig;
import io.harness.licensing.enforcement.client.FlexEnforcementClientConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.mongo.MongoConfig;
import io.harness.ng.core.metrics.ProjectEntityMigrationMetricsConfig;
import io.harness.ngtriggers.TriggerConfiguration;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.objectstore.beans.ObjectStoreConfig;
import io.harness.opaclient.OpaServiceConfiguration;
import io.harness.pms.event.overviewLandingPage.DebeziumConsumersConfig;
import io.harness.pms.event.overviewLandingPage.kafka.CdcKafkaConfig;
import io.harness.pms.resourcerestraint.reconciliation.config.ResourceRestraintReconciliationConfig;
import io.harness.pms.sdk.core.PipelineSdkRedisEventsConfig;
import io.harness.pms.statusreconciliation.config.ExecutionStatusReconciliationConfig;
import io.harness.postgres.PostgresDBConfig;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ResourceGroupClientConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.repositories.planExecutionJson.ExpandedJsonLockConfig;
import io.harness.secret.ConfigSecret;
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.sto.beans.entities.QwietServiceConfig;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.threading.ThreadPoolConfig;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.yaml.schema.client.config.YamlSchemaClientConfig;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.inject.Singleton;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.server.ServerFactory;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.logging.common.FileAppenderFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.RequestLogFactory;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.integration.api.OpenAPIConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

// Todo: Streamline this

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
@Data
@EqualsAndHashCode(callSuper = false)
@Slf4j
@Singleton
public class PipelineServiceConfiguration extends Configuration {
  public static final String RESOURCE_PACKAGE = "io.harness.pms";
  public static final String NG_TRIGGER_RESOURCE_PACKAGE = "io.harness.ngtriggers";
  public static final String FILTER_PACKAGE = "io.harness.filter";
  public static final String ENFORCEMENT_PACKAGE = "io.harness.enforcement";
  public static final String NOTIFY_PACKAGE = "io.harness.notify";
  public static final Collection<Class<?>> HARNESS_RESOURCE_CLASSES = getResourceClasses();

  @JsonProperty("swagger") private SwaggerBundleConfiguration swaggerBundleConfiguration;
  @JsonProperty("mongo") private MongoConfig mongoConfig;
  @JsonProperty("commonPoolConfig") private ThreadPoolConfig commonPoolConfig;
  @JsonProperty("orchestrationVisualizationThreadPoolConfig")
  private ThreadPoolConfig orchestrationVisualizationThreadPoolConfig;
  @JsonProperty("pmsSdkGrpcServerConfig") private GrpcServerConfig pmsSdkGrpcServerConfig;
  @JsonProperty("pmsGrpcClientConfig") private GrpcClientConfig pmsGrpcClientConfig;
  @JsonProperty("pipelineExecutionPoolConfig") private ThreadPoolConfig pipelineExecutionPoolConfig;
  @JsonProperty("pmsSdkExecutionPoolConfig") private ThreadPoolConfig pmsSdkExecutionPoolConfig;
  @JsonProperty("pmsSdkTriggerActivationPoolConfig") private ThreadPoolConfig pmsSdkTriggerActivationPoolConfig;
  @JsonProperty("pmsSdkOrchestrationEventPoolConfig") private ThreadPoolConfig pmsSdkOrchestrationEventPoolConfig;
  @JsonProperty("pmsSdkOrchestrationHandlerPoolConfig") private ThreadPoolConfig pmsSdkOrchestrationHandlerPoolConfig;
  @JsonProperty("debeziumConsumersConfigs") DebeziumConsumersConfig debeziumConsumersConfigs;
  @JsonProperty("cdcKafkaConfig") CdcKafkaConfig cdcKafkaConfig;
  @JsonProperty("orchestrationPoolConfig") private ThreadPoolConfig orchestrationPoolConfig;
  @JsonProperty("nodeExecutionObserverPoolConfig") private ThreadPoolConfig nodeExecutionObserverPoolConfig;
  @JsonProperty("ciSecretResolutionPoolConfig") private ThreadPoolConfig ciSecretResolutionPoolConfig;
  @JsonProperty("sdkResponseEventPoolConfig") private ThreadPoolConfig sdkResponseEventPoolConfig;
  @JsonProperty("grpcServerConfig") private GrpcServerConfig grpcServerConfig;
  @JsonProperty("grpcClientConfigs") private Map<String, GrpcClientConfig> grpcClientConfigs;
  @JsonProperty("moduleSpecificInfoMap") private Map<String, ModuleSpecificInfo> moduleSpecificInfoMap;
  @JsonProperty("releaseManagementServiceClientConfig")
  private ServiceHttpClientConfig releaseManagementServiceClientConfig;
  @JsonProperty("releaseManagementEventType") private String releaseManagementEventType;
  @JsonProperty("releaseManagementMaxArtifactsPerType") private int releaseManagementMaxArtifactsPerType;
  @JsonProperty("changeAdvisorServiceClientConfig") private ServiceHttpClientConfig changeAdvisorServiceClientConfig;
  @JsonProperty("ngManagerServiceHttpClientConfig") private ServiceHttpClientConfig ngManagerServiceHttpClientConfig;
  @JsonProperty("rhsClientConfig") private ServiceHttpClientConfig rhsClientConfig;
  @JsonProperty("rhsEnabled") private boolean rhsEnabled;
  @JsonProperty("rhsServiceSecret") @ConfigSecret private String rhsServiceSecret;
  @JsonProperty("secretConnectorServiceClientConfig")
  private ServiceHttpClientConfig secretConnectorServiceClientConfig;
  @JsonProperty("secretConnectorServiceEnabled") private boolean secretConnectorServiceEnabled;
  @JsonProperty("secretConnectorServiceSecret") @ConfigSecret private String secretConnectorServiceSecret;
  @JsonProperty("platformConfigServiceClientConfig") private ServiceHttpClientConfig platformConfigServiceClientConfig;
  @JsonProperty("platformConfigServiceEnabled") private boolean platformConfigServiceEnabled;
  @JsonProperty("platformConfigServiceSecret") @ConfigSecret private String platformConfigServiceSecret;
  @JsonProperty("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceClientConfig;
  @JsonProperty("templateServiceClientConfig") private ServiceHttpClientConfig templateServiceClientConfig;
  @JsonProperty("harnessCodeClientConfig") private ServiceHttpClientConfig harnessCodeServiceClientConfig;
  @JsonProperty("harnessRegistryClientConfig") private ServiceHttpClientConfig harnessRegistryServiceClientConfig;
  @JsonProperty("harnessCodeGitBaseUrl") private String harnessCodeGitBaseUrl;
  @JsonProperty("ciServiceClientConfig") private ServiceHttpClientConfig ciServiceClientConfig;
  @JsonProperty("goConvertConnectionConfig") private GoConvertConnectionConfig goConvertConnectionConfig;
  @JsonProperty("containerStepConfigureWithCi") private boolean containerStepConfigureWithCi;
  @JsonProperty("ngManagerServiceSecret") private String ngManagerServiceSecret;
  @JsonProperty("annotationsSecret") private String annotationsSecret;
  @JsonProperty("annotationsBaseUrl") private String annotationsBaseUrl;
  @JsonProperty("pipelineServiceSecret") private String pipelineServiceSecret;
  @JsonProperty("templateServiceSecret") private String templateServiceSecret;
  @JsonProperty("harnessCodeServiceSecret") private String harnessCodeServiceSecret;
  @JsonProperty("harnessRegistryServiceSecret") private String harnessRegistryServiceSecret;
  @JsonProperty("ciServiceSecret") private String ciServiceSecret;
  @JsonProperty("jwtAuthSecret") private String jwtAuthSecret;
  @JsonProperty("meshIdentity") private io.harness.security.mesh.MeshIdentityConfig meshIdentity;
  @JsonProperty("jwtIdentityServiceSecret") private String jwtIdentityServiceSecret;
  @JsonProperty("harnessIdClientConfig") private HarnessIdServiceConfig harnessIdClientConfig;
  @JsonProperty("resourceGroupClientConfig") @ConfigSecret private ResourceGroupClientConfig resourceGroupClientConfig;
  @JsonProperty("redisLockConfig") private RedisConfig redisLockConfig;
  @JsonProperty("distributedLockImplementation") private DistributedLockImplementation distributedLockImplementation;
  @Builder.Default @JsonProperty("allowedOrigins") private List<String> allowedOrigins = new ArrayList<>();
  @JsonProperty("notificationClient") private NotificationClientConfiguration notificationClientConfiguration;
  @JsonProperty("eventsFramework") private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("eventsFrameworkSnapshotDebezium")
  private EventsFrameworkConfiguration eventsFrameworkSnapshotConfiguration;
  @JsonProperty("pipelineServiceBaseUrl") private String pipelineServiceBaseUrl;
  @JsonProperty("pmsApiBaseUrl") private String pmsApiBaseUrl;
  @JsonProperty("idpServiceSecret") private String idpServiceSecret;
  @JsonProperty("idpBaseUrl") private String idpBaseUrl;
  @JsonProperty("yamlSchemaClientConfig") private YamlSchemaClientConfig yamlSchemaClientConfig;
  @JsonProperty("accessControlClient") private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("flexEnforcementClient") private FlexEnforcementClientConfig flexEnforcementClientConfig;
  @JsonProperty("timescaledb") private TimeScaleDBConfig timeScaleDBConfig;
  @JsonProperty("postgres") private PostgresDBConfig postgresDBConfig;
  @JsonProperty("secondaryTimescaledb") @ConfigSecret private TimeScaleDBConfig secondaryTimeScaleDBConfig;
  @JsonProperty("orchestrationStepConfig") private OrchestrationStepConfig orchestrationStepConfig;
  @JsonProperty("enableDashboardTimescale") private Boolean enableDashboardTimescale;
  @JsonProperty("enablePostgres") private Boolean enablePostgres;
  @JsonProperty("auditClientConfig") private ServiceHttpClientConfig auditClientConfig;
  @JsonProperty(value = "enableAudit") private boolean enableAudit;
  @JsonProperty("cacheConfig") private CacheConfig cacheConfig;
  @JsonProperty("shouldUseEventsFrameworkSnapshotDebezium") private boolean shouldUseEventsFrameworkSnapshotDebezium;
  @JsonProperty("hostname") String hostname = "localhost";
  @JsonProperty("basePathPrefix") String basePathPrefix = "";
  @JsonProperty("segmentConfiguration") private SegmentConfiguration segmentConfiguration;
  @JsonProperty("pipelineEventConsumersConfig") PipelineServiceConsumersConfig pipelineServiceConsumersConfig;
  @JsonProperty("enforcementClientConfiguration") EnforcementClientConfiguration enforcementClientConfiguration;
  @JsonProperty("shouldUseInstanceCache") boolean shouldUseInstanceCache;
  @JsonProperty("skipSdkMongoRegistration") private boolean skipSdkMongoRegistration;
  @JsonProperty("pmsPlanCreatorServicePoolConfig") private ThreadPoolConfig pmsPlanCreatorServicePoolConfig;
  @JsonProperty("planCreatorMergeServicePoolConfig") private ThreadPoolConfig planCreatorMergeServicePoolConfig;
  @JsonProperty("planCreationServicePoolConfig") private ThreadPoolConfig planCreationServicePoolConfig;
  @JsonProperty("customWebhookTriggerExecutionPoolConfig")
  private ThreadPoolConfig customWebhookTriggerExecutionPoolConfig;
  @JsonProperty("executionRetentionSyncServicePoolConfig")
  private ThreadPoolConfig executionRetentionSyncServicePoolConfig;
  @JsonProperty("variableCreatorMergeServicePoolConfig") private ThreadPoolConfig variableCreatorMergeServicePoolConfig;
  @JsonProperty("filterCreatorMergeServicePoolConfig") private ThreadPoolConfig filterCreatorMergeServicePoolConfig;
  @JsonProperty("pipelineRedisEventsConfig") private PipelineRedisEventsConfig pipelineRedisEventsConfig;
  @JsonProperty("pipelineSdkRedisEventsConfig") private PipelineSdkRedisEventsConfig pipelineSdkRedisEventsConfig;
  @JsonProperty("orchestrationRedisEventsConfig") private OrchestrationRedisEventsConfig orchestrationRedisEventsConfig;
  @JsonProperty("orchestrationLogConfiguration") private OrchestrationLogConfiguration orchestrationLogConfiguration;
  @JsonProperty("flowGovernorConfig") private FlowGovernorConfig flowGovernorConfig;
  @JsonProperty("planCreatorMergeServiceDependencyBatch") private Integer planCreatorMergeServiceDependencyBatch;
  @JsonProperty("jsonExpansionPoolConfig") private ThreadPoolConfig jsonExpansionPoolConfig;
  @JsonProperty("inputsMetadataPoolConfig") private ThreadPoolConfig inputsMetadataPoolConfig;
  @JsonProperty("jsonExpansionRequestBatchSize") private Integer jsonExpansionBatchSize;
  @JsonProperty("elasticsearch") ElasticSearchDBConfig elasticSearchDBConfig;
  @JsonProperty("executionStatusReconciliation")
  ExecutionStatusReconciliationConfig executionStatusReconciliationConfig;
  @JsonProperty("resourceRestraintReconciliation")
  ResourceRestraintReconciliationConfig resourceRestraintReconciliationConfig;
  @JsonProperty("objectStore") ObjectStoreConfig objectStoreConfig;
  @JsonProperty("inputsMetadataRequestBatchSize") private Integer inputsMetadataBatchSize;
  @JsonProperty("initiateNodeRequestBatchSize") private Integer initiateNodeRequestBatchSize;
  @JsonProperty("dataRetention") DataRetentionConfig dataRetentionConfig;
  @JsonProperty("orchestrationGraphCacheCleanup")
  OrchestrationGraphCacheCleanupConfig orchestrationGraphCacheCleanupConfig;
  @JsonProperty(value = "enableOpentelemetry") private Boolean enableOpentelemetry;
  @JsonProperty(value = "enableLoopDetection") private Boolean enableLoopDetection;
  @JsonProperty(value = "loopDetectionThreshold") private int loopDetectionThreshold;
  @JsonProperty(value = "orchestrationRestrictionConfiguration")
  OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration;
  @JsonProperty("yamlSchemaExecutorServiceConfig") private ThreadPoolConfig yamlSchemaExecutorServiceConfig;
  @JsonProperty("dashboardExecutorServiceConfig") private ThreadPoolConfig dashboardExecutorServiceConfig;
  @JsonProperty(value = "containerStepConfig") ContainerExecutionConfig containerExecutionConfig;
  @JsonProperty(value = "grpcNegotiationType") NegotiationType grpcNegotiationType;
  // If flag is enabled, only one thread does Notify response cleanup.
  @JsonProperty(value = "lockNotifyResponseCleanup") private boolean lockNotifyResponseCleanup;
  @JsonProperty("queueServiceClientConfig") private QueueServiceClientConfig queueServiceClientConfig;
  @JsonProperty(value = "disableFreezeNotificationTemplate") private boolean disableFreezeNotificationTemplate;
  @JsonProperty("cfClientConfig") @ConfigSecret private CfClientConfig cfClientConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;
  @JsonProperty("triggerAuthenticationPoolConfig") private ThreadPoolConfig triggerAuthenticationPoolConfig;
  @JsonProperty("expandedJsonConfig") private ExpandedJsonLockConfig expandedJsonLockConfig;
  @JsonProperty("pipelineSetupUsageCreationExecutorServiceConfig")
  private ThreadPoolConfig pipelineSetupUsageCreationPoolConfig;
  @JsonProperty("streamPerServiceConfiguration") private boolean streamPerServiceConfiguration;

  @JsonProperty("podCleanUpThreadPoolConfig") private ThreadPoolConfig podCleanUpThreadPoolConfig;

  @JsonProperty("staticSchemaFileURL") private String staticSchemaFileURL;
  @JsonProperty("timeoutIteratorMode") private String timeoutIteratorMode;
  @JsonProperty("enableCustomWebhookRedisBatchModeIterator") private Boolean enableCustomWebhookRedisBatchModeIterator;
  @JsonProperty("webhookEventHsqsDequeueConfig") private HsqsDequeueConfig webhookEventHsqsDequeueConfig;
  @JsonProperty("planCreationHsqsDequeueConfig") private HsqsDequeueConfig planCreationHsqsDequeueConfig;
  @JsonProperty("maxMultiArtifactTriggerSources") private Integer maxMultiArtifactTriggerSources;
  @JsonProperty("graphConsumerSleepIntervalMs") private Integer graphConsumerSleepIntervalMs;
  @JsonProperty("asyncFilterCreationConsumerSleepIntervalMs")
  private Integer asyncFilterCreationConsumerSleepIntervalMs;
  @JsonProperty("publishAdviserEventForCustomAdvisers") private Boolean publishAdviserEventForCustomAdvisers;
  @JsonProperty("pipelineExecutionDetailsDeleteMaxBatchSize")
  private Integer pipelineExecutionDetailsDeleteMaxBatchSize;
  @JsonProperty("pipelineExecutionClusterStepConcurrencyLimit")
  private Long pipelineExecutionClusterStepConcurrencyLimit;
  @JsonProperty("pipelineExecutionDefaultMaxLeafStepConcurrency")
  private Integer pipelineExecutionDefaultMaxLeafStepConcurrency;
  @JsonProperty("stepConcurrencyCounterMutationEnabled") private Boolean stepConcurrencyCounterMutationEnabled;
  @JsonProperty("stepConcurrencyQueueStoreEnabled") private Boolean stepConcurrencyQueueStoreEnabled;
  @JsonProperty("stepConcurrencyGateMode") private String stepConcurrencyGateMode;
  @JsonProperty("pipelineExecutionCounterRebuildJobEnabled") private Boolean pipelineExecutionCounterRebuildJobEnabled;
  @JsonProperty("useDbQueueForPlanCreation") private Boolean useDbQueueForPlanCreation;
  @JsonProperty("planCreationDbQueueBatchSize") private Integer planCreationDbQueueBatchSize;
  @JsonProperty("planConcurrencyCounterMutationEnabled") private Boolean planConcurrencyCounterMutationEnabled;
  @JsonProperty("planConcurrencyGateMode") private String planConcurrencyGateMode;
  @JsonProperty("planConcurrencyRebuildJobEnabled") private Boolean planConcurrencyRebuildJobEnabled;
  private boolean useQueueServiceForWebhookTriggers;
  @JsonProperty("useQueueServiceForPlanCreation") private boolean useQueueServiceForPlanCreation;
  @JsonProperty("useQueueServiceForCustomWebhookTriggers") private boolean useQueueServiceForCustomWebhookTriggers;
  @JsonProperty(value = "useSchemaFromHarnessSchemaRepo") private Boolean useSchemaFromHarnessSchemaRepo;
  @JsonProperty("preflightConnectorTimeoutSeconds") private int preflightConnectorTimeoutSeconds;
  @JsonProperty("stuckNodeExecutionsMarkingConfig")
  private StuckNodeExecutionsMarkingConfig stuckNodeExecutionsMarkingConfig;
  @JsonProperty("stuckExecutionDetectorEnabled") private boolean stuckExecutionDetectorEnabled;
  @JsonProperty("useRemoteGrpcForPipeline") private boolean useRemoteGrpcForPipeline;
  private boolean enableDelegateResponseCleanupIterator;
  private boolean enableWaitNotifyEngineOptimisation;
  @JsonProperty("publishNodeExecutionTimeTakenDetails") private Boolean publishNodeExecutionTimeTakenDetails;
  @JsonProperty("bulkReconciliationConsumerSleepIntervalMs") private Integer bulkReconciliationConsumerSleepIntervalMs;
  @JsonProperty("outboxEventPollConfig") private PipelineOutboxPollConfiguration pipelineOutboxPollConfiguration;
  @JsonProperty("kafkaModuleConfig") KafkaModuleConfig kafkaModuleConfig;
  @JsonProperty("graphGenerationStreamsConfig")
  private GraphGenerationStreamsConfig graphGenerationStreamsConfig = GraphGenerationStreamsConfig.builder().build();
  @JsonProperty("stepDataIngestionTopicName") String stepDataIngestionTopicName;
  @JsonProperty("pipelineDataIngestionTopicName") String pipelineDataIngestionTopicName;
  @JsonProperty("stageDataIngestionTopicName") String stageDataIngestionTopicName;
  @JsonProperty("cdcNodeExecutionTopicName") String cdcNodeExecutionTopicName;
  @JsonProperty("gcsMigrationConfig") private GCSMigrationConfig gcsMigrationConfig;
  @JsonProperty("projectMovementEntityMigrationMetricsConfig")
  private ProjectEntityMigrationMetricsConfig projectMovementProjectEntityMigrationMetricsConfig;
  @JsonProperty("ngBaseUrl") private String ngBaseUrl;

  private String managerServiceSecret;
  private String managerTarget;
  private String managerAuthority;
  private ServiceHttpClientConfig managerClientConfig;
  @JsonProperty("fmeServiceClientConfig") private io.harness.fme.FmeServiceConfig fmeServiceClientConfig;
  @JsonProperty("aiSreServiceClientConfig") private io.harness.aisre.AiSreServiceConfig aiSreServiceClientConfig;
  private LogStreamingServiceConfiguration logStreamingServiceConfig;
  private TriggerConfiguration triggerConfig;
  private OpaServiceConfiguration opaServerConfig;
  private String policyManagerSecret;
  private ServiceHttpClientConfig opaClientConfig;
  @JsonProperty("opaEvaluationPluginImage") private String opaEvaluationPluginImage;

  private SSCAServiceConfig sscaServiceConfig;

  private STOServiceConfig stoServiceConfig;

  @JsonProperty("qwietServiceConfig") private QwietServiceConfig qwietServiceConfig;

  @JsonProperty("cvngClientConfig") private ServiceHttpClientConfig cvngClientConfig;
  private String cvngServiceSecret;

  private PipelineServiceIteratorsConfig iteratorsConfig;
  private boolean shouldDeployWithGitSync;
  @Builder.Default @JsonProperty("enableTenantIsolationFilter") private boolean enableTenantIsolationFilter = true;
  private GitSdkConfiguration gitSdkConfiguration;
  private DelegatePollingConfig delegatePollingConfig;
  private ThreadPoolConfig
      pipelineAsyncValidationPoolConfig; // to be used for defining thread config for async validations of Pipelines

  @JsonProperty("policyEvaluationDetailsMaxPageSize") private Integer policyEvaluationDetailsMaxPageSize;
  @JsonProperty("gitAwareEntityHelperPoolConfig") private ThreadPoolConfig gitAwareEntityHelperPoolConfig;
  @JsonProperty("pipelineYamlConversionPoolConfig") private ThreadPoolConfig pipelineYamlConversionPoolConfig;
  @JsonProperty("opaGitxStatusPoolConfig") private ThreadPoolConfig opaGitxStatusPoolConfig;
  @JsonProperty("workloadIdentityTokenPoolConfig") private ThreadPoolConfig workloadIdentityTokenPoolConfig;

  public PipelineServiceConfiguration() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    defaultServerFactory.setJerseyRootPath("/api");
    defaultServerFactory.setRegisterDefaultExceptionMappers(Boolean.FALSE);
    defaultServerFactory.setAdminContextPath("/admin");
    defaultServerFactory.setAdminConnectors(singletonList(getDefaultAdminConnectorFactory()));
    defaultServerFactory.setApplicationConnectors(singletonList(getDefaultApplicationConnectorFactory()));
    defaultServerFactory.setRequestLogFactory(getDefaultLogbackAccessRequestLogFactory());
    defaultServerFactory.setMaxThreads(512);
    super.setServerFactory(defaultServerFactory);
  }

  @Override
  public void setServerFactory(ServerFactory factory) {
    DefaultServerFactory defaultServerFactory = (DefaultServerFactory) factory;
    ((DefaultServerFactory) getServerFactory())
        .setApplicationConnectors(defaultServerFactory.getApplicationConnectors());
    ((DefaultServerFactory) getServerFactory()).setAdminConnectors(defaultServerFactory.getAdminConnectors());
    ((DefaultServerFactory) getServerFactory()).setRequestLogFactory(defaultServerFactory.getRequestLogFactory());
    ((DefaultServerFactory) getServerFactory()).setMaxThreads(defaultServerFactory.getMaxThreads());
  }

  public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration = new SwaggerBundleConfiguration();
    String resourcePackage = String.join(",", getUniquePackages(HARNESS_RESOURCE_CLASSES));
    defaultSwaggerBundleConfiguration.setResourcePackage(resourcePackage);
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost(hostname);
    defaultSwaggerBundleConfiguration.setUriPrefix(basePathPrefix);
    defaultSwaggerBundleConfiguration.setTitle("PMS API Reference");
    defaultSwaggerBundleConfiguration.setVersion("2.0");
    return Optional.ofNullable(swaggerBundleConfiguration).orElse(defaultSwaggerBundleConfiguration);
  }

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(klazz
            -> StringUtils.startsWithAny(klazz.getPackage().getName(), RESOURCE_PACKAGE, NG_TRIGGER_RESOURCE_PACKAGE,
                FILTER_PACKAGE, ENFORCEMENT_PACKAGE, NOTIFY_PACKAGE))
        .collect(Collectors.toSet());
  }

  private ConnectorFactory getDefaultApplicationConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(12001);
    return factory;
  }

  private ConnectorFactory getDefaultAdminConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(12002);
    return factory;
  }

  private RequestLogFactory getDefaultLogbackAccessRequestLogFactory() {
    LogbackAccessRequestLogFactory logbackAccessRequestLogFactory = new LogbackAccessRequestLogFactory();
    FileAppenderFactory<IAccessEvent> fileAppenderFactory = new FileAppenderFactory<>();
    fileAppenderFactory.setArchive(true);
    fileAppenderFactory.setCurrentLogFilename("access.log");
    fileAppenderFactory.setThreshold(Level.ALL.toString());
    fileAppenderFactory.setArchivedLogFilenamePattern("access.%d.log.gz");
    fileAppenderFactory.setArchivedFileCount(14);
    logbackAccessRequestLogFactory.setAppenders(ImmutableList.of(fileAppenderFactory));
    return logbackAccessRequestLogFactory;
  }

  private static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }

  public static Set<String> getOpenApiResources() {
    return HARNESS_RESOURCE_CLASSES.stream()
        .filter(x -> x.isAnnotationPresent(Tag.class))
        .map(Class::getName)
        .collect(toSet());
  }

  @JsonIgnore
  public OpenAPIConfiguration getOasConfig() {
    OpenAPI oas = new OpenAPI();
    Info info = new Info()
                    .title("Pipeline Service API Reference")
                    .description("This is the Open Api Spec 3 for the Pipeline Service. This is under active "
                        + "development. Beware of the breaking change with respect to the generated code stub")
                    .termsOfService("https://harness.io/terms-of-use/")
                    .version("3.0")
                    .contact(new Contact().email("contact@harness.io"));
    oas.info(info);
    URL baseurl = null;
    try {
      baseurl = new URL("https", hostname, basePathPrefix);
      Server server = new Server();
      server.setUrl(baseurl.toString());
      oas.servers(Collections.singletonList(server));
    } catch (MalformedURLException e) {
      log.error("failed to set baseurl for server, {}/{}", hostname, basePathPrefix);
    }
    Set<String> resourceClasses = getOpenApiResources();
    return new SwaggerConfiguration()
        .openAPI(oas)
        .prettyPrint(true)
        .resourceClasses(resourceClasses)
        .scannerClass("io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (mongoConfig != null) {
      dbAliases.add(mongoConfig.getAliasDBName());
    }
    return dbAliases;
  }
}

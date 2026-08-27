/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.app;

import static io.harness.idp.provision.ProvisionConstants.PROVISION_MODULE_CONFIG;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;

import io.harness.AccessControlClientConfiguration;
import io.harness.accesscontrol.AccessControlAdminClientConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.CacheServiceConfig;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.cache.CacheConfig;
import io.harness.cf.CfClientConfig;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.coverage.CoverageServiceConfig;
import io.harness.clients.POServerConfig;
import io.harness.enforcement.client.servicedependencies.EnforcementClientConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.ff.FeatureFlagConfig;
import io.harness.file.FileServiceConfiguration;
import io.harness.gitsync.GitSdkConfiguration;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.harnessid.client.HarnessIdServiceConfig;
import io.harness.idp.catalog.config.CatalogContentConfig;
import io.harness.idp.common.DslClientConfig;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.common.IdpAppConfig;
import io.harness.idp.common.OkHttpClientConnectionPoolConfig;
import io.harness.idp.common.encryption.IdpContentEncryptionConfig;
import io.harness.idp.config.CdcKafkaConfig;
import io.harness.idp.dataplatform.UdpTypeIngestionConfig;
import io.harness.idp.events.config.DebeziumConsumersConfig;
import io.harness.idp.homepage.config.HomePageCardIconConfig;
import io.harness.idp.iterators.config.IteratorsConfig;
import io.harness.idp.onboarding.config.OnboardingModuleConfig;
import io.harness.idp.onboarding.config.OnboardingModuleV2Config;
import io.harness.idp.plugin.config.CustomPluginsConfig;
import io.harness.idp.provision.ProvisionModuleConfig;
import io.harness.idp.proxy.config.ProxyAllowListConfig;
import io.harness.idp.scorecard.tiergroups.config.DefaultTierGroupConfig;
import io.harness.idp.scorecard.tiergroups.config.TierIconConfig;
import io.harness.idp.workflowlibrary.config.WorkflowLibraryConfig;
import io.harness.iterator.IteratorExecutionHandler.DynamicIteratorConfig;
import io.harness.kafka.KafkaModuleConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.mongo.MongoConfig;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.pms.sdk.core.PipelineSdkRedisEventsConfig;
import io.harness.queryservice.QueryServiceConfig;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.resourcegroupclient.remote.ResourceGroupClientConfig;
import io.harness.secret.ConfigSecret;
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.sto.beans.entities.QwietServiceConfig;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.threading.ThreadPoolConfig;
import io.harness.timescaledb.TimeScaleDBConfig;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
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
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Getter
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class IdpConfiguration extends Configuration {
  @Setter @JsonProperty("mongo") private MongoConfig mongoConfig;
  @JsonProperty("eventsFramework") private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("logStreamingServiceConfig")
  @ConfigSecret
  private LogStreamingServiceConfiguration logStreamingServiceConfig;
  @JsonProperty("redisLockConfig") private RedisConfig redisLockConfig;
  @JsonProperty("distributedLockImplementation") private DistributedLockImplementation distributedLockImplementation;
  @JsonProperty("managerClientConfig") private ServiceHttpClientConfig managerClientConfig;
  @Setter
  @JsonProperty("ngManagerServiceHttpClientConfig")
  private ServiceHttpClientConfig ngManagerServiceHttpClientConfig;
  @JsonProperty("rhsClientConfig") private ServiceHttpClientConfig rhsClientConfig;
  @JsonProperty("rhsEnabled") private boolean rhsEnabled;
  @JsonProperty("rhsServiceSecret") @ConfigSecret private String rhsServiceSecret;
  @Setter @JsonProperty("scsClientConfig") private ServiceHttpClientConfig scsClientConfig;
  @Setter @JsonProperty("scsServiceSecret") @ConfigSecret private String scsServiceSecret;
  @Setter @JsonProperty("scsCutoverEnabled") private boolean scsCutoverEnabled;
  @JsonProperty("platformConfigServiceClientConfig") private ServiceHttpClientConfig platformConfigServiceClientConfig;
  @JsonProperty("platformConfigServiceEnabled") private boolean platformConfigServiceEnabled;
  @JsonProperty("platformConfigServiceSecret") @ConfigSecret private String platformConfigServiceSecret;
  @JsonProperty("ngManagerServiceSecret") private String ngManagerServiceSecret;
  @JsonProperty("managerServiceSecret") private String managerServiceSecret;
  @JsonProperty("backstageHttpClientConfig") private ServiceHttpClientConfig backstageHttpClientConfig;
  @JsonProperty("idpAgentHttpClientConfig") private ServiceHttpClientConfig idpAgentHttpClientConfig;
  @JsonProperty("backstageServiceSecret") private String backstageServiceSecret;
  @JsonProperty("idpServiceSecret") private String idpServiceSecret;
  @JsonProperty("idpAutomationGitHubToken") private String idpAutomationGitHubToken;
  @JsonProperty("idpAutomationXApiKey") private String idpAutomationXApiKey;
  @JsonProperty("jwtAuthSecret") private String jwtAuthSecret;
  @JsonProperty("meshIdentity") private io.harness.security.mesh.MeshIdentityConfig meshIdentity;
  @JsonProperty("jwtIdentityServiceSecret") private String jwtIdentityServiceSecret;
  @JsonProperty("harnessIdClientConfig") private HarnessIdServiceConfig harnessIdClientConfig;
  @JsonProperty("onboardingModuleConfig") private OnboardingModuleConfig onboardingModuleConfig;
  @JsonProperty("onboardingModuleV2Config") private OnboardingModuleV2Config onboardingModuleV2Config;
  @JsonProperty("grpcNegotiationType") NegotiationType grpcNegotiationType;
  @JsonProperty("accessControlClient") private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("idpAppConfig") private IdpAppConfig idpAppConfig;
  @JsonProperty("backstageEntitiesFetchLimit") private String backstageEntitiesFetchLimit;
  @JsonProperty("env") private String env;
  @JsonProperty("base") private String base;
  @JsonProperty("proxyEndPointEnv") private String proxyEndPointEnv;
  @JsonProperty("devSpaceDefaultBackstageNamespace") private String devSpaceDefaultBackstageNamespace;
  @JsonProperty("devSpaceDefaultAccountId") private String devSpaceDefaultAccountId;
  @JsonProperty(PROVISION_MODULE_CONFIG) private ProvisionModuleConfig provisionModuleConfig;
  @JsonProperty("backstageAppBaseUrl") private String backstageAppBaseUrl;
  @JsonProperty("backstagePostgresHost") private String backstagePostgresHost;
  @JsonProperty("pmsSdkGrpcServerConfig") private GrpcServerConfig pmsSdkGrpcServerConfig;
  @JsonProperty("pmsGrpcClientConfig") private GrpcClientConfig pmsGrpcClientConfig;
  @JsonProperty("shouldConfigureWithPMS") private Boolean shouldConfigureWithPMS;
  @JsonProperty("cacheConfig") private CacheConfig cacheConfig;
  @JsonProperty("delegateSelectorsCacheMode") private String delegateSelectorsCacheMode;
  @JsonProperty("idpEncryptionSecret") private String idpEncryptionSecret;
  @JsonProperty("proxyAllowList") private ProxyAllowListConfig proxyAllowList;
  @JsonProperty("shouldConfigureWithNotification") private Boolean shouldConfigureWithNotification;
  @JsonProperty("notificationClient") private NotificationClientConfiguration notificationClientConfiguration;
  @JsonProperty("notificationConfigs") private HashMap<String, String> notificationConfigs;
  @JsonProperty("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceConfiguration;
  @JsonProperty("pipelineServiceSecret") private String pipelineServiceSecret;
  @JsonProperty("jwtExternalServiceSecret") private String jwtExternalServiceSecret;
  @JsonProperty("tiServiceConfig") private TIServiceConfig tiServiceConfig;
  @JsonProperty("coverageServiceConfig") private CoverageServiceConfig coverageServiceConfig;
  @JsonProperty("iteratorsConfig") private IteratorsConfig iteratorsConfig;
  @JsonProperty("workflowLibraryConfig") private WorkflowLibraryConfig workflowLibraryConfig;
  @JsonProperty("cpu") private String cpu;
  @JsonProperty("scoreComputerThreadsPerCoreForIterator") private String scoreComputerThreadsPerCoreForIterator;
  @JsonProperty("scoreComputerThreadsPerCoreForUser") private String scoreComputerThreadsPerCoreForUser;
  @JsonProperty("aggregationRuleComputeThreadsPerCore") private String aggregationRuleComputeThreadsPerCore;
  @JsonProperty("allowedOrigins") private List<String> allowedOrigins = Lists.newArrayList();
  @JsonProperty("hostname") String hostname = "localhost";
  @JsonProperty("basePathPrefix") String basePathPrefix = "";
  @JsonProperty("auditClientConfig") private ServiceHttpClientConfig auditClientConfig;
  @JsonProperty("enableAudit") private boolean enableAudit;
  private String managerTarget;
  private String managerAuthority;
  @JsonProperty("streamPerServiceConfiguration") private boolean streamPerServiceConfiguration;
  @JsonProperty("internalAccounts") private List<String> internalAccounts;
  @JsonProperty("logServiceConfig") private LogServiceConfig logServiceConfig;
  @JsonProperty("sscaServiceConfig") private SSCAServiceConfig sscaServiceConfig;
  @JsonProperty("cacheServiceConfig") private CacheServiceConfig cacheServiceConfig;
  @JsonProperty("stoServiceRestClientConfig") private ServiceHttpClientConfig stoServiceRestClientConfig;
  @JsonProperty("stoServiceConfig") private STOServiceConfig stoServiceConfig;
  @JsonProperty("qwietServiceConfig") private QwietServiceConfig qwietServiceConfig;
  @JsonProperty("apiUrl") private String apiUrl;
  @JsonProperty("iacmServiceConfig") private IACMServiceConfig iacmServiceConfig;
  @JsonProperty("poServerConfig") private POServerConfig poServerConfig;
  @JsonProperty("pmsSdkExecutionPoolConfig") private ThreadPoolConfig pmsSdkExecutionPoolConfig;
  @JsonProperty("pmsSdkOrchestrationEventPoolConfig") private ThreadPoolConfig pmsSdkOrchestrationEventPoolConfig;
  @JsonProperty("pmsSdkOrchestrationHandlerPoolConfig") private ThreadPoolConfig pmsSdkOrchestrationHandlerPoolConfig;
  @JsonProperty("pmsPlanCreatorServicePoolConfig") private ThreadPoolConfig pmsPlanCreatorServicePoolConfig;
  @JsonProperty("opaClientConfig") private ServiceHttpClientConfig opaClientConfig;
  @JsonProperty("policyManagerSecret") private String policyManagerSecret;
  @JsonProperty("queryServiceConfig") private QueryServiceConfig queryServiceConfig;
  @JsonProperty("opaConnectivityEnabled") private boolean opaConnectivityEnabled;
  @JsonProperty("ciExecutionServiceConfig") private CIExecutionServiceConfig ciExecutionServiceConfig;
  @JsonProperty("enforcementClientConfiguration") EnforcementClientConfiguration enforcementClientConfiguration;
  @JsonProperty("harnessCodeGitUrl") private String harnessCodeGitUrl;
  @JsonProperty("llmGatewayBaseUrl") private String llmGatewayBaseUrl;
  @JsonProperty("segmentConfiguration") private SegmentConfiguration segmentConfiguration;
  @JsonProperty("enableMetrics") private boolean enableMetrics;
  @JsonProperty("enableAPIMetrics") private boolean enableAPIMetrics;
  @JsonProperty("enableOpenTelemetry") private Boolean enableOpenTelemetry;
  @JsonProperty("allowedKindsForCatalogSync") private List<String> allowedKindsForCatalogSync;
  @JsonProperty("allowedKindsForAudit") private List<String> allowedKindsForAudit;
  @JsonProperty("customPlugins") private CustomPluginsConfig customPluginsConfig;
  @JsonProperty("catalogContent") private CatalogContentConfig catalogContentConfig;
  @JsonProperty("contentEncryption") private IdpContentEncryptionConfig contentEncryptionConfig;
  @JsonProperty("debeziumConsumersConfigs") DebeziumConsumersConfig debeziumConsumersConfigs;
  @JsonProperty("cdcKafkaConfig") private CdcKafkaConfig cdcKafkaConfig;
  @JsonProperty("enableDashboardTimescale") private Boolean enableDashboardTimescale;
  @JsonProperty("timescaledb") private TimeScaleDBConfig timeScaleDBConfig;
  @JsonProperty("numberOfThreadsToUseForConsumers") private HashMap<String, Integer> numberOfThreadsToUseForConsumers;
  @JsonProperty("okHttpClientConnectionPoolConfigs")
  private HashMap<String, OkHttpClientConnectionPoolConfig> okHttpClientConnectionPoolConfigs;
  @JsonProperty("accessControlAdminClient")
  @ConfigSecret
  private AccessControlAdminClientConfiguration accessControlAdminClientConfiguration;
  @JsonProperty("resourceGroupClientConfig") @ConfigSecret private ResourceGroupClientConfig resourceGroupClientConfig;
  @JsonProperty("dslClientConfig") private DslClientConfig dslClientConfig;
  @JsonProperty("deploymentType") private String deploymentType;
  @JsonProperty("deploymentNamespace") private String deploymentNamespace;
  @JsonProperty("dynamicConfigResolution") private boolean dynamicConfigResolution;
  @JsonProperty("harnessCodeRepoConfig") private HarnessCodeRepoConfig harnessCodeRepoConfig;
  @JsonProperty("fileServiceConfiguration") private FileServiceConfiguration fileServiceConfiguration;
  @JsonProperty("notifyResponseIterator") private DynamicIteratorConfig notifyResponseRedisConfig;
  @JsonProperty("homePageCardIconConfig") private HomePageCardIconConfig homePageCardIconConfig;
  @JsonProperty("defaultTierGroupConfig") private DefaultTierGroupConfig defaultTierGroupConfig;
  @JsonProperty("tierIconConfig") private TierIconConfig tierIconConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;
  @JsonProperty("cfClientConfig") @ConfigSecret private CfClientConfig cfClientConfig;
  @JsonProperty("mongoReplacementConfig") private HashMap<String, String> mongoReplacementConfig;
  private boolean enableDelegateResponseCleanupIterator;
  private boolean enableWaitNotifyEngineOptimisation;
  @JsonProperty("enableQueue") private Boolean enableQueue;
  @JsonProperty("pipelineSdkRedisEventsConfig") private PipelineSdkRedisEventsConfig pipelineSdkRedisEventsConfig;
  @JsonProperty("userBillingMetricJobEnabled") private boolean userBillingMetricJobEnabled;
  @JsonProperty("gitSdkConfiguration") private GitSdkConfiguration gitSdkConfiguration;
  @JsonProperty("gitAwareEntityHelperPoolConfig") private ThreadPoolConfig gitAwareEntityHelperPoolConfig;
  @JsonProperty("gcsForTechDocsDelegate") private boolean gcsForTechDocsDelegate;
  @JsonProperty("kafkaModuleConfig") @ConfigSecret KafkaModuleConfig kafkaModuleConfig;
  @JsonProperty("udpTypeIngestion")
  private UdpTypeIngestionConfig udpTypeIngestionConfig = new UdpTypeIngestionConfig();
  @JsonProperty("harnessCiCdAnnotationsServiceUrl") String harnessCiCdAnnotationsServiceUrl;
  @JsonProperty("integrationManagerClientConfig") private ServiceHttpClientConfig integrationManagerClientConfig;
  @JsonProperty("integrationManagerSecret") private String integrationManagerSecret;
  @JsonProperty("integrationManagerIdpMappingId") private String integrationManagerIdpMappingId;
  @JsonProperty("integrationsHarnessCiCdAnnotationsServiceUrl") String integrationsHarnessCiCdAnnotationsServiceUrl;

  public static final Collection<Class<?>> HARNESS_RESOURCE_CLASSES = getResourceClasses();
  public static final String IDP_SPEC_PACKAGE = "io.harness.spec.server.idp.v1";
  public static final String SERVICES_PROXY_PACKAGE = "io.harness.idp.proxy.services";
  public static final String DELEGATE_PROXY_PACKAGE = "io.harness.idp.proxy.delegate";
  public static final String EXTERNAL_PROXY_PACKAGE = "io.harness.idp.proxy.external.resource";
  public static final String IDP_STEP_RESOURCES_PACKAGE = "io.harness.idp.pipeline";
  private static final String PLUGIN_PACKAGE = "io.harness.idp.configmanager.resource";
  public static final String IDP_HEALTH_PACKAGE = "io.harness.idp.health";
  public static final String LICENSING_USAGE_PACKAGE = "io.harness.licensing.usage.resources";
  public static final String IDP_LICENSE_USAGE_PACKAGE = "io.harness.idp.license.usage.resources";
  private static final String IDP_YAML_SCHEMA = "io.harness.idp.pipeline.stages.yamlschema";
  private static final String HOME_PAGE_RESOURCE = "io.harness.idp.homepage.resource";
  public static final String IDP_SCORECARD_RESOURCES_PACKAGE = "io.harness.idp.scorecard.tiergroups.resources";

  public IdpConfiguration() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    defaultServerFactory.setJerseyRootPath("/");
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

  @JsonIgnore
  public OpenAPIConfiguration getOasConfig() {
    OpenAPI oas = new OpenAPI();
    Info info = new Info()
                    .title("IDP Service API Reference")
                    .description("This is the Open Api Spec 3 for the IDP Service. This is under active development. "
                        + "Beware of the breaking change with respect to the generated code stub")
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
      log.error("The base URL of the server could not be set. {}/{}", hostname, basePathPrefix);
    }
    Collection<Class<?>> allResourceClasses = HARNESS_RESOURCE_CLASSES;
    final Set<String> resourceClasses =
        getOAS3ResourceClassesOnly(allResourceClasses).stream().map(Class::getCanonicalName).collect(toSet());
    return new SwaggerConfiguration()
        .openAPI(oas)
        .prettyPrint(true)
        .resourceClasses(resourceClasses)
        .scannerClass("io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  private ConnectorFactory getDefaultApplicationConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(12003);
    return factory;
  }

  private ConnectorFactory getDefaultAdminConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(12004);
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

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (mongoConfig != null) {
      dbAliases.add(mongoConfig.getAliasDBName());
    }
    return dbAliases;
  }

  /**
   * Resolves the HTTP client config for the secrets/connectors clients (PL-72327). When the SCS cutover
   * flag is on and an SCS config is present, traffic is routed to SCS; otherwise it falls back to the
   * shared ng-manager config so behavior is unchanged when the flag is off.
   */
  @JsonIgnore
  public ServiceHttpClientConfig getSecretsConnectorsServiceHttpClientConfig() {
    return scsCutoverEnabled && scsClientConfig != null ? scsClientConfig : ngManagerServiceHttpClientConfig;
  }

  /**
   * Resolves the service secret paired with {@link #getSecretsConnectorsServiceHttpClientConfig()}.
   * Falls back to the ng-manager secret when the SCS cutover flag is off or no SCS secret is configured.
   */
  @JsonIgnore
  public String getSecretsConnectorsServiceSecret() {
    return scsCutoverEnabled && StringUtils.isNotBlank(scsServiceSecret) ? scsServiceSecret : ngManagerServiceSecret;
  }

  public static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream()
        .filter(x -> x.isAnnotationPresent(Tag.class))
        .map(aClass -> aClass.getPackage().getName())
        .collect(toSet());
  }

  public static Collection<Class<?>> getOAS3ResourceClassesOnly(Collection<Class<?>> allResourceClasses) {
    return allResourceClasses.stream().collect(Collectors.toList());
  }

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(klazz
            -> StringUtils.startsWithAny(klazz.getPackage().getName(), IDP_SPEC_PACKAGE, SERVICES_PROXY_PACKAGE,
                DELEGATE_PROXY_PACKAGE, EXTERNAL_PROXY_PACKAGE, IDP_STEP_RESOURCES_PACKAGE, PLUGIN_PACKAGE,
                IDP_HEALTH_PACKAGE, IDP_YAML_SCHEMA, LICENSING_USAGE_PACKAGE, IDP_LICENSE_USAGE_PACKAGE,
                HOME_PAGE_RESOURCE, IDP_SCORECARD_RESOURCES_PACKAGE))
        .collect(Collectors.toSet());
  }
}

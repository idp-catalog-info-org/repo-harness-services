/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.stream.Collectors.toSet;

import io.harness.AccessControlClientConfiguration;
import io.harness.ScmConnectionConfig;
import io.harness.app.beans.entities.CacheServiceConfig;
import io.harness.beans.entities.AnnotationsConfig;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.cache.CacheConfig;
import io.harness.cf.CfClientConfig;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.coverage.CoverageServiceConfig;
import io.harness.dropwizard.bundles.configured.AssetsBundleConfiguration;
import io.harness.dropwizard.bundles.configured.AssetsConfiguration;
import io.harness.dropwizard.bundles.swagger.SwaggerBundleConfiguration;
import io.harness.enforcement.client.servicedependencies.EnforcementClientConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.ff.FeatureFlagConfig;
import io.harness.fulcio.beans.HarnessFulcioServiceConfig;
import io.harness.gitsync.GitSdkConfiguration;
import io.harness.goconvert.GoConvertConnectionConfig;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.harnessid.client.HarnessIdGrpcClientConfig;
import io.harness.hsa.beans.HSAServiceConfig;
import io.harness.iterator.IteratorExecutionHandler.DynamicIteratorConfig;
import io.harness.kafka.KafkaModuleConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.mongo.MongoConfig;
import io.harness.pms.sdk.core.PipelineSdkRedisEventsConfig;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secret.ConfigSecret;
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.sto.beans.entities.QwietServiceConfig;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.sto.beans.entities.TicketServiceConfig;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.threading.ThreadPoolConfig;
import io.harness.timescaledb.TimeScaleDBConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Slf4j
public class CIManagerConfiguration extends Configuration implements AssetsBundleConfiguration {
  public static final String BASE_PACKAGE = "io.harness.app.resources";

  public static final String CI_API_PACKAGE = "io.harness.ci.api";
  public static final String NG_PIPELINE_PACKAGE = "io.harness.ngpipeline";
  public static final String ENFORCEMENT_CLIENT_PACKAGE = "io.harness.enforcement.client.resources";
  public static final Collection<Class<?>> HARNESS_RESOURCE_CLASSES = getResourceClasses();

  @JsonProperty
  private AssetsConfiguration assetsConfiguration =
      AssetsConfiguration.builder()
          .mimeTypes(of("js", "application/json; charset=UTF-8", "zip", "application/zip"))
          .build();
  @JsonProperty("gitAwareEntityHelperPoolConfig") private ThreadPoolConfig gitAwareEntityHelperPoolConfig;
  @JsonProperty("meshIdentity") private io.harness.security.mesh.MeshIdentityConfig meshIdentity;
  @Builder.Default @JsonProperty("cimanager-mongo") private MongoConfig harnessCIMongo = MongoConfig.builder().build();
  @Builder.Default @JsonProperty("harness-mongo") private MongoConfig harnessMongo = MongoConfig.builder().build();
  @JsonProperty("swagger") private SwaggerBundleConfiguration swaggerBundleConfiguration;
  private ScmConnectionConfig scmConnectionConfig;
  @JsonProperty("goConvertConnectionConfig") private GoConvertConnectionConfig goConvertConnectionConfig;

  @JsonProperty("managerClientConfig") private ServiceHttpClientConfig managerClientConfig;
  @JsonProperty("ngManagerClientConfig") private ServiceHttpClientConfig ngManagerClientConfig;
  // Workload Identity (OIDC-without-connector): HarnessID gRPC (Register) + REST (token generate) client config.
  @JsonProperty("harnessIdGrpcClientConfig") private HarnessIdGrpcClientConfig harnessIdGrpcClientConfig;
  @JsonProperty("harnessIdRestClientConfig") private ServiceHttpClientConfig harnessIdRestClientConfig;
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
  @JsonProperty("timescaledb") private TimeScaleDBConfig timeScaleDBConfig;
  @JsonProperty("accessControlClient") private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("eventsFramework") private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("cacheConfig") private CacheConfig cacheConfig;
  @JsonProperty("enforcementClientConfiguration") EnforcementClientConfiguration enforcementClientConfiguration;
  @JsonProperty("pmsSdkOrchestrationEventPoolConfig") private ThreadPoolConfig pmsSdkOrchestrationEventPoolConfig;
  @JsonProperty("pmsSdkOrchestrationHandlerPoolConfig") private ThreadPoolConfig pmsSdkOrchestrationHandlerPoolConfig;
  @JsonProperty("pmsPlanCreatorServicePoolConfig") private ThreadPoolConfig pmsPlanCreatorServicePoolConfig;
  @JsonProperty("asyncDelegateResponseConsumption") private ThreadPoolConfig asyncDelegateResponseConsumption;
  @JsonProperty("segmentConfiguration") @ConfigSecret private SegmentConfiguration segmentConfiguration;
  @JsonProperty("redisLockConfig") private RedisConfig redisLockConfig;
  @JsonProperty("distributedLockImplementation") private DistributedLockImplementation distributedLockImplementation;
  @JsonProperty("pmsSdkExecutionPoolConfig") private ThreadPoolConfig pmsSdkExecutionPoolConfig;
  @JsonProperty("cfClientConfig") @ConfigSecret private CfClientConfig cfClientConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;
  @JsonProperty("streamPerServiceConfiguration") private boolean streamPerServiceConfiguration;
  @JsonProperty("asyncResourceCleanupPool") private ThreadPoolConfig asyncResourceCleanupPool;
  @JsonProperty("enableAsyncResourceCleanup") private boolean enableAsyncResourceCleanup;
  @JsonProperty("base") private String base;
  @JsonProperty("notifyResponseIterator") private DynamicIteratorConfig notifyResponseRedisConfig;
  @JsonProperty("delegateResponseTaskExecutionPoolConfig")
  private ThreadPoolConfig delegateResponseTaskExecutionPoolConfig;
  private boolean enableDelegateResponseCleanupIterator;
  private boolean enableWaitNotifyEngineOptimisation;
  private String ngManagerServiceSecret;
  private LogServiceConfig logServiceConfig;
  private TIServiceConfig tiServiceConfig;
  private CacheServiceConfig cacheServiceConfig;
  private STOServiceConfig stoServiceConfig;
  private QwietServiceConfig qwietServiceConfig;
  private TicketServiceConfig ticketServiceConfig;
  private HSAServiceConfig hsaServiceConfig;
  private SSCAServiceConfig sscaServiceConfig;
  private HarnessFulcioServiceConfig harnessFulcioServiceConfig;
  private IACMServiceConfig iacmServiceConfig;
  private CoverageServiceConfig coverageServiceConfig;
  private AnnotationsConfig annotationsConfig;

  private String managerServiceSecret;
  private String jwtAuthSecret;
  private String jwtIdentityServiceSecret;
  private String jwtDataHandlerSecret;
  private boolean enableAuth;
  private String managerTarget;
  private String managerAuthority;
  private CIExecutionServiceConfig ciExecutionServiceConfig;
  private ServiceHttpClientConfig opaClientConfig;
  private String policyManagerSecret;

  @JsonProperty("cvngClientConfig") private ServiceHttpClientConfig cvngClientConfig;
  private String cvngServiceSecret;

  @JsonProperty("aiTestAutomationClientConfig") private ServiceHttpClientConfig aiTestAutomationClientConfig;
  @JsonProperty("aitGcpClientConfig") private ServiceHttpClientConfig aitGcpClientConfig;
  private String aiTestAutomationServiceSecret;

  @JsonProperty("logStreamingExecutorPoolConfig") private ThreadPoolConfig logStreamingExecutorPoolConfig;

  @JsonProperty("pmsSdkGrpcServerConfig") private GrpcServerConfig pmsSdkGrpcServerConfig;
  @JsonProperty("pmsGrpcClientConfig") private GrpcClientConfig pmsGrpcClientConfig;
  @JsonProperty("shouldConfigureWithPMS") private Boolean shouldConfigureWithPMS;
  @JsonProperty("shouldDeregisterWithPMS") private Boolean shouldDeregisterWithPMS;
  @JsonProperty("enableDashboardTimescale") private Boolean enableDashboardTimescale;
  @JsonProperty("apiUrl") private String apiUrl;
  @JsonProperty("hostname") String hostname;
  @JsonProperty("basePathPrefix") String basePathPrefix;
  @JsonProperty(value = "enableOpentelemetry") private Boolean enableOpentelemetry;
  @JsonProperty(value = "enableLoopDetection") private Boolean enableLoopDetection;
  @JsonProperty(value = "loopDetectionThreshold") private int loopDetectionThreshold;
  @JsonProperty("enableTelemetry") private Boolean enableTelemetry;
  @JsonProperty("enableQueue") private Boolean enableQueue;
  @JsonProperty("harnessCodeGitUrl") private String harnessCodeGitUrl;
  @JsonProperty("llmGatewayBaseUrl") private String llmGatewayBaseUrl;
  private boolean shouldDeployWithGitSync;
  @JsonProperty("gitSdkConfiguration") private GitSdkConfiguration gitSdkConfiguration;
  @JsonProperty("pipelineSdkRedisEventsConfig") private PipelineSdkRedisEventsConfig pipelineSdkRedisEventsConfig;
  @JsonProperty("kafkaModuleConfig") @ConfigSecret KafkaModuleConfig kafkaModuleConfig;

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(klazz
            -> StringUtils.startsWithAny(klazz.getPackage().getName(), BASE_PACKAGE, CI_API_PACKAGE,
                NG_PIPELINE_PACKAGE, ENFORCEMENT_CLIENT_PACKAGE))
        .collect(Collectors.toSet());
  }

  public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration = new SwaggerBundleConfiguration();
    String resourcePackage = String.join(",", getUniquePackages(HARNESS_RESOURCE_CLASSES));
    defaultSwaggerBundleConfiguration.setResourcePackage(resourcePackage);
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost(
        "localhost"); // TODO, we should set the appropriate host here ex: qa.harness.io etc
    defaultSwaggerBundleConfiguration.setTitle("CI API Reference");
    defaultSwaggerBundleConfiguration.setVersion("2.0");

    return Optional.ofNullable(swaggerBundleConfiguration).orElse(defaultSwaggerBundleConfiguration);
  }

  @Override
  public AssetsConfiguration getAssetsConfiguration() {
    return assetsConfiguration;
  }

  public static Collection<Class<?>> getOAS3ResourceClassesOnly() {
    return HARNESS_RESOURCE_CLASSES.stream().filter(x -> x.isAnnotationPresent(Tag.class)).collect(Collectors.toList());
  }

  public static Set<String> getOAS3ResourceClassNames() {
    return getOAS3ResourceClassesOnly().stream().map(Class::getCanonicalName).collect(toSet());
  }

  private static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }

  @JsonIgnore
  public OpenAPIConfiguration getOasConfig() {
    OpenAPI oas = new OpenAPI();
    Info info = new Info()
                    .title("CI Manager API Reference")
                    .description("This is the Open Api Spec 3 for the CI Manager. This is under active development. "
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
      log.error("failed to set baseurl for server, {}/{}", hostname, basePathPrefix);
    }
    Set<String> resourceClasses = getOAS3ResourceClassNames();
    return new SwaggerConfiguration()
        .openAPI(oas)
        .prettyPrint(true)
        .resourceClasses(resourceClasses)
        .scannerClass("io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (harnessCIMongo != null) {
      dbAliases.add(harnessCIMongo.getAliasDBName());
    }
    if (harnessMongo != null) {
      dbAliases.add(harnessMongo.getAliasDBName());
    }
    return dbAliases;
  }
}

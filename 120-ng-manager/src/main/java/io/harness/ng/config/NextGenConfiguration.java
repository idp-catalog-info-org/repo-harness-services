/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.config;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;

import io.harness.AccessControlClientConfiguration;
import io.harness.Microservice;
import io.harness.NgIteratorsConfig;
import io.harness.accesscontrol.AccessControlAdminClientConfiguration;
import io.harness.account.AccountConfig;
import io.harness.alloydb.AlloyDBConfig;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.retrypolicy.AwsSdkDefaultBackOffStrategyConfiguration;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.cache.CacheConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStagePlanCreationInfoThreadPoolConfiguration;
import io.harness.cdng.plugininfoproviders.PluginExecutionConfig;
import io.harness.cf.CfClientConfig;
import io.harness.config.StripeConfig;
import io.harness.connector.oidc.swagger.OidcSwaggerListener;
import io.harness.connector.validator.ConnectorTestConfig;
import io.harness.dropwizard.bundles.swagger.SwaggerBundleConfiguration;
import io.harness.enforcement.client.servicedependencies.EnforcementClientConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.ff.FeatureFlagConfig;
import io.harness.file.FileServiceConfiguration;
import io.harness.fme.FmeClientConfiguration;
import io.harness.gitops.GitopsResourceClientConfig;
import io.harness.gitsync.GitSdkConfiguration;
import io.harness.gitsync.configurations.GitServiceConfiguration;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.harnessid.client.HarnessIdServiceConfig;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.iterator.IteratorExecutionHandler.DynamicIteratorConfig;
import io.harness.iterator.config.InstanceSyncIteratorConfig;
import io.harness.kafka.KafkaModuleConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.mongo.MongoConfig;
import io.harness.ng.BaseUrls;
import io.harness.ng.core.beans.GlobalTemplatesConfig;
import io.harness.ng.core.beans.InstanceSyncPerpetualTaskConfig;
import io.harness.ng.core.environment.EnvironmentGitXThreadConfiguration;
import io.harness.ng.core.metrics.ProjectEntityMigrationMetricsConfig;
import io.harness.ng.core.metrics.ProjectMovementTimescaleDbMigrationMetricsConfig;
import io.harness.ng.core.migration.DeDuplicateUserGroupsConfig;
import io.harness.ng.core.migration.OrphanUserGroupsCleanupConfig;
import io.harness.ng.core.service.ServiceGitXThreadConfiguration;
import io.harness.ng.gitops.config.CdcKafkaConfig;
import io.harness.ng.iro.config.IRConfig;
import io.harness.ng.overview.config.DeploymentCountBQConfig;
import io.harness.ng.privateconnectivity.config.PrivateConnectivityOrgConfig;
import io.harness.ng.support.client.CannyConfig;
import io.harness.ng.webhook.WebhookSecretsConfig;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.opaclient.OpaServiceConfiguration;
import io.harness.outbox.OutboxPollConfiguration;
import io.harness.pms.redisConsumer.DebeziumConsumersConfig;
import io.harness.pms.sdk.core.PipelineSdkRedisEventsConfig;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.CEAwsServiceEndpointConfig;
import io.harness.remote.CEAwsSetupConfig;
import io.harness.remote.CEAzureSetupConfig;
import io.harness.remote.CEGcpSetupConfig;
import io.harness.remote.CEProxyConfig;
import io.harness.remote.FRPSTunnelConfig;
import io.harness.remote.NextGenConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.resourcegroupclient.remote.ResourceGroupClientConfig;
import io.harness.secret.ConfigSecret;
import io.harness.secret.SecretsConfiguration;
import io.harness.signup.SignupDomainDenylistConfiguration;
import io.harness.signup.clients.ClearBitClientConfig;
import io.harness.signup.notification.SignupNotificationConfiguration;
import io.harness.subscription.SubscriptionConfig;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.threading.ThreadPoolConfig;
import io.harness.timescaledb.TimeScaleDBConfig;

import software.wings.security.authentication.oauth.BitbucketConfig;
import software.wings.security.authentication.oauth.ConfluenceConfig;
import software.wings.security.authentication.oauth.GitlabConfig;
import software.wings.security.authentication.oauth.GoogleChatConfig;
import software.wings.security.authentication.oauth.MsTeamsConfig;
import software.wings.security.authentication.oauth.ProviderConfig;
import software.wings.security.authentication.oauth.SlackConfig;
import software.wings.security.authentication.oauth.ZoomConfig;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.classic.Level;
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
import io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigurationProperties;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Getter
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class NextGenConfiguration extends Configuration {
  public static final String SERVICE_ID = "ng-manager";
  public static final String CORE_PACKAGE = "io.harness.ng.core.remote";
  public static final String INVITE_PACKAGE = "io.harness.ng.core.invites.remote";
  public static final String CONNECTOR_PACKAGE = "io.harness.connector.apis.resource";
  public static final String GITOPS_PROVIDER_RESOURCE_PACKAGE = "io.harness.gitopsprovider.resource";
  public static final String GIT_SYNC_PACKAGE = "io.harness.gitsync";
  public static final String CDNG_RESOURCES_PACKAGE = "io.harness.cdng";
  public static final String OVERLAY_INPUT_SET_RESOURCE_PACKAGE = "io.harness.ngpipeline";
  public static final String YAML_PACKAGE = "io.harness.yaml";
  public static final String FILTER_PACKAGE = "io.harness.filter";
  public static final String SIGNUP_PACKAGE = "io.harness.signup";
  public static final String MOCKSERVER_PACKAGE = "io.harness.ng.core.acl.mockserver";
  public static final String ACCOUNT_PACKAGE = "io.harness.account.resource";
  public static final String LICENSE_PACKAGE = "io.harness.licensing.api.resource";
  public static final String SUBSCRIPTION_PACKAGE = "io.harness.subscription.resource";
  public static final String CREDIT_PACKAGE = "io.harness.credit.resource";
  public static final String POLLING_PACKAGE = "io.harness.ng.webhook.polling";
  public static final String ENFORCEMENT_PACKAGE = "io.harness.enforcement.resource";
  public static final String ENFORCEMENT_CLIENT_PACKAGE = "io.harness.enforcement.client.resources";
  public static final String ARTIFACTS_PACKAGE = "io.harness.ng.core.artifacts.resources";
  public static final String AUTHENTICATION_SETTINGS_PACKAGE = "io.harness.ng.authenticationsettings.resources";
  public static final String SERVICE_PACKAGE = "io.harness.ng.core.service.resources";
  public static final String AIAGENT_PACKAGE = "io.harness.ng.core.aiagent.resources";
  public static final String CUSTOM_DEPLOYMENT_PACKAGE = "io.harness.ng.core.customDeployment.resources";
  public static final String TAS_PACKAGE = "io.harness.ng.core.tas.resources";
  public static final String VARIABLE_RESOURCE_PACKAGE = "io.harness.ng.core.variable.resources";
  public static final String CD_OVERVIEW_PACKAGE = "io.harness.ng.overview.resource";
  public static final String ROLLBACK_PACKAGE = "io.harness.ng.rollback";
  public static final String ACTIVITY_HISTORY_PACKAGE = "io.harness.ng.core.activityhistory.resource";
  public static final String SERVICE_ACCOUNTS_PACKAGE = "io.harness.ng.serviceaccounts.resource";
  public static final String BUCKETS_PACKAGE = "io.harness.ng.core.buckets.resources";
  public static final String CLUSTER_GCP_PACKAGE = "io.harness.ng.core.k8s.cluster.resources.gcp";
  public static final String CLUSTER_RANCHER_PACKAGE = "io.harness.ng.core.k8s.cluster.resources.rancher";
  public static final String WEBHOOK_PACKAGE = "io.harness.ng.webhook.resources";
  public static final String ENVIRONMENT_PACKAGE = "io.harness.ng.core.environment.resources";
  public static final String SERVICE_OVERRIDES_PACKAGE = "io.harness.ng.core.serviceoverrides.resources";
  public static final String PROVIDERS_PACKAGE = "io.harness.ng.core.provider.resources";
  public static final String USERPROFILE_PACKAGE = "io.harness.ng.userprofile.resource";
  public static final String USER_PACKAGE = "io.harness.ng.core.user.remote";
  public static final String JIRA_PACKAGE = "io.harness.ng.jira.resources";
  public static final String EXECUTION_PACKAGE = "io.harness.ng.executions.resources";
  public static final String ENTITYSETUP_PACKAGE = "io.harness.ng.core.entitysetupusage.resource";
  public static final String DELEGATE_PACKAGE = "io.harness.ng.core.delegate.resources";
  public static final String AGENT_PACKAGE = "io.harness.ng.core.agent.resources";
  public static final String ACCESS_CONTROL_PACKAGE = "io.harness.ng.accesscontrol.resources";
  public static final String FEEDBACK_PACKAGE = "io.harness.ng.feedback.resources";
  public static final String INSTANCE_SYNC_PACKAGE = "io.harness.ng.instancesync.resources";
  public static final String INSTANCE_NG_PACKAGE = "io.harness.ng.instance";
  public static final String SMTP_NG_RESOURCE = "io.harness.ng.core.smtp.resources";
  public static final String SERVICENOW_PACKAGE = "io.harness.ng.servicenow.resources";
  public static final String SCIM_NG_RESOURCE = "io.harness.ng.scim.resource";
  public static final String LICENSING_USAGE_PACKAGE = "io.harness.licensing.usage.resources";
  public static final String ACCOUNT_SETTING_PACKAGE = "io.harness.ng.core.accountsetting.resources";
  public static final String ENV_GROUP_RESOURCE = "io.harness.ng.core.envGroup.resource";
  public static final String NG_GLOBAL_KMS_RESOURCE_PACKAGE = "io.harness.ng.core.globalkms.resource";
  public static final String AZURE_RESOURCES_PACKAGE = "io.harness.ng.core.resources.azure";
  public static final String NG_TRIAL_SIGNUP_PACKAGE = "io.harness.ng.trialsignup";
  public static final String AWS_PACKAGE = "io.harness.ng.core.aws.resources";
  public static final String FILE_STORE_RESOURCE_PACKAGE = "io.harness.filestore.resource";
  public static final String GITOPS_RESOURCE_PACKAGE = "io.harness.ng.gitops.resource";
  public static final String INFRA_RESOURCE_PACKAGE = "io.harness.ng.core.infrastructure.resource";
  public static final String OAUTH_RESOURCE_PACKAGE = "io.harness.ng.oauth";
  public static final String LDAP_PACKAGE = "io.harness.ldap.resource";
  public static final String CHAOS_PACKAGE = "io.harness.ng.chaos";
  public static final String LOAD_TEST_PACKAGE = "io.harness.ng.loadtest";
  public static final String STO_PACKAGE = "io.harness.ng.sto";
  public static final String AI_TEST_AUTOMATION_PACKAGE = "io.harness.ng.aitestautomation";
  public static final String SERVICE_DISCOVERY_PACKAGE = "io.harness.ng.servicediscovery";
  public static final String SUPPORT_PACKAGE = "io.harness.ng.support.resource";
  public static final String ONBOARDING_RESOURCE_PACKAGE = "io.harness.ng.core.onboarding.resources";
  public static final String IAC_PACKAGE = "io.harness.ng.iac";
  public static final String HARNESS_CODE_RESOURCE_PACKAGE = "io.harness.ng.code.resources";
  public static final String HARNESS_SMP_RESOURCE_PACKAGE = "io.harness.ng.smp.resources";

  public static final String IP_ALLOWLIST_PACKAGE = "io.harness.ipallowlist.resource";
  public static final String NGSUBSCRIPTIONS_PACKAGE = "io.harness.ngsubscriptions.resource";

  public static final String K8S_RELEASE_DETAILS_PACKAGE = "io.harness.ng.core.releasedetails.resources";
  public static final String FAVORITES_PACKAGE = "io.harness.favorites.remote";
  public static final String EULA_PACKAGE = "io.harness.eula.resource";
  public static final String SETTINGS_RESOURCE_PACKAGE = "io.harness.ngsettings.remote";
  public static final String NG_CERTIFICATES_RESOURCE_PACKAGE = "io.harness.ngcertificates.remote";
  public static final String NG_BANNERS_RESOURCE_PACKAGE = "io.harness.ngbanners.remote";
  public static final String BRANDING_RESOURCE_PACKAGE = "io.harness.branding.remote";
  public static final String FREEZE_RESOURCE_PACKAGE = "io.harness.ng.freeze.resource";
  public static final String MANIFEST_RESOURCE_PACKAGE = "io.harness.ng.core.manifests.resources";
  private static final String REFRESH_RESOURCE_PACKAGE = "io.harness.ng.core.refresh";
  private static final String DEPLOYMENT_STAGE_PACKAGE = "io.harness.ng.core.deploymentstage";
  private static final String SERVICE_ENV_MIGRATION_RESOURCE_PACKAGE =
      "io.harness.ng.core.migration.serviceenvmigrationv2.resources";
  private static final String CUSTOM_DEPLOYMENT_METADATA_MIGRATION_PACKAGE =
      "io.harness.ng.core.migration.customdeployment";
  private static final String HARNESS_ARTIFACT_INSTANCE_RESOURCE_PACKAGE =
      "io.harness.ng.harnessartifactinstance.resources";
  private static final String GCP_PACKAGE = "io.harness.ng.core.gcp.resources";
  private static final String MODULEVERSION_RESOURCE_PACKAGE = "io.harness.ng.moduleversion.resource";
  private static final String TERRAFORM_CLOUD_RESOURCE_PACKAGE = "io.harness.ng.core.terraformcloud.resources";
  private static final String EOL_BANNER_RESOURCE_PACKAGE = "io.harness.ng.core.eolbanner.resources";
  private static final String TERRAFORM_RESOURCE_PACKAGE = "io.harness.ng.core.terraform.resources";
  private static final String TERRAGRUNT_RESOURCE_PACKAGE = "io.harness.ng.core.terragrunt.resources";
  private static final String GITX_WEBHOOKS_PACKAGE = "io.harness.ng.gitxwebhook";
  private static final String SCM_PACKAGE = "io.harness.ng.scm";
  private static final String WEBHOOKS_PACKAGE = "io.harness.ng.webhook";
  public static final String TUNNEL_RESOURCE_PACKAGE = "io.harness.ng.tunnel.resources";
  public static final String PRIVATE_CONNECTIVITY_RESOURCE_PACKAGE = "io.harness.ng.privateconnectivity.resources";
  public static final String NG_LDAP_RESOURCE_PACKAGE = "io.harness.ldap.remote";

  private static final String OIDC_CORE_RESOURCE = "io.harness.ng.core.oidc";
  private static final String IRO_PACKAGE = "io.harness.ng.iro";

  public static final String MONITORING_MANAGER_PACKAGE = "io.harness.ng.monitoringmanager";

  public static final String IRO_MANAGER_PACKAGE = "io.harness.ng.iro";
  public static final String CGI_CONFIG_PACKAGE = "io.harness.ng.runner.cgi";
  public static final String PLUGIN_CONFIG_PACKAGE = "io.harness.ng.runner.plugin";
  public static final String OIDC_AUTH_PROVIDER_PACKAGE = "io.harness.oidc_auth.remote";
  public static final String DEPLOYABLE_PACKAGE = "io.harness.ng.core.deployable.resources";
  public static final String SALESFORCE_COMPARISON_PAIR_PACKAGE = "io.harness.ng.core.sfcomparisonpair.resources";
  public static final String SALESFORCE_CHANGESET_PACKAGE = "io.harness.ng.core.sfchangeset.resources";
  public static final String SALESFORCE_EXECUTION_PACKAGE = "io.harness.ng.core.sfexecution.resources";
  public static final String SALESFORCE_DEFAULT_PIPELINES_PACKAGE = "io.harness.ng.core.salesforce.defaultpipelines";

  public static final Collection<Class<?>> HARNESS_RESOURCE_CLASSES = getResourceClasses();

  @JsonProperty("swagger") private SwaggerBundleConfiguration swaggerBundleConfiguration;
  @Setter @JsonProperty("mongo") @ConfigSecret private MongoConfig mongoConfig;
  @JsonProperty("commonPoolConfig") private ThreadPoolConfig commonPoolConfig;
  @JsonProperty("disableResourceValidation") private boolean disableResourceValidation;
  @JsonProperty("pmsSdkExecutionPoolConfig") private ThreadPoolConfig pmsSdkExecutionPoolConfig;
  @JsonProperty("pmsSdkOrchestrationEventPoolConfig") private ThreadPoolConfig pmsSdkOrchestrationEventPoolConfig;
  @JsonProperty("pmsSdkOrchestrationHandlerPoolConfig") private ThreadPoolConfig pmsSdkOrchestrationHandlerPoolConfig;
  @JsonProperty("pmsSdkBackfillOrchestrationEventPoolConfig")
  private ThreadPoolConfig pmsSdkBackfillOrchestrationEventPoolConfig;
  @JsonProperty("backfillOrchestrationEventHandlerPoolConfig")
  private ThreadPoolConfig backfillOrchestrationEventHandlerPoolConfig;
  @JsonProperty("allowedOrigins") private List<String> allowedOrigins = Lists.newArrayList();
  @JsonProperty("managerClientConfig") private ServiceHttpClientConfig managerClientConfig;
  @JsonProperty("grpcClient") private GrpcClientConfig grpcClientConfig;
  @JsonProperty("grpcServer") private GrpcServerConfig grpcServerConfig;
  @JsonProperty("nextGen") @ConfigSecret private NextGenConfig nextGenConfig;
  @JsonProperty("ciDefaultEntityConfiguration")
  @ConfigSecret
  private CiDefaultEntityConfiguration ciDefaultEntityConfiguration;
  @JsonProperty("ngManagerClientConfig") private ServiceHttpClientConfig ngManagerClientConfig;
  @JsonProperty("rhsClientConfig") private ServiceHttpClientConfig rhsClientConfig;
  @JsonProperty("rhsEnabled") private boolean rhsEnabled;
  @JsonProperty("rhsServiceSecret") @ConfigSecret private String rhsServiceSecret;
  @JsonProperty("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceClientConfig;
  @JsonProperty("auditClientConfig") private ServiceHttpClientConfig auditClientConfig;
  @JsonProperty("ceNextGenClientConfig") private ServiceHttpClientConfig ceNextGenClientConfig;
  @JsonProperty("cvngClientConfig") private ServiceHttpClientConfig cvngClientConfig;
  @JsonProperty("stoCoreClientConfig") private ServiceHttpClientConfig stoCoreClientConfig;
  @JsonProperty("lightwingClientConfig") private ServiceHttpClientConfig lightwingClientConfig;
  @JsonProperty("templateServiceClientConfig") private ServiceHttpClientConfig templateServiceClientConfig;
  @JsonProperty("chaosServiceClientConfig") private ServiceHttpClientConfig chaosServiceClientConfig;
  @JsonProperty("loadTestServiceClientConfig") private ServiceHttpClientConfig loadTestServiceClientConfig;
  @JsonProperty("aiTestAutomationClientConfig") private ServiceHttpClientConfig aiTestAutomationClientConfig;
  @JsonProperty("aitGcpClientConfig") private ServiceHttpClientConfig aitGcpClientConfig;
  @JsonProperty("seiServiceClientConfig") private ServiceHttpClientConfig seiServiceClientConfig;
  @JsonProperty("dbOpsServiceClientConfig") private ServiceHttpClientConfig dbOpsServiceClientConfig;
  @JsonProperty("aiMLOpsServiceClientConfig") private ServiceHttpClientConfig aiMLOpsServiceClientConfig;
  @JsonProperty("harnessRegistryServiceClientConfig")
  private ServiceHttpClientConfig harnessRegistryServiceClientConfig;
  @JsonProperty("serviceDiscoveryServiceClientConfig")
  private ServiceHttpClientConfig serviceDiscoveryServiceClientConfig;
  @JsonProperty("monitoringManagerServiceClientConfig")
  private ServiceHttpClientConfig monitoringManagerServiceClientConfig;
  @JsonProperty("iroManagerServiceClientConfig") private ServiceHttpClientConfig iroManagerServiceClientConfig;
  @JsonProperty("idpServiceClientConfig") private ServiceHttpClientConfig idpServiceClientConfig;
  @JsonProperty("eventsFramework") @ConfigSecret private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("redisLockConfig") @ConfigSecret private RedisConfig redisLockConfig;
  @JsonProperty(value = "enableAuth", defaultValue = "true") private boolean enableAuth;
  @JsonProperty("sendEventToDS") private boolean sendEventToDS;
  @JsonProperty(value = "ngIteratorsConfig") private NgIteratorsConfig ngIteratorsConfig;
  @JsonProperty("ceAwsSetupConfig") @ConfigSecret @Deprecated private CEAwsSetupConfig ceAwsSetupConfig;
  @JsonProperty("ceAzureSetupConfig") @ConfigSecret private CEAzureSetupConfig ceAzureSetupConfig;
  @JsonProperty("ceGcpSetupConfig") private CEGcpSetupConfig ceGcpSetupConfig;
  @JsonProperty(value = "enableAudit") private boolean enableAudit;
  @JsonProperty(value = "ngAuthUIEnabled") private boolean isNgAuthUIEnabled;
  @JsonProperty("pmsSdkGrpcServerConfig") private GrpcServerConfig pmsSdkGrpcServerConfig;
  @JsonProperty("pmsGrpcClientConfig") private GrpcClientConfig pmsGrpcClientConfig;
  @JsonProperty("shouldConfigureWithPMS") private Boolean shouldConfigureWithPMS;
  @JsonProperty("deploymentCountBQConfig") private DeploymentCountBQConfig deploymentCountBQConfig;
  @JsonProperty("accessControlClient")
  @ConfigSecret
  private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("accountConfig") private AccountConfig accountConfig;
  @JsonProperty("logStreamingServiceConfig")
  @ConfigSecret
  private LogStreamingServiceConfiguration logStreamingServiceConfig;
  @JsonProperty("cannyApiConfig") private CannyConfig cannyConfig;
  @JsonProperty("clearbitApiConfig") private ClearBitClientConfig clearBitClientConfig;
  private OpaServiceConfiguration opaServerConfig;
  private String policyManagerSecret;
  private ServiceHttpClientConfig opaClientConfig;
  @JsonProperty("gitSyncServerConfig") private GrpcServerConfig gitSyncGrpcServerConfig;
  @JsonProperty("gitGrpcClientConfigs") private Map<Microservice, GrpcClientConfig> gitGrpcClientConfigs;
  @JsonProperty("shouldDeployWithGitSync") private Boolean shouldDeployWithGitSync;
  @JsonProperty("notificationClient")
  @ConfigSecret
  private NotificationClientConfiguration notificationClientConfiguration;

  @JsonProperty("resourceGroupClientConfig") @ConfigSecret private ResourceGroupClientConfig resourceGroupClientConfig;
  @JsonProperty("accessControlAdminClient")
  @ConfigSecret
  private AccessControlAdminClientConfiguration accessControlAdminClientConfiguration;
  @JsonProperty("outboxPollConfig") private OutboxPollConfiguration outboxPollConfig;
  @JsonProperty("segmentConfiguration") @ConfigSecret private SegmentConfiguration segmentConfiguration;
  @JsonProperty("subscriptionConfig") @ConfigSecret private SubscriptionConfig subscriptionConfig;
  @JsonProperty("gitSdkConfiguration") private GitSdkConfiguration gitSdkConfiguration;
  @JsonProperty("fileServiceConfiguration") private FileServiceConfiguration fileServiceConfiguration;
  @JsonProperty("baseUrls") private BaseUrls baseUrls;
  @JsonProperty("cfClientConfig") @ConfigSecret private CfClientConfig cfClientConfig;
  @JsonProperty("fmeClientConfig") private FmeClientConfiguration fmeClientConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;
  @JsonProperty("cdLicensingV2CacheExpiryInHours") private long cdLicensingV2CacheExpiryInHours;
  @JsonProperty("timescaledb") @ConfigSecret private TimeScaleDBConfig timeScaleDBConfig;
  @JsonProperty("alloydb") @ConfigSecret private AlloyDBConfig alloyDBConfig;
  @JsonProperty("ngManagerServiceHttpClientConfig") private ServiceHttpClientConfig ngManagerServiceHttpClientConfig;
  @JsonProperty("secondaryTimescaledb") @ConfigSecret private TimeScaleDBConfig secondaryTimeScaleDBConfig;
  @JsonProperty("enableDashboardTimescale") private Boolean enableDashboardTimescale;
  @JsonProperty("enablePaginatedQueryOnTimescale") private Boolean enablePaginatedQueryOnTimescale;
  @JsonProperty("distributedLockImplementation") private DistributedLockImplementation distributedLockImplementation;
  @JsonProperty("exportMetricsToStackDriver") private boolean exportMetricsToStackDriver;
  @JsonProperty("signupNotificationConfiguration")
  private SignupNotificationConfiguration signupNotificationConfiguration;
  @JsonProperty("cacheConfig") private CacheConfig cacheConfig;
  @JsonProperty(value = "scopeAccessCheckEnabled", defaultValue = "false") private boolean isScopeAccessCheckEnabled;
  @JsonProperty(value = "signupTargetEnv") private String signupTargetEnv;
  @JsonProperty(value = "delegateStatusEndpoint") private String delegateStatusEndpoint;
  @JsonProperty(value = "gitlabConfig") private GitlabConfig gitlabConfig;
  @JsonProperty(value = "bitbucketConfig") private BitbucketConfig bitbucketConfig;
  @JsonProperty(value = "providerConfig") private ProviderConfig providerConfig;
  @JsonProperty(value = "msTeamsConfig") private MsTeamsConfig msTeamsConfig;
  @JsonProperty(value = "slackConfig") private SlackConfig slackConfig;
  @JsonProperty(value = "zoomConfig") private ZoomConfig zoomConfig;
  @JsonProperty(value = "confluenceConfig") private ConfluenceConfig confluenceConfig;
  @JsonProperty(value = "googleChatConfig") private GoogleChatConfig googleChatConfig;
  @JsonProperty(value = "cloudCreditsHourlyRollUpJobSchedule") private long cloudCreditsHourlyRollUpJobSchedule;
  @JsonProperty(value = "cloudCreditsHourlyRollUpBatchSize") private long cloudCreditsHourlyRollUpBatchSize;
  @JsonProperty(value = "oauthRefreshFrequency") private long oauthRefreshFrequency;
  @JsonProperty(value = "oauthRefreshEnabled") private boolean oauthRefreshEnabled;
  @JsonProperty(value = "opaConnectivityEnabled") private boolean opaConnectivityEnabled;
  @JsonProperty("hostname") String hostname = "localhost";
  @JsonProperty("basePathPrefix") String basePathPrefix = "";
  @JsonProperty("enforcementClientConfiguration") EnforcementClientConfiguration enforcementClientConfiguration;
  @JsonProperty("ciManagerClientConfig") ServiceHttpClientConfig ciManagerClientConfig;
  @JsonProperty("secretsConfiguration") private SecretsConfiguration secretsConfiguration;
  @JsonProperty("pmsPlanCreatorServicePoolConfig") private ThreadPoolConfig pmsPlanCreatorServicePoolConfig;
  @JsonProperty("ffServerClientConfig") ServiceHttpClientConfig ffServerClientConfig;
  @JsonProperty("iacmClientConfig") IACMServiceConfig iacmClientConfig;
  @ConfigSecret @JsonProperty("gitopsResourceClientConfig") GitopsResourceClientConfig gitopsResourceClientConfig;
  @JsonProperty("debeziumConsumersConfigs") DebeziumConsumersConfig debeziumConsumersConfigs;
  @JsonProperty("cdcKafka") CdcKafkaConfig cdcKafkaConfig;
  @JsonProperty(value = "cdTsDbRetentionPeriodMonths") private String cdTsDbRetentionPeriodMonths;
  @JsonProperty(value = "enableOpentelemetry") private Boolean enableOpentelemetry;
  @JsonProperty(value = "enableLoopDetection") private Boolean enableLoopDetection;
  @JsonProperty(value = "loopDetectionThreshold") private int loopDetectionThreshold;
  @JsonProperty("gitService") private GitServiceConfiguration gitServiceConfiguration;
  @JsonProperty(value = "disableFreezeNotificationTemplate") private boolean disableFreezeNotificationTemplate;
  @JsonProperty(value = "disableChaosNotificationTemplate") private boolean disableChaosNotificationTemplate;
  @JsonProperty(value = "disableIRNotificationTemplate") private boolean disableIRNotificationTemplate;
  @JsonProperty(value = "disableStoNotificationTemplate") private boolean disableStoNotificationTemplate;
  @JsonProperty(value = "disableHarNotificationTemplate") private boolean disableHarNotificationTemplate;
  @JsonProperty(value = "disableAitNotificationTemplate") private boolean disableAitNotificationTemplate;
  @JsonProperty(value = "disableServiceAccountNotificationTemplate")
  private boolean disableServiceAccountNotificationTemplate;
  @JsonProperty("dashboardExecutorServiceConfig") private ThreadPoolConfig dashboardExecutorServiceConfig;
  @JsonProperty("gitOpsStepExecutorServiceConfig") private ThreadPoolConfig gitOpsStepExecutorServiceConfig;
  @JsonProperty("batchSecretsExecutorServiceConfig") private ThreadPoolConfig batchSecretsExecutorServiceConfig;
  @JsonProperty(value = "pluginExecutionConfig") private PluginExecutionConfig pluginExecutionConfig;
  @JsonProperty("signupDomainDenylistConfig")
  private SignupDomainDenylistConfiguration signupDomainDenylistConfiguration;
  @JsonProperty("queueServiceClientConfig") private QueueServiceClientConfig queueServiceClientConfig;
  @JsonProperty("webhookBranchHookEventHsqsDequeueConfig")
  private HsqsDequeueConfig webhookBranchHookEventHsqsDequeueConfig;
  @JsonProperty("gitXWebhookEventQueueConfig") private HsqsDequeueConfig gitXWebhookEventQueueConfig;
  @JsonProperty("gitXWebhookEventValidationQueueConfig")
  private HsqsDequeueConfig gitXWebhookEventValidationQueueConfig;
  @JsonProperty("webhookPushEventHsqsDequeueConfig") private HsqsDequeueConfig webhookPushEventHsqsDequeueConfig;
  @JsonProperty("webhookGitXPushEventQueueConfig") private HsqsDequeueConfig webhookGitXPushEventQueueConfig;
  @JsonProperty("serviceStepMaxTimeout") private String serviceStepMaxTimeout;
  @JsonProperty("infraStepMaxTimeout") private String infraStepMaxTimeout;
  @JsonProperty("proxy") private CEProxyConfig ceProxyConfig;
  @JsonProperty("awsServiceEndpointUrls") private CEAwsServiceEndpointConfig ceAwsServiceEndpointConfig;
  private boolean useQueueServiceForWebhookTriggers;
  @JsonProperty("useQueueServiceForGitXWebhook") private boolean useQueueServiceForGitXWebhook;
  @JsonProperty("streamPerServiceConfiguration") private boolean streamPerServiceConfiguration;
  @JsonProperty("skipSdkMongoRegistration") private boolean skipSdkMongoRegistration;
  @JsonProperty("serviceGitXThreadConfig") private ServiceGitXThreadConfiguration serviceGitXThreadConfig;
  @JsonProperty("environmentGitXThreadConfig") private EnvironmentGitXThreadConfiguration environmentGitXThreadConfig;
  @JsonProperty("oidcConfigPath") private String oidcConfigPath;
  @JsonProperty("devOpsEssentialsConfigPath") private String devOpsEssentialsConfigPath;
  @JsonProperty("stripeConfig") private StripeConfig stripeConfig;
  @JsonProperty("deploymentStagePlanCreationInfoThreadConfig")
  private DeploymentStagePlanCreationInfoThreadPoolConfiguration deploymentStagePlanCreationInfoThreadPoolConfiguration;
  @JsonProperty("entityCleanupConfig") private EntityCleanupConfiguration entityCleanupConfiguration;
  @JsonProperty("connectorTestConfig") private ConnectorTestConfig connectorTestConfig;
  @JsonProperty("frpsTunnel") private FRPSTunnelConfig frpsTunnelConfig;
  @JsonProperty("publishAccountActivityMetrics") private boolean publishAccountActivityMetrics;
  @JsonProperty("deDuplicateUserGroupsJobConfig") private DeDuplicateUserGroupsConfig deDuplicateUserGroupsJobConfig;
  @JsonProperty("orphanUserGroupsCleanupJobConfig")
  private OrphanUserGroupsCleanupConfig orphanUserGroupsCleanupJobConfig;
  @JsonProperty("serviceUniqueIdBackfillJobConfig")
  private ServiceUniqueIdBackfillConfig serviceUniqueIdBackfillJobConfig;
  @JsonProperty("awsSdkDefaultBackOffStrategyConfiguration")
  private AwsSdkDefaultBackOffStrategyConfiguration awsSdkDefaultBackOffStrategyConfiguration;
  @JsonProperty("ldapGroupSyncPoolConfig") private ThreadPoolConfig ldapGroupSyncPoolConfig;
  @JsonProperty("opaGitxStatusPoolConfig") private ThreadPoolConfig opaGitxStatusPoolConfig;
  @JsonProperty("notifyResponseIterator") private DynamicIteratorConfig notifyResponseRedisConfig;
  private boolean enableDelegateResponseCleanupIterator;
  private boolean enableWaitNotifyEngineOptimisation;
  @JsonProperty("enableDefaultUserGroupsCreationJob") private boolean enableDefaultUserGroupsCreationJob;
  @JsonProperty("resilience4j.circuitbreaker")
  private CircuitBreakerConfigurationProperties circuitBreakerConfiguration;
  @JsonProperty("resilience4j.bulkhead") private BulkheadConfigurationProperties bulkheadConfiguration;
  @JsonProperty("cgiTaskConfigPath") private String cgiTaskConfigPath;
  @JsonProperty("taskBinaryConfigPath") private String taskBinaryConfigPath;
  @JsonProperty("globalTemplatesConfig") @ConfigSecret private GlobalTemplatesConfig globalTemplatesConfig;
  @JsonProperty("pipelineSdkRedisEventsConfig") private PipelineSdkRedisEventsConfig pipelineSdkRedisEventsConfig;
  @JsonProperty("instanceSyncPerpetualTaskConfig")
  private InstanceSyncPerpetualTaskConfig instanceSyncPerpetualTaskConfig;
  @JsonProperty("irConfig") private IRConfig irConfig;
  // [secondary-db]: Uncomment this and the corresponding config in yaml file if you want to connect to another database
  //  @JsonProperty("secondary-mongo") MongoConfig secondaryMongoConfig;
  @JsonProperty("webhookSecretsConfig") @ConfigSecret WebhookSecretsConfig webhookSecretsConfig;
  @JsonProperty("projectMovementEntityMigrationMetricsConfig")
  private ProjectEntityMigrationMetricsConfig projectMovementProjectEntityMigrationMetricsConfig;
  @JsonProperty("projectMovementTimescaleDbMigrationMetricsConfig")
  private ProjectMovementTimescaleDbMigrationMetricsConfig projectMovementTimescaleDbMigrationMetricsConfig;
  @JsonProperty("tokenExpirationConfig") private TokenExpirationConfig tokenExpirationConfig;
  @JsonProperty("scopedPermissionsBackfillConfig")
  private ScopedPermissionsBackfillConfig scopedPermissionsBackfillConfig;
  @JsonProperty("enableOrganizationBillingJob") private boolean enableOrganizationBillingJob;
  @JsonProperty("enableCDBillingJob") private boolean enableCDBillingJob;
  @JsonProperty("kafkaModuleConfig") @ConfigSecret KafkaModuleConfig kafkaModuleConfig;
  @JsonProperty("enableCdcMigrationByCreatingChangeEventsInMongo")
  private boolean enableCdcMigrationByCreatingChangeEventsInMongo;
  @JsonProperty("enableTsdbMigrationForParentUniqueId") private boolean enableTsdbMigrationForParentUniqueId;
  @JsonProperty("permissionCheckBatchSizeForConnectorListing") private int permissionCheckBatchSizeForConnectorListing;
  @JsonProperty("smpConfig") private SmpConfig smpConfig;
  @JsonProperty("instanceSyncIterator") private InstanceSyncIteratorConfig instanceSyncIteratorConfig;
  @JsonProperty("tokenExpiryAlertIterator") private TokenExpiryAlertIteratorConfig tokenExpiryAlertIteratorConfig;
  @JsonProperty("gitxWebhookEventProcessorExecutorServiceConfig")
  private ThreadPoolConfig gitXWebhookEventProcessorThreadPoolConfig;
  @JsonProperty("enableLicenseManager") private boolean enableLicenseManager;
  @JsonProperty("licenseManagerClientConfig") private ServiceHttpClientConfig licenseManagerClientConfig;
  @JsonProperty("autoProvisionLicenseConfig") private AutoProvisionLicenseConfig autoProvisionLicenseConfig;
  @JsonProperty("dailyAccountUsersJobInitialDelayInMinutes") private long dailyAccountUsersJobInitialDelayInMinutes = 1;
  @JsonProperty("totalAccountUsersJobInitialDelayInMinutes") private long totalAccountUsersJobInitialDelayInMinutes = 1;
  @JsonProperty("meshIdentity") private io.harness.security.mesh.MeshIdentityConfig meshIdentity;
  @JsonProperty("privateConnectivityOrgConfig")
  @ConfigSecret
  private PrivateConnectivityOrgConfig privateConnectivityOrgConfig;
  @JsonProperty("harnessIdClientConfig") @ConfigSecret private HarnessIdServiceConfig harnessIdClientConfig;

  public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
    final var defaultSwaggerBundleConfiguration = new SwaggerBundleConfiguration();
    String resourcePackage = String.join(",", getUniquePackages(HARNESS_RESOURCE_CLASSES));
    defaultSwaggerBundleConfiguration.setResourcePackage(resourcePackage);
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost(hostname);
    defaultSwaggerBundleConfiguration.setUriPrefix(basePathPrefix);
    defaultSwaggerBundleConfiguration.setTitle("CD NextGen API Reference");
    defaultSwaggerBundleConfiguration.setVersion("2.0");
    return Optional.ofNullable(swaggerBundleConfiguration).orElse(defaultSwaggerBundleConfiguration);
  }

  public static Collection<Class<?>> getResourceClasses() {
    List<Class<?>> resourceClasses =
        HarnessReflections.get()
            .getTypesAnnotatedWith(Path.class)
            .stream()
            .filter(klazz
                -> StringUtils.startsWithAny(klazz.getPackage().getName(), NextGenConfiguration.CORE_PACKAGE,
                    NextGenConfiguration.CONNECTOR_PACKAGE, NextGenConfiguration.GITOPS_PROVIDER_RESOURCE_PACKAGE,
                    NextGenConfiguration.GIT_SYNC_PACKAGE, NextGenConfiguration.CDNG_RESOURCES_PACKAGE,
                    NextGenConfiguration.OVERLAY_INPUT_SET_RESOURCE_PACKAGE, NextGenConfiguration.YAML_PACKAGE,
                    NextGenConfiguration.FILTER_PACKAGE, NextGenConfiguration.SIGNUP_PACKAGE,
                    NextGenConfiguration.MOCKSERVER_PACKAGE, NextGenConfiguration.ACCOUNT_PACKAGE,
                    NextGenConfiguration.LICENSE_PACKAGE, NextGenConfiguration.SUBSCRIPTION_PACKAGE,
                    NextGenConfiguration.CREDIT_PACKAGE, NextGenConfiguration.POLLING_PACKAGE,
                    NextGenConfiguration.ENFORCEMENT_PACKAGE, NextGenConfiguration.ENFORCEMENT_CLIENT_PACKAGE,
                    NextGenConfiguration.ARTIFACTS_PACKAGE, NextGenConfiguration.AUTHENTICATION_SETTINGS_PACKAGE,
                    NextGenConfiguration.CD_OVERVIEW_PACKAGE, NextGenConfiguration.ROLLBACK_PACKAGE,
                    NextGenConfiguration.ACTIVITY_HISTORY_PACKAGE, NextGenConfiguration.SERVICE_PACKAGE,
                    NextGenConfiguration.AIAGENT_PACKAGE, NextGenConfiguration.SERVICE_ACCOUNTS_PACKAGE,
                    NextGenConfiguration.BUCKETS_PACKAGE, NextGenConfiguration.CLUSTER_GCP_PACKAGE,
                    NextGenConfiguration.CLUSTER_RANCHER_PACKAGE, NextGenConfiguration.WEBHOOK_PACKAGE,
                    NextGenConfiguration.ENVIRONMENT_PACKAGE, NextGenConfiguration.USERPROFILE_PACKAGE,
                    NextGenConfiguration.JIRA_PACKAGE, NextGenConfiguration.EXECUTION_PACKAGE,
                    NextGenConfiguration.ENTITYSETUP_PACKAGE, NextGenConfiguration.DELEGATE_PACKAGE,
                    NextGenConfiguration.ACCESS_CONTROL_PACKAGE, NextGenConfiguration.FEEDBACK_PACKAGE,
                    NextGenConfiguration.INSTANCE_SYNC_PACKAGE, NextGenConfiguration.INVITE_PACKAGE,
                    NextGenConfiguration.USER_PACKAGE, NextGenConfiguration.INSTANCE_NG_PACKAGE,
                    NextGenConfiguration.LICENSING_USAGE_PACKAGE, NextGenConfiguration.SMTP_NG_RESOURCE,
                    NextGenConfiguration.SERVICENOW_PACKAGE, NextGenConfiguration.SCIM_NG_RESOURCE,
                    NextGenConfiguration.NG_GLOBAL_KMS_RESOURCE_PACKAGE, NextGenConfiguration.ACCOUNT_SETTING_PACKAGE,
                    NextGenConfiguration.ENV_GROUP_RESOURCE, NextGenConfiguration.AZURE_RESOURCES_PACKAGE,
                    NextGenConfiguration.NG_TRIAL_SIGNUP_PACKAGE, NextGenConfiguration.VARIABLE_RESOURCE_PACKAGE,
                    NextGenConfiguration.FILE_STORE_RESOURCE_PACKAGE, NextGenConfiguration.GITOPS_RESOURCE_PACKAGE,
                    NextGenConfiguration.INFRA_RESOURCE_PACKAGE, NextGenConfiguration.AWS_PACKAGE,
                    NextGenConfiguration.OAUTH_RESOURCE_PACKAGE, NextGenConfiguration.LDAP_PACKAGE,
                    NextGenConfiguration.CHAOS_PACKAGE, NextGenConfiguration.LOAD_TEST_PACKAGE,
                    NextGenConfiguration.SETTINGS_RESOURCE_PACKAGE, NextGenConfiguration.AGENT_PACKAGE,
                    NextGenConfiguration.CUSTOM_DEPLOYMENT_PACKAGE, NextGenConfiguration.FREEZE_RESOURCE_PACKAGE,
                    NextGenConfiguration.MODULEVERSION_RESOURCE_PACKAGE, NextGenConfiguration.REFRESH_RESOURCE_PACKAGE,
                    DEPLOYMENT_STAGE_PACKAGE, NextGenConfiguration.MANIFEST_RESOURCE_PACKAGE,
                    NextGenConfiguration.TAS_PACKAGE, NextGenConfiguration.SERVICE_ENV_MIGRATION_RESOURCE_PACKAGE,
                    NextGenConfiguration.CUSTOM_DEPLOYMENT_METADATA_MIGRATION_PACKAGE,
                    NextGenConfiguration.TERRAFORM_CLOUD_RESOURCE_PACKAGE, NextGenConfiguration.GCP_PACKAGE,
                    NextGenConfiguration.EOL_BANNER_RESOURCE_PACKAGE, NextGenConfiguration.TERRAFORM_RESOURCE_PACKAGE,
                    NextGenConfiguration.IP_ALLOWLIST_PACKAGE, NextGenConfiguration.SERVICE_OVERRIDES_PACKAGE,
                    NextGenConfiguration.FAVORITES_PACKAGE, NextGenConfiguration.SERVICE_DISCOVERY_PACKAGE,
                    NextGenConfiguration.SUPPORT_PACKAGE, NextGenConfiguration.ONBOARDING_RESOURCE_PACKAGE,
                    NextGenConfiguration.EULA_PACKAGE, NextGenConfiguration.TERRAGRUNT_RESOURCE_PACKAGE,
                    NextGenConfiguration.GITX_WEBHOOKS_PACKAGE, NextGenConfiguration.SCM_PACKAGE,
                    NextGenConfiguration.K8S_RELEASE_DETAILS_PACKAGE, NextGenConfiguration.OIDC_CORE_RESOURCE,
                    NextGenConfiguration.NG_CERTIFICATES_RESOURCE_PACKAGE, NextGenConfiguration.TUNNEL_RESOURCE_PACKAGE,
                    NextGenConfiguration.PRIVATE_CONNECTIVITY_RESOURCE_PACKAGE,
                    NextGenConfiguration.NG_LDAP_RESOURCE_PACKAGE, NextGenConfiguration.LDAP_PACKAGE,
                    NextGenConfiguration.IAC_PACKAGE, NextGenConfiguration.MONITORING_MANAGER_PACKAGE,
                    NextGenConfiguration.IRO_MANAGER_PACKAGE, NextGenConfiguration.IRO_PACKAGE,
                    NextGenConfiguration.PROVIDERS_PACKAGE, NextGenConfiguration.WEBHOOKS_PACKAGE,
                    NextGenConfiguration.HARNESS_ARTIFACT_INSTANCE_RESOURCE_PACKAGE,
                    NextGenConfiguration.NGSUBSCRIPTIONS_PACKAGE, NextGenConfiguration.NG_BANNERS_RESOURCE_PACKAGE,
                    NextGenConfiguration.BRANDING_RESOURCE_PACKAGE, NextGenConfiguration.CGI_CONFIG_PACKAGE,
                    NextGenConfiguration.PLUGIN_CONFIG_PACKAGE, NextGenConfiguration.OIDC_AUTH_PROVIDER_PACKAGE,
                    NextGenConfiguration.HARNESS_CODE_RESOURCE_PACKAGE, NextGenConfiguration.AI_TEST_AUTOMATION_PACKAGE,
                    NextGenConfiguration.HARNESS_SMP_RESOURCE_PACKAGE, NextGenConfiguration.STO_PACKAGE,
                    NextGenConfiguration.DEPLOYABLE_PACKAGE, NextGenConfiguration.SALESFORCE_COMPARISON_PAIR_PACKAGE,
                    NextGenConfiguration.SALESFORCE_CHANGESET_PACKAGE,
                    NextGenConfiguration.SALESFORCE_EXECUTION_PACKAGE,
                    NextGenConfiguration.SALESFORCE_DEFAULT_PIPELINES_PACKAGE))
            .sorted(Comparator.comparing(Class::getName))
            .collect(Collectors.toList());
    resourceClasses.add(OidcSwaggerListener.class);
    return resourceClasses;
  }

  public NextGenConfiguration() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    defaultServerFactory.setJerseyRootPath("/");
    defaultServerFactory.setRegisterDefaultExceptionMappers(false);
    defaultServerFactory.setAdminContextPath("/admin");
    defaultServerFactory.setAdminConnectors(singletonList(getDefaultAdminConnectorFactory()));
    defaultServerFactory.setApplicationConnectors(singletonList(getDefaultApplicationConnectorFactory()));
    defaultServerFactory.setRequestLogFactory(getDefaultlogbackAccessRequestLogFactory());
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

  protected ConnectorFactory getDefaultAdminConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(7091);
    return factory;
  }

  protected ConnectorFactory getDefaultApplicationConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(7090);
    return factory;
  }

  private RequestLogFactory getDefaultlogbackAccessRequestLogFactory() {
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

  public static Set<String> getUniquePackagesContainingResources() {
    return HARNESS_RESOURCE_CLASSES.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }

  @JsonIgnore
  public OpenAPIConfiguration getOasConfig() {
    OpenAPI oas = new OpenAPI();
    Info info = new Info()
                    .title("Harness NextGen Software Delivery Platform API Reference")
                    .description("This is the Open Api Spec 3 for the NextGen Manager. This is under active "
                        + "development. Beware of the breaking change with respect to the generated code stub")
                    .termsOfService("https://harness.io/terms-of-use/")
                    .version("3.0")
                    .contact(new Contact().email("contact@harness.io"));
    oas.info(info);
    try {
      URL baseurl = new URL("https", hostname, basePathPrefix);
      Server server = new Server();
      server.setUrl(baseurl.toString());
      oas.servers(Collections.singletonList(server));
    } catch (MalformedURLException e) {
      log.error("failed to set baseurl for server, {}/{}", hostname, basePathPrefix);
    }
    final Set<String> resourceClasses =
        getOAS3ResourceClassesOnly().stream().map(Class::getCanonicalName).collect(toSet());
    return new SwaggerConfiguration()
        .openAPI(oas)
        .prettyPrint(true)
        .resourceClasses(resourceClasses)
        .scannerClass("io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  public static Collection<Class<?>> getOAS3ResourceClassesOnly() {
    return HARNESS_RESOURCE_CLASSES.stream().filter(x -> x.isAnnotationPresent(Tag.class)).collect(Collectors.toList());
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (mongoConfig != null) {
      dbAliases.add(mongoConfig.getAliasDBName());
    }
    return dbAliases;
  }
}

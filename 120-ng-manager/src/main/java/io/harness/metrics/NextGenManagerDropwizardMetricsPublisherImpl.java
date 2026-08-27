/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics;

import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class NextGenManagerDropwizardMetricsPublisherImpl implements MetricsPublisher {
  private final MetricRegistry metricRegistry;
  private final MetricService metricService;
  private static final Double SNAPSHOT_FACTOR = 1.0D / (double) TimeUnit.SECONDS.toNanos(1L);
  private static final Pattern METRIC_NAME_RE = Pattern.compile("[^a-zA-Z0-9:_]");
  private static final String NAMESPACE = System.getenv("NAMESPACE");
  private static final String SERVICE_NAME = "ng-manager";
  private static final MetricFilter MUTABLE_SERVLET_CONTEXT_HANDLER_FILTER =
      MetricFilter.startsWith("io.dropwizard.jetty.MutableServletContextHandler");

  private static final List<String> RESOURCE_METRIC_NAMES = List.of(
      "io.harness.ng.core.service.resources.ServiceResourceV2",
      "io.harness.ng.core.environment.resources.EnvironmentResourceV2",
      "io.harness.ng.core.infrastructure.resource.InfrastructureResource",
      "io.harness.ng.core.artifacts.resources.docker.DockerArtifactResource",
      "io.harness.ng.core.remote.ProjectResource", "io.harness.ng.core.remote.OrganizationResource",
      "io.harness.ng.core.remote.OrgProjectApiImpl", "io.harness.ng.core.remote.OrganizationApiImpl",
      "io.harness.ngsettings.remote.SettingsResource", "io.harness.ng.core.delegate.resources.DelegateDownloadResource",
      "io.harness.ng.core.delegate.resources.DelegateTokenNgResource",
      "io.harness.ng.core.delegate.resources.DelegateConfigNgV2Resource",
      "io.harness.ng.core.delegate.resources.DelegateGroupTagsResource",
      "io.harness.ng.core.delegate.resources.DelegateProfileNgResource",
      "io.harness.ng.core.delegate.resources.DelegateSetupNgResource",
      "io.harness.connector.apis.resource.ConnectorResource", "io.harness.ng.core.service.resources.ServiceResource",
      "io.harness.ng.core.migration.serviceenvmigrationv2.resources.ServiceEnvironmentV2MigrationResource",
      "io.harness.ng.core.serviceoverrides.resources.ServiceOverridesResource",
      "io.harness.ng.servicediscovery.ServiceDiscoveryResource",
      "io.harness.ng.servicenow.resources.ServiceNowResource",
      "io.harness.ng.core.environment.resources.EnvironmentResource",
      "io.harness.ng.core.accountsetting.resources.AccountSettingResource",
      "io.harness.ng.serviceaccounts.resource.ServiceAccountResource",
      "io.harness.ng.core.remote.NGSecretManagerResource", "io.harness.ng.core.remote.NGSecretResourceV2",
      "io.harness.connector.apis.resource.GcpConnectorResource", "io.harness.filestore.resource.FileStoreResource",
      "io.harness.ng.core.terraform.resources.TerraformResource",
      "io.harness.ng.core.terragrunt.resources.TerragruntResource",
      "io.harness.ng.core.terraformcloud.resources.TerraformCloudResource",
      "io.harness.ng.core.artifacts.resources.acr.AcrArtifactResource",
      "io.harness.ng.core.artifacts.resources.ami.AMIArtifactResource",
      "io.harness.ng.core.artifacts.resources.artifactory.ArtifactoryArtifactResource",
      "io.harness.ng.core.artifacts.resources.azureartifacts.AzureArtifactsArtifactResource",
      "io.harness.ng.core.artifacts.resources.bamboo.BambooArtifactResource",
      "io.harness.ng.core.artifacts.resources.custom.CustomArtifactResource",
      "io.harness.ng.core.artifacts.resources.ecr.EcrArtifactResource",
      "io.harness.ng.core.artifacts.resources.gar.GARArtifactResource",
      "io.harness.ng.core.artifacts.resources.gcr.GcrArtifactResource",
      "io.harness.ng.core.artifacts.resources.gcp.GCEImageArtifactResource",
      "io.harness.ng.core.artifacts.resources.githubpackages.GithubPackagesArtifactResource",
      "io.harness.ng.core.artifacts.resources.googlecloudstorage.GoogleCloudStorageArtifactResource",
      "io.harness.ng.core.artifacts.resources.jenkins.JenkinsArtifactResource",
      "io.harness.ng.core.artifacts.resources.nexus.NexusArtifactResource",
      "io.harness.ng.core.aws.resources.AwsHelperResource",
      "io.harness.ng.core.buckets.resources.gcs.GcsBucketsResource",
      "io.harness.ng.core.buckets.resources.s3.S3BucketResource",
      "io.harness.ng.core.customDeployment.resources.CustomDeployment",
      "io.harness.ng.core.manifests.resources.HelmChartVersionResource",
      "io.harness.ng.core.deploymentstage.DeploymentStageConfigResource",
      "io.harness.ng.core.gcp.resources.GcpResource", "io.harness.ng.core.k8s.cluster.resources.gcp.GcpClusterResource",
      "io.harness.ng.core.tas.resources.TasResource", "io.harness.ng.core.resources.azure.AzureResource",
      "io.harness.ng.gitops.resource.ClusterResource", "io.harness.ng.instance.InstanceNGResource",
      "io.harness.ng.jira.resources.JiraResource", "io.harness.ng.overview.resource.CDDashboardOverviewResource",
      "io.harness.ng.overview.resource.CDLandingDashboardResource",
      "io.harness.ng.overview.resource.CDLandingPageResource", "io.harness.ng.rollback.PostProdRollbackResource",
      "io.harness.ng.core.refresh.EntityRefreshResource", "io.harness.ng.core.remote.NGHostResource",
      "io.harness.ng.core.remote.SampleManifestsResource", "io.harness.ng.support.resource.CannyResource",
      "io.harness.ng.webhook.polling.PollingResource", "io.harness.gitopsprovider.resource.GitopsProviderResource",
      "io.harness.ng.core.remote.ScopeInfoResource", "io.harness.cdng.usage.v2.resource.CDLicenseUsageResourceV2",
      "io.harness.ng.core.remote.NGAggregateResource", "io.harness.ng.core.remote.ProjectOrgAggregateResource",
      "io.harness.ng.core.variable.resources.VariableResource", "io.harness.ng.feedback.resources.FeedbackResource",
      "io.harness.ng.scim.resource.NGScimGroupResource", "io.harness.ng.scim.resource.NGScimStaticResource",
      "io.harness.ng.scim.resource.NGScimUserResource", "io.harness.ng.serviceaccounts.resource.ServiceAccountResource",
      "io.harness.ldap.resource.NGLdapResource", "io.harness.ldap.resource.NGLdapResourceInternal",
      "io.harness.ng.core.agent.resources.AgentMtlsEndpointNgResource",
      "io.harness.ng.core.entitysetupusage.resource.EntitySetupUsageResource",
      "io.harness.ng.core.globalkms.resource.NgGlobalKmsResource", "io.harness.ng.core.invites.remote.InviteResource",
      "io.harness.ng.core.oidc.NgDelegateOidcResource", "io.harness.ng.core.oidc.NgOidcAccessTokenResource",
      "io.harness.ng.core.oidc.NgOidcIDTokenResource", "io.harness.ng.core.oidc.OidcResource",
      "io.harness.ng.core.remote.ApiKeyResource", "io.harness.ng.core.remote.HealthResource",
      "io.harness.ng.core.remote.TokenResource", "io.harness.ng.core.remote.UserGroupResource",
      "io.harness.ng.core.remote.UserGroupResourceV2", "io.harness.ng.core.smtp.resources.SmtpNgResource",
      "io.harness.ngsettings.remote.UserSettingResource", "io.harness.spec.server.ng.v1",
      "io.harness.ng.webhook.resources", "io.harness.ng.instancesync.resources.InstanceSyncResource");

  @Override
  public void recordMetrics() {
    metricRegistry.getMeters(MUTABLE_SERVLET_CONTEXT_HANDLER_FILTER)
        .forEach((key, value) -> recordMutableServletContextMeter(key, value));
    RESOURCE_METRIC_NAMES.forEach(resourceMetricName
        -> metricRegistry.getMeters(MetricFilter.startsWith(resourceMetricName)).forEach(this::recordResourceMeter));
    Set<Map.Entry<String, Gauge>> gaugeSet = metricRegistry.getGauges().entrySet();
    gaugeSet.forEach(entry -> recordGauge(entry.getKey(), entry.getValue()));
    Set<Map.Entry<String, Timer>> timerSet = metricRegistry.getTimers().entrySet();
    timerSet.forEach(entry -> recordTimer(entry.getKey(), entry.getValue()));
    Set<Map.Entry<String, Counter>> counterSet = metricRegistry.getCounters().entrySet();
    counterSet.forEach(entry -> recordCounter(entry.getKey(), entry.getValue()));
  }

  private void recordMutableServletContextMeter(String metricName, Meter meter) {
    try (NextGenMetricsContext ignore = new NextGenMetricsContext(NAMESPACE, SERVICE_NAME)) {
      recordMetric(sanitizeMetricName(metricName) + "_count", meter.getCount());
    }
  }

  private void recordResourceMeter(String originalMetricName, Meter meter) {
    String[] s = originalMetricName.split("\\.");
    if (s.length >= 4) {
      String statusCode = s[s.length - 1].split("-")[0];
      String method = s[s.length - 2];
      String resource = s[s.length - 3];
      try (NextGenMetricsContext ignore =
               new NextGenMetricsContext(NAMESPACE, SERVICE_NAME, resource, method, statusCode)) {
        recordMetric(DwMetricContext.IO_HARNESS_RESOURCE_RESPONSES_COUNT_METRIC_NAME, meter.getCount());
      }
    }
  }

  private void recordCounter(String metricName, Counter counter) {
    try (NextGenMetricsContext ignore = new NextGenMetricsContext(NAMESPACE, SERVICE_NAME)) {
      recordMetric(sanitizeMetricName(metricName), counter.getCount());
    }
  }

  private void recordGauge(String metricName, Gauge gauge) {
    try (NextGenMetricsContext ignore = new NextGenMetricsContext(NAMESPACE, SERVICE_NAME)) {
      Object obj = gauge.getValue();
      double value;
      if (obj instanceof Number) {
        value = ((Number) obj).doubleValue();
      } else {
        if (!(obj instanceof Boolean)) {
          log.debug(String.format(
              "Invalid type for Gauge %s: %s", metricName, obj == null ? "null" : obj.getClass().getName()));
          return;
        }
        value = (Boolean) obj ? 1.0D : 0.0D;
      }
      recordMetric(sanitizeMetricName(metricName), value);
    }
  }

  private void recordTimer(String metricName, Timer timer) {
    if (isNgManagerResourceMetric(metricName)) {
      addTimerMetricsForResources(metricName, DwMetricContext.IO_HARNESS_RESOURCE_RESPONSES_LATENCY_METRIC_NAME, timer);
    } else {
      try (NextGenMetricsContext ignore = new NextGenMetricsContext(NAMESPACE, SERVICE_NAME)) {
        String sanitizedMetricName = sanitizeMetricName(metricName);
        recordMetric(sanitizedMetricName + "_count", timer.getCount());
        recordSnapshot(sanitizedMetricName + "_snapshot", timer.getSnapshot());
      }
    }
  }

  private void addTimerMetricsForResources(String originalMetricName, String metricName, Timer timer) {
    String[] s = originalMetricName.split("\\.");
    if (s.length >= 3) {
      String methodName = s[s.length - 2];
      String resourceName = s[s.length - 3];
      try (
          NextGenMetricsContext ignore = new NextGenMetricsContext(NAMESPACE, SERVICE_NAME, resourceName, methodName)) {
        String sanitizedMetricName = sanitizeMetricName(metricName);
        Snapshot snapshot = timer.getSnapshot();
        recordMetric(sanitizedMetricName + "_snapshot_95thPercentile", snapshot.get95thPercentile() * SNAPSHOT_FACTOR);
        recordMetric(sanitizedMetricName + "_snapshot_99thPercentile", snapshot.get99thPercentile() * SNAPSHOT_FACTOR);
      }
    }
  }

  private boolean isNgManagerResourceMetric(String metricName) {
    for (String resourceName : RESOURCE_METRIC_NAMES) {
      // Logging only total metrics as we want to find total time spent for each api
      if (metricName.startsWith(resourceName) && metricName.contains(".total")) {
        return true;
      }
    }
    return false;
  }

  private void recordSnapshot(String metricName, Snapshot snapshot) {
    try (NextGenMetricsContext ignore = new NextGenMetricsContext(NAMESPACE, SERVICE_NAME)) {
      recordMetric(metricName + "_95thPercentile", snapshot.get95thPercentile() * SNAPSHOT_FACTOR);
      recordMetric(metricName + "_99thPercentile", snapshot.get99thPercentile() * SNAPSHOT_FACTOR);
      recordMetric(metricName + "_999thPercentile", snapshot.get999thPercentile() * SNAPSHOT_FACTOR);
    }
  }

  private void recordMetric(String name, double value) {
    metricService.recordMetric(name, value);
  }

  private static String sanitizeMetricName(String dropwizardName) {
    String name = METRIC_NAME_RE.matcher(dropwizardName).replaceAll("_");
    if (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
      name = "_" + name;
    }
    return name;
  }
}

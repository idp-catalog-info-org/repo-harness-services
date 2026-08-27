/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.*;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cistatus.service.GithubService;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.idp.integrations.service.git.BitbucketCloudIntegrationOpsImpl;
import io.harness.idp.integrations.service.git.GithubIntegrationOpsImpl;
import io.harness.idp.integrations.service.git.GitlabIntegrationOpsImpl;
import io.harness.idp.proxy.envvariable.ProxyEnvVariableServiceWrapper;
import io.harness.idp.proxy.services.IdpAuthInterceptor;
import io.harness.idp.scorecard.datapoints.parser.factory.DataSourceDataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactoryV2;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.DataSourceProvider;
import io.harness.idp.scorecard.datasources.providers.scm.*;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;

import com.google.inject.Inject;
import com.google.inject.name.Named;

@OwnedBy(HarnessTeam.IDP)
public class DataSourceProviderFactory {
  @Inject DataPointService dataPointService;
  @Inject DataSourceLocationFactory dataSourceLocationFactory;
  @Inject DataSourceLocationFactoryV2 dataSourceLocationFactoryV2;
  @Inject DataSourceLocationRepository dataSourceLocationRepository;
  @Inject DataSourceDataPointParserFactory dataSourceDataPointParserFactory;

  @Inject IdpAuthInterceptor idpAuthInterceptor;
  @Inject @Named("base") private String base;

  @Inject ConfigReader configReader;
  @Inject DataSourceRepository dataSourceRepository;
  @Inject GithubService githubService;
  @Inject ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper;
  @Inject @Named("harnessCodeRepoConfig") HarnessCodeRepoConfig harnessCodeRepoConfig;
  @Inject HarnessCodeConnectorUtils harnessCodeConnectorUtils;

  @Inject IntegrationEntityRepository integrationEntityRepository;
  @Inject ConnectorResourceClient connectorResourceClient;
  @Inject BitbucketCloudIntegrationOpsImpl bitbucketCloudIntegrationOps;
  @Inject GitlabIntegrationOpsImpl gitlabIntegrationOps;
  @Inject GithubIntegrationOpsImpl githubIntegrationOps;
  @Inject BackstageEnvVariableService backstageEnvVariableService;
  @Inject AccountClient accountClient;

  public DataSourceProvider getProvider(String dataSource, boolean isUseLocalGitConnectorForScoreComputationEnabled) {
    switch (dataSource) {
      case CATALOG_IDENTIFIER:
        return new CatalogProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(CATALOG_IDENTIFIER), dataSourceRepository);
      case GITHUB_IDENTIFIER: {
        GithubProvider githubProvider = new GithubProvider(dataPointService, dataSourceLocationFactory,
            dataSourceLocationRepository, dataSourceDataPointParserFactory.getDataPointParserFactory(GITHUB_IDENTIFIER),
            configReader, dataSourceRepository, githubService, proxyEnvVariableServiceWrapper, connectorResourceClient,
            githubIntegrationOps, backstageEnvVariableService, accountClient,
            isUseLocalGitConnectorForScoreComputationEnabled);
        return new GithubDataSourceProviderV2(dataPointService, dataSourceLocationFactoryV2,
            dataSourceLocationRepository, dataSourceDataPointParserFactory.getDataPointParserFactory(GITHUB_IDENTIFIER),
            dataSourceRepository, githubProvider);
      }
      case BITBUCKET_IDENTIFIER: {
        BitbucketProvider bitbucketProvider = new BitbucketProvider(dataPointService, dataSourceLocationFactory,
            dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(BITBUCKET_IDENTIFIER), configReader,
            dataSourceRepository, integrationEntityRepository, connectorResourceClient, bitbucketCloudIntegrationOps,
            backstageEnvVariableService, accountClient, isUseLocalGitConnectorForScoreComputationEnabled);
        return new BitbucketDataSourceProviderV2(dataPointService, dataSourceLocationFactoryV2,
            dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(BITBUCKET_IDENTIFIER), dataSourceRepository,
            bitbucketProvider);
      }
      case GITLAB_IDENTIFIER:
        return new GitlabProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(GITLAB_IDENTIFIER), configReader,
            dataSourceRepository, connectorResourceClient, gitlabIntegrationOps, backstageEnvVariableService,
            accountClient, isUseLocalGitConnectorForScoreComputationEnabled);
      case HARNESS_IDENTIFIER:
        return new HarnessProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(HARNESS_IDENTIFIER), harnessCodeRepoConfig,
            harnessCodeConnectorUtils, base, dataSourceRepository, accountClient,
            isUseLocalGitConnectorForScoreComputationEnabled);
      case CUSTOM_IDENTIFIER:
        return new CustomProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(CUSTOM_IDENTIFIER), dataSourceRepository);
      case PAGERDUTY_IDENTIFIER: {
        PagerDutyProvider pagerDutyProvider =
            new PagerDutyProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
                dataSourceDataPointParserFactory.getDataPointParserFactory(PAGERDUTY_IDENTIFIER), configReader,
                dataSourceRepository);
        return new PagerDutyDataSourceProviderV2(dataPointService, dataSourceLocationFactoryV2,
            dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(PAGERDUTY_IDENTIFIER), dataSourceRepository,
            pagerDutyProvider);
      }
      case JIRA_IDENTIFIER:
        return new JiraProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(JIRA_IDENTIFIER), configReader,
            dataSourceRepository);
      case KUBERNETES_IDENTIFIER: {
        KubernetesProvider kubernetesProvider =
            new KubernetesProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
                dataSourceDataPointParserFactory.getDataPointParserFactory(KUBERNETES_IDENTIFIER), configReader,
                idpAuthInterceptor, base, dataSourceRepository);
        return new KubernetesDataSourceProviderV2(dataPointService, dataSourceLocationFactoryV2,
            dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(KUBERNETES_IDENTIFIER), dataSourceRepository,
            kubernetesProvider);
      }
      case TRACEABLE_IDENTIFIER:
        return new TraceableDataSourceProvider(dataPointService, dataSourceLocationFactoryV2,
            dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(TRACEABLE_IDENTIFIER), dataSourceRepository);
      case DATADOG_IDENTIFIER:
        return new DatadogProvider(dataPointService, dataSourceLocationFactoryV2, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(DATADOG_IDENTIFIER), dataSourceRepository);
      case DYNATRACE_IDENTIFIER:
        return new DynatraceProvider(dataPointService, dataSourceLocationFactoryV2, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(DYNATRACE_IDENTIFIER), dataSourceRepository);
      case GCP_IDENTIFIER:
        return new GcpProvider(dataPointService, dataSourceLocationFactoryV2, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(GCP_IDENTIFIER), dataSourceRepository);
      case HARNESS_CD_IDENTIFIER:
        return new HarnessCDProvider(dataPointService, dataSourceLocationFactoryV2, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(HARNESS_CD_IDENTIFIER), dataSourceRepository);
      case SONAR_IDENTIFIER:
        return new SonarQubeProvider(dataPointService, dataSourceLocationFactoryV2, dataSourceLocationRepository,
            dataSourceDataPointParserFactory.getDataPointParserFactory(SONAR_IDENTIFIER), dataSourceRepository);
      default:
        throw new IllegalArgumentException("DataSource provider " + dataSource + " is not supported yet");
    }
  }
}

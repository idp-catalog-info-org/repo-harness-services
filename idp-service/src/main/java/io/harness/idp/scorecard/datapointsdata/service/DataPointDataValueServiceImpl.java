/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.service;

import static io.harness.idp.common.Constants.BITBUCKET_IDENTIFIER;
import static io.harness.idp.common.Constants.GITHUB_IDENTIFIER;
import static io.harness.idp.common.Constants.GITLAB_IDENTIFIER;
import static io.harness.idp.common.Constants.HARNESS_IDENTIFIER;
import static io.harness.idp.common.Constants.KUBERNETES_IDENTIFIER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DataSourceDsl;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.factory.DataSourceDslFactory;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointInfo;
import io.harness.spec.server.idp.v1.model.DataSourceLocationInfo;
import io.harness.spec.server.idp.v1.model.KubernetesConfig;
import io.harness.spec.server.idp.v1.model.ScmConfig;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class DataPointDataValueServiceImpl implements DataPointDataValueService {
  DataSourceDslFactory dataSourceDataProviderFactory;
  DataPointService dataPointService;

  public Map<String, Object> getDataPointDataValues(
      String accountIdentifier, String datasourceIdentifier, Object config) {
    DataSourceDsl dataSourceDataProvider =
        dataSourceDataProviderFactory.getDataSourceDataProvider(datasourceIdentifier);

    Map<String, List<DataPointEntity>> dataToFetch = dataPointService.getDslDataPointsInfo(accountIdentifier,
        getDataPointIdentifiers(datasourceIdentifier, config)
            .getDataPoints()
            .stream()
            .map(DataPointInputValues::getDataPointIdentifier)
            .collect(Collectors.toList()),
        datasourceIdentifier);

    List<String> dslIdentifiers = new ArrayList<>(dataToFetch.keySet());
    log.info("Mapped DSL identifier for data points - {}", dslIdentifiers.get(0));

    DslDataProvider dslDataProvider = dataSourceDataProvider.getDslDataProvider(dslIdentifiers.get(0));
    return dslDataProvider.getDslData(accountIdentifier, config);
  }

  DataSourceLocationInfo getDataPointIdentifiers(String dataSourceIdentifier, Object config) {
    switch (dataSourceIdentifier) {
      case HARNESS_IDENTIFIER -> {
        if (config instanceof DataSourceDataPointInfo) {
          return ((DataSourceDataPointInfo) config).getDataSourceLocation();
        } else {
          return ((ScmConfig) config).getDataSourceLocation();
        }
      }
      case KUBERNETES_IDENTIFIER -> {
        return ((KubernetesConfig) config).getDataSourceLocation();
      }
      case GITHUB_IDENTIFIER, BITBUCKET_IDENTIFIER, GITLAB_IDENTIFIER -> {
        return ((ScmConfig) config).getDataSourceLocation();
      }
      default ->
              throw new UnsupportedOperationException(String.format("%s data source is not supported", dataSourceIdentifier));
    }
  }
}

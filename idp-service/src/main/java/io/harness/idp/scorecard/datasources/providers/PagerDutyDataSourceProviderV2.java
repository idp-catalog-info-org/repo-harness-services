/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.PAGERDUTY_IDENTIFIER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactoryV2;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class PagerDutyDataSourceProviderV2 extends IntegrationDataSourceProvider {
  private final PagerDutyProvider pagerDutyProvider;

  protected PagerDutyDataSourceProviderV2(DataPointService dataPointService,
      DataSourceLocationFactoryV2 dataSourceLocationFactory, DataSourceLocationRepository dataSourceLocationRepository,
      DataPointParserFactory dataPointParserFactory, DataSourceRepository dataSourceRepository,
      PagerDutyProvider pagerDutyProvider) {
    super(PAGERDUTY_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
    this.pagerDutyProvider = pagerDutyProvider;
  }

  @Override
  public Map<String, Map<String, Object>> fetchData(
      String accountIdentifier, Object entity, List<DataFetchDTO> dataToFetch, String configs) {
    return fetchDataWithLegacySplit(accountIdentifier, entity, dataToFetch, configs, pagerDutyProvider);
  }
}

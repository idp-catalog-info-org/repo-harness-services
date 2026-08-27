/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.TRACEABLE_IDENTIFIER;

import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactoryV2;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;

public class TraceableDataSourceProvider extends IntegrationDataSourceProvider {
  protected TraceableDataSourceProvider(DataPointService dataPointService,
      DataSourceLocationFactoryV2 dataSourceLocationFactory, DataSourceLocationRepository dataSourceLocationRepository,
      DataPointParserFactory dataPointParserFactory, DataSourceRepository dataSourceRepository) {
    super(TRACEABLE_IDENTIFIER, dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
  }
}

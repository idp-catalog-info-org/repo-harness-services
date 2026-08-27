/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.factory;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DefaultCatalogDSLParser;
import io.harness.idp.scorecard.datapoints.parser.DefaultHQLParser;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;

@OwnedBy(HarnessTeam.IDP)
public abstract class IntegrationDataPointParserFactory implements DataPointParserFactory {
  protected final DefaultCatalogDSLParser defaultCatalogDSLParser;
  protected final DefaultHQLParser defaultHQLParser;

  public IntegrationDataPointParserFactory(
      DefaultHQLParser defaultHQLParser, DefaultCatalogDSLParser defaultCatalogDSLParser) {
    this.defaultCatalogDSLParser = defaultCatalogDSLParser;
    this.defaultHQLParser = defaultHQLParser;
  }

  @Override
  public DataPointParser getParser(String identifier, DataSourceLocationType type) {
    if (type == DataSourceLocationType.HQL) {
      return defaultHQLParser;
    } else if (type == DataSourceLocationType.CATALOG) {
      return defaultCatalogDSLParser;
    }
    throw new UnsupportedOperationException(String.format("Could not find DataPoint parser for %s", identifier));
  }
}

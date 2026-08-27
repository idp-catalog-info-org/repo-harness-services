/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.factory;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.parser.DefaultCatalogDSLParser;
import io.harness.idp.scorecard.datapoints.parser.DefaultHQLParser;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.IDP)
public class DefaultIntegrationDataPointParserFactory extends IntegrationDataPointParserFactory {
  @Inject
  public DefaultIntegrationDataPointParserFactory(
      DefaultHQLParser defaultHQLParser, DefaultCatalogDSLParser defaultCatalogDSLParser) {
    super(defaultHQLParser, defaultCatalogDSLParser);
  }
}

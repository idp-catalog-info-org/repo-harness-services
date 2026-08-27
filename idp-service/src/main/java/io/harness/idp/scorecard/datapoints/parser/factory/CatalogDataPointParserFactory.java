/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.factory;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_AI_ASSET_DISCOVERED_AT;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_AI_ASSET_ID_PREFIX_MATCH;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_AI_ASSET_PROVIDER_EXISTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_AI_ASSET_SOURCE_FILE_PATTERN_MATCH;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_ANNOTATION_EXISTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_EVALUATE_EXPR;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_PAGERDUTY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_SPEC_OWNER;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_SYSTEM_IS_DEFINED_AND_IT_EXISTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_TECH_DOCS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.parser.AIAssetDiscoveryRecencyParser;
import io.harness.idp.scorecard.datapoints.parser.AIAssetIdPrefixParser;
import io.harness.idp.scorecard.datapoints.parser.AIAssetProviderParser;
import io.harness.idp.scorecard.datapoints.parser.AIAssetSourceFileParser;
import io.harness.idp.scorecard.datapoints.parser.AnnotationParser;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.GenericExpressionParser;
import io.harness.idp.scorecard.datapoints.parser.SystemExistsParser;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class CatalogDataPointParserFactory implements DataPointParserFactory {
  private GenericExpressionParser genericExpressionParser;
  private AnnotationParser annotationParser;
  private SystemExistsParser systemExistsParser;
  private AIAssetSourceFileParser assetSourceFileParser;
  private AIAssetIdPrefixParser aiAssetIdPrefixParser;
  private AIAssetProviderParser assetProviderParser;
  private AIAssetDiscoveryRecencyParser aiAssetDiscoveryRecencyParser;

  public DataPointParser getParser(String identifier, DataSourceLocationType dataSourceLocationType) {
    switch (identifier) {
      case CATALOG_TECH_DOCS:
      case CATALOG_PAGERDUTY:
      case CATALOG_SPEC_OWNER:
      case CATALOG_EVALUATE_EXPR:
        return genericExpressionParser;
      case CATALOG_SYSTEM_IS_DEFINED_AND_IT_EXISTS:
        return systemExistsParser;
      case CATALOG_ANNOTATION_EXISTS:
        return annotationParser;
      case CATALOG_AI_ASSET_SOURCE_FILE_PATTERN_MATCH:
        return assetSourceFileParser;
      case CATALOG_AI_ASSET_ID_PREFIX_MATCH:
        return aiAssetIdPrefixParser;
      case CATALOG_AI_ASSET_PROVIDER_EXISTS:
        return assetProviderParser;
      case CATALOG_AI_ASSET_DISCOVERED_AT:
        return aiAssetDiscoveryRecencyParser;
      default:
        throw new UnsupportedOperationException(String.format("Could not find DataPoint parser for %s", identifier));
    }
  }
}

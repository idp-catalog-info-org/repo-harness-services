/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.factory;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.EXTRACT_STRING_FROM_A_FILE;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_BRANCH_PROTECTED;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_FILE_EXISTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.MATCH_STRING_IN_A_FILE;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.PULL_REQUEST_MEAN_TIME_TO_MERGE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DefaultCatalogDSLParser;
import io.harness.idp.scorecard.datapoints.parser.DefaultHQLParser;
import io.harness.idp.scorecard.datapoints.parser.scm.bitbucket.BitbucketFileContainsParser;
import io.harness.idp.scorecard.datapoints.parser.scm.bitbucket.BitbucketFileContentsParser;
import io.harness.idp.scorecard.datapoints.parser.scm.bitbucket.BitbucketFileExistsParser;
import io.harness.idp.scorecard.datapoints.parser.scm.bitbucket.BitbucketIsBranchProtectedParser;
import io.harness.idp.scorecard.datapoints.parser.scm.bitbucket.BitbucketMeanTimeToMergeParser;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.IDP)
public class BitbucketDataPointParserFactory extends IntegrationDataPointParserFactory {
  private final BitbucketMeanTimeToMergeParser bitbucketMeanTimeToMergeParser;
  private final BitbucketIsBranchProtectedParser bitbucketIsBranchProtectedParser;
  private final BitbucketFileContainsParser bitbucketFileContainsParser;
  private final BitbucketFileContentsParser bitbucketFileContentsParser;
  private final BitbucketFileExistsParser bitbucketFileExistsParser;

  @Inject
  public BitbucketDataPointParserFactory(BitbucketMeanTimeToMergeParser bitbucketMeanTimeToMergeParser,
      BitbucketIsBranchProtectedParser bitbucketIsBranchProtectedParser,
      BitbucketFileContainsParser bitbucketFileContainsParser, BitbucketFileContentsParser bitbucketFileContentsParser,
      BitbucketFileExistsParser bitbucketFileExistsParser, DefaultCatalogDSLParser defaultCatalogDSLParser,
      DefaultHQLParser defaultHQLParser) {
    super(defaultHQLParser, defaultCatalogDSLParser);
    this.bitbucketMeanTimeToMergeParser = bitbucketMeanTimeToMergeParser;
    this.bitbucketIsBranchProtectedParser = bitbucketIsBranchProtectedParser;
    this.bitbucketFileContainsParser = bitbucketFileContainsParser;
    this.bitbucketFileContentsParser = bitbucketFileContentsParser;
    this.bitbucketFileExistsParser = bitbucketFileExistsParser;
  }

  @Override
  public DataPointParser getParser(String identifier, DataSourceLocationType dataSourceLocationType) {
    switch (identifier) {
      case PULL_REQUEST_MEAN_TIME_TO_MERGE:
        return bitbucketMeanTimeToMergeParser;
      case IS_BRANCH_PROTECTED:
        return bitbucketIsBranchProtectedParser;
      case EXTRACT_STRING_FROM_A_FILE:
        return bitbucketFileContentsParser;
      case MATCH_STRING_IN_A_FILE:
        return bitbucketFileContainsParser;
      case IS_FILE_EXISTS:
        return bitbucketFileExistsParser;
      default:
        return super.getParser(identifier, dataSourceLocationType);
    }
  }
}

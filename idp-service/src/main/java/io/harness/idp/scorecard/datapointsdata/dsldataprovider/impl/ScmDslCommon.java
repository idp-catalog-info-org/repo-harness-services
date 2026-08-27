/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.utils.DslCommons;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.spec.server.idp.v1.model.ScmConfig;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ScmDslCommon {
  @Inject DslClientFactory dslClientFactory;

  private static final String REPOSITORY_DETAILS_URL =
      "/2.0/repositories/{REPOSITORY_OWNER}/{REPOSITORY_NAME}?fields=mainbranch";

  private static final String SCM_PROVIDER_IDENTIFIER_FOR_BITBUCKET = "bitbucket.org";

  private static final String DEFAULT_BRANCH_PLACEHOLDER = "HEAD";

  public String getBranch(String accountIdentifier, Object config) {
    String defaultBranch;
    ScmConfig scmConfig = (ScmConfig) config;

    if (scmConfig.getRepoScm().equals(SCM_PROVIDER_IDENTIFIER_FOR_BITBUCKET)) {
      DslClient client = dslClientFactory.getClient(accountIdentifier, scmConfig.getRepoScm(),
          scmConfig.isThroughDelegate() != null ? scmConfig.isThroughDelegate() : false);
      Map<String, String> headers = Map.of(AUTHORIZATION_HEADER, scmConfig.getToken());
      String baseUrl = "https://api." + scmConfig.getRepoScm();
      ApiRequestDetails apiRequestDetails =
          ApiRequestDetails.builder()
              .method("GET")
              .headers(headers)
              .url(baseUrl
                  + DslCommons.replacePlaceholdersFromSourceLocationAnnotation(REPOSITORY_DETAILS_URL, scmConfig))
              .build();

      Response response = client.call(accountIdentifier, apiRequestDetails,
          new HashSet<>(
              scmConfig.getDelegateSelectors() != null ? scmConfig.getDelegateSelectors() : Collections.emptySet()),
          null);
      defaultBranch = DslCommons.getBranchFromBitbucketResponse(response);
      if (defaultBranch == null) {
        throw new BadRequestException("Unable to find default branch");
      }
    } else if (scmConfig.getRepoScm().contains("harness.io")) {
      defaultBranch = "";
    } else {
      defaultBranch = DEFAULT_BRANCH_PLACEHOLDER;
    }
    return defaultBranch;
  }
}

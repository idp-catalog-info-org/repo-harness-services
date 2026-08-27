/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class AIAssetProviderParser extends AbstractAIAssetParser {
  private static final String PROVIDER = "provider";

  @Override
  protected Object evaluate(Map<String, Object> providerProperties, DataFetchDTO dataFetchDTO) {
    Object provider = providerProperties.get(PROVIDER);
    boolean exists = provider instanceof String && !((String) provider).trim().isEmpty();
    return buildResponse(dataFetchDTO, exists, null);
  }

  @Override
  protected String getErrorMessage() {
    return "Failed to evaluate provider existence";
  }
}

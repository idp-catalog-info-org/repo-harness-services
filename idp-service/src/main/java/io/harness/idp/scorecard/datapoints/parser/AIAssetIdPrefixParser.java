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
public class AIAssetIdPrefixParser extends AbstractAIAssetParser {
  private static final String ASSET_ID = "asset_id";

  private static final Map<String, String> TYPE_TO_PREFIX =
      Map.of("plugin", "p_", "skill", "s_", "command", "c_", "agent", "a_");

  @Override
  protected Object evaluate(Map<String, Object> providerProperties, DataFetchDTO dataFetchDTO) {
    String assetId = (String) providerProperties.get(ASSET_ID);
    String type = (String) providerProperties.get(TYPE);

    if (assetId == null || type == null) {
      return buildResponse(dataFetchDTO, false, "asset_id or type is missing in provider properties");
    }

    String expectedPrefix = TYPE_TO_PREFIX.get(type.toLowerCase());
    if (expectedPrefix == null) {
      log.warn("Unknown AI asset type: {}", type);
      return buildResponse(dataFetchDTO, false, String.format("Unknown AI asset type: %s", type));
    }

    boolean matches = assetId.startsWith(expectedPrefix);
    return buildResponse(dataFetchDTO, matches, null);
  }

  @Override
  protected String getErrorMessage() {
    return "Failed to evaluate asset ID prefix";
  }
}

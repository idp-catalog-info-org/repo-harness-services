/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public abstract class AbstractAIAssetParser implements DataPointParser {
  protected static final String INTEGRATION_PROPERTIES = "integration_properties";
  protected static final String TYPE = "type";
  private static final String KIND = "kind";
  private static final String EXPECTED_KIND = "AiAsset";

  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    try {
      @SuppressWarnings("unchecked") Map<String, Object> dslResponse = (Map<String, Object>) data.get(DSL_RESPONSE);
      if (dslResponse == null) {
        return buildResponse(dataFetchDTO, false, "No DSL response found");
      }

      String kind = (String) dslResponse.get(KIND);
      if (!EXPECTED_KIND.equalsIgnoreCase(kind)) {
        return buildResponse(
            dataFetchDTO, false, String.format("Entity kind is '%s', expected '%s'", kind, EXPECTED_KIND));
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> integrationProperties =
          (Map<String, Object>) CommonUtils.findObjectByName(dslResponse, INTEGRATION_PROPERTIES);
      if (integrationProperties == null) {
        return buildResponse(dataFetchDTO, false, "integration_properties not found");
      }

      Map<String, Object> providerProperties = findFirstProviderProperties(integrationProperties);
      if (providerProperties == null) {
        return buildResponse(dataFetchDTO, false, "No provider properties found under integration_properties");
      }

      return evaluate(providerProperties, dataFetchDTO);

    } catch (Exception e) {
      log.warn("Failed to parse AI asset data point {}", dataFetchDTO.getDataPoint().getIdentifier(), e);
      return buildResponse(dataFetchDTO, false, getErrorMessage());
    }
  }

  protected abstract Object evaluate(Map<String, Object> providerProperties, DataFetchDTO dataFetchDTO);

  protected abstract String getErrorMessage();

  protected Map<String, Object> buildResponse(DataFetchDTO dataFetchDTO, Object value, String errorMessage) {
    Map<String, Object> dataPointResponse = new HashMap<>();
    dataPointResponse.put(DATA_POINT_VALUE_KEY, value);
    dataPointResponse.put(ERROR_MESSAGE_KEY, errorMessage);
    return Map.of(dataFetchDTO.getRuleIdentifier(), dataPointResponse);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> findFirstProviderProperties(Map<String, Object> integrationProperties) {
    for (Map.Entry<String, Object> entry : integrationProperties.entrySet()) {
      if (entry.getValue() instanceof Map) {
        return (Map<String, Object>) entry.getValue();
      }
    }
    return null;
  }
}

/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.harness;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;

import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationNoLoop;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class HarnessSTOActiveVulnerabilitiesDsl extends DataSourceLocationNoLoop {
  @SuppressWarnings("unchecked")
  @Override
  public Map<String, Object> fetchData(String accountIdentifier, Object entity,
      DataSourceLocationEntity dataSourceLocationEntity, List<DataFetchDTO> dataPointAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs, DataSourceConfig dataSourceConfig, boolean throughDelegate,
      Set<String> delegateSelectors) {
    Map<String, Object> ruleData = new HashMap<>();
    if (entity instanceof BackstageCatalogEntity) {
      ruleData.put(ERROR_MESSAGE_KEY, "STO Active Vulnerabilities data point is not supported");
      return ruleData;
    }
    Map<String, Object> decorator = ((CatalogEntity) entity).getFailSafeDecorator();
    Map<String, Object> sto = (Map<String, Object>) CommonUtils.findObjectByName(decorator, "sto");
    if (isEmpty(sto)) {
      ruleData.put(ERROR_MESSAGE_KEY, "No STO scan results found");
      return ruleData;
    }
    Optional<InputValue> scannerToolOptional = dataPointAndInputValues.get(0)
                                                   .getInputValues()
                                                   .stream()
                                                   .filter(inputValue -> inputValue.getKey().equals("scannerTool"))
                                                   .findFirst();
    Set<String> scannersInput = new HashSet<>();
    if (scannerToolOptional.isPresent()) {
      String inputValue = scannerToolOptional.get().getValue();
      inputValue = inputValue.replace("\"", "");
      if (!isEmpty(inputValue)) {
        String[] scannerTools = inputValue.split(",");
        for (String scannerTool : scannerTools) {
          scannersInput.add(scannerTool.trim().toLowerCase());
        }
      }
    }
    Optional<InputValue> severityOptional = dataPointAndInputValues.get(0)
                                                .getInputValues()
                                                .stream()
                                                .filter(inputValue -> inputValue.getKey().equals("severity"))
                                                .findFirst();
    Set<String> severitiesInput = new HashSet<>();
    if (severityOptional.isPresent()) {
      String inputValue = severityOptional.get().getValue();
      inputValue = inputValue.replace("\"", "");
      if (!isEmpty(inputValue)) {
        String[] severities = inputValue.split(",");
        for (String severity : severities) {
          severitiesInput.add(severity.trim().toLowerCase());
        }
      }
    }

    Map<String, Integer> result = new HashMap<>();
    for (Map.Entry<String, Object> target : sto.entrySet()) {
      Map<String, Object> scanners = (Map<String, Object>) target.getValue();
      for (Map.Entry<String, Object> scanner : scanners.entrySet()) {
        String scannerKey = scanner.getKey();
        if (!isEmpty(scannersInput) && !scannersInput.contains(scannerKey)) {
          continue;
        }
        Map<String, Object> issues = (Map<String, Object>) scanner.getValue();
        for (Map.Entry<String, Object> issue : issues.entrySet()) {
          String key = issue.getKey();
          if ((!isEmpty(severitiesInput) && !severitiesInput.contains(key))
              || (isEmpty(severitiesInput) && key.equals("total"))) {
            continue;
          }
          int value = (issue.getValue() instanceof Number) ? ((Number) issue.getValue()).intValue()
                                                           : Integer.parseInt(issue.getValue().toString());
          result.merge(key, value, Integer::sum);
        }
      }
    }

    return Map.of(DSL_RESPONSE, Map.of("total", result.values().stream().mapToInt(Integer::intValue).sum()));
  }

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestBody(
      String requestBody, DataPointEntity dataPoint, List<InputValue> inputValues, Object entity) {
    return null;
  }

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    return null;
  }

  @Override
  protected boolean validate(DataFetchDTO dataFetchDTO, Map<String, Object> data,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs) {
    return false;
  }

  @Override
  protected String getHost(Map<String, String> data) {
    return null;
  }
}

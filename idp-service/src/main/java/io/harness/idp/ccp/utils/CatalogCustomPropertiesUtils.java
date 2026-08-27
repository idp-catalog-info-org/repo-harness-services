/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.utils;

import static io.harness.idp.common.CommonUtils.removeNestedKey;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.common.YamlUtils;
import io.harness.spec.server.idp.v1.model.CustomPropertiesBase;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogCustomPropertiesUtils {
  @SuppressWarnings("unchecked")
  public Map<String, Object> insertMap(
      Map<String, Object> parentMap, Map<String, Object> mapToBeInserted, CustomPropertiesBase.ModeEnum mode) {
    for (Map.Entry<String, Object> entry : mapToBeInserted.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (parentMap.containsKey(key)) {
        if (parentMap.get(key) instanceof Map && value instanceof Map) {
          parentMap.put(key, insertMap((Map<String, Object>) parentMap.get(key), (Map<String, Object>) value, mode));
        } else if (parentMap.get(key) instanceof List && value instanceof List) {
          List<Object> existingValue = (List) parentMap.get(key);
          if (CustomPropertiesBase.ModeEnum.APPEND.equals(mode)) {
            existingValue.addAll((List) value);
            parentMap.put(key, existingValue);
          } else {
            parentMap.put(key, value);
          }
        } else {
          parentMap.put(key, value);
        }
      } else {
        parentMap.put(key, value);
      }
    }
    return parentMap;
  }

  public void removeProperties(Map<String, Object> entityMap, List<String> propertiesToRemove) {
    for (String property : propertiesToRemove) {
      removeNestedKey(entityMap, property);
    }
  }

  public static String removePropertiesFromYaml(String yaml, List<String> propertiesToRemove) {
    Map<String, Object> entityMap = YamlUtils.loadYamlStringAsMap(yaml);
    for (String property : propertiesToRemove) {
      removeNestedKey(entityMap, property);
    }
    return YamlUtils.writeObjectAsYaml(entityMap);
  }
}

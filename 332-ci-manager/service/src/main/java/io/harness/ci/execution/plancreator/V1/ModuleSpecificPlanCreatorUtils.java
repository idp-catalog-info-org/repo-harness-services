/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator.V1;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import java.util.Map;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CI)
@UtilityClass
public class ModuleSpecificPlanCreatorUtils {
  public static void addStringParameter(
      Map<String, Object> deployModuleNodesInfo, String key, Consumer<ParameterField<String>> setter) {
    if (deployModuleNodesInfo.containsKey(key)) {
      setter.accept(ParameterField.createValueField((String) deployModuleNodesInfo.get(key)));
    }
  }

  public static void addMapParameter(
      Map<String, Object> deployModuleNodesInfo, String key, Consumer<ParameterField<Map<String, Object>>> setter) {
    if (deployModuleNodesInfo.containsKey(key)) {
      setter.accept(ParameterField.createValueField((Map<String, Object>) deployModuleNodesInfo.get(key)));
    }
  }

  public static <T> void addExpressionParameter(Map<String, Object> deployModuleNodesInfo, String key,
      String expression, boolean isTypeString, Consumer<ParameterField<T>> setter) {
    if (deployModuleNodesInfo.containsKey(key)) {
      setter.accept(ParameterField.createExpressionField(true, expression, null, isTypeString));
    }
  }
}

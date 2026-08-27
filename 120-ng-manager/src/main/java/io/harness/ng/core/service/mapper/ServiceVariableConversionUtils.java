/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.common.NGExpressionUtils;
import io.harness.encryption.SecretRefData;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NumberNGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Utility class for converting service variables from NG to Unified format.

 */
@UtilityClass
public class ServiceVariableConversionUtils {
  // Field name constants
  private static final String REQUIRED = "required";
  private static final String DESCRIPTION = "description";
  private static final String TYPE = "type";
  private static final String STRING = "string";
  private static final String NUMBER = "number";
  private static final String SECRET = "secret";
  private static final String PATTERN = "pattern";
  private static final String ENUM = "enum";
  private static final String VALUE = "value";
  private static final String DEFAULT = "default";

  /**
   * Converts a list of NG variables to unified inputs format.
   *
   * @param ngVariables list of NG variables
   * @return map of variable name to variable value properties
   */
  public static Map<String, Object> toUnifiedInputs(List<NGVariable> ngVariables) {
    Map<String, Object> serviceInputsV1 = new HashMap<>();
    if (isNotEmpty(ngVariables)) {
      ngVariables.forEach(ngVariable -> {
        Map.Entry<String, Object> unifiedVariable = toUnifiedVariable(ngVariable);
        serviceInputsV1.put(unifiedVariable.getKey(), unifiedVariable.getValue());
      });
    }
    return serviceInputsV1;
  }

  /**
   * Converts a single NG variable to unified format.
   *
   * @param ngVariable the NG variable to convert
   * @return map entry with variable name as key and properties as value
   */
  public static Map.Entry<String, Object> toUnifiedVariable(NGVariable ngVariable) {
    Map<String, Object> variableValue = new HashMap<>();

    // Set common properties
    variableValue.put(REQUIRED, ngVariable.isRequired());
    if (ngVariable.getDescription() != null && !ngVariable.getDescription().isEmpty()) {
      variableValue.put(DESCRIPTION, ngVariable.getDescription());
    }

    // Handle specific variable types and their expressions
    if (ngVariable instanceof StringNGVariable stringNGVariable) {
      variableValue.put(TYPE, STRING);
      handleStringVariableExpression(stringNGVariable, variableValue);
    } else if (ngVariable instanceof NumberNGVariable numberNGVariable) {
      variableValue.put(TYPE, NUMBER);
      handleNumberVariableExpression(numberNGVariable, variableValue);
    } else if (ngVariable instanceof SecretNGVariable secretNGVariable) {
      variableValue.put(TYPE, SECRET);
      handleSecretVariableExpression(secretNGVariable, variableValue);
    }

    return Map.entry(ngVariable.getName(), variableValue);
  }

  /**
   * Handles string variable expression conversion.
   */
  private static void handleStringVariableExpression(StringNGVariable variable, Map<String, Object> variableValue) {
    if (NGExpressionUtils.isRuntimeField(variable.getValue().getExpressionValue())) {
      if (variable.getValue().getInputSetValidator() != null) {
        switch (variable.getValue().getInputSetValidator().getValidatorType()) {
          case REGEX:
            String pattern = variable.getValue().getInputSetValidator().getParameters();
            if (pattern != null) {
              variableValue.put(PATTERN, pattern);
            }
            break;
          case ALLOWED_VALUES:
            String allowedValuesStr = variable.getValue().getInputSetValidator().getParameters();
            if (allowedValuesStr != null) {
              List<String> allowedValues = Arrays.stream(allowedValuesStr.split(","))
                                               .map(String::trim)
                                               .filter(s -> !s.isEmpty())
                                               .collect(Collectors.toList());
              if (!allowedValues.isEmpty()) {
                variableValue.put(ENUM, allowedValues);
              }
            }
            break;
          default:
            // No action needed for other validator types
            break;
        }
      }
    } else {
      variableValue.put(VALUE, variable.getValue().obtainValue());
    }
    if (variable.getDefaultValue() != null) {
      variableValue.put(DEFAULT, variable.getDefaultValue());
    }
  }

  /**
   * Handles number variable expression conversion.
   */
  private static void handleNumberVariableExpression(NumberNGVariable variable, Map<String, Object> variableValue) {
    if (NGExpressionUtils.isRuntimeField(variable.getValue().getExpressionValue())) {
      if (variable.getValue().getInputSetValidator() != null) {
        switch (variable.getValue().getInputSetValidator().getValidatorType()) {
          case REGEX:
            String pattern = variable.getValue().getInputSetValidator().getParameters();
            if (pattern != null) {
              variableValue.put(PATTERN, pattern);
            }
            break;
          case ALLOWED_VALUES:
            String allowedValuesStr = variable.getValue().getInputSetValidator().getParameters();
            if (allowedValuesStr != null) {
              List<String> allowedValues = Arrays.stream(allowedValuesStr.split(","))
                                               .map(String::trim)
                                               .filter(s -> !s.isEmpty())
                                               .collect(Collectors.toList());
              if (!allowedValues.isEmpty()) {
                variableValue.put(ENUM, allowedValues);
              }
            }
            break;
          default:
            // No action needed for other validator types
            break;
        }
      }
    } else {
      variableValue.put(VALUE, variable.getValue().obtainValue());
    }
    if (variable.getDefaultValue() != null) {
      variableValue.put(DEFAULT, variable.getDefaultValue());
    }
  }

  /**
   * Handles secret variable expression conversion.
   */
  private static void handleSecretVariableExpression(SecretNGVariable variable, Map<String, Object> variableValue) {
    if (NGExpressionUtils.isRuntimeField(variable.getValue().getExpressionValue())) {
      if (variable.getValue().getInputSetValidator() != null) {
        switch (variable.getValue().getInputSetValidator().getValidatorType()) {
          case REGEX:
            String pattern = variable.getValue().getInputSetValidator().getParameters();
            if (pattern != null) {
              variableValue.put(PATTERN, pattern);
            }
            break;
          case ALLOWED_VALUES:
            String allowedValuesStr = variable.getValue().getInputSetValidator().getParameters();
            if (allowedValuesStr != null) {
              List<String> allowedValues = Arrays.stream(allowedValuesStr.split(","))
                                               .map(String::trim)
                                               .filter(s -> !s.isEmpty())
                                               .collect(Collectors.toList());
              if (!allowedValues.isEmpty()) {
                variableValue.put(ENUM, allowedValues);
              }
            }
            break;
          default:
            // No action needed for other validator types
            break;
        }
      }
    } else {
      SecretRefData secretRefData = variable.getValue().obtainValue();
      variableValue.put(VALUE, secretRefData == null ? "" : secretRefData.toSecretRefStringValue());
    }
    if (variable.getDefaultValue() != null) {
      variableValue.put(DEFAULT, variable.getDefaultValue());
    }
  }
}

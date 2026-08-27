/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.utils;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.math.NumberUtils;

@UtilityClass
public class ParserUtils {
  public boolean isNumber(Object val) {
    if (val instanceof Number) {
      return true;
    }
    if (val instanceof String strVal) {
      return NumberUtils.isCreatable(strVal);
    }
    return false;
  }

  public Optional<Boolean> coerceBoolean(Object val) {
    if (val instanceof Boolean booleanValue) {
      return Optional.of(booleanValue);
    }
    if (val instanceof String strVal) {
      if ("true".equalsIgnoreCase(strVal) || "false".equalsIgnoreCase(strVal)) {
        return Optional.of(Boolean.valueOf(strVal));
      }
      if (NumberUtils.isCreatable(strVal)) {
        return coerceNumericBoolean(NumberUtils.createNumber(strVal).doubleValue());
      }
    } else if (val instanceof Number numberValue) {
      return coerceNumericBoolean(numberValue.doubleValue());
    }
    return Optional.empty();
  }

  public Optional<Boolean> coerceNumericBoolean(double value) {
    if (value == 1.0) {
      return Optional.of(true);
    }
    if (value == 0.0) {
      return Optional.of(false);
    }
    return Optional.empty();
  }
}

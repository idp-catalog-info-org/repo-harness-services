/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme.enums;

import io.harness.steps.fme.exception.FmeInvalidParameterException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * Enum representing the types of segments supported by FME.
 */
public enum SegmentType {
  STANDARD_SEGMENT("Standard", "standard_segment"),
  LARGE_SEGMENT("Large", "large_segment"),
  RULE_BASED_SEGMENT("RuleBased", "rule_based_segment");

  private final String value;
  @Getter private final String fmeType;

  SegmentType(String value, String fmeType) {
    this.value = value;
    this.fmeType = fmeType;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static SegmentType fromValue(String value) {
    if (value == null) {
      return null;
    }

    String normalizedValue = value.trim();

    // Case-insensitive match on display value
    for (SegmentType type : SegmentType.values()) {
      if (type.value.equalsIgnoreCase(normalizedValue)) {
        return type;
      }
    }

    throw new FmeInvalidParameterException(
        String.format("Invalid segment type: '%s'. Valid types are: Standard, Large, RuleBased", value));
  }

  @Override
  public String toString() {
    return value;
  }
}

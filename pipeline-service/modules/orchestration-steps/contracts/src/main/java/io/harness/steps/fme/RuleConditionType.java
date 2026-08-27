/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.annotations.dev.HarnessTeam.FME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Types of conditions for feature flag targeting rules.
 */
@OwnedBy(FME)
@RecasterAlias("io.harness.steps.fme.RuleConditionType")
public enum RuleConditionType {
  @JsonProperty("IN_SEGMENT") IN_SEGMENT,
  @JsonProperty("IN_SPLIT") IN_SPLIT,
  @JsonProperty("BOOLEAN") BOOLEAN,
  @JsonProperty("ON_DATE") ON_DATE,
  @JsonProperty("ON_OR_AFTER_DATE") ON_OR_AFTER_DATE,
  @JsonProperty("ON_OR_BEFORE_DATE") ON_OR_BEFORE_DATE,
  @JsonProperty("BETWEEN_DATE") BETWEEN_DATE,
  @JsonProperty("EQUAL_SET") EQUAL_SET,
  @JsonProperty("ANY_OF_SET") ANY_OF_SET,
  @JsonProperty("ALL_OF_SET") ALL_OF_SET,
  @JsonProperty("PART_OF_SET") PART_OF_SET,
  @JsonProperty("EQUAL_NUMBER") EQUAL_NUMBER,
  @JsonProperty("LESS_THAN_OR_EQUAL_NUMBER") LESS_THAN_OR_EQUAL_NUMBER,
  @JsonProperty("GREATER_THAN_OR_EQUAL_NUMBER") GREATER_THAN_OR_EQUAL_NUMBER,
  @JsonProperty("BETWEEN_NUMBER") BETWEEN_NUMBER,
  @JsonProperty("IN_LIST_STRING") IN_LIST_STRING,
  @JsonProperty("STARTS_WITH_STRING") STARTS_WITH_STRING,
  @JsonProperty("ENDS_WITH_STRING") ENDS_WITH_STRING,
  @JsonProperty("CONTAINS_STRING") CONTAINS_STRING,
  @JsonProperty("MATCHES_STRING") MATCHES_STRING,
  @JsonProperty("EQUAL_TO_SEMVER") EQUAL_TO_SEMVER,
  @JsonProperty("GREATER_THAN_OR_EQUAL_TO_SEMVER") GREATER_THAN_OR_EQUAL_TO_SEMVER,
  @JsonProperty("LESS_THAN_OR_EQUAL_TO_SEMVER") LESS_THAN_OR_EQUAL_TO_SEMVER,
  @JsonProperty("BETWEEN_SEMVER") BETWEEN_SEMVER,
  @JsonProperty("IN_LIST_SEMVER") IN_LIST_SEMVER
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.annotations.dev.HarnessTeam.FME;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.schema.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Represents a single rule within a condition.
 * Rules determine which users/requests match based on attributes, segments, or other criteria.
 */
@Value
@Builder
@Jacksonized
@OwnedBy(FME)
@RecasterAlias("io.harness.steps.fme.Rule")
public class Rule {
  /**
   * Type of condition (e.g., IN_SEGMENT, BOOLEAN, EQUAL_NUMBER, etc.).
   */
  @NotNull
  @YamlSchemaTypes(value = {runtime, expression})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  ParameterField<RuleConditionType> type;

  /**
   * Whether to negate the condition result.
   * Default is false.
   */
  @Nullable
  @YamlSchemaTypes(value = {runtime, expression})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  ParameterField<Boolean> negate;

  /**
   * Feature flag name (only required for IN_SPLIT type).
   * References another feature flag to check its evaluation result.
   */
  @Nullable
  @YamlSchemaTypes(value = {runtime, expression})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @JsonProperty("feature_flag")
  ParameterField<String> featureFlag;

  /**
   * Attribute name for attribute-based matchers.
   * The user/request attribute to evaluate (e.g., "email", "country", "version").
   */
  @Nullable
  @YamlSchemaTypes(value = {runtime, expression})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  ParameterField<String> attribute;

  /**
   * Value for the matcher.
   * Can be:
   * - Boolean (for BOOLEAN type)
   * - Number (for number comparisons)
   * - String (for string/date/semver comparisons)
   * - List of strings/numbers (for IN_LIST, ANY_OF, ALL_OF types)
   * - RangeValue object with from/to (for BETWEEN types)
   * - JEXL expression
   */
  @Nullable
  @YamlSchemaTypes(value = {runtime, expression})
  @ApiModelProperty(
      value = "Value for the matcher (polymorphic: can be boolean, number, string, list, or range object)")
  ParameterField<Object> value;
}

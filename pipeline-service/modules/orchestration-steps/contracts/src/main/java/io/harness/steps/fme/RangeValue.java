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

import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Represents a range value with 'from' and 'to' bounds.
 * Used for BETWEEN_* condition types (dates, numbers, semver).
 */
@Value
@Builder
@Jacksonized
@OwnedBy(FME)
@RecasterAlias("io.harness.steps.fme.RangeValue")
public class RangeValue {
  /**
   * Starting bound of the range (inclusive).
   */
  @NotNull
  @YamlSchemaTypes(value = {runtime, expression})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  ParameterField<String> from;

  /**
   * Ending bound of the range (inclusive).
   */
  @NotNull
  @YamlSchemaTypes(value = {runtime, expression})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  ParameterField<String> to;
}

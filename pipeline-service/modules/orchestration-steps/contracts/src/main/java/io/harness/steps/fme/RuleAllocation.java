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
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Represents traffic allocation to a specific treatment.
 * Defines what percentage of matched users receive a particular treatment/variation.
 */
@Value
@Builder
@Jacksonized
@OwnedBy(FME)
@RecasterAlias("io.harness.steps.fme.RuleAllocation")
public class RuleAllocation {
  /**
   * Treatment/variation identifier.
   * The treatment that matched users will receive.
   */
  @NotNull
  @YamlSchemaTypes(value = {runtime, expression})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  ParameterField<String> treatment;

  /**
   * Percentage allocation (0-100).
   * The percentage of matched users that should receive this treatment.
   * Total across all allocations in a rule should sum to 100.
   */
  @NotNull
  @Min(0)
  @Max(100)
  @YamlSchemaTypes(value = {runtime, expression})
  @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH)
  ParameterField<Integer> size;
}

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@OwnedBy(FME)
@RecasterAlias("io.harness.steps.fme.FmeSetBaselineTreatmentInstruction")
public class FmeSetBaselineTreatmentInstruction extends FmeDefinitionInstruction {
  @NotNull
  @YamlSchemaTypes(value = {runtime, expression})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  ParameterField<String> value;

  @Override
  public FmeInstructionType getType() {
    return FmeInstructionType.SetBaselineTreatment;
  }
}

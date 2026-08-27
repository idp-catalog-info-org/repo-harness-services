/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.fme;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Value
@Builder
@TypeAlias("fmeFlagDefaultAllocationParameters")
@RecasterAlias("io.harness.steps.fme.FmeFlagDefaultAllocationStepParameters")
public class FmeFlagDefaultAllocationStepParameters implements SpecParameters {
  String type = "FmeFlagDefaultAllocation";
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> flagName;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> environment;
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.FME_ALLOCATION_LIST_CLASSPATH)
  ParameterField<List<Allocation>> allocation;
}

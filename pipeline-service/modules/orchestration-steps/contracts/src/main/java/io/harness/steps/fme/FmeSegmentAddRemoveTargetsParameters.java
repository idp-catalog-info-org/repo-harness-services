/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
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
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Value
@Builder
@TypeAlias("fmeSegmentAddRemoveTargetsParameters")
@RecasterAlias("io.harness.steps.fme.FmeSegmentAddRemoveTargetsParameters")
public class FmeSegmentAddRemoveTargetsParameters implements SpecParameters {
  String type = "FmeSegmentAddRemoveTargets";

  @ApiModelProperty(required = true, value = "Segment name", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> segmentName;

  @ApiModelProperty(required = true, value = "FME environment ID", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> environment;

  @ApiModelProperty(value = "List of keys to add to the segment", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  ParameterField<List<String>> addKeys;

  @ApiModelProperty(
      value = "List of keys to remove from the segment", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Nullable
  ParameterField<List<String>> removeKeys;
}

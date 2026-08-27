/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.annotations.dev.HarnessTeam.FME;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.FME_FLAG_LIMIT_EXPOSURE)
@TypeAlias("FmeFlagLimitExposureNode")
@OwnedBy(FME)
@RecasterAlias("io.harness.plancreator.steps.internal.FmeFlagLimitExposureNode")
public class FmeFlagLimitExposureNode extends PmsAbstractStepNode {
  @JsonProperty("type") @NotNull FmeFlagLimitExposureNode.StepType type = StepType.FmeFlagLimitExposure;

  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  FmeFlagLimitExposureInfo fmeFlagLimitExposureInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.FME_FLAG_LIMIT_EXPOSURE_STEP_TYPE.getType();
  }

  @Override
  public StepSpecType getStepSpecType() {
    return fmeFlagLimitExposureInfo;
  }

  enum StepType {
    FmeFlagLimitExposure(StepSpecTypeConstants.FME_FLAG_LIMIT_EXPOSURE);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
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
@JsonTypeName(StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES)
@TypeAlias("FmeSegmentSetTargetingRulesNode")
@OwnedBy(FME)
@RecasterAlias("io.harness.plancreator.steps.internal.FmeSegmentSetTargetingRulesNode")
public class FmeSegmentSetTargetingRulesNode extends PmsAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.FmeSegmentSetTargetingRules;

  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  FmeSegmentSetTargetingRulesInfo fmeSegmentSetTargetingRulesInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES_STEP_TYPE.getType();
  }

  @Override
  public StepSpecType getStepSpecType() {
    return fmeSegmentSetTargetingRulesInfo;
  }

  enum StepType {
    FmeSegmentSetTargetingRules(StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}

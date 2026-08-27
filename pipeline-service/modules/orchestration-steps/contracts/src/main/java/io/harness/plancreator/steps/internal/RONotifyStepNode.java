/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
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
@JsonTypeName(StepSpecTypeConstants.RO_NOTIFY)
@TypeAlias("RONotifyStepNode")
@OwnedBy(HarnessTeam.PIPELINE)
@RecasterAlias("io.harness.plancreator.steps.internal.RONotifyStepNode")
public class RONotifyStepNode extends PmsAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.RONotify;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  RONotifyStepInfo roNotifyStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.RO_NOTIFY_STEP_TYPE.getType();
  }

  @Override
  public StepSpecType getStepSpecType() {
    return roNotifyStepInfo;
  }

  enum StepType {
    RONotify(StepSpecTypeConstants.RO_NOTIFY);
    @Getter String name;

    StepType(String name) {
      this.name = name;
    }
  }
}

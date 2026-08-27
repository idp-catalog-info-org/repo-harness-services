/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.steps.beans.stepnode;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.steps.beans.stepinfo.ActionStepInfo;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
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
@JsonTypeName(StepSpecTypeConstants.IDP_ACTION)
@TypeAlias("actionStepNode")
@OwnedBy(HarnessTeam.IDP)
@RecasterAlias("io.harness.steps.idp.action.step.ActionStepNode")
public class ActionStepNode extends PmsAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.IdpAction;

  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  ActionStepInfo actionStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.IDP_ACTION;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return actionStepInfo;
  }

  enum StepType {
    IdpAction(StepSpecTypeConstants.IDP_ACTION);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}

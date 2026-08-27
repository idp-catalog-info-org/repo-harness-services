/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.unified;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.plancreator.steps.resourceconstraint.ResourceConstraintStepInfo;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Value;

@Value
@JsonTypeName(StepSpecTypeConstants.RESOURCE_CONSTRAINT)
public class UnifiedResourceConstraintStepNode extends PmsAbstractStepNode {
  String type = StepSpecTypeConstants.RESOURCE_CONSTRAINT;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  ResourceConstraintStepInfo resourceConstraintStepInfo;

  @Override
  public StepSpecType getStepSpecType() {
    return resourceConstraintStepInfo;
  }
}

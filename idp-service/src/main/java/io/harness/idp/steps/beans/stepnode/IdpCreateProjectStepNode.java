/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.steps.beans.stepnode;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.idp.steps.Constants;
import io.harness.idp.steps.beans.stepinfo.IdpCreateProjectStepInfo;
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
@JsonTypeName(Constants.CREATE_PROJECT)
@TypeAlias(Constants.CREATE_PROJECT_STEP_NODE)
@OwnedBy(HarnessTeam.IDP)
@RecasterAlias("io.harness.idp.pipeline.steps.beans.stepNode.IdpCreateProjectStepNode")
public class IdpCreateProjectStepNode extends CIAbstractStepNode {
  @JsonProperty("type")
  private IdpCreateProjectStepNode.StepType type = IdpCreateProjectStepNode.StepType.CreateProject;

  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  private IdpCreateProjectStepInfo idpCreateProjectStepInfo;

  @Override
  public String getType() {
    return type.getName();
  }

  @Override
  public StepSpecType getStepSpecType() {
    return idpCreateProjectStepInfo;
  }

  enum StepType {
    CreateProject(CIStepInfoType.CREATE_PROJECT.getDisplayName());
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}

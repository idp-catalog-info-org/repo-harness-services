/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.steps.beans.stepinfo;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.idp.steps.beans.stepparameters.ActionStepParameters;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@JsonTypeName(StepSpecTypeConstants.IDP_ACTION)
@TypeAlias("actionStepInfo")
@RecasterAlias("io.harness.steps.idp.action.step.ActionStepInfo")
@OwnedBy(HarnessTeam.IDP)
public class ActionStepInfo implements PMSStepInfo {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> actionRef;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> actionVersion;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_MAP_CLASSPATH) ParameterField<Map<String, String>> inputs;

  @Builder(builderMethodName = "infoBuilder")
  public ActionStepInfo(ParameterField<String> actionRef, ParameterField<String> actionVersion,
      ParameterField<Map<String, String>> inputs) {
    this.actionRef = actionRef;
    this.actionVersion = actionVersion;
    this.inputs = inputs;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstants.IDP_ACTION_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return ActionStepParameters.builder().actionRef(actionRef).actionVersion(actionVersion).inputs(inputs).build();
  }
}

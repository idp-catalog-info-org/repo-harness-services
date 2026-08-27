/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.eventlistener;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonTypeName(StepSpecTypeConstants.EVENT_LISTENER)
@TypeAlias("eventListenerStepInfo")
@RecasterAlias("io.harness.steps.eventlistener.EventListenerStepInfo")
public class EventListenerStepInfo implements PMSStepInfo {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> webhookIdentifier;

  @VariableExpression(skipVariableExpression = true) List<NGVariable> outputVariables;

  List<NGVariable> inputVariables;

  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  private ParameterField<String> successCriteria;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> failureCriteria;
  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.EVENT_LISTENER_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return EventListenerStepParameters.builder()
        .webhookIdentifier(webhookIdentifier)
        .failureCriteria(failureCriteria)
        .successCriteria(successCriteria)
        .inputVariables(NGVariablesUtils.getMapOfVariables(inputVariables))
        .outputVariables(NGVariablesUtils.getMapOfVariables(outputVariables))
        .build();
  }

  @Override
  public ExpressionMode getExpressionMode() {
    return ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;
  }
}

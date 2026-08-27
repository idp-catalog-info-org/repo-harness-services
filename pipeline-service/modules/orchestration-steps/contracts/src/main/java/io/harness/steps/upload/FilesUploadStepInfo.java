/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.upload;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.walktree.visitor.helper.Visitable;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@JsonTypeName(StepSpecTypeConstants.UPLOAD)
@OwnedBy(PIPELINE)
@TypeAlias("FilesUploadStepInfo")
@RecasterAlias("io.harness.steps.upload.FilesUploadStepInfo")
public class FilesUploadStepInfo implements PMSStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  List<NGVariable> inputVariables;

  @VariableExpression(skipVariableExpression = true) List<NGVariable> outputVariables;

  @Builder(builderMethodName = "infoBuilder")
  public FilesUploadStepInfo(String uuid, List<NGVariable> outputVariables, List<NGVariable> inputVariables) {
    this.uuid = uuid;
    this.outputVariables = outputVariables;
    this.inputVariables = inputVariables;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstants.UPLOAD_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return FilesUploadStepParameters.infoBuilder()
        .outputVariables(NGVariablesUtils.getMapOfVariablesWithoutSecretExpression(outputVariables))
        .inputVariables(NGVariablesUtils.getMapOfVariables(inputVariables))
        .build();
  }
}
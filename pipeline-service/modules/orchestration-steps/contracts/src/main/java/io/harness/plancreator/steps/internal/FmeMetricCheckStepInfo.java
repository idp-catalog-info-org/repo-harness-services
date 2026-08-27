/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeFailureCriteria;
import io.harness.steps.fme.FmeMetricCheckStepParameters;
import io.harness.steps.fme.FmeMetricRef;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.FME_METRIC_CHECK)
@TypeAlias("fmeMetricCheckStepInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.FmeMetricCheckStepInfo")
public class FmeMetricCheckStepInfo implements PMSStepInfo {
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> flagName;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> environment;
  List<FmeMetricRef> metrics;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> lookbackWindow;
  @NotNull FmeFailureCriteria failureCriteria;

  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Builder
  @ConstructorProperties({"flagName", "environment", "metrics", "lookbackWindow", "failureCriteria"})
  public FmeMetricCheckStepInfo(ParameterField<String> flagName, ParameterField<String> environment,
      List<FmeMetricRef> metrics, ParameterField<String> lookbackWindow, FmeFailureCriteria failureCriteria) {
    this.flagName = flagName;
    this.environment = environment;
    this.metrics = metrics;
    this.lookbackWindow = lookbackWindow;
    this.failureCriteria = failureCriteria;
  }

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.FME_METRIC_CHECK_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return FmeMetricCheckStepParameters.builder()
        .flagName(flagName)
        .environment(environment)
        .metrics(metrics)
        .lookbackWindow(lookbackWindow)
        .failureCriteria(failureCriteria)
        .build();
  }
}

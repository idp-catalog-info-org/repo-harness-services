/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

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
import io.harness.steps.fme.FmeSegmentSetTargetingRulesParameters;
import io.harness.steps.fme.SegmentTargetRules;
import io.harness.walktree.visitor.helper.Visitable;
import io.harness.yaml.schema.YamlSchemaTypes;

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
@JsonTypeName(StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES)
@TypeAlias("fmeSegmentSetTargetingRulesInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.FmeSegmentSetTargetingRulesInfo")
public class FmeSegmentSetTargetingRulesInfo implements PMSStepInfo, Visitable {
  @ApiModelProperty(required = true, value = "Segment name", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<String> segmentName;

  @ApiModelProperty(required = true, value = "FME environment ID", dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<String> environment;

  @ApiModelProperty(value = "List of segment targeting rules with structured conditions")
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<SegmentTargetRules>> rules;

  @ApiModelProperty(value = "List of keys to exclude", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<String>> excludeKeys;

  @ApiModelProperty(value = "List of segment names to exclude", dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<List<String>> excludeSegments;

  @ApiModelProperty(value = "Optional comment", dataType = SwaggerConstants.STRING_CLASSPATH)
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<String> comment;

  @ApiModelProperty(value = "Optional title", dataType = SwaggerConstants.STRING_CLASSPATH)
  @YamlSchemaTypes(value = {runtime, expression})
  ParameterField<String> title;

  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Builder
  @ConstructorProperties({"segmentName", "environment", "rules", "excludeKeys", "excludeSegments", "comment", "title"})
  public FmeSegmentSetTargetingRulesInfo(ParameterField<String> segmentName, ParameterField<String> environment,
      ParameterField<List<SegmentTargetRules>> rules, ParameterField<List<String>> excludeKeys,
      ParameterField<List<String>> excludeSegments, ParameterField<String> comment, ParameterField<String> title) {
    this.segmentName = segmentName;
    this.environment = environment;
    this.rules = rules;
    this.excludeKeys = excludeKeys;
    this.excludeSegments = excludeSegments;
    this.comment = comment;
    this.title = title;
  }

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.FME_SEGMENT_SET_TARGETING_RULES_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return FmeSegmentSetTargetingRulesParameters.builder()
        .segmentName(segmentName)
        .environment(environment)
        .rules(rules)
        .excludeKeys(excludeKeys)
        .excludeSegments(excludeSegments)
        .comment(comment)
        .title(title)
        .build();
  }
}

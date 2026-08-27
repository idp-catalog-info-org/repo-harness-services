/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.email;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.email.EmailStepParameters;
import io.harness.walktree.visitor.helper.SimpleVisitorHelper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.schema.YamlSchemaTypes;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@JsonTypeName(StepSpecTypeConstants.EMAIL)
@SimpleVisitorHelper(helperClass = EmailStepInfoVisitorHelper.class)
@TypeAlias("emailStepInfo")
@RecasterAlias("io.harness.plancreator.steps.email.EmailStepInfo")
@OwnedBy(CDC)
public class EmailStepInfo implements PMSStepInfo {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> to;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> cc;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH) ParameterField<List<String>> toUserGroups;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH) ParameterField<List<String>> ccUserGroups;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> subject;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> body;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {expression})
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  @YamlSchemaTypes(value = {runtime, expression}) ParameterField<List<NGVariable>> inputVariables;
  Boolean fireAndForget;

  @Builder(builderMethodName = "infoBuilder")
  public EmailStepInfo(ParameterField<String> to, ParameterField<String> cc, ParameterField<List<String>> toUserGroups,
      ParameterField<List<String>> ccUserGroups, ParameterField<String> subject, ParameterField<String> body,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, ParameterField<List<NGVariable>> inputVariables,
      Boolean fireAndForget) {
    this.to = to;
    this.cc = cc;
    this.toUserGroups = toUserGroups;
    this.ccUserGroups = ccUserGroups;
    this.subject = subject;
    this.body = body;
    this.delegateSelectors = delegateSelectors;
    this.inputVariables = inputVariables;
    this.fireAndForget = fireAndForget;
  }
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstants.EMAIL_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return EmailStepParameters.builder()
        .subject(subject)
        .body(body)
        .cc(cc)
        .delegateSelectors(delegateSelectors)
        .to(to)
        .toUserGroups(toUserGroups)
        .ccUserGroups(ccUserGroups)
        .inputVariables(inputVariables == null
                ? null
                : ParameterField.createValueField(NGVariablesUtils.getMapOfVariables((inputVariables.obtainValue()))))
        .fireAndForget(fireAndForget)
        .build();
  }
}

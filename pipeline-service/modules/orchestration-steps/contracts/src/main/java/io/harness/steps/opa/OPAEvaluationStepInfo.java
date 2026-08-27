/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.walktree.visitor.helper.SimpleVisitorHelper;
import io.harness.walktree.visitor.helper.Visitable;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SimpleVisitorHelper(helperClass = OPAEvaluationStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.OPA_EVALUATION)
@TypeAlias("opaEvaluationStepInfo")
@RecasterAlias("io.harness.steps.opa.OPAEvaluationStepInfo")

public class OPAEvaluationStepInfo extends OPAEvaluationBaseStepInfo implements PMSStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> policySetId;

  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> evaluationId;

  @ApiModelProperty(dataType = STRING_CLASSPATH)
  ParameterField<String> policySetOrgId; // Org ID where the policy set exists (can be null for account-level)

  @ApiModelProperty(dataType = STRING_CLASSPATH)
  ParameterField<String>
      policySetProjectId; // Project ID where the policy set exists (can be null for account/org-level)

  @Builder(builderMethodName = "infoBuilder")
  public OPAEvaluationStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors, ParameterField<String> image,
      ParameterField<String> connectorRef, ContainerResource resources,
      ParameterField<Map<String, String>> envVariables, ParameterField<Boolean> privileged,
      ParameterField<Integer> runAsUser, ParameterField<ImagePullPolicy> imagePullPolicy,
      ParameterField<String> policySetId, ParameterField<String> evaluationId, ParameterField<String> policySetOrgId,
      ParameterField<String> policySetProjectId) {
    super(delegateSelectors, image, connectorRef, resources, envVariables, privileged, runAsUser, imagePullPolicy);
    this.policySetId = policySetId;
    this.evaluationId = evaluationId;
    this.policySetOrgId = policySetOrgId;
    this.policySetProjectId = policySetProjectId;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstants.OPA_EVALUATION_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return OPAEvaluationStepParameters.infoBuilder()
        .delegateSelectors(getDelegateSelectors())
        .image(getImage())
        .resources(getResources())
        .connectorRef(getConnectorRef())
        .privileged(getPrivileged())
        .runAsUser(getRunAsUser())
        .imagePullPolicy(getImagePullPolicy())
        .envVariables(getEnvVariables())
        .policySetId(getPolicySetId())
        .evaluationId(getEvaluationId())
        .policySetOrgId(getPolicySetOrgId())
        .policySetProjectId(getPolicySetProjectId())
        .build();
  }

  @Override
  public ExpressionMode getExpressionMode() {
    return ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED;
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.http.v1;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.filters.WithSecretRef;
import io.harness.http.HttpHeaderConfig;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.walktree.visitor.helper.Visitable;
import io.harness.yaml.core.variables.v1.NGVariableV1Wrapper;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

@Value
@JsonTypeName(StepSpecTypeConstantsV1.HTTP)
@OwnedBy(HarnessTeam.PIPELINE)
@RecasterAlias("io.harness.steps.http.v1.HttpStepInfoV1")
public class HttpStepInfoV1
    extends HttpBaseStepInfoV1 implements Visitable, PMSStepInfo, WithDelegateSelector, WithSecretRef {
  NGVariableV1Wrapper output_vars;
  NGVariableV1Wrapper input_vars;
  List<HttpHeaderConfig> headers;
  ParameterField<String> cert;
  ParameterField<String> cert_key;

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstantsV1.HTTP_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public ExpressionMode getExpressionMode() {
    return ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegates();
  }

  @Override
  public void setDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegates) {
    setDelegates(delegates);
  }

  @Override
  public Map<String, ParameterField<String>> extractSecretRefs() {
    Map<String, ParameterField<String>> secretRefMap = new HashMap<>();
    if (ParameterField.isNotBlank(cert)) {
      secretRefMap.put(YAMLFieldNameConstants.CERTIFICATE_V1, cert);
    }
    if (ParameterField.isNotBlank(cert_key)) {
      secretRefMap.put(YAMLFieldNameConstants.CERTIFICATE_KEY_V1, cert_key);
    }
    return secretRefMap;
  }
}

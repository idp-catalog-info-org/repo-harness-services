/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.email.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Value
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.email.v1.EmailStepParameters")
public class EmailStepParameters implements SpecParameters {
  ParameterField<String> to;
  ParameterField<String> cc;
  ParameterField<String> subject;
  ParameterField<String> body;
  ParameterField<List<TaskSelectorYaml>> delegates;
  Boolean fireAndForget;

  @Override
  public String getVersion() {
    return HarnessYamlVersion.V1;
  }

  @Builder(builderMethodName = "infoBuilder")
  public EmailStepParameters(ParameterField<String> to, ParameterField<String> cc, ParameterField<String> subject,
      ParameterField<String> body, ParameterField<List<TaskSelectorYaml>> delegates, Boolean fireAndForget) {
    this.to = to;
    this.cc = cc;
    this.subject = subject;
    this.body = body;
    this.delegates = delegates;
    this.fireAndForget = fireAndForget;
  }

  public io.harness.steps.email.EmailStepParameters toEmailStepParametersV0() {
    return io.harness.steps.email.EmailStepParameters.builder()
        .body(body)
        .cc(cc)
        .to(to)
        .subject(subject)
        .delegateSelectors(delegates)
        .fireAndForget(fireAndForget)
        .build();
  }
}

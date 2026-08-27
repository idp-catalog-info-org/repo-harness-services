/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.changeadvisor;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.approval.step.harness.beans.Approvers;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@TypeAlias("changeAdvisorStepSpecParameters")
@RecasterAlias("io.harness.steps.changeadvisor.ChangeAdvisorStepSpecParameters")
public class ChangeAdvisorStepSpecParameters implements SpecParameters {
  ParameterField<String> mode;
  ParameterField<String> policyPack;
  ParameterField<Integer> timeoutMinutes;
  ParameterField<List<String>> presets;
  ParameterField<String> env;
  Approvers approvers;
}

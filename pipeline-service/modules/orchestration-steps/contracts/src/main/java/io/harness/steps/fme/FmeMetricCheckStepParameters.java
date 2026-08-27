/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.FME)
@Value
@Builder
@TypeAlias("fmeMetricCheckStepParameters")
@RecasterAlias("io.harness.steps.fme.FmeMetricCheckStepParameters")
public class FmeMetricCheckStepParameters implements SpecParameters {
  String type = "FmeMetricCheck";

  @NotNull ParameterField<String> flagName;
  @NotNull ParameterField<String> environment;
  List<FmeMetricRef> metrics;
  @NotNull ParameterField<String> lookbackWindow;
  @NotNull FmeFailureCriteria failureCriteria;
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.steps.beans.stepparameters;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.IDP)
@Value
@Builder
@TypeAlias("actionStepParameters")
@RecasterAlias("io.harness.steps.idp.action.step.ActionStepParameters")
public class ActionStepParameters implements SpecParameters {
  ParameterField<String> actionRef;

  // Named actionVersion rather than version because SpecParameters defines a
  // default getVersion(): String that cannot be overridden with a wrapped type.
  ParameterField<String> actionVersion;

  ParameterField<Map<String, String>> inputs;
}

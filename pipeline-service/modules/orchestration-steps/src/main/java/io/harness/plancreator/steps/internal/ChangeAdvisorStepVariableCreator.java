/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class ChangeAdvisorStepVariableCreator extends GenericStepVariableCreator<ChangeAdvisorStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.CHANGE_ADVISOR);
  }

  @Override
  public Class<ChangeAdvisorStepNode> getFieldClass() {
    return ChangeAdvisorStepNode.class;
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filters.v1.GenericStepPMSFilterJsonCreatorV3;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepSpecTypeConstantsV1;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(PIPELINE)
public class PmsStepFilterJsonCreatorV3 extends GenericStepPMSFilterJsonCreatorV3 {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstantsV1.HTTP, StepSpecTypeConstantsV1.SHELL_SCRIPT,
        StepSpecTypeConstantsV1.WAIT_STEP, StepSpecTypeConstantsV1.JIRA_APPROVAL,
        StepSpecTypeConstantsV1.HARNESS_APPROVAL, StepSpecTypeConstantsV1.CUSTOM_APPROVAL,
        StepSpecTypeConstantsV1.SERVICENOW_APPROVAL, StepSpecTypeConstantsV1.QUEUE, StepSpecTypeConstantsV1.EMAIL,
        StepSpecTypeConstantsV1.CHANGE_ADVISOR, StepSpecTypeConstantsV1.BARRIER,
        YAMLFieldNameConstants.UNIFIED_MANUAL_APPROVAL, YAMLFieldNameConstants.UNIFIED_CUSTOM_APPROVAL,
        YAMLFieldNameConstants.UNIFIED_POLICY, YAMLFieldNameConstants.UNIFIED_SERVICENOW_APPROVAL,
        YAMLFieldNameConstants.UNIFIED_JIRA_APPROVAL);
  }
}

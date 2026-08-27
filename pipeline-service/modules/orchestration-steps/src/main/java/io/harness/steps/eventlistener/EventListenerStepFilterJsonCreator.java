/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.eventlistener;

import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;

public class EventListenerStepFilterJsonCreator extends GenericStepPMSFilterJsonCreatorV2 {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.EVENT_LISTENER);
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    return super.handleNode(filterCreationContext, yamlField);
  }
}

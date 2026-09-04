/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.plan.creator;

import static io.harness.beans.FeatureName.CI_SECRET_EXPRESSION_REFERENCES;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.execution.plan.creator.filter.CISecretExpressionExtractor;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.sto.plan.creator.step.STOStepFilterJsonCreatorV2;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CI)
public class STOSecretAwareStepFilterJsonCreator extends STOStepFilterJsonCreatorV2 {
  @Inject private CIFeatureFlagService ciFeatureFlagService;

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    FilterCreationResponse response = super.handleNode(filterCreationContext, yamlField);
    if (ciFeatureFlagService.isEnabled(
            CI_SECRET_EXPRESSION_REFERENCES, filterCreationContext.getSetupMetadata().getAccountId())) {
      response.addReferredEntities(CISecretExpressionExtractor.extract(filterCreationContext));
    }
    return response;
  }
}

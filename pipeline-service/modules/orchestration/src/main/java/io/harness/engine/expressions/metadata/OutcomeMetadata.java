/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.metadata;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
public class OutcomeMetadata {
  PmsOutcomeService pmsOutcomeService;
  String planExecutionId;

  public OutcomeMetadata(PmsOutcomeService pmsOutcomeService, String planExecutionId) {
    this.pmsOutcomeService = pmsOutcomeService;
    this.planExecutionId = planExecutionId;
  }

  /**
   * Efficiently checks if an outcome with the given name exists for the current planExecutionId
   * without loading all outcome names.
   */
  public boolean existsOutcomeName(String name) {
    return pmsOutcomeService.existsOutcomeName(planExecutionId, name);
  }
}

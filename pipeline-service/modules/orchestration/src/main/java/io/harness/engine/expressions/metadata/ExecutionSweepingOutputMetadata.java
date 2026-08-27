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
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;

import java.util.Collections;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
public class ExecutionSweepingOutputMetadata {
  private volatile List<String> existingOutputNames;
  private volatile List<String> existingOutputNamesForRollbackMode;
  PmsSweepingOutputService pmsSweepingOutputService;
  String planExecutionId;

  // in case of rollback mode
  String originalPlanExecutionIdForRollbackMode;

  public ExecutionSweepingOutputMetadata(PmsSweepingOutputService pmsSweepingOutputService, String planExecutionId,
      String originalPlanExecutionIdForRollbackMode) {
    this.pmsSweepingOutputService = pmsSweepingOutputService;
    this.planExecutionId = planExecutionId;
    this.originalPlanExecutionIdForRollbackMode = originalPlanExecutionIdForRollbackMode;
  }

  public List<String> getExistingOutputNames() {
    if (existingOutputNames == null) {
      synchronized (this) {
        if (existingOutputNames == null) {
          existingOutputNames = pmsSweepingOutputService.fetchNameOfOutcomesInPlanExecutionId(planExecutionId);
        }
      }
    }
    return existingOutputNames;
  }

  public List<String> getOutputNamesForRollbackMode() {
    if (existingOutputNamesForRollbackMode == null) {
      synchronized (this) {
        if (existingOutputNamesForRollbackMode == null) {
          if (EmptyPredicate.isEmpty(originalPlanExecutionIdForRollbackMode)) {
            existingOutputNamesForRollbackMode = Collections.emptyList();
          } else {
            existingOutputNamesForRollbackMode =
                pmsSweepingOutputService.fetchNameOfOutcomesInPlanExecutionId(originalPlanExecutionIdForRollbackMode);
          }
        }
      }
    }
    return existingOutputNamesForRollbackMode;
  }
}

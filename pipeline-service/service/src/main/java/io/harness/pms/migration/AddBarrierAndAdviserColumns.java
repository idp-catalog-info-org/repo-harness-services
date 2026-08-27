/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.migration.postgres.NGAbstractPostgresMigration;

/**
 * Migration to add missing CDC graph columns:
 * - has_barrier_child: marks stage nodes containing barrier steps (for GraphLayoutNodeDTO.barrierFound)
 * - adviser_response: stores AdviserResponse protobuf (for GraphVertexDTO.manualInterventionAvailableActions)
 * - children_count: count of direct children for container nodes (for GraphVertexDTO.childrenCount)
 *   Uses DEFAULT NULL to distinguish "not yet populated by CDC" from "genuinely 0 children".
 *   Pre-migration rows remain NULL until touched by CDC; mapper returns 0 for NULL.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class AddBarrierAndAdviserColumns extends NGAbstractPostgresMigration {
  @Override
  public String getFileName() {
    return "timescale/add_barrier_and_adviser_columns.sql";
  }

  @Override
  public boolean runInTransaction() {
    return true;
  }
}

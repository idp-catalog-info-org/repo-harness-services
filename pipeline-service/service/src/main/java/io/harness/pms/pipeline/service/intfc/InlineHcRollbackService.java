/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.pipeline.ConsolidatedRollbackResponse;
import io.harness.pms.pipeline.InlineHcMigrationEntityType;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface InlineHcRollbackService {
  /**
   * Rollback entities with storeType INLINE_HC to storeType INLINE
   *
   * @param accountIdentifier The account identifier
   * @param entityType The type of entity to rollback (PIPELINE, INPUT_SET, ALL)
   * @return Response containing the number of entities migrated per type
   */
  ConsolidatedRollbackResponse rollbackFromInlineHCToInline(
      String accountIdentifier, InlineHcMigrationEntityType entityType);
}

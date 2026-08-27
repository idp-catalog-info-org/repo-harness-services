/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationEntity;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationMonitorEntity;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity;

import java.util.Set;

@OwnedBy(PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH, HarnessModuleComponent.CDS_DATA_RETENTION})
public class RetentionMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(PipelineSearchIndexMigrationEntity.class);
    set.add(ExecutionRetentionReconciliationEntity.class);
    set.add(ExecutionRetentionMetadata.class);
    set.add(ExecutionRetentionReconciliationMonitorEntity.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {}
}

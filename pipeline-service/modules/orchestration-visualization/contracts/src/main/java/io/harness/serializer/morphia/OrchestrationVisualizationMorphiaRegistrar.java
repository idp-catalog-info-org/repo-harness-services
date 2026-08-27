/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.app.beans.entities.CIManagerUniqueIdParentIdMigrationStatus;
import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.app.beans.entities.ExecutionQueueLimit;
import io.harness.app.beans.entities.PluginMetadataConfig;
import io.harness.app.beans.entities.PluginMetadataStatus;
import io.harness.beans.GraphDeleteEvent;
import io.harness.ci.beans.entities.BuildNumberDetails;
import io.harness.ci.beans.entities.CIBuild;
import io.harness.ci.beans.entities.CIBuildImageVmConfig;
import io.harness.ci.beans.entities.CIExecutionConfig;
import io.harness.ci.beans.entities.CITelemetrySentStatus;
import io.harness.ci.beans.entities.PipelineModuleInfoEntity;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import java.util.Set;

public class OrchestrationVisualizationMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(PipelineExecutionSummaryEntity.class);
    set.add(GraphUpdateInfo.class);
    set.add(GraphDeleteEvent.class);
    set.add(CIExecutionConfig.class);
    set.add(CITelemetrySentStatus.class);
    set.add(PluginMetadataConfig.class);
    set.add(CIBuild.class);
    set.add(BuildNumberDetails.class);
    set.add(PluginMetadataStatus.class);
    set.add(PipelineModuleInfoEntity.class);
    set.add(CIBuildImageVmConfig.class);
    set.add(CIManagerUniqueIdParentIdMigrationStatus.class);
    set.add(ExecutionQueueLimit.class);
    set.add(CIResourceCleanup.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {}
}

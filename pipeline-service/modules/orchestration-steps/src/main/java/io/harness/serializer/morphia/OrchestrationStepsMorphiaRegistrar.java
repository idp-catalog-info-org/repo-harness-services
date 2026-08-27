/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.CIManagerUniqueIdParentIdMigrationStatus;
import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.EnvironmentGroupEntity;
import io.harness.app.beans.entities.ExecutionQueueLimit;
import io.harness.app.beans.entities.InfrastructureEntity;
import io.harness.app.beans.entities.PluginMetadataConfig;
import io.harness.app.beans.entities.PluginMetadataStatus;
import io.harness.app.beans.entities.ServiceEntity;
import io.harness.app.beans.entities.StageRollbackData;
import io.harness.app.beans.entities.StepExecutionParameters;
import io.harness.ci.beans.entities.BuildNumberDetails;
import io.harness.ci.beans.entities.CIBuild;
import io.harness.ci.beans.entities.CIBuildImageVmConfig;
import io.harness.ci.beans.entities.CIExecutionConfig;
import io.harness.ci.beans.entities.CITelemetrySentStatus;
import io.harness.ci.beans.entities.PipelineModuleInfoEntity;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.entities.ServiceNowApprovalInstance;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.upload.RuntimeFileInputData;

import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationStepsMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(BarrierExecutionInstance.class);
    set.add(ResourceRestraint.class);
    set.add(ResourceRestraintInstance.class);
    set.add(ApprovalInstance.class);
    set.add(HarnessApprovalInstance.class);
    set.add(JiraApprovalInstance.class);
    set.add(ServiceNowApprovalInstance.class);
    set.add(CustomApprovalInstance.class);
    set.add(RuntimeFileInputData.class);
    set.add(EventListenerStepInstance.class);
    set.add(EnvironmentGroupEntity.class);
    set.add(InfrastructureEntity.class);
    set.add(BuildNumberDetails.class);
    set.add(StepExecutionParameters.class);
    set.add(PluginMetadataStatus.class);
    set.add(ExecutionQueueLimit.class);
    set.add(CIResourceCleanup.class);
    set.add(EnvironmentEntity.class);
    set.add(CITelemetrySentStatus.class);
    set.add(PluginMetadataConfig.class);
    set.add(CIBuild.class);
    set.add(ServiceEntity.class);
    set.add(PipelineModuleInfoEntity.class);
    set.add(CIBuildImageVmConfig.class);
    set.add(CIExecutionConfig.class);
    set.add(StageRollbackData.class);
    set.add(CIManagerUniqueIdParentIdMigrationStatus.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {}
}

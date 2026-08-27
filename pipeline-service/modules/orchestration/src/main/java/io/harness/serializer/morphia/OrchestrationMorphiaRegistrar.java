/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
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
import io.harness.beans.steps.output.CIStageOutput;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.EcsStageInfraDetails;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.ci.beans.entities.BuildNumberDetails;
import io.harness.ci.beans.entities.CIBuild;
import io.harness.ci.beans.entities.CIBuildImageVmConfig;
import io.harness.ci.beans.entities.CIExecutionConfig;
import io.harness.ci.beans.entities.CITelemetrySentStatus;
import io.harness.ci.beans.entities.PipelineModuleInfoEntity;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity;
import io.harness.engine.expressions.usages.beans.ExpressionUsagesEntity;
import io.harness.engine.interrupts.AbortInterruptCallback;
import io.harness.engine.pms.resume.callback.resume.EngineResumeCallback;
import io.harness.engine.pms.resume.callback.resumeall.EngineResumeAllCallback;
import io.harness.engine.pms.resume.callback.waitretry.EngineWaitRetryCallback;
import io.harness.engine.pms.resume.callback.waitretry.v2.EngineWaitRetryCallbackV2;
import io.harness.engine.progress.EngineProgressCallback;
import io.harness.entity.accountoverrides.DataRetentionEntity;
import io.harness.entity.eventlog.NotificationEventLog;
import io.harness.entity.eventlog.OrchestrationEventLog;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerFailureNotificationDetailsEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;

import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
public class OrchestrationMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(OrchestrationEventLog.class);
    set.add(ExpressionUsagesEntity.class);
    set.add(ExecutionExpressionUsagesEntity.class);
    set.add(DataRetentionEntity.class);
    set.add(NotificationEventLog.class);
    set.add(K8StageInfraDetails.class);
    set.add(EcsStageInfraDetails.class);
    set.add(StageDetails.class);
    set.add(ContextElement.class);
    set.add(VmStageInfraDetails.class);
    set.add(K8PodDetails.class);
    set.add(StageInfraDetails.class);
    set.add(CIStageOutput.class);
    set.add(DliteVmStageInfraDetails.class);
    set.add(TriggerCustomWebhookEvent.class);
    set.add(TriggerEventHistory.class);
    set.add(TriggerWebhookEvent.class);
    set.add(NGTriggerEntity.class);
    set.add(TriggerFailureNotificationDetailsEntity.class);
    set.add(EnvironmentGroupEntity.class);
    set.add(InfrastructureEntity.class);
    set.add(EnvironmentEntity.class);
    set.add(ServiceEntity.class);
    set.add(StepExecutionParameters.class);
    set.add(StageRollbackData.class);
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
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // Engine Callback
    h.put("engine.resume.EngineResumeAllCallback", EngineResumeAllCallback.class);
    h.put("engine.resume.EngineResumeCallback", EngineResumeCallback.class);
    h.put("engine.resume.EngineWaitRetryCallback", EngineWaitRetryCallback.class);
    h.put("engine.resume.EngineWaitRetryCallbackV2", EngineWaitRetryCallbackV2.class);
    h.put("engine.progress.EngineProgressCallback", EngineProgressCallback.class);
    h.put("engine.interrupts.InterruptCallback", AbortInterruptCallback.class);
  }
}

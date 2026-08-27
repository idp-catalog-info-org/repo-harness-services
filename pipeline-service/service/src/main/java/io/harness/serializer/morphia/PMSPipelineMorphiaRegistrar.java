/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.ExecutionOutboxEvent;
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
import io.harness.pms.annotations.PipelineAnnotationEntity;
import io.harness.pms.approval.custom.CustomApprovalCallback;
import io.harness.pms.approval.jira.JiraApprovalCallback;
import io.harness.pms.approval.servicenow.ServiceNowApprovalCallback;
import io.harness.pms.conversion.beans.ConversionChecksum;
import io.harness.pms.conversion.beans.ConversionJobEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.migration.InputSetConnectorBackfillMigrationStatus;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaGitxStatusEntity;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineMetadata;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.branchsequence.PipelineBranchSequence;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.yamlConversion.PipelineYamlConversionEntity;
import io.harness.pms.pipelinedelete.beans.entity.PipelineDeleteProcessorIteratorEntity;
import io.harness.pms.preflight.entity.PreFlightEntity;
import io.harness.projectmovement.mongo.PipelineCDCEntitiesMigrationStatus;
import io.harness.projectmovement.mongo.PipelineUniqueIdParentIdMigrationStatus;
import io.harness.provider.entity.ProviderEntity;

import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSPipelineMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(PipelineEntity.class);
    set.add(PipelineOpaGitxStatusEntity.class);
    set.add(PipelineAnnotationEntity.class);
    set.add(InputSetEntity.class);
    set.add(InputSetConnectorBackfillMigrationStatus.class);
    set.add(PreFlightEntity.class);
    set.add(PipelineMetadata.class);
    set.add(PipelineMetadataV2.class);
    set.add(PipelineValidationEvent.class);
    set.add(PipelineBranchSequence.class);
    set.add(ConversionJobEntity.class);
    set.add(ConversionChecksum.class);
    set.add(ExecutionOutboxEvent.class);
    set.add(PipelineDeleteProcessorIteratorEntity.class);
    set.add(ProviderEntity.class);
    set.add(PipelineUniqueIdParentIdMigrationStatus.class);
    set.add(PipelineCDCEntitiesMigrationStatus.class);
    set.add(EnvironmentGroupEntity.class);
    set.add(InfrastructureEntity.class);
    set.add(StepExecutionParameters.class);
    set.add(PluginMetadataStatus.class);
    set.add(ExecutionQueueLimit.class);
    set.add(CIResourceCleanup.class);
    set.add(EnvironmentEntity.class);
    set.add(PluginMetadataConfig.class);
    set.add(ServiceEntity.class);
    set.add(CIExecutionConfig.class);
    set.add(BuildNumberDetails.class);
    set.add(CITelemetrySentStatus.class);
    set.add(CIBuild.class);
    set.add(PipelineModuleInfoEntity.class);
    set.add(CIBuildImageVmConfig.class);
    set.add(StageRollbackData.class);
    set.add(CIManagerUniqueIdParentIdMigrationStatus.class);
    set.add(PipelineYamlConversionEntity.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    h.put("pms.approval.jira", JiraApprovalCallback.class);
    h.put("pms.approval.servicenow", ServiceNowApprovalCallback.class);
    h.put("pms.approval.custom", CustomApprovalCallback.class);
  }
}

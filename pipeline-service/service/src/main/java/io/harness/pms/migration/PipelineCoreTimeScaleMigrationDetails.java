/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.migration.beans.MigrationDetails;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.beans.NGMigration;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineCoreTimeScaleMigrationDetails implements MigrationDetails {
  @Override
  public MigrationType getMigrationTypeName() {
    return MigrationType.TimeScaleMigration;
  }

  @Override
  public boolean isBackground() {
    return false;
  }

  @Override
  public List<Pair<Integer, Class<? extends NGMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends NGMigration>>>()
        .add(Pair.of(1, CreateTimescaleCDCTablesWhereNotExist.class))
        .add(Pair.of(2, UpdateTimescalePipelineExecutionSummary.class))
        .add(Pair.of(3, UpdateTimescaleCIPipelineExecutionSummary.class))
        .add(Pair.of(4, UpdateTimescaleTablePipelineExecutionSummaryCd.class))
        .add(Pair.of(5, UpdateTimescaleTableCIWithTriggerInfo.class))
        .add(Pair.of(6, UpdateTsCIWithPR.class))
        .add(Pair.of(7, UpdateTsCIWithIsRepoPrivate.class))
        .add(Pair.of(8, AddInfrastructureIdentifierInServiceInfraInfoTable.class))
        .add(Pair.of(9, AddGitOpsEnabledInServiceInfraInfoTable.class))
        .add(Pair.of(10, AddInfrastructureNameInServiceInfraInfoTable.class))
        .add(Pair.of(11, CreateTimeScalePipelineExecutionSummary.class))
        .add(Pair.of(12, AddEnvGroupInServiceInfraInfoTable.class))
        .add(Pair.of(13, AddNewTableForRevertExecutionsRevertColumns.class))
        .add(Pair.of(14, AddArtifactDisplayNameToServiceInfraInfoTable.class))
        .add(Pair.of(15, UpdatePipelineExecutionSummaryCdTimescaleTable.class))
        .add(Pair.of(16, AddExecutionFailureDetailsToServiceInfraInfoTable.class))
        .add(Pair.of(17, CreateCustomStageTimeScaleTable.class))
        .add(Pair.of(18, CreateStepExecutionsTimeScaleTable.class))
        .add(Pair.of(19, CreateIndexForPipelineExecutionSummaryMean.class))
        .add(Pair.of(20, AddDeletedAtColumnsToPipelineTable.class))
        .add(Pair.of(21, CreateIndexOnAccIdOrgIdProjIdIdDeletedDeletedAtForPipelineTable.class))
        .add(Pair.of(22, AddIndexesOnTriggerIdToPipelineExecutionSummaryCDTable.class))
        .add(Pair.of(23, AddIndexesOnPlanExecIdToPipelineExecutionSummaryCDTable.class))
        .add(Pair.of(24, AddIndexesOnAccIdOrgIdProjIdPipeIdToPipelineExecutionSummaryCDTable.class))
        .add(Pair.of(25, AddIndexesStepExecutionInfoTable.class))
        .add(Pair.of(26, AddIndexOnOrgIdProjectIdToPipelineExecutionSummaryTable.class))
        .add(Pair.of(27, AddCreatedAtUpdatedAtColumnsToPipelineCoreTable.class))
        .add(Pair.of(28, AddUniqueIdAndParentUniqueIdToPipelineExecutionSummaryTable.class))
        .add(Pair.of(29, AddUniqueIdAndParentUniqueIdToPipelineExecutionSummaryCDTable.class))
        .add(Pair.of(30, AddUniqueIdAndParentUniqueIdToServiceInfraInfoTable.class))
        .add(Pair.of(31, AddUniqueIdAndParentUniqueIdToPipelinesTable.class))
        .add(Pair.of(32, AddUniqueIdAndParentUniqueIdToStepExecutionTable.class))
        .add(Pair.of(33, AddIndexesStepExecutionPlanExecutionTable.class))
        .add(Pair.of(34, CreateServiceNowStepExecutionTimeScaleTable.class))
        .build();
  }
}

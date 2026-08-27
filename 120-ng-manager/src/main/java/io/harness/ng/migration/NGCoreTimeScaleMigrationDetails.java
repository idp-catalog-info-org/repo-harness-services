/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.migration.beans.MigrationDetails;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.beans.NGMigration;
import io.harness.migration.utils.NoopMigration;
import io.harness.ng.core.migration.timescale.AddArchivePathAndSchemaFileToDBOpsStepExecutionTable;
import io.harness.ng.core.migration.timescale.AddChartVersionToCDStageHelmManifestTable;
import io.harness.ng.core.migration.timescale.AddColumnsToCDStageTable;
import io.harness.ng.core.migration.timescale.AddColumnsToCustomStageTable;
import io.harness.ng.core.migration.timescale.AddCreatedAtUpdatedAtColumnsToTables;
import io.harness.ng.core.migration.timescale.AddCreatedUpdatedColumnsToDBOpsStepExecutionTable;
import io.harness.ng.core.migration.timescale.AddDBConnectionUrlToDBOpsStepExecutionTable;
import io.harness.ng.core.migration.timescale.AddDeletedAtColumns;
import io.harness.ng.core.migration.timescale.AddEnvUniqueIdColumnsToCDStageExecutionTable;
import io.harness.ng.core.migration.timescale.AddExecutionStrategyTypeToCDStageExecutionTable;
import io.harness.ng.core.migration.timescale.AddFullyQualifiedIdentifierColumnToEnvironmentsTable;
import io.harness.ng.core.migration.timescale.AddFullyQualifiedIdentifierColumnToServices;
import io.harness.ng.core.migration.timescale.AddGinIndexOnGitopsEnvIdsToServiceInfraInfoTable;
import io.harness.ng.core.migration.timescale.AddGitOpsEnabledToCDStageTable;
import io.harness.ng.core.migration.timescale.AddGitopsEnvIdsToServiceInfraInfoTable;
import io.harness.ng.core.migration.timescale.AddIndexOnAccIdLastSyncStartedAtTsToGitopsAppInfoTable;
import io.harness.ng.core.migration.timescale.AddIndexOnAccIdOrgIdProjectIdServiceIdToServiceInfraInfoTable;
import io.harness.ng.core.migration.timescale.AddIndexOnAccountDisplayNameToRuntimeInputsInfoTable;
import io.harness.ng.core.migration.timescale.AddIndexOnLastSyncStartedAtTsToGitopsAppInfoTable;
import io.harness.ng.core.migration.timescale.AddIndexToCDStageHelpManifestInfoTable;
import io.harness.ng.core.migration.timescale.AddIndexToRuntimeInputsInfoTable;
import io.harness.ng.core.migration.timescale.AddIndexToServiceInfraInfoBasedOnPipelineExecutionSummaryCdId;
import io.harness.ng.core.migration.timescale.AddIndexToServiceInfraInfoTable;
import io.harness.ng.core.migration.timescale.AddIndexesOnPlanExecIdToStageExecutionTable;
import io.harness.ng.core.migration.timescale.AddIndexesToStageExecutionTable;
import io.harness.ng.core.migration.timescale.AddInfraUniqueIdColumnsToCDStageExecutionTable;
import io.harness.ng.core.migration.timescale.AddMissingColumnsForModulesInModuleLicense;
import io.harness.ng.core.migration.timescale.AddModuleTypeSpecificColumnsToModuleLicensesTable;
import io.harness.ng.core.migration.timescale.AddRollbackDurationToServiceInfraInfoTable;
import io.harness.ng.core.migration.timescale.AddServiceIdToGitopsAppInfoTable;
import io.harness.ng.core.migration.timescale.AddServiceUniqueIdColumnsToCDStageExecutionTable;
import io.harness.ng.core.migration.timescale.AddStageNodeIdToStageStageExecutionTable;
import io.harness.ng.core.migration.timescale.AddStoreTypeToEnvironmentsTable;
import io.harness.ng.core.migration.timescale.AddStoreTypeToServicesTable;
import io.harness.ng.core.migration.timescale.AddUniqueIdAndParentUniqueIdToConnectorsTable;
import io.harness.ng.core.migration.timescale.AddUniqueIdAndParentUniqueIdToEnvironmentsTable;
import io.harness.ng.core.migration.timescale.AddUniqueIdAndParentUniqueIdToInfrastructuresTable;
import io.harness.ng.core.migration.timescale.AddUniqueIdAndParentUniqueIdToRuntimeInputsInfoTable;
import io.harness.ng.core.migration.timescale.AddUniqueIdAndParentUniqueIdToServicesTable;
import io.harness.ng.core.migration.timescale.AddUniqueIdAndParentUniqueIdToStageExecutionTable;
import io.harness.ng.core.migration.timescale.AddUniqueIdAndParentUniqueIdToUserMetadataTable;
import io.harness.ng.core.migration.timescale.AddUsageDetailColumnsToServiceInstancesLicenseDailyReport;
import io.harness.ng.core.migration.timescale.AddUsageDetailColumnsToServicesLicenseDailyReport;
import io.harness.ng.core.migration.timescale.ChangeGitopsAppInfoUniqueIndexIncludeServiceId;
import io.harness.ng.core.migration.timescale.CreateCDStageHelmManifestTable;
import io.harness.ng.core.migration.timescale.CreateCDStageTable;
import io.harness.ng.core.migration.timescale.CreateConnectorsTable;
import io.harness.ng.core.migration.timescale.CreateCustomStageTable;
import io.harness.ng.core.migration.timescale.CreateDBOpsStepExecutionTable;
import io.harness.ng.core.migration.timescale.CreateGitopsAppInfoTable;
import io.harness.ng.core.migration.timescale.CreateHarnessDateBinNGMgrFunction;
import io.harness.ng.core.migration.timescale.CreateHarnessTimeBucketListFunction;
import io.harness.ng.core.migration.timescale.CreateLicenseUsageDailyTable;
import io.harness.ng.core.migration.timescale.CreateLicenseUsageHourlyTable;
import io.harness.ng.core.migration.timescale.CreateLicenseUsageMonthlyTable;
import io.harness.ng.core.migration.timescale.CreateLicenseUsageYearlyTable;
import io.harness.ng.core.migration.timescale.CreateModuleLicensesTable;
import io.harness.ng.core.migration.timescale.CreateNgUserTable;
import io.harness.ng.core.migration.timescale.CreateRuntimeInputsInfoTable;
import io.harness.ng.core.migration.timescale.CreateServiceInstancesLicenseDailyReport;
import io.harness.ng.core.migration.timescale.CreateServicesLicenseDailyReport;
import io.harness.ng.core.migration.timescale.CreateStageTable;
import io.harness.ng.core.migration.timescale.CreateTimeBucketListCDStatusFunction;
import io.harness.ng.core.migration.timescale.GetActiveServicesByDateFunction;
import io.harness.ng.core.migration.timescale.GetServiceInstancesByDateFunction;
import io.harness.ng.core.migration.timescale.MakeLicenseTypeNullable;
import io.harness.ng.core.migration.timescale.PopulateParentUniqueIdAndUniqueIdInOrganizationTable;
import io.harness.ng.core.migration.timescale.ResetParentUniqueIdColumnForProjectsCollection;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class NGCoreTimeScaleMigrationDetails implements MigrationDetails {
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
        .add(Pair.of(1, AddRollbackDurationToServiceInfraInfoTable.class))
        .add(Pair.of(2, CreateModuleLicensesTable.class))
        .add(Pair.of(3, GetServiceInstancesByDateFunction.class))
        .add(Pair.of(4, GetActiveServicesByDateFunction.class))
        .add(Pair.of(5, AddModuleTypeSpecificColumnsToModuleLicensesTable.class))
        .add(Pair.of(6, AddIndexToServiceInfraInfoTable.class))
        .add(Pair.of(7, CreateConnectorsTable.class))
        .add(Pair.of(8, CreateNgUserTable.class))
        .add(Pair.of(9, AddDeletedAtColumns.class))
        .add(Pair.of(10, CreateRuntimeInputsInfoTable.class))
        .add(Pair.of(11, CreateStageTable.class))
        .add(Pair.of(12, CreateCDStageTable.class))
        .add(Pair.of(13, AddColumnsToCDStageTable.class))
        .add(Pair.of(14, GetActiveServicesByDateFunction.class))
        .add(Pair.of(15, GetServiceInstancesByDateFunction.class))
        .add(Pair.of(16, CreateServiceInstancesLicenseDailyReport.class))
        .add(Pair.of(17, CreateServicesLicenseDailyReport.class))
        .add(Pair.of(18, CreateCDStageHelmManifestTable.class))
        .add(Pair.of(19, AddChartVersionToCDStageHelmManifestTable.class))
        .add(Pair.of(20, CreateTimeBucketListCDStatusFunction.class))
        .add(Pair.of(21, CreateHarnessDateBinNGMgrFunction.class))
        .add(Pair.of(22, CreateCustomStageTable.class))
        .add(Pair.of(23, AddColumnsToCustomStageTable.class))
        .add(Pair.of(24, GetActiveServicesByDateFunction.class))
        .add(Pair.of(25, GetServiceInstancesByDateFunction.class))
        .add(Pair.of(26, AddFullyQualifiedIdentifierColumnToServices.class))
        .add(Pair.of(27, GetActiveServicesByDateFunction.class))
        .add(Pair.of(28, AddStoreTypeToServicesTable.class))
        .add(Pair.of(29, AddStoreTypeToEnvironmentsTable.class))
        .add(Pair.of(30, AddFullyQualifiedIdentifierColumnToEnvironmentsTable.class))
        .add(Pair.of(31, GetActiveServicesByDateFunction.class))
        .add(Pair.of(32, GetServiceInstancesByDateFunction.class))
        .add(Pair.of(33, CreateHarnessDateBinNGMgrFunction.class))
        .add(Pair.of(34, CreateHarnessTimeBucketListFunction.class))
        .add(Pair.of(35, CreateGitopsAppInfoTable.class))
        .add(Pair.of(81, AddMissingColumnsForModulesInModuleLicense.class))
        .add(Pair.of(82, MakeLicenseTypeNullable.class))
        .add(Pair.of(83, AddIndexToServiceInfraInfoBasedOnPipelineExecutionSummaryCdId.class))
        .add(Pair.of(84, AddIndexesToStageExecutionTable.class))
        .add(Pair.of(85, AddIndexToRuntimeInputsInfoTable.class))
        .add(Pair.of(86, CreateLicenseUsageHourlyTable.class))
        .add(Pair.of(87, CreateLicenseUsageDailyTable.class))
        .add(Pair.of(88, CreateLicenseUsageMonthlyTable.class))
        .add(Pair.of(89, AddIndexesOnPlanExecIdToStageExecutionTable.class))
        .add(Pair.of(90, AddIndexToCDStageHelpManifestInfoTable.class))
        .add(Pair.of(91, CreateLicenseUsageYearlyTable.class))
        .add(Pair.of(92, AddUsageDetailColumnsToServiceInstancesLicenseDailyReport.class))
        .add(Pair.of(93, AddUsageDetailColumnsToServicesLicenseDailyReport.class))
        .add(Pair.of(94, GetServiceInstancesByDateFunction.class))
        .add(Pair.of(95, AddIndexOnAccIdOrgIdProjectIdServiceIdToServiceInfraInfoTable.class))
        .add(Pair.of(96, CreateDBOpsStepExecutionTable.class))
        .add(Pair.of(97, AddArchivePathAndSchemaFileToDBOpsStepExecutionTable.class))
        .add(Pair.of(98, AddDBConnectionUrlToDBOpsStepExecutionTable.class))
        .add(Pair.of(99, AddGitOpsEnabledToCDStageTable.class))
        .add(Pair.of(100, AddCreatedAtUpdatedAtColumnsToTables.class))
        .add(Pair.of(101, AddCreatedUpdatedColumnsToDBOpsStepExecutionTable.class))
        .add(Pair.of(102, AddUniqueIdAndParentUniqueIdToServicesTable.class))
        .add(Pair.of(103, AddUniqueIdAndParentUniqueIdToInfrastructuresTable.class))
        .add(Pair.of(104, AddUniqueIdAndParentUniqueIdToEnvironmentsTable.class))
        .add(Pair.of(105, AddUniqueIdAndParentUniqueIdToRuntimeInputsInfoTable.class))
        .add(Pair.of(106, AddUniqueIdAndParentUniqueIdToStageExecutionTable.class))
        .add(Pair.of(107, AddUniqueIdAndParentUniqueIdToUserMetadataTable.class))
        .add(Pair.of(108, AddStageNodeIdToStageStageExecutionTable.class))
        .add(Pair.of(109, AddUniqueIdAndParentUniqueIdToConnectorsTable.class))
        .add(Pair.of(110, AddExecutionStrategyTypeToCDStageExecutionTable.class))
        .add(Pair.of(111, PopulateParentUniqueIdAndUniqueIdInOrganizationTable.class))
        .add(Pair.of(112, AddGitopsEnvIdsToServiceInfraInfoTable.class))
        .add(Pair.of(113, AddGinIndexOnGitopsEnvIdsToServiceInfraInfoTable.class))
        .add(Pair.of(114, ResetParentUniqueIdColumnForProjectsCollection.class))
        .add(Pair.of(115, NoopMigration.class))
        .add(Pair.of(116, NoopMigration.class))
        .add(Pair.of(117, NoopMigration.class))
        .add(Pair.of(118, AddServiceUniqueIdColumnsToCDStageExecutionTable.class))
        .add(Pair.of(119, AddEnvUniqueIdColumnsToCDStageExecutionTable.class))
        .add(Pair.of(120, AddInfraUniqueIdColumnsToCDStageExecutionTable.class))
        .add(Pair.of(121, AddIndexOnAccountDisplayNameToRuntimeInputsInfoTable.class))
        .add(Pair.of(122, AddServiceIdToGitopsAppInfoTable.class))
        .add(Pair.of(123, NoopMigration.class))
        .add(Pair.of(124, AddIndexOnAccIdLastSyncStartedAtTsToGitopsAppInfoTable.class))
        .add(Pair.of(125, ChangeGitopsAppInfoUniqueIndexIncludeServiceId.class))
        .add(Pair.of(126, AddIndexOnLastSyncStartedAtTsToGitopsAppInfoTable.class))
        .build();
  }
}

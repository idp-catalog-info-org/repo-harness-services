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
import io.harness.ng.core.migration.AddEnvRollbackPermissionToPipelineExecutorRoles;
import io.harness.ng.core.migration.CodeRoleMigrationForRepoCreatePermission;
import io.harness.ng.core.migration.HarnessManagedLLMConnectorBackfillMigration;
import io.harness.ng.core.migration.NGWebhookMendateSettingsCategoryUpdateMigration;
import io.harness.ng.core.migration.OpaRbacRoleMigration;
import io.harness.ng.core.migration.PopulateYamlFieldInNGEnvironmentMigration;
import io.harness.ng.core.migration.PublicCodeRepoRoleMigration;
import io.harness.ng.core.migration.background.AddDefaultReportTypeToCEAwsConnectorsMigration;
import io.harness.ng.core.migration.background.AddDeploymentTypeToInfrastructureEntityMigration;
import io.harness.ng.core.migration.background.AddServiceOverrideV2RelatedFieldsMigration;
import io.harness.ng.core.migration.background.AddServiceOverrideV2YamlV2GenerationMigration;
import io.harness.ng.core.migration.background.AidaSettingOptOutMigration;
import io.harness.ng.core.migration.background.CICacheIntelOverrideSettingMigration;
import io.harness.ng.core.migration.background.CIEnableIntelligentDefaultsSettingMigration;
import io.harness.ng.core.migration.background.CleanupCdAccountExecutionMetadata;
import io.harness.ng.core.migration.background.CleanupDeploymentAccounts;
import io.harness.ng.core.migration.background.CleanupDeploymentSummaryNg;
import io.harness.ng.core.migration.background.CleanupDuplicateEntitySetupUsage;
import io.harness.ng.core.migration.background.CleanupInfrastructureMappingNg;
import io.harness.ng.core.migration.background.CleanupInstanceNg;
import io.harness.ng.core.migration.background.CleanupOrphanedApiKeysMigration;
import io.harness.ng.core.migration.background.CleanupOrphanedTokensMigration;
import io.harness.ng.core.migration.background.DeleteSoftDeletedConnectorsMigration;
import io.harness.ng.core.migration.background.ExperianEcsPerpetualTaskDeDuplicationMigration;
import io.harness.ng.core.migration.background.IgnoreHttpResponseCodeSettingMigration;
import io.harness.ng.core.migration.background.OptimizedGitFetchFilesSettingMigration;
import io.harness.ng.core.migration.background.PopulateEntityIdV2InTerraformConfigMigration;
import io.harness.ng.core.migration.background.PopulateEntityIdV2InTerragruntConfigMigration;
import io.harness.ng.core.migration.background.PopulatePerpetualTaskEntityReferenceMigration;
import io.harness.ng.core.migration.background.PopulateSettingsForHelmSteadyStateCheckFFMigration;
import io.harness.ng.core.migration.background.PopulateYamlAuthFieldInNGJiraConnectorMigration;
import io.harness.ng.core.migration.background.PopulateYamlAuthFieldInNGServiceNowConnectorMigration;
import io.harness.ng.core.migration.background.PopulateYamlFieldInNGServiceEntityMigration;
import io.harness.ng.core.migration.background.RerunParentUniqueIdMigrationForGitXWebhooks;
import io.harness.ng.core.migration.background.RerunParentUniqueIdMigrationForOrgAndProjectEntityViaCDC;
import io.harness.ng.core.migration.background.RerunParentUniqueIdMigrationForServiceEntityViaCDC;
import io.harness.ng.core.migration.background.RerunParentUniqueIdMigrationForStageExecutionInTSDBViaCDC;
import io.harness.ng.core.migration.background.ResetForceMigrationForGitXWebhooks;
import io.harness.ng.core.migration.background.ResetInfrastructureMappingNgMigrationStatus;
import io.harness.ng.core.migration.background.ResetKubernetesAndHelmPerpetualTaskMigration;
import io.harness.ng.core.migration.background.ResetLicenseUsageMigrationStatus;
import io.harness.ng.core.migration.background.ResetProjectMovementMigrationStatus;
import io.harness.ng.core.migration.background.ResetServiceOverrideMigrationStatus;
import io.harness.ng.core.migration.background.ResetServiceOverridesNgMigrationStatus;
import io.harness.ng.core.migration.background.ResetStageExecutionInfoMigrationStatus;
import io.harness.ng.core.migration.background.ResetUserMembershipMigrationStatus;
import io.harness.ng.core.migration.background.SetNestedEntitiesMigrationStatusCompleted;
import io.harness.ng.core.migration.background.SkipAddingTrackLabelSelectorSettingMigration;
import io.harness.ng.core.migration.background.StageExecutionInfoConnectorReferenceMigration;
import io.harness.ng.core.migration.background.StopParentUniqueIdMigrationForGitXWebhooks;
import io.harness.ng.core.migration.background.UpdateEnvironmentRefValueInServiceOverrideNGMigration;
import io.harness.ng.core.migration.background.UpdateIdentifierServiceOverrideNGMigration;
import io.harness.ng.core.migration.background.UserMetadataTwoFactorAuthenticationMigration;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class NGCoreBackgroundMigrationDetails implements MigrationDetails {
  @Override
  public MigrationType getMigrationTypeName() {
    return MigrationType.MongoBGMigration;
  }

  @Override
  public boolean isBackground() {
    return true;
  }

  @Override
  public List<Pair<Integer, Class<? extends NGMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends NGMigration>>>()
        .add(Pair.of(1, AddDeploymentTypeToInfrastructureEntityMigration.class))
        .add(Pair.of(2, NoopMigration.class))
        .add(Pair.of(3, NoopMigration.class))
        .add(Pair.of(4, NoopMigration.class))
        .add(Pair.of(5, PopulateYamlAuthFieldInNGServiceNowConnectorMigration.class))
        .add(Pair.of(6, NGWebhookMendateSettingsCategoryUpdateMigration.class))
        .add(Pair.of(7, DeleteSoftDeletedConnectorsMigration.class))
        .add(Pair.of(8, PopulateYamlAuthFieldInNGJiraConnectorMigration.class))
        .add(Pair.of(9, NoopMigration.class))
        .add(Pair.of(10, UserMetadataTwoFactorAuthenticationMigration.class))
        .add(Pair.of(11, UpdateEnvironmentRefValueInServiceOverrideNGMigration.class))
        .add(Pair.of(12, PopulateYamlFieldInNGEnvironmentMigration.class))
        .add(Pair.of(13, PopulateYamlFieldInNGServiceEntityMigration.class))
        .add(Pair.of(14, AddServiceOverrideV2RelatedFieldsMigration.class))
        .add(Pair.of(15, CleanupCdAccountExecutionMetadata.class))
        .add(Pair.of(16, CleanupDeploymentAccounts.class))
        .add(Pair.of(17, CleanupDeploymentSummaryNg.class))
        .add(Pair.of(18, CleanupInfrastructureMappingNg.class))
        .add(Pair.of(19, CleanupInstanceNg.class))
        .add(Pair.of(20, PopulateSettingsForHelmSteadyStateCheckFFMigration.class))
        .add(Pair.of(21, OpaRbacRoleMigration.class))
        .add(Pair.of(22, NoopMigration.class))
        .add(Pair.of(23, OptimizedGitFetchFilesSettingMigration.class))
        .add(Pair.of(24, AddEnvRollbackPermissionToPipelineExecutorRoles.class))
        .add(Pair.of(25, UpdateIdentifierServiceOverrideNGMigration.class))
        .add(Pair.of(26, NoopMigration.class))
        .add(Pair.of(27, SkipAddingTrackLabelSelectorSettingMigration.class))
        .add(Pair.of(28, IgnoreHttpResponseCodeSettingMigration.class))
        .add(Pair.of(29, CleanupDuplicateEntitySetupUsage.class))
        .add(Pair.of(30, ExperianEcsPerpetualTaskDeDuplicationMigration.class))
        .add(Pair.of(31, PublicCodeRepoRoleMigration.class))
        .add(Pair.of(32, NoopMigration.class))
        .add(Pair.of(33, NoopMigration.class))
        .add(Pair.of(34, AddServiceOverrideV2YamlV2GenerationMigration.class))
        .add(Pair.of(35, CodeRoleMigrationForRepoCreatePermission.class))
        .add(Pair.of(36, StageExecutionInfoConnectorReferenceMigration.class))
        .add(Pair.of(37, ResetLicenseUsageMigrationStatus.class))
        .add(Pair.of(38, CICacheIntelOverrideSettingMigration.class))
        .add(Pair.of(39, CIEnableIntelligentDefaultsSettingMigration.class))
        .add(Pair.of(40, ResetProjectMovementMigrationStatus.class))
        .add(Pair.of(41, SetNestedEntitiesMigrationStatusCompleted.class))
        .add(Pair.of(42, ResetStageExecutionInfoMigrationStatus.class))
        .add(Pair.of(43, ResetServiceOverrideMigrationStatus.class))
        .add(Pair.of(44, ResetInfrastructureMappingNgMigrationStatus.class))
        .add(Pair.of(45, ResetKubernetesAndHelmPerpetualTaskMigration.class))
        .add(Pair.of(46, ResetUserMembershipMigrationStatus.class))
        .add(Pair.of(47, RerunParentUniqueIdMigrationForGitXWebhooks.class))
        .add(Pair.of(48, RerunParentUniqueIdMigrationForStageExecutionInTSDBViaCDC.class))
        .add(Pair.of(49, ResetForceMigrationForGitXWebhooks.class))
        .add(Pair.of(50, RerunParentUniqueIdMigrationForServiceEntityViaCDC.class))
        .add(Pair.of(51, StopParentUniqueIdMigrationForGitXWebhooks.class))
        .add(Pair.of(52, RerunParentUniqueIdMigrationForOrgAndProjectEntityViaCDC.class))
        .add(Pair.of(53, NoopMigration.class))
        .add(Pair.of(54, NoopMigration.class))
        .add(Pair.of(55, ResetServiceOverridesNgMigrationStatus.class))
        .add(Pair.of(56, PopulateEntityIdV2InTerraformConfigMigration.class))
        .add(Pair.of(57, PopulateEntityIdV2InTerragruntConfigMigration.class))
        .add(Pair.of(58, AddDefaultReportTypeToCEAwsConnectorsMigration.class))
        .add(Pair.of(59, CleanupOrphanedApiKeysMigration.class))
        .add(Pair.of(60, CleanupOrphanedTokensMigration.class))
        .add(Pair.of(61, HarnessManagedLLMConnectorBackfillMigration.class))
        .add(Pair.of(62, AidaSettingOptOutMigration.class))
        .add(Pair.of(63, PopulatePerpetualTaskEntityReferenceMigration.class))
        .build();
  }
}

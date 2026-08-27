/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.iterators.config;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.IteratorExecutionHandler.DynamicIteratorConfig;
import io.harness.mongo.iterator.pojos.IteratorConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@OwnedBy(IDP)
@Value
@Builder
public class IteratorsConfig {
  IteratorConfig scorecardScoreComputation;
  IteratorConfig backstageEnvVariablesSync;
  IteratorConfig backstagePermissionsSync;
  IteratorConfig userSync;
  IteratorConfig configPurge;
  IteratorConfig configSync;
  IteratorConfig licenseUsageCount;
  IteratorConfig scaffolderTasksSync;
  IteratorConfig telemetryRecords;
  IteratorConfig onboardingFlow;
  IteratorConfig marketPlacePluginsSync;
  IteratorConfig statsComputationSync;
  IteratorConfig activeDevelopersSync;
  IteratorConfig scorecardStatsComputation;
  IteratorConfig idpToHarnessEntities;
  IteratorConfig harnessToIDPUserGroupSync;
  IteratorConfig catalogEntitiesVerification;
  IteratorConfig modifyEntityIdentifierInDependentsForIdpV2;
  IteratorConfig modifyDefaultToAccountNamespaceInBackstageForIdpV2;
  IteratorConfig modifyWorkflowFormContextDataForIdpV2;
  IteratorConfig populateQueryableEntityRefInCatalogForIdpV2;
  IteratorConfig migrateEntityScopeForIdpV2;
  IteratorConfig aggregationRulesComputation;
  IteratorConfig relationshipRetry;
  IteratorConfig scopeTopologyCacheRebuild;
  IteratorConfig workflowLibrarySync;
  ApiEndpointRefreshIteratorConfig apiEndpointRefresh;
  @JsonProperty("notifyResponse") DynamicIteratorConfig notifyResponseRedisConfig;
}

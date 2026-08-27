/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.serializer.morphia;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.backstage.entities.BackstageCatalogApiEntity;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogDomainEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.entities.BackstageCatalogGroupEntity;
import io.harness.idp.backstage.entities.BackstageCatalogLocationEntity;
import io.harness.idp.backstage.entities.BackstageCatalogResourceEntity;
import io.harness.idp.backstage.entities.BackstageCatalogSystemEntity;
import io.harness.idp.backstage.entities.BackstageCatalogTemplateEntity;
import io.harness.idp.backstage.entities.BackstageCatalogUserEntity;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.entities.BuiltInKindEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntityVersion;
import io.harness.idp.catalog.entities.CatalogTableEntity;
import io.harness.idp.catalog.entities.CustomKindEntity;
import io.harness.idp.catalog.entities.EntityLinks;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.ccp.entities.CatalogCustomPropertyEntity;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.entities.CustomPluginV2Entity;
import io.harness.idp.configmanager.entities.PluginConfigEnvVariablesEntity;
import io.harness.idp.configmanager.entities.PluginInfoEntity;
import io.harness.idp.configmanager.entities.PluginRequestEntity;
import io.harness.idp.configmanager.entities.PluginsProxyInfoEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.groups.entities.GroupEntity;
import io.harness.idp.homepage.entities.CardEntity;
import io.harness.idp.homepage.entities.HomePageLayoutEntity;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.AzureIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketCloudIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketServerIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitlabIntegrationEntity;
import io.harness.idp.integrations.entities.git.HarnessCodeRepoIntegrationEntity;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.license.usage.entities.ActiveDevelopersDailyCountEntity;
import io.harness.idp.license.usage.entities.ActiveDevelopersEntity;
import io.harness.idp.license.usage.entities.IDPTelemetrySentStatus;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.onboarding.entities.AsyncCatalogImportEntity;
import io.harness.idp.onboarding.entities.OnboardingFlowEntity;
import io.harness.idp.personaview.entities.PersonaViewEntity;
import io.harness.idp.proxy.layout.beans.entity.LayoutEntity;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatsEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatusEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.CatalogDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.CustomHttpDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DirectHttpDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HQLDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.NoopDataSourceLocationEntity;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.entity.HttpDataSourceEntity;
import io.harness.idp.scorecard.datasources.entity.NoopDataSourceEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardStatsEntity;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.idp.settings.beans.entity.BackstagePermissionsEntity;
import io.harness.idp.status.beans.StatusInfoEntity;
import io.harness.idp.user.beans.entity.UserEventEntity;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class IdpServiceMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(AppConfigEntity.class);
    set.add(BackstageEnvVariableEntity.class);
    set.add(StatusInfoEntity.class);
    set.add(NamespaceEntity.class);
    set.add(BackstagePermissionsEntity.class);
    set.add(CatalogConnectorEntity.class);
    set.add(PluginInfoEntity.class);
    set.add(UserEventEntity.class);
    set.add(PluginConfigEnvVariablesEntity.class);
    set.add(PluginRequestEntity.class);
    set.add(AsyncCatalogImportEntity.class);
    set.add(PluginsProxyInfoEntity.class);
    set.add(ScorecardEntity.class);
    set.add(TierGroupEntity.class);
    set.add(CheckEntity.class);
    set.add(CheckStatusEntity.class);
    set.add(DataSourceEntity.class);
    set.add(DataPointEntity.class);
    set.add(DataSourceLocationEntity.class);
    set.add(DirectHttpDataSourceLocationEntity.class);
    set.add(CustomHttpDataSourceLocationEntity.class);
    set.add(NoopDataSourceLocationEntity.class);
    set.add(HQLDataSourceLocationEntity.class);
    set.add(CatalogDataSourceLocationEntity.class);
    set.add(ScoreEntity.class);
    set.add(ActiveDevelopersEntity.class);
    set.add(ActiveDevelopersDailyCountEntity.class);
    set.add(HttpDataSourceEntity.class);
    set.add(NoopDataSourceEntity.class);
    set.add(IDPTelemetrySentStatus.class);
    set.add(CheckStatsEntity.class);
    set.add(ScorecardStatsEntity.class);
    set.add(BackstageCatalogEntity.class);
    set.add(BackstageCatalogApiEntity.class);
    set.add(BackstageCatalogComponentEntity.class);
    set.add(BackstageCatalogDomainEntity.class);
    set.add(BackstageCatalogGroupEntity.class);
    set.add(BackstageCatalogLocationEntity.class);
    set.add(BackstageCatalogResourceEntity.class);
    set.add(BackstageCatalogSystemEntity.class);
    set.add(BackstageCatalogTemplateEntity.class);
    set.add(BackstageCatalogUserEntity.class);
    set.add(LayoutEntity.class);
    set.add(BackstageScaffolderTaskEntity.class);
    set.add(IntegrationEntity.class);
    set.add(GitIntegrationEntity.class);
    set.add(GithubIntegrationEntity.class);
    set.add(GitlabIntegrationEntity.class);
    set.add(BitbucketCloudIntegrationEntity.class);
    set.add(BitbucketServerIntegrationEntity.class);
    set.add(AzureIntegrationEntity.class);
    set.add(HarnessCodeRepoIntegrationEntity.class);
    set.add(CatalogCustomPropertyEntity.class);
    set.add(IteratorEntity.class);
    set.add(OnboardingFlowEntity.class);
    set.add(GroupEntity.class);
    set.add(HomePageLayoutEntity.class);
    set.add(PersonaViewEntity.class);
    set.add(CardEntity.class);
    set.add(CatalogEntity.class);
    set.add(InlineCatalogEntity.class);
    set.add(GitReferencedCatalogEntity.class);
    set.add(CatalogEntityVersion.class);
    set.add(CatalogTableEntity.class);
    set.add(AggregationRuleEntity.class);
    set.add(CustomPluginV2Entity.class);
    set.add(KindEntity.class);
    set.add(BuiltInKindEntity.class);
    set.add(CustomKindEntity.class);
    set.add(io.harness.idp.layout.entities.LayoutEntity.class);
    set.add(Action.class);
    set.add(EntityLinks.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // Nothing to register
  }
}

/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.idp.common.Constants.AZURE_REPO;
import static io.harness.idp.common.Constants.BITBUCKET;
import static io.harness.idp.common.Constants.GITHUB;
import static io.harness.idp.common.Constants.GITLAB;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.AzureIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.migration.beans.NGMigration;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogConnectorIntegrationsInitialConfigDeprecateMigration implements NGMigration {
  AppConfigRepository appConfigRepository;
  IntegrationEntityRepository integrationEntityRepository;
  GitIntegrationServiceImpl gitIntegrationService;

  @Override
  public void migrate() {
    log.info("Starting the migration for catalogConnectors integrations initial config deprecate.");

    List<String> configIds = List.of(AZURE_REPO, BITBUCKET, GITHUB, GITLAB);
    log.info("Performing catalogConnectors config deprecate migration for configIds = {}", configIds);
    List<AppConfigEntity> appConfigEntities = appConfigRepository.findAllByConfigIdIn(configIds);
    log.info("Found total of {} catalogConnectors config for deprecate migration", appConfigEntities.size());
    appConfigEntities.forEach(appConfigEntity -> appConfigEntity.setEnabled(false));
    appConfigEntities = (List<AppConfigEntity>) appConfigRepository.saveAll(appConfigEntities);
    log.info("Updated total of {} catalogConnectors config in deprecate migration", appConfigEntities.size());

    List<IntegrationEntity> integrationEntities =
        integrationEntityRepository.findByIntegration(IntegrationEntity.Integration.GIT);
    integrationEntities.forEach(integrationEntity -> {
      try {
        gitIntegrationService.update(integrationEntity.getAccountIdentifier(), integrationEntity.getIdentifier(),
            gitIntegrationRequest(((GitIntegrationEntity) integrationEntity).getConnectorIdentifier()), false);
      } catch (Exception ex) {
        log.warn("Error in updating integration for sync - {}", ex.getMessage(), ex);
      }
    });

    List<String> azureOrganizations = new ArrayList<>();
    integrationEntities.forEach(integrationEntity -> {
      GitIntegrationEntity gitIntegrationEntity = (GitIntegrationEntity) integrationEntity;
      if (gitIntegrationEntity.getParentType().equals(IntegrationEntity.ParentType.AZURE)) {
        AzureIntegrationEntity azureIntegrationEntity = (AzureIntegrationEntity) gitIntegrationEntity;
        azureOrganizations.add(azureIntegrationEntity.getOrganization());
      }
    });

    configIds = new ArrayList<>();
    configIds.add(IntegrationEntity.ParentType.AZURE.name());
    for (String azureOrganization : azureOrganizations) {
      configIds.add(IntegrationEntity.ParentType.AZURE + "_" + azureOrganization);
    }

    configIds.add(IntegrationEntity.ParentType.BITBUCKET_SERVER.name());
    configIds.add(IntegrationEntity.ParentType.GITHUB.name() + "_" + IntegrationEntity.SubType.GITHUB_DIRECT.name());
    configIds.add(
        IntegrationEntity.ParentType.GITHUB.name() + "_" + IntegrationEntity.SubType.GITHUB_ENTERPRISE.name());
    configIds.add(IntegrationEntity.ParentType.GITLAB.name());

    log.info("Performing integrations initial config deprecate migration for configIds = {}", configIds);
    appConfigEntities = appConfigRepository.findAllByConfigIdIn(configIds);
    log.info("Found total of {} integrations initial config for deprecate migration", appConfigEntities.size());
    appConfigEntities.forEach(appConfigEntity -> appConfigEntity.setEnabled(false));
    appConfigEntities = (List<AppConfigEntity>) appConfigRepository.saveAll(appConfigEntities);
    log.info("Updated total of {} integrations initial config in deprecate migration", appConfigEntities.size());

    log.info("Completed the migration for catalogConnectors integrations initial config deprecate.");
  }

  private GitIntegrationRequest gitIntegrationRequest(String connectorIdentifier) {
    GitIntegrationRequest gitIntegrationRequest = new GitIntegrationRequest();
    gitIntegrationRequest.setType(BaseIntegrationRequest.TypeEnum.GIT);
    gitIntegrationRequest.setConnectorIdentifier(connectorIdentifier);
    return gitIntegrationRequest;
  }
}

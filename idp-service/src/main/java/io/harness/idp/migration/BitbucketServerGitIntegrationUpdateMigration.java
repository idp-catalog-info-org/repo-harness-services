/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.migration.beans.NGMigration;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BitbucketServerGitIntegrationUpdateMigration implements NGMigration {
  IntegrationEntityRepository integrationEntityRepository;
  GitIntegrationServiceImpl gitIntegrationService;

  @Override
  public void migrate() {
    log.info("Starting the migration for bitbucket server git integration update sync.");

    List<IntegrationEntity> integrationEntities =
        integrationEntityRepository.findByIntegration(IntegrationEntity.Integration.GIT);
    integrationEntities.forEach(integrationEntity -> {
      if (integrationEntity.getParentType().equals(IntegrationEntity.ParentType.BITBUCKET_SERVER)) {
        try {
          gitIntegrationService.update(integrationEntity.getAccountIdentifier(), integrationEntity.getIdentifier(),
              gitIntegrationRequest(((GitIntegrationEntity) integrationEntity).getConnectorIdentifier()), false);
        } catch (Exception ex) {
          log.warn("Error in updating bitbucket server git integration for sync - Account {} Identifier {} Error {}",
              integrationEntity.getAccountIdentifier(), integrationEntity.getIdentifier(), ex.getMessage(), ex);
        }
      }
    });

    log.info("Completed the migration for bitbucket server git integration update sync.");
  }

  private GitIntegrationRequest gitIntegrationRequest(String connectorIdentifier) {
    GitIntegrationRequest gitIntegrationRequest = new GitIntegrationRequest();
    gitIntegrationRequest.setType(BaseIntegrationRequest.TypeEnum.GIT);
    gitIntegrationRequest.setConnectorIdentifier(connectorIdentifier);
    return gitIntegrationRequest;
  }
}

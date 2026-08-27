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
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationHarnessCodeRepoAdditionalHostsMigration implements NGMigration {
  @Inject NamespaceService namespaceService;
  @Inject GitIntegrationServiceImpl gitIntegrationService;
  @Inject IdpCommonService idpCommonService;

  @Override
  public void migrate() {
    log.info("Starting the migration for adding additional host for HCR git integration.");

    List<String> activeIdpAccounts = namespaceService.getAccountIds();
    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
    activeIdpAccounts.forEach(accountIdentifier -> {
      try {
        List<IntegrationEntity> integrationEntities =
            gitIntegrationService.fetchManagedGitIntegrations(accountIdentifier);
        integrationEntities.forEach(integrationEntity -> {
          if (integrationEntity.getParentType().equals(IntegrationEntity.ParentType.HARNESS_CODE_REPO)) {
            gitIntegrationService.updateDefaultConnectorLessManagedHarnessCodeRepoIntegrationWithoutBaseUrlOverriding(
                accountIdentifier);
          }
        });
      } catch (Exception ex) {
        log.warn("Error in migration for adding additional host for HCR git integration for account = {} Error = {}",
            accountIdentifier, ex.getMessage(), ex);
      }
    });

    log.info("Completed the migration for adding additional host for HCR git integration.");
  }
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.YamlUtils.loadYamlStringAsMap;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.GitDetails;
import io.harness.spec.server.idp.v1.model.GitUpdateDetails;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class PopulateNameInCatalogIfMissingMigration implements NGMigration {
  NamespaceService namespaceService;
  CatalogService catalogService;
  IDPGitXHelper idpGitXHelper;

  @Override
  public void migrate() {
    log.info("Starting the migration for populating name in catalog if missing.");

    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
    List<String> activeIdpAccounts = namespaceService.getAccountIds();
    activeIdpAccounts.forEach(accountIdentifier -> {
      try {
        GetEntitiesDTO getEntitiesDTO = catalogService.getEntities(accountIdentifier, 0, -1, null, null, false,
            "account.*", null, false, false, null, null, null, null, null, "name=null", false);
        SourcePrincipalContextBuilder.setSourcePrincipal(
            new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
        List<EntityResponse> entityResponses = getEntitiesDTO.getEntityResponses();
        entityResponses.forEach(entityResponse -> {
          String yaml = entityResponse.getYaml();
          if (isEmpty(entityResponse.getName())) {
            yaml = YamlUtils.writeObjectAsYaml(
                YamlUtils.merge(loadYamlStringAsMap(yaml), Map.of("name", entityResponse.getIdentifier())));
            EntityUpdateRequest entityUpdateRequest = new EntityUpdateRequest();
            entityUpdateRequest.setYaml(yaml);
            if (entityResponse.getGitDetails() != null) {
              EntityResponse getEntityResponse =
                  catalogService.getEntity(accountIdentifier, entityResponse.getOrgIdentifier(),
                      entityResponse.getProjectIdentifier(), entityResponse.getEntityRef(), false, false, true, false);
              GitDetails gitDetails = getEntityResponse.getGitDetails();
              GitUpdateDetails gitUpdateDetails = new GitUpdateDetails();
              gitUpdateDetails.setStoreType(GitUpdateDetails.StoreTypeEnum.valueOf(gitDetails.getStoreType().name()));
              gitUpdateDetails.setRepoName(gitDetails.getRepoName());
              gitUpdateDetails.setLastObjectId(gitDetails.getObjectId());
              gitUpdateDetails.setLastCommitId(gitDetails.getCommitId());
              gitUpdateDetails.setIsHarnessCodeRepo(gitDetails.isIsHarnessCodeRepo());
              gitUpdateDetails.setFilePath(gitDetails.getFilePath());
              gitUpdateDetails.setConnectorRef(gitDetails.getConnectorRef());
              gitUpdateDetails.setCommitMessage(gitDetails.getCommitMessage());
              gitUpdateDetails.setBranchName(gitDetails.getBranchName());
              gitUpdateDetails.setBaseBranch(gitDetails.getBaseBranch());
              entityUpdateRequest.setGitDetails(gitUpdateDetails);
              GitAwareContextHelper.populateGitDetails(
                  idpGitXHelper.populateGitUpdateDetails(entityUpdateRequest.getGitDetails()));
            }
            catalogService.updateEntity(accountIdentifier, entityResponse.getOrgIdentifier(),
                entityResponse.getProjectIdentifier(), entityResponse.getEntityRef(), entityUpdateRequest, false, true,
                false, false);
            GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
          }
        });
      } catch (Exception ex) {
        log.warn("Error in migration for populating name in catalog if missing for account = {} "
                + "Error = {}",
            accountIdentifier, ex.getMessage(), ex);
      }
    });

    log.info("Completed the migration for populating name in catalog if missing.");
  }
}

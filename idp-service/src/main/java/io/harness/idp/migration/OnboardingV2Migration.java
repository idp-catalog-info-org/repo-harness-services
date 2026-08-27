/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.idp.backstage.Constants.ORGANIZATION;
import static io.harness.idp.backstage.Constants.PROJECT;
import static io.harness.idp.backstage.Constants.SERVICE;
import static io.harness.idp.backstage.beans.MetadataFieldConstants.ABSOLUTE_IDENTIFIER;
import static io.harness.idp.common.CommonUtils.removeTrailingAndLeadingSlash;
import static io.harness.idp.common.CommonUtils.replaceAccountScopeFromIdentifier;
import static io.harness.idp.common.Constants.ACCOUNT_SCOPED;
import static io.harness.idp.common.Constants.SLASH_DELIMITER;
import static io.harness.idp.onboarding.utils.Constants.YAML_FILE_EXTENSION;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.gitintegration.beans.CatalogRepositoryDetails;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.repositories.CatalogConnectorRepository;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.onboarding.entities.AsyncCatalogImportEntity;
import io.harness.idp.onboarding.entities.OnboardingFlowEntity;
import io.harness.idp.onboarding.repositories.AsyncCatalogImportRepository;
import io.harness.idp.onboarding.repositories.OnboardingFlowEntityRepository;
import io.harness.idp.status.beans.StatusInfoEntity;
import io.harness.idp.status.enums.StatusType;
import io.harness.idp.status.repositories.StatusInfoRepository;
import io.harness.migration.beans.NGMigration;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;
import io.harness.spec.server.idp.v1.model.StatusInfo;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class OnboardingV2Migration implements NGMigration {
  @Inject StatusInfoRepository statusInfoRepository;
  @Inject CatalogConnectorRepository catalogConnectorRepository;
  @Inject AsyncCatalogImportRepository asyncCatalogImportRepository;
  @Inject GitIntegrationServiceImpl gitIntegrationService;
  @Inject OnboardingFlowEntityRepository onboardingFlowEntityRepository;

  @Override
  public void migrate() {
    log.info("Starting the migration for onboarding V2.");
    List<StatusInfoEntity> completedOnboarding =
        statusInfoRepository.findByTypeAndStatus(StatusType.ONBOARDING, StatusInfo.CurrentStatusEnum.COMPLETED);
    completedOnboarding.forEach(statusInfoEntity -> {
      String accountIdentifier = statusInfoEntity.getAccountIdentifier();
      try {
        List<CatalogConnectorEntity> catalogConnectorEntities =
            catalogConnectorRepository.findByAccountIdentifierAndCatalogRepositoryDetailsNotNullOrderByCreatedAtAsc(
                accountIdentifier);
        Set<OnboardingFlowEntity.WriteDetails> writeDetailsList = new HashSet<>();

        catalogConnectorEntities.forEach(catalogConnectorEntity -> {
          CatalogRepositoryDetails catalogRepositoryDetails = catalogConnectorEntity.getCatalogRepositoryDetails();
          OnboardingFlowEntity.WriteDetails writeDetails =
              OnboardingFlowEntity.WriteDetails.builder()
                  .connectorIdentifier(ACCOUNT_SCOPED + catalogConnectorEntity.getConnectorIdentifier())
                  .repositoryUrl(catalogRepositoryDetails.getRepo())
                  .branch(catalogRepositoryDetails.getBranch())
                  .path(removeTrailingAndLeadingSlash(catalogRepositoryDetails.getPath()))
                  .build();
          writeDetailsList.add(writeDetails);
        });

        AsyncCatalogImportEntity asyncCatalogImportEntity =
            asyncCatalogImportRepository.findByAccountIdentifier(accountIdentifier);

        OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
        onboardingFlowEntity.setAccountIdentifier(accountIdentifier);
        onboardingFlowEntity.setSkippedAt(OnboardingFlowEntity.SkippedAt.NA);
        onboardingFlowEntity.setWriteDetails(writeDetailsList);
        onboardingFlowEntity.setImportedSampleEntityDefinition(asyncCatalogImportEntity == null);

        if (asyncCatalogImportEntity != null) {
          List<? extends BackstageCatalogEntity> catalogDomains =
              Objects.nonNull(asyncCatalogImportEntity.getCatalogDomains())
                  && Objects.nonNull(asyncCatalogImportEntity.getCatalogDomains().getEntities())
              ? asyncCatalogImportEntity.getCatalogDomains().getEntities()
              : new ArrayList<>();
          List<? extends BackstageCatalogEntity> catalogSystems =
              Objects.nonNull(asyncCatalogImportEntity.getCatalogSystems())
                  && Objects.nonNull(asyncCatalogImportEntity.getCatalogSystems().getEntities())
              ? asyncCatalogImportEntity.getCatalogSystems().getEntities()
              : new ArrayList<>();
          List<? extends BackstageCatalogEntity> catalogComponents =
              Objects.nonNull(asyncCatalogImportEntity.getCatalogComponents())
                  && Objects.nonNull(asyncCatalogImportEntity.getCatalogComponents().getEntities())
              ? asyncCatalogImportEntity.getCatalogComponents().getEntities()
              : new ArrayList<>();

          onboardingFlowEntity.setNumberOfCDEntitiesImported(
              catalogDomains.size() + catalogSystems.size() + catalogComponents.size());

          String connectorIdentifier = catalogConnectorEntities.get(0).getConnectorIdentifier();
          connectorIdentifier = replaceAccountScopeFromIdentifier(connectorIdentifier);
          ConnectorInfoDTO connectorInfoDTO =
              gitIntegrationService.getConnectorInfo(accountIdentifier, null, null, connectorIdentifier);
          String gitIntegrationType = gitIntegrationService.getGitIntegrationType(connectorInfoDTO);
          GitIntegrationRequest gitIntegrationRequest = new GitIntegrationRequest();
          gitIntegrationRequest.setType(BaseIntegrationRequest.TypeEnum.GIT);
          gitIntegrationRequest.setConnectorIdentifier(connectorIdentifier);
          WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
          writeValidationDetails.setRepository(catalogConnectorEntities.get(0).getCatalogRepositoryDetails().getRepo());
          writeValidationDetails.setBranch(catalogConnectorEntities.get(0).getCatalogRepositoryDetails().getBranch());
          writeValidationDetails.setPath(catalogConnectorEntities.get(0).getCatalogRepositoryDetails().getPath());
          gitIntegrationRequest.setWriteValidationDetails(writeValidationDetails);

          List<String> importedCDEntities = new ArrayList<>();
          Map<String, Set<String>> importedCDEntitiesRef = new HashMap<>();
          catalogDomains.forEach(catalogDomain -> {
            String absoluteIdentifier = (String) catalogDomain.getMetadata().get(ABSOLUTE_IDENTIFIER);
            importedCDEntities.add(absoluteIdentifier);
            Set<String> existingImportedRef = importedCDEntitiesRef.getOrDefault(absoluteIdentifier, new HashSet<>());
            existingImportedRef.add(
                onboardingFlowEntity.idpCatalogSourceLocation(gitIntegrationType, gitIntegrationRequest,
                    ORGANIZATION + SLASH_DELIMITER + catalogDomain.getMetadata().get("name") + YAML_FILE_EXTENSION));
            importedCDEntitiesRef.put(absoluteIdentifier, existingImportedRef);
          });
          catalogSystems.forEach(catalogSystem -> {
            String absoluteIdentifier = (String) catalogSystem.getMetadata().get(ABSOLUTE_IDENTIFIER);
            importedCDEntities.add(absoluteIdentifier);
            Set<String> existingImportedRef = importedCDEntitiesRef.getOrDefault(absoluteIdentifier, new HashSet<>());
            existingImportedRef.add(
                onboardingFlowEntity.idpCatalogSourceLocation(gitIntegrationType, gitIntegrationRequest,
                    PROJECT + SLASH_DELIMITER + catalogSystem.getMetadata().get("name") + YAML_FILE_EXTENSION));
            importedCDEntitiesRef.put(absoluteIdentifier, existingImportedRef);
          });
          catalogComponents.forEach(catalogComponent -> {
            String absoluteIdentifier = (String) catalogComponent.getMetadata().get(ABSOLUTE_IDENTIFIER);
            importedCDEntities.add(absoluteIdentifier);
            Set<String> existingImportedRef = importedCDEntitiesRef.getOrDefault(absoluteIdentifier, new HashSet<>());
            existingImportedRef.add(
                onboardingFlowEntity.idpCatalogSourceLocation(gitIntegrationType, gitIntegrationRequest,
                    SERVICE + SLASH_DELIMITER + catalogComponent.getMetadata().get("name") + YAML_FILE_EXTENSION));
            importedCDEntitiesRef.put(absoluteIdentifier, existingImportedRef);
          });

          onboardingFlowEntity.setImportedCDEntities(new HashSet<>(importedCDEntities));
          onboardingFlowEntity.setImportedCDEntitiesRef(importedCDEntitiesRef);
        }

        onboardingFlowEntity.setRegisterEntitiesOnIdpAt(Long.MAX_VALUE);
        onboardingFlowEntity.setEntitiesToRegisterOnIdp(new ArrayList<>());
        onboardingFlowEntity.setCurrentStatus("ONBOARDING_COMPLETED_ALLOW_FURTHER");
        onboardingFlowEntityRepository.save(onboardingFlowEntity);
      } catch (Exception ex) {
        log.error("Error in onboarding V2 migration for accountIdentifier = {} Error = {}", accountIdentifier,
            ex.getMessage(), ex);
      }
    });
    log.info("Completed the migration for onboarding V2.");
  }
}

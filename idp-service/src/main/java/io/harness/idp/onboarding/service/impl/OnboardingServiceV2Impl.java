/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.service.impl;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.backstage.Constants.ORGANIZATION;
import static io.harness.idp.backstage.Constants.PROJECT;
import static io.harness.idp.backstage.Constants.SERVICE;
import static io.harness.idp.backstage.beans.MetadataFieldConstants.ABSOLUTE_IDENTIFIER;
import static io.harness.idp.backstage.beans.MetadataFieldConstants.NAME;
import static io.harness.idp.common.CommonUtils.readFileFromClassPath;
import static io.harness.idp.common.CommonUtils.replaceAccountScopeFromIdentifier;
import static io.harness.idp.common.Constants.ACCOUNT_SCOPED;
import static io.harness.idp.common.Constants.SLASH_DELIMITER;
import static io.harness.idp.common.Constants.SUCCESS_RESPONSE;
import static io.harness.idp.common.YamlUtils.writeObjectAsYaml;
import static io.harness.idp.integrations.utils.Constants.HCR_CONNECTOR_IDENTIFIER;
import static io.harness.idp.onboarding.utils.Constants.BACKSTAGE_LOCATION_URL_TYPE;
import static io.harness.idp.onboarding.utils.Constants.ENTITY_UNKNOWN_REF;
import static io.harness.idp.onboarding.utils.Constants.ONBOARDING_COMPLETED_ALLOW_FURTHER;
import static io.harness.idp.onboarding.utils.Constants.ONBOARDING_SAMPLE_CATALOG_INFO;
import static io.harness.idp.onboarding.utils.Constants.PAGE_LIMIT_FOR_ENTITY_FETCH;
import static io.harness.idp.onboarding.utils.Constants.STATUS_UPDATE_REASON_FOR_ONBOARDING_COMPLETED;
import static io.harness.idp.onboarding.utils.Constants.STATUS_UPDATE_REASON_FOR_ONBOARDING_SKIPPED;
import static io.harness.idp.onboarding.utils.Constants.YAML_FILE_EXTENSION;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.clients.BackstageCatalogLocationCreateRequest;
import io.harness.clients.BackstageResourceClient;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.exception.UnexpectedException;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogDomainEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.entities.BackstageCatalogSystemEntity;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.onboarding.config.OnboardingModuleV2Config;
import io.harness.idp.onboarding.entities.OnboardingFlowEntity;
import io.harness.idp.onboarding.mappers.HarnessEntityToBackstageEntity;
import io.harness.idp.onboarding.mappers.HarnessOrgToBackstageDomain;
import io.harness.idp.onboarding.mappers.HarnessProjectToBackstageSystem;
import io.harness.idp.onboarding.mappers.HarnessServiceToBackstageComponent;
import io.harness.idp.onboarding.repositories.OnboardingFlowEntityRepository;
import io.harness.idp.onboarding.service.OnboardingServiceV2;
import io.harness.idp.status.enums.StatusType;
import io.harness.idp.status.service.StatusInfoService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.service.remote.ServiceResourceClient;
import io.harness.spec.server.idp.v1.model.CDEntityAsIdpEntity;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesCountResponse;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesFetchRequest;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesFetchRequestFilterOptions;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesFetchResponse;
import io.harness.spec.server.idp.v1.model.OnboardingGenerateYamlDefRequest;
import io.harness.spec.server.idp.v1.model.OnboardingGenerateYamlDefResponse;
import io.harness.spec.server.idp.v1.model.OnboardingImportCdEntitiesRequest;
import io.harness.spec.server.idp.v1.model.OnboardingImportCdEntitiesResponse;
import io.harness.spec.server.idp.v1.model.OnboardingSkipRequest;
import io.harness.spec.server.idp.v1.model.OnboardingSkipResponse;
import io.harness.spec.server.idp.v1.model.OnboardingStatusResponse;
import io.harness.spec.server.idp.v1.model.StatusInfo;
import io.harness.spec.server.idp.v1.model.StatusInfoV2;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.data.domain.Pageable;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class OnboardingServiceV2Impl implements OnboardingServiceV2 {
  @Inject IdpCommonService idpCommonService;
  @Inject ServiceResourceClient serviceResourceClient;
  @Inject @Named("PRIVILEGED") OrganizationClient organizationClient;
  @Inject @Named("PRIVILEGED") ProjectClient projectClient;
  @Inject HarnessOrgToBackstageDomain harnessOrgToBackstageDomain;
  @Inject HarnessProjectToBackstageSystem harnessProjectToBackstageSystem;
  @Inject HarnessServiceToBackstageComponent harnessServiceToBackstageComponent;
  @Inject @Named("onboardingModuleV2Config") OnboardingModuleV2Config onboardingModuleV2Config;
  @Inject OnboardingFlowEntityRepository onboardingFlowEntityRepository;
  @Inject GitIntegrationServiceImpl gitIntegrationService;
  @Inject StatusInfoService statusInfoService;
  @Inject BackstageResourceClient backstageResourceClient;

  @Override
  public OnboardingCdEntitiesCountResponse cdEntitiesCount(String harnessAccount) {
    int servicesTotalCount = getServicesTotalCount(harnessAccount);
    if (!idpCommonService.isLegacyCDFlow(harnessAccount)) {
      servicesTotalCount = checkAndRemoveAlreadyImportedCDEntitiesForCount(harnessAccount, servicesTotalCount);
    }
    OnboardingCdEntitiesCountResponse cdEntitiesCountResponse = new OnboardingCdEntitiesCountResponse();
    cdEntitiesCountResponse.setCdEntitiesCount(servicesTotalCount);
    return cdEntitiesCountResponse;
  }

  @Override
  public OnboardingCdEntitiesFetchResponse cdEntitiesFetch(String harnessAccount,
      OnboardingCdEntitiesFetchRequest onboardingCdEntitiesFetchRequest, Pageable pageable, String searchTerm) {
    OnboardingCdEntitiesFetchRequestFilterOptions onboardingCdEntitiesFetchRequestFilterOptions =
        onboardingCdEntitiesFetchRequest.getFilterOptions();
    String organizationFilter = onboardingCdEntitiesFetchRequestFilterOptions.getOrganization();
    String projectFilter = onboardingCdEntitiesFetchRequestFilterOptions.getProject();
    List<ServiceResponseDTO> services = getServices(harnessAccount, organizationFilter, projectFilter, searchTerm);
    List<BackstageCatalogComponentEntity> components = harnessServiceToBackstageComponent(services);
    List<CDEntityAsIdpEntity> cdEntitiesAsIdpEntities =
        new ArrayList<>(BackstageCatalogComponentEntity.mapV2(components));

    checkAndRemoveAlreadyImportedCDEntities(harnessAccount, cdEntitiesAsIdpEntities);
    Optional<OnboardingFlowEntity> existingOnboardingFlowEntity = optionalOnboardingFlowEntity(harnessAccount);

    OnboardingFlowEntity onboardingFlowEntity = existingOnboardingFlowEntity.orElseGet(OnboardingFlowEntity::new);
    Pair<Integer, Integer> orgAndProjectCountAfterRemovingAlreadyImported =
        getOrgAndProjectCountAfterRemovingAlreadyImported(onboardingFlowEntity, cdEntitiesAsIdpEntities);

    OnboardingCdEntitiesFetchResponse cdEntitiesFetchResponse = new OnboardingCdEntitiesFetchResponse();
    cdEntitiesFetchResponse.setOrganizationsCount(orgAndProjectCountAfterRemovingAlreadyImported.getKey());
    cdEntitiesFetchResponse.setProjectsCount(orgAndProjectCountAfterRemovingAlreadyImported.getValue());
    cdEntitiesFetchResponse.setServicesCount(cdEntitiesAsIdpEntities.size());
    cdEntitiesFetchResponse.setEntities(
        PageUtils.offsetAndLimit(cdEntitiesAsIdpEntities, pageable.getPageNumber(), pageable.getPageSize())
            .getContent());
    return cdEntitiesFetchResponse;
  }

  @Override
  public OnboardingGenerateYamlDefResponse generateYamlDef(
      String harnessAccount, OnboardingGenerateYamlDefRequest onboardingGenerateYamlDefRequest) {
    OnboardingGenerateYamlDefRequest.TypeEnum type = onboardingGenerateYamlDefRequest.getType();
    OnboardingGenerateYamlDefResponse generateYamlDefResponse = new OnboardingGenerateYamlDefResponse();
    if (type == OnboardingGenerateYamlDefRequest.TypeEnum.SAMPLE) {
      generateYamlDefResponse.setYamlDefDesc(onboardingModuleV2Config.getDescriptionForSampleCatalogInfoDef());
      generateYamlDefResponse.setYamlDef(readFileFromClassPath(ONBOARDING_SAMPLE_CATALOG_INFO));
    } else {
      String entity = onboardingGenerateYamlDefRequest.getEntityIdentifier();
      String[] orgProjectService = entity.split("\\|");
      ServiceResponseDTO serviceResponseDTO =
          getService(harnessAccount, orgProjectService[0].equals(ENTITY_UNKNOWN_REF) ? null : orgProjectService[0],
              orgProjectService[1].equals(ENTITY_UNKNOWN_REF) ? null : orgProjectService[1], orgProjectService[2]);
      BackstageCatalogComponentEntity backstageCatalogComponentEntity =
          harnessServiceToBackstageComponent(Collections.singletonList(serviceResponseDTO)).get(0);
      generateYamlDefResponse.setYamlDefDesc(onboardingModuleV2Config.getDescriptionForActualCatalogInfoDef());
      generateYamlDefResponse.setYamlDef(writeObjectAsYaml(backstageCatalogComponentEntity));
    }
    return generateYamlDefResponse;
  }

  @Override
  public OnboardingStatusResponse getOnboardingStatus(String harnessAccount) {
    Optional<OnboardingFlowEntity> existingOnboardingFlowEntity = optionalOnboardingFlowEntity(harnessAccount);
    OnboardingStatusResponse statusResponse = new OnboardingStatusResponse();
    List<IntegrationEntity> nonManagedGitIntegrations =
        gitIntegrationService.fetchNonManagedGitIntegrations(harnessAccount);
    if (existingOnboardingFlowEntity.isEmpty()) {
      OnboardingStatusResponse.StatusEnum status = OnboardingStatusResponse.StatusEnum.GET_STARTED;
      if (!CollectionUtils.isEmpty(nonManagedGitIntegrations)) {
        status = OnboardingStatusResponse.StatusEnum.WITH_INTEGRATION_NO_IMPORT;
      }
      statusResponse.setStatus(status);
    } else {
      OnboardingFlowEntity onboardingFlowEntity = existingOnboardingFlowEntity.get();
      String currentStatus = onboardingFlowEntity.getCurrentStatus();
      if ((Objects.equals(currentStatus, "GET_STARTED") || Objects.equals(currentStatus, "WITHOUT_INTEGRATION"))
          && !CollectionUtils.isEmpty(nonManagedGitIntegrations)) {
        currentStatus = OnboardingStatusResponse.StatusEnum.WITH_INTEGRATION_NO_IMPORT.name();
      }
      statusResponse.setStatus(OnboardingStatusResponse.StatusEnum.valueOf(currentStatus));
    }
    return statusResponse;
  }

  @Override
  public OnboardingImportCdEntitiesResponse importCdEntities(
      String harnessAccount, OnboardingImportCdEntitiesRequest onboardingImportCdEntitiesRequest) {
    OnboardingImportCdEntitiesRequest.TypeEnum type = onboardingImportCdEntitiesRequest.getType();
    List<String> entities = onboardingImportCdEntitiesRequest.getEntities();
    GitIntegrationRequest gitIntegrationRequest = onboardingImportCdEntitiesRequest.getWriteTo();
    setProperBaseUrlForImport(harnessAccount, gitIntegrationRequest);
    Optional<OnboardingFlowEntity> existingOnboardingFlowEntity = optionalOnboardingFlowEntity(harnessAccount);
    OnboardingFlowEntity onboardingFlowEntity = existingOnboardingFlowEntity.orElseGet(OnboardingFlowEntity::new);
    onboardingFlowEntity.setAccountIdentifier(harnessAccount);
    OnboardingImportCdEntitiesResponse onboardingImportCdEntitiesResponse = new OnboardingImportCdEntitiesResponse();
    if (type.equals(OnboardingImportCdEntitiesRequest.TypeEnum.SAMPLE)) {
      importSampleEntity(harnessAccount, gitIntegrationRequest, onboardingFlowEntity);
    } else {
      importAllOrSelectedEntities(harnessAccount, type, entities, gitIntegrationRequest, onboardingFlowEntity);
    }
    saveOnboardingStatusIfNotAlreadyCompleted(harnessAccount, STATUS_UPDATE_REASON_FOR_ONBOARDING_COMPLETED);
    onboardingImportCdEntitiesResponse.setStatus(SUCCESS_RESPONSE);
    return onboardingImportCdEntitiesResponse;
  }

  @Override
  public OnboardingSkipResponse postOnboardingSkip(String harnessAccount, OnboardingSkipRequest onboardingSkipRequest) {
    Optional<OnboardingFlowEntity> existingOnboardingFlowEntity = optionalOnboardingFlowEntity(harnessAccount);
    OnboardingFlowEntity onboardingFlowEntity;
    if (existingOnboardingFlowEntity.isEmpty()) {
      onboardingFlowEntity = new OnboardingFlowEntity();
      onboardingFlowEntity.setAccountIdentifier(harnessAccount);
    } else {
      onboardingFlowEntity = existingOnboardingFlowEntity.get();
    }
    onboardingFlowEntity.setSkippedAt(
        OnboardingFlowEntity.SkippedAt.valueOf(onboardingSkipRequest.getSkippedAt().name()));
    onboardingFlowEntity.setCurrentStatus(onboardingSkipRequest.getSkippedAt().name());
    onboardingFlowEntityRepository.save(onboardingFlowEntity);
    saveOnboardingStatusIfNotAlreadyCompleted(harnessAccount, STATUS_UPDATE_REASON_FOR_ONBOARDING_SKIPPED);
    OnboardingSkipResponse onboardingSkipResponse = new OnboardingSkipResponse();
    onboardingSkipResponse.setStatus(SUCCESS_RESPONSE);
    return onboardingSkipResponse;
  }

  private int getServicesTotalCount(String accountIdentifier) {
    PageResponse<ServiceResponse> services =
        getResponse(serviceResourceClient.getAllServicesList(accountIdentifier, null, null, null, 0, 1, null));
    return (int) services.getTotalItems();
  }

  private int checkAndRemoveAlreadyImportedCDEntitiesForCount(String harnessAccount, int servicesTotalCount) {
    Optional<OnboardingFlowEntity> existingOnboardingFlowEntity = optionalOnboardingFlowEntity(harnessAccount);
    if (existingOnboardingFlowEntity.isPresent()) {
      Set<String> importedCDEntities = emptyIfNull(existingOnboardingFlowEntity.get().getImportedCDEntities());
      for (String importedCDEntity : importedCDEntities) {
        if (importedCDEntity.split("-").length == 3) {
          servicesTotalCount--;
        }
      }
    }
    return servicesTotalCount;
  }

  private List<BackstageCatalogComponentEntity> harnessServiceToBackstageComponent(List<ServiceResponseDTO> services) {
    HarnessServiceToBackstageComponent harnessServiceToBackstageComponentMapper =
        (HarnessServiceToBackstageComponent) getMapperByType(SERVICE);
    harnessServiceToBackstageComponentMapper.entityNamesSeenSoFar.clear();
    return services.stream()
        .map(harnessServiceToBackstageComponentMapper::map)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private HarnessEntityToBackstageEntity<?, ? extends BackstageCatalogEntity> getMapperByType(String type) {
    switch (type) {
      case ORGANIZATION:
        return harnessOrgToBackstageDomain;
      case PROJECT:
        return harnessProjectToBackstageSystem;
      case SERVICE:
        return harnessServiceToBackstageComponent;
      default:
        throw new UnsupportedOperationException(type + " type not supported for harness to backstage entity mapping");
    }
  }

  private void checkAndRemoveAlreadyImportedCDEntities(
      String harnessAccount, List<CDEntityAsIdpEntity> cdEntitiesAsIdpEntities) {
    Optional<OnboardingFlowEntity> existingOnboardingFlowEntity = optionalOnboardingFlowEntity(harnessAccount);
    if (existingOnboardingFlowEntity.isPresent()) {
      OnboardingFlowEntity onboardingFlowEntity = existingOnboardingFlowEntity.get();
      if (!CollectionUtils.isEmpty(onboardingFlowEntity.getImportedCDEntities())) {
        Set<String> importedCDEntities = onboardingFlowEntity.getImportedCDEntities();
        cdEntitiesAsIdpEntities.removeIf(cdEntityAsIdpEntity
            -> importedCDEntities.contains(cdEntityAsIdpEntity.getHarnessAbsoluteIdentifier().replace("|", "-")));
      }
    }
  }

  private Optional<OnboardingFlowEntity> optionalOnboardingFlowEntity(String accountIdentifier) {
    return onboardingFlowEntityRepository.findByAccountIdentifier(accountIdentifier);
  }

  private Pair<Integer, Integer> getOrgAndProjectCountAfterRemovingAlreadyImported(
      OnboardingFlowEntity onboardingFlowEntity, List<CDEntityAsIdpEntity> cdEntitiesAsIdpEntities) {
    Set<String> alreadyImportedOrganizations = new HashSet<>();
    Set<String> alreadyImportedProjects = new HashSet<>();

    if (!CollectionUtils.isEmpty(onboardingFlowEntity.getImportedCDEntities())) {
      Set<String> importedCDEntities = onboardingFlowEntity.getImportedCDEntities();
      importedCDEntities.forEach(importedCDEntity -> {
        String[] orgProjectService = importedCDEntity.split("-");
        if (orgProjectService.length == 1) {
          alreadyImportedOrganizations.add(orgProjectService[0]);
        }
        if (orgProjectService.length > 1) {
          alreadyImportedOrganizations.add(orgProjectService[0]);
          alreadyImportedProjects.add(orgProjectService[1]);
        }
      });
    }

    Set<String> organizations = new HashSet<>();
    Set<String> projects = new HashSet<>();
    cdEntitiesAsIdpEntities.forEach(cdEntityAsIdpEntity -> {
      String[] orgProjectService = cdEntityAsIdpEntity.getHarnessAbsoluteIdentifier().split("\\|");
      if (!Objects.equals(orgProjectService[0], ENTITY_UNKNOWN_REF)
          && !alreadyImportedOrganizations.contains(orgProjectService[0])) {
        organizations.add(orgProjectService[0]);
      }
      if (!Objects.equals(orgProjectService[1], ENTITY_UNKNOWN_REF)
          && !alreadyImportedProjects.contains(orgProjectService[1])) {
        projects.add(orgProjectService[1]);
      }
    });

    return Pair.of(organizations.size(), projects.size());
  }

  private ServiceResponseDTO getService(
      String accountIdentifier, String organizationIdentifier, String projectIdentifier, String serviceIdentifier) {
    ServiceResponse services = getResponse(serviceResourceClient.getService(
        serviceIdentifier, accountIdentifier, organizationIdentifier, projectIdentifier));
    return services.getService();
  }

  private void setProperBaseUrlForImport(String accountIdentifier, GitIntegrationRequest gitIntegrationRequest) {
    if (gitIntegrationRequest.getConnectorIdentifier().equals(ACCOUNT_SCOPED + HCR_CONNECTOR_IDENTIFIER)) {
      WriteValidationDetails writeValidationDetails = gitIntegrationRequest.getWriteValidationDetails();
      String repository = writeValidationDetails.getRepository();
      URI repositoryUri;
      try {
        repositoryUri = new URI(repository);
      } catch (URISyntaxException e) {
        throw new UnexpectedException("Invalid repository URL for import");
      }
      String baseUrl = gitIntegrationService.getAccountBaseUrl(accountIdentifier);
      repository = baseUrl + repositoryUri.getPath();
      writeValidationDetails.setRepository(repository);
      gitIntegrationRequest.setWriteValidationDetails(writeValidationDetails);
    }
  }

  private void importSampleEntity(
      String harnessAccount, GitIntegrationRequest gitIntegrationRequest, OnboardingFlowEntity onboardingFlowEntity) {
    String sampleFile = "sample/sample-catalog-info.yaml";
    String sampleFileContent = readFileFromClassPath(ONBOARDING_SAMPLE_CATALOG_INFO);
    List<Pair<String, String>> files = Collections.singletonList(Pair.of(sampleFile, sampleFileContent));
    String connectorIdentifier = gitIntegrationRequest.getConnectorIdentifier();
    connectorIdentifier = replaceAccountScopeFromIdentifier(connectorIdentifier);
    ConnectorInfoDTO connectorInfoDTO =
        gitIntegrationService.connectorInfoDTO(harnessAccount, connectorIdentifier, gitIntegrationRequest);
    String gitIntegrationType = gitIntegrationService.getGitIntegrationType(connectorInfoDTO);
    gitIntegrationService.writeThroughAPI(harnessAccount, gitIntegrationRequest, files);
    Set<OnboardingFlowEntity.WriteDetails> writeDetails = emptyIfNull(onboardingFlowEntity.getWriteDetails());
    writeDetails.remove(OnboardingFlowEntity.from(gitIntegrationRequest));
    writeDetails.add(OnboardingFlowEntity.from(gitIntegrationRequest));
    onboardingFlowEntity.setWriteDetails(writeDetails);
    onboardingFlowEntity.setImportedSampleEntityDefinition(true);
    onboardingFlowEntity.setSkippedAt(OnboardingFlowEntity.SkippedAt.NA);
    onboardingFlowEntity.setCurrentStatus(ONBOARDING_COMPLETED_ALLOW_FURTHER);
    onboardingFlowEntity.setRegisterEntitiesOnIdpAt(onboardingFlowEntity.calculateRegisterEntitiesOnIdpAt());
    List<String> entitiesToRegisterOnIdp = emptyIfNull(onboardingFlowEntity.getEntitiesToRegisterOnIdp());
    entitiesToRegisterOnIdp.add(
        onboardingFlowEntity.idpCatalogSourceLocation(gitIntegrationType, gitIntegrationRequest, sampleFile));
    onboardingFlowEntity.setEntitiesToRegisterOnIdp(entitiesToRegisterOnIdp);
    onboardingFlowEntityRepository.save(onboardingFlowEntity);
  }

  private void importAllOrSelectedEntities(String harnessAccount, OnboardingImportCdEntitiesRequest.TypeEnum type,
      List<String> entities, GitIntegrationRequest gitIntegrationRequest, OnboardingFlowEntity onboardingFlowEntity) {
    if (type.equals(OnboardingImportCdEntitiesRequest.TypeEnum.ALL)) {
      entities = forAllEntitiesImport(harnessAccount);
    }

    ImmutableTriple<Set<String>, Map<String, Set<String>>, Map<String, Map<String, Set<String>>>>
        organizationsProjectsServices = organizationsProjectsServicesMapping(entities);

    Map<String, Map<String, Set<String>>> organizationProjectsServices = organizationsProjectsServices.getRight();
    Map.Entry<String, Map<String, Set<String>>> orgProjectsServices =
        organizationProjectsServices.entrySet().iterator().next();
    Map.Entry<String, Set<String>> orgProjectServices = orgProjectsServices.getValue().entrySet().iterator().next();

    String organization = orgProjectsServices.getKey();
    String project = orgProjectServices.getKey();
    String service = orgProjectServices.getValue().iterator().next();
    ServiceResponseDTO serviceResponseDTO = getService(harnessAccount, organization, project, service);
    List<BackstageCatalogComponentEntity> catalogComponents =
        harnessServiceToBackstageComponent(Collections.singletonList(serviceResponseDTO));
    BackstageCatalogComponentEntity catalogComponentEntity = catalogComponents.get(0);

    String file = SERVICE + SLASH_DELIMITER + catalogComponentEntity.getMetadata().get(NAME) + YAML_FILE_EXTENSION;
    String fileContent = writeObjectAsYaml(catalogComponentEntity);
    List<Pair<String, String>> files = Collections.singletonList(Pair.of(file, fileContent));

    String connectorIdentifier = gitIntegrationRequest.getConnectorIdentifier();
    connectorIdentifier = replaceAccountScopeFromIdentifier(connectorIdentifier);
    ConnectorInfoDTO connectorInfoDTO =
        gitIntegrationService.connectorInfoDTO(harnessAccount, connectorIdentifier, gitIntegrationRequest);
    String gitIntegrationType = gitIntegrationService.getGitIntegrationType(connectorInfoDTO);

    gitIntegrationService.writeThroughAPI(harnessAccount, gitIntegrationRequest, files);

    Set<OnboardingFlowEntity.WriteDetails> writeDetails = emptyIfNull(onboardingFlowEntity.getWriteDetails());
    writeDetails.remove(OnboardingFlowEntity.from(gitIntegrationRequest));
    writeDetails.add(OnboardingFlowEntity.from(gitIntegrationRequest));
    onboardingFlowEntity.setWriteDetails(writeDetails);
    Set<String> importedCDEntities = emptyIfNull(onboardingFlowEntity.getImportedCDEntities());
    if (!importedCDEntities.contains((organization == null ? ENTITY_UNKNOWN_REF : organization) + "-"
            + (project == null ? ENTITY_UNKNOWN_REF : project) + "-" + service)) {
      onboardingFlowEntity.setNumberOfCDEntitiesImported(onboardingFlowEntity.getNumberOfCDEntitiesImported() + 1);
    }
    importedCDEntities.add((String) catalogComponentEntity.getMetadata().get(ABSOLUTE_IDENTIFIER));
    onboardingFlowEntity.setImportedCDEntities(importedCDEntities);
    Map<String, Set<String>> importedCDEntitiesRef = emptyIfNull(onboardingFlowEntity.getImportedCDEntitiesRef());
    Set<String> existingImportedCDEntitiesRef = importedCDEntitiesRef.getOrDefault(
        (String) catalogComponentEntity.getMetadata().get(ABSOLUTE_IDENTIFIER), new HashSet<>());
    existingImportedCDEntitiesRef.add(
        onboardingFlowEntity.idpCatalogSourceLocation(gitIntegrationType, gitIntegrationRequest, file));
    importedCDEntitiesRef.put(
        (String) catalogComponentEntity.getMetadata().get(ABSOLUTE_IDENTIFIER), existingImportedCDEntitiesRef);
    onboardingFlowEntity.setImportedCDEntitiesRef(importedCDEntitiesRef);
    organizationsProjectsServices = replaceNullKeysWithUnknown(organizationsProjectsServices);
    onboardingFlowEntity.setEntitiesToImport(organizationsProjectsServices);
    onboardingFlowEntity.setSkippedAt(OnboardingFlowEntity.SkippedAt.NA);
    onboardingFlowEntity.setCurrentStatus(ONBOARDING_COMPLETED_ALLOW_FURTHER);
    onboardingFlowEntity.setRegisterEntitiesOnIdpAt(onboardingFlowEntity.calculateRegisterEntitiesOnIdpAt());
    List<String> entitiesToRegisterOnIdp = emptyIfNull(onboardingFlowEntity.getEntitiesToRegisterOnIdp());
    entitiesToRegisterOnIdp.add(
        onboardingFlowEntity.idpCatalogSourceLocation(gitIntegrationType, gitIntegrationRequest, file));
    onboardingFlowEntity.setEntitiesToRegisterOnIdp(entitiesToRegisterOnIdp);
    onboardingFlowEntityRepository.save(onboardingFlowEntity);
  }

  private List<String> forAllEntitiesImport(String accountIdentifier) {
    List<String> entities = new ArrayList<>();
    List<ServiceResponseDTO> services = getServices(accountIdentifier, null, null, null);
    List<BackstageCatalogComponentEntity> components = harnessServiceToBackstageComponent(services);
    for (BackstageCatalogComponentEntity component : components) {
      entities.add(((String) component.getMetadata().get("absoluteIdentifier")).replace("-", "|"));
    }
    return entities;
  }

  private List<ServiceResponseDTO> getServices(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String searchTerm) {
    List<ServiceResponseDTO> serviceResponseDTOS = new ArrayList<>();
    PageResponse<ServiceResponse> services;
    int page = 0;
    do {
      services = getResponse(serviceResourceClient.getAllServicesList(
          accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, page, PAGE_LIMIT_FOR_ENTITY_FETCH, null));
      if (services != null && isNotEmpty(services.getContent())) {
        serviceResponseDTOS.addAll(services.getContent().stream().map(ServiceResponse::getService).toList());
      }
      page++;
    } while (services != null && isNotEmpty(services.getContent()));
    return serviceResponseDTOS;
  }

  private ImmutableTriple<Set<String>, Map<String, Set<String>>, Map<String, Map<String, Set<String>>>>
  organizationsProjectsServicesMapping(List<String> entities) {
    Set<String> organizations = new HashSet<>();
    Map<String, Set<String>> organizationProjects = new HashMap<>();
    Map<String, Map<String, Set<String>>> organizationProjectsServices = new HashMap<>();

    entities.forEach(entity -> {
      String[] orgProjectService = entity.split("\\|");

      String organization = nullIfUnknown(orgProjectService[0]);
      String project = orgProjectService.length >= 2 ? nullIfUnknown(orgProjectService[1]) : null;
      String service = orgProjectService.length == 3 ? orgProjectService[2] : null;

      if (organization != null) {
        organizations.add(organization);
      }

      if (organization != null && project != null) {
        organizationProjects.computeIfAbsent(organization, k -> new HashSet<>()).add(project);
      }

      organizationProjectsServices.computeIfAbsent(organization, k -> new HashMap<>())
          .computeIfAbsent(project, k -> new HashSet<>())
          .add(service);
    });

    return ImmutableTriple.of(organizations, organizationProjects, organizationProjectsServices);
  }

  private String nullIfUnknown(String entity) {
    return entity.equals(ENTITY_UNKNOWN_REF) ? null : entity;
  }

  private ImmutableTriple<Set<String>, Map<String, Set<String>>, Map<String, Map<String, Set<String>>>>
  replaceNullKeysWithUnknown(Triple<Set<String>, Map<String, Set<String>>, Map<String, Map<String, Set<String>>>>
          organizationsProjectsServices) {
    Map<String, Map<String, Set<String>>> organizationProjectsServices = organizationsProjectsServices.getRight();

    Map<String, Map<String, Set<String>>> organizationProjectsServicesCopy =
        new HashMap<>(organizationProjectsServices);
    organizationProjectsServicesCopy.forEach((k, v) -> {
      if (k == null) {
        organizationProjectsServices.remove(null);
        organizationProjectsServices.put(ENTITY_UNKNOWN_REF, v);
      }
      Map<String, Set<String>> vCopy = new HashMap<>(v);
      vCopy.forEach((i, j) -> {
        if (i == null) {
          v.remove(null);
          v.put(ENTITY_UNKNOWN_REF, j);
          organizationProjectsServices.put(k == null ? ENTITY_UNKNOWN_REF : k, v);
        }
      });
    });

    return ImmutableTriple.of(organizationsProjectsServices.getLeft(), organizationsProjectsServices.getMiddle(),
        organizationProjectsServices);
  }

  private void saveOnboardingStatusIfNotAlreadyCompleted(String accountIdentifier, String reason) {
    StatusInfoV2 statusInfoV2 =
        statusInfoService.findByAccountIdentifierAndTypeV2(accountIdentifier, StatusType.ONBOARDING.name());
    StatusInfo statusInfo = statusInfoV2.get(StatusType.ONBOARDING.name().toLowerCase());
    if (statusInfo != null && !StatusInfo.CurrentStatusEnum.COMPLETED.equals(statusInfo.getCurrentStatus())) {
      statusInfoService.save(prepareStatusInfo(reason), accountIdentifier, StatusType.ONBOARDING.name());
    }
  }

  private StatusInfo prepareStatusInfo(String reason) {
    StatusInfo statusInfo = new StatusInfo();
    statusInfo.setCurrentStatus(StatusInfo.CurrentStatusEnum.COMPLETED);
    statusInfo.setReason(reason);
    return statusInfo;
  }

  public void asyncImport() {
    List<OnboardingFlowEntity> onboardingFlowEntities =
        onboardingFlowEntityRepository.findByRegisterEntitiesOnIdpAtNot(Long.MAX_VALUE);
    onboardingFlowEntities.forEach(onboardingFlowEntity -> {
      String accountIdentifier = onboardingFlowEntity.getAccountIdentifier();
      try {
        if (!Objects.isNull(onboardingFlowEntity.getEntitiesToImport())) {
          Triple<Set<String>, Map<String, Set<String>>, Map<String, Map<String, Set<String>>>> entitiesToImport =
              onboardingFlowEntity.getEntitiesToImport();
          List<OrganizationDTO> organizationDTOS = getOrganizations(accountIdentifier, entitiesToImport.getLeft());
          List<ProjectDTO> projectDTOS = getProjectsByOrganization(accountIdentifier, entitiesToImport.getMiddle());
          List<ServiceResponseDTO> serviceResponseDTOS = getServices(accountIdentifier, entitiesToImport.getRight());

          List<BackstageCatalogDomainEntity> catalogDomainEntities = harnessOrgToBackstageDomain(organizationDTOS);
          List<BackstageCatalogSystemEntity> catalogSystemEntities = harnessProjectToBackstageSystem(projectDTOS);
          List<BackstageCatalogComponentEntity> catalogComponentEntities =
              harnessServiceToBackstageComponent(serviceResponseDTOS);

          GitIntegrationRequest gitIntegrationRequest =
              OnboardingFlowEntity.from(new ArrayList<>(onboardingFlowEntity.getWriteDetails())
                                            .get(onboardingFlowEntity.getWriteDetails().size() - 1));

          String connectorIdentifier = gitIntegrationRequest.getConnectorIdentifier();
          connectorIdentifier = replaceAccountScopeFromIdentifier(connectorIdentifier);
          ConnectorInfoDTO connectorInfoDTO =
              gitIntegrationService.connectorInfoDTO(accountIdentifier, connectorIdentifier, gitIntegrationRequest);
          String gitIntegrationType = gitIntegrationService.getGitIntegrationType(connectorInfoDTO);

          List<Pair<String, String>> files = new ArrayList<>();
          Set<String> importedCDEntities = emptyIfNull(onboardingFlowEntity.getImportedCDEntities());
          Map<String, Set<String>> importedCDEntitiesRef = emptyIfNull(onboardingFlowEntity.getImportedCDEntitiesRef());
          List<String> entitiesToRegisterOnIdp = emptyIfNull(onboardingFlowEntity.getEntitiesToRegisterOnIdp());
          catalogDomainEntities.forEach(backstageCatalogDomainEntity -> {
            String name = (String) backstageCatalogDomainEntity.getMetadata().get(NAME);
            String file = ORGANIZATION + SLASH_DELIMITER + name + YAML_FILE_EXTENSION;
            String fileContent = writeObjectAsYaml(backstageCatalogDomainEntity);
            files.add(Pair.of(file, fileContent));
            String absoluteIdentifier = (String) backstageCatalogDomainEntity.getMetadata().get(ABSOLUTE_IDENTIFIER);
            importedCDEntities.add(absoluteIdentifier);
            Set<String> existingImportedRef = importedCDEntitiesRef.getOrDefault(absoluteIdentifier, new HashSet<>());
            String catalogLocation =
                onboardingFlowEntity.idpCatalogSourceLocation(gitIntegrationType, gitIntegrationRequest, file);
            existingImportedRef.add(catalogLocation);
            importedCDEntitiesRef.put(absoluteIdentifier, existingImportedRef);
            entitiesToRegisterOnIdp.add(catalogLocation);
          });
          catalogSystemEntities.forEach(backstageCatalogSystemEntity -> {
            String name = (String) backstageCatalogSystemEntity.getMetadata().get(NAME);
            String file = PROJECT + SLASH_DELIMITER + name + YAML_FILE_EXTENSION;
            String fileContent = writeObjectAsYaml(backstageCatalogSystemEntity);
            files.add(Pair.of(file, fileContent));
            String absoluteIdentifier = (String) backstageCatalogSystemEntity.getMetadata().get(ABSOLUTE_IDENTIFIER);
            importedCDEntities.add(absoluteIdentifier);
            Set<String> existingImportedRef = importedCDEntitiesRef.getOrDefault(absoluteIdentifier, new HashSet<>());
            String catalogLocation =
                onboardingFlowEntity.idpCatalogSourceLocation(gitIntegrationType, gitIntegrationRequest, file);
            existingImportedRef.add(catalogLocation);
            importedCDEntitiesRef.put(absoluteIdentifier, existingImportedRef);
            entitiesToRegisterOnIdp.add(catalogLocation);
          });
          catalogComponentEntities.forEach(backstageCatalogComponentEntity -> {
            String name = (String) backstageCatalogComponentEntity.getMetadata().get(NAME);
            String file = SERVICE + SLASH_DELIMITER + name + YAML_FILE_EXTENSION;
            String fileContent = writeObjectAsYaml(backstageCatalogComponentEntity);
            files.add(Pair.of(file, fileContent));
            String absoluteIdentifier = (String) backstageCatalogComponentEntity.getMetadata().get(ABSOLUTE_IDENTIFIER);
            importedCDEntities.add(absoluteIdentifier);
            Set<String> existingImportedRef = importedCDEntitiesRef.getOrDefault(absoluteIdentifier, new HashSet<>());
            String catalogLocation =
                onboardingFlowEntity.idpCatalogSourceLocation(gitIntegrationType, gitIntegrationRequest, file);
            existingImportedRef.add(catalogLocation);
            importedCDEntitiesRef.put(absoluteIdentifier, existingImportedRef);
            entitiesToRegisterOnIdp.add(catalogLocation);
          });

          UserPrincipal userPrincipal = new UserPrincipal(onboardingFlowEntity.getLastUpdatedBy().getUuid(),
              onboardingFlowEntity.getLastUpdatedBy().getEmail(), onboardingFlowEntity.getLastUpdatedBy().getName(),
              accountIdentifier);
          SourcePrincipalContextBuilder.setSourcePrincipal(userPrincipal);

          gitIntegrationService.writeThroughAPI(accountIdentifier, gitIntegrationRequest, files);

          registerLocationInBackstage(accountIdentifier, BACKSTAGE_LOCATION_URL_TYPE, entitiesToRegisterOnIdp);

          onboardingFlowEntity.setSkippedAt(OnboardingFlowEntity.SkippedAt.NA);
          onboardingFlowEntity.setNumberOfCDEntitiesImported(importedCDEntities.size());
          onboardingFlowEntity.setImportedCDEntities(importedCDEntities);
          onboardingFlowEntity.setImportedCDEntitiesRef(importedCDEntitiesRef);
          onboardingFlowEntity.setEntitiesToImport(null);
          onboardingFlowEntity.setRegisterEntitiesOnIdpAt(Long.MAX_VALUE);
          onboardingFlowEntity.setEntitiesToRegisterOnIdp(null);
          onboardingFlowEntity.setCurrentStatus(ONBOARDING_COMPLETED_ALLOW_FURTHER);
          onboardingFlowEntityRepository.save(onboardingFlowEntity);
        }
        if (!CollectionUtils.isEmpty(onboardingFlowEntity.getEntitiesToRegisterOnIdp())) {
          registerLocationInBackstage(
              accountIdentifier, BACKSTAGE_LOCATION_URL_TYPE, onboardingFlowEntity.getEntitiesToRegisterOnIdp());
          onboardingFlowEntity.setSkippedAt(OnboardingFlowEntity.SkippedAt.NA);
          onboardingFlowEntity.setRegisterEntitiesOnIdpAt(Long.MAX_VALUE);
          onboardingFlowEntity.setEntitiesToRegisterOnIdp(null);
          onboardingFlowEntity.setCurrentStatus(ONBOARDING_COMPLETED_ALLOW_FURTHER);
          onboardingFlowEntityRepository.save(onboardingFlowEntity);
        }
      } catch (Exception ex) {
        log.error(
            "Exception in asyncImport for accountIdentifier = {} Error = {}", accountIdentifier, ex.getMessage(), ex);
      }
    });
  }

  private List<OrganizationDTO> getOrganizations(String accountIdentifier, Set<String> identifiers) {
    List<OrganizationDTO> organizationDTOS = new ArrayList<>();
    if (identifiers.isEmpty()) {
      return organizationDTOS;
    }
    PageResponse<OrganizationResponse> organizations;
    int page = 0;
    do {
      organizations = getResponse(organizationClient.listOrganization(
          accountIdentifier, identifiers.stream().toList(), null, page, PAGE_LIMIT_FOR_ENTITY_FETCH, null));
      if (organizations != null && isNotEmpty(organizations.getContent())) {
        organizationDTOS.addAll(
            organizations.getContent().stream().map(OrganizationResponse::getOrganization).toList());
      }
      page++;
    } while (organizations != null && isNotEmpty(organizations.getContent()));
    return organizationDTOS;
  }

  private List<ProjectDTO> getProjectsByOrganization(
      String accountIdentifier, Map<String, Set<String>> orgProjectsMapping) {
    List<ProjectDTO> projectDTOS = new ArrayList<>();
    for (var projectIdentifier : orgProjectsMapping.entrySet()) {
      String org = projectIdentifier.getKey();
      Optional<OrganizationResponse> organizationResponse = Optional.empty();
      try {
        organizationResponse = getResponse(organizationClient.getOrganization(org, accountIdentifier));
      } catch (Exception ignored) {
      }
      if (organizationResponse.isPresent()) {
        PageResponse<ProjectResponse> projects;
        int page = 0;
        do {
          projects = getResponse(projectClient.listProjects(accountIdentifier, org,
              new ArrayList<>(projectIdentifier.getValue()), page, PAGE_LIMIT_FOR_ENTITY_FETCH));
          if (projects != null && isNotEmpty(projects.getContent())) {
            projectDTOS.addAll(projects.getContent().stream().map(ProjectResponse::getProject).toList());
          }
          page++;
        } while (projects != null && isNotEmpty(projects.getContent()));
      }
    }
    return projectDTOS;
  }

  private List<ServiceResponseDTO> getServices(
      String accountIdentifier, Map<String, Map<String, Set<String>>> orgProjectsServicesMapping) {
    List<ServiceResponseDTO> serviceResponseDTOS = new ArrayList<>();
    for (var serviceIdentifier : orgProjectsServicesMapping.entrySet()) {
      String org = nullIfUnknown(serviceIdentifier.getKey());
      Optional<OrganizationResponse> organizationResponse = Optional.empty();
      if (org != null) {
        try {
          organizationResponse = getResponse(organizationClient.getOrganization(org, accountIdentifier));
        } catch (Exception ignored) {
        }
      }
      if (org == null || organizationResponse.isPresent()) {
        for (var projectService : serviceIdentifier.getValue().entrySet()) {
          String project = nullIfUnknown(projectService.getKey());
          Optional<ProjectResponse> projectResponse = Optional.empty();
          if (project != null) {
            try {
              projectResponse = getResponse(projectClient.getProject(project, accountIdentifier, org));
            } catch (Exception ignored) {
            }
          }
          if (project == null || projectResponse.isPresent()) {
            PageResponse<ServiceResponse> services;
            int page = 0;
            do {
              services = getResponse(serviceResourceClient.listServicesForProject(page, PAGE_LIMIT_FOR_ENTITY_FETCH,
                  accountIdentifier, org, project, new ArrayList<>(projectService.getValue()), null));
              if (services != null && isNotEmpty(services.getContent())) {
                serviceResponseDTOS.addAll(services.getContent().stream().map(ServiceResponse::getService).toList());
              }
              page++;
            } while (services != null && isNotEmpty(services.getContent()));
          }
        }
      }
    }
    return serviceResponseDTOS;
  }

  private List<BackstageCatalogDomainEntity> harnessOrgToBackstageDomain(List<OrganizationDTO> organizationDTOList) {
    HarnessOrgToBackstageDomain mapper = (HarnessOrgToBackstageDomain) getMapperByType(ORGANIZATION);
    mapper.entityNamesSeenSoFar.clear();
    return organizationDTOList.stream().map(mapper::map).collect(Collectors.toList());
  }

  private List<BackstageCatalogSystemEntity> harnessProjectToBackstageSystem(List<ProjectDTO> projectDTOList) {
    HarnessProjectToBackstageSystem mapper = (HarnessProjectToBackstageSystem) getMapperByType(PROJECT);
    mapper.entityNamesSeenSoFar.clear();
    return projectDTOList.stream().map(mapper::map).collect(Collectors.toList());
  }

  public void registerLocationInBackstage(String accountIdentifier, String type, List<String> targets) {
    for (String target : targets) {
      try {
        getGeneralResponse(backstageResourceClient.createCatalogLocation(
            accountIdentifier, new BackstageCatalogLocationCreateRequest(type, target)));
      } catch (Exception e) {
        log.error("Unable to register target of type = {} with location = {} in backstage, ex = {}", type, target,
            e.getMessage(), e);
      }
    }
  }
}

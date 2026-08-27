/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.backstage.Constants.ORGANIZATION;
import static io.harness.idp.backstage.Constants.PROJECT;
import static io.harness.idp.backstage.Constants.SERVICE;
import static io.harness.idp.common.CommonUtils.readFileFromClassPath;
import static io.harness.idp.common.Constants.SLASH_DELIMITER;
import static io.harness.idp.common.YamlUtils.writeObjectAsYaml;
import static io.harness.idp.onboarding.utils.Constants.BACKSTAGE_LOCATION_URL_TYPE;
import static io.harness.idp.onboarding.utils.Constants.ENTITY_UNKNOWN_REF;
import static io.harness.idp.onboarding.utils.Constants.PAGE_LIMIT_FOR_ENTITY_FETCH;
import static io.harness.idp.onboarding.utils.Constants.SAMPLE_ENTITY_CLASSPATH_LOCATION;
import static io.harness.idp.onboarding.utils.Constants.SUCCESS_RESPONSE_STRING;
import static io.harness.idp.onboarding.utils.Constants.YAML_FILE_EXTENSION;
import static io.harness.idp.onboarding.utils.FileUtils.cleanUpDirectories;
import static io.harness.idp.onboarding.utils.FileUtils.createDirectories;
import static io.harness.idp.onboarding.utils.FileUtils.writeObjectAsYamlInFile;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.clients.BackstageCatalogLocationCreateRequest;
import io.harness.clients.BackstageResourceClient;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.gitintegration.processor.base.ConnectorProcessor;
import io.harness.idp.gitintegration.processor.factory.ConnectorProcessorFactory;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.onboarding.beans.AsyncCatalogImportDetails;
import io.harness.idp.onboarding.config.OnboardingModuleConfig;
import io.harness.idp.onboarding.entities.AsyncCatalogImportEntity;
import io.harness.idp.onboarding.mappers.HarnessEntityToBackstageEntity;
import io.harness.idp.onboarding.mappers.HarnessOrgToBackstageDomain;
import io.harness.idp.onboarding.mappers.HarnessProjectToBackstageSystem;
import io.harness.idp.onboarding.mappers.HarnessServiceToBackstageComponent;
import io.harness.idp.onboarding.repositories.AsyncCatalogImportRepository;
import io.harness.idp.onboarding.service.OnboardingService;
import io.harness.idp.onboarding.service.OnboardingServiceV2;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.service.remote.ServiceResourceClient;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;
import io.harness.spec.server.idp.v1.model.EntitiesForImport;
import io.harness.spec.server.idp.v1.model.GenerateYamlRequest;
import io.harness.spec.server.idp.v1.model.GenerateYamlResponse;
import io.harness.spec.server.idp.v1.model.GenerateYamlResponseGeneratedYaml;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;
import io.harness.spec.server.idp.v1.model.HarnessBackstageEntities;
import io.harness.spec.server.idp.v1.model.HarnessEntitiesCountResponse;
import io.harness.spec.server.idp.v1.model.ImportEntitiesBase;
import io.harness.spec.server.idp.v1.model.ImportEntitiesResponse;
import io.harness.spec.server.idp.v1.model.IndividualEntitiesImport;
import io.harness.spec.server.idp.v1.model.OnboardingImportCdEntitiesRequest;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class OnboardingServiceImpl implements OnboardingService {
  @Inject @Named("onboardingModuleConfig") OnboardingModuleConfig onboardingModuleConfig;
  @Inject @Named("PRIVILEGED") OrganizationClient organizationClient;
  @Inject @Named("PRIVILEGED") ProjectClient projectClient;
  @Inject ServiceResourceClient serviceResourceClient;
  @Inject HarnessOrgToBackstageDomain harnessOrgToBackstageDomain;
  @Inject HarnessProjectToBackstageSystem harnessProjectToBackstageSystem;
  @Inject HarnessServiceToBackstageComponent harnessServiceToBackstageComponent;
  @Inject ConnectorProcessorFactory connectorProcessorFactory;
  @Inject GitIntegrationServiceImpl gitIntegrationServiceImpl;
  @Inject BackstageResourceClient backstageResourceClient;
  @Inject AsyncCatalogImportRepository asyncCatalogImportRepository;
  @Inject OnboardingServiceV2 onboardingServiceV2;

  @Override
  public HarnessEntitiesCountResponse getHarnessEntitiesCount(String accountIdentifier) {
    long organizationsTotalCount = getOrganizationsTotalCount(accountIdentifier);
    long projectsTotalCount = getProjectsTotalCount(accountIdentifier);
    long servicesTotalCount = getServicesTotalCount(accountIdentifier);
    log.info("Found {} organizations, {} projects, {} services for IDP onboarding import", organizationsTotalCount,
        projectsTotalCount, servicesTotalCount);

    HarnessEntitiesCountResponse harnessEntitiesCountResponse = new HarnessEntitiesCountResponse();

    harnessEntitiesCountResponse.setOrgCount((int) organizationsTotalCount);
    harnessEntitiesCountResponse.setProjectCount((int) projectsTotalCount);
    harnessEntitiesCountResponse.setServiceCount((int) servicesTotalCount);

    return harnessEntitiesCountResponse;
  }

  @Override
  public PageResponse<HarnessBackstageEntities> getHarnessEntities(String accountIdentifier, int page, int limit,
      String sort, String order, String searchTerm, String projectToFilter) {
    List<ServiceResponseDTO> services = getServices(accountIdentifier, searchTerm);
    services = filterByProject.apply(services, projectToFilter);

    List<BackstageCatalogComponentEntity> catalogComponents = harnessServiceToBackstageComponent(services);
    log.info("Mapped harness entities to backstage entities for IDP onboarding import");

    List<HarnessBackstageEntities> harnessBackstageEntities = new ArrayList<>();
    harnessBackstageEntities.addAll(BackstageCatalogComponentEntity.map(catalogComponents));
    log.info("Converted harness backstage entities to response view");

    return PageUtils.offsetAndLimit(harnessBackstageEntities, page, limit);
  }

  @Override
  public GenerateYamlResponse generateYaml(String harnessAccount, GenerateYamlRequest generateYamlRequest) {
    List<EntitiesForImport> entities = generateYamlRequest.getEntities();
    GenerateYamlResponse generateYamlResponse = new GenerateYamlResponse();
    GenerateYamlResponseGeneratedYaml generatedYaml = new GenerateYamlResponseGeneratedYaml();
    if (!entities.isEmpty()) {
      Map<String, Map<String, List<String>>> orgProjectsServicesMapping = getOrgProjectsServicesMapping(
          entities.stream().map(EntitiesForImport::getIdentifier).collect(Collectors.toList()));
      List<ServiceResponseDTO> serviceResponseDTOS = getServiceDTOS(harnessAccount, orgProjectsServicesMapping);
      if (serviceResponseDTOS.size() > 0) {
        ServiceResponseDTO serviceResponseDTO = getServiceDTOS(harnessAccount, orgProjectsServicesMapping).get(0);
        BackstageCatalogComponentEntity backstageCatalogComponentEntity =
            harnessServiceToBackstageComponent(Collections.singletonList(serviceResponseDTO)).get(0);
        generatedYaml.setYamlDef(writeObjectAsYaml(backstageCatalogComponentEntity));
        generatedYaml.setDescription(onboardingModuleConfig.getDescriptionForEntitySelected());
      } else {
        setSampleEntityDetails(generatedYaml);
      }
    } else {
      setSampleEntityDetails(generatedYaml);
    }
    generateYamlResponse.setGeneratedYaml(generatedYaml);
    return generateYamlResponse;
  }

  @Override
  public ImportEntitiesResponse importHarnessEntities(
      String accountIdentifier, ImportEntitiesBase importHarnessEntitiesRequest) {
    OnboardingImportCdEntitiesRequest onboardingImportCdEntitiesRequest = new OnboardingImportCdEntitiesRequest();
    GitIntegrationRequest gitIntegrationRequest = new GitIntegrationRequest();
    gitIntegrationRequest.setType(BaseIntegrationRequest.TypeEnum.GIT);
    CatalogConnectorInfo catalogConnectorInfo = importHarnessEntitiesRequest.getCatalogConnectorInfo();
    gitIntegrationRequest.setConnectorIdentifier(catalogConnectorInfo.getConnector().getIdentifier());
    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository(catalogConnectorInfo.getRepo());
    writeValidationDetails.setBranch(catalogConnectorInfo.getBranch());
    writeValidationDetails.setPath(catalogConnectorInfo.getPath());
    gitIntegrationRequest.setWriteValidationDetails(writeValidationDetails);
    ImportEntitiesBase.TypeEnum type = importHarnessEntitiesRequest.getType();
    OnboardingImportCdEntitiesRequest.TypeEnum typeV2 = null;
    if (type.equals(ImportEntitiesBase.TypeEnum.SAMPLE)) {
      typeV2 = OnboardingImportCdEntitiesRequest.TypeEnum.SAMPLE;
    }
    if (type.equals(ImportEntitiesBase.TypeEnum.ALL)) {
      typeV2 = OnboardingImportCdEntitiesRequest.TypeEnum.ALL;
    }
    if (type.equals(ImportEntitiesBase.TypeEnum.INDIVIDUAL)) {
      typeV2 = OnboardingImportCdEntitiesRequest.TypeEnum.SELECTED;
    }
    List<String> entities = new ArrayList<>();
    if (typeV2 == OnboardingImportCdEntitiesRequest.TypeEnum.SELECTED) {
      List<EntitiesForImport> idpSaveHarnessEntities =
          ((IndividualEntitiesImport) importHarnessEntitiesRequest).getEntities();
      idpSaveHarnessEntities.forEach(idpSaveHarnessEntity -> entities.add(idpSaveHarnessEntity.getIdentifier()));
    }
    onboardingImportCdEntitiesRequest.setType(typeV2);
    onboardingImportCdEntitiesRequest.setEntities(entities);
    onboardingImportCdEntitiesRequest.setWriteTo(gitIntegrationRequest);
    onboardingServiceV2.importCdEntities(accountIdentifier, onboardingImportCdEntitiesRequest);
    saveCatalogConnector(accountIdentifier, catalogConnectorInfo);

    return new ImportEntitiesResponse().status(SUCCESS_RESPONSE_STRING);
  }

  public void asyncCatalogImport(EntityChangeDTO entityChangeDTO) {
    log.info("Starting async operations for remaining entities import");

    try {
      String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();

      AsyncCatalogImportEntity asyncCatalogImportEntity =
          asyncCatalogImportRepository.findByAccountIdentifier(accountIdentifier);

      AsyncCatalogImportDetails catalogDomains = asyncCatalogImportEntity.getCatalogDomains();
      AsyncCatalogImportDetails catalogSystems = asyncCatalogImportEntity.getCatalogSystems();
      AsyncCatalogImportDetails catalogComponents = asyncCatalogImportEntity.getCatalogComponents();
      CatalogConnectorInfo catalogConnectorInfo = asyncCatalogImportEntity.getCatalogConnectorInfo();
      SourcePrincipalContextBuilder.setSourcePrincipal(asyncCatalogImportEntity.getUserPrincipal());

      String orgYamlPath = catalogDomains.getYamlPath();
      String projectYamlPath = catalogSystems.getYamlPath();
      String serviceYamlPath = catalogComponents.getYamlPath();

      createDirectories(orgYamlPath, projectYamlPath, serviceYamlPath);

      List<String> filesToPush = new ArrayList<>();
      List<String> targets;
      List<String> locationTargets = new ArrayList<>();

      ConnectorProcessor connectorProcessor = connectorProcessorFactory.getConnectorProcessor(
          ConnectorType.fromString(String.valueOf(catalogConnectorInfo.getConnector().getType())));

      filesToPush.addAll(writeEntityAsYamlInFile(catalogDomains.getEntities(), orgYamlPath));
      targets = prepareEntitiesTarget(connectorProcessor, catalogConnectorInfo, catalogDomains.getEntities(),
          catalogDomains.getEntityTargetParentPath());
      locationTargets.addAll(targets);

      filesToPush.addAll(writeEntityAsYamlInFile(catalogSystems.getEntities(), projectYamlPath));
      targets = prepareEntitiesTarget(connectorProcessor, catalogConnectorInfo, catalogSystems.getEntities(),
          catalogSystems.getEntityTargetParentPath());
      locationTargets.addAll(targets);

      filesToPush.addAll(writeEntityAsYamlInFile(catalogComponents.getEntities(), serviceYamlPath));
      targets = prepareEntitiesTarget(connectorProcessor, catalogConnectorInfo, catalogComponents.getEntities(),
          catalogComponents.getEntityTargetParentPath());
      locationTargets.addAll(targets);

      connectorProcessor.performPushOperation(accountIdentifier, catalogConnectorInfo,
          onboardingModuleConfig.getTmpPathForCatalogInfoYamlStore() + SLASH_DELIMITER + accountIdentifier, filesToPush,
          false);

      registerLocationInBackstage(accountIdentifier, BACKSTAGE_LOCATION_URL_TYPE, locationTargets);

      log.info("Cleaning up directories created during IDP async onboarding");
      cleanUpDirectories(orgYamlPath, serviceYamlPath, serviceYamlPath);

      log.info("Finished async operation of yaml generation, pushing to source, registering in backstage, "
          + "creating connector secret in K8S for all entities");
    } catch (Exception ex) {
      log.error(
          "Error in asyncCatalogImport for entityChangeDTO = {} with error = {}", entityChangeDTO, ex.getMessage(), ex);
    }
  }

  private long getOrganizationsTotalCount(String accountIdentifier) {
    PageResponse<OrganizationResponse> organizations =
        getResponse(organizationClient.listOrganization(accountIdentifier, null, null, 0, 1, null));
    return organizations.getTotalItems();
  }

  private long getProjectsTotalCount(String accountIdentifier) {
    PageResponse<ProjectResponse> projects =
        getResponse(projectClient.listProject(accountIdentifier, null, false, null, null, 0, 1, null));
    return projects.getTotalItems();
  }

  private long getServicesTotalCount(String accountIdentifier) {
    PageResponse<ServiceResponse> services =
        getResponse(serviceResourceClient.getAllServicesList(accountIdentifier, null, null, null, 0, 1, null));
    return services.getTotalItems();
  }

  private List<ServiceResponseDTO> getServices(String accountIdentifier, String searchTerm) {
    List<ServiceResponseDTO> serviceResponseDTOS = new ArrayList<>();
    PageResponse<ServiceResponse> services;
    int page = 0;
    do {
      services = getResponse(serviceResourceClient.getAllServicesList(
          accountIdentifier, null, null, searchTerm, page, PAGE_LIMIT_FOR_ENTITY_FETCH, null));
      if (services != null && isNotEmpty(services.getContent())) {
        serviceResponseDTOS.addAll(
            services.getContent().stream().map(ServiceResponse::getService).collect(Collectors.toList()));
      }
      page++;
    } while (services != null && isNotEmpty(services.getContent()));
    return serviceResponseDTOS;
  }

  private final BiFunction<List<ServiceResponseDTO>, String, List<ServiceResponseDTO>> filterByProject =
      (services, projectToFilter) -> {
    if (!isEmpty(projectToFilter)) {
      return services.stream()
          .filter(service
              -> service.getProjectIdentifier() != null && service.getProjectIdentifier().contains(projectToFilter))
          .collect(Collectors.toList());
    }
    return services;
  };

  private List<BackstageCatalogComponentEntity> harnessServiceToBackstageComponent(
      List<ServiceResponseDTO> serviceResponseDTOList) {
    HarnessServiceToBackstageComponent harnessServiceToBackstageComponentMapper =
        (HarnessServiceToBackstageComponent) getMapperByType(SERVICE);
    harnessServiceToBackstageComponentMapper.entityNamesSeenSoFar.clear();
    return serviceResponseDTOList.stream()
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

  private void setSampleEntityDetails(GenerateYamlResponseGeneratedYaml generatedYaml) {
    generatedYaml.setYamlDef(readFileFromClassPath(SAMPLE_ENTITY_CLASSPATH_LOCATION));
    generatedYaml.setDescription(onboardingModuleConfig.getDescriptionForSampleEntity());
  }

  private Map<String, Map<String, List<String>>> getOrgProjectsServicesMapping(List<String> harnessEntitiesServices) {
    Map<String, Map<String, List<String>>> serviceIdentifiers = new HashMap<>();
    harnessEntitiesServices.forEach(service -> {
      String[] orgProjectService = service.split("\\|");
      orgProjectService[0] = orgProjectService[0].equals(ENTITY_UNKNOWN_REF) ? null : orgProjectService[0];
      orgProjectService[1] = orgProjectService[1].equals(ENTITY_UNKNOWN_REF) ? null : orgProjectService[1];
      if (serviceIdentifiers.containsKey(orgProjectService[0])) {
        Map<String, List<String>> existingProjectsServices =
            new HashMap<>(serviceIdentifiers.get(orgProjectService[0]));
        if (existingProjectsServices.containsKey(orgProjectService[1])) {
          List<String> existingServices = new ArrayList<>(existingProjectsServices.get(orgProjectService[1]));
          existingServices.add(orgProjectService[2]);
          existingProjectsServices.put(orgProjectService[1], existingServices);
          serviceIdentifiers.put(orgProjectService[0], existingProjectsServices);
        } else {
          existingProjectsServices.put(orgProjectService[1], Collections.singletonList(orgProjectService[2]));
          serviceIdentifiers.put(orgProjectService[0], existingProjectsServices);
        }
      } else {
        Map<String, List<String>> map = new HashMap<>();
        map.put(orgProjectService[1], Collections.singletonList(orgProjectService[2]));
        serviceIdentifiers.put(orgProjectService[0], map);
      }
    });
    return serviceIdentifiers;
  }

  private List<ServiceResponseDTO> getServiceDTOS(
      String accountIdentifier, Map<String, Map<String, List<String>>> orgProjectsServicesMapping) {
    return orgProjectsServicesMapping.size() > 0 ? getServices(accountIdentifier, orgProjectsServicesMapping)
                                                 : new ArrayList<>();
  }

  private List<ServiceResponseDTO> getServices(
      String accountIdentifier, Map<String, Map<String, List<String>>> orgProjectsServicesMapping) {
    List<ServiceResponseDTO> serviceResponseDTOS = new ArrayList<>();
    for (var serviceIdentifier : orgProjectsServicesMapping.entrySet()) {
      String org = serviceIdentifier.getKey();
      for (var projectService : serviceIdentifier.getValue().entrySet()) {
        PageResponse<ServiceResponse> services;
        int page = 0;
        do {
          services = getResponse(serviceResourceClient.listServicesForProject(page, PAGE_LIMIT_FOR_ENTITY_FETCH,
              accountIdentifier, org, projectService.getKey(), projectService.getValue(), null));
          if (services != null && isNotEmpty(services.getContent())) {
            serviceResponseDTOS.addAll(
                services.getContent().stream().map(ServiceResponse::getService).collect(Collectors.toList()));
          }
          page++;
        } while (services != null && isNotEmpty(services.getContent()));
      }
    }
    return serviceResponseDTOS;
  }

  private void saveCatalogConnector(String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo) {
    GitIntegrationRequest gitIntegrationRequest = new GitIntegrationRequest();
    gitIntegrationRequest.setType(BaseIntegrationRequest.TypeEnum.GIT);
    gitIntegrationRequest.setConnectorIdentifier(catalogConnectorInfo.getConnector().getIdentifier());
    gitIntegrationServiceImpl.saveOrUpdate(accountIdentifier, gitIntegrationRequest);
    log.info("Saved catalogConnector to DB. Account = {}", accountIdentifier);
  }

  private List<String> writeEntityAsYamlInFile(List<? extends BackstageCatalogEntity> entities, String prefixPath) {
    List<String> files = new ArrayList<>();
    entities.forEach(entity -> {
      String filePath = prefixPath
          + BackstageCatalogEntity.getValue(entity.getMetadata(), MetadataFieldConstants.NAME, String.class)
          + YAML_FILE_EXTENSION;
      writeObjectAsYamlInFile(entity, filePath);
      files.add(filePath);
    });
    return files;
  }

  private List<String> prepareEntitiesTarget(ConnectorProcessor connectorProcessor,
      CatalogConnectorInfo catalogConnectorInfo, List<? extends BackstageCatalogEntity> entities, String prefixPath) {
    List<String> targets = new ArrayList<>();
    entities.forEach(entity
        -> targets.add(connectorProcessor.getLocationTarget(catalogConnectorInfo,
            prefixPath
                + BackstageCatalogEntity.getValue(entity.getMetadata(), MetadataFieldConstants.NAME, String.class)
                + YAML_FILE_EXTENSION)));
    return targets;
  }

  @Override
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

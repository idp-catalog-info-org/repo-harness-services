/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.catalog;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPSERT_ACTION;
import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.idp.catalog.utils.Constants.HARNESS_API_VERSION;
import static io.harness.idp.integrations.utils.Constants.HARNESS_CD_CATALOG_INTEGRATION;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.events.CatalogUpdateEvent;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.mapper.CatalogMapper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.integrations.beans.catalog.HarnessCDIntegrationSyncRequest;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.catalog.HarnessCDIntegrationEntity;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.outbox.api.OutboxService;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.service.remote.ServiceResourceClient;
import io.harness.spec.server.idp.v1.model.EntityCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.GitDetails;
import io.harness.spec.server.idp.v1.model.GitUpdateDetails;
import io.harness.spec.server.idp.v1.model.HarnessCDIntegrationRequest;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public final class HarnessCDIntegrationOpsImpl extends CatalogIntegrationOps<HarnessCDIntegrationEntity,
    HarnessCDIntegrationRequest, HarnessCDIntegrationSyncRequest> {
  @Inject NamespaceService namespaceService;
  @Inject IdpCommonService idpCommonService;
  @Inject IntegrationEntityRepository integrationEntityRepository;
  @Inject @Named("HarnessCDCatalogIntegrationSync") ExecutorService executorService;
  @Inject ServiceResourceClient serviceResourceClient;
  @Inject ScopeInfoClient scopeInfoClient;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject OutboxService outboxService;
  @Inject SetupUsageProducer setupUsageProducer;
  @Inject CatalogService catalogService;
  @Inject IDPGitXHelper idpGitXHelper;
  @Inject @Named("harnessCiCdAnnotationsServiceUrl") String harnessCiCdAnnotationsServiceUrl;
  @Inject @Named("PRIVILEGED") OrganizationClient organizationClient;
  @Inject @Named("PRIVILEGED") ProjectClient projectClient;
  @Inject CatalogServiceHelper catalogServiceHelper;
  @Inject @Named("harnessCodeRepoConfig") HarnessCodeRepoConfig harnessCodeRepoConfig;

  @FunctionalInterface
  interface TriFunction<A, B, C, R> {
    R apply(A a, B b, C c);
  }

  @Override
  HarnessCDIntegrationEntity prepare(
      String accountIdentifier, HarnessCDIntegrationRequest harnessCDIntegrationRequest) {
    return HarnessCDIntegrationEntity.builder()
        .accountIdentifier(accountIdentifier)
        .identifier(HARNESS_CD_CATALOG_INTEGRATION)
        .integration(IntegrationEntity.Integration.CATALOG)
        .parentType(IntegrationEntity.ParentType.HARNESS_CD)
        .subType(null)
        .additionalIndexer(null)
        .enabled(harnessCDIntegrationRequest.isEnabled())
        .scopesToSync(harnessCDIntegrationRequest.getScopes())
        .autoDeletion(true)
        .build();
  }

  @Override
  HarnessCDIntegrationSyncRequest prepareCatalogIntegrationSyncRequest(
      HarnessCDIntegrationEntity harnessCDIntegrationEntity) {
    return HarnessCDIntegrationSyncRequest.builder()
        .accountIdentifier(harnessCDIntegrationEntity.getAccountIdentifier())
        .scope(harnessCDIntegrationEntity.getScopesToSync())
        .build();
  }

  @Override
  CompletableFuture<Void> performSyncInBackground(HarnessCDIntegrationSyncRequest harnessCDIntegrationSyncRequest) {
    return CompletableFuture.runAsync(() -> performSync(harnessCDIntegrationSyncRequest), executorService);
  }

  @Override
  public void performSync(HarnessCDIntegrationSyncRequest harnessCDIntegrationSyncRequest) {
    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));

    if (!preChecks(harnessCDIntegrationSyncRequest)) {
      log.warn("Pre checks failed for harness CD catalog integration sync. HarnessCDIntegrationSyncRequest = {}",
          harnessCDIntegrationSyncRequest);
      return;
    }

    if (!validationChecksForSync(harnessCDIntegrationSyncRequest)) {
      log.warn("Validation checks failed for harness CD catalog integration sync. HarnessCDIntegrationSyncRequest = {}",
          harnessCDIntegrationSyncRequest);
      return;
    }

    if (harnessCDIntegrationSyncRequest.getScope().contains("account.*")) {
      performCompleteSyncInBackground(harnessCDIntegrationSyncRequest);
      return;
    }

    String accountIdentifier = harnessCDIntegrationSyncRequest.getAccountIdentifier();
    String scopes = harnessCDIntegrationSyncRequest.getScope();
    List<ServiceResponseDTO> serviceResponseDTOS = new ArrayList<>();

    if (scopes.contains(",account,") || scopes.contains("account,") || scopes.contains(",account")
        || scopes.equals("account")) {
      serviceResponseDTOS.addAll(getServices(accountIdentifier, null, null)
                                     .stream()
                                     .filter(serviceResponseDTO
                                         -> isEmpty(serviceResponseDTO.getOrgIdentifier())
                                             && isEmpty(serviceResponseDTO.getProjectIdentifier()))
                                     .toList());
    }

    if (scopes.contains("account.org")) {
      List<OrganizationResponse> organizationResponseList =
          NGRestUtils
              .getResponse(organizationClient.listAllOrganizations(accountIdentifier, Collections.emptyList(), null))
              .getContent();
      organizationResponseList.forEach(organizationResponse
          -> serviceResponseDTOS.addAll(
              getServices(accountIdentifier, organizationResponse.getOrganization().getIdentifier(), null)));
    }

    if (scopes.contains("account.org.project")) {
      List<ProjectDTO> projectDTOList = NGRestUtils.getResponse(projectClient.getProjectList(accountIdentifier, null));
      projectDTOList.forEach(projectDTO
          -> serviceResponseDTOS.addAll(
              getServices(accountIdentifier, projectDTO.getOrgIdentifier(), projectDTO.getIdentifier())));
    }

    scopes = Arrays.stream(scopes.split(","))
                 .filter(s -> !Arrays.asList("account.*", "account", "account.org", "account.org.project").contains(s))
                 .collect(Collectors.joining(","));

    if (!isEmpty(scopes)) {
      for (String scope : scopes.split(",")) {
        String[] hierarchyScope = scope.split("\\.");
        String orgIdentifier = hierarchyScope[1];
        String projectIdentifier = scope.endsWith(".*") ? null : hierarchyScope[2];
        serviceResponseDTOS.addAll(getServices(accountIdentifier, orgIdentifier, projectIdentifier));
      }
    }

    Map<String, ScopeInfo> scopeInfoLookup = scopeInfoLookup(accountIdentifier, serviceResponseDTOS);
    String accountBaseUrl = getAccountBaseUrl(accountIdentifier);

    serviceResponseDTOS.stream().distinct().toList().forEach(serviceResponseDTO -> {
      try {
        String scopeInfoLookupKey = scopeInfoLookupKey(
            accountIdentifier, serviceResponseDTO.getOrgIdentifier(), serviceResponseDTO.getProjectIdentifier());
        ScopeInfo scopeInfo = scopeInfoLookup.get(scopeInfoLookupKey);
        if (scopeInfo == null) {
          log.warn("ScopeInfo not found for serviceResponseDTO in performSync = {}", serviceResponseDTO);
          return;
        }
        catalogEntityRepository
            .findByParentUniqueIdAndKindAndIdentifier(
                scopeInfo.getUniqueId(), COMPONENT_KIND, serviceResponseDTO.getIdentifier())
            .ifPresentOrElse(entity
                -> updateHandler(serviceResponseDTO, entity, accountBaseUrl),
                () -> createHandlerForEntityNotPresent(serviceResponseDTO, accountBaseUrl));
      } catch (Exception ex) {
        log.warn("performSync failed for serviceResponseDTO = {} Error = {}", serviceResponseDTO, ex.getMessage(), ex);
      }
    });
  }

  @Override
  CompletableFuture<Void> performCompleteSyncInBackground(
      HarnessCDIntegrationSyncRequest harnessCDIntegrationSyncRequest) {
    return CompletableFuture.runAsync(() -> performCompleteSync(harnessCDIntegrationSyncRequest), executorService);
  }

  @Override
  public void performCompleteSync(HarnessCDIntegrationSyncRequest harnessCDIntegrationSyncRequest) {
    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));

    if (!preChecks(harnessCDIntegrationSyncRequest)) {
      log.warn(
          "Pre checks failed for harness CD catalog integration complete sync. HarnessCDIntegrationSyncRequest = {}",
          harnessCDIntegrationSyncRequest);
      return;
    }

    if (!validationChecksForCompleteSync(harnessCDIntegrationSyncRequest)) {
      log.warn("Validation checks failed for harness CD catalog integration complete sync. "
              + "HarnessCDIntegrationSyncRequest = {}",
          harnessCDIntegrationSyncRequest);
      return;
    }

    String accountIdentifier = harnessCDIntegrationSyncRequest.getAccountIdentifier();
    List<ServiceResponseDTO> serviceResponseDTOS = getServices(accountIdentifier, null, null);
    Map<String, ScopeInfo> scopeInfoLookup = scopeInfoLookup(accountIdentifier, serviceResponseDTOS);
    String accountBaseUrl = getAccountBaseUrl(accountIdentifier);
    serviceResponseDTOS.forEach(serviceResponseDTO -> {
      try {
        String scopeInfoLookupKey = scopeInfoLookupKey(
            accountIdentifier, serviceResponseDTO.getOrgIdentifier(), serviceResponseDTO.getProjectIdentifier());
        ScopeInfo scopeInfo = scopeInfoLookup.get(scopeInfoLookupKey);
        if (scopeInfo == null) {
          log.warn("ScopeInfo not found for serviceResponseDTO in performCompleteSync = {}", serviceResponseDTO);
          return;
        }
        catalogEntityRepository
            .findByParentUniqueIdAndKindAndIdentifier(
                scopeInfo.getUniqueId(), COMPONENT_KIND, serviceResponseDTO.getIdentifier())
            .ifPresentOrElse(entity
                -> updateHandler(serviceResponseDTO, entity, accountBaseUrl),
                () -> createHandlerForEntityNotPresent(serviceResponseDTO, accountBaseUrl));
      } catch (Exception ex) {
        log.warn("performCompleteSync failed for serviceResponseDTO = {} Error = {}", serviceResponseDTO,
            ex.getMessage(), ex);
      }
    });
  }

  @Override
  public void performIncrementalSync(HarnessCDIntegrationSyncRequest harnessCDIntegrationSyncRequest) {
    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));

    if (!preChecks(harnessCDIntegrationSyncRequest)) {
      log.warn(
          "Pre checks failed for harness CD catalog integration incremental sync. HarnessCDIntegrationSyncRequest = {}",
          harnessCDIntegrationSyncRequest);
      return;
    }

    String accountIdentifier = harnessCDIntegrationSyncRequest.getAccountIdentifier();
    String orgIdentifier = harnessCDIntegrationSyncRequest.getOrgIdentifier();
    orgIdentifier = isEmpty(orgIdentifier) ? null : orgIdentifier;
    String projectIdentifier = harnessCDIntegrationSyncRequest.getProjectIdentifier();
    projectIdentifier = isEmpty(projectIdentifier) ? null : projectIdentifier;
    String identifier = harnessCDIntegrationSyncRequest.getIdentifier();
    String scope = harnessCDIntegrationSyncRequest.getScope();
    String scopeUniqueId = harnessCDIntegrationSyncRequest.getScopeUniqueId();
    String action = harnessCDIntegrationSyncRequest.getAction();

    ScopeInfo scopeInfo;
    if (!isEmpty(scope) && !isEmpty(scopeUniqueId)) {
      Map<String, Optional<ScopeInfo>> scopeInfoPerUniqueId =
          getResponse(scopeInfoClient.getScopeInfos(accountIdentifier, Collections.singleton(scopeUniqueId)));
      Optional<ScopeInfo> optScopeInfo = scopeInfoPerUniqueId.getOrDefault(scopeUniqueId, Optional.empty());
      if (optScopeInfo.isEmpty()) {
        return;
      }
      scopeInfo = optScopeInfo.get();
      orgIdentifier = scopeInfo.getOrgIdentifier();
      projectIdentifier = scopeInfo.getProjectIdentifier();
      harnessCDIntegrationSyncRequest.setOrgIdentifier(orgIdentifier);
      harnessCDIntegrationSyncRequest.setProjectIdentifier(projectIdentifier);
    } else {
      scopeInfo = getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
    }

    if (!validationChecksForIncrementalSync(harnessCDIntegrationSyncRequest)) {
      log.warn("Validation checks failed for harness CD catalog integration incremental sync. "
              + "HarnessCDIntegrationSyncRequest = {}",
          harnessCDIntegrationSyncRequest);
      return;
    }

    Optional<CatalogEntity> optionalCatalogEntity = catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
        scopeInfo.getUniqueId(), COMPONENT_KIND, identifier);
    CatalogEntity catalogEntity = optionalCatalogEntity.orElse(null);
    Map<String, Object> spec = catalogEntity != null ? catalogEntity.getSpec() : new HashMap<>();
    Map<String, Object> harnessService =
        !isEmpty(spec) ? (Map<String, Object>) spec.get("harnessService") : new HashMap<>();

    if (action.equals(DELETE_ACTION) && catalogEntity != null && !isEmpty(harnessService)
        && Objects.equals(harnessService.get("orgIdentifier"), orgIdentifier)
        && Objects.equals(harnessService.get("projectIdentifier"), projectIdentifier)
        && Objects.equals(harnessService.get("identifier"), identifier)) {
      catalogEntity.getSpec().remove("harnessService");
      ((Map<String, Object>) catalogEntity.getMetadata().get("annotations")).remove("harness.io/services");
      catalogEntity.setYaml(CatalogMapper.presentationYaml(catalogEntity));
      catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity));
      catalogEntityRepository.save(catalogEntity);
      setupUsageProducer.deleteCdServiceSetupUsage(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      return;
    }

    ServiceResponseDTO serviceResponseDTO = getService(accountIdentifier, orgIdentifier, projectIdentifier, identifier);

    if ((action.equals(CREATE_ACTION) || action.equals(UPDATE_ACTION) || action.equals(UPSERT_ACTION))
        && serviceResponseDTO == null) {
      return;
    }

    String accountBaseUrl = getAccountBaseUrl(accountIdentifier);

    if ((action.equals(CREATE_ACTION) || action.equals(UPDATE_ACTION) || action.equals(UPSERT_ACTION))
        && catalogEntity == null) {
      createHandlerForEntityNotPresent(serviceResponseDTO, accountBaseUrl);
      return;
    }

    if (action.equals(CREATE_ACTION)) {
      createHandlerForEntityPresent(serviceResponseDTO, catalogEntity, accountBaseUrl);
      return;
    }

    if (action.equals(UPDATE_ACTION) || action.equals(UPSERT_ACTION)) {
      updateHandler(serviceResponseDTO, catalogEntity, accountBaseUrl);
    }
  }

  @Override
  Object transform(Object rawEntity) {
    ServiceResponseDTO serviceResponseDTO = (ServiceResponseDTO) rawEntity;
    String accountBaseUrl = getAccountBaseUrl(serviceResponseDTO.getAccountId());
    return transform(serviceResponseDTO, accountBaseUrl);
  }

  private Object transform(ServiceResponseDTO serviceResponseDTO, String accountBaseUrl) {
    InlineCatalogEntity inlineCatalogEntity = InlineCatalogEntity.builder().build();
    inlineCatalogEntity.setAccountIdentifier(serviceResponseDTO.getAccountId());
    inlineCatalogEntity.setOrgIdentifier(serviceResponseDTO.getOrgIdentifier());
    inlineCatalogEntity.setProjectIdentifier(serviceResponseDTO.getProjectIdentifier());
    inlineCatalogEntity.setIdentifier(serviceResponseDTO.getIdentifier());
    inlineCatalogEntity.setReferenceType(ReferenceType.INLINE);
    inlineCatalogEntity.setApiVersion(HARNESS_API_VERSION);
    inlineCatalogEntity.setKind(COMPONENT_KIND);
    inlineCatalogEntity.setType("service");
    inlineCatalogEntity.setName(serviceResponseDTO.getName());
    inlineCatalogEntity.setDescription(serviceResponseDTO.getDescription());
    inlineCatalogEntity.setOwner("Unknown");
    inlineCatalogEntity.setTags(
        Stream.concat(serviceResponseDTO.getTags().keySet().stream(), serviceResponseDTO.getTags().values().stream())
            .filter(tag -> !isEmpty(tag))
            .collect(Collectors.toList()));
    inlineCatalogEntity.setSpec(new HashMap<>() {
      {
        put("lifecycle", "Unknown");
        put("harnessService", new HashMap<String, Object>() {
          {
            if (!EmptyPredicate.isEmpty(serviceResponseDTO.getOrgIdentifier())) {
              put("orgIdentifier", serviceResponseDTO.getOrgIdentifier());
            }
            if (!EmptyPredicate.isEmpty(serviceResponseDTO.getProjectIdentifier())) {
              put("projectIdentifier", serviceResponseDTO.getProjectIdentifier());
            }
            put("identifier", serviceResponseDTO.getIdentifier());
          }
        });
      }
    });
    inlineCatalogEntity.setMetadata(new HashMap<>() {
      {
        put("annotations", new HashMap<String, Object>() {
          { put("harness.io/services", getServiceUrlForHarnessCiCdAnnotation(serviceResponseDTO, accountBaseUrl)); }
        });
      }
    });
    return CatalogMapper.presentationYaml(inlineCatalogEntity);
  }

  @Override
  Object transform(Object rawEntity, Object existingTransformedEntity) {
    ServiceResponseDTO serviceResponseDTO = (ServiceResponseDTO) rawEntity;
    String accountBaseUrl = getAccountBaseUrl(serviceResponseDTO.getAccountId());
    return transform(serviceResponseDTO, existingTransformedEntity, accountBaseUrl);
  }

  private Object transform(
      ServiceResponseDTO serviceResponseDTO, Object existingTransformedEntity, String accountBaseUrl) {
    CatalogEntity catalogEntity = (CatalogEntity) existingTransformedEntity;
    catalogEntity.setName(serviceResponseDTO.getName());
    catalogEntity.setDescription(serviceResponseDTO.getDescription());
    catalogEntity.setTags(
        Stream.concat(serviceResponseDTO.getTags().keySet().stream(), serviceResponseDTO.getTags().values().stream())
            .filter(tag -> !isEmpty(tag))
            .collect(Collectors.toList()));
    catalogEntity.getSpec().put("harnessService", new HashMap<String, Object>() {
      {
        if (!EmptyPredicate.isEmpty(serviceResponseDTO.getOrgIdentifier())) {
          put("orgIdentifier", serviceResponseDTO.getOrgIdentifier());
        }
        if (!EmptyPredicate.isEmpty(serviceResponseDTO.getProjectIdentifier())) {
          put("projectIdentifier", serviceResponseDTO.getProjectIdentifier());
        }
        put("identifier", serviceResponseDTO.getIdentifier());
      }
    });
    ((Map<String, Object>) catalogEntity.getMetadata().computeIfAbsent("annotations", k -> new HashMap<>()))
        .put("harness.io/services", getServiceUrlForHarnessCiCdAnnotation(serviceResponseDTO, accountBaseUrl));
    return CatalogMapper.presentationYaml(catalogEntity);
  }

  boolean preChecks(HarnessCDIntegrationSyncRequest harnessCDIntegrationSyncRequest) {
    String accountIdentifier = harnessCDIntegrationSyncRequest.getAccountIdentifier();
    if (idpCommonService.idpIntegrationsEnabled(accountIdentifier)) {
      return false;
    }
    if (!namespaceService.getAccountIdpStatus(accountIdentifier)) {
      return false;
    }
    if (!idpCommonService.idpV2Enabled(accountIdentifier)) {
      return false;
    }
    return idpCommonService.idpCatalogCDAutoDiscoveryEnabled(accountIdentifier);
  }

  private boolean validationChecksForSync(HarnessCDIntegrationSyncRequest harnessCDIntegrationSyncRequest) {
    String accountIdentifier = harnessCDIntegrationSyncRequest.getAccountIdentifier();

    IntegrationEntity integrationEntity = getByAccountAndIdentifier(accountIdentifier, HARNESS_CD_CATALOG_INTEGRATION);
    if (integrationEntity == null) {
      return false;
    }

    HarnessCDIntegrationEntity harnessCDIntegrationEntity = (HarnessCDIntegrationEntity) integrationEntity;
    return harnessCDIntegrationEntity.isEnabled();
  }

  private IntegrationEntity getByAccountAndIdentifier(String accountIdentifier, String identifier) {
    Optional<IntegrationEntity> optionalCatalogIntegrationEntity =
        integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
            accountIdentifier, identifier, IntegrationEntity.Integration.CATALOG);
    return optionalCatalogIntegrationEntity.orElse(null);
  }

  private boolean validationChecksForCompleteSync(HarnessCDIntegrationSyncRequest harnessCDIntegrationSyncRequest) {
    String accountIdentifier = harnessCDIntegrationSyncRequest.getAccountIdentifier();

    IntegrationEntity integrationEntity = getByAccountAndIdentifier(accountIdentifier, HARNESS_CD_CATALOG_INTEGRATION);
    if (integrationEntity == null) {
      return false;
    }

    HarnessCDIntegrationEntity harnessCDIntegrationEntity = (HarnessCDIntegrationEntity) integrationEntity;
    if (!harnessCDIntegrationEntity.isEnabled()) {
      return false;
    }

    return Arrays.stream(harnessCDIntegrationEntity.getScopesToSync().split(","))
        .anyMatch(s -> s.trim().equals("account.*"));
  }

  private List<ServiceResponseDTO> getServices(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<ServiceResponseDTO> serviceResponseDTOS = new ArrayList<>();
    PageResponse<ServiceResponse> services;
    int page = 0;
    do {
      services = getResponse(serviceResourceClient.getAllServicesList(
          accountIdentifier, orgIdentifier, projectIdentifier, null, page, 1000, null));
      if (services != null && isNotEmpty(services.getContent())) {
        serviceResponseDTOS.addAll(services.getContent().stream().map(ServiceResponse::getService).toList());
      }
      page++;
    } while (services != null && isNotEmpty(services.getContent()));
    return serviceResponseDTOS;
  }

  private boolean validationChecksForIncrementalSync(HarnessCDIntegrationSyncRequest harnessCDIntegrationSyncRequest) {
    String accountIdentifier = harnessCDIntegrationSyncRequest.getAccountIdentifier();
    String orgIdentifier = harnessCDIntegrationSyncRequest.getOrgIdentifier();
    String projectIdentifier = harnessCDIntegrationSyncRequest.getProjectIdentifier();

    IntegrationEntity integrationEntity = getByAccountAndIdentifier(accountIdentifier, HARNESS_CD_CATALOG_INTEGRATION);
    if (integrationEntity == null) {
      return false;
    }

    HarnessCDIntegrationEntity harnessCDIntegrationEntity = (HarnessCDIntegrationEntity) integrationEntity;
    if (!harnessCDIntegrationEntity.isEnabled()) {
      return false;
    }

    if (harnessCDIntegrationSyncRequest.getAction().equals(DELETE_ACTION)
        && !harnessCDIntegrationEntity.isAutoDeletion()) {
      return false;
    }

    return isScopeMatchingAgainstConfiguredScopes.apply(
        orgIdentifier, projectIdentifier, harnessCDIntegrationEntity.getScopesToSync());
  }

  private final TriFunction<String, String, String, Boolean> isScopeMatchingAgainstConfiguredScopes =
      (orgIdentifier, projectIdentifier, configuredScopes) -> {
    String fullyQualifiedScope = fullyQualifiedScope(orgIdentifier, projectIdentifier);

    return Arrays.stream(configuredScopes.split("\\s*,\\s*"))
        .map(String::trim)
        .anyMatch(configuredScope -> scopeMatches(fullyQualifiedScope, configuredScope));
  };

  private String fullyQualifiedScope(String orgIdentifier, String projectIdentifier) {
    if (!isEmpty(orgIdentifier) && !isEmpty(projectIdentifier)) {
      return "account"
          + "." + orgIdentifier + "." + projectIdentifier;
    }
    if (!isEmpty(orgIdentifier)) {
      return "account"
          + "." + orgIdentifier;
    }
    return "account";
  }

  private boolean scopeMatches(String fullyQualifiedScope, String configuredScope) {
    return IntStream.range(0, (int) Arrays.stream(configuredScope.split("\\.")).takeWhile(s -> !"*".equals(s)).count())
               .allMatch(i
                   -> i < fullyQualifiedScope.split("\\.").length
                       && ("org".equals(configuredScope.split("\\.")[i])
                           || "project".equals(configuredScope.split("\\.")[i])
                           || configuredScope.split("\\.")[i].equals(fullyQualifiedScope.split("\\.")[i])))
        && (configuredScope.contains("*")
            || fullyQualifiedScope.split("\\.").length == configuredScope.split("\\.").length);
  }

  private ServiceResponseDTO getService(
      String accountIdentifier, String organizationIdentifier, String projectIdentifier, String serviceIdentifier) {
    try {
      ServiceResponse service = getResponse(serviceResourceClient.getService(
          serviceIdentifier, accountIdentifier, organizationIdentifier, projectIdentifier));
      return service.getService();
    } catch (Exception ex) {
      log.warn("Error in get service for accountIdentifier = {} organizationIdentifier = {} projectIdentifier = {} "
              + "serviceIdentifier = {}",
          accountIdentifier, organizationIdentifier, projectIdentifier, serviceIdentifier);
      return null;
    }
  }

  private String getServiceUrlForHarnessCiCdAnnotation(ServiceResponseDTO serviceResponseDTO, String accountBaseUrl) {
    String url = serviceResponseDTO.getIdentifier() + ": " + accountBaseUrl
        + harnessCiCdAnnotationsServiceUrl.replace(ACCOUNT_KEY, serviceResponseDTO.getAccountId())
              .replace(
                  ORG_KEY, !isEmpty(serviceResponseDTO.getOrgIdentifier()) ? serviceResponseDTO.getOrgIdentifier() : "")
              .replace(PROJECT_KEY,
                  !isEmpty(serviceResponseDTO.getProjectIdentifier()) ? serviceResponseDTO.getProjectIdentifier() : "")
              .replace(SERVICE_IDENTIFIER_KEY, serviceResponseDTO.getIdentifier())
        + "\n";
    url = url.replaceAll("/orgs//", "/");
    url = url.replaceAll("/projects//", "/");
    return url;
  }

  private void createHandlerForEntityNotPresent(ServiceResponseDTO serviceResponseDTO, String accountBaseUrl) {
    String accountIdentifier = serviceResponseDTO.getAccountId();
    String orgIdentifier = serviceResponseDTO.getOrgIdentifier();
    String projectIdentifier = serviceResponseDTO.getProjectIdentifier();
    String identifier = serviceResponseDTO.getIdentifier();

    Object transformedEntity = transform(serviceResponseDTO, accountBaseUrl);
    catalogService.createEntity(accountIdentifier, orgIdentifier, projectIdentifier, false, false,
        new EntityCreateRequest().yaml((String) transformedEntity));
    setupUsageProducer.publishCdServiceSetupUsage(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, identifier);
  }

  private void createHandlerForEntityPresent(
      ServiceResponseDTO serviceResponseDTO, CatalogEntity catalogEntity, String accountBaseUrl) {
    String accountIdentifier = serviceResponseDTO.getAccountId();
    String orgIdentifier = serviceResponseDTO.getOrgIdentifier();
    String projectIdentifier = serviceResponseDTO.getProjectIdentifier();
    String identifier = serviceResponseDTO.getIdentifier();
    Map<String, Object> spec = catalogEntity.getSpec();
    Map<String, Object> harnessService =
        !isEmpty(spec) ? (Map<String, Object>) spec.get("harnessService") : new HashMap<>();

    String existingCatalogEntityYaml = catalogEntity.getYaml();

    harnessService.put("orgIdentifier", serviceResponseDTO.getOrgIdentifier());
    harnessService.put("projectIdentifier", serviceResponseDTO.getProjectIdentifier());
    harnessService.put("identifier", serviceResponseDTO.getIdentifier());
    spec.put("harnessService", harnessService);
    catalogEntity.setSpec(spec);
    ((Map<String, Object>) catalogEntity.getMetadata().computeIfAbsent("annotations", k -> new HashMap<>()))
        .put("harness.io/services", getServiceUrlForHarnessCiCdAnnotation(serviceResponseDTO, accountBaseUrl));
    catalogEntity.setYaml(CatalogMapper.presentationYaml(catalogEntity));
    catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity));
    catalogEntityRepository.save(catalogEntity);
    outboxService.save(new CatalogUpdateEvent(
        getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier)),
        catalogEntity.getYaml(), existingCatalogEntityYaml, catalogEntity.getKind(), catalogEntity.getIdentifier()));
    setupUsageProducer.publishCdServiceSetupUsage(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, identifier);
  }

  private void updateHandler(
      ServiceResponseDTO serviceResponseDTO, CatalogEntity catalogEntity, String accountBaseUrl) {
    String accountIdentifier = serviceResponseDTO.getAccountId();
    String orgIdentifier = serviceResponseDTO.getOrgIdentifier();
    String projectIdentifier = serviceResponseDTO.getProjectIdentifier();
    String identifier = serviceResponseDTO.getIdentifier();

    EntityResponse entityResponse = catalogService.getEntity(accountIdentifier, orgIdentifier, projectIdentifier,
        CatalogUtils.entityRef(catalogEntity), false, false, true, false);
    Object transformedEntity = transform(serviceResponseDTO, catalogEntity, accountBaseUrl);
    GitDetails gitDetails = entityResponse.getGitDetails();
    GitUpdateDetails gitUpdateDetails = new GitUpdateDetails();
    if (gitDetails != null) {
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
      GitAwareContextHelper.populateGitDetails(idpGitXHelper.populateGitUpdateDetails(gitUpdateDetails));
    }
    catalogService.updateEntity(accountIdentifier, orgIdentifier, projectIdentifier, entityResponse.getEntityRef(),
        new EntityUpdateRequest().yaml((String) transformedEntity).gitDetails(gitUpdateDetails), false, true, false,
        false);
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
    setupUsageProducer.publishCdServiceSetupUsage(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, identifier);
  }

  private Map<String, ScopeInfo> scopeInfoLookup(
      String accountIdentifier, List<ServiceResponseDTO> serviceResponseDTOS) {
    Set<String> orgIdentifiers = serviceResponseDTOS.stream()
                                     .filter(s -> !isEmpty(s.getOrgIdentifier()) && isEmpty(s.getProjectIdentifier()))
                                     .map(ServiceResponseDTO::getOrgIdentifier)
                                     .collect(Collectors.toSet());
    Map<String, Set<String>> projectIdentifiersByOrgIdentifier =
        serviceResponseDTOS.stream()
            .filter(s -> !isEmpty(s.getOrgIdentifier()) && !isEmpty(s.getProjectIdentifier()))
            .collect(Collectors.groupingBy(ServiceResponseDTO::getOrgIdentifier,
                Collectors.mapping(ServiceResponseDTO::getProjectIdentifier, Collectors.toSet())));
    List<ScopeInfo> scopeInfoList = new ArrayList<>();
    scopeInfoList.add(getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, null, null)));
    if (!isEmpty(orgIdentifiers)) {
      scopeInfoList.addAll(getResponse(scopeInfoClient.getScopeInfoList(accountIdentifier, orgIdentifiers)));
    }
    if (!isEmpty(projectIdentifiersByOrgIdentifier)) {
      projectIdentifiersByOrgIdentifier.forEach(
          (k, v) -> scopeInfoList.addAll(getResponse(scopeInfoClient.getScopeInfoList(accountIdentifier, k, v))));
    }
    return scopeInfoList.stream().collect(Collectors.toMap(s
        -> scopeInfoLookupKey(accountIdentifier, s.getOrgIdentifier(), s.getProjectIdentifier()),
        s -> s, (a, b) -> a));
  }

  private String scopeInfoLookupKey(String accountId, String orgIdentifier, String projectIdentifier) {
    if (isEmpty(orgIdentifier) && isEmpty(projectIdentifier)) {
      return "account:" + accountId;
    } else if (!isEmpty(orgIdentifier) && isEmpty(projectIdentifier)) {
      return "account:" + accountId + ".org:" + orgIdentifier;
    } else {
      return "account:" + accountId + ".org:" + orgIdentifier + ".project:" + projectIdentifier;
    }
  }

  public String getAccountBaseUrl(String accountIdentifier) {
    AccountDTO accountDTO = idpCommonService.getAccountDTO(accountIdentifier);
    String baseUrl = harnessCodeRepoConfig.getBaseUrl();
    if (isNotEmpty(accountDTO.getSubdomainURL())) {
      baseUrl = accountDTO.getSubdomainURL();
      if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
        baseUrl = "https://" + baseUrl;
      }
    }
    return baseUrl;
  }
}

/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.catalog;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.idp.common.YamlUtils.applyDataMerge;
import static io.harness.idp.common.YamlUtils.deepMerge;
import static io.harness.idp.common.YamlUtils.mergeIgnoringEmpty;
import static io.harness.idp.common.YamlUtils.putByPath;
import static io.harness.idp.common.YamlUtils.removeByPath;
import static io.harness.idp.common.YamlUtils.removeFields;
import static io.harness.remote.client.NGRestUtils.executeGeneralRequestWithRetry;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.clients.integrationmanager.EntityMappedEntityResponse;
import io.harness.clients.integrationmanager.EntityMappedEntityResponseObject;
import io.harness.clients.integrationmanager.EntitySubscribeEntitiesResponse;
import io.harness.clients.integrationmanager.IntegrationManagerClientHelper;
import io.harness.clients.integrationmanager.OpenapiGetMappedEntitiesRequest;
import io.harness.clients.integrationmanager.OpenapiSubscribeEntitiesRequest;
import io.harness.clients.integrationmanager.OpenapiUpdateIntegrationConfigRequest;
import io.harness.clients.integrationmanager.TypesEntityMapping;
import io.harness.clients.integrationmanager.TypesIntegrationConfig;
import io.harness.eventsframework.schemas.idp.UserPrincipal;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.beans.Kind;
import io.harness.idp.catalog.beans.KindType;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.config.CatalogContentConfig;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.catalog.events.CatalogDecoratorUpdateEvent;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.helpers.IDPToHarnessHelper;
import io.harness.idp.catalog.processor.RelationsProcessor;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.repositories.KindEntityRepository;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.GcpStorageUtil;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.common.JacksonUtils;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.common.encryption.IdpContentEncryptionService;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.integrations.beans.catalog.CatalogIntegrationSyncRequest;
import io.harness.idp.integrations.beans.common.DiscoverEntitiesDTO;
import io.harness.idp.integrations.beans.common.ImportedEntitiesDTO;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.catalog.CatalogIntegrationEntity;
import io.harness.idp.integrations.events.CatalogIntegrationCreateEvent;
import io.harness.idp.integrations.events.CatalogIntegrationDeleteEvent;
import io.harness.idp.integrations.events.CatalogIntegrationUpdateEvent;
import io.harness.idp.integrations.mapper.catalog.CatalogIntegrationMapper;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.idp.integrations.service.common.CommonIntegrationService;
import io.harness.outbox.api.OutboxService;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.CatalogIntegrationRequest;
import io.harness.spec.server.idp.v1.model.CatalogIntegrationResponse;
import io.harness.spec.server.idp.v1.model.DiscoverEntitiesResponse;
import io.harness.spec.server.idp.v1.model.DiscoverEntitiesResponseActionDestination;
import io.harness.spec.server.idp.v1.model.DiscoverEntitiesResponseActionDestinationMerge;
import io.harness.spec.server.idp.v1.model.DiscoverEntitiesResponseActionDestinationRegister;
import io.harness.spec.server.idp.v1.model.EntityCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityMoveOperationType;
import io.harness.spec.server.idp.v1.model.EntityMoveRequest;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.GitCreateDetails;
import io.harness.spec.server.idp.v1.model.GitDetails;
import io.harness.spec.server.idp.v1.model.GitMoveDetails;
import io.harness.spec.server.idp.v1.model.GitUpdateDetails;
import io.harness.spec.server.idp.v1.model.HarnessCDIntegrationRequest;
import io.harness.spec.server.idp.v1.model.ImportedEntityResponse;
import io.harness.spec.server.idp.v1.model.ImportedEntityResponseRawEntityDetails;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequest;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequestIntegrationEntities;
import io.harness.spec.server.idp.v1.model.UnlinkIntegrationEntitiesResponse;
import io.harness.springdata.TransactionHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class CatalogIntegrationServiceImpl
    implements CommonIntegrationService<CatalogIntegrationRequest, CatalogIntegrationResponse> {
  public LoadingCache<String, String> entityTemplateCache =
      CacheBuilder.newBuilder().maximumSize(12).build(new CacheLoader<>() {
        @NotNull
        @Override
        public String load(@NotNull String kindVersion) {
          return CommonUtils.readFileFromClassPath("catalog/entity-schema/" + kindVersion + ".yaml");
        }
      });

  public LoadingCache<String, String> integrationEntityLinkageConfigCache =
      CacheBuilder.newBuilder().maximumSize(1).build(new CacheLoader<>() {
        @NotNull
        @Override
        public String load(@NotNull String ignored) {
          return CommonUtils.readFileFromClassPath("catalog/entity-schema/integration_entity_linkage_config.json");
        }
      });

  public LoadingCache<String, String> integrationEntityAdditionalLinkageConfigPlaceholderCache =
      CacheBuilder.newBuilder().maximumSize(1).build(new CacheLoader<>() {
        @NotNull
        @Override
        public String load(@NotNull String ignored) {
          return CommonUtils.readFileFromClassPath(
              "catalog/entity-schema/integration_entity_additional_linkage_config_placeholder.json");
        }
      });

  public LoadingCache<String, String> integrationEntityAdditionalLinkageConfigCache =
      CacheBuilder.newBuilder().maximumSize(1).build(new CacheLoader<>() {
        @NotNull
        @Override
        public String load(@NotNull String ignored) {
          return CommonUtils.readFileFromClassPath(
              "catalog/entity-schema/integration_entity_additional_linkage_config.json");
        }
      });

  @lombok.Value
  private static class SaveEntityResult {
    CatalogEntity processedEntity;
    String linkageIdentifier;
  }

  @Inject HarnessCDIntegrationOpsImpl harnessCDIntegrationOps;
  @Inject TransactionHelper transactionHelper;
  @Inject IntegrationEntityRepository integrationEntityRepository;
  @Inject OutboxService outboxService;
  @Inject IntegrationManagerClientHelper integrationManagerClientHelper;
  @Inject CatalogService catalogService;
  @Inject IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;
  @Inject IdpCommonService idpCommonService;
  @Inject CatalogServiceHelper catalogServiceHelper;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject SetupUsageProducer setupUsageProducer;
  @Inject IDPGitXHelper idpGitXHelper;
  @Inject RelationsProcessor relationsProcessor;
  @Inject MongoTemplate mongoTemplate;
  @Inject GcpStorageUtil gcpStorageUtil;
  @Inject CatalogContentConfig catalogContentConfig;
  @Inject IdpContentEncryptionService idpContentEncryptionService;
  @Inject KindEntityRepository kindEntityRepository;
  @Inject IDPToHarnessHelper idpToHarnessHelper;
  private static final String MAPPED_ENTITY_RESPONSE_KIND_KEY = "kind";
  private static final String MAPPED_ENTITY_RESPONSE_TYPE_KEY = "type";
  private static final String METADATA_TAGS_KEY = "tags";

  private static final String CATALOG_INFO_DATA_CONTENT = "content";
  private static final String CATALOG_INFO_DATA_REPO = "repo";
  private static final String CATALOG_INFO_DATA_BRANCH = "branch";
  private static final String CATALOG_INFO_CONFIG_GIT_SYNC_ENABLED = "git_sync_enabled";
  private static final String CATALOG_INFO_CONFIG_GIT_SYNC_CONNECTOR_REF = "git_sync_connector_ref";
  private static final String CATALOG_INFO_CONFIG_SYNC_TO_SOURCE_REPO = "sync_to_source_repo";
  private static final String CATALOG_INFO_CONFIG_SYNC_REPO = "sync_repo";
  private static final String CATALOG_INFO_CONFIG_SYNC_BRANCH = "sync_branch";
  private static final String CATALOG_INFO_CONFIG_SYNC_BASE_PATH = "sync_base_path";
  private static final String CATALOG_INFO_DEFAULT_SYNC_BRANCH = "main";
  private static final String CATALOG_INFO_DEFAULT_SYNC_BASE_PATH = ".harness/idp";
  private static final String CATALOG_INFO_GIT_SYNC_COMMIT_MESSAGE = "chore: sync catalog-info from IDP";

  @Override
  public CatalogIntegrationResponse save(
      String accountIdentifier, CatalogIntegrationRequest request, boolean dryRun, boolean writeValidation) {
    try {
      CatalogIntegrationOps<CatalogIntegrationEntity, CatalogIntegrationRequest,
          CatalogIntegrationSyncRequest> catalogIntegrationOps =
          (CatalogIntegrationOps<CatalogIntegrationEntity, CatalogIntegrationRequest, CatalogIntegrationSyncRequest>)
              getServiceForCatalogIntegration(request.getCatalogIntegrationType());
      CatalogIntegrationEntity catalogIntegrationEntity = catalogIntegrationOps.prepare(accountIdentifier,
          getCatalogIntegrationRequest(request.getCatalogIntegrationType(), request.getCatalogIntegrationRequest()));
      transactionHelper.performTransaction(() -> {
        integrationEntityRepository.save(catalogIntegrationEntity);
        outboxService.save(new CatalogIntegrationCreateEvent(accountIdentifier, catalogIntegrationEntity));
        return null;
      });
      performSyncInBackground(catalogIntegrationOps, catalogIntegrationEntity);
      return CatalogIntegrationMapper.toResponse(catalogIntegrationEntity);
    } catch (Exception ex) {
      log.error("Error in catalog integration save for accountIdentifier = {} request = {} Exception = {}",
          accountIdentifier, request, ex.getMessage(), ex);
      throw new UnexpectedException(ex.getMessage());
    }
  }

  @Override
  public CatalogIntegrationResponse update(
      String accountIdentifier, String identifier, CatalogIntegrationRequest request, boolean dryRun) {
    try {
      IntegrationEntity existingCatalogIntegrationEntity = getByAccountAndIdentifier(accountIdentifier, identifier);
      CatalogIntegrationOps<CatalogIntegrationEntity, CatalogIntegrationRequest,
          CatalogIntegrationSyncRequest> catalogIntegrationOps =
          (CatalogIntegrationOps<CatalogIntegrationEntity, CatalogIntegrationRequest, CatalogIntegrationSyncRequest>)
              getServiceForCatalogIntegration(request.getCatalogIntegrationType());
      CatalogIntegrationEntity catalogIntegrationEntity = catalogIntegrationOps.prepare(accountIdentifier,
          getCatalogIntegrationRequest(request.getCatalogIntegrationType(), request.getCatalogIntegrationRequest()));
      catalogIntegrationEntity.setId(existingCatalogIntegrationEntity.getId());
      catalogIntegrationEntity.setCreatedAt(existingCatalogIntegrationEntity.getCreatedAt());
      catalogIntegrationEntity.setCreatedBy(existingCatalogIntegrationEntity.getCreatedBy());
      transactionHelper.performTransaction(() -> {
        integrationEntityRepository.save(catalogIntegrationEntity);
        outboxService.save(new CatalogIntegrationUpdateEvent(
            accountIdentifier, (CatalogIntegrationEntity) existingCatalogIntegrationEntity, catalogIntegrationEntity));
        return null;
      });
      performSyncInBackground(catalogIntegrationOps, catalogIntegrationEntity);
      return CatalogIntegrationMapper.toResponse(catalogIntegrationEntity);
    } catch (Exception ex) {
      log.error(
          "Error in catalog integration update for accountIdentifier = {} identifier = {} request = {} Exception = {}",
          accountIdentifier, identifier, request, ex.getMessage(), ex);
      throw new UnexpectedException(ex.getMessage());
    }
  }

  @Override
  public CatalogIntegrationResponse saveOrUpdate(String accountIdentifier, CatalogIntegrationRequest request) {
    throw new UnsupportedOperationException("Catalog integration saveOrUpdate not supported yet");
  }

  @Override
  public List<CatalogIntegrationResponse> get(String accountIdentifier, Pageable pageRequest, String searchTerm) {
    Criteria criteria = buildGetCriteria(accountIdentifier, searchTerm);
    Page<IntegrationEntity> entities = integrationEntityRepository.findAll(criteria, pageRequest);
    return CatalogIntegrationMapper.toResponse(entities.getContent());
  }

  @Override
  public CatalogIntegrationResponse get(String accountIdentifier, String identifier) {
    return CatalogIntegrationMapper.toResponse(getByAccountAndIdentifier(accountIdentifier, identifier));
  }

  @Override
  public void delete(String accountIdentifier, String identifier, boolean forceDelete) {
    IntegrationEntity existingCatalogIntegrationEntity = getByAccountAndIdentifier(accountIdentifier, identifier);
    transactionHelper.performTransaction(() -> {
      integrationEntityRepository.delete(existingCatalogIntegrationEntity);
      outboxService.save(new CatalogIntegrationDeleteEvent(
          accountIdentifier, (CatalogIntegrationEntity) existingCatalogIntegrationEntity));
      return null;
    });
  }

  @Override
  public void delete(String accountIdentifier) {
    List<IntegrationEntity> integrationEntities = integrationEntityRepository.findByAccountIdentifierAndIntegration(
        accountIdentifier, CatalogIntegrationEntity.Integration.CATALOG);
    integrationEntities.forEach(
        integrationEntity -> delete(accountIdentifier, integrationEntity.getIdentifier(), true));
  }

  @Override
  public DiscoverEntitiesDTO discoverEntities(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String integrationId, int pageIndex, int pageLimit, String sort, String searchTerm, String kinds,
      List<String> filters, String includeFields, String includePaths, Integer prevOffset, Integer nextOffset) {
    long methodStart = System.currentTimeMillis();
    log.info("discoverEntities called for account = {}, integrationId = {}, kinds = {}", accountIdentifier,
        integrationId, kinds);

    long t0 = System.currentTimeMillis();
    TypesIntegrationConfig integrationConfig =
        getIntegrationConfig(accountIdentifier, orgIdentifier, projectIdentifier, integrationId);
    log.info("[TIMER] getIntegrationConfig took {}ms", System.currentTimeMillis() - t0);
    Map<String, List<String>> parsedFilters = parseDiscoverFilters(filters);
    if (integrationConfig.getIntegrationType() == TypesIntegrationConfig.EnumIntegrationType.HarnessCI) {
      if (isNotEmpty(parsedFilters)) {
        throw new InvalidRequestException("Filters are not supported for HarnessCI integrations");
      }
      return discoverHarnessCIEntities(accountIdentifier, orgIdentifier, projectIdentifier, integrationId, pageLimit,
          sort, searchTerm, kinds, prevOffset, nextOffset, integrationConfig);
    }

    t0 = System.currentTimeMillis();
    List<String> filterKinds = resolveFilterKinds(kinds, integrationConfig, parsedFilters);
    UnsubscribedIntegrationEntitiesResult unsubscribedResult =
        getUnsubscribedIntegrationEntities(accountIdentifier, orgIdentifier, projectIdentifier, integrationId, true,
            true, List.of(), sort, searchTerm, kinds, parsedFilters, filterKinds, pageIndex, pageLimit);
    List<EntityMappedEntityResponse> entityMappedEntityResponses = unsubscribedResult.getEntities();
    int unsubscribedTotalElements = unsubscribedResult.getTotalElements();
    log.info("[TIMER] getIntegrationEntities took {}ms, returned {} entities (totalElements={}, totalPages={}, "
            + "pageNumber={}, pageSize={}, nextPage={})",
        System.currentTimeMillis() - t0, entityMappedEntityResponses.size(), unsubscribedTotalElements,
        unsubscribedResult.getTotalPages(), unsubscribedResult.getPageNumber(), unsubscribedResult.getPageSize(),
        unsubscribedResult.getNextPage());

    if (isEmpty(entityMappedEntityResponses)) {
      log.info("[TIMER] discoverEntities total={}ms (early return, empty)", System.currentTimeMillis() - methodStart);
      return DiscoverEntitiesDTO.builder()
          .totalElements(unsubscribedTotalElements)
          .discoverEntitiesResponses(List.of())
          .mergeSuggestions(List.of())
          .offsetPagination(false)
          .build();
    }

    DiscoveryPreparationResult preparationResult =
        prepareDiscoveryResponses(accountIdentifier, integrationId, integrationConfig, entityMappedEntityResponses,
            new DiscoveryCatalogCache(), parseIncludeTokens(includeFields), parseIncludeTokens(includePaths));
    log.debug("[TIMER] discoverEntities total={}ms for account={}, integrationId={}, "
            + "mappedEntities={}, discoverResponses={}, mergeSuggestions={}",
        System.currentTimeMillis() - methodStart, accountIdentifier, integrationId, entityMappedEntityResponses.size(),
        preparationResult.getDiscoverEntitiesResponses().size(), preparationResult.getMergeSuggestions().size());
    return DiscoverEntitiesDTO.builder()
        .totalElements(unsubscribedTotalElements)
        .discoverEntitiesResponses(preparationResult.getDiscoverEntitiesResponses())
        .mergeSuggestions(preparationResult.getMergeSuggestions())
        .offsetPagination(false)
        .build();
  }

  private List<String> parseIncludeTokens(String includeTokens) {
    if (isEmpty(includeTokens)) {
      return List.of();
    }
    return Arrays.stream(includeTokens.split(","))
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .toList();
  }

  private Map<String, List<String>> parseDiscoverFilters(List<String> filters) {
    if (isEmpty(filters)) {
      return Map.of();
    }
    Map<String, LinkedHashSet<String>> valuesByField = new LinkedHashMap<>();
    for (String filter : filters) {
      if (StringUtils.isBlank(filter)) {
        throw new InvalidRequestException("Filter must use field_name:value format");
      }
      int separator = filter.indexOf(':');
      if (separator <= 0 || separator == filter.length() - 1) {
        throw new InvalidRequestException("Filter must use field_name:value format: " + filter);
      }
      String fieldName = filter.substring(0, separator).trim();
      String fieldValue = filter.substring(separator + 1).trim();
      if (!fieldName.matches("[A-Za-z0-9_]+") || fieldValue.isEmpty()) {
        throw new InvalidRequestException("Invalid discover filter: " + filter);
      }
      valuesByField.computeIfAbsent(fieldName, ignored -> new LinkedHashSet<>()).add(fieldValue);
    }
    Map<String, List<String>> parsedFilters = new LinkedHashMap<>();
    valuesByField.forEach((field, values) -> parsedFilters.put(field, List.copyOf(values)));
    return parsedFilters;
  }

  private List<String> resolveFilterKinds(
      String kinds, TypesIntegrationConfig integrationConfig, Map<String, List<String>> parsedFilters) {
    if (isEmpty(parsedFilters)) {
      return List.of();
    }
    List<String> resolvedKinds = isEmpty(kinds) ? integrationConfig.getKinds() : Arrays.asList(kinds.split(","));
    if (isEmpty(resolvedKinds)) {
      throw new InvalidRequestException("Unable to resolve integration kinds for discover filters");
    }
    List<String> normalizedKinds =
        resolvedKinds.stream().map(String::trim).filter(StringUtils::isNotBlank).distinct().toList();
    if (normalizedKinds.isEmpty()) {
      throw new InvalidRequestException("At least one kind is required when discover filters are provided");
    }
    return normalizedKinds;
  }

  private DiscoverEntitiesDTO discoverHarnessCIEntities(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, int pageLimit, String sortBy, String searchTerm, String kinds,
      Integer prevOffset, Integer nextOffset, TypesIntegrationConfig integrationConfig) {
    if (prevOffset != null && nextOffset != null) {
      throw new InvalidRequestException("prevOffset and nextOffset cannot both be provided");
    }
    if ((prevOffset != null && prevOffset < 0) || (nextOffset != null && nextOffset < 0)) {
      throw new InvalidRequestException("Pagination offsets cannot be negative");
    }
    if (pageLimit <= 0) {
      return DiscoverEntitiesDTO.builder()
          .discoverEntitiesResponses(List.of())
          .mergeSuggestions(List.of())
          .offsetPagination(true)
          .build();
    }

    DiscoveryCatalogCache catalogCache = new DiscoveryCatalogCache();
    return prevOffset != null
        ? discoverHarnessCIEntitiesBackward(accountIdentifier, orgIdentifier, projectIdentifier, integrationId,
              pageLimit, sortBy, searchTerm, kinds, prevOffset, integrationConfig, catalogCache)
        : discoverHarnessCIEntitiesForward(accountIdentifier, orgIdentifier, projectIdentifier, integrationId,
              pageLimit, sortBy, searchTerm, kinds, nextOffset == null ? 0 : nextOffset, integrationConfig,
              catalogCache);
  }

  private DiscoverEntitiesDTO discoverHarnessCIEntitiesForward(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, int pageLimit, String sortBy, String searchTerm, String kinds,
      int startOffset, TypesIntegrationConfig integrationConfig, DiscoveryCatalogCache catalogCache) {
    int cursor = startOffset;
    boolean sourceExhausted = false;
    List<DiscoverEntitiesResponse> selectedResponses = new ArrayList<>();
    List<DiscoverEntitiesResponseActionDestinationMerge> mergeSuggestions = new ArrayList<>();
    Set<String> mergeSuggestionRefs = new HashSet<>();

    while (selectedResponses.size() < pageLimit && !sourceExhausted) {
      OffsetMappedEntitiesResult window = getMappedEntitiesByOffset(accountIdentifier, orgIdentifier, projectIdentifier,
          integrationId, sortBy, searchTerm, kinds, cursor, Math.max(pageLimit, 100));
      List<EntityMappedEntityResponse> sourceEntities = window.getEntities();
      if (sourceEntities.isEmpty()) {
        sourceExhausted = true;
        break;
      }
      DiscoveryPreparationResult prepared = prepareDiscoveryResponses(
          accountIdentifier, integrationId, integrationConfig, sourceEntities, catalogCache, List.of(), List.of());
      addUniqueMergeSuggestions(mergeSuggestions, mergeSuggestionRefs, prepared.getMergeSuggestions());
      Map<String, DiscoverEntitiesResponse> responseByUuid =
          prepared.getDiscoverEntitiesResponses().stream().collect(Collectors.toMap(
              DiscoverEntitiesResponse::getIntegrationEntityId, response -> response, (left, right) -> left));

      int inspected = 0;
      for (EntityMappedEntityResponse sourceEntity : sourceEntities) {
        inspected++;
        DiscoverEntitiesResponse preparedResponse = responseByUuid.get(sourceEntity.getUuid());
        if (preparedResponse != null) {
          selectedResponses.add(preparedResponse);
          if (selectedResponses.size() == pageLimit) {
            break;
          }
        }
      }
      cursor += inspected;
      sourceExhausted = inspected == sourceEntities.size() && sourceEntities.size() < window.getRequestedLimit();
    }

    Integer previous = startOffset == 0 ? null : startOffset;
    Integer next = sourceExhausted ? null : cursor;
    return DiscoverEntitiesDTO.builder()
        .discoverEntitiesResponses(selectedResponses)
        .mergeSuggestions(mergeSuggestions)
        .prevOffset(previous)
        .nextOffset(next)
        .offsetPagination(true)
        .build();
  }

  private DiscoverEntitiesDTO discoverHarnessCIEntitiesBackward(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, int pageLimit, String sortBy, String searchTerm, String kinds,
      int boundary, TypesIntegrationConfig integrationConfig, DiscoveryCatalogCache catalogCache) {
    int cursor = boundary;
    int earliestSelectedOffset = boundary;
    List<DiscoverEntitiesResponse> reverseSelectedResponses = new ArrayList<>();
    List<DiscoverEntitiesResponseActionDestinationMerge> mergeSuggestions = new ArrayList<>();
    Set<String> mergeSuggestionRefs = new HashSet<>();

    while (reverseSelectedResponses.size() < pageLimit && cursor > 0) {
      int windowLimit = Math.min(Math.max(pageLimit, 100), cursor);
      int windowOffset = cursor - windowLimit;
      OffsetMappedEntitiesResult window = getMappedEntitiesByOffset(accountIdentifier, orgIdentifier, projectIdentifier,
          integrationId, sortBy, searchTerm, kinds, windowOffset, windowLimit);
      List<EntityMappedEntityResponse> sourceEntities = window.getEntities();
      if (sourceEntities.isEmpty()) {
        cursor = windowOffset;
        continue;
      }
      DiscoveryPreparationResult prepared = prepareDiscoveryResponses(
          accountIdentifier, integrationId, integrationConfig, sourceEntities, catalogCache, List.of(), List.of());
      addUniqueMergeSuggestions(mergeSuggestions, mergeSuggestionRefs, prepared.getMergeSuggestions());
      Map<String, DiscoverEntitiesResponse> responseByUuid =
          prepared.getDiscoverEntitiesResponses().stream().collect(Collectors.toMap(
              DiscoverEntitiesResponse::getIntegrationEntityId, response -> response, (left, right) -> left));

      for (int index = sourceEntities.size() - 1; index >= 0; index--) {
        int rawOffset = windowOffset + index;
        cursor = rawOffset;
        DiscoverEntitiesResponse preparedResponse = responseByUuid.get(sourceEntities.get(index).getUuid());
        if (preparedResponse != null) {
          reverseSelectedResponses.add(preparedResponse);
          earliestSelectedOffset = rawOffset;
          if (reverseSelectedResponses.size() == pageLimit) {
            break;
          }
        }
      }
    }

    if (reverseSelectedResponses.size() < pageLimit && cursor == 0) {
      return discoverHarnessCIEntitiesForward(accountIdentifier, orgIdentifier, projectIdentifier, integrationId,
          pageLimit, sortBy, searchTerm, kinds, 0, integrationConfig, catalogCache);
    }

    Collections.reverse(reverseSelectedResponses);
    Integer previous = reverseSelectedResponses.size() == pageLimit && earliestSelectedOffset > 0
            && hasEligibleHarnessCIEntityBefore(accountIdentifier, orgIdentifier, projectIdentifier, integrationId,
                sortBy, searchTerm, kinds, earliestSelectedOffset, integrationConfig, catalogCache)
        ? earliestSelectedOffset
        : null;
    Integer next = boundary == 0 ? null : boundary;
    return DiscoverEntitiesDTO.builder()
        .discoverEntitiesResponses(reverseSelectedResponses)
        .mergeSuggestions(mergeSuggestions)
        .prevOffset(previous)
        .nextOffset(next)
        .offsetPagination(true)
        .build();
  }

  private boolean hasEligibleHarnessCIEntityBefore(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, String sortBy, String searchTerm, String kinds, int boundary,
      TypesIntegrationConfig integrationConfig, DiscoveryCatalogCache catalogCache) {
    int cursor = boundary;
    while (cursor > 0) {
      int windowLimit = Math.min(100, cursor);
      int windowOffset = cursor - windowLimit;
      OffsetMappedEntitiesResult window = getMappedEntitiesByOffset(accountIdentifier, orgIdentifier, projectIdentifier,
          integrationId, sortBy, searchTerm, kinds, windowOffset, windowLimit);
      if (!window.getEntities().isEmpty()
          && !prepareDiscoveryResponses(accountIdentifier, integrationId, integrationConfig, window.getEntities(),
              catalogCache, List.of(), List.of())
                  .getDiscoverEntitiesResponses()
                  .isEmpty()) {
        return true;
      }
      cursor = windowOffset;
    }
    return false;
  }

  private void addUniqueMergeSuggestions(List<DiscoverEntitiesResponseActionDestinationMerge> target,
      Set<String> seenEntityRefs, List<DiscoverEntitiesResponseActionDestinationMerge> additions) {
    additions.forEach(suggestion -> {
      if (seenEntityRefs.add(suggestion.getEntityRef())) {
        target.add(suggestion);
      }
    });
  }

  private DiscoveryPreparationResult prepareDiscoveryResponses(String accountIdentifier, String integrationId,
      TypesIntegrationConfig integrationConfig, List<EntityMappedEntityResponse> entityMappedEntityResponses,
      DiscoveryCatalogCache catalogCache, List<String> includeFields, List<String> includePaths) {
    if (isEmpty(entityMappedEntityResponses)) {
      return new DiscoveryPreparationResult(List.of(), List.of());
    }

    Set<String> uniqueKinds =
        entityMappedEntityResponses.stream()
            .map(response
                -> response.getData() != null ? (String) response.getData().get(MAPPED_ENTITY_RESPONSE_KIND_KEY) : null)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Set<String> uniqueTypes =
        entityMappedEntityResponses.stream()
            .map(response
                -> response.getData() != null ? (String) response.getData().get(MAPPED_ENTITY_RESPONSE_TYPE_KEY) : null)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    // Remap IM-facing kinds to catalog kinds only for the correlation lookup (template→workflow).
    // uniqueKinds stays as IM sent them so Discover responses still surface Template for unimported rows.
    List<String> idpKinds =
        uniqueKinds.stream().map(this::toCatalogKindForDiscoverLookup).distinct().collect(Collectors.toList());
    List<String> idpTypes = new ArrayList<>(uniqueTypes);

    long t0 = System.currentTimeMillis();
    String scopes;
    if (catalogCache.scopesInitialized) {
      scopes = catalogCache.scopes;
    } else {
      scopes = getScopes(accountIdentifier, integrationConfig);
      catalogCache.scopes = scopes;
      catalogCache.scopesInitialized = true;
    }
    log.info("[TIMER] getScopes took {}ms", System.currentTimeMillis() - t0);

    Set<String> getEntitiesSpecialKinds = Set.of("workflow", "environment", "environmentblueprint");
    boolean getEntitiesSpecialKindsExists =
        idpKinds.stream().map(String::toLowerCase).anyMatch(getEntitiesSpecialKinds::contains);
    List<EntityResponse> entityResponses = new ArrayList<>();

    String mainQueryKinds = getEntitiesSpecialKindsExists
        ? idpKinds.stream()
              .filter(k -> !getEntitiesSpecialKinds.contains(k.toLowerCase()))
              .collect(Collectors.joining(","))
        : String.join(",", idpKinds);

    t0 = System.currentTimeMillis();
    // When every kind is special (e.g. template→workflow), the filter above is empty; skip the main
    // query rather than calling getEntitiesV2 with kind="" and limit=-1 (full-account scan).
    if (!isEmpty(mainQueryKinds)) {
      CatalogQueryKey queryKey = CatalogQueryKey.of(false, List.of(mainQueryKinds.split(",")), idpTypes, scopes);
      List<EntityResponse> cachedEntities = catalogCache.entitiesByQuery.get(queryKey);
      if (cachedEntities == null) {
        GetEntitiesDTO catalogEntities =
            catalogService.getEntitiesV2(accountIdentifier, 0, -1, null, null, false, scopes, null, null, null,
                mainQueryKinds, String.join(",", idpTypes), null, null, null, null, false, false, true);
        cachedEntities = List.copyOf(catalogEntities.getEntityResponses());
        catalogCache.entitiesByQuery.put(queryKey, cachedEntities);
        log.info("[TIMER] catalogService.getEntities (main) took {}ms, returned {} entities",
            System.currentTimeMillis() - t0, cachedEntities.size());
      }
      entityResponses.addAll(cachedEntities);
    } else {
      log.info("[TIMER] catalogService.getEntities (main) skipped — all kinds are special (kinds={})", idpKinds);
    }

    List<String> specialKinds =
        idpKinds.stream().filter(k -> getEntitiesSpecialKinds.contains(k.toLowerCase())).toList();
    t0 = System.currentTimeMillis();
    for (String specialKind : specialKinds) {
      CatalogQueryKey queryKey = CatalogQueryKey.of(true, List.of(specialKind), idpTypes, scopes);
      List<EntityResponse> cachedEntities = catalogCache.entitiesByQuery.get(queryKey);
      if (cachedEntities == null) {
        GetEntitiesDTO catalogEntities = catalogService.getEntities(accountIdentifier, 0, -1, null, null, false, scopes,
            null, null, null, specialKind, String.join(",", idpTypes), null, null, null, null, false, false);
        cachedEntities = List.copyOf(catalogEntities.getEntityResponses());
        catalogCache.entitiesByQuery.put(queryKey, cachedEntities);
      }
      entityResponses.addAll(cachedEntities);
    }
    log.info("[TIMER] catalogService.getEntities (specialKinds={}) took {}ms", specialKinds,
        System.currentTimeMillis() - t0);

    String spacePath = normalizeSpacePath(integrationConfig.getSpacePath(), accountIdentifier);
    Set<String> alreadyCorrelatedEntities = new HashSet<>();
    t0 = System.currentTimeMillis();
    entityResponses.forEach(er -> {
      Set<String> correlatedUUIDs = correlateRawIntegrationEntityAndIDPCatalogEntity(er, spacePath, integrationId);
      alreadyCorrelatedEntities.addAll(correlatedUUIDs);
    });
    log.info("[TIMER] correlateRawIntegrationEntityAndIDPCatalogEntity loop ({} entities) took {}ms",
        entityResponses.size(), System.currentTimeMillis() - t0);

    t0 = System.currentTimeMillis();
    CorrelationLookup correlationLookup = buildCorrelationLookup(entityResponses, entityMappedEntityResponses);
    log.info("[TIMER] buildCorrelationLookup ({} entities, {} target paths) took {}ms", entityResponses.size(),
        correlationLookup.targetPaths.size(), System.currentTimeMillis() - t0);

    List<DiscoverEntitiesResponse> discoverEntitiesResponses = new ArrayList<>();
    List<String> selectedScopes = new ArrayList<>();
    if (integrationConfig.getConfiguration().get("selected_scopes") != null
        && !isEmpty((ArrayList<String>) integrationConfig.getConfiguration().get("selected_scopes"))) {
      List<String> integrationScopes = (ArrayList<String>) integrationConfig.getConfiguration().get("selected_scopes");
      integrationScopes.replaceAll(s -> s.replace(accountIdentifier, "account").replace("/", "."));
      selectedScopes.addAll(integrationScopes);
    }
    boolean allScopes = integrationConfig.getConfiguration().get("all_scopes") != null
        && (boolean) integrationConfig.getConfiguration().get("all_scopes");
    boolean allowCrossScopeCorrelation = integrationConfig.getIntegrationMode() != null
        && !integrationConfig.getIntegrationMode().equals(TypesIntegrationConfig.IntegrationMode.platform);
    log.info("allScopes: {}, allowCrossScopeCorrelation: {}", allScopes, allowCrossScopeCorrelation);
    Map<String, String> actionPerKind = integrationConfig.getActionPerKind();
    Map<String, List<SourceTargetFieldMapping>> fieldMappingsPerKind =
        isEmpty(includeFields) ? Map.of() : parseFieldMappingsPerKind(integrationConfig);

    // Build map: IDP kind name (lowercase) -> configAction
    // actionPerKind keys use integration-manager naming (e.g., "services", "teams")
    // but catalog entities use IDP Kind enum naming (e.g., "component", "group")
    // entityMappedEntityResponse has both: getKind() = integration-manager kind, getData().get("kind") = IDP kind
    Map<String, String> idpKindToConfigAction = new HashMap<>();
    if (actionPerKind != null) {
      for (EntityMappedEntityResponse emr : entityMappedEntityResponses) {
        String integrationKind = emr.getKind();
        String idpKind = emr.getData() != null ? (String) emr.getData().get(MAPPED_ENTITY_RESPONSE_KIND_KEY) : null;
        if (integrationKind != null && idpKind != null && actionPerKind.containsKey(integrationKind)) {
          idpKindToConfigAction.put(idpKind.toLowerCase(), actionPerKind.get(integrationKind));
        }
      }
    }

    t0 = System.currentTimeMillis();
    entityMappedEntityResponses.forEach(entityMappedEntityResponse -> {
      if ((allScopes || selectedScopes.contains(entityMappedEntityResponse.getScope().toString()))
          && !alreadyCorrelatedEntities.contains(entityMappedEntityResponse.getUuid())) {
        try {
          String integrationKind = entityMappedEntityResponse.getKind();
          String configAction =
              actionPerKind != null && integrationKind != null ? actionPerKind.get(integrationKind) : null;
          Optional<DiscoverEntitiesResponse> optionalDiscoverEntitiesResponse =
              prepareDiscoverEntitiesResponse(entityMappedEntityResponse, entityResponses, spacePath, integrationId,
                  allowCrossScopeCorrelation, integrationConfig.getIntegrationType(), configAction, correlationLookup,
                  includeFields, includePaths, fieldMappingsPerKind);
          optionalDiscoverEntitiesResponse.ifPresent(discoverEntitiesResponses::add);
        } catch (Exception e) {
          log.error("Error in prepareDiscoverEntitiesResponse for entityMappedEntityResponse = {} Exception = {}",
              entityMappedEntityResponse, e.getMessage(), e);
        }
      }
    });
    log.info("[TIMER] prepareDiscoverEntitiesResponse loop ({} mapped entities) took {}ms",
        entityMappedEntityResponses.size(), System.currentTimeMillis() - t0);

    // Common merge suggestions — integrations other than HarnessCD/HarnessCI use a shared suggestion list.
    // HarnessCD and HarnessCI use per-entity suggestions.
    // Also skip kinds where action_per_kind restricts to "Register" (no merge allowed)
    t0 = System.currentTimeMillis();
    List<DiscoverEntitiesResponseActionDestinationMerge> mergeSuggestions = List.of();
    if (!usesPerEntityMergeSuggestions(integrationConfig.getIntegrationType())) {
      mergeSuggestions =
          entityResponses.stream()
              .filter(er -> uniqueKinds.stream().anyMatch(k -> er.getKindIdentifier().equalsIgnoreCase(k)))
              .filter(er -> uniqueTypes.stream().anyMatch(t -> er.getType().equalsIgnoreCase(t)))
              .filter(er -> {
                // Skip merge suggestions for kinds restricted to Register only
                String action = idpKindToConfigAction.get(er.getKindIdentifier().toLowerCase());
                return !"Register".equals(action);
              })
              .filter(er -> {
                if (!allowCrossScopeCorrelation) {
                  String erScope = er.getEntityRef().split(":")[1].split("/")[0];
                  return selectedScopes.isEmpty() || allScopes
                      || selectedScopes.stream().anyMatch(s -> s.equalsIgnoreCase(erScope));
                }
                return true;
              })
              .map(er -> {
                DiscoverEntitiesResponseActionDestinationMerge suggestion =
                    new DiscoverEntitiesResponseActionDestinationMerge();
                suggestion.setEntityRef(er.getEntityRef());
                suggestion.setName(er.getName());
                return suggestion;
              })
              .toList();
    }
    log.info("[TIMER] mergeSuggestions took {}ms", System.currentTimeMillis() - t0);
    return new DiscoveryPreparationResult(discoverEntitiesResponses, mergeSuggestions);
  }

  private boolean usesPerEntityMergeSuggestions(TypesIntegrationConfig.EnumIntegrationType integrationType) {
    return integrationType == TypesIntegrationConfig.EnumIntegrationType.HarnessCD
        || integrationType == TypesIntegrationConfig.EnumIntegrationType.HarnessCI;
  }

  private record CatalogQueryKey(boolean specialKind, List<String> kinds, List<String> types, String scopes) {
    private static CatalogQueryKey of(boolean specialKind, List<String> kinds, List<String> types, String scopes) {
      return new CatalogQueryKey(specialKind, normalize(kinds), normalize(types), scopes);
    }

    private static List<String> normalize(List<String> values) {
      return values.stream()
          .filter(Objects::nonNull)
          .map(String::trim)
          .filter(value -> !value.isEmpty())
          .map(String::toLowerCase)
          .distinct()
          .sorted()
          .toList();
    }
  }

  private static class DiscoveryCatalogCache {
    private final Map<CatalogQueryKey, List<EntityResponse>> entitiesByQuery = new HashMap<>();
    private boolean scopesInitialized;
    private String scopes;
  }

  @Override
  public ImportedEntitiesDTO getImportedEntities(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, int pageIndex, int pageLimit, String sort, String searchTerm,
      String kinds) {
    TypesIntegrationConfig integrationConfig =
        getIntegrationConfig(accountIdentifier, orgIdentifier, projectIdentifier, integrationId);

    String spacePath = normalizeSpacePath(integrationConfig.getSpacePath(), accountIdentifier);
    String basePath = "decorator._processed_data.metadata." + CatalogIntegrationEntityConstants.INTEGRATION_PATH_PREFIX
        + "." + spacePath + "." + integrationId;
    String filter;
    if (isEmpty(kinds)) {
      filter = basePath;
    } else {
      filter =
          Arrays.stream(kinds.split(",")).map(kind -> basePath + "." + kind.trim()).collect(Collectors.joining("&"));
      log.info("getImportedEntities: Filtering by kinds, filter = {}", filter);
    }

    GetEntitiesDTO catalogEntities = catalogService.getEntitiesV2(accountIdentifier, pageIndex, pageLimit, sort,
        searchTerm, false, "account.*", null, null, null, null, null, null, null, null, filter, false, false, true);

    if (isEmpty(catalogEntities.getEntityResponses())) {
      return ImportedEntitiesDTO.builder().totalElements(0).importedEntityResponses(List.of()).build();
    }

    String integrationBasePath =
        "metadata." + CatalogIntegrationEntityConstants.INTEGRATION_PATH_PREFIX + "." + spacePath + "." + integrationId;

    Set<String> allEntityUuids = new HashSet<>();
    Map<String, Map<String, String>> entityRefToUuidActions = new HashMap<>();
    Map<String, Map<String, Long>> entityRefToUuidImportedAt = new HashMap<>();
    for (EntityResponse entityResponse : catalogEntities.getEntityResponses()) {
      String decorator = entityResponse.getDecorator();
      if (StringUtils.isEmpty(decorator)) {
        continue;
      }
      try {
        Map<String, Object> decoratorMap = YamlUtils.loadYamlStringAsMap(decorator);
        Map<String, Object> processedData = decoratorMap.containsKey(PROCESSED_DATA)
            ? (Map<String, Object>) decoratorMap.get(PROCESSED_DATA)
            : decoratorMap;
        Map<String, String> uuidToAction = collectEntityLinkageInfoFromLinkage(processedData, integrationBasePath);
        allEntityUuids.addAll(uuidToAction.keySet());
        entityRefToUuidActions.put(entityResponse.getEntityRef(), uuidToAction);
        Map<String, Long> uuidToImportedAt = collectEntityImportedAtFromLinkage(processedData, integrationBasePath);
        entityRefToUuidImportedAt.put(entityResponse.getEntityRef(), uuidToImportedAt);
      } catch (Exception ex) {
        log.warn("Error extracting entity UUIDs from decorator for entity = {} Error = {}",
            entityResponse.getEntityRef(), ex.getMessage(), ex);
      }
    }

    Map<String, EntityMappedEntityResponse> uuidToMappedEntity = new HashMap<>();
    if (!allEntityUuids.isEmpty()) {
      try {
        List<EntityMappedEntityResponse> mappedEntities = getIntegrationEntities(accountIdentifier, orgIdentifier,
            projectIdentifier, integrationId, false, false, new ArrayList<>(allEntityUuids), sort, null, null);
        mappedEntities.forEach(me -> uuidToMappedEntity.put(me.getUuid(), me));
      } catch (Exception ex) {
        log.error("Error fetching mapped entities for imported entities API. Error = {}", ex.getMessage(), ex);
      }
    }

    List<ImportedEntityResponse> importedEntityResponses = new ArrayList<>();
    for (EntityResponse entityResponse : catalogEntities.getEntityResponses()) {
      ImportedEntityResponse importedEntityResponse = new ImportedEntityResponse();
      importedEntityResponse.setEntity(entityResponse);

      Map<String, String> uuidActions =
          entityRefToUuidActions.getOrDefault(entityResponse.getEntityRef(), Collections.emptyMap());
      ImportedEntityResponseRawEntityDetails rawEntityDetails = new ImportedEntityResponseRawEntityDetails();
      for (Map.Entry<String, String> entry : uuidActions.entrySet()) {
        EntityMappedEntityResponse mappedEntity = uuidToMappedEntity.get(entry.getKey());
        if (mappedEntity != null) {
          rawEntityDetails.setName(mappedEntity.getName());
          rawEntityDetails.setIdentifier(
              mappedEntity.getEntityInfo() != null ? mappedEntity.getEntityInfo().getIdentifier() : null);
          rawEntityDetails.setActionPerformed(
              ImportedEntityResponseRawEntityDetails.ActionPerformedEnum.fromValue(entry.getValue()));
          Map<String, Long> uuidImportedAt =
              entityRefToUuidImportedAt.getOrDefault(entityResponse.getEntityRef(), Collections.emptyMap());
          Long importedAt = uuidImportedAt.get(entry.getKey());
          rawEntityDetails.setImportedAt(importedAt != null ? importedAt : mappedEntity.getDetectedAt());
          break;
        }
      }
      importedEntityResponse.setRawEntityDetails(rawEntityDetails);
      importedEntityResponses.add(importedEntityResponse);
    }

    return ImportedEntitiesDTO.builder()
        .totalElements(catalogEntities.getTotalElements())
        .importedEntityResponses(importedEntityResponses)
        .build();
  }

  @Override
  public void saveDiscoverEntities(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String integrationId, SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest) {
    idpServiceMiscRedisProducer.publishIDPIntegrationCatalogProcessorEventToRedis(
        accountIdentifier, orgIdentifier, projectIdentifier, integrationId, saveDiscoverEntitiesRequest);
  }

  @Override
  public UnlinkIntegrationEntitiesResponse unlinkIntegrationEntities(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, List<String> entityRefs) {
    TypesIntegrationConfig typesIntegrationConfig =
        getIntegrationConfig(accountIdentifier, orgIdentifier, projectIdentifier, integrationId);
    String integrationType = typesIntegrationConfig.getIntegrationType().name();
    String integrationIdentifier = typesIntegrationConfig.getIdentifier();

    UnlinkIntegrationEntitiesResponse unlinkIntegrationEntitiesResponse = new UnlinkIntegrationEntitiesResponse();
    List<DiscoverEntitiesResponseActionDestinationMerge> success = new ArrayList<>();
    List<DiscoverEntitiesResponseActionDestinationMerge> failed = new ArrayList<>();

    List<ScopeInfo> scopeInfos =
        catalogServiceHelper
            .getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, null, String.join(",", entityRefs))
            .getLeft();
    List<CatalogEntity> catalogEntities = catalogEntityRepository.getEntitiesForEntityRefsAndKinds(
        accountIdentifier, String.join(",", entityRefs), scopeInfos, List.of());

    failed.addAll(entityRefs.stream()
                      .filter(ref -> catalogEntities.stream().noneMatch(ce -> CatalogUtils.entityRef(ce).equals(ref)))
                      .map(ref
                          -> new DiscoverEntitiesResponseActionDestinationMerge().entityRef(ref).name(
                              catalogEntities.stream()
                                  .filter(ce -> CatalogUtils.entityRef(ce).equals(ref))
                                  .map(CatalogEntity::getName)
                                  .findFirst()
                                  .orElse(null)))
                      .toList());

    String catalogIntegrationEntityAdditionalLinkageConfig =
        getIntegrationEntityAdditionalLinkageConfig(integrationType);

    String spacePath = normalizeSpacePath(typesIntegrationConfig.getSpacePath(), accountIdentifier);
    String integrationBasePath =
        "metadata." + CatalogIntegrationEntityConstants.INTEGRATION_PATH_PREFIX + "." + spacePath + "." + integrationId;

    catalogEntities.forEach(catalogEntity -> {
      try {
        EntityResponse entityResponse = catalogService.getEntity(accountIdentifier, catalogEntity.getOrgIdentifier(),
            catalogEntity.getProjectIdentifier(), CatalogUtils.entityRef(catalogEntity), false, false, true);
        String entityRef = CatalogUtils.entityRef(catalogEntity);
        String entityName = catalogEntity.getName();

        Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData();
        Map<String, String> entityUuidToKind = collectEntityUuidToKindFromLinkage(processedData, integrationBasePath);

        boolean allUnlinked = true;
        if (typesIntegrationConfig.getIntegrationType() != TypesIntegrationConfig.EnumIntegrationType.HarnessCI) {
          for (Map.Entry<String, String> entry : entityUuidToKind.entrySet()) {
            OpenapiSubscribeEntitiesRequest subscribeEntitiesRequest =
                prepareSubscribeEntitiesRequest(entry.getValue(), entry.getKey());
            EntitySubscribeEntitiesResponse entitySubscribeEntitiesResponse = integrationEntitiesUnsubscribe(
                accountIdentifier, orgIdentifier, projectIdentifier, integrationId, subscribeEntitiesRequest);

            if (isEmpty(entitySubscribeEntitiesResponse.getSuccess())) {
              allUnlinked = false;
              break;
            }
          }
        }

        if (allUnlinked) {
          removeDecoratedIntegrationMetadataFromIDPCatalog(
              catalogEntity, integrationBasePath, typesIntegrationConfig.getIntegrationType());
          setupUsageProducer.deleteCdServiceSetupUsage(catalogEntity.getAccountIdentifier(),
              catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier(), catalogEntity.getIdentifier());
          String yaml = removeFields(catalogEntity.getYaml(), catalogIntegrationEntityAdditionalLinkageConfig);
          EntityUpdateRequest entityUpdateRequest = entityUpdateRequest(accountIdentifier, entityResponse, yaml);
          boolean shouldUpdateOnGit = catalogEntity.getReferenceType() == ReferenceType.GIT;
          catalogService.updateEntity(accountIdentifier, catalogEntity.getOrgIdentifier(),
              catalogEntity.getProjectIdentifier(), CatalogUtils.entityRef(catalogEntity), entityUpdateRequest, false,
              shouldUpdateOnGit, false, false);

          success.add(new DiscoverEntitiesResponseActionDestinationMerge().entityRef(entityRef).name(entityName));
        } else {
          failed.add(new DiscoverEntitiesResponseActionDestinationMerge().entityRef(entityRef).name(entityName));
        }
      } catch (Exception ex) {
        log.error("Error in unlinkIntegrationEntities. Exception = {}", ex.getMessage(), ex);
        failed.add(new DiscoverEntitiesResponseActionDestinationMerge()
                       .entityRef(CatalogUtils.entityRef(catalogEntity))
                       .name(catalogEntity.getName()));
      } finally {
        GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
      }
    });

    unlinkIntegrationEntitiesResponse.setSuccess(success);
    unlinkIntegrationEntitiesResponse.setFailed(failed);
    return unlinkIntegrationEntitiesResponse;
  }

  public void unlinkIntegrationEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String integrationId, String uuid, String kind, String integrationKind) {
    TypesIntegrationConfig typesIntegrationConfig =
        getIntegrationConfig(accountIdentifier, orgIdentifier, projectIdentifier, integrationId);
    String integrationType = typesIntegrationConfig.getIntegrationType().name();
    String catalogIntegrationEntityAdditionalLinkageConfig =
        getIntegrationEntityAdditionalLinkageConfig(integrationType);
    String spacePath = normalizeSpacePath(typesIntegrationConfig.getSpacePath(), accountIdentifier);
    String integrationBasePath = buildLinkageConfigPath(spacePath, integrationId, integrationKind);

    List<ScopeInfo> scopeInfos =
        catalogServiceHelper
            .getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, catalogServiceHelper.getAllScopes(), "")
            .getLeft();

    CatalogEntity catalogEntity = findMappedEntity(
        uuid, integrationBasePath, scopeInfos, kind, typesIntegrationConfig.getIntegrationType(), null);

    if (catalogEntity != null) {
      log.info("Unlinking catalog entity {}", catalogServiceHelper.queryableEntityRef(catalogEntity));
      EntityResponse entityResponse = catalogService.getEntity(accountIdentifier, catalogEntity.getOrgIdentifier(),
          catalogEntity.getProjectIdentifier(), CatalogUtils.entityRef(catalogEntity), false, false, true);
      removeDecoratedIntegrationMetadataFromIDPCatalog(
          catalogEntity, integrationBasePath, typesIntegrationConfig.getIntegrationType());
      if (TypesIntegrationConfig.EnumIntegrationType.HarnessCD.equals(typesIntegrationConfig.getIntegrationType())) {
        setupUsageProducer.deleteCdServiceSetupUsage(catalogEntity.getAccountIdentifier(),
            catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier(), catalogEntity.getIdentifier());
      }
      String yaml = removeFields(catalogEntity.getYaml(), catalogIntegrationEntityAdditionalLinkageConfig);
      EntityUpdateRequest entityUpdateRequest = entityUpdateRequest(accountIdentifier, entityResponse, yaml);
      try {
        catalogService.updateEntity(accountIdentifier, catalogEntity.getOrgIdentifier(),
            catalogEntity.getProjectIdentifier(), CatalogUtils.entityRef(catalogEntity), entityUpdateRequest, false,
            true, false, false);
      } finally {
        GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
      }
    }
  }

  public void saveDiscoverEntitiesInternal(String accountIdentifier, String integrationOrgIdentifier,
      String integrationProjectIdentifier, String integrationId,
      SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest, UserPrincipal userPrincipal) {
    idpCommonService.newFlowCheck(accountIdentifier);
    TypesIntegrationConfig integrationConfig =
        getIntegrationConfig(accountIdentifier, integrationOrgIdentifier, integrationProjectIdentifier, integrationId);

    List<String> integrationEntitiesUuids = new ArrayList<>();
    saveDiscoverEntitiesRequest.getIntegrationEntities().forEach(saveDiscoverEntitiesRequestIntegrationEntities
        -> integrationEntitiesUuids.add(saveDiscoverEntitiesRequestIntegrationEntities.getIntegrationEntityId()));
    boolean fetchAll = !SaveDiscoverEntitiesRequest.SelectionFilterEnum.MANUAL.equals(
        saveDiscoverEntitiesRequest.getSelectionFilter());
    List<EntityMappedEntityResponse> entityMappedEntityResponses =
        integrationConfig.getIntegrationType() == TypesIntegrationConfig.EnumIntegrationType.HarnessCI
        ? getHarnessCIIntegrationEntities(accountIdentifier, integrationOrgIdentifier, integrationProjectIdentifier,
              integrationId, fetchAll, true, integrationEntitiesUuids)
        : getIntegrationEntities(accountIdentifier, integrationOrgIdentifier, integrationProjectIdentifier,
              integrationId, fetchAll, true, integrationEntitiesUuids, null, null, null);

    if (integrationConfig.getIntegrationType() == TypesIntegrationConfig.EnumIntegrationType.HarnessCI && !fetchAll) {
      Set<String> returnedUuids =
          entityMappedEntityResponses.stream().map(EntityMappedEntityResponse::getUuid).collect(Collectors.toSet());
      List<String> missingUuids =
          integrationEntitiesUuids.stream().filter(uuid -> !returnedUuids.contains(uuid)).toList();
      if (!missingUuids.isEmpty()) {
        throw new UnexpectedException(
            String.format("HarnessCI mapped entities were not returned for requested UUIDs: %s", missingUuids));
      }
    }

    if (userPrincipal != null) {
      configureIntegrationAutoDiscoveryAsPerRequest(accountIdentifier, integrationOrgIdentifier,
          integrationProjectIdentifier, integrationId, integrationConfig,
          saveDiscoverEntitiesRequest.isAutoDiscover() != null && saveDiscoverEntitiesRequest.isAutoDiscover());
    }

    Map<String, Object> catalogIntegrationEntityAdditionalLinkageConfigPlaceholder =
        getIntegrationEntityAdditionalLinkageConfigPlaceholder(integrationConfig.getIntegrationType().name());

    final boolean isPlatformMode =
        integrationConfig.getIntegrationMode().equals(TypesIntegrationConfig.IntegrationMode.platform);
    final List<ScopeInfo> allScopeInfos;
    final Map<String, ScopeInfo> scopeInfoCache = new HashMap<>();

    if (!isPlatformMode) {
      allScopeInfos =
          catalogServiceHelper
              .getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, catalogServiceHelper.getAllScopes(), "")
              .getLeft();
    } else {
      allScopeInfos = null;
      Set<String> uniqueScopeKeys =
          entityMappedEntityResponses.stream()
              .map(e -> scopeKeyForScope(e.getScope().getOrgIdentifier(), e.getScope().getProjectIdentifier()))
              .collect(Collectors.toSet());

      for (String scopeKey : uniqueScopeKeys) {
        String[] orgAndProject = parseScopeKey(scopeKey);
        ScopeInfo scopeInfo = catalogServiceHelper.getScopeInfo(accountIdentifier, orgAndProject[0], orgAndProject[1]);
        scopeInfoCache.put(scopeKey, scopeInfo);
      }
    }

    Map<String, String> typeOverrides = new HashMap<>();
    if (integrationConfig.getIntegrationType() == TypesIntegrationConfig.EnumIntegrationType.GitHub
        && saveDiscoverEntitiesRequest.getIntegrationEntities() != null) {
      for (SaveDiscoverEntitiesRequestIntegrationEntities reqEntity :
          saveDiscoverEntitiesRequest.getIntegrationEntities()) {
        if (reqEntity.getIntegrationEntityId() != null && !StringUtils.isBlank(reqEntity.getType())) {
          typeOverrides.put(reqEntity.getIntegrationEntityId(), reqEntity.getType());
        }
      }
    }

    entityMappedEntityResponses.forEach(entityMappedEntityResponse -> {
      try {
        String integrationEntityUuid = entityMappedEntityResponse.getUuid();

        String kind = (String) entityMappedEntityResponse.getData().get(MAPPED_ENTITY_RESPONSE_KIND_KEY);
        String type = typeOverrides.getOrDefault(
            integrationEntityUuid, (String) entityMappedEntityResponse.getData().get(MAPPED_ENTITY_RESPONSE_TYPE_KEY));

        if ("file_content".equals(type)) {
          String fileOrgId = entityMappedEntityResponse.getScope().getOrgIdentifier();
          String fileProjId = entityMappedEntityResponse.getScope().getProjectIdentifier();
          handleFileContentUpload(entityMappedEntityResponse, accountIdentifier, fileOrgId, fileProjId);
          return;
        }

        if (integrationConfig.getIntegrationType() == TypesIntegrationConfig.EnumIntegrationType.CatalogInfo) {
          String ciOrgId = entityMappedEntityResponse.getScope().getOrgIdentifier();
          String ciProjId = entityMappedEntityResponse.getScope().getProjectIdentifier();
          SaveDiscoverEntitiesRequestIntegrationEntities catalogInfoRequestEntity =
              saveDiscoverEntitiesRequest.getIntegrationEntities() == null
              ? null
              : saveDiscoverEntitiesRequest.getIntegrationEntities()
                    .stream()
                    .filter(e -> entityMappedEntityResponse.getUuid().equals(e.getIntegrationEntityId()))
                    .findFirst()
                    .orElse(null);
          String actionDestination =
              catalogInfoRequestEntity != null ? catalogInfoRequestEntity.getActionDestination() : null;
          String actionIdentifier =
              catalogInfoRequestEntity != null ? catalogInfoRequestEntity.getActionIdentifier() : null;
          SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum action =
              catalogInfoRequestEntity != null ? catalogInfoRequestEntity.getAction() : null;
          List<ScopeInfo> catalogInfoScopeInfos;
          if (isPlatformMode) {
            catalogInfoScopeInfos = Collections.singletonList(scopeInfoCache.get(scopeKeyForScope(ciOrgId, ciProjId)));
          } else {
            // Bound the linkage lookup to this entity's scope so Mongo filters on a single indexed
            // parentUniqueId rather than every scope in the account. allScopeInfos only contains scopes
            // that already have catalog entities, so a miss means the scope has no entities and therefore
            // cannot hold a linkage - resolve to an empty list (lookup matches nothing) instead of
            // widening back to the whole account, which is exactly the first-time-register path.
            catalogInfoScopeInfos =
                allScopeInfos.stream()
                    .filter(scopeInfo
                        -> StringUtils.equals(StringUtils.defaultString(scopeInfo.getOrgIdentifier()),
                               StringUtils.defaultString(ciOrgId))
                            && StringUtils.equals(StringUtils.defaultString(scopeInfo.getProjectIdentifier()),
                                StringUtils.defaultString(ciProjId)))
                    .findFirst()
                    .map(Collections::singletonList)
                    .orElseGet(Collections::emptyList);
          }
          boolean registered = handleCatalogInfoEntity(entityMappedEntityResponse, accountIdentifier, ciOrgId, ciProjId,
              integrationConfig, integrationId, actionDestination, actionIdentifier, action, catalogInfoScopeInfos);
          if (registered && userPrincipal != null) {
            subscribeForIntegrationEntity(accountIdentifier, ciOrgId, ciProjId, integrationId,
                entityMappedEntityResponse.getKind(), integrationEntityUuid, userPrincipal);
          }
          return;
        }

        String orgIdentifier = entityMappedEntityResponse.getScope().getOrgIdentifier();
        String projectIdentifier = entityMappedEntityResponse.getScope().getProjectIdentifier();
        String identifier = (String) entityMappedEntityResponse.getData().get("identifier");
        String linkageIdentifier = (String) entityMappedEntityResponse.getData().get("identifier");

        String catalogIntegrationEntityLinkageConfigPath =
            buildLinkageConfigPath(normalizeSpacePath(integrationConfig.getSpacePath(), accountIdentifier),
                integrationId, entityMappedEntityResponse.getKind());

        List<ScopeInfo> scopeInfos;
        if (isPlatformMode) {
          String scopeKey = scopeKeyForScope(orgIdentifier, projectIdentifier);
          scopeInfos = Collections.singletonList(scopeInfoCache.get(scopeKey));
        } else {
          scopeInfos = allScopeInfos;
        }

        CatalogEntity mappedEntity = findMappedEntity(integrationEntityUuid, catalogIntegrationEntityLinkageConfigPath,
            scopeInfos, kind, integrationConfig.getIntegrationType(), entityMappedEntityResponse);
        CatalogEntity correlatedEntity = null;
        if (mappedEntity == null) {
          correlatedEntity = findCorrelatedEntity(entityMappedEntityResponse, scopeInfos, kind, identifier);
        }

        Map<String, String> actionPerKind = integrationConfig.getActionPerKind();
        String configAction = actionPerKind != null ? actionPerKind.get(entityMappedEntityResponse.getKind()) : null;

        SaveEntityResult result;
        if ("Register".equals(configAction)) {
          result = processEntityRegisterOnly(saveDiscoverEntitiesRequest, entityMappedEntityResponse,
              integrationEntityUuid, mappedEntity, correlatedEntity, accountIdentifier, orgIdentifier,
              projectIdentifier, kind, type, identifier, catalogIntegrationEntityLinkageConfigPath,
              catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig, scopeInfos);
        } else if ("Merge".equals(configAction)) {
          result = processEntityMergeOnly(saveDiscoverEntitiesRequest, entityMappedEntityResponse,
              integrationEntityUuid, mappedEntity, correlatedEntity, accountIdentifier,
              catalogIntegrationEntityLinkageConfigPath, catalogIntegrationEntityAdditionalLinkageConfigPlaceholder,
              integrationConfig, scopeInfos, identifier);
        } else {
          result = processEntityDefault(saveDiscoverEntitiesRequest, entityMappedEntityResponse, integrationEntityUuid,
              mappedEntity, correlatedEntity, accountIdentifier, orgIdentifier, projectIdentifier, kind, type,
              identifier, catalogIntegrationEntityLinkageConfigPath,
              catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig, scopeInfos);
        }
        CatalogEntity processedEntity = result.getProcessedEntity();
        linkageIdentifier = result.getLinkageIdentifier();

        if (processedEntity != null) {
          Optional<CatalogEntity> freshEntity = catalogEntityRepository.findById(processedEntity.getId());
          if (freshEntity.isEmpty()) {
            log.warn("Entity {} (id: {}) no longer exists after merge/register, skipping relation handling",
                CatalogUtils.entityRef(processedEntity), processedEntity.getId());
          } else {
            processedEntity = freshEntity.get();
            log.info("Storing and resolving relations for entity {} (UUID: {})",
                CatalogUtils.entityRef(processedEntity), integrationEntityUuid);

            List<String> relationUuids = new ArrayList<>();
            relationUuids.addAll(extractDependsOnUuids(entityMappedEntityResponse));
            relationUuids.addAll(extractPartOfUuids(entityMappedEntityResponse));
            relationUuids.addAll(extractOwnedByUuids(entityMappedEntityResponse));
            Map<String, EntityMappedEntityResponse> uuidToMappedEntityForRelation = Collections.emptyMap();
            Map<String, String> uuidToLinkagePath = Collections.emptyMap();
            if (isNotEmpty(relationUuids)) {
              uuidToMappedEntityForRelation = getMappedEntitiesForUuids(accountIdentifier, integrationOrgIdentifier,
                  integrationProjectIdentifier, integrationId, relationUuids);
              String spacePath = normalizeSpacePath(integrationConfig.getSpacePath(), accountIdentifier);
              uuidToLinkagePath = buildLinkagePathPerUuid(relationUuids, uuidToMappedEntityForRelation, spacePath,
                  integrationId, catalogIntegrationEntityLinkageConfigPath);
            }

            try {
              storeDependsOnUuids(processedEntity, entityMappedEntityResponse, uuidToLinkagePath,
                  uuidToMappedEntityForRelation, catalogIntegrationEntityLinkageConfigPath, scopeInfos,
                  integrationConfig.getIntegrationType());
            } catch (Exception e) {
              log.error("Error storing dependsOn for entity {} (UUID: {}): {}", CatalogUtils.entityRef(processedEntity),
                  integrationEntityUuid, e.getMessage(), e);
            }
            freshEntity = catalogEntityRepository.findById(processedEntity.getId());
            if (freshEntity.isPresent()) {
              processedEntity = freshEntity.get();
            }

            try {
              storePartOfUuids(processedEntity, entityMappedEntityResponse, uuidToLinkagePath,
                  uuidToMappedEntityForRelation, catalogIntegrationEntityLinkageConfigPath, scopeInfos,
                  integrationConfig.getIntegrationType());
            } catch (Exception e) {
              log.error("Error storing partOf for entity {} (UUID: {}): {}", CatalogUtils.entityRef(processedEntity),
                  integrationEntityUuid, e.getMessage(), e);
            }
            freshEntity = catalogEntityRepository.findById(processedEntity.getId());
            if (freshEntity.isPresent()) {
              processedEntity = freshEntity.get();
            }

            try {
              storeOwnedByUuids(processedEntity, entityMappedEntityResponse, uuidToLinkagePath,
                  uuidToMappedEntityForRelation, catalogIntegrationEntityLinkageConfigPath, scopeInfos,
                  integrationConfig.getIntegrationType());
            } catch (Exception e) {
              log.error("Error storing ownedBy for entity {} (UUID: {}): {}", CatalogUtils.entityRef(processedEntity),
                  integrationEntityUuid, e.getMessage(), e);
            }
            freshEntity = catalogEntityRepository.findById(processedEntity.getId());
            if (freshEntity.isPresent()) {
              processedEntity = freshEntity.get();
            }

            try {
              resolveWaitingEntitiesWithBatching(integrationEntityUuid, processedEntity);
            } catch (Exception e) {
              log.error("Error resolving waiting dependsOn entities for UUID {}: {}", integrationEntityUuid,
                  e.getMessage(), e);
            }
            try {
              resolveWaitingPartOfEntitiesWithBatching(integrationEntityUuid, processedEntity);
            } catch (Exception e) {
              log.error(
                  "Error resolving waiting partOf entities for UUID {}: {}", integrationEntityUuid, e.getMessage(), e);
            }
            try {
              resolveWaitingOwnedByEntitiesWithBatching(integrationEntityUuid, processedEntity);
            } catch (Exception e) {
              log.error(
                  "Error resolving waiting ownedBy entities for UUID {}: {}", integrationEntityUuid, e.getMessage(), e);
            }
          }
        } else {
          log.debug("No processed entity found for UUID {}, skipping relation handling", integrationEntityUuid);
        }

        // After creating/updating an ai_asset catalog entity, fetch and upload its file content from IM.
        if (processedEntity != null && "aiasset".equals(kind) && !"file_content".equals(type)) {
          String fileContentKind = "aiasset_" + type + "_file_content";
          String assetId = getIntegrationMetadataProperty(
              processedEntity, integrationConfig.getIntegrationType().name(), "asset_id");
          if (isNotEmpty(assetId)) {
            fetchAndUploadFileContent(processedEntity, assetId, fileContentKind, accountIdentifier,
                integrationOrgIdentifier, integrationProjectIdentifier, integrationId);
          }
        }

        if (integrationConfig.getIntegrationType().equals(TypesIntegrationConfig.EnumIntegrationType.HarnessCD)) {
          setupUsageProducer.publishCdServiceSetupUsage(
              accountIdentifier, orgIdentifier, projectIdentifier, identifier, linkageIdentifier);
        }

        if (userPrincipal != null
            && integrationConfig.getIntegrationType() != TypesIntegrationConfig.EnumIntegrationType.HarnessCI) {
          subscribeForIntegrationEntity(accountIdentifier, integrationOrgIdentifier, integrationProjectIdentifier,
              integrationId, entityMappedEntityResponse.getKind(), integrationEntityUuid, userPrincipal);
        }
      } catch (Exception ex) {
        log.error("Error in processing integration entity = {} Exception = {}", entityMappedEntityResponse.getUuid(),
            ex.getMessage(), ex);
        if (integrationConfig.getIntegrationType() == TypesIntegrationConfig.EnumIntegrationType.HarnessCI) {
          throw new UnexpectedException(
              "Error while registering HarnessCI integration entity " + entityMappedEntityResponse.getUuid(), ex);
        }
      } finally {
        GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
      }
    });
  }

  /**
   * Handles a CatalogInfo integration entity. Already-linked entities are resolved by integration
   * UUID before any Backstage→Harness conversion so manual REGISTER can fail and git-synced entities
   * can skip without doing unnecessary conversion.
   * <p>
   * For inline linked entities, Redis/IM updates carry refreshed Backstage YAML in data.content. It is
   * converted and applied to the existing Harness entity while preserving the existing identifier,
   * then the integration linkage metadata is refreshed. For unlinked entities, the converted YAML
   * creates the catalog entity (inline or git-referenced based on git sync config) and is decorated.
   * Kind and identifier are taken from the converted Harness YAML (after any UI rename) — not from
   * Integration Manager data — so Template→workflow remaps stay consistent for existence checks, git
   * sync, and post-create decorate/subscribe.
   * <p>
   * Already-linked entities (found by integration UUID): skip when git sync is enabled; otherwise
   * refresh from converted Backstage YAML. Manual REGISTER against an already-linked entity throws.
   * Explicit MERGE is rejected (CatalogInfo is register-only); Redis updates with action null still
   * decorate when linked. Returns true if a new entity was registered, false if skipped or if a
   * non-fatal error prevented registration. Exceptions from createEntity propagate to the caller's
   * forEach try/catch.
   */
  boolean handleCatalogInfoEntity(EntityMappedEntityResponse entity, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, TypesIntegrationConfig integrationConfig, String integrationId,
      String actionDestination, String actionIdentifier,
      SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum action, List<ScopeInfo> scopeInfos) {
    // CatalogInfo UI is register-only. Explicit MERGE would otherwise fall through to createEntity and
    // decorate a duplicate. Redis/IM updates leave action null and still decorate when already linked.
    if (SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.MERGE.equals(action)) {
      throw new InvalidRequestException(
          "Action MERGE is not allowed for CatalogInfo. Integration supports Register only");
    }

    Map<String, Object> data = entity.getData();
    String content = (String) data.get(CATALOG_INFO_DATA_CONTENT);
    String sourceRepo = (String) data.get(CATALOG_INFO_DATA_REPO);
    String sourceBranch = (String) data.get(CATALOG_INFO_DATA_BRANCH);

    String spacePath = normalizeSpacePath(integrationConfig.getSpacePath(), accountIdentifier);
    String linkagePath = buildLinkageConfigPath(spacePath, integrationId, entity.getKind());
    // No kind filter: the catalog kind comes from the converted YAML and can change when the
    // catalog-info.yaml changes, while the linkage must still be found to avoid a duplicate entity.
    // Scope list is bounded by the caller to the entity's own scope where possible.
    // Resolve before conversion so REGISTER and git-sync skip paths do not do unnecessary work.
    CatalogEntity mappedEntity = findMappedEntity(entity.getUuid(), linkagePath, scopeInfos, null,
        TypesIntegrationConfig.EnumIntegrationType.CatalogInfo, entity);

    if (mappedEntity != null) {
      if (SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER.equals(action)) {
        throw new InvalidRequestException(
            String.format("Cannot register: integration entity %s is already linked to catalog entity %s",
                entity.getUuid(), CatalogUtils.entityRef(mappedEntity)));
      }
      boolean configGitSyncEnabled = isGitSyncEnabled(integrationConfig);
      boolean gitReferenced = mappedEntity instanceof GitReferencedCatalogEntity;
      if (configGitSyncEnabled || gitReferenced) {
        // Git is source of truth: skip when the integration still syncs to git, and also when the
        // linked entity is already git-backed (sync later disabled, or moved to git manually). A
        // redis refresh with shouldUpdateOnGit would otherwise commit converted Backstage YAML to
        // the customer repo on every IM update.
        log.debug("Skipping CatalogInfo entity {} - already linked and git-backed (configSync={}, gitReferenced={}); "
                + "update skipped",
            entity.getUuid(), configGitSyncEnabled, gitReferenced);
        return false;
      }
    }

    if (isEmpty(content)) {
      log.warn("CatalogInfo entity {} missing content; skipping", entity.getUuid());
      return false;
    }

    String harnessYaml;
    Map<String, Object> harnessYamlMap;
    try {
      harnessYaml =
          idpToHarnessHelper.convertBackstageToHarness(accountIdentifier, orgIdentifier, projectIdentifier, content);
      // Parsing is part of the conversion contract: malformed or non-map converter output must skip the
      // entity like a conversion failure, not escape to the caller's generic catch.
      harnessYamlMap = YamlUtils.loadYamlStringAsMap(harnessYaml);
    } catch (Exception e) {
      log.warn("Skipping CatalogInfo entity {} - conversion failed: {}", entity.getUuid(), e.getMessage());
      return false;
    }
    if (isEmpty(harnessYamlMap)) {
      log.warn("Skipping CatalogInfo entity {} - converted YAML is empty", entity.getUuid());
      return false;
    }

    boolean yamlOverridden =
        applyCatalogInfoRegisterNameAndIdentifier(harnessYamlMap, actionDestination, actionIdentifier);
    String kind = CommonUtils.from(harnessYamlMap, "kind", String.class);
    String identifier = CommonUtils.from(harnessYamlMap, "identifier", String.class);
    if (isEmpty(kind) || isEmpty(identifier)) {
      log.warn("CatalogInfo entity {} converted YAML missing kind/identifier; skipping", entity.getUuid());
      return false;
    }
    // The converter serializes kind in display casing (Component, Workflow, API) while the catalog
    // persists and queries it lowercase, so every lookup and git path below needs the sanitized form.
    // createEntity keeps receiving the converter's YAML unchanged - it sanitizes the kind itself.
    kind = kind.toLowerCase();

    if (mappedEntity != null) {
      // updateEntity derives kind from the entity ref and rejects a YAML kind that disagrees with it,
      // so a catalog-info.yaml that changed kind cannot refresh the linked entity in place.
      if (!kind.equalsIgnoreCase(mappedEntity.getKind())) {
        log.warn("Skipping CatalogInfo entity {} - converted kind {} differs from linked catalog entity {}",
            entity.getUuid(), kind, CatalogUtils.entityRef(mappedEntity));
        return false;
      }
      // An IM update may rename metadata.name, but linkage is to the existing Harness entity. Keep
      // that stable identifier while refreshing all other fields from the converted Backstage YAML.
      harnessYamlMap.put("identifier", mappedEntity.getIdentifier());
      String yamlToUpdate = YamlUtils.writeObjectAsYaml(harnessYamlMap);
      if (catalogInfoYamlContentEquivalent(yamlToUpdate, mappedEntity.getYaml())) {
        log.debug("Skipping CatalogInfo entity {} - YAML unchanged; skipping decorate and update", entity.getUuid());
        return false;
      }
      // Decorate first, like mergeEntity: it saves this pre-update snapshot under the same document id,
      // so running it after updateEntity would replay the stale YAML over the refresh. updateEntity
      // re-reads the entity and carries its decorator forward, so the linkage written here survives.
      // Stamp REGISTER, not MERGE: CatalogInfo is register-only (explicit MERGE is rejected above), and
      // the Imported Entities API surfaces this field as action_performed. A Redis refresh is not a merge.
      decorateIDPCatalogWithIntegrationMetadata(mappedEntity, linkagePath, entity,
          SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER.name(),
          TypesIntegrationConfig.EnumIntegrationType.CatalogInfo, spacePath, integrationConfig.getIdentifier());
      EntityResponse existingResponse = catalogService.getEntity(accountIdentifier, mappedEntity.getOrgIdentifier(),
          mappedEntity.getProjectIdentifier(), CatalogUtils.entityRef(mappedEntity), false, false, true);
      EntityUpdateRequest updateRequest = entityUpdateRequest(accountIdentifier, existingResponse, yamlToUpdate);
      try {
        catalogService.updateEntity(accountIdentifier, mappedEntity.getOrgIdentifier(),
            mappedEntity.getProjectIdentifier(), CatalogUtils.entityRef(mappedEntity), updateRequest, false, true,
            false, false);
      } finally {
        GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
      }
      return false;
    }

    CatalogEntity existing =
        catalogServiceHelper.catalogEntity(accountIdentifier, orgIdentifier, projectIdentifier, kind, identifier);
    if (existing != null) {
      log.info("Skipping CatalogInfo entity {} - already exists as {}/{} ({}); register-only", entity.getUuid(), kind,
          identifier, (existing instanceof GitReferencedCatalogEntity ? "git-referenced" : "inline"));
      return false;
    }

    GitCreateDetails gitDetails = buildGitCreateDetails(
        integrationConfig, kind, identifier, sourceRepo, sourceBranch, orgIdentifier, projectIdentifier);

    String yamlToCreate = yamlOverridden ? YamlUtils.writeObjectAsYaml(harnessYamlMap) : harnessYaml;
    EntityResponse createdResponse = catalogService.createEntity(accountIdentifier, orgIdentifier, projectIdentifier,
        false, false, new EntityCreateRequest().yaml(yamlToCreate));
    String createdIdentifier = createdResponse.getIdentifier();

    if (gitDetails != null) {
      attemptGitSync(accountIdentifier, orgIdentifier, projectIdentifier, kind, createdIdentifier, gitDetails);
    }

    CatalogEntity saved = catalogServiceHelper.catalogEntity(
        accountIdentifier, orgIdentifier, projectIdentifier, kind, createdIdentifier);
    if (saved == null) {
      log.warn("CatalogInfo entity {} - created but not found on re-fetch (kind={}, identifier={}); skipping decorate",
          entity.getUuid(), kind, createdIdentifier);
      return false;
    }
    decorateIDPCatalogWithIntegrationMetadata(saved, linkagePath, entity,
        SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER.name(),
        TypesIntegrationConfig.EnumIntegrationType.CatalogInfo, spacePath, integrationConfig.getIdentifier());
    return true;
  }

  /**
   * Applies the user's rename to the converted Harness YAML map, in place. Only the UI-supplied
   * action_destination (name) and action_identifier are honoured, and each is applied independently:
   * <ul>
   *   <li>Name: action_destination when present</li>
   *   <li>Identifier: action_identifier when present (as-is); otherwise sanitize(action_destination)</li>
   * </ul>
   * When the UI supplied neither, the converted values are left untouched. That matters because
   * {@link IDPToHarnessHelper#convertBackstageToHarness} maps hyphens to underscores in both the
   * identifier and every relation ref, so re-deriving the identifier here (which drops hyphens) would
   * create {@code myservice} while sibling refs still point at {@code my_service}, silently dropping
   * relations on every auto-discovered entity. A name that sanitizes to nothing (all digits, hyphens
   * or {@code $}) still applies as the display name; only the identifier falls back to the converted
   * value. Returns true when the map was modified.
   * <p>
   * Invalid identifiers (e.g. leading digit after sanitization) are not special-cased here — same as
   * other integrations, {@code createEntity} rejects them and the batch loop logs and skips.
   */
  private boolean applyCatalogInfoRegisterNameAndIdentifier(
      Map<String, Object> harnessYamlMap, String actionDestination, String actionIdentifier) {
    boolean hasActionDestination = !StringUtils.isBlank(actionDestination);
    boolean hasActionIdentifier = !StringUtils.isBlank(actionIdentifier);
    if (!hasActionDestination && !hasActionIdentifier) {
      return false;
    }

    if (hasActionDestination) {
      harnessYamlMap.put("name", actionDestination);
    }

    String effectiveIdentifier =
        hasActionIdentifier ? actionIdentifier.trim() : sanitizeRegisterIdentifier(actionDestination);
    if (StringUtils.isBlank(effectiveIdentifier)) {
      log.warn("CatalogInfo register name '{}' produced no usable identifier; keeping the converted identifier",
          actionDestination);
      return hasActionDestination;
    }

    harnessYamlMap.put("identifier", effectiveIdentifier);
    return true;
  }

  /**
   * Same identifier sanitizer as {@link #registerNewEntity}: strip leading digits/hyphens/{@code $},
   * drop disallowed characters, turn whitespace into underscores. Does not re-validate the result —
   * invalid identifiers fail later in createEntity, matching other integrations.
   */
  static String sanitizeRegisterIdentifier(String name) {
    if (StringUtils.isBlank(name)) {
      return "";
    }
    return name.trim().replaceAll("^[0-9\\-$]*", "").replaceAll("[^0-9a-zA-Z_$ ]", "").replaceAll("\\s+", "_");
  }

  private boolean isGitSyncEnabled(TypesIntegrationConfig integrationConfig) {
    Map<String, Object> config = integrationConfig.getConfiguration();
    return config != null && Boolean.TRUE.equals(config.get(CATALOG_INFO_CONFIG_GIT_SYNC_ENABLED));
  }

  /**
   * Builds a scope-aware destination path under the configured sync_base_path, mirroring the official
   * bulk Move-to-Git migration script's scheme so entities migrated either way share one layout:
   *   account : {base}/{kind}/{identifier}.yaml
   *   org     : {base}/{kind}/orgs/{org}/{identifier}.yaml
   *   project : {base}/{kind}/orgs/{org}/projects/{project}/{identifier}.yaml
   * The platform writes this path verbatim and only enforces a .yaml/.yml extension.
   */
  private String buildSyncFilePath(
      String syncBasePath, String kind, String orgIdentifier, String projectIdentifier, String identifier) {
    String base = syncBasePath == null ? "" : StringUtils.strip(syncBasePath, "/");
    StringBuilder path = new StringBuilder();
    if (!base.isEmpty()) {
      path.append(base).append('/');
    }
    path.append(kind);
    if (isNotEmpty(orgIdentifier)) {
      path.append("/orgs/").append(orgIdentifier);
      if (isNotEmpty(projectIdentifier)) {
        path.append("/projects/").append(projectIdentifier);
      }
    }
    path.append('/').append(identifier).append(".yaml");
    return path.toString();
  }

  private GitCreateDetails buildGitCreateDetails(TypesIntegrationConfig integrationConfig, String kind,
      String identifier, String sourceRepo, String sourceBranch, String orgIdentifier, String projectIdentifier) {
    if (!isGitSyncEnabled(integrationConfig)) {
      return null;
    }
    Map<String, Object> config = integrationConfig.getConfiguration();
    if (config == null) {
      log.warn(
          "CatalogInfo git sync enabled but integration configuration is null; entity {} stays inline", identifier);
      return null;
    }
    String connectorRef = (String) config.get(CATALOG_INFO_CONFIG_GIT_SYNC_CONNECTOR_REF);
    String configuredBranch = config.get(CATALOG_INFO_CONFIG_SYNC_BRANCH) != null
        ? (String) config.get(CATALOG_INFO_CONFIG_SYNC_BRANCH)
        : CATALOG_INFO_DEFAULT_SYNC_BRANCH;
    String syncBasePath = config.get(CATALOG_INFO_CONFIG_SYNC_BASE_PATH) != null
        ? (String) config.get(CATALOG_INFO_CONFIG_SYNC_BASE_PATH)
        : CATALOG_INFO_DEFAULT_SYNC_BASE_PATH;
    boolean syncToSourceRepo = Boolean.TRUE.equals(config.get(CATALOG_INFO_CONFIG_SYNC_TO_SOURCE_REPO));
    // Airbyte emits owner/repo (e.g. my-org/service-a); GitX connectors expect the bare repo path.
    String syncRepo = syncToSourceRepo ? bareRepoName(sourceRepo) : (String) config.get(CATALOG_INFO_CONFIG_SYNC_REPO);
    // sync_branch belongs to sync_repo — never borrow it for the source repo. A missing source branch
    // keeps the entity inline rather than committing to an unrelated branch name.
    String syncBranch = syncToSourceRepo ? sourceBranch : configuredBranch;
    if (isEmpty(connectorRef) || isEmpty(syncRepo) || isEmpty(syncBranch)) {
      log.warn(
          "CatalogInfo git sync missing connector/repo/branch (sync_to_source_repo={}, sourceRepo={}, sourceBranch={}, "
              + "resolvedRepo={}, resolvedBranch={}); entity {} stays inline",
          syncToSourceRepo, sourceRepo, sourceBranch, syncRepo, syncBranch, identifier);
      return null;
    }
    String targetPath = buildSyncFilePath(syncBasePath, kind, orgIdentifier, projectIdentifier, identifier);
    return new GitCreateDetails()
        .connectorRef(connectorRef)
        .repoName(syncRepo)
        .branchName(syncBranch)
        .filePath(targetPath)
        .storeType(GitCreateDetails.StoreTypeEnum.REMOTE)
        .commitMessage(CATALOG_INFO_GIT_SYNC_COMMIT_MESSAGE);
  }

  /**
   * Strips the owner/org prefix from a repository full name so GitX connectors receive the repo path
   * they expect. Only the first segment is removed, since GitLab subgroups are part of the repo path
   * the connector needs: {@code my-org/service-a} → {@code service-a},
   * {@code group/subgroup/project} → {@code subgroup/project}. Already-bare names are unchanged.
   */
  static String bareRepoName(String repo) {
    if (isEmpty(repo)) {
      return repo;
    }
    String trimmed = repo.trim();
    int slash = trimmed.indexOf('/');
    return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
  }

  private void attemptGitSync(String accountIdentifier, String orgIdentifier, String projectIdentifier, String kind,
      String identifier, GitCreateDetails gitDetails) {
    try {
      GitMoveDetails gitMoveDetails = new GitMoveDetails()
                                          .branchName(gitDetails.getBranchName())
                                          .filePath(gitDetails.getFilePath())
                                          .commitMessage(gitDetails.getCommitMessage())
                                          .connectorRef(gitDetails.getConnectorRef())
                                          .repoName(gitDetails.getRepoName())
                                          .isHarnessCodeRepo(gitDetails.isIsHarnessCodeRepo());

      EntityMoveRequest moveRequest = new EntityMoveRequest()
                                          .gitDetails(gitMoveDetails)
                                          .entityMoveOperationType(EntityMoveOperationType.INLINE_TO_REMOTE);

      String entityRef = CatalogUtils.entityRef(kind, orgIdentifier, projectIdentifier, identifier);
      catalogService.moveEntity(accountIdentifier, orgIdentifier, projectIdentifier, entityRef, moveRequest);
      log.info("CatalogInfo git-sync: successfully moved {}/{} to remote (repo={}, branch={}, path={})", kind,
          identifier, gitDetails.getRepoName(), gitDetails.getBranchName(), gitDetails.getFilePath());
    } catch (Exception e) {
      log.warn("CatalogInfo git-sync failed for {}/{}; entity remains inline. Error: {}", kind, identifier,
          e.getMessage(), e);
    } finally {
      GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
    }
  }

  private void handleFileContentUpload(
      EntityMappedEntityResponse entity, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Map<String, Object> data = entity.getData();
    String content = (String) data.get("content");
    String sourceAssetId = (String) data.get("source_asset_id");
    String filePath = (String) data.get("file_path");

    if (isEmpty(content) || isEmpty(sourceAssetId) || isEmpty(filePath)) {
      log.warn("Skipping file_content upload: missing required fields (source_asset_id={}, file_path={})",
          sourceAssetId, filePath);
      return;
    }

    // Look up the corresponding ai_asset catalog entity by source_asset_id in its decorator.
    // If the catalog entity exists, upload using its uniqueId. If not, skip —
    // the content will be fetched when the ai_asset entity is processed.
    Optional<CatalogEntity> catalogEntityOpt = catalogEntityRepository.findByAccountIdentifierAndScopeAndGitHubAssetId(
        accountIdentifier, orgIdentifier, projectIdentifier, sourceAssetId);
    if (catalogEntityOpt.isEmpty()) {
      log.info("No catalog entity found for source_asset_id={}, skipping file_content upload (will be fetched on "
              + "ai_asset processing)",
          sourceAssetId);
      return;
    }

    uploadFileContentToGcs(catalogEntityOpt.get(), content, filePath, accountIdentifier);
  }

  /**
   * After creating/updating an ai_asset catalog entity, fetch its file content from IM
   * and upload to GCS using the catalog entity's uniqueId.
   */
  private void fetchAndUploadFileContent(CatalogEntity catalogEntity, String assetId, String fileContentKind,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String integrationId) {
    try {
      OpenapiGetMappedEntitiesRequest request = new OpenapiGetMappedEntitiesRequest();
      request.setKinds(List.of(fileContentKind));
      request.setFieldValsPerKind(Map.of(fileContentKind,
          List.of(new OpenapiGetMappedEntitiesRequest.FieldValFilter("source_asset_id", List.of(assetId)))));

      Response<EntityMappedEntityResponseObject> response = executeGeneralRequestWithRetry(
          integrationManagerClientHelper.getMappedEntities(accountIdentifier, accountIdentifier, orgIdentifier,
              projectIdentifier, integrationId, integrationManagerClientHelper.getIntegrationManagerIdpMappingId(),
              true, "name", "asc", 0, 100, null, request, false));

      if (!response.isSuccessful() || response.body() == null || isEmpty(response.body().getItems())) {
        log.debug("No file_content entities found in IM for asset_id={}", assetId);
        return;
      }

      for (EntityMappedEntityResponse fileEntity : response.body().getItems()) {
        Map<String, Object> fileData = fileEntity.getData();
        String content = (String) fileData.get("content");
        String filePath = (String) fileData.get("file_path");
        if (isEmpty(content) || isEmpty(filePath)) {
          continue;
        }
        uploadFileContentToGcs(catalogEntity, content, filePath, accountIdentifier);
      }
    } catch (Exception e) {
      log.error("Failed to fetch/upload file content for asset_id={}: {}", assetId, e.getMessage(), e);
    }
  }

  private void uploadFileContentToGcs(
      CatalogEntity catalogEntity, String content, String filePath, String accountIdentifier) {
    String gcsPath =
        catalogContentConfig.getGcsBasePath(accountIdentifier, catalogEntity.getKind(), catalogEntity.getUniqueId());
    try {
      byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
      byte[] toUpload = idpContentEncryptionService.encrypt(contentBytes, accountIdentifier);
      gcpStorageUtil.uploadFileToGcs(
          catalogContentConfig.getBucketName(), gcsPath, filePath, new ByteArrayInputStream(toUpload));
      String label = contentFileLabel(catalogEntity.getType(), filePath);
      catalogEntityRepository.addContentFile(accountIdentifier, catalogEntity.getUniqueId(), filePath, label);
      log.info("Uploaded file content to GCS: {}/{}", gcsPath, filePath);
    } catch (Exception e) {
      log.error("Failed to upload file content to GCS: {}/{}: {}", gcsPath, filePath, e.getMessage(), e);
    }
  }

  private static String contentFileLabel(String entityType, String filePath) {
    if ("skill".equals(entityType) || "agent".equals(entityType)) {
      return "Instructions";
    }
    String name = filePath.contains("/") ? filePath.substring(filePath.lastIndexOf('/') + 1) : filePath;
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  /**
   * Default entity processing — original logic, handles both register and merge.
   * Used when configAction is "Merge_Register" or null (backward compatible).
   */
  SaveEntityResult processEntityDefault(SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest,
      EntityMappedEntityResponse entityMappedEntityResponse, String integrationEntityUuid, CatalogEntity mappedEntity,
      CatalogEntity correlatedEntity, String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String kind, String type, String identifier, String catalogIntegrationEntityLinkageConfigPath,
      Map<String, Object> catalogIntegrationEntityAdditionalLinkageConfigPlaceholder,
      TypesIntegrationConfig integrationConfig, List<ScopeInfo> scopeInfos) {
    CatalogEntity processedEntity = null;
    String linkageIdentifier = identifier;
    String spacePath = integrationConfig.getSpacePath() != null
        ? normalizeSpacePath(integrationConfig.getSpacePath(), accountIdentifier)
        : null;
    String integrationConfigId = integrationConfig.getIdentifier();

    if (SaveDiscoverEntitiesRequest.SelectionFilterEnum.ALL.equals(saveDiscoverEntitiesRequest.getSelectionFilter())) {
      if (mappedEntity != null) {
        mergeEntity(mappedEntity, entityMappedEntityResponse, catalogIntegrationEntityLinkageConfigPath,
            catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig.getIntegrationType(),
            spacePath, integrationConfigId);
        processedEntity = mappedEntity;
      } else if (correlatedEntity != null) {
        mergeEntity(correlatedEntity, entityMappedEntityResponse, catalogIntegrationEntityLinkageConfigPath,
            catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig.getIntegrationType(),
            spacePath, integrationConfigId);
        processedEntity = correlatedEntity;
      } else {
        processedEntity = registerNewEntity(entityMappedEntityResponse, accountIdentifier, orgIdentifier,
            projectIdentifier, kind, type, catalogIntegrationEntityLinkageConfigPath,
            catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, null, null,
            integrationConfig.getIntegrationType(), spacePath, integrationConfigId);
      }
    } else if (SaveDiscoverEntitiesRequest.SelectionFilterEnum.REGISTER.equals(
                   saveDiscoverEntitiesRequest.getSelectionFilter())) {
      if (mappedEntity != null) {
        throw new InvalidRequestException(
            String.format("Cannot register: integration entity %s is already linked to catalog entity %s",
                integrationEntityUuid, CatalogUtils.entityRef(mappedEntity)));
      }
      processedEntity =
          registerNewEntity(entityMappedEntityResponse, accountIdentifier, orgIdentifier, projectIdentifier, kind, type,
              catalogIntegrationEntityLinkageConfigPath, catalogIntegrationEntityAdditionalLinkageConfigPlaceholder,
              null, null, integrationConfig.getIntegrationType(), spacePath, integrationConfigId);
    } else if (SaveDiscoverEntitiesRequest.SelectionFilterEnum.MERGE_RECOMMENDED.equals(
                   saveDiscoverEntitiesRequest.getSelectionFilter())) {
      if (mappedEntity != null) {
        mergeEntity(mappedEntity, entityMappedEntityResponse, catalogIntegrationEntityLinkageConfigPath,
            catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig.getIntegrationType(),
            spacePath, integrationConfigId);
        processedEntity = mappedEntity;
      } else if (correlatedEntity != null) {
        mergeEntity(correlatedEntity, entityMappedEntityResponse, catalogIntegrationEntityLinkageConfigPath,
            catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig.getIntegrationType(),
            spacePath, integrationConfigId);
        processedEntity = correlatedEntity;
      } else {
        throw new InvalidRequestException(
            String.format("Cannot merge: no target entity found for integration entity %s. "
                    + "Integration config restricts action to Merge but entity is neither linked nor correlated",
                integrationEntityUuid));
      }
    } else {
      SaveDiscoverEntitiesRequestIntegrationEntities saveDiscoverEntitiesRequestIntegrationEntities =
          saveDiscoverEntitiesRequest.getIntegrationEntities()
              .stream()
              .filter(e -> entityMappedEntityResponse.getUuid().equals(e.getIntegrationEntityId()))
              .findFirst()
              .orElseThrow(()
                               -> new IllegalArgumentException(
                                   "IntegrationEntity not found: " + entityMappedEntityResponse.getUuid()));

      SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum action =
          saveDiscoverEntitiesRequestIntegrationEntities.getAction();
      String destination = saveDiscoverEntitiesRequestIntegrationEntities.getActionDestination();
      String actionIdentifier = saveDiscoverEntitiesRequestIntegrationEntities.getActionIdentifier();
      if (action == null) {
        if (mappedEntity != null) {
          mergeEntity(mappedEntity, entityMappedEntityResponse, catalogIntegrationEntityLinkageConfigPath,
              catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig.getIntegrationType(),
              spacePath, integrationConfigId);
          linkageIdentifier = mappedEntity.getIdentifier();
          processedEntity = mappedEntity;
        } else if (correlatedEntity != null) {
          mergeEntity(correlatedEntity, entityMappedEntityResponse, catalogIntegrationEntityLinkageConfigPath,
              catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig.getIntegrationType(),
              spacePath, integrationConfigId);
          linkageIdentifier = correlatedEntity.getIdentifier();
          processedEntity = correlatedEntity;
        } else {
          CatalogEntity createdEntity = registerNewEntity(entityMappedEntityResponse, accountIdentifier, orgIdentifier,
              projectIdentifier, kind, type, catalogIntegrationEntityLinkageConfigPath,
              catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, null, null,
              integrationConfig.getIntegrationType(), spacePath, integrationConfigId);
          linkageIdentifier = createdEntity.getIdentifier();
          processedEntity = createdEntity;
        }
      } else if (action.equals(SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER)) {
        if (mappedEntity != null) {
          throw new InvalidRequestException(
              String.format("Cannot register: integration entity %s is already linked to catalog entity %s",
                  integrationEntityUuid, CatalogUtils.entityRef(mappedEntity)));
        }
        try {
          CatalogEntity createdEntity = registerNewEntity(entityMappedEntityResponse, accountIdentifier, orgIdentifier,
              projectIdentifier, kind, type, catalogIntegrationEntityLinkageConfigPath,
              catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, destination, actionIdentifier,
              integrationConfig.getIntegrationType(), spacePath, integrationConfigId);
          linkageIdentifier = createdEntity.getIdentifier();
          processedEntity = createdEntity;
        } catch (InvalidRequestException e) {
          if (e.getMessage() != null && e.getMessage().contains("already exists")) {
            log.warn("Entity already exists while registering for integration entity {}, finding existing entity "
                    + "to merge",
                integrationEntityUuid, e);
            String lookupIdentifier = !Strings.isBlank(destination) ? destination.trim()
                                                                          .replaceAll("^[0-9\\-$]*", "")
                                                                          .replaceAll("[^0-9a-zA-Z_$ ]", "")
                                                                          .replaceAll("\\s+", "_")
                                                                    : identifier;
            CatalogEntity existingEntity = findCorrelatedEntity(scopeInfos, kind, lookupIdentifier);
            if (existingEntity == null) {
              throw new UnexpectedException(
                  String.format("Failed to register entity for integration entity %s due to duplicate key, and "
                          + "could not find existing entity to merge with",
                      integrationEntityUuid),
                  e);
            }
            mergeEntity(existingEntity, entityMappedEntityResponse, catalogIntegrationEntityLinkageConfigPath,
                catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig.getIntegrationType(),
                spacePath, integrationConfigId);
            linkageIdentifier = existingEntity.getIdentifier();
            processedEntity = existingEntity;
          } else {
            throw e;
          }
        }
      } else if (action.equals(SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.MERGE)) {
        CatalogEntity targetEntity = null;
        if (Strings.isBlank(destination)) {
          if (mappedEntity != null) {
            targetEntity = mappedEntity;
          } else if (correlatedEntity != null) {
            targetEntity = correlatedEntity;
          } else {
            throw new InvalidRequestException(String.format("Cannot merge: no valid destination found for entity %s"
                    + " and neither is it linked or correlated",
                integrationEntityUuid));
          }
        } else {
          String[] destinationParts = destination.split("[:/]");
          if (destinationParts.length != 3) {
            throw new InvalidRequestException(
                String.format("Cannot merge: invalid destination %s for uuid %s", destination, integrationEntityUuid));
          }
          String destinationKind = destinationParts[0];
          String destinationScope = destinationParts[1];
          String destinationIdentifier = destinationParts[2];
          String[] scopeParts = destinationScope.split("\\.");
          String destinationOrgIdentifier = null;
          String destinationProjectIdentifier = null;
          if (scopeParts.length > 1) {
            destinationOrgIdentifier = scopeParts[1];
          }
          if (scopeParts.length > 2) {
            destinationProjectIdentifier = scopeParts[2];
          }

          if (mappedEntity != null) {
            String mappedEntityRef = CatalogUtils.entityRef(mappedEntity);
            if (!mappedEntityRef.equals(destination)) {
              throw new InvalidRequestException(
                  String.format("Cannot merge: integration entity %s is already linked to catalog entity %s, "
                          + "cannot link to different entity %s",
                      integrationEntityUuid, mappedEntityRef, destination));
            }
            targetEntity = mappedEntity;
          } else {
            targetEntity = catalogServiceHelper.catalogEntity(accountIdentifier, destinationOrgIdentifier,
                destinationProjectIdentifier, destinationKind, destinationIdentifier);
            if (targetEntity == null) {
              log.error("Catalog entity not found for destination: {}", destination);
              throw new UnexpectedException("Catalog entity not found for destination: " + destination);
            }
          }
        }
        mergeEntity(targetEntity, entityMappedEntityResponse, catalogIntegrationEntityLinkageConfigPath,
            catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig.getIntegrationType(),
            spacePath, integrationConfigId);
        linkageIdentifier = targetEntity.getIdentifier();
        processedEntity = targetEntity;
      }
    }
    return new SaveEntityResult(processedEntity, linkageIdentifier);
  }

  /**
   * Register-only entity processing — used when integration config restricts action to "Register" for this kind.
   * all=true:  MAPPED → throw, CORRELATED → register new, UNMAPPED → register new
   * all=false: action=REGISTER or null → register, action=MERGE → throw
   */
  SaveEntityResult processEntityRegisterOnly(SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest,
      EntityMappedEntityResponse entityMappedEntityResponse, String integrationEntityUuid, CatalogEntity mappedEntity,
      CatalogEntity correlatedEntity, String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String kind, String type, String identifier, String catalogIntegrationEntityLinkageConfigPath,
      Map<String, Object> catalogIntegrationEntityAdditionalLinkageConfigPlaceholder,
      TypesIntegrationConfig integrationConfig, List<ScopeInfo> scopeInfos) {
    CatalogEntity processedEntity = null;
    String linkageIdentifier = identifier;
    String spacePath = integrationConfig.getSpacePath() != null
        ? normalizeSpacePath(integrationConfig.getSpacePath(), accountIdentifier)
        : null;
    String integrationConfigId = integrationConfig.getIdentifier();

    if (SaveDiscoverEntitiesRequest.SelectionFilterEnum.ALL.equals(saveDiscoverEntitiesRequest.getSelectionFilter())) {
      if (mappedEntity != null) {
        throw new InvalidRequestException(
            String.format("Cannot register: integration entity %s is already linked to catalog entity %s",
                integrationEntityUuid, CatalogUtils.entityRef(mappedEntity)));
      }
      processedEntity =
          registerNewEntity(entityMappedEntityResponse, accountIdentifier, orgIdentifier, projectIdentifier, kind, type,
              catalogIntegrationEntityLinkageConfigPath, catalogIntegrationEntityAdditionalLinkageConfigPlaceholder,
              null, null, integrationConfig.getIntegrationType(), spacePath, integrationConfigId);
    } else if (SaveDiscoverEntitiesRequest.SelectionFilterEnum.MANUAL.equals(
                   saveDiscoverEntitiesRequest.getSelectionFilter())) {
      SaveDiscoverEntitiesRequestIntegrationEntities saveDiscoverEntitiesRequestIntegrationEntities =
          saveDiscoverEntitiesRequest.getIntegrationEntities()
              .stream()
              .filter(e -> entityMappedEntityResponse.getUuid().equals(e.getIntegrationEntityId()))
              .findFirst()
              .orElseThrow(()
                               -> new IllegalArgumentException(
                                   "IntegrationEntity not found: " + entityMappedEntityResponse.getUuid()));

      SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum action =
          saveDiscoverEntitiesRequestIntegrationEntities.getAction();
      String destination = saveDiscoverEntitiesRequestIntegrationEntities.getActionDestination();
      String actionIdentifier = saveDiscoverEntitiesRequestIntegrationEntities.getActionIdentifier();

      if (action != null && action.equals(SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.MERGE)) {
        throw new InvalidRequestException(String.format(
            "Action MERGE is not allowed for this integration kind. Integration config restricts action to Register"));
      }

      if (mappedEntity != null) {
        throw new InvalidRequestException(
            String.format("Cannot register: integration entity %s is already linked to catalog entity %s",
                integrationEntityUuid, CatalogUtils.entityRef(mappedEntity)));
      }
      CatalogEntity createdEntity =
          registerNewEntity(entityMappedEntityResponse, accountIdentifier, orgIdentifier, projectIdentifier, kind, type,
              catalogIntegrationEntityLinkageConfigPath, catalogIntegrationEntityAdditionalLinkageConfigPlaceholder,
              destination, actionIdentifier, integrationConfig.getIntegrationType(), spacePath, integrationConfigId);
      linkageIdentifier = createdEntity.getIdentifier();
      processedEntity = createdEntity;
    } else {
      throw new InvalidRequestException(String.format("Unsupported selection filter %s for REGISTER only action",
          saveDiscoverEntitiesRequest.getSelectionFilter()));
    }
    return new SaveEntityResult(processedEntity, linkageIdentifier);
  }

  /**
   * Merge-only entity processing — used when integration config restricts action to "Merge" for this kind.
   * all=true:  MAPPED → merge, CORRELATED → merge, UNMAPPED → throw
   * all=false: action=MERGE or null → merge, action=REGISTER → throw
   */
  SaveEntityResult processEntityMergeOnly(SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest,
      EntityMappedEntityResponse entityMappedEntityResponse, String integrationEntityUuid, CatalogEntity mappedEntity,
      CatalogEntity correlatedEntity, String accountIdentifier, String catalogIntegrationEntityLinkageConfigPath,
      Map<String, Object> catalogIntegrationEntityAdditionalLinkageConfigPlaceholder,
      TypesIntegrationConfig integrationConfig, List<ScopeInfo> scopeInfos, String identifier) {
    CatalogEntity processedEntity = null;
    String linkageIdentifier = identifier;
    String spacePath = integrationConfig.getSpacePath() != null
        ? normalizeSpacePath(integrationConfig.getSpacePath(), accountIdentifier)
        : null;
    String integrationConfigId = integrationConfig.getIdentifier();

    if (SaveDiscoverEntitiesRequest.SelectionFilterEnum.MERGE_RECOMMENDED.equals(
            saveDiscoverEntitiesRequest.getSelectionFilter())) {
      if (mappedEntity != null) {
        mergeEntity(mappedEntity, entityMappedEntityResponse, catalogIntegrationEntityLinkageConfigPath,
            catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig.getIntegrationType(),
            spacePath, integrationConfigId);
        processedEntity = mappedEntity;
      } else if (correlatedEntity != null) {
        mergeEntity(correlatedEntity, entityMappedEntityResponse, catalogIntegrationEntityLinkageConfigPath,
            catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig.getIntegrationType(),
            spacePath, integrationConfigId);
        processedEntity = correlatedEntity;
      } else {
        throw new InvalidRequestException(
            String.format("Cannot merge: no target entity found for integration entity %s. "
                    + "Integration config restricts action to Merge but entity is neither linked nor correlated",
                integrationEntityUuid));
      }
    } else if (SaveDiscoverEntitiesRequest.SelectionFilterEnum.MANUAL.equals(
                   saveDiscoverEntitiesRequest.getSelectionFilter())) {
      SaveDiscoverEntitiesRequestIntegrationEntities saveDiscoverEntitiesRequestIntegrationEntities =
          saveDiscoverEntitiesRequest.getIntegrationEntities()
              .stream()
              .filter(e -> entityMappedEntityResponse.getUuid().equals(e.getIntegrationEntityId()))
              .findFirst()
              .orElseThrow(()
                               -> new IllegalArgumentException(
                                   "IntegrationEntity not found: " + entityMappedEntityResponse.getUuid()));

      SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum action =
          saveDiscoverEntitiesRequestIntegrationEntities.getAction();
      String destination = saveDiscoverEntitiesRequestIntegrationEntities.getActionDestination();

      if (action != null && action.equals(SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER)) {
        throw new InvalidRequestException(String.format(
            "Action REGISTER is not allowed for this integration kind. Integration config restricts action to Merge"));
      }

      CatalogEntity targetEntity = null;
      if (Strings.isBlank(destination)) {
        if (mappedEntity != null) {
          targetEntity = mappedEntity;
        } else if (correlatedEntity != null) {
          targetEntity = correlatedEntity;
        } else {
          throw new InvalidRequestException(
              String.format("Cannot merge: no target entity found for integration entity %s. "
                      + "Integration config restricts action to Merge but entity is neither linked nor correlated",
                  integrationEntityUuid));
        }
      } else {
        String[] destinationParts = destination.split("[:/]");
        if (destinationParts.length != 3) {
          throw new InvalidRequestException(
              String.format("Cannot merge: invalid destination %s for uuid %s", destination, integrationEntityUuid));
        }
        String destinationKind = destinationParts[0];
        String destinationScope = destinationParts[1];
        String destinationIdentifier = destinationParts[2];
        String[] scopeParts = destinationScope.split("\\.");
        String destinationOrgIdentifier = null;
        String destinationProjectIdentifier = null;
        if (scopeParts.length > 1) {
          destinationOrgIdentifier = scopeParts[1];
        }
        if (scopeParts.length > 2) {
          destinationProjectIdentifier = scopeParts[2];
        }

        if (mappedEntity != null) {
          String mappedEntityRef = CatalogUtils.entityRef(mappedEntity);
          if (!mappedEntityRef.equals(destination)) {
            throw new InvalidRequestException(
                String.format("Cannot merge: integration entity %s is already linked to catalog entity %s, "
                        + "cannot link to different entity %s",
                    integrationEntityUuid, mappedEntityRef, destination));
          }
          targetEntity = mappedEntity;
        } else {
          targetEntity = catalogServiceHelper.catalogEntity(accountIdentifier, destinationOrgIdentifier,
              destinationProjectIdentifier, destinationKind, destinationIdentifier);
          if (targetEntity == null) {
            log.error("Catalog entity not found for destination: {}", destination);
            throw new UnexpectedException("Catalog entity not found for destination: " + destination);
          }
        }
      }
      mergeEntity(targetEntity, entityMappedEntityResponse, catalogIntegrationEntityLinkageConfigPath,
          catalogIntegrationEntityAdditionalLinkageConfigPlaceholder, integrationConfig.getIntegrationType(), spacePath,
          integrationConfigId);
      linkageIdentifier = targetEntity.getIdentifier();
      processedEntity = targetEntity;
    } else {
      throw new InvalidRequestException(String.format(
          "Unsupported selection filter %s for MERGE only action", saveDiscoverEntitiesRequest.getSelectionFilter()));
    }
    return new SaveEntityResult(processedEntity, linkageIdentifier);
  }

  private CatalogEntity findCatalogEntity(String filterPath, List<ScopeInfo> scopeInfos, String kind) {
    log.debug("Using Filter path to get catalog entity: {}", filterPath);
    List<String> kinds = (kind == null || kind.isEmpty()) ? Collections.emptyList() : List.of(kind);
    List<CatalogEntity> catalogEntities = catalogEntityRepository.getEntitiesFilters(
        scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList(), kinds, filterPath);
    log.debug("Found {} catalog entities for filter path: {} (kind filter: {})", catalogEntities.size(), filterPath,
        kind == null ? "ALL" : kind);
    CatalogEntity catalogEntity = !catalogEntities.isEmpty() ? catalogEntities.get(0) : null;
    if (catalogEntity == null) {
      // Expected on first-time register: no linkage yet. Do not log ERROR — bulk discover would
      // otherwise emit one false-positive error per successfully registered entity.
      log.debug("Catalog entity not found for filter path: {}", filterPath);
    }
    return catalogEntity;
  }

  private CatalogEntity findCorrelatedEntityByContains(
      List<ScopeInfo> scopeInfos, String kind, String destPath, String correlationValue) {
    String fieldExistsFilter = String.format("decorator._processed_data.%s&%s", destPath, destPath);
    log.info("Fetching catalog entities with field exists filter: {}", fieldExistsFilter);
    List<String> kinds = (kind == null || kind.isEmpty()) ? Collections.emptyList() : List.of(kind);
    List<CatalogEntity> candidateEntities = catalogEntityRepository.getEntitiesFilters(
        scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList(), kinds, fieldExistsFilter);
    log.info("Found {} candidate entities for contains matching on field: {}", candidateEntities.size(), destPath);
    for (CatalogEntity entity : candidateEntities) {
      Map<String, Object> entityMap = entity.getDecoratedEntityMap();
      String fieldValue = CommonUtils.from(entityMap, destPath, String.class);
      if (fieldValue != null && StringUtils.containsIgnoreCase(correlationValue, fieldValue)) {
        return entity;
      }
    }
    log.error("No catalog entity found with contains match for field: {} value: {}", destPath, correlationValue);
    return null;
  }

  private CatalogEntity findMappedEntity(String integrationEntityUuid, String linkagePath, List<ScopeInfo> scopeInfos,
      String kind, TypesIntegrationConfig.EnumIntegrationType integrationType,
      EntityMappedEntityResponse entityMappedEntityResponse) {
    String filter = String.format("decorator._processed_data.%s.entity_uuid=%s", linkagePath, integrationEntityUuid);
    CatalogEntity entity = findCatalogEntity(filter, scopeInfos, kind);
    if (entity != null) {
      return entity;
    }

    if (integrationType == TypesIntegrationConfig.EnumIntegrationType.HarnessK8s) {
      if (entityMappedEntityResponse != null) {
        Set<String> namespaces = extractNamespacesFromEntityData(entityMappedEntityResponse);
        for (String namespace : namespaces) {
          String nsFilter = String.format(
              "decorator._processed_data.%s.%s.entity_uuid=%s", linkagePath, namespace, integrationEntityUuid);
          CatalogEntity nsEntity = findCatalogEntity(nsFilter, scopeInfos, kind);
          if (nsEntity != null) {
            return nsEntity;
          }
        }
      } else {
        CatalogEntity nsEntity = findK8sMappedEntityByBroadSearch(integrationEntityUuid, linkagePath, scopeInfos, kind);
        return nsEntity;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private CatalogEntity findK8sMappedEntityByBroadSearch(
      String integrationEntityUuid, String linkagePath, List<ScopeInfo> scopeInfos, String kind) {
    String existsFilter = String.format("decorator._processed_data.%s", linkagePath);
    log.info("K8s broad search for entity_uuid={} under path: {}", integrationEntityUuid, linkagePath);
    List<String> kinds = (kind == null || kind.isEmpty()) ? Collections.emptyList() : List.of(kind);
    List<CatalogEntity> candidates = catalogEntityRepository.getEntitiesFilters(
        scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList(), kinds, existsFilter);
    for (CatalogEntity candidate : candidates) {
      Map<String, Object> processedData = candidate.getFailSafeProcessedData();
      Object linkageNode = CommonUtils.from(processedData, linkagePath, Object.class);
      if (!(linkageNode instanceof Map)) {
        continue;
      }
      for (Object value : ((Map<String, Object>) linkageNode).values()) {
        if (value instanceof Map && integrationEntityUuid.equals(((Map<String, Object>) value).get("entity_uuid"))) {
          return candidate;
        }
      }
    }
    return null;
  }

  private Set<String> extractNamespacesFromEntityData(EntityMappedEntityResponse entityMappedEntityResponse) {
    Set<String> namespaces = new HashSet<>();
    Object metadata = entityMappedEntityResponse.getData().get("metadata");
    if (!(metadata instanceof Map)) {
      return namespaces;
    }
    Object integrationPropertiesObj =
        ((Map<String, Object>) metadata).get(CatalogIntegrationEntityConstants.INTEGRATION_PROPERTIES);
    if (!(integrationPropertiesObj instanceof Map)) {
      return namespaces;
    }
    for (Object typeValue : ((Map<String, Object>) integrationPropertiesObj).values()) {
      if (!(typeValue instanceof Map)) {
        continue;
      }
      for (Object envValue : ((Map<String, Object>) typeValue).values()) {
        if (!(envValue instanceof List)) {
          continue;
        }
        for (Object entry : (List<?>) envValue) {
          if (!(entry instanceof Map)) {
            continue;
          }
          Object nsObj = ((Map<String, Object>) entry).get("namespace");
          if (nsObj != null) {
            namespaces.add(nsObj.toString());
          }
        }
      }
    }
    return namespaces;
  }

  private CatalogEntity findCorrelatedEntity(EntityMappedEntityResponse entityMappedEntityResponse,
      List<ScopeInfo> scopeInfos, String kind, String identifier) {
    if (!StringUtils.isBlank(entityMappedEntityResponse.getCorrelationField())) {
      String correlationField = entityMappedEntityResponse.getCorrelationField();
      String path = correlationField.startsWith(".") ? correlationField.substring(1) : correlationField;
      String correlationValue = CommonUtils.from(entityMappedEntityResponse.getData(), path, String.class);
      if (!StringUtils.isBlank(correlationValue)) {
        String filter =
            String.format("decorator._processed_data.%s=%s&%s=%s", path, correlationValue, path, correlationValue);
        return findCatalogEntity(filter, scopeInfos, kind);
      }
    } else if (entityMappedEntityResponse.hasCorrelationMapping()) {
      EntityMappedEntityResponse.CorrelationMapping mapping = entityMappedEntityResponse.getCorrelationMapping();
      String sourcePath =
          mapping.getSourcePath().startsWith(".") ? mapping.getSourcePath().substring(1) : mapping.getSourcePath();
      String destPath = mapping.getDestinationPath().startsWith(".") ? mapping.getDestinationPath().substring(1)
                                                                     : mapping.getDestinationPath();
      String correlationValue = CommonUtils.from(entityMappedEntityResponse.getData(), sourcePath, String.class);
      if (!StringUtils.isBlank(correlationValue)) {
        String op = mapping.getOperator();
        if (op == null || "eq".equalsIgnoreCase(op)) {
          String filter = String.format(
              "decorator._processed_data.%s=%s&%s=%s", destPath, correlationValue, destPath, correlationValue);
          return findCatalogEntity(filter, scopeInfos, kind);
        } else if ("contains".equalsIgnoreCase(op)) {
          return findCorrelatedEntityByContains(scopeInfos, kind, destPath, correlationValue);
        } else {
          throw new InvalidRequestException(
              String.format("Unsupported correlation operator '%s'. Supported operators: eq, contains", op));
        }
      }
    }
    String filter = String.format("identifier=%s", identifier);
    return findCatalogEntity(filter, scopeInfos, kind);
  }

  private CatalogEntity findCorrelatedEntity(List<ScopeInfo> scopeInfos, String kind, String identifier) {
    String filter = String.format("identifier=%s", identifier);
    return findCatalogEntity(filter, scopeInfos, kind);
  }

  private CatalogEntity registerNewEntity(EntityMappedEntityResponse entityMappedEntityResponse,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String kind, String type,
      String linkagePath, Map<String, Object> additionalLinkageConfig, String customName, String customIdentifier,
      TypesIntegrationConfig.EnumIntegrationType integrationType, String spacePath, String integrationConfigId) {
    log.info("Creating new catalog entity for integration entity: {}", entityMappedEntityResponse.getUuid());
    String effectiveName = !StringUtils.isBlank(customName) ? customName : entityMappedEntityResponse.getName();
    if (!StringUtils.isBlank(effectiveName)) {
      if (!StringUtils.isBlank(customIdentifier)) {
        entityMappedEntityResponse.getData().put("modifiedIdentifier", customIdentifier);
      } else {
        entityMappedEntityResponse.getData().put("modifiedIdentifier",
            effectiveName.trim()
                .replaceAll("^[0-9\\-$]*", "")
                .replaceAll("[^0-9a-zA-Z_$ ]", "")
                .replaceAll("\\s+", "_"));
      }
      entityMappedEntityResponse.getData().put("modifiedName", effectiveName);
    }
    String yaml =
        prepareCatalogEntityFromIntegrationEntityData(entityMappedEntityResponse, kind, type, additionalLinkageConfig);
    EntityResponse entityResponse = catalogService.createEntity(
        accountIdentifier, orgIdentifier, projectIdentifier, false, false, new EntityCreateRequest().yaml(yaml));
    CatalogEntity createdEntity = catalogServiceHelper.catalogEntity(
        accountIdentifier, orgIdentifier, projectIdentifier, kind, entityResponse.getIdentifier());
    decorateIDPCatalogWithIntegrationMetadata(createdEntity, linkagePath, entityMappedEntityResponse,
        SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER.name(), integrationType, spacePath,
        integrationConfigId);
    return createdEntity;
  }

  private void mergeEntity(CatalogEntity targetEntity, EntityMappedEntityResponse entityMappedEntityResponse,
      String linkagePath, Map<String, Object> additionalLinkageConfig,
      TypesIntegrationConfig.EnumIntegrationType integrationType, String spacePath, String integrationConfigId) {
    log.info("Updating catalog entity: {} for integration entity: {}", targetEntity.getIdentifier(),
        entityMappedEntityResponse.getUuid());
    decorateIDPCatalogWithIntegrationMetadata(targetEntity, linkagePath, entityMappedEntityResponse,
        SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.MERGE.name(), integrationType, spacePath,
        integrationConfigId);
    performAdditionalLinkageOnCatalogEntity(entityMappedEntityResponse, targetEntity, additionalLinkageConfig);
  }

  private List<String> extractDependsOnUuids(EntityMappedEntityResponse entityMappedEntityResponse) {
    Object dependsOnObj = entityMappedEntityResponse.getData().get("dependsOn");
    if (dependsOnObj instanceof List) {
      return ((List<?>) dependsOnObj)
          .stream()
          .filter(o -> o instanceof String)
          .map(String.class ::cast)
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private List<String> extractPartOfUuids(EntityMappedEntityResponse entityMappedEntityResponse) {
    Object partOfObj = entityMappedEntityResponse.getData().get("partOf");
    if (partOfObj instanceof String) {
      return List.of((String) partOfObj);
    }
    if (partOfObj instanceof List) {
      return ((List<?>) partOfObj)
          .stream()
          .filter(o -> o instanceof String)
          .map(String.class ::cast)
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private List<String> extractOwnedByUuids(EntityMappedEntityResponse entityMappedEntityResponse) {
    Object ownedByObj = entityMappedEntityResponse.getData().get("ownedBy");
    if (ownedByObj instanceof String) {
      return List.of((String) ownedByObj);
    }
    if (ownedByObj instanceof List) {
      return ((List<?>) ownedByObj)
          .stream()
          .filter(o -> o instanceof String)
          .map(String.class ::cast)
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private void storeDependsOnUuids(CatalogEntity catalogEntity, EntityMappedEntityResponse entityMappedEntityResponse,
      Map<String, String> uuidToLinkagePath, Map<String, EntityMappedEntityResponse> uuidToMappedEntityForRelation,
      String defaultLinkagePath, List<ScopeInfo> scopeInfos,
      TypesIntegrationConfig.EnumIntegrationType integrationType) {
    List<String> dependsOnUuids = extractDependsOnUuids(entityMappedEntityResponse);
    if (dependsOnUuids.isEmpty()) {
      log.debug("No dependsOn UUIDs found for entity {}", CatalogUtils.entityRef(catalogEntity));
      return;
    }

    log.info("Processing {} dependsOn UUIDs for entity {} (kind: {})", dependsOnUuids.size(),
        CatalogUtils.entityRef(catalogEntity), catalogEntity.getKind());

    Set<String> resolvedRefs = new HashSet<>();
    List<String> unresolvedUuids = new ArrayList<>();

    String entityKind = catalogEntity.getKind();
    for (String uuid : dependsOnUuids) {
      try {
        String linkagePath = uuidToLinkagePath.getOrDefault(uuid, defaultLinkagePath);
        EntityMappedEntityResponse targetMappedEntity = uuidToMappedEntityForRelation.get(uuid);
        CatalogEntity linkedEntity =
            findMappedEntity(uuid, linkagePath, scopeInfos, entityKind, integrationType, targetMappedEntity);
        if (linkedEntity != null) {
          String entityRef = CatalogUtils.entityRef(linkedEntity);
          resolvedRefs.add(entityRef);
          log.info("Resolved UUID {} to entity ref {} for entity {}", uuid, entityRef,
              CatalogUtils.entityRef(catalogEntity));
        } else {
          unresolvedUuids.add(uuid);
          log.info("UUID {} not yet resolvable, will store in decorator for later resolution", uuid);
        }
      } catch (Exception e) {
        log.warn(
            "Error resolving UUID {} for entity {}: {}", uuid, CatalogUtils.entityRef(catalogEntity), e.getMessage());
        unresolvedUuids.add(uuid);
      }
    }

    log.info("Resolution summary for entity {}: {} resolved immediately, {} pending resolution",
        CatalogUtils.entityRef(catalogEntity), resolvedRefs.size(), unresolvedUuids.size());

    if (!resolvedRefs.isEmpty()) {
      Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(catalogEntity.getYaml());
      Map<String, Object> spec = (Map<String, Object>) yamlMap.computeIfAbsent("spec", k -> new HashMap<>());
      List<String> existingDependsOn = CommonUtils.from(spec, "dependsOn", List.class);
      if (existingDependsOn == null) {
        existingDependsOn = new ArrayList<>();
      } else {
        existingDependsOn = new ArrayList<>(existingDependsOn);
      }
      existingDependsOn.addAll(resolvedRefs);
      spec.put("dependsOn", existingDependsOn);
      String updatedYaml = YamlUtils.writeObjectAsYaml(yamlMap);
      updateEntityViaApi(catalogEntity, updatedYaml);
      log.info("Added {} resolved dependencies to entity {} via catalog API: {}", resolvedRefs.size(),
          CatalogUtils.entityRef(catalogEntity), resolvedRefs);
    }

    if (!unresolvedUuids.isEmpty()) {
      CatalogEntity entityToUpdate = catalogEntity;
      if (!resolvedRefs.isEmpty()) {
        entityToUpdate = catalogEntityRepository.findById(catalogEntity.getId()).orElse(catalogEntity);
      }
      Map<String, Object> decorator = entityToUpdate.getDecorator();
      if (decorator == null) {
        decorator = new HashMap<>();
      }
      Map<String, Object> pendingRelations =
          (Map<String, Object>) decorator.computeIfAbsent("_unresolved_depends_on", k -> new HashMap<>());
      pendingRelations.put("uuids", unresolvedUuids);
      entityToUpdate.setDecorator(decorator);
      catalogEntityRepository.save(entityToUpdate);
      log.info("Stored {} unresolved UUIDs in decorator for entity {} for later reconciliation: {}",
          unresolvedUuids.size(), CatalogUtils.entityRef(entityToUpdate), unresolvedUuids);
    }

    log.info("Completed processing dependsOn for entity {}", CatalogUtils.entityRef(catalogEntity));
  }

  private void storePartOfUuids(CatalogEntity catalogEntity, EntityMappedEntityResponse entityMappedEntityResponse,
      Map<String, String> uuidToLinkagePath, Map<String, EntityMappedEntityResponse> uuidToMappedEntityForRelation,
      String defaultLinkagePath, List<ScopeInfo> scopeInfos,
      TypesIntegrationConfig.EnumIntegrationType integrationType) {
    List<String> partOfUuids = extractPartOfUuids(entityMappedEntityResponse);
    if (partOfUuids.isEmpty()) {
      log.debug("No partOf UUIDs found for entity {}", CatalogUtils.entityRef(catalogEntity));
      return;
    }

    log.info("Processing {} partOf UUIDs for entity {} (kind: {})", partOfUuids.size(),
        CatalogUtils.entityRef(catalogEntity), catalogEntity.getKind());

    Set<String> resolvedRefs = new HashSet<>();
    List<String> unresolvedUuids = new ArrayList<>();

    for (String uuid : partOfUuids) {
      try {
        String linkagePath = uuidToLinkagePath.getOrDefault(uuid, defaultLinkagePath);
        EntityMappedEntityResponse targetMappedEntity = uuidToMappedEntityForRelation.get(uuid);
        CatalogEntity linkedEntity =
            findMappedEntity(uuid, linkagePath, scopeInfos, null, integrationType, targetMappedEntity);
        if (linkedEntity != null) {
          String entityRef = CatalogUtils.entityRef(linkedEntity);
          resolvedRefs.add(entityRef);
          log.info("Resolved partOf UUID {} to entity ref {} for entity {}", uuid, entityRef,
              CatalogUtils.entityRef(catalogEntity));
        } else {
          unresolvedUuids.add(uuid);
          log.info("partOf UUID {} not yet resolvable, will store in decorator for later resolution", uuid);
        }
      } catch (Exception e) {
        log.warn("Error resolving partOf UUID {} for entity {}: {}", uuid, CatalogUtils.entityRef(catalogEntity),
            e.getMessage());
        unresolvedUuids.add(uuid);
      }
    }

    log.info("partOf resolution summary for entity {}: {} resolved immediately, {} pending resolution",
        CatalogUtils.entityRef(catalogEntity), resolvedRefs.size(), unresolvedUuids.size());

    if (!resolvedRefs.isEmpty()) {
      Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(catalogEntity.getYaml());
      Map<String, Object> spec = (Map<String, Object>) yamlMap.computeIfAbsent("spec", k -> new HashMap<>());
      List<String> partOfList = CommonUtils.from(spec, "partOf", List.class);
      partOfList = partOfList == null ? new ArrayList<>() : new ArrayList<>(partOfList);
      List<String> systemList = CommonUtils.from(spec, "system", List.class);
      systemList = systemList == null ? new ArrayList<>() : new ArrayList<>(systemList);
      for (String ref : resolvedRefs) {
        if (ref != null && ref.startsWith("system:")) {
          if (!systemList.contains(ref)) {
            systemList.add(ref);
          }
        } else {
          if (!partOfList.contains(ref)) {
            partOfList.add(ref);
          }
        }
      }
      spec.put("partOf", partOfList);
      spec.put("system", systemList);
      String updatedYaml = YamlUtils.writeObjectAsYaml(yamlMap);
      updateEntityViaApi(catalogEntity, updatedYaml);
      log.info("Added {} resolved partOf relations to entity {} via catalog API: {}", resolvedRefs.size(),
          CatalogUtils.entityRef(catalogEntity), resolvedRefs);
    }

    if (!unresolvedUuids.isEmpty()) {
      CatalogEntity entityToUpdate = catalogEntity;
      if (!resolvedRefs.isEmpty()) {
        entityToUpdate = catalogEntityRepository.findById(catalogEntity.getId()).orElse(catalogEntity);
      }
      Map<String, Object> decorator = entityToUpdate.getDecorator();
      if (decorator == null) {
        decorator = new HashMap<>();
      }
      Map<String, Object> pendingRelations =
          (Map<String, Object>) decorator.computeIfAbsent("_unresolved_part_of", k -> new HashMap<>());
      pendingRelations.put("uuids", unresolvedUuids);
      entityToUpdate.setDecorator(decorator);
      catalogEntityRepository.save(entityToUpdate);
      log.info("Stored {} unresolved partOf UUIDs in decorator for entity {} for later reconciliation: {}",
          unresolvedUuids.size(), CatalogUtils.entityRef(entityToUpdate), unresolvedUuids);
    }

    log.info("Completed processing partOf for entity {}", CatalogUtils.entityRef(catalogEntity));
  }

  private void resolveWaitingPartOfEntitiesWithBatching(String justCreatedUuid, CatalogEntity catalogEntity) {
    log.info("Starting reactive partOf resolution for entities waiting for UUID {}", justCreatedUuid);

    try {
      String targetEntityRef = CatalogUtils.entityRef(catalogEntity);
      int totalSuccessCount = 0;
      int totalFailureCount = 0;
      int batchNumber = 0;

      while (true) {
        batchNumber++;
        Query query = new Query(Criteria.where("decorator._unresolved_part_of.uuids").is(justCreatedUuid)).limit(100);

        List<CatalogEntity> waitingEntities = mongoTemplate.find(query, CatalogEntity.class);
        if (waitingEntities.isEmpty()) {
          break;
        }

        log.info("partOf batch {}: found {} entities waiting for UUID {} (entity: {})", batchNumber,
            waitingEntities.size(), justCreatedUuid, CatalogUtils.entityRef(catalogEntity));

        int successCount = 0;
        int failureCount = 0;

        for (CatalogEntity waitingEntity : waitingEntities) {
          String waitingEntityRef = CatalogUtils.entityRef(waitingEntity);
          try {
            log.debug("Processing waiting entity {} for partOf UUID {}", waitingEntityRef, justCreatedUuid);

            Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(waitingEntity.getYaml());
            Map<String, Object> spec = (Map<String, Object>) yamlMap.computeIfAbsent("spec", k -> new HashMap<>());
            if (targetEntityRef != null && targetEntityRef.startsWith("system:")) {
              List<String> systemList = CommonUtils.from(spec, "system", List.class);
              systemList = systemList == null ? new ArrayList<>() : new ArrayList<>(systemList);
              if (!systemList.contains(targetEntityRef)) {
                systemList.add(targetEntityRef);
              }
              spec.put("system", systemList);
            } else {
              List<String> partOfList = CommonUtils.from(spec, "partOf", List.class);
              partOfList = partOfList == null ? new ArrayList<>() : new ArrayList<>(partOfList);
              if (!partOfList.contains(targetEntityRef)) {
                partOfList.add(targetEntityRef);
              }
              spec.put("partOf", partOfList);
            }
            String updatedYaml = YamlUtils.writeObjectAsYaml(yamlMap);

            updateEntityViaApi(waitingEntity, updatedYaml);

            CatalogEntity entityAfterUpdate =
                catalogEntityRepository.findById(waitingEntity.getId()).orElse(waitingEntity);
            Map<String, Object> decorator = entityAfterUpdate.getDecorator();
            if (decorator != null) {
              Map<String, Object> unresolvedPartOf = (Map<String, Object>) decorator.get("_unresolved_part_of");
              if (unresolvedPartOf != null) {
                List<String> uuids = (List<String>) unresolvedPartOf.get("uuids");
                if (uuids != null) {
                  uuids = new ArrayList<>(uuids);
                  uuids.remove(justCreatedUuid);
                  if (uuids.isEmpty()) {
                    decorator.remove("_unresolved_part_of");
                  } else {
                    unresolvedPartOf.put("uuids", uuids);
                  }
                  entityAfterUpdate.setDecorator(decorator);
                  catalogEntityRepository.save(entityAfterUpdate);
                }
              }
            }
            successCount++;
            log.info("Updated entity {} to resolve partOf UUID {} to {}", waitingEntityRef, justCreatedUuid,
                targetEntityRef);

          } catch (Exception e) {
            log.error("Error resolving partOf UUID {} for waiting entity {} (id: {}): {}", justCreatedUuid,
                waitingEntityRef, waitingEntity.getId(), e.getMessage(), e);
            failureCount++;
          }
        }

        log.info("partOf batch {}: resolved UUID {} for {} entities (success: {}, failed: {})", batchNumber,
            justCreatedUuid, successCount + failureCount, successCount, failureCount);

        totalSuccessCount += successCount;
        totalFailureCount += failureCount;

        if (waitingEntities.size() < 100) {
          break;
        }
      }

      log.info("Completed reactive partOf resolution for UUID {}: {} entities updated, {} failed across {} batches",
          justCreatedUuid, totalSuccessCount, totalFailureCount, batchNumber);

    } catch (Exception e) {
      log.error("Error in resolveWaitingPartOfEntitiesWithBatching for UUID {} (entity: {}): {}", justCreatedUuid,
          CatalogUtils.entityRef(catalogEntity), e.getMessage(), e);
    }
  }

  private void storeOwnedByUuids(CatalogEntity catalogEntity, EntityMappedEntityResponse entityMappedEntityResponse,
      Map<String, String> uuidToLinkagePath, Map<String, EntityMappedEntityResponse> uuidToMappedEntityForRelation,
      String defaultLinkagePath, List<ScopeInfo> scopeInfos,
      TypesIntegrationConfig.EnumIntegrationType integrationType) {
    List<String> ownedByUuids = extractOwnedByUuids(entityMappedEntityResponse);
    if (ownedByUuids.isEmpty()) {
      log.debug("No ownedBy UUIDs found for entity {}", CatalogUtils.entityRef(catalogEntity));
      return;
    }

    log.info("Processing {} ownedBy UUIDs for entity {} (kind: {})", ownedByUuids.size(),
        CatalogUtils.entityRef(catalogEntity), catalogEntity.getKind());

    Set<String> resolvedRefs = new HashSet<>();
    List<String> unresolvedUuids = new ArrayList<>();
    String firstResolvedRef = null;

    for (String uuid : ownedByUuids) {
      try {
        String linkagePath = uuidToLinkagePath.getOrDefault(uuid, defaultLinkagePath);
        EntityMappedEntityResponse targetMappedEntity = uuidToMappedEntityForRelation.get(uuid);
        CatalogEntity linkedEntity =
            findMappedEntity(uuid, linkagePath, scopeInfos, null, integrationType, targetMappedEntity);
        if (linkedEntity != null) {
          String entityRef = CatalogUtils.entityRef(linkedEntity);
          resolvedRefs.add(entityRef);
          if (firstResolvedRef == null) {
            firstResolvedRef = entityRef;
          }
          log.info("Resolved ownedBy UUID {} to entity ref {} for entity {}", uuid, entityRef,
              CatalogUtils.entityRef(catalogEntity));
        } else {
          unresolvedUuids.add(uuid);
          log.info("ownedBy UUID {} not yet resolvable, will store in decorator for later resolution", uuid);
        }
      } catch (Exception e) {
        log.warn("Error resolving ownedBy UUID {} for entity {}: {}", uuid, CatalogUtils.entityRef(catalogEntity),
            e.getMessage());
        unresolvedUuids.add(uuid);
      }
    }

    log.info("ownedBy resolution summary for entity {}: {} resolved immediately, {} pending resolution",
        CatalogUtils.entityRef(catalogEntity), resolvedRefs.size(), unresolvedUuids.size());

    if (!resolvedRefs.isEmpty()) {
      Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(catalogEntity.getYaml());
      yamlMap.put("owner", firstResolvedRef);
      String updatedYaml = YamlUtils.writeObjectAsYaml(yamlMap);
      updateEntityViaApi(catalogEntity, updatedYaml);
      log.info("Added {} resolved ownedBy relations to entity {} via catalog API: {}", resolvedRefs.size(),
          CatalogUtils.entityRef(catalogEntity), resolvedRefs);
    }

    if (!unresolvedUuids.isEmpty()) {
      CatalogEntity entityToUpdate = catalogEntity;
      if (!resolvedRefs.isEmpty()) {
        entityToUpdate = catalogEntityRepository.findById(catalogEntity.getId()).orElse(catalogEntity);
      }
      Map<String, Object> decorator = entityToUpdate.getDecorator();
      if (decorator == null) {
        decorator = new HashMap<>();
      }
      Map<String, Object> pendingRelations =
          (Map<String, Object>) decorator.computeIfAbsent("_unresolved_owned_by", k -> new HashMap<>());
      pendingRelations.put("uuids", unresolvedUuids);
      entityToUpdate.setDecorator(decorator);
      catalogEntityRepository.save(entityToUpdate);
      log.info("Stored {} unresolved ownedBy UUIDs in decorator for entity {} for later reconciliation: {}",
          unresolvedUuids.size(), CatalogUtils.entityRef(entityToUpdate), unresolvedUuids);
    }

    log.info("Completed processing ownedBy for entity {}", CatalogUtils.entityRef(catalogEntity));
  }

  private void resolveWaitingOwnedByEntitiesWithBatching(String justCreatedUuid, CatalogEntity catalogEntity) {
    log.info("Starting reactive ownedBy resolution for entities waiting for UUID {}", justCreatedUuid);

    try {
      String targetEntityRef = CatalogUtils.entityRef(catalogEntity);
      int totalSuccessCount = 0;
      int totalFailureCount = 0;
      int batchNumber = 0;

      while (true) {
        batchNumber++;
        Query query = new Query(Criteria.where("decorator._unresolved_owned_by.uuids").is(justCreatedUuid)).limit(100);

        List<CatalogEntity> waitingEntities = mongoTemplate.find(query, CatalogEntity.class);
        if (waitingEntities.isEmpty()) {
          break;
        }

        log.info("ownedBy batch {}: found {} entities waiting for UUID {} (entity: {})", batchNumber,
            waitingEntities.size(), justCreatedUuid, CatalogUtils.entityRef(catalogEntity));

        int successCount = 0;
        int failureCount = 0;

        for (CatalogEntity waitingEntity : waitingEntities) {
          String waitingEntityRef = CatalogUtils.entityRef(waitingEntity);
          try {
            log.debug("Processing waiting entity {} for ownedBy UUID {}", waitingEntityRef, justCreatedUuid);

            Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(waitingEntity.getYaml());
            yamlMap.put("owner", targetEntityRef);
            String updatedYaml = YamlUtils.writeObjectAsYaml(yamlMap);

            updateEntityViaApi(waitingEntity, updatedYaml);

            CatalogEntity entityAfterUpdate =
                catalogEntityRepository.findById(waitingEntity.getId()).orElse(waitingEntity);
            Map<String, Object> decorator = entityAfterUpdate.getDecorator();
            if (decorator != null) {
              Map<String, Object> unresolvedOwnedBy = (Map<String, Object>) decorator.get("_unresolved_owned_by");
              if (unresolvedOwnedBy != null) {
                List<String> uuids = (List<String>) unresolvedOwnedBy.get("uuids");
                if (uuids != null) {
                  uuids = new ArrayList<>(uuids);
                  uuids.remove(justCreatedUuid);
                  if (uuids.isEmpty()) {
                    decorator.remove("_unresolved_owned_by");
                  } else {
                    unresolvedOwnedBy.put("uuids", uuids);
                  }
                  entityAfterUpdate.setDecorator(decorator);
                  catalogEntityRepository.save(entityAfterUpdate);
                }
              }
            }
            successCount++;
            log.info("Updated entity {} to resolve ownedBy UUID {} to {}", waitingEntityRef, justCreatedUuid,
                targetEntityRef);

          } catch (Exception e) {
            log.error("Error resolving ownedBy UUID {} for waiting entity {} (id: {}): {}", justCreatedUuid,
                waitingEntityRef, waitingEntity.getId(), e.getMessage(), e);
            failureCount++;
          }
        }

        log.info("ownedBy batch {}: resolved UUID {} for {} entities (success: {}, failed: {})", batchNumber,
            justCreatedUuid, successCount + failureCount, successCount, failureCount);

        totalSuccessCount += successCount;
        totalFailureCount += failureCount;

        if (waitingEntities.size() < 100) {
          break;
        }
      }

      log.info("Completed reactive ownedBy resolution for UUID {}: {} entities updated, {} failed across {} batches",
          justCreatedUuid, totalSuccessCount, totalFailureCount, batchNumber);

    } catch (Exception e) {
      log.error("Error in resolveWaitingOwnedByEntitiesWithBatching for UUID {} (entity: {}): {}", justCreatedUuid,
          CatalogUtils.entityRef(catalogEntity), e.getMessage(), e);
    }
  }

  private void resolveWaitingEntitiesWithBatching(String justCreatedUuid, CatalogEntity catalogEntity) {
    log.info("Starting reactive resolution for entities waiting for UUID {}", justCreatedUuid);

    try {
      String targetEntityRef = CatalogUtils.entityRef(catalogEntity);
      int totalSuccessCount = 0;
      int totalFailureCount = 0;
      int batchNumber = 0;

      while (true) {
        batchNumber++;
        Query query =
            new Query(Criteria.where("decorator._unresolved_depends_on.uuids").is(justCreatedUuid)).limit(100);

        List<CatalogEntity> waitingEntities = mongoTemplate.find(query, CatalogEntity.class);
        if (waitingEntities.isEmpty()) {
          break;
        }

        log.info("Batch {}: found {} entities waiting for UUID {} (entity: {})", batchNumber, waitingEntities.size(),
            justCreatedUuid, CatalogUtils.entityRef(catalogEntity));

        int successCount = 0;
        int failureCount = 0;

        for (CatalogEntity waitingEntity : waitingEntities) {
          String waitingEntityRef = CatalogUtils.entityRef(waitingEntity);
          try {
            log.debug("Processing waiting entity {} for UUID {}", waitingEntityRef, justCreatedUuid);

            Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(waitingEntity.getYaml());
            Map<String, Object> spec = (Map<String, Object>) yamlMap.computeIfAbsent("spec", k -> new HashMap<>());
            List<String> dependsOn = CommonUtils.from(spec, "dependsOn", List.class);
            if (dependsOn == null) {
              dependsOn = new ArrayList<>();
            } else {
              dependsOn = new ArrayList<>(dependsOn);
            }
            if (!dependsOn.contains(targetEntityRef)) {
              dependsOn.add(targetEntityRef);
            }
            spec.put("dependsOn", dependsOn);
            String updatedYaml = YamlUtils.writeObjectAsYaml(yamlMap);

            updateEntityViaApi(waitingEntity, updatedYaml);

            CatalogEntity entityAfterUpdate =
                catalogEntityRepository.findById(waitingEntity.getId()).orElse(waitingEntity);
            Map<String, Object> decorator = entityAfterUpdate.getDecorator();
            if (decorator != null) {
              Map<String, Object> unresolvedDependsOn = (Map<String, Object>) decorator.get("_unresolved_depends_on");
              if (unresolvedDependsOn != null) {
                List<String> uuids = (List<String>) unresolvedDependsOn.get("uuids");
                if (uuids != null) {
                  uuids = new ArrayList<>(uuids);
                  uuids.remove(justCreatedUuid);
                  if (uuids.isEmpty()) {
                    decorator.remove("_unresolved_depends_on");
                  } else {
                    unresolvedDependsOn.put("uuids", uuids);
                  }
                  entityAfterUpdate.setDecorator(decorator);
                  catalogEntityRepository.save(entityAfterUpdate);
                }
              }
            }
            successCount++;
            log.info("Updated entity {} to resolve UUID {} to {}", waitingEntityRef, justCreatedUuid, targetEntityRef);

          } catch (Exception e) {
            log.error("Error resolving UUID {} for waiting entity {} (id: {}): {}", justCreatedUuid, waitingEntityRef,
                waitingEntity.getId(), e.getMessage(), e);
            failureCount++;
          }
        }

        log.info("Batch {}: resolved UUID {} for {} entities (success: {}, failed: {})", batchNumber, justCreatedUuid,
            successCount + failureCount, successCount, failureCount);

        totalSuccessCount += successCount;
        totalFailureCount += failureCount;

        if (waitingEntities.size() < 100) {
          break;
        }
      }

      log.info("Completed reactive resolution for UUID {}: {} entities updated, {} failed across {} batches",
          justCreatedUuid, totalSuccessCount, totalFailureCount, batchNumber);

    } catch (Exception e) {
      log.error("Error in resolveWaitingEntitiesWithBatching for UUID {} (entity: {}): {}", justCreatedUuid,
          CatalogUtils.entityRef(catalogEntity), e.getMessage(), e);
    }
  }

  private List<CatalogEntity> updateDependsOnRelation(
      CatalogEntity catalogEntity, String resolvedUuid, String targetEntityRef) {
    Map<String, Object> decorator = catalogEntity.getDecorator();
    if (decorator == null) {
      log.debug("No decorator found for entity {}, skipping relation update", CatalogUtils.entityRef(catalogEntity));
      return null;
    }

    Map<String, Object> unresolvedDependsOn = (Map<String, Object>) decorator.get("_unresolved_depends_on");
    if (unresolvedDependsOn == null) {
      log.debug("No unresolved dependencies found in decorator for entity {}", CatalogUtils.entityRef(catalogEntity));
      return null;
    }

    List<String> uuids = (List<String>) unresolvedDependsOn.get("uuids");
    if (uuids == null || !uuids.contains(resolvedUuid)) {
      log.debug("UUID {} not found in pending list for entity {}", resolvedUuid, CatalogUtils.entityRef(catalogEntity));
      return null;
    }

    log.info("Updating entity {} to resolve UUID {} to {}", CatalogUtils.entityRef(catalogEntity), resolvedUuid,
        targetEntityRef);
    uuids.remove(resolvedUuid);
    log.debug("Removed UUID {} from unresolved list, {} remaining", resolvedUuid, uuids.size());

    if (uuids.isEmpty()) {
      decorator.remove("_unresolved_depends_on");
      log.info("All dependencies resolved for entity {}, removed _unresolved_depends_on from decorator",
          CatalogUtils.entityRef(catalogEntity));
    } else {
      unresolvedDependsOn.put("uuids", uuids);
      log.info("Entity {} still has {} unresolved dependencies: {}", CatalogUtils.entityRef(catalogEntity),
          uuids.size(), uuids);
    }
    catalogEntity.setDecorator(decorator);

    // Snapshot must be taken before relations are mutated. toBuilder().build() is a shallow copy,
    // so we must deep-copy the relations map to prevent in-place mutation from corrupting the snapshot.
    // Without this, updateRelations sees identical old/new and computes an empty diff — the reverse
    // relation (e.g. dependencyOf) on the target entity is never established.
    CatalogEntity existingSnapshot = null;
    if (catalogEntity instanceof InlineCatalogEntity) {
      existingSnapshot = ((InlineCatalogEntity) catalogEntity).toBuilder().build();
    } else if (catalogEntity instanceof GitReferencedCatalogEntity) {
      existingSnapshot = ((GitReferencedCatalogEntity) catalogEntity).toBuilder().build();
    }
    if (existingSnapshot != null) {
      existingSnapshot.setRelations(deepCopyRelations(existingSnapshot.getRelations()));
    }

    Map<String, Set<String>> relations = catalogEntity.getRelations();
    if (relations == null) {
      relations = new HashMap<>();
    }
    Set<String> dependsOn = relations.getOrDefault("dependsOn", new HashSet<>());
    dependsOn.add(targetEntityRef);
    relations.put("dependsOn", dependsOn);
    catalogEntity.setRelations(relations);

    List<CatalogEntity> referencedEntities = relationsProcessor.updateRelations(existingSnapshot, catalogEntity);

    log.info("Successfully resolved UUID {} to {} for entity {}", resolvedUuid, targetEntityRef,
        CatalogUtils.entityRef(catalogEntity));

    return referencedEntities;
  }

  private List<CatalogEntity> updatePartOfRelation(
      CatalogEntity catalogEntity, String resolvedUuid, String targetEntityRef, Kind targetKind) {
    Map<String, Object> decorator = catalogEntity.getDecorator();
    if (decorator == null) {
      log.debug(
          "No decorator found for entity {}, skipping partOf relation update", CatalogUtils.entityRef(catalogEntity));
      return null;
    }

    Map<String, Object> unresolvedPartOf = (Map<String, Object>) decorator.get("_unresolved_part_of");
    if (unresolvedPartOf == null) {
      log.debug("No unresolved partOf found in decorator for entity {}", CatalogUtils.entityRef(catalogEntity));
      return null;
    }

    List<String> uuids = (List<String>) unresolvedPartOf.get("uuids");
    if (uuids == null || !uuids.contains(resolvedUuid)) {
      log.debug("UUID {} not found in pending partOf list for entity {}", resolvedUuid,
          CatalogUtils.entityRef(catalogEntity));
      return null;
    }

    log.info("Updating entity {} to resolve partOf UUID {} to {}", CatalogUtils.entityRef(catalogEntity), resolvedUuid,
        targetEntityRef);
    uuids.remove(resolvedUuid);
    log.debug("Removed UUID {} from unresolved partOf list, {} remaining", resolvedUuid, uuids.size());

    if (uuids.isEmpty()) {
      decorator.remove("_unresolved_part_of");
      log.info("All partOf relations resolved for entity {}, removed _unresolved_part_of from decorator",
          CatalogUtils.entityRef(catalogEntity));
    } else {
      unresolvedPartOf.put("uuids", uuids);
      log.info("Entity {} still has {} unresolved partOf UUIDs: {}", CatalogUtils.entityRef(catalogEntity),
          uuids.size(), uuids);
    }
    catalogEntity.setDecorator(decorator);

    CatalogEntity existingSnapshot = null;
    if (catalogEntity instanceof InlineCatalogEntity) {
      existingSnapshot = ((InlineCatalogEntity) catalogEntity).toBuilder().build();
    } else if (catalogEntity instanceof GitReferencedCatalogEntity) {
      existingSnapshot = ((GitReferencedCatalogEntity) catalogEntity).toBuilder().build();
    }
    if (existingSnapshot != null) {
      existingSnapshot.setRelations(deepCopyRelations(existingSnapshot.getRelations()));
    }

    Map<String, Object> spec = catalogEntity.getSpec();
    if (spec == null) {
      spec = new HashMap<>();
    }
    List<String> systemList =
        spec.get("system") instanceof List ? new ArrayList<>((List<String>) spec.get("system")) : new ArrayList<>();
    if (!systemList.contains(targetEntityRef)) {
      systemList.add(targetEntityRef);
    }
    spec.put("system", systemList);
    catalogEntity.setSpec(spec);

    Map<String, Set<String>> relations = catalogEntity.getRelations();
    if (relations == null) {
      relations = new HashMap<>();
    }
    Set<String> partOf = relations.getOrDefault("partOf", new HashSet<>());
    partOf.add(targetEntityRef);
    relations.put("partOf", partOf);
    catalogEntity.setRelations(relations);

    List<CatalogEntity> referencedEntities = relationsProcessor.updateRelations(existingSnapshot, catalogEntity);

    log.info("Successfully resolved partOf UUID {} to {} for entity {}", resolvedUuid, targetEntityRef,
        CatalogUtils.entityRef(catalogEntity));

    return referencedEntities;
  }

  private List<CatalogEntity> updateOwnedByRelation(
      CatalogEntity catalogEntity, String resolvedUuid, String targetEntityRef) {
    Map<String, Object> decorator = catalogEntity.getDecorator();
    if (decorator == null) {
      log.debug(
          "No decorator found for entity {}, skipping ownedBy relation update", CatalogUtils.entityRef(catalogEntity));
      return null;
    }

    Map<String, Object> unresolvedOwnedBy = (Map<String, Object>) decorator.get("_unresolved_owned_by");
    if (unresolvedOwnedBy == null) {
      log.debug("No unresolved ownedBy found in decorator for entity {}", CatalogUtils.entityRef(catalogEntity));
      return null;
    }

    List<String> uuids = (List<String>) unresolvedOwnedBy.get("uuids");
    if (uuids == null || !uuids.contains(resolvedUuid)) {
      log.debug("UUID {} not found in pending ownedBy list for entity {}", resolvedUuid,
          CatalogUtils.entityRef(catalogEntity));
      return null;
    }

    log.info("Updating entity {} to resolve ownedBy UUID {} to {}", CatalogUtils.entityRef(catalogEntity), resolvedUuid,
        targetEntityRef);
    uuids.remove(resolvedUuid);
    log.debug("Removed UUID {} from unresolved ownedBy list, {} remaining", resolvedUuid, uuids.size());

    if (uuids.isEmpty()) {
      decorator.remove("_unresolved_owned_by");
      log.info("All ownedBy relations resolved for entity {}, removed _unresolved_owned_by from decorator",
          CatalogUtils.entityRef(catalogEntity));
    } else {
      unresolvedOwnedBy.put("uuids", uuids);
      log.info("Entity {} still has {} unresolved ownedBy UUIDs: {}", CatalogUtils.entityRef(catalogEntity),
          uuids.size(), uuids);
    }
    catalogEntity.setDecorator(decorator);

    CatalogEntity existingSnapshot = null;
    if (catalogEntity instanceof InlineCatalogEntity) {
      existingSnapshot = ((InlineCatalogEntity) catalogEntity).toBuilder().build();
    } else if (catalogEntity instanceof GitReferencedCatalogEntity) {
      existingSnapshot = ((GitReferencedCatalogEntity) catalogEntity).toBuilder().build();
    }
    if (existingSnapshot != null) {
      existingSnapshot.setRelations(deepCopyRelations(existingSnapshot.getRelations()));
    }

    catalogEntity.setOwner(targetEntityRef);

    Map<String, Set<String>> relations = catalogEntity.getRelations();
    if (relations == null) {
      relations = new HashMap<>();
    }
    Set<String> ownedBy = relations.getOrDefault("ownedBy", new HashSet<>());
    ownedBy.add(targetEntityRef);
    relations.put("ownedBy", ownedBy);
    catalogEntity.setRelations(relations);

    List<CatalogEntity> referencedEntities = relationsProcessor.updateRelations(existingSnapshot, catalogEntity);

    log.info("Successfully resolved ownedBy UUID {} to {} for entity {}", resolvedUuid, targetEntityRef,
        CatalogUtils.entityRef(catalogEntity));

    return referencedEntities;
  }

  /**
   * Deep-copies a relations map so that mutations to the original do not affect the copy.
   * Used when creating snapshots before modifying an entity's relations in-place.
   */
  private Map<String, Set<String>> deepCopyRelations(Map<String, Set<String>> relations) {
    if (relations == null) {
      return null;
    }
    Map<String, Set<String>> copy = new HashMap<>();
    for (Map.Entry<String, Set<String>> entry : relations.entrySet()) {
      copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
    }
    return copy;
  }

  /**
   * Merges relations from an incoming referenced entity into an already-accumulated version.
   * This is needed when the same entity (e.g., the just-created entity) appears as a referenced entity
   * across multiple iterations — each iteration fetches a fresh copy from DB, so we must union
   * the reverse relations to avoid losing earlier updates.
   */
  private void mergeReferencedEntityRelations(CatalogEntity existing, CatalogEntity incoming) {
    Map<String, Set<String>> existingRelations = existing.getRelations();
    Map<String, Set<String>> incomingRelations = incoming.getRelations();
    if (incomingRelations == null) {
      return;
    }
    if (existingRelations == null) {
      existingRelations = new HashMap<>();
    }
    for (Map.Entry<String, Set<String>> entry : incomingRelations.entrySet()) {
      existingRelations.merge(entry.getKey(), new HashSet<>(entry.getValue()), (a, b) -> {
        a.addAll(b);
        return a;
      });
    }
    existing.setRelations(existingRelations);
  }

  private CatalogIntegrationOps<?, ?, ?> getServiceForCatalogIntegration(
      CatalogIntegrationRequest.CatalogIntegrationTypeEnum catalogIntegration) {
    switch (catalogIntegration) {
      case HARNESS_CD:
        return harnessCDIntegrationOps;
      default:
        throw new UnexpectedException("Catalog Integration " + catalogIntegration + " not supported yet");
    }
  }

  private <T extends CatalogIntegrationRequest> T getCatalogIntegrationRequest(
      CatalogIntegrationRequest.CatalogIntegrationTypeEnum catalogIntegration, Object request) {
    switch (catalogIntegration) {
      case HARNESS_CD:
        Map<String, Object> requestMap = JacksonUtils.convert(request);
        HarnessCDIntegrationRequest harnessCDIntegrationRequest = new HarnessCDIntegrationRequest();
        harnessCDIntegrationRequest.setType(BaseIntegrationRequest.TypeEnum.CATALOG);
        harnessCDIntegrationRequest.setCatalogIntegrationType(
            CatalogIntegrationRequest.CatalogIntegrationTypeEnum.HARNESS_CD);
        harnessCDIntegrationRequest.setEnabled((Boolean) requestMap.get("enabled"));
        harnessCDIntegrationRequest.setScopes((String) requestMap.get("scopes"));
        harnessCDIntegrationRequest.setAutoDeletion((Boolean) requestMap.get("auto_deletion"));
        return (T) harnessCDIntegrationRequest;
      default:
        throw new UnexpectedException("Catalog Integration " + catalogIntegration + " not supported yet");
    }
  }

  private void performSyncInBackground(
      CatalogIntegrationOps<CatalogIntegrationEntity, CatalogIntegrationRequest, CatalogIntegrationSyncRequest>
          catalogIntegrationOps,
      CatalogIntegrationEntity catalogIntegrationEntity) {
    CatalogIntegrationSyncRequest catalogIntegrationSyncRequest =
        catalogIntegrationOps.prepareCatalogIntegrationSyncRequest(catalogIntegrationEntity);
    catalogIntegrationOps.performSyncInBackground(catalogIntegrationSyncRequest)
        .thenRun(()
                     -> log.info("Sync completed in background for catalogIntegrationSyncRequest = {}",
                         catalogIntegrationSyncRequest))
        .exceptionally(ex -> {
          log.error("Error in performSyncInBackground for catalogIntegrationSyncRequest = {} Exception = {}",
              catalogIntegrationSyncRequest, ex.getMessage(), ex);
          return null;
        });
  }

  private IntegrationEntity getByAccountAndIdentifier(String accountIdentifier, String identifier) {
    Optional<IntegrationEntity> optionalCatalogIntegrationEntity =
        integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
            accountIdentifier, identifier, IntegrationEntity.Integration.CATALOG);
    if (optionalCatalogIntegrationEntity.isEmpty()) {
      throw new InvalidRequestException("Catalog integration with identifier " + identifier + " not found");
    }
    return optionalCatalogIntegrationEntity.get();
  }

  private Criteria buildGetCriteria(String accountIdentifier, String searchTerm) {
    Criteria criteria = new Criteria();
    criteria.and(IntegrationEntity.IntegrationsKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(IntegrationEntity.IntegrationsKeys.integration).is(IntegrationEntity.Integration.CATALOG);

    if (isNotEmpty(searchTerm)) {
      criteria.andOperator(
          new Criteria().orOperator(where(IntegrationEntity.IntegrationsKeys.identifier)
                                        .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS)));
    }
    return criteria;
  }

  public List<TypesEntityMapping> listEntityMappings(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String integrationId) {
    try {
      return getGeneralResponse(
          integrationManagerClientHelper.listEntityMappings(accountIdentifier, accountIdentifier, orgIdentifier,
              projectIdentifier, integrationId, integrationManagerClientHelper.getIntegrationManagerIdpMappingId()));
    } catch (Exception ex) {
      log.error("Error in listEntityMappings. Error = {}", ex.getMessage(), ex);
      throw new UnexpectedException("Error while fetching entity mappings");
    }
  }

  private List<String> getIdpKinds(List<TypesEntityMapping> entityMappings) {
    return entityMappings.stream()
        .map(TypesEntityMapping::getMappingConfig)
        .filter(Objects::nonNull)
        .map(TypesEntityMapping.TypesMappingConfig::getIdpKind)
        .filter(Objects::nonNull)
        .toList();
  }

  private List<String> getIdpTypes(List<TypesEntityMapping> entityMappings) {
    return entityMappings.stream()
        .map(TypesEntityMapping::getMappingConfig)
        .filter(Objects::nonNull)
        .map(TypesEntityMapping.TypesMappingConfig::getIdpType)
        .filter(Objects::nonNull)
        .toList();
  }

  public TypesIntegrationConfig getIntegrationConfig(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String integrationId) {
    try {
      return getGeneralResponse(integrationManagerClientHelper.getIntegrationConfig(
          accountIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, integrationId));
    } catch (Exception ex) {
      log.error("Error in getIntegrationConfig. Error = {}", ex.getMessage(), ex);
      throw new UnexpectedException("Error while fetching integration config");
    }
  }

  private String getScopes(String accountIdentifier, TypesIntegrationConfig integrationConfig) {
    return (boolean) integrationConfig.getConfiguration().get("all_scopes")
        ? "account.*"
        : (String.join(",", (ArrayList) integrationConfig.getConfiguration().get("selected_scopes"))
                  .replace(accountIdentifier, "account")
                  .replace("/", "."));
  }

  private Set<String> correlateRawIntegrationEntityAndIDPCatalogEntity(
      EntityResponse entityResponse, String spacePath, String integrationId) {
    String decorator = entityResponse.getDecorator();
    if (StringUtils.isEmpty(decorator)) {
      return Collections.emptySet();
    }
    try {
      Map<String, Object> decoratorMap = YamlUtils.loadYamlStringAsMap(decorator);

      Map<String, Object> processedData = decoratorMap.containsKey(PROCESSED_DATA)
          ? (Map<String, Object>) decoratorMap.get(PROCESSED_DATA)
          : decoratorMap;

      Object metadataObj = processedData.get("metadata");
      if (!(metadataObj instanceof Map)) {
        return Collections.emptySet();
      }

      Object integrationObj =
          ((Map<String, Object>) metadataObj).get(CatalogIntegrationEntityConstants.INTEGRATION_PATH_PREFIX);
      if (!(integrationObj instanceof Map)) {
        return Collections.emptySet();
      }

      Object spacePathObj = ((Map<String, Object>) integrationObj).get(spacePath);
      if (!(spacePathObj instanceof Map)) {
        return Collections.emptySet();
      }

      Object integrationIdObj = ((Map<String, Object>) spacePathObj).get(integrationId);
      if (!(integrationIdObj instanceof Map)) {
        return Collections.emptySet();
      }

      Map<String, Object> integrationEntries = (Map<String, Object>) integrationIdObj;
      Set<String> entityUuids = new HashSet<>();

      for (Map.Entry<String, Object> kindEntry : integrationEntries.entrySet()) {
        if (!(kindEntry.getValue() instanceof Map)) {
          continue;
        }
        Map<String, Object> kindMap = (Map<String, Object>) kindEntry.getValue();
        Object directUuid = kindMap.get("entity_uuid");
        if (directUuid instanceof String) {
          entityUuids.add((String) directUuid);
          continue;
        }
        // for k8s
        for (Map.Entry<String, Object> subEntry : kindMap.entrySet()) {
          if (subEntry.getValue() instanceof Map) {
            Object nestedUuid = ((Map<String, Object>) subEntry.getValue()).get("entity_uuid");
            if (nestedUuid instanceof String) {
              entityUuids.add((String) nestedUuid);
            }
          }
        }
      }

      return entityUuids;
    } catch (Exception ex) {
      log.warn("Error in correlateRawIntegrationEntityAndIDPCatalogEntity for entityRef = {} Error = {}",
          entityResponse.getEntityRef(), ex.getMessage(), ex);
      return Collections.emptySet();
    }
  }

  @VisibleForTesting
  boolean matchesCorrelationFieldInEntity(
      EntityResponse entityResponse, String correlationField, String correlationValue) {
    return matchesCorrelationFieldInEntity(entityResponse, correlationField, correlationValue, null);
  }

  @VisibleForTesting
  boolean matchesCorrelationFieldInEntity(
      EntityResponse entityResponse, String correlationField, String correlationValue, String operator) {
    if (entityResponse == null || StringUtils.isBlank(correlationField) || StringUtils.isBlank(correlationValue)) {
      return false;
    }
    String path = correlationField.startsWith(".") ? correlationField.substring(1) : correlationField;
    try {
      // Check in decorator
      String decorator = entityResponse.getDecorator();
      if (!StringUtils.isBlank(decorator)) {
        Map<String, Object> decoratorMap = YamlUtils.loadYamlStringAsMap(decorator);
        String decoratorValue = CommonUtils.from(decoratorMap, path, String.class);
        if (matchesValue(correlationValue, decoratorValue, operator)) {
          return true;
        }
      }

      // Check in yaml
      String yaml = entityResponse.getYaml();
      if (!StringUtils.isBlank(yaml)) {
        Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(yaml);
        String yamlValue = CommonUtils.from(yamlMap, path, String.class);
        return matchesValue(correlationValue, yamlValue, operator);
      }

      return false;
    } catch (Exception ex) {
      log.warn("Error matching correlation field '{}' with value '{}' in entity '{}': {}", correlationField,
          correlationValue, entityResponse.getEntityRef(), ex.getMessage(), ex);
      return false;
    }
  }

  private boolean matchesValue(String correlationValue, String actualValue, String operator) {
    if (actualValue == null) {
      return false;
    }
    if (operator == null || "eq".equalsIgnoreCase(operator)) {
      return correlationValue.equals(actualValue);
    }
    if ("contains".equalsIgnoreCase(operator)) {
      return StringUtils.containsIgnoreCase(correlationValue, actualValue);
    }
    throw new InvalidRequestException(
        String.format("Unsupported correlation operator '%s'. Supported operators: eq, contains", operator));
  }

  private boolean matchesCorrelation(
      EntityMappedEntityResponse entityMappedEntityResponse, EntityResponse entityResponse) {
    String correlationField = entityMappedEntityResponse.getCorrelationField();
    if (!StringUtils.isBlank(correlationField)) {
      String path = correlationField.startsWith(".") ? correlationField.substring(1) : correlationField;
      String correlationValue = CommonUtils.from(entityMappedEntityResponse.getData(), path, String.class);
      if (StringUtils.isBlank(correlationValue)) {
        log.debug("Correlation value not found for path '{}' in integration entity data", correlationField);
        return false;
      }
      return matchesCorrelationFieldInEntity(entityResponse, correlationField, correlationValue);
    }
    if (entityMappedEntityResponse.hasCorrelationMapping()) {
      EntityMappedEntityResponse.CorrelationMapping mapping = entityMappedEntityResponse.getCorrelationMapping();
      String sourcePath =
          mapping.getSourcePath().startsWith(".") ? mapping.getSourcePath().substring(1) : mapping.getSourcePath();
      String correlationValue = CommonUtils.from(entityMappedEntityResponse.getData(), sourcePath, String.class);
      if (StringUtils.isBlank(correlationValue)) {
        log.debug("Correlation value not found for path '{}' in integration entity data", mapping.getSourcePath());
        return false;
      }
      return matchesCorrelationFieldInEntity(
          entityResponse, mapping.getDestinationPath(), correlationValue, mapping.getOperator());
    }
    // Fallback to identifier matching when no correlation field is configured
    String discoveredIdentifier = entityMappedEntityResponse.getEntityInfo().getIdentifier();
    if (StringUtils.isBlank(discoveredIdentifier)) {
      return false;
    }
    String entityRefParts = entityResponse.getEntityRef();
    if (StringUtils.isBlank(entityRefParts) || !entityRefParts.contains("/")) {
      return false;
    }
    String catalogIdentifier = entityRefParts.split("/")[1];
    return discoveredIdentifier.equalsIgnoreCase(catalogIdentifier);
  }

  private Optional<DiscoverEntitiesResponse> prepareDiscoverEntitiesResponse(
      EntityMappedEntityResponse entityMappedEntityResponse, List<EntityResponse> entityResponses, String spacePath,
      String integrationId, boolean allowCrossScopeCorrelation,
      TypesIntegrationConfig.EnumIntegrationType integrationType, String configAction,
      CorrelationLookup correlationLookup, List<String> includeFields, List<String> includePaths,
      Map<String, List<SourceTargetFieldMapping>> fieldMappingsPerKind) {
    String kind = (String) entityMappedEntityResponse.getData().get(MAPPED_ENTITY_RESPONSE_KIND_KEY);
    String type = (String) entityMappedEntityResponse.getData().get(MAPPED_ENTITY_RESPONSE_TYPE_KEY);
    String scope = entityMappedEntityResponse.getScope().toString();
    String identifier = entityMappedEntityResponse.getEntityInfo().getIdentifier();
    DiscoverEntitiesResponse discoverEntitiesResponse = new DiscoverEntitiesResponse();
    discoverEntitiesResponse.setIntegrationEntityId(entityMappedEntityResponse.getUuid());
    discoverEntitiesResponse.setKind(kind);
    discoverEntitiesResponse.setScope(scope);
    discoverEntitiesResponse.setIdentifier(identifier);
    String entityRef = kind + ":" + scope + "/" + identifier;

    discoverEntitiesResponse.setType(type);
    discoverEntitiesResponse.setName(entityMappedEntityResponse.getName());
    if (isNotEmpty(includeFields)) {
      discoverEntitiesResponse.setFields(extractIncludedFields(entityMappedEntityResponse.getData(),
          entityMappedEntityResponse.getKind(), fieldMappingsPerKind, includeFields));
    }
    if (isNotEmpty(includePaths)) {
      discoverEntitiesResponse.setPaths(extractIncludedPaths(entityMappedEntityResponse.getData(), includePaths));
    }
    DiscoverEntitiesResponseActionDestination discoverEntitiesResponseActionDestination =
        new DiscoverEntitiesResponseActionDestination();

    List<EntityResponse> correlatedEntities = lookupCorrelatedEntities(entityMappedEntityResponse, correlationLookup,
        kind, type, scope, allowCrossScopeCorrelation, spacePath, integrationId);

    if ("Register".equals(configAction)) {
      // Register only — skip merge computation, always set REGISTER as default
      discoverEntitiesResponseActionDestination.setDefaultAction(
          DiscoverEntitiesResponseActionDestination.DefaultActionEnum.REGISTER);
      DiscoverEntitiesResponseActionDestinationRegister discoverEntitiesResponseActionDestinationRegister =
          new DiscoverEntitiesResponseActionDestinationRegister();
      discoverEntitiesResponseActionDestinationRegister.setName(discoverEntitiesResponse.getName());
      discoverEntitiesResponseActionDestination.setRegister(discoverEntitiesResponseActionDestinationRegister);
      discoverEntitiesResponseActionDestination.setMerge(List.of());
      discoverEntitiesResponseActionDestination.setMergeSuggestions(List.of());
    } else if ("Merge".equals(configAction)) {
      // Merge only — compute merge candidates, always set MERGE as default, skip register destination
      List<DiscoverEntitiesResponseActionDestinationMerge> merge =
          correlatedEntities.stream()
              .map(er -> {
                DiscoverEntitiesResponseActionDestinationMerge d = new DiscoverEntitiesResponseActionDestinationMerge();
                d.setEntityRef(er.getEntityRef());
                d.setName(er.getName());
                return d;
              })
              .sorted((a, b) -> {
                if (a.getEntityRef().equals(entityRef)) {
                  return -1;
                }
                if (b.getEntityRef().equals(entityRef)) {
                  return 1;
                }
                return 0;
              })
              .toList();
      discoverEntitiesResponseActionDestination.setDefaultAction(
          DiscoverEntitiesResponseActionDestination.DefaultActionEnum.MERGE);
      discoverEntitiesResponseActionDestination.setMerge(merge);

      // HarnessCD and HarnessCI expose merge suggestions per discovered entity.
      List<DiscoverEntitiesResponseActionDestinationMerge> perEntityMergeSuggestions = List.of();
      if (usesPerEntityMergeSuggestions(integrationType)) {
        perEntityMergeSuggestions =
            filterByKindTypeScope(entityResponses, kind, type, scope, allowCrossScopeCorrelation)
                .filter(er -> !isAlreadyLinkedToEntity(er, spacePath, integrationId, entityMappedEntityResponse))
                .map(er -> {
                  DiscoverEntitiesResponseActionDestinationMerge suggestion =
                      new DiscoverEntitiesResponseActionDestinationMerge();
                  suggestion.setEntityRef(er.getEntityRef());
                  suggestion.setName(er.getName());
                  return suggestion;
                })
                .toList();
      }
      discoverEntitiesResponseActionDestination.setMergeSuggestions(perEntityMergeSuggestions);
    } else {
      // Default case (null / "Merge_Register") — original logic unchanged
      discoverEntitiesResponseActionDestination.setDefaultAction(
          DiscoverEntitiesResponseActionDestination.DefaultActionEnum.REGISTER);
      DiscoverEntitiesResponseActionDestinationRegister discoverEntitiesResponseActionDestinationRegister =
          new DiscoverEntitiesResponseActionDestinationRegister();
      discoverEntitiesResponseActionDestinationRegister.setName(discoverEntitiesResponse.getName());
      discoverEntitiesResponseActionDestination.setRegister(discoverEntitiesResponseActionDestinationRegister);

      List<DiscoverEntitiesResponseActionDestinationMerge> merge =
          correlatedEntities.stream()
              .map(er -> {
                DiscoverEntitiesResponseActionDestinationMerge d = new DiscoverEntitiesResponseActionDestinationMerge();
                d.setEntityRef(er.getEntityRef());
                d.setName(er.getName());
                return d;
              })
              .sorted((a, b) -> {
                if (a.getEntityRef().equals(entityRef)) {
                  return -1;
                }
                if (b.getEntityRef().equals(entityRef)) {
                  return 1;
                }
                return 0;
              })
              .toList();

      if (!merge.isEmpty()) {
        discoverEntitiesResponseActionDestination.setDefaultAction(
            DiscoverEntitiesResponseActionDestination.DefaultActionEnum.MERGE);
      }

      discoverEntitiesResponseActionDestination.setMerge(merge);

      // HarnessCD and HarnessCI expose merge suggestions per discovered entity.
      List<DiscoverEntitiesResponseActionDestinationMerge> perEntityMergeSuggestions = List.of();
      if (usesPerEntityMergeSuggestions(integrationType)) {
        perEntityMergeSuggestions =
            filterByKindTypeScope(entityResponses, kind, type, scope, allowCrossScopeCorrelation)
                .filter(er -> !isAlreadyLinkedToEntity(er, spacePath, integrationId, entityMappedEntityResponse))
                .map(er -> {
                  DiscoverEntitiesResponseActionDestinationMerge suggestion =
                      new DiscoverEntitiesResponseActionDestinationMerge();
                  suggestion.setEntityRef(er.getEntityRef());
                  suggestion.setName(er.getName());
                  return suggestion;
                })
                .toList();
      }
      discoverEntitiesResponseActionDestination.setMergeSuggestions(perEntityMergeSuggestions);
    }

    discoverEntitiesResponse.setActionDestination(discoverEntitiesResponseActionDestination);
    discoverEntitiesResponse.setDiscoveredAt(entityMappedEntityResponse.getDetectedAt());
    return Optional.of(discoverEntitiesResponse);
  }

  /**
   * Resolves {@code include_fields} through {@code field_mappings_per_kind} only. A missing mapping or missing
   * target path is {@code null}; there is no integration-properties fallback.
   */
  private Map<String, Object> extractIncludedFields(Map<String, Object> mappedEntityData, String integrationKind,
      Map<String, List<SourceTargetFieldMapping>> fieldMappingsPerKind, List<String> includeFields) {
    Map<String, Object> fields = new LinkedHashMap<>();
    includeFields.forEach(field -> fields.put(field, null));
    if (isEmpty(mappedEntityData)) {
      return fields;
    }
    List<SourceTargetFieldMapping> kindMappings = lookupKindFieldMappings(fieldMappingsPerKind, integrationKind);
    for (String field : includeFields) {
      String targetField = resolveTargetField(kindMappings, field);
      fields.put(field, isNotEmpty(targetField) ? readDottedPath(mappedEntityData, targetField) : null);
    }
    return fields;
  }

  /** Reads {@code include_paths} from mapped-entity {@code data}. The response key is the path token as requested. */
  private Map<String, Object> extractIncludedPaths(Map<String, Object> mappedEntityData, List<String> includePaths) {
    Map<String, Object> paths = new LinkedHashMap<>();
    includePaths.forEach(path -> paths.put(path, null));
    if (isEmpty(mappedEntityData)) {
      return paths;
    }
    for (String path : includePaths) {
      paths.put(path, readDottedPath(mappedEntityData, path));
    }
    return paths;
  }

  private Map<String, List<SourceTargetFieldMapping>> parseFieldMappingsPerKind(
      TypesIntegrationConfig integrationConfig) {
    if (integrationConfig == null || isEmpty(integrationConfig.getConfiguration())) {
      return Map.of();
    }
    Object rawMappings = integrationConfig.getConfiguration().get("field_mappings_per_kind");
    if (!(rawMappings instanceof Map<?, ?> mappingsByKind)) {
      return Map.of();
    }
    Map<String, List<SourceTargetFieldMapping>> parsed = new LinkedHashMap<>();
    mappingsByKind.forEach((kindKey, mappings) -> {
      if (kindKey == null || !(mappings instanceof List<?> mappingList)) {
        return;
      }
      List<SourceTargetFieldMapping> kindMappings = new ArrayList<>();
      for (Object mapping : mappingList) {
        if (!(mapping instanceof Map<?, ?> mappingMap)) {
          continue;
        }
        String sourceField = stringField(mappingMap, "source_field");
        String targetField = stringField(mappingMap, "target_field");
        if (isNotEmpty(sourceField) || isNotEmpty(targetField)) {
          kindMappings.add(new SourceTargetFieldMapping(sourceField, targetField));
        }
      }
      if (isNotEmpty(kindMappings)) {
        parsed.put(String.valueOf(kindKey), kindMappings);
      }
    });
    return parsed;
  }

  private List<SourceTargetFieldMapping> lookupKindFieldMappings(
      Map<String, List<SourceTargetFieldMapping>> fieldMappingsPerKind, String integrationKind) {
    if (isEmpty(fieldMappingsPerKind) || isEmpty(integrationKind)) {
      return List.of();
    }
    List<SourceTargetFieldMapping> kindMappings = fieldMappingsPerKind.get(integrationKind);
    if (kindMappings != null) {
      return kindMappings;
    }
    return fieldMappingsPerKind.entrySet()
        .stream()
        .filter(entry -> integrationKind.equalsIgnoreCase(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(List.of());
  }

  private String resolveTargetField(List<SourceTargetFieldMapping> kindMappings, String includeField) {
    if (isEmpty(kindMappings) || isEmpty(includeField)) {
      return null;
    }
    for (SourceTargetFieldMapping mapping : kindMappings) {
      if (includeField.equals(mapping.getSourceField()) && isNotEmpty(mapping.getTargetField())) {
        return mapping.getTargetField();
      }
    }
    return null;
  }

  private Object readDottedPath(Map<String, Object> data, String path) {
    Object current = data;
    for (String part : path.split("\\.")) {
      if (!(current instanceof Map<?, ?> currentMap)) {
        return null;
      }
      current = currentMap.get(part);
    }
    return current;
  }

  private String stringField(Map<?, ?> mappingMap, String key) {
    Object value = mappingMap.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private boolean isAlreadyLinkedToEntity(EntityResponse er, String spacePath, String integrationId,
      EntityMappedEntityResponse entityMappedEntityResponse) {
    if (er.getMetadata() instanceof Map<?, ?> metadataMap
        && metadataMap.get(CatalogIntegrationEntityConstants.INTEGRATION_PATH_PREFIX)
                instanceof Map<?, ?> integrationMap
        && integrationMap.get(spacePath) instanceof Map<?, ?> spacePathMap
        && spacePathMap.get(integrationId) instanceof Map<?, ?> integrationIdMap
        && integrationIdMap.get(entityMappedEntityResponse.getKind()) instanceof Map<?, ?> kindMap) {
      String targetUuid = entityMappedEntityResponse.getUuid();
      if (targetUuid.equals(kindMap.get("entity_uuid"))) {
        return true;
      }
      for (Object value : kindMap.values()) {
        if (value instanceof Map && targetUuid.equals(((Map<?, ?>) value).get("entity_uuid"))) {
          return true;
        }
      }
    }
    return false;
  }

  private Stream<EntityResponse> filterByKindTypeScope(List<EntityResponse> entityResponses, String kind, String type,
      String scope, boolean allowCrossScopeCorrelation) {
    return entityResponses.stream()
        .filter(er -> er.getKindIdentifier().equalsIgnoreCase(kind))
        .filter(er -> er.getType().equalsIgnoreCase(type))
        .filter(er -> {
          if (!allowCrossScopeCorrelation) {
            return er.getEntityRef().split(":")[1].split("/")[0].equalsIgnoreCase(scope);
          }
          return true;
        });
  }

  private static class CorrelationLookup {
    final Set<String> targetPaths;
    final Map<String, Map<String, List<EntityResponse>>> pathIndex;
    final Map<String, List<EntityResponse>> identifierIndex;

    CorrelationLookup(Set<String> targetPaths, Map<String, Map<String, List<EntityResponse>>> pathIndex,
        Map<String, List<EntityResponse>> identifierIndex) {
      this.targetPaths = targetPaths;
      this.pathIndex = pathIndex;
      this.identifierIndex = identifierIndex;
    }
  }

  private CorrelationLookup buildCorrelationLookup(
      List<EntityResponse> entityResponses, List<EntityMappedEntityResponse> entityMappedEntityResponses) {
    Set<String> targetPaths = new HashSet<>();
    boolean hasIdentifierFallback = false;

    for (EntityMappedEntityResponse emr : entityMappedEntityResponses) {
      String corrField = emr.getCorrelationField();
      if (!StringUtils.isBlank(corrField)) {
        targetPaths.add(corrField.startsWith(".") ? corrField.substring(1) : corrField);
      } else if (emr.hasCorrelationMapping()) {
        String dest = emr.getCorrelationMapping().getDestinationPath();
        targetPaths.add(dest.startsWith(".") ? dest.substring(1) : dest);
      } else {
        hasIdentifierFallback = true;
      }
    }

    Map<String, Map<String, List<EntityResponse>>> pathIndex = new HashMap<>();
    for (String path : targetPaths) {
      pathIndex.put(path, new HashMap<>());
    }
    Map<String, List<EntityResponse>> identifierIndex = new HashMap<>();

    for (EntityResponse er : entityResponses) {
      Map<String, Object> decoratorMap = null;
      Map<String, Object> yamlMap = null;

      String decorator = er.getDecorator();
      if (!StringUtils.isBlank(decorator)) {
        try {
          decoratorMap = YamlUtils.loadYamlStringAsMap(decorator);
        } catch (Exception ignored) {
        }
      }
      String yaml = er.getYaml();
      if (!StringUtils.isBlank(yaml)) {
        try {
          yamlMap = YamlUtils.loadYamlStringAsMap(yaml);
        } catch (Exception ignored) {
        }
      }

      for (String path : targetPaths) {
        String value = null;
        if (decoratorMap != null) {
          value = CommonUtils.from(decoratorMap, path, String.class);
        }
        if (value == null && yamlMap != null) {
          value = CommonUtils.from(yamlMap, path, String.class);
        }
        if (value != null) {
          pathIndex.get(path).computeIfAbsent(value, k -> new ArrayList<>()).add(er);
        }
      }

      if (hasIdentifierFallback) {
        String entityRefStr = er.getEntityRef();
        if (!StringUtils.isBlank(entityRefStr) && entityRefStr.contains("/")) {
          String catalogId = entityRefStr.split("/")[1];
          identifierIndex.computeIfAbsent(catalogId.toLowerCase(), k -> new ArrayList<>()).add(er);
        }
      }
    }

    return new CorrelationLookup(targetPaths, pathIndex, identifierIndex);
  }

  private List<EntityResponse> lookupCorrelatedEntities(EntityMappedEntityResponse emr, CorrelationLookup lookup,
      String kind, String type, String scope, boolean allowCrossScopeCorrelation, String spacePath,
      String integrationId) {
    List<EntityResponse> candidates;

    String correlationField = emr.getCorrelationField();
    if (!StringUtils.isBlank(correlationField)) {
      String path = correlationField.startsWith(".") ? correlationField.substring(1) : correlationField;
      String correlationValue = CommonUtils.from(emr.getData(), path, String.class);
      if (StringUtils.isBlank(correlationValue)) {
        return List.of();
      }
      candidates = lookup.pathIndex.getOrDefault(path, Map.of()).getOrDefault(correlationValue, List.of());
    } else if (emr.hasCorrelationMapping()) {
      EntityMappedEntityResponse.CorrelationMapping mapping = emr.getCorrelationMapping();
      String sourcePath =
          mapping.getSourcePath().startsWith(".") ? mapping.getSourcePath().substring(1) : mapping.getSourcePath();
      String correlationValue = CommonUtils.from(emr.getData(), sourcePath, String.class);
      if (StringUtils.isBlank(correlationValue)) {
        return List.of();
      }
      String destPath = mapping.getDestinationPath().startsWith(".") ? mapping.getDestinationPath().substring(1)
                                                                     : mapping.getDestinationPath();

      if ("contains".equalsIgnoreCase(mapping.getOperator())) {
        Map<String, List<EntityResponse>> valueMap = lookup.pathIndex.getOrDefault(destPath, Map.of());
        candidates = valueMap.entrySet()
                         .stream()
                         .filter(e -> StringUtils.containsIgnoreCase(correlationValue, e.getKey()))
                         .flatMap(e -> e.getValue().stream())
                         .toList();
      } else {
        candidates = lookup.pathIndex.getOrDefault(destPath, Map.of()).getOrDefault(correlationValue, List.of());
      }
    } else {
      String discoveredIdentifier = emr.getEntityInfo().getIdentifier();
      if (StringUtils.isBlank(discoveredIdentifier)) {
        return List.of();
      }
      candidates = lookup.identifierIndex.getOrDefault(discoveredIdentifier.toLowerCase(), List.of());
    }

    return candidates.stream()
        .filter(er -> er.getKindIdentifier().equalsIgnoreCase(kind))
        .filter(er -> er.getType().equalsIgnoreCase(type))
        .filter(er -> {
          if (!allowCrossScopeCorrelation) {
            return er.getEntityRef().split(":")[1].split("/")[0].equalsIgnoreCase(scope);
          }
          return true;
        })
        .filter(er -> !isAlreadyLinkedToEntity(er, spacePath, integrationId, emr))
        .toList();
  }

  @lombok.Value
  @lombok.Builder
  private static class UnsubscribedIntegrationEntitiesResult {
    List<EntityMappedEntityResponse> entities;
    int totalElements;
    int totalPages;
    int pageNumber;
    int pageSize;
    Integer nextPage;
  }

  @lombok.Value
  private static class DiscoveryPreparationResult {
    List<DiscoverEntitiesResponse> discoverEntitiesResponses;
    List<DiscoverEntitiesResponseActionDestinationMerge> mergeSuggestions;
  }

  @lombok.Value
  private static class SourceTargetFieldMapping {
    String sourceField;
    String targetField;
  }

  @lombok.Value
  private static class OffsetMappedEntitiesResult {
    List<EntityMappedEntityResponse> entities;
    int requestedLimit;
  }

  private static Integer parseIntegerHeader(
      okhttp3.Headers headers, String name, Integer defaultValue, String context) {
    String value = headers.get(name);
    if (isEmpty(value)) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException nfe) {
      log.warn("Unable to parse header {} = {} ({})", name, value, context);
      return defaultValue;
    }
  }

  private static int parseIntHeader(okhttp3.Headers headers, String name, int defaultValue, String context) {
    Integer parsed = parseIntegerHeader(headers, name, null, context);
    return parsed != null ? parsed : defaultValue;
  }

  private OffsetMappedEntitiesResult getMappedEntitiesByOffset(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, String sortBy, String searchTerm, String kinds, int offset,
      int limit) {
    String sort = "name";
    String order = "asc";
    if (!isEmpty(sortBy)) {
      String[] sortParts = sortBy.split(",");
      sort = sortParts[0];
      if (sortParts.length > 1) {
        order = sortParts[1];
      }
    }
    OpenapiGetMappedEntitiesRequest request = new OpenapiGetMappedEntitiesRequest();
    List<String> kindsList = isEmpty(kinds) ? null : Arrays.asList(kinds.split(","));
    if (isNotEmpty(kindsList)) {
      request.setKinds(kindsList);
    }

    try {
      Response<EntityMappedEntityResponseObject> response = executeGeneralRequestWithRetry(
          integrationManagerClientHelper.getMappedEntitiesByOffset(accountIdentifier, accountIdentifier, orgIdentifier,
              projectIdentifier, integrationId, integrationManagerClientHelper.getIntegrationManagerIdpMappingId(),
              true, sort, order, offset, limit, searchTerm, request, false));
      if (!response.isSuccessful()) {
        throw new UnexpectedException(
            String.format("Unable to fetch HarnessCI mapped entities by offset. HTTP status = %s", response.code()));
      }
      List<EntityMappedEntityResponse> entities =
          response.body() != null && isNotEmpty(response.body().getItems()) ? response.body().getItems() : List.of();
      return new OffsetMappedEntitiesResult(entities, limit);
    } catch (UnexpectedException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("Error fetching integration entities by offset. Error = {}", ex.getMessage(), ex);
      throw new UnexpectedException("Error while fetching integration entities");
    }
  }

  private UnsubscribedIntegrationEntitiesResult getUnsubscribedIntegrationEntities(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String integrationId, boolean fetchAll, boolean detailed,
      List<String> integrationEntitiesUuids, String sortBy, String searchTerm, String kinds,
      Map<String, List<String>> parsedFilters, List<String> filterKinds, int page, int limit) {
    String sort = "name";
    String order = "asc";
    if (!isEmpty(sortBy)) {
      sort = sortBy.split(",")[0];
      order = sortBy.split(",")[1];
    }
    List<String> kindsList =
        isNotEmpty(parsedFilters) ? filterKinds : (isEmpty(kinds) ? null : Arrays.asList(kinds.split(",")));
    OpenapiGetMappedEntitiesRequest fetchAllRequest = new OpenapiGetMappedEntitiesRequest();
    if (isNotEmpty(kindsList)) {
      fetchAllRequest.setKinds(kindsList);
      log.info("getIntegrationEntities: Filtering by kinds = {} for account = {}, integrationId = {}", kindsList,
          accountIdentifier, integrationId);
    }
    if (isNotEmpty(parsedFilters)) {
      Map<String, List<OpenapiGetMappedEntitiesRequest.FieldValFilter>> fieldValsPerKind = new LinkedHashMap<>();
      for (String kind : filterKinds) {
        List<OpenapiGetMappedEntitiesRequest.FieldValFilter> fieldFilters =
            parsedFilters.entrySet()
                .stream()
                .map(entry -> new OpenapiGetMappedEntitiesRequest.FieldValFilter(entry.getKey(), entry.getValue()))
                .toList();
        fieldValsPerKind.put(kind, fieldFilters);
      }
      fetchAllRequest.setFieldValsPerKind(fieldValsPerKind);
    }
    try {
      Response<EntityMappedEntityResponseObject> entityMappedEntityResponseObjectResponse;
      List<EntityMappedEntityResponse> entityMappedEntityResponses = new ArrayList<>();
      entityMappedEntityResponseObjectResponse = executeGeneralRequestWithRetry(
          integrationManagerClientHelper.getMappedEntities(accountIdentifier, accountIdentifier, orgIdentifier,
              projectIdentifier, integrationId, integrationManagerClientHelper.getIntegrationManagerIdpMappingId(),
              detailed, sort, order, page, limit, searchTerm, fetchAllRequest, true));
      if (entityMappedEntityResponseObjectResponse.isSuccessful()
          && entityMappedEntityResponseObjectResponse.body() != null
          && isNotEmpty(entityMappedEntityResponseObjectResponse.body().getItems())) {
        entityMappedEntityResponses.addAll(entityMappedEntityResponseObjectResponse.body().getItems());
      }
      String headerContext = String.format("account=%s, integrationId=%s", accountIdentifier, integrationId);
      okhttp3.Headers headers = entityMappedEntityResponseObjectResponse.headers();
      int totalElements = parseIntHeader(headers, "x-total", entityMappedEntityResponses.size(), headerContext);
      int totalPages = parseIntHeader(headers, "x-total-pages", 0, headerContext);
      int pageNumber = parseIntHeader(headers, "x-page", page, headerContext);
      int pageSize = parseIntHeader(headers, "x-per-page", limit, headerContext);
      Integer nextPage = parseIntegerHeader(headers, "x-next-page", null, headerContext);
      log.info("getIntegrationEntities: Fetched {} entities (totalElements={}, totalPages={}, pageNumber={}, "
              + "pageSize={}, nextPage={}) for account={}, integrationId={}, kinds={}",
          entityMappedEntityResponses.size(), totalElements, totalPages, pageNumber, pageSize, nextPage,
          accountIdentifier, integrationId, kinds);
      return UnsubscribedIntegrationEntitiesResult.builder()
          .entities(entityMappedEntityResponses)
          .totalElements(totalElements)
          .totalPages(totalPages)
          .pageNumber(pageNumber)
          .pageSize(pageSize)
          .nextPage(nextPage)
          .build();
    } catch (Exception ex) {
      log.error("Error in getIntegrationEntities. Error = {}", ex.getMessage(), ex);
      throw new UnexpectedException("Error while fetching integration entities");
    }
  }

  private List<EntityMappedEntityResponse> getIntegrationEntities(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, boolean fetchAll, boolean detailed,
      List<String> integrationEntitiesUuids, String sortBy, String searchTerm, String kinds) {
    String sort = "name";
    String order = "asc";
    if (!isEmpty(sortBy)) {
      sort = sortBy.split(",")[0];
      order = sortBy.split(",")[1];
    }
    List<String> kindsList = isEmpty(kinds) ? null : Arrays.asList(kinds.split(","));
    try {
      Response<EntityMappedEntityResponseObject> entityMappedEntityResponseObjectResponse;
      List<EntityMappedEntityResponse> entityMappedEntityResponses = new ArrayList<>();

      if (fetchAll) {
        OpenapiGetMappedEntitiesRequest fetchAllRequest = new OpenapiGetMappedEntitiesRequest();
        if (isNotEmpty(kindsList)) {
          fetchAllRequest.setKinds(kindsList);
          log.info("getIntegrationEntities: Filtering by kinds = {} for account = {}, integrationId = {}", kindsList,
              accountIdentifier, integrationId);
        }
        int page = 0;
        do {
          entityMappedEntityResponseObjectResponse = executeGeneralRequestWithRetry(
              integrationManagerClientHelper.getMappedEntities(accountIdentifier, accountIdentifier, orgIdentifier,
                  projectIdentifier, integrationId, integrationManagerClientHelper.getIntegrationManagerIdpMappingId(),
                  detailed, sort, order, page, 2000, searchTerm, fetchAllRequest, false));
          if (entityMappedEntityResponseObjectResponse.isSuccessful()
              && entityMappedEntityResponseObjectResponse.body() != null
              && isNotEmpty(entityMappedEntityResponseObjectResponse.body().getItems())) {
            entityMappedEntityResponses.addAll(entityMappedEntityResponseObjectResponse.body().getItems());
          }
          page++;
        } while (Integer.parseInt(entityMappedEntityResponseObjectResponse.headers().get("X-Page"))
            <= Integer.parseInt(entityMappedEntityResponseObjectResponse.headers().get("X-Total-Pages")));
      } else {
        OpenapiGetMappedEntitiesRequest getMappedEntitiesRequest = new OpenapiGetMappedEntitiesRequest();
        List<OpenapiGetMappedEntitiesRequest.OpenapiEntityFilterRequest> entityFilterRequests = new ArrayList<>();
        integrationEntitiesUuids.forEach(integrationEntitiesUuid -> {
          OpenapiGetMappedEntitiesRequest.OpenapiEntityFilterRequest entityFilterRequest =
              new OpenapiGetMappedEntitiesRequest.OpenapiEntityFilterRequest();
          entityFilterRequest.setUuid(integrationEntitiesUuid);
          entityFilterRequests.add(entityFilterRequest);
        });
        getMappedEntitiesRequest.setIdentifiers(entityFilterRequests);
        if (isNotEmpty(kindsList)) {
          getMappedEntitiesRequest.setKinds(kindsList);
          log.info(
              "getIntegrationEntities: Filtering by kinds = {} for account = {}, integrationId = {} (non-fetchAll)",
              kindsList, accountIdentifier, integrationId);
        }
        int page = 0;
        do {
          entityMappedEntityResponseObjectResponse = executeGeneralRequestWithRetry(
              integrationManagerClientHelper.getMappedEntities(accountIdentifier, accountIdentifier, orgIdentifier,
                  projectIdentifier, integrationId, integrationManagerClientHelper.getIntegrationManagerIdpMappingId(),
                  detailed, sort, order, page, 2000, searchTerm, getMappedEntitiesRequest, false));
          if (entityMappedEntityResponseObjectResponse.isSuccessful()
              && entityMappedEntityResponseObjectResponse.body() != null
              && isNotEmpty(entityMappedEntityResponseObjectResponse.body().getItems())) {
            entityMappedEntityResponses.addAll(entityMappedEntityResponseObjectResponse.body().getItems());
          }
          page++;
        } while (Integer.parseInt(entityMappedEntityResponseObjectResponse.headers().get("X-Page"))
            <= Integer.parseInt(entityMappedEntityResponseObjectResponse.headers().get("X-Total-Pages")));
      }
      log.info("getIntegrationEntities: Fetched {} entities for account = {}, integrationId = {}, kinds = {}",
          entityMappedEntityResponses.size(), accountIdentifier, integrationId, kinds);
      return entityMappedEntityResponses;
    } catch (Exception ex) {
      log.error("Error in getIntegrationEntities. Error = {}", ex.getMessage(), ex);
      throw new UnexpectedException("Error while fetching integration entities");
    }
  }

  private List<EntityMappedEntityResponse> getHarnessCIIntegrationEntities(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String integrationId, boolean fetchAll, boolean detailed,
      List<String> integrationEntitiesUuids) {
    List<EntityMappedEntityResponse> entityMappedEntityResponses = new ArrayList<>();
    Set<String> requestedUuids = new HashSet<>(integrationEntitiesUuids);
    Set<String> foundUuids = new HashSet<>();
    int offset = 0;
    int limit = 2000;

    do {
      OpenapiGetMappedEntitiesRequest request = new OpenapiGetMappedEntitiesRequest();
      if (!fetchAll) {
        List<OpenapiGetMappedEntitiesRequest.OpenapiEntityFilterRequest> entityFilters =
            integrationEntitiesUuids.stream()
                .map(uuid -> {
                  OpenapiGetMappedEntitiesRequest.OpenapiEntityFilterRequest filter =
                      new OpenapiGetMappedEntitiesRequest.OpenapiEntityFilterRequest();
                  filter.setUuid(uuid);
                  return filter;
                })
                .toList();
        request.setIdentifiers(entityFilters);
      }

      try {
        Response<EntityMappedEntityResponseObject> response =
            executeGeneralRequestWithRetry(integrationManagerClientHelper.getMappedEntitiesByOffset(accountIdentifier,
                accountIdentifier, orgIdentifier, projectIdentifier, integrationId,
                integrationManagerClientHelper.getIntegrationManagerIdpMappingId(), detailed, "name", "asc", offset,
                limit, null, request, false));
        if (!response.isSuccessful()) {
          throw new UnexpectedException(
              String.format("Unable to fetch HarnessCI mapped entities. HTTP status = %s", response.code()));
        }

        List<EntityMappedEntityResponse> window =
            response.body() != null && isNotEmpty(response.body().getItems()) ? response.body().getItems() : List.of();
        if (fetchAll) {
          entityMappedEntityResponses.addAll(window);
        } else {
          window.stream()
              .filter(entity -> requestedUuids.contains(entity.getUuid()) && foundUuids.add(entity.getUuid()))
              .forEach(entityMappedEntityResponses::add);
        }

        boolean allRequestedEntitiesFound = !fetchAll && foundUuids.containsAll(requestedUuids);
        if (allRequestedEntitiesFound || window.size() < limit) {
          break;
        }
        offset += window.size();
      } catch (UnexpectedException ex) {
        throw ex;
      } catch (Exception ex) {
        log.error("Error fetching HarnessCI integration entities. Error = {}", ex.getMessage(), ex);
        throw new UnexpectedException("Error while fetching HarnessCI integration entities", ex);
      }
    } while (true);

    log.info("Fetched {} HarnessCI integration entities for account = {}, integrationId = {}, requestedUuids = {}",
        entityMappedEntityResponses.size(), accountIdentifier, integrationId,
        fetchAll ? "ALL" : integrationEntitiesUuids.size());
    return entityMappedEntityResponses;
  }

  /**
   * Fetches mapped entities from integration manager for the given UUIDs (to resolve relation targets).
   * Returns a map of uuid -> EntityMappedEntityResponse so we can get each target's integration kind and build
   * the correct linkage path per entity.
   */
  private Map<String, EntityMappedEntityResponse> getMappedEntitiesForUuids(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String integrationId, List<String> uuids) {
    if (isEmpty(uuids)) {
      return Collections.emptyMap();
    }
    List<String> distinctUuids = uuids.stream().distinct().collect(Collectors.toList());
    List<EntityMappedEntityResponse> responses = getIntegrationEntities(accountIdentifier, orgIdentifier,
        projectIdentifier, integrationId, false, true, distinctUuids, null, null, null);
    Map<String, EntityMappedEntityResponse> uuidToMapped = new HashMap<>();
    for (EntityMappedEntityResponse r : responses) {
      if (r != null && r.getUuid() != null) {
        uuidToMapped.put(r.getUuid(), r);
      }
    }
    return uuidToMapped;
  }

  /**
   * Builds linkage path per UUID using the target entity's integration kind (entity kind from mapped response).
   * UUIDs not found in uuidToMappedEntity use the default linkage path (current entity's path).
   */
  private Map<String, String> buildLinkagePathPerUuid(List<String> uuids,
      Map<String, EntityMappedEntityResponse> uuidToMappedEntity, String spacePath, String integrationId,
      String defaultLinkagePath) {
    Map<String, String> uuidToPath = new HashMap<>();
    for (String uuid : uuids) {
      EntityMappedEntityResponse mapped = uuidToMappedEntity.get(uuid);
      if (mapped != null && mapped.getKind() != null) {
        uuidToPath.put(uuid, buildLinkageConfigPath(spacePath, integrationId, mapped.getKind()));
      } else {
        uuidToPath.put(uuid, defaultLinkagePath);
      }
    }
    return uuidToPath;
  }

  private void validateIdpMappingExistsForIntegrationEntitiesKinds(
      Set<String> integrationEntitiesKinds, Map<String, TypesEntityMapping> integrationEntityKindToIdpMapping) {
    integrationEntitiesKinds.forEach(integrationEntityKind -> {
      if (!integrationEntityKindToIdpMapping.containsKey(integrationEntityKind)) {
        throw new IllegalArgumentException(
            "Error in discovering entities. Kind mapping not found kind = " + integrationEntityKind);
      }
    });
  }

  private String prepareCatalogEntityFromIntegrationEntityData(EntityMappedEntityResponse entityMappedEntityResponse,
      String kind, String type, Map<String, Object> catalogIntegrationEntityAdditionalLinkageConfig) {
    String template;
    List<KindEntity> kindEntities = kindEntityRepository.findAllByAccountIdentifierInAndIdentifierIn(
        List.of(entityMappedEntityResponse.getScope().getAccountIdentifier(), GLOBAL_ACCOUNT_ID),
        Collections.singletonList(kind));
    if (isEmpty(kindEntities)) {
      throw new UnexpectedException("Kind " + kind + " not found");
    }
    KindEntity kindEntity = kindEntities.get(0);
    String kindTemplate = kind;
    if (kindEntity.getKindType().equals(KindType.CUSTOM)) {
      kindTemplate = "custom";
    }
    try {
      template = entityTemplateCache.get(kindTemplate + ".v1");
    } catch (ExecutionException e) {
      throw new UnexpectedException(e.getMessage());
    }

    Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(template);
    yamlMap.put("kind", kind);
    yamlMap.put("accountIdentifier", entityMappedEntityResponse.getScope().getAccountIdentifier());
    if (!isEmpty(entityMappedEntityResponse.getScope().getOrgIdentifier())) {
      yamlMap.put("orgIdentifier", entityMappedEntityResponse.getScope().getOrgIdentifier());
      yamlMap.put("orgDetails", "orgs/" + entityMappedEntityResponse.getScope().getOrgIdentifier() + "/");
    }
    if (!isEmpty(entityMappedEntityResponse.getScope().getProjectIdentifier())) {
      yamlMap.put("projectIdentifier", entityMappedEntityResponse.getScope().getProjectIdentifier());
      yamlMap.put("projectDetails", "projects/" + entityMappedEntityResponse.getScope().getProjectIdentifier() + "/");
    }
    yamlMap.put("identifier", entityMappedEntityResponse.getData().get("identifier"));
    yamlMap.put("name", entityMappedEntityResponse.getName());
    yamlMap.put("type", type);

    Map<String, Object> data = entityMappedEntityResponse.getData();

    if (isNotEmpty(data)) {
      Object owner = data.get("owner");
      if (owner != null) {
        yamlMap.put("owner", owner);
      }

      Object originSpec = data.get("spec");
      if (originSpec instanceof Map) {
        Map<String, Object> spec = (Map<String, Object>) yamlMap.computeIfAbsent("spec", k -> new HashMap<>());
        // Extract relation arrays from spec so RelationsProcessor can process them
        for (String relationKey : List.of("partOf", "dependsOn", "dependencyOf", "hasPart")) {
          Object relationValue = ((Map<?, ?>) originSpec).get(relationKey);
          if (relationValue != null) {
            spec.put(relationKey, relationValue);
          }
        }
        deepMerge(spec, (Map<String, Object>) originSpec);
      }

      Object metadata = data.get("metadata");
      if (metadata instanceof Map) {
        Map<String, Object> metadataMap =
            (Map<String, Object>) yamlMap.computeIfAbsent("metadata", k -> new HashMap<>());
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) metadata).entrySet()) {
          if (!CatalogIntegrationEntityConstants.INTEGRATION_PROPERTIES.equals(entry.getKey())
              && !"kubernetes".equals(entry.getKey())
              && !(METADATA_TAGS_KEY.equals(entry.getKey()) && entry.getValue() == null)) {
            metadataMap.put(entry.getKey(), entry.getValue());
          }
        }
      }
    }

    if (isNotEmpty(catalogIntegrationEntityAdditionalLinkageConfig)) {
      String catalogIntegrationEntityAdditionalLinkageConfigYaml =
          YamlUtils.writeObjectAsYaml(catalogIntegrationEntityAdditionalLinkageConfig);

      StringLookup interpolator = StringLookupFactory.INSTANCE.interpolatorStringLookup(yamlMap);
      StringSubstitutor substitutor = new StringSubstitutor(interpolator);
      substitutor.setEnableSubstitutionInVariables(true);
      catalogIntegrationEntityAdditionalLinkageConfigYaml =
          substitutor.replace(catalogIntegrationEntityAdditionalLinkageConfigYaml);

      Map<String, Object> linkageConfig =
          YamlUtils.loadYamlStringAsMap(catalogIntegrationEntityAdditionalLinkageConfigYaml);

      yamlMap = mergeIgnoringEmpty(yamlMap, linkageConfig);
      applyDataMerge(yamlMap, yamlMap);
    }

    if (entityMappedEntityResponse.getData().get("modifiedIdentifier") != null
        && entityMappedEntityResponse.getData().get("modifiedName") != null) {
      yamlMap.put("identifier", entityMappedEntityResponse.getData().get("modifiedIdentifier"));
      yamlMap.put("name", entityMappedEntityResponse.getData().get("modifiedName"));
    }

    yamlMap.remove("accountIdentifier");
    yamlMap.remove("orgDetails");
    yamlMap.remove("projectDetails");

    return YamlUtils.writeObjectAsYaml(yamlMap);
  }

  private void subscribeForIntegrationEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String integrationId, String kind, String integrationEntityUuid, UserPrincipal userPrincipal) {
    Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    try {
      if (userPrincipal != null) {
        SourcePrincipalContextBuilder.setSourcePrincipal(new io.harness.security.dto.UserPrincipal(
            userPrincipal.getUuid(), userPrincipal.getEmail(), userPrincipal.getName(), accountIdentifier));
      }
      OpenapiSubscribeEntitiesRequest subscribeEntitiesRequest =
          prepareSubscribeEntitiesRequest(kind, integrationEntityUuid);
      getGeneralResponse(integrationManagerClientHelper.subscribeToEntityUpdates(accountIdentifier, accountIdentifier,
          orgIdentifier, projectIdentifier, integrationId, subscribeEntitiesRequest));
    } catch (Exception ex) {
      log.error("Error in subscribeForIntegrationEntity. Exception = {}", ex.getMessage(), ex);
      throw new UnexpectedException("Error in integration entity subscribe");
    } finally {
      SourcePrincipalContextBuilder.setSourcePrincipal(sourcePrincipal);
    }
  }

  private void configureIntegrationAutoDiscoveryAsPerRequest(String accountIdentifier, String integrationOrgIdentifier,
      String integrationProjectIdentifier, String integrationId, TypesIntegrationConfig integrationConfig,
      boolean autoDiscover) {
    try {
      OpenapiUpdateIntegrationConfigRequest updateIntegrationConfigRequest =
          new OpenapiUpdateIntegrationConfigRequest();
      Map<String, Object> configuration = integrationConfig.getConfiguration();
      configuration.put("auto_import", autoDiscover);
      updateIntegrationConfigRequest.setEnabled(integrationConfig.isEnabled());
      updateIntegrationConfigRequest.setConfiguration(configuration);
      getGeneralResponse(integrationManagerClientHelper.updateIntegrationConfig(accountIdentifier, accountIdentifier,
          integrationOrgIdentifier, integrationProjectIdentifier, integrationId, updateIntegrationConfigRequest));
    } catch (Exception ex) {
      log.error("Error in configureIntegrationAutoDiscoveryAsPerRequest. Exception = {}", ex.getMessage(), ex);
      throw new UnexpectedException("Error in setting integration auto discovery config");
    }
  }

  private String normalizeSpacePath(String spacePath, String accountIdentifier) {
    return spacePath.replace(accountIdentifier, "account");
  }

  /**
   * Maps Integration Manager data.kind values to the kind stored in the catalog for Discover
   * correlation lookups. Backstage Template registers as Workflow; without this remap, Discover
   * queries kind=template, finds no catalog entities, and never correlates already-imported rows.
   * Display kind on Discover responses is unchanged — callers pass the remapped value only into
   * getEntities / getEntitiesV2.
   */
  @VisibleForTesting
  String toCatalogKindForDiscoverLookup(String kind) {
    if (kind == null) {
      return null;
    }
    return "template".equalsIgnoreCase(kind) ? "workflow" : kind;
  }

  /**
   * Returns true when the converted CatalogInfo YAML would not change the linked catalog entity.
   * Kind is compared case-insensitively because the converter emits display casing while persisted
   * YAML is lowercase. Parse failures return false so a refresh is attempted rather than skipped.
   */
  @VisibleForTesting
  boolean catalogInfoYamlContentEquivalent(String candidateYaml, String existingYaml) {
    if (StringUtils.isBlank(candidateYaml) || StringUtils.isBlank(existingYaml)) {
      return false;
    }
    try {
      Map<String, Object> candidate =
          normalizeCatalogInfoYamlForComparison(YamlUtils.loadYamlStringAsMap(candidateYaml));
      Map<String, Object> existing = normalizeCatalogInfoYamlForComparison(YamlUtils.loadYamlStringAsMap(existingYaml));
      return Objects.equals(candidate, existing);
    } catch (Exception ex) {
      log.debug("Unable to compare CatalogInfo YAML for equivalence: {}", ex.getMessage());
      return false;
    }
  }

  private Map<String, Object> normalizeCatalogInfoYamlForComparison(Map<String, Object> yamlMap) {
    Map<String, Object> normalized = new HashMap<>(yamlMap);
    Object kind = normalized.get("kind");
    if (kind instanceof String kindStr) {
      normalized.put("kind", kindStr.toLowerCase());
    }
    return normalized;
  }

  /**
   * Build a consistent scope cache key; blank org/project becomes "" so "" and null produce the same key.
   */
  private static String scopeKeyForScope(String orgIdentifier, String projectIdentifier) {
    String o = StringUtils.isBlank(orgIdentifier) ? "" : orgIdentifier;
    String p = StringUtils.isBlank(projectIdentifier) ? "" : projectIdentifier;
    return o + ":" + p;
  }

  /** Parse scope key to [orgId, projectId]; blank segments become null. */
  private static String[] parseScopeKey(String scopeKey) {
    String[] parts = scopeKey.split(":", 2);
    return new String[] {StringUtils.isBlank(parts[0]) ? null : parts[0],
        parts.length <= 1 || StringUtils.isBlank(parts[1]) ? null : parts[1]};
  }

  @SuppressWarnings("unchecked")
  private String getIntegrationMetadataProperty(CatalogEntity entity, String integrationType, String key) {
    Map<String, Object> metadata = entity.getDecoratedMetadata();
    if (metadata == null) {
      return null;
    }
    Map<String, Object> integrationProps = (Map<String, Object>) metadata.get("integration_properties");
    if (integrationProps == null) {
      return null;
    }
    Map<String, Object> provider = (Map<String, Object>) integrationProps.get(integrationType);
    return provider != null ? (String) provider.get(key) : null;
  }

  private String buildLinkageConfigPath(String spacePath, String integrationIdentifier, String kind) {
    return "metadata." + CatalogIntegrationEntityConstants.INTEGRATION_PATH_PREFIX + "." + spacePath + "."
        + integrationIdentifier + "." + kind;
  }

  private String getIntegrationEntityLinkageConfig(String integrationType) {
    try {
      Map<String, Object> integrationEntityLinkageConfig =
          GsonUtils.convertJsonStringToObject(integrationEntityLinkageConfigCache.get(""), Map.class);
      return (String) ((Map<String, Object>) integrationEntityLinkageConfig.get("catalog")).get(integrationType);
    } catch (Exception e) {
      log.error("Error in getIntegrationEntityLinkageConfig. Exception = {}", e.getMessage(), e);
      throw new UnexpectedException("Error in get integration entity linkage config");
    }
  }

  private Set<String> collectEntityUuidsFromLinkage(Map<String, Object> processedData, String integrationBasePath) {
    return collectEntityLinkageInfoFromLinkage(processedData, integrationBasePath).keySet();
  }

  private Map<String, String> collectEntityUuidToKindFromLinkage(
      Map<String, Object> processedData, String integrationBasePath) {
    Map<String, String> uuidToKind = new HashMap<>();
    try {
      Map<String, Object> current = processedData;
      for (String part : integrationBasePath.split("\\.")) {
        Object next = current.get(part);
        if (!(next instanceof Map)) {
          return uuidToKind;
        }
        current = (Map<String, Object>) next;
      }

      for (Map.Entry<String, Object> kindEntry : current.entrySet()) {
        String kind = kindEntry.getKey();
        if (!(kindEntry.getValue() instanceof Map)) {
          continue;
        }
        Map<String, Object> kindMap = (Map<String, Object>) kindEntry.getValue();
        Object directUuid = kindMap.get("entity_uuid");
        if (directUuid instanceof String) {
          uuidToKind.put((String) directUuid, kind);
          continue;
        }
        // K8s: nested namespace -> {entity_uuid}
        for (Map.Entry<String, Object> subEntry : kindMap.entrySet()) {
          if (subEntry.getValue() instanceof Map) {
            Map<String, Object> nestedMap = (Map<String, Object>) subEntry.getValue();
            Object nestedUuid = nestedMap.get("entity_uuid");
            if (nestedUuid instanceof String) {
              uuidToKind.put((String) nestedUuid, kind);
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("Error collecting entity uuid to kind from linkage for path={}", integrationBasePath, e);
    }
    return uuidToKind;
  }

  private Map<String, String> collectEntityLinkageInfoFromLinkage(
      Map<String, Object> processedData, String integrationBasePath) {
    Map<String, String> uuidToAction = new HashMap<>();
    try {
      Map<String, Object> current = processedData;
      for (String part : integrationBasePath.split("\\.")) {
        Object next = current.get(part);
        if (!(next instanceof Map)) {
          return uuidToAction;
        }
        current = (Map<String, Object>) next;
      }

      for (Map.Entry<String, Object> kindEntry : current.entrySet()) {
        if (!(kindEntry.getValue() instanceof Map)) {
          continue;
        }
        Map<String, Object> kindMap = (Map<String, Object>) kindEntry.getValue();
        Object directUuid = kindMap.get("entity_uuid");
        if (directUuid instanceof String) {
          String action = kindMap.get("entity_action") instanceof String ? (String) kindMap.get("entity_action") : null;
          uuidToAction.put((String) directUuid, action);
          continue;
        }
        // K8s: nested namespace -> {entity_uuid}
        for (Map.Entry<String, Object> subEntry : kindMap.entrySet()) {
          if (subEntry.getValue() instanceof Map) {
            Map<String, Object> nestedMap = (Map<String, Object>) subEntry.getValue();
            Object nestedUuid = nestedMap.get("entity_uuid");
            if (nestedUuid instanceof String) {
              String action =
                  nestedMap.get("entity_action") instanceof String ? (String) nestedMap.get("entity_action") : null;
              uuidToAction.put((String) nestedUuid, action);
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("Error collecting entity linkage info from linkage for path={}", integrationBasePath, e);
    }
    return uuidToAction;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Long> collectEntityImportedAtFromLinkage(
      Map<String, Object> processedData, String integrationBasePath) {
    Map<String, Long> uuidToImportedAt = new HashMap<>();
    try {
      Map<String, Object> current = processedData;
      for (String part : integrationBasePath.split("\\.")) {
        Object next = current.get(part);
        if (!(next instanceof Map)) {
          return uuidToImportedAt;
        }
        current = (Map<String, Object>) next;
      }

      for (Map.Entry<String, Object> kindEntry : current.entrySet()) {
        if (!(kindEntry.getValue() instanceof Map)) {
          continue;
        }
        Map<String, Object> kindMap = (Map<String, Object>) kindEntry.getValue();
        Object directUuid = kindMap.get("entity_uuid");
        if (directUuid instanceof String) {
          Object importedAt = kindMap.get("imported_at");
          if (importedAt instanceof Number) {
            uuidToImportedAt.put((String) directUuid, ((Number) importedAt).longValue());
          }
          continue;
        }
        for (Map.Entry<String, Object> subEntry : kindMap.entrySet()) {
          if (subEntry.getValue() instanceof Map) {
            Map<String, Object> nestedMap = (Map<String, Object>) subEntry.getValue();
            Object nestedUuid = nestedMap.get("entity_uuid");
            if (nestedUuid instanceof String) {
              Object importedAt = nestedMap.get("imported_at");
              if (importedAt instanceof Number) {
                uuidToImportedAt.put((String) nestedUuid, ((Number) importedAt).longValue());
              }
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("Error collecting imported_at from linkage for path={}", integrationBasePath, e);
    }
    return uuidToImportedAt;
  }

  @SuppressWarnings("unchecked")
  private Object getExistingLinkageField(Map<String, Object> processedData, String linkagePath, String fieldName) {
    try {
      Map<String, Object> current = processedData;
      for (String part : linkagePath.split("\\.")) {
        Object next = current.get(part);
        if (!(next instanceof Map)) {
          return null;
        }
        current = (Map<String, Object>) next;
      }
      return current.get(fieldName);
    } catch (Exception e) {
      return null;
    }
  }

  private Map<String, Object> getIntegrationEntityAdditionalLinkageConfigPlaceholder(String integrationType) {
    try {
      Map<String, Object> integrationEntityLinkageConfigPlaceholder = GsonUtils.convertJsonStringToObject(
          integrationEntityAdditionalLinkageConfigPlaceholderCache.get(""), Map.class);
      return (Map<String, Object>) ((Map<String, Object>) integrationEntityLinkageConfigPlaceholder.get("catalog"))
          .get(integrationType);
    } catch (Exception e) {
      log.error("Error in getIntegrationEntityAdditionalLinkageConfigPlaceholder. Exception = {}", e.getMessage(), e);
      throw new UnexpectedException("Error in get integration entity additional linkage config placeholder");
    }
  }

  private String getIntegrationEntityAdditionalLinkageConfig(String integrationType) {
    try {
      Map<String, Object> integrationEntityAdditionalLinkageConfig =
          GsonUtils.convertJsonStringToObject(integrationEntityAdditionalLinkageConfigCache.get(""), Map.class);
      return (String) ((Map<String, Object>) integrationEntityAdditionalLinkageConfig.get("catalog"))
          .get(integrationType);
    } catch (Exception e) {
      log.error("Error in getIntegrationEntityAdditionalLinkageConfig. Exception = {}", e.getMessage(), e);
      throw new UnexpectedException("Error in get integration entity additional linkage config");
    }
  }

  @SuppressWarnings("unchecked")
  private void decorateIDPCatalogWithIntegrationMetadata(CatalogEntity catalogEntity,
      String catalogIntegrationEntityLinkageConfig, EntityMappedEntityResponse entityMappedEntityResponse,
      String action, TypesIntegrationConfig.EnumIntegrationType integrationType, String spacePath,
      String integrationConfigId) {
    Map<String, Object> decorator = catalogEntity.getFailSafeDecorator();
    Map<String, Object> initialProcessedData = catalogEntity.getFailSafeProcessedData(decorator);
    Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData(decorator);

    Map<String, Object> linkageData = new HashMap<>();
    linkageData.put("entity_uuid", entityMappedEntityResponse.getUuid());

    Object existingAction =
        getExistingLinkageField(processedData, catalogIntegrationEntityLinkageConfig, "entity_action");
    linkageData.put("entity_action", existingAction instanceof String ? existingAction : action);

    Object existingImportedAt =
        getExistingLinkageField(processedData, catalogIntegrationEntityLinkageConfig, "imported_at");
    linkageData.put(
        "imported_at", existingImportedAt instanceof Number ? existingImportedAt : System.currentTimeMillis());

    boolean isK8s = integrationType == TypesIntegrationConfig.EnumIntegrationType.HarnessK8s;
    Map<String, Object> metadataInProcessedData =
        (Map<String, Object>) processedData.computeIfAbsent("metadata", k -> new HashMap<>());

    Object metadata = entityMappedEntityResponse.getData().get("metadata");
    if (isK8s) {
      Object integrationPropertiesObj = metadata instanceof Map
          ? ((Map<String, Object>) metadata).get(CatalogIntegrationEntityConstants.INTEGRATION_PROPERTIES)
          : null;
      if (integrationPropertiesObj instanceof Map) {
        Map<String, Object> integrationPropertiesMap = (Map<String, Object>) metadataInProcessedData.computeIfAbsent(
            CatalogIntegrationEntityConstants.INTEGRATION_PROPERTIES, k -> new HashMap<>());
        mergeK8sIntegrationProperties(integrationPropertiesMap, (Map<String, Object>) integrationPropertiesObj);
      }

      if (integrationPropertiesObj instanceof Map) {
        Map<String, Object> namespaceLinkageMap = new HashMap<>();
        for (Object typeValue : ((Map<String, Object>) integrationPropertiesObj).values()) {
          if (!(typeValue instanceof Map)) {
            continue;
          }
          for (Object envValue : ((Map<String, Object>) typeValue).values()) {
            if (!(envValue instanceof List)) {
              continue;
            }
            for (Object entry : (List<?>) envValue) {
              if (!(entry instanceof Map)) {
                continue;
              }
              Object nsObj = ((Map<String, Object>) entry).get("namespace");
              if (nsObj != null) {
                Map<String, Object> nsLinkage = new HashMap<>(linkageData);
                namespaceLinkageMap.put(nsObj.toString(), nsLinkage);
              }
            }
          }
        }
        if (!namespaceLinkageMap.isEmpty()) {
          for (Map.Entry<String, Object> nsEntry : namespaceLinkageMap.entrySet()) {
            putByPath(
                processedData, catalogIntegrationEntityLinkageConfig + "." + nsEntry.getKey(), nsEntry.getValue());
          }
        }
      }
    } else {
      if (metadata instanceof Map) {
        Object integrationPropertiesObj =
            ((Map<String, Object>) metadata).get(CatalogIntegrationEntityConstants.INTEGRATION_PROPERTIES);
        if (integrationPropertiesObj instanceof Map) {
          Map<String, Object> integrationPropertiesMap = (Map<String, Object>) metadataInProcessedData.computeIfAbsent(
              CatalogIntegrationEntityConstants.INTEGRATION_PROPERTIES, k -> new HashMap<>());
          deepMerge(integrationPropertiesMap, (Map<String, Object>) integrationPropertiesObj);
        }
      }
      putByPath(processedData, catalogIntegrationEntityLinkageConfig, linkageData);
    }

    if (metadataInProcessedData.get(METADATA_TAGS_KEY) == null) {
      metadataInProcessedData.remove(METADATA_TAGS_KEY);
    }

    String integrationKind = entityMappedEntityResponse.getKind();
    if (integrationType != null && integrationKind != null && spacePath != null && integrationConfigId != null) {
      String configRefKey = integrationType.name() + "." + integrationKind;
      String configRefValue = spacePath + "." + integrationConfigId;
      Map<String, Object> integrationConfigRef =
          (Map<String, Object>) processedData.computeIfAbsent("integration_config_ref", k -> new HashMap<>());
      List<String> refList = (List<String>) integrationConfigRef.computeIfAbsent(configRefKey, k -> new ArrayList<>());
      if (!refList.contains(configRefValue)) {
        refList.add(configRefValue);
      }
    }

    decorator.put(PROCESSED_DATA, processedData);
    catalogEntity.setDecorator(decorator);
    catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity));
    transactionHelper.performTransaction(() -> {
      catalogEntityRepository.save(catalogEntity);
      return null;
    });
    outboxService.save(
        new CatalogDecoratorUpdateEvent(ScopeInfo.builder()
                                            .accountIdentifier(catalogEntity.getAccountIdentifier())
                                            .orgIdentifier(catalogEntity.getOrgIdentifier())
                                            .projectIdentifier(catalogEntity.getProjectIdentifier())
                                            .scopeType(ScopeLevel.of(catalogEntity.getAccountIdentifier(),
                                                catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier()))
                                            .uniqueId(catalogEntity.getParentUniqueId())
                                            .build(),
            processedData, initialProcessedData, catalogEntity.getKind(), catalogEntity.getIdentifier()));
  }

  private void mergeK8sIntegrationProperties(
      Map<String, Object> integrationPropertiesMap, Map<String, Object> newIntegrationProperties) {
    for (Map.Entry<String, Object> typeEntry : newIntegrationProperties.entrySet()) {
      String typeKey = typeEntry.getKey();
      if (!(typeEntry.getValue() instanceof Map)) {
        continue;
      }
      Map<String, Object> newEnvMap = (Map<String, Object>) typeEntry.getValue();
      Map<String, Object> existingTypeMap =
          (Map<String, Object>) integrationPropertiesMap.computeIfAbsent(typeKey, k -> new HashMap<>());

      for (Map.Entry<String, Object> envEntry : newEnvMap.entrySet()) {
        String envKey = envEntry.getKey();
        if (!(envEntry.getValue() instanceof List)) {
          continue;
        }
        List<?> newEntries = (List<?>) envEntry.getValue();

        Object existingObj = existingTypeMap.get(envKey);
        List<Map<String, Object>> existingEntries;
        if (existingObj instanceof List) {
          existingEntries = (List<Map<String, Object>>) existingObj;
        } else {
          existingEntries = new ArrayList<>();
          existingTypeMap.put(envKey, existingEntries);
        }

        for (Object newEntryObj : newEntries) {
          if (!(newEntryObj instanceof Map)) {
            continue;
          }
          Map<String, Object> newEntry = (Map<String, Object>) newEntryObj;
          Map<String, Object> filtered = new HashMap<>();
          if (newEntry.containsKey("namespace")) {
            filtered.put("namespace", newEntry.get("namespace"));
          }
          if (newEntry.containsKey("kind")) {
            filtered.put("kind", newEntry.get("kind"));
          }
          if (newEntry.containsKey("name")) {
            filtered.put("name", newEntry.get("name"));
          }
          if (newEntry.containsKey("replicas")) {
            filtered.put("replicas", newEntry.get("replicas"));
          }

          String namespace = newEntry.get("namespace") != null ? newEntry.get("namespace").toString() : null;
          String kind = newEntry.get("kind") != null ? newEntry.get("kind").toString() : null;
          String name = newEntry.get("name") != null ? newEntry.get("name").toString() : null;

          boolean replaced = false;
          for (int i = 0; i < existingEntries.size(); i++) {
            Map<String, Object> existing = existingEntries.get(i);
            boolean nsMatch = Objects.equals(
                namespace, existing.get("namespace") != null ? existing.get("namespace").toString() : null);
            boolean kindMatch =
                Objects.equals(kind, existing.get("kind") != null ? existing.get("kind").toString() : null);
            boolean nameMatch =
                Objects.equals(name, existing.get("name") != null ? existing.get("name").toString() : null);
            if (nsMatch && kindMatch && nameMatch) {
              existingEntries.set(i, filtered);
              replaced = true;
              break;
            }
          }
          if (!replaced) {
            existingEntries.add(filtered);
          }
        }
      }
    }
  }

  private void performAdditionalLinkageOnCatalogEntity(EntityMappedEntityResponse entityMappedEntityResponse,
      CatalogEntity catalogEntity, Map<String, Object> catalogIntegrationEntityAdditionalLinkageConfigPlaceholder) {
    String yaml = catalogEntity.getYaml();
    Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(yaml);

    String originalIdentifier = (String) yamlMap.get("identifier");

    yamlMap.put("accountIdentifier", entityMappedEntityResponse.getScope().getAccountIdentifier());
    yamlMap.put("identifier", entityMappedEntityResponse.getData().get("identifier"));
    yamlMap.put("name",
        !isEmpty((String) entityMappedEntityResponse.getData().get("name"))
            ? entityMappedEntityResponse.getData().get("name")
            : catalogEntity.getName());
    if (!isEmpty(entityMappedEntityResponse.getScope().getOrgIdentifier())) {
      yamlMap.put("orgIdentifier", entityMappedEntityResponse.getScope().getOrgIdentifier());
      yamlMap.put("orgDetails", "orgs/" + entityMappedEntityResponse.getScope().getOrgIdentifier() + "/");
    }
    if (!isEmpty(entityMappedEntityResponse.getScope().getProjectIdentifier())) {
      yamlMap.put("projectIdentifier", entityMappedEntityResponse.getScope().getProjectIdentifier());
      yamlMap.put("projectDetails", "projects/" + entityMappedEntityResponse.getScope().getProjectIdentifier() + "/");
    }

    Object metadata = entityMappedEntityResponse.getData().get("metadata");
    if (metadata instanceof Map) {
      Map<String, Object> filteredMetadata = new HashMap<>();
      for (Map.Entry<String, Object> entry : ((Map<String, Object>) metadata).entrySet()) {
        if (!CatalogIntegrationEntityConstants.INTEGRATION_PROPERTIES.equals(entry.getKey())
            && !"kubernetes".equals(entry.getKey())
            && !(METADATA_TAGS_KEY.equals(entry.getKey()) && entry.getValue() == null)) {
          filteredMetadata.put(entry.getKey(), entry.getValue());
        }
      }
      Map<String, Object> yamlMetadata =
          (Map<String, Object>) yamlMap.computeIfAbsent("metadata", k -> new HashMap<>());
      deepMerge(yamlMetadata, filteredMetadata);
    }

    Object spec = entityMappedEntityResponse.getData().get("spec");
    if (spec instanceof Map) {
      Map<String, Object> yamlSpec = (Map<String, Object>) yamlMap.computeIfAbsent("spec", k -> new HashMap<>());
      deepMerge(yamlSpec, (Map<String, Object>) spec);
    }

    Object owner = entityMappedEntityResponse.getData().get("owner");
    if (owner != null) {
      yamlMap.put("owner", owner);
    }

    String catalogIntegrationEntityAdditionalLinkageConfigYaml =
        YamlUtils.writeObjectAsYaml(catalogIntegrationEntityAdditionalLinkageConfigPlaceholder);
    StringLookup interpolator = StringLookupFactory.INSTANCE.interpolatorStringLookup(yamlMap);
    StringSubstitutor substitutor = new StringSubstitutor(interpolator);
    substitutor.setEnableSubstitutionInVariables(true);
    catalogIntegrationEntityAdditionalLinkageConfigYaml =
        substitutor.replace(catalogIntegrationEntityAdditionalLinkageConfigYaml);
    Map<String, Object> linkageConfig =
        YamlUtils.loadYamlStringAsMap(catalogIntegrationEntityAdditionalLinkageConfigYaml);
    yamlMap = mergeIgnoringEmpty(yamlMap, linkageConfig);
    applyDataMerge(yamlMap, yamlMap);

    yamlMap.put("identifier", originalIdentifier);
    yamlMap.remove("accountIdentifier");
    yamlMap.remove("orgDetails");
    yamlMap.remove("projectDetails");

    yaml = YamlUtils.writeObjectAsYaml(yamlMap);

    EntityResponse entityResponse =
        catalogService.getEntity(catalogEntity.getAccountIdentifier(), catalogEntity.getOrgIdentifier(),
            catalogEntity.getProjectIdentifier(), CatalogUtils.entityRef(catalogEntity), false, false, true);
    EntityUpdateRequest entityUpdateRequest =
        entityUpdateRequest(catalogEntity.getAccountIdentifier(), entityResponse, yaml);
    try {
      catalogService.updateEntity(catalogEntity.getAccountIdentifier(), catalogEntity.getOrgIdentifier(),
          catalogEntity.getProjectIdentifier(), CatalogUtils.entityRef(catalogEntity), entityUpdateRequest, false, true,
          false, false);
    } finally {
      GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
    }
  }

  @SuppressWarnings("unchecked")
  private void removeDecoratedIntegrationMetadataFromIDPCatalog(CatalogEntity catalogEntity,
      String catalogIntegrationEntityLinkageConfig, TypesIntegrationConfig.EnumIntegrationType integrationType) {
    Map<String, Object> decorator = catalogEntity.getFailSafeDecorator();
    Map<String, Object> initialProcessedData = catalogEntity.getFailSafeProcessedData(decorator);
    Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData(decorator);

    removeByPath(processedData, catalogIntegrationEntityLinkageConfig);

    if (integrationType != null) {
      String typeKey = integrationType.name();
      Object metadataObj = processedData.get("metadata");
      if (metadataObj instanceof Map) {
        Object integrationProperties =
            ((Map<String, Object>) metadataObj).get(CatalogIntegrationEntityConstants.INTEGRATION_PROPERTIES);
        if (integrationProperties instanceof Map) {
          ((Map<String, Object>) integrationProperties).remove(typeKey);
        }
      }
      // Clean up integration_config_ref entries for this integration
      String[] pathParts = catalogIntegrationEntityLinkageConfig.split("\\.");
      if (pathParts.length >= 4) {
        String spacePath = pathParts[2];
        String configId = pathParts[3];
        String configRefValue = spacePath + "." + configId;
        removeIntegrationConfigRefEntry(processedData, typeKey, configRefValue);
        removeIntegrationIdEntryFromMetadata(processedData, spacePath, configId);
      }
    }

    decorator.put(PROCESSED_DATA, processedData);
    catalogEntity.setDecorator(decorator);
    catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity));
    transactionHelper.performTransaction(() -> {
      catalogEntityRepository.save(catalogEntity);
      return null;
    });

    outboxService.save(
        new CatalogDecoratorUpdateEvent(ScopeInfo.builder()
                                            .accountIdentifier(catalogEntity.getAccountIdentifier())
                                            .orgIdentifier(catalogEntity.getOrgIdentifier())
                                            .projectIdentifier(catalogEntity.getProjectIdentifier())
                                            .scopeType(ScopeLevel.of(catalogEntity.getAccountIdentifier(),
                                                catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier()))
                                            .uniqueId(catalogEntity.getParentUniqueId())
                                            .build(),
            processedData, initialProcessedData, catalogEntity.getKind(), catalogEntity.getIdentifier()));
  }

  @SuppressWarnings("unchecked")
  private void removeIntegrationIdEntryFromMetadata(
      Map<String, Object> processedData, String spacePath, String configId) {
    Map<String, Object> metadata = (Map<String, Object>) processedData.get("metadata");
    if (metadata == null) {
      return;
    }

    Object integration = metadata.get(CatalogIntegrationEntityConstants.INTEGRATION_PATH_PREFIX);
    if (integration instanceof Map) {
      Object scopeObj = ((Map<String, Object>) integration).get(spacePath);
      if (scopeObj instanceof Map) {
        Object configValue = ((Map<String, Object>) scopeObj).get(configId);
        if (configValue != null) {
          ((Map<String, Object>) scopeObj).remove(configId);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void removeIntegrationConfigRefEntry(
      Map<String, Object> processedData, String integrationType, String configRefValue) {
    Object integrationConfigRefObj = processedData.get("integration_config_ref");
    if (!(integrationConfigRefObj instanceof Map)) {
      return;
    }
    Map<String, Object> integrationConfigRef = (Map<String, Object>) integrationConfigRefObj;
    List<String> keysToRemove = new ArrayList<>();
    for (Map.Entry<String, Object> entry : integrationConfigRef.entrySet()) {
      if (entry.getKey().startsWith(integrationType + ".") && entry.getValue() instanceof List) {
        ((List<?>) entry.getValue()).remove(configRefValue);
        if (((List<?>) entry.getValue()).isEmpty()) {
          keysToRemove.add(entry.getKey());
        }
      }
    }
    keysToRemove.forEach(integrationConfigRef::remove);
    if (integrationConfigRef.isEmpty()) {
      processedData.remove("integration_config_ref");
    }
  }

  private EntityUpdateRequest entityUpdateRequest(
      String accountIdentifier, EntityResponse entityResponse, String yaml) {
    EntityUpdateRequest entityUpdateRequest = new EntityUpdateRequest();
    entityUpdateRequest.setYaml(yaml);
    if (entityResponse.getGitDetails() != null) {
      EntityResponse getEntityResponse = catalogService.getEntity(accountIdentifier, entityResponse.getOrgIdentifier(),
          entityResponse.getProjectIdentifier(), entityResponse.getEntityRef(), false, false, true, true);
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
    return entityUpdateRequest;
  }

  private void updateEntityViaApi(CatalogEntity entity, String updatedYaml) {
    EntityResponse entityResponse = catalogService.getEntity(entity.getAccountIdentifier(), entity.getOrgIdentifier(),
        entity.getProjectIdentifier(), CatalogUtils.entityRef(entity), false, false, true);
    EntityUpdateRequest request = entityUpdateRequest(entity.getAccountIdentifier(), entityResponse, updatedYaml);
    try {
      catalogService.updateEntity(entity.getAccountIdentifier(), entity.getOrgIdentifier(),
          entity.getProjectIdentifier(), CatalogUtils.entityRef(entity), request, false, false, false, false);
    } finally {
      GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
    }
  }

  private OpenapiSubscribeEntitiesRequest prepareSubscribeEntitiesRequest(String kind, String uuid) {
    OpenapiSubscribeEntitiesRequest subscribeEntitiesRequest = new OpenapiSubscribeEntitiesRequest();
    subscribeEntitiesRequest.setEntities(
        List.of(OpenapiSubscribeEntitiesRequest.EntityEntityReference.builder()
                    .mappingId(integrationManagerClientHelper.getIntegrationManagerIdpMappingId())
                    .kind(kind)
                    .uuid(uuid)
                    .build()));
    return subscribeEntitiesRequest;
  }

  private EntitySubscribeEntitiesResponse integrationEntitiesUnsubscribe(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, OpenapiSubscribeEntitiesRequest subscribeEntitiesRequest) {
    return getGeneralResponse(integrationManagerClientHelper.unsubscribeFromEntityUpdates(accountIdentifier,
        accountIdentifier, orgIdentifier, projectIdentifier, integrationId, subscribeEntitiesRequest));
  }
}

/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.favorites.ResourceType.IDPENTITY;
import static io.harness.idp.catalog.mapper.CatalogMapper.populateIsCustomUserGroupInSpec;
import static io.harness.idp.catalog.utils.CatalogUtils.getEntityUniqueIdForByNameAPI;
import static io.harness.idp.catalog.utils.Constants.API_KIND;
import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.idp.catalog.utils.Constants.CORE_KINDS;
import static io.harness.idp.catalog.utils.Constants.ENVIRONMENT_BLUEPRINT_KIND;
import static io.harness.idp.catalog.utils.Constants.ENVIRONMENT_KIND;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.HIERARCHY_KIND;
import static io.harness.idp.catalog.utils.Constants.LIFECYCLE;
import static io.harness.idp.catalog.utils.Constants.NON_FILTERABLE_KINDS;
import static io.harness.idp.catalog.utils.Constants.OWNER;
import static io.harness.idp.catalog.utils.Constants.PARENT;
import static io.harness.idp.catalog.utils.Constants.REFERENCED_TYPES;
import static io.harness.idp.catalog.utils.Constants.RESOURCE_KIND;
import static io.harness.idp.catalog.utils.Constants.SCOPES;
import static io.harness.idp.catalog.utils.Constants.SOURCE_LOCATION_UNSUPPORTED_KINDS;
import static io.harness.idp.catalog.utils.Constants.SYSTEM_KIND;
import static io.harness.idp.catalog.utils.Constants.TAGS;
import static io.harness.idp.catalog.utils.Constants.TEMPLATE_KIND;
import static io.harness.idp.catalog.utils.Constants.TYPE;
import static io.harness.idp.catalog.utils.Constants.USER_KIND;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.idp.common.CommonUtils.from;
import static io.harness.idp.common.CommonUtils.throwIfMongoWriteConflictError;
import static io.harness.idp.common.Constants.COMMA_SEPARATOR;
import static io.harness.idp.common.JacksonUtils.write;
import static io.harness.idp.common.YamlUtils.mergeDecorator;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.security.SecurityContextBuilder.EMAIL;
import static io.harness.security.SecurityContextBuilder.UNIQUE_ID;
import static io.harness.security.SecurityContextBuilder.USERNAME;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.clients.BackstageResourceClient;
import io.harness.clients.integrationmanager.IntegrationManagerClientHelper;
import io.harness.clients.integrationmanager.OpenapiSubscribeEntitiesRequest;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.eventsframework.schemas.usermembership.UserMembershipDTO;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.ScmException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.gitx.GitXFileValidationLogContext;
import io.harness.governance.GovernanceMetadata;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.repositories.BackstageScaffolderTaskEntityRepository;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.beans.GetEntitiesGroupsDTO;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.config.CatalogContentConfig;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.CatalogEntityVersion;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.catalog.events.CatalogCreateEvent;
import io.harness.idp.catalog.events.CatalogDeleteEvent;
import io.harness.idp.catalog.events.CatalogUpdateEvent;
import io.harness.idp.catalog.events.EnvironmentBlueprintCreateEvent;
import io.harness.idp.catalog.events.EnvironmentBlueprintDeleteEvent;
import io.harness.idp.catalog.events.EnvironmentBlueprintUpdateEvent;
import io.harness.idp.catalog.events.EnvironmentCreateEvent;
import io.harness.idp.catalog.events.EnvironmentDeleteEvent;
import io.harness.idp.catalog.events.EnvironmentUpdateEvent;
import io.harness.idp.catalog.events.TeamCreateEvent;
import io.harness.idp.catalog.events.TeamDeleteEvent;
import io.harness.idp.catalog.events.TeamUpdateEvent;
import io.harness.idp.catalog.events.WorkflowCreateEvent;
import io.harness.idp.catalog.events.WorkflowDeleteEvent;
import io.harness.idp.catalog.events.WorkflowUpdateEvent;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.HarnessToIDPHelper;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.helpers.IDPToHarnessHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.helpers.STOHelper;
import io.harness.idp.catalog.mapper.CatalogMapper;
import io.harness.idp.catalog.opa.IdpEntityOpaService;
import io.harness.idp.catalog.processor.PlaceholderProcessor;
import io.harness.idp.catalog.processor.RelationsProcessor;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.repositories.CatalogEntityVersionRepository;
import io.harness.idp.catalog.repositories.EntityLinkRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.catalog.utils.SupportedProvidersInSourceLocation;
import io.harness.idp.ccp.entities.CatalogCustomPropertyEntity;
import io.harness.idp.ccp.events.CatalogCustomPropertyUpdateEvent;
import io.harness.idp.ccp.repositories.CatalogCustomPropertiesRepository;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.GcpStorageUtil;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.common.encryption.IdpContentEncryptionService;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.idp.groups.entities.GroupEntity;
import io.harness.idp.groups.repositories.GroupsRepository;
import io.harness.idp.integrations.helpers.CatalogIntegrationServiceHelper;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.events.ScorecardUpdateEvent;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardDetailsMapper;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardRepository;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.scores.mappers.ScoreTierMapper;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.user.remote.dto.UserFilter;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.opaclient.model.OpaConstants;
import io.harness.organization.remote.OrganizationClient;
import io.harness.outbox.api.OutboxService;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.spec.server.idp.v1.model.CatalogSyncRequest;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.EntitiesConvertRequestBody;
import io.harness.spec.server.idp.v1.model.EntitiesGroups;
import io.harness.spec.server.idp.v1.model.EntitiesGroupsResponse;
import io.harness.spec.server.idp.v1.model.EntitiesGroupsResponseCount;
import io.harness.spec.server.idp.v1.model.EntitiesGroupsResponseCountOrg;
import io.harness.spec.server.idp.v1.model.EntitiesGroupsResponseCountProject;
import io.harness.spec.server.idp.v1.model.EntitiesGroupsResponseData;
import io.harness.spec.server.idp.v1.model.EntitiesGroupsResponseDataAccount;
import io.harness.spec.server.idp.v1.model.EntitiesMigrateRequest;
import io.harness.spec.server.idp.v1.model.EntityConvertResponse;
import io.harness.spec.server.idp.v1.model.EntityConvertV2Response;
import io.harness.spec.server.idp.v1.model.EntityCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityFiltersResponse;
import io.harness.spec.server.idp.v1.model.EntityKindsResponse;
import io.harness.spec.server.idp.v1.model.EntityMoveOperationType;
import io.harness.spec.server.idp.v1.model.EntityMoveRequest;
import io.harness.spec.server.idp.v1.model.EntityRequest;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityResponseEntityValidityDetails;
import io.harness.spec.server.idp.v1.model.EntityResponseScorecards;
import io.harness.spec.server.idp.v1.model.EntityResponseScorecardsScores;
import io.harness.spec.server.idp.v1.model.EntityResponseStoDetails;
import io.harness.spec.server.idp.v1.model.EntityResponseStoDetailsTestTargets;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.EntityValidateRequest;
import io.harness.spec.server.idp.v1.model.EntityValidateResponse;
import io.harness.spec.server.idp.v1.model.EntityValidateResponseEntityMetadata;
import io.harness.spec.server.idp.v1.model.EntityValidateResponseEntityMetadataScope;
import io.harness.spec.server.idp.v1.model.EntityValidateResponseValidationErrorMetadata;
import io.harness.spec.server.idp.v1.model.EntityVersionCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityVersionResponse;
import io.harness.spec.server.idp.v1.model.EntityVersionUpdateRequest;
import io.harness.spec.server.idp.v1.model.EnvironmentBluePrintInfoResponse;
import io.harness.spec.server.idp.v1.model.EnvironmentBluePrintVersionInfo;
import io.harness.spec.server.idp.v1.model.GitDetails;
import io.harness.spec.server.idp.v1.model.GitMetadataUpdateRequest;
import io.harness.spec.server.idp.v1.model.GitUpdateDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.User;
import io.harness.spec.server.idp.v1.model.WorkflowExecutionHistoryResponse;
import io.harness.springdata.TransactionHelper;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.yaml.snakeyaml.scanner.ScannerException;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogServiceImpl implements CatalogService {
  static final List<String> kindsOrder = List.of(COMPONENT_KIND, API_KIND, RESOURCE_KIND, SYSTEM_KIND, WORKFLOW_KIND,
      HIERARCHY_KIND, GROUP_KIND, USER_KIND, ENVIRONMENT_KIND, ENVIRONMENT_BLUEPRINT_KIND);
  static final String LOCK_NAME_FORMAT = "EntitiesMigrate_%s";
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  IDPToHarnessHelper idpToHarnessHelper;
  BackstageResourceClient backstageResourceClient;
  CatalogEntityRepository catalogEntityRepository;
  EntityLinkRepository entityLinkRepository;
  ScopeInfoClient scopeInfoClient;
  HarnessToIDPHelper harnessToIDPHelper;
  RelationsProcessor relationsProcessor;
  PlaceholderProcessor placeholderProcessor;
  TransactionHelper transactionHelper;
  ScoreRepository scoreRepository;
  CatalogServiceHelper catalogServiceHelper;
  OutboxService outboxService;
  NamespaceService namespaceService;
  IdpCommonService idpCommonService;
  OrganizationClient organizationClient;
  ProjectClient projectClient;
  GroupsRepository groupsRepository;
  ScorecardService scorecardService;
  ScoreService scoreService;
  IDPGitXHelper idpGitXHelper;
  GitIntegrationServiceImpl gitIntegrationService;
  HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  HarnessCodeRepoConfig harnessCodeRepoConfig;
  STOHelper stoHelper;
  SCMGitSyncHelper scmGitSyncHelper;
  String harnessNextGenUiUrl;
  HashMap<String, String> mongoReplacementConfig;
  SetupUsageProducer setupUsageProducer;
  EntitySetupUsageClient entitySetupUsageClient;
  CatalogEntityVersionRepository catalogEntityVersionRepository;
  ResourceLocker resourceLocker;
  CatalogVersionService catalogVersionService;
  CatalogCustomPropertiesRepository catalogCustomPropertiesRepository;
  BackstageScaffolderTaskEntityRepository backstageScaffolderTaskEntityRepository;
  ScorecardRepository scorecardRepository;
  KindServiceHelper kindServiceHelper;
  IdpEntityOpaService idpEntityOpaService;
  CatalogServiceV2Impl catalogServiceV2Impl;
  GcpStorageUtil gcpStorageUtil;
  CatalogContentConfig catalogContentConfig;
  IdpContentEncryptionService idpContentEncryptionService;
  ScorecardScoreHelper scorecardScoreHelper;
  CatalogScopeResolver catalogScopeResolver;
  CatalogIntegrationServiceHelper catalogIntegrationServiceHelper;
  IntegrationManagerClientHelper integrationManagerClientHelper;
  CatalogOrgProjectService catalogOrgProjectService;
  ExecutorService entitiesGroupExecutor;

  @Inject
  public CatalogServiceImpl(IDPToHarnessHelper idpToHarnessHelper, BackstageResourceClient backstageResourceClient,
      CatalogEntityRepository catalogEntityRepository, EntityLinkRepository entityLinkRepository,
      ScopeInfoClient scopeInfoClient, HarnessToIDPHelper harnessToIDPHelper, RelationsProcessor relationsProcessor,
      PlaceholderProcessor placeholderProcessor, TransactionHelper transactionHelper, ScoreRepository scoreRepository,
      CatalogServiceHelper catalogServiceHelper, OutboxService outboxService, NamespaceService namespaceService,
      IdpCommonService idpCommonService, @Named("PRIVILEGED") OrganizationClient organizationClient,
      @Named("PRIVILEGED") ProjectClient projectClient, GroupsRepository groupsRepository,
      ScorecardService scorecardService, ScoreService scoreService, GitIntegrationServiceImpl gitIntegrationService,
      HarnessCodeConnectorUtils harnessCodeConnectorUtils,
      @Named("harnessCodeRepoConfig") HarnessCodeRepoConfig harnessCodeRepoConfig, IDPGitXHelper idpGitXHelper,
      STOHelper stoHelper, SCMGitSyncHelper scmGitSyncHelper, @Named("ngBaseUrl") String harnessNextGenUiUrl,
      @Named("mongoReplacementConfig") HashMap<String, String> mongoReplacementConfig,
      SetupUsageProducer setupUsageProducer, EntitySetupUsageClient entitySetupUsageClient,
      CatalogEntityVersionRepository catalogEntityVersionRepository, ResourceLocker resourceLocker,
      CatalogVersionService catalogVersionService, CatalogCustomPropertiesRepository catalogCustomPropertiesRepository,
      BackstageScaffolderTaskEntityRepository backstageScaffolderTaskEntityRepository,
      ScorecardRepository scorecardRepository, KindServiceHelper kindServiceHelper,
      CatalogServiceV2Impl catalogServiceV2Impl, IdpEntityOpaService idpEntityOpaService, GcpStorageUtil gcpStorageUtil,
      CatalogContentConfig catalogContentConfig, IdpContentEncryptionService idpContentEncryptionService,
      ScorecardScoreHelper scorecardScoreHelper, CatalogScopeResolver catalogScopeResolver,
      CatalogIntegrationServiceHelper catalogIntegrationServiceHelper,
      IntegrationManagerClientHelper integrationManagerClientHelper, CatalogOrgProjectService catalogOrgProjectService,
      @Named("EntitiesGroupExecutor") ExecutorService entitiesGroupExecutor) {
    this.idpToHarnessHelper = idpToHarnessHelper;
    this.backstageResourceClient = backstageResourceClient;
    this.catalogEntityRepository = catalogEntityRepository;
    this.entityLinkRepository = entityLinkRepository;
    this.scopeInfoClient = scopeInfoClient;
    this.harnessToIDPHelper = harnessToIDPHelper;
    this.relationsProcessor = relationsProcessor;
    this.placeholderProcessor = placeholderProcessor;
    this.transactionHelper = transactionHelper;
    this.scoreRepository = scoreRepository;
    this.catalogServiceHelper = catalogServiceHelper;
    this.outboxService = outboxService;
    this.namespaceService = namespaceService;
    this.idpCommonService = idpCommonService;
    this.organizationClient = organizationClient;
    this.projectClient = projectClient;
    this.groupsRepository = groupsRepository;
    this.scorecardService = scorecardService;
    this.scoreService = scoreService;
    this.idpGitXHelper = idpGitXHelper;
    this.gitIntegrationService = gitIntegrationService;
    this.harnessCodeConnectorUtils = harnessCodeConnectorUtils;
    this.harnessCodeRepoConfig = harnessCodeRepoConfig;
    this.stoHelper = stoHelper;
    this.scmGitSyncHelper = scmGitSyncHelper;
    this.harnessNextGenUiUrl = harnessNextGenUiUrl;
    this.mongoReplacementConfig = mongoReplacementConfig;
    this.setupUsageProducer = setupUsageProducer;
    this.entitySetupUsageClient = entitySetupUsageClient;
    this.catalogEntityVersionRepository = catalogEntityVersionRepository;
    this.resourceLocker = resourceLocker;
    this.catalogVersionService = catalogVersionService;
    this.catalogCustomPropertiesRepository = catalogCustomPropertiesRepository;
    this.backstageScaffolderTaskEntityRepository = backstageScaffolderTaskEntityRepository;
    this.scorecardRepository = scorecardRepository;
    this.kindServiceHelper = kindServiceHelper;
    this.idpEntityOpaService = idpEntityOpaService;
    this.catalogServiceV2Impl = catalogServiceV2Impl;
    this.gcpStorageUtil = gcpStorageUtil;
    this.catalogContentConfig = catalogContentConfig;
    this.idpContentEncryptionService = idpContentEncryptionService;
    this.scorecardScoreHelper = scorecardScoreHelper;
    this.catalogScopeResolver = catalogScopeResolver;
    this.catalogIntegrationServiceHelper = catalogIntegrationServiceHelper;
    this.integrationManagerClientHelper = integrationManagerClientHelper;
    this.catalogOrgProjectService = catalogOrgProjectService;
    this.entitiesGroupExecutor = entitiesGroupExecutor;
  }

  @Override
  public void backgroundMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(String accountIdentifier) {
    idpToHarnessHelper.validateAndMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(accountIdentifier);
  }

  @Override
  public boolean syncInSynchronousMode(String accountIdentifier, String entityUid, String action) {
    try {
      log.info("Syncing IDP catalog entities as Harness Catalog entities for accountIdentifier = {} EntityUid = {} "
              + "Action = {}",
          accountIdentifier, entityUid, action);

      switch (CatalogSyncRequest.ActionEnum.fromValue(action)) {
        case CREATE:
          handleCreateOrUpdateAction(accountIdentifier, entityUid, action, true);
          break;
        case UPDATE:
          handleCreateOrUpdateAction(accountIdentifier, entityUid, action, false);
          break;
        case DELETE:
          handleDeleteAction(accountIdentifier, entityUid);
          break;
        default:
          throw new UnexpectedException(
              "Unsupported action for syncing IdpCatalogHarnessEntitiesAsHarnessEntities in synchronous mode");
      }
    } catch (Exception ex) {
      log.error(
          "Error in IdpCatalogHarnessEntitiesAsHarnessEntities sync for accountIdentifier = {} EntityUid = {} Action = "
              + "{} Error = {}",
          accountIdentifier, entityUid, action, ex.getMessage(), ex);
      return false;
    }
    return true;
  }

  @Override
  public void handleUserBasedOnAction(String accountIdentifier, UserMembershipDTO userMembershipDTO, String action) {
    switch (action) {
      case UPDATE_ACTION:
      case CREATE_ACTION:
        updateUserCatalogEntity(accountIdentifier, userMembershipDTO, action);
        break;
      case DELETE_ACTION:
        deleteUserCatalogEntity(accountIdentifier, userMembershipDTO);
        break;
      default:
        log.warn("ACTION - {} is not to be handled by IDP user event handler", action);
    }
  }

  @Override
  public void handleUserGroupBasedOnAction(String accountIdentifier, String userGroupIdentifier, String action) {
    switch (action) {
      case UPDATE_ACTION:
      case CREATE_ACTION:
        updateUserGroupCatalogEntity(accountIdentifier, userGroupIdentifier, action);
        break;
      case DELETE_ACTION:
        deleteUserGroupCatalogEntity(accountIdentifier, userGroupIdentifier);
        break;
      default:
        log.warn("ACTION - {} is not to be handled by IDP user group event handler", action);
    }
  }

  @Override
  public void migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(String accountIdentifier) {
    idpToHarnessHelper.migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(accountIdentifier);
  }

  @Override
  public List<EntityKindsResponse> getEntitiesKinds(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<CatalogEntity> catalogEntities =
        catalogEntityRepository.findAllByAccountIdentifierAndReturnProjectedFields(accountIdentifier);
    Map<String, KindEntity> kindEntityMap = kindServiceHelper.findByAccountIdentifierIn(accountIdentifier)
                                                .stream()
                                                .collect(Collectors.toMap(KindEntity::getIdentifier, entity -> entity));

    return catalogEntities.stream()
        .filter(catalogEntity -> kindEntityMap.containsKey(catalogEntity.getKind()))
        .collect(Collectors.groupingBy(CatalogEntity::getKind, Collectors.counting()))
        .entrySet()
        .stream()
        .sorted(Comparator.comparing(entry -> {
          String kindName = entry.getKey();
          int index = kindsOrder.indexOf(kindName);
          return index == -1 ? kindsOrder.size() + kindName.hashCode() : index;
        }))
        .map(entry
            -> new EntityKindsResponse()
                   .kind(entry.getKey())
                   .displayName(kindEntityMap.get(entry.getKey()).getDisplayName())
                   .description(kindEntityMap.get(entry.getKey()).getDescription())
                   .total(entry.getValue().intValue()))
        .collect(Collectors.toList());
  }

  @Override
  public List<EntityFiltersResponse> getEntitiesFilters(
      String accountIdentifier, String scopes, String kind, String filter) {
    scopes = isEmpty(scopes) ? "account" : scopes;
    if (!isEmpty(filter)) {
      if (mongoReplacementConfig != null && !mongoReplacementConfig.isEmpty()) {
        for (Map.Entry<String, String> replacement : mongoReplacementConfig.entrySet()) {
          if (filter.contains(replacement.getKey())) {
            filter = filter.replace(replacement.getKey(), replacement.getValue());
          }
        }
      }
    }
    return getEntitiesFiltersResponse(accountIdentifier, scopes, kind, null, filter);
  }

  @Override
  public List<EntityFiltersResponse> getEntitiesFiltersByRefs(
      String accountIdentifier, String entityRefs, String kind, String filter) {
    return getEntitiesByRefsFiltersResponse(accountIdentifier, null, kind, entityRefs, filter);
  }

  private List<EntityFiltersResponse> getEntitiesFiltersResponse(
      String accountIdentifier, String scopes, String kind, String entityRefs, String filter) {
    List<ScopeInfo> scopeInfos =
        catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, scopes, entityRefs).getLeft();
    List<String> kinds = !isEmpty(kind) ? Arrays.asList(kind.split(COMMA_SEPARATOR)) : CORE_KINDS;
    List<CatalogEntity> catalogEntities = catalogEntityRepository.getEntitiesFilters(
        scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList(), kinds, filter);

    return getEntitiesFiltersResponse(catalogEntities, accountIdentifier, kinds, filter);
  }

  private List<EntityFiltersResponse> getEntitiesByRefsFiltersResponse(
      String accountIdentifier, String scopes, String kind, String entityRefs, String filter) {
    List<ScopeInfo> scopeInfos =
        catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, scopes, entityRefs).getLeft();
    List<String> kinds = !isEmpty(kind) ? Arrays.asList(kind.split(COMMA_SEPARATOR)) : CORE_KINDS;
    List<CatalogEntity> catalogEntities =
        catalogEntityRepository.getEntitiesForEntityRefsAndKinds(accountIdentifier, entityRefs, scopeInfos, kinds);

    return getEntitiesFiltersResponse(catalogEntities, accountIdentifier, kinds, filter);
  }

  private List<EntityFiltersResponse> getEntitiesFiltersResponse(
      List<CatalogEntity> catalogEntities, String accountIdentifier, List<String> kinds, String filter) {
    List<EntityFiltersResponse> entityFiltersResponses = new ArrayList<>();
    ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(accountIdentifier)
                                     .scopeType(ScopeLevel.ACCOUNT)
                                     .uniqueId(accountIdentifier)
                                     .build();

    EntityFiltersResponse entityFiltersResponse = new EntityFiltersResponse();
    entityFiltersResponse.setFilter(TYPE);
    entityFiltersResponse.setValues(catalogEntities.stream()
                                        .map(CatalogEntity::getType)
                                        .filter(Objects::nonNull)
                                        .map(String::toLowerCase)
                                        .distinct()
                                        .toList());
    entityFiltersResponses.add(entityFiltersResponse);
    if (!NON_FILTERABLE_KINDS.containsAll(kinds)) {
      entityFiltersResponse = new EntityFiltersResponse();
      entityFiltersResponse.setFilter(SCOPES);
      entityFiltersResponse.setValues(catalogServiceHelper.getScopeFilter(accountIdentifier, kinds, filter));
      entityFiltersResponses.add(entityFiltersResponse);

      catalogEntities = catalogServiceHelper.resolveOwner(accountScopeInfo.getUniqueId(), catalogEntities);
      entityFiltersResponse = new EntityFiltersResponse();
      entityFiltersResponse.setFilter(OWNER);
      entityFiltersResponse.setValues(catalogEntities.stream()
                                          .map(CatalogEntity::getOwner)
                                          .filter(Objects::nonNull)
                                          .map(String::toLowerCase)
                                          .distinct()
                                          .toList());
      entityFiltersResponses.add(entityFiltersResponse);

      entityFiltersResponse = new EntityFiltersResponse();
      entityFiltersResponse.setFilter(TAGS);
      entityFiltersResponse.setValues(
          catalogEntities.stream()
              .flatMap(catalogEntity
                  -> Optional.ofNullable(catalogEntity.getTags()).orElse(Collections.emptyList()).stream())
              .filter(Objects::nonNull)
              .map(String::toLowerCase)
              .distinct()
              .toList());
      entityFiltersResponses.add(entityFiltersResponse);
    }

    if (!NON_FILTERABLE_KINDS.containsAll(kinds)) {
      entityFiltersResponse = new EntityFiltersResponse();
      entityFiltersResponse.setFilter(LIFECYCLE);
      entityFiltersResponse.setValues(catalogEntities.stream()
                                          .map(catalogEntity -> {
                                            Map<String, Object> spec = catalogEntity.getSpec();
                                            return (!isEmpty(spec) && !isEmpty((String) spec.get(LIFECYCLE)))
                                                ? spec.get(LIFECYCLE).toString()
                                                : null;
                                          })
                                          .filter(Objects::nonNull)
                                          .map(String::toLowerCase)
                                          .distinct()
                                          .toList());
      entityFiltersResponses.add(entityFiltersResponse);
    }

    return entityFiltersResponses;
  }

  @Override
  public EntityConvertResponse convertEntity(
      String harnessAccount, String option, EntityRequest body, String entityRef, boolean loadFromFallbackBranch) {
    String yaml = null;
    if (option.equals("backstage-to-harness")) {
      yaml = idpToHarnessHelper.convertBackstageToHarness(harnessAccount, body.getYaml());
    } else if (option.equals("harness-to-backstage")) {
      yaml = harnessToIDPHelper.convertHarnessToBackstage(
          harnessAccount, body.getYaml(), entityRef, loadFromFallbackBranch);
    }
    EntityConvertResponse entityConvertResponse = new EntityConvertResponse();
    entityConvertResponse.setYaml(yaml);
    return entityConvertResponse;
  }

  private GovernanceMetadata evaluateOpaGovernance(CatalogEntity catalogEntity, String action) {
    return idpEntityOpaService.evaluatePoliciesWithEntity(catalogEntity, action);
  }

  @Override
  public EntityResponse createEntity(String harnessAccount, String orgIdentifier, String projectIdentifier,
      Boolean forceConvert, Boolean dryRun, EntityCreateRequest body) {
    return createEntity(harnessAccount, orgIdentifier, projectIdentifier, forceConvert, dryRun, body, null, false)
        .getLeft();
  }

  @Override
  public Pair<EntityResponse, EntityVersionResponse> createEntity(String harnessAccount, String orgIdentifier,
      String projectIdentifier, Boolean forceConvert, Boolean dryRun, EntityCreateRequest body,
      EntityVersionCreateRequest versionCreateRequest, boolean versionedEntity) {
    String entityYaml = versionedEntity ? versionCreateRequest.getYaml() : body.getYaml();

    catalogServiceHelper.validateMultipleDefinitionInYaml(entityYaml);

    Map<String, Object> entityYamlMap = YamlUtils.loadYamlStringAsMap(entityYaml);
    String identifier = from(entityYamlMap, "identifier", String.class);
    try {
      String kind = from(entityYamlMap, "kind", String.class);
      kind = catalogServiceHelper.validateAndSanitizeKind(kind);

      if (versionedEntity) {
        catalogServiceHelper.validateKindForVersioning(kind);
        catalogServiceHelper.validateVersionLabel(versionCreateRequest.getVersion());
      }

      if (forceConvert && TEMPLATE_KIND.equals(kind)) {
        kind = WORKFLOW_KIND;
      }
      if (kind.equals(HIERARCHY_KIND)) {
        idpCommonService.allowCreateUpdateDeleteOnHierarchyKindEntity(harnessAccount, false, false);
      }
      catalogServiceHelper.validateKindForCreateUpdateDelete(kind);

      String orgIdentifierFromYaml = from(entityYamlMap, "orgIdentifier", String.class);
      String projectIdentifierFromYaml = from(entityYamlMap, "projectIdentifier", String.class);
      if ((!isEmpty(orgIdentifier) && !orgIdentifier.equals(orgIdentifierFromYaml))
          || (!isEmpty(projectIdentifier) && !projectIdentifier.equals(projectIdentifierFromYaml))) {
        throw new InvalidRequestException(
            "Mismatch in orgIdentifier / projectIdentifier between query param and YAML input");
      }
      orgIdentifier = orgIdentifierFromYaml;
      projectIdentifier = projectIdentifierFromYaml;
      String orgName = catalogOrgProjectService.getOrgName(harnessAccount, orgIdentifier);
      String projectName = catalogOrgProjectService.getProjectName(harnessAccount, orgIdentifier, projectIdentifier);

      if (forceConvert) {
        entityYaml = idpToHarnessHelper.convertBackstageToHarness(harnessAccount, entityYaml);
      }
      entityYamlMap = YamlUtils.loadYamlStringAsMap(entityYaml);
      identifier = from(entityYamlMap, "identifier", String.class);
      identifier = catalogServiceHelper.validateAndSanitizeIdentifier(identifier);
      catalogServiceHelper.checkCreateRbac(harnessAccount, orgIdentifier, projectIdentifier, kind,
          CatalogUtils.entityRef(kind, orgIdentifier, projectIdentifier, identifier));
      catalogServiceHelper.validateIdentifierPattern(identifier, kind, from(entityYamlMap, "type", String.class));
      KindEntity kindEntity = kindServiceHelper.kindEntity(harnessAccount, kind);
      catalogServiceHelper.validateAgainstJsonSchema(kind, entityYaml, kindEntity.getSchema());
      ScopeInfo scopeInfo = catalogScopeResolver.resolveSingleScopeInfo(
          harnessAccount, CatalogUtils.getScope(orgIdentifier, projectIdentifier));
      entityYaml = catalogServiceHelper.resolveExpressionsInEntityYaml(harnessAccount, entityYaml);
      entityYaml = catalogServiceHelper.resolveMembersForCustomUserGroup(kind, entityYaml);
      catalogServiceHelper.validateWorkflowNoCaseCollidingKeys(kind, YamlUtils.loadYamlStringAsMap(entityYaml));
      idpGitXHelper.applyGitXSettingsIfApplicable(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier(), EntityType.IDP_CATALOG);
      Set<String> groupingKinds = kindServiceHelper.groupingKinds(harnessAccount);
      CatalogEntity catalogEntity =
          CatalogMapper.yamlToEntity(scopeInfo, identifier, kind, entityYaml, null, groupingKinds);
      catalogServiceHelper.validateAndPopulateIsCustomForCustomUserGroup(catalogEntity);
      Pair<Boolean, String> validCatalog = getReadValidationAndSourceLocationDetails(catalogEntity, null, false);
      String sourceUrlConnectorError = null;
      if (validCatalog != null) {
        if (!validCatalog.getLeft()) {
          log.warn("Connector used in entity [{}] does not have read permission. Source URL: {}",
              catalogEntity.getIdentifier(), validCatalog.getRight());
          sourceUrlConnectorError = "Connector used in entity for source URL does not have read permission";
        } else {
          populateSourceLocationUrlInSpecOfEntity(catalogEntity, validCatalog.getRight());
        }
      }
      populateEntityStatusForSourceLocationConnector(catalogEntity, sourceUrlConnectorError);
      catalogEntity.setOwner(catalogServiceHelper.resolveOwner(harnessAccount, catalogEntity.getOwner()));
      catalogServiceHelper.validateOwnerScope(
          CatalogUtils.getScope(catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier()),
          catalogEntity.getOwner());
      catalogServiceHelper.validateParentTeam(CatalogUtils.entityRef(catalogEntity),
          !isEmpty(catalogEntity.getSpec()) ? (String) catalogEntity.getSpec().get(PARENT) : null);
      idpGitXHelper.addGitParamsToOverrideEntity(catalogEntity, scopeInfo);
      List<CatalogEntity> referencedEntities = relationsProcessor.establishRelations(catalogEntity);
      resolvePlaceholders(catalogEntity);
      catalogServiceHelper.validateSystemScope(catalogEntity);
      GovernanceMetadata governanceMetadata =
          evaluateOpaGovernance(catalogEntity, OpaConstants.OPA_EVALUATION_ACTION_SAVE);
      if (governanceMetadata != null && governanceMetadata.getDeny()) {
        EntityResponse entityResponse = new EntityResponse();
        entityResponse.setGovernanceMetadata(governanceMetadata);
        EntityVersionResponse entityVersionResponse = versionedEntity ? new EntityVersionResponse() : null;
        if (entityVersionResponse != null) {
          entityVersionResponse.setGovernanceMetadata(governanceMetadata);
        }
        return Pair.of(entityResponse, entityVersionResponse);
      }
      List<CatalogEntity> entities = new ArrayList<>();

      CatalogEntity exsistingCatalogEntity = null;
      if (versionedEntity) {
        Optional<CatalogEntity> optionalExistingCatalogEntity =
            catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(scopeInfo.getUniqueId(), kind, identifier);
        if (optionalExistingCatalogEntity.isPresent()) {
          exsistingCatalogEntity = optionalExistingCatalogEntity.get();
        }
      }

      final CatalogEntity finalExsistingCatalogEntity = exsistingCatalogEntity;

      entities.add(catalogEntity);
      entities.addAll(referencedEntities);
      EntityVersionResponse entityVersionResponse = null;

      if (!dryRun) {
        entityVersionResponse = transactionHelper.performTransaction(() -> {
          EntityVersionResponse transactionalEntityVersionResponse = null;
          CatalogEntity entityForVersion = finalExsistingCatalogEntity;

          if (versionedEntity) {
            if (finalExsistingCatalogEntity == null) {
              entityForVersion = catalogEntityRepository.save(catalogEntity);
              entities.remove(catalogEntity);
            }

            transactionalEntityVersionResponse =
                catalogVersionService.createEntityVersion(entityForVersion, versionCreateRequest.getYaml(),
                    versionCreateRequest.getVersion(), versionCreateRequest.getDescription(),
                    versionCreateRequest.isDeprecated(), versionCreateRequest.isStable(), orgName, projectName);
            if (finalExsistingCatalogEntity != null) {
              return transactionalEntityVersionResponse;
            }
          }
          idpGitXHelper.pushToGit(catalogEntity, scopeInfo);
          if (catalogEntity.getKind().equals(WORKFLOW_KIND)) {
            outboxService.save(new WorkflowCreateEvent(
                scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
          } else if (catalogEntity.getKind().equals(ENVIRONMENT_KIND)) {
            outboxService.save(new EnvironmentCreateEvent(
                scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
          } else if (catalogEntity.getKind().equals(ENVIRONMENT_BLUEPRINT_KIND)) {
            outboxService.save(new EnvironmentBlueprintCreateEvent(
                scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
          } else if (catalogEntity.getKind().equals(GROUP_KIND)) {
            outboxService.save(new TeamCreateEvent(
                scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
          } else {
            outboxService.save(new CatalogCreateEvent(
                scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
          }
          createOutboxUpdateEventForReferencedEntities(referencedEntities);
          entities.forEach(entity -> entity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(entity)));
          catalogEntityRepository.saveAll(entities);
          harnessToIDPHelper.harnessToIdpSync(List.of(catalogEntity), harnessAccount, CREATE_ACTION);
          // Publish setup usage for Environment -> Environment Blueprint relation on create
          if (ENVIRONMENT_KIND.equals(catalogEntity.getKind())) {
            Map<String, Object> spec = catalogEntity.getSpec();
            Map<String, Object> envBlueprint = spec != null ? from(spec, "environmentBlueprint", Map.class) : null;
            String envBlueprintIdentifier =
                envBlueprint != null ? from(envBlueprint, "identifier", String.class) : null;
            String envBlueprintVersion = envBlueprint != null ? from(envBlueprint, "version", String.class) : null;

            if (!isEmpty(envBlueprintIdentifier) && !isEmpty(envBlueprintVersion)) {
              String[] bpScope = CommonUtils.resolveScopeFromIdentifier(
                  envBlueprintIdentifier, catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier());
              setupUsageProducer.publishEnvironmentBluePrintSetupUsages(harnessAccount,
                  catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier(),
                  catalogServiceHelper.getBlueprintVersionIdentifier(envBlueprintIdentifier, envBlueprintVersion),
                  catalogEntity.getIdentifier(), bpScope[0], bpScope[1]);
            }
          }
          return transactionalEntityVersionResponse;
        });
        idpToHarnessHelper.sendCatalogEventsToRedis(Collections.singletonList(catalogEntity), CREATE_ACTION);
        idpToHarnessHelper.sendCatalogEventsToRedis(referencedEntities, UPDATE_ACTION);
        catalogServiceHelper.publishAsyncComputationEvent(
            harnessAccount, null, CatalogUtils.getEntityUUId(catalogEntity));
      }
      EntityResponse entityResponse = CatalogMapper.entityToResponse(
          (catalogEntity instanceof GitReferencedCatalogEntity)
              ? CatalogMapper.yamlToEntity(scopeInfo, identifier, kind, catalogEntity.getYaml(), null, groupingKinds)
              : catalogEntity,
          orgName, projectName, null, kindEntity.getIcon(), null, false);
      if (governanceMetadata != null) {
        entityResponse.setGovernanceMetadata(governanceMetadata);
        if (entityVersionResponse != null) {
          entityVersionResponse.setGovernanceMetadata(governanceMetadata);
        }
      }
      return Pair.of(entityResponse, entityVersionResponse);
    } catch (DuplicateKeyException e) {
      assert identifier != null;
      String errorMessage = String.format("Entity with identifier [%s] already exists for the same kind", identifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while creating entity: [%s]", identifier), e);
      throw e;
    } catch (Exception ex) {
      throwIfMongoWriteConflictError(ex);
      log.error("Error in create entity. Exception = {}", ex.getMessage(), ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  public void populateSourceLocationUrlInSpecOfEntity(CatalogEntity entity, String sourceLocationUrl) {
    // Update spec.sourceCode.url unconditionally
    Map<String, Object> spec = entity.getSpec();
    Map<String, Object> sourceCode = from(spec, "sourceCode", Map.class);
    sourceCode.put("url", sourceLocationUrl);
    spec.put("sourceCode", sourceCode);
    entity.setSpec(spec);
    Set<String> groupingKinds = kindServiceHelper.groupingKinds(entity.getAccountIdentifier());
    entity.setYaml(CatalogMapper.presentationYaml(entity, groupingKinds));
  }

  public Pair<Boolean, String> getReadValidationAndSourceLocationDetails(
      CatalogEntity entity, CatalogEntity existing, boolean shouldCheckExistingSourceValidation) {
    Map<String, Object> spec = entity.getSpec();
    if (isEmpty(spec)) {
      return null;
    }

    Map<String, Object> sourceCode = from(spec, "sourceCode", Map.class);
    if (isEmpty(sourceCode)) {
      return null;
    }

    if (SOURCE_LOCATION_UNSUPPORTED_KINDS.contains(entity.getKind())) {
      throw new InvalidRequestException("Source location is not supported for kind - " + entity.getKind());
    }

    if (shouldCheckExistingSourceValidation && existing != null && isNotEmpty(existing.getSpec())) {
      Map<String, Object> existingSourceCode = from(existing.getSpec(), "sourceCode", Map.class);
      if (Objects.equals(sourceCode, existingSourceCode)) {
        return null;
      }
    }

    String provider = from(sourceCode, "provider", String.class);
    if (isEmpty(provider)) {
      throw new InvalidRequestException(String.format(
          "provider cannot be empty for the entity - %s in account - %s org - %s project - %s ", entity.getIdentifier(),
          entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier()));
    }
    if (!SupportedProvidersInSourceLocation.getAllProviderNames().contains(provider.toLowerCase())) {
      throw new InvalidRequestException(String.format("provider - %s is not supported, supported providers are - %s",
          provider, String.join(",", SupportedProvidersInSourceLocation.getAllProviderNames())));
    }

    String connectorIdentifier = from(sourceCode, "connectorRef", String.class);
    if (!SupportedProvidersInSourceLocation.HARNESS.getName().equals(provider.toLowerCase())
        && isEmpty(connectorIdentifier)) {
      throw new InvalidRequestException(String.format(
          "Connector ref is not found in sourceCode for the entity - %s in account - %s org - %s project - %s ",
          entity.getIdentifier(), entity.getAccountIdentifier(), entity.getOrgIdentifier(),
          entity.getProjectIdentifier()));
    }

    String repoName = from(sourceCode, "repoName", String.class);
    if (isEmpty(repoName)) {
      throw new InvalidRequestException(String.format(
          "repoName cannot be empty for the entity - %s in account - %s org - %s project - %s ", entity.getIdentifier(),
          entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier()));
    }
    String projectIdentifier = entity.getProjectIdentifier();
    String orgIdentifier = entity.getOrgIdentifier();
    if (SupportedProvidersInSourceLocation.HARNESS.getName().equals(provider.toLowerCase())) {
      String projectIdentifierFromSpec = from(sourceCode, "harnessCodeRepoProjectIdentifier", String.class);
      String orgIdentifierFromSpec = from(sourceCode, "harnessCodeRepoOrgIdentifier", String.class);

      projectIdentifier = isEmpty(projectIdentifierFromSpec) ? projectIdentifier : projectIdentifierFromSpec;
      orgIdentifier = isEmpty(orgIdentifierFromSpec) ? orgIdentifier : orgIdentifierFromSpec;
    }

    String branch = from(sourceCode, "branch", String.class);
    if (isEmpty(branch)) {
      ScopeInfo scopeInfo = null;
      if (SupportedProvidersInSourceLocation.HARNESS.getName().equals(provider.toLowerCase())) {
        scopeInfo =
            getScopeInfoForConnectorUsed(entity.getAccountIdentifier(), orgIdentifier, projectIdentifier, repoName);
      } else {
        scopeInfo = getScopeInfoForConnectorUsed(
            entity.getAccountIdentifier(), orgIdentifier, projectIdentifier, connectorIdentifier);
      }
      branch = idpGitXHelper.getDefaultBranch(connectorIdentifier, repoName, scopeInfo, true);
      if (isEmpty(branch)) {
        throw new InvalidRequestException(String.format(
            "branch cannot be empty for the entity - %s in account - %s org - %s project - %s ", entity.getIdentifier(),
            entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier()));
      }
      log.info("Fetched default branch {} for the entity - {} in account - {} org - {} project - {} ", branch,
          entity.getIdentifier(), entity.getAccountIdentifier(), entity.getOrgIdentifier(),
          entity.getProjectIdentifier());
    }

    ConnectorInfoDTO connectorInfo = null;
    Boolean isHarnessCodeRepo = false;
    if (!SupportedProvidersInSourceLocation.HARNESS.getName().equals(provider.toLowerCase())) {
      connectorInfo = getConnectorInfo(entity.getAccountIdentifier(), entity.getOrgIdentifier(),
          entity.getProjectIdentifier(), connectorIdentifier, extractActualConnectorIdentifier(connectorIdentifier));
    } else {
      isHarnessCodeRepo = true;
    }

    Boolean isMonoRepo = from(sourceCode, "monoRepo", Boolean.class);
    String directory = from(sourceCode, "monoRepoSubDirectoryPath", String.class);

    String sourceLocationUrl = getSourceLocationUrl(entity.getAccountIdentifier(), orgIdentifier, projectIdentifier,
        connectorIdentifier, isHarnessCodeRepo, isMonoRepo, branch, directory, connectorInfo, repoName);

    if (isEmpty(sourceLocationUrl)) {
      throw new InvalidRequestException("Source location url is not found for the entity - " + entity.getIdentifier());
    }

    boolean isReadValidationSuccessful = true;

    // This will require chanes in manager and delegate DTO we will re visit this as this does not have any functional
    // impact

    //    if (!isHarnessCodeRepo) {
    //      isReadValidationSuccessful =
    //          gitIntegrationService.validateReadPermission(entity.getAccountIdentifier(), connectorInfo,
    //          sourceLocationUrl);
    //    }

    return Pair.of(isReadValidationSuccessful, sourceLocationUrl);
  }

  private ScopeInfo getScopeInfoForConnectorUsed(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    String resolvedOrgIdentifier = null;
    String resolvedProjectIdentifier = null;

    if (connectorIdentifier.startsWith("account.")) {
    } else if (connectorIdentifier.startsWith("org.")) {
      resolvedOrgIdentifier = orgIdentifier;
    } else {
      resolvedOrgIdentifier = orgIdentifier;
      resolvedProjectIdentifier = projectIdentifier;
    }

    return getResponse(
        scopeInfoClient.getScopeInfo(accountIdentifier, resolvedOrgIdentifier, resolvedProjectIdentifier));
  }

  private String getSourceLocationUrl(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, Boolean isHarnessCodeRepo, Boolean isMonoRepo, String branch, String directory,
      ConnectorInfoDTO connectorInfoDto, String repoName) {
    String connectorType;

    String repoUrl = null;
    if (isHarnessCodeRepo) {
      HarnessConnectorDTO harnessConnector =
          getHarnessConnector(harnessAccount, orgIdentifier, projectIdentifier, repoName);
      connectorType = harnessConnector.getConnectorType().toString();
      repoUrl = harnessConnector.getRepoUiUrl();
    } else {
      ConnectorInfoDTO connectorInfo = connectorInfoDto;
      connectorType = gitIntegrationService.getGitIntegrationType(connectorInfo);

      Scope scope = Scope.of(harnessAccount, orgIdentifier, projectIdentifier);
      repoUrl = scmGitSyncHelper.getRepoUrl(scope, repoName, connectorIdentifier, Collections.emptyMap()).getRepoUrl();
    }

    if (isEmpty(repoUrl)) {
      throw new InvalidRequestException(
          String.format("No repo URL found for connector - %s in account - %s, org - %s, project - %s ",
              connectorIdentifier, harnessAccount, orgIdentifier, projectIdentifier));
    }

    if (Boolean.TRUE.equals(isMonoRepo)) {
      validateMonoRepoParams(branch, directory);
      repoUrl = CommonUtils.getDirectoryPathForSourceCode(connectorType, repoUrl, branch, directory);
    } else {
      repoUrl = CommonUtils.getBranchOnlyUrlForSourceCode(connectorType, repoUrl, branch);
    }

    return repoUrl;
  }

  private String extractActualConnectorIdentifier(String connectorIdentifier) {
    if (connectorIdentifier.startsWith("account.")) {
      return connectorIdentifier.replaceFirst("account\\.", "");
    } else if (connectorIdentifier.startsWith("org.")) {
      return connectorIdentifier.replaceFirst("org\\.", "");
    }
    return connectorIdentifier;
  }

  private HarnessConnectorDTO getHarnessConnector(
      String account, String org, String project, String actualConnectorIdentifier) {
    String processedUiUrl = harnessNextGenUiUrl;
    if (harnessNextGenUiUrl != null && harnessNextGenUiUrl.endsWith("#")) {
      processedUiUrl = harnessNextGenUiUrl.substring(0, harnessNextGenUiUrl.length() - 1);
    }
    processedUiUrl = resolveVanityUiBaseUrl(account, processedUiUrl);
    return harnessCodeConnectorUtils.getDummyHarnessCodeConnectorWithJwtAuth(account, org, project,
        actualConnectorIdentifier, harnessCodeRepoConfig.getServiceClientSharedSecret(),
        harnessCodeRepoConfig.getApiUrl(), harnessCodeRepoConfig.getGitBaseUrl(), processedUiUrl);
  }

  private String resolveVanityUiBaseUrl(String accountIdentifier, String processedUiUrl) {
    try {
      AccountDTO accountDTO = idpCommonService.getAccountDTO(accountIdentifier);
      String subdomainUrl = accountDTO.getSubdomainURL();
      if (isNotEmpty(subdomainUrl)) {
        if (!subdomainUrl.startsWith("http://") && !subdomainUrl.startsWith("https://")) {
          subdomainUrl = "https://" + subdomainUrl;
        }
        URL originalUrl = new URL(processedUiUrl);
        URL vanityUrl = new URL(subdomainUrl);
        return new URL(vanityUrl.getProtocol(), vanityUrl.getHost(), originalUrl.getPort(), originalUrl.getFile())
            .toString();
      }
    } catch (Exception ex) {
      log.info("Failed to resolve vanity UI base URL for account {}, falling back to default", accountIdentifier, ex);
    }
    return processedUiUrl;
  }

  private ConnectorInfoDTO getConnectorInfo(
      String account, String org, String project, String connectorIdentifier, String actualConnectorIdentifier) {
    if (connectorIdentifier.startsWith("account.")) {
      return gitIntegrationService.getConnectorInfo(account, null, null, actualConnectorIdentifier);
    } else if (connectorIdentifier.startsWith("org.")) {
      return gitIntegrationService.getConnectorInfo(account, org, null, actualConnectorIdentifier);
    } else {
      return gitIntegrationService.getConnectorInfo(account, org, project, actualConnectorIdentifier);
    }
  }

  private void validateMonoRepoParams(String branch, String directory) {
    if (isEmpty(branch)) {
      throw new InvalidRequestException("branch cannot be empty for monorepo");
    }
    if (isEmpty(directory)) {
      throw new InvalidRequestException("monoRepoSubDirectoryPath cannot be empty for monorepo");
    }
  }

  @Override
  public EntityResponse importEntity(String harnessAccount, String orgIdentifier, String projectIdentifier) {
    GitAwareContextHelper.updateGitEntityContextWithRemoteStoreType();
    idpGitXHelper.applyGitXSettingsIfApplicable(
        harnessAccount, orgIdentifier, projectIdentifier, EntityType.IDP_CATALOG);
    catalogServiceHelper.getRepoUrlAndCheckForFileUniqueness(harnessAccount, orgIdentifier, projectIdentifier);
    String entityYaml = catalogServiceHelper.importCatalogFromRemote(harnessAccount, orgIdentifier, projectIdentifier);
    if (isEmpty(entityYaml)) {
      String errorMessage =
          String.format("Empty YAML found on Git in branch [%s].", GitAwareContextHelper.getBranchFromGitContext());
      throw new InvalidRequestException(errorMessage);
    }
    Map<String, Object> entityYamlMap;
    try {
      entityYamlMap = YamlUtils.loadYamlStringAsMap(entityYaml);
    } catch (ScannerException e) {
      String errorMessage = String.format("Invalid YAML found on Git in branch [%s]: %s",
          GitAwareContextHelper.getBranchFromGitContext(), e.getMessage());
      log.error(errorMessage, e);
      throw new InvalidRequestException(errorMessage);
    } catch (Exception e) {
      log.error("Exception while loading YAML from Git", e.getMessage(), e);
      throw new InvalidRequestException(e.getMessage());
    }

    String identifier = from(entityYamlMap, "identifier", String.class);
    String kind = from(entityYamlMap, "kind", String.class);
    try {
      kind = catalogServiceHelper.validateAndSanitizeKind(kind);
      if (HIERARCHY_KIND.equals(kind)) {
        throw new InvalidRequestException("Kind hierarchy is not supported for import entity");
      }
      catalogServiceHelper.validateKindForCreateUpdateDelete(kind);
      String orgIdentifierFromYaml = from(entityYamlMap, "orgIdentifier", String.class);
      String projectIdentifierFromYaml = from(entityYamlMap, "projectIdentifier", String.class);
      if (!Objects.equals(orgIdentifier, orgIdentifierFromYaml)
          || !Objects.equals(projectIdentifier, projectIdentifierFromYaml)) {
        throw new InvalidRequestException(
            "Mismatch in orgIdentifier / projectIdentifier between query param and YAML input");
      }
      orgIdentifier = orgIdentifierFromYaml;
      projectIdentifier = projectIdentifierFromYaml;
      String orgName = catalogOrgProjectService.getOrgName(harnessAccount, orgIdentifier);
      String projectName = catalogOrgProjectService.getProjectName(harnessAccount, orgIdentifier, projectIdentifier);
      identifier = catalogServiceHelper.validateAndSanitizeIdentifier(identifier);
      catalogServiceHelper.checkCreateRbac(harnessAccount, orgIdentifier, projectIdentifier, kind,
          CatalogUtils.entityRef(kind, orgIdentifier, projectIdentifier, identifier));
      catalogServiceHelper.validateIdentifierPattern(identifier, kind, from(entityYamlMap, "type", String.class));
      KindEntity kindEntity = kindServiceHelper.kindEntity(harnessAccount, kind);
      catalogServiceHelper.validateAgainstJsonSchema(kind, entityYaml, kindEntity.getSchema());
      ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
      entityYaml = catalogServiceHelper.resolveExpressionsInEntityYaml(harnessAccount, entityYaml);
      entityYaml = catalogServiceHelper.resolveMembersForCustomUserGroup(kind, entityYaml);
      catalogServiceHelper.validateWorkflowNoCaseCollidingKeys(kind, YamlUtils.loadYamlStringAsMap(entityYaml));
      Set<String> groupingKinds = kindServiceHelper.groupingKinds(harnessAccount);
      CatalogEntity catalogEntity =
          CatalogMapper.yamlToEntity(scopeInfo, identifier, kind, entityYaml, null, groupingKinds);
      catalogServiceHelper.validateAndPopulateIsCustomForCustomUserGroup(catalogEntity);
      catalogEntity.setOwner(catalogServiceHelper.resolveOwner(harnessAccount, catalogEntity.getOwner()));
      catalogServiceHelper.validateOwnerScope(
          CatalogUtils.getScope(catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier()),
          catalogEntity.getOwner());
      catalogServiceHelper.validateParentTeam(CatalogUtils.entityRef(catalogEntity),
          !isEmpty(catalogEntity.getSpec()) ? (String) catalogEntity.getSpec().get(PARENT) : null);
      idpGitXHelper.addGitParamsToOverrideEntity(catalogEntity, scopeInfo);
      List<CatalogEntity> referencedEntities = relationsProcessor.establishRelations(catalogEntity);
      Pair<Boolean, String> validCatalog = getReadValidationAndSourceLocationDetails(catalogEntity, null, false);
      String sourceUrlConnectorError = null;
      if (validCatalog != null) {
        if (!validCatalog.getLeft()) {
          log.warn("Connector used in entity [{}] does not have read permission. Source URL: {}",
              catalogEntity.getIdentifier(), validCatalog.getRight());
          sourceUrlConnectorError = "Connector used in entity for source URL does not have read permission";
        } else {
          populateSourceLocationUrlInSpecOfEntity(catalogEntity, validCatalog.getRight());
        }
      }
      populateEntityStatusForSourceLocationConnector(catalogEntity, sourceUrlConnectorError);
      catalogServiceHelper.validateSystemScope(catalogEntity);
      GovernanceMetadata governanceMetadata =
          evaluateOpaGovernance(catalogEntity, OpaConstants.OPA_EVALUATION_ACTION_SAVE);
      if (governanceMetadata != null && governanceMetadata.getDeny()) {
        EntityResponse entityResponse = new EntityResponse();
        entityResponse.setGovernanceMetadata(governanceMetadata);
        return entityResponse;
      }
      List<CatalogEntity> entities = new ArrayList<>();
      entities.add(catalogEntity);
      entities.addAll(referencedEntities);
      transactionHelper.performTransaction(() -> {
        if (catalogEntity.getKind().equals(WORKFLOW_KIND)) {
          outboxService.save(new WorkflowCreateEvent(
              scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
        } else if (catalogEntity.getKind().equals(ENVIRONMENT_KIND)) {
          outboxService.save(new EnvironmentCreateEvent(
              scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
        } else if (catalogEntity.getKind().equals(ENVIRONMENT_BLUEPRINT_KIND)) {
          outboxService.save(new EnvironmentBlueprintCreateEvent(
              scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
        } else {
          outboxService.save(new CatalogCreateEvent(
              scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
        }
        createOutboxUpdateEventForReferencedEntities(referencedEntities);
        entities.forEach(entity -> entity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(entity)));
        catalogEntityRepository.saveAll(entities);
        harnessToIDPHelper.harnessToIdpSync(List.of(catalogEntity), harnessAccount, CREATE_ACTION);

        // Publish setup usage for Environment -> Environment Blueprint relation on create
        if (ENVIRONMENT_KIND.equals(catalogEntity.getKind())) {
          Map<String, Object> spec = catalogEntity.getSpec();
          Map<String, Object> envBlueprint = spec != null ? from(spec, "environmentBlueprint", Map.class) : null;
          String envBlueprintIdentifier = envBlueprint != null ? from(envBlueprint, "identifier", String.class) : null;
          String envBlueprintVersion = envBlueprint != null ? from(envBlueprint, "version", String.class) : null;
          if (!isEmpty(envBlueprintIdentifier) && !isEmpty(envBlueprintVersion)) {
            String[] bpScope = CommonUtils.resolveScopeFromIdentifier(
                envBlueprintIdentifier, catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier());
            setupUsageProducer.publishEnvironmentBluePrintSetupUsages(harnessAccount, catalogEntity.getOrgIdentifier(),
                catalogEntity.getProjectIdentifier(),
                catalogServiceHelper.getBlueprintVersionIdentifier(envBlueprintIdentifier, envBlueprintVersion),
                catalogEntity.getIdentifier(), bpScope[0], bpScope[1]);
          }
        }

        return null;
      });
      idpToHarnessHelper.sendCatalogEventsToRedis(Collections.singletonList(catalogEntity), CREATE_ACTION);
      idpToHarnessHelper.sendCatalogEventsToRedis(referencedEntities, UPDATE_ACTION);
      return CatalogMapper.entityToResponse((catalogEntity instanceof GitReferencedCatalogEntity)
              ? CatalogMapper.yamlToEntity(scopeInfo, identifier, kind, catalogEntity.getYaml(), null, groupingKinds)
              : catalogEntity,
          orgName, projectName, null, kindEntity.getIcon(), null, false);
    } catch (DuplicateKeyException e) {
      assert identifier != null;
      String errorMessage =
          String.format("Entity with identifier [%s] already exists for the same kind", identifier.toLowerCase());
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while fetching YAML: [%s]", identifier), e);
      throw e;
    } catch (Exception ex) {
      throwIfMongoWriteConflictError(ex);
      log.error("Error in create entity. Exception = {}", ex.getMessage(), ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  @Override
  public void moveEntity(
      String harnessAccount, String orgIdentifier, String projectIdentifier, String entityRef, EntityMoveRequest body) {
    try {
      Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
      String kind = catalogServiceHelper.validateAndSanitizeKind(kindScopeIdentifier.getLeft());
      if (HIERARCHY_KIND.equals(kind)) {
        throw new InvalidRequestException("Kind hierarchy is not supported for move entity");
      }
      String identifier = catalogServiceHelper.validateAndSanitizeIdentifier(kindScopeIdentifier.getRight());
      String orgIdentifierFromScope, projectIdentifierFromScope;
      String scope = kindScopeIdentifier.getMiddle();
      String[] scopeSplit = scope.split("\\.");
      orgIdentifierFromScope = scopeSplit.length >= 2 ? scopeSplit[1] : null;
      projectIdentifierFromScope = scopeSplit.length == 3 ? scopeSplit[2] : null;
      if ((!isEmpty(orgIdentifier) && !orgIdentifier.equals(orgIdentifierFromScope))
          || (!isEmpty(projectIdentifier) && !projectIdentifier.equals(projectIdentifierFromScope))) {
        throw new InvalidRequestException(
            "Organization / Project from request query params doesn't match with the details provided for scope");
      }
      orgIdentifier = orgIdentifierFromScope;
      projectIdentifier = projectIdentifierFromScope;

      ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
      CatalogEntity catalogEntity = catalogServiceHelper.catalogEntity(scopeInfo.getUniqueId(), kind, identifier);
      catalogServiceHelper.checkRbacWithOwnerFallback(harnessAccount, entityRef, catalogEntity.getOwner(), "edit");
      catalogServiceHelper.validateUpdateDeleteForCustomUserGroup(catalogEntity);
      if (EntityMoveOperationType.INLINE_TO_REMOTE.equals(body.getEntityMoveOperationType())) {
        GitAwareContextHelper.populateGitDetails(idpGitXHelper.populateGitMoveDetails(body.getGitDetails()));
        transactionHelper.performTransaction(() -> {
          idpGitXHelper.pushToGit(catalogServiceHelper.convertInlineToGitEntity(catalogEntity), scopeInfo);
          catalogEntityRepository.convertInlineToGit(scopeInfo, kind, identifier);
          return null;
        });
      } else {
        log.error("Invalid entity move operation provided: {}", body.getEntityMoveOperationType());
        throw new InvalidRequestException(
            "Invalid entity move operation provided " + body.getEntityMoveOperationType());
      }
    } catch (Exception e) {
      throwIfMongoWriteConflictError(e);
      log.error("Error in moving entity. Exception = {}", e.getMessage(), e);
      throw new InvalidRequestException(e.getMessage());
    }
  }

  @Override
  public EntityResponse getEntity(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String entityRef, boolean resolvePlaceholders, boolean loadFromFallbackBranch, boolean loadFromCache) {
    return getEntity(harnessAccount, orgIdentifier, projectIdentifier, entityRef, resolvePlaceholders,
        loadFromFallbackBranch, loadFromCache, true);
  }

  @Override
  public EntityResponse getEntity(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String entityRef, boolean resolvePlaceholders, boolean loadFromFallbackBranch, boolean loadFromCache,
      boolean shouldValidateRBAC) {
    try {
      Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
      String kind = catalogServiceHelper.validateAndSanitizeKind(kindScopeIdentifier.getLeft());
      String identifier = kindScopeIdentifier.getRight();
      if (!(Objects.equals(kind, "user") || Objects.equals(kind, "group"))) {
        catalogServiceHelper.validateAndSanitizeIdentifier(identifier);
      } else {
        catalogServiceHelper.validateIdentifier(identifier);
      }
      String orgIdentifierFromScope, projectIdentifierFromScope;
      String scope = kindScopeIdentifier.getMiddle();
      String[] scopeSplit = scope.split("\\.");
      orgIdentifierFromScope = scopeSplit.length >= 2 ? scopeSplit[1] : null;
      projectIdentifierFromScope = scopeSplit.length == 3 ? scopeSplit[2] : null;
      if ((!isEmpty(orgIdentifier) && !orgIdentifier.equals(orgIdentifierFromScope))
          || (!isEmpty(projectIdentifier) && !projectIdentifier.equals(projectIdentifierFromScope))) {
        throw new InvalidRequestException(
            "Organization / Project from request query params doesn't match with the details provided for scope");
      }
      orgIdentifier = orgIdentifierFromScope;
      projectIdentifier = projectIdentifierFromScope;
      String orgName = catalogOrgProjectService.getOrgName(harnessAccount, orgIdentifier);
      String projectName = catalogOrgProjectService.getProjectName(harnessAccount, orgIdentifier, projectIdentifier);
      ScopeInfo scopeInfo = catalogScopeResolver.resolveSingleScopeInfo(harnessAccount, scope);
      CatalogEntity catalogEntity = catalogServiceHelper.catalogEntityFromGit(
          scopeInfo.getUniqueId(), kind, identifier, loadFromCache, loadFromFallbackBranch);
      String owner = catalogServiceHelper.resolveOwner(harnessAccount, catalogEntity.getOwner());
      if (shouldValidateRBAC) {
        catalogServiceHelper.checkRbacWithOwnerFallback(harnessAccount, entityRef, owner, "view");
      }
      catalogEntity.setOwner(owner);
      String userFavoriteEntityRefs = catalogServiceHelper.getUserFavoriteEntityRefs(
          harnessAccount, orgIdentifier, projectIdentifier, IDPENTITY.name());
      List<ScoreEntity> scoreEntities = new ArrayList<>();
      Map<String, String> scorecardIdToNameMap = new HashMap<>();
      if (CORE_KINDS.contains(catalogEntity.getKind()) || catalogEntity.getKind().equals("group")) {
        List<ScorecardAndChecks> scorecardAndChecks = scorecardService.getAllScorecardAndChecks(harnessAccount, null);
        scoreEntities = fetchScoresForEntity(catalogEntity, scorecardAndChecks, harnessAccount);
        scorecardIdToNameMap.putAll(scorecardAndChecks.stream().collect(Collectors.toMap(scorecard
            -> scorecard.getScorecard().getIdentifier(),
            scorecard -> scorecard.getScorecard().getName(), (a, b) -> a)));
      }
      idpGitXHelper.populateGitDetailsIfRequired(catalogEntity);
      KindEntity kindEntity = kindServiceHelper.kindEntity(harnessAccount, kind);
      Set<String> groupingKinds = kindServiceHelper.groupingKinds(harnessAccount);
      EntityResponse entityResponse =
          CatalogMapper.entityToResponse((catalogEntity instanceof GitReferencedCatalogEntity)
                  ? CatalogMapper.yamlToEntity(scopeInfo, identifier, kind, catalogEntity.getYaml(),
                        catalogEntity.getDecorator(), groupingKinds)
                  : catalogEntity,
              orgName, projectName, userFavoriteEntityRefs, kindEntity.getIcon(),
              constructEntityScorecards(scoreEntities, scorecardIdToNameMap), resolvePlaceholders);
      entityResponse.setStoDetails(constructSTODetails(catalogEntity));
      String entityYaml = catalogEntity.getYaml();
      List<String> errorMessages = new ArrayList<>();
      try {
        catalogServiceHelper.validateAgainstJsonSchema(kind, entityYaml, kindEntity.getSchema());
      } catch (Exception e) {
        errorMessages.add(e.getMessage());
      }

      if (catalogEntity instanceof GitReferencedCatalogEntity) {
        entityResponse.setSpec(
            populateIsCustomUserGroupInSpec(entityResponse.getSpec(), entityResponse.getKindIdentifier()));
        entityResponse.getGitDetails().setConnectorRef(((GitReferencedCatalogEntity) catalogEntity).getConnectorRef());
        entityResponse.getGitDetails().setRepoUrl(((GitReferencedCatalogEntity) catalogEntity).getRepoURL());
        entityResponse.getGitDetails().setStoreType(GitDetails.StoreTypeEnum.REMOTE);

        try {
          catalogServiceHelper.validateSystemScope(catalogEntity);
          catalogServiceHelper.validateOwnerScope(
              CatalogUtils.getScope(catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier()),
              catalogEntity.getOwner());
          catalogServiceHelper.validateParentTeam(CatalogUtils.entityRef(catalogEntity),
              !isEmpty(catalogEntity.getSpec()) ? (String) catalogEntity.getSpec().get(PARENT) : null);
        } catch (Exception e) {
          errorMessages.add(e.getMessage());
        }

        GovernanceMetadata governanceMetadata =
            evaluateOpaGovernance(catalogEntity, OpaConstants.OPA_EVALUATION_ACTION_SAVE);
        if (governanceMetadata != null) {
          entityResponse.setGovernanceMetadata(governanceMetadata);
          if (governanceMetadata.getDeny()) {
            errorMessages.add("IDP catalog entity does not follow the governance policies");
          }
        }

        Map<String, Object> entityYamlMap = YamlUtils.loadYamlStringAsMap(entityYaml);
        String identifierFromYAML = from(entityYamlMap, "identifier", String.class);
        String kindFromYAML = from(entityYamlMap, "kind", String.class);
        if ((!isEmpty(identifierFromYAML) && !Objects.equals(identifier, identifierFromYAML))
            || (!isEmpty(kindFromYAML) && !Objects.equals(kind, kindFromYAML.toLowerCase()))) {
          errorMessages.add("Mismatch in identifier / kind between query param and YAML input");
        }
        String orgIdentifierFromYaml = from(entityYamlMap, "orgIdentifier", String.class);
        String projectIdentifierFromYaml = from(entityYamlMap, "projectIdentifier", String.class);
        if (!Objects.equals(orgIdentifier, orgIdentifierFromYaml)
            || !Objects.equals(projectIdentifier, projectIdentifierFromYaml)) {
          errorMessages.add("Mismatch in orgIdentifier / projectIdentifier between query param and YAML input");
        }
      }

      if (!isEmpty(errorMessages)) {
        EntityResponseEntityValidityDetails entityResponseEntityValidityDetails =
            new EntityResponseEntityValidityDetails();
        entityResponseEntityValidityDetails.setIsValid(false);
        entityResponseEntityValidityDetails.setErrorMessages(errorMessages);
        entityResponse.setEntityValidityDetails(entityResponseEntityValidityDetails);
      }
      return entityResponse;
    } catch (EntityNotFoundException ex) {
      log.error("Error in get entity. Exception = {}", ex.getMessage(), ex);
      throw ex;
    } catch (Exception ex) {
      log.error("Error in get entity. Exception = {}", ex.getMessage(), ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  @Override
  public List<EntityValidateResponse> validateYaml(String harnessAccount, EntityValidateRequest entityValidateRequest) {
    YamlValidationRequestDTO yamlValidationRequestDTO =
        YamlValidationRequestDTO.builder()
            .yaml(entityValidateRequest.getYaml())
            .repoName(entityValidateRequest.getGitDetails().getRepoName())
            .filePath(entityValidateRequest.getGitDetails().getFilePath())
            .branch(entityValidateRequest.getGitDetails().getBranch())
            .isDefaultBranch(entityValidateRequest.getGitDetails().isIsDefaultBranch())
            .build();
    GitReferencedCatalogEntity existingCatalogEntity;
    List<EntityValidateResponse> entityValidateResponses = new ArrayList<>();
    try (GitXFileValidationLogContext context = new GitXFileValidationLogContext(yamlValidationRequestDTO)) {
      existingCatalogEntity = (GitReferencedCatalogEntity) catalogEntityRepository.findByFilePathAndRepo(
          harnessAccount, yamlValidationRequestDTO.getFilePath(), yamlValidationRequestDTO.getRepoName());
      if (existingCatalogEntity == null) {
        log.error("No entity exists with file path: {}, repo: {}, branch: {}", yamlValidationRequestDTO.getFilePath(),
            yamlValidationRequestDTO.getRepoName(), yamlValidationRequestDTO.getBranch());
        EntityValidateResponse entityValidateResponse =
            constructEntityValidateResponseForEntityNotFound(yamlValidationRequestDTO);
        return List.of(entityValidateResponse);
      }
      try {
        String entityYaml = entityValidateRequest.getYaml();
        catalogServiceHelper.validateKindForCreateUpdateDelete(existingCatalogEntity.getKind());
        KindEntity kindEntity = kindServiceHelper.kindEntity(harnessAccount, existingCatalogEntity.getKind());
        catalogServiceHelper.validateAgainstJsonSchema(
            existingCatalogEntity.getKind(), entityYaml, kindEntity.getSchema());
        catalogServiceHelper.validateUpdateDeleteForCustomUserGroup(existingCatalogEntity);
        Map<String, Object> entityYamlMap = YamlUtils.loadYamlStringAsMap(entityYaml);
        String identifier = from(entityYamlMap, "identifier", String.class);
        identifier = catalogServiceHelper.validateAndSanitizeIdentifier(identifier);
        String kind = from(entityYamlMap, "kind", String.class);
        kind = catalogServiceHelper.validateAndSanitizeKind(kind);
        String orgIdentifier = from(entityYamlMap, "orgIdentifier", String.class);
        String projectIdentifier = from(entityYamlMap, "projectIdentifier", String.class);
        if (!Objects.equals(existingCatalogEntity.getIdentifier(), identifier)
            || !Objects.equals(existingCatalogEntity.getKind(), kind)) {
          throw new InvalidRequestException("Mismatch in identifier / kind between existing entity and YAML input");
        }
        if (!Objects.equals(existingCatalogEntity.getOrgIdentifier(), orgIdentifier)
            || !Objects.equals(existingCatalogEntity.getProjectIdentifier(), projectIdentifier)) {
          throw new InvalidRequestException(
              "Mismatch in orgIdentifier / projectIdentifier between existing entity and YAML input");
        }

        EntityValidateResponse entityValidateResponse = new EntityValidateResponse();
        entityValidateResponse.setIsValid(true);
        EntityValidateResponseEntityMetadata entityMetadata = new EntityValidateResponseEntityMetadata();
        entityMetadata.setIdentifier(identifier);
        entityMetadata.setEntityRef(CatalogUtils.entityRef(existingCatalogEntity));
        EntityValidateResponseEntityMetadataScope scope = new EntityValidateResponseEntityMetadataScope();
        scope.setAccountIdentifier(harnessAccount);
        scope.setOrgIdentifier(orgIdentifier);
        scope.setProjectIdentifier(projectIdentifier);
        entityMetadata.setScope(scope);
        entityValidateResponse.setEntityMetadata(entityMetadata);
        entityValidateResponses.add(entityValidateResponse);
        ScopeInfo scopeInfo = catalogScopeResolver.resolveSingleScopeInfo(
            harnessAccount, CatalogUtils.getScope(orgIdentifier, projectIdentifier));

        if ((yamlValidationRequestDTO.getIsDefaultBranch()
                || (yamlValidationRequestDTO.getBranch().equals(existingCatalogEntity.getFallBackBranch())
                    && catalogServiceHelper.yamlNotExistsInDefaultBranch(existingCatalogEntity,
                        idpGitXHelper.getDefaultBranch(existingCatalogEntity.getConnectorRef(),
                            existingCatalogEntity.getRepo(), scopeInfo, true))))
            && !entityYaml.equals(existingCatalogEntity.getYaml())) {
          try {
            GitAwareContextHelper.updateGitEntityContextWithRemoteStoreType();
            entityYaml = catalogServiceHelper.resolveExpressionsInEntityYaml(harnessAccount, entityYaml);
            entityYaml = catalogServiceHelper.resolveMembersForCustomUserGroup(kind, entityYaml);
            catalogServiceHelper.validateWorkflowNoCaseCollidingKeys(kind, YamlUtils.loadYamlStringAsMap(entityYaml));
            Set<String> groupingKinds = kindServiceHelper.groupingKinds(harnessAccount);
            CatalogEntity catalogEntity = CatalogMapper.yamlToEntity(
                scopeInfo, identifier, kind, entityYaml, existingCatalogEntity.getDecorator(), groupingKinds);
            catalogServiceHelper.validateAndPopulateIsCustomForCustomUserGroup(catalogEntity);
            catalogEntity.setOwner(catalogServiceHelper.resolveOwner(harnessAccount, catalogEntity.getOwner()));
            catalogServiceHelper.validateOwnerScope(
                CatalogUtils.getScope(catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier()),
                catalogEntity.getOwner());
            catalogServiceHelper.validateParentTeam(CatalogUtils.entityRef(catalogEntity),
                !isEmpty(catalogEntity.getSpec()) ? (String) catalogEntity.getSpec().get(PARENT) : null);
            catalogEntity.setId(existingCatalogEntity.getId());
            catalogEntity.setUniqueId(existingCatalogEntity.getUniqueId());
            catalogEntity.setCreatedAt(existingCatalogEntity.getCreatedAt());
            catalogEntity.setCreatedBy(existingCatalogEntity.getCreatedBy());
            idpGitXHelper.addGitParamsFromExistingEntity(catalogEntity, existingCatalogEntity);
            preserveSystemEntityRelations(catalogEntity, existingCatalogEntity);
            List<CatalogEntity> referencedEntities =
                relationsProcessor.updateRelations(existingCatalogEntity, catalogEntity);
            Pair<Boolean, String> validCatalog =
                getReadValidationAndSourceLocationDetails(catalogEntity, existingCatalogEntity, true);
            String sourceUrlConnectorError = null;
            if (validCatalog != null) {
              if (!validCatalog.getLeft()) {
                log.warn("Connector used in entity [{}] does not have read permission. Source URL: {}",
                    catalogEntity.getIdentifier(), validCatalog.getRight());
                sourceUrlConnectorError = "Connector used in entity for source URL does not have read permission";
              } else {
                populateSourceLocationUrlInSpecOfEntity(catalogEntity, validCatalog.getRight());
              }
            }
            populateEntityStatusForSourceLocationConnector(catalogEntity, sourceUrlConnectorError);
            catalogServiceHelper.validateSystemScope(catalogEntity);
            GovernanceMetadata governanceMetadata =
                evaluateOpaGovernance(catalogEntity, OpaConstants.OPA_EVALUATION_ACTION_SAVE);
            if (governanceMetadata != null && governanceMetadata.getDeny()) {
              throw new InvalidRequestException(
                  "IDP catalog entity does not follow the governance policies. Entity: " + identifier);
            }
            List<CatalogEntity> entities = new ArrayList<>();
            entities.add(catalogEntity);
            entities.addAll(referencedEntities);
            transactionHelper.performTransaction(() -> {
              if (catalogEntity.getKind().equals(WORKFLOW_KIND)) {
                outboxService.save(new WorkflowUpdateEvent(scopeInfo, catalogEntity.getYaml(),
                    existingCatalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
              } else if (catalogEntity.getKind().equals(ENVIRONMENT_KIND)) {
                outboxService.save(new EnvironmentUpdateEvent(scopeInfo, catalogEntity.getYaml(),
                    existingCatalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
              } else if (catalogEntity.getKind().equals(ENVIRONMENT_BLUEPRINT_KIND)) {
                outboxService.save(new EnvironmentBlueprintUpdateEvent(scopeInfo, catalogEntity.getYaml(),
                    existingCatalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
              } else {
                outboxService.save(new CatalogUpdateEvent(scopeInfo, catalogEntity.getYaml(),
                    existingCatalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
              }
              createOutboxUpdateEventForReferencedEntities(referencedEntities);
              entities.forEach(entity -> entity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(entity)));
              catalogEntityRepository.saveAll(entities);
              harnessToIDPHelper.harnessToIdpSync(List.of(catalogEntity), harnessAccount, UPDATE_ACTION);

              // If Environment's Environment Blueprint reference changed, update setup usages accordingly
              if (ENVIRONMENT_KIND.equals(catalogEntity.getKind())) {
                Map<String, Object> newSpec = catalogEntity.getSpec();
                Map<String, Object> oldSpec = existingCatalogEntity.getSpec();
                Map<String, Object> newEnvBlueprint =
                    newSpec != null ? from(newSpec, "environmentBlueprint", Map.class) : null;
                Map<String, Object> oldEnvBlueprint =
                    oldSpec != null ? from(oldSpec, "environmentBlueprint", Map.class) : null;
                String newEnvBlueprintIdentifier =
                    newEnvBlueprint != null ? from(newEnvBlueprint, "identifier", String.class) : null;
                String newEnvBlueprintVersion =
                    newEnvBlueprint != null ? from(newEnvBlueprint, "version", String.class) : null;
                String oldEnvBlueprintIdentifier =
                    oldEnvBlueprint != null ? from(oldEnvBlueprint, "identifier", String.class) : null;
                String oldEnvBlueprintVersion =
                    oldEnvBlueprint != null ? from(oldEnvBlueprint, "version", String.class) : null;
                // Construct full blueprint version identifiers for comparison
                String newBlueprintVersionId = (!isEmpty(newEnvBlueprintIdentifier) && !isEmpty(newEnvBlueprintVersion))
                    ? catalogServiceHelper.getBlueprintVersionIdentifier(
                          newEnvBlueprintIdentifier, newEnvBlueprintVersion)
                    : null;
                String oldBlueprintVersionId = (!isEmpty(oldEnvBlueprintIdentifier) && !isEmpty(oldEnvBlueprintVersion))
                    ? catalogServiceHelper.getBlueprintVersionIdentifier(
                          oldEnvBlueprintIdentifier, oldEnvBlueprintVersion)
                    : null;

                if (!Objects.equals(oldBlueprintVersionId, newBlueprintVersionId)) {
                  // Remove old usage (if any)
                  setupUsageProducer.deleteEnvironmentSetupUsage(
                      harnessAccount, orgIdentifier, projectIdentifier, catalogEntity.getIdentifier());
                  // Add new usage (if any)
                  if (newBlueprintVersionId != null) {
                    String[] bpScope = CommonUtils.resolveScopeFromIdentifier(
                        newEnvBlueprintIdentifier, orgIdentifier, projectIdentifier);
                    setupUsageProducer.publishEnvironmentBluePrintSetupUsages(harnessAccount, orgIdentifier,
                        projectIdentifier, newBlueprintVersionId, catalogEntity.getIdentifier(), bpScope[0],
                        bpScope[1]);
                  }
                }
              }

              return null;
            });
            idpToHarnessHelper.sendCatalogEventsToRedis(List.of(catalogEntity), UPDATE_ACTION);
            entities.remove(catalogEntity);
            idpToHarnessHelper.sendCatalogEventsToRedis(entities, UPDATE_ACTION);
          } catch (Exception e) {
            log.error("Error while updating entity through webhook event. Exception = {}", e.getMessage(), e);
          }
        }
      } catch (Exception e) {
        EntityValidateResponse entityValidateResponse =
            constructEntityValidateResponseException(existingCatalogEntity, e);
        entityValidateResponses.add(entityValidateResponse);
      }
    }
    return entityValidateResponses;
  }

  @Override
  public EntityResponse updateEntity(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String entityRef, EntityUpdateRequest body) {
    return updateEntity(harnessAccount, orgIdentifier, projectIdentifier, entityRef, body, true, true, true, false);
  }

  @Override
  public EntityResponse updateEntity(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String entityRef, EntityUpdateRequest body, boolean shouldValidateRBAC, boolean shouldUpdateOnGit,
      boolean shouldCheckExistingSourceValidation, boolean metadataEnrichmentByUser) {
    return updateEntity(harnessAccount, orgIdentifier, projectIdentifier, entityRef, body, shouldValidateRBAC,
        shouldUpdateOnGit, shouldCheckExistingSourceValidation, null, false, null, false)
        .getLeft();
  }

  @Override
  public Pair<EntityResponse, EntityVersionResponse> updateEntity(String harnessAccount, String orgIdentifier,
      String projectIdentifier, String entityRef, EntityUpdateRequest body, boolean shouldValidateRBAC,
      boolean shouldUpdateOnGit, boolean shouldCheckExistingSourceValidation,
      EntityVersionUpdateRequest entityVersionUpdateRequest, boolean versionedEntity, String version,
      boolean metadataEnrichmentByUser) {
    try {
      Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);

      String entityYaml = versionedEntity ? entityVersionUpdateRequest.getYaml() : body.getYaml();

      if (versionedEntity && entityYaml == null) {
        String kind = kindScopeIdentifier.getLeft();
        String scope = kindScopeIdentifier.getMiddle();
        String identifier = kindScopeIdentifier.getRight();

        // Load existing version to get its YAML
        EntityVersionResponse existingVersion = catalogVersionService.getEntityVersion(
            harnessAccount, orgIdentifier, projectIdentifier, scope, kind, identifier, version);
        if (existingVersion == null) {
          throw new InvalidRequestException(String.format("Cannot update version %s: version not found", version));
        }
        entityYaml = existingVersion.getYaml();

        if (entityYaml == null) {
          throw new InvalidRequestException(
              String.format("Cannot update version %s: existing version has no YAML stored", version));
        }
      }

      catalogServiceHelper.validateMultipleDefinitionInYaml(entityYaml);

      Map<String, Object> entityYamlMap = YamlUtils.loadYamlStringAsMap(entityYaml);
      String kind = from(entityYamlMap, "kind", String.class);

      kind = catalogServiceHelper.validateAndSanitizeKind(kind);

      if (kind.equals(HIERARCHY_KIND)) {
        idpCommonService.allowCreateUpdateDeleteOnHierarchyKindEntity(harnessAccount, metadataEnrichmentByUser, false);
      }

      if (versionedEntity) {
        catalogServiceHelper.validateKindForVersioning(kind);
        catalogServiceHelper.validateVersionLabel(version);
      }
      catalogServiceHelper.validateKindForCreateUpdateDelete(kind);
      String identifier = from(entityYamlMap, "identifier", String.class);
      identifier = catalogServiceHelper.validateAndSanitizeIdentifier(identifier);
      if (!kindScopeIdentifier.getLeft().equals(kind) || !kindScopeIdentifier.getRight().equals(identifier)) {
        throw new InvalidRequestException("Mismatch in kind / identifier between entity ref and YAML input");
      }

      String orgIdentifierFromScope, projectIdentifierFromScope;
      String scope = kindScopeIdentifier.getMiddle();
      String[] scopeSplit = scope.split("\\.");
      orgIdentifierFromScope = scopeSplit.length >= 2 ? scopeSplit[1] : null;
      projectIdentifierFromScope = scopeSplit.length == 3 ? scopeSplit[2] : null;
      if ((!isEmpty(orgIdentifier) && !orgIdentifier.equals(orgIdentifierFromScope))
          || (!isEmpty(projectIdentifier) && !projectIdentifier.equals(projectIdentifierFromScope))) {
        throw new InvalidRequestException(
            "Organization / Project from request query params doesn't match with the details provided for scope");
      }

      String orgIdentifierFromYaml = from(entityYamlMap, "orgIdentifier", String.class);
      String projectIdentifierFromYaml = from(entityYamlMap, "projectIdentifier", String.class);
      if ((!isEmpty(orgIdentifier) && !orgIdentifier.equals(orgIdentifierFromYaml))
          || (!isEmpty(projectIdentifier) && !projectIdentifier.equals(projectIdentifierFromYaml))) {
        throw new InvalidRequestException(
            "Mismatch in orgIdentifier / projectIdentifier between query param and YAML input");
      }
      if ((!isEmpty(orgIdentifierFromYaml) && !orgIdentifierFromYaml.equals(orgIdentifierFromScope))
          || (!isEmpty(projectIdentifierFromYaml) && !projectIdentifierFromYaml.equals(projectIdentifierFromScope))) {
        throw new InvalidRequestException(
            "Organization / Project from YAML input doesn't match with the details provided for scope");
      }

      orgIdentifier = orgIdentifierFromScope;
      projectIdentifier = projectIdentifierFromScope;

      String orgName = catalogOrgProjectService.getOrgName(harnessAccount, orgIdentifier);
      String projectName = catalogOrgProjectService.getProjectName(harnessAccount, orgIdentifier, projectIdentifier);

      String owner = catalogServiceHelper.resolveOwner(harnessAccount, from(entityYamlMap, "owner", String.class));
      catalogServiceHelper.validateOwnerScope(CatalogUtils.getScope(orgIdentifier, projectIdentifier), owner);
      catalogServiceHelper.validateParentTeam(
          kind + ":" + scope + "/" + identifier, from(entityYamlMap, "spec.parent", String.class));
      if (shouldValidateRBAC) {
        catalogServiceHelper.checkRbacWithOwnerFallback(harnessAccount, entityRef, owner, "edit");
      }
      ScopeInfo scopeInfo = catalogScopeResolver.resolveSingleScopeInfo(harnessAccount, scope);
      CatalogEntity existingCatalogEntity =
          catalogServiceHelper.catalogEntityFromGit(scopeInfo.getUniqueId(), kind, identifier, false, false);
      catalogServiceHelper.validateForModifiableAction(existingCatalogEntity.getKind(), kind,
          existingCatalogEntity.getIdentifier(), identifier, existingCatalogEntity.getOrgIdentifier(), orgIdentifier,
          existingCatalogEntity.getProjectIdentifier(), projectIdentifier);

      KindEntity kindEntity = kindServiceHelper.kindEntity(harnessAccount, existingCatalogEntity.getKind());
      catalogServiceHelper.validateAgainstJsonSchema(
          existingCatalogEntity.getKind(), entityYaml, kindEntity.getSchema());

      if (versionedEntity) {
        CatalogEntity catalogEntityForOpa = CatalogMapper.yamlToEntity(scopeInfo, identifier, kind, entityYaml, null);
        GovernanceMetadata governanceMetadata =
            evaluateOpaGovernance(catalogEntityForOpa, OpaConstants.OPA_EVALUATION_ACTION_SAVE);
        if (governanceMetadata != null && governanceMetadata.getDeny()) {
          EntityVersionResponse entityVersionResponse = new EntityVersionResponse();
          entityVersionResponse.setGovernanceMetadata(governanceMetadata);
          return Pair.of(null, entityVersionResponse);
        }
        EntityVersionResponse entityVersionResponse = catalogVersionService.updateEntityVersion(harnessAccount,
            orgIdentifier, projectIdentifier, version, entityVersionUpdateRequest, existingCatalogEntity);
        if (governanceMetadata != null) {
          entityVersionResponse.setGovernanceMetadata(governanceMetadata);
        }
        return Pair.of(null, entityVersionResponse);
      }

      Map<String, Object> harnessService = !isEmpty(existingCatalogEntity.getSpec())
          ? (Map<String, Object>) existingCatalogEntity.getSpec().get("harnessService")
          : new HashMap<>();
      String ciCdPluginAnnotation = !isEmpty(existingCatalogEntity.getMetadata())
              && existingCatalogEntity.getMetadata().get("annotations") != null
              && ((Map<String, Object>) existingCatalogEntity.getMetadata().get("annotations"))
                      .get("harness.io/services")
                  != null
          ? ((Map<String, Object>) existingCatalogEntity.getMetadata().get("annotations"))
                .get("harness.io/services")
                .toString()
          : null;
      if (!isEmpty(harnessService) && shouldValidateRBAC) {
        if (isEmpty(from(entityYamlMap, "spec.harnessService", Map.class))) {
          throw new InvalidRequestException(existingCatalogEntity.getName()
              + (" is managed using Harness CD. spec.harnessService cannot be modified. Learn more about Catalog and "
                  + "Harness CD integration. "
                  + "https://developer.harness.io/docs/internal-developer-portal/catalog/catalog-discovery/"
                  + "harness-cd"));
        }
        if (!Objects.equals(from(entityYamlMap, "spec.harnessService", Map.class), harnessService)) {
          throw new InvalidRequestException(existingCatalogEntity.getName()
              + (" is managed using Harness CD. spec.harnessService cannot be modified. Learn more about Catalog and "
                  + "Harness CD integration. "
                  + "https://developer.harness.io/docs/internal-developer-portal/catalog/catalog-discovery/"
                  + "harness-cd"));
        }

        if (isEmpty(from(entityYamlMap, "metadata.annotations", Map.class).get("harness.io/services").toString())) {
          throw new InvalidRequestException(existingCatalogEntity.getName()
              + (" is managed using Harness CD. metadata.annotations.harness.io/services cannot be modified. Learn "
                  + "more about Catalog and Harness CD integration. "
                  + "https://developer.harness.io/docs/internal-developer-portal/catalog/catalog-discovery/"
                  + "harness-cd"));
        }
        if (!Objects.equals(
                from(entityYamlMap, "metadata.annotations", Map.class).get("harness.io/services").toString(),
                ciCdPluginAnnotation)) {
          throw new InvalidRequestException(existingCatalogEntity.getName()
              + (" is managed using Harness CD. metadata.annotations.harness.io/services cannot be modified. Learn "
                  + "more about Catalog and Harness CD integration. "
                  + "https://developer.harness.io/docs/internal-developer-portal/catalog/catalog-discovery/"
                  + "harness-cd"));
        }
      }
      catalogServiceHelper.validateUpdateDeleteForCustomUserGroup(existingCatalogEntity);
      entityYaml = catalogServiceHelper.resolveExpressionsInEntityYaml(harnessAccount, entityYaml);
      entityYaml = catalogServiceHelper.resolveMembersForCustomUserGroup(kind, entityYaml);
      catalogServiceHelper.validateWorkflowNoCaseCollidingKeys(kind, YamlUtils.loadYamlStringAsMap(entityYaml));
      if (existingCatalogEntity instanceof GitReferencedCatalogEntity) {
        GitAwareContextHelper.updateGitEntityContextWithRemoteStoreType();
      }
      Set<String> groupingKinds = kindServiceHelper.groupingKinds(harnessAccount);
      CatalogEntity catalogEntity = CatalogMapper.yamlToEntity(
          scopeInfo, identifier, kind, entityYaml, existingCatalogEntity.getDecorator(), groupingKinds);
      catalogEntity.setOwner(owner);
      catalogEntity.setId(existingCatalogEntity.getId());
      catalogEntity.setUniqueId(existingCatalogEntity.getUniqueId());
      catalogEntity.setCreatedAt(existingCatalogEntity.getCreatedAt());
      catalogEntity.setCreatedBy(existingCatalogEntity.getCreatedBy());
      catalogServiceHelper.validateAndPopulateIsCustomForCustomUserGroup(catalogEntity);
      idpGitXHelper.addGitParamsFromExistingEntity(catalogEntity, existingCatalogEntity);
      preserveSystemEntityRelations(catalogEntity, existingCatalogEntity);
      List<CatalogEntity> referencedEntities = relationsProcessor.updateRelations(existingCatalogEntity, catalogEntity);
      resolvePlaceholders(catalogEntity);
      catalogServiceHelper.validateSystemScope(catalogEntity);
      GovernanceMetadata governanceMetadata =
          evaluateOpaGovernance(catalogEntity, OpaConstants.OPA_EVALUATION_ACTION_SAVE);
      if (governanceMetadata != null && governanceMetadata.getDeny()) {
        EntityResponse entityResponse = new EntityResponse();
        entityResponse.setGovernanceMetadata(governanceMetadata);
        return Pair.of(entityResponse, null);
      }

      Pair<Boolean, String> validCatalog = getReadValidationAndSourceLocationDetails(
          catalogEntity, existingCatalogEntity, shouldCheckExistingSourceValidation);
      String sourceUrlConnectorError = null;
      if (validCatalog != null) {
        if (!validCatalog.getLeft()) {
          log.warn("Connector used in entity [{}] does not have read permission. Source URL: {}",
              catalogEntity.getIdentifier(), validCatalog.getRight());
          sourceUrlConnectorError = "Connector used in entity for source URL does not have read permission";
        } else {
          populateSourceLocationUrlInSpecOfEntity(catalogEntity, validCatalog.getRight());
        }
        populateEntityStatusForSourceLocationConnector(catalogEntity, sourceUrlConnectorError);
      }
      List<CatalogEntity> entities = new ArrayList<>();
      String defaultBranch = (catalogEntity instanceof GitReferencedCatalogEntity)
          ? idpGitXHelper.getDefaultBranch(((GitReferencedCatalogEntity) catalogEntity).getConnectorRef(),
                ((GitReferencedCatalogEntity) catalogEntity).getRepo(), scopeInfo, true)
          : null;
      transactionHelper.performTransaction(() -> {
        if (shouldUpdateOnGit) {
          idpGitXHelper.updateGit(catalogEntity, scopeInfo);
        }
        if (catalogEntity instanceof InlineCatalogEntity
            || (catalogEntity instanceof GitReferencedCatalogEntity
                && (defaultBranch.equals(GitAwareContextHelper.getBranchInRequest())
                    || (existingCatalogEntity instanceof GitReferencedCatalogEntity
                        && ((GitReferencedCatalogEntity) existingCatalogEntity)
                               .getFallBackBranch()
                               .equals(GitAwareContextHelper.getBranchInRequest())
                        && catalogServiceHelper.yamlNotExistsInDefaultBranch(catalogEntity, defaultBranch))))) {
          entities.add(catalogEntity);
          entities.addAll(referencedEntities);
          if (catalogEntity.getKind().equals(WORKFLOW_KIND)) {
            outboxService.save(new WorkflowUpdateEvent(scopeInfo, catalogEntity.getYaml(),
                existingCatalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
          } else if (catalogEntity.getKind().equals(ENVIRONMENT_KIND)) {
            outboxService.save(new EnvironmentUpdateEvent(scopeInfo, catalogEntity.getYaml(),
                existingCatalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
          } else if (catalogEntity.getKind().equals(ENVIRONMENT_BLUEPRINT_KIND)) {
            outboxService.save(new EnvironmentBlueprintUpdateEvent(scopeInfo, catalogEntity.getYaml(),
                existingCatalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
          } else if (catalogEntity.getKind().equals(GROUP_KIND)) {
            outboxService.save(new TeamUpdateEvent(scopeInfo, catalogEntity.getYaml(), existingCatalogEntity.getYaml(),
                catalogEntity.getKind(), catalogEntity.getIdentifier()));
          } else {
            outboxService.save(new CatalogUpdateEvent(scopeInfo, catalogEntity.getYaml(),
                existingCatalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
          }
          createOutboxUpdateEventForReferencedEntities(referencedEntities);
          entities.forEach(entity -> entity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(entity)));
          catalogEntityRepository.saveAll(entities);
          if (ENVIRONMENT_KIND.equals(catalogEntity.getKind())) {
            Map<String, Object> newSpec = catalogEntity.getSpec();
            Map<String, Object> oldSpec = existingCatalogEntity.getSpec();
            Map<String, Object> newEnvBlueprint =
                newSpec != null ? from(newSpec, "environmentBlueprint", Map.class) : null;
            Map<String, Object> oldEnvBlueprint =
                oldSpec != null ? from(oldSpec, "environmentBlueprint", Map.class) : null;
            String newEnvBlueprintIdentifier =
                newEnvBlueprint != null ? from(newEnvBlueprint, "identifier", String.class) : null;
            String newEnvBlueprintVersion =
                newEnvBlueprint != null ? from(newEnvBlueprint, "version", String.class) : null;
            String oldEnvBlueprintIdentifier =
                oldEnvBlueprint != null ? from(oldEnvBlueprint, "identifier", String.class) : null;
            String oldEnvBlueprintVersion =
                oldEnvBlueprint != null ? from(oldEnvBlueprint, "version", String.class) : null;

            if (!isEmpty(newEnvBlueprintIdentifier) && !isEmpty(oldEnvBlueprintIdentifier)
                && !isEmpty(newEnvBlueprintVersion) && !isEmpty(oldEnvBlueprintVersion)) {
              if (!Objects.equals(catalogServiceHelper.getBlueprintVersionIdentifier(
                                      oldEnvBlueprintIdentifier, oldEnvBlueprintVersion),
                      catalogServiceHelper.getBlueprintVersionIdentifier(
                          newEnvBlueprintIdentifier, newEnvBlueprintVersion))) {
                String[] bpScope = CommonUtils.resolveScopeFromIdentifier(
                    newEnvBlueprintIdentifier, catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier());
                setupUsageProducer.deleteEnvironmentSetupUsage(harnessAccount, catalogEntity.getOrgIdentifier(),
                    catalogEntity.getProjectIdentifier(), catalogEntity.getIdentifier());
                setupUsageProducer.publishEnvironmentBluePrintSetupUsages(harnessAccount,
                    catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier(),
                    catalogServiceHelper.getBlueprintVersionIdentifier(
                        newEnvBlueprintIdentifier, newEnvBlueprintVersion),
                    catalogEntity.getIdentifier(), bpScope[0], bpScope[1]);
              }
            }
          }
          harnessToIDPHelper.harnessToIdpSync(List.of(catalogEntity), harnessAccount, UPDATE_ACTION);
        }
        return null;
      });
      if (!isEmpty(entities)) {
        idpToHarnessHelper.sendCatalogEventsToRedis(List.of(catalogEntity), UPDATE_ACTION);
        entities.remove(catalogEntity);
        idpToHarnessHelper.sendCatalogEventsToRedis(entities, UPDATE_ACTION);
        catalogServiceHelper.publishAsyncComputationEvent(
            harnessAccount, null, CatalogUtils.getEntityUUId(catalogEntity));
      }
      String userFavoriteEntityRefs = catalogServiceHelper.getUserFavoriteEntityRefs(
          harnessAccount, orgIdentifier, projectIdentifier, IDPENTITY.name());
      Map<String, String> scorecardIdToNameMap = new HashMap<>();
      List<ScorecardAndChecks> scorecardAndChecks = scorecardService.getAllScorecardAndChecks(harnessAccount, null);
      List<ScoreEntity> scoreEntities = fetchScoresForEntity(catalogEntity, scorecardAndChecks, harnessAccount);
      scorecardIdToNameMap.putAll(scorecardAndChecks.stream().collect(Collectors.toMap(scorecard
          -> scorecard.getScorecard().getIdentifier(),
          scorecard -> scorecard.getScorecard().getName(), (a, b) -> a)));
      EntityResponse entityResponse =
          CatalogMapper.entityToResponse((catalogEntity instanceof GitReferencedCatalogEntity)
                  ? CatalogMapper.yamlToEntity(scopeInfo, identifier, kind, catalogEntity.getYaml(),
                        catalogEntity.getDecorator(), groupingKinds)
                  : catalogEntity,
              orgName, projectName, userFavoriteEntityRefs, kindEntity.getIcon(),
              constructEntityScorecards(scoreEntities, scorecardIdToNameMap), false);
      if (governanceMetadata != null) {
        entityResponse.setGovernanceMetadata(governanceMetadata);
      }
      return Pair.of(entityResponse, null);

    } catch (Exception ex) {
      throwIfMongoWriteConflictError(ex);
      log.error("Error in update entity. Exception = {}", ex.getMessage(), ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  @Override
  public void updateSourceCodeInEntityOnConnectorUpdate(
      String harnessAccount, String orgIdentifier, String projectIdentifier, String connectorRef) {
    try {
      List<KindEntity> kindEntities = kindServiceHelper.findByAccountIdentifierIn(harnessAccount);
      List<String> supportedSourceLocationKinds =
          kindEntities.stream()
              .map(KindEntity::getIdentifier)
              .filter(identifier -> !SOURCE_LOCATION_UNSUPPORTED_KINDS.contains(identifier))
              .toList();
      List<CatalogEntity> catalogEntities = catalogEntityRepository.getEntitiesForArbitraryFields(harnessAccount,
          Map.of("spec.sourceCode.connectorRef", connectorRef), String.join(",", supportedSourceLocationKinds));

      for (CatalogEntity entity : catalogEntities) {
        EntityUpdateRequest entityUpdateRequest = new EntityUpdateRequest();

        EntityResponse getEntityResponse = getEntity(entity.getAccountIdentifier(), entity.getOrgIdentifier(),
            entity.getProjectIdentifier(), CatalogUtils.entityRef(entity), false, true, false);

        SourcePrincipalContextBuilder.setSourcePrincipal(
            new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));

        if (entity instanceof GitReferencedCatalogEntity) {
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
        entityUpdateRequest.setYaml(entity.getYaml());

        updateEntity(entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier(),
            CatalogUtils.entityRef(entity), entityUpdateRequest, false, true, false, false);
        GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
      }
    } catch (Exception ex) {
      log.error("Error in update entity for source location on connector update. Exception = {}", ex.getMessage(), ex);
    }
  }

  @Override
  public void removeSourceCodeReferencesOnConnectorDeletion(
      String harnessAccount, String orgIdentifier, String projectIdentifier, String connectorRef) {
    try {
      List<KindEntity> kindEntities = kindServiceHelper.findByAccountIdentifierIn(harnessAccount);
      List<String> supportedSourceLocationKinds =
          kindEntities.stream()
              .map(KindEntity::getIdentifier)
              .filter(identifier -> !SOURCE_LOCATION_UNSUPPORTED_KINDS.contains(identifier))
              .toList();
      List<CatalogEntity> catalogEntities = catalogEntityRepository.getEntitiesForArbitraryFields(harnessAccount,
          Map.of("spec.sourceCode.connectorRef", connectorRef), String.join(",", supportedSourceLocationKinds));

      for (CatalogEntity entity : catalogEntities) {
        EntityUpdateRequest entityUpdateRequest = new EntityUpdateRequest();

        EntityResponse getEntityResponse = getEntity(entity.getAccountIdentifier(), entity.getOrgIdentifier(),
            entity.getProjectIdentifier(), CatalogUtils.entityRef(entity), false, true, false);

        SourcePrincipalContextBuilder.setSourcePrincipal(
            new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));

        if (entity instanceof GitReferencedCatalogEntity) {
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

        Map<String, Object> spec = entity.getSpec();

        spec.remove("sourceCode");

        entity.setSpec(spec);

        Set<String> groupingKinds = kindServiceHelper.groupingKinds(entity.getAccountIdentifier());
        entity.setYaml(CatalogMapper.presentationYaml(entity, groupingKinds));

        entityUpdateRequest.setYaml(entity.getYaml());

        updateEntity(entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier(),
            CatalogUtils.entityRef(entity), entityUpdateRequest, false, true, true, false);
        GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
      }

    } catch (Exception ex) {
      log.error("Error while cleaning up connector references. Exception = {}", ex.getMessage(), ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  @Override
  public void updateGitMetadata(String harnessAccount, String orgIdentifier, String projectIdentifier, String entityRef,
      GitMetadataUpdateRequest body) {
    try {
      Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
      String kind = catalogServiceHelper.validateAndSanitizeKind(kindScopeIdentifier.getLeft());
      String identifier = catalogServiceHelper.validateAndSanitizeIdentifier(kindScopeIdentifier.getRight());
      String orgIdentifierFromScope, projectIdentifierFromScope;
      String scope = kindScopeIdentifier.getMiddle();
      String[] scopeSplit = scope.split("\\.");
      orgIdentifierFromScope = scopeSplit.length >= 2 ? scopeSplit[1] : null;
      projectIdentifierFromScope = scopeSplit.length == 3 ? scopeSplit[2] : null;
      if ((!isEmpty(orgIdentifier) && !orgIdentifier.equals(orgIdentifierFromScope))
          || (!isEmpty(projectIdentifier) && !projectIdentifier.equals(projectIdentifierFromScope))) {
        throw new InvalidRequestException(
            "Organization / Project from request query params doesn't match with the details provided for scope");
      }
      orgIdentifier = orgIdentifierFromScope;
      projectIdentifier = projectIdentifierFromScope;

      ScopeInfo scopeInfo = catalogScopeResolver.resolveSingleScopeInfo(
          harnessAccount, CatalogUtils.getScope(orgIdentifier, projectIdentifier));
      CatalogEntity catalogEntity = catalogServiceHelper.catalogEntity(scopeInfo.getUniqueId(), kind, identifier);
      catalogServiceHelper.checkRbacWithOwnerFallback(harnessAccount, entityRef, catalogEntity.getOwner(), "edit");
      if (catalogEntity instanceof InlineCatalogEntity) {
        throw new InvalidRequestException("Cannot update git metadata for INLINE entities");
      }
      if (!isEmpty(body.getConnectorRef())) {
        ((GitReferencedCatalogEntity) catalogEntity).setConnectorRef(body.getConnectorRef());
      }
      if (!isEmpty(body.getRepoName())) {
        ((GitReferencedCatalogEntity) catalogEntity).setRepo(body.getRepoName());
      }
      if (!isEmpty(body.getBranchName())) {
        ((GitReferencedCatalogEntity) catalogEntity).setFallBackBranch(body.getBranchName());
      }
      ((GitReferencedCatalogEntity) catalogEntity)
          .setRepoURL(catalogServiceHelper.getRepoUrlAndCheckForFileUniqueness(catalogEntity, body));
      if (!isEmpty(body.getFilePath())) {
        idpGitXHelper.validateFilePath(body.getFilePath());
        ((GitReferencedCatalogEntity) catalogEntity).setFilePath(body.getFilePath());
      }
      idpGitXHelper.validateRepo(catalogEntity);
      String entityYaml = catalogServiceHelper.fetchYAMLFromRemote(catalogEntity);
      if (isEmpty(entityYaml)) {
        String errorMessage = String.format("Empty YAML found on Git in branch [%s].", body.getBranchName());
        throw new InvalidRequestException(errorMessage);
      }
      KindEntity kindEntity = kindServiceHelper.kindEntity(harnessAccount, catalogEntity.getKind());
      catalogServiceHelper.validateAgainstJsonSchema(catalogEntity.getKind(), entityYaml, kindEntity.getSchema());
      Map<String, Object> entityYamlMap = YamlUtils.loadYamlStringAsMap(entityYaml);
      String identifierFromYAML = from(entityYamlMap, "identifier", String.class);
      identifierFromYAML = catalogServiceHelper.validateAndSanitizeIdentifier(identifierFromYAML);
      String kindFromYAML = from(entityYamlMap, "kind", String.class);
      kindFromYAML = catalogServiceHelper.validateAndSanitizeKind(kindFromYAML);
      if (!Objects.equals(identifier, identifierFromYAML) || !Objects.equals(kind, kindFromYAML)) {
        throw new InvalidRequestException("Mismatch in identifier / kind between query param and YAML input");
      }
      String orgIdentifierFromYaml = from(entityYamlMap, "orgIdentifier", String.class);
      String projectIdentifierFromYaml = from(entityYamlMap, "projectIdentifier", String.class);
      if (!Objects.equals(orgIdentifier, orgIdentifierFromYaml)
          || !Objects.equals(projectIdentifier, projectIdentifierFromYaml)) {
        throw new InvalidRequestException(
            "Mismatch in orgIdentifier / projectIdentifier between query param and YAML input");
      }
      catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity));
      catalogEntityRepository.save(catalogEntity);
    } catch (Exception e) {
      log.error("Error in updating git-metadata. Exception = {}", e.getMessage(), e);
      throw new InvalidRequestException(e.getMessage());
    }
  }

  private void updateEntityForMigrationDefaultToAccount(CatalogEntity catalogEntity, String harnessAccount) {
    try {
      transactionHelper.performTransaction(() -> {
        Map<String, Object> spec =
            !isEmpty(catalogEntity.getSpec()) ? new HashMap<>(catalogEntity.getSpec()) : new HashMap<>();
        if (!isEmpty(catalogEntity.getRelations())) {
          catalogEntity.setRelations(transformRelationsForAddingAccountNamespace(catalogEntity.getRelations()));

          // Here we are populating spec again from relations because in DB in spec most of them where not having any
          // kind associated with identifier.
          harnessToIDPHelper.populateSpec(catalogEntity.getRelations(), spec,
              harnessToIDPHelper.convertToBackstageKind(catalogEntity.getKind()), catalogEntity.getIdentifier(),
              catalogEntity.getName(), false, false);
        }

        catalogEntity.setSpec(spec);
        catalogEntity.setYaml(CatalogMapper.presentationYaml(catalogEntity));
        catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity));
        catalogEntityRepository.save(catalogEntity);

        Object entityWithDefaultNamespace = harnessToIDPHelper.buildBackstageCatalog(catalogEntity, false, true, false);
        Object entityWithAccountNamespace =
            harnessToIDPHelper.buildBackstageCatalog(catalogEntity, false, false, false);

        Map<String, List<Object>> actionOnEntities = new HashMap<>();
        actionOnEntities.put(DELETE_ACTION, List.of(entityWithDefaultNamespace));
        actionOnEntities.put(CREATE_ACTION, List.of(entityWithAccountNamespace));
        log.info("Map for syncing  - {}", actionOnEntities);

        harnessToIDPHelper.harnessToIdpSyncForMigration(harnessAccount, actionOnEntities);
        return null;
      });
    } catch (Exception ex) {
      log.error(
          "Migration - Error in update entity migration for default to account. Exception = {}", ex.getMessage(), ex);
    }
  }

  private List<ScoreEntity> fetchScoresForEntity(
      CatalogEntity catalogEntity, List<ScorecardAndChecks> scorecardAndChecks, String accountIdentifier) {
    return scoreService.fetchScoresForCatalogEntity(accountIdentifier, catalogEntity, scorecardAndChecks);
  }

  private Map<String, List<ScoreEntity>> fetchScoresForEntities(List<CatalogEntity> catalogEntities,
      List<ScorecardAndChecks> scorecardAndChecks, String accountIdentifier,
      Map<String, List<ScopeInfo>> scopeInfosForScopes) {
    return scoreService.fetchScoresForCatalogEntities(
        accountIdentifier, catalogEntities, scorecardAndChecks, scopeInfosForScopes);
  }

  public static Map<String, Set<String>> transformRelationsForAddingAccountNamespace(
      Map<String, Set<String>> relations) {
    Map<String, Set<String>> updatedRelations = new HashMap<>();
    String account = "account";

    for (Map.Entry<String, Set<String>> entry : relations.entrySet()) {
      Set<String> updatedSet = new HashSet<>();

      for (String value : entry.getValue()) {
        if (value.contains(":")) {
          // Value already has a kind:identifier structure
          String[] parts = value.split(":", 2);
          if (parts.length == 2) {
            String kind = parts[0];
            String identifier = parts[1];

            if (identifier.contains("/") && !identifier.startsWith("default/")) {
              // Already custom namespace
              updatedSet.add(kind + ":" + identifier);
            } else {
              // Update namespace to account
              if (identifier.startsWith("default/")) {
                identifier = identifier.substring("default/".length());
              }
              updatedSet.add(kind + ":" + account + "/" + identifier);
            }
          }
        } else {
          // No kind given, figure out if it's user or group
          if (value.contains("@")) {
            String username = value.split("@")[0].replaceAll("\\+", "plus");
            updatedSet.add("user:" + account + "/" + username);
          } else {
            updatedSet.add("group:" + account + "/" + value);
          }
        }
      }

      updatedRelations.put(entry.getKey(), updatedSet);
    }

    return updatedRelations;
  }

  @Override
  public void deleteEntity(String harnessAccount, String orgIdentifier, String projectIdentifier, String entityRef,
      Boolean deleteHierarchyKindEntity) {
    deleteEntity(harnessAccount, orgIdentifier, projectIdentifier, entityRef, null, false, deleteHierarchyKindEntity);
  }

  @Override
  public void deleteEntity(String harnessAccount, String orgIdentifier, String projectIdentifier, String entityRef,
      String version, boolean versionedEntity, Boolean deleteHierarchyKindEntity) {
    try {
      String[] entityRefSplit = entityRef.split(":");
      if (entityRefSplit.length != 2) {
        throw new InvalidRequestException("Invalid entityRef = " + entityRef + " provided for delete entity");
      }
      String entityKind = entityRefSplit[0].toLowerCase();
      String entityScopeIdentifier = entityRefSplit[1];

      String scope = "account";
      String entityIdentifier;
      int slashIndex = entityScopeIdentifier.indexOf("/");
      scope = slashIndex != -1 ? entityScopeIdentifier.substring(0, slashIndex) : scope;
      entityIdentifier = slashIndex != -1 ? entityScopeIdentifier.substring(slashIndex + 1) : entityScopeIdentifier;

      entityKind = catalogServiceHelper.validateAndSanitizeKind(entityKind);
      if (entityKind.equals(HIERARCHY_KIND)) {
        idpCommonService.allowCreateUpdateDeleteOnHierarchyKindEntity(harnessAccount, false, deleteHierarchyKindEntity);
      }
      if (versionedEntity) {
        catalogServiceHelper.validateKindForVersioning(entityKind);
        catalogServiceHelper.validateVersionLabel(version);
      }
      catalogServiceHelper.validateKindForCreateUpdateDelete(entityKind);
      entityIdentifier = catalogServiceHelper.validateAndSanitizeIdentifier(entityIdentifier);

      String orgIdentifierFromScope, projectIdentifierFromScope;
      String[] scopeSplit = scope.split("\\.");
      orgIdentifierFromScope = scopeSplit.length >= 2 ? scopeSplit[1] : null;
      projectIdentifierFromScope = scopeSplit.length == 3 ? scopeSplit[2] : null;
      if ((!isEmpty(orgIdentifier) && !orgIdentifier.equals(orgIdentifierFromScope))
          || (!isEmpty(projectIdentifier) && !projectIdentifier.equals(projectIdentifierFromScope))) {
        throw new InvalidRequestException(
            "Organization / Project from request query params doesn't match with the details provided for scope");
      }

      ScopeInfo scopeInfo = catalogScopeResolver.resolveSingleScopeInfo(
          harnessAccount, CatalogUtils.getScope(orgIdentifier, projectIdentifier));
      CatalogEntity catalogEntity =
          catalogServiceHelper.catalogEntity(scopeInfo.getUniqueId(), entityKind, entityIdentifier);
      catalogServiceHelper.checkRbacWithOwnerFallback(harnessAccount, entityRef, catalogEntity.getOwner(), "delete");

      if (versionedEntity) {
        AcquiredLock lock = null;

        try {
          if (ENVIRONMENT_BLUEPRINT_KIND.equals(entityKind)) {
            String envBlueprintLockName =
                String.format("environmentblueprint_delete_%s_%s", harnessAccount, entityIdentifier);
            lock = resourceLocker.acquireLock(String.format(LOCK_NAME_FORMAT, envBlueprintLockName));

            if (lock == null) {
              throw new InvalidRequestException("Multiple request triggered for Version Delete API");
            }
            validateEnvironmentBlueprintNotUsedByOthers(
                scopeInfo, catalogServiceHelper.getBlueprintVersionIdentifier(entityIdentifier, version));
            validateNotLastBlueprintVersion(catalogEntity, version);
          }
          catalogVersionService.deleteEntityVersion(
              harnessAccount, orgIdentifier, projectIdentifier, catalogEntity, version);
        } finally {
          if (lock != null) {
            resourceLocker.releaseLock(lock);
          }
        }
        return;
      }

      catalogServiceHelper.validateUpdateDeleteForCustomUserGroup(catalogEntity);
      List<CatalogEntity> referencedEntities = relationsProcessor.disbandRelations(catalogEntity);
      Map<String, Object> spec = catalogEntity.getSpec();
      Map<String, Object> harnessService =
          !isEmpty(spec) ? (Map<String, Object>) spec.get("harnessService") : new HashMap<>();
      transactionHelper.performTransaction(() -> {
        if (catalogEntity.getKind().equals(WORKFLOW_KIND)) {
          outboxService.save(new WorkflowDeleteEvent(
              scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
          entityLinkRepository.deleteByAccountIdentifierAndEntityRef(harnessAccount,
              CatalogUtils.entityRef(catalogEntity.getKind(), catalogEntity.getOrgIdentifier(),
                  catalogEntity.getProjectIdentifier(), catalogEntity.getIdentifier()));
        } else if (catalogEntity.getKind().equals(ENVIRONMENT_KIND)) {
          outboxService.save(new EnvironmentDeleteEvent(
              scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
        } else if (catalogEntity.getKind().equals(ENVIRONMENT_BLUEPRINT_KIND)) {
          outboxService.save(new EnvironmentBlueprintDeleteEvent(
              scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
        } else if (catalogEntity.getKind().equals(GROUP_KIND)) {
          outboxService.save(new TeamDeleteEvent(
              scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
        } else {
          outboxService.save(new CatalogDeleteEvent(
              scopeInfo, catalogEntity.getYaml(), catalogEntity.getKind(), catalogEntity.getIdentifier()));
        }
        createOutboxUpdateEventForReferencedEntities(referencedEntities);
        catalogEntityRepository.delete(catalogEntity);
        referencedEntities.forEach(
            entity -> entity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(entity)));
        catalogEntityRepository.saveAll(referencedEntities);
        harnessToIDPHelper.harnessToIdpSync(List.of(catalogEntity), harnessAccount, DELETE_ACTION);
        catalogServiceHelper.deleteFavorite(harnessAccount, orgIdentifier, projectIdentifier, entityRef);
        if (!isEmpty(harnessService)) {
          setupUsageProducer.deleteCdServiceSetupUsage(
              harnessAccount, orgIdentifier, projectIdentifier, catalogEntity.getIdentifier());
        }
        if (catalogEntity.getKind().equals(ENVIRONMENT_KIND)) {
          setupUsageProducer.deleteEnvironmentSetupUsage(
              harnessAccount, orgIdentifier, projectIdentifier, catalogEntity.getIdentifier());
        }

        if (catalogEntity.getKind().equals(ENVIRONMENT_KIND)) {
          setupUsageProducer.deleteEnvironmentSetupUsage(harnessAccount, catalogEntity.getOrgIdentifier(),
              catalogEntity.getProjectIdentifier(), catalogEntity.getIdentifier());
        }

        if (catalogEntity.getKind().equals(ENVIRONMENT_BLUEPRINT_KIND)) {
          // This is for handling the case that blueprint should not get deleted if any one of the version is getting
          // used in environment. Environments store scoped identifiers (e.g. "account.bp1") so we need to build
          // the scoped identifier from the request scope params to match.
          String scopedBlueprintIdentifier = CommonUtils.getScopedIdentifier(
              harnessAccount, orgIdentifier, projectIdentifier, catalogEntity.getIdentifier());
          Map<String, Object> arbitraryFields = new HashMap<>();
          arbitraryFields.put("spec.environmentBlueprint.identifier", scopedBlueprintIdentifier);
          List<CatalogEntity> catalogEntities =
              catalogEntityRepository.getEntitiesForArbitraryFields(harnessAccount, arbitraryFields, ENVIRONMENT_KIND);
          if (!catalogEntities.isEmpty()) {
            throw new InvalidRequestException("Blueprint can not be deleted as versions are referred by environments");
          }
          // Delete all versions of the entity - currently only blueprints are versioned, and the usage of these are
          // guarded above.
          catalogEntityVersionRepository.deleteAllByEntityId(catalogEntity.getId());
        }

        return null;
      });
      unsubscribeLinkedIntegrationEntities(harnessAccount, catalogEntity);
      idpToHarnessHelper.sendCatalogEventsToRedis(Collections.singletonList(catalogEntity), DELETE_ACTION);
      idpToHarnessHelper.sendCatalogEventsToRedis(referencedEntities, UPDATE_ACTION);
    } catch (Exception ex) {
      throwIfMongoWriteConflictError(ex);
      log.error("Error in delete entity. Exception = {}", ex.getMessage(), ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  public void validateEnvironmentBlueprintNotUsedByOthers(ScopeInfo scopeInfo, String blueprintIdentifier) {
    // Blueprint version identifiers use format "blueprintId&version" (using & delimiter)
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(scopeInfo.getAccountIdentifier())
                                      .orgIdentifier(scopeInfo.getOrgIdentifier())
                                      .projectIdentifier(scopeInfo.getProjectIdentifier())
                                      .identifier(blueprintIdentifier)
                                      .build();
    Boolean isEntityReferenced;

    try {
      isEntityReferenced =
          NGRestUtils.getResponse(entitySetupUsageClient.isEntityReferenced(scopeInfo.getAccountIdentifier(),
              identifierRef.getFullyQualifiedName(), EntityType.IDP_ENVIRONMENT_BLUEPRINT));
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          blueprintIdentifier, ex);
      throw new UnexpectedException("Error while deleting the Blueprint");
    }
    if (isEntityReferenced) {
      throw new ReferencedEntityException(String.format(
          "Could not delete the blueprint %s as it is referenced by other entities", blueprintIdentifier));
    }
  }

  private void validateNotLastBlueprintVersion(CatalogEntity blueprintEntity, String version) {
    boolean existsAnother =
        catalogEntityVersionRepository.existsByEntityIdAndVersionNot(blueprintEntity.getId(), version);

    if (!existsAnother) {
      throw new InvalidRequestException(String.format("Cannot delete version %s of blueprint %s. "
              + "At least one version must exist for the blueprint. ",
          version, blueprintEntity.getIdentifier()));
    }
  }

  @Override
  public GetEntitiesDTO getEntities(String harnessAccount, Integer page, Integer limit, String sort, String searchTerm,
      boolean resolvePlaceholders, String scopes, String entityRefs, Boolean ownedByMe, Boolean favorites, String kind,
      String type, String owner, String lifecycle, String tags, String filter, boolean includeScorecardsData) {
    return getEntities(harnessAccount, page, limit, sort, searchTerm, resolvePlaceholders, scopes, entityRefs,
        ownedByMe, favorites, kind, type, owner, lifecycle, tags, filter, includeScorecardsData, false);
  }

  @Override
  public GetEntitiesDTO getEntitiesV2(String harnessAccount, Integer page, Integer limit, String sort,
      String searchTerm, boolean resolvePlaceholders, String scopes, String entityRefs, Boolean ownedByMe,
      Boolean favorites, String kind, String type, String owner, String lifecycle, String tags, String filter,
      boolean includeScorecardsData, boolean entityRefAndCriteria, Boolean skipFavorites) {
    return catalogServiceV2Impl.getEntitiesV2(harnessAccount, page, limit, sort, searchTerm, resolvePlaceholders,
        scopes, entityRefs, ownedByMe, favorites, kind, type, owner, lifecycle, tags, filter, includeScorecardsData,
        entityRefAndCriteria, skipFavorites);
  }

  @Override
  public GetEntitiesDTO getEntities(String harnessAccount, Integer page, Integer limit, String sort, String searchTerm,
      boolean resolvePlaceholders, String scopes, String entityRefs, Boolean ownedByMe, Boolean favorites, String kind,
      String type, String owner, String lifecycle, String tags, String filter, boolean includeScorecardsData,
      boolean entityRefAndCriteria) {
    try {
      String requestedEntityRefs = entityRefs;

      if (isEmpty(scopes) && isEmpty(entityRefs)) {
        scopes = catalogServiceHelper.getAllScopes();
      }

      if (!isEmpty(kind)) {
        List<String> kinds = List.of(kind.split(","));
        if (kinds.size() > 1) {
          // Check if groups, workflow, environment, or environmentblueprint is present in the list
          List<String> unsupportedInMultiSelection =
              List.of(WORKFLOW_KIND, ENVIRONMENT_KIND, ENVIRONMENT_BLUEPRINT_KIND, GROUP_KIND);
          for (String k : kinds) {
            if (unsupportedInMultiSelection.contains(k.toLowerCase())) {
              throw new InvalidRequestException(
                  "Group, Workflow, Environment, and EnvironmentBlueprint kinds cannot be "
                  + "included when specifying multiple kinds");
            }
          }
        }
      }
      Map<String, KindEntity> kindEntityMap =
          kindServiceHelper.findByAccountIdentifierIn(harnessAccount)
              .stream()
              .collect(Collectors.toMap(KindEntity::getIdentifier, entity -> entity));

      Pair<List<ScopeInfo>, Map<String, List<ScopeInfo>>> scopeInfosAndScopeInfosForScopes =
          catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(harnessAccount, scopes, entityRefs);
      List<ScopeInfo> scopeInfos = scopeInfosAndScopeInfosForScopes.getLeft();

      ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                       .accountIdentifier(harnessAccount)
                                       .scopeType(ScopeLevel.ACCOUNT)
                                       .uniqueId(harnessAccount)
                                       .build();

      Set<String> permittedEntityRefs = new HashSet<>(catalogServiceHelper.checkEntitiesRbac(
          harnessAccount, scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList()));

      if (isEmpty(entityRefs)) {
        List<String> kinds = !isEmpty(kind) ? List.of(kind.split(",")) : CORE_KINDS;
        if (kinds.size() == 1) {
          String kindValue = kinds.get(0).toLowerCase();
          if (kindValue.equals(WORKFLOW_KIND)) {
            permittedEntityRefs = permittedEntityRefs.stream()
                                      .filter(permittedEntityRef -> permittedEntityRef.startsWith("workflow:"))
                                      .collect(Collectors.toSet());
          } else if (kindValue.equals(ENVIRONMENT_KIND)) {
            permittedEntityRefs = permittedEntityRefs.stream()
                                      .filter(permittedEntityRef -> permittedEntityRef.startsWith("environment:"))
                                      .collect(Collectors.toSet());
          } else if (kindValue.equals(ENVIRONMENT_BLUEPRINT_KIND)) {
            permittedEntityRefs =
                permittedEntityRefs.stream()
                    .filter(permittedEntityRef -> permittedEntityRef.startsWith("environmentblueprint:"))
                    .collect(Collectors.toSet());
          } else if (kindValue.equals(GROUP_KIND)) {
            permittedEntityRefs = permittedEntityRefs.stream()
                                      .filter(permittedEntityRef -> permittedEntityRef.startsWith("group:"))
                                      .collect(Collectors.toSet());
          } else {
            permittedEntityRefs =
                permittedEntityRefs.stream()
                    .filter(permittedEntityRef
                        -> !permittedEntityRef.startsWith("workflow:") && !permittedEntityRef.startsWith("environment:")
                            && !permittedEntityRef.startsWith("environmentblueprint:")
                            && !permittedEntityRef.startsWith("group:"))
                    .collect(Collectors.toSet());
          }
        } else {
          permittedEntityRefs =
              permittedEntityRefs.stream()
                  .filter(permittedEntityRef
                      -> !permittedEntityRef.startsWith("workflow:") && !permittedEntityRef.startsWith("environment:")
                          && !permittedEntityRef.startsWith("environmentblueprint:")
                          && !permittedEntityRef.startsWith("group:"))
                  .collect(Collectors.toSet());
        }
      }

      if (!isEmpty(kind)) {
        List<String> kindsList = List.of(kind.split(","));
        if (kindsList.stream().anyMatch(catalogServiceHelper::isInheritableKind)) {
          Set<String> uniqueScopesForGroups = catalogServiceHelper.uniqueParentScopesForGroups(scopeInfos);
          List<ScopeInfo> scopeInfosForGroups = catalogServiceHelper
                                                    .getScopeInfosBasedOnScopesAndEntityRefs(
                                                        harnessAccount, String.join(",", uniqueScopesForGroups), null)
                                                    .getLeft();
          List<String> permittedGroupEntityRefs = catalogServiceHelper.checkEntitiesRbacByKind(
              harnessAccount, scopeInfosForGroups.stream().map(ScopeInfo::getUniqueId).distinct().toList(), GROUP_KIND);
          List<String> entityRefsOwnedByGroups =
              catalogEntityRepository
                  .findAllByParentUniqueIdInAndKindInAndOwnerIn(
                      scopeInfos.stream().map(ScopeInfo::getUniqueId).toList(), kindsList, permittedGroupEntityRefs)
                  .stream()
                  .map(CatalogUtils::entityRef)
                  .toList();
          permittedEntityRefs.addAll(entityRefsOwnedByGroups);
        }
      }

      List<ScopeInfo> permittedEntityRefsScopeInfo = new ArrayList<>();
      Set<String> orgs = new HashSet<>();
      Map<String, Set<String>> projectsByOrg = new HashMap<>();
      permittedEntityRefs.forEach(permittedEntityRef -> {
        Triple<String, String, String> kindScopeIdentifier =
            catalogServiceHelper.getKindScopeIdentifier(permittedEntityRef);
        String scope = kindScopeIdentifier.getMiddle();
        String[] scopeSplit = scope.split("\\.");
        if (scopeSplit.length == 1) {
          if (!permittedEntityRefsScopeInfo.contains(accountScopeInfo)) {
            permittedEntityRefsScopeInfo.add(accountScopeInfo);
          }
        }
        if (scopeSplit.length == 2) {
          orgs.add(scopeSplit[1]);
        }
        if (scopeSplit.length == 3) {
          projectsByOrg.computeIfAbsent(scopeSplit[1], k -> new HashSet<>()).add(scopeSplit[2]);
        }
      });

      if (!isEmpty(orgs)) {
        List<OrganizationResponse> organizationResponses =
            NGRestUtils
                .getResponse(organizationClient.listAllOrganizations(harnessAccount, orgs.stream().toList(), null))
                .getContent();
        Set<String> orgIdentifiers = organizationResponses.stream()
                                         .map(response -> response.getOrganization().getIdentifier())
                                         .collect(Collectors.toSet());
        List<ScopeInfo> scopeInfoList =
            NGRestUtils.getResponse(scopeInfoClient.getScopeInfoList(harnessAccount, orgIdentifiers));
        if (!isEmpty(scopeInfoList)) {
          permittedEntityRefsScopeInfo.addAll(scopeInfoList.stream().filter(Objects::nonNull).toList());
        }
      }

      Set<String> orgIdentifiers =
          projectsByOrg.keySet().stream().filter(key -> !isEmpty(key)).collect(Collectors.toSet());
      if (!isEmpty(orgIdentifiers)) {
        List<OrganizationResponse> organizationResponses =
            NGRestUtils
                .getResponse(
                    organizationClient.listAllOrganizations(harnessAccount, orgIdentifiers.stream().toList(), null))
                .getContent();
        orgIdentifiers = organizationResponses.stream()
                             .map(response -> response.getOrganization().getIdentifier())
                             .collect(Collectors.toSet());
        final Set<String> validOrgIdentifiers = orgIdentifiers;
        projectsByOrg.entrySet().removeIf(entry -> !validOrgIdentifiers.contains(entry.getKey()));
      }
      List<String> projectIdentifiers = projectsByOrg.values()
                                            .stream()
                                            .filter(Objects::nonNull)
                                            .flatMap(Set::stream)
                                            .filter(v -> !isEmpty(v))
                                            .toList();

      List<ProjectDTO> projectDTOS = new ArrayList<>();
      if (!isEmpty(orgIdentifiers) && !isEmpty(projectIdentifiers)) {
        int pageNumber = 0;
        final int pageSize = 100;
        while (true) {
          PageResponse<ProjectResponse> projectResponsePageResponse =
              NGRestUtils.getResponse(projectClient.listWithMultiOrg(harnessAccount, orgIdentifiers, false,
                  projectIdentifiers, null, null, pageNumber, pageSize, null, false));
          if (projectResponsePageResponse == null || isEmpty(projectResponsePageResponse.getContent())) {
            break;
          }
          projectDTOS.addAll(
              projectResponsePageResponse.getContent().stream().map(ProjectResponse::getProject).toList());
          if (projectResponsePageResponse.getContent().size() < pageSize) {
            break;
          }
          pageNumber++;
        }
      }

      Map<String, Set<String>> projectsByOrganization = projectDTOS.stream().collect(Collectors.groupingBy(
          ProjectDTO::getOrgIdentifier, Collectors.mapping(ProjectDTO::getIdentifier, Collectors.toSet())));

      projectsByOrganization.forEach((k, v) -> {
        List<ScopeInfo> projectsScopeInfoList =
            NGRestUtils.getResponse(scopeInfoClient.getScopeInfoList(harnessAccount, k, v));
        if (!isEmpty(projectsScopeInfoList)) {
          permittedEntityRefsScopeInfo.addAll(projectsScopeInfoList.stream().filter(Objects::nonNull).toList());
        }
      });

      List<ScopeInfo> scopeInfosWithRbac = catalogServiceHelper.scopeInfosRbac(harnessAccount, scopeInfos, kind);
      scopeInfos = !isEmpty(scopeInfosWithRbac) ? new ArrayList<>(scopeInfosWithRbac) : new ArrayList<>();
      scopeInfos.addAll(permittedEntityRefsScopeInfo);

      List<OrganizationDTO> organizations = getOrganizations(
          harnessAccount, scopeInfos.stream().map(ScopeInfo::getOrgIdentifier).collect(Collectors.toSet()));
      Map<String, Set<String>> orgProjects = new HashMap<>();
      scopeInfos.forEach(scopeInfo -> {
        if (scopeInfo.getScopeType().equals(ScopeLevel.PROJECT)) {
          Set<String> projects = orgProjects.getOrDefault(scopeInfo.getOrgIdentifier(), new HashSet<>());
          projects.add(scopeInfo.getProjectIdentifier());
          orgProjects.put(scopeInfo.getOrgIdentifier(), projects);
        }
      });

      orgIdentifiers = orgProjects.keySet().stream().filter(key -> !isEmpty(key)).collect(Collectors.toSet());
      if (!isEmpty(orgIdentifiers)) {
        List<OrganizationResponse> organizationResponses =
            NGRestUtils
                .getResponse(
                    organizationClient.listAllOrganizations(harnessAccount, orgIdentifiers.stream().toList(), null))
                .getContent();
        orgIdentifiers = organizationResponses.stream()
                             .map(response -> response.getOrganization().getIdentifier())
                             .collect(Collectors.toSet());
        final Set<String> validOrgIdentifiers = orgIdentifiers;
        orgProjects.entrySet().removeIf(entry -> !validOrgIdentifiers.contains(entry.getKey()));
      }
      projectIdentifiers =
          orgProjects.values().stream().filter(Objects::nonNull).flatMap(Set::stream).filter(v -> !isEmpty(v)).toList();

      List<ProjectDTO> projects = new ArrayList<>();
      if (!isEmpty(orgIdentifiers) && !isEmpty(projectIdentifiers)) {
        int pageNumber = 0;
        final int pageSize = 100;
        while (true) {
          PageResponse<ProjectResponse> projectResponsePageResponse =
              NGRestUtils.getResponse(projectClient.listWithMultiOrg(harnessAccount, orgIdentifiers, false,
                  projectIdentifiers, null, null, pageNumber, pageSize, null, false));
          if (projectResponsePageResponse == null || isEmpty(projectResponsePageResponse.getContent())) {
            break;
          }
          projects.addAll(projectResponsePageResponse.getContent().stream().map(ProjectResponse::getProject).toList());
          if (projectResponsePageResponse.getContent().size() < pageSize) {
            break;
          }
          pageNumber++;
        }
      }
      List<String> entityRefList = isEmpty(entityRefs) ? Collections.emptyList() : Arrays.asList(entityRefs.split(","));
      List<String> providedVsPermittedEntityRefs = new ArrayList<>(permittedEntityRefs);
      providedVsPermittedEntityRefs.retainAll(entityRefList);
      entityRefs = String.join(",", providedVsPermittedEntityRefs);

      boolean ownedByMeFilter = ownedByMe != null && ownedByMe;
      if (ownedByMeFilter) {
        owner = catalogServiceHelper.getOwnedByMe(accountScopeInfo.getUniqueId(), owner);
      }

      String userFavoriteEntityRefs = null;
      Set<String> kinds = !isEmpty(kind) ? new HashSet<>(Arrays.asList(kind.split(","))) : new HashSet<>();
      if (scopeInfos.stream().anyMatch(scopeInfo -> scopeInfo.getScopeType().equals(ScopeLevel.ACCOUNT))) {
        String accountLevelUserFavoriteEntityRefs =
            catalogServiceHelper.getUserFavoriteEntityRefs(harnessAccount, null, null, IDPENTITY.name());
        if (!isEmpty(accountLevelUserFavoriteEntityRefs)) {
          userFavoriteEntityRefs = accountLevelUserFavoriteEntityRefs;
        }
      }
      if (scopeInfos.stream().anyMatch(scopeInfo -> scopeInfo.getScopeType().equals(ScopeLevel.ORGANIZATION))) {
        String orgLevelUserFavoriteEntityRefs = catalogServiceHelper.getUserFavoriteEntityRefsForOrgs(
            harnessAccount, organizations.stream().map(OrganizationDTO::getIdentifier).toList(), IDPENTITY.name());
        if (!isEmpty(orgLevelUserFavoriteEntityRefs)) {
          if (isEmpty(userFavoriteEntityRefs)) {
            userFavoriteEntityRefs = orgLevelUserFavoriteEntityRefs;
          } else {
            userFavoriteEntityRefs = userFavoriteEntityRefs + "," + orgLevelUserFavoriteEntityRefs;
          }
        }
      }
      if (scopeInfos.stream().anyMatch(scopeInfo -> scopeInfo.getScopeType().equals(ScopeLevel.PROJECT))) {
        String projectLevelUserFavoriteEntityRefs = catalogServiceHelper.getUserFavoriteEntityRefsForProjects(
            harnessAccount,
            projects.stream().map(p -> p.getOrgIdentifier() + "." + p.getIdentifier()).collect(Collectors.joining(",")),
            IDPENTITY.name());
        if (!isEmpty(projectLevelUserFavoriteEntityRefs)) {
          if (isEmpty(userFavoriteEntityRefs)) {
            userFavoriteEntityRefs = projectLevelUserFavoriteEntityRefs;
          } else {
            userFavoriteEntityRefs = userFavoriteEntityRefs + "," + projectLevelUserFavoriteEntityRefs;
          }
        }
      }

      if (!isEmpty(kinds) && !isEmpty(userFavoriteEntityRefs)) {
        List<String> filteredByKind = new ArrayList<>();
        String[] userFavoriteEntityRefsList = userFavoriteEntityRefs.split(",");
        for (String userFavoriteEntityRef : userFavoriteEntityRefsList) {
          String[] entityRefSplit = userFavoriteEntityRef.split(":", 2);
          if (entityRefSplit.length == 2 && kinds.contains(entityRefSplit[0])) {
            filteredByKind.add(userFavoriteEntityRef);
          }
        }
        userFavoriteEntityRefs = String.join(",", filteredByKind);
      }

      boolean favoritesFilter = favorites != null && favorites;
      if (favoritesFilter) {
        if (isEmpty(userFavoriteEntityRefs)) {
          return GetEntitiesDTO.builder().entityResponses(Collections.emptyList()).build();
        }
        entityRefs = userFavoriteEntityRefs;
      }
      if (!isEmpty(filter)) {
        if (mongoReplacementConfig != null && !mongoReplacementConfig.isEmpty()) {
          for (Map.Entry<String, String> replacement : mongoReplacementConfig.entrySet()) {
            if (filter.contains(replacement.getKey())) {
              filter = filter.replace(replacement.getKey(), replacement.getValue());
            }
          }
        }
      }
      String ownedByMeCriteriaForTotalCount = catalogServiceHelper.getOwnedByMe(accountScopeInfo.getUniqueId(), null);
      long totalOwned = catalogEntityRepository.getOwnedEntitiesCount(
          scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList(), kind, ownedByMeCriteriaForTotalCount,
          harnessAccount, scopeInfos, requestedEntityRefs, entityRefAndCriteria, filter);
      long totalFavorites = catalogEntityRepository.getFavoritesEntitiesCount(
          harnessAccount, scopeInfos, userFavoriteEntityRefs, requestedEntityRefs, entityRefAndCriteria, filter);

      Page<CatalogEntity> catalogEntitiesPaged = catalogEntityRepository.getEntities(harnessAccount, scopeInfos, page,
          limit, sort, searchTerm, requestedEntityRefs, entityRefs, String.join(",", permittedEntityRefs), kind, type,
          owner, lifecycle, tags, null, filter, entityRefAndCriteria);

      List<CatalogEntity> catalogEntitiesPagedContent = catalogEntitiesPaged.getContent();
      List<EntityResponse> entityResponses = new ArrayList<>();
      catalogEntitiesPagedContent =
          catalogServiceHelper.resolveOwner(accountScopeInfo.getUniqueId(), catalogEntitiesPagedContent);
      List<String> entitiesKinds = catalogEntitiesPagedContent.stream().map(CatalogEntity::getKind).toList();
      boolean hasCoreKind = entitiesKinds.stream().anyMatch(CORE_KINDS::contains);
      Map<String, List<ScoreEntity>> entityScores = new HashMap<>();
      Map<String, String> scorecardIdToNameMap = new HashMap<>();
      if (!isEmpty(catalogEntitiesPagedContent) && (hasCoreKind || entitiesKinds.stream().anyMatch(GROUP_KIND::equals))
          && includeScorecardsData) {
        List<ScorecardAndChecks> scorecardAndChecks = scorecardService.getAllScorecardAndChecks(harnessAccount, null);
        entityScores = fetchScoresForEntities(catalogEntitiesPagedContent, scorecardAndChecks, harnessAccount,
            scopeInfosAndScopeInfosForScopes.getRight());

        scorecardIdToNameMap.putAll(scorecardAndChecks.stream().collect(Collectors.toMap(scorecard
            -> scorecard.getScorecard().getIdentifier(),
            scorecard -> scorecard.getScorecard().getName(), (a, b) -> a)));
      }
      for (CatalogEntity catalogEntity : catalogEntitiesPagedContent) {
        List<ScoreEntity> scoreEntities = entityScores.get(CatalogUtils.entityRef(catalogEntity));
        String orgName = !isEmpty(catalogEntity.getOrgIdentifier())
            ? organizations.stream()
                  .filter(org -> org.getIdentifier().equals(catalogEntity.getOrgIdentifier()))
                  .map(OrganizationDTO::getName)
                  .findFirst()
                  .orElse(null)
            : null;
        String projName = !isEmpty(catalogEntity.getOrgIdentifier()) && !isEmpty(catalogEntity.getProjectIdentifier())
            ? projects.stream()
                  .filter(project
                      -> project.getOrgIdentifier().equals(catalogEntity.getOrgIdentifier())
                          && project.getIdentifier().equals(catalogEntity.getProjectIdentifier()))
                  .map(ProjectDTO::getName)
                  .findFirst()
                  .orElse(null)
            : null;
        if (!isEmpty(catalogEntity.getOrgIdentifier()) && isEmpty(orgName)) {
          continue;
        }
        if (!isEmpty(catalogEntity.getOrgIdentifier()) && !isEmpty(catalogEntity.getProjectIdentifier())
            && (isEmpty(orgName) || isEmpty(projName))) {
          continue;
        }
        EntityResponse entityResponse = CatalogMapper.entityToResponse(catalogEntity, orgName, projName,
            userFavoriteEntityRefs,
            kindEntityMap.containsKey(catalogEntity.getKind()) ? kindEntityMap.get(catalogEntity.getKind()).getIcon()
                                                               : null,
            constructEntityScorecards(scoreEntities, scorecardIdToNameMap), resolvePlaceholders);
        entityResponse.setGitDetails(idpGitXHelper.getEntityDetails(catalogEntity));
        entityResponses.add(entityResponse);
      }

      return GetEntitiesDTO.builder()
          .pageNumber(catalogEntitiesPaged.getNumber())
          .totalElements(catalogEntitiesPaged.getTotalElements())
          .entityResponses(entityResponses)
          .totalOwned(totalOwned)
          .totalStarred(totalFavorites)
          .build();
    } catch (Exception ex) {
      log.error("Error in get entities. Exception = {}", ex.getMessage(), ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  @Override
  public void migrateEntities(String harnessAccount, EntitiesMigrateRequest body) {
    AcquiredLock lock = null;
    try {
      lock = resourceLocker.acquireLock(String.format(LOCK_NAME_FORMAT, harnessAccount));
      if (lock == null) {
        throw new InvalidRequestException("Multiple request triggered for Entities migrate API");
      }
      catalogServiceHelper.validateMigrateRequest(body, harnessAccount);
      Optional<NamespaceEntity> optionalNamespaceEntity =
          namespaceService.getEntityForAccountIdentifier(harnessAccount);
      if (optionalNamespaceEntity.isPresent()) {
        NamespaceEntity namespaceEntity = optionalNamespaceEntity.get();
        NamespaceEntity.Metadata metadata = namespaceEntity.getMetadata();
        if (metadata != null && metadata.getIdpV2MigrationInfo() != null
            && metadata.getIdpV2MigrationInfo().isMigrateDefaultToAccountNamespaceInBackstageCompleted()
            && metadata.getIdpV2MigrationInfo().isMigrateDefaultToAccountNamespaceInDependentsCompleted()) {
          NamespaceEntity.Metadata.IdpV2MigrationInfo.MigrateScopeInfo migrateScopeInfo =
              Objects.isNull(metadata.getIdpV2MigrationInfo().getMigrateScopeInfo())
              ? NamespaceEntity.Metadata.IdpV2MigrationInfo.MigrateScopeInfo.builder().build()
              : metadata.getIdpV2MigrationInfo().getMigrateScopeInfo();
          if (migrateScopeInfo.isActive()) {
            throw new InvalidRequestException("Entities migrate is already triggered and it's currently in progress.");
          } else {
            migrateScopeInfo.setRequest(write(body));
            migrateScopeInfo.setActive(true);
            User user = new User();
            user.setName(SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(USERNAME));
            user.email(SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(EMAIL));
            user.setUuid(SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(UNIQUE_ID));
            migrateScopeInfo.setUpdatedBy(user);
            migrateScopeInfo.setUpdatedAt(System.currentTimeMillis());
            NamespaceEntity.Metadata.IdpV2MigrationInfo idpV2MigrationInfo = metadata.getIdpV2MigrationInfo();
            idpV2MigrationInfo.setMigrateScopeInfo(migrateScopeInfo);
            metadata.setIdpV2MigrationInfo(idpV2MigrationInfo);
            namespaceService.save(namespaceEntity);
          }
        } else {
          throw new InvalidRequestException("IDP2.0 migration is not yet completed.");
        }
      }
    } finally {
      if (lock != null) {
        resourceLocker.releaseLock(lock);
      }
    }
  }

  @Override
  public void syncCatalogEntities(String harnessAccount, String option, CatalogSyncRequest body) {
    if (ObjectUtils.isEmpty(body)) {
      idpCommonService.checkUserAuthorization();
      migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(harnessAccount);
    } else {
      syncInSynchronousMode(harnessAccount, body.getIdentifier(), body.getAction().value());
    }
  }

  @Override
  public CatalogEntity changeScope(CatalogEntity existingCatalogEntity, ScopeInfo destinationScopeInfo) {
    try {
      List<CatalogEntity> referencedEntities =
          relationsProcessor.changeScope(existingCatalogEntity, destinationScopeInfo);
      String yaml = existingCatalogEntity.getYaml();
      Map<String, Object> entityYamlMap = YamlUtils.loadYamlStringAsMap(yaml);
      if (destinationScopeInfo.getOrgIdentifier() != null) {
        entityYamlMap.put("orgIdentifier", destinationScopeInfo.getOrgIdentifier());
      }
      if (destinationScopeInfo.getProjectIdentifier() != null) {
        entityYamlMap.put("projectIdentifier", destinationScopeInfo.getProjectIdentifier());
      }
      Set<String> groupingKinds = kindServiceHelper.groupingKinds(destinationScopeInfo.getAccountIdentifier());
      CatalogEntity modifiedEntity = CatalogMapper.yamlToEntity(destinationScopeInfo,
          existingCatalogEntity.getIdentifier(), existingCatalogEntity.getKind(),
          YamlUtils.writeObjectAsYaml(entityYamlMap), existingCatalogEntity.getDecorator(), groupingKinds);
      modifiedEntity.setId(existingCatalogEntity.getId());
      modifiedEntity.setUniqueId(existingCatalogEntity.getUniqueId());
      modifiedEntity.setCreatedAt(existingCatalogEntity.getCreatedAt());
      modifiedEntity.setCreatedBy(existingCatalogEntity.getCreatedBy());

      Object existingBackstageCatalog =
          harnessToIDPHelper.buildBackstageCatalog(existingCatalogEntity, false, false, false);
      Object modifiedBackstageCatalog = harnessToIDPHelper.buildBackstageCatalog(modifiedEntity, false, false, false);
      Map<String, List<Object>> actionOnEntities = new HashMap<>();
      actionOnEntities.put(DELETE_ACTION, List.of(existingBackstageCatalog));
      actionOnEntities.put(CREATE_ACTION, List.of(modifiedBackstageCatalog));

      List<CatalogEntity> entities = new ArrayList<>();
      entities.add(modifiedEntity);
      entities.addAll(referencedEntities);
      transactionHelper.performTransaction(() -> {
        idpToHarnessHelper.sendCatalogEventsToRedis(Collections.singletonList(existingCatalogEntity), DELETE_ACTION);
        entities.forEach(entity -> entity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(entity)));
        catalogEntityRepository.saveAll(entities);
        idpToHarnessHelper.sendCatalogEventsToRedis(Collections.singletonList(modifiedEntity), CREATE_ACTION);
        harnessToIDPHelper.harnessToIdpSyncForMigration(modifiedEntity.getAccountIdentifier(), actionOnEntities);
        if (modifiedEntity.getKind().equals(WORKFLOW_KIND)) {
          outboxService.save(new WorkflowUpdateEvent(destinationScopeInfo, modifiedEntity.getYaml(),
              existingCatalogEntity.getYaml(), modifiedEntity.getKind(), modifiedEntity.getIdentifier()));
        } else {
          outboxService.save(new CatalogCreateEvent(destinationScopeInfo, modifiedEntity.getYaml(),
              modifiedEntity.getKind(), modifiedEntity.getIdentifier()));
        }
        createOutboxUpdateEventForReferencedEntities(referencedEntities);
        return null;
      });
      idpToHarnessHelper.sendCatalogEventsToRedis(referencedEntities, UPDATE_ACTION);
      return modifiedEntity;
    } catch (Exception e) {
      log.error("Error occurred during the IDP 2.0 MigrationAPI Operation for account {} entityRef {}",
          existingCatalogEntity.getAccountIdentifier(), CatalogUtils.entityRef(existingCatalogEntity), e);
      throw new UnexpectedException(e.getMessage());
    }
  }

  @Override
  public String getJsonSchema(String kind) {
    try {
      kind = catalogServiceHelper.validateAndSanitizeKind(kind);
      return catalogServiceHelper.entitySchemaCache.get(kind + ".v1");
    } catch (ExecutionException e) {
      throw new UnexpectedException("Error while fetching json schema for kind = " + kind);
    }
  }

  @Override
  public GetEntitiesGroupsDTO getEntitiesGroups(String harnessAccount, String searchOnEntities, String searchOnGroups,
      String scopes, String kind, Boolean ownedByMe, Boolean favorites, String type, String owner, String lifecycle,
      String tags) {
    scopes = isEmpty(scopes) ? "account.*" : scopes;
    String kindCriteria = isEmpty(kind) ? WORKFLOW_KIND : kind;
    String accountScope = scopes.equals("account.*") ? "account" : scopes;
    String orgScope = scopes.equals("account.*") ? "account.org" : scopes;
    String projectScope = scopes.equals("account.*") ? "account.org.project" : scopes;

    CompletableFuture<GetEntitiesDTO> accountFuture = CompletableFuture.supplyAsync(
        ()
            -> getEntities(harnessAccount, 0, -1, null, searchOnEntities, false, accountScope, null, ownedByMe,
                favorites, WORKFLOW_KIND, type, owner, lifecycle, tags, null, false, false),
        entitiesGroupExecutor);
    CompletableFuture<GetEntitiesDTO> orgFuture = CompletableFuture.supplyAsync(
        ()
            -> getEntities(harnessAccount, 0, -1, null, searchOnEntities, false, orgScope, null, ownedByMe, favorites,
                WORKFLOW_KIND, type, owner, lifecycle, tags, null, false, false),
        entitiesGroupExecutor);
    CompletableFuture<GetEntitiesDTO> projectFuture = CompletableFuture.supplyAsync(
        ()
            -> getEntities(harnessAccount, 0, -1, null, searchOnEntities, false, projectScope, null, ownedByMe,
                favorites, WORKFLOW_KIND, type, owner, lifecycle, tags, null, false, false),
        entitiesGroupExecutor);

    try {
      CompletableFuture.allOf(accountFuture, orgFuture, projectFuture).join();
    } catch (CompletionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof WingsException) {
        throw (WingsException) cause;
      }
      throw ex;
    }

    GetEntitiesDTO accountWorkflows = accountFuture.join();
    GetEntitiesDTO orgWorkflows = orgFuture.join();
    GetEntitiesDTO projectWorkflows = projectFuture.join();

    GetEntitiesDTO validWorkflowsResponse = new GetEntitiesDTO();

    List<EntityResponse> mergedEntities = new ArrayList<>();
    mergedEntities.addAll(accountWorkflows.getEntityResponses());
    mergedEntities.addAll(orgWorkflows.getEntityResponses());
    mergedEntities.addAll(projectWorkflows.getEntityResponses());
    validWorkflowsResponse.setEntityResponses(mergedEntities);

    validWorkflowsResponse.setTotalElements(
        accountWorkflows.getTotalElements() + orgWorkflows.getTotalElements() + projectWorkflows.getTotalElements());

    validWorkflowsResponse.setTotalOwned(
        accountWorkflows.getTotalOwned() + orgWorkflows.getTotalOwned() + projectWorkflows.getTotalOwned());

    validWorkflowsResponse.setTotalStarred(
        accountWorkflows.getTotalStarred() + orgWorkflows.getTotalStarred() + projectWorkflows.getTotalStarred());

    validWorkflowsResponse.setPageNumber(1);

    Set<String> validWorkflowEntityRefs =
        validWorkflowsResponse.getEntityResponses()
            .stream()
            .map(EntityResponse::getEntityRef)
            .map(entityRef -> {
              Triple<String, String, String> entityRefKindScopeIdentifier =
                  catalogServiceHelper.getKindScopeIdentifier(entityRef);
              String entityRefKind = entityRefKindScopeIdentifier.getLeft();
              String entityRefScope = entityRefKindScopeIdentifier.getMiddle();
              String entityRefIdentifier = entityRefKindScopeIdentifier.getRight();
              StringBuilder entityRefBuilder = new StringBuilder();
              if ("workflow".equals(entityRefKind)) {
                entityRefBuilder.append(entityRefScope).append("/template/").append(entityRefIdentifier);
              } else {
                entityRefBuilder.append(entityRefScope)
                    .append("/")
                    .append(entityRefKind)
                    .append("/")
                    .append(entityRefIdentifier);
              }
              return entityRefBuilder.toString();
            })
            .collect(Collectors.toSet());

    EntitiesGroupsResponse entitiesGroupsResponse = new EntitiesGroupsResponse();

    List<GroupEntity> groupEntitiesForAccount = groupsRepository.findAllByAccountIdentifier(harnessAccount);

    List<GroupEntity> groupEntitiesForAccountWithSearchOnName;
    if (!isEmpty(searchOnGroups)) {
      groupEntitiesForAccountWithSearchOnName =
          groupsRepository.findByAccountIdentifierAndSearchOnName(harnessAccount, searchOnGroups);
    } else {
      groupEntitiesForAccountWithSearchOnName = groupEntitiesForAccount;
    }

    List<GroupEntity> groupEntities =
        Stream.concat(groupEntitiesForAccount.stream(), groupEntitiesForAccountWithSearchOnName.stream())
            .collect(Collectors.toMap(
                GroupEntity::getId, entity -> entity, (existing, replacement) -> replacement, LinkedHashMap::new))
            .values()
            .stream()
            .toList();

    Set<String> orgs =
        groupEntities.stream().map(GroupEntity::getOrgIdentifier).filter(Objects::nonNull).collect(Collectors.toSet());

    Map<String, Set<String>> projectsByOrg =
        groupEntities.stream()
            .filter(ge -> ge.getOrgIdentifier() != null && ge.getProjectIdentifier() != null)
            .collect(Collectors.groupingBy(GroupEntity::getOrgIdentifier,
                Collectors.mapping(GroupEntity::getProjectIdentifier, Collectors.toSet())));

    List<OrganizationDTO> organizationDTOS = getOrganizations(harnessAccount, orgs);
    List<ProjectDTO> projectDTOS = getProjectsByOrganization(harnessAccount, projectsByOrg);

    List<EntitiesGroups> accountGroups;
    List<EntitiesGroups> orgGroups;
    List<EntitiesGroups> projectGroups;

    Map<String, List<EntitiesGroups>> groupEntitiesByScope = new HashMap<>();
    Map<GroupEntity, List<String>> workflowsWithGroup = new HashMap<>();
    groupEntitiesForAccount.forEach(groupEntity -> {
      String groupOrgIdentifier = groupEntity.getOrgIdentifier();
      String groupProjectIdentifier = groupEntity.getProjectIdentifier();
      String groupIdentifier = groupEntity.getIdentifier();
      String groupName = groupEntity.getName();
      String groupDescription = groupEntity.getDescription();
      String groupIcon = groupEntity.getIcon();
      String groupScope =
          catalogServiceHelper.getScope(groupEntity.getAccountIdentifier(), groupOrgIdentifier, groupProjectIdentifier);
      List<String> workflows =
          catalogServiceHelper.filterValidWorkflows(groupEntity.getWorkflows(), validWorkflowEntityRefs);

      int workflowsInGroup = !isEmpty(workflows) ? workflows.size() : 0;
      EntitiesGroups entitiesGroups = new EntitiesGroups();
      entitiesGroups.setOrgIdentifier(groupOrgIdentifier);
      entitiesGroups.setProjectIdentifier(groupProjectIdentifier);
      entitiesGroups.setGroupIdentifier(groupIdentifier);
      entitiesGroups.setGroupName(groupName);
      entitiesGroups.setGroupDescription(groupDescription);
      entitiesGroups.setGroupIcon(groupIcon);
      entitiesGroups.setOrder(groupEntity.getOrder());
      entitiesGroups.setTotal(workflowsInGroup);

      List<EntitiesGroups> entitiesGroupsList = groupEntitiesByScope.getOrDefault(groupScope, new ArrayList<>());
      entitiesGroupsList.add(entitiesGroups);
      entitiesGroupsList.sort(Comparator.comparingInt(EntitiesGroups::getOrder));
      groupEntitiesByScope.put(groupScope, entitiesGroupsList);
    });

    groupEntitiesForAccountWithSearchOnName.forEach(groupEntity -> {
      List<String> workflows =
          catalogServiceHelper.filterValidWorkflows(groupEntity.getWorkflows(), validWorkflowEntityRefs);
      if (!isEmpty(workflows)) {
        workflowsWithGroup.put(groupEntity, workflows);
      }
    });

    accountGroups = groupEntitiesByScope.getOrDefault(ScopeLevel.ACCOUNT.name(), new ArrayList<>());
    orgGroups = groupEntitiesByScope.getOrDefault(ScopeLevel.ORGANIZATION.name(), new ArrayList<>());
    projectGroups = groupEntitiesByScope.getOrDefault(ScopeLevel.PROJECT.name(), new ArrayList<>());

    List<EntitiesGroupsResponseCountOrg> entitiesGroupsResponseCountOrgList = new ArrayList<>();
    List<EntitiesGroupsResponseCountProject> entitiesGroupsResponseCountProjectList = new ArrayList<>();

    Map<String, List<EntitiesGroups>> groupsByOrgIdentifier =
        orgGroups.stream().collect(Collectors.groupingBy(EntitiesGroups::getOrgIdentifier));

    Map<String, Map<String, List<EntitiesGroups>>> groupsByOrgIdentifierProjectIdentifier =
        projectGroups.stream().collect(Collectors.groupingBy(
            EntitiesGroups::getOrgIdentifier, Collectors.groupingBy(EntitiesGroups::getProjectIdentifier)));

    groupsByOrgIdentifier.forEach((k, v) -> {
      EntitiesGroupsResponseCountOrg entitiesGroupsResponseCountOrg = new EntitiesGroupsResponseCountOrg();
      entitiesGroupsResponseCountOrg.setOrgIdentifier(k);
      OrganizationDTO organizationDTO =
          organizationDTOS.stream().filter(org -> k.equals(org.getIdentifier())).findFirst().orElse(null);
      entitiesGroupsResponseCountOrg.setOrgName(organizationDTO != null ? organizationDTO.getName() : null);
      entitiesGroupsResponseCountOrg.setGroups(v);
      entitiesGroupsResponseCountOrgList.add(entitiesGroupsResponseCountOrg);
    });

    groupsByOrgIdentifierProjectIdentifier.forEach((k, v) -> v.forEach((k1, v1) -> {
      EntitiesGroupsResponseCountProject entitiesGroupsResponseCountProject = new EntitiesGroupsResponseCountProject();
      entitiesGroupsResponseCountProject.setOrgIdentifier(k);
      OrganizationDTO organizationDTO =
          organizationDTOS.stream().filter(org -> k.equals(org.getIdentifier())).findFirst().orElse(null);
      entitiesGroupsResponseCountProject.setOrgName(organizationDTO != null ? organizationDTO.getName() : null);
      entitiesGroupsResponseCountProject.setProjectIdentifier(k1);
      ProjectDTO projectDTO =
          projectDTOS.stream()
              .filter(project -> k.equals(project.getOrgIdentifier()) && k1.equals(project.getIdentifier()))
              .findFirst()
              .orElse(null);
      entitiesGroupsResponseCountProject.setProjectName(projectDTO != null ? projectDTO.getName() : null);
      entitiesGroupsResponseCountProject.setGroups(v1);
      entitiesGroupsResponseCountProjectList.add(entitiesGroupsResponseCountProject);
    }));

    EntitiesGroupsResponseCount entitiesGroupsResponseCount = new EntitiesGroupsResponseCount();
    entitiesGroupsResponseCount.setAccount(accountGroups);
    entitiesGroupsResponseCount.setOrg(entitiesGroupsResponseCountOrgList);
    entitiesGroupsResponseCount.setProject(entitiesGroupsResponseCountProjectList);
    entitiesGroupsResponseCount.setTotal(validWorkflowsResponse.getEntityResponses().size());

    EntitiesGroupsResponseData entitiesGroupsResponseData = new EntitiesGroupsResponseData();

    EntitiesGroupsResponseDataAccount entitiesGroupsResponseDataAccount = new EntitiesGroupsResponseDataAccount();
    List<EntitiesGroups> accountWorkflowsWithGroup = new ArrayList<>();
    List<EntityResponse> accountWorkflowsWithoutGroup = new ArrayList<>();
    accountWorkflows.getEntityResponses().forEach(entityResponse -> {
      if (entityResponse.getScope().equals(EntityResponse.ScopeEnum.ACCOUNT)) {
        entityResponse.setYaml(null);

        Object metadata = entityResponse.getMetadata();
        Map<String, Object> metadataMap = (Map<String, Object>) metadata;
        Set<String> topLevelKeys = Set.of("icon", "actionButton", "annotations");
        Map<String, Object> filtered = metadataMap.entrySet()
                                           .stream()
                                           .filter(e -> topLevelKeys.contains(e.getKey()))
                                           .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, Object> actionButton = (Map<String, Object>) filtered.get("actionButton");
        if (actionButton != null) {
          filtered.put("actionButton",
              actionButton.entrySet()
                  .stream()
                  .filter(e -> Set.of("text", "intent").contains(e.getKey()))
                  .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
        Map<String, Object> annotations = (Map<String, Object>) filtered.get("annotations");
        if (annotations != null) {
          filtered.put("annotations",
              annotations.entrySet()
                  .stream()
                  .filter(e
                      -> Set.of("backstage.io/techdocs-entity", "backstage.io/techdocs-ref",
                                "backstage.io/techdocs-entity-path")
                             .contains(e.getKey()))
                  .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
        entityResponse.setMetadata(filtered);

        entityResponse.setScorecards(null);
        Triple<String, String, String> kindScopeIdentifier =
            catalogServiceHelper.getKindScopeIdentifier(entityResponse.getEntityRef());
        if (workflowsWithGroup.values().stream().anyMatch(workflows
                -> workflows.contains(CatalogUtils.getIdentifierForWorkflowsInGroup(
                    kindScopeIdentifier.getMiddle(), TEMPLATE_KIND, kindScopeIdentifier.getRight())))) {
          List<GroupEntity> matchingGroupsForWorkflow =
              workflowsWithGroup.entrySet()
                  .stream()
                  .filter(entry
                      -> entry.getValue().contains(CatalogUtils.getIdentifierForWorkflowsInGroup(
                          kindScopeIdentifier.getMiddle(), TEMPLATE_KIND, kindScopeIdentifier.getRight())))
                  .map(Map.Entry::getKey)
                  .toList();
          matchingGroupsForWorkflow.forEach(groupEntity -> {
            EntitiesGroups entitiesGroups = new EntitiesGroups();
            if (!isEmpty(groupEntity.getOrgIdentifier())) {
              entitiesGroups.setOrgIdentifier(groupEntity.getOrgIdentifier());
              OrganizationDTO organizationDTO =
                  organizationDTOS.stream()
                      .filter(org -> groupEntity.getOrgIdentifier().equals(org.getIdentifier()))
                      .findFirst()
                      .orElse(null);
              entitiesGroups.setOrgName(organizationDTO != null ? organizationDTO.getName() : null);
            }
            if (!isEmpty(groupEntity.getProjectIdentifier())) {
              entitiesGroups.setProjectIdentifier(groupEntity.getProjectIdentifier());
              ProjectDTO projectDTO = projectDTOS.stream()
                                          .filter(project
                                              -> groupEntity.getOrgIdentifier().equals(project.getOrgIdentifier())
                                                  && groupEntity.getProjectIdentifier().equals(project.getIdentifier()))
                                          .findFirst()
                                          .orElse(null);
              entitiesGroups.setProjectName(projectDTO != null ? projectDTO.getName() : null);
            }
            entitiesGroups.setGroupIdentifier(groupEntity.getIdentifier());
            entitiesGroups.setGroupName(groupEntity.getName());
            entitiesGroups.setGroupDescription(groupEntity.getDescription());
            entitiesGroups.setGroupIcon(groupEntity.getIcon());
            entitiesGroups.setOrder(groupEntity.getOrder());
            entitiesGroups.setWorkflows(
                catalogServiceHelper.filterValidWorkflows(groupEntity.getWorkflows(), validWorkflowEntityRefs));
            entitiesGroups.setTotal(1);
            entitiesGroups.setEntities(Collections.singletonList(entityResponse));
            accountWorkflowsWithGroup.add(entitiesGroups);
          });
        } else {
          accountWorkflowsWithoutGroup.add(entityResponse);
        }
      }
    });

    groupEntitiesForAccountWithSearchOnName.forEach(groupEntity -> {
      if (isEmpty(groupEntity.getOrgIdentifier()) && isEmpty(groupEntity.getProjectIdentifier())) {
        EntitiesGroups entitiesGroups = new EntitiesGroups();
        if (!isEmpty(groupEntity.getOrgIdentifier())) {
          entitiesGroups.setOrgIdentifier(groupEntity.getOrgIdentifier());
          OrganizationDTO organizationDTO =
              organizationDTOS.stream()
                  .filter(org -> groupEntity.getOrgIdentifier().equals(org.getIdentifier()))
                  .findFirst()
                  .orElse(null);
          entitiesGroups.setOrgName(organizationDTO != null ? organizationDTO.getName() : null);
        }
        if (!isEmpty(groupEntity.getProjectIdentifier())) {
          entitiesGroups.setProjectIdentifier(groupEntity.getProjectIdentifier());
          ProjectDTO projectDTO = projectDTOS.stream()
                                      .filter(project
                                          -> groupEntity.getOrgIdentifier().equals(project.getOrgIdentifier())
                                              && groupEntity.getProjectIdentifier().equals(project.getIdentifier()))
                                      .findFirst()
                                      .orElse(null);
          entitiesGroups.setProjectName(projectDTO != null ? projectDTO.getName() : null);
        }
        entitiesGroups.setGroupIdentifier(groupEntity.getIdentifier());
        entitiesGroups.setGroupName(groupEntity.getName());
        entitiesGroups.setGroupDescription(groupEntity.getDescription());
        entitiesGroups.setGroupIcon(groupEntity.getIcon());
        entitiesGroups.setOrder(groupEntity.getOrder());
        entitiesGroups.setWorkflows(
            catalogServiceHelper.filterValidWorkflows(groupEntity.getWorkflows(), validWorkflowEntityRefs));
        entitiesGroups.setTotal(0);
        entitiesGroups.setEntities(Collections.emptyList());
        accountWorkflowsWithGroup.add(entitiesGroups);
      }
    });

    List<EntitiesGroups> accountWorkflowsWithGroupResponse =
        accountWorkflowsWithGroup.stream()
            .collect(Collectors.groupingBy(entityGroup
                -> Arrays.asList(entityGroup.getOrgIdentifier(), entityGroup.getProjectIdentifier(),
                    entityGroup.getGroupIdentifier()),
                Collectors.collectingAndThen(Collectors.toList(),
                    groupedList -> {
                      EntitiesGroups first = groupedList.get(0);
                      int totalSum = groupedList.stream().mapToInt(EntitiesGroups::getTotal).sum();

                      List<EntityResponse> allEntities =
                          groupedList.stream().flatMap(eg -> eg.getEntities().stream()).collect(Collectors.toList());
                      allEntities = !isEmpty(allEntities) ? allEntities : new ArrayList<>();
                      List<String> workflows = !isEmpty(first.getWorkflows())
                          ? catalogServiceHelper.filterValidWorkflows(first.getWorkflows(), validWorkflowEntityRefs)
                          : new ArrayList<>();

                      List<EntityResponse> allEntitiesOrdered = new ArrayList<>();
                      Map<String, EntityResponse> entityMapByEntityRef = new HashMap<>();
                      for (EntityResponse entity : allEntities) {
                        entityMapByEntityRef.put(entity.getEntityRef(), entity);
                      }
                      for (String workflow : workflows) {
                        String[] workflowParts = workflow.split("/");
                        String scope = workflowParts[0];
                        String kindValue = workflowParts[1];
                        String identifier = workflowParts[2];
                        if (kindValue.equals("template")) {
                          kindValue = "workflow";
                        }
                        String entityKey = kindValue + ":" + scope + "/" + identifier;
                        if (entityMapByEntityRef.containsKey(entityKey)) {
                          allEntitiesOrdered.add(entityMapByEntityRef.get(entityKey));
                        }
                      }

                      EntitiesGroups aggregated = new EntitiesGroups();
                      aggregated.setOrgIdentifier(first.getOrgIdentifier());
                      aggregated.setOrgName(first.getOrgName());
                      aggregated.setProjectIdentifier(first.getProjectIdentifier());
                      aggregated.setProjectName(first.getProjectName());
                      aggregated.setGroupIdentifier(first.getGroupIdentifier());
                      aggregated.setGroupName(first.getGroupName());
                      aggregated.setGroupDescription(first.getGroupDescription());
                      aggregated.setGroupIcon(first.getGroupIcon());
                      aggregated.setOrder(first.getOrder());
                      aggregated.setTotal(totalSum);
                      aggregated.setEntities(allEntitiesOrdered);

                      return aggregated;
                    })))
            .values()
            .stream()
            .sorted(Comparator.comparingInt(EntitiesGroups::getOrder))
            .collect(Collectors.toList());
    entitiesGroupsResponseDataAccount.setWithGroup(accountWorkflowsWithGroupResponse);
    entitiesGroupsResponseDataAccount.setWithoutGroup(accountWorkflowsWithoutGroup);
    entitiesGroupsResponseData.setAccount(entitiesGroupsResponseDataAccount);

    EntitiesGroupsResponseDataAccount entitiesGroupsResponseDataOrg = new EntitiesGroupsResponseDataAccount();
    List<EntitiesGroups> orgWorkflowsWithGroup = new ArrayList<>();
    List<EntityResponse> orgWorkflowsWithoutGroup = new ArrayList<>();
    orgWorkflows.getEntityResponses().forEach(entityResponse -> {
      if (entityResponse.getScope().equals(EntityResponse.ScopeEnum.ORGANIZATION)) {
        entityResponse.setYaml(null);

        Object metadata = entityResponse.getMetadata();
        Map<String, Object> metadataMap = (Map<String, Object>) metadata;
        Set<String> topLevelKeys = Set.of("icon", "actionButton", "annotations");
        Map<String, Object> filtered = metadataMap.entrySet()
                                           .stream()
                                           .filter(e -> topLevelKeys.contains(e.getKey()))
                                           .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, Object> actionButton = (Map<String, Object>) filtered.get("actionButton");
        if (actionButton != null) {
          filtered.put("actionButton",
              actionButton.entrySet()
                  .stream()
                  .filter(e -> Set.of("text", "intent").contains(e.getKey()))
                  .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
        Map<String, Object> annotations = (Map<String, Object>) filtered.get("annotations");
        if (annotations != null) {
          filtered.put("annotations",
              annotations.entrySet()
                  .stream()
                  .filter(e
                      -> Set.of("backstage.io/techdocs-entity", "backstage.io/techdocs-ref",
                                "backstage.io/techdocs-entity-path")
                             .contains(e.getKey()))
                  .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
        entityResponse.setMetadata(filtered);

        entityResponse.setScorecards(null);
        Triple<String, String, String> kindScopeIdentifier =
            catalogServiceHelper.getKindScopeIdentifier(entityResponse.getEntityRef());
        if (workflowsWithGroup.values().stream().anyMatch(workflows
                -> workflows.contains(CatalogUtils.getIdentifierForWorkflowsInGroup(
                    kindScopeIdentifier.getMiddle(), TEMPLATE_KIND, kindScopeIdentifier.getRight())))) {
          List<GroupEntity> matchingGroupsForWorkflow =
              workflowsWithGroup.entrySet()
                  .stream()
                  .filter(entry
                      -> entry.getValue().contains(CatalogUtils.getIdentifierForWorkflowsInGroup(
                          kindScopeIdentifier.getMiddle(), TEMPLATE_KIND, kindScopeIdentifier.getRight())))
                  .map(Map.Entry::getKey)
                  .toList();
          matchingGroupsForWorkflow.forEach(groupEntity -> {
            EntitiesGroups entitiesGroups = new EntitiesGroups();
            if (!isEmpty(groupEntity.getOrgIdentifier())) {
              entitiesGroups.setOrgIdentifier(groupEntity.getOrgIdentifier());
              OrganizationDTO organizationDTO =
                  organizationDTOS.stream()
                      .filter(org -> groupEntity.getOrgIdentifier().equals(org.getIdentifier()))
                      .findFirst()
                      .orElse(null);
              entitiesGroups.setOrgName(organizationDTO != null ? organizationDTO.getName() : null);
            }
            if (!isEmpty(groupEntity.getProjectIdentifier())) {
              entitiesGroups.setProjectIdentifier(groupEntity.getProjectIdentifier());
              ProjectDTO projectDTO = projectDTOS.stream()
                                          .filter(project
                                              -> groupEntity.getOrgIdentifier().equals(project.getOrgIdentifier())
                                                  && groupEntity.getProjectIdentifier().equals(project.getIdentifier()))
                                          .findFirst()
                                          .orElse(null);
              entitiesGroups.setProjectName(projectDTO != null ? projectDTO.getName() : null);
            }
            entitiesGroups.setGroupIdentifier(groupEntity.getIdentifier());
            entitiesGroups.setGroupName(groupEntity.getName());
            entitiesGroups.setGroupDescription(groupEntity.getDescription());
            entitiesGroups.setGroupIcon(groupEntity.getIcon());
            entitiesGroups.setOrder(groupEntity.getOrder());
            entitiesGroups.setWorkflows(
                catalogServiceHelper.filterValidWorkflows(groupEntity.getWorkflows(), validWorkflowEntityRefs));
            entitiesGroups.setTotal(1);
            entitiesGroups.setEntities(Collections.singletonList(entityResponse));
            orgWorkflowsWithGroup.add(entitiesGroups);
          });
        } else {
          orgWorkflowsWithoutGroup.add(entityResponse);
        }
      }
    });

    groupEntitiesForAccountWithSearchOnName.forEach(groupEntity -> {
      if (!isEmpty(groupEntity.getOrgIdentifier()) && isEmpty(groupEntity.getProjectIdentifier())) {
        EntitiesGroups entitiesGroups = new EntitiesGroups();
        if (!isEmpty(groupEntity.getOrgIdentifier())) {
          entitiesGroups.setOrgIdentifier(groupEntity.getOrgIdentifier());
          OrganizationDTO organizationDTO =
              organizationDTOS.stream()
                  .filter(org -> groupEntity.getOrgIdentifier().equals(org.getIdentifier()))
                  .findFirst()
                  .orElse(null);
          entitiesGroups.setOrgName(organizationDTO != null ? organizationDTO.getName() : null);
        }
        if (!isEmpty(groupEntity.getProjectIdentifier())) {
          entitiesGroups.setProjectIdentifier(groupEntity.getProjectIdentifier());
          ProjectDTO projectDTO = projectDTOS.stream()
                                      .filter(project
                                          -> groupEntity.getOrgIdentifier().equals(project.getOrgIdentifier())
                                              && groupEntity.getProjectIdentifier().equals(project.getIdentifier()))
                                      .findFirst()
                                      .orElse(null);
          entitiesGroups.setProjectName(projectDTO != null ? projectDTO.getName() : null);
        }
        entitiesGroups.setGroupIdentifier(groupEntity.getIdentifier());
        entitiesGroups.setGroupName(groupEntity.getName());
        entitiesGroups.setGroupDescription(groupEntity.getDescription());
        entitiesGroups.setGroupIcon(groupEntity.getIcon());
        entitiesGroups.setOrder(groupEntity.getOrder());
        entitiesGroups.setWorkflows(
            catalogServiceHelper.filterValidWorkflows(groupEntity.getWorkflows(), validWorkflowEntityRefs));
        entitiesGroups.setTotal(0);
        entitiesGroups.setEntities(Collections.emptyList());
        orgWorkflowsWithGroup.add(entitiesGroups);
      }
    });

    List<EntitiesGroups> orgWorkflowsWithGroupResponse =
        orgWorkflowsWithGroup.stream()
            .collect(Collectors.groupingBy(entityGroup
                -> Arrays.asList(entityGroup.getOrgIdentifier(), entityGroup.getProjectIdentifier(),
                    entityGroup.getGroupIdentifier()),
                Collectors.collectingAndThen(Collectors.toList(),
                    groupedList -> {
                      EntitiesGroups first = groupedList.get(0);
                      int totalSum = groupedList.stream().mapToInt(EntitiesGroups::getTotal).sum();

                      List<EntityResponse> allEntities =
                          groupedList.stream().flatMap(eg -> eg.getEntities().stream()).collect(Collectors.toList());
                      allEntities = !isEmpty(allEntities) ? allEntities : new ArrayList<>();
                      List<String> workflows = !isEmpty(first.getWorkflows())
                          ? catalogServiceHelper.filterValidWorkflows(first.getWorkflows(), validWorkflowEntityRefs)
                          : new ArrayList<>();

                      List<EntityResponse> allEntitiesOrdered = new ArrayList<>();
                      Map<String, EntityResponse> entityMapByEntityRef = new HashMap<>();
                      for (EntityResponse entity : allEntities) {
                        entityMapByEntityRef.put(entity.getEntityRef(), entity);
                      }
                      for (String workflow : workflows) {
                        String[] workflowParts = workflow.split("/");
                        String scope = workflowParts[0];
                        String kindValue = workflowParts[1];
                        String identifier = workflowParts[2];
                        if (kindValue.equals("template")) {
                          kindValue = "workflow";
                        }
                        String entityKey = kindValue + ":" + scope + "/" + identifier;
                        if (entityMapByEntityRef.containsKey(entityKey)) {
                          allEntitiesOrdered.add(entityMapByEntityRef.get(entityKey));
                        }
                      }

                      EntitiesGroups aggregated = new EntitiesGroups();
                      aggregated.setOrgIdentifier(first.getOrgIdentifier());
                      aggregated.setOrgName(first.getOrgName());
                      aggregated.setProjectIdentifier(first.getProjectIdentifier());
                      aggregated.setProjectName(first.getProjectName());
                      aggregated.setGroupIdentifier(first.getGroupIdentifier());
                      aggregated.setGroupName(first.getGroupName());
                      aggregated.setGroupDescription(first.getGroupDescription());
                      aggregated.setGroupIcon(first.getGroupIcon());
                      aggregated.setOrder(first.getOrder());
                      aggregated.setTotal(totalSum);
                      aggregated.setEntities(allEntitiesOrdered);

                      return aggregated;
                    })))
            .values()
            .stream()
            .sorted(Comparator.comparingInt(EntitiesGroups::getOrder))
            .collect(Collectors.toList());
    entitiesGroupsResponseDataOrg.setWithGroup(orgWorkflowsWithGroupResponse);
    entitiesGroupsResponseDataOrg.setWithoutGroup(orgWorkflowsWithoutGroup);
    entitiesGroupsResponseData.setOrg(entitiesGroupsResponseDataOrg);

    EntitiesGroupsResponseDataAccount entitiesGroupsResponseDataProject = new EntitiesGroupsResponseDataAccount();
    List<EntitiesGroups> projectWorkflowsWithGroup = new ArrayList<>();
    List<EntityResponse> projectWorkflowsWithoutGroup = new ArrayList<>();
    projectWorkflows.getEntityResponses().forEach(entityResponse -> {
      if (entityResponse.getScope().equals(EntityResponse.ScopeEnum.PROJECT)) {
        entityResponse.setYaml(null);

        Object metadata = entityResponse.getMetadata();
        Map<String, Object> metadataMap = (Map<String, Object>) metadata;
        Set<String> topLevelKeys = Set.of("icon", "actionButton", "annotations");
        Map<String, Object> filtered = metadataMap.entrySet()
                                           .stream()
                                           .filter(e -> topLevelKeys.contains(e.getKey()))
                                           .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, Object> actionButton = (Map<String, Object>) filtered.get("actionButton");
        if (actionButton != null) {
          filtered.put("actionButton",
              actionButton.entrySet()
                  .stream()
                  .filter(e -> Set.of("text", "intent").contains(e.getKey()))
                  .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
        Map<String, Object> annotations = (Map<String, Object>) filtered.get("annotations");
        if (annotations != null) {
          filtered.put("annotations",
              annotations.entrySet()
                  .stream()
                  .filter(e
                      -> Set.of("backstage.io/techdocs-entity", "backstage.io/techdocs-ref",
                                "backstage.io/techdocs-entity-path")
                             .contains(e.getKey()))
                  .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
        entityResponse.setMetadata(filtered);

        entityResponse.setScorecards(null);
        Triple<String, String, String> kindScopeIdentifier =
            catalogServiceHelper.getKindScopeIdentifier(entityResponse.getEntityRef());
        if (workflowsWithGroup.values().stream().anyMatch(workflows
                -> workflows.contains(CatalogUtils.getIdentifierForWorkflowsInGroup(
                    kindScopeIdentifier.getMiddle(), TEMPLATE_KIND, kindScopeIdentifier.getRight())))) {
          List<GroupEntity> matchingGroupsForWorkflow =
              workflowsWithGroup.entrySet()
                  .stream()
                  .filter(entry
                      -> entry.getValue().contains(CatalogUtils.getIdentifierForWorkflowsInGroup(
                          kindScopeIdentifier.getMiddle(), TEMPLATE_KIND, kindScopeIdentifier.getRight())))
                  .map(Map.Entry::getKey)
                  .toList();
          matchingGroupsForWorkflow.forEach(groupEntity -> {
            EntitiesGroups entitiesGroups = new EntitiesGroups();
            if (!isEmpty(groupEntity.getOrgIdentifier())) {
              entitiesGroups.setOrgIdentifier(groupEntity.getOrgIdentifier());
              OrganizationDTO organizationDTO =
                  organizationDTOS.stream()
                      .filter(org -> groupEntity.getOrgIdentifier().equals(org.getIdentifier()))
                      .findFirst()
                      .orElse(null);
              entitiesGroups.setOrgName(organizationDTO != null ? organizationDTO.getName() : null);
            }
            if (!isEmpty(groupEntity.getProjectIdentifier())) {
              entitiesGroups.setProjectIdentifier(groupEntity.getProjectIdentifier());
              ProjectDTO projectDTO = projectDTOS.stream()
                                          .filter(project
                                              -> groupEntity.getOrgIdentifier().equals(project.getOrgIdentifier())
                                                  && groupEntity.getProjectIdentifier().equals(project.getIdentifier()))
                                          .findFirst()
                                          .orElse(null);
              entitiesGroups.setProjectName(projectDTO != null ? projectDTO.getName() : null);
            }
            entitiesGroups.setGroupIdentifier(groupEntity.getIdentifier());
            entitiesGroups.setGroupName(groupEntity.getName());
            entitiesGroups.setGroupDescription(groupEntity.getDescription());
            entitiesGroups.setGroupIcon(groupEntity.getIcon());
            entitiesGroups.setOrder(groupEntity.getOrder());
            entitiesGroups.setWorkflows(
                catalogServiceHelper.filterValidWorkflows(groupEntity.getWorkflows(), validWorkflowEntityRefs));
            entitiesGroups.setTotal(1);
            entitiesGroups.setEntities(Collections.singletonList(entityResponse));
            projectWorkflowsWithGroup.add(entitiesGroups);
          });
        } else {
          projectWorkflowsWithoutGroup.add(entityResponse);
        }
      }
    });

    groupEntitiesForAccountWithSearchOnName.forEach(groupEntity -> {
      if (!isEmpty(groupEntity.getOrgIdentifier()) && !isEmpty(groupEntity.getProjectIdentifier())) {
        EntitiesGroups entitiesGroups = new EntitiesGroups();
        if (!isEmpty(groupEntity.getOrgIdentifier())) {
          entitiesGroups.setOrgIdentifier(groupEntity.getOrgIdentifier());
          OrganizationDTO organizationDTO =
              organizationDTOS.stream()
                  .filter(org -> groupEntity.getOrgIdentifier().equals(org.getIdentifier()))
                  .findFirst()
                  .orElse(null);
          entitiesGroups.setOrgName(organizationDTO != null ? organizationDTO.getName() : null);
        }
        if (!isEmpty(groupEntity.getProjectIdentifier())) {
          entitiesGroups.setProjectIdentifier(groupEntity.getProjectIdentifier());
          ProjectDTO projectDTO = projectDTOS.stream()
                                      .filter(project
                                          -> groupEntity.getOrgIdentifier().equals(project.getOrgIdentifier())
                                              && groupEntity.getProjectIdentifier().equals(project.getIdentifier()))
                                      .findFirst()
                                      .orElse(null);
          entitiesGroups.setProjectName(projectDTO != null ? projectDTO.getName() : null);
        }
        entitiesGroups.setGroupIdentifier(groupEntity.getIdentifier());
        entitiesGroups.setGroupName(groupEntity.getName());
        entitiesGroups.setGroupDescription(groupEntity.getDescription());
        entitiesGroups.setGroupIcon(groupEntity.getIcon());
        entitiesGroups.setOrder(groupEntity.getOrder());
        entitiesGroups.setWorkflows(
            catalogServiceHelper.filterValidWorkflows(groupEntity.getWorkflows(), validWorkflowEntityRefs));
        entitiesGroups.setTotal(0);
        entitiesGroups.setEntities(Collections.emptyList());
        projectWorkflowsWithGroup.add(entitiesGroups);
      }
    });

    List<EntitiesGroups> projectWorkflowsWithGroupResponse =
        projectWorkflowsWithGroup.stream()
            .collect(Collectors.groupingBy(entityGroup
                -> Arrays.asList(entityGroup.getOrgIdentifier(), entityGroup.getProjectIdentifier(),
                    entityGroup.getGroupIdentifier()),
                Collectors.collectingAndThen(Collectors.toList(),
                    groupedList -> {
                      EntitiesGroups first = groupedList.get(0);
                      int totalSum = groupedList.stream().mapToInt(EntitiesGroups::getTotal).sum();

                      List<EntityResponse> allEntities =
                          groupedList.stream().flatMap(eg -> eg.getEntities().stream()).collect(Collectors.toList());
                      allEntities = !isEmpty(allEntities) ? allEntities : new ArrayList<>();
                      List<String> workflows = !isEmpty(first.getWorkflows())
                          ? catalogServiceHelper.filterValidWorkflows(first.getWorkflows(), validWorkflowEntityRefs)
                          : new ArrayList<>();

                      List<EntityResponse> allEntitiesOrdered = new ArrayList<>();
                      Map<String, EntityResponse> entityMapByEntityRef = new HashMap<>();
                      for (EntityResponse entity : allEntities) {
                        entityMapByEntityRef.put(entity.getEntityRef(), entity);
                      }
                      for (String workflow : workflows) {
                        String[] workflowParts = workflow.split("/");
                        String scope = workflowParts[0];
                        String kindValue = workflowParts[1];
                        String identifier = workflowParts[2];
                        if (kindValue.equals("template")) {
                          kindValue = "workflow";
                        }
                        String entityKey = kindValue + ":" + scope + "/" + identifier;
                        if (entityMapByEntityRef.containsKey(entityKey)) {
                          allEntitiesOrdered.add(entityMapByEntityRef.get(entityKey));
                        }
                      }

                      EntitiesGroups aggregated = new EntitiesGroups();
                      aggregated.setOrgIdentifier(first.getOrgIdentifier());
                      aggregated.setOrgName(first.getOrgName());
                      aggregated.setProjectIdentifier(first.getProjectIdentifier());
                      aggregated.setProjectName(first.getProjectName());
                      aggregated.setGroupIdentifier(first.getGroupIdentifier());
                      aggregated.setGroupName(first.getGroupName());
                      aggregated.setGroupDescription(first.getGroupDescription());
                      aggregated.setGroupIcon(first.getGroupIcon());
                      aggregated.setOrder(first.getOrder());
                      aggregated.setTotal(totalSum);
                      aggregated.setEntities(allEntitiesOrdered);

                      return aggregated;
                    })))
            .values()
            .stream()
            .sorted(Comparator.comparingInt(EntitiesGroups::getOrder))
            .collect(Collectors.toList());
    entitiesGroupsResponseDataProject.setWithGroup(projectWorkflowsWithGroupResponse);
    entitiesGroupsResponseDataProject.setWithoutGroup(projectWorkflowsWithoutGroup);
    entitiesGroupsResponseData.setProject(entitiesGroupsResponseDataProject);

    entitiesGroupsResponse.setData(entitiesGroupsResponseData);

    long totalOwned =
        accountWorkflows.getTotalOwned() + orgWorkflows.getTotalOwned() + projectWorkflows.getTotalOwned();
    long totalStarred =
        accountWorkflows.getTotalStarred() + orgWorkflows.getTotalStarred() + projectWorkflows.getTotalStarred();

    entitiesGroupsResponseCount.setTotalOwned((int) totalOwned);
    entitiesGroupsResponseCount.setTotalStarred((int) totalStarred);

    entitiesGroupsResponse.setCount(entitiesGroupsResponseCount);

    return GetEntitiesGroupsDTO.builder()
        .entitiesGroupsResponse(entitiesGroupsResponse)
        .totalOwned(totalOwned)
        .totalStarred(totalStarred)
        .build();
  }

  @Override
  public void recreateCatalogsWithAccountAsNamespaceForIDPV2(String accountIdentifier) {
    List<CatalogEntity> catalogEntityListForAccount =
        catalogEntityRepository.findAllByParentUniqueId(accountIdentifier);
    for (CatalogEntity catalogEntity : catalogEntityListForAccount) {
      EntityRequest entityRequest = new EntityRequest();
      entityRequest.setYaml(catalogEntity.getYaml());
      log.info("Updating account as namespace for catalog entity identifier - {} and account - {}",
          catalogEntity.getIdentifier(), catalogEntity.getAccountIdentifier());
      updateEntityForMigrationDefaultToAccount(catalogEntity, catalogEntity.getAccountIdentifier());
    }
  }

  @Override
  public CatalogEntity getCatalogEntityByParentUniqueIdAndKindAndIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String kind, String identifier) {
    ScopeInfo scopeInfo =
        getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
    Optional<CatalogEntity> optionalCatalogEntity =
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(scopeInfo.getUniqueId(), kind, identifier);
    if (optionalCatalogEntity.isEmpty()) {
      throw new InvalidRequestException(String.format("No record found for kind %s, identifier %s", kind, identifier));
    }
    return optionalCatalogEntity.get();
  }

  private void handleCreateOrUpdateAction(
      String accountIdentifier, String entityUid, String action, Boolean creatingEntity) {
    Object response;
    try {
      response = getGeneralResponse(
          backstageResourceClient.getCatalogEntityByName(accountIdentifier, getEntityUniqueIdForByNameAPI(entityUid)));
    } catch (Exception ex) {
      log.warn("Error in fetching catalog entity by name for account = {} entityUid = {} Error = {}", accountIdentifier,
          entityUid, ex.getMessage(), ex);
      return;
    }
    Map<String, Object> filteredBackstageCatalogEntity = (Map<String, Object>) response;
    if (creatingEntity) {
      String kind = from(filteredBackstageCatalogEntity, "kind", String.class);
      kind = Objects.requireNonNull(kind).toLowerCase();
      String name = from(filteredBackstageCatalogEntity, "metadata.name", String.class);
      name = Objects.requireNonNull(name).toLowerCase();
      String namespace = from(filteredBackstageCatalogEntity, "metadata.namespace", String.class);
      Optional<CatalogEntity> optionalCatalogEntity =
          catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(accountIdentifier, kind, name);
      if (optionalCatalogEntity.isPresent()) {
        name = namespace + "_" + name;
        Map<String, Object> metadata = (Map<String, Object>) filteredBackstageCatalogEntity.get("metadata");
        metadata.put("name", name);
        filteredBackstageCatalogEntity.put("metadata", metadata);
      }
    }
    List<Map<String, Object>> filteredBackstageCatalogEntities = new ArrayList<>();
    filteredBackstageCatalogEntities.add(filteredBackstageCatalogEntity);
    syncInternal(accountIdentifier, entityUid, action, filteredBackstageCatalogEntities, creatingEntity);
  }

  private void handleDeleteAction(String accountIdentifier, String entityUid) {
    log.info("Delete action received in BackstageToHarnessCatalog sync for accountIdentifier = {}, entityUid = {}",
        accountIdentifier, entityUid);
    ScopeInfo scopeInfo =
        catalogScopeResolver.resolveSingleScopeInfo(accountIdentifier, CatalogUtils.getScope(null, null));
    catalogEntityRepository.deleteByParentUniqueIdAndKindAndIdentifier(scopeInfo.getUniqueId(),
        CatalogUtils.getBackstageCatalogKindFromEntityUid(entityUid),
        CatalogUtils.getBackstageCatalogNameFromEntityUid(entityUid));
    log.info("Deleted catalog entities from BackstageToHarnessCatalog accountIdentifier = {}, entityUid = {}",
        accountIdentifier, entityUid);
  }

  @Override
  public List<EnvironmentBluePrintInfoResponse> getEnvironmentBlueprintInfo(
      String accountIdentifier, List<String> bluePrintIdentifiers) {
    List<EnvironmentBluePrintInfoResponse> environmentBluePrintInfoResponses = new ArrayList<>();

    // Parse scoped entity refs (e.g. "environmentblueprint:account.org.project/id") and group by scope
    Map<String, List<String>> scopeToIdentifiers = new HashMap<>();
    for (String blueprintRef : bluePrintIdentifiers) {
      Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(blueprintRef);
      scopeToIdentifiers.computeIfAbsent(kindScopeIdentifier.getMiddle(), k -> new ArrayList<>())
          .add(kindScopeIdentifier.getRight());
    }

    // Resolve all scopes upfront, then fetch all blueprints in a single DB query
    Map<String, Pair<String, String>> uniqueIdToOrgProject = new LinkedHashMap<>();
    Set<String> requestedIdentifiers = new HashSet<>();
    for (Map.Entry<String, List<String>> entry : scopeToIdentifiers.entrySet()) {
      Pair<String, String> orgProject = catalogServiceHelper.getOrgProjectFromScope(entry.getKey());
      ScopeInfo scopeInfo =
          getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgProject.getLeft(), orgProject.getRight()));
      uniqueIdToOrgProject.put(scopeInfo.getUniqueId(), orgProject);
      requestedIdentifiers.addAll(entry.getValue());
    }

    List<CatalogEntity> fetchedBlueprints = catalogEntityRepository.findByParentUniqueIdInAndKind(
        new ArrayList<>(uniqueIdToOrgProject.keySet()), ENVIRONMENT_BLUEPRINT_KIND);

    // Filter to only requested identifiers and build entity ref map
    Map<String, CatalogEntity> entityRefToBlueprintMap = new LinkedHashMap<>();
    for (CatalogEntity bp : fetchedBlueprints) {
      if (!requestedIdentifiers.contains(bp.getIdentifier())) {
        continue;
      }
      Pair<String, String> orgProject = uniqueIdToOrgProject.get(bp.getParentUniqueId());
      String entityRef = CatalogUtils.entityRef(
          ENVIRONMENT_BLUEPRINT_KIND, orgProject.getLeft(), orgProject.getRight(), bp.getIdentifier());
      entityRefToBlueprintMap.put(entityRef, bp);
    }

    // Batch RBAC check — filter to only permitted blueprints
    Set<String> permittedEntityRefs = new HashSet<>(
        catalogServiceHelper.filterPermittedEntityRefs(accountIdentifier, entityRefToBlueprintMap.keySet()));
    List<CatalogEntity> allBluePrints = new ArrayList<>();
    Set<String> blueprintEntityRefs = new HashSet<>();
    for (Map.Entry<String, CatalogEntity> mapEntry : entityRefToBlueprintMap.entrySet()) {
      if (permittedEntityRefs.contains(mapEntry.getKey())) {
        allBluePrints.add(mapEntry.getValue());
        blueprintEntityRefs.add(mapEntry.getKey());
      }
    }

    List<String> ids = allBluePrints.stream().map(CatalogEntity::getId).collect(Collectors.toList());
    List<CatalogEntityVersion> catalogEntityVersions = catalogEntityVersionRepository.findAllByEntityIdIn(ids);
    Map<String, List<CatalogEntityVersion>> catalogEntityVersionMap =
        catalogEntityVersions.stream().collect(Collectors.groupingBy(CatalogEntityVersion::getEntityId));

    List<CatalogEntity> environments =
        catalogEntityRepository.findAllByAccountIdentifierAndKind(accountIdentifier, ENVIRONMENT_KIND);

    // Map blueprint identifier -> list of environments referencing it
    Map<String, List<CatalogEntity>> blueprintToEnvironmentsMap = new HashMap<>();
    for (CatalogEntity env : environments) {
      Map<String, Object> spec = env.getSpec();
      if (spec == null) {
        continue;
      }
      Map<String, Object> envBlueprint = from(spec, "environmentBlueprint", Map.class);
      if (envBlueprint == null) {
        continue;
      }
      String blueprintId = from(envBlueprint, "identifier", String.class);
      if (isEmpty(blueprintId)) {
        continue;
      }
      // Resolve the scoped identifier relative to the environment's own scope
      // e.g. "org.MyBP" in env with org=default, project=ssem -> resolvedOrg=default, resolvedProject=null
      // bare "MyBP" in env with org=default, project=ssem -> resolvedOrg=default, resolvedProject=ssem
      String[] resolvedScope =
          CommonUtils.resolveScopeFromIdentifier(blueprintId, env.getOrgIdentifier(), env.getProjectIdentifier());
      String bareBlueprintId = CommonUtils.removeScopeFromIdentifier(blueprintId);
      String entityRef =
          CatalogUtils.entityRef(ENVIRONMENT_BLUEPRINT_KIND, resolvedScope[0], resolvedScope[1], bareBlueprintId);
      if (blueprintEntityRefs.contains(entityRef)) {
        blueprintToEnvironmentsMap.computeIfAbsent(bareBlueprintId, k -> new ArrayList<>()).add(env);
      }
    }

    for (CatalogEntity catalogEntity : allBluePrints) {
      EnvironmentBluePrintInfoResponse environmentBluePrintInfoResponse = new EnvironmentBluePrintInfoResponse();
      List<EnvironmentBluePrintVersionInfo> environmentBluePrintVersionInfos = new ArrayList<>();
      if (catalogEntityVersionMap.containsKey(catalogEntity.getId())) {
        List<CatalogEntityVersion> versions = catalogEntityVersionMap.get(catalogEntity.getId());
        for (CatalogEntityVersion catalogEntityVersion : versions) {
          EnvironmentBluePrintVersionInfo environmentBluePrintVersionInfo = new EnvironmentBluePrintVersionInfo();
          environmentBluePrintVersionInfo.setVersion(catalogEntityVersion.getVersion());
          environmentBluePrintVersionInfo.setYaml(catalogEntityVersion.getYaml());

          List<CatalogEntity> envsUsingBlueprint =
              blueprintToEnvironmentsMap.getOrDefault(catalogEntity.getIdentifier(), Collections.emptyList());

          List<String> envsUsingThisVersion =
              envsUsingBlueprint.stream()
                  .filter(env
                      -> Optional.ofNullable(env.getSpec())
                             .map(spec -> from(spec, "environmentBlueprint", Map.class))
                             .map(envBlueprint -> from(envBlueprint, "version", String.class))
                             .map(version -> catalogEntityVersion.getVersion().equals(version))
                             .orElse(false))
                  .map(CatalogUtils::getEntityRef)
                  .collect(Collectors.toList());

          environmentBluePrintVersionInfo.setReferencedByEnvironmentRefs(envsUsingThisVersion);
          environmentBluePrintVersionInfos.add(environmentBluePrintVersionInfo);
        }
      }
      environmentBluePrintInfoResponse.setVersions(environmentBluePrintVersionInfos);

      List<CatalogEntity> envsUsingBlueprint =
          blueprintToEnvironmentsMap.getOrDefault(catalogEntity.getIdentifier(), Collections.emptyList());
      List<String> envsUsingBlueprintEntityRefs =
          envsUsingBlueprint.stream().map(CatalogUtils::getEntityRef).collect(Collectors.toList());

      environmentBluePrintInfoResponse.setReferencedByEnvironmentsRefs(envsUsingBlueprintEntityRefs);
      environmentBluePrintInfoResponse.setReferencedByEnvironmentsCount(envsUsingBlueprintEntityRefs.size());
      environmentBluePrintInfoResponse.setEnvironmentBlueprintIdentifier(catalogEntity.getIdentifier());
      environmentBluePrintInfoResponses.add(environmentBluePrintInfoResponse);
    }

    return environmentBluePrintInfoResponses;
  }

  @Override
  public GetEntitiesDTO getEnvironmentsByBlueprintIdentifier(String harnessAccount, String orgIdentifier,
      String projectIdentifier, String blueprintIdentifier, Integer page, Integer limit, String sort,
      String searchTerm) {
    try {
      catalogServiceHelper.checkCrudRbac(harnessAccount, orgIdentifier, projectIdentifier, ENVIRONMENT_BLUEPRINT_KIND,
          CatalogUtils.entityRef(ENVIRONMENT_BLUEPRINT_KIND, orgIdentifier, projectIdentifier, blueprintIdentifier),
          "view");

      int pageNumber = (page == null) ? 0 : page;
      int pageLimit = (limit == null) ? 10 : limit;

      String scopes = catalogServiceHelper.getAllScopes();
      Pair<List<ScopeInfo>, Map<String, List<ScopeInfo>>> scopeInfosAndScopeInfosForScopes =
          catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(harnessAccount, scopes, null);
      List<ScopeInfo> scopeInfos = scopeInfosAndScopeInfosForScopes.getLeft();

      // Get permitted environment entity refs based on RBAC (optimized to only check environments)
      List<String> permittedEnvironmentRefs = catalogServiceHelper.checkEntitiesRbacByKind(
          harnessAccount, scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList(), ENVIRONMENT_KIND);

      if (isEmpty(permittedEnvironmentRefs)) {
        return GetEntitiesDTO.builder()
            .entityResponses(Collections.emptyList())
            .totalElements(0L)
            .pageNumber(pageNumber)
            .totalOwned(0L)
            .totalStarred(0L)
            .build();
      }

      Pageable pageable;
      if (!isEmpty(sort)) {
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction =
            sortParams.length > 1 && "DESC".equalsIgnoreCase(sortParams[1]) ? Sort.Direction.DESC : Sort.Direction.ASC;
        pageable = PageRequest.of(pageNumber, pageLimit, Sort.by(direction, sortField));
      } else {
        pageable = PageRequest.of(pageNumber, pageLimit);
      }

      // Construct scoped identifier to match against stored value in environment spec
      String scopedBlueprintIdentifier =
          CommonUtils.getScopedIdentifier(harnessAccount, orgIdentifier, projectIdentifier, blueprintIdentifier);

      // Resolve parentUniqueIds to scope environment results to only those that can reference this blueprint:
      // Account-level blueprint → null (no filter, all project environments can reference it)
      // Org or project-level blueprint → use getUniqueIdsIncludingChildScope to get all relevant parentUniqueIds
      List<String> environmentParentUniqueIds = null;
      if (!isEmpty(orgIdentifier)) {
        ScopeInfo blueprintScopeInfo =
            getResponse(scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
        Set<String> childUniqueIds = getResponse(scopeInfoClient.getUniqueIdsIncludingChildScope(blueprintScopeInfo));
        environmentParentUniqueIds = isEmpty(childUniqueIds) ? new ArrayList<>() : new ArrayList<>(childUniqueIds);
      }

      Page<CatalogEntity> catalogEntitiesPaged =
          catalogEntityRepository.findEnvironmentsByBlueprintIdentifier(harnessAccount, scopedBlueprintIdentifier,
              environmentParentUniqueIds, searchTerm, permittedEnvironmentRefs, scopeInfos, pageable);

      if (isEmpty(catalogEntitiesPaged.getContent())) {
        return GetEntitiesDTO.builder()
            .entityResponses(Collections.emptyList())
            .totalElements(0L)
            .pageNumber(pageNumber)
            .totalOwned(0L)
            .totalStarred(0L)
            .build();
      }

      ScopeInfo accountScopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, null, null));

      List<CatalogEntity> catalogEntitiesPagedContent = catalogEntitiesPaged.getContent();
      Set<String> orgIdentifiers = catalogEntitiesPagedContent.stream()
                                       .map(CatalogEntity::getOrgIdentifier)
                                       .filter(Objects::nonNull)
                                       .collect(Collectors.toSet());

      Map<String, Set<String>> orgProjects = new HashMap<>();
      catalogEntitiesPagedContent.forEach(entity -> {
        if (entity.getOrgIdentifier() != null && entity.getProjectIdentifier() != null) {
          orgProjects.computeIfAbsent(entity.getOrgIdentifier(), k -> new HashSet<>())
              .add(entity.getProjectIdentifier());
        }
      });

      List<OrganizationDTO> organizations = getOrganizations(harnessAccount, orgIdentifiers);
      List<ProjectDTO> projects = getProjectsByOrganization(harnessAccount, orgProjects);

      catalogEntitiesPagedContent =
          catalogServiceHelper.resolveOwner(accountScopeInfo.getUniqueId(), catalogEntitiesPagedContent);

      KindEntity kindEntity = kindServiceHelper.kindEntity(harnessAccount, ENVIRONMENT_KIND);
      List<EntityResponse> entityResponses = new ArrayList<>();
      for (CatalogEntity catalogEntity : catalogEntitiesPagedContent) {
        String orgName = !isEmpty(catalogEntity.getOrgIdentifier())
            ? organizations.stream()
                  .filter(org -> org.getIdentifier().equals(catalogEntity.getOrgIdentifier()))
                  .map(OrganizationDTO::getName)
                  .findFirst()
                  .orElse(null)
            : null;
        String projName = !isEmpty(catalogEntity.getOrgIdentifier()) && !isEmpty(catalogEntity.getProjectIdentifier())
            ? projects.stream()
                  .filter(project
                      -> project.getOrgIdentifier().equals(catalogEntity.getOrgIdentifier())
                          && project.getIdentifier().equals(catalogEntity.getProjectIdentifier()))
                  .map(ProjectDTO::getName)
                  .findFirst()
                  .orElse(null)
            : null;

        EntityResponse entityResponse = CatalogMapper.entityToResponse(
            catalogEntity, orgName, projName, null, kindEntity.getIcon(), new EntityResponseScorecards(), false);
        entityResponse.setGitDetails(idpGitXHelper.getEntityDetails(catalogEntity));
        entityResponses.add(entityResponse);
      }

      return GetEntitiesDTO.builder()
          .pageNumber(catalogEntitiesPaged.getNumber())
          .totalElements(catalogEntitiesPaged.getTotalElements())
          .entityResponses(entityResponses)
          .totalOwned(0L)
          .totalStarred(0L)
          .build();
    } catch (Exception e) {
      log.error("Error fetching environments for blueprint: {}", blueprintIdentifier, e);
      throw new InvalidRequestException(
          "Failed to fetch environments for blueprint: " + blueprintIdentifier + ". Error: " + e.getMessage());
    }
  }

  @Override
  public GetEntitiesDTO getEntityAssociations(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String kind, String identifier, String relations, Integer page, Integer limit, String sort, String searchTerm,
      Boolean ownedByMe, Boolean favorites, String associationKind, String type, String owner, String lifecycle,
      String tags, String filter) {
    try {
      kind = catalogServiceHelper.validateAndSanitizeKind(kind);
      if (isEmpty(relations)) {
        throw new InvalidRequestException("relations parameter is required");
      }

      CatalogEntity entity = getCatalogEntityByParentUniqueIdAndKindAndIdentifier(
          harnessAccount, orgIdentifier, projectIdentifier, kind, identifier);

      catalogServiceHelper.checkRbacWithOwnerFallback(
          harnessAccount, CatalogUtils.entityRef(entity), entity.getOwner(), "view");

      int pageNumber = (page == null) ? 0 : page;
      int pageLimit = (limit == null) ? 10 : limit;

      // Collect entity refs from all requested relation types
      List<String> relationTypes = Arrays.asList(relations.split(","));
      Set<String> associatedRefs = new HashSet<>();
      if (entity.getRelations() != null) {
        for (String relationType : relationTypes) {
          Set<String> refs = entity.getRelations().getOrDefault(relationType.trim(), Collections.emptySet());
          associatedRefs.addAll(refs);
        }
      }
      if (isEmpty(associatedRefs)) {
        return GetEntitiesDTO.builder()
            .entityResponses(Collections.emptyList())
            .totalElements(0L)
            .pageNumber(pageNumber)
            .totalOwned(0L)
            .totalStarred(0L)
            .build();
      }

      String associatedRefsString = String.join(",", associatedRefs);
      Pair<List<ScopeInfo>, Map<String, List<ScopeInfo>>> scopeInfosAndScopeInfosForScopes =
          catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(harnessAccount, null, associatedRefsString);
      List<ScopeInfo> scopeInfos = scopeInfosAndScopeInfosForScopes.getLeft();

      // Get permitted entity refs based on RBAC and intersect with associated refs
      List<String> permittedEntityRefs = catalogServiceHelper.checkEntitiesRbac(
          harnessAccount, scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList());

      Set<String> permittedSet = new HashSet<>(permittedEntityRefs);

      List<String> inheritableAssociatedKinds =
          associatedRefs.stream()
              .map(ref -> catalogServiceHelper.getKindScopeIdentifier(ref).getLeft())
              .filter(catalogServiceHelper::isInheritableKind)
              .distinct()
              .toList();
      boolean hasUnresolvedRefs = associatedRefs.stream().anyMatch(ref -> !permittedSet.contains(ref));
      if (hasUnresolvedRefs && !inheritableAssociatedKinds.isEmpty()) {
        Set<String> uniqueScopesForGroups = catalogServiceHelper.uniqueParentScopesForGroups(scopeInfos);
        List<ScopeInfo> scopeInfosForGroups =
            catalogServiceHelper
                .getScopeInfosBasedOnScopesAndEntityRefs(harnessAccount, String.join(",", uniqueScopesForGroups), null)
                .getLeft();
        List<String> permittedGroupEntityRefs = catalogServiceHelper.checkEntitiesRbacByKind(
            harnessAccount, scopeInfosForGroups.stream().map(ScopeInfo::getUniqueId).distinct().toList(), GROUP_KIND);
        List<String> associatedRefsOwnedByGroups =
            catalogEntityRepository
                .findAllByParentUniqueIdInAndKindInAndOwnerIn(scopeInfos.stream().map(ScopeInfo::getUniqueId).toList(),
                    inheritableAssociatedKinds, permittedGroupEntityRefs)
                .stream()
                .map(CatalogUtils::entityRef)
                .toList();
        permittedSet.addAll(associatedRefsOwnedByGroups);
      }

      List<String> allowedRefs = associatedRefs.stream().filter(permittedSet::contains).toList();

      if (isEmpty(allowedRefs)) {
        return GetEntitiesDTO.builder()
            .entityResponses(Collections.emptyList())
            .totalElements(0L)
            .pageNumber(pageNumber)
            .totalOwned(0L)
            .totalStarred(0L)
            .build();
      }

      ScopeInfo accountScopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, null, null));

      boolean ownedByMeFilter = ownedByMe != null && ownedByMe;
      if (ownedByMeFilter) {
        owner = catalogServiceHelper.getOwnedByMe(accountScopeInfo.getUniqueId(), owner);
      }

      String userFavoriteEntityRefs =
          catalogServiceHelper.getUserFavoriteEntityRefs(harnessAccount, null, null, IDPENTITY.name());

      boolean favoritesFilter = favorites != null && favorites;
      if (favoritesFilter) {
        if (isEmpty(userFavoriteEntityRefs)) {
          return GetEntitiesDTO.builder()
              .entityResponses(Collections.emptyList())
              .totalElements(0L)
              .pageNumber(pageNumber)
              .totalOwned(0L)
              .totalStarred(0L)
              .build();
        }
        Set<String> favoriteSet = new HashSet<>(Arrays.asList(userFavoriteEntityRefs.split(",")));
        allowedRefs = allowedRefs.stream().filter(favoriteSet::contains).toList();
        if (isEmpty(allowedRefs)) {
          return GetEntitiesDTO.builder()
              .entityResponses(Collections.emptyList())
              .totalElements(0L)
              .pageNumber(pageNumber)
              .totalOwned(0L)
              .totalStarred(0L)
              .build();
        }
      }

      // Compute totalStarred count from intersection of allowedRefs and user favorites
      long totalStarred = 0L;
      if (!isEmpty(userFavoriteEntityRefs)) {
        Set<String> favoriteSet = new HashSet<>(Arrays.asList(userFavoriteEntityRefs.split(",")));
        totalStarred = allowedRefs.stream().filter(favoriteSet::contains).count();
      }

      // Compute totalOwned count from intersection of allowedRefs and owned entities
      String ownedByMeCriteriaForTotalCount = catalogServiceHelper.getOwnedByMe(accountScopeInfo.getUniqueId(), null);
      long totalOwned = 0L;
      if (!isEmpty(ownedByMeCriteriaForTotalCount)) {
        totalOwned = catalogEntityRepository.getOwnedEntitiesCount(
            scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList(), null, ownedByMeCriteriaForTotalCount,
            harnessAccount, scopeInfos, String.join(",", allowedRefs), true, null);
      }

      Pageable pageable;
      if (!isEmpty(sort)) {
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction =
            sortParams.length > 1 && "DESC".equalsIgnoreCase(sortParams[1]) ? Sort.Direction.DESC : Sort.Direction.ASC;
        pageable = PageRequest.of(pageNumber, pageLimit, Sort.by(direction, sortField));
      } else {
        pageable = PageRequest.of(pageNumber, pageLimit);
      }

      Page<CatalogEntity> catalogEntitiesPaged = catalogEntityRepository.findEntitiesByRelationRefs(harnessAccount,
          allowedRefs, searchTerm, scopeInfos, pageable, associationKind, type, owner, lifecycle, tags, filter);

      if (isEmpty(catalogEntitiesPaged.getContent())) {
        return GetEntitiesDTO.builder()
            .entityResponses(Collections.emptyList())
            .totalElements(0L)
            .pageNumber(pageNumber)
            .totalOwned(totalOwned)
            .totalStarred(totalStarred)
            .build();
      }

      List<CatalogEntity> catalogEntitiesPagedContent = catalogEntitiesPaged.getContent();
      Set<String> orgIdentifiers = catalogEntitiesPagedContent.stream()
                                       .map(CatalogEntity::getOrgIdentifier)
                                       .filter(Objects::nonNull)
                                       .collect(Collectors.toSet());

      Map<String, Set<String>> orgProjects = new HashMap<>();
      catalogEntitiesPagedContent.forEach(catalogEnt -> {
        if (catalogEnt.getOrgIdentifier() != null && catalogEnt.getProjectIdentifier() != null) {
          orgProjects.computeIfAbsent(catalogEnt.getOrgIdentifier(), k -> new HashSet<>())
              .add(catalogEnt.getProjectIdentifier());
        }
      });

      List<OrganizationDTO> organizations = getOrganizations(harnessAccount, orgIdentifiers);
      List<ProjectDTO> projects = getProjectsByOrganization(harnessAccount, orgProjects);

      catalogEntitiesPagedContent =
          catalogServiceHelper.resolveOwner(accountScopeInfo.getUniqueId(), catalogEntitiesPagedContent);

      Map<String, KindEntity> kindEntityMap =
          kindServiceHelper.findByAccountIdentifierIn(harnessAccount)
              .stream()
              .collect(Collectors.toMap(KindEntity::getIdentifier, kindEntity -> kindEntity));

      boolean hasCoreKind =
          catalogEntitiesPagedContent.stream().map(CatalogEntity::getKind).anyMatch(CORE_KINDS::contains);
      Map<String, List<ScoreEntity>> entityScores = new HashMap<>();
      Map<String, String> scorecardIdToNameMap = new HashMap<>();

      if (!isEmpty(catalogEntitiesPagedContent)
          && (hasCoreKind
              || catalogEntitiesPagedContent.stream().map(CatalogEntity::getKind).anyMatch(GROUP_KIND::equals))) {
        try {
          ScopeTopology topology = catalogScopeResolver.getOrBuildTopology(harnessAccount);
          List<ScorecardAndChecks> scorecardAndChecks = scorecardService.getAllScorecardAndChecks(harnessAccount, null);
          entityScores = scorecardScoreHelper.fetchScoresForEntities(
              harnessAccount, catalogEntitiesPagedContent, scorecardAndChecks, topology);
          scorecardIdToNameMap.putAll(scorecardAndChecks.stream().collect(Collectors.toMap(scorecard
              -> scorecard.getScorecard().getIdentifier(),
              scorecard -> scorecard.getScorecard().getName(), (a, b) -> a)));
        } catch (Exception ex) {
          log.warn("Error enriching associations with scorecards for account={}. Error={}", harnessAccount,
              ex.getMessage(), ex);
          entityScores = new HashMap<>();
          scorecardIdToNameMap = new HashMap<>();
        }
      }

      List<EntityResponse> entityResponses = new ArrayList<>();
      for (CatalogEntity catalogEntity : catalogEntitiesPagedContent) {
        String orgName = !isEmpty(catalogEntity.getOrgIdentifier())
            ? organizations.stream()
                  .filter(org -> org.getIdentifier().equals(catalogEntity.getOrgIdentifier()))
                  .map(OrganizationDTO::getName)
                  .findFirst()
                  .orElse(null)
            : null;
        String projName = !isEmpty(catalogEntity.getOrgIdentifier()) && !isEmpty(catalogEntity.getProjectIdentifier())
            ? projects.stream()
                  .filter(project
                      -> project.getOrgIdentifier().equals(catalogEntity.getOrgIdentifier())
                          && project.getIdentifier().equals(catalogEntity.getProjectIdentifier()))
                  .map(ProjectDTO::getName)
                  .findFirst()
                  .orElse(null)
            : null;

        List<ScoreEntity> scoreEntities = entityScores.get(CatalogUtils.entityRef(catalogEntity));
        EntityResponseScorecards scorecards = isEmpty(scoreEntities)
            ? new EntityResponseScorecards()
            : constructEntityScorecards(scoreEntities, scorecardIdToNameMap);
        EntityResponse entityResponse = CatalogMapper.entityToResponse(catalogEntity, orgName, projName,
            userFavoriteEntityRefs,
            kindEntityMap.containsKey(catalogEntity.getKind()) ? kindEntityMap.get(catalogEntity.getKind()).getIcon()
                                                               : null,
            scorecards, false);
        entityResponse.setGitDetails(idpGitXHelper.getEntityDetails(catalogEntity));
        entityResponses.add(entityResponse);
      }

      return GetEntitiesDTO.builder()
          .pageNumber(catalogEntitiesPaged.getNumber())
          .totalElements(catalogEntitiesPaged.getTotalElements())
          .entityResponses(entityResponses)
          .totalOwned(totalOwned)
          .totalStarred(totalStarred)
          .build();
    } catch (Exception e) {
      log.error("Error fetching associations for kind: {}, identifier: {}", kind, identifier, e);
      throw new InvalidRequestException(
          "Failed to fetch associations for " + kind + ": " + identifier + ". Error: " + e.getMessage());
    }
  }

  @Override
  public void projectMovement(ProjectEntityChangeDTO projectEntityChangeDTO) {
    String accountIdentifier = projectEntityChangeDTO.getAccountIdentifier();
    String oldOrgIdentifier = projectEntityChangeDTO.getOldOrgIdentifier();
    String newOrgIdentifier = projectEntityChangeDTO.getOrgIdentifier();
    String projectIdentifier = projectEntityChangeDTO.getIdentifier();

    ScopeInfo scopeInfo = catalogScopeResolver.resolveSingleScopeInfo(
        accountIdentifier, CatalogUtils.getScope(newOrgIdentifier, projectIdentifier));
    projectMovementCore(accountIdentifier, oldOrgIdentifier, newOrgIdentifier, projectIdentifier, scopeInfo);
    projectMovementDependents(accountIdentifier, oldOrgIdentifier, newOrgIdentifier, projectIdentifier, scopeInfo);
  }

  @Override
  public Response getWorkflowExecutionHistory(String accountIdentifier, List<String> entityRefs, boolean executedByMe,
      List<String> status, Long startTime, Long endTime, String searchTerm, String sort, int page, int size) {
    String scopes = catalogServiceHelper.getAllScopes();
    Pair<List<ScopeInfo>, Map<String, List<ScopeInfo>>> scopeInfosAndScopeInfosForScopes =
        catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(accountIdentifier, scopes, null);
    List<ScopeInfo> scopeInfos = scopeInfosAndScopeInfosForScopes.getLeft();
    List<String> permittedEntityRefs = catalogServiceHelper.checkEntitiesRbac(
        accountIdentifier, scopeInfos.stream().map(ScopeInfo::getUniqueId).distinct().toList());
    permittedEntityRefs = permittedEntityRefs.stream().filter(entityRef -> entityRef.startsWith("workflow:")).toList();

    if (!isEmpty(entityRefs)) {
      entityRefs.retainAll(permittedEntityRefs);
    }

    Page<BackstageScaffolderTaskEntity> scaffolderTaskEntityPage =
        backstageScaffolderTaskEntityRepository.findExecutionHistory(
            accountIdentifier, entityRefs, status, page, size, sort, startTime, endTime, searchTerm, executedByMe);
    List<WorkflowExecutionHistoryResponse> responses = new ArrayList<>();
    for (BackstageScaffolderTaskEntity entity : scaffolderTaskEntityPage.getContent()) {
      WorkflowExecutionHistoryResponse response = new WorkflowExecutionHistoryResponse();
      response.setExecutionId(entity.getIdentifier());
      response.setExecutedAt(entity.getTaskCreatedAt());
      response.setStatus(entity.getStatus());
      response.setWorkflowName(entity.getName());
      try {
        JsonNode spec = objectMapper.readTree(entity.getSpec());
        if (spec.get("gitDetails") != null) {
          response.setSource("Git");
          response.setBranchName(spec.get("gitDetails").get("branch_name").asText());
        } else {
          response.setSource("Inline");
        }
      } catch (Exception e) {
        response.setSource("Inline");
      }
      String[] entityRefSplit = entity.getEntityRef().split(":");
      String scope = null;
      if (entityRefSplit.length == 2) {
        String[] hierarchyScope = entityRefSplit[1].split("\\.");
        if (hierarchyScope.length == 3) {
          scope = io.harness.idp.catalog.beans.Scope.PROJECT.name();
        } else if (hierarchyScope.length == 2) {
          scope = io.harness.idp.catalog.beans.Scope.ORGANIZATION.name();
        } else {
          scope = io.harness.idp.catalog.beans.Scope.ACCOUNT.name();
        }
      }
      response.setScope(scope);
      if (!isEmpty(entity.getTaskCreatedBy()) && entity.getTaskCreatedBy().contains("/")) {
        response.setExecutedBy(entity.getTaskCreatedBy().split("/")[1]);
      } else {
        response.setExecutedBy("unknown");
      }
      responses.add(response);
    }
    return idpCommonService.buildPageResponse(page, size, scaffolderTaskEntityPage.getTotalElements(), responses);
  }

  // Supporting list of Catalog Entities as we can reuse the same function for bulk operations
  private void syncInternal(String accountIdentifier, String entityUid, String action,
      List<Map<String, Object>> filteredCatalogEntities, Boolean creatingEntity) {
    if (filteredCatalogEntities.isEmpty()) {
      return;
    }
    log.info("Fetched {} catalog entities in IdpCatalogEntitiesAsHarnessEntities sync for accountIdentifier = {} "
            + "EntityUid = {} Action = {}",
        filteredCatalogEntities.size(), accountIdentifier, entityUid, action);

    CatalogSyncRequest.ActionEnum actionEnum = CatalogSyncRequest.ActionEnum.fromValue(action);

    ScopeInfo scopeInfo =
        catalogScopeResolver.resolveSingleScopeInfo(accountIdentifier, CatalogUtils.getScope(null, null));

    Optional<CatalogEntity> oldCatalogEntityOptional = catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
        scopeInfo.getUniqueId(), CatalogUtils.getBackstageCatalogKindFromEntityUid(entityUid),
        CatalogUtils.getBackstageCatalogNameFromEntityUid(entityUid));

    Set<String> users = listOfUsers(filteredCatalogEntities);
    List<UserMetadataDTO> userMetadataDTOS = new ArrayList<>();
    for (String user : users) {
      userMetadataDTOS.addAll(
          idpToHarnessHelper.getUsers(accountIdentifier, UserFilter.builder().searchTerm(user).build()));
    }
    Map<String, String> usernameAndEmailMapping = userMetadataDTOS.stream().collect(Collectors.toMap(userMetadataDTO
        -> userMetadataDTO.getEmail().split("@")[0],
        UserMetadataDTO::getEmail, (existing, replacement) -> existing));

    transactionHelper.performTransaction(() -> {
      List<InlineCatalogEntity> entitiesToSave = new ArrayList<>();
      for (Map<String, Object> filteredBackstageCatalogEntity : filteredCatalogEntities) {
        InlineCatalogEntity createdOrUpdatedEntity =
            idpToHarnessHelper.getInlineEntityForApiOrComponentOrResourceOrTemplate(
                accountIdentifier, filteredBackstageCatalogEntity, scopeInfo, creatingEntity, usernameAndEmailMapping);

        if (actionEnum.equals(CatalogSyncRequest.ActionEnum.CREATE)) {
          entitiesToSave.add(createdOrUpdatedEntity);

        } else if (actionEnum.equals(CatalogSyncRequest.ActionEnum.UPDATE)) {
          if (!creatingEntity && oldCatalogEntityOptional.isPresent()) {
            createdOrUpdatedEntity = fetchUpdatedEntity(accountIdentifier, entityUid, oldCatalogEntityOptional.get(),
                createdOrUpdatedEntity, scopeInfo, usernameAndEmailMapping);
            createdOrUpdatedEntity.setId(oldCatalogEntityOptional.get().getId());
            createdOrUpdatedEntity.setUniqueId(oldCatalogEntityOptional.get().getUniqueId());
            entitiesToSave.add(createdOrUpdatedEntity);
          }
        } else {
          // upsert case from migration.
          entitiesToSave.add(createdOrUpdatedEntity);
        }
      }
      entitiesToSave.forEach(entity -> entity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(entity)));
      catalogEntityRepository.saveAll(entitiesToSave);
      return true;
    });

    log.info("Saved {} catalog entities into DB in IdpCatalogEntitiesAsHarnessEntities sync for accountIdentifier = {} "
            + "EntityUid = {} Action = {}",
        filteredCatalogEntities.size(), accountIdentifier, entityUid, action);
    log.info("Synced IDP catalog entities as Harness entities for accountIdentifier = {} EntityUid = {} Action = {}",
        accountIdentifier, entityUid, action);
  }

  private InlineCatalogEntity fetchUpdatedEntity(String accountIdentifier, String entityUid,
      CatalogEntity oldCatalogEntity, InlineCatalogEntity newCatalogEntity, ScopeInfo scopeInfo,
      Map<String, String> usernameAndEmailMapping) {
    Object response = null;
    int count = 0;
    InlineCatalogEntity updatedCatalogEntity = newCatalogEntity;
    log.debug("Starting to fetch updated entity");
    while (updatedCatalogEntity.getYaml().equals(oldCatalogEntity.getYaml()) && count < 5) {
      try {
        log.debug("Starting to fetch updated entity count - {}", count);
        Thread.sleep(2000);
        response = getGeneralResponse(backstageResourceClient.getCatalogEntityByName(
            accountIdentifier, getEntityUniqueIdForByNameAPI(entityUid)));
      } catch (Exception ex) {
        log.warn("Error in fetching catalog entity by name for account = {} entityUid = {} Error = {}",
            accountIdentifier, entityUid, ex.getMessage(), ex);
      }
      updatedCatalogEntity = idpToHarnessHelper.getInlineEntityForApiOrComponentOrResourceOrTemplate(
          accountIdentifier, (Map<String, Object>) response, scopeInfo, false, usernameAndEmailMapping);
      count++;
    }
    log.debug("Fetching completed, yaml - {} ", updatedCatalogEntity.getYaml());

    if (updatedCatalogEntity.getYaml().equals(oldCatalogEntity.getYaml())) {
      log.debug("Unable to fetch the updated catalog yaml for account - {}, entityUid - {} after 10 secs",
          accountIdentifier, entityUid);
      return newCatalogEntity;
    }
    return updatedCatalogEntity;
  }

  public void updateUserCatalogEntity(String accountIdentifier, UserMembershipDTO userMembershipDTO, String action) {
    Boolean creatingEntity = action.equals(CREATE_ACTION) ? true : false;
    final CatalogEntity[] redisCatalog = new CatalogEntity[1];
    Boolean isIDPV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    transactionHelper.performTransaction(() -> {
      redisCatalog[0] = idpToHarnessHelper.updateUser(accountIdentifier, userMembershipDTO, creatingEntity);
      if (isIDPV2Enabled) {
        harnessToIDPHelper.harnessToIdpSync(new ArrayList<>(List.of(redisCatalog[0])), accountIdentifier, action);
      }
      return null;
    });
    if (isIDPV2Enabled) {
      idpToHarnessHelper.sendCatalogEventsToRedis(Collections.singletonList(redisCatalog[0]), action);
    }
  }

  public void deleteUserCatalogEntity(String accountIdentifier, UserMembershipDTO userMembershipDTO) {
    Optional<CatalogEntity> catalogEntity =
        catalogEntityRepository.findUserBasedOnAccountIdAndUUID(accountIdentifier, userMembershipDTO.getUserId());
    Boolean isIDPV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    if (catalogEntity.isPresent() && namespaceService.getAccountIdpStatus(accountIdentifier)) {
      transactionHelper.performTransaction(() -> {
        catalogEntityRepository.delete(catalogEntity.get());
        if (isIDPV2Enabled) {
          harnessToIDPHelper.harnessToIdpSync(
              List.of(catalogEntity.get()), catalogEntity.get().getAccountIdentifier(), DELETE_ACTION);
        }
        return null;
      });
      if (isIDPV2Enabled) {
        idpToHarnessHelper.sendCatalogEventsToRedis(Collections.singletonList(catalogEntity.get()), DELETE_ACTION);
      }
    }
  }

  public void updateUserGroupCatalogEntity(String accountIdentifier, String userGroupIdentifier, String action) {
    idpToHarnessHelper.updateUserGroup(accountIdentifier, userGroupIdentifier, action);
  }

  public void deleteUserGroupCatalogEntity(String accountIdentifier, String userGroupIdentifier) {
    ScopeInfo scopeInfo =
        catalogScopeResolver.resolveSingleScopeInfo(accountIdentifier, CatalogUtils.getScope(null, null));
    Optional<CatalogEntity> userGroupCatalogEntity = catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
        scopeInfo.getUniqueId(), GROUP_KIND, userGroupIdentifier);
    if (userGroupCatalogEntity.isPresent()
        && catalogServiceHelper.checkIfCustomUserGroup(userGroupCatalogEntity.get())) {
      log.warn("Skipping event processing since platform user group with the same identifier is already existing has "
          + "custom user group");
      return;
    }
    List<CatalogEntity> usersCatalogEntities =
        idpToHarnessHelper.removeRelationsForUsers(accountIdentifier, userGroupIdentifier);
    Boolean isIDPV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    transactionHelper.performTransaction(() -> {
      usersCatalogEntities.forEach(
          catalogEntity -> catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity)));
      catalogEntityRepository.saveAll(usersCatalogEntities);
      if (userGroupCatalogEntity.isPresent()) {
        if (isIDPV2Enabled) {
          harnessToIDPHelper.harnessToIdpSync(List.of(userGroupCatalogEntity.get()), accountIdentifier, DELETE_ACTION);
        }
        catalogEntityRepository.delete(userGroupCatalogEntity.get());
      }
      return null;
    });
    if (isIDPV2Enabled) {
      idpToHarnessHelper.sendCatalogEventsToRedis(usersCatalogEntities, UPDATE_ACTION);
      idpToHarnessHelper.sendCatalogEventsToRedis(
          Collections.singletonList(userGroupCatalogEntity.get()), DELETE_ACTION);
    }
  }

  private Set<String> listOfUsers(List<Map<String, Object>> filteredCatalogEntities) {
    Set<String> users = new HashSet<>();
    for (Map<String, Object> filteredCatalogEntity : filteredCatalogEntities) {
      List<Map<String, Object>> relations = from(filteredCatalogEntity, "relations", List.class);
      for (Map<String, Object> relation : relations) {
        Map<String, String> target = (Map<String, String>) relation.get("target");
        if (target.get("kind").equalsIgnoreCase("user")) {
          users.add(target.get("name").replaceAll("plus", "+"));
        }
      }
    }
    return users;
  }

  private void createOutboxUpdateEventForReferencedEntities(List<CatalogEntity> referencedEntities) {
    if (!isEmpty(referencedEntities)) {
      Map<String, CatalogEntity> referencedEntityMap = referencedEntities.stream().collect(Collectors.toMap(
          CatalogEntity::getUniqueId, catalogEntity -> catalogEntity, (existing, duplicate) -> existing));

      List<String> referencedEntitiesUniqueIds = referencedEntities.stream()
                                                     .map(CatalogEntity::getUniqueId) // Extract uniqueId
                                                     .distinct()
                                                     .collect(Collectors.toList());

      // Here we have unique index on UniqueId but we are using account identifier also just for unifromity, based on
      // suggestions we can query based only on uniqueIds
      List<CatalogEntity> oldReferencedEntities = catalogEntityRepository.findByUniqueIdIn(referencedEntitiesUniqueIds);

      Map<String, CatalogEntity> oldReferencedEntityMap = oldReferencedEntities.stream().collect(Collectors.toMap(
          CatalogEntity::getUniqueId, catalogEntity -> catalogEntity, (existing, duplicate) -> existing));

      for (String uniqueId : referencedEntitiesUniqueIds) {
        ScopeInfo scopeInfo = ScopeInfo.builder()
                                  .accountIdentifier(referencedEntityMap.get(uniqueId).getAccountIdentifier())
                                  .orgIdentifier(referencedEntityMap.get(uniqueId).getOrgIdentifier())
                                  .projectIdentifier(referencedEntityMap.get(uniqueId).getProjectIdentifier())
                                  .uniqueId(referencedEntityMap.get(uniqueId).getParentUniqueId())
                                  .scopeType(ScopeLevel.valueOf(referencedEntityMap.get(uniqueId).getScope()))
                                  .build();

        outboxService.save(new CatalogUpdateEvent(scopeInfo, referencedEntityMap.get(uniqueId).getYaml(),
            oldReferencedEntityMap.get(uniqueId).getYaml(), referencedEntityMap.get(uniqueId).getKind(),
            referencedEntityMap.get(uniqueId).getIdentifier()));
      }
    }
  }

  private List<OrganizationDTO> getOrganizations(String accountIdentifier, Set<String> identifiers) {
    List<OrganizationDTO> organizationDTOS = new ArrayList<>();
    if (identifiers.isEmpty()) {
      return organizationDTOS;
    }
    PageResponse<OrganizationResponse> organizations;
    int page = 0;
    final int pageSize = 1000;
    while (true) {
      organizations = getResponse(organizationClient.listOrganization(
          accountIdentifier, identifiers.stream().toList(), null, page, pageSize, null));
      if (organizations == null || isEmpty(organizations.getContent())) {
        break;
      }
      organizationDTOS.addAll(organizations.getContent().stream().map(OrganizationResponse::getOrganization).toList());
      if (organizations.getContent().size() < pageSize) {
        break;
      }
      page++;
    }
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
        int page = 0;
        final int pageSize = 1000;
        while (true) {
          PageResponse<ProjectResponse> projects = getResponse(projectClient.listProjects(
              accountIdentifier, org, new ArrayList<>(projectIdentifier.getValue()), page, pageSize));
          if (projects == null || isEmpty(projects.getContent())) {
            break;
          }
          projectDTOS.addAll(projects.getContent().stream().map(ProjectResponse::getProject).toList());
          if (projects.getContent().size() < pageSize) {
            break;
          }
          page++;
        }
      }
    }
    return projectDTOS;
  }

  private EntityResponseScorecards constructEntityScorecards(
      List<ScoreEntity> scoreEntities, Map<String, String> scorecardIdToNameMap) {
    if (isEmpty(scoreEntities)) {
      return new EntityResponseScorecards();
    }

    List<EntityResponseScorecardsScores> scores =
        scoreEntities.stream()
            .filter(Objects::nonNull)
            .map(scoreEntity -> {
              EntityResponseScorecardsScores entityResponseScorecardsScores = new EntityResponseScorecardsScores();
              entityResponseScorecardsScores.setScorecard(scoreEntity.getScorecardIdentifier());
              entityResponseScorecardsScores.setScorecardName(scorecardIdToNameMap.getOrDefault(
                  scoreEntity.getScorecardIdentifier(), scoreEntity.getScorecardIdentifier()));
              entityResponseScorecardsScores.setScore(BigDecimal.valueOf(scoreEntity.getScore()));
              entityResponseScorecardsScores.setTotalChecks(BigDecimal.valueOf(
                  Optional.ofNullable(scoreEntity.getCheckStatus()).orElse(Collections.emptyList()).size()));
              entityResponseScorecardsScores.setPassedChecks(BigDecimal.valueOf(
                  Optional.ofNullable(scoreEntity.getCheckStatus())
                      .orElse(Collections.emptyList())
                      .stream()
                      .filter(checkStatus -> checkStatus.getStatus().equals(CheckStatus.StatusEnum.PASS))
                      .toList()
                      .size()));
              ScoreTierMapper.fromScoreEntity(scoreEntity).ifPresent(entityResponseScorecardsScores::setTier);
              return entityResponseScorecardsScores;
            })
            .collect(Collectors.toList());
    EntityResponseScorecards scorecards = new EntityResponseScorecards();
    scorecards.setScores(scores);
    if (!scores.isEmpty()) {
      double averageScore =
          scoreEntities.stream().filter(Objects::nonNull).mapToInt(ScoreEntity::getScore).average().orElse(0);
      scorecards.setAverage(BigDecimal.valueOf(averageScore).setScale(0, RoundingMode.HALF_UP));
    }
    return scorecards;
  }

  private EntityValidateResponse constructEntityValidateResponseException(CatalogEntity catalogEntity, Exception e) {
    EntityValidateResponse entityValidateResponse = new EntityValidateResponse();
    entityValidateResponse.setIsValid(false);
    EntityValidateResponseEntityMetadata entityMetadata = new EntityValidateResponseEntityMetadata();
    entityMetadata.setIdentifier(catalogEntity.getIdentifier());
    entityMetadata.setEntityRef(CatalogUtils.entityRef(catalogEntity));
    EntityValidateResponseEntityMetadataScope scope = new EntityValidateResponseEntityMetadataScope();
    scope.setAccountIdentifier(catalogEntity.getAccountIdentifier());
    scope.setOrgIdentifier(catalogEntity.getOrgIdentifier());
    scope.setProjectIdentifier(catalogEntity.getProjectIdentifier());
    entityMetadata.setScope(scope);
    entityValidateResponse.setEntityMetadata(entityMetadata);
    EntityValidateResponseValidationErrorMetadata validationErrorMetadata =
        new EntityValidateResponseValidationErrorMetadata();
    validationErrorMetadata.setErrorMessage(e.getMessage());
    entityValidateResponse.setValidationErrorMetadata(validationErrorMetadata);
    return entityValidateResponse;
  }

  private void populateEntityStatusForSourceLocationConnector(
      CatalogEntity catalogEntity, String sourceUrlConnectorError) {
    if (sourceUrlConnectorError != null) {
      List<Map<String, String>> statuses =
          !isEmpty(catalogEntity.getStatus()) ? catalogEntity.getStatus() : new ArrayList<>();
      Map<String, String> status = new HashMap<>();
      status.put("type", "source location error");
      status.put("level", "error");
      status.put("message", sourceUrlConnectorError);
      catalogEntity.setStatus(statuses);
    }
  }

  private EntityValidateResponse constructEntityValidateResponseForEntityNotFound(
      YamlValidationRequestDTO yamlValidationRequestDTO) {
    EntityValidateResponse entityValidateResponse = new EntityValidateResponse();
    entityValidateResponse.setIsValid(false);
    EntityValidateResponseValidationErrorMetadata validationErrorMetadata =
        new EntityValidateResponseValidationErrorMetadata();
    validationErrorMetadata.setHint(String.format("Please check if there exist any entity with the file path [%s] in "
            + "repo name [%s] and branch [%s] in Harness.",
        yamlValidationRequestDTO.getFilePath(), yamlValidationRequestDTO.getRepoName(),
        yamlValidationRequestDTO.getBranch()));
    entityValidateResponse.setValidationErrorMetadata(validationErrorMetadata);
    return entityValidateResponse;
  }

  private EntityResponseStoDetails constructSTODetails(CatalogEntity catalogEntity) {
    Map<String, List<Pair<String, String>>> testTargetsMap = stoHelper.getSTOTestTargets(catalogEntity);
    if (isEmpty(testTargetsMap)) {
      return null;
    }
    EntityResponseStoDetails entityResponseStoDetails = new EntityResponseStoDetails();
    Map<String, Set<String>> projectIdsByOrg = new HashMap<>();
    for (Map.Entry<String, List<Pair<String, String>>> entry : testTargetsMap.entrySet()) {
      String[] scopeSplit = entry.getKey().split("\\.");
      projectIdsByOrg.computeIfAbsent(scopeSplit[0], k -> new HashSet<>()).add(scopeSplit[1]);
    }
    Set<String> orgIdentifiers =
        projectIdsByOrg.keySet().stream().filter(key -> !isEmpty(key)).collect(Collectors.toSet());
    Map<String, String> orgIdAndName =
        catalogOrgProjectService.getOrgNames(catalogEntity.getAccountIdentifier(), orgIdentifiers);
    Map<String, String> projectNameMap =
        catalogOrgProjectService.getProjectNames(catalogEntity.getAccountIdentifier(), orgIdentifiers, projectIdsByOrg);
    Map<String, String> orgProjectIdToName = new HashMap<>();
    for (Map.Entry<String, String> entry : projectNameMap.entrySet()) {
      String key = entry.getKey();
      String[] scopeSplit = key.split(":");
      orgProjectIdToName.put(
          key.replace(":", "."), orgIdAndName.getOrDefault(scopeSplit[0], StringUtils.EMPTY) + "." + entry.getValue());
    }

    List<EntityResponseStoDetailsTestTargets> testTargets = new ArrayList<>();
    for (Map.Entry<String, List<Pair<String, String>>> entry : testTargetsMap.entrySet()) {
      String orgProjectName = orgProjectIdToName.get(entry.getKey());
      if (!isEmpty(orgProjectName)) {
        String[] scopeSplit = orgProjectName.split("\\.");
        String orgName = scopeSplit[0];
        String projectName = scopeSplit[1];
        for (Pair<String, String> pair : entry.getValue()) {
          EntityResponseStoDetailsTestTargets entityResponseStoDetailsTestTargets =
              new EntityResponseStoDetailsTestTargets();
          entityResponseStoDetailsTestTargets.setScope(entry.getKey());
          entityResponseStoDetailsTestTargets.setName(pair.getLeft());
          entityResponseStoDetailsTestTargets.setVariant(pair.getRight());
          entityResponseStoDetailsTestTargets.setOrgName(orgName);
          entityResponseStoDetailsTestTargets.setProjectName(projectName);
          testTargets.add(entityResponseStoDetailsTestTargets);
        }
      }
    }
    entityResponseStoDetails.setTestTargets(testTargets);
    return entityResponseStoDetails;
  }

  private void preserveSystemEntityRelations(CatalogEntity newEntity, CatalogEntity existingEntity) {
    if (existingEntity.getRelations() == null) {
      return;
    }

    if (newEntity.getKind().equalsIgnoreCase(SYSTEM_KIND)) {
      Set<String> newOwnedBy = newEntity.getRelations() != null && newEntity.getRelations().containsKey("ownedBy")
          ? newEntity.getRelations().get("ownedBy")
          : new HashSet<>();
      Set<String> newPartOf = newEntity.getRelations() != null && newEntity.getRelations().containsKey("partOf")
          ? newEntity.getRelations().get("partOf")
          : new HashSet<>();
      Set<String> existingPartOf = existingEntity.getRelations().getOrDefault("partOf", new HashSet<>());

      Map<String, Set<String>> mergedRelations = new HashMap<>();
      existingEntity.getRelations().forEach((key, value) -> {
        if (!key.equals("ownedBy") && !key.equals("partOf")) {
          mergedRelations.put(key, new HashSet<>(value));
        }
      });

      if (!newOwnedBy.isEmpty()) {
        mergedRelations.put("ownedBy", newOwnedBy);
      }
      mergedRelations.put("partOf", !newPartOf.isEmpty() ? newPartOf : existingPartOf);

      newEntity.setRelations(mergedRelations);
      return;
    }

    if (newEntity.getRelations() == null) {
      newEntity.setRelations(new HashMap<>());
    }

    final Map<String, Object> existingSpec =
        existingEntity.getSpec() != null ? existingEntity.getSpec() : new HashMap<>();

    existingEntity.getRelations().forEach((relationKey, relationValues) -> {
      if (newEntity.getRelations().containsKey(relationKey)) {
        return;
      }

      if (!REFERENCED_TYPES.contains(relationKey)) {
        return;
      }

      if (existingSpec.containsKey(relationKey)) {
        return;
      }

      newEntity.getRelations().put(relationKey, new HashSet<>(relationValues));
    });
  }

  private void resolvePlaceholders(CatalogEntity catalogEntity) {
    String resolvedYaml = catalogEntity.getYaml();
    try {
      resolvedYaml = placeholderProcessor.process(catalogEntity);
    } catch (Exception ex) {
      log.warn("Failed to resolve placeholders for entity [{}]", catalogEntity.getQueryableEntityRef(), ex);
    }
    if (catalogEntity.getYaml().equals(resolvedYaml)) {
      return;
    }
    try {
      Map<String, Object> placeholdersDecorator =
          placeholderProcessor.getPlaceholdersDecorator(catalogEntity.getYaml(), resolvedYaml);
      catalogEntity.setDecorator(mergeDecorator(catalogEntity.getDecorator(), placeholdersDecorator));
    } catch (Exception ex) {
      log.warn("Failed to persist placeholder decorator for entity [{}]", catalogEntity.getQueryableEntityRef(), ex);
    }
  }

  private void projectMovementCore(String accountIdentifier, String oldOrgIdentifier, String newOrgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo) {
    List<CatalogEntity> catalogEntities =
        catalogEntityRepository.findAllByAccountIdentifierAndOrgIdentifierIsAndProjectIdentifierIs(
            accountIdentifier, oldOrgIdentifier, projectIdentifier);

    SourcePrincipalContextBuilder.setSourcePrincipal(
        new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));

    catalogEntities.forEach(catalogEntity -> {
      try {
        CatalogEntity existingCatalogEntity = null;
        if (catalogEntity instanceof InlineCatalogEntity) {
          existingCatalogEntity = ((InlineCatalogEntity) catalogEntity).toBuilder().build();
        } else if (catalogEntity instanceof GitReferencedCatalogEntity) {
          existingCatalogEntity = ((GitReferencedCatalogEntity) catalogEntity).toBuilder().build();
        }
        String oldInlineCatalogEntityYaml = catalogEntity.getYaml();
        String sourceLocation = catalogEntity.getSourceLocation();
        if (!isEmpty(sourceLocation)
            && sourceLocation.contains("account/" + accountIdentifier + "/module/code/orgs/" + oldOrgIdentifier
                + "/projects/" + projectIdentifier)) {
          sourceLocation = sourceLocation.replace("account/" + accountIdentifier + "/module/code/orgs/"
                  + oldOrgIdentifier + "/projects/" + projectIdentifier,
              "account/" + accountIdentifier + "/module/code/orgs/" + newOrgIdentifier + "/projects/"
                  + projectIdentifier);
          catalogEntity.setSourceLocation(sourceLocation);
        }
        Map<String, Object> metadata = catalogEntity.getMetadata();
        Map<String, Object> annotations = from(metadata, "annotations", Map.class);
        String sourceLocationAnnotation = from(annotations, "backstage\\.io/source-location", String.class);
        String managedByLocationAnnotation = from(annotations, "backstage\\.io/managed-by-location", String.class);
        String managedByOriginLocationAnnotation =
            from(annotations, "backstage\\.io/managed-by-origin-location", String.class);
        String techDocsRefAnnotation = from(annotations, "backstage\\.io/techdocs-ref", String.class);
        if (!isEmpty(sourceLocationAnnotation)
            && sourceLocationAnnotation.contains("account/" + accountIdentifier + "/module/code/orgs/"
                + oldOrgIdentifier + "/projects/" + projectIdentifier)) {
          sourceLocationAnnotation = sourceLocationAnnotation.replace("account/" + accountIdentifier
                  + "/module/code/orgs/" + oldOrgIdentifier + "/projects/" + projectIdentifier,
              "account/" + accountIdentifier + "/module/code/orgs/" + newOrgIdentifier + "/projects/"
                  + projectIdentifier);
          annotations.put("backstage.io/source-location", sourceLocationAnnotation);
          metadata.put("annotations", annotations);
          catalogEntity.setMetadata(metadata);
        }
        if (!isEmpty(managedByLocationAnnotation)
            && managedByLocationAnnotation.contains("account/" + accountIdentifier + "/module/code/orgs/"
                + oldOrgIdentifier + "/projects/" + projectIdentifier)) {
          managedByLocationAnnotation = managedByLocationAnnotation.replace("account/" + accountIdentifier
                  + "/module/code/orgs/" + oldOrgIdentifier + "/projects/" + projectIdentifier,
              "account/" + accountIdentifier + "/module/code/orgs/" + newOrgIdentifier + "/projects/"
                  + projectIdentifier);
          annotations.put("backstage.io/managed-by-location", managedByLocationAnnotation);
          metadata.put("annotations", annotations);
          catalogEntity.setMetadata(metadata);
        }
        if (!isEmpty(managedByOriginLocationAnnotation)
            && managedByOriginLocationAnnotation.contains("account/" + accountIdentifier + "/module/code/orgs/"
                + oldOrgIdentifier + "/projects/" + projectIdentifier)) {
          managedByOriginLocationAnnotation = managedByOriginLocationAnnotation.replace("account/" + accountIdentifier
                  + "/module/code/orgs/" + oldOrgIdentifier + "/projects/" + projectIdentifier,
              "account/" + accountIdentifier + "/module/code/orgs/" + newOrgIdentifier + "/projects/"
                  + projectIdentifier);
          annotations.put("backstage.io/managed-by-origin-location", managedByOriginLocationAnnotation);
          metadata.put("annotations", annotations);
          catalogEntity.setMetadata(metadata);
        }
        if (!isEmpty(techDocsRefAnnotation)
            && techDocsRefAnnotation.contains("account/" + accountIdentifier + "/module/code/orgs/" + oldOrgIdentifier
                + "/projects/" + projectIdentifier)) {
          techDocsRefAnnotation = techDocsRefAnnotation.replace("account/" + accountIdentifier + "/module/code/orgs/"
                  + oldOrgIdentifier + "/projects/" + projectIdentifier,
              "account/" + accountIdentifier + "/module/code/orgs/" + newOrgIdentifier + "/projects/"
                  + projectIdentifier);
          annotations.put("backstage.io/techdocs-ref", techDocsRefAnnotation);
          metadata.put("annotations", annotations);
          catalogEntity.setMetadata(metadata);
        }
        Map<String, Object> entitySpec = catalogEntity.getSpec();
        Map<String, Object> sourceCode = from(entitySpec, "sourceCode", Map.class);
        if (!isEmpty(sourceCode) && !isEmpty((String) sourceCode.get("provider"))
            && sourceCode.get("provider").equals("Harness")) {
          String url = (String) sourceCode.get("url");
          if (!isEmpty(url)
              && url.contains("account/" + accountIdentifier + "/module/code/orgs/" + oldOrgIdentifier + "/projects/"
                  + projectIdentifier)) {
            url = url.replace("account/" + accountIdentifier + "/module/code/orgs/" + oldOrgIdentifier + "/projects/"
                    + projectIdentifier,
                "account/" + accountIdentifier + "/module/code/orgs/" + newOrgIdentifier + "/projects/"
                    + projectIdentifier);
            sourceCode.put("url", url);
            entitySpec.put("sourceCode", sourceCode);
            catalogEntity.setSpec(entitySpec);
          }
        }
        Map<String, Object> ciCdPluginAnnotationMap = from(annotations, "harness\\.io/services", Map.class);
        String ciCdPluginAnnotationValue = !isEmpty(ciCdPluginAnnotationMap)
            ? (String) ciCdPluginAnnotationMap.get(catalogEntity.getIdentifier())
            : "";
        if (!isEmpty(ciCdPluginAnnotationValue)
            && ciCdPluginAnnotationValue.contains("account/" + accountIdentifier + "/module/code/orgs/"
                + oldOrgIdentifier + "/projects/" + projectIdentifier)) {
          ciCdPluginAnnotationValue = ciCdPluginAnnotationValue.replace("account/" + accountIdentifier
                  + "/module/code/orgs/" + oldOrgIdentifier + "/projects/" + projectIdentifier,
              "account/" + accountIdentifier + "/module/code/orgs/" + newOrgIdentifier + "/projects/"
                  + projectIdentifier);
          ciCdPluginAnnotationMap.put(catalogEntity.getIdentifier(), ciCdPluginAnnotationValue);
          annotations.put("harness.io/services", ciCdPluginAnnotationMap);
          metadata.put("annotations", annotations);
          catalogEntity.setMetadata(metadata);
        }
        Map<String, Object> harnessService = from(entitySpec, "harnessService", Map.class);
        if (!isEmpty(harnessService) && harnessService.get("orgIdentifier").equals(oldOrgIdentifier)
            && harnessService.get("projectIdentifier").equals(projectIdentifier)) {
          harnessService.put("orgIdentifier", newOrgIdentifier);
          entitySpec.put("harnessService", harnessService);
          catalogEntity.setSpec(entitySpec);
          setupUsageProducer.deleteCdServiceSetupUsage(
              accountIdentifier, oldOrgIdentifier, projectIdentifier, catalogEntity.getIdentifier());
          setupUsageProducer.publishCdServiceSetupUsage(accountIdentifier, newOrgIdentifier, projectIdentifier,
              catalogEntity.getIdentifier(), catalogEntity.getIdentifier());
        }
        catalogEntity.setOrgIdentifier(newOrgIdentifier);
        catalogEntity.setParentUniqueId(scopeInfo.getUniqueId());
        catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity));
        Set<String> groupingKinds = kindServiceHelper.groupingKinds(accountIdentifier);
        catalogEntity.setYaml(CatalogMapper.presentationYaml(catalogEntity, groupingKinds));
        catalogEntityRepository.save(catalogEntity);
        outboxService.save(new CatalogUpdateEvent(scopeInfo, catalogEntity.getYaml(), oldInlineCatalogEntityYaml,
            catalogEntity.getKind(), catalogEntity.getIdentifier()));
        existingCatalogEntity.setOrgIdentifier(newOrgIdentifier);
        harnessToIDPHelper.harnessToIdpSync(List.of(existingCatalogEntity), accountIdentifier, DELETE_ACTION);
        existingCatalogEntity.setOrgIdentifier(oldOrgIdentifier);
        harnessToIDPHelper.harnessToIdpSync(List.of(catalogEntity), accountIdentifier, CREATE_ACTION);
        if (catalogEntity instanceof GitReferencedCatalogEntity) {
          catalogServiceHelper.catalogEntityFromGit(
              scopeInfo.getUniqueId(), catalogEntity.getKind(), catalogEntity.getIdentifier(), true, false);
          idpGitXHelper.populateGitUpdateDetailsProjectMovement(catalogEntity);
          idpGitXHelper.updateGit(catalogEntity, scopeInfo);
          GitAwareContextHelper.initDefaultScmGitMetaDataAndRequestParams();
        }
        idpToHarnessHelper.sendCatalogEventsToRedis(List.of(catalogEntity), UPDATE_ACTION);

        if (ENVIRONMENT_KIND.equals(catalogEntity.getKind())) {
          Map<String, Object> spec = catalogEntity.getSpec();
          Map<String, Object> environmentBlueprint = from(spec, "environmentBlueprint", Map.class);
          String blueprintIdentifier = from(environmentBlueprint, "identifier", String.class);
          String blueprintVersion = from(environmentBlueprint, "version", String.class);
          String[] bpScope = CommonUtils.resolveScopeFromIdentifier(
              blueprintIdentifier, catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier());

          setupUsageProducer.deleteEnvironmentSetupUsage(accountIdentifier, existingCatalogEntity.getOrgIdentifier(),
              catalogEntity.getProjectIdentifier(), catalogEntity.getIdentifier());
          setupUsageProducer.publishEnvironmentBluePrintSetupUsages(accountIdentifier, catalogEntity.getOrgIdentifier(),
              catalogEntity.getProjectIdentifier(),
              catalogServiceHelper.getBlueprintVersionIdentifier(blueprintIdentifier, blueprintVersion),
              catalogEntity.getIdentifier(), bpScope[0], bpScope[1]);
        }
      } catch (Exception ex) {
        log.error("Error in project movement for catalog entity = {} on accountIdentifier = {} "
                + "oldOrgIdentifier = {} newOrgIdentifier = {} projectIdentifier = {} Error = {}",
            catalogEntity, accountIdentifier, oldOrgIdentifier, newOrgIdentifier, projectIdentifier, ex.getMessage(),
            ex);
      } finally {
        GitAwareContextHelper.initDefaultScmGitMetaDataAndRequestParams();
      }
    });
  }

  private void projectMovementDependents(String accountIdentifier, String oldOrgIdentifier, String newOrgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo) {
    List<GroupEntity> groupEntitiesForUpdate =
        projectMovementGroups(accountIdentifier, oldOrgIdentifier, newOrgIdentifier, projectIdentifier, scopeInfo);
    Pair<List<CatalogCustomPropertyEntity>, List<CatalogCustomPropertyUpdateEvent>>
        catalogCustomPropertyEntitiesForUpdateAndCatalogCustomPropertyUpdateEvents =
            projectMovementCatalogCustomProperties(
                accountIdentifier, oldOrgIdentifier, newOrgIdentifier, projectIdentifier, scopeInfo);
    List<BackstageScaffolderTaskEntity> backstageScaffolderTaskEntitiesForUpdate =
        projectMovementBackstageScaffolderTaskEntities(
            accountIdentifier, oldOrgIdentifier, newOrgIdentifier, projectIdentifier, scopeInfo);
    Pair<List<ScorecardEntity>, List<ScorecardUpdateEvent>> scorecardEntitiesForUpdateAndScorecardUpdateEvents =
        projectMovementScorecards(accountIdentifier, oldOrgIdentifier, newOrgIdentifier, projectIdentifier, scopeInfo);

    transactionHelper.performTransaction(() -> {
      groupsRepository.saveAll(groupEntitiesForUpdate);
      catalogCustomPropertiesRepository.saveAll(
          catalogCustomPropertyEntitiesForUpdateAndCatalogCustomPropertyUpdateEvents.getLeft());
      backstageScaffolderTaskEntityRepository.saveAll(backstageScaffolderTaskEntitiesForUpdate);
      catalogCustomPropertyEntitiesForUpdateAndCatalogCustomPropertyUpdateEvents.getRight().forEach(
          catalogCustomPropertyUpdateEvent -> outboxService.save(catalogCustomPropertyUpdateEvent));
      scorecardRepository.saveAll(scorecardEntitiesForUpdateAndScorecardUpdateEvents.getLeft());
      scorecardEntitiesForUpdateAndScorecardUpdateEvents.getRight().forEach(
          scorecardUpdateEvent -> outboxService.save(scorecardUpdateEvent));
      return null;
    });
  }

  private List<GroupEntity> projectMovementGroups(String accountIdentifier, String oldOrgIdentifier,
      String newOrgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    List<GroupEntity> groupEntities = groupsRepository.findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
        accountIdentifier, oldOrgIdentifier, projectIdentifier);

    List<GroupEntity> groupEntitiesForUpdate = new ArrayList<>();
    groupEntities.forEach(groupEntity -> {
      try {
        List<String> workflows = !isEmpty(groupEntity.getWorkflows()) ? groupEntity.getWorkflows() : new ArrayList<>();
        List<String> modifiedWorkflows = new ArrayList<>();
        workflows.forEach(workflow -> {
          workflow = workflow.replaceFirst("^([^.]+)\\.([^.]+)\\.([^.]+)", "$1." + newOrgIdentifier + ".$3");
          modifiedWorkflows.add(workflow);
        });
        groupEntity.setWorkflows(modifiedWorkflows);
        groupEntity.setOrgIdentifier(newOrgIdentifier);
        groupEntity.setParentUniqueId(scopeInfo.getUniqueId());
        groupEntitiesForUpdate.add(groupEntity);
      } catch (Exception ex) {
        log.error("Error in project movement for groups entity = {} on accountIdentifier = {} "
                + "oldOrgIdentifier = {} newOrgIdentifier = {} projectIdentifier = {} Error = {}",
            groupEntity, accountIdentifier, oldOrgIdentifier, newOrgIdentifier, projectIdentifier, ex.getMessage(), ex);
      }
    });
    return groupEntitiesForUpdate;
  }

  private Pair<List<CatalogCustomPropertyEntity>, List<CatalogCustomPropertyUpdateEvent>>
  projectMovementCatalogCustomProperties(String accountIdentifier, String oldOrgIdentifier, String newOrgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo) {
    List<CatalogCustomPropertyEntity> catalogCustomPropertyEntities =
        catalogCustomPropertiesRepository.findByAccountIdentifier(accountIdentifier);

    List<CatalogCustomPropertyEntity> catalogCustomPropertyEntitiesForUpdate = new ArrayList<>();
    List<CatalogCustomPropertyUpdateEvent> catalogCustomPropertyUpdateEvents = new ArrayList<>();
    catalogCustomPropertyEntities.forEach(catalogCustomPropertyEntity -> {
      try {
        CatalogCustomPropertyEntity existingCatalogCustomPropertyEntity =
            catalogCustomPropertyEntity.toBuilder().build();
        String entityRef = catalogCustomPropertyEntity.getEntityRef();
        Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
        String scope = kindScopeIdentifier.getMiddle();
        String[] scopeSplit = scope.split("\\.");
        if (scopeSplit.length == 3 && scopeSplit[1].equals(oldOrgIdentifier)
            && scopeSplit[2].equals(projectIdentifier)) {
          scope = scopeSplit[0] + "." + newOrgIdentifier + "." + scopeSplit[2];
          entityRef = kindScopeIdentifier.getLeft() + ":" + scope + "/" + kindScopeIdentifier.getRight();
          catalogCustomPropertyEntity.setEntityRef(entityRef);
        }
        catalogCustomPropertyEntitiesForUpdate.add(catalogCustomPropertyEntity);
        catalogCustomPropertyUpdateEvents.add(new CatalogCustomPropertyUpdateEvent(
            accountIdentifier, existingCatalogCustomPropertyEntity, catalogCustomPropertyEntity));
      } catch (Exception ex) {
        log.error("Error in project movement for catalogCustomProperties entity = {} on accountIdentifier = {} "
                + "oldOrgIdentifier = {} newOrgIdentifier = {} projectIdentifier = {} Error = {}",
            catalogCustomPropertyEntity, accountIdentifier, oldOrgIdentifier, newOrgIdentifier, projectIdentifier,
            ex.getMessage(), ex);
      }
    });
    return Pair.of(catalogCustomPropertyEntitiesForUpdate, catalogCustomPropertyUpdateEvents);
  }

  private List<BackstageScaffolderTaskEntity> projectMovementBackstageScaffolderTaskEntities(String accountIdentifier,
      String oldOrgIdentifier, String newOrgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    List<BackstageScaffolderTaskEntity> backstageScaffolderTaskEntities =
        backstageScaffolderTaskEntityRepository.findByAccountIdentifier(accountIdentifier);

    List<BackstageScaffolderTaskEntity> backstageScaffolderTaskEntitiesForUpdate = new ArrayList<>();
    backstageScaffolderTaskEntities.forEach(backstageScaffolderTaskEntity -> {
      try {
        JsonNode spec = objectMapper.readTree(backstageScaffolderTaskEntity.getSpec());
        JsonNode templateInfo = spec.get("templateInfo");
        String entityRef = templateInfo.get("entityRef").asText();
        Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
        String scope = kindScopeIdentifier.getMiddle();
        String[] scopeSplit = scope.split("\\.");
        if (scopeSplit.length == 3 && scopeSplit[1].equals(oldOrgIdentifier)
            && scopeSplit[2].equals(projectIdentifier)) {
          scope = scopeSplit[0] + "." + newOrgIdentifier + "." + scopeSplit[2];
          entityRef = kindScopeIdentifier.getLeft() + ":" + scope + "/" + kindScopeIdentifier.getRight();
          ((ObjectNode) templateInfo).put("entityRef", entityRef);
          backstageScaffolderTaskEntity.setEntityRef(entityRef);
          JsonNode entity = templateInfo.get("entity");
          if (entity != null && entity.get("metadata") != null) {
            JsonNode metadata = entity.get("metadata");
            ((ObjectNode) metadata).put("namespace", scope);
          }
          backstageScaffolderTaskEntity.setSpec(write(spec));
          backstageScaffolderTaskEntitiesForUpdate.add(backstageScaffolderTaskEntity);
        }
      } catch (Exception ex) {
        log.error("Error in project movement for backstageScaffolderTasks entity = {} on accountIdentifier = {} "
                + "oldOrgIdentifier = {} newOrgIdentifier = {} projectIdentifier = {} Error = {}",
            backstageScaffolderTaskEntity, accountIdentifier, oldOrgIdentifier, newOrgIdentifier, projectIdentifier,
            ex.getMessage(), ex);
      }
    });
    return backstageScaffolderTaskEntitiesForUpdate;
  }

  private Pair<List<ScorecardEntity>, List<ScorecardUpdateEvent>> projectMovementScorecards(String accountIdentifier,
      String oldOrgIdentifier, String newOrgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    List<ScorecardEntity> scorecardEntities = scorecardRepository.findByAccountIdentifier(accountIdentifier);

    List<ScorecardEntity> scorecardEntitiesForUpdate = new ArrayList<>();
    List<ScorecardUpdateEvent> scorecardUpdateEvents = new ArrayList<>();
    scorecardEntities.forEach(scorecardEntity -> {
      ScorecardEntity existingScorecardEntity = scorecardEntity.toBuilder().build();
      ScorecardFilter scorecardFilter = scorecardEntity.getFilter();
      List<String> scorecardScopes =
          !isEmpty(scorecardFilter.getScopes()) ? scorecardFilter.getScopes() : new ArrayList<>();
      List<String> updatedScopes = scorecardScopes.stream()
                                       .map(scope
                                           -> scope.equals("account." + oldOrgIdentifier + "." + projectIdentifier)
                                               ? "account." + newOrgIdentifier + "." + projectIdentifier
                                               : scope)
                                       .collect(Collectors.toList());
      scorecardFilter.setScopes(updatedScopes);
      scorecardEntity.setFilter(scorecardFilter);
      scorecardEntitiesForUpdate.add(scorecardEntity);

      ScorecardDetailsResponse oldScorecardDetailsResponse = ScorecardDetailsMapper.toDTO(existingScorecardEntity,
          scorecardService.getIdentifierCheckEntityMapping(accountIdentifier,
              existingScorecardEntity.getChecks()
                  .stream()
                  .map(ScorecardEntity.Check::getIdentifier)
                  .collect(Collectors.toSet())),
          null, idpCommonService.idpScorecardTiersEnabled(accountIdentifier), accountIdentifier);
      ScorecardDetailsResponse newScorecardDetailsResponse = ScorecardDetailsMapper.toDTO(scorecardEntity,
          scorecardService.getIdentifierCheckEntityMapping(accountIdentifier,
              scorecardEntity.getChecks()
                  .stream()
                  .map(ScorecardEntity.Check::getIdentifier)
                  .collect(Collectors.toSet())),
          null, idpCommonService.idpScorecardTiersEnabled(accountIdentifier), accountIdentifier);

      scorecardUpdateEvents.add(
          new ScorecardUpdateEvent(accountIdentifier, newScorecardDetailsResponse, oldScorecardDetailsResponse));
    });

    return Pair.of(scorecardEntitiesForUpdate, scorecardUpdateEvents);
  }

  @Override
  public Pair<String, String> getEntityContent(String harnessAccount, String orgIdentifier, String projectIdentifier,
      String scope, String kind, String identifier, String path) {
    ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
    Optional<CatalogEntity> entityOpt =
        catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(scopeInfo.getUniqueId(), kind, identifier);

    if (entityOpt.isEmpty()) {
      throw new EntityNotFoundException(String.format("Entity not found: %s/%s", kind, identifier));
    }

    CatalogEntity entity = entityOpt.get();
    catalogServiceHelper.checkRbacWithOwnerFallback(
        harnessAccount, CatalogUtils.entityRef(entity), entity.getOwner(), "view");

    Map<String, Object> decoratedMetadata = entity.getDecoratedMetadata();
    List<Map<String, Object>> contentFiles =
        decoratedMetadata != null ? (List<Map<String, Object>>) decoratedMetadata.get("contentFiles") : null;
    if (contentFiles == null || contentFiles.stream().noneMatch(f -> path.equals(f.get("path")))) {
      throw new EntityNotFoundException("File not found: " + path);
    }

    String gcsObjectPath = catalogContentConfig.getGcsObjectPath(harnessAccount, kind, entity.getUniqueId(), path);

    try {
      byte[] encrypted = gcpStorageUtil.readFileFromGcs(catalogContentConfig.getBucketName(), gcsObjectPath);
      byte[] content = idpContentEncryptionService.decrypt(encrypted, harnessAccount);
      String contentType = path.endsWith(".md") ? "text/markdown" : "text/plain";
      return Pair.of(new String(content, StandardCharsets.UTF_8), contentType + "; charset=utf-8");
    } catch (Exception e) {
      log.error("Failed to read file content from GCS: {}/{}", catalogContentConfig.getBucketName(), gcsObjectPath, e);
      throw new EntityNotFoundException("File content not found");
    }
  }

  @Override
  public List<EntityConvertV2Response> convertEntityV2(
      String harnessAccount, String option, List<EntitiesConvertRequestBody> entitiesConvertRequestBodyList) {
    List<EntityConvertV2Response> entityConvertResponses = new ArrayList<>();
    entitiesConvertRequestBodyList.forEach(entitiesConvertRequestBody -> {
      try {
        GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                     .branch(entitiesConvertRequestBody.getBranchName())
                                                     .connectorRef(entitiesConvertRequestBody.getConnectorRef())
                                                     .repoName(entitiesConvertRequestBody.getRepoName())
                                                     .build());
        EntityRequest entityRequest = new EntityRequest();
        entityRequest.setYaml("");
        EntityConvertResponse entityConvertResponse =
            convertEntity(harnessAccount, option, entityRequest, entitiesConvertRequestBody.getEntityRef(), true);
        EntityConvertV2Response entityConvertV2Response = new EntityConvertV2Response();
        entityConvertV2Response.setEntityRef(entitiesConvertRequestBody.getEntityRef());
        entityConvertV2Response.setYaml(entityConvertResponse.getYaml());
        entityConvertResponses.add(entityConvertV2Response);
      } catch (Exception ex) {
        log.error("Error in convertEntityV2 for entity = {} Error = {}", entitiesConvertRequestBody.getEntityRef(),
            ex.getMessage(), ex);
      }
    });
    return entityConvertResponses;
  }

  @SuppressWarnings("unchecked")
  private void unsubscribeLinkedIntegrationEntities(String harnessAccount, CatalogEntity catalogEntity) {
    Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData();
    if (isEmpty(processedData)) {
      return;
    }

    Object metadataObj = processedData.get("metadata");
    if (!(metadataObj instanceof Map)) {
      return;
    }

    Object integrationObj = ((Map<String, Object>) metadataObj).get("integration");
    if (!(integrationObj instanceof Map)) {
      return;
    }

    Map<String, Object> integrationBySpacePath = (Map<String, Object>) integrationObj;
    for (Map.Entry<String, Object> spacePathEntry : integrationBySpacePath.entrySet()) {
      if (!(spacePathEntry.getValue() instanceof Map)) {
        continue;
      }

      String[] orgAndProject = catalogIntegrationServiceHelper.parseSpacePath(spacePathEntry.getKey());
      String orgIdentifier = orgAndProject[0];
      String projectIdentifier = orgAndProject[1];

      Map<String, Object> integrationsById = (Map<String, Object>) spacePathEntry.getValue();
      for (Map.Entry<String, Object> integrationEntry : integrationsById.entrySet()) {
        if (!(integrationEntry.getValue() instanceof Map)) {
          continue;
        }

        String integrationConfigIdentifier = integrationEntry.getKey();
        Map<String, String> entityUuidToKind =
            catalogIntegrationServiceHelper.collectEntityUuidToKind((Map<String, Object>) integrationEntry.getValue());

        for (Map.Entry<String, String> uuidToKind : entityUuidToKind.entrySet()) {
          String entityUuid = uuidToKind.getKey();
          String kind = uuidToKind.getValue();
          try {
            OpenapiSubscribeEntitiesRequest unsubscribeRequest =
                catalogIntegrationServiceHelper.prepareSubscribeEntitiesRequest(kind, entityUuid);
            getGeneralResponse(integrationManagerClientHelper.unsubscribeFromEntityUpdates(harnessAccount,
                harnessAccount, orgIdentifier, projectIdentifier, integrationConfigIdentifier, unsubscribeRequest));
          } catch (Exception ex) {
            log.error("Error unsubscribing entity uuid={} kind={} from integration-manager for integrationConfig={} "
                    + "scope=[org={}, project={}]. Exception = {}",
                entityUuid, kind, integrationConfigIdentifier, orgIdentifier, projectIdentifier, ex.getMessage(), ex);
          }
        }
      }
    }
  }
}

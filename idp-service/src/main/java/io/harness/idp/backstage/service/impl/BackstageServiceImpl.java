/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.backstage.beans.BackstageCatalogEntityTypes.GROUP;
import static io.harness.idp.backstage.beans.BackstageCatalogEntityTypes.USER;
import static io.harness.idp.backstage.beans.BackstageScaffolderTaskListItem.toEntities;
import static io.harness.idp.backstage.utils.BackstageUtils.getEntityUniqueId;
import static io.harness.idp.backstage.utils.BackstageUtils.getEntityUniqueIdForByNameAPI;
import static io.harness.idp.common.Constants.BACKSTAGE_KINDS;
import static io.harness.idp.common.JacksonUtils.convert;
import static io.harness.idp.common.JacksonUtils.readValueForObject;
import static io.harness.idp.common.JacksonUtils.write;
import static io.harness.idp.common.YamlUtils.writeObjectAsYaml;
import static io.harness.idp.common.YamlUtils.yamlObject;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.CGRestUtils.getResponse;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.clients.BackstageResourceClient;
import io.harness.context.GlobalContextData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.expression.common.ExpressionMode;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.BackstageScaffolderTask;
import io.harness.idp.backstage.beans.BackstageScaffolderTaskListItem;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogApiEntity;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity.Relation;
import io.harness.idp.backstage.entities.BackstageCatalogEntity.Target;
import io.harness.idp.backstage.entities.BackstageCatalogResourceEntity;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.events.BackstageCatalogEntityCreateEvent;
import io.harness.idp.backstage.events.BackstageCatalogEntityDeleteEvent;
import io.harness.idp.backstage.events.BackstageCatalogEntityUpdateEvent;
import io.harness.idp.backstage.events.BackstageScaffolderTaskStartEvent;
import io.harness.idp.backstage.expression.IdpVariableExpressionEvaluator;
import io.harness.idp.backstage.repositories.BackstageCatalogDuplicateEntry;
import io.harness.idp.backstage.repositories.BackstageCatalogEntityRepository;
import io.harness.idp.backstage.repositories.BackstageScaffolderTaskEntityRepository;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.Constants;
import io.harness.idp.events.producers.IdpEntityCrudStreamProducer;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.repositories.NamespaceRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.variable.dto.VariableListRequestDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.BackstageHarnessSyncRequest;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.User;
import io.harness.variable.remote.VariableClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.client.result.UpdateResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class BackstageServiceImpl implements BackstageService {
  @Inject @Named("allowedKindsForCatalogSync") private List<String> allowedKindsForCatalogSync;
  @Inject @Named("allowedKindsForAudit") private List<String> allowedKindsForAudit;
  @Inject AccountClient accountClient;
  @Inject @Named("PRIVILEGED") VariableClient variableClient;
  @Inject NamespaceService namespaceService;
  @Inject BackstageResourceClient backstageResourceClient;
  @Inject BackstageCatalogEntityRepository backstageCatalogEntityRepository;
  @Inject IdpEntityCrudStreamProducer idpEntityCrudStreamProducer;
  @Inject IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;
  @Inject OutboxService outboxService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  @Inject NamespaceRepository namespaceRepository;
  @Inject BackstageScaffolderTaskEntityRepository scaffolderTaskEntityRepository;
  public final static Pattern VARIABLE_EXPRESSION_PATTERN = Pattern.compile("<\\+variable\\.account\\.(.*?)>");
  private static final Yaml yaml = yamlObject();
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  @Override
  public void sync() {
    List<String> accountIdentifiers = namespaceService.getAccountIds();
    log.info("Fetched {} IDP active accounts for IdpCatalogEntitiesAsHarnessEntities sync", accountIdentifiers.size());
    accountIdentifiers.forEach(this::sync);
  }

  @Override
  public boolean sync(String accountIdentifier) {
    try {
      log.info("Syncing IDP catalog entities as Harness entities for accountIdentifier = {}", accountIdentifier);
      String url = String.format("%s/idp/api/catalog/entities", accountIdentifier);
      Object response = getGeneralResponse(backstageResourceClient.getCatalogEntities(url));

      List<Map<String, Object>> entities =
          objectMapper.convertValue(response, new TypeReference<List<Map<String, Object>>>() {});

      for (Map<String, Object> entity : entities) {
        CommonUtils.normalizeSystemField(entity);
      }

      List<BackstageCatalogEntity> backstageCatalogEntities = convert(entities, BackstageCatalogEntity.class);
      backstageCatalogEntities = filter(backstageCatalogEntities);
      syncInternal(
          accountIdentifier, "", BackstageHarnessSyncRequest.ActionEnum.UPSERT.value(), backstageCatalogEntities, null);
    } catch (Exception ex) {
      log.error("Error in IdpCatalogEntitiesAsHarnessEntities sync for accountIdentifier = {} Error = {}",
          accountIdentifier, ex.getMessage(), ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean syncByType(String accountIdentifier, BackstageHarnessSyncRequest.TypeEnum type, String identifier,
      String action, String syncMode, User user) {
    switch (type) {
      case ENTITY:
        return sync(accountIdentifier, identifier, action, syncMode, user);
      case TASK:
        return syncScaffolderTasks(accountIdentifier, identifier, action, syncMode, user);
      default:
        throw new UnexpectedException("Unsupported type for syncing backstage idp entities as harness entities");
    }
  }

  @Override
  public boolean sync(String accountIdentifier, String entityUid, String action, String syncMode, User user) {
    switch (BackstageHarnessSyncRequest.SyncModeEnum.fromValue(syncMode)) {
      case SYNC:
        return syncInSynchronousMode(accountIdentifier, entityUid, action, user);
      case ASYNC:
        return syncInAsynchronousMode(accountIdentifier, entityUid, action, user);
      default:
        throw new UnexpectedException("Unsupported sync mode for syncing IdpCatalogEntitiesAsHarnessEntities");
    }
  }

  @Override
  public boolean syncScaffolderTasks(
      String accountIdentifier, String taskId, String action, String syncMode, User user) {
    switch (BackstageHarnessSyncRequest.SyncModeEnum.fromValue(syncMode)) {
      case SYNC:
        return syncTaskInSynchronousMode(accountIdentifier, taskId, action, user);
      case ASYNC:
        return syncTaskInAsynchronousMode(accountIdentifier, taskId, action, user);
      default:
        throw new UnexpectedException("Unsupported sync mode for syncing Scaffolder Tasks");
    }
  }

  private boolean syncTaskInSynchronousMode(String accountIdentifier, String taskId, String action, User user) {
    try {
      log.info("Syncing IDP backstage tasks as Harness entities for accountIdentifier = {} taskId = {} Action = {}",
          accountIdentifier, taskId, action);
      switch (BackstageHarnessSyncRequest.ActionEnum.fromValue(action)) {
        case START:
          handleStartAction(accountIdentifier, taskId, user);
          break;
        default:
          throw new UnexpectedException("Unsupported action for syncing Scaffolder Tasks in synchronous mode");
      }
    } catch (Exception ex) {
      log.error("Error in Scaffolder Tasks sync for accountIdentifier = {} taskId = {} Action = {} Error = {}",
          accountIdentifier, taskId, action, ex.getMessage(), ex);
      return false;
    }
    return true;
  }

  private void handleStartAction(String accountIdentifier, String taskId, User user) {
    Object response;
    try {
      response = getGeneralResponse(backstageResourceClient.getScaffolderTask(accountIdentifier, taskId));
    } catch (Exception ex) {
      log.error("Error in fetching scaffolder task by id for account = {} taskId = {} Error = {}", accountIdentifier,
          taskId, ex.getMessage(), ex);
      return;
    }
    BackstageScaffolderTask scaffolderTask = readValueForObject(response, BackstageScaffolderTask.class);
    BackstageScaffolderTaskEntity scaffolderEntity =
        BackstageScaffolderTask.toEntity(accountIdentifier, scaffolderTask, scaffolderTaskEntityRepository);
    GlobalContextData currentPrincipalContext = GlobalContextManager.get(PrincipalContextData.PRINCIPAL_CONTEXT);
    setUserContext(accountIdentifier, user);

    try {
      Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        scaffolderTaskEntityRepository.save(scaffolderEntity);
        String auditResourceIdentifier = buildScaffolderTaskAuditResourceIdentifier(scaffolderTask);
        if (StringUtils.isNotBlank(auditResourceIdentifier)) {
          outboxService.save(new BackstageScaffolderTaskStartEvent(accountIdentifier, auditResourceIdentifier));
        }
        return true;
      }));
    } catch (Exception e) {
      log.error("Error publishing audit event for task with identifier {}.", taskId, e);
    } finally {
      GlobalContextManager.upsertGlobalContextRecord(currentPrincipalContext);
    }

    log.info("Successfully saved scaffolder task for accountIdentifier = {} taskId = {}", accountIdentifier,
        scaffolderEntity.getIdentifier());
  }

  @Override
  public void syncScaffolderTasks() {
    List<NamespaceEntity> activeAccounts = namespaceService.getActiveAccounts();
    log.info("Fetched {} IDP active accounts for scaffolder tasks sync", activeAccounts.size());
    activeAccounts.forEach(namespaceEntity -> {
      String accountIdentifier = namespaceEntity.getAccountIdentifier();
      long syncFrom = Objects.nonNull(namespaceEntity.getMetadata())
          ? namespaceEntity.getMetadata().getScaffolderTasksSyncFrom()
          : 0;
      syncScaffolderTasks(accountIdentifier, syncFrom, namespaceEntity);
    });
  }

  @Override
  public List<BackstageCatalogEntity> findAllByAccountIdentifier(String accountIdentifier) {
    return backstageCatalogEntityRepository.findAllByAccountIdentifier(accountIdentifier);
  }

  @Override
  public List<BackstageCatalogEntity> queryEntities(
      ScorecardFilter filter, String accountIdentifier, List<String> skipEntityUids) {
    return backstageCatalogEntityRepository.queryEntities(filter.getKind(), filter.getType(), filter.getOwners(),
        filter.getTags(), filter.getLifecycle(), accountIdentifier, skipEntityUids);
  }

  @Override
  public List<BackstageCatalogEntity> findAllByAccountIdentifierAndEntityRefs(
      String accountIdentifier, List<String> entityUids) {
    return backstageCatalogEntityRepository.findAllByAccountIdentifierAndEntityUidIn(accountIdentifier, entityUids);
  }

  @Override
  public BackstageCatalogEntity findByAccountIdentifierAndEntityRef(String accountIdentifier, String entityUid) {
    Optional<BackstageCatalogEntity> optionalBackstageCatalogEntity =
        backstageCatalogEntityRepository.findByAccountIdentifierAndEntityUidIgnoreCase(
            accountIdentifier, entityUid.replaceAll("[^a-zA-Z0-9]", "\\\\$0"));
    if (optionalBackstageCatalogEntity.isEmpty()) {
      throw new InvalidRequestException(String.format("No record found for entityUid %s", entityUid));
    }
    return optionalBackstageCatalogEntity.get();
  }

  @Override
  public void modifyEntityIdentifier(String accountIdentifier) {
    List<String> entityIdentifiers =
        backstageCatalogEntityRepository.findEntityIdentifiersByAccountIdentifier(accountIdentifier);
    log.info("Totally {} unique records present in BackstageCatalog collection for account {}",
        entityIdentifiers.size(), accountIdentifier);
    entityIdentifiers.forEach(entityIdentifier -> {
      String[] kindNamespaceAndName = entityIdentifier.split("/");
      if (kindNamespaceAndName.length == 3 && BACKSTAGE_KINDS.contains(kindNamespaceAndName[0])) {
        String modifiedEntityIdentifier =
            getEntityUniqueId(kindNamespaceAndName[1], kindNamespaceAndName[0], kindNamespaceAndName[2]);
        try {
          UpdateResult updateResult = backstageCatalogEntityRepository.updateEntityIdentifier(
              accountIdentifier, entityIdentifier, modifiedEntityIdentifier);
          log.info("{} record modified in BackstageCatalog collection for account {}, identifier {}",
              updateResult.getModifiedCount(), accountIdentifier, entityIdentifier);
        } catch (Exception e) {
          log.error("Error occurred while modifying BackstageCatalog collection for account {}, identifier {}",
              accountIdentifier, entityIdentifier, e);
        }
      }
    });
  }

  @Override
  public void modifyEntityIdentifierForIdpV2(String accountIdentifier, Set<String> conflictedEntityUids) {
    List<BackstageCatalogEntity> backstageCatalogEntities =
        backstageCatalogEntityRepository.findAllByAccountIdentifier(accountIdentifier);
    log.info("Totally {} records present in BackstageCatalog collection for account {}",
        backstageCatalogEntities.size(), accountIdentifier);
    List<BackstageCatalogEntity> entitiesToSave = new ArrayList<>();
    for (BackstageCatalogEntity backstageCatalogEntity : backstageCatalogEntities) {
      String entityUid = backstageCatalogEntity.getEntityUid();
      String[] namespaceKindName = entityUid.split("/");
      if (namespaceKindName.length == 3 && !namespaceKindName[0].equals("account")
          && !namespaceKindName[0].contains(".")) {
        String namespace = namespaceKindName[0].toLowerCase();
        String kind = namespaceKindName[1].toLowerCase();
        String name = namespaceKindName[2].toLowerCase();
        String modifiedEntityUid = "account/" + kind + "/" + name;
        String modifiedEntityUidForConflict = "account/" + kind + "/" + namespace + "_" + name;
        if (conflictedEntityUids.contains(modifiedEntityUidForConflict)) {
          name = namespace + "_" + name;
          backstageCatalogEntity.setEntityUid(modifiedEntityUidForConflict);
        } else {
          backstageCatalogEntity.setEntityUid(modifiedEntityUid);
        }
        backstageCatalogEntity.getMetadata().put(MetadataFieldConstants.NAMESPACE, "account");
        backstageCatalogEntity.getMetadata().put(MetadataFieldConstants.NAME, name);
        backstageCatalogEntity.setYaml(writeObjectAsYaml(backstageCatalogEntity));
        boolean isBackstageCatalogEntityPresent = backstageCatalogEntities.stream().anyMatch(
            entity -> entity.getEntityUid().equals(backstageCatalogEntity.getEntityUid()));
        if (!isBackstageCatalogEntityPresent) {
          entitiesToSave.add(backstageCatalogEntity);
        }
      }
    }
    log.info("Totally {} records to be modified in BackstageCatalog collection for account {}", entitiesToSave.size(),
        accountIdentifier);
    backstageCatalogEntityRepository.saveAll(entitiesToSave);
  }

  @SneakyThrows
  @Override
  public void modifyEntityRefInScaffolderTaskForIdpV2(String accountIdentifier, Set<String> conflictedEntityRefs) {
    List<BackstageScaffolderTaskEntity> backstageScaffolderTaskEntities =
        scaffolderTaskEntityRepository.findByAccountIdentifier(accountIdentifier);
    log.info("Totally {} records present in BackstageScaffolderTasks collection for account {}",
        backstageScaffolderTaskEntities.size(), accountIdentifier);
    List<BackstageScaffolderTaskEntity> entitiesToSave = new ArrayList<>();
    for (BackstageScaffolderTaskEntity backstageScaffolderTaskEntity : backstageScaffolderTaskEntities) {
      JsonNode spec = objectMapper.readTree(backstageScaffolderTaskEntity.getSpec());
      JsonNode templateInfo = spec.get("templateInfo");
      String entityRef = templateInfo.get("entityRef").asText();
      int colonIndex = entityRef.indexOf(':');
      int slashIndex = entityRef.indexOf('/');

      if (colonIndex == -1 || slashIndex == -1) {
        continue;
      }
      String kind = entityRef.substring(0, colonIndex).toLowerCase();
      String namespace = entityRef.substring(colonIndex + 1, slashIndex).toLowerCase();
      String name = entityRef.substring(slashIndex + 1).toLowerCase();
      if (!namespace.equals("account") && !namespace.contains(".")) {
        String modifiedEntityRef = kind + ":account/" + name;
        String modifiedEntityRefForConflict = kind + ":account/" + namespace + "_" + name;
        if (conflictedEntityRefs.contains(modifiedEntityRefForConflict)) {
          name = namespace + "_" + name;
          ((ObjectNode) templateInfo).put("entityRef", modifiedEntityRefForConflict);
        } else {
          ((ObjectNode) templateInfo).put("entityRef", modifiedEntityRef);
        }
        JsonNode entity = templateInfo.get("entity");
        if (entity != null && entity.get("metadata") != null) {
          JsonNode metadata = entity.get("metadata");
          ((ObjectNode) metadata).put("namespace", "account");
          ((ObjectNode) metadata).put("name", name);
        }
        backstageScaffolderTaskEntity.setSpec(write(spec));
        entitiesToSave.add(backstageScaffolderTaskEntity);
      }
    }
    log.info("Totally {} records to be modified in BackstageScaffolderTasks collection for account {}",
        entitiesToSave.size(), accountIdentifier);
    scaffolderTaskEntityRepository.saveAll(entitiesToSave);
  }

  @Override
  public String resolveExpressions(String entity, String accountIdentifier) {
    Matcher matcher = VARIABLE_EXPRESSION_PATTERN.matcher(entity);
    Set<String> identifiers = new HashSet<>();
    while (matcher.find()) {
      String identifier = matcher.group(1);
      identifiers.add(identifier);
    }
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .uniqueId(accountIdentifier)
                              .build();
    IdpVariableExpressionEvaluator evaluator =
        new IdpVariableExpressionEvaluator(getResponse(accountClient.getAccountDTO(scopeInfo.getAccountIdentifier())),
            fetchAccountLevelVariables(scopeInfo, identifiers));
    return yaml.dump(evaluator.resolve(yaml.load(entity), ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED));
  }

  @Override
  public List<BackstageCatalogEntity> findAllByAccountIdentifierAndKind(String accountIdentifier, String kind) {
    return backstageCatalogEntityRepository.findAllByAccountIdentifierAndKind(accountIdentifier, kind);
  }

  @Override
  public Page<BackstageCatalogEntity> findAllByAccountIdentifierAndKind(
      String accountIdentifier, String kind, int page, int limit) {
    Criteria criteria = createCriteriaForAccountAndKind(accountIdentifier, kind);
    Pageable pageable = PageRequest.of(page, limit);
    return backstageCatalogEntityRepository.findAll(criteria, pageable);
  }

  @Override
  public void removeDuplicateEntries() {
    List<BackstageCatalogDuplicateEntry> duplicateEntries = backstageCatalogEntityRepository.findDuplicateEntities();
    List<String> idsToBeRemoved = new ArrayList<>();
    for (BackstageCatalogDuplicateEntry entry : duplicateEntries) {
      log.info("Duplicate entries found for accountId: {}, entityUid: {}, ids: {}", entry.getAccountIdentifier(),
          entry.getEntityUid(), entry.getDuplicates());
      idsToBeRemoved.addAll(entry.getDuplicates().subList(1, entry.getDuplicates().size()).stream().toList());
    }
    if (!isEmpty(idsToBeRemoved)) {
      backstageCatalogEntityRepository.deleteAllById(idsToBeRemoved);
    }
  }

  @Override
  public void changeSystemAsList(String accountIdentifier) {
    log.info("Starting system field migration for accountIdentifier: {}", accountIdentifier);

    List<BackstageCatalogEntity> backstageCatalogEntities =
        backstageCatalogEntityRepository.findAllByAccountIdentifierAndKindIn(
            accountIdentifier, Constants.ENTITIES_SUPPORTING_SYSTEM);

    List<BackstageCatalogEntity> entitiesToUpdate = new ArrayList<>();

    for (BackstageCatalogEntity entity : backstageCatalogEntities) {
      try {
        String kind = entity.getKind();
        if (kind == null) {
          continue;
        }

        boolean updated = false;

        switch (kind) {
          case "Component":
            if (entity instanceof BackstageCatalogComponentEntity) {
              BackstageCatalogComponentEntity componentEntity = (BackstageCatalogComponentEntity) entity;
              List<String> system = componentEntity.getSpec().getSystem();
              if (system != null) {
                componentEntity.getSpec().setSystem(system);
                updated = true;
              }
              componentEntity.setYaml(writeObjectAsYaml(componentEntity));
            }
            break;

          case "API":
            if (entity instanceof BackstageCatalogApiEntity) {
              BackstageCatalogApiEntity apiEntity = (BackstageCatalogApiEntity) entity;
              List<String> system = apiEntity.getSpec().getSystem();
              if (system != null) {
                apiEntity.getSpec().setSystem(system);
                updated = true;
              }
              apiEntity.setYaml(writeObjectAsYaml(apiEntity));
            }
            break;

          case "Resource":
            if (entity instanceof BackstageCatalogResourceEntity) {
              BackstageCatalogResourceEntity resourceEntity = (BackstageCatalogResourceEntity) entity;
              List<String> system = resourceEntity.getSpec().getSystem();
              if (system != null) {
                resourceEntity.getSpec().setSystem(system);
                updated = true;
              }
              resourceEntity.setYaml(writeObjectAsYaml(resourceEntity));
            }
            break;

          default:
            break;
        }

        if (updated) {
          entitiesToUpdate.add(entity);
          log.info("Updating entity uid - {} update for entity id: {}, account - {}", entity.getEntityUid(),
              accountIdentifier);
        }

      } catch (Exception ex) {
        log.warn("Failed to process entity id: {}, error: {}", entity.getId(), ex.getMessage(), ex);
      }
    }

    if (!entitiesToUpdate.isEmpty()) {
      backstageCatalogEntityRepository.saveAll(entitiesToUpdate);
      log.info("Updated {} entities for accountIdentifier: {}", entitiesToUpdate.size(), accountIdentifier);
    } else {
      log.info("No entities needed update for accountIdentifier: {}", accountIdentifier);
    }

    log.info("Completed system field migration for accountIdentifier: {}", accountIdentifier);
  }

  private Criteria createCriteriaForAccountAndKind(String accountIdentifier, String kind) {
    Criteria criteria = new Criteria();
    criteria.and(BackstageCatalogEntity.BackstageCatalogKeys.accountIdentifier)
        .is(accountIdentifier)
        .and(BackstageCatalogEntity.BackstageCatalogKeys.kind)
        .is(kind);
    return criteria;
  }

  private void syncScaffolderTasks(String accountIdentifier, long syncFrom, NamespaceEntity namespaceEntity) {
    log.info("Syncing scaffolder tasks for accountIdentifier = {} syncFrom = {}", accountIdentifier, syncFrom);
    try {
      long syncTo = OffsetDateTime.now().toInstant().toEpochMilli();
      int pageSize = 100;
      List<BackstageScaffolderTaskListItem> allScaffolderTasks = new ArrayList<>();

      Object firstResponse = getGeneralResponse(
          backstageResourceClient.scaffolderListTasksPaginated(accountIdentifier, syncFrom, syncTo, 1, pageSize));

      Map<String, Object> firstResponseMap = (Map<String, Object>) firstResponse;
      int totalCount = ((Number) firstResponseMap.get("totalCount")).intValue();

      if (totalCount == 0) {
        log.info("No scaffolder tasks to sync for accountIdentifier = {} syncFrom = {}", accountIdentifier, syncFrom);
        syncScaffolderTasksInternal(accountIdentifier, syncFrom, namespaceEntity, allScaffolderTasks);
        return;
      }

      int totalPages = (int) Math.ceil((double) totalCount / pageSize);
      log.info("Total {} scaffolder tasks to sync in {} pages for accountIdentifier = {} syncFrom = {}", totalCount,
          totalPages, accountIdentifier, syncFrom);

      for (int page = 1; page <= totalPages; page++) {
        log.info("Fetching scaffolder tasks page {}/{} for accountIdentifier = {} syncFrom = {}", page, totalPages,
            accountIdentifier, syncFrom);

        Object response = (page == 1) ? firstResponse
                                      : getGeneralResponse(backstageResourceClient.scaffolderListTasksPaginated(
                                            accountIdentifier, syncFrom, syncTo, page, pageSize));

        Map<String, Object> responseMap = (Map<String, Object>) response;
        List<Object> tasksData = (List<Object>) responseMap.get("data");

        if (tasksData == null || tasksData.isEmpty()) {
          log.info("No tasks found on page {} for accountIdentifier = {}", page, accountIdentifier);
          continue;
        }

        List<BackstageScaffolderTaskListItem> scaffolderTasks =
            convert(tasksData, BackstageScaffolderTaskListItem.class);
        allScaffolderTasks.addAll(scaffolderTasks);
        log.info("Fetched {} scaffolder tasks on page {}/{} for accountIdentifier = {} syncFrom = {}",
            scaffolderTasks.size(), page, totalPages, accountIdentifier, syncFrom);
      }

      log.info("Fetched total {} scaffolder tasks for accountIdentifier = {} syncFrom = {}", allScaffolderTasks.size(),
          accountIdentifier, syncFrom);
      syncScaffolderTasksInternal(accountIdentifier, syncFrom, namespaceEntity, allScaffolderTasks);
    } catch (Exception ex) {
      log.error("Error in syncing scaffolder tasks for accountIdentifier = {} syncFrom = {} Skipping update to "
              + "metadata scaffolderTasksSyncFrom Error = {}",
          accountIdentifier, syncFrom, ex.getMessage(), ex);
    }
  }

  private String buildScaffolderTaskAuditResourceIdentifier(BackstageScaffolderTask scaffolderTask) {
    String resourceIdentifier = "";
    String entityRef = scaffolderTask.getSpec().getTemplateInfo().getEntityRef();
    if (StringUtils.isNotBlank(entityRef)) {
      String[] kindAndNamespaceName = entityRef.split(":");
      if (kindAndNamespaceName.length == 2) {
        resourceIdentifier = kindAndNamespaceName[1] + "/" + scaffolderTask.getIdentifier();
      }
    }
    return resourceIdentifier;
  }

  private void setUserContext(String accountIdentifier, User user) {
    if (user != null && StringUtils.isNotBlank(user.getUuid())) {
      GlobalContextManager.upsertGlobalContextRecord(
          PrincipalContextData.builder()
              .principal(new UserPrincipal(user.getUuid(), user.getEmail(), user.getName(), accountIdentifier))
              .build());
    }
  }

  private List<BackstageCatalogEntity> filter(List<BackstageCatalogEntity> backstageCatalogEntities) {
    return backstageCatalogEntities.stream()
        .filter(backstageCatalogEntity -> allowedKindsForCatalogSync.contains(backstageCatalogEntity.getKind()))
        .collect(Collectors.toList());
  }

  private boolean syncTaskInAsynchronousMode(String accountIdentifier, String taskId, String action, User user) {
    idpServiceMiscRedisProducer.publishIDPCatalogEntitiesSyncCaptureToRedis(
        accountIdentifier, taskId, action, user, BackstageHarnessSyncRequest.TypeEnum.TASK);
    return true;
  }

  private boolean shouldPublishAudit(BackstageCatalogEntity backstageCatalogEntity) {
    return allowedKindsForAudit.contains(backstageCatalogEntity.getKind());
  }

  private void syncInternal(String accountIdentifier, String entityUid, String action,
      List<BackstageCatalogEntity> backstageCatalogEntities, User user) {
    if (backstageCatalogEntities.isEmpty()) {
      return;
    }
    log.info("Fetched {} catalog entities in IdpCatalogEntitiesAsHarnessEntities sync for accountIdentifier = {} "
            + "EntityUid = {} Action = {}",
        backstageCatalogEntities.size(), accountIdentifier, entityUid, action);
    List<Pair<BackstageCatalogEntity, BackstageCatalogEntity>> entitiesList =
        prepareEntitiesForSave(accountIdentifier, backstageCatalogEntities);

    BackstageHarnessSyncRequest.ActionEnum actionEnum = BackstageHarnessSyncRequest.ActionEnum.fromValue(action);

    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      List<BackstageCatalogEntity> entitiesToSave = new ArrayList<>();
      for (Pair<BackstageCatalogEntity, BackstageCatalogEntity> oldNewEntityPair : entitiesList) {
        BackstageCatalogEntity oldEntity = oldNewEntityPair.getFirst();
        BackstageCatalogEntity newEntity = oldNewEntityPair.getSecond();
        if (actionEnum.equals(BackstageHarnessSyncRequest.ActionEnum.CREATE)) {
          entitiesToSave.add(newEntity);
          if (StringUtils.isNotBlank(entityUid)) {
            publishAuditEventForCreate(accountIdentifier, entityUid, newEntity, user);
          }
        } else if (actionEnum.equals(BackstageHarnessSyncRequest.ActionEnum.UPDATE) && oldEntity != null) {
          // Add this later when we get context on the events which is happening on idp-app side.(In case of no updates
          // we are getting events here)
          //          newEntity = fetchUpdatedEntity(accountIdentifier, entityUid, oldEntity, newEntity);
          boolean updated = !oldEntity.getYaml().equals(newEntity.getYaml());
          if (updated) {
            entitiesToSave.add(newEntity);
            if (StringUtils.isNotBlank(entityUid)) {
              publishAuditEventForUpdate(accountIdentifier, oldEntity, newEntity, user);
            }
          }
        } else {
          // upsert case from migration.
          entitiesToSave.add(newEntity);
        }
      }
      backstageCatalogEntityRepository.saveAll(entitiesToSave);
      return true;
    }));

    log.info("Saved {} catalog entities into DB in IdpCatalogEntitiesAsHarnessEntities sync for accountIdentifier = {} "
            + "EntityUid = {} Action = {}",
        backstageCatalogEntities.size(), accountIdentifier, entityUid, action);
    log.info("Synced IDP catalog entities as Harness entities for accountIdentifier = {} EntityUid = {} Action = {}",
        accountIdentifier, entityUid, action);
  }

  private BackstageCatalogEntity fetchUpdatedEntity(String accountIdentifier, String entityUid,
      BackstageCatalogEntity oldBackstageCatalogEntity, BackstageCatalogEntity newBackstageCatalogEntity) {
    Object response = null;
    BackstageCatalogEntity updatedCatalogEntity = newBackstageCatalogEntity;
    log.debug("Starting to fetch updated entity - backstage catalog");
    if (updatedCatalogEntity.getYaml().equals(oldBackstageCatalogEntity.getYaml())) {
      try {
        Thread.sleep(1000);
        response = getGeneralResponse(backstageResourceClient.getCatalogEntityByName(
            accountIdentifier, getEntityUniqueIdForByNameAPI(entityUid)));
      } catch (Exception ex) {
        log.warn("Error in fetching backstage catalog entity by name for account = {} entityUid = {} Error = {}",
            accountIdentifier, entityUid, ex.getMessage(), ex);
      }
      updatedCatalogEntity = readValueForObject(response, BackstageCatalogEntity.class);
      updatedCatalogEntity.setYaml(writeObjectAsYaml(updatedCatalogEntity));
    }
    log.debug("Fetching completed - backstage catalog, yaml - {} ", updatedCatalogEntity.getYaml());

    if (updatedCatalogEntity.getYaml().equals(oldBackstageCatalogEntity.getYaml())) {
      log.debug("Unable to fetch the updated backstage catalog yaml for account - {}, entityUid - {} after 10 secs",
          accountIdentifier, entityUid);
      return newBackstageCatalogEntity;
    }
    return updatedCatalogEntity;
  }

  private void publishAuditEventForCreate(
      String accountIdentifier, String entityUid, BackstageCatalogEntity newEntity, User user) {
    GlobalContextData currentPrincipalContext = GlobalContextManager.get(PrincipalContextData.PRINCIPAL_CONTEXT);
    setUserContext(accountIdentifier, user);

    try {
      boolean producerResult = idpEntityCrudStreamProducer.publishAsyncScoreComputationChangeEventToRedis(
          accountIdentifier, null, entityUid);
      if (!producerResult) {
        log.error("Error in producing event for async score computation. AccountIdentifier = {} "
                + "BackstageCatalogEntityUid = {}",
            accountIdentifier, entityUid);
      }
      if (shouldPublishAudit(newEntity)) {
        outboxService.save(
            new BackstageCatalogEntityCreateEvent(accountIdentifier, newEntity.getEntityUid(), newEntity.getYaml()));
      }
    } catch (Exception e) {
      log.error("Error publishing create audit event for EntityUid {}.", entityUid, e);
    } finally {
      GlobalContextManager.upsertGlobalContextRecord(currentPrincipalContext);
    }
  }

  private void publishAuditEventForUpdate(
      String accountIdentifier, BackstageCatalogEntity oldEntity, BackstageCatalogEntity newEntity, User user) {
    if (shouldPublishAudit(newEntity)) {
      outboxService.save(new BackstageCatalogEntityUpdateEvent(
          accountIdentifier, newEntity.getEntityUid(), oldEntity.getYaml(), newEntity.getYaml()));
    }
    handleEntityDeletionIfRequired(accountIdentifier, oldEntity, newEntity, user);
  }

  private void handleEntityDeletionIfRequired(
      String accountIdentifier, BackstageCatalogEntity oldEntity, BackstageCatalogEntity newEntity, User user) {
    if (GROUP.equals(BackstageCatalogEntityTypes.fromString(oldEntity.getKind()))
        || USER.equals(BackstageCatalogEntityTypes.fromString(oldEntity.getKind()))) {
      Set<Relation> oldRelations = oldEntity.getRelations();
      Set<Relation> newRelations = newEntity.getRelations();
      oldRelations.removeAll(newRelations);
      for (Relation oldRelation : oldRelations) {
        if ("ownerOf".equals(oldRelation.getType())) {
          Target target = oldRelation.getTarget();
          String kind = BackstageCatalogEntityTypes.fromString(target.getKind()).kind;
          String entityUidToDelete = getEntityUniqueId(target.getNamespace(), kind, target.getName());
          handleDeleteAction(accountIdentifier, entityUidToDelete, user);
        }
      }
    }
  }

  private boolean syncInSynchronousMode(String accountIdentifier, String entityUid, String action, User user) {
    try {
      log.info("Syncing IDP catalog entities as Harness entities for accountIdentifier = {} EntityUid = {} Action = {}",
          accountIdentifier, entityUid, action);
      switch (BackstageHarnessSyncRequest.ActionEnum.fromValue(action)) {
        case CREATE:
        case UPDATE:
          handleCreateOrUpdateAction(accountIdentifier, entityUid, action, user);
          break;
        case DELETE:
          handleDeleteAction(accountIdentifier, entityUid, user);
          break;
        default:
          throw new UnexpectedException(
              "Unsupported action for syncing IdpCatalogEntitiesAsHarnessEntities in synchronous mode");
      }
    } catch (Exception ex) {
      log.error("Error in IdpCatalogEntitiesAsHarnessEntities sync for accountIdentifier = {} EntityUid = {} Action = "
              + "{} Error = {}",
          accountIdentifier, entityUid, action, ex.getMessage(), ex);
      return false;
    }
    return true;
  }

  private boolean syncInAsynchronousMode(String accountIdentifier, String entityUid, String action, User user) {
    idpServiceMiscRedisProducer.publishIDPCatalogEntitiesSyncCaptureToRedis(
        accountIdentifier, entityUid, action, user, BackstageHarnessSyncRequest.TypeEnum.ENTITY);
    return true;
  }

  private List<Pair<BackstageCatalogEntity, BackstageCatalogEntity>> prepareEntitiesForSave(
      String accountIdentifier, List<BackstageCatalogEntity> backstageCatalogEntities) {
    List<Pair<BackstageCatalogEntity, BackstageCatalogEntity>> entitesList = new ArrayList<>();

    backstageCatalogEntities.forEach(backstageCatalogEntity -> {
      String entityUid = getEntityUniqueId(backstageCatalogEntity);
      Optional<BackstageCatalogEntity> optionalBackstageCatalogEntity =
          backstageCatalogEntityRepository.findByAccountIdentifierAndEntityUidIgnoreCase(
              accountIdentifier, entityUid.replaceAll("[^a-zA-Z0-9]", "\\\\$0"));
      optionalBackstageCatalogEntity.ifPresentOrElse(backstageCatalogEntityExisting -> {
        entitesList.add(new Pair<>(backstageCatalogEntityExisting, backstageCatalogEntity));
        backstageCatalogEntity.setId(backstageCatalogEntityExisting.getId());
        backstageCatalogEntity.setCreatedAt(backstageCatalogEntityExisting.getCreatedAt());
      }, () -> entitesList.add(new Pair<>(null, backstageCatalogEntity)));

      String entityOwner = BackstageCatalogEntityTypes.getEntityOwner(backstageCatalogEntity);
      if (!isEmpty(entityOwner)) {
        BackstageCatalogEntityTypes.setEntityOwner(backstageCatalogEntity, entityOwner.toLowerCase());
      }
      backstageCatalogEntity.setAccountIdentifier(accountIdentifier);
      backstageCatalogEntity.setEntityUid(entityUid);
      backstageCatalogEntity.setYaml(writeObjectAsYaml(backstageCatalogEntity));
    });
    return entitesList;
  }

  private void handleCreateOrUpdateAction(String accountIdentifier, String entityUid, String action, User user) {
    Object response;
    try {
      response = getGeneralResponse(
          backstageResourceClient.getCatalogEntityByName(accountIdentifier, getEntityUniqueIdForByNameAPI(entityUid)));
    } catch (Exception ex) {
      log.warn("Error in fetching catalog entity by name for account = {} entityUid = {} Error = {}", accountIdentifier,
          entityUid, ex.getMessage(), ex);
      return;
    }

    Map<String, Object> entity = objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {});

    CommonUtils.normalizeSystemField(entity);

    BackstageCatalogEntity backstageCatalogEntity = readValueForObject(entity, BackstageCatalogEntity.class);
    List<BackstageCatalogEntity> backstageCatalogEntities = Collections.singletonList(backstageCatalogEntity);
    backstageCatalogEntities = filter(backstageCatalogEntities);
    syncInternal(accountIdentifier, entityUid, action, backstageCatalogEntities, user);
  }

  private void handleDeleteAction(String accountIdentifier, String entityUid, User user) {
    log.info(
        "Delete action received in IdpCatalogEntitiesAsHarnessEntities sync for accountIdentifier = {}, entityUid = {}",
        accountIdentifier, entityUid);
    Optional<BackstageCatalogEntity> optionalBackstageCatalogEntity =
        backstageCatalogEntityRepository.findByAccountIdentifierAndEntityUidIgnoreCase(
            accountIdentifier, entityUid.replaceAll("[^a-zA-Z0-9]", "\\\\$0"));
    optionalBackstageCatalogEntity.ifPresent(backstageCatalogEntityExisting -> {
      log.info("Found BackstageCatalogEntity for delete, deleting it. AccountIdentifier = {}, entityUid = {}",
          accountIdentifier, entityUid);
      Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        backstageCatalogEntityRepository.delete(backstageCatalogEntityExisting);
        if (shouldPublishAudit(backstageCatalogEntityExisting)) {
          GlobalContextData currentPrincipalContext = GlobalContextManager.get(PrincipalContextData.PRINCIPAL_CONTEXT);
          setUserContext(accountIdentifier, user);
          try {
            outboxService.save(new BackstageCatalogEntityDeleteEvent(accountIdentifier,
                backstageCatalogEntityExisting.getEntityUid(), backstageCatalogEntityExisting.getYaml()));
          } catch (Exception e) {
            log.error("Error publishing audit event for EntityUid {}.", entityUid, e);
          } finally {
            GlobalContextManager.upsertGlobalContextRecord(currentPrincipalContext);
          }
        }
        return true;
      }));
    });
  }

  private void syncScaffolderTasksInternal(String accountIdentifier, long syncFrom, NamespaceEntity namespaceEntity,
      List<BackstageScaffolderTaskListItem> scaffolderTasks) {
    long nextSyncFrom = syncFrom;
    if (isNotEmpty(scaffolderTasks)) {
      List<BackstageScaffolderTaskEntity> scaffolderTasksEntities =
          toEntities(accountIdentifier, scaffolderTasks, scaffolderTaskEntityRepository);
      int batchSize = 100;
      nextSyncFrom = Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        for (int i = 0; i < scaffolderTasksEntities.size(); i += batchSize) {
          List<BackstageScaffolderTaskEntity> batch =
              scaffolderTasksEntities.subList(i, Math.min(i + batchSize, scaffolderTasksEntities.size()));
          int batchNumber = (i / batchSize) + 1;
          scaffolderTaskEntityRepository.saveAll(batch);
          log.info("Saved batch {} of scaffolder tasks for accountIdentifier = {}", batchNumber, accountIdentifier);
        }
        log.info("Successfully synced scaffolder tasks for accountIdentifier = {} syncFrom = {}", accountIdentifier,
            syncFrom);
        return scaffolderTasksEntities.stream()
                   .max(Comparator.comparing(BackstageScaffolderTaskEntity::getLastHeartbeatAt))
                   .orElse(new BackstageScaffolderTaskEntity())
                   .getLastHeartbeatAt()
            + 1;
      }));
    }
    updateScaffolderTasksSyncFrom(namespaceEntity, nextSyncFrom);
    log.info("Updated scaffolder tasks sync from to {} for accountIdentifier = {}", nextSyncFrom, accountIdentifier);
  }

  private void updateScaffolderTasksSyncFrom(NamespaceEntity namespaceEntity, long nextSyncFrom) {
    NamespaceEntity.Metadata metadata = Objects.isNull(namespaceEntity.getMetadata())
        ? NamespaceEntity.Metadata.builder().build()
        : namespaceEntity.getMetadata();
    metadata.setScaffolderTasksSyncFrom(nextSyncFrom);
    namespaceEntity.setMetadata(metadata);
    namespaceRepository.save(namespaceEntity);
  }

  private Map<String, String> fetchAccountLevelVariables(ScopeInfo scopeInfo, Set<String> identifiers) {
    Map<String, String> accountLevelVariables = new HashMap<>();
    if (!isEmpty(identifiers)) {
      try {
        List<VariableResponseDTO> variableResponseDTOList =
            NGRestUtils
                .getResponse(variableClient.getVariablesListV2(scopeInfo.getAccountIdentifier(), null, null, 0, 100,
                    null, false, VariableListRequestDTO.builder().identifiers(identifiers.stream().toList()).build()))
                .getContent();
        variableResponseDTOList.forEach(variableResponseDTO
            -> accountLevelVariables.put(variableResponseDTO.getVariable().getIdentifier(),
                (String) variableResponseDTO.getVariable().getVariableConfig().getValue()));
      } catch (Exception e) {
        log.error("Error occurred while fetching variables: " + identifiers, e);
      }
    }
    return accountLevelVariables;
  }
}

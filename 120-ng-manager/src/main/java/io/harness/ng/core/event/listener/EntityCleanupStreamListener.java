/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.mongo.helper.MongoConstants.ID;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.mongodb.core.BulkOperations.BulkMode.UNORDERED;

import io.harness.annotations.AutoCleanupConfig;
import io.harness.annotations.CleanupTriggerEntity;
import io.harness.beans.ScopeInfo;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.ng.core.event.MessageListener;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
public class EntityCleanupStreamListener implements MessageListener {
  Set<Class<?>> entitiesToCleanUp;
  Map<Class<?>, List<AutoCleanupConfig>> entityToCleanupConfigsMap;
  private ExecutorService executorService;

  private MongoTemplate mongoTemplate;
  int batchSize;

  @Inject
  public EntityCleanupStreamListener(@Named("entity-clean-up-executor") ExecutorService executorService,
      MongoTemplate mongoTemplate, @Named("entities-to-clean-up") Set<Class<?>> entitiesToCleanUp,
      @Named("entity-to-clean-up-configs-map") Map<Class<?>, List<AutoCleanupConfig>> entityToCleanupConfigsMap,
      @Named("entity-clean-up-batch-size") int batchSize) {
    this.executorService = executorService;
    this.mongoTemplate = mongoTemplate;
    this.entitiesToCleanUp = entitiesToCleanUp;
    this.entityToCleanupConfigsMap = entityToCleanupConfigsMap;
    this.batchSize = batchSize;
  }

  @Override
  public boolean handleMessage(Message message) {
    List<Future<Boolean>> futures = new ArrayList<>();
    entitiesToCleanUp.forEach(entityClass -> {
      List<AutoCleanupConfig> entitiesToCleanUp = entityToCleanupConfigsMap.get(entityClass);
      if (entitiesToCleanUp == null) {
        return;
      }
      for (AutoCleanupConfig autoCleanupConfig : entitiesToCleanUp) {
        if (autoCleanupConfig.processDeleteEvents()) {
          Future<Boolean> future = executorService.submit(() -> handleMessage(message, autoCleanupConfig));
          futures.add(future);
        }
      }
    });

    return futures.isEmpty() || futures.stream().allMatch(future -> {
      try {
        return future.get();
      } catch (Exception e) {
        log.error(format("Failed to process future due to error: %s", e.getMessage()), e);
        return false;
      }
    });
  }

  protected boolean handleMessage(Message message, AutoCleanupConfig autoCleanupConfig) {
    Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null && DELETE_ACTION.equals(metadataMap.get(ACTION))) {
      String entityType = metadataMap.get(ENTITY_TYPE);
      for (CleanupTriggerEntity cleanupTriggerEntity : autoCleanupConfig.cleanupTriggers()) {
        if (cleanupTriggerEntity.entityType().equals(entityType)) {
          processDeleteEvent(autoCleanupConfig, message, cleanupTriggerEntity);
        }
      }
    }
    return true;
  }

  private boolean processDeleteEvent(
      AutoCleanupConfig autoCleanupConfig, Message message, CleanupTriggerEntity cleanupTriggerEntity) {
    ScopeInfo scopeInfo = fetchScopeInfoForTheEvent(message, cleanupTriggerEntity.entityType());
    if (scopeInfo == null || isBlank(scopeInfo.getAccountIdentifier()) || isBlank(scopeInfo.getUniqueId())
        || isBlank(cleanupTriggerEntity.identifierField())) {
      return true;
    }
    String collectionName = autoCleanupConfig.collectionName();
    try {
      Criteria criteria = buildCriteriaForDeleteEvent(cleanupTriggerEntity, scopeInfo);

      deleteInBatches(scopeInfo, collectionName, criteria);
      return true;
    } catch (Exception e) {
      log.warn(
          format("Error while deleting %s for eventType [%s] and identifier [%s] : %s", collectionName,
              cleanupTriggerEntity.entityType(), cleanupTriggerEntity.identifierField(), ExceptionUtils.getMessage(e)),
          e);
      return false;
    }
  }

  private Criteria buildCriteriaForDeleteEvent(CleanupTriggerEntity cleanupTriggerEntity, ScopeInfo scopeInfo) {
    Criteria criteria = new Criteria();
    criteria.and(cleanupTriggerEntity.identifierField()).is(scopeInfo.getUniqueId());
    if (isNotBlank(cleanupTriggerEntity.accountIdentifierField()) && isNotBlank(scopeInfo.getAccountIdentifier())) {
      criteria.and(cleanupTriggerEntity.accountIdentifierField()).is(scopeInfo.getAccountIdentifier());
    }
    if (isNotBlank(cleanupTriggerEntity.organizationIdentifierField()) && isNotBlank(scopeInfo.getOrgIdentifier())) {
      criteria.and(cleanupTriggerEntity.organizationIdentifierField()).is(scopeInfo.getOrgIdentifier());
    }
    if (isNotBlank(cleanupTriggerEntity.projectIdentifierField()) && isNotBlank(scopeInfo.getProjectIdentifier())) {
      criteria.and(cleanupTriggerEntity.projectIdentifierField()).is(scopeInfo.getProjectIdentifier());
    }
    return criteria;
  }

  private ScopeInfo fetchScopeInfoForTheEvent(Message message, String entityType) {
    switch (entityType) {
      case ORGANIZATION_ENTITY:
        return fetchScopeForOrgEntity(message);
      case PROJECT_ENTITY:
        return fetchScopeForProjectEntity(message);
      case ACCOUNT_ENTITY:
        return fetchScopeForAccountEntity(message);
      default:
        return fetchScopeForGivenEntity(message);
    }
  }

  private ScopeInfo fetchScopeForGivenEntity(Message message) {
    EntityChangeDTO entityChangeDTO = null;
    try {
      entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (Exception e) {
      log.error(format("Message %s can't be parsed correctly to EntityChangeDTO", e.getMessage()), e);
    }
    return entityChangeDTO == null ? null
                                   : ScopeInfo.builder()
                                         .uniqueId(entityChangeDTO.getIdentifier().getValue())
                                         .accountIdentifier(entityChangeDTO.getAccountIdentifier().getValue())
                                         .orgIdentifier(entityChangeDTO.getOrgIdentifier().getValue())
                                         .projectIdentifier(entityChangeDTO.getProjectIdentifier().getValue())
                                         .build();
  }

  private ScopeInfo fetchScopeForAccountEntity(Message message) {
    AccountEntityChangeDTO accountEntityChangeDTO = null;
    try {
      accountEntityChangeDTO = AccountEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (Exception e) {
      log.error(format("Message %s can't be parsed correctly to AccountEntityChangeDTO", e.getMessage()), e);
    }
    return accountEntityChangeDTO == null ? null
                                          : ScopeInfo.builder()
                                                .uniqueId(accountEntityChangeDTO.getAccountId())
                                                .accountIdentifier(accountEntityChangeDTO.getAccountId())
                                                .build();
  }

  private ScopeInfo fetchScopeForProjectEntity(Message message) {
    ProjectEntityChangeDTO projectEntityChangeDTO = null;
    try {
      projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (Exception e) {
      log.error(format("Message %s can't be parsed correctly to ProjectEntityChangeDTO", e.getMessage()), e);
    }
    return projectEntityChangeDTO == null ? null
                                          : ScopeInfo.builder()
                                                .uniqueId(projectEntityChangeDTO.getIdentifier())
                                                .accountIdentifier(projectEntityChangeDTO.getAccountIdentifier())
                                                .orgIdentifier(projectEntityChangeDTO.getOrgIdentifier())
                                                .build();
  }

  private ScopeInfo fetchScopeForOrgEntity(Message message) {
    OrganizationEntityChangeDTO organizationEntityChangeDTO = null;
    try {
      organizationEntityChangeDTO = OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (Exception e) {
      log.error(format("Message %s can't be parsed correctly to OrganizationEntityChangeDTO", e.getMessage()), e);
    }

    return organizationEntityChangeDTO == null
        ? null
        : ScopeInfo.builder()
              .uniqueId(organizationEntityChangeDTO.getIdentifier())
              .accountIdentifier(organizationEntityChangeDTO.getAccountIdentifier())
              .build();
  }

  protected void deleteInBatches(ScopeInfo scopeInfo, String collectionName, Criteria criteria) {
    List<String> idsToDelete;
    do {
      idsToDelete = findDocumentIdsToDeleteInSingleBatch(scopeInfo, criteria, batchSize, collectionName);
      if (!idsToDelete.isEmpty()) {
        deleteDocumentsBatch(scopeInfo, collectionName, idsToDelete);
      }
    } while (!idsToDelete.isEmpty());
  }

  private List<String> findDocumentIdsToDeleteInSingleBatch(
      ScopeInfo scopeInfo, Criteria criteria, int limit, String collectionName) {
    Query query = new Query(criteria).limit(limit);
    query.fields().include(ID);
    return Failsafe.with(getDeleteRetryPolicy(scopeInfo, collectionName))
        .get(()
                 -> mongoTemplate.find(query, Document.class, collectionName)
                        .stream()
                        .map(document -> document.get(ID).toString())
                        .collect(Collectors.toList()));
  }

  private void deleteDocumentsBatch(ScopeInfo scopeInfo, String collectionName, List<String> idsToDelete) {
    BulkOperations bulkOps = mongoTemplate.bulkOps(UNORDERED, collectionName);
    Query deletionQuery =
        new Query(Criteria.where(ID).in(idsToDelete.stream().map(ObjectId::new).collect(Collectors.toList())));
    bulkOps.remove(deletionQuery);
    Failsafe.with(getDeleteRetryPolicy(scopeInfo, collectionName)).get(() -> bulkOps.execute());
  }

  private RetryPolicy<Object> getDeleteRetryPolicy(ScopeInfo scopeInfo, String collectionName) {
    return PersistenceUtils.getRetryPolicy(format("[Retrying]: Failed deleting {} for account: [%s]; attempt: {}",
                                               collectionName, scopeInfo.getAccountIdentifier()),
        format("[Failed]: Failed {} for account: [%s]; attempt: {}", collectionName, scopeInfo.getAccountIdentifier()));
  }
}

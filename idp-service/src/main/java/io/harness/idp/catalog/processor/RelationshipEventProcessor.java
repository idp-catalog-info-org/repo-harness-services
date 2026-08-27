/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor;

import static io.harness.idp.catalog.utils.Constants.RELATIONSHIP_LOCK_PREFIX;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.RelationshipEventType;
import io.harness.idp.catalog.events.RelationshipProcessingEvent;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.interfaces.AcquiredLock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class RelationshipEventProcessor {
  private final RelationsProcessor relationsProcessor;
  private final CatalogEntityRepository catalogEntityRepository;
  private final ResourceLocker resourceLocker;
  private final ObjectMapper objectMapper;

  @Inject
  public RelationshipEventProcessor(RelationsProcessor relationsProcessor,
      CatalogEntityRepository catalogEntityRepository, ResourceLocker resourceLocker) {
    this.relationsProcessor = relationsProcessor;
    this.catalogEntityRepository = catalogEntityRepository;
    this.resourceLocker = resourceLocker;
    this.objectMapper = new ObjectMapper();
  }

  public void processEvent(RelationshipProcessingEvent event) {
    String entityId = event.getEntityId();
    RelationshipEventType eventType = event.getEventType();

    Optional<CatalogEntity> entityOpt = catalogEntityRepository.findById(entityId);

    if (eventType == RelationshipEventType.ESTABLISH) {
      if (entityOpt.isEmpty()) {
        log.warn("Entity not found for ESTABLISH event, entityId={}", entityId);
        return;
      }
      List<CatalogEntity> referencedEntities = relationsProcessor.establishRelations(entityOpt.get());
      saveReferencedEntitiesWithLocking(referencedEntities);

    } else if (eventType == RelationshipEventType.UPDATE) {
      if (entityOpt.isEmpty()) {
        log.warn("Entity not found for UPDATE event, entityId={}", entityId);
        return;
      }
      CatalogEntity currentEntity = entityOpt.get();
      CatalogEntity existingSnapshot = deserializeSnapshot(event.getExistingEntitySnapshot());
      if (existingSnapshot != null) {
        List<CatalogEntity> referencedEntities = relationsProcessor.updateRelations(existingSnapshot, currentEntity);
        saveReferencedEntitiesWithLocking(referencedEntities);
      }

    } else if (eventType == RelationshipEventType.DISBAND) {
      CatalogEntity deletedSnapshot = deserializeSnapshot(event.getDeletedEntitySnapshot());
      if (deletedSnapshot != null) {
        List<CatalogEntity> referencedEntities = relationsProcessor.disbandRelations(deletedSnapshot);
        saveReferencedEntitiesWithLocking(referencedEntities);
      }

    } else if (eventType == RelationshipEventType.MOVE) {
      if (entityOpt.isEmpty()) {
        log.warn("Entity not found for MOVE event, entityId={}", entityId);
        return;
      }
      if (event.getNewScope() != null) {
        List<CatalogEntity> referencedEntities = relationsProcessor.changeScope(entityOpt.get(), event.getNewScope());
        saveReferencedEntitiesWithLocking(referencedEntities);
      }
    }
  }

  public void saveReferencedEntitiesWithLocking(List<CatalogEntity> referencedEntities) {
    for (CatalogEntity entity : referencedEntities) {
      String lockName = RELATIONSHIP_LOCK_PREFIX + entity.getId();
      AcquiredLock<?> lock = null;
      try {
        lock = resourceLocker.acquireLock(lockName);
        if (lock != null) {
          catalogEntityRepository.save(entity);
        } else {
          log.warn("Could not acquire lock for referenced entity {}, will retry", entity.getId());
          throw new RuntimeException("Failed to acquire lock for entity " + entity.getId());
        }
      } finally {
        if (lock != null) {
          resourceLocker.releaseLock(lock);
        }
      }
    }
  }

  public CatalogEntity deserializeSnapshot(String snapshot) {
    if (snapshot == null || snapshot.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.readValue(snapshot, CatalogEntity.class);
    } catch (Exception e) {
      log.error("Failed to deserialize entity snapshot: {}", e.getMessage(), e);
      return null;
    }
  }

  public long calculateNextRetryTime(int retryCount) {
    long baseDelayMs = 60_000;
    int safeRetryCount = Math.max(retryCount, 1);
    long delay = baseDelayMs * (1L << Math.min(safeRetryCount - 1, 4));
    return System.currentTimeMillis() + delay;
  }
}

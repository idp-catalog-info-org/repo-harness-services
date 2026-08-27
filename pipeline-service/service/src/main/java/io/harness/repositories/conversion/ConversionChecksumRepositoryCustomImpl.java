/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.conversion;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.StoreType;
import io.harness.goconvert.EntityType;
import io.harness.pms.conversion.beans.ConversionChecksum;
import io.harness.pms.conversion.beans.ConversionChecksum.ConversionChecksumKeys;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Custom repository implementation for ConversionChecksum.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ConversionChecksumRepositoryCustomImpl implements ConversionChecksumRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Optional<ConversionChecksum> findByInlineEntity(String accountId, String parentUniqueId, String orgId,
      String projectId, String entityId, EntityType entityType, String versionLabel) {
    Criteria criteria = Criteria.where(ConversionChecksumKeys.accountId)
                            .is(accountId)
                            .and(ConversionChecksumKeys.parentUniqueId)
                            .is(parentUniqueId)
                            .and(ConversionChecksumKeys.entityId)
                            .is(entityId)
                            .and(ConversionChecksumKeys.entityType)
                            .is(entityType)
                            .and(ConversionChecksumKeys.versionLabel)
                            .is(versionLabel)
                            .and(ConversionChecksumKeys.storeType)
                            .is(StoreType.INLINE);
    ConversionChecksum checksum = mongoTemplate.findOne(new Query(criteria), ConversionChecksum.class);
    if (checksum != null) {
      return Optional.of(checksum);
    }
    // Legacy fallback for pre-re-key records (null parentUniqueId), keyed on orgId/projectId.
    Criteria legacyCriteria = legacyScopeCriteria(accountId, orgId, projectId)
                                  .and(ConversionChecksumKeys.entityId)
                                  .is(entityId)
                                  .and(ConversionChecksumKeys.entityType)
                                  .is(entityType)
                                  .and(ConversionChecksumKeys.versionLabel)
                                  .is(versionLabel)
                                  .and(ConversionChecksumKeys.storeType)
                                  .is(StoreType.INLINE);
    return Optional.ofNullable(mongoTemplate.findOne(new Query(legacyCriteria), ConversionChecksum.class));
  }

  @Override
  public Optional<ConversionChecksum> findByRemoteEntity(String accountId, String parentUniqueId, String orgId,
      String projectId, String entityId, String repoURL, String branch, String versionLabel) {
    Criteria criteria = Criteria.where(ConversionChecksumKeys.accountId)
                            .is(accountId)
                            .and(ConversionChecksumKeys.parentUniqueId)
                            .is(parentUniqueId)
                            .and(ConversionChecksumKeys.entityId)
                            .is(entityId)
                            .and(ConversionChecksumKeys.versionLabel)
                            .is(versionLabel)
                            .and(ConversionChecksumKeys.repoURL)
                            .is(repoURL)
                            .and(ConversionChecksumKeys.branch)
                            .is(branch)
                            .and(ConversionChecksumKeys.storeType)
                            .is(StoreType.REMOTE);
    ConversionChecksum checksum = mongoTemplate.findOne(new Query(criteria), ConversionChecksum.class);
    if (checksum != null) {
      return Optional.of(checksum);
    }
    // Legacy fallback for pre-re-key records (null parentUniqueId), keyed on orgId/projectId.
    Criteria legacyCriteria = legacyScopeCriteria(accountId, orgId, projectId)
                                  .and(ConversionChecksumKeys.entityId)
                                  .is(entityId)
                                  .and(ConversionChecksumKeys.versionLabel)
                                  .is(versionLabel)
                                  .and(ConversionChecksumKeys.repoURL)
                                  .is(repoURL)
                                  .and(ConversionChecksumKeys.branch)
                                  .is(branch)
                                  .and(ConversionChecksumKeys.storeType)
                                  .is(StoreType.REMOTE);
    return Optional.ofNullable(mongoTemplate.findOne(new Query(legacyCriteria), ConversionChecksum.class));
  }

  @Override
  public Optional<ConversionChecksum> findAnyByEntity(
      String accountId, String parentUniqueId, String orgId, String projectId, String entityId, EntityType entityType) {
    Criteria criteria = Criteria.where(ConversionChecksumKeys.accountId)
                            .is(accountId)
                            .and(ConversionChecksumKeys.parentUniqueId)
                            .is(parentUniqueId)
                            .and(ConversionChecksumKeys.entityId)
                            .is(entityId)
                            .and(ConversionChecksumKeys.entityType)
                            .is(entityType);
    Query query = new Query(criteria).with(Sort.by(Sort.Direction.ASC, ConversionChecksumKeys.createdAt));
    ConversionChecksum checksum = mongoTemplate.findOne(query, ConversionChecksum.class);
    if (checksum != null) {
      return Optional.of(checksum);
    }
    // Legacy fallback for pre-re-key records (null parentUniqueId), keyed on orgId/projectId.
    Criteria legacyCriteria = legacyScopeCriteria(accountId, orgId, projectId)
                                  .and(ConversionChecksumKeys.entityId)
                                  .is(entityId)
                                  .and(ConversionChecksumKeys.entityType)
                                  .is(entityType);
    Query legacyQuery = new Query(legacyCriteria).with(Sort.by(Sort.Direction.ASC, ConversionChecksumKeys.createdAt));
    return Optional.ofNullable(mongoTemplate.findOne(legacyQuery, ConversionChecksum.class));
  }

  /**
   * Legacy scope criteria (accountId + orgId + projectId) used to match records written before the parentUniqueId
   * re-key. Null org/project are matched with isNull so account/org-level records resolve correctly.
   */
  private Criteria legacyScopeCriteria(String accountId, String orgId, String projectId) {
    Criteria criteria = Criteria.where(ConversionChecksumKeys.accountId).is(accountId);
    if (orgId != null) {
      criteria.and(ConversionChecksumKeys.orgId).is(orgId);
    } else {
      criteria.and(ConversionChecksumKeys.orgId).isNull();
    }
    if (projectId != null) {
      criteria.and(ConversionChecksumKeys.projectId).is(projectId);
    } else {
      criteria.and(ConversionChecksumKeys.projectId).isNull();
    }
    return criteria;
  }

  @Override
  public ConversionChecksum upsert(ConversionChecksum conversionChecksum) {
    Criteria criteria = buildCriteriaForUniqueEntity(conversionChecksum);
    Query query = new Query(criteria);

    long now = System.currentTimeMillis();
    Update update = buildUpsertUpdate(conversionChecksum, now);

    FindAndModifyOptions options = new FindAndModifyOptions().upsert(true).returnNew(true);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("upsert");

    try {
      return Failsafe.with(retryPolicy)
          .get(() -> mongoTemplate.findAndModify(query, update, options, ConversionChecksum.class));
    } catch (DuplicateKeyException ex) {
      // Insert collided with a legacy record (pre-re-key, null parentUniqueId) on the old orgId/projectId unique
      // index. Heal it in place by its legacy scope key, backfilling parentUniqueId so future lookups hit.
      log.info("[CONVERSION]: upsert collided on legacy unique index for entityId={}; healing legacy record in place",
          conversionChecksum.getEntityId());
      Query legacyQuery = new Query(buildLegacyCriteriaForUniqueEntity(conversionChecksum));
      // Plain update, no upsert: the record provably exists, so $set only (upsert could re-insert and re-collide).
      Update healUpdate = commonFieldsUpdate(conversionChecksum, now)
                              .set(ConversionChecksumKeys.parentUniqueId, conversionChecksum.getParentUniqueId());
      return mongoTemplate.findAndModify(
          legacyQuery, healUpdate, new FindAndModifyOptions().returnNew(true), ConversionChecksum.class);
    }
  }

  /**
   * Criteria fields are NOT re-seeded via $setOnInsert: Mongo auto-seeds equality-filter fields on insert, and the
   * same path in both filter and update operator triggers ConflictingUpdateOperators (error 40).
   */
  private Update buildUpsertUpdate(ConversionChecksum conversionChecksum, long now) {
    Update update = commonFieldsUpdate(conversionChecksum, now)
                        .setOnInsert(ConversionChecksumKeys.createdAt, now)
                        .setOnInsert(ConversionChecksumKeys.orgId, conversionChecksum.getOrgId())
                        .setOnInsert(ConversionChecksumKeys.projectId, conversionChecksum.getProjectId());
    // entityType is part of the INLINE criteria (auto-seeded on insert) but not the REMOTE criteria, so REMOTE rows
    // must seed it explicitly — findAnyByEntity and entity_lookup_idx both key on entityType.
    if (conversionChecksum.getStoreType() == StoreType.REMOTE) {
      update.setOnInsert(ConversionChecksumKeys.entityType, conversionChecksum.getEntityType());
    }
    return update;
  }

  /** $set of the fields that change on every write (both the upsert and the legacy heal path). */
  private Update commonFieldsUpdate(ConversionChecksum conversionChecksum, long now) {
    return new Update()
        .set(ConversionChecksumKeys.checksum, conversionChecksum.getChecksum())
        .set(ConversionChecksumKeys.v1Identifier, conversionChecksum.getV1Identifier())
        .set(ConversionChecksumKeys.filePath, conversionChecksum.getFilePath())
        .set(ConversionChecksumKeys.lastUpdatedAt, now);
  }

  @Override
  public ConversionChecksum update(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("update");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), ConversionChecksum.class));
  }

  private Criteria buildCriteriaForUniqueEntity(ConversionChecksum conversionChecksum) {
    Criteria criteria = Criteria.where(ConversionChecksumKeys.accountId)
                            .is(conversionChecksum.getAccountId())
                            .and(ConversionChecksumKeys.parentUniqueId)
                            .is(conversionChecksum.getParentUniqueId());
    return appendEntityIdentityCriteria(criteria, conversionChecksum);
  }

  /**
   * Criteria matching a legacy record by its orgId/projectId scope key (instead of parentUniqueId). Used to heal a
   * pre-re-key record (null parentUniqueId) after an upsert insert collides with it on the old unique index.
   */
  private Criteria buildLegacyCriteriaForUniqueEntity(ConversionChecksum conversionChecksum) {
    Criteria criteria = legacyScopeCriteria(
        conversionChecksum.getAccountId(), conversionChecksum.getOrgId(), conversionChecksum.getProjectId());
    return appendEntityIdentityCriteria(criteria, conversionChecksum);
  }

  /**
   * Append the entity-identity part of the unique key (entityId, storeType, versionLabel, plus repoURL/branch for
   * REMOTE or entityType for INLINE) to a scope criteria. Shared by the parentUniqueId-keyed and legacy-scoped
   * unique-entity lookups, which differ only in their scope prefix.
   */
  private Criteria appendEntityIdentityCriteria(Criteria criteria, ConversionChecksum conversionChecksum) {
    criteria.and(ConversionChecksumKeys.entityId)
        .is(conversionChecksum.getEntityId())
        .and(ConversionChecksumKeys.storeType)
        .is(conversionChecksum.getStoreType());

    if (conversionChecksum.getVersionLabel() != null) {
      criteria.and(ConversionChecksumKeys.versionLabel).is(conversionChecksum.getVersionLabel());
    } else {
      criteria.and(ConversionChecksumKeys.versionLabel).isNull();
    }

    if (conversionChecksum.getStoreType() == StoreType.REMOTE) {
      criteria.and(ConversionChecksumKeys.repoURL)
          .is(conversionChecksum.getRepoURL())
          .and(ConversionChecksumKeys.branch)
          .is(conversionChecksum.getBranch());
    } else {
      criteria.and(ConversionChecksumKeys.entityType).is(conversionChecksum.getEntityType());
    }

    return criteria;
  }

  @Override
  public long deleteByCriteria(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, ConversionChecksum.class).getDeletedCount();
  }

  private RetryPolicy<Object> getRetryPolicy(String operation) {
    return PersistenceUtils.getRetryPolicy(
        String.format("[Retrying]: Failed %s ConversionChecksum; attempt: {}", operation),
        String.format("[Failed]: Failed %s ConversionChecksum; attempt: {}", operation));
  }
}

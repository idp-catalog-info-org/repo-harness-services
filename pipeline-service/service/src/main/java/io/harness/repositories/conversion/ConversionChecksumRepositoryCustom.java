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

import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Custom repository methods for ConversionChecksum.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public interface ConversionChecksumRepositoryCustom {
  /**
   * Find checksum record for inline entity.
   *
   * <p>Queries by parentUniqueId first (movement-stable key). On a miss, falls back to the legacy
   * orgId/projectId key so records written before the parentUniqueId re-key (which have a null
   * parentUniqueId) are still found.
   *
   * @param accountId Account identifier
   * @param parentUniqueId Containing scope's uniqueId (stable across project movement)
   * @param orgId Org identifier (legacy fallback key)
   * @param projectId Project identifier (legacy fallback key)
   * @param entityId Entity identifier
   * @param entityType Entity type
   * @param versionLabel Version label (for TEMPLATE entities; null for others)
   * @return Optional ConversionChecksum
   */
  Optional<ConversionChecksum> findByInlineEntity(String accountId, String parentUniqueId, String orgId,
      String projectId, String entityId, EntityType entityType, String versionLabel);

  /**
   * Find checksum record for remote entity.
   *
   * <p>Queries by parentUniqueId first (movement-stable key). On a miss, falls back to the legacy
   * orgId/projectId key so pre-re-key records (null parentUniqueId) are still found.
   *
   * @param accountId Account identifier
   * @param parentUniqueId Containing scope's uniqueId (stable across project movement)
   * @param orgId Org identifier (legacy fallback key)
   * @param projectId Project identifier (legacy fallback key)
   * @param entityId Entity identifier
   * @param repoURL Repository URL
   * @param branch Branch name
   * @param versionLabel Version label (for TEMPLATE entities; null for others)
   * @return Optional ConversionChecksum
   */
  Optional<ConversionChecksum> findByRemoteEntity(String accountId, String parentUniqueId, String orgId,
      String projectId, String entityId, String repoURL, String branch, String versionLabel);

  /**
   * Find any checksum record for an entity (ignoring versionLabel).
   * Used to look up the shared V1 identifier when converting a new versionLabel of the same template.
   *
   * <p>Queries by parentUniqueId first (movement-stable key). On a miss, falls back to the legacy
   * orgId/projectId key so pre-re-key records (null parentUniqueId) are still found.
   *
   * @param accountId Account identifier
   * @param parentUniqueId Containing scope's uniqueId (stable across project movement)
   * @param orgId Org identifier (legacy fallback key)
   * @param projectId Project identifier (legacy fallback key)
   * @param entityId Entity identifier
   * @param entityType Entity type
   * @return Optional ConversionChecksum
   */
  Optional<ConversionChecksum> findAnyByEntity(
      String accountId, String parentUniqueId, String orgId, String projectId, String entityId, EntityType entityType);

  /**
   * Upsert (insert or update) checksum record.
   *
   * @param conversionChecksum Checksum record to upsert
   * @return Upserted ConversionChecksum
   */
  ConversionChecksum upsert(ConversionChecksum conversionChecksum);

  /**
   * Update checksum record with given criteria and update operations.
   *
   * @param criteria Update criteria
   * @param update Update operations
   * @return Updated ConversionChecksum
   */
  ConversionChecksum update(Criteria criteria, Update update);

  /**
   * Delete checksum records matching the given criteria.
   *
   * @param criteria Delete criteria
   * @return Number of records deleted
   */
  long deleteByCriteria(Criteria criteria);
}

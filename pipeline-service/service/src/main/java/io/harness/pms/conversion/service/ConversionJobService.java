/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.goconvert.EntityType;
import io.harness.pms.conversion.beans.ConversionJobEntity;
import io.harness.pms.conversion.beans.ConversionJobMetricsDTO;
import io.harness.pms.conversion.beans.ConversionStatus;
import io.harness.security.dto.Principal;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing V0 to V1 conversion jobs.
 * Jobs are processed by the ConversionJobIterator (not async executor).
 */
@OwnedBy(HarnessTeam.PIPELINE)
public interface ConversionJobService {
  /**
   * Create a new conversion job in QUEUED status.
   * The iterator will pick it up via nextIteration.
   *
   * @param jobEntity Conversion job entity (with required fields populated)
   * @return Created ConversionJobEntity with UUID
   */
  ConversionJobEntity createJob(ConversionJobEntity jobEntity);

  /**
   * Get conversion job by UUID.
   *
   * @param uuid Job UUID
   * @return Optional ConversionJobEntity
   */
  Optional<ConversionJobEntity> getJobByUuid(String uuid);

  /**
   * Get the most recent conversion job for a given entity by its scope.
   *
   * @param accountId Account identifier
   * @param orgId Organization identifier (nullable for account-scope)
   * @param projectId Project identifier (nullable for account/org-scope)
   * @param entityId Entity identifier
   * @param entityType Entity type (PIPELINE, TEMPLATE, INPUT_SET)
   * @return Optional ConversionJobEntity (most recent)
   */
  Optional<ConversionJobEntity> getJobByEntityScope(
      String accountId, String orgId, String projectId, String entityId, EntityType entityType);

  /**
   * Update conversion job status and metrics.
   *
   * @param uuid Job UUID
   * @param status New status
   * @param metrics Updated metrics
   * @return Updated ConversionJobEntity
   */
  ConversionJobEntity updateJobStatus(String uuid, ConversionStatus status, ConversionJobMetricsDTO metrics);

  /**
   * Retry a FAILED conversion job.
   * Resets retry count, sets status to QUEUED, and sets nextIteration for immediate pickup.
   *
   * @param uuid Job UUID
   * @return Updated ConversionJobEntity in QUEUED status
   */
  ConversionJobEntity retryJob(String uuid);

  /**
   * Get all direct child jobs for a given parent job.
   * Used to populate entityReferences in the response for BATCH/PROJECT jobs.
   *
   * @param parentJobId Parent job UUID
   * @return List of child ConversionJobEntity
   */
  List<ConversionJobEntity> getChildJobs(String parentJobId);

  /**
   * Update the triggerPrincipal on a job and all its children recursively.
   * Used on retry to re-capture the retrying admin's identity.
   */
  void updateTriggerPrincipal(String uuid, Principal principal);

  /**
   * Delete conversion checksum records by scope.
   *
   * @param accountId Account identifier (required)
   * @param orgId Organization identifier (optional)
   * @param projectId Project identifier (optional)
   * @param entityId Entity identifier (optional)
   * @param entityType Entity type (required when entityId is provided)
   * @param versionLabel Version label (optional, for TEMPLATE entities)
   * @return Number of records deleted
   */
  long deleteChecksums(
      String accountId, String orgId, String projectId, String entityId, EntityType entityType, String versionLabel);
}

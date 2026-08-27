/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.goconvert.EntityType;
import io.harness.iterator.interfaces.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.security.dto.Principal;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Entity for storing V0 to V1 conversion job metadata.
 * TTL: 7 days after creation.
 */
@OwnedBy(PIPELINE)
@Data
@Builder
@FieldNameConstants(innerTypeName = "ConversionJobEntityKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "conversionJobs", noClassnameStored = true)
@Document("conversionJobs")
@TypeAlias("conversionJobs")
@HarnessEntity(exportable = false)
public class ConversionJobEntity implements PersistentRegularIterable, UuidAware, CreatedAtAware {
  public static final Long KEEP_JOB_IN_DB_DAYS = 14L;
  public static final int DEFAULT_MAX_RETRIES = 3;

  /**
   * MongoDB indexes for efficient querying.
   */
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_createdAt_idx")
                 .field(ConversionJobEntityKeys.accountId)
                 .descSortField(ConversionJobEntityKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_status_createdAt_idx")
                 .field(ConversionJobEntityKeys.accountId)
                 .field(ConversionJobEntityKeys.status)
                 .descSortField(ConversionJobEntityKeys.createdAt)
                 .build())
        // Iterator picks up jobs by nextIteration + status
        .add(SortCompoundMongoIndex.builder()
                 .name("nextIteration_status_idx")
                 .field(ConversionJobEntityKeys.status)
                 .ascSortField(ConversionJobEntityKeys.nextIteration)
                 .build())
        // Parent lookups: find children by parentJobId + status
        .add(CompoundMongoIndex.builder()
                 .name("parentJobId_status_idx")
                 .field(ConversionJobEntityKeys.parentJobId)
                 .field(ConversionJobEntityKeys.status)
                 .build())
        // Entity scope lookup: find most recent job for a given entity
        .add(SortCompoundMongoIndex.builder()
                 .name("entity_scope_lookup_idx")
                 .field(ConversionJobEntityKeys.accountId)
                 .field(ConversionJobEntityKeys.orgId)
                 .field(ConversionJobEntityKeys.projectId)
                 .field(ConversionJobEntityKeys.entityType)
                 .field(ConversionJobEntityKeys.entityIdentifier)
                 .descSortField(ConversionJobEntityKeys.createdAt)
                 .build())
        // Dedup lookup: find completed job for same entity within same conversion run
        .add(CompoundMongoIndex.builder()
                 .name("rootJobId_entity_dedup_idx")
                 .field(ConversionJobEntityKeys.rootJobId)
                 .field(ConversionJobEntityKeys.entityIdentifier)
                 .field(ConversionJobEntityKeys.entityType)
                 .field(ConversionJobEntityKeys.status)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id String uuid;

  @NotNull ConversionStatus status;

  @NotNull String accountId;

  String orgId;

  String projectId;

  @NotNull ConversionActionType actionType;

  @NotNull EntityType entityType;

  // Single entity identifier (for SINGLE action type)
  String entityIdentifier;

  // Full entity reference details for SINGLE action type (storeType, git metadata, etc.)
  EntityIdentifierDTO entityReference;

  // For INPUT_SET: the pipeline this input set belongs to
  String pipelineIdentifier;

  // Parent job ID for child jobs (SINGLE children of BATCH/PROJECT, or template children of pipeline)
  String parentJobId;

  // Root job ID — same for all jobs in a single conversion run hierarchy (null for root itself)
  String rootJobId;

  // List of entity references (for BATCH action type)
  List<EntityIdentifierDTO> entityReferences;

  // Whether this job has expanded (created children) — prevents re-expansion
  @Builder.Default Boolean expanded = false;

  // V0 entity metadata — populated in Phase 1, used in Phase 2, $unset on final status
  EntityMetadata entityMetadata;

  // Checksum of v0Yaml — used for skip detection
  String v0YamlChecksum;

  // Force re-conversion even if checksum is unchanged; updates existing V1 entities in place
  @Builder.Default Boolean forceReconvert = false;

  // Whether this entity itself has been converted (V0→V1).
  // Used for PIPELINE SINGLE jobs to separate "convert self" from "wait for input set children".
  @Builder.Default Boolean yamlConverted = false;

  // The newly created V1 entity identifier (e.g. "my_pipeline_v1_a3f2")
  String v1Identifier;

  // Job depth in the tree (root = 0, children = parent.depth + 1)
  @Builder.Default Integer depth = 0;

  // Total number of child jobs created
  @Builder.Default Integer totalChildJobs = 0;

  ConversionJobMetricsDTO conversionMetrics;

  List<ConversionErrorDetail> conversionErrors;

  List<ConversionNodeSummary> conversionResults;

  // The principal of the user who triggered this conversion (admin with branch protection bypass)
  Principal triggerPrincipal;

  // Retry tracking
  @Builder.Default Integer retryCount = 0;

  @Builder.Default Integer maxRetries = DEFAULT_MAX_RETRIES;

  String lastFailureReason;

  String errorMessage;

  @CreatedDate long createdAt;

  @LastModifiedDate @NonFinal @Setter Long lastUpdatedAt;

  Long startTs;

  Long endTs;

  // Iterator field — controls when the iterator picks up this job
  @NonFinal @Setter Long nextIteration;

  @FdIndex @Setter @NonFinal String uniqueId;
  @FdIndex @Setter @NonFinal String parentUniqueId;

  // TTL: 14 days
  @Builder.Default
  @FdTtlIndex
  Date validUntil = Date.from(OffsetDateTime.now().plusDays(KEEP_JOB_IN_DB_DAYS).toInstant());

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }
}

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
import io.harness.gitsync.beans.StoreType;
import io.harness.goconvert.EntityType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.mongodb.BasicDBObject;
import dev.morphia.annotations.Entity;
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
 * Tracks V0 to V1 conversion checksums and their corresponding V1 identifiers.
 *
 * <p>Design:
 * - ONE record per V0 entity (not per V1 entity)
 * - Maps V0 YAML checksum → V1 entity identifier
 * - Enables deduplication: same V0 checksum → reuse same V1 entity
 * - Enables re-conversion: different V0 checksum → create new V1 entity with new identifier
 * - Record is updated when V0 entity changes (not inserted)
 *
 * <p>Examples:
 *   Initial: { entityId: "template_x", checksum: "abc123", v1Identifier: "template_x_v1" }
 *   After V0 modification: { entityId: "template_x", checksum: "xyz789", v1Identifier: "template_x_v1_1" }
 *   After another modification: { entityId: "template_x", checksum: "def456", v1Identifier: "template_x_v1_2" }
 *
 * <p>V1 entities created: template_x_v1, template_x_v1_1, template_x_v1_2 (all exist)
 * Checksum records: Only 1 record (always points to latest conversion)
 */
@OwnedBy(PIPELINE)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "ConversionChecksumKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "conversionChecksums", noClassnameStored = true)
@Document("conversionChecksums")
@TypeAlias("conversionChecksums")
@HarnessEntity(exportable = false)
public class ConversionChecksum implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList
        .<MongoIndex>builder()
        // Unique index for inline entities. Keyed on parentUniqueId (the containing scope's uniqueId), which is
        // stable across project movement, instead of orgId/projectId strings which change when an entity moves.
        .add(CompoundMongoIndex.builder()
                 .name("unique_inline_entity_parent_idx")
                 .unique(true)
                 .field(ConversionChecksumKeys.accountId)
                 .field(ConversionChecksumKeys.parentUniqueId)
                 .field(ConversionChecksumKeys.entityId)
                 .field(ConversionChecksumKeys.entityType)
                 .field(ConversionChecksumKeys.versionLabel)
                 .partialFilterExpression(new BasicDBObject(ConversionChecksumKeys.storeType, StoreType.INLINE.name()))
                 .build())
        // Unique index for remote entities (branch-aware), keyed on parentUniqueId for movement stability.
        .add(CompoundMongoIndex.builder()
                 .name("unique_remote_entity_parent_idx")
                 .unique(true)
                 .field(ConversionChecksumKeys.accountId)
                 .field(ConversionChecksumKeys.parentUniqueId)
                 .field(ConversionChecksumKeys.entityId)
                 .field(ConversionChecksumKeys.versionLabel)
                 .field(ConversionChecksumKeys.repoURL)
                 .field(ConversionChecksumKeys.branch)
                 .partialFilterExpression(new BasicDBObject(ConversionChecksumKeys.storeType, StoreType.REMOTE.name()))
                 .build())
        // Lookup by entity
        .add(CompoundMongoIndex.builder()
                 .name("entity_lookup_idx")
                 .field(ConversionChecksumKeys.accountId)
                 .field(ConversionChecksumKeys.entityId)
                 .field(ConversionChecksumKeys.entityType)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id String uuid;

  // Scope identifiers
  @NotNull String accountId;
  String orgId;
  String projectId;

  // V0 entity identifier
  @NotNull String entityId;
  @NotNull EntityType entityType;
  // Template version label (only for TEMPLATE entities with multiple versions)
  String versionLabel;
  @NotNull StoreType storeType;

  // Git metadata (REMOTE entities only)
  String repoURL;
  String filePath;
  String branch;

  // V0 YAML checksum (SHA-256)
  @NotNull String checksum;

  // V1 entity identifier created for this V0 entity
  // Updated when V0 entity changes and requires re-conversion
  // Pattern: template_x_v1, template_x_v1_1, template_x_v1_2, ...
  @NotNull String v1Identifier;

  @FdIndex @Setter @NonFinal String uniqueId;
  @FdIndex @Setter @NonFinal String parentUniqueId;

  // Timestamps
  @Setter @NonFinal @SchemaIgnore @CreatedDate long createdAt;
  @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate long lastUpdatedAt;
}

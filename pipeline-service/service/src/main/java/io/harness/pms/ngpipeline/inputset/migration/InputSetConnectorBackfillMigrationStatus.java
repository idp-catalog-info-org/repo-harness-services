/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.migration;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Migration status tracking entity for the InputSet Git connector backfill migration.
 * This entity tracks progress and allows the migration to resume from where it left off.
 */
@OwnedBy(PIPELINE)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "InputSetConnectorBackfillMigrationStatusKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "inputSetConnectorBackfillMigrationStatus", noClassnameStored = true)
@Document("inputSetConnectorBackfillMigrationStatus")
@TypeAlias("inputSetConnectorBackfillMigrationStatus")
public class InputSetConnectorBackfillMigrationStatus implements PersistentEntity {
  @Id @dev.morphia.annotations.Id String id;

  Long lastProcessedTimestamp;

  String lastProcessedUuid;

  Boolean migrationCompleted;

  Long totalProcessed;
  Long totalGitCalls;
}

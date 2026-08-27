/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Migration status tracking entity for the Pipeline connector scope fix migration.
 * This entity tracks progress and allows the migration to resume from where it left off.
 */
@OwnedBy(PIPELINE)
@Data
@Builder
@FieldNameConstants(innerTypeName = "PipelineConnectorScopeMigrationStatusKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "pipelineConnectorScopeMigrationStatus", noClassnameStored = true)
@Document("pipelineConnectorScopeMigrationStatus")
@TypeAlias("pipelineConnectorScopeMigrationStatus")
public class PipelineConnectorScopeMigrationStatus implements PersistentEntity {
  @Id @dev.morphia.annotations.Id String id;

  Long lastProcessedTimestamp;

  Boolean migrationCompleted;
}

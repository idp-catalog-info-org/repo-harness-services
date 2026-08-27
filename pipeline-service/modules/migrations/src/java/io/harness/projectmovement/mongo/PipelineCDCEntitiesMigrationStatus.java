/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.projectmovement.mongo;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Data
@Builder
@FieldNameConstants(innerTypeName = "PipelineCDCEntitiesMigrationStatusKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "cdcEntitiesMigrationStatus", noClassnameStored = true)
@Document("cdcEntitiesMigrationStatus")
@TypeAlias("pipelineCdcEntitiesMigrationStatus")
public class PipelineCDCEntitiesMigrationStatus implements PersistentEntity {
  @Id @dev.morphia.annotations.Id String id;
  @NotEmpty @FdUniqueIndex String entityClassName;
  Boolean cdcMigrationCompleted;
  @NotEmpty @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
}

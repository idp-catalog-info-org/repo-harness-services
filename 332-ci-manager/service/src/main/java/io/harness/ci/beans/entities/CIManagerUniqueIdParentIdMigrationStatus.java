/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.beans.entities;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;

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

@OwnedBy(CI)
@Data
@Builder
@FieldNameConstants(innerTypeName = "CIManagerUniqueIdParentIdMigrationStatusKeys")
@StoreIn(DbAliases.CIMANAGER)
@Entity(value = "uniqueIdParentIdMigrationStatus", noClassnameStored = true)
@Document("uniqueIdParentIdMigrationStatus")
@TypeAlias("uniqueIdParentIdMigrationStatus")
public class CIManagerUniqueIdParentIdMigrationStatus {
  @Id @dev.morphia.annotations.Id String id;
  @NotEmpty @FdUniqueIndex String entityClassName;
  Boolean uniqueIdMigrationCompleted;
  Boolean parentIdMigrationCompleted;
  Boolean orphanEntityParentIdMigrationCompleted;
  Boolean indexCreationCompleted;
  @NotEmpty @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
}
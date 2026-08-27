/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.beans.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.interfaces.PersistentIterable;
import io.harness.iterator.interfaces.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.spec.server.idp.v1.model.User;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "NamespaceKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "backstageNamespace", noClassnameStored = true)
@Document("backstageNamespace")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class NamespaceEntity implements PersistentIterable, PersistentRegularIterable {
  @Id @org.mongodb.morphia.annotations.Id private String id;
  @FdUniqueIndex private String accountIdentifier;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @FdIndex Long nextIteration;
  private boolean isDeleted;
  private long deletedAt;
  private Metadata metadata;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return this.nextIteration;
  }

  @Override
  public String getUuid() {
    return this.id;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldNameConstants(innerTypeName = "NamespaceMetadataKeys")
  public static class Metadata {
    private long scaffolderTasksSyncFrom;
    private boolean catalogCustomPropertiesEnabled;
    private boolean migrateCatalogEntitiesFromBackstageToHarnessCompleted;
    private boolean userGroupSyncCompleted;
    private boolean postgresIdpV2MigrationCompleted;
    @JsonIgnore private IdpV2MigrationInfo idpV2MigrationInfo;
    private boolean idpV2FFState;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldNameConstants(innerTypeName = "IdpV2MigrationInfoKeys")
    public static class IdpV2MigrationInfo {
      private boolean migrateDefaultToAccountNamespaceInBackstageCompleted;
      private long migrateDefaultToAccountNamespaceInBackstageFrom;
      private boolean migrateDefaultToAccountNamespaceInDependentsCompleted;
      private long migrateDefaultToAccountNamespaceInDependentsFrom;
      private boolean migrateWorkflowFormContextDataCompleted;
      private long migrateWorkflowFormContextDataFrom;
      private boolean populateQueryableEntityRefInCatalogCompleted;
      private long populateQueryableEntityRefInCatalogFrom;
      @JsonIgnore private MigrateScopeInfo migrateScopeInfo;

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      @FieldNameConstants(innerTypeName = "MigrateScopeInfoKeys")
      public static class MigrateScopeInfo {
        private boolean isActive;
        private String request;
        private User updatedBy;
        private long updatedAt;
      }
    }
  }
}

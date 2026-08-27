/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dataretention.entity;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.entity.beans.RetentionFileData;
import io.harness.dataretention.entity.beans.RetentionFileData.RetentionFileDataKeys;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.UtilityClass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/*
 * This entity is used to store the metadata of the objects stored in the object store
 * We store 4 collections in object store per pipeline execution, so we will have one record of this entity per
 * execution This entity is also used while reading objects from the object store, which checks if this metadata is
 * present then the object is in object store, otherwise not
 */
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.PMS)
@FieldNameConstants(innerTypeName = "ExecutionRetentionMetadataKeys")
@Entity(value = "executionRetentionMetadata")
@Document(value = "executionRetentionMetadata")
@TypeAlias("executionRetentionMetadata")
@OwnedBy(HarnessTeam.PIPELINE)
@HarnessEntity(exportable = true)
@Persistent
public class ExecutionRetentionMetadata implements PersistentEntity {
  @Setter @NonFinal @Id @dev.morphia.annotations.Id String uuid;
  @FdUniqueIndex @NotNull String planExecutionId;
  @NotNull String accountId;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
  Long endTs;
  String bucketName;
  List<RetentionFileData> retentionFileData;
  String parentUniqueId;
  String pipelineIdentifier;
  String orgIdentifier;
  String projectIdentifier;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("retentionFile_uuid_collectionName_idx")
                 .field(ExecutionRetentionMetadataKeys.retentionFileUUID)
                 .field(ExecutionRetentionMetadataKeys.retentionFileCollectionName)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_endTs_idx")
                 .field(ExecutionRetentionMetadataKeys.accountId)
                 .field(ExecutionRetentionMetadataKeys.endTs)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("retentionFile_uuid_collection_collectionName_idx")
                 .field(ExecutionRetentionMetadataKeys.retentionFileUUID)
                 .field(ExecutionRetentionMetadataKeys.retentionFileCollection)
                 .field(ExecutionRetentionMetadataKeys.retentionFileCollectionName)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("parentUniqueId_idx")
                 .field(ExecutionRetentionMetadataKeys.parentUniqueId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("retentionFile_collection_idx")
                 .field(ExecutionRetentionMetadataKeys.retentionFileCollection)
                 .build())
        .build();
  }

  @UtilityClass
  public static class ExecutionRetentionMetadataKeys {
    public static final String retentionFileUUID =
        ExecutionRetentionMetadataKeys.retentionFileData + "." + RetentionFileDataKeys.uuid;
    public static final String retentionFileCollectionName =
        ExecutionRetentionMetadataKeys.retentionFileData + "." + RetentionFileDataKeys.collectionName;
    public static final String retentionFileCollection =
        ExecutionRetentionMetadataKeys.retentionFileData + "." + RetentionFileDataKeys.collection;
  }
}

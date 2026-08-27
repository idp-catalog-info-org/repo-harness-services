/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.beans.entities.artifacts;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "ArtifactDetailsKeys")
@StoreIn(DbAliases.CIMANAGER)
@Entity(value = "artifactDetails", noClassnameStored = true)
@Document("artifactDetails")
@TypeAlias("artifactDetails")
@RecasterAlias("io.harness.ci.beans.entities.ArtifactDetails")
@HarnessEntity(exportable = false)
public class ArtifactDetails {
  @Id @dev.morphia.annotations.Id String id;
  @NotNull String accountId;
  @NotNull String orgIdentifier;
  @NotNull String projectIdentifier;
  @NotNull String pipelineExecutionId;
  @NotNull String pipelineIdentifier;
  @NotNull String stageExecutionId;
  @NotNull String stepExecutionId;
  @NotNull String type;
  @NotNull List<ArtifactMetadata> artifactMetadataList;
  String parentUniqueId;
  @NotNull @CreatedDate @Builder.Default long createdAt;
  @NotNull @LastModifiedDate @Builder.Default long lastUpdatedAt;

  @UtilityClass
  public static class ArtifactDetailsKeysAdditional {
    public static final String imagePath = "artifactMetadataList.imagePath";
    public static final String tag = "artifactMetadataList.tag";
    public static final String digest = "artifactMetadataList.digest";
    public static final String bucketName = "artifactMetadataList.bucketName";
    public static final String filePath = "artifactMetadataList.filePath";
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder().name("accountId_1").field(ArtifactDetailsKeys.accountId).build())
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifier_imagePath_tag")
                 .field(ArtifactDetailsKeys.accountId)
                 .field(ArtifactDetailsKeysAdditional.imagePath)
                 .field(ArtifactDetailsKeysAdditional.tag)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifier_imagePath_digest")
                 .field(ArtifactDetailsKeys.accountId)
                 .field(ArtifactDetailsKeysAdditional.imagePath)
                 .field(ArtifactDetailsKeysAdditional.digest)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifier_bucketName_filePath")
                 .field(ArtifactDetailsKeys.accountId)
                 .field(ArtifactDetailsKeysAdditional.bucketName)
                 .field(ArtifactDetailsKeysAdditional.filePath)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifier_file_digest")
                 .field(ArtifactDetailsKeys.accountId)
                 .field(ArtifactDetailsKeysAdditional.digest)
                 .build())
        .build();
  }
}

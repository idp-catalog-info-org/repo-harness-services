/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.gitmetadata;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UniqueIdAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/*
 * This entity is used to store the git metadata like repo and branch name per account/project/org/pipeline combination
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.PMS)
@FieldNameConstants(innerTypeName = "PipelineExecutionGitMetadataKeys")
@Entity(value = "pipelineExecutionGitMetadata")
@Document(value = "pipelineExecutionGitMetadata")
@TypeAlias("pipelineExecutionGitMetadata")
@OwnedBy(HarnessTeam.PIPELINE)
@HarnessEntity(exportable = true)
@Persistent
public class PipelineExecutionGitMetadata implements PersistentEntity, UuidAware, UniqueIdAware {
  @Setter @NonFinal @Id @dev.morphia.annotations.Id String uuid;

  @Setter @NonFinal String uniqueId;
  @Setter @NonFinal String parentUniqueId;

  String accountIdentifier;
  @Deprecated String orgIdentifier;
  @Deprecated String projectIdentifier;
  String pipelineIdentifier;
  String repoName;
  List<String> branch;

  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_pipelineId_repoName_idx")
                 .field(PipelineExecutionGitMetadataKeys.accountIdentifier)
                 .field(PipelineExecutionGitMetadataKeys.parentUniqueId)
                 .field(PipelineExecutionGitMetadataKeys.pipelineIdentifier)
                 .field(PipelineExecutionGitMetadataKeys.repoName)
                 .build())
        .build();
  }
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.branchsequence;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
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
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CI)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "PipelineBranchSequenceKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "pipelineBranchSequence", noClassnameStored = true)
@Document("pipelineBranchSequence")
@TypeAlias("pipelineBranchSequence")
@HarnessEntity(exportable = true)
public class PipelineBranchSequence implements PersistentEntity, UuidAware, UniqueIdAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_branch_counter_idx")
                 .unique(true)
                 .field(PipelineBranchSequenceKeys.accountIdentifier)
                 .field(PipelineBranchSequenceKeys.orgIdentifier)
                 .field(PipelineBranchSequenceKeys.projectIdentifier)
                 .field(PipelineBranchSequenceKeys.pipelineIdentifier)
                 .field(PipelineBranchSequenceKeys.normalizedRepoUrl)
                 .field(PipelineBranchSequenceKeys.branch)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("pipeline_cleanup_idx")
                 .field(PipelineBranchSequenceKeys.accountIdentifier)
                 .field(PipelineBranchSequenceKeys.orgIdentifier)
                 .field(PipelineBranchSequenceKeys.projectIdentifier)
                 .field(PipelineBranchSequenceKeys.pipelineIdentifier)
                 .build())
        .build();
  }

  @Setter @NonFinal @Id @dev.morphia.annotations.Id String uuid;
  @Setter @NonFinal @FdIndex String uniqueId;
  @FdIndex String parentUniqueId;
  @NotEmpty String accountIdentifier;
  @NotEmpty String orgIdentifier;
  @NotEmpty String projectIdentifier;
  @NotEmpty String pipelineIdentifier;
  @NotEmpty String normalizedRepoUrl;
  @NotEmpty String branch;
  long sequenceId;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
}

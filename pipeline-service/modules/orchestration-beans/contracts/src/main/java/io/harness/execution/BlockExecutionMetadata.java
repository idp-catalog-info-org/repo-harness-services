/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.UniqueIdAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.Wither;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Value
@Builder
@FieldNameConstants(innerTypeName = "BlockExecutionKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "blockExecutions", noClassnameStored = true)
@Document("blockExecutions")
@TypeAlias("BlockExecutionMetadata")
public class BlockExecutionMetadata implements UniqueIdAware {
  String accountId;
  @Deprecated String orgId;
  @Deprecated String projectId;
  String pipelineId;
  @Setter @NonFinal @FdIndex String uniqueId;
  @FdIndex String parentUniqueId;

  @Wither @Id @dev.morphia.annotations.Id String uuid;
  @Wither @CreatedDate Long createdAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("parentUniqueId_pipelineId_idx")
                 .field(BlockExecutionKeys.parentUniqueId)
                 .field(BlockExecutionKeys.pipelineId)
                 .build())
        .build();
  }
}

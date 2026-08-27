/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.annotations;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CI)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "PipelineAnnotationEntityKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "pipelineAnnotations", noClassnameStored = true)
@Document("pipelineAnnotations")
@TypeAlias("pipelineAnnotations")
@HarnessEntity(exportable = true)
public class PipelineAnnotationEntity implements PersistentEntity {
  public static final long TTL_MONTHS = 6;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_idx")
                 .field(PipelineAnnotationEntityKeys.planExecutionId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_planExecutionId_contextId_unique_idx")
                 .unique(true)
                 .field(PipelineAnnotationEntityKeys.accountId)
                 .field(PipelineAnnotationEntityKeys.planExecutionId)
                 .field(PipelineAnnotationEntityKeys.contextId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_contextId_idx")
                 .field(PipelineAnnotationEntityKeys.planExecutionId)
                 .field(PipelineAnnotationEntityKeys.contextId)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id private String id;

  private String accountId;
  private String orgId;
  private String projectId;
  private String pipelineId;
  private String parentUniqueId;
  private String planExecutionId;
  private String stageExecutionId;
  private String stepId;

  private String contextId;

  // Annotation data fields
  private String style;
  private String summary;
  private Integer priority;
  private Long createdAt;

  @LastModifiedDate private Long lastUpdatedAt;

  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());
}

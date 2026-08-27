/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.beans.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(HarnessTeam.CI)
@FieldNameConstants(innerTypeName = "PipelineModuleInfoEntityKeys")
@StoreIn(DbAliases.CIMANAGER)
@StoreIn(DbAliases.IACM_MANAGER)
@Entity(value = "pipelineModuleInfo", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("pipelineModuleInfo")
@TypeAlias("pipelineModuleInfo")
@HarnessEntity(exportable = true)
public class PipelineModuleInfoEntity implements PersistentEntity, UuidAware {
  @Id @dev.morphia.annotations.Id String uuid;
  private String accountIdentifier;
  private String orgIdentifier;
  private String projIdentifier;
  private String pipelineIdentifier;
  private String planExecutionId;

  @Builder.Default
  private List<io.harness.ci.beans.entities.StageModuleInfoEntity> stageModuleInfoList = new ArrayList<>();

  private Long createdAt;
  private Long updatedAt;
  private String parentUniqueId;

  @UtilityClass
  public static class PipelineModuleInfoEntityKeysAdditional {
    public static final String stageExecutionTag = "stageModuleInfoList.stageExecutionId";
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("plan_execution_id")
                 .field(PipelineModuleInfoEntityKeys.planExecutionId)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("stage_execution_id")
                 .field(PipelineModuleInfoEntityKeysAdditional.stageExecutionTag)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("stage_and_plan_execution_id")
                 .field(PipelineModuleInfoEntityKeysAdditional.stageExecutionTag)
                 .field(PipelineModuleInfoEntityKeys.planExecutionId)
                 .unique(true)
                 .build())
        .build();
  }
}

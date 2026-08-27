/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinedelete.beans.entity;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.iterator.interfaces.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UniqueIdAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import javax.validation.constraints.NotNull;
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
 * This entity will be used for the pipeline delete event iterator which processes the pipeline delete event and
 * handles delete for all related entities.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.PMS)
@FieldNameConstants(innerTypeName = "PipelineDeleteProcessorIteratorEntityKeys")
@Entity(value = "pipelineDeleteProcessorIterator")
@Document(value = "pipelineDeleteProcessorIterator")
@TypeAlias("pipelineDeleteProcessorIterator")
@OwnedBy(HarnessTeam.PIPELINE)
@HarnessEntity(exportable = false)
@Persistent
public class PipelineDeleteProcessorIteratorEntity
    implements PersistentEntity, PersistentRegularIterable, UniqueIdAware {
  @Id @dev.morphia.annotations.Id String uuid;

  @NotNull String accountIdentifier;
  @NotNull @Deprecated String orgIdentifier;
  @NotNull @Deprecated String projectIdentifier;
  @NotNull String pipelineIdentifier;

  @Builder.Default Boolean retainPipelineExecutionDetailsAfterDelete = Boolean.FALSE;

  String parentUniqueId;
  @NonFinal @Setter String uniqueId;

  @NonFinal Long nextIteration;
  @FdIndex @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;

  public boolean isRetainPipelineExecutionDetailsAfterDelete() {
    return Boolean.TRUE.equals(retainPipelineExecutionDetailsAfterDelete);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }
}

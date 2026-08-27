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
import io.harness.execution.gitmetadata.beans.ExecutionGitMetadataReconciliationStatus;
import io.harness.iterator.interfaces.PersistentRegularIterable;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import lombok.Builder;
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
 * This entity is required to migrate old records from PipelineExecutionSummaryEntity to PipelineExecutionGitMetadata
 * It will sync the records upto the specified time in syncUntil, which compares the endTs of the execution
 * It will iterate over PipelineExecutionSummaryEntity collection
 * This entity can also be used to do reconciliation to sync any missing data in future
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.PMS)
@FieldNameConstants(innerTypeName = "ExecutionGitMetadataReconciliationEntityKeys")
@Entity(value = "executionGitMetadataReconciliation")
@Document(value = "executionGitMetadataReconciliation")
@TypeAlias("executionGitMetadataReconciliation")
@OwnedBy(HarnessTeam.PIPELINE)
@HarnessEntity(exportable = false)
@Persistent
public class ExecutionGitMetadataReconciliationEntity implements PersistentEntity, PersistentRegularIterable {
  @Id @dev.morphia.annotations.Id String uuid;

  /*
   * The data will be migrated from syncCompletedUntil upto syncUntil, which compares the endTs of the execution
   */
  @NonFinal Long syncCompletedUntil;
  @NonFinal Long syncUntil;

  @NonFinal Long nextIteration;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
  @Builder.Default
  ExecutionGitMetadataReconciliationStatus status = ExecutionGitMetadataReconciliationStatus.IN_PROGRESS;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }
}

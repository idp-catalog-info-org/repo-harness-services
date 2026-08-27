/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.reconciliation.entity;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.iterator.interfaces.PersistentRegularIterable;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationDB;
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationStatus;

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
 * This entity is required to sync old data present in mongoDB to object store and elastic both using a single job
 * It will sync the records upto the specified time in syncUntil, which compares the endTs of the execution
 * It will iterate over PipelineExecutionSummaryEntity collection
 * This entity can also be used to do reconciliation to sync any missing data in future
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DATA_RETENTION, HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.PMS)
@FieldNameConstants(innerTypeName = "ExecutionRetentionReconciliationEntityKeys")
@Entity(value = "executionRetentionReconciliation")
@Document(value = "executionRetentionReconciliation")
@TypeAlias("executionRetentionReconciliation")
@OwnedBy(HarnessTeam.PIPELINE)
@HarnessEntity(exportable = false)
@Persistent
public class ExecutionRetentionReconciliationEntity implements PersistentEntity, PersistentRegularIterable {
  @Id @dev.morphia.annotations.Id String uuid;

  /*
   * The data will be synced from syncCompletedUntil upto syncUntil, which compares the endTs of the execution
   */
  @NonFinal Long syncCompletedUntil;
  @NonFinal Long syncUntil;

  String accountIdentifier;

  /**
   * When set, reconciliation is scoped to this org within the account. When null, reconciliation runs at account scope.
   */
  String orgIdentifier;

  @NonFinal Long nextIteration;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
  @Builder.Default ExecutionRetentionReconciliationStatus status = ExecutionRetentionReconciliationStatus.IN_PROGRESS;

  // The below boolean is to verify if record exists in elastic before inserting it
  @Builder.Default Boolean verifyRecordExistsBeforeInsert = Boolean.FALSE;

  // The below boolean is to update record in elastic instead of inserting it
  @Builder.Default Boolean shouldOnlyUpdate = Boolean.FALSE;

  // The below boolean is to insert/update record in elastic from GCS instead of MongoDB
  @Builder.Default Boolean shouldSyncFromGCS = Boolean.FALSE;

  // Control where all the reconciliation job needs to sync the data to which all DBs
  ExecutionRetentionReconciliationDB reconciliationDB;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }
}

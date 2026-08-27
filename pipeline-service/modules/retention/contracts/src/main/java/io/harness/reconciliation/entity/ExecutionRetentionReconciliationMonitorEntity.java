/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
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
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationMonitorStatus;

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
 * This entity is will compare the records present in elastic vs the ExecutionRetentionMetadata collection
 * and will log the executions which are not present
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DATA_RETENTION, HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.PMS)
@FieldNameConstants(innerTypeName = "ExecutionRetentionReconciliationMonitorEntityKeys")
@Entity(value = "executionRetentionReconciliationMonitor")
@Document(value = "executionRetentionReconciliationMonitor")
@TypeAlias("executionRetentionReconciliationMonitor")
@OwnedBy(HarnessTeam.PIPELINE)
@HarnessEntity(exportable = false)
@Persistent
public class ExecutionRetentionReconciliationMonitorEntity implements PersistentEntity, PersistentRegularIterable {
  @Id @dev.morphia.annotations.Id String uuid;

  /*
   * The data will be compared from syncCompletedUntil upto syncUntil, which compares the endTs of the execution
   */
  @NonFinal Long syncCompletedUntil;
  @NonFinal Long syncUntil;

  String accountIdentifier;

  @NonFinal Long nextIteration;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
  @Builder.Default
  ExecutionRetentionReconciliationMonitorStatus status = ExecutionRetentionReconciliationMonitorStatus.IN_PROGRESS;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }
}

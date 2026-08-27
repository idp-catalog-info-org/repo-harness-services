/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.iterator.interfaces.PersistentRegularIterable;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;

import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Data
@Builder
@FieldNameConstants(innerTypeName = "GraphDeleteEventsKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "graphDeleteEvents", noClassnameStored = true)
@Document("graphDeleteEvents")
@TypeAlias("graphDeleteEvents")
@HarnessEntity(exportable = true)
public class GraphDeleteEvent implements PersistentEntity, UuidAccess, PersistentRegularIterable {
  // This collection can be used when we want delete outputs from the graphs for executions of a particular pipeline by
  // inserting a db record of this entity with required info. It can later be extended to use to delete other elements
  // as well from graph if necessary
  public static final long delayBeforeNextIteration = 7200000; // 2hrs
  @Id @dev.morphia.annotations.Id String uuid;
  String accountId;
  String orgId;
  String projectId;
  String pipelineIdentifier;
  String stepType;
  Long startTs;
  Long endTs;
  @NonFinal @Builder.Default Long nextIteration = 0L;
  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration + delayBeforeNextIteration;
  }
}

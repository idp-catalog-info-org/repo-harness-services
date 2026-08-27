/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.iterators.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.interfaces.PersistentIterable;
import io.harness.iterator.interfaces.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "IteratorsKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "iterators", noClassnameStored = true)
@Document("iterators")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class IteratorEntity implements PersistentIterable, PersistentRegularIterable {
  @Id @org.mongodb.morphia.annotations.Id private String id;
  @FdUniqueIndex String name;
  @LastModifiedDate Long lastModifiedAt;
  @FdIndex Long nextIteration;

  @Override
  public Long obtainNextIteration(String fieldName) {
    return this.nextIteration;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public String getUuid() {
    return this.id;
  }
}

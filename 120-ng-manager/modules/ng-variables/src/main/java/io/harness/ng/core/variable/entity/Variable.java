/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.variable.VariableType;
import io.harness.ng.core.variable.VariableValueType;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UniqueIdAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.PL)
@Data
@FieldNameConstants(innerTypeName = "VariableKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "variables", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("variables")
@Persistent
public abstract class Variable implements PersistentEntity, NGAccountAccess, UniqueIdAware {
  @Id @dev.morphia.annotations.Id String id;
  @NotEmpty @EntityIdentifier String identifier;
  @NotEmpty @NGEntityName String name;
  String description;
  @Trimmed @NotEmpty String accountIdentifier;
  @Trimmed @Deprecated String orgIdentifier;
  @Trimmed @Deprecated String projectIdentifier;
  @NotNull VariableType type;
  @NotNull VariableValueType valueType;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @FdUniqueIndex String uniqueId;
  String parentUniqueId;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_createdAt_decreasing_sort_Index")
                 .fields(Arrays.asList(
                     VariableKeys.accountIdentifier, VariableKeys.orgIdentifier, VariableKeys.projectIdentifier))
                 .descSortField(VariableKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_createdAt_decreasing_sort_Index")
                 .fields(Arrays.asList(VariableKeys.accountIdentifier, VariableKeys.parentUniqueId))
                 .descSortField(VariableKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_lastModifiedAt_decreasing_sort_Index")
                 .fields(Arrays.asList(
                     VariableKeys.accountIdentifier, VariableKeys.orgIdentifier, VariableKeys.projectIdentifier))
                 .descSortField(VariableKeys.lastModifiedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_lastModifiedAt_decreasing_sort_Index")
                 .fields(Arrays.asList(VariableKeys.accountIdentifier, VariableKeys.parentUniqueId))
                 .descSortField(VariableKeys.lastModifiedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_with_collation")
                 .field(VariableKeys.accountIdentifier)
                 .field(VariableKeys.orgIdentifier)
                 .field(VariableKeys.projectIdentifier)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.SECONDARY).build())
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_identifier_with_collation")
                 .field(VariableKeys.accountIdentifier)
                 .field(VariableKeys.parentUniqueId)
                 .field(VariableKeys.identifier)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.SECONDARY).build())
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("parentId_identifier_unique_idx")
                 .field(VariableKeys.parentUniqueId)
                 .field(VariableKeys.identifier)
                 .unique(true)
                 .build())
        .build();
  }

  public String getScope(ScopeLevel scopeLevel) {
    if (scopeLevel == ScopeLevel.PROJECT) {
      return "project";
    } else if (scopeLevel == ScopeLevel.ORGANIZATION) {
      return "org";
    }
    return "account";
  }

  public String getExpression(ScopeLevel scopeLevel) {
    return "variable" + (getScope(scopeLevel).equals("project") ? "" : "." + getScope(scopeLevel)) + "."
        + getIdentifier();
  }
}

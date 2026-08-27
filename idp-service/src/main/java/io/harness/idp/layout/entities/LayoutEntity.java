/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.layout.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UniqueIdAware;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "LayoutKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.IDP)
@Entity(value = "layout", noClassnameStored = true)
@Document("layout")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.IDP)
public class LayoutEntity
    implements PersistentEntity, CreatedAtAware, UpdatedAtAware, CreatedByAware, UpdatedByAware, UniqueIdAware {
  @JsonIgnore @Id private String id;

  @NotEmpty private String accountIdentifier;

  @JsonIgnore @FdUniqueIndex String uniqueId;
  @JsonIgnore String parentUniqueId;

  @NotEmpty private String name;
  private String displayName;
  @NotEmpty private String yaml;
  @NotEmpty private String defaultYaml;
  private String description;
  @NotEmpty private LayoutType type;
  @NotEmpty private String entityKind;
  @NotEmpty private String entityType;
  @NotEmpty private boolean harnessManaged = false;

  @JsonIgnore @CreatedDate private long createdAt;
  @JsonIgnore @CreatedBy private EmbeddedUser createdBy;
  @JsonIgnore @LastModifiedDate private long lastUpdatedAt;
  @JsonIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_name")
                 .field(LayoutKeys.accountIdentifier)
                 .field(LayoutKeys.name)
                 .unique(true)
                 .build())
        .build();
  }
}

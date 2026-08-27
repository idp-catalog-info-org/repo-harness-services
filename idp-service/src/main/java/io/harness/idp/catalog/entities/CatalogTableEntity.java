/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "CatalogTableKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.IDP)
@Entity(value = "catalogTable", noClassnameStored = true)
@Document("catalogTable")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.IDP)
public class CatalogTableEntity
    implements PersistentEntity, CreatedAtAware, CreatedByAware, UpdatedByAware, UpdatedAtAware {
  @Id private String id;
  @NotNull private String identifier;
  @NotNull private String name;
  @NotEmpty private String kind;
  @NotEmpty private String type;
  @NotNull private String accountIdentifier;
  private Filter filter;
  private List<ColumnDetails> columnDetails;
  @CreatedDate private long createdAt;
  @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedDate private long lastUpdatedAt;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;

  @Data
  @Builder
  public static class Filter {
    private List<String> owners;
    private List<String> tags;
    private List<String> lifecycles;
    private List<String> scopes;
  }

  @Data
  @Builder
  public static class ColumnDetails {
    private String id;
    private String type;
    private String headerName;
    private Integer size;
    private String accessorKey;
    private String description;
    @Builder.Default private boolean visible = false;
    private String pinned;
    private Map<String, Object> properties;
    @Builder.Default private boolean harnessManaged = false;
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_identifier")
                 .field(CatalogTableEntity.CatalogTableKeys.accountIdentifier)
                 .field(CatalogTableEntity.CatalogTableKeys.identifier)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_kind_type")
                 .field(CatalogTableEntity.CatalogTableKeys.accountIdentifier)
                 .field(CatalogTableEntity.CatalogTableKeys.kind)
                 .field(CatalogTableEntity.CatalogTableKeys.type)
                 .build())
        .build();
  }
}

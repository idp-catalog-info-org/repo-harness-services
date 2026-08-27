/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.catalog.entities;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import com.mongodb.BasicDBObject;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldNameConstants(innerTypeName = "CatalogEntityVersionKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "catalogEntityVersion")
@Document("catalogEntityVersion")
@OwnedBy(HarnessTeam.IDP)
public class CatalogEntityVersion
    implements PersistentEntity, CreatedAtAware, CreatedByAware, UpdatedAtAware, UpdatedByAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_entityId_version")
                 .unique(true)
                 .field(CatalogEntityVersionKeys.entityId)
                 .field(CatalogEntityVersionKeys.version)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_entityId")
                 .unique(true)
                 .partialFilterExpression(new BasicDBObject().append("stable", true))
                 .field(CatalogEntityVersionKeys.entityId)
                 .build())
        .build();
  }

  @NotEmpty String entityId;

  @Id private String id;
  @NotEmpty private String version;
  @JsonIgnore @CreatedDate private long createdAt;
  @JsonIgnore @CreatedBy private EmbeddedUser createdBy;
  @JsonIgnore @LastModifiedDate private long lastUpdatedAt;
  @JsonIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  private boolean deprecated;
  private long deprecatedAt;
  @Builder.Default private boolean stable = false;
  private String description;
  private String yaml;
}

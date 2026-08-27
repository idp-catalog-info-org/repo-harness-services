/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.entities;

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

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.IDP)
@FieldNameConstants(innerTypeName = "CustomPluginV2EntityKeys")
@Entity(value = "customPluginV2", noClassnameStored = true)
@Document("customPluginV2")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class CustomPluginV2Entity
    implements PersistentEntity, CreatedAtAware, CreatedByAware, UpdatedAtAware, UpdatedByAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_identifier")
                 .unique(true)
                 .field(CustomPluginV2EntityKeys.accountIdentifier)
                 .field(CustomPluginV2EntityKeys.identifier)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id private String id;
  private String identifier;
  private String accountIdentifier;
  private String name;
  private String description;
  private String icon;
  private String fileUrl;

  @SchemaIgnore @CreatedBy private EmbeddedUser createdBy;
  @CreatedDate private long createdAt;
  @SchemaIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @LastModifiedDate private long lastUpdatedAt;
}

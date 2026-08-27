/*
 * Copyright 2026 Harness Inc. All rights reserved.
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
import com.mongodb.BasicDBObject;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
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
@FieldNameConstants(innerTypeName = "ActionKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "actions")
@Document("actions")
@OwnedBy(HarnessTeam.IDP)
public class Action
    implements PersistentEntity, CreatedAtAware, CreatedByAware, UpdatedAtAware, UpdatedByAware, UniqueIdAware {
  public static final String GLOBAL_ACCOUNT_IDENTIFIER = "__GLOBAL_ACCOUNT_ID__";
  public static final String GLOBAL_PARENT_UNIQUE_ID = "__GLOBAL_PARENT_UNIQUE_ID__";

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_parentUniqueId_identifier_version")
                 .unique(true)
                 .field(ActionKeys.parentUniqueId)
                 .field(ActionKeys.identifier)
                 .field(ActionKeys.version)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_parentUniqueId_identifier_published")
                 .unique(true)
                 .partialFilterExpression(new BasicDBObject().append(ActionKeys.status, ActionStatus.PUBLISHED.name()))
                 .field(ActionKeys.parentUniqueId)
                 .field(ActionKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("idx_parentUniqueId_status_category")
                 .field(ActionKeys.parentUniqueId)
                 .field(ActionKeys.status)
                 .field(ActionKeys.category)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("idx_accountIdentifier_status_category")
                 .field(ActionKeys.accountIdentifier)
                 .field(ActionKeys.status)
                 .field(ActionKeys.category)
                 .build())
        .build();
  }

  @Id private String id;
  @FdUniqueIndex String uniqueId;
  String parentUniqueId;
  @NotEmpty private String accountIdentifier;

  @NotEmpty private String identifier;
  @NotEmpty private String name;
  @Size(max = 1024) private String description;
  @NotEmpty private String version;
  @Builder.Default private ActionStatus status = ActionStatus.DRAFT;
  @Builder.Default private ActionType type = ActionType.HTTP;
  private Map<String, Object> inputSchema;
  private Map<String, String> outputMapping;
  private ActionHttpConfig httpConfig;
  private ActionBuiltinConfig builtinConfig;
  private String connectorRef;
  private List<String> delegateSelectors;
  private String category;
  private List<String> tags;
  @JsonIgnore @CreatedDate private long createdAt;
  @JsonIgnore @CreatedBy private EmbeddedUser createdBy;
  @JsonIgnore @LastModifiedDate private long lastUpdatedAt;
  @JsonIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  private long deprecatedAt;
}

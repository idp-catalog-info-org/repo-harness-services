/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
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

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder(toBuilder = true)
@FieldNameConstants(innerTypeName = "EntityLinkKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "entityLinks", noClassnameStored = true)
@Document("entityLinks")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class EntityLinks implements PersistentEntity, CreatedAtAware, UpdatedAtAware, CreatedByAware, UpdatedByAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_entityRef")
                 .unique(true)
                 .field(EntityLinkKeys.accountIdentifier)
                 .field(EntityLinkKeys.entityRef)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("idx_accountIdentifier_targets_entityKind_entityType")
                 .field(EntityLinkKeys.accountIdentifier)
                 .field("targets.entityKind")
                 .field("targets.entityType")
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("idx_accountIdentifier_integrations_identifier_spacePath")
                 .field(EntityLinkKeys.accountIdentifier)
                 .field("integrations.identifier")
                 .field("integrations.spacePath")
                 .build())
        .build();
  }

  @Id private String id;

  @NotNull private String accountIdentifier;
  @NotNull private String entityRef;

  private List<String> scopes;
  private List<LinkTarget> targets;
  private List<String> entityIdentifiers;
  private List<FieldMapping> fieldMappings;
  private List<IntegrationReference> integrations;

  @NotNull @CreatedDate private long createdAt;
  @NotNull @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedDate private long lastUpdatedAt;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LinkTarget {
    private String entityKind;
    private String entityType;
    private List<String> entityIdentifiers;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FieldMapping {
    private String input;
    private String entityFieldSource;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class IntegrationReference {
    private String identifier;
    private String spacePath;
  }
}

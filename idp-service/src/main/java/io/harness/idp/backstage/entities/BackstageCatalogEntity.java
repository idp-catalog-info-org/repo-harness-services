/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.entities;

import static io.harness.idp.backstage.repositories.BackstageCatalogEntityRepositoryCustomImpl.METADATA_TAGS;
import static io.harness.idp.backstage.repositories.BackstageCatalogEntityRepositoryCustomImpl.SPEC_LIFECYCLE;
import static io.harness.idp.backstage.repositories.BackstageCatalogEntityRepositoryCustomImpl.SPEC_OWNER;
import static io.harness.idp.backstage.repositories.BackstageCatalogEntityRepositoryCustomImpl.SPEC_TYPE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "BackstageCatalogKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = BackstageCatalogApiEntity.class, name = "API")
  , @JsonSubTypes.Type(value = BackstageCatalogComponentEntity.class, name = "Component"),
      @JsonSubTypes.Type(value = BackstageCatalogDomainEntity.class, name = "Domain"),
      @JsonSubTypes.Type(value = BackstageCatalogGroupEntity.class, name = "Group"),
      @JsonSubTypes.Type(value = BackstageCatalogLocationEntity.class, name = "Location"),
      @JsonSubTypes.Type(value = BackstageCatalogResourceEntity.class, name = "Resource"),
      @JsonSubTypes.Type(value = BackstageCatalogSystemEntity.class, name = "System"),
      @JsonSubTypes.Type(value = BackstageCatalogTemplateEntity.class, name = "Template"),
      @JsonSubTypes.Type(value = BackstageCatalogUserEntity.class, name = "User")
})
@StoreIn(DbAliases.IDP)
@Entity(value = "backstageCatalog", noClassnameStored = true)
@Document("backstageCatalog")
@HarnessEntity(exportable = true)
public abstract class BackstageCatalogEntity
    implements PersistentEntity, CreatedAtAware, UpdatedAtAware, CreatedByAware, UpdatedByAware {
  @JsonIgnore @Id private String id;
  @JsonIgnore private String accountIdentifier;
  @JsonIgnore private String entityUid;
  private String apiVersion = "backstage.io/v1alpha1";

  @JsonIgnoreProperties(value = {"identifier", "absoluteIdentifier", "uid", "etag"})
  private Map<String, Object> metadata;

  @JsonIgnore private String kind;
  private Set<Relation> relations;
  @JsonIgnore private Object status;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldNameConstants(innerTypeName = "BackstageCatalogEntityRelationKeys")
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Relation {
    String type;
    String targetRef;
    Target target;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldNameConstants(innerTypeName = "BackstageCatalogEntityRelationTargetKeys")
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Target {
    String kind;
    String namespace;
    String name;
  }

  @JsonIgnore private String yaml;

  @JsonIgnore @CreatedDate private long createdAt;
  @JsonIgnore @SchemaIgnore @CreatedBy private EmbeddedUser createdBy;
  @JsonIgnore @LastModifiedDate private long lastUpdatedAt;
  @JsonIgnore @SchemaIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_entityUid")
                 .field(BackstageCatalogKeys.accountIdentifier)
                 .field(BackstageCatalogKeys.entityUid)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_kind_specType_specOwner_specLifecycle_metadataTags")
                 .field(BackstageCatalogKeys.accountIdentifier)
                 .field(BackstageCatalogKeys.kind)
                 .field(SPEC_TYPE)
                 .field(SPEC_OWNER)
                 .field(SPEC_LIFECYCLE)
                 .field(METADATA_TAGS)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("spec_type_with_collation")
                 .field(SPEC_TYPE)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.SECONDARY).build())
                 .build())
        .build();
  }

  public static <T> T getValue(Map<String, Object> metadata, String field, Class<T> clazz) {
    Object value = metadata.get(field);
    if (clazz.isInstance(value)) {
      return (T) value;
    }
    return null;
  }
}

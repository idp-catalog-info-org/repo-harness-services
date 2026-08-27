/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.entities;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.from;
import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.idp.common.YamlUtils.loadYamlStringAsMap;
import static io.harness.idp.common.YamlUtils.mergeDecorator;
import static io.harness.idp.common.YamlUtils.writeObjectAsYaml;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.beans.Scope;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UniqueIdAware;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
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
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "CatalogKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "referenceType", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = InlineCatalogEntity.class, name = "INLINE")
  , @JsonSubTypes.Type(value = GitReferencedCatalogEntity.class, name = "GIT")
})
@JsonPropertyOrder({"apiVersion", "kind", "type", "identifier", "name", "owner", "orgIdentifier", "projectIdentifier",
    "metadata", "spec"})
@StoreIn(DbAliases.IDP)
@Entity(value = "catalog", noClassnameStored = true)
@Document("catalog")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.IDP)
public abstract class CatalogEntity
    implements PersistentEntity, CreatedAtAware, UpdatedAtAware, CreatedByAware, UpdatedByAware, UniqueIdAware {
  @JsonIgnore @Id private String id;

  @NotEmpty private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;

  @NotEmpty private String identifier;

  @NotEmpty @JsonIgnore private ReferenceType referenceType;

  @NotEmpty private String apiVersion;
  @NotEmpty private String kind;
  private String type;
  private String name;
  private String description;
  private String owner;
  private List<String> tags;

  private String sourceLocation;

  @JsonIgnoreProperties(value = {"type", "owner", "system", "ownedBy", "ownerOf", "consumesApi", "apiConsumedBy",
                            "providesApi", "apiProvidedBy", "dependsOn", "dependencyOf", "parentOf", "childOf",
                            "memberOf", "hasMember", "partOf", "hasPart"})
  @JsonPropertyOrder({"lifecycle", "parameters", "steps", "definition"})
  private Map<String, Object> spec;

  @JsonIgnoreProperties(
      value = {"identifier", "absoluteIdentifier", "uid", "etag", "name", "title", "description", "tags", "namespace"})
  @JsonSerialize(using = MetadataSerializer.class)
  @JsonPropertyOrder({"description", "labels", "annotations", "tags", "links"})
  private Map<String, Object> metadata;

  private Map<String, Set<String>> relations;
  @NotEmpty @JsonIgnore private String yaml;
  @JsonIgnore private List<Map<String, String>> status;

  @FdUniqueIndex String uniqueId;
  String parentUniqueId;

  private Map<String, Object> decorator;

  @FdUniqueIndex @JsonIgnore String queryableEntityRef;

  @JsonIgnore @CreatedDate private long createdAt;
  @JsonIgnore @CreatedBy private EmbeddedUser createdBy;
  @JsonIgnore @LastModifiedDate private long lastUpdatedAt;
  @JsonIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_parentUniqueId_kind_identifier")
                 .field(CatalogKeys.parentUniqueId) // this will be some thing like - accountId/orgId?/projectId?
                 .field(CatalogKeys.kind)
                 .field(CatalogKeys.identifier) // metadata.name is identifier
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_org_project")
                 .field(CatalogKeys.accountIdentifier)
                 .field(CatalogKeys.orgIdentifier)
                 .field(CatalogKeys.projectIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("parentUniqueId_kind_name_identifier_type_owner_collation")
                 .field(CatalogKeys.parentUniqueId)
                 .field(CatalogKeys.kind)
                 .field(CatalogKeys.name)
                 .field(CatalogKeys.identifier)
                 .field(CatalogKeys.type)
                 .field(CatalogKeys.owner)
                 .collation(
                     Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.SECONDARY).build())
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("kind_apisLastCheckedAt_for_endpoint_sync")
                 .field(CatalogKeys.kind)
                 .ascSortField("decorator._processed_data.metadata.apis.lastCheckedAt")
                 .build())
        .build();
  }

  @JsonIgnore
  public String getScope() {
    if (isEmpty(this.orgIdentifier) && isEmpty(this.projectIdentifier)) {
      return Scope.ACCOUNT.name();
    }
    if (!isEmpty(this.orgIdentifier) && isEmpty(this.projectIdentifier)) {
      return Scope.ORGANIZATION.name();
    }
    return Scope.PROJECT.name();
  }

  public Object fromSpecification(String field) {
    return from(this.spec, field, null);
  }

  public <T> T fromSpecification(String field, Class<T> clazz) {
    return from(this.spec, field, clazz);
  }

  public Object fromMetadata(String field) {
    return from(this.metadata, field, null);
  }

  public <T> T fromMetadata(String field, Class<T> clazz) {
    return from(this.metadata, field, clazz);
  }

  public Set<String> getRelationsFor(String type) {
    return this.relations.get(type);
  }

  @JsonIgnore
  public Map<String, Object> getFailSafeDecorator() {
    return Objects.isNull(this.getDecorator()) ? new HashMap<>() : this.getDecorator();
  }

  @JsonIgnore
  public Map<String, Object> getFailSafeProcessedData() {
    Map<String, Object> decorator = getFailSafeDecorator();
    return getFailSafeProcessedData(decorator);
  }

  @JsonIgnore
  @SuppressWarnings("unchecked")
  public Map<String, Object> getFailSafeProcessedData(Map<String, Object> decorator) {
    return Objects.isNull(decorator.get(PROCESSED_DATA)) ? new HashMap<>()
                                                         : (Map<String, Object>) decorator.get(PROCESSED_DATA);
  }

  @JsonIgnore
  public String getDecoratedYaml() {
    Map<String, Object> processedData = getFailSafeProcessedData();
    if (!isEmpty(processedData)) {
      Map<String, Object> entityMap = loadYamlStringAsMap(this.getYaml());
      Map<String, Object> merged = mergeDecorator(entityMap, processedData);
      return writeObjectAsYaml(merged);
    }
    return this.getYaml();
  }

  @JsonIgnore
  @SuppressWarnings("unchecked")
  public Map<String, Object> getDecoratedEntityMap() {
    Map<String, Object> decorator = Objects.isNull(this.getDecorator()) ? new HashMap<>() : this.getDecorator();
    Map<String, Object> processedData = Objects.isNull(decorator.get(PROCESSED_DATA))
        ? new HashMap<>()
        : (Map<String, Object>) decorator.get(PROCESSED_DATA);
    if (!isEmpty(processedData)) {
      Map<String, Object> entityMap = loadYamlStringAsMap(this.getYaml());
      return mergeDecorator(entityMap, processedData);
    }
    return loadYamlStringAsMap(this.getYaml());
  }

  @JsonIgnore
  @SuppressWarnings("unchecked")
  public Map<String, Object> getDecoratedMetadata() {
    Map<String, Object> metadata = this.getMetadata();
    if (metadata == null) {
      metadata = new HashMap<>();
    }

    Map<String, Object> decorator = Objects.isNull(this.getDecorator()) ? new HashMap<>() : this.getDecorator();
    Map<String, Object> processedData = Objects.isNull(decorator.get(PROCESSED_DATA))
        ? new HashMap<>()
        : (Map<String, Object>) decorator.get(PROCESSED_DATA);

    if (!isEmpty(processedData)) {
      Object metadataObj = processedData.get("metadata");
      if (!Objects.isNull(metadataObj) && metadataObj instanceof Map) {
        try {
          Map<String, Object> processedMetadata = (Map<String, Object>) metadataObj;
          return mergeDecorator(metadata, processedMetadata);
        } catch (ClassCastException e) {
          return metadata;
        }
      }
    }
    return metadata;
  }
}

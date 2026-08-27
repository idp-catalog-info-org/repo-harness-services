/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.platform.schema.service.api.v1.EntityFieldMetadata;
import io.harness.platform.schema.service.api.v1.EntityType;
import io.harness.platform.schema.service.api.v1.FieldType;
import io.harness.platform.schema.service.api.v1.ObjectKind;
import io.harness.platform.schema.service.api.v1.ObjectType;
import io.harness.platform.schema.service.api.v1.Unit;
import io.harness.platform.schema.service.api.v1.UnitCategory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class DefaultKindToUpgradePackMapper implements KindToObjectTypeMapper {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String DEFAULT_PROVIDER = "idp";
  private static final String CONTAINER_SPEC = "spec";
  private static final String CONTAINER_METADATA = "metadata";
  private static final String TENANT_FIELD = "account_identifier";
  private static final String ENTITY_REF_FIELD = "entity_ref";
  private static final String FIELD_PROPERTIES = "properties";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_FORMAT = "format";
  private static final String TYPE_STRING = "string";
  private static final String TYPE_ARRAY = "array";
  private static final String TYPE_OBJECT = "object";
  private static final String TYPE_BOOLEAN = "boolean";
  private static final String TYPE_INTEGER = "integer";
  private static final String TYPE_NUMBER = "number";
  private static final String FORMAT_DATE_TIME = "date-time";
  private static final String FORMAT_TIMESTAMP = "timestamp";
  private static final List<BaseFieldDef> BASE_FIELDS =
      List.of(new BaseFieldDef("account_identifier", FieldType.FIELD_TYPE_STR, "Account Identifier",
                  "Harness account identifier for scoping"),
          new BaseFieldDef(
              "org_identifier", FieldType.FIELD_TYPE_STR, "Organization Identifier", "Harness organization identifier"),
          new BaseFieldDef(
              "project_identifier", FieldType.FIELD_TYPE_STR, "Project Identifier", "Harness project identifier"),
          new BaseFieldDef("identifier", FieldType.FIELD_TYPE_STR, "Identifier", "Entity identifier"),
          new BaseFieldDef(ENTITY_REF_FIELD, FieldType.FIELD_TYPE_STR, "Entity Ref", "Canonical entity reference"),
          new BaseFieldDef("catalog_resource_id", FieldType.FIELD_TYPE_STR, "Catalog Resource ID",
              "Kind and identifier composite key"),
          new BaseFieldDef(
              "scope", FieldType.FIELD_TYPE_STR, "Scope", "Harness scope level (ACCOUNT, ORGANIZATION, PROJECT)"),
          new BaseFieldDef("unique_id", FieldType.FIELD_TYPE_STR, "Unique ID", "Harness globally unique identifier"),
          new BaseFieldDef(
              "parent_unique_id", FieldType.FIELD_TYPE_STR, "Parent Unique ID", "Parent scope unique identifier"),
          new BaseFieldDef("created_at", FieldType.FIELD_TYPE_TIMESTAMP, "Created At", "Entity creation timestamp"),
          new BaseFieldDef(
              "last_updated_at", FieldType.FIELD_TYPE_TIMESTAMP, "Last Updated At", "Entity last update timestamp"),
          new BaseFieldDef(CONTAINER_METADATA, FieldType.FIELD_TYPE_MAP, "Metadata", "Entity metadata payload"),
          new BaseFieldDef(CONTAINER_SPEC, FieldType.FIELD_TYPE_MAP, "Spec", "Entity spec payload"));

  private final UdpTypeIngestionConfig udpTypeIngestionConfig;

  @Inject
  public DefaultKindToUpgradePackMapper(UdpTypeIngestionConfig udpTypeIngestionConfig) {
    this.udpTypeIngestionConfig = udpTypeIngestionConfig;
  }

  @Override
  public ObjectType toObjectType(KindEntity kindEntity) {
    return buildObjectType(kindEntity);
  }

  private ObjectType buildObjectType(KindEntity kindEntity) {
    String typeId = buildTypeId(kindEntity.getIdentifier());
    String displayName = isEmpty(kindEntity.getDisplayName()) ? kindEntity.getName() : kindEntity.getDisplayName();

    EntityType.Builder entityTypeBuilder = EntityType.newBuilder()
                                               .setKind(ObjectKind.OBJECT_KIND_ENTITY)
                                               .setId(typeId)
                                               .setName(nullSafe(displayName))
                                               .setDescription(nullSafe(kindEntity.getDescription()))
                                               .setTenantField(TENANT_FIELD)
                                               .addIdFields(ENTITY_REF_FIELD)
                                               .addAnnotations("idp")
                                               .addAnnotations("catalog")
                                               .addAnnotations(kindEntity.getIdentifier().toLowerCase(Locale.ROOT));
    addBaseFieldMetadata(entityTypeBuilder);
    addSchemaFields(entityTypeBuilder, kindEntity.getSchema());
    return ObjectType.newBuilder().setEntityType(entityTypeBuilder.build()).build();
  }

  private String buildTypeId(String identifier) {
    String provider =
        isEmpty(udpTypeIngestionConfig.getTypeProvider()) ? DEFAULT_PROVIDER : udpTypeIngestionConfig.getTypeProvider();
    return provider + ":" + identifier;
  }

  private String nullSafe(String value) {
    return value == null ? "" : value;
  }

  private void addBaseFieldMetadata(EntityType.Builder entityTypeBuilder) {
    for (BaseFieldDef baseField : BASE_FIELDS) {
      addFieldMetadata(
          entityTypeBuilder, baseField.key, baseField.fieldType, baseField.displayName, baseField.description);
    }
  }

  private void addSchemaFields(EntityType.Builder entityTypeBuilder, String schema) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(schema);
      JsonNode properties = root.path(FIELD_PROPERTIES);
      if (!properties.isObject()) {
        return;
      }

      addRootPrimitiveFields(entityTypeBuilder, properties);
      addNamespacedSchemaFields(entityTypeBuilder, properties, CONTAINER_SPEC);
      addNamespacedSchemaFields(entityTypeBuilder, properties, CONTAINER_METADATA);
    } catch (Exception ex) {
      log.warn("Failed to parse custom kind schema for UDP mapping", ex);
    }
  }

  private void addRootPrimitiveFields(EntityType.Builder entityTypeBuilder, JsonNode rootProperties) {
    Iterator<String> fieldNames = rootProperties.fieldNames();
    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next();
      if (CONTAINER_SPEC.equals(fieldName) || CONTAINER_METADATA.equals(fieldName)) {
        continue;
      }

      JsonNode fieldSchema = rootProperties.get(fieldName);
      if (isObjectWithProperties(fieldSchema)) {
        continue;
      }

      addFieldMetadata(entityTypeBuilder, fieldName, toUdpFieldType(fieldSchema), prettify(fieldName),
          fieldSchema.path("description").asText(prettify(fieldName)));
    }
  }

  private void addNamespacedSchemaFields(
      EntityType.Builder entityTypeBuilder, JsonNode rootProperties, String containerFieldName) {
    JsonNode containerFieldSchema = rootProperties.path(containerFieldName);
    if (!isObjectWithProperties(containerFieldSchema)) {
      return;
    }
    addSchemaFieldsRecursively(entityTypeBuilder, containerFieldSchema.path(FIELD_PROPERTIES), containerFieldName);
  }

  private void addSchemaFieldsRecursively(
      EntityType.Builder entityTypeBuilder, JsonNode properties, String parentPrefix) {
    Iterator<String> fields = properties.fieldNames();
    while (fields.hasNext()) {
      String fieldName = fields.next();
      JsonNode fieldSchema = properties.get(fieldName);
      String flattenedFieldName = isEmpty(parentPrefix) ? fieldName : parentPrefix + "_" + fieldName;
      String defaultDescription =
          isEmpty(parentPrefix) ? prettify(fieldName) : prettify(parentPrefix + " " + fieldName);

      addFieldMetadata(entityTypeBuilder, flattenedFieldName, toUdpFieldType(fieldSchema), prettify(flattenedFieldName),
          fieldSchema.path("description").asText(defaultDescription));

      if (isObjectWithProperties(fieldSchema)) {
        addSchemaFieldsRecursively(entityTypeBuilder, fieldSchema.path(FIELD_PROPERTIES), flattenedFieldName);
      }
    }
  }

  private boolean isObjectWithProperties(JsonNode fieldSchema) {
    String type = fieldSchema.path("type").asText("");
    return fieldSchema.path(FIELD_PROPERTIES).isObject() && fieldSchema.path(FIELD_PROPERTIES).size() > 0
        && (isEmpty(type) || "object".equals(type));
  }

  private FieldType toUdpFieldType(JsonNode fieldSchema) {
    String type = fieldSchema.path(FIELD_TYPE).asText(TYPE_STRING);
    String format = fieldSchema.path(FIELD_FORMAT).asText("");
    if (FORMAT_DATE_TIME.equalsIgnoreCase(format) || FORMAT_TIMESTAMP.equalsIgnoreCase(format)) {
      return FieldType.FIELD_TYPE_TIMESTAMP;
    }

    return switch (type) {
      case TYPE_ARRAY -> FieldType.FIELD_TYPE_LIST;
      case TYPE_OBJECT -> FieldType.FIELD_TYPE_MAP;
      case TYPE_BOOLEAN -> FieldType.FIELD_TYPE_BOOL;
      case TYPE_INTEGER -> FieldType.FIELD_TYPE_LONG;
      case TYPE_NUMBER -> FieldType.FIELD_TYPE_DOUBLE;
      default -> FieldType.FIELD_TYPE_STR;
    };
  }

  private String prettify(String fieldName) {
    String normalized = fieldName.replace('_', ' ');
    if (normalized.isEmpty()) {
      return fieldName;
    }
    return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
  }

  private void addFieldMetadata(
      EntityType.Builder entityTypeBuilder, String key, FieldType fieldType, String displayName, String description) {
    EntityFieldMetadata metadata = EntityFieldMetadata.newBuilder()
                                       .setFieldType(fieldType)
                                       .setDisplayName(displayName)
                                       .setDescription(description)
                                       .setGroupable(false)
                                       .setSearchable(true)
                                       .setSortable(false)
                                       .setIndexed(true)
                                       .setReserved(false)
                                       .setUnit(Unit.newBuilder()
                                                    .setCategory(UnitCategory.UNIT_CATEGORY_UNSPECIFIED)
                                                    .setSubcategory("")
                                                    .build())
                                       .build();
    entityTypeBuilder.putFields(key, metadata);
  }

  private static final class BaseFieldDef {
    private final String key;
    private final FieldType fieldType;
    private final String displayName;
    private final String description;

    private BaseFieldDef(String key, FieldType fieldType, String displayName, String description) {
      this.key = key;
      this.fieldType = fieldType;
      this.displayName = displayName;
      this.description = description;
    }
  }
}

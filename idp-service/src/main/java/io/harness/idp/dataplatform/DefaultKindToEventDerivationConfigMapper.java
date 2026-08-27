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
import io.harness.config_models.derivation_config.v1.EntityDerivationMapping;
import io.harness.config_models.derivation_config.v1.EventDerivationConfig;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.platform.schema.service.api.v1.EntityType;
import io.harness.platform.schema.service.api.v1.FieldType;
import io.harness.shared_models.transformation.v1.AttributeDerivationMapping;
import io.harness.shared_models.transformation.v1.DataTransformationConfig;
import io.harness.shared_models.transformation.v1.DerivationRule;
import io.harness.shared_models.transformation.v1.JexlExpressionConfig;
import io.harness.shared_models.transformation.v1.JexlScriptConfig;
import io.harness.shared_models.transformation.v1.VariableDerivationMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class DefaultKindToEventDerivationConfigMapper implements KindToEventDerivationConfigMapper {
  private static final String DEFAULT_PROVIDER = "idp";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String FIELD_PROPERTIES = "properties";
  private static final String FIELD_TYPE = "type";
  private static final String TYPE_OBJECT = "object";
  private static final Set<String> CORE_VARIABLE_NAMES = Set.of("cdc_event", "idp_entity_ref");
  private static final Map<String, String> KNOWN_DOCUMENT_PATHS =
      Map.ofEntries(Map.entry("account_identifier", "cdc_event.fullDocument.accountIdentifier"),
          Map.entry("org_identifier", "cdc_event.fullDocument.orgIdentifier"),
          Map.entry("project_identifier", "cdc_event.fullDocument.projectIdentifier"),
          Map.entry("identifier", "cdc_event.fullDocument.identifier"),
          Map.entry("unique_id", "cdc_event.fullDocument.uniqueId"),
          Map.entry("parent_unique_id", "cdc_event.fullDocument.parentUniqueId"),
          Map.entry("created_at", "cdc_event.fullDocument.createdAt"),
          Map.entry("last_updated_at", "cdc_event.fullDocument.lastUpdatedAt"),
          Map.entry("spec", "cdc_event.fullDocument.spec"), Map.entry("metadata", "cdc_event.fullDocument.metadata"));
  private static final Map<String, String> KNOWN_EXPRESSIONS = Map.of("catalog_resource_id",
      "cdc_event.fullDocument.kind.getAsString() + \":\" + cdc_event.fullDocument.identifier.getAsString()");
  private static final String SCOPE_JEXL_SCRIPT =
      "var org = cdc_event.fullDocument.orgIdentifier; var proj = cdc_event.fullDocument.projectIdentifier; if (proj !="
      + " null && proj.getAsString() != \"\") { return \"PROJECT\"; } else if (org != null && org.getAsString() != "
      + "\"\")"
      + " { return \"ORGANIZATION\"; } else { return \"ACCOUNT\"; }";

  private final UdpTypeIngestionConfig udpTypeIngestionConfig;
  private final EventDerivationConfigYamlParser eventDerivationConfigYamlParser;

  @Inject
  public DefaultKindToEventDerivationConfigMapper(
      UdpTypeIngestionConfig udpTypeIngestionConfig, EventDerivationConfigYamlParser eventDerivationConfigYamlParser) {
    this.udpTypeIngestionConfig = udpTypeIngestionConfig;
    this.eventDerivationConfigYamlParser = eventDerivationConfigYamlParser;
  }

  @Override
  public EventDerivationConfig toEventDerivationConfig(KindEntity kindEntity, EntityType entityType) {
    String provider =
        isEmpty(udpTypeIngestionConfig.getTypeProvider()) ? DEFAULT_PROVIDER : udpTypeIngestionConfig.getTypeProvider();
    String derivedEntityTypeId = provider + ":" + kindEntity.getIdentifier();
    log.info("{} mapper start kindIdentifier={} provider={} entityType={}", UdpEventDerivationConstants.LOG_PREFIX,
        kindEntity.getIdentifier(), provider, derivedEntityTypeId);
    Map<String, String> schemaDocumentPaths = deriveDocumentPaths(kindEntity.getSchema());
    EntityDerivationMapping.Builder builder = EntityDerivationMapping.newBuilder()
                                                  .setEntityType(derivedEntityTypeId)
                                                  .addIdAttributes(simpleNameOnly("entity_ref"));
    EventDerivationConfig templateEnvelope =
        eventDerivationConfigYamlParser.parseForCdcKind(kindEntity.getIdentifier());
    EventDerivationConfig.Builder resultBuilder =
        EventDerivationConfig.newBuilder()
            .addAllVariables(filterCoreVariables(templateEnvelope.getVariablesList()))
            .setScopeExpression(templateEnvelope.getScopeExpression())
            .setSource(templateEnvelope.getSource())
            .setTenantId(templateEnvelope.getTenantId())
            .setEventTimestamp(templateEnvelope.getEventTimestamp());
    if (entityType != null) {
      for (Map.Entry<String, io.harness.platform.schema.service.api.v1.EntityFieldMetadata> field :
          entityType.getFieldsMap().entrySet()) {
        Optional<AttributeDerivationMapping> derivation =
            toAttributeDerivation(field.getKey(), field.getValue().getFieldType(), schemaDocumentPaths);
        if (derivation.isEmpty()) {
          log.warn("{} mapper skipping entity field with no derivation path fieldName={} entityType={}",
              UdpEventDerivationConstants.LOG_PREFIX, field.getKey(), derivedEntityTypeId);
          continue;
        }
        builder.addAttributes(simpleNameOnly(field.getKey()));
        resultBuilder.addAttributes(derivation.get());
      }
    }
    EventDerivationConfig result = resultBuilder.addEntities(builder.build()).build();
    log.info("{} mapper complete kindIdentifier={} entityType={} entities={} attributes={} variables={}",
        UdpEventDerivationConstants.LOG_PREFIX, kindEntity.getIdentifier(), derivedEntityTypeId,
        result.getEntitiesCount(), result.getAttributesCount(), result.getVariablesCount());
    return result;
  }

  private AttributeDerivationMapping simpleNameOnly(String name) {
    return AttributeDerivationMapping.newBuilder().setName(name).build();
  }

  private Optional<AttributeDerivationMapping> toAttributeDerivation(
      String fieldName, FieldType fieldType, Map<String, String> schemaDocumentPaths) {
    if ("scope".equals(fieldName)) {
      DerivationRule rule =
          DerivationRule.newBuilder()
              .setTransformationConfig(
                  DataTransformationConfig.newBuilder()
                      .setJexlScript(JexlScriptConfig.newBuilder().setJexlScript(SCOPE_JEXL_SCRIPT).build())
                      .setOutputType(fieldType)
                      .build())
              .build();
      return Optional.of(
          AttributeDerivationMapping.newBuilder().setName(fieldName).setType(fieldType).addRules(rule).build());
    }

    return toFieldJexlExpression(fieldName, fieldType, schemaDocumentPaths)
        .map(expression
            -> AttributeDerivationMapping.newBuilder()
                   .setName(fieldName)
                   .setType(fieldType)
                   .addRules(DerivationRule.newBuilder()
                                 .setTransformationConfig(
                                     DataTransformationConfig.newBuilder()
                                         .setJexlExpression(
                                             JexlExpressionConfig.newBuilder().setJexlExpression(expression).build())
                                         .setOutputType(fieldType)
                                         .build())
                                 .build())
                   .build());
  }

  private Optional<String> toFieldJexlExpression(
      String fieldName, FieldType fieldType, Map<String, String> schemaDocumentPaths) {
    if ("entity_ref".equals(fieldName)) {
      return Optional.of("idp_entity_ref");
    }
    if (KNOWN_EXPRESSIONS.containsKey(fieldName)) {
      return Optional.of(KNOWN_EXPRESSIONS.get(fieldName));
    }
    return toDocumentPath(fieldName, schemaDocumentPaths).map(documentPath -> switch (fieldType) {
      case FIELD_TYPE_STR -> documentPath + ".getAsString()";
      case FIELD_TYPE_BOOL -> documentPath + ".getAsBoolean()";
      case FIELD_TYPE_LONG, FIELD_TYPE_TIMESTAMP -> documentPath + "['$numberLong'].getAsLong()";
      case FIELD_TYPE_DOUBLE -> documentPath + ".getAsDouble()";
      case FIELD_TYPE_LIST, FIELD_TYPE_MAP -> documentPath;
      default -> documentPath;
    });
  }

  private Optional<String> toDocumentPath(String fieldName, Map<String, String> schemaDocumentPaths) {
    if (schemaDocumentPaths.containsKey(fieldName)) {
      return Optional.of(schemaDocumentPaths.get(fieldName));
    }
    if (KNOWN_DOCUMENT_PATHS.containsKey(fieldName)) {
      return Optional.of(KNOWN_DOCUMENT_PATHS.get(fieldName));
    }
    return Optional.empty();
  }

  private Map<String, String> deriveDocumentPaths(String schema) {
    Map<String, String> paths = new HashMap<>();
    if (isEmpty(schema)) {
      return paths;
    }
    try {
      JsonNode root = OBJECT_MAPPER.readTree(schema);
      JsonNode properties = root.path(FIELD_PROPERTIES);
      if (!properties.isObject()) {
        return paths;
      }
      properties.fieldNames().forEachRemaining(fieldName -> {
        JsonNode fieldSchema = properties.get(fieldName);
        String documentPath = "cdc_event.fullDocument." + fieldName;
        paths.put(fieldName, documentPath);
        if (isObjectWithProperties(fieldSchema)) {
          JsonNode nestedProperties = fieldSchema.path(FIELD_PROPERTIES);
          collectNestedPaths(paths, fieldName, documentPath, nestedProperties);
        }
      });
    } catch (Exception ex) {
      log.warn("{} mapper failed to parse schema for derivation path extraction", UdpEventDerivationConstants.LOG_PREFIX, ex);
    }
    return paths;
  }

  private void collectNestedPaths(
      Map<String, String> paths, String fieldPrefix, String documentPathPrefix, JsonNode properties) {
    if (!properties.isObject()) {
      return;
    }
    properties.fieldNames().forEachRemaining(fieldName -> {
      JsonNode fieldSchema = properties.get(fieldName);
      String flattenedName = fieldPrefix + "_" + fieldName;
      String documentPath = documentPathPrefix + "." + fieldName;
      paths.put(flattenedName, documentPath);
      if (isObjectWithProperties(fieldSchema)) {
        collectNestedPaths(paths, flattenedName, documentPath, fieldSchema.path(FIELD_PROPERTIES));
      }
    });
  }

  private boolean isObjectWithProperties(JsonNode fieldSchema) {
    String type = fieldSchema.path(FIELD_TYPE).asText("");
    return fieldSchema.path(FIELD_PROPERTIES).isObject() && fieldSchema.path(FIELD_PROPERTIES).size() > 0
        && (isEmpty(type) || TYPE_OBJECT.equals(type));
  }

  private List<VariableDerivationMapping> filterCoreVariables(List<VariableDerivationMapping> variables) {
    return variables.stream().filter(variable -> CORE_VARIABLE_NAMES.contains(variable.getName())).toList();
  }

  static List<String> attributeNamesForTests(EntityType entityType) {
    return List.copyOf(entityType.getFieldsMap().keySet());
  }
}

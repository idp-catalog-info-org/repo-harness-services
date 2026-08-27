/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import static io.harness.rule.OwnerRule.HARJAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.config_models.derivation_config.v1.EntityDerivationMapping;
import io.harness.config_models.derivation_config.v1.EventDerivationConfig;
import io.harness.idp.catalog.beans.KindType;
import io.harness.idp.catalog.entities.CustomKindEntity;
import io.harness.platform.schema.service.api.v1.EntityFieldMetadata;
import io.harness.platform.schema.service.api.v1.EntityType;
import io.harness.platform.schema.service.api.v1.FieldType;
import io.harness.platform.schema.service.api.v1.ObjectType;
import io.harness.rule.Owner;
import io.harness.shared_models.transformation.v1.AttributeDerivationMapping;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DefaultKindToEventDerivationConfigMapperTest extends CategoryTest {
  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testOwnedEntityUsesEntityTypeIdAndEntityTypeFields() {
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    config.setTypeProvider("idp");
    ObjectType computedType =
        ObjectType.newBuilder()
            .setEntityType(EntityType.newBuilder()
                               .setId("idp:MyKind")
                               .putFields("z_field",
                                   EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_STR).build())
                               .putFields("a_field",
                                   EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_STR).build())
                               .putFields("entity_ref",
                                   EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_STR).build())
                               .build())
            .build();
    DefaultKindToEventDerivationConfigMapper mapper =
        new DefaultKindToEventDerivationConfigMapper(config, new EventDerivationConfigYamlParser());
    CustomKindEntity kind =
        CustomKindEntity.builder()
            .kindType(KindType.CUSTOM)
            .identifier("MyKind")
            .name("My Kind")
            .schema("{\"properties\":{\"a_field\":{\"type\":\"string\"},\"z_field\":{\"type\":\"string\"}}}")
            .build();
    EventDerivationConfig cfg = mapper.toEventDerivationConfig(kind, computedType.getEntityType());
    EntityDerivationMapping m = cfg.getEntities(0);
    assertThat(m.getEntityType()).isEqualTo("idp:MyKind");
    assertThat(m.getIdAttributesCount()).isEqualTo(1);
    assertThat(m.getIdAttributes(0).getName()).isEqualTo("entity_ref");
    assertThat(m.getAttributesList())
        .extracting(AttributeDerivationMapping::getName)
        .containsExactlyInAnyOrderElementsOf(
            DefaultKindToEventDerivationConfigMapper.attributeNamesForTests(computedType.getEntityType()));
    assertThat(cfg.getAttributesList())
        .extracting(AttributeDerivationMapping::getName)
        .contains("a_field", "entity_ref");
    AttributeDerivationMapping entityRefAttr =
        cfg.getAttributesList().stream().filter(attr -> "entity_ref".equals(attr.getName())).findFirst().orElseThrow();
    assertThat(entityRefAttr.getRulesCount()).isEqualTo(1);
    assertThat(entityRefAttr.getRules(0).getTransformationConfig().getJexlExpression().getJexlExpression())
        .isEqualTo("idp_entity_ref");
    AttributeDerivationMapping fieldAttr =
        cfg.getAttributesList().stream().filter(attr -> "a_field".equals(attr.getName())).findFirst().orElseThrow();
    assertThat(fieldAttr.getRulesCount()).isEqualTo(1);
    assertThat(fieldAttr.getRules(0).getTransformationConfig().getJexlExpression().getJexlExpression())
        .isEqualTo("cdc_event.fullDocument.a_field.getAsString()");
    assertThat(cfg.hasScopeExpression()).isTrue();
    assertThat(cfg.hasSource()).isTrue();
    assertThat(cfg.hasTenantId()).isTrue();
    assertThat(cfg.hasEventTimestamp()).isTrue();
    assertThat(cfg.getVariablesList())
        .extracting(variable -> variable.getName())
        .containsExactlyInAnyOrder("cdc_event", "idp_entity_ref");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testDefaultCatalogFieldsUseProductionExpressions() {
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    config.setTypeProvider("idp");
    ObjectType computedType =
        ObjectType.newBuilder()
            .setEntityType(
                EntityType.newBuilder()
                    .setId("idp:MyKind")
                    .putFields("catalog_resource_id",
                        EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_STR).build())
                    .putFields("scope", EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_STR).build())
                    .putFields("created_at",
                        EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_TIMESTAMP).build())
                    .putFields("last_updated_at",
                        EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_TIMESTAMP).build())
                    .build())
            .build();
    DefaultKindToEventDerivationConfigMapper mapper =
        new DefaultKindToEventDerivationConfigMapper(config, new EventDerivationConfigYamlParser());
    CustomKindEntity kind = CustomKindEntity.builder()
                                .kindType(KindType.CUSTOM)
                                .identifier("MyKind")
                                .name("My Kind")
                                .schema("{\"properties\":{}}")
                                .build();

    EventDerivationConfig cfg = mapper.toEventDerivationConfig(kind, computedType.getEntityType());

    AttributeDerivationMapping catalogResourceId = cfg.getAttributesList()
                                                       .stream()
                                                       .filter(attr -> "catalog_resource_id".equals(attr.getName()))
                                                       .findFirst()
                                                       .orElseThrow();
    assertThat(catalogResourceId.getRules(0).getTransformationConfig().getJexlExpression().getJexlExpression())
        .isEqualTo(
            "cdc_event.fullDocument.kind.getAsString() + \":\" + cdc_event.fullDocument.identifier.getAsString()");

    AttributeDerivationMapping scope =
        cfg.getAttributesList().stream().filter(attr -> "scope".equals(attr.getName())).findFirst().orElseThrow();
    assertThat(scope.getRules(0).getTransformationConfig().hasJexlScript()).isTrue();
    assertThat(scope.getRules(0).getTransformationConfig().getJexlScript().getJexlScript()).contains("PROJECT");

    AttributeDerivationMapping createdAt =
        cfg.getAttributesList().stream().filter(attr -> "created_at".equals(attr.getName())).findFirst().orElseThrow();
    assertThat(createdAt.getRules(0).getTransformationConfig().getJexlExpression().getJexlExpression())
        .isEqualTo("cdc_event.fullDocument.createdAt['$numberLong'].getAsLong()");

    AttributeDerivationMapping lastUpdatedAt = cfg.getAttributesList()
                                                   .stream()
                                                   .filter(attr -> "last_updated_at".equals(attr.getName()))
                                                   .findFirst()
                                                   .orElseThrow();
    assertThat(lastUpdatedAt.getRules(0).getTransformationConfig().getJexlExpression().getJexlExpression())
        .isEqualTo("cdc_event.fullDocument.lastUpdatedAt['$numberLong'].getAsLong()");

    assertThat(cfg.getEventTimestamp().getRules(0).getTransformationConfig().getJexlExpression().getJexlExpression())
        .isEqualTo("cdc_event.fullDocument.lastUpdatedAt['$numberLong'].getAsLong()");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testSchemaLongAndTimestampFieldsUseNumberLongUnwrap() {
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    config.setTypeProvider("idp");
    ObjectType computedType =
        ObjectType.newBuilder()
            .setEntityType(
                EntityType.newBuilder()
                    .setId("idp:MyKind")
                    .putFields("spec_retry_count",
                        EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_LONG).build())
                    .putFields("spec_due_at",
                        EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_TIMESTAMP).build())
                    .build())
            .build();
    DefaultKindToEventDerivationConfigMapper mapper =
        new DefaultKindToEventDerivationConfigMapper(config, new EventDerivationConfigYamlParser());
    CustomKindEntity kind = CustomKindEntity.builder()
                                .kindType(KindType.CUSTOM)
                                .identifier("MyKind")
                                .name("My Kind")
                                .schema("{\"properties\":{\"spec\":{\"type\":\"object\",\"properties\":{"
                                    + "\"retry_count\":{\"type\":\"integer\"},"
                                    + "\"due_at\":{\"type\":\"string\",\"format\":\"date-time\"}}}}}")
                                .build();

    EventDerivationConfig cfg = mapper.toEventDerivationConfig(kind, computedType.getEntityType());

    AttributeDerivationMapping retryCount = cfg.getAttributesList()
                                                .stream()
                                                .filter(attr -> "spec_retry_count".equals(attr.getName()))
                                                .findFirst()
                                                .orElseThrow();
    assertThat(retryCount.getRules(0).getTransformationConfig().getJexlExpression().getJexlExpression())
        .isEqualTo("cdc_event.fullDocument.spec.retry_count['$numberLong'].getAsLong()");

    AttributeDerivationMapping dueAt =
        cfg.getAttributesList().stream().filter(attr -> "spec_due_at".equals(attr.getName())).findFirst().orElseThrow();
    assertThat(dueAt.getRules(0).getTransformationConfig().getJexlExpression().getJexlExpression())
        .isEqualTo("cdc_event.fullDocument.spec.due_at['$numberLong'].getAsLong()");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testNestedSpecFieldWithUnderscoresUsesExactSchemaPath() {
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    config.setTypeProvider("idp");
    ObjectType computedType =
        ObjectType.newBuilder()
            .setEntityType(EntityType.newBuilder()
                               .setId("idp:MyKind")
                               .putFields("spec_a_c_b_d",
                                   EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_STR).build())
                               .build())
            .build();
    DefaultKindToEventDerivationConfigMapper mapper =
        new DefaultKindToEventDerivationConfigMapper(config, new EventDerivationConfigYamlParser());
    CustomKindEntity kind = CustomKindEntity.builder()
                                .kindType(KindType.CUSTOM)
                                .identifier("MyKind")
                                .name("My Kind")
                                .schema("{\"properties\":{\"spec\":{\"type\":\"object\",\"properties\":{\"a_c\":{"
                                    + "\"type\":\"object\",\"properties\":{\"b_d\":{\"type\":\"string\"}}}}}}}")
                                .build();

    EventDerivationConfig cfg = mapper.toEventDerivationConfig(kind, computedType.getEntityType());
    AttributeDerivationMapping fieldAttr = cfg.getAttributesList()
                                               .stream()
                                               .filter(attr -> "spec_a_c_b_d".equals(attr.getName()))
                                               .findFirst()
                                               .orElseThrow();
    assertThat(fieldAttr.getRules(0).getTransformationConfig().getJexlExpression().getJexlExpression())
        .isEqualTo("cdc_event.fullDocument.spec.a_c.b_d.getAsString()");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testFieldWithNoDerivationPathIsSkipped() {
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    config.setTypeProvider("idp");
    ObjectType computedType =
        ObjectType.newBuilder()
            .setEntityType(EntityType.newBuilder()
                               .setId("idp:MyKind")
                               .putFields("spec_a_c_b_d",
                                   EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_STR).build())
                               .putFields("entity_ref",
                                   EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_STR).build())
                               .build())
            .build();
    DefaultKindToEventDerivationConfigMapper mapper =
        new DefaultKindToEventDerivationConfigMapper(config, new EventDerivationConfigYamlParser());
    CustomKindEntity kind = CustomKindEntity.builder()
                                .kindType(KindType.CUSTOM)
                                .identifier("MyKind")
                                .name("My Kind")
                                .schema("{\"properties\":{}}")
                                .build();

    EventDerivationConfig cfg = mapper.toEventDerivationConfig(kind, computedType.getEntityType());

    assertThat(cfg.getAttributesList())
        .extracting(AttributeDerivationMapping::getName)
        .doesNotContain("spec_a_c_b_d")
        .contains("entity_ref");
    assertThat(cfg.getEntities(0).getAttributesList())
        .extracting(AttributeDerivationMapping::getName)
        .doesNotContain("spec_a_c_b_d")
        .contains("entity_ref");
  }
}

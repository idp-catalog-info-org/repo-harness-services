/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import static io.harness.rule.OwnerRule.HARJAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.config_models.derivation_config.v1.EntityDerivationMapping;
import io.harness.config_models.derivation_config.v1.EventDerivationConfig;
import io.harness.platform.schema.service.api.v1.FieldType;
import io.harness.rule.Owner;
import io.harness.shared_models.transformation.v1.AttributeDerivationMapping;

import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EventDerivationConfigEntitiesMergerTest extends CategoryTest {
  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testCreatePathUsesBootstrapAndOwnedEntityOnly() {
    EventDerivationConfig bootstrap =
        EventDerivationConfig.newBuilder()
            .addEntities(EntityDerivationMapping.newBuilder().setEntityType("ignore:me").build())
            .addAttributes(
                AttributeDerivationMapping.newBuilder().setName("a").setType(FieldType.FIELD_TYPE_STR).build())
            .build();
    EventDerivationConfig owned =
        EventDerivationConfig.newBuilder()
            .addEntities(EntityDerivationMapping.newBuilder()
                             .setEntityType("idp:svc")
                             .addAttributes(AttributeDerivationMapping.newBuilder().setName("a").build())
                             .build())
            .addAttributes(
                AttributeDerivationMapping.newBuilder().setName("a").setType(FieldType.FIELD_TYPE_STR).build())
            .build();

    EventDerivationConfig out = EventDerivationConfigEntitiesMerger.mergeForPublish(null, owned);
    assertThat(out.getEntitiesCount()).isEqualTo(1);
    assertThat(out.getEntities(0).getEntityType()).isEqualTo("idp:svc");
    assertThat(out.getEntities(0).getAttributesList())
        .extracting(AttributeDerivationMapping::getName)
        .containsExactly("a");
    assertThat(out.getAttributesList()).extracting(AttributeDerivationMapping::getName).containsExactly("a");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testMergeReplacesAttributesForMatchingEntityAndPreservesOthers() {
    EventDerivationConfig existing =
        EventDerivationConfig.newBuilder()
            .addRelationships(io.harness.config_models.derivation_config.v1.RelationshipDerivationMapping.newBuilder()
                                  .setRelationshipType("idp:rel")
                                  .build())
            .addEntities(EntityDerivationMapping.newBuilder()
                             .setEntityType("idp:other")
                             .addAttributes(AttributeDerivationMapping.newBuilder().setName("keep").build())
                             .build())
            .addEntities(EntityDerivationMapping.newBuilder()
                             .setEntityType("idp:svc")
                             .addAttributes(AttributeDerivationMapping.newBuilder().setName("old").build())
                             .build())
            .addAttributes(
                AttributeDerivationMapping.newBuilder().setName("keep_attr").setType(FieldType.FIELD_TYPE_STR).build())
            .addAttributes(
                AttributeDerivationMapping.newBuilder().setName("new1").setType(FieldType.FIELD_TYPE_LIST).build())
            .build();
    EventDerivationConfig owned =
        EventDerivationConfig.newBuilder()
            .addEntities(EntityDerivationMapping.newBuilder()
                             .setEntityType("idp:svc")
                             .addAttributes(AttributeDerivationMapping.newBuilder().setName("new1").build())
                             .addAttributes(AttributeDerivationMapping.newBuilder().setName("new2").build())
                             .build())
            .addAttributes(
                AttributeDerivationMapping.newBuilder().setName("new1").setType(FieldType.FIELD_TYPE_STR).build())
            .addAttributes(
                AttributeDerivationMapping.newBuilder().setName("new2").setType(FieldType.FIELD_TYPE_STR).build())
            .build();

    EventDerivationConfig out = EventDerivationConfigEntitiesMerger.mergeForPublish(existing, owned);
    assertThat(out.getRelationshipsCount()).isEqualTo(1);
    assertThat(out.getEntitiesCount()).isEqualTo(2);
    assertThat(out.getEntities(0).getEntityType()).isEqualTo("idp:other");
    assertThat(out.getEntities(0).getAttributes(0).getName()).isEqualTo("keep");
    assertThat(out.getEntities(1).getEntityType()).isEqualTo("idp:svc");
    assertThat(out.getEntities(1).getAttributesList())
        .extracting(AttributeDerivationMapping::getName)
        .containsExactly("new1", "new2");
    assertThat(out.getAttributesList())
        .extracting(AttributeDerivationMapping::getName)
        .containsExactly("keep_attr", "new1", "new2");
    assertThat(out.getAttributesList()
                   .stream()
                   .filter(attribute -> "new1".equals(attribute.getName()))
                   .findFirst()
                   .orElseThrow()
                   .getType())
        .isEqualTo(FieldType.FIELD_TYPE_STR);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testMergeAppendsWhenEntityTypeMissing() {
    EventDerivationConfig existing =
        EventDerivationConfig.newBuilder()
            .addEntities(EntityDerivationMapping.newBuilder().setEntityType("idp:other").build())
            .build();
    EventDerivationConfig owned =
        EventDerivationConfig.newBuilder()
            .addEntities(EntityDerivationMapping.newBuilder().setEntityType("idp:svc").build())
            .build();

    EventDerivationConfig out = EventDerivationConfigEntitiesMerger.mergeForPublish(existing, owned);
    assertThat(out.getEntitiesCount()).isEqualTo(2);
    assertThat(out.getEntities(1).getEntityType()).isEqualTo("idp:svc");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testMergeBackfillsCoreDerivationSectionsFromBootstrapWhenMissingInExisting() {
    EventDerivationConfig bootstrap = new EventDerivationConfigYamlParser().parseForCdcKind("service");
    EventDerivationConfig existing =
        EventDerivationConfig.newBuilder()
            .addEntities(EntityDerivationMapping.newBuilder().setEntityType("idp:service").build())
            .build();
    EventDerivationConfig owned =
        bootstrap.toBuilder()
            .addEntities(EntityDerivationMapping.newBuilder()
                             .setEntityType("idp:service")
                             .addAttributes(AttributeDerivationMapping.newBuilder().setName("identifier").build())
                             .build())
            .build();

    EventDerivationConfig out = EventDerivationConfigEntitiesMerger.mergeForPublish(existing, owned);

    assertThat(out.hasScopeExpression()).isFalse();
    assertThat(out.hasSource()).isFalse();
    assertThat(out.hasTenantId()).isFalse();
    assertThat(out.hasEventTimestamp()).isFalse();
    assertThat(out.getVariablesList().stream().map(v -> v.getName()).collect(Collectors.toSet())).isEmpty();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testMergeFailsWhenComputedContainsMoreThanOneOwnedEntity() {
    EventDerivationConfig existing =
        EventDerivationConfig.newBuilder()
            .addEntities(EntityDerivationMapping.newBuilder().setEntityType("idp:service").build())
            .build();
    EventDerivationConfig computed =
        EventDerivationConfig.newBuilder()
            .addEntities(EntityDerivationMapping.newBuilder().setEntityType("idp:service").build())
            .addEntities(EntityDerivationMapping.newBuilder().setEntityType("idp:service_v2").build())
            .build();

    assertThatThrownBy(() -> EventDerivationConfigEntitiesMerger.mergeForPublish(existing, computed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly one owned entity mapping");
  }
}

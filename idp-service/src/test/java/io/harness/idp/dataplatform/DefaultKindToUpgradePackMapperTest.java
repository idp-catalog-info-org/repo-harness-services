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
import io.harness.idp.catalog.beans.KindType;
import io.harness.idp.catalog.entities.CustomKindEntity;
import io.harness.platform.schema.service.api.v1.ObjectType;
import io.harness.platform.type.ingestion.TypeIngestionRequest;
import io.harness.platform.type.registry.model.v1.UpgradePack;
import io.harness.rule.Owner;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DefaultKindToUpgradePackMapperTest extends CategoryTest {
  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testToObjectTypeBuildsEntityTypeFromKindSchema() {
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    config.setTypeProvider("idp");

    DefaultKindToUpgradePackMapper mapper = new DefaultKindToUpgradePackMapper(config);
    ObjectType objectType = mapper.toObjectType(
        CustomKindEntity.builder()
            .kindType(KindType.CUSTOM)
            .accountIdentifier("acc")
            .identifier("service")
            .name("Service")
            .description("desc")
            .schema("{\"type\":\"object\",\"properties\":{\"owner\":{\"type\":\"string\"},\"type\":{\"type\":"
                + "\"string\"},\"spec\":{\"type\":\"object\",\"properties\":{\"tags\":{\"type\":\"array\"},"
                + "\"config\":{\"type\":\"object\",\"properties\":{\"region\":{\"type\":\"string\"}}}}},"
                + "\"metadata\":{\"type\":\"object\",\"properties\":{\"labels\":{\"type\":\"object\","
                + "\"properties\":{\"team\":{\"type\":\"string\"}}}}}}}")
            .build());

    Descriptors.FieldDescriptor entityTypeField = objectType.getDescriptorForType().findFieldByName("entity_type");
    assertThat(entityTypeField).isNotNull();

    Message entityType = (Message) objectType.getField(entityTypeField);
    assertThat(readStringField(entityType, "id")).isEqualTo("idp:service");
    assertThat(readStringField(entityType, "tenant_field")).isEqualTo("account_identifier");
    assertThat(readRepeatedStrings(entityType, "id_fields")).contains("entity_ref");

    assertThat(readFieldMetadataKeys(entityType))
        .contains("account_identifier", "entity_ref", "catalog_resource_id", "scope", "unique_id", "parent_unique_id",
            "created_at", "last_updated_at", "spec", "metadata", "type", "owner", "spec_tags", "spec_config",
            "spec_config_region", "metadata_labels", "metadata_labels_team")
        .doesNotContain("tags", "config");
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testRequestAssemblerBuildsEnvelopeWithTenantAndPack() {
    TypeIngestionRequestAssembler assembler = new DefaultTypeIngestionRequestAssembler();
    UpgradePack upgradePack = UpgradePack.newBuilder().build();

    TypeIngestionRequest request = assembler.toRequest("account1", upgradePack);

    assertThat(request.getTenantId()).isEqualTo("account1");
    assertThat(request.getUpgradePack()).isEqualTo(upgradePack);
  }

  private String readStringField(Message message, String fieldName) {
    Descriptors.FieldDescriptor fieldDescriptor = message.getDescriptorForType().findFieldByName(fieldName);
    assertThat(fieldDescriptor).isNotNull();
    return (String) message.getField(fieldDescriptor);
  }

  @SuppressWarnings("unchecked")
  private Iterable<String> readRepeatedStrings(Message message, String fieldName) {
    Descriptors.FieldDescriptor fieldDescriptor = message.getDescriptorForType().findFieldByName(fieldName);
    assertThat(fieldDescriptor).isNotNull();
    return (Iterable<String>) message.getField(fieldDescriptor);
  }

  private Iterable<String> readFieldMetadataKeys(Message entityType) {
    Descriptors.FieldDescriptor fieldsDescriptor = entityType.getDescriptorForType().findFieldByName("fields");
    assertThat(fieldsDescriptor).isNotNull();
    return ((java.util.List<?>) entityType.getField(fieldsDescriptor))
        .stream()
        .map(entry -> (Message) entry)
        .map(entry -> entry.getField(entry.getDescriptorForType().findFieldByName("key")))
        .map(String.class ::cast)
        .toList();
  }
}

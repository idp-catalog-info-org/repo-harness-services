/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import static io.harness.rule.OwnerRule.HARJAS;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.config_models.derivation_config.v1.EntityDerivationMapping;
import io.harness.config_models.derivation_config.v1.EventDerivationConfig;
import io.harness.idp.catalog.beans.KindType;
import io.harness.idp.catalog.entities.CustomKindEntity;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.platform.schema.service.api.v1.EntityFieldMetadata;
import io.harness.platform.schema.service.api.v1.EntityType;
import io.harness.platform.schema.service.api.v1.FieldType;
import io.harness.platform.schema.service.api.v1.ObjectType;
import io.harness.platform.type.ingestion.TypeIngestionRequest;
import io.harness.platform.type.registry.model.v1.ConfigUpgradeCommand;
import io.harness.rule.Owner;

import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class CustomKindUdpPublisherImplTest extends CategoryTest {
  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testPublishCreateOrUpdateNoOpWhenDisabled() {
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    config.setEnabled(false);

    KindToObjectTypeMapper mapper = Mockito.mock(KindToObjectTypeMapper.class);
    KindToEventDerivationConfigMapper derivationMapper = Mockito.mock(KindToEventDerivationConfigMapper.class);
    TypeIngestionRequestAssembler assembler = Mockito.mock(TypeIngestionRequestAssembler.class);
    TypeIngestionKafkaClient client = Mockito.mock(TypeIngestionKafkaClient.class);
    UdpEntityTypeReader reader = Mockito.mock(UdpEntityTypeReader.class);
    UdpEventDerivationConfigReader derivationReader = Mockito.mock(UdpEventDerivationConfigReader.class);

    CustomKindUdpPublisher publisher =
        new CustomKindUdpPublisherImpl(config, mapper, derivationMapper, assembler, client, reader, derivationReader);

    publisher.publishCreateOrUpdate("acc", customKind());

    verifyNoInteractions(mapper, derivationMapper, assembler, client, reader, derivationReader);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testPublishCreateOrUpdateNoOpForNonCustomKind() {
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    config.setEnabled(true);

    KindToObjectTypeMapper mapper = Mockito.mock(KindToObjectTypeMapper.class);
    KindToEventDerivationConfigMapper derivationMapper = Mockito.mock(KindToEventDerivationConfigMapper.class);
    TypeIngestionRequestAssembler assembler = Mockito.mock(TypeIngestionRequestAssembler.class);
    TypeIngestionKafkaClient client = Mockito.mock(TypeIngestionKafkaClient.class);
    UdpEntityTypeReader reader = Mockito.mock(UdpEntityTypeReader.class);
    UdpEventDerivationConfigReader derivationReader = Mockito.mock(UdpEventDerivationConfigReader.class);

    CustomKindUdpPublisher publisher =
        new CustomKindUdpPublisherImpl(config, mapper, derivationMapper, assembler, client, reader, derivationReader);
    KindEntity builtInKind = customKind().toBuilder().kindType(KindType.BUILT_IN).build();

    publisher.publishCreateOrUpdate("acc", builtInKind);

    verifyNoInteractions(mapper, derivationMapper, assembler, client, reader, derivationReader);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testPublishCreateOrUpdateOrchestratesMapperAssemblerAndKafkaClient() {
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    config.setEnabled(true);

    KindToObjectTypeMapper mapper = Mockito.mock(KindToObjectTypeMapper.class);
    KindToEventDerivationConfigMapper derivationMapper = Mockito.mock(KindToEventDerivationConfigMapper.class);
    TypeIngestionRequestAssembler assembler = Mockito.mock(TypeIngestionRequestAssembler.class);
    TypeIngestionKafkaClient client = Mockito.mock(TypeIngestionKafkaClient.class);
    UdpEntityTypeReader reader = Mockito.mock(UdpEntityTypeReader.class);
    UdpEventDerivationConfigReader derivationReader = Mockito.mock(UdpEventDerivationConfigReader.class);

    ObjectType computedType = buildObjectType(Map.of("spec_owner", "computed owner"));
    ObjectType existingType = ObjectType.newBuilder()
                                  .setEntityType(EntityType.newBuilder()
                                                     .setId("idp:service")
                                                     .putFields("other_field", fieldMetadata("existing"))
                                                     .build())
                                  .build();
    TypeIngestionRequest request = TypeIngestionRequest.newBuilder().setTenantId("acc").build();
    when(mapper.toObjectType(any())).thenReturn(computedType);
    when(reader.getEntityType("acc", "idp:service")).thenReturn(Optional.of(existingType));
    EventDerivationConfig owned =
        EventDerivationConfig.newBuilder()
            .addEntities(
                EntityDerivationMapping.newBuilder()
                    .setEntityType("idp:service")
                    .addAttributes(io.harness.shared_models.transformation.v1.AttributeDerivationMapping.newBuilder()
                                       .setName("identifier")
                                       .build())
                    .build())
            .build();
    when(derivationMapper.toEventDerivationConfig(any(), any())).thenReturn(owned);
    when(derivationReader.getConfig("acc", UdpEventDerivationConstants.derivationConfigUuid("acc", "service")))
        .thenReturn(Optional.empty());
    when(assembler.toRequest(eq("acc"), any())).thenReturn(request);
    when(client.sendTypeIngestionRequest("acc", request)).thenReturn(true);

    CustomKindUdpPublisher publisher =
        new CustomKindUdpPublisherImpl(config, mapper, derivationMapper, assembler, client, reader, derivationReader);
    publisher.publishCreateOrUpdate("acc", customKind());

    verify(mapper).toObjectType(any());
    verify(reader).getEntityType("acc", "idp:service");
    verify(derivationMapper).toEventDerivationConfig(any(), eq(computedType.getEntityType()));
    verify(derivationReader).getConfig("acc", UdpEventDerivationConstants.derivationConfigUuid("acc", "service"));
    verify(assembler).toRequest(eq("acc"), Mockito.argThat(mergedUpgradePack -> {
      EntityType mergedEntityType = mergedUpgradePack.getObjectTypeUpgradeSpecs().getSpecs(0).getType().getEntityType();
      boolean objectTypeOk =
          mergedEntityType.containsFields("other_field") && mergedEntityType.containsFields("spec_owner");
      boolean hasDerivationConfig = mergedUpgradePack.getConfigUpgradeSpecs().getSpecsCount() == 2
          && mergedUpgradePack.getConfigUpgradeSpecs().getSpecs(0).getCommand()
              == ConfigUpgradeCommand.CONFIG_UPGRADE_COMMAND_UPSERT
          && mergedUpgradePack.getConfigUpgradeSpecs().getSpecs(0).getConfig().getPayload().getJsonValue().contains(
              "identifier");
      boolean hasConnectorMapping =
          mergedUpgradePack.getConfigUpgradeSpecs().getSpecs(1).getConfig().getId().getTypeId().equals(
              "connector_mapping_config");
      return objectTypeOk && hasDerivationConfig && hasConnectorMapping;
    }));
    verify(client).sendTypeIngestionRequest("acc", request);
    verify(client, never()).sendTypeIngestionRequest("acc", TypeIngestionRequest.getDefaultInstance());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testPublishCreateOrUpdateUsesComputedTypeWhenExistingTypeNotFound() {
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    config.setEnabled(true);

    KindToObjectTypeMapper mapper = Mockito.mock(KindToObjectTypeMapper.class);
    KindToEventDerivationConfigMapper derivationMapper = Mockito.mock(KindToEventDerivationConfigMapper.class);
    TypeIngestionRequestAssembler assembler = Mockito.mock(TypeIngestionRequestAssembler.class);
    TypeIngestionKafkaClient client = Mockito.mock(TypeIngestionKafkaClient.class);
    UdpEntityTypeReader reader = Mockito.mock(UdpEntityTypeReader.class);
    UdpEventDerivationConfigReader derivationReader = Mockito.mock(UdpEventDerivationConfigReader.class);

    ObjectType computedType = buildObjectType(Map.of("spec_owner", "computed owner"));
    TypeIngestionRequest request = TypeIngestionRequest.newBuilder().setTenantId("acc").build();
    when(mapper.toObjectType(any())).thenReturn(computedType);
    when(reader.getEntityType("acc", "idp:service")).thenReturn(Optional.empty());
    when(derivationMapper.toEventDerivationConfig(any(), any()))
        .thenReturn(EventDerivationConfig.newBuilder()
                        .addEntities(EntityDerivationMapping.newBuilder().setEntityType("idp:service").build())
                        .build());
    when(derivationReader.getConfig(eq("acc"), any())).thenReturn(Optional.empty());
    when(assembler.toRequest(eq("acc"), any())).thenReturn(request);
    when(client.sendTypeIngestionRequest("acc", request)).thenReturn(true);

    CustomKindUdpPublisher publisher =
        new CustomKindUdpPublisherImpl(config, mapper, derivationMapper, assembler, client, reader, derivationReader);
    publisher.publishCreateOrUpdate("acc", customKind());

    verify(assembler).toRequest(eq("acc"), Mockito.argThat(computedUpgradePack -> {
      EntityType entityType = computedUpgradePack.getObjectTypeUpgradeSpecs().getSpecs(0).getType().getEntityType();
      return entityType.containsFields("spec_owner");
    }));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testPublishCreateOrUpdateMergePreservesUnrelatedEntitiesAndPatchesOwnedAttributes() {
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    config.setEnabled(true);
    KindToObjectTypeMapper mapper = Mockito.mock(KindToObjectTypeMapper.class);
    KindToEventDerivationConfigMapper derivationMapper = Mockito.mock(KindToEventDerivationConfigMapper.class);
    TypeIngestionRequestAssembler assembler = Mockito.mock(TypeIngestionRequestAssembler.class);
    TypeIngestionKafkaClient client = Mockito.mock(TypeIngestionKafkaClient.class);
    UdpEntityTypeReader reader = Mockito.mock(UdpEntityTypeReader.class);
    UdpEventDerivationConfigReader derivationReader = Mockito.mock(UdpEventDerivationConfigReader.class);

    ObjectType computedType = buildObjectType(Map.of("spec_owner", "x"));
    when(mapper.toObjectType(any())).thenReturn(computedType);
    when(reader.getEntityType("acc", "idp:service")).thenReturn(Optional.empty());

    EventDerivationConfig owned =
        EventDerivationConfig.newBuilder()
            .addEntities(
                EntityDerivationMapping.newBuilder()
                    .setEntityType("idp:service")
                    .addAttributes(io.harness.shared_models.transformation.v1.AttributeDerivationMapping.newBuilder()
                                       .setName("account_identifier")
                                       .build())
                    .addAttributes(io.harness.shared_models.transformation.v1.AttributeDerivationMapping.newBuilder()
                                       .setName("identifier")
                                       .build())
                    .build())
            .build();
    when(derivationMapper.toEventDerivationConfig(any(), any())).thenReturn(owned);

    EventDerivationConfig existing =
        EventDerivationConfig.newBuilder()
            .addEntities(
                EntityDerivationMapping.newBuilder()
                    .setEntityType("idp:other")
                    .addAttributes(io.harness.shared_models.transformation.v1.AttributeDerivationMapping.newBuilder()
                                       .setName("keep_me")
                                       .build())
                    .build())
            .addEntities(
                EntityDerivationMapping.newBuilder()
                    .setEntityType("idp:service")
                    .addAttributes(io.harness.shared_models.transformation.v1.AttributeDerivationMapping.newBuilder()
                                       .setName("stale")
                                       .build())
                    .build())
            .build();
    when(derivationReader.getConfig(eq("acc"), any())).thenReturn(Optional.of(existing));
    TypeIngestionRequest request = TypeIngestionRequest.newBuilder().setTenantId("acc").build();
    when(assembler.toRequest(eq("acc"), any())).thenReturn(request);
    when(client.sendTypeIngestionRequest("acc", request)).thenReturn(true);

    CustomKindUdpPublisher publisher =
        new CustomKindUdpPublisherImpl(config, mapper, derivationMapper, assembler, client, reader, derivationReader);
    publisher.publishCreateOrUpdate("acc", customKind());

    verify(assembler).toRequest(eq("acc"), Mockito.argThat(pack -> {
      String json = pack.getConfigUpgradeSpecs().getSpecs(0).getConfig().getPayload().getJsonValue();
      return json.contains("account_identifier") && json.contains("identifier") && !json.contains("stale")
          && json.contains("keep_me");
    }));
  }

  private CustomKindEntity customKind() {
    return CustomKindEntity.builder()
        .kindType(KindType.CUSTOM)
        .accountIdentifier("acc")
        .identifier("service")
        .name("Service")
        .schema("{\"type\":\"object\"}")
        .build();
  }

  private ObjectType buildObjectType(Map<String, String> fields) {
    EntityType.Builder entityType = EntityType.newBuilder().setId("idp:service");
    fields.forEach((key, description) -> entityType.putFields(key, fieldMetadata(description)));
    return ObjectType.newBuilder().setEntityType(entityType.build()).build();
  }

  private EntityFieldMetadata fieldMetadata(String description) {
    return EntityFieldMetadata.newBuilder().setFieldType(FieldType.FIELD_TYPE_STR).setDescription(description).build();
  }
}

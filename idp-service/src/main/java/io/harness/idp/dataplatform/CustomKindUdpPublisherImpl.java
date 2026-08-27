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
import io.harness.config_models.derivation_config.v1.EventDerivationConfig;
import io.harness.idp.catalog.beans.KindType;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.platform.config.service.api.v1.Config;
import io.harness.platform.config.service.api.v1.ConfigPayload;
import io.harness.platform.config.service.api.v1.ConfigReference;
import io.harness.platform.schema.service.api.v1.EntityType;
import io.harness.platform.schema.service.api.v1.ObjectType;
import io.harness.platform.type.ingestion.TypeIngestionRequest;
import io.harness.platform.type.registry.model.v1.ConfigUpgradeCommand;
import io.harness.platform.type.registry.model.v1.ConfigUpgradeSpec;
import io.harness.platform.type.registry.model.v1.ConfigUpgradeSpecs;
import io.harness.platform.type.registry.model.v1.ObjectTypeUpgradeCommand;
import io.harness.platform.type.registry.model.v1.ObjectTypeUpgradeSpec;
import io.harness.platform.type.registry.model.v1.ObjectTypeUpgradeSpecs;
import io.harness.platform.type.registry.model.v1.UpgradePack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CustomKindUdpPublisherImpl implements CustomKindUdpPublisher {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final UdpTypeIngestionConfig udpTypeIngestionConfig;
  private final KindToObjectTypeMapper kindToObjectTypeMapper;
  private final KindToEventDerivationConfigMapper kindToEventDerivationConfigMapper;
  private final TypeIngestionRequestAssembler typeIngestionRequestAssembler;
  private final TypeIngestionKafkaClient typeIngestionKafkaClient;
  private final UdpEntityTypeReader udpEntityTypeReader;
  private final UdpEventDerivationConfigReader udpEventDerivationConfigReader;

  @Inject
  public CustomKindUdpPublisherImpl(UdpTypeIngestionConfig udpTypeIngestionConfig,
      KindToObjectTypeMapper kindToObjectTypeMapper,
      KindToEventDerivationConfigMapper kindToEventDerivationConfigMapper,
      TypeIngestionRequestAssembler typeIngestionRequestAssembler, TypeIngestionKafkaClient typeIngestionKafkaClient,
      UdpEntityTypeReader udpEntityTypeReader, UdpEventDerivationConfigReader udpEventDerivationConfigReader) {
    this.udpTypeIngestionConfig = udpTypeIngestionConfig;
    this.kindToObjectTypeMapper = kindToObjectTypeMapper;
    this.kindToEventDerivationConfigMapper = kindToEventDerivationConfigMapper;
    this.typeIngestionRequestAssembler = typeIngestionRequestAssembler;
    this.typeIngestionKafkaClient = typeIngestionKafkaClient;
    this.udpEntityTypeReader = udpEntityTypeReader;
    this.udpEventDerivationConfigReader = udpEventDerivationConfigReader;
  }

  @Override
  public void publishCreateOrUpdate(String accountIdentifier, KindEntity kindEntity) {
    log.info("{} publisher start accountIdPresent={} kindIdentifier={} kindType={}",
        UdpEventDerivationConstants.LOG_PREFIX, !isEmpty(accountIdentifier),
        kindEntity == null ? null : kindEntity.getIdentifier(), kindEntity == null ? null : kindEntity.getKindType());
    if (!udpTypeIngestionConfig.isEnabled() || isEmpty(accountIdentifier) || kindEntity == null
        || !KindType.CUSTOM.equals(kindEntity.getKindType())) {
      log.debug("{} publisher skip enabled={} accountIdentifierPresent={} kindPresent={} customKind={}",
          UdpEventDerivationConstants.LOG_PREFIX, udpTypeIngestionConfig.isEnabled(), !isEmpty(accountIdentifier),
          kindEntity != null, kindEntity != null && KindType.CUSTOM.equals(kindEntity.getKindType()));
      return;
    }

    ObjectType computedType = kindToObjectTypeMapper.toObjectType(kindEntity);
    log.info("{} publisher computed objectType entityTypeId={}", UdpEventDerivationConstants.LOG_PREFIX,
        computedType.hasEntityType() ? computedType.getEntityType().getId() : null);
    ObjectType finalType = mergeWithExistingTypeFields(accountIdentifier, computedType);
    // Derive attributes from the computed (IDP-owned) type, not the merged one: the merged type can carry
    // fields owned by other writers or left behind by renames, which have no derivation path in the schema.
    EventDerivationConfig computedEntitiesConfig = kindToEventDerivationConfigMapper.toEventDerivationConfig(
        kindEntity, computedType.hasEntityType() ? computedType.getEntityType() : null);
    String derivationUuid =
        UdpEventDerivationConstants.derivationConfigUuid(accountIdentifier, kindEntity.getIdentifier());
    Optional<EventDerivationConfig> existingDerivation =
        udpEventDerivationConfigReader.getConfig(accountIdentifier, derivationUuid);
    EventDerivationConfig mergedDerivation =
        EventDerivationConfigEntitiesMerger.mergeForPublish(existingDerivation.orElse(null), computedEntitiesConfig);
    log.info("{} publisher derivation existingPresent={} computedEntities={} mergedEntities={}",
        UdpEventDerivationConstants.LOG_PREFIX, existingDerivation.isPresent(),
        computedEntitiesConfig.getEntitiesCount(), mergedDerivation.getEntitiesCount());

    UpgradePack finalUpgradePack =
        buildUpgradePack(finalType, mergedDerivation, accountIdentifier, kindEntity.getIdentifier(), derivationUuid);
    TypeIngestionRequest request = typeIngestionRequestAssembler.toRequest(accountIdentifier, finalUpgradePack);
    log.info("{} publisher sending request tenantId={} objectSpecs={} configSpecs={}",
        UdpEventDerivationConstants.LOG_PREFIX, request.getTenantId(),
        request.getUpgradePack().getObjectTypeUpgradeSpecs().getSpecsCount(),
        request.getUpgradePack().getConfigUpgradeSpecs().getSpecsCount());
    boolean sent = typeIngestionKafkaClient.sendTypeIngestionRequest(accountIdentifier, request);

    if (sent) {
      log.info("{} publisher request sent account={} kind={}", UdpEventDerivationConstants.LOG_PREFIX,
          accountIdentifier, kindEntity.getIdentifier());
    } else {
      log.warn("{} publisher request failed account={} kind={}", UdpEventDerivationConstants.LOG_PREFIX,
          accountIdentifier, kindEntity.getIdentifier());
    }
  }

  private ObjectType mergeWithExistingTypeFields(String accountIdentifier, ObjectType computedType) {
    if (!computedType.hasEntityType()) {
      log.debug(
          "{} publisher entity merge skip no entityType on computed object", UdpEventDerivationConstants.LOG_PREFIX);
      return computedType;
    }

    EntityType computedEntityType = computedType.getEntityType();
    if (isEmpty(computedEntityType.getId())) {
      log.debug("{} publisher entity merge skip empty computed entityType id", UdpEventDerivationConstants.LOG_PREFIX);
      return computedType;
    }

    log.info("{} publisher entity merge fetch start typeId={}", UdpEventDerivationConstants.LOG_PREFIX,
        computedEntityType.getId());
    Optional<ObjectType> existingType =
        udpEntityTypeReader.getEntityType(accountIdentifier, computedEntityType.getId());
    if (existingType.isEmpty() || !existingType.get().hasEntityType()) {
      log.info("{} publisher entity merge no existing typeId={}, using computed",
          UdpEventDerivationConstants.LOG_PREFIX, computedEntityType.getId());
      return computedType;
    }

    int existingFieldCount = existingType.get().getEntityType().getFieldsCount();
    int computedFieldCount = computedEntityType.getFieldsCount();
    EntityType mergedEntityType =
        existingType.get().getEntityType().toBuilder().putAllFields(computedEntityType.getFieldsMap()).build();
    // todo: support deletion of fields
    log.info("{} publisher entity merge complete typeId={} existingFields={} computedFields={} mergedFields={}",
        UdpEventDerivationConstants.LOG_PREFIX, computedEntityType.getId(), existingFieldCount, computedFieldCount,
        mergedEntityType.getFieldsCount());

    return existingType.get().toBuilder().setEntityType(mergedEntityType).build();
  }

  private UpgradePack buildUpgradePack(ObjectType objectType, EventDerivationConfig derivationConfig,
      String accountIdentifier, String kindIdentifier, String derivationConfigUuid) {
    ObjectTypeUpgradeSpec objectSpec = ObjectTypeUpgradeSpec.newBuilder()
                                           .setCommand(ObjectTypeUpgradeCommand.OBJECT_TYPE_UPGRADE_COMMAND_UPSERT)
                                           .setType(objectType)
                                           .build();

    ConfigUpgradeSpec derivationConfigSpec =
        buildDerivationConfigSpec(derivationConfig, accountIdentifier, kindIdentifier, derivationConfigUuid);
    ConfigUpgradeSpec connectorMappingSpec =
        buildConnectorMappingConfigSpec(objectType, accountIdentifier, kindIdentifier);

    return UpgradePack.newBuilder()
        .setObjectTypeUpgradeSpecs(ObjectTypeUpgradeSpecs.newBuilder().addSpecs(objectSpec).build())
        .setConfigUpgradeSpecs(
            ConfigUpgradeSpecs.newBuilder().addSpecs(derivationConfigSpec).addSpecs(connectorMappingSpec).build())
        .build();
  }

  private ConfigUpgradeSpec buildDerivationConfigSpec(EventDerivationConfig derivationConfig, String accountIdentifier,
      String kindIdentifier, String derivationConfigUuid) {
    String jsonPayload;
    try {
      jsonPayload = JsonFormat.printer().omittingInsignificantWhitespace().print(derivationConfig);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Failed to serialize EventDerivationConfig for kind " + kindIdentifier, e);
    }
    Config config =
        Config.newBuilder()
            .setId(ConfigReference.newBuilder()
                       .setTypeId(UdpEventDerivationConstants.EVENT_DERIVATION_CONFIG_TYPE_ID)
                       .setUuid(derivationConfigUuid)
                       .build())
            .setName(UdpEventDerivationConstants.derivationConfigName(accountIdentifier, kindIdentifier))
            .setDescription("IDP catalog kind " + kindIdentifier + " entity event derivation config")
            .setEnabled(true)
            .setPayload(ConfigPayload.newBuilder()
                            .setJsonValue(jsonPayload)
                            .setSerdeProtoClass(UdpEventDerivationConstants.EVENT_DERIVATION_SERDE_PROTO_CLASS)
                            .build())
            .build();
    return ConfigUpgradeSpec.newBuilder()
        .setCommand(ConfigUpgradeCommand.CONFIG_UPGRADE_COMMAND_UPSERT)
        .setConfig(config)
        .build();
  }

  private ConfigUpgradeSpec buildConnectorMappingConfigSpec(
      ObjectType objectType, String accountIdentifier, String kindIdentifier) {
    String entityTypeId = objectType.hasEntityType() ? objectType.getEntityType().getId() : "";
    String connectorMappingUuid =
        UdpEventDerivationConstants.connectorMappingConfigUuid(accountIdentifier, kindIdentifier);
    String connectorMappingJson = buildConnectorMappingJson(entityTypeId);

    Config config =
        Config.newBuilder()
            .setId(ConfigReference.newBuilder()
                       .setTypeId(UdpEventDerivationConstants.CONNECTOR_MAPPING_CONFIG_TYPE_ID)
                       .setUuid(connectorMappingUuid)
                       .build())
            .setName(UdpEventDerivationConstants.connectorMappingConfigName(accountIdentifier, kindIdentifier))
            .setDescription("IDP catalog kind " + kindIdentifier + " connector mapping config")
            .setEnabled(true)
            .setPayload(ConfigPayload.newBuilder()
                            .setJsonValue(connectorMappingJson)
                            .setSerdeProtoClass(UdpEventDerivationConstants.CONNECTOR_MAPPING_SERDE_PROTO_CLASS)
                            .build())
            .build();
    return ConfigUpgradeSpec.newBuilder()
        .setCommand(ConfigUpgradeCommand.CONFIG_UPGRADE_COMMAND_UPSERT)
        .setConfig(config)
        .build();
  }

  private String buildConnectorMappingJson(String entityTypeId) {
    ObjectNode root = OBJECT_MAPPER.createObjectNode();
    ObjectNode connectorReference = root.putObject("connector_reference");
    connectorReference.put("connector_name", UdpEventDerivationConstants.CONNECTOR_NAME);
    ObjectNode configReference = connectorReference.putObject("config_reference");
    configReference.put("type_id", UdpEventDerivationConstants.CONNECTOR_CONFIG_TYPE_ID);
    configReference.put("uuid", UdpEventDerivationConstants.CONNECTOR_NAME);
    ObjectNode typeReference = root.putObject("type_reference");
    typeReference.put("id", entityTypeId);
    typeReference.put("object_kind", "OBJECT_KIND_ENTITY");
    root.put("table_fqn", UdpEventDerivationConstants.TABLE_FQN);
    root.putObject("additionalProperties");
    return root.toString();
  }
}

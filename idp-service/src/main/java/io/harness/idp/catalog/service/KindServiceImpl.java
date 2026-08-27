/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.beans.KindRequestDTO;
import io.harness.idp.catalog.beans.KindResponseDTO;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.catalog.events.KindCreateEvent;
import io.harness.idp.catalog.events.KindDeleteEvent;
import io.harness.idp.catalog.events.KindUpdateEvent;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.mapper.KindMapper;
import io.harness.idp.catalog.repositories.KindEntityRepository;
import io.harness.idp.dataplatform.CustomKindUdpPublisher;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.idp.layout.entities.LayoutEntity;
import io.harness.idp.layout.repositories.LayoutEntityRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.KindResponseBody;
import io.harness.spec.server.idp.v1.model.KindSchemaResponseBody;
import io.harness.springdata.TransactionHelper;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class KindServiceImpl implements KindService {
  YamlSchemaValidator yamlSchemaValidator;
  KindServiceHelper kindServiceHelper;
  KindEntityRepository kindEntityRepository;
  TransactionHelper transactionHelper;
  OutboxService outboxService;
  IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;
  CatalogService catalogService;
  LayoutEntityRepository layoutEntityRepository;
  CustomKindUdpPublisher customKindUdpPublisher;
  ExecutorService udpPublisherExecutor;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Inject
  public KindServiceImpl(YamlSchemaValidator yamlSchemaValidator, KindServiceHelper kindServiceHelper,
      KindEntityRepository kindEntityRepository, TransactionHelper transactionHelper, OutboxService outboxService,
      IdpServiceMiscRedisProducer idpServiceMiscRedisProducer, CatalogService catalogService,
      LayoutEntityRepository layoutEntityRepository, CustomKindUdpPublisher customKindUdpPublisher,
      @Named("UdpPublisher") ExecutorService udpPublisherExecutor) {
    this.yamlSchemaValidator = yamlSchemaValidator;
    this.kindServiceHelper = kindServiceHelper;
    this.kindEntityRepository = kindEntityRepository;
    this.transactionHelper = transactionHelper;
    this.outboxService = outboxService;
    this.idpServiceMiscRedisProducer = idpServiceMiscRedisProducer;
    this.catalogService = catalogService;
    this.layoutEntityRepository = layoutEntityRepository;
    this.customKindUdpPublisher = customKindUdpPublisher;
    this.udpPublisherExecutor = udpPublisherExecutor;
  }

  @Override
  public KindResponseBody save(String accountIdentifier, KindRequestDTO kindRequestDTO) {
    kindServiceHelper.validateIdentifier(kindRequestDTO.getIdentifier());
    kindRequestDTO.setIdentifier(kindRequestDTO.getIdentifier().toLowerCase());
    validateSchema(kindRequestDTO.getSchema());
    kindRequestDTO.setSchema(addKindEnumConstraint(kindRequestDTO));
    Optional<KindEntity> optionalKindEntity =
        kindEntityRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, kindRequestDTO.getIdentifier());
    if (optionalKindEntity.isPresent()) {
      log.error("Error in kind create. Kind already exist for accountIdentifier = {} | identifier = {}",
          accountIdentifier, kindRequestDTO.getIdentifier());
      throw new InvalidRequestException("Unable to create kind as it already exist");
    }
    KindEntity kindEntity = KindMapper.dtoToEntity(accountIdentifier, kindRequestDTO, true);
    ScopeInfo scopeInfo = kindServiceHelper.accountScopeInfo(accountIdentifier);
    LayoutEntity layoutEntity = kindServiceHelper.layoutForKind(kindEntity);
    transactionHelper.performTransaction(() -> {
      kindEntityRepository.save(kindEntity);
      layoutEntityRepository.save(layoutEntity);
      outboxService.save(new KindCreateEvent(scopeInfo, kindEntity));
      return null;
    });
    publishKindToUdp(accountIdentifier, kindEntity, "create");
    return KindMapper.entityToDto(kindEntity);
  }

  @Override
  public KindResponseBody update(String accountIdentifier, String identifier, KindRequestDTO kindRequestDTO) {
    kindRequestDTO.setIdentifier(identifier);
    validateSchema(kindRequestDTO.getSchema());
    kindRequestDTO.setSchema(addKindEnumConstraint(kindRequestDTO));
    Optional<KindEntity> optionalKindEntity =
        kindEntityRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    if (optionalKindEntity.isEmpty()) {
      log.error("Error in kind update. No kind found for accountIdentifier = {} | identifier = {}", accountIdentifier,
          identifier);
      throw new InvalidRequestException("Unable to update kind as it doesn't exist");
    }
    KindEntity existingKindEntity = optionalKindEntity.get();
    KindEntity kindEntity = KindMapper.dtoToEntity(accountIdentifier, kindRequestDTO, true);
    KindEntity updatedKindEntity = KindMapper.fromExistingEntity(existingKindEntity, kindEntity);
    ScopeInfo scopeInfo = kindServiceHelper.accountScopeInfo(accountIdentifier);
    transactionHelper.performTransaction(() -> {
      kindEntityRepository.save(updatedKindEntity);
      outboxService.save(new KindUpdateEvent(scopeInfo, existingKindEntity, updatedKindEntity));
      return null;
    });
    publishKindToUdp(accountIdentifier, updatedKindEntity, "update");
    return KindMapper.entityToDto(updatedKindEntity);
  }

  @Override
  public void delete(String accountIdentifier, String identifier) {
    Optional<KindEntity> optionalKindEntity =
        kindEntityRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    if (optionalKindEntity.isEmpty()) {
      log.error("Error in kind delete. No kind found for accountIdentifier = {} | identifier = {}", accountIdentifier,
          identifier);
      throw new InvalidRequestException("Unable to delete kind as it doesn't exist");
    }
    KindEntity kindEntity = optionalKindEntity.get();
    ScopeInfo scopeInfo = kindServiceHelper.accountScopeInfo(accountIdentifier);
    List<LayoutEntity> layoutEntities =
        layoutEntityRepository.findByAccountIdentifierAndEntityKind(accountIdentifier, kindEntity.getIdentifier());
    transactionHelper.performTransaction(() -> {
      kindEntityRepository.delete(kindEntity);
      layoutEntityRepository.deleteAll(layoutEntities);
      outboxService.save(new KindDeleteEvent(scopeInfo, kindEntity));
      idpServiceMiscRedisProducer.publishIDPKindProcessorEventToRedis(accountIdentifier, identifier, DELETE_ACTION);
      return null;
    });
  }

  @Override
  public void processKindDelete(String accountIdentifier, String identifier) {
    GetEntitiesDTO entities = catalogService.getEntities(accountIdentifier, 0, -1, null, null, false, "account.*", null,
        null, null, identifier, null, null, null, null, null, false);
    entities.getEntityResponses().forEach(entity
        -> catalogService.deleteEntity(
            accountIdentifier, entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getEntityRef(), false));
  }

  @Override
  public KindResponseBody get(String accountIdentifier, String identifier, Boolean custom) {
    Optional<KindEntity> optionalKindEntity;
    if (custom) {
      optionalKindEntity = kindEntityRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    } else {
      optionalKindEntity = kindEntityRepository.findByAccountIdentifierAndIdentifier(GLOBAL_ACCOUNT_ID, identifier);
    }
    if (optionalKindEntity.isEmpty()) {
      log.error("Error in kind get. No kind found for accountIdentifier = {} | identifier = {}", accountIdentifier,
          identifier);
      throw new InvalidRequestException("Unable to get kind as it doesn't exist");
    }
    KindEntity kindEntity = optionalKindEntity.get();
    return KindMapper.entityToDto(kindEntity);
  }

  @Override
  public KindSchemaResponseBody getSchema() {
    try {
      KindSchemaResponseBody kindSchemaResponseBody = new KindSchemaResponseBody();
      kindSchemaResponseBody.setSchema(kindServiceHelper.baseSchema());
      return kindSchemaResponseBody;
    } catch (Exception ex) {
      log.error("Error while fetching base schema. Error = {}", ex.getMessage(), ex);
      throw new UnexpectedException();
    }
  }

  @Override
  public KindResponseDTO get(
      String accountIdentifier, int pageIndex, int pageLimit, String sort, String searchTerm, Boolean custom) {
    Page<KindEntity> kindEntities =
        kindEntityRepository.getKinds(accountIdentifier, pageIndex, pageLimit, sort, searchTerm, custom);
    return KindResponseDTO.builder()
        .pageNumber(kindEntities.getNumber())
        .totalElements(kindEntities.getTotalElements())
        .kindResponseBodyList(KindMapper.entityToDto(kindEntities.getContent()))
        .build();
  }

  @Override
  public void validateSchema(String schema) {
    try {
      JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
      JsonSchema metaSchema = factory.getSchema(kindServiceHelper.draftSchema());

      JsonNode schemaNode = OBJECT_MAPPER.readTree(schema);

      if (schemaNode.has("$schema")) {
        ((ObjectNode) schemaNode).remove("$schema");
      }

      Set<ValidationMessage> errors = metaSchema.validate(schemaNode);
      if (!isEmpty(errors)) {
        Set<String> errorsMessages = errors.stream().map(ValidationMessage::getMessage).collect(Collectors.toSet());
        throw new InvalidRequestException(String.join(",", errorsMessages));
      }

      String baseSchema = kindServiceHelper.baseSchema();
      JsonNode baseSchemaNode = OBJECT_MAPPER.readTree(baseSchema);

      JsonNode userKindNode = schemaNode.at("/properties/kind");
      if (!userKindNode.isMissingNode() && userKindNode.isObject()) {
        ((ObjectNode) userKindNode).remove("enum");
      }

      List<String> protectedPaths = List.of(
          "/type", "/additionalProperties", "/properties/apiVersion", "/properties/kind", "/properties/identifier");
      for (String protectedPath : protectedPaths) {
        JsonNode baseNode = baseSchemaNode.at(protectedPath);
        JsonNode userNode = schemaNode.at(protectedPath);
        if (!baseNode.equals(userNode)) {
          throw new InvalidRequestException("Modification not allowed for schema path: " + protectedPath);
        }
      }

      JsonNode baseRequired = baseSchemaNode.at("/required");
      JsonNode userRequired = schemaNode.at("/required");
      if (baseRequired.isArray()) {
        if (userRequired.isMissingNode() || !userRequired.isArray()) {
          throw new InvalidRequestException("Modification not allowed for schema path: /required");
        }
        Set<String> userRequiredSet = new HashSet<>();
        userRequired.forEach(node -> userRequiredSet.add(node.asText()));
        for (JsonNode entry : baseRequired) {
          if (!userRequiredSet.contains(entry.asText())) {
            throw new InvalidRequestException(
                "Removing or editing existing entries not allowed in /required. Missing: " + entry.asText());
          }
        }
      }

      JsonNode properties = schemaNode.path("properties");
      Iterator<String> fieldNames = properties.fieldNames();

      Set<String> allowed = Set.of("apiVersion", "kind", "identifier", "name", "type", "owner", "spec", "metadata",
          "orgIdentifier", "projectIdentifier");
      while (fieldNames.hasNext()) {
        String field = fieldNames.next();
        if (!allowed.contains(field)) {
          throw new InvalidRequestException("Property not allowed: " + field);
        }
      }
    } catch (Exception ex) {
      if (ex instanceof InvalidRequestException) {
        log.error("Error while validating user provided schema against base schema. Error = {}", ex.getMessage(), ex);
        throw new InvalidRequestException(ex.getMessage());
      }
      log.error("Error while validating user provided schema against base schema. Error = {}", ex.getMessage(), ex);
      throw new UnexpectedException();
    }
  }

  private String addKindEnumConstraint(KindRequestDTO kindRequestDTO) {
    try {
      JsonNode schemaNode = OBJECT_MAPPER.readTree(kindRequestDTO.getSchema());
      JsonNode kindNode = schemaNode.at("/properties/kind");
      if (!kindNode.isMissingNode() && kindNode.isObject()) {
        ObjectNode kindObjectNode = (ObjectNode) kindNode;
        kindObjectNode.set("enum", OBJECT_MAPPER.createArrayNode().add(kindRequestDTO.getIdentifier()));
      }
      return OBJECT_MAPPER.writeValueAsString(schemaNode);
    } catch (Exception ex) {
      log.error("Error while adding kind enum constraint to schema. Error = {}", ex.getMessage(), ex);
      throw new UnexpectedException("Failed to add kind enum constraint to schema");
    }
  }

  private void publishKindToUdp(String accountIdentifier, KindEntity kindEntity, String action) {
    CompletableFuture
        .runAsync(
            () -> customKindUdpPublisher.publishCreateOrUpdate(accountIdentifier, kindEntity), udpPublisherExecutor)
        .exceptionally(ex -> {
          log.warn(
              "Failed to publish UDP type ingestion for action {} and kind {}", action, kindEntity.getIdentifier(), ex);
          return null;
        });
  }
}

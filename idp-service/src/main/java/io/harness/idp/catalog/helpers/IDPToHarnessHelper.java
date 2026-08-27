/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.idp.catalog.utils.CatalogUtils.getHarnessCatalogKind;
import static io.harness.idp.catalog.utils.CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef;
import static io.harness.idp.catalog.utils.CatalogUtils.replaceEmailAddressInCatalogRef;
import static io.harness.idp.catalog.utils.Constants.API_COMPONENT_RESOURCE_TEMPLATE_TYPE;
import static io.harness.idp.catalog.utils.Constants.API_CONSUMED_BY;
import static io.harness.idp.catalog.utils.Constants.API_KIND;
import static io.harness.idp.catalog.utils.Constants.API_PROVIDED_BY;
import static io.harness.idp.catalog.utils.Constants.API_VERSION;
import static io.harness.idp.catalog.utils.Constants.BACKSTAGE_API_VERSION;
import static io.harness.idp.catalog.utils.Constants.BACKSTAGE_TEMPLATE_API_VERSION;
import static io.harness.idp.catalog.utils.Constants.CHILD_OF;
import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.idp.catalog.utils.Constants.CONSUMES_API;
import static io.harness.idp.catalog.utils.Constants.CORE_KINDS;
import static io.harness.idp.catalog.utils.Constants.DEPENDENCY_OF;
import static io.harness.idp.catalog.utils.Constants.DEPENDS_ON;
import static io.harness.idp.catalog.utils.Constants.DESCRIPTION;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.HARNESS_API_VERSION;
import static io.harness.idp.catalog.utils.Constants.HAS_LEADER;
import static io.harness.idp.catalog.utils.Constants.HAS_MEMBER;
import static io.harness.idp.catalog.utils.Constants.HAS_PART;
import static io.harness.idp.catalog.utils.Constants.KIND;
import static io.harness.idp.catalog.utils.Constants.LEADER_OF;
import static io.harness.idp.catalog.utils.Constants.LEVEL;
import static io.harness.idp.catalog.utils.Constants.MEMBER_OF;
import static io.harness.idp.catalog.utils.Constants.MESSAGE;
import static io.harness.idp.catalog.utils.Constants.METADATA;
import static io.harness.idp.catalog.utils.Constants.METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION;
import static io.harness.idp.catalog.utils.Constants.METADATA_TAGS;
import static io.harness.idp.catalog.utils.Constants.NAME;
import static io.harness.idp.catalog.utils.Constants.NAMESPACE;
import static io.harness.idp.catalog.utils.Constants.OWNED_BY;
import static io.harness.idp.catalog.utils.Constants.OWNER;
import static io.harness.idp.catalog.utils.Constants.OWNER_OF;
import static io.harness.idp.catalog.utils.Constants.PARENT_OF;
import static io.harness.idp.catalog.utils.Constants.PART_OF;
import static io.harness.idp.catalog.utils.Constants.PROVIDES_API;
import static io.harness.idp.catalog.utils.Constants.RELATION_REFS;
import static io.harness.idp.catalog.utils.Constants.RESOURCE_KIND;
import static io.harness.idp.catalog.utils.Constants.SOURCE_LOCATION_ANNOTATION;
import static io.harness.idp.catalog.utils.Constants.SPEC;
import static io.harness.idp.catalog.utils.Constants.SYSTEM_SPEC_RELATION_REF;
import static io.harness.idp.catalog.utils.Constants.TAGS;
import static io.harness.idp.catalog.utils.Constants.TARGET_REF;
import static io.harness.idp.catalog.utils.Constants.TEMPLATE;
import static io.harness.idp.catalog.utils.Constants.TITLE;
import static io.harness.idp.catalog.utils.Constants.TYPE;
import static io.harness.idp.catalog.utils.Constants.USER_GROUP_TYPE;
import static io.harness.idp.catalog.utils.Constants.USER_KIND;
import static io.harness.idp.catalog.utils.Constants.USER_TYPE;
import static io.harness.idp.catalog.utils.Constants.UUID;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.idp.common.CommonUtils.from;
import static io.harness.idp.common.JacksonUtils.convert;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.clients.BackstageResourceClient;
import io.harness.data.structure.UUIDGenerator;
import io.harness.eventsframework.schemas.usermembership.UserMembershipDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.mapper.CatalogMapper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.remote.dto.UserFilter;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.springdata.TransactionHelper;
import io.harness.user.remote.UserClient;
import io.harness.usergroups.UserGroupClient;
import io.harness.userng.remote.UserNGClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class IDPToHarnessHelper {
  NamespaceService namespaceService;
  BackstageResourceClient backstageResourceClient;
  UserNGClient userNGClient;
  UserGroupClient userGroupClient;
  ScopeInfoClient scopeInfoClient;
  CatalogEntityRepository catalogEntityRepository;
  TransactionHelper transactionHelper;
  UserClient userClient;
  CatalogServiceHelper catalogServiceHelper;
  HarnessToIDPHelper harnessToIDPHelper;
  IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;
  IdpCommonService idpCommonService;

  @Inject
  public IDPToHarnessHelper(NamespaceService namespaceService, BackstageResourceClient backstageResourceClient,
      @Named("PRIVILEGED") UserNGClient userNGClient, @Named("PRIVILEGED") UserGroupClient userGroupClient,
      ScopeInfoClient scopeInfoClient, CatalogEntityRepository catalogEntityRepository,
      TransactionHelper transactionHelper, UserClient userClient, CatalogServiceHelper catalogServiceHelper,
      HarnessToIDPHelper harnessToIDPHelper, IdpServiceMiscRedisProducer idpServiceMiscRedisProducer,
      IdpCommonService idpCommonService) {
    this.namespaceService = namespaceService;
    this.backstageResourceClient = backstageResourceClient;
    this.userNGClient = userNGClient;
    this.userGroupClient = userGroupClient;
    this.scopeInfoClient = scopeInfoClient;
    this.catalogEntityRepository = catalogEntityRepository;
    this.transactionHelper = transactionHelper;
    this.userClient = userClient;
    this.catalogServiceHelper = catalogServiceHelper;
    this.harnessToIDPHelper = harnessToIDPHelper;
    this.idpServiceMiscRedisProducer = idpServiceMiscRedisProducer;
    this.idpCommonService = idpCommonService;
  }

  public void validateAndMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(String accountIdentifier) {
    Optional<NamespaceEntity> optionalNamespaceEntity =
        namespaceService.getEntityForAccountIdentifier(accountIdentifier);
    if (optionalNamespaceEntity.isEmpty()) {
      return;
    }
    NamespaceEntity namespaceEntity = optionalNamespaceEntity.get();
    boolean migrateCatalogEntitiesFromBackstageToHarnessCompleted = Objects.nonNull(namespaceEntity.getMetadata())
        && namespaceEntity.getMetadata().isMigrateCatalogEntitiesFromBackstageToHarnessCompleted();
    if (migrateCatalogEntitiesFromBackstageToHarnessCompleted) {
      return;
    }
    migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(namespaceEntity);
  }

  public void migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(String accountIdentifier) {
    Optional<NamespaceEntity> optionalNamespaceEntity =
        namespaceService.getEntityForAccountIdentifier(accountIdentifier);
    if (optionalNamespaceEntity.isEmpty()) {
      return;
    }
    NamespaceEntity namespaceEntity = optionalNamespaceEntity.get();
    migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(namespaceEntity);
  }

  public void migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(NamespaceEntity namespaceEntity) {
    String accountIdentifier = namespaceEntity.getAccountIdentifier();
    log.info("Starting the migration for IDP to harness entities for account {}", accountIdentifier);
    String url = String.format(
        "%s/idp/api/catalog/entities?filter=kind=api&filter=kind=component&filter=kind=resource&filter=kind=template",
        accountIdentifier);
    Object response = getGeneralResponse(backstageResourceClient.getCatalogEntities(url));
    List<Map<String, Object>> filteredBackstageCatalogEntities = convert(response, Map.class);
    handleForUniqueness(filteredBackstageCatalogEntities);

    List<UserMetadataDTO> userMetadataDTOS = getUsers(accountIdentifier, UserFilter.builder().build());
    List<UserGroupDTO> userGroupDTOS = getUserGroups(accountIdentifier);

    Map<String, String> uuidAndEmailMapping =
        userMetadataDTOS.stream().collect(Collectors.toMap(UserMetadataDTO::getUuid, UserMetadataDTO::getEmail));
    Map<String, String> usernameAndEmailMapping = userMetadataDTOS.stream().collect(Collectors.toMap(userMetadataDTO
        -> userMetadataDTO.getEmail().split("@")[0],
        UserMetadataDTO::getEmail, (existing, replacement) -> existing));

    ScopeInfo scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, null, null));

    List<CatalogEntity> alreadySavedCatalogEntities =
        catalogEntityRepository.findAllByParentUniqueId(scopeInfo.getUniqueId());
    Map<String, CatalogEntity> catalogEntityMap = alreadySavedCatalogEntities.stream().collect(
        Collectors.toMap(this::getUniqueIdentifiersCombination, entity -> entity));

    List<CatalogEntity> catalogEntities = new ArrayList<>();

    for (UserMetadataDTO userMetadataDTO : userMetadataDTOS) {
      updateInlineEntityIfAlreadyPresent(catalogEntityMap, catalogEntities,
          getInlineEntityForUsers(accountIdentifier, userMetadataDTO, userGroupDTOS, scopeInfo, true));
    }

    for (UserGroupDTO userGroupDTO : userGroupDTOS) {
      updateInlineEntityIfAlreadyPresent(catalogEntityMap, catalogEntities,
          getInlineEntityForUserGroups(
              accountIdentifier, userGroupDTO, userGroupDTOS, scopeInfo, userMetadataDTOS, true));

      List<String> emailOfUsersInUserGroup = new ArrayList<>();
      for (String uuid : userGroupDTO.getUsers()) {
        if (uuidAndEmailMapping.containsKey(uuid)) {
          emailOfUsersInUserGroup.add(uuidAndEmailMapping.get(uuid));
        }
      }

      catalogEntities.stream()
          .filter(catalogEntity -> emailOfUsersInUserGroup.contains(catalogEntity.getIdentifier()))
          .filter(catalogEntity -> catalogEntity.getKind().equals(USER_KIND))
          .forEach(catalogEntity -> updateUserCatalogEntityRelations(catalogEntity, userGroupDTO.getIdentifier()));
    }

    for (Map<String, Object> filteredBackstageCatalogEntity : filteredBackstageCatalogEntities) {
      updateInlineEntityIfAlreadyPresent(catalogEntityMap, catalogEntities,
          getInlineEntityForApiOrComponentOrResourceOrTemplate(
              accountIdentifier, filteredBackstageCatalogEntity, scopeInfo, true, usernameAndEmailMapping));
    }

    catalogEntities.forEach(
        catalogEntity -> catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity)));

    transactionHelper.performTransaction(() -> {
      catalogEntityRepository.saveAll(catalogEntities);
      updateMigrateCatalogEntitiesFromBackstageToHarnessCompletedToTrue(namespaceEntity);
      return null;
    });
    log.info("Completed the migration for IDP to harness entities for account {}", accountIdentifier);
  }

  public void seedUsersAndUserGroups(String accountIdentifier) {
    List<UserMetadataDTO> userMetadataDTOS = getUsers(accountIdentifier, UserFilter.builder().build());
    List<UserGroupDTO> userGroupDTOS = getUserGroups(accountIdentifier);

    Map<String, String> uuidAndEmailMapping =
        userMetadataDTOS.stream().collect(Collectors.toMap(UserMetadataDTO::getUuid, UserMetadataDTO::getEmail));

    ScopeInfo scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, null, null));

    List<CatalogEntity> alreadySavedCatalogEntities =
        catalogEntityRepository.findAllByParentUniqueId(scopeInfo.getUniqueId());
    Map<String, CatalogEntity> catalogEntityMap = alreadySavedCatalogEntities.stream().collect(
        Collectors.toMap(this::getUniqueIdentifiersCombination, entity -> entity));

    List<CatalogEntity> catalogEntities = new ArrayList<>();

    for (UserMetadataDTO userMetadataDTO : userMetadataDTOS) {
      updateInlineEntityIfAlreadyPresent(catalogEntityMap, catalogEntities,
          getInlineEntityForUsers(accountIdentifier, userMetadataDTO, userGroupDTOS, scopeInfo, true));
    }

    for (UserGroupDTO userGroupDTO : userGroupDTOS) {
      updateInlineEntityIfAlreadyPresent(catalogEntityMap, catalogEntities,
          getInlineEntityForUserGroups(
              accountIdentifier, userGroupDTO, userGroupDTOS, scopeInfo, userMetadataDTOS, true));

      List<String> emailOfUsersInUserGroup = new ArrayList<>();
      for (String uuid : userGroupDTO.getUsers()) {
        if (uuidAndEmailMapping.containsKey(uuid)) {
          emailOfUsersInUserGroup.add(uuidAndEmailMapping.get(uuid));
        }
      }

      catalogEntities.stream()
          .filter(catalogEntity -> emailOfUsersInUserGroup.contains(catalogEntity.getIdentifier()))
          .filter(catalogEntity -> catalogEntity.getKind().equals(USER_KIND))
          .forEach(catalogEntity -> updateUserCatalogEntityRelations(catalogEntity, userGroupDTO.getIdentifier()));
    }

    catalogEntities.forEach(
        catalogEntity -> catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity)));

    transactionHelper.performTransaction(() -> {
      catalogEntityRepository.saveAll(catalogEntities);
      return null;
    });
  }

  private void updateInlineEntityIfAlreadyPresent(Map<String, CatalogEntity> catalogEntityMap,
      List<CatalogEntity> catalogEntities, CatalogEntity constructedEntity) {
    String uniqueIdentifiersCombination = getUniqueIdentifiersCombination(constructedEntity);
    if (catalogEntityMap.containsKey(uniqueIdentifiersCombination)) {
      CatalogEntity savedCatalogEntity = catalogEntityMap.get(uniqueIdentifiersCombination);
      if (savedCatalogEntity instanceof GitReferencedCatalogEntity) {
        return;
      }
      constructedEntity.setUniqueId(savedCatalogEntity.getUniqueId());
      constructedEntity.setId(savedCatalogEntity.getId());
    }
    catalogEntities.add(constructedEntity);
  }

  public String convertBackstageToHarness(String accountIdentifier, String backstageYaml) {
    return convertBackstageToHarness(accountIdentifier, null, null, backstageYaml);
  }

  public String convertBackstageToHarness(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String backstageYaml) {
    try {
      Map<String, Object> backstageYamlMap = YamlUtils.loadYamlStringAsMap(backstageYaml);

      String kind = from(backstageYamlMap, KIND, String.class);
      if (isEmpty(kind)) {
        throw new InvalidRequestException("Kind cannot be null or empty");
      }

      String apiVersion = from(backstageYamlMap, API_VERSION, String.class);
      kind = kind.toLowerCase();
      if (isEmpty(apiVersion)) {
        throw new InvalidRequestException("apiVersion cannot be null or empty");
      }
      if (kind.equalsIgnoreCase(TEMPLATE) && !apiVersion.equals(BACKSTAGE_TEMPLATE_API_VERSION)) {
        throw new InvalidRequestException(
            "apiVersion should be " + BACKSTAGE_TEMPLATE_API_VERSION + " for backstage to harness entity conversion");
      } else if (!kind.equalsIgnoreCase(TEMPLATE) && !apiVersion.equals(BACKSTAGE_API_VERSION)) {
        throw new InvalidRequestException(
            "apiVersion should be " + BACKSTAGE_API_VERSION + " for backstage to harness entity conversion");
      }
      kind = getHarnessCatalogKind(kind);
      catalogServiceHelper.validateKindForCreateUpdateDelete(kind);

      String identifier = from(backstageYamlMap, "metadata.name", String.class);
      if (isEmpty(identifier)) {
        throw new InvalidRequestException("metadata.name cannot be null or empty");
      }
      identifier = identifier.replace("-", "_").replaceAll("[^a-zA-Z0-9_]", "");

      String name = from(backstageYamlMap, "metadata.title", String.class);
      if (isEmpty(name)) {
        name = identifier;
      } else {
        name = name.replace("-", "_").replaceAll("[^a-zA-Z0-9_]", "");
      }

      String sourceLocation = from(backstageYamlMap, METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION, String.class);

      String owner = from(backstageYamlMap, "spec.owner", String.class);
      if (CORE_KINDS.contains(kind) && isEmpty(owner)) {
        throw new InvalidRequestException("owner cannot be null or empty for kind as api / component / resource");
      }

      if (!isEmpty(owner)) {
        owner = owner.replace("-", "_");
      }

      Map<String, Object> spec = !isEmpty(from(backstageYamlMap, SPEC, Map.class))
          ? new HashMap<>(from(backstageYamlMap, SPEC, Map.class))
          : new HashMap<>();
      spec.remove("type");
      spec.remove("owner");
      spec.remove("system");

      processSpecRelations(spec);

      Map<String, Object> metadata = !isEmpty(from(backstageYamlMap, METADATA, Map.class))
          ? new HashMap<>(from(backstageYamlMap, METADATA, Map.class))
          : new HashMap<>();
      metadata.remove("identifier");
      metadata.remove("absoluteIdentifier");
      metadata.remove("uid");
      metadata.remove("etag");
      metadata.remove("name");
      metadata.remove("title");
      metadata.remove("description");
      metadata.remove("tags");
      metadata.remove("namespace");
      metadata.remove("backstage.io/managed-by-location");
      metadata.remove("backstage.io/managed-by-origin-location");
      metadata.remove("backstage.io/view-url");
      metadata.remove("backstage.io/edit-url");
      metadata.remove("backstage.io/source-location");
      metadata.remove("backstage.io/source-template");
      metadata.remove("backstage.io/orphan");

      InlineCatalogEntity inlineCatalogEntity =
          (InlineCatalogEntity) InlineCatalogEntity.builder()
              .accountIdentifier(accountIdentifier)
              .orgIdentifier(isEmpty(orgIdentifier) ? null : orgIdentifier)
              .projectIdentifier(isEmpty(projectIdentifier) ? null : projectIdentifier)
              .identifier(identifier)
              .referenceType(ReferenceType.INLINE)
              .apiVersion(HARNESS_API_VERSION)
              .kind(kind)
              .type(from(backstageYamlMap, "spec.type", String.class))
              .name(name)
              .description(from(backstageYamlMap, "metadata.description", String.class))
              .owner(parseBackstageEntityReferenceToCatalogRelationRef(owner, null))
              .tags(from(backstageYamlMap, METADATA_TAGS, List.class))
              .sourceLocation(sourceLocation)
              .spec(spec)
              .metadata(metadata)
              .build();

      catalogServiceHelper.validateSystemScope(inlineCatalogEntity);
      inlineCatalogEntity.setYaml(CatalogMapper.presentationYaml(inlineCatalogEntity));

      return inlineCatalogEntity.getYaml();
    } catch (Exception ex) {
      log.error("Error in convertBackstageToHarness. Exception = {}", ex.getMessage(), ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }
  private void processSpecRelations(Map<String, Object> spec) {
    for (String relationRef : RELATION_REFS) {
      if (spec.containsKey(relationRef)) {
        Object relationValue = spec.get(relationRef);
        if (relationValue instanceof List) {
          List<?> relationList = (List<?>) relationValue;
          List<String> processedRelations = new ArrayList<>();

          for (Object relation : relationList) {
            if (relation instanceof String) {
              String relationStr = (String) relation;
              processedRelations.add(relationStr.replace("-", "_"));
            } else {
              processedRelations.add(String.valueOf(relation));
            }
          }

          spec.put(relationRef, processedRelations);
        } else if (relationValue instanceof String) {
          String relationStr = (String) relationValue;
          spec.put(relationRef, relationStr.replace("-", "_"));
        }
      }
    }
  }

  private void handleForUniqueness(List<Map<String, Object>> filteredBackstageCatalogEntities) {
    Map<String, Long> nameKindCounts = filteredBackstageCatalogEntities.stream()
                                           .map(entity -> {
                                             Map<String, Object> metadata =
                                                 (Map<String, Object>) entity.get("metadata");
                                             return entity.get("kind") + ":" + metadata.get("name");
                                           })
                                           .collect(Collectors.groupingBy(key -> key, Collectors.counting()));

    filteredBackstageCatalogEntities.forEach(entity -> {
      Map<String, Object> metadata = (Map<String, Object>) entity.get("metadata");
      String key = entity.get("kind") + ":" + metadata.get("name");
      if (nameKindCounts.getOrDefault(key, 0L) > 1) {
        String updatedName = metadata.get("namespace") + "_" + metadata.get("name");
        metadata.put("name", updatedName);
      }
    });
  }

  public List<UserMetadataDTO> getUsers(String accountIdentifier, UserFilter userFilter) {
    List<UserMetadataDTO> userMetadataDTOS = new ArrayList<>();
    io.harness.ng.beans.PageResponse<UserMetadataDTO> userMetadataDTOPageResponse;
    int page = 0;
    do {
      userMetadataDTOPageResponse =
          NGRestUtils.getResponse(userNGClient.userBatch(accountIdentifier, null, null, page, 100, userFilter));
      if (userMetadataDTOPageResponse != null && isNotEmpty(userMetadataDTOPageResponse.getContent())) {
        userMetadataDTOS.addAll(userMetadataDTOPageResponse.getContent().stream().toList());
      }
      page++;
    } while (userMetadataDTOPageResponse != null && isNotEmpty(userMetadataDTOPageResponse.getContent()));
    return userMetadataDTOS;
  }

  public InlineCatalogEntity getInlineEntityForApiOrComponentOrResourceOrTemplate(String accountIdentifier,
      Map<String, Object> filteredBackstageCatalogEntity, ScopeInfo scopeInfo, Boolean creatingEntity,
      Map<String, String> usernameAndEmailMapping) {
    InlineCatalogEntity inlineCatalogEntity = buildInlineCatalogEntityForMigration(accountIdentifier,
        API_COMPONENT_RESOURCE_TEMPLATE_TYPE, filteredBackstageCatalogEntity, usernameAndEmailMapping);
    if (creatingEntity) {
      inlineCatalogEntity.setUniqueId(UUIDGenerator.generateUuid());
    }
    inlineCatalogEntity.setParentUniqueId(scopeInfo.getUniqueId());
    return inlineCatalogEntity;
  }

  private InlineCatalogEntity getInlineEntityForUsers(String accountIdentifier, UserMetadataDTO userMetadataDTO,
      List<UserGroupDTO> userGroupDTOS, ScopeInfo scopeInfo, Boolean creatingEntity) {
    Map<String, List<String>> userUserGroups =
        userGroupDTOS.stream()
            .flatMap(group -> group.getUsers().stream().map(user -> Map.entry(user, "group:" + group.getIdentifier())))
            .collect(
                Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    InlineCatalogEntity inlineCatalogEntity =
        buildInlineCatalogEntityForMigration(accountIdentifier, USER_TYPE, userMetadataDTO, null);
    /*
    Not setting the relations here as user create event is coming before user group update event which is setting
    relations in correctly, so we will update user's relation as downstream flow of user group update.
    * */
    //    inlineCatalogEntity.setRelations(Map.of(MEMBER_OF, userUserGroups.get(userMetadataDTO.getUuid())));
    inlineCatalogEntity.setYaml(CatalogMapper.presentationYaml(inlineCatalogEntity));
    if (creatingEntity) {
      inlineCatalogEntity.setUniqueId(UUIDGenerator.generateUuid());
    }
    inlineCatalogEntity.setParentUniqueId(scopeInfo.getUniqueId());
    return inlineCatalogEntity;
  }

  private InlineCatalogEntity getInlineEntityForUserGroups(String accountIdentifier, UserGroupDTO userGroupDTO,
      List<UserGroupDTO> userGroupDTOS, ScopeInfo scopeInfo, List<UserMetadataDTO> userMetadataDTOS,
      Boolean creatingEntity) {
    Map<String, List<String>> userGroupUsers =
        userGroupDTOS.stream().collect(Collectors.toMap(UserGroupDTO::getIdentifier, UserGroupDTO::getUsers));

    InlineCatalogEntity inlineCatalogEntity =
        buildInlineCatalogEntityForMigration(accountIdentifier, USER_GROUP_TYPE, userGroupDTO, null);
    List<String> userGroupMembersWithUUID = userGroupUsers.get(userGroupDTO.getIdentifier());
    Set<String> userGroupMembers = userGroupMembersWithUUID.stream()
                                       .map(uuid
                                           -> userMetadataDTOS.stream()
                                                  .filter(userInfo -> userInfo.getUuid().equals(uuid))
                                                  .map(userInfo -> "user:" + userInfo.getEmail())
                                                  .findFirst()
                                                  .orElse(null))
                                       .filter(Objects::nonNull)
                                       .collect(Collectors.toSet());
    inlineCatalogEntity.setRelations(Map.of(HAS_MEMBER, userGroupMembers));
    inlineCatalogEntity.setYaml(CatalogMapper.presentationYaml(inlineCatalogEntity));
    if (creatingEntity) {
      inlineCatalogEntity.setUniqueId(UUIDGenerator.generateUuid());
    }
    inlineCatalogEntity.setParentUniqueId(scopeInfo.getUniqueId());
    return inlineCatalogEntity;
  }

  public CatalogEntity updateUser(
      String accountIdentifier, UserMembershipDTO userMembershipDTO, Boolean creatingEntity) {
    UserMetadataDTO userMetadataDTO = getUserMetaDataFromUserMembershipDTO(userMembershipDTO);
    ScopeInfo scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, null, null));
    // Currently we are fetching all the userGroups for mapping will see if any other api we can use it to optimise it
    InlineCatalogEntity inlineCatalogEntity = getInlineEntityForUsers(
        accountIdentifier, userMetadataDTO, getUserGroups(accountIdentifier), scopeInfo, creatingEntity);
    Optional<CatalogEntity> savedInlineCatalogEntity = catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
        scopeInfo.getAccountIdentifier(), USER_KIND, userMetadataDTO.getEmail());
    if (savedInlineCatalogEntity.isPresent()) {
      inlineCatalogEntity.setId(savedInlineCatalogEntity.get().getId());
      inlineCatalogEntity.setUniqueId(savedInlineCatalogEntity.get().getUniqueId());
    }
    inlineCatalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(inlineCatalogEntity));
    return catalogEntityRepository.save(inlineCatalogEntity);
  }

  public void updateUserGroup(String accountIdentifier, String userGroupIdentifier, String action) {
    Boolean creatingEntity = action.equals(CREATE_ACTION) ? true : false;
    ScopeInfo scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, null, null));
    Optional<CatalogEntity> savedInlineCatalogEntity = catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
        scopeInfo.getAccountIdentifier(), GROUP_KIND, userGroupIdentifier);
    if (savedInlineCatalogEntity.isPresent()
        && catalogServiceHelper.checkIfCustomUserGroup(savedInlineCatalogEntity.get())) {
      log.warn("Skipping event processing since platform user group with the same identifier is already existing has "
          + "custom user group");
      return;
    }
    UserGroupDTO userGroupDTO =
        NGRestUtils.getResponse(userGroupClient.getUserGroup(userGroupIdentifier, accountIdentifier, null, null));
    // Currently we are fetching all the users for mapping will see if any other api we can use it to optimise it
    List<UserMetadataDTO> userMetadataDTOS = getUsers(accountIdentifier, UserFilter.builder().build());
    InlineCatalogEntity inlineCatalogEntity = getInlineEntityForUserGroups(
        accountIdentifier, userGroupDTO, List.of(userGroupDTO), scopeInfo, userMetadataDTOS, creatingEntity);
    if (savedInlineCatalogEntity.isPresent()) {
      inlineCatalogEntity.setId(savedInlineCatalogEntity.get().getId());
      inlineCatalogEntity.setUniqueId(savedInlineCatalogEntity.get().getUniqueId());
    }
    List<CatalogEntity> userCatalogEntity = updateRelationsForUsers(
        accountIdentifier, userGroupDTO.getUsers(), userGroupIdentifier, userMetadataDTOS, savedInlineCatalogEntity);
    userCatalogEntity.forEach(
        catalogEntity -> catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity)));
    inlineCatalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(inlineCatalogEntity));
    Boolean isIDPV2Enabled = idpCommonService.idpV2Enabled(accountIdentifier);
    transactionHelper.performTransaction(() -> {
      // Updating relations for users
      catalogEntityRepository.saveAll(userCatalogEntity);
      catalogEntityRepository.save(inlineCatalogEntity);

      if (isIDPV2Enabled) {
        harnessToIDPHelper.harnessToIdpSync(List.of(inlineCatalogEntity), accountIdentifier, action);
      }
      return null;
    });
    if (isIDPV2Enabled) {
      sendCatalogEventsToRedis(userCatalogEntity, UPDATE_ACTION);
      sendCatalogEventsToRedis(Collections.singletonList(inlineCatalogEntity), action);
    }
  }

  public List<CatalogEntity> removeRelationsForUsers(String accountIdentifier, String userGroupId) {
    String userGroupIdForInlineCatalogEntity = "group:" + userGroupId;
    ScopeInfo scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, null, null));
    List<CatalogEntity> userCatalogEntities =
        catalogEntityRepository.findAllByParentUniqueIdAndKind(scopeInfo.getUniqueId(), USER_KIND);
    for (CatalogEntity userCatalogEntity : userCatalogEntities) {
      if (!isEmpty(userCatalogEntity.getRelations())) {
        Map<String, Set<String>> relations = userCatalogEntity.getRelations();
        if (relations.containsKey(MEMBER_OF)) {
          Set<String> userPartOfUserGroups = relations.get(MEMBER_OF);
          if (userPartOfUserGroups.contains(userGroupIdForInlineCatalogEntity)) {
            userPartOfUserGroups.remove(userGroupIdForInlineCatalogEntity);
          }
          relations.put(MEMBER_OF, userPartOfUserGroups);
        }
        userCatalogEntity.setRelations(relations);
        userCatalogEntity.setYaml(CatalogMapper.presentationYaml(userCatalogEntity));
      }
    }
    return userCatalogEntities;
  }

  public void sendCatalogEventsToRedis(List<CatalogEntity> entities, String action) {
    for (CatalogEntity entity : entities) {
      idpServiceMiscRedisProducer.publishIDPCatalogEntitiesToRedisV2(
          entity.getAccountIdentifier(), entity.getParentUniqueId(), CatalogUtils.entityRef(entity), action);
    }
  }

  private List<CatalogEntity> updateRelationsForUsers(String accountIdentifier, List<String> userGroupUserIds,
      String userGroupId, List<UserMetadataDTO> accountUsers, Optional<CatalogEntity> userGroupEntity) {
    ScopeInfo scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, null, null));

    List<String> emailOfUserGroupUsers = accountUsers.stream()
                                             .filter(user -> userGroupUserIds.contains(user.getUuid()))
                                             .map(UserMetadataDTO::getEmail)
                                             .collect(Collectors.toList());

    List<CatalogEntity> userCatalogEntities =
        catalogEntityRepository.findAllByParentUniqueIdAndKind(scopeInfo.getUniqueId(), USER_KIND);

    String userGroup = "group:" + userGroupId;

    List<CatalogEntity> toUpdateCatalogEntities = new ArrayList<>();

    // Handling delete case - if user is removed from the user group.
    List<CatalogEntity> oldUsersForUserGroup = new ArrayList<>();
    for (CatalogEntity userCatalogEntity : userCatalogEntities) {
      if (!isEmpty(userCatalogEntity.getRelations())
          && userCatalogEntity.getRelations().get(MEMBER_OF).contains(userGroup)) {
        oldUsersForUserGroup.add(userCatalogEntity);
      }
    }

    // Users which are removed from user group
    List<CatalogEntity> removedUsersFromUserGroup =
        oldUsersForUserGroup.stream()
            .filter(oldUser -> !emailOfUserGroupUsers.contains(oldUser.getIdentifier()))
            .collect(Collectors.toList());

    // updating relations for removed users
    for (CatalogEntity userCatalogEntity : removedUsersFromUserGroup) {
      Map<String, Set<String>> relations = userCatalogEntity.getRelations();
      Set<String> memberOfList = relations.get(MEMBER_OF);
      memberOfList.remove(userGroup);
      relations.put(MEMBER_OF, memberOfList);
      userCatalogEntity.setRelations(relations);
      userCatalogEntity.setYaml(CatalogMapper.presentationYaml(userCatalogEntity));
    }

    List<CatalogEntity> newCatalogUsers =
        userCatalogEntities.stream()
            .filter(oldUser -> emailOfUserGroupUsers.contains(oldUser.getIdentifier()))
            .collect(Collectors.toList());

    if (userGroupEntity.isPresent()) {
      Map<String, Set<String>> relations = userGroupEntity.get().getRelations();
      if (!isEmpty(relations)) {
        Set<String> memberOfList = relations.get(HAS_MEMBER);

        if (!isEmpty(memberOfList)) {
          // Remove the "user:" prefix from all entries in memberOfList
          Set<String> emailIdsInMemberOfList = memberOfList.stream()
                                                   .map(member -> member.substring(5)) // Directly remove "user:" prefix
                                                   .collect(Collectors.toSet());

          // Filter out newCatalogUsers that exist in the memberOfList
          newCatalogUsers = newCatalogUsers.stream()
                                .filter(newUser -> !emailIdsInMemberOfList.contains(newUser.getIdentifier()))
                                .collect(Collectors.toList());
        }
      }
    }

    for (CatalogEntity userCatalogEntity : newCatalogUsers) {
      updateUserCatalogEntityRelations(userCatalogEntity, userGroupId);
    }

    toUpdateCatalogEntities.addAll(newCatalogUsers);
    toUpdateCatalogEntities.addAll(removedUsersFromUserGroup);
    return toUpdateCatalogEntities;
  }

  private void updateUserCatalogEntityRelations(CatalogEntity userCatalogEntity, String userGroupId) {
    Map<String, Set<String>> relations = userCatalogEntity.getRelations();
    String userGroup = "group:" + userGroupId;

    if (!isEmpty(relations) && relations.containsKey(MEMBER_OF)) {
      relations.get(MEMBER_OF).add(userGroup);
    } else {
      // making it mutable
      Map<String, Set<String>> map = new HashMap<>();
      map.put(MEMBER_OF, new HashSet<>(Set.of(userGroup)));
      relations = map;
    }
    userCatalogEntity.setRelations(relations);
    userCatalogEntity.setYaml(CatalogMapper.presentationYaml(userCatalogEntity));
  }

  private UserMetadataDTO getUserMetaDataFromUserMembershipDTO(UserMembershipDTO userMembershipDTO) {
    Optional<UserInfo> userInfo = CGRestUtils.getResponse(userClient.getUserById(userMembershipDTO.getUserId()));

    if (userInfo.isEmpty()) {
      throw new UnexpectedException("Error in getting the user info for user id - " + userMembershipDTO.getUserId()
          + " in account - " + userMembershipDTO.getScope().getAccountIdentifier());
    }

    return UserMetadataDTO.builder()
        .email(userInfo.get().getEmail())
        .name(userInfo.get().getName())
        .uuid(userInfo.get().getUuid())
        .build();
  }

  private List<UserGroupDTO> getUserGroups(String accountIdentifier) {
    List<UserGroupDTO> userGroupDTOS = new ArrayList<>();
    io.harness.ng.beans.PageResponse<UserGroupDTO> userGroupDTOPageResponse;
    int page = 0;
    do {
      userGroupDTOPageResponse = NGRestUtils.getResponse(userGroupClient.getUserGroups(accountIdentifier,
          UserGroupFilterDTO.builder().accountIdentifier(accountIdentifier).build(), page, 100, null));
      if (userGroupDTOPageResponse != null && isNotEmpty(userGroupDTOPageResponse.getContent())) {
        userGroupDTOS.addAll(userGroupDTOPageResponse.getContent().stream().toList());
      }
      page++;
    } while (userGroupDTOPageResponse != null && isNotEmpty(userGroupDTOPageResponse.getContent()));
    return userGroupDTOS;
  }

  @VisibleForTesting
  <T> InlineCatalogEntity buildInlineCatalogEntityForMigration(
      String accountIdentifier, String type, T entity, Map<String, String> usernameAndEmailMapping) {
    switch (type) {
      case USER_TYPE:
        return buildInlineCatalogEntityForMigrationUser(accountIdentifier, (UserMetadataDTO) entity);
      case USER_GROUP_TYPE:
        return buildInlineCatalogEntityForMigrationUserGroup(accountIdentifier, (UserGroupDTO) entity);
      case API_COMPONENT_RESOURCE_TEMPLATE_TYPE:
        return buildInlineCatalogEntityForMigrationApiComponentResourceTemplate(
            accountIdentifier, (Map<String, Object>) entity, usernameAndEmailMapping);
      default:
        throw new InvalidRequestException(
            "Unsupported type " + type + " for IDP catalog entities to Harness native entities migration");
    }
  }

  private InlineCatalogEntity buildInlineCatalogEntityForMigrationUser(
      String accountIdentifier, UserMetadataDTO userMetadataDTO) {
    return InlineCatalogEntity.builder()
        .accountIdentifier(accountIdentifier)
        .identifier(userMetadataDTO.getEmail())
        .referenceType(ReferenceType.INLINE)
        .apiVersion(HARNESS_API_VERSION)
        .kind(USER_KIND)
        .name(userMetadataDTO.getName())
        .metadata(Map.of(UUID, userMetadataDTO.getUuid()))
        .build();
  }

  private InlineCatalogEntity buildInlineCatalogEntityForMigrationUserGroup(
      String accountIdentifier, UserGroupDTO userGroupDTO) {
    return InlineCatalogEntity.builder()
        .accountIdentifier(accountIdentifier)
        .identifier(userGroupDTO.getIdentifier())
        .referenceType(ReferenceType.INLINE)
        .apiVersion(HARNESS_API_VERSION)
        .kind(GROUP_KIND)
        .type("team")
        .name(userGroupDTO.getName())
        .build();
  }

  private InlineCatalogEntity buildInlineCatalogEntityForMigrationApiComponentResourceTemplate(String accountIdentifier,
      Map<String, Object> filteredBackstageCatalogEntity, Map<String, String> usernameAndEmailMapping) {
    String identifier = Objects.requireNonNull(from(filteredBackstageCatalogEntity, "metadata.name", String.class));

    String kind = Objects.requireNonNull(from(filteredBackstageCatalogEntity, KIND, String.class)).toLowerCase();
    kind = getHarnessCatalogKind(kind);

    String name = from(filteredBackstageCatalogEntity, "metadata.title", String.class);
    if (isEmpty(name)) {
      name = identifier;
    }

    Map<String, Object> spec =
        new HashMap<>(Objects.requireNonNull(from(filteredBackstageCatalogEntity, SPEC, Map.class)));
    if (!isEmpty(spec)) {
      spec.remove(TYPE);
      spec.remove(OWNER);
      spec.remove(SYSTEM_SPEC_RELATION_REF);
      spec.remove(OWNED_BY);
      spec.remove(OWNER_OF);
      spec.remove(CONSUMES_API);
      spec.remove(API_CONSUMED_BY);
      spec.remove(PROVIDES_API);
      spec.remove(API_PROVIDED_BY);
      spec.remove(DEPENDS_ON);
      spec.remove(DEPENDENCY_OF);
      spec.remove(PARENT_OF);
      spec.remove(CHILD_OF);
      spec.remove(MEMBER_OF);
      spec.remove(HAS_MEMBER);
      spec.remove(PART_OF);
      spec.remove(HAS_PART);
      spec.remove(LEADER_OF);
      spec.remove(HAS_LEADER);
    }

    String specYaml = YamlUtils.writeObjectAsYaml(spec);
    if (specYaml.contains("getContextData: '{{ formContext")) {
      specYaml = specYaml.replace("getContextData: '{{ formContext", "getContextData: '${{ formContext");
    }
    spec = YamlUtils.loadYamlStringAsMap(specYaml);

    Map<String, Object> metadata =
        new HashMap<>(Objects.requireNonNull(from(filteredBackstageCatalogEntity, "metadata", Map.class)));
    if (!isEmpty(metadata)) {
      metadata.remove("identifier");
      metadata.remove("absoluteIdentifier");
      metadata.remove("uid");
      metadata.remove("etag");
      metadata.remove(NAME);
      metadata.remove(TITLE);
      metadata.remove(DESCRIPTION);
      metadata.remove(TAGS);
      metadata.remove(NAMESPACE);
      metadata.remove(SOURCE_LOCATION_ANNOTATION);
      metadata.remove("backstage.io/source-template");
      metadata.remove("backstage.io/orphan");
    }

    List<Map<String, Object>> filteredBackstageCatalogEntityRelations =
        from(filteredBackstageCatalogEntity, "relations", List.class);
    Map<String, Set<String>> relations = null;
    if (!isEmpty(filteredBackstageCatalogEntityRelations)) {
      relations =
          filteredBackstageCatalogEntityRelations.stream()
              .collect(Collectors.groupingBy(map
                  -> (String) map.get(TYPE),
                  Collectors.mapping(
                      map
                      -> {
                        String targetRef = ((String) map.get(TARGET_REF)).toLowerCase();
                        return parseBackstageEntityReferenceToCatalogRelationRef(targetRef, usernameAndEmailMapping);
                      },
                      Collectors.filtering(Objects::nonNull, Collectors.toSet()))))
              .entrySet()
              .stream()
              .filter(entry -> !entry.getValue().isEmpty())
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    List<Map<String, Object>> filteredBackstageCatalogEntityStatusItems =
        from(filteredBackstageCatalogEntity, "status.items", List.class);
    List<Map<String, String>> status = null;
    if (!isEmpty(filteredBackstageCatalogEntityStatusItems)) {
      status = filteredBackstageCatalogEntityStatusItems.stream()
                   .map(map
                       -> Map.of(TYPE, (String) map.get(TYPE), LEVEL, (String) map.get(LEVEL), MESSAGE,
                           (String) map.get(MESSAGE)))
                   .toList();
    }

    InlineCatalogEntity inlineCatalogEntity =
        (InlineCatalogEntity) InlineCatalogEntity.builder()
            .accountIdentifier(accountIdentifier)
            .identifier(identifier)
            .referenceType(ReferenceType.INLINE)
            .apiVersion(HARNESS_API_VERSION)
            .kind(kind)
            .type(from(filteredBackstageCatalogEntity, "spec.type", String.class))
            .name(name)
            .description(from(filteredBackstageCatalogEntity, "metadata.description", String.class))
            .owner(parseBackstageEntityReferenceToCatalogRelationRef(
                from(filteredBackstageCatalogEntity, "spec.owner", String.class), usernameAndEmailMapping))
            .tags(from(filteredBackstageCatalogEntity, METADATA_TAGS, List.class))
            .sourceLocation(
                from(filteredBackstageCatalogEntity, METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION, String.class))
            .spec(spec)
            .metadata(metadata)
            .relations(relations)
            .status(status)
            .build();

    inlineCatalogEntity.setYaml(CatalogMapper.presentationYaml(inlineCatalogEntity));

    return inlineCatalogEntity;
  }

  private void updateMigrateCatalogEntitiesFromBackstageToHarnessCompletedToTrue(NamespaceEntity namespaceEntity) {
    NamespaceEntity.Metadata metadata = Objects.isNull(namespaceEntity.getMetadata())
        ? NamespaceEntity.Metadata.builder().build()
        : namespaceEntity.getMetadata();
    metadata.setMigrateCatalogEntitiesFromBackstageToHarnessCompleted(true);
    namespaceEntity.setMetadata(metadata);
    namespaceService.save(namespaceEntity);
  }

  private String getUniqueIdentifiersCombination(CatalogEntity catalogEntity) {
    return catalogEntity.getParentUniqueId() + ":" + catalogEntity.getKind() + ":" + catalogEntity.getIdentifier();
  }

  public void migrateRelationsEmailAddress(String accountIdentifier) {
    log.info("Starting the migration for Catalog Relations Email Address for account {}", accountIdentifier);
    List<CatalogEntity> catalogEntities = catalogEntityRepository.findAllByParentUniqueIdAndKindIn(
        accountIdentifier, List.of(API_KIND, COMPONENT_KIND, RESOURCE_KIND, WORKFLOW_KIND));
    List<UserMetadataDTO> userMetadataDTOS = getUsers(accountIdentifier, UserFilter.builder().build());
    Map<String, String> usernameAndEmailMapping = userMetadataDTOS.stream().collect(Collectors.toMap(userMetadataDTO
        -> userMetadataDTO.getEmail().split("@")[0],
        UserMetadataDTO::getEmail, (existing, replacement) -> existing));
    List<CatalogEntity> replacedCatalogEntities = new ArrayList<>();
    for (CatalogEntity catalogEntity : catalogEntities) {
      if (catalogEntity.getYaml().contains("@harness.io")) {
        replaceEmailAddress(catalogEntity, usernameAndEmailMapping);
        replacedCatalogEntities.add(catalogEntity);
      }
    }
    log.info("Found {} records for the migration for Catalog Relations Email Address for account {}",
        replacedCatalogEntities.size(), accountIdentifier);
    if (!replacedCatalogEntities.isEmpty()) {
      replacedCatalogEntities.forEach(
          catalogEntity -> catalogEntity.setQueryableEntityRef(catalogServiceHelper.queryableEntityRef(catalogEntity)));
      catalogEntityRepository.saveAll(replacedCatalogEntities);
    }
    log.info("Completed the migration for Catalog Relations Email Address for account {}", accountIdentifier);
  }

  private void replaceEmailAddress(CatalogEntity catalogEntity, Map<String, String> usernameAndEmailMapping) {
    catalogEntity.setOwner(replaceEmailAddressInCatalogRef(usernameAndEmailMapping, catalogEntity.getOwner()));
    catalogEntity.getRelations().forEach((key, value) -> {
      Set<String> updatedRelations =
          value.stream()
              .map(relation -> replaceEmailAddressInCatalogRef(usernameAndEmailMapping, relation))
              .collect(Collectors.toSet());
      catalogEntity.getRelations().put(key, updatedRelations);
    });
    catalogEntity.setYaml(CatalogMapper.presentationYaml(catalogEntity));
  }
}

/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.idp.catalog.utils.CatalogUtils.parseBackstageEntityRefFromCatalogRef;
import static io.harness.idp.catalog.utils.CatalogUtils.parseBackstageEntityRefFromCatalogRefWithoutUserManipulation;
import static io.harness.idp.catalog.utils.Constants.AIASSET;
import static io.harness.idp.catalog.utils.Constants.ANNOTATIONS;
import static io.harness.idp.catalog.utils.Constants.API;
import static io.harness.idp.catalog.utils.Constants.API_VERSION;
import static io.harness.idp.catalog.utils.Constants.BACKSTAGE_API_VERSION;
import static io.harness.idp.catalog.utils.Constants.BACKSTAGE_TEMPLATE_API_VERSION;
import static io.harness.idp.catalog.utils.Constants.CHILDREN;
import static io.harness.idp.catalog.utils.Constants.CHILD_OF;
import static io.harness.idp.catalog.utils.Constants.COMPONENT;
import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.idp.catalog.utils.Constants.CONSUMES_API;
import static io.harness.idp.catalog.utils.Constants.CORE_KINDS;
import static io.harness.idp.catalog.utils.Constants.DEFAULT_NAMESPACE;
import static io.harness.idp.catalog.utils.Constants.DEPENDENCY_OF;
import static io.harness.idp.catalog.utils.Constants.DEPENDS_ON;
import static io.harness.idp.catalog.utils.Constants.DESCRIPTION;
import static io.harness.idp.catalog.utils.Constants.DISPLAY_NAME;
import static io.harness.idp.catalog.utils.Constants.EMAIL;
import static io.harness.idp.catalog.utils.Constants.ENTITY_CONFLICT;
import static io.harness.idp.catalog.utils.Constants.ENTITY_UUID_ANNOTATION;
import static io.harness.idp.catalog.utils.Constants.GROUP;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.HARNESS_API_VERSION;
import static io.harness.idp.catalog.utils.Constants.HAS_LEADER;
import static io.harness.idp.catalog.utils.Constants.HAS_MEMBER;
import static io.harness.idp.catalog.utils.Constants.HIERARCHY_KIND;
import static io.harness.idp.catalog.utils.Constants.KIND;
import static io.harness.idp.catalog.utils.Constants.LEADERS;
import static io.harness.idp.catalog.utils.Constants.LEADER_OF;
import static io.harness.idp.catalog.utils.Constants.MANAGED_BY_LOCATION_ANNOTATION;
import static io.harness.idp.catalog.utils.Constants.MANAGED_BY_ORIGIN_LOCATION_ANNOTATION;
import static io.harness.idp.catalog.utils.Constants.MEMBERS;
import static io.harness.idp.catalog.utils.Constants.MEMBER_OF;
import static io.harness.idp.catalog.utils.Constants.METADATA;
import static io.harness.idp.catalog.utils.Constants.METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION;
import static io.harness.idp.catalog.utils.Constants.METADATA_TAGS;
import static io.harness.idp.catalog.utils.Constants.NAME;
import static io.harness.idp.catalog.utils.Constants.NAMESPACE;
import static io.harness.idp.catalog.utils.Constants.NAMESPACE_FOR_ENTITY_CONFLICT;
import static io.harness.idp.catalog.utils.Constants.OWNED_BY;
import static io.harness.idp.catalog.utils.Constants.OWNER;
import static io.harness.idp.catalog.utils.Constants.PARENT;
import static io.harness.idp.catalog.utils.Constants.PARENT_OF;
import static io.harness.idp.catalog.utils.Constants.PART_OF;
import static io.harness.idp.catalog.utils.Constants.PROFILE;
import static io.harness.idp.catalog.utils.Constants.PROVIDES_API;
import static io.harness.idp.catalog.utils.Constants.RELATIONS;
import static io.harness.idp.catalog.utils.Constants.RESOURCE;
import static io.harness.idp.catalog.utils.Constants.ROLES_ANNOTATION;
import static io.harness.idp.catalog.utils.Constants.SOURCE_LOCATION_ANNOTATION;
import static io.harness.idp.catalog.utils.Constants.SPEC;
import static io.harness.idp.catalog.utils.Constants.SUB_COMPONENT_OF;
import static io.harness.idp.catalog.utils.Constants.SUPPORTED_HARNESS_TO_IDP_CONVERSION_KINDS;
import static io.harness.idp.catalog.utils.Constants.SUPPORTED_HARNESS_TO_IDP_SYNC_KINDS;
import static io.harness.idp.catalog.utils.Constants.SYSTEM;
import static io.harness.idp.catalog.utils.Constants.SYSTEM_SPEC_RELATION_REF;
import static io.harness.idp.catalog.utils.Constants.TAGS;
import static io.harness.idp.catalog.utils.Constants.TARGET;
import static io.harness.idp.catalog.utils.Constants.TARGET_REF;
import static io.harness.idp.catalog.utils.Constants.TEMPLATE;
import static io.harness.idp.catalog.utils.Constants.TEMPLATE_KIND;
import static io.harness.idp.catalog.utils.Constants.TITLE;
import static io.harness.idp.catalog.utils.Constants.TYPE;
import static io.harness.idp.catalog.utils.Constants.USER;
import static io.harness.idp.catalog.utils.Constants.UUID;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.idp.common.CommonUtils.from;
import static io.harness.idp.common.Constants.ORGANIZATION_IDENTIFIER;
import static io.harness.idp.common.Constants.ORGANIZATION_NAME;
import static io.harness.idp.common.Constants.PROJECT_IDENTIFIER;
import static io.harness.idp.common.Constants.PROJECT_NAME;
import static io.harness.idp.common.Constants.RESPONSE_STATUS;
import static io.harness.idp.common.JacksonUtils.readValueForObject;
import static io.harness.idp.common.YamlUtils.mergeDecorator;
import static io.harness.idp.common.YamlUtils.writeObjectAsYaml;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.utils.YamlPipelineUtils.writeYamlString;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.clients.BackstageResourceClient;
import io.harness.clients.HarnessToIDPSyncRequest;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.mapper.CatalogMapper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.role.dto.RoleAssignmentMetadataDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.userng.remote.UserNGClient;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class HarnessToIDPHelper {
  @Inject @Named("PRIVILEGED") UserNGClient userNGClient;
  @Inject CatalogServiceHelper catalogServiceHelper;
  @Inject BackstageResourceClient backstageResourceClient;
  @Inject NamespaceService namespaceService;
  @Inject CatalogEntityRepository catalogEntityRepository;
  @Inject @Named("PRIVILEGED") ProjectClient projectClient;
  @Inject @Named("PRIVILEGED") OrganizationClient organizationClient;
  @Inject IdpCommonService idpCommonService;

  public static <T extends CatalogEntity> boolean shouldCopyToBackstageCatalog(T catalogEntity) {
    return SUPPORTED_HARNESS_TO_IDP_SYNC_KINDS.contains(catalogEntity.getKind());
  }

  public <T extends CatalogEntity> Object buildBackstageCatalog(
      T catalogEntity, boolean shouldPopulateRelations, boolean migration, boolean shouldPopulateCustomRelations) {
    String yaml = writeObjectAsYaml(catalogEntity);
    Map<String, Object> catalogEntityMap = YamlUtils.loadYamlStringAsMap(yaml);

    catalogEntityMap.remove("id");
    catalogEntityMap.remove("accountIdentifier");
    catalogEntityMap.remove("referenceType");
    catalogEntityMap.remove("yaml");
    catalogEntityMap.remove("status");
    catalogEntityMap.remove("uniqueId");
    catalogEntityMap.remove("parentUniqueId");
    catalogEntityMap.remove("createdAt");
    catalogEntityMap.remove("createdBy");
    catalogEntityMap.remove("lastUpdatedAt");
    catalogEntityMap.remove("lastUpdatedBy");

    catalogEntityMap.remove("identifier");
    catalogEntityMap.remove(NAME);
    catalogEntityMap.remove(DESCRIPTION);
    catalogEntityMap.remove(TAGS);
    catalogEntityMap.remove("sourceLocation");
    catalogEntityMap.remove("orgIdentifier");
    catalogEntityMap.remove("projectIdentifier");
    catalogEntityMap.remove(OWNER);
    catalogEntityMap.remove(RELATIONS);
    catalogEntityMap.remove("storeType");
    catalogEntityMap.remove("repo");
    catalogEntityMap.remove("filePath");
    catalogEntityMap.remove("connectorRef");
    catalogEntityMap.remove("repoURL");
    catalogEntityMap.remove("fallBackBranch");
    catalogEntityMap.remove("entityType");
    catalogEntityMap.remove("data");
    catalogEntityMap.remove("decorator");

    String kind = convertToBackstageKind(catalogEntity.getKind());
    catalogEntityMap.put(KIND, kind);
    if (kind.equalsIgnoreCase(TEMPLATE)) {
      catalogEntityMap.put(API_VERSION, BACKSTAGE_TEMPLATE_API_VERSION);
    } else {
      catalogEntityMap.put(API_VERSION, BACKSTAGE_API_VERSION);
    }

    String type = catalogEntity.getType();
    catalogEntityMap.remove(TYPE);

    String identifier = catalogEntity.getIdentifier();
    String name = catalogEntity.getName();
    List<String> tags = catalogEntity.getTags();
    String description = catalogEntity.getDescription();
    String sourceLocation = catalogEntity.getSourceLocation();

    Map<String, Object> metadata =
        !isEmpty(catalogEntity.getMetadata()) ? new HashMap<>(catalogEntity.getMetadata()) : new HashMap<>();
    metadata.put(NAME, kind.equals(USER) ? identifier.split("@")[0].replaceAll("\\+", "plus") : identifier);
    if (!isEmpty(name)) {
      metadata.put(TITLE, name);
    }
    String namespace = null;

    if (migration) {
      // This is for deleting the older entity having the default namespace.
      if (metadata.containsKey(ENTITY_CONFLICT) && metadata.get(ENTITY_CONFLICT).equals(true)) {
        String[] namespaceName = identifier.split("_");
        metadata.put(NAMESPACE, metadata.get(NAMESPACE_FOR_ENTITY_CONFLICT));
        metadata.put(
            NAME, kind.equals(USER) ? namespaceName[1].split("@")[0].replaceAll("\\+", "plus") : namespaceName[1]);
      } else {
        namespace = DEFAULT_NAMESPACE;
        metadata.put(NAMESPACE, namespace);
      }

    } else {
      namespace = CatalogUtils.getNamespace(catalogEntity);
      metadata.put(NAMESPACE, namespace);
      metadata = addOrgAndProjectMetadata(metadata, catalogEntity.getAccountIdentifier(),
          catalogEntity.getOrgIdentifier(), catalogEntity.getProjectIdentifier());
    }

    if (!isEmpty(description)) {
      metadata.put(DESCRIPTION, description);
    }

    if (!isEmpty(tags)) {
      metadata.put(TAGS, tags);
    }

    Map<String, Object> annotations = !isEmpty((Map<String, Object>) metadata.get(ANNOTATIONS))
        ? new HashMap<>((Map<String, Object>) metadata.get(ANNOTATIONS))
        : new HashMap<>();

    Map<String, Object> spec =
        !isEmpty(catalogEntity.getSpec()) ? new HashMap<>(catalogEntity.getSpec()) : new HashMap<>();

    Map<String, Object> sourceCode = spec != null ? (Map<String, Object>) spec.get("sourceCode") : null;
    if (!isEmpty(sourceLocation)) {
      annotations.put(SOURCE_LOCATION_ANNOTATION, sourceLocation);
      modifySourceLocationAndUpdateAnnotation(sourceLocation, metadata, annotations);
    } else if (sourceCode != null) {
      String url = (String) sourceCode.get("url");

      if (!isEmpty(url)) {
        annotations.put(SOURCE_LOCATION_ANNOTATION, "url:" + url);
      }
      modifySourceLocationAndUpdateAnnotation(url, metadata, annotations);
    } else {
      annotations.put(MANAGED_BY_LOCATION_ANNOTATION,
          "url:https://app.harness.io/" + kind + "/" + namespace + "/" + metadata.get(NAME));
      annotations.put(MANAGED_BY_ORIGIN_LOCATION_ANNOTATION,
          "url:https://app.harness.io/" + kind + "/" + namespace + "/" + metadata.get(NAME));
    }

    if (!isEmpty(annotations)) {
      metadata.put(ANNOTATIONS, annotations);
    }
    populateMetadata(metadata, kind, catalogEntity.getAccountIdentifier());
    catalogEntityMap.put(METADATA, metadata);

    Map<String, Set<String>> relations =
        !isEmpty(catalogEntity.getRelations()) ? new HashMap<>(catalogEntity.getRelations()) : new HashMap<>();

    if (!isEmpty(type)) {
      spec.put(TYPE, type);
    }

    Set<String> ownedBy = relations.get(OWNED_BY);
    if (!isEmpty(ownedBy)) {
      String owner = ownedBy.stream().findFirst().orElse(null);
      if (!isEmpty(owner)
          && (kind.equals(API) || kind.equals(COMPONENT) || kind.equals(TEMPLATE) || kind.equals(RESOURCE)
              || kind.equals(SYSTEM) || kind.equals(AIASSET))) {
        spec.put(OWNER, parseBackstageEntityRefFromCatalogRef(owner, migration));
      }
    }

    populateSpec(relations, spec, kind, identifier, name, migration, shouldPopulateCustomRelations);
    catalogEntityMap.put(SPEC, spec);

    if (shouldPopulateRelations) {
      catalogEntityMap.put(RELATIONS, populateRelations(catalogEntity, relations));
    }

    Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData();
    if (isEmpty(processedData)) {
      return catalogEntityMap;
    } else {
      if (processedData.get("integration_config_ref") != null) {
        processedData.remove("integration_config_ref");
      }
      return mergeDecorator(catalogEntityMap, processedData);
    }
  }

  private void modifySourceLocationAndUpdateAnnotation(
      String sourceLocation, Map<String, Object> metadata, Map<String, Object> annotations) {
    if (sourceLocation.startsWith("url:")) {
      sourceLocation = sourceLocation.substring(4);
    }
    if (!sourceLocation.endsWith("/")) {
      sourceLocation = sourceLocation + "/";
    }
    if (annotations.get(MANAGED_BY_LOCATION_ANNOTATION) == null) {
      annotations.put(MANAGED_BY_LOCATION_ANNOTATION, "url:" + sourceLocation + metadata.get(NAME) + ".yaml");
    }
    if (annotations.get(MANAGED_BY_ORIGIN_LOCATION_ANNOTATION) == null) {
      annotations.put(MANAGED_BY_ORIGIN_LOCATION_ANNOTATION, "url:" + sourceLocation + metadata.get(NAME) + ".yaml");
    }
  }

  public Map<String, Object> addOrgAndProjectMetadata(
      Map<String, Object> metadata, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (!isEmpty(orgIdentifier)) {
      try {
        Optional<OrganizationResponse> response =
            getResponse(organizationClient.getOrganization(orgIdentifier, accountIdentifier));
        if (response.isPresent()) {
          metadata.put(ORGANIZATION_NAME, response.get().getOrganization().getName());
        }
        metadata.put(ORGANIZATION_IDENTIFIER, orgIdentifier);

      } catch (InvalidRequestException e) {
        throw new InvalidRequestException(String.format("Organisation with orgIdentifier %s not found in account - %s",
                                              orgIdentifier, accountIdentifier),
            e);
      }
    }

    if (!isEmpty(projectIdentifier)) {
      try {
        Optional<ProjectResponse> response =
            getResponse(projectClient.getProject(projectIdentifier, accountIdentifier, orgIdentifier));
        if (response.isPresent()) {
          metadata.put(PROJECT_NAME, response.get().getProject().getName());
        }
        metadata.put(PROJECT_IDENTIFIER, projectIdentifier);
      } catch (InvalidRequestException e) {
        throw new InvalidRequestException(
            String.format("Project with orgIdentifier %s and identifier %s not found in account - %s", orgIdentifier,
                projectIdentifier, accountIdentifier),
            e);
      }
    }
    return metadata;
  }

  public String convertHarnessToBackstage(
      String harnessAccount, String yaml, String entityRef, boolean loadFromFallbackBranch) {
    try {
      CatalogEntity catalogEntity = null;
      if (!isEmpty(entityRef)) {
        Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
        List<ScopeInfo> scopeInfos =
            catalogServiceHelper
                .getScopeInfosBasedOnScopesAndEntityRefs(harnessAccount, kindScopeIdentifier.getMiddle(), null)
                .getLeft();
        catalogEntity = catalogServiceHelper.catalogEntityFromGit(scopeInfos.get(0).getUniqueId(),
            kindScopeIdentifier.getLeft(), kindScopeIdentifier.getRight(), true, loadFromFallbackBranch);
        catalogServiceHelper.checkRbacWithOwnerFallback(harnessAccount, entityRef, catalogEntity.getOwner(), "view");
        catalogEntity.setYaml(
            catalogServiceHelper.resolveExpressionsInEntityYaml(harnessAccount, catalogEntity.getYaml()));
        yaml = catalogEntity.getDecoratedYaml();
      }

      Map<String, Object> yamlMap = YamlUtils.loadYamlStringAsMap(yaml);
      String apiVersion = from(yamlMap, API_VERSION, String.class);
      if (isEmpty(apiVersion) || !apiVersion.equals(HARNESS_API_VERSION)) {
        throw new InvalidRequestException(
            "apiVersion should be " + HARNESS_API_VERSION + " for harness to backstage entity conversion");
      }

      String kind = from(yamlMap, KIND, String.class);
      if (isEmpty(kind)) {
        throw new InvalidRequestException("Kind cannot be null or empty");
      }
      kind = kind.toLowerCase();
      if (HIERARCHY_KIND.equals(kind)) {
        throw new InvalidRequestException(
            "Kind " + kind + " is not supported for harness to backstage entity conversion");
      }

      String identifier = from(yamlMap, "identifier", String.class);
      if (isEmpty(identifier)) {
        throw new InvalidRequestException("Identifier cannot be null or empty");
      }

      String orgIdentifier = from(yamlMap, "orgIdentifier", String.class);
      String projectIdentifier = from(yamlMap, "projectIdentifier", String.class);

      if (catalogEntity != null
          && (!Objects.equals(kind, catalogEntity.getKind())
              || !Objects.equals(identifier, catalogEntity.getIdentifier())
              || !Objects.equals(orgIdentifier, catalogEntity.getOrgIdentifier())
              || !Objects.equals(projectIdentifier, catalogEntity.getProjectIdentifier()))) {
        throw new InvalidRequestException(
            "Mismatch in kind / identifier / orgIdentifier / projectIdentifier between existing entity and YAML input");
      }

      String name = from(yamlMap, NAME, String.class);
      if (isEmpty(name)) {
        name = identifier;
      }

      String sourceLocation = from(yamlMap, METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION, String.class);

      String owner = from(yamlMap, OWNER, String.class);
      if (CORE_KINDS.contains(kind) && isEmpty(owner)) {
        throw new InvalidRequestException("owner cannot be null or empty for kind as api / component / resource");
      }

      Map<String, Set<String>> relations = new HashMap<>();
      if (catalogEntity != null) {
        relations = catalogEntity.getRelations();
        if (relations == null) {
          relations = new HashMap<>();
        }
      }
      if (!isEmpty(owner)) {
        relations.put(OWNED_BY, Set.of(owner));
      }

      InlineCatalogEntity inlineCatalogEntity = new InlineCatalogEntity();
      inlineCatalogEntity.setAccountIdentifier(harnessAccount);
      inlineCatalogEntity.setOrgIdentifier(orgIdentifier);
      inlineCatalogEntity.setProjectIdentifier(projectIdentifier);
      inlineCatalogEntity.setIdentifier(identifier);
      inlineCatalogEntity.setReferenceType(ReferenceType.INLINE);
      inlineCatalogEntity.setApiVersion(BACKSTAGE_API_VERSION);
      inlineCatalogEntity.setKind(kind);
      inlineCatalogEntity.setType(from(yamlMap, TYPE, String.class));
      inlineCatalogEntity.setName(name);
      inlineCatalogEntity.setDescription(from(yamlMap, "metadata.description", String.class));
      inlineCatalogEntity.setOwner(owner);
      inlineCatalogEntity.setTags(from(yamlMap, METADATA_TAGS, List.class));
      inlineCatalogEntity.setSourceLocation(sourceLocation);
      inlineCatalogEntity.setSpec(from(yamlMap, SPEC, Map.class));
      inlineCatalogEntity.setMetadata(from(yamlMap, METADATA, Map.class));
      inlineCatalogEntity.setRelations(relations);
      inlineCatalogEntity.setYaml(CatalogMapper.presentationYaml(inlineCatalogEntity));
      catalogServiceHelper.validateSystemScope(inlineCatalogEntity);

      Object backstageCatalog = buildBackstageCatalog(inlineCatalogEntity, true, false, true);
      String backstageYaml = writeYamlString(backstageCatalog);
      if (SUPPORTED_HARNESS_TO_IDP_CONVERSION_KINDS.contains(kind)) {
        catalogServiceHelper.validateAgainstBackstageJsonSchema(
            WORKFLOW_KIND.equals(inlineCatalogEntity.getKind()) ? TEMPLATE_KIND : inlineCatalogEntity.getKind(),
            backstageYaml);
      }
      return backstageYaml;
    } catch (Exception ex) {
      log.error("Error in convertHarnessToBackstage. Exception = {}", ex.getMessage(), ex);
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  public void syncUserGroupsIdentifierStartingWithUnderscore(NamespaceEntity namespaceEntity) {
    String accountIdentifier = namespaceEntity.getAccountIdentifier();
    log.info("Starting the migration for user groups sync for identifiers starting with underscore for account {}",
        accountIdentifier);
    try {
      List<CatalogEntity> catalogEntities =
          catalogEntityRepository.findAllByAccountIdentifierAndKindAndIdentifierStartingWithUnderscore(
              accountIdentifier, GROUP_KIND);
      if (!isEmpty(catalogEntities)) {
        harnessToIdpSync(catalogEntities, accountIdentifier, CREATE_ACTION);
        updateUserGroupSyncCompletedToTrue(namespaceEntity);
      }
      log.info(
          "Found {} entities for migrating user groups sync for identifiers starting with underscore for account {}",
          accountIdentifier, catalogEntities.size());
    } catch (Exception e) {
      log.error("Error occurred during the user groups sync for identifiers starting with underscore for account {}",
          accountIdentifier, e);
    }
  }

  private void populateMetadata(Map<String, Object> metadata, String kind, String accountIdentifier) {
    switch (kind) {
      case GROUP -> populateMetadataForGroup(metadata);
      case USER -> populateMetadataForUser(metadata, accountIdentifier);
    }
  }

  private void populateMetadataForUser(Map<String, Object> metadata, String accountIdentifier) {
    String uuid = (String) metadata.get(UUID);
    try {
      String roles = NGRestUtils.getResponse(userNGClient.getAggregatedUser(uuid, accountIdentifier, null, null))
              .getRoleAssignmentMetadata().stream().map(RoleAssignmentMetadataDTO::getRoleIdentifier).collect(Collectors.joining(","));
      metadata.put(ROLES_ANNOTATION, roles);
    }catch (Exception e){
      log.info("Unable to fetch the roles for the user - {} in account - {}", uuid, accountIdentifier, e);
    }
    Map<String, Object> annotations = !isEmpty((Map<String, Object>) metadata.get(ANNOTATIONS))
            ? new HashMap<>((Map<String, Object>) metadata.get(ANNOTATIONS))
            :
        new HashMap<>();
        annotations.put(ENTITY_UUID_ANNOTATION, uuid);
        metadata.put(ANNOTATIONS, annotations);
        metadata.remove(UUID);
    }

    private void populateMetadataForGroup(Map<String, Object> metadata) {
      metadata.put("created_by", "Harness");
    }

    public void populateSpec(Map<String, Set<String>> relations, Map<String, Object> spec, String kind,
        String identifier, String name, boolean migration, boolean shouldPopulateCustomRelations) {
      switch (kind) {
        case COMPONENT -> populateSpecForComponent(relations, spec, migration);
        case GROUP -> populateSpecForGroup(relations, spec, migration);
        case USER -> populateSpecForUser(relations, spec, identifier, name, migration, shouldPopulateCustomRelations);
        case RESOURCE -> populateSpecForResource(relations, spec, migration);
        case API -> populateSpecForApi(relations, spec, migration); // System is supported for Component, Apis and Resources - https://backstage.io/docs/features/software-catalog/system-model/
    }
  }

  private void populateSpecForComponent(Map<String, Set<String>> relations, Map<String, Object> spec, boolean migration) {
    Set<String> partOf = relations.get(PART_OF);
    Set<String> providesApi = relations.get(PROVIDES_API);
    Set<String> consumesApi = relations.get(CONSUMES_API);
    Set<String> dependsOn = relations.get(DEPENDS_ON);
    Set<String> dependencyOf = relations.get(DEPENDENCY_OF);

    if (!isEmpty(partOf)) {
      List<String> systemRefs = new ArrayList<>();
      List<String> partOfRefs = new ArrayList<>();
      boolean hasSubcomponentOf = spec.containsKey(SUB_COMPONENT_OF);

      for (String part : partOf) {
            if (part.startsWith(COMPONENT.toLowerCase())) {
              if (hasSubcomponentOf) {
                spec.put(SUB_COMPONENT_OF, parseBackstageEntityRefFromCatalogRef(part, migration));
              } else {
                partOfRefs.add(parseBackstageEntityRefFromCatalogRef(part, migration));
              }
            } else if (part.startsWith(SYSTEM_SPEC_RELATION_REF.toLowerCase())) {
              systemRefs.add(parseBackstageEntityRefFromCatalogRef(part, migration));
            } else {
              partOfRefs.add(parseBackstageEntityRefFromCatalogRef(part, migration));
            }
          }

          if (!systemRefs.isEmpty()) {
            spec.put(SYSTEM_SPEC_RELATION_REF.toLowerCase(), systemRefs);
          }
          if (!partOfRefs.isEmpty()) {
            spec.put(PART_OF, partOfRefs);
          }
      }

      if (!isEmpty(providesApi)) {
        spec.put(PROVIDES_API,
            providesApi.stream()
                .map(part -> CatalogUtils.parseBackstageEntityRefFromCatalogRef(part, migration))
                .collect(Collectors.toList()));
      }
      if (!isEmpty(consumesApi)) {
        spec.put(CONSUMES_API,
            consumesApi.stream()
                .map(part -> CatalogUtils.parseBackstageEntityRefFromCatalogRef(part, migration))
                .collect(Collectors.toList()));
      }
      if (!isEmpty(dependsOn)) {
        spec.put(DEPENDS_ON,
            dependsOn.stream()
                .map(value -> {
                  String entityRef = Objects.requireNonNull(parseBackstageEntityRefFromCatalogRef(value, migration));
                  return entityRef.contains(":") ? entityRef : COMPONENT.toLowerCase() + ":" + entityRef;
                })
                .collect(Collectors.toList()));
      }
      if (!isEmpty(dependencyOf)) {
        spec.put(DEPENDENCY_OF,
            dependencyOf.stream()
                .map(part -> CatalogUtils.parseBackstageEntityRefFromCatalogRef(part, migration))
                .collect(Collectors.toList()));
      }
    }

    private void populateSpecForGroup(Map<String, Set<String>> relations, Map<String, Object> spec, boolean migration) {
      if (isEmpty(relations)) {
        spec.put(CHILDREN, new ArrayList<>());
        return;
      }
      String childOf =
          !isEmpty(relations.get(CHILD_OF)) ? relations.get(CHILD_OF).stream().findFirst().orElse(null) : null;
      Set<String> parentOf = relations.get(PARENT_OF);
      Set<String> hasMember = relations.get(HAS_MEMBER);
      Set<String> hasLeader = relations.get(HAS_LEADER);

      if (!isEmpty(childOf)) {
        spec.put(PARENT, parseBackstageEntityRefFromCatalogRef(childOf, migration));
      }

      spec.put(CHILDREN,
          !isEmpty(parentOf) ? parentOf.stream()
                                   .map(part -> CatalogUtils.parseBackstageEntityRefFromCatalogRef(part, migration))
                                   .collect(Collectors.toList())
                             : new ArrayList<>());

      if (!isEmpty(hasMember)) {
        spec.put(MEMBERS,
            hasMember.stream()
                .map(part -> CatalogUtils.parseBackstageEntityRefFromCatalogRef(part, migration))
                .collect(Collectors.toList()));
      }

      if (!isEmpty(hasLeader)) {
        spec.put(LEADERS,
            hasLeader.stream()
                .map(part -> CatalogUtils.parseBackstageEntityRefFromCatalogRef(part, migration))
                .collect(Collectors.toList()));
      }
    }

    private void populateSpecForUser(Map<String, Set<String>> relations, Map<String, Object> spec, String identifier,
        String name, boolean migration, boolean shouldPopulateCustomRelations) {
      Set<String> memberOf = relations.get(MEMBER_OF);
      Set<String> leaderOf = relations.get(LEADER_OF);

      spec.put(MEMBER_OF,
          !isEmpty(memberOf) ? memberOf.stream()
                                   .map(part -> CatalogUtils.parseBackstageEntityRefFromCatalogRef(part, migration))
                                   .collect(Collectors.toList())
                             : new ArrayList<>());

      if (!isEmpty(leaderOf) && shouldPopulateCustomRelations) {
        spec.put(LEADER_OF,
            leaderOf.stream()
                .map(part -> CatalogUtils.parseBackstageEntityRefFromCatalogRef(part, migration))
                .collect(Collectors.toList()));
      }

      Map<String, String> profile = new HashMap<>() {
        {
          put(DISPLAY_NAME, name);
          put(EMAIL, identifier);
        }
      };
      spec.put(PROFILE, profile);
    }

    private void populateSpecForResource(
        Map<String, Set<String>> relations, Map<String, Object> spec, boolean migration) {
      Set<String> dependsOn = relations.get(DEPENDS_ON);
      Set<String> dependencyOf = relations.get(DEPENDENCY_OF);
      Set<String> partOf = relations.get(PART_OF);

      if (!isEmpty(partOf)) {
        List<String> systemRefs = new ArrayList<>();

        for (String part : partOf) {
          if (part.startsWith(SYSTEM_SPEC_RELATION_REF.toLowerCase())) {
            systemRefs.add(parseBackstageEntityRefFromCatalogRef(part, migration));
          }
        }

        if (!systemRefs.isEmpty()) {
          spec.put(SYSTEM_SPEC_RELATION_REF, systemRefs); // storing system as a list
        }
      }

      if (!isEmpty(dependsOn)) {
        spec.put(DEPENDS_ON,
            dependsOn.stream()
                .map(part -> CatalogUtils.parseBackstageEntityRefFromCatalogRef(part, migration))
                .collect(Collectors.toList()));
      }
      if (!isEmpty(dependencyOf)) {
        spec.put(DEPENDENCY_OF,
            dependencyOf.stream()
                .map(part -> CatalogUtils.parseBackstageEntityRefFromCatalogRef(part, migration))
                .collect(Collectors.toList()));
      }
    }

    private void populateSpecForApi(Map<String, Set<String>> relations, Map<String, Object> spec, boolean migration) {
      Set<String> partOf = relations.get(PART_OF);

      if (!isEmpty(partOf)) {
        List<String> systemRefs = new ArrayList<>();

        for (String part : partOf) {
          if (part.startsWith(SYSTEM_SPEC_RELATION_REF.toLowerCase())) {
            systemRefs.add(parseBackstageEntityRefFromCatalogRef(part, migration));
          }
        }

        if (!systemRefs.isEmpty()) {
          spec.put(SYSTEM_SPEC_RELATION_REF, systemRefs); // storing system as a list
        }
      }
    }

    public <T extends CatalogEntity> void harnessToIdpSync(
        List<T> catalogEntities, String accountIdentifier, String action) {
      List<Object> entities = new ArrayList<>();
      catalogEntities.forEach(catalogEntity -> {
        if (shouldCopyToBackstageCatalog(catalogEntity))
          entities.add(buildBackstageCatalog(catalogEntity, false, false, false));
      });
      if (entities.isEmpty())
        return;

      Map<String, List<Object>> actionOnEntities = new HashMap<>();
      actionOnEntities.put(action, entities);

      HarnessToIDPSyncRequest request = HarnessToIDPSyncRequest.builder().actionOnEntities(actionOnEntities).build();

      sendSyncRequestToIdpApp(accountIdentifier, request);
    }

    public <T extends CatalogEntity> void harnessToIdpSyncForMigration(
        String accountIdentifier, Map<String, List<Object>> actionOnEntities) {
      HarnessToIDPSyncRequest request = HarnessToIDPSyncRequest.builder().actionOnEntities(actionOnEntities).build();

      sendSyncRequestToIdpApp(accountIdentifier, request);
    }

    private void sendSyncRequestToIdpApp(String accountIdentifier, HarnessToIDPSyncRequest request) {
      try {
        Object response = getGeneralResponse(backstageResourceClient.harnessToIdpSync(accountIdentifier, request));
        Map<String, Integer> responseMap = (Map<String, Integer>) readValueForObject(response, Map.class);
        if (!responseMap.get(RESPONSE_STATUS).equals(200)) {
          log.error("HarnessToIDP entity sync failed with status {} and error {} for account={}",
              responseMap.get(RESPONSE_STATUS), responseMap.get("error"), accountIdentifier);
          throw new UnexpectedException("Internal error during entity operation");
        }
      } catch (Exception e) {
        log.error(
            "HarnessToIDP entity sync failed for account={} with error: {}", accountIdentifier, e.getMessage(), e);
        throw new UnexpectedException("Internal error during entity operation");
      }
    }

    private List<Map<String, Object>> populateRelations(
        CatalogEntity catalogEntity, Map<String, Set<String>> relations) {
      return relations.entrySet()
          .stream()
          .flatMap(entry
              -> entry.getValue()
                     .stream()
                     .map(value -> {
                       String entityRef = parseBackstageEntityRefFromCatalogRefWithoutUserManipulation(value, false);
                       if (entityRef != null) {
                         Triple<String, String, String> kindScopeIdentifier =
                             catalogServiceHelper.getKindScopeIdentifier(entityRef);
                         String kind = kindScopeIdentifier.getLeft();
                         if (entry.getKey().equalsIgnoreCase("ownedBy") && kind.equalsIgnoreCase("component")) {
                           kind = "group";
                         }
                         Map<String, Object> relation = new HashMap<>();
                         if (entry.getKey().equals(PROVIDES_API)) {
                           relation.put(TYPE, "providesApi");
                         } else if (entry.getKey().equals(CONSUMES_API)) {
                           relation.put(TYPE, "consumesApi");
                         } else if (entry.getKey().equals(PART_OF) && kind.equalsIgnoreCase(COMPONENT_KIND)
                             && !isEmpty(catalogEntity.getSpec())
                             && catalogEntity.getSpec().containsKey(SUB_COMPONENT_OF)) {
                           relation.put(TYPE, SUB_COMPONENT_OF);
                         } else {
                           relation.put(TYPE, entry.getKey());
                         }
                         relation.put(TARGET_REF,
                             kind + ":" + kindScopeIdentifier.getMiddle() + "/" + kindScopeIdentifier.getRight());

                         Map<String, String> target = new HashMap<>();
                         target.put(KIND, kind);
                         target.put(NAMESPACE, kindScopeIdentifier.getMiddle());
                         target.put(NAME, kindScopeIdentifier.getRight());
                         relation.put(TARGET, target);
                         return relation;
                       }
                       return null;
                     })
                     .filter(Objects::nonNull))
          .toList();
    }

    private void updateUserGroupSyncCompletedToTrue(NamespaceEntity namespaceEntity) {
      NamespaceEntity.Metadata metadata = Objects.isNull(namespaceEntity.getMetadata())
          ? NamespaceEntity.Metadata.builder().build()
          : namespaceEntity.getMetadata();
      metadata.setUserGroupSyncCompleted(true);
      namespaceEntity.setMetadata(metadata);
      namespaceService.save(namespaceEntity);
    }

    public static String convertToBackstageKind(String kind) {
      return switch (kind) {
      case "api" -> API;
      case "component" -> COMPONENT;
      case "workflow" -> TEMPLATE;
      case "user" -> USER;
      case "group" -> GROUP;
      case "resource" -> RESOURCE;
      case "system" -> SYSTEM;
      case "aiasset" -> AIASSET;
      default -> kind;
      };
  }
}

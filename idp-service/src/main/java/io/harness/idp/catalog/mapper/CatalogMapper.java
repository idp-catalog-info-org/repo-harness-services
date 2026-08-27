/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.AI_ASSET_KIND;
import static io.harness.idp.catalog.utils.Constants.ANNOTATIONS;
import static io.harness.idp.catalog.utils.Constants.API_KIND;
import static io.harness.idp.catalog.utils.Constants.CHILD_OF;
import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.idp.catalog.utils.Constants.DESCRIPTION;
import static io.harness.idp.catalog.utils.Constants.ENVIRONMENT_BLUEPRINT_KIND;
import static io.harness.idp.catalog.utils.Constants.ENVIRONMENT_KIND;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.HARNESS_API_VERSION;
import static io.harness.idp.catalog.utils.Constants.HAS_LEADER;
import static io.harness.idp.catalog.utils.Constants.HAS_MEMBER;
import static io.harness.idp.catalog.utils.Constants.HIERARCHY_KIND;
import static io.harness.idp.catalog.utils.Constants.IS_CUSTOM_USER_GROUP;
import static io.harness.idp.catalog.utils.Constants.KIND;
import static io.harness.idp.catalog.utils.Constants.LEADERS;
import static io.harness.idp.catalog.utils.Constants.LIFECYCLE;
import static io.harness.idp.catalog.utils.Constants.MEMBERS;
import static io.harness.idp.catalog.utils.Constants.METADATA;
import static io.harness.idp.catalog.utils.Constants.METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION;
import static io.harness.idp.catalog.utils.Constants.METADATA_TAGS;
import static io.harness.idp.catalog.utils.Constants.OWNED_BY;
import static io.harness.idp.catalog.utils.Constants.PARENT;
import static io.harness.idp.catalog.utils.Constants.REFERENCED_TYPES;
import static io.harness.idp.catalog.utils.Constants.RELATIONS;
import static io.harness.idp.catalog.utils.Constants.RELATION_REFS;
import static io.harness.idp.catalog.utils.Constants.RESOURCE_KIND;
import static io.harness.idp.catalog.utils.Constants.SOURCE_LOCATION_ANNOTATION;
import static io.harness.idp.catalog.utils.Constants.SPEC;
import static io.harness.idp.catalog.utils.Constants.SUB_COMPONENT_OF;
import static io.harness.idp.catalog.utils.Constants.SYSTEM_KIND;
import static io.harness.idp.catalog.utils.Constants.SYSTEM_SPEC_RELATION_REF;
import static io.harness.idp.catalog.utils.Constants.TAGS;
import static io.harness.idp.catalog.utils.Constants.TYPE;
import static io.harness.idp.catalog.utils.Constants.USER_KIND;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.idp.common.CommonUtils.from;
import static io.harness.idp.common.YamlUtils.writeObjectAsYaml;
import static io.harness.idp.common.YamlUtils.yamlObject;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.UnexpectedException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.JacksonUtils;
import io.harness.idp.common.YamlUtils;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityResponseScorecards;
import io.harness.spec.server.idp.v1.model.EntityResponseStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.yaml.snakeyaml.Yaml;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class CatalogMapper {
  public <T extends CatalogEntity> String presentationYaml(T catalogEntity) {
    return presentationYaml(catalogEntity, null);
  }

  public <T extends CatalogEntity> String presentationYaml(T catalogEntity, Set<String> groupingKindIdentifiers) {
    String catalogEntityYaml = writeObjectAsYaml(catalogEntity);
    Map<String, Object> catalogEntityMap = YamlUtils.loadYamlStringAsMap(catalogEntityYaml);

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
    catalogEntityMap.remove("entityType");
    catalogEntityMap.remove("storeType");
    catalogEntityMap.remove("repo");
    catalogEntityMap.remove("filePath");
    catalogEntityMap.remove("connectorRef");
    catalogEntityMap.remove("repoURL");
    catalogEntityMap.remove("fallBackBranch");
    catalogEntityMap.remove("data");
    catalogEntityMap.remove("decorator");

    catalogEntityMap.remove(DESCRIPTION);
    catalogEntityMap.remove(TAGS);
    catalogEntityMap.remove("sourceLocation");

    catalogEntityMap.remove(METADATA);
    catalogEntityMap.remove(RELATIONS);

    String kind = convertToHarnessNaming(catalogEntity.getKind());
    catalogEntityMap.put(KIND, kind);
    String type = catalogEntity.getType();
    if (!isEmpty(type)) {
      catalogEntityMap.put(TYPE, type.toLowerCase());
    }
    String description = catalogEntity.getDescription();
    List<String> tags = catalogEntity.getTags();
    String sourceLocation = catalogEntity.getSourceLocation();

    Map<String, Object> metadata =
        !isEmpty(catalogEntity.getMetadata()) ? new HashMap<>(catalogEntity.getMetadata()) : new HashMap<>();
    if (!isEmpty(description)) {
      metadata.put(DESCRIPTION, description);
    }
    if (!isEmpty(tags)) {
      metadata.put(TAGS, tags);
    }
    Map<String, Object> annotations = from(metadata, "annotations", Map.class);
    if (isEmpty(annotations)) {
      annotations = new HashMap<>();
    }
    if (!isEmpty(sourceLocation)) {
      annotations.put(SOURCE_LOCATION_ANNOTATION, sourceLocation);
    }
    metadata.remove(ANNOTATIONS);
    if (!isEmpty(annotations)) {
      metadata.put(ANNOTATIONS, annotations);
    }
    if (!isEmpty(metadata)) {
      catalogEntityMap.put(METADATA, metadata);
    }

    Map<String, Object> spec =
        !isEmpty(catalogEntity.getSpec()) ? new HashMap<>(catalogEntity.getSpec()) : new HashMap<>();
    if (!isEmpty(spec)) {
      spec.remove(IS_CUSTOM_USER_GROUP);
    }
    Map<String, Set<String>> relations =
        !isEmpty(catalogEntity.getRelations()) ? new HashMap<>(catalogEntity.getRelations()) : new HashMap<>();

    Map<String, List<String>> relationsAsList = new HashMap<>();
    if (relations != null) {
      for (Map.Entry<String, Set<String>> entry : relations.entrySet()) {
        String key = entry.getKey();
        Set<String> valueSet = entry.getValue();

        if ("partOf".equals(key)) {
          Set<String> allGroupingKindRefs = new HashSet<>();

          Object systemObj = spec.get("system");
          if (systemObj instanceof List) {
            @SuppressWarnings("unchecked") List<String> systemList = (List<String>) systemObj;
            allGroupingKindRefs.addAll(systemList);
          }

          if (!isEmpty(groupingKindIdentifiers)) {
            for (String groupingKindIdentifier : groupingKindIdentifiers) {
              Object groupingKindObj = spec.get(groupingKindIdentifier);
              if (groupingKindObj instanceof List) {
                @SuppressWarnings("unchecked") List<String> groupingKindList = (List<String>) groupingKindObj;
                allGroupingKindRefs.addAll(groupingKindList);
              }
            }
          }

          String subcomponentOfRef =
              spec.get(SUB_COMPONENT_OF) instanceof String ? (String) spec.get(SUB_COMPONENT_OF) : null;

          List<String> filtered =
              valueSet.stream()
                  .filter(val -> !allGroupingKindRefs.contains(val))
                  .filter(val
                      -> isEmpty(subcomponentOfRef)
                          || !(val.equals(subcomponentOfRef) || val.endsWith("/" + subcomponentOfRef)))
                  .collect(Collectors.toList());

          if (!filtered.isEmpty()) {
            relationsAsList.put(key, filtered);
          } else {
            spec.remove(key);
          }
        } else {
          relationsAsList.put(key, new ArrayList<>(valueSet));
        }
      }
    }

    if (!isEmpty(relationsAsList)) {
      relationsAsList.remove(OWNED_BY);
      relationsAsList.remove(HAS_MEMBER);
      relationsAsList.remove(CHILD_OF);
      relationsAsList.remove(HAS_LEADER);
      REFERENCED_TYPES.forEach(relationsAsList::remove);
      spec.putAll(relationsAsList);
    }
    catalogEntityMap.put(SPEC, spec);

    return yamlObject().dump(catalogEntityMap);
  }

  public <T extends CatalogEntity> T yamlToEntity(
      ScopeInfo scopeInfo, String identifier, String kind, String yaml, Map<String, Object> decorator) {
    return yamlToEntity(scopeInfo, identifier, kind, yaml, decorator, null);
  }

  public <T extends CatalogEntity> T yamlToEntity(ScopeInfo scopeInfo, String identifier, String kind, String yaml,
      Map<String, Object> decorator, Set<String> groupingKindIdentifiers) {
    Map<String, Object> entityYamlMap = YamlUtils.loadYamlStringAsMap(yaml);
    String name = from(entityYamlMap, "name", String.class);
    String description = from(entityYamlMap, "metadata.description", String.class);
    List<String> tags = from(entityYamlMap, METADATA_TAGS, List.class);
    Map<String, Object> metadata = from(entityYamlMap, "metadata", Map.class);
    metadata = !isEmpty(metadata) ? metadata : new HashMap<>();
    metadata.remove("description");
    metadata.remove("tags");
    // metadata.apis is system-managed (lives in the decorator); never persist it from submitted YAML.
    metadata.remove("apis");
    Map<String, Object> spec = from(entityYamlMap, "spec", Map.class);
    spec = !isEmpty(spec) ? spec : new HashMap<>();
    Map<String, Set<String>> relations = new HashMap<>();
    spec.remove("type");
    spec.remove("owner");

    if (spec.containsKey(SYSTEM_SPEC_RELATION_REF)) {
      Object value = spec.get(SYSTEM_SPEC_RELATION_REF);
      if (value instanceof String) {
        // Convert to List and update spec
        String systemStr = (String) value;
        List<String> systemList = List.of(systemStr);
        spec.put(SYSTEM_SPEC_RELATION_REF, systemList);
      }
    }

    if (!isEmpty(groupingKindIdentifiers)) {
      for (String groupingKindIdentifier : groupingKindIdentifiers) {
        if (spec.containsKey(groupingKindIdentifier)) {
          Object value = spec.get(groupingKindIdentifier);
          if (value instanceof String) {
            String groupingKindStr = (String) value;
            List<String> groupingKindList = List.of(groupingKindStr);
            spec.put(groupingKindIdentifier, groupingKindList);
          }
        }
      }
    }

    String owner = from(entityYamlMap, "owner", String.class);
    if (!isEmpty(owner)) {
      relations.put(OWNED_BY, Set.of(owner));
    }

    for (String relationRef : RELATION_REFS) {
      if (spec.containsKey(relationRef)) {
        if (relationRef.equals(SUB_COMPONENT_OF)) {
          String subcomponentOf = from(spec, relationRef, String.class);
          if (!isEmpty(subcomponentOf)) {
            Set<String> existingPartOf = relations.getOrDefault("partOf", new HashSet<>());
            Set<String> mergedPartOf = new HashSet<>(existingPartOf);
            mergedPartOf.add(subcomponentOf);
            relations.put("partOf", mergedPartOf);
          }
        } else if (relationRef.equals(SYSTEM_SPEC_RELATION_REF)) {
          Set<String> systems = from(spec, relationRef, Set.class);
          if (!isEmpty(systems)) {
            Set<String> existingPartOf = relations.getOrDefault("partOf", new HashSet<>());
            Set<String> mergedPartOf = new HashSet<>(existingPartOf);
            mergedPartOf.addAll(systems);
            relations.put("partOf", mergedPartOf);
          }
        } else if (relationRef.equals(MEMBERS)) {
          Set<String> members = from(spec, relationRef, Set.class);
          if (!isEmpty(members)) {
            relations.put(HAS_MEMBER, members);
          }
        } else if (relationRef.equals(PARENT)) {
          String parent = from(spec, relationRef, String.class);
          if (!isEmpty(parent)) {
            relations.put(CHILD_OF, Set.of(parent));
          }
        } else if (relationRef.equals(LEADERS)) {
          Set<String> leaders = from(spec, relationRef, Set.class);
          if (!isEmpty(leaders)) {
            relations.put(HAS_LEADER, leaders);
          }
        } else {
          relations.put(relationRef, from(spec, relationRef, Set.class));
        }
      }
    }

    if (!isEmpty(groupingKindIdentifiers)) {
      for (String groupingKindIdentifier : groupingKindIdentifiers) {
        if (spec.containsKey(groupingKindIdentifier)) {
          Set<String> groupingKindRefs = from(spec, groupingKindIdentifier, Set.class);
          if (!isEmpty(groupingKindRefs)) {
            Set<String> existingPartOf = relations.getOrDefault("partOf", new HashSet<>());
            Set<String> mergedPartOf = new HashSet<>(existingPartOf);
            mergedPartOf.addAll(groupingKindRefs);
            relations.put("partOf", mergedPartOf);
          }
        }
      }
    }

    CatalogEntity catalogEntity;
    if (!GitAwareContextHelper.isRemoteEntity()) {
      catalogEntity = new InlineCatalogEntity();
      catalogEntity.setReferenceType(ReferenceType.INLINE);
    } else {
      catalogEntity = new GitReferencedCatalogEntity();
      catalogEntity.setReferenceType(ReferenceType.GIT);
    }
    catalogEntity.setAccountIdentifier(scopeInfo.getAccountIdentifier());
    catalogEntity.setOrgIdentifier(from(entityYamlMap, "orgIdentifier", String.class));
    catalogEntity.setProjectIdentifier(from(entityYamlMap, "projectIdentifier", String.class));
    catalogEntity.setIdentifier(identifier);
    catalogEntity.setApiVersion(HARNESS_API_VERSION);
    catalogEntity.setKind(kind);
    catalogEntity.setType(from(entityYamlMap, "type", String.class));
    catalogEntity.setName(isEmpty(name) || name.trim().isEmpty() ? identifier : name);
    catalogEntity.setDescription(description);
    catalogEntity.setOwner(from(entityYamlMap, "owner", String.class));
    catalogEntity.setTags(tags);
    catalogEntity.setSourceLocation(
        from(entityYamlMap, METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION, String.class));
    catalogEntity.setSpec(spec);
    catalogEntity.setMetadata(metadata);
    catalogEntity.setRelations(relations);
    if (!isEmpty(decorator)) {
      catalogEntity.setDecorator(decorator);
    }
    catalogEntity.setYaml(CatalogMapper.presentationYaml(catalogEntity, groupingKindIdentifiers));
    catalogEntity.setUniqueId(UUIDGenerator.generateUuid());
    catalogEntity.setParentUniqueId(scopeInfo.getUniqueId());
    catalogEntity.setQueryableEntityRef(
        catalogEntity.getParentUniqueId() + "/" + catalogEntity.getKind() + "/" + catalogEntity.getIdentifier());
    return (T) catalogEntity;
  }

  public static EntityResponse entityToResponse(CatalogEntity catalogEntity, String orgName, String projectName,
      String userFavoriteEntityRefs, String kindIcon, EntityResponseScorecards scorecards,
      boolean resolvePlaceholders) {
    EntityResponse entityResponse = new EntityResponse();
    entityResponse.setIdentifier(catalogEntity.getIdentifier());
    entityResponse.setEntityRef(CatalogUtils.entityRef(catalogEntity));
    entityResponse.setOrgIdentifier(catalogEntity.getOrgIdentifier());
    entityResponse.setOrgName(orgName);
    entityResponse.setProjectIdentifier(catalogEntity.getProjectIdentifier());
    entityResponse.setProjectName(projectName);
    entityResponse.setScope(EntityResponse.ScopeEnum.valueOf(catalogEntity.getScope()));
    entityResponse.setReferenceType(EntityResponse.ReferenceTypeEnum.valueOf(catalogEntity.getReferenceType().name()));
    if (Arrays.stream(EntityResponse.KindEnum.values())
            .anyMatch(kind -> kind.value().toLowerCase().equals(catalogEntity.getKind()))) {
      entityResponse.setKind(EntityResponse.KindEnum.fromValue(catalogEntity.getKind()));
    }
    entityResponse.setKindIdentifier(catalogEntity.getKind());
    entityResponse.setKindIcon(kindIcon);
    entityResponse.setType(catalogEntity.getType());
    entityResponse.setName(catalogEntity.getName());
    entityResponse.setDescription(catalogEntity.getDescription());
    entityResponse.setOwner(catalogEntity.getOwner());
    entityResponse.setTags(catalogEntity.getTags());
    entityResponse.setLifecycle(from(catalogEntity.getSpec(), LIFECYCLE, String.class));
    entityResponse.setMetadata(catalogEntity.getDecoratedMetadata());
    entityResponse.setSpec(catalogEntity.getSpec());
    entityResponse.setRelations(catalogEntity.getRelations());
    entityResponse.setCreated(catalogEntity.getCreatedAt());
    entityResponse.setUpdated(catalogEntity.getLastUpdatedAt());
    Map<String, Object> processedData = catalogEntity.getFailSafeProcessedData();
    if (!isEmpty(processedData)) {
      entityResponse.setDecorator(writeObjectAsYaml(processedData));
    }
    if (catalogEntity instanceof GitReferencedCatalogEntity) {
      entityResponse.setGitDetails(IDPGitXMapper.getEntityGitDetails());
      entityResponse.setCacheResponseData(IDPGitXMapper.getCacheResponseFromGitContext());
    }
    if (scorecards != null) {
      entityResponse.setScorecards(scorecards);
    }
    if (resolvePlaceholders) {
      try {
        Object yamlNode = new Yaml().load(catalogEntity.getYaml());
        Object mergedYaml = mergeYamlWithPlaceholders(yamlNode, catalogEntity.getDecorator());
        catalogEntity.setYaml(yamlObject().dump(mergedYaml));
      } catch (Exception ignored) {
      }
    }
    entityResponse.setYaml(catalogEntity.getYaml());

    if (!isEmpty(userFavoriteEntityRefs)) {
      boolean starred = Arrays.stream(userFavoriteEntityRefs.split(","))
                            .map(String::trim)
                            .anyMatch(entityRef -> entityRef.equals(CatalogUtils.entityRef(catalogEntity)));
      entityResponse.setStarred(starred);
    }

    List<EntityResponseStatus> entityResponseStatuses = new ArrayList<>();
    List<Map<String, String>> statuses =
        !isEmpty(catalogEntity.getStatus()) ? catalogEntity.getStatus() : new ArrayList<>();
    statuses.forEach(status -> {
      EntityResponseStatus entityResponseStatus = new EntityResponseStatus();
      entityResponseStatus.setType(status.get("type"));
      entityResponseStatus.setLevel(status.get("level"));
      entityResponseStatus.setMessage(status.get("message"));
      entityResponseStatuses.add(entityResponseStatus);
    });
    entityResponse.setStatus(entityResponseStatuses);

    return entityResponse;
  }

  @SuppressWarnings("unchecked")
  public static Object mergeYamlWithPlaceholders(Object yamlNode, Map<String, Object> decoratorNode) {
    if (!(yamlNode instanceof Map<?, ?> yamlMap) || decoratorNode == null) {
      return yamlNode;
    }

    Map<String, Object> result = new LinkedHashMap<>();

    for (Map.Entry<?, ?> entry : yamlMap.entrySet()) {
      result.put(entry.getKey().toString(), entry.getValue());
    }

    for (String placeholder : Arrays.asList("$yaml", "$json", "$text")) {
      if (yamlMap.containsKey(placeholder) && decoratorNode.containsKey(placeholder)) {
        Object replacement = decoratorNode.get(placeholder);
        Object resolvedContent;

        switch (placeholder) {
          case "$yaml":
            resolvedContent = new Yaml().load(replacement.toString());
            break;
          case "$json":
            try {
              resolvedContent = new Yaml().load(JacksonUtils.JSON_MAPPER.readTree(replacement.toString()).toString());
              break;
            } catch (JsonProcessingException ex) {
              throw new UnexpectedException(ex.getMessage());
            }
          case "$text":
          default:
            resolvedContent = replacement;
        }

        if (resolvedContent instanceof Map<?, ?> resolvedMap) {
          Map<String, Object> mergedMap = new LinkedHashMap<>(result);
          mergedMap.putAll((Map<String, Object>) resolvedMap);
          mergedMap.remove(placeholder);
          result = mergedMap;
        } else {
          result.put(placeholder, resolvedContent);
        }
      }
    }

    for (Map.Entry<String, Object> entry : new LinkedHashMap<>(result).entrySet()) {
      Object value = entry.getValue();
      Map<String, Object> decoratorChild =
          decoratorNode.get(entry.getKey()) instanceof Map<?, ?> d ? (Map<String, Object>) d : null;
      if (value instanceof Map<?, ?>) {
        result.put(entry.getKey(), mergeYamlWithPlaceholders(value, decoratorChild));
      }
    }

    return result;
  }

  public static Object populateIsCustomUserGroupInSpec(Object spec, String kind) {
    if (GROUP_KIND.equals(kind)) {
      @SuppressWarnings("unchecked")
      Map<String, Object> convertedSpec = (spec != null) ? (Map<String, Object>) spec : new HashMap<>();
      convertedSpec.put(IS_CUSTOM_USER_GROUP, true);
      return convertedSpec;
    }
    return spec;
  }

  private static String convertToHarnessNaming(String kind) {
    return switch (kind) {
      case API_KIND -> "API";
      case COMPONENT_KIND -> "Component";
      case SYSTEM_KIND -> "System";
      case RESOURCE_KIND -> "Resource";
      case USER_KIND -> "User";
      case GROUP_KIND -> "Group";
      case WORKFLOW_KIND -> "Workflow";
      case ENVIRONMENT_BLUEPRINT_KIND -> "EnvironmentBlueprint";
      case ENVIRONMENT_KIND -> "Environment";
      case HIERARCHY_KIND -> "Hierarchy";
      case AI_ASSET_KIND -> "AIAsset";
      default -> kind;
    };
  }
}

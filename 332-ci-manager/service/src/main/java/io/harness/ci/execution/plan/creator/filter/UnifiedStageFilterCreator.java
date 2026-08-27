/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.filter;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cimanager.stages.V1.UnifiedStageNodeV1;
import io.harness.encryption.Scope;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.InfraDefinitionReferenceProtoDTO;
import io.harness.filters.v1.GenericStageFilterJsonCreatorV3;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CI)
@Slf4j
public class UnifiedStageFilterCreator extends GenericStageFilterJsonCreatorV3<UnifiedStageNodeV1> {
  @Override
  public Set<String> getSupportedStageTypes() {
    return new HashSet<>(Arrays.asList(YAMLFieldNameConstants.UNIFIED));
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, UnifiedStageNodeV1 stageNode) {
    return CIFilter.builder().build();
  }

  @Override
  public Class<UnifiedStageNodeV1> getFieldClass() {
    return UnifiedStageNodeV1.class;
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, UnifiedStageNodeV1 stageNode) {
    return FilterCreationResponse.builder()
        .referredEntities(extractReferredEntities(filterCreationContext, stageNode))
        .build();
  }

  /**
   * Extracts referred entities (services, environments, infrastructures) from unified stage.
   * Supports both inline format and items format.
   */
  List<EntityDetailProtoDTO> extractReferredEntities(
      FilterCreationContext filterCreationContext, UnifiedStageNodeV1 stageNode) {
    String accountId = filterCreationContext.getSetupMetadata().getAccountId();
    String orgId = filterCreationContext.getSetupMetadata().getOrgId();
    String projectId = filterCreationContext.getSetupMetadata().getProjectId();

    List<EntityDetailProtoDTO> refs = new ArrayList<>();

    // Extract service references
    extractServiceReferences(stageNode, accountId, orgId, projectId, refs);

    // Extract environment and infrastructure references
    extractEnvironmentReferences(stageNode, accountId, orgId, projectId, refs);

    return refs;
  }

  /**
   * Extracts service references from stageNode.
   * Handles both inline service (string or object with id) and items format (list of services).
   */
  private void extractServiceReferences(
      UnifiedStageNodeV1 stageNode, String accountId, String orgId, String projectId, List<EntityDetailProtoDTO> refs) {
    if (stageNode.getService() == null || !ParameterField.isNotNull(stageNode.getService())) {
      return;
    }

    Object serviceValue = stageNode.getService().getValue();
    if (serviceValue == null) {
      return;
    }

    try {
      // Handle inline service (string identifier)
      if (serviceValue instanceof String) {
        String serviceId = (String) serviceValue;
        if (isNotEmpty(serviceId)) {
          refs.add(createEntityRef(accountId, orgId, projectId, serviceId, EntityTypeProtoEnum.SERVICE, null));
        }
      }
      // Handle service as Map (object with id field or items array)
      else if (serviceValue instanceof Map) {
        Map<String, Object> serviceMap = (Map<String, Object>) serviceValue;

        // Check for items format (multi-service)
        if (serviceMap.containsKey(YAMLFieldNameConstants.ITEMS)) {
          Object itemsObj = serviceMap.get(YAMLFieldNameConstants.ITEMS);
          if (itemsObj instanceof List) {
            List<?> items = (List<?>) itemsObj;
            for (Object item : items) {
              extractServiceItem(item, accountId, orgId, projectId, refs);
            }
          }
        }
        // Check for inline service with id field
        else if (serviceMap.containsKey(YAMLFieldNameConstants.ID)) {
          Object idObj = serviceMap.get(YAMLFieldNameConstants.ID);
          if (idObj instanceof String && isNotEmpty((String) idObj)) {
            refs.add(createEntityRef(accountId, orgId, projectId, (String) idObj, EntityTypeProtoEnum.SERVICE, null));
          }
        }
      }
      // Handle JsonNode (for runtime resolution)
      else if (serviceValue instanceof JsonNode) {
        JsonNode serviceNode = (JsonNode) serviceValue;
        if (serviceNode.isTextual() && isNotEmpty(serviceNode.asText())) {
          refs.add(
              createEntityRef(accountId, orgId, projectId, serviceNode.asText(), EntityTypeProtoEnum.SERVICE, null));
        } else if (serviceNode.isObject()) {
          if (serviceNode.has(YAMLFieldNameConstants.ITEMS)) {
            JsonNode itemsNode = serviceNode.get(YAMLFieldNameConstants.ITEMS);
            if (itemsNode.isArray()) {
              for (JsonNode item : itemsNode) {
                extractServiceItemFromJsonNode(item, accountId, orgId, projectId, refs);
              }
            }
          } else if (serviceNode.has(YAMLFieldNameConstants.ID)) {
            JsonNode idNode = serviceNode.get(YAMLFieldNameConstants.ID);
            if (idNode.isTextual() && isNotEmpty(idNode.asText())) {
              refs.add(
                  createEntityRef(accountId, orgId, projectId, idNode.asText(), EntityTypeProtoEnum.SERVICE, null));
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("Could not extract service entity refs for unified V1 stage: {}", e.getMessage());
    }
  }

  /**
   * Extracts environment and infrastructure references from stageNode.
   * Handles inline environment and items format.
   */
  private void extractEnvironmentReferences(
      UnifiedStageNodeV1 stageNode, String accountId, String orgId, String projectId, List<EntityDetailProtoDTO> refs) {
    if (stageNode.getEnvironment() == null || !ParameterField.isNotNull(stageNode.getEnvironment())) {
      return;
    }

    Object envValue = stageNode.getEnvironment().getValue();
    if (envValue == null) {
      return;
    }

    try {
      if (envValue instanceof Map) {
        Map<String, Object> envMap = (Map<String, Object>) envValue;

        // Check for items format (multi-environment)
        if (envMap.containsKey(YAMLFieldNameConstants.ITEMS)) {
          Object itemsObj = envMap.get(YAMLFieldNameConstants.ITEMS);
          if (itemsObj instanceof List) {
            List<?> items = (List<?>) itemsObj;
            for (Object item : items) {
              extractEnvironmentItem(item, accountId, orgId, projectId, refs);
            }
          }
        }
        // Handle inline environment
        else if (envMap.containsKey(YAMLFieldNameConstants.ID)) {
          Object idObj = envMap.get(YAMLFieldNameConstants.ID);
          String envIdentifier = null;
          if (idObj instanceof String && isNotEmpty((String) idObj)) {
            envIdentifier = (String) idObj;
            refs.add(
                createEntityRef(accountId, orgId, projectId, envIdentifier, EntityTypeProtoEnum.ENVIRONMENT, null));
          }

          // Extract infrastructure from deploy-to with env context
          if (envMap.containsKey(YAMLFieldNameConstants.DEPLOY_TO) && envIdentifier != null) {
            extractInfrastructureFromDeployTo(
                envMap.get(YAMLFieldNameConstants.DEPLOY_TO), accountId, orgId, projectId, envIdentifier, refs);
          }
        }
      } else if (envValue instanceof JsonNode) {
        JsonNode envNode = (JsonNode) envValue;
        if (envNode.isObject()) {
          if (envNode.has(YAMLFieldNameConstants.ITEMS)) {
            JsonNode itemsNode = envNode.get(YAMLFieldNameConstants.ITEMS);
            if (itemsNode.isArray()) {
              for (JsonNode item : itemsNode) {
                extractEnvironmentItemFromJsonNode(item, accountId, orgId, projectId, refs);
              }
            }
          } else if (envNode.has(YAMLFieldNameConstants.ID)) {
            JsonNode idNode = envNode.get(YAMLFieldNameConstants.ID);
            String envIdentifier = null;
            if (idNode.isTextual() && isNotEmpty(idNode.asText())) {
              envIdentifier = idNode.asText();
              refs.add(
                  createEntityRef(accountId, orgId, projectId, envIdentifier, EntityTypeProtoEnum.ENVIRONMENT, null));
            }

            if (envNode.has(YAMLFieldNameConstants.DEPLOY_TO) && envIdentifier != null) {
              extractInfrastructureFromDeployToJsonNode(
                  envNode.get(YAMLFieldNameConstants.DEPLOY_TO), accountId, orgId, projectId, envIdentifier, refs);
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("Could not extract environment entity refs for unified V1 stage: {}", e.getMessage());
    }
  }

  private void extractServiceItem(
      Object item, String accountId, String orgId, String projectId, List<EntityDetailProtoDTO> refs) {
    if (item instanceof String && isNotEmpty((String) item)) {
      refs.add(createEntityRef(accountId, orgId, projectId, (String) item, EntityTypeProtoEnum.SERVICE, null));
    } else if (item instanceof Map) {
      Map<String, Object> itemMap = (Map<String, Object>) item;
      if (itemMap.containsKey(YAMLFieldNameConstants.ID)) {
        Object idObj = itemMap.get(YAMLFieldNameConstants.ID);
        if (idObj instanceof String && isNotEmpty((String) idObj)) {
          refs.add(createEntityRef(accountId, orgId, projectId, (String) idObj, EntityTypeProtoEnum.SERVICE, null));
        }
      }
    }
  }

  private void extractServiceItemFromJsonNode(
      JsonNode item, String accountId, String orgId, String projectId, List<EntityDetailProtoDTO> refs) {
    if (item.isTextual() && isNotEmpty(item.asText())) {
      refs.add(createEntityRef(accountId, orgId, projectId, item.asText(), EntityTypeProtoEnum.SERVICE, null));
    } else if (item.isObject() && item.has(YAMLFieldNameConstants.ID)) {
      JsonNode idNode = item.get(YAMLFieldNameConstants.ID);
      if (idNode.isTextual() && isNotEmpty(idNode.asText())) {
        refs.add(createEntityRef(accountId, orgId, projectId, idNode.asText(), EntityTypeProtoEnum.SERVICE, null));
      }
    }
  }

  private void extractEnvironmentItem(
      Object item, String accountId, String orgId, String projectId, List<EntityDetailProtoDTO> refs) {
    if (item instanceof Map) {
      Map<String, Object> itemMap = (Map<String, Object>) item;
      String envIdentifier = null;
      if (itemMap.containsKey(YAMLFieldNameConstants.ID)) {
        Object idObj = itemMap.get(YAMLFieldNameConstants.ID);
        if (idObj instanceof String && isNotEmpty((String) idObj)) {
          envIdentifier = (String) idObj;
          refs.add(createEntityRef(accountId, orgId, projectId, envIdentifier, EntityTypeProtoEnum.ENVIRONMENT, null));
        }
      }

      if (itemMap.containsKey(YAMLFieldNameConstants.DEPLOY_TO) && envIdentifier != null) {
        extractInfrastructureFromDeployTo(
            itemMap.get(YAMLFieldNameConstants.DEPLOY_TO), accountId, orgId, projectId, envIdentifier, refs);
      }
    }
  }

  private void extractEnvironmentItemFromJsonNode(
      JsonNode item, String accountId, String orgId, String projectId, List<EntityDetailProtoDTO> refs) {
    if (item.isObject() && item.has(YAMLFieldNameConstants.ID)) {
      JsonNode idNode = item.get(YAMLFieldNameConstants.ID);
      String envIdentifier = null;
      if (idNode.isTextual() && isNotEmpty(idNode.asText())) {
        envIdentifier = idNode.asText();
        refs.add(createEntityRef(accountId, orgId, projectId, envIdentifier, EntityTypeProtoEnum.ENVIRONMENT, null));
      }

      if (item.has(YAMLFieldNameConstants.DEPLOY_TO) && envIdentifier != null) {
        extractInfrastructureFromDeployToJsonNode(
            item.get(YAMLFieldNameConstants.DEPLOY_TO), accountId, orgId, projectId, envIdentifier, refs);
      }
    }
  }

  private void extractInfrastructureFromDeployTo(Object deployToValue, String accountId, String orgId, String projectId,
      String envIdentifier, List<EntityDetailProtoDTO> refs) {
    // deploy-to can be a string (single infra), array (multiple infras), or object (with items/filters)
    if (deployToValue instanceof String && isNotEmpty((String) deployToValue)) {
      refs.add(createEntityRef(
          accountId, orgId, projectId, (String) deployToValue, EntityTypeProtoEnum.INFRASTRUCTURE, envIdentifier));
    } else if (deployToValue instanceof List) {
      List<?> infraList = (List<?>) deployToValue;
      for (Object infra : infraList) {
        if (infra instanceof String && isNotEmpty((String) infra)) {
          refs.add(createEntityRef(
              accountId, orgId, projectId, (String) infra, EntityTypeProtoEnum.INFRASTRUCTURE, envIdentifier));
        }
      }
    }
  }

  private void extractInfrastructureFromDeployToJsonNode(JsonNode deployToNode, String accountId, String orgId,
      String projectId, String envIdentifier, List<EntityDetailProtoDTO> refs) {
    if (deployToNode.isTextual() && isNotEmpty(deployToNode.asText())) {
      refs.add(createEntityRef(
          accountId, orgId, projectId, deployToNode.asText(), EntityTypeProtoEnum.INFRASTRUCTURE, envIdentifier));
    } else if (deployToNode.isArray()) {
      for (JsonNode infraNode : deployToNode) {
        if (infraNode.isTextual() && isNotEmpty(infraNode.asText())) {
          refs.add(createEntityRef(
              accountId, orgId, projectId, infraNode.asText(), EntityTypeProtoEnum.INFRASTRUCTURE, envIdentifier));
        }
      }
    }
  }

  private EntityDetailProtoDTO createEntityRef(String accountId, String orgId, String projectId, String identifier,
      EntityTypeProtoEnum entityType, String envIdentifier) {
    // For infrastructure entities, derive scope from the environment identifier, not the infrastructure identifier
    // Infrastructure identifiers are always plain (no scope prefix), and their scope matches the environment's scope
    if (entityType == EntityTypeProtoEnum.INFRASTRUCTURE && envIdentifier != null) {
      // Parse envIdentifier to extract scope and actual identifier
      IdentifierRef envRef;
      try {
        envRef = IdentifierRefHelper.getIdentifierRef(envIdentifier, accountId, orgId, projectId);
      } catch (Exception e) {
        log.warn("Failed to parse env identifier reference '{}': {}. Falling back to project scope.", envIdentifier,
            e.getMessage());
        // Fallback to project scope if parsing fails
        envRef = IdentifierRef.builder()
                     .accountIdentifier(accountId)
                     .orgIdentifier(orgId)
                     .projectIdentifier(projectId)
                     .identifier(envIdentifier)
                     .scope(Scope.PROJECT)
                     .build();
      }

      // Extract scope from environment (infrastructure inherits environment's scope)
      String scopedAccountId = envRef.getAccountIdentifier();
      String scopedOrgId = null;
      String scopedProjectId = null;

      if (envRef.getScope() == Scope.ORG || envRef.getScope() == Scope.PROJECT) {
        scopedOrgId = envRef.getOrgIdentifier();
      }

      if (envRef.getScope() == Scope.PROJECT) {
        scopedProjectId = envRef.getProjectIdentifier();
      }

      // Infrastructure identifier is always plain (no scope prefix)
      // envIdentifier should be the actual identifier without scope prefix
      return EntityDetailProtoDTO.newBuilder()
          .setInfraDefRef(InfraDefinitionReferenceProtoDTO.newBuilder()
                              .setAccountIdentifier(StringValue.of(scopedAccountId))
                              .setOrgIdentifier(StringValue.of(defaultIfBlank(scopedOrgId, "")))
                              .setProjectIdentifier(StringValue.of(defaultIfBlank(scopedProjectId, "")))
                              .setIdentifier(StringValue.of(identifier))
                              .setEnvIdentifier(StringValue.of(envRef.getIdentifier()))
                              .build())
          .setType(entityType)
          .build();
    }

    // For non-infrastructure entities (service, environment), parse the identifier normally
    IdentifierRef identifierRef;
    try {
      identifierRef = IdentifierRefHelper.getIdentifierRef(identifier, accountId, orgId, projectId);
    } catch (Exception e) {
      log.warn(
          "Failed to parse identifier reference '{}': {}. Falling back to project scope.", identifier, e.getMessage());
      // Fallback to project scope if parsing fails
      identifierRef = IdentifierRef.builder()
                          .accountIdentifier(accountId)
                          .orgIdentifier(orgId)
                          .projectIdentifier(projectId)
                          .identifier(identifier)
                          .scope(Scope.PROJECT)
                          .build();
    }

    // Extract scope-specific identifiers based on the parsed scope
    String scopedAccountId = identifierRef.getAccountIdentifier();
    String scopedOrgId = null;
    String scopedProjectId = null;

    if (identifierRef.getScope() == Scope.ORG || identifierRef.getScope() == Scope.PROJECT) {
      scopedOrgId = identifierRef.getOrgIdentifier();
    }

    if (identifierRef.getScope() == Scope.PROJECT) {
      scopedProjectId = identifierRef.getProjectIdentifier();
    }

    String actualIdentifier = identifierRef.getIdentifier();

    // For other entity types (service, environment), use standard IdentifierRefProtoDTO
    return EntityDetailProtoDTO.newBuilder()
        .setIdentifierRef(IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
            scopedAccountId, scopedOrgId, scopedProjectId, actualIdentifier))
        .setType(entityType)
        .build();
  }
}

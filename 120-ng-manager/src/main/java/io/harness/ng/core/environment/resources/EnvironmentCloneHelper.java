/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.common.EntityTypeConstants.INFRASTRUCTURE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.gitsync.beans.StoreType.INLINE;
import static io.harness.gitsync.beans.StoreType.REMOTE;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;

import static java.lang.String.format;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitx.EntityGitDetailsGuard;
import io.harness.gitx.GitXTransientBranchGuard;
import io.harness.jackson.JsonNodeUtils;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentCloneResponse;
import io.harness.ng.core.environment.beans.EnvironmentGovernanceDataResponse;
import io.harness.ng.core.environment.dto.DestinationEnvironmentConfig;
import io.harness.ng.core.environment.dto.SourceEnvironmentConfig;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureGovernanceDataResponse;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.telemetry.entity.SvcEnvCloneTelemetryInfo;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.telemetry.helpers.SvcEnvCloneInstrumentationHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Slf4j
public class EnvironmentCloneHelper {
  @Inject private EnvironmentService environmentService;
  @Inject @Named("NON_PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private SvcEnvCloneInstrumentationHelper instrumentationHelper;
  @Inject private ScopeInfoService scopeInfoService;
  private ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
  private YAMLMapper yamlMapper = new YAMLMapper();

  public EnvironmentCloneResponse cloneEnvironment(String accountId, SourceEnvironmentConfig sourceEnvConfig,
      DestinationEnvironmentConfig destinationEnvConfig, boolean cloneInfrastructures) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, sourceEnvConfig.getOrgIdentifier(), sourceEnvConfig.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, sourceEnvConfig.getEnvIdentifier()), ENVIRONMENT_VIEW_PERMISSION);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        destinationEnvConfig.getOrgIdentifier(), destinationEnvConfig.getProjectIdentifier(), accountId);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        accountId, sourceEnvConfig.getOrgIdentifier(), sourceEnvConfig.getProjectIdentifier());
    ScopeInfo destinationScopeInfo;

    boolean isSameScope = Objects.equals(sourceEnvConfig.getOrgIdentifier(), destinationEnvConfig.getOrgIdentifier())
        && Objects.equals(sourceEnvConfig.getProjectIdentifier(), destinationEnvConfig.getProjectIdentifier());

    if (isSameScope) {
      destinationScopeInfo = scopeInfo;
    } else {
      destinationScopeInfo = scopeInfoService.getScopeInfo(
          accountId, destinationEnvConfig.getOrgIdentifier(), destinationEnvConfig.getProjectIdentifier());
    }

    Optional<Environment> optionalEnvironment;
    try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(sourceEnvConfig.getBranch())) {
      optionalEnvironment = environmentService.get(scopeInfo, sourceEnvConfig.getEnvIdentifier(), false, true, false);
    }

    Environment environment = null;
    if (optionalEnvironment.isPresent()) {
      environment = optionalEnvironment.get();
    } else {
      throw new NotFoundException(format("Environment with identifier [%s] in project [%s], org [%s] not found",
          sourceEnvConfig.getEnvIdentifier(), sourceEnvConfig.getProjectIdentifier(),
          sourceEnvConfig.getOrgIdentifier()));
    }

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, destinationEnvConfig.getOrgIdentifier(),
                                                  destinationEnvConfig.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, null, getEnvironmentAttributesMap(environment.getType().toString())),
        ENVIRONMENT_CREATE_PERMISSION);

    String sourceEnvStoreType = INLINE.toString();
    if (REMOTE.equals(environment.getStoreType())) {
      sourceEnvStoreType = REMOTE.toString();
    }
    String sourceEnvYaml = environment.getYaml(scopeInfo);
    JsonNode jsonNode;
    try {
      jsonNode = objectMapper.readTree(sourceEnvYaml);
    } catch (JsonProcessingException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check that the yaml of the source environment is valid",
          "The yaml of the source environment is invalid",
          new InvalidRequestException(
              format("An error occurred while cloning yaml of environment [%s] in project [%s], org [%s]",
                  environment.getIdentifier(), scopeInfo.getProjectIdentifier(), scopeInfo.getOrgIdentifier()),
              e));
    }

    if (isEmpty(destinationEnvConfig.getProjectIdentifier())) {
      JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) jsonNode.get("environment"), "projectIdentifier");
    } else if (!(destinationEnvConfig.getProjectIdentifier()).equals(environment.getProjectIdentifier())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("environment"), "projectIdentifier", destinationEnvConfig.getProjectIdentifier());
    }

    if (isEmpty(destinationEnvConfig.getOrgIdentifier())) {
      JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) jsonNode.get("environment"), "orgIdentifier");
    } else if (!(destinationEnvConfig.getOrgIdentifier()).equals(scopeInfo.getOrgIdentifier())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("environment"), "orgIdentifier", destinationEnvConfig.getOrgIdentifier());
    }
    if (destinationEnvConfig.getEnvIdentifier() != null
        && !destinationEnvConfig.getEnvIdentifier().equals(environment.getIdentifier())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("environment"), "identifier", destinationEnvConfig.getEnvIdentifier());
    }
    if (destinationEnvConfig.getDescription() != null
        && !destinationEnvConfig.getDescription().equals(environment.getDescription())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("environment"), "description", destinationEnvConfig.getDescription());
    }
    if (destinationEnvConfig.getTags() != null) {
      Map<String, String> tags = destinationEnvConfig.getTags();
      ObjectMapper jsonMapper = new ObjectMapper();
      ((ObjectNode) jsonNode.get("environment")).set("tags", jsonMapper.convertValue(tags, JsonNode.class));
    }
    if (destinationEnvConfig.getEnvName() != null && !destinationEnvConfig.getEnvName().equals(environment.getName())) {
      JsonNodeUtils.updatePropertyInObjectNode(jsonNode.get("environment"), "name", destinationEnvConfig.getEnvName());
    }

    String modifiedSourceYaml;
    try {
      modifiedSourceYaml = yamlMapper.writeValueAsString(jsonNode);
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check that the yaml of the source environment is valid",
          "The yaml of the cloned environment is invalid",
          new InvalidRequestException(
              format("An error occurred while cloning yaml of environment [%s] in project [%s], org [%s]",
                  environment.getIdentifier(), scopeInfo.getProjectIdentifier(), scopeInfo.getOrgIdentifier()),
              e));
    }

    environment.setId(null);
    environment.setVersion(null);
    environment.setOrgIdentifier(destinationEnvConfig.getOrgIdentifier());
    environment.setProjectIdentifier(destinationEnvConfig.getProjectIdentifier());
    if (destinationEnvConfig.getDescription() != null) {
      environment.setDescription(destinationEnvConfig.getDescription());
    }
    if (destinationEnvConfig.getTags() != null) {
      environment.setTags(convertToList(destinationEnvConfig.getTags()));
    }
    environment.setName(destinationEnvConfig.getEnvName());
    environment.setIdentifier(destinationEnvConfig.getEnvIdentifier());
    environment.setYaml(modifiedSourceYaml);

    environment.setConnectorRef(null);
    environment.setFallBackBranch(null);
    environment.setRepo(null);
    environment.setRepoURL(null);
    environment.setFilePath(null);
    environment.setUniqueId(null);
    environment.setParentUniqueId(null);

    EnvironmentGovernanceDataResponse clonedEnvironmentMapper;
    if (REMOTE.equals(destinationEnvConfig.getStoreType())) {
      GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                        .branch(destinationEnvConfig.getBranch())
                                        .filePath(destinationEnvConfig.getFilePath())
                                        .commitMsg(destinationEnvConfig.getCommitMessage())
                                        .isNewBranch(isNotEmpty(destinationEnvConfig.getBranch())
                                            && isNotEmpty(destinationEnvConfig.getBaseBranch()))
                                        .baseBranch(destinationEnvConfig.getBaseBranch())
                                        .connectorRef(destinationEnvConfig.getConnectorRef())
                                        .isHarnessCodeRepo(destinationEnvConfig.isHarnessCodeRepo())
                                        .storeType(REMOTE)
                                        .repoName(destinationEnvConfig.getRepoName())
                                        .build();
      try (EntityGitDetailsGuard entityGitDetailsGuard = new EntityGitDetailsGuard(gitEntityInfo)) {
        if (destinationScopeInfo == null) {
          destinationScopeInfo = scopeInfoService.getScopeInfo(
              accountId, destinationEnvConfig.getOrgIdentifier(), destinationEnvConfig.getProjectIdentifier());
        }
        clonedEnvironmentMapper = environmentService.create(environment, destinationScopeInfo);
      }
    } else {
      if (destinationScopeInfo == null) {
        destinationScopeInfo = scopeInfoService.getScopeInfo(environment.getAccountIdentifier(),
            destinationEnvConfig.getOrgIdentifier(), destinationEnvConfig.getProjectIdentifier());
      }
      clonedEnvironmentMapper = environmentService.create(environment, destinationScopeInfo);
    }

    // clone corresponding infrastructures
    List<InfrastructureEntity> clonedInfrastructureEntities = new ArrayList<>();
    List<String> cloneFailedInfrastructures = new ArrayList<>();
    if (cloneInfrastructures) {
      scopeInfo = scopeInfoService.getScopeInfo(
          accountId, sourceEnvConfig.getOrgIdentifier(), sourceEnvConfig.getProjectIdentifier());
      List<InfrastructureEntity> infraEntitiesMetadata =
          infrastructureEntityService.getAllInfrastructureMetadataFromEnvRef(accountId,
              sourceEnvConfig.getOrgIdentifier(), sourceEnvConfig.getProjectIdentifier(), scopeInfo,
              sourceEnvConfig.getEnvIdentifier());
      GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                   .parentEntityRepoName(environment.getRepo())
                                                   .branch(sourceEnvConfig.getBranch())
                                                   .build());
      for (InfrastructureEntity infrastructureEntityMetadata : infraEntitiesMetadata) {
        try {
          InfrastructureEntity clonedInfrastructureEntity = cloneInfrastructure(
              accountId, infrastructureEntityMetadata.getIdentifier(), sourceEnvConfig, destinationEnvConfig, scopeInfo)
                                                                .getInfrastructureEntity();
          clonedInfrastructureEntities.add(clonedInfrastructureEntity);
        } catch (Exception e) {
          log.error(format("An error occurred while cloning of infrastructure with identifier [%s], env identifier "
                            + "[%s] in project [%s], org [%s]",
                        infrastructureEntityMetadata.getIdentifier(), sourceEnvConfig.getEnvIdentifier(),
                        sourceEnvConfig.getProjectIdentifier(), sourceEnvConfig.getOrgIdentifier()),
              e);
          cloneFailedInfrastructures.add(infrastructureEntityMetadata.getIdentifier());
        }
      }
    }

    publishEnvCloneTelemetryData(
        accountId, sourceEnvConfig, destinationEnvConfig, cloneInfrastructures, sourceEnvStoreType);

    return EnvironmentCloneResponse.builder()
        .environment(clonedEnvironmentMapper.getEnvironment())
        .infrastructureEntities(clonedInfrastructureEntities)
        .cloneFailedInfrastructures(cloneFailedInfrastructures)
        .governanceMetadata(clonedEnvironmentMapper.getGovernanceMetadata())
        .build();
  }

  public InfrastructureGovernanceDataResponse cloneInfrastructure(String accountId, String infraIdentifier,
      SourceEnvironmentConfig sourceEnvironmentConfig, DestinationEnvironmentConfig destinationEnvConfig,
      ScopeInfo sourceEnvironmentConfigScopeInfo) {
    Optional<InfrastructureEntity> optionalInfrastructure = infrastructureEntityService.get(accountId,
        sourceEnvironmentConfig.getOrgIdentifier(), sourceEnvironmentConfig.getProjectIdentifier(),
        sourceEnvironmentConfigScopeInfo, sourceEnvironmentConfig.getEnvIdentifier(), infraIdentifier, true, false);

    InfrastructureEntity infrastructureEntity = null;
    if (optionalInfrastructure.isPresent()) {
      infrastructureEntity = optionalInfrastructure.get();
    } else {
      throw new NotFoundException(
          format("Infrastructure with identifier [%s] with env identifier [%s] in project [%s], org [%s] not found",
              infraIdentifier, sourceEnvironmentConfig.getEnvIdentifier(),
              sourceEnvironmentConfig.getProjectIdentifier(), sourceEnvironmentConfig.getOrgIdentifier()));
    }

    String sourceInfraStoreType = INLINE.toString();
    if (REMOTE.equals(infrastructureEntity.getStoreType())) {
      sourceInfraStoreType = REMOTE.toString();
    }
    String sourceInfraYaml = infrastructureEntity.getYaml();

    JsonNode jsonNode;
    try {
      jsonNode = objectMapper.readTree(sourceInfraYaml);
    } catch (JsonProcessingException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check that the yaml of the source infrastructure is valid",
          "The yaml of the source infrastructure is invalid",
          new InvalidRequestException(
              format("An error occurred while cloning yaml of infrastructure [%s] belonging to environment [%s] in "
                      + "project [%s], org [%s]",
                  infrastructureEntity.getIdentifier(), infrastructureEntity.getEnvIdentifier(),
                  infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getOrgIdentifier()),
              e));
    }

    if (isEmpty(destinationEnvConfig.getProjectIdentifier())) {
      JsonNodeUtils.deletePropertiesInJsonNode(
          (ObjectNode) jsonNode.get("infrastructureDefinition"), "projectIdentifier");
    } else if (!(destinationEnvConfig.getProjectIdentifier()).equals(infrastructureEntity.getProjectIdentifier())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("infrastructureDefinition"), "projectIdentifier", destinationEnvConfig.getProjectIdentifier());
    }
    if (isEmpty(destinationEnvConfig.getOrgIdentifier())) {
      JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) jsonNode.get("infrastructureDefinition"), "orgIdentifier");
    } else if (!(destinationEnvConfig.getOrgIdentifier()).equals(infrastructureEntity.getOrgIdentifier())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("infrastructureDefinition"), "orgIdentifier", destinationEnvConfig.getOrgIdentifier());
    }
    if (destinationEnvConfig.getEnvIdentifier() != null
        && !destinationEnvConfig.getEnvIdentifier().equals(infrastructureEntity.getEnvIdentifier())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("infrastructureDefinition"), "environmentRef", destinationEnvConfig.getEnvIdentifier());
    }
    String modifiedSourceYaml;
    try {
      modifiedSourceYaml = yamlMapper.writeValueAsString(jsonNode);
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check that the yaml of the source infrastructure is valid",
          "The yaml of the cloned infrastructure is invalid",
          new InvalidRequestException(
              format("An error occurred while cloning yaml of infrastructure [%s] belonging to environment [%s] in "
                      + "project [%s], org [%s]",
                  infrastructureEntity.getIdentifier(), infrastructureEntity.getEnvIdentifier(),
                  infrastructureEntity.getProjectIdentifier(), infrastructureEntity.getOrgIdentifier()),
              e));
    }

    infrastructureEntity.setId(null);
    infrastructureEntity.setOrgIdentifier(destinationEnvConfig.getOrgIdentifier());
    infrastructureEntity.setProjectIdentifier(destinationEnvConfig.getProjectIdentifier());
    infrastructureEntity.setEnvIdentifier(destinationEnvConfig.getEnvIdentifier());
    infrastructureEntity.setYaml(modifiedSourceYaml);

    infrastructureEntity.setConnectorRef(null);
    infrastructureEntity.setFallBackBranch(null);
    infrastructureEntity.setRepo(null);
    infrastructureEntity.setRepoURL(null);
    infrastructureEntity.setFilePath(null);
    infrastructureEntity.setParentUniqueId(null);
    infrastructureEntity.setUniqueId(null);
    infrastructureEntity.setTemplateMetadata(null);
    infrastructureEntity.setStoreType(INLINE);

    publishInfraCloneTelemetryData(
        accountId, infraIdentifier, infrastructureEntity.getStoreType(), sourceInfraStoreType);

    return infrastructureEntityService.create(infrastructureEntity);
  }

  private void publishEnvCloneTelemetryData(String accountId, SourceEnvironmentConfig sourceEnvConfig,
      DestinationEnvironmentConfig destinationEnvConfig, boolean cloneInfrastructures, String sourceEnvStoreType) {
    try {
      String destinationEnvStoreType = INLINE.toString();
      if (REMOTE.equals(destinationEnvConfig.getStoreType())) {
        destinationEnvStoreType = REMOTE.toString();
      }
      SvcEnvCloneTelemetryInfo svcEnvCloneTelemetryInfo = SvcEnvCloneTelemetryInfo.builder()
                                                              .accountIdentifier(accountId)
                                                              .entityIdentifier(sourceEnvConfig.getEnvIdentifier())
                                                              .entityType(ENVIRONMENT)
                                                              .sourceStoreType(sourceEnvStoreType)
                                                              .destinationStoreType(destinationEnvStoreType)
                                                              .cloneAllInfraOfEnv(cloneInfrastructures)
                                                              .build();
      instrumentationHelper.sendSvcEnvCloneEvent(svcEnvCloneTelemetryInfo);
    } catch (Exception ex) {
      log.warn("Exception occurred while sending telemetry event for environment clone: {}",
          ExceptionUtils.getMessage(ex), ex);
    }
  }

  private void publishInfraCloneTelemetryData(
      String accountId, String infraIdentifier, StoreType infrastructureEntityStoreType, String sourceInfraStoreType) {
    try {
      String destinationInfraStoreType = INLINE.toString();
      if (REMOTE.equals(infrastructureEntityStoreType)) {
        destinationInfraStoreType = REMOTE.toString();
      }
      SvcEnvCloneTelemetryInfo svcEnvCloneTelemetryInfo = SvcEnvCloneTelemetryInfo.builder()
                                                              .accountIdentifier(accountId)
                                                              .entityIdentifier(infraIdentifier)
                                                              .entityType(INFRASTRUCTURE)
                                                              .sourceStoreType(sourceInfraStoreType)
                                                              .destinationStoreType(destinationInfraStoreType)
                                                              .build();
      instrumentationHelper.sendSvcEnvCloneEvent(svcEnvCloneTelemetryInfo);
    } catch (Exception ex) {
      log.warn("Exception occurred while sending telemetry event for infrastructure clone: {}",
          ExceptionUtils.getMessage(ex), ex);
    }
  }

  private Map<String, String> getEnvironmentAttributesMap(String environmentType) {
    Map<String, String> environmentAttributes = new HashMap<>();
    environmentAttributes.put("type", environmentType);
    return environmentAttributes;
  }
}

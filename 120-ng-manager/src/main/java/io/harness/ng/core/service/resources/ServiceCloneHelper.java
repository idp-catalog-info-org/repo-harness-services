/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.common.EntityTypeConstants.SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.gitsync.beans.StoreType.INLINE;
import static io.harness.gitsync.beans.StoreType.REMOTE;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitx.EntityGitDetailsGuard;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.gitx.GitXTransientBranchGuard;
import io.harness.gitx.GitXUtils;
import io.harness.jackson.JsonNodeUtils;
import io.harness.ng.core.service.dto.DestinationServiceConfig;
import io.harness.ng.core.service.dto.SourceServiceConfig;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceGovernanceDataResponse;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.telemetry.entity.SvcEnvCloneTelemetryInfo;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.telemetry.helpers.SvcEnvCloneInstrumentationHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.ScopeResolutionHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Slf4j
public class ServiceCloneHelper {
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Inject @Named("NON_PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private GitXSettingsHelper gitXSettingsHelper;
  @Inject private SvcEnvCloneInstrumentationHelper instrumentationHelper;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;
  private ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
  private YAMLMapper yamlMapper = new YAMLMapper();

  public ServiceGovernanceDataResponse cloneService(String accountId, SourceServiceConfig sourceServiceConfig,
      DestinationServiceConfig destinationServiceConfig, ScopeInfo scopeInfo) {
    boolean useScopeInfo = scopeInfo != null;
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, sourceServiceConfig.getOrgIdentifier(), sourceServiceConfig.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, sourceServiceConfig.getServiceIdentifier()), SERVICE_VIEW_PERMISSION);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, destinationServiceConfig.getOrgIdentifier(),
                                                  destinationServiceConfig.getProjectIdentifier()),
        Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);

    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        destinationServiceConfig.getOrgIdentifier(), destinationServiceConfig.getProjectIdentifier(), accountId);

    Optional<ServiceEntity> originalServiceEntityOptional;

    try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(sourceServiceConfig.getBranch())) {
      if (useScopeInfo) {
        originalServiceEntityOptional =
            serviceEntityService.get(scopeInfo, sourceServiceConfig.getServiceIdentifier(), false, true, false);
      } else {
        originalServiceEntityOptional = serviceEntityService.get(accountId, sourceServiceConfig.getOrgIdentifier(),
            sourceServiceConfig.getProjectIdentifier(), sourceServiceConfig.getServiceIdentifier(), false, true, false);
      }
    }

    ServiceEntity serviceEntity = null;
    if (originalServiceEntityOptional.isPresent()) {
      serviceEntity = originalServiceEntityOptional.get();
    } else {
      throw new NotFoundException(ServiceElementMapper.getServiceNotFoundError(sourceServiceConfig.getOrgIdentifier(),
          sourceServiceConfig.getProjectIdentifier(), sourceServiceConfig.getServiceIdentifier()));
    }

    String sourceServiceStoreType = INLINE.toString();
    if (REMOTE.equals(serviceEntity.getStoreType())) {
      sourceServiceStoreType = REMOTE.toString();
    }
    String sourceServiceYaml = useScopeInfo ? serviceEntity.getYaml(scopeInfo) : serviceEntity.getYaml();

    JsonNode jsonNode;
    try {
      jsonNode = objectMapper.readTree(sourceServiceYaml);
    } catch (JsonProcessingException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check that the yaml of the source service is valid", "The yaml of the source service is invalid",
          new InvalidRequestException(
              String.format("An error occurred while cloning yaml of service [%s] in project [%s], org [%s]",
                  serviceEntity.getIdentifier(),
                  useScopeInfo ? scopeInfo.getProjectIdentifier() : serviceEntity.getProjectIdentifier(),
                  useScopeInfo ? scopeInfo.getOrgIdentifier() : serviceEntity.getOrgIdentifier()),
              e));
    }

    if (isEmpty(destinationServiceConfig.getProjectIdentifier())) {
      JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) jsonNode.get("service"), "projectIdentifier");
    } else if (!(destinationServiceConfig.getProjectIdentifier()).equals(sourceServiceConfig.getProjectIdentifier())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("service"), "projectIdentifier", destinationServiceConfig.getProjectIdentifier());
    }

    if (isEmpty(destinationServiceConfig.getOrgIdentifier())) {
      JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) jsonNode.get("service"), "orgIdentifier");
    } else if (!(destinationServiceConfig.getOrgIdentifier()).equals(sourceServiceConfig.getOrgIdentifier())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("service"), "orgIdentifier", destinationServiceConfig.getOrgIdentifier());
    }
    if (destinationServiceConfig.getServiceIdentifier() != null
        && !destinationServiceConfig.getServiceIdentifier().equals(serviceEntity.getIdentifier())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("service"), "identifier", destinationServiceConfig.getServiceIdentifier());
    }
    if (destinationServiceConfig.getDescription() != null
        && !destinationServiceConfig.getDescription().equals(serviceEntity.getDescription())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("service"), "description", destinationServiceConfig.getDescription());
    }
    if (destinationServiceConfig.getTags() != null) {
      Map<String, String> tags = destinationServiceConfig.getTags();
      ObjectMapper jsonMapper = new ObjectMapper();
      ((ObjectNode) jsonNode.get("service")).set("tags", jsonMapper.convertValue(tags, JsonNode.class));
    }
    if (destinationServiceConfig.getServiceName() != null
        && !destinationServiceConfig.getServiceName().equals(serviceEntity.getName())) {
      JsonNodeUtils.updatePropertyInObjectNode(
          jsonNode.get("service"), "name", destinationServiceConfig.getServiceName());
    }

    String modifiedSourceYaml;
    try {
      modifiedSourceYaml = yamlMapper.writeValueAsString(jsonNode);
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check that the yaml of the source service is valid", "The yaml of the cloned service is invalid",
          new InvalidRequestException(
              String.format("An error occurred while cloning yaml of service [%s] in project [%s], org [%s]",
                  serviceEntity.getIdentifier(),
                  useScopeInfo ? scopeInfo.getProjectIdentifier() : serviceEntity.getProjectIdentifier(),
                  useScopeInfo ? scopeInfo.getOrgIdentifier() : serviceEntity.getOrgIdentifier()),
              e));
    }

    serviceEntity.setId(null);
    serviceEntity.setVersion(null);
    serviceEntity.setOrgIdentifier(destinationServiceConfig.getOrgIdentifier());
    serviceEntity.setProjectIdentifier(destinationServiceConfig.getProjectIdentifier());
    if (destinationServiceConfig.getDescription() != null) {
      serviceEntity.setDescription(destinationServiceConfig.getDescription());
    }
    if (destinationServiceConfig.getTags() != null) {
      serviceEntity.setTags(convertToList(destinationServiceConfig.getTags()));
    }
    serviceEntity.setName(destinationServiceConfig.getServiceName());
    serviceEntity.setIdentifier(destinationServiceConfig.getServiceIdentifier());
    serviceEntity.setYaml(modifiedSourceYaml);

    serviceEntity.setRepo(null);
    serviceEntity.setRepoURL(null);
    serviceEntity.setConnectorRef(null);
    serviceEntity.setFilePath(null);
    serviceEntity.setFallBackBranch(null);
    serviceEntity.setParentUniqueId(null);
    serviceEntity.setUniqueId(null);
    serviceEntity.setTemplateMetadata(null);
    ServiceGovernanceDataResponse clonedServiceMapper;
    if (REMOTE.equals(destinationServiceConfig.getStoreType())) {
      GitXUtils.validateConnectorRefRequirement(
          destinationServiceConfig.getConnectorRef(), destinationServiceConfig.isHarnessCodeRepo());
      GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                        .branch(destinationServiceConfig.getBranch())
                                        .filePath(destinationServiceConfig.getFilePath())
                                        .commitMsg(destinationServiceConfig.getCommitMessage())
                                        .isNewBranch(isNotEmpty(destinationServiceConfig.getBranch())
                                            && isNotEmpty(destinationServiceConfig.getBaseBranch()))
                                        .baseBranch(destinationServiceConfig.getBaseBranch())
                                        .connectorRef(destinationServiceConfig.getConnectorRef())
                                        .isHarnessCodeRepo(destinationServiceConfig.isHarnessCodeRepo())
                                        .storeType(REMOTE)
                                        .repoName(destinationServiceConfig.getRepoName())
                                        .build();
      try (EntityGitDetailsGuard entityGitDetailsGuard = new EntityGitDetailsGuard(gitEntityInfo)) {
        if (useScopeInfo) {
          ScopeInfo destinationScopeInfo = scopeResolutionHelper.getScopeInfo(serviceEntity.getAccountIdentifier(),
              destinationServiceConfig.getOrgIdentifier(), destinationServiceConfig.getProjectIdentifier());
          clonedServiceMapper = serviceEntityService.create(serviceEntity, destinationScopeInfo);
        } else {
          clonedServiceMapper = serviceEntityService.create(serviceEntity);
        }
      } catch (PolicyEvaluationFailureException e) {
        log.warn(
            String.format("Policy evaluation failed while cloning the service: [%s]", serviceEntity.getIdentifier()),
            e);
        throw e;
      } catch (Exception e) {
        log.error(String.format("An unexpected error occurred while cloning service: [%s] to remote destination",
                      serviceEntity.getIdentifier()),
            e);
        throw new InvalidRequestException(
            String.format("An error occurred while cloning service [%s] to remote destination: %s",
                serviceEntity.getIdentifier(), e.getMessage()));
      }
    } else {
      try {
        if (useScopeInfo) {
          ScopeInfo destinationScopeInfo = scopeResolutionHelper.getScopeInfo(serviceEntity.getAccountIdentifier(),
              destinationServiceConfig.getOrgIdentifier(), destinationServiceConfig.getProjectIdentifier());
          clonedServiceMapper = serviceEntityService.create(serviceEntity, destinationScopeInfo);
        } else {
          clonedServiceMapper = serviceEntityService.create(serviceEntity);
        }
      } catch (PolicyEvaluationFailureException e) {
        log.warn(
            String.format("Policy evaluation failed while cloning the service: [%s]", serviceEntity.getIdentifier()),
            e);
        throw e;
      } catch (Exception ex) {
        String message = ex.getMessage();
        if (message.contains("cannot be empty for PROJECT scope")) {
          String entityIdentifier = serviceEntity.getIdentifier();
          String targetLevel = message.contains("projectIdentifier")
              ? (isEmpty(serviceEntity.getOrgIdentifier()) ? "ACCOUNT" : "ORG")
              : "ACCOUNT";

          throw new InvalidRequestException(String.format(
              "One of the entities in [%s] referenced at the [PROJECT] level cannot be accessed at the target [%s].",
              entityIdentifier, targetLevel));
        } else {
          throw new InvalidRequestException(
              String.format("An error occurred while cloning service [%s] to inline destination: %s",
                  serviceEntity.getIdentifier(), ex.getMessage()));
        }
      }
    }

    publishSvcCloneTelemetryData(accountId, sourceServiceConfig, destinationServiceConfig, sourceServiceStoreType);

    return clonedServiceMapper;
  }

  private void publishSvcCloneTelemetryData(String accountId, SourceServiceConfig sourceServiceConfig,
      DestinationServiceConfig destinationServiceConfig, String sourceServiceStoreType) {
    try {
      String destinationServiceStoreType = INLINE.toString();
      if (REMOTE.equals(destinationServiceConfig.getStoreType())) {
        destinationServiceStoreType = REMOTE.toString();
      }
      SvcEnvCloneTelemetryInfo svcEnvCloneTelemetryInfo =
          SvcEnvCloneTelemetryInfo.builder()
              .accountIdentifier(accountId)
              .entityIdentifier(sourceServiceConfig.getServiceIdentifier())
              .entityType(SERVICE)
              .sourceStoreType(sourceServiceStoreType)
              .destinationStoreType(destinationServiceStoreType)
              .build();
      instrumentationHelper.sendSvcEnvCloneEvent(svcEnvCloneTelemetryInfo);
    } catch (Exception ex) {
      log.warn(
          "Exception occurred while sending telemetry event for service clone: {}", ExceptionUtils.getMessage(ex), ex);
    }
  }
}

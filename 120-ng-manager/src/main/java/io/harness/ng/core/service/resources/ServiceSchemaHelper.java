/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.yaml.YAMLFieldNameConstants.INPUT_TYPE_STRING;

import static java.util.stream.Collectors.toList;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.gitx.GitXTransientBranchGuard;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.helpers.ServiceFilterHelper;
import io.harness.ng.core.service.mapper.TemplateBasedServiceMapper;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.spec.server.ng.v1.model.ArtifactSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.ArtifactSchemaResponseDTO;
import io.harness.spec.server.ng.v1.model.ConfigFileSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.ConfigFileSchemaResponseDTO;
import io.harness.spec.server.ng.v1.model.ManifestSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.ManifestSchemaResponseDTO;
import io.harness.spec.server.ng.v1.model.ServiceSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.ServiceSchemaResponseDTO;
import io.harness.spec.server.ng.v1.model.ServicesSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.ServicesSchemaResponseDTO;
import io.harness.spec.server.ng.v1.model.UnifiedSchemaResponseDTO.TypeEnum;
import io.harness.unified.cd.service.artifacts.ArtifactConfig;
import io.harness.unified.cd.service.configfiles.ConfigFile;
import io.harness.unified.cd.service.manifests.ManifestConfig;
import io.harness.unified.cd.service.spec.ServiceConfig;
import io.harness.unified.cd.utils.UnifiedEntitySchemaUtils;
import io.harness.utils.ApiUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@Singleton
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ServiceSchemaHelper {
  private final AccessControlClient accessControlClient;
  private final ScopeAccessHelper scopeAccessHelper;
  private final ServiceEntityService serviceEntityService;
  private final ScopeInfoService scopeInfoService;
  private final ServiceHelper serviceHelper;
  private final TemplateBasedServiceMapper templateBasedServiceMapper;
  private final ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());

  public Response generateServicesUnifiedSchema(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, Pageable pageRequest, ServicesSchemaRequestDTO requestDTO) {
    accessControlClient.checkForAccessOrThrow(List.of(scopeAccessHelper.getPermissionCheckDtoForViewAccessForScope(
                                                  Scope.of(accountIdentifier, orgIdentifier, projectIdentifier))),
        "Unauthorized to list services");

    Criteria criteria;
    if (scopeInfo != null) {
      Map<ScopeLevel, String> uniqueIdsMap = scopeInfoService.getUniqueIdsIncludingParentScopes(scopeInfo);
      criteria =
          ServiceFilterHelper.createCriteriaForGetList(scopeInfo, false, null, null, false, false, null, uniqueIdsMap);
    } else {
      criteria = ServiceFilterHelper.createCriteriaForGetList(
          accountIdentifier, orgIdentifier, projectIdentifier, false, null, null, false, false, null);
    }

    List<ServiceResponse> serviceList = serviceEntityService.listRunTimePermission(criteria, false)
                                            .stream()
                                            .map(ServiceElementMapper::toAccessListResponseWrapper)
                                            .collect(Collectors.toList());
    List<PermissionCheckDTO> permissionCheckDTOS =
        serviceList.stream().map(CDNGRbacUtility::serviceResponseToPermissionCheckDTO).collect(Collectors.toList());
    List<AccessControlDTO> accessControlList =
        accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();
    serviceList = serviceHelper.filterByPermissionAndId(accessControlList, serviceList);
    List<String> serviceIds = serviceList.stream().map(svcRes -> svcRes.getService().getIdentifier()).collect(toList());
    String yaml = getGeneratedServicesSchemaYaml(serviceIds);
    ServicesSchemaResponseDTO responseDTO = new ServicesSchemaResponseDTO();
    responseDTO.setYaml(yaml);
    responseDTO.setType(TypeEnum.SERVICES);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithPageInfo =
        ApiUtils.addLinksHeader(responseBuilder, 1, pageRequest.getPageNumber(), pageRequest.getPageSize());
    return responseBuilderWithPageInfo.entity(responseDTO).build();
  }

  public Response generateManifestUnifiedSchema(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, Pageable pageRequest, ManifestSchemaRequestDTO requestDTO) {
    String serviceId = requestDTO.getServiceId();
    String serviceBranch = requestDTO.getServiceBranch();
    String manifestId = requestDTO.getManifestId();
    String result = getGeneratedManifestSchemaYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, serviceId, serviceBranch, manifestId);
    ManifestSchemaResponseDTO responseDTO = new ManifestSchemaResponseDTO();
    responseDTO.setYaml(result);
    responseDTO.setType(TypeEnum.MANIFEST);
    responseDTO.setServiceId(serviceId);
    responseDTO.setServiceBranch(serviceBranch);
    responseDTO.setManifestId(manifestId);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithPageInfo =
        ApiUtils.addLinksHeader(responseBuilder, 1, pageRequest.getPageNumber(), pageRequest.getPageSize());
    return responseBuilderWithPageInfo.entity(responseDTO).build();
  }

  public Response generateArtifactUnifiedSchema(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, Pageable pageRequest, ArtifactSchemaRequestDTO requestDTO) {
    String serviceId = requestDTO.getServiceId();
    String serviceBranch = requestDTO.getServiceBranch();
    String artifactId = requestDTO.getArtifactId();
    String result = getGeneratedArtifactSchemaYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, serviceId, serviceBranch, artifactId);
    ArtifactSchemaResponseDTO responseDTO = new ArtifactSchemaResponseDTO();
    responseDTO.setYaml(result);
    responseDTO.setType(TypeEnum.ARTIFACT);
    responseDTO.setServiceId(serviceId);
    responseDTO.setServiceBranch(serviceBranch);
    responseDTO.setArtifactId(artifactId);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithPageInfo =
        ApiUtils.addLinksHeader(responseBuilder, 1, pageRequest.getPageNumber(), pageRequest.getPageSize());
    return responseBuilderWithPageInfo.entity(responseDTO).build();
  }

  public Response generateConfigFileUnifiedSchema(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, Pageable pageRequest, ConfigFileSchemaRequestDTO requestDTO) {
    String serviceId = requestDTO.getServiceId();
    String serviceBranch = requestDTO.getServiceBranch();
    String configFileId = requestDTO.getConfigFileId();
    String result = getGeneratedConfigFileSchemaYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, serviceId, serviceBranch, configFileId);
    ConfigFileSchemaResponseDTO responseDTO = new ConfigFileSchemaResponseDTO();
    responseDTO.setYaml(result);
    responseDTO.setType(TypeEnum.CONFIG_FILE);
    responseDTO.setServiceId(serviceId);
    responseDTO.setServiceBranch(serviceBranch);
    responseDTO.setConfigFileId(configFileId);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithPageInfo =
        ApiUtils.addLinksHeader(responseBuilder, 1, pageRequest.getPageNumber(), pageRequest.getPageSize());
    return responseBuilderWithPageInfo.entity(responseDTO).build();
  }

  public Response generateServiceUnifiedSchema(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, Pageable pageRequest, ServiceSchemaRequestDTO requestDTO) {
    String serviceId = requestDTO.getServiceId();
    String serviceBranch = requestDTO.getServiceBranch();
    String serviceInputYaml = requestDTO.getServiceInputYaml();
    String result = getGeneratedServiceSchemaYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, serviceId, serviceBranch, serviceInputYaml);
    ServiceSchemaResponseDTO responseDTO = new ServiceSchemaResponseDTO();
    responseDTO.setYaml(result);
    responseDTO.setType(TypeEnum.SERVICE);
    responseDTO.setServiceId(serviceId);
    responseDTO.setServiceBranch(serviceBranch);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithPageInfo =
        ApiUtils.addLinksHeader(responseBuilder, 1, pageRequest.getPageNumber(), pageRequest.getPageSize());
    return responseBuilderWithPageInfo.entity(responseDTO).build();
  }

  private String getGeneratedServicesSchemaYaml(List<String> serviceIds) {
    String uuid = generateUuid();
    JsonNode jsonNode = UnifiedEntitySchemaUtils.generateSingleInputSection(INPUT_TYPE_STRING, true, serviceIds);
    return YamlUtils.generateInputsSectionYaml(Map.of(uuid, jsonNode));
  }

  private String getGeneratedManifestSchemaYaml(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, String serviceId, String serviceBranch, String manifestId) {
    NGServiceConfig ngServiceConfig =
        getNgServiceConfig(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, serviceId, serviceBranch);

    ServiceConfig unifiedServiceConfig = templateBasedServiceMapper.toUnifiedServiceWithTemplate(ngServiceConfig);
    if (unifiedServiceConfig == null) {
      log.warn("Template-based conversion failed for service: {}. POJO-based conversion has been removed.", serviceId);
      return "";
    }

    ManifestConfig manifestConfig = unifiedServiceConfig.getServiceInfoConfig()
                                        .getWith()
                                        .getManifests()
                                        .getSources()
                                        .stream()
                                        .filter(source -> source.getId().equals(manifestId))
                                        .findFirst()
                                        .orElse(null);
    if (manifestConfig != null) {
      Map<String, JsonNode> fqnToJsonNodeMap = manifestConfig.getGeneratedSchemaForInput();
      return YamlUtils.generateYamlWithInputsSchema(
          YamlPipelineUtils.writeYamlString(manifestConfig), YamlUtils.generateInputsSectionYaml(fqnToJsonNodeMap));
    }
    return "";
  }

  private String getGeneratedArtifactSchemaYaml(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, String serviceId, String serviceBranch, String artifactId) {
    NGServiceConfig ngServiceConfig =
        getNgServiceConfig(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, serviceId, serviceBranch);

    ServiceConfig unifiedServiceConfig = templateBasedServiceMapper.toUnifiedServiceWithTemplate(ngServiceConfig);
    if (unifiedServiceConfig == null) {
      log.warn("Template-based conversion failed for service: {}. POJO-based conversion has been removed.", serviceId);
      return "";
    }

    ArtifactConfig artifactConfig = unifiedServiceConfig.getServiceInfoConfig()
                                        .getWith()
                                        .getArtifacts()
                                        .getSources()
                                        .stream()
                                        .filter(source -> source.getId().equals(artifactId))
                                        .findFirst()
                                        .orElse(null);
    if (artifactConfig != null) {
      Map<String, JsonNode> fqnToJsonNodeMap = artifactConfig.getGeneratedSchemaForInput();
      return YamlUtils.generateYamlWithInputsSchema(
          YamlPipelineUtils.writeYamlString(artifactConfig), YamlUtils.generateInputsSectionYaml(fqnToJsonNodeMap));
    }
    return "";
  }

  private String getGeneratedConfigFileSchemaYaml(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, String serviceId, String serviceBranch, String configFileId) {
    NGServiceConfig ngServiceConfig =
        getNgServiceConfig(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, serviceId, serviceBranch);

    ServiceConfig unifiedServiceConfig = templateBasedServiceMapper.toUnifiedServiceWithTemplate(ngServiceConfig);
    if (unifiedServiceConfig == null) {
      log.warn("Template-based conversion failed for service: {}. POJO-based conversion has been removed.", serviceId);
      return "";
    }

    ConfigFile configFile = unifiedServiceConfig.getServiceInfoConfig()
                                .getWith()
                                .getConfigFiles()
                                .stream()
                                .filter(file -> file.getId().equals(configFileId))
                                .findFirst()
                                .orElse(null);
    if (configFile != null) {
      Map<String, JsonNode> fqnToJsonNodeMap = configFile.getGeneratedSchemaForInput();
      return YamlUtils.generateYamlWithInputsSchema(
          YamlPipelineUtils.writeYamlString(configFile), YamlUtils.generateInputsSectionYaml(fqnToJsonNodeMap));
    }
    return "";
  }

  private String getGeneratedServiceSchemaYaml(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, String serviceId, String serviceBranch, String serviceInputYaml) {
    NGServiceConfig ngServiceConfig =
        getNgServiceConfig(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, serviceId, serviceBranch);
    Pair<String, String> serviceAndInputSchemaYaml =
        getUnifiedServiceAndInputSchemaYaml(serviceId, serviceBranch, ngServiceConfig, serviceInputYaml);
    if (serviceAndInputSchemaYaml == null) {
      return "";
    }
    return YamlUtils.generateYamlWithInputsSchema(
        serviceAndInputSchemaYaml.getLeft(), serviceAndInputSchemaYaml.getRight());
  }

  private NGServiceConfig getNgServiceConfig(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, String serviceId, String serviceBranch) {
    Optional<ServiceEntity> serviceEntityOpt =
        getServiceEntity(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, serviceId, serviceBranch);
    try {
      if (serviceEntityOpt.isPresent()) {
        return YamlUtils.read(
            scopeInfo != null ? serviceEntityOpt.get().getYaml(scopeInfo) : serviceEntityOpt.get().getYaml(),
            NGServiceConfig.class);
      }
    } catch (Exception ex) {
      log.warn(String.format(
          "Could not parse V0 service yaml. Service id %s, service branch: %s", serviceId, serviceBranch));
    }
    return null;
  }

  private Pair<String, String> getUnifiedServiceAndInputSchemaYaml(
      String serviceId, String serviceBranch, NGServiceConfig ngServiceConfig, String serviceInputYaml) {
    try {
      ServiceConfig unifiedServiceConfig = templateBasedServiceMapper.toUnifiedServiceWithTemplate(ngServiceConfig);
      if (unifiedServiceConfig == null) {
        log.warn(
            "Template-based conversion failed for service: {}. POJO-based conversion has been removed.", serviceId);
        return null;
      }

      String unifiedServiceYaml = YamlPipelineUtils.writeYamlString(unifiedServiceConfig);
      String inputSchemaYaml = YamlUtils.generateInputsSectionYaml(unifiedServiceConfig.getGeneratedSchemaForInput());
      return Pair.of(unifiedServiceYaml, inputSchemaYaml);
    } catch (Exception ex) {
      log.warn(String.format("Conversion of V0 service yaml to unified yaml failed. Service id %s, service branch: %s",
          serviceId, serviceBranch));
    }
    return null;
  }

  private Optional<ServiceEntity> getServiceEntity(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, String serviceId, String serviceBranch) {
    try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(serviceBranch)) {
      return serviceEntityService.get(scopeInfo, serviceId, false, false, false);
    }
  }
}

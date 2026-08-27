/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.pms.yaml.YAMLFieldNameConstants.INPUT_TYPE_STRING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.infra.definition.config.InfrastructureConfig;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitx.EntityGitDetailsGuard;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.mappers.InfrastructureFilterHelper;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.yaml.YamlUtils;
import io.harness.spec.server.ng.v1.model.EntityGitDetails;
import io.harness.spec.server.ng.v1.model.EnvironmentMetadata;
import io.harness.spec.server.ng.v1.model.EnvironmentSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.EnvironmentSchemaResponseDTO;
import io.harness.spec.server.ng.v1.model.InfrastructureSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.InfrastructureSchemaResponseDTO;
import io.harness.spec.server.ng.v1.model.UnifiedSchemaResponseDTO.TypeEnum;
import io.harness.unified.cd.utils.UnifiedEntitySchemaUtils;
import io.harness.utils.ApiUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Helper class for generating and managing infrastructure schemas.
 */
@OwnedBy(CDP)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class InfrastructureSchemaHelper {
  private final InfrastructureEntityService infrastructureEntityService;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final EnvironmentValidationHelper environmentValidationHelper;
  @Inject private InfrastructureHelper infrastructureHelper;

  public Response generateInfrastructureUnifiedSchema(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, Pageable pageRequest, InfrastructureSchemaRequestDTO requestDTO) {
    String envId = requestDTO.getEnvId();
    String envBranch = requestDTO.getEnvBranch();
    String infraId = requestDTO.getInfraId();
    String infraInputYaml = requestDTO.getInfraInputYaml();
    String result = getGeneratedSchemaYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, infraId, envId, envBranch, infraInputYaml);
    InfrastructureSchemaResponseDTO responseDTO = new InfrastructureSchemaResponseDTO();
    responseDTO.setYaml(result);
    responseDTO.setType(TypeEnum.INFRASTRUCTURE);
    responseDTO.setEnvId(envId);
    responseDTO.setInfraId(infraId);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithPageInfo =
        ApiUtils.addLinksHeader(responseBuilder, 1, pageRequest.getPageNumber(), pageRequest.getPageSize());
    return responseBuilderWithPageInfo.entity(responseDTO).build();
  }

  private Pair<String, String> getUnifiedInfraAndInputSchemaYaml(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, String infraId, String envId, String envBranch,
      String infraInputYaml) {
    Optional<InfrastructureEntity> infrastructureEntityOp = getInfrastructureEntity(
        accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, infraId, envId, envBranch);
    try {
      if (infrastructureEntityOp.isPresent()) {
        InfrastructureConfig ngInfrastructureConfig =
            YamlUtils.read(infrastructureEntityOp.get().getYaml(), InfrastructureConfig.class);
        return UnifiedInfrastructureConversionUtility.getUnifiedInfraAndInputSchemaYaml(
            ngInfrastructureConfig, infraInputYaml);
      }
    } catch (Exception ex) {
      log.warn(
          String.format("conversion of V0 infra yaml to unified yaml failed. Infra id %s, envId: %s", infraId, envId));
    }
    return null;
  }

  private Optional<InfrastructureEntity> getInfrastructureEntity(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, String infraId, String envId, String envBranch) {
    try (EntityGitDetailsGuard ignore =
             new EntityGitDetailsGuard(infrastructureEntityService.getGitDetailsForInfrastructure(
                 accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, envId, envBranch))) {
      return infrastructureEntityService.get(
          accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, envId, infraId);
    }
  }
  /**
   * Generates unified schema for infrastructures.
   *
   * @param accountIdentifier  Account identifier
   * @param orgIdentifier      Organization identifier
   * @param projectIdentifier  Project identifier
   * @param scopeInfo          Scope information
   * @param pageRequest        Pagination request
   * @param requestDTO         Request DTO containing filter criteria
   * @return Response containing the generated schema YAML
   */
  public Response generateEnvironmentUnifiedSchema(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, Pageable pageRequest, EnvironmentSchemaRequestDTO requestDTO) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
        orgIdentifier, projectIdentifier, accountIdentifier);
    String envId = requestDTO.getEnvId();
    Environment environment =
        environmentValidationHelper.checkThatEnvExists(accountIdentifier, orgIdentifier, projectIdentifier, envId);
    infrastructureHelper.checkForAccessOrThrow(
        accountIdentifier, orgIdentifier, projectIdentifier, envId, ENVIRONMENT_VIEW_PERMISSION, "list");
    Criteria criteria = InfrastructureFilterHelper.createListCriteria(scopeInfo, envId, null, null, null, null, false);
    Page<InfrastructureEntity> infraEntities = infrastructureEntityService.list(criteria, pageRequest, false);
    List<String> infraIds = infraEntities.getContent().stream().map(InfrastructureEntity::getIdentifier).toList();
    String yaml = getGeneratedSchemaYaml(infraIds);

    EnvironmentSchemaResponseDTO responseDTO = new EnvironmentSchemaResponseDTO();
    responseDTO.setYaml(yaml);
    responseDTO.setEnvId(envId);
    responseDTO.setType(TypeEnum.ENVIRONMENT);
    responseDTO.setEnvMetadata(getEnvironmentMetaData(environment));
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithPageInfo = ApiUtils.addLinksHeader(
        responseBuilder, infraEntities.getTotalElements(), pageRequest.getPageNumber(), pageRequest.getPageSize());
    return responseBuilderWithPageInfo.entity(responseDTO).build();
  }

  private EnvironmentMetadata getEnvironmentMetaData(Environment environment) {
    if (StoreType.REMOTE.equals(environment.getStoreType())) {
      EnvironmentMetadata environmentMetadata = new EnvironmentMetadata();
      EntityGitDetails entityGitDetails = new EntityGitDetails();
      entityGitDetails.setConnectorRef(environment.getConnectorRef());
      entityGitDetails.setFilePath(environment.getFilePath());
      entityGitDetails.setRepoName(environment.getRepo());
      environmentMetadata.setGitDetails(entityGitDetails);
      return environmentMetadata;
    }
    return null;
  }

  private String getGeneratedSchemaYaml(List<String> infraIds) {
    String uuid = generateUuid();
    JsonNode jsonNode = UnifiedEntitySchemaUtils.generateSingleInputSection(INPUT_TYPE_STRING, true, infraIds);
    return YamlUtils.generateInputsSectionYaml(Map.of(uuid, jsonNode));
  }

  private String getGeneratedSchemaYaml(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, String infraId, String envId, String envBranch, String infraInputYaml) {
    Pair<String, String> infraAndInputSchemaYaml = getUnifiedInfraAndInputSchemaYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, infraId, envId, envBranch, infraInputYaml);
    if (infraAndInputSchemaYaml == null) {
      return "";
    }
    return YamlUtils.generateYamlWithInputsSchema(
        infraAndInputSchemaYaml.getLeft(), infraAndInputSchemaYaml.getRight());
  }
}

/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.envGroup.resource.EnvironmentGroupSchemaHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.ng.core.environment.resources.EnvironmentSchemaHelper;
import io.harness.ng.core.infrastructure.resource.InfrastructureSchemaHelper;
import io.harness.ng.core.service.resources.ServiceSchemaHelper;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.UnifiedSchemaGenerationApi;
import io.harness.spec.server.ng.v1.model.ArtifactSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.ConfigFileSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.EnvGroupsSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.EnvironmentSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.EnvironmentsSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.GitEntityFindInfoDTO;
import io.harness.spec.server.ng.v1.model.InfrastructureSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.ManifestSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.ServiceSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.ServicesSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.UnifiedSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.UnifiedSchemaRequestDTO.TypeEnum;
import io.harness.utils.PageUtils;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.Arrays;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(CI)
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class UnifiedSchemaGenerationApiImpl implements UnifiedSchemaGenerationApi {
  private final ScopeInfoService scopeInfoService;
  private final EnvironmentGroupSchemaHelper environmentGroupSchemaHelper;
  private final EnvironmentSchemaHelper environmentSchemaHelper;
  private InfrastructureSchemaHelper infrastructureSchemaHelper;
  private ServiceSchemaHelper serviceSchemaHelper;
  private static final String ERROR_MESSAGE_FORMAT = "Unsupported entity type: %s"
      + ". Supported values are: " + Arrays.toString(TypeEnum.values());

  @Override
  @Timed
  @ResponseMetered
  public Response generateUnifiedSchema(@Valid UnifiedSchemaRequestDTO body, String entityType, String harnessAccount,
      String project, String org, Integer page, @Max(1000) Integer limit) {
    log.info("Generating unified schema for entity type: {} in project: {}, org: {}, account: {}", entityType, project,
        org, harnessAccount);
    if (body == null) {
      throw new InvalidRequestException("Request body must not be null.");
    }

    // populating git details
    populateGitDetails(body);

    try {
      Pageable pageRequest =
          PageUtils.getPageRequest(page, limit, null, Sort.by(Sort.Direction.DESC, "lastModifiedAt"));
      ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, org, project);
      return processUnifiedSchemaRequest(harnessAccount, org, project, entityType, body, scopeInfo, pageRequest);
    } catch (Exception e) {
      log.error("Error generating unified schema", e);
      throw new InvalidRequestException("Failed to generate unified schema: " + e.getMessage());
    }
  }

  public Response processUnifiedSchemaRequest(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String entityType, UnifiedSchemaRequestDTO requestDTO, ScopeInfo scopeInfo, Pageable pageRequest) {
    log.info("Processing unified schema request for entity type: {} in project: {}, org: {}, account: {}", entityType,
        projectIdentifier, orgIdentifier, accountIdentifier);
    try {
      switch (getTypeEnum(entityType)) {
        case ENVIRONMENT_GROUPS -> {
          return environmentGroupSchemaHelper.generateEnvGroupUnifiedSchema(
                  accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, pageRequest, (EnvGroupsSchemaRequestDTO) requestDTO);
        }
        case ENVIRONMENTS -> {
          return environmentSchemaHelper.generateEnvironmentsUnifiedSchema(
                  accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, pageRequest, (EnvironmentsSchemaRequestDTO) requestDTO);
        }
        case ENVIRONMENT -> {
          return infrastructureSchemaHelper.generateEnvironmentUnifiedSchema(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, pageRequest, (EnvironmentSchemaRequestDTO) requestDTO);
        }
        case INFRASTRUCTURE -> {
          return infrastructureSchemaHelper.generateInfrastructureUnifiedSchema(accountIdentifier,orgIdentifier, projectIdentifier, scopeInfo, pageRequest, (InfrastructureSchemaRequestDTO) requestDTO);
        }
        case SERVICES -> {
          return serviceSchemaHelper.generateServicesUnifiedSchema(accountIdentifier,orgIdentifier, projectIdentifier, scopeInfo, pageRequest, (ServicesSchemaRequestDTO) requestDTO);
        }
        case SERVICE -> {
          return serviceSchemaHelper.generateServiceUnifiedSchema(accountIdentifier,orgIdentifier, projectIdentifier, scopeInfo, pageRequest, (ServiceSchemaRequestDTO) requestDTO);
        }
        case ARTIFACT -> {
          return serviceSchemaHelper.generateArtifactUnifiedSchema(accountIdentifier,orgIdentifier, projectIdentifier, scopeInfo, pageRequest, (ArtifactSchemaRequestDTO) requestDTO);
        }
        case MANIFEST -> {
          return serviceSchemaHelper.generateManifestUnifiedSchema(accountIdentifier,orgIdentifier, projectIdentifier, scopeInfo, pageRequest, (ManifestSchemaRequestDTO) requestDTO);
        }
        case CONFIG_FILE -> {
          return serviceSchemaHelper.generateConfigFileUnifiedSchema(accountIdentifier,orgIdentifier, projectIdentifier, scopeInfo, pageRequest, (ConfigFileSchemaRequestDTO) requestDTO);
        }

        default -> {
          log.error(ERROR_MESSAGE_FORMAT, entityType);
          return Response.status(Response.Status.BAD_REQUEST)
              .entity(String.format(ERROR_MESSAGE_FORMAT, entityType))
              .build();
        }
      }
    } catch (Exception e) {
      log.error("Error generating unified schema for entity type: {}", entityType, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("Failed to generate schema: " + e.getMessage())
          .build();
    }
  }

  private void populateGitDetails(UnifiedSchemaRequestDTO request) {
    if(request != null && request.getGitDetails() != null ) {
      GitEntityFindInfoDTO gitEntityFindInfoDTO = request.getGitDetails();
      GitEntityInfo gitEntityInfo =
              GitEntityInfo.builder()
                      .branch(gitEntityFindInfoDTO.getBranch())
                      .repoName(gitEntityFindInfoDTO.getRepoName())
                      .yamlGitConfigId(gitEntityFindInfoDTO.getYamlGitConfigId())
                      .parentEntityConnectorRef(gitEntityFindInfoDTO.getParentEntityConnectorRef())
                      .parentEntityRepoName(gitEntityFindInfoDTO.getParentEntityRepoName())
                      .parentEntityAccountIdentifier(gitEntityFindInfoDTO.getParentEntityAccountIdentifier())
                      .parentEntityOrgIdentifier(gitEntityFindInfoDTO.getParentEntityOrgIdentifier())
                      .parentEntityProjectIdentifier(gitEntityFindInfoDTO.getParentEntityProjectIdentifier())
                      .build();
      GitAwareContextHelper.populateGitDetails(gitEntityInfo);
    }
  }

  private static TypeEnum getTypeEnum(String v) {
    try {
      return TypeEnum.fromValue(v);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(String.format(ERROR_MESSAGE_FORMAT, v), e);
    }
  }

}

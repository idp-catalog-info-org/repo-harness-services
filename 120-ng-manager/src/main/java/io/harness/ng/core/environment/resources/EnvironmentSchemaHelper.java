/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.ng.core.environment.resources.EnvironmentResourceConstants.UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.INPUT_TYPE_STRING;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.EnvironmentMapper;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.helpers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.spec.server.ng.v1.model.EnvironmentsSchemaRequestDTO;
import io.harness.spec.server.ng.v1.model.EnvironmentsSchemaResponseDTO;
import io.harness.spec.server.ng.v1.model.UnifiedSchemaResponseDTO.TypeEnum;
import io.harness.unified.cd.utils.UnifiedEntitySchemaUtils;
import io.harness.utils.ApiUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Helper class for generating and managing environment schemas.
 */
@OwnedBy(CDC)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class EnvironmentSchemaHelper {
  private final EnvironmentService environmentService;
  private final AccessControlClient accessControlClient;
  private final EnvironmentRbacHelper environmentRbacHelper;
  private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final EnvironmentFilterHelper environmentFilterHelper;
  private final ScopeAccessHelper scopeAccessHelper;
  private final EnvironmentGroupService environmentGroupService;

  /**
   * Generates a unified schema for environments.
   *
   * @param accountIdentifier Account identifier
   * @param orgIdentifier Organization identifier
   * @param projectIdentifier Project identifier
   * @param scopeInfo Scope information
   * @param pageRequest Page request
   * @return Response containing the generated schema
   */
  public Response generateEnvironmentsUnifiedSchema(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, Pageable pageRequest, EnvironmentsSchemaRequestDTO requestDTO) {
    accessControlClient.checkForAccessOrThrow(List.of(scopeAccessHelper.getPermissionCheckDtoForViewAccessForScope(
                                                  Scope.of(accountIdentifier, orgIdentifier, projectIdentifier))),
        UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE);

    String envGroupId = requestDTO.getEnvGroupId();
    List<EnvironmentResponse> environmentList;
    boolean useScopeInfoForEnvGrp = scopeInfo != null
        && pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PL_USE_SCOPE_INFO_FOR_ENV_GRP_ENTITY);
    boolean useScopeInfoForEnv = scopeInfo != null
        && pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PL_USE_SCOPE_INFO_FOR_ENV_ENTITY);
    if (isNotEmpty(envGroupId)) {
      Optional<EnvironmentGroupEntity> environmentGroupEntity = useScopeInfoForEnvGrp
          ? environmentGroupService.get(scopeInfo, envGroupId, false)
          : environmentGroupService.get(accountIdentifier, orgIdentifier, projectIdentifier, envGroupId, false);
      List<String> envGroupEnvIds =
          environmentGroupEntity.map(EnvironmentGroupEntity::getEnvIdentifiers)
              .orElseThrow(()
                               -> new InvalidRequestException(
                                   format("Could not find environment group with identifier: %s", envGroupId)));
      environmentList = environmentService.listEnvironmentsForCrossScopedEnvGroup(scopeInfo, envGroupEnvIds, null);
    } else {
      Criteria criteria = useScopeInfoForEnv
          ? environmentFilterHelper.createCriteriaForGetList(scopeInfo, false, null, null)
          : environmentFilterHelper.createCriteriaForGetList(
                accountIdentifier, orgIdentifier, projectIdentifier, false, null, null);
      environmentList = environmentService.list(criteria, pageRequest)
                            .stream()
                            .map(env
                                -> useScopeInfoForEnv ? EnvironmentMapper.toResponseWrapper(env, scopeInfo)
                                                      : EnvironmentMapper.toResponseWrapper(env))
                            .collect(Collectors.toList());
    }
    List<PermissionCheckDTO> permissionCheckDTOS = environmentList.stream()
                                                       .map(CDNGRbacUtility::environmentResponseToPermissionCheckDTO)
                                                       .collect(Collectors.toList());
    List<AccessControlDTO> accessControlList =
        accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();
    environmentList =
        environmentRbacHelper.filterEnvironmentResponseByPermissionAndId(accessControlList, environmentList);
    List<String> envIds =
        environmentList.stream().map(envRes -> envRes.getEnvironment().getIdentifier()).collect(toList());
    String yaml = getGeneratedEnvironmentSchemaYaml(envIds);

    EnvironmentsSchemaResponseDTO responseDTO = new EnvironmentsSchemaResponseDTO();
    responseDTO.setYaml(yaml);
    responseDTO.setType(TypeEnum.ENVIRONMENTS);
    responseDTO.setEnvGroupId(envGroupId);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithPageInfo =
        ApiUtils.addLinksHeader(responseBuilder, 1, pageRequest.getPageNumber(), pageRequest.getPageSize());
    return responseBuilderWithPageInfo.entity(responseDTO).build();
  }

  private String getGeneratedEnvironmentSchemaYaml(List<String> envIds) {
    String uuid = generateUuid();
    JsonNode jsonNode = UnifiedEntitySchemaUtils.generateSingleInputSection(INPUT_TYPE_STRING, true, envIds);
    return YamlUtils.generateInputsSectionYaml(Map.of(uuid, jsonNode));
  }
}

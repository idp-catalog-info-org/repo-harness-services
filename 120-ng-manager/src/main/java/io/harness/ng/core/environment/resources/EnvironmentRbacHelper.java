/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.CollectionUtils;
import io.harness.ng.core.dto.EntityScopeInfo;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.pms.rbac.NGResourceType;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class EnvironmentRbacHelper {
  @Inject private AccessControlClient accessControlClient;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  private final String TYPE = "type";
  public List<Environment> getPermittedEnvironmentsList(List<Environment> environments) {
    // This method assumes that all environments are at the same scope
    if (isEmpty(environments)) {
      return Collections.emptyList();
    }

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolutionHelper.getScopeInfos(
        environments.get(0).getAccountId(), environments.stream().map(Environment::getParentUniqueId).toList());

    Map<EntityScopeInfo, Environment> environmentMap = environments.stream().collect(Collectors.toMap(environment -> {
      ScopeInfo scopeInfo = scopeInfoMap.get(environment.getParentUniqueId()).get();
      return EnvironmentRbacHelper.getEntityScopeInfoFromEnvironment(environment, scopeInfo);
    }, Function.identity()));

    final List<PermissionCheckDTO> permissionChecks =
        environments.stream()
            .map(environment -> {
              ScopeInfo scopeInfo = scopeInfoMap.get(environment.getParentUniqueId()).get();
              return PermissionCheckDTO.builder()
                  .permission(ENVIRONMENT_VIEW_PERMISSION)
                  .resourceIdentifier(environment.getIdentifier())
                  .resourceScope(ResourceScope.of(
                      scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()))
                  .resourceType(NGResourceType.ENVIRONMENT)
                  .build();
            })
            .collect(Collectors.toList());

    ScopeInfo firstEnvironmentScopeInfo = scopeInfoMap.get(environments.get(0).getParentUniqueId()).get();

    // pre-prod permission check
    permissionChecks.add(
        PermissionCheckDTO.builder()
            .permission(ENVIRONMENT_VIEW_PERMISSION)
            .resourceAttributes(getEnvironmentAttributesMap(EnvironmentType.PreProduction.name()))
            .resourceScope(ResourceScope.of(firstEnvironmentScopeInfo.getAccountIdentifier(),
                firstEnvironmentScopeInfo.getOrgIdentifier(), firstEnvironmentScopeInfo.getProjectIdentifier()))
            .resourceType(NGResourceType.ENVIRONMENT)
            .build());

    // Prod permission check
    permissionChecks.add(
        PermissionCheckDTO.builder()
            .permission(ENVIRONMENT_VIEW_PERMISSION)
            .resourceAttributes(getEnvironmentAttributesMap(EnvironmentType.Production.name()))
            .resourceScope(ResourceScope.of(firstEnvironmentScopeInfo.getAccountIdentifier(),
                firstEnvironmentScopeInfo.getOrgIdentifier(), firstEnvironmentScopeInfo.getProjectIdentifier()))
            .resourceType(NGResourceType.ENVIRONMENT)
            .build());
    final AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);

    final EnvironmentTypeFilteredResponse environmentTypeFilteredResponse =
        checkingTypeBasedFilters(accessCheckResponse.getAccessControlList());
    final List<AccessControlDTO> onlyIdentifierBasedAccessCheckList =
        removeTypeBasedAccessControlDTOs(accessCheckResponse.getAccessControlList());

    final boolean hasPreProdAccess = environmentTypeFilteredResponse.hasPreProdAccess;
    final boolean hasProdAccess = environmentTypeFilteredResponse.hasProdAccess;

    List<Environment> permittedEnvironments = new ArrayList<>();

    for (AccessControlDTO accessControlDTO : onlyIdentifierBasedAccessCheckList) {
      Environment environment =
          environmentMap.get(EnvironmentRbacHelper.getEntityScopeInfoFromAccessControlDTO(accessControlDTO));

      if (environment == null) {
        continue;
      }

      if (accessControlDTO.isPermitted()
          || (EnvironmentType.PreProduction.name().equals(environment.getType().name()) && hasPreProdAccess)
          || (EnvironmentType.Production.name().equals(environment.getType().name()) && hasProdAccess)) {
        permittedEnvironments.add(environment);
      }
    }

    return permittedEnvironments;
  }

  /*
  This method simply takes a list of environments and returns those environments for which the rbac permission is there
  The older method getPermittedEnvironmentsList also had the same functionality but it used to handle access control for
  environments types (Prod & PreProd) separately which is not required here.
   */
  public List<Environment> getPermittedEnvironmentsListV2(
      List<Environment> environments, String environmentRBACPermission) {
    if (isEmpty(environments)) {
      return Collections.emptyList();
    }

    Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeResolutionHelper.getScopeInfos(
        environments.get(0).getAccountId(), environments.stream().map(Environment::getParentUniqueId).toList());

    Map<EntityScopeInfo, Environment> environmentMap = environments.stream().collect(Collectors.toMap(environment -> {
      ScopeInfo scopeInfo = scopeInfoMap.get(environment.getParentUniqueId()).orElse(null);
      return EnvironmentRbacHelper.getEntityScopeInfoFromEnvironment(environment, scopeInfo);
    }, Function.identity()));

    List<PermissionCheckDTO> permissionChecks = new ArrayList<>();
    for (Environment environment : environments) {
      Optional<ScopeInfo> scopeInfoOptional = scopeInfoMap.get(environment.getParentUniqueId());
      boolean useScopeInfo = scopeInfoOptional != null && scopeInfoOptional.isPresent();
      if (useScopeInfo) {
        ScopeInfo scopeInfo = scopeInfoOptional.get();
        permissionChecks.add(PermissionCheckDTO.builder()
                                 .permission(environmentRBACPermission)
                                 .resourceIdentifier(environment.getIdentifier())
                                 .resourceScope(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                     scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()))
                                 .resourceType(NGResourceType.ENVIRONMENT)
                                 .build());
      } else {
        permissionChecks.add(PermissionCheckDTO.builder()
                                 .permission(environmentRBACPermission)
                                 .resourceIdentifier(environment.getIdentifier())
                                 .resourceScope(ResourceScope.of(environment.getAccountId(),
                                     environment.getOrgIdentifier(), environment.getProjectIdentifier()))
                                 .resourceType(NGResourceType.ENVIRONMENT)
                                 .build());
      }
    }

    final AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);
    List<Environment> permittedEnvironments = new ArrayList<>();

    for (AccessControlDTO accessControlDTO : accessCheckResponse.getAccessControlList()) {
      Environment environment =
          environmentMap.get(EnvironmentRbacHelper.getEntityScopeInfoFromAccessControlDTO(accessControlDTO));
      if (environment == null) {
        continue;
      }
      if (accessControlDTO.isPermitted()) {
        permittedEnvironments.add(environment);
      }
    }

    return permittedEnvironments;
  }

  private List<AccessControlDTO> removeTypeBasedAccessControlDTOs(List<AccessControlDTO> accessControlDTOList) {
    return accessControlDTOList.stream()
        .filter(dto -> isEmpty(dto.getResourceAttributes()) || isEmpty(dto.getResourceAttributes().get(TYPE)))
        .collect(Collectors.toList());
  }

  private EnvironmentTypeFilteredResponse checkingTypeBasedFilters(List<AccessControlDTO> accessControlDTOList) {
    boolean hasPreProdAccess = false;
    boolean hasProdAccess = false;

    for (AccessControlDTO accessControlDTO : accessControlDTOList) {
      if (accessControlDTO.isPermitted() && accessControlDTO.getResourceAttributes() != null) {
        if (EnvironmentType.PreProduction.name().equals(accessControlDTO.getResourceAttributes().get(TYPE))) {
          hasPreProdAccess = true;
        } else if (EnvironmentType.Production.name().equals(accessControlDTO.getResourceAttributes().get(TYPE))) {
          hasProdAccess = true;
        }
      }
    }
    return new EnvironmentTypeFilteredResponse(hasPreProdAccess, hasProdAccess);
  }

  private static EntityScopeInfo getEntityScopeInfoFromEnvironment(Environment environmentEntity, ScopeInfo scopeInfo) {
    return EntityScopeInfo.builder()
        .accountIdentifier(environmentEntity.getAccountId())
        .orgIdentifier(isBlank(environmentEntity.getOrgIdentifier()) ? null : scopeInfo.getOrgIdentifier())
        .projectIdentifier(isBlank(environmentEntity.getProjectIdentifier()) ? null : scopeInfo.getProjectIdentifier())
        .identifier(environmentEntity.getIdentifier())
        .build();
  }

  private static EntityScopeInfo getEntityScopeInfoFromAccessControlDTO(AccessControlDTO accessControlDTO) {
    return EntityScopeInfo.builder()
        .accountIdentifier(accessControlDTO.getResourceScope().getAccountIdentifier())
        .orgIdentifier(isBlank(accessControlDTO.getResourceScope().getOrgIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getOrgIdentifier())
        .projectIdentifier(isBlank(accessControlDTO.getResourceScope().getProjectIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getProjectIdentifier())
        .identifier(accessControlDTO.getResourceIdentifier())
        .build();
  }
  public Map<String, String> getEnvironmentAttributesMap(String environmentType) {
    Map<String, String> environmentAttributes = new HashMap<>();
    environmentAttributes.put(TYPE, environmentType);
    return environmentAttributes;
  }

  public void checkForAccessOrThrow(
      Map<String, String> environmentAttributes, ResourceScope resourceScope, String identifier, String permission) {
    List<PermissionCheckDTO> permissionChecks = new ArrayList<>();
    permissionChecks.add(PermissionCheckDTO.builder()
                             .permission(permission)
                             .resourceIdentifier(identifier)
                             .resourceScope(resourceScope)
                             .resourceType(ENVIRONMENT)
                             .build());

    if (isNotEmpty(environmentAttributes)) {
      permissionChecks.add(PermissionCheckDTO.builder()
                               .permission(permission)
                               .resourceAttributes(environmentAttributes)
                               .resourceScope(resourceScope)
                               .resourceType(ENVIRONMENT)
                               .build());
    }

    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);
    List<AccessControlDTO> accessControlDTOList = accessCheckResponse.getAccessControlList();

    final boolean isActionAllowed =
        CollectionUtils.emptyIfNull(accessControlDTOList).stream().anyMatch(AccessControlDTO::isPermitted);
    if (!isActionAllowed) {
      throw new NGAccessDeniedException(
          format("Missing permission %s on %s with identifier %s", permission, ENVIRONMENT, identifier), USER,
          permissionChecks);
    }
  }

  public boolean hasRequiredPermissionForAllEnvironments(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRBACPermission) {
    return accessControlClient.hasAccess(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, null), environmentRBACPermission);
  }

  public List<EnvironmentResponse> filterEnvironmentResponseByPermissionAndId(
      List<AccessControlDTO> accessControlList, List<EnvironmentResponse> environmentList) {
    List<EnvironmentResponse> filteredAccessControlDtoList = new ArrayList<>();
    for (int i = 0; i < accessControlList.size(); i++) {
      AccessControlDTO accessControlDTO = accessControlList.get(i);
      EnvironmentResponse environmentResponse = environmentList.get(i);
      if (accessControlDTO.isPermitted()
          && environmentResponse.getEnvironment().getIdentifier().equals(accessControlDTO.getResourceIdentifier())) {
        filteredAccessControlDtoList.add(environmentResponse);
      }
    }
    return filteredAccessControlDtoList;
  }
}

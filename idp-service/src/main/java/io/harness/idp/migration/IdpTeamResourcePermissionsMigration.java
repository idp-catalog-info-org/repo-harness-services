/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.common.RbacConstants.IDP_CATALOG;
import static io.harness.idp.common.RbacConstants.IDP_CATALOG_DELETE;
import static io.harness.idp.common.RbacConstants.IDP_CATALOG_EDIT;
import static io.harness.idp.common.RbacConstants.IDP_CATALOG_VIEW;
import static io.harness.idp.common.RbacConstants.IDP_TEAM;
import static io.harness.idp.common.RbacConstants.IDP_TEAM_DELETE;
import static io.harness.idp.common.RbacConstants.IDP_TEAM_EDIT;
import static io.harness.idp.common.RbacConstants.IDP_TEAM_VIEW;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ResourceSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupRequest;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.resourcegroupclient.remote.ResourceGroupClient;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpTeamResourcePermissionsMigration implements NGMigration {
  private static final String MIGRATION_PURPOSE = "idp team resource permissions";

  private final NamespaceService namespaceService;
  private final AccessControlAdminClient accessControlAdminClient;
  private final ResourceGroupClient resourceGroupClient;
  private final OrganizationClient organizationClient;
  private final ProjectClient projectClient;

  @Inject
  private IdpTeamResourcePermissionsMigration(NamespaceService namespaceService,
      @Named("PRIVILEGED") ResourceGroupClient resourceGroupClient,
      @Named("PRIVILEGED") AccessControlAdminClient accessControlAdminClient,
      @Named("PRIVILEGED") OrganizationClient organizationClient, @Named("PRIVILEGED") ProjectClient projectClient) {
    this.namespaceService = namespaceService;
    this.resourceGroupClient = resourceGroupClient;
    this.accessControlAdminClient = accessControlAdminClient;
    this.organizationClient = organizationClient;
    this.projectClient = projectClient;
  }

  @Override
  public void migrate() {
    log.info("Starting the migration for {}.", MIGRATION_PURPOSE);
    List<String> accountIdentifiers = namespaceService.getAccountIds();
    log.info("Fetched total of {} NG Accounts", accountIdentifiers.size());
    accountIdentifiers.forEach(accountIdentifier -> {
      log.info("Migrating {} for accountIdentifier = {}", MIGRATION_PURPOSE, accountIdentifier);
      rolesAndResourceGroupsUpdate(accountIdentifier);
      log.info("Migrated {} for accountIdentifier = {}", MIGRATION_PURPOSE, accountIdentifier);
    });
    log.info("Completed the migration for {}.", MIGRATION_PURPOSE);
  }

  private void rolesAndResourceGroupsUpdate(String accountIdentifier) {
    List<OrganizationResponse> organizationResponseList =
        NGRestUtils
            .getResponse(organizationClient.listAllOrganizations(accountIdentifier, Collections.emptyList(), null))
            .getContent();

    List<ProjectDTO> projectDTOList = NGRestUtils.getResponse(projectClient.getProjectList(accountIdentifier, null));

    rolesUpdate(accountIdentifier, organizationResponseList, projectDTOList);
    resourceGroupsUpdate(accountIdentifier, organizationResponseList, projectDTOList);
  }

  private void rolesUpdate(
      String accountIdentifier, List<OrganizationResponse> organizationResponseList, List<ProjectDTO> projectDTOList) {
    rolesUpdateInternal(accountIdentifier, null, null);

    organizationResponseList.forEach(organizationResponse -> {
      OrganizationDTO organizationDTO = organizationResponse.getOrganization();
      rolesUpdateInternal(accountIdentifier, organizationDTO.getIdentifier(), null);
    });

    projectDTOList.forEach(projectDTO
        -> rolesUpdateInternal(accountIdentifier, projectDTO.getOrgIdentifier(), projectDTO.getIdentifier()));
  }

  private void resourceGroupsUpdate(
      String accountIdentifier, List<OrganizationResponse> organizationResponseList, List<ProjectDTO> projectDTOList) {
    resourceGroupsUpdateInternal(accountIdentifier, null, null);

    organizationResponseList.forEach(organizationResponse -> {
      OrganizationDTO organizationDTO = organizationResponse.getOrganization();
      resourceGroupsUpdateInternal(accountIdentifier, organizationDTO.getIdentifier(), null);
    });

    projectDTOList.forEach(projectDTO
        -> resourceGroupsUpdateInternal(accountIdentifier, projectDTO.getOrgIdentifier(), projectDTO.getIdentifier()));
  }

  private void rolesUpdateInternal(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    try {
      PageResponse<RoleResponseDTO> rolesResponse;
      int page = 0;
      do {
        try {
          rolesResponse = getResponse(
              accessControlAdminClient.getRoles(page, 100, accountIdentifier, orgIdentifier, projectIdentifier, null));
        } catch (Exception ex) {
          log.error("Error in fetching roles for accountIdentifier {} orgIdentifier {} projectIdentifier {} | page {}",
              accountIdentifier, orgIdentifier, projectIdentifier, page, ex);
          throw new UnexpectedException("Error in fetching roles for accountIdentifier " + accountIdentifier
              + " orgIdentifier " + orgIdentifier + " projectIdentifier " + projectIdentifier + " | page " + page);
        }
        if (Objects.nonNull(rolesResponse) && isNotEmpty(rolesResponse.getContent())) {
          List<RoleResponseDTO> roleResponseDTOS = rolesResponse.getContent();
          roleResponseDTOS.forEach(roleResponseDTO -> {
            RoleDTO roleDTO = roleResponseDTO.getRole();
            String roleIdentifier = roleDTO.getIdentifier();
            Set<String> permissions = roleDTO.getPermissions();
            if (!roleIdentifier.startsWith("_") && isNotEmpty(permissions)) {
              boolean permissionsModified = false;
              if (permissions.contains(IDP_CATALOG_EDIT)) {
                log.info("Found idp catalog resource edit permission in role = {} account = {} org = {} project = {}, "
                        + "adding idp team edit permission",
                    roleIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
                permissions.add(IDP_TEAM_EDIT);
                permissionsModified = true;
              }
              if (permissions.contains(IDP_CATALOG_DELETE)) {
                log.info("Found idp catalog resource delete permission in role = {} account = {} org = {} project = "
                        + "{}, adding idp team "
                        + "delete permission",
                    roleIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
                permissions.add(IDP_TEAM_DELETE);
                permissionsModified = true;
              }
              if (permissions.contains(IDP_CATALOG_VIEW)) {
                log.info("Found idp catalog resource view permission in role = {} account = {} org = {} project = {}, "
                        + "adding idp team view permission",
                    roleIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
                permissions.add(IDP_TEAM_VIEW);
                permissionsModified = true;
              }

              if (permissionsModified) {
                try {
                  getResponse(accessControlAdminClient.updateRole(
                      roleIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, roleDTO));
                  log.info("Updated role = {} in account = {} org = {} project = {} with idp team resource permissions",
                      roleIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
                } catch (Exception ex) {
                  log.error("Error updating role = {} in account = {} org = {} project = {} with idp team resource "
                          + "permissions",
                      roleIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, ex);
                }
              }
            }
          });
        }
        page++;
      } while (rolesResponse != null && isNotEmpty(rolesResponse.getContent()));
    } catch (Exception ex) {
      log.error("Error in roles update for accountIdentifier = {} orgIdentifier = {} projectIdentifier = {}",
          accountIdentifier, orgIdentifier, projectIdentifier, ex);
    }
  }

  private void resourceGroupsUpdateInternal(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    try {
      PageResponse<ResourceGroupResponse> resourceGroupsResponse;
      int page = 0;
      do {
        try {
          resourceGroupsResponse =
              getResponse(resourceGroupClient.getFilteredResourceGroups(ResourceGroupFilterDTO.builder()
                                                                            .accountIdentifier(accountIdentifier)
                                                                            .orgIdentifier(orgIdentifier)
                                                                            .projectIdentifier(projectIdentifier)
                                                                            .build(),
                  accountIdentifier, page, 100));
        } catch (Exception ex) {
          log.error("Error in fetching resource groups for accountIdentifier {} orgIdentifier {} projectIdentifier {} "
                  + "| page {}",
              accountIdentifier, orgIdentifier, projectIdentifier, page, ex);
          throw new UnexpectedException("Error in fetching resource groups for accountIdentifier " + accountIdentifier
              + " orgIdentifier " + orgIdentifier + " projectIdentifier " + projectIdentifier + " | page " + page);
        }
        if (Objects.nonNull(resourceGroupsResponse) && isNotEmpty(resourceGroupsResponse.getContent())) {
          List<ResourceGroupResponse> resourceGroupResponses = resourceGroupsResponse.getContent();
          resourceGroupResponses.forEach(resourceGroupResponse -> {
            ResourceGroupDTO resourceGroupDTO = resourceGroupResponse.getResourceGroup();
            boolean harnessManaged = resourceGroupResponse.isHarnessManaged();
            ResourceFilter resourceFilter = resourceGroupDTO.getResourceFilter();
            String resourceGroupIdentifier = resourceGroupDTO.getIdentifier();
            List<ResourceSelector> resourceSelectors =
                Objects.nonNull(resourceFilter) ? resourceFilter.getResources() : new ArrayList<>();
            if (isNotEmpty(resourceSelectors)) {
              for (ResourceSelector resourceSelector : resourceSelectors) {
                if (!harnessManaged && resourceSelector.getResourceType().equals(IDP_CATALOG)) {
                  log.info("Found idp_catalog resource in resource group = {} account = {} org = {} project = {}",
                      resourceGroupIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
                  boolean teamResourcePresent =
                      resourceSelectors.stream().anyMatch(rs -> IDP_TEAM.equals(rs.getResourceType()));
                  if (teamResourcePresent) {
                    continue;
                  }
                  List<ResourceSelector> resourceSelectorsUpdated = new ArrayList<>(resourceSelectors);
                  resourceSelectorsUpdated.add(ResourceSelector.builder().resourceType(IDP_TEAM).build());
                  resourceFilter.setResources(resourceSelectorsUpdated);
                  resourceGroupDTO.setResourceFilter(resourceFilter);
                  try {
                    getResponse(resourceGroupClient.updateResourceGroup(resourceGroupDTO.getIdentifier(),
                        accountIdentifier, orgIdentifier, projectIdentifier,
                        ResourceGroupRequest.builder().resourceGroup(resourceGroupDTO).build()));
                    log.info("Updated resource group = {} in account = {} org = {} project = {} with idp team resource",
                        resourceGroupIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
                  } catch (Exception ex) {
                    log.error("Error updating resource group = {} in account = {} org = {} project = {} with idp team "
                            + "resource",
                        resourceGroupIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, ex);
                  }
                }
              }
            }
          });
        }
        page++;
      } while (resourceGroupsResponse != null && isNotEmpty(resourceGroupsResponse.getContent()));
    } catch (Exception ex) {
      log.error("Error in resource groups update for accountIdentifier = {} orgIdentifier = {} projectIdentifier = {}",
          accountIdentifier, orgIdentifier, projectIdentifier, ex);
    }
  }
}

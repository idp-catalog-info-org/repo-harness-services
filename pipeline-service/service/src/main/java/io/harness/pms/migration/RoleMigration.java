/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public abstract class RoleMigration {
  @Inject private ProjectClient projectClient;
  @Inject private OrganizationClient organizationClient;
  @Inject private AccessControlAdminClient accessControlAdminClient;

  abstract Set<String> getAccountsWithFFEnabled();

  void updateRoles(String accountId) {
    log.info("Update roles started for AccountId: " + accountId);
    updateRolesAtAccountLevel(accountId);
    updateRolesAtOrgLevel(accountId);
    updateRolesAtProjectLevel(accountId);
  }

  void updateRolesAtAccountLevel(String accountId) {
    log.info("Update roles started at account level for AccountId: " + accountId);
    List<RoleResponseDTO> rolesResponse;
    for (int rolePageNo = 0; rolePageNo < 100; rolePageNo++) {
      try {
        rolesResponse =
            getResponse(accessControlAdminClient.getRoles(rolePageNo, 100, accountId, null, null, null)).getContent();
        if (EmptyPredicate.isEmpty(rolesResponse)) {
          break;
        }
        updateRole(rolesResponse, accountId, null, null);
      } catch (Exception ex) {
        throw new UnexpectedException(String.format(
            "Error in fetching roles at account level with accountId: %s and rolePageNo: %s ", accountId, rolePageNo));
      }
    }
  }

  void updateRolesAtOrgLevel(String accountId) {
    try {
      List<OrganizationResponse> organizationResponseList =
          getResponse(organizationClient.listAllOrganizations(accountId, Collections.emptyList(), null)).getContent();
      if (EmptyPredicate.isEmpty(organizationResponseList)) {
        return;
      }
      for (OrganizationResponse organizationResponse : organizationResponseList) {
        updateRolesForOrg(accountId, organizationResponse.getOrganization().getIdentifier());
      }
    } catch (Exception ex) {
      throw new UnexpectedException("Error in fetching organizations of accountId: " + accountId);
    }
  }

  void updateRolesForOrg(String accountId, String orgId) {
    log.info(String.format("Update roles started at org level for AccountId: %s and orgId: %s", accountId, orgId));
    List<RoleResponseDTO> rolesResponse;
    for (int rolePageNo = 0; rolePageNo < 100; rolePageNo++) {
      try {
        rolesResponse =
            getResponse(accessControlAdminClient.getRoles(rolePageNo, 100, accountId, orgId, null, null)).getContent();
        if (EmptyPredicate.isEmpty(rolesResponse)) {
          break;
        }
        updateRole(rolesResponse, accountId, orgId, null);
      } catch (Exception ex) {
        throw new UnexpectedException(
            String.format("Error in fetching roles at org level for accountId %s, orgId %s and rolePageNo %s",
                accountId, orgId, rolePageNo));
      }
    }
  }

  void updateRolesAtProjectLevel(String accountId) {
    List<ProjectResponse> projects;
    for (int pageNo = 0; pageNo < 100; pageNo++) {
      try {
        projects =
            getResponse(projectClient.listProject(accountId, null, false, null, null, pageNo, 100, null)).getContent();
        if (EmptyPredicate.isEmpty(projects)) {
          break;
        }
        for (ProjectResponse projectResponse : projects) {
          updateRolesForProject(accountId, pageNo, projectResponse);
        }
      } catch (Exception ex) {
        throw new UnexpectedException(
            String.format("Error in fetching projects list for accountId %s and pageNo %s", accountId, pageNo));
      }
    }
  }

  void updateRolesForProject(String accountId, int projectPageNo, ProjectResponse projectResponse) {
    log.info(String.format("Update roles started at project level for AccountId: %s , orgId: %s and projectId: %s",
        accountId, projectResponse.getProject().getOrgIdentifier(), projectResponse.getProject().getIdentifier()));
    List<RoleResponseDTO> rolesResponse;
    for (int rolePageNo = 0; rolePageNo < 100; rolePageNo++) {
      try {
        rolesResponse = getResponse(
            accessControlAdminClient.getRoles(rolePageNo, 100, accountId,
                projectResponse.getProject().getOrgIdentifier(), projectResponse.getProject().getIdentifier(), null))
                            .getContent();
        if (EmptyPredicate.isEmpty(rolesResponse)) {
          break;
        }
        updateRole(rolesResponse, accountId, projectResponse.getProject().getOrgIdentifier(),
            projectResponse.getProject().getIdentifier());
      } catch (Exception ex) {
        throw new UnexpectedException(String.format(
            "Error in fetching roles at project level for accountId %s, orgId %s , projectId %s , projectPageNo %s and rolePageNo %s",
            accountId, projectResponse.getProject().getOrgIdentifier(), projectResponse.getProject().getIdentifier(),
            projectPageNo, rolePageNo));
      }
    }
  }

  void updateRole(List<RoleResponseDTO> rolesResponse, String accountId, String orgId, String projectId) {
    for (int i = 0; i < rolesResponse.size(); i++) {
      RoleDTO roleDTO = rolesResponse.get(i).getRole();
      int permissionSize = roleDTO.getPermissions().size();
      roleDTO = updateRoleDto(roleDTO);
      try {
        if (permissionSize != roleDTO.getPermissions().size()) {
          getResponse(
              accessControlAdminClient.updateRole(roleDTO.getIdentifier(), accountId, orgId, projectId, roleDTO));
          log.info("role with identifier " + roleDTO.getIdentifier() + " got updated");
        }
      } catch (Exception ex) {
        throw new UnexpectedException(String.format("Update Role failed for %s", roleDTO.getIdentifier()));
      }
    }
  }

  abstract RoleDTO updateRoleDto(RoleDTO roleDTO);
}

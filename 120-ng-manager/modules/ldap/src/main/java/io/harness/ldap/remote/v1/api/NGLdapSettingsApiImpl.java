/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.remote.v1.api;

import static io.harness.ng.accesscontrol.PlatformPermissions.DELETE_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.AUTHSETTING;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USERGROUP;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NoResultFoundException;
import io.harness.ldap.entity.NGLdapSettings;
import io.harness.ldap.mapper.NgLdapSettingsMapper;
import io.harness.ldap.service.NGLdapSettingsService;
import io.harness.ng.accesscontrol.usergroup.UserGroupPermissionUtils;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.spec.server.ng.v1.LdapSettingsApi;
import io.harness.spec.server.ng.v1.model.CronExpressionRequestDTO;
import io.harness.spec.server.ng.v1.model.LdapSettingsIterations;
import io.harness.spec.server.ng.v1.model.LdapSettingsRequest;
import io.harness.spec.server.ng.v1.model.LdapSettingsResponse;
import io.harness.spec.server.ng.v1.model.LdapTestLoginRequestDTO;
import io.harness.spec.server.ng.v1.model.LinkSSOGroupRequestDTO;
import io.harness.spec.server.ng.v1.model.UnlinkSSOGroupRequestDTO;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.SSOType;
import software.wings.helpers.ext.ldap.LdapResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PL)
@Timed
@ResponseMetered
public class NGLdapSettingsApiImpl implements LdapSettingsApi {
  private final AccessControlClient accessControlClient;
  private final NGLdapSettingsService ngLdapSettingsService;
  private final NgLdapSettingsMapper ngLdapSettingsMapper;
  private final UserGroupService userGroupService;
  private final ScopeInfoService scopeInfoService;
  private final UserGroupPermissionUtils userGroupPermissionUtils;

  @Override
  public Response createNgLdapSettings(@Valid LdapSettingsRequest body, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    try {
      NGLdapSettings ngLdapSettings =
          ngLdapSettingsService.create(ngLdapSettingsMapper.ngLdapSettings(body.getLdapSettings()));
      LdapSettingsResponse ldapSettingsResponse =
          new LdapSettingsResponse().ldapSettings(ngLdapSettingsMapper.toLdapSettingsDTO(ngLdapSettings));
      return Response.status(Response.Status.CREATED).entity(ldapSettingsResponse).build();
    } catch (Exception e) {
      log.error("Exception while creating NG ldap settings", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getLdapSettings(String identifier, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(AUTHSETTING, harnessAccount), VIEW_AUTHSETTING_PERMISSION);
    NGLdapSettings ngLdapSettings = ngLdapSettingsService.get(harnessAccount);
    LdapSettingsResponse ldapSettingsResponse =
        new LdapSettingsResponse().ldapSettings(ngLdapSettingsMapper.toLdapSettingsDTO(ngLdapSettings));
    return Response.status(Response.Status.OK).entity(ldapSettingsResponse).build();
  }

  @Override
  public Response ldapSettingsLdapLoginTest(@Valid LdapTestLoginRequestDTO body, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    try {
      LdapResponse ldapTestResponse = ngLdapSettingsService.testLDAPLogin(ScopeInfo.builder()
                                                                              .accountIdentifier(harnessAccount)
                                                                              .uniqueId(harnessAccount)
                                                                              .scopeType(ScopeLevel.ACCOUNT)
                                                                              .build(),
          body.getEmail(), body.getPassword());
      return Response.status(Response.Status.OK).entity(ldapTestResponse).build();
    } catch (Exception e) {
      log.error("Exception while validating connection settings for NG ldap settings", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response ldapSettingsIterations(@Valid CronExpressionRequestDTO body, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(AUTHSETTING, harnessAccount), VIEW_AUTHSETTING_PERMISSION);
    List<Long> iterations = ngLdapSettingsService.getIterationsFromCron(harnessAccount, body.getCron());
    LdapSettingsIterations ldapSettingsIterations = new LdapSettingsIterations().iterations(iterations);
    return Response.status(Response.Status.OK).entity(ldapSettingsIterations).build();
  }

  @Override
  public Response updateLdapSettings(String identifier, @Valid LdapSettingsRequest body, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    try {
      NGLdapSettings ngLdapSettings =
          ngLdapSettingsService.update(ngLdapSettingsMapper.ngLdapSettings(body.getLdapSettings()), harnessAccount);
      LdapSettingsResponse ldapSettingsResponse =
          new LdapSettingsResponse().ldapSettings(ngLdapSettingsMapper.toLdapSettingsDTO(ngLdapSettings));
      return Response.status(Response.Status.OK).entity(ldapSettingsResponse).build();
    } catch (Exception e) {
      log.error("Exception while creating NG ldap settings", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response deleteLdapSettings(String identifier, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), DELETE_AUTHSETTING_PERMISSION);
    try {
      boolean success = ngLdapSettingsService.delete(harnessAccount);
      return Response.status(success ? Response.Status.OK : Response.Status.BAD_REQUEST).build();
    } catch (NoResultFoundException e) {
      log.error("Exception while deleting LDAP Settings", e);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (InvalidRequestException | IllegalArgumentException e) {
      log.error("Exception while deleting LDAP Settings", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
  }

  @Override
  public Response getAccountLdapSettings(String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(AUTHSETTING, harnessAccount), VIEW_AUTHSETTING_PERMISSION);
    NGLdapSettings ngLdapSettings = ngLdapSettingsService.get(harnessAccount);
    LdapSettingsResponse ldapSettingsResponse =
        new LdapSettingsResponse().ldapSettings(ngLdapSettingsMapper.toLdapSettingsDTO(ngLdapSettings));
    return Response.status(Response.Status.OK).entity(ldapSettingsResponse).build();
  }

  @Override
  public Response ldapSettingsSyncGroups(String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(USERGROUP, null), userGroupPermissionUtils.getUserGroupManagePermissionForSSO(harnessAccount));
    try {
      ngLdapSettingsService.syncUserGroupsJob(harnessAccount);
      return Response.status(Response.Status.OK).build();
    } catch (Exception e) {
      log.error("Exception on NG ldap settings sync groups", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response linkLdapSettings(String groupId, @Valid LinkSSOGroupRequestDTO body, String harnessAccount) {
    try {
      ScopeInfo scopeInfo = ScopeInfo.builder()
                                .accountIdentifier(harnessAccount)
                                .uniqueId(harnessAccount)
                                .scopeType(ScopeLevel.ACCOUNT)
                                .build();
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(), null, null),
          Resource.of(USERGROUP, groupId), userGroupPermissionUtils.getUserGroupManagePermissionForSSO(harnessAccount));
      checkExternallyManaged(scopeInfo, groupId);
      UserGroup userGroup = ngLdapSettingsService.linkToSsoGroup(
          scopeInfo, groupId, SSOType.LDAP, body.getSsoId(), body.getSsoGroupId(), body.getSsoGroupName());
      return Response.status(Response.Status.OK).entity(userGroup).build();
    } catch (Exception e) {
      log.error("Exception on NG ldap settings sync groups", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response linkLdapSettingsOrg(
      String org, String groupId, @Valid LinkSSOGroupRequestDTO body, String harnessAccount) {
    try {
      ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, org, null);
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), null),
          Resource.of(USERGROUP, groupId), userGroupPermissionUtils.getUserGroupManagePermissionForSSO(harnessAccount));
      UserGroup userGroup = ngLdapSettingsService.linkToSsoGroup(
          scopeInfo, groupId, SSOType.LDAP, body.getSsoId(), body.getSsoGroupId(), body.getSsoGroupName());
      return Response.status(Response.Status.OK).entity(userGroup).build();
    } catch (Exception e) {
      log.error("Exception on NG ldap settings sync groups", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response linkLdapSettingsProj(
      String org, String project, String groupId, @Valid LinkSSOGroupRequestDTO body, String harnessAccount) {
    try {
      ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, org, project);
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                    scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
          Resource.of(USERGROUP, groupId), userGroupPermissionUtils.getUserGroupManagePermissionForSSO(harnessAccount));
      checkExternallyManaged(scopeInfo, groupId);
      UserGroup userGroup = ngLdapSettingsService.linkToSsoGroup(
          scopeInfo, groupId, SSOType.LDAP, body.getSsoId(), body.getSsoGroupId(), body.getSsoGroupName());
      return Response.status(Response.Status.OK).entity(userGroup).build();
    } catch (Exception e) {
      log.error("Exception on NG ldap settings sync groups", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response unlinkLdapSettings(String groupId, @Valid UnlinkSSOGroupRequestDTO body, String harnessAccount) {
    try {
      ScopeInfo scopeInfo = ScopeInfo.builder()
                                .accountIdentifier(harnessAccount)
                                .uniqueId(harnessAccount)
                                .scopeType(ScopeLevel.ACCOUNT)
                                .build();
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(), null, null),
          Resource.of(USERGROUP, groupId), userGroupPermissionUtils.getUserGroupManagePermissionForSSO(harnessAccount));
      checkExternallyManaged(scopeInfo, groupId);
      UserGroup userGroup = userGroupService.unlinkSsoGroup(scopeInfo, groupId, body.isRetainMembers());
      return Response.status(Response.Status.OK).entity(userGroup).build();
    } catch (Exception e) {
      log.error("Exception on NG ldap settings sync groups", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response unlinkLdapSettingsOrg(
      String org, String groupId, @Valid UnlinkSSOGroupRequestDTO body, String harnessAccount) {
    try {
      ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, org, null);
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), null),
          Resource.of(USERGROUP, groupId), userGroupPermissionUtils.getUserGroupManagePermissionForSSO(harnessAccount));
      checkExternallyManaged(scopeInfo, groupId);
      UserGroup userGroup = userGroupService.unlinkSsoGroup(scopeInfo, groupId, body.isRetainMembers());
      return Response.status(Response.Status.OK).entity(userGroup).build();
    } catch (Exception e) {
      log.error("Exception on NG ldap settings sync groups", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response unlinkLdapSettingsProj(
      String org, String project, String groupId, @Valid UnlinkSSOGroupRequestDTO body, String harnessAccount) {
    try {
      ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(harnessAccount, org, project);
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                    scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
          Resource.of(USERGROUP, groupId), userGroupPermissionUtils.getUserGroupManagePermissionForSSO(harnessAccount));
      checkExternallyManaged(scopeInfo, groupId);
      UserGroup userGroup = userGroupService.unlinkSsoGroup(scopeInfo, groupId, body.isRetainMembers());
      return Response.status(Response.Status.OK).entity(userGroup).build();
    } catch (Exception e) {
      log.error("Exception on NG ldap settings sync groups", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response ldapSettingsSyncGroupWithId(String userGroupId, String harnessAccount) {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(harnessAccount)
                              .uniqueId(harnessAccount)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .build();
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(), null, null),
        Resource.of(USERGROUP, userGroupId),
        userGroupPermissionUtils.getUserGroupManagePermissionForSSO(harnessAccount));
    try {
      ngLdapSettingsService.syncUserGroupWithGroupId(scopeInfo, userGroupId);
      return Response.status(Response.Status.OK).entity("Sync successful").build();
    } catch (Exception e) {
      log.error("Exception on group sync with user group id ", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response validateConnectionSettings(@Valid LdapSettingsRequest body, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    try {
      LdapTestResponse ldapTestResponse = ngLdapSettingsService.validateLdapConnectionSettings(
          harnessAccount, ngLdapSettingsMapper.ngLdapSettings(body.getLdapSettings()));
      return Response.status(Response.Status.OK).entity(ldapTestResponse).build();
    } catch (Exception e) {
      log.error("Exception while validating connection settings for NG ldap settings", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response validateGroupSettings(@Valid LdapSettingsRequest body, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    try {
      LdapTestResponse ldapTestResponse = ngLdapSettingsService.validateLdapGroupSettings(
          harnessAccount, ngLdapSettingsMapper.ngLdapSettings(body.getLdapSettings()));
      return Response.status(Response.Status.OK).entity(ldapTestResponse).build();
    } catch (Exception e) {
      log.error("Exception while validating connection settings for NG ldap settings", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response validateUserSettings(@Valid LdapSettingsRequest body, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(harnessAccount, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    try {
      LdapTestResponse ldapTestResponse = ngLdapSettingsService.validateLdapUserSettings(
          harnessAccount, ngLdapSettingsMapper.ngLdapSettings(body.getLdapSettings()));
      return Response.status(Response.Status.OK).entity(ldapTestResponse).build();
    } catch (Exception e) {
      log.error("Exception while validating connection settings for NG ldap settings", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response ldapSettingsSearchGroup(String groupId, String harnessAccount) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, null, null),
        Resource.of(USERGROUP, groupId), userGroupPermissionUtils.getUserGroupManagePermissionForSSO(harnessAccount));
    try {
      Collection<LdapGroupResponse> responseCollection =
          ngLdapSettingsService.searchLdapGroupsByName(harnessAccount, groupId);
      return Response.status(Response.Status.OK).entity(responseCollection).build();
    } catch (Exception e) {
      log.error("Exception on group sync with user group id ", e);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  private void checkExternallyManaged(ScopeInfo scopeInfo, String identifier) {
    if (userGroupService.isExternallyManaged(scopeInfo, identifier)) {
      throw new InvalidRequestException("This API call is not supported for externally managed group" + identifier);
    }
  }
}

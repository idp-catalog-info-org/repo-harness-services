/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.invites.remote;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_USER_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USER;
import static io.harness.ng.core.invites.mapper.InviteMapper.writeDTO;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.beans.SortOrder;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.invites.remote.InviteAcceptResponse;
import io.harness.ng.accesscontrol.user.ACLAggregateFilter;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.invites.dto.InviteDTO;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.ng.core.invites.entities.Invite.InviteKeys;
import io.harness.ng.core.invites.mapper.InviteMapper;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.utils.PmsFeatureFlagHelper;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@Api("invites")
@Path("invites")
@Produces({"application/json", "text/yaml"})
@Consumes({"application/json", "text/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Invite", description = "This contains APIs related to Invite as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })

@NextGenManagerAuth
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class InviteResource {
  private final InviteService inviteService;
  private final AccessControlClient accessControlClient;
  private final NgUserService ngUserService;
  private final ScopeInfoService scopeInfoService;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Inject
  InviteResource(InviteService inviteService, AccessControlClient accessControlClient, NgUserService ngUserService,
      ScopeInfoService scopeInfoService, PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    this.inviteService = inviteService;
    this.accessControlClient = accessControlClient;
    this.ngUserService = ngUserService;
    this.scopeInfoService = scopeInfoService;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
  }

  @GET
  @Path("invite")
  @ApiOperation(value = "Get invite", nickname = "getInvite")
  @Operation(operationId = "getInvite", summary = "Get Invite",
      description = "Gets an Invite by either Invite Id or JwtToken",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the Invite having"
                + " either InviteId or JwtToken as specified in request")
      })
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<InviteDTO>
  getInviteWithToken(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = "Invitation Id") @QueryParam("inviteId") String inviteId,
      @Parameter(description = "JWT Token") @QueryParam("jwttoken") String jwtToken) {
    if ((isBlank(inviteId) && isBlank(jwtToken)) || (!isBlank(inviteId) && !isBlank(jwtToken))) {
      throw new InvalidRequestException("Specify either inviteId or jwtToken");
    }
    Optional<Invite> inviteOpt = Optional.empty();
    if (!isBlank(inviteId)) {
      inviteOpt = inviteService.getInvite(inviteId, false);
    } else if (!isBlank(jwtToken)) {
      inviteOpt = inviteService.getInviteFromToken(jwtToken, false);
    }
    if (!inviteOpt.isPresent()) {
      return ResponseDTO.newResponse(null);
    }

    Invite invite = inviteOpt.get();

    ScopeInfo scopeInfo =
        scopeInfoService.getScopeInfo(invite.getAccountIdentifier(), Collections.singleton(invite.getParentUniqueId()))
            .get(invite.getParentUniqueId())
            .orElseThrow();

    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(invite.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of("USER", null), VIEW_USER_PERMISSION);

    return ResponseDTO.newResponse(writeDTO(invite, scopeInfo));
  }

  @GET
  @ApiOperation(value = "Get all invites for the queried project/organization", nickname = "getInvites")
  @Operation(operationId = "getInvites", summary = "List Invites",
      description = "List all the Invites for a Project or Organization",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Paginated list of Invites")
      })
  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = USER, permission = VIEW_USER_PERMISSION)
  public ResponseDTO<PageResponse<InviteDTO>>
  getInvites(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @NotNull @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam("orgIdentifier") @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam("projectIdentifier")
      @ProjectIdentifier String projectIdentifier, @BeanParam PageRequest pageRequest, @Context ScopeInfo scopeInfo) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(InviteKeys.createdAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    Criteria criteria = Criteria.where(InviteKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(InviteKeys.parentUniqueId)
                            .is(scopeInfo.getUniqueId())
                            .and(InviteKeys.approved)
                            .is(Boolean.FALSE)
                            .and(InviteKeys.deleted)
                            .is(Boolean.FALSE);

    PageResponse<InviteDTO> invites =
        inviteService.getInvites(criteria, pageRequest).map(invite -> writeDTO(invite, scopeInfo));
    return ResponseDTO.newResponse(invites);
  }

  @POST
  @Path("aggregate")
  @ApiOperation(value = "Get a page of pending users for access control", nickname = "getPendingUsersAggregated")
  @Operation(operationId = "getPendingUsersAggregated", summary = "Get pending users",
      description = "List of all the pending users in a scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Paginated list of Pending Invites")
      })
  @NGAccessControlCheck(resourceType = USER, permission = VIEW_USER_PERMISSION)
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<InviteDTO>>
  getPendingInvites(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                        NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Search term") @QueryParam("searchTerm") String searchTerm,
      @BeanParam PageRequest pageRequest, ACLAggregateFilter aclAggregateFilter, @Context ScopeInfo scopeInfo) {
    PageResponse<InviteDTO> inviteDTOs =
        inviteService.getPendingInvites(scopeInfo, searchTerm, pageRequest, aclAggregateFilter);
    return ResponseDTO.newResponse(inviteDTOs);
  }

  @GET
  @Hidden
  @Path("internal/link")
  @ApiOperation(value = "Get invite link from invite id for Harness User Group Users",
      nickname = "getInviteLinkInternal", hidden = true)
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<String>
  getInviteLink(@QueryParam("inviteId") @NotNull String inviteId,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier) {
    boolean isHarnessSupportGroupUser = ngUserService.verifyHarnessSupportGroupUser();
    if (!isHarnessSupportGroupUser) {
      throw new AccessDeniedException("Only Harness Support Group Users can access this endpoint. Not authorized",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
    return ResponseDTO.newResponse(inviteService.getInviteLinkFromInviteId(inviteId));
  }

  @GET
  @Hidden
  @Path("accept")
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Verify user invite", nickname = "verifyInvite", hidden = true)
  public ResponseDTO<InviteAcceptResponse> accept(@QueryParam("token") @NotNull String jwtToken) {
    return ResponseDTO.newResponse(inviteService.acceptInvite(jwtToken));
  }

  @GET
  @Hidden
  @Path("verify")
  @ApiOperation(
      value = "Verify user invite with the new NG Auth UI flow", nickname = "verifyInviteViaNGAuthUi", hidden = true)
  @PublicApi
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public Response
  verifyInviteViaNGAuthUi(@QueryParam("token") @NotNull String jwtToken,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @QueryParam("email") @NotNull String email) {
    InviteAcceptResponse inviteAcceptResponse = inviteService.acceptInvite(jwtToken);
    try {
      String decodedEmail = URLDecoder.decode(email, "UTF-8");
      URI redirectURL = inviteService.getRedirectUrl(inviteAcceptResponse, email, decodedEmail, jwtToken);
      return Response.seeOther(redirectURL).build();
    } catch (UnsupportedEncodingException e) {
      throw new InvalidRequestException("Unable to decode email");
    }
  }

  @GET
  @Hidden
  @Path("complete")
  @ApiOperation(value = "Complete user invite", nickname = "completeInvite", hidden = true)
  @Operation(operationId = "completeInvite", summary = "Complete the User Invite",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the boolean status")
      })
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean>
  completeInvite(@Parameter(description = "JWT Token") @QueryParam("token") String token) {
    Optional<Invite> inviteOpt = inviteService.getInviteFromToken(token, false);
    return ResponseDTO.newResponse(inviteService.completeInvite(inviteOpt));
  }

  @POST
  @Hidden
  @Path("complete-jit")
  @ApiOperation(value = "Complete user creation in ng for JIT", nickname = "completeJIT", hidden = true)
  @Operation(operationId = "completeJIT", summary = "Complete user creation in ng for JIT",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the boolean status")
      })
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean>
  completeInvite(@Parameter(description = "email") @QueryParam("email") String email,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    return ResponseDTO.newResponse(inviteService.completeUserNgSetupWithoutInvite(email, accountIdentifier));
  }

  @PUT
  @Path("{inviteId}")
  @ApiOperation(value = "Resend invite mail", nickname = "updateInvite")
  @Operation(operationId = "updateInvite", summary = "Resend invite", description = "Resend the invite email",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Invite")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<Optional<InviteDTO>>
  updateInvite(@Parameter(description = "Invite id") @PathParam("inviteId") @NotNull String inviteId,
      @RequestBody(required = true, description = "Details of the Updated Invite") @NotNull @Valid InviteDTO inviteDTO,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY)
      String accountIdentifier, @Context ScopeInfo scopeInfo) {
    Invite invite = InviteMapper.toInvite(inviteDTO, scopeInfo);
    invite.setId(inviteId);
    Optional<Invite> inviteOptional = inviteService.updateInvite(scopeInfo, invite);

    return ResponseDTO.newResponse(inviteOptional.map(inv -> writeDTO(invite, scopeInfo)));
  }

  @DELETE
  @Path("{inviteId}")
  @ApiOperation(value = "Delete a invite for the specified project/organization", nickname = "deleteInvite")
  @Operation(operationId = "deleteInvite", summary = "Delete Invite", description = "Delete an Invite by Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns either empty value of Deleted Invite")
      })
  @Produces("application/json")
  @Consumes()
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<Optional<InviteDTO>> delete(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY)
      String accountIdentifier, @Parameter(description = "Invite Id") @PathParam("inviteId") @NotNull String inviteId) {
    Optional<Invite> inviteOptional = inviteService.getInvite(inviteId, false);
    if (!inviteOptional.isPresent()) {
      return ResponseDTO.newResponse(null);
    }

    Invite invite = inviteOptional.get();
    ScopeInfo scopeInfo =
        scopeInfoService.getScopeInfo(invite.getAccountIdentifier(), Collections.singleton(invite.getParentUniqueId()))
            .get(invite.getParentUniqueId())
            .orElseThrow();

    inviteService.deleteInvite(inviteOptional.get(), scopeInfo);

    return ResponseDTO.newResponse(inviteOptional.map(inv -> writeDTO(invite, scopeInfo)));
  }
}

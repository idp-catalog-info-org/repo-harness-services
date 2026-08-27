/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.resources;

import static io.harness.idp.common.RbacConstants.IDP_SCORECARD;
import static io.harness.idp.common.RbacConstants.IDP_SCORECARD_DELETE;
import static io.harness.idp.common.RbacConstants.IDP_SCORECARD_EDIT;
import static io.harness.idp.common.RbacConstants.IDP_SCORECARD_VIEW;
import static io.harness.idp.scorecard.tiergroups.service.TierGroupConstants.normalizeIdentifier;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.idp.scorecard.tiergroups.mappers.TierGroupDetailsMapper;
import io.harness.idp.scorecard.tiergroups.service.TierGroupService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.TierGroupsApi;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsRequest;
import io.harness.spec.server.idp.v1.model.TierGroupDetailsResponse;
import io.harness.spec.server.idp.v1.model.TierGroupResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@Slf4j
@Timed
@ResponseMetered
public class TierGroupsApiImpl implements TierGroupsApi {
  private static final String SCORECARD_TIERS_DISABLED_MESSAGE = "Scorecard tiers is not enabled for this account";

  private final TierGroupService tierGroupService;
  private final IdpCommonService idpCommonService;

  @Inject
  public TierGroupsApiImpl(TierGroupService tierGroupService, IdpCommonService idpCommonService) {
    this.tierGroupService = tierGroupService;
    this.idpCommonService = idpCommonService;
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_SCORECARD, permission = IDP_SCORECARD_VIEW)
  public Response getTierGroups(@AccountIdentifier String harnessAccount) {
    Response disabledResponse = scorecardTiersDisabledResponse(harnessAccount);
    if (disabledResponse != null) {
      return disabledResponse;
    }
    try {
      List<TierGroupResponse> tierGroups =
          tierGroupService.getAllTierGroups(harnessAccount)
              .stream()
              .sorted(
                  Comparator.comparing(TierGroupEntity::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
              .map(entity -> new TierGroupResponse().tierGroup(TierGroupDetailsMapper.toListItem(entity)))
              .collect(Collectors.toList());
      return Response.status(Response.Status.OK).entity(tierGroups).build();
    } catch (InvalidRequestException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      log.error("Error fetching tier groups for account {}", harnessAccount, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_SCORECARD, permission = IDP_SCORECARD_VIEW)
  public Response getTierGroup(String tierGroupId, @AccountIdentifier String harnessAccount) {
    Response disabledResponse = scorecardTiersDisabledResponse(harnessAccount);
    if (disabledResponse != null) {
      return disabledResponse;
    }
    try {
      TierGroupDetailsResponse response = tierGroupService.getTierGroupDetails(harnessAccount, tierGroupId);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception e) {
      log.error("Error fetching tier group {} for account {}", tierGroupId, harnessAccount, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_SCORECARD, permission = IDP_SCORECARD_EDIT)
  public Response createTierGroup(@Valid TierGroupDetailsRequest body, @AccountIdentifier String harnessAccount) {
    Response disabledResponse = scorecardTiersDisabledResponse(harnessAccount);
    if (disabledResponse != null) {
      return disabledResponse;
    }
    try {
      TierGroupDetailsResponse response = tierGroupService.saveTierGroup(body, harnessAccount);
      return Response.status(Response.Status.CREATED).entity(response).build();
    } catch (InvalidRequestException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (DuplicateKeyException e) {
      String errorMessage = String.format(
          "Tier group [%s] already exists for accountId [%s]", body.getTierGroup().getIdentifier(), harnessAccount);
      log.info(errorMessage);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(errorMessage).build())
          .build();
    } catch (Exception e) {
      log.error("Could not create tier group", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_SCORECARD, permission = IDP_SCORECARD_EDIT)
  public Response updateTierGroup(@ResourceIdentifier String tierGroupId, @Valid TierGroupDetailsRequest body,
      @AccountIdentifier String harnessAccount) {
    Response disabledResponse = scorecardTiersDisabledResponse(harnessAccount);
    if (disabledResponse != null) {
      return disabledResponse;
    }
    try {
      if (!normalizeIdentifier(tierGroupId).equals(normalizeIdentifier(body.getTierGroup().getIdentifier()))) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ResponseMessage.builder()
                        .message("Path tier-group-id must match tier_group.identifier in request body")
                        .build())
            .build();
      }
      TierGroupDetailsResponse response = tierGroupService.updateTierGroup(body, harnessAccount);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception e) {
      log.error("Could not update tier group {}", tierGroupId, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_SCORECARD, permission = IDP_SCORECARD_DELETE)
  public Response deleteTierGroup(@ResourceIdentifier String tierGroupId, @AccountIdentifier String harnessAccount) {
    Response disabledResponse = scorecardTiersDisabledResponse(harnessAccount);
    if (disabledResponse != null) {
      return disabledResponse;
    }
    try {
      tierGroupService.deleteTierGroup(harnessAccount, tierGroupId);
      return Response.status(Response.Status.NO_CONTENT).build();
    } catch (InvalidRequestException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      log.error("Could not delete tier group {}", tierGroupId, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  private Response scorecardTiersDisabledResponse(String harnessAccount) {
    if (idpCommonService.idpScorecardTiersEnabled(harnessAccount)) {
      return null;
    }
    return Response.status(Response.Status.FORBIDDEN)
        .entity(ResponseMessage.builder().message(SCORECARD_TIERS_DISABLED_MESSAGE).build())
        .build();
  }
}

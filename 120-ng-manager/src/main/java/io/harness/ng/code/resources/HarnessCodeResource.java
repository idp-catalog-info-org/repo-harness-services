/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.code.resources;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.code.CodeRepoResponseDTO;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.gitx.InlineHCConstants;
import io.harness.ng.code.services.HarnessCodeService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.UserHelperService;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(HarnessTeam.CODE)
@Api("code")
@Path("/code")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "HarnessCodeRepository", description = "This contains APIs related to Harness Code module")
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Hidden
@Slf4j
public class HarnessCodeResource {
  private static final String USER_ID_PLACEHOLDER = "{{USER}}";

  private final HarnessCodeService harnessCodeService;
  private final UserHelperService userHelperService;

  @POST
  @Path("/default-repository")
  @ApiOperation(
      value = "Creates a default harness code repository", nickname = "createHarnessCodeRepository", hidden = true)
  @Timed
  @Hidden
  @ResponseMetered
  public ResponseDTO<CodeRepoResponseDTO>
  createHarnessDefaultRepository(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = "Target account identifier", required = true) @NotBlank @QueryParam(
          "targetAccountIdentifier") @AccountIdentifier String targetAccountIdentifier) {
    checkUserAuthorization(String.format("User : %s not allowed to create default harness code repository %s",
        USER_ID_PLACEHOLDER, InlineHCConstants.REPO_NAME));
    return ResponseDTO.newResponse(harnessCodeService.createHarnessDefaultRepository(targetAccountIdentifier));
  }

  private void checkUserAuthorization(String errorMessageIfAuthorizationFailed) {
    UserPrincipal userPrincipal = userHelperService.getUserPrincipalOrThrow();
    String userId = userPrincipal.getName();
    if (!userHelperService.isHarnessSupportUser(userId)) {
      log.error(errorMessageIfAuthorizationFailed.replace(USER_ID_PLACEHOLDER, userId));
      throw new AccessDeniedException("Not Authorized", WingsException.USER);
    }
  }
}

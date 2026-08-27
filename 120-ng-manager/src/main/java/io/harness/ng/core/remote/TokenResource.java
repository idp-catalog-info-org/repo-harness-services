/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.NGResourceFilterConstants.IDENTIFIERS;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.utils.PageUtils.getPageRequest;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.beans.SortOrder;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.OPAPolicyEvaluationException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.Status;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.SSHValidateDTO;
import io.harness.ng.core.dto.TokenAggregateDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.TokenDTOInternal;
import io.harness.ng.core.dto.TokenFilterDTO;
import io.harness.ng.core.entities.Token.TokenKeys;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.UserInfo;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.annotations.InternalApi;
import io.harness.security.dto.Principal;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.typesafe.config.Optional;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("token")
@Path("token")
@Produces({"application/json", "application/yaml", "text/plain"})
@Consumes({"application/json", "application/yaml", "text/plain"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Tag(name = "Token", description = "This contains APIs related to Token as defined in Harness")
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
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Slf4j
@OwnedBy(PL)
public class TokenResource {
  private final TokenService tokenService;
  private final ApiKeyService apiKeyService;
  private final ScopeInfoService scopeResolverService;

  @POST
  @ApiOperation(value = "Create token", nickname = "createToken")
  @Operation(operationId = "createToken", summary = "Create a Token",
      description = "Creates a Token for the given API Key Type.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created Token details")
      })
  @FeatureRestrictionCheck(FeatureRestrictionName.MULTIPLE_API_TOKENS)
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<String>
  createToken(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
                  NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Valid @NotNull TokenDTO tokenDTO) {
    tokenDTO.setAccountIdentifier(accountIdentifier);
    deriveScopedTokenParentIfNeeded(tokenDTO);
    validateNonScopedTokenFields(tokenDTO);
    ScopeInfo scopeInfo = null;
    try {
      scopeInfo = scopeResolverService.getScopeInfo(
          tokenDTO.getAccountIdentifier(), tokenDTO.getOrgIdentifier(), tokenDTO.getProjectIdentifier());
    } catch (EntityNotFoundException ex) {
      log.error("Error creating token", ex);
      throw new InvalidArgumentsException(ex.getMessage(), USER_SRE);
    }
    apiKeyService.validateParentIdentifier(scopeInfo, tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier());
    String tokenGenerated = null;
    try {
      tokenGenerated = tokenService.createToken(tokenDTO, scopeInfo);
    } catch (OPAPolicyEvaluationException ex) {
      ResponseDTO<String> responseDTO = ResponseDTO.newResponse(null, null, ex.getMetadata());
      responseDTO.setStatus(Status.FAILURE);
      return responseDTO;
    }
    return ResponseDTO.newResponse(tokenGenerated);
  }

  @PUT
  @Path("{identifier}")
  @ApiOperation(value = "Update token", nickname = "updateToken")
  @Operation(operationId = "updateToken", summary = "Update a Token",
      description = "Updates a Token for the given API Key Type.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns updated Token details")
      })
  @ScopeInfoResolutionExemptedApi
  @Timed
  @ResponseMetered
  public ResponseDTO<TokenDTO>
  updateToken(@Parameter(description = "Token ID") @NotNull @PathParam("identifier") String identifier,
      @Valid TokenDTO tokenDTO,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @AccountIdentifier @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    tokenDTO.setAccountIdentifier(accountIdentifier);
    deriveScopedTokenParentIfNeeded(tokenDTO);
    validateNonScopedTokenFields(tokenDTO);
    ScopeInfo scopeInfo = null;
    try {
      scopeInfo = scopeResolverService.getScopeInfo(
          tokenDTO.getAccountIdentifier(), tokenDTO.getOrgIdentifier(), tokenDTO.getProjectIdentifier());
    } catch (EntityNotFoundException ex) {
      log.error("Error updating token", ex);
      throw new InvalidArgumentsException(ex.getMessage(), USER_SRE);
    }
    apiKeyService.validateParentIdentifier(scopeInfo, tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier());
    TokenDTO tokenGenerated = null;

    try {
      tokenGenerated = tokenService.updateToken(tokenDTO, scopeInfo);
    } catch (OPAPolicyEvaluationException ex) {
      ResponseDTO<TokenDTO> responseDTO = ResponseDTO.newResponse(null, null, ex.getMetadata());
      responseDTO.setStatus(Status.FAILURE);
      return responseDTO;
    }
    return ResponseDTO.newResponse(tokenGenerated);
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete token", nickname = "deleteToken")
  @Operation(operationId = "deleteToken", summary = "Delete a Token",
      description = "Deletes a Token for the given API Key Type.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "It returns true if the Token is deleted successfully and false if the Token is not deleted.")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean>
  deleteToken(@Parameter(description = "Token ID") @NotNull @PathParam("identifier") String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @Optional @QueryParam(ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @Optional @QueryParam(
          PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is the API Key type like Personal Access Key or Service Account Key.") @NotNull
      @QueryParam("apiKeyType") ApiKeyType apiKeyType,
      @Parameter(description = "ID of API key's Parent Service Account") @NotNull @QueryParam(
          "parentIdentifier") String parentIdentifier,
      @Parameter(description = "API key ID") @NotNull @QueryParam("apiKeyIdentifier") String apiKeyIdentifier,
      @Context ScopeInfo scopeInfo) {
    apiKeyService.validateParentIdentifier(scopeInfo, apiKeyType, parentIdentifier);
    return ResponseDTO.newResponse(
        tokenService.revokeToken(scopeInfo, apiKeyType, parentIdentifier, apiKeyIdentifier, identifier));
  }

  @DELETE
  @Path("scoped/resource/{parentResourceId}")
  @Hidden
  @InternalApi
  @ApiOperation(
      value = "Bulk delete scoped tokens by parent resource ID", nickname = "deleteScopedTokensByParentResource")
  @Timed
  @ResponseMetered
  public ResponseDTO<Long>
  deleteScopedTokensByParentResource(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                                     @QueryParam(ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = "Parent resource identifier (e.g. pipeline execution ID)",
          required = true) @NotEmpty @PathParam("parentResourceId") String parentResourceId) {
    long deleted = tokenService.deleteAllScopedTokensByParentResourceId(accountIdentifier, parentResourceId);
    return ResponseDTO.newResponse(deleted);
  }

  @GET
  @Hidden
  @InternalApi
  @ApiOperation(value = "Get token", nickname = "getToken")
  @Timed
  @ResponseMetered
  public ResponseDTO<TokenDTO> getToken(@QueryParam("tokenId") String tokenId) {
    return ResponseDTO.newResponse(tokenService.getToken(tokenId, true));
  }

  @GET
  @Path("/ssh")
  @Hidden
  @InternalApi
  @ApiOperation(value = "Get SSH token with public key", nickname = "getSSHTokenWithPublicKey")
  @Timed
  @ResponseMetered
  public ResponseDTO<TokenDTO> getSSHTokenWithPublicKey(
      @NotNull @QueryParam(ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam("parentIdentifier") String parentIdentifier,
      @NotNull @QueryParam("tokenIdentifier") String tokenIdentifier) {
    return ResponseDTO.newResponse(
        tokenService.getSSHTokenWithPublicKey(tokenIdentifier, accountIdentifier, parentIdentifier));
  }

  @POST
  @Path("rotate/{identifier}")
  @ApiOperation(value = "Rotate token", nickname = "rotateToken")
  @Operation(operationId = "rotateToken", summary = "Rotate a Token",
      description = "Rotates a Token for the given API Key Type.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the rotated Token")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<String>
  rotateToken(@Parameter(description = "Token Identifier") @NotNull @PathParam("identifier") String identifier,
      @Parameter(description = "Time stamp till when the old token will be valid post rotation.") @QueryParam(
          "rotateTimestamp") Long rotateTimestamp,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @Optional @QueryParam(ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @Optional @QueryParam(
          PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is the API Key type like Personal Access Key or Service Account Key.") @NotNull
      @QueryParam("apiKeyType") ApiKeyType apiKeyType,
      @Parameter(description = "ID of API key's Parent Service Account") @NotNull @QueryParam(
          "parentIdentifier") String parentIdentifier,
      @Parameter(description = "API key ID") @NotNull @QueryParam("apiKeyIdentifier") String apiKeyIdentifier,
      @Context ScopeInfo scopeInfo) {
    apiKeyService.validateParentIdentifier(scopeInfo, apiKeyType, parentIdentifier);
    String tokenGenerated = null;
    try {
      tokenGenerated = tokenService.rotateToken(
          scopeInfo, apiKeyType, parentIdentifier, apiKeyIdentifier, identifier, Instant.ofEpochMilli(rotateTimestamp));
    } catch (OPAPolicyEvaluationException ex) {
      ResponseDTO<String> responseDTO = ResponseDTO.newResponse(null, null, ex.getMetadata());
      responseDTO.setStatus(Status.FAILURE);
      return responseDTO;
    }
    return ResponseDTO.newResponse(tokenGenerated);
  }

  @GET
  @Path("aggregate")
  @ApiOperation(value = "List tokens", nickname = "listAggregatedTokens")
  @Operation(operationId = "listAggregatedTokens", summary = "List all Tokens",
      description = "Lists all the Tokens matching the given search criteria.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Aggregated Tokens.")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<TokenAggregateDTO>>
  listAggregatedTokens(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                           ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @Optional @QueryParam(ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @Optional @QueryParam(
          PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is the API Key type like Personal Access Key or Service Account Key.") @NotNull
      @QueryParam("apiKeyType") ApiKeyType apiKeyType,
      @Parameter(description = "ID of API key's Parent Service Account") @QueryParam(
          "parentIdentifier") String parentIdentifier,
      @Parameter(description = "API key ID") @QueryParam("apiKeyIdentifier") String apiKeyIdentifier,
      @Parameter(description = "This is the list of Token IDs. Details specific to these IDs would be fetched.")
      @Optional @QueryParam(IDENTIFIERS) List<String> identifiers, @BeanParam PageRequest pageRequest,
      @Parameter(description = "This would be used to filter Tokens. Any Token having the specified string in its "
              + "Name, ID and Tag would be filtered.") @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY)
      String searchTerm,
      @Parameter(description = "Boolean value to indicate whether to list only active tokens or all tokens. By "
              + "default, all tokens will be listed.") @QueryParam("includeOnlyActiveTokens")
      boolean includeOnlyActiveTokens,
      @Context ScopeInfo scopeInfo) {
    if (isNotEmpty(apiKeyIdentifier) && isEmpty(parentIdentifier)) {
      throw new InvalidRequestException(
          "Need to pass parentIdentifier along with apikeyIdentifier to list the tokens.");
    }

    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(TokenKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    TokenFilterDTO filterDTO = TokenFilterDTO.builder()
                                   .accountIdentifier(accountIdentifier)
                                   .parentIdentifier(parentIdentifier)
                                   .apiKeyType(apiKeyType)
                                   .searchTerm(searchTerm)
                                   .apiKeyIdentifier(apiKeyIdentifier)
                                   .identifiers(identifiers)
                                   .includeOnlyActiveTokens(includeOnlyActiveTokens)
                                   .build();
    tokenService.validateTokenListPermissions(scopeInfo, filterDTO);
    PageResponse<TokenAggregateDTO> requestDTOS =
        tokenService.listAggregateTokens(scopeInfo, getPageRequest(pageRequest), filterDTO);
    return ResponseDTO.newResponse(requestDTOS);
  }

  @POST
  @Path("validate")
  @ApiOperation(value = "Validate token", nickname = "validateToken")
  @Operation(operationId = "validateToken", summary = "Validate a Token",
      description = "Validate a Token for the given account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Validate a Token for the given account")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<TokenDTO>
  validateToken(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                    ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @ApiParam(name = "apiKey", value = "API Key token to be validated", required = true) @NotNull String apiKey) {
    log.info(String.format(
        "API_KEY_NG_VALIDATE: Validate token for API key or JWT token called for account id: %s", accountIdentifier));
    TokenDTO tokenDTO = tokenService.validateToken(accountIdentifier, apiKey);
    return ResponseDTO.newResponse(tokenDTO);
  }

  @POST
  @Path("internal/validate")
  @ApiOperation(value = "Validate token", nickname = "validateTokenInternal")
  @Hidden
  @InternalApi
  @Operation(operationId = "validateTokenInternal", summary = "Validate a Token",
      description = "Validate a Token for the given account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Validate a Token for the given account")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<TokenDTOInternal>
  validateTokenInternal(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(ACCOUNT_KEY)
                        @AccountIdentifier String accountIdentifier, @NotNull String apiKey) {
    log.info(String.format(
        "API_KEY_NG_VALIDATE: Validate token for API key or JWT token called for account id: %s", accountIdentifier));
    TokenDTOInternal tokenDTO = tokenService.validateTokenInternal(accountIdentifier, apiKey);
    return ResponseDTO.newResponse(tokenDTO);
  }

  @POST
  @Hidden
  @InternalApi
  @ApiOperation(value = "Validate SSH Key", nickname = "validateSSHKey")
  @Path("ssh/validate")
  @Timed
  @ResponseMetered
  public ResponseDTO<UserInfo> validateSSHKey(@Parameter(
      description = "SSH key which needs to be validated to find the parent") @Valid SSHValidateDTO sshValidateDTO) {
    return tokenService.validateSSHKey(sshValidateDTO);
  }

  private void deriveScopedTokenParentIfNeeded(TokenDTO tokenDTO) {
    if (ApiKeyType.SCOPED_TOKEN.equals(tokenDTO.getApiKeyType()) && isEmpty(tokenDTO.getParentIdentifier())) {
      Principal principal = SourcePrincipalContextBuilder.getSourcePrincipal();
      if (principal != null) {
        tokenDTO.setParentIdentifier(principal.getName());
      }
    }
  }

  private void validateNonScopedTokenFields(TokenDTO tokenDTO) {
    if (!ApiKeyType.SCOPED_TOKEN.equals(tokenDTO.getApiKeyType())) {
      if (isEmpty(tokenDTO.getParentIdentifier())) {
        throw new InvalidRequestException("parentIdentifier is required for PAT/SAT tokens");
      }
      if (isEmpty(tokenDTO.getApiKeyIdentifier())) {
        throw new InvalidRequestException("apiKeyIdentifier is required for PAT/SAT tokens");
      }
    }
  }
}

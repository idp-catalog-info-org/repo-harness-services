/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ng.core.api.utils.KeyUtils.API_KEY_KEY_IDENTIFIER;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.NGCommonEntityConstants;
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
import io.harness.ng.core.Status;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.api.utils.KeyUtils;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PublicKeyScheme;
import io.harness.ng.core.common.beans.PublicKeyUsage;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.KeyDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.TokenAggregateDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.TokenFilterDTO;
import io.harness.ng.core.dto.UpdatePublicKeyRequest;
import io.harness.ng.core.entities.Token.TokenKeys;
import io.harness.ng.core.services.ScopeInfoService;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("keys")
@Path("keys")
@Produces({"application/json", "application/yaml", "text/plain"})
@Consumes({"application/json", "application/yaml", "text/plain"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Tag(name = "Keys", description = "This contains APIs related to Keys as defined in Harness")
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
public class KeyResource {
  private final TokenService tokenService;
  private final ApiKeyService apiKeyService;
  private final ScopeInfoService scopeResolverService;

  @POST
  @ApiOperation(value = "Create Key", nickname = "createKey")
  @Operation(operationId = "createKey", summary = "Create Key",
      description = "Creates a SSH or PGP Key. Use query param keyScheme=ssh (default) or keyScheme=pgp. Defaults to "
          + "SSH when keyScheme is not passed.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created Key details")
      })
  @FeatureRestrictionCheck(FeatureRestrictionName.MULTIPLE_API_TOKENS)
  @Timed
  @ResponseMetered
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<String>
  createToken(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
                  NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = "Key scheme: ssh (default) or pgp") @QueryParam("keyScheme") String keyScheme,
      @Valid @NotNull KeyDTO keyDTO) {
    // Query param takes precedence; fall back to body keyScheme for backward compatibility, then default to ssh
    ApiKeyType apiKeyType;
    if (isNotBlank(keyScheme)) {
      apiKeyType = parseApiKeyType(keyScheme.trim());
    } else if (keyDTO.getKeyScheme() != null) {
      apiKeyType = keyDTO.getKeyScheme() == PublicKeyScheme.PGP ? ApiKeyType.PGP_KEY : ApiKeyType.SSH_KEY;
    } else {
      apiKeyType = ApiKeyType.SSH_KEY;
    }
    validateKeyContent(keyDTO.getKey(), apiKeyType);
    return createKeyToken(accountIdentifier, keyDTO, apiKeyType);
  }

  private void validateKeyContent(String keyContent, ApiKeyType apiKeyType) {
    if (keyContent == null || keyContent.isBlank()) {
      return;
    }
    String content = keyContent.trim();
    if (apiKeyType == ApiKeyType.SSH_KEY && !content.contains("ssh-")) {
      throw new InvalidArgumentsException("Key content does not appear to be a valid SSH key");
    }
    if (apiKeyType == ApiKeyType.PGP_KEY && !content.contains("BEGIN PGP") && !content.contains("-----BEGIN")) {
      throw new InvalidArgumentsException("Key content does not appear to be a valid PGP key");
    }
  }

  private ResponseDTO<String> createKeyToken(String accountIdentifier, KeyDTO keyDTO, ApiKeyType apiKeyType) {
    keyDTO.setAccountIdentifier(accountIdentifier);
    ScopeInfo scopeInfo = null;
    try {
      scopeInfo = scopeResolverService.getScopeInfo(keyDTO.getAccountIdentifier(), null, null);
    } catch (EntityNotFoundException ex) {
      log.error("Error creating token", ex);
      throw new InvalidArgumentsException(ex.getMessage(), USER_SRE);
    }
    apiKeyService.validateParentIdentifier(scopeInfo, apiKeyType, keyDTO.getParentIdentifier());
    String tokenGenerated = null;
    try {
      TokenDTO tokenDTO = KeyUtils.mapKeyToToken(keyDTO, apiKeyType);
      tokenGenerated = tokenService.createToken(tokenDTO, scopeInfo);
    } catch (OPAPolicyEvaluationException ex) {
      ResponseDTO<String> responseDTO = ResponseDTO.newResponse(null, null, ex.getMetadata());
      responseDTO.setStatus(Status.FAILURE);
      return responseDTO;
    }
    return ResponseDTO.newResponse(tokenGenerated);
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete key", nickname = "deleteKey")
  @Operation(operationId = "deleteKey", summary = "Delete a Key",
      description = "Deletes a Key (SSH or PGP) by its identifier. For PGP primary keys, all subkeys are also deleted.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "It returns true if the Key is deleted successfully and false if the Key is not deleted.")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean>
  deleteToken(@Parameter(description = "Key identifier") @NotNull @PathParam("identifier") String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = "Identifier of the key's Parent Account") @NotNull @QueryParam("parentIdentifier")
      String parentIdentifier, @Context ScopeInfo scopeInfo) {
    // Validate parent identifier for both SSH and PGP key types
    apiKeyService.validateParentIdentifier(scopeInfo, ApiKeyType.SSH_KEY, parentIdentifier);
    return ResponseDTO.newResponse(
        tokenService.deleteKey(scopeInfo, parentIdentifier, API_KEY_KEY_IDENTIFIER, identifier));
  }

  @PUT
  @Path("{identifier}")
  @ApiOperation(value = "Update key", nickname = "updateKey")
  @Operation(operationId = "updateKey", summary = "Update a Key",
      description = "Updates a Key's validity period or revocation status. "
          + "For COMPROMISED revocation of PGP keys, the code-api service is notified first.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Key details")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<KeyDTO>
  updateKey(@Parameter(description = "Key identifier") @NotNull @PathParam("identifier") String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = "Identifier of the key's Parent Account") @NotNull @QueryParam("parentIdentifier")
      String parentIdentifier, @Valid @NotNull UpdatePublicKeyRequest request, @Context ScopeInfo scopeInfo) {
    apiKeyService.validateParentIdentifier(scopeInfo, ApiKeyType.SSH_KEY, parentIdentifier);

    TokenDTO updatedToken =
        tokenService.updateKey(scopeInfo, parentIdentifier, API_KEY_KEY_IDENTIFIER, identifier, request);

    return ResponseDTO.newResponse(KeyUtils.mapTokenToKey(updatedToken));
  }

  private ApiKeyType parseApiKeyType(String keyScheme) {
    if (keyScheme == null || keyScheme.isBlank() || "ssh".equalsIgnoreCase(keyScheme.trim())) {
      return ApiKeyType.SSH_KEY;
    } else if ("pgp".equalsIgnoreCase(keyScheme.trim())) {
      return ApiKeyType.PGP_KEY;
    }
    throw new InvalidArgumentsException("Invalid keyScheme. Must be 'ssh' or 'pgp'");
  }

  @GET
  @ApiOperation(value = "List Keys", nickname = "ListAggregatedKeys")
  @Operation(operationId = "ListAggregatedKeys", summary = "List Keys",
      description = "Lists SSH, PGP, or all Keys based on keyScheme parameter. Supports filtering by fingerprint, "
          + "subKeyId, usages, and schemes.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Keys matching the criteria.")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PageResponse<KeyDTO>>
  listKeys(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
               ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = "Identifier of the key's Parent Account") @QueryParam(
          "parentIdentifier") String parentIdentifier,
      @Parameter(description = "Key scheme: ssh (default), pgp, or all") @QueryParam("keyScheme") String keyScheme,
      @Parameter(description = "Key fingerprint filter (exact match)") @QueryParam("fingerprint") String fingerprint,
      @Parameter(description = "Subkey ID filter (PGP only)") @QueryParam("subKeyId") String subKeyId,
      @Parameter(description = "Key usage filter") @QueryParam("usages") List<PublicKeyUsage> usages,
      @Parameter(description = "Key scheme filter") @QueryParam("schemes") List<PublicKeyScheme> schemes,
      @BeanParam PageRequest pageRequest, @Context ScopeInfo scopeInfo) {
    KeySchemeConfig schemeConfig = parseKeyScheme(keyScheme);

    // Handle filtered search by fingerprint or subKeyId
    if (hasSpecificFilters(fingerprint, subKeyId)) {
      return handleFilteredKeySearch(accountIdentifier, parentIdentifier, fingerprint, subKeyId, usages, schemeConfig);
    }

    // Handle general listing
    if (schemeConfig.includeAllSchemes) {
      return listAllKeyTypes(accountIdentifier, parentIdentifier, pageRequest, scopeInfo);
    } else {
      return listKeysByType(accountIdentifier, parentIdentifier, pageRequest, scopeInfo, schemeConfig.apiKeyType);
    }
  }

  private KeySchemeConfig parseKeyScheme(String keyScheme) {
    if (keyScheme == null || keyScheme.isBlank() || "ssh".equalsIgnoreCase(keyScheme.trim())) {
      return new KeySchemeConfig(ApiKeyType.SSH_KEY, false);
    }
    if ("pgp".equalsIgnoreCase(keyScheme.trim())) {
      return new KeySchemeConfig(ApiKeyType.PGP_KEY, false);
    }
    if ("all".equalsIgnoreCase(keyScheme.trim())) {
      return new KeySchemeConfig(null, true);
    }
    throw new InvalidArgumentsException("Invalid keyScheme. Must be 'ssh', 'pgp', or 'all'");
  }

  private boolean hasSpecificFilters(String fingerprint, String subKeyId) {
    return isNotBlank(fingerprint) || isNotBlank(subKeyId);
  }

  private ResponseDTO<PageResponse<KeyDTO>> handleFilteredKeySearch(String accountIdentifier, String parentIdentifier,
      String fingerprint, String subKeyId, List<PublicKeyUsage> usages, KeySchemeConfig schemeConfig) {
    try {
      List<PublicKeyScheme> searchSchemes = schemeConfig.toSchemes();
      List<TokenDTO> tokens =
          tokenService.listTokensByKeyFilters(accountIdentifier, isNotBlank(fingerprint) ? fingerprint.trim() : null,
              isNotBlank(subKeyId) ? subKeyId.trim() : null, parentIdentifier, usages, searchSchemes);

      List<KeyDTO> keyDTOs = tokens.stream().map(KeyUtils::mapTokenToKey).collect(Collectors.toList());
      return ResponseDTO.newResponse(PageResponse.<KeyDTO>builder()
                                         .content(keyDTOs)
                                         .totalItems(keyDTOs.size())
                                         .pageIndex(0)
                                         .pageSize(keyDTOs.size())
                                         .totalPages(1)
                                         .build());
    } catch (Exception e) {
      log.error("Error searching for keys by fingerprint/subKeyId", e);
      throw new InvalidRequestException("Failed to search for keys: " + e.getMessage());
    }
  }

  private static class KeySchemeConfig {
    final ApiKeyType apiKeyType;
    final boolean includeAllSchemes;

    KeySchemeConfig(ApiKeyType apiKeyType, boolean includeAllSchemes) {
      this.apiKeyType = apiKeyType;
      this.includeAllSchemes = includeAllSchemes;
    }

    List<PublicKeyScheme> toSchemes() {
      if (includeAllSchemes) {
        return null;
      }
      if (apiKeyType == ApiKeyType.SSH_KEY) {
        return List.of(PublicKeyScheme.SSH);
      }
      if (apiKeyType == ApiKeyType.PGP_KEY) {
        return List.of(PublicKeyScheme.PGP);
      }
      return null;
    }
  }

  private ResponseDTO<PageResponse<KeyDTO>> listKeysByType(String accountIdentifier, String parentIdentifier,
      PageRequest pageRequest, ScopeInfo scopeInfo, ApiKeyType apiKeyType) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(TokenKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    TokenFilterDTO filterDTO = TokenFilterDTO.builder()
                                   .accountIdentifier(accountIdentifier)
                                   .parentIdentifier(parentIdentifier)
                                   .apiKeyType(apiKeyType)
                                   .apiKeyIdentifier(API_KEY_KEY_IDENTIFIER)
                                   .build();
    try {
      tokenService.validateTokenListPermissions(scopeInfo, filterDTO);
      PageResponse<TokenAggregateDTO> requestDTOS =
          tokenService.listAggregateTokens(scopeInfo, getPageRequest(pageRequest), filterDTO);
      return ResponseDTO.newResponse(requestDTOS.map(t -> KeyUtils.mapTokenToKey(t.getToken())));
    } catch (Exception e) {
      log.error("Error listing keys", e);
      throw new InvalidRequestException("Failed to list keys: " + e.getMessage());
    }
  }

  private ResponseDTO<PageResponse<KeyDTO>> listAllKeyTypes(
      String accountIdentifier, String parentIdentifier, PageRequest pageRequest, ScopeInfo scopeInfo) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(TokenKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }

    try {
      // Validate permissions for both key types
      TokenFilterDTO sshFilterDTO = TokenFilterDTO.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .parentIdentifier(parentIdentifier)
                                        .apiKeyType(ApiKeyType.SSH_KEY)
                                        .apiKeyIdentifier(API_KEY_KEY_IDENTIFIER)
                                        .build();
      tokenService.validateTokenListPermissions(scopeInfo, sshFilterDTO);

      TokenFilterDTO pgpFilterDTO = TokenFilterDTO.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .parentIdentifier(parentIdentifier)
                                        .apiKeyType(ApiKeyType.PGP_KEY)
                                        .apiKeyIdentifier(API_KEY_KEY_IDENTIFIER)
                                        .build();
      tokenService.validateTokenListPermissions(scopeInfo, pgpFilterDTO);

      // Use database-level sorting and pagination for both SSH and PGP keys
      List<ApiKeyType> keyTypes = List.of(ApiKeyType.SSH_KEY, ApiKeyType.PGP_KEY);
      PageResponse<TokenAggregateDTO> allTokens = tokenService.listTokensByApiKeyTypes(
          scopeInfo, accountIdentifier, parentIdentifier, keyTypes, getPageRequest(pageRequest));

      List<KeyDTO> keyDTOs = allTokens.getContent()
                                 .stream()
                                 .map(tokenAggregate -> KeyUtils.mapTokenToKey(tokenAggregate.getToken()))
                                 .collect(Collectors.toList());

      PageResponse<KeyDTO> pageResponse = PageResponse.<KeyDTO>builder()
                                              .content(keyDTOs)
                                              .totalItems(allTokens.getTotalItems())
                                              .pageIndex(allTokens.getPageIndex())
                                              .pageSize(allTokens.getPageSize())
                                              .totalPages(allTokens.getTotalPages())
                                              .build();

      return ResponseDTO.newResponse(pageResponse);
    } catch (Exception e) {
      log.error("Error listing all key types", e);
      throw new InvalidRequestException("Failed to list all keys: " + e.getMessage());
    }
  }
}

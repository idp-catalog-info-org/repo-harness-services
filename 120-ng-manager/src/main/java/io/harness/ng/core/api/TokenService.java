/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PublicKeyScheme;
import io.harness.ng.core.common.beans.PublicKeyUsage;
import io.harness.ng.core.dto.PublicKeyDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.SSHValidateDTO;
import io.harness.ng.core.dto.TokenAggregateDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.TokenDTOInternal;
import io.harness.ng.core.dto.TokenFilterDTO;
import io.harness.ng.core.dto.UpdatePublicKeyRequest;
import io.harness.ng.core.user.UserInfo;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public interface TokenService {
  String createToken(TokenDTO tokenDTO, ScopeInfo scopeInfo);
  boolean revokeToken(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier, String identifier);

  String rotateToken(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier,
      String identifier, Instant scheduledExpireTime);
  TokenDTO updateToken(TokenDTO tokenDTO, ScopeInfo scopeInfo);

  Map<String, Integer> getTokensPerApiKeyIdentifier(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, List<String> apiKeyIdentifiers);

  PageResponse<TokenAggregateDTO> listAggregateTokens(ScopeInfo scopeInfo, Pageable pageable, TokenFilterDTO filterDTO);

  long deleteAllByParentIdentifier(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier);

  long deleteAllByApiKeyIdentifier(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier);
  TokenDTO getToken(String tokenId, boolean withEncodedPassword);

  TokenDTO getSSHTokenWithPublicKey(String tokenIdentifier, String accountIdentifier, String parentIdentifier);

  TokenDTO getPGPTokenWithPublicKey(String tokenIdentifier, String accountIdentifier, String parentIdentifier);

  TokenDTO validateToken(String accountIdentifier, String apiKey);

  TokenDTOInternal validateTokenInternal(String accountIdentifier, String apiKey);

  void validateTokenListPermissions(ScopeInfo scopeInfo, TokenFilterDTO filterDTO);

  Long countApiTokens(String accountIdentifier);

  ResponseDTO<UserInfo> validateSSHKey(SSHValidateDTO sshValidateDTO);

  List<PublicKeyDTO> listByFingerprint(String accountIdentifier, String fingerprint, String principalIdentifier,
      List<PublicKeyUsage> usages, List<PublicKeyScheme> schemes);

  List<PublicKeyDTO> listBySubKeyId(String accountIdentifier, String subKeyId, String principalIdentifier,
      List<PublicKeyUsage> usages, List<PublicKeyScheme> schemes);

  List<PublicKeyDTO> listByPrincipal(
      String accountIdentifier, String principalIdentifier, List<PublicKeyUsage> usages, List<PublicKeyScheme> schemes);

  List<TokenDTO> listTokensByKeyFilters(String accountIdentifier, String fingerprint, String subKeyId,
      String principalIdentifier, List<PublicKeyUsage> usages, List<PublicKeyScheme> schemes);

  PageResponse<TokenAggregateDTO> listTokensByApiKeyTypes(ScopeInfo scopeInfo, String accountIdentifier,
      String parentIdentifier, List<ApiKeyType> apiKeyTypes, Pageable pageable);

  /**
   * Deletes a key (SSH or PGP) by its identifier.
   * For PGP primary keys, all subkeys will also be deleted.
   * For PGP subkeys, only that subkey will be deleted.
   *
   * @param scopeInfo scope information
   * @param parentIdentifier the parent (user) identifier
   * @param apiKeyIdentifier the API key identifier
   * @param identifier the token identifier (unique across all key types)
   * @return true if the key was deleted, false otherwise
   */
  boolean deleteKey(ScopeInfo scopeInfo, String parentIdentifier, String apiKeyIdentifier, String identifier);

  /**
   * Updates a key's validity period or revocation status.
   * For COMPROMISED revocation, calls code-api service first.
   * For PGP primary keys, all subkeys are updated when revoking.
   *
   * @param scopeInfo scope information
   * @param parentIdentifier the parent (user) identifier
   * @param apiKeyIdentifier the API key identifier
   * @param identifier the token identifier
   * @param request the update request
   * @return updated TokenDTO
   */
  TokenDTO updateKey(ScopeInfo scopeInfo, String parentIdentifier, String apiKeyIdentifier, String identifier,
      UpdatePublicKeyRequest request);

  long deleteAllScopedTokensByParentResourceId(String accountIdentifier, String parentResourceId);
}

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
import io.harness.ng.core.dto.ApiKeyAggregateDTO;
import io.harness.ng.core.dto.ApiKeyDTO;
import io.harness.ng.core.dto.ApiKeyFilterDTO;
import io.harness.ng.core.entities.ApiKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public interface ApiKeyService {
  ApiKeyDTO createApiKey(ApiKeyDTO apiKeyDTO, ScopeInfo scopeInfo);
  ApiKeyDTO updateApiKey(ApiKeyDTO apiKeyDTO, ScopeInfo scopeInfo);
  boolean deleteApiKey(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String identifier);
  List<ApiKeyDTO> listApiKeys(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, List<String> identifiers);
  Optional<ApiKey> getApiKey(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String identifier);

  Map<String, Integer> getApiKeysPerParentIdentifier(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, List<String> parentIdentifier);

  PageResponse<ApiKeyAggregateDTO> listAggregateApiKeys(
      ScopeInfo scopeInfo, Pageable pageable, ApiKeyFilterDTO filterDTO);

  ApiKeyAggregateDTO getApiKeyAggregateDTO(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String identifier);

  long deleteAllByParentIdentifier(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier);

  void deleteAtAllScopes(ScopeInfo scopeInfo);

  void validateParentIdentifier(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier);

  Long countApiKeys(String accountIdentifier);

  /**
   * Deletes all API keys associated with a specific parent identifier.
   * @param scopeInfo The scope information
   * @param apiKeyType The type of API key
   * @param parentIdentifier The parent identifier for which to delete API keys
   * @return The number of API keys deleted
   */
  int deleteAllApiKeysForParent(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier);
}

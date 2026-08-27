/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ng.core.spring;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.entities.Token;
import io.harness.repositories.ng.core.custom.TokenCustomRepository;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(PL)
@HarnessRepo
public interface TokenRepository
    extends PagingAndSortingRepository<Token, String>, CrudRepository<Token, String>, TokenCustomRepository {
  long deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifier(
      String accountIdentifier, String parentUniqueId, ApiKeyType apiKeyType, String parentIdentifier);

  long deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifier(
      String accountIdentifier, String parentUniqueId, ApiKeyType apiKeyType, String parentIdentifier,
      String apiKeyIdentifier);

  Optional<Token>
  findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
      String accountIdentifier, String parentUniqueId, ApiKeyType apiKeyType, String parentIdentifier,
      String apiKeyIdentifier, String identifier);

  long countByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifier(
      String accountIdentifier, String parentUniqueId, ApiKeyType apiKeyType, String parentIdentifier,
      String apiKeyIdentifier);
  Long countByAccountIdentifier(String accountIdentifier);

  Optional<Token> findByAccountIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
      String accountIdentifier, ApiKeyType apiKeyType, String parentIdentifier, String identifier);

  Optional<Token> findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
      String accountIdentifier, String parentUniqueId, String parentIdentifier, String apiKeyIdentifier,
      String identifier);

  long deleteAllByAccountIdentifierAndScopedResourceMetadata_ParentResourceIdAndApiKeyType(
      String accountIdentifier, String parentResourceId, ApiKeyType apiKeyType);

  long countByAccountIdentifierAndParentIdentifierAndApiKeyTypeAndTokenMode(String accountIdentifier,
      String parentIdentifier, ApiKeyType apiKeyType, io.harness.ng.core.common.beans.TokenMode tokenMode);
}

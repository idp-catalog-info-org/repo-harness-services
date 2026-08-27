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
import io.harness.ng.core.entities.ApiKey;
import io.harness.repositories.ng.core.custom.ApiKeyCustomRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(PL)
@HarnessRepo
public interface ApiKeyRepository
    extends PagingAndSortingRepository<ApiKey, String>, CrudRepository<ApiKey, String>, ApiKeyCustomRepository {
  Optional<ApiKey> findByAccountIdentifierAndParentUniqueIdAndAndApiKeyTypeAndParentIdentifierAndIdentifier(
      String accountIdentifier, String parentUniqueId, ApiKeyType apiKeyType, String parentIdentifier,
      String identifier);

  long deleteByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndIdentifier(String accountIdentifier,
      String parentUniqueId, ApiKeyType apiKeyType, String parentIdentifier, String identifier);

  List<ApiKey> findAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifier(
      String accountIdentifier, String parentUniqueId, ApiKeyType apiKeyType, String parentIdentifier);

  List<ApiKey> findAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndIdentifierIn(
      String accountIdentifier, String parentUniqueId, ApiKeyType apiKeyType, String parentIdentifier,
      List<String> identifiers);

  long deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifier(
      String accountIdentifier, String parentUniqueId, ApiKeyType apiKeyType, String parentIdentifier);

  long countByAccountIdentifierAndParentUniqueIdAndParentIdentifier(
      String accountIdentifier, String parentUniqueId, String parentIdentifier);

  Long countByAccountIdentifier(String accountIdentifier);
}

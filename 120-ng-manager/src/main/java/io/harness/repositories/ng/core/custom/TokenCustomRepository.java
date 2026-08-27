/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ng.core.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PublicKeyScheme;
import io.harness.ng.core.common.beans.PublicKeyUsage;
import io.harness.ng.core.entities.Token;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public interface TokenCustomRepository {
  Page<Token> findAll(Criteria criteria, Pageable pageable);

  <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn);

  Map<String, Integer> getTokensPerParentIdentifier(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, List<String> apiKeyIdentifiers);

  List<Token> findExpiredTokens(Instant currentTime);

  /**
   * Unified method to find tokens by key filters (fingerprint, keyId, parentKeyId, principal).
   * This is the primary query method for key lookups to reduce distinct query patterns.
   * - fingerprint: matches SSH fingerprint or PGP fingerprint
   * - keyId: matches PGP keyId (applies to both primary keys and subkeys)
   * - parentKeyId: matches PGP subkeys by their parent key's keyId
   * - principalIdentifier: the user/service account owning the key
   */
  List<Token> findByKeyFilters(String accountIdentifier, String fingerprint, String keyId, String parentKeyId,
      String principalIdentifier, List<PublicKeyUsage> usages, List<PublicKeyScheme> schemes);

  /**
   * Check if a token exists matching the given criteria
   */
  boolean exists(Criteria criteria);

  /**
   * Find tokens by multiple API key types with database-level sorting and pagination
   */
  Page<Token> findByApiKeyTypes(
      String accountIdentifier, String parentIdentifier, List<ApiKeyType> apiKeyTypes, Pageable pageable);

  /**
   * Delete tokens matching criteria
   * @param criteria the criteria to match
   * @return the number of deleted tokens
   */
  long deleteAll(Criteria criteria);

  /**
   * Find a batch of SCOPED_TOKEN documents that still carry at least one
   * {@code scopedResourcePermissions[]} entry where the deprecated single
   * {@code permission} field is set but the new {@code permissions} list is missing or empty.
   * Used by the recurring backfill job to normalize legacy entries to the new list shape.
   *
   * @param batchSize maximum number of tokens to return in a single batch
   * @return tokens needing backfill, capped at {@code batchSize}
   */
  List<Token> findScopedTokensNeedingPermissionsBackfill(int batchSize);
}

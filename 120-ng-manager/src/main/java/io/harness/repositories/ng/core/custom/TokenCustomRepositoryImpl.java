/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ng.core.custom;

import static io.harness.NGCommonEntityConstants.MONGODB_ID;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ng.core.beans.CountByKeyAggregationResult;
import io.harness.ng.core.beans.CountByKeyAggregationResult.CountByKeyAggregationResultKeys;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PGPKeyUsage;
import io.harness.ng.core.common.beans.PublicKeyScheme;
import io.harness.ng.core.common.beans.PublicKeyUsage;
import io.harness.ng.core.common.beans.SSHKeyUsage;
import io.harness.ng.core.common.beans.ScopedResourcePermission.ScopedResourcePermissionKeys;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.entities.Token.TokenKeys;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class TokenCustomRepositoryImpl implements TokenCustomRepository {
  private final MongoTemplate mongoTemplate;

  @Override
  public Page<Token> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<Token> tokens = mongoTemplate.find(query, Token.class);
    return PageableExecutionUtils.getPage(
        tokens, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Token.class));
  }

  @Override
  public <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn) {
    return mongoTemplate.aggregate(aggregation, Token.class, classToFillResultIn);
  }

  @Override
  public Map<String, Integer> getTokensPerParentIdentifier(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, List<String> apiKeyIdentifiers) {
    Criteria criteria = Criteria.where(TokenKeys.accountIdentifier)
                            .is(scopeInfo.getAccountIdentifier())
                            .and(TokenKeys.parentUniqueId)
                            .is(scopeInfo.getUniqueId())
                            .and(TokenKeys.apiKeyType)
                            .is(apiKeyType.name())
                            .and(TokenKeys.parentIdentifier)
                            .is(parentIdentifier);
    if (isNotEmpty(apiKeyIdentifiers)) {
      criteria.and(TokenKeys.apiKeyIdentifier).in(apiKeyIdentifiers);
    }
    MatchOperation matchStage = Aggregation.match(criteria);
    GroupOperation groupByApiKeyStage =
        group(TokenKeys.apiKeyIdentifier).count().as(CountByKeyAggregationResultKeys.count);
    ProjectionOperation projectionStage = project()
                                              .and(MONGODB_ID)
                                              .as(CountByKeyAggregationResultKeys.key)
                                              .andInclude(CountByKeyAggregationResultKeys.count);
    Map<String, Integer> result = new HashMap<>();
    aggregate(newAggregation(matchStage, groupByApiKeyStage, projectionStage), CountByKeyAggregationResult.class)
        .getMappedResults()
        .forEach(countByKeyAggregationResult
            -> result.put(countByKeyAggregationResult.getKey(), countByKeyAggregationResult.getCount()));
    return result;
  }

  @Override
  public List<Token> findExpiredTokens(Instant currentTime) {
    // Exclude PGP_KEY types from cleanup because:
    // 1. The private key is still on the user side - it's not up to us to clean up
    // 2. We need to preserve revocation history for auditing purposes
    Query query = new Query(
        Criteria.where(TokenKeys.validUntil).lte(currentTime).and(TokenKeys.apiKeyType).ne(ApiKeyType.PGP_KEY))
                      .limit(100);
    return mongoTemplate.find(query, Token.class);
  }

  private void addUsageFilters(Criteria criteria, List<PublicKeyUsage> usages, List<PublicKeyScheme> schemes) {
    List<Criteria> usageCriteriaList = new ArrayList<>();

    for (PublicKeyUsage usage : usages) {
      // For SSH keys
      if (schemes == null || schemes.contains(PublicKeyScheme.SSH)) {
        try {
          SSHKeyUsage sshUsage = SSHKeyUsage.valueOf(usage.name());
          usageCriteriaList.add(
              Criteria.where(TokenKeys.apiKeyType).is(ApiKeyType.SSH_KEY).and("sshPublicKey.keyUsage").in(sshUsage));
        } catch (IllegalArgumentException e) {
          // Usage not supported for SSH, skip
        }
      }

      // For PGP keys
      if (schemes == null || schemes.contains(PublicKeyScheme.PGP)) {
        try {
          PGPKeyUsage pgpUsage = PGPKeyUsage.valueOf(usage.name());
          usageCriteriaList.add(
              Criteria.where(TokenKeys.apiKeyType).is(ApiKeyType.PGP_KEY).and("pgpPublicKey.usage").in(pgpUsage));
        } catch (IllegalArgumentException e) {
          // Usage not supported for PGP, skip
        }
      }
    }

    if (!usageCriteriaList.isEmpty()) {
      criteria.andOperator(new Criteria().orOperator(usageCriteriaList.toArray(new Criteria[0])));
    }
  }

  @Override
  public List<Token> findByKeyFilters(String accountIdentifier, String fingerprint, String keyId, String parentKeyId,
      String principalIdentifier, List<PublicKeyUsage> usages, List<PublicKeyScheme> schemes) {
    Criteria criteria = Criteria.where(TokenKeys.accountIdentifier).is(accountIdentifier);

    List<Criteria> keyCriteriaList = new ArrayList<>();
    addFingerprintCriteria(keyCriteriaList, fingerprint, schemes);
    addKeyIdCriteria(keyCriteriaList, keyId, schemes);
    addParentKeyIdCriteria(keyCriteriaList, parentKeyId);

    if (!keyCriteriaList.isEmpty()) {
      criteria.andOperator(keyCriteriaList.toArray(new Criteria[0]));
    }
    if (principalIdentifier != null) {
      criteria.and(TokenKeys.parentIdentifier).is(principalIdentifier);
    }
    if (usages != null && !usages.isEmpty()) {
      addUsageFilters(criteria, usages, schemes);
    }

    return mongoTemplate.find(new Query(criteria), Token.class);
  }

  private void addFingerprintCriteria(
      List<Criteria> keyCriteriaList, String fingerprint, List<PublicKeyScheme> schemes) {
    if (StringUtils.isBlank(fingerprint)) {
      return;
    }
    List<Criteria> fingerprintCriteriaList = new ArrayList<>();
    List<String> normalizedFingerprints = normalizeFingerprint(fingerprint.trim());

    if (schemes == null || schemes.contains(PublicKeyScheme.SSH)) {
      for (String normalizedFp : normalizedFingerprints) {
        fingerprintCriteriaList.add(Criteria.where(TokenKeys.apiKeyType)
                                        .is(ApiKeyType.SSH_KEY)
                                        .and("sshPublicKey.fingerPrint")
                                        .is(normalizedFp));
      }
    }
    if (schemes == null || schemes.contains(PublicKeyScheme.PGP)) {
      for (String normalizedFp : normalizedFingerprints) {
        fingerprintCriteriaList.add(Criteria.where(TokenKeys.apiKeyType)
                                        .is(ApiKeyType.PGP_KEY)
                                        .and("pgpPublicKey.fingerprint")
                                        .is(normalizedFp));
      }
    }
    if (!fingerprintCriteriaList.isEmpty()) {
      keyCriteriaList.add(new Criteria().orOperator(fingerprintCriteriaList.toArray(new Criteria[0])));
    }
  }

  private void addKeyIdCriteria(List<Criteria> keyCriteriaList, String keyId, List<PublicKeyScheme> schemes) {
    if (StringUtils.isBlank(keyId) || (schemes != null && !schemes.contains(PublicKeyScheme.PGP))) {
      return;
    }
    String trimmedKeyId = keyId.trim();
    List<Criteria> keyIdCriteriaList = new ArrayList<>();
    keyIdCriteriaList.add(
        Criteria.where(TokenKeys.apiKeyType).is(ApiKeyType.PGP_KEY).and("pgpPublicKey.keyId").is(trimmedKeyId));
    keyIdCriteriaList.add(
        Criteria.where(TokenKeys.apiKeyType).is(ApiKeyType.PGP_KEY).and("pgpPublicKey.subKeyIds").in(trimmedKeyId));
    keyCriteriaList.add(new Criteria().orOperator(keyIdCriteriaList.toArray(new Criteria[0])));
  }

  private void addParentKeyIdCriteria(List<Criteria> keyCriteriaList, String parentKeyId) {
    if (StringUtils.isBlank(parentKeyId)) {
      return;
    }
    // parentKeyId filter is only for PGP keys - find subkeys by their parent's keyId
    keyCriteriaList.add(Criteria.where(TokenKeys.apiKeyType)
                            .is(ApiKeyType.PGP_KEY)
                            .and("pgpPublicKey.parentKeyId")
                            .is(parentKeyId.trim()));
  }

  private List<String> normalizeFingerprint(String fingerprint) {
    if (StringUtils.isBlank(fingerprint)) {
      return List.of();
    }

    String trimmed = fingerprint.trim();
    List<String> variants = new ArrayList<>();

    // Check if this looks like an SSH fingerprint (starts with SHA256: or sha256:)
    if (trimmed.toUpperCase().startsWith("SHA256:")) {
      // SSH fingerprint normalization
      String hash = trimmed.substring(7); // Remove the prefix (7 chars for "SHA256:" or "sha256:")

      // Fix URL encoding issue: spaces in base64 are invalid and likely were '+' characters
      // that got converted to spaces by URL decoding
      String fixedHash = hash.replace(" ", "+");

      // Remove trailing '=' padding (ssh-keygen output doesn't include them)
      String normalizedHash = fixedHash.replaceAll("=+$", "");

      // The canonical format: uppercase SHA256: prefix + normalized hash
      String canonicalFingerprint = "SHA256:" + normalizedHash;
      variants.add(canonicalFingerprint);

      // Also add the original in case it was stored differently
      if (!trimmed.equals(canonicalFingerprint)) {
        variants.add(trimmed);
      }
    } else {
      // PGP fingerprint normalization (40-char hex format)
      // Remove any formatting characters (colons, spaces, dashes)
      String hexOnly = trimmed.replaceAll("[:\\s-]", "");

      // PGP fingerprints are stored as uppercase hex
      String upperHex = hexOnly.toUpperCase();
      String lowerHex = hexOnly.toLowerCase();
      variants.add(upperHex);

      // Also try lowercase in case of different storage
      if (!upperHex.equals(lowerHex)) {
        variants.add(lowerHex);
      }

      // Add original if different
      if (!trimmed.equals(upperHex) && !trimmed.equals(lowerHex)) {
        variants.add(trimmed);
      }
    }

    return variants.stream().distinct().collect(Collectors.toList());
  }

  @Override
  public boolean exists(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.exists(query, Token.class);
  }

  @Override
  public Page<Token> findByApiKeyTypes(
      String accountIdentifier, String parentIdentifier, List<ApiKeyType> apiKeyTypes, Pageable pageable) {
    Criteria criteria = Criteria.where(TokenKeys.accountIdentifier).is(accountIdentifier);

    if (parentIdentifier != null) {
      criteria.and(TokenKeys.parentIdentifier).is(parentIdentifier);
    }

    if (apiKeyTypes != null && !apiKeyTypes.isEmpty()) {
      criteria.and(TokenKeys.apiKeyType).in(apiKeyTypes);
    }

    return findAll(criteria, pageable);
  }

  @Override
  public long deleteAll(Criteria criteria) {
    return mongoTemplate.remove(new Query(criteria), Token.class).getDeletedCount();
  }

  @Override
  public List<Token> findScopedTokensNeedingPermissionsBackfill(int batchSize) {
    // An entry "needs backfill" when the deprecated single `permission` is set
    // AND the new `permissions` list is either missing or an empty array.
    // We match docs that contain at least one such entry via $elemMatch.
    Criteria missingPermissions =
        new Criteria().andOperator(Criteria.where(ScopedResourcePermissionKeys.permission).ne(null).ne(""),
            Criteria.where(ScopedResourcePermissionKeys.permissions).exists(false));
    Criteria emptyPermissions =
        new Criteria().andOperator(Criteria.where(ScopedResourcePermissionKeys.permission).ne(null).ne(""),
            Criteria.where(ScopedResourcePermissionKeys.permissions).size(0));

    Criteria criteria = new Criteria().andOperator(Criteria.where(TokenKeys.apiKeyType).is(ApiKeyType.SCOPED_TOKEN),
        new Criteria().orOperator(Criteria.where(TokenKeys.scopedResourcePermissions).elemMatch(missingPermissions),
            Criteria.where(TokenKeys.scopedResourcePermissions).elemMatch(emptyPermissions)));

    return mongoTemplate.find(new Query(criteria).limit(batchSize), Token.class);
  }
}

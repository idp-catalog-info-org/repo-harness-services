/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.code.CodeResourceClient;
import io.harness.code.KeyUpdatePayload;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.api.PublicKeyRevoker;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.RevocationReason;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.entities.Token.TokenKeys;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.ng.core.spring.TokenRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Public key revoker implementation that notifies the code-api service.
 * Handles COMPROMISED revocations where external services need to be notified
 * to invalidate already signed commits.
 */
@Singleton
@OwnedBy(PL)
public class CodeApiPublicKeyRevoker implements PublicKeyRevoker {
  private static final int MAX_PAGE_SIZE = 1024;
  private static final String PGP_PARENT_KEY_ID_FIELD = "pgpPublicKey.parentKeyId";

  private final CodeResourceClient codeResourceClient;
  private final TokenRepository tokenRepository;

  @Inject
  public CodeApiPublicKeyRevoker(CodeResourceClient codeResourceClient, TokenRepository tokenRepository) {
    this.codeResourceClient = codeResourceClient;
    this.tokenRepository = tokenRepository;
  }

  @Override
  public boolean handles(RevocationReason reason) {
    return RevocationReason.COMPROMISED.equals(reason);
  }

  @Override
  public void revoke(ScopeInfo scopeInfo, Token token) {
    String principalIdentifier = token.getParentIdentifier();
    KeyUpdatePayload payload;

    if (ApiKeyType.PGP_KEY.equals(token.getApiKeyType()) && token.getPgpPublicKey() != null) {
      List<String> keyIds = collectPGPKeyIds(scopeInfo, token);
      payload =
          KeyUpdatePayload.builder().principalIdentifier(principalIdentifier).keyIds(keyIds).keyScheme("pgp").build();
    } else if (ApiKeyType.SSH_KEY.equals(token.getApiKeyType()) && token.getSshPublicKey() != null) {
      payload = KeyUpdatePayload.builder()
                    .principalIdentifier(principalIdentifier)
                    .fingerprint(token.getSshPublicKey().getFingerPrint())
                    .keyScheme("ssh")
                    .build();
    } else {
      throw new InvalidRequestException("Unsupported key type for revocation");
    }

    try {
      NGRestUtils.getGeneralResponse(codeResourceClient.revokePublicKey(scopeInfo.getAccountIdentifier(), payload));
    } catch (Exception e) {
      throw new InvalidRequestException("Failed to revoke key in code service", e);
    }
  }

  private List<String> collectPGPKeyIds(ScopeInfo scopeInfo, Token token) {
    List<String> keyIds = new ArrayList<>();
    String primaryKeyId = token.getPgpPublicKey().getKeyId();

    if (primaryKeyId != null) {
      keyIds.add(primaryKeyId);
    }

    // If this is a primary key, also collect all subkey IDs
    if (isPrimaryPGPKey(token) && primaryKeyId != null) {
      Criteria subKeysCriteria = Criteria.where(TokenKeys.accountIdentifier)
                                     .is(scopeInfo.getAccountIdentifier())
                                     .and(TokenKeys.apiKeyType)
                                     .is(ApiKeyType.PGP_KEY)
                                     .and(PGP_PARENT_KEY_ID_FIELD)
                                     .is(primaryKeyId);

      Page<Token> subKeysPage = tokenRepository.findAll(subKeysCriteria, Pageable.ofSize(MAX_PAGE_SIZE));
      for (Token subKey : subKeysPage.getContent()) {
        if (subKey.getPgpPublicKey() != null && subKey.getPgpPublicKey().getKeyId() != null) {
          keyIds.add(subKey.getPgpPublicKey().getKeyId());
        }
      }
    }

    return keyIds;
  }

  private boolean isPrimaryPGPKey(Token token) {
    if (token.getPgpPublicKey() == null) {
      return false;
    }
    String parentKeyId = token.getPgpPublicKey().getParentKeyId();
    Boolean isSubKey = token.getPgpPublicKey().getIsSubKey();
    return isEmpty(parentKeyId) && (isSubKey == null || !isSubKey);
  }
}

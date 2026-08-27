/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.util;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/** Stores provider-created child OAuth credentials as account-scoped Harness-encrypted records. */
@OwnedBy(CI)
@Singleton
public class PrivateConnectivityChildCredentialService {
  private static final String SECRET_IDENTIFIER_PREFIX = "__INTERNAL_pc_tailnet_oauth_";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final NGEncryptedDataService encryptedDataService;

  @Inject
  public PrivateConnectivityChildCredentialService(NGEncryptedDataService encryptedDataService) {
    this.encryptedDataService = encryptedDataService;
  }

  /** Store the credential under the deterministic reference persisted before tailnet creation. */
  public String store(String accountIdentifier, String providerNetworkName, String clientId, String clientSecret) {
    if (StringUtils.isAnyBlank(accountIdentifier, providerNetworkName, clientId, clientSecret)) {
      throw new InvalidRequestException(
          "Account, provider network name, child OAuth client ID, and child OAuth secret are required");
    }
    String secretIdentifier = secretIdentifier(providerNetworkName);
    ScopeInfo scopeInfo = accountScope(accountIdentifier);

    final String serialized;
    try {
      serialized = MAPPER.writeValueAsString(new StoredCredential(clientId, clientSecret));
    } catch (JsonProcessingException exception) {
      throw new InvalidRequestException("Unable to serialize the provider child OAuth credential");
    }
    SecretDTOV2 secret = SecretDTOV2.builder()
                             .identifier(secretIdentifier)
                             .name(secretIdentifier)
                             .orgIdentifier(null)
                             .projectIdentifier(null)
                             .type(SecretType.SecretText)
                             .parentUniqueId(accountIdentifier)
                             .spec(SecretTextSpecDTO.builder()
                                       .secretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                       .valueType(ValueType.Inline)
                                       .value(serialized)
                                       .build())
                             .build();
    encryptedDataService.createSecretText(scopeInfo, secret);
    return secretIdentifier;
  }

  public ChildCredential load(String accountIdentifier, String secretIdentifier) {
    if (StringUtils.isAnyBlank(accountIdentifier, secretIdentifier)) {
      throw new InvalidRequestException("Account and child OAuth secret reference are required");
    }
    validateSecretIdentifier(secretIdentifier);
    DecryptedSecretValue decrypted =
        encryptedDataService.decryptSecret(accountScope(accountIdentifier), secretIdentifier);
    if (decrypted == null || StringUtils.isBlank(decrypted.getDecryptedValue())) {
      throw new InvalidRequestException("The provider child OAuth credential could not be loaded");
    }
    try {
      StoredCredential stored = MAPPER.readValue(decrypted.getDecryptedValue(), StoredCredential.class);
      if (stored == null || StringUtils.isAnyBlank(stored.clientId(), stored.clientSecret())) {
        throw new InvalidRequestException("The provider child OAuth credential is incomplete");
      }
      return new ChildCredential(stored.clientId(), stored.clientSecret());
    } catch (JsonProcessingException exception) {
      // Parser causes may contain source excerpts. This source contains the provider secret.
      throw new InvalidRequestException("The provider child OAuth credential is malformed");
    }
  }

  public Optional<ChildCredential> loadForProviderNetworkName(String accountIdentifier, String providerNetworkName) {
    String secretIdentifier = secretIdentifier(providerNetworkName);
    if (encryptedDataService.get(accountScope(accountIdentifier), secretIdentifier) == null) {
      return Optional.empty();
    }
    return Optional.of(load(accountIdentifier, secretIdentifier));
  }

  /** Delete the credential only after provider-network absence has been confirmed. */
  public void delete(String accountIdentifier, String secretIdentifier) {
    if (StringUtils.isBlank(secretIdentifier)) {
      return;
    }
    if (StringUtils.isBlank(accountIdentifier)) {
      throw new InvalidRequestException("Account is required to delete a child OAuth credential");
    }
    validateSecretIdentifier(secretIdentifier);
    ScopeInfo scopeInfo = accountScope(accountIdentifier);
    if (encryptedDataService.get(scopeInfo, secretIdentifier) == null) {
      return;
    }
    encryptedDataService.delete(scopeInfo, secretIdentifier, false);
    if (encryptedDataService.get(scopeInfo, secretIdentifier) != null) {
      throw new InvalidRequestException("The provider child OAuth credential could not be deleted");
    }
  }

  public String secretIdentifier(String providerNetworkName) {
    if (StringUtils.isBlank(providerNetworkName)) {
      throw new InvalidRequestException("Provider network name is required for the child OAuth secret reference");
    }
    return SECRET_IDENTIFIER_PREFIX + providerNetworkName.replaceAll("[^a-zA-Z0-9_$]", "_") + "__";
  }

  private static ScopeInfo accountScope(String accountIdentifier) {
    return ScopeInfo.builder()
        .accountIdentifier(accountIdentifier)
        .scopeType(ScopeLevel.ACCOUNT)
        .uniqueId(accountIdentifier)
        .build();
  }

  private static void validateSecretIdentifier(String secretIdentifier) {
    if (!secretIdentifier.startsWith(SECRET_IDENTIFIER_PREFIX)) {
      throw new InvalidRequestException("Invalid provider child OAuth secret reference");
    }
  }

  public record ChildCredential(String clientId, String clientSecret) {
    @Override
    public String toString() {
      return "ChildCredential[clientId=" + clientId + ", clientSecret=<redacted>]";
    }
  }

  private record StoredCredential(String clientId, String clientSecret) {
    @Override
    public String toString() {
      return "StoredCredential[clientId=" + clientId + ", clientSecret=<redacted>]";
    }
  }
}

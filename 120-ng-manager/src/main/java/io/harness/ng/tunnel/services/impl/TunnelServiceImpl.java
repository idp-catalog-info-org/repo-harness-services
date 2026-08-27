/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.tunnel.services.impl;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.common.NGExpressionUtils.EMPTY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.TunnelRegisterRequestDTO;
import io.harness.ng.core.dto.TunnelResponseDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.tunnel.entities.Tunnel;
import io.harness.ng.tunnel.entities.Tunnel.TunnelKeys;
import io.harness.ngmanager.TunnelService;
import io.harness.persistence.HPersistence;
import io.harness.repositories.ng.tunnel.TunnelRepository;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.security.SecurityContextBuilder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CI)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class TunnelServiceImpl implements TunnelService {
  private final TunnelRepository tunnelRepository;
  private NextGenConfiguration nextGenConfiguration;
  private HPersistence persistence;
  private final SecretCrudService ngSecretService;
  private final NGEncryptedDataService encryptedDataService;
  private static final String CI_SECURE_TUNNEL_PREFIX = "CI_SECURE_TUNNEL_";
  private static final String SPECIAL_CHARACTER_REGEX = "[^a-zA-Z0-9]";

  @Override
  public Boolean registerTunnel(String accountId, TunnelRegisterRequestDTO tunnelRegisterRequestDTO) {
    if (!validateTunnel(tunnelRegisterRequestDTO)) {
      return Boolean.FALSE;
    }
    checkAndStoreUserCredentialsInSecretManager(tunnelRegisterRequestDTO, accountId);
    UpdateOperations<Tunnel> updateOperations = persistence.createUpdateOperations(Tunnel.class)
                                                    .setOnInsert(TunnelKeys.accountIdentifier, accountId)
                                                    .set(TunnelKeys.port, tunnelRegisterRequestDTO.getPort());
    Query<Tunnel> upsertQuery =
        persistence.createQuery(Tunnel.class, excludeAuthority).filter(TunnelKeys.accountIdentifier, accountId);
    persistence.upsert(upsertQuery, updateOperations);
    return Boolean.TRUE;
  }
  @Override
  public Boolean deleteTunnel(String accountId) {
    Optional<Tunnel> optionalTunnel = tunnelRepository.findByAccountIdentifier(accountId);
    if (optionalTunnel.isPresent()) {
      Query<Tunnel> deleteQuery =
          persistence.createQuery(Tunnel.class, excludeAuthority).filter(TunnelKeys.accountIdentifier, accountId);
      if (persistence.delete(deleteQuery)) {
        return checkAndDeleteUserCredentialsInSecretManager(accountId, optionalTunnel.get().getPort());
      }
    }
    return false;
  }

  @Override
  public TunnelResponseDTO getTunnel(String accountId) {
    Optional<Tunnel> optionalTunnel = tunnelRepository.findByAccountIdentifier(accountId);
    if (optionalTunnel.isEmpty()) {
      return TunnelResponseDTO.builder().serverUrl("").port("").build();
    }
    return TunnelResponseDTO.builder()
        .serverUrl(nextGenConfiguration.getFrpsTunnelConfig().getHost())
        .port(optionalTunnel.get().getPort())
        .userCredentials(getDecryptedUserCredentialsFromSecretManager(accountId, optionalTunnel.get().getPort()))
        .build();
  }

  private boolean validateTunnel(TunnelRegisterRequestDTO tunnelRegisterRequestDTO) {
    if (isEmpty(tunnelRegisterRequestDTO.getPort())) {
      log.error("Port number cannot be empty for tunnel creation");
      return false;
    }

    try {
      int port = Integer.parseInt(tunnelRegisterRequestDTO.getPort());
      return port >= 0 && port <= 65535;
    } catch (NumberFormatException e) {
      // If parsing as an integer fails, it's not a valid port number.
      log.error("Port number should be an integer value", e);
      return false;
    }
  }

  private void checkAndStoreUserCredentialsInSecretManager(
      TunnelRegisterRequestDTO tunnelRegisterRequestDTO, String accountId) {
    if (isNotEmpty(tunnelRegisterRequestDTO.getUserCredentials())) {
      String secretKeyIdentifier = getIdentifierForTunnelUserCredentials(accountId, tunnelRegisterRequestDTO.getPort());
      SecretDTOV2 secretDto = SecretDTOV2.builder()
                                  .identifier(secretKeyIdentifier)
                                  .name(secretKeyIdentifier)
                                  .orgIdentifier(null)
                                  .projectIdentifier(null)
                                  .type(SecretType.SecretText)
                                  .parentUniqueId(accountId)
                                  .spec(SecretTextSpecDTO.builder()
                                            .secretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                            .valueType(ValueType.Inline)
                                            .value(tunnelRegisterRequestDTO.getUserCredentials())
                                            .build())
                                  .build();
      // this is to make the secret invisible to the user
      secretDto.setOwner(SecurityContextBuilder.getPrincipal());

      ScopeInfo scopeInfo = getScopeInfoForSecretCreation(accountId);

      if (doesSecretExistsInSecretManager(accountId, tunnelRegisterRequestDTO.getPort())) {
        ngSecretService.update(scopeInfo, secretKeyIdentifier, secretDto);
      } else {
        ngSecretService.create(scopeInfo, secretDto);
      }
    }
  }

  private boolean checkAndDeleteUserCredentialsInSecretManager(String accountId, String port) {
    if (doesSecretExistsInSecretManager(accountId, port)) {
      return ngSecretService.delete(
          getScopeInfoForSecretCreation(accountId), getIdentifierForTunnelUserCredentials(accountId, port), false);
    }
    return true;
  }

  private boolean doesSecretExistsInSecretManager(String accountId, String port) {
    String secretKeyIdentifier = getIdentifierForTunnelUserCredentials(accountId, port);
    return ngSecretService.get(getScopeInfoForSecretCreation(accountId), secretKeyIdentifier).isPresent();
  }

  private String getDecryptedUserCredentialsFromSecretManager(String accountId, String port) {
    if (doesSecretExistsInSecretManager(accountId, port)) {
      DecryptedSecretValue decryptedSecretValue = encryptedDataService.decryptSecret(
          getScopeInfoForSecretCreation(accountId), getIdentifierForTunnelUserCredentials(accountId, port));
      return decryptedSecretValue.getDecryptedValue();
    }
    return EMPTY;
  }

  private String getIdentifierForTunnelUserCredentials(String accountId, String port) {
    return CI_SECURE_TUNNEL_PREFIX + accountId.replaceAll(SPECIAL_CHARACTER_REGEX, "_") + port;
  }

  private ScopeInfo getScopeInfoForSecretCreation(String accountId) {
    return ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();
  }
}

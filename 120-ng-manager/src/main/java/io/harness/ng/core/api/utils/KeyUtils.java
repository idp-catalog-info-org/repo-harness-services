/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.utils;

import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PGPKeyUsage;
import io.harness.ng.core.common.beans.PublicKeyScheme;
import io.harness.ng.core.common.beans.PublicKeyUsage;
import io.harness.ng.core.common.beans.SSHKeyUsage;
import io.harness.ng.core.dto.KeyDTO;
import io.harness.ng.core.dto.TokenDTO;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class KeyUtils {
  public static String API_KEY_KEY_IDENTIFIER = "api_key_key_identifier";

  public KeyDTO mapTokenToKey(TokenDTO tokenDTO) {
    if (ApiKeyType.PGP_KEY.equals(tokenDTO.getApiKeyType())) {
      return KeyDTO.builder()
          .identifier(tokenDTO.getIdentifier())
          .name(tokenDTO.getName())
          .validFrom(tokenDTO.getValidFrom())
          .validTo(tokenDTO.getValidTo())
          .accountIdentifier(tokenDTO.getAccountIdentifier())
          .parentIdentifier(tokenDTO.getParentIdentifier())
          .description(tokenDTO.getDescription())
          .tags(tokenDTO.getTags())
          .key(tokenDTO.getContent())
          .keyFingerprint(tokenDTO.getPgpKeyFingerprint())
          .keyUsage(convertPGPUsagesToPublicKeyUsages(tokenDTO.getPgpKeyUsage()))
          .keyId(tokenDTO.getPgpKeyId())
          .keyAlgorithm(tokenDTO.getPgpKeyAlgorithm())
          .keyScheme(PublicKeyScheme.PGP)
          .identities(tokenDTO.getPgpIdentities())
          .primaryIdentity(tokenDTO.getPgpPrimaryIdentity())
          .parentKeyId(tokenDTO.getPgpParentKeyId())
          .isSubKey(tokenDTO.getPgpIsSubKey())
          .revocationReason(tokenDTO.getRevocationReason())
          .build();
    } else {
      return KeyDTO.builder()
          .identifier(tokenDTO.getIdentifier())
          .name(tokenDTO.getName())
          .validFrom(tokenDTO.getValidFrom())
          .validTo(tokenDTO.getValidTo())
          .accountIdentifier(tokenDTO.getAccountIdentifier())
          .parentIdentifier(tokenDTO.getParentIdentifier())
          .description(tokenDTO.getDescription())
          .tags(tokenDTO.getTags())
          .key(tokenDTO.getSshKeyContent())
          .keyUsage(convertSSHUsagesToPublicKeyUsages(tokenDTO.getSshKeyUsage()))
          .keyFingerprint(tokenDTO.getSshKeyFingerprint())
          .keyScheme(PublicKeyScheme.SSH)
          .keyId(null)
          .primaryUserId(null)
          .keyAlgorithm(null)
          .parentKeyId(null)
          .isSubKey(false)
          .revocationReason(tokenDTO.getRevocationReason())
          .build();
    }
  }

  private static List<PublicKeyUsage> convertSSHUsagesToPublicKeyUsages(List<SSHKeyUsage> sshUsages) {
    if (sshUsages == null) {
      return null;
    }
    List<PublicKeyUsage> publicUsages = new ArrayList<>();
    for (SSHKeyUsage sshUsage : sshUsages) {
      switch (sshUsage) {
        case SIGN:
          publicUsages.add(PublicKeyUsage.SIGN);
          break;
        case AUTH:
          publicUsages.add(PublicKeyUsage.AUTH);
          break;
        default:
          break;
      }
    }
    return publicUsages;
  }

  private static List<PublicKeyUsage> convertPGPUsagesToPublicKeyUsages(List<PGPKeyUsage> pgpUsages) {
    if (pgpUsages == null) {
      return null;
    }
    List<PublicKeyUsage> publicUsages = new ArrayList<>();
    for (PGPKeyUsage pgpUsage : pgpUsages) {
      switch (pgpUsage) {
        case SIGN:
          publicUsages.add(PublicKeyUsage.SIGN);
          break;
        case ENCRYPT:
          publicUsages.add(PublicKeyUsage.ENCRYPT);
          break;
        case AUTH:
          publicUsages.add(PublicKeyUsage.AUTH);
          break;
        case CERTIFY:
          publicUsages.add(PublicKeyUsage.CERTIFY);
          break;
        default:
          break;
      }
    }
    return publicUsages;
  }

  private static List<SSHKeyUsage> convertPublicKeyUsagesToSSHUsages(List<PublicKeyUsage> publicUsages) {
    if (publicUsages == null) {
      return null;
    }
    List<SSHKeyUsage> sshUsages = new ArrayList<>();
    for (PublicKeyUsage publicUsage : publicUsages) {
      switch (publicUsage) {
        case SIGN:
          sshUsages.add(SSHKeyUsage.SIGN);
          break;
        case AUTH:
          sshUsages.add(SSHKeyUsage.AUTH);
          break;
        default:
          // SSH doesn't support ENCRYPT or CERTIFY, so skip those
          break;
      }
    }
    return sshUsages;
  }

  private static List<PGPKeyUsage> convertPublicKeyUsagesToPGPUsages(List<PublicKeyUsage> publicUsages) {
    if (publicUsages == null) {
      return null;
    }
    List<PGPKeyUsage> pgpUsages = new ArrayList<>();
    for (PublicKeyUsage publicUsage : publicUsages) {
      switch (publicUsage) {
        case SIGN:
          pgpUsages.add(PGPKeyUsage.SIGN);
          break;
        case ENCRYPT:
          pgpUsages.add(PGPKeyUsage.ENCRYPT);
          break;
        case AUTH:
          pgpUsages.add(PGPKeyUsage.AUTH);
          break;
        case CERTIFY:
          pgpUsages.add(PGPKeyUsage.CERTIFY);
          break;
        default:
          break;
      }
    }
    return pgpUsages;
  }

  public TokenDTO mapKeyToToken(KeyDTO keyDTO, ApiKeyType apiKeyType) {
    var builder = TokenDTO.builder()
                      .apiKeyType(apiKeyType)
                      .description(keyDTO.getDescription())
                      .tags(keyDTO.getTags())
                      .parentIdentifier(keyDTO.getParentIdentifier())
                      .accountIdentifier(keyDTO.getAccountIdentifier())
                      .name(keyDTO.getName())
                      .identifier(keyDTO.getIdentifier())
                      .validFrom(keyDTO.getValidFrom())
                      .validTo(keyDTO.getValidTo())
                      .apiKeyIdentifier(API_KEY_KEY_IDENTIFIER);

    if (ApiKeyType.PGP_KEY.equals(apiKeyType)) {
      builder.content(keyDTO.getKey())
          .pgpKeyFingerprint(keyDTO.getKeyFingerprint())
          .pgpKeyUsage(convertPublicKeyUsagesToPGPUsages(keyDTO.getKeyUsage()))
          .pgpKeyId(keyDTO.getKeyId())
          .pgpPrimaryUserId(keyDTO.getPrimaryUserId())
          .pgpKeyAlgorithm(keyDTO.getKeyAlgorithm());
    } else {
      // For SSH keys, default to AUTH usage if not provided (backward compatibility)
      List<PublicKeyUsage> keyUsages = keyDTO.getKeyUsage();
      if (keyUsages == null || keyUsages.isEmpty()) {
        keyUsages = List.of(PublicKeyUsage.AUTH);
      }
      builder.sshKeyContent(keyDTO.getKey())
          .sshKeyFingerprint(keyDTO.getKeyFingerprint())
          .sshKeyUsage(convertPublicKeyUsagesToSSHUsages(keyUsages));
    }

    return builder.build();
  }

  public TokenDTO mapKeyToToken(KeyDTO keyDTO) {
    return mapKeyToToken(keyDTO, ApiKeyType.SSH_KEY);
  }
}

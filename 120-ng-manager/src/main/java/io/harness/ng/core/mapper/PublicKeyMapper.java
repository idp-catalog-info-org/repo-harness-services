/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.mapper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PGPKeyUsage;
import io.harness.ng.core.common.beans.PublicKeyScheme;
import io.harness.ng.core.common.beans.PublicKeyUsage;
import io.harness.ng.core.common.beans.SSHKeyUsage;
import io.harness.ng.core.common.beans.SSHPublicKey;
import io.harness.ng.core.dto.PublicKeyDTO;
import io.harness.ng.core.dto.PublicKeyDTO.PublicKeyDTOBuilder;
import io.harness.ng.core.entities.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
@OwnedBy(PL)
public class PublicKeyMapper {
  public static PublicKeyDTO fromToken(Token token) {
    if (token == null) {
      return null;
    }

    PublicKeyDTOBuilder builder = PublicKeyDTO.builder()
                                      .identifier(token.getIdentifier())
                                      .name(token.getName())
                                      .accountIdentifier(token.getAccountIdentifier())
                                      .principalIdentifier(token.getParentIdentifier())
                                      .createdAt(token.getCreatedAt());

    if (ApiKeyType.SSH_KEY.equals(token.getApiKeyType()) && token.getSshPublicKey() != null) {
      builder.scheme(PublicKeyScheme.SSH)
          .fingerprint(token.getSshPublicKey().getFingerPrint())
          .algorithm(token.getSshPublicKey().getAlgorithm())
          .key(convertSSHKeyToContent(token.getSshPublicKey()))
          .usages(convertSSHUsages(token.getSshPublicKey().getKeyUsage()));
    } else if (ApiKeyType.PGP_KEY.equals(token.getApiKeyType()) && token.getPgpPublicKey() != null) {
      builder.scheme(PublicKeyScheme.PGP)
          .fingerprint(token.getPgpPublicKey().getFingerprint())
          .keyId(token.getPgpPublicKey().getKeyId())
          .algorithm(token.getPgpPublicKey().getAlgorithm())
          .bitLength(token.getPgpPublicKey().getBitLength())
          .key(token.getPgpPublicKey().getPgpKeyContent())
          .usages(convertPGPUsages(token.getPgpPublicKey().getUsage()))
          .parentKeyId(token.getPgpPublicKey().getParentKeyId())
          .isSubKey(token.getPgpPublicKey().getIsSubKey())
          .comment(token.getPgpPublicKey().getComment())
          .validFrom(token.getPgpPublicKey().getValidFrom())
          .validTo(token.getPgpPublicKey().getValidTo())
          .identities(token.getPgpPublicKey().getIdentities())
          .primaryIdentity(token.getPgpPublicKey().getPrimaryIdentity());
    }

    return builder.build();
  }

  public static List<PublicKeyDTO> fromTokens(List<Token> tokens) {
    if (tokens == null) {
      return new ArrayList<>();
    }
    return tokens.stream().map(PublicKeyMapper::fromToken).collect(Collectors.toList());
  }

  private static List<PublicKeyUsage> convertSSHUsages(List<SSHKeyUsage> sshUsages) {
    if (sshUsages == null) {
      return new ArrayList<>();
    }
    return sshUsages.stream()
        .map(sshUsage -> {
          switch (sshUsage) {
            case SIGN:
              return PublicKeyUsage.SIGN;
            case AUTH:
              return PublicKeyUsage.AUTH;
            default:
              throw new IllegalArgumentException("Unsupported SSH key usage: " + sshUsage);
          }
        })
        .collect(Collectors.toList());
  }

  private static List<PublicKeyUsage> convertPGPUsages(List<PGPKeyUsage> pgpUsages) {
    if (pgpUsages == null) {
      return new ArrayList<>();
    }
    return pgpUsages.stream()
        .map(pgpUsage -> {
          switch (pgpUsage) {
            case SIGN:
              return PublicKeyUsage.SIGN;
            case ENCRYPT:
              return PublicKeyUsage.ENCRYPT;
            case AUTH:
              return PublicKeyUsage.AUTH;
            case CERTIFY:
              return PublicKeyUsage.CERTIFY;
            default:
              throw new IllegalArgumentException("Unsupported PGP key usage: " + pgpUsage);
          }
        })
        .collect(Collectors.toList());
  }

  private static String convertSSHKeyToContent(SSHPublicKey sshPublicKey) {
    if (sshPublicKey == null) {
      return null;
    }
    StringBuilder key = new StringBuilder();
    if (StringUtils.isNotEmpty(sshPublicKey.getAlgorithm())) {
      key.append(sshPublicKey.getAlgorithm());
    }
    if (StringUtils.isNotEmpty(sshPublicKey.getSshKey())) {
      if (key.length() > 0) {
        key.append(' ');
      }
      key.append(sshPublicKey.getSshKey());
    }
    if (StringUtils.isNotEmpty(sshPublicKey.getComment())) {
      key.append(' ').append(sshPublicKey.getComment());
    }
    return key.toString();
  }
}
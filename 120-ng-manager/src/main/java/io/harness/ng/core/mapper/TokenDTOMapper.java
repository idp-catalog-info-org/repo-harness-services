/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.mapper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PGPKeyUsage;
import io.harness.ng.core.common.beans.RevocationReason;
import io.harness.ng.core.common.beans.SSHPublicKey;
import io.harness.ng.core.dto.PGPPublicKeyDTOInternal;
import io.harness.ng.core.dto.SSHPublicKeyDTOInternal;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.TokenDTOInternal;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.entities.Token.TokenBuilder;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@UtilityClass
public class TokenDTOMapper {
  private static final String SUBKEY_NAME_SUFFIX = "Subkey";

  public Token getTokenFromDTO(ScopeInfo scopeInfo, TokenDTO dto, Long defaultTimeout) {
    TokenBuilder tokenBuilder = Token.builder()
                                    .identifier(dto.getIdentifier())
                                    .name(dto.getName())
                                    .description(dto.getDescription())
                                    .tags(TagMapper.convertToList(dto.getTags()))
                                    .apiKeyIdentifier(dto.getApiKeyIdentifier())
                                    .parentIdentifier(dto.getParentIdentifier())
                                    .apiKeyType(dto.getApiKeyType())
                                    .accountIdentifier(scopeInfo.getAccountIdentifier())
                                    .orgIdentifier(scopeInfo.getOrgIdentifier())
                                    .projectIdentifier(scopeInfo.getProjectIdentifier());
    Instant validFrom = dto.getValidFrom() != null ? Instant.ofEpochMilli(dto.getValidFrom()) : Instant.now();
    Instant validTo =
        dto.getValidTo() != null ? Instant.ofEpochMilli(dto.getValidTo()) : validFrom.plusMillis(defaultTimeout);
    tokenBuilder.validFrom(validFrom);
    tokenBuilder.validTo(validTo);
    if (dto.getScheduledExpireTime() != null) {
      tokenBuilder.scheduledExpireTime(Instant.ofEpochMilli(dto.getScheduledExpireTime()));
    }
    if (ApiKeyType.SCOPED_TOKEN.equals(dto.getApiKeyType())) {
      tokenBuilder.scopedResourcePermissions(dto.getScopedResourcePermissions())
          .tokenMode(dto.getTokenMode())
          .scopedResourceMetadata(dto.getScopedResourceMetadata());
    }
    Token token = tokenBuilder.build();
    token.setValidUntil(new Date(token.getExpiryTimestamp().toEpochMilli()));
    return token;
  }

  public Token getTokenFromDTOAndSSHPublicKey(
      ScopeInfo scopeInfo, TokenDTO dto, SSHPublicKeyDTOInternal sshPublicKeyDTOInternal) {
    return Token.builder()
        .identifier(dto.getIdentifier())
        .name(dto.getName())
        .description(dto.getDescription())
        .tags(TagMapper.convertToList(dto.getTags()))
        .parentIdentifier(dto.getParentIdentifier())
        .apiKeyType(dto.getApiKeyType())
        .apiKeyIdentifier(dto.getApiKeyIdentifier())
        .accountIdentifier(scopeInfo.getAccountIdentifier())
        .orgIdentifier(scopeInfo.getOrgIdentifier())
        .projectIdentifier(scopeInfo.getProjectIdentifier())
        .sshPublicKey(sshPublicKeyDTOInternal.toSSHKey())
        .build();
  }

  public Token getTokenFromDTOAndPGPPublicKey(
      ScopeInfo scopeInfo, TokenDTO dto, PGPPublicKeyDTOInternal pgpPublicKeyDTOInternal) {
    return getTokenFromDTOAndPGPPublicKey(scopeInfo, dto, pgpPublicKeyDTOInternal, dto.getIdentifier());
  }

  public Token getTokenFromDTOAndPGPPublicKey(
      ScopeInfo scopeInfo, TokenDTO dto, PGPPublicKeyDTOInternal pgpPublicKeyDTOInternal, String identifier) {
    String name = dto.getName();
    if (Boolean.TRUE.equals(pgpPublicKeyDTOInternal.getIsSubKey())) {
      name = dto.getName() + " (" + SUBKEY_NAME_SUFFIX + " " + pgpPublicKeyDTOInternal.getKeyId() + ")";
    }

    return Token.builder()
        .identifier(identifier)
        .name(name)
        .description(dto.getDescription())
        .tags(TagMapper.convertToList(dto.getTags()))
        .parentIdentifier(dto.getParentIdentifier())
        .apiKeyType(dto.getApiKeyType())
        .apiKeyIdentifier(dto.getApiKeyIdentifier())
        .accountIdentifier(scopeInfo.getAccountIdentifier())
        .orgIdentifier(scopeInfo.getOrgIdentifier())
        .projectIdentifier(scopeInfo.getProjectIdentifier())
        .pgpPublicKey(pgpPublicKeyDTOInternal.toPGPKey())
        .validFrom(pgpPublicKeyDTOInternal.getValidFrom() != null
                ? Instant.ofEpochMilli(pgpPublicKeyDTOInternal.getValidFrom())
                : null)
        .validTo(pgpPublicKeyDTOInternal.getValidTo() != null
                ? Instant.ofEpochMilli(pgpPublicKeyDTOInternal.getValidTo())
                : null)
        .build();
  }

  public TokenDTO getDTOFromTokenForRotation(ScopeInfo scopeInfo, Token token) {
    TokenDTO dto = TokenDTO.builder()
                       .identifier(token.getIdentifier())
                       .name(token.getName())
                       .description(token.getDescription())
                       .tags(TagMapper.convertToMap(token.getTags()))
                       .validFrom(Instant.now().toEpochMilli())
                       .validTo(token.getValidTo().toEpochMilli())
                       .apiKeyIdentifier(token.getApiKeyIdentifier())
                       .parentIdentifier(token.getParentIdentifier())
                       .apiKeyType(token.getApiKeyType())
                       .accountIdentifier(scopeInfo.getAccountIdentifier())
                       .orgIdentifier(scopeInfo.getOrgIdentifier())
                       .projectIdentifier(scopeInfo.getProjectIdentifier())
                       .build();
    if (ApiKeyType.SCOPED_TOKEN.equals(token.getApiKeyType())) {
      // Carry over scoped-token specific fields so the rotated token retains
      // the same permissions, mode, and metadata (incl. createdBy) as the original.
      dto.setScopedResourcePermissions(token.getScopedResourcePermissions());
      dto.setTokenMode(token.getTokenMode());
      dto.setScopedResourceMetadata(token.getScopedResourceMetadata());
    }
    return dto;
  }

  public TokenDTO getDTOFromToken(Token token, ScopeInfo scopeInfo) {
    TokenDTO.Builder builder = TokenDTO.Builder();
    builder.identifier(token.getIdentifier())
        .name(token.getName())
        .apiKeyIdentifier(token.getApiKeyIdentifier())
        .parentIdentifier(token.getParentIdentifier())
        .apiKeyType(token.getApiKeyType())
        .accountIdentifier(token.getAccountIdentifier())
        .scheduledExpireTime(
            token.getScheduledExpireTime() != null ? token.getScheduledExpireTime().toEpochMilli() : null)
        .description(token.getDescription())
        .tags(TagMapper.convertToMap(token.getTags()))
        .parentUniqueId(scopeInfo.getUniqueId())
        .uniqueId(token.getUniqueId());

    if (!ApiKeyType.SSH_KEY.equals(token.getApiKeyType()) && !ApiKeyType.PGP_KEY.equals(token.getApiKeyType())) {
      builder.validFrom(token.getValidFrom().toEpochMilli())
          .validTo(token.getValidTo() != null ? token.getValidTo().toEpochMilli() : null)
          .orgIdentifier(scopeInfo.getOrgIdentifier())
          .projectIdentifier(scopeInfo.getProjectIdentifier())
          .valid(token.isValid());
    }

    if (ApiKeyType.SSH_KEY.equals(token.getApiKeyType())) {
      RevocationReason sshRevocationReason =
          token.getSshPublicKey() != null ? token.getSshPublicKey().getRevocationReason() : null;
      builder.validTo(null)
          .sshKeyFingerprint(token.getSshPublicKey().getFingerPrint())
          .sshKeyContent(convertSSHKeyToContent(token.getSshPublicKey()))
          .sshKeyUsage(token.getSshPublicKey().getKeyUsage())
          .revocationReason(sshRevocationReason);
    }

    if (ApiKeyType.SCOPED_TOKEN.equals(token.getApiKeyType())) {
      builder.scopedResourcePermissions(token.getScopedResourcePermissions())
          .tokenMode(token.getTokenMode())
          .scopedResourceMetadata(token.getScopedResourceMetadata());
    }

    if (ApiKeyType.PGP_KEY.equals(token.getApiKeyType())) {
      Long pgpValidFrom = token.getPgpPublicKey().getValidFrom();
      Long pgpValidTo = token.getPgpPublicKey().getValidTo();
      List<PGPKeyUsage> pgpUsage = token.getPgpPublicKey().getUsage();
      if (pgpUsage == null || pgpUsage.isEmpty()) {
        pgpUsage = List.of(PGPKeyUsage.SIGN);
      }
      RevocationReason pgpRevocationReason =
          token.getPgpPublicKey() != null ? token.getPgpPublicKey().getRevocationReason() : null;
      builder.validFrom(pgpValidFrom)
          .validTo(pgpValidTo)
          .pgpKeyFingerprint(token.getPgpPublicKey().getFingerprint())
          .content(token.getPgpPublicKey().getPgpKeyContent())
          .pgpKeyUsage(pgpUsage)
          .pgpKeyId(token.getPgpPublicKey().getKeyId())
          .pgpPrimaryUserId(null)
          .pgpKeyAlgorithm(token.getPgpPublicKey().getAlgorithm())
          .pgpIdentities(token.getPgpPublicKey().getIdentities())
          .pgpPrimaryIdentity(token.getPgpPublicKey().getPrimaryIdentity())
          .pgpParentKeyId(token.getPgpPublicKey().getParentKeyId())
          .pgpIsSubKey(token.getPgpPublicKey().getIsSubKey())
          .revocationReason(pgpRevocationReason);
    }

    return builder.build();
  }

  public static TokenDTOInternal getTokenDTOInternalFromTokenDTO(TokenDTO token) {
    return (TokenDTOInternal) TokenDTOInternal.builder()
        .identifier(token.getIdentifier())
        .email(token.getEmail())
        .username(token.getUsername())
        .encodedPassword(token.getEncodedPassword())
        .parentEntityUniqueIdInternal(token.getParentEntityUniqueId())
        .parentUniqueId(token.getParentUniqueId())
        .uniqueId(token.getUniqueId())
        .name(token.getName())
        .validFrom(token.getValidFrom())
        .validTo(token.getValidTo())
        .apiKeyIdentifier(token.getApiKeyIdentifier())
        .parentIdentifier(token.getParentIdentifier())
        .apiKeyType(token.getApiKeyType())
        .accountIdentifier(token.getAccountIdentifier())
        .orgIdentifier(token.getOrgIdentifier())
        .projectIdentifier(token.getProjectIdentifier())
        .scheduledExpireTime(token.getScheduledExpireTime())
        .valid(token.isValid())
        .description(token.getDescription())
        .tags(token.getTags())
        .scopedResourcePermissions(token.getScopedResourcePermissions())
        .tokenMode(token.getTokenMode())
        .scopedResourceMetadata(token.getScopedResourceMetadata())
        .build();
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

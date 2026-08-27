/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USER_PERMISSION;
import static io.harness.ng.core.account.ServiceAccountConfig.DEFAULT_TOKEN_LIMIT;
import static io.harness.ng.core.entities.ApiKey.DEFAULT_TTL_FOR_TOKEN;
import static io.harness.ng.core.entities.Token.pgpPublicKeyFingerPrint;
import static io.harness.ng.core.entities.Token.sshPublicKeyFingerPrint;
import static io.harness.ng.core.utils.NGUtils.validate;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2A;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.engine.governance.GovernanceMetadataErrorDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.OPAPolicyEvaluationException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.accesscontrol.PlatformResourceTypes;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.account.ServiceAccountConfig;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.PublicKeyRevoker;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.api.utils.JWTTokenFlowAuthFilterUtils;
import io.harness.ng.core.api.utils.PGPKeyUtils;
import io.harness.ng.core.api.utils.SSHKeyUtils;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PGPKeyUsage;
import io.harness.ng.core.common.beans.PGPPublicKey;
import io.harness.ng.core.common.beans.PublicKeyScheme;
import io.harness.ng.core.common.beans.PublicKeyUsage;
import io.harness.ng.core.common.beans.RevocationReason;
import io.harness.ng.core.common.beans.SSHKeyUsage;
import io.harness.ng.core.common.beans.SSHPublicKey;
import io.harness.ng.core.common.beans.ScopedResourceMetadata;
import io.harness.ng.core.common.beans.ScopedResourcePermission;
import io.harness.ng.core.common.beans.TokenMode;
import io.harness.ng.core.dto.ApiKeyDTO;
import io.harness.ng.core.dto.GatewayAccountRequestDTO;
import io.harness.ng.core.dto.PGPPublicKeyDTOInternal;
import io.harness.ng.core.dto.PublicKeyDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.SSHPublicKeyDTOInternal;
import io.harness.ng.core.dto.SSHValidateDTO;
import io.harness.ng.core.dto.TokenAggregateDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.TokenDTOInternal;
import io.harness.ng.core.dto.TokenFilterDTO;
import io.harness.ng.core.dto.UpdatePublicKeyRequest;
import io.harness.ng.core.entities.ApiKey;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.entities.Token.TokenKeys;
import io.harness.ng.core.events.TokenCreateEvent;
import io.harness.ng.core.events.TokenDeleteEvent;
import io.harness.ng.core.events.TokenUpdateEvent;
import io.harness.ng.core.mapper.PublicKeyMapper;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.mapper.TokenDTOMapper;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.opa.entities.token.TokenOpaService;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.opaclient.model.OpaConstants;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.ng.core.spring.TokenRepository;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.token.TokenValidationHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(PL)
public class TokenServiceImpl implements TokenService {
  @Inject private TokenRepository tokenRepository;
  @Inject private ApiKeyService apiKeyService;
  @Inject private ServiceAccountService serviceAccountService;
  @Inject private OutboxService outboxService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private NgUserService ngUserService;
  @Inject private AccountService accountService;
  @Inject private TokenValidationHelper tokenValidationHelper;
  @Inject private JWTTokenFlowAuthFilterUtils jwtTokenAuthFilterHelper;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject private AccessControlClient accessControlClient;
  @Inject private TokenOpaService tokenOpaService;
  @Inject private PublicKeyRevokerFactory publicKeyRevokerFactory;

  @Inject private ScopeInfoService scopeInfoService;
  @Inject private ScopeInfoClient scopeInfoClient;

  private static final String deliminator = ".";
  public static final int MAX_PAGE_SIZE = 1024;
  private static final String PGP_PARENT_KEY_ID_FIELD = "pgpPublicKey.parentKeyId";

  private static final int SCOPED_TOKEN_PERSISTENT_LIMIT = 20;
  private static final int SCOPED_TOKEN_EPHEMERAL_LIMIT = 100;
  private static final long SCOPED_TOKEN_EPHEMERAL_DEFAULT_TTL_MS = Duration.ofMinutes(5).toMillis();
  private static final long SCOPED_TOKEN_EPHEMERAL_MAX_TTL_MS = Duration.ofHours(24).toMillis();
  static final String SCOPED_TOKEN_API_KEY_USER = "_scopedTokensUser";
  static final String SCOPED_TOKEN_API_KEY_SA = "_scopedTokensSA";

  @Override
  public String createToken(TokenDTO tokenDTO, ScopeInfo scopeInfo) {
    if (ApiKeyType.SCOPED_TOKEN.equals(tokenDTO.getApiKeyType())) {
      prepareScopedToken(tokenDTO, scopeInfo);
    }
    setScopeForUserToken(tokenDTO, scopeInfo);
    setScopeForPAT(tokenDTO);
    if (tokenDTO.getApiKeyType() == ApiKeyType.SSH_KEY) {
      return saveSSHKey(tokenDTO, scopeInfo);
    }
    if (tokenDTO.getApiKeyType() == ApiKeyType.PGP_KEY) {
      return savePGPKey(tokenDTO, scopeInfo);
    }
    return generateAccessToken(tokenDTO, scopeInfo);
  }

  private void validateSSHTokenDTO(TokenDTO tokenDTO) {
    if (!tokenDTO.getApiKeyType().equals(ApiKeyType.SSH_KEY) || StringUtils.isEmpty(tokenDTO.getSshKeyContent())) {
      throw new InvalidRequestException("Invalid SSH_KEY request");
    }
  }

  private String saveSSHKey(TokenDTO tokenDTO, ScopeInfo scopeInfo) {
    if (tokenDTO.getSshKeyUsage() == null || tokenDTO.getSshKeyUsage().isEmpty()) {
      tokenDTO.setSshKeyUsage(List.of(SSHKeyUsage.AUTH));
    }
    validateSSHTokenDTO(tokenDTO);
    SSHPublicKeyDTOInternal sshPublicKeyDTOInternal = SSHKeyUtils.validateAndExtractKey(tokenDTO.getSshKeyContent());
    sshPublicKeyDTOInternal.setKeyUsage(tokenDTO.getSshKeyUsage());

    Token token = TokenDTOMapper.getTokenFromDTOAndSSHPublicKey(scopeInfo, tokenDTO, sshPublicKeyDTOInternal);
    token.setParentUniqueId(scopeInfo.getUniqueId());
    Page<Token> result = tokenRepository.findAll(
        createFingerprintCriteria(sshPublicKeyDTOInternal.getFingerPrint(), scopeInfo.getAccountIdentifier()),
        Pageable.ofSize(MAX_PAGE_SIZE));
    boolean duplicateToken = result.stream().anyMatch(
        dbToken -> dbToken.getSshPublicKey().getSshKey().equals(sshPublicKeyDTOInternal.getSshKey()));
    if (duplicateToken) {
      throw new DuplicateFieldException("Uploaded SSH key is already in use, please try again with a different key");
    }
    try {
      tokenRepository.save(token);
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(
          String.format("Try using different token name, [%s] already exists", tokenDTO.getIdentifier()));
    }
    return sshPublicKeyDTOInternal.getFingerPrint();
  }

  private void validatePGPTokenDTO(TokenDTO tokenDTO) {
    if (!tokenDTO.getApiKeyType().equals(ApiKeyType.PGP_KEY) || StringUtils.isEmpty(tokenDTO.getContent())) {
      throw new InvalidRequestException("Invalid PGP_KEY request");
    }
  }

  private String savePGPKey(TokenDTO tokenDTO, ScopeInfo scopeInfo) {
    setDefaultPGPKeyUsage(tokenDTO);
    validatePGPKeyUsage(tokenDTO);
    validatePGPTokenDTO(tokenDTO);

    String userEmail = extractCurrentUserEmail();
    PGPPublicKeyDTOInternal pgpPublicKeyDTOInternal =
        PGPKeyUtils.validateAndExtractKey(tokenDTO.getContent(), userEmail);
    pgpPublicKeyDTOInternal.setUsage(tokenDTO.getPgpKeyUsage());

    Token primaryToken = TokenDTOMapper.getTokenFromDTOAndPGPPublicKey(scopeInfo, tokenDTO, pgpPublicKeyDTOInternal);
    primaryToken.setParentUniqueId(scopeInfo.getUniqueId());

    checkForDuplicatePGPKey(scopeInfo, pgpPublicKeyDTOInternal.getFingerprint());
    savePrimaryAndSubKeys(scopeInfo, tokenDTO, primaryToken, pgpPublicKeyDTOInternal);

    return pgpPublicKeyDTOInternal.getFingerprint();
  }

  private void setDefaultPGPKeyUsage(TokenDTO tokenDTO) {
    if (tokenDTO.getPgpKeyUsage() == null || tokenDTO.getPgpKeyUsage().isEmpty()) {
      tokenDTO.setPgpKeyUsage(List.of(PGPKeyUsage.SIGN));
    }
  }

  private void validatePGPKeyUsage(TokenDTO tokenDTO) {
    for (PGPKeyUsage usage : tokenDTO.getPgpKeyUsage()) {
      if (usage != PGPKeyUsage.SIGN) {
        throw new InvalidRequestException("PGP keys can only be used for signing");
      }
    }
  }

  private String extractCurrentUserEmail() {
    if (SourcePrincipalContextBuilder.getSourcePrincipal() == null
        || SourcePrincipalContextBuilder.getSourcePrincipal().getType() != PrincipalType.USER) {
      return null;
    }
    String currentUserId = SourcePrincipalContextBuilder.getSourcePrincipal().getName();
    Optional<UserInfo> optionalUserInfo = ngUserService.getUserById(currentUserId);
    if (optionalUserInfo.isEmpty()) {
      throw new InvalidRequestException(
          String.format("User with id [%s] not found for PGP key creation", currentUserId));
    }
    return optionalUserInfo.get().getEmail();
  }

  private void checkForDuplicatePGPKey(ScopeInfo scopeInfo, String fingerprint) {
    Criteria duplicateCheckCriteria = Criteria.where(TokenKeys.accountIdentifier)
                                          .is(scopeInfo.getAccountIdentifier())
                                          .and(TokenKeys.apiKeyType)
                                          .is(ApiKeyType.PGP_KEY)
                                          .and("pgpPublicKey.fingerprint")
                                          .is(fingerprint);
    if (tokenRepository.exists(duplicateCheckCriteria)) {
      throw new DuplicateFieldException("Uploaded PGP key is already in use, please try again with a different key");
    }
  }

  private void savePrimaryAndSubKeys(
      ScopeInfo scopeInfo, TokenDTO tokenDTO, Token primaryToken, PGPPublicKeyDTOInternal pgpPublicKeyDTOInternal) {
    try {
      tokenRepository.save(primaryToken);
      saveSubKeys(scopeInfo, tokenDTO, pgpPublicKeyDTOInternal);
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(
          String.format("Try using different token name, [%s] already exists", tokenDTO.getIdentifier()));
    }
  }

  private void saveSubKeys(ScopeInfo scopeInfo, TokenDTO tokenDTO, PGPPublicKeyDTOInternal pgpPublicKeyDTOInternal) {
    List<PGPPublicKeyDTOInternal> subKeys = pgpPublicKeyDTOInternal.getSubKeys();
    if (subKeys == null || subKeys.isEmpty()) {
      return;
    }
    for (int i = 0; i < subKeys.size(); i++) {
      PGPPublicKeyDTOInternal subKeyDTO = subKeys.get(i);
      if (subKeyDTO.getUsage() == null || subKeyDTO.getUsage().isEmpty()) {
        subKeyDTO.setUsage(tokenDTO.getPgpKeyUsage());
      }
      Token subKeyToken = TokenDTOMapper.getTokenFromDTOAndPGPPublicKey(
          scopeInfo, tokenDTO, subKeyDTO, tokenDTO.getIdentifier() + "_subkey_" + (i + 1));
      subKeyToken.setParentUniqueId(scopeInfo.getUniqueId());
      saveSubKeyToken(subKeyToken, subKeyDTO.getKeyId());
    }
  }

  private void saveSubKeyToken(Token subKeyToken, String keyId) {
    try {
      tokenRepository.save(subKeyToken);
    } catch (DuplicateKeyException e) {
      log.warn("Subkey {} already exists, skipping", keyId);
    }
  }

  private String generateAccessToken(TokenDTO tokenDTO, ScopeInfo scopeInfo) {
    validateTokenRequest(
        scopeInfo, tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier(), tokenDTO.getApiKeyIdentifier(), tokenDTO);
    if (tokenDTO.getApiKeyType() != ApiKeyType.SCOPED_TOKEN) {
      validateTokenLimit(
          scopeInfo, tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier(), tokenDTO.getApiKeyIdentifier());
    }
    String randomString = RandomStringUtils.random(20, 0, 0, true, true, null, new SecureRandom());
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder($2A, 10);
    String tokenString = passwordEncoder.encode(randomString);
    Optional<ApiKey> apiKeyOptional = apiKeyService.getApiKey(
        scopeInfo, tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier(), tokenDTO.getApiKeyIdentifier());
    if (apiKeyOptional.isEmpty()) {
      throw new InvalidRequestException(
          String.format("API key not present in scope for identifier: [%s]", tokenDTO.getApiKeyIdentifier()));
    }
    validateTokenWithOPAPolicies(tokenDTO, scopeInfo);

    try {
      Token token =
          TokenDTOMapper.getTokenFromDTO(scopeInfo, tokenDTO, apiKeyOptional.get().getDefaultTimeToExpireToken());
      token.setParentUniqueId(scopeInfo.getUniqueId());
      token.setEncodedPassword(tokenString);
      validate(token);
      Token newToken = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        Token savedToken = tokenRepository.save(token);
        outboxService.save(new TokenCreateEvent(TokenDTOMapper.getDTOFromToken(savedToken, scopeInfo)));
        return savedToken;
      }));
      return token.getApiKeyType().getValue() + deliminator + newToken.getAccountIdentifier() + deliminator
          + newToken.getUuid() + deliminator + randomString;
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(
          String.format("Try using a different token name, [%s] already exists", tokenDTO.getIdentifier()));
    }
  }

  private void setScopeForUserToken(TokenDTO tokenDTO, ScopeInfo scopeInfo) {
    if (ApiKeyType.USER.equals(tokenDTO.getApiKeyType())) {
      scopeInfo.setOrgIdentifier(null);
      scopeInfo.setProjectIdentifier(null);
      scopeInfo.setUniqueId(scopeInfo.getAccountIdentifier());
    }
  }

  @VisibleForTesting
  protected void validateTokenRequest(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier, TokenDTO tokenDTO) {
    validateTokenExpiryTime(tokenDTO);
    Optional<ApiKey> apiKeyOptional =
        apiKeyService.getApiKey(scopeInfo, apiKeyType, parentIdentifier, apiKeyIdentifier);
    if (apiKeyOptional.isEmpty()) {
      createApiKey(tokenDTO, scopeInfo);
    }
  }

  private void setScopeForPAT(TokenDTO tokenDTO) {
    if (ApiKeyType.USER.equals(tokenDTO.getApiKeyType())) {
      tokenDTO.setOrgIdentifier(null);
      tokenDTO.setProjectIdentifier(null);
    }
  }

  private void createApiKey(TokenDTO tokenDTO, ScopeInfo scopeInfo) {
    apiKeyService.createApiKey(ApiKeyDTO.builder()
                                   .name(tokenDTO.getApiKeyIdentifier().concat("_auto"))
                                   .description("Auto Generated API key")
                                   .accountIdentifier(tokenDTO.getAccountIdentifier())
                                   .orgIdentifier(scopeInfo.getOrgIdentifier())
                                   .projectIdentifier(scopeInfo.getProjectIdentifier())
                                   .identifier(tokenDTO.getApiKeyIdentifier())
                                   .parentIdentifier(tokenDTO.getParentIdentifier())
                                   .apiKeyType(tokenDTO.getApiKeyType())
                                   .defaultTimeToExpireToken(DEFAULT_TTL_FOR_TOKEN)
                                   .build(),
        scopeInfo);
  }

  private void validateTokenExpiryTime(TokenDTO tokenDTO) {
    long nowMillis = Instant.now().toEpochMilli();
    if (tokenDTO.getValidTo() != null && tokenDTO.getValidTo() < nowMillis) {
      throw new InvalidRequestException(
          String.format("Token's validTo cannot be set before current time. validTo: [%s], current time: [%s]",
              Instant.ofEpochMilli(tokenDTO.getValidTo()), Instant.ofEpochMilli(nowMillis)),
          USER_SRE);
    }

    if (tokenDTO.getValidTo() != null && tokenDTO.getValidFrom() != null
        && tokenDTO.getValidFrom() > tokenDTO.getValidTo()) {
      throw new InvalidRequestException(
          String.format("Token's validFrom time cannot be after validTo time. validFrom: [%s], validTo: [%s]",
              Instant.ofEpochMilli(tokenDTO.getValidFrom()), Instant.ofEpochMilli(tokenDTO.getValidTo())),
          USER_SRE);
    }
  }

  private void validateTokenLimit(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier) {
    ServiceAccountConfig serviceAccountConfig =
        accountService.getAccount(scopeInfo.getAccountIdentifier()).getServiceAccountConfig();
    long tokenLimit = serviceAccountConfig != null ? serviceAccountConfig.getTokenLimit() : DEFAULT_TOKEN_LIMIT;
    long existingTokenCount =
        tokenRepository.countByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifier(
            scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyType, parentIdentifier, apiKeyIdentifier);
    if (existingTokenCount >= tokenLimit) {
      throw new InvalidRequestException("Maximum Token limit has been reached");
    }
  }

  private void invalidateSSHRequest(ApiKeyType apiKeyType) {
    if (apiKeyType.equals(ApiKeyType.SSH_KEY)) {
      throw new InvalidRequestException("Operation not allowed for SSH_KEY type");
    }
  }

  private void invalidatePGPRequest(ApiKeyType apiKeyType) {
    if (apiKeyType.equals(ApiKeyType.PGP_KEY)) {
      throw new InvalidRequestException("Operation not allowed for PGP_KEY type");
    }
  }

  private void validateUpdateTokenRequest(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier, TokenDTO tokenDTO) {
    invalidateSSHRequest(apiKeyType);
    invalidatePGPRequest(apiKeyType);
    if (tokenDTO.getScheduledExpireTime() != null) {
      throw new InvalidRequestException("Rotated tokens cannot be updated", USER_SRE);
    }
    validateTokenRequest(scopeInfo, apiKeyType, parentIdentifier, apiKeyIdentifier, tokenDTO);
  }

  @Override
  public boolean revokeToken(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier, String identifier) {
    Optional<Token> optionalToken =
        tokenRepository
            .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
                scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyType, parentIdentifier,
                apiKeyIdentifier, identifier);
    if (optionalToken.isEmpty()) {
      throw new EntityNotFoundException(
          String.format("Token with identifier [%s] does not exist in account [%s], org [%s], project [%s] "
                  + "for apiKeyType [%s], parentIdentifier [%s], apiKeyIdentifier [%s]",
              identifier, scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
              scopeInfo.getProjectIdentifier(), apiKeyType, parentIdentifier, apiKeyIdentifier));
    }
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      Token tokenToDelete = optionalToken.get();
      tokenRepository.deleteById(tokenToDelete.getUuid());
      tokenValidationHelper.invalidateApiKeyToken(tokenToDelete.getUuid());
      outboxService.save(new TokenDeleteEvent(TokenDTOMapper.getDTOFromToken(tokenToDelete, scopeInfo)));
      return true;
    }));
  }

  @Override
  public TokenDTO getToken(String tokenId, boolean withEncodedPassword) {
    Optional<Token> optionalToken = tokenRepository.findById(tokenId);
    if (optionalToken.isPresent()) {
      ScopeInfo scopeInfo =
          scopeInfoService
              .getScopeInfo(optionalToken.get().getAccountIdentifier(), Set.of(optionalToken.get().getParentUniqueId()))
              .get(optionalToken.get().getParentUniqueId())
              .get();
      TokenDTO tokenDTO = optionalToken.map(token -> TokenDTOMapper.getDTOFromToken(token, scopeInfo)).orElse(null);
      if (withEncodedPassword) {
        tokenDTO.setEncodedPassword(optionalToken.get().getEncodedPassword());
      }
      if (isScopedTokenForUser(tokenDTO) || ApiKeyType.USER == tokenDTO.getApiKeyType()) {
        Optional<UserInfo> optionalUserInfo = ngUserService.getUserById(tokenDTO.getParentIdentifier());
        if (optionalUserInfo.isPresent()) {
          UserInfo userInfo = optionalUserInfo.get();
          tokenDTO.setEmail(userInfo.getEmail());
          tokenDTO.setUsername(userInfo.getName());
          tokenDTO.setParentEntityUniqueId(userInfo.getUuid());
          return tokenDTO;
        }
      } else {
        ServiceAccountDTO serviceAccountDTO =
            serviceAccountService.getServiceAccountDTO(scopeInfo, tokenDTO.getParentIdentifier());
        tokenDTO.setEmail(serviceAccountDTO.getEmail());
        tokenDTO.setUsername(serviceAccountDTO.getName());
        tokenDTO.setParentEntityUniqueId(serviceAccountDTO.getUniqueId());
        return tokenDTO;
      }
    }
    return null;
  }

  private static boolean isScopedTokenForUser(TokenDTO tokenDTO) {
    return ApiKeyType.SCOPED_TOKEN == tokenDTO.getApiKeyType()
        && SCOPED_TOKEN_API_KEY_USER.equals(tokenDTO.getApiKeyIdentifier());
  }

  @Override
  public TokenDTO getSSHTokenWithPublicKey(String tokenIdentifier, String accountIdentifier, String parentIdentifier) {
    Optional<Token> optionalToken =
        tokenRepository.findByAccountIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
            accountIdentifier, ApiKeyType.SSH_KEY, parentIdentifier, tokenIdentifier);
    if (optionalToken.isEmpty()) {
      throw new NotFoundException(String.format("Failed to find ssh token %s in account %s and parent %s",
          tokenIdentifier, accountIdentifier, parentIdentifier));
    }
    Token token = optionalToken.get();
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(token.getAccountIdentifier(), Set.of(token.getParentUniqueId()))
                              .get(token.getParentUniqueId())
                              .get();
    TokenDTO tokenDTO = TokenDTOMapper.getDTOFromToken(token, scopeInfo);
    String sshKeyContent = SSHKeyUtils.convertToKeyContent(token.getSshPublicKey());
    tokenDTO.setSshKeyContent(sshKeyContent);
    tokenDTO.setSshKeyUsage(token.getSshPublicKey().getKeyUsage());
    return tokenDTO;
  }

  @Override
  public TokenDTO getPGPTokenWithPublicKey(String tokenIdentifier, String accountIdentifier, String parentIdentifier) {
    Optional<Token> optionalToken =
        tokenRepository.findByAccountIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
            accountIdentifier, ApiKeyType.PGP_KEY, parentIdentifier, tokenIdentifier);
    if (optionalToken.isEmpty()) {
      throw new NotFoundException(String.format("Failed to find PGP token %s in account %s and parent %s",
          tokenIdentifier, accountIdentifier, parentIdentifier));
    }
    Token token = optionalToken.get();
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(token.getAccountIdentifier(), Set.of(token.getParentUniqueId()))
                              .get(token.getParentUniqueId())
                              .get();
    TokenDTO tokenDTO = TokenDTOMapper.getDTOFromToken(token, scopeInfo);
    String pgpKeyContent = PGPKeyUtils.convertToKeyContent(token.getPgpPublicKey());
    tokenDTO.setContent(pgpKeyContent);
    tokenDTO.setPgpKeyUsage(token.getPgpPublicKey().getUsage());
    return tokenDTO;
  }

  @Override
  public String rotateToken(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier,
      String apiKeyIdentifier, String identifier, Instant scheduledExpireTime) {
    invalidateSSHRequest(apiKeyType);
    invalidatePGPRequest(apiKeyType);
    Optional<Token> optionalToken =
        tokenRepository
            .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
                scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyType, parentIdentifier,
                apiKeyIdentifier, identifier);
    Preconditions.checkState(optionalToken.isPresent(), "No token present with identifier: " + identifier);
    Token tokenThatNeedsToBeRotated = optionalToken.get();
    TokenDTO oldTokenDTO = TokenDTOMapper.getDTOFromToken(tokenThatNeedsToBeRotated, scopeInfo);
    String oldIdentifier = tokenThatNeedsToBeRotated.getIdentifier();

    tokenThatNeedsToBeRotated.setIdentifier("rotated_" + RandomStringUtils.randomAlphabetic(15));
    tokenThatNeedsToBeRotated.setScheduledExpireTime(scheduledExpireTime);
    tokenThatNeedsToBeRotated.setValidUntil(new Date(tokenThatNeedsToBeRotated.getExpiryTimestamp().toEpochMilli()));

    TokenDTO toRotateTokenDTO = TokenDTOMapper.getDTOFromToken(tokenThatNeedsToBeRotated, scopeInfo);
    validateTokenWithOPAPolicies(toRotateTokenDTO, scopeInfo);

    Token newToken = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      Token savedRotatedToken = tokenRepository.save(tokenThatNeedsToBeRotated);
      TokenDTO newTokenDTO = TokenDTOMapper.getDTOFromToken(savedRotatedToken, scopeInfo);
      outboxService.save(new TokenUpdateEvent(oldTokenDTO, newTokenDTO));
      return savedRotatedToken;
    }));

    TokenDTO rotatedTokenDTO = TokenDTOMapper.getDTOFromTokenForRotation(scopeInfo, newToken);
    rotatedTokenDTO.setIdentifier(oldIdentifier);
    return createTokenForRotation(rotatedTokenDTO, scopeInfo);
  }

  /** Rotation-only creation path: skips prepareScopedToken to preserve original SCOPED_TOKEN fields. */
  private String createTokenForRotation(TokenDTO tokenDTO, ScopeInfo scopeInfo) {
    setScopeForUserToken(tokenDTO, scopeInfo);
    setScopeForPAT(tokenDTO);
    return generateAccessToken(tokenDTO, scopeInfo);
  }

  @Override
  public TokenDTO updateToken(TokenDTO tokenDTO, ScopeInfo scopeInfo) {
    validateUpdateTokenRequest(
        scopeInfo, tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier(), tokenDTO.getApiKeyIdentifier(), tokenDTO);
    Optional<Token> optionalToken =
        tokenRepository
            .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
                scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), tokenDTO.getApiKeyType(),
                tokenDTO.getParentIdentifier(), tokenDTO.getApiKeyIdentifier(), tokenDTO.getIdentifier());
    Preconditions.checkState(
        optionalToken.isPresent(), "No token present with identifier: " + tokenDTO.getIdentifier());
    Token token = optionalToken.get();
    TokenDTO oldToken = TokenDTOMapper.getDTOFromToken(token, scopeInfo);
    token.setName(tokenDTO.getName());
    token.setValidFrom(Instant.ofEpochMilli(tokenDTO.getValidFrom()));
    token.setValidTo(Instant.ofEpochMilli(tokenDTO.getValidTo()));
    token.setValidUntil(new Date(token.getExpiryTimestamp().toEpochMilli()));
    token.setDescription(tokenDTO.getDescription());
    token.setTags(TagMapper.convertToList(tokenDTO.getTags()));
    validate(token);
    validateTokenWithOPAPolicies(tokenDTO, scopeInfo);
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      Token savedToken = tokenRepository.save(token);
      TokenDTO newToken = TokenDTOMapper.getDTOFromToken(savedToken, scopeInfo);
      outboxService.save(new TokenUpdateEvent(oldToken, newToken));
      return newToken;
    }));
  }

  @Override
  public Map<String, Integer> getTokensPerApiKeyIdentifier(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, List<String> apiKeyIdentifiers) {
    return tokenRepository.getTokensPerParentIdentifier(scopeInfo, apiKeyType, parentIdentifier, apiKeyIdentifiers);
  }

  @Override
  public PageResponse<TokenAggregateDTO> listAggregateTokens(
      ScopeInfo scopeInfo, Pageable pageable, TokenFilterDTO filterDTO) {
    Criteria criteria = createApiKeyFilterCriteria(createScopeCriteria(scopeInfo), filterDTO);
    Page<Token> tokens = tokenRepository.findAll(criteria, pageable);
    return PageUtils.getNGPageResponse(tokens.map(token -> {
      TokenDTO tokenDTO = TokenDTOMapper.getDTOFromToken(token, scopeInfo);
      return TokenAggregateDTO.builder()
          .token(tokenDTO)
          .expiryAt(token.getExpiryTimestamp() != null ? token.getExpiryTimestamp().toEpochMilli() : null)
          .createdAt(token.getCreatedAt())
          .lastModifiedAt(token.getLastModifiedAt())
          .build();
    }));
  }

  private Criteria createApiKeyFilterCriteria(Criteria criteria, TokenFilterDTO filterDTO) {
    if (filterDTO == null) {
      return criteria;
    }
    if (isNotBlank(filterDTO.getSearchTerm())) {
      criteria.orOperator(Criteria.where(TokenKeys.name).regex(filterDTO.getSearchTerm(), "i"),
          Criteria.where(TokenKeys.identifier).regex(filterDTO.getSearchTerm(), "i"));
    }
    if (isNotEmpty(filterDTO.getParentIdentifier())) {
      criteria.and(TokenKeys.parentIdentifier).is(filterDTO.getParentIdentifier());
    }

    if (isNotEmpty(filterDTO.getApiKeyIdentifier())) {
      criteria.and(TokenKeys.apiKeyIdentifier).is(filterDTO.getApiKeyIdentifier());
    }
    criteria.and(TokenKeys.apiKeyType).is(filterDTO.getApiKeyType());

    if (filterDTO.isIncludeOnlyActiveTokens()) {
      criteria.and(TokenKeys.validFrom).lte(Instant.now());
      criteria.and(TokenKeys.validUntil).gte(new Date(System.currentTimeMillis()));
    }

    if (Objects.nonNull(filterDTO.getIdentifiers()) && !filterDTO.getIdentifiers().isEmpty()) {
      criteria.and(TokenKeys.identifier).in(filterDTO.getIdentifiers());
    }
    return criteria;
  }

  private Criteria createScopeCriteria(ScopeInfo scopeInfo) {
    Criteria criteria = new Criteria();
    criteria.and(TokenKeys.accountIdentifier).is(scopeInfo.getAccountIdentifier());
    criteria.and(TokenKeys.parentUniqueId).is(scopeInfo.getUniqueId());
    return criteria;
  }

  public Criteria createFingerprintCriteria(String fingerprint, String accountID) {
    Criteria criteria = new Criteria();
    return criteria.and(sshPublicKeyFingerPrint).is(fingerprint).and(TokenKeys.accountIdentifier).is(accountID);
  }

  public Criteria createPGPFingerprintCriteria(String fingerprint, String accountID) {
    Criteria criteria = new Criteria();
    return criteria.and(pgpPublicKeyFingerPrint).is(fingerprint).and(TokenKeys.accountIdentifier).is(accountID);
  }

  @Override
  public PageResponse<TokenAggregateDTO> listTokensByApiKeyTypes(ScopeInfo scopeInfo, String accountIdentifier,
      String parentIdentifier, List<ApiKeyType> apiKeyTypes, org.springframework.data.domain.Pageable pageable) {
    Page<Token> tokens = tokenRepository.findByApiKeyTypes(accountIdentifier, parentIdentifier, apiKeyTypes, pageable);

    return PageResponse.<TokenAggregateDTO>builder()
        .content(tokens.getContent()
                     .stream()
                     .map(token
                         -> TokenAggregateDTO.builder().token(TokenDTOMapper.getDTOFromToken(token, scopeInfo)).build())
                     .collect(Collectors.toList()))
        .totalItems(tokens.getTotalElements())
        .pageIndex(pageable.getPageNumber())
        .pageSize(pageable.getPageSize())
        .totalPages(tokens.getTotalPages())
        .build();
  }

  @Override
  public long deleteAllByParentIdentifier(ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier) {
    return tokenRepository.deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifier(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyType, parentIdentifier);
  }

  @Override
  public long deleteAllByApiKeyIdentifier(
      ScopeInfo scopeInfo, ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier) {
    return tokenRepository
        .deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifier(
            scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), apiKeyType, parentIdentifier, apiKeyIdentifier);
  }

  @Override
  public TokenDTO validateToken(String accountIdentifier, String apiKey) {
    if (ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_SUPPORT_JWT_TOKEN_SCIM_API)
        && jwtTokenAuthFilterHelper.isJWTTokenType(apiKey, accountIdentifier)) {
      return jwtTokenAuthFilterHelper.handleSCIMJwtTokenFlow(accountIdentifier, apiKey);
    } else {
      String tokenId = tokenValidationHelper.parseApiKeyToken(apiKey);
      TokenDTO tokenDTO = getToken(tokenId, true);
      tokenValidationHelper.validateToken(tokenDTO, accountIdentifier, tokenId, apiKey);
      tokenDTO.setEncodedPassword(null);
      return tokenDTO;
    }
  }

  @Override
  public TokenDTOInternal validateTokenInternal(String accountIdentifier, String apiKey) {
    TokenDTO tokenDTO = validateToken(accountIdentifier, apiKey);
    return TokenDTOMapper.getTokenDTOInternalFromTokenDTO(tokenDTO);
  }

  @Override
  public void validateTokenListPermissions(ScopeInfo scopeInfo, TokenFilterDTO filterDTO) {
    switch (filterDTO.getApiKeyType()) {
      case USER:
      case SSH_KEY:
      case PGP_KEY:
        try {
          checkIfUserHasUserManagementPermission(scopeInfo, filterDTO);
        } catch (WingsException ex) {
          // if userId is present, check if its loggedIn user
          if (isNotEmpty(filterDTO.getParentIdentifier())) {
            checkIfItsLoggedInUser(scopeInfo.getAccountIdentifier(), filterDTO.getParentIdentifier());
          } else if (ex instanceof NGAccessDeniedException) {
            String message = String.format(
                "Error while listing all users [%s]: [%s] ", filterDTO.getApiKeyType().value, ex.getMessage());
            throw new NGAccessDeniedException(
                message, ex.getReportTargets(), ((NGAccessDeniedException) ex).getFailedPermissionChecks());
          } else {
            throw ex;
          }
        }
        break;
      case SERVICE_ACCOUNT:
        checkIfUserHasServiceAccountManagementPermission(scopeInfo, filterDTO);
        break;
      case SCOPED_TOKEN:
        if (SCOPED_TOKEN_API_KEY_SA.equals(filterDTO.getApiKeyIdentifier())) {
          checkIfUserHasServiceAccountManagementPermission(scopeInfo, filterDTO);
        } else {
          try {
            checkIfUserHasUserManagementPermission(scopeInfo, filterDTO);
          } catch (WingsException ex) {
            if (isNotEmpty(filterDTO.getParentIdentifier())) {
              checkIfItsLoggedInUser(scopeInfo.getAccountIdentifier(), filterDTO.getParentIdentifier());
            } else {
              throw ex;
            }
          }
        }
        break;
      default:
        throw new InvalidArgumentsException(String.format("Invalid API key type: %s", filterDTO.getApiKeyType()));
    }
  }

  private void checkIfUserHasUserManagementPermission(ScopeInfo scopeInfo, TokenFilterDTO filterDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(PlatformResourceTypes.USER, null), MANAGE_USER_PERMISSION);
  }

  private void checkIfUserHasServiceAccountManagementPermission(ScopeInfo scopeInfo, TokenFilterDTO filterDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(PlatformResourceTypes.SERVICEACCOUNT, filterDTO.getParentIdentifier()),
        MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION);
  }

  private void checkIfItsLoggedInUser(String accountIdentifier, String parentIdentifier) {
    Optional<String> userId = Optional.empty();
    if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
        && SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.USER) {
      userId = Optional.of(SourcePrincipalContextBuilder.getSourcePrincipal().getName());
    }
    if (userId.isEmpty()) {
      throw new InvalidArgumentsException("No user identifier present in context");
    }
    if (!userId.get().equals(parentIdentifier)) {
      throw new InvalidArgumentsException(
          String.format("User [%s] not authenticated to list tokens for user [%s]", userId.get(), parentIdentifier));
    }
    Optional<UserInfo> userInfo = ngUserService.getUserById(userId.get());
    if (userInfo.isEmpty()) {
      throw new InvalidArgumentsException(String.format("No user found with id: [%s]", userId.get()));
    }

    List<GatewayAccountRequestDTO> userAccounts = userInfo.get().getAccounts();
    if (userAccounts == null
        || userAccounts.stream().filter(account -> account.getUuid().equals(accountIdentifier)).findFirst().isEmpty()) {
      throw new UnauthorizedException(String.format("User [%s] is not authorized to list tokens for account: [%s]",
                                          userId.get(), accountIdentifier),
          WingsException.USER);
    }
  }

  @Override
  public Long countApiTokens(String accountIdentifier) {
    return tokenRepository.countByAccountIdentifier(accountIdentifier);
  }

  private void validateTokenWithOPAPolicies(TokenDTO tokenDTO, ScopeInfo scopeInfo) {
    GovernanceMetadata governanceMetadata = tokenOpaService.evaluatePoliciesWithEntity(
        scopeInfo, tokenDTO, OpaConstants.OPA_EVALUATION_ACTION_SAVE, tokenDTO.getIdentifier());
    if (governanceMetadata != null && OpaConstants.OPA_STATUS_ERROR.equals(governanceMetadata.getStatus())) {
      GovernanceMetadataErrorDTO errorMetadataDTO =
          GovernanceMetadataErrorDTO.builder().governanceMetadata(governanceMetadata).build();
      throw new OPAPolicyEvaluationException(
          "Error: Failed to save the Token due to Policy enforcement ", errorMetadataDTO);
    }
  }

  @Override
  public ResponseDTO<UserInfo> validateSSHKey(SSHValidateDTO sshValidateDTO) {
    SSHPublicKeyDTOInternal sshPublicKeyDTOInternal;
    // maintaining bg compatibility
    if (isNotEmpty(sshValidateDTO.getSshKey())) {
      sshPublicKeyDTOInternal = SSHKeyUtils.validateAndExtractKey(sshValidateDTO.getSshKey());
    } else {
      sshPublicKeyDTOInternal = SSHKeyUtils.validateAndExtractKey(sshValidateDTO);
    }
    Token token = retrieveToken(sshPublicKeyDTOInternal, sshValidateDTO.getAccountIdentifier());

    // Update key in db updating verified timestamp
    if (sshValidateDTO.getVerified() != null) {
      SSHPublicKey sshPublicKey = token.getSshPublicKey();
      sshPublicKey.setVerified(sshValidateDTO.getVerified());
      token.setSshPublicKey(sshPublicKey);
      try {
        tokenRepository.save(token);
      } catch (Exception e) {
        throw new InvalidRequestException("Unable to update token verification timestamp");
      }
    }

    //    Return the userInfo to the caller
    Optional<UserInfo> userInfoOptional = ngUserService.getUserById(token.getParentIdentifier());
    if (userInfoOptional.isEmpty()) {
      throw new InvalidArgumentsException("No user exists for given SSH key");
    }

    return ResponseDTO.newResponse(userInfoOptional.get());
  }

  private Token retrieveToken(SSHPublicKeyDTOInternal sshPublicKeyDTOInternal, String accountIdentifier) {
    Page<Token> result =
        tokenRepository.findAll(createFingerprintCriteria(sshPublicKeyDTOInternal.getFingerPrint(), accountIdentifier),
            Pageable.ofSize(MAX_PAGE_SIZE));
    if (result == null) {
      throw new NotFoundException(String.format("Failed to find %s with provided fingerprint", ApiKeyType.SSH_KEY));
    }
    List<Token> matchedKeys =
        result.stream()
            .filter(token -> token.getSshPublicKey().getSshKey().equals(sshPublicKeyDTOInternal.getSshKey()))
            .collect(Collectors.toList());
    if (matchedKeys.size() <= 0) {
      throw new NotFoundException("No stored key found matching the provided value");
    }
    return matchedKeys.get(0);
  }

  @Override
  public List<PublicKeyDTO> listByFingerprint(String accountIdentifier, String fingerprint, String principalIdentifier,
      List<PublicKeyUsage> usages, List<PublicKeyScheme> schemes) {
    // Delegate to unified findByKeyFilters to reduce query patterns
    List<Token> tokens = tokenRepository.findByKeyFilters(
        accountIdentifier, fingerprint, null, null, principalIdentifier, usages, schemes);
    return PublicKeyMapper.fromTokens(tokens);
  }

  @Override
  public List<PublicKeyDTO> listBySubKeyId(String accountIdentifier, String subKeyId, String principalIdentifier,
      List<PublicKeyUsage> usages, List<PublicKeyScheme> schemes) {
    // Delegate to unified findByKeyFilters to reduce query patterns
    List<Token> tokens =
        tokenRepository.findByKeyFilters(accountIdentifier, null, subKeyId, null, principalIdentifier, usages, schemes);
    return PublicKeyMapper.fromTokens(tokens);
  }

  @Override
  public List<PublicKeyDTO> listByPrincipal(String accountIdentifier, String principalIdentifier,
      List<PublicKeyUsage> usages, List<PublicKeyScheme> schemes) {
    // Delegate to unified findByKeyFilters to reduce query patterns
    List<Token> tokens =
        tokenRepository.findByKeyFilters(accountIdentifier, null, null, null, principalIdentifier, usages, schemes);
    return PublicKeyMapper.fromTokens(tokens);
  }

  @Override
  public List<TokenDTO> listTokensByKeyFilters(String accountIdentifier, String fingerprint, String subKeyId,
      String principalIdentifier, List<PublicKeyUsage> usages, List<PublicKeyScheme> schemes) {
    List<Token> tokens = tokenRepository.findByKeyFilters(
        accountIdentifier, fingerprint, subKeyId, null, principalIdentifier, usages, schemes);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(accountIdentifier, null, null);
    return tokens.stream().map(token -> TokenDTOMapper.getDTOFromToken(token, scopeInfo)).collect(Collectors.toList());
  }

  @Override
  public boolean deleteKey(ScopeInfo scopeInfo, String parentIdentifier, String apiKeyIdentifier, String identifier) {
    Optional<Token> optionalToken =
        tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
            scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), parentIdentifier, apiKeyIdentifier, identifier);

    if (optionalToken.isEmpty()) {
      log.info("Key with identifier {} not found, treating as already deleted", identifier);
      return true;
    }

    Token tokenToDelete = optionalToken.get();
    ApiKeyType keyType = tokenToDelete.getApiKeyType();

    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      int deletedCount = 0;

      // If this is a PGP primary key, delete its subkeys first
      if (ApiKeyType.PGP_KEY.equals(keyType)) {
        PGPPublicKey pgpPublicKey = tokenToDelete.getPgpPublicKey();
        if (pgpPublicKey != null) {
          boolean isPrimaryKey = isEmpty(pgpPublicKey.getParentKeyId())
              || (pgpPublicKey.getIsSubKey() != null && !pgpPublicKey.getIsSubKey());

          if (isPrimaryKey && pgpPublicKey.getKeyId() != null) {
            // Delete all subkeys first
            Criteria subKeysCriteria = Criteria.where(TokenKeys.accountIdentifier)
                                           .is(scopeInfo.getAccountIdentifier())
                                           .and(TokenKeys.apiKeyType)
                                           .is(ApiKeyType.PGP_KEY)
                                           .and(PGP_PARENT_KEY_ID_FIELD)
                                           .is(pgpPublicKey.getKeyId());
            long subKeysDeleted = tokenRepository.deleteAll(subKeysCriteria);
            deletedCount += (int) subKeysDeleted;
            log.info("Deleted {} subkeys for primary PGP key {}", subKeysDeleted, identifier);
          }
        }
      }

      // Delete the key itself
      tokenRepository.deleteById(tokenToDelete.getUuid());
      deletedCount++;

      // Emit delete event
      outboxService.save(new TokenDeleteEvent(TokenDTOMapper.getDTOFromToken(tokenToDelete, scopeInfo)));

      log.info("Deleted {} key {} (totalDeleted: {})", keyType, identifier, deletedCount);
      return deletedCount > 0;
    }));
  }

  @Override
  public TokenDTO updateKey(ScopeInfo scopeInfo, String parentIdentifier, String apiKeyIdentifier, String identifier,
      UpdatePublicKeyRequest request) {
    validateUpdatePublicKeyRequest(request);

    Optional<Token> optionalToken =
        tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
            scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), parentIdentifier, apiKeyIdentifier, identifier);

    if (optionalToken.isEmpty()) {
      throw new InvalidRequestException("Key not found with identifier: " + identifier);
    }

    Token token = optionalToken.get();

    if (!isSSHOrPgpKey(token.getApiKeyType())) {
      throw new InvalidRequestException("Update operation only supported for SSH_KEY and PGP_KEY types");
    }

    TokenDTO oldToken = TokenDTOMapper.getDTOFromToken(token, scopeInfo);

    if (request.getRevocationReason() != null) {
      return handleRevocationUpdate(scopeInfo, token, oldToken, request.getRevocationReason());
    }
    return handleValidityUpdate(scopeInfo, token, oldToken, request);
  }

  private boolean isSSHOrPgpKey(ApiKeyType keyType) {
    return ApiKeyType.SSH_KEY.equals(keyType) || ApiKeyType.PGP_KEY.equals(keyType);
  }

  private void validateUpdatePublicKeyRequest(UpdatePublicKeyRequest request) {
    if (request.getRevocationReason() != null && (request.getValidFrom() != null || request.getValidTo() != null)) {
      throw new InvalidArgumentsException("Must either revoke the key or update its validity period, not both");
    }

    if (request.getValidFrom() != null && request.getValidTo() != null
        && request.getValidFrom() > request.getValidTo()) {
      throw new InvalidArgumentsException("Invalid validity period: validFrom must be <= validTo");
    }
  }

  private RevocationReason getKeyRevocationReason(Token token) {
    if (ApiKeyType.PGP_KEY.equals(token.getApiKeyType()) && token.getPgpPublicKey() != null) {
      return token.getPgpPublicKey().getRevocationReason();
    }
    if (ApiKeyType.SSH_KEY.equals(token.getApiKeyType()) && token.getSshPublicKey() != null) {
      return token.getSshPublicKey().getRevocationReason();
    }
    return token.getRevocationReason();
  }

  private TokenDTO handleRevocationUpdate(
      ScopeInfo scopeInfo, Token token, TokenDTO oldToken, RevocationReason newReason) {
    RevocationReason currentReason = getKeyRevocationReason(token);

    // Cannot change revocation reason of COMPROMISED keys
    if (RevocationReason.COMPROMISED.equals(currentReason)) {
      throw new InvalidRequestException("Cannot update revocation reason of a COMPROMISED key");
    }

    // Use factory to get all revokers and invoke them
    List<PublicKeyRevoker> revokers = publicKeyRevokerFactory.getRevokers(newReason);
    revokers.forEach(r -> r.revoke(scopeInfo, token));

    // Update MongoDB after services succeed (or for non-COMPROMISED revocations)
    Instant now = Instant.now();
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      updateTokenRevocation(token, newReason, now);
      Token savedToken = tokenRepository.save(token);

      // For PGP primary keys, update all subkeys
      if (ApiKeyType.PGP_KEY.equals(token.getApiKeyType()) && isPrimaryPGPKey(token)) {
        updatePGPSubkeysRevocation(scopeInfo, token, newReason, now);
      }

      TokenDTO newToken = TokenDTOMapper.getDTOFromToken(savedToken, scopeInfo);
      outboxService.save(new TokenUpdateEvent(oldToken, newToken));
      return newToken;
    }));
  }

  private boolean isPrimaryPGPKey(Token token) {
    if (token.getPgpPublicKey() == null) {
      return false;
    }
    String parentKeyId = token.getPgpPublicKey().getParentKeyId();
    Boolean isSubKey = token.getPgpPublicKey().getIsSubKey();
    return (parentKeyId == null || parentKeyId.isEmpty()) && (isSubKey == null || !isSubKey);
  }

  private void updateTokenRevocation(Token token, RevocationReason reason, Instant now) {
    token.setRevocationReason(reason);

    // Set validTo to current time if not already expired
    if (token.getValidTo() == null || token.getValidTo().isAfter(now)) {
      token.setValidTo(now);
      token.setValidUntil(new Date(now.toEpochMilli()));
    }

    // Update embedded key objects
    if (ApiKeyType.PGP_KEY.equals(token.getApiKeyType()) && token.getPgpPublicKey() != null) {
      token.getPgpPublicKey().setRevocationReason(reason);
      if (token.getPgpPublicKey().getValidTo() == null || token.getPgpPublicKey().getValidTo() > now.toEpochMilli()) {
        token.getPgpPublicKey().setValidTo(now.toEpochMilli());
      }
    } else if (ApiKeyType.SSH_KEY.equals(token.getApiKeyType()) && token.getSshPublicKey() != null) {
      token.getSshPublicKey().setRevocationReason(reason);
    }
  }

  private void updatePGPSubkeysRevocation(
      ScopeInfo scopeInfo, Token primaryToken, RevocationReason reason, Instant now) {
    String primaryKeyId = primaryToken.getPgpPublicKey().getKeyId();
    if (primaryKeyId == null) {
      return;
    }

    Criteria subKeysCriteria = Criteria.where(TokenKeys.accountIdentifier)
                                   .is(scopeInfo.getAccountIdentifier())
                                   .and(TokenKeys.apiKeyType)
                                   .is(ApiKeyType.PGP_KEY)
                                   .and(PGP_PARENT_KEY_ID_FIELD)
                                   .is(primaryKeyId);

    Page<Token> subKeysPage = tokenRepository.findAll(subKeysCriteria, Pageable.ofSize(MAX_PAGE_SIZE));
    List<Token> subKeys = subKeysPage.getContent();
    for (Token subKey : subKeys) {
      updateTokenRevocation(subKey, reason, now);
      tokenRepository.save(subKey);
    }
    log.info("Updated {} subkeys for primary PGP key {}", subKeys.size(), primaryToken.getIdentifier());
  }

  private TokenDTO handleValidityUpdate(
      ScopeInfo scopeInfo, Token token, TokenDTO oldToken, UpdatePublicKeyRequest request) {
    RevocationReason currentReason = getKeyRevocationReason(token);

    // Cannot update validity period of revoked keys
    if (currentReason != null) {
      throw new InvalidRequestException("Cannot update validity period of a revoked key");
    }

    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      boolean validFromChanged = updateValidFrom(token, request.getValidFrom());
      boolean validToChanged = updateValidTo(token, request.getValidTo());

      if (!validFromChanged && !validToChanged) {
        return oldToken;
      }

      Token savedToken = tokenRepository.save(token);
      TokenDTO newToken = TokenDTOMapper.getDTOFromToken(savedToken, scopeInfo);
      outboxService.save(new TokenUpdateEvent(oldToken, newToken));
      return newToken;
    }));
  }

  private boolean updateValidFrom(Token token, Long newValidFrom) {
    if (newValidFrom == null) {
      return false;
    }
    Long currentValidFrom = token.getValidFrom() != null ? token.getValidFrom().toEpochMilli() : null;
    if (newValidFrom.equals(currentValidFrom)) {
      return false;
    }
    token.setValidFrom(Instant.ofEpochMilli(newValidFrom));
    if (ApiKeyType.PGP_KEY.equals(token.getApiKeyType()) && token.getPgpPublicKey() != null) {
      token.getPgpPublicKey().setValidFrom(newValidFrom);
    }
    return true;
  }

  private boolean updateValidTo(Token token, Long newValidTo) {
    if (newValidTo == null) {
      return false;
    }
    Long currentValidTo = token.getValidTo() != null ? token.getValidTo().toEpochMilli() : null;
    if (newValidTo.equals(currentValidTo)) {
      return false;
    }
    token.setValidTo(Instant.ofEpochMilli(newValidTo));
    token.setValidUntil(new Date(newValidTo));
    if (ApiKeyType.PGP_KEY.equals(token.getApiKeyType()) && token.getPgpPublicKey() != null) {
      token.getPgpPublicKey().setValidTo(newValidTo);
    }
    return true;
  }

  private void prepareScopedToken(TokenDTO tokenDTO, ScopeInfo scopeInfo) {
    tokenDTO.setApiKeyIdentifier(getScopedTokenApiKeyIdentifier(tokenDTO.getParentIdentifier(), scopeInfo));
    validateScopedTokenRequest(tokenDTO);
    validateScopedResourcePermissionScopes(scopeInfo.getAccountIdentifier(), tokenDTO);
    validateScopedTokenPermissions(scopeInfo.getAccountIdentifier(), tokenDTO);
    validateScopedTokenLimit(scopeInfo.getAccountIdentifier(), tokenDTO);
    applyEphemeralTokenTTL(tokenDTO);

    ScopedResourceMetadata metadata = tokenDTO.getScopedResourceMetadata();
    if (metadata == null) {
      metadata = ScopedResourceMetadata.builder().build();
      tokenDTO.setScopedResourceMetadata(metadata);
    }
    if (tokenDTO.getTokenMode() == TokenMode.EPHEMERAL) {
      metadata.setCreatedBy("SYSTEM");
    } else {
      metadata.setCreatedBy(getCurrentCallerId());
    }
  }

  private void applyEphemeralTokenTTL(TokenDTO tokenDTO) {
    if (tokenDTO.getTokenMode() != TokenMode.EPHEMERAL) {
      return;
    }
    long baseTime = tokenDTO.getValidFrom() != null ? tokenDTO.getValidFrom() : Instant.now().toEpochMilli();
    long maxValidTo = baseTime + SCOPED_TOKEN_EPHEMERAL_MAX_TTL_MS;

    if (tokenDTO.getValidTo() == null) {
      tokenDTO.setValidTo(baseTime + SCOPED_TOKEN_EPHEMERAL_DEFAULT_TTL_MS);
    } else if (tokenDTO.getValidTo() > maxValidTo) {
      throw new InvalidRequestException("Ephemeral scoped tokens cannot have a validity exceeding 24 hours");
    }
  }

  @Override
  public long deleteAllScopedTokensByParentResourceId(String accountIdentifier, String parentResourceId) {
    if (isEmpty(accountIdentifier) || isEmpty(parentResourceId)) {
      throw new InvalidRequestException("accountIdentifier and parentResourceId are required for bulk token deletion");
    }
    long deleted = tokenRepository.deleteAllByAccountIdentifierAndScopedResourceMetadata_ParentResourceIdAndApiKeyType(
        accountIdentifier, parentResourceId, ApiKeyType.SCOPED_TOKEN);
    log.info("Deleted {} ephemeral scoped tokens for parentResourceId {}", deleted, parentResourceId);
    return deleted;
  }

  @VisibleForTesting
  void validateScopedResourcePermissionScopes(String accountIdentifier, TokenDTO tokenDTO) {
    Set<String> orgOnlyScopes = new HashSet<>();
    Map<String, Set<String>> projectsByOrg = new HashMap<>();

    for (ScopedResourcePermission entry : tokenDTO.getScopedResourcePermissions()) {
      String orgId = entry.getOrgIdentifier();
      String projectId = entry.getProjectIdentifier();
      if (isEmpty(orgId) && isEmpty(projectId)) {
        continue;
      }
      if (isEmpty(orgId) && isNotEmpty(projectId)) {
        throw new InvalidRequestException(
            "Invalid scope in scoped permission entry: projectIdentifier requires a non-empty orgIdentifier");
      }
      if (isEmpty(projectId)) {
        orgOnlyScopes.add(orgId);
      } else {
        projectsByOrg.computeIfAbsent(orgId, k -> new HashSet<>()).add(projectId);
        orgOnlyScopes.add(orgId);
      }
    }

    List<String> invalidScopes = new ArrayList<>();

    if (isNotEmpty(orgOnlyScopes)) {
      try {
        List<ScopeInfo> validOrgs =
            NGRestUtils.getResponse(scopeInfoClient.getScopeInfoList(accountIdentifier, orgOnlyScopes));
        Set<String> foundOrgs = validOrgs.stream().map(ScopeInfo::getOrgIdentifier).collect(Collectors.toSet());
        for (String orgId : orgOnlyScopes) {
          if (!foundOrgs.contains(orgId)) {
            invalidScopes.add("org=[" + orgId + "] does not exist");
          }
        }
      } catch (Exception e) {
        log.warn("Scope validation skipped — failed to validate org scopes: {}", e.getMessage());
      }
    }

    for (Map.Entry<String, Set<String>> entry : projectsByOrg.entrySet()) {
      String orgId = entry.getKey();
      Set<String> projectIds = entry.getValue();
      try {
        List<ScopeInfo> validProjects =
            NGRestUtils.getResponse(scopeInfoClient.getScopeInfoList(accountIdentifier, orgId, projectIds));
        Set<String> foundProjects =
            validProjects.stream().map(ScopeInfo::getProjectIdentifier).collect(Collectors.toSet());
        for (String projectId : projectIds) {
          if (!foundProjects.contains(projectId)) {
            invalidScopes.add("org=[" + orgId + "] project=[" + projectId + "] does not exist");
          }
        }
      } catch (Exception e) {
        log.warn(
            "Scope validation skipped — failed to validate project scopes for org=[{}]: {}", orgId, e.getMessage());
      }
    }

    if (isNotEmpty(invalidScopes)) {
      throw new InvalidRequestException(
          "Invalid scopes in scoped permission entries: " + String.join("; ", invalidScopes));
    }
  }

  private void validateScopedTokenRequest(TokenDTO tokenDTO) {
    if (isEmpty(tokenDTO.getScopedResourcePermissions())) {
      throw new InvalidRequestException("scopedResourcePermissions must not be empty for scoped tokens");
    }
    if (tokenDTO.getTokenMode() == null) {
      throw new InvalidRequestException("tokenMode is required for scoped tokens");
    }
    if (tokenDTO.getTokenMode() == TokenMode.EPHEMERAL
        && (tokenDTO.getScopedResourceMetadata() == null
            || isEmpty(tokenDTO.getScopedResourceMetadata().getParentResourceId()))) {
      throw new InvalidRequestException(
          "scopedResourceMetadata.parentResourceId is required for ephemeral scoped tokens");
    }
    for (ScopedResourcePermission entry : tokenDTO.getScopedResourcePermissions()) {
      if (isEmpty(entry.getResourceType())) {
        throw new InvalidRequestException("resourceType is required in each scope entry");
      }
      // Deprecated field is checked first: a request carrying `permission` alongside an empty
      // `permissions` should be told to stop sending the deprecated field, which is the
      // actionable fix, rather than to populate a list it is trying to replace.
      if (isNotEmpty(entry.getPermission())) {
        throw new InvalidRequestException("The deprecated `permission` field is no longer accepted in a scope entry; "
            + "use a non-empty `permissions` list instead");
      }
      if (isEmpty(entry.getEffectivePermissions())) {
        throw new InvalidRequestException("A non-empty `permissions` list is required in each scope entry");
      }
    }
  }
  private void validateScopedTokenPermissions(String accountIdentifier, TokenDTO tokenDTO) {
    List<PermissionCheckDTO> permissionChecks = new ArrayList<>();
    for (ScopedResourcePermission entry : tokenDTO.getScopedResourcePermissions()) {
      ResourceScope scope = ResourceScope.of(accountIdentifier, entry.getOrgIdentifier(), entry.getProjectIdentifier());
      // Fan out one PermissionCheckDTO per effective permission. The parent principal must
      // already hold every one of them, otherwise we refuse to mint a scoped token granting it.
      // validateScopedTokenRequest has already rejected the deprecated singular field, so on this
      // path the effective permissions are just the blank-filtered `permissions` list.
      for (String perm : entry.getEffectivePermissions()) {
        if (isNotEmpty(entry.getResourceIdentifiers())) {
          for (String resourceId : entry.getResourceIdentifiers()) {
            permissionChecks.add(PermissionCheckDTO.builder()
                                     .permission(perm)
                                     .resourceType(entry.getResourceType())
                                     .resourceScope(scope)
                                     .resourceIdentifier(resourceId)
                                     .build());
          }
        } else {
          permissionChecks.add(PermissionCheckDTO.builder()
                                   .permission(perm)
                                   .resourceType(entry.getResourceType())
                                   .resourceScope(scope)
                                   .build());
        }
      }
    }

    AccessCheckResponseDTO response = accessControlClient.checkForAccess(permissionChecks);

    List<String> deniedPermissions =
        response.getAccessControlList()
            .stream()
            .filter(check -> !check.isPermitted())
            .map(check
                -> check.getPermission() + " on " + check.getResourceType()
                    + (check.getResourceIdentifier() != null ? ":" + check.getResourceIdentifier() : ""))
            .collect(Collectors.toList());

    if (isNotEmpty(deniedPermissions)) {
      throw new InvalidRequestException(
          "Cannot create scoped token with permissions the parent principal does not have: " + deniedPermissions);
    }
  }

  private void validateScopedTokenLimit(String accountIdentifier, TokenDTO tokenDTO) {
    if (tokenDTO.getTokenMode() == TokenMode.EPHEMERAL) {
      long existingCount = tokenRepository.countByAccountIdentifierAndParentIdentifierAndApiKeyTypeAndTokenMode(
          accountIdentifier, tokenDTO.getParentIdentifier(), ApiKeyType.SCOPED_TOKEN, TokenMode.EPHEMERAL);
      if (existingCount >= SCOPED_TOKEN_EPHEMERAL_LIMIT) {
        throw new InvalidRequestException(
            String.format("Maximum ephemeral scoped token limit (%d) reached for parent Identifier %s",
                SCOPED_TOKEN_EPHEMERAL_LIMIT, tokenDTO.getParentIdentifier()));
      }
    } else {
      long existingCount = tokenRepository.countByAccountIdentifierAndParentIdentifierAndApiKeyTypeAndTokenMode(
          accountIdentifier, tokenDTO.getParentIdentifier(), ApiKeyType.SCOPED_TOKEN, TokenMode.PERSISTENT);
      if (existingCount >= SCOPED_TOKEN_PERSISTENT_LIMIT) {
        throw new InvalidRequestException(
            String.format("Maximum persistent scoped token limit (%d) reached for parent Identifier %s",
                SCOPED_TOKEN_PERSISTENT_LIMIT, tokenDTO.getParentIdentifier()));
      }
    }
  }

  private String getScopedTokenApiKeyIdentifier(String parentIdentifier, ScopeInfo scopeInfo) {
    Principal principal = SourcePrincipalContextBuilder.getSourcePrincipal();
    if (principal != null && principal.getType() == PrincipalType.USER) {
      return SCOPED_TOKEN_API_KEY_USER;
    }
    if (principal != null && principal.getType() == PrincipalType.SERVICE_ACCOUNT) {
      return SCOPED_TOKEN_API_KEY_SA;
    }
    throw new InvalidRequestException("Unable to determine parent type for scoped token. Principal type: "
        + (principal != null ? principal.getType() : "null"));
  }

  private String getCurrentCallerId() {
    if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
        && SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.USER) {
      return SourcePrincipalContextBuilder.getSourcePrincipal().getName();
    }
    return "unknown";
  }
}

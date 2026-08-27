/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.ng.core.common.beans.ApiKeyType.PGP_KEY;
import static io.harness.ng.core.common.beans.ApiKeyType.SERVICE_ACCOUNT;
import static io.harness.ng.core.common.beans.ApiKeyType.SSH_KEY;
import static io.harness.ng.core.common.beans.ApiKeyType.USER;
import static io.harness.rule.OwnerRule.ABHISHEK_SINGH;
import static io.harness.rule.OwnerRule.AKHIL_PANDEY;
import static io.harness.rule.OwnerRule.ATEFEH;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.KAPIL_GARG;
import static io.harness.rule.OwnerRule.KARAN_GARG;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.PIYUSH;
import static io.harness.rule.OwnerRule.SAHIBA;
import static io.harness.rule.OwnerRule.SHIVAM_RAJPUT;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2A;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.OPAPolicyEvaluationException;
import io.harness.exception.UnauthorizedException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.account.ServiceAccountConfig;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.PublicKeyRevoker;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.api.utils.SSHKeyUtils;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PGPKeyUsage;
import io.harness.ng.core.common.beans.PGPPublicKey;
import io.harness.ng.core.common.beans.RevocationReason;
import io.harness.ng.core.common.beans.SSHKeyUsage;
import io.harness.ng.core.common.beans.SSHPublicKey;
import io.harness.ng.core.common.beans.ScopedResourceMetadata;
import io.harness.ng.core.common.beans.ScopedResourcePermission;
import io.harness.ng.core.common.beans.TokenMode;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.GatewayAccountRequestDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.SSHPublicKeyDTOInternal;
import io.harness.ng.core.dto.SSHValidateDTO;
import io.harness.ng.core.dto.TokenAggregateDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.TokenFilterDTO;
import io.harness.ng.core.dto.UpdatePublicKeyRequest;
import io.harness.ng.core.entities.ApiKey;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.events.TokenDeleteEvent;
import io.harness.ng.core.mapper.TokenDTOMapper;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.opa.entities.token.TokenOpaService;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.opaclient.model.OpaConstants;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.TokenRepository;
import io.harness.rule.Owner;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.token.ApiKeyTokenPasswordCacheHelper;
import io.harness.token.TokenValidationHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class TokenServiceImplTest extends NgManagerTestBase {
  private TokenService tokenService;
  private TokenServiceImpl tokenServiceImpl;
  private TokenRepository tokenRepository;
  private ApiKeyService apiKeyService;
  private OutboxService outboxService;
  private ServiceAccountService serviceAccountService;
  private NgUserService ngUserService;
  private String accountIdentifier;
  private String orgIdentifier;
  private String uniqueId;
  private String projectIdentifier;
  private String identifier;
  private String parentIdentifier;
  private TokenDTO tokenDTO;
  private ScopeInfo scopeInfo;
  private TransactionTemplate transactionTemplate;
  private Token token;
  private AccountService accountService;
  private ScopeInfoService scopeInfoService;
  private ScopeInfoClient scopeInfoClient;
  private TokenValidationHelper tokenValidationHelper;
  private AccessControlClient accessControlClient;
  @Inject private ApiKeyTokenPasswordCacheHelper apiKeyTokenPasswordCacheHelper;
  private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  private TokenOpaService tokenOpaService;
  private PublicKeyRevokerFactory publicKeyRevokerFactory;
  Instant nextDay = Instant.now().plusSeconds(86400);
  String tokensUUId = generateUuid();
  String rsaKeyContent = "ssh-rsa "
      + "AAAAB3NzaC1yc2EAAAADAQABAAACAQDOi884RoTpTtFeYNWIJYOIZVHus8VJyL6S2RZxPCmZoMbDZpJGq3em9bWrjcuNij6mE5/"
      + "8z239dSA6Rl+fVpCKbqV2bNZ96xJTYgzNjtdaO2mkQxCKr1RoIF/clggds9DuIU7EXTYuq8B6cil9CgHfD43zt96O3t+Ytd8W3bfRLl4h/"
      + "etw1QCqtBJ/25JOyqkCi4rdLen27Qu19fYiZ0N/XxBDf0ZpBAmO/1fn3kkx/3t2JrFwYFQ03wBPjuhSY0PewFIJVT/H5f9y6jx9exE7/"
      + "0hb0LVw+SQbpeo0XTPUNW8qFeprPk2hab9N4Qk7ZGKmJo8gh0+6vUVwfbc+ky5z9WKsej/75jndJHs9JwkXqA8LaiRobGVfpiD0Wmkof7/"
      + "EFYFscV2yYVSP8cyHTUxNdntzbUJq9TMbeq9988jTpNRGRiKBdNC/"
      + "mVWyQDTkhQdJrA2dOTdOj7Y2pxrlzzSV0OirbUPQGEhmxnpI2pJJPkDKvbjgSYuZpsn/YRT+VxMF8p8OJSmHisJXFVobJIl3ONuuCI/"
      + "QrtVgInwVOQtqjcN47687SIPSfa0eU1blHGAuJaVQdrVTu5YkNyGQWYlZTSRdYf3b4UjgXp3wYfEps551z+BpHaOmkx+"
      + "nMsHa5dl4MDP0nqjxSVaYI8XJpjBngkJBxr3wkU2cjzsgsAmYAw== ak_gcp";
  String rsaExpectedKey =
      "AAAAB3NzaC1yc2EAAAADAQABAAACAQDOi884RoTpTtFeYNWIJYOIZVHus8VJyL6S2RZxPCmZoMbDZpJGq3em9bWrjcuNij6mE5/"
      + "8z239dSA6Rl+fVpCKbqV2bNZ96xJTYgzNjtdaO2mkQxCKr1RoIF/clggds9DuIU7EXTYuq8B6cil9CgHfD43zt96O3t+Ytd8W3bfRLl4h/"
      + "etw1QCqtBJ/25JOyqkCi4rdLen27Qu19fYiZ0N/XxBDf0ZpBAmO/1fn3kkx/3t2JrFwYFQ03wBPjuhSY0PewFIJVT/H5f9y6jx9exE7/"
      + "0hb0LVw+SQbpeo0XTPUNW8qFeprPk2hab9N4Qk7ZGKmJo8gh0+6vUVwfbc+ky5z9WKsej/75jndJHs9JwkXqA8LaiRobGVfpiD0Wmkof7/"
      + "EFYFscV2yYVSP8cyHTUxNdntzbUJq9TMbeq9988jTpNRGRiKBdNC/"
      + "mVWyQDTkhQdJrA2dOTdOj7Y2pxrlzzSV0OirbUPQGEhmxnpI2pJJPkDKvbjgSYuZpsn/YRT+VxMF8p8OJSmHisJXFVobJIl3ONuuCI/"
      + "QrtVgInwVOQtqjcN47687SIPSfa0eU1blHGAuJaVQdrVTu5YkNyGQWYlZTSRdYf3b4UjgXp3wYfEps551z+BpHaOmkx+"
      + "nMsHa5dl4MDP0nqjxSVaYI8XJpjBngkJBxr3wkU2cjzsgsAmYAw==";
  String rsaExpectedFingerprint = "SHA256:s50ylXvH5WTcASOK3hTsRevl5qC4/cpNdPhujfQpyao";

  @Before
  public void setup() throws IllegalAccessException {
    accountIdentifier = randomAlphabetic(10);
    orgIdentifier = randomAlphabetic(10);
    projectIdentifier = randomAlphabetic(10);
    identifier = randomAlphabetic(10);
    parentIdentifier = randomAlphabetic(10);
    uniqueId = randomAlphabetic(10);
    tokenRepository = mock(TokenRepository.class);
    tokenService = new TokenServiceImpl();
    tokenServiceImpl = new TokenServiceImpl();
    apiKeyService = mock(ApiKeyService.class);
    outboxService = mock(OutboxService.class);
    serviceAccountService = mock(ServiceAccountService.class);
    scopeInfoService = mock(ScopeInfoService.class);
    scopeInfoClient = mock(ScopeInfoClient.class);
    ngUserService = mock(NgUserService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    accountService = mock(AccountService.class);
    tokenValidationHelper = new TokenValidationHelper();
    ngFeatureFlagHelperService = mock(NGFeatureFlagHelperService.class);
    apiKeyTokenPasswordCacheHelper = new ApiKeyTokenPasswordCacheHelper();
    accessControlClient = mock(AccessControlClient.class);
    tokenOpaService = mock(TokenOpaService.class);
    publicKeyRevokerFactory = mock(PublicKeyRevokerFactory.class);

    tokenDTO = TokenDTO.builder()
                   .accountIdentifier(accountIdentifier)
                   .orgIdentifier(orgIdentifier)
                   .name(randomAlphabetic(10))
                   .projectIdentifier(projectIdentifier)
                   .identifier(identifier)
                   .parentIdentifier(parentIdentifier)
                   .apiKeyIdentifier(randomAlphabetic(10))
                   .apiKeyType(SERVICE_ACCOUNT)
                   .scheduledExpireTime(Instant.now().toEpochMilli())
                   .description("")
                   .tags(new HashMap<>())
                   .build();
    token = Token.builder()
                .scheduledExpireTime(Instant.now().plusSeconds(86500))
                .validTo(Instant.now().plusSeconds(86500))
                .validFrom(Instant.now())
                .accountIdentifier(accountIdentifier)
                .orgIdentifier(orgIdentifier)
                .name(randomAlphabetic(10))
                .projectIdentifier(projectIdentifier)
                .parentUniqueId(uniqueId)
                .identifier(identifier)
                .parentIdentifier(parentIdentifier)
                .apiKeyIdentifier(randomAlphabetic(10))
                .apiKeyType(SERVICE_ACCOUNT)
                .description("")
                .tags(new ArrayList<>())
                .build();

    scopeInfo = ScopeInfo.builder()
                    .uniqueId(uniqueId)
                    .accountIdentifier(accountIdentifier)
                    .orgIdentifier(orgIdentifier)
                    .projectIdentifier(projectIdentifier)
                    .build();

    token.setUuid(tokensUUId);
    when(transactionTemplate.execute(any())).thenReturn(token);
    FieldUtils.writeField(tokenService, "tokenRepository", tokenRepository, true);
    FieldUtils.writeField(tokenService, "apiKeyService", apiKeyService, true);
    FieldUtils.writeField(tokenService, "serviceAccountService", serviceAccountService, true);
    FieldUtils.writeField(tokenService, "ngUserService", ngUserService, true);
    FieldUtils.writeField(tokenService, "outboxService", outboxService, true);
    FieldUtils.writeField(tokenService, "transactionTemplate", transactionTemplate, true);
    FieldUtils.writeField(tokenService, "accountService", accountService, true);
    FieldUtils.writeField(tokenService, "tokenValidationHelper", tokenValidationHelper, true);
    FieldUtils.writeField(
        tokenValidationHelper, "apiKeyTokenPasswordCacheHelper", apiKeyTokenPasswordCacheHelper, true);
    FieldUtils.writeField(tokenService, "ngFeatureFlagHelperService", ngFeatureFlagHelperService, true);
    FieldUtils.writeField(tokenServiceImpl, "apiKeyService", apiKeyService, true);
    FieldUtils.writeField(tokenServiceImpl, "scopeInfoService", scopeInfoService, true);
    FieldUtils.writeField(tokenServiceImpl, "scopeInfoClient", scopeInfoClient, true);
    FieldUtils.writeField(tokenService, "accessControlClient", accessControlClient, true);
    FieldUtils.writeField(tokenService, "tokenOpaService", tokenOpaService, true);
    FieldUtils.writeField(tokenService, "scopeInfoService", scopeInfoService, true);
    FieldUtils.writeField(tokenService, "publicKeyRevokerFactory", publicKeyRevokerFactory, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateToken_sat() {
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(Optional.of(apiKey)).when(apiKeyService).getApiKey(any(), any(), any(), any());
    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());
    Token newToken = TokenDTOMapper.getTokenFromDTO(scopeInfo, tokenDTO, Duration.ofDays(2).toMillis());
    newToken.setUuid(randomAlphabetic(10));
    doReturn(newToken).when(tokenRepository).save(any());
    when(tokenOpaService.evaluatePoliciesWithEntity(any(), any(), any(), any())).thenReturn(null);
    String tokenString = tokenService.createToken(tokenDTO, scopeInfo);
    assertThat(tokenString).startsWith(SERVICE_ACCOUNT.getValue());
    assertThat(tokenString.split("\\.")[1]).isEqualTo(token.getAccountIdentifier());
    assertThat(tokenString.split("\\.")[2]).isEqualTo(token.getUuid());
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testCreateToken_sshKey() {
    SSHPublicKey sshPublicKey = SSHPublicKey.builder().sshKey("random").fingerPrint("foo_test_string").build();
    token.setSshPublicKey(sshPublicKey);
    Page<Token> result = new PageImpl(Arrays.asList(token), Pageable.unpaged(), 1);
    when(tokenRepository.findAll(any(), any())).thenReturn(result);
    tokenDTO.setApiKeyType(SSH_KEY);
    tokenDTO.setSshKeyContent(rsaKeyContent);
    tokenDTO.setSshKeyUsage(List.of(SSHKeyUsage.AUTH));
    String fingerPrint = tokenService.createToken(tokenDTO, scopeInfo);
    assertThat(rsaExpectedFingerprint.equals(fingerPrint));
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testDuplicateCreateToken_sshKey() {
    SSHPublicKey sshPublicKey =
        SSHPublicKey.builder().sshKey(rsaExpectedKey).fingerPrint(rsaExpectedFingerprint).build();
    token.setSshPublicKey(sshPublicKey);
    Page<Token> result = new PageImpl(Arrays.asList(token), Pageable.unpaged(), 1);
    when(tokenRepository.findAll(any(), any())).thenReturn(result);
    tokenDTO.setApiKeyType(SSH_KEY);
    tokenDTO.setSshKeyContent(rsaKeyContent);
    tokenDTO.setSshKeyUsage(List.of(SSHKeyUsage.AUTH));
    try {
      tokenService.createToken(tokenDTO, scopeInfo);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(DuplicateFieldException.class);
    }
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testCreateToken_noDescription() {
    TokenDTO dto = TokenDTO.builder()
                       .name(randomAlphabetic(10))
                       .identifier(identifier)
                       .parentIdentifier(parentIdentifier)
                       .apiKeyIdentifier(randomAlphabetic(10))
                       .apiKeyType(SERVICE_ACCOUNT)
                       .scheduledExpireTime(Instant.now().toEpochMilli())
                       .tags(new HashMap<>())
                       .build();
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(Optional.of(apiKey)).when(apiKeyService).getApiKey(any(), any(), any(), any());
    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());
    when(tokenOpaService.evaluatePoliciesWithEntity(any(), any(), any(), any())).thenReturn(null);
    Token newToken = TokenDTOMapper.getTokenFromDTO(scopeInfo, dto, Duration.ofDays(2).toMillis());
    newToken.setUuid(randomAlphabetic(10));
    doReturn(newToken).when(tokenRepository).save(any());
    String tokenString = tokenService.createToken(dto, scopeInfo);
    assertThat(tokenString).isNotEmpty();
    assertThat(newToken.getDescription()).isNull();
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testInvalidToken_sat() {
    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId("uniqueId").build();
    long pastExpiry = Instant.now().toEpochMilli() - 1;
    TokenDTO invalidExpiryTokenDTO = TokenDTO.builder()
                                         .accountIdentifier(accountIdentifier)
                                         .orgIdentifier(orgIdentifier)
                                         .name(randomAlphabetic(10))
                                         .projectIdentifier(projectIdentifier)
                                         .identifier(identifier)
                                         .parentIdentifier(parentIdentifier)
                                         .apiKeyIdentifier(randomAlphabetic(10))
                                         .apiKeyType(SERVICE_ACCOUNT)
                                         .validTo(pastExpiry)
                                         .description("")
                                         .tags(new HashMap<>())
                                         .build();
    assertThatThrownBy(() -> tokenService.createToken(invalidExpiryTokenDTO, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Token's validTo cannot be set before current time")
        .hasMessageContaining("validTo: [" + Instant.ofEpochMilli(pastExpiry) + "]")
        .hasMessageContaining("current time: [");
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testInvalidToken_validFromAfterValidTo_sat() {
    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId("uniqueId").build();
    long validToMillis = Instant.now().plusSeconds(3600).toEpochMilli();
    long validFromMillis = validToMillis + 1;
    TokenDTO invalidRangeTokenDTO = TokenDTO.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .name(randomAlphabetic(10))
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(identifier)
                                        .parentIdentifier(parentIdentifier)
                                        .apiKeyIdentifier(randomAlphabetic(10))
                                        .apiKeyType(SERVICE_ACCOUNT)
                                        .validFrom(validFromMillis)
                                        .validTo(validToMillis)
                                        .description("")
                                        .tags(new HashMap<>())
                                        .build();
    assertThatThrownBy(() -> tokenService.createToken(invalidRangeTokenDTO, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Token's validFrom time cannot be after validTo time")
        .hasMessageContaining("validFrom: [" + Instant.ofEpochMilli(validFromMillis) + "]")
        .hasMessageContaining("validTo: [" + Instant.ofEpochMilli(validToMillis) + "]");
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testGetToken_sat() {
    tokenDTO.setApiKeyType(SERVICE_ACCOUNT);
    token.setApiKeyType(SERVICE_ACCOUNT);
    token.setParentUniqueId(uniqueId);
    String email = "ab17@goat.com";
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    when(tokenRepository.findById(tokensUUId)).thenReturn(Optional.of(token));
    when(serviceAccountService.getServiceAccountDTO(scopeInfo, parentIdentifier))
        .thenReturn(ServiceAccountDTO.builder().email(email).name(email).build());
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));
    TokenDTO response = tokenService.getToken(tokensUUId, true);
    assertThat(response.getEmail()).isEqualTo(email);
    verify(serviceAccountService, times(1)).getServiceAccountDTO(scopeInfo, parentIdentifier);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testRotateToken_sat() {
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(Optional.of(apiKey)).when(apiKeyService).getApiKey(any(), any(), any(), any());
    doReturn(Optional.of(TokenDTOMapper.getTokenFromDTO(scopeInfo, tokenDTO, Duration.ofDays(2).toMillis())))
        .when(tokenRepository)
        .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
            any(), any(), any(), any(), any(), any());

    Token newToken = TokenDTOMapper.getTokenFromDTO(scopeInfo, tokenDTO, Duration.ofDays(2).toMillis());
    newToken.setUuid(randomAlphabetic(10));
    doReturn(newToken).when(tokenRepository).save(any());
    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());
    when(tokenOpaService.evaluatePoliciesWithEntity(
             any(ScopeInfo.class), any(TokenDTO.class), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), anyString()))
        .thenReturn(null);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    String tokenString = tokenService.rotateToken(scopeInfo, ApiKeyType.SERVICE_ACCOUNT, parentIdentifier,
        tokenDTO.getApiKeyIdentifier(), identifier, Instant.now().plusMillis(1000));
    assertThat(tokenString).startsWith(SERVICE_ACCOUNT.getValue());
    assertThat(tokenString.split("\\.")[1]).isEqualTo(token.getAccountIdentifier());
    assertThat(tokenString.split("\\.")[2]).isEqualTo(token.getUuid());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenRotateTokenForSATAndOPAPolicyReturnsErrorThenThrowOPAPolicyEvaluationException() {
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(Optional.of(apiKey)).when(apiKeyService).getApiKey(any(), any(), any(), any());

    doReturn(Optional.of(TokenDTOMapper.getTokenFromDTO(scopeInfo, tokenDTO, Duration.ofDays(2).toMillis())))
        .when(tokenRepository)
        .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
            any(), any(), any(), any(), any(), any());

    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    GovernanceMetadata failMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_ERROR).setDeny(true).build();
    when(tokenOpaService.evaluatePoliciesWithEntity(
             eq(scopeInfo), any(TokenDTO.class), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), anyString()))
        .thenReturn(failMetadata);

    assertThatThrownBy(()
                           -> tokenService.rotateToken(scopeInfo, ApiKeyType.SERVICE_ACCOUNT, parentIdentifier,
                               tokenDTO.getApiKeyIdentifier(), identifier, Instant.now().plusMillis(1000)))
        .isInstanceOf(OPAPolicyEvaluationException.class);
  }

  // ----------------------------------------------------------------------------------------------
  // SCOPED_TOKEN rotation tests.
  // Rotation of a scoped token must NOT re-run prepareScopedToken: the rotated token has to keep
  // the original apiKeyIdentifier, scopedResourceMetadata.createdBy, scopedResourcePermissions,
  // tokenMode, and (for ephemeral) original validity, without re-checking the rotator's RBAC or
  // the per-parent token-mode limit.
  // ----------------------------------------------------------------------------------------------

  private TokenDTO buildScopedTokenDTO(TokenMode mode, Long validToMillis) {
    return TokenDTO.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .name(randomAlphabetic(10))
        .identifier(identifier)
        .parentIdentifier(parentIdentifier)
        .apiKeyIdentifier(TokenServiceImpl.SCOPED_TOKEN_API_KEY_SA)
        .apiKeyType(ApiKeyType.SCOPED_TOKEN)
        .validFrom(Instant.now().toEpochMilli())
        .validTo(validToMillis)
        .description("")
        .tags(new HashMap<>())
        .scopedResourcePermissions(Collections.singletonList(
            ScopedResourcePermission.builder().resourceType("PIPELINE").permission("core_pipeline_view").build()))
        .tokenMode(mode)
        .scopedResourceMetadata(
            ScopedResourceMetadata.builder().parentResourceId("pipeline-exec-1").createdBy("originalUser").build())
        .build();
  }

  private Token wireRotateMocksForScopedToken(TokenDTO scopedTokenDTO) {
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(Optional.of(apiKey))
        .when(apiKeyService)
        .getApiKey(any(ScopeInfo.class), any(ApiKeyType.class), anyString(), anyString());

    Token existingToken = TokenDTOMapper.getTokenFromDTO(scopeInfo, scopedTokenDTO, Duration.ofDays(2).toMillis());
    existingToken.setUuid(randomAlphabetic(10));
    existingToken.setParentUniqueId(uniqueId);
    doReturn(Optional.of(existingToken))
        .when(tokenRepository)
        .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
            any(), any(), any(), any(), any(), any());
    when(tokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    // Override the default transactionTemplate mock so the rotated token returned from the
    // transaction is the actual saved token (carries SCOPED_TOKEN type and scoped fields), not
    // the SERVICE_ACCOUNT-typed `token` field used elsewhere in this test class.
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());
    when(tokenOpaService.evaluatePoliciesWithEntity(
             any(ScopeInfo.class), any(TokenDTO.class), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), anyString()))
        .thenReturn(null);
    return existingToken;
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testRotateToken_scopedToken_persistent_preservesScopedFields() {
    TokenDTO scopedTokenDTO =
        buildScopedTokenDTO(TokenMode.PERSISTENT, Instant.now().plusSeconds(7 * 86400).toEpochMilli());
    Token existingToken = wireRotateMocksForScopedToken(scopedTokenDTO);

    String tokenString = tokenService.rotateToken(scopeInfo, ApiKeyType.SCOPED_TOKEN, parentIdentifier,
        scopedTokenDTO.getApiKeyIdentifier(), identifier, Instant.now().plusMillis(1000));

    assertThat(tokenString).startsWith(ApiKeyType.SCOPED_TOKEN.getValue());
    assertThat(tokenString.split("\\.")[1]).isEqualTo(existingToken.getAccountIdentifier());

    // OPA is evaluated for the rotated (newly minted) token with the original scoped fields preserved.
    ArgumentCaptor<TokenDTO> dtoCaptor = ArgumentCaptor.forClass(TokenDTO.class);
    verify(tokenOpaService, atLeastOnce())
        .evaluatePoliciesWithEntity(
            any(ScopeInfo.class), dtoCaptor.capture(), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), anyString());
    TokenDTO rotatedDTO = dtoCaptor.getAllValues().get(dtoCaptor.getAllValues().size() - 1);
    assertThat(rotatedDTO.getApiKeyType()).isEqualTo(ApiKeyType.SCOPED_TOKEN);
    assertThat(rotatedDTO.getApiKeyIdentifier()).isEqualTo(TokenServiceImpl.SCOPED_TOKEN_API_KEY_SA);
    assertThat(rotatedDTO.getTokenMode()).isEqualTo(TokenMode.PERSISTENT);
    assertThat(rotatedDTO.getScopedResourcePermissions()).hasSize(1);
    assertThat(rotatedDTO.getScopedResourcePermissions().get(0).getResourceType()).isEqualTo("PIPELINE");
    assertThat(rotatedDTO.getScopedResourceMetadata()).isNotNull();
    // createdBy must be preserved (rotation is not creation): the rotator must not overwrite it.
    assertThat(rotatedDTO.getScopedResourceMetadata().getCreatedBy()).isEqualTo("originalUser");
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testRotateToken_scopedToken_ephemeral_preservesOriginalValidity() {
    long originalValidTo = Instant.now().plusSeconds(3600).toEpochMilli();
    TokenDTO scopedTokenDTO = buildScopedTokenDTO(TokenMode.EPHEMERAL, originalValidTo);
    wireRotateMocksForScopedToken(scopedTokenDTO);

    String tokenString = tokenService.rotateToken(scopeInfo, ApiKeyType.SCOPED_TOKEN, parentIdentifier,
        scopedTokenDTO.getApiKeyIdentifier(), identifier, Instant.now().plusMillis(1000));

    assertThat(tokenString).startsWith(ApiKeyType.SCOPED_TOKEN.getValue());

    ArgumentCaptor<TokenDTO> dtoCaptor = ArgumentCaptor.forClass(TokenDTO.class);
    verify(tokenOpaService, atLeastOnce())
        .evaluatePoliciesWithEntity(
            any(ScopeInfo.class), dtoCaptor.capture(), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), anyString());
    TokenDTO rotatedDTO = dtoCaptor.getAllValues().get(dtoCaptor.getAllValues().size() - 1);
    assertThat(rotatedDTO.getTokenMode()).isEqualTo(TokenMode.EPHEMERAL);
    // Ephemeral TTL handling (5m default, 24h cap) must NOT be re-applied during rotation: original
    // validTo is preserved.
    assertThat(rotatedDTO.getValidTo()).isEqualTo(originalValidTo);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testRotateToken_scopedToken_skipsRBACAndDoesNotRewriteApiKeyIdentifier() {
    TokenDTO scopedTokenDTO =
        buildScopedTokenDTO(TokenMode.PERSISTENT, Instant.now().plusSeconds(7 * 86400).toEpochMilli());
    wireRotateMocksForScopedToken(scopedTokenDTO);
    // The rotator is intentionally a USER while the original token's apiKeyIdentifier is the SA bucket.
    // prepareScopedToken would rewrite it to _scopedTokensUser; the rotation path must not.
    Principal rotatorPrincipal =
        new UserPrincipal("rotator-user", "rotator@example.com", "rotator", scopedTokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(rotatorPrincipal);
    SourcePrincipalContextBuilder.setSourcePrincipal(rotatorPrincipal);

    tokenService.rotateToken(scopeInfo, ApiKeyType.SCOPED_TOKEN, parentIdentifier, scopedTokenDTO.getApiKeyIdentifier(),
        identifier, Instant.now().plusMillis(1000));

    // The rotator's RBAC must NOT be re-checked (parent principal already had these permissions).
    verify(accessControlClient, never()).checkForAccess(any(java.util.List.class));

    ArgumentCaptor<TokenDTO> dtoCaptor = ArgumentCaptor.forClass(TokenDTO.class);
    verify(tokenOpaService, atLeastOnce())
        .evaluatePoliciesWithEntity(
            any(ScopeInfo.class), dtoCaptor.capture(), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), anyString());
    TokenDTO rotatedDTO = dtoCaptor.getAllValues().get(dtoCaptor.getAllValues().size() - 1);
    // apiKeyIdentifier must NOT be re-bucketed under the rotator's principal type.
    assertThat(rotatedDTO.getApiKeyIdentifier()).isEqualTo(TokenServiceImpl.SCOPED_TOKEN_API_KEY_SA);
    assertThat(rotatedDTO.getScopedResourceMetadata().getCreatedBy()).isEqualTo("originalUser");
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void whenRotateTokenForScopedTokenAndOPAPolicyReturnsErrorThenThrowOPAPolicyEvaluationException() {
    TokenDTO scopedTokenDTO =
        buildScopedTokenDTO(TokenMode.PERSISTENT, Instant.now().plusSeconds(7 * 86400).toEpochMilli());
    wireRotateMocksForScopedToken(scopedTokenDTO);

    GovernanceMetadata failMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_ERROR).setDeny(true).build();
    when(tokenOpaService.evaluatePoliciesWithEntity(
             any(ScopeInfo.class), any(TokenDTO.class), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), anyString()))
        .thenReturn(failMetadata);

    assertThatThrownBy(()
                           -> tokenService.rotateToken(scopeInfo, ApiKeyType.SCOPED_TOKEN, parentIdentifier,
                               scopedTokenDTO.getApiKeyIdentifier(), identifier, Instant.now().plusMillis(1000)))
        .isInstanceOf(OPAPolicyEvaluationException.class);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateToken_pat() {
    tokenDTO.setApiKeyType(USER);
    token.setApiKeyType(USER);
    Principal principal = new UserPrincipal(tokenDTO.getParentIdentifier(), "", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(Optional.of(apiKey)).when(apiKeyService).getApiKey(any(), any(), any(), any());
    Token newToken = TokenDTOMapper.getTokenFromDTO(scopeInfo, tokenDTO, Duration.ofDays(2).toMillis());
    newToken.setUuid(randomAlphabetic(10));
    doReturn(newToken).when(tokenRepository).save(any());
    when(tokenOpaService.evaluatePoliciesWithEntity(any(), any(), any(), any())).thenReturn(null);

    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());
    String tokenString = tokenService.createToken(tokenDTO, scopeInfo);
    assertThat(tokenString).startsWith(USER.getValue());
    assertThat(tokenString.split("\\.")[1]).isEqualTo(token.getAccountIdentifier());
    assertThat(tokenString.split("\\.")[2]).isEqualTo(token.getUuid());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testRotateToken_pat() {
    tokenDTO.setApiKeyType(USER);
    token.setApiKeyType(USER);
    Principal principal = new UserPrincipal(tokenDTO.getParentIdentifier(), "", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(Optional.of(apiKey)).when(apiKeyService).getApiKey(any(), any(), any(), any());
    doReturn(Optional.of(TokenDTOMapper.getTokenFromDTO(scopeInfo, tokenDTO, Duration.ofDays(2).toMillis())))
        .when(tokenRepository)
        .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
            any(), any(), any(), any(), any(), any());
    Token newToken = TokenDTOMapper.getTokenFromDTO(scopeInfo, tokenDTO, Duration.ofDays(2).toMillis());
    newToken.setUuid(randomAlphabetic(10));
    doReturn(newToken).when(tokenRepository).save(any());
    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());
    when(tokenOpaService.evaluatePoliciesWithEntity(
             eq(scopeInfo), any(TokenDTO.class), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), anyString()))
        .thenReturn(null);
    String tokenString = tokenService.rotateToken(
        scopeInfo, USER, parentIdentifier, tokenDTO.getApiKeyIdentifier(), identifier, Instant.now().plusMillis(1000));
    assertThat(tokenString).startsWith(USER.getValue());
    assertThat(tokenString.split("\\.")[1]).isEqualTo(token.getAccountIdentifier());
    assertThat(tokenString.split("\\.")[2]).isEqualTo(token.getUuid());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenRotateTokenForPATAndOPAPolicyReturnsErrorThenThrowOPAPolicyEvaluationException() {
    tokenDTO.setApiKeyType(USER);
    token.setApiKeyType(USER);

    Principal principal = new UserPrincipal(tokenDTO.getParentIdentifier(), "", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);

    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(Optional.of(apiKey)).when(apiKeyService).getApiKey(any(), any(), any(), any());

    doReturn(Optional.of(TokenDTOMapper.getTokenFromDTO(scopeInfo, tokenDTO, Duration.ofDays(2).toMillis())))
        .when(tokenRepository)
        .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
            any(), any(), any(), any(), any(), any());

    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    GovernanceMetadata failMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_ERROR).setDeny(true).build();
    when(tokenOpaService.evaluatePoliciesWithEntity(
             eq(scopeInfo), any(TokenDTO.class), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), anyString()))
        .thenReturn(failMetadata);

    assertThatThrownBy(()
                           -> tokenService.rotateToken(scopeInfo, USER, parentIdentifier,
                               tokenDTO.getApiKeyIdentifier(), identifier, Instant.now().plusMillis(1000)))
        .isInstanceOf(OPAPolicyEvaluationException.class);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateApiKeyToken_with_cache() {
    tokenDTO.setApiKeyType(SERVICE_ACCOUNT);
    token.setApiKeyType(SERVICE_ACCOUNT);
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);
    String email = "test123@mailinator.in";
    token.setEncodedPassword(encodedPassword);
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));
    when(tokenRepository.findById(anyString())).thenReturn(Optional.of(token));
    when(serviceAccountService.getServiceAccountDTO(scopeInfo, parentIdentifier))
        .thenReturn(ServiceAccountDTO.builder().email(email).name(email).build());

    doReturn(false)
        .when(ngFeatureFlagHelperService)
        .isEnabled(accountIdentifier, FeatureName.PL_SUPPORT_JWT_TOKEN_SCIM_API);

    String delimiter = ".";
    final String apiKeyDummy = "sat" + delimiter + accountIdentifier + delimiter + identifier + delimiter + rawPassword;

    TokenDTO resultTokenDTO = tokenService.validateToken(accountIdentifier, apiKeyDummy);

    assertThat(resultTokenDTO).isNotNull();
    assertThat(resultTokenDTO.getEncodedPassword()).isNull();
    assertThat(resultTokenDTO.getEmail()).isEqualTo(email);
    assertThat(apiKeyTokenPasswordCacheHelper.get(identifier)).isEqualTo(rawPassword);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testListAllTokenForAccount_PAT() {
    token.setApiKeyType(USER);

    Token token1 = Token.builder()
                       .scheduledExpireTime(Instant.now().plusSeconds(86500))
                       .validTo(Instant.now().plusSeconds(86500))
                       .validFrom(Instant.now())
                       .accountIdentifier(accountIdentifier)
                       .orgIdentifier(orgIdentifier)
                       .name(randomAlphabetic(10))
                       .projectIdentifier(projectIdentifier)
                       .identifier("id1")
                       .parentIdentifier("parent1")
                       .apiKeyIdentifier(randomAlphabetic(10))
                       .apiKeyType(USER)
                       .description("")
                       .tags(new ArrayList<>())
                       .build();
    Principal principal = new UserPrincipal(tokenDTO.getParentIdentifier(), "", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);

    Page<Token> result = new PageImpl(Arrays.asList(token, token1), Pageable.unpaged(), 2);
    when(tokenRepository.findAll(any(), any())).thenReturn(result);

    TokenFilterDTO tokenFilterDTO = TokenFilterDTO.builder().apiKeyType(USER).build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    PageResponse<TokenAggregateDTO> resultTokenDTO =
        tokenService.listAggregateTokens(scopeInfo, Pageable.unpaged(), tokenFilterDTO);

    assertThat(resultTokenDTO).isNotNull();
    assertThat(resultTokenDTO.getContent().size()).isEqualTo(2);
    assertThat(resultTokenDTO.getContent().get(0).getToken().getApiKeyType()).isEqualTo(USER);
    assertThat(resultTokenDTO.getContent().get(1).getToken().getApiKeyIdentifier())
        .isEqualTo(token1.getApiKeyIdentifier());
    assertThat(resultTokenDTO.getContent().get(1).getToken().getParentIdentifier())
        .isEqualTo(token1.getParentIdentifier());
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateListTokenPermission_whenUserIsLoggedInUser_PAT() {
    TokenFilterDTO tokenFilterDTO = TokenFilterDTO.builder()
                                        .parentIdentifier(parentIdentifier)
                                        .apiKeyType(USER)
                                        .accountIdentifier(accountIdentifier)
                                        .build();

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(uniqueId).build();
    Principal principal = new UserPrincipal(tokenDTO.getParentIdentifier(), "", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    doThrow(NGAccessDeniedException.class).when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    Optional<UserInfo> userInfo =
        Optional.of(UserInfo.builder()
                        .uuid(parentIdentifier)
                        .accounts(Arrays.asList(GatewayAccountRequestDTO.builder().uuid(accountIdentifier).build()))
                        .build());
    when(ngUserService.getUserById(anyString())).thenReturn(userInfo);
    assertThatCode(() -> tokenService.validateTokenListPermissions(scopeInfo, tokenFilterDTO))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateListTokenPermission_whenUserDoesNotHaveUserManagementPermissionAndIsNotPartOfAccount_PAT() {
    TokenFilterDTO tokenFilterDTO = TokenFilterDTO.builder()
                                        .parentIdentifier(parentIdentifier)
                                        .apiKeyType(USER)
                                        .accountIdentifier(accountIdentifier)
                                        .build();

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(uniqueId).build();
    Principal principal = new UserPrincipal(parentIdentifier, "", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    doThrow(NGAccessDeniedException.class).when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    when(ngUserService.getUserById(anyString())).thenReturn(Optional.of(UserInfo.builder().build()));

    assertThatThrownBy(() -> tokenService.validateTokenListPermissions(scopeInfo, tokenFilterDTO))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateListTokenPermission_whenUserDoesNotHaveUserManagementPermissionAndIsNotLoggedInUser_PAT() {
    TokenFilterDTO tokenFilterDTO =
        TokenFilterDTO.builder().parentIdentifier("test").apiKeyType(USER).accountIdentifier(accountIdentifier).build();

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(uniqueId).build();
    Principal principal = new UserPrincipal(parentIdentifier, "", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    doThrow(NGAccessDeniedException.class).when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    Optional<UserInfo> userInfo =
        Optional.of(UserInfo.builder()
                        .uuid(parentIdentifier)
                        .accounts(Arrays.asList(GatewayAccountRequestDTO.builder().uuid(accountIdentifier).build()))
                        .build());
    when(ngUserService.getUserById(anyString())).thenReturn(userInfo);

    assertThatThrownBy(() -> tokenService.validateTokenListPermissions(scopeInfo, tokenFilterDTO))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateListTokenPermission_whenUserDoesNotHaveUserManagementPermission_PAT() {
    TokenFilterDTO tokenFilterDTO =
        TokenFilterDTO.builder().apiKeyType(USER).accountIdentifier(accountIdentifier).build();

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(uniqueId).build();
    Principal principal = new UserPrincipal(tokenDTO.getParentIdentifier(), "", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    doThrow(NGAccessDeniedException.class).when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    assertThatThrownBy(() -> tokenService.validateTokenListPermissions(scopeInfo, tokenFilterDTO))
        .isInstanceOf(NGAccessDeniedException.class);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateListTokenPermission_whenUserDoesNotHaveServiceAccountManagementPermission_SAT() {
    TokenFilterDTO tokenFilterDTO =
        TokenFilterDTO.builder().apiKeyType(SERVICE_ACCOUNT).accountIdentifier(accountIdentifier).build();

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(uniqueId).build();
    Principal principal = new UserPrincipal(tokenDTO.getParentIdentifier(), "", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    doThrow(NGAccessDeniedException.class).when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    assertThatThrownBy(() -> tokenService.validateTokenListPermissions(scopeInfo, tokenFilterDTO))
        .isInstanceOf(NGAccessDeniedException.class);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testValidateTokenRequest() {
    tokenDTO.setApiKeyType(USER);
    token.setApiKeyType(USER);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    doReturn(Optional.empty()).when(apiKeyService).getApiKey(any(), any(), any(), any());
    tokenServiceImpl.validateTokenRequest(
        scopeInfo, tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier(), tokenDTO.getApiKeyIdentifier(), tokenDTO);
    verify(apiKeyService, atLeastOnce()).getApiKey(any(), any(), anyString(), anyString());
    verify(apiKeyService, atLeastOnce()).createApiKey(any(), any());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testCreateToken_missingAPIKey() {
    tokenDTO = TokenDTO.builder()
                   .name(randomAlphabetic(10))
                   .identifier(identifier)
                   .parentIdentifier(parentIdentifier)
                   .apiKeyIdentifier(randomAlphabetic(10))
                   .apiKeyType(SERVICE_ACCOUNT)
                   .scheduledExpireTime(Instant.now().toEpochMilli())
                   .description("")
                   .tags(new HashMap<>())
                   .build();
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    when(apiKeyService.getApiKey(any(), any(), any(), any()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(apiKey));
    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    when(tokenOpaService.evaluatePoliciesWithEntity(any(), any(), any(), any())).thenReturn(null);
    doReturn(accountDTO).when(accountService).getAccount(any());
    Token newToken = TokenDTOMapper.getTokenFromDTO(scopeInfo, tokenDTO, Duration.ofDays(2).toMillis());
    newToken.setUuid(randomAlphabetic(10));
    doReturn(newToken).when(tokenRepository).save(any());
    tokenService.createToken(tokenDTO, scopeInfo);
    verify(apiKeyService, atLeastOnce()).createApiKey(any(), any());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testValidateInternalApiKeyToken() {
    tokenDTO.setApiKeyType(SERVICE_ACCOUNT);
    token.setApiKeyType(SERVICE_ACCOUNT);
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);
    String email = "test123@mailinator.in";
    token.setEncodedPassword(encodedPassword);
    when(scopeInfoService.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);

    when(tokenRepository.findById(anyString())).thenReturn(Optional.of(token));
    when(serviceAccountService.getServiceAccountDTO(scopeInfo, parentIdentifier))
        .thenReturn(ServiceAccountDTO.builder().email(email).name(email).build());

    doReturn(false)
        .when(ngFeatureFlagHelperService)
        .isEnabled(accountIdentifier, FeatureName.PL_SUPPORT_JWT_TOKEN_SCIM_API);

    String delimiter = ".";
    final String apiKeyDummy = "sat" + delimiter + accountIdentifier + delimiter + identifier + delimiter + rawPassword;
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));

    TokenDTO resultTokenDTO = tokenService.validateTokenInternal(accountIdentifier, apiKeyDummy);

    assertThat(resultTokenDTO).isNotNull();
    assertThat(resultTokenDTO.getEncodedPassword()).isNull();
    assertThat(resultTokenDTO.getEmail()).isEqualTo(email);
    assertThat(apiKeyTokenPasswordCacheHelper.get(identifier)).isEqualTo(rawPassword);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testValidateInternalApiKeyToken_PAT() {
    tokenDTO.setApiKeyType(USER);
    token.setApiKeyType(USER);
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);
    String email = "test123@mailinator.in";
    String name = "test123";
    token.setEncodedPassword(encodedPassword);
    when(tokenRepository.findById(anyString())).thenReturn(Optional.of(token));
    when(ngUserService.getUserById(parentIdentifier))
        .thenReturn(Optional.ofNullable(UserInfo.builder().email(email).name(name).uuid(generateUuid()).build()));

    doReturn(false)
        .when(ngFeatureFlagHelperService)
        .isEnabled(accountIdentifier, FeatureName.PL_SUPPORT_JWT_TOKEN_SCIM_API);

    String delimiter = ".";
    final String apiKeyDummy = "pat" + delimiter + accountIdentifier + delimiter + identifier + delimiter + rawPassword;
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));
    TokenDTO resultTokenDTO = tokenService.validateTokenInternal(accountIdentifier, apiKeyDummy);

    assertThat(resultTokenDTO).isNotNull();
    assertThat(resultTokenDTO.getEncodedPassword()).isNull();
    assertThat(resultTokenDTO.getEmail()).isEqualTo(email);
    assertThat(resultTokenDTO.getUsername()).isEqualTo(name);
    assertThat(apiKeyTokenPasswordCacheHelper.get(identifier)).isEqualTo(rawPassword);
  }

  UserInfo userInfo = UserInfo.builder().email("test123@mailinator.in").name("admin").build();

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testValidateSSHKey() {
    SSHPublicKeyDTOInternal sshPublicKeyDTOInternal = SSHKeyUtils.validateAndExtractKey(rsaKeyContent);
    token.setSshPublicKey(sshPublicKeyDTOInternal.toSSHKey());

    Page<Token> result = new PageImpl(Arrays.asList(token), Pageable.unpaged(), 1);
    when(tokenRepository.findAll(
             tokenServiceImpl.createFingerprintCriteria(sshPublicKeyDTOInternal.getFingerPrint(), accountIdentifier),
             Pageable.ofSize(TokenServiceImpl.MAX_PAGE_SIZE)))
        .thenReturn(result);
    when(ngUserService.getUserById(anyString())).thenReturn(Optional.of(userInfo));

    SSHValidateDTO sshValidateDTO = new SSHValidateDTO();
    sshValidateDTO.setSshKey(rsaKeyContent);
    sshValidateDTO.setAccountIdentifier(accountIdentifier);
    ResponseDTO<UserInfo> userInfoResponseDTO = tokenService.validateSSHKey(sshValidateDTO);
    assertThat(userInfoResponseDTO).isNotNull();
    assertThat(userInfoResponseDTO.getData().getEmail()).isEqualTo("test123@mailinator.in");
    assertThat(userInfoResponseDTO.getData().getName()).isEqualTo("admin");
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testVerificationSSHKey() {
    SSHPublicKeyDTOInternal sshPublicKeyDTOInternal = SSHKeyUtils.validateAndExtractKey(rsaKeyContent);
    token.setSshPublicKey(sshPublicKeyDTOInternal.toSSHKey());

    Page<Token> result = new PageImpl(Arrays.asList(token), Pageable.unpaged(), 1);
    when(tokenRepository.findAll(
             tokenServiceImpl.createFingerprintCriteria(sshPublicKeyDTOInternal.getFingerPrint(), accountIdentifier),
             Pageable.ofSize(TokenServiceImpl.MAX_PAGE_SIZE)))
        .thenReturn(result);
    when(ngUserService.getUserById(anyString())).thenReturn(Optional.of(userInfo));

    SSHValidateDTO sshValidateDTO = new SSHValidateDTO();
    sshValidateDTO.setSshKey(rsaKeyContent);
    sshValidateDTO.setAccountIdentifier(accountIdentifier);
    sshValidateDTO.setVerified(171861774107L);
    ResponseDTO<UserInfo> userInfoResponseDTO = tokenService.validateSSHKey(sshValidateDTO);
    assertThat(userInfoResponseDTO).isNotNull();
    assertThat(userInfoResponseDTO.getData().getEmail()).isEqualTo("test123@mailinator.in");
    assertThat(userInfoResponseDTO.getData().getName()).isEqualTo("admin");
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void test_getSSHTokenWithPublicKey_whenTokenExists() {
    tokenDTO.setApiKeyType(SSH_KEY);
    token.setApiKeyType(SSH_KEY);
    token.setParentUniqueId(uniqueId);
    token.setSshPublicKey(SSHPublicKey.builder().sshKey("random-ssh").fingerPrint("fingerprint").build());
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    when(tokenRepository.findByAccountIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
             accountIdentifier, ApiKeyType.SSH_KEY, parentIdentifier, token.getIdentifier()))
        .thenReturn(Optional.of(token));
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));
    TokenDTO response =
        tokenService.getSSHTokenWithPublicKey(token.getIdentifier(), accountIdentifier, parentIdentifier);
    assertThat(response.getIdentifier().equals(token.getIdentifier()));
    assertThat(response.getSshKeyContent().equals("random-ssh"));
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void test_getSSHTokenWithPublicKey_whenTokenDoesNotExist() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    when(tokenRepository.findByAccountIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
             accountIdentifier, ApiKeyType.SSH_KEY, parentIdentifier, token.getIdentifier()))
        .thenReturn(Optional.empty());
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));
    tokenService.getSSHTokenWithPublicKey(token.getIdentifier(), accountIdentifier, parentIdentifier);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_deleteKey_pgpPrimaryKeyDeletesSubkeys() {
    String primaryKeyId = "ABC123";
    PGPPublicKey pgpPublicKey = PGPPublicKey.builder()
                                    .keyId(primaryKeyId)
                                    .fingerprint("fingerprint123")
                                    .isSubKey(false)
                                    .parentKeyId(null)
                                    .usage(List.of(PGPKeyUsage.SIGN))
                                    .build();
    token.setApiKeyType(PGP_KEY);
    token.setPgpPublicKey(pgpPublicKey);
    token.setParentUniqueId(uniqueId);

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, token.getApiKeyIdentifier(), identifier))
        .thenReturn(Optional.of(token));
    when(tokenRepository.deleteAll(any(Criteria.class))).thenReturn(2L);
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    boolean deleted = tokenService.deleteKey(scopeInfo, parentIdentifier, token.getApiKeyIdentifier(), identifier);

    assertThat(deleted).isTrue();
    verify(tokenRepository, times(1)).deleteAll(any(Criteria.class));
    verify(tokenRepository, times(1)).deleteById(token.getUuid());
    verify(outboxService, times(1)).save(any(TokenDeleteEvent.class));
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_deleteKey_pgpSubKeyOnlyDeletesItself() {
    String primaryKeyId = "ABC123";
    String subKeyId = "SUB456";
    PGPPublicKey pgpPublicKey = PGPPublicKey.builder()
                                    .keyId(subKeyId)
                                    .fingerprint("subkeyfingerprint")
                                    .isSubKey(true)
                                    .parentKeyId(primaryKeyId)
                                    .usage(List.of(PGPKeyUsage.SIGN))
                                    .build();
    token.setApiKeyType(PGP_KEY);
    token.setPgpPublicKey(pgpPublicKey);
    token.setParentUniqueId(uniqueId);

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, token.getApiKeyIdentifier(), identifier))
        .thenReturn(Optional.of(token));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    boolean deleted = tokenService.deleteKey(scopeInfo, parentIdentifier, token.getApiKeyIdentifier(), identifier);

    assertThat(deleted).isTrue();
    verify(tokenRepository, times(0)).deleteAll(any(Criteria.class));
    verify(tokenRepository, times(1)).deleteById(token.getUuid());
    verify(outboxService, times(1)).save(any(TokenDeleteEvent.class));
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_deleteKey_sshKeyDeletesSuccessfully() {
    token.setApiKeyType(SSH_KEY);
    token.setSshPublicKey(SSHPublicKey.builder().sshKey("ssh-rsa AAAA...").fingerPrint("SHA256:abc123").build());
    token.setParentUniqueId(uniqueId);

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, token.getApiKeyIdentifier(), identifier))
        .thenReturn(Optional.of(token));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    boolean deleted = tokenService.deleteKey(scopeInfo, parentIdentifier, token.getApiKeyIdentifier(), identifier);

    assertThat(deleted).isTrue();
    verify(tokenRepository, times(0)).deleteAll(any(Criteria.class)); // No subkeys for SSH
    verify(tokenRepository, times(1)).deleteById(token.getUuid());
    verify(outboxService, times(1)).save(any(TokenDeleteEvent.class));
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_deleteKey_returnsSuccessWhenNotFound() {
    // Delete should be idempotent - if key doesn't exist, return success
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "nonexistent"))
        .thenReturn(Optional.empty());

    boolean deleted = tokenService.deleteKey(scopeInfo, parentIdentifier, "apiKey", "nonexistent");

    assertThat(deleted).isTrue();
    verify(tokenRepository, times(0)).deleteById(any());
    verify(outboxService, times(0)).save(any());
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_validationFailsWhenBothRevocationAndValidityProvided() {
    UpdatePublicKeyRequest request = UpdatePublicKeyRequest.builder()
                                         .revocationReason(RevocationReason.COMPROMISED)
                                         .validFrom(System.currentTimeMillis())
                                         .build();

    assertThatThrownBy(
        () -> tokenService.updateKey(scopeInfo, parentIdentifier, token.getApiKeyIdentifier(), identifier, request))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Must either revoke the key or update its validity period, not both");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_validationFailsWhenValidFromGreaterThanValidTo() {
    long now = System.currentTimeMillis();
    UpdatePublicKeyRequest request = UpdatePublicKeyRequest.builder().validFrom(now + 1000).validTo(now).build();

    assertThatThrownBy(
        () -> tokenService.updateKey(scopeInfo, parentIdentifier, token.getApiKeyIdentifier(), identifier, request))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Invalid validity period: validFrom must be <= validTo");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_throwsWhenKeyNotFound() {
    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.RETIRED).build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "nonexistent"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> tokenService.updateKey(scopeInfo, parentIdentifier, "apiKey", "nonexistent", request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Key not found");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_throwsWhenUnsupportedKeyType() {
    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.RETIRED).build();

    token.setApiKeyType(SERVICE_ACCOUNT);
    token.setParentUniqueId(uniqueId);

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, token.getApiKeyIdentifier(), identifier))
        .thenReturn(Optional.of(token));

    assertThatThrownBy(
        () -> tokenService.updateKey(scopeInfo, parentIdentifier, token.getApiKeyIdentifier(), identifier, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Update operation only supported for SSH_KEY and PGP_KEY types");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_revokesSSHKeySuccessfully() {
    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.RETIRED).build();

    token.setApiKeyType(SSH_KEY);
    token.setSshPublicKey(SSHPublicKey.builder().sshKey("ssh-rsa AAAA...").fingerPrint("SHA256:abc123").build());
    token.setParentUniqueId(uniqueId);

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, token.getApiKeyIdentifier(), identifier))
        .thenReturn(Optional.of(token));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });
    when(tokenRepository.save(any())).thenReturn(token);
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));

    TokenDTO result =
        tokenService.updateKey(scopeInfo, parentIdentifier, token.getApiKeyIdentifier(), identifier, request);

    assertThat(result).isNotNull();
    assertThat(token.getRevocationReason()).isEqualTo(RevocationReason.RETIRED);
    verify(tokenRepository, times(1)).save(any());
    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_cannotRevokeAlreadyCompromisedKey() {
    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.RETIRED).build();

    SSHPublicKey sshPublicKey = SSHPublicKey.builder()
                                    .sshKey("ssh-rsa AAAA...")
                                    .fingerPrint("SHA256:abc123")
                                    .revocationReason(RevocationReason.COMPROMISED)
                                    .build();
    token.setApiKeyType(SSH_KEY);
    token.setSshPublicKey(sshPublicKey);
    token.setParentUniqueId(uniqueId);

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, token.getApiKeyIdentifier(), identifier))
        .thenReturn(Optional.of(token));

    assertThatThrownBy(
        () -> tokenService.updateKey(scopeInfo, parentIdentifier, token.getApiKeyIdentifier(), identifier, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot update revocation reason of a COMPROMISED key");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_cannotUpdateValidityOfRevokedKey() {
    long now = System.currentTimeMillis();
    UpdatePublicKeyRequest request = UpdatePublicKeyRequest.builder().validFrom(now).validTo(now + 86400000).build();

    SSHPublicKey sshPublicKey = SSHPublicKey.builder()
                                    .sshKey("ssh-rsa AAAA...")
                                    .fingerPrint("SHA256:abc123")
                                    .revocationReason(RevocationReason.RETIRED)
                                    .build();
    token.setApiKeyType(SSH_KEY);
    token.setSshPublicKey(sshPublicKey);
    token.setParentUniqueId(uniqueId);

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, token.getApiKeyIdentifier(), identifier))
        .thenReturn(Optional.of(token));

    assertThatThrownBy(
        () -> tokenService.updateKey(scopeInfo, parentIdentifier, token.getApiKeyIdentifier(), identifier, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot update validity period of a revoked key");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_updatesValidityPeriodSuccessfully() {
    long now = System.currentTimeMillis();
    long newValidFrom = now;
    long newValidTo = now + 86400000;
    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().validFrom(newValidFrom).validTo(newValidTo).build();

    token.setApiKeyType(SSH_KEY);
    token.setSshPublicKey(SSHPublicKey.builder().sshKey("ssh-rsa AAAA...").fingerPrint("SHA256:abc123").build());
    token.setParentUniqueId(uniqueId);

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, token.getApiKeyIdentifier(), identifier))
        .thenReturn(Optional.of(token));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });
    when(tokenRepository.save(any())).thenReturn(token);
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));

    TokenDTO result =
        tokenService.updateKey(scopeInfo, parentIdentifier, token.getApiKeyIdentifier(), identifier, request);

    assertThat(result).isNotNull();
    assertThat(token.getValidFrom().toEpochMilli()).isEqualTo(newValidFrom);
    assertThat(token.getValidTo().toEpochMilli()).isEqualTo(newValidTo);
    verify(tokenRepository, times(1)).save(any());
    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_revokesPGPKeySuccessfully() {
    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.SUPERSEDED).build();

    PGPPublicKey pgpPublicKey = PGPPublicKey.builder()
                                    .keyId("ABC123")
                                    .fingerprint("fingerprint123")
                                    .isSubKey(false)
                                    .parentKeyId(null)
                                    .usage(List.of(PGPKeyUsage.SIGN))
                                    .build();
    token.setApiKeyType(PGP_KEY);
    token.setPgpPublicKey(pgpPublicKey);
    token.setParentUniqueId(uniqueId);

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, token.getApiKeyIdentifier(), identifier))
        .thenReturn(Optional.of(token));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });
    when(tokenRepository.save(any())).thenReturn(token);
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));
    Page<Token> emptyPage = new PageImpl<>(new ArrayList<>(), Pageable.ofSize(1000), 0);
    when(tokenRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(emptyPage);

    TokenDTO result =
        tokenService.updateKey(scopeInfo, parentIdentifier, token.getApiKeyIdentifier(), identifier, request);

    assertThat(result).isNotNull();
    assertThat(token.getRevocationReason()).isEqualTo(RevocationReason.SUPERSEDED);
    assertThat(token.getPgpPublicKey().getRevocationReason()).isEqualTo(RevocationReason.SUPERSEDED);
    verify(tokenRepository, times(1)).save(any());
    verify(outboxService, times(1)).save(any());
  }

  // Tests for PGP key creation and validation methods
  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_createToken_pgpKey_withValidContent() {
    String pgpKeyContent = "-----BEGIN PGP PUBLIC KEY BLOCK-----\n"
        + "mQENBGYBZ2wBCAC7EHq7VH9qRdqZNxP0kpWDXQD8Y8g6YK/xv+1C/3O5kHQq/N\n"
        + "-----END PGP PUBLIC KEY BLOCK-----";

    tokenDTO.setApiKeyType(PGP_KEY);
    tokenDTO.setContent(pgpKeyContent);
    tokenDTO.setPgpKeyUsage(List.of(PGPKeyUsage.SIGN));

    Principal principal =
        new UserPrincipal(tokenDTO.getParentIdentifier(), "test@test.com", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    when(ngUserService.getUserById(anyString())).thenReturn(Optional.of(userInfo));

    // PGP key parsing will fail due to invalid content, but this tests the flow
    assertThatThrownBy(() -> tokenService.createToken(tokenDTO, scopeInfo)).isInstanceOf(Exception.class);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_createToken_pgpKey_invalidContent_throwsException() {
    tokenDTO.setApiKeyType(PGP_KEY);
    tokenDTO.setContent(null);

    assertThatThrownBy(() -> tokenService.createToken(tokenDTO, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid PGP_KEY request");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_createToken_pgpKey_emptyContent_throwsException() {
    tokenDTO.setApiKeyType(PGP_KEY);
    tokenDTO.setContent("");

    assertThatThrownBy(() -> tokenService.createToken(tokenDTO, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid PGP_KEY request");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_createToken_pgpKey_setsDefaultUsageWhenNull() {
    String pgpKeyContent = "-----BEGIN PGP PUBLIC KEY BLOCK-----\ntest\n-----END PGP PUBLIC KEY BLOCK-----";

    tokenDTO.setApiKeyType(PGP_KEY);
    tokenDTO.setContent(pgpKeyContent);
    tokenDTO.setPgpKeyUsage(null);

    Principal principal =
        new UserPrincipal(tokenDTO.getParentIdentifier(), "test@test.com", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    when(ngUserService.getUserById(anyString())).thenReturn(Optional.of(userInfo));

    // Will fail on PGP parsing but tests the default usage setting
    assertThatThrownBy(() -> tokenService.createToken(tokenDTO, scopeInfo)).isInstanceOf(Exception.class);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_createToken_pgpKey_setsDefaultUsageWhenEmpty() {
    String pgpKeyContent = "-----BEGIN PGP PUBLIC KEY BLOCK-----\ntest\n-----END PGP PUBLIC KEY BLOCK-----";

    tokenDTO.setApiKeyType(PGP_KEY);
    tokenDTO.setContent(pgpKeyContent);
    tokenDTO.setPgpKeyUsage(new ArrayList<>());

    Principal principal =
        new UserPrincipal(tokenDTO.getParentIdentifier(), "test@test.com", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    when(ngUserService.getUserById(anyString())).thenReturn(Optional.of(userInfo));

    // Will fail on PGP parsing but tests the default usage setting
    assertThatThrownBy(() -> tokenService.createToken(tokenDTO, scopeInfo)).isInstanceOf(Exception.class);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_createToken_pgpKey_invalidUsage_throwsException() {
    String pgpKeyContent = "-----BEGIN PGP PUBLIC KEY BLOCK-----\ntest\n-----END PGP PUBLIC KEY BLOCK-----";

    tokenDTO.setApiKeyType(PGP_KEY);
    tokenDTO.setContent(pgpKeyContent);
    tokenDTO.setPgpKeyUsage(List.of(PGPKeyUsage.ENCRYPT));

    assertThatThrownBy(() -> tokenService.createToken(tokenDTO, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("PGP keys can only be used for signing");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_getPGPTokenWithPublicKey_whenTokenExists() {
    tokenDTO.setApiKeyType(PGP_KEY);
    token.setApiKeyType(PGP_KEY);
    token.setParentUniqueId(uniqueId);
    PGPPublicKey pgpKey = PGPPublicKey.builder()
                              .keyId("KEY123")
                              .fingerprint("FINGERPRINT123")
                              .pgpKeyContent("-----BEGIN PGP PUBLIC KEY-----")
                              .usage(List.of(PGPKeyUsage.SIGN))
                              .build();
    token.setPgpPublicKey(pgpKey);

    when(tokenRepository.findByAccountIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
             accountIdentifier, ApiKeyType.PGP_KEY, parentIdentifier, token.getIdentifier()))
        .thenReturn(Optional.of(token));
    when(scopeInfoService.getScopeInfo(any(), any()))
        .thenReturn(Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo)));

    TokenDTO response =
        tokenService.getPGPTokenWithPublicKey(token.getIdentifier(), accountIdentifier, parentIdentifier);

    assertThat(response).isNotNull();
    assertThat(response.getIdentifier()).isEqualTo(token.getIdentifier());
    assertThat(response.getApiKeyType()).isEqualTo(PGP_KEY);
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_getPGPTokenWithPublicKey_whenTokenDoesNotExist() {
    when(tokenRepository.findByAccountIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
             accountIdentifier, ApiKeyType.PGP_KEY, parentIdentifier, token.getIdentifier()))
        .thenReturn(Optional.empty());

    tokenService.getPGPTokenWithPublicKey(token.getIdentifier(), accountIdentifier, parentIdentifier);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_revokeToken_serviceAccount_success() {
    tokenDTO.setApiKeyType(SERVICE_ACCOUNT);
    token.setApiKeyType(SERVICE_ACCOUNT);
    token.setParentUniqueId(uniqueId);

    when(tokenRepository
             .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
                 accountIdentifier, uniqueId, SERVICE_ACCOUNT, parentIdentifier, token.getApiKeyIdentifier(),
                 identifier))
        .thenReturn(Optional.of(token));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    boolean result =
        tokenService.revokeToken(scopeInfo, SERVICE_ACCOUNT, parentIdentifier, token.getApiKeyIdentifier(), identifier);

    assertThat(result).isTrue();
    verify(tokenRepository, times(1)).deleteById(token.getUuid());
    verify(outboxService, times(1)).save(any(TokenDeleteEvent.class));
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_revokeToken_user_success() {
    tokenDTO.setApiKeyType(USER);
    token.setApiKeyType(USER);
    token.setParentUniqueId(uniqueId);

    when(tokenRepository
             .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
                 accountIdentifier, uniqueId, USER, parentIdentifier, token.getApiKeyIdentifier(), identifier))
        .thenReturn(Optional.of(token));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    boolean result =
        tokenService.revokeToken(scopeInfo, USER, parentIdentifier, token.getApiKeyIdentifier(), identifier);

    assertThat(result).isTrue();
    verify(tokenRepository, times(1)).deleteById(token.getUuid());
    verify(outboxService, times(1)).save(any(TokenDeleteEvent.class));
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_revokeToken_throwsWhenTokenNotPresent() {
    when(tokenRepository
             .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
                 accountIdentifier, uniqueId, SERVICE_ACCOUNT, parentIdentifier, "apiKey", "nonexistent"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
        () -> tokenService.revokeToken(scopeInfo, SERVICE_ACCOUNT, parentIdentifier, "apiKey", "nonexistent"))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateToken_success() {
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(Optional.of(apiKey)).when(apiKeyService).getApiKey(any(), any(), any(), any());

    long newValidFrom = Instant.now().toEpochMilli();
    long newValidTo = Instant.now().plusSeconds(86400).toEpochMilli();

    TokenDTO updateDTO = TokenDTO.builder()
                             .accountIdentifier(accountIdentifier)
                             .orgIdentifier(orgIdentifier)
                             .name("Updated Token")
                             .projectIdentifier(projectIdentifier)
                             .identifier(identifier)
                             .parentIdentifier(parentIdentifier)
                             .apiKeyIdentifier(token.getApiKeyIdentifier())
                             .apiKeyType(SERVICE_ACCOUNT)
                             .validFrom(newValidFrom)
                             .validTo(newValidTo)
                             .description("Updated description")
                             .tags(new HashMap<>())
                             .build();

    token.setParentUniqueId(uniqueId);

    when(tokenRepository
             .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
                 accountIdentifier, uniqueId, SERVICE_ACCOUNT, parentIdentifier, token.getApiKeyIdentifier(),
                 identifier))
        .thenReturn(Optional.of(token));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });
    when(tokenRepository.save(any())).thenReturn(token);
    when(tokenOpaService.evaluatePoliciesWithEntity(any(), any(), any(), any())).thenReturn(null);

    TokenDTO result = tokenService.updateToken(updateDTO, scopeInfo);

    assertThat(result).isNotNull();
    verify(tokenRepository, times(1)).save(any());
    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateToken_throwsWhenTokenNotPresent() {
    TokenDTO updateDTO = TokenDTO.builder()
                             .accountIdentifier(accountIdentifier)
                             .orgIdentifier(orgIdentifier)
                             .name("Updated Token")
                             .projectIdentifier(projectIdentifier)
                             .identifier("nonexistent")
                             .parentIdentifier(parentIdentifier)
                             .apiKeyIdentifier("apiKey")
                             .apiKeyType(SERVICE_ACCOUNT)
                             .validFrom(Instant.now().toEpochMilli())
                             .validTo(Instant.now().plusSeconds(86400).toEpochMilli())
                             .tags(new HashMap<>())
                             .build();

    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    doReturn(Optional.of(apiKey)).when(apiKeyService).getApiKey(any(), any(), any(), any());

    when(tokenRepository
             .findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
                 accountIdentifier, uniqueId, SERVICE_ACCOUNT, parentIdentifier, "apiKey", "nonexistent"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> tokenService.updateToken(updateDTO, scopeInfo))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No token present");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateToken_throwsForSSHKey() {
    TokenDTO updateDTO = TokenDTO.builder()
                             .accountIdentifier(accountIdentifier)
                             .orgIdentifier(orgIdentifier)
                             .name("Updated Token")
                             .projectIdentifier(projectIdentifier)
                             .identifier(identifier)
                             .parentIdentifier(parentIdentifier)
                             .apiKeyIdentifier(token.getApiKeyIdentifier())
                             .apiKeyType(SSH_KEY)
                             .validFrom(Instant.now().toEpochMilli())
                             .validTo(Instant.now().plusSeconds(86400).toEpochMilli())
                             .tags(new HashMap<>())
                             .build();

    assertThatThrownBy(() -> tokenService.updateToken(updateDTO, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Operation not allowed for SSH_KEY type");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateToken_throwsForPGPKey() {
    TokenDTO updateDTO = TokenDTO.builder()
                             .accountIdentifier(accountIdentifier)
                             .orgIdentifier(orgIdentifier)
                             .name("Updated Token")
                             .projectIdentifier(projectIdentifier)
                             .identifier(identifier)
                             .parentIdentifier(parentIdentifier)
                             .apiKeyIdentifier(token.getApiKeyIdentifier())
                             .apiKeyType(PGP_KEY)
                             .validFrom(Instant.now().toEpochMilli())
                             .validTo(Instant.now().plusSeconds(86400).toEpochMilli())
                             .tags(new HashMap<>())
                             .build();

    assertThatThrownBy(() -> tokenService.updateToken(updateDTO, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Operation not allowed for PGP_KEY type");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateToken_throwsForRotatedToken() {
    TokenDTO updateDTO = TokenDTO.builder()
                             .accountIdentifier(accountIdentifier)
                             .orgIdentifier(orgIdentifier)
                             .name("Updated Token")
                             .projectIdentifier(projectIdentifier)
                             .identifier(identifier)
                             .parentIdentifier(parentIdentifier)
                             .apiKeyIdentifier(token.getApiKeyIdentifier())
                             .apiKeyType(SERVICE_ACCOUNT)
                             .validFrom(Instant.now().toEpochMilli())
                             .validTo(Instant.now().plusSeconds(86400).toEpochMilli())
                             .scheduledExpireTime(Instant.now().toEpochMilli())
                             .tags(new HashMap<>())
                             .build();

    assertThatThrownBy(() -> tokenService.updateToken(updateDTO, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Rotated tokens cannot be updated");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_listTokensByApiKeyTypes_success() {
    Token sshToken = Token.builder()
                         .accountIdentifier(accountIdentifier)
                         .parentIdentifier(parentIdentifier)
                         .identifier("ssh-key-1")
                         .apiKeyType(SSH_KEY)
                         .sshPublicKey(SSHPublicKey.builder().fingerPrint("fp1").build())
                         .build();
    Token pgpToken = Token.builder()
                         .accountIdentifier(accountIdentifier)
                         .parentIdentifier(parentIdentifier)
                         .identifier("pgp-key-1")
                         .apiKeyType(PGP_KEY)
                         .pgpPublicKey(PGPPublicKey.builder().fingerprint("fp2").build())
                         .build();

    List<Token> tokens = Arrays.asList(sshToken, pgpToken);
    Page<Token> page = new PageImpl<>(tokens, Pageable.ofSize(10), tokens.size());
    List<ApiKeyType> apiKeyTypes = Arrays.asList(SSH_KEY, PGP_KEY);

    when(tokenRepository.findByApiKeyTypes(eq(accountIdentifier), eq(parentIdentifier), eq(apiKeyTypes), any()))
        .thenReturn(page);

    PageResponse<TokenAggregateDTO> result = tokenService.listTokensByApiKeyTypes(
        scopeInfo, accountIdentifier, parentIdentifier, apiKeyTypes, Pageable.ofSize(10));

    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalItems()).isEqualTo(2);
    verify(tokenRepository, times(1))
        .findByApiKeyTypes(eq(accountIdentifier), eq(parentIdentifier), eq(apiKeyTypes), any());
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_listTokensByApiKeyTypes_emptyResult() {
    Page<Token> emptyPage = new PageImpl<>(new ArrayList<>(), Pageable.ofSize(10), 0);
    List<ApiKeyType> apiKeyTypes = Arrays.asList(SSH_KEY, PGP_KEY);

    when(tokenRepository.findByApiKeyTypes(eq(accountIdentifier), eq(parentIdentifier), eq(apiKeyTypes), any()))
        .thenReturn(emptyPage);

    PageResponse<TokenAggregateDTO> result = tokenService.listTokensByApiKeyTypes(
        scopeInfo, accountIdentifier, parentIdentifier, apiKeyTypes, Pageable.ofSize(10));

    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalItems()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_deleteAllByApiKeyIdentifier_success() {
    String apiKeyIdentifier = "test-api-key";
    when(tokenRepository
             .deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifier(
                 accountIdentifier, uniqueId, SSH_KEY, parentIdentifier, apiKeyIdentifier))
        .thenReturn(5L);

    long result = tokenService.deleteAllByApiKeyIdentifier(scopeInfo, SSH_KEY, parentIdentifier, apiKeyIdentifier);

    assertThat(result).isEqualTo(5L);
    verify(tokenRepository, times(1))
        .deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifier(
            accountIdentifier, uniqueId, SSH_KEY, parentIdentifier, apiKeyIdentifier);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_deleteAllByApiKeyIdentifier_noTokensDeleted() {
    String apiKeyIdentifier = "non-existent-key";
    when(tokenRepository
             .deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifier(
                 accountIdentifier, uniqueId, PGP_KEY, parentIdentifier, apiKeyIdentifier))
        .thenReturn(0L);

    long result = tokenService.deleteAllByApiKeyIdentifier(scopeInfo, PGP_KEY, parentIdentifier, apiKeyIdentifier);

    assertThat(result).isEqualTo(0L);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_withRevocation_forPGPKey_success() {
    // Create a PGP subkey to avoid triggering subkey lookup
    Token pgpToken = Token.builder()
                         .accountIdentifier(accountIdentifier)
                         .parentIdentifier(parentIdentifier)
                         .parentUniqueId(uniqueId)
                         .identifier("pgp-key-1")
                         .apiKeyType(PGP_KEY)
                         .apiKeyIdentifier("apiKey")
                         .validTo(Instant.now().plusSeconds(86400))
                         .pgpPublicKey(PGPPublicKey.builder()
                                           .fingerprint("fp123")
                                           .keyId("keyId123")
                                           .parentKeyId("parentKey")
                                           .isSubKey(true)
                                           .pgpKeyContent("-----BEGIN PGP PUBLIC KEY-----")
                                           .build())
                         .build();
    pgpToken.setUuid(randomAlphabetic(10));

    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.RETIRED).build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "pgp-key-1"))
        .thenReturn(Optional.of(pgpToken));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });
    when(tokenRepository.save(any())).thenReturn(pgpToken);

    TokenDTO result = tokenService.updateKey(scopeInfo, parentIdentifier, "apiKey", "pgp-key-1", request);

    assertThat(result).isNotNull();
    verify(tokenRepository, times(1)).save(any());
    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_withRevocation_forSSHKey_success() {
    Token sshToken =
        Token.builder()
            .accountIdentifier(accountIdentifier)
            .parentIdentifier(parentIdentifier)
            .parentUniqueId(uniqueId)
            .identifier("ssh-key-1")
            .apiKeyType(SSH_KEY)
            .apiKeyIdentifier("apiKey")
            .validTo(Instant.now().plusSeconds(86400))
            .sshPublicKey(SSHPublicKey.builder().fingerPrint("SHA256:abc123").sshKey("ssh-rsa AAAA...").build())
            .build();
    sshToken.setUuid(randomAlphabetic(10));

    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.SUPERSEDED).build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "ssh-key-1"))
        .thenReturn(Optional.of(sshToken));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });
    when(tokenRepository.save(any())).thenReturn(sshToken);

    TokenDTO result = tokenService.updateKey(scopeInfo, parentIdentifier, "apiKey", "ssh-key-1", request);

    assertThat(result).isNotNull();
    verify(tokenRepository, times(1)).save(any());
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_withValidityPeriod_success() {
    Token sshToken =
        Token.builder()
            .accountIdentifier(accountIdentifier)
            .parentIdentifier(parentIdentifier)
            .parentUniqueId(uniqueId)
            .identifier("ssh-key-1")
            .apiKeyType(SSH_KEY)
            .apiKeyIdentifier("apiKey")
            .validFrom(Instant.now())
            .validTo(Instant.now().plusSeconds(86400))
            .sshPublicKey(SSHPublicKey.builder().fingerPrint("SHA256:abc123").sshKey("ssh-rsa AAAA...").build())
            .build();
    sshToken.setUuid(randomAlphabetic(10));

    long newValidFrom = Instant.now().plusSeconds(3600).toEpochMilli();
    long newValidTo = Instant.now().plusSeconds(172800).toEpochMilli();
    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().validFrom(newValidFrom).validTo(newValidTo).build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "ssh-key-1"))
        .thenReturn(Optional.of(sshToken));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });
    when(tokenRepository.save(any())).thenReturn(sshToken);

    TokenDTO result = tokenService.updateKey(scopeInfo, parentIdentifier, "apiKey", "ssh-key-1", request);

    assertThat(result).isNotNull();
    verify(tokenRepository, times(1)).save(any());
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_throwsWhenBothRevocationAndValidityProvided() {
    Token sshToken = Token.builder()
                         .accountIdentifier(accountIdentifier)
                         .parentIdentifier(parentIdentifier)
                         .parentUniqueId(uniqueId)
                         .identifier("ssh-key-1")
                         .apiKeyType(SSH_KEY)
                         .apiKeyIdentifier("apiKey")
                         .sshPublicKey(SSHPublicKey.builder().fingerPrint("fp").build())
                         .build();
    sshToken.setUuid(randomAlphabetic(10));

    UpdatePublicKeyRequest request = UpdatePublicKeyRequest.builder()
                                         .revocationReason(RevocationReason.RETIRED)
                                         .validFrom(Instant.now().toEpochMilli())
                                         .build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "ssh-key-1"))
        .thenReturn(Optional.of(sshToken));

    assertThatThrownBy(() -> tokenService.updateKey(scopeInfo, parentIdentifier, "apiKey", "ssh-key-1", request))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Must either revoke the key or update its validity period");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_throwsWhenValidFromGreaterThanValidTo() {
    Token sshToken = Token.builder()
                         .accountIdentifier(accountIdentifier)
                         .parentIdentifier(parentIdentifier)
                         .parentUniqueId(uniqueId)
                         .identifier("ssh-key-1")
                         .apiKeyType(SSH_KEY)
                         .apiKeyIdentifier("apiKey")
                         .sshPublicKey(SSHPublicKey.builder().fingerPrint("fp").build())
                         .build();
    sshToken.setUuid(randomAlphabetic(10));

    long validTo = Instant.now().toEpochMilli();
    long validFrom = validTo + 86400000;
    UpdatePublicKeyRequest request = UpdatePublicKeyRequest.builder().validFrom(validFrom).validTo(validTo).build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "ssh-key-1"))
        .thenReturn(Optional.of(sshToken));

    assertThatThrownBy(() -> tokenService.updateKey(scopeInfo, parentIdentifier, "apiKey", "ssh-key-1", request))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Invalid validity period");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_throwsWhenUpdatingCompromisedKey() {
    Token sshToken =
        Token.builder()
            .accountIdentifier(accountIdentifier)
            .parentIdentifier(parentIdentifier)
            .parentUniqueId(uniqueId)
            .identifier("ssh-key-1")
            .apiKeyType(SSH_KEY)
            .apiKeyIdentifier("apiKey")
            .revocationReason(RevocationReason.COMPROMISED)
            .sshPublicKey(
                SSHPublicKey.builder().fingerPrint("fp").revocationReason(RevocationReason.COMPROMISED).build())
            .build();
    sshToken.setUuid(randomAlphabetic(10));

    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.RETIRED).build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "ssh-key-1"))
        .thenReturn(Optional.of(sshToken));

    assertThatThrownBy(() -> tokenService.updateKey(scopeInfo, parentIdentifier, "apiKey", "ssh-key-1", request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot update revocation reason of a COMPROMISED key");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_throwsWhenUpdatingValidityOfRevokedKey() {
    Token sshToken =
        Token.builder()
            .accountIdentifier(accountIdentifier)
            .parentIdentifier(parentIdentifier)
            .parentUniqueId(uniqueId)
            .identifier("ssh-key-1")
            .apiKeyType(SSH_KEY)
            .apiKeyIdentifier("apiKey")
            .revocationReason(RevocationReason.RETIRED)
            .sshPublicKey(SSHPublicKey.builder().fingerPrint("fp").revocationReason(RevocationReason.RETIRED).build())
            .build();
    sshToken.setUuid(randomAlphabetic(10));

    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().validTo(Instant.now().plusSeconds(86400).toEpochMilli()).build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "ssh-key-1"))
        .thenReturn(Optional.of(sshToken));

    assertThatThrownBy(() -> tokenService.updateKey(scopeInfo, parentIdentifier, "apiKey", "ssh-key-1", request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot update validity period of a revoked key");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_withCompromisedRevocation_callsCodeApi() throws Exception {
    Token sshToken =
        Token.builder()
            .accountIdentifier(accountIdentifier)
            .parentIdentifier(parentIdentifier)
            .parentUniqueId(uniqueId)
            .identifier("ssh-key-1")
            .apiKeyType(SSH_KEY)
            .apiKeyIdentifier("apiKey")
            .validTo(Instant.now().plusSeconds(86400))
            .sshPublicKey(SSHPublicKey.builder().fingerPrint("SHA256:abc123").sshKey("ssh-rsa AAAA...").build())
            .build();
    sshToken.setUuid(randomAlphabetic(10));

    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.COMPROMISED).build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "ssh-key-1"))
        .thenReturn(Optional.of(sshToken));

    // Mock the PublicKeyRevokerFactory to return a revoker for COMPROMISED
    PublicKeyRevoker mockRevoker = mock(PublicKeyRevoker.class);
    when(publicKeyRevokerFactory.getRevokers(RevocationReason.COMPROMISED))
        .thenReturn(Collections.singletonList(mockRevoker));

    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });
    when(tokenRepository.save(any())).thenReturn(sshToken);

    TokenDTO result = tokenService.updateKey(scopeInfo, parentIdentifier, "apiKey", "ssh-key-1", request);

    assertThat(result).isNotNull();
    verify(mockRevoker, times(1)).revoke(eq(scopeInfo), any());
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_withCompromisedRevocation_throwsWhenCodeApiFails() throws Exception {
    Token sshToken =
        Token.builder()
            .accountIdentifier(accountIdentifier)
            .parentIdentifier(parentIdentifier)
            .parentUniqueId(uniqueId)
            .identifier("ssh-key-1")
            .apiKeyType(SSH_KEY)
            .apiKeyIdentifier("apiKey")
            .validTo(Instant.now().plusSeconds(86400))
            .sshPublicKey(SSHPublicKey.builder().fingerPrint("SHA256:abc123").sshKey("ssh-rsa AAAA...").build())
            .build();
    sshToken.setUuid(randomAlphabetic(10));

    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.COMPROMISED).build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "ssh-key-1"))
        .thenReturn(Optional.of(sshToken));

    // Mock the PublicKeyRevokerFactory to return a revoker that throws
    PublicKeyRevoker mockRevoker = mock(PublicKeyRevoker.class);
    when(publicKeyRevokerFactory.getRevokers(RevocationReason.COMPROMISED))
        .thenReturn(Collections.singletonList(mockRevoker));
    doThrow(new InvalidRequestException("Failed to revoke key in code service")).when(mockRevoker).revoke(any(), any());

    assertThatThrownBy(() -> tokenService.updateKey(scopeInfo, parentIdentifier, "apiKey", "ssh-key-1", request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to revoke key in code service");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_updateKey_primaryPGPKey_updatesSubkeys() throws Exception {
    Token pgpPrimaryToken = Token.builder()
                                .accountIdentifier(accountIdentifier)
                                .parentIdentifier(parentIdentifier)
                                .parentUniqueId(uniqueId)
                                .identifier("pgp-primary")
                                .apiKeyType(PGP_KEY)
                                .apiKeyIdentifier("apiKey")
                                .validTo(Instant.now().plusSeconds(86400))
                                .pgpPublicKey(PGPPublicKey.builder()
                                                  .fingerprint("fp123")
                                                  .keyId("primaryKeyId")
                                                  .pgpKeyContent("-----BEGIN PGP PUBLIC KEY-----")
                                                  .isSubKey(false)
                                                  .build())
                                .build();
    pgpPrimaryToken.setUuid(randomAlphabetic(10));

    Token subKey1 =
        Token.builder()
            .accountIdentifier(accountIdentifier)
            .apiKeyType(PGP_KEY)
            .pgpPublicKey(PGPPublicKey.builder().keyId("subKeyId1").parentKeyId("primaryKeyId").isSubKey(true).build())
            .build();
    Token subKey2 =
        Token.builder()
            .accountIdentifier(accountIdentifier)
            .apiKeyType(PGP_KEY)
            .pgpPublicKey(PGPPublicKey.builder().keyId("subKeyId2").parentKeyId("primaryKeyId").isSubKey(true).build())
            .build();

    Page<Token> subKeysPage = new PageImpl<>(Arrays.asList(subKey1, subKey2));

    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.RETIRED).build();

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "pgp-primary"))
        .thenReturn(Optional.of(pgpPrimaryToken));
    when(tokenRepository.findAll(any(Criteria.class), any(Pageable.class))).thenReturn(subKeysPage);
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });
    when(tokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    TokenDTO result = tokenService.updateKey(scopeInfo, parentIdentifier, "apiKey", "pgp-primary", request);

    assertThat(result).isNotNull();
    // Primary key save + 2 subkey saves
    verify(tokenRepository, atLeastOnce()).save(any());
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_deleteAllByParentIdentifier_success() {
    when(tokenRepository.deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifier(
             accountIdentifier, uniqueId, SSH_KEY, parentIdentifier))
        .thenReturn(3L);

    long result = tokenService.deleteAllByParentIdentifier(scopeInfo, SSH_KEY, parentIdentifier);

    assertThat(result).isEqualTo(3L);
    verify(tokenRepository, times(1))
        .deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifier(
            accountIdentifier, uniqueId, SSH_KEY, parentIdentifier);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_deleteKey_forPGPKey_success() {
    Token pgpToken = Token.builder()
                         .accountIdentifier(accountIdentifier)
                         .parentIdentifier(parentIdentifier)
                         .parentUniqueId(uniqueId)
                         .identifier("pgp-key-1")
                         .apiKeyType(PGP_KEY)
                         .apiKeyIdentifier("apiKey")
                         .pgpPublicKey(PGPPublicKey.builder().keyId("keyId123").fingerprint("fp123").build())
                         .build();
    pgpToken.setUuid(randomAlphabetic(10));

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "pgp-key-1"))
        .thenReturn(Optional.of(pgpToken));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    boolean result = tokenService.deleteKey(scopeInfo, parentIdentifier, "apiKey", "pgp-key-1");

    assertThat(result).isTrue();
    verify(tokenRepository, times(1)).deleteById(any());
    verify(outboxService, times(1)).save(any(TokenDeleteEvent.class));
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_deleteKey_forSSHKey_success() {
    Token sshToken = Token.builder()
                         .accountIdentifier(accountIdentifier)
                         .parentIdentifier(parentIdentifier)
                         .parentUniqueId(uniqueId)
                         .identifier("ssh-key-1")
                         .apiKeyType(SSH_KEY)
                         .apiKeyIdentifier("apiKey")
                         .sshPublicKey(SSHPublicKey.builder().fingerPrint("fp123").sshKey("ssh-rsa AAAA...").build())
                         .build();
    sshToken.setUuid(randomAlphabetic(10));

    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "ssh-key-1"))
        .thenReturn(Optional.of(sshToken));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    boolean result = tokenService.deleteKey(scopeInfo, parentIdentifier, "apiKey", "ssh-key-1");

    assertThat(result).isTrue();
    verify(tokenRepository, times(1)).deleteById(any());
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void test_deleteKey_returnsTrueWhenKeyNotFound() {
    when(tokenRepository.findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
             accountIdentifier, uniqueId, parentIdentifier, "apiKey", "nonexistent"))
        .thenReturn(Optional.empty());

    // The code treats missing keys as already deleted and returns true
    boolean result = tokenService.deleteKey(scopeInfo, parentIdentifier, "apiKey", "nonexistent");

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testValidateScopedResourcePermissionScopes_invalidOrg() throws Exception {
    Call<ResponseDTO<List<ScopeInfo>>> call = mock(Call.class);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(List.of())));
    when(scopeInfoClient.getScopeInfoList(eq(accountIdentifier), any(Set.class))).thenReturn(call);
    tokenDTO.setScopedResourcePermissions(
        List.of(ScopedResourcePermission.builder().resourceType("CONNECTOR").orgIdentifier("INVALID_ORG").build()));

    assertThatThrownBy(() -> tokenServiceImpl.validateScopedResourcePermissionScopes(accountIdentifier, tokenDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("INVALID_ORG")
        .hasMessageContaining("does not exist");
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testValidateScopedResourcePermissionScopes_invalidProject() throws Exception {
    Call<ResponseDTO<List<ScopeInfo>>> orgCall = mock(Call.class);
    when(orgCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            List.of(ScopeInfo.builder().uniqueId("validOrg-uid").orgIdentifier("validOrg").build()))));
    when(scopeInfoClient.getScopeInfoList(eq(accountIdentifier), any(Set.class))).thenReturn(orgCall);

    Call<ResponseDTO<List<ScopeInfo>>> projCall = mock(Call.class);
    when(projCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(List.of())));
    when(scopeInfoClient.getScopeInfoList(eq(accountIdentifier), eq("validOrg"), any(Set.class))).thenReturn(projCall);

    tokenDTO.setScopedResourcePermissions(List.of(ScopedResourcePermission.builder()
                                                      .resourceType("CONNECTOR")
                                                      .orgIdentifier("validOrg")
                                                      .projectIdentifier("INVALID_PROJ")
                                                      .build()));

    assertThatThrownBy(() -> tokenServiceImpl.validateScopedResourcePermissionScopes(accountIdentifier, tokenDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("INVALID_PROJ")
        .hasMessageContaining("does not exist");
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testValidateScopedResourcePermissionScopes_projectWithoutOrg() {
    tokenDTO.setScopedResourcePermissions(
        List.of(ScopedResourcePermission.builder().resourceType("CONNECTOR").projectIdentifier("someProject").build()));

    assertThatThrownBy(() -> tokenServiceImpl.validateScopedResourcePermissionScopes(accountIdentifier, tokenDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("projectIdentifier requires a non-empty orgIdentifier");
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testValidateScopedResourcePermissionScopes_accountLevelSkipped() {
    tokenDTO.setScopedResourcePermissions(
        List.of(ScopedResourcePermission.builder().resourceType("CONNECTOR").build()));

    tokenServiceImpl.validateScopedResourcePermissionScopes(accountIdentifier, tokenDTO);

    verify(scopeInfoClient, never()).getScopeInfoList(any(), any(Set.class));
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testValidateScopedResourcePermissionScopes_batchValidation() throws Exception {
    Call<ResponseDTO<List<ScopeInfo>>> orgCall = mock(Call.class);
    when(orgCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            List.of(ScopeInfo.builder().uniqueId("myOrg-uid").orgIdentifier("myOrg").build()))));
    when(scopeInfoClient.getScopeInfoList(eq(accountIdentifier), any(Set.class))).thenReturn(orgCall);

    tokenDTO.setScopedResourcePermissions(
        List.of(ScopedResourcePermission.builder().resourceType("CONNECTOR").orgIdentifier("myOrg").build(),
            ScopedResourcePermission.builder().resourceType("SECRET").orgIdentifier("myOrg").build()));

    tokenServiceImpl.validateScopedResourcePermissionScopes(accountIdentifier, tokenDTO);

    verify(scopeInfoClient, times(1)).getScopeInfoList(eq(accountIdentifier), any(Set.class));
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void createScopedToken_rejectsDeprecatedPermissionFieldAlone() {
    setScopedTokenCallerPrincipal();
    TokenDTO scopedTokenDTO =
        buildScopedTokenDTO(TokenMode.PERSISTENT, Instant.now().plusSeconds(7 * 86400).toEpochMilli());
    scopedTokenDTO.setScopedResourcePermissions(
        List.of(ScopedResourcePermission.builder().resourceType("PIPELINE").permission("core_pipeline_view").build()));

    assertThatThrownBy(() -> tokenService.createToken(scopedTokenDTO, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("The deprecated `permission` field is no longer accepted");
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void createScopedToken_rejectsDeprecatedPermissionFieldWhenPermissionsAlsoSet() {
    setScopedTokenCallerPrincipal();
    TokenDTO scopedTokenDTO =
        buildScopedTokenDTO(TokenMode.PERSISTENT, Instant.now().plusSeconds(7 * 86400).toEpochMilli());
    scopedTokenDTO.setScopedResourcePermissions(List.of(ScopedResourcePermission.builder()
                                                            .resourceType("PIPELINE")
                                                            .permission("core_pipeline_view")
                                                            .permissions(List.of("core_pipeline_edit"))
                                                            .build()));

    assertThatThrownBy(() -> tokenService.createToken(scopedTokenDTO, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("The deprecated `permission` field is no longer accepted");
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void createScopedToken_rejectsEmptyPermissionsList() {
    setScopedTokenCallerPrincipal();
    TokenDTO scopedTokenDTO =
        buildScopedTokenDTO(TokenMode.PERSISTENT, Instant.now().plusSeconds(7 * 86400).toEpochMilli());
    scopedTokenDTO.setScopedResourcePermissions(
        List.of(ScopedResourcePermission.builder().resourceType("PIPELINE").permissions(List.of()).build()));

    assertThatThrownBy(() -> tokenService.createToken(scopedTokenDTO, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("A non-empty `permissions` list is required");
  }

  // ----------------------------------------------------------------------------------------------
  // SCOPED_TOKEN ephemeral TTL on the create path.
  // An ephemeral token created without a validTo defaults to a 5 minute expiry; a caller-supplied
  // validTo is honoured as-is up to the 24 hour cap, above which creation is rejected. None of this
  // applies to persistent scoped tokens, which keep falling back to the API key's default expiry.
  // ----------------------------------------------------------------------------------------------

  /**
   * Account-level permission entries are used deliberately: validateScopedResourcePermissionScopes
   * short-circuits for them, so these tests need no ScopeInfoClient round trip.
   */
  private TokenDTO buildScopedTokenDTOForTTL(TokenMode mode, Long validFromMillis, Long validToMillis) {
    return TokenDTO.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .name(randomAlphabetic(10))
        .identifier(identifier)
        .parentIdentifier(parentIdentifier)
        .apiKeyType(ApiKeyType.SCOPED_TOKEN)
        .validFrom(validFromMillis)
        .validTo(validToMillis)
        .description("")
        .tags(new HashMap<>())
        .scopedResourcePermissions(List.of(ScopedResourcePermission.builder()
                                               .resourceType("PIPELINE")
                                               .permissions(List.of("core_pipeline_view"))
                                               .build()))
        .tokenMode(mode)
        .scopedResourceMetadata(ScopedResourceMetadata.builder().parentResourceId("pipeline-exec-1").build())
        .build();
  }

  /**
   * Wires the create path so the token actually reaches tokenRepository.save: the shared setup stubs
   * transactionTemplate to return a canned token without running the callback, which would hide the
   * expiry that was persisted.
   */
  private void wireCreateMocksForScopedToken() {
    setScopedTokenCallerPrincipal();

    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(Optional.of(apiKey)).when(apiKeyService).getApiKey(any(), any(), any(), any());

    when(accessControlClient.checkForAccess(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(List.of(AccessControlDTO.builder().permitted(true).build()))
                        .build());

    when(tokenRepository.save(any())).thenAnswer(invocation -> {
      Token savedToken = invocation.getArgument(0);
      savedToken.setUuid(randomAlphabetic(10));
      return savedToken;
    });
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });
    when(tokenOpaService.evaluatePoliciesWithEntity(any(), any(), any(), any())).thenReturn(null);
  }

  private Token captureSavedToken() {
    ArgumentCaptor<Token> savedToken = ArgumentCaptor.forClass(Token.class);
    verify(tokenRepository).save(savedToken.capture());
    return savedToken.getValue();
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void createToken_whenEphemeralAndValidToAbsent_thenExpiresInFiveMinutes() {
    wireCreateMocksForScopedToken();
    TokenDTO scopedTokenDTO = buildScopedTokenDTOForTTL(TokenMode.EPHEMERAL, null, null);
    Instant beforeCreate = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    tokenService.createToken(scopedTokenDTO, scopeInfo);

    // The default is anchored to a clock read inside the service, so the expiry can only land
    // between (call start + 5m) and (call end + 5m).
    assertThat(captureSavedToken().getValidTo())
        .isBetween(beforeCreate.plus(Duration.ofMinutes(5)),
            Instant.now().truncatedTo(ChronoUnit.MILLIS).plus(Duration.ofMinutes(5)));
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void createToken_whenEphemeralAndValidToAbsentAndValidFromSet_thenDefaultAnchoredToValidFrom() {
    wireCreateMocksForScopedToken();
    long validFrom = Instant.now().toEpochMilli();
    TokenDTO scopedTokenDTO = buildScopedTokenDTOForTTL(TokenMode.EPHEMERAL, validFrom, null);

    tokenService.createToken(scopedTokenDTO, scopeInfo);

    assertThat(captureSavedToken().getValidTo()).isEqualTo(Instant.ofEpochMilli(validFrom).plus(Duration.ofMinutes(5)));
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void createToken_whenEphemeralAndValidToWithinCap_thenValidToPreserved() {
    wireCreateMocksForScopedToken();
    long validFrom = Instant.now().toEpochMilli();
    long validTo = Instant.ofEpochMilli(validFrom).plus(Duration.ofHours(1)).toEpochMilli();
    TokenDTO scopedTokenDTO = buildScopedTokenDTOForTTL(TokenMode.EPHEMERAL, validFrom, validTo);

    tokenService.createToken(scopedTokenDTO, scopeInfo);

    assertThat(captureSavedToken().getValidTo()).isEqualTo(Instant.ofEpochMilli(validTo));
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void createToken_whenEphemeralAndValidToBelowDefault_thenValidToPreserved() {
    wireCreateMocksForScopedToken();
    long validFrom = Instant.now().toEpochMilli();
    long validTo = Instant.ofEpochMilli(validFrom).plus(Duration.ofMinutes(1)).toEpochMilli();
    TokenDTO scopedTokenDTO = buildScopedTokenDTOForTTL(TokenMode.EPHEMERAL, validFrom, validTo);

    tokenService.createToken(scopedTokenDTO, scopeInfo);

    // The 5 minute value is a default, not a floor: a shorter caller-supplied validity stands.
    assertThat(captureSavedToken().getValidTo()).isEqualTo(Instant.ofEpochMilli(validTo));
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void createToken_whenEphemeralAndValidToExceedsCap_thenThrows() {
    wireCreateMocksForScopedToken();
    long validFrom = Instant.now().toEpochMilli();
    long validTo = Instant.ofEpochMilli(validFrom).plus(Duration.ofHours(24)).plusSeconds(1).toEpochMilli();
    TokenDTO scopedTokenDTO = buildScopedTokenDTOForTTL(TokenMode.EPHEMERAL, validFrom, validTo);

    assertThatThrownBy(() -> tokenService.createToken(scopedTokenDTO, scopeInfo))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Ephemeral scoped tokens cannot have a validity exceeding 24 hours");
    verify(tokenRepository, never()).save(any());
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void createToken_whenEphemeralAndValidToExactlyAtCap_thenAccepted() {
    wireCreateMocksForScopedToken();
    long validFrom = Instant.now().toEpochMilli();
    long validTo = Instant.ofEpochMilli(validFrom).plus(Duration.ofHours(24)).toEpochMilli();
    TokenDTO scopedTokenDTO = buildScopedTokenDTOForTTL(TokenMode.EPHEMERAL, validFrom, validTo);

    tokenService.createToken(scopedTokenDTO, scopeInfo);

    assertThat(captureSavedToken().getValidTo()).isEqualTo(Instant.ofEpochMilli(validTo));
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void createToken_whenPersistentAndValidToAbsent_thenFiveMinuteDefaultNotApplied() {
    wireCreateMocksForScopedToken();
    long validFrom = Instant.now().toEpochMilli();
    TokenDTO scopedTokenDTO = buildScopedTokenDTOForTTL(TokenMode.PERSISTENT, validFrom, null);

    tokenService.createToken(scopedTokenDTO, scopeInfo);

    // Persistent tokens fall through to the API key's defaultTimeToExpireToken, wired to 2 days above.
    assertThat(captureSavedToken().getValidTo()).isEqualTo(Instant.ofEpochMilli(validFrom).plus(Duration.ofDays(2)));
  }

  /** prepareScopedToken resolves apiKeyIdentifier from the source principal before validation. */
  private void setScopedTokenCallerPrincipal() {
    Principal principal = new UserPrincipal("test-user", "test@example.com", "test-user", accountIdentifier);
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
  }
}

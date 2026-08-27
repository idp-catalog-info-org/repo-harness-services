/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.smp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.base.NgManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.config.SmpConfig;
import io.harness.ng.smp.dto.PublicKeyDTO;
import io.harness.ng.smp.entities.SMPAuthInfo;
import io.harness.repositories.ng.smp.SMPAuthInfoRepository;
import io.harness.rsa.RSAKeyPairPEM;
import io.harness.rsa.RSAKeysUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.JWTTokenServiceUtils;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;

import com.auth0.jwt.interfaces.Claim;
import com.mongodb.DuplicateKeyException;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class SMPAuthServiceImplTest extends NgManagerTestBase {
  private static final String ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String SMP_ACCOUNT_IDENTIFIER = "smp-account-id";
  private static final String PUBLIC_KEY =
      "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\n-----END PUBLIC KEY-----";
  private static final String PRIVATE_KEY =
      "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...\n-----END PRIVATE KEY-----";
  private static final String NEW_PUBLIC_KEY =
      "-----BEGIN PUBLIC KEY-----\nNEW_KEY_CONTENT...\n-----END PUBLIC KEY-----";
  private static final String NEW_PRIVATE_KEY =
      "-----BEGIN PRIVATE KEY-----\nNEW_PRIVATE_KEY_CONTENT...\n-----END PRIVATE KEY-----";
  private static final String USER_EMAIL = "test@harness.io";
  private static final String JWT_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...";
  private static final String BASE_URL = "https://smp.harness.io";
  private static final String ZENDESK_SSO_PATH = "ng/api/smp/auth/zendesk/sso";
  private static final String RETURN_TO_URL = "https://harness.io";

  @Mock private SMPAuthInfoRepository smpAuthInfoRepository;
  @Mock private RSAKeysUtils rsaKeysUtils;
  @Mock private NextGenConfiguration nextGenConfiguration;
  @Mock private SmpConfig smpConfig;

  @InjectMocks private SMPAuthServiceImpl smpAuthService;

  private SMPAuthInfo smpAuthInfo;
  private RSAKeyPairPEM rsaKeyPairPEM;
  private PublicKeyDTO publicKeyDTO;
  private UserPrincipal userPrincipal;

  @Before
  public void setUp() {
    smpAuthInfo = SMPAuthInfo.builder()
                      .accountIdentifier(ACCOUNT_IDENTIFIER)
                      .smpAccountIdentifier(SMP_ACCOUNT_IDENTIFIER)
                      .publicKey(PUBLIC_KEY)
                      .privateKey(PRIVATE_KEY)
                      .build();

    rsaKeyPairPEM = RSAKeyPairPEM.builder().publicKeyPem(NEW_PUBLIC_KEY).privateKeyPem(NEW_PRIVATE_KEY).build();

    publicKeyDTO = PublicKeyDTO.builder().accountIdentifier(SMP_ACCOUNT_IDENTIFIER).publicKey(PUBLIC_KEY).build();

    userPrincipal = new UserPrincipal("userId", USER_EMAIL, "userName", ACCOUNT_IDENTIFIER);

    when(nextGenConfiguration.getSmpConfig()).thenReturn(smpConfig);
    when(smpConfig.getSaasBaseUrl()).thenReturn(BASE_URL);
  }

  // ===== 1. getPublicKey() Tests =====

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testGetPublicKey_Success() {
    // Test existing key scenario
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);

    String result = smpAuthService.getPublicKey(ACCOUNT_IDENTIFIER);

    assertThat(result).isEqualTo(PUBLIC_KEY);
    verify(smpAuthInfoRepository).findByAccountIdentifier(ACCOUNT_IDENTIFIER);

    // Test null scenario
    when(smpAuthInfoRepository.findByAccountIdentifier("non-existent")).thenReturn(null);

    String nullResult = smpAuthService.getPublicKey("non-existent");

    assertThat(nullResult).isNull();
    verify(smpAuthInfoRepository).findByAccountIdentifier("non-existent");
  }

  // ===== 2. generateKeyPair() Tests =====

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testGenerateKeyPair_Success() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(null);
    when(rsaKeysUtils.generateKeyPairPEM()).thenReturn(rsaKeyPairPEM);

    PublicKeyDTO result = smpAuthService.generateKeyPair(ACCOUNT_IDENTIFIER);

    assertThat(result).isNotNull();
    assertThat(result.getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(result.getPublicKey()).isEqualTo(NEW_PUBLIC_KEY);

    verify(smpAuthInfoRepository).findByAccountIdentifier(ACCOUNT_IDENTIFIER);
    verify(rsaKeysUtils).generateKeyPairPEM();
    verify(smpAuthInfoRepository).save(any(SMPAuthInfo.class));
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testGenerateKeyPair_ExistingKey_ThrowsDuplicateFieldException() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);

    assertThatThrownBy(() -> smpAuthService.generateKeyPair(ACCOUNT_IDENTIFIER))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessageContaining("Key pair already exists for account identifier");

    verify(smpAuthInfoRepository).findByAccountIdentifier(ACCOUNT_IDENTIFIER);
    verify(rsaKeysUtils, never()).generateKeyPairPEM();
    verify(smpAuthInfoRepository, never()).save(any(SMPAuthInfo.class));
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testGenerateKeyPair_SaveFailure_ThrowsDuplicateFieldException() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(null);
    when(rsaKeysUtils.generateKeyPairPEM()).thenReturn(rsaKeyPairPEM);

    BsonDocument errorDoc = new BsonDocument("errmsg", new BsonString("Duplicate key"));
    when(smpAuthInfoRepository.save(any(SMPAuthInfo.class))).thenThrow(new DuplicateKeyException(errorDoc, null, null));

    assertThatThrownBy(() -> smpAuthService.generateKeyPair(ACCOUNT_IDENTIFIER))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessageContaining("SMP auth info with account identifier");

    verify(smpAuthInfoRepository).save(any(SMPAuthInfo.class));
  }

  // ===== 3. rotateKeyPair() Tests =====

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testRotateKeyPair_Success() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);
    when(rsaKeysUtils.generateKeyPairPEM()).thenReturn(rsaKeyPairPEM);

    PublicKeyDTO result = smpAuthService.rotateKeyPair(ACCOUNT_IDENTIFIER);

    assertThat(result).isNotNull();
    assertThat(result.getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(result.getPublicKey()).isEqualTo(NEW_PUBLIC_KEY);
    assertThat(smpAuthInfo.getPublicKey()).isEqualTo(NEW_PUBLIC_KEY);
    assertThat(smpAuthInfo.getPrivateKey()).isEqualTo(NEW_PRIVATE_KEY);

    verify(smpAuthInfoRepository).findByAccountIdentifier(ACCOUNT_IDENTIFIER);
    verify(rsaKeysUtils).generateKeyPairPEM();
    verify(smpAuthInfoRepository).save(smpAuthInfo);
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testRotateKeyPair_NoExistingKey_ThrowsInvalidRequestException() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(null);

    assertThatThrownBy(() -> smpAuthService.rotateKeyPair(ACCOUNT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No key pair found for account identifier");

    verify(smpAuthInfoRepository).findByAccountIdentifier(ACCOUNT_IDENTIFIER);
    verify(rsaKeysUtils, never()).generateKeyPairPEM();
    verify(smpAuthInfoRepository, never()).save(any(SMPAuthInfo.class));
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testRotateKeyPair_SaveFailure_ThrowsInvalidRequestException() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);
    when(rsaKeysUtils.generateKeyPairPEM()).thenReturn(rsaKeyPairPEM);
    when(smpAuthInfoRepository.save(smpAuthInfo)).thenThrow(new RuntimeException("Save failed"));

    assertThatThrownBy(() -> smpAuthService.rotateKeyPair(ACCOUNT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to rotate key pair");

    verify(smpAuthInfoRepository).save(smpAuthInfo);
  }

  // ===== 4. createPublicKey() Tests =====

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testCreatePublicKey_Success() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(null);

    PublicKeyDTO result = smpAuthService.createPublicKey(ACCOUNT_IDENTIFIER, publicKeyDTO);

    assertThat(result).isNotNull();
    assertThat(result.getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(result.getPublicKey()).isEqualTo(PUBLIC_KEY);

    verify(smpAuthInfoRepository).findByAccountIdentifier(ACCOUNT_IDENTIFIER);
    verify(smpAuthInfoRepository).save(any(SMPAuthInfo.class));
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testCreatePublicKey_ExistingKey_ThrowsDuplicateFieldException() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);

    assertThatThrownBy(() -> smpAuthService.createPublicKey(ACCOUNT_IDENTIFIER, publicKeyDTO))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessageContaining("Public key already exists for account identifier");

    verify(smpAuthInfoRepository).findByAccountIdentifier(ACCOUNT_IDENTIFIER);
    verify(smpAuthInfoRepository, never()).save(any(SMPAuthInfo.class));
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testCreatePublicKey_SaveFailure_ThrowsDuplicateFieldException() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(null);

    BsonDocument errorDoc = new BsonDocument("errmsg", new BsonString("Duplicate"));
    when(smpAuthInfoRepository.save(any(SMPAuthInfo.class))).thenThrow(new DuplicateKeyException(errorDoc, null, null));

    assertThatThrownBy(() -> smpAuthService.createPublicKey(ACCOUNT_IDENTIFIER, publicKeyDTO))
        .isInstanceOf(DuplicateFieldException.class);

    verify(smpAuthInfoRepository).save(any(SMPAuthInfo.class));
  }

  // ===== 5. updatePublicKey() Tests =====

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testUpdatePublicKey_Success() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);

    PublicKeyDTO result = smpAuthService.updatePublicKey(ACCOUNT_IDENTIFIER, publicKeyDTO);

    assertThat(result).isNotNull();
    assertThat(result.getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(result.getPublicKey()).isEqualTo(PUBLIC_KEY);
    assertThat(smpAuthInfo.getPublicKey()).isEqualTo(PUBLIC_KEY);
    assertThat(smpAuthInfo.getSmpAccountIdentifier()).isEqualTo(SMP_ACCOUNT_IDENTIFIER);

    verify(smpAuthInfoRepository).findByAccountIdentifier(ACCOUNT_IDENTIFIER);
    verify(smpAuthInfoRepository).save(smpAuthInfo);
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testUpdatePublicKey_NoExistingKey_ThrowsInvalidRequestException() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(null);

    assertThatThrownBy(() -> smpAuthService.updatePublicKey(ACCOUNT_IDENTIFIER, publicKeyDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No public key found for account identifier");

    verify(smpAuthInfoRepository).findByAccountIdentifier(ACCOUNT_IDENTIFIER);
    verify(smpAuthInfoRepository, never()).save(any(SMPAuthInfo.class));
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testUpdatePublicKey_SaveFailure_ThrowsInvalidRequestException() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);
    when(smpAuthInfoRepository.save(smpAuthInfo)).thenThrow(new RuntimeException("Save failed"));

    assertThatThrownBy(() -> smpAuthService.updatePublicKey(ACCOUNT_IDENTIFIER, publicKeyDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to update public key");

    verify(smpAuthInfoRepository).save(smpAuthInfo);
  }

  // ===== 6. generateAuthToken() Tests =====

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testGenerateAuthToken_Success() {
    RSAPrivateKey mockPrivateKey = mock(RSAPrivateKey.class);

    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);
    when(rsaKeysUtils.readPemFile(PRIVATE_KEY)).thenReturn(mockPrivateKey);

    try (MockedStatic<SourcePrincipalContextBuilder> mockedContext =
             Mockito.mockStatic(SourcePrincipalContextBuilder.class);
         MockedStatic<JWTTokenServiceUtils> mockedJWT = Mockito.mockStatic(JWTTokenServiceUtils.class)) {
      mockedContext.when(SourcePrincipalContextBuilder::getSourcePrincipal).thenReturn(userPrincipal);
      mockedJWT.when(() -> JWTTokenServiceUtils.generateJWTToken(anyMap(), anyMap(), anyLong(), eq(mockPrivateKey)))
          .thenReturn(JWT_TOKEN);

      String result = smpAuthService.generateAuthToken(ACCOUNT_IDENTIFIER);

      assertThat(result).isEqualTo(JWT_TOKEN);
      verify(smpAuthInfoRepository).findByAccountIdentifier(ACCOUNT_IDENTIFIER);
      verify(rsaKeysUtils).readPemFile(PRIVATE_KEY);

      // Verify JWT generation was called with correct parameters
      mockedJWT.verify(()
                           -> JWTTokenServiceUtils.generateJWTToken(
                               anyMap(), anyMap(), eq(TimeUnit.MINUTES.toMillis(5)), eq(mockPrivateKey)));
    }
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testGenerateAuthToken_ValidationFailures() {
    // Test no SMPAuthInfo
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(null);

    assertThatThrownBy(() -> smpAuthService.generateAuthToken(ACCOUNT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Private key not found for account");

    // Test no private key
    SMPAuthInfo authInfoNoPrivateKey =
        SMPAuthInfo.builder().accountIdentifier(ACCOUNT_IDENTIFIER).publicKey(PUBLIC_KEY).privateKey(null).build();
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(authInfoNoPrivateKey);

    assertThatThrownBy(() -> smpAuthService.generateAuthToken(ACCOUNT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Private key not found for account");

    // Test empty private key
    SMPAuthInfo authInfoEmptyPrivateKey =
        SMPAuthInfo.builder().accountIdentifier(ACCOUNT_IDENTIFIER).publicKey(PUBLIC_KEY).privateKey("").build();
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(authInfoEmptyPrivateKey);

    assertThatThrownBy(() -> smpAuthService.generateAuthToken(ACCOUNT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Private key not found for account");
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testGenerateAuthToken_InvalidPrivateKey_ThrowsException() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);
    when(rsaKeysUtils.readPemFile(PRIVATE_KEY)).thenThrow(new RuntimeException("Invalid key format"));

    try (MockedStatic<SourcePrincipalContextBuilder> mockedContext =
             Mockito.mockStatic(SourcePrincipalContextBuilder.class)) {
      mockedContext.when(SourcePrincipalContextBuilder::getSourcePrincipal).thenReturn(userPrincipal);

      assertThatThrownBy(() -> smpAuthService.generateAuthToken(ACCOUNT_IDENTIFIER))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("Failed to generate auth token");
    }
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testGenerateAuthToken_JWTGenerationFailure_ThrowsException() {
    RSAPrivateKey mockPrivateKey = mock(RSAPrivateKey.class);

    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);
    when(rsaKeysUtils.readPemFile(PRIVATE_KEY)).thenReturn(mockPrivateKey);

    try (MockedStatic<SourcePrincipalContextBuilder> mockedContext =
             Mockito.mockStatic(SourcePrincipalContextBuilder.class);
         MockedStatic<JWTTokenServiceUtils> mockedJWT = Mockito.mockStatic(JWTTokenServiceUtils.class)) {
      mockedContext.when(SourcePrincipalContextBuilder::getSourcePrincipal).thenReturn(userPrincipal);
      mockedJWT.when(() -> JWTTokenServiceUtils.generateJWTToken(anyMap(), anyMap(), anyLong(), eq(mockPrivateKey)))
          .thenThrow(new RuntimeException("JWT generation failed"));

      assertThatThrownBy(() -> smpAuthService.generateAuthToken(ACCOUNT_IDENTIFIER))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("Failed to generate auth token");
    }
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testGenerateAuthToken_UserContextFailures() {
    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);

    try (MockedStatic<SourcePrincipalContextBuilder> mockedContext =
             Mockito.mockStatic(SourcePrincipalContextBuilder.class)) {
      // Test null source principal
      mockedContext.when(SourcePrincipalContextBuilder::getSourcePrincipal).thenReturn(null);

      assertThatThrownBy(() -> smpAuthService.generateAuthToken(ACCOUNT_IDENTIFIER))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("Only user accounts are allowed to create Zendesk tickets");

      // Test non-user principal type
      UserPrincipal nonUserPrincipal = mock(UserPrincipal.class);
      when(nonUserPrincipal.getType()).thenReturn(PrincipalType.SERVICE);
      mockedContext.when(SourcePrincipalContextBuilder::getSourcePrincipal).thenReturn(nonUserPrincipal);

      assertThatThrownBy(() -> smpAuthService.generateAuthToken(ACCOUNT_IDENTIFIER))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("Only user accounts are allowed to create Zendesk tickets");
    }
  }

  // ===== 7. verifyAuthToken() Tests =====

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testVerifyAuthToken_ValidToken_ReturnsTrue() {
    RSAKey mockPublicKey = mock(RSAKey.class);
    Map<String, Claim> mockClaims = new HashMap<>();
    Claim mockClaim = mock(Claim.class);
    when(mockClaim.asString()).thenReturn(SMP_ACCOUNT_IDENTIFIER);
    mockClaims.put("account_id", mockClaim);

    when(smpAuthInfoRepository.findBySmpAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);
    when(rsaKeysUtils.readPemFile(PUBLIC_KEY)).thenReturn(mockPublicKey);

    try (MockedStatic<JWTTokenServiceUtils> mockedJWT = Mockito.mockStatic(JWTTokenServiceUtils.class)) {
      mockedJWT.when(() -> JWTTokenServiceUtils.verifyJWTToken(JWT_TOKEN, mockPublicKey, "Harness SMP"))
          .thenReturn(mockClaims);

      boolean result = smpAuthService.verifyAuthToken(JWT_TOKEN, ACCOUNT_IDENTIFIER);

      assertThat(result).isTrue();
      verify(smpAuthInfoRepository).findBySmpAccountIdentifier(ACCOUNT_IDENTIFIER);
      verify(rsaKeysUtils).readPemFile(PUBLIC_KEY);
    }
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testVerifyAuthToken_ValidationFailures_ReturnsFalse() {
    // Test no SMPAuthInfo
    when(smpAuthInfoRepository.findBySmpAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(null);

    boolean result = smpAuthService.verifyAuthToken(JWT_TOKEN, ACCOUNT_IDENTIFIER);
    assertThat(result).isFalse();

    // Test no public key
    SMPAuthInfo authInfoNoPublicKey =
        SMPAuthInfo.builder().accountIdentifier(ACCOUNT_IDENTIFIER).publicKey(null).privateKey(PRIVATE_KEY).build();
    when(smpAuthInfoRepository.findBySmpAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(authInfoNoPublicKey);

    result = smpAuthService.verifyAuthToken(JWT_TOKEN, ACCOUNT_IDENTIFIER);
    assertThat(result).isFalse();

    // Test empty public key
    SMPAuthInfo authInfoEmptyPublicKey =
        SMPAuthInfo.builder().accountIdentifier(ACCOUNT_IDENTIFIER).publicKey("").privateKey(PRIVATE_KEY).build();
    when(smpAuthInfoRepository.findBySmpAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(authInfoEmptyPublicKey);

    result = smpAuthService.verifyAuthToken(JWT_TOKEN, ACCOUNT_IDENTIFIER);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testVerifyAuthToken_InvalidTokenScenarios_ReturnsFalse() {
    RSAKey mockPublicKey = mock(RSAKey.class);

    when(smpAuthInfoRepository.findBySmpAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);
    when(rsaKeysUtils.readPemFile(PUBLIC_KEY)).thenReturn(mockPublicKey);

    try (MockedStatic<JWTTokenServiceUtils> mockedJWT = Mockito.mockStatic(JWTTokenServiceUtils.class)) {
      // Test invalid token format
      mockedJWT.when(() -> JWTTokenServiceUtils.verifyJWTToken("invalid-token", mockPublicKey, "Harness SMP"))
          .thenThrow(new RuntimeException("Invalid token"));

      boolean result = smpAuthService.verifyAuthToken("invalid-token", ACCOUNT_IDENTIFIER);
      assertThat(result).isFalse();

      // Test expired token
      mockedJWT.when(() -> JWTTokenServiceUtils.verifyJWTToken("expired-token", mockPublicKey, "Harness SMP"))
          .thenThrow(new RuntimeException("Token expired"));

      result = smpAuthService.verifyAuthToken("expired-token", ACCOUNT_IDENTIFIER);
      assertThat(result).isFalse();
    }
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testVerifyAuthToken_ClaimValidation_ReturnsFalse() {
    RSAKey mockPublicKey = mock(RSAKey.class);

    when(smpAuthInfoRepository.findBySmpAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);
    when(rsaKeysUtils.readPemFile(PUBLIC_KEY)).thenReturn(mockPublicKey);

    try (MockedStatic<JWTTokenServiceUtils> mockedJWT = Mockito.mockStatic(JWTTokenServiceUtils.class)) {
      // Test missing account ID claim
      Map<String, Claim> claimsWithoutAccountId = new HashMap<>();
      mockedJWT.when(() -> JWTTokenServiceUtils.verifyJWTToken(JWT_TOKEN, mockPublicKey, "Harness SMP"))
          .thenReturn(claimsWithoutAccountId);

      boolean result = smpAuthService.verifyAuthToken(JWT_TOKEN, ACCOUNT_IDENTIFIER);
      assertThat(result).isFalse();

      // Test mismatched account ID claim
      Map<String, Claim> claimsWithWrongAccountId = new HashMap<>();
      Claim wrongClaim = mock(Claim.class);
      when(wrongClaim.asString()).thenReturn("wrong-account-id");
      claimsWithWrongAccountId.put("account_id", wrongClaim);

      mockedJWT.when(() -> JWTTokenServiceUtils.verifyJWTToken(JWT_TOKEN, mockPublicKey, "Harness SMP"))
          .thenReturn(claimsWithWrongAccountId);

      result = smpAuthService.verifyAuthToken(JWT_TOKEN, ACCOUNT_IDENTIFIER);
      assertThat(result).isFalse();
    }
  }

  // ===== 8. generateSMPZendeskRedirectUrl() Tests =====

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testGenerateSMPZendeskRedirectUrl_Success() {
    RSAPrivateKey mockPrivateKey = mock(RSAPrivateKey.class);

    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);
    when(rsaKeysUtils.readPemFile(PRIVATE_KEY)).thenReturn(mockPrivateKey);

    try (MockedStatic<SourcePrincipalContextBuilder> mockedContext =
             Mockito.mockStatic(SourcePrincipalContextBuilder.class);
         MockedStatic<JWTTokenServiceUtils> mockedJWT = Mockito.mockStatic(JWTTokenServiceUtils.class)) {
      mockedContext.when(SourcePrincipalContextBuilder::getSourcePrincipal).thenReturn(userPrincipal);
      mockedJWT.when(() -> JWTTokenServiceUtils.generateJWTToken(anyMap(), anyMap(), anyLong(), eq(mockPrivateKey)))
          .thenReturn(JWT_TOKEN);

      String result = smpAuthService.generateSMPZendeskRedirectUrl(ACCOUNT_IDENTIFIER, RETURN_TO_URL);

      assertThat(result).isNotNull();
      assertThat(result).contains(BASE_URL);
      assertThat(result).contains(ZENDESK_SSO_PATH);
      assertThat(result).contains("token=" + JWT_TOKEN);
      assertThat(result).contains("accountId=" + ACCOUNT_IDENTIFIER);
      assertThat(result).contains("returnTo=" + RETURN_TO_URL);

      verify(smpConfig).getSaasBaseUrl();
    }
  }

  @Test
  @Owner(developers = OwnerRule.GOKUL)
  @Category(UnitTests.class)
  public void testGenerateSMPZendeskRedirectUrl_UrlFormatting() {
    RSAPrivateKey mockPrivateKey = mock(RSAPrivateKey.class);

    when(smpAuthInfoRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER)).thenReturn(smpAuthInfo);
    when(rsaKeysUtils.readPemFile(PRIVATE_KEY)).thenReturn(mockPrivateKey);

    try (MockedStatic<SourcePrincipalContextBuilder> mockedContext =
             Mockito.mockStatic(SourcePrincipalContextBuilder.class);
         MockedStatic<JWTTokenServiceUtils> mockedJWT = Mockito.mockStatic(JWTTokenServiceUtils.class)) {
      mockedContext.when(SourcePrincipalContextBuilder::getSourcePrincipal).thenReturn(userPrincipal);
      mockedJWT.when(() -> JWTTokenServiceUtils.generateJWTToken(anyMap(), anyMap(), anyLong(), eq(mockPrivateKey)))
          .thenReturn(JWT_TOKEN);

      // Test base URL without trailing slash
      when(smpConfig.getSaasBaseUrl()).thenReturn("https://smp.harness.io");
      String result1 = smpAuthService.generateSMPZendeskRedirectUrl(ACCOUNT_IDENTIFIER, RETURN_TO_URL);
      assertThat(result1).contains("https://smp.harness.io/" + ZENDESK_SSO_PATH);

      // Test base URL with trailing slash
      when(smpConfig.getSaasBaseUrl()).thenReturn("https://smp.harness.io/");
      String result2 = smpAuthService.generateSMPZendeskRedirectUrl(ACCOUNT_IDENTIFIER, RETURN_TO_URL);
      assertThat(result2).contains("https://smp.harness.io/" + ZENDESK_SSO_PATH);

      // Both should have proper URL structure
      assertThat(result1).doesNotContain("//ng/");
      assertThat(result2).doesNotContain("//ng/");
    }
  }
}

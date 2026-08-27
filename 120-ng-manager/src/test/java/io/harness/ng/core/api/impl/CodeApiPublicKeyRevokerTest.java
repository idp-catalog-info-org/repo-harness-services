/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ATEFEH;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.code.CodeResourceClient;
import io.harness.code.KeyUpdatePayload;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PGPPublicKey;
import io.harness.ng.core.common.beans.RevocationReason;
import io.harness.ng.core.common.beans.SSHPublicKey;
import io.harness.ng.core.entities.Token;
import io.harness.repositories.ng.core.spring.TokenRepository;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class CodeApiPublicKeyRevokerTest extends CategoryTest {
  @Mock private CodeResourceClient codeResourceClient;
  @Mock private TokenRepository tokenRepository;

  private CodeApiPublicKeyRevoker codeApiPublicKeyRevoker;

  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  private static final String PARENT_IDENTIFIER = randomAlphabetic(10);
  private static final String UNIQUE_ID = randomAlphabetic(10);

  private ScopeInfo scopeInfo;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    codeApiPublicKeyRevoker = new CodeApiPublicKeyRevoker(codeResourceClient, tokenRepository);
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                    .uniqueId(UNIQUE_ID)
                    .scopeType(ScopeLevel.ACCOUNT)
                    .build();
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testHandles_returnsTrue_forCompromised() {
    assertThat(codeApiPublicKeyRevoker.handles(RevocationReason.COMPROMISED)).isTrue();
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testHandles_returnsFalse_forOtherReasons() {
    assertThat(codeApiPublicKeyRevoker.handles(RevocationReason.RETIRED)).isFalse();
    assertThat(codeApiPublicKeyRevoker.handles(RevocationReason.SUPERSEDED)).isFalse();
    assertThat(codeApiPublicKeyRevoker.handles(RevocationReason.UNKNOWN)).isFalse();
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testRevoke_sshKey_success() throws Exception {
    Token sshToken =
        Token.builder()
            .accountIdentifier(ACCOUNT_IDENTIFIER)
            .parentIdentifier(PARENT_IDENTIFIER)
            .identifier("ssh-key-1")
            .apiKeyType(ApiKeyType.SSH_KEY)
            .sshPublicKey(SSHPublicKey.builder().fingerPrint("SHA256:abc123").sshKey("ssh-rsa AAAA").build())
            .build();

    Call<Void> mockCall = mock(Call.class);
    Response<Void> mockResponse = Response.success(null);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(codeResourceClient.revokePublicKey(eq(ACCOUNT_IDENTIFIER), any())).thenReturn(mockCall);

    codeApiPublicKeyRevoker.revoke(scopeInfo, sshToken);

    ArgumentCaptor<KeyUpdatePayload> payloadCaptor = ArgumentCaptor.forClass(KeyUpdatePayload.class);
    verify(codeResourceClient, times(1)).revokePublicKey(eq(ACCOUNT_IDENTIFIER), payloadCaptor.capture());

    KeyUpdatePayload capturedPayload = payloadCaptor.getValue();
    assertThat(capturedPayload.getFingerprint()).isEqualTo("SHA256:abc123");
    assertThat(capturedPayload.getKeyScheme()).isEqualTo("ssh");
    assertThat(capturedPayload.getPrincipalIdentifier()).isEqualTo(PARENT_IDENTIFIER);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testRevoke_pgpKey_success() throws Exception {
    Token pgpToken = Token.builder()
                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                         .parentIdentifier(PARENT_IDENTIFIER)
                         .identifier("pgp-key-1")
                         .apiKeyType(ApiKeyType.PGP_KEY)
                         .pgpPublicKey(PGPPublicKey.builder()
                                           .fingerprint("PGPFP123")
                                           .keyId("KEY123")
                                           .isSubKey(true)
                                           .parentKeyId("PARENT456")
                                           .build())
                         .build();

    Call<Void> mockCall = mock(Call.class);
    Response<Void> mockResponse = Response.success(null);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(codeResourceClient.revokePublicKey(eq(ACCOUNT_IDENTIFIER), any())).thenReturn(mockCall);

    codeApiPublicKeyRevoker.revoke(scopeInfo, pgpToken);

    ArgumentCaptor<KeyUpdatePayload> payloadCaptor = ArgumentCaptor.forClass(KeyUpdatePayload.class);
    verify(codeResourceClient, times(1)).revokePublicKey(eq(ACCOUNT_IDENTIFIER), payloadCaptor.capture());

    KeyUpdatePayload capturedPayload = payloadCaptor.getValue();
    assertThat(capturedPayload.getKeyIds()).contains("KEY123");
    assertThat(capturedPayload.getKeyScheme()).isEqualTo("pgp");
    assertThat(capturedPayload.getPrincipalIdentifier()).isEqualTo(PARENT_IDENTIFIER);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testRevoke_pgpPrimaryKey_collectsSubKeyIds() throws Exception {
    Token primaryPgpToken = Token.builder()
                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                .parentIdentifier(PARENT_IDENTIFIER)
                                .identifier("pgp-primary-key")
                                .apiKeyType(ApiKeyType.PGP_KEY)
                                .pgpPublicKey(PGPPublicKey.builder()
                                                  .fingerprint("PGPFP123")
                                                  .keyId("PRIMARY_KEY_ID")
                                                  .isSubKey(false)
                                                  .parentKeyId(null)
                                                  .build())
                                .build();

    Token subKey1 =
        Token.builder()
            .apiKeyType(ApiKeyType.PGP_KEY)
            .pgpPublicKey(PGPPublicKey.builder().keyId("SUBKEY1").parentKeyId("PRIMARY_KEY_ID").isSubKey(true).build())
            .build();

    Token subKey2 =
        Token.builder()
            .apiKeyType(ApiKeyType.PGP_KEY)
            .pgpPublicKey(PGPPublicKey.builder().keyId("SUBKEY2").parentKeyId("PRIMARY_KEY_ID").isSubKey(true).build())
            .build();

    when(tokenRepository.findAll(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(subKey1, subKey2)));

    Call<Void> mockCall = mock(Call.class);
    Response<Void> mockResponse = Response.success(null);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(codeResourceClient.revokePublicKey(eq(ACCOUNT_IDENTIFIER), any())).thenReturn(mockCall);

    codeApiPublicKeyRevoker.revoke(scopeInfo, primaryPgpToken);

    ArgumentCaptor<KeyUpdatePayload> payloadCaptor = ArgumentCaptor.forClass(KeyUpdatePayload.class);
    verify(codeResourceClient, times(1)).revokePublicKey(eq(ACCOUNT_IDENTIFIER), payloadCaptor.capture());

    KeyUpdatePayload capturedPayload = payloadCaptor.getValue();
    assertThat(capturedPayload.getKeyIds()).containsExactlyInAnyOrder("PRIMARY_KEY_ID", "SUBKEY1", "SUBKEY2");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testRevoke_throwsException_forUnsupportedKeyType() {
    Token satToken = Token.builder()
                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                         .parentIdentifier(PARENT_IDENTIFIER)
                         .identifier("sat-token")
                         .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
                         .build();

    assertThatThrownBy(() -> codeApiPublicKeyRevoker.revoke(scopeInfo, satToken))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unsupported key type for revocation");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testRevoke_throwsException_whenCodeApiCallFails() throws Exception {
    Token sshToken =
        Token.builder()
            .accountIdentifier(ACCOUNT_IDENTIFIER)
            .parentIdentifier(PARENT_IDENTIFIER)
            .identifier("ssh-key-1")
            .apiKeyType(ApiKeyType.SSH_KEY)
            .sshPublicKey(SSHPublicKey.builder().fingerPrint("SHA256:abc123").sshKey("ssh-rsa AAAA").build())
            .build();

    Call<Void> mockCall = mock(Call.class);
    when(mockCall.execute()).thenThrow(new RuntimeException("Connection failed"));
    when(codeResourceClient.revokePublicKey(eq(ACCOUNT_IDENTIFIER), any())).thenReturn(mockCall);

    assertThatThrownBy(() -> codeApiPublicKeyRevoker.revoke(scopeInfo, sshToken))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to revoke key in code service");
  }
}

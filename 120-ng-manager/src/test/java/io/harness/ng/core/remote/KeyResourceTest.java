/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ATEFEH;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.Status;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PublicKeyScheme;
import io.harness.ng.core.common.beans.RevocationReason;
import io.harness.ng.core.dto.KeyDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.UpdatePublicKeyRequest;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;

import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class KeyResourceTest extends CategoryTest {
  @Mock private TokenService tokenService;
  @Mock private ApiKeyService apiKeyService;
  @Mock private ScopeInfoService scopeResolverService;

  @InjectMocks KeyResource keyResource;

  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  private static final String PARENT_IDENTIFIER = randomAlphabetic(10);
  private static final String KEY_IDENTIFIER = randomAlphabetic(10);

  private ScopeInfo scopeInfo;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                    .uniqueId(ACCOUNT_IDENTIFIER)
                    .scopeType(ScopeLevel.ACCOUNT)
                    .build();
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testUpdateKey_WithRevocationReason_ReturnsUpdatedKey() {
    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.COMPROMISED).build();

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.SSH_KEY)
                            .identifier(KEY_IDENTIFIER)
                            .name("Test Key")
                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                            .parentIdentifier(PARENT_IDENTIFIER)
                            .sshKeyContent("ssh-rsa AAAA...")
                            .sshKeyFingerprint("SHA256:abc123")
                            .revocationReason(RevocationReason.COMPROMISED)
                            .tags(new HashMap<>())
                            .build();

    when(tokenService.updateKey(eq(scopeInfo), eq(PARENT_IDENTIFIER), any(), eq(KEY_IDENTIFIER), eq(request)))
        .thenReturn(tokenDTO);

    ResponseDTO<KeyDTO> response =
        keyResource.updateKey(KEY_IDENTIFIER, ACCOUNT_IDENTIFIER, PARENT_IDENTIFIER, request, scopeInfo);

    verify(apiKeyService, times(1)).validateParentIdentifier(scopeInfo, ApiKeyType.SSH_KEY, PARENT_IDENTIFIER);
    verify(tokenService, times(1))
        .updateKey(eq(scopeInfo), eq(PARENT_IDENTIFIER), any(), eq(KEY_IDENTIFIER), eq(request));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getKeyScheme()).isEqualTo(PublicKeyScheme.SSH);
    assertThat(response.getData().getRevocationReason()).isEqualTo(RevocationReason.COMPROMISED);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testUpdateKey_WithValidityPeriod_ReturnsUpdatedKey() {
    long validFrom = System.currentTimeMillis();
    long validTo = validFrom + 86400000; // +1 day

    UpdatePublicKeyRequest request = UpdatePublicKeyRequest.builder().validFrom(validFrom).validTo(validTo).build();

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.SSH_KEY)
                            .identifier(KEY_IDENTIFIER)
                            .name("Test Key")
                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                            .parentIdentifier(PARENT_IDENTIFIER)
                            .sshKeyContent("ssh-rsa AAAA...")
                            .sshKeyFingerprint("SHA256:abc123")
                            .validFrom(validFrom)
                            .validTo(validTo)
                            .tags(new HashMap<>())
                            .build();

    when(tokenService.updateKey(eq(scopeInfo), eq(PARENT_IDENTIFIER), any(), eq(KEY_IDENTIFIER), eq(request)))
        .thenReturn(tokenDTO);

    ResponseDTO<KeyDTO> response =
        keyResource.updateKey(KEY_IDENTIFIER, ACCOUNT_IDENTIFIER, PARENT_IDENTIFIER, request, scopeInfo);

    verify(apiKeyService, times(1)).validateParentIdentifier(scopeInfo, ApiKeyType.SSH_KEY, PARENT_IDENTIFIER);
    verify(tokenService, times(1))
        .updateKey(eq(scopeInfo), eq(PARENT_IDENTIFIER), any(), eq(KEY_IDENTIFIER), eq(request));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getValidFrom()).isEqualTo(validFrom);
    assertThat(response.getData().getValidTo()).isEqualTo(validTo);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testUpdateKey_ForPGPKey_ReturnsUpdatedKey() {
    UpdatePublicKeyRequest request =
        UpdatePublicKeyRequest.builder().revocationReason(RevocationReason.RETIRED).build();

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.PGP_KEY)
                            .identifier(KEY_IDENTIFIER)
                            .name("Test PGP Key")
                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                            .parentIdentifier(PARENT_IDENTIFIER)
                            .content("-----BEGIN PGP PUBLIC KEY-----")
                            .pgpKeyFingerprint("ABC123")
                            .pgpKeyId("KEY123")
                            .revocationReason(RevocationReason.RETIRED)
                            .tags(new HashMap<>())
                            .build();

    when(tokenService.updateKey(eq(scopeInfo), eq(PARENT_IDENTIFIER), any(), eq(KEY_IDENTIFIER), eq(request)))
        .thenReturn(tokenDTO);

    ResponseDTO<KeyDTO> response =
        keyResource.updateKey(KEY_IDENTIFIER, ACCOUNT_IDENTIFIER, PARENT_IDENTIFIER, request, scopeInfo);

    verify(apiKeyService, times(1)).validateParentIdentifier(scopeInfo, ApiKeyType.SSH_KEY, PARENT_IDENTIFIER);
    verify(tokenService, times(1))
        .updateKey(eq(scopeInfo), eq(PARENT_IDENTIFIER), any(), eq(KEY_IDENTIFIER), eq(request));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getKeyScheme()).isEqualTo(PublicKeyScheme.PGP);
    assertThat(response.getData().getRevocationReason()).isEqualTo(RevocationReason.RETIRED);
    assertThat(response.getData().getKeyId()).isEqualTo("KEY123");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testDeleteKey_ReturnsTrue() {
    when(tokenService.deleteKey(eq(scopeInfo), eq(PARENT_IDENTIFIER), any(), eq(KEY_IDENTIFIER))).thenReturn(true);

    ResponseDTO<Boolean> response =
        keyResource.deleteToken(KEY_IDENTIFIER, ACCOUNT_IDENTIFIER, PARENT_IDENTIFIER, scopeInfo);

    verify(apiKeyService, times(1)).validateParentIdentifier(scopeInfo, ApiKeyType.SSH_KEY, PARENT_IDENTIFIER);
    verify(tokenService, times(1)).deleteKey(eq(scopeInfo), eq(PARENT_IDENTIFIER), any(), eq(KEY_IDENTIFIER));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData()).isTrue();
  }
}

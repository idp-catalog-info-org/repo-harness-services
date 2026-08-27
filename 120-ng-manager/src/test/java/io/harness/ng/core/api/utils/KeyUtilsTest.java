/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.utils;

import static io.harness.rule.OwnerRule.ATEFEH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PublicKeyScheme;
import io.harness.ng.core.common.beans.RevocationReason;
import io.harness.ng.core.dto.KeyDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.rule.Owner;

import java.util.HashMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class KeyUtilsTest {
  private static final String ACCOUNT_IDENTIFIER = "testAccount";
  private static final String PARENT_IDENTIFIER = "testUser";
  private static final String IDENTIFIER = "testKey";
  private static final String NAME = "Test Key";

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testMapTokenToKey_SSHKey_WithRevocationReason() {
    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.SSH_KEY)
                            .identifier(IDENTIFIER)
                            .name(NAME)
                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                            .parentIdentifier(PARENT_IDENTIFIER)
                            .sshKeyContent("ssh-rsa AAAA...")
                            .sshKeyFingerprint("SHA256:abc123")
                            .revocationReason(RevocationReason.COMPROMISED)
                            .tags(new HashMap<>())
                            .build();

    KeyDTO keyDTO = KeyUtils.mapTokenToKey(tokenDTO);

    assertThat(keyDTO).isNotNull();
    assertThat(keyDTO.getKeyScheme()).isEqualTo(PublicKeyScheme.SSH);
    assertThat(keyDTO.getRevocationReason()).isEqualTo(RevocationReason.COMPROMISED);
    assertThat(keyDTO.getIdentifier()).isEqualTo(IDENTIFIER);
    assertThat(keyDTO.getName()).isEqualTo(NAME);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testMapTokenToKey_SSHKey_WithoutRevocationReason() {
    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.SSH_KEY)
                            .identifier(IDENTIFIER)
                            .name(NAME)
                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                            .parentIdentifier(PARENT_IDENTIFIER)
                            .sshKeyContent("ssh-rsa AAAA...")
                            .sshKeyFingerprint("SHA256:abc123")
                            .tags(new HashMap<>())
                            .build();

    KeyDTO keyDTO = KeyUtils.mapTokenToKey(tokenDTO);

    assertThat(keyDTO).isNotNull();
    assertThat(keyDTO.getKeyScheme()).isEqualTo(PublicKeyScheme.SSH);
    assertThat(keyDTO.getRevocationReason()).isNull();
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testMapTokenToKey_PGPKey_WithRevocationReason() {
    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.PGP_KEY)
                            .identifier(IDENTIFIER)
                            .name(NAME)
                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                            .parentIdentifier(PARENT_IDENTIFIER)
                            .content("-----BEGIN PGP PUBLIC KEY-----")
                            .pgpKeyFingerprint("ABC123")
                            .pgpKeyId("KEY123")
                            .revocationReason(RevocationReason.RETIRED)
                            .tags(new HashMap<>())
                            .build();

    KeyDTO keyDTO = KeyUtils.mapTokenToKey(tokenDTO);

    assertThat(keyDTO).isNotNull();
    assertThat(keyDTO.getKeyScheme()).isEqualTo(PublicKeyScheme.PGP);
    assertThat(keyDTO.getRevocationReason()).isEqualTo(RevocationReason.RETIRED);
    assertThat(keyDTO.getKeyId()).isEqualTo("KEY123");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testMapTokenToKey_PGPKey_WithoutRevocationReason() {
    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.PGP_KEY)
                            .identifier(IDENTIFIER)
                            .name(NAME)
                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                            .parentIdentifier(PARENT_IDENTIFIER)
                            .content("-----BEGIN PGP PUBLIC KEY-----")
                            .pgpKeyFingerprint("ABC123")
                            .tags(new HashMap<>())
                            .build();

    KeyDTO keyDTO = KeyUtils.mapTokenToKey(tokenDTO);

    assertThat(keyDTO).isNotNull();
    assertThat(keyDTO.getKeyScheme()).isEqualTo(PublicKeyScheme.PGP);
    assertThat(keyDTO.getRevocationReason()).isNull();
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testMapTokenToKey_SSHKey_AllRevocationReasons() {
    for (RevocationReason reason : RevocationReason.values()) {
      TokenDTO tokenDTO = TokenDTO.builder()
                              .apiKeyType(ApiKeyType.SSH_KEY)
                              .identifier(IDENTIFIER)
                              .name(NAME)
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .parentIdentifier(PARENT_IDENTIFIER)
                              .sshKeyContent("ssh-rsa AAAA...")
                              .revocationReason(reason)
                              .tags(new HashMap<>())
                              .build();

      KeyDTO keyDTO = KeyUtils.mapTokenToKey(tokenDTO);

      assertThat(keyDTO.getRevocationReason()).isEqualTo(reason);
    }
  }
}

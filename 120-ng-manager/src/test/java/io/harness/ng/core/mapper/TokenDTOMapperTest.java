/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.mapper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ATEFEH;
import static io.harness.rule.OwnerRule.SHIVAM_RAJPUT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.PGPKeyUsage;
import io.harness.ng.core.common.beans.ScopedResourceMetadata;
import io.harness.ng.core.common.beans.ScopedResourcePermission;
import io.harness.ng.core.common.beans.TokenMode;
import io.harness.ng.core.dto.PGPPublicKeyDTOInternal;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.entities.Token;
import io.harness.rule.Owner;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class TokenDTOMapperTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  private static final String ORG_IDENTIFIER = randomAlphabetic(10);
  private static final String PROJECT_IDENTIFIER = randomAlphabetic(10);
  private static final String PARENT_IDENTIFIER = randomAlphabetic(10);
  private static final String KEY_IDENTIFIER = randomAlphabetic(10);
  private static final String API_KEY_IDENTIFIER = randomAlphabetic(10);

  private ScopeInfo scopeInfo;

  @Before
  public void setup() {
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                    .orgIdentifier(ORG_IDENTIFIER)
                    .projectIdentifier(PROJECT_IDENTIFIER)
                    .uniqueId(ACCOUNT_IDENTIFIER)
                    .scopeType(ScopeLevel.ACCOUNT)
                    .build();
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testGetTokenFromDTOAndPGPPublicKey_primaryKey() {
    TokenDTO dto = TokenDTO.builder()
                       .identifier(KEY_IDENTIFIER)
                       .name("Test PGP Key")
                       .description("Test Description")
                       .tags(new HashMap<>())
                       .parentIdentifier(PARENT_IDENTIFIER)
                       .apiKeyType(ApiKeyType.PGP_KEY)
                       .apiKeyIdentifier(API_KEY_IDENTIFIER)
                       .accountIdentifier(ACCOUNT_IDENTIFIER)
                       .build();

    PGPPublicKeyDTOInternal pgpDTO = PGPPublicKeyDTOInternal.builder()
                                         .fingerprint("ABC123DEF456")
                                         .keyId("KEY123")
                                         .algorithm("RSA")
                                         .bitLength(4096)
                                         .content("-----BEGIN PGP PUBLIC KEY-----")
                                         .usage(List.of(PGPKeyUsage.SIGN))
                                         .validFrom(1704067200000L)
                                         .validTo(1735689600000L)
                                         .isSubKey(false)
                                         .build();

    Token token = TokenDTOMapper.getTokenFromDTOAndPGPPublicKey(scopeInfo, dto, pgpDTO);

    assertThat(token).isNotNull();
    assertThat(token.getIdentifier()).isEqualTo(KEY_IDENTIFIER);
    assertThat(token.getName()).isEqualTo("Test PGP Key");
    assertThat(token.getDescription()).isEqualTo("Test Description");
    assertThat(token.getParentIdentifier()).isEqualTo(PARENT_IDENTIFIER);
    assertThat(token.getApiKeyType()).isEqualTo(ApiKeyType.PGP_KEY);
    assertThat(token.getApiKeyIdentifier()).isEqualTo(API_KEY_IDENTIFIER);
    assertThat(token.getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(token.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(token.getProjectIdentifier()).isEqualTo(PROJECT_IDENTIFIER);
    assertThat(token.getPgpPublicKey()).isNotNull();
    assertThat(token.getPgpPublicKey().getFingerprint()).isEqualTo("ABC123DEF456");
    assertThat(token.getPgpPublicKey().getKeyId()).isEqualTo("KEY123");
    assertThat(token.getPgpPublicKey().getAlgorithm()).isEqualTo("RSA");
    assertThat(token.getValidFrom()).isNotNull();
    assertThat(token.getValidFrom().toEpochMilli()).isEqualTo(1704067200000L);
    assertThat(token.getValidTo()).isNotNull();
    assertThat(token.getValidTo().toEpochMilli()).isEqualTo(1735689600000L);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testGetTokenFromDTOAndPGPPublicKey_subKey() {
    TokenDTO dto = TokenDTO.builder()
                       .identifier(KEY_IDENTIFIER)
                       .name("Test PGP Key")
                       .description("Test Description")
                       .tags(new HashMap<>())
                       .parentIdentifier(PARENT_IDENTIFIER)
                       .apiKeyType(ApiKeyType.PGP_KEY)
                       .apiKeyIdentifier(API_KEY_IDENTIFIER)
                       .accountIdentifier(ACCOUNT_IDENTIFIER)
                       .build();

    PGPPublicKeyDTOInternal pgpDTO = PGPPublicKeyDTOInternal.builder()
                                         .fingerprint("SUBKEY123")
                                         .keyId("SUBKEY456")
                                         .algorithm("RSA")
                                         .bitLength(4096)
                                         .content("-----BEGIN PGP PUBLIC KEY-----")
                                         .usage(List.of(PGPKeyUsage.SIGN))
                                         .isSubKey(true)
                                         .parentKeyId("PARENT123")
                                         .build();

    Token token = TokenDTOMapper.getTokenFromDTOAndPGPPublicKey(scopeInfo, dto, pgpDTO);

    assertThat(token).isNotNull();
    assertThat(token.getIdentifier()).isEqualTo(KEY_IDENTIFIER);
    // Subkeys get modified name with suffix
    assertThat(token.getName()).isEqualTo("Test PGP Key (Subkey SUBKEY456)");
    assertThat(token.getPgpPublicKey()).isNotNull();
    assertThat(token.getPgpPublicKey().getIsSubKey()).isTrue();
    assertThat(token.getPgpPublicKey().getParentKeyId()).isEqualTo("PARENT123");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testGetTokenFromDTOAndPGPPublicKey_withCustomIdentifier() {
    TokenDTO dto = TokenDTO.builder()
                       .identifier(KEY_IDENTIFIER)
                       .name("Test PGP Key")
                       .description("Test Description")
                       .tags(new HashMap<>())
                       .parentIdentifier(PARENT_IDENTIFIER)
                       .apiKeyType(ApiKeyType.PGP_KEY)
                       .apiKeyIdentifier(API_KEY_IDENTIFIER)
                       .accountIdentifier(ACCOUNT_IDENTIFIER)
                       .build();

    PGPPublicKeyDTOInternal pgpDTO = PGPPublicKeyDTOInternal.builder()
                                         .fingerprint("ABC123")
                                         .keyId("KEY123")
                                         .algorithm("RSA")
                                         .content("-----BEGIN PGP PUBLIC KEY-----")
                                         .usage(List.of(PGPKeyUsage.SIGN))
                                         .isSubKey(false)
                                         .build();

    String customIdentifier = "custom-identifier-123";
    Token token = TokenDTOMapper.getTokenFromDTOAndPGPPublicKey(scopeInfo, dto, pgpDTO, customIdentifier);

    assertThat(token).isNotNull();
    assertThat(token.getIdentifier()).isEqualTo(customIdentifier);
    assertThat(token.getName()).isEqualTo("Test PGP Key");
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testGetTokenFromDTOAndPGPPublicKey_nullValidityDates() {
    TokenDTO dto = TokenDTO.builder()
                       .identifier(KEY_IDENTIFIER)
                       .name("Test PGP Key")
                       .tags(new HashMap<>())
                       .parentIdentifier(PARENT_IDENTIFIER)
                       .apiKeyType(ApiKeyType.PGP_KEY)
                       .apiKeyIdentifier(API_KEY_IDENTIFIER)
                       .accountIdentifier(ACCOUNT_IDENTIFIER)
                       .build();

    PGPPublicKeyDTOInternal pgpDTO = PGPPublicKeyDTOInternal.builder()
                                         .fingerprint("ABC123")
                                         .keyId("KEY123")
                                         .algorithm("RSA")
                                         .content("-----BEGIN PGP PUBLIC KEY-----")
                                         .usage(List.of(PGPKeyUsage.SIGN))
                                         .validFrom(null)
                                         .validTo(null)
                                         .isSubKey(false)
                                         .build();

    Token token = TokenDTOMapper.getTokenFromDTOAndPGPPublicKey(scopeInfo, dto, pgpDTO);

    assertThat(token).isNotNull();
    assertThat(token.getValidFrom()).isNull();
    assertThat(token.getValidTo()).isNull();
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testGetDTOFromTokenForRotation_scopedToken_carriesScopedFields() {
    Instant validTo = Instant.now().plusSeconds(7 * 86400);
    ScopedResourcePermission permission =
        ScopedResourcePermission.builder().resourceType("PIPELINE").permission("core_pipeline_view").build();
    ScopedResourceMetadata metadata =
        ScopedResourceMetadata.builder().parentResourceId("pipeline-exec-1").createdBy("originalUser").build();

    Token token = Token.builder()
                      .identifier(KEY_IDENTIFIER)
                      .name("Scoped Token")
                      .description("desc")
                      .accountIdentifier(ACCOUNT_IDENTIFIER)
                      .orgIdentifier(ORG_IDENTIFIER)
                      .projectIdentifier(PROJECT_IDENTIFIER)
                      .parentIdentifier(PARENT_IDENTIFIER)
                      .apiKeyIdentifier("_scopedTokensSA")
                      .apiKeyType(ApiKeyType.SCOPED_TOKEN)
                      .validFrom(Instant.now())
                      .validTo(validTo)
                      .scopedResourcePermissions(Collections.singletonList(permission))
                      .tokenMode(TokenMode.PERSISTENT)
                      .scopedResourceMetadata(metadata)
                      .build();

    TokenDTO dto = TokenDTOMapper.getDTOFromTokenForRotation(scopeInfo, token);

    assertThat(dto).isNotNull();
    assertThat(dto.getApiKeyType()).isEqualTo(ApiKeyType.SCOPED_TOKEN);
    assertThat(dto.getApiKeyIdentifier()).isEqualTo("_scopedTokensSA");
    assertThat(dto.getValidTo()).isEqualTo(validTo.toEpochMilli());
    assertThat(dto.getTokenMode()).isEqualTo(TokenMode.PERSISTENT);
    assertThat(dto.getScopedResourcePermissions()).containsExactly(permission);
    assertThat(dto.getScopedResourceMetadata()).isEqualTo(metadata);
    assertThat(dto.getScopedResourceMetadata().getCreatedBy()).isEqualTo("originalUser");
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testGetDTOFromTokenForRotation_nonScopedToken_omitsScopedFields() {
    Token token = Token.builder()
                      .identifier(KEY_IDENTIFIER)
                      .name("PAT")
                      .accountIdentifier(ACCOUNT_IDENTIFIER)
                      .parentIdentifier(PARENT_IDENTIFIER)
                      .apiKeyIdentifier(API_KEY_IDENTIFIER)
                      .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
                      .validFrom(Instant.now())
                      .validTo(Instant.now().plusSeconds(86400))
                      .build();

    TokenDTO dto = TokenDTOMapper.getDTOFromTokenForRotation(scopeInfo, token);

    assertThat(dto).isNotNull();
    assertThat(dto.getApiKeyType()).isEqualTo(ApiKeyType.SERVICE_ACCOUNT);
    assertThat(dto.getScopedResourcePermissions()).isNull();
    assertThat(dto.getTokenMode()).isNull();
    assertThat(dto.getScopedResourceMetadata()).isNull();
  }
}

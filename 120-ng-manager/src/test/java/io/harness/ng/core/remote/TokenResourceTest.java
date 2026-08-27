/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN_GARG;
import static io.harness.rule.OwnerRule.SHIVAM_RAJPUT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.Status;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.TokenAggregateDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.TokenFilterDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.PageUtils;

import java.time.Instant;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class TokenResourceTest extends CategoryTest {
  @Mock private TokenService tokenService;
  @Mock private ApiKeyService apiKeyService;
  @Mock private ScopeInfoService scopeInfoService;

  @InjectMocks TokenResource tokenResource;

  String accountIdentifier = randomAlphabetic(10);
  String orgIdentifier = randomAlphabetic(10);
  String projectIdentifier = randomAlphabetic(10);
  String parentIdentifier = randomAlphabetic(10);
  String tokenIdentifier = randomAlphabetic(10);
  String apiKeyIdentifier = randomAlphabetic(10);
  ApiKeyType apiKeyType = ApiKeyType.SERVICE_ACCOUNT;

  ScopeInfo scopeInfo = ScopeInfo.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .uniqueId(accountIdentifier)
                            .scopeType(ScopeLevel.ACCOUNT)
                            .build();

  TokenDTO tokenDTO;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    tokenDTO = TokenDTO.builder()
                   .accountIdentifier(accountIdentifier)
                   .orgIdentifier(orgIdentifier)
                   .projectIdentifier(projectIdentifier)
                   .apiKeyType(apiKeyType)
                   .parentIdentifier(parentIdentifier)
                   .apiKeyIdentifier(apiKeyIdentifier)
                   .name("Test Token")
                   .build();
  }

  /*
   Tests token creation when provided with a valid TokenDTO.
   Verifies that the token service is called with correct parameters and returns the expected token.
  */
  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void whenCreateToken_WithValidTokenDTO_ThenReturnGeneratedToken() {
    // Mock token value that would be returned by the token service
    String generatedToken = "generated-token-value";

    when(scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(scopeInfo);
    when(tokenService.createToken(any(TokenDTO.class), eq(scopeInfo))).thenReturn(generatedToken);

    ResponseDTO<String> response = tokenResource.createToken(accountIdentifier, tokenDTO);

    verify(apiKeyService, times(1)).validateParentIdentifier(scopeInfo, apiKeyType, parentIdentifier);
    verify(tokenService, times(1)).createToken(tokenDTO, scopeInfo);
    assertThat(response.getData()).isEqualTo(generatedToken);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
  }

  /*
  Tests token update functionality with valid token data.
  Verifies that the token service properly updates the token and returns the updated token information.
  */
  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void whenUpdateToken_WithValidTokenDTO_ThenReturnUpdatedToken() {
    when(scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(scopeInfo);
    when(tokenService.updateToken(any(TokenDTO.class), eq(scopeInfo))).thenReturn(tokenDTO);

    ResponseDTO<TokenDTO> response = tokenResource.updateToken(tokenIdentifier, tokenDTO, accountIdentifier);

    verify(apiKeyService, times(1)).validateParentIdentifier(scopeInfo, apiKeyType, parentIdentifier);
    verify(tokenService, times(1)).updateToken(tokenDTO, scopeInfo);
    assertThat(response.getData()).isEqualTo(tokenDTO);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
  }

  /*
   Tests token deletion with valid token identifier and related parameters.
   Verifies that the service correctly handles token revocation and returns success status.
  */
  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void whenDeleteToken_WithValidParameters_ThenReturnTrue() {
    when(tokenService.revokeToken(scopeInfo, apiKeyType, parentIdentifier, apiKeyIdentifier, tokenIdentifier))
        .thenReturn(true);

    ResponseDTO<Boolean> response = tokenResource.deleteToken(tokenIdentifier, accountIdentifier, orgIdentifier,
        projectIdentifier, apiKeyType, parentIdentifier, apiKeyIdentifier, scopeInfo);

    verify(apiKeyService, times(1)).validateParentIdentifier(scopeInfo, apiKeyType, parentIdentifier);
    verify(tokenService, times(1))
        .revokeToken(scopeInfo, apiKeyType, parentIdentifier, apiKeyIdentifier, tokenIdentifier);
    assertThat(response.getData()).isTrue();
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
  }

  /*
   Tests token rotation with valid rotation timestamp.
   Verifies that the token service rotates the token correctly and returns the newly generated token.
  */
  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void whenRotateToken_WithValidTimestamp_ThenReturnRotatedToken() {
    // Create mock data for token rotation
    String generatedToken = "rotated-token-value";
    long rotateTimestamp = System.currentTimeMillis();
    // Convert timestamp to Instant which is what the service expects
    Instant rotateInstant = Instant.ofEpochMilli(rotateTimestamp);

    when(tokenService.rotateToken(
             scopeInfo, apiKeyType, parentIdentifier, apiKeyIdentifier, tokenIdentifier, rotateInstant))
        .thenReturn(generatedToken);

    ResponseDTO<String> response = tokenResource.rotateToken(tokenIdentifier, rotateTimestamp, accountIdentifier,
        orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, apiKeyIdentifier, scopeInfo);

    verify(apiKeyService, times(1)).validateParentIdentifier(scopeInfo, apiKeyType, parentIdentifier);
    verify(tokenService, times(1))
        .rotateToken(scopeInfo, apiKeyType, parentIdentifier, apiKeyIdentifier, tokenIdentifier, rotateInstant);
    assertThat(response.getData()).isEqualTo(generatedToken);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
  }

  /*
   Tests listing and filtering of tokens with pagination.
   Verifies that the proper filter validation occurs and the service returns a correctly paged response.
  */
  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void whenListAggregatedTokens_WithFilterParameters_ThenReturnPagedResponse() {
    // Create filter criteria that matches the request parameters
    TokenFilterDTO filterDTO = TokenFilterDTO.builder()
                                   .accountIdentifier(accountIdentifier)
                                   .parentIdentifier(parentIdentifier)
                                   .apiKeyType(apiKeyType)
                                   .apiKeyIdentifier(apiKeyIdentifier)
                                   .build();

    // Set up pagination parameters and create an empty page response for testing
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    PageResponse<TokenAggregateDTO> pageResponse = PageUtils.offsetAndLimit(Collections.emptyList(), 0, 10);

    when(tokenService.listAggregateTokens(eq(scopeInfo), any(), any())).thenReturn(pageResponse);

    ResponseDTO<PageResponse<TokenAggregateDTO>> response =
        tokenResource.listAggregatedTokens(accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType,
            parentIdentifier, apiKeyIdentifier, null, pageRequest, null, false, scopeInfo);

    verify(tokenService, times(1)).validateTokenListPermissions(scopeInfo, filterDTO);
    verify(tokenService, times(1)).listAggregateTokens(eq(scopeInfo), any(), eq(filterDTO));
    assertThat(response.getData()).isEqualTo(pageResponse);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
  }

  /*
   Tests token validation with a valid API key.
   Verifies that the token service correctly validates the API key and returns the associated token information.
  */
  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void whenValidateToken_WithValidApiKey_ThenReturnTokenDTO() {
    String apiKey = "test-api-key";

    when(tokenService.validateToken(accountIdentifier, apiKey)).thenReturn(tokenDTO);

    ResponseDTO<TokenDTO> response = tokenResource.validateToken(accountIdentifier, apiKey);

    verify(tokenService, times(1)).validateToken(accountIdentifier, apiKey);
    assertThat(response.getData()).isEqualTo(tokenDTO);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void whenCreateScopedToken_WithoutParentIdentifier_ThenAutoPopulateFromContext() {
    String userId = randomAlphabetic(10);
    Principal principal = new UserPrincipal(userId, "", "", accountIdentifier);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);

    TokenDTO scopedTokenDTO = TokenDTO.builder()
                                  .accountIdentifier(accountIdentifier)
                                  .apiKeyType(ApiKeyType.SCOPED_TOKEN)
                                  .name("Scoped Token Test")
                                  .build();

    when(scopeInfoService.getScopeInfo(accountIdentifier, null, null)).thenReturn(scopeInfo);
    when(tokenService.createToken(any(TokenDTO.class), eq(scopeInfo))).thenReturn("st.token");

    ResponseDTO<String> response = tokenResource.createToken(accountIdentifier, scopedTokenDTO);

    assertThat(scopedTokenDTO.getParentIdentifier()).isEqualTo(userId);
    assertThat(response.getData()).isEqualTo("st.token");
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);

    SourcePrincipalContextBuilder.setSourcePrincipal(null);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void whenCreateScopedToken_WithParentIdentifier_ThenDoNotOverwrite() {
    String userId = randomAlphabetic(10);
    String explicitParent = randomAlphabetic(10);
    Principal principal = new UserPrincipal(userId, "", "", accountIdentifier);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);

    TokenDTO scopedTokenDTO = TokenDTO.builder()
                                  .accountIdentifier(accountIdentifier)
                                  .apiKeyType(ApiKeyType.SCOPED_TOKEN)
                                  .parentIdentifier(explicitParent)
                                  .name("Scoped Token Explicit Parent")
                                  .build();

    when(scopeInfoService.getScopeInfo(accountIdentifier, null, null)).thenReturn(scopeInfo);
    when(tokenService.createToken(any(TokenDTO.class), eq(scopeInfo))).thenReturn("st.token");

    tokenResource.createToken(accountIdentifier, scopedTokenDTO);

    assertThat(scopedTokenDTO.getParentIdentifier()).isEqualTo(explicitParent);

    SourcePrincipalContextBuilder.setSourcePrincipal(null);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void whenCreateScopedToken_WithoutApiKeyIdentifier_ThenSucceed() {
    String userId = randomAlphabetic(10);
    Principal principal = new UserPrincipal(userId, "", "", accountIdentifier);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);

    TokenDTO scopedTokenDTO = TokenDTO.builder()
                                  .accountIdentifier(accountIdentifier)
                                  .apiKeyType(ApiKeyType.SCOPED_TOKEN)
                                  .parentIdentifier(userId)
                                  .name("Scoped Token No ApiKey")
                                  .build();

    when(scopeInfoService.getScopeInfo(accountIdentifier, null, null)).thenReturn(scopeInfo);
    when(tokenService.createToken(any(TokenDTO.class), eq(scopeInfo))).thenReturn("st.token");

    ResponseDTO<String> response = tokenResource.createToken(accountIdentifier, scopedTokenDTO);

    assertThat(response.getData()).isEqualTo("st.token");
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);

    SourcePrincipalContextBuilder.setSourcePrincipal(null);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void whenCreatePATToken_WithoutApiKeyIdentifier_ThenThrowException() {
    TokenDTO patTokenDTO = TokenDTO.builder()
                               .accountIdentifier(accountIdentifier)
                               .apiKeyType(ApiKeyType.USER)
                               .parentIdentifier(parentIdentifier)
                               .name("PAT Token No ApiKey")
                               .build();

    assertThatThrownBy(() -> tokenResource.createToken(accountIdentifier, patTokenDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("apiKeyIdentifier is required for PAT/SAT tokens");
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void whenCreateSATToken_WithoutApiKeyIdentifier_ThenThrowException() {
    TokenDTO satTokenDTO = TokenDTO.builder()
                               .accountIdentifier(accountIdentifier)
                               .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
                               .parentIdentifier(parentIdentifier)
                               .name("SAT Token No ApiKey")
                               .build();

    assertThatThrownBy(() -> tokenResource.createToken(accountIdentifier, satTokenDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("apiKeyIdentifier is required for PAT/SAT tokens");
  }
}

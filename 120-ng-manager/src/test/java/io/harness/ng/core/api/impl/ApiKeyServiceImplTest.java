/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.common.beans.ApiKeyType.SERVICE_ACCOUNT;
import static io.harness.ng.core.common.beans.ApiKeyType.USER;
import static io.harness.rule.OwnerRule.ABHISHEK_SINGH;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.GAURAV_NANDA;
import static io.harness.rule.OwnerRule.KARAN_GARG;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.ListUtils;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.account.ServiceAccountConfig;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.ApiKeyDTO;
import io.harness.ng.core.dto.GatewayAccountRequestDTO;
import io.harness.ng.core.entities.ApiKey;
import io.harness.ng.core.mapper.ApiKeyDTOMapper;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.opa.entities.apiKey.ApiKeyOpaService;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.opaclient.model.OpaConstants;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.ApiKeyRepository;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;
import io.harness.serviceaccount.ServiceAccountDTO;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class ApiKeyServiceImplTest extends NgManagerTestBase {
  private ApiKeyService apiKeyService;
  private ApiKeyRepository apiKeyRepository;
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String uniqueId;
  private String identifier;
  private String parentIdentifier;

  private ScopeInfo scopeInfo;
  private ApiKeyDTO apiKeyDTO;
  private ApiKey apiKey;
  private ServiceAccountDTO serviceAccountDTO;
  private AccountService accountService;
  private TransactionTemplate transactionTemplate;
  private NgUserService ngUserService;
  private ServiceAccountService serviceAccountService;

  private ApiKeyOpaService apiKeyOpaService;
  private OutboxService outboxService;

  private static final String TEST_PRINCIPAL = "TEST_PRINCIPAL";
  private static final String TEST_ACCOUNT_ID = "TEST_ACCOUNT_ID";
  private static final String TEST_ACCOUNT_ID2 = "TEST_ACCOUNT_ID2";
  private static final String TEST_USER_EMAIL = "test.user@harness.io";

  @Before
  public void setup() throws IllegalAccessException {
    accountIdentifier = randomAlphabetic(10);
    orgIdentifier = randomAlphabetic(10);
    projectIdentifier = randomAlphabetic(10);
    uniqueId = randomAlphabetic(10);
    identifier = randomAlphabetic(10);
    parentIdentifier = randomAlphabetic(10);
    apiKeyRepository = mock(ApiKeyRepository.class);
    apiKeyService = new ApiKeyServiceImpl();
    accountService = mock(AccountService.class);
    ngUserService = mock(NgUserService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    serviceAccountService = mock(ServiceAccountService.class);
    apiKeyOpaService = mock(ApiKeyOpaService.class);
    outboxService = mock(OutboxService.class);

    apiKeyDTO = ApiKeyDTO.builder()
                    .accountIdentifier(accountIdentifier)
                    .orgIdentifier(orgIdentifier)
                    .projectIdentifier(projectIdentifier)
                    .identifier(identifier)
                    .parentIdentifier(parentIdentifier)
                    .apiKeyType(SERVICE_ACCOUNT)
                    .name(randomAlphabetic(10))
                    .defaultTimeToExpireToken(Instant.now().toEpochMilli())
                    .description("")
                    .tags(new HashMap<>())
                    .build();
    apiKey = ApiKey.builder()
                 .accountIdentifier(accountIdentifier)
                 .orgIdentifier(orgIdentifier)
                 .projectIdentifier(projectIdentifier)
                 .identifier(identifier)
                 .parentIdentifier(parentIdentifier)
                 .apiKeyType(SERVICE_ACCOUNT)
                 .name(randomAlphabetic(10))
                 .defaultTimeToExpireToken(Instant.now().toEpochMilli())
                 .description("")
                 .tags(new ArrayList<>())
                 .build();
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(accountIdentifier)
                    .orgIdentifier(orgIdentifier)
                    .projectIdentifier(projectIdentifier)
                    .uniqueId(uniqueId)
                    .build();

    when(transactionTemplate.execute(any())).thenReturn(apiKeyDTO);
    when(serviceAccountService.getServiceAccountDTO(scopeInfo, parentIdentifier)).thenReturn(serviceAccountDTO);
    FieldUtils.writeField(apiKeyService, "apiKeyRepository", apiKeyRepository, true);
    FieldUtils.writeField(apiKeyService, "accountService", accountService, true);
    FieldUtils.writeField(apiKeyService, "transactionTemplate", transactionTemplate, true);
    FieldUtils.writeField(apiKeyService, "ngUserService", ngUserService, true);
    FieldUtils.writeField(apiKeyService, "serviceAccountService", serviceAccountService, true);
    FieldUtils.writeField(apiKeyService, "apiKeyOpaService", apiKeyOpaService, true);
    FieldUtils.writeField(apiKeyService, "outboxService", outboxService, true);

    Principal principal = new UserPrincipal(TEST_PRINCIPAL, TEST_USER_EMAIL, "", TEST_ACCOUNT_ID);
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateApiKey_duplicateIdentifier() {
    doReturn(AccountDTO.builder()
                 .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
                 .build())
        .when(accountService)
        .getAccount(any());
    when(serviceAccountService.getServiceAccountDTO(scopeInfo, parentIdentifier)).thenReturn(serviceAccountDTO);
    when(apiKeyOpaService.evaluatePoliciesWithEntity(any(), any(), any(), any())).thenReturn(null);
    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId("uniqueId").build();
    apiKeyService.createApiKey(apiKeyDTO, scopeInfo);
    doThrow(new DuplicateFieldException(String.format("Try using different Key name, [%s] already exists", identifier)))
        .when(transactionTemplate)
        .execute(any());
    assertThatThrownBy(() -> apiKeyService.createApiKey(apiKeyDTO, scopeInfo))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(String.format("Try using different Key name, [%s] already exists", identifier));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testCreateApiKey_noDescription() {
    ApiKeyDTO dto = ApiKeyDTO.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .identifier("createApiKey_noDescription")
                        .parentIdentifier(parentIdentifier)
                        .apiKeyType(SERVICE_ACCOUNT)
                        .name(randomAlphabetic(10))
                        .defaultTimeToExpireToken(Instant.now().toEpochMilli())
                        .tags(new HashMap<>())
                        .build();
    doReturn(AccountDTO.builder()
                 .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
                 .build())
        .when(accountService)
        .getAccount(any());
    when(transactionTemplate.execute(any())).thenReturn(dto);
    when(apiKeyOpaService.evaluatePoliciesWithEntity(any(), any(), any(), any())).thenReturn(null);
    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId("uniqueId").build();
    ApiKeyDTO apiKey = apiKeyService.createApiKey(dto, scopeInfo);
    assertThat(apiKey.getDescription()).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateApiKey_noAccountExists() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    doReturn(Optional.empty())
        .when(apiKeyRepository)
        .findByAccountIdentifierAndParentUniqueIdAndAndApiKeyTypeAndParentIdentifierAndIdentifier(
            scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), USER, parentIdentifier, identifier);
    doReturn(AccountDTO.builder()
                 .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
                 .build())
        .when(accountService)
        .getAccount(any());
    when(serviceAccountService.getServiceAccountDTO(scopeInfo, parentIdentifier)).thenReturn(serviceAccountDTO);
    assertThatThrownBy(() -> apiKeyService.updateApiKey(apiKeyDTO, scopeInfo))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Api key not present in scope for identifier: " + identifier);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void listServiceAccountDTO() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    doReturn(ListUtils.newArrayList(ApiKey.builder()
                                        .identifier(identifier)
                                        .accountIdentifier(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .build()))
        .when(apiKeyRepository)
        .findAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifier(
            scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), SERVICE_ACCOUNT, parentIdentifier);
    List<ApiKeyDTO> apiKeys =
        apiKeyService.listApiKeys(scopeInfo, SERVICE_ACCOUNT, parentIdentifier, new ArrayList<>());
    assertThat(apiKeys.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = GAURAV_NANDA)
  @Category(UnitTests.class)
  public void validateParentIdentifier_userBelongToAccount_noExceptionThrown() {
    // Arrange
    doReturn(
        Optional.of(UserInfo.builder()
                        .email(TEST_USER_EMAIL)
                        .uuid(TEST_PRINCIPAL)
                        .accounts(ImmutableList.of(GatewayAccountRequestDTO.builder().uuid(TEST_ACCOUNT_ID).build()))
                        .build()))
        .when(ngUserService)
        .getUserById(any());

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_ID).uniqueId(uniqueId).build();
    // Act
    apiKeyService.validateParentIdentifier(scopeInfo, ApiKeyType.USER, TEST_PRINCIPAL);
  }

  @Test(expected = NGAccessDeniedException.class)
  @Owner(developers = GAURAV_NANDA)
  @Category(UnitTests.class)
  public void validateParentIdentifier_userDoesNotBelongToAccount_notAuthorizedExceptionThrown() {
    // Arrange
    String randomAccountId = "34353";
    doReturn(
        Optional.of(UserInfo.builder()
                        .email(TEST_USER_EMAIL)
                        .uuid(TEST_PRINCIPAL)
                        .accounts(ImmutableList.of(GatewayAccountRequestDTO.builder().uuid(randomAccountId).build()))
                        .build()))
        .when(ngUserService)
        .getUserById(any());

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_ID).uniqueId(uniqueId).build();
    // Act
    apiKeyService.validateParentIdentifier(scopeInfo, ApiKeyType.USER, TEST_PRINCIPAL);
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void validateParentIdentifier_nullSourcePrincipal() {
    SourcePrincipalContextBuilder.setSourcePrincipal(null);

    assertThatThrownBy(() -> apiKeyService.validateParentIdentifier(scopeInfo, USER, TEST_PRINCIPAL))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("No user identifier present in context");
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void validateParentIdentifier_nonUserPrincipal() {
    Principal nonUserPrincipal = mock(Principal.class);
    when(nonUserPrincipal.getType()).thenReturn(PrincipalType.SERVICE);
    when(nonUserPrincipal.getName()).thenReturn("service-account");
    SourcePrincipalContextBuilder.setSourcePrincipal(nonUserPrincipal);

    assertThatThrownBy(() -> apiKeyService.validateParentIdentifier(scopeInfo, USER, TEST_PRINCIPAL))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage("User [service-account] is not authorized");
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void validateParentIdentifier_nullAccountsList() {
    Principal userPrincipal = new UserPrincipal(TEST_PRINCIPAL, TEST_USER_EMAIL, "", TEST_ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(userPrincipal);

    // User exists but has null accounts list
    doReturn(Optional.of(UserInfo.builder()
                             .email(TEST_USER_EMAIL)
                             .uuid(TEST_PRINCIPAL)
                             .accounts(null) // Explicitly set accounts to null
                             .build()))
        .when(ngUserService)
        .getUserById(any());

    assertThatThrownBy(() -> apiKeyService.validateParentIdentifier(scopeInfo, USER, TEST_PRINCIPAL))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage(String.format("User [%s] is not authorized to perform action on [%s] Key for account: [%s]",
            TEST_PRINCIPAL, USER, scopeInfo.getAccountIdentifier()));
  }

  @Test
  @Owner(developers = KARAN_GARG)
  @Category(UnitTests.class)
  public void validateParentIdentifier_differentUserIdentifier() {
    String differentUserId = "different-user-id";
    Principal userPrincipal = new UserPrincipal(TEST_PRINCIPAL, TEST_USER_EMAIL, "", TEST_ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(userPrincipal);

    assertThatThrownBy(() -> apiKeyService.validateParentIdentifier(scopeInfo, USER, differentUserId))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage(String.format(
            "User [%s] not authorized to perform the action for user [%s]", TEST_PRINCIPAL, differentUserId));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenCreateApiKey_AndOPAPolicyReturnsError_ThenReturnWithGovernanceMetadata() {
    doReturn(AccountDTO.builder()
                 .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
                 .build())
        .when(accountService)
        .getAccount(eq(accountIdentifier));

    GovernanceMetadata errorMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_ERROR).setDeny(true).build();
    when(apiKeyOpaService.evaluatePoliciesWithEntity(
             eq(scopeInfo), eq(apiKeyDTO), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier)))
        .thenReturn(errorMetadata);

    ApiKeyDTO result = apiKeyService.createApiKey(apiKeyDTO, scopeInfo);

    assertThat(result.getGovernanceMetadata()).isEqualTo(errorMetadata);
    assertThat(result.getIdentifier()).isNull();
    assertThat(result.getName()).isNull();
    assertThat(result.getDescription()).isNull();
    assertThat(result.getTags()).isNull();
    assertThat(result.getApiKeyType()).isNull();
    assertThat(result.getParentIdentifier()).isNull();
    assertThat(result.getDefaultTimeToExpireToken()).isNull();
    assertThat(result.getAccountIdentifier()).isNull();
    assertThat(result.getOrgIdentifier()).isNull();
    assertThat(result.getProjectIdentifier()).isNull();
    assertThat(result.getUniqueId()).isNull();
    assertThat(result.getParentUniqueId()).isNull();
    verify(transactionTemplate, times(0)).execute(any());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenCreateApiKey_AndOPAPolicyReturnsWarning_ThenSaveAndReturnWithGovernanceMetadata() {
    doReturn(AccountDTO.builder()
                 .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
                 .build())
        .when(accountService)
        .getAccount(eq(accountIdentifier));

    GovernanceMetadata warnMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_WARNING).setDeny(false).build();
    when(apiKeyOpaService.evaluatePoliciesWithEntity(
             eq(scopeInfo), eq(apiKeyDTO), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier)))
        .thenReturn(warnMetadata);

    doReturn(apiKey).when(apiKeyRepository).save(any());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocation
            -> invocation.getArgument(0, TransactionCallback.class).doInTransaction(new SimpleTransactionStatus()));

    ApiKeyDTO result = apiKeyService.createApiKey(apiKeyDTO, scopeInfo);

    ApiKeyDTO expected = ApiKeyDTOMapper.getDTOFromApiKey(apiKey, scopeInfo);
    expected.setGovernanceMetadata(warnMetadata);

    assertThat(result).isEqualTo(expected);
    assertThat(result.getIdentifier()).isEqualTo(expected.getIdentifier());
    assertThat(result.getName()).isEqualTo(expected.getName());
    assertThat(result.getParentIdentifier()).isEqualTo(expected.getParentIdentifier());
    assertThat(result.getAccountIdentifier()).isEqualTo(expected.getAccountIdentifier());
    assertThat(result.getGovernanceMetadata()).isEqualTo(warnMetadata);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenUpdateApiKey_AndOPAPolicyReturnsError_ThenReturnWithGovernanceMetadata() {
    doReturn(Optional.of(apiKey))
        .when(apiKeyRepository)
        .findByAccountIdentifierAndParentUniqueIdAndAndApiKeyTypeAndParentIdentifierAndIdentifier(
            eq(accountIdentifier), eq(uniqueId), eq(SERVICE_ACCOUNT), eq(parentIdentifier), eq(identifier));

    GovernanceMetadata errorMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_ERROR).setDeny(true).build();
    when(apiKeyOpaService.evaluatePoliciesWithEntity(
             eq(scopeInfo), eq(apiKeyDTO), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier)))
        .thenReturn(errorMetadata);

    ApiKeyDTO result = apiKeyService.updateApiKey(apiKeyDTO, scopeInfo);

    assertThat(result.getGovernanceMetadata()).isEqualTo(errorMetadata);
    assertThat(result.getIdentifier()).isNull();
    assertThat(result.getName()).isNull();
    assertThat(result.getDescription()).isNull();
    assertThat(result.getTags()).isNull();
    assertThat(result.getApiKeyType()).isNull();
    assertThat(result.getParentIdentifier()).isNull();
    assertThat(result.getDefaultTimeToExpireToken()).isNull();
    assertThat(result.getAccountIdentifier()).isNull();
    assertThat(result.getOrgIdentifier()).isNull();
    assertThat(result.getProjectIdentifier()).isNull();
    assertThat(result.getUniqueId()).isNull();
    assertThat(result.getParentUniqueId()).isNull();
    verify(transactionTemplate, times(0)).execute(any());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenUpdateApiKey_AndOPAPolicyReturnsWarning_ThenSaveAndReturnWithGovernanceMetadata() {
    doReturn(Optional.of(apiKey))
        .when(apiKeyRepository)
        .findByAccountIdentifierAndParentUniqueIdAndAndApiKeyTypeAndParentIdentifierAndIdentifier(
            eq(accountIdentifier), eq(uniqueId), eq(SERVICE_ACCOUNT), eq(parentIdentifier), eq(identifier));

    GovernanceMetadata warnMetadata =
        GovernanceMetadata.newBuilder().setStatus(OpaConstants.OPA_STATUS_WARNING).setDeny(false).build();
    when(apiKeyOpaService.evaluatePoliciesWithEntity(
             eq(scopeInfo), eq(apiKeyDTO), eq(OpaConstants.OPA_EVALUATION_ACTION_SAVE), eq(identifier)))
        .thenReturn(warnMetadata);

    doReturn(apiKey).when(apiKeyRepository).save(any());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocation
            -> invocation.getArgument(0, TransactionCallback.class).doInTransaction(new SimpleTransactionStatus()));

    ApiKeyDTO result = apiKeyService.updateApiKey(apiKeyDTO, scopeInfo);

    ApiKeyDTO expected = ApiKeyDTOMapper.getDTOFromApiKey(apiKey, scopeInfo);
    expected.setGovernanceMetadata(warnMetadata);

    assertThat(result).isEqualTo(expected);
    assertThat(result.getIdentifier()).isEqualTo(expected.getIdentifier());
    assertThat(result.getName()).isEqualTo(expected.getName());
    assertThat(result.getParentIdentifier()).isEqualTo(expected.getParentIdentifier());
    assertThat(result.getAccountIdentifier()).isEqualTo(expected.getAccountIdentifier());
    assertThat(result.getGovernanceMetadata()).isEqualTo(warnMetadata);
    verify(transactionTemplate, times(1)).execute(any());
  }
}

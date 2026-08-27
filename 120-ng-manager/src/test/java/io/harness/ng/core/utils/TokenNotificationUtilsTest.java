/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ABHISHEK_SINGH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.entities.NotificationEvent;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.utils.ScopeResolutionHelper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class TokenNotificationUtilsTest extends CategoryTest {
  private static final String TEST_ACCOUNT_NAME = "Test Account";
  private static final String TEST_SERVICE_ACCOUNT_NAME = "Test SA";

  @Mock private FeatureFlagService featureFlagService;
  @Mock private AccountClient accountClient;
  @Mock private NotificationClient notificationClient;
  @Mock private ServiceAccountService serviceAccountService;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;

  @InjectMocks private TokenNotificationUtils tokenNotificationUtils;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  private void assertCommonTemplateData(Map<String, String> templateData, String templateIdentifier, String tokenName,
      String tokenIdentifier, String parentIdentifier, String apiKeyIdentifier, String accountIdentifier, String orgId,
      String projectId) {
    assertEquals(templateIdentifier, templateData.get("TEMPLATE_IDENTIFIER"));
    assertEquals(tokenName, templateData.get("TOKEN_NAME"));
    assertEquals(tokenIdentifier, templateData.get("TOKEN_IDENTIFIER"));
    assertEquals(parentIdentifier, templateData.get("PARENT_IDENTIFIER"));
    assertEquals(apiKeyIdentifier, templateData.get("API_KEY_IDENTIFIER"));
    assertEquals("SERVICE_ACCOUNT", templateData.get("API_KEY_TYPE"));
    assertEquals(accountIdentifier, templateData.get("ACCOUNT_IDENTIFIER"));
    assertEquals(orgId, templateData.get("ORG_IDENTIFIER"));
    assertEquals(projectId, templateData.get("PROJECT_IDENTIFIER"));
    assertEquals(TEST_SERVICE_ACCOUNT_NAME, templateData.get("SERVICE_ACCOUNT_NAME"));
    assertEquals(TEST_ACCOUNT_NAME, templateData.get("ACCOUNT_NAME"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testGetTemplateIdentifier_ReturnsCorrectTemplateForAllEvents() {
    // Test TOKEN_CREATED
    assertEquals("token_created", tokenNotificationUtils.getTemplateIdentifier(NotificationEvent.TOKEN_CREATED));

    // Test TOKEN_EDITED
    assertEquals("token_edited", tokenNotificationUtils.getTemplateIdentifier(NotificationEvent.TOKEN_EDITED));

    // Test TOKEN_ROTATED
    assertEquals("token_rotated", tokenNotificationUtils.getTemplateIdentifier(NotificationEvent.TOKEN_ROTATED));

    // Test TOKEN_DELETED
    assertEquals("token_deleted", tokenNotificationUtils.getTemplateIdentifier(NotificationEvent.TOKEN_DELETED));

    // Test TOKEN_EXPIRED
    assertEquals("token_expired", tokenNotificationUtils.getTemplateIdentifier(NotificationEvent.TOKEN_EXPIRED));

    // Test TOKEN_ABOUT_TO_EXPIRE
    assertEquals(
        "token_about_to_expire", tokenNotificationUtils.getTemplateIdentifier(NotificationEvent.TOKEN_ABOUT_TO_EXPIRE));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testBuildBaseTemplateData_WithTokenDTO_BuildsCorrectTemplateData() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String parentUniqueId = randomAlphabetic(10);
    String parentIdentifier = randomAlphabetic(10);
    String orgId = randomAlphabetic(10);
    String projectId = randomAlphabetic(10);
    String tokenIdentifier = randomAlphabetic(10);
    String tokenName = randomAlphabetic(10);
    String apiKeyIdentifier = randomAlphabetic(10);
    String username = randomAlphabetic(10);

    TokenDTO token = TokenDTO.builder()
                         .accountIdentifier(accountIdentifier)
                         .orgIdentifier(orgId)
                         .projectIdentifier(projectId)
                         .identifier(tokenIdentifier)
                         .name(tokenName)
                         .parentIdentifier(parentIdentifier)
                         .parentUniqueId(parentUniqueId)
                         .apiKeyIdentifier(apiKeyIdentifier)
                         .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
                         .username(username)
                         .validFrom(System.currentTimeMillis())
                         .validTo(System.currentTimeMillis() + 86400000L)
                         .build();

    Map<String, String> templateData = tokenNotificationUtils.buildBaseTemplateData(
        token, "token_created", orgId, projectId, TEST_SERVICE_ACCOUNT_NAME, TEST_ACCOUNT_NAME);

    assertCommonTemplateData(templateData, "token_created", tokenName, tokenIdentifier, parentIdentifier,
        apiKeyIdentifier, accountIdentifier, orgId, projectId);
    assertNotNull(templateData.get("EXPIRY_DATE"));
    assertEquals(username, templateData.get("ACTOR_NAME"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testSendNotificationInternal_SendsNotification() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String tokenUniqueId = randomAlphabetic(10);
    String parentUniqueId = randomAlphabetic(10);
    String serviceAccountIdentifier = randomAlphabetic(10);

    Map<String, String> templateData = Map.of("TEMPLATE_IDENTIFIER", "token_created");

    tokenNotificationUtils.sendNotificationInternal(accountIdentifier, serviceAccountIdentifier, parentUniqueId,
        tokenUniqueId, orgIdentifier, projectIdentifier, NotificationEvent.TOKEN_CREATED, "TOKEN_CREATED",
        templateData);

    ArgumentCaptor<NotificationTriggerRequest> requestCaptor =
        ArgumentCaptor.forClass(NotificationTriggerRequest.class);

    verify(notificationClient, times(1)).sendNotificationTrigger(requestCaptor.capture());

    NotificationTriggerRequest request = requestCaptor.getValue();
    assertEquals(serviceAccountIdentifier, request.getEntityIdentifier());
    assertEquals("SERVICE_ACCOUNT", request.getEventEntity());
    assertEquals("TOKEN_CREATED", request.getEvent());
    assertEquals(accountIdentifier, request.getAccountId());
    assertEquals(parentUniqueId, request.getParentUniqueId());
    assertEquals(orgIdentifier, request.getOrgId());
    assertEquals(projectIdentifier, request.getProjectId());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testSendNotificationInternal_RetriesOnFailure() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String tokenUniqueId = randomAlphabetic(10);
    String parentUniqueId = randomAlphabetic(10);
    String serviceAccountIdentifier = randomAlphabetic(10);

    Map<String, String> templateData = Map.of("TEMPLATE_IDENTIFIER", "token_created");

    when(notificationClient.sendNotificationTrigger(any()))
        .thenThrow(new RuntimeException("Runtime Exception"))
        .thenReturn(null);

    tokenNotificationUtils.sendNotificationInternal(accountIdentifier, serviceAccountIdentifier, parentUniqueId,
        tokenUniqueId, orgIdentifier, projectIdentifier, NotificationEvent.TOKEN_CREATED, "TOKEN_CREATED",
        templateData);

    verify(notificationClient, times(2)).sendNotificationTrigger(any());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testGetServiceAccountName_ReturnsName() {
    String parentIdentifier = randomAlphabetic(10);
    String serviceAccountName = randomAlphabetic(10);

    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId(randomAlphabetic(10)).build();
    ServiceAccountDTO serviceAccountDTO = ServiceAccountDTO.builder().name(serviceAccountName).build();

    when(serviceAccountService.getServiceAccountDTO(scopeInfo, parentIdentifier)).thenReturn(serviceAccountDTO);

    String result = tokenNotificationUtils.getServiceAccountName(scopeInfo, parentIdentifier);
    assertEquals(serviceAccountName, result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testGetServiceAccountName_ReturnsParentIdentifier_WhenFails() {
    String parentIdentifier = randomAlphabetic(10);

    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId(randomAlphabetic(10)).build();
    when(serviceAccountService.getServiceAccountDTO(any(ScopeInfo.class), eq(parentIdentifier)))
        .thenThrow(new RuntimeException("fail"));

    String result = tokenNotificationUtils.getServiceAccountName(scopeInfo, parentIdentifier);
    assertEquals(parentIdentifier, result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testGetAccountName_ReturnsName() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    AccountDTO accountDTO = AccountDTO.builder().name(TEST_ACCOUNT_NAME).build();

    Call<RestResponse<AccountDTO>> accountCall = mock(Call.class);
    when(accountClient.getAccountDTO(accountIdentifier)).thenReturn(accountCall);
    when(accountCall.clone()).thenReturn(accountCall);
    when(accountCall.execute()).thenReturn(Response.success(new RestResponse<>(accountDTO)));

    String result = tokenNotificationUtils.getAccountName(accountIdentifier);
    assertEquals(TEST_ACCOUNT_NAME, result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testGetAccountName_ReturnsAccountIdentifier_WhenFails() {
    String accountIdentifier = randomAlphabetic(10);
    when(accountClient.getAccountDTO(accountIdentifier)).thenThrow(new RuntimeException("fail"));

    String result = tokenNotificationUtils.getAccountName(accountIdentifier);
    assertEquals(accountIdentifier, result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testFormatEpochMillis_FormatsCorrectly() {
    Long epochMillis = 1735689600000L; // Jan 1, 2025 00:00:00 UTC
    String result = tokenNotificationUtils.formatEpochMillis(epochMillis);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals("Jan 01, 2025 12:00 AM Z", result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testFormatEpochMillis_NullReturnsEmpty() {
    assertEquals("", tokenNotificationUtils.formatEpochMillis(null));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testIsServiceAccountTokenNotificationEnabled_ReturnsFalse_WhenNotServiceAccount() {
    String accountIdentifier = randomAlphabetic(10);
    String tokenIdentifier = randomAlphabetic(10);

    boolean result =
        tokenNotificationUtils.isServiceAccountTokenNotificationEnabled(accountIdentifier, ApiKeyType.USER);

    assertFalse(result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testIsServiceAccountTokenNotificationEnabled_ReturnsTrue_WhenFlagEnabled() {
    String accountIdentifier = randomAlphabetic(10);
    String tokenIdentifier = randomAlphabetic(10);

    when(featureFlagService.isEnabled(FeatureName.PL_SERVICE_ACCOUNT_NOTIFICATION, accountIdentifier)).thenReturn(true);

    boolean result =
        tokenNotificationUtils.isServiceAccountTokenNotificationEnabled(accountIdentifier, ApiKeyType.SERVICE_ACCOUNT);

    assertTrue(result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testIsServiceAccountTokenNotificationEnabled_ReturnsFalse_WhenFlagDisabled() {
    String accountIdentifier = randomAlphabetic(10);
    String tokenIdentifier = randomAlphabetic(10);

    when(featureFlagService.isEnabled(FeatureName.PL_SERVICE_ACCOUNT_NOTIFICATION, accountIdentifier))
        .thenReturn(false);

    boolean result =
        tokenNotificationUtils.isServiceAccountTokenNotificationEnabled(accountIdentifier, ApiKeyType.SERVICE_ACCOUNT);

    assertFalse(result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testFormatDaysToHumanReadable_ConvertsCorrectly() {
    assertEquals("1 day", TokenNotificationUtils.formatDaysToHumanReadable(1));
    assertEquals("4 weeks", TokenNotificationUtils.formatDaysToHumanReadable(28));
  }

  private TokenDTO buildServiceAccountTokenDTO(String accountIdentifier, String parentIdentifier, String parentUniqueId,
      String tokenIdentifier, String tokenName, String apiKeyIdentifier, String username) {
    return TokenDTO.builder()
        .accountIdentifier(accountIdentifier)
        .identifier(tokenIdentifier)
        .name(tokenName)
        .parentIdentifier(parentIdentifier)
        .parentUniqueId(parentUniqueId)
        .apiKeyIdentifier(apiKeyIdentifier)
        .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
        .username(username)
        .uniqueId(randomAlphabetic(10))
        .validFrom(System.currentTimeMillis())
        .validTo(System.currentTimeMillis() + 86400000L)
        .build();
  }

  private void mockAccountClient(String accountIdentifier) throws IOException {
    AccountDTO accountDTO = AccountDTO.builder().name(TEST_ACCOUNT_NAME).build();
    Call<RestResponse<AccountDTO>> accountCall = mock(Call.class);
    when(accountClient.getAccountDTO(accountIdentifier)).thenReturn(accountCall);
    when(accountCall.clone()).thenReturn(accountCall);
    when(accountCall.execute()).thenReturn(Response.success(new RestResponse<>(accountDTO)));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testSendTokenNotification_WhenNotEnabled_DoesNotSendNotification() {
    String accountIdentifier = randomAlphabetic(10);
    TokenDTO token =
        TokenDTO.builder().accountIdentifier(accountIdentifier).apiKeyType(ApiKeyType.SERVICE_ACCOUNT).build();
    when(featureFlagService.isEnabled(eq(FeatureName.PL_SERVICE_ACCOUNT_NOTIFICATION), eq(accountIdentifier)))
        .thenReturn(false);

    tokenNotificationUtils.sendTokenNotification(token, NotificationEvent.TOKEN_CREATED, "TOKEN_CREATED", null);

    verify(notificationClient, never()).sendNotificationTrigger(any());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testSendTokenNotification_WhenEnabled_SendsNotificationWithCorrectData() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String serviceAccountIdentifier = randomAlphabetic(10);
    String parentUniqueId = randomAlphabetic(10);
    String tokenIdentifier = randomAlphabetic(10);
    String tokenName = randomAlphabetic(10);
    String apiKeyIdentifier = randomAlphabetic(10);
    String username = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    TokenDTO token = buildServiceAccountTokenDTO(accountIdentifier, serviceAccountIdentifier, parentUniqueId,
        tokenIdentifier, tokenName, apiKeyIdentifier, username);

    when(featureFlagService.isEnabled(FeatureName.PL_SERVICE_ACCOUNT_NOTIFICATION, accountIdentifier)).thenReturn(true);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .uniqueId(parentUniqueId)
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .build();
    when(scopeResolutionHelper.getScopeInfo(accountIdentifier, parentUniqueId)).thenReturn(scopeInfo);
    ServiceAccountDTO serviceAccountDTO = ServiceAccountDTO.builder().name(TEST_SERVICE_ACCOUNT_NAME).build();
    when(serviceAccountService.getServiceAccountDTO(any(ScopeInfo.class), eq(serviceAccountIdentifier)))
        .thenReturn(serviceAccountDTO);
    mockAccountClient(accountIdentifier);

    tokenNotificationUtils.sendTokenNotification(token, NotificationEvent.TOKEN_CREATED, "TOKEN_CREATED", null);

    ArgumentCaptor<NotificationTriggerRequest> requestCaptor =
        ArgumentCaptor.forClass(NotificationTriggerRequest.class);
    verify(notificationClient, times(1)).sendNotificationTrigger(requestCaptor.capture());

    NotificationTriggerRequest request = requestCaptor.getValue();
    assertEquals(serviceAccountIdentifier, request.getEntityIdentifier());
    assertEquals("SERVICE_ACCOUNT", request.getEventEntity());
    assertEquals("TOKEN_CREATED", request.getEvent());
    assertEquals(accountIdentifier, request.getAccountId());
    assertEquals(parentUniqueId, request.getParentUniqueId());
    assertEquals(orgIdentifier, request.getOrgId());
    assertEquals(projectIdentifier, request.getProjectId());

    Map<String, String> templateData = request.getTemplateDataMap();
    assertEquals("token_created", templateData.get("TEMPLATE_IDENTIFIER"));
    assertEquals(tokenName, templateData.get("TOKEN_NAME"));
    assertEquals(tokenIdentifier, templateData.get("TOKEN_IDENTIFIER"));
    assertEquals(serviceAccountIdentifier, templateData.get("PARENT_IDENTIFIER"));
    assertEquals(TEST_SERVICE_ACCOUNT_NAME, templateData.get("SERVICE_ACCOUNT_NAME"));
    assertEquals(TEST_ACCOUNT_NAME, templateData.get("ACCOUNT_NAME"));
    assertEquals(username, templateData.get("ACTOR_NAME"));
    assertEquals(orgIdentifier, templateData.get("ORG_IDENTIFIER"));
    assertEquals(projectIdentifier, templateData.get("PROJECT_IDENTIFIER"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testSendTokenNotification_WhenScopeResolutionFails_SendsNotificationWithEmptyOrgProject()
      throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String serviceAccountIdentifier = randomAlphabetic(10);
    String parentUniqueId = randomAlphabetic(10);
    String tokenIdentifier = randomAlphabetic(10);

    TokenDTO token = buildServiceAccountTokenDTO(accountIdentifier, serviceAccountIdentifier, parentUniqueId,
        tokenIdentifier, randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));

    when(featureFlagService.isEnabled(FeatureName.PL_SERVICE_ACCOUNT_NOTIFICATION, accountIdentifier)).thenReturn(true);
    when(scopeResolutionHelper.getScopeInfo(accountIdentifier, parentUniqueId))
        .thenThrow(new RuntimeException("scope resolution failed"));
    mockAccountClient(accountIdentifier);

    tokenNotificationUtils.sendTokenNotification(token, NotificationEvent.TOKEN_CREATED, "TOKEN_CREATED", null);

    ArgumentCaptor<NotificationTriggerRequest> requestCaptor =
        ArgumentCaptor.forClass(NotificationTriggerRequest.class);
    verify(notificationClient, times(1)).sendNotificationTrigger(requestCaptor.capture());

    NotificationTriggerRequest request = requestCaptor.getValue();
    assertEquals("", request.getOrgId());
    assertEquals("", request.getProjectId());
    assertEquals("NA", request.getTemplateDataMap().get("ORG_IDENTIFIER"));
    assertEquals("NA", request.getTemplateDataMap().get("PROJECT_IDENTIFIER"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testSendTokenNotification_WithAdditionalTemplateData_MergesIntoTemplateData() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String serviceAccountIdentifier = randomAlphabetic(10);
    String parentUniqueId = randomAlphabetic(10);

    TokenDTO token = buildServiceAccountTokenDTO(accountIdentifier, serviceAccountIdentifier, parentUniqueId,
        randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));

    when(featureFlagService.isEnabled(FeatureName.PL_SERVICE_ACCOUNT_NOTIFICATION, accountIdentifier)).thenReturn(true);
    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId(parentUniqueId).accountIdentifier(accountIdentifier).build();
    when(scopeResolutionHelper.getScopeInfo(accountIdentifier, parentUniqueId)).thenReturn(scopeInfo);
    ServiceAccountDTO serviceAccountDTO = ServiceAccountDTO.builder().name(TEST_SERVICE_ACCOUNT_NAME).build();
    when(serviceAccountService.getServiceAccountDTO(any(ScopeInfo.class), eq(serviceAccountIdentifier)))
        .thenReturn(serviceAccountDTO);
    mockAccountClient(accountIdentifier);

    Map<String, String> additionalData = new HashMap<>();
    additionalData.put("timeToExpire", "7");
    additionalData.put("DURATION", "1 week");

    tokenNotificationUtils.sendTokenNotification(
        token, NotificationEvent.TOKEN_ABOUT_TO_EXPIRE, "TOKEN_ABOUT_TO_EXPIRE_7", additionalData);

    ArgumentCaptor<NotificationTriggerRequest> requestCaptor =
        ArgumentCaptor.forClass(NotificationTriggerRequest.class);
    verify(notificationClient, times(1)).sendNotificationTrigger(requestCaptor.capture());

    Map<String, String> templateData = requestCaptor.getValue().getTemplateDataMap();
    assertEquals("token_about_to_expire", templateData.get("TEMPLATE_IDENTIFIER"));
    assertEquals("7", templateData.get("timeToExpire"));
    assertEquals("1 week", templateData.get("DURATION"));
    assertEquals("NA", templateData.get("ORG_IDENTIFIER"));
    assertEquals("NA", templateData.get("PROJECT_IDENTIFIER"));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testBuildBaseTemplateData_BlankOrgProject_UsesNAInTemplateData() {
    String accountIdentifier = randomAlphabetic(10);
    TokenDTO token = TokenDTO.builder()
                         .accountIdentifier(accountIdentifier)
                         .identifier(randomAlphabetic(10))
                         .name(randomAlphabetic(10))
                         .parentIdentifier(randomAlphabetic(10))
                         .apiKeyIdentifier(randomAlphabetic(10))
                         .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
                         .validFrom(System.currentTimeMillis())
                         .validTo(System.currentTimeMillis() + 86400000L)
                         .build();

    Map<String, String> templateData = tokenNotificationUtils.buildBaseTemplateData(
        token, "token_created", "", "   ", TEST_SERVICE_ACCOUNT_NAME, TEST_ACCOUNT_NAME);

    assertEquals("NA", templateData.get("ORG_IDENTIFIER"));
    assertEquals("NA", templateData.get("PROJECT_IDENTIFIER"));
  }
}

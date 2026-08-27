/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.iterator;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ABHISHEK_SINGH;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.entities.Token.TokenKeys;
import io.harness.ng.core.utils.TokenNotificationUtils;
import io.harness.notification.entities.NotificationEvent;
import io.harness.rule.Owner;
import io.harness.utils.ScopeResolutionHelper;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PL)
public class TokenExpiryAlertIteratorTest extends CategoryTest {
  private PersistenceIteratorFactory persistenceIteratorFactory;
  private MongoTemplate mongoTemplate;
  private TokenNotificationUtils tokenNotificationUtils;
  private ScopeResolutionHelper scopeResolutionHelper;
  private TokenExpiryAlertIterator tokenExpiryAlertIterator;

  @Before
  public void setup() throws Exception {
    persistenceIteratorFactory = mock(PersistenceIteratorFactory.class);
    mongoTemplate = mock(MongoTemplate.class);
    tokenNotificationUtils = mock(TokenNotificationUtils.class);
    scopeResolutionHelper = mock(ScopeResolutionHelper.class);

    tokenExpiryAlertIterator = new TokenExpiryAlertIterator();

    // Use reflection to set private fields
    setPrivateField(tokenExpiryAlertIterator, "persistenceIteratorFactory", persistenceIteratorFactory);
    setPrivateField(tokenExpiryAlertIterator, "mongoTemplate", mongoTemplate);
    setPrivateField(tokenExpiryAlertIterator, "tokenNotificationUtils", tokenNotificationUtils);
    setPrivateField(tokenExpiryAlertIterator, "scopeResolutionHelper", scopeResolutionHelper);
  }

  private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private Token buildToken(String accountIdentifier, String tokenIdentifier, Instant expiryTimestamp) {
    String parentUniqueId = randomAlphabetic(10);
    Token token = Token.builder()
                      .accountIdentifier(accountIdentifier)
                      .identifier(tokenIdentifier)
                      .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
                      .validUntil(Date.from(expiryTimestamp))
                      .validFrom(Instant.now())
                      .validTo(expiryTimestamp)
                      .parentUniqueId(parentUniqueId)
                      .build();

    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId(parentUniqueId).accountIdentifier(accountIdentifier).build();
    when(scopeResolutionHelper.getScopeInfo(accountIdentifier, parentUniqueId)).thenReturn(scopeInfo);

    return token;
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testCalculateDaysUntilExpiry_FiveDaysRemaining_ReturnsSevenDayThreshold() {
    long currentTime = System.currentTimeMillis();
    long expiryTime = currentTime + TimeUnit.DAYS.toMillis(5);

    int result = TokenExpiryAlertIterator.calculateDaysUntilExpiry(expiryTime, currentTime);

    // 5 days should match the 7-day threshold
    assertEquals(7, result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testCalculateDaysUntilExpiry_Exact7DaysRemaining() {
    long currentTime = System.currentTimeMillis();
    long expiryTime = currentTime + TimeUnit.DAYS.toMillis(7);

    int result = TokenExpiryAlertIterator.calculateDaysUntilExpiry(expiryTime, currentTime);

    assertEquals(7, result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testCalculateDaysUntilExpiry_ThirtyDaysRemaining_ReturnsMinusOne() {
    long currentTime = System.currentTimeMillis();
    long expiryTime = currentTime + TimeUnit.DAYS.toMillis(30);

    int result = TokenExpiryAlertIterator.calculateDaysUntilExpiry(expiryTime, currentTime);

    // Beyond 28 days, should return -1
    assertEquals(-1, result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testFindMatchingThreshold_ExactMatch_SevenDayThreshold() {
    int result = TokenExpiryAlertIterator.findMatchingThreshold(7);
    assertEquals(7, result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testFindMatchingThreshold_BetweenThresholds_ThreeDays_ReturnsSevenDayThreshold() {
    int result = TokenExpiryAlertIterator.findMatchingThreshold(3);
    // 3 days should return the next threshold >= 3, which is 7
    assertEquals(7, result);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testHandle_TokenNotificationEnabled_SendsNotification() {
    String accountIdentifier = randomAlphabetic(10);
    String tokenIdentifier = randomAlphabetic(10);
    Instant expiryTimestamp = Instant.now().plus(7, ChronoUnit.DAYS);

    Token token = buildToken(accountIdentifier, tokenIdentifier, expiryTimestamp);

    tokenExpiryAlertIterator.handle(token);

    String expectedPrefix = "TOKEN_ABOUT_TO_EXPIRE_7_" + expiryTimestamp.toEpochMilli();
    ArgumentCaptor<TokenDTO> tokenDTOCaptor = ArgumentCaptor.forClass(TokenDTO.class);
    verify(tokenNotificationUtils, times(1))
        .sendTokenNotification(
            tokenDTOCaptor.capture(), eq(NotificationEvent.TOKEN_ABOUT_TO_EXPIRE), eq(expectedPrefix), any(Map.class));

    TokenDTO capturedTokenDTO = tokenDTOCaptor.getValue();
    assertEquals(accountIdentifier, capturedTokenDTO.getAccountIdentifier());
    assertEquals(tokenIdentifier, capturedTokenDTO.getIdentifier());
    assertEquals(ApiKeyType.SERVICE_ACCOUNT, capturedTokenDTO.getApiKeyType());
    assertEquals(expiryTimestamp.toEpochMilli(), capturedTokenDTO.getValidTo().longValue());
    assertEquals(token.getParentUniqueId(), capturedTokenDTO.getParentUniqueId());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testHandle_NullValidUntil_DoesNotSendNotification() {
    String accountIdentifier = randomAlphabetic(10);
    String tokenIdentifier = randomAlphabetic(10);

    Token token = Token.builder()
                      .accountIdentifier(accountIdentifier)
                      .identifier(tokenIdentifier)
                      .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
                      .build();

    tokenExpiryAlertIterator.handle(token);

    verify(tokenNotificationUtils, never())
        .sendTokenNotification(any(TokenDTO.class), any(NotificationEvent.class), anyString(), any());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testHandle_TokenDoesNotMatchAnyThreshold_DoesNotSendNotification() {
    String accountIdentifier = randomAlphabetic(10);
    String tokenIdentifier = randomAlphabetic(10);
    // Token expiring in 30 days - beyond all thresholds
    Instant expiryTimestamp = Instant.now().plus(30, ChronoUnit.DAYS);

    Token token = buildToken(accountIdentifier, tokenIdentifier, expiryTimestamp);

    tokenExpiryAlertIterator.handle(token);

    verify(tokenNotificationUtils, never())
        .sendTokenNotification(any(TokenDTO.class), any(NotificationEvent.class), anyString(), any());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testHandle_SendNotificationsFails_ThrowsRuntimeException() {
    String accountIdentifier = randomAlphabetic(10);
    String tokenIdentifier = randomAlphabetic(10);
    Instant expiryTimestamp = Instant.now().plus(7, ChronoUnit.DAYS);

    Token token = buildToken(accountIdentifier, tokenIdentifier, expiryTimestamp);

    doThrow(new RuntimeException("Exception While Sending Notifications"))
        .when(tokenNotificationUtils)
        .sendTokenNotification(any(TokenDTO.class), any(NotificationEvent.class), anyString(), any());

    RuntimeException exception = assertThrows(RuntimeException.class, () -> tokenExpiryAlertIterator.handle(token));
    assertEquals("Failed to process token expiry alert for token: " + token.getIdentifier(), exception.getMessage());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testHandle_ValidUntilUsedForExpiry_SendsNotificationWithCorrectThreshold() {
    String accountIdentifier = randomAlphabetic(10);
    String tokenIdentifier = randomAlphabetic(10);
    // Create token with validUntil set to 14 days from now
    Instant expiryTimestamp = Instant.now().plus(14, ChronoUnit.DAYS);

    Token token = buildToken(accountIdentifier, tokenIdentifier, expiryTimestamp);

    tokenExpiryAlertIterator.handle(token);

    // Should use validUntil (14 days) for threshold calculation
    String expectedPrefix = "TOKEN_ABOUT_TO_EXPIRE_14_" + expiryTimestamp.toEpochMilli();
    verify(tokenNotificationUtils, times(1))
        .sendTokenNotification(
            any(TokenDTO.class), eq(NotificationEvent.TOKEN_ABOUT_TO_EXPIRE), eq(expectedPrefix), any(Map.class));
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void testGetFilterQuery_AddsCorrectCriteria() {
    long beforeFilterTime = System.currentTimeMillis();
    SpringFilterExpander filterExpander = tokenExpiryAlertIterator.getFilterQuery();
    Query query = new Query();

    filterExpander.filter(query);
    long afterFilterTime = System.currentTimeMillis();

    Document queryObject = query.getQueryObject();

    // Verify apiKeyType criteria
    assertEquals(true, queryObject.containsKey(TokenKeys.apiKeyType));
    assertEquals(ApiKeyType.SERVICE_ACCOUNT, queryObject.get(TokenKeys.apiKeyType));

    // Verify validUntil criteria with $gt and $lt operators
    assertEquals(true, queryObject.containsKey(TokenKeys.validUntil));
    Document validUntilDoc = (Document) queryObject.get(TokenKeys.validUntil);

    // $gt should be approximately "now"
    Date gtValue = validUntilDoc.getDate("$gt");
    assertEquals(true, gtValue.getTime() >= beforeFilterTime);
    assertEquals(true, gtValue.getTime() <= afterFilterTime);

    // $lt should be approximately "now + 28 days"
    Date ltValue = validUntilDoc.getDate("$lt");
    long expectedThresholdLow = beforeFilterTime + TimeUnit.DAYS.toMillis(28);
    long expectedThresholdHigh = afterFilterTime + TimeUnit.DAYS.toMillis(28);
    assertEquals(true, ltValue.getTime() >= expectedThresholdLow);
    assertEquals(true, ltValue.getTime() <= expectedThresholdHigh);
  }
}

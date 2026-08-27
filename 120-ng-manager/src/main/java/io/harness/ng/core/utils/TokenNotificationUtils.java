/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.entities.NotificationEntity;
import io.harness.notification.entities.NotificationEvent;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Centralised utility for building and sending token-related notification requests.
 * Used by both {@code TokenEventHandler} (outbox-driven CRUD events) and
 * {@code TokenExpiryAlertIterator} (iterator-driven about-to-expire alerts).
 */
@Slf4j
@Singleton
@OwnedBy(PL)
public class TokenNotificationUtils {
  private final FeatureFlagService featureFlagService;
  private final AccountClient accountClient;
  private final NotificationClient notificationClient;
  private final ServiceAccountService serviceAccountService;
  private final ScopeResolutionHelper scopeResolutionHelper;

  private static final int MAX_NOTIFICATION_RETRIES = 3;
  private static final long RETRY_DELAY_MILLIS = 5000;

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a z").withZone(ZoneOffset.UTC);

  // Sentinel value for "never expires" - Wed Dec 30 2099 18:30:00.000 UTC
  private static final long NEVER_EXPIRE_TIME_MILLIS = 4102338600000L;

  /** Display value for notification templates when org or project scope is not present. */
  private static final String DEFAULT_ORG_PROJECT = "NA";

  @Inject
  public TokenNotificationUtils(FeatureFlagService featureFlagService, AccountClient accountClient,
      NotificationClient notificationClient, ServiceAccountService serviceAccountService,
      ScopeResolutionHelper scopeResolutionHelper) {
    this.featureFlagService = featureFlagService;
    this.accountClient = accountClient;
    this.notificationClient = notificationClient;
    this.serviceAccountService = serviceAccountService;
    this.scopeResolutionHelper = scopeResolutionHelper;
  }

  /**
   * Checks whether notifications are enabled for the given service account token.
   * Notifications are only sent for {@link ApiKeyType#SERVICE_ACCOUNT} tokens
   * when the {@link FeatureName#PL_SERVICE_ACCOUNT_NOTIFICATION} feature flag is enabled.
   *
   * @param accountIdentifier The account identifier
   * @param apiKeyType The API key type
   * @return true if notifications should be sent, false otherwise
   */
  public boolean isServiceAccountTokenNotificationEnabled(String accountIdentifier, ApiKeyType apiKeyType) {
    if (apiKeyType != ApiKeyType.SERVICE_ACCOUNT) {
      return false;
    }
    return featureFlagService.isEnabled(FeatureName.PL_SERVICE_ACCOUNT_NOTIFICATION, accountIdentifier);
  }

  /**
   * Sends notification for token events (created, edited, rotated, deleted, expired, about-to-expire).
   * Called from TokenEventHandler (outbox-driven CRUD events) and TokenExpiryAlertIterator (about-to-expire alerts).
   *
   * @param token The TokenDTO object
   * @param event The notification event type
   * @param idempotencyPrefix The prefix for idempotency key
   * @param additionalTemplateData Additional template data to merge (can be null)
   */
  public void sendTokenNotification(
      TokenDTO token, NotificationEvent event, String idempotencyPrefix, Map<String, String> additionalTemplateData) {
    // Check if notifications are enabled for this service account token
    if (!isServiceAccountTokenNotificationEnabled(token.getAccountIdentifier(), token.getApiKeyType())) {
      return;
    }

    String templateIdentifier = getTemplateIdentifier(event);

    ScopeInfo scopeInfo = null;
    if (token.getParentUniqueId() != null) {
      try {
        scopeInfo = scopeResolutionHelper.getScopeInfo(token.getAccountIdentifier(), token.getParentUniqueId());
      } catch (Exception e) {
        log.warn("Failed to resolve scope for accountId: {}, parentUniqueId: {}", token.getAccountIdentifier(),
            token.getParentUniqueId(), e);
      }
    }
    String orgId = scopeInfo != null && scopeInfo.getOrgIdentifier() != null ? scopeInfo.getOrgIdentifier() : "";
    String projectId =
        scopeInfo != null && scopeInfo.getProjectIdentifier() != null ? scopeInfo.getProjectIdentifier() : "";

    String serviceAccountName = getServiceAccountName(scopeInfo, token.getParentIdentifier());
    String accountName = getAccountName(token.getAccountIdentifier());

    Map<String, String> templateData =
        buildBaseTemplateData(token, templateIdentifier, orgId, projectId, serviceAccountName, accountName);

    if (additionalTemplateData != null) {
      templateData.putAll(additionalTemplateData);
    }

    sendNotificationInternal(token.getAccountIdentifier(), token.getParentIdentifier(), token.getParentUniqueId(),
        token.getUniqueId(), orgId, projectId, event, idempotencyPrefix, templateData);
  }

  /**
   * Builds a {@link NotificationTriggerRequest} and sends it via the notification client.
   * Retries once after a 5-second delay on failure. Any exception is logged but not thrown,
   * so that callers (outbox handler / iterator) are never blocked by notification failures.
   */
  void sendNotificationInternal(String accountIdentifier, String serviceAccountIdentifier, String parentUniqueId,
      String tokenUniqueId, String orgIdentifier, String projectIdentifier, NotificationEvent event,
      String idempotencyPrefix, Map<String, String> templateData) {
    NotificationTriggerRequest notificationTriggerRequest =
        NotificationTriggerRequest.newBuilder()
            .setId(generateUuid())
            .setEntityIdentifier(serviceAccountIdentifier)
            .setEventEntity(NotificationEntity.SERVICE_ACCOUNT.name())
            .setEvent(event.name())
            .setAccountId(accountIdentifier)
            .setParentUniqueId(parentUniqueId)
            .setOrgId(orgIdentifier)
            .setProjectId(projectIdentifier)
            .putAllTemplateData(templateData)
            .build();

    for (int attempt = 0; attempt < MAX_NOTIFICATION_RETRIES; attempt++) {
      try {
        notificationClient.sendNotificationTrigger(notificationTriggerRequest);
        return;
      } catch (Exception e) {
        try {
          Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          log.error("Notification retry interrupted for event: {}, tokenUniqueId: {}", event.name(), tokenUniqueId);
          return;
        }
      }
    }
  }

  Map<String, String> buildBaseTemplateData(TokenDTO token, String templateIdentifier, String orgId, String projectId,
      String serviceAccountName, String accountName) {
    Map<String, String> templateData = new HashMap<>();
    templateData.put("TEMPLATE_IDENTIFIER", templateIdentifier);
    templateData.put("TOKEN_NAME", token.getName());
    templateData.put("TOKEN_IDENTIFIER", token.getIdentifier());
    templateData.put("PARENT_IDENTIFIER", token.getParentIdentifier());
    templateData.put("API_KEY_IDENTIFIER", token.getApiKeyIdentifier());
    templateData.put("API_KEY_TYPE", token.getApiKeyType() != null ? token.getApiKeyType().toString() : "");
    templateData.put("ACCOUNT_IDENTIFIER", token.getAccountIdentifier());
    templateData.put("ORG_IDENTIFIER", orgOrProjectDisplayValue(orgId));
    templateData.put("PROJECT_IDENTIFIER", orgOrProjectDisplayValue(projectId));
    String expiryDate =
        formatEpochMillis(token.getScheduledExpireTime() != null ? token.getScheduledExpireTime() : token.getValidTo());
    templateData.put("EXPIRY_DATE", expiryDate.isEmpty() ? "Never" : expiryDate);
    templateData.put("SERVICE_ACCOUNT_NAME", serviceAccountName);
    templateData.put("ACCOUNT_NAME", accountName);
    templateData.put("ACTOR_NAME", token.getUsername() != null ? token.getUsername() : "");
    return templateData;
  }

  /**
   * Maps a NotificationEvent to the corresponding notification template identifier.
   */
  String getTemplateIdentifier(NotificationEvent event) {
    switch (event) {
      case TOKEN_CREATED:
        return "token_created";
      case TOKEN_EDITED:
        return "token_edited";
      case TOKEN_ROTATED:
        return "token_rotated";
      case TOKEN_DELETED:
        return "token_deleted";
      case TOKEN_EXPIRED:
        return "token_expired";
      case TOKEN_ABOUT_TO_EXPIRE:
        return "token_about_to_expire";
      default:
        throw new InvalidArgumentsException("NotificationEvent is not a valid Token Event");
    }
  }

  /**
   * Looks up the service account name using ServiceAccountService.
   */
  String getServiceAccountName(ScopeInfo scopeInfo, String parentIdentifier) {
    try {
      ServiceAccountDTO serviceAccountDTO = serviceAccountService.getServiceAccountDTO(scopeInfo, parentIdentifier);
      return serviceAccountDTO != null ? serviceAccountDTO.getName() : parentIdentifier;
    } catch (Exception e) {
      log.warn("Failed to get service account name for parentId: {}", parentIdentifier, e);
      return parentIdentifier;
    }
  }

  /**
   * Looks up the account name using AccountClient.
   */
  String getAccountName(String accountIdentifier) {
    try {
      AccountDTO accountDTO = CGRestUtils.getResponse(accountClient.getAccountDTO(accountIdentifier));
      return accountDTO != null ? accountDTO.getName() : accountIdentifier;
    } catch (Exception e) {
      log.warn("Failed to get account name for accountId: {}", accountIdentifier, e);
      return accountIdentifier;
    }
  }

  /**
   * Converts epoch milliseconds to a human-readable date string (e.g. "Feb 13, 2026 10:30 AM UTC").
   * Returns an empty string when the input is null, 0, or equals the "never expires" sentinel value.
   */
  String formatEpochMillis(Long epochMillis) {
    if (epochMillis == null || epochMillis == 0 || epochMillis == NEVER_EXPIRE_TIME_MILLIS) {
      return "";
    }
    return DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
  }

  /** Template display value for org/project scope; blank becomes NA. */
  static String orgOrProjectDisplayValue(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_ORG_PROJECT;
    }
    return value;
  }

  /**
   * Converts a number of days into a human-readable duration string.
   * Examples: 28 → "4 weeks", 7 → "1 week", 1 → "1 day".
   */
  public static String formatDaysToHumanReadable(int days) {
    if (days <= 1) {
      return "1 day";
    }
    if (days < 7) {
      return days + " days";
    }
    int weeks = days / 7;
    return weeks + (weeks == 1 ? " week" : " weeks");
  }
}

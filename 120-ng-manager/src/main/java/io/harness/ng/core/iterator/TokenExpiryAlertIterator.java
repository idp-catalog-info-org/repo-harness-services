/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.iterator;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ng.config.TokenExpiryAlertIteratorConfig;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.entities.Token.TokenKeys;
import io.harness.ng.core.mapper.TokenDTOMapper;
import io.harness.ng.core.utils.TokenNotificationUtils;
import io.harness.notification.entities.NotificationEvent;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class TokenExpiryAlertIterator implements MongoPersistenceIterator.Handler<Token> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private TokenNotificationUtils tokenNotificationUtils;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  @Inject @Named("tokenExpiryAlertIteratorConfig") private TokenExpiryAlertIteratorConfig config;

  static final List<Integer> NOTIFICATION_THRESHOLDS = List.of(1, 7, 14, 21, 28);
  private static final String ITERATOR_NAME = "TokenExpiryAlert";
  private static final int BATCH_SIZE_MULTIPLY_FACTOR = 2;

  public void registerIterator() {
    if ("REDIS_BATCH".equalsIgnoreCase(config.getIteratorMode())) {
      registerRedisBatchIterator(config);
    } else {
      registerPumpIterator(config);
    }
  }

  private void registerRedisBatchIterator(TokenExpiryAlertIteratorConfig config) {
    int batchSize = config.getBatchSize();
    if (batchSize == 0) {
      batchSize = BATCH_SIZE_MULTIPLY_FACTOR * config.getThreadPoolSize();
    }

    PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions =
        PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
            .name(ITERATOR_NAME)
            .poolSize(config.getThreadPoolSize())
            .batchSize(batchSize)
            .lockTimeout(config.getRedisLockTimeoutSeconds())
            .interval(ofSeconds(config.getThreadPoolIntervalInSeconds()))
            .build();

    persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions, Token.class,
        MongoPersistenceIterator.<Token, SpringFilterExpander>builder()
            .clazz(Token.class)
            .fieldName(TokenKeys.tokenExpiryAlertNextIteration)
            .filterExpander(getFilterQuery())
            .targetInterval(ofSeconds(config.getTargetIntervalInSeconds()))
            .acceptableNoAlertDelay(ofMinutes(2))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate)));
  }

  private void registerPumpIterator(TokenExpiryAlertIteratorConfig config) {
    PersistenceIteratorFactory.PumpExecutorOptions executorOptions =
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(ITERATOR_NAME)
            .poolSize(config.getThreadPoolSize())
            .interval(ofSeconds(config.getThreadPoolIntervalInSeconds()))
            .build();

    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions, Token.class,
        MongoPersistenceIterator.<Token, SpringFilterExpander>builder()
            .clazz(Token.class)
            .fieldName(TokenKeys.tokenExpiryAlertNextIteration)
            .filterExpander(getFilterQuery())
            .targetInterval(ofSeconds(config.getTargetIntervalInSeconds()))
            .acceptableNoAlertDelay(ofMinutes(2))
            .acceptableExecutionTime(ofSeconds(30))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  SpringFilterExpander getFilterQuery() {
    return query -> {
      Date now = new Date(System.currentTimeMillis());
      Date threshold = Date.from(Instant.now().plus(28, ChronoUnit.DAYS));

      query.addCriteria(Criteria.where(TokenKeys.apiKeyType).is(ApiKeyType.SERVICE_ACCOUNT));
      // Adding the > now criteria for tokens which might have expired but not yet removed from the database
      query.addCriteria(Criteria.where(TokenKeys.validUntil).gt(now).lt(threshold));
    };
  }

  @Override
  public void handle(Token token) {
    try {
      // Use validUntil field to match the filter query criteria
      Date validUntil = token.getValidUntil();
      if (validUntil == null) {
        log.warn("Token {} has no validUntil date, skipping alert", token.getUuid());
        return;
      }

      int matchedThreshold = calculateDaysUntilExpiry(validUntil.getTime(), System.currentTimeMillis());
      if (matchedThreshold < 0) {
        log.debug("Token {} does not match any notification threshold, skipping alert", token.getIdentifier());
        return;
      }

      String idempotencyPrefix =
          "TOKEN_ABOUT_TO_EXPIRE_" + matchedThreshold + "_" + token.getExpiryTimestamp().toEpochMilli();

      ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(token.getAccountIdentifier(), token.getParentUniqueId());
      TokenDTO tokenDTO = TokenDTOMapper.getDTOFromToken(token, scopeInfo);

      Map<String, String> additionalTemplateData = Map.of("timeToExpire", String.valueOf(matchedThreshold), "DURATION",
          TokenNotificationUtils.formatDaysToHumanReadable(matchedThreshold));

      tokenNotificationUtils.sendTokenNotification(
          tokenDTO, NotificationEvent.TOKEN_ABOUT_TO_EXPIRE, idempotencyPrefix, additionalTemplateData);
    } catch (Exception e) {
      log.error("Failed to send token expiry notification for token: {}", token.getIdentifier(), e);
      throw new RuntimeException("Failed to process token expiry alert for token: " + token.getIdentifier(), e);
    }
  }

  /**
   * Calculates the number of days until expiry and maps it to the nearest matching
   * notification threshold from {@link #NOTIFICATION_THRESHOLDS}.
   * Returns the threshold value if the token expires within that threshold bucket,
   * or -1 if the token does not fall within any notification threshold.
   * Uses ceiling logic - partial days are rounded up (e.g., 28.5 days = 29 days).
   *
   * @param expiryTimestamp the token expiry time in epoch milliseconds
   * @param currentTimeMillis the current time in epoch milliseconds (externalized for testability)
   */
  static int calculateDaysUntilExpiry(long expiryTimestamp, long currentTimeMillis) {
    long diffMillis = expiryTimestamp - currentTimeMillis;
    if (diffMillis <= 0) {
      return -1;
    }

    // Calculate days and round up for partial days (ceiling)
    int day = (int) (diffMillis / (1000 * 60 * 60 * 24));
    if (diffMillis % (1000 * 60 * 60 * 24) != 0) {
      day += 1;
    }

    return findMatchingThreshold(day);
  }

  /**
   * Finds the nearest notification threshold that is >= the given day.
   * For example, if day=5 and thresholds are [1,7,14,21,28], returns 7.
   * If day=7, returns 7.
   * Returns -1 if the day exceeds all thresholds.
   */
  static int findMatchingThreshold(int day) {
    List<Integer> sortedThresholds = new ArrayList<>(NOTIFICATION_THRESHOLDS);
    Collections.sort(sortedThresholds);
    for (int threshold : sortedThresholds) {
      if (threshold >= day) {
        return threshold;
      }
    }
    return -1;
  }
}

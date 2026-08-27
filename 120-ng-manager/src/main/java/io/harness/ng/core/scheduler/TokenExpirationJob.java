/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.scheduler;

import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.events.TokenExpireEvent;
import io.harness.ng.core.mapper.TokenDTOMapper;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.TokenRepository;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class TokenExpirationJob implements Runnable {
  private final TokenRepository tokenRepository;
  private final OutboxService outboxService;
  private final TransactionTemplate outboxTransactionTemplate;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final ScopeInfoService scopeInfoService;
  private final PersistentLocker persistentLocker;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;

  private static final String LOCK_NAME = "AddTokenExpirationJobLock";

  @Inject
  public TokenExpirationJob(TokenRepository tokenRepository, OutboxService outboxService,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate outboxTransactionTemplate,
      ScopeInfoService scopeInfoService, PersistentLocker persistentLocker, PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    this.tokenRepository = tokenRepository;
    this.outboxService = outboxService;
    this.outboxTransactionTemplate = outboxTransactionTemplate;
    this.scopeInfoService = scopeInfoService;
    this.persistentLocker = persistentLocker;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
  }

  @Override
  public void run() {
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.debug("TokenExpirationJob: Could not acquire lock. Skipping execution.");
        return;
      }

      List<Token> expiredTokens = tokenRepository.findExpiredTokens(Instant.now());
      if (expiredTokens.isEmpty()) {
        return;
      }

      log.info("TokenExpirationJob: Found {} expired tokens", expiredTokens.size());

      for (Token token : expiredTokens) {
        try {
          processExpiredToken(token);
        } catch (Exception ex) {
          log.error("TokenExpirationJob: Failed to process expired token with ID {}", token.getUuid(), ex);
        }
      }
    } catch (Exception ex) {
      log.error("TokenExpirationJob: Failed while trying to acquire or run with lock", ex);
    }
  }

  @VisibleForTesting
  protected void processExpiredToken(Token token) {
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(token.getAccountIdentifier(), FeatureName.PL_USE_SCOPE_INFO_FOR_TOKEN_ENTITY)
        ? scopeInfoService.getScopeInfo(token.getAccountIdentifier(), Set.of(token.getParentUniqueId()))
              .get(token.getParentUniqueId())
              .get()
        : scopeInfoService.getScopeInfo(
              token.getAccountIdentifier(), token.getOrgIdentifier(), token.getProjectIdentifier());
    log.debug("TokenExpirationJob: Fetched ScopeInfo - account: {}, org: {}, project: {} | Token identifier: {}",
        scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(),
        token.getIdentifier());
    Failsafe.with(transactionRetryPolicy).get(() -> outboxTransactionTemplate.execute(status -> {
      tokenRepository.deleteById(token.getUuid());
      outboxService.save(new TokenExpireEvent(TokenDTOMapper.getDTOFromToken(token, scopeInfo)));
      log.info("TokenExpirationJob: Deleted expired token {} and saved outbox event", token.getIdentifier());
      return null;
    }));
  }
}

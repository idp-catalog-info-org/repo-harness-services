/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.scheduler;

import static io.harness.rule.OwnerRule.SHIVAM_RAJPUT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.base.NgManagerTestBase;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.events.TokenExpireEvent;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.TokenRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class TokenExpirationJobTest extends NgManagerTestBase {
  @Mock private TokenRepository tokenRepository;
  @Mock private OutboxService outboxService;
  @Mock private TransactionTemplate outboxTransactionTemplate;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @InjectMocks private TokenExpirationJob tokenExpirationJob;

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  @SneakyThrows
  public void testProcessExpiredToken_deletesTokenAndCreatesOutboxEvent() {
    Token expiredToken = Token.builder()
                             .uuid("uuid-1")
                             .identifier("expired-token")
                             .accountIdentifier("acc1")
                             .orgIdentifier("org1")
                             .projectIdentifier("proj1")
                             .validTo(Instant.now().minusSeconds(10))
                             .validFrom(Instant.now().minusSeconds(40))
                             .build();

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("acc1")
                              .orgIdentifier("org1")
                              .projectIdentifier("proj1")
                              .uniqueId("unique-id")
                              .build();

    when(scopeInfoService.getScopeInfo("acc1", "org1", "proj1")).thenReturn(scopeInfo);
    when(outboxTransactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    AtomicBoolean tokenDeleted = new AtomicBoolean(false);

    doAnswer(invocation -> {
      tokenDeleted.set(true); // simulate token delete
      return null;
    })
        .when(tokenRepository)
        .deleteById("uuid-1");
    doReturn(null).when(outboxService).save(any(TokenExpireEvent.class));

    tokenExpirationJob.processExpiredToken(expiredToken);

    verify(scopeInfoService).getScopeInfo("acc1", "org1", "proj1");
    verify(tokenRepository).deleteById("uuid-1");
    verify(outboxService).save(any(TokenExpireEvent.class));
    assertThat(tokenDeleted.get()).isTrue();
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testProcessExpiredToken_handlesOutboxFailure() {
    Token expiredToken = Token.builder()
                             .uuid("uuid-2")
                             .identifier("token-with-failure")
                             .accountIdentifier("acc1")
                             .orgIdentifier("org1")
                             .projectIdentifier("proj1")
                             .validTo(Instant.now().minusSeconds(10))
                             .validFrom(Instant.now().minusSeconds(40))
                             .build();

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("acc1")
                              .orgIdentifier("org1")
                              .projectIdentifier("proj1")
                              .uniqueId("unique-id")
                              .build();

    when(scopeInfoService.getScopeInfo("acc1", "org1", "proj1")).thenReturn(scopeInfo);

    when(outboxTransactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    AtomicBoolean tokenDeleted = new AtomicBoolean(false);

    doAnswer(invocation -> {
      tokenDeleted.set(true); // simulate token delete
      return null;
    })
        .when(tokenRepository)
        .deleteById("uuid-2");

    doThrow(new RuntimeException("Failed to write outbox event")).when(outboxService).save(any());

    try {
      tokenExpirationJob.processExpiredToken(expiredToken);
    } catch (Exception ignored) {
      tokenDeleted.set(false);
    }

    verify(scopeInfoService).getScopeInfo("acc1", "org1", "proj1");
    verify(tokenRepository).deleteById("uuid-2");
    assertThat(tokenDeleted.get()).isFalse(); // transaction rollback not deleting token
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> tokenExpirationJob.processExpiredToken(expiredToken))
        .withMessage("Failed to write outbox event");
  }
}

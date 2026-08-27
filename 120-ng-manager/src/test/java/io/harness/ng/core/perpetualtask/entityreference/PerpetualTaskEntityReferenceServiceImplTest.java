/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.perpetualtask.entityreference;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.perpetualtask.entityreference.PerpetualTaskBundleRefreshCallback;
import io.harness.perpetualtask.entityreference.PerpetualTaskEntityReference;
import io.harness.perpetualtask.entityreference.PerpetualTaskReferenceEntityType;
import io.harness.repositories.perpetualtask.PerpetualTaskEntityReferenceRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.inject.Injector;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PerpetualTaskEntityReferenceServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ENTITY_REF = "account.connector";
  private static final String PT_ID_1 = "pt-1";
  private static final String PT_ID_2 = "pt-2";

  @Mock private PerpetualTaskEntityReferenceRepository referenceRepository;
  @Mock private Injector injector;
  @Mock private DelegateServiceGrpcClient delegateServiceGrpcClient;
  @Mock private ExecutorService refreshExecutor;

  private PerpetualTaskEntityReferenceServiceImpl service;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    RateLimiter rateLimiter = RateLimiter.of("test",
        RateLimiterConfig.custom()
            .limitForPeriod(1000)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(1))
            .build());
    Retry retry = Retry.of("test", RetryConfig.custom().maxAttempts(1).build());
    service = new PerpetualTaskEntityReferenceServiceImpl(
        referenceRepository, injector, delegateServiceGrpcClient, refreshExecutor, rateLimiter, retry);
    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    })
        .when(refreshExecutor)
        .submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = OwnerRule.LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void onEntityUpdatedShouldRefreshEachUniquePerpetualTaskAsynchronously() {
    PerpetualTaskBundleRefreshCallback callback1 = mock(PerpetualTaskBundleRefreshCallback.class);
    PerpetualTaskBundleRefreshCallback callback2 = mock(PerpetualTaskBundleRefreshCallback.class);
    when(referenceRepository.findByAccountIdAndReferredEntityTypeAndReferredEntityRef(
             ACCOUNT_ID, PerpetualTaskReferenceEntityType.CONNECTOR, ENTITY_REF))
        .thenReturn(List.of(PerpetualTaskEntityReference.builder()
                                .accountId(ACCOUNT_ID)
                                .perpetualTaskId(PT_ID_1)
                                .referredEntityType(PerpetualTaskReferenceEntityType.CONNECTOR)
                                .referredEntityRef(ENTITY_REF)
                                .callback(callback1)
                                .build(),
            PerpetualTaskEntityReference.builder()
                .accountId(ACCOUNT_ID)
                .perpetualTaskId(PT_ID_2)
                .referredEntityType(PerpetualTaskReferenceEntityType.CONNECTOR)
                .referredEntityRef(ENTITY_REF)
                .callback(callback2)
                .build()));

    service.onEntityUpdated(ACCOUNT_ID, PerpetualTaskReferenceEntityType.CONNECTOR, ENTITY_REF, UPDATE_ACTION);

    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(refreshExecutor, times(2)).submit(runnableCaptor.capture());
    verify(injector, times(2)).injectMembers(any());
    verify(callback1, times(1)).refresh();
    verify(callback2, times(1)).refresh();
  }

  @Test
  @Owner(developers = OwnerRule.LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void onEntityDeletedShouldDeletePerpetualTaskAndUnregisterReferences() {
    PerpetualTaskBundleRefreshCallback callback = mock(PerpetualTaskBundleRefreshCallback.class);
    when(referenceRepository.findByAccountIdAndReferredEntityTypeAndReferredEntityRef(
             ACCOUNT_ID, PerpetualTaskReferenceEntityType.CONNECTOR, ENTITY_REF))
        .thenReturn(List.of(PerpetualTaskEntityReference.builder()
                                .accountId(ACCOUNT_ID)
                                .perpetualTaskId(PT_ID_1)
                                .referredEntityType(PerpetualTaskReferenceEntityType.CONNECTOR)
                                .referredEntityRef(ENTITY_REF)
                                .callback(callback)
                                .build()));

    service.onEntityUpdated(ACCOUNT_ID, PerpetualTaskReferenceEntityType.CONNECTOR, ENTITY_REF, DELETE_ACTION);

    verify(refreshExecutor, times(1)).submit(any(Runnable.class));
    verify(delegateServiceGrpcClient, times(1)).deletePerpetualTask(any(), any());
    verify(referenceRepository, times(1)).deleteByPerpetualTaskId(PT_ID_1);
    verifyNoInteractions(callback);
  }
}

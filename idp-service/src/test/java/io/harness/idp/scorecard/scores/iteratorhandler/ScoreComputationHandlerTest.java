/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.iteratorhandler;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.scorecard.scores.service.ScoreComputerService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ScoreComputationHandlerTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks private ScoreComputationHandler handler;
  @Mock private ScoreComputerService scoreComputerService;
  @Mock private AccountClient accountClient;
  @Mock private IdpIteratorMetricRecorder idpIteratorMetricRecorder;
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;

  private static final String TEST_ACCOUNT = "test-account";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleSuccessWithAsyncEnabled() throws Exception {
    NamespaceEntity entity = NamespaceEntity.builder().accountIdentifier(TEST_ACCOUNT).build();
    Call<RestResponse<Boolean>> call = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(eq(FeatureName.IDP_ASYNC_SCORE_COMPUTATION.name()), eq(TEST_ACCOUNT)))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(RestResponse.Builder.aRestResponse().withResource(true).build()));

    handler.handle(entity);

    verify(scoreComputerService, times(1)).computeScoresAsync(eq(TEST_ACCOUNT), any(), any());
    verify(idpIteratorMetricRecorder, times(1)).recordSuccess("IDPScoreCardScoreCalculator", TEST_ACCOUNT);
    verify(idpIteratorMetricRecorder, times(0)).recordFailure(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleSuccessWithAsyncDisabled() throws Exception {
    NamespaceEntity entity = NamespaceEntity.builder().accountIdentifier(TEST_ACCOUNT).build();
    Call<RestResponse<Boolean>> call = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(eq(FeatureName.IDP_ASYNC_SCORE_COMPUTATION.name()), eq(TEST_ACCOUNT)))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(RestResponse.Builder.aRestResponse().withResource(false).build()));

    handler.handle(entity);

    verify(scoreComputerService, times(1)).computeScores(eq(TEST_ACCOUNT), any(), any());
    verify(idpIteratorMetricRecorder, times(1)).recordSuccess("IDPScoreCardScoreCalculator", TEST_ACCOUNT);
    verify(idpIteratorMetricRecorder, times(0)).recordFailure(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleFailure() throws Exception {
    NamespaceEntity entity = NamespaceEntity.builder().accountIdentifier(TEST_ACCOUNT).build();
    Call<RestResponse<Boolean>> call = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(eq(FeatureName.IDP_ASYNC_SCORE_COMPUTATION.name()), eq(TEST_ACCOUNT)))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(RestResponse.Builder.aRestResponse().withResource(false).build()));
    doThrow(new RuntimeException("Computation failed")).when(scoreComputerService).computeScores(any(), any(), any());

    try {
      handler.handle(entity);
    } catch (RuntimeException e) {
      // Expected
    }

    verify(idpIteratorMetricRecorder, times(1)).recordFailure("IDPScoreCardScoreCalculator", TEST_ACCOUNT);
    verify(idpIteratorMetricRecorder, times(0)).recordSuccess(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    handler.registerIterators(IteratorConfig.builder().threadPoolCount(2).targetIntervalInSeconds(60).build());
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(ScoreComputationHandler.class), any());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}

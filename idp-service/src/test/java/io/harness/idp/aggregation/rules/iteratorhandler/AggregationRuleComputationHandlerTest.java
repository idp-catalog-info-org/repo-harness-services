/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.iteratorhandler;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.helper.AggregationRulesHelper;
import io.harness.idp.aggregation.rules.service.AggregationRulesService;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.pojos.IteratorConfig;
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

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class AggregationRuleComputationHandlerTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks private AggregationRulesComputationHandler handler;
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock AggregationRulesService aggregationRulesService;
  @Mock AggregationRulesHelper aggregationRulesHelper;
  @Mock IdpIteratorMetricRecorder idpIteratorMetricRecorder;

  private static final String TEST_ACCOUNT = "test-account";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandle() {
    AggregationRuleEntity aggregationRuleEntity =
        AggregationRuleEntity.builder().accountIdentifier(TEST_ACCOUNT).build();
    doNothing().when(aggregationRulesService).compute(aggregationRuleEntity);
    doNothing()
        .when(aggregationRulesHelper)
        .updateEntity(aggregationRuleEntity, AggregationRuleEntity.ComputedStatus.SUCCESS, null);
    handler.handle(aggregationRuleEntity);
    verify(aggregationRulesService).compute(aggregationRuleEntity);
    verify(idpIteratorMetricRecorder, times(1)).recordSuccess("AggregationRulesComputationHandler", TEST_ACCOUNT);
    verify(idpIteratorMetricRecorder, times(0)).recordFailure(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleFailure() {
    AggregationRuleEntity aggregationRuleEntity =
        AggregationRuleEntity.builder().accountIdentifier(TEST_ACCOUNT).build();
    RuntimeException exception = new RuntimeException("Computation failed");
    doThrow(exception).when(aggregationRulesService).compute(aggregationRuleEntity);
    try {
      handler.handle(aggregationRuleEntity);
    } catch (RuntimeException e) {
      // Expected
    }
    verify(aggregationRulesService).compute(aggregationRuleEntity);
    verify(idpIteratorMetricRecorder, times(1)).recordFailure("AggregationRulesComputationHandler", TEST_ACCOUNT);
    verify(idpIteratorMetricRecorder, times(0)).recordSuccess(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    handler.registerIterators(IteratorConfig.builder().build());
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(AggregationRulesComputationHandler.class), any());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}

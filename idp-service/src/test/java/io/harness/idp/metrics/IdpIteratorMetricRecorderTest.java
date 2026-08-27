/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.metrics;

import static io.harness.idp.metrics.IdpIteratorMetricRecorder.ITERATOR_FAILURE_METRIC;
import static io.harness.idp.metrics.IdpIteratorMetricRecorder.ITERATOR_SUCCESS_METRIC;
import static io.harness.rule.OwnerRule.ANKUR;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
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
public class IdpIteratorMetricRecorderTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks private IdpIteratorMetricRecorder recorder;
  @Mock private MetricService metricService;

  private static final String TEST_ITERATOR = "TestIterator";
  private static final String TEST_ACCOUNT = "test-account-123";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordSuccessWithValidAccount() {
    recorder.recordSuccess(TEST_ITERATOR, TEST_ACCOUNT);
    verify(metricService, times(1)).incCounter(ITERATOR_SUCCESS_METRIC);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordFailureWithValidAccount() {
    recorder.recordFailure(TEST_ITERATOR, TEST_ACCOUNT);
    verify(metricService, times(1)).incCounter(ITERATOR_FAILURE_METRIC);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordSuccessWithNullAccountIdentifier() {
    recorder.recordSuccess(TEST_ITERATOR, null);
    verify(metricService, times(1)).incCounter(ITERATOR_SUCCESS_METRIC);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordSuccessWithEmptyAccountIdentifier() {
    recorder.recordSuccess(TEST_ITERATOR, "");
    verify(metricService, times(1)).incCounter(ITERATOR_SUCCESS_METRIC);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRecordFailureWithNullAccountIdentifier() {
    recorder.recordFailure(TEST_ITERATOR, null);
    verify(metricService, times(1)).incCounter(ITERATOR_FAILURE_METRIC);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testMultipleConsecutiveRecordings() {
    recorder.recordSuccess(TEST_ITERATOR, TEST_ACCOUNT);
    recorder.recordFailure(TEST_ITERATOR, TEST_ACCOUNT);
    recorder.recordSuccess(TEST_ITERATOR, "another-account");
    recorder.recordFailure(TEST_ITERATOR, null);

    verify(metricService, times(2)).incCounter(ITERATOR_SUCCESS_METRIC);
    verify(metricService, times(2)).incCounter(ITERATOR_FAILURE_METRIC);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}

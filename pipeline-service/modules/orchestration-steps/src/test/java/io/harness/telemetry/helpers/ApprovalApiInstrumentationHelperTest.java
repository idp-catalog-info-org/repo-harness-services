/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.rule.OwnerRule.RISHABH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ApprovalApiInstrumentationHelperTest extends CategoryTest {
  private static final String ACCOUNT = "account";
  private static final String ORG = "org";
  private static final String PROJECT = "project";
  private static final String EXECUTION_ID = "execution_id";
  @InjectMocks ApprovalApiInstrumentationHelper instrumentationHelper;
  @Mock TelemetryReporter telemetryReporter;
  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    Reflect.on(instrumentationHelper).set("telemetryReporter", telemetryReporter);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testSuccessEvent() {
    instrumentationHelper.sendApprovalApiEvent(
        ACCOUNT, ORG, PROJECT, EXECUTION_ID, ApprovalApiInstrumentationHelper.SUCCESS, null);
    verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFailureEvent() {
    instrumentationHelper.sendApprovalApiEvent(ACCOUNT, ORG, PROJECT, EXECUTION_ID,
        ApprovalApiInstrumentationHelper.FAILURE, ApprovalApiInstrumentationHelper.MULTIPLE_APPROVALS_FOUND);
    verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), any(), any(), any(), any());
  }
}

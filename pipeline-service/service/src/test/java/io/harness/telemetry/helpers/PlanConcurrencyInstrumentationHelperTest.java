/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PlanConcurrencyInstrumentationHelperTest extends CategoryTest {
  @InjectMocks PlanConcurrencyInstrumentationHelper helper;
  @Mock TelemetryReporter telemetryReporter;

  private static final String ACCOUNT = "acc";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void configChangeEventCarriesIdentifierValueAndScope() {
    helper.sendConcurrencyConfigChangeEvent(ACCOUNT, null, null, "pipeline_execution_concurrency_mode", "PerProject");
    ArgumentCaptor<HashMap> captor = ArgumentCaptor.forClass(HashMap.class);
    verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), captor.capture(), any(), any(), any());
    HashMap<String, Object> props = captor.getValue();
    assertThat(props.get(PlanConcurrencyInstrumentationHelper.SETTING_IDENTIFIER))
        .isEqualTo("pipeline_execution_concurrency_mode");
    assertThat(props.get(PlanConcurrencyInstrumentationHelper.NEW_VALUE)).isEqualTo("PerProject");
    // Account-scoped setting (no org/project) -> scope "account".
    assertThat(props.get(PlanConcurrencyInstrumentationHelper.SCOPE)).isEqualTo("account");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void configChangeEventReportsProjectScope() {
    helper.sendConcurrencyConfigChangeEvent(ACCOUNT, "org", "proj", "project_execution_concurrency_limit", "50");
    ArgumentCaptor<HashMap> captor = ArgumentCaptor.forClass(HashMap.class);
    verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), captor.capture(), any(), any(), any());
    HashMap<String, Object> props = captor.getValue();
    assertThat(props.get(PlanConcurrencyInstrumentationHelper.SCOPE)).isEqualTo("project");
  }
}

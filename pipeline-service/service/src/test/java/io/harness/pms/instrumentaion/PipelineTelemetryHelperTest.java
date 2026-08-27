/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.instrumentaion;

import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.ACCOUNT_NAME;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.HARNESS_PIPELINE_ANNOTATIONS_USED;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.ORG_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PIPELINE_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PLAN_EXECUTION_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PROJECT_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.STAGE_EXECUTION_ID;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.pms.annotations.CreateAnnotationsRequest;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineTelemetryHelperTest extends CategoryTest {
  @Mock TelemetryReporter telemetryReporter;
  @Mock AccountService accountService;
  @Mock Executor executor;
  @Mock PlanExecutionService planExecutionService;
  @InjectMocks PipelineTelemetryHelper pipelineTelemetryHelper;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    })
        .when(executor)
        .execute(any(Runnable.class));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSendTelemetryEventInternal() {
    ArgumentCaptor<HashMap> propertiesCaptor = ArgumentCaptor.forClass(HashMap.class);
    ArgumentCaptor<String> eventNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(AccountDTO.builder().name("accountName").build()).when(accountService).getAccount("accountId");
    pipelineTelemetryHelper.sendTelemetryEventInternal("eventName", "accountId", new HashMap<>());

    verify(telemetryReporter, times(1))
        .sendTrackEvent(eventNameCaptor.capture(), any(), accountIdCaptor.capture(), propertiesCaptor.capture(), any(),
            any(), any());

    assertEquals(eventNameCaptor.getValue(), "eventName");
    assertEquals(accountIdCaptor.getValue(), "accountId");
    HashMap<String, Object> properties = propertiesCaptor.getValue();
    assertEquals(properties.get(ACCOUNT_NAME), "accountName");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSendHarnessAnnotationsUsageTelemetry() {
    ArgumentCaptor<HashMap> propertiesCaptor = ArgumentCaptor.forClass(HashMap.class);
    ArgumentCaptor<String> eventNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(AccountDTO.builder().name("accountName").build()).when(accountService).getAccount("accountId");

    CreateAnnotationsRequest request = CreateAnnotationsRequest.builder()
                                           .orgId("org1")
                                           .projectId("proj1")
                                           .pipelineId("pipe1")
                                           .planExecutionId("plan1")
                                           .stageExecutionId("stage1")
                                           .annotations(Collections.emptyList())
                                           .build();

    pipelineTelemetryHelper.sendHarnessAnnotationsUsageTelemetry("accountId", request);

    verify(telemetryReporter, times(1))
        .sendTrackEvent(eventNameCaptor.capture(), any(), accountIdCaptor.capture(), propertiesCaptor.capture(), any(),
            any(), any());

    assertEquals(HARNESS_PIPELINE_ANNOTATIONS_USED, eventNameCaptor.getValue());
    assertEquals("accountId", accountIdCaptor.getValue());
    HashMap<String, Object> properties = propertiesCaptor.getValue();
    assertEquals("plan1", properties.get(PLAN_EXECUTION_ID));
    assertEquals("org1", properties.get(ORG_ID));
    assertEquals("proj1", properties.get(PROJECT_ID));
    assertEquals("pipe1", properties.get(PIPELINE_ID));
    assertEquals("stage1", properties.get(STAGE_EXECUTION_ID));
    assertEquals("accountName", properties.get(ACCOUNT_NAME));
  }
}

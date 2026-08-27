/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.instrumentaion;

import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.ACCOUNT_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.ACCOUNT_NAME;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.HARNESS_PIPELINE_ANNOTATIONS_USED;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.INTERRUPT_TYPE;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.ORG_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.ORG_IDENTIFIER;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PIPELINE_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PIPELINE_INTERRUPT_EVENT;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PLAN_EXECUTION_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PROJECT_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PROJECT_IDENTIFIER;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.STAGE_EXECUTION_ID;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.pms.annotations.CreateAnnotationsRequest;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PipelineTelemetryHelper {
  @Inject TelemetryReporter telemetryReporter;
  @Inject AccountService accountService;
  @Inject @Named("TelemetrySenderExecutor") Executor executor;

  @Inject private PlanExecutionService planExecutionService;

  public void sendTelemetryEventWithAccountName(
      String eventName, String accountId, HashMap<String, Object> properties) {
    executor.execute(() -> sendTelemetryEventInternal(eventName, accountId, properties));
  }

  protected void sendTelemetryEventInternal(String eventName, String accountId, HashMap<String, Object> properties) {
    AccountDTO accountDTO = accountService.getAccount(accountId);
    String accountName = accountDTO.getName();
    properties.put(ACCOUNT_NAME, accountName);
    properties.put(ACCOUNT_ID, accountId);
    telemetryReporter.sendTrackEvent(eventName, null, accountId, properties, Collections.singletonMap(AMPLITUDE, true),
        Category.GLOBAL, TelemetryOption.builder().sendForCommunity(false).build());
  }

  /**
   * Sends telemetry event when a pipeline is interrupted.
   *
   * @param interruptPackage
   */
  public void sendInterruptTelemetryEvent(InterruptPackage interruptPackage) {
    try {
      PlanExecution planExecution = planExecutionService.getWithFieldsIncludedFromAnalytics(
          interruptPackage.getPlanExecutionId(), Set.of(PlanExecutionKeys.ambiance));
      HashMap<String, Object> propertiesMap = new HashMap<>();
      Ambiance ambiance = planExecution.getAmbiance();
      propertiesMap.put(PROJECT_IDENTIFIER, AmbianceUtils.getProjectIdentifier(ambiance));
      propertiesMap.put(ORG_IDENTIFIER, AmbianceUtils.getOrgIdentifier(ambiance));
      propertiesMap.put(PLAN_EXECUTION_ID, interruptPackage.getPlanExecutionId());
      propertiesMap.put(PIPELINE_ID, AmbianceUtils.getPipelineIdentifier(ambiance));
      propertiesMap.put(INTERRUPT_TYPE, interruptPackage.getInterruptType());
      sendTelemetryEventWithAccountName(PIPELINE_INTERRUPT_EVENT, AmbianceUtils.getAccountId(ambiance), propertiesMap);
    } catch (Exception e) {
      log.error("Failed to send the telemetry event for the Interrupt for PlanExecutionId: {} with Error: {}",
          interruptPackage.getPlanExecutionId(), e.getMessage());
    }
  }

  /**
   * Sends telemetry when Harness pipeline annotations were successfully processed for an execution.
   *
   * @param accountId account identifier
   * @param request create-annotations request (scope, pipeline, stage ids)
   */
  public void sendHarnessAnnotationsUsageTelemetry(String accountId, CreateAnnotationsRequest request) {
    try {
      HashMap<String, Object> properties = new HashMap<>();
      properties.put(PLAN_EXECUTION_ID, request.getPlanExecutionId());
      properties.put(ORG_ID, request.getOrgId());
      properties.put(PROJECT_ID, request.getProjectId());
      properties.put(PIPELINE_ID, request.getPipelineId());
      properties.put(STAGE_EXECUTION_ID, request.getStageExecutionId());
      executor.execute(() -> {
        try {
          sendTelemetryEventInternal(HARNESS_PIPELINE_ANNOTATIONS_USED, accountId, properties);
        } catch (Exception e) {
          log.error("Failed to send Harness annotations usage telemetry for planExecutionId: {}",
              request.getPlanExecutionId(), e);
        }
      });
    } catch (Exception e) {
      log.error("Failed to schedule Harness annotations usage telemetry for planExecutionId: {}",
          request.getPlanExecutionId(), e);
    }
  }
}

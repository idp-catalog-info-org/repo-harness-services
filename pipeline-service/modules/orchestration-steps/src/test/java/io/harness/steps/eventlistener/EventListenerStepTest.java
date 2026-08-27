/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.eventlistener;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.GitXWebhookResponse;
import io.harness.steps.eventlistener.beans.EventListenerStepInstanceStatus;
import io.harness.steps.eventlistener.beans.EventListenerStepResponseData;
import io.harness.steps.eventlistener.entities.EventListenerStepInstance;
import io.harness.steps.executable.LogBaseUrlProvider;
import io.harness.tasks.ResponseData;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;
import io.harness.utils.AmbianceScopeResolutionHelper;
import io.harness.webhook.utils.GitxWebhookUtils;

import com.google.common.collect.ImmutableMap;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDC)
@RunWith(MockitoJUnitRunner.class)
public class EventListenerStepTest extends CategoryTest {
  @Mock private EventListenerStepInstanceService eventListenerStepInstanceService;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private LogBaseUrlProvider logBaseUrlProvider;
  @Mock private GitxWebhookUtils gitxWebhookUtils;
  @Mock private AmbianceScopeResolutionHelper scopeResolutionHelper;
  @InjectMocks private EventlistenerStep eventlistenerStep;
  private ILogStreamingStepClient logStreamingStepClient;

  private static final String STATUS = "status";

  @Before
  public void setup() {
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testExecuteSync() {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(EventListenerStepParameters.builder()
                      .webhookIdentifier(ParameterField.createValueField("account.webhookId"))
                      .successCriteria(ParameterField.createValueField("true == true"))
                      .failureCriteria(ParameterField.createValueField("false == true"))
                      .build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId").build();
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier("accountId").uniqueId("uniqueId").build();
    when(scopeResolutionHelper.getScopeInfo(ambiance)).thenReturn(scopeInfo);
    when(gitxWebhookUtils.getWebhook("accountId", null, null, "webhookId")).thenReturn(new GitXWebhookResponse());
    when(eventListenerStepInstanceService.save(any()))
        .thenReturn(EventListenerStepInstance.fromStepParameters(ambiance, stepElementParameters, scopeInfo));
    AsyncExecutableResponse response = eventlistenerStep.executeAsync(ambiance, stepElementParameters, null, null);
    verify(eventListenerStepInstanceService).save(any());
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testSyncResponseSuccess() {
    String eventId = UUID.randomUUID().toString();
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId").build();

    EventListenerStepInstance instance = EventListenerStepInstance.builder()
                                             .accountIdentifier("accountId")
                                             .webhookIdentifier("account.webhookId")
                                             .build();
    instance.setStatus(EventListenerStepInstanceStatus.SUCCEEDED);
    when(eventListenerStepInstanceService.get(anyString())).thenReturn(instance);
    ResponseData responseData = EventListenerStepResponseData.builder()
                                    .instanceId(UUID.randomUUID().toString())
                                    .eventCorrelationId(eventId)
                                    .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(EventListenerStepParameters.builder()
                      .webhookIdentifier(ParameterField.createValueField("account.webhookId"))
                      .successCriteria(ParameterField.createValueField("true == true"))
                      .build())
            .build();
    when(scopeResolutionHelper.getScopeInfo(ambiance))
        .thenReturn(ScopeInfo.builder().accountIdentifier("accountId").uniqueId("uniqueId").build());
    StepResponse stepResponse =
        eventlistenerStep.handleAsyncResponse(ambiance, stepElementParameters, ImmutableMap.of("xyz", responseData));
    verify(logStreamingStepClient).closeStream(any());
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getFailureInfo()).isNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(2);
    assertThat(stepResponse.getStepOutcomes().stream().findFirst().orElseThrow(IllegalStateException::new).getName())
        .isEqualTo("output");
    assertThat(stepResponse.getStepOutcomes().stream().findFirst().orElseThrow(IllegalStateException::new).getOutcome())
        .isEqualTo(EventListenerStepOutcome.builder()
                       .webhookIdentifier("account.webhookId")
                       .eventCorrelationId(eventId)
                       .outputVariables(ImmutableMap.of())
                       .build());
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testAbort() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    eventlistenerStep.handleAbort(ambiance, null, null, false);
    verify(eventListenerStepInstanceService).abortByNodeExecutionId(any());
    verify(logStreamingStepClient).closeStream(any());
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetStepExecutionTelemetryEventDTO() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(EventListenerStepParameters.builder()
                      .webhookIdentifier(ParameterField.createValueField("webhookId"))
                      .successCriteria(ParameterField.createValueField("true == true"))
                      .build())
            .build();

    StepExecutionTelemetryEventDTO stepExecutionTelemetryEventDTO =
        eventlistenerStep.getStepExecutionTelemetryEventDTO(ambiance, stepElementParameters);

    assertThat(stepExecutionTelemetryEventDTO.getStepType()).isEqualTo(EventlistenerStep.STEP_TYPE.getType());
  }
}

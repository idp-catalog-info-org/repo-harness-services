/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.custom;

import static io.harness.eraro.ErrorCode.APPROVAL_REJECTION;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.exception.ApprovalStepNGException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.custom.CustomApprovalOutcome;
import io.harness.steps.approval.step.custom.CustomApprovalSpecParameters;
import io.harness.steps.approval.step.custom.CustomApprovalStep;
import io.harness.steps.approval.step.custom.IrregularApprovalInstanceHandler;
import io.harness.steps.approval.step.custom.beans.CustomApprovalResponseData;
import io.harness.steps.approval.step.custom.beans.CustomApprovalTicketNG;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstanceService;
import io.harness.steps.approval.step.entities.CustomApprovalInstance;
import io.harness.steps.executable.LogBaseUrlProvider;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;
import io.harness.tasks.ResponseData;
import io.harness.telemetry.helpers.ApprovalInstrumentationHelper;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;
import io.harness.telemetry.helpers.StepsInstrumentationHelper;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.core.timeout.Timeout;

import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDC)
@RunWith(MockitoJUnitRunner.class)
public class CustomApprovalStepTest extends CategoryTest {
  @Mock ApprovalInstanceService approvalInstanceService;
  @Mock private IrregularApprovalInstanceHandler irregularApprovalInstanceHandler;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  private static final String STATUS = "status";
  @Mock ExecutorService dashboardExecutorService;
  @Mock private ApprovalInstrumentationHelper instrumentationHelper;
  @Mock private StepsInstrumentationHelper stepsInstrumentationHelper;
  @Mock private LogBaseUrlProvider logBaseUrlProvider;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @InjectMocks private CustomApprovalStep customApprovalStep;
  private ILogStreamingStepClient logStreamingStepClient;

  @Before
  public void setup() {
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
    lenient()
        .when(scopeResolutionHelper.getScopeInfo(nullable(String.class), nullable(String.class)))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier("accId")
                        .orgIdentifier("orgId")
                        .projectIdentifier("projId")
                        .uniqueId("uniqueId")
                        .build());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testExecuteSync() {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .type(ApprovalType.CUSTOM_APPROVAL.name())
            .spec(CustomApprovalSpecParameters.builder()
                      .shellType(ShellType.Bash)
                      .source(ShellScriptSourceWrapper.builder()
                                  .spec(ShellScriptInlineSource.builder()
                                            .script(ParameterField.createValueField("echo 'HELLO WORLD!'"))
                                            .build())
                                  .build())
                      .scriptTimeout(ParameterField.createValueField(Timeout.fromString("10m")))
                      .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                      .build())
            .timeout(ParameterField.createValueField("1d"))
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    when(approvalInstanceService.save(any()))
        .thenReturn(CustomApprovalInstance.fromStepParameters(ambiance, stepElementParameters));

    AsyncExecutableResponse response = customApprovalStep.executeAsync(ambiance, stepElementParameters, null, null);
    ArgumentCaptor<Long> argumentCaptorForStreamTimeout = ArgumentCaptor.forClass(Long.class);
    verify(logStreamingStepClient, times(1))
        .openStream(eq(ShellScriptTaskNG.COMMAND_UNIT), argumentCaptorForStreamTimeout.capture());
    assertThat(argumentCaptorForStreamTimeout.getValue())
        .isCloseTo(Duration.ofDays(1).getSeconds(), offset(Duration.of(1, ChronoUnit.MINUTES).toSeconds()));
    verify(approvalInstanceService).save(any());
    verify(irregularApprovalInstanceHandler).wakeup();
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testSyncResponseSuccess() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    ApprovalInstance instance = CustomApprovalInstance.builder().build();
    instance.setStatus(ApprovalStatus.APPROVED);
    when(approvalInstanceService.get(anyString())).thenReturn(instance);
    ResponseData responseData =
        CustomApprovalResponseData.builder()
            .instanceId(UUID.randomUUID().toString())
            .ticket(CustomApprovalTicketNG.builder().fields(ImmutableMap.of(STATUS, "APPROVED")).build())
            .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .type(ApprovalType.CUSTOM_APPROVAL.name())
            .spec(CustomApprovalSpecParameters.builder()
                      .shellType(ShellType.Bash)
                      .source(ShellScriptSourceWrapper.builder()
                                  .spec(ShellScriptInlineSource.builder()
                                            .script(ParameterField.createValueField("echo 'HELLO WORLD!'"))
                                            .build())
                                  .build())
                      .scriptTimeout(ParameterField.createValueField(Timeout.fromString("10m")))
                      .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                      .build())
            .build();

    StepResponse stepResponse =
        customApprovalStep.handleAsyncResponse(ambiance, stepElementParameters, ImmutableMap.of("xyz", responseData));
    verify(logStreamingStepClient).closeAllOpenStreamsWithPrefix(any());
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getFailureInfo()).isNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    assertThat(stepResponse.getStepOutcomes().stream().findFirst().orElseThrow(IllegalStateException::new).getName())
        .isEqualTo("output");
    assertThat(stepResponse.getStepOutcomes().stream().findFirst().orElseThrow(IllegalStateException::new).getOutcome())
        .isEqualTo(CustomApprovalOutcome.builder().outputVariables(ImmutableMap.of(STATUS, "APPROVED")).build());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testSyncResponseFailure() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    ApprovalInstance instance = CustomApprovalInstance.builder().build();
    instance.setStatus(ApprovalStatus.FAILED);
    instance.setErrorMessage("Custom Approval has no output fields. At least one output field must be set");
    when(approvalInstanceService.get(anyString())).thenReturn(instance);
    ResponseData responseData = CustomApprovalResponseData.builder().instanceId(UUID.randomUUID().toString()).build();

    assertThatThrownBy(
        () -> customApprovalStep.handleAsyncResponse(ambiance, null, ImmutableMap.of("xyz", responseData)))
        .isInstanceOf(ApprovalStepNGException.class)
        .hasMessage("Custom Approval has no output fields. At least one output field must be set");
    verify(logStreamingStepClient).closeAllOpenStreamsWithPrefix(any());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testAbort() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    customApprovalStep.handleAbort(ambiance, null, null, false);
    verify(approvalInstanceService).abortByNodeExecutionId(any());
    verify(logStreamingStepClient).closeAllOpenStreamsWithPrefix(any());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testAsyncResponseRejected() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    ApprovalInstance instance = CustomApprovalInstance.builder().build();
    instance.setStatus(ApprovalStatus.REJECTED);
    when(approvalInstanceService.get(anyString())).thenReturn(instance);
    ResponseData responseData =
        CustomApprovalResponseData.builder()
            .instanceId(UUID.randomUUID().toString())
            .ticket(CustomApprovalTicketNG.builder().fields(ImmutableMap.of(STATUS, "REJECTED")).build())
            .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .type(ApprovalType.CUSTOM_APPROVAL.name())
            .spec(CustomApprovalSpecParameters.builder()
                      .shellType(ShellType.Bash)
                      .source(ShellScriptSourceWrapper.builder()
                                  .spec(ShellScriptInlineSource.builder()
                                            .script(ParameterField.createValueField("echo 'HELLO WORLD!'"))
                                            .build())
                                  .build())
                      .scriptTimeout(ParameterField.createValueField(Timeout.fromString("10m")))
                      .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                      .build())
            .build();

    StepResponse stepResponse =
        customApprovalStep.handleAsyncResponse(ambiance, stepElementParameters, ImmutableMap.of("xyz", responseData));
    verify(logStreamingStepClient).closeAllOpenStreamsWithPrefix(any());
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.APPROVAL_REJECTED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getFailureTypesList())
        .containsExactly(FailureType.APPROVAL_REJECTION);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo("Approval Step has been Rejected");
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getCode()).isEqualTo(APPROVAL_REJECTION.name());
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    assertThat(stepResponse.getStepOutcomes().stream().findFirst().orElseThrow(IllegalStateException::new).getName())
        .isEqualTo("output");
    assertThat(stepResponse.getStepOutcomes().stream().findFirst().orElseThrow(IllegalStateException::new).getOutcome())
        .isEqualTo(CustomApprovalOutcome.builder().outputVariables(ImmutableMap.of(STATUS, "REJECTED")).build());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetStepExecutionTelemetryEventDTO() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .type(ApprovalType.CUSTOM_APPROVAL.name())
            .spec(CustomApprovalSpecParameters.builder()
                      .shellType(ShellType.Bash)
                      .source(ShellScriptSourceWrapper.builder()
                                  .spec(ShellScriptInlineSource.builder()
                                            .script(ParameterField.createValueField("echo 'HELLO WORLD!'"))
                                            .build())
                                  .build())
                      .scriptTimeout(ParameterField.createValueField(Timeout.fromString("10m")))
                      .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                      .build())
            .build();

    StepExecutionTelemetryEventDTO stepExecutionTelemetryEventDTO =
        customApprovalStep.getStepExecutionTelemetryEventDTO(ambiance, stepElementParameters);

    assertThat(stepExecutionTelemetryEventDTO.getStepType()).isEqualTo(customApprovalStep.STEP_TYPE.getType());
  }
}

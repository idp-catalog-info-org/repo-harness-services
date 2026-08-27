/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.plancreator.steps.TaskSelectorYamlUtils.ORIGIN_DEFAULT;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.vivekveman;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.oidc.helper.OIDCContextHelper;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ShellExecutionData;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.executable.LogBaseUrlProvider;
import io.harness.steps.workloadidentity.StepIdentityHelper;
import io.harness.utils.LogOutcome;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.joor.Reflect;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PIPELINE)
public class ShellScriptStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private KryoSerializer kryoSerializer;
  @Mock private StepHelper stepHelper;
  @Mock private ShellScriptHelperServiceOld shellScriptHelperServiceOld;
  @Mock private ShellScriptHelperService shellScriptHelperService;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private LogBaseUrlProvider logBaseUrlProvider;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private OIDCContextHelper oidcContextHelper;
  @Mock private StepIdentityHelper stepIdentityHelper;
  @Mock LogStreamingStepClientImpl logClient;
  @InjectMocks private ShellScriptStep shellScriptStep;
  private ILogStreamingStepClient logStreamingStepClient;

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
        .setMetadata(ExecutionMetadata.newBuilder()
                         .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                         .setPipelineIdentifier("pipelineIdentifier")
                         .build())
        .build();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testHandleTaskResult_enrichesWithLogUrl_onSuccess() throws Exception {
    when(logBaseUrlProvider.getBaseUrl(any())).thenReturn("https://logs.harness.io");
    Ambiance ambiance = buildAmbiance();

    ShellScriptStepParameters stepParameters =
        ShellScriptStepParameters.infoBuilder().shellType(ShellType.Bash).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    ExecuteCommandResponse executeCommandResponse =
        ExecuteCommandResponse.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .commandExecutionData(ShellExecutionData.builder().sweepingOutputEnvVariables(new HashMap<>()).build())
            .build();
    ShellScriptTaskResponseNG successResponse = ShellScriptTaskResponseNG.builder()
                                                    .status(CommandExecutionStatus.SUCCESS)
                                                    .executeCommandResponse(executeCommandResponse)
                                                    .build();

    StepResponse stepResponse =
        shellScriptStep.handleTaskResult(ambiance, stepElementParameters, () -> successResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    StepResponse.StepOutcome logOutcome = stepResponse.getStepOutcomes()
                                              .stream()
                                              .filter(o -> OutputExpressionConstants.LOG.equals(o.getName()))
                                              .findFirst()
                                              .orElse(null);
    assertThat(logOutcome).isNotNull();
    assertThat(logOutcome.getOutcome()).isInstanceOf(LogOutcome.class);

    String expectedKey = "accountId:accId/orgId:orgId/projectId:projId/pipelineId:pipelineIdentifier/runSequence:0";
    String expectedUrl =
        String.format(StepUtils.LOG_SERVICE_DOWNLOAD_LOG_URL, "https://logs.harness.io", "accId", expectedKey);
    assertThat(((LogOutcome) logOutcome.getOutcome()).getUrl()).isEqualTo(expectedUrl);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testHandleTaskResult_enrichesWithLogUrl_onFailure() throws Exception {
    when(logBaseUrlProvider.getBaseUrl(any())).thenReturn("https://logs.harness.io");
    Ambiance ambiance = buildAmbiance();

    ShellScriptStepParameters stepParameters =
        ShellScriptStepParameters.infoBuilder().shellType(ShellType.Bash).build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).identifier("testIdentifier").build();

    ExecuteCommandResponse executeCommandResponse =
        ExecuteCommandResponse.builder()
            .status(CommandExecutionStatus.FAILURE)
            .commandExecutionData(ShellExecutionData.builder().sweepingOutputEnvVariables(new HashMap<>()).build())
            .build();
    ShellScriptTaskResponseNG failResponse = ShellScriptTaskResponseNG.builder()
                                                 .status(CommandExecutionStatus.FAILURE)
                                                 .errorMessage("err")
                                                 .executeCommandResponse(executeCommandResponse)
                                                 .build();

    StepResponse stepResponse = shellScriptStep.handleTaskResult(ambiance, stepElementParameters, () -> failResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    StepResponse.StepOutcome logOutcome = stepResponse.getStepOutcomes()
                                              .stream()
                                              .filter(o -> OutputExpressionConstants.LOG.equals(o.getName()))
                                              .findFirst()
                                              .orElse(null);
    assertThat(logOutcome).isNotNull();
    assertThat(logOutcome.getOutcome()).isInstanceOf(LogOutcome.class);

    String expectedKey = "accountId:accId/orgId:orgId/projectId:projId/pipelineId:pipelineIdentifier/runSequence:0";
    String expectedUrl =
        String.format(StepUtils.LOG_SERVICE_DOWNLOAD_LOG_URL, "https://logs.harness.io", "accId", expectedKey);
    assertThat(((LogOutcome) logOutcome.getOutcome()).getUrl()).isEqualTo(expectedUrl);
  }

  private AutoCloseable mocks;
  @Before
  public void setup() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logClient);
    when(pmsFeatureFlagHelper.isEnabled(any(String.class), any(FeatureName.class))).thenReturn(false);
    when(pmsFeatureFlagService.isEnabled(any(String.class), any(FeatureName.class))).thenReturn(false);

    // Set parent class fields using reflection
    Reflect.on(shellScriptStep).set("pmsFeatureFlagHelper", pmsFeatureFlagHelper);
    Reflect.on(shellScriptStep).set("oidcContextHelper", oidcContextHelper);
    Reflect.on(shellScriptStep).set("featureFlagService", pmsFeatureFlagService);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testObtainTaskOld() {
    Ambiance ambiance = buildAmbiance();
    ShellScriptStepParameters stepParameters =
        ShellScriptStepParameters.infoBuilder().shellType(ShellType.Bash).build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("45m")).build();
    ShellScriptTaskParametersNG taskParametersNG = ShellScriptTaskParametersNG.builder().script("echo hello").build();
    doReturn(taskParametersNG)
        .when(shellScriptHelperServiceOld)
        .buildShellScriptTaskParametersNG(ambiance, stepParameters, null, null);

    TaskRequest taskRequest = shellScriptStep.obtainTask(ambiance, stepElementParameters, null);
    ArgumentCaptor<Long> argumentCaptorForStreamTimeout = ArgumentCaptor.forClass(Long.class);
    verify(logClient, times(1))
        .openStream(eq(ShellScriptTaskNG.COMMAND_UNIT), argumentCaptorForStreamTimeout.capture());
    assertThat(argumentCaptorForStreamTimeout.getValue())
        .isCloseTo(
            Duration.of(45, ChronoUnit.MINUTES).toSeconds(), offset(Duration.of(1, ChronoUnit.MINUTES).toSeconds()));
    assertThat(taskRequest.getDelegateTaskRequest().getRequest().getDetails().getExecutionTimeout().getSeconds())
        .isEqualTo(2700);
    assertThat(taskRequest.getDelegateTaskRequest().getLogKeysList())
        .containsExactly("accountId:accId/orgId:orgId/projectId:projId/pipelineId:pipelineIdentifier/"
            + "runSequence:0-commandUnit:Execute");
    assertThat(taskRequest).isNotNull();
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testObtainTask() {
    Ambiance ambiance = buildAmbiance();
    ShellScriptStepParametersV0 stepParameters =
        ShellScriptStepParametersV0.infoBuilder().shellType(ShellType.Bash).build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("45m")).build();
    ShellScriptTaskParametersNG taskParametersNG = ShellScriptTaskParametersNG.builder().script("echo hello").build();
    doReturn(taskParametersNG)
        .when(shellScriptHelperService)
        .buildShellScriptTaskParametersNG(ambiance, stepParameters, "45m", null, null);

    TaskRequest taskRequest = shellScriptStep.obtainTask(ambiance, stepElementParameters, null);
    ArgumentCaptor<Long> argumentCaptorForStreamTimeout = ArgumentCaptor.forClass(Long.class);
    verify(logClient, times(1))
        .openStream(eq(ShellScriptTaskNG.COMMAND_UNIT), argumentCaptorForStreamTimeout.capture());
    assertThat(argumentCaptorForStreamTimeout.getValue())
        .isCloseTo(
            Duration.of(45, ChronoUnit.MINUTES).toSeconds(), offset(Duration.of(1, ChronoUnit.MINUTES).toSeconds()));
    assertThat(taskRequest.getDelegateTaskRequest().getRequest().getDetails().getExecutionTimeout().getSeconds())
        .isEqualTo(2700);
    assertThat(taskRequest.getDelegateTaskRequest().getLogKeysList())
        .containsExactly("accountId:accId/orgId:orgId/projectId:projId/pipelineId:pipelineIdentifier/"
            + "runSequence:0-commandUnit:Execute");
    assertThat(taskRequest).isNotNull();
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testObtainTaskForPowerShellOld() {
    Ambiance ambiance = buildAmbiance();
    ShellScriptStepParameters stepParameters =
        ShellScriptStepParameters.infoBuilder().shellType(ShellType.PowerShell).build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("45m")).build();
    ShellScriptTaskParametersNG taskParametersNG =
        ShellScriptTaskParametersNG.builder().script("Write-Host hello").build();
    doReturn(taskParametersNG)
        .when(shellScriptHelperServiceOld)
        .buildShellScriptTaskParametersNG(ambiance, stepParameters, null, null);
    TaskRequest taskRequest = shellScriptStep.obtainTask(ambiance, stepElementParameters, null);
    ArgumentCaptor<Long> argumentCaptorForStreamTimeout = ArgumentCaptor.forClass(Long.class);
    verify(logClient, times(1))
        .openStream(eq(ShellScriptTaskNG.COMMAND_UNIT), argumentCaptorForStreamTimeout.capture());
    assertThat(new HashSet<>(taskRequest.getDelegateTaskRequest().getLogKeysList()))
        .containsExactlyInAnyOrder("accountId:accId/orgId:orgId/projectId:projId/pipelineId:pipelineIdentifier/"
                + "runSequence:0-commandUnit:Initialize",
            "accountId:accId/orgId:orgId/projectId:projId/pipelineId:pipelineIdentifier/"
                + "runSequence:0-commandUnit:Execute");
    assertThat(taskRequest).isNotNull();
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testObtainTaskForPowerShell() {
    Ambiance ambiance = buildAmbiance();
    ShellScriptStepParametersV0 stepParameters =
        ShellScriptStepParametersV0.infoBuilder().shellType(ShellType.PowerShell).build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("45m")).build();
    ShellScriptTaskParametersNG taskParametersNG =
        ShellScriptTaskParametersNG.builder().script("Write-Host hello").build();
    doReturn(taskParametersNG)
        .when(shellScriptHelperService)
        .buildShellScriptTaskParametersNG(ambiance, stepParameters, "45m", null, null);
    TaskRequest taskRequest = shellScriptStep.obtainTask(ambiance, stepElementParameters, null);
    ArgumentCaptor<Long> argumentCaptorForStreamTimeout = ArgumentCaptor.forClass(Long.class);
    verify(logClient, times(1))
        .openStream(eq(ShellScriptTaskNG.COMMAND_UNIT), argumentCaptorForStreamTimeout.capture());
    assertThat(new HashSet<>(taskRequest.getDelegateTaskRequest().getLogKeysList()))
        .containsExactlyInAnyOrder("accountId:accId/orgId:orgId/projectId:projId/pipelineId:pipelineIdentifier/"
                + "runSequence:0-commandUnit:Initialize",
            "accountId:accId/orgId:orgId/projectId:projId/pipelineId:pipelineIdentifier/"
                + "runSequence:0-commandUnit:Execute");
    assertThat(taskRequest).isNotNull();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testObtainTaskSkipsTokenGenerationWhenNoIdentitiesConfigured() {
    Ambiance ambiance = buildAmbiance();
    ShellScriptStepParametersV0 stepParameters =
        ShellScriptStepParametersV0.infoBuilder().shellType(ShellType.Bash).build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("45m")).build();
    ShellScriptTaskParametersNG taskParametersNG = ShellScriptTaskParametersNG.builder().script("echo hello").build();

    when(stepIdentityHelper.resolveIdentityTokens(ambiance)).thenReturn(Collections.emptyMap());
    doReturn(taskParametersNG)
        .when(shellScriptHelperService)
        .buildShellScriptTaskParametersNG(ambiance, stepParameters, "45m", null, Collections.emptyMap());

    TaskRequest taskRequest = shellScriptStep.obtainTask(ambiance, stepElementParameters, null);

    assertThat(taskRequest).isNotNull();
    verify(shellScriptHelperService, times(1))
        .buildShellScriptTaskParametersNG(ambiance, stepParameters, "45m", null, Collections.emptyMap());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testObtainTaskGeneratesIdentityTokensWhenIdentitiesPresent() {
    Ambiance ambiance = buildAmbiance();
    ShellScriptStepParametersV0 stepParameters =
        ShellScriptStepParametersV0.infoBuilder().shellType(ShellType.Bash).build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("45m")).build();
    ShellScriptTaskParametersNG taskParametersNG = ShellScriptTaskParametersNG.builder().script("echo hello").build();
    Map<String, String> identityTokens = new HashMap<>();
    identityTokens.put("AWS_ID_TOKEN", "id-token-value");

    when(stepIdentityHelper.resolveIdentityTokens(ambiance)).thenReturn(identityTokens);
    doReturn(taskParametersNG)
        .when(shellScriptHelperService)
        .buildShellScriptTaskParametersNG(ambiance, stepParameters, "45m", null, identityTokens);

    TaskRequest taskRequest = shellScriptStep.obtainTask(ambiance, stepElementParameters, null);

    assertThat(taskRequest).isNotNull();
    verify(stepIdentityHelper, times(1)).resolveIdentityTokens(ambiance);
    verify(shellScriptHelperService, times(1))
        .buildShellScriptTaskParametersNG(ambiance, stepParameters, "45m", null, identityTokens);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testObtainTaskSkipsWorkloadIdentityWhenDisabled() {
    Ambiance ambiance = buildAmbiance();
    ShellScriptStepParametersV0 stepParameters =
        ShellScriptStepParametersV0.infoBuilder().shellType(ShellType.Bash).build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("45m")).build();
    ShellScriptTaskParametersNG taskParametersNG = ShellScriptTaskParametersNG.builder().script("echo hello").build();

    when(stepIdentityHelper.resolveIdentityTokens(ambiance)).thenReturn(Collections.emptyMap());
    doReturn(taskParametersNG)
        .when(shellScriptHelperService)
        .buildShellScriptTaskParametersNG(ambiance, stepParameters, "45m", null, Collections.emptyMap());

    TaskRequest taskRequest = shellScriptStep.obtainTask(ambiance, stepElementParameters, null);

    assertThat(taskRequest).isNotNull();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testHandleTaskResultForFailedTask() throws Exception {
    Ambiance ambiance = buildAmbiance();
    Map<String, Object> outputVariables = new HashMap<>();
    ShellScriptStepParameters stepParameters = ShellScriptStepParameters.infoBuilder()
                                                   .outputVariables(outputVariables)
                                                   .shellType(ShellType.PowerShell)
                                                   .outputAlias(OutputAlias.builder().build())
                                                   .build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().identifier("shellScriptStepId").spec(stepParameters).build();
    Map<String, String> envVariables = new HashMap<>();
    ExecuteCommandResponse executeCommandResponse =
        ExecuteCommandResponse.builder()
            .status(CommandExecutionStatus.FAILURE)
            .commandExecutionData(ShellExecutionData.builder().sweepingOutputEnvVariables(envVariables).build())
            .build();
    ShellScriptTaskResponseNG taskResponseNG = ShellScriptTaskResponseNG.builder()
                                                   .status(CommandExecutionStatus.FAILURE)
                                                   .errorMessage("Failed")
                                                   .executeCommandResponse(executeCommandResponse)
                                                   .build();

    StepResponse stepResponse = shellScriptStep.handleTaskResult(ambiance, stepElementParameters, () -> taskResponseNG);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo("Failed");

    verify(logClient, times(1)).closeStream("Initialize");
    verify(logClient, times(1)).closeStream("Execute");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHandleTaskResultForFailedTaskWithExitCode() throws Exception {
    Ambiance ambiance = buildAmbiance();
    Map<String, Object> outputVariables = new HashMap<>();
    ShellScriptStepParameters stepParameters = ShellScriptStepParameters.infoBuilder()
                                                   .outputVariables(outputVariables)
                                                   .shellType(ShellType.PowerShell)
                                                   .outputAlias(OutputAlias.builder().build())
                                                   .build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().identifier("shellScriptStepId").spec(stepParameters).build();
    Map<String, String> envVariables = new HashMap<>();
    ExecuteCommandResponse executeCommandResponse =
        ExecuteCommandResponse.builder()
            .status(CommandExecutionStatus.FAILURE)
            .commandExecutionData(
                ShellExecutionData.builder().sweepingOutputEnvVariables(envVariables).exitCode(1).build())
            .build();
    ShellScriptTaskResponseNG taskResponseNG = ShellScriptTaskResponseNG.builder()
                                                   .status(CommandExecutionStatus.FAILURE)
                                                   .errorMessage("Failed")
                                                   .executeCommandResponse(executeCommandResponse)
                                                   .build();

    StepResponse stepResponse = shellScriptStep.handleTaskResult(ambiance, stepElementParameters, () -> taskResponseNG);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo("Failed");

    StepResponse.StepOutcome[] stepOutcomes = stepResponse.getStepOutcomes().toArray(new StepResponse.StepOutcome[0]);
    ShellScriptOutcome outcome = (ShellScriptOutcome) stepOutcomes[0].getOutcome();
    assertThat(outcome.getExitCode()).isEqualTo("1");
    verify(logClient, times(1)).closeStream("Initialize");
    verify(logClient, times(1)).closeStream("Execute");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testHandleTaskResultForSuccessTask() throws Exception {
    Ambiance ambiance = buildAmbiance();
    Map<String, Object> outputVariables = new HashMap<>();
    ShellScriptStepParameters stepParameters = ShellScriptStepParameters.infoBuilder()
                                                   .outputVariables(outputVariables)
                                                   .shellType(ShellType.Bash)
                                                   .outputAlias(OutputAlias.builder().build())
                                                   .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();
    Map<String, String> envVariables = new HashMap<>();

    ExecuteCommandResponse executeCommandResponse =
        ExecuteCommandResponse.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .commandExecutionData(ShellExecutionData.builder().sweepingOutputEnvVariables(envVariables).build())
            .build();
    ShellScriptTaskResponseNG successResponse = ShellScriptTaskResponseNG.builder()
                                                    .status(CommandExecutionStatus.SUCCESS)
                                                    .executeCommandResponse(executeCommandResponse)
                                                    .build();

    StepResponse stepResponse =
        shellScriptStep.handleTaskResult(ambiance, stepElementParameters, () -> successResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).hasSize(2);

    ShellScriptOutcome shellScriptOutcome = ShellScriptOutcome.builder().outputVariables(new HashMap<>()).build();
    doReturn(shellScriptOutcome)
        .when(shellScriptHelperServiceOld)
        .prepareShellScriptOutcome(envVariables, outputVariables);
    stepResponse = shellScriptStep.handleTaskResult(ambiance, stepElementParameters, () -> successResponse);
    assertThat(stepResponse.getStepOutcomes()).hasSize(2);
    assertThat(((List<StepOutcome>) stepResponse.getStepOutcomes()).get(0).getOutcome()).isEqualTo(shellScriptOutcome);
    verify(logClient, times(2)).closeStream("Execute");
    verify(shellScriptHelperServiceOld, times(2))
        .exportOutputVariablesUsingAlias(ambiance, stepParameters, shellScriptOutcome);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHandleTaskResultForSuccessTaskWithExitCode() throws Exception {
    Ambiance ambiance = buildAmbiance();
    Map<String, Object> outputVariables = new HashMap<>();
    ShellScriptStepParameters stepParameters = ShellScriptStepParameters.infoBuilder()
                                                   .outputVariables(outputVariables)
                                                   .shellType(ShellType.Bash)
                                                   .outputAlias(OutputAlias.builder().build())
                                                   .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();
    Map<String, String> envVariables = new HashMap<>();

    ExecuteCommandResponse executeCommandResponse =
        ExecuteCommandResponse.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .commandExecutionData(
                ShellExecutionData.builder().sweepingOutputEnvVariables(envVariables).exitCode(0).build())
            .build();
    ShellScriptTaskResponseNG successResponse = ShellScriptTaskResponseNG.builder()
                                                    .status(CommandExecutionStatus.SUCCESS)
                                                    .executeCommandResponse(executeCommandResponse)
                                                    .build();

    StepResponse stepResponse =
        shellScriptStep.handleTaskResult(ambiance, stepElementParameters, () -> successResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).hasSize(2);
    StepResponse.StepOutcome[] stepOutcomes = stepResponse.getStepOutcomes().toArray(new StepResponse.StepOutcome[0]);
    ShellScriptOutcome outcome = (ShellScriptOutcome) stepOutcomes[0].getOutcome();
    assertThat(outcome.getExitCode()).isEqualTo("0");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testShellScriptStepSerialization() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("shellScriptStep.yml");
    ShellScriptStepParameters shellScriptStepParameters =
        YamlPipelineUtils.read(testFile, ShellScriptStepParameters.class);
    assertThat(shellScriptStepParameters.getOnDelegate().getValue()).isEqualTo(true);
    assertThat(shellScriptStepParameters.getShell()).isEqualTo(ShellType.Bash);
    assertThat(shellScriptStepParameters.getSource().getType()).isEqualTo("Inline");
    assertThat(((ShellScriptInlineSource) shellScriptStepParameters.getSource().getSpec()).getScript().getValue())
        .isEqualTo("echo hi");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testCreateTaskSelectors_withFeatureFlagDisabled_shouldReturnOriginalSelectors() {
    Ambiance ambiance = buildAmbiance();
    TaskSelectorYaml selector1 = new TaskSelectorYaml("selector1");
    TaskSelectorYaml selector2 = new TaskSelectorYaml("selector2");
    List<TaskSelectorYaml> selectorYamls = List.of(selector1, selector2);
    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.createValueField(selectorYamls);

    when(pmsFeatureFlagService.isEnabled(
             eq("accId"), eq(FeatureName.CDS_SET_TASK_SELECTOR_ORIGIN_AS_PIPELINE_FOR_SHELL_SCRIPT)))
        .thenReturn(false);

    List<TaskSelector> result = shellScriptStep.createTaskSelectors(ambiance, delegateSelectors);

    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getSelector()).isEqualTo("selector1");
    assertThat(result.get(1).getSelector()).isEqualTo("selector2");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testCreateTaskSelectors_withFeatureFlagEnabled_shouldUpdateOriginToPipeline() {
    Ambiance ambiance = buildAmbiance();
    TaskSelectorYaml selector1 = new TaskSelectorYaml("selector1");
    TaskSelectorYaml selector2 = new TaskSelectorYaml("selector2");
    List<TaskSelectorYaml> selectorYamls = List.of(selector1, selector2);
    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.createValueField(selectorYamls);

    when(pmsFeatureFlagService.isEnabled(
             eq("accId"), eq(FeatureName.CDS_SET_TASK_SELECTOR_ORIGIN_AS_PIPELINE_FOR_SHELL_SCRIPT)))
        .thenReturn(true);

    List<TaskSelector> result = shellScriptStep.createTaskSelectors(ambiance, delegateSelectors);

    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getSelector()).isEqualTo("selector1");
    assertThat(result.get(0).getOrigin()).isEqualTo(YAMLFieldNameConstants.PIPELINE);
    assertThat(result.get(1).getSelector()).isEqualTo("selector2");
    assertThat(result.get(1).getOrigin()).isEqualTo(YAMLFieldNameConstants.PIPELINE);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testCreateTaskSelectors_withFeatureFlagEnabled_shouldNotUpdateOriginIfAlreadySet() {
    Ambiance ambiance = buildAmbiance();
    TaskSelectorYaml selector1 = new TaskSelectorYaml("selector1");
    selector1.setOrigin("customOrigin");
    List<TaskSelectorYaml> selectorYamls = List.of(selector1);
    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.createValueField(selectorYamls);

    when(pmsFeatureFlagService.isEnabled(
             eq("accId"), eq(FeatureName.CDS_SET_TASK_SELECTOR_ORIGIN_AS_PIPELINE_FOR_SHELL_SCRIPT)))
        .thenReturn(true);

    List<TaskSelector> result = shellScriptStep.createTaskSelectors(ambiance, delegateSelectors);

    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getSelector()).isEqualTo("selector1");
    assertThat(result.get(0).getOrigin()).isEqualTo("customOrigin");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testCreateTaskSelectors_withFeatureFlagEnabled_shouldUpdateOriginIfDefault() {
    Ambiance ambiance = buildAmbiance();
    TaskSelectorYaml selector1 = new TaskSelectorYaml("selector1");
    selector1.setOrigin(ORIGIN_DEFAULT);
    List<TaskSelectorYaml> selectorYamls = List.of(selector1);
    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.createValueField(selectorYamls);

    when(pmsFeatureFlagService.isEnabled(
             eq("accId"), eq(FeatureName.CDS_SET_TASK_SELECTOR_ORIGIN_AS_PIPELINE_FOR_SHELL_SCRIPT)))
        .thenReturn(true);

    List<TaskSelector> result = shellScriptStep.createTaskSelectors(ambiance, delegateSelectors);

    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getSelector()).isEqualTo("selector1");
    assertThat(result.get(0).getOrigin()).isEqualTo(YAMLFieldNameConstants.PIPELINE);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testCreateTaskSelectors_withNullDelegateSelectors_shouldReturnEmptyList() {
    Ambiance ambiance = buildAmbiance();
    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.createValueField(null);

    when(pmsFeatureFlagService.isEnabled(
             eq("accId"), eq(FeatureName.CDS_SET_TASK_SELECTOR_ORIGIN_AS_PIPELINE_FOR_SHELL_SCRIPT)))
        .thenReturn(true);

    List<TaskSelector> result = shellScriptStep.createTaskSelectors(ambiance, delegateSelectors);

    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void testCreateTaskSelectors_withEmptyDelegateSelectors_shouldReturnEmptyList() {
    Ambiance ambiance = buildAmbiance();
    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.createValueField(Collections.emptyList());

    when(pmsFeatureFlagService.isEnabled(
             eq("accId"), eq(FeatureName.CDS_SET_TASK_SELECTOR_ORIGIN_AS_PIPELINE_FOR_SHELL_SCRIPT)))
        .thenReturn(true);

    List<TaskSelector> result = shellScriptStep.createTaskSelectors(ambiance, delegateSelectors);

    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }
}

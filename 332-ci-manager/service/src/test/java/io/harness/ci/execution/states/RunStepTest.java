/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.outcomes.LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME;
import static io.harness.beans.steps.stepinfo.InitializeStepInfo.CALLBACK_IDS;
import static io.harness.beans.steps.stepinfo.InitializeStepInfo.LOG_KEYS;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODE_BASE_CONNECTOR_REF;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.INIT_ENV_VARS;
import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.MAYANK_CHAMARTHI;
import static io.harness.rule.OwnerRule.OMPRAGASH;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.SHUBHAM;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.steps.outcome.CIStepOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.output.CIStageOutput;
import io.harness.beans.steps.stepinfo.CiStepParametersUtils;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.beans.steps.v1.Container;
import io.harness.beans.sweepingoutputs.CodeBaseConnectorRefSweepingOutput;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StepLogKeyDetails;
import io.harness.beans.sweepingoutputs.StepTaskDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.serializer.RunStepProtobufSerializer;
import io.harness.ci.execution.serializer.vm.VmStepSerializer;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.delegate.task.stepstatus.ErrorDetails;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactDescriptor;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.FileArtifactDescriptor;
import io.harness.delegate.task.stepstatus.artifact.FileArtifactMetadata;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.iacm.execution.PluginSettingUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureSubType;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.plugin.CIVMExecuteStepTaskParamsHelper;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.plugin.CommonStepExecutionHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.repositories.CILogKeyRepository;
import io.harness.repositories.CIStageOutputRepository;
import io.harness.rule.Owner;
import io.harness.runner.request.helpers.RunnerV0YamlHelper;
import io.harness.runner.request.utils.RunnerCommonExecuteUtils;
import io.harness.tasks.ResponseData;
import io.harness.vm.VmExecuteStepUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CI)
public class RunStepTest extends CIExecutionTestBase {
  public static final String STEP_ID = "runStepId";
  public static final String OUTPUT_KEY = "VAR1";
  public static final String OUTPUT_VALUE = "VALUE1";
  public static final String STEP_RESPONSE = "runStep";
  public static final String ERROR = "Error executing run step";
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private OutcomeService outcomeService;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Mock private RunStepProtobufSerializer runStepProtobufSerializer;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private VmStepSerializer vmStepSerializer;
  @Mock private VmExecuteStepUtils vmExecuteStepUtils;
  @Mock private StepBaseParameters stepParameters;
  @Mock CILogServiceUtils logServiceUtils;
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Mock SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock PluginSettingUtils pluginSettingUtils;

  @Mock io.harness.utils.ConnectorUtils ngConnectorUtils;
  @Mock CiStepParametersUtils ciStepParametersUtils;

  @Mock protected CIFeatureFlagService featureFlagService;
  @Mock protected CIStageOutputRepository ciStageOutputRepository;
  @Mock protected CILogKeyRepository ciLogKeyRepository;
  @Mock protected RunnerV0YamlHelper runnerV0YamlHelper;
  @Mock private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Mock private CommonStepExecutionHelper commonStepExecutionHelper;

  @Inject private ExceptionManager exceptionManager;
  @InjectMocks private RunnerCommonExecuteUtils runnerCommonExecuteUtils;
  @InjectMocks private CIVMExecuteStepTaskParamsHelper civmExecuteStepTaskParamsHelper;
  @InjectMocks private CommonAbstractStepUtils commonAbstractStepUtils;
  @InjectMocks RunStep runStep;
  //@InjectMocks private DliteVmInfraInfo dliteVmInfraInfo;
  // private VmInfraInfo vmInfraInfo = VmInfraInfo.builder().poolId("test").build();
  private static final CIInitializeTaskParams.Type vmInfraInfo = CIInitializeTaskParams.Type.VM;
  private static final CIInitializeTaskParams.Type dliteVmInfraInfo = CIInitializeTaskParams.Type.DLITE_VM;

  private Ambiance ambiance;
  private RunStepInfo stepInfo;
  private StepElementParameters stepElementParameters;
  private StepInputPackage stepInputPackage;
  private StepTaskDetails stepTaskDetails;
  private ContainerPortDetails containerPortDetails;
  private StepLogKeyDetails stepLogKeyDetails;
  private final String callbackId = UUID.randomUUID().toString();
  private final String callbackId2 = UUID.randomUUID().toString();
  private Map<String, ResponseData> responseDataMap;
  private CodeBaseConnectorRefSweepingOutput codeBaseConnectorRefSweepingOutput;

  @Before
  public void setUp() {
    on(runStep).set("exceptionManager", exceptionManager);
    on(runStep).set("runnerCommonExecuteUtils", runnerCommonExecuteUtils);
    on(runStep).set("commonAbstractStepUtils", commonAbstractStepUtils);
    on(runnerCommonExecuteUtils).set("civmExecuteStepTaskParamsHelper", civmExecuteStepTaskParamsHelper);
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");

    ambiance = Ambiance.newBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .setPipelineIdentifier("pipelineId")
                                    .setRunSequence(1)
                                    .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                                    .build())
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("runtimeId")
                                  .setIdentifier("runStepId")
                                  .setOriginalIdentifier("runStepId")
                                  .setRetryIndex(1)
                                  .build())
                   .build();

    stepInfo = RunStepInfo.builder()
                   .identifier(STEP_ID)
                   .command(ParameterField.<String>builder().expressionValue("ls").build())
                   .image(ParameterField.<String>builder().expressionValue("alpine").build())
                   .build();
    stepElementParameters = StepElementParameters.builder().name("name").spec(stepInfo).build();
    stepInputPackage = StepInputPackage.builder().build();
    Map<String, String> callbackIds = new HashMap<>();
    callbackIds.put(STEP_ID, callbackId);
    stepTaskDetails = StepTaskDetails.builder().taskIds(callbackIds).build();
    Map<String, List<Integer>> portDetails = new HashMap<>();
    portDetails.put(STEP_ID, asList(10));

    containerPortDetails = ContainerPortDetails.builder().portDetails(portDetails).build();
    responseDataMap = new HashMap<>();
    codeBaseConnectorRefSweepingOutput =
        CodeBaseConnectorRefSweepingOutput.builder().codeBaseConnectorRef("codeBaseConnectorRef").build();
  }

  @After
  public void tearDown() throws Exception {
    responseDataMap.clear();
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldExecuteAsync() {
    Map<String, List<String>> logKeys = new HashMap<>();
    String key =
        "accountId:accountId/orgId:orgId/projectId:projectId/pipelineId:pipelineId/runSequence:1/level0:runStepId_1";
    logKeys.put(STEP_ID, Collections.singletonList(key));
    StepLogKeyDetails stepLogKeyDetails = StepLogKeyDetails.builder().logKeys(logKeys).build();
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
        LiteEnginePodDetailsOutcome.builder().ipAddress("122.32.433.43").build();

    RefObject refObject1 = RefObjectUtils.getSweepingOutputRefObject(CALLBACK_IDS);
    RefObject refObject2 = RefObjectUtils.getSweepingOutputRefObject(LOG_KEYS);
    RefObject refObject3 = RefObjectUtils.getSweepingOutputRefObject(PORT_DETAILS);
    RefObject refObject4 = RefObjectUtils.getSweepingOutputRefObject(CODE_BASE_CONNECTOR_REF);

    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(K8StageInfraDetails.builder()
                                    .podName("podName")
                                    .infrastructure(K8sDirectInfraYaml.builder()
                                                        .spec(K8sDirectInfraYamlSpec.builder()
                                                                  .connectorRef(ParameterField.createValueField("fd"))
                                                                  .namespace(ParameterField.createValueField("fd"))
                                                                  .build())
                                                        .build())
                                    .containerNames(new ArrayList<>())
                                    .build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), any())).thenReturn(true);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("output1", "output1Value");
    when(ciStageOutputRepository.findFirstByStageExecutionId(any()))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));
    when(executionSweepingOutputResolver.resolve(eq(ambiance), eq(refObject1))).thenReturn(stepTaskDetails);
    when(executionSweepingOutputResolver.resolve(eq(ambiance), eq(refObject2))).thenReturn(stepLogKeyDetails);
    when(executionSweepingOutputResolver.resolve(eq(ambiance), eq(refObject3))).thenReturn(containerPortDetails);
    when(executionSweepingOutputResolver.resolveOptional(eq(ambiance), eq(refObject4)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(codeBaseConnectorRefSweepingOutput).build());
    when(outcomeService.resolve(ambiance, RefObjectUtils.getOutcomeRefObject(POD_DETAILS_OUTCOME)))
        .thenReturn(liteEnginePodDetailsOutcome);
    when(ciExecutionServiceConfig.isLocal()).thenReturn(false);

    when(ciDelegateTaskExecutor.queueParkedDelegateTask(any(), anyLong(), any(), anyList())).thenReturn(callbackId);
    when(ciDelegateTaskExecutor.queueTask(any(), any(), any(), any(), eq(false), any())).thenReturn(callbackId2);

    when(runStepProtobufSerializer.serializeStepWithStepParameters(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UnitStep.newBuilder().build());
    when(ngConnectorUtils.getConnectorDetails(any(), anyString())).thenReturn(ConnectorDetails.builder().build());

    AsyncExecutableResponse asyncExecutableResponse =
        runStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse)
        .isEqualTo(AsyncExecutableResponse.newBuilder()
                       .addCallbackIds(callbackId)
                       .addCallbackIds(callbackId2)
                       .addLogKeys(key)
                       .build());
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void shouldHandleSuccessAsyncResponse() {
    responseDataMap.put(STEP_RESPONSE,
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder()
                            .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                            .output(StepMapOutput.builder().output(OUTPUT_KEY, OUTPUT_VALUE).build())
                            .build())
            .build());
    StepResponse stepResponse = runStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);

    assertThat(stepResponse)
        .isEqualTo(
            StepResponse.builder()
                .status(Status.SUCCEEDED)
                .stepOutcome(
                    StepResponse.StepOutcome.builder()
                        .outcome(CIStepOutcome.builder()
                                     .outputVariables(
                                         StepMapOutput.builder().output(OUTPUT_KEY, OUTPUT_VALUE).build().getMap())
                                     .build())
                        .name("outputVariables")
                        .build())
                .build());
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldHandleFailureAsyncResponse() {
    ResponseData responseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder().stepExecutionStatus(StepExecutionStatus.FAILURE).error(ERROR).build())
            .build();
    responseDataMap.put(STEP_RESPONSE, responseData);

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(
            OptionalSweepingOutput.builder()
                .found(true)
                .output(K8StageInfraDetails.builder().podName("podName").containerNames(new ArrayList<>()).build())
                .build());
    StepResponse stepResponse = runStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);

    assertThat(stepResponse)
        .isEqualTo(StepResponse.builder()
                       .status(Status.FAILED)
                       .failureInfo(FailureInfo.newBuilder()
                                        .addFailureData(CIStepInfoUtils.getDefaultCIFailureDataInfo(ERROR, ambiance))
                                        .setErrorMessage(ERROR)
                                        .addAllFailureTypes(EnumSet.of(FailureType.APPLICATION_FAILURE))
                                        .build())
                       .build());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void shouldHandleSkippedAsyncResponse() {
    ResponseData responseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder().stepExecutionStatus(StepExecutionStatus.SKIPPED).build())
            .build();
    responseDataMap.put(STEP_RESPONSE, responseData);

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);

    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(
            OptionalSweepingOutput.builder()
                .found(true)
                .output(K8StageInfraDetails.builder().podName("podName").containerNames(new ArrayList<>()).build())
                .build());
    StepResponse stepResponse = runStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);

    assertThat(stepResponse).isEqualTo(StepResponse.builder().status(Status.SKIPPED).build());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldHandleLiteEngineTaskFailure() {
    ResponseData responseData = ErrorNotifyResponseData.builder().errorMessage("error message").build();
    responseDataMap.put(STEP_RESPONSE, responseData);

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);

    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(
            OptionalSweepingOutput.builder()
                .found(true)
                .output(K8StageInfraDetails.builder().podName("podName").containerNames(new ArrayList<>()).build())
                .build());
    StepResponse stepResponse = runStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);

    ErrorDetails errorDetails = ErrorDetails.builder()
                                    .failureType(FailureType.INFRASTRUCTURE_FAILURE.name())
                                    .failureSubType(FailureSubType.GENERAL_ERROR.name())
                                    .build();
    assertThat(stepResponse)
        .isEqualTo(StepResponse.builder()
                       .status(Status.FAILED)
                       .failureInfo(FailureInfo.newBuilder()
                                        .setErrorMessage("Delegate is not able to connect to created build farm")
                                        .addFailureData(CIStepInfoUtils.getDefaultCIFailureDataInfo(
                                            "HINT. EXPLANATION", ambiance, errorDetails))
                                        .addAllFailureTypes(EnumSet.of(FailureType.INFRASTRUCTURE_FAILURE))
                                        .build())
                       .build());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldHandleLiteEngineTaskFailureSingleCallback() {
    List<String> callbackIds = new ArrayList<>();
    callbackIds.add("callbackId1");
    callbackIds.add("callbackId2");
    ResponseData responseData = ErrorNotifyResponseData.builder().errorMessage("error message").build();

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    runStep.handleForCallbackId(ambiance, stepElementParameters, callbackIds, "callbackId1", responseData);
    verify(waitNotifyEngine, times(1)).doneWith(any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void shouldAbortSiblingCallbacksViaUpsertWhenFFEnabled() {
    // With PIE_CONTAINER_STEP_ABORT_USE_UPSERT enabled, aborting sibling callbacks must use the
    // idempotent doneWithUpsert path to avoid E11000 duplicate-key errors on notifyResponses when a
    // sibling callback (e.g. the lite-engine task) has already been notified.
    Ambiance upsertAmbiance =
        ambiance.toBuilder()
            .setMetadata(ambiance.getMetadata()
                             .toBuilder()
                             .putFeatureFlagToValueMap(FeatureName.PIE_CONTAINER_STEP_ABORT_USE_UPSERT.name(), true)
                             .build())
            .build();
    List<String> callbackIds = new ArrayList<>();
    callbackIds.add("callbackId1");
    callbackIds.add("callbackId2");
    ResponseData responseData = ErrorNotifyResponseData.builder().errorMessage("error message").build();

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    runStep.handleForCallbackId(upsertAmbiance, stepElementParameters, callbackIds, "callbackId1", responseData);

    verify(waitNotifyEngine, times(1)).doneWithUpsert(eq("callbackId2"), any());
    verify(waitNotifyEngine, never()).doneWith(any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void shouldAbortSiblingCallbacksViaDoneWithWhenFFDisabled() {
    // With the FF disabled (default ambiance), aborting siblings must stay on the legacy doneWith path.
    List<String> callbackIds = new ArrayList<>();
    callbackIds.add("callbackId1");
    callbackIds.add("callbackId2");
    ResponseData responseData = ErrorNotifyResponseData.builder().errorMessage("error message").build();

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    runStep.handleForCallbackId(ambiance, stepElementParameters, callbackIds, "callbackId1", responseData);

    verify(waitNotifyEngine, times(1)).doneWith(eq("callbackId2"), any());
    verify(waitNotifyEngine, never()).doneWithUpsert(any(), any());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void shouldExecuteAsyncVm() {
    Map<String, List<String>> logKeys = new HashMap<>();
    String key =
        "accountId:accountId/orgId:orgId/projectId:projectId/pipelineId:pipelineId/runSequence:1/level0:runStepId_1";
    logKeys.put(STEP_ID, Collections.singletonList(key));

    RefObject refObject = RefObjectUtils.getSweepingOutputRefObject(CODE_BASE_CONNECTOR_REF);

    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(VmStageInfraDetails.builder().infraInfo(vmInfraInfo).build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(eq(ambiance), eq(refObject)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(codeBaseConnectorRefSweepingOutput).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(
            OptionalSweepingOutput.builder()
                .found(true)
                .output(
                    StageDetails.builder().stageRuntimeID("test").infrastructure(VmInfraYaml.builder().build()).build())
                .build());
    when(outcomeService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(VmDetailsOutcome.VM_DETAILS_OUTCOME)))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(VmDetailsOutcome.builder().ipAddress("1.1.1.1").delegateId("test").build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(VmStageInfraDetails.builder().infraInfo(vmInfraInfo).build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(INIT_ENV_VARS)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(vmStepSerializer.serialize(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(VmRunStep.builder().envVariables(new HashMap<>()).build());
    when(ciDelegateTaskExecutor.queueDelegateTaskRequest(any())).thenReturn(callbackId);

    AsyncExecutableResponse asyncExecutableResponse =
        runStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse)
        .isEqualTo(AsyncExecutableResponse.newBuilder().addCallbackIds(callbackId).addLogKeys(key).build());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void shouldExecuteAsyncVmWithDelegateId() {
    Map<String, List<String>> logKeys = new HashMap<>();
    String key =
        "accountId:accountId/orgId:orgId/projectId:projectId/pipelineId:pipelineId/runSequence:1/level0:runStepId_1";
    logKeys.put(STEP_ID, Collections.singletonList(key));

    RefObject refObject = RefObjectUtils.getSweepingOutputRefObject(CODE_BASE_CONNECTOR_REF);

    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(VmStageInfraDetails.builder().infraInfo(vmInfraInfo).build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(eq(ambiance), eq(refObject)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(codeBaseConnectorRefSweepingOutput).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(
            OptionalSweepingOutput.builder()
                .found(true)
                .output(
                    StageDetails.builder().stageRuntimeID("test").infrastructure(VmInfraYaml.builder().build()).build())
                .build());
    when(outcomeService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(VmDetailsOutcome.VM_DETAILS_OUTCOME)))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(VmDetailsOutcome.builder().ipAddress("1.1.1.1").build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(VmStageInfraDetails.builder().infraInfo(vmInfraInfo).build())
                        .build());

    when(vmStepSerializer.serialize(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(VmRunStep.builder().envVariables(new HashMap<>()).build());
    when(ciDelegateTaskExecutor.queueDelegateTaskRequest(any())).thenReturn(callbackId);
    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(INIT_ENV_VARS)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    AsyncExecutableResponse asyncExecutableResponse =
        runStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse)
        .isEqualTo(AsyncExecutableResponse.newBuilder().addCallbackIds(callbackId).addLogKeys(key).build());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void shouldExecuteAsyncHostedVmWithDelegateId() {
    Map<String, List<String>> logKeys = new HashMap<>();
    String key =
        "accountId:accountId/orgId:orgId/projectId:projectId/pipelineId:pipelineId/runSequence:1/level0:runStepId_1";
    logKeys.put(STEP_ID, Collections.singletonList(key));
    RefObject refObject = RefObjectUtils.getSweepingOutputRefObject(CODE_BASE_CONNECTOR_REF);

    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(DliteVmStageInfraDetails.builder().infraInfo(dliteVmInfraInfo).build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(eq(ambiance), eq(refObject)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(codeBaseConnectorRefSweepingOutput).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder()
                                    .stageRuntimeID("test")
                                    .infrastructure(HostedVmInfraYaml.builder().build())
                                    .build())
                        .build());
    when(outcomeService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(VmDetailsOutcome.VM_DETAILS_OUTCOME)))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(VmDetailsOutcome.builder().ipAddress("1.1.1.1").build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(VmStageInfraDetails.builder().infraInfo(vmInfraInfo).build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(INIT_ENV_VARS)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(vmStepSerializer.serialize(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(VmRunStep.builder().envVariables(new HashMap<>()).build());
    when(vmStepSerializer.getStepSecrets(any(), any())).thenReturn(new HashSet<>());
    when(vmExecuteStepUtils.convertStep(any(), any())).thenReturn(ExecuteStepRequest.builder());
    when(ciDelegateTaskExecutor.queueDelegateTaskRequest(any())).thenReturn(callbackId);

    AsyncExecutableResponse asyncExecutableResponse =
        runStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse)
        .isEqualTo(AsyncExecutableResponse.newBuilder().addCallbackIds(callbackId).addLogKeys(key).build());
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void shouldHandleSuccessVmAsyncResponse() {
    ResponseData responseData =
        VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    responseDataMap.put(STEP_RESPONSE, responseData);

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(VmStageInfraDetails.builder().infraInfo(vmInfraInfo).build())
                        .build());
    StepResponse stepResponse = runStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);

    assertThat(stepResponse).isEqualTo(StepResponse.builder().status(Status.SUCCEEDED).build());
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testInjectOutputVarsAsEnvVars() {
    VmStepInfo vmStepInfo = VmRunStep.builder().envVariables(new HashMap<>()).build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), any())).thenReturn(true);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("output1", "output1Value");
    when(ciStageOutputRepository.findFirstByStageExecutionId(any()))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));

    civmExecuteStepTaskParamsHelper.injectOutputVarsAsEnvVars(vmStepInfo, "acc", "stage");
    assertThat(((VmRunStep) vmStepInfo).getEnvVariables().size()).isEqualTo(1);
    assertThat(((VmRunStep) vmStepInfo).getEnvVariables().get("output1")).isEqualTo("output1Value");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testInjectOutputVarsAsEnvVarsExisting() {
    Map<String, String> existingEnv = new HashMap<>();
    existingEnv.put("existing1", "existingValue1");
    existingEnv.put("existing2", "existingValue2");
    VmStepInfo vmStepInfo = VmRunStep.builder().envVariables(existingEnv).build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), any())).thenReturn(true);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("output1", "output1Value");
    outputMap.put("existing2", "overridenValue");
    when(ciStageOutputRepository.findFirstByStageExecutionId(any()))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));

    civmExecuteStepTaskParamsHelper.injectOutputVarsAsEnvVars(vmStepInfo, "acc", "stage");
    assertThat(((VmRunStep) vmStepInfo).getEnvVariables().size()).isEqualTo(3);
    assertThat(((VmRunStep) vmStepInfo).getEnvVariables().get("output1")).isEqualTo("output1Value");
    assertThat(((VmRunStep) vmStepInfo).getEnvVariables().get("existing1")).isEqualTo("existingValue1");
    assertThat(((VmRunStep) vmStepInfo).getEnvVariables().get("existing2")).isEqualTo("existingValue2");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testInjectOutputVarsAsEnvVarsVmInheritedStageEnvLosesToOutputs() {
    Map<String, String> stepEnv = new HashMap<>();
    stepEnv.put("evar1", "eval1");
    stepEnv.put("out1", "step2");
    stepEnv.put("svar1", "sval1");
    stepEnv.put("svar2", "sval2");
    VmStepInfo vmStepInfo = VmRunStep.builder().envVariables(stepEnv).build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), any())).thenReturn(true);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("out1", "val1");
    outputMap.put("svar1", "step1");
    when(ciStageOutputRepository.findFirstByStageExecutionId(any()))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));
    RunStepInfoV1 ciStepInfo = RunStepInfoV1.builder().inheritedEnvKeys(Set.of("svar1", "svar2")).build();

    civmExecuteStepTaskParamsHelper.injectOutputVarsAsEnvVars(vmStepInfo, "acc", "stage", "two", ciStepInfo);

    Map<String, String> resolvedEnv = ((VmRunStep) vmStepInfo).getEnvVariables();
    assertThat(resolvedEnv).hasSize(4);
    assertThat(resolvedEnv.get("evar1")).isEqualTo("eval1");
    // step declared it, so the step value wins over the output
    assertThat(resolvedEnv.get("out1")).isEqualTo("step2");
    // inherited from stage env and produced as an output, so the output wins
    assertThat(resolvedEnv.get("svar1")).isEqualTo("step1");
    // inherited from stage env without a matching output, so the stage value is kept
    assertThat(resolvedEnv.get("svar2")).isEqualTo("sval2");
    // persisted stage outputs must not be polluted with this step's env vars
    assertThat(outputMap).containsOnlyKeys("out1", "svar1");
    assertThat(outputMap.get("out1")).isEqualTo("val1");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testInjectOutputVarsAsEnvVarsVmPluginInheritedStageEnvLosesToOutputs() {
    Map<String, String> stepEnv = new HashMap<>();
    stepEnv.put("svar1", "sval1");
    VmStepInfo vmStepInfo = VmPluginStep.builder().envVariables(stepEnv).build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), any())).thenReturn(true);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("svar1", "step1");
    when(ciStageOutputRepository.findFirstByStageExecutionId(any()))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));
    RunStepInfoV1 ciStepInfo = RunStepInfoV1.builder().inheritedEnvKeys(Set.of("svar1")).build();

    civmExecuteStepTaskParamsHelper.injectOutputVarsAsEnvVars(vmStepInfo, "acc", "stage", "two", ciStepInfo);

    assertThat(((VmPluginStep) vmStepInfo).getEnvVariables().get("svar1")).isEqualTo("step1");
  }

  @Test
  @Owner(developers = MAYANK_CHAMARTHI)
  @Category(UnitTests.class)
  public void testInjectOutputVarsAsEnvVarsVmWithoutInheritedKeysKeepsStepEnv() {
    Map<String, String> stepEnv = new HashMap<>();
    stepEnv.put("existing1", "existingValue1");
    VmStepInfo vmStepInfo = VmRunStep.builder().envVariables(stepEnv).build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), any())).thenReturn(true);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("existing1", "overridenValue");
    when(ciStageOutputRepository.findFirstByStageExecutionId(any()))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));

    // no inheritedEnvKeys (V0 or a step that declared everything itself) -> step env keeps winning
    civmExecuteStepTaskParamsHelper.injectOutputVarsAsEnvVars(
        vmStepInfo, "acc", "stage", "two", RunStepInfoV1.builder().build());

    assertThat(((VmRunStep) vmStepInfo).getEnvVariables().get("existing1")).isEqualTo("existingValue1");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testInjectOutputVarsAsEnvVarsNoFF() {
    Map<String, String> existingEnv = new HashMap<>();
    existingEnv.put("existing1", "existingValue1");
    existingEnv.put("existing2", "existingValue2");
    VmStepInfo vmStepInfo = VmRunStep.builder().envVariables(existingEnv).build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), any())).thenReturn(false);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("output1", "output1Value");
    outputMap.put("existing2", "overridenValue");
    when(ciStageOutputRepository.findFirstByStageExecutionId(any()))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));

    civmExecuteStepTaskParamsHelper.injectOutputVarsAsEnvVars(vmStepInfo, "acc", "stage");
    assertThat(((VmRunStep) vmStepInfo).getEnvVariables().size()).isEqualTo(2);
    assertThat(((VmRunStep) vmStepInfo).getEnvVariables().get("existing1")).isEqualTo("existingValue1");
    assertThat(((VmRunStep) vmStepInfo).getEnvVariables().get("existing2")).isEqualTo("existingValue2");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testInjectOutputVarsAsEnvVarsK8Existing() {
    Map<String, String> existingEnv = new HashMap<>();
    existingEnv.put("existing1", "existingValue1");
    existingEnv.put("existing2", "existingValue2");
    UnitStep unitStep =
        UnitStep.newBuilder()
            .setRun(io.harness.product.ci.engine.proto.RunStep.newBuilder().putAllEnvironment(existingEnv).build())
            .build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), any())).thenReturn(true);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("output1", "output1Value");
    outputMap.put("existing2", "overridenValue");
    when(ciStageOutputRepository.findFirstByStageExecutionId(any()))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));

    unitStep = runStep.injectOutputVarsAsEnvVars(unitStep, "acc", "stage");
    assertThat(unitStep.getRun().getEnvironmentCount()).isEqualTo(3);
    assertThat(unitStep.getRun().getEnvironmentOrThrow("existing1")).isEqualTo("existingValue1");
    assertThat(unitStep.getRun().getEnvironmentOrThrow("output1")).isEqualTo("output1Value");
    assertThat(unitStep.getRun().getEnvironmentOrThrow("existing2")).isEqualTo("existingValue2");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testInjectOutputVarsAsEnvVarsK8() {
    UnitStep unitStep =
        UnitStep.newBuilder().setRun(io.harness.product.ci.engine.proto.RunStep.newBuilder().build()).build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), any())).thenReturn(true);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("output1", "output1Value");
    outputMap.put("output2", "output2Value");
    when(ciStageOutputRepository.findFirstByStageExecutionId(any()))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));

    unitStep = runStep.injectOutputVarsAsEnvVars(unitStep, "acc", "stage");
    assertThat(unitStep.getRun().getEnvironmentCount()).isEqualTo(2);
    assertThat(unitStep.getRun().getEnvironmentOrThrow("output1")).isEqualTo("output1Value");
    assertThat(unitStep.getRun().getEnvironmentOrThrow("output2")).isEqualTo("output2Value");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testInjectOutputVarsAsEnvVarsK8NoFF() {
    Map<String, String> existingEnv = new HashMap<>();
    existingEnv.put("existing1", "existingValue1");
    existingEnv.put("existing2", "existingValue2");
    UnitStep unitStep =
        UnitStep.newBuilder()
            .setRun(io.harness.product.ci.engine.proto.RunStep.newBuilder().putAllEnvironment(existingEnv).build())
            .build();
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), any())).thenReturn(false);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("output1", "output1Value");
    outputMap.put("existing2", "overridenValue");
    when(ciStageOutputRepository.findFirstByStageExecutionId(any()))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));

    unitStep = runStep.injectOutputVarsAsEnvVars(unitStep, "acc", "stage");
    assertThat(unitStep.getRun().getEnvironmentCount()).isEqualTo(2);
    assertThat(unitStep.getRun().getEnvironmentOrThrow("existing1")).isEqualTo("existingValue1");
    assertThat(unitStep.getRun().getEnvironmentOrThrow("existing2")).isEqualTo("existingValue2");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testInjectOutputVarsAsEnvVarsK8SkipsAutoInjectedSteps() {
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), eq("acc"))).thenReturn(true);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("AWS_ACCESS_KEY_ID", "shouldNotLeak");
    when(ciStageOutputRepository.findFirstByStageExecutionId(eq("stage")))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));

    for (String autoInjectedId : new String[] {"save-cache-harness", "restore-cache-harness", "harness-build-cache"}) {
      UnitStep unitStep = UnitStep.newBuilder()
                              .setId(autoInjectedId)
                              .setPlugin(io.harness.product.ci.engine.proto.PluginStep.newBuilder().build())
                              .build();
      UnitStep result = runStep.injectOutputVarsAsEnvVars(unitStep, "acc", "stage");
      assertThat(result.getPlugin().getEnvironmentCount()).isEqualTo(0);
    }
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testInjectOutputVarsAsEnvVarsVmSkipsAutoInjectedSteps() {
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), eq("acc"))).thenReturn(true);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("AWS_ACCESS_KEY_ID", "shouldNotLeak");
    when(ciStageOutputRepository.findFirstByStageExecutionId(eq("stage")))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));

    VmStepInfo pluginStep =
        io.harness.delegate.beans.ci.vm.steps.VmPluginStep.builder().envVariables(new HashMap<>()).build();
    civmExecuteStepTaskParamsHelper.injectOutputVarsAsEnvVars(pluginStep, "acc", "stage", "save-cache-harness");
    assertThat(((io.harness.delegate.beans.ci.vm.steps.VmPluginStep) pluginStep).getEnvVariables().size()).isEqualTo(0);

    VmStepInfo bgStep =
        io.harness.delegate.beans.ci.vm.steps.VmBackgroundStep.builder().envVariables(new HashMap<>()).build();
    civmExecuteStepTaskParamsHelper.injectOutputVarsAsEnvVars(bgStep, "acc", "stage", "harness-build-cache");
    assertThat(((io.harness.delegate.beans.ci.vm.steps.VmBackgroundStep) bgStep).getEnvVariables().size()).isEqualTo(0);

    VmStepInfo restoreStep =
        io.harness.delegate.beans.ci.vm.steps.VmRunStep.builder().envVariables(new HashMap<>()).build();
    civmExecuteStepTaskParamsHelper.injectOutputVarsAsEnvVars(restoreStep, "acc", "stage", "restore-cache-harness");
    assertThat(((io.harness.delegate.beans.ci.vm.steps.VmRunStep) restoreStep).getEnvVariables().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testPopulateCIStageOutputs() {
    Map<String, String> outputVariables = new HashMap<>();
    outputVariables.put("key1", "value1");
    outputVariables.put("key2", "null");
    outputVariables.put("key3", null);
    when(featureFlagService.isEnabled(eq(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV), any())).thenReturn(true);
    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("output1", "output1Value");
    outputMap.put("output2", null);
    when(ciStageOutputRepository.findFirstByStageExecutionId(any()))
        .thenReturn(Optional.of(CIStageOutput.builder().stageExecutionId("stage").outputs(outputMap).build()));
    runStep.populateCIStageOutputs(outputVariables, "acc", "stage");
  }

  @Test
  @Owner(developers = {OMPRAGASH})
  @Category(UnitTests.class)
  public void shouldHandleFileArtifactMetadataSuccessfully() {
    ArtifactMetadata artifactMetadata = mock(ArtifactMetadata.class);
    FileArtifactMetadata fileArtifactMetadata = mock(FileArtifactMetadata.class);
    FileArtifactDescriptor descriptor1 = mock(FileArtifactDescriptor.class);
    when(descriptor1.getUrl()).thenReturn("https://url1.com");
    FileArtifactDescriptor descriptor2 = mock(FileArtifactDescriptor.class);
    when(descriptor2.getUrl()).thenReturn("https://url2.com");
    List<FileArtifactDescriptor> fileArtifactDescriptors = Arrays.asList(descriptor1, descriptor2);
    when(artifactMetadata.getType()).thenReturn(ArtifactMetadataType.FILE_ARTIFACT_METADATA);
    when(artifactMetadata.getSpec()).thenReturn(fileArtifactMetadata);
    when(fileArtifactMetadata.getFileArtifactDescriptors()).thenReturn(fileArtifactDescriptors);
    StepArtifacts result = runStep.handleArtifact(artifactMetadata, stepParameters, ambiance);
    assertThat(result.getPublishedFileArtifacts()).hasSize(2);
    assertThat(result.getPublishedFileArtifacts().get(0).getUrl()).isEqualTo("https://url1.com");
    assertThat(result.getPublishedFileArtifacts().get(1).getUrl()).isEqualTo("https://url2.com");
  }

  // ==================================
  // V1 handleArtifact Tests
  // ==================================

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testHandleArtifactV1_DockerBuildAndPush_WithDockerHubRegistry() {
    // Setup V1 ambiance
    Ambiance v1Ambiance = createV1Ambiance();

    // Setup V1 step parameters with Docker plugin image
    RunStepInfoV1 runStepInfoV1 = createRunStepInfoV1WithDockerPluginAndWith("plugins/docker", "myrepo/myimage");
    StepBaseParameters v1StepParameters = mock(StepBaseParameters.class);
    when(v1StepParameters.getVersion()).thenReturn(HarnessYamlVersion.V1);
    when(v1StepParameters.getSpec()).thenReturn(runStepInfoV1);

    // Create Docker artifact metadata for Docker Hub
    ArtifactMetadata artifactMetadata = createDockerHubArtifactMetadata("myrepo/myimage:v1.0", "sha256:abc123");

    StepArtifacts result = runStep.handleArtifact(artifactMetadata, v1StepParameters, v1Ambiance);

    assertThat(result.getPublishedImageArtifacts()).hasSize(1);
    assertThat(result.getPublishedImageArtifacts().get(0).getImageName()).isEqualTo("myrepo/myimage");
    assertThat(result.getPublishedImageArtifacts().get(0).getTag()).isEqualTo("v1.0");
    assertThat(result.getPublishedImageArtifacts().get(0).getDigest()).isEqualTo("sha256:abc123");
    assertThat(result.getPublishedImageArtifacts().get(0).getUrl()).contains("hub.docker.com");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testHandleArtifactV1_DockerBuildAndPush_WithHarnessRegistry() {
    Ambiance v1Ambiance = createV1Ambiance();

    RunStepInfoV1 runStepInfoV1 = createRunStepInfoV1WithDockerPluginAndWith("plugins/docker", "myrepo");
    StepBaseParameters v1StepParameters = mock(StepBaseParameters.class);
    when(v1StepParameters.getVersion()).thenReturn(HarnessYamlVersion.V1);
    when(v1StepParameters.getSpec()).thenReturn(runStepInfoV1);

    // Create artifact metadata for Harness Registry (non-Docker Hub)
    ArtifactMetadata artifactMetadata =
        createDockerArtifactMetadata("app.harness.io", "app.harness.io/myrepo/myimage:latest", "sha256:xyz789");

    StepArtifacts result = runStep.handleArtifact(artifactMetadata, v1StepParameters, v1Ambiance);

    assertThat(result.getPublishedImageArtifacts()).hasSize(1);
    assertThat(result.getPublishedImageArtifacts().get(0).getTag()).isEqualTo("latest");
    // For Harness Registry, URL format is https://image:tag
    assertThat(result.getPublishedImageArtifacts().get(0).getUrl()).startsWith("https://");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testHandleArtifactV1_ECRBuildAndPush() {
    Ambiance v1Ambiance = createV1Ambiance();

    // ECR plugin with REGISTRY and REGION in with field
    RunStepInfoV1 runStepInfoV1 = createRunStepInfoV1WithECRPlugin();
    StepBaseParameters v1StepParameters = mock(StepBaseParameters.class);
    when(v1StepParameters.getVersion()).thenReturn(HarnessYamlVersion.V1);
    when(v1StepParameters.getSpec()).thenReturn(runStepInfoV1);

    ArtifactMetadata artifactMetadata = createDockerArtifactMetadata("123456789012.dkr.ecr.us-west-2.amazonaws.com",
        "123456789012.dkr.ecr.us-west-2.amazonaws.com/myrepo:v1.0", "sha256:ecr123");

    StepArtifacts result = runStep.handleArtifact(artifactMetadata, v1StepParameters, v1Ambiance);

    assertThat(result.getPublishedImageArtifacts()).hasSize(1);
    assertThat(result.getPublishedImageArtifacts().get(0).getDigest()).isEqualTo("sha256:ecr123");
    assertThat(result.getPublishedImageArtifacts().get(0).getUrl()).contains("console.aws.amazon.com/ecr");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testHandleArtifactV1_GARBuildAndPush() {
    Ambiance v1Ambiance = createV1Ambiance();

    RunStepInfoV1 runStepInfoV1 = createRunStepInfoV1WithGARPlugin();
    StepBaseParameters v1StepParameters = mock(StepBaseParameters.class);
    when(v1StepParameters.getVersion()).thenReturn(HarnessYamlVersion.V1);
    when(v1StepParameters.getSpec()).thenReturn(runStepInfoV1);

    ArtifactMetadata artifactMetadata = createDockerArtifactMetadata(
        "us-central1-docker.pkg.dev", "us-central1-docker.pkg.dev/my-project/my-repo/myimage:v2.0", "sha256:gar456");

    StepArtifacts result = runStep.handleArtifact(artifactMetadata, v1StepParameters, v1Ambiance);

    assertThat(result.getPublishedImageArtifacts()).hasSize(1);
    assertThat(result.getPublishedImageArtifacts().get(0).getDigest()).isEqualTo("sha256:gar456");
    assertThat(result.getPublishedImageArtifacts().get(0).getUrl()).contains("console.cloud.google.com/artifacts");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testHandleArtifactV1_ACRBuildAndPush() {
    Ambiance v1Ambiance = createV1Ambiance();

    RunStepInfoV1 runStepInfoV1 = createRunStepInfoV1WithACRPlugin();
    StepBaseParameters v1StepParameters = mock(StepBaseParameters.class);
    when(v1StepParameters.getVersion()).thenReturn(HarnessYamlVersion.V1);
    when(v1StepParameters.getSpec()).thenReturn(runStepInfoV1);

    ArtifactMetadata artifactMetadata = createDockerArtifactMetadata(
        "https://portal.azure.com", "myregistry.azurecr.io/myrepo/myimage:v3.0", "sha256:acr789");

    StepArtifacts result = runStep.handleArtifact(artifactMetadata, v1StepParameters, v1Ambiance);

    assertThat(result.getPublishedImageArtifacts()).hasSize(1);
    assertThat(result.getPublishedImageArtifacts().get(0).getDigest()).isEqualTo("sha256:acr789");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testHandleArtifactV1_WithNullArtifactMetadata() {
    Ambiance v1Ambiance = createV1Ambiance();

    RunStepInfoV1 runStepInfoV1 = createRunStepInfoV1WithDockerPluginAndWith("plugins/docker", "myrepo");
    StepBaseParameters v1StepParameters = mock(StepBaseParameters.class);
    when(v1StepParameters.getVersion()).thenReturn(HarnessYamlVersion.V1);
    when(v1StepParameters.getSpec()).thenReturn(runStepInfoV1);

    StepArtifacts result = runStep.handleArtifact(null, v1StepParameters, v1Ambiance);

    assertThat(result.getPublishedImageArtifacts()).isEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testHandleArtifactV1_WithNonPluginImage() {
    Ambiance v1Ambiance = createV1Ambiance();

    // Non-plugin image (e.g., alpine) should not trigger Docker artifact population
    Container container = Container.builder().image(ParameterField.createValueField("alpine:latest")).build();
    RunStepInfoV1 runStepInfoV1 = RunStepInfoV1.builder().container(container).build();

    StepBaseParameters v1StepParameters = mock(StepBaseParameters.class);
    when(v1StepParameters.getVersion()).thenReturn(HarnessYamlVersion.V1);
    when(v1StepParameters.getSpec()).thenReturn(runStepInfoV1);

    ArtifactMetadata artifactMetadata =
        createDockerArtifactMetadata("index.docker.io", "alpine:latest", "sha256:alpine123");

    StepArtifacts result = runStep.handleArtifact(artifactMetadata, v1StepParameters, v1Ambiance);

    // No image artifacts because the step is not a build and push step
    assertThat(result.getPublishedImageArtifacts()).isEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testHandleArtifactV1_WithMultipleDockerArtifacts() {
    Ambiance v1Ambiance = createV1Ambiance();

    RunStepInfoV1 runStepInfoV1 = createRunStepInfoV1WithDockerPluginAndWith("plugins/docker", "myrepo");
    StepBaseParameters v1StepParameters = mock(StepBaseParameters.class);
    when(v1StepParameters.getVersion()).thenReturn(HarnessYamlVersion.V1);
    when(v1StepParameters.getSpec()).thenReturn(runStepInfoV1);

    // Create artifact metadata with multiple images
    DockerArtifactDescriptor desc1 =
        DockerArtifactDescriptor.builder().imageName("myrepo/image1:tag1").digest("sha256:digest1").build();
    DockerArtifactDescriptor desc2 =
        DockerArtifactDescriptor.builder().imageName("myrepo/image2:tag2").digest("sha256:digest2").build();
    DockerArtifactMetadata dockerMetadata = DockerArtifactMetadata.builder()
                                                .registryUrl("index.docker.io")
                                                .dockerArtifacts(Arrays.asList(desc1, desc2))
                                                .build();
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder().type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA).spec(dockerMetadata).build();

    StepArtifacts result = runStep.handleArtifact(artifactMetadata, v1StepParameters, v1Ambiance);

    assertThat(result.getPublishedImageArtifacts()).hasSize(2);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testHandleArtifactV1_FileArtifactWithV1Step() {
    Ambiance v1Ambiance = createV1Ambiance();

    RunStepInfoV1 runStepInfoV1 = createRunStepInfoV1WithDockerPluginAndWith("plugins/docker", "myrepo");
    StepBaseParameters v1StepParameters = mock(StepBaseParameters.class);
    when(v1StepParameters.getVersion()).thenReturn(HarnessYamlVersion.V1);
    when(v1StepParameters.getSpec()).thenReturn(runStepInfoV1);

    // Create file artifact metadata
    ArtifactMetadata artifactMetadata = mock(ArtifactMetadata.class);
    FileArtifactMetadata fileArtifactMetadata = mock(FileArtifactMetadata.class);
    FileArtifactDescriptor descriptor = mock(FileArtifactDescriptor.class);
    when(descriptor.getUrl()).thenReturn("https://storage.example.com/artifact.zip");
    when(descriptor.getName()).thenReturn("artifact.zip");
    when(artifactMetadata.getType()).thenReturn(ArtifactMetadataType.FILE_ARTIFACT_METADATA);
    when(artifactMetadata.getSpec()).thenReturn(fileArtifactMetadata);
    when(fileArtifactMetadata.getFileArtifactDescriptors()).thenReturn(Collections.singletonList(descriptor));

    StepArtifacts result = runStep.handleArtifact(artifactMetadata, v1StepParameters, v1Ambiance);

    assertThat(result.getPublishedFileArtifacts()).hasSize(1);
    assertThat(result.getPublishedFileArtifacts().get(0).getUrl())
        .isEqualTo("https://storage.example.com/artifact.zip");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testHandleArtifactV1_ECRWithMissingRegistry() {
    Ambiance v1Ambiance = createV1Ambiance();

    // ECR plugin without REGISTRY in with field
    Container container = Container.builder().image(ParameterField.createValueField("plugins/ecr")).build();
    RunStepInfoV1 runStepInfoV1 = RunStepInfoV1.builder()
                                      .container(container)
                                      .with(ParameterField.createValueField(new HashMap<>())) // Empty with field
                                      .build();

    StepBaseParameters v1StepParameters = mock(StepBaseParameters.class);
    when(v1StepParameters.getVersion()).thenReturn(HarnessYamlVersion.V1);
    when(v1StepParameters.getSpec()).thenReturn(runStepInfoV1);

    ArtifactMetadata artifactMetadata = createDockerArtifactMetadata("123456789012.dkr.ecr.us-west-2.amazonaws.com",
        "123456789012.dkr.ecr.us-west-2.amazonaws.com/myrepo:v1.0", "sha256:ecr123");

    StepArtifacts result = runStep.handleArtifact(artifactMetadata, v1StepParameters, v1Ambiance);

    // Should not populate artifacts because registry/region info is missing
    assertThat(result.getPublishedImageArtifacts()).isEmpty();
  }

  // ==================================
  // Helper Methods for V1 Tests
  // ==================================

  private Ambiance createV1Ambiance() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");

    return Ambiance.newBuilder()
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPipelineIdentifier("pipelineId")
                         .setRunSequence(1)
                         .setHarnessVersion(HarnessYamlVersion.V1)
                         .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                         .build())
        .putAllSetupAbstractions(setupAbstractions)
        .addLevels(Level.newBuilder()
                       .setRuntimeId("runtimeId")
                       .setIdentifier("runStepId")
                       .setOriginalIdentifier("runStepId")
                       .setRetryIndex(1)
                       .build())
        .build();
  }

  private RunStepInfoV1 createRunStepInfoV1WithDockerPluginAndWith(String image, String repo) {
    Container container = Container.builder().image(ParameterField.createValueField(image)).build();

    ObjectMapper mapper = new ObjectMapper();
    Map<String, JsonNode> withMap = new HashMap<>();
    withMap.put("REPO", mapper.valueToTree(repo));

    return RunStepInfoV1.builder().container(container).with(ParameterField.createValueField(withMap)).build();
  }

  private RunStepInfoV1 createRunStepInfoV1WithECRPlugin() {
    Container container = Container.builder().image(ParameterField.createValueField("plugins/ecr")).build();

    ObjectMapper mapper = new ObjectMapper();
    Map<String, JsonNode> withMap = new HashMap<>();
    withMap.put("REGISTRY", mapper.valueToTree("123456789012.dkr.ecr.us-west-2.amazonaws.com"));
    withMap.put("REGION", mapper.valueToTree("us-west-2"));

    return RunStepInfoV1.builder().container(container).with(ParameterField.createValueField(withMap)).build();
  }

  private RunStepInfoV1 createRunStepInfoV1WithGARPlugin() {
    Container container = Container.builder().image(ParameterField.createValueField("plugins/gar")).build();

    ObjectMapper mapper = new ObjectMapper();
    Map<String, JsonNode> withMap = new HashMap<>();
    withMap.put("REGISTRY", mapper.valueToTree("us-central1-docker.pkg.dev/my-project"));

    return RunStepInfoV1.builder().container(container).with(ParameterField.createValueField(withMap)).build();
  }

  private RunStepInfoV1 createRunStepInfoV1WithACRPlugin() {
    Container container = Container.builder().image(ParameterField.createValueField("plugins/acr")).build();

    ObjectMapper mapper = new ObjectMapper();
    Map<String, JsonNode> withMap = new HashMap<>();
    withMap.put("REPO", mapper.valueToTree("myregistry.azurecr.io/myrepo"));

    Map<String, JsonNode> envMap = new HashMap<>();
    envMap.put("SUBSCRIPTION_ID", mapper.valueToTree("sub-123-456"));

    return RunStepInfoV1.builder()
        .container(container)
        .with(ParameterField.createValueField(withMap))
        .env(ParameterField.createValueField(envMap))
        .build();
  }

  private ArtifactMetadata createDockerHubArtifactMetadata(String imageName, String digest) {
    return createDockerArtifactMetadata("index.docker.io", imageName, digest);
  }

  private ArtifactMetadata createDockerArtifactMetadata(String registryUrl, String imageName, String digest) {
    DockerArtifactDescriptor descriptor =
        DockerArtifactDescriptor.builder().imageName(imageName).digest(digest).build();
    DockerArtifactMetadata dockerMetadata = DockerArtifactMetadata.builder()
                                                .registryUrl(registryUrl)
                                                .dockerArtifacts(Collections.singletonList(descriptor))
                                                .build();
    return ArtifactMetadata.builder().type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA).spec(dockerMetadata).build();
  }
}

/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.V1.cd;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.ConfigFilesOutcome;
import io.harness.cd.beans.outcomes.ConfigFilesSweepingOutput;
import io.harness.ci.execution.common.MapBasedReferenceExtractor;
import io.harness.ci.execution.common.MapBasedValidator;
import io.harness.ci.execution.common.ServiceStepOutcomeHelper;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.execution.utils.ScmGitFileOperationsHelper;
import io.harness.ci.states.V1.cd.ConfigFilesStep;
import io.harness.ci.states.V1.cd.ConfigFilesStepHelper;
import io.harness.ci.states.V1.cd.HarnessConfigFileStoreFetcher;
import io.harness.ci.states.V1.cd.ResponseHandlerUtils;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.plancreator.stages.v1.EmptyStepParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.runnercommons.cgi.task.git.RunnerGithubFetchFileTaskBuilder;
import io.harness.tasks.ResponseData;
import io.harness.utils.CDStepsExpressionResolver;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ConfigFilesStepTest {
  @Mock private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Mock private MapBasedReferenceExtractor mapBasedReferenceExtractor;
  @Mock private EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Mock private ResponseHandlerUtils responseHandlerUtils;
  @Mock private ScmGitFileOperationsHelper scmGitFileOperationsHelper;
  @Mock private RunnerGithubFetchFileTaskBuilder runnerGithubFetchFileTaskBuilder;
  @Mock private CommonAbstractStepUtils commonAbstractStepUtils;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private ServiceStepOutcomeHelper serviceStepOutcomeHelper;
  @Mock private MapBasedValidator mapBasedValidator;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private ConfigFilesStepHelper configFilesStepHelper;
  @Mock private HarnessConfigFileStoreFetcher harnessConfigFileStoreFetcher;

  @InjectMocks private ConfigFilesStep configFilesStep;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Map.of("accountId", "acc", "orgIdentifier", "org", "projectIdentifier", "proj"))
        .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(configFilesStep.getStepParametersClass()).isEqualTo(EmptyStepParameters.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac_NoConfigFiles() {
    Ambiance ambiance = buildAmbiance();
    EmptyStepParameters stepParameters = new EmptyStepParameters();

    when(serviceStepSweepingOutputHelper.fetchServiceConfigFilesSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    var response = configFilesStep.executeAsyncAfterRbac(ambiance, stepParameters, null);

    assertThat(response.getCallbackIdsList()).isEmpty();
    assertThat(response.getLogKeysList()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac_EmptyConfigFilesMap() {
    Ambiance ambiance = buildAmbiance();
    EmptyStepParameters stepParameters = new EmptyStepParameters();

    ConfigFilesSweepingOutput sweepingOutput =
        ConfigFilesSweepingOutput.builder().configFilesMetadataMap(new LinkedHashMap<>()).build();

    when(serviceStepSweepingOutputHelper.fetchServiceConfigFilesSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(sweepingOutput).build());

    var response = configFilesStep.executeAsyncAfterRbac(ambiance, stepParameters, null);

    assertThat(response.getCallbackIdsList()).isEmpty();
    assertThat(response.getLogKeysList()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_EmptyResponseMap_NoExistingOutcome() {
    Ambiance ambiance = buildAmbiance();
    EmptyStepParameters stepParameters = new EmptyStepParameters();
    Map<String, ResponseData> responseDataMap = new HashMap<>();

    when(serviceStepSweepingOutputHelper.fetchConfigFilesSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    StepResponse response = configFilesStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SKIPPED);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_EmptyResponseMap_WithExistingOutcome() {
    Ambiance ambiance = buildAmbiance();
    EmptyStepParameters stepParameters = new EmptyStepParameters();
    Map<String, ResponseData> responseDataMap = new HashMap<>();

    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    when(serviceStepSweepingOutputHelper.fetchConfigFilesSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(configFilesOutcome).build());

    StepResponse response = configFilesStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateResources_DoesNotThrow() {
    Ambiance ambiance = buildAmbiance();
    EmptyStepParameters stepParameters = new EmptyStepParameters();
    configFilesStep.validateResources(ambiance, stepParameters);
    // No exception means pass
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_K8Response_Success() {
    Ambiance ambiance = buildAmbiance();
    EmptyStepParameters stepParameters = new EmptyStepParameters();

    io.harness.delegate.task.stepstatus.StepStatusTaskResponseData stepStatusData =
        io.harness.delegate.task.stepstatus.StepStatusTaskResponseData.builder()
            .stepStatus(io.harness.delegate.task.stepstatus.StepStatus.builder()
                            .stepExecutionStatus(io.harness.delegate.task.stepstatus.StepExecutionStatus.SUCCESS)
                            .build())
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("callback-1", stepStatusData);

    when(serializedResponseDataHelper.deserialize(stepStatusData)).thenReturn(stepStatusData);

    K8StageInfraDetails k8StageInfraDetails = K8StageInfraDetails.builder().build();
    when(commonAbstractStepUtils.getStageInfra(any())).thenReturn(k8StageInfraDetails);

    when(serviceStepSweepingOutputHelper.fetchConfigFilesInfoSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(serviceStepSweepingOutputHelper.fetchConfigFilesSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(serviceStepSweepingOutputHelper.fetchConfigFileUnitStatusesSweepingOutput(any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    StepResponse response = configFilesStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }
}

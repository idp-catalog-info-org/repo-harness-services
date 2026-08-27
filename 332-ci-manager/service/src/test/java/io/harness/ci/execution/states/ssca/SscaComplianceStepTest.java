/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.ssca;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.TARUN_ACHARYA;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.CiStepParametersUtils;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CommonStepExecutionHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIStageOutputRepository;
import io.harness.rule.Owner;
import io.harness.ssca.beans.source.GithubComplianceSource;
import io.harness.ssca.beans.source.HarnessComplianceType;
import io.harness.ssca.beans.source.HarnessSscaComplianceSource;
import io.harness.ssca.beans.source.RepoSscaComplianceSource;
import io.harness.ssca.beans.source.SscaComplianceSource;
import io.harness.ssca.beans.source.SscaComplianceSourceType;
import io.harness.ssca.beans.stepinfo.SscaComplianceStepInfo;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.tasks.ResponseData;

import groovy.util.logging.Slf4j;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@lombok.extern.slf4j.Slf4j
@Slf4j
@OwnedBy(HarnessTeam.SSCA)
public class SscaComplianceStepTest extends CIExecutionTestBase {
  @InjectMocks SscaComplianceStep sscaComplianceStep;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;

  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;

  @Mock protected CIStageOutputRepository ciStageOutputRepository;
  @Mock CiStepParametersUtils ciStepParametersUtils;
  @Mock protected CIFeatureFlagService featureFlagService;
  @Mock private SSCAServiceUtils sscaServiceUtils;
  @Mock private CommonStepExecutionHelper commonStepExecutionHelper;

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandleK8sAsyncResponse() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    SscaComplianceStepInfo stepInfo =
        SscaComplianceStepInfo.builder()
            .name("Ssca_Compliance")
            .source(SscaComplianceSource.builder()
                        .type(SscaComplianceSourceType.GITHUB)
                        .sscaComplianceSourceSpec(GithubComplianceSource.builder()
                                                      .connectorRef(ParameterField.createValueField("connection"))
                                                      .repoName(ParameterField.createValueField("foobar"))
                                                      .scan_org(ParameterField.createValueField(true))
                                                      .build())
                        .build())
            .identifier(SscaTestsUtility.STEP_IDENTIFIER)
            .build();
    StepElementParameters stepElementParameters = SscaTestsUtility.getStepElementParameters(stepInfo);

    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder().stepExecutionStatus(StepExecutionStatus.SUCCESS).build())
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("response", stepStatusTaskResponseData);
    when(serializedResponseDataHelper.deserialize(stepStatusTaskResponseData)).thenReturn(stepStatusTaskResponseData);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(K8StageInfraDetails.builder().build()).build());

    StepResponse stepResponse =
        sscaComplianceStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandleK8sAsyncResponseForHarnessCiCd() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    SscaComplianceStepInfo stepInfo =
        SscaComplianceStepInfo.builder()
            .name("Ssca_Compliance")
            .source(SscaComplianceSource.builder()
                        .type(SscaComplianceSourceType.HARNESS)
                        .sscaComplianceSourceSpec(
                            HarnessSscaComplianceSource.builder()
                                .type(ParameterField.createValueField(HarnessComplianceType.PIPELINE))
                                .pipelineIds(ParameterField.createValueField(List.of("pipelineId1", "pipelineId2")))
                                .build())
                        .build())
            .identifier(SscaTestsUtility.STEP_IDENTIFIER)
            .build();
    StepElementParameters stepElementParameters = SscaTestsUtility.getStepElementParameters(stepInfo);

    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder().stepExecutionStatus(StepExecutionStatus.SUCCESS).build())
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("response", stepStatusTaskResponseData);
    when(serializedResponseDataHelper.deserialize(stepStatusTaskResponseData)).thenReturn(stepStatusTaskResponseData);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(K8StageInfraDetails.builder().build()).build());

    StepResponse stepResponse =
        sscaComplianceStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = TARUN_ACHARYA)
  @Category(UnitTests.class)
  public void testHandleVmAsyncResponse() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    SscaComplianceStepInfo stepInfo =
        SscaComplianceStepInfo.builder()
            .name("Ssca_Compliance")
            .source(SscaComplianceSource.builder()
                        .type(SscaComplianceSourceType.SCM)
                        .sscaComplianceSourceSpec(RepoSscaComplianceSource.builder()
                                                      .connectorRef(ParameterField.createValueField("connection"))
                                                      .repoName(ParameterField.createValueField("foobar"))
                                                      .scan_org(ParameterField.createValueField(true))
                                                      .build())
                        .build())
            .identifier(SscaTestsUtility.STEP_IDENTIFIER)
            .build();
    StepElementParameters stepElementParameters = SscaTestsUtility.getStepElementParameters(stepInfo);

    ResponseData responseData =
        VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("response", responseData);
    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(VmStageInfraDetails.builder().build()).build());

    StepResponse stepResponse =
        sscaComplianceStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }
}

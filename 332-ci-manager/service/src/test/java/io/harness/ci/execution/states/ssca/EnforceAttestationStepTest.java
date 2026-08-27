/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.ssca;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.VEDANT;

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
import io.harness.ssca.beans.attestation.scope.AttestedEventScope;
import io.harness.ssca.beans.attestation.scope.AttestedEventScopeType;
import io.harness.ssca.beans.policy.EnforcementPolicy;
import io.harness.ssca.beans.source.ImageSbomSource;
import io.harness.ssca.beans.source.SbomSource;
import io.harness.ssca.beans.source.SbomSourceType;
import io.harness.ssca.beans.stepinfo.EnforceAttestationStepInfo;
import io.harness.ssca.beans.store.HarnessStore;
import io.harness.ssca.beans.store.PolicyStore;
import io.harness.ssca.beans.store.StoreType;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.tasks.ResponseData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class EnforceAttestationStepTest extends CIExecutionTestBase {
  @InjectMocks EnforceAttestationStep enforceAttestationStep;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock protected CIStageOutputRepository ciStageOutputRepository;
  @Mock CiStepParametersUtils ciStepParametersUtils;
  @Mock protected CIFeatureFlagService featureFlagService;
  @Mock private SSCAServiceUtils sscaServiceUtils;
  @Mock private CommonStepExecutionHelper commonStepExecutionHelper;

  private EnforceAttestationStepInfo buildStepInfo() {
    return EnforceAttestationStepInfo.builder()
        .name("Enforce Attestation")
        .source(SbomSource.builder()
                    .type(SbomSourceType.IMAGE)
                    .sbomSourceSpec(ImageSbomSource.builder()
                                        .image(ParameterField.createValueField("myorg/myapp"))
                                        .connector(ParameterField.createValueField("docker_connector"))
                                        .build())
                    .build())
        .policy(EnforcementPolicy.builder()
                    .store(PolicyStore.builder()
                               .type(StoreType.HARNESS)
                               .storeSpec(HarnessStore.builder()
                                              .file(ParameterField.createValueField("/policies/attest.rego"))
                                              .build())
                               .build())
                    .build())
        .verifyAttestationSignatures(ParameterField.createValueField(true))
        .attestedEventScope(AttestedEventScope.builder()
                                .scope(AttestedEventScopeType.SPECIFIC)
                                .eventTypes(ParameterField.createValueField(Arrays.asList("Build", "Security")))
                                .build())
        .identifier(SscaTestsUtility.STEP_IDENTIFIER)
        .build();
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testHandleK8sAsyncResponse() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    EnforceAttestationStepInfo stepInfo = buildStepInfo();
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
        enforceAttestationStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testHandleVmAsyncResponse() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    EnforceAttestationStepInfo stepInfo = buildStepInfo();
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
        enforceAttestationStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }
}

/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.ssca;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.HUMANSHU_ARORA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.artifactSigning.beans.signing.source.ArtifactSigningSource;
import io.harness.artifactSigning.beans.signing.source.ArtifactSigningSourceType;
import io.harness.artifactSigning.beans.signing.source.DockerSourceSpec;
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
import io.harness.slsa.beans.verification.verify.CosignSlsaVerifyAttestation;
import io.harness.slsa.beans.verification.verify.SlsaVerifyAttestation;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.stepinfo.SscaArtifactVerificationStepInfo;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.tasks.ResponseData;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SscaArtifactVerificationStepTest extends CIExecutionTestBase {
  @InjectMocks SscaArtifactVerificationStep sscaArtifactVerificationStep;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;

  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;

  @Mock protected CIStageOutputRepository ciStageOutputRepository;
  @Mock CiStepParametersUtils ciStepParametersUtils;
  @Mock protected CIFeatureFlagService featureFlagService;
  @Mock private SSCAServiceUtils sscaServiceUtils;
  @Mock private CommonStepExecutionHelper commonStepExecutionHelper;

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testHandleK8sAsyncResponse() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    SscaArtifactVerificationStepInfo stepInfo =
        SscaArtifactVerificationStepInfo.builder()
            .name("Artifact Signing")
            .source(ArtifactSigningSource.builder()
                        .type(ArtifactSigningSourceType.DOCKER)
                        .spec(DockerSourceSpec.builder()
                                  .connector(ParameterField.createValueField("Docker Connector"))
                                  .image(ParameterField.createValueField("Image"))
                                  .build())
                        .build())
            .verifySign(SlsaVerifyAttestation.builder()
                            .type(AttestationType.COSIGN)
                            .slsaVerifyAttestationSpec(CosignSlsaVerifyAttestation.builder()
                                                           .key(ParameterField.createValueField("key"))
                                                           .publicKey(ParameterField.createValueField("publicKey"))
                                                           .build())
                            .build())
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
        sscaArtifactVerificationStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testHandleK8sAsyncResponseForHarnessCiCd() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    SscaArtifactVerificationStepInfo stepInfo =
        SscaArtifactVerificationStepInfo.builder()
            .name("Artifact Signing")
            .source(ArtifactSigningSource.builder()
                        .type(ArtifactSigningSourceType.DOCKER)
                        .spec(DockerSourceSpec.builder()
                                  .connector(ParameterField.createValueField("Docker Connector"))
                                  .image(ParameterField.createValueField("Image"))
                                  .build())
                        .build())
            .verifySign(SlsaVerifyAttestation.builder()
                            .type(AttestationType.COSIGN)
                            .slsaVerifyAttestationSpec(CosignSlsaVerifyAttestation.builder()
                                                           .key(ParameterField.createValueField("key"))
                                                           .publicKey(ParameterField.createValueField("publicKey"))
                                                           .build())
                            .build())
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
        sscaArtifactVerificationStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testHandleVmAsyncResponse() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    SscaArtifactVerificationStepInfo stepInfo =
        SscaArtifactVerificationStepInfo.builder()
            .name("Artifact Signing")
            .source(ArtifactSigningSource.builder()
                        .type(ArtifactSigningSourceType.DOCKER)
                        .spec(DockerSourceSpec.builder()
                                  .connector(ParameterField.createValueField("Docker Connector"))
                                  .image(ParameterField.createValueField("Image"))
                                  .build())
                        .build())
            .verifySign(SlsaVerifyAttestation.builder()
                            .type(AttestationType.COSIGN)
                            .slsaVerifyAttestationSpec(CosignSlsaVerifyAttestation.builder()
                                                           .key(ParameterField.createValueField("key"))
                                                           .publicKey(ParameterField.createValueField("publicKey"))
                                                           .build())
                            .build())
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
        sscaArtifactVerificationStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }
}

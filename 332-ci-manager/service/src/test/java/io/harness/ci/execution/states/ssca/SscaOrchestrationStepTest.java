/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.ssca;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.SHASHWAT_SACHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.delegate.task.stepstatus.artifact.ssca.DriftSummary;
import io.harness.delegate.task.stepstatus.artifact.ssca.OssRisksSummary;
import io.harness.delegate.task.stepstatus.artifact.ssca.Scorecard;
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
import io.harness.repositories.CIStageOutputRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.ssca.v1.model.OrchestrationDriftSummary;
import io.harness.spec.server.ssca.v1.model.OrchestrationOssRisksSummary;
import io.harness.spec.server.ssca.v1.model.OrchestrationScorecardSummary;
import io.harness.spec.server.ssca.v1.model.OrchestrationSummaryResponse;
import io.harness.ssca.beans.stepinfo.SscaOrchestrationStepInfo;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.execution.orchestration.outcome.PublishedSbomArtifact;
import io.harness.tasks.ResponseData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class SscaOrchestrationStepTest extends CIExecutionTestBase {
  @InjectMocks SscaOrchestrationStep sscaOrchestrationStep;

  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private SSCAServiceUtils sscaServiceUtils;
  @Mock protected CIFeatureFlagService featureFlagService;
  @Mock protected CIStageOutputRepository ciStageOutputRepository;
  @Mock private CommonStepExecutionHelper commonStepExecutionHelper;

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testHandleK8sAsyncResponse() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder().identifier(SscaTestsUtility.STEP_IDENTIFIER).build();
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

    when(sscaServiceUtils.getOrchestrationSummaryResponse(SscaTestsUtility.STEP_EXECUTION_ID,
             SscaTestsUtility.ACCOUNT_ID, SscaTestsUtility.ORG_ID, SscaTestsUtility.PROJECT_ID))
        .thenReturn(new OrchestrationSummaryResponse()
                        .artifact(new io.harness.spec.server.ssca.v1.model.Artifact()
                                      .type(io.harness.spec.server.ssca.v1.model.Artifact.TypeEnum.IMAGE)
                                      .name("library/nginx")
                                      .tag("latest")
                                      .id("someId")
                                      .registryUrl("https://someurl.com"))
                        .sbom(new io.harness.spec.server.ssca.v1.model.SbomDetails().name("blah_sbom"))
                        .isAttested(true)
                        .stepExecutionId(SscaTestsUtility.STEP_EXECUTION_ID));

    PublishedSbomArtifact publishedSbomArtifact = PublishedSbomArtifact.builder()
                                                      .stepExecutionId(SscaTestsUtility.STEP_EXECUTION_ID)
                                                      .imageName("library/nginx")
                                                      .name("library/nginx")
                                                      .type("image")
                                                      .url("https://someurl.com")
                                                      .id("someId")
                                                      .sbomName("blah_sbom")
                                                      .sbomUrl(null)
                                                      .tag("latest")
                                                      .build();
    StepResponse stepResponse =
        sscaOrchestrationStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(2);
    List<StepResponse.StepOutcome> stepOutcomeList = new ArrayList<>();
    stepResponse.getStepOutcomes().forEach(stepOutcome -> {
      if (stepOutcome.getOutcome() instanceof CIStepArtifactOutcome) {
        stepOutcomeList.add(stepOutcome);
      }
    });
    assertThat(stepOutcomeList).hasSize(1);
    stepOutcomeList.forEach(stepOutcome -> {
      assertThat(stepOutcome.getOutcome()).isInstanceOf(CIStepArtifactOutcome.class);
      CIStepArtifactOutcome outcome = (CIStepArtifactOutcome) stepOutcome.getOutcome();
      assertThat(outcome).isNotNull();
      assertThat(outcome.getStepArtifacts()).isNotNull();
      assertThat(outcome.getStepArtifacts().getPublishedSbomArtifacts()).isNotNull().hasSize(1);
      assertThat(outcome.getStepArtifacts().getPublishedSbomArtifacts().get(0)).isEqualTo(publishedSbomArtifact);
      assertThat(stepOutcome.getName()).isEqualTo("artifact_identifierId");
    });
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testHandleVmAsyncResponse() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder().identifier(SscaTestsUtility.STEP_IDENTIFIER).build();
    StepElementParameters stepElementParameters = SscaTestsUtility.getStepElementParameters(stepInfo);

    ResponseData responseData =
        VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("response", responseData);
    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(VmStageInfraDetails.builder().build()).build());

    when(sscaServiceUtils.getOrchestrationSummaryResponse(SscaTestsUtility.STEP_EXECUTION_ID,
             SscaTestsUtility.ACCOUNT_ID, SscaTestsUtility.ORG_ID, SscaTestsUtility.PROJECT_ID))
        .thenReturn(new OrchestrationSummaryResponse()
                        .artifact(new io.harness.spec.server.ssca.v1.model.Artifact()
                                      .type(io.harness.spec.server.ssca.v1.model.Artifact.TypeEnum.IMAGE)
                                      .name("library/nginx")
                                      .tag("latest")
                                      .id("someId")
                                      .registryUrl("https://someurl.com"))
                        .sbom(new io.harness.spec.server.ssca.v1.model.SbomDetails().name("blah_sbom"))
                        .isAttested(true)
                        .stepExecutionId(SscaTestsUtility.STEP_EXECUTION_ID));

    PublishedSbomArtifact publishedSbomArtifact =
        PublishedSbomArtifact.builder()
            .stepExecutionId(SscaTestsUtility.STEP_EXECUTION_ID)
            .imageName("library/nginx")
            .name("library/nginx")
            .type(String.valueOf(io.harness.spec.server.ssca.v1.model.Artifact.TypeEnum.IMAGE))
            .id("someId")
            .sbomName("blah_sbom")
            .url("https://someurl.com")
            .tag("latest")
            .build();
    StepResponse stepResponse =
        sscaOrchestrationStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(1);
    stepResponse.getStepOutcomes().forEach(stepOutcome -> {
      assertThat(stepOutcome.getOutcome()).isInstanceOf(CIStepArtifactOutcome.class);
      CIStepArtifactOutcome outcome = (CIStepArtifactOutcome) stepOutcome.getOutcome();
      assertThat(outcome).isNotNull();
      assertThat(outcome.getStepArtifacts()).isNotNull();
      assertThat(outcome.getStepArtifacts().getPublishedSbomArtifacts()).isNotNull().hasSize(1);
      assertThat(outcome.getStepArtifacts().getPublishedSbomArtifacts().get(0)).isEqualTo(publishedSbomArtifact);
      assertThat(stepOutcome.getName()).isEqualTo("artifact_identifierId");
    });
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testHandleK8sAsyncResponseSSCAManagerEnabled() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder().identifier(SscaTestsUtility.STEP_IDENTIFIER).build();
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

    when(sscaServiceUtils.getOrchestrationSummaryResponse(SscaTestsUtility.STEP_EXECUTION_ID,
             SscaTestsUtility.ACCOUNT_ID, SscaTestsUtility.ORG_ID, SscaTestsUtility.PROJECT_ID))
        .thenReturn(new OrchestrationSummaryResponse()
                        .artifact(new io.harness.spec.server.ssca.v1.model.Artifact()
                                      .type(io.harness.spec.server.ssca.v1.model.Artifact.TypeEnum.IMAGE)
                                      .name("library/nginx")
                                      .tag("latest")
                                      .id("someId")
                                      .registryUrl("https://someurl.com"))
                        .sbom(new io.harness.spec.server.ssca.v1.model.SbomDetails().name("blah_sbom"))
                        .isAttested(true)
                        .stepExecutionId(SscaTestsUtility.STEP_EXECUTION_ID)
                        .scorecardSummary(new OrchestrationScorecardSummary().avgScore("7.0").maxScore("10.0"))
                        .driftSummary(new OrchestrationDriftSummary()
                                          .driftId("someDriftId")
                                          .base("BASELINE")
                                          .baseTag("someBaseTag")
                                          .totalDrifts(4)
                                          .componentDrifts(3)
                                          .licenseDrifts(1)
                                          .componentsAdded(1)
                                          .componentsModified(1)
                                          .componentsDeleted(1)
                                          .licenseAdded(1)
                                          .licenseDeleted(0)));

    PublishedSbomArtifact publishedSbomArtifact =
        PublishedSbomArtifact.builder()
            .stepExecutionId(SscaTestsUtility.STEP_EXECUTION_ID)
            .imageName("library/nginx")
            .name("library/nginx")
            .type("image")
            .id("someId")
            .sbomName("blah_sbom")
            .url("https://someurl.com")
            .tag("latest")
            .scorecard(Scorecard.builder().avgScore("7.0").maxScore("10.0").build())
            .drift(DriftSummary.builder()
                       .base("BASELINE")
                       .driftId("someDriftId")
                       .baseTag("someBaseTag")
                       .totalDrifts(4)
                       .componentDrifts(3)
                       .licenseDrifts(1)
                       .componentsAdded(1)
                       .componentsModified(1)
                       .componentsDeleted(1)
                       .licenseAdded(1)
                       .licenseDeleted(0)
                       .build())
            .build();

    StepResponse stepResponse =
        sscaOrchestrationStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(2);
    List<StepResponse.StepOutcome> stepOutcomeList = new ArrayList<>();
    stepResponse.getStepOutcomes().forEach(stepOutcome -> {
      if (stepOutcome.getOutcome() instanceof CIStepArtifactOutcome) {
        stepOutcomeList.add(stepOutcome);
      }
    });
    assertThat(stepOutcomeList).hasSize(1);
    stepOutcomeList.forEach(stepOutcome -> {
      assertThat(stepOutcome.getOutcome()).isInstanceOf(CIStepArtifactOutcome.class);
      CIStepArtifactOutcome outcome = (CIStepArtifactOutcome) stepOutcome.getOutcome();
      assertThat(outcome).isNotNull();
      assertThat(outcome.getStepArtifacts()).isNotNull();
      assertThat(outcome.getStepArtifacts().getPublishedSbomArtifacts()).isNotNull().hasSize(1);
      assertThat(outcome.getStepArtifacts().getPublishedSbomArtifacts().get(0)).isEqualTo(publishedSbomArtifact);
      assertThat(stepOutcome.getName()).isEqualTo("artifact_identifierId");
    });
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testOssRisksSummaryIncludesMaliciousPackagesCounts() {
    assertOssRisksSummaryIncludesMaliciousPackagesCounts();
  }

  private void assertOssRisksSummaryIncludesMaliciousPackagesCounts() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder().identifier(SscaTestsUtility.STEP_IDENTIFIER).build();
    StepElementParameters stepElementParameters = SscaTestsUtility.getStepElementParameters(stepInfo);

    Map<String, ResponseData> responseDataMap = mockK8SuccessfulStepStatusAsyncResponse(ambiance);

    when(sscaServiceUtils.getOrchestrationSummaryResponse(SscaTestsUtility.STEP_EXECUTION_ID,
             SscaTestsUtility.ACCOUNT_ID, SscaTestsUtility.ORG_ID, SscaTestsUtility.PROJECT_ID))
        .thenReturn(orchestrationSummaryWithOssRisksFromSsca());

    PublishedSbomArtifact expected = publishedSbomArtifactWithOssRisks(6, 2);

    StepResponse stepResponse =
        sscaOrchestrationStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);

    List<StepResponse.StepOutcome> artifactOutcomes = new ArrayList<>();
    stepResponse.getStepOutcomes().forEach(stepOutcome -> {
      if (stepOutcome.getOutcome() instanceof CIStepArtifactOutcome) {
        artifactOutcomes.add(stepOutcome);
      }
    });
    assertThat(artifactOutcomes).hasSize(1);
    CIStepArtifactOutcome outcome = (CIStepArtifactOutcome) artifactOutcomes.get(0).getOutcome();
    assertThat(outcome.getStepArtifacts().getPublishedSbomArtifacts()).hasSize(1);
    PublishedSbomArtifact actual = outcome.getStepArtifacts().getPublishedSbomArtifacts().get(0);
    assertThat(actual).isEqualTo(expected);
  }

  private Map<String, ResponseData> mockK8SuccessfulStepStatusAsyncResponse(Ambiance ambiance) {
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
    return responseDataMap;
  }

  private static OrchestrationSummaryResponse orchestrationSummaryWithOssRisksFromSsca() {
    return new OrchestrationSummaryResponse()
        .artifact(new io.harness.spec.server.ssca.v1.model.Artifact()
                      .type(io.harness.spec.server.ssca.v1.model.Artifact.TypeEnum.IMAGE)
                      .name("library/nginx")
                      .tag("latest")
                      .id("someId")
                      .registryUrl("https://someurl.com"))
        .sbom(new io.harness.spec.server.ssca.v1.model.SbomDetails().name("blah_sbom"))
        .isAttested(true)
        .stepExecutionId(SscaTestsUtility.STEP_EXECUTION_ID)
        .ossRisksSummary(new OrchestrationOssRisksSummary()
                             .totalEolComponentCount(5)
                             .definiteEolComponentCount(3)
                             .derivedEolComponentCount(2)
                             .closeToEolComponentCount(1)
                             .unmaintainedComponentCount(4)
                             .outdatedComponentCount(7)
                             .typosquattedComponentCount(6)
                             .maliciousComponentCount(2));
  }

  private static PublishedSbomArtifact publishedSbomArtifactWithOssRisks(
      Integer typosquattedComponentCount, Integer maliciousComponentCount) {
    return PublishedSbomArtifact.builder()
        .stepExecutionId(SscaTestsUtility.STEP_EXECUTION_ID)
        .imageName("library/nginx")
        .name("library/nginx")
        .type("image")
        .url("https://someurl.com")
        .id("someId")
        .sbomName("blah_sbom")
        .tag("latest")
        .ossRisksSummary(OssRisksSummary.builder()
                             .totalEolComponentCount(5)
                             .definiteEolComponentCount(3)
                             .derivedEolComponentCount(2)
                             .closeToEolComponentCount(1)
                             .unmaintainedComponentCount(4)
                             .outdatedComponentCount(7)
                             .typosquattedComponentCount(typosquattedComponentCount)
                             .maliciousComponentCount(maliciousComponentCount)
                             .build())
        .build();
  }
}

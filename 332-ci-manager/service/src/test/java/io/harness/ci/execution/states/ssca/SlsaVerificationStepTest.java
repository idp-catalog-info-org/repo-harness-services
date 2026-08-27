/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.ssca;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.ABHIJEET_GUPTA;
import static io.harness.rule.OwnerRule.SHASHWAT_SACHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.ReleaseWebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.provenance.BuildDefinition;
import io.harness.beans.provenance.CodeMetadata;
import io.harness.beans.provenance.ExternalParameters;
import io.harness.beans.provenance.InternalParameters;
import io.harness.beans.provenance.Metadata;
import io.harness.beans.provenance.ProvenancePredicate;
import io.harness.beans.provenance.RunDetails;
import io.harness.beans.provenance.TriggerMetadata;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.steps.outcome.CIStepOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.ProvenanceMetaData;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CommonStepExecutionHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.slsa.beans.verification.source.SlsaDockerSourceSpec;
import io.harness.slsa.beans.verification.source.SlsaVerificationSource;
import io.harness.slsa.beans.verification.source.SlsaVerificationSourceType;
import io.harness.ssca.beans.stepinfo.SlsaVerificationStepInfo;
import io.harness.tasks.ResponseData;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)

public class SlsaVerificationStepTest extends CIExecutionTestBase {
  @InjectMocks SlsaVerificationStep slsaVerificationStep;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;

  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;

  @Mock protected CIFeatureFlagService featureFlagService;
  @Mock private CommonStepExecutionHelper commonStepExecutionHelper;

  private Ambiance ambiance;

  private HashMap<String, String> setupAbstractions = new HashMap<>();

  private SlsaVerificationStepInfo stepInfo;

  private ArtifactMetadata metadata;

  private String predicateString;

  @Before
  public void setUp() {
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");

    ambiance = Ambiance.newBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .setPipelineIdentifier("pipelineId")
                                    .setRunSequence(1)
                                    .setTriggerInfo(
                                        ExecutionTriggerInfo.newBuilder()
                                            .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("triggerBy").build())
                                            .build())
                                    .build())
                   .setPlanExecutionId("pipelineExecutionUuid")
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("runtimeId")
                                  .setIdentifier("identifierId")
                                  .setOriginalIdentifier("originalIdentifierId")
                                  .setRetryIndex(1)
                                  .build())
                   .build();

    stepInfo = SlsaVerificationStepInfo.builder()
                   .source(SlsaVerificationSource.builder()
                               .type(SlsaVerificationSourceType.DOCKER)
                               .spec(SlsaDockerSourceSpec.builder()
                                         .connector(ParameterField.createValueField("connectorRef"))
                                         .image_path(ParameterField.createValueField("image"))
                                         .tag(ParameterField.createValueField("2.0"))
                                         .build())
                               .build())
                   .build();

    BuildDefinition buildDefinition = getBuildDefinition(ambiance);
    RunDetails runDetails = RunDetails.builder().metadata(Metadata.builder().invocationId("12").build()).build();

    ProvenancePredicate predicate =
        ProvenancePredicate.builder().buildDefinition(buildDefinition).runDetails(runDetails).build();

    ObjectMapper mapper = new ObjectMapper();
    predicateString = null;
    try {
      predicateString = mapper.writeValueAsString(predicate);
    } catch (Exception e) {
    }

    when(featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, "accountId")).thenReturn(true);
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testHandleK8sAsyncResponse() {
    metadata = ArtifactMetadata.builder()
                   .type(ArtifactMetadataType.PROVENANCE_ARTIFACT_METADATA)
                   .spec(ProvenanceMetaData.builder().provenance(predicateString).build())
                   .build();

    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder()
                            .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                            .artifactMetadata(metadata)
                            .build())
            .build();

    StepElementParameters stepElementParameters = SscaTestsUtility.getStepElementParameters(stepInfo);

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("response", stepStatusTaskResponseData);
    when(serializedResponseDataHelper.deserialize(stepStatusTaskResponseData)).thenReturn(stepStatusTaskResponseData);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(K8StageInfraDetails.builder().build()).build());

    StepResponse stepResponse =
        slsaVerificationStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
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
      assertThat(outcome.getStepArtifacts().getProvenanceArtifacts()).isNotNull().hasSize(1);
      assertThat(stepOutcome.getName()).isEqualTo("artifact_identifierId");
    });
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testHandleArtifactsForVm() {
    StepElementParameters stepElementParameters = SscaTestsUtility.getStepElementParameters(stepInfo);

    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder().type(ArtifactMetadataType.PROVENANCE_ARTIFACT_METADATA).build();

    Map<String, String> outputVars = new HashMap<>();

    outputVars.put("SLSA_PROVENANCE_"
            + "runtimeId",
        predicateString);

    VmTaskExecutionResponse vmTaskExecutionResponse = VmTaskExecutionResponse.builder().outputVars(outputVars).build();

    StepArtifacts stepArtifacts = slsaVerificationStep.handleArtifactForVm(
        artifactMetadata, stepElementParameters, ambiance, vmTaskExecutionResponse);

    assertThat(stepArtifacts).isNotNull();
    assertThat(stepArtifacts.getProvenanceArtifacts()).isNotNull();
  }

  private BuildDefinition getBuildDefinition(Ambiance ambiance) {
    TriggerMetadata triggerMetadata = getTriggerMetadata(ambiance);
    CodeMetadata codeMetadata = getCodeMetada(ambiance);

    ExternalParameters externalParameters =
        ExternalParameters.builder().triggerMetadata(triggerMetadata).codeMetadata(codeMetadata).build();

    InternalParameters internalParameters = InternalParameters.builder()
                                                .pipelineExecutionId("pipelineExecutionUuid")
                                                .pipelineIdentifier("pipelineId")
                                                .accountId("accountId")
                                                .build();

    BuildDefinition buildDefinition = BuildDefinition.builder()
                                          .buildType("https://developer.harness.io/docs/continuous-integration")
                                          .externalParameters(externalParameters)
                                          .internalParameters(internalParameters)
                                          .build();
    return buildDefinition;
  }

  private TriggerMetadata getTriggerMetadata(Ambiance ambiance) {
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder()
                                    .executionSource(WebhookExecutionSource.builder()
                                                         .webhookEvent(ReleaseWebhookEvent.builder().build())
                                                         .build())
                                    .build())
                        .build());
    TriggerMetadata triggerMetadata = new TriggerMetadata("WEBHOOK", "triggerBy", "RELEASE");
    return triggerMetadata;
  }

  private CodeMetadata getCodeMetada(Ambiance ambiance) {
    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .tag("tag")
                                    .repoUrl("repoUrl")
                                    .commitSha("commitSha")
                                    .branch("branch")
                                    .prNumber("PrNumber")
                                    .build())
                        .build());
    CodeMetadata codeMetadata = new CodeMetadata("repoUrl", "branch", "PrNumber", "tag", "commitSha");
    return codeMetadata;
  }

  @Test
  @Owner(developers = ABHIJEET_GUPTA)
  @Category(UnitTests.class)
  public void testHandleK8sAsyncResponse_whenEmptyStepArtifacts_thenReturnBlankOutputVariables() {
    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder().stepExecutionStatus(StepExecutionStatus.SUCCESS).build())
            .build();

    StepElementParameters stepElementParameters = SscaTestsUtility.getStepElementParameters(stepInfo);

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("response", stepStatusTaskResponseData);
    when(serializedResponseDataHelper.deserialize(stepStatusTaskResponseData)).thenReturn(stepStatusTaskResponseData);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(K8StageInfraDetails.builder().build()).build());

    StepResponse stepResponse =
        slsaVerificationStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    List<StepResponse.StepOutcome> stepOutcomeList = new ArrayList<>(stepResponse.getStepOutcomes());
    assertThat(stepOutcomeList).hasSize(1);
    stepOutcomeList.forEach(stepOutcome -> {
      assertThat(stepOutcome.getOutcome()).isInstanceOf(CIStepOutcome.class);
      CIStepOutcome outcome = (CIStepOutcome) stepOutcome.getOutcome();
      assertThat(outcome).isNotNull();
      assertThat(outcome.getOutputVariables()).isNotNull().hasSize(0);
    });
  }
}

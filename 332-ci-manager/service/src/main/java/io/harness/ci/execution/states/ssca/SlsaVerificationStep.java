/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.ssca;

import static io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;
import static io.harness.ssca.beans.SscaConstants.PREDICATE_TYPE;
import static io.harness.ssca.beans.SscaConstants.SLSA_VERIFICATION_STEP_TYPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.artifact.ProvenanceArtifact;
import io.harness.beans.provenance.ProvenancePredicate;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.ci.execution.states.AbstractStepExecutable;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.ProvenanceMetaData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.CommonStepExecutionHelper;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.serializer.JsonUtils;
import io.harness.ssca.beans.stepinfo.SlsaVerificationStepInfo;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@OwnedBy(HarnessTeam.SSCA)
public class SlsaVerificationStep extends AbstractStepExecutable {
  private static final Logger log = LoggerFactory.getLogger(SlsaVerificationStep.class);
  public static final StepType STEP_TYPE = SLSA_VERIFICATION_STEP_TYPE;

  @Inject private CommonStepExecutionHelper commonStepExecutionHelper;

  protected boolean shouldPublishArtifact(StepStatus stepStatus) {
    return true;
  }

  protected boolean shouldPublishOutcome(StepStatus stepStatus) {
    return true;
  }

  @Override
  protected void modifyStepStatus(Ambiance ambiance, StepStatus stepStatus, String stepIdentifier) {
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String outputKey = "SLSA_PROVENANCE_" + stepExecutionId;
    StepMapOutput stepOutput = (StepMapOutput) stepStatus.getOutput();

    if (stepOutput != null && stepOutput.getMap() != null && stepOutput.getMap().containsKey(outputKey)) {
      stepStatus.setArtifactMetadata(
          ArtifactMetadata.builder()
              .type(ArtifactMetadataType.PROVENANCE_ARTIFACT_METADATA)
              .spec(ProvenanceMetaData.builder().provenance(stepOutput.getMap().get(outputKey)).build())
              .build());

      stepOutput.setMap(stepOutput.getMap()
                            .entrySet()
                            .stream()
                            .filter(entry -> !entry.getKey().equals(outputKey))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
  }

  @Override
  protected StepArtifacts handleArtifact(
      ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters, Ambiance ambiance) {
    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();
    SlsaVerificationStepInfo slsaVerificationStepInfo = (SlsaVerificationStepInfo) stepParameters.getSpec();

    if (artifactMetadata != null
        && ArtifactMetadataType.PROVENANCE_ARTIFACT_METADATA.equals(artifactMetadata.getType())) {
      ProvenanceMetaData provenanceMetaData = (ProvenanceMetaData) artifactMetadata.getSpec();
      ProvenancePredicate predicate = JsonUtils.asObject(provenanceMetaData.getProvenance(), ProvenancePredicate.class);
      stepArtifactsBuilder.provenanceArtifact(
          ProvenanceArtifact.builder().predicateType(PREDICATE_TYPE).predicate(predicate).build());
    }
    return stepArtifactsBuilder.build();
  }

  @Override
  protected StepArtifacts handleArtifactForVm(ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters,
      Ambiance ambiance, VmTaskExecutionResponse vmTaskExecutionResponse) {
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    Map<String, String> outputVariables =
        Optional
            .ofNullable(commonStepExecutionHelper.getOutputVariables(vmTaskExecutionResponse.getOutputs(),
                vmTaskExecutionResponse.getOutputVars(), AmbianceUtils.getAccountId(ambiance)))
            .orElse(Collections.emptyMap());
    String outputKey = "SLSA_PROVENANCE_" + stepExecutionId;
    String provenancePredicate = null;
    if (outputVariables.containsKey(outputKey)) {
      provenancePredicate = outputVariables.get(outputKey);
      if (vmTaskExecutionResponse.getOutputs() != null) {
        vmTaskExecutionResponse.setOutputs(vmTaskExecutionResponse.getOutputs()
                                               .stream()
                                               .filter(stepOutputV2 -> !stepOutputV2.getKey().equals(outputKey))
                                               .collect(Collectors.toList()));
      }
      if (EmptyPredicate.isNotEmpty(vmTaskExecutionResponse.getOutputVars())) {
        vmTaskExecutionResponse.setOutputVars(vmTaskExecutionResponse.getOutputVars()
                                                  .entrySet()
                                                  .stream()
                                                  .filter(entry -> !entry.getKey().equals(outputKey))
                                                  .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
      }
    }

    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();

    if (provenancePredicate != null) {
      ProvenancePredicate predicate = JsonUtils.asObject(provenancePredicate, ProvenancePredicate.class);
      stepArtifactsBuilder.provenanceArtifact(
          ProvenanceArtifact.builder().predicateType(PREDICATE_TYPE).predicate(predicate).build());
    }

    return stepArtifactsBuilder.build();
  }
}

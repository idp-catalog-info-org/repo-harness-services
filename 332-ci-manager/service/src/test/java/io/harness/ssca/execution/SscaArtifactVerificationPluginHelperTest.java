/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.rule.OwnerRule.AKSHAY_PANDEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactSigning.beans.signing.source.ArtifactSigningSource;
import io.harness.artifactSigning.beans.signing.source.ArtifactSigningSourceType;
import io.harness.artifactSigning.beans.signing.source.DockerSourceSpec;
import io.harness.artifactSigning.beans.signing.source.HarSourceSpec;
import io.harness.artifactSigning.execution.verification.SscaArtifactVerificationStepPluginUtils;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.slsa.beans.verification.verify.CosignSlsaVerifyAttestation;
import io.harness.slsa.beans.verification.verify.SlsaVerifyAttestation;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.stepinfo.SscaArtifactVerificationStepInfo;
import io.harness.ssca.client.NgSettingsUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.SSCA)
public class SscaArtifactVerificationPluginHelperTest {
  @InjectMocks private SscaArtifactVerificationPluginHelper sscaArtifactVerificationPluginHelper;
  @Mock private SscaPluginUtils sscaPluginUtils;
  @Mock private NgSettingsUtils ngSettingsUtils;

  private Ambiance ambiance;

  private static final String CONNECTOR = "connectorRef";
  private static final String DOCKER_REPO = "library/nginx:latest";
  private static final String COSIGN_PUBLIC_KEY = "cosignPublicKey";
  private static final String STEP_IDENTIFIER = "stepIdentifier_Verification_Step";
  private static final String HAR_REGISTRY = "harRegistry";
  private static final String HAR_IMAGE = "my-app:latest";
  private static final String HAR_REGISTRY_URL = "https://app.harness.io/registry";
  private static final String HAR_IMAGE_PATH = "accountId/harRegistry/my-app:latest";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    HashMap<String, String> setupAbstractions = new HashMap<>();
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
                                            .setTriggerType(TriggerType.MANUAL)
                                            .build())
                                    .build())
                   .setPlanExecutionId("pipelineExecutionUuid")
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("runtimeId")
                                  .setSetupId("setupId")
                                  .setStepType(StepType.newBuilder().setType("SSCA_VERIFICATION").build())
                                  .setIdentifier("stepId")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("stageExecutionId")
                                  .setSetupId("stageSetupId")
                                  .setIdentifier("stageName")
                                  .build())
                   .build();

    // Set the harnessArtifactRegistryUrl field using reflection
    Field harnessArtifactRegistryUrlField =
        SscaArtifactVerificationPluginHelper.class.getDeclaredField("harnessArtifactRegistryUrl");
    harnessArtifactRegistryUrlField.setAccessible(true);
    harnessArtifactRegistryUrlField.set(sscaArtifactVerificationPluginHelper, HAR_REGISTRY_URL);

    when(sscaPluginUtils.getStageName(any())).thenReturn("stageName");
    when(sscaPluginUtils.getStageExecutionIdentifier(any())).thenReturn("stageExecutionId");
    when(sscaPluginUtils.getPipelineTriggerBy(any())).thenReturn("triggerBy");
    when(sscaPluginUtils.getPipelineTriggerType(any())).thenReturn("MANUAL");
    when(sscaPluginUtils.getPipelineTriggerByEmail(any())).thenReturn("triggerBy@harness.io");
  }

  @Test
  @Owner(developers = AKSHAY_PANDEY)
  @Category(UnitTests.class)
  public void testGetArtifactVerificationDockerStepEnvVariables() {
    SscaArtifactVerificationStepInfo stepInfo = getDockerSourceSpec();
    Map<String, String> envMap = sscaArtifactVerificationPluginHelper.getSscaArtifactVerificationStepEnvVariables(
        stepInfo, STEP_IDENTIFIER, ambiance, Type.K8, false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(13);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_REPO, DOCKER_REPO);
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_REGISTRY_TYPE, "docker");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = AKSHAY_PANDEY)
  @Category(UnitTests.class)
  public void testGetArtifactVerificationHARStepEnvVariables() {
    when(ngSettingsUtils.getAirgapEnabled("accountId", "orgId", "projectId")).thenReturn(true);
    when(sscaPluginUtils.getHarnessArtifactRegistryImage(any(), any(), any())).thenReturn(HAR_IMAGE_PATH);
    SscaArtifactVerificationStepInfo stepInfo = getStepInfoForHar();
    Map<String, String> envMap = sscaArtifactVerificationPluginHelper.getSscaArtifactVerificationStepEnvVariables(
        stepInfo, STEP_IDENTIFIER, ambiance, Type.K8, false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(14);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_REPO, HAR_IMAGE_PATH);
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_REGISTRY_TYPE, "har");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.ENABLE_SSCA_AIRGAP, "true");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_REGISTRY, HAR_REGISTRY_URL);
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = AKSHAY_PANDEY)
  @Category(UnitTests.class)
  public void testGetArtifactVerificationHARStepEnvVariablesWithAirgapDisabled() {
    when(ngSettingsUtils.getAirgapEnabled("accountId", "orgId", "projectId")).thenReturn(false);
    when(sscaPluginUtils.getHarnessArtifactRegistryImage(any(), any(), any())).thenReturn(HAR_IMAGE_PATH);
    SscaArtifactVerificationStepInfo stepInfo = getStepInfoForHar();
    Map<String, String> envMap = sscaArtifactVerificationPluginHelper.getSscaArtifactVerificationStepEnvVariables(
        stepInfo, STEP_IDENTIFIER, ambiance, Type.K8, false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(14);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_REPO, HAR_IMAGE_PATH);
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_REGISTRY_TYPE, "har");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.ENABLE_SSCA_AIRGAP, "false");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_REGISTRY, HAR_REGISTRY_URL);
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = AKSHAY_PANDEY)
  @Category(UnitTests.class)
  public void testGetArtifactVerificationHARStepEnvVariablesWithoutRegistry() {
    when(ngSettingsUtils.getAirgapEnabled("accountId", "orgId", "projectId")).thenReturn(false);
    when(sscaPluginUtils.getHarnessArtifactRegistryImage(any(), isNull(), any())).thenReturn(HAR_IMAGE_PATH);
    SscaArtifactVerificationStepInfo stepInfo = getStepInfoForHarWithoutRegistry();
    Map<String, String> envMap = sscaArtifactVerificationPluginHelper.getSscaArtifactVerificationStepEnvVariables(
        stepInfo, STEP_IDENTIFIER, ambiance, Type.K8, false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(14);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_REPO, HAR_IMAGE_PATH);
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_REGISTRY_TYPE, "har");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.ENABLE_SSCA_AIRGAP, "false");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_REGISTRY, HAR_REGISTRY_URL);
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  private SscaArtifactVerificationStepInfo getDockerSourceSpec() {
    return SscaArtifactVerificationStepInfo.builder()
        .name("artifactVerificationStep")
        .source(ArtifactSigningSource.builder()
                    .type(ArtifactSigningSourceType.DOCKER)
                    .spec(DockerSourceSpec.builder()
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .image(ParameterField.createValueField(DOCKER_REPO))
                              .build())
                    .build())
        .verifySign(SlsaVerifyAttestation.builder()
                        .type(AttestationType.COSIGN)
                        .slsaVerifyAttestationSpec(CosignSlsaVerifyAttestation.builder()
                                                       .publicKey(ParameterField.createValueField(COSIGN_PUBLIC_KEY))
                                                       .build())
                        .build())
        .build();
  }

  private SscaArtifactVerificationStepInfo getStepInfoForHar() {
    return SscaArtifactVerificationStepInfo.builder()
        .name("artifactVerificationStep")
        .source(ArtifactSigningSource.builder()
                    .type(ArtifactSigningSourceType.HAR)
                    .spec(HarSourceSpec.builder()
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .registry(ParameterField.createValueField(HAR_REGISTRY))
                              .image(ParameterField.createValueField(HAR_IMAGE))
                              .build())
                    .build())
        .verifySign(SlsaVerifyAttestation.builder()
                        .type(AttestationType.COSIGN)
                        .slsaVerifyAttestationSpec(CosignSlsaVerifyAttestation.builder()
                                                       .publicKey(ParameterField.createValueField(COSIGN_PUBLIC_KEY))
                                                       .build())
                        .build())
        .build();
  }

  private SscaArtifactVerificationStepInfo getStepInfoForHarWithoutRegistry() {
    return SscaArtifactVerificationStepInfo.builder()
        .name("artifactVerificationStep")
        .source(ArtifactSigningSource.builder()
                    .type(ArtifactSigningSourceType.HAR)
                    .spec(HarSourceSpec.builder()
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .image(ParameterField.createValueField(HAR_IMAGE))
                              .build())
                    .build())
        .verifySign(SlsaVerifyAttestation.builder()
                        .type(AttestationType.COSIGN)
                        .slsaVerifyAttestationSpec(CosignSlsaVerifyAttestation.builder()
                                                       .publicKey(ParameterField.createValueField(COSIGN_PUBLIC_KEY))
                                                       .build())
                        .build())
        .build();
  }

  private Map<String, String> getExpectedEnvMap() {
    Map<String, String> expectedEnvMap = new HashMap<>();
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.STEP_EXECUTION_ID, "stageExecutionId");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_TYPE, "verify");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.STAGE_EXECUTION_ID, "stageExecutionId");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.STAGE_NAME, "stageName");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.STEP_NAME, "artifactVerificationStep");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.STEP_ID, "stepIdentifier_Verification_Step");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PIPELINE_TRIGGER_TYPE, "MANUAL");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PIPELINE_TRIGGER_BY, "triggerBy");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PIPELINE_TRIGGER_BY_EMAIL, "triggerBy@harness.io");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.PLUGIN_BASE64_SECRET, "false");
    expectedEnvMap.put(SscaArtifactVerificationStepPluginUtils.ENABLE_SSCA_AIRGAP, "false");
    return expectedEnvMap;
  }
}

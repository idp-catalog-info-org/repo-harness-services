/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.rule.OwnerRule.AKSHAY_PANDEY;
import static io.harness.rule.OwnerRule.HUMANSHU_ARORA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactSigning.beans.signing.beans.UploadSignature;
import io.harness.artifactSigning.beans.signing.sign.ArtifactSigningStepEnvVariables;
import io.harness.artifactSigning.beans.signing.source.AcrSourceSpec;
import io.harness.artifactSigning.beans.signing.source.ArtifactSigningSource;
import io.harness.artifactSigning.beans.signing.source.ArtifactSigningSourceType;
import io.harness.artifactSigning.beans.signing.source.DockerSourceSpec;
import io.harness.artifactSigning.beans.signing.source.EcrSourceSpec;
import io.harness.artifactSigning.beans.signing.source.GarSourceSpec;
import io.harness.artifactSigning.beans.signing.source.GcrSourceSpec;
import io.harness.artifactSigning.beans.signing.source.HarSourceSpec;
import io.harness.artifactSigning.beans.signing.source.LocalSourceSpec;
import io.harness.artifactSigning.execution.signing.SscaArtifactSigningStepPluginUtils;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.attestation.v1.AttestationV1;
import io.harness.ssca.beans.attestation.v1.CosignAttestationV1;
import io.harness.ssca.beans.stepinfo.SscaArtifactSigningStepInfo;
import io.harness.ssca.client.NgSettingsUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class SscaArtifactSigningPluginHelperTest extends CIExecutionTestBase {
  @InjectMocks private SscaArtifactSigningPluginHelper sscaArtifactSigningPluginHelper;
  @Mock private SscaPluginUtils sscaPluginUtils;
  @Mock private NgSettingsUtils ngSettingsUtils;

  private Ambiance ambiance;

  private static final String CONNECTOR = "connectorRef";
  private static final String DOCKER_REPO = "library/nginx:latest";
  private static final String COSIGN_PASS = "cosignPass";
  private static final String COSIGN_PRIVATE_KEY = "cosignKey";
  private static final String STEP_IDENTIFIER = "stepIdentifier_Signing_Step";
  private static final String GCR_HOST = "us.gcr.io";
  private static final String GCP_PROJECT = "my-project";
  private static final String ACR_REPO = "acrRegistry.azurecr.io/imageName:latest";
  private static final String ECR_REPO = "account.dkr.ecr.us-east-1.amazonaws.com";
  private static final String HAR_REGISTRY = "harRegistry";
  private static final String HAR_IMAGE = "my-app:latest";
  private static final String HAR_REGISTRY_URL = "https://app.harness.io/registry";
  private static final String HAR_IMAGE_PATH = "accountId/harRegistry/my-app:latest";

  @Before
  public void setup() throws Exception {
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
                                  .setIdentifier(STEP_IDENTIFIER)
                                  .setOriginalIdentifier("originalIdentifierId")
                                  .setRetryIndex(1)
                                  .build())
                   .build();

    // Set the harnessArtifactRegistryUrl field using reflection
    Field harnessArtifactRegistryUrlField =
        SscaArtifactSigningPluginHelper.class.getDeclaredField("harnessArtifactRegistryUrl");
    harnessArtifactRegistryUrlField.setAccessible(true);
    harnessArtifactRegistryUrlField.set(sscaArtifactSigningPluginHelper, HAR_REGISTRY_URL);

    when(sscaPluginUtils.getStageName(any())).thenReturn("stageName");
    when(sscaPluginUtils.getStageExecutionIdentifier(any())).thenReturn("stageExecutionId");
    when(sscaPluginUtils.getPipelineTriggerBy(any())).thenReturn("triggerBy");
    when(sscaPluginUtils.getPipelineTriggerType(any())).thenReturn("MANUAL");
    when(sscaPluginUtils.getPipelineTriggerByEmail(any())).thenReturn("triggerBy@harness.io");
    when(sscaPluginUtils.setGarAndGcrPluginRepo(any(), any())).thenReturn(DOCKER_REPO);
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetArtifactSigningDockerStepEnvVariables() {
    when(ngSettingsUtils.getAirgapEnabled("accountId", "orgId", "projectId")).thenReturn(true);
    SscaArtifactSigningStepInfo sscaArtifactSigningStepInfo = getDockerSourceSpec();
    Map<String, String> envMap = sscaArtifactSigningPluginHelper.getSscaArtifactSigningStepEnvVariables(
        sscaArtifactSigningStepInfo, STEP_IDENTIFIER, ambiance, Type.K8, false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(15);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REPO, DOCKER_REPO);
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY_TYPE, "docker");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.ENABLE_SSCA_AIRGAP, "true");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.UPLOAD_REGISTRY, "true");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetArtifactSigningACRStepEnvVariables() {
    when(ngSettingsUtils.getAirgapEnabled("accountId", "orgId", "projectId")).thenReturn(true);
    SscaArtifactSigningStepInfo sscaArtifactSigningStepInfo = getAcrSourceSpec();
    Map<String, String> envMap = sscaArtifactSigningPluginHelper.getSscaArtifactSigningStepEnvVariables(
        sscaArtifactSigningStepInfo, STEP_IDENTIFIER, ambiance, Type.K8, false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(16);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REPO, ACR_REPO);
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY_TYPE, "acr");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.ENABLE_SSCA_AIRGAP, "true");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY, "acrRegistry.azurecr.io");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.UPLOAD_REGISTRY, "true");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetArtifactSigningGCRStepEnvVariables() {
    when(ngSettingsUtils.getAirgapEnabled("accountId", "orgId", "projectId")).thenReturn(true);
    SscaArtifactSigningStepInfo sscaArtifactSigningStepInfo = getGcrSourceSpec();
    Map<String, String> envMap = sscaArtifactSigningPluginHelper.getSscaArtifactSigningStepEnvVariables(
        sscaArtifactSigningStepInfo, STEP_IDENTIFIER, ambiance, Type.K8, false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(16);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REPO, DOCKER_REPO);
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY_TYPE, "gcr");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.ENABLE_SSCA_AIRGAP, "true");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.UPLOAD_REGISTRY, "true");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY, "us.gcr.io");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetArtifactSigningGARStepEnvVariables() {
    when(ngSettingsUtils.getAirgapEnabled("accountId", "orgId", "projectId")).thenReturn(true);
    SscaArtifactSigningStepInfo sscaArtifactSigningStepInfo = getGarSourceSpec();
    Map<String, String> envMap = sscaArtifactSigningPluginHelper.getSscaArtifactSigningStepEnvVariables(
        sscaArtifactSigningStepInfo, STEP_IDENTIFIER, ambiance, Type.K8, false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(16);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REPO, DOCKER_REPO);
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY_TYPE, "gar");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.ENABLE_SSCA_AIRGAP, "true");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.UPLOAD_REGISTRY, "true");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY, "us.gcr.io");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetArtifactSigningECRStepEnvVariables() {
    when(ngSettingsUtils.getAirgapEnabled("accountId", "orgId", "projectId")).thenReturn(true);
    SscaArtifactSigningStepInfo sscaArtifactSigningStepInfo = getEcrSourceSpec();
    Map<String, String> envMap = sscaArtifactSigningPluginHelper.getSscaArtifactSigningStepEnvVariables(
        sscaArtifactSigningStepInfo, STEP_IDENTIFIER, ambiance, Type.K8, false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(17);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REPO, "image");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY_TYPE, "ecr");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.ENABLE_SSCA_AIRGAP, "true");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.UPLOAD_REGISTRY, "true");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY, ECR_REPO);
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGION, "us-east-1");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetArtifactSigningLocalStepEnvVariables() {
    SscaArtifactSigningStepInfo sscaArtifactSigningStepInfo = getLocalSourceSpec();
    Map<String, String> envMap = sscaArtifactSigningPluginHelper.getSscaArtifactSigningStepEnvVariables(
        sscaArtifactSigningStepInfo, STEP_IDENTIFIER, ambiance, Type.K8, false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(17);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_WORKSPACE, "workspace");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY_TYPE, "local");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_LOCAL_FILE_VERSION, "version");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_NON_CONTAINER_ARTIFACT_NAME, "artifactName");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.UPLOAD_REGISTRY, "true");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetArtifactSigningLocalStepEnvVariablesNullNameAndVersion() {
    SscaArtifactSigningStepInfo sscaArtifactSigningStepInfo = getLocalSourceSpec();
    ((LocalSourceSpec) sscaArtifactSigningStepInfo.getSource().getSpec()).setArtifactName(null);
    ((LocalSourceSpec) sscaArtifactSigningStepInfo.getSource().getSpec()).setVersion(null);
    Map<String, String> envMap = sscaArtifactSigningPluginHelper.getSscaArtifactSigningStepEnvVariables(
        sscaArtifactSigningStepInfo, STEP_IDENTIFIER, ambiance, Type.K8, false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(15);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_WORKSPACE, "workspace");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.UPLOAD_REGISTRY, "true");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY_TYPE, "local");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = AKSHAY_PANDEY)
  @Category(UnitTests.class)
  public void testGetSourceEnvVariablesForHar() {
    SscaArtifactSigningStepInfo stepInfo = getStepInfoForHar();
    when(sscaPluginUtils.getHarnessArtifactRegistryImage(any(), any(), any())).thenReturn(HAR_IMAGE_PATH);

    ArtifactSigningStepEnvVariables envVariables = ArtifactSigningStepEnvVariables.builder().build();
    sscaArtifactSigningPluginHelper.getSourceEnvVariables(stepInfo, STEP_IDENTIFIER, envVariables, false, ambiance);

    assertThat(envVariables.getPluginRegistry()).isEqualTo(HAR_REGISTRY_URL);
    assertThat(envVariables.getPluginRepo()).isEqualTo(HAR_IMAGE_PATH);
  }

  @Test
  @Owner(developers = AKSHAY_PANDEY)
  @Category(UnitTests.class)
  public void testGetSourceEnvVariablesForHarWithoutRegistry() {
    SscaArtifactSigningStepInfo stepInfo = getStepInfoForHarWithoutRegistry();
    when(sscaPluginUtils.getHarnessArtifactRegistryImage(any(), isNull(), any())).thenReturn(HAR_IMAGE_PATH);

    ArtifactSigningStepEnvVariables envVariables = ArtifactSigningStepEnvVariables.builder().build();
    sscaArtifactSigningPluginHelper.getSourceEnvVariables(stepInfo, STEP_IDENTIFIER, envVariables, false, ambiance);

    assertThat(envVariables.getPluginRegistry()).isEqualTo(HAR_REGISTRY_URL);
    assertThat(envVariables.getPluginRepo()).isEqualTo(HAR_IMAGE_PATH);
  }

  @Test
  @Owner(developers = AKSHAY_PANDEY)
  @Category(UnitTests.class)
  public void testGetArtifactSigningHARStepEnvVariables() {
    when(ngSettingsUtils.getAirgapEnabled("accountId", "orgId", "projectId")).thenReturn(true);
    when(sscaPluginUtils.getHarnessArtifactRegistryImage(any(), any(), any())).thenReturn(HAR_IMAGE_PATH);
    SscaArtifactSigningStepInfo sscaArtifactSigningStepInfo = getStepInfoForHar();
    Map<String, String> envMap = sscaArtifactSigningPluginHelper.getSscaArtifactSigningStepEnvVariables(
        sscaArtifactSigningStepInfo, STEP_IDENTIFIER, ambiance, Type.K8, false);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(16);
    Map<String, String> expectedEnvMap = getExpectedEnvMap();
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REPO, HAR_IMAGE_PATH);
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY_TYPE, "har");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.ENABLE_SSCA_AIRGAP, "true");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.UPLOAD_REGISTRY, "true");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_REGISTRY, HAR_REGISTRY_URL);
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  private SscaArtifactSigningStepInfo getLocalSourceSpec() {
    return SscaArtifactSigningStepInfo.builder()
        .name("artifactSigningStep")
        .source(ArtifactSigningSource.builder()
                    .type(ArtifactSigningSourceType.LOCAL)
                    .spec(LocalSourceSpec.builder()
                              .artifactName(ParameterField.createValueField("artifactName"))
                              .version(ParameterField.createValueField("version"))
                              .workspace(ParameterField.createValueField("workspace"))
                              .build())
                    .build())
        .signing(AttestationV1.builder()
                     .type(AttestationType.COSIGN)
                     .spec(CosignAttestationV1.builder()
                               .key(ParameterField.createValueField("key"))
                               .password(COSIGN_PASS)
                               .private_key(COSIGN_PRIVATE_KEY)
                               .build())
                     .build())
        .uploadSignature(UploadSignature.builder().upload(ParameterField.createValueField(true)).build())
        .build();
  }

  private SscaArtifactSigningStepInfo getGarSourceSpec() {
    return SscaArtifactSigningStepInfo.builder()
        .name("artifactSigningStep")
        .source(ArtifactSigningSource.builder()
                    .type(ArtifactSigningSourceType.GAR)
                    .spec(GarSourceSpec.builder()
                              .host(ParameterField.createValueField(GCR_HOST))
                              .image(ParameterField.createValueField(DOCKER_REPO))
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .projectId(ParameterField.createValueField(GCP_PROJECT))
                              .build())
                    .build())
        .signing(AttestationV1.builder()
                     .type(AttestationType.COSIGN)
                     .spec(CosignAttestationV1.builder()
                               .key(ParameterField.createValueField("key"))
                               .password(COSIGN_PASS)
                               .private_key(COSIGN_PRIVATE_KEY)
                               .build())
                     .build())
        .uploadSignature(UploadSignature.builder().upload(ParameterField.createValueField(true)).build())
        .build();
  }

  private SscaArtifactSigningStepInfo getDockerSourceSpec() {
    return SscaArtifactSigningStepInfo.builder()
        .name("artifactSigningStep")
        .source(ArtifactSigningSource.builder()
                    .type(ArtifactSigningSourceType.DOCKER)
                    .spec(DockerSourceSpec.builder()
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .image(ParameterField.createValueField(DOCKER_REPO))
                              .build())
                    .build())
        .signing(AttestationV1.builder()
                     .type(AttestationType.COSIGN)
                     .spec(CosignAttestationV1.builder()
                               .key(ParameterField.createValueField("key"))
                               .password(COSIGN_PASS)
                               .private_key(COSIGN_PRIVATE_KEY)
                               .build())
                     .build())
        .uploadSignature(UploadSignature.builder().upload(ParameterField.createValueField(true)).build())
        .build();
  }

  private SscaArtifactSigningStepInfo getGcrSourceSpec() {
    return SscaArtifactSigningStepInfo.builder()
        .name("artifactSigningStep")
        .source(ArtifactSigningSource.builder()
                    .type(ArtifactSigningSourceType.GCR)
                    .spec(GcrSourceSpec.builder()
                              .host(ParameterField.createValueField(GCR_HOST))
                              .image(ParameterField.createValueField(DOCKER_REPO))
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .projectId(ParameterField.createValueField(GCP_PROJECT))
                              .build())
                    .build())
        .signing(AttestationV1.builder()
                     .type(AttestationType.COSIGN)
                     .spec(CosignAttestationV1.builder()
                               .key(ParameterField.createValueField("key"))
                               .password(COSIGN_PASS)
                               .private_key(COSIGN_PRIVATE_KEY)
                               .build())
                     .build())
        .uploadSignature(UploadSignature.builder().upload(ParameterField.createValueField(true)).build())
        .build();
  }

  private SscaArtifactSigningStepInfo getEcrSourceSpec() {
    return SscaArtifactSigningStepInfo.builder()
        .name("artifactSigningStep")
        .source(ArtifactSigningSource.builder()
                    .type(ArtifactSigningSourceType.ECR)
                    .spec(EcrSourceSpec.builder()
                              .image(ParameterField.createValueField("image"))
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .region(ParameterField.createValueField("us-east-1"))
                              .account(ParameterField.createValueField("account"))
                              .build())
                    .build())
        .signing(AttestationV1.builder()
                     .type(AttestationType.COSIGN)
                     .spec(CosignAttestationV1.builder()
                               .key(ParameterField.createValueField("key"))
                               .password(COSIGN_PASS)
                               .private_key(COSIGN_PRIVATE_KEY)
                               .build())
                     .build())
        .uploadSignature(UploadSignature.builder().upload(ParameterField.createValueField(true)).build())
        .build();
  }

  private SscaArtifactSigningStepInfo getAcrSourceSpec() {
    return SscaArtifactSigningStepInfo.builder()
        .name("artifactSigningStep")
        .source(ArtifactSigningSource.builder()
                    .type(ArtifactSigningSourceType.ACR)
                    .spec(AcrSourceSpec.builder()
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .image(ParameterField.createValueField(ACR_REPO))
                              .build())
                    .build())
        .signing(AttestationV1.builder()
                     .type(AttestationType.COSIGN)
                     .spec(CosignAttestationV1.builder()
                               .password(COSIGN_PASS)
                               .key(ParameterField.createValueField("key"))
                               .private_key(COSIGN_PRIVATE_KEY)
                               .build())
                     .build())
        .uploadSignature(UploadSignature.builder().upload(ParameterField.createValueField(true)).build())
        .build();
  }

  private SscaArtifactSigningStepInfo getStepInfoForHar() {
    return SscaArtifactSigningStepInfo.builder()
        .name("artifactSigningStep")
        .source(ArtifactSigningSource.builder()
                    .type(ArtifactSigningSourceType.HAR)
                    .spec(HarSourceSpec.builder()
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .registry(ParameterField.createValueField(HAR_REGISTRY))
                              .image(ParameterField.createValueField(HAR_IMAGE))
                              .build())
                    .build())
        .signing(AttestationV1.builder()
                     .type(AttestationType.COSIGN)
                     .spec(CosignAttestationV1.builder()
                               .key(ParameterField.createValueField("key"))
                               .password(COSIGN_PASS)
                               .private_key(COSIGN_PRIVATE_KEY)
                               .build())
                     .build())
        .uploadSignature(UploadSignature.builder().upload(ParameterField.createValueField(true)).build())
        .build();
  }

  private SscaArtifactSigningStepInfo getStepInfoForHarWithoutRegistry() {
    return SscaArtifactSigningStepInfo.builder()
        .name("artifactSigningStep")
        .source(ArtifactSigningSource.builder()
                    .type(ArtifactSigningSourceType.HAR)
                    .spec(HarSourceSpec.builder()
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .image(ParameterField.createValueField(HAR_IMAGE))
                              .build())
                    .build())
        .signing(AttestationV1.builder()
                     .type(AttestationType.COSIGN)
                     .spec(CosignAttestationV1.builder()
                               .key(ParameterField.createValueField("key"))
                               .password(COSIGN_PASS)
                               .private_key(COSIGN_PRIVATE_KEY)
                               .build())
                     .build())
        .uploadSignature(UploadSignature.builder().upload(ParameterField.createValueField(true)).build())
        .build();
  }

  private Map<String, String> getExpectedEnvMap() {
    Map<String, String> expectedEnvMap = new HashMap<>();
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.STEP_EXECUTION_ID, "runtimeId");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_TYPE, "sign");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.STAGE_EXECUTION_ID, "stageExecutionId");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.STAGE_NAME, "stageName");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.STEP_NAME, "artifactSigningStep");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.STEP_ID, "stepIdentifier_Signing_Step");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.VAULT_COSIGN_KEY_PATH, "key");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PLUGIN_BASE64_SECRET, "false");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.ENABLE_SSCA_AIRGAP, "false");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PIPELINE_TRIGGER_TYPE, "MANUAL");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PIPELINE_TRIGGER_BY, "triggerBy");
    expectedEnvMap.put(SscaArtifactSigningStepPluginUtils.PIPELINE_TRIGGER_BY_EMAIL, "triggerBy@harness.io");
    return expectedEnvMap;
  }
}

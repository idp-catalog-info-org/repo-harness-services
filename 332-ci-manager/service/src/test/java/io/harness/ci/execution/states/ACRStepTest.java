/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.SHASHWAT_SACHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.execution.artifact.ProvenanceArtifact;
import io.harness.beans.provenance.BuildDefinition;
import io.harness.beans.provenance.BuildMetadata;
import io.harness.beans.provenance.ExternalParameters;
import io.harness.beans.provenance.InternalParameters;
import io.harness.beans.provenance.Metadata;
import io.harness.beans.provenance.ProvenanceBuilder;
import io.harness.beans.provenance.ProvenanceBuilderData;
import io.harness.beans.provenance.ProvenanceGenerator;
import io.harness.beans.provenance.ProvenancePredicate;
import io.harness.beans.provenance.RunDetails;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.stepinfo.ACRStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.execution.execution.CIExecutionConfigServiceImpl;
import io.harness.ci.execution.execution.artifactDetails.ArtifactDetailsServiceImpl;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactDescriptor;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIStageOutputRepository;
import io.harness.rule.Owner;
import io.harness.ssca.execution.provenance.ProvenanceStepGenerator;

import com.google.api.client.util.DateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class ACRStepTest extends CIExecutionTestBase {
  @InjectMocks ACRStep acrStep;
  @Mock SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock protected CIFeatureFlagService featureFlagService;
  @Mock protected CIStageOutputRepository ciStageOutputRepository;
  @Mock private CIExecutionConfigServiceImpl ciExecutionConfigService;
  @Mock private ProvenanceGenerator provenanceGenerator;
  @Mock ArtifactDetailsServiceImpl artifactDetailsService;

  private Ambiance ambiance;
  private HashMap<String, String> setupAbstractions = new HashMap<>();
  private StepElementParameters stepElementParameters;

  public static final String STEP_ID = "acrStepID";

  @Before
  public void setUp() {
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");

    ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipelineId").setRunSequence(1).build())
            .putAllSetupAbstractions(setupAbstractions)
            .addLevels(Level.newBuilder()
                           .setRuntimeId("runtimeId")
                           .setIdentifier("identifierId")
                           .setOriginalIdentifier("originalIdentifierId")
                           .setRetryIndex(1)
                           .build())
            .build();
    on(acrStep).set("artifactDetailsService", artifactDetailsService);
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void shouldHandleArtifact() {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("stepId")
            .spec(ACRStepInfo.builder()
                      .connectorRef(ParameterField.createValueField("connectorRef"))
                      .repository(ParameterField.createValueField("acrRegistry/image"))
                      .subscriptionId(ParameterField.createValueField("subscriptionId"))
                      .tags(ParameterField.createValueField(Arrays.asList("1.0", "latest")))
                      .build())
            .build();
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder()
            .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
            .spec(DockerArtifactMetadata.builder()
                      .registryType("ACR")
                      .registryUrl("registry.azurecr.io")
                      .dockerArtifact(
                          DockerArtifactDescriptor.builder()
                              .imageName("harness/ci-automation:1.0")
                              .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                              .build())
                      .dockerArtifact(
                          DockerArtifactDescriptor.builder()
                              .imageName("harness/ci-automation:latest")
                              .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71")
                              .build())
                      .build())
            .build();
    StepArtifacts stepArtifacts = acrStep.handleArtifact(artifactMetadata, stepElementParameters, ambiance);
    assertThat(stepArtifacts).isNotNull();
    assertThat(stepArtifacts.getPublishedImageArtifacts())
        .contains(PublishedImageArtifact.builder()
                      .imageName("harness/ci-automation")
                      .tag("1.0")
                      .url("registry.azurecr.io/repositoryName/image/tag/1.0/loginServer/acrRegistry")
                      .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                      .build(),
            PublishedImageArtifact.builder()
                .imageName("harness/ci-automation")
                .tag("latest")
                .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71")
                .url("registry.azurecr.io/repositoryName/image/tag/latest/loginServer/acrRegistry")
                .build());
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void shouldHandleArtifactWithProvenance() {
    when(featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, "accountId")).thenReturn(true);

    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("stepId")
            .spec(ACRStepInfo.builder()
                      .connectorRef(ParameterField.createValueField("connectorRef"))
                      .repository(ParameterField.createValueField("acrRegistry/image"))
                      .subscriptionId(ParameterField.createValueField("subscriptionId"))
                      .tags(ParameterField.createValueField(Arrays.asList("1.0", "latest")))
                      .build())
            .build();
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder()
            .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
            .spec(DockerArtifactMetadata.builder()
                      .registryType("ACR")
                      .registryUrl("registry.azurecr.io")
                      .dockerArtifact(
                          DockerArtifactDescriptor.builder()
                              .imageName("harness/ci-automation:1.0")
                              .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                              .build())
                      .dockerArtifact(
                          DockerArtifactDescriptor.builder()
                              .imageName("harness/ci-automation:latest")
                              .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71")
                              .build())
                      .build())
            .build();

    when(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ACR, "accountId"))
        .thenReturn(StepImageConfig.builder().image("plugins/kaniko-acr:0.0.0").build());

    ProvenanceBuilderData provenanceBuilder =
        ProvenanceBuilderData.builder()
            .accountId("accountId")
            .stepExecutionId("runtimeId")
            .pipelineIdentifier("pipelineId")
            .pipelineExecutionId("")
            .startTime(ambiance.getStartTs())
            .pluginInfo("plugins/kaniko-acr:0.0.0")
            .buildMetadata(BuildMetadata.builder().image("ci-unittest").dockerFile("./dockerfile").build())
            .build();

    Map<String, String> versionMap = new HashMap<>();
    versionMap.put("plugins/kaniko-acr", "0.0.0");
    ProvenancePredicate predicate =
        ProvenancePredicate.builder()
            .buildDefinition(
                BuildDefinition.builder()
                    .buildType("https://developer.harness.io/docs/continuous-integration")
                    .internalParameters(InternalParameters.builder()
                                            .accountId(provenanceBuilder.getAccountId())
                                            .pipelineExecutionId(provenanceBuilder.getPipelineExecutionId())
                                            .pipelineIdentifier(provenanceBuilder.getPipelineIdentifier())
                                            .build())
                    .externalParameters(
                        ExternalParameters.builder().buildMetadata(provenanceBuilder.getBuildMetadata()).build())
                    .build())
            .runDetails(RunDetails.builder()
                            .builder(ProvenanceBuilder.builder()
                                         .id("https://developer.harness.io/docs/continuous-integration")
                                         .version(versionMap)
                                         .build())
                            .metadata(Metadata.builder()
                                          .invocationId(provenanceBuilder.getStepExecutionId())
                                          .startedOn(new DateTime(provenanceBuilder.getStartTime()).toStringRfc3339())
                                          .finishedOn(new DateTime(System.currentTimeMillis()).toStringRfc3339())
                                          .build())
                            .build())
            .build();

    doReturn(predicate).when(provenanceGenerator).buildProvenancePredicate(any(), any());

    Mockito.mockStatic(ProvenanceStepGenerator.class);
    when(ProvenanceStepGenerator.getAllowedTypesForProvenance())
        .thenReturn(List.of(CIStepInfoType.DOCKER, CIStepInfoType.ACR));

    StepArtifacts stepArtifacts = acrStep.handleArtifact(artifactMetadata, stepElementParameters, ambiance);
    assertThat(stepArtifacts).isNotNull();
    assertThat(stepArtifacts.getPublishedImageArtifacts())
        .contains(PublishedImageArtifact.builder()
                      .imageName("harness/ci-automation")
                      .tag("1.0")
                      .url("registry.azurecr.io/repositoryName/image/tag/1.0/loginServer/acrRegistry")
                      .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                      .build());
    assertThat(stepArtifacts.getProvenanceArtifacts())
        .contains(
            ProvenanceArtifact.builder().predicateType("https://slsa.dev/provenance/v1").predicate(predicate).build());
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testPublishArtifactDetails() {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("stepId")
            .spec(ACRStepInfo.builder()
                      .connectorRef(ParameterField.createValueField("connectorRef"))
                      .repository(ParameterField.createValueField("acrRegistry/image"))
                      .subscriptionId(ParameterField.createValueField("subscriptionId"))
                      .tags(ParameterField.createValueField(Arrays.asList("1.0", "latest")))
                      .build())
            .build();
    ArtifactMetadata artifactMetadata = ArtifactMetadata.builder()
                                            .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
                                            .spec(DockerArtifactMetadata.builder()
                                                      .registryType("ACR")
                                                      .registryUrl("registry.azurecr.io")
                                                      .dockerArtifact(DockerArtifactDescriptor.builder()
                                                                          .imageName("harness/ci-automation:1.0")
                                                                          .digest("sha256:digest1")
                                                                          .build())
                                                      .dockerArtifact(DockerArtifactDescriptor.builder()
                                                                          .imageName("harness/ci-automation:latest")
                                                                          .digest("sha256:digest2")
                                                                          .build())
                                                      .build())
                                            .build();

    when(artifactDetailsService.saveDockerArtifactDetails(any(), any())).thenReturn(null);

    ArgumentCaptor<PublishedImageArtifact> captor = ArgumentCaptor.forClass(PublishedImageArtifact.class);
    acrStep.handleArtifact(artifactMetadata, stepElementParameters, ambiance);
    verify(artifactDetailsService, times(2)).saveDockerArtifactDetails(captor.capture(), any());
    assertThat(captor.getAllValues().get(0).getImageName()).isEqualTo("harness/ci-automation");
    assertThat(captor.getAllValues().get(0).getTag()).isEqualTo("1.0");
    assertThat(captor.getAllValues().get(1).getImageName()).isEqualTo("harness/ci-automation");
    assertThat(captor.getAllValues().get(1).getTag()).isEqualTo("latest");
  }
}

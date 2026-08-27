/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SHASHWAT_SACHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.stepinfo.ECRStepInfo;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.execution.execution.CIExecutionConfigServiceImpl;
import io.harness.ci.execution.execution.artifactDetails.ArtifactDetailsServiceImpl;
import io.harness.ci.execution.integrationstage.K8InitializeStepUtilsHelper;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactDescriptor;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SSCAModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CommonStepExecutionHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIStageOutputRepository;
import io.harness.rule.Owner;
import io.harness.ssca.execution.SSCALicenseHelper;
import io.harness.ssca.execution.provenance.ProvenanceStepGenerator;
import io.harness.tasks.ResponseData;

import com.google.api.client.util.DateTime;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.mockito.stubbing.Answer;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CI)
public class ECRStepTest extends CIExecutionTestBase {
  @InjectMocks ECRStep ecrStep;
  @Mock SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock protected CIFeatureFlagService featureFlagService;
  @Mock protected CIStageOutputRepository ciStageOutputRepository;
  @Mock private CIExecutionConfigServiceImpl ciExecutionConfigService;
  @Mock private ProvenanceGenerator provenanceGenerator;
  @Inject SSCALicenseHelper sscaLicenseHelper;
  @Mock NgLicenseHttpClient ngLicenseHttpClient;
  @Mock CommonStepExecutionHelper commonStepExecutionHelper;
  @Mock ArtifactDetailsServiceImpl artifactDetailsService;

  public static final String STEP_ID = "ecrStepID";
  private Ambiance ambiance;
  private HashMap<String, String> setupAbstractions = new HashMap<>();
  private StepElementParameters stepElementParameters;
  Call<ResponseDTO<AccountLicenseDTO>> call = Mockito.mock(Call.class);

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

    ECRStepInfo stepInfo = ECRStepInfo.builder()
                               .identifier(STEP_ID)
                               .account(ParameterField.createValueField("accountId"))
                               .region(ParameterField.createValueField("us-east-1"))
                               .connectorRef(ParameterField.createValueField("connectorRef"))
                               .tags(ParameterField.createValueField(Arrays.asList("1.0", "2.0")))
                               .build();
    stepElementParameters =
        StepElementParameters.builder().identifier("identifier").name("name").spec(stepInfo).build();
    on(sscaLicenseHelper).set("ngLicenseHttpClient", ngLicenseHttpClient);
    on(ecrStep).set("sscaLicenseHelper", sscaLicenseHelper);
    on(ecrStep).set("artifactDetailsService", artifactDetailsService);
    when(ngLicenseHttpClient.getAccountLicensesDTO(Mockito.any()))
        .thenAnswer((Answer<Call<ResponseDTO<AccountLicenseDTO>>>) invocation -> {
          when(call.execute())
              .thenReturn(Response.success(ResponseDTO.newResponse(AccountLicenseDTO.builder().build())));
          when(call.clone()).thenReturn(null);
          return call;
        });
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldHandleArtifact() {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("stepId")
            .spec(ECRStepInfo.builder()
                      .account(ParameterField.createValueField("854707204582"))
                      .region(ParameterField.createValueField("us-east-1"))
                      .imageName(ParameterField.createValueField("harness/ci-unittest"))
                      .tags(ParameterField.createValueField(Arrays.asList("1.0", "latest")))
                      .build())
            .build();
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder()
            .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
            .spec(DockerArtifactMetadata.builder()
                      .registryType("ECR")
                      .registryUrl("https://854707204582.dkr.ecr.us-east-1.amazonaws.com")
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
    StepArtifacts stepArtifacts = ecrStep.handleArtifact(artifactMetadata, stepElementParameters, ambiance);
    assertThat(stepArtifacts).isNotNull();
    assertThat(stepArtifacts.getPublishedImageArtifacts())
        .contains(PublishedImageArtifact.builder()
                      .imageName("harness/ci-automation")
                      .tag("1.0")
                      .url("https://console.aws.amazon.com/ecr/repositories/private/854707204582/harness/ci-automation/"
                          + "_/image/sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70/details/"
                          + "?region=us-east-1")
                      .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                      .build(),
            PublishedImageArtifact.builder()
                .imageName("harness/ci-automation")
                .tag("latest")
                .url("https://console.aws.amazon.com/ecr/repositories/private/854707204582/harness/ci-automation/_/"
                    + "image/sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71/details/"
                    + "?region=us-east-1")
                .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71")
                .build());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldHandleSuccessVMAsyncResponseWithArtifacts() {
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    K8InitializeStepUtilsHelper k8InitializeStepUtilsHelper = new K8InitializeStepUtilsHelper();
    String artifact = k8InitializeStepUtilsHelper.readFile("ecr-artifact.json");
    ResponseData responseData = VmTaskExecutionResponse.builder()
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .artifact(artifact.getBytes(StandardCharsets.UTF_8))
                                    .build();

    responseDataMap.put("response", responseData);
    PublishedImageArtifact expectedArtifact =
        PublishedImageArtifact.builder()
            .imageName("test")
            .tag("1.0")
            .url("https://console.aws.amazon.com/ecr/repositories/private/accountId/test/_/image/sha256:digest/details/"
                + "?region=us-east-1")
            .digest("sha256:digest")
            .build();

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(
            OptionalSweepingOutput.builder().found(true).output(DliteVmStageInfraDetails.builder().build()).build());
    StepResponse stepResponse = ecrStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(1);
    stepResponse.getStepOutcomes().forEach(stepOutcome -> {
      if (stepOutcome.getOutcome() instanceof CIStepArtifactOutcome) {
        CIStepArtifactOutcome outcome = (CIStepArtifactOutcome) stepOutcome.getOutcome();
        assertThat(outcome).isNotNull();
        assertThat(outcome.getStepArtifacts().getPublishedImageArtifacts().size()).isEqualTo(1);
        assertThat(outcome.getStepArtifacts().getPublishedImageArtifacts().get(0)).isEqualTo(expectedArtifact);
        assertThat(stepOutcome.getName()).isEqualTo("artifact_identifierId");
      }
    });
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void shouldHandleArtifactWithProvenance() throws IOException {
    when(featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, "accountId")).thenReturn(true);
    Map<ModuleType, ModuleLicenseDTO> testLicenses = new HashMap<>();
    ModuleLicenseDTO sscaModuleLicneseDTO = SSCAModuleLicenseDTO.builder()
                                                .moduleType(ModuleType.SSCA)
                                                .status(LicenseStatus.ACTIVE)
                                                .startTime(1594684800000L) // 14 July 2020 00:00:00
                                                .build();
    testLicenses.put(ModuleType.SSCA, sscaModuleLicneseDTO);
    when(call.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(AccountLicenseDTO.builder().moduleLicenses(testLicenses).build())));

    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("stepId")
            .spec(ECRStepInfo.builder()
                      .account(ParameterField.createValueField("854707204582"))
                      .region(ParameterField.createValueField("us-east-1"))
                      .imageName(ParameterField.createValueField("harness/ci-unittest"))
                      .tags(ParameterField.createValueField(Arrays.asList("1.0", "latest")))
                      .build())
            .build();
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder()
            .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
            .spec(DockerArtifactMetadata.builder()
                      .registryType("ECR")
                      .registryUrl("https://854707204582.dkr.ecr.us-east-1.amazonaws.com")
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

    when(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ECR, "accountId"))
        .thenReturn(StepImageConfig.builder().image("plugins/kaniko-ecr:0.0.0").build());

    ProvenanceBuilderData provenanceBuilder =
        ProvenanceBuilderData.builder()
            .accountId("accountId")
            .stepExecutionId("runtimeId")
            .pipelineIdentifier("pipelineId")
            .pipelineExecutionId("")
            .startTime(ambiance.getStartTs())
            .pluginInfo("plugins/kaniko-ecr:0.0.0")
            .buildMetadata(BuildMetadata.builder().image("ci-unittest").dockerFile("./dockerfile").build())
            .build();

    Map<String, String> versionMap = new HashMap<>();
    versionMap.put("plugins/kaniko-ecr", "0.0.0");
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
        .thenReturn(List.of(CIStepInfoType.DOCKER, CIStepInfoType.ECR));

    StepArtifacts stepArtifacts = ecrStep.handleArtifact(artifactMetadata, stepElementParameters, ambiance);
    assertThat(stepArtifacts).isNotNull();
    assertThat(stepArtifacts.getPublishedImageArtifacts())
        .contains(PublishedImageArtifact.builder()
                      .imageName("harness/ci-automation")
                      .tag("1.0")
                      .url("https://console.aws.amazon.com/ecr/repositories/private/854707204582/harness/ci-automation/"
                          + "_/image/sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70/details/"
                          + "?region=us-east-1")
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
            .spec(ECRStepInfo.builder()
                      .account(ParameterField.createValueField("854707204582"))
                      .region(ParameterField.createValueField("us-east-1"))
                      .imageName(ParameterField.createValueField("harness/ci-unittest"))
                      .tags(ParameterField.createValueField(Arrays.asList("1.0", "latest")))
                      .build())
            .build();
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder()
            .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
            .spec(DockerArtifactMetadata.builder()
                      .registryType("ECR")
                      .registryUrl("https://854707204582.dkr.ecr.us-east-1.amazonaws.com")
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
    ecrStep.handleArtifact(artifactMetadata, stepElementParameters, ambiance);
    verify(artifactDetailsService, times(2)).saveDockerArtifactDetails(captor.capture(), any());
    assertThat(captor.getAllValues().get(0).getImageName()).isEqualTo("harness/ci-automation");
    assertThat(captor.getAllValues().get(0).getTag()).isEqualTo("1.0");
    assertThat(captor.getAllValues().get(1).getImageName()).isEqualTo("harness/ci-automation");
    assertThat(captor.getAllValues().get(1).getTag()).isEqualTo("latest");
  }
}
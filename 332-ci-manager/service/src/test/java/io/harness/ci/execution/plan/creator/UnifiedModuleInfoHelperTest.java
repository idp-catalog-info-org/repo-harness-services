/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator;

import static io.harness.beans.steps.CIStepInfoType.UNIFIED_CD_INFRA_STEP;
import static io.harness.beans.steps.CIStepInfoType.UNIFIED_SERVICE_STEP;
import static io.harness.beans.steps.outcome.CIOutcomeNames.INTEGRATION_STAGE_OUTCOME;
import static io.harness.cd.beans.outcomes.CdOutcomeConstants.INFRA_STEP_OUTCOME;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.unified.service.NGOutcomes.NG_OUTCOMES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.beans.execution.PublishedFileArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.steps.outcome.IntegrationStageOutcome;
import io.harness.category.element.UnitTests;
import io.harness.cd.beans.moduleinfo.UnifiedPipelineExecutionModuleInfo;
import io.harness.cd.beans.moduleinfo.UnifiedStageModuleInfo;
import io.harness.cd.beans.outcomes.EnvGroupOutcome;
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.cd.beans.outcomes.InfraStepOutcome;
import io.harness.cdng.artifact.DockerArtifactSummary;
import io.harness.cdng.artifact.GarArtifactSummary;
import io.harness.cdng.artifact.HarDockerArtifactSummary;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.GarArtifactOutcome;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.service.beans.ServiceOutcome;
import io.harness.delegate.task.artifacts.source.ArtifactSourceConstants;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.ssca.execution.orchestration.outcome.PublishedSbomArtifact;
import io.harness.unified.service.NGOutcomes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class UnifiedModuleInfoHelperTest {
  private static final String ACCOUNT_ID = "test-account";
  private static final String ORG_ID = "test-org";
  private static final String PROJECT_ID = "test-project";
  private static final String PIPELINE_ID = "test-pipeline";
  private static final String PLAN_EXECUTION_ID = "test-plan-execution";
  private static final String STAGE_EXECUTION_ID = "test-stage-execution";

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private OutcomeService outcomeService;

  @InjectMocks private UnifiedModuleInfoHelper unifiedModuleInfoHelper;

  private Ambiance ambiance;
  private StepType serviceStepType;
  private StepType infraStepType;
  private StepType otherStepType;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);

    serviceStepType =
        StepType.newBuilder().setType(UNIFIED_SERVICE_STEP.getDisplayName()).setStepCategory(StepCategory.STEP).build();

    infraStepType = StepType.newBuilder()
                        .setType(UNIFIED_CD_INFRA_STEP.getDisplayName())
                        .setStepCategory(StepCategory.STEP)
                        .build();

    otherStepType = StepType.newBuilder().setType("other_step").setStepCategory(StepCategory.STEP).build();

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", ACCOUNT_ID);
    setupAbstractions.put("orgIdentifier", ORG_ID);
    setupAbstractions.put("projectIdentifier", PROJECT_ID);

    Level stageLevel = Level.newBuilder()
                           .setRuntimeId(STAGE_EXECUTION_ID)
                           .setSetupId("setup-id")
                           .setIdentifier("stage_1")
                           .setStepType(serviceStepType)
                           .build();

    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .setStageExecutionId(STAGE_EXECUTION_ID)
                   .addLevels(stageLevel)
                   .putAllSetupAbstractions(setupAbstractions)
                   .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build())
                   .build();
  }

  // ==================== isUnifiedServiceStepType Test ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testIsUnifiedServiceStepType() {
    assertThat(unifiedModuleInfoHelper.isUnifiedServiceStepType(serviceStepType)).isTrue();
    assertThat(unifiedModuleInfoHelper.isUnifiedServiceStepType(infraStepType)).isFalse();
    assertThat(unifiedModuleInfoHelper.isUnifiedServiceStepType(null)).isFalse();
    assertThat(unifiedModuleInfoHelper.isUnifiedServiceStepType(otherStepType)).isFalse();
  }

  // ==================== isUnifiedInfraStepType Test ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testIsUnifiedInfraStepType() {
    assertThat(unifiedModuleInfoHelper.isUnifiedInfraStepType(infraStepType)).isTrue();
    assertThat(unifiedModuleInfoHelper.isUnifiedInfraStepType(serviceStepType)).isFalse();
    assertThat(unifiedModuleInfoHelper.isUnifiedInfraStepType(null)).isFalse();
  }

  // ==================== isUnifiedServiceNodeAndCompleted Test ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testIsUnifiedServiceNodeAndCompleted() {
    assertThat(unifiedModuleInfoHelper.isUnifiedServiceNodeAndCompleted(serviceStepType, Status.SUCCEEDED)).isTrue();
    assertThat(unifiedModuleInfoHelper.isUnifiedServiceNodeAndCompleted(serviceStepType, Status.FAILED)).isTrue();
    assertThat(unifiedModuleInfoHelper.isUnifiedServiceNodeAndCompleted(serviceStepType, Status.RUNNING)).isFalse();
    assertThat(unifiedModuleInfoHelper.isUnifiedServiceNodeAndCompleted(otherStepType, Status.SUCCEEDED)).isFalse();
  }

  // ==================== isUnifiedInfraNodeAndCompleted Test ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testIsUnifiedInfraNodeAndCompleted() {
    assertThat(unifiedModuleInfoHelper.isUnifiedInfraNodeAndCompleted(infraStepType, Status.SUCCEEDED)).isTrue();
    assertThat(unifiedModuleInfoHelper.isUnifiedInfraNodeAndCompleted(infraStepType, Status.ABORTED)).isTrue();
    assertThat(unifiedModuleInfoHelper.isUnifiedInfraNodeAndCompleted(infraStepType, Status.QUEUED)).isFalse();
  }

  // ==================== getServiceOutcome Tests ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetServiceOutcome_WithValidServiceOutcome_ReturnsServiceOutcome() throws Exception {
    ServiceOutcome serviceOutcome = ServiceOutcome.builder()
                                        .identifier("service-id")
                                        .name("Service Name")
                                        .type("Kubernetes")
                                        .description("Test service")
                                        .build();
    String serviceYaml = YamlUtils.writeYamlString(serviceOutcome);

    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    ngOutcomes.put(NGOutcomes.SERVICE.getName(), serviceYaml);

    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(true).output(ngOutcomes).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    Optional<ServiceOutcome> result = unifiedModuleInfoHelper.getServiceOutcome(ambiance);

    assertThat(result).isPresent();
    assertThat(result.get().getIdentifier()).isEqualTo("service-id");
    assertThat(result.get().getName()).isEqualTo("Service Name");
    assertThat(result.get().getType()).isEqualTo("Kubernetes");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetServiceOutcome_WithNoNgOutcomes_ReturnsEmpty() {
    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(false).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    Optional<ServiceOutcome> result = unifiedModuleInfoHelper.getServiceOutcome(ambiance);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetServiceOutcome_WithNullServiceKey_ReturnsEmpty() {
    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    // No SERVICE key added

    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(true).output(ngOutcomes).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    Optional<ServiceOutcome> result = unifiedModuleInfoHelper.getServiceOutcome(ambiance);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetServiceOutcome_WithEmptyServiceYaml_ReturnsEmpty() {
    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    ngOutcomes.put(NGOutcomes.SERVICE.getName(), "");

    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(true).output(ngOutcomes).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    Optional<ServiceOutcome> result = unifiedModuleInfoHelper.getServiceOutcome(ambiance);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetServiceOutcome_WithException_ReturnsEmpty() {
    doThrow(new RuntimeException("Test exception"))
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    Optional<ServiceOutcome> result = unifiedModuleInfoHelper.getServiceOutcome(ambiance);

    assertThat(result).isEmpty();
  }

  // ==================== getInfraStepOutcome Tests ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetInfraStepOutcome_WithValidInfraOutcome_ReturnsInfraOutcome() {
    EnvironmentOutcome environmentOutcome =
        EnvironmentOutcome.builder()
            .identifier("env-id")
            .name("Production")
            .type(EnvironmentType.Production)
            .group(EnvGroupOutcome.builder().ref("env-group-ref").name("Env Group").build())
            .build();

    InfraStepOutcome infraStepOutcome = InfraStepOutcome.builder()
                                            .identifier("infra-id")
                                            .name("Infrastructure Name")
                                            .kind("KubernetesDirect")
                                            .infrastructureKey("infra-key")
                                            .environment(environmentOutcome)
                                            .build();
    infraStepOutcome.populateMap();

    OptionalSweepingOutput sweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(infraStepOutcome).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME)));

    Optional<InfraStepOutcome> result = unifiedModuleInfoHelper.getInfraStepOutcome(ambiance);

    assertThat(result).isPresent();
    assertThat(result.get().getEnvironment()).isNotNull();
    assertThat(result.get().getEnvironment().getIdentifier()).isEqualTo("env-id");
    assertThat(result.get().getIdentifier()).isEqualTo("infra-id");
    assertThat(result.get().getName()).isEqualTo("Infrastructure Name");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetInfraStepOutcome_WithNoInfraStepOutcome_ReturnsEmpty() {
    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(false).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME)));

    Optional<InfraStepOutcome> result = unifiedModuleInfoHelper.getInfraStepOutcome(ambiance);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetInfraStepOutcome_WithException_ReturnsEmpty() {
    doThrow(new RuntimeException("Test exception"))
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME)));

    Optional<InfraStepOutcome> result = unifiedModuleInfoHelper.getInfraStepOutcome(ambiance);

    assertThat(result).isEmpty();
  }

  // ==================== getArtifactsOutcome Tests ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetArtifactsOutcome_WithValidArtifacts_ReturnsArtifactsOutcome() throws Exception {
    DockerArtifactOutcome primaryArtifact =
        DockerArtifactOutcome.builder().imagePath("docker.io/harness/test").tag("v1.0").build();

    ArtifactsOutcome artifactsOutcome = ArtifactsOutcome.builder().primary(primaryArtifact).build();
    String artifactsYaml = YamlUtils.writeYamlString(artifactsOutcome);

    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    ngOutcomes.put(NGOutcomes.ARTIFACTS.getName(), artifactsYaml);

    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(true).output(ngOutcomes).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    Optional<ArtifactsOutcome> result = unifiedModuleInfoHelper.getArtifactsOutcome(ambiance);

    assertThat(result).isPresent();
    assertThat(result.get().getPrimary()).isNotNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetArtifactsOutcome_WithNoNgOutcomes_ReturnsEmpty() {
    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(false).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    Optional<ArtifactsOutcome> result = unifiedModuleInfoHelper.getArtifactsOutcome(ambiance);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetArtifactsOutcome_WithNoArtifactsKey_ReturnsEmpty() {
    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    // No ARTIFACTS key

    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(true).output(ngOutcomes).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    Optional<ArtifactsOutcome> result = unifiedModuleInfoHelper.getArtifactsOutcome(ambiance);

    assertThat(result).isEmpty();
  }

  // ==================== ArtifactSummary Tests (Docker, Har, Gar) ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testDockerArtifactSummary() {
    DockerArtifactSummary dockerSummary = DockerArtifactSummary.builder()
                                              .imagePath("docker.io/harness/delegate")
                                              .tag("latest")
                                              .digest("sha256:abc123")
                                              .build();

    // Verify type
    assertThat(dockerSummary.getType()).isEqualTo(ArtifactSourceConstants.DOCKER_REGISTRY_NAME);
    assertThat(dockerSummary.getType()).isEqualTo("DockerRegistry");

    // Verify display name format: imagePath:tag
    assertThat(dockerSummary.getDisplayName()).isEqualTo("docker.io/harness/delegate:latest");

    // Verify digest
    assertThat(dockerSummary.getDigest()).isEqualTo("sha256:abc123");

    // Verify individual fields
    assertThat(dockerSummary.getImagePath()).isEqualTo("docker.io/harness/delegate");
    assertThat(dockerSummary.getTag()).isEqualTo("latest");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testHarDockerArtifactSummary() {
    // Test with image path (Docker-style)
    HarDockerArtifactSummary harDockerSummary = HarDockerArtifactSummary.builder()
                                                    .registryRef("my-registry")
                                                    .imagePath("harness/my-image")
                                                    .tag("v1.0.0")
                                                    .digest("sha256:def456")
                                                    .build();

    // Verify type
    assertThat(harDockerSummary.getType()).isEqualTo(ArtifactSourceConstants.HARNESS_ARTIFACT_REGISTRY_NAME);
    assertThat(harDockerSummary.getType()).isEqualTo("Har");

    // Verify display name format: registryRef/imagePath:tag
    assertThat(harDockerSummary.getDisplayName()).isEqualTo("my-registry/harness/my-image:v1.0.0");

    // Verify artifactId format: imagePath:tag
    assertThat(harDockerSummary.getArtifactId()).isEqualTo("harness/my-image:v1.0.0");

    // Verify digest
    assertThat(harDockerSummary.getDigest()).isEqualTo("sha256:def456");

    // Test with artifact/version (generic artifact style)
    HarDockerArtifactSummary harGenericSummary =
        HarDockerArtifactSummary.builder().registryRef("my-registry").artifact("my-artifact").version("2.0.0").build();

    // Verify display name format: registryRef/artifact/version
    assertThat(harGenericSummary.getDisplayName()).isEqualTo("my-registry/my-artifact/2.0.0");

    // Verify artifactId format: artifact/version
    assertThat(harGenericSummary.getArtifactId()).isEqualTo("my-artifact/2.0.0");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGarArtifactSummary() {
    GarArtifactSummary garSummary = GarArtifactSummary.builder()
                                        .pkg("my-package")
                                        .version("1.2.3")
                                        .region("us-central1")
                                        .repositoryName("my-repo")
                                        .project("my-gcp-project")
                                        .digest("sha256:ghi789")
                                        .build();

    // Verify type
    assertThat(garSummary.getType()).isEqualTo(ArtifactSourceConstants.GOOGLE_ARTIFACT_REGISTRY_NAME);
    assertThat(garSummary.getType()).isEqualTo("GoogleArtifactRegistry");

    // Verify display name format: package:version
    assertThat(garSummary.getDisplayName()).isEqualTo("my-package:1.2.3");

    // Verify digest
    assertThat(garSummary.getDigest()).isEqualTo("sha256:ghi789");

    // Verify individual fields
    assertThat(garSummary.getPkg()).isEqualTo("my-package");
    assertThat(garSummary.getVersion()).isEqualTo("1.2.3");
    assertThat(garSummary.getRegion()).isEqualTo("us-central1");
    assertThat(garSummary.getRepositoryName()).isEqualTo("my-repo");
    assertThat(garSummary.getProject()).isEqualTo("my-gcp-project");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetArtifactsOutcome_WithGarArtifact_ReturnsArtifactsOutcome() throws Exception {
    GarArtifactOutcome primaryArtifact = GarArtifactOutcome.builder()
                                             .repositoryName("my-repo")
                                             .pkg("my-package")
                                             .version("v1.0.0")
                                             .region("us-central1")
                                             .project("my-gcp-project")
                                             .build();

    ArtifactsOutcome artifactsOutcome = ArtifactsOutcome.builder().primary(primaryArtifact).build();
    String artifactsYaml = YamlUtils.writeYamlString(artifactsOutcome);

    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    ngOutcomes.put(NGOutcomes.ARTIFACTS.getName(), artifactsYaml);

    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(true).output(ngOutcomes).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    Optional<ArtifactsOutcome> result = unifiedModuleInfoHelper.getArtifactsOutcome(ambiance);

    assertThat(result).isPresent();
    assertThat(result.get().getPrimary()).isNotNull();
    assertThat(result.get().getPrimary()).isInstanceOf(GarArtifactOutcome.class);
  }

  // ==================== getManifestsOutcome Tests ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetManifestsOutcome_WithNoNgOutcomes_ReturnsEmpty() {
    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(false).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    Optional<ManifestsOutcome> result = unifiedModuleInfoHelper.getManifestsOutcome(ambiance);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetManifestsOutcome_WithNoManifestsKey_ReturnsEmpty() {
    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    // No MANIFESTS key

    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(true).output(ngOutcomes).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    Optional<ManifestsOutcome> result = unifiedModuleInfoHelper.getManifestsOutcome(ambiance);

    assertThat(result).isEmpty();
  }

  // ==================== buildUnifiedPipelineExecutionModuleInfoFromServiceStep Tests ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildUnifiedPipelineExecutionModuleInfoFromServiceStep_WithValidServiceOutcome_PopulatesServiceInfo()
      throws Exception {
    ServiceOutcome serviceOutcome =
        ServiceOutcome.builder().identifier("service-id").name("Service Name").type("Kubernetes").build();
    String serviceYaml = YamlUtils.writeYamlString(serviceOutcome);

    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    ngOutcomes.put(NGOutcomes.SERVICE.getName(), serviceYaml);

    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(true).output(ngOutcomes).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromServiceStep(event);

    assertThat(result).isNotNull();
    assertThat(result.getPipelineCDInfo()).isNotNull();
    assertThat(result.getPipelineCDInfo().getServiceIdentifiers()).contains("service-id");
    assertThat(result.getPipelineCDInfo().getServiceTypes()).contains("Kubernetes");
    assertThat(result.getStageInfoMap()).isNotEmpty();
    assertThat(result.getStageInfoMap()).containsKey(STAGE_EXECUTION_ID);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildUnifiedPipelineExecutionModuleInfoFromServiceStep_WithNoServiceOutcome_ReturnsEmptyBuilder() {
    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(false).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromServiceStep(event);

    assertThat(result).isNotNull();
  }

  // ==================== buildUnifiedPipelineExecutionModuleInfoFromInfraStep Tests ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildUnifiedPipelineExecutionModuleInfoFromInfraStep_WithValidInfraOutcome_PopulatesInfraInfo() {
    EnvironmentOutcome environmentOutcome =
        EnvironmentOutcome.builder()
            .identifier("env-id")
            .name("Production")
            .type(EnvironmentType.Production)
            .group(EnvGroupOutcome.builder().ref("env-group-ref").name("Env Group").build())
            .build();

    InfraStepOutcome infraStepOutcome = InfraStepOutcome.builder()
                                            .identifier("infra-id")
                                            .name("Infrastructure Name")
                                            .kind("KubernetesDirect")
                                            .infrastructureKey("infra-key")
                                            .environment(environmentOutcome)
                                            .build();
    infraStepOutcome.populateMap();

    OptionalSweepingOutput sweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(infraStepOutcome).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME)));

    // Mock NG_OUTCOMES for artifact display names (even if empty)
    OptionalSweepingOutput ngSweepingOutput = OptionalSweepingOutput.builder().found(false).build();
    doReturn(ngSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromInfraStep(event);

    assertThat(result).isNotNull();
    assertThat(result.getPipelineCDInfo()).isNotNull();
    assertThat(result.getPipelineCDInfo().getEnvIdentifiers()).contains("env-id");
    assertThat(result.getPipelineCDInfo().getEnvironmentTypes()).contains(EnvironmentType.Production);
    assertThat(result.getPipelineCDInfo().getInfrastructureIdentifiers()).contains("infra-id");
    assertThat(result.getPipelineCDInfo().getInfrastructureNames()).contains("Infrastructure Name");
    assertThat(result.getStageInfoMap()).isNotEmpty();
    assertThat(result.getStageInfoMap()).containsKey(STAGE_EXECUTION_ID);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildUnifiedPipelineExecutionModuleInfoFromInfraStep_WithNoInfraOutcome_ReturnsEmptyBuilder() {
    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(false).build();

    doReturn(sweepingOutput).when(executionSweepingOutputService).resolveOptional(any(Ambiance.class), any());

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromInfraStep(event);

    assertThat(result).isNotNull();
  }

  // ==================== buildUnifiedStageModuleInfoFromServiceStep Tests ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildUnifiedStageModuleInfoFromServiceStep_WithValidServiceOutcome_ReturnsStageModuleInfo()
      throws Exception {
    ServiceOutcome serviceOutcome =
        ServiceOutcome.builder().identifier("service-id").name("Service Name").type("Kubernetes").build();
    String serviceYaml = YamlUtils.writeYamlString(serviceOutcome);

    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    ngOutcomes.put(NGOutcomes.SERVICE.getName(), serviceYaml);

    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(true).output(ngOutcomes).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    UnifiedStageModuleInfo result = unifiedModuleInfoHelper.buildUnifiedStageModuleInfoFromServiceStep(ambiance);

    assertThat(result).isNotNull();
    assertThat(result.getServiceInfo()).isNotNull();
    assertThat(result.getServiceInfo().getIdentifier()).isEqualTo("service-id");
    assertThat(result.getServiceInfo().getDisplayName()).isEqualTo("Service Name");
    assertThat(result.getServiceInfo().getDeploymentType()).isEqualTo("Kubernetes");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildUnifiedStageModuleInfoFromServiceStep_WithNoServiceOutcome_ReturnsEmptyBuilder() {
    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(false).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    UnifiedStageModuleInfo result = unifiedModuleInfoHelper.buildUnifiedStageModuleInfoFromServiceStep(ambiance);

    assertThat(result).isNotNull();
    assertThat(result.getServiceInfo()).isNull();
  }

  // ==================== buildUnifiedStageModuleInfoFromInfraStep Tests ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildUnifiedStageModuleInfoFromInfraStep_WithValidInfraOutcome_ReturnsStageModuleInfo() {
    EnvironmentOutcome environmentOutcome =
        EnvironmentOutcome.builder()
            .identifier("env-id")
            .name("Production Environment")
            .type(EnvironmentType.Production)
            .group(EnvGroupOutcome.builder().ref("env-group-id").name("Env Group Name").build())
            .build();

    InfraStepOutcome infraStepOutcome = InfraStepOutcome.builder()
                                            .identifier("infra-id")
                                            .name("Infrastructure Name")
                                            .kind("KubernetesDirect")
                                            .infrastructureKey("infra-key")
                                            .environment(environmentOutcome)
                                            .build();
    infraStepOutcome.populateMap();

    OptionalSweepingOutput sweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(infraStepOutcome).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME)));

    UnifiedStageModuleInfo result = unifiedModuleInfoHelper.buildUnifiedStageModuleInfoFromInfraStep(ambiance);

    assertThat(result).isNotNull();
    assertThat(result.getInfraExecutionSummary()).isNotNull();
    assertThat(result.getInfraExecutionSummary().getIdentifier()).isEqualTo("env-id");
    assertThat(result.getInfraExecutionSummary().getName()).isEqualTo("Production Environment");
    assertThat(result.getInfraExecutionSummary().getType()).isEqualTo("Production");
    assertThat(result.getInfraExecutionSummary().getEnvGroupId()).isEqualTo("env-group-id");
    assertThat(result.getInfraExecutionSummary().getEnvGroupName()).isEqualTo("Env Group Name");
    assertThat(result.getInfraExecutionSummary().getInfrastructureIdentifier()).isEqualTo("infra-id");
    assertThat(result.getInfraExecutionSummary().getInfrastructureName()).isEqualTo("Infrastructure Name");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildUnifiedStageModuleInfoFromInfraStep_WithNoInfraOutcome_ReturnsEmptyBuilder() {
    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(false).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME)));

    UnifiedStageModuleInfo result = unifiedModuleInfoHelper.buildUnifiedStageModuleInfoFromInfraStep(ambiance);

    assertThat(result).isNotNull();
    assertThat(result.getInfraExecutionSummary()).isNull();
  }

  // ==================== Edge Cases / Exception Handling Tests ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildUnifiedPipelineExecutionModuleInfoFromServiceStep_WithException_HandlesGracefully() {
    doThrow(new RuntimeException("Test exception"))
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), any());

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromServiceStep(event);

    // Should not throw exception and return a valid builder result
    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildUnifiedPipelineExecutionModuleInfoFromInfraStep_WithException_HandlesGracefully() {
    doThrow(new RuntimeException("Test exception"))
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), any());

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromInfraStep(event);

    // Should not throw exception and return a valid builder result
    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildUnifiedStageModuleInfoFromServiceStep_WithException_HandlesGracefully() {
    doThrow(new RuntimeException("Test exception"))
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), any());

    UnifiedStageModuleInfo result = unifiedModuleInfoHelper.buildUnifiedStageModuleInfoFromServiceStep(ambiance);

    // Should not throw exception and return a valid builder result
    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildUnifiedStageModuleInfoFromInfraStep_WithException_HandlesGracefully() {
    doThrow(new RuntimeException("Test exception"))
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), any());

    UnifiedStageModuleInfo result = unifiedModuleInfoHelper.buildUnifiedStageModuleInfoFromInfraStep(ambiance);

    // Should not throw exception and return a valid builder result
    assertThat(result).isNotNull();
  }

  // ==================== Empty Stage Execution ID Tests ====================

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void
  testBuildUnifiedPipelineExecutionModuleInfoFromServiceStep_WithEmptyStageExecutionId_DoesNotAddToStageInfo()
      throws Exception {
    // Create ambiance with empty stage execution id
    Ambiance ambianceNoStage =
        Ambiance.newBuilder()
            .setPlanExecutionId(PLAN_EXECUTION_ID)
            .setStageExecutionId("")
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build())
            .build();

    ServiceOutcome serviceOutcome =
        ServiceOutcome.builder().identifier("service-id").name("Service Name").type("Kubernetes").build();
    String serviceYaml = YamlUtils.writeYamlString(serviceOutcome);

    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    ngOutcomes.put(NGOutcomes.SERVICE.getName(), serviceYaml);

    OptionalSweepingOutput sweepingOutput = OptionalSweepingOutput.builder().found(true).output(ngOutcomes).build();

    doReturn(sweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES)));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambianceNoStage).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromServiceStep(event);

    assertThat(result).isNotNull();
    // Stage info should not be added when stage execution id is empty
    assertThat(result.getStageInfoMap()).isEmpty();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testBuildUnifiedPipelineExecutionModuleInfoFromIntegrationStage_WithAllArtifacts_ReturnsModuleInfo() {
    PublishedImageArtifact imageArtifact = PublishedImageArtifact.builder()
                                               .imageName("myimage")
                                               .tag("latest")
                                               .url("docker.io/myimage:latest")
                                               .digest("sha256:abc123")
                                               .build();

    PublishedFileArtifact fileArtifact =
        PublishedFileArtifact.builder().name("artifact.jar").url("https://storage.example.com/artifact.jar").build();

    PublishedSbomArtifact sbomArtifact = PublishedSbomArtifact.builder()
                                             .id("sbom-1")
                                             .imageName("myimage")
                                             .tag("latest")
                                             .sbomName("myimage-sbom.json")
                                             .sbomUrl("https://storage.example.com/sbom.json")
                                             .build();

    IntegrationStageOutcome integrationStageOutcome = IntegrationStageOutcome.builder()
                                                          .imageArtifact(imageArtifact)
                                                          .fileArtifact(fileArtifact)
                                                          .sbomArtifact(sbomArtifact)
                                                          .build();

    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(integrationStageOutcome).build();

    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INTEGRATION_STAGE_OUTCOME)));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(event, outcomeService);

    assertThat(result).isNotNull();
    assertThat(result.getPipelineCIInfo()).isNotNull();
    assertThat(result.getPipelineCIInfo().getImageArtifacts()).hasSize(1);
    assertThat(result.getPipelineCIInfo().getFileArtifacts()).hasSize(1);
    assertThat(result.getPipelineCIInfo().getSbomArtifacts()).hasSize(1);
    assertThat(result.getStageInfoMap()).containsKey(STAGE_EXECUTION_ID);

    UnifiedStageModuleInfo stageModuleInfo = result.getStageInfoMap().get(STAGE_EXECUTION_ID);
    assertThat(stageModuleInfo).isNotNull();
    assertThat(stageModuleInfo.getCiImageArtifacts()).hasSize(1);
    assertThat(stageModuleInfo.getCiFileArtifacts()).hasSize(1);
    assertThat(stageModuleInfo.getCiSbomArtifacts()).hasSize(1);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void
  testBuildUnifiedPipelineExecutionModuleInfoFromIntegrationStage_WithOnlyImageArtifacts_ReturnsModuleInfo() {
    PublishedImageArtifact imageArtifact1 = PublishedImageArtifact.builder()
                                                .imageName("image1")
                                                .tag("v1.0")
                                                .url("docker.io/image1:v1.0")
                                                .digest("sha256:def456")
                                                .build();

    PublishedImageArtifact imageArtifact2 = PublishedImageArtifact.builder()
                                                .imageName("image2")
                                                .tag("v2.0")
                                                .url("gcr.io/project/image2:v2.0")
                                                .digest("sha256:ghi789")
                                                .build();

    IntegrationStageOutcome integrationStageOutcome =
        IntegrationStageOutcome.builder().imageArtifact(imageArtifact1).imageArtifact(imageArtifact2).build();

    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(integrationStageOutcome).build();

    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INTEGRATION_STAGE_OUTCOME)));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(event, outcomeService);

    assertThat(result).isNotNull();
    assertThat(result.getPipelineCIInfo()).isNotNull();
    assertThat(result.getPipelineCIInfo().getImageArtifacts()).hasSize(2);
    assertThat(result.getPipelineCIInfo().getFileArtifacts()).isNullOrEmpty();
    assertThat(result.getPipelineCIInfo().getSbomArtifacts()).isNullOrEmpty();
    assertThat(result.getStageInfoMap()).containsKey(STAGE_EXECUTION_ID);
    assertThat(result.getStageInfoMap().get(STAGE_EXECUTION_ID).getCiImageArtifacts()).hasSize(2);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testBuildUnifiedPipelineExecutionModuleInfoFromIntegrationStage_WithNoOutcome_ReturnsNull() {
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(false).build();

    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INTEGRATION_STAGE_OUTCOME)));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(event, outcomeService);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testBuildUnifiedPipelineExecutionModuleInfoFromIntegrationStage_WithNullOutcome_ReturnsNull() {
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(null).build();

    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INTEGRATION_STAGE_OUTCOME)));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(event, outcomeService);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testBuildUnifiedPipelineExecutionModuleInfoFromIntegrationStage_WithEmptyStageExecutionId_ReturnsNull() {
    Ambiance ambianceNoStage =
        Ambiance.newBuilder()
            .setPlanExecutionId(PLAN_EXECUTION_ID)
            .setStageExecutionId("")
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build())
            .build();

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambianceNoStage).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(event, outcomeService);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testBuildUnifiedPipelineExecutionModuleInfoFromIntegrationStage_WithException_ReturnsNull() {
    doThrow(new RuntimeException("Test exception")).when(outcomeService).resolveOptional(any(Ambiance.class), any());

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(event, outcomeService);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testBuildUnifiedPipelineExecutionModuleInfoFromIntegrationStage_WithEmptyArtifacts_ReturnsModuleInfo() {
    IntegrationStageOutcome integrationStageOutcome = IntegrationStageOutcome.builder().build();

    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(integrationStageOutcome).build();

    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INTEGRATION_STAGE_OUTCOME)));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(event, outcomeService);

    assertThat(result).isNotNull();
    assertThat(result.getPipelineCIInfo()).isNotNull();
    assertThat(result.getPipelineCIInfo().getImageArtifacts()).isNullOrEmpty();
    assertThat(result.getPipelineCIInfo().getFileArtifacts()).isNullOrEmpty();
    assertThat(result.getPipelineCIInfo().getSbomArtifacts()).isNullOrEmpty();
    assertThat(result.getStageInfoMap()).containsKey(STAGE_EXECUTION_ID);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void
  testBuildUnifiedPipelineExecutionModuleInfoFromIntegrationStage_WithOnlySbomArtifacts_ReturnsModuleInfo() {
    PublishedSbomArtifact sbomArtifact1 = PublishedSbomArtifact.builder()
                                              .id("sbom-1")
                                              .imageName("myimage1")
                                              .tag("v1")
                                              .sbomName("sbom1.json")
                                              .sbomUrl("https://storage.example.com/sbom1.json")
                                              .build();

    PublishedSbomArtifact sbomArtifact2 = PublishedSbomArtifact.builder()
                                              .id("sbom-2")
                                              .imageName("myimage2")
                                              .tag("v2")
                                              .sbomName("sbom2.json")
                                              .sbomUrl("https://storage.example.com/sbom2.json")
                                              .build();

    IntegrationStageOutcome integrationStageOutcome =
        IntegrationStageOutcome.builder().sbomArtifact(sbomArtifact1).sbomArtifact(sbomArtifact2).build();

    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).outcome(integrationStageOutcome).build();

    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(INTEGRATION_STAGE_OUTCOME)));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    UnifiedPipelineExecutionModuleInfo result =
        unifiedModuleInfoHelper.buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(event, outcomeService);

    assertThat(result).isNotNull();
    assertThat(result.getPipelineCIInfo()).isNotNull();
    assertThat(result.getPipelineCIInfo().getImageArtifacts()).isNullOrEmpty();
    assertThat(result.getPipelineCIInfo().getFileArtifacts()).isNullOrEmpty();
    assertThat(result.getPipelineCIInfo().getSbomArtifacts()).hasSize(2);
    assertThat(result.getStageInfoMap().get(STAGE_EXECUTION_ID).getCiSbomArtifacts()).hasSize(2);
  }
}

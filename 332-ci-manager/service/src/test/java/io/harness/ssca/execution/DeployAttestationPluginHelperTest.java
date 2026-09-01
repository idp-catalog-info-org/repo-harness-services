/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.rule.OwnerRule.VEDANT;
import static io.harness.ssca.execution.attestation.DeployAttestationStepPluginUtils.PLUGIN_DEPLOYMENT_INFO;
import static io.harness.ssca.execution.attestation.DeployAttestationStepPluginUtils.PLUGIN_KEYLESS_TYPE;
import static io.harness.ssca.execution.attestation.DeployAttestationStepPluginUtils.PLUGIN_NON_HARNESS_OIDC_TOKEN;
import static io.harness.ssca.execution.attestation.DeployAttestationStepPluginUtils.PLUGIN_TYPE;
import static io.harness.ssca.execution.attestation.DeployAttestationStepPluginUtils.PLUGIN_TYPE_DEPLOY_ATTEST;
import static io.harness.ssca.execution.attestation.DeployAttestationStepPluginUtils.STAGE_EXECUTION_ID;
import static io.harness.ssca.execution.attestation.DeployAttestationStepPluginUtils.STAGE_NAME;
import static io.harness.ssca.execution.attestation.DeployAttestationStepPluginUtils.STAGE_TYPE;
import static io.harness.ssca.execution.attestation.DeployAttestationStepPluginUtils.STEP_EXECUTION_ID;
import static io.harness.ssca.execution.attestation.DeployAttestationStepPluginUtils.STEP_ID;
import static io.harness.ssca.execution.attestation.DeployAttestationStepPluginUtils.STEP_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.instance.outcome.DeploymentInfoOutcome;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.delegate.beans.ecs.EcsContainer;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.NativeHelmServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.PdcServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.ServerInstanceInfo;
import io.harness.delegate.task.helm.flag.HelmChartInfo;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.K8sContainer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.ssca.beans.attestation.KeylessType;
import io.harness.ssca.beans.stepinfo.DeployAttestationStepInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class DeployAttestationPluginHelperTest extends CIExecutionTestBase {
  @InjectMocks private DeployAttestationPluginHelper deployAttestationPluginHelper;

  @Mock private SscaPluginUtils sscaPluginUtils;
  @Mock private OutcomeService outcomeService;

  Ambiance ambiance = Ambiance.newBuilder()
                          .putSetupAbstractions("accountId", "accountId")
                          .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                          .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                          .addLevels(Level.newBuilder()
                                         .setRuntimeId("runtimeID")
                                         .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE)))
                          .build();

  @Before
  public void setup() {
    deployAttestationPluginHelper = new DeployAttestationPluginHelper(sscaPluginUtils, outcomeService);
    when(sscaPluginUtils.getStageName(any())).thenReturn("stageName");
    when(sscaPluginUtils.getStageExecutionIdentifier(any())).thenReturn("stageExecId");
    when(sscaPluginUtils.getStageType(any())).thenReturn("Deployment");
    when(sscaPluginUtils.getPipelineTriggerType(any())).thenReturn("MANUAL");
    when(sscaPluginUtils.getPipelineTriggerBy(any())).thenReturn("user");
  }

  private DeploymentInfoOutcome k8sDeploymentInfoOutcome() {
    K8sContainer container1 = K8sContainer.builder()
                                  .containerId("containerd://abc")
                                  .name("app")
                                  .image("docker.io/org/app:tag")
                                  .imageId("docker.io/org/app@sha256:appdigest")
                                  .indexDigest("sha256:appdigest")
                                  .build();
    K8sContainer container2 = K8sContainer.builder()
                                  .name("sidecar")
                                  .image("docker.io/org/sidecar:tag")
                                  .imageId("docker.io/org/sidecar@sha256:sidecardigest")
                                  .indexDigest("sha256:sidecardigest")
                                  .build();
    K8sServerInstanceInfo serverInstanceInfo = K8sServerInstanceInfo.builder()
                                                   .name("app-pod-1")
                                                   .namespace("ns")
                                                   .releaseName("release-1")
                                                   .podIP("10.0.0.1")
                                                   .canary(true)
                                                   .containerList(List.of(container1, container2))
                                                   .build();
    return DeploymentInfoOutcome.builder()
        .serverInstanceInfoList(List.of((ServerInstanceInfo) serverInstanceInfo))
        .build();
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testGetDeployAttestationStepEnvVariables() {
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(k8sDeploymentInfoOutcome()).build());

    DeployAttestationStepInfo stepInfo =
        DeployAttestationStepInfo.builder()
            .name("deploy-attest-step")
            .deployStepRef(ParameterField.createExpressionField(
                true, "<+pipeline.stages.deploy.spec.execution.steps.K8sRolling>", null, false))
            .oidcProvider(KeylessType.HARNESS)
            .build();

    Map<String, String> envMap =
        deployAttestationPluginHelper.getDeployAttestationStepEnvVariables(stepInfo, "id1", ambiance);

    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap.get(PLUGIN_TYPE)).isEqualTo(PLUGIN_TYPE_DEPLOY_ATTEST);
    assertThat(envMap.get(PLUGIN_DEPLOYMENT_INFO))
        .contains("app-pod-1")
        .contains("docker.io/org/app:tag")
        .contains("sha256:appdigest")
        .contains("docker.io/org/sidecar:tag")
        .contains("sha256:sidecardigest")
        .contains("K8S")
        .contains("10.0.0.1")
        .contains("containerd://abc")
        .contains("\"canary\":true");
    assertThat(envMap.get(PLUGIN_KEYLESS_TYPE)).isEqualTo(KeylessType.HARNESS.toString());
    assertThat(envMap.get(STEP_EXECUTION_ID)).isEqualTo("runtimeID");
    assertThat(envMap.get(STEP_ID)).isEqualTo("id1");
    assertThat(envMap.get(STEP_NAME)).isEqualTo("deploy-attest-step");
    assertThat(envMap.get(STAGE_NAME)).isEqualTo("stageName");
    assertThat(envMap.get(STAGE_EXECUTION_ID)).isEqualTo("stageExecId");
    assertThat(envMap.get(STAGE_TYPE)).isEqualTo("Deployment");
    assertThat(envMap.values()).doesNotContainNull();
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testGetDeployAttestationStepEnvVariables_nonHarnessOidc() {
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(k8sDeploymentInfoOutcome()).build());
    when(sscaPluginUtils.getKeylessSigningOidcToken(any())).thenReturn("oidc-token");

    DeployAttestationStepInfo stepInfo =
        DeployAttestationStepInfo.builder()
            .name("deploy-attest-step")
            .deployStepRef(ParameterField.createExpressionField(
                true, "<+pipeline.stages.deploy.spec.execution.steps.K8sRolling>", null, false))
            .oidcProvider(KeylessType.NON_HARNESS)
            .build();

    Map<String, String> envMap =
        deployAttestationPluginHelper.getDeployAttestationStepEnvVariables(stepInfo, "id2", ambiance);

    assertThat(envMap.get(PLUGIN_KEYLESS_TYPE)).isEqualTo(KeylessType.NON_HARNESS.toString());
    assertThat(envMap.get(PLUGIN_NON_HARNESS_OIDC_TOKEN)).isEqualTo("oidc-token");
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testGetDeployAttestationStepEnvVariables_outcomeNotFound() {
    when(outcomeService.resolveOptional(any(), any())).thenReturn(OptionalOutcome.builder().found(false).build());

    DeployAttestationStepInfo stepInfo =
        DeployAttestationStepInfo.builder()
            .name("deploy-attest-step")
            .deployStepRef(ParameterField.createExpressionField(true, "<+bad.reference>", null, false))
            .oidcProvider(KeylessType.HARNESS)
            .build();

    assertThatThrownBy(
        () -> deployAttestationPluginHelper.getDeployAttestationStepEnvVariables(stepInfo, "id3", ambiance))
        .isInstanceOf(CIStageExecutionException.class);
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testGetDeployAttestationStepEnvVariables_emptyServerInstances() {
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(
            OptionalOutcome.builder()
                .found(true)
                .outcome(DeploymentInfoOutcome.builder().serverInstanceInfoList(Collections.emptyList()).build())
                .build());

    DeployAttestationStepInfo stepInfo =
        DeployAttestationStepInfo.builder()
            .name("deploy-attest-step")
            .deployStepRef(ParameterField.createExpressionField(
                true, "<+pipeline.stages.deploy.spec.execution.steps.K8sRolling>", null, false))
            .oidcProvider(KeylessType.HARNESS)
            .build();

    Map<String, String> envMap =
        deployAttestationPluginHelper.getDeployAttestationStepEnvVariables(stepInfo, "id4", ambiance);

    assertThat(envMap.get(PLUGIN_DEPLOYMENT_INFO)).isEqualTo("[]");
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testGetDeployAttestationStepEnvVariables_ecsContainers() {
    EcsContainer container = EcsContainer.builder()
                                 .name("app")
                                 .image("123.dkr.ecr.us-east-1.amazonaws.com/app:tag")
                                 .indexDigest("sha256:ecsdigest")
                                 .build();
    EcsServerInstanceInfo serverInstanceInfo = EcsServerInstanceInfo.builder()
                                                   .taskArn("task-arn-1")
                                                   .serviceName("service-1")
                                                   .containers(List.of(container))
                                                   .build();
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(DeploymentInfoOutcome.builder()
                                     .serverInstanceInfoList(List.of((ServerInstanceInfo) serverInstanceInfo))
                                     .build())
                        .build());

    DeployAttestationStepInfo stepInfo =
        DeployAttestationStepInfo.builder()
            .name("deploy-attest-step")
            .deployStepRef(ParameterField.createExpressionField(
                true, "<+pipeline.stages.deploy.spec.execution.steps.EcsRollingDeploy>", null, false))
            .oidcProvider(KeylessType.HARNESS)
            .build();

    Map<String, String> envMap =
        deployAttestationPluginHelper.getDeployAttestationStepEnvVariables(stepInfo, "id5", ambiance);

    assertThat(envMap.get(PLUGIN_DEPLOYMENT_INFO))
        .contains("task-arn-1")
        .contains("123.dkr.ecr.us-east-1.amazonaws.com/app:tag")
        .contains("123.dkr.ecr.us-east-1.amazonaws.com/app@sha256:ecsdigest")
        .contains("sha256:ecsdigest")
        .contains("ECS")
        .contains("service-1");
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testGetDeployAttestationStepEnvVariables_unsupportedDeploymentType() {
    PdcServerInstanceInfo serverInstanceInfo =
        PdcServerInstanceInfo.builder().serviceType("pdc").infrastructureKey("key").host("host-1").build();
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(DeploymentInfoOutcome.builder()
                                     .serverInstanceInfoList(List.of((ServerInstanceInfo) serverInstanceInfo))
                                     .build())
                        .build());

    DeployAttestationStepInfo stepInfo =
        DeployAttestationStepInfo.builder()
            .name("deploy-attest-step")
            .deployStepRef(ParameterField.createExpressionField(
                true, "<+pipeline.stages.deploy.spec.execution.steps.PdcDeploy>", null, false))
            .oidcProvider(KeylessType.HARNESS)
            .build();

    assertThatThrownBy(
        () -> deployAttestationPluginHelper.getDeployAttestationStepEnvVariables(stepInfo, "id7", ambiance))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Unsupported deployment type");
  }

  @Test
  @Owner(developers = VEDANT)
  @Category(UnitTests.class)
  public void testGetDeployAttestationStepEnvVariables_helmContainers() {
    K8sContainer container = K8sContainer.builder()
                                 .name("helm-app")
                                 .image("docker.io/org/helmapp:tag")
                                 .imageId("docker.io/org/helmapp@sha256:helmdigest")
                                 .indexDigest("sha256:helmdigest")
                                 .build();
    NativeHelmServerInstanceInfo serverInstanceInfo = NativeHelmServerInstanceInfo.builder()
                                                          .podName("helm-pod-1")
                                                          .ip("10.1.1.1")
                                                          .namespace("helm-ns")
                                                          .releaseName("helm-release-1")
                                                          .helmVersion(HelmVersion.V3)
                                                          .helmChartInfo(HelmChartInfo.builder()
                                                                             .name("my-chart")
                                                                             .version("1.2.3")
                                                                             .repoUrl("https://charts.example.com")
                                                                             .subChartPath("charts/sub")
                                                                             .build())
                                                          .containerList(List.of(container))
                                                          .build();
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(DeploymentInfoOutcome.builder()
                                     .serverInstanceInfoList(List.of((ServerInstanceInfo) serverInstanceInfo))
                                     .build())
                        .build());

    DeployAttestationStepInfo stepInfo =
        DeployAttestationStepInfo.builder()
            .name("deploy-attest-step")
            .deployStepRef(ParameterField.createExpressionField(
                true, "<+pipeline.stages.deploy.spec.execution.steps.HelmDeploy>", null, false))
            .oidcProvider(KeylessType.HARNESS)
            .build();

    Map<String, String> envMap =
        deployAttestationPluginHelper.getDeployAttestationStepEnvVariables(stepInfo, "id6", ambiance);

    assertThat(envMap.get(PLUGIN_DEPLOYMENT_INFO))
        .contains("NATIVE_HELM")
        .contains("helm-pod-1")
        .contains("10.1.1.1")
        .contains("docker.io/org/helmapp@sha256:helmdigest")
        .contains("my-chart")
        .contains("1.2.3")
        .contains("https://charts.example.com")
        .contains("charts/sub")
        .contains("V3");
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.rollback;

import static io.harness.rule.OwnerRule.DANIEL;
import static io.harness.rule.OwnerRule.PRASHANTPAREEK;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.delegate.task.helm.flag.HelmChartInfo;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.dtos.rollback.GitOpsPostProdRollbackInfo;
import io.harness.dtos.rollback.K8sPostProdRollbackInfo;
import io.harness.dtos.rollback.NativeHelmPostProdRollbackInfo;
import io.harness.dtos.rollback.PostProdRollbackSwimLaneInfo;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.Instance;
import io.harness.entities.InstanceType;
import io.harness.entities.instanceinfo.GitopsInstanceInfo;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.entities.instanceinfo.NativeHelmInstanceInfo;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.rule.Owner;
import io.harness.service.deploymentsummary.DeploymentSummaryService;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class PostProdRollbackHelperUtilsTest {
  @InjectMocks @Spy private PostProdRollbackHelperUtils postProdRollbackHelperUtils;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Mock private DeploymentSummaryService deploymentSummaryService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  String instanceKey = "instanceUuid";
  String infraMappingId = "instanceUuid";
  String accountId = "accountId";
  String planExecutionId = "planExecutionId";
  String orgId = "orgId";
  String projectId = "projectId";
  String parentUniqueId = "parentUniqueId";
  String serviceId = "serviceId";
  String envId = "envId";
  String infraId = "infraId";
  String artifactName = "artifactName";
  String artifactId = "artifactId";
  String releaseName = "releaseName";
  String connectorRef = "connectorRef";
  String infraKind = "KubernetesDirect";
  String infraKey = "infraKey";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetK8sSwimlaneInfoNoArtifact() {
    Instance instance = Instance.builder()
                            .lastPipelineExecutionName(planExecutionId)
                            .lastDeployedAt(100)
                            .lastPipelineExecutionId(planExecutionId)
                            .infrastructureMappingId(infraMappingId)
                            .infrastructureKind(infraKind)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .id(instanceKey)
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgId)
                            .projectIdentifier(projectId)
                            .parentUniqueId(parentUniqueId)
                            .serviceIdentifier(serviceId)
                            .envName(envId)
                            .envIdentifier(envId)
                            .infraName(infraId)
                            .infraIdentifier(infraId)
                            .instanceInfo(K8sInstanceInfo.builder().releaseName(releaseName).build())
                            .connectorRef(connectorRef)
                            .build();

    doReturn(Optional.empty()).when(infrastructureMappingService).getByInfrastructureMappingId(eq(infraMappingId));
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(K8sPostProdRollbackInfo.class);
    K8sPostProdRollbackInfo k8sPostProdRollbackInfo = (K8sPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastDeployedAt()).isEqualTo(100);
    assertThat(k8sPostProdRollbackInfo.getEnvName()).isEqualTo(envId);
    assertThat(k8sPostProdRollbackInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(k8sPostProdRollbackInfo.getInfraIdentifier()).isEqualTo(infraId);
    assertThat(k8sPostProdRollbackInfo.getInfraName()).isEqualTo(infraId);
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactDisplayName()).isNull();
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactId()).isNull();
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactId()).isNull();
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactDisplayName()).isNull();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetK8sSwimlaneInfo() {
    Instance instance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(infraMappingId)
            .instanceType(InstanceType.K8S_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .parentUniqueId(parentUniqueId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .infraName(infraId)
            .infraIdentifier(infraId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(K8sInstanceInfo.builder().releaseName(releaseName).blueGreenColor(null).build())
            .connectorRef(connectorRef)
            .build();

    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO(instance);
    doReturn(Optional.of(infrastructureMappingDTO))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(Optional.empty())
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(2), eq(releaseName), eq(infrastructureMappingDTO), eq(false));
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(K8sPostProdRollbackInfo.class);
    K8sPostProdRollbackInfo k8sPostProdRollbackInfo = (K8sPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastDeployedAt()).isEqualTo(100);
    assertThat(k8sPostProdRollbackInfo.getEnvName()).isEqualTo(envId);
    assertThat(k8sPostProdRollbackInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(k8sPostProdRollbackInfo.getInfraIdentifier()).isEqualTo(infraId);
    assertThat(k8sPostProdRollbackInfo.getInfraName()).isEqualTo(infraId);
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactDisplayName()).isEqualTo(artifactName);
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactId()).isEqualTo(artifactId);
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactId()).isNull();
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactDisplayName()).isNull();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetK8sSwimlaneInfoWithArtifact() {
    Instance instance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(infraMappingId)
            .infrastructureKind(infraKind)
            .instanceType(InstanceType.K8S_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .parentUniqueId(parentUniqueId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .infraName(infraId)
            .infraIdentifier(infraId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(K8sInstanceInfo.builder().releaseName(releaseName).build())
            .connectorRef(connectorRef)
            .build();

    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO(instance);
    Optional<DeploymentSummaryDTO> optionalDeploymentSummaryDTO = createDeploymentSummaryDTO(instance, false);
    doReturn(Optional.of(infrastructureMappingDTO))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(optionalDeploymentSummaryDTO)
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(2), eq(releaseName), eq(infrastructureMappingDTO), eq(false));
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(K8sPostProdRollbackInfo.class);
    K8sPostProdRollbackInfo k8sPostProdRollbackInfo = (K8sPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastDeployedAt()).isEqualTo(100);
    assertThat(k8sPostProdRollbackInfo.getEnvName()).isEqualTo(envId);
    assertThat(k8sPostProdRollbackInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(k8sPostProdRollbackInfo.getInfraIdentifier()).isEqualTo(infraId);
    assertThat(k8sPostProdRollbackInfo.getInfraName()).isEqualTo(infraId);
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactDisplayName()).isEqualTo(artifactName);
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactId()).isEqualTo(artifactId);
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactId()).isEqualTo("rollback_artifact_id");
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactDisplayName()).isEqualTo("rollback_artifact_name");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetK8sSwimlaneInfoWithBgDeploymentInstanceSyncKeyCreation() {
    Instance instance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(infraMappingId)
            .infrastructureKind(infraKind)
            .instanceType(InstanceType.K8S_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .parentUniqueId(parentUniqueId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .infraName(infraId)
            .infraIdentifier(infraId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(K8sInstanceInfo.builder().releaseName(releaseName).blueGreenColor("blue").build())
            .connectorRef(connectorRef)
            .build();

    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO(instance);
    Optional<DeploymentSummaryDTO> optionalDeploymentSummaryDTO = createDeploymentSummaryDTO(instance, false);
    doReturn(Optional.of(infrastructureMappingDTO))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(optionalDeploymentSummaryDTO)
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(1), eq(releaseName + "_green"), eq(infrastructureMappingDTO), eq(false));
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(K8sPostProdRollbackInfo.class);
    K8sPostProdRollbackInfo k8sPostProdRollbackInfo = (K8sPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactId()).isEqualTo("rollback_artifact_id");
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactDisplayName()).isEqualTo("rollback_artifact_name");

    // Inverse color
    instance.setInstanceInfo(K8sInstanceInfo.builder().releaseName(releaseName).blueGreenColor("green").build());
    doReturn(Optional.of(infrastructureMappingDTO))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(optionalDeploymentSummaryDTO)
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(1), eq(releaseName + "_blue"), eq(infrastructureMappingDTO), eq(false));
    postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(K8sPostProdRollbackInfo.class);
    k8sPostProdRollbackInfo = (K8sPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactId()).isEqualTo("rollback_artifact_id");
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactDisplayName()).isEqualTo("rollback_artifact_name");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetNativeHelmSwimlaneInfoNoArtifact() {
    Instance instance = Instance.builder()
                            .lastPipelineExecutionName(planExecutionId)
                            .lastDeployedAt(100)
                            .lastPipelineExecutionId(planExecutionId)
                            .infrastructureMappingId(infraMappingId)
                            .infrastructureKind(infraKind)
                            .instanceType(InstanceType.NATIVE_HELM_INSTANCE)
                            .id(instanceKey)
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgId)
                            .projectIdentifier(projectId)
                            .parentUniqueId(parentUniqueId)
                            .serviceIdentifier(serviceId)
                            .envName(envId)
                            .envIdentifier(envId)
                            .infraName(infraId)
                            .infraIdentifier(infraId)
                            .instanceInfo(NativeHelmInstanceInfo.builder().releaseName(releaseName).build())
                            .connectorRef(connectorRef)
                            .build();

    doReturn(Optional.empty()).when(infrastructureMappingService).getByInfrastructureMappingId(eq(infraMappingId));
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(NativeHelmPostProdRollbackInfo.class);
    NativeHelmPostProdRollbackInfo nativeHelmPostProdRollbackInfo =
        (NativeHelmPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(nativeHelmPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(nativeHelmPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(nativeHelmPostProdRollbackInfo.getLastDeployedAt()).isEqualTo(100);
    assertThat(nativeHelmPostProdRollbackInfo.getEnvName()).isEqualTo(envId);
    assertThat(nativeHelmPostProdRollbackInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(nativeHelmPostProdRollbackInfo.getInfraIdentifier()).isEqualTo(infraId);
    assertThat(nativeHelmPostProdRollbackInfo.getInfraName()).isEqualTo(infraId);
    assertThat(nativeHelmPostProdRollbackInfo.getCurrentArtifactDisplayName()).isNull();
    assertThat(nativeHelmPostProdRollbackInfo.getCurrentArtifactId()).isNull();
    assertThat(nativeHelmPostProdRollbackInfo.getPreviousArtifactId()).isNull();
    assertThat(nativeHelmPostProdRollbackInfo.getPreviousArtifactDisplayName()).isNull();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetNativeHelmSwimlaneInfo() {
    Instance instance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(infraMappingId)
            .infrastructureKind(infraKind)
            .instanceType(InstanceType.NATIVE_HELM_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .parentUniqueId(projectId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .infraName(infraId)
            .infraIdentifier(infraId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(NativeHelmInstanceInfo.builder().releaseName(releaseName).build())
            .connectorRef(connectorRef)
            .build();

    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO(instance);
    doReturn(Optional.of(infrastructureMappingDTO))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(Optional.empty())
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(2), eq(releaseName), eq(infrastructureMappingDTO), eq(false));
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(NativeHelmPostProdRollbackInfo.class);
    NativeHelmPostProdRollbackInfo nativeHelmPostProdRollbackInfo =
        (NativeHelmPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(nativeHelmPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(nativeHelmPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(nativeHelmPostProdRollbackInfo.getLastDeployedAt()).isEqualTo(100);
    assertThat(nativeHelmPostProdRollbackInfo.getEnvName()).isEqualTo(envId);
    assertThat(nativeHelmPostProdRollbackInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(nativeHelmPostProdRollbackInfo.getInfraIdentifier()).isEqualTo(infraId);
    assertThat(nativeHelmPostProdRollbackInfo.getInfraName()).isEqualTo(infraId);
    assertThat(nativeHelmPostProdRollbackInfo.getCurrentArtifactDisplayName()).isEqualTo(artifactName);
    assertThat(nativeHelmPostProdRollbackInfo.getCurrentArtifactId()).isEqualTo(artifactId);
    assertThat(nativeHelmPostProdRollbackInfo.getPreviousArtifactId()).isNull();
    assertThat(nativeHelmPostProdRollbackInfo.getPreviousArtifactDisplayName()).isNull();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetNativeHelmSwimlaneInfoWithArtifact() {
    Instance instance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(infraMappingId)
            .infrastructureKind(infraKind)
            .instanceType(InstanceType.NATIVE_HELM_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .parentUniqueId(projectId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .infraName(infraId)
            .infraIdentifier(infraId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(NativeHelmInstanceInfo.builder().releaseName(releaseName).build())
            .connectorRef(connectorRef)
            .build();

    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO(instance);
    Optional<DeploymentSummaryDTO> optionalDeploymentSummaryDTO = createDeploymentSummaryDTO(instance, false);
    doReturn(Optional.of(infrastructureMappingDTO))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(optionalDeploymentSummaryDTO)
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(2), eq(releaseName), eq(infrastructureMappingDTO), eq(false));

    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(NativeHelmPostProdRollbackInfo.class);
    NativeHelmPostProdRollbackInfo nativeHelmPostProdRollbackInfo =
        (NativeHelmPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(nativeHelmPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(nativeHelmPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(nativeHelmPostProdRollbackInfo.getLastDeployedAt()).isEqualTo(100);
    assertThat(nativeHelmPostProdRollbackInfo.getEnvName()).isEqualTo(envId);
    assertThat(nativeHelmPostProdRollbackInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(nativeHelmPostProdRollbackInfo.getInfraIdentifier()).isEqualTo(infraId);
    assertThat(nativeHelmPostProdRollbackInfo.getInfraName()).isEqualTo(infraId);
    assertThat(nativeHelmPostProdRollbackInfo.getCurrentArtifactDisplayName()).isEqualTo(artifactName);
    assertThat(nativeHelmPostProdRollbackInfo.getCurrentArtifactId()).isEqualTo(artifactId);
    assertThat(nativeHelmPostProdRollbackInfo.getPreviousArtifactId()).isEqualTo("rollback_artifact_id");
    assertThat(nativeHelmPostProdRollbackInfo.getPreviousArtifactDisplayName()).isEqualTo("rollback_artifact_name");
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testNativeHelmCurrentArtifactDisplayNamePriority() {
    // Test case 1: displayName = "my-app", chartVersion = "1.2.3" → "my-app"
    Instance instance1 = createNativeHelmInstance("my-app", "1.2.3");
    NativeHelmPostProdRollbackInfo result1 =
        (NativeHelmPostProdRollbackInfo) postProdRollbackHelperUtils.getSwimlaneInfo(instance1);
    assertThat(result1.getCurrentArtifactDisplayName()).isEqualTo("my-app");

    // Test case 2: displayName = "", chartVersion = "1.2.3" → "1.2.3"
    Instance instance2 = createNativeHelmInstance("", "1.2.3");
    NativeHelmPostProdRollbackInfo result2 =
        (NativeHelmPostProdRollbackInfo) postProdRollbackHelperUtils.getSwimlaneInfo(instance2);
    assertThat(result2.getCurrentArtifactDisplayName()).isEqualTo("1.2.3");

    // Test case 3: displayName = "", chartVersion = null → ""
    Instance instance3 = createNativeHelmInstance("", null);
    NativeHelmPostProdRollbackInfo result3 =
        (NativeHelmPostProdRollbackInfo) postProdRollbackHelperUtils.getSwimlaneInfo(instance3);
    assertThat(result3.getCurrentArtifactDisplayName()).isEqualTo("");

    // Test case 4: displayName = null, chartVersion = "1.2.3" → "1.2.3
    Instance instance4 = createNativeHelmInstance(null, "1.2.3");
    NativeHelmPostProdRollbackInfo result4 =
        (NativeHelmPostProdRollbackInfo) postProdRollbackHelperUtils.getSwimlaneInfo(instance4);
    assertThat(result4.getCurrentArtifactDisplayName()).isEqualTo("1.2.3");

    // Test case 5: displayName = null, chartVersion = null → null
    Instance instance5 = createNativeHelmInstance(null, null);
    NativeHelmPostProdRollbackInfo result5 =
        (NativeHelmPostProdRollbackInfo) postProdRollbackHelperUtils.getSwimlaneInfo(instance5);
    assertThat(result5.getCurrentArtifactDisplayName()).isNull();
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testK8sHelmPreviousArtifactDisplayNamePriority() {
    // Test case 1: displayName = null, chartVersion = "2.0.1" → "2.0.1"
    Instance instance1 = createK8sHelmInstance(null, "2.0.1");

    DeploymentSummaryDTO summary1 =
        DeploymentSummaryDTO.builder()
            .accountIdentifier(instance1.getAccountIdentifier())
            .orgIdentifier(instance1.getOrgIdentifier())
            .projectIdentifier(instance1.getProjectIdentifier())
            .parentUniqueId(instance1.getParentUniqueId())
            .infrastructureMappingId(instance1.getInfrastructureMappingId())
            .artifactDetails(ArtifactDetails.builder().displayName(null).artifactId("rollback-artifact").build())
            .deploymentInfoDTO(
                K8sDeploymentInfoDTO.builder().helmChartInfo(HelmChartInfo.builder().version("2.0.1").build()).build())
            .isRollbackDeployment(false)
            .build();

    doReturn(Optional.of(mockInfraMappingDTO(instance1)))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(Optional.of(summary1))
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(2), eq(releaseName), eq(mockInfraMappingDTO(instance1)), eq(false));

    K8sPostProdRollbackInfo result1 = (K8sPostProdRollbackInfo) postProdRollbackHelperUtils.getSwimlaneInfo(instance1);
    assertThat(result1.getPreviousArtifactDisplayName()).isEqualTo("2.0.1");

    // Test case 2: displayName = null, chartVersion = null → null
    Instance instance2 = createK8sHelmInstance(null, null);

    DeploymentSummaryDTO summary2 =
        DeploymentSummaryDTO.builder()
            .accountIdentifier(instance2.getAccountIdentifier())
            .orgIdentifier(instance2.getOrgIdentifier())
            .projectIdentifier(instance2.getProjectIdentifier())
            .parentUniqueId(instance2.getParentUniqueId())
            .infrastructureMappingId(instance2.getInfrastructureMappingId())
            .artifactDetails(ArtifactDetails.builder().displayName(null).artifactId("rollback-artifact").build())
            .deploymentInfoDTO(K8sDeploymentInfoDTO.builder().helmChartInfo(null).build())
            .isRollbackDeployment(false)
            .build();

    doReturn(Optional.of(mockInfraMappingDTO(instance2)))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(Optional.of(summary2))
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(2), eq(releaseName), eq(mockInfraMappingDTO(instance2)), eq(false));

    K8sPostProdRollbackInfo result2 = (K8sPostProdRollbackInfo) postProdRollbackHelperUtils.getSwimlaneInfo(instance2);
    assertThat(result2.getPreviousArtifactDisplayName()).isNull();

    // Test case 3: displayName = "rollback-label", chartVersion = null → "rollback-label"
    Instance instance3 = createK8sHelmInstance(null, null);

    DeploymentSummaryDTO summary3 =
        DeploymentSummaryDTO.builder()
            .accountIdentifier(instance3.getAccountIdentifier())
            .orgIdentifier(instance3.getOrgIdentifier())
            .projectIdentifier(instance3.getProjectIdentifier())
            .parentUniqueId(instance3.getParentUniqueId())
            .infrastructureMappingId(instance3.getInfrastructureMappingId())
            .artifactDetails(
                ArtifactDetails.builder().displayName("rollback-label").artifactId("rollback-artifact").build())
            .deploymentInfoDTO(K8sDeploymentInfoDTO.builder().helmChartInfo(null).build())
            .isRollbackDeployment(false)
            .build();

    doReturn(Optional.of(mockInfraMappingDTO(instance3)))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(Optional.of(summary3))
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(2), eq(releaseName), eq(mockInfraMappingDTO(instance3)), eq(false));

    K8sPostProdRollbackInfo result3 = (K8sPostProdRollbackInfo) postProdRollbackHelperUtils.getSwimlaneInfo(instance3);
    assertThat(result3.getPreviousArtifactDisplayName()).isEqualTo("rollback-label");
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testGetSwimlaneInfo_GitOpsInstanceWithNullInfraMappingId() {
    Instance instance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(null)
            .instanceType(InstanceType.K8S_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .parentUniqueId(parentUniqueId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .infraName(infraId)
            .infraIdentifier(infraId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(K8sInstanceInfo.builder().releaseName(releaseName).build())
            .connectorRef(connectorRef)
            .build();

    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(K8sPostProdRollbackInfo.class);
    K8sPostProdRollbackInfo k8sPostProdRollbackInfo = (K8sPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactDisplayName()).isEqualTo(artifactName);
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactId()).isEqualTo(artifactId);
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactId()).isNull();
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactDisplayName()).isNull();
    // Verify infrastructureMappingService is never called when infraMappingId is null
    verify(infrastructureMappingService, never()).getByInfrastructureMappingId(any());
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testGetSwimlaneInfo_GitOpsInstanceReturnsGitOpsInfo() {
    String clusterIdVal = "my-cluster";
    String agentIdVal = "my-agent";
    String appIdVal = "my-argo-app";

    Instance instance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(200)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(null)
            .infrastructureKind(InfrastructureKind.GITOPS)
            .instanceType(InstanceType.K8S_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .parentUniqueId(parentUniqueId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(GitopsInstanceInfo.builder()
                              .namespace("default")
                              .podName("pod-1")
                              .podId("pod-id-1")
                              .appIdentifier(appIdVal)
                              .agentIdentifier(agentIdVal)
                              .clusterIdentifier(clusterIdVal)
                              .build())
            .connectorRef(connectorRef)
            .build();

    PostProdRollbackSwimLaneInfo result = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(result).isInstanceOf(GitOpsPostProdRollbackInfo.class);
    GitOpsPostProdRollbackInfo gitOpsInfo = (GitOpsPostProdRollbackInfo) result;
    assertThat(gitOpsInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(gitOpsInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(gitOpsInfo.getLastDeployedAt()).isEqualTo(200);
    assertThat(gitOpsInfo.getEnvName()).isEqualTo(envId);
    assertThat(gitOpsInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(gitOpsInfo.getCurrentArtifactDisplayName()).isEqualTo(artifactName);
    assertThat(gitOpsInfo.getCurrentArtifactId()).isEqualTo(artifactId);
    assertThat(gitOpsInfo.getClusterIdentifier()).isEqualTo(clusterIdVal);
    assertThat(gitOpsInfo.getAgentIdentifier()).isEqualTo(agentIdVal);
    assertThat(gitOpsInfo.getAppIdentifier()).isEqualTo(appIdVal);
    verify(infrastructureMappingService, never()).getByInfrastructureMappingId(any());
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testGetSwimlaneInfo_GitOpsInstanceWithNoArtifact() {
    Instance instance = Instance.builder()
                            .lastPipelineExecutionName(planExecutionId)
                            .lastDeployedAt(300)
                            .lastPipelineExecutionId(planExecutionId)
                            .infrastructureMappingId(null)
                            .infrastructureKind(InfrastructureKind.GITOPS)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .id(instanceKey)
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgId)
                            .projectIdentifier(projectId)
                            .parentUniqueId(parentUniqueId)
                            .serviceIdentifier(serviceId)
                            .envName(envId)
                            .envIdentifier(envId)
                            .instanceInfo(GitopsInstanceInfo.builder()
                                              .namespace("default")
                                              .podName("pod-1")
                                              .podId("pod-id-1")
                                              .appIdentifier("app-1")
                                              .agentIdentifier("agent-1")
                                              .clusterIdentifier("cluster-1")
                                              .build())
                            .connectorRef(connectorRef)
                            .build();

    PostProdRollbackSwimLaneInfo result = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(result).isInstanceOf(GitOpsPostProdRollbackInfo.class);
    GitOpsPostProdRollbackInfo gitOpsInfo = (GitOpsPostProdRollbackInfo) result;
    assertThat(gitOpsInfo.getCurrentArtifactDisplayName()).isNull();
    assertThat(gitOpsInfo.getCurrentArtifactId()).isNull();
    assertThat(gitOpsInfo.getClusterIdentifier()).isEqualTo("cluster-1");
    assertThat(gitOpsInfo.getAgentIdentifier()).isEqualTo("agent-1");
    assertThat(gitOpsInfo.getAppIdentifier()).isEqualTo("app-1");
    verify(infrastructureMappingService, never()).getByInfrastructureMappingId(any());
  }

  @Test
  @Owner(developers = PRASHANTPAREEK)
  @Category(UnitTests.class)
  public void testGetSwimlaneInfo_K8sInstanceWithGitOpsKindNotConfusedWithRegularK8s() {
    Instance gitOpsInstance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(null)
            .infrastructureKind(InfrastructureKind.GITOPS)
            .instanceType(InstanceType.K8S_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .parentUniqueId(parentUniqueId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(GitopsInstanceInfo.builder()
                              .namespace("default")
                              .podName("pod-1")
                              .podId("pod-id-1")
                              .appIdentifier("app-1")
                              .agentIdentifier("agent-1")
                              .clusterIdentifier("cluster-1")
                              .build())
            .connectorRef(connectorRef)
            .build();

    Instance regularK8sInstance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(infraMappingId)
            .infrastructureKind(infraKind)
            .instanceType(InstanceType.K8S_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .parentUniqueId(parentUniqueId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .infraName(infraId)
            .infraIdentifier(infraId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(K8sInstanceInfo.builder().releaseName(releaseName).build())
            .connectorRef(connectorRef)
            .build();

    PostProdRollbackSwimLaneInfo gitOpsResult = postProdRollbackHelperUtils.getSwimlaneInfo(gitOpsInstance);
    assertThat(gitOpsResult).isInstanceOf(GitOpsPostProdRollbackInfo.class);

    doReturn(Optional.empty()).when(infrastructureMappingService).getByInfrastructureMappingId(eq(infraMappingId));
    PostProdRollbackSwimLaneInfo k8sResult = postProdRollbackHelperUtils.getSwimlaneInfo(regularK8sInstance);
    assertThat(k8sResult).isInstanceOf(K8sPostProdRollbackInfo.class);
  }

  private Instance createK8sHelmInstance(String displayName, String chartVersion) {
    ArtifactDetails artifactDetails =
        ArtifactDetails.builder().displayName(displayName).artifactId("test-artifact").build();

    return Instance.builder()
        .lastPipelineExecutionName(planExecutionId)
        .lastDeployedAt(100)
        .lastPipelineExecutionId(planExecutionId)
        .infrastructureMappingId(infraMappingId)
        .infrastructureKind(infraKind)
        .instanceType(InstanceType.K8S_INSTANCE)
        .id(instanceKey)
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .parentUniqueId(projectId)
        .serviceIdentifier(serviceId)
        .envName(envId)
        .envIdentifier(envId)
        .infraName(infraId)
        .infraIdentifier(infraId)
        .primaryArtifact(artifactDetails)
        .instanceInfo(K8sInstanceInfo.builder()
                          .releaseName(releaseName)
                          .helmChartInfo(HelmChartInfo.builder().version(chartVersion).build())
                          .build())
        .connectorRef(connectorRef)
        .build();
  }

  private Instance createNativeHelmInstance(String displayName, String chartVersion) {
    ArtifactDetails artifactDetails =
        ArtifactDetails.builder().displayName(displayName).artifactId("test-artifact").build();

    return Instance.builder()
        .lastPipelineExecutionName(planExecutionId)
        .lastDeployedAt(100)
        .lastPipelineExecutionId(planExecutionId)
        .infrastructureMappingId(infraMappingId)
        .infrastructureKind(infraKind)
        .instanceType(InstanceType.NATIVE_HELM_INSTANCE)
        .id(instanceKey)
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .parentUniqueId(parentUniqueId)
        .serviceIdentifier(serviceId)
        .envName(envId)
        .envIdentifier(envId)
        .infraName(infraId)
        .infraIdentifier(infraId)
        .primaryArtifact(artifactDetails)
        .instanceInfo(NativeHelmInstanceInfo.builder()
                          .releaseName(releaseName)
                          .helmChartInfo(HelmChartInfo.builder().version(chartVersion).build())
                          .build())
        .connectorRef(connectorRef)
        .build();
  }

  private Optional<DeploymentSummaryDTO> createDeploymentSummaryDTO(Instance instance, boolean isRollbackDeployment) {
    return Optional.of(DeploymentSummaryDTO.builder()
                           .accountIdentifier(instance.getAccountIdentifier())
                           .artifactDetails(ArtifactDetails.builder()
                                                .displayName("rollback_artifact_name")
                                                .artifactId("rollback_artifact_id")
                                                .build())
                           .isRollbackDeployment(isRollbackDeployment)
                           .infrastructureMappingId(instance.getInfrastructureMappingId())
                           .orgIdentifier(instance.getOrgIdentifier())
                           .projectIdentifier(instance.getProjectIdentifier())
                           .parentUniqueId(instance.getParentUniqueId())
                           .build());
  }

  private InfrastructureMappingDTO mockInfraMappingDTO(Instance instance) {
    return InfrastructureMappingDTO.builder()
        .id(instance.getInfrastructureMappingId())
        .accountIdentifier(instance.getAccountIdentifier())
        .orgIdentifier(instance.getOrgIdentifier())
        .projectIdentifier(instance.getProjectIdentifier())
        .parentUniqueId(instance.getParentUniqueId())
        .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
        .envIdentifier(instance.getEnvIdentifier())
        .serviceIdentifier(instance.getServiceIdentifier())
        .infrastructureKey(infraKey)
        .build();
  }
}

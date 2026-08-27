/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
import static io.harness.rule.OwnerRule.ABHAY;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.DHIRAJ;
import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;
import static io.harness.rule.OwnerRule.SMCCONKEY;
import static io.harness.rule.OwnerRule.TAPAN;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.steps.CIRegistry;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.beans.steps.stepinfo.IACMTerraformPluginInfo;
import io.harness.beans.steps.stepinfo.UploadToArtifactoryStepInfo;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.DockerInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.ContainerlessPluginConfig;
import io.harness.ci.execution.execution.CIExecutionConfigServiceImpl;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.task.stepstatus.ErrorDetails;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureSubType;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CIStepInfoUtilsTest extends CIExecutionTestBase {
  @Inject private CIStepInfoUtils ciStepInfoUtils;
  @Mock private CIExecutionConfigServiceImpl ciExecutionConfigService;
  @Mock private CIFeatureFlagService ciFeatureFlagService;
  @Mock private Infrastructure infrastructure;
  @Mock private CIInfraDetails ciInfraDetails;

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testResolveConnectorFromRegistries() {
    List<CIRegistry> registries = null;
    Optional<String> connector = ciStepInfoUtils.resolveConnectorFromRegistries(registries, null);
    assertThat(connector).isEmpty();

    registries = Arrays.asList(CIRegistry.builder().connectorIdentifier("id1").match("^test/").build(),
        CIRegistry.builder().connectorIdentifier("id2").connectorType(ConnectorType.GCP).build(),
        CIRegistry.builder().connectorIdentifier("id3").connectorType(ConnectorType.AWS).build(),
        CIRegistry.builder().connectorIdentifier("id4").connectorType(ConnectorType.AZURE).build(),
        CIRegistry.builder().connectorIdentifier("id5").connectorType(ConnectorType.DOCKER).build());
    connector = ciStepInfoUtils.resolveConnectorFromRegistries(registries, "test/image");
    assertThat(connector).isPresent();
    assertThat(connector.get()).isEqualTo("id1");
    connector = ciStepInfoUtils.resolveConnectorFromRegistries(registries, "test2/image");
    assertThat(connector).isPresent();
    assertThat(connector.get()).isEqualTo("id5");
    connector = ciStepInfoUtils.resolveConnectorFromRegistries(registries, "us.gcr.io/image");
    assertThat(connector).isPresent();
    assertThat(connector.get()).isEqualTo("id2");
    connector = ciStepInfoUtils.resolveConnectorFromRegistries(registries, "account.dkr.ecr.region.amazonaws.com/img");
    assertThat(connector).isPresent();
    assertThat(connector.get()).isEqualTo("id3");
    connector = ciStepInfoUtils.resolveConnectorFromRegistries(registries, "myregistry.azurecr.io/samples/nginx");
    assertThat(connector).isPresent();
    assertThat(connector.get()).isEqualTo("id4");
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testCanRunVmStepOnHostDliteStage() {
    String accountId = "accountId";
    DliteVmStageInfraDetails stageInfraDetails = DliteVmStageInfraDetails.builder().build();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder().build();
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(CIStepInfoType.DOCKER, dockerStepInfo))
        .thenReturn(ContainerlessPluginConfig.builder().name("pluginName").build());
    boolean result = CIStepInfoUtils.canRunVmStepOnHost(CIStepInfoType.DOCKER, stageInfraDetails, accountId,
        ciExecutionConfigService, ciFeatureFlagService, dockerStepInfo);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testCanRunVmStepOnHostDliteStageFfDisabled() {
    String accountId = "accountId";
    DliteVmStageInfraDetails stageInfraDetails = DliteVmStageInfraDetails.builder().build();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder().build();
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(false);
    boolean result = CIStepInfoUtils.canRunVmStepOnHost(CIStepInfoType.DOCKER, stageInfraDetails, accountId,
        ciExecutionConfigService, ciFeatureFlagService, dockerStepInfo);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testCanRunVmStepOnHostVmStage() {
    String accountId = "accountId";
    VmStageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().build();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder().build();
    boolean result = CIStepInfoUtils.canRunVmStepOnHost(CIStepInfoType.DOCKER, stageInfraDetails, accountId,
        ciExecutionConfigService, ciFeatureFlagService, dockerStepInfo);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testCanRunVmStepOnHostVmStageGitCloneContainer() {
    String accountId = "accountId";
    VmStageInfraDetails stageInfraDetails =
        VmStageInfraDetails.builder().infraInfo(CIInitializeTaskParams.Type.VM).build();
    VmInfraYaml infra =
        VmInfraYaml.builder()
            .spec(VmPoolYaml.builder()
                      .spec(VmPoolYamlSpec.builder().os(ParameterField.createValueField(OSType.Linux)).build())
                      .build())
            .build();
    GitCloneStepInfo gitCloneStep = GitCloneStepInfo.builder().build();
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_GIT_CLONE_CONTAINERLESS, accountId)).thenReturn(false);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(CIStepInfoType.GIT_CLONE, gitCloneStep))
        .thenReturn(ContainerlessPluginConfig.builder().name("pluginName").build());
    boolean result = CIStepInfoUtils.canRunVmStepOnHost(CIStepInfoType.GIT_CLONE, stageInfraDetails, accountId,
        ciExecutionConfigService, ciFeatureFlagService, gitCloneStep, infra);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testCanRunVmStepOnHostDliteGitCloneContainer() {
    String accountId = "accountId";
    DliteVmStageInfraDetails stageInfraDetails = DliteVmStageInfraDetails.builder().build();
    HostedVmInfraYaml infra = HostedVmInfraYaml.builder().build();
    GitCloneStepInfo gitCloneStep = GitCloneStepInfo.builder().build();
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_GIT_CLONE_CONTAINERLESS, accountId)).thenReturn(false);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(CIStepInfoType.GIT_CLONE, gitCloneStep))
        .thenReturn(ContainerlessPluginConfig.builder().name("pluginName").build());
    boolean result = CIStepInfoUtils.canRunVmStepOnHost(CIStepInfoType.GIT_CLONE, stageInfraDetails, accountId,
        ciExecutionConfigService, ciFeatureFlagService, gitCloneStep, infra);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testCanRunVmStepOnHostVmStageGitCloneEnabled() {
    String accountId = "accountId";
    VmStageInfraDetails stageInfraDetails =
        VmStageInfraDetails.builder().infraInfo(CIInitializeTaskParams.Type.VM).build();
    VmInfraYaml infra =
        VmInfraYaml.builder()
            .spec(VmPoolYaml.builder()
                      .spec(VmPoolYamlSpec.builder().os(ParameterField.createValueField(OSType.Linux)).build())
                      .build())
            .build();
    GitCloneStepInfo gitCloneStep = GitCloneStepInfo.builder().build();
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_GIT_CLONE_CONTAINERLESS, accountId)).thenReturn(true);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(CIStepInfoType.GIT_CLONE, gitCloneStep))
        .thenReturn(ContainerlessPluginConfig.builder().name("pluginName").build());
    boolean result = CIStepInfoUtils.canRunVmStepOnHost(CIStepInfoType.GIT_CLONE, stageInfraDetails, accountId,
        ciExecutionConfigService, ciFeatureFlagService, gitCloneStep, infra);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testCanRunVmStepOnHostVmStageGitCloneDisabled() {
    String accountId = "accountId";
    VmStageInfraDetails stageInfraDetails =
        VmStageInfraDetails.builder().infraInfo(CIInitializeTaskParams.Type.VM).build();
    VmInfraYaml infra =
        VmInfraYaml.builder()
            .spec(VmPoolYaml.builder()
                      .spec(VmPoolYamlSpec.builder().os(ParameterField.createValueField(OSType.Linux)).build())
                      .build())
            .build();
    GitCloneStepInfo gitCloneStep = GitCloneStepInfo.builder().build();
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_GIT_CLONE_CONTAINERLESS, accountId)).thenReturn(false);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(CIStepInfoType.GIT_CLONE, gitCloneStep))
        .thenReturn(ContainerlessPluginConfig.builder().name("pluginName").build());
    boolean result = CIStepInfoUtils.canRunVmStepOnHost(CIStepInfoType.GIT_CLONE, stageInfraDetails, accountId,
        ciExecutionConfigService, ciFeatureFlagService, gitCloneStep, infra);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testCanRunVmStepOnHostDliteEnabled() {
    String accountId = "accountId";
    DliteVmStageInfraDetails stageInfraDetails = DliteVmStageInfraDetails.builder().build();
    HostedVmInfraYaml infra = HostedVmInfraYaml.builder().build();
    GitCloneStepInfo gitCloneStep = GitCloneStepInfo.builder().build();
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_GIT_CLONE_CONTAINERLESS, accountId)).thenReturn(true);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(any())).thenReturn(ciInfraDetails);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(CIStepInfoType.GIT_CLONE, gitCloneStep))
        .thenReturn(ContainerlessPluginConfig.builder().name("pluginName").build());
    boolean result = CIStepInfoUtils.canRunVmStepOnHost(CIStepInfoType.GIT_CLONE, stageInfraDetails, accountId,
        ciExecutionConfigService, ciFeatureFlagService, gitCloneStep, infra);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testCanRunVmStepOnHostDliteDisabled() {
    String accountId = "accountId";
    DliteVmStageInfraDetails stageInfraDetails = DliteVmStageInfraDetails.builder().build();
    HostedVmInfraYaml infra = HostedVmInfraYaml.builder().build();
    GitCloneStepInfo gitCloneStep = GitCloneStepInfo.builder().build();
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_GIT_CLONE_CONTAINERLESS, accountId)).thenReturn(false);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(any())).thenReturn(ciInfraDetails);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(CIStepInfoType.GIT_CLONE, gitCloneStep))
        .thenReturn(ContainerlessPluginConfig.builder().name("pluginName").build());
    boolean result = CIStepInfoUtils.canRunVmStepOnHost(CIStepInfoType.GIT_CLONE, stageInfraDetails, accountId,
        ciExecutionConfigService, ciFeatureFlagService, gitCloneStep, infra);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCanRunVmStepOnHostForArtifactoryUploadStepNonMac() {
    String accountId = "accountId";
    DliteVmStageInfraDetails stageInfraDetails = DliteVmStageInfraDetails.builder().build();
    HostedVmInfraYaml infra = HostedVmInfraYaml.builder().build();
    UploadToArtifactoryStepInfo uploadToArtifactoryStepInfo = UploadToArtifactoryStepInfo.builder().build();
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_ENABLE_CONTAINERLESS_ARTIFACTORY, accountId)).thenReturn(true);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(any())).thenReturn(ciInfraDetails);
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(
             CIStepInfoType.UPLOAD_ARTIFACTORY, uploadToArtifactoryStepInfo))
        .thenReturn(ContainerlessPluginConfig.builder().name("pluginName").build());
    boolean result = CIStepInfoUtils.canRunVmStepOnHost(CIStepInfoType.UPLOAD_ARTIFACTORY, stageInfraDetails, accountId,
        ciExecutionConfigService, ciFeatureFlagService, uploadToArtifactoryStepInfo, infra);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCanRunVmStepOnHostForArtifactoryUploadStepMac() {
    String accountId = "accountId";
    DliteVmStageInfraDetails stageInfraDetails = DliteVmStageInfraDetails.builder().build();
    HostedVmInfraYaml infra = HostedVmInfraYaml.builder().build();
    UploadToArtifactoryStepInfo uploadToArtifactoryStepInfo = UploadToArtifactoryStepInfo.builder().build();
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_HOSTED_CONTAINERLESS_OOTB_STEP_ENABLED, accountId))
        .thenReturn(true);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(any()))
        .thenReturn(CIInfraDetails.builder().infraOSType("MacOS").build());
    when(ciExecutionConfigService.getContainerlessPluginNameForVM(
             CIStepInfoType.UPLOAD_ARTIFACTORY, uploadToArtifactoryStepInfo))
        .thenReturn(ContainerlessPluginConfig.builder().name("pluginName").build());
    boolean result = CIStepInfoUtils.canRunVmStepOnHost(CIStepInfoType.UPLOAD_ARTIFACTORY, stageInfraDetails, accountId,
        ciExecutionConfigService, ciFeatureFlagService, uploadToArtifactoryStepInfo, infra);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testUseS3ForCacheIntel_whenEnableS3Only() {
    String accountId = "accountId";
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_S3_FOR_CACHE, accountId)).thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_CACHE, accountId)).thenReturn(false);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(infrastructure)).thenReturn(ciInfraDetails);
    when(ciInfraDetails.getInfraOSType()).thenReturn(OSType.Linux.toString());
    boolean result = CIStepInfoUtils.useS3ForCacheIntel(ciFeatureFlagService, accountId, infrastructure);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testUseS3ForCacheIntel_whenEnableAllAndNotMac() {
    String accountId = "accountId";
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_S3_FOR_CACHE, accountId)).thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_CACHE, accountId)).thenReturn(true);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(infrastructure)).thenReturn(ciInfraDetails);
    when(ciInfraDetails.getInfraOSType()).thenReturn(OSType.Linux.toString());
    boolean result = CIStepInfoUtils.useS3ForCacheIntel(ciFeatureFlagService, accountId, infrastructure);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testUseS3ForCacheIntel_whenEnableAllAndMac() {
    String accountId = "accountId";
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_S3_FOR_CACHE, accountId)).thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_CACHE, accountId)).thenReturn(true);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(infrastructure)).thenReturn(ciInfraDetails);
    when(ciInfraDetails.getInfraOSType()).thenReturn(OSType.MacOS.toString());
    boolean result = CIStepInfoUtils.useS3ForCacheIntel(ciFeatureFlagService, accountId, infrastructure);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testUseS3ForCacheIntel_whenEnableNone() {
    String accountId = "accountId";
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_S3_FOR_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_CACHE, accountId)).thenReturn(false);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(infrastructure)).thenReturn(ciInfraDetails);
    when(ciInfraDetails.getInfraOSType()).thenReturn(OSType.MacOS.toString());
    boolean result = CIStepInfoUtils.useS3ForCacheIntel(ciFeatureFlagService, accountId, infrastructure);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testUseS3ForDLC_whenEnableGCPMac() {
    String accountId = "accountId";
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_S3_FOR_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_DLC, accountId)).thenReturn(true);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(infrastructure)).thenReturn(ciInfraDetails);
    boolean result = CIStepInfoUtils.useGCPForDLCMac(ciFeatureFlagService, accountId, OSType.MacOS.toString());
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testUseS3ForDLC_whenEnableGCPMacSignedUrl() {
    String accountId = "accountId";
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_S3_FOR_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_DLC, accountId)).thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_DLC_SIGNED_URL, accountId)).thenReturn(true);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(infrastructure)).thenReturn(ciInfraDetails);
    boolean result = CIStepInfoUtils.useGCPForDLCMac(ciFeatureFlagService, accountId, OSType.MacOS.toString());
    assertThat(result).isTrue();
    result = CIStepInfoUtils.useHarnessForDLC(ciFeatureFlagService, accountId, OSType.MacOS.toString());
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testUseS3ForDLC_whenEnableGCPLinuxSignedUrl() {
    String accountId = "accountId";
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_S3_FOR_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_DLC, accountId)).thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_DLC_SIGNED_URL, accountId)).thenReturn(true);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(infrastructure)).thenReturn(ciInfraDetails);
    boolean result = CIStepInfoUtils.useGCPForDLCMac(ciFeatureFlagService, accountId, OSType.Linux.toString());
    assertThat(result).isFalse();
    result = CIStepInfoUtils.useHarnessForDLC(ciFeatureFlagService, accountId, OSType.Linux.toString());
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testUseS3ForDLC_whenEnableGCPMacNoFlagSignedUrl() {
    String accountId = "accountId";
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_S3_FOR_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_DLC, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_DLC_SIGNED_URL, accountId)).thenReturn(true);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(infrastructure)).thenReturn(ciInfraDetails);
    boolean result = CIStepInfoUtils.useGCPForDLCMac(ciFeatureFlagService, accountId, OSType.MacOS.toString());
    assertThat(result).isFalse();
    result = CIStepInfoUtils.useHarnessForDLC(ciFeatureFlagService, accountId, OSType.MacOS.toString());
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testUseS3ForCacheIntel_whenEnableGCPMacSignedURL() {
    String accountId = "accountId";
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_S3_FOR_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_CACHE, accountId)).thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_CACHE_INTELLIGENCE_SIGNED_URL, accountId)).thenReturn(true);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(infrastructure)).thenReturn(ciInfraDetails);
    when(ciInfraDetails.getInfraOSType()).thenReturn(OSType.MacOS.toString());
    boolean result = CIStepInfoUtils.useHarnessForCacheIntel(ciFeatureFlagService, accountId, infrastructure);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testUseS3ForCacheIntel_whenEnableGCPLinuxSignedURL() {
    String accountId = "accountId";
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_S3_FOR_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_CACHE, accountId)).thenReturn(true);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_CACHE_INTELLIGENCE_SIGNED_URL, accountId)).thenReturn(true);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(infrastructure)).thenReturn(ciInfraDetails);
    when(ciInfraDetails.getInfraOSType()).thenReturn(OSType.Linux.toString());
    boolean result = CIStepInfoUtils.useHarnessForCacheIntel(ciFeatureFlagService, accountId, infrastructure);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testUseS3ForCacheIntel_whenEnableGCPMacNoFlagSignedURL() {
    String accountId = "accountId";
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_S3_FOR_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_USE_GCS_FOR_MACOS_CACHE, accountId)).thenReturn(false);
    when(ciFeatureFlagService.isEnabled(FeatureName.CI_CACHE_INTELLIGENCE_SIGNED_URL, accountId)).thenReturn(true);
    mockStatic(IntegrationStageUtils.class);
    when(IntegrationStageUtils.getCiInfraDetails(infrastructure)).thenReturn(ciInfraDetails);
    when(ciInfraDetails.getInfraOSType()).thenReturn(OSType.MacOS.toString());
    boolean result = CIStepInfoUtils.useHarnessForCacheIntel(ciFeatureFlagService, accountId, infrastructure);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testIsLocalBuild() {
    StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().build();
    assertThat(CIStepInfoUtils.isLocal(stageInfraDetails)).isFalse();

    stageInfraDetails = VmStageInfraDetails.builder().infraInfo(CIInitializeTaskParams.Type.DOCKER).build();
    assertThat(CIStepInfoUtils.isLocal(stageInfraDetails)).isTrue();

    stageInfraDetails = VmStageInfraDetails.builder().infraInfo(CIInitializeTaskParams.Type.VM).build();
    assertThat(CIStepInfoUtils.isLocal(stageInfraDetails)).isFalse();
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testIsLocalContainerless() {
    StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().isContainerLessWithRunner(true).build();
    assertThat(CIStepInfoUtils.isLocalContainerLess(true, CIStepInfoType.GIT_CLONE)).isTrue();
    assertThat(CIStepInfoUtils.isLocalContainerLess(false, CIStepInfoType.GIT_CLONE)).isFalse();
    assertThat(CIStepInfoUtils.isLocalContainerLess(true, CIStepInfoType.UPLOAD_ARTIFACTORY)).isTrue();
    assertThat(CIStepInfoUtils.isLocalContainerLess(false, CIStepInfoType.UPLOAD_ARTIFACTORY)).isFalse();
    assertThat(CIStepInfoUtils.isLocalContainerLess(true, CIStepInfoType.UPLOAD_GCS)).isTrue();
    assertThat(CIStepInfoUtils.isLocalContainerLess(false, CIStepInfoType.UPLOAD_GCS)).isFalse();
    assertThat(CIStepInfoUtils.isGitCloneContainerless(stageInfraDetails, "accountId", ciFeatureFlagService, ""))
        .isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetContainerlessGitCloneConfigsFolder() {
    Ambiance ambiance = Ambiance.newBuilder().setStageExecutionId("stageId").build();
    String stepId = "stepId";
    DockerInfraYaml linuxInfra =
        DockerInfraYaml.builder()
            .spec(DockerInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();
    DockerInfraYaml windowsInfra =
        DockerInfraYaml.builder()
            .spec(DockerInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Windows)).build()))
                      .build())
            .build();
    String linuxPath = CIStepInfoUtils.getContainerlessGitCloneConfigsFolder(ambiance, stepId, linuxInfra);
    assertThat(linuxPath).isNotNull();
    assertThat(linuxPath).isEqualTo("/tmp/harness/stageId_config/stepId_gitconfig");
    String windowsPath = CIStepInfoUtils.getContainerlessGitCloneConfigsFolder(ambiance, stepId, windowsInfra);
    assertThat(windowsPath).isNotNull();
    assertThat(windowsPath).isEqualTo("C:\\tmp\\harness\\stageId_config\\stepId_gitconfig");
  }

  // Error Categorization Tests

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testMapErrorCategoryToFailureType_NullErrorDetails() {
    FailureType result = CIStepInfoUtils.mapErrorCategoryToFailureType(null);
    assertThat(result).isEqualTo(FailureType.APPLICATION_FAILURE);
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testMapErrorCategoryToFailureType_ValidErrorDetails() {
    ErrorDetails errorDetails = ErrorDetails.builder().failureType("INFRASTRUCTURE_FAILURE").build();
    FailureType result = CIStepInfoUtils.mapErrorCategoryToFailureType(errorDetails);
    assertThat(result).isEqualTo(FailureType.INFRASTRUCTURE_FAILURE);
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testMapErrorCategoryToFailureType_InvalidFailureType() {
    ErrorDetails errorDetails = ErrorDetails.builder().failureType("INVALID_TYPE").build();
    FailureType result = CIStepInfoUtils.mapErrorCategoryToFailureType(errorDetails);
    assertThat(result).isEqualTo(FailureType.UNKNOWN_FAILURE);
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testBuildFailureMessage_NullErrorDetails() {
    String originalError = "Original error message";
    String result = CIStepInfoUtils.buildFailureMessage(originalError, null);
    assertThat(result).isEqualTo(originalError);
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testBuildFailureMessage_ErrorDetailsWithCustomMessage() {
    String originalError = "Original error message";
    ErrorDetails errorDetails = ErrorDetails.builder().message("Custom error message").build();
    String result = CIStepInfoUtils.buildFailureMessage(originalError, errorDetails);
    assertThat(result).isEqualTo("Custom error message");
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testBuildFailureMessage_ErrorDetailsWithEmptyMessage() {
    String originalError = "Original error message";
    ErrorDetails errorDetails = ErrorDetails.builder().message("").build();
    String result = CIStepInfoUtils.buildFailureMessage(originalError, errorDetails);
    assertThat(result).isEqualTo(originalError);
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testBuildFailureMessage_ErrorDetailsWithNullMessage() {
    String originalError = "Original error message";
    ErrorDetails errorDetails = ErrorDetails.builder().build();
    String result = CIStepInfoUtils.buildFailureMessage(originalError, errorDetails);
    assertThat(result).isEqualTo(originalError);
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetDefaultCIFailureDataInfo_WithNullErrorDetails() {
    Ambiance ambiance = Ambiance.newBuilder().setStageExecutionId("stageId").build();
    String errorMessage = "Test error message";

    FailureData result = CIStepInfoUtils.getDefaultCIFailureDataInfo(errorMessage, ambiance, null);

    assertThat(result).isNotNull();
    assertThat(result.getMessage()).isEqualTo(errorMessage);
    assertThat(result.getFailureTypesList()).contains(FailureType.APPLICATION_FAILURE);
    assertThat(result.getFailureTypeInfosList()).isNotEmpty();
    assertThat(result.getFailureTypeInfosList().get(0).getFailureType()).isEqualTo(FailureType.APPLICATION_FAILURE);
    assertThat(result.getFailureTypeInfosList().get(0).getFailureSubType()).isEqualTo(FailureSubType.GENERAL_ERROR);
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetDefaultCIFailureDataInfo_WithValidErrorDetails() {
    Ambiance ambiance = Ambiance.newBuilder().setStageExecutionId("stageId").build();
    String errorMessage = "Test error message";
    ErrorDetails errorDetails = ErrorDetails.builder()
                                    .failureType("INFRASTRUCTURE_FAILURE")
                                    .failureSubType("POD_EVICTION")
                                    .message("Custom message")
                                    .build();

    FailureData result = CIStepInfoUtils.getDefaultCIFailureDataInfo(errorMessage, ambiance, errorDetails);

    assertThat(result).isNotNull();
    assertThat(result.getFailureTypesList()).contains(FailureType.INFRASTRUCTURE_FAILURE);
    assertThat(result.getFailureTypeInfosList()).isNotEmpty();
    assertThat(result.getFailureTypeInfosList().get(0).getFailureType()).isEqualTo(FailureType.INFRASTRUCTURE_FAILURE);
    assertThat(result.getFailureTypeInfosList().get(0).getFailureSubType()).isEqualTo(FailureSubType.POD_EVICTION);
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetDefaultCIFailureDataInfo_TwoParamOverload() {
    Ambiance ambiance = Ambiance.newBuilder().setStageExecutionId("stageId").build();
    String errorMessage = "Test error message";

    FailureData result = CIStepInfoUtils.getDefaultCIFailureDataInfo(errorMessage, ambiance);

    assertThat(result).isNotNull();
    assertThat(result.getMessage()).isEqualTo(errorMessage);
    assertThat(result.getFailureTypesList()).contains(FailureType.APPLICATION_FAILURE);
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetDefaultCIFailureDataInfo_AllCIFailureTypes() {
    Ambiance ambiance = Ambiance.newBuilder().setStageExecutionId("stageId").build();

    // Test PLUGIN_IMAGE_FAILURE
    ErrorDetails pluginError = ErrorDetails.builder().failureType("PLUGIN_IMAGE_FAILURE").build();
    FailureData pluginResult = CIStepInfoUtils.getDefaultCIFailureDataInfo("error", ambiance, pluginError);
    assertThat(pluginResult.getFailureTypesList()).contains(FailureType.PLUGIN_IMAGE_FAILURE);

    // Test RESOURCE_LIMITS_FAILURE
    ErrorDetails resourceError = ErrorDetails.builder().failureType("RESOURCE_LIMITS_FAILURE").build();
    FailureData resourceResult = CIStepInfoUtils.getDefaultCIFailureDataInfo("error", ambiance, resourceError);
    assertThat(resourceResult.getFailureTypesList()).contains(FailureType.RESOURCE_LIMITS_FAILURE);

    // Test CONFIGURATION_FAILURE
    ErrorDetails configError = ErrorDetails.builder().failureType("CONFIGURATION_FAILURE").build();
    FailureData configResult = CIStepInfoUtils.getDefaultCIFailureDataInfo("error", ambiance, configError);
    assertThat(configResult.getFailureTypesList()).contains(FailureType.CONFIGURATION_FAILURE);

    // Test RETRYABLE_TRANSIENT_FAILURE
    ErrorDetails retryableError = ErrorDetails.builder().failureType("RETRYABLE_TRANSIENT_FAILURE").build();
    FailureData retryableResult = CIStepInfoUtils.getDefaultCIFailureDataInfo("error", ambiance, retryableError);
    assertThat(retryableResult.getFailureTypesList()).contains(FailureType.RETRYABLE_TRANSIENT_FAILURE);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsLocalVmsStageVarEnabled_WhenSetToTrue() {
    List<NGVariable> variables = List.of(StringNGVariable.builder()
                                             .name(CIStepInfoUtils.HARNESS_CI_INTERNAL_LOCAL_VMS)
                                             .type(NGVariableType.STRING)
                                             .value(ParameterField.createValueField("true"))
                                             .build());
    assertThat(CIStepInfoUtils.isLocalVmsStageVarEnabled(variables)).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsLocalVmsStageVarEnabled_WhenSetToFalse() {
    List<NGVariable> variables = List.of(StringNGVariable.builder()
                                             .name(CIStepInfoUtils.HARNESS_CI_INTERNAL_LOCAL_VMS)
                                             .type(NGVariableType.STRING)
                                             .value(ParameterField.createValueField("false"))
                                             .build());
    assertThat(CIStepInfoUtils.isLocalVmsStageVarEnabled(variables)).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsLocalVmsStageVarEnabled_WhenNotSet() {
    assertThat(CIStepInfoUtils.isLocalVmsStageVarEnabled(List.of())).isFalse();
    assertThat(CIStepInfoUtils.isLocalVmsStageVarEnabled(null)).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsLocalVmsStageVarDisabled_WhenSetToFalse() {
    List<NGVariable> variables = List.of(StringNGVariable.builder()
                                             .name(CIStepInfoUtils.HARNESS_CI_INTERNAL_LOCAL_VMS)
                                             .type(NGVariableType.STRING)
                                             .value(ParameterField.createValueField("false"))
                                             .build());
    assertThat(CIStepInfoUtils.isLocalVmsStageVarDisabled(variables)).isTrue();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsLocalVmsStageVarDisabled_WhenSetToTrue() {
    List<NGVariable> variables = List.of(StringNGVariable.builder()
                                             .name(CIStepInfoUtils.HARNESS_CI_INTERNAL_LOCAL_VMS)
                                             .type(NGVariableType.STRING)
                                             .value(ParameterField.createValueField("true"))
                                             .build());
    assertThat(CIStepInfoUtils.isLocalVmsStageVarDisabled(variables)).isFalse();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testIsLocalVmsStageVarDisabled_WhenNotSet() {
    assertThat(CIStepInfoUtils.isLocalVmsStageVarDisabled(List.of())).isFalse();
    assertThat(CIStepInfoUtils.isLocalVmsStageVarDisabled(null)).isFalse();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testIsRequiredFieldsOnlyInitStripStageVarEnabled_WhenSetToTrue() {
    List<NGVariable> variables = List.of(StringNGVariable.builder()
                                             .name(CIStepInfoUtils.CI_INIT_REQUIRED_FIELDS_ONLY)
                                             .type(NGVariableType.STRING)
                                             .value(ParameterField.createValueField("true"))
                                             .build());
    assertThat(CIStepInfoUtils.isRequiredFieldsOnlyInitStripStageVarEnabled(variables)).isTrue();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testIsRequiredFieldsOnlyInitStripStageVarEnabled_LenientTruthyValues() {
    for (String truthy : List.of("True", "TRUE", "  true  ", "tRuE")) {
      List<NGVariable> variables = List.of(StringNGVariable.builder()
                                               .name(CIStepInfoUtils.CI_INIT_REQUIRED_FIELDS_ONLY)
                                               .type(NGVariableType.STRING)
                                               .value(ParameterField.createValueField(truthy))
                                               .build());
      assertThat(CIStepInfoUtils.isRequiredFieldsOnlyInitStripStageVarEnabled(variables))
          .as("value=%s should opt in", truthy)
          .isTrue();
    }
    for (String nonTruthy : List.of("false", "yes", "1", "", "<+pipeline.variables.x>")) {
      List<NGVariable> variables = List.of(StringNGVariable.builder()
                                               .name(CIStepInfoUtils.CI_INIT_REQUIRED_FIELDS_ONLY)
                                               .type(NGVariableType.STRING)
                                               .value(ParameterField.createValueField(nonTruthy))
                                               .build());
      assertThat(CIStepInfoUtils.isRequiredFieldsOnlyInitStripStageVarEnabled(variables))
          .as("value=%s should not opt in", nonTruthy)
          .isFalse();
    }
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testIsRequiredFieldsOnlyInitStripStageVarEnabled_WhenSetToFalse() {
    List<NGVariable> variables = List.of(StringNGVariable.builder()
                                             .name(CIStepInfoUtils.CI_INIT_REQUIRED_FIELDS_ONLY)
                                             .type(NGVariableType.STRING)
                                             .value(ParameterField.createValueField("false"))
                                             .build());
    assertThat(CIStepInfoUtils.isRequiredFieldsOnlyInitStripStageVarEnabled(variables)).isFalse();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testIsRequiredFieldsOnlyInitStripStageVarEnabled_WhenNotSet() {
    assertThat(CIStepInfoUtils.isRequiredFieldsOnlyInitStripStageVarEnabled(List.of())).isFalse();
    assertThat(CIStepInfoUtils.isRequiredFieldsOnlyInitStripStageVarEnabled(null)).isFalse();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testIsRequiredFieldsOnlyInitStripStageVarEnabled_WhenDifferentVariable() {
    List<NGVariable> variables = List.of(StringNGVariable.builder()
                                             .name("SOME_OTHER_VAR")
                                             .type(NGVariableType.STRING)
                                             .value(ParameterField.createValueField("true"))
                                             .build());
    assertThat(CIStepInfoUtils.isRequiredFieldsOnlyInitStripStageVarEnabled(variables)).isFalse();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void testIsRequiredFieldsOnlyInitStripEnabled_FfOrStageVar() {
    List<NGVariable> stageVarTrue = List.of(StringNGVariable.builder()
                                                .name(CIStepInfoUtils.CI_INIT_REQUIRED_FIELDS_ONLY)
                                                .type(NGVariableType.STRING)
                                                .value(ParameterField.createValueField("true"))
                                                .build());
    List<NGVariable> stageVarFalse = List.of(StringNGVariable.builder()
                                                 .name(CIStepInfoUtils.CI_INIT_REQUIRED_FIELDS_ONLY)
                                                 .type(NGVariableType.STRING)
                                                 .value(ParameterField.createValueField("false"))
                                                 .build());

    assertThat(CIStepInfoUtils.isRequiredFieldsOnlyInitStripEnabled(true, List.of())).isTrue();
    assertThat(CIStepInfoUtils.isRequiredFieldsOnlyInitStripEnabled(true, stageVarFalse)).isTrue();
    assertThat(CIStepInfoUtils.isRequiredFieldsOnlyInitStripEnabled(false, stageVarTrue)).isTrue();
    assertThat(CIStepInfoUtils.isRequiredFieldsOnlyInitStripEnabled(false, stageVarFalse)).isFalse();
    assertThat(CIStepInfoUtils.isRequiredFieldsOnlyInitStripEnabled(false, null)).isFalse();
  }

  @Test
  @Owner(developers = SMCCONKEY)
  @Category(UnitTests.class)
  public void testGetImagePullPolicyReturnsStepLevelPolicyForIACMStep() {
    IACMTerraformPluginInfo step = IACMTerraformPluginInfo.builder().build();
    step.setImagePullPolicy(ParameterField.createValueField(ImagePullPolicy.ALWAYS));

    ParameterField<ImagePullPolicy> result = CIStepInfoUtils.getImagePullPolicy(step);

    assertThat(result).isNotNull();
    assertThat(result.getValue()).isEqualTo(ImagePullPolicy.ALWAYS);
  }

  @Test
  @Owner(developers = SMCCONKEY)
  @Category(UnitTests.class)
  public void testGetImagePullPolicyReturnsEmptyForIACMStepWhenUnset() {
    IACMTerraformPluginInfo step = IACMTerraformPluginInfo.builder().build();

    ParameterField<ImagePullPolicy> result = CIStepInfoUtils.getImagePullPolicy(step);

    assertThat(result).isNotNull();
    assertThat(result.getValue()).isNull();
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testEnrichImagePullErrorMessageAppendsGarHintForEcrRateLimit() {
    String errorMessage = "failed to pull image image=public.ecr.aws/harness/harness/drone-git:1.7.16 error=Error "
        + "response from daemon: toomanyrequests: Data limit exceeded";

    String result = CIStepInfoUtils.enrichImagePullErrorMessage(errorMessage);

    assertThat(result).startsWith(errorMessage);
    assertThat(result).contains("us-docker.pkg.dev/gar-prod-setup/harness-public");
    assertThat(result).contains("harnessImage");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testEnrichImagePullErrorMessageFiresForK8sKubeletPhrasings() {
    // kubelet/containerd ErrImagePull message phrasing (spaced "too many requests" and "429")
    String kubeletMsg =
        "ErrImagePull: failed to pull image \"public.ecr.aws/harness/harness/drone-git:1.7.16\": 429 Too Many Requests";
    String result = CIStepInfoUtils.enrichImagePullErrorMessage(kubeletMsg);
    assertThat(result).startsWith(kubeletMsg);
    assertThat(result).contains("us-docker.pkg.dev/gar-prod-setup/harness-public");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testEnrichImagePullErrorMessageLeavesK8sBackoffWithoutRateLimitSignalUntouched() {
    // ImagePullBackOff window: image ref present but no rate-limit signal -> do not mis-advise GAR switch
    String backoffMsg = "ImagePullBackOff: Back-off pulling image \"public.ecr.aws/harness/harness/drone-git:1.7.16\"";
    String result = CIStepInfoUtils.enrichImagePullErrorMessage(backoffMsg);
    assertThat(result).isEqualTo(backoffMsg);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testEnrichImagePullErrorMessageLeavesUnrelatedRateLimitUntouched() {
    // toomanyrequests but not on Public ECR (e.g. DockerHub) -> no ECR/GAR hint appended
    String errorMessage = "toomanyrequests: You have reached your pull rate limit from registry-1.docker.io";

    String result = CIStepInfoUtils.enrichImagePullErrorMessage(errorMessage);

    assertThat(result).isEqualTo(errorMessage);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testEnrichImagePullErrorMessageLeavesNonRateLimitEcrErrorUntouched() {
    String errorMessage = "failed to pull image public.ecr.aws/harness/harness/drone-git:1.7.16 error=manifest unknown";

    String result = CIStepInfoUtils.enrichImagePullErrorMessage(errorMessage);

    assertThat(result).isEqualTo(errorMessage);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testEnrichImagePullErrorMessageLeavesCustomerPublicEcrImageUntouched() {
    // A customer's own rate-limited Public ECR image (not under public.ecr.aws/harness/) must NOT be advised to
    // switch the harnessImage connector. K8s init aggregates errors across all build-pod containers, so this guards
    // against misfiring on user images. See CI-23169.
    String errorMessage = "failed to pull image image=public.ecr.aws/customer/repo:tag error=Error response from "
        + "daemon: toomanyrequests: Data limit exceeded";

    String result = CIStepInfoUtils.enrichImagePullErrorMessage(errorMessage);

    assertThat(result).isEqualTo(errorMessage);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testEnrichImagePullErrorMessageHandlesNullAndEmpty() {
    assertThat(CIStepInfoUtils.enrichImagePullErrorMessage(null)).isNull();
    assertThat(CIStepInfoUtils.enrichImagePullErrorMessage("")).isEmpty();
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.ABHAY;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SATYAKOTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.beans.yaml.extended.buildIntelligence.BuildIntelligence;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.serializer.SerializerUtils;
import io.harness.ci.execution.workloadidentity.WorkloadIdentitySerializerHelper;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.vm.steps.Binary;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.beans.ci.vm.steps.VmWorkloadIdentity;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.Map;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class VmRunStepSerializerTest extends CategoryTest {
  @Mock private ConnectorUtils connectorUtils;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private SerializerUtils serializerUtils;
  @Mock private VmStepSerializer vmStepSerializer;
  @Mock private VmBuildIntelligenceUtils vmBuildIntelligenceUtils;
  @Mock private WorkloadIdentitySerializerHelper workloadIdentitySerializerHelper;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;

  @InjectMocks private VmRunStepSerializer vmRunStepSerializer;
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                            "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                                        .build();
  private final StageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(workloadIdentitySerializerHelper.buildVmWorkloadIdentities(any(), any(), any(), anyLong()))
        .thenReturn(Collections.emptyList());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testRunStepSerialize() {
    RunStepInfo runStepInfo =
        RunStepInfo.builder()
            .image(ParameterField.createValueField("image"))
            .command(ParameterField.createValueField("echo hello"))
            .privileged(ParameterField.createValueField(true))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .runAsUser(ParameterField.createValueField(1000))
            .outputVariables(ParameterField.createValueField(Collections.emptyList()))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(
                "key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2"))))
            .build();
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), eq(false)))
        .thenReturn("image");
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, null, null);
    assertThat(vmRunStep.isPrivileged()).isTrue();
    assertThat(vmRunStep.getImage()).isEqualTo("image");
    assertThat(vmRunStep.getCommand()).isEqualTo("set -e; echo hello");
    assertThat(vmRunStep.getRunAsUser()).isEqualTo("1000");
    assertThat(vmRunStep.getEnvVariables()).isEqualTo(Map.of("key1", "val1", "key2", "val2"));
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testRunStepSerializePopulatesWorkloadIdentities() {
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("image"))
                                  .command(ParameterField.createValueField("echo hello"))
                                  .connectorRef(ParameterField.createValueField("connectorRef"))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), eq(false)))
        .thenReturn("image");
    when(ciExecutionServiceConfig.getHarnessIdTokenGenerateUrl()).thenReturn("https://harnessid/token/generate");
    when(workloadIdentitySerializerHelper.buildVmWorkloadIdentities(any(), any(), any(), anyLong()))
        .thenReturn(Collections.singletonList(VmWorkloadIdentity.builder()
                                                  .name("AWS_ID_TOKEN")
                                                  .workloadToken("wtok")
                                                  .audience("https://sts.amazonaws.com")
                                                  .tokenMode("STANDARD")
                                                  .build()));

    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, null, null);

    assertThat(vmRunStep.getWorkloadIdentities()).hasSize(1);
    assertThat(vmRunStep.getWorkloadIdentities().get(0).getName()).isEqualTo("AWS_ID_TOKEN");
    assertThat(vmRunStep.getWorkloadIdentities().get(0).getWorkloadToken()).isEqualTo("wtok");
    assertThat(vmRunStep.getWorkloadIdentityTokenGenerateUrl()).isEqualTo("https://harnessid/token/generate");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testRunStepSerializeEmptyCommand() {
    RunStepInfo runStepInfo =
        RunStepInfo.builder()
            .image(ParameterField.createValueField("image"))
            .privileged(ParameterField.createValueField(true))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .runAsUser(ParameterField.createValueField(1000))
            .outputVariables(ParameterField.createValueField(Collections.emptyList()))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(
                "key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2"))))
            .build();
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), eq(false)))
        .thenReturn("image");
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, null, null);
    assertThat(vmRunStep.isPrivileged()).isTrue();
    assertThat(vmRunStep.getImage()).isEqualTo("image");
    assertThat(vmRunStep.getCommand()).isNull();
    assertThat(vmRunStep.getEntrypoint()).isNull();
    assertThat(vmRunStep.getRunAsUser()).isEqualTo("1000");
    assertThat(vmRunStep.getEnvVariables()).isEqualTo(Map.of("key1", "val1", "key2", "val2"));
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testRunStepSerializeEmptyCommandWithShell() {
    RunStepInfo runStepInfo =
        RunStepInfo.builder()
            .image(ParameterField.createValueField("image"))
            .privileged(ParameterField.createValueField(true))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .runAsUser(ParameterField.createValueField(1000))
            .shell(ParameterField.<CIShellType>builder().value(CIShellType.SH).build())
            .outputVariables(ParameterField.createValueField(Collections.emptyList()))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(
                "key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2"))))
            .build();
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), eq(false)))
        .thenReturn("image");
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, null, null);
    assertThat(vmRunStep.isPrivileged()).isTrue();
    assertThat(vmRunStep.getImage()).isEqualTo("image");
    assertThat(vmRunStep.getCommand()).isNull();
    assertThat(vmRunStep.getEntrypoint()).isNull();
    assertThat(vmRunStep.getRunAsUser()).isEqualTo("1000");
    assertThat(vmRunStep.getEnvVariables()).isEqualTo(Map.of("key1", "val1", "key2", "val2"));
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testRunStepSerializeEmptyCommandImage() {
    RunStepInfo runStepInfo =
        RunStepInfo.builder()
            .privileged(ParameterField.createValueField(true))
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .runAsUser(ParameterField.createValueField(1000))
            .outputVariables(ParameterField.createValueField(Collections.emptyList()))
            .reports(ParameterField.createValueField(null))
            .envVariables(ParameterField.createValueField(Map.of(
                "key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2"))))
            .build();

    assertThatThrownBy(
        () -> vmRunStepSerializer.serialize(runStepInfo, ambiance, "id", null, null, null, null, null, null, null))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessage("Command and/or image must have a value.");
  }

  /**
   * Test: Binary is created for DOCKER infrastructure with Build Intelligence enabled
   * Scenario: When infrastructure is DOCKER and BI is enabled, binary should be created
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBinaryCreatedForDockerWithBuildIntelligenceEnabled() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("echo hello"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = DockerInfraYaml.builder().type(Infrastructure.Type.DOCKER).build();

    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().enabled(ParameterField.createValueField(true)).build();

    StageDetails stageDetails = StageDetails.builder().buildIntelligence(buildIntelligence).build();

    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    Binary mockBinary =
        Binary.builder()
            .name("auto-injection")
            .source(java.util.Arrays.asList("https://app.harness.io/storage/harness-download/harness-ti/auto-injection/"
                + "{{ release }}/linux/amd64/auto-injection"))
            .compressed(false)
            .version("1.0.11")
            .build();
    when(vmBuildIntelligenceUtils.getAutoInjectionBinary(any())).thenReturn(mockBinary);

    // Act
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, stageDetails);

    // Assert
    assertThat(vmRunStep.getPlugin()).isNotNull();
    assertThat(vmRunStep.getPlugin().getBinary()).isNotNull();
    assertThat(vmRunStep.getPlugin().getBinary().getName()).isEqualTo("auto-injection");
    assertThat(vmRunStep.getPlugin().getBinary().getSource()).hasSize(1);
    assertThat(vmRunStep.getPlugin().getBinary().getSource().get(0)).contains("auto-injection");
    assertThat(vmRunStep.getPlugin().getBinary().isCompressed()).isFalse();
    assertThat(vmRunStep.getPlugin().getBinary().getVersion()).isEqualTo("1.0.11");
  }

  /**
   * Test: Binary is NOT created for DOCKER infrastructure with Build Intelligence disabled
   * Scenario: When infrastructure is DOCKER but BI is disabled, binary should NOT be created
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBinaryNotCreatedForDockerWithBuildIntelligenceDisabled() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("echo hello"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = DockerInfraYaml.builder().type(Infrastructure.Type.DOCKER).build();

    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().enabled(ParameterField.createValueField(false)).build();

    StageDetails stageDetails = StageDetails.builder().buildIntelligence(buildIntelligence).build();

    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    // Act
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, stageDetails);

    // Assert
    assertThat(vmRunStep.getPlugin()).isNull();
  }

  /**
   * Test: Binary is NOT created for VM infrastructure even with BI enabled
   * Scenario: When infrastructure is VM, binary should NOT be created regardless of BI setting
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBinaryNotCreatedForVMInfrastructure() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("echo hello"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = VmInfraYaml.builder().type(Infrastructure.Type.VM).build();

    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().enabled(ParameterField.createValueField(true)).build();

    StageDetails stageDetails = StageDetails.builder().buildIntelligence(buildIntelligence).build();

    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    // Act
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, stageDetails);

    // Assert
    assertThat(vmRunStep.getPlugin()).isNull();
  }

  /**
   * Test: Binary is NOT created for DLITE_VM infrastructure even with BI enabled
   * Scenario: When infrastructure is DLITE_VM, binary should NOT be created regardless of BI setting
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBinaryNotCreatedForDliteVMInfrastructure() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("echo hello"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = HostedVmInfraYaml.builder().type(Infrastructure.Type.HOSTED_VM).build();

    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().enabled(ParameterField.createValueField(true)).build();

    StageDetails stageDetails = StageDetails.builder().buildIntelligence(buildIntelligence).build();

    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    // Act
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, stageDetails);

    // Assert
    assertThat(vmRunStep.getPlugin()).isNull();
  }

  /**
   * Test: Binary is NOT created for K8 infrastructure
   * Scenario: When infrastructure is KUBERNETES_DIRECT, binary should NOT be created
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBinaryNotCreatedForK8Infrastructure() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("echo hello"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).build();

    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().enabled(ParameterField.createValueField(true)).build();

    StageDetails stageDetails = StageDetails.builder().buildIntelligence(buildIntelligence).build();

    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    // Act
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, stageDetails);

    // Assert
    assertThat(vmRunStep.getPlugin()).isNull();
  }

  /**
   * Test: Binary creation handles null StageDetails gracefully
   * Scenario: When StageDetails is null, should not throw exception and no binary created
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBinaryCreationWithNullStageDetails() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("echo hello"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = DockerInfraYaml.builder().type(Infrastructure.Type.DOCKER).build();

    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    // Act - passing null stageDetails but valid stageInfraDetails
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, null);

    // Assert - Should not throw exception
    assertThat(vmRunStep).isNotNull();
    assertThat(vmRunStep.getPlugin()).isNull();
  }

  /**
   * Test: Binary creation handles null BuildIntelligence gracefully
   * Scenario: When BuildIntelligence is null in StageDetails, should not throw exception
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBinaryCreationWithNullBuildIntelligence() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("echo hello"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = DockerInfraYaml.builder().type(Infrastructure.Type.DOCKER).build();

    StageDetails stageDetails = StageDetails.builder().buildIntelligence(null).build();

    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    // Act
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, stageDetails);

    // Assert - Should not throw exception
    assertThat(vmRunStep).isNotNull();
    assertThat(vmRunStep.getPlugin()).isNull();
  }

  /**
   * Test: Binary creation handles null infrastructure gracefully
   * Scenario: When infrastructure is null, should not throw exception
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBinaryCreationWithNullInfrastructure() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("echo hello"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().enabled(ParameterField.createValueField(true)).build();

    StageDetails stageDetails = StageDetails.builder().buildIntelligence(buildIntelligence).build();

    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    // Act - passing null infrastructure but valid stageInfraDetails
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, null, stageDetails);

    // Assert - Should not throw exception
    assertThat(vmRunStep).isNotNull();
    assertThat(vmRunStep.getPlugin()).isNull();
  }

  /**
   * Test: Maven build cache config with containerless image and no existing args
   * Scenario: When image is empty and no existing MAVEN_ARGS, should set containerless config
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testMavenBuildCacheConfigContainerlessImageNoExistingArgs() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField(""))
                                  .command(ParameterField.createValueField("mvn clean install"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = HostedVmInfraYaml.builder().type(Infrastructure.Type.HOSTED_VM).build();

    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().enabled(ParameterField.createValueField(true)).build();

    StageDetails stageDetails = StageDetails.builder().buildIntelligence(buildIntelligence).build();

    when(vmStepSerializer.isBuildIntelligenceEnabled(any(), any(), any())).thenReturn(true);
    when(vmStepSerializer.injectAutoInjectionBinariesToCommand(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn("mvn clean install");
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("");

    // Act
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, stageDetails);

    // Assert
    assertThat(vmRunStep.getEnvVariables()).containsKey("MAVEN_ARGS");
    assertThat(vmRunStep.getEnvVariables().get("MAVEN_ARGS"))
        .isEqualTo("-Dmaven.build.cache.configPath=.mvn/maven-build-cache-containerless-config.xml");
  }

  /**
   * Test: Maven build cache config with container image and no existing args
   * Scenario: When image is provided and no existing MAVEN_ARGS, should set container config
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testMavenBuildCacheConfigContainerImageNoExistingArgs() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("mvn clean install"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = HostedVmInfraYaml.builder().type(Infrastructure.Type.HOSTED_VM).build();

    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().enabled(ParameterField.createValueField(true)).build();

    StageDetails stageDetails = StageDetails.builder().buildIntelligence(buildIntelligence).build();

    when(vmStepSerializer.isBuildIntelligenceEnabled(any(), any(), any())).thenReturn(true);
    when(vmStepSerializer.injectAutoInjectionBinariesToCommand(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn("mvn clean install");
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    // Act
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, stageDetails);

    // Assert
    assertThat(vmRunStep.getEnvVariables()).containsKey("MAVEN_ARGS");
    assertThat(vmRunStep.getEnvVariables().get("MAVEN_ARGS"))
        .isEqualTo("-Dmaven.build.cache.configPath=.mvn/maven-build-cache-container-config.xml");
  }

  /**
   * Test: Maven build cache config concatenates with existing args
   * Scenario: When MAVEN_ARGS already exist, should prepend cache config to existing args
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testMavenBuildCacheConfigConcatenatesWithExistingArgs() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("mvn clean install"))
                                  .envVariables(ParameterField.createValueField(
                                      Map.of("MAVEN_ARGS", ParameterField.createValueField("-X -DskipTests"))))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = HostedVmInfraYaml.builder().type(Infrastructure.Type.HOSTED_VM).build();

    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().enabled(ParameterField.createValueField(true)).build();

    StageDetails stageDetails = StageDetails.builder().buildIntelligence(buildIntelligence).build();

    when(vmStepSerializer.isBuildIntelligenceEnabled(any(), any(), any())).thenReturn(true);
    when(vmStepSerializer.injectAutoInjectionBinariesToCommand(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn("mvn clean install");
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    // Act
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, stageDetails);

    // Assert
    assertThat(vmRunStep.getEnvVariables()).containsKey("MAVEN_ARGS");
    assertThat(vmRunStep.getEnvVariables().get("MAVEN_ARGS"))
        .isEqualTo("-Dmaven.build.cache.configPath=.mvn/maven-build-cache-container-config.xml -X -DskipTests");
  }

  /**
   * Test: Cache proxy port defaults to 8082 when null
   * Scenario: When cacheProxyPort is null in StageDetails, should default to 8082
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testCacheProxyPortDefaultValue() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("mvn clean install"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = HostedVmInfraYaml.builder().type(Infrastructure.Type.HOSTED_VM).build();

    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().enabled(ParameterField.createValueField(true)).build();

    StageDetails stageDetails =
        StageDetails.builder().buildIntelligence(buildIntelligence).cacheProxyPort(null).build();

    when(vmStepSerializer.isBuildIntelligenceEnabled(any(), any(), any())).thenReturn(true);
    when(vmStepSerializer.injectAutoInjectionBinariesToCommand(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn("mvn clean install");
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    // Act
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, stageDetails);

    // Assert
    assertThat(vmRunStep.getEnvVariables()).containsKey("HARNESS_BUILD_CACHE_PORT");
    assertThat(vmRunStep.getEnvVariables().get("HARNESS_BUILD_CACHE_PORT")).isEqualTo("8082");
  }

  /**
   * Test: Cache proxy port uses custom value when provided
   * Scenario: When cacheProxyPort is set in StageDetails, should use that value
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testCacheProxyPortCustomValue() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("mvn clean install"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = HostedVmInfraYaml.builder().type(Infrastructure.Type.HOSTED_VM).build();

    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().enabled(ParameterField.createValueField(true)).build();

    StageDetails stageDetails =
        StageDetails.builder().buildIntelligence(buildIntelligence).cacheProxyPort("9090").build();

    when(vmStepSerializer.isBuildIntelligenceEnabled(any(), any(), any())).thenReturn(true);
    when(vmStepSerializer.injectAutoInjectionBinariesToCommand(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn("mvn clean install");
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    // Act
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, stageDetails);

    // Assert
    assertThat(vmRunStep.getEnvVariables()).containsKey("HARNESS_BUILD_CACHE_PORT");
    assertThat(vmRunStep.getEnvVariables().get("HARNESS_BUILD_CACHE_PORT")).isEqualTo("9090");
  }

  /**
   * Test: Maven args and cache port are both set for HOSTED_VM
   * Scenario: When HOSTED_VM infrastructure with BI enabled, both MAVEN_ARGS and HARNESS_BUILD_CACHE_PORT should be set
   */
  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testMavenArgsAndCachePortBothSetForHostedVm() {
    // Arrange
    RunStepInfo runStepInfo = RunStepInfo.builder()
                                  .image(ParameterField.createValueField("ubuntu:20.04"))
                                  .command(ParameterField.createValueField("mvn clean install"))
                                  .envVariables(ParameterField.createValueField(Collections.emptyMap()))
                                  .outputVariables(ParameterField.createValueField(Collections.emptyList()))
                                  .reports(ParameterField.createValueField(null))
                                  .build();

    Infrastructure infrastructure = HostedVmInfraYaml.builder().type(Infrastructure.Type.HOSTED_VM).build();

    BuildIntelligence buildIntelligence =
        BuildIntelligence.builder().enabled(ParameterField.createValueField(true)).build();

    StageDetails stageDetails =
        StageDetails.builder().buildIntelligence(buildIntelligence).cacheProxyPort("8080").build();

    when(vmStepSerializer.isBuildIntelligenceEnabled(any(), any(), any())).thenReturn(true);
    when(vmStepSerializer.injectAutoInjectionBinariesToCommand(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn("mvn clean install");
    when(serializerUtils.checkAndGetFullyQualifiedName(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn("ubuntu:20.04");

    // Act
    VmRunStep vmRunStep = vmRunStepSerializer.serialize(
        runStepInfo, ambiance, "id", null, null, null, null, stageInfraDetails, infrastructure, stageDetails);

    // Assert
    assertThat(vmRunStep.getEnvVariables()).containsKeys("MAVEN_ARGS", "HARNESS_BUILD_CACHE_PORT");
    assertThat(vmRunStep.getEnvVariables().get("MAVEN_ARGS"))
        .isEqualTo("-Dmaven.build.cache.configPath=.mvn/maven-build-cache-container-config.xml");
    assertThat(vmRunStep.getEnvVariables().get("HARNESS_BUILD_CACHE_PORT")).isEqualTo("8080");
  }
}

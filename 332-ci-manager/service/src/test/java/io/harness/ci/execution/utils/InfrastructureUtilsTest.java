/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.GARGI;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.TAPAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
import io.harness.beans.yaml.extended.runtime.CloudRuntime;
import io.harness.beans.yaml.extended.runtime.CloudRuntime.CloudRuntimeSpec;
import io.harness.beans.yaml.extended.runtime.DockerRuntime;
import io.harness.beans.yaml.extended.runtime.DockerRuntime.DockerRuntimeSpec;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class InfrastructureUtilsTest {
  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorFork8Direct() {
    String connectorRefValue = "docker";
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder()
                      .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                      .build())
            .build();
    Optional<ParameterField<String>> optionalHarnessImageConnector =
        InfrastructureUtils.getHarnessImageConnector(infrastructure);
    assertThat(true).isEqualTo(optionalHarnessImageConnector.isPresent());
    assertThat(connectorRefValue).isEqualTo(optionalHarnessImageConnector.get().getValue());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorForVM() {
    String connectorRefValue = "docker";
    Infrastructure infrastructure =
        VmInfraYaml.builder()
            .spec(VmPoolYaml.builder()
                      .spec(VmPoolYamlSpec.builder()
                                .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                                .build())
                      .build())
            .build();
    Optional<ParameterField<String>> optionalHarnessImageConnector =
        InfrastructureUtils.getHarnessImageConnector(infrastructure);
    assertThat(true).isEqualTo(optionalHarnessImageConnector.isPresent());
    assertThat(connectorRefValue).isEqualTo(optionalHarnessImageConnector.get().getValue());
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorDockerRuntime() {
    String runtimeConnector = "runtimeConn";

    Infrastructure infrastructure =
        io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.builder()
            .spec(io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.DockerInfraSpec.builder().build())
            .build();
    DockerRuntime runtime = DockerRuntime.builder()
                                .spec(DockerRuntimeSpec.builder()
                                          .harnessImageConnectorRef(ParameterField.createValueField(runtimeConnector))
                                          .build())
                                .build();
    Optional<ParameterField<String>> resolved = InfrastructureUtils.getHarnessImageConnector(infrastructure, runtime);
    assertThat(resolved).isPresent();
    assertThat(resolved.get().getValue()).isEqualTo(runtimeConnector);
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorHandlesNulls() {
    Infrastructure infrastructure =
        io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.builder()
            .spec(io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.DockerInfraSpec.builder().build())
            .build();
    Optional<ParameterField<String>> resolved = InfrastructureUtils.getHarnessImageConnector(infrastructure, null);
    assertThat(resolved).isNotPresent();
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorK8sDirect() {
    String connectorRefValue = "k8s-connector";
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder()
                      .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                      .build())
            .build();

    Optional<ParameterField<String>> singleParamResult = InfrastructureUtils.getHarnessImageConnector(infrastructure);
    assertThat(singleParamResult).isPresent();
    assertThat(singleParamResult.get().getValue()).isEqualTo(connectorRefValue);

    Optional<ParameterField<String>> twoParamResult =
        InfrastructureUtils.getHarnessImageConnector(infrastructure, null);
    assertThat(twoParamResult).isPresent();
    assertThat(twoParamResult.get().getValue()).isEqualTo(connectorRefValue);

    assertThat(singleParamResult.get().getValue()).isEqualTo(twoParamResult.get().getValue());
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorVM() {
    String connectorRefValue = "vm-connector";
    Infrastructure infrastructure =
        VmInfraYaml.builder()
            .spec(VmPoolYaml.builder()
                      .spec(VmPoolYamlSpec.builder()
                                .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                                .build())
                      .build())
            .build();

    Optional<ParameterField<String>> singleParamResult = InfrastructureUtils.getHarnessImageConnector(infrastructure);
    assertThat(singleParamResult).isPresent();
    assertThat(singleParamResult.get().getValue()).isEqualTo(connectorRefValue);

    Optional<ParameterField<String>> twoParamResult =
        InfrastructureUtils.getHarnessImageConnector(infrastructure, null);
    assertThat(twoParamResult).isPresent();
    assertThat(twoParamResult.get().getValue()).isEqualTo(connectorRefValue);

    assertThat(singleParamResult.get().getValue()).isEqualTo(twoParamResult.get().getValue());
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testRuntimeWithNullSpec() {
    Infrastructure infrastructure =
        io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.builder()
            .spec(io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.DockerInfraSpec.builder().build())
            .build();

    DockerRuntime runtime = DockerRuntime.builder().spec(null).build();

    Optional<ParameterField<String>> result = InfrastructureUtils.getHarnessImageConnector(infrastructure, runtime);
    assertThat(result).isNotPresent();
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorK8sInfraWithConnectorNoRuntime() {
    String connectorRefValue = "k8s-connector";
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder()
                      .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                      .build())
            .build();

    Optional<ParameterField<String>> result = InfrastructureUtils.getHarnessImageConnector(infrastructure, null);
    assertThat(result).isPresent();
    assertThat(result.get().getValue()).isEqualTo(connectorRefValue);
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorK8sInfraWithoutConnectorNoRuntime() {
    Infrastructure infrastructure = K8sDirectInfraYaml.builder()
                                        .spec(K8sDirectInfraYamlSpec.builder().harnessImageConnectorRef(null).build())
                                        .build();

    Optional<ParameterField<String>> result = InfrastructureUtils.getHarnessImageConnector(infrastructure, null);
    assertThat(result).isNotPresent();
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorVmInfraWithConnectorNoRuntime() {
    String connectorRefValue = "vm-connector";
    Infrastructure infrastructure =
        VmInfraYaml.builder()
            .spec(VmPoolYaml.builder()
                      .spec(VmPoolYamlSpec.builder()
                                .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                                .build())
                      .build())
            .build();

    Optional<ParameterField<String>> result = InfrastructureUtils.getHarnessImageConnector(infrastructure, null);
    assertThat(result).isPresent();
    assertThat(result.get().getValue()).isEqualTo(connectorRefValue);
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorVmInfraWithoutConnectorNoRuntime() {
    Infrastructure infrastructure =
        VmInfraYaml.builder()
            .spec(VmPoolYaml.builder().spec(VmPoolYamlSpec.builder().harnessImageConnectorRef(null).build()).build())
            .build();

    Optional<ParameterField<String>> result = InfrastructureUtils.getHarnessImageConnector(infrastructure, null);
    assertThat(result).isNotPresent();
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorNoDockerInfraOnlyRuntimeWithConnector() {
    String runtimeConnector = "runtime-docker-connector";

    DockerRuntime runtime = DockerRuntime.builder()
                                .spec(DockerRuntimeSpec.builder()
                                          .harnessImageConnectorRef(ParameterField.createValueField(runtimeConnector))
                                          .build())
                                .build();

    Optional<ParameterField<String>> result = InfrastructureUtils.getHarnessImageConnector(null, runtime);
    assertThat(result).isPresent();
    assertThat(result.get().getValue()).isEqualTo(runtimeConnector);
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorNoDockerInfraOnlyRuntimeWithoutConnector() {
    DockerRuntime runtime =
        DockerRuntime.builder().spec(DockerRuntimeSpec.builder().harnessImageConnectorRef(null).build()).build();

    Optional<ParameterField<String>> result = InfrastructureUtils.getHarnessImageConnector(null, runtime);
    assertThat(result).isNotPresent();
  }
  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorBothNullInfraAndRuntime() {
    Optional<ParameterField<String>> result = InfrastructureUtils.getHarnessImageConnector(null, null);
    assertThat(result).isNotPresent();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetRunAsUserFromVmInfra() {
    Integer runAsUserValue = 1000;
    Infrastructure infrastructure =
        VmInfraYaml.builder()
            .spec(VmPoolYaml.builder()
                      .spec(VmPoolYamlSpec.builder().runAsUser(ParameterField.createValueField(runAsUserValue)).build())
                      .build())
            .build();

    Optional<ParameterField<Integer>> result = InfrastructureUtils.getRunAsUser(infrastructure);
    assertThat(result).isPresent();
    assertThat(result.get().getValue()).isEqualTo(runAsUserValue);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetRunAsUserFromDockerRuntime() {
    Integer runAsUserValue = 1500;
    Infrastructure infrastructure =
        io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.builder()
            .spec(io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.DockerInfraSpec.builder().build())
            .build();
    DockerRuntime runtime =
        DockerRuntime.builder()
            .spec(DockerRuntimeSpec.builder().runAsUser(ParameterField.createValueField(runAsUserValue)).build())
            .build();

    Optional<ParameterField<Integer>> result = InfrastructureUtils.getRunAsUser(infrastructure, runtime);
    assertThat(result).isPresent();
    assertThat(result.get().getValue()).isEqualTo(runAsUserValue);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetRunAsUserFromCloudRuntime() {
    Integer runAsUserValue = 2000;
    Infrastructure infrastructure =
        io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.builder()
            .spec(io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.DockerInfraSpec.builder().build())
            .build();
    CloudRuntime runtime =
        CloudRuntime.builder()
            .spec(CloudRuntimeSpec.builder().runAsUser(ParameterField.createValueField(runAsUserValue)).build())
            .build();

    Optional<ParameterField<Integer>> result = InfrastructureUtils.getRunAsUser(infrastructure, runtime);
    assertThat(result).isPresent();
    assertThat(result.get().getValue()).isEqualTo(runAsUserValue);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetRunAsUserInfraOverridesRuntime() {
    Integer infraRunAsUser = 1000;
    Integer runtimeRunAsUser = 2000;
    Infrastructure infrastructure =
        VmInfraYaml.builder()
            .spec(VmPoolYaml.builder()
                      .spec(VmPoolYamlSpec.builder().runAsUser(ParameterField.createValueField(infraRunAsUser)).build())
                      .build())
            .build();
    DockerRuntime runtime =
        DockerRuntime.builder()
            .spec(DockerRuntimeSpec.builder().runAsUser(ParameterField.createValueField(runtimeRunAsUser)).build())
            .build();

    Optional<ParameterField<Integer>> result = InfrastructureUtils.getRunAsUser(infrastructure, runtime);
    assertThat(result).isPresent();
    assertThat(result.get().getValue()).isEqualTo(infraRunAsUser);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetRunAsUserBothNull() {
    Optional<ParameterField<Integer>> result = InfrastructureUtils.getRunAsUser(null, null);
    assertThat(result).isNotPresent();
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorDockerInfraWithEmbeddedRuntime() {
    String connectorRefValue = "override-connector";
    DockerRuntime embeddedRuntime =
        DockerRuntime.builder()
            .spec(DockerRuntimeSpec.builder()
                      .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                      .build())
            .build();
    Infrastructure infrastructure =
        io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.builder()
            .spec(io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.DockerInfraSpec.builder()
                      .runtime(ParameterField.createValueField(embeddedRuntime))
                      .build())
            .build();

    Optional<ParameterField<String>> result = InfrastructureUtils.getHarnessImageConnector(infrastructure, null);
    assertThat(result).isPresent();
    assertThat(result.get().getValue()).isEqualTo(connectorRefValue);
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorDockerInfraWithoutEmbeddedRuntime() {
    Infrastructure infrastructure =
        io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.builder()
            .spec(io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.DockerInfraSpec.builder().build())
            .build();

    Optional<ParameterField<String>> result = InfrastructureUtils.getHarnessImageConnector(infrastructure, null);
    assertThat(result).isNotPresent();
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorSingleArgDockerInfraReturnsEmpty() {
    String connectorRefValue = "override-connector";
    DockerRuntime embeddedRuntime =
        DockerRuntime.builder()
            .spec(DockerRuntimeSpec.builder()
                      .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                      .build())
            .build();
    Infrastructure infrastructure =
        io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.builder()
            .spec(io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.DockerInfraSpec.builder()
                      .runtime(ParameterField.createValueField(embeddedRuntime))
                      .build())
            .build();

    Optional<ParameterField<String>> result = InfrastructureUtils.getHarnessImageConnector(infrastructure);
    assertThat(result).isNotPresent();
  }
}

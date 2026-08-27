/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IDPBuildEnforcerImplTest extends CategoryTest {
  private IDPBuildEnforcerImpl idpBuildEnforcer;
  @Mock private QueueExecutionUtils queueExecutionUtils;
  private static final String accountID = "accountID";
  Boolean queueEnabled = true;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    idpBuildEnforcer = new IDPBuildEnforcerImpl(queueExecutionUtils, queueEnabled);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldQueueWhenGetEnableQueueIsFalse() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);
    when(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, false, ModuleType.IDP.name()))
        .thenReturn(false);
    assertThat(idpBuildEnforcer.shouldQueue(accountID, hostedVMInfrastructure, ModuleType.IDP.name())).isFalse();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldQueueWhenGetEnableQueueIsTrue() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);
    when(queueExecutionUtils.shouldQueue(accountID, hostedVMInfrastructure, true, ModuleType.IDP.name()))
        .thenReturn(true);
    assertThat(idpBuildEnforcer.shouldQueue(accountID, hostedVMInfrastructure, ModuleType.IDP.name())).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldRun() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.MacOS);
    when(queueExecutionUtils.shouldRun(accountID, hostedVMInfrastructure, ModuleType.IDP.name())).thenReturn(true);
    assertThat(idpBuildEnforcer.shouldRun(accountID, hostedVMInfrastructure, ModuleType.IDP.name())).isTrue();
  }

  private Infrastructure getHostedVMInfrastructure(OSType osType) {
    return HostedVmInfraYaml.builder()
        .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                  .platform(ParameterField.createValueField(
                      Platform.builder().os(ParameterField.createValueField(osType)).build()))
                  .build())
        .build();
  }
}

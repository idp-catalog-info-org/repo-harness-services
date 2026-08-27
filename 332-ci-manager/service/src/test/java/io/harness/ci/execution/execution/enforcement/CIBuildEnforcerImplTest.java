/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution.enforcement;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.category.element.UnitTests;
import io.harness.ci.enforcement.CIBuildEnforcerImpl;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CIBuildEnforcerImplTest extends CIExecutionTestBase {
  @InjectMocks private CIBuildEnforcerImpl ciBuildEnforcer;
  @Mock private QueueExecutionUtils queueExecutionUtils;
  private static final String accountID = "accountID";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldQueueWhenGetEnableQueueIsTrue() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.Linux);
    when(queueExecutionUtils.shouldQueue(
             eq(accountID), eq(hostedVMInfrastructure), eq(true), eq(ModuleType.CI.name()), any()))
        .thenReturn(true);
    assertThat(ciBuildEnforcer.shouldQueue(accountID, hostedVMInfrastructure, ModuleType.CI.name())).isTrue();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShouldRun() {
    Infrastructure hostedVMInfrastructure = getHostedVMInfrastructure(OSType.MacOS);
    when(queueExecutionUtils.shouldRun(eq(accountID), eq(hostedVMInfrastructure), eq(ModuleType.CI.name()), any()))
        .thenReturn(true);
    assertThat(ciBuildEnforcer.shouldRun(accountID, hostedVMInfrastructure, ModuleType.CI.name())).isTrue();
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

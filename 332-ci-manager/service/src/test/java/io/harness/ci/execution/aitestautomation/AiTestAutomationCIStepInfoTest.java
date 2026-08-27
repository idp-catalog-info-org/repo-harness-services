/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.aitestautomation;

import static io.harness.rule.OwnerRule.SARTHAK_DALMIA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.AiTestAutomationCIStepInfo;
import io.harness.beans.steps.stepinfo.AiTestAutomationCIStepParameters;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.AI)
public class AiTestAutomationCIStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testStepType() {
    assertThat(AiTestAutomationCIStepInfo.STEP_TYPE.getType())
        .isEqualTo(CIStepInfoType.AI_TEST_AUTOMATION.getDisplayName());
    assertThat(AiTestAutomationCIStepInfo.STEP_TYPE.getStepCategory()).isEqualTo(StepCategory.STEP);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testFacilitatorType() {
    AiTestAutomationCIStepInfo stepInfo = AiTestAutomationCIStepInfo.builder().build();
    assertThat(stepInfo.getFacilitatorType()).isEqualTo(OrchestrationFacilitatorType.ASYNC);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    AiTestAutomationCIStepInfo stepInfo = AiTestAutomationCIStepInfo.builder()
                                              .applicationName(ParameterField.createValueField("my-app"))
                                              .environmentName(ParameterField.createValueField("my-env"))
                                              .testSuiteName(ParameterField.createValueField("my-suite"))
                                              .testType(ParameterField.createValueField("playwright"))
                                              .buildId(ParameterField.createValueField("build-1"))
                                              .tunnelName(ParameterField.createValueField("tunnel-1"))
                                              .executionAliasId(ParameterField.createValueField("alias-1"))
                                              .configOverride(ParameterField.createValueField("{\"key\":\"val\"}"))
                                              .build();

    AiTestAutomationCIStepParameters params = (AiTestAutomationCIStepParameters) stepInfo.getSpecParameters();

    assertThat(params.getApplicationName().getValue()).isEqualTo("my-app");
    assertThat(params.getEnvironmentName().getValue()).isEqualTo("my-env");
    assertThat(params.getTestSuiteName().getValue()).isEqualTo("my-suite");
    assertThat(params.getTestType().getValue()).isEqualTo("playwright");
    assertThat(params.getBuildId().getValue()).isEqualTo("build-1");
    assertThat(params.getTunnelName().getValue()).isEqualTo("tunnel-1");
    assertThat(params.getExecutionAliasId().getValue()).isEqualTo("alias-1");
    assertThat(params.getConfigOverride().getValue()).isEqualTo("{\"key\":\"val\"}");
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testDefaultRetry() {
    AiTestAutomationCIStepInfo stepInfo = AiTestAutomationCIStepInfo.builder().build();
    assertThat(stepInfo.getRetry()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testNonYamlInfo() {
    AiTestAutomationCIStepInfo stepInfo = AiTestAutomationCIStepInfo.builder().build();
    assertThat(stepInfo.getNonYamlInfo().getStepInfoType()).isEqualTo(CIStepInfoType.AI_TEST_AUTOMATION);
  }

  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testSkipUnresolvedExpressionsCheck() {
    AiTestAutomationCIStepInfo stepInfo = AiTestAutomationCIStepInfo.builder().build();
    assertThat(stepInfo.skipUnresolvedExpressionsCheck()).isTrue();
  }
}

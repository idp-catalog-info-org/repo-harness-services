/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.rule.OwnerRule.HUMANSHU_ARORA;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.PIPELINE_TRIGGER_BY;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.PIPELINE_TRIGGER_TYPE;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.PLUGIN_ARTIFACT_TAG;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.PLUGIN_ARTIFACT_TYPE;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.PLUGIN_ARTIFACT_URL;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.PLUGIN_ARTIFACT_VARIANT_TYPE;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.PLUGIN_JUNIT_REPORT_PATH;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.PLUGIN_KEYLESS_TYPE;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.STAGE_EXECUTION_ID;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.STAGE_NAME;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.STAGE_TYPE;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.STEP_EXECUTION_ID;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.STEP_ID;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.STEP_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.ssca.beans.attestation.KeylessType;
import io.harness.ssca.beans.stepinfo.SscaJunitAttestationStepInfo;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class SscaJunitAttestationPluginHelperTest extends CIExecutionTestBase {
  @InjectMocks private SscaJunitAttestationPluginHelper sscaJunitAttestationPluginHelper;

  @Mock private SscaPluginUtils sscaPluginUtils;

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
    sscaJunitAttestationPluginHelper = new SscaJunitAttestationPluginHelper(sscaPluginUtils);
    when(sscaPluginUtils.getStageName(any())).thenReturn("stageName");
    when(sscaPluginUtils.getStageExecutionIdentifier(any())).thenReturn("stageExecId");
    when(sscaPluginUtils.getStageType(any())).thenReturn("CI");
    when(sscaPluginUtils.getPipelineTriggerType(any())).thenReturn("MANUAL");
    when(sscaPluginUtils.getPipelineTriggerBy(any())).thenReturn("user");
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetSscaJunitAttestationStepEnvVariables() {
    SscaJunitAttestationStepInfo stepInfo =
        SscaJunitAttestationStepInfo.builder()
            .name("junit-attest-step")
            .repoUrl(ParameterField.createValueField("https://github.com/org/repo"))
            .branch(ParameterField.createValueField("main"))
            .junitReportPath(ParameterField.createValueField("/path/to/junit-report.xml"))
            .oidcProvider(KeylessType.HARNESS)
            .build();

    Map<String, String> envMap =
        sscaJunitAttestationPluginHelper.getSscaJunitAttestationStepEnvVariables(stepInfo, "id1", ambiance);

    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap.get(PLUGIN_ARTIFACT_URL)).isEqualTo("https://github.com/org/repo");
    assertThat(envMap.get(PLUGIN_ARTIFACT_TYPE)).isEqualTo("repository");
    assertThat(envMap.get(PLUGIN_ARTIFACT_VARIANT_TYPE)).isEqualTo("branch");
    assertThat(envMap.get(PLUGIN_ARTIFACT_TAG)).isEqualTo("main");
    assertThat(envMap.get(PLUGIN_JUNIT_REPORT_PATH)).isEqualTo("/path/to/junit-report.xml");
    assertThat(envMap.get(PLUGIN_KEYLESS_TYPE)).isEqualTo(KeylessType.HARNESS.toString());
    assertThat(envMap.get(STEP_EXECUTION_ID)).isEqualTo("runtimeID");
    assertThat(envMap.get(STEP_ID)).isEqualTo("id1");
    assertThat(envMap.get(STEP_NAME)).isEqualTo("junit-attest-step");
    assertThat(envMap.get(STAGE_NAME)).isEqualTo("stageName");
    assertThat(envMap.get(STAGE_EXECUTION_ID)).isEqualTo("stageExecId");
    assertThat(envMap.get(STAGE_TYPE)).isEqualTo("CI");
    assertThat(envMap.get(PIPELINE_TRIGGER_TYPE)).isEqualTo("MANUAL");
    assertThat(envMap.get(PIPELINE_TRIGGER_BY)).isEqualTo("user");
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetSscaJunitAttestationStepEnvVariables_defaultStepName() {
    SscaJunitAttestationStepInfo stepInfo =
        SscaJunitAttestationStepInfo.builder()
            .repoUrl(ParameterField.createValueField("https://github.com/org/repo"))
            .branch(ParameterField.createValueField("develop"))
            .junitReportPath(ParameterField.createValueField("/reports/test-results.xml"))
            .oidcProvider(KeylessType.HARNESS)
            .build();

    Map<String, String> envMap =
        sscaJunitAttestationPluginHelper.getSscaJunitAttestationStepEnvVariables(stepInfo, "id2", ambiance);

    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap.get(STEP_NAME)).isEqualTo("");
    assertThat(envMap.get(PLUGIN_JUNIT_REPORT_PATH)).isEqualTo("/reports/test-results.xml");
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetSscaJunitAttestationStepEnvVariables_noNullValues() {
    SscaJunitAttestationStepInfo stepInfo = SscaJunitAttestationStepInfo.builder()
                                                .repoUrl(ParameterField.createValueField("https://github.com/org/repo"))
                                                .branch(ParameterField.createValueField("main"))
                                                .junitReportPath(ParameterField.createValueField("/path/to/report.xml"))
                                                .oidcProvider(KeylessType.HARNESS)
                                                .build();

    Map<String, String> envMap =
        sscaJunitAttestationPluginHelper.getSscaJunitAttestationStepEnvVariables(stepInfo, "id3", ambiance);

    assertThat(envMap.values()).doesNotContainNull();
  }
}

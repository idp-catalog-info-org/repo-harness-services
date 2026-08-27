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
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.PLUGIN_KEYLESS_TYPE;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.PLUGIN_PR_NUMBER;
import static io.harness.ssca.execution.attestation.SscaAttestationStepPluginUtils.SOURCE_PLATFORM;
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
import io.harness.ci.utils.BaseConnectorUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.ssca.beans.attestation.KeylessType;
import io.harness.ssca.beans.source.SourcePlatform;
import io.harness.ssca.beans.stepinfo.SscaPrAttestationStepInfo;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class SscaPrAttestationPluginHelperTest extends CIExecutionTestBase {
  @InjectMocks private SscaPrAttestationPluginHelper sscaPrAttestationPluginHelper;

  @Mock private SscaPluginUtils sscaPluginUtils;
  @Mock private BaseConnectorUtils baseConnectorUtils;

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
    sscaPrAttestationPluginHelper = new SscaPrAttestationPluginHelper(sscaPluginUtils, baseConnectorUtils);
    when(sscaPluginUtils.getStageName(any())).thenReturn("stageName");
    when(sscaPluginUtils.getStageExecutionIdentifier(any())).thenReturn("stageExecId");
    when(sscaPluginUtils.getStageType(any())).thenReturn("CI");
    when(sscaPluginUtils.getPipelineTriggerType(any())).thenReturn("MANUAL");
    when(sscaPluginUtils.getPipelineTriggerBy(any())).thenReturn("user");
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetSscaPrAttestationStepEnvVariables() {
    SscaPrAttestationStepInfo stepInfo = SscaPrAttestationStepInfo.builder()
                                             .name("pr-attest-step")
                                             .repoUrl(ParameterField.createValueField("https://github.com/org/repo"))
                                             .branch(ParameterField.createValueField("main"))
                                             .prNumber(ParameterField.createValueField("42"))
                                             .oidcProvider(KeylessType.HARNESS)
                                             .sourcePlatform(SourcePlatform.GITHUB)
                                             .build();

    Map<String, String> envMap =
        sscaPrAttestationPluginHelper.getSscaPrAttestationStepEnvVariables(stepInfo, "id1", ambiance);

    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap.get(PLUGIN_ARTIFACT_URL)).isEqualTo("https://github.com/org/repo");
    assertThat(envMap.get(PLUGIN_ARTIFACT_TYPE)).isEqualTo("repository");
    assertThat(envMap.get(PLUGIN_ARTIFACT_VARIANT_TYPE)).isEqualTo("branch");
    assertThat(envMap.get(PLUGIN_ARTIFACT_TAG)).isEqualTo("main");
    assertThat(envMap.get(PLUGIN_PR_NUMBER)).isEqualTo("42");
    assertThat(envMap.get(SOURCE_PLATFORM)).isEqualTo("GITHUB");
    assertThat(envMap.get(PLUGIN_KEYLESS_TYPE)).isEqualTo(KeylessType.HARNESS.toString());
    assertThat(envMap.get(STEP_EXECUTION_ID)).isEqualTo("runtimeID");
    assertThat(envMap.get(STEP_ID)).isEqualTo("id1");
    assertThat(envMap.get(STEP_NAME)).isEqualTo("pr-attest-step");
    assertThat(envMap.get(STAGE_NAME)).isEqualTo("stageName");
    assertThat(envMap.get(STAGE_EXECUTION_ID)).isEqualTo("stageExecId");
    assertThat(envMap.get(STAGE_TYPE)).isEqualTo("CI");
    assertThat(envMap.get(PIPELINE_TRIGGER_TYPE)).isEqualTo("MANUAL");
    assertThat(envMap.get(PIPELINE_TRIGGER_BY)).isEqualTo("user");
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetSscaPrAttestationStepEnvVariables_withoutPrNumber() {
    SscaPrAttestationStepInfo stepInfo = SscaPrAttestationStepInfo.builder()
                                             .repoUrl(ParameterField.createValueField("https://github.com/org/repo"))
                                             .branch(ParameterField.createValueField("develop"))
                                             .oidcProvider(KeylessType.HARNESS)
                                             .sourcePlatform(SourcePlatform.GITLAB)
                                             .build();

    Map<String, String> envMap =
        sscaPrAttestationPluginHelper.getSscaPrAttestationStepEnvVariables(stepInfo, "id2", ambiance);

    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap.get(PLUGIN_ARTIFACT_URL)).isEqualTo("https://github.com/org/repo");
    assertThat(envMap.get(PLUGIN_ARTIFACT_TAG)).isEqualTo("develop");
    assertThat(envMap.get(SOURCE_PLATFORM)).isEqualTo("GITLAB");
    assertThat(envMap.get(STEP_NAME)).isEqualTo("");
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetSscaPrAttestationStepEnvVariables_withAllFieldsSet_noNullValues() {
    SscaPrAttestationStepInfo stepInfo = SscaPrAttestationStepInfo.builder()
                                             .repoUrl(ParameterField.createValueField("https://github.com/org/repo"))
                                             .branch(ParameterField.createValueField("main"))
                                             .prNumber(ParameterField.createValueField("10"))
                                             .oidcProvider(KeylessType.HARNESS)
                                             .sourcePlatform(SourcePlatform.GITHUB)
                                             .build();

    Map<String, String> envMap =
        sscaPrAttestationPluginHelper.getSscaPrAttestationStepEnvVariables(stepInfo, "id3", ambiance);

    assertThat(envMap.values()).doesNotContainNull();
  }
}

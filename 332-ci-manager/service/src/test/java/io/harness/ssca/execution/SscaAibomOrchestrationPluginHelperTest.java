/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.rule.OwnerRule.HUMANSHU_ARORA;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PIPELINE_TRIGGER_BY;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PIPELINE_TRIGGER_BY_EMAIL;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PIPELINE_TRIGGER_TYPE;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PLUGIN_AIBOMDESTINATION;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PLUGIN_AIBOMSOURCE_TYPE;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PLUGIN_CLI_FLAGS;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PLUGIN_FORMAT;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PLUGIN_MODE;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PLUGIN_REPO_AIBOMSOURCE_CLONED_CODEBASE;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PLUGIN_REPO_AIBOMSOURCE_PATH;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PLUGIN_REPO_AIBOMSOURCE_URL;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PLUGIN_REPO_AIBOMSOURCE_VARIANT;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.PLUGIN_REPO_AIBOMSOURCE_VARIANT_TYPE;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.STAGE_EXECUTION_ID;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.STAGE_NAME;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.STAGE_TYPE;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.STEP_EXECUTION_ID;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.STEP_ID;
import static io.harness.ssca.execution.orchestration.SscaAibomOrchestrationStepPluginUtils.STEP_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.ssca.beans.source.RepoSbomVariantType;
import io.harness.ssca.beans.source.SscaAibomRepositorySource;
import io.harness.ssca.beans.source.SscaAibomSource;
import io.harness.ssca.beans.source.SscaAibomSourceType;
import io.harness.ssca.beans.stepinfo.SscaAibomOrchestrationStepInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class SscaAibomOrchestrationPluginHelperTest extends CIExecutionTestBase {
  @InjectMocks private SscaAibomOrchestrationPluginHelper sscaAibomOrchestrationPluginHelper;

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
    sscaAibomOrchestrationPluginHelper = new SscaAibomOrchestrationPluginHelper(sscaPluginUtils);
    when(sscaPluginUtils.getStageName(any())).thenReturn("stageName");
    when(sscaPluginUtils.getStageExecutionIdentifier(any())).thenReturn("stageExecId");
    when(sscaPluginUtils.getStageType(any())).thenReturn("CI");
    when(sscaPluginUtils.getPipelineTriggerType(any())).thenReturn("MANUAL");
    when(sscaPluginUtils.getPipelineTriggerBy(any())).thenReturn("user");
    when(sscaPluginUtils.getPipelineTriggerByEmail(any())).thenReturn("user@harness.io");
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetAibomOrchestrationStepEnvVariables_repoSource() {
    SscaAibomOrchestrationStepInfo stepInfo =
        SscaAibomOrchestrationStepInfo.builder()
            .name("aibom-step")
            .source(SscaAibomSource.builder()
                        .type(SscaAibomSourceType.REPOSITORY)
                        .sscaAibomSourceSpec(SscaAibomRepositorySource.builder()
                                                 .url(ParameterField.createValueField("https://github.com/org/repo"))
                                                 .path(ParameterField.createValueField("/src"))
                                                 .variant(ParameterField.createValueField("main"))
                                                 .variantType(RepoSbomVariantType.BRANCH)
                                                 .clonedCodebase(ParameterField.createValueField("/harness"))
                                                 .build())
                        .build())
            .format(ParameterField.createValueField("cyclonedx"))
            .additionalCliFlags(ParameterField.createValueField("--verbose"))
            .build();

    Map<String, String> envMap = sscaAibomOrchestrationPluginHelper.getSscaAibomOrchestrationStepEnvVariables(
        stepInfo, "id1", ambiance, Type.K8);

    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap.get(PLUGIN_AIBOMSOURCE_TYPE)).isEqualTo("repository");
    assertThat(envMap.get(PLUGIN_REPO_AIBOMSOURCE_URL)).isEqualTo("https://github.com/org/repo");
    assertThat(envMap.get(PLUGIN_REPO_AIBOMSOURCE_PATH)).isEqualTo("/src");
    assertThat(envMap.get(PLUGIN_REPO_AIBOMSOURCE_VARIANT)).isEqualTo("main");
    assertThat(envMap.get(PLUGIN_REPO_AIBOMSOURCE_VARIANT_TYPE)).isEqualTo(RepoSbomVariantType.BRANCH.toString());
    assertThat(envMap.get(PLUGIN_REPO_AIBOMSOURCE_CLONED_CODEBASE)).isEqualTo("/harness");
    assertThat(envMap.get(PLUGIN_AIBOMDESTINATION)).isEqualTo("harness/aibom");
    assertThat(envMap.get(PLUGIN_FORMAT)).isEqualTo("cyclonedx");
    assertThat(envMap.get(PLUGIN_CLI_FLAGS)).isEqualTo("--verbose");
    assertThat(envMap.get(PLUGIN_MODE)).isEqualTo("generation");
    assertThat(envMap.get(STEP_EXECUTION_ID)).isEqualTo("runtimeID");
    assertThat(envMap.get(STEP_ID)).isEqualTo("id1");
    assertThat(envMap.get(STEP_NAME)).isEqualTo("aibom-step");
    assertThat(envMap.get(STAGE_NAME)).isEqualTo("stageName");
    assertThat(envMap.get(STAGE_EXECUTION_ID)).isEqualTo("stageExecId");
    assertThat(envMap.get(STAGE_TYPE)).isEqualTo("CI");
    assertThat(envMap.get(PIPELINE_TRIGGER_TYPE)).isEqualTo("MANUAL");
    assertThat(envMap.get(PIPELINE_TRIGGER_BY)).isEqualTo("user");
    assertThat(envMap.get(PIPELINE_TRIGGER_BY_EMAIL)).isEqualTo("user@harness.io");
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetAibomOrchestrationStepEnvVariables_defaultClonedCodebase() {
    SscaAibomOrchestrationStepInfo stepInfo =
        SscaAibomOrchestrationStepInfo.builder()
            .source(SscaAibomSource.builder()
                        .type(SscaAibomSourceType.REPOSITORY)
                        .sscaAibomSourceSpec(SscaAibomRepositorySource.builder()
                                                 .url(ParameterField.createValueField("https://github.com/org/repo"))
                                                 .variant(ParameterField.createValueField("develop"))
                                                 .variantType(RepoSbomVariantType.BRANCH)
                                                 .build())
                        .build())
            .build();

    Map<String, String> envMap = sscaAibomOrchestrationPluginHelper.getSscaAibomOrchestrationStepEnvVariables(
        stepInfo, "id2", ambiance, Type.K8);

    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap.get(PLUGIN_REPO_AIBOMSOURCE_CLONED_CODEBASE)).isEqualTo("/harness");
    assertThat(envMap.get(PLUGIN_AIBOMDESTINATION)).isEqualTo("harness/aibom");
    assertThat(envMap.get(PLUGIN_MODE)).isEqualTo("generation");
    assertThat(envMap.get(STEP_NAME)).isEqualTo("");
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetAibomOrchestrationStepEnvVariables_noNullValuesInMap() {
    SscaAibomOrchestrationStepInfo stepInfo =
        SscaAibomOrchestrationStepInfo.builder()
            .source(SscaAibomSource.builder()
                        .type(SscaAibomSourceType.REPOSITORY)
                        .sscaAibomSourceSpec(SscaAibomRepositorySource.builder()
                                                 .url(ParameterField.createValueField("https://github.com/org/repo"))
                                                 .variant(ParameterField.createValueField("main"))
                                                 .variantType(RepoSbomVariantType.BRANCH)
                                                 .build())
                        .build())
            .build();

    Map<String, String> envMap = sscaAibomOrchestrationPluginHelper.getSscaAibomOrchestrationStepEnvVariables(
        stepInfo, "id3", ambiance, Type.K8);

    assertThat(envMap.values()).doesNotContainNull();
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetAibomOrchestrationStepEnvVariables_withScsSettings() {
    doCallRealMethod().when(sscaPluginUtils).addScsSettings(any(), anyString(), anyString(), any());

    Map<String, JsonNode> settingsMap = new HashMap<>();
    settingsMap.put("HARNESS_SSCA_SERVICE_ENDPOINT", new TextNode("https://custom-proxy.internal/ssca-manager/"));

    SscaAibomOrchestrationStepInfo stepInfo =
        SscaAibomOrchestrationStepInfo.builder()
            .source(SscaAibomSource.builder()
                        .type(SscaAibomSourceType.REPOSITORY)
                        .sscaAibomSourceSpec(SscaAibomRepositorySource.builder()
                                                 .url(ParameterField.createValueField("https://github.com/org/repo"))
                                                 .variant(ParameterField.createValueField("main"))
                                                 .variantType(RepoSbomVariantType.BRANCH)
                                                 .build())
                        .build())
            .settings(ParameterField.createValueField(settingsMap))
            .build();

    Map<String, String> envMap = sscaAibomOrchestrationPluginHelper.getSscaAibomOrchestrationStepEnvVariables(
        stepInfo, "id4", ambiance, Type.K8);

    assertThat(envMap.get("SCS_SETTINGS_HARNESS_SSCA_SERVICE_ENDPOINT"))
        .isEqualTo("https://custom-proxy.internal/ssca-manager/");
  }
}

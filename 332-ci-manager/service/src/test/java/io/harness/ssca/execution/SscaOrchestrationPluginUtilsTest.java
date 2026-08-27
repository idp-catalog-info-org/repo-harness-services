/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.HUMANSHU_ARORA;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.SHASHWAT_SACHAN;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;
import static io.harness.ssca.beans.SscaExecutionConstants.SCS_SETTINGS_ENV_PREFIX;
import static io.harness.ssca.execution.SscaOrchestrationPluginUtils.getSscaOrchestrationSecretVars;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_BASE64_SECRET;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_CLI_FLAGS;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_FORMAT;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_LOCAL_FILE_VERSION;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_MODE;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_NON_CONTAINER_ARTIFACT_NAME;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_REPO_SBOMSOURCE_CLONED_CODEBASE;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_REPO_SBOMSOURCE_PATH;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_REPO_SBOMSOURCE_URL;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_REPO_SBOMSOURCE_VARIANT;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_REPO_SBOMSOURCE_VARIANT_TYPE;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_SBOMDESTINATION;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_SBOMSOURCE;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_SBOMSOURCE_TYPE;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_SBOM_DRIFT;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_TOOL;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.PLUGIN_TYPE;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.SKIP_NORMALISATION;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.SSCA_ORCHESTRATION_COSIGN_PASSWORD;
import static io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils.SSCA_ORCHESTRATION_COSIGN_PRIVATE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.k8s.K8InitializeTaskUtils;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.ssca.beans.Attestation;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.attestation.CosignAttestation;
import io.harness.ssca.beans.ingestion.SbomFile;
import io.harness.ssca.beans.mode.SbomModeType;
import io.harness.ssca.beans.sbomDrift.SbomDrift;
import io.harness.ssca.beans.sbomDrift.SbomDriftBase;
import io.harness.ssca.beans.source.GarSbomSource;
import io.harness.ssca.beans.source.ImageSbomSource;
import io.harness.ssca.beans.source.LocalSbomSource;
import io.harness.ssca.beans.source.RepoSbomVariantType;
import io.harness.ssca.beans.source.RepositorySbomSource;
import io.harness.ssca.beans.source.SbomSource;
import io.harness.ssca.beans.source.SbomSourceType;
import io.harness.ssca.beans.stepinfo.SscaOrchestrationStepInfo;
import io.harness.ssca.beans.tools.CdxgenSbomOrchestration;
import io.harness.ssca.beans.tools.SbomOrchestrationTool;
import io.harness.ssca.beans.tools.SbomOrchestrationToolType;
import io.harness.ssca.beans.tools.SyftSbomOrchestration;
import io.harness.ssca.beans.tools.SyftSbomOrchestration.SyftOrchestrationFormat;
import io.harness.ssca.client.NgSettingsUtils;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class SscaOrchestrationPluginUtilsTest extends CIExecutionTestBase {
  @InjectMocks @Inject private SscaOrchestrationPluginUtils sscaOrchestrationPluginUtils;

  @Mock private K8InitializeTaskUtils k8InitializeTaskUtils;
  @Mock private NgSettingsUtils ngSettingsUtils;

  @Mock private NGSettingsClient settingsClient;

  @Mock private AccountClient accountClient;
  @Mock private SscaPluginUtils sscaPluginUtils;
  @Mock private CIFeatureFlagService featureFlagService;
  @InjectMocks @Named("harnessArtifactRegistryUrl") String harnessArtifactRegistryUrl;

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
    sscaOrchestrationPluginUtils = new SscaOrchestrationPluginUtils(
        ngSettingsUtils, sscaPluginUtils, accountClient, featureFlagService, harnessArtifactRegistryUrl);
    when(sscaPluginUtils.getStageName(any())).thenReturn("stageName");
    when(sscaPluginUtils.getStageExecutionIdentifier(any())).thenReturn("stageExecId");
    when(sscaPluginUtils.getPipelineTriggerByEmail(any())).thenReturn("triggerBy@harness.io");
  }
  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetSscaOrcherstrationStepEnvVariables() {
    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder()
            .tool(SbomOrchestrationTool.builder()
                      .type(SbomOrchestrationToolType.SYFT)
                      .sbomOrchestrationSpec(
                          SyftSbomOrchestration.builder().format(SyftOrchestrationFormat.SPDX_JSON).build())
                      .build())
            .source(SbomSource.builder()
                        .type(SbomSourceType.IMAGE)
                        .sbomSourceSpec(ImageSbomSource.builder()
                                            .image(ParameterField.createValueField("image:tag"))
                                            .connector(ParameterField.createValueField("conn1"))
                                            .build())
                        .build())
            .sbomDrift(SbomDrift.builder().base(SbomDriftBase.BASELINE).build())
            .build();

    Map<String, String> sscaEnvVarMap =
        sscaOrchestrationPluginUtils.getSscaOrchestrationStepEnvVariables(stepInfo, "id1", ambiance, Type.K8);
    assertThat(sscaEnvVarMap).isNotNull().isNotEmpty();
    assertThat(sscaEnvVarMap).hasSize(21);
    assertThat(sscaEnvVarMap.get(PLUGIN_MODE)).isEqualTo(SbomModeType.GENERATION.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_TOOL)).isEqualTo(SbomOrchestrationToolType.SYFT.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_FORMAT)).isEqualTo(SyftOrchestrationFormat.SPDX_JSON.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE)).isEqualTo("image:tag");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE_TYPE)).isEqualTo("image");
    assertThat(sscaEnvVarMap.get(PLUGIN_TYPE)).isEqualTo("Orchestrate");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMDESTINATION)).isEqualTo("harness/sbom");
    assertThat(sscaEnvVarMap.get(SKIP_NORMALISATION)).isEqualTo("true");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOM_DRIFT)).isEqualTo(SbomDriftBase.BASELINE.toString());
  }

  @Test
  @Owner(developers = HUMANSHU_ARORA)
  @Category(UnitTests.class)
  public void testGetSscaOrcherstrationStepEnvVariablesForGAR() {
    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder()
            .tool(SbomOrchestrationTool.builder()
                      .type(SbomOrchestrationToolType.SYFT)
                      .sbomOrchestrationSpec(
                          SyftSbomOrchestration.builder().format(SyftOrchestrationFormat.SPDX_JSON).build())
                      .build())
            .source(SbomSource.builder()
                        .type(SbomSourceType.GAR)
                        .sbomSourceSpec(GarSbomSource.builder()
                                            .image(ParameterField.createValueField("gar-image:tag"))
                                            .connector(ParameterField.createValueField("connector1"))
                                            .projectId(ParameterField.createValueField("project1"))
                                            .host(ParameterField.createValueField("host1"))
                                            .build())
                        .build())
            .sbomDrift(SbomDrift.builder().base(SbomDriftBase.BASELINE).build())
            .build();

    Map<String, String> sscaEnvVarMap =
        sscaOrchestrationPluginUtils.getSscaOrchestrationStepEnvVariables(stepInfo, "id1", ambiance, Type.K8);
    assertThat(sscaEnvVarMap).isNotNull().isNotEmpty();
    assertThat(sscaEnvVarMap).hasSize(22);
    assertThat(sscaEnvVarMap.get(PLUGIN_MODE)).isEqualTo(SbomModeType.GENERATION.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_TOOL)).isEqualTo(SbomOrchestrationToolType.SYFT.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_FORMAT)).isEqualTo(SyftOrchestrationFormat.SPDX_JSON.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE)).isEqualTo("project1/gar-image:tag");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE_TYPE)).isEqualTo("gar");
    assertThat(sscaEnvVarMap.get(PLUGIN_TYPE)).isEqualTo("Orchestrate");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMDESTINATION)).isEqualTo("harness/sbom");
    assertThat(sscaEnvVarMap.get(SKIP_NORMALISATION)).isEqualTo("true");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOM_DRIFT)).isEqualTo(SbomDriftBase.BASELINE.toString());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSscaOrcherstrationStepEnvVariables_ForCdxgen() {
    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder()
            .tool(
                SbomOrchestrationTool.builder()
                    .type(SbomOrchestrationToolType.CDXGEN)
                    .sbomOrchestrationSpec(CdxgenSbomOrchestration.builder()
                                               .format(CdxgenSbomOrchestration.CdxgenOrchestrationFormat.CYCLONEDX_JSON)
                                               .cliFlags(ParameterField.createValueField("--no-install-deps -t java"))
                                               .build())
                    .build())
            .source(SbomSource.builder()
                        .type(SbomSourceType.IMAGE)
                        .sbomSourceSpec(ImageSbomSource.builder()
                                            .image(ParameterField.createValueField("image:tag"))
                                            .connector(ParameterField.createValueField("conn1"))
                                            .build())
                        .build())
            .sbomDrift(SbomDrift.builder().base(SbomDriftBase.BASELINE).build())
            .build();

    Map<String, String> sscaEnvVarMap =
        sscaOrchestrationPluginUtils.getSscaOrchestrationStepEnvVariables(stepInfo, "id1", ambiance, Type.K8);
    assertThat(sscaEnvVarMap).isNotNull().isNotEmpty();
    assertThat(sscaEnvVarMap).hasSize(22);
    assertThat(sscaEnvVarMap.get(PLUGIN_MODE)).isEqualTo(SbomModeType.GENERATION.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_TOOL)).isEqualTo(SbomOrchestrationToolType.CDXGEN.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_FORMAT)).isEqualTo(SyftOrchestrationFormat.CYCLONEDX_JSON.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE)).isEqualTo("image:tag");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE_TYPE)).isEqualTo("image");
    assertThat(sscaEnvVarMap.get(PLUGIN_TYPE)).isEqualTo("Orchestrate");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMDESTINATION)).isEqualTo("harness/sbom");
    assertThat(sscaEnvVarMap.get(SKIP_NORMALISATION)).isEqualTo("true");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOM_DRIFT)).isEqualTo(SbomDriftBase.BASELINE.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_CLI_FLAGS)).isEqualTo("--no-install-deps -t java");
  }
  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetSscaOrcherstrationStepEnvVariables_repoSource() {
    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder()
            .tool(SbomOrchestrationTool.builder()
                      .type(SbomOrchestrationToolType.SYFT)
                      .sbomOrchestrationSpec(
                          SyftSbomOrchestration.builder().format(SyftOrchestrationFormat.SPDX_JSON).build())
                      .build())
            .source(SbomSource.builder()
                        .type(SbomSourceType.REPOSITORY)
                        .sbomSourceSpec(RepositorySbomSource.builder()
                                            .url(ParameterField.createValueField("url"))
                                            .path(ParameterField.createValueField("/path"))
                                            .variant(ParameterField.createValueField("develop"))
                                            .variantType(RepoSbomVariantType.BRANCH)
                                            .clonedCodebase(ParameterField.createValueField("/root/user"))
                                            .build())
                        .build())
            .sbomDrift(SbomDrift.builder().base(SbomDriftBase.BASELINE).build())
            .build();

    Map<String, String> sscaEnvVarMap =
        sscaOrchestrationPluginUtils.getSscaOrchestrationStepEnvVariables(stepInfo, "id1", ambiance, Type.K8);
    assertThat(sscaEnvVarMap).isNotNull().isNotEmpty();
    assertThat(sscaEnvVarMap).hasSize(24);
    assertThat(sscaEnvVarMap.get(PLUGIN_MODE)).isEqualTo(SbomModeType.GENERATION.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_TOOL)).isEqualTo(SbomOrchestrationToolType.SYFT.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_FORMAT)).isEqualTo(SyftOrchestrationFormat.SPDX_JSON.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE)).isEqualTo(null);
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE_TYPE)).isEqualTo("repository");
    assertThat(sscaEnvVarMap.get(PLUGIN_REPO_SBOMSOURCE_URL)).isEqualTo("url");
    assertThat(sscaEnvVarMap.get(PLUGIN_REPO_SBOMSOURCE_PATH)).isEqualTo("/path");
    assertThat(sscaEnvVarMap.get(PLUGIN_REPO_SBOMSOURCE_VARIANT)).isEqualTo("develop");
    assertThat(sscaEnvVarMap.get(PLUGIN_REPO_SBOMSOURCE_VARIANT_TYPE)).isEqualTo(RepoSbomVariantType.BRANCH.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_REPO_SBOMSOURCE_CLONED_CODEBASE)).isEqualTo("/root/user");
    assertThat(sscaEnvVarMap.get(PLUGIN_TYPE)).isEqualTo("Orchestrate");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMDESTINATION)).isEqualTo("harness/sbom");
    assertThat(sscaEnvVarMap.get(SKIP_NORMALISATION)).isEqualTo("true");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOM_DRIFT)).isEqualTo(SbomDriftBase.BASELINE.toString());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetSscaOrchestrationSecretVars() {
    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder()
            .attestation(Attestation.builder()
                             .type(AttestationType.COSIGN)
                             .attestationSpec(CosignAttestation.builder().password("test").privateKey("key").build())
                             .build())
            .build();

    Map<String, SecretNGVariable> secretNGVariableMap = getSscaOrchestrationSecretVars(stepInfo);
    assertThat(secretNGVariableMap).isNotNull().isNotEmpty().hasSize(4);
    assertThat(secretNGVariableMap.get(SSCA_ORCHESTRATION_COSIGN_PASSWORD)).isNotNull();
    SecretNGVariable variable = secretNGVariableMap.get(SSCA_ORCHESTRATION_COSIGN_PASSWORD);
    assertThat(variable.getType()).isEqualTo(NGVariableType.SECRET);
    assertThat(variable.getName()).isEqualTo(SSCA_ORCHESTRATION_COSIGN_PASSWORD);
    assertThat(variable.getValue()).isNotNull();
    SecretRefData secretRefData = variable.getValue().getValue();
    assertThat(secretRefData).isNotNull();
    assertThat(secretRefData.getScope()).isEqualTo(Scope.PROJECT);
    assertThat(secretRefData.toSecretRefStringValue()).isEqualTo("test");

    SscaOrchestrationStepInfo accountLevelSecretStepInfo =
        SscaOrchestrationStepInfo.builder()
            .attestation(
                Attestation.builder()
                    .type(AttestationType.COSIGN)
                    .attestationSpec(CosignAttestation.builder().password("account.test").privateKey("key").build())
                    .build())
            .build();
    Map<String, SecretNGVariable> secretVariableMap1 = getSscaOrchestrationSecretVars(accountLevelSecretStepInfo);
    assertThat(secretVariableMap1).isNotEmpty().hasSize(4);
    assertThat(secretVariableMap1.get(SSCA_ORCHESTRATION_COSIGN_PASSWORD)).isNotNull();
    assertThat(secretVariableMap1.get(SSCA_ORCHESTRATION_COSIGN_PASSWORD).getValue().getValue()).isNotNull();
    assertThat(secretVariableMap1.get(SSCA_ORCHESTRATION_COSIGN_PASSWORD).getValue().getValue().getScope())
        .isEqualTo(Scope.ACCOUNT);
    assertThat(secretVariableMap1.get(SSCA_ORCHESTRATION_COSIGN_PASSWORD).getValue().getValue().getIdentifier())
        .isEqualTo("test");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetSscaOrcherstrationStepEnvVariablesForVM() {
    long expressionFunctorToken = 12345;
    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder()
            .mode(SbomModeType.GENERATION)
            .tool(SbomOrchestrationTool.builder()
                      .type(SbomOrchestrationToolType.SYFT)
                      .sbomOrchestrationSpec(
                          SyftSbomOrchestration.builder().format(SyftOrchestrationFormat.SPDX_JSON).build())
                      .build())
            .source(SbomSource.builder()
                        .type(SbomSourceType.IMAGE)
                        .sbomSourceSpec(ImageSbomSource.builder()
                                            .image(ParameterField.createValueField("image:tag"))
                                            .connector(ParameterField.createValueField("conn1"))
                                            .build())
                        .build())
            .attestation(
                Attestation.builder()
                    .type(AttestationType.COSIGN)
                    .attestationSpec(CosignAttestation.builder().password("account.test").privateKey("key").build())
                    .build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .setExpressionFunctorToken(expressionFunctorToken)
                            .addLevels(Level.newBuilder()
                                           .setRuntimeId("runtimeID")
                                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE)))

                            .build();

    Map<String, String> sscaEnvVarMap =
        sscaOrchestrationPluginUtils.getSscaOrchestrationStepEnvVariables(stepInfo, "id1", ambiance, Type.VM);
    assertThat(sscaEnvVarMap).isNotNull().isNotEmpty();
    assertThat(sscaEnvVarMap).hasSize(24);
    assertThat(sscaEnvVarMap.get(PLUGIN_TOOL)).isEqualTo(SbomOrchestrationToolType.SYFT.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_FORMAT)).isEqualTo(SyftOrchestrationFormat.SPDX_JSON.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE)).isEqualTo("image:tag");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE_TYPE)).isEqualTo("image");
    assertThat(sscaEnvVarMap.get(PLUGIN_TYPE)).isEqualTo("Orchestrate");
    assertThat(sscaEnvVarMap.get(PLUGIN_MODE)).isEqualTo("generation");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMDESTINATION)).isEqualTo("harness/sbom");
    assertThat(sscaEnvVarMap.get(SKIP_NORMALISATION)).isEqualTo("true");
    assertThat(sscaEnvVarMap.get(PLUGIN_BASE64_SECRET)).isEqualTo("false");
    assertThat(sscaEnvVarMap.get(SSCA_ORCHESTRATION_COSIGN_PASSWORD))
        .isEqualTo(
            NGVariablesUtils.fetchSecretExpressionWithExpressionToken("account.test", expressionFunctorToken, false));
    assertThat(sscaEnvVarMap.get(SSCA_ORCHESTRATION_COSIGN_PRIVATE_KEY))
        .isEqualTo(NGVariablesUtils.fetchSecretExpressionWithExpressionToken("key", expressionFunctorToken, false));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetSscaOrcherstrationIngestionStepEnvVariables() {
    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder()
            .mode(SbomModeType.INGESTION)
            .ingestion(SbomFile.builder().file(ParameterField.createValueField("/path/to/sbom")).build())
            .source(SbomSource.builder()
                        .type(SbomSourceType.IMAGE)
                        .sbomSourceSpec(ImageSbomSource.builder()
                                            .image(ParameterField.createValueField("image:tag"))
                                            .connector(ParameterField.createValueField("conn1"))
                                            .build())
                        .build())
            .build();

    Map<String, String> sscaEnvVarMap =
        sscaOrchestrationPluginUtils.getSscaOrchestrationStepEnvVariables(stepInfo, "id1", ambiance, Type.K8);
    assertThat(sscaEnvVarMap).isNotNull().isNotEmpty();
    assertThat(sscaEnvVarMap).hasSize(18);
    assertThat(sscaEnvVarMap.get(PLUGIN_MODE)).isEqualTo(SbomModeType.INGESTION.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE)).isEqualTo("image:tag");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE_TYPE)).isEqualTo("image");
    assertThat(sscaEnvVarMap.get(PLUGIN_TYPE)).isEqualTo("Orchestrate");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMDESTINATION)).isEqualTo("/path/to/sbom");
    assertThat(sscaEnvVarMap.get(SKIP_NORMALISATION)).isEqualTo("true");
    assertThat(sscaEnvVarMap.get(PLUGIN_BASE64_SECRET)).isEqualTo("false");

    k8InitializeTaskUtils.removeEnvVarsWithSecretRef(sscaEnvVarMap);
    assertThat(sscaEnvVarMap).hasSize(18);
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testGetSscaOrchestrationIngestionStepEnvVariables_localSource() {
    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder()
            .mode(SbomModeType.INGESTION)
            .ingestion(SbomFile.builder().file(ParameterField.createValueField("/path/to/sbom")).build())
            .source(SbomSource.builder()
                        .type(SbomSourceType.LOCAL)
                        .sbomSourceSpec(LocalSbomSource.builder()
                                            .artifactName(ParameterField.createValueField("artifact1"))
                                            .version(ParameterField.createValueField("version1"))
                                            .build())
                        .build())
            .build();

    Map<String, String> sscaEnvVarMap =
        sscaOrchestrationPluginUtils.getSscaOrchestrationStepEnvVariables(stepInfo, "id1", ambiance, Type.K8);
    assertThat(sscaEnvVarMap).isNotNull().isNotEmpty();
    assertThat(sscaEnvVarMap).hasSize(19);
    assertThat(sscaEnvVarMap.get(PLUGIN_MODE)).isEqualTo(SbomModeType.INGESTION.toString());
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMSOURCE_TYPE)).isEqualTo("local");
    assertThat(sscaEnvVarMap.get(PLUGIN_TYPE)).isEqualTo("Orchestrate");
    assertThat(sscaEnvVarMap.get(PLUGIN_SBOMDESTINATION)).isEqualTo("/path/to/sbom");
    assertThat(sscaEnvVarMap.get(PLUGIN_NON_CONTAINER_ARTIFACT_NAME)).isEqualTo("artifact1");
    assertThat(sscaEnvVarMap.get(PLUGIN_LOCAL_FILE_VERSION)).isEqualTo("version1");
    assertThat(sscaEnvVarMap.get(SKIP_NORMALISATION)).isEqualTo("true");
    assertThat(sscaEnvVarMap.get(PLUGIN_BASE64_SECRET)).isEqualTo("false");

    k8InitializeTaskUtils.removeEnvVarsWithSecretRef(sscaEnvVarMap);
    assertThat(sscaEnvVarMap).hasSize(19);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testScsSettingsPrefixAppliedWhenSettingsProvided() {
    doCallRealMethod().when(sscaPluginUtils).addScsSettings(any(), anyString(), anyString(), any());

    Map<String, JsonNode> settingsMap = new HashMap<>();
    settingsMap.put("HARNESS_SSCA_SERVICE_ENDPOINT", new TextNode("https://custom-proxy.internal/ssca-manager/"));

    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder()
            .tool(SbomOrchestrationTool.builder()
                      .type(SbomOrchestrationToolType.SYFT)
                      .sbomOrchestrationSpec(
                          SyftSbomOrchestration.builder().format(SyftOrchestrationFormat.SPDX_JSON).build())
                      .build())
            .source(SbomSource.builder()
                        .type(SbomSourceType.IMAGE)
                        .sbomSourceSpec(ImageSbomSource.builder()
                                            .image(ParameterField.createValueField("image:tag"))
                                            .connector(ParameterField.createValueField("conn1"))
                                            .build())
                        .build())
            .settings(ParameterField.createValueField(settingsMap))
            .build();

    Map<String, String> sscaEnvVarMap =
        sscaOrchestrationPluginUtils.getSscaOrchestrationStepEnvVariables(stepInfo, "id1", ambiance, Type.K8);

    assertThat(sscaEnvVarMap.get(SCS_SETTINGS_ENV_PREFIX + "HARNESS_SSCA_SERVICE_ENDPOINT"))
        .isEqualTo("https://custom-proxy.internal/ssca-manager/");
    assertThat(sscaEnvVarMap.get(PLUGIN_TOOL)).isEqualTo(SbomOrchestrationToolType.SYFT.toString());
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testNoScsSettingsWhenSettingsNotProvided() {
    doCallRealMethod().when(sscaPluginUtils).addScsSettings(any(), anyString(), anyString(), any());

    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder()
            .tool(SbomOrchestrationTool.builder()
                      .type(SbomOrchestrationToolType.SYFT)
                      .sbomOrchestrationSpec(
                          SyftSbomOrchestration.builder().format(SyftOrchestrationFormat.SPDX_JSON).build())
                      .build())
            .source(SbomSource.builder()
                        .type(SbomSourceType.IMAGE)
                        .sbomSourceSpec(ImageSbomSource.builder()
                                            .image(ParameterField.createValueField("image:tag"))
                                            .connector(ParameterField.createValueField("conn1"))
                                            .build())
                        .build())
            .build();

    Map<String, String> sscaEnvVarMap =
        sscaOrchestrationPluginUtils.getSscaOrchestrationStepEnvVariables(stepInfo, "id1", ambiance, Type.K8);

    boolean hasScsPrefix = sscaEnvVarMap.keySet().stream().anyMatch(key -> key.startsWith(SCS_SETTINGS_ENV_PREFIX));
    assertThat(hasScsPrefix).isFalse();
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testScsSettingsSkipsNullValues() {
    doCallRealMethod().when(sscaPluginUtils).addScsSettings(any(), anyString(), anyString(), any());

    Map<String, JsonNode> settingsMap = new HashMap<>();
    settingsMap.put("HARNESS_SSCA_SERVICE_ENDPOINT", new TextNode("https://custom-proxy.internal/ssca-manager/"));
    settingsMap.put("NULL_SETTING", NullNode.getInstance());

    SscaOrchestrationStepInfo stepInfo =
        SscaOrchestrationStepInfo.builder()
            .tool(SbomOrchestrationTool.builder()
                      .type(SbomOrchestrationToolType.SYFT)
                      .sbomOrchestrationSpec(
                          SyftSbomOrchestration.builder().format(SyftOrchestrationFormat.SPDX_JSON).build())
                      .build())
            .source(SbomSource.builder()
                        .type(SbomSourceType.IMAGE)
                        .sbomSourceSpec(ImageSbomSource.builder()
                                            .image(ParameterField.createValueField("image:tag"))
                                            .connector(ParameterField.createValueField("conn1"))
                                            .build())
                        .build())
            .settings(ParameterField.createValueField(settingsMap))
            .build();

    Map<String, String> sscaEnvVarMap =
        sscaOrchestrationPluginUtils.getSscaOrchestrationStepEnvVariables(stepInfo, "id1", ambiance, Type.K8);

    assertThat(sscaEnvVarMap.get(SCS_SETTINGS_ENV_PREFIX + "HARNESS_SSCA_SERVICE_ENDPOINT"))
        .isEqualTo("https://custom-proxy.internal/ssca-manager/");
    assertThat(sscaEnvVarMap.containsKey(SCS_SETTINGS_ENV_PREFIX + "NULL_SETTING")).isFalse();
  }
}

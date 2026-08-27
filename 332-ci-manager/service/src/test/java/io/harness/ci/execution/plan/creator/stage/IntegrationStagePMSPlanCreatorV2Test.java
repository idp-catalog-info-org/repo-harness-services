/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.SHUBHAM_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.CIIntegrationStageModifier;
import io.harness.ci.execution.states.IntegrationStageStepPMS;
import io.harness.ci.execution.utils.CIStagePlanCreationUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.dto.CILicenseSummaryDTO;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NumberNGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class IntegrationStagePMSPlanCreatorV2Test {
  @Mock private CIIntegrationStageModifier ciIntegrationStageModifier;
  @Mock private CIStagePlanCreationUtils ciStagePlanCreationUtils;
  @Mock private CILicenseService ciLicenseService;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks IntegrationStagePMSPlanCreatorV2 integrationStagePMSPlanCreatorV2;

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testAddPipelineVariablesToStageNode() {
    String yaml = "pipeline:\n"
        + "  name: variables\n"
        + "  variables:\n"
        + "    - name: number\n"
        + "      type: Number\n"
        + "      description: \"\"\n"
        + "      value: 23\n"
        + "    - name: string\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      value: someString\n"
        + "    - name: secret\n"
        + "      type: Secret\n"
        + "      description: \"\"\n"
        + "      value: stage_secret_large\n";
    PlanCreationContext planCreationContext = PlanCreationContext.builder().yaml(yaml).build();
    IntegrationStageNode integrationStageNode = IntegrationStageNode.builder().build();
    integrationStagePMSPlanCreatorV2.addPipelineVariablesToStageNode(planCreationContext, integrationStageNode);
    List<NGVariable> pipelineVariables = integrationStageNode.getPipelineVariables();
    assertThat(pipelineVariables).isNotEmpty();
    assertThat(pipelineVariables.get(0)).isInstanceOf(NumberNGVariable.class);
    assertThat(pipelineVariables.get(0).getCurrentValue().getValue()).isEqualTo(23.0);
    assertThat(pipelineVariables.get(1)).isInstanceOf(StringNGVariable.class);
    assertThat(pipelineVariables.get(1).getCurrentValue().getValue()).isEqualTo("someString");
    assertThat(pipelineVariables.get(2)).isInstanceOf(SecretNGVariable.class);
    assertThat(pipelineVariables.get(2).getCurrentValue().getValue()).isInstanceOf(SecretRefData.class);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testAddPipelineVariablesToStageNode_empty() {
    String yaml = "pipeline:\n"
        + "  name: variables\n";
    PlanCreationContext planCreationContext = PlanCreationContext.builder().yaml(yaml).build();
    IntegrationStageNode integrationStageNode = IntegrationStageNode.builder().build();
    integrationStagePMSPlanCreatorV2.addPipelineVariablesToStageNode(planCreationContext, integrationStageNode);
    List<NGVariable> pipelineVariables = integrationStageNode.getPipelineVariables();
    assertThat(pipelineVariables).isEmpty();
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_whenAitBypass_thenSkipsValidation() throws Exception {
    // AIT bypass is centralized in CILicenseServiceImpl — returns ENTERPRISE for AIT principals
    when(ciLicenseService.getLicenseSummary(any(), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(ciIntegrationStageModifier.modifyExecutionPlan(any(), any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("stop-after-validation"));

    YamlField stageField = getStageField();
    PlanCreationContext ctx = getPlanCreationContext(stageField);
    IntegrationStageNode stageNode = buildStageNode();

    assertThatThrownBy(() -> integrationStagePMSPlanCreatorV2.createPlanForChildrenNodes(ctx, stageNode))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("stop-after-validation");

    verify(ciStagePlanCreationUtils)
        .validateFreeAccountStageExecutionLimit(eq(false), eq("accountId"), any(Infrastructure.class), eq("CI"));
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_whenNotBypassed_thenValidatesFreeAccountLimit() throws Exception {
    when(ciLicenseService.getLicenseSummary(any(), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.FREE).build());
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(ciIntegrationStageModifier.modifyExecutionPlan(any(), any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("stop-after-validation"));

    YamlField stageField = getStageField();
    PlanCreationContext ctx = getPlanCreationContext(stageField);
    IntegrationStageNode stageNode = buildStageNode();

    assertThatThrownBy(() -> integrationStagePMSPlanCreatorV2.createPlanForChildrenNodes(ctx, stageNode))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("stop-after-validation");

    verify(ciStagePlanCreationUtils)
        .validateFreeAccountStageExecutionLimit(eq(true), eq("accountId"), any(Infrastructure.class), eq("CI"));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStageTypes_shouldReturnCIStage() {
    Set<String> supportedTypes = integrationStagePMSPlanCreatorV2.getSupportedStageTypes();
    assertThat(supportedTypes).isEqualTo(ImmutableSet.of(StepSpecTypeConstants.CI_STAGE));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepType_shouldReturnIntegrationStageStepType() {
    IntegrationStageNode stageNode = buildStageNode();
    StepType stepType = integrationStagePMSPlanCreatorV2.getStepType(stageNode);
    assertThat(stepType).isEqualTo(IntegrationStageStepPMS.STEP_TYPE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldClass_shouldReturnIntegrationStageNodeClass() {
    Class<IntegrationStageNode> fieldClass = integrationStagePMSPlanCreatorV2.getFieldClass();
    assertThat(fieldClass).isEqualTo(IntegrationStageNode.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions_shouldReturnV0() {
    Set<String> versions = integrationStagePMSPlanCreatorV2.getSupportedYamlVersions();
    assertThat(versions).containsExactly(HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_whenLicenseIsNull_shouldThrowCIStageException() throws Exception {
    when(ciLicenseService.getLicenseSummary(any(), any(), any())).thenReturn(null);

    YamlField stageField = getStageField();
    PlanCreationContext ctx = getPlanCreationContext(stageField);
    IntegrationStageNode stageNode = buildStageNode();

    assertThatThrownBy(() -> integrationStagePMSPlanCreatorV2.createPlanForChildrenNodes(ctx, stageNode))
        .isInstanceOf(CIStageExecutionException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_happyPath_shouldReturnPlanWithSpecNode() throws Exception {
    when(ciLicenseService.getLicenseSummary(any(), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(ciIntegrationStageModifier.modifyExecutionPlan(any(), any(), any(), any(), any(), any()))
        .thenReturn(ExecutionElementConfig.builder().uuid("exec-uuid").steps(List.of()).build());

    YamlField stageField = getStageField();
    PlanCreationContext ctx = getPlanCreationContext(stageField);
    IntegrationStageNode stageNode = buildStageNodeWithServiceDependencies();

    LinkedHashMap<String, PlanCreationResponse> result =
        integrationStagePMSPlanCreatorV2.createPlanForChildrenNodes(ctx, stageNode);

    assertThat(result).isNotEmpty();
    boolean hasSpecNode = result.values()
                              .stream()
                              .filter(r -> r.getNodes() != null)
                              .flatMap(r -> r.getNodes().values().stream())
                              .anyMatch(n -> "spec".equals(n.getIdentifier()));
    assertThat(hasSpecNode).isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_withFeatureFlagEnabled_shouldSetPipelineVariables() throws Exception {
    when(ciLicenseService.getLicenseSummary(any(), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());
    when(featureFlagService.isEnabled(eq(FeatureName.CI_PIPELINE_VARIABLES_IN_STEPS), eq("accountId")))
        .thenReturn(true);
    when(ciIntegrationStageModifier.modifyExecutionPlan(any(), any(), any(), any(), any(), any()))
        .thenReturn(ExecutionElementConfig.builder().uuid("exec-uuid").steps(List.of()).build());

    String pipelineYaml = "pipeline:\n"
        + "  name: test\n"
        + "  variables:\n"
        + "    - name: myVar\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      value: testValue\n";
    YamlField stageField = getStageField();
    PlanCreationContext ctx = getPlanCreationContext(stageField);
    when(ctx.getYaml()).thenReturn(pipelineYaml);
    IntegrationStageNode stageNode = buildStageNodeWithServiceDependencies();

    LinkedHashMap<String, PlanCreationResponse> result =
        integrationStagePMSPlanCreatorV2.createPlanForChildrenNodes(ctx, stageNode);

    assertThat(result).isNotEmpty();
    assertThat(stageNode.getPipelineVariables()).hasSize(1);
    assertThat(stageNode.getPipelineVariables().get(0)).isInstanceOf(StringNGVariable.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode_shouldReturnPlanNodeWithCorrectStepType() throws Exception {
    when(ciStagePlanCreationUtils.getStageParameters(any()))
        .thenReturn(StageElementParameters.builder().name("build").identifier("build"));

    YamlField stageField = getStageField();
    PlanCreationContext ctx = getPlanCreationContext(stageField);
    IntegrationStageNode stageNode = buildStageNodeWithServiceDependencies();
    stageNode.setUuid("stage-uuid");

    var planNode = integrationStagePMSPlanCreatorV2.createPlanForParentNode(ctx, stageNode, List.of("child-1"));

    assertThat(planNode).isNotNull();
    assertThat(planNode.getStepType()).isEqualTo(IntegrationStageStepPMS.STEP_TYPE);
    assertThat(planNode.getIdentifier()).isEqualTo("build");
    assertThat(planNode.getName()).isEqualTo("build");
  }

  private YamlField getStageField() throws Exception {
    String yaml = "stage:\n"
        + "  identifier: build\n"
        + "  type: CI\n"
        + "  name: build\n"
        + "  spec:\n"
        + "    cloneCodebase: false\n"
        + "    infrastructure:\n"
        + "      type: KubernetesDirect\n"
        + "      spec:\n"
        + "        connectorRef: account.testConnector\n"
        + "        namespace: harness-delegate-ng\n"
        + "        automountServiceAccountToken: true\n"
        + "        os: Linux\n"
        + "    execution:\n"
        + "      steps: []\n";
    return YamlUtils.readTree(YamlUtils.injectUuid(yaml)).getNode().getField("stage");
  }

  private PlanCreationContext getPlanCreationContext(YamlField stageField) {
    PlanCreationContext ctx = mock(PlanCreationContext.class);
    when(ctx.getCurrentField()).thenReturn(stageField);
    when(ctx.getAccountIdentifier()).thenReturn("accountId");
    when(ctx.getPrincipalInfo())
        .thenReturn(ExecutionPrincipalInfo.newBuilder()
                        .setPrincipalType(PrincipalType.SERVICE)
                        .setPrincipal("AITestExecutionNodeApiService")
                        .build());
    return ctx;
  }

  private IntegrationStageNode buildStageNode() {
    return IntegrationStageNode.builder()
        .identifier("build")
        .name("build")
        .type(IntegrationStageNode.StepType.CI)
        .integrationStageConfig(
            IntegrationStageConfigImpl.builder()
                .cloneCodebase(ParameterField.createValueField(false))
                .infrastructure(K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).build())
                .execution(ExecutionElementConfig.builder().steps(List.of()).build())
                .build())
        .build();
  }

  private IntegrationStageNode buildStageNodeWithServiceDependencies() {
    return IntegrationStageNode.builder()
        .identifier("build")
        .name("build")
        .type(IntegrationStageNode.StepType.CI)
        .integrationStageConfig(
            IntegrationStageConfigImpl.builder()
                .cloneCodebase(ParameterField.createValueField(false))
                .infrastructure(K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).build())
                .execution(ExecutionElementConfig.builder().steps(List.of()).build())
                .serviceDependencies(ParameterField.createValueField(List.of()))
                .build())
        .build();
  }
}

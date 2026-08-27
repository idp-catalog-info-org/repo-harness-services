/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.stages.SecurityStageConfigImpl;
import io.harness.beans.stages.SecurityStageNode;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.integrationstage.CIIntegrationStageModifier;
import io.harness.ci.execution.states.SecurityStageStepPMS;
import io.harness.ci.execution.utils.CIStagePlanCreationUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
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
public class SecurityStagePMSPlanCreatorTest {
  @Mock private CIIntegrationStageModifier ciIntegrationStageModifier;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private CIStagePlanCreationUtils ciStagePlanCreationUtils;
  @Mock private CIFeatureFlagService featureFlagService;
  @InjectMocks private SecurityStagePMSPlanCreator securityStagePMSPlanCreator;

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStageTypes_shouldReturnSecurityStage() {
    Set<String> supportedTypes = securityStagePMSPlanCreator.getSupportedStageTypes();
    assertThat(supportedTypes).isEqualTo(ImmutableSet.of(StepSpecTypeConstants.SECURITY_STAGE));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepType_shouldReturnSecurityStageStepType() {
    SecurityStageNode stageNode = buildSecurityStageNode(false);
    StepType stepType = securityStagePMSPlanCreator.getStepType(stageNode);
    assertThat(stepType).isEqualTo(SecurityStageStepPMS.STEP_TYPE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldClass_shouldReturnSecurityStageNodeClass() {
    Class<SecurityStageNode> fieldClass = securityStagePMSPlanCreator.getFieldClass();
    assertThat(fieldClass).isEqualTo(SecurityStageNode.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddPipelineVariablesToStageNode_shouldSetVariablesOnNode() {
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
    SecurityStageNode stageNode = buildSecurityStageNode(false);
    securityStagePMSPlanCreator.addPipelineVariablesToStageNode(planCreationContext, stageNode);
    List<NGVariable> pipelineVariables = stageNode.getPipelineVariables();
    assertThat(pipelineVariables).hasSize(3);
    assertThat(pipelineVariables)
        .extracting(Object::getClass)
        .containsExactly(NumberNGVariable.class, StringNGVariable.class, SecretNGVariable.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddPipelineVariablesToStageNode_whenNoVariables_shouldSetEmptyList() {
    String yaml = "pipeline:\n"
        + "  name: noVars\n";
    PlanCreationContext planCreationContext = PlanCreationContext.builder().yaml(yaml).build();
    SecurityStageNode stageNode = buildSecurityStageNode(false);
    securityStagePMSPlanCreator.addPipelineVariablesToStageNode(planCreationContext, stageNode);
    List<NGVariable> pipelineVariables = stageNode.getPipelineVariables();
    assertThat(pipelineVariables).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_whenNoInfraOrRuntime_shouldThrowCIStageException() throws Exception {
    when(featureFlagService.isEnabled(eq(FeatureName.CI_PIPELINE_VARIABLES_IN_STEPS), any())).thenReturn(false);

    YamlField stageField = getStageField();
    PlanCreationContext ctx = buildPlanCreationContext(stageField, getFullPipelineYaml());
    SecurityStageNode stageNode = buildSecurityStageNodeWithoutInfra();

    assertThatThrownBy(() -> securityStagePMSPlanCreator.createPlanForChildrenNodes(ctx, stageNode))
        .isInstanceOf(CIStageExecutionException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSpecParameters_whenNoInfraOrRuntime_shouldThrowCIStageException() throws Exception {
    YamlField stageField = getStageField();
    PlanCreationContext ctx = buildPlanCreationContext(stageField, getFullPipelineYaml());
    SecurityStageNode stageNode = buildSecurityStageNodeWithoutInfra();

    assertThatThrownBy(() -> securityStagePMSPlanCreator.getSpecParameters("child-node-id", ctx, stageNode))
        .isInstanceOf(CIStageExecutionException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_happyPath_shouldReturnPlanWithSpecNode() throws Exception {
    when(featureFlagService.isEnabled(eq(FeatureName.CI_PIPELINE_VARIABLES_IN_STEPS), any())).thenReturn(false);
    when(ciIntegrationStageModifier.modifyExecutionPlan(any(), any(), any(), any(), any(), any()))
        .thenReturn(ExecutionElementConfig.builder().uuid("exec-uuid").steps(List.of()).build());

    YamlField stageField = getStageField();
    PlanCreationContext ctx = buildPlanCreationContext(stageField, getFullPipelineYaml());
    SecurityStageNode stageNode = buildSecurityStageNodeWithInfra();

    LinkedHashMap<String, PlanCreationResponse> result =
        securityStagePMSPlanCreator.createPlanForChildrenNodes(ctx, stageNode);

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
  public void testCreatePlanForChildrenNodes_withFeatureFlagEnabled_shouldSetVariablesAndReturnPlan() throws Exception {
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
    PlanCreationContext ctx = buildPlanCreationContext(stageField, pipelineYaml);
    SecurityStageNode stageNode = buildSecurityStageNodeWithInfra();

    LinkedHashMap<String, PlanCreationResponse> result =
        securityStagePMSPlanCreator.createPlanForChildrenNodes(ctx, stageNode);

    assertThat(result).isNotEmpty();
    assertThat(stageNode.getPipelineVariables()).hasSize(1);
    assertThat(stageNode.getPipelineVariables().get(0)).isInstanceOf(StringNGVariable.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode_shouldReturnPlanNodeWithCorrectStepType() throws Exception {
    when(ciStagePlanCreationUtils.getStageParameters(any()))
        .thenReturn(StageElementParameters.builder().name("Security Stage").identifier("security_stage"));

    YamlField stageField = getStageField();
    PlanCreationContext ctx = buildPlanCreationContext(stageField, getFullPipelineYaml());
    when(ctx.getCurrentField()).thenReturn(stageField);
    SecurityStageNode stageNode = buildSecurityStageNodeWithInfra();

    var planNode = securityStagePMSPlanCreator.createPlanForParentNode(ctx, stageNode, List.of("child-1"));

    assertThat(planNode).isNotNull();
    assertThat(planNode.getStepType()).isEqualTo(SecurityStageStepPMS.STEP_TYPE);
    assertThat(planNode.getIdentifier()).isEqualTo("security_stage");
    assertThat(planNode.getName()).isEqualTo("Security Stage");
  }

  private SecurityStageNode buildSecurityStageNode(boolean cloneCodebase) {
    SecurityStageConfigImpl config =
        SecurityStageConfigImpl.builder()
            .cloneCodebase(ParameterField.createValueField(cloneCodebase))
            .execution(ExecutionElementConfig.builder().steps(List.of()).build())
            .infrastructure(K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).build())
            .build();
    SecurityStageNode stageNode = SecurityStageNode.builder().securityStageConfig(config).build();
    stageNode.setIdentifier("security_stage");
    stageNode.setName("Security Stage");
    stageNode.setUuid("stage-uuid");
    return stageNode;
  }

  private SecurityStageNode buildSecurityStageNodeWithInfra() {
    SecurityStageConfigImpl config =
        SecurityStageConfigImpl.builder()
            .cloneCodebase(ParameterField.createValueField(false))
            .execution(ExecutionElementConfig.builder().steps(List.of()).build())
            .infrastructure(K8sDirectInfraYaml.builder().type(Infrastructure.Type.KUBERNETES_DIRECT).build())
            .serviceDependencies(ParameterField.createValueField(List.of()))
            .build();
    SecurityStageNode stageNode = SecurityStageNode.builder().securityStageConfig(config).build();
    stageNode.setIdentifier("security_stage");
    stageNode.setName("Security Stage");
    stageNode.setUuid("stage-uuid");
    return stageNode;
  }

  private SecurityStageNode buildSecurityStageNodeWithoutInfra() {
    SecurityStageConfigImpl config = SecurityStageConfigImpl.builder()
                                         .cloneCodebase(ParameterField.createValueField(false))
                                         .execution(ExecutionElementConfig.builder().steps(List.of()).build())
                                         .build();
    SecurityStageNode stageNode = SecurityStageNode.builder().securityStageConfig(config).build();
    stageNode.setIdentifier("security_stage");
    stageNode.setName("Security Stage");
    stageNode.setUuid("stage-uuid");
    return stageNode;
  }

  private PlanCreationContext buildPlanCreationContext(YamlField stageField, String yaml) {
    PlanCreationContext ctx = mock(PlanCreationContext.class);
    when(ctx.getCurrentField()).thenReturn(stageField);
    when(ctx.getAccountIdentifier()).thenReturn("accountId");
    when(ctx.getYaml()).thenReturn(yaml);
    return ctx;
  }

  private YamlField getStageField() throws Exception {
    String yaml = "stage:\n"
        + "  identifier: security_stage\n"
        + "  type: SecurityTests\n"
        + "  name: Security Stage\n"
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

  private String getFullPipelineYaml() {
    return "pipeline:\n"
        + "  name: test\n";
  }
}

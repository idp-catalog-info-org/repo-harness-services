/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.ContainerResource;
import io.harness.opaclient.model.KubernetesDirectInfraParams;
import io.harness.opaclient.model.PolicySetData;
import io.harness.opaclient.model.ResourceLimits;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.execution.ExecutionModeUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class OpaEvaluationStageHelperTest extends CategoryTest {
  @Mock private OpaServiceClient opaServiceClient;

  private OpaEvaluationStageHelper opaEvaluationStageHelper;

  private PlanCreationContext planCreationContext;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    planCreationContext = PlanCreationContext.builder().build();

    // Create helper instance and inject dependencies using reflection
    opaEvaluationStageHelper = new OpaEvaluationStageHelper();

    // Inject OpaServiceClient
    java.lang.reflect.Field opaServiceClientField = OpaEvaluationStageHelper.class.getDeclaredField("opaServiceClient");
    opaServiceClientField.setAccessible(true);
    opaServiceClientField.set(opaEvaluationStageHelper, opaServiceClient);

    // Inject opaEvaluationPluginImage String
    java.lang.reflect.Field pluginImageField =
        OpaEvaluationStageHelper.class.getDeclaredField("opaEvaluationPluginImage");
    pluginImageField.setAccessible(true);
    pluginImageField.set(opaEvaluationStageHelper, "harness/opa-evaluation-plugin:latest");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreateAggregatorStepNameAndIdentifier() throws Exception {
    // Use reflection to access private method
    Method method = OpaEvaluationStageHelper.class.getDeclaredMethod("createAggregatorStep", PlanCreationContext.class);
    method.setAccessible(true);
    ObjectNode stepNode = (ObjectNode) method.invoke(opaEvaluationStageHelper, planCreationContext);

    // Verify step name
    assertThat(stepNode.get("name").asText()).isEqualTo("OPA Evaluation Decision");

    // Verify step identifier
    assertThat(stepNode.get("identifier").asText()).isEqualTo("opa_evaluation_decision");

    // Verify step type
    assertThat(stepNode.get("type").asText()).isEqualTo(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreateAggregatorStepWhenCondition() throws Exception {
    // Use reflection to access private method
    Method method = OpaEvaluationStageHelper.class.getDeclaredMethod("createAggregatorStep", PlanCreationContext.class);
    method.setAccessible(true);
    ObjectNode stepNode = (ObjectNode) method.invoke(opaEvaluationStageHelper, planCreationContext);

    // Verify "when" condition exists
    assertThat(stepNode.has("when")).isTrue();

    // Verify "when.stageStatus" is "All"
    ObjectNode whenNode = (ObjectNode) stepNode.get("when");
    assertThat(whenNode.get("stageStatus").asText()).isEqualTo("All");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildOpaEvaluationStepWhenCondition() throws Exception {
    // Create a mock PolicySetData using builder
    PolicySetData policySetData =
        PolicySetData.builder().identifier("test-policy-set").name("Test Policy Set").account_id("account-id").build();

    // Use reflection to access private method
    Method method = OpaEvaluationStageHelper.class.getDeclaredMethod(
        "buildOpaEvaluationStep", PolicySetData.class, String.class, String.class);
    method.setAccessible(true);
    ObjectNode stepNode =
        (ObjectNode) method.invoke(opaEvaluationStageHelper, policySetData, "test-policy-set", "account");

    // Verify "when" condition exists
    assertThat(stepNode.has("when")).isTrue();

    // Verify "when.stageStatus" is "All"
    ObjectNode whenNode = (ObjectNode) stepNode.get("when");
    assertThat(whenNode.get("stageStatus").asText()).isEqualTo("All");

    // Verify step name
    assertThat(stepNode.get("name").asText()).isEqualTo("Policy set Evaluation");

    // Verify step type
    assertThat(stepNode.get("type").asText()).isEqualTo(StepSpecTypeConstants.OPA_EVALUATION);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testInjectOpaStageIntoProcessedYamlWithEmptyAccountId() {
    String processedYaml = "pipeline:\n  stages:\n    - stage:\n        name: Test Stage";
    String result = opaEvaluationStageHelper.injectOpaStageIntoProcessedYaml(
        "", "org-id", "project-id", "execution-uuid", "pipeline-id", ExecutionMode.NORMAL, processedYaml);

    // Should return original YAML without modification
    assertThat(result).isEqualTo(processedYaml);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testInjectOpaStageIntoProcessedYamlWithRollbackMode() {
    String processedYaml = "pipeline:\n  stages:\n    - stage:\n        name: Test Stage";

    try (MockedStatic<ExecutionModeUtils> executionModeUtilsMock = mockStatic(ExecutionModeUtils.class)) {
      executionModeUtilsMock.when(() -> ExecutionModeUtils.isRollbackMode(ExecutionMode.POST_EXECUTION_ROLLBACK))
          .thenReturn(true);

      String result = opaEvaluationStageHelper.injectOpaStageIntoProcessedYaml("account-id", "org-id", "project-id",
          "execution-uuid", "pipeline-id", ExecutionMode.POST_EXECUTION_ROLLBACK, processedYaml);

      // Should return original YAML without modification
      assertThat(result).isEqualTo(processedYaml);
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testInjectOpaStageIntoProcessedYamlWithNoPolicySets() {
    String processedYaml = "pipeline:\n  stages:\n    - stage:\n        name: Test Stage";

    try (MockedStatic<SafeHttpCall> safeHttpCallMock = mockStatic(SafeHttpCall.class)) {
      Call<List<PolicySetData>> accountCall = mock(Call.class);
      Call<List<PolicySetData>> orgCall = mock(Call.class);
      Call<List<PolicySetData>> projectCall = mock(Call.class);

      when(opaServiceClient.listOpaPolicySetsWithTypeAndAction(
               anyString(), isNull(), isNull(), isNull(), anyString(), anyString()))
          .thenReturn(accountCall);
      when(opaServiceClient.listOpaPolicySetsWithTypeAndAction(
               anyString(), anyString(), isNull(), isNull(), anyString(), anyString()))
          .thenReturn(orgCall);
      when(opaServiceClient.listOpaPolicySetsWithTypeAndAction(
               anyString(), anyString(), anyString(), isNull(), anyString(), anyString()))
          .thenReturn(projectCall);

      safeHttpCallMock.when(() -> SafeHttpCall.executeWithErrorMessage(any(Call.class))).thenReturn(new ArrayList<>());

      String result = opaEvaluationStageHelper.injectOpaStageIntoProcessedYaml(
          "account-id", "org-id", "project-id", "execution-uuid", "pipeline-id", ExecutionMode.NORMAL, processedYaml);

      // Should return original YAML without modification
      assertThat(result).isEqualTo(processedYaml);
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testPolicySetListingCarriesPrincipal() {
    String processedYaml = "pipeline:\n  stages:\n    - stage:\n        name: Test Stage";
    AtomicReference<Principal> principalDuringCall = new AtomicReference<>();

    SecurityContextBuilder.unsetCompleteContext();
    try (MockedStatic<SafeHttpCall> safeHttpCallMock = mockStatic(SafeHttpCall.class)) {
      when(opaServiceClient.listOpaPolicySetsWithTypeAndAction(
               anyString(), any(), any(), isNull(), anyString(), anyString()))
          .thenReturn(mock(Call.class));
      safeHttpCallMock.when(() -> SafeHttpCall.executeWithErrorMessage(any(Call.class))).thenAnswer(invocation -> {
        principalDuringCall.compareAndSet(null, SecurityContextBuilder.getPrincipal());
        return new ArrayList<>();
      });

      opaEvaluationStageHelper.injectOpaStageIntoProcessedYaml(
          "account-id", "org-id", "project-id", "execution-uuid", "pipeline-id", ExecutionMode.NORMAL, processedYaml);
    }

    assertThat(principalDuringCall.get()).isInstanceOf(ServicePrincipal.class);
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreateAggregatorStepSpecAndTimeout() throws Exception {
    Method method = OpaEvaluationStageHelper.class.getDeclaredMethod("createAggregatorStep", PlanCreationContext.class);
    method.setAccessible(true);
    ObjectNode stepNode = (ObjectNode) method.invoke(opaEvaluationStageHelper, planCreationContext);

    // Verify spec exists
    assertThat(stepNode.has("spec")).isTrue();

    // Verify timeout in spec
    ObjectNode specNode = (ObjectNode) stepNode.get("spec");
    assertThat(specNode.get("timeout").asText()).isEqualTo("5m");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildOpaEvaluationStepWithAccountScope() throws Exception {
    PolicySetData policySetData = PolicySetData.builder()
                                      .identifier("test-policy-set")
                                      .name("Test Policy Set")
                                      .account_id("account-id")
                                      .org_id(null)
                                      .project_id(null)
                                      .build();

    Method method = OpaEvaluationStageHelper.class.getDeclaredMethod(
        "buildOpaEvaluationStep", PolicySetData.class, String.class, String.class);
    method.setAccessible(true);
    ObjectNode stepNode =
        (ObjectNode) method.invoke(opaEvaluationStageHelper, policySetData, "test-policy-set", "account");

    // Verify step identifier includes account scope prefix
    assertThat(stepNode.get("identifier").asText()).isEqualTo("account_opa_eval_test-policy-set");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildOpaEvaluationStepWithOrgScope() throws Exception {
    PolicySetData policySetData = PolicySetData.builder()
                                      .identifier("test-policy-set")
                                      .name("Test Policy Set")
                                      .account_id("account-id")
                                      .org_id("org-id")
                                      .project_id(null)
                                      .build();

    Method method = OpaEvaluationStageHelper.class.getDeclaredMethod(
        "buildOpaEvaluationStep", PolicySetData.class, String.class, String.class);
    method.setAccessible(true);
    ObjectNode stepNode = (ObjectNode) method.invoke(opaEvaluationStageHelper, policySetData, "test-policy-set", "org");

    // Verify step identifier includes org scope prefix
    assertThat(stepNode.get("identifier").asText()).isEqualTo("org_opa_eval_test-policy-set");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildOpaEvaluationStepWithProjectScope() throws Exception {
    PolicySetData policySetData = PolicySetData.builder()
                                      .identifier("test-policy-set")
                                      .name("Test Policy Set")
                                      .account_id("account-id")
                                      .org_id("org-id")
                                      .project_id("project-id")
                                      .build();

    Method method = OpaEvaluationStageHelper.class.getDeclaredMethod(
        "buildOpaEvaluationStep", PolicySetData.class, String.class, String.class);
    method.setAccessible(true);
    ObjectNode stepNode =
        (ObjectNode) method.invoke(opaEvaluationStageHelper, policySetData, "test-policy-set", "project");

    // Verify step identifier includes project scope prefix
    assertThat(stepNode.get("identifier").asText()).isEqualTo("project_opa_eval_test-policy-set");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildStepSpec() throws Exception {
    PolicySetData policySetData = PolicySetData.builder()
                                      .identifier("test-policy-set")
                                      .org_id("org-id")
                                      .project_id("project-id")
                                      .account_id("account-id")
                                      .build();

    Method method =
        OpaEvaluationStageHelper.class.getDeclaredMethod("buildStepSpec", PolicySetData.class, String.class);
    method.setAccessible(true);
    ObjectNode stepSpecNode = (ObjectNode) method.invoke(opaEvaluationStageHelper, policySetData, "test-policy-set");

    // Verify policySetId
    assertThat(stepSpecNode.get("policySetId").asText()).isEqualTo("test-policy-set");

    // Verify org and project IDs
    assertThat(stepSpecNode.get("policySetOrgId").asText()).isEqualTo("org-id");
    assertThat(stepSpecNode.get("policySetProjectId").asText()).isEqualTo("project-id");

    // Verify image
    assertThat(stepSpecNode.get("image").asText()).isEqualTo("harness/opa-evaluation-plugin:latest");

    // Verify timeout
    assertThat(stepSpecNode.get("timeout").asText()).isEqualTo("10m");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildResourcesNode() throws Exception {
    ResourceLimits limits = ResourceLimits.builder().memory("500Mi").cpu("500m").build();
    ContainerResource resources = ContainerResource.builder().limits(limits).build();

    Method method = OpaEvaluationStageHelper.class.getDeclaredMethod(
        "buildResourcesNode", ContainerResource.class, com.fasterxml.jackson.databind.node.JsonNodeFactory.class);
    method.setAccessible(true);
    ObjectNode resourcesNode = (ObjectNode) method.invoke(
        opaEvaluationStageHelper, resources, com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);

    // Verify limits exist
    assertThat(resourcesNode.has("limits")).isTrue();
    ObjectNode limitsNode = (ObjectNode) resourcesNode.get("limits");
    assertThat(limitsNode.get("memory").asText()).isEqualTo("500Mi");
    assertThat(limitsNode.get("cpu").asText()).isEqualTo("500m");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildOpaStageYaml() throws Exception {
    List<PolicySetData> policySets = new ArrayList<>();
    KubernetesDirectInfraParams k8sParams = KubernetesDirectInfraParams.builder()
                                                .connectorRef("k8s-connector")
                                                .namespace("default")
                                                .initTimeout("10m")
                                                .build();
    PolicySetData policySet = PolicySetData.builder()
                                  .identifier("policy-set-1")
                                  .name("Policy Set 1")
                                  .account_id("account-id")
                                  .infra_type("KubernetesDirect")
                                  .infra_params(k8sParams)
                                  .build();
    policySets.add(policySet);

    Method method =
        OpaEvaluationStageHelper.class.getDeclaredMethod("buildOpaStageYaml", List.class, PlanCreationContext.class);
    method.setAccessible(true);
    ObjectNode rootNode = (ObjectNode) method.invoke(opaEvaluationStageHelper, policySets, planCreationContext);

    // Verify root node has stage field
    assertThat(rootNode.has(YAMLFieldNameConstants.STAGE)).isTrue();

    ObjectNode stageNode = (ObjectNode) rootNode.get(YAMLFieldNameConstants.STAGE);
    assertThat(stageNode.get("name").asText()).isEqualTo("OPA Evaluation");
    assertThat(stageNode.get("identifier").asText()).isEqualTo("Harness_OPA_Evaluation");
    assertThat(stageNode.get("type").asText()).isEqualTo("Custom");

    // Verify spec.execution.steps exists
    ObjectNode specNode = (ObjectNode) stageNode.get("spec");
    ObjectNode executionNode = (ObjectNode) specNode.get("execution");
    ArrayNode stepsArray = (ArrayNode) executionNode.get("steps");

    // Should have parallel step groups + aggregator step
    assertThat(stepsArray.size()).isGreaterThan(0);

    // Verify aggregator step is present
    boolean hasAggregatorStep = false;
    for (JsonNode step : stepsArray) {
      if (step.has(YAMLFieldNameConstants.STEP)) {
        ObjectNode stepObj = (ObjectNode) step.get(YAMLFieldNameConstants.STEP);
        if (stepObj.get("identifier").asText().equals("opa_evaluation_decision")) {
          hasAggregatorStep = true;
          break;
        }
      }
    }
    assertThat(hasAggregatorStep).isTrue();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreateStepGroupForPolicySet() throws Exception {
    KubernetesDirectInfraParams k8sParams = KubernetesDirectInfraParams.builder()
                                                .connectorRef("k8s-connector")
                                                .namespace("default")
                                                .initTimeout("10m")
                                                .build();
    PolicySetData policySet = PolicySetData.builder()
                                  .identifier("policy-set-1")
                                  .name("Policy Set 1")
                                  .account_id("account-id")
                                  .org_id(null)
                                  .project_id(null)
                                  .infra_type("KubernetesDirect")
                                  .infra_params(k8sParams)
                                  .build();

    Method method = OpaEvaluationStageHelper.class.getDeclaredMethod(
        "createStepGroupForPolicySet", PolicySetData.class, PlanCreationContext.class);
    method.setAccessible(true);
    ObjectNode stepGroupNode = (ObjectNode) method.invoke(opaEvaluationStageHelper, policySet, planCreationContext);

    // Verify step group name includes account scope
    assertThat(stepGroupNode.get("name").asText()).contains("account");
    assertThat(stepGroupNode.get("name").asText()).contains("Policy Set");

    // Verify step group identifier
    assertThat(stepGroupNode.get("identifier").asText()).isEqualTo("account_policy_set_policy-set-1");

    // Verify infrastructure exists
    assertThat(stepGroupNode.has("stepGroupInfra")).isTrue();

    // Verify steps array exists
    assertThat(stepGroupNode.has("steps")).isTrue();
    ArrayNode stepsArray = (ArrayNode) stepGroupNode.get("steps");
    assertThat(stepsArray.size()).isEqualTo(1);
  }
}

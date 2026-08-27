/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.plancreator.V1.UnifiedStageCDInfraPlanCreatorUtils;
import io.harness.ci.states.V1.cd.UnifiedCDInfraStep;
import io.harness.ci.states.V1.cd.UnifiedCDInfraStepParameters;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.v1.RunInfoUtilsV1;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class UnifiedStageCDInfraPlanCreatorUtilsTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private KryoSerializer kryoSerializer;

  @Before
  public void setUp() {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddCDInfrastructureNode_shouldCreateInfraNodeWithCorrectProperties() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    deployModuleInfo.put("environment", "my-env");
    deployModuleInfo.put("infrastructure", "my-infra");

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageCDInfraPlanCreatorUtils.addCDInfrastructureNode(
          kryoSerializer, "next-node-id", "infra-node-id", deployModuleInfo, false, null);

      assertThat(result).as("should contain one entry for infra node").hasSize(1);
      assertThat(result.containsKey("infra-node-id")).as("should use infraNodeId as key").isTrue();

      PlanNode node = result.get("infra-node-id").getPlanNode();
      assertThat(node.getStepType()).as("should be UnifiedCDInfraStep type").isEqualTo(UnifiedCDInfraStep.STEP_TYPE);
      assertThat(node.getName()).as("should have Infrastructure name").isEqualTo("Infrastructure");
      assertThat(node.getIdentifier()).as("should have infrastructure identifier").isEqualTo("infrastructure");
      assertThat(node.getGroup()).as("should have infrastructureGroup").isEqualTo("infrastructureGroup");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddCDInfrastructureNode_whenSingleEnvAndSingleService_shouldSetValueParameters() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    deployModuleInfo.put("environment", "my-env-ref");
    deployModuleInfo.put("infrastructure", "my-infra-id");
    deployModuleInfo.put("INFRA_INPUTS", Map.of("key1", "val1"));
    deployModuleInfo.put("envBranchRef", "env-branch");
    deployModuleInfo.put("service", "my-service-ref");

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageCDInfraPlanCreatorUtils.addCDInfrastructureNode(
          kryoSerializer, "next-id", "infra-id", deployModuleInfo, false, null);

      PlanNode node = result.get("infra-id").getPlanNode();
      assertThat(node.getStepParameters()).isInstanceOf(UnifiedCDInfraStepParameters.class);
      UnifiedCDInfraStepParameters params = (UnifiedCDInfraStepParameters) node.getStepParameters();

      assertThat(params.getEnvironmentRef().getValue()).as("should set environmentRef").isEqualTo("my-env-ref");
      assertThat(params.getInfraId().getValue()).as("should set infraId").isEqualTo("my-infra-id");
      assertThat(params.getInfraInputs().getValue()).as("should set infraInputs").containsEntry("key1", "val1");
      assertThat(params.getEnvBranchRef().getValue()).as("should set envBranchRef").isEqualTo("env-branch");
      assertThat(params.getServiceRef().getValue()).as("should set serviceRef as value").isEqualTo("my-service-ref");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddCDInfrastructureNode_whenMultiEnvAndMultiService_shouldSetExpressionParameters() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    deployModuleInfo.put("multiService", "true");
    deployModuleInfo.put("multiEnvironment", "true");
    deployModuleInfo.put("environment", "any-env");
    deployModuleInfo.put("infrastructure", "any-infra");
    deployModuleInfo.put("INFRA_INPUTS", "infra-input");
    deployModuleInfo.put("envBranchRef", "env-branch");
    deployModuleInfo.put("environmentGroup", "my-env-group");
    deployModuleInfo.put("service", "any-service");

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageCDInfraPlanCreatorUtils.addCDInfrastructureNode(
          kryoSerializer, "next-id", "infra-id", deployModuleInfo, false, null);

      PlanNode node = result.get("infra-id").getPlanNode();
      assertThat(node.getStepParameters()).isInstanceOf(UnifiedCDInfraStepParameters.class);
      UnifiedCDInfraStepParameters params = (UnifiedCDInfraStepParameters) node.getStepParameters();

      assertThat(params.getEnvironmentRef().isExpression()).as("environmentRef should be expression").isTrue();
      assertThat(params.getEnvironmentRef().getExpressionValue())
          .as("should use matrix environmentRef")
          .isEqualTo("<+matrix.environmentRef>");
      assertThat(params.getInfraId().isExpression()).as("infraId should be expression").isTrue();
      assertThat(params.getInfraId().getExpressionValue())
          .as("should use matrix infraId")
          .isEqualTo("<+matrix.infraId>");
      assertThat(params.getInfraInputs().isExpression()).as("infraInputs should be expression").isTrue();
      assertThat(params.getEnvBranchRef().isExpression()).as("envBranchRef should be expression").isTrue();
      assertThat(params.getEnvGroupRef().getValue()).as("envGroupRef should be string value").isEqualTo("my-env-group");
      assertThat(params.getServiceRef().isExpression()).as("serviceRef should be expression in multi-service").isTrue();
      assertThat(params.getServiceRef().getExpressionValue())
          .as("should use matrix serviceRef")
          .isEqualTo("<+matrix.serviceRef>");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddCDInfrastructureNode_whenNoServiceKey_shouldNotSetServiceRef() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    deployModuleInfo.put("environment", "env-ref");

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageCDInfraPlanCreatorUtils.addCDInfrastructureNode(
          kryoSerializer, "next-id", "infra-id", deployModuleInfo, false, null);

      PlanNode node = result.get("infra-id").getPlanNode();
      assertThat(node.getStepParameters()).isInstanceOf(UnifiedCDInfraStepParameters.class);
      UnifiedCDInfraStepParameters params = (UnifiedCDInfraStepParameters) node.getStepParameters();

      assertThat(params.getServiceRef()).as("serviceRef should be null when no service key").isNull();
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddCDInfrastructureNode_whenIsStepInsideRollback_shouldSetRollbackWhenCondition() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    deployModuleInfo.put("environment", "env-ref");

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(null, true)).thenReturn("<+OnRollback>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageCDInfraPlanCreatorUtils.addCDInfrastructureNode(
          kryoSerializer, "next-id", "infra-id", deployModuleInfo, true, null);

      PlanNode node = result.get("infra-id").getPlanNode();
      assertThat(node.getWhenCondition()).as("should set rollback when condition").isEqualTo("<+OnRollback>");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddCDInfrastructureNode_shouldSetAdviserForRollbackExecutionModes() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    deployModuleInfo.put("environment", "env-ref");

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageCDInfraPlanCreatorUtils.addCDInfrastructureNode(
          kryoSerializer, "next-id", "infra-id", deployModuleInfo, false, null);

      PlanNode node = result.get("infra-id").getPlanNode();
      assertThat(node.getAdvisorObtainmentsForExecutionMode().get(ExecutionMode.PIPELINE_ROLLBACK))
          .as("should have adviser for PIPELINE_ROLLBACK")
          .isNotEmpty();
      assertThat(node.getAdvisorObtainmentsForExecutionMode().get(ExecutionMode.POST_EXECUTION_ROLLBACK))
          .as("should have adviser for POST_EXECUTION_ROLLBACK")
          .isNotEmpty();
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddCDInfrastructureNode_whenEnvVarsProvided_shouldSetEnvVarsInParameters() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    deployModuleInfo.put("environment", "env-ref");

    Map<String, ParameterField<JsonNode>> envVarsMap = new HashMap<>();
    ParameterField<Map<String, ParameterField<JsonNode>>> envVars = ParameterField.createValueField(envVarsMap);

    try (MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageCDInfraPlanCreatorUtils.addCDInfrastructureNode(
          kryoSerializer, "next-id", "infra-id", deployModuleInfo, false, envVars);

      PlanNode node = result.get("infra-id").getPlanNode();
      assertThat(node.getStepParameters()).isInstanceOf(UnifiedCDInfraStepParameters.class);
      UnifiedCDInfraStepParameters params = (UnifiedCDInfraStepParameters) node.getStepParameters();
      assertThat(params.getEnvVars()).as("should set envVars in parameters").isEqualTo(envVars);
    }
  }
}

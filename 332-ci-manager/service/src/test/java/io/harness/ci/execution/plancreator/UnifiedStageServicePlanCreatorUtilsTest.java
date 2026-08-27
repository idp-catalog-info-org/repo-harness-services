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
import io.harness.ci.execution.plancreator.V1.UnifiedStageServicePlanCreatorUtils;
import io.harness.ci.states.V1.cd.ArtifactsStep;
import io.harness.ci.states.V1.cd.ConfigFilesStep;
import io.harness.ci.states.V1.cd.ManifestsStep;
import io.harness.ci.states.V1.cd.ServiceHooksStep;
import io.harness.ci.states.V1.cd.UnifiedServiceStep;
import io.harness.ci.states.V1.cd.UnifiedServiceStepParameters;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.section.chain.SectionChainStep;
import io.harness.when.utils.v1.RunInfoUtilsV1;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class UnifiedStageServicePlanCreatorUtilsTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private KryoSerializer kryoSerializer;

  @Before
  public void setUp() {
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void
  testAddServiceNode_whenEmptyDeployModuleInfo_serviceHooksDisabled_shouldCreateServiceNodeWithChildrenOnly() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    String nextNodeID = "next-node-id";
    String serviceNodeID = "service-node-id";

    try (MockedStatic<UUIDGenerator> uuidMock = mockStatic(UUIDGenerator.class);
         MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      AtomicInteger uuidCounter = new AtomicInteger(0);
      uuidMock.when(UUIDGenerator::generateUuid).thenAnswer(inv -> "generated-uuid-" + uuidCounter.getAndIncrement());
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageServicePlanCreatorUtils.addServiceNode(
          kryoSerializer, nextNodeID, serviceNodeID, deployModuleInfo, false, null, false);

      assertThat(result).as("should contain service node + manifests + artifacts + configFiles").hasSize(4);
      assertThat(result.containsKey(serviceNodeID)).as("should contain service node").isTrue();

      PlanNode serviceNode = result.get(serviceNodeID).getPlanNode();
      assertThat(serviceNode.getStepType())
          .as("should be UnifiedServiceStep type")
          .isEqualTo(UnifiedServiceStep.STEP_TYPE);
      assertThat(serviceNode.getName()).as("should have Service name").isEqualTo("Service");
      assertThat(serviceNode.getIdentifier()).as("should have service identifier").isEqualTo("service");

      UnifiedServiceStepParameters params = (UnifiedServiceStepParameters) serviceNode.getStepParameters();
      assertThat(params.getChildrenNodeIds()).as("should have 3 children node ids").hasSize(3);
      assertThat(params.getServiceRef()).as("serviceRef should be null when empty map").isNull();
      assertThat(params.getEnvironmentRef()).as("environmentRef should be null when empty map").isNull();

      boolean hasManifests =
          result.values().stream().anyMatch(r -> ManifestsStep.STEP_TYPE.equals(r.getPlanNode().getStepType()));
      assertThat(hasManifests).as("should contain a plain manifests child node (no hook section)").isTrue();
      boolean hasSectionChain =
          result.values().stream().anyMatch(r -> SectionChainStep.STEP_TYPE.equals(r.getPlanNode().getStepType()));
      assertThat(hasSectionChain).as("should not contain a manifest section chain node when hooks disabled").isFalse();
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void
  testAddServiceNode_whenEmptyDeployModuleInfo_serviceHooksEnabled_shouldCreateManifestSectionWithHookNodes() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    String nextNodeID = "next-node-id";
    String serviceNodeID = "service-node-id";

    try (MockedStatic<UUIDGenerator> uuidMock = mockStatic(UUIDGenerator.class);
         MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      AtomicInteger uuidCounter = new AtomicInteger(0);
      uuidMock.when(UUIDGenerator::generateUuid).thenAnswer(inv -> "generated-uuid-" + uuidCounter.getAndIncrement());
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageServicePlanCreatorUtils.addServiceNode(
          kryoSerializer, nextNodeID, serviceNodeID, deployModuleInfo, false, null, true);

      assertThat(result)
          .as("should contain service node + manifest section node + preHooks + manifests + postHooks + artifacts + "
              + "configFiles")
          .hasSize(7);
      assertThat(result.containsKey(serviceNodeID)).as("should contain service node").isTrue();

      UnifiedServiceStepParameters params =
          (UnifiedServiceStepParameters) result.get(serviceNodeID).getPlanNode().getStepParameters();
      assertThat(params.getChildrenNodeIds()).as("should have 3 children node ids").hasSize(3);

      boolean hasSectionChain =
          result.values().stream().anyMatch(r -> SectionChainStep.STEP_TYPE.equals(r.getPlanNode().getStepType()));
      assertThat(hasSectionChain).as("should contain a manifest section chain node").isTrue();

      long hookNodeCount = result.values()
                               .stream()
                               .filter(r -> ServiceHooksStep.STEP_TYPE.equals(r.getPlanNode().getStepType()))
                               .count();
      assertThat(hookNodeCount).as("should contain pre and post fetch hook nodes").isEqualTo(2);
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddServiceNode_whenSingleServiceAndSingleEnv_shouldSetParametersFromMap() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    deployModuleInfo.put("service", "my-service-ref");
    deployModuleInfo.put("SERVICE_INPUTS", Map.of("key1", "val1"));
    deployModuleInfo.put("svcBranchRef", "feature-branch");
    deployModuleInfo.put("environment", "my-env-ref");
    deployModuleInfo.put("infrastructure", "my-infra-id");
    deployModuleInfo.put("INFRA_INPUTS", Map.of("infraKey", "infraVal"));
    deployModuleInfo.put("envOverridesInputs", Map.of("envKey", "envVal"));
    deployModuleInfo.put("svcOverridesInputs", Map.of("svcKey", "svcVal"));
    deployModuleInfo.put("envBranchRef", "env-branch");

    try (MockedStatic<UUIDGenerator> uuidMock = mockStatic(UUIDGenerator.class);
         MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      AtomicInteger uuidCounter = new AtomicInteger(0);
      uuidMock.when(UUIDGenerator::generateUuid).thenAnswer(inv -> "generated-uuid-" + uuidCounter.getAndIncrement());
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageServicePlanCreatorUtils.addServiceNode(
          kryoSerializer, "next-id", "svc-id", deployModuleInfo, false, null, false);

      PlanNode serviceNode = result.get("svc-id").getPlanNode();
      UnifiedServiceStepParameters params = (UnifiedServiceStepParameters) serviceNode.getStepParameters();

      assertThat(params.getServiceRef().getValue()).as("should set serviceRef").isEqualTo("my-service-ref");
      assertThat(params.getBranch().getValue()).as("should set branch").isEqualTo("feature-branch");
      assertThat(params.getServiceInputs().getValue()).as("should set serviceInputs").containsEntry("key1", "val1");
      assertThat(params.getEnvironmentRef().getValue()).as("should set environmentRef").isEqualTo("my-env-ref");
      assertThat(params.getInfraId().getValue()).as("should set infraId").isEqualTo("my-infra-id");
      assertThat(params.getInfraInputs().getValue()).as("should set infraInputs").containsEntry("infraKey", "infraVal");
      assertThat(params.getEnvOverridesInputs().getValue())
          .as("should set envOverridesInputs")
          .containsEntry("envKey", "envVal");
      assertThat(params.getSvcOverridesInputs().getValue())
          .as("should set svcOverridesInputs")
          .containsEntry("svcKey", "svcVal");
      assertThat(params.getEnvBranchRef().getValue()).as("should set envBranchRef").isEqualTo("env-branch");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddServiceNode_whenMultiServiceAndMultiEnv_shouldSetExpressionParameters() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    deployModuleInfo.put("service", "any-service");
    deployModuleInfo.put("multiService", "true");
    deployModuleInfo.put("multiEnvironment", "true");
    deployModuleInfo.put("SERVICE_INPUTS", "some-input");
    deployModuleInfo.put("svcBranchRef", "svc-branch");
    deployModuleInfo.put("environment", "any-env");
    deployModuleInfo.put("infrastructure", "any-infra");
    deployModuleInfo.put("INFRA_INPUTS", "infra-input");
    deployModuleInfo.put("envOverridesInputs", "env-overrides");
    deployModuleInfo.put("svcOverridesInputs", "svc-overrides");
    deployModuleInfo.put("envBranchRef", "env-branch");
    deployModuleInfo.put("environmentGroup", "my-env-group");

    try (MockedStatic<UUIDGenerator> uuidMock = mockStatic(UUIDGenerator.class);
         MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      AtomicInteger uuidCounter = new AtomicInteger(0);
      uuidMock.when(UUIDGenerator::generateUuid).thenAnswer(inv -> "generated-uuid-" + uuidCounter.getAndIncrement());
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageServicePlanCreatorUtils.addServiceNode(
          kryoSerializer, "next-id", "svc-id", deployModuleInfo, false, null, false);

      PlanNode serviceNode = result.get("svc-id").getPlanNode();
      UnifiedServiceStepParameters params = (UnifiedServiceStepParameters) serviceNode.getStepParameters();

      assertThat(params.getServiceRef().isExpression()).as("serviceRef should be expression in multi-service").isTrue();
      assertThat(params.getServiceRef().getExpressionValue())
          .as("should use matrix serviceRef expression")
          .isEqualTo("<+matrix.serviceRef>");
      assertThat(params.getServiceInputs().isExpression()).as("serviceInputs should be expression").isTrue();
      assertThat(params.getBranch().isExpression()).as("branch should be expression in multi-service").isTrue();
      assertThat(params.getBranch().getExpressionValue())
          .as("should use matrix svcBranchRef expression")
          .isEqualTo("<+matrix.svcBranchRef>");
      assertThat(params.getEnvironmentRef().isExpression())
          .as("environmentRef should be expression in multi-env")
          .isTrue();
      assertThat(params.getInfraId().isExpression()).as("infraId should be expression in multi-env").isTrue();
      assertThat(params.getInfraInputs().isExpression()).as("infraInputs should be expression in multi-env").isTrue();
      assertThat(params.getEnvOverridesInputs().isExpression())
          .as("envOverridesInputs should be expression in multi-env")
          .isTrue();
      assertThat(params.getSvcOverridesInputs().isExpression())
          .as("svcOverridesInputs should be expression in multi-env")
          .isTrue();
      assertThat(params.getEnvBranchRef().isExpression()).as("envBranchRef should be expression in multi-env").isTrue();
      assertThat(params.getEnvGroupRef().getValue())
          .as("envGroupRef should be set as string param")
          .isEqualTo("my-env-group");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddServiceNode_shouldCreateChildrenNodesForManifestsArtifactsAndConfigFiles() {
    Map<String, Object> deployModuleInfo = new HashMap<>();

    try (MockedStatic<UUIDGenerator> uuidMock = mockStatic(UUIDGenerator.class);
         MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      AtomicInteger uuidCounter = new AtomicInteger(0);
      uuidMock.when(UUIDGenerator::generateUuid).thenAnswer(inv -> "child-uuid-" + uuidCounter.getAndIncrement());
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageServicePlanCreatorUtils.addServiceNode(
          kryoSerializer, "next-id", "svc-id", deployModuleInfo, false, null, false);

      assertThat(result).as("service node and three child nodes").hasSize(4);
      boolean hasManifests = false;
      boolean hasArtifacts = false;
      boolean hasConfigFiles = false;
      for (PlanCreationResponse response : result.values()) {
        PlanNode node = response.getPlanNode();
        if (node.getStepType().equals(ManifestsStep.STEP_TYPE)) {
          hasManifests = true;
          assertThat(node.getName()).as("manifests node name").isEqualTo("Manifests");
          assertThat(node.getIdentifier()).as("manifests node identifier").isEqualTo("manifests");
        } else if (node.getStepType().equals(ArtifactsStep.STEP_TYPE)) {
          hasArtifacts = true;
          assertThat(node.getName()).as("artifacts node name").isEqualTo("Artifacts");
          assertThat(node.getIdentifier()).as("artifacts node identifier").isEqualTo("artifacts");
        } else if (node.getStepType().equals(ConfigFilesStep.STEP_TYPE)) {
          hasConfigFiles = true;
          assertThat(node.getName()).as("config files node name").isEqualTo("ConfigFiles");
          assertThat(node.getIdentifier()).as("config files node identifier").isEqualTo("configFiles");
        }
      }
      assertThat(hasManifests).as("should contain manifests child node").isTrue();
      assertThat(hasArtifacts).as("should contain artifacts child node").isTrue();
      assertThat(hasConfigFiles).as("should contain config files child node").isTrue();
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddServiceNode_whenIsStepInsideRollback_shouldSetRollbackWhenCondition() {
    Map<String, Object> deployModuleInfo = new HashMap<>();

    try (MockedStatic<UUIDGenerator> uuidMock = mockStatic(UUIDGenerator.class);
         MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      AtomicInteger uuidCounter = new AtomicInteger(0);
      uuidMock.when(UUIDGenerator::generateUuid).thenAnswer(inv -> "generated-uuid-" + uuidCounter.getAndIncrement());
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(null, true)).thenReturn("<+OnRollback>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageServicePlanCreatorUtils.addServiceNode(
          kryoSerializer, "next-id", "svc-id", deployModuleInfo, true, null, false);

      PlanNode serviceNode = result.get("svc-id").getPlanNode();
      assertThat(serviceNode.getWhenCondition()).as("should pass rollback when condition").isEqualTo("<+OnRollback>");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddServiceNode_whenEnvVarsProvided_shouldSetEnvVarsInParameters() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    deployModuleInfo.put("service", "svc-ref");

    Map<String, ParameterField<JsonNode>> envVarsMap = new HashMap<>();
    ParameterField<Map<String, ParameterField<JsonNode>>> envVars = ParameterField.createValueField(envVarsMap);

    try (MockedStatic<UUIDGenerator> uuidMock = mockStatic(UUIDGenerator.class);
         MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      AtomicInteger uuidCounter = new AtomicInteger(0);
      uuidMock.when(UUIDGenerator::generateUuid).thenAnswer(inv -> "generated-uuid-" + uuidCounter.getAndIncrement());
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageServicePlanCreatorUtils.addServiceNode(
          kryoSerializer, "next-id", "svc-id", deployModuleInfo, false, envVars, false);

      PlanNode serviceNode = result.get("svc-id").getPlanNode();
      UnifiedServiceStepParameters params = (UnifiedServiceStepParameters) serviceNode.getStepParameters();
      assertThat(params.getEnvVars()).as("should set envVars in parameters").isEqualTo(envVars);
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddServiceNode_whenNoServiceKeyInMap_shouldNotSetServiceOrEnvParams() {
    Map<String, Object> deployModuleInfo = new HashMap<>();
    deployModuleInfo.put("someOtherKey", "value");

    Map<String, ParameterField<JsonNode>> envVarsMap = new HashMap<>();
    ParameterField<Map<String, ParameterField<JsonNode>>> envVars = ParameterField.createValueField(envVarsMap);

    try (MockedStatic<UUIDGenerator> uuidMock = mockStatic(UUIDGenerator.class);
         MockedStatic<RunInfoUtilsV1> runInfoMock = mockStatic(RunInfoUtilsV1.class)) {
      AtomicInteger uuidCounter = new AtomicInteger(0);
      uuidMock.when(UUIDGenerator::generateUuid).thenAnswer(inv -> "generated-uuid-" + uuidCounter.getAndIncrement());
      runInfoMock.when(() -> RunInfoUtilsV1.getStepWhenCondition(any(), any(Boolean.class)))
          .thenReturn("<+OnPipelineSuccess>");

      LinkedHashMap<String, PlanCreationResponse> result = UnifiedStageServicePlanCreatorUtils.addServiceNode(
          kryoSerializer, "next-id", "svc-id", deployModuleInfo, false, envVars, false);

      PlanNode serviceNode = result.get("svc-id").getPlanNode();
      UnifiedServiceStepParameters params = (UnifiedServiceStepParameters) serviceNode.getStepParameters();
      assertThat(params.getServiceRef()).as("serviceRef should be null without service key").isNull();
      assertThat(params.getEnvironmentRef()).as("environmentRef should be null without service key").isNull();
      assertThat(params.getEnvVars())
          .as("envVars is always set from the parameter, regardless of service key")
          .isEqualTo(envVars);
    }
  }
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.steps;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.execution.plancreator.V1.GitClonePlanCreator;
import io.harness.ci.execution.plancreator.V1.InitializeStepPlanCreatorV1;
import io.harness.ci.execution.plancreator.V1.RenderingPlanCreator;
import io.harness.ci.execution.plancreator.V1.TemplatingPlanCreator;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.plan.creator.step.v1.PlanCreatorEnvVarHelper;
import io.harness.ci.states.V1.cd.ServiceHookTaskHelper;
import io.harness.exception.InvalidYamlException;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.unified.depoloymentfreeze.NgDeploymentFreezeResourceClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CIStepsPlanCreatorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private GitClonePlanCreator gitClonePlanCreator;
  @Mock private InitializeStepPlanCreatorV1 initializeStepPlanCreatorV1;
  @Mock private CIPlanCreatorUtils ciPlanCreatorUtils;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private RenderingPlanCreator renderingPlanCreator;
  @Mock private TemplatingPlanCreator templatingPlanCreator;
  @Mock private PlanCreatorEnvVarHelper planCreatorEnvVarHelper;
  @Mock private NGSettingsClient settingsClient;
  @Mock private AccessControlClient accessControlClient;
  @Mock private NgDeploymentFreezeResourceClient ngDeploymentFreezeResourceClient;
  @Mock private ServiceHookTaskHelper serviceHookTaskHelper;

  @InjectMocks private CIStepsPlanCreator ciStepsPlanCreator;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = ciStepsPlanCreator.getSupportedTypes();

    assertThat(supportedTypes).as("Supported types should not be null").isNotNull();
    assertThat(supportedTypes).as("Should contain STEPS key").containsKey(YAMLFieldNameConstants.STEPS);
    assertThat(supportedTypes.get(YAMLFieldNameConstants.STEPS))
        .as("Should support ANY_TYPE for steps")
        .contains(PlanCreatorUtils.ANY_TYPE);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions() {
    Set<String> versions = ciStepsPlanCreator.getSupportedYamlVersions();

    assertThat(versions).as("Should return non-empty set").isNotEmpty();
    assertThat(versions).as("Should support V1 yaml version").contains(HarnessYamlVersion.V1);
    assertThat(versions).as("Should only support V1").hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldObject() {
    YamlField field = mock(YamlField.class);
    YamlField result = ciStepsPlanCreator.getFieldObject(field);

    assertThat(result).as("getFieldObject should return the same field passed in").isSameAs(field);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode_insideGroup() {
    YamlField config = mock(YamlField.class);
    when(config.getUuid()).thenReturn("test-uuid");

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder()
                                                     .putData(PlanCreatorConstants.IS_STEPS_INSIDE_GROUP,
                                                         HarnessValue.newBuilder().setBoolValue(true).build())
                                                     .build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    List<String> childrenNodeIds = List.of("child-1");
    PlanNode planNode = ciStepsPlanCreator.createPlanForParentNode(ctx, config, childrenNodeIds);

    assertThat(planNode).as("Plan node should not be null").isNotNull();
    assertThat(planNode.getUuid()).as("UUID should match config UUID").isEqualTo("test-uuid");
    assertThat(planNode.getIdentifier()).as("Identifier should be 'steps'").isEqualTo(YAMLFieldNameConstants.STEPS);
    assertThat(planNode.getAdvisorObtainmentsForExecutionMode())
        .as("Inside group should not have rollback advisers")
        .isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode_notInsideGroup() {
    YamlField config = mock(YamlField.class);
    when(config.getUuid()).thenReturn("test-uuid");

    Dependency dependency =
        Dependency.newBuilder()
            .setNodeMetadata(HarnessStruct.newBuilder()
                                 .putData(PlanCreatorConstants.IS_STEPS_INSIDE_GROUP,
                                     HarnessValue.newBuilder().setBoolValue(false).build())
                                 .build())
            .setParentInfo(HarnessStruct.newBuilder()
                               .putData(PlanCreatorConstants.STAGE_ID,
                                   HarnessValue.newBuilder().setStringValue("stage-node-id").build())
                               .build())
            .build();

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[] {1, 2, 3});

    List<String> childrenNodeIds = List.of("child-1");
    PlanNode planNode = ciStepsPlanCreator.createPlanForParentNode(ctx, config, childrenNodeIds);

    assertThat(planNode).as("Plan node should not be null").isNotNull();
    assertThat(planNode.getUuid()).as("UUID should match config UUID").isEqualTo("test-uuid");
    assertThat(planNode.getAdvisorObtainmentsForExecutionMode())
        .as("Not inside group should have rollback advisers for execution modes")
        .isNotEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepRunNode_whenNull() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("getStepRunNode", YamlField.class);
    method.setAccessible(true);

    JsonNode result = (JsonNode) method.invoke(ciStepsPlanCreator, (YamlField) null);

    assertThat(result).as("Null input should return null").isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepRunNode_whenNoRunField() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("getStepRunNode", YamlField.class);
    method.setAccessible(true);

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.put("action", "something");

    YamlNode yamlNode = mock(YamlNode.class);
    when(yamlNode.getCurrJsonNode()).thenReturn(stepNode);

    YamlField field = mock(YamlField.class);
    when(field.getNode()).thenReturn(yamlNode);

    JsonNode result = (JsonNode) method.invoke(ciStepsPlanCreator, field);

    assertThat(result).as("No run field should return null").isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepRunNode_whenHasRunField() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("getStepRunNode", YamlField.class);
    method.setAccessible(true);

    ObjectNode runNode = objectMapper.createObjectNode();
    runNode.put("script", "echo hello");

    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("run", runNode);

    YamlNode yamlNode = mock(YamlNode.class);
    when(yamlNode.getCurrJsonNode()).thenReturn(stepNode);

    YamlField field = mock(YamlField.class);
    when(field.getNode()).thenReturn(yamlNode);

    JsonNode result = (JsonNode) method.invoke(ciStepsPlanCreator, field);

    assertThat(result).as("Should return run node").isNotNull();
    assertThat(result.get("script").asText()).as("Script should match").isEqualTo("echo hello");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsRenderingStep_whenNull() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("isRenderingStep", JsonNode.class);
    method.setAccessible(true);

    boolean result = (boolean) method.invoke(ciStepsPlanCreator, (JsonNode) null);

    assertThat(result).as("Null env node should not be a rendering step").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsRenderingStep_whenNoPluginKey() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("isRenderingStep", JsonNode.class);
    method.setAccessible(true);

    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("MY_VAR", "value");

    boolean result = (boolean) method.invoke(ciStepsPlanCreator, envNode);

    assertThat(result).as("Env node without plugin rendering key should not be rendering step").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsRenderingStep_whenPluginRenderingTrue() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("isRenderingStep", JsonNode.class);
    method.setAccessible(true);

    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("PLUGIN_RENDERING_STEP", true);

    boolean result = (boolean) method.invoke(ciStepsPlanCreator, envNode);

    assertThat(result).as("Env node with PLUGIN_RENDERING_STEP=true should be rendering step").isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsRenderingStep_whenPluginRenderingFalse() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("isRenderingStep", JsonNode.class);
    method.setAccessible(true);

    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("PLUGIN_RENDERING_STEP", false);

    boolean result = (boolean) method.invoke(ciStepsPlanCreator, envNode);

    assertThat(result).as("Env node with PLUGIN_RENDERING_STEP=false should not be rendering step").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepEnvNode_whenRunNodeIsNull() {
    JsonNode result = ciStepsPlanCreator.getStepEnvNode(null);

    assertThat(result).as("Null run node should return null env node").isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepEnvNode_whenRunNodeHasNoEnv() {
    ObjectNode runNode = objectMapper.createObjectNode();
    runNode.put("script", "echo hello");

    JsonNode result = ciStepsPlanCreator.getStepEnvNode(runNode);

    assertThat(result).as("Run node without env should return null").isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepEnvNode_whenRunNodeHasEnv() {
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.put("MY_VAR", "my_value");

    ObjectNode runNode = objectMapper.createObjectNode();
    runNode.set("env", envNode);

    JsonNode result = ciStepsPlanCreator.getStepEnvNode(runNode);

    assertThat(result).as("Run node with env should return env node").isNotNull();
    assertThat(result.get("MY_VAR").asText()).as("Env variable value should match").isEqualTo("my_value");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateTemplateUses_withInvalidTypes() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "validateTemplateUses", io.harness.cd.beans.ModuleSpecificMetadata.class, Map.class);
    method.setAccessible(true);

    io.harness.cd.beans.ModuleSpecificMetadata metadata = io.harness.cd.beans.ModuleSpecificMetadata.builder()
                                                              .modules(Set.of(io.harness.pms.yaml.TemplateType.TEST))
                                                              .build();

    Map<String, String> templateToFirstStepId = new java.util.HashMap<>();
    templateToFirstStepId.put(io.harness.pms.yaml.TemplateType.DEPLOY.getName(), "step-1");

    assertThatThrownBy(() -> {
      try {
        method.invoke(ciStepsPlanCreator, metadata, templateToFirstStepId);
      } catch (java.lang.reflect.InvocationTargetException e) {
        throw e.getCause();
      }
    })
        .as("Should throw InvalidYamlException for invalid template types")
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Invalid template types used");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateTemplateUses_whenTemplateMapEmpty() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "validateTemplateUses", io.harness.cd.beans.ModuleSpecificMetadata.class, Map.class);
    method.setAccessible(true);

    io.harness.cd.beans.ModuleSpecificMetadata metadata =
        io.harness.cd.beans.ModuleSpecificMetadata.builder().modules(Collections.emptySet()).build();

    method.invoke(ciStepsPlanCreator, metadata, Collections.emptyMap());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetNextNodeId_firstNonBlank() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("getNextNodeId", String[].class);
    method.setAccessible(true);

    String result = (String) method.invoke(ciStepsPlanCreator, (Object) new String[] {null, "", "node-2", "node-3"});

    assertThat(result).as("Should return first non-blank value").isEqualTo("node-2");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetNextNodeId_allBlank() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("getNextNodeId", String[].class);
    method.setAccessible(true);

    String result = (String) method.invoke(ciStepsPlanCreator, (Object) new String[] {null, "", "  "});

    assertThat(result).as("All blank values should return null").isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetNextNodeId_singleValue() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("getNextNodeId", String[].class);
    method.setAccessible(true);

    String result = (String) method.invoke(ciStepsPlanCreator, (Object) new String[] {"only-node"});

    assertThat(result).as("Single non-blank value should be returned").isEqualTo("only-node");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testMapModuleTemplateTypeToStepPosition_emptySteps() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("mapModuleTemplateTypeToStepPosition", List.class);
    method.setAccessible(true);

    Map<String, String> result = (Map<String, String>) method.invoke(ciStepsPlanCreator, Collections.emptyList());

    assertThat(result).as("Empty steps should return empty map").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testMapModuleTemplateTypeToStepPosition_nullSteps() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("mapModuleTemplateTypeToStepPosition", List.class);
    method.setAccessible(true);

    Map<String, String> result = (Map<String, String>) method.invoke(ciStepsPlanCreator, (List<YamlField>) null);

    assertThat(result).as("Null steps should return empty map").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetModuleSpecificMetadata() throws Exception {
    String yaml = "---\nsteps:\n  - run:\n      script: echo hello\n";
    YamlField config = YamlUtils.injectUuidInYamlField(yaml);

    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("getModuleSpecificMetadata", YamlField.class);
    method.setAccessible(true);

    io.harness.cd.beans.ModuleSpecificMetadata result =
        (io.harness.cd.beans.ModuleSpecificMetadata) method.invoke(ciStepsPlanCreator, config);

    assertThat(result).as("Module specific metadata should not be null").isNotNull();
    assertThat(result.getModules()).as("Modules set should not be null").isNotNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsChildOfStage_whenParentIsUnified() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("isChildOfStage", PlanCreationContext.class);
    method.setAccessible(true);

    YamlNode parentNode = mock(YamlNode.class);
    when(parentNode.getType()).thenReturn("unified");

    YamlNode currentNode = mock(YamlNode.class);
    when(currentNode.getParentNode()).thenReturn(parentNode);

    YamlField currentField = mock(YamlField.class);
    when(currentField.getNode()).thenReturn(currentNode);

    PlanCreationContext ctx = PlanCreationContext.builder().currentField(currentField).build();

    boolean result = (boolean) method.invoke(ciStepsPlanCreator, ctx);

    assertThat(result).as("Should return true when parent type is 'unified'").isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsChildOfStage_whenParentIsNotUnified() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("isChildOfStage", PlanCreationContext.class);
    method.setAccessible(true);

    YamlNode parentNode = mock(YamlNode.class);
    when(parentNode.getType()).thenReturn("step_group");

    YamlNode currentNode = mock(YamlNode.class);
    when(currentNode.getParentNode()).thenReturn(parentNode);

    YamlField currentField = mock(YamlField.class);
    when(currentField.getNode()).thenReturn(currentNode);

    PlanCreationContext ctx = PlanCreationContext.builder().currentField(currentField).build();

    boolean result = (boolean) method.invoke(ciStepsPlanCreator, ctx);

    assertThat(result).as("Should return false when parent type is not 'unified'").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetRollbackSteps_notRollbackMode_noParent() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "getRollbackSteps", PlanCreationContext.class, YamlField.class, boolean.class);
    method.setAccessible(true);

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder().build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    YamlNode configNode = mock(YamlNode.class);
    when(configNode.getParentNode()).thenReturn(null);
    YamlField config = mock(YamlField.class);
    when(config.getNode()).thenReturn(configNode);

    List<YamlField> result = (List<YamlField>) method.invoke(null, ctx, config, true);

    assertThat(result).as("Should return empty list when parent node is null").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetRollbackSteps_notRollbackMode_notStageChild() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "getRollbackSteps", PlanCreationContext.class, YamlField.class, boolean.class);
    method.setAccessible(true);

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder().build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    YamlNode parentNode = mock(YamlNode.class);
    YamlNode configNode = mock(YamlNode.class);
    when(configNode.getParentNode()).thenReturn(parentNode);
    when(parentNode.getField(YAMLFieldNameConstants.ROLLBACK_STEPS_V1)).thenReturn(null);
    YamlField config = mock(YamlField.class);
    when(config.getNode()).thenReturn(configNode);

    List<YamlField> result = (List<YamlField>) method.invoke(null, ctx, config, false);

    assertThat(result).as("Should return empty list when not stage child").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetPreDeployPlanCreationResult_whenTemplateBasedInfoEmpty() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "getPreDeployPlanCreationResult", io.harness.cd.beans.TemplateTypeBasedPlanCreatorData.class, String.class);
    method.setAccessible(true);

    io.harness.cd.beans.TemplateTypeBasedPlanCreatorData data =
        io.harness.cd.beans.TemplateTypeBasedPlanCreatorData.builder()
            .ctx(PlanCreationContext.builder().build())
            .templateBasedInfo(Collections.emptyMap())
            .build();

    java.util.Optional<?> result = (java.util.Optional<?>) method.invoke(ciStepsPlanCreator, data, "next-step");

    assertThat(result).as("Empty template info should return empty optional").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetIacmPlanCreationResult_whenTemplateBasedInfoEmpty() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "getIacmPlanCreationResult", io.harness.cd.beans.TemplateTypeBasedPlanCreatorData.class, String.class);
    method.setAccessible(true);

    io.harness.cd.beans.TemplateTypeBasedPlanCreatorData data =
        io.harness.cd.beans.TemplateTypeBasedPlanCreatorData.builder()
            .ctx(PlanCreationContext.builder().build())
            .templateBasedInfo(Collections.emptyMap())
            .build();

    java.util.Optional<?> result = (java.util.Optional<?>) method.invoke(ciStepsPlanCreator, data, "next-step");

    assertThat(result).as("Empty template info should return empty optional for IACM").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetDeployPlanResult_whenNoDeployResult() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("getDeployPlanResult", Map.class);
    method.setAccessible(true);

    Map<String, io.harness.cd.beans.ModuleSpecificPlanCreationResult> templateResults = new java.util.HashMap<>();

    io.harness.cd.beans.DeployPlanCreationResult result =
        (io.harness.cd.beans.DeployPlanCreationResult) method.invoke(ciStepsPlanCreator, templateResults);

    assertThat(result).as("No deploy result in template results should return null").isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsModuleTemplateImplicitStepPositionOverridden_true() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("isModuleTemplateImplicitStepPositionOverridden",
            io.harness.cd.beans.ModuleSpecificMetadata.class, Map.class, io.harness.pms.yaml.TemplateType.class);
    method.setAccessible(true);

    io.harness.pms.yaml.TemplateType templateType = io.harness.pms.yaml.TemplateType.DEPLOY;
    io.harness.cd.beans.ModuleSpecificMetadata metadata =
        io.harness.cd.beans.ModuleSpecificMetadata.builder().modules(Set.of(templateType)).build();
    Map<String, String> templateToStepMap = new java.util.HashMap<>();
    templateToStepMap.put(templateType.getName(), "step-1");

    boolean result = (boolean) method.invoke(ciStepsPlanCreator, metadata, templateToStepMap, templateType);

    assertThat(result).as("Should return true when module contains type and map has key").isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsModuleTemplateImplicitStepPositionOverridden_false() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("isModuleTemplateImplicitStepPositionOverridden",
            io.harness.cd.beans.ModuleSpecificMetadata.class, Map.class, io.harness.pms.yaml.TemplateType.class);
    method.setAccessible(true);

    io.harness.pms.yaml.TemplateType templateType = io.harness.pms.yaml.TemplateType.DEPLOY;
    io.harness.cd.beans.ModuleSpecificMetadata metadata =
        io.harness.cd.beans.ModuleSpecificMetadata.builder().modules(Set.of(templateType)).build();
    Map<String, String> templateToStepMap = Collections.emptyMap();

    boolean result = (boolean) method.invoke(ciStepsPlanCreator, metadata, templateToStepMap, templateType);

    assertThat(result).as("Should return false when map does not contain the template type key").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddDeployPlanCreationResponse_withAllResponses() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "addDeployPlanCreationResponse", LinkedHashMap.class, io.harness.cd.beans.DeployPlanCreationResult.class);
    method.setAccessible(true);

    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    LinkedHashMap<String, PlanCreationResponse> svcResponses = new LinkedHashMap<>();
    svcResponses.put("svc-1", PlanCreationResponse.builder().build());

    LinkedHashMap<String, PlanCreationResponse> infraResponses = new LinkedHashMap<>();
    infraResponses.put("infra-1", PlanCreationResponse.builder().build());

    LinkedHashMap<String, PlanCreationResponse> rcResponses = new LinkedHashMap<>();
    rcResponses.put("rc-1", PlanCreationResponse.builder().build());

    LinkedHashMap<String, PlanCreationResponse> renderingResponses = new LinkedHashMap<>();
    renderingResponses.put("render-1", PlanCreationResponse.builder().build());

    io.harness.cd.beans.DeployPlanCreationResult deployResult = io.harness.cd.beans.DeployPlanCreationResult.builder()
                                                                    .svcPlanCreationResponses(svcResponses)
                                                                    .infraPlanCreationResponses(infraResponses)
                                                                    .rcPlanCreationResponse(rcResponses)
                                                                    .renderingCreationResponse(renderingResponses)
                                                                    .build();

    method.invoke(null, responseMap, deployResult);

    assertThat(responseMap).as("Response map should contain all four responses").hasSize(4);
    assertThat(responseMap).as("Should contain service response").containsKey("svc-1");
    assertThat(responseMap).as("Should contain infra response").containsKey("infra-1");
    assertThat(responseMap).as("Should contain rc response").containsKey("rc-1");
    assertThat(responseMap).as("Should contain rendering response").containsKey("render-1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddDeployPlanCreationResponse_withEmptyResponses() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "addDeployPlanCreationResponse", LinkedHashMap.class, io.harness.cd.beans.DeployPlanCreationResult.class);
    method.setAccessible(true);

    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    io.harness.cd.beans.DeployPlanCreationResult deployResult =
        io.harness.cd.beans.DeployPlanCreationResult.builder().build();

    method.invoke(null, responseMap, deployResult);

    assertThat(responseMap).as("Empty deploy result should leave response map empty").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddIacmPlanCreationResponse_withResponses() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "addIacmPlanCreationResponse", LinkedHashMap.class, io.harness.iacm.beans.IACMPlanCreationResult.class);
    method.setAccessible(true);

    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    LinkedHashMap<String, PlanCreationResponse> iacmResponses = new LinkedHashMap<>();
    iacmResponses.put("iacm-1", PlanCreationResponse.builder().build());

    io.harness.iacm.beans.IACMPlanCreationResult iacmResult = io.harness.iacm.beans.IACMPlanCreationResult.builder()
                                                                  .iacmPlanCreationResponses(iacmResponses)
                                                                  .iacmNodeId("iacm-node")
                                                                  .build();

    method.invoke(null, responseMap, iacmResult);

    assertThat(responseMap).as("Response map should contain IACM responses").hasSize(1);
    assertThat(responseMap).as("Should contain IACM response key").containsKey("iacm-1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testAddIacmPlanCreationResponse_withEmptyResponses() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "addIacmPlanCreationResponse", LinkedHashMap.class, io.harness.iacm.beans.IACMPlanCreationResult.class);
    method.setAccessible(true);

    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    io.harness.iacm.beans.IACMPlanCreationResult iacmResult =
        io.harness.iacm.beans.IACMPlanCreationResult.builder().build();

    method.invoke(null, responseMap, iacmResult);

    assertThat(responseMap).as("Empty IACM result should leave response map empty").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testSetDeployModuleEntitiesIds_withServiceAndInfra() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("setDeployModuleEntitiesIds",
        io.harness.cd.beans.DeployPlanCreationResult.DeployPlanCreationResultBuilder.class, Map.class);
    method.setAccessible(true);

    io.harness.cd.beans.DeployPlanCreationResult.DeployPlanCreationResultBuilder builder =
        io.harness.cd.beans.DeployPlanCreationResult.builder();

    Map<String, Object> entities = new java.util.HashMap<>();
    entities.put(YAMLFieldNameConstants.SERVICE, "svc-ref");
    entities.put(YAMLFieldNameConstants.ENVIRONMENT, "env-ref");
    entities.put(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE, "infra-id");

    method.invoke(null, builder, entities);

    io.harness.cd.beans.DeployPlanCreationResult result = builder.build();
    assertThat(result.getServiceRef()).as("Service ref should be set").isEqualTo("svc-ref");
    assertThat(result.getEnvRef()).as("Env ref should be set").isEqualTo("env-ref");
    assertThat(result.getInfraId()).as("Infra ID should be set").isEqualTo("infra-id");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testSetDeployModuleEntitiesIds_withEmpty() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("setDeployModuleEntitiesIds",
        io.harness.cd.beans.DeployPlanCreationResult.DeployPlanCreationResultBuilder.class, Map.class);
    method.setAccessible(true);

    io.harness.cd.beans.DeployPlanCreationResult.DeployPlanCreationResultBuilder builder =
        io.harness.cd.beans.DeployPlanCreationResult.builder();

    method.invoke(null, builder, Collections.emptyMap());

    io.harness.cd.beans.DeployPlanCreationResult result = builder.build();
    assertThat(result.getServiceRef()).as("Service ref should be null when not set").isNull();
    assertThat(result.getEnvRef()).as("Env ref should be null when not set").isNull();
    assertThat(result.getInfraId()).as("Infra ID should be null when not set").isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetDependencyMetadata_withNextId() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("getDependencyMetadata", PlanCreationContext.class, String.class);
    method.setAccessible(true);

    Dependency dependency =
        Dependency.newBuilder()
            .setNodeMetadata(HarnessStruct.newBuilder()
                                 .putData("someKey", HarnessValue.newBuilder().setStringValue("someValue").build())
                                 .build())
            .setParentInfo(HarnessStruct.newBuilder()
                               .putData("parentKey", HarnessValue.newBuilder().setStringValue("parentVal").build())
                               .build())
            .build();

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    Dependency result = (Dependency) method.invoke(ciStepsPlanCreator, ctx, "next-node-id");

    assertThat(result).as("Dependency should not be null").isNotNull();
    assertThat(result.getNodeMetadata().getDataMap())
        .as("Should contain NEXT_ID in node metadata")
        .containsKey(PlanCreatorConstants.NEXT_ID);
    assertThat(result.getNodeMetadata().getDataMap().get(PlanCreatorConstants.NEXT_ID).getStringValue())
        .as("NEXT_ID value should match provided nextId")
        .isEqualTo("next-node-id");
    assertThat(result.getNodeMetadata().getDataMap()).as("Should contain parent key 'parent'").containsKey("parent");
    assertThat(result.getNodeMetadata().getDataMap().get("parent").getStringValue())
        .as("Parent value should be 'steps'")
        .isEqualTo("steps");
    assertThat(result.getParentInfo().getDataMap())
        .as("Should preserve parent info from context")
        .containsKey("parentKey");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdateNextIdIfFirstStepIsFromTemplate_noMatch() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("updateNextIdIfFirstStepIsFromTemplate", String.class, Map.class,
            io.harness.cd.beans.ModuleTemplatePlanCreationResults.class, String.class);
    method.setAccessible(true);

    Map<String, String> templateTypeToFirstStepIdMap = Collections.emptyMap();
    io.harness.cd.beans.ModuleTemplatePlanCreationResults results =
        io.harness.cd.beans.ModuleTemplatePlanCreationResults.builder()
            .planCreationResults(Collections.emptyMap())
            .build();

    String result = (String) method.invoke(null, "first-step", templateTypeToFirstStepIdMap, results, "next-step");

    assertThat(result).as("No match should return original next step ID").isEqualTo("next-step");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdateNextIdIfFirstStepIsFromTemplate_nullResults() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("updateNextIdIfFirstStepIsFromTemplate", String.class, Map.class,
            io.harness.cd.beans.ModuleTemplatePlanCreationResults.class, String.class);
    method.setAccessible(true);

    Map<String, String> templateTypeToFirstStepIdMap = new java.util.HashMap<>();
    templateTypeToFirstStepIdMap.put("deploy", "first-step");

    String result = (String) method.invoke(null, "first-step", templateTypeToFirstStepIdMap, null, "next-step");

    assertThat(result).as("Null results should return original next step ID").isEqualTo("next-step");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdateNextIdIfFirstStepIsFromTemplate_withMatchingResult() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("updateNextIdIfFirstStepIsFromTemplate", String.class, Map.class,
            io.harness.cd.beans.ModuleTemplatePlanCreationResults.class, String.class);
    method.setAccessible(true);

    String deployName = io.harness.pms.yaml.TemplateType.DEPLOY.getName();

    Map<String, String> templateTypeToFirstStepIdMap = new java.util.HashMap<>();
    templateTypeToFirstStepIdMap.put(deployName, "first-step");

    io.harness.cd.beans.DeployPlanCreationResult deployResult =
        io.harness.cd.beans.DeployPlanCreationResult.builder().serviceNodeID("svc-node-id").build();

    Map<String, io.harness.cd.beans.ModuleSpecificPlanCreationResult> planResults = new java.util.HashMap<>();
    planResults.put(deployName, deployResult);

    io.harness.cd.beans.ModuleTemplatePlanCreationResults results =
        io.harness.cd.beans.ModuleTemplatePlanCreationResults.builder().planCreationResults(planResults).build();

    String result = (String) method.invoke(null, "first-step", templateTypeToFirstStepIdMap, results, "next-step");

    assertThat(result).as("Should return first node ID from matching template result").isEqualTo("svc-node-id");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFailIfProjectIsFrozen_noFreeze() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("failIfProjectIsFrozen", PlanCreationContext.class);
    method.setAccessible(true);

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder().build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    PlanCreationContext ctx =
        PlanCreationContext.builder()
            .dependency(dependency)
            .globalContext("metadata", io.harness.pms.contracts.plan.PlanCreationContextValue.newBuilder().build())
            .build();

    io.harness.freeze.beans.response.ShouldDisableDeploymentFreezeResponseDTO responseDTO =
        io.harness.freeze.beans.response.ShouldDisableDeploymentFreezeResponseDTO.builder()
            .shouldDisable(false)
            .build();

    when(accessControlClient.hasAccess(any(), any(), any())).thenReturn(false);
    when(ngDeploymentFreezeResourceClient.shouldDisableDeployment(any(), any(), any(), any()))
        .thenReturn(retrofit2.Call.class.cast(mock(retrofit2.Call.class)));

    method.invoke(ciStepsPlanCreator, ctx);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFailIfProjectIsFrozen_exceptionHandled() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("failIfProjectIsFrozen", PlanCreationContext.class);
    method.setAccessible(true);

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder().build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    PlanCreationContext ctx =
        PlanCreationContext.builder()
            .dependency(dependency)
            .globalContext("metadata", io.harness.pms.contracts.plan.PlanCreationContextValue.newBuilder().build())
            .build();

    when(accessControlClient.hasAccess(any(), any(), any())).thenThrow(new RuntimeException("access error"));

    method.invoke(ciStepsPlanCreator, ctx);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateGitClonePlanCreator_whenCodebaseIsNull() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("createGitClonePlanCreator", PlanCreationContext.class,
            LinkedHashMap.class, List.class, io.harness.yaml.extended.ci.codebase.CodeBase.class, String.class);
    method.setAccessible(true);

    PlanCreationContext ctx = PlanCreationContext.builder().build();
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    List<io.harness.plancreator.execution.ExecutionWrapperConfig> configs = new java.util.ArrayList<>();

    String result = (String) method.invoke(ciStepsPlanCreator, ctx, responseMap, configs, null, "child-1");

    assertThat(result).as("Should return null when codebase is null").isNull();
    assertThat(responseMap).as("Response map should be empty when codebase is null").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateGitClonePlanCreator_whenCodebaseNotNull() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("createGitClonePlanCreator", PlanCreationContext.class,
            LinkedHashMap.class, List.class, io.harness.yaml.extended.ci.codebase.CodeBase.class, String.class);
    method.setAccessible(true);

    PlanCreationContext ctx = PlanCreationContext.builder().build();
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    List<io.harness.plancreator.execution.ExecutionWrapperConfig> configs = new java.util.ArrayList<>();

    io.harness.yaml.extended.ci.codebase.CodeBase codeBase =
        io.harness.yaml.extended.ci.codebase.CodeBase.builder().build();

    PlanNode planNode =
        PlanNode.builder()
            .uuid("git-clone-uuid")
            .identifier("gitClone")
            .stepType(io.harness.pms.contracts.steps.StepType.newBuilder().setType("gitClone").build())
            .name("gitClone")
            .stepParameters(mock(io.harness.pms.sdk.core.steps.io.StepParameters.class))
            .facilitatorObtainment(
                io.harness.pms.contracts.facilitators.FacilitatorObtainment.newBuilder()
                    .setType(io.harness.pms.contracts.facilitators.FacilitatorType.newBuilder().setType("TASK").build())
                    .build())
            .build();

    PlanCreationResponse planResponse = PlanCreationResponse.builder().planNode(planNode).build();

    ObjectNode jsonNode = objectMapper.createObjectNode();
    jsonNode.put("type", "gitClone");

    when(gitClonePlanCreator.createPlan(any(), any(), anyString()))
        .thenReturn(org.apache.commons.lang3.tuple.Pair.of(planResponse, jsonNode));

    String result = (String) method.invoke(ciStepsPlanCreator, ctx, responseMap, configs, codeBase, "child-1");

    assertThat(result).as("Should return the plan node UUID").isEqualTo("git-clone-uuid");
    assertThat(responseMap).as("Response map should contain the git clone response").hasSize(1);
    assertThat(configs).as("Execution configs should have the git clone config prepended").hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleIACMPlanCreation_withNonEmptyInfo() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "handleIACMPlanCreation", PlanCreationContext.class, String.class, Map.class);
    method.setAccessible(true);

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder().build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    Map<String, Object> iacmInfo = new java.util.HashMap<>();
    iacmInfo.put("iacmKey", "iacmValue");

    try (var mockedStatic =
             org.mockito.Mockito.mockStatic(io.harness.ci.execution.integrationstage.V1.IACMPlanCreatorUtils.class)) {
      LinkedHashMap<String, PlanCreationResponse> iacmResponses = new LinkedHashMap<>();
      iacmResponses.put("iacm-node", PlanCreationResponse.builder().build());

      mockedStatic
          .when(()
                    -> io.harness.ci.execution.integrationstage.V1.IACMPlanCreatorUtils.addIACMNode(
                        eq(kryoSerializer), eq("child-node"), anyString(), eq(iacmInfo), eq(false), eq(ctx)))
          .thenReturn(iacmResponses);

      java.util.Optional<?> result =
          (java.util.Optional<?>) method.invoke(ciStepsPlanCreator, ctx, "child-node", iacmInfo);

      assertThat(result).as("Should return non-empty optional for non-empty IACM info").isPresent();
      io.harness.iacm.beans.IACMPlanCreationResult iacmResult =
          (io.harness.iacm.beans.IACMPlanCreationResult) result.get();
      assertThat(iacmResult.getIacmPlanCreationResponses())
          .as("Should contain IACM plan creation responses")
          .isNotEmpty();
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleIACMPlanCreation_withEmptyInfo() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "handleIACMPlanCreation", PlanCreationContext.class, String.class, Map.class);
    method.setAccessible(true);

    PlanCreationContext ctx = PlanCreationContext.builder().build();

    java.util.Optional<?> result =
        (java.util.Optional<?>) method.invoke(ciStepsPlanCreator, ctx, "child-node", Collections.emptyMap());

    assertThat(result).as("Should return empty optional for empty IACM info").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testMapModuleTemplateTypeToStepPosition_withSteps() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("mapModuleTemplateTypeToStepPosition", List.class);
    method.setAccessible(true);

    String deployName = io.harness.pms.yaml.TemplateType.DEPLOY.getName();

    ObjectNode jsonNode1 = objectMapper.createObjectNode();
    jsonNode1.put(YAMLFieldNameConstants.PARENT_TEMPLATE_TYPE, deployName);

    YamlNode yamlNode1 = mock(YamlNode.class);
    when(yamlNode1.getCurrJsonNode()).thenReturn(jsonNode1);
    when(yamlNode1.getUuid()).thenReturn("step-uuid-1");

    YamlField field1 = mock(YamlField.class);
    when(field1.getNode()).thenReturn(yamlNode1);

    ObjectNode jsonNode2 = objectMapper.createObjectNode();
    jsonNode2.put(YAMLFieldNameConstants.PARENT_TEMPLATE_TYPE, deployName);

    YamlNode yamlNode2 = mock(YamlNode.class);
    when(yamlNode2.getCurrJsonNode()).thenReturn(jsonNode2);
    when(yamlNode2.getUuid()).thenReturn("step-uuid-2");

    YamlField field2 = mock(YamlField.class);
    when(field2.getNode()).thenReturn(yamlNode2);

    Map<String, String> result =
        (Map<String, String>) method.invoke(ciStepsPlanCreator, java.util.Arrays.asList(field1, field2));

    assertThat(result).as("Should map deploy type to first step UUID").containsEntry(deployName, "step-uuid-1");
    assertThat(result).as("Should only contain first occurrence").hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testMapModuleTemplateTypeToStepPosition_withNonCustomType() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("mapModuleTemplateTypeToStepPosition", List.class);
    method.setAccessible(true);

    ObjectNode jsonNode = objectMapper.createObjectNode();
    jsonNode.put(YAMLFieldNameConstants.PARENT_TEMPLATE_TYPE, "non-existing-type");

    YamlNode yamlNode = mock(YamlNode.class);
    when(yamlNode.getCurrJsonNode()).thenReturn(jsonNode);
    when(yamlNode.getUuid()).thenReturn("step-uuid");

    YamlField field = mock(YamlField.class);
    when(field.getNode()).thenReturn(yamlNode);

    Map<String, String> result =
        (Map<String, String>) method.invoke(ciStepsPlanCreator, java.util.Arrays.asList(field));

    assertThat(result).as("Non-custom type should not be included").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testMapModuleTemplateTypeToStepPosition_withNullParentTemplateType() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("mapModuleTemplateTypeToStepPosition", List.class);
    method.setAccessible(true);

    ObjectNode jsonNode = objectMapper.createObjectNode();

    YamlNode yamlNode = mock(YamlNode.class);
    when(yamlNode.getCurrJsonNode()).thenReturn(jsonNode);
    when(yamlNode.getUuid()).thenReturn("step-uuid");

    YamlField field = mock(YamlField.class);
    when(field.getNode()).thenReturn(yamlNode);

    Map<String, String> result =
        (Map<String, String>) method.invoke(ciStepsPlanCreator, java.util.Arrays.asList(field));

    assertThat(result).as("Steps without parent_template_type should be excluded").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetInfrastructure() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("getInfrastructure", PlanCreationContext.class);
    method.setAccessible(true);

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder().build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    io.harness.beans.yaml.extended.infrastrucutre.Infrastructure infra =
        mock(io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.class);
    when(ciPlanCreatorUtils.getDeserializedObjectFromDependency(eq(dependency), eq("infrastructure")))
        .thenReturn(java.util.Optional.of(infra));

    io.harness.beans.yaml.extended.infrastrucutre.Infrastructure result =
        (io.harness.beans.yaml.extended.infrastrucutre.Infrastructure) method.invoke(ciStepsPlanCreator, ctx);

    assertThat(result).as("Should return infrastructure").isSameAs(infra);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetInfrastructure_whenEmpty_shouldThrow() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("getInfrastructure", PlanCreationContext.class);
    method.setAccessible(true);

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder().build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    when(ciPlanCreatorUtils.getDeserializedObjectFromDependency(eq(dependency), eq("infrastructure")))
        .thenReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> {
      try {
        method.invoke(ciStepsPlanCreator, ctx);
      } catch (java.lang.reflect.InvocationTargetException e) {
        throw e.getCause();
      }
    })
        .as("Should throw when infrastructure is empty")
        .isInstanceOf(io.harness.exception.InvalidRequestException.class)
        .hasMessageContaining("Infrastructure cannot be empty");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCodeBase_whenPresent() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("getCodeBase", PlanCreationContext.class);
    method.setAccessible(true);

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder().build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    io.harness.yaml.extended.ci.codebase.CodeBase codeBase =
        io.harness.yaml.extended.ci.codebase.CodeBase.builder().build();
    when(ciPlanCreatorUtils.getDeserializedObjectFromDependency(eq(dependency), eq("codebase")))
        .thenReturn(java.util.Optional.of(codeBase));

    io.harness.yaml.extended.ci.codebase.CodeBase result =
        (io.harness.yaml.extended.ci.codebase.CodeBase) method.invoke(ciStepsPlanCreator, ctx);

    assertThat(result).as("Should return codebase when present").isSameAs(codeBase);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetCodeBase_whenEmpty() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("getCodeBase", PlanCreationContext.class);
    method.setAccessible(true);

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder().build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    when(ciPlanCreatorUtils.getDeserializedObjectFromDependency(eq(dependency), eq("codebase")))
        .thenReturn(java.util.Optional.empty());

    io.harness.yaml.extended.ci.codebase.CodeBase result =
        (io.harness.yaml.extended.ci.codebase.CodeBase) method.invoke(ciStepsPlanCreator, ctx);

    assertThat(result).as("Should return null when codebase is empty").isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStageNode() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("getStageNode", PlanCreationContext.class);
    method.setAccessible(true);

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder().build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    io.harness.plancreator.stages.stage.v1.AbstractStageNodeV1 stageNode =
        mock(io.harness.plancreator.stages.stage.v1.AbstractStageNodeV1.class);
    when(ciPlanCreatorUtils.getDeserializedObjectFromDependency(eq(dependency), eq("stageNode")))
        .thenReturn(java.util.Optional.of(stageNode));

    io.harness.plancreator.stages.stage.v1.AbstractStageNodeV1 result =
        (io.harness.plancreator.stages.stage.v1.AbstractStageNodeV1) method.invoke(ciStepsPlanCreator, ctx);

    assertThat(result).as("Should return stage node").isSameAs(stageNode);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStageNode_whenEmpty_shouldThrow() throws Exception {
    java.lang.reflect.Method method =
        CIStepsPlanCreator.class.getDeclaredMethod("getStageNode", PlanCreationContext.class);
    method.setAccessible(true);

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder().build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();

    when(ciPlanCreatorUtils.getDeserializedObjectFromDependency(eq(dependency), eq("stageNode")))
        .thenReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> {
      try {
        method.invoke(ciStepsPlanCreator, ctx);
      } catch (java.lang.reflect.InvocationTargetException e) {
        throw e.getCause();
      }
    })
        .as("Should throw when stage node is empty")
        .isInstanceOf(io.harness.exception.InvalidRequestException.class)
        .hasMessageContaining("IntegrationStageNode cannot be empty");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetIacmPlanCreationResult_withNonEmptyInfo() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod(
        "getIacmPlanCreationResult", io.harness.cd.beans.TemplateTypeBasedPlanCreatorData.class, String.class);
    method.setAccessible(true);

    Dependency dependency = Dependency.newBuilder()
                                .setNodeMetadata(HarnessStruct.newBuilder().build())
                                .setParentInfo(HarnessStruct.newBuilder().build())
                                .build();

    Map<String, Object> templateInfo = new java.util.HashMap<>();
    templateInfo.put("iacmKey", "val");

    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();
    io.harness.cd.beans.TemplateTypeBasedPlanCreatorData data =
        io.harness.cd.beans.TemplateTypeBasedPlanCreatorData.builder().ctx(ctx).templateBasedInfo(templateInfo).build();

    try (var mockedStatic =
             org.mockito.Mockito.mockStatic(io.harness.ci.execution.integrationstage.V1.IACMPlanCreatorUtils.class)) {
      LinkedHashMap<String, PlanCreationResponse> iacmResponses = new LinkedHashMap<>();
      iacmResponses.put("iacm-1", PlanCreationResponse.builder().build());

      mockedStatic
          .when(()
                    -> io.harness.ci.execution.integrationstage.V1.IACMPlanCreatorUtils.addIACMNode(
                        eq(kryoSerializer), eq("next-step"), anyString(), eq(templateInfo), eq(false), eq(ctx)))
          .thenReturn(iacmResponses);

      java.util.Optional<?> result = (java.util.Optional<?>) method.invoke(ciStepsPlanCreator, data, "next-step");

      assertThat(result).as("Should return IACM plan result when info is present").isPresent();
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdateResponseMapAndGetNextId_withMatch() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("updateResponseMapAndGetNextId",
        LinkedHashMap.class, io.harness.cd.beans.ModuleTemplatePlanCreationResults.class, Map.class, String.class);
    method.setAccessible(true);

    String deployName = io.harness.pms.yaml.TemplateType.DEPLOY.getName();

    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    LinkedHashMap<String, PlanCreationResponse> svcResponses = new LinkedHashMap<>();
    svcResponses.put("svc-1", PlanCreationResponse.builder().build());

    io.harness.cd.beans.DeployPlanCreationResult deployResult = io.harness.cd.beans.DeployPlanCreationResult.builder()
                                                                    .serviceNodeID("svc-first-node")
                                                                    .svcPlanCreationResponses(svcResponses)
                                                                    .build();

    Map<String, io.harness.cd.beans.ModuleSpecificPlanCreationResult> planResults = new java.util.HashMap<>();
    planResults.put(deployName, deployResult);

    io.harness.cd.beans.ModuleTemplatePlanCreationResults results =
        io.harness.cd.beans.ModuleTemplatePlanCreationResults.builder().planCreationResults(planResults).build();

    Map<String, String> templateOverrides = new java.util.HashMap<>();
    templateOverrides.put(deployName, "target-step-id");

    String updatedId =
        (String) method.invoke(ciStepsPlanCreator, responseMap, results, templateOverrides, "target-step-id");

    assertThat(updatedId).as("Should return first node ID from deploy result").isEqualTo("svc-first-node");
    assertThat(responseMap).as("Response map should contain deploy responses").containsKey("svc-1");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdateResponseMapAndGetNextId_noMatch() throws Exception {
    java.lang.reflect.Method method = CIStepsPlanCreator.class.getDeclaredMethod("updateResponseMapAndGetNextId",
        LinkedHashMap.class, io.harness.cd.beans.ModuleTemplatePlanCreationResults.class, Map.class, String.class);
    method.setAccessible(true);

    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    io.harness.cd.beans.ModuleTemplatePlanCreationResults results =
        io.harness.cd.beans.ModuleTemplatePlanCreationResults.builder()
            .planCreationResults(new java.util.HashMap<>())
            .build();

    Map<String, String> templateOverrides = new java.util.HashMap<>();

    String updatedId =
        (String) method.invoke(ciStepsPlanCreator, responseMap, results, templateOverrides, "original-step");

    assertThat(updatedId).as("Should return original step ID when no match").isEqualTo("original-step");
  }
}

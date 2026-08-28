/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.ci.commonconstants.BuildEnvironmentConstants.PLUGIN_OVERRIDE_IMAGE;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.EnvironmentVariablesResolver.EnvironmentVariableRef;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class EnvironmentVariablesResolverTest extends CIExecutionTestBase {
  private static final String JEXL_OVERRIDE =
      "<+serverlessImageConfig.get(\"harnessdev/serverless-plugin:nodejs18.x-3.39.0-0.0.3\")>";
  private static final String CEL_OVERRIDE =
      "${{serverlessImageConfig.get(\"harnessdev/serverless-plugin:nodejs18.x-3.39.0-0.0.3\")}}";
  private static final String RESOLVED_IMAGE = "harnessdev/serverless-plugin:python3.12-4.0.0-0.0.3";
  private static final String PLAIN_IMAGE = "harnessdev/serverless-plugin:nodejs18.x-3.39.0-0.0.3";

  private final ObjectMapper objectMapper = new ObjectMapper();

  // ---------- Phase 1: getEnvVarsToResolve (stash) ----------

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetEnvVars_collectsJexlExpressionFromRunStep() {
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, JEXL_OVERRIDE);
    ExecutionElementConfig config = elementOf(runStepWrapper(envNode));

    List<EnvironmentVariableRef> refs = EnvironmentVariablesResolver.getEnvVarsToResolve(config);

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getRawExpression()).isEqualTo(JEXL_OVERRIDE);
    // The captured node is the very same env ObjectNode instance (so Phase 2 writes land back on the tree).
    assertThat(refs.get(0).getEnvNode()).isSameAs(envNode);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetEnvVars_collectsCelExpressionFromRunStep() {
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, CEL_OVERRIDE);
    ExecutionElementConfig config = elementOf(runStepWrapper(envNode));

    List<EnvironmentVariableRef> refs = EnvironmentVariablesResolver.getEnvVarsToResolve(config);

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getRawExpression()).isEqualTo(CEL_OVERRIDE);
    assertThat(refs.get(0).getEnvNode()).isSameAs(envNode);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetEnvVars_collectsExpressionFromBackgroundStep() {
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, JEXL_OVERRIDE);
    ExecutionElementConfig config = elementOf(backgroundStepWrapper(envNode));

    List<EnvironmentVariableRef> refs = EnvironmentVariablesResolver.getEnvVarsToResolve(config);

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getEnvNode()).isSameAs(envNode);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetEnvVars_ignoresPlainImageValue() {
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, PLAIN_IMAGE);
    ExecutionElementConfig config = elementOf(runStepWrapper(envNode));

    List<EnvironmentVariableRef> refs = EnvironmentVariablesResolver.getEnvVarsToResolve(config);

    assertThat(refs).isEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetEnvVars_ignoresWhenTargetKeyAbsent() {
    ObjectNode envNode = envWith("PLUGIN_HARNESS_CONNECTOR", "${{inputs.connector}}");
    ExecutionElementConfig config = elementOf(runStepWrapper(envNode));

    List<EnvironmentVariableRef> refs = EnvironmentVariablesResolver.getEnvVarsToResolve(config);

    assertThat(refs).isEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetEnvVars_collectsFromBareArrayParallel() {
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, JEXL_OVERRIDE);
    ArrayNode parallelArray = objectMapper.createArrayNode();
    parallelArray.add(wrapperObject("step", runStepNode(envNode)));
    ExecutionWrapperConfig parallel = ExecutionWrapperConfig.builder().parallel(parallelArray).build();

    List<EnvironmentVariableRef> refs = EnvironmentVariablesResolver.getEnvVarsToResolve(elementOf(parallel));

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getEnvNode()).isSameAs(envNode);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetEnvVars_collectsFromStepGroup() {
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, CEL_OVERRIDE);
    ArrayNode stepsArray = objectMapper.createArrayNode();
    stepsArray.add(wrapperObject("step", runStepNode(envNode)));
    ObjectNode stepGroupNode = objectMapper.createObjectNode();
    stepGroupNode.set("steps", stepsArray);
    ExecutionWrapperConfig stepGroup = ExecutionWrapperConfig.builder().stepGroup(stepGroupNode).build();

    List<EnvironmentVariableRef> refs = EnvironmentVariablesResolver.getEnvVarsToResolve(elementOf(stepGroup));

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getEnvNode()).isSameAs(envNode);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetEnvVars_collectsFromNestedParallelStepGroup() {
    // parallel(array) -> stepGroup -> step -> run.env (depth 2)
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, JEXL_OVERRIDE);
    ArrayNode innerSteps = objectMapper.createArrayNode();
    innerSteps.add(wrapperObject("step", runStepNode(envNode)));
    ObjectNode stepGroupNode = objectMapper.createObjectNode();
    stepGroupNode.set("steps", innerSteps);

    ArrayNode parallelArray = objectMapper.createArrayNode();
    parallelArray.add(wrapperObject("stepGroup", stepGroupNode));
    ExecutionWrapperConfig parallel = ExecutionWrapperConfig.builder().parallel(parallelArray).build();

    List<EnvironmentVariableRef> refs = EnvironmentVariablesResolver.getEnvVarsToResolve(elementOf(parallel));

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getEnvNode()).isSameAs(envNode);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetEnvVars_collectsMultipleAcrossSteps() {
    ObjectNode packageEnv = envWith(PLUGIN_OVERRIDE_IMAGE, JEXL_OVERRIDE);
    ObjectNode deployEnv = envWith(PLUGIN_OVERRIDE_IMAGE, CEL_OVERRIDE);
    ExecutionElementConfig config = elementOf(runStepWrapper(packageEnv), runStepWrapper(deployEnv));

    List<EnvironmentVariableRef> refs = EnvironmentVariablesResolver.getEnvVarsToResolve(config);

    assertThat(refs).hasSize(2);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetEnvVars_nullExecutionElementConfig_returnsEmptyList() {
    assertThat(EnvironmentVariablesResolver.getEnvVarsToResolve(null)).isEmpty();
  }

  // ---------- Phase 2: resolveEnvVars ----------

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolve_writesResolvedValueBackToEnvNode() {
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, JEXL_OVERRIDE);
    List<EnvironmentVariableRef> refs = Collections.singletonList(new EnvironmentVariableRef(envNode, JEXL_OVERRIDE));

    EnvironmentVariablesResolver.resolveEnvVars(refs, raw -> RESOLVED_IMAGE);

    assertThat(envNode.get(PLUGIN_OVERRIDE_IMAGE).asText()).isEqualTo(RESOLVED_IMAGE);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolve_repairsJexlNodeClobberedToNull() {
    // Simulate the full flow: stash the raw JEXL, then resolveGitAppFunctor clobbers the node value to "null",
    // then Phase 2 must repair it using the stashed raw (never rendering the clobbered "null").
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, JEXL_OVERRIDE);
    List<EnvironmentVariableRef> refs =
        EnvironmentVariablesResolver.getEnvVarsToResolve(elementOf(runStepWrapper(envNode)));
    envNode.put(PLUGIN_OVERRIDE_IMAGE, "null"); // clobber

    // Renderer only produces the real image for the ORIGINAL raw expression, proving the raw survived the clobber.
    EnvironmentVariablesResolver.resolveEnvVars(refs, raw -> JEXL_OVERRIDE.equals(raw) ? RESOLVED_IMAGE : "null");

    assertThat(envNode.get(PLUGIN_OVERRIDE_IMAGE).asText()).isEqualTo(RESOLVED_IMAGE);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolve_fallsBackToRawWhenUnresolved() {
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, JEXL_OVERRIDE);
    List<EnvironmentVariableRef> refs = Collections.singletonList(new EnvironmentVariableRef(envNode, JEXL_OVERRIDE));

    // RETURN_ORIGINAL semantics: renderer echoes the raw expression back when it cannot resolve.
    EnvironmentVariablesResolver.resolveEnvVars(refs, raw -> raw);

    assertThat(envNode.get(PLUGIN_OVERRIDE_IMAGE).asText()).isEqualTo(JEXL_OVERRIDE);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolve_fallsBackToRawWhenRendererReturnsNullLiteral() {
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, JEXL_OVERRIDE);
    List<EnvironmentVariableRef> refs = Collections.singletonList(new EnvironmentVariableRef(envNode, JEXL_OVERRIDE));

    EnvironmentVariablesResolver.resolveEnvVars(refs, raw -> "null");

    // Must never persist the literal "null" - the raw expression is kept so downstream serializers can retry.
    assertThat(envNode.get(PLUGIN_OVERRIDE_IMAGE).asText()).isEqualTo(JEXL_OVERRIDE);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolve_fallsBackToRawWhenRendererReturnsEmpty() {
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, CEL_OVERRIDE);
    List<EnvironmentVariableRef> refs = Collections.singletonList(new EnvironmentVariableRef(envNode, CEL_OVERRIDE));

    EnvironmentVariablesResolver.resolveEnvVars(refs, raw -> "");

    assertThat(envNode.get(PLUGIN_OVERRIDE_IMAGE).asText()).isEqualTo(CEL_OVERRIDE);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolve_nullRefs_isNoop() {
    // Must not throw.
    EnvironmentVariablesResolver.resolveEnvVars(null, raw -> RESOLVED_IMAGE);
    EnvironmentVariablesResolver.resolveEnvVars(new ArrayList<>(), raw -> RESOLVED_IMAGE);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolve_nullRenderer_isNoop() {
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, JEXL_OVERRIDE);
    List<EnvironmentVariableRef> refs = Collections.singletonList(new EnvironmentVariableRef(envNode, JEXL_OVERRIDE));

    EnvironmentVariablesResolver.resolveEnvVars(refs, null);

    assertThat(envNode.get(PLUGIN_OVERRIDE_IMAGE).asText()).isEqualTo(JEXL_OVERRIDE);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolve_rendererThrows_leavesNodeUnchanged() {
    ObjectNode envNode = envWith(PLUGIN_OVERRIDE_IMAGE, JEXL_OVERRIDE);
    List<EnvironmentVariableRef> refs = Collections.singletonList(new EnvironmentVariableRef(envNode, JEXL_OVERRIDE));

    UnaryOperator<String> throwingRenderer = raw -> {
      throw new RuntimeException("boom");
    };
    // Per-ref exceptions are swallowed so init is never broken.
    EnvironmentVariablesResolver.resolveEnvVars(refs, throwingRenderer);

    assertThat(envNode.get(PLUGIN_OVERRIDE_IMAGE).asText()).isEqualTo(JEXL_OVERRIDE);
  }

  // ---------- End-to-end: stash then resolve ----------

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testStashThenResolve_resolvesBothJexlAndCelAcrossSteps() {
    String jexlImage = "harnessdev/serverless-plugin:nodejs18.x-3.39.0-0.0.3";
    String celImage = "harnessdev/serverless-plugin:python3.12-4.0.0-0.0.3";

    ObjectNode jexlEnv = envWith(PLUGIN_OVERRIDE_IMAGE, JEXL_OVERRIDE);
    ObjectNode celEnv = envWith(PLUGIN_OVERRIDE_IMAGE, CEL_OVERRIDE);
    ExecutionElementConfig config = elementOf(runStepWrapper(jexlEnv), backgroundStepWrapper(celEnv));

    List<EnvironmentVariableRef> refs = EnvironmentVariablesResolver.getEnvVarsToResolve(config);
    assertThat(refs).hasSize(2);

    EnvironmentVariablesResolver.resolveEnvVars(refs, raw -> raw.startsWith("${{") ? celImage : jexlImage);

    assertThat(jexlEnv.get(PLUGIN_OVERRIDE_IMAGE).asText()).isEqualTo(jexlImage);
    assertThat(celEnv.get(PLUGIN_OVERRIDE_IMAGE).asText()).isEqualTo(celImage);
  }

  // ---------- helpers ----------

  private ObjectNode envWith(String key, String value) {
    ObjectNode env = objectMapper.createObjectNode();
    env.put(key, value);
    return env;
  }

  private ObjectNode runStepNode(ObjectNode envNode) {
    ObjectNode runNode = objectMapper.createObjectNode();
    runNode.set("env", envNode);
    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("run", runNode);
    return stepNode;
  }

  private ObjectNode backgroundStepNode(ObjectNode envNode) {
    ObjectNode backgroundNode = objectMapper.createObjectNode();
    backgroundNode.set("env", envNode);
    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.set("background", backgroundNode);
    return stepNode;
  }

  private ExecutionWrapperConfig runStepWrapper(ObjectNode envNode) {
    return ExecutionWrapperConfig.builder().step(runStepNode(envNode)).build();
  }

  private ExecutionWrapperConfig backgroundStepWrapper(ObjectNode envNode) {
    return ExecutionWrapperConfig.builder().step(backgroundStepNode(envNode)).build();
  }

  private ObjectNode wrapperObject(String key, ObjectNode value) {
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set(key, value);
    return wrapper;
  }

  private ExecutionElementConfig elementOf(ExecutionWrapperConfig... wrappers) {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    Collections.addAll(steps, wrappers);
    return ExecutionElementConfig.builder().steps(steps).build();
  }
}

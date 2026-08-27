/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.powermock.reflect.Whitebox;

/**
 * Regression coverage confirming that {@link UnifiedMultiDeploymentSpawnerStep}'s genuinely-multi (`items` count
 * &gt;= 2) environment extraction is unaffected by the count-based single/multi reclassification (per the
 * "Service Environment Yamls - Revisited" design): a length-1 `items` environment is now classified SINGLE upstream
 * and never reaches this spawner step at all, but a length-&gt;=2 `items` environment must still spawn/extract the
 * same candidates as before.
 */
@OwnedBy(HarnessTeam.CI)
public class UnifiedMultiDeploymentSpawnerStepRegressionTest extends CategoryTest {
  private final UnifiedMultiDeploymentSpawnerStep step = new UnifiedMultiDeploymentSpawnerStep();

  private static Map<String, Object> envItem(String id, String deployTo) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", id);
    item.put("deploy-to", deployTo);
    return item;
  }

  @SuppressWarnings("unchecked")
  private List<Object> extractEnvironmentObjects(Map<?, ?> envNodeAsMap) throws Exception {
    return Whitebox.invokeMethod(step, "extractEnvironmentObjects", envNodeAsMap, "proj", "org", "acct");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExtractEnvironmentObjects_MultiElementItemsUnchanged() throws Exception {
    // Genuine multi (2 elements): unaffected by the single/multi rework, each item is returned as-is (no filter to
    // apply), same as before this change.
    Map<String, Object> env1 = envItem("env1", "infra1");
    Map<String, Object> env2 = envItem("env2", "infra2");
    Map<String, Object> envNode = new LinkedHashMap<>();
    envNode.put("items", List.of(env1, env2));

    List<Object> result = extractEnvironmentObjects(envNode);

    assertThat(result).containsExactly(env1, env2);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExtractEnvironmentObjects_SingleElementItemsStillExtractableHere() throws Exception {
    // This step-level extraction itself still knows how to process a length-1 `items` list (it is agnostic to the
    // single/multi classification decided upstream); the classification change only means this step is no longer
    // *reached* for length-1 `items` stages (verified by UnifiedMultiDeploymentUtilsTest /
    // CDStepsPlanCreatorUtilsTest).
    Map<String, Object> env1 = envItem("env1", "infra1");
    Map<String, Object> envNode = new LinkedHashMap<>();
    envNode.put("items", List.of(env1));

    List<Object> result = extractEnvironmentObjects(envNode);

    assertThat(result).containsExactly(env1);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExtractEnvironmentObjects_BareIdShapeTreatedAsSingleEntryList() throws Exception {
    // Legacy bare `{id, deploy-to}` shape (no `items`) is treated as a single-entry item list, unchanged.
    Map<String, Object> envNode = envItem("env1", "infra1");

    List<Object> result = extractEnvironmentObjects(envNode);

    assertThat(result).containsExactly(envNode);
  }
}

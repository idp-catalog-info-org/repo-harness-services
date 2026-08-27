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
import io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils;
import io.harness.cimanager.stages.V1.UnifiedStageNodeV1;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.powermock.reflect.Whitebox;

/**
 * Regression coverage confirming {@link UnifiedMultiDeploymentPlanCreatorHelper}'s multi-deployment sub-type
 * computation (MULTI_SERVICE_DEPLOYMENT / MULTI_ENV_DEPLOYMENT / MULTI_SERVICE_ENV_DEPLOYMENT) is unchanged for
 * genuinely-multi (`items` count &gt;= 2) service/environment shapes after the count-based single/multi rework —
 * this helper is only invoked once a stage has already been classified MULTI by {@code UnifiedMultiDeploymentUtils}.
 */
@OwnedBy(HarnessTeam.CI)
public class UnifiedMultiDeploymentPlanCreatorHelperTest extends CategoryTest {
  private final UnifiedMultiDeploymentPlanCreatorHelper helper = new UnifiedMultiDeploymentPlanCreatorHelper();

  private static Map<String, Object> mapOf(Object... kv) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      map.put((String) kv[i], kv[i + 1]);
    }
    return map;
  }

  private static UnifiedStageNodeV1 stageNodeWith(Object service, Object environment) {
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(service == null ? ParameterField.ofNull() : ParameterField.createValueField(service));
    stageNode.setEnvironment(
        environment == null ? ParameterField.ofNull() : ParameterField.createValueField(environment));
    return stageNode;
  }

  private String getMultiDeploymentSubType(UnifiedStageNodeV1 stageNode) throws Exception {
    return Whitebox.invokeMethod(helper, "getMultiDeploymentSubType", stageNode);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetMultiDeploymentSubType_MultiServiceOnly() throws Exception {
    UnifiedStageNodeV1 stageNode =
        stageNodeWith(mapOf("items", List.of("svc1", "svc2")), mapOf("id", "env1", "deploy-to", "infra1"));

    assertThat(getMultiDeploymentSubType(stageNode)).isEqualTo(UnifiedMultiDeploymentUtils.MULTI_SERVICE_DEPLOYMENT);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetMultiDeploymentSubType_MultiEnvironmentOnly() throws Exception {
    UnifiedStageNodeV1 stageNode = stageNodeWith("svc1",
        mapOf(
            "items", List.of(mapOf("id", "env1", "deploy-to", "infra1"), mapOf("id", "env2", "deploy-to", "infra2"))));

    assertThat(getMultiDeploymentSubType(stageNode)).isEqualTo(UnifiedMultiDeploymentUtils.MULTI_ENV_DEPLOYMENT);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetMultiDeploymentSubType_MultiServiceAndEnvironment() throws Exception {
    UnifiedStageNodeV1 stageNode = stageNodeWith(mapOf("items", List.of("svc1", "svc2")),
        mapOf(
            "items", List.of(mapOf("id", "env1", "deploy-to", "infra1"), mapOf("id", "env2", "deploy-to", "infra2"))));

    assertThat(getMultiDeploymentSubType(stageNode))
        .isEqualTo(UnifiedMultiDeploymentUtils.MULTI_SERVICE_ENV_DEPLOYMENT);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetMultiDeploymentSubType_SingleElementEnvironmentItemsWithMultiDeployToIsMultiEnv()
      throws Exception {
    // Nested multi-infra fan-out (env items count == 1, but its sole deploy-to resolves to >1) must still be
    // classified as a multi-environment deployment, unaffected by the count-based rework.
    UnifiedStageNodeV1 stageNode =
        stageNodeWith("svc1", mapOf("items", List.of(mapOf("id", "env1", "deploy-to", List.of("infra1", "infra2")))));

    assertThat(getMultiDeploymentSubType(stageNode)).isEqualTo(UnifiedMultiDeploymentUtils.MULTI_ENV_DEPLOYMENT);
  }
}

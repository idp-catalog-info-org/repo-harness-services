/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage.V1;

import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_ENVIRONMENT_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_INFRA_ID;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_SERVICE_INPUTS;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_SERVICE_REF;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.MULTI_ENVIRONMENT;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.MULTI_SERVICE;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.SERVICE_INPUTS;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.SERVICE_TYPE;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.plan.creator.stage.V3.UnifiedStagePMSPlanCreator;
import io.harness.cimanager.stages.V1.UnifiedStageNodeV1;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * End-to-end (YAML -> deploy module implicit nodes info map) coverage of the count-based single/multi
 * classification for the Unified {@code service}/{@code environment} node, per the "Service Environment Yamls -
 * Revisited" design. Complements {@code UnifiedDeploymentItemsResolverTest} and {@code
 * UnifiedMultiDeploymentUtilsTest}, which cover the counting/classification primitives in isolation.
 */
@OwnedBy(HarnessTeam.CI)
public class CDStepsPlanCreatorUtilsTest extends CategoryTest {
  private static final UnifiedStagePMSPlanCreator PLAN_CREATOR = new UnifiedStagePMSPlanCreator();

  private static Map<String, Object> getDeployNodesInfoForYaml(String stageYaml) throws Exception {
    YamlField stageField = YamlUtils.readTree(stageYaml);
    UnifiedStageNodeV1 stageNode = PLAN_CREATOR.getFieldObject(stageField);
    return CDStepsPlanCreatorUtils.getDeployNodesInfo(stageField, stageNode);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDeployNodesInfo_BareSingleServiceAndEnvironmentIsSingle() throws Exception {
    String yaml = "service: svc1\n"
        + "environment:\n"
        + "  id: env1\n"
        + "  deploy-to: infra1\n";

    Map<String, Object> info = getDeployNodesInfoForYaml(yaml);

    assertThat(info.get(MULTI_SERVICE)).isEqualTo("false");
    assertThat(info.get(MULTI_ENVIRONMENT)).isEqualTo("false");
    assertThat(info.get(YAMLFieldNameConstants.SERVICE)).isEqualTo("svc1");
    assertThat(info.get(YAMLFieldNameConstants.ENVIRONMENT)).isEqualTo("env1");
    assertThat(info.get(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE)).isEqualTo("infra1");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDeployNodesInfo_SingleElementItemsUnwrapsToSameResultAsBareShape() throws Exception {
    // Service Environment Yamls - Revisited: a 1-element `items` list is a SINGLE service/environment, and must be
    // read the same way as the equivalent bare shape (no strategy/matrix expressions, direct id/infra values).
    String yaml = "service:\n"
        + "  items:\n"
        + "    - svc1\n"
        + "environment:\n"
        + "  items:\n"
        + "    - id: env1\n"
        + "      deploy-to: infra1\n";

    Map<String, Object> info = getDeployNodesInfoForYaml(yaml);

    assertThat(info.get(MULTI_SERVICE)).isEqualTo("false");
    assertThat(info.get(MULTI_ENVIRONMENT)).isEqualTo("false");
    assertThat(info.get(YAMLFieldNameConstants.SERVICE)).isEqualTo("svc1");
    assertThat(info.get(YAMLFieldNameConstants.ENVIRONMENT)).isEqualTo("env1");
    assertThat(info.get(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE)).isEqualTo("infra1");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDeployNodesInfo_SingleElementServiceItemsMapShapeUnwraps() throws Exception {
    String yaml = "service:\n"
        + "  type: kubernetes\n"
        + "  items:\n"
        + "    - id: svc1\n"
        + "      with:\n"
        + "        overlay: {}\n"
        + "environment:\n"
        + "  id: env1\n"
        + "  deploy-to: infra1\n";

    Map<String, Object> info = getDeployNodesInfoForYaml(yaml);

    assertThat(info.get(MULTI_SERVICE)).isEqualTo("false");
    assertThat(info.get(YAMLFieldNameConstants.SERVICE)).isEqualTo("svc1");
    assertThat(info.get(SERVICE_TYPE)).isEqualTo("kubernetes");
    assertThat(info.get(SERVICE_INPUTS)).isNotNull();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDeployNodesInfo_MultiElementServiceItemsUsesMatrixExpressions() throws Exception {
    String yaml = "service:\n"
        + "  items:\n"
        + "    - svc1\n"
        + "    - svc2\n"
        + "environment:\n"
        + "  id: env1\n"
        + "  deploy-to: infra1\n";

    Map<String, Object> info = getDeployNodesInfoForYaml(yaml);

    assertThat(info.get(MULTI_SERVICE)).isEqualTo("true");
    assertThat(info.get(YAMLFieldNameConstants.SERVICE)).isEqualTo(MATRIX_SERVICE_REF);
    assertThat(info.get(SERVICE_INPUTS)).isEqualTo(MATRIX_SERVICE_INPUTS);
    // Environment side is unaffected and remains single.
    assertThat(info.get(MULTI_ENVIRONMENT)).isEqualTo("false");
    assertThat(info.get(YAMLFieldNameConstants.ENVIRONMENT)).isEqualTo("env1");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDeployNodesInfo_SingleElementEnvironmentItemsWithSingleDeployToIsSingle() throws Exception {
    String yaml = "service: svc1\n"
        + "environment:\n"
        + "  items:\n"
        + "    - id: env1\n"
        + "      deploy-to:\n"
        + "        - infra1\n";

    Map<String, Object> info = getDeployNodesInfoForYaml(yaml);

    assertThat(info.get(MULTI_ENVIRONMENT)).isEqualTo("false");
    assertThat(info.get(YAMLFieldNameConstants.ENVIRONMENT)).isEqualTo("env1");
    assertThat(info.get(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE)).isEqualTo("infra1");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDeployNodesInfo_SingleElementEnvironmentItemsWithMultiDeployToIsMulti() throws Exception {
    // The environment `items` count is 1 (single environment), but that sole environment's own `deploy-to`
    // resolves to more than one infra -> multi (nested infra fan-out), per the count-based rule.
    String yaml = "service: svc1\n"
        + "environment:\n"
        + "  items:\n"
        + "    - id: env1\n"
        + "      deploy-to:\n"
        + "        - infra1\n"
        + "        - infra2\n";

    Map<String, Object> info = getDeployNodesInfoForYaml(yaml);

    assertThat(info.get(MULTI_ENVIRONMENT)).isEqualTo("true");
    assertThat(info.get(YAMLFieldNameConstants.ENVIRONMENT)).isEqualTo(MATRIX_ENVIRONMENT_REF);
    assertThat(info.get(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE)).isEqualTo(MATRIX_INFRA_ID);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDeployNodesInfo_MultiElementEnvironmentItemsIsMulti() throws Exception {
    String yaml = "service: svc1\n"
        + "environment:\n"
        + "  items:\n"
        + "    - id: env1\n"
        + "      deploy-to: infra1\n"
        + "    - id: env2\n"
        + "      deploy-to: infra2\n";

    Map<String, Object> info = getDeployNodesInfoForYaml(yaml);

    assertThat(info.get(MULTI_ENVIRONMENT)).isEqualTo("true");
    assertThat(info.get(YAMLFieldNameConstants.ENVIRONMENT)).isEqualTo(MATRIX_ENVIRONMENT_REF);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDeployNodesInfo_RejectsLiteralRuntimeInputAsSoleEnvironmentItemsElement() throws Exception {
    String yaml = "service: svc1\n"
        + "environment:\n"
        + "  items:\n"
        + "    - <+input>\n";

    assertThatThrownBy(() -> getDeployNodesInfoForYaml(yaml))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("not supported as an individual element");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetDeployNodesInfo_RejectsLiteralRuntimeInputAsNestedDeployToElement() throws Exception {
    String yaml = "service: svc1\n"
        + "environment:\n"
        + "  items:\n"
        + "    - id: env1\n"
        + "      deploy-to:\n"
        + "        - <+input>\n";

    assertThatThrownBy(() -> getDeployNodesInfoForYaml(yaml))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("deploy-to");
  }
}

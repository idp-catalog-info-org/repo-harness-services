/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.base.CiBeansTestBase;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.category.element.UnitTests;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class V0PipelinePermissionsYamlTest extends CiBeansTestBase {
  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testV0PermissionsBlockDeserialization() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    URL testFile = classLoader.getResource("cipms-permissions.yml");
    String yaml = Resources.toString(testFile, Charsets.UTF_8);

    YamlField pipeline = YamlUtils.readTree(yaml);
    List<YamlNode> stages = pipeline.getNode().getField("pipeline").getNode().getField("stages").getNode().asArray();

    YamlNode stageNode = stages.get(0).getField("stage").getNode();
    IntegrationStageNode integrationStageNode =
        YamlPipelineUtils.read(stageNode.getCurrJsonNode().toString(), IntegrationStageNode.class);

    assertThat(integrationStageNode).isNotNull();
    IntegrationStageConfigImpl config = integrationStageNode.getIntegrationStageConfig();
    assertThat(config).isNotNull();

    Map<String, String> permissions = config.getPermissions();
    assertThat(permissions).isNotNull();
    assertThat(permissions).containsEntry("pipeline", "view|execute");
    assertThat(permissions).containsEntry("code_repository", "push|pull");
    assertThat(permissions).containsEntry("artifact_registry", "read");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testV0StageWithoutPermissionsReturnsNull() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    URL testFile = classLoader.getResource("cipms.yml");
    String yaml = Resources.toString(testFile, Charsets.UTF_8);

    YamlField pipeline = YamlUtils.readTree(yaml);
    List<YamlNode> stages = pipeline.getNode().getField("pipeline").getNode().getField("stages").getNode().asArray();

    YamlNode stageNode = stages.get(0).getField("stage").getNode();
    IntegrationStageNode integrationStageNode =
        YamlPipelineUtils.read(stageNode.getCurrJsonNode().toString(), IntegrationStageNode.class);

    assertThat(integrationStageNode).isNotNull();
    Map<String, String> permissions = integrationStageNode.getIntegrationStageConfig().getPermissions();
    assertThat(permissions).isNull();
  }
}

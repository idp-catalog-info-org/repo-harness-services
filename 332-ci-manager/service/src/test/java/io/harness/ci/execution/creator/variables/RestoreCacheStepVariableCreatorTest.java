/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.creator.variables;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.SATYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.RestoreCacheNode;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class RestoreCacheStepVariableCreatorTest extends CategoryTest {
  @Inject RestoreCacheStepVariableCreator restoreCacheStepVariableCreator = new RestoreCacheStepVariableCreator();

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void getSupportedStepTypes() {
    Set<String> stepTypes = restoreCacheStepVariableCreator.getSupportedStepTypes();
    assertThat(stepTypes).containsOnly(CIStepInfoType.RESTORE_CACHE.getDisplayName());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void getFieldClass() {
    Class<?> fieldClass = restoreCacheStepVariableCreator.getFieldClass();
    assertThat(fieldClass).isEqualTo(RestoreCacheNode.class);
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("saveRestoreCacheJsonStep.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Navigate to RestoreCache step in pipeline
    YamlField stepField = fullYamlField.getNode()
                              .getField("pipeline")
                              .getNode()
                              .getField("stages")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("stage")
                              .getNode()
                              .getField("spec")
                              .getNode()
                              .getField("execution")
                              .getNode()
                              .getField("steps")
                              .getNode()
                              .asArray()
                              .get(1)
                              .getField("step");

    // Parse RestoreCacheNode from yaml
    RestoreCacheNode restoreCacheNode = YamlUtils.read(stepField.getNode().toString(), RestoreCacheNode.class);

    // Create variables
    VariableCreationResponse variablesForParentNodeV2 = restoreCacheStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(), restoreCacheNode);

    // Assert yaml properties
    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());

    assertThat(fqnPropertiesList)
        .containsExactlyInAnyOrder("pipeline.stages.generic_cache.spec.execution.steps.restoreCache.name",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.description",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.timeout",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.when",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.connectorRef",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.providerType",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.key",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.bucket",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.sourcePaths",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.region",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.endpoint",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.pathStyle",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.failIfKeyNotFound",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.archiveFormat",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.runAsUser",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.storageAccount",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.spec.containerName");

    // Assert extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get(restoreCacheNode.getUuid())
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());

    assertThat(fqnExtraPropertiesList)
        .containsExactlyInAnyOrder("pipeline.stages.generic_cache.spec.execution.steps.restoreCache.type",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.identifier",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.startTs",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.endTs",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.status",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.nodeExecutionId",
            "pipeline.stages.generic_cache.spec.execution.steps.restoreCache.log.url");
  }
}

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
import io.harness.beans.steps.nodes.SaveCacheNode;
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
public class SaveCacheStepVariableCreatorTest extends CategoryTest {
  @Inject SaveCacheStepVariableCreator saveCacheStepVariableCreator = new SaveCacheStepVariableCreator();

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void getSupportedStepTypes() {
    Set<String> stepTypes = saveCacheStepVariableCreator.getSupportedStepTypes();
    assertThat(stepTypes).containsOnly(CIStepInfoType.SAVE_CACHE.getDisplayName());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void getFieldClass() {
    Class<?> fieldClass = saveCacheStepVariableCreator.getFieldClass();
    assertThat(fieldClass).isEqualTo(SaveCacheNode.class);
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("saveRestoreCacheJsonStep.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Navigate to SaveCache step in pipeline
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
                              .get(0)
                              .getField("step");

    // Parse SaveCacheNode from yaml
    SaveCacheNode saveCacheNode = YamlUtils.read(stepField.getNode().toString(), SaveCacheNode.class);

    // Create variables
    VariableCreationResponse variablesForParentNodeV2 = saveCacheStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(), saveCacheNode);

    // Assert yaml properties
    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());

    assertThat(fqnPropertiesList)
        .containsExactlyInAnyOrder("pipeline.stages.generic_cache.spec.execution.steps.saveCache.name",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.description",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.timeout",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.when",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.connectorRef",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.providerType",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.archiveFormat",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.override",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.ignoreMissingPaths",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.pathStyle",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.runAsUser",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.endpoint",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.region",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.key",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.bucket",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.sourcePaths",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.storageAccount",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.containerName");

    // Assert extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get(saveCacheNode.getUuid())
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());

    assertThat(fqnExtraPropertiesList)
        .containsExactlyInAnyOrder("pipeline.stages.generic_cache.spec.execution.steps.saveCache.type",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.identifier",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.startTs",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.endTs",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.status",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.nodeExecutionId",
            "pipeline.stages.generic_cache.spec.execution.steps.saveCache.log.url");

    // Assert extra properties for step spec info
    List<String> fqnExtraPropertiesListForSpec = variablesForParentNodeV2.getYamlExtraProperties()
                                                     .get(saveCacheNode.getSaveCacheStepInfo().getUuid())
                                                     .getPropertiesList()
                                                     .stream()
                                                     .map(YamlProperties::getFqn)
                                                     .collect(Collectors.toList());

    assertThat(fqnExtraPropertiesListForSpec)
        .containsOnly("pipeline.stages.generic_cache.spec.execution.steps.saveCache.spec.resources");
  }
}

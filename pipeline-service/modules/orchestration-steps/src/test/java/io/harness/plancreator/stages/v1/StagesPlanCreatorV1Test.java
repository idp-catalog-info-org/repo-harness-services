/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages.v1;

import static io.harness.rule.OwnerRule.KUSHAL_DASARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class StagesPlanCreatorV1Test extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks private StagesPlanCreatorV1 stagesPlanCreatorV1;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    doReturn(new byte[0]).when(kryoSerializer).asBytes(any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_SetsStageFqnInParentInfo_ForV1() throws IOException {
    String v1PipelineYaml = "{\n"
        + "  \"pipeline\": {\n"
        + "    \"stages\": [\n"
        + "      {\n"
        + "        \"id\": \"stage1\",\n"
        + "        \"name\": \"Stage 1\"\n"
        + "      },\n"
        + "      {\n"
        + "        \"id\": \"stage2\",\n"
        + "        \"name\": \"Stage 2\"\n"
        + "      }\n"
        + "    ]\n"
        + "  }\n"
        + "}";

    String yamlWithUuid = YamlUtils.injectUuid(v1PipelineYaml);
    YamlField pipelineYamlField = YamlUtils.readTree(yamlWithUuid);
    YamlField stagesField = pipelineYamlField.getNode().getField("pipeline").getNode().getField("stages");

    Dependency dependency = Dependency.newBuilder()
                                .setParentInfo(HarnessStruct.newBuilder().putData(PlanCreatorConstants.YAML_VERSION,
                                    HarnessValue.newBuilder().setStringValue("V1").build()))
                                .setNodeMetadata(HarnessStruct.newBuilder())
                                .build();

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(stagesField)
                                  .dependency(dependency)
                                  .globalContext(Collections.emptyMap())
                                  .build();

    LinkedHashMap<String, PlanCreationResponse> responseMap =
        stagesPlanCreatorV1.createPlanForChildrenNodes(ctx, stagesField);

    assertThat(responseMap).isNotEmpty();

    // Check each stage dependency (excluding the pipeline rollback stage entry) has STAGE_FQN in parentInfo
    for (Map.Entry<String, PlanCreationResponse> entry : responseMap.entrySet()) {
      Dependencies deps = entry.getValue().getDependencies();
      if (deps == null) {
        continue;
      }
      for (Map.Entry<String, Dependency> depEntry : deps.getDependencyMetadataMap().entrySet()) {
        Dependency dep = depEntry.getValue();
        if (dep.getParentInfo().getDataMap().containsKey(PlanCreatorConstants.STAGE_FQN)) {
          String stageFqn = dep.getParentInfo().getDataMap().get(PlanCreatorConstants.STAGE_FQN).getStringValue();
          assertThat(stageFqn).startsWith("stages.");
        }
      }
    }

    // Verify at least one dependency has the STAGE_FQN set
    boolean foundStageFqn = false;
    for (PlanCreationResponse response : responseMap.values()) {
      Dependencies deps = response.getDependencies();
      if (deps == null) {
        continue;
      }
      for (Dependency dep : deps.getDependencyMetadataMap().values()) {
        if (dep.getParentInfo().getDataMap().containsKey(PlanCreatorConstants.STAGE_FQN)) {
          foundStageFqn = true;
          break;
        }
      }
      if (foundStageFqn) {
        break;
      }
    }
    assertThat(foundStageFqn).as("Expected at least one dependency with STAGE_FQN in parentInfo").isTrue();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_SkipsStageFqn_ForV0() throws IOException {
    // V0 YAML uses "stage" wrapper with "identifier" instead of "id"
    String v0PipelineYaml = "{\n"
        + "  \"pipeline\": {\n"
        + "    \"stages\": [\n"
        + "      {\n"
        + "        \"stage\": {\n"
        + "          \"identifier\": \"stage1\",\n"
        + "          \"name\": \"Stage 1\",\n"
        + "          \"type\": \"Deployment\"\n"
        + "        }\n"
        + "      },\n"
        + "      {\n"
        + "        \"stage\": {\n"
        + "          \"identifier\": \"stage2\",\n"
        + "          \"name\": \"Stage 2\",\n"
        + "          \"type\": \"Deployment\"\n"
        + "        }\n"
        + "      }\n"
        + "    ]\n"
        + "  }\n"
        + "}";

    String yamlWithUuid = YamlUtils.injectUuid(v0PipelineYaml);
    YamlField pipelineYamlField = YamlUtils.readTree(yamlWithUuid);
    YamlField stagesField = pipelineYamlField.getNode().getField("pipeline").getNode().getField("stages");

    Dependency dependency = Dependency.newBuilder()
                                .setParentInfo(HarnessStruct.newBuilder().putData(PlanCreatorConstants.YAML_VERSION,
                                    HarnessValue.newBuilder().setStringValue("V0").build()))
                                .setNodeMetadata(HarnessStruct.newBuilder())
                                .build();

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(stagesField)
                                  .dependency(dependency)
                                  .globalContext(Collections.emptyMap())
                                  .build();

    LinkedHashMap<String, PlanCreationResponse> responseMap =
        stagesPlanCreatorV1.createPlanForChildrenNodes(ctx, stagesField);

    assertThat(responseMap).isNotEmpty();

    // For V0, no dependency should have STAGE_FQN in parentInfo
    for (Map.Entry<String, PlanCreationResponse> entry : responseMap.entrySet()) {
      Dependencies deps = entry.getValue().getDependencies();
      if (deps == null) {
        continue;
      }
      for (Map.Entry<String, Dependency> depEntry : deps.getDependencyMetadataMap().entrySet()) {
        Dependency dep = depEntry.getValue();
        assertThat(dep.getParentInfo().getDataMap().containsKey(PlanCreatorConstants.STAGE_FQN))
            .as("V0 stages should not have STAGE_FQN in parentInfo")
            .isFalse();
      }
    }
  }
}

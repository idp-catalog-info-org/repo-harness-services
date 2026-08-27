/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.preprocess;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.stages.OpaEvaluationStageHelper;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.rule.Owner;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class PlanCreationYamlPreprocessorV0Test extends CategoryTest {
  @Mock private OpaEvaluationStageHelper opaEvaluationStageHelper;

  private PlanCreationYamlPreprocessorV0 preprocessor;

  private static final String ACCOUNT_ID = "account-id";
  private static final String ORG_ID = "org-id";
  private static final String PROJECT_ID = "project-id";
  private static final String EXECUTION_UUID = "execution-uuid";
  private static final String PIPELINE_ID = "pipeline-id";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    preprocessor = new PlanCreationYamlPreprocessorV0(opaEvaluationStageHelper);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testPreprocessPipelineYamlSuccess() {
    String originalYaml = "{\"pipeline\":{\"identifier\":\"test-pipeline\"}}";
    String updatedYaml =
        "{\"pipeline\":{\"identifier\":\"test-pipeline\",\"stages\":[{\"stage\":{\"name\":\"OPA Evaluation\"}}]}}";

    JsonNode originalJsonNode = JsonPipelineUtils.readTree(originalYaml);

    when(opaEvaluationStageHelper.injectOpaStageIntoProcessedYaml(
             anyString(), anyString(), anyString(), anyString(), anyString(), any(ExecutionMode.class), anyString()))
        .thenReturn(updatedYaml);

    JsonNode result = preprocessor.preprocessPipelineYaml(
        originalJsonNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isNotNull();
    assertThat(result.has("pipeline")).isTrue();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testPreprocessPipelineYamlWithNullHelper() {
    PlanCreationYamlPreprocessorV0 preprocessorWithNullHelper = new PlanCreationYamlPreprocessorV0(null);

    String originalYaml = "{\"pipeline\":{\"identifier\":\"test-pipeline\"}}";
    JsonNode originalJsonNode = JsonPipelineUtils.readTree(originalYaml);

    JsonNode result = preprocessorWithNullHelper.preprocessPipelineYaml(
        originalJsonNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    assertThat(result).isEqualTo(originalJsonNode);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testPreprocessPipelineYamlWithException() {
    String originalYaml = "{\"pipeline\":{\"identifier\":\"test-pipeline\"}}";
    JsonNode originalJsonNode = JsonPipelineUtils.readTree(originalYaml);

    when(opaEvaluationStageHelper.injectOpaStageIntoProcessedYaml(
             anyString(), anyString(), anyString(), anyString(), anyString(), any(ExecutionMode.class), anyString()))
        .thenThrow(new RuntimeException("Test exception"));

    JsonNode result = preprocessor.preprocessPipelineYaml(
        originalJsonNode, ACCOUNT_ID, ORG_ID, PROJECT_ID, EXECUTION_UUID, PIPELINE_ID, ExecutionMode.NORMAL);

    // Should return original node on exception
    assertThat(result).isEqualTo(originalJsonNode);
  }
}

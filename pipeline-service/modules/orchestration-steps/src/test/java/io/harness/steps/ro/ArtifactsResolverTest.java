/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.ro;

import static io.harness.rule.OwnerRule.ZANINI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ArtifactsResolverTest extends CategoryTest {
  @Mock private PmsOutcomeService outcomeService;
  @Mock private NodeExecutionService nodeExecutionService;
  private ArtifactsResolver resolver;
  private Ambiance ambiance;

  @Before
  public void setUp() {
    resolver =
        new ArtifactsResolver(outcomeService, nodeExecutionService, ArtifactsResolver.DEFAULT_MAX_ITEMS_PER_TYPE);
    ambiance = Ambiance.newBuilder().setPlanExecutionId("plan-1").build();
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void stageMode_aggregatesArtifactOutcomes() {
    when(outcomeService.findAllOutcomeNamesByPlanExecutionId("plan-1"))
        .thenReturn(Arrays.asList("artifact_BuildAndPushECR_1", "artifact_BuildAndPushECR_2", "unrelated"));
    String json1 = "{\"stepArtifacts\":{\"publishedImageArtifacts\":[{\"imageName\":\"a/b\",\"tag\":\"1\"}]}}";
    String json2 = "{\"stepArtifacts\":{\"publishedImageArtifacts\":[{\"imageName\":\"a/c\",\"tag\":\"2\"}]}}";
    when(outcomeService.resolve(any(), any())).thenReturn(json1, json2);

    ArtifactsResolver.ResolvedArtifacts result = resolver.resolve(ambiance, ArtifactsResolver.Scope.STAGE);

    assertThat(result.getImages()).hasSize(2);
    assertThat(result.getImages().get(0).get("imageName")).isEqualTo("a/b");
    assertThat(result.getImages().get(1).get("imageName")).isEqualTo("a/c");
    assertThat(result.getFiles()).isEmpty();
    assertThat(result.getSbom()).isEmpty();
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void stageMode_returnsEmptyWhenNoArtifactOutcomes() {
    when(outcomeService.findAllOutcomeNamesByPlanExecutionId("plan-1")).thenReturn(Collections.emptyList());

    ArtifactsResolver.ResolvedArtifacts result = resolver.resolve(ambiance, ArtifactsResolver.Scope.STAGE);

    assertThat(result.getImages()).isEmpty();
    assertThat(result.getFiles()).isEmpty();
    assertThat(result.getSbom()).isEmpty();
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void stageMode_swallowsExceptionFromSingleOutcomeAndContinues() {
    when(outcomeService.findAllOutcomeNamesByPlanExecutionId("plan-1"))
        .thenReturn(Arrays.asList("artifact_A", "artifact_B"));
    when(outcomeService.resolve(any(), any()))
        .thenThrow(new RuntimeException("kaboom"))
        .thenReturn("{\"stepArtifacts\":{\"publishedImageArtifacts\":[{\"imageName\":\"x\",\"tag\":\"1\"}]}}");

    ArtifactsResolver.ResolvedArtifacts result = resolver.resolve(ambiance, ArtifactsResolver.Scope.STAGE);

    assertThat(result.getImages()).hasSize(1);
    assertThat(result.getImages().get(0).get("imageName")).isEqualTo("x");
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void pipelineMode_aggregatesIntegrationStageOutcomes() {
    io.harness.execution.NodeExecution stage1 = mock(io.harness.execution.NodeExecution.class);
    io.harness.execution.NodeExecution stage2 = mock(io.harness.execution.NodeExecution.class);
    io.harness.execution.NodeExecution stillRunning = mock(io.harness.execution.NodeExecution.class);
    ExecutionContext ctx = ExecutionContext.newBuilder()
                               .setPlanExecutionId("plan-1")
                               .addLevels(Level.newBuilder().setRuntimeId("r1").build())
                               .build();
    when(stage1.getStatus()).thenReturn(io.harness.pms.contracts.execution.Status.SUCCEEDED);
    when(stage2.getStatus()).thenReturn(io.harness.pms.contracts.execution.Status.SUCCEEDED);
    when(stillRunning.getStatus()).thenReturn(io.harness.pms.contracts.execution.Status.RUNNING);
    when(stage1.getExecutionContext()).thenReturn(ctx);
    when(stage2.getExecutionContext()).thenReturn(ctx);
    when(nodeExecutionService.fetchStageExecutions("plan-1")).thenReturn(Arrays.asList(stage1, stage2, stillRunning));

    String json1 =
        "{\"imageArtifacts\":[{\"imageName\":\"a\",\"tag\":\"1\"}],\"fileArtifacts\":[],\"sbomArtifacts\":[]}";
    String json2 =
        "{\"imageArtifacts\":[{\"imageName\":\"b\",\"tag\":\"2\"}],\"fileArtifacts\":[],\"sbomArtifacts\":[]}";
    when(outcomeService.resolve(any(), any())).thenReturn(json1, json2);

    ArtifactsResolver.ResolvedArtifacts result = resolver.resolve(ambiance, ArtifactsResolver.Scope.PIPELINE);

    assertThat(result.getImages()).extracting("imageName").containsExactly("a", "b");
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void pipelineMode_emptyWhenNoStages() {
    when(nodeExecutionService.fetchStageExecutions("plan-1")).thenReturn(Collections.emptyList());

    ArtifactsResolver.ResolvedArtifacts result = resolver.resolve(ambiance, ArtifactsResolver.Scope.PIPELINE);

    assertThat(result.getImages()).isEmpty();
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void pipelineMode_resolvesFromExecutionContextLevels() {
    // Regression: PIPE_REMOVE_AMBIANCE_POPULATION_IN_NODE_EXECUTION strips levels from the persisted
    // ambiance field. The resolver must use executionContext (which always carries levels) and must
    // never touch the deprecated ambiance field.
    ExecutionContext ctx = ExecutionContext.newBuilder()
                               .setPlanExecutionId("plan-1")
                               .addLevels(Level.newBuilder().setRuntimeId("rid-1").build())
                               .addLevels(Level.newBuilder().setRuntimeId("rid-2").build())
                               .addLevels(Level.newBuilder().setRuntimeId("rid-3").build())
                               .build();

    io.harness.execution.NodeExecution stage = mock(io.harness.execution.NodeExecution.class);
    when(stage.getStatus()).thenReturn(io.harness.pms.contracts.execution.Status.SUCCEEDED);
    when(stage.getExecutionContext()).thenReturn(ctx);
    when(nodeExecutionService.fetchStageExecutions("plan-1")).thenReturn(Collections.singletonList(stage));

    String json =
        "{\"imageArtifacts\":[{\"imageName\":\"svc\",\"tag\":\"1.0\"}],\"fileArtifacts\":[],\"sbomArtifacts\":[]}";
    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    when(outcomeService.resolve(ambianceCaptor.capture(), any())).thenReturn(json);

    ArtifactsResolver.ResolvedArtifacts result = resolver.resolve(ambiance, ArtifactsResolver.Scope.PIPELINE);

    assertThat(ambianceCaptor.getValue().getLevelsList()).hasSize(3);
    assertThat(ambianceCaptor.getValue().getLevelsList())
        .extracting(Level::getRuntimeId)
        .containsExactly("rid-1", "rid-2", "rid-3");
    assertThat(result.getImages()).hasSize(1);
    assertThat(result.getImages().get(0).get("imageName")).isEqualTo("svc");
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void pipelineMode_skipsStageWithNoExecutionContext() {
    io.harness.execution.NodeExecution noCtx = mock(io.harness.execution.NodeExecution.class);
    when(noCtx.getStatus()).thenReturn(io.harness.pms.contracts.execution.Status.SUCCEEDED);
    when(noCtx.getExecutionContext()).thenReturn(null);
    when(nodeExecutionService.fetchStageExecutions("plan-1")).thenReturn(Collections.singletonList(noCtx));

    ArtifactsResolver.ResolvedArtifacts result = resolver.resolve(ambiance, ArtifactsResolver.Scope.PIPELINE);

    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = ZANINI)
  @Category(UnitTests.class)
  public void stageMode_capsImagesAtMaxItemsPerType() {
    // One outcome whose stepArtifacts publishes 2x the cap of images. The
    // resolver must truncate to the configured per-type cap; downstream
    // serialization (and RM ingestion) is bounded.
    int over = resolver.getMaxItemsPerType() * 2;
    StringBuilder imagesArray = new StringBuilder("[");
    for (int i = 0; i < over; i++) {
      if (i > 0) {
        imagesArray.append(',');
      }
      imagesArray.append("{\"imageName\":\"a/img").append(i).append("\",\"tag\":\"1\"}");
    }
    imagesArray.append(']');
    String json = "{\"stepArtifacts\":{\"publishedImageArtifacts\":" + imagesArray + "}}";

    when(outcomeService.findAllOutcomeNamesByPlanExecutionId("plan-1"))
        .thenReturn(java.util.Arrays.asList("artifact_runawayStep"));
    when(outcomeService.resolve(any(), any())).thenReturn(json);

    ArtifactsResolver.ResolvedArtifacts result = resolver.resolve(ambiance, ArtifactsResolver.Scope.STAGE);

    assertThat(result.getImages()).hasSize(resolver.getMaxItemsPerType());
  }
}

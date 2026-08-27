/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;

/**
 * Shared traversal helpers over the resolved pipeline YAML tree. Keeping the stage-wrapper walk in
 * one place means a schema change (e.g. a new grouping type beyond {@code parallel}) is fixed once
 * rather than in each rule that iterates stages.
 */
@UtilityClass
@OwnedBy(PIPELINE)
public class SemanticYamlUtils {
  /** V0 stage wrapper key. V1 stages are the array element itself. */
  private static final String STAGE = "stage";
  private static final String PARALLEL = "parallel";
  private static final String PIPELINE_NODE = "pipeline";
  private static final String STAGES = "stages";
  private static final String IDENTIFIER = "identifier";
  private static final String ID = "id";

  /** V0-compatible traversal (visits {@code wrapper.stage}, recurses into {@code parallel}). */
  public void forEachStage(JsonNode stages, Consumer<JsonNode> visitor) {
    forEachStage(stages, false, visitor);
  }

  /**
   * Visit every stage under a {@code stages} array, descending into {@code parallel} groups. V0
   * stages are wrapped ({@code - stage: {...}}); V1 stages are the array element directly.
   *
   * <p>V1 parallel groups are objects with a nested {@code stages} array
   * ({@code parallel: { stages: [...] }}). A bare array ({@code parallel: [...]}) is also accepted
   * for robustness.
   */
  public void forEachStage(JsonNode stages, boolean isV1, Consumer<JsonNode> visitor) {
    if (stages == null || !stages.isArray()) {
      return;
    }
    for (JsonNode wrapper : stages) {
      if (isV1) {
        JsonNode parallel = wrapper.get(PARALLEL);
        if (parallel != null) {
          JsonNode nestedStages = parallel.isArray() ? parallel : parallel.get(STAGES);
          forEachStage(nestedStages, true, visitor);
        } else {
          visitor.accept(wrapper);
        }
      } else {
        JsonNode stage = wrapper.get(STAGE);
        if (stage != null) {
          visitor.accept(stage);
        }
        JsonNode parallel = wrapper.get(PARALLEL);
        if (parallel != null) {
          forEachStage(parallel, false, visitor);
        }
      }
    }
  }

  /** The {@code pipeline.stages} array (same path in both dialects), or null. */
  public JsonNode stagesNode(JsonNode root, boolean isV1) {
    if (root == null) {
      return null;
    }
    JsonNode pipeline = root.get(PIPELINE_NODE);
    JsonNode base = pipeline != null ? pipeline : root;
    return base.get(STAGES);
  }

  /** Stage identifier: V0 {@code identifier}, V1 {@code id}. */
  public String stageId(JsonNode stage, boolean isV1) {
    if (stage == null) {
      return null;
    }
    JsonNode value = stage.get(isV1 ? ID : IDENTIFIER);
    return value == null || value.isNull() ? null : value.asText();
  }
}

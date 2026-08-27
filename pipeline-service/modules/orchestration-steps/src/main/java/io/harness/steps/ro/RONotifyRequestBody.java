/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.ro;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RONotifyRequestBody {
  String eventType;
  RONotifyPipelineInfo pipeline;
  Map<String, String> metadata;
  Artifacts artifacts;

  @Value
  @Builder
  public static class RONotifyPipelineInfo {
    String orgId;
    String projectId;
    String identifier;
    String executionId;
    // Runtime id of the RO Notify step.
    String stepExecutionId;
    // Runtime id of the nearest STAGE-category ambiance level; null when none.
    // Joins to RM's stage_execution_id column on the activity model.
    String stageExecutionId;
  }

  @Value
  @Builder
  public static class Artifacts {
    List<Map<String, Object>> images;
    List<Map<String, Object>> files;
    List<Map<String, Object>> sbom;
  }
}

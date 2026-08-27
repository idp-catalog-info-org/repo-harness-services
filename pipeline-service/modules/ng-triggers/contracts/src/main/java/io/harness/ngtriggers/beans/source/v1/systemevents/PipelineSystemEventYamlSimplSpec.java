/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.systemevents;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;

import java.util.List;
import lombok.Value;

/**
 * V1 (3.0 YAML) spec for a pipeline system-event trigger.
 *
 * <p>Example:
 * <pre>
 * spec:
 *   type: system-event
 *   spec:
 *     type: pipeline
 *     spec:
 *       eventType: pipeline-failure
 *       payloadConditions:
 *         - key: sourcePipeline
 *           operator: Equals
 *           value: upstream-pipeline
 * </pre>
 */
@Value
@OwnedBy(PIPELINE)
public class PipelineSystemEventYamlSimplSpec implements SystemEventYamlSimplSpec {
  /** e.g. {@code "pipeline-failure"} or {@code "pipeline-success"} */
  String eventType;

  /**
   * Optional conditions on the event payload. The only supported key is {@code "sourcePipeline"}.
   * If empty or null, the trigger fires for any source pipeline in the same project.
   */
  List<TriggerEventDataCondition> payloadConditions;
}

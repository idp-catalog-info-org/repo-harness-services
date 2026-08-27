/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.systemevents;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Spec for a pipeline system-event trigger.
 *
 * <p>Example YAML (V2):
 * <pre>
 * source:
 *   type: SystemEvent
 *   spec:
 *     type: Pipeline
 *     spec:
 *       eventType: PipelineFailure
 *       payloadConditions:
 *         - key: sourcePipeline
 *           operator: Equals
 *           value: my-upstream-pipeline
 * </pre>
 *
 * <p>Supported condition key: {@code sourcePipeline} — the identifier of the pipeline whose
 * event triggered the evaluation. Supports all {@link io.harness.ngtriggers.conditionchecker.ConditionOperator}
 * operators (Equals, Regex, In, NotIn, StartsWith, EndsWith, Contains, DoesNotContain, NotEquals),
 * following the same model as git trigger branch filters.
 * An empty {@code payloadConditions} list (or null) matches any source pipeline.
 */
@Data
@NoArgsConstructor
@OwnedBy(PIPELINE)
public class PipelineSystemEventSpec implements SystemEventSpec {
  /** The platform event type to react to (e.g. {@code Failure}, {@code Success}). */
  SystemEventType eventType;

  /**
   * Optional conditions on the event payload. The only supported key is {@code "sourcePipeline"}.
   * If empty or null, the trigger fires for any source pipeline in the same project.
   */
  List<TriggerEventDataCondition> payloadConditions;

  @Builder
  public PipelineSystemEventSpec(SystemEventType eventType, List<TriggerEventDataCondition> payloadConditions) {
    this.eventType = eventType;
    this.payloadConditions = payloadConditions;
  }
}

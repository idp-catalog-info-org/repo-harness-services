/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.artifact;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.v1.systemevents.SystemEventYamlSimplSpec;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Value;

/**
 * V1 (3.0 YAML) top-level config for {@code spec.type: system-event} triggers.
 *
 * <p>Example YAML:
 * <pre>
 * spec:
 *   type: system-event
 *   spec:
 *     type: pipeline
 *     spec:
 *       eventType: pipeline-failure
 *       sourcePipelineIdentifier: "upstream-pipeline"   # optional
 * </pre>
 */
@Value
@OwnedBy(PIPELINE)
public class SystemEventTriggerYamlSimplConfig implements NGTriggerYamlSimplSpec {
  /** Sub-category, e.g. {@code "pipeline"}. */
  String type;

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = EXTERNAL_PROPERTY, property = "type", visible = true)
  SystemEventYamlSimplSpec spec;
}

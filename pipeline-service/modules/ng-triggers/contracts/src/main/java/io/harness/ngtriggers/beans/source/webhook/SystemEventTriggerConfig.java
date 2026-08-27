/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.systemevents.SystemEventSpec;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level trigger config for {@code source.type: SystemEvent}.
 *
 * <p>Example YAML (ng / V2 format):
 * <pre>
 * source:
 *   type: SystemEvent
 *   spec:
 *     type: Pipeline
 *     spec:
 *       eventType: PipelineFailure
 *       sourcePipelineIdentifier: "upstream-pipeline"   # optional
 * </pre>
 *
 * <p>The {@code type} field discriminates the {@link SystemEventSpec} sub-spec (e.g. {@code Pipeline}).
 */
@Data
@NoArgsConstructor
@OwnedBy(PIPELINE)
public class SystemEventTriggerConfig implements NGTriggerSpecV2 {
  /** Sub-category, e.g. {@code "Pipeline"}. */
  String type;

  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true) SystemEventSpec spec;

  @Builder
  public SystemEventTriggerConfig(String type, SystemEventSpec spec) {
    this.type = type;
    this.spec = spec;
  }
}

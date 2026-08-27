/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.gcp;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.gcp.GCEImageFilter;
import io.harness.delegate.task.artifacts.gcp.GCEImageLabel;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDC)
@Schema(name = "GCEImageRequestBody", description = "This has details of the GCE Image Labels and Filters")
public class GCEImageRequestBody {
  @Schema(description = "Runtime input YAML") String runtimeInputYaml;

  @Schema(description = "GCE image labels for filtering") List<GCEImageLabel> labels;

  @Schema(description = "GCE image filters for filtering") List<GCEImageFilter> filters;
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.goconvert.EntityType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PIPELINE)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversionNodeSummary {
  String entityIdentifier;
  String versionLabel;
  String v1Identifier;
  EntityType entityType;
  ConversionStatus status;
  String errorMessage;
  PipelineConversionMetricsDTO pipelineMetrics;
  List<ConversionErrorDetail> errors;
  List<ConversionNodeSummary> children;
}

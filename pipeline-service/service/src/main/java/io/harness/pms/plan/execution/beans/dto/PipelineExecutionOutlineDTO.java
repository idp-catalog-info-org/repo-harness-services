/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.validator.Trimmed;
import io.harness.pms.execution.ExecutionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("PipelineExecutionOutline")
@Schema(name = "PipelineExecutionOutline", description = "This is the view of the Pipeline Execution Outline")
public class PipelineExecutionOutlineDTO {
  @NotEmpty String accountIdentifier;
  @NotEmpty String orgIdentifier;
  @Trimmed @NotEmpty String projectIdentifier;
  String pipelineIdentifier;
  String planExecutionId;
  String name;
  String startingNodeId;
  List<String> startingNodeIds; // For DAG support - multiple root nodes can start simultaneously
  Boolean isDagEnabled; // True when DAG execution is enabled for this pipeline
  Map<String, List<String>> dependencyGraph; // Stage dependency graph (nodeId -> list of dependency nodeIds)
  ExecutionStatus status;
  String failureInfo;
  Map<String, NodeExecutionOutlineDTO> stagesMap;
  List<String> modules;
  Long startTs;
  Long endTs;
  Long createdAt;
  Long lastUpdatedAt;
  String runtimeInputYaml;
  int runSequence;
}

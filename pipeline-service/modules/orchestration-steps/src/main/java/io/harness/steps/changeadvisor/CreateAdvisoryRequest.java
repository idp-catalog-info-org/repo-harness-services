/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.changeadvisor;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CV)
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateAdvisoryRequest {
  String pipelineEngine;
  String stageType;
  PipelineContext pipelineContext;
  ExecutionRef execution;
  ServiceRef service;
  EnvironmentRef environment;
  ArtifactRef artifact;
  DeployerRef deployer;
  EvaluationOptions options;
  List<String> presets;

  @Value
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class PipelineContext {
    String accountId;
    String orgId;
    String projectId;
    String pipelineId;
    String pipelineVersion;
    String triggerType;
    Map<String, String> metadata;
  }

  @Value
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ExecutionRef {
    String pipelineExecutionId;
    String stageExecutionId;
    String planExecutionId;
    Long runSequence;
  }

  @Value
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ServiceRef {
    String id;
    String name;
  }

  @Value
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class EnvironmentRef {
    String id;
    String name;
    String type;
  }

  @Value
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ArtifactRef {
    String name;
    String tag;
    String digest;
    String source;
  }

  @Value
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class DeployerRef {
    String principalId;
    String principalType;
    String principalName;
  }

  @Value
  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class EvaluationOptions {
    Boolean dryRun;
    Boolean requireApproval;
    Integer timeoutSeconds;
  }
}

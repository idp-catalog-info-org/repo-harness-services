/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.GITOPS)
@Value
@Builder
public class GitOpsStageMetadata {
  String planExecutionId;
  String serviceId; // cd_stage_execution.service_id
  String envId; // cd_stage_execution.env_id
  String stageExecutionId; // stage_execution.stage_execution_id — the runtime node execution UUID needed by
                           // triggerPostExecutionRollback
  String stageStatus;

  // Post-CDS-114264 a multi-service/multi-env GitOps pipeline emits one stage per (service, env) under a single
  // plan_execution_id, so stage metadata must be resolved per (planExecution, service, env) rather than per plan.
  // '|' is a safe delimiter: plan_execution_id is a UUID and NG identifiers cannot contain it.
  public static String buildKey(String planExecutionId, String serviceId, String envId) {
    return planExecutionId + "|" + serviceId + "|" + envId;
  }
}

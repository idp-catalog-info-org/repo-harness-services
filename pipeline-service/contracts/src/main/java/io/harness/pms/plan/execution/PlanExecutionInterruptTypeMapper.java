/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.pms.contracts.commons.RepairActionCode;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PlanExecutionInterruptTypeMapper {
  public RepairActionCode toRepairActionCode(PlanExecutionInterruptType interruptType) {
    if (interruptType == null) {
      return null;
    }
    switch (interruptType) {
      case ABORTALL:
      case ABORT:
        return RepairActionCode.END_EXECUTION;
      case PAUSE:
      case RESUME:
      case EXPIREALL:
      case UserMarkedFailure:
        return RepairActionCode.UNKNOWN;
      case IGNORE:
        return RepairActionCode.IGNORE;
      case STAGEROLLBACK:
        return RepairActionCode.STAGE_ROLLBACK;
      case PIPELINEROLLBACK:
        return RepairActionCode.PIPELINE_ROLLBACK;
      case STEPGROUPROLLBACK:
        return RepairActionCode.STEP_GROUP_ROLLBACK;
      case MARKASSUCCESS:
        return RepairActionCode.MARK_AS_SUCCESS;
      case RETRY:
        return RepairActionCode.RETRY;
      case MarkAsFailure:
        return RepairActionCode.MARK_AS_FAILURE;
      case RETRYSTEPGROUP:
        return RepairActionCode.RETRY_STEP_GROUP;
      default:
        return null;
    }
  }
}

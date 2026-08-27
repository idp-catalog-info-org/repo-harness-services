/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import static io.harness.govern.Switch.unhandled;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.notification.PipelineEventType;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.utils.execution.ExecutionModeUtils;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PipelineNotificationUtils {
  public static String getStatusForImage(Status status) {
    if (status == null) {
      return "running";
    }
    switch (status) {
      case SUCCEEDED:
      case IGNORE_FAILED:
        return "completed";
      case FAILED:
      case ERRORED:
        return PipelineNotificationConstants.FAILED_STATUS;
      case PAUSED:
        return "paused";
      case ABORTED:
        return "aborted";
      case APPROVAL_REJECTED:
        return "rejected";
      case EXPIRED:
        return "expired";
      case INTERVENTION_WAITING, APPROVAL_WAITING, INPUT_WAITING, UPLOAD_WAITING:
        return "action_required";
      case ASYNC_WAITING:
      case QUEUED:
      case RUNNING:
      case QUEUED_STEP_LIMIT_REACHED:
      case STARTING_QUEUED_STEP:
        return "resumed";
      default:
        unhandled(status);
        return PipelineNotificationConstants.FAILED_STATUS;
    }
  }

  public static String getNodeStatus(
      Status status, PipelineEventType pipelineEventType, boolean changeNotifactionEventMessage) {
    if (status == null) {
      return "started";
    }
    if (pipelineEventType.equals(PipelineEventType.PIPELINE_RESUMED)) {
      return "resumed";
    }
    if (changeNotifactionEventMessage) {
      if (pipelineEventType.equals(PipelineEventType.PIPELINE_END)) {
        return "ended";
      }
    }

    switch (status) {
      case SUCCEEDED:
      case IGNORE_FAILED:
        return changeNotifactionEventMessage ? "succeeded" : "completed";
      case FAILED:
      case ERRORED:
        return "failed";
      case PAUSED:
        return "paused";
      case ABORTED:
        return "aborted";
      case APPROVAL_REJECTED:
        return "rejected";
      case EXPIRED:
        return "expired";
      case INTERVENTION_WAITING, APPROVAL_WAITING, INPUT_WAITING, UPLOAD_WAITING:
        return "action_required";
      case ASYNC_WAITING:
      case QUEUED:
      case RUNNING:
        return "started";
      default:
        unhandled(status);
        return "started";
    }
  }

  public static String getThemeColor(Status status) {
    switch (status) {
      case SUCCEEDED:
      case IGNORE_FAILED:
        return PipelineNotificationConstants.SUCCEEDED_COLOR;
      case EXPIRED:
      case APPROVAL_REJECTED:
      case FAILED:
        return PipelineNotificationConstants.FAILED_COLOR;
      case PAUSED:
        return PipelineNotificationConstants.PAUSED_COLOR;
      case ABORTED:
        return PipelineNotificationConstants.ABORTED_COLOR;
      default:
        return PipelineNotificationConstants.BLUE_COLOR;
    }
  }

  public static boolean shouldUseOriginalNodeToGetModuleInfo(Ambiance ambiance) {
    return ExecutionModeUtils.isRollbackMode(ambiance.getMetadata().getExecutionMode())
        && NodeType.IDENTITY_PLAN_NODE.equals(
            NodeType.valueOf(AmbianceUtils.obtainCurrentLevel(ambiance).getNodeType()))
        && AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.CDS_USE_PARENT_NODE_TO_GET_MODULE_INFO.name());
  }
}

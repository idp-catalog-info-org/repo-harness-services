/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.engine.pms.tasks.TaskExecutor;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.TaskChainExecutableResponse;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterruptHelper {
  @Inject private Map<TaskCategory, TaskExecutor> taskExecutorMap;

  /**
   * @deprecated use {@link #evaluateUnitProgressesFromProgressData(NodeExecution, UnitStatus)} instead.
   */
  @VisibleForTesting
  @Deprecated
  public static List<UnitProgress> evaluateUnitProgresses(NodeExecution nodeExecution, UnitStatus unitStatus) {
    return evaluateUnitProgress(nodeExecution.getUnitProgresses(), unitStatus);
  }

  private static List<UnitProgress> evaluateUnitProgress(List<UnitProgress> unitProgresses, UnitStatus unitStatus) {
    List<UnitProgress> unitProgressList = new ArrayList<>();
    if (!EmptyPredicate.isEmpty(unitProgresses)) {
      for (UnitProgress up : unitProgresses) {
        if (isFinalUnitProgress(up.getStatus())) {
          unitProgressList.add(up);
        } else {
          unitProgressList.add(up.toBuilder().setStatus(unitStatus).setEndTime(System.currentTimeMillis()).build());
        }
      }
    }
    return unitProgressList;
  }

  private static boolean isFinalUnitProgress(UnitStatus status) {
    return EnumSet.of(UnitStatus.FAILURE, UnitStatus.SKIPPED, UnitStatus.SUCCESS).contains(status);
  }

  /**
   * utility to evaluate unit progress from progress data
   * @param nodeExecution
   * @param unitStatus
   * @throws io.harness.exceptions.RecasterException if there is no UnitProgressData present inside nodeExecution's
   *     progressData
   */
  public static List<UnitProgress> evaluateUnitProgressesFromProgressData(
      NodeExecution nodeExecution, UnitStatus unitStatus) {
    Map<String, Object> progressData = nodeExecution.getProgressData();
    if (EmptyPredicate.isEmpty(progressData)) {
      return new ArrayList<>();
    }
    UnitProgressData unitProgressData = RecastOrchestrationUtils.fromMap(progressData, UnitProgressData.class);

    return evaluateUnitProgress(unitProgressData.getUnitProgresses(), unitStatus);
  }

  public boolean discontinueTaskIfRequired(NodeExecution nodeExecution) {
    ExecutableResponse executableResponse = nodeExecution.obtainLatestExecutableResponse();
    if (executableResponse == null || !ExecutionModeUtils.isTaskMode(nodeExecution.getMode())) {
      return true;
    }
    String taskId;
    TaskCategory taskCategory;
    switch (executableResponse.getResponseCase()) {
      case TASK:
        TaskExecutableResponse taskExecutableResponse = executableResponse.getTask();
        taskId = taskExecutableResponse.getTaskId();
        taskCategory = taskExecutableResponse.getTaskCategory();
        break;
      case TASKCHAIN:
        TaskChainExecutableResponse taskChainExecutableResponse = executableResponse.getTaskChain();
        taskId = taskChainExecutableResponse.getTaskId();
        taskCategory = taskChainExecutableResponse.getTaskCategory();
        break;
      default:
        throw new InvalidRequestException("Executable Response should contain either task or taskChain");
    }
    TaskExecutor executor = taskExecutorMap.get(taskCategory);
    return executor.abortTask(nodeExecution.getSetupAbstractionsMap(), taskId);
  }
}

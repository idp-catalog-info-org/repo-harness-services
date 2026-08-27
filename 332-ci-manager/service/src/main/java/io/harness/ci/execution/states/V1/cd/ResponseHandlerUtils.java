/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.ci.execution.utils.ci.CIStepInfoUtils.getDefaultCIFailureDataInfo;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepOutputV2;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.product.ci.engine.proto.OutputVariable;
import io.harness.security.SimpleEncryption;
import io.harness.tasks.ResponseData;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResponseHandlerUtils {
  private static final String SWEEPING_OUTPUT_SECRET_OBTAIN_PREFIX = "${sweepingOutputSecrets.obtain(\"";
  private static final String PLUGIN_EXECUTION_ERROR = "PLUGIN_EXECUTION_ERROR";

  public VmTaskExecutionResponse filterVmStepResponse(Map<String, ResponseData> responseDataMap) {
    // Filter final response from step
    return responseDataMap.entrySet()
        .stream()
        .filter(entry -> entry.getValue() instanceof VmTaskExecutionResponse)
        .filter(entry
            -> ((VmTaskExecutionResponse) entry.getValue()).getCommandExecutionStatus()
                != CommandExecutionStatus.RUNNING)
        .findFirst()
        .map(obj -> (VmTaskExecutionResponse) obj.getValue())
        .orElse(null);
  }

  public Map<String, String> getOutputVariables(List<StepOutputV2> outputVariables) {
    Map<String, String> resolvedOutputVariables = new HashMap<>();

    if (isNotEmpty(outputVariables)) {
      SimpleEncryption encryption = new SimpleEncryption();
      outputVariables.forEach(outputVariable -> {
        if (OutputVariable.OutputType.SECRET.toString().equals(outputVariable.getType())
            && isNotEmpty(outputVariable.getValue())) {
          String encodedValue = EncodingUtils.encodeBase64(
              encryption.encrypt(outputVariable.getValue().getBytes(StandardCharsets.UTF_8)));
          String finalValue =
              SWEEPING_OUTPUT_SECRET_OBTAIN_PREFIX + outputVariable.getKey() + "\",\"" + encodedValue + "\")}";
          resolvedOutputVariables.put(outputVariable.getKey(), finalValue);
        } else {
          resolvedOutputVariables.put(outputVariable.getKey(), outputVariable.getValue());
        }
      });
    }
    return resolvedOutputVariables;
  }

  public String getFailureErrorMsg(Map<String, String> outputVariables, String defaultErrorMsg, String vmErrorMsg) {
    String failureErrorMsg;
    if (isNotEmpty(outputVariables) && outputVariables.containsKey(PLUGIN_EXECUTION_ERROR)) {
      failureErrorMsg = outputVariables.get(PLUGIN_EXECUTION_ERROR);
    } else {
      failureErrorMsg = defaultErrorMsg + (isNotEmpty(vmErrorMsg) ? vmErrorMsg : "");
    }
    return failureErrorMsg;
  }

  public StepResponse getGenericFailedStepResponse(Ambiance ambiance, String defaultErrorMsg, String failureDataMsg) {
    return StepResponse.builder()
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .setErrorMessage(defaultErrorMsg)
                         .addFailureData(getDefaultCIFailureDataInfo(failureDataMsg, ambiance))
                         .build())
        .build();
  }

  /**
   * Maps a delegate response (K8s {@link StepStatusTaskResponseData} / {@link ErrorNotifyResponseData}
   * or VM {@link VmTaskExecutionResponse}) to a {@link UnitStatus} value. Used by individual
   * swimlane steps to record per-resource (manifest / artifact / config file) unit statuses for
   * the unit-progress timeline without intruding on the chain-control flow in the primary
   * failure handlers.
   *
   * <p>For unknown response types we default to {@link UnitStatus#UNKNOWN} rather than guessing.
   */
  public static UnitStatus getUnitStatus(ResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      return UnitStatus.FAILURE;
    }
    if (responseData instanceof StepStatusTaskResponseData stepStatusTaskResponseData) {
      if (stepStatusTaskResponseData.getStepStatus() == null) {
        return UnitStatus.FAILURE;
      }
      return mapStepExecutionStatusToUnitStatus(stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus());
    }
    if (responseData instanceof VmTaskExecutionResponse vmTaskExecutionResponse) {
      CommandExecutionStatus commandExecutionStatus = vmTaskExecutionResponse.getCommandExecutionStatus();
      // CommandExecutionStatus already exposes a UnitStatus mapping for every value.
      return commandExecutionStatus == null ? UnitStatus.UNKNOWN : commandExecutionStatus.getUnitStatus();
    }
    return UnitStatus.UNKNOWN;
  }

  private static UnitStatus mapStepExecutionStatusToUnitStatus(StepExecutionStatus stepExecutionStatus) {
    if (stepExecutionStatus == null) {
      return UnitStatus.UNKNOWN;
    }
    return switch (stepExecutionStatus) {
      case SUCCESS -> UnitStatus.SUCCESS;
      case FAILURE, ABORTED -> UnitStatus.FAILURE;
      case RUNNING -> UnitStatus.RUNNING;
      case QUEUED -> UnitStatus.QUEUED;
      case SKIPPED -> UnitStatus.SKIPPED;
      default -> UnitStatus.UNKNOWN;
    };
  }

  public static CommandExecutionStatus getCommandExecutionStatusForK8s(StepExecutionStatus stepExecutionStatus) {
    switch (stepExecutionStatus) {
      case FAILURE, ABORTED -> {
        return CommandExecutionStatus.FAILURE;
      }
      case RUNNING -> {
        return CommandExecutionStatus.RUNNING;
      }
      case QUEUED -> {
        return CommandExecutionStatus.QUEUED;
      }
      case SKIPPED -> {
        return CommandExecutionStatus.SKIPPED;
      }
      default -> {
        return CommandExecutionStatus.SUCCESS;
      }
    }
  }
}

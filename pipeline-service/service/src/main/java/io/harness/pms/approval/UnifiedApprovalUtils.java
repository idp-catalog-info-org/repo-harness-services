/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.approval.UnifiedApprovalConstants.PLUGIN_EXECUTION_ERROR_ENV;
import static io.harness.pms.approval.UnifiedApprovalConstants.PLUGIN_EXECUTION_ERROR_NO_DETAILS_MSG;
import static io.harness.pms.approval.UnifiedApprovalConstants.PLUGIN_EXECUTION_FAILURE_TYPE_ENV;
import static io.harness.pms.approval.UnifiedApprovalConstants.PLUGIN_EXECUTION_FAILURE_TYPE_UNKNOWN;
import static io.harness.pms.approval.UnifiedApprovalConstants.PLUGIN_EXECUTION_STATUS_ENV;
import static io.harness.pms.approval.UnifiedApprovalConstants.PLUGIN_EXECUTION_STATUS_FAILURE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepOutputV2;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.exception.ApprovalStepNGException;
import io.harness.exception.InvalidRequestException;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.product.ci.engine.proto.OutputVariable;
import io.harness.security.SimpleEncryption;
import io.harness.servicenow.ServiceNowFieldValueNG;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.servicenow.mixin.ServiceNowFieldValueNGMixIn;
import io.harness.servicenow.mixin.ServiceNowTicketNGMixIn;
import io.harness.tasks.ResponseData;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@Slf4j
public class UnifiedApprovalUtils {
  public static final String SWEEPING_OUTPUT_SECRET_OBTAIN_PREFIX = "${sweepingOutputSecrets.obtain(\"";
  private final SerializedResponseDataHelper serializedResponseDataHelper;
  private final ObjectMapper objectMapper;

  @Inject
  public UnifiedApprovalUtils(SerializedResponseDataHelper serializedResponseDataHelper, ObjectMapper objectMapper) {
    this.serializedResponseDataHelper = serializedResponseDataHelper;
    this.objectMapper = objectMapper;
  }

  public Map<String, String> getOutputVariables(
      List<StepOutputV2> outputVariables, Map<String, String> defaultOutputVariables, String accountId) {
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
      return resolvedOutputVariables;
    }
    return defaultOutputVariables;
  }

  public Map<String, String> handleUnifiedApprovalResponse(ResponseData responseData, String accountId) {
    ResponseData data = serializedResponseDataHelper.deserialize(responseData);
    Map<String, String> outVars = Collections.emptyMap();
    if (data instanceof VmTaskExecutionResponse vmTaskResponse) {
      outVars = getOutputVariables(vmTaskResponse.getOutputs(), vmTaskResponse.getOutputVars(), accountId);
    } else if (data instanceof StepStatusTaskResponseData stepStatusTaskResponseData) {
      if (stepStatusTaskResponseData.getStepStatus() == null) {
        return Collections.emptyMap();
      }
      Object output = stepStatusTaskResponseData.getStepStatus().getOutput();
      if (!(output instanceof StepMapOutput)) {
        return Collections.emptyMap();
      }
      outVars = getOutputVariables(
          stepStatusTaskResponseData.getStepStatus().getOutputV2(), ((StepMapOutput) output).getMap(), accountId);
    }
    throwExceptionIfExecutionFailed(data, outVars);
    return isEmpty(outVars) ? Collections.emptyMap() : outVars;
  }

  private void throwExceptionIfExecutionFailed(ResponseData data, Map<String, String> outVars) {
    if (isNotEmpty(outVars) && outVars.containsKey(PLUGIN_EXECUTION_STATUS_ENV)
        && PLUGIN_EXECUTION_STATUS_FAILURE.equals(outVars.get(PLUGIN_EXECUTION_STATUS_ENV))) {
      String failureType =
          outVars.getOrDefault(PLUGIN_EXECUTION_FAILURE_TYPE_ENV, PLUGIN_EXECUTION_FAILURE_TYPE_UNKNOWN);
      String errorMessage = outVars.getOrDefault(PLUGIN_EXECUTION_ERROR_ENV, PLUGIN_EXECUTION_ERROR_NO_DETAILS_MSG);
      throw new InvalidRequestException(
          String.format("Plugin execution failed. Failure Type: [%s], Message: %s", failureType, errorMessage));
    }

    boolean hasFailed = false;
    String errorMsg = PLUGIN_EXECUTION_ERROR_NO_DETAILS_MSG;
    if (data instanceof VmTaskExecutionResponse vmTaskResponse) {
      if (vmTaskResponse.getCommandExecutionStatus() != null) {
        CommandExecutionStatus status = vmTaskResponse.getCommandExecutionStatus();
        if (CommandExecutionStatus.FAILURE.equals(status)) {
          if (isNotEmpty(vmTaskResponse.getErrorMessage())) {
            errorMsg = vmTaskResponse.getErrorMessage();
          }
          hasFailed = true;
        }
      }
    } else if (data instanceof StepStatusTaskResponseData stepStatusTaskResponseData) {
      if (stepStatusTaskResponseData.getStepStatus() != null
          && stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus() != null) {
        StepExecutionStatus stepExecutionStatus = stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus();
        if (StepExecutionStatus.FAILURE.equals(stepExecutionStatus)) {
          if (isNotEmpty(stepStatusTaskResponseData.getStepStatus().getError())) {
            errorMsg = stepStatusTaskResponseData.getStepStatus().getError();
          }
          hasFailed = true;
        }
      }
    }

    if (hasFailed) {
      throw new ApprovalStepNGException(String.format("Execution failed, message: %s", errorMsg), true);
    }
  }

  public void sanitizeOutputVariables(Map<String, String> outVars) {
    outVars.remove(PLUGIN_EXECUTION_STATUS_ENV);
    outVars.remove(PLUGIN_EXECUTION_FAILURE_TYPE_ENV);
    outVars.remove(PLUGIN_EXECUTION_ERROR_ENV);
  }

  public <T> T deserializeOutputVariable(String varJson, Class<T> varType) {
    if (isEmpty(varJson)) {
      return null;
    }
    if (varType == null) {
      throw new InvalidRequestException("Variable type cannot be null for deserialization");
    }

    try {
      if (varType == ServiceNowTicketNG.class) {
        return (T) deserializeServiceNowTicketNG(varJson, objectMapper.copy());
      }

      return objectMapper.readValue(varJson, varType);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException(String.format("Failed to deserialize output variable of type [%s]: %s",
                                            varType.getSimpleName(), e.getMessage()),
          e);
    }
  }

  @Nullable
  private ServiceNowTicketNG deserializeServiceNowTicketNG(String json, ObjectMapper mapper)
      throws JsonProcessingException {
    // Create a custom mapper that overrides the deserializer on ServiceNowTicketNG
    mapper.addMixIn(ServiceNowTicketNG.class, ServiceNowTicketNGMixIn.class);
    mapper.addMixIn(ServiceNowFieldValueNG.class, ServiceNowFieldValueNGMixIn.class);
    // This removes ServiceNowTicketDeserializer deserializer from ServiceNowTicketNG via MixIn
    mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
      @Override
      public Object findDeserializer(Annotated a) {
        if (a.getRawType() == ServiceNowTicketNG.class) {
          return null; // Use default deserializer
        }
        return super.findDeserializer(a);
      }
    });

    return mapper.readValue(json, ServiceNowTicketNG.class);
  }
}

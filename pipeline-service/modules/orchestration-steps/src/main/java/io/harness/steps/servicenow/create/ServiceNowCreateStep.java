/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.create;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.STATE_FIELD;
import static io.harness.delegate.task.shell.ShellScriptTaskNG.COMMAND_UNIT;
import static io.harness.steps.servicenow.create.ServiceNowCreateSpecParameters.getAction;
import static io.harness.steps.servicenow.create.ServiceNowCreateSpecParameters.getUseServiceNowTemplate;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters.ServiceNowTaskNGParametersBuilder;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGResponse;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.engine.executions.step.StepExecutionEntityService;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.step.ServiceNowCreateStepExecutionDetails;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.servicenow.ServiceNowFieldValueNG;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.executables.PipelineTaskExecutable;
import io.harness.steps.servicenow.ServiceNowStepHelperService;
import io.harness.steps.servicenow.beans.ServiceNowCreateType;
import io.harness.steps.servicenow.beans.ServiceNowStepUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public class ServiceNowCreateStep extends PipelineTaskExecutable<ServiceNowTaskNGResponse> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.SERVICE_NOW_CREATE_STEP_TYPE;

  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private ServiceNowStepHelperService serviceNowStepHelperService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private StepExecutionEntityService stepExecutionEntityService;
  private static final String CHANGE_REQUEST = "CHANGE_REQUEST";

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    ServiceNowCreateSpecParameters specParameters = (ServiceNowCreateSpecParameters) stepParameters.getSpec();
    String connectorRef = specParameters.getConnectorRef().getValue();
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    List<EntityDetail> entityDetailList = new ArrayList<>();
    entityDetailList.add(entityDetail);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    ServiceNowCreateSpecParameters specParameters = (ServiceNowCreateSpecParameters) stepParameters.getSpec();
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
    logStreamingStepClient.openStream(ShellScriptTaskNG.COMMAND_UNIT);
    try {
      validateStandardTemplateAndTicketType(specParameters);
      checkOneoffCreateTypeOrUseServiceNowTemplate(specParameters);
      ServiceNowTaskNGParametersBuilder paramsBuilder =
          ServiceNowTaskNGParameters.builder()
              .action(getAction(specParameters))
              .ticketType(specParameters.getTicketType().getValue())
              .templateName(specParameters.getTemplateName().getValue())
              .useServiceNowTemplate(getUseServiceNowTemplate(specParameters))
              .delegateSelectors(
                  StepUtils.getDelegateSelectorListFromTaskSelectorYaml(specParameters.getDelegateSelectors()))
              .fields(ServiceNowStepUtils.processServiceNowFieldsInSpec(specParameters.getFields(), logCallback));
      return serviceNowStepHelperService.prepareTaskRequest(paramsBuilder, ambiance,
          specParameters.getConnectorRef().getValue(), stepParameters.getTimeout().getValue(),
          "ServiceNow Task: Create Ticket", TaskSelectorYaml.toTaskSelector(specParameters.getDelegateSelectors()));
    } catch (InvalidRequestException ex) {
      closeLogStream(ambiance);
      throw ex;
    }
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      ThrowingSupplier<ServiceNowTaskNGResponse> responseSupplier) throws Exception {
    try {
      ServiceNowTaskNGResponse taskResponse = responseSupplier.get();
      ServiceNowCreateSpecParameters specParameters = (ServiceNowCreateSpecParameters) stepParameters.getSpec();
      StepResponse stepResponse = serviceNowStepHelperService.prepareStepResponse(() -> taskResponse);
      updateServiceNowCreateStepExecutionDetails(ambiance, taskResponse, stepParameters.getName(),
          specParameters.getTicketType().getValue(), stepResponse.getStatus());
      return stepResponse;
    } catch (Exception ex) {
      updateStepExecutionEntityOnFailure(ambiance, stepParameters.getName(), ex.getMessage());
      throw ex;
    } finally {
      closeLogStream(ambiance);
    }
  }

  private void updateServiceNowCreateStepExecutionDetails(
      Ambiance ambiance, ServiceNowTaskNGResponse taskResponse, String stepName, String ticketType, Status status) {
    try {
      if (taskResponse != null && taskResponse.getTicket() != null) {
        ServiceNowTicketNG ticket = taskResponse.getTicket();
        String ticketStatus = null;
        if (ticket.getFields() != null && ticket.getFields().containsKey(STATE_FIELD)) {
          ServiceNowFieldValueNG stateField = ticket.getFields().get(STATE_FIELD);
          if (stateField != null) {
            ticketStatus = stateField.getDisplayValue();
          }
        }
        ServiceNowCreateStepExecutionDetails executionDetails = ServiceNowCreateStepExecutionDetails.builder()
                                                                    .url(ticket.getUrl())
                                                                    .ticketNumber(ticket.getNumber())
                                                                    .ticketType(ticketType)
                                                                    .ticketStatus(ticketStatus)
                                                                    .build();
        stepExecutionEntityService.updateStepExecutionEntity(ambiance, null, executionDetails, stepName, status);
      }
    } catch (Exception ex) {
      log.warn("Failed to update step execution entity for ServiceNow Create step", ex);
    }
  }

  private void updateStepExecutionEntityOnFailure(Ambiance ambiance, String stepName, String errorMessage) {
    try {
      FailureInfo failureInfo =
          FailureInfo.newBuilder()
              .addFailureData(FailureData.newBuilder().setLevel(Level.ERROR.name()).setMessage(errorMessage).build())
              .build();
      stepExecutionEntityService.updateStepExecutionEntity(ambiance, failureInfo, null, stepName, Status.FAILED);
    } catch (Exception ex) {
      log.warn("Failed to update step execution entity on failure for ServiceNow Create step", ex);
    }
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepParameters,
      TaskExecutableResponse executableResponse, boolean userMarked) {
    closeLogStream(ambiance);
  }

  @Override
  public StepExecutionTelemetryEventDTO getStepExecutionTelemetryEventDTO(
      Ambiance ambiance, StepBaseParameters stepParameters) {
    return StepExecutionTelemetryEventDTO.builder().stepType(STEP_TYPE.getType()).build();
  }

  private void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeStream(COMMAND_UNIT);
  }

  public void validateStandardTemplateAndTicketType(ServiceNowCreateSpecParameters specParameters) {
    if (ParameterField.isNotNull(specParameters.getTicketType())
        && !specParameters.getTicketType().getValue().equalsIgnoreCase(CHANGE_REQUEST)
        && specParameters.getCreateType() == ServiceNowCreateType.STANDARD) {
      throw new InvalidRequestException("ServiceNow create using standard template is supported only for ticket type "
          + "change Request for ServiceNowCreate step");
    }
  }
  public void checkOneoffCreateTypeOrUseServiceNowTemplate(ServiceNowCreateSpecParameters specParameters) {
    if (ParameterField.isBlank(specParameters.getUseServiceNowTemplate()) && specParameters.getCreateType() == null) {
      throw new InvalidRequestException("One of createType or useServiceNowTemplate is required when creating ticket "
          + "from ServiceNow in ServiceNowCreate step");
    }

    if (!ParameterField.isBlank(specParameters.getUseServiceNowTemplate()) && specParameters.getCreateType() != null) {
      throw new InvalidRequestException("Only one of createType or useServiceNowTemplate should be present when "
          + "creating ticket from ServiceNow in ServiceNowCreate step");
    }
  }
}

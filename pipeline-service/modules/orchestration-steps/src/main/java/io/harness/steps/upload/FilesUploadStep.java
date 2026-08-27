/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.steps.upload;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.execution.Status.UPLOAD_WAITING;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.UUIDGenerator;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.exception.EntityNotFoundException;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.repositories.RuntimeFileInputDataRepository;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.executables.PipelineAsyncExecutable;
import io.harness.steps.upload.RuntimeFileInputData.RuntimeFileInputDataKeys;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * This step is used when we want to upload file during execution as evidence
 * This Step will wait until the file is uploaded to resume the execution similar to the current {Wait} step
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
public class FilesUploadStep extends PipelineAsyncExecutable {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.UPLOAD_STEP_TYPE;
  public static final String COMMAND_UNIT = "Execute";

  @Inject RuntimeFileInputDataRepository runtimeFileInputDataRepository;

  @Inject LogStreamingStepClientFactory logStreamingStepClientFactory;

  private static final Integer MAXIMUM_UPLOAD_STEPS_IN_PIPELINE_RESTRICTION = 10;

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    openLogStream(ambiance);
    validateNumberOfUploadSteps(AmbianceUtils.getPlanExecutionIdForExecutionMode(ambiance));
    String correlationId = UUIDGenerator.generateUuid();
    runtimeFileInputDataRepository.save(RuntimeFileInputData.builder()
                                            .uuid(correlationId)
                                            .createdAt(System.currentTimeMillis())
                                            .nodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                                            .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
                                            .planExecutionId(AmbianceUtils.getPlanExecutionIdForExecutionMode(ambiance))
                                            .build());
    return AsyncExecutableResponse.newBuilder()
        .setStatus(UPLOAD_WAITING)
        .addCallbackIds(correlationId)
        .addAllLogKeys(CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(
            StepUtils.generateLogAbstractions(ambiance), Collections.singletonList(COMMAND_UNIT))))
        .build();
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
    RuntimeFileInputData runtimeFileInputData = runtimeFileInputDataRepository.findByNodeExecutionId(nodeExecutionId);
    if (runtimeFileInputData == null) {
      log.error(String.format("No File input data found nodeExecutionId [%s]", nodeExecutionId));
      logCallback.saveExecutionLog("No input files found...Failing the step", LogLevel.ERROR);
      throw new EntityNotFoundException(
          String.format("We could not process the request as no file data found for the given node execution id [%s]",
              nodeExecutionId));
    }
    List<String> fileNames =
        runtimeFileInputData.getFileInfos().stream().map(FileInfo::getFilePath).collect(Collectors.toList());
    FilesUploadOutcome uploadOutcome =
        new FilesUploadOutcome(runtimeFileInputData.getSubmittedBy().getEmail(), fileNames);
    logCallback.saveExecutionLog(String.format(
        "Files uploaded successfully: %s. Uploaded by: %s.", fileNames, uploadOutcome.getUploadedBy(), LogLevel.INFO));
    closeLogStream(ambiance);
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder().name("output").outcome(uploadOutcome).build())
        .build();
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  private void openLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.openStream(COMMAND_UNIT);
  }

  private void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeStream(COMMAND_UNIT);
  }

  private void validateNumberOfUploadSteps(String planExecutionId) {
    // Retrieve the number of instances already stored for a given plan execution ID,
    // in order to enforce a limit on the maximum number of upload steps in the pipeline.
    // uses planExecutionId_idx
    Criteria criteria = new Criteria();
    criteria.and(RuntimeFileInputDataKeys.planExecutionId).is(planExecutionId);
    Long count = runtimeFileInputDataRepository.count(criteria);
    if (count != null && count >= MAXIMUM_UPLOAD_STEPS_IN_PIPELINE_RESTRICTION) {
      throw new LimitExceededException(String.format(
          "The request could not be processed because the number of Upload steps in the pipeline exceeds the maximum allowed limit of {%s}. Please reduce the number of Upload steps and try again.",
          MAXIMUM_UPLOAD_STEPS_IN_PIPELINE_RESTRICTION));
    }
  }
}

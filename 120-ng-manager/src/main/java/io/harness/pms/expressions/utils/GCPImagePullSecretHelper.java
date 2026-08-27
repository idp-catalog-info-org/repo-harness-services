/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.utils;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.common.NGTaskType;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.GcpConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.gar.GarDelegateRequest;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.delegate.task.artifacts.source.ArtifactSourceDelegateRequest;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Slf4j
@Singleton
public class GCPImagePullSecretHelper {
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;

  List<EncryptedDataDetail> getEncryptionDetailsGCP(
      @Nonnull GcpConnectorDTO gcpConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (gcpConnectorDTO.getCredential() != null && gcpConnectorDTO.getCredential().getConfig() != null) {
      return secretManagerClientService.getEncryptionDetails(ngAccess, gcpConnectorDTO.getCredential().getConfig());
    }
    return new ArrayList<>();
  }

  ArtifactTaskExecutionResponse executeSyncTaskGAR(
      GarDelegateRequest garRequest, ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData =
        getResponseDataArtifacts(ngAccess, garRequest, taskType, NGTaskType.GOOGLE_ARTIFACT_REGISTRY_TASK_NG.name());
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  ArtifactTaskExecutionResponse executeSyncTaskGCR(
      GcrArtifactDelegateRequest gcrRequest, ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData =
        getResponseDataArtifacts(ngAccess, gcrRequest, taskType, NGTaskType.GCR_ARTIFACT_TASK_NG.name());
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseDataArtifacts(BaseNGAccess ngAccess,
      ArtifactSourceDelegateRequest artifactSourceDelegateRequest, ArtifactTaskType artifactTaskType,
      String ngTaskType) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(artifactSourceDelegateRequest)
                                                        .build();

    Map<String, String> taskSetupAbstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(ngTaskType)
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(30))
            .taskSetupAbstractions(taskSetupAbstractions)
            .taskSelectors(getDelegateSelectors(artifactSourceDelegateRequest))
            .build();
    try {
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  private Set<String> getDelegateSelectors(ArtifactSourceDelegateRequest artifactSourceDelegateRequest) {
    if (artifactSourceDelegateRequest instanceof GarDelegateRequest) {
      return ((GarDelegateRequest) artifactSourceDelegateRequest).getGcpConnectorDTO().getDelegateSelectors();
    } else if (artifactSourceDelegateRequest instanceof GcrArtifactDelegateRequest) {
      return ((GcrArtifactDelegateRequest) artifactSourceDelegateRequest).getGcpConnectorDTO().getDelegateSelectors();
    }
    return Collections.emptySet();
  }

  private ArtifactTaskExecutionResponse getTaskExecutionResponse(
      DelegateResponseData responseData, String ifFailedMessage) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ArtifactServerException(ifFailedMessage + " - " + errorNotifyResponseData.getErrorMessage());
    }
    ArtifactTaskResponse artifactTaskResponse = (ArtifactTaskResponse) responseData;
    if (artifactTaskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactServerException(ifFailedMessage + " - " + artifactTaskResponse.getErrorMessage()
          + " with error code: " + artifactTaskResponse.getErrorCode());
    }
    return artifactTaskResponse.getArtifactTaskExecutionResponse();
  }
}

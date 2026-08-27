/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.k8s.cluster.resources.rancher;

import static io.harness.cdng.artifact.utils.ArtifactUtils.getTaskSetupAbstractions;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.utils.ApiUtils.addLinksHeader;

import static software.wings.beans.TaskType.RANCHER_LIST_CLUSTERS_TASK_NG;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.RancherConnectorDTO;
import io.harness.delegate.beans.connector.rancher.RancherListClustersTaskResponse;
import io.harness.delegate.beans.connector.rancher.RancherTaskParams;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.service.DelegateTaskResponseV1;
import io.harness.delegate.service.DelegateTaskServiceWrapper;
import io.harness.delegate.task.unified.UnifiedActionDetails;
import io.harness.delegate.utils.Outputs;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rancher.RancherClusterItem;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class RancherClusterHelper {
  @Inject @Named(DEFAULT_CONNECTOR_SERVICE) private ConnectorService connectorService;
  @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private DelegateTaskServiceWrapper delegateTaskServiceWrapper;
  @Inject private ExceptionManager exceptionManager;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private ScopeInfoService scopeInfoService;
  @Inject private Outputs outputs;

  private static final String CONNECTOR_NOT_FOUND_MESSAGE = "Connector not found for identifier [%s], scope: [%s]";
  private static final String LIST_CLUSTERS_ERROR_MESSAGE = "Failed to list rancher clusters. Error: ";
  private static final String OUTPUT_CLUSTERS = "CLUSTERS";

  DelegateResponseData executeListClustersDelegateTask(
      RancherTaskParams taskParams, BaseNGAccess ngAccess, String connectorRef) {
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(RANCHER_LIST_CLUSTERS_TASK_NG.name())
            .taskParameters(taskParams)
            .executionTimeout(Duration.ofMinutes(5))
            .taskSetupAbstractions(getTaskSetupAbstractions(ngAccess))
            .taskSelectors(taskParams.getRancherConnectorDTO().getDelegateSelectors())
            .build();
    try {
      UnifiedActionDetails actionDetails = taskParams.getUnifiedActionDetails();
      DelegateResponseData responseData =
          delegateTaskServiceWrapper.executeSyncTask(ngAccess, connectorRef, actionDetails.getTaskName(),
              actionDetails.getBinaryName(), Map.of("PLUGIN_ACTION", actionDetails.getName()), delegateTaskRequest);
      return getTaskExecutionResponse(responseData);

    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  private DelegateResponseData getTaskExecutionResponse(DelegateResponseData responseData) {
    if (responseData instanceof DelegateTaskResponseV1 responseV1) {
      return getRancherListClustersTaskResponse(responseV1);
    } else {
      return responseData;
    }
  }

  @VisibleForTesting
  RancherListClustersTaskResponse getRancherListClustersTaskResponse(DelegateTaskResponseV1 responseV1) {
    if (responseV1.getCommandExecutionStatus() != SUCCESS) {
      return RancherListClustersTaskResponse.builder()
          .commandExecutionStatus(responseV1.getCommandExecutionStatus())
          .errorMessage(responseV1.getErrorMessage())
          .build();
    }
    if (isEmpty(responseV1.getOutVars())) {
      return RancherListClustersTaskResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage("Task response output variables are empty")
          .build();
    }

    List<RancherClusterItem> clusterItems =
        Optional
            .ofNullable(outputs.deserialize(
                responseV1.getOutVars().get(OUTPUT_CLUSTERS), new TypeReference<List<RancherClusterItem>>() {}))
            .orElse(List.of());

    return RancherListClustersTaskResponse.builder().clusterItems(clusterItems).commandExecutionStatus(SUCCESS).build();
  }

  List<EncryptedDataDetail> getEncryptionDetails(RancherConnectorDTO rancherConnectorDTO, BaseNGAccess baseNGAccess) {
    List<DecryptableEntity> decryptableEntities = rancherConnectorDTO.getDecryptableEntities();
    if (isEmpty(decryptableEntities)) {
      return Collections.emptyList();
    }
    return secretManagerClientService.getEncryptionDetails(baseNGAccess, decryptableEntities.get(0));
  }

  RancherConnectorDTO getRancherConnector(IdentifierRef connectorRef) {
    // connectorRef has updated values for org and project coming via API Impls
    ScopeInfo scopeInfo = pmsFeatureFlagHelper.isEnabled(connectorRef.getAccountIdentifier(),
                              FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    Optional<ConnectorResponseDTO> connectorDTOOptional = scopeInfo != null
        ? connectorService.get(scopeInfo, connectorRef.getIdentifier())
        : connectorService.get(connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(),
              connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());

    if (connectorDTOOptional.isEmpty() || !isRancherConnector(connectorDTOOptional.get())) {
      String errorMessage = format(CONNECTOR_NOT_FOUND_MESSAGE, connectorRef.getIdentifier(), connectorRef.getScope());
      throw new InvalidRequestException(errorMessage);
    }
    ConnectorInfoDTO connectors = connectorDTOOptional.get().getConnector();
    return (RancherConnectorDTO) connectors.getConnectorConfig();
  }

  static void throwExceptionIfTaskFailed(DelegateResponseData delegateTaskResponse) {
    if (delegateTaskResponse instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) delegateTaskResponse;
      throw new InvalidRequestException(LIST_CLUSTERS_ERROR_MESSAGE + errorNotifyResponseData.getErrorMessage());
    }
    RancherListClustersTaskResponse taskResponse = (RancherListClustersTaskResponse) delegateTaskResponse;
    if (taskResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      throw new InvalidRequestException(LIST_CLUSTERS_ERROR_MESSAGE + taskResponse.getErrorMessage());
    }
  }

  Response generateResponseWithHeaders(RancherClusterListResponseDTO responseDTO, Integer page, Integer limit) {
    ResponseBuilder responseBuilder = Response.ok().entity(responseDTO);

    int size = 0;
    if (isNotEmpty(responseDTO.getClusterItems())) {
      size = responseDTO.getClusterItems().size();
    } else if (isNotEmpty(responseDTO.getClusters())) {
      size = responseDTO.getClusters().size();
    }
    addLinksHeader(responseBuilder, size, page, limit);

    return responseBuilder.build();
  }

  Map<String, String> createPageRequestParamsMap(Integer page, Integer limit, String sort, String order) {
    Map<String, String> pageRequestParamsMap = new HashMap<>();
    if (page != null) {
      pageRequestParamsMap.put("page", String.valueOf(page));
    }
    if (limit != null) {
      pageRequestParamsMap.put("limit", String.valueOf(limit));
    }
    if (isNotEmpty(order)) {
      pageRequestParamsMap.put("order", order);
    }
    if (isNotEmpty(sort)) {
      pageRequestParamsMap.put("sort", sort);
    }
    return pageRequestParamsMap;
  }

  private static boolean isRancherConnector(ConnectorResponseDTO connectorResponse) {
    return ConnectorType.RANCHER == connectorResponse.getConnector().getConnectorType();
  }
}

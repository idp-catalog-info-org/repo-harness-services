/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.DelegateTaskRequest.DelegateTaskRequestBuilder;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.PrometheusConnectorDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.task.iro.IRODataCollectionRequest;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.remote.client.NGRestUtils;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.spec.server.ng.v1.model.PrometheusQueryRequest;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.TaskType;
import software.wings.delegatetasks.cv.IRODataCollectionTaskResult;

import clients.iromanager.beans.IRODataCollectionTaskItem;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CE)
@Slf4j
public class IRODataCollectionTaskServiceImpl implements IRODataCollectionTaskService {
  private final SecretNGManagerClient secretNGManagerClient;
  private final ConnectorService connectorService;
  private final DelegateGrpcClientWrapper delegateService;
  private final WaitNotifyEngine waitNotifyEngine;
  private final DecryptionHelper decryptionHelper;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final ScopeInfoService scopeInfoService;

  @Inject
  public IRODataCollectionTaskServiceImpl(@Named("PRIVILEGED") SecretNGManagerClient secretNGManagerClient,
      @Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService, DelegateGrpcClientWrapper delegateService,
      WaitNotifyEngine waitNotifyEngine, DecryptionHelper decryptionHelper, PmsFeatureFlagHelper pmsFeatureFlagHelper,
      ScopeInfoService scopeInfoService) {
    this.secretNGManagerClient = secretNGManagerClient;
    this.connectorService = connectorService;
    this.delegateService = delegateService;
    this.waitNotifyEngine = waitNotifyEngine;
    this.decryptionHelper = decryptionHelper;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
    this.scopeInfoService = scopeInfoService;
  }

  private List<EncryptedDataDetail> getEncryptedDataDetails(
      NGAccess basicNgAccessObject, DecryptableEntity decryptableEntity) {
    return NGRestUtils.getResponse(
        secretNGManagerClient.getEncryptionDetails(basicNgAccessObject.getAccountIdentifier(),
            NGAccessWithEncryptionConsumer.builder()
                .ngAccess(basicNgAccessObject)
                .decryptableEntity(decryptableEntity)
                .build()));
  }

  @Override
  public IRODataCollectionTaskResult getDataCollectionResult(String accountId, String orgIdentifier,
      String projectIdentifier, @Valid PrometheusQueryRequest prometheusQueryRequest) throws IOException {
    NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                       .accountIdentifier(accountId)
                                       .orgIdentifier(orgIdentifier)
                                       .projectIdentifier(projectIdentifier)
                                       .build();
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        prometheusQueryRequest.getConnectorRef(), accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = pmsFeatureFlagHelper.isEnabled(identifierRef.getAccountIdentifier(),
                              FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
              identifierRef.getProjectIdentifier())
        : null;
    ConnectorResponseDTO connectorResponseDTO = scopeInfo != null
        ? connectorService.get(scopeInfo, identifierRef.getIdentifier()).get()
        : connectorService
              .get(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
                  identifierRef.getProjectIdentifier(), identifierRef.getIdentifier())
              .get();
    List<List<EncryptedDataDetail>> encryptedDataDetails = new ArrayList<>();
    List<DecryptableEntity> decryptableEntities =
        connectorResponseDTO.getConnector().getConnectorConfig().getDecryptableEntities();
    if (isNotEmpty(decryptableEntities)) {
      decryptableEntities.forEach(decryptableEntity
          -> encryptedDataDetails.add(getEncryptedDataDetails(basicNGAccessObject, decryptableEntity)));
    }
    IRODataCollectionRequest iroDataCollectionRequest =
        IROUtils.convertToIRODataCollectionRequest(prometheusQueryRequest)
            .connectorInfoDTO(connectorResponseDTO.getConnector())
            .accountId(accountId)
            .encryptedDataDetails(encryptedDataDetails)
            .build();

    final Map<String, String> ngTaskSetupAbstractions =
        getNGTaskSetupAbstractionsWithOwner(accountId, orgIdentifier, projectIdentifier);
    DelegateTaskRequestBuilder delegateTaskRequestBuilder;
    if (connectorResponseDTO.getConnector().getConnectorType().equals(ConnectorType.PROMETHEUS)) {
      PrometheusConnectorDTO prometheusConnectorDTO =
          (PrometheusConnectorDTO) iroDataCollectionRequest.getConnectorConfigDTO();
      delegateTaskRequestBuilder = DelegateTaskRequest.builder()
                                       .accountId(accountId)
                                       .taskParameters(iroDataCollectionRequest)
                                       .taskType(TaskType.IRO_DATA_COLLECTION_TASK.name())
                                       .executionTimeout(Duration.ofMinutes(2))
                                       .taskSetupAbstractions(ngTaskSetupAbstractions)
                                       .taskSelectors(prometheusConnectorDTO.getDelegateSelectors());
    } else {
      GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) iroDataCollectionRequest.getConnectorConfigDTO();
      delegateTaskRequestBuilder = DelegateTaskRequest.builder()
                                       .accountId(accountId)
                                       .taskParameters(iroDataCollectionRequest)
                                       .taskType(TaskType.IRO_DATA_COLLECTION_TASK.name())
                                       .executionTimeout(Duration.ofMinutes(2))
                                       .taskSetupAbstractions(ngTaskSetupAbstractions)
                                       .taskSelectors(gcpConnectorDTO.getDelegateSelectors());
    }
    return (IRODataCollectionTaskResult) delegateService.executeSyncTaskV2(delegateTaskRequestBuilder.build());
  }

  @NonNull
  private static LinkedHashMap<String, String> buildLogAbstractions(
      IRODataCollectionRequest iroDataCollectionRequest, String uid) {
    LinkedHashMap<String, String> logAbstractions = new LinkedHashMap<>();
    logAbstractions.put("accountId", iroDataCollectionRequest.getAccountId());
    logAbstractions.put("uid", uid);
    return logAbstractions;
  }

  @Override
  public String submitAsyncDataCollectionTask(String accountId, String orgIdentifier, String projectIdentifier,
      IRODataCollectionTaskItem iroDataCollectionTaskItem) {
    NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                       .accountIdentifier(accountId)
                                       .orgIdentifier(orgIdentifier)
                                       .projectIdentifier(projectIdentifier)
                                       .build();
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        iroDataCollectionTaskItem.getConnectorIdentifier(), accountId, orgIdentifier, projectIdentifier);
    // Assumes IRODataCollectionTaskItem always has updated values
    ScopeInfo scopeInfo = pmsFeatureFlagHelper.isEnabled(identifierRef.getAccountIdentifier(),
                              FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
              identifierRef.getProjectIdentifier())
        : null;
    ConnectorResponseDTO connectorResponseDTO = scopeInfo != null
        ? connectorService.get(scopeInfo, identifierRef.getIdentifier()).get()
        : connectorService
              .get(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
                  identifierRef.getProjectIdentifier(), identifierRef.getIdentifier())
              .get();
    List<List<EncryptedDataDetail>> encryptedDataDetails = new ArrayList<>();
    List<DecryptableEntity> decryptableEntities =
        connectorResponseDTO.getConnector().getConnectorConfig().getDecryptableEntities();
    if (isNotEmpty(decryptableEntities)) {
      decryptableEntities.forEach(decryptableEntity
          -> encryptedDataDetails.add(getEncryptedDataDetails(basicNGAccessObject, decryptableEntity)));
    }

    final Map<String, String> ngTaskSetupAbstractions =
        getNGTaskSetupAbstractionsWithOwner(accountId, orgIdentifier, projectIdentifier);

    IRODataCollectionRequest iroDataCollectionRequest =
        IROUtils.convertToIRODataCollectionRequest(iroDataCollectionTaskItem)
            .connectorInfoDTO(connectorResponseDTO.getConnector())
            .accountId(accountId)
            .encryptedDataDetails(encryptedDataDetails)
            .build();
    ConnectorConfigDTO connectorConfigDTO = iroDataCollectionRequest.getConnectorConfigDTO();
    DelegateTaskRequestBuilder delegateTaskRequestBuilder;
    if (connectorConfigDTO instanceof GcpConnectorDTO) {
      GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) iroDataCollectionRequest.getConnectorConfigDTO();
      delegateTaskRequestBuilder = DelegateTaskRequest.builder()
                                       .accountId(accountId)
                                       .taskParameters(iroDataCollectionRequest)
                                       .taskType(TaskType.IRO_DATA_COLLECTION_TASK.name())
                                       .executionTimeout(Duration.ofMinutes(10))
                                       .taskSetupAbstractions(ngTaskSetupAbstractions)
                                       .taskSelectors(gcpConnectorDTO.getDelegateSelectors());
    } else {
      PrometheusConnectorDTO prometheusConnectorDTO =
          (PrometheusConnectorDTO) iroDataCollectionRequest.getConnectorConfigDTO();
      delegateTaskRequestBuilder = DelegateTaskRequest.builder()
                                       .accountId(accountId)
                                       .taskParameters(iroDataCollectionRequest)
                                       .taskType(TaskType.IRO_DATA_COLLECTION_TASK.name())
                                       .executionTimeout(Duration.ofMinutes(10))
                                       .taskSetupAbstractions(ngTaskSetupAbstractions)
                                       .taskSelectors(prometheusConnectorDTO.getDelegateSelectors());
    }
    String taskId = delegateService.submitAsyncTaskV2(delegateTaskRequestBuilder.build(), Duration.ZERO);
    waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION, new IROManagerNotifyCallback(taskId), taskId);
    return taskId;
  }
}
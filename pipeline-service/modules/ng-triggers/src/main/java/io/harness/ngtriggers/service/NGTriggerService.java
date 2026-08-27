/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.ng.core.dto.PollingTriggerStatusUpdateDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.BulkTriggersRequestDTO;
import io.harness.ngtriggers.beans.dto.BulkTriggersResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerExecutorDTO;
import io.harness.ngtriggers.beans.dto.TriggerYamlDiffDTO;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.source.GitMoveOperationType;
import io.harness.ngtriggers.beans.source.TriggerUpdateCount;
import io.harness.ngtriggers.validations.result.ValidationResult;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
public interface NGTriggerService {
  ResponseDTO<NGTriggerResponseDTO> createTriggerWithValidation(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String targetIdentifier, String yaml, TriggerExecutorDTO executorInfo,
      boolean ignoreError, boolean withServiceV2, ScopeInfo scopeInfo);

  ResponseDTO<NGTriggerResponseDTO> updateTriggerWithValidation(String ifMatch, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String targetIdentifier, String triggerIdentifier, String yaml,
      TriggerExecutorDTO executorInfo, boolean ignoreError, ScopeInfo scopeInfo);

  NGTriggerEntity create(NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  Optional<NGTriggerEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String targetIdentifier, String identifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  NGTriggerEntity update(NGTriggerEntity ngTriggerEntity, NGTriggerEntity oldNgTriggerEntity, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled);

  BulkTriggersResponseDTO toggleTriggers(boolean enable, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String type, ScopeInfo scopeInfo);

  boolean updateTriggerStatus(
      NGTriggerEntity ngTriggerEntity, boolean status, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  boolean updateTriggerPollingStatus(String accountId, PollingTriggerStatusUpdateDTO statusUpdate);

  Page<NGTriggerEntity> list(Criteria criteria, Pageable pageable);

  List<NGTriggerEntity> listEnabledTriggersForCurrentProject(
      String accountId, String orgIdentifier, String projectIdentifier);

  @Deprecated List<NGTriggerEntity> listEnabledTriggersForAccount(String accountId);

  List<NGTriggerEntity> findTriggersForCustomWehbook(TriggerWebhookEvent triggerWebhookEvent, boolean enabled);

  Optional<NGTriggerEntity> findTriggersForCustomWebhookViaCustomWebhookToken(String webhookToken);

  List<NGTriggerEntity> findTriggersForWehbookBySourceRepoType(
      TriggerWebhookEvent triggerWebhookEvent, boolean enabled);
  List<NGTriggerEntity> findBuildTriggersByAccountIdAndSignature(String accountId, List<String> signatures);
  List<NGTriggerEntity> findTriggersByCriteria(Criteria criteria);
  boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String targetIdentifier,
      String identifier, Long version, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  TriggerWebhookEvent addEventToQueue(TriggerWebhookEvent webhookEventQueueRecord);
  TriggerCustomWebhookEvent enqueueTriggerCustomWebhookEvent(TriggerCustomWebhookEvent triggerCustomWebhookEvent);
  TriggerWebhookEvent updateTriggerWebhookEvent(TriggerWebhookEvent webhookEventQueueRecord);
  TriggerCustomWebhookEvent updateTriggerCustomWebhookEvent(
      String customWebhookEventId, Integer attemptCount, String status, List<String> allowedStatus);
  void deleteTriggerWebhookEvent(TriggerWebhookEvent webhookEventQueueRecord);
  void deleteTriggerCustomWebhookEvent(TriggerCustomWebhookEvent webhookEventQueueRecord);
  List<ConnectorResponseDTO> fetchConnectorsByFQN(String accountId, List<String> fqns);

  void validateTriggerConfigForCreate(
      TriggerDetails triggerDetails, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);
  void validateTriggerConfig(TriggerDetails triggerDetails, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);
  boolean deleteAllForPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String parentUniqueId);

  WebhookEventProcessingDetails fetchTriggerEventHistory(String accountId, String eventId);
  NGTriggerEntity updateTriggerWithValidationStatus(NGTriggerEntity ngTriggerEntity, ValidationResult validationResult,
      boolean whileExecution, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  TriggerDetails fetchTriggerEntityV1(String accountId, String orgId, String projectId, String pipelineId,
      String triggerId, NGTriggerConfigV2 config, NGTriggerEntity entity, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled);

  TriggerDetails fetchTriggerEntity(String accountId, String orgId, String projectId, String pipelineId,
      String triggerId, String newYaml, boolean withServiceV2, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);
  Object fetchExecutionSummaryV2(String planExecutionId, String accountId, String orgId, String projectId);

  List<TriggerCatalogItem> getTriggerCatalog(String accountIdentifier);

  void validatePipelineRef(TriggerDetails triggerDetails, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);

  void checkAuthorization(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, List<HeaderConfig> headerConfigs);

  void checkAuthorizationForWebhookDetailsResources(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String endpoint);

  TriggerYamlDiffDTO getTriggerYamlDiff(
      TriggerDetails triggerDetails, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled);
  void resetPollingTriggers(Criteria criteria, boolean isParentIdQueryingEnabled, String accountIdentifier);

  TriggerUpdateCount updateBranchName(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, GitMoveOperationType operationType, String pipelineBranchName, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled);

  BulkTriggersResponseDTO toggleTriggersInBulk(String accountIdentifier, BulkTriggersRequestDTO bulkTriggersRequestDTO);

  String fetchExecutionURL(String accountId, String orgId, String projectId, String pipelineId, String planExecutionId,
      List<String> modules);

  long count(String accountIdentifier);

  List<NGTriggerEntity> findTriggersForHarnessArtifactRegistryByAccountIdAndRegistry(
      String accountId, String registryName, String action);
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.setupusage;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.ENVIRONMENT;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.ENVIRONMENT_GROUP;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.FILES;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.INFRASTRUCTURE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.PIPELINES;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.SECRETS;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.SERVICE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.TEMPLATE;

import io.harness.EntityType;
import io.harness.PipelineSetupUsageUtils;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.bulkReconciliation.SyncReferenceReconcileEventForPipelineRequest;
import io.harness.beans.bulkReconciliation.SyncReferenceReconcileEventForPipelineRequest.TemplateReferenceItem;
import io.harness.data.structure.EmptyPredicate;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityGitMetadata;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO.EntityReferredByPipelineDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO.PipelineDetailType;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.events.delete.PipelineDeleteEvent;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.observer.PipelineActionObserver;
import io.harness.pms.pipeline.references.filter.FilterCreationGitMetadata;
import io.harness.pms.pipeline.references.filter.FilterCreationParams;
import io.harness.pms.yaml.YamlUtils;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineSetupUsageHelper implements PipelineActionObserver {
  @Inject @Named(EventsFrameworkConstants.SETUP_USAGE) private Producer eventProducer;
  @Inject private EntitySetupUsageClient entitySetupUsageClient;
  @Inject private GitSyncSdkService gitSyncSdkService;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private MetricService metricService;
  @Inject
  @Named("pipelineSetupUsageCreationExecutorService")
  private ExecutorService pipelineSetupUsageCreationExecutorService;

  @VisibleForTesting
  static final String REFERENCE_RECONCILE_SYNC_TIME_METRIC_NAME = "pipeline_reference_reconcile_sync_time";
  @VisibleForTesting
  static final String REFERENCE_RECONCILE_SYNC_COUNT_METRIC_NAME = "pipeline_reference_reconcile_sync_count";
  private static final String METRIC_STATUS_SUCCESS = "SUCCESS";
  private static final String METRIC_STATUS_FAILURE = "FAILURE";
  private static final String METRIC_STATUS_REJECTED = "REJECTED";

  private static final int PAGE = 0;
  private static final int SIZE = 100;

  public final Set<EntityTypeProtoEnum> entityTypesSupportedByNGCore = Sets.newHashSet(
      SECRETS, CONNECTORS, SERVICE, ENVIRONMENT, ENVIRONMENT_GROUP, TEMPLATE, FILES, PIPELINES, INFRASTRUCTURE);

  /**
   * Performs the following:
   * - queries the entitySetupUsage framework to get all entities referenced in the given pipeline yaml. (static, inputs
   * and runtimeExpression)
   * - extracts the value of a runtime input using fqn from the given pipeline yaml
   * - does not resolve runtimeExpressions as they can only be resolved during execution.
   * - can filter out resources of given entityType too. If entityType is null, it gives all resources.
   *
   * @param accountIdentifier                   - accountIdentifier of the pipeline
   * @param orgIdentifier                       -  orgIdentifier of the pipeline
   * @param projectIdentifier                   - projectIdentifier of the pipeline
   * @param pipelineId                          - pipelineIdentifier
   * @param pipelineYamlWithUnresolvedTemplates - merged pipeline yaml with no templates resolved, because the
   *                                            references are saved based on unresolved yaml.
   * @param entityType                          - returns response of given entity type referred in the pipeline.
   * @param scopeInfo
   * @param isParentUniqueIdQueryingEnabled
   */
  public List<EntityDetail> getReferencesOfPipeline(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineId, String pipelineYamlWithUnresolvedTemplates, EntityType entityType,
      ScopeInfo scopeInfo, boolean isParentUniqueIdQueryingEnabled) {
    String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(scopeInfo, pipelineId);
    List<EntitySetupUsageDTO> allReferredUsages =
        NGRestUtils.getResponse(entitySetupUsageClient.listAllReferredUsagesV2(PAGE, SIZE, accountIdentifier,
                                    fullyQualifiedIdentifier, EntityType.PIPELINES, entityType, null, false),
            "Could not extract setup usage of pipeline with id " + pipelineId + " after {} attempts.");

    return PipelineSetupUsageUtils.extractInputReferredEntityFromYaml(accountIdentifier, orgIdentifier,
        projectIdentifier, pipelineYamlWithUnresolvedTemplates, allReferredUsages, scopeInfo,
        isParentUniqueIdQueryingEnabled);
  }

  public List<EntityDetail> getReferencesOfPipeline(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineId, JsonNode pipelineJsonNodeWithUnresolvedTemplates,
      EntityType entityType, ScopeInfo scopeInfo, boolean isParentUniqueIdQueryingEnabled, String harnessYamlVersion) {
    String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(scopeInfo, pipelineId);
    List<EntitySetupUsageDTO> allReferredUsages =
        NGRestUtils.getResponse(entitySetupUsageClient.listAllReferredUsagesV2(PAGE, SIZE, accountIdentifier,
                                    fullyQualifiedIdentifier, EntityType.PIPELINES, entityType, null, false),
            "Could not extract setup usage of pipeline with id " + pipelineId + " after {} attempts.");
    return PipelineSetupUsageUtils.extractInputReferredEntityFromYaml(accountIdentifier, orgIdentifier,
        projectIdentifier, pipelineJsonNodeWithUnresolvedTemplates, allReferredUsages, scopeInfo,
        isParentUniqueIdQueryingEnabled, harnessYamlVersion);
  }

  public void deleteExistingSetupUsages(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, ScopeInfo scopeInfo, boolean isParentUniqueIdQueryingEnabled) {
    IdentifierRefProtoDTO pipelineReference =
        IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(scopeInfo, identifier);
    EntityDetailProtoDTO pipelineDetails = EntityDetailProtoDTO.newBuilder()
                                               .setIdentifierRef(pipelineReference)
                                               .setType(EntityTypeProtoEnum.PIPELINES)
                                               .build();
    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountIdentifier)
                                                         .setReferredByEntity(pipelineDetails)
                                                         .setDeleteOldReferredByRecords(true)
                                                         .build();
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier, EventsFrameworkMetadataConstants.ACTION,
                  EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.error("Error deleting the setup usages for the connector with the identifier {} in project {} in org {}",
          identifier, projectIdentifier, orgIdentifier);
    }
  }

  public void publishSetupUsageEvent(
      FilterCreationParams filterCreationParams, List<EntityDetailProtoDTO> referredEntities) {
    PipelineEntity pipelineEntity = filterCreationParams.getPipelineEntity();
    FilterCreationGitMetadata gitMetadata = filterCreationParams.getFilterCreationGitMetadata();
    if (!shouldPublishSetupUsage(pipelineEntity, gitMetadata)) {
      return;
    }
    log.info(String.format("Publishing setup usages for pipeline [%s] in repo [%s] in default branch",
        pipelineEntity.getIdentifier(), pipelineEntity.getRepo()));
    try {
      if (EmptyPredicate.isEmpty(referredEntities)) {
        deleteSetupUsagesForGivenPipeline(
            pipelineEntity, new ArrayList<>(entityTypesSupportedByNGCore), filterCreationParams.getScopeInfo());
        syncReferenceReconcileEventForPipeline(
            pipelineEntity, filterCreationParams.getScopeInfo(), Collections.emptyList());
        return;
      }
      EntityDetailProtoDTO pipelineDetails =
          populateEntityDetailProtoDTO(pipelineEntity, gitMetadata, filterCreationParams.getScopeInfo());
      Map<String, List<EntityDetailProtoDTO>> referredEntityTypeToReferredEntities = new HashMap<>();
      for (EntityDetailProtoDTO entityDetailProtoDTO : referredEntities) {
        List<EntityDetailProtoDTO> entityDetailProtoDTOS =
            referredEntityTypeToReferredEntities.getOrDefault(entityDetailProtoDTO.getType().name(), new ArrayList<>());
        entityDetailProtoDTOS.add(entityDetailProtoDTO);
        referredEntityTypeToReferredEntities.put(entityDetailProtoDTO.getType().name(), entityDetailProtoDTOS);
      }

      for (Map.Entry<String, List<EntityDetailProtoDTO>> entry : referredEntityTypeToReferredEntities.entrySet()) {
        List<EntityDetailProtoDTO> entityDetailProtoDTOs = entry.getValue();
        List<EntityDetailWithSetupUsageDetailProtoDTO> entityDetailWithSetupUsageDetailProtoDTOS =
            convertToReferredEntityWithSetupUsageDetail(entityDetailProtoDTOs,
                Objects.requireNonNull(SetupUsageDetailType.getTypeFromEntityTypeProtoEnumName(entry.getKey())).name(),
                pipelineEntity.getIdentifier());
        EntitySetupUsageCreateV2DTO entityReferenceDTO =
            EntitySetupUsageCreateV2DTO.newBuilder()
                .setAccountIdentifier(pipelineEntity.getAccountId())
                .setReferredByEntity(pipelineDetails)
                .addAllReferredEntityWithSetupUsageDetail(entityDetailWithSetupUsageDetailProtoDTOS)
                .setDeleteOldReferredByRecords(true)
                .build();
        eventProducer.send(
            Message.newBuilder()
                .putAllMetadata(ImmutableMap.of("accountId", pipelineEntity.getAccountId(),
                    EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, entry.getKey(),
                    EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                .setData(entityReferenceDTO.toByteString())
                .build());
      }

      // This is being added to handle the case for entities which were earlier present but have been removed in updated
      // pipeline.Example: envGroup was initially used in pipeline but later environment is being used
      for (EntityTypeProtoEnum key : entityTypesSupportedByNGCore) {
        if (!referredEntityTypeToReferredEntities.containsKey(key.name())) {
          EntitySetupUsageCreateV2DTO entityReferenceDTO =
              EntitySetupUsageCreateV2DTO.newBuilder()
                  .setAccountIdentifier(pipelineEntity.getAccountIdentifier())
                  .setReferredByEntity(pipelineDetails)
                  .setDeleteOldReferredByRecords(true)
                  .build();
          try {
            eventProducer.send(
                Message.newBuilder()
                    .putAllMetadata(ImmutableMap.of("accountId", pipelineEntity.getAccountIdentifier(),
                        EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, key.name(),
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                    .setData(entityReferenceDTO.toByteString())
                    .build());
          } catch (Exception ex) {
            log.error(
                "Error deleting the setup usages for the connector with the identifier {} in project {} in org {}: ",
                pipelineEntity.getIdentifier(), pipelineEntity.getAccountIdentifier(),
                pipelineEntity.getOrgIdentifier(), ex);
          }
        }
      }

      List<EntityDetailProtoDTO> templateRefs =
          referredEntityTypeToReferredEntities.getOrDefault(TEMPLATE.name(), Collections.emptyList());
      syncReferenceReconcileEventForPipeline(pipelineEntity, filterCreationParams.getScopeInfo(), templateRefs);
    } catch (Exception ex) {
      log.error("Error publishing the setup usages for the connector with the identifier {} in project {} in org {}: ",
          pipelineEntity.getIdentifier(), pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(), ex);
    }
  }

  private void syncReferenceReconcileEventForPipeline(
      PipelineEntity pipelineEntity, ScopeInfo scopeInfo, List<EntityDetailProtoDTO> templateReferredEntities) {
    String accountId = pipelineEntity.getAccountId();
    if (!pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_BULK_RECONCILIATION_PHASE2)) {
      return;
    }

    List<TemplateReferenceItem> templates = buildTemplateReferenceItems(templateReferredEntities);
    SyncReferenceReconcileEventForPipelineRequest body =
        SyncReferenceReconcileEventForPipelineRequest.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(pipelineEntity.getOrgIdentifier())
            .projectIdentifier(pipelineEntity.getProjectIdentifier())
            .parentUniqueId(scopeInfo == null ? null : scopeInfo.getUniqueId())
            .pipelineIdentifier(pipelineEntity.getIdentifier())
            .currentReferencedTemplates(templates)
            .build();

    try {
      pipelineSetupUsageCreationExecutorService.execute(
          () -> executeSyncReferenceReconcileEventForPipeline(pipelineEntity, accountId, body, templates.size()));
    } catch (RejectedExecutionException rejectedExecutionException) {
      log.warn("Skipping background reference reconcile sync for pipeline {} as task queue is full: {}",
          pipelineEntity.getIdentifier(), rejectedExecutionException.getMessage());
      recordReferenceReconcileSyncMetric(
          accountId, pipelineEntity.getIdentifier(), METRIC_STATUS_REJECTED, 0, templates.size());
    } catch (Exception exception) {
      log.error("Failed to submit background reference reconcile sync task for pipeline {}",
          pipelineEntity.getIdentifier(), exception);
      recordReferenceReconcileSyncMetric(
          accountId, pipelineEntity.getIdentifier(), METRIC_STATUS_FAILURE, 0, templates.size());
    }
  }

  private void executeSyncReferenceReconcileEventForPipeline(PipelineEntity pipelineEntity, String accountId,
      SyncReferenceReconcileEventForPipelineRequest body, int templateCount) {
    long startTs = System.currentTimeMillis();
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
      SourcePrincipalContextBuilder.setSourcePrincipal(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
      NGRestUtils.getResponse(templateResourceClient.syncReferenceReconcileEventForPipeline(accountId, body));
      long durationMs = System.currentTimeMillis() - startTs;
      log.info("Completed background reference reconcile sync for pipelineId: {} in {}ms",
          pipelineEntity.getIdentifier(), durationMs);
      recordReferenceReconcileSyncMetric(
          accountId, pipelineEntity.getIdentifier(), METRIC_STATUS_SUCCESS, durationMs, templateCount);
    } catch (Exception ex) {
      log.warn("Failed to sync ReferenceReconcileEvent for pipeline {} (account {}, org {}, project {}): {}",
          pipelineEntity.getIdentifier(), pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
          pipelineEntity.getProjectIdentifier(), ex.getMessage(), ex);
      recordReferenceReconcileSyncMetric(accountId, pipelineEntity.getIdentifier(), METRIC_STATUS_FAILURE,
          System.currentTimeMillis() - startTs, templateCount);
    }
  }

  private List<TemplateReferenceItem> buildTemplateReferenceItems(List<EntityDetailProtoDTO> templateReferredEntities) {
    List<TemplateReferenceItem> templates = new ArrayList<>();
    if (templateReferredEntities == null) {
      return templates;
    }
    for (EntityDetailProtoDTO entityDetailProtoDTO : templateReferredEntities) {
      if (!EntityTypeProtoEnum.TEMPLATE.equals(entityDetailProtoDTO.getType())) {
        continue;
      }
      TemplateReferenceProtoDTO templateRef = entityDetailProtoDTO.getTemplateRef();
      if (templateRef == null || isEmpty(templateRef.getIdentifier().getValue())) {
        continue;
      }
      templates.add(TemplateReferenceItem.builder()
                        .identifier(templateRef.getIdentifier().getValue())
                        .versionLabel(templateRef.getVersionLabel().getValue())
                        .orgIdentifier(templateRef.getOrgIdentifier().getValue())
                        .projectIdentifier(templateRef.getProjectIdentifier().getValue())
                        .build());
    }
    return templates;
  }

  private void recordReferenceReconcileSyncMetric(
      String accountId, String pipelineIdentifier, String status, long durationMs, int templateCount) {
    Map<String, String> metricContext = ImmutableMap.<String, String>builder()
                                            .put(PmsEventMonitoringConstants.ACCOUNT_ID, accountId)
                                            .put(PmsEventMonitoringConstants.PIPELINE_IDENTIFIER, pipelineIdentifier)
                                            .put(PmsEventMonitoringConstants.STATUS, status)
                                            .put("templateCount", String.valueOf(templateCount))
                                            .build();
    try (PmsMetricContextGuard ignore = new PmsMetricContextGuard(metricContext)) {
      metricService.incCounter(REFERENCE_RECONCILE_SYNC_COUNT_METRIC_NAME);
      if (durationMs > 0) {
        metricService.recordMetric(REFERENCE_RECONCILE_SYNC_TIME_METRIC_NAME, durationMs);
      }
    }
  }

  @VisibleForTesting
  boolean shouldPublishSetupUsage(PipelineEntity pipelineEntity, FilterCreationGitMetadata gitMetadata) {
    if (!StoreType.REMOTE.equals(pipelineEntity.getStoreType())
        && !StoreType.REMOTE.equals(GitAwareContextHelper.getStoreTypeFromGitContext())) {
      return true;
    } else {
      return gitMetadata != null && gitMetadata.isGitDefaultBranch();
    }
  }

  private EntityDetailProtoDTO populateEntityDetailProtoDTO(
      PipelineEntity pipelineEntity, FilterCreationGitMetadata gitMetadata, ScopeInfo scopeInfo) {
    EntityDetailProtoDTO pipelineDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(scopeInfo != null
                    ? IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(scopeInfo, pipelineEntity.getIdentifier())
                    : IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(pipelineEntity.getAccountId(),
                          pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(),
                          pipelineEntity.getIdentifier()))
            .setType(PIPELINES)
            .setName(pipelineEntity.getName())
            .build();
    if (gitMetadata != null) {
      pipelineDetails = EntityDetailProtoDTO.newBuilder(pipelineDetails)
                            .setEntityGitMetadata(EntityGitMetadata.newBuilder()
                                                      .setRepo(gitMetadata.getRepo())
                                                      .setBranch(gitMetadata.getBranch())
                                                      .build())
                            .build();
    }
    return pipelineDetails;
  }

  private List<EntityDetailWithSetupUsageDetailProtoDTO> convertToReferredEntityWithSetupUsageDetail(
      List<EntityDetailProtoDTO> entityDetailProtoDTOs, String setupUsageDetailType, String pipelineIdentifier) {
    List<EntityDetailWithSetupUsageDetailProtoDTO> res = new ArrayList<>();
    for (EntityDetailProtoDTO entityDetailProtoDTO : entityDetailProtoDTOs) {
      String fqn;
      if (EntityTypeProtoEnum.TEMPLATE.equals(entityDetailProtoDTO.getType())) {
        fqn = entityDetailProtoDTO.getTemplateRef().getMetadataMap().get(PreFlightCheckMetadata.FQN);
      } else {
        fqn = entityDetailProtoDTO.getIdentifierRef().getMetadataMap().get(PreFlightCheckMetadata.FQN);
      }

      EntityReferredByPipelineDetailProtoDTO entityReferredByPipelineDetailProtoDTO =
          getSetupDetailProtoDTO(fqn, pipelineIdentifier);
      res.add(EntityDetailWithSetupUsageDetailProtoDTO.newBuilder()
                  .setReferredEntity(entityDetailProtoDTO)
                  .setType(setupUsageDetailType)
                  .setEntityInPipelineDetail(entityReferredByPipelineDetailProtoDTO)
                  .build());
    }
    return res;
  }

  private EntityReferredByPipelineDetailProtoDTO getSetupDetailProtoDTO(String fqn, String pipelineIdentifier) {
    if (isEmpty(fqn)) {
      return EntityReferredByPipelineDetailProtoDTO.newBuilder()
          .setIdentifier(pipelineIdentifier)
          .setType(PipelineDetailType.PIPELINE_IDENTIFIER)
          .build();
    }

    String stageIdentifier = YamlUtils.getStageIdentifierFromFqn(fqn);
    if (stageIdentifier != null) {
      return EntityReferredByPipelineDetailProtoDTO.newBuilder()
          .setIdentifier(stageIdentifier)
          .setType(PipelineDetailType.STAGE_IDENTIFIER)
          .build();
    } else {
      String variableName = YamlUtils.getPipelineVariableNameFromFqn(fqn);
      if (isNotEmpty(variableName)) {
        return EntityReferredByPipelineDetailProtoDTO.newBuilder()
            .setIdentifier(variableName)
            .setType(PipelineDetailType.VARIABLE_NAME)
            .build();
      } else {
        return EntityReferredByPipelineDetailProtoDTO.newBuilder()
            .setIdentifier(pipelineIdentifier)
            .setType(PipelineDetailType.PIPELINE_IDENTIFIER)
            .build();
      }
    }
  }

  public void deleteSetupUsagesForGivenPipeline(
      PipelineEntity pipelineEntity, List<EntityTypeProtoEnum> entityTypeProtoEnumList, ScopeInfo scopeInfo) {
    EntityDetailProtoDTO pipelineDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(scopeInfo != null
                    ? IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(scopeInfo, pipelineEntity.getIdentifier())
                    : IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(pipelineEntity.getAccountId(),
                          pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(),
                          pipelineEntity.getIdentifier()))
            .setType(EntityTypeProtoEnum.PIPELINES)
            .setName(pipelineEntity.getName())
            .build();

    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(pipelineEntity.getAccountId())
                                                         .setReferredByEntity(pipelineDetails)
                                                         .addAllReferredEntities(new ArrayList<>())
                                                         .setDeleteOldReferredByRecords(true)
                                                         .build();
    // Send Events for all refferredEntitiesType so as to delete them
    for (EntityTypeProtoEnum protoEnum : entityTypeProtoEnumList) {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", pipelineEntity.getAccountId(),
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, protoEnum.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    }
  }

  @Override
  public void onDelete(PipelineDeleteEvent pipelineDeleteEvent) {
    try {
      ScopeInfo scopeInfo = pipelineDeleteEvent.getScopeInfo();
      deleteSetupUsagesForGivenPipeline(
          pipelineDeleteEvent.getPipeline(), new ArrayList<>(entityTypesSupportedByNGCore), scopeInfo);
    } catch (EventsFrameworkDownException ex) {
      log.error("Redis Producer shutdown", ex);
    }
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static io.harness.NGConstants.X_API_KEY;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.eventsframework.EventsFrameworkConstants.TRIGGER_CUSTOM_WEBHOOK_EVENT;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ngtriggers.Constants.MANDATE_CUSTOM_WEBHOOK_AUTHORIZATION;
import static io.harness.ngtriggers.Constants.MANDATE_CUSTOM_WEBHOOK_TRUE_VALUE;
import static io.harness.ngtriggers.Constants.MANDATE_PIPELINE_CREATE_EDIT_PERMISSION_TO_CREATE_EDIT_TRIGGERS;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MULTI_REGION_ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;
import static io.harness.ngtriggers.beans.source.YamlFields.PIPELINE_BRANCH_NAME;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.GITHUB;
import static io.harness.pms.yaml.validation.RuntimeInputValuesValidator.validateStaticValues;

import static java.lang.Long.parseLong;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.common.NGExpressionUtils;
import io.harness.common.NGTimeConversionHelper;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.dto.PollingResponseDTO;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.TriggerException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.NGPipelineNotFoundException;
import io.harness.gitsync.beans.StoreType;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.iterator.PersistentNGCronIterable;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.dto.PollingTriggerStatusUpdateDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.BulkTriggerDetailDTO;
import io.harness.ngtriggers.beans.dto.BulkTriggersRequestDTO;
import io.harness.ngtriggers.beans.dto.BulkTriggersResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerExecutorDTO;
import io.harness.ngtriggers.beans.dto.TriggerYamlDiffDTO;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails.WebhookEventProcessingDetailsBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent.TriggerCustomWebhookEventsKeys;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEventPayload;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent.TriggerWebhookEventsKeys;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatusData;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.entity.metadata.status.PollingSubscriptionStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.StatusResult;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.ValidationStatus;
import io.harness.ngtriggers.beans.source.GitMoveOperationType;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.TriggerUpdateCount;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpecWrapper;
import io.harness.ngtriggers.beans.source.artifact.BuildAware;
import io.harness.ngtriggers.beans.source.webhook.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.MultiRegionArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.webhook.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.events.TriggerCreateEvent;
import io.harness.ngtriggers.events.TriggerDeleteEvent;
import io.harness.ngtriggers.events.TriggerUpdateEvent;
import io.harness.ngtriggers.exceptions.yaml.InvalidTriggerYamlException;
import io.harness.ngtriggers.helpers.TriggerCatalogHelper;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.helpers.TriggerSetupUsageHelper;
import io.harness.ngtriggers.instrumentation.TriggerTelemetryHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.service.NGTriggerWebhookRegistrationService;
import io.harness.ngtriggers.utils.MaxMultiArtifactTriggerSourcesProvider;
import io.harness.ngtriggers.utils.TriggerReferenceHelper;
import io.harness.ngtriggers.utils.polling.PollingSubscriptionHelper;
import io.harness.ngtriggers.validations.impl.TriggerValidationHandler;
import io.harness.ngtriggers.validations.result.ValidationResult;
import io.harness.outbox.api.OutboxService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.yaml.YamlConfig;
import io.harness.pms.merger.yaml.YamlSubMapExtractor;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.triggers.TriggerExecutorResolver;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.polling.client.PollingResourceClient;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.service.PollingDocument;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.repositories.spring.TriggerCustomWebhookEventRepository;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.repositories.spring.TriggerWebhookEventRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.validator.NGRegexValidatorConstants;

import com.cronutils.model.Cron;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.CollectionUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerServiceImpl implements NGTriggerService {
  public static final long TRIGGER_CURRENT_YML_VERSION = 3l;
  public static final int WEBHOOK_POLLING_UNSUBSCRIBE = 0;
  public static final int WEBHOOOk_POLLING_MIN_INTERVAL = 2;
  public static final int WEBHOOOk_POLLING_MAX_INTERVAL = 60;
  private static final long MIN_INTERVAL_MINUTES = 5;
  private static final long MAX_DISABLE_BATCH_SIZE = 50;
  private static final long RESET_POLLING_TRIGGERS_BATCH_SIZE = 10;
  private static final Set<String> VALID_EVENT_CONDITION_KEYS = Set.of("build", "version");
  private final AccessControlClient accessControlClient;
  private final NGSettingsClient settingsClient;
  private final NGTriggerRepository ngTriggerRepository;
  private final TriggerWebhookEventRepository webhookEventQueueRepository;
  private final TriggerCustomWebhookEventRepository triggerCustomWebhookEventRepository;
  private final HsqsClientService hsqsClientService;
  private final TriggerEventHistoryRepository triggerEventHistoryRepository;
  private final ConnectorResourceClient connectorResourceClient;
  private final NGTriggerWebhookRegistrationService ngTriggerWebhookRegistrationService;
  private final TriggerValidationHandler triggerValidationHandler;
  private final PollingSubscriptionHelper pollingSubscriptionHelper;
  private final ExecutorService executorService;
  private final KryoSerializer kryoSerializer;
  private final PipelineServiceClient pipelineServiceClient;
  private final TriggerCatalogHelper triggerCatalogHelper;
  private final PollingResourceClient pollingResourceClient;
  private final NGTriggerElementMapper ngTriggerElementMapper;
  private final OutboxService outboxService;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final BuildTriggerHelper validationHelper;
  private final TriggerReferenceHelper triggerReferenceHelper;
  private final TriggerSetupUsageHelper triggerSetupUsageHelper;
  private final MaxMultiArtifactTriggerSourcesProvider maxMultiArtifactTriggerSourcesProvider;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final PipelineSettingsService pipelineSettingsService;
  private final PipelineRetentionService pipelineRetentionService;
  private final PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  private final TriggerExecutorResolver triggerExecutorResolver;
  private final TriggerTelemetryHelper triggerTelemetryHelper;
  private final MetricService metricService;
  private static final String TRIGGER = "trigger";
  private static final String INPUT_YAML = "inputYaml";
  private static final Pattern EXPRESSION_PATTERN = Pattern.compile("<\\+.*?>");
  private static final String TRIGGER_EXECUTOR_VALIDATION_CREATE_UPDATE_COUNT =
      "trigger_executor_validation_create_update_count";

  private static final String DUP_KEY_EXP_FORMAT_STRING = "Trigger [%s] already exists or is soft deleted";

  @Override
  public ResponseDTO<NGTriggerResponseDTO> createTriggerWithValidation(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String targetIdentifier, String yaml, TriggerExecutorDTO executorInfo,
      boolean ignoreError, boolean withServiceV2, ScopeInfo scopeInfo) {
    if (getMandatoryPipelineCreateEditPermissionToCreateEditTriggers(
            accountIdentifier, orgIdentifier, projectIdentifier)) {
      pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountIdentifier, orgIdentifier,
          projectIdentifier, targetIdentifier,
          pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
          PipelineRbacPermissions.PIPELINE_EDIT,
          Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", targetIdentifier), PipelineRbacPermissions.PIPELINE_EXECUTE);

    long currentTriggerCount = count(accountIdentifier);
    if (!pipelineSettingsService.isTriggerCreationWithinLimit(accountIdentifier, currentTriggerCount)) {
      log.warn("[TRIGGER_CREATION_LIMIT_EXCEEDED]: Cannot create a new trigger because the trigger creation limit is "
              + "exceeded for the account {}.",
          accountIdentifier);
      if (pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_HARD_IMPOSE_EXECUTION_LIMITS)) {
        throw new LimitExceededException("You have exceeded the max trigger creation limit allowed on the account. "
            + "Please upgrade your plan or contact Harness Support.");
      }
      try {
        pipelineRetentionService.updateMaxTriggerCreationLimit(accountIdentifier, currentTriggerCount);
      } catch (Exception ex) {
        log.warn(
            String.format(
                "Can be ignored - Error in overriding the trigger creation limit for account id: {%s}, to size: {%d}:",
                accountIdentifier, currentTriggerCount),
            ex);
      }
    }

    NGTriggerEntity createdEntity = null;
    boolean isParentIdQueryingEnabled = true;
    try {
      TriggerDetails triggerDetails = ngTriggerElementMapper.toTriggerDetails(
          scopeInfo != null ? scopeInfo.getAccountIdentifier() : accountIdentifier,
          scopeInfo != null ? scopeInfo.getOrgIdentifier() : orgIdentifier,
          scopeInfo != null ? scopeInfo.getProjectIdentifier() : projectIdentifier,
          scopeInfo != null ? scopeInfo.getUniqueId() : null, yaml, withServiceV2);
      validateTriggerConfigForCreate(triggerDetails, scopeInfo, isParentIdQueryingEnabled);

      boolean executorFeatureEnabled =
          pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_ENFORCE_TRIGGER_EXECUTOR_IDENTITY);
      if (executorFeatureEnabled) {
        boolean enforceExecutor =
            triggerExecutorResolver.isEnforceExecutorEnabled(accountIdentifier, orgIdentifier, projectIdentifier);
        validateExecutorAndRecordMetric(accountIdentifier, executorInfo,
            ()
                -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(triggerDetails.getNgTriggerEntity(),
                    executorInfo, accountIdentifier, orgIdentifier, projectIdentifier, enforceExecutor));
      } else {
        triggerDetails.getNgTriggerEntity().setExecutorInfo(null);
      }

      if (ignoreError) {
        createdEntity = create(triggerDetails.getNgTriggerEntity(), scopeInfo, isParentIdQueryingEnabled);
      } else {
        validatePipelineRef(triggerDetails, scopeInfo, isParentIdQueryingEnabled);
        createdEntity = create(triggerDetails.getNgTriggerEntity(), scopeInfo, isParentIdQueryingEnabled);
      }
      triggerTelemetryHelper.sendTriggersCreateEvent(
          createdEntity, triggerDetails, scopeInfo, isParentIdQueryingEnabled);
      NGTriggerResponseDTO responseDTO =
          ngTriggerElementMapper.toResponseDTO(createdEntity, scopeInfo, isParentIdQueryingEnabled);
      return ResponseDTO.newResponse(createdEntity.getVersion().toString(), responseDTO);
    } catch (InvalidTriggerYamlException e) {
      return ResponseDTO.newResponse(ngTriggerElementMapper.toErrorDTO(e, scopeInfo, isParentIdQueryingEnabled));
    } catch (NGAccessDeniedException e) {
      throw new NGAccessDeniedException(e.getMessage(), WingsException.USER, null);
    } catch (Exception e) {
      throw new InvalidRequestException("Failed while Saving Trigger: " + e.getMessage());
    }
  }

  @Override
  public ResponseDTO<NGTriggerResponseDTO> updateTriggerWithValidation(String ifMatch, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String targetIdentifier, String triggerIdentifier, String yaml,
      TriggerExecutorDTO executorInfo, boolean ignoreError, ScopeInfo scopeInfo) {
    if (getMandatoryPipelineCreateEditPermissionToCreateEditTriggers(
            accountIdentifier, orgIdentifier, projectIdentifier)) {
      pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountIdentifier, orgIdentifier,
          projectIdentifier, targetIdentifier,
          pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
          PipelineRbacPermissions.PIPELINE_EDIT,
          Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", targetIdentifier), PipelineRbacPermissions.PIPELINE_EXECUTE);
    boolean isParentIdQueryingEnabled = true;
    Optional<NGTriggerEntity> ngTriggerEntity = get(accountIdentifier, orgIdentifier, projectIdentifier,
        targetIdentifier, triggerIdentifier, scopeInfo, isParentIdQueryingEnabled);
    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", triggerIdentifier));
    }

    try {
      TriggerDetails triggerDetails =
          fetchTriggerEntity(scopeInfo != null ? scopeInfo.getAccountIdentifier() : accountIdentifier,
              scopeInfo != null ? scopeInfo.getOrgIdentifier() : orgIdentifier,
              scopeInfo != null ? scopeInfo.getProjectIdentifier() : projectIdentifier, targetIdentifier,
              triggerIdentifier, yaml, ngTriggerEntity.get().getWithServiceV2(), scopeInfo, isParentIdQueryingEnabled);

      validateTriggerConfig(triggerDetails, scopeInfo, isParentIdQueryingEnabled);

      boolean executorFeatureEnabled =
          pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_ENFORCE_TRIGGER_EXECUTOR_IDENTITY);
      if (executorFeatureEnabled) {
        boolean enforceExecutor =
            triggerExecutorResolver.isEnforceExecutorEnabled(accountIdentifier, orgIdentifier, projectIdentifier);
        validateExecutorAndRecordMetric(accountIdentifier, executorInfo,
            ()
                -> triggerExecutorResolver.handleExecutorOnUpdate(triggerDetails.getNgTriggerEntity(),
                    ngTriggerEntity.get(), executorInfo, accountIdentifier, orgIdentifier, projectIdentifier,
                    enforceExecutor));
      } else {
        triggerDetails.getNgTriggerEntity().setExecutorInfo(ngTriggerEntity.get().getExecutorInfo());
      }

      triggerDetails.getNgTriggerEntity().setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
      NGTriggerEntity updatedEntity;

      if (ignoreError) {
        updatedEntity =
            update(triggerDetails.getNgTriggerEntity(), ngTriggerEntity.get(), scopeInfo, isParentIdQueryingEnabled);
      } else {
        validatePipelineRef(triggerDetails, scopeInfo, isParentIdQueryingEnabled);
        updatedEntity =
            update(triggerDetails.getNgTriggerEntity(), ngTriggerEntity.get(), scopeInfo, isParentIdQueryingEnabled);
      }
      NGTriggerResponseDTO responseDTO =
          ngTriggerElementMapper.toResponseDTO(updatedEntity, scopeInfo, isParentIdQueryingEnabled);
      return ResponseDTO.newResponse(updatedEntity.getVersion().toString(), responseDTO);
    } catch (InvalidTriggerYamlException e) {
      return ResponseDTO.newResponse(ngTriggerElementMapper.toErrorDTO(e, scopeInfo, isParentIdQueryingEnabled));
    } catch (NGAccessDeniedException e) {
      throw new NGAccessDeniedException(e.getMessage(), WingsException.USER, null);
    } catch (Exception e) {
      throw new InvalidRequestException("Failed while updating Trigger: " + e.getMessage());
    }
  }

  private boolean getMandatoryPipelineCreateEditPermissionToCreateEditTriggers(
      String accountId, String orgIdentifier, String projectIdentifier) {
    String response;
    try {
      response =
          NGRestUtils
              .getResponse(settingsClient.getSetting(MANDATE_PIPELINE_CREATE_EDIT_PERMISSION_TO_CREATE_EDIT_TRIGGERS,
                  accountId, orgIdentifier, projectIdentifier))
              .getValue();
    } catch (Exception ex) {
      log.error("Failed to fetch setting {} for accountId {} orgId {} and projectId {}",
          MANDATE_PIPELINE_CREATE_EDIT_PERMISSION_TO_CREATE_EDIT_TRIGGERS, accountId, orgIdentifier, projectIdentifier,
          ex);
      return true;
    }
    return Objects.equals(response, "true");
  }

  @Override
  public NGTriggerEntity create(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    try {
      // Enabling the FF disables custom webhook authentication
      if (!pmsFeatureFlagService.isEnabled(
              ngTriggerEntity.getAccountId(), FeatureName.SPG_DISABLE_CUSTOM_WEBHOOK_V3_URL)) {
        populateCustomWebhookTokenForCustomWebhookTriggers(ngTriggerEntity);
      }
      validateUniqueIdAndParentUniqueId(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
      NGTriggerEntity savedNgTriggerEntity = ngTriggerRepository.save(ngTriggerEntity);
      performPostUpsertFlow(savedNgTriggerEntity, false, scopeInfo, isParentIdQueryingEnabled);
      outboxService.save(new TriggerCreateEvent(ngTriggerEntity.getAccountId(),
          isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
          isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
          savedNgTriggerEntity));
      try {
        List<EntityDetailProtoDTO> referredEntities =
            triggerReferenceHelper.getReferences(ngTriggerEntity.getAccountId(),
                ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled),
                scopeInfo, isParentIdQueryingEnabled);
        triggerSetupUsageHelper.publishSetupUsageEvent(
            ngTriggerEntity, referredEntities, scopeInfo, isParentIdQueryingEnabled);
      } catch (Exception ex) {
        log.error("Error publishing the setup usages for the trigger with the identifier {}, in project {} in org {} "
                + "with parentUniqueId {}",
            ngTriggerEntity.getAccountId(),
            isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
            isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
            ngTriggerEntity.getParentUniqueId(), ex);
      }
      return savedNgTriggerEntity;
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(
          String.format(DUP_KEY_EXP_FORMAT_STRING, ngTriggerEntity.getIdentifier()), USER_SRE, e);
    }
  }

  private void populateCustomWebhookTokenForCustomWebhookTriggers(NGTriggerEntity ngTriggerEntity) {
    if (ngTriggerEntity.getMetadata().getWebhook() != null
        && ngTriggerEntity.getMetadata().getWebhook().getCustom() != null) {
      ngTriggerEntity.setCustomWebhookToken(generateUuid());
    }
  }

  private void performPostUpsertFlow(
      NGTriggerEntity ngTriggerEntity, boolean isUpdate, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEntity validatedTrigger = validateTrigger(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    registerWebhookAsync(validatedTrigger, scopeInfo, isParentIdQueryingEnabled);
    registerPollingAsync(validatedTrigger, isUpdate, false, scopeInfo, isParentIdQueryingEnabled);
  }

  private void registerPollingAsync(NGTriggerEntity ngTriggerEntity, boolean isUpdate, boolean isResetPollingInterval,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (checkForValidationFailure(ngTriggerEntity)) {
      log.warn(
          String.format("Trigger Validation Failed for Trigger: %s, Skipping Polling Framework subscription request",
              TriggerHelper.getTriggerRef(ngTriggerEntity)));
      return;
    }

    // Polling not required for other trigger types
    if (ngTriggerEntity.getType() != ARTIFACT && ngTriggerEntity.getType() != MULTI_REGION_ARTIFACT
        && ngTriggerEntity.getType() != MANIFEST) {
      return;
    }

    executorService.submit(() -> {
      subscribePolling(ngTriggerEntity, isUpdate, isResetPollingInterval, scopeInfo, isParentIdQueryingEnabled);
    });
  }

  public void subscribePolling(NGTriggerEntity ngTriggerEntity, boolean isUpdate, boolean isResetPollingInterval,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (ngTriggerEntity.getType() == MULTI_REGION_ARTIFACT) {
      executePollingSubscriptionChanges(ngTriggerEntity, isUpdate, scopeInfo, isParentIdQueryingEnabled);
      return;
    }

    List<PollingItem> pollingItems =
        pollingSubscriptionHelper.generatePollingItems(ngTriggerEntity, true, scopeInfo, isParentIdQueryingEnabled);

    try {
      if (isEmpty(pollingItems)) {
        throw new InvalidRequestException("Cannot subscribe polling for empty pollingItems");
      }
      PollingItem pollingItem = pollingItems.get(0);
      byte[] pollingItemBytes = kryoSerializer.asBytes(pollingItem);

      if (!ngTriggerEntity.getEnabled()
          && executePollingUnSubscription(ngTriggerEntity, pollingItemBytes).equals(Boolean.TRUE)) {
        updatePollingRegistrationStatus(
            ngTriggerEntity, null, StatusResult.SUCCESS, null, scopeInfo, isParentIdQueryingEnabled);
      } else if (isWebhookGitPollingEnabled(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled)
          && NGTimeConversionHelper.convertTimeStringToMinutesZeroAllowed(ngTriggerEntity.getPollInterval())
              == WEBHOOK_POLLING_UNSUBSCRIBE
          && executePollingUnSubscription(ngTriggerEntity, pollingItemBytes).equals(Boolean.TRUE)) {
        updatePollingRegistrationStatus(
            ngTriggerEntity, null, StatusResult.SUCCESS, null, scopeInfo, isParentIdQueryingEnabled);
      } else {
        if (isUpdate) {
          executePollingUnSubscription(ngTriggerEntity, pollingItemBytes);
        }

        PollingResponseDTO responseDTO = executePollingSubscription(ngTriggerEntity, pollingItemBytes);
        PollingDocument pollingDocument = (PollingDocument) kryoSerializer.asObject(responseDTO.getPollingResponse());
        try (
            AutoLogContext ignore0 = new NgTriggerAutoLogContext("pollingDocumentId", pollingDocument.getPollingDocId(),
                ngTriggerEntity.getIdentifier(), ngTriggerEntity.getTargetIdentifier(),
                isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
                isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
                ngTriggerEntity.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
          log.info("Polling Subscription successful for Trigger {} with pollingDocumentId {}",
              ngTriggerEntity.getIdentifier(), pollingDocument.getPollingDocId());
          updateTriggerStatus(responseDTO, ngTriggerEntity, pollingDocument, scopeInfo, isParentIdQueryingEnabled);
        }
      }
    } catch (Exception exception) {
      log.error(String.format("Polling Subscription Request failed for Trigger: %s with error",
                    TriggerHelper.getTriggerRef(ngTriggerEntity)),
          exception);
      if (isResetPollingInterval) {
        TriggerStatus triggerStatus = ngTriggerEntity.getTriggerStatus();
        triggerStatus.setPollingSubscriptionStatus(
            PollingSubscriptionStatus.builder()
                .statusResult(StatusResult.FAILED)
                .detailedMessage("Failed to register for polling while resetting polling interval please disable and "
                    + "re enable the trigger to retry polling registration.")
                .build());
        ngTriggerEntity.setTriggerStatus(triggerStatus);
        updatePollingRegistrationStatus(
            ngTriggerEntity, null, StatusResult.FAILED, null, scopeInfo, isParentIdQueryingEnabled);
        throw new InvalidRequestException(exception.getMessage());
      }
      updatePollingRegistrationStatus(
          ngTriggerEntity, null, StatusResult.FAILED, null, scopeInfo, isParentIdQueryingEnabled);
      throw new InvalidRequestException(exception.getMessage());
    }
  }
  private void updateTriggerStatus(PollingResponseDTO responseDTO, NGTriggerEntity ngTriggerEntity,
      PollingDocument pollingDocument, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    StatusResult statusResult = StatusResult.PENDING;
    PollingResponseDTO pollingResponseDTO = null;
    if (responseDTO != null && responseDTO.getIsExistingPollingDoc() && isNotEmpty(responseDTO.getLastPolled())) {
      statusResult = StatusResult.SUCCESS;
      pollingResponseDTO = responseDTO;
    }
    updatePollingRegistrationStatus(ngTriggerEntity, Collections.singletonList(pollingDocument), statusResult,
        pollingResponseDTO, scopeInfo, isParentIdQueryingEnabled);
  }

  public void executePollingSubscriptionChanges(
      NGTriggerEntity ngTriggerEntity, boolean isUpdate, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    // This method is a generalization of `subscribePolling` that we created for handling MultiRegionArtifact Triggers.
    // We intend to replace `subscribePolling` by this method once we are confident it will introduce no issues.
    List<PollingItem> pollingItems =
        pollingSubscriptionHelper.generatePollingItems(ngTriggerEntity, true, scopeInfo, isParentIdQueryingEnabled);

    try {
      if (isEmpty(pollingItems)) {
        throw new InvalidRequestException("Cannot make polling subscription changes for empty pollingItems");
      }
      boolean shouldSubscribe = checkIfShouldSubscribePolling(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
      boolean shouldUnsubscribe =
          checkIfShouldUnsubscribePolling(ngTriggerEntity, isUpdate, scopeInfo, isParentIdQueryingEnabled);
      boolean unsubscribeSuccess = true;

      if (shouldUnsubscribe) {
        List<PollingItem> pollingItemsToUnsubscribe =
            getPollingItemsToUnsubscribe(ngTriggerEntity, pollingItems, scopeInfo, isParentIdQueryingEnabled);
        unsubscribeSuccess = unsubscribePolling(ngTriggerEntity, pollingItemsToUnsubscribe);
      }

      if (shouldSubscribe) {
        List<PollingDocument> pollingDocuments =
            subscribePollingV2(ngTriggerEntity, pollingItems, scopeInfo, isParentIdQueryingEnabled);
        updatePollingRegistrationStatus(
            ngTriggerEntity, pollingDocuments, StatusResult.PENDING, null, scopeInfo, isParentIdQueryingEnabled);
      } else if (unsubscribeSuccess) {
        // no subscription done, check if unsubscription worked.
        updatePollingRegistrationStatus(
            ngTriggerEntity, null, StatusResult.SUCCESS, null, scopeInfo, isParentIdQueryingEnabled);
      } else {
        // unsubscription failed for at least one of the polling items.
        updatePollingRegistrationStatus(
            ngTriggerEntity, null, StatusResult.FAILED, null, scopeInfo, isParentIdQueryingEnabled);
      }

    } catch (Exception exception) {
      log.error(String.format("Polling Subscription Request failed for Trigger: %s with error",
                    TriggerHelper.getTriggerRef(ngTriggerEntity)),
          exception);
      updatePollingRegistrationStatus(
          ngTriggerEntity, null, StatusResult.FAILED, null, scopeInfo, isParentIdQueryingEnabled);
      throw new InvalidRequestException(exception.getMessage());
    }
  }

  private List<PollingDocument> subscribePollingV2(NGTriggerEntity ngTriggerEntity, List<PollingItem> pollingItems,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    List<PollingDocument> pollingDocuments = new ArrayList<>();
    if (isEmpty(pollingItems)) {
      return pollingDocuments;
    }
    for (PollingItem pollingItem : pollingItems) {
      byte[] pollingItemBytes = kryoSerializer.asBytes(pollingItem);
      PollingResponseDTO responseDTO = executePollingSubscription(ngTriggerEntity, pollingItemBytes);
      PollingDocument pollingDocument = (PollingDocument) kryoSerializer.asObject(responseDTO.getPollingResponse());
      pollingDocuments.add(pollingDocument);
      try (AutoLogContext ignore0 = new NgTriggerAutoLogContext("pollingDocumentId", pollingDocument.getPollingDocId(),
               ngTriggerEntity.getIdentifier(), ngTriggerEntity.getTargetIdentifier(),
               isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
               isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
               ngTriggerEntity.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
        log.info("Polling Subscription successful for Trigger {} with pollingDocumentId {}",
            ngTriggerEntity.getIdentifier(), pollingDocument.getPollingDocId());
      }
    }
    return pollingDocuments;
  }

  private boolean unsubscribePolling(NGTriggerEntity ngTriggerEntity, List<PollingItem> pollingItems) {
    if (isEmpty(pollingItems)) {
      // nothing to do here.
      return true;
    }
    boolean unsubscribeSuccess = true;
    for (PollingItem pollingItem : pollingItems) {
      byte[] pollingItemBytes = kryoSerializer.asBytes(pollingItem);
      unsubscribeSuccess =
          unsubscribeSuccess && executePollingUnSubscription(ngTriggerEntity, pollingItemBytes).equals(Boolean.TRUE);
    }
    return unsubscribeSuccess;
  }

  private PollingResponseDTO executePollingSubscription(NGTriggerEntity ngTriggerEntity, byte[] pollingItemBytes) {
    try {
      boolean isUnifiedPipelineFlow = Boolean.TRUE.equals(ngTriggerEntity.getIsUnifiedPipelineFlow());
      return NGRestUtils.getResponse(pollingResourceClient.subscribe(
          RequestBody.create(MediaType.parse("application/octet-stream"), pollingItemBytes), isUnifiedPipelineFlow));

    } catch (Exception exception) {
      String msg = String.format("Polling Subscription Request failed for Trigger: %s with error ",
                       TriggerHelper.getTriggerRef(ngTriggerEntity))
          + exception;
      log.error(msg);
      throw new InvalidRequestException(msg);
    }
  }

  private Boolean executePollingUnSubscription(NGTriggerEntity ngTriggerEntity, byte[] pollingItemBytes) {
    try {
      return NGRestUtils.getGeneralResponse(pollingResourceClient.unsubscribe(
          RequestBody.create(MediaType.parse("application/octet-stream"), pollingItemBytes)));
    } catch (Exception exception) {
      String msg = String.format("Polling Unsubscription Request failed for Trigger: %s with error ",
                       TriggerHelper.getTriggerRef(ngTriggerEntity))
          + exception;
      log.error(msg);
      throw new InvalidRequestException(msg);
    }
  }

  private boolean checkForValidationFailure(NGTriggerEntity ngTriggerEntity) {
    return null != ngTriggerEntity.getTriggerStatus()
        && ngTriggerEntity.getTriggerStatus().getValidationStatus() != null
        && ngTriggerEntity.getTriggerStatus().getValidationStatus().getStatusResult() != StatusResult.SUCCESS;
  }

  private void updatePollingRegistrationStatus(NGTriggerEntity ngTriggerEntity, List<PollingDocument> pollingDocuments,
      StatusResult statusResult, PollingResponseDTO responseDTO, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    Criteria criteria =
        getTriggerEqualityCriteriaWithoutDbVersion(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);

    stampPollingStatusInfo(ngTriggerEntity, pollingDocuments, statusResult);
    if (responseDTO != null && statusResult.equals(StatusResult.SUCCESS)) {
      TriggerStatus status = ngTriggerEntity.getTriggerStatus();
      status.setPollingSubscriptionStatus(PollingSubscriptionStatus.builder()
                                              .statusResult(StatusResult.SUCCESS)
                                              .lastPolled(responseDTO.getLastPolled())
                                              .lastPollingUpdate(responseDTO.getLastPollingUpdate())
                                              .build());
      ngTriggerEntity.setTriggerStatus(status);
    }
    NGTriggerEntity updatedEntity = ngTriggerRepository.updateValidationStatusAndMetadata(criteria, ngTriggerEntity);

    if (updatedEntity == null) {
      throw new InvalidRequestException(
          String.format("NGTrigger [%s] couldn't be updated or doesn't exist", ngTriggerEntity.getIdentifier()));
    }
  }

  private void stampPollingStatusInfo(
      NGTriggerEntity ngTriggerEntity, List<PollingDocument> pollingDocuments, StatusResult statusResult) {
    // change pollingDocId only if request was successful. Else, we dont know what happened.
    // In next trigger upsert, we will try again
    if (statusResult == StatusResult.SUCCESS || statusResult == StatusResult.PENDING) {
      if (ngTriggerEntity.getType() == MULTI_REGION_ARTIFACT) {
        stampPollingInfoForMultiArtifactTrigger(ngTriggerEntity, pollingDocuments);
      } else {
        String pollingDocId = isEmpty(pollingDocuments) ? null : pollingDocuments.get(0).getPollingDocId();
        ngTriggerEntity.getMetadata().getBuildMetadata().getPollingConfig().setPollingDocId(pollingDocId);
      }
    }

    if (ngTriggerEntity.getTriggerStatus() == null) {
      ngTriggerEntity.setTriggerStatus(
          TriggerStatus.builder().pollingSubscriptionStatus(PollingSubscriptionStatus.builder().build()).build());
    } else if (ngTriggerEntity.getTriggerStatus().getPollingSubscriptionStatus() == null) {
      ngTriggerEntity.getTriggerStatus().setPollingSubscriptionStatus(PollingSubscriptionStatus.builder().build());
    }
    ngTriggerEntity.getTriggerStatus().getPollingSubscriptionStatus().setStatusResult(statusResult);
  }

  public void stampPollingInfoForMultiArtifactTrigger(
      NGTriggerEntity ngTriggerEntity, List<PollingDocument> pollingDocuments) {
    if (pollingDocuments == null) {
      ngTriggerEntity.getMetadata().setMultiBuildMetadata(Collections.emptyList());
      ngTriggerEntity.getMetadata().setSignatures(Collections.emptyList());
    } else {
      // Copy each pollingDocId to the corresponding BuildMetadata in Triggers metadata.
      IntStream.range(0, pollingDocuments.size())
          .forEach(index
              -> ngTriggerEntity.getMetadata().getMultiBuildMetadata().get(index).getPollingConfig().setPollingDocId(
                  pollingDocuments.get(index).getPollingDocId()));
      // Copy the new trigger's signatures to `ngTriggerEntity.metadata.signatures` list.
      ngTriggerEntity.getMetadata().setSignatures(
          ngTriggerEntity.getMetadata()
              .getMultiBuildMetadata()
              .stream()
              .map(buildMetadata -> buildMetadata.getPollingConfig().getSignature())
              .collect(Collectors.toList()));
    }
  }

  private void registerWebhookAsync(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (ngTriggerEntity.getType() == WEBHOOK
        && (ngTriggerEntity.getMetadata().getWebhook().getGit() != null
            || WebhookTriggerType.HARNESS_ARTIFACT_REGISTRY.getEntityMetadataName().equals(
                ngTriggerEntity.getMetadata().getWebhook().getType()))) {
      executorService.submit(() -> {
        try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext("webhookId", ngTriggerEntity.getWebhookId(),
                 ngTriggerEntity.getIdentifier(), ngTriggerEntity.getTargetIdentifier(),
                 isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
                 isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
                 ngTriggerEntity.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
          WebhookRegistrationStatusData registrationStatus = ngTriggerWebhookRegistrationService.registerWebhook(
              ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
          updateWebhookRegistrationStatus(ngTriggerEntity, registrationStatus, scopeInfo, isParentIdQueryingEnabled);
          checkAndEnableWebhookPolling(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
        }
      });
    }
  }

  private void checkAndEnableWebhookPolling(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (ngTriggerElementMapper.shouldGitWebhookPolling(ngTriggerEntity.getAccountId(),
            ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(), scopeInfo,
            isParentIdQueryingEnabled)
        && GITHUB.getEntityMetadataName().equalsIgnoreCase(ngTriggerEntity.getMetadata().getWebhook().getType())) {
      String webhookId = ngTriggerEntity.getTriggerStatus().getWebhookInfo().getWebhookId();
      String pollInterval = ngTriggerEntity.getPollInterval();

      if (StringUtils.isEmpty(webhookId) || StringUtils.isEmpty(pollInterval)) {
        log.error(String.format("Either Webhook Id or Poll Interval null. Polling cannot be enabled for the trigger %s"
                + ", webhookId %s, pollInterval %s}",
            ngTriggerEntity.getIdentifier(), webhookId, pollInterval));
        return;
      }
      subscribePolling(ngTriggerEntity, false, false, scopeInfo, isParentIdQueryingEnabled);
    }
  }

  @Override
  public Optional<NGTriggerEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String targetIdentifier, String identifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return scopeInfo != null
        ? ngTriggerRepository.findByParentUniqueIdAndTargetIdentifierAndIdentifier(
              scopeInfo.getUniqueId(), targetIdentifier, identifier)
        : ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifier(
              accountId, orgIdentifier, projectIdentifier, targetIdentifier, identifier);
  }

  @Override
  public NGTriggerEntity update(NGTriggerEntity ngTriggerEntity, NGTriggerEntity oldNgTriggerEntity,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    ngTriggerEntity.setYmlVersion(TRIGGER_CURRENT_YML_VERSION);
    Criteria criteria = getTriggerEqualityCriteria(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    NGTriggerEntity updatedTriggerEntity =
        updateTriggerEntity(ngTriggerEntity, criteria, scopeInfo, isParentIdQueryingEnabled);
    outboxService.save(new TriggerUpdateEvent(
        isParentIdQueryingEnabled ? scopeInfo.getAccountIdentifier() : ngTriggerEntity.getAccountId(),
        isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
        isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
        oldNgTriggerEntity, updatedTriggerEntity));
    try {
      List<EntityDetailProtoDTO> referredEntities =
          triggerReferenceHelper.getReferences(updatedTriggerEntity.getAccountId(),
              ngTriggerElementMapper.toTriggerConfigV2(updatedTriggerEntity, scopeInfo, isParentIdQueryingEnabled),
              scopeInfo, isParentIdQueryingEnabled);
      triggerSetupUsageHelper.publishSetupUsageEvent(
          updatedTriggerEntity, referredEntities, scopeInfo, isParentIdQueryingEnabled);
    } catch (Exception ex) {
      log.error("Error publishing the setup usages for the trigger with the identifier {} in project {} in org {} with "
              + "parentUniqueId {}",
          updatedTriggerEntity.getIdentifier(),
          isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : updatedTriggerEntity.getProjectIdentifier(),
          isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : updatedTriggerEntity.getOrgIdentifier(),
          isParentIdQueryingEnabled ? scopeInfo.getUniqueId() : updatedTriggerEntity.getParentUniqueId(), ex);
    }
    return updatedTriggerEntity;
  }

  @Override
  public BulkTriggersResponseDTO toggleTriggers(boolean enable, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String type, ScopeInfo scopeInfo) {
    boolean isParentIdQueryingEnabled = true;
    Criteria criteria = TriggerFilterHelper.getCriteriaForTogglingTriggersInBulk(enable, accountIdentifier,
        orgIdentifier, projectIdentifier, pipelineIdentifier, type, scopeInfo, isParentIdQueryingEnabled);

    List<NGTriggerEntity> toBeToggledTriggers = new ArrayList<>();
    List<NGTriggerEntity> triggersToggled = new ArrayList<>();

    long successfullyUpdated = 0;
    long failedToUpdate = 0;

    try (Stream<NGTriggerEntity> stream = ngTriggerRepository.findAll(criteria)) {
      Iterator<NGTriggerEntity> iterator = stream.iterator();
      while (iterator.hasNext()) {
        NGTriggerEntity ngTriggerEntity = iterator.next();
        ngTriggerEntity.setEnabled(enable);
        ngTriggerElementMapper.updateEntityYmlWithEnabledValue(ngTriggerEntity);
        toBeToggledTriggers.add(ngTriggerEntity);
        triggersToggled.add(ngTriggerEntity);

        if (toBeToggledTriggers.size() >= MAX_DISABLE_BATCH_SIZE) {
          TriggerUpdateCount triggerUpdateCount =
              ngTriggerRepository.toggleTriggerInBulk(toBeToggledTriggers, enable, isParentIdQueryingEnabled);
          successfullyUpdated = successfullyUpdated + triggerUpdateCount.getSuccessCount();
          failedToUpdate = failedToUpdate + triggerUpdateCount.getFailureCount();
          toBeToggledTriggers.clear();
        }
      }
    }

    if (EmptyPredicate.isNotEmpty(toBeToggledTriggers)) {
      TriggerUpdateCount triggerUpdateCount =
          ngTriggerRepository.toggleTriggerInBulk(toBeToggledTriggers, enable, isParentIdQueryingEnabled);
      successfullyUpdated = successfullyUpdated + triggerUpdateCount.getSuccessCount();
      failedToUpdate = failedToUpdate + triggerUpdateCount.getFailureCount();
    }

    String toggledAction = enable ? "enabled" : "disabled";

    log.info("Successfully {} {} and failed to {} {} triggers in account {}, org {}, project {}, pipeline {}",
        toggledAction, successfullyUpdated, toggledAction, failedToUpdate, accountIdentifier, orgIdentifier,
        projectIdentifier, pipelineIdentifier);

    // mapping the response
    List<BulkTriggerDetailDTO> bulkTriggerDetails = toBulkTriggerDetails(triggersToggled, null, false);

    return BulkTriggersResponseDTO.builder()
        .count(successfullyUpdated)
        .bulkTriggerDetailDTOList(bulkTriggerDetails)
        .build();
  }

  @NotNull
  private NGTriggerEntity updateTriggerEntity(
      NGTriggerEntity ngTriggerEntity, Criteria criteria, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEntity updatedEntity = ngTriggerRepository.update(criteria, ngTriggerEntity);
    if (updatedEntity == null) {
      throw new InvalidRequestException(
          String.format("NGTrigger [%s] couldn't be updated or doesn't exist", ngTriggerEntity.getIdentifier()));
    }

    performPostUpsertFlow(updatedEntity, true, scopeInfo, isParentIdQueryingEnabled);
    return updatedEntity;
  }

  @Override
  public boolean updateTriggerStatus(
      NGTriggerEntity ngTriggerEntity, boolean status, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria = getTriggerEqualityCriteria(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    ngTriggerEntity.setEnabled(status);
    NGTriggerEntity updatedEntity =
        updateTriggerEntity(ngTriggerEntity, criteria, scopeInfo, isParentIdQueryingEnabled);
    if (updatedEntity != null) {
      return updatedEntity.getEnabled();
    } else {
      throw new InvalidRequestException(
          String.format("NGTrigger [%s] couldn't be updated or doesn't exist", ngTriggerEntity.getIdentifier()));
    }
  }

  @Override
  public boolean updateTriggerPollingStatus(String accountId, PollingTriggerStatusUpdateDTO statusUpdate) {
    if (isEmpty(statusUpdate.getSignatures())) {
      throw new InvalidRequestException("Empty signatures list provided for trigger polling status update");
    }
    return ngTriggerRepository.updateManyTriggerPollingSubscriptionStatusBySignatures(accountId,
        statusUpdate.getSignatures(), statusUpdate.isSuccess(), statusUpdate.getErrorMessage(),
        statusUpdate.getLastCollectedVersions(), statusUpdate.getLastCollectedTime(),
        statusUpdate.getErrorStatusValidUntil());
  }

  @Override
  public Page<NGTriggerEntity> list(Criteria criteria, Pageable pageable) {
    return ngTriggerRepository.findAll(criteria, pageable);
  }

  @Override
  public boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String targetIdentifier,
      String identifier, Long version, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria = getTriggerEqualityCriteria(accountId, orgIdentifier, projectIdentifier, targetIdentifier,
        identifier, version, scopeInfo != null ? scopeInfo.getUniqueId() : null, isParentIdQueryingEnabled);

    Optional<NGTriggerEntity> ngTriggerEntity = get(accountId, orgIdentifier, projectIdentifier, targetIdentifier,
        identifier, scopeInfo, isParentIdQueryingEnabled);

    DeleteResult hardDeleteResult = ngTriggerRepository.hardDelete(criteria);
    if (!hardDeleteResult.wasAcknowledged()) {
      throw new InvalidRequestException(String.format("NGTrigger [%s] couldn't hard delete", identifier));
    }
    log.info("NGTrigger {} hard delete successful", identifier);

    if (ngTriggerEntity.isPresent()) {
      NGTriggerEntity foundTriggerEntity = ngTriggerEntity.get();
      outboxService.save(new TriggerDeleteEvent(
          isParentIdQueryingEnabled ? scopeInfo.getAccountIdentifier() : foundTriggerEntity.getAccountId(),
          isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : foundTriggerEntity.getOrgIdentifier(),
          isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : foundTriggerEntity.getProjectIdentifier(),
          foundTriggerEntity));

      boolean isWebhookGitPollingEnabled =
          isWebhookGitPollingEnabled(foundTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
      if (foundTriggerEntity.getType() == MANIFEST || foundTriggerEntity.getType() == ARTIFACT
          || foundTriggerEntity.getType() == MULTI_REGION_ARTIFACT || isWebhookGitPollingEnabled) {
        log.info("Submitting unsubscribe request after delete for Trigger :"
            + TriggerHelper.getTriggerRef(foundTriggerEntity));
        submitUnsubscribeAsync(foundTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
      }
      try {
        triggerSetupUsageHelper.deleteExistingSetupUsages(foundTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
      } catch (Exception ex) {
        log.error("Error while deleting the setup usages for the trigger with the identifier {} in project {} in org "
                + "{} with parentUniqueId {}",
            foundTriggerEntity.getIdentifier(),
            isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : foundTriggerEntity.getProjectIdentifier(),
            isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : foundTriggerEntity.getOrgIdentifier(),
            foundTriggerEntity.getParentUniqueId(), ex);
      }
    }
    return true;
  }

  private boolean isWebhookGitPollingEnabled(
      NGTriggerEntity foundTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (foundTriggerEntity.getType() == WEBHOOK
        && GITHUB.getEntityMetadataName().equalsIgnoreCase(foundTriggerEntity.getMetadata().getWebhook().getType())
        && ngTriggerElementMapper.shouldGitWebhookPolling(foundTriggerEntity.getAccountId(),
            foundTriggerEntity.getOrgIdentifier(), foundTriggerEntity.getProjectIdentifier(), scopeInfo,
            isParentIdQueryingEnabled)) {
      if (foundTriggerEntity.getTriggerStatus().getWebhookInfo() != null) {
        String webhookId = foundTriggerEntity.getTriggerStatus().getWebhookInfo().getWebhookId();
        String pollInterval = foundTriggerEntity.getPollInterval();
        return !StringUtils.isEmpty(webhookId) && !StringUtils.isEmpty(pollInterval);
      }
    }
    return false;
  }

  private void submitUnsubscribeAsync(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    // Fetch trigger to unsubscribe from polling
    if (ngTriggerEntity != null) {
      executorService.submit(() -> {
        try {
          List<PollingItem> pollingItems = pollingSubscriptionHelper.generatePollingItems(
              ngTriggerEntity, false, scopeInfo, isParentIdQueryingEnabled);
          for (PollingItem pollingItem : pollingItems) {
            if (!executePollingUnSubscription(ngTriggerEntity, kryoSerializer.asBytes(pollingItem))) {
              log.warn(String.format("Trigger %s failed to unsubscribe from Polling", ngTriggerEntity.getIdentifier()));
            }
          }
        } catch (Exception exception) {
          log.error(exception.getMessage());
        }
      });
    }
  }

  @Override
  public boolean deleteAllForPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String parentUniqueId) {
    String pipelineRef = new StringBuilder(128)
                             .append(accountId)
                             .append('/')
                             .append(orgIdentifier)
                             .append('/')
                             .append(projectIdentifier)
                             .append('/')
                             .append(pipelineIdentifier)
                             .toString();

    final AtomicBoolean exceptionOccured = new AtomicBoolean(false);

    try {
      boolean isParentIdQueryingEnabled = true;

      Optional<List<NGTriggerEntity>> nonDeletedTriggerForPipeline = isParentIdQueryingEnabled
          ? ngTriggerRepository.findByParentUniqueIdAndTargetIdentifier(parentUniqueId, pipelineIdentifier)
          : ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifier(
                accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);

      if (nonDeletedTriggerForPipeline.isPresent()) {
        log.info("Deleting triggers for pipeline-deletion-event. PipelineRef: " + pipelineRef);
        List<NGTriggerEntity> ngTriggerEntities = nonDeletedTriggerForPipeline.get();
        String triggerRef = new StringBuilder(128)
                                .append(accountId)
                                .append('/')
                                .append(orgIdentifier)
                                .append('/')
                                .append(projectIdentifier)
                                .append('/')
                                .append(pipelineIdentifier)
                                .append('/')
                                .append("{trigger}")
                                .toString();

        ngTriggerEntities.forEach(ngTriggerEntity -> {
          try {
            log.info("Deleting triggers for pipeline-deletion-event. TriggerRef: "
                + triggerRef.replace("{trigger}", ngTriggerEntity.getIdentifier()));
            ScopeInfo scopeInfo = ScopeInfo.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .uniqueId(parentUniqueId)
                                      .scopeType(ScopeLevel.PROJECT)
                                      .build();
            delete(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, ngTriggerEntity.getIdentifier(),
                null, scopeInfo, isParentIdQueryingEnabled);
          } catch (Exception e) {
            log.error("Error while deleting trigger while processing pipeline-delete-event. TriggerRef: "
                + triggerRef.replace("{trigger}", ngTriggerEntity.getIdentifier()));
            exceptionOccured.set(true);
          }
        });
      } else {
        log.info("No non-deleted Trigger found while processing pipeline-deletion-event. PipelineRef: " + pipelineRef);
      }
    } catch (Exception e) {
      log.error("Error while deleting triggers while processing pipeline-delete-event. PipelineRef: " + pipelineRef);
      exceptionOccured.set(true);
    }

    return !exceptionOccured.get();
  }

  @Override
  public WebhookEventProcessingDetails fetchTriggerEventHistory(String accountId, String eventId) {
    List<TriggerEventHistory> triggerEventHistoryList =
        triggerEventHistoryRepository.findByAccountIdAndEventCorrelationId(accountId, eventId);
    boolean isParentIdQueryingEnabled = true;
    if (triggerEventHistoryList.size() == 0) {
      throw new InvalidRequestException(
          String.format("Trigger event history doesn't exist for event with eventId %s", eventId));
    }
    TriggerEventHistory triggerEventHistory = triggerEventHistoryList.get(0);
    String warningMsg = null;
    if (triggerEventHistoryList.size() > 1) {
      warningMsg =
          "There are multiple trigger events generated from this eventId. This response contains only one of them.";
    }
    WebhookEventProcessingDetailsBuilder builder =
        WebhookEventProcessingDetails.builder().eventId(eventId).accountIdentifier(accountId);
    if (triggerEventHistory == null) {
      builder.eventFound(false);
    } else {
      ScopeInfo scopeInfo = null;
      if (isParentIdQueryingEnabled) {
        scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, triggerEventHistory.getParentUniqueId());
      }
      builder.eventFound(true)
          .orgIdentifier(
              isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : triggerEventHistory.getOrgIdentifier())
          .projectIdentifier(
              isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : triggerEventHistory.getProjectIdentifier())
          .triggerIdentifier(triggerEventHistory.getTriggerIdentifier())
          .pipelineIdentifier(triggerEventHistory.getTargetIdentifier())
          .exceptionOccured(triggerEventHistory.isExceptionOccurred())
          .status(triggerEventHistory.getFinalStatus())
          .message(triggerEventHistory.getMessage())
          .payload(triggerEventHistory.getPayload())
          .eventCreatedAt(triggerEventHistory.getCreatedAt())
          .warningMsg(warningMsg);

      if (triggerEventHistory.getTargetExecutionSummary() != null) {
        builder.pipelineExecutionId(triggerEventHistory.getTargetExecutionSummary().getPlanExecutionId())
            .runtimeInput(triggerEventHistory.getTargetExecutionSummary().getRuntimeInput());
      }
    }

    return builder.build();
  }

  @Override
  public TriggerWebhookEvent addEventToQueue(TriggerWebhookEvent webhookEventQueueRecord) {
    try {
      validateUniqueIdAndParentUniqueId(webhookEventQueueRecord);
      return webhookEventQueueRepository.save(webhookEventQueueRecord);
    } catch (Exception e) {
      log.error("Webhook event could not be received", e);
      throw new InvalidRequestException("Webhook event could not be received");
    }
  }

  public TriggerCustomWebhookEvent enqueueTriggerCustomWebhookEvent(
      TriggerCustomWebhookEvent triggerCustomWebhookEvent) {
    try {
      validateUniqueIdAndParentUniqueId(triggerCustomWebhookEvent);
      TriggerCustomWebhookEvent event = triggerCustomWebhookEventRepository.save(triggerCustomWebhookEvent);
      String topic = "pms" + TRIGGER_CUSTOM_WEBHOOK_EVENT;
      String payload = RecastOrchestrationUtils.toJson(TriggerCustomWebhookEventPayload.builder()
                                                           .eventCorrelationId(event.getUuid())
                                                           .accountId(event.getAccountId())
                                                           .build());
      EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                          .topic(topic)
                                          .subTopic(event.getAccountId())
                                          .producerName(topic)
                                          .payload(payload)
                                          .build();
      hsqsClientService.enqueue(enqueueRequest);
      return event;
    } catch (Exception e) {
      log.error("Exception while queueing webhook request", e);
      throw new InternalServerErrorException("Exception while queueing webhook request");
    }
  }

  private void validateUniqueIdAndParentUniqueId(TriggerWebhookEvent triggerWebhookEvent) {
    if (isEmpty(triggerWebhookEvent.getUniqueId())) {
      triggerWebhookEvent.setUniqueId(generateUuid());
    }
    if (isEmpty(triggerWebhookEvent.getParentUniqueId())) {
      Optional<ScopeInfo> scopeInfo = scopeResolutionHelper.getScopeInfoOptional(triggerWebhookEvent.getAccountId(),
          triggerWebhookEvent.getOrgIdentifier(), triggerWebhookEvent.getProjectIdentifier());
      String parentUniqueId = null;
      if (scopeInfo.isPresent()) {
        parentUniqueId = scopeInfo.get().getUniqueId();
      }
      triggerWebhookEvent.setParentUniqueId(parentUniqueId);
    }
  }

  private void validateUniqueIdAndParentUniqueId(TriggerCustomWebhookEvent triggerWebhookEvent) {
    if (isEmpty(triggerWebhookEvent.getUniqueId())) {
      triggerWebhookEvent.setUniqueId(generateUuid());
    }
    if (isEmpty(triggerWebhookEvent.getParentUniqueId())) {
      Optional<ScopeInfo> scopeInfo = scopeResolutionHelper.getScopeInfoOptional(triggerWebhookEvent.getAccountId(),
          triggerWebhookEvent.getOrgIdentifier(), triggerWebhookEvent.getProjectIdentifier());
      String parentUniqueId = null;
      if (scopeInfo.isPresent()) {
        parentUniqueId = scopeInfo.get().getUniqueId();
      }
      triggerWebhookEvent.setParentUniqueId(parentUniqueId);
    }
  }

  @Override
  public TriggerWebhookEvent updateTriggerWebhookEvent(TriggerWebhookEvent webhookEventQueueRecord) {
    Criteria criteria = getTriggerWebhookEventEqualityCriteria(webhookEventQueueRecord);
    TriggerWebhookEvent updatedEntity = webhookEventQueueRepository.update(criteria, webhookEventQueueRecord);
    if (updatedEntity == null) {
      throw new InvalidRequestException(
          "TriggerWebhookEvent with uuid " + webhookEventQueueRecord.getUuid() + " could not be updated");
    }
    return updatedEntity;
  }

  @Override
  public TriggerCustomWebhookEvent updateTriggerCustomWebhookEvent(
      String customWebhookEventId, Integer attemptCount, String status, List<String> allowedStatus) {
    Criteria criteria = Criteria.where(TriggerCustomWebhookEventsKeys.uuid)
                            .is(customWebhookEventId)
                            .and(TriggerCustomWebhookEventsKeys.processingStatus)
                            .in(allowedStatus);
    TriggerCustomWebhookEvent updatedEntity =
        triggerCustomWebhookEventRepository.update(criteria, attemptCount, status);
    if (updatedEntity == null) {
      log.warn("Unable to update TriggerCustomWebhookEvent with uuid {}", customWebhookEventId);
    }
    return updatedEntity;
  }

  @Override
  public void deleteTriggerWebhookEvent(TriggerWebhookEvent webhookEventQueueRecord) {
    webhookEventQueueRepository.delete(webhookEventQueueRecord);
  }

  @Override
  public void deleteTriggerCustomWebhookEvent(TriggerCustomWebhookEvent webhookEventQueueRecord) {
    triggerCustomWebhookEventRepository.delete(webhookEventQueueRecord);
  }

  @Override
  public List<NGTriggerEntity> findTriggersForCustomWehbook(TriggerWebhookEvent triggerWebhookEvent, boolean enabled) {
    boolean isParentUniqueIdQueryingEnabled = true;
    Page<NGTriggerEntity> triggersPage = list(TriggerFilterHelper.createCriteriaForCustomWebhookTriggerGetList(
                                                  triggerWebhookEvent, EMPTY, enabled, isParentUniqueIdQueryingEnabled),
        Pageable.unpaged());

    return triggersPage.get().collect(Collectors.toList());
  }

  @Override
  public Optional<NGTriggerEntity> findTriggersForCustomWebhookViaCustomWebhookToken(String webhookToken) {
    return ngTriggerRepository.findByCustomWebhookToken(webhookToken);
  }

  @Override
  public List<NGTriggerEntity> findTriggersForWehbookBySourceRepoType(
      TriggerWebhookEvent triggerWebhookEvent, boolean enabled) {
    Page<NGTriggerEntity> triggersPage =
        list(TriggerFilterHelper.createCriteriaFormWebhookTriggerGetListByRepoType(triggerWebhookEvent, EMPTY, enabled),
            Pageable.unpaged());

    return triggersPage.get().collect(Collectors.toList());
  }

  @Override
  public List<NGTriggerEntity> findBuildTriggersByAccountIdAndSignature(String accountId, List<String> signatures) {
    Page<NGTriggerEntity> triggersPage =
        list(TriggerFilterHelper.createCriteriaFormBuildTriggerUsingAccIdAndSignature(accountId, signatures),
            Pageable.unpaged());
    return triggersPage.get().collect(Collectors.toList());
  }

  @Override
  public List<NGTriggerEntity> findTriggersByCriteria(Criteria criteria) {
    Page<NGTriggerEntity> triggersPage = list(criteria, Pageable.unpaged());
    return triggersPage.get().collect(Collectors.toList());
  }

  @Override
  @Deprecated
  // unused method.
  public List<NGTriggerEntity> listEnabledTriggersForCurrentProject(
      String accountId, String orgIdentifier, String projectIdentifier) {
    Optional<List<NGTriggerEntity>> enabledTriggerForProject;

    // Now kept for backward compatibility, but will be changed soon to validate for non-empty project and
    // orgIdentifier.
    if (isNotEmpty(projectIdentifier) && isNotEmpty(orgIdentifier)) {
      enabledTriggerForProject = ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnabled(
          accountId, orgIdentifier, projectIdentifier, true);
    } else if (isNotEmpty(orgIdentifier)) {
      enabledTriggerForProject =
          ngTriggerRepository.findByAccountIdAndOrgIdentifierAndEnabled(accountId, orgIdentifier, true);
    } else {
      enabledTriggerForProject = ngTriggerRepository.findByAccountIdAndEnabled(accountId, true);
    }

    if (enabledTriggerForProject.isPresent()) {
      return enabledTriggerForProject.get();
    }

    return emptyList();
  }

  @Override
  public List<NGTriggerEntity> listEnabledTriggersForAccount(String accountId) {
    return listEnabledTriggersForCurrentProject(accountId, null, null);
  }

  @Override
  public List<ConnectorResponseDTO> fetchConnectorsByFQN(String accountIdentifier, List<String> fqns) {
    if (isEmpty(fqns)) {
      return emptyList();
    }
    try {
      return NGRestUtils.getResponse(connectorResourceClient.listConnectorByFQN(accountIdentifier, fqns));
    } catch (Exception e) {
      log.error("Failed while retrieving connectors", e);
      throw new TriggerException("Failed while retrieving connectors" + e.getMessage(), e, USER_SRE);
    }
  }

  @Override
  public void validateTriggerConfigForCreate(
      TriggerDetails triggerDetails, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    validateTriggerConfig(triggerDetails, scopeInfo, isParentIdQueryingEnabled);

    // Skip regex validation if feature flag is enabled
    if (pmsFeatureFlagService.isEnabled(triggerDetails.getNgTriggerEntity().getAccountId(),
            FeatureName.PIPE_DISABLE_TRIGGER_NAME_IDENTIFIER_REGEX_VALIDATION)) {
      return;
    }

    // Trigger name/identifier pattern validation
    String name = triggerDetails.getNgTriggerEntity().getName();
    if (!Pattern.compile(NGRegexValidatorConstants.NAME_PATTERN).matcher(name).matches()) {
      throw new InvalidArgumentsException(
          String.format("Trigger name must start with a letter, number, underscore, hyphen, or dot and can only "
                  + "contain alphanumeric, dot, hyphen, space and underscore characters: %s",
              name));
    }

    String identifier = triggerDetails.getNgTriggerEntity().getIdentifier();
    if (!Pattern.compile(NGRegexValidatorConstants.IDENTIFIER_PATTERN).matcher(identifier).matches()) {
      throw new InvalidArgumentsException(String.format("Trigger identifier must start with a letter or underscore and "
              + "can only contain alphanumeric and underscore characters: %s",
          identifier));
    }
  }

  @Override
  public void validateTriggerConfig(
      TriggerDetails triggerDetails, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    // will be returned if certain conditions are not met. Either use this as a gateway or spin off a specific class
    // for the validation.

    // trigger source validation

    if (isBlank(triggerDetails.getNgTriggerEntity().getIdentifier())) {
      throw new InvalidArgumentsException("Identifier can not be empty");
    }

    if (isBlank(triggerDetails.getNgTriggerEntity().getName())) {
      throw new InvalidArgumentsException("Name can not be empty");
    }
    if (isBlank(triggerDetails.getNgTriggerEntity().getTargetIdentifier())) {
      throw new InvalidArgumentsException("Pipeline identifier can not be empty");
    }
    // name and identifier cannot be an expression
    if (EXPRESSION_PATTERN.matcher(triggerDetails.getNgTriggerEntity().getName()).find()) {
      throw new InvalidArgumentsException(String.format("Trigger name can not contain special characters or spaces: %s",
          triggerDetails.getNgTriggerEntity().getName()));
    }
    if (EXPRESSION_PATTERN.matcher(triggerDetails.getNgTriggerEntity().getIdentifier()).find()) {
      throw new InvalidArgumentsException(
          String.format("Trigger identifier can not contain special characters or spaces: %s",
              triggerDetails.getNgTriggerEntity().getIdentifier()));
    }

    NGTriggerSourceV2 triggerSource = triggerDetails.getNgTriggerConfigV2().getSource();
    NGTriggerSpecV2 spec = triggerSource.getSpec();
    switch (triggerSource.getType()) {
      case WEBHOOK:
        // Validate webhook polling trigger
        WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) triggerSource.getSpec();
        if (webhookTriggerConfig.getType() != GITHUB) {
          return;
        }

        if (ngTriggerElementMapper.shouldGitWebhookPolling(triggerDetails.getNgTriggerEntity().getAccountId(),
                triggerDetails.getNgTriggerEntity().getOrgIdentifier(),
                triggerDetails.getNgTriggerEntity().getProjectIdentifier(), scopeInfo, isParentIdQueryingEnabled)) {
          String pollInterval = triggerDetails.getNgTriggerEntity().getPollInterval();
          if (StringUtils.isEmpty(pollInterval)) {
            log.info("Polling not enabled for trigger {}, pollInterval {} ",
                triggerDetails.getNgTriggerEntity().getIdentifier(), pollInterval);
            return;
          }
          int pollInt = NGTimeConversionHelper.convertTimeStringToMinutesZeroAllowed(
              triggerDetails.getNgTriggerEntity().getPollInterval());
          if (pollInt != WEBHOOK_POLLING_UNSUBSCRIBE
              && (pollInt < WEBHOOOk_POLLING_MIN_INTERVAL || pollInt > WEBHOOOk_POLLING_MAX_INTERVAL)) {
            throw new InvalidArgumentsException("Poll Interval should be between " + WEBHOOOk_POLLING_MIN_INTERVAL
                + " and " + WEBHOOOk_POLLING_MAX_INTERVAL + " minutes");
          }
        }
        return; // TODO define other trigger source validation
      case SCHEDULED:
        ScheduledTriggerConfig scheduledTriggerConfig = (ScheduledTriggerConfig) triggerSource.getSpec();
        CronTriggerSpec cronTriggerSpec = (CronTriggerSpec) scheduledTriggerConfig.getSpec();
        boolean useAndSemantics = Boolean.TRUE.equals(triggerDetails.getNgTriggerEntity().getCronAndSemantics());
        CronParser cronParser;
        if (cronTriggerSpec.getType() != null && cronTriggerSpec.getType().equalsIgnoreCase("QUARTZ")) {
          cronParser =
              useAndSemantics ? PersistentNGCronIterable.quartzAndParser : PersistentNGCronIterable.quartzParser;
        } else {
          cronParser = useAndSemantics ? PersistentNGCronIterable.unixAndParser : PersistentNGCronIterable.unixParser;
        }
        Cron cron = cronParser.parse(cronTriggerSpec.getExpression());
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        Optional<ZonedDateTime> firstExecutionTimeOptional = executionTime.nextExecution(ZonedDateTime.now());
        if (!firstExecutionTimeOptional.isPresent()) {
          throw new InvalidArgumentsException("cannot find iteration time!");
        }
        ZonedDateTime firstExecutionTime = firstExecutionTimeOptional.get();
        Optional<ZonedDateTime> secondExecutionTimeOptional = executionTime.nextExecution(firstExecutionTime);
        if (secondExecutionTimeOptional.isPresent()) {
          ZonedDateTime secondExecutionTime = secondExecutionTimeOptional.get();
          if (Duration.between(firstExecutionTime, secondExecutionTime).getSeconds() < MIN_INTERVAL_MINUTES * 60) {
            throw new InvalidArgumentsException("Cron interval must be greater than or equal to " + MIN_INTERVAL_MINUTES
                + " minutes. The next two execution times when this trigger is suppose to fire are "
                + firstExecutionTime.toLocalTime().toString() + " and " + secondExecutionTime.toLocalTime().toString()
                + " which do not have a difference of " + MIN_INTERVAL_MINUTES + " minutes between them.");
          }
        }
        return;
      case MANIFEST:
        validateStageIdentifierAndBuildRef(
            (BuildAware) spec, "manifestRef", triggerDetails.getNgTriggerEntity().getWithServiceV2());
        ManifestTriggerConfig manifestTriggerConfig = (ManifestTriggerConfig) spec;
        validateEventConditionKeys(manifestTriggerConfig.getSpec() == null
                ? null
                : manifestTriggerConfig.getSpec().fetchEventDataConditions());
        return;
      case ARTIFACT:
        validateStageIdentifierAndBuildRef(
            (BuildAware) spec, "artifactRef", triggerDetails.getNgTriggerEntity().getWithServiceV2());
        ArtifactTriggerConfig artifactTriggerConfig = (ArtifactTriggerConfig) spec;
        validateEventConditionKeys(artifactTriggerConfig.getSpec() == null
                ? null
                : artifactTriggerConfig.getSpec().fetchEventDataConditions());
        return;
      case MULTI_REGION_ARTIFACT:
        MultiRegionArtifactTriggerConfig multiRegionArtifactTriggerConfig = (MultiRegionArtifactTriggerConfig) spec;
        validateMultiRegionArtifactTriggerConfig(
            multiRegionArtifactTriggerConfig, triggerDetails.getNgTriggerEntity().getWithServiceV2());
        validateEventConditionKeys(multiRegionArtifactTriggerConfig.getEventConditions());
        return;
      default:
        return; // not implemented
    }
  }

  private void validateEventConditionKeys(List<TriggerEventDataCondition> eventConditions) {
    if (isEmpty(eventConditions)) {
      return;
    }

    for (TriggerEventDataCondition condition : eventConditions) {
      if (condition == null || !VALID_EVENT_CONDITION_KEYS.contains(condition.getKey())) {
        throw new InvalidRequestException(
            String.format("Invalid eventConditions key '%s'. Valid keys are [build, version].",
                condition == null ? null : condition.getKey()));
      }
    }
  }

  @Override
  public void validatePipelineRef(
      TriggerDetails triggerDetails, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())) {
      PMSPipelineResponseDTO pipelineResponse =
          validationHelper.fetchPipelineForTrigger(triggerDetails, scopeInfo, isParentIdQueryingEnabled);
      if (pipelineResponse != null && pipelineResponse.getStoreType() == StoreType.REMOTE
          && isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())) {
        throw new InvalidRequestException("pipelineBranchName is missing or is empty.");
      }
    }
  }

  private String getPipelineComponent(String triggerYml) {
    try {
      if (isEmpty(triggerYml)) {
        return triggerYml;
      }
      JsonNode node = YamlUtils.readTree(triggerYml).getNode().getCurrJsonNode();
      ObjectNode innerMap = (ObjectNode) node.get(TRIGGER);
      if (innerMap == null) {
        throw new InvalidRequestException("Invalid Trigger Yaml.");
      }
      JsonNode inputYaml = innerMap.get(INPUT_YAML);
      if (inputYaml == null) {
        throw new InvalidRequestException("Invalid Trigger Yaml.");
      }
      JsonNode pipelineNode = YamlUtils.readTree(inputYaml.asText()).getNode().getCurrJsonNode();
      return YamlUtils.writeYamlString(pipelineNode);
    } catch (IOException e) {
      throw new InvalidYamlException("Invalid Trigger Yaml", e);
    }
  }

  private String setPipelineComponent(String triggerYml, String pipelineComponent) {
    try {
      if (isEmpty(triggerYml)) {
        return triggerYml;
      }
      JsonNode node = YamlUtils.readTree(triggerYml).getNode().getCurrJsonNode();
      ObjectNode innerMap = (ObjectNode) node.get(TRIGGER);
      if (innerMap == null) {
        throw new InvalidRequestException("Invalid Trigger Yaml.");
      }
      innerMap.set(INPUT_YAML, new TextNode(pipelineComponent));
      return YamlUtils.writeYamlString(node);
    } catch (IOException e) {
      throw new InvalidYamlException("Invalid Trigger Yaml", e);
    }
  }

  public Map<FQN, String> getInvalidFQNsInTrigger(
      String templateYaml, String triggerPipelineCompYaml, String accountIdentifier) {
    Map<FQN, String> errorMap = new LinkedHashMap<>();
    YamlConfig triggerConfig = new YamlConfig(triggerPipelineCompYaml);
    Set<FQN> triggerFQNs = new LinkedHashSet<>(triggerConfig.getFqnToValueMap().keySet());
    if (isEmpty(templateYaml)) {
      triggerFQNs.forEach(fqn -> errorMap.put(fqn, "Pipeline no longer contains runtime input"));
      return errorMap;
    }
    YamlConfig templateConfig = new YamlConfig(templateYaml);

    if (CollectionUtils.isEmpty(triggerFQNs)) {
      templateConfig.getFqnToValueMap().keySet().forEach(
          fqn -> errorMap.put(fqn, "Trigger does not contain pipeline runtime input"));
      return errorMap;
    }

    // Make sure everything in trigger exist in pipeline
    templateConfig.getFqnToValueMap().keySet().forEach(key -> {
      if (triggerFQNs.contains(key)) {
        Object templateValue = templateConfig.getFqnToValueMap().get(key);
        Object value = triggerConfig.getFqnToValueMap().get(key);
        if (key.isType() || key.isIdentifierOrVariableName()) {
          if (!value.toString().equals(templateValue.toString())) {
            errorMap.put(key,
                "The value for " + key.getExpressionFqn() + " is " + templateValue.toString()
                    + "in the pipeline yaml, but the trigger has it as " + value.toString());
          }
        } else {
          String error = validateStaticValues(templateValue, value, key.getExpressionFqn());
          if (isNotEmpty(error)) {
            errorMap.put(key, error);
          }
        }

        triggerFQNs.remove(key);
      } else {
        Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(triggerConfig.getFqnToValueMap(), key);
        subMap.keySet().forEach(triggerFQNs::remove);
      }
    });
    triggerFQNs.forEach(fqn -> errorMap.put(fqn, "Field either not present in pipeline or not a runtime input"));
    return errorMap;
  }

  public String createRuntimeInputForm(String yaml) {
    YamlConfig yamlConfig = new YamlConfig(yaml);
    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();
    fullMap.keySet().forEach(key -> {
      String value = fullMap.get(key).toString().replace("\"", "");
      if (NGExpressionUtils.matchesInputSetPattern(value)) {
        templateMap.put(key, fullMap.get(key));
      }
    });
    return (new YamlConfig(templateMap, yamlConfig.getYamlMap())).getYaml();
  }

  private void validateStageIdentifierAndBuildRef(BuildAware buildAware, String fieldName, boolean serviceV2) {
    StringBuilder msg = new StringBuilder(128);
    boolean validationFailed = false;
    if (serviceV2 == false && isBlank(buildAware.fetchStageRef())) {
      msg.append("stageIdentifier can not be blank/missing. ");
      validationFailed = true;
    }
    if (serviceV2 == false && isBlank(buildAware.fetchbuildRef())) {
      msg.append(fieldName).append(" can not be blank/missing. ");
      validationFailed = true;
    }

    if (validationFailed) {
      throw new InvalidArgumentsException(msg.toString());
    }
  }

  private void validateMultiRegionArtifactTriggerConfig(
      MultiRegionArtifactTriggerConfig triggerConfig, boolean serviceV2) {
    StringBuilder msg = new StringBuilder(256);
    boolean validationFailed = false;
    if (!serviceV2) {
      msg.append("Multi-Artifact triggers are only supported with Service V2.\n");
      validationFailed = true;
    }
    if (triggerConfig.getType() == null) {
      msg.append("Multi-region Artifact trigger source type must have a valid artifact source type value.\n");
    }
    if (isEmpty(triggerConfig.getSources())) {
      msg.append("Multi-region Artifact trigger sources list must have at least one element.\n");
      validationFailed = true;
    }
    if (isNotEmpty(triggerConfig.getSources())) {
      String artifactBuildType = triggerConfig.fetchBuildType();
      for (ArtifactTypeSpecWrapper artifactSpecWrapper : triggerConfig.getSources()) {
        if (!artifactBuildType.equals(artifactSpecWrapper.getSpec().fetchBuildType())) {
          msg.append("Multi-region Artifact sources must all be of type ").append(artifactBuildType).append(".\n");
          validationFailed = true;
          break;
        }
      }
      if (triggerConfig.getSources().size() > maxMultiArtifactTriggerSourcesProvider.get()) {
        msg.append("The maximum number of sources for Multi-Artifact trigger is ")
            .append(maxMultiArtifactTriggerSourcesProvider.get())
            .append(".\n");
        validationFailed = true;
      }
    }
    if (validationFailed) {
      throw new InvalidArgumentsException(msg.toString());
    }
  }

  private void updateWebhookRegistrationStatus(NGTriggerEntity ngTriggerEntity,
      WebhookRegistrationStatusData registrationStatus, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria =
        getTriggerEqualityCriteriaWithoutDbVersion(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    TriggerHelper.stampWebhookRegistrationInfo(ngTriggerEntity, registrationStatus.getWebhookAutoRegistrationStatus());
    TriggerHelper.stampWebhookIdInfo(ngTriggerEntity, registrationStatus.getWebhookId());
    NGTriggerEntity updatedEntity = ngTriggerRepository.update(criteria, ngTriggerEntity);
    if (updatedEntity == null) {
      throw new InvalidRequestException(
          String.format("NGTrigger [%s] couldn't be updated or doesn't exist", ngTriggerEntity.getIdentifier()));
    }
  }
  private Criteria getTriggerWebhookEventEqualityCriteria(TriggerWebhookEvent webhookEventQueueRecord) {
    return Criteria.where(TriggerWebhookEventsKeys.uuid).is(webhookEventQueueRecord.getUuid());
  }

  private Criteria getTriggerEqualityCriteria(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return getTriggerEqualityCriteria(ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
        ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getTargetIdentifier(), ngTriggerEntity.getIdentifier(),
        ngTriggerEntity.getVersion(), isParentIdQueryingEnabled ? scopeInfo.getUniqueId() : null,
        isParentIdQueryingEnabled);
  }

  private Criteria getTriggerEqualityCriteriaWithoutDbVersion(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return getTriggerEqualityCriteria(ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
        ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getTargetIdentifier(), ngTriggerEntity.getIdentifier(),
        null, isParentIdQueryingEnabled ? scopeInfo.getUniqueId() : null, isParentIdQueryingEnabled);
  }

  private Criteria getTriggerEqualityCriteria(String accountId, String orgIdentifier, String projectIdentifier,
      String targetIdentifier, String identifier, Long version, String parentUniqueId,
      boolean isParentIdQueryingEnabled) {
    Criteria criteria = new Criteria();
    if (isParentIdQueryingEnabled) {
      criteria.and(NGTriggerEntityKeys.parentUniqueId).is(parentUniqueId);
    } else {
      criteria.and(NGTriggerEntityKeys.accountId).is(accountId);
      criteria.and(NGTriggerEntityKeys.orgIdentifier).is(orgIdentifier);
      criteria.and(NGTriggerEntityKeys.projectIdentifier).is(projectIdentifier);
    }
    criteria.and(NGTriggerEntityKeys.targetIdentifier).is(targetIdentifier);
    criteria.and(NGTriggerEntityKeys.identifier).is(identifier);
    if (version != null) {
      criteria.and(NGTriggerEntityKeys.version).is(version);
    }
    return criteria;
  }

  public NGTriggerEntity validateTrigger(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    try {
      ValidationResult validationResult;
      if (HarnessYamlVersion.V0.equals(ngTriggerEntity.getHarnessVersion())) {
        validationResult = triggerValidationHandler.applyValidations(
            ngTriggerElementMapper.toTriggerDetails(ngTriggerEntity.getAccountId(),
                isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
                isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
                ngTriggerEntity.getParentUniqueId(), ngTriggerEntity.getYaml(), ngTriggerEntity.getWithServiceV2()),
            scopeInfo, isParentIdQueryingEnabled);
      } else {
        validationResult = triggerValidationHandler.applyValidations(
            ngTriggerElementMapper.toTriggerDetails(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled), scopeInfo,
            isParentIdQueryingEnabled);
      }
      if (!validationResult.isSuccess()) {
        ngTriggerEntity.setEnabled(false);
      }
      return updateTriggerWithValidationStatus(
          ngTriggerEntity, validationResult, false, scopeInfo, isParentIdQueryingEnabled);
    } catch (Exception e) {
      ValidationResult validationResult = ValidationResult.builder().success(false).message(e.getMessage()).build();
      log.error(String.format("Failed in trigger validation for Trigger: %s", ngTriggerEntity.getIdentifier()), e);
      return updateTriggerWithValidationStatus(
          ngTriggerEntity, validationResult, true, scopeInfo, isParentIdQueryingEnabled);
    }
  }

  /*
  This function is to update ValidatioStatus. Will be used by triggerlist to display the status of trigger in case of
  failure in Polling, etc
   */
  public NGTriggerEntity updateTriggerWithValidationStatus(NGTriggerEntity ngTriggerEntity,
      ValidationResult validationResult, boolean whileExecution, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    String identifier = ngTriggerEntity.getIdentifier();
    Criteria criteria =
        getTriggerEqualityCriteriaWithoutDbVersion(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    boolean needsUpdate = false;

    if (ngTriggerEntity.getTriggerStatus() == null) {
      ngTriggerEntity.setTriggerStatus(
          TriggerStatus.builder().validationStatus(ValidationStatus.builder().build()).build());
    } else if (ngTriggerEntity.getTriggerStatus().getValidationStatus() == null) {
      ngTriggerEntity.getTriggerStatus().setValidationStatus(ValidationStatus.builder().build());
    }

    if (validationResult.isSuccess() && ngTriggerEntity.getTriggerStatus().getValidationStatus() != null
        && ngTriggerEntity.getTriggerStatus().getValidationStatus().getStatusResult() != StatusResult.SUCCESS) {
      // Validation result was a failure and now it's a success
      ngTriggerEntity.getTriggerStatus().setValidationStatus(
          ValidationStatus.builder().statusResult(StatusResult.SUCCESS).build());
      needsUpdate = true;
    } else if (!validationResult.isSuccess()) {
      // Validation failed
      ngTriggerEntity.getTriggerStatus().setValidationStatus(ValidationStatus.builder()
                                                                 .statusResult(StatusResult.FAILED)
                                                                 .detailedMessage(validationResult.getMessage())
                                                                 .build());
      if (!whileExecution) {
        ngTriggerEntity.setEnabled(false);
      }
      needsUpdate = true;
    }

    if (needsUpdate) {
      // enabled filed is part of yml as well as extracted at the entity level.
      // if we are setting it to false, we need to update yml content as well.
      // With gitsync, we need to brainstorm
      ngTriggerElementMapper.updateEntityYmlWithEnabledValue(ngTriggerEntity);
      ngTriggerEntity = ngTriggerRepository.updateValidationStatus(criteria, ngTriggerEntity);
      if (ngTriggerEntity == null) {
        throw new InvalidRequestException(
            String.format("NGTrigger [%s] couldn't be updated or doesn't exist", identifier));
      }
    }

    return ngTriggerEntity;
  }

  @Override
  public TriggerDetails fetchTriggerEntityV1(String accountId, String orgId, String projectId, String pipelineId,
      String triggerId, NGTriggerConfigV2 config, NGTriggerEntity entity, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    Optional<NGTriggerEntity> existingEntity =
        get(accountId, orgId, projectId, pipelineId, triggerId, scopeInfo, isParentIdQueryingEnabled);
    if (existingEntity.isPresent()) {
      ngTriggerElementMapper.copyEntityFieldsOutsideOfYml(
          existingEntity.get(), entity, scopeInfo, isParentIdQueryingEnabled);
    }

    return TriggerDetails.builder().ngTriggerConfigV2(config).ngTriggerEntity(entity).build();
  }

  @Override
  public TriggerDetails fetchTriggerEntity(String accountId, String orgId, String projectId, String pipelineId,
      String triggerId, String newYaml, boolean withServiceV2, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerConfigV2 config = ngTriggerElementMapper.toTriggerConfigV2(newYaml);
    Optional<NGTriggerEntity> existingEntity =
        get(accountId, orgId, projectId, pipelineId, triggerId, scopeInfo, isParentIdQueryingEnabled);
    NGTriggerEntity entity =
        ngTriggerElementMapper.toTriggerEntity(accountId, orgId, projectId, triggerId, newYaml, withServiceV2);
    if (existingEntity.isPresent()) {
      ngTriggerElementMapper.copyEntityFieldsOutsideOfYml(
          existingEntity.get(), entity, scopeInfo, isParentIdQueryingEnabled);
    }

    return TriggerDetails.builder().ngTriggerConfigV2(config).ngTriggerEntity(entity).build();
  }

  public Object fetchExecutionSummaryV2(String planExecutionId, String accountId, String orgId, String projectId) {
    return NGRestUtils.getResponse(
        pipelineServiceClient.getExecutionDetailV2(planExecutionId, accountId, orgId, projectId));
  }
  @Override
  public List<TriggerCatalogItem> getTriggerCatalog(String accountIdentifier) {
    return triggerCatalogHelper.getTriggerTypeToCategoryMapping(accountIdentifier);
  }

  private String getUpdatedTriggerPipelineComponent(String triggerPipelineYaml, String templateYaml) {
    // This method updates the input specs in trigger's yaml according to the current pipeline's input specs
    // In case pipeline's input specs have changed since trigger's creation, this will fix the trigger's input yaml
    String newTriggerPipelineYaml = "pipeline: {}\n";
    if (isNotEmpty(templateYaml)) {
      YamlConfig templateConfig = new YamlConfig(templateYaml);
      YamlConfig triggerConfig = new YamlConfig(triggerPipelineYaml);
      Map<FQN, Object> toUpdateTriggerPipelineFQNToValueMap = new HashMap<>();
      Set<FQN> triggerFQNs = new LinkedHashSet<>(triggerConfig.getFqnToValueMap().keySet());
      templateConfig.getFqnToValueMap().forEach((key, templateValue) -> {
        // Iterate through pipeline's input keys
        // If trigger contains input spec which no longer exists in pipeline, it will not be added
        if (triggerFQNs.contains(key)) {
          // Case where trigger input yaml contains a key from pipeline's input yaml
          Object triggerValue = triggerConfig.getFqnToValueMap().get(key);
          if (key.isType() || key.isIdentifierOrVariableName()) {
            // If key is variable identifier or name, keep it
            toUpdateTriggerPipelineFQNToValueMap.put(key, templateValue);
          } else {
            // If key is variable value, validate its value type
            String error = validateStaticValues(templateValue, triggerValue, key.getExpressionFqn());
            if (isNotEmpty(error)) {
              // Replace by empty variable value if validation fails (user will need to provide the value)
              toUpdateTriggerPipelineFQNToValueMap.put(key, "");
            } else {
              // Keep variable value if validation succeeds
              toUpdateTriggerPipelineFQNToValueMap.put(key, triggerValue);
            }
          }
        } else {
          // Case where trigger input yaml does not contain a key from pipeline's input yaml
          if (key.isType() || key.isIdentifierOrVariableName()) {
            // If key is variable identifier or name, add it
            toUpdateTriggerPipelineFQNToValueMap.put(key, templateValue);
          } else {
            // If key is variable value, add it with empty value (user will need to provide the value)
            toUpdateTriggerPipelineFQNToValueMap.put(key, "");
          }
        }
      });
      newTriggerPipelineYaml =
          new YamlConfig(toUpdateTriggerPipelineFQNToValueMap, templateConfig.getYamlMap(), true).getYaml();
    }
    return newTriggerPipelineYaml;
  }

  @Override
  public void checkAuthorization(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, List<HeaderConfig> headerConfigs) {
    boolean hasApiKey = false;
    for (HeaderConfig headerConfig : headerConfigs) {
      if (headerConfig.getKey().equalsIgnoreCase(X_API_KEY)) {
        hasApiKey = true;
        break;
      }
    }
    if (!hasApiKey) {
      String mandatoryAuth = NGRestUtils
                                 .getResponse(settingsClient.getSetting(MANDATE_CUSTOM_WEBHOOK_AUTHORIZATION,
                                     accountIdentifier, orgIdentifier, projectIdentifier))
                                 .getValue();
      if (mandatoryAuth.equals(MANDATE_CUSTOM_WEBHOOK_TRUE_VALUE)) {
        throw new InvalidRequestException(String.format(
            "Authorization is mandatory for custom triggers in %s:%s:%s. Please add %s header in the request",
            accountIdentifier, orgIdentifier, projectIdentifier, X_API_KEY));
      }
    }
    if (hasApiKey) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
          Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_EXECUTE);
    }
  }

  @Override
  public void checkAuthorizationForWebhookDetailsResources(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String endpoint) {
    /* This method is for usage by handlers of /triggerProcessingDetails and /triggerExecutionDetails endpoints.
       Here we check if the principal has PIPELINE_VIEW permission. If either the principal is not present
       (non-authenticated call) or the principal doesn't have the permission, then we check if Feature Flag
       CDS_AUTH_CHECK_IN_WEBHOOK_DETAILS_ENDPOINTS is enabled. If it is enabled, an error is thrown. Otherwise, a
       warning is logged.

       This will be necessary because the above-mentioned endpoints have always been public.
       We will slowly transition customers to use authentication in these APIs, then proceed to enable the Feature
       Flag for all of them. */
    try {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
          Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_VIEW);
    } catch (Exception ex) {
      if (pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.CDS_AUTH_CHECK_IN_WEBHOOK_DETAILS_ENDPOINTS)) {
        io.harness.security.dto.Principal contextPrincipal = SecurityContextBuilder.getPrincipal();
        if (contextPrincipal == null) {
          throw new InvalidRequestException("Invalid or missing credentials", INVALID_CREDENTIAL, USER);
        } else {
          throw ex;
        }
      } else {
        log.warn("access-control check failed in call to webhook details endpoint /{} with message {}", endpoint,
            ex.getMessage());
      }
    }
  }

  public TriggerYamlDiffDTO getTriggerYamlDiff(
      TriggerDetails triggerDetails, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String triggerYaml = triggerDetails.getNgTriggerEntity().getYaml();
    String newTriggerYaml;
    List<String> inputSetRefs = new ArrayList<>();
    if (triggerDetails.getNgTriggerConfigV2() != null && triggerDetails.getNgTriggerConfigV2().getInputSetRefs() != null
        && !triggerDetails.getNgTriggerConfigV2().getInputSetRefs().isExpression()) {
      inputSetRefs = triggerDetails.getNgTriggerConfigV2().getInputSetRefs().getValue();
    }
    if (triggerDetails.getNgTriggerConfigV2() == null
        || isNotEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName()) || isNotEmpty(inputSetRefs)
        || (triggerDetails.getNgTriggerConfigV2().getInputSetRefs() != null
            && triggerDetails.getNgTriggerConfigV2().getInputSetRefs().isExpression())) {
      // No reconciliation can be done at trigger level if it is for remote pipeline or if it is using input sets
      newTriggerYaml = triggerYaml;
    } else {
      Optional<String> pipelineYmlOptional =
          validationHelper.fetchPipelineYamlForTrigger(triggerDetails, scopeInfo, isParentIdQueryingEnabled);
      if (pipelineYmlOptional.isPresent()) {
        String pipelineYaml = pipelineYmlOptional.get();
        String templateYaml = createRuntimeInputForm(pipelineYaml);
        String triggerPipelineYaml = getPipelineComponent(triggerYaml);
        String newTriggerPipelineYaml = getUpdatedTriggerPipelineComponent(triggerPipelineYaml, templateYaml);
        newTriggerYaml = setPipelineComponent(triggerYaml, newTriggerPipelineYaml);
      } else {
        throw new NGPipelineNotFoundException(
            "No pipeline found for trigger " + triggerDetails.getNgTriggerConfigV2().getIdentifier());
      }
    }
    return TriggerYamlDiffDTO.builder().oldYAML(triggerYaml).newYAML(newTriggerYaml).build();
  }

  public void resetPollingTriggers(Criteria criteria, boolean isParentIdQueryingEnabled, String accountIdentifier) {
    /* This method can be used to reset any set of polling triggers (ARTIFACT or MANIFEST types), by providing an
       appropriate criteria to fetch them from the `triggersNG` collection.
       WARNING: Make sure there is an index for the provided query in `triggersNG` collection. */
    try {
      List<NGTriggerEntity> triggersToReset = new ArrayList<>();
      try (Stream<NGTriggerEntity> stream = ngTriggerRepository.findAll(criteria)) {
        Iterator<NGTriggerEntity> triggersToResetIterator = stream.iterator();
        while (triggersToResetIterator.hasNext()) {
          triggersToReset.add(triggersToResetIterator.next());
          if (triggersToReset.size() >= RESET_POLLING_TRIGGERS_BATCH_SIZE) {
            bulkDeletePollingDocsAndRegisterPollingAsync(triggersToReset, isParentIdQueryingEnabled, accountIdentifier);
            triggersToReset.clear();
          }
        }
        if (isNotEmpty(triggersToReset)) {
          bulkDeletePollingDocsAndRegisterPollingAsync(triggersToReset, isParentIdQueryingEnabled, accountIdentifier);
        }
      }
    } catch (Exception exception) {
      String msg = "Reset polling triggers request failed " + exception;
      throw new InvalidRequestException(msg);
    }
  }

  private void bulkDeletePollingDocsAndRegisterPollingAsync(
      List<NGTriggerEntity> triggers, boolean isParentIdQueryingEnabled, String accountIdentifier) throws Exception {
    /* This method is used for resetting a set of polling triggers (ARTIFACT or MANIFEST types).
       It will delete the polling docs and perpetual tasks for these triggers and re-create them by calling the
       `registerPollingAsync` method for each one of them. */
    List<String> pollingDocIdsToDelete = getPollingDocIds(triggers);
    if (isEmpty(pollingDocIdsToDelete)) {
      return;
    }
    log.info("Resetting triggers {} with pollingDocIds {}",
        triggers.stream().map(TriggerHelper::getTriggerRef).collect(Collectors.toList()), pollingDocIdsToDelete);
    Long deletedPollingDocs =
        NGRestUtils.getGeneralResponse(pollingResourceClient.unsubscribeBulk(pollingDocIdsToDelete));
    if (deletedPollingDocs != null && deletedPollingDocs < pollingDocIdsToDelete.size()) {
      // Do not throw exception here - it can be the case that these pollingDocs were already deleted in
      // a previous attempt.
      log.warn("In resetting polling triggers, failed to delete {} in {} pollingDocIds within {}", deletedPollingDocs,
          pollingDocIdsToDelete.size(), pollingDocIdsToDelete);
    }
    Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap = null;
    if (isParentIdQueryingEnabled) {
      List<String> parentUniqueIds =
          triggers.stream().map(triggerDetails -> triggerDetails.getParentUniqueId()).collect(Collectors.toList());
      parentUniqueIdToScopeInfoMap = scopeResolutionHelper.getScopeInfos(accountIdentifier, parentUniqueIds);
    }
    for (NGTriggerEntity trigger : triggers) {
      ScopeInfo scopeInfo = isParentIdQueryingEnabled
          ? parentUniqueIdToScopeInfoMap.getOrDefault(trigger.getParentUniqueId(), Optional.empty()).orElse(null)
          : null;
      registerPollingAsync(trigger, false, true, scopeInfo, isParentIdQueryingEnabled);
    }
    log.info("Successful reset of {} triggers with {} pollingDocIds", triggers.size(), pollingDocIdsToDelete.size());
  }

  private List<String> getPollingDocIds(List<NGTriggerEntity> triggers) {
    // Returns the deduplicated list of pollingDocIds, given a list of polling triggers (ARTIFACT or MANIFEST type).
    return triggers.stream()
        .map(trigger -> {
          if (trigger.getMetadata() != null && trigger.getMetadata().getBuildMetadata() != null
              && trigger.getMetadata().getBuildMetadata().getPollingConfig() != null) {
            return trigger.getMetadata().getBuildMetadata().getPollingConfig().getPollingDocId();
          } else {
            return null;
          }
        })
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  public TriggerUpdateCount updateBranchName(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String targetIdentifier, GitMoveOperationType operationType, String pipelineBranchName, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    Optional<List<NGTriggerEntity>> listOfTriggers = isParentIdQueryingEnabled
        ? ngTriggerRepository.findByParentUniqueIdAndTargetIdentifier(scopeInfo.getUniqueId(), targetIdentifier)
        : ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifier(
              accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier);
    List<NGTriggerEntity> listOfUpdatedTriggers = new ArrayList<>();
    long failedYamlUpdateCount = 0;
    if (listOfTriggers.isPresent()) {
      for (NGTriggerEntity triggerEntity : listOfTriggers.get()) {
        try {
          YamlField yamlField = YamlUtils.readTree(triggerEntity.getYaml());
          YamlNode triggerNode = yamlField.getNode().getField("trigger").getNode();
          if (Objects.equals(operationType, GitMoveOperationType.INLINE_TO_REMOTE)) {
            ((ObjectNode) triggerNode.getCurrJsonNode()).put(PIPELINE_BRANCH_NAME, pipelineBranchName);
          } else if (Objects.equals(operationType, GitMoveOperationType.REMOTE_TO_INLINE)) {
            ((ObjectNode) triggerNode.getCurrJsonNode()).remove(PIPELINE_BRANCH_NAME);
          }
          String updateYml = YamlUtils.writeYamlString(yamlField);
          triggerEntity.setYaml(updateYml);
          listOfUpdatedTriggers.add(triggerEntity);
        } catch (Exception e) {
          failedYamlUpdateCount++;
          log.error(
              "Error performing updateBranchName operation on trigger: " + TriggerHelper.getTriggerRef(triggerEntity),
              e);
        }
      }

      Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap = null;
      if (isParentIdQueryingEnabled) {
        List<String> parentUniqueIds = listOfUpdatedTriggers.stream()
                                           .map(triggerEntity -> triggerEntity.getParentUniqueId())
                                           .collect(Collectors.toList());
        parentUniqueIdToScopeInfoMap = scopeResolutionHelper.getScopeInfos(accountIdentifier, parentUniqueIds);
      }

      TriggerUpdateCount updateTriggerYamlResult = ngTriggerRepository.updateTriggerYaml(
          listOfUpdatedTriggers, parentUniqueIdToScopeInfoMap, isParentIdQueryingEnabled);
      return TriggerUpdateCount.builder()
          .failureCount(updateTriggerYamlResult.getFailureCount() + failedYamlUpdateCount)
          .successCount(updateTriggerYamlResult.getSuccessCount())
          .build();
    } else {
      log.info("No non-deleted Trigger found to update pipelineBranchName");
      return TriggerUpdateCount.builder().successCount(0).failureCount(0).build();
    }
  }

  @Override
  public BulkTriggersResponseDTO toggleTriggersInBulk(
      String accountIdentifier, BulkTriggersRequestDTO bulkTriggersRequestDTO) {
    String orgIdentifier = null;
    String projectIdentifier = null;
    String pipelineIdentifier = null;
    String type = null;
    boolean enable = false;

    // Filters and Data from the RequestBody
    if (bulkTriggersRequestDTO.getFilters() != null) {
      orgIdentifier = bulkTriggersRequestDTO.getFilters().getOrgIdentifier();
      projectIdentifier = bulkTriggersRequestDTO.getFilters().getProjectIdentifier();
      pipelineIdentifier = bulkTriggersRequestDTO.getFilters().getPipelineIdentifier();
      type = bulkTriggersRequestDTO.getFilters().getType();
    }
    if (bulkTriggersRequestDTO.getData() != null) {
      enable = bulkTriggersRequestDTO.getData().isEnable();
    }

    return toggleTriggers(enable, accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, type,
        scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
  }

  @Override
  public String fetchExecutionURL(String accountId, String orgId, String projectId, String pipelineId,
      String planExecutionId, List<String> modules) {
    return NGRestUtils.getResponse(
        pipelineServiceClient.getExecutionURL(accountId, orgId, projectId, pipelineId, planExecutionId, modules));
  }

  @Override
  public long count(String accountIdentifier) {
    return ngTriggerRepository.count(accountIdentifier);
  }

  @Override
  public List<NGTriggerEntity> findTriggersForHarnessArtifactRegistryByAccountIdAndRegistry(
      String accountId, String registryName, String action) {
    Page<NGTriggerEntity> triggersPage = list(
        TriggerFilterHelper.createCriteriaFormWebhookTriggerGetListRegistryName(accountId, registryName, action, EMPTY),
        Pageable.unpaged());

    return triggersPage.get().collect(Collectors.toList());
  }

  private List<BulkTriggerDetailDTO> toBulkTriggerDetails(
      List<NGTriggerEntity> triggerEntities, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    List<BulkTriggerDetailDTO> bulkTriggerDetails = new ArrayList<>();

    for (NGTriggerEntity trigger : triggerEntities) {
      BulkTriggerDetailDTO bulkTriggerDetailDTO =
          BulkTriggerDetailDTO.builder()
              .accountIdentifier(trigger.getAccountId())
              .orgIdentifier(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : trigger.getOrgIdentifier())
              .projectIdentifier(
                  isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : trigger.getProjectIdentifier())
              .pipelineIdentifier(trigger.getTargetIdentifier())
              .triggerIdentifier(trigger.getIdentifier())
              .type(trigger.getType())
              .build();

      bulkTriggerDetails.add(bulkTriggerDetailDTO);
    }

    return bulkTriggerDetails;
  }

  public boolean checkIfShouldSubscribePolling(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    switch (ngTriggerEntity.getType()) {
      case MANIFEST:
      case ARTIFACT:
      case MULTI_REGION_ARTIFACT:
        return ngTriggerEntity.getEnabled();
      case WEBHOOK:
        if (!ngTriggerEntity.getEnabled()) {
          return false;
        }
        if (isWebhookGitPollingEnabled(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled)
            && NGTimeConversionHelper.convertTimeStringToMinutesZeroAllowed(ngTriggerEntity.getPollInterval())
                == WEBHOOK_POLLING_UNSUBSCRIBE) {
          return false;
        }
        return true;
      default:
        return false;
    }
  }

  public boolean checkIfShouldUnsubscribePolling(
      NGTriggerEntity ngTriggerEntity, boolean isUpdate, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    switch (ngTriggerEntity.getType()) {
      case MANIFEST:
      case ARTIFACT:
      case MULTI_REGION_ARTIFACT:
        return !ngTriggerEntity.getEnabled() || isUpdate;
      case WEBHOOK:
        if (!ngTriggerEntity.getEnabled() || isUpdate) {
          return true;
        }
        return isWebhookGitPollingEnabled(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled)
            && NGTimeConversionHelper.convertTimeStringToMinutesZeroAllowed(ngTriggerEntity.getPollInterval())
            == WEBHOOK_POLLING_UNSUBSCRIBE;
      default:
        return false;
    }
  }

  public List<PollingItem> getPollingItemsToUnsubscribe(NGTriggerEntity ngTriggerEntity, List<PollingItem> pollingItems,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (ngTriggerEntity.getType() == MULTI_REGION_ARTIFACT) {
      /* MultiRegionArtifact triggers need different handling. Because the number of artifacts we are listening to
      could have changed during an update, we generate pollingItems to unsubscribe
      from the `ngTriggerEntity.metadata.signatures` list. */
      return pollingSubscriptionHelper.generateMultiArtifactPollingItemsToUnsubscribe(
          ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    }
    return pollingItems;
  }

  private void validateUniqueIdAndParentUniqueId(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (isEmpty(ngTriggerEntity.getUniqueId())) {
      ngTriggerEntity.setUniqueId(generateUuid());
    }
    if (isEmpty(ngTriggerEntity.getParentUniqueId())) {
      if (isParentIdQueryingEnabled) {
        ngTriggerEntity.setParentUniqueId(scopeInfo.getUniqueId());
        return;
      }
      Optional<ScopeInfo> scopeInfoOptional = scopeResolutionHelper.getScopeInfoOptional(
          ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier());
      if (scopeInfoOptional.isPresent()) {
        ngTriggerEntity.setParentUniqueId(scopeInfoOptional.get().getUniqueId());
      }
    }
  }

  private void validateExecutorAndRecordMetric(String accountId, TriggerExecutorDTO executorInfo, Runnable validation) {
    String requestedExecutorType =
        executorInfo != null && executorInfo.getType() != null ? executorInfo.getType().name() : "NONE";
    try {
      validation.run();
      recordExecutorCrudMetric(accountId, requestedExecutorType, "SUCCESS");
    } catch (NGAccessDeniedException e) {
      recordExecutorCrudMetric(accountId, requestedExecutorType, "PERMISSION_DENIED");
      throw e;
    } catch (EntityNotFoundException e) {
      recordExecutorCrudMetric(accountId, requestedExecutorType, "ENTITY_NOT_FOUND");
      throw e;
    } catch (InvalidArgumentsException e) {
      recordExecutorCrudMetric(accountId, requestedExecutorType, "INVALID_ARGS");
      throw e;
    } catch (Exception e) {
      recordExecutorCrudMetric(accountId, requestedExecutorType, "ERROR");
      throw e;
    }
  }

  private void recordExecutorCrudMetric(String accountId, String executorType, String result) {
    try (PmsMetricContextGuard ctx = new PmsMetricContextGuard(ImmutableMap.of(PmsEventMonitoringConstants.ACCOUNT_ID,
             accountId, PmsEventMonitoringConstants.EXECUTOR_TYPE, executorType, PmsEventMonitoringConstants.RESULT,
             result))) {
      metricService.incCounter(TRIGGER_EXECUTOR_VALIDATION_CREATE_UPDATE_COUNT);
    } catch (Exception e) {
      log.warn("Failed to record executor crud metric", e);
    }
  }
}

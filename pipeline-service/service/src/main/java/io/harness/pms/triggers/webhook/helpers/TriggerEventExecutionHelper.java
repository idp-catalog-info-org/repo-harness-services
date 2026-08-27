/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.constants.Constants.X_EVENT_BRIDGE_TRIGGER;
import static io.harness.constants.Constants.X_HARNESS_SYSTEM_EVENT_TRIGGER;
import static io.harness.constants.Constants.X_HUB_SIGNATURE_256;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.ngtriggers.Constants.MANDATE_GITHUB_AUTHENTICATION_TRUE_VALUE;
import static io.harness.ngtriggers.Constants.TRIGGERS_MANDATE_GITHUB_AUTHENTICATION;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.EXCEPTION_WHILE_PROCESSING;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_RUNTIME_INPUT_YAML;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.MERGE_QUEUE_CHECKS_ALREADY_RUNNING;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.MERGE_QUEUE_CHECKS_CANCELED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.SKIPPED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_AUTHENTICATION_FAILED;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.AWS_CODECOMMIT;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.AZURE;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.BITBUCKET;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.CUSTOM;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.GITHUB;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.GITLAB;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.HARNESS;
import static io.harness.pms.contracts.triggers.Type.WEBHOOK;

import static java.util.stream.Collectors.toList;

import io.harness.NgAutoLogContext;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.ScopeInfo;
import io.harness.beans.WebhookEncryptedSecretDTO;
import io.harness.delegate.beans.gitapi.GitRepoType;
import io.harness.delegate.beans.trigger.TriggerAuthenticationTaskParams;
import io.harness.delegate.beans.trigger.TriggerAuthenticationTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.eventsframework.webhookpayloads.webhookdata.EventHeader;
import io.harness.eventsframework.webhookpayloads.webhookdata.TriggerExecutionDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.PersistentLockException;
import io.harness.execution.PlanExecution;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.WebhookSecretData;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData.TriggerNotificationDataBuilder;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult.WebhookEventProcessingResultBuilder;
import io.harness.ngtriggers.beans.entity.GitRepoDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookAutoRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookRegistrationStatus;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.response.TriggerEventStatus;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.webhook.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.eventmapper.impl.WebhookEventMapperHelper;
import io.harness.ngtriggers.helpers.ArtifactConfigHelper;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.instrumentation.TriggerTelemetryHelper;
import io.harness.ngtriggers.utils.TaskExecutionUtils;
import io.harness.pms.contracts.triggers.ArtifactData;
import io.harness.pms.contracts.triggers.ManifestData;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.TriggerPayload.Builder;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.triggers.TriggerExecutionHelper;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.polling.contracts.PollingResponse;
import io.harness.product.ci.scm.proto.Action;
import io.harness.product.ci.scm.proto.MergeQueueHook;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.secretmanagerclient.dto.config.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.tracing.TracingUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import software.wings.beans.TaskType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.StopWatch;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerEventExecutionHelper {
  private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject @Named("PRIVILEGED") private SecretManagerClientService ngSecretService;
  @Inject private PMSPipelineRepository pmsPipelineRepository;
  @Inject private PMSPipelineServiceHelper pmsPipelineServiceHelper;
  private TaskExecutionUtils taskExecutionUtils;
  private final NGSettingsClient settingsClient;
  private final NGTriggerRepository ngTriggerRepository;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final TriggerExecutionHelper triggerExecutionHelper;
  private final WebhookEventMapperHelper webhookEventMapperHelper;
  private final TriggerWebhookEventPublisher triggerWebhookEventPublisher;
  private final TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  private final TriggerTelemetryHelper triggerTelemetryHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final PersistentLocker persistentLocker;
  @Inject @Named("TriggerAuthenticationExecutorService") ExecutorService triggerAuthenticationExecutor;
  private final MetricService metricService;
  private static final String EVENT_ID = "eventId";
  public static final Tracer tracer =
      GlobalOpenTelemetry.getTracer("io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper");
  private static final String TRIGGER_ACTIVATION_AUTHENTICATION_PROCESS_TIME =
      "trigger_activation_authentication_process_time";
  private static final String TRIGGER_ACTIVATION_TIME = "trigger_activation_time";
  private static final String TRIGGER_ACTIVATION_TRIGGER_EXECUTION_V1_PROCESS_TIME =
      "trigger_activation_trigger_execution_v1_process_time";
  private static final String MERGE_QUEUE_EXECUTION_LOCK_PREFIX = "mergeQueueExecution/";
  private static final Duration MERGE_QUEUE_EXECUTION_LOCK_TIMEOUT = Duration.ofMinutes(5);
  private static final Duration MERGE_QUEUE_EXECUTION_LOCK_WAIT_TIMEOUT = Duration.ofSeconds(30);

  public WebhookEventProcessingResult handleTriggerWebhookEvent(
      TriggerMappingRequestData mappingRequestData, TriggerNotificationDataBuilder triggerNotificationDataBuilder) {
    try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext(EVENT_ID,
             mappingRequestData.getWebhookDTO() == null ? null : mappingRequestData.getWebhookDTO().getEventId(),
             mappingRequestData.getTriggerWebhookEvent().getTriggerIdentifier(),
             mappingRequestData.getTriggerWebhookEvent().getPipelineIdentifier(),
             mappingRequestData.getTriggerWebhookEvent().getProjectIdentifier(),
             mappingRequestData.getTriggerWebhookEvent().getOrgIdentifier(),
             mappingRequestData.getTriggerWebhookEvent().getAccountId(),
             AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      WebhookEventMappingResponse webhookEventMappingResponse =
          webhookEventMapperHelper.mapWebhookEventToTriggers(mappingRequestData);

      TriggerWebhookEvent triggerWebhookEvent = mappingRequestData.getTriggerWebhookEvent();
      WebhookEventProcessingResultBuilder resultBuilder = WebhookEventProcessingResult.builder();
      List<TriggerEventResponse> eventResponses = new ArrayList<>();
      if (mappingRequestData.getWebhookDTO() != null
          && pmsFeatureFlagService.isEnabled(
              mappingRequestData.getWebhookDTO().getAccountId(), FeatureName.CDS_YAML_SIMPLIFICATION)) {
        processTriggerV1(mappingRequestData.getWebhookDTO(), triggerWebhookEvent);
      }
      if (webhookEventMappingResponse.isFailedToFindTrigger()) {
        resultBuilder.mappedToTriggers(false);
        eventResponses.add(webhookEventMappingResponse.getWebhookEventResponse());
      } else {
        authenticateTriggers(triggerWebhookEvent, webhookEventMappingResponse);
        log.info("Preparing for pipeline execution request");
        resultBuilder.mappedToTriggers(true);
        if (isNotEmpty(webhookEventMappingResponse.getTriggers())) {
          if (!pmsFeatureFlagService.isEnabled(
                  triggerWebhookEvent.getAccountId(), FeatureName.PIPE_INVERTED_FF_FOR_TRIGGER_PROCESSING)) {
            processTriggers(mappingRequestData, webhookEventMappingResponse, triggerWebhookEvent, eventResponses,
                triggerNotificationDataBuilder);
          } else {
            // Fallback to existing code when feature flag is enabled
            for (TriggerDetails triggerDetails : webhookEventMappingResponse.getTriggers()) {
              NGTriggerEntity triggerEntity = triggerDetails.getNgTriggerEntity();
              try (TracingUtils.TracingContext tracingContext = triggerEntity != null
                      ? TriggerExecutionHelper.generateTraceIdAndStartSpan(tracer, triggerEntity)
                      : null) {
                if (triggerEntity == null) {
                  log.error("Trigger Entity is empty, This should not happen, please check");
                  continue;
                }
                boolean isParentIdQueryingEnabled = true;
                ScopeInfo scopeInfo = isParentIdQueryingEnabled
                    ? scopeResolutionHelper.getScopeInfo(triggerDetails.getNgTriggerEntity().getAccountId(),
                          triggerDetails.getNgTriggerEntity().getParentUniqueId())
                    : null;
                if (triggerDetails.getAuthenticated() != null && !triggerDetails.getAuthenticated()) {
                  eventResponses.add(generateEventHistoryForAuthenticationError(triggerWebhookEvent, triggerDetails,
                      triggerDetails.getNgTriggerEntity(), scopeInfo, isParentIdQueryingEnabled));
                  continue;
                }
                // Trigger event will be skipped if ci skip/skip ci/pipeline skip/skip pipeline will be found.
                if (webhookEventMappingResponse.getWebhookEventResponse() != null
                    && webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus() == SKIPPED) {
                  eventResponses.add(TriggerEventResponseHelper.toResponse(SKIPPED, triggerWebhookEvent, null,
                      triggerDetails.getNgTriggerEntity(), "Trigger event was skipped.", null));
                  continue;
                }
                if (mappingRequestData.getWebhookDTO() != null) {
                  // Added condition for webhookDTO to be not null as the flow should not go to redis if it comes via V1
                  // flow.
                  WebhookDTO webhookDTO = mappingRequestData.getWebhookDTO();
                  // Set the parsedResponse to use the one after webhookEventMapperHelper.mapWebhookEventToTrigger()
                  // instead of the one received from the mappingRequestData.
                  ParseWebhookResponse parseWebhookResponse = webhookEventMappingResponse.getParseWebhookResponse();
                  if (parseWebhookResponse != null) {
                    webhookDTO = webhookDTO.toBuilder().setParsedResponse(parseWebhookResponse).build();
                  }
                  TriggerExecutionDTO triggerExecutionDTO =
                      TriggerExecutionDTO.newBuilder()
                          .setWebhookDto(webhookDTO)
                          .setAccountId(triggerDetails.getNgTriggerEntity().getAccountId())
                          .setOrgIdentifier(isParentIdQueryingEnabled
                                  ? scopeInfo.getOrgIdentifier()
                                  : triggerDetails.getNgTriggerEntity().getOrgIdentifier())
                          .setProjectIdentifier(isParentIdQueryingEnabled
                                  ? scopeInfo.getProjectIdentifier()
                                  : triggerDetails.getNgTriggerEntity().getProjectIdentifier())
                          .setParentUniqueId(emptyIfNull(triggerDetails.getNgTriggerEntity().getParentUniqueId()))
                          .setTargetIdentifier(triggerDetails.getNgTriggerEntity().getTargetIdentifier())
                          .setTriggerIdentifier(triggerDetails.getNgTriggerEntity().getIdentifier())
                          .setAuthenticated(triggerDetails.getAuthenticated() != null
                                  ? triggerDetails.getAuthenticated()
                                  : Boolean.TRUE)
                          .addAllChangedFiles(webhookEventMappingResponse.getChangedFiles())
                          .build();
                  triggerWebhookEventPublisher.publishTriggerWebhookEvent(triggerExecutionDTO);
                } else {
                  updateWebhookRegistrationStatusAndTriggerPipelineExecution(
                      webhookEventMappingResponse.getParseWebhookResponse(), triggerWebhookEvent, eventResponses,
                      triggerDetails, webhookEventMappingResponse.getChangedFiles(), triggerNotificationDataBuilder,
                      true);
                }
              }
            }
          }
        }
      }

      return resultBuilder.responses(eventResponses).build();
    }
  }

  public void validateUniqueIdAndParentUniqueId(TriggerEventHistory triggerEventHistory) {
    if (isEmpty(triggerEventHistory.getUniqueId())) {
      triggerEventHistory.setUniqueId(generateUuid());
    }
    if (isEmpty(triggerEventHistory.getParentUniqueId())) {
      Optional<ScopeInfo> scopeInfo = scopeResolutionHelper.getScopeInfoOptional(triggerEventHistory.getAccountId(),
          triggerEventHistory.getOrgIdentifier(), triggerEventHistory.getProjectIdentifier());
      String parentUniqueId = null;
      if (scopeInfo.isPresent()) {
        parentUniqueId = scopeInfo.get().getUniqueId();
      } else {
        log.warn("Parent unique id not found for trigger event history with accountId {} , orgId {}, projectId {}",
            triggerEventHistory.getAccountId(), triggerEventHistory.getOrgIdentifier(),
            triggerEventHistory.getProjectIdentifier());
      }
      triggerEventHistory.setParentUniqueId(parentUniqueId);
    }
  }

  public void updateWebhookRegistrationStatusAndTriggerPipelineExecution(ParseWebhookResponse parseWebhookResponse,
      TriggerWebhookEvent triggerWebhookEvent, List<TriggerEventResponse> eventResponses, TriggerDetails triggerDetails,
      Set<String> filesChanged, TriggerNotificationDataBuilder triggerNotificationDataBuilder,
      boolean isParentIdQueryingEnabled) {
    long yamlVersion = triggerDetails.getNgTriggerEntity().getYmlVersion() == null
        ? 3
        : triggerDetails.getNgTriggerEntity().getYmlVersion();
    NGTriggerEntity triggerEntity = triggerDetails.getNgTriggerEntity();
    Criteria criteria = new Criteria();
    if (isParentIdQueryingEnabled) {
      criteria.and(NGTriggerEntityKeys.parentUniqueId).is(triggerEntity.getParentUniqueId());
    } else {
      criteria.and(NGTriggerEntityKeys.accountId).is(triggerEntity.getAccountId());
      criteria.and(NGTriggerEntityKeys.orgIdentifier).is(triggerEntity.getOrgIdentifier());
      criteria.and(NGTriggerEntityKeys.projectIdentifier).is(triggerEntity.getProjectIdentifier());
    }
    criteria.and(NGTriggerEntityKeys.targetIdentifier).is(triggerEntity.getTargetIdentifier());
    criteria.and(NGTriggerEntityKeys.identifier).is(triggerEntity.getIdentifier());
    if (triggerEntity.getVersion() != null) {
      criteria.and(NGTriggerEntityKeys.version).is(triggerEntity.getVersion());
    }
    try {
      TriggerHelper.stampWebhookRegistrationInfo(triggerEntity,
          WebhookAutoRegistrationStatus.builder().registrationResult(WebhookRegistrationStatus.SUCCESS).build());
    } catch (Exception ex) {
      log.error("Webhook registration status update failed", ex);
    }
    ngTriggerRepository.updateValidationStatus(criteria, triggerEntity);
    List<HeaderConfig> headerConfigList = triggerWebhookEvent.getHeaders();
    if (triggerNotificationDataBuilder != null) {
      triggerNotificationDataBuilder.headerConfigs(headerConfigList);
      triggerNotificationDataBuilder.payload(triggerWebhookEvent.getPayload());
    }
    String connectorRef = null;
    if (triggerDetails.getNgTriggerConfigV2() != null && triggerDetails.getNgTriggerConfigV2().getSource() != null
        && triggerDetails.getNgTriggerConfigV2().getSource().getSpec() instanceof WebhookTriggerConfigV2) {
      WebhookTriggerConfigV2 webhookTriggerConfigV2 =
          (WebhookTriggerConfigV2) triggerDetails.getNgTriggerConfigV2().getSource().getSpec();
      if (webhookTriggerConfigV2.getSpec() != null && webhookTriggerConfigV2.getSpec().fetchGitAware() != null) {
        connectorRef = webhookTriggerConfigV2.getSpec().fetchGitAware().fetchConnectorRef();
      }
    }
    eventResponses.add(triggerPipelineExecution(triggerWebhookEvent, triggerDetails,
        getTriggerPayloadForWebhookTrigger(parseWebhookResponse, triggerWebhookEvent, yamlVersion, connectorRef,
            filesChanged, triggerNotificationDataBuilder),
        triggerWebhookEvent.getPayload(), headerConfigList));
  }

  @VisibleForTesting
  TriggerPayload getTriggerPayloadForWebhookTrigger(ParseWebhookResponse parseWebhookResponse,
      TriggerWebhookEvent triggerWebhookEvent, long version, String connectorRef, Set<String> filesChanged,
      TriggerNotificationDataBuilder triggerNotificationDataBuilder) {
    Builder builder = TriggerPayload.newBuilder().setType(Type.WEBHOOK);

    if (CUSTOM.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.CUSTOM_REPO);
    } else if (GITHUB.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.GITHUB_REPO);
    } else if (AZURE.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.AZURE_REPO);
    } else if (GITLAB.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.GITLAB_REPO);
    } else if (BITBUCKET.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.BITBUCKET_REPO);
    } else if (AWS_CODECOMMIT.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.BITBUCKET_REPO);
    } else if (HARNESS.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.HARNESS_REPO);
    }

    if (parseWebhookResponse != null) {
      if (parseWebhookResponse.hasRelease()) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setRelease(parseWebhookResponse.getRelease()).build())
            .build();
      } else if (parseWebhookResponse.hasBranch()
          && Action.DELETE.equals(parseWebhookResponse.getBranch().getAction())) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setBranch(parseWebhookResponse.getBranch()).build())
            .build();
      } else if (parseWebhookResponse.hasTag() && Action.DELETE.equals(parseWebhookResponse.getTag().getAction())) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setTag(parseWebhookResponse.getTag()).build()).build();
      } else if (parseWebhookResponse.hasBranch()
          && Action.CREATE.equals(parseWebhookResponse.getBranch().getAction())) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setBranch(parseWebhookResponse.getBranch()).build())
            .build();
      } else if (parseWebhookResponse.hasTag() && Action.CREATE.equals(parseWebhookResponse.getTag().getAction())) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setTag(parseWebhookResponse.getTag()).build()).build();
      } else if (parseWebhookResponse.hasPr()) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setPr(parseWebhookResponse.getPr()).build()).build();
      } else if (parseWebhookResponse.hasMergeQueue()) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setMergeQueue(parseWebhookResponse.getMergeQueue()).build())
            .build();
      } else {
        builder.setParsedPayload(ParsedPayload.newBuilder().setPush(parseWebhookResponse.getPush()).build()).build();
      }
    }
    builder.setVersion(version);
    if (isNotEmpty(connectorRef)) {
      builder.setConnectorRef(connectorRef);
    }
    if (isNotEmpty(filesChanged)) {
      builder.addAllChangedFiles(filesChanged);
    }
    TriggerPayload triggerPayload = builder.setType(WEBHOOK).build();
    if (triggerNotificationDataBuilder != null) {
      triggerNotificationDataBuilder.triggerPayload(triggerPayload);
    }
    return triggerPayload;
  }

  public void triggerPipelineExecutionForV1(
      List<PipelineEntity> pipelineEntities, TriggerWebhookEvent triggerWebhookEvent) {
    triggerExecutionHelper.createPlanExecutionForV1Triggers(pipelineEntities, triggerWebhookEvent);
  }

  private TriggerEventResponse triggerPipelineExecution(TriggerWebhookEvent triggerWebhookEvent,
      TriggerDetails triggerDetails, TriggerPayload triggerPayload, String payload, List<HeaderConfig> header) {
    StopWatch triggerActivationWatch = new StopWatch();
    triggerActivationWatch.start();
    String runtimeInputYaml = null;
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo = isParentIdQueryingEnabled
        ? scopeResolutionHelper.getScopeInfo(ngTriggerEntity.getAccountId(), ngTriggerEntity.getParentUniqueId())
        : null;
    try (AutoLogContext ignore = new NgTriggerAutoLogContext(EVENT_ID, triggerWebhookEvent.getUuid(),
             ngTriggerEntity.getIdentifier(), ngTriggerEntity.getTargetIdentifier(),
             isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
             isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
             ngTriggerEntity.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      try {
        // Every merge queue path funnels through this method, including the legacy inline loop and the async
        // consumer, both of which skip processSingleTrigger's cancel handling. Without this guard a cancel on those
        // paths would fall through and start a build for a commit that was already dequeued.
        if (isMergeQueueChecksCancel(triggerPayload)) {
          return abortForMergeQueueChecksCanceled(triggerWebhookEvent, triggerDetails,
              triggerPayload.getParsedPayload().getMergeQueue(), scopeInfo, isParentIdQueryingEnabled);
        }
        List<String> inputSetRefs = triggerExecutionHelper.getInputSetRefs(
            triggerDetails.getNgTriggerConfigV2().getInputSetRefs(), triggerWebhookEvent);
        if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName()) && isEmpty(inputSetRefs)) {
          runtimeInputYaml = triggerDetails.getNgTriggerConfigV2().getInputYaml();
        } else {
          SecurityContextBuilder.setContext(
              new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
          SourcePrincipalContextBuilder.setSourcePrincipal(
              new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
          if (pmsFeatureFlagService.isEnabled(triggerWebhookEvent.getAccountId(),
                  FeatureName.CDS_USE_EXECUTION_TRIGGER_PAYLOAD_TO_EVALUATE_BRANCH_EXPRESSION)) {
            runtimeInputYaml = triggerExecutionHelper.fetchInputSetYAML(triggerDetails, triggerWebhookEvent,
                inputSetRefs, triggerPayload, scopeInfo, isParentIdQueryingEnabled);
          } else {
            runtimeInputYaml = triggerExecutionHelper.fetchInputSetYAML(
                triggerDetails, triggerWebhookEvent, inputSetRefs, null, scopeInfo, isParentIdQueryingEnabled);
          }
        }
        MergeQueueExecutionClaim claim = isMergeQueueChecksRequest(triggerPayload)
            ? claimMergeQueueExecution(triggerDetails, triggerPayload, scopeInfo, isParentIdQueryingEnabled)
            : MergeQueueExecutionClaim.notApplicable();
        if (claim.isHeldByAnotherReplica()) {
          return mergeQueueChecksAlreadyRunningResponse(triggerWebhookEvent, ngTriggerEntity);
        }
        PlanExecution response;
        try (AcquiredLock<?> lock = claim.getLock()) {
          if (isDuplicateMergeQueueChecksRequest(
                  triggerDetails, triggerPayload, scopeInfo, isParentIdQueryingEnabled)) {
            return mergeQueueChecksAlreadyRunningResponse(triggerWebhookEvent, ngTriggerEntity);
          }
          response = triggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionRequest(triggerDetails, triggerPayload,
              triggerWebhookEvent, payload, header, runtimeInputYaml, scopeInfo, isParentIdQueryingEnabled);
        }
        triggerActivationWatch.stop();
        recordTriggerActivationTime(triggerActivationWatch, ngTriggerEntity);
        return generateEventHistoryForSuccess(triggerDetails, runtimeInputYaml, ngTriggerEntity, triggerWebhookEvent,
            response, null, null, scopeInfo, isParentIdQueryingEnabled);
      } catch (Exception e) {
        return generateEventHistoryForError(triggerWebhookEvent, triggerDetails, runtimeInputYaml, ngTriggerEntity, e,
            null, null, scopeInfo, isParentIdQueryingEnabled);
      }
    }
  }

  /**
   * @return true when this is a merge queue checks_requested event whose speculative commit already has an execution
   *     in flight, i.e. a redelivery of an event that was already acted on. Cancels are handled before this point and
   *     other event types are unaffected.
   */
  private boolean isDuplicateMergeQueueChecksRequest(TriggerDetails triggerDetails, TriggerPayload triggerPayload,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (!isMergeQueueChecksRequest(triggerPayload)) {
      return false;
    }
    MergeQueueHook mergeQueueHook = triggerPayload.getParsedPayload().getMergeQueue();
    return triggerExecutionHelper.hasUnterminatedExecutionForMergeQueue(
        triggerDetails, mergeQueueHook, scopeInfo, isParentIdQueryingEnabled);
  }

  private boolean isMergeQueueChecksRequest(TriggerPayload triggerPayload) {
    return triggerPayload != null && triggerPayload.hasParsedPayload()
        && triggerPayload.getParsedPayload().hasMergeQueue()
        && Action.CHECKS_REQUESTED.equals(triggerPayload.getParsedPayload().getMergeQueue().getAction());
  }

  private boolean isMergeQueueChecksCancel(TriggerPayload triggerPayload) {
    return triggerPayload != null && triggerPayload.hasParsedPayload()
        && triggerPayload.getParsedPayload().hasMergeQueue()
        && Action.CHECKS_CANCELED.equals(triggerPayload.getParsedPayload().getMergeQueue().getAction());
  }

  /**
   * Shared by processSingleTrigger's fast path and the choke point in triggerPipelineExecution. Aborting is
   * idempotent, so a second call simply finds nothing left to abort.
   */
  private TriggerEventResponse abortForMergeQueueChecksCanceled(TriggerWebhookEvent triggerWebhookEvent,
      TriggerDetails triggerDetails, MergeQueueHook mergeQueueHook, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    int aborted = triggerExecutionHelper.abortExecutionsForMergeQueueCancel(
        triggerDetails, mergeQueueHook, scopeInfo, isParentIdQueryingEnabled);
    log.info("Merge queue checks canceled: aborted {} execution(s) for trigger {}", aborted,
        triggerDetails.getNgTriggerEntity().getIdentifier());
    return TriggerEventResponseHelper.toResponse(MERGE_QUEUE_CHECKS_CANCELED, triggerWebhookEvent, null,
        triggerDetails.getNgTriggerEntity(),
        String.format("Merge queue checks canceled: aborted %d execution(s)", aborted), null);
  }

  /**
   * Claims the exclusive right to start the execution for one merge queue speculative commit.
   *
   * <p>waitToAcquireLockOptional collapses contention and a redis outage into a single null, but the two need opposite
   * responses: contention must suppress the event, an outage must not (or the queue stalls with no ci check at all).
   * waitToAcquireLock separates them - contention throws PersistentLockException, a redis failure is wrapped as
   * UnexpectedException.
   */
  private MergeQueueExecutionClaim claimMergeQueueExecution(TriggerDetails triggerDetails,
      TriggerPayload triggerPayload, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    MergeQueueHook mergeQueueHook = triggerPayload.getParsedPayload().getMergeQueue();
    String executionTag = triggerExecutionHelper.getMergeQueueExecutionTag(
        triggerDetails, mergeQueueHook, scopeInfo, isParentIdQueryingEnabled);
    try {
      return MergeQueueExecutionClaim.acquired(
          persistentLocker.waitToAcquireLock(MERGE_QUEUE_EXECUTION_LOCK_PREFIX + executionTag,
              MERGE_QUEUE_EXECUTION_LOCK_TIMEOUT, MERGE_QUEUE_EXECUTION_LOCK_WAIT_TIMEOUT));
    } catch (PersistentLockException e) {
      // Another replica holds this exact tag and will submit inside its own lock, so this event is a duplicate.
      // A thread interrupt also lands here; suppressing is safe since webhook delivery is at-least-once.
      log.info("Merge queue execution lock for tag {} is held elsewhere; treating this event as a duplicate",
          executionTag, e);
      return MergeQueueExecutionClaim.heldByAnotherReplica();
    } catch (Exception e) {
      // Locker unavailable. Serialization is lost, but the de-dupe read below still suppresses a genuine redelivery
      // once the other execution is persisted, so no build is silently dropped.
      log.error("Could not reach the locker for merge queue execution tag {}; proceeding on the de-dupe check alone",
          executionTag, e);
      return MergeQueueExecutionClaim.lockerUnavailable();
    }
  }

  /**
   * Outcome of {@link #claimMergeQueueExecution}. A null lock runs the body unserialized, which try-with-resources
   * permits.
   */
  @Value
  private static class MergeQueueExecutionClaim {
    AcquiredLock<?> lock;
    boolean heldByAnotherReplica;

    static MergeQueueExecutionClaim acquired(AcquiredLock<?> lock) {
      return new MergeQueueExecutionClaim(lock, false);
    }

    static MergeQueueExecutionClaim heldByAnotherReplica() {
      return new MergeQueueExecutionClaim(null, true);
    }

    static MergeQueueExecutionClaim lockerUnavailable() {
      return new MergeQueueExecutionClaim(null, false);
    }

    static MergeQueueExecutionClaim notApplicable() {
      return new MergeQueueExecutionClaim(null, false);
    }
  }

  private TriggerEventResponse mergeQueueChecksAlreadyRunningResponse(
      TriggerWebhookEvent triggerWebhookEvent, NGTriggerEntity ngTriggerEntity) {
    return TriggerEventResponseHelper.toResponse(MERGE_QUEUE_CHECKS_ALREADY_RUNNING, triggerWebhookEvent, null,
        ngTriggerEntity, MERGE_QUEUE_CHECKS_ALREADY_RUNNING.getMessage(), null);
  }

  public TriggerEventResponse generateEventHistoryForError(TriggerWebhookEvent triggerWebhookEvent,
      TriggerDetails triggerDetails, String runtimeInputYaml, NGTriggerEntity ngTriggerEntity, Exception e,
      String pollingDocId, String build, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String errorMessage = TriggerEventResponseHelper.extractErrorMessage(e);
    log.error(new StringBuilder(512)
                  .append("Exception occurred while requesting pipeline execution using Trigger ")
                  .append(TriggerHelper.getTriggerRef(ngTriggerEntity))
                  .append(". Exception Message: ")
                  .append(errorMessage)
                  .toString(),
        e);

    TargetExecutionSummary targetExecutionSummary = TriggerEventResponseHelper.prepareTargetExecutionSummary(
        (PlanExecution) null, triggerDetails, runtimeInputYaml);

    triggerTelemetryHelper.sendTriggersExecutionEvent(
        ngTriggerEntity, triggerDetails, TriggerEventStatus.FinalResponse.FAILED, scopeInfo, isParentIdQueryingEnabled);

    return TriggerEventResponseHelper.toResponseWithPollingInfo(INVALID_RUNTIME_INPUT_YAML, triggerWebhookEvent, null,
        ngTriggerEntity, triggerDetails.getNgTriggerConfigV2(), errorMessage, targetExecutionSummary, pollingDocId,
        build);
  }

  public TriggerEventResponse generateEventHistoryForAuthenticationError(TriggerWebhookEvent triggerWebhookEvent,
      TriggerDetails triggerDetails, NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    log.warn(String.format("Trigger Authentication for trigger: %s for pipeline: %s for account: %s for project: %s "
            + "for org: %s failed. Please check the delegate logs for TRIGGER_AUTHENTICATION_TASK",
        ngTriggerEntity.getIdentifier(), ngTriggerEntity.getTargetIdentifier(), ngTriggerEntity.getAccountId(),
        isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
        isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier()));

    TargetExecutionSummary targetExecutionSummary =
        TriggerEventResponseHelper.prepareTargetExecutionSummary((PlanExecution) null, triggerDetails, null);
    return TriggerEventResponseHelper.toResponse(TRIGGER_AUTHENTICATION_FAILED, triggerWebhookEvent, null,
        ngTriggerEntity, "Please check if the secret provided for webhook is correct.", targetExecutionSummary);
  }

  public List<TriggerEventResponse> processTriggersForActivation(List<TriggerDetails> mappedTriggers,
      PollingResponse pollingResponse, TriggerNotificationDataBuilder triggerNotificationDataBuilder) {
    List<TriggerEventResponse> responses = new ArrayList<>();
    for (TriggerDetails triggerDetails : mappedTriggers) {
      try {
        responses.add(triggerEventPipelineExecution(triggerDetails, pollingResponse));
        triggerNotificationDataBuilder.triggerPayload(
            buildTriggerPayloadBuilder(triggerDetails, pollingResponse).build());
      } catch (Exception e) {
        NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
        log.error(String.format("Error while requesting pipeline execution for Build Trigger: %s",
                      ngTriggerEntity != null ? TriggerHelper.getTriggerRef(ngTriggerEntity) : "unknown"),
            e);
        if (ngTriggerEntity == null) {
          log.error("NGTriggerEntity is null, cannot create exception response", e);
          continue;
        }
        TriggerWebhookEvent pseudoEvent = TriggerWebhookEvent.builder()
                                              .accountId(ngTriggerEntity.getAccountId())
                                              .createdAt(System.currentTimeMillis())
                                              .build();
        TargetExecutionSummary targetExecutionSummary =
            TriggerEventResponseHelper.prepareTargetExecutionSummary((PlanExecution) null, triggerDetails, null);
        responses.add(TriggerEventResponseHelper.toResponseWithPollingInfo(EXCEPTION_WHILE_PROCESSING, pseudoEvent,
            null, ngTriggerEntity, triggerDetails.getNgTriggerConfigV2(),
            e.getMessage() != null ? e.getMessage() : "Exception while processing trigger", targetExecutionSummary,
            pollingResponse.getPollingDocId(),
            pollingResponse.getBuildInfo().getVersionsCount() > 0 ? pollingResponse.getBuildInfo().getVersions(0)
                                                                  : null));
      }
    }

    return responses;
  }

  public TriggerEventResponse triggerEventPipelineExecution(
      TriggerDetails triggerDetails, PollingResponse pollingResponse) {
    try (TracingUtils.TracingContext tracingContext =
             TriggerExecutionHelper.generateTraceIdAndStartSpan(tracer, triggerDetails.getNgTriggerEntity())) {
      StopWatch triggerActivationWatch = new StopWatch();
      triggerActivationWatch.start();
      String pollingDocId = null;
      String build = null;
      String runtimeInputYaml = null;
      NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
      TriggerWebhookEvent pseudoEvent = TriggerWebhookEvent.builder()
                                            .accountId(ngTriggerEntity.getAccountId())
                                            .createdAt(System.currentTimeMillis())
                                            .build();
      boolean isParentIdQueryingEnabled = true;
      ScopeInfo scopeInfo = isParentIdQueryingEnabled
          ? scopeResolutionHelper.getScopeInfo(ngTriggerEntity.getAccountId(), ngTriggerEntity.getParentUniqueId())
          : null;
      try (AutoLogContext ignore1 = new NgTriggerAutoLogContext("pollingDocumentId", pollingResponse.getPollingDocId(),
               ngTriggerEntity.getIdentifier(), ngTriggerEntity.getTargetIdentifier(),
               isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
               isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
               ngTriggerEntity.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
        pollingDocId = pollingResponse.getPollingDocId();
        TriggerPayload triggerPayload = buildTriggerPayloadBuilder(triggerDetails, pollingResponse).build();
        List<String> inputSetRefs = triggerExecutionHelper.getInputSetRefs(
            triggerDetails.getNgTriggerConfigV2().getInputSetRefs(), pseudoEvent);

        if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName()) && isEmpty(inputSetRefs)) {
          runtimeInputYaml = triggerDetails.getNgTriggerConfigV2().getInputYaml();
        } else {
          SecurityContextBuilder.setContext(
              new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
          SourcePrincipalContextBuilder.setSourcePrincipal(
              new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
          if (pmsFeatureFlagService.isEnabled(ngTriggerEntity.getAccountId(),
                  FeatureName.CDS_USE_EXECUTION_TRIGGER_PAYLOAD_TO_EVALUATE_BRANCH_EXPRESSION)) {
            runtimeInputYaml = triggerExecutionHelper.fetchInputSetYAML(
                triggerDetails, pseudoEvent, inputSetRefs, triggerPayload, scopeInfo, isParentIdQueryingEnabled);
          } else {
            runtimeInputYaml = triggerExecutionHelper.fetchInputSetYAML(
                triggerDetails, pseudoEvent, inputSetRefs, null, scopeInfo, isParentIdQueryingEnabled);
          }
        }
        build = pollingResponse.getBuildInfo().getVersions(0);
        if (triggerPayload != null) {
          pseudoEvent.setPayload(triggerPayload.toString());
        }
        PlanExecution response =
            triggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionRequestForArtifactManifestPollingFlow(
                triggerDetails, triggerPayload, runtimeInputYaml, scopeInfo, isParentIdQueryingEnabled);
        triggerActivationWatch.stop();
        recordTriggerActivationTime(triggerActivationWatch, ngTriggerEntity);
        return generateEventHistoryForSuccess(triggerDetails, runtimeInputYaml, ngTriggerEntity, pseudoEvent, response,
            pollingDocId, build, scopeInfo, isParentIdQueryingEnabled);
      } catch (Exception e) {
        return generateEventHistoryForError(pseudoEvent, triggerDetails, runtimeInputYaml, ngTriggerEntity, e,
            pollingDocId, build, scopeInfo, isParentIdQueryingEnabled);
      }
    }
  }

  public void recordTriggerActivationTime(StopWatch stopWatch, NGTriggerEntity ngTriggerEntity) {
    try (PmsMetricContextGuard metricContext = new PmsMetricContextGuard(
             ImmutableMap.<String, String>builder()
                 .put(PmsEventMonitoringConstants.ACCOUNT_ID, ngTriggerEntity.getAccountId())
                 .put(PmsEventMonitoringConstants.TRIGGER_TYPE, ngTriggerEntity.getType().name())
                 .build())) {
      metricService.recordMetric(TRIGGER_ACTIVATION_TIME, stopWatch.getTotalTimeMillis());
    } catch (Exception e) {
      log.error("Exception while recording trigger activation time", e);
    }
  }

  public Builder buildTriggerPayloadBuilder(TriggerDetails triggerDetails, PollingResponse pollingResponse) {
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    Type buildType = getBuildType(ngTriggerEntity);
    String build = null;

    Builder triggerPayloadBuilder = TriggerPayload.newBuilder().setType(buildType);

    build = pollingResponse.getBuildInfo().getVersions(0);
    if (buildType == Type.ARTIFACT) {
      Map<String, String> metadata = new HashMap<>();
      if (pollingResponse.getBuildInfo().getMetadataCount() != 0) {
        metadata = pollingResponse.getBuildInfo().getMetadata(0).getMetadataMap();
      }
      triggerPayloadBuilder.setArtifactData(ArtifactData.newBuilder().setBuild(build).putAllMetadata(metadata).build());

      // Fetching connectorRef and image path
      if (triggerDetails.getNgTriggerConfigV2() != null && triggerDetails.getNgTriggerConfigV2().getSource() != null
          && triggerDetails.getNgTriggerConfigV2().getSource().getSpec() instanceof ArtifactTriggerConfig) {
        ArtifactTriggerConfig artifactConfig =
            (ArtifactTriggerConfig) triggerDetails.getNgTriggerConfigV2().getSource().getSpec();
        ArtifactConfigHelper.setConnectorAndImage(triggerPayloadBuilder, artifactConfig);
      }

    } else if (buildType == Type.MANIFEST) {
      triggerPayloadBuilder.setManifestData(ManifestData.newBuilder().setVersion(build).build());
    }
    return triggerPayloadBuilder;
  }

  private TriggerEventResponse generateEventHistoryForSuccess(TriggerDetails triggerDetails, String runtimeInputYaml,
      NGTriggerEntity ngTriggerEntity, TriggerWebhookEvent pseudoEvent, PlanExecution response, String pollingDocId,
      String build, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    try (
        AutoLogContext ignore1 = new NgAutoLogContext(
            isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
            isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
            ngTriggerEntity.getAccountId(), isParentIdQueryingEnabled ? scopeInfo.getUniqueId() : null, OVERRIDE_ERROR);
        AutoLogContext ignore2 = new AutoLogContext(
            ImmutableMap.of("planExecutionId", response.getUuid()), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      TargetExecutionSummary targetExecutionSummary =
          TriggerEventResponseHelper.prepareTargetExecutionSummary(response, triggerDetails, runtimeInputYaml);

      log.info(ngTriggerEntity.getTargetType() + " execution was requested successfully for Pipeline: "
          + ngTriggerEntity.getTargetIdentifier() + ", using trigger: " + ngTriggerEntity.getIdentifier());

      triggerTelemetryHelper.sendTriggersExecutionEvent(ngTriggerEntity, triggerDetails,
          TriggerEventStatus.FinalResponse.SUCCESS, scopeInfo, isParentIdQueryingEnabled);
      return TriggerEventResponseHelper.toResponseWithPollingInfo(TARGET_EXECUTION_REQUESTED, pseudoEvent,
          ngTriggerEntity, triggerDetails.getNgTriggerConfigV2(), "Pipeline execution was requested successfully",
          targetExecutionSummary, pollingDocId, build);
    }
  }

  public void authenticateTriggers(
      TriggerWebhookEvent triggerWebhookEvent, WebhookEventMappingResponse webhookEventMappingResponse) {
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    List<TriggerDetails> triggersToAuthenticate =
        getTriggersToAuthenticate(triggerWebhookEvent, webhookEventMappingResponse);
    if (isEmpty(triggersToAuthenticate)) {
      return;
    }
    String hashedPayload = getHashedPayload(triggerWebhookEvent);
    if (hashedPayload == null) {
      for (TriggerDetails triggerDetails : triggersToAuthenticate) {
        triggerDetails.setAuthenticated(false);
      }
      return;
    }
    CompletableFutures<ResponseData> completableFutures = new CompletableFutures<>(triggerAuthenticationExecutor);
    Map<Integer, TriggerDetails> triggerDetailsMap = new HashMap<>();
    int counter = 0;
    for (TriggerDetails triggerDetails : triggersToAuthenticate) {
      try {
        NGTriggerConfigV2 ngTriggerConfigV2 = triggerDetails.getNgTriggerConfigV2();
        NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                           .accountIdentifier(triggerWebhookEvent.getAccountId())
                                           .orgIdentifier(ngTriggerConfigV2.getOrgIdentifier())
                                           .projectIdentifier(ngTriggerConfigV2.getProjectIdentifier())
                                           .build();
        SecretRefData secretRefData =
            SecretRefHelper.createSecretRef(ngTriggerConfigV2.getEncryptedWebhookSecretIdentifier());
        WebhookEncryptedSecretDTO webhookEncryptedSecretDTO =
            WebhookEncryptedSecretDTO.builder().secretRef(secretRefData).build();
        List<EncryptedDataDetail> encryptedDataDetail =
            ngSecretService.getEncryptionDetails(basicNGAccessObject, webhookEncryptedSecretDTO);
        List<WebhookSecretData> webhookSecretData =
            Collections.singletonList(WebhookSecretData.builder()
                                          .webhookEncryptedSecretDTO(webhookEncryptedSecretDTO)
                                          .encryptedDataDetails(encryptedDataDetail)
                                          .build());
        Set<String> taskSelectors =
            getAuthenticationTaskSelectors(basicNGAccessObject, secretRefData, ngTriggerConfigV2.getIdentifier());
        log.info("Authenticating trigger [" + ngTriggerConfigV2.getIdentifier()
            + "] with delegate selectors: " + taskSelectors);
        completableFutures.supplyAsync(
            ()
                -> taskExecutionUtils.executeSyncTask(
                    DelegateTaskRequest.builder()
                        .accountId(triggerWebhookEvent.getAccountId())
                        .executionTimeout(
                            Duration.ofSeconds(60)) // todo: Gather suggestions regarding this timeout value.
                        .taskType(TaskType.TRIGGER_AUTHENTICATION_TASK.toString())
                        .taskSelectors(taskSelectors)
                        .taskSetupAbstractions(buildAbstractions(triggerWebhookEvent.getAccountId(),
                            ngTriggerConfigV2.getOrgIdentifier(), ngTriggerConfigV2.getProjectIdentifier()))
                        .taskParameters(TriggerAuthenticationTaskParams.builder()
                                            .eventPayload(triggerWebhookEvent.getPayload())
                                            .gitRepoType(GitRepoType.GITHUB)
                                            .hashedPayload(hashedPayload)
                                            .webhookSecretData(webhookSecretData)
                                            .build())
                        .build()));
        triggerDetailsMap.put(counter, triggerDetails);
        counter++;
      } catch (Exception e) {
        triggerDetails.setAuthenticated(false);
        log.error("Exception while authenticating trigger with id {}",
            triggerDetails.getNgTriggerEntity().getIdentifier(), e);
      }
    }
    try {
      List<ResponseData> authenticationTaskResponses = completableFutures.allOf().get(2, TimeUnit.MINUTES);
      int index = 0;
      for (ResponseData responseData : authenticationTaskResponses) {
        TriggerDetails triggerDetails = triggerDetailsMap.get(index);
        if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
          BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
          Object object = binaryResponseData.isUsingKryoWithoutReference()
              ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
              : kryoSerializer.asInflatedObject(binaryResponseData.getData());
          if (object instanceof TriggerAuthenticationTaskResponse) {
            triggerDetails.setAuthenticated(
                ((TriggerAuthenticationTaskResponse) object).getTriggersAuthenticationStatus().get(0));
          } else if (object instanceof ErrorResponseData) {
            ErrorResponseData errorResponseData = (ErrorResponseData) object;
            log.error("Failed to authenticate trigger {}. Reason: {}",
                triggerDetails.getNgTriggerEntity().getIdentifier(), errorResponseData.getErrorMessage());
            triggerDetails.setAuthenticated(false);
          }
        }
        index++;
      }
    } catch (Exception e) {
      log.error("Exception while authenticating triggers: ", e);
      for (TriggerDetails triggerDetails : triggersToAuthenticate) {
        triggerDetails.setAuthenticated(false);
      }
    }
    stopWatch.stop();
    try (PmsMetricContextGuard metricContext = new PmsMetricContextGuard(
             ImmutableMap.<String, String>builder()
                 .put(PmsEventMonitoringConstants.ACCOUNT_ID, triggerWebhookEvent.getAccountId())
                 .build())) {
      metricService.recordMetric(TRIGGER_ACTIVATION_AUTHENTICATION_PROCESS_TIME, stopWatch.getTotalTimeMillis());
    }
  }

  public List<TriggerDetails> getTriggersToAuthenticate(
      TriggerWebhookEvent triggerWebhookEvent, WebhookEventMappingResponse webhookEventMappingResponse) {
    // Only GitHub events authentication is supported for now
    List<TriggerDetails> triggersToAuthenticate = new ArrayList<>();
    boolean isParentUniqueIdQueryingEnabled = true;
    if (GITHUB.name().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      for (TriggerDetails triggerDetails : webhookEventMappingResponse.getTriggers()) {
        NGTriggerConfigV2 ngTriggerConfigV2 = triggerDetails.getNgTriggerConfigV2();
        if (ngTriggerConfigV2 != null && triggerDetails.getNgTriggerEntity() != null
            && shouldAuthenticateTrigger(triggerWebhookEvent, ngTriggerConfigV2,
                triggerDetails.getNgTriggerEntity().getParentUniqueId(), isParentUniqueIdQueryingEnabled)) {
          triggersToAuthenticate.add(triggerDetails);
        }
      }
    }
    return triggersToAuthenticate;
  }

  private String getHashedPayload(TriggerWebhookEvent triggerWebhookEvent) {
    String hashedPayload = null;
    for (HeaderConfig headerConfig : triggerWebhookEvent.getHeaders()) {
      if (headerConfig.getKey().equalsIgnoreCase(X_HUB_SIGNATURE_256)) {
        List<String> values = headerConfig.getValues();
        if (isNotEmpty(values) && values.size() == 1) {
          hashedPayload = values.get(0);
        }
        break;
      }
    }
    return hashedPayload;
  }

  private Boolean shouldAuthenticateTrigger(TriggerWebhookEvent triggerWebhookEvent,
      NGTriggerConfigV2 ngTriggerConfigV2, String parentUniqueId, boolean isParentUniqueIdQueryingEnabled) {
    String mandatoryAuth = isParentUniqueIdQueryingEnabled
        ? NGRestUtils
              .getResponse(settingsClient.getSettingV2(
                  TRIGGERS_MANDATE_GITHUB_AUTHENTICATION, triggerWebhookEvent.getAccountId(), parentUniqueId))
              .getValue()
        : NGRestUtils
              .getResponse(
                  settingsClient.getSetting(TRIGGERS_MANDATE_GITHUB_AUTHENTICATION, triggerWebhookEvent.getAccountId(),
                      ngTriggerConfigV2.getOrgIdentifier(), ngTriggerConfigV2.getProjectIdentifier()))
              .getValue();
    if (mandatoryAuth.equals(MANDATE_GITHUB_AUTHENTICATION_TRUE_VALUE)) {
      return true;
    }

    return isNotEmpty(ngTriggerConfigV2.getEncryptedWebhookSecretIdentifier());
  }

  public Set<String> getAuthenticationTaskSelectors(
      NGAccess ngAccess, SecretRefData secretRefData, String triggerIdentifier) {
    NGAccess secretNGAccess = SecretRefHelper.getScopeIdentifierForSecretRef(
        secretRefData, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    SecretResponseWrapper secret = ngSecretService.getSecret(secretNGAccess.getAccountIdentifier(),
        secretNGAccess.getOrgIdentifier(), secretNGAccess.getProjectIdentifier(), secretNGAccess.getIdentifier());
    if (secret == null || secret.getSecret() == null || !(secret.getSecret().getSpec() instanceof SecretTextSpecDTO)) {
      log.warn("Secret with identifier [" + secretRefData.getIdentifier()
          + "] either does not exist or is not of Text type. Attempting to authenticate trigger [" + triggerIdentifier
          + "] with no delegate selectors.");
      return Collections.emptySet();
    }
    String secretManagerIdentifier = ((SecretTextSpecDTO) secret.getSecret().getSpec()).getSecretManagerIdentifier();
    SecretManagerConfigDTO secretManagerDTO = ngSecretService.getSecretManager(secretNGAccess.getAccountIdentifier(),
        secretNGAccess.getOrgIdentifier(), secretNGAccess.getProjectIdentifier(), secretManagerIdentifier, false);
    return SecretManagerConfigMapper.getDelegateSelectors(secretManagerDTO);
  }

  public List<HeaderConfig> prepareHeaders(WebhookDTO webhookDTO) {
    List<EventHeader> headersList = webhookDTO.getHeadersList();

    List<HeaderConfig> headerConfigs = headersList.stream()
                                           .map(eventHeader
                                               -> HeaderConfig.builder()
                                                      .key(eventHeader.getKey())
                                                      .values(eventHeader.getValuesList().stream().collect(toList()))
                                                      .build())
                                           .collect(toList());

    if (webhookDTO.getParsedResponse() != null && webhookDTO.getParsedResponse().hasEventBridge()) {
      headerConfigs.add(HeaderConfig.builder()
                            .key(X_EVENT_BRIDGE_TRIGGER)
                            .values(Collections.singletonList(X_EVENT_BRIDGE_TRIGGER))
                            .build());
    }

    if (webhookDTO.getWebhookTriggerType()
        == io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType.SYSTEM_EVENTS) {
      headerConfigs.add(HeaderConfig.builder()
                            .key(X_HARNESS_SYSTEM_EVENT_TRIGGER)
                            .values(Collections.singletonList(X_HARNESS_SYSTEM_EVENT_TRIGGER))
                            .build());
    }

    return headerConfigs;
  }

  private Map<String, String> buildAbstractions(
      String accountIdIdentifier, String orgIdentifier, String projectIdentifier) {
    Map<String, String> abstractions = new HashMap<>(2);
    String owner = taskSetupAbstractionHelper.getOwner(accountIdIdentifier, orgIdentifier, projectIdentifier);
    if (isNotEmpty(owner)) {
      abstractions.put(OWNER, owner);
    }
    abstractions.put(NG, "true");
    return abstractions;
  }

  private Type getBuildType(NGTriggerEntity ngTriggerEntity) {
    if (ngTriggerEntity.getType() == NGTriggerType.ARTIFACT
        || ngTriggerEntity.getType() == NGTriggerType.MULTI_REGION_ARTIFACT) {
      return Type.ARTIFACT;
    }
    return Type.MANIFEST;
  }

  private boolean isPipelineOnConditionValid(String pipelineYaml, WebhookDTO webhookDTO, String branch) {
    try {
      JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineYaml).get(YAMLFieldNameConstants.PIPELINE);
      if (pipelineJsonNode == null) {
        return false;
      }
      JsonNode pipelineOnJsonNode = pipelineJsonNode.get(YAMLFieldNameConstants.ON);
      if (pipelineOnJsonNode == null) {
        return false;
      }
      ParseWebhookResponse parseWebhookResponse = webhookDTO.getParsedResponse();
      return switch (parseWebhookResponse.getHookCase()) {
        case PUSH -> validatePipelineONCondition(pipelineOnJsonNode, YAMLFieldNameConstants.PUSH, branch);
        case PR -> validatePipelineONCondition(pipelineOnJsonNode, YAMLFieldNameConstants.PULL_REQUEST, branch);
        default -> false;
      };
    } catch (Exception e) {
      log.error("Error while checking pipeline on condition", e);
      return false;
    }
  }

  private GitRepoDetails getGitRepoDetails(WebhookDTO webhookDTO) {
    ParseWebhookResponse parseWebhookResponse = webhookDTO.getParsedResponse();
    switch (parseWebhookResponse.getHookCase()) {
      case PUSH:
          PushHook pushHook = parseWebhookResponse.getPush();
          return GitRepoDetails.builder()
              .branch(pushHook.getRepo().getBranch())
              .repoUrl(pushHook.getRepo().getLink())
              .repoName(pushHook.getRepo().getName())
              .build();
        case PR:
          PullRequestHook prHook = parseWebhookResponse.getPr();
          return GitRepoDetails.builder()
              .branch(prHook.getPr().getSource())
              .repoUrl(prHook.getRepo().getLink())
              .repoName(prHook.getRepo().getName())
              .build();
        default:
          return GitRepoDetails.builder().build();
      }
    }

    private boolean validatePipelineONCondition(JsonNode pipelineOnNode, String field, String branch) {
      for (JsonNode node : pipelineOnNode) {
        if (TextNode.valueOf(field).equals(node)) {
          return true;
        }
        if (YamlUtils.isYamlFieldPresent(pipelineOnNode, field)) {
          JsonNode fieldNode = pipelineOnNode.get(field);
          if (!YamlUtils.isYamlFieldPresent(fieldNode, YAMLFieldNameConstants.BRANCHES)) {
            return false;
          }
          JsonNode branchNodes = fieldNode.get(YAMLFieldNameConstants.BRANCHES);
          for (JsonNode branchNode : branchNodes) {
            String getBranch = branchNode.asText();
            if (matchesString(getBranch, branch)) {
              return true;
            }
          }
        }
      }
      return false;
    }

    private void processTriggerV1(WebhookDTO webhookDTO, TriggerWebhookEvent triggerWebhookEvent) {
      StopWatch stopWatch = new StopWatch();
      stopWatch.start();
      try {
        GitRepoDetails gitRepoDetails = getGitRepoDetails(webhookDTO);
        setupGitContext(gitRepoDetails);
        List<PipelineEntity> pipelineEntityList = new ArrayList<>();

        // Here we are building criteria to fetch V1 pipelines stored remotely in some repo.
        Criteria getV1PipelineEntityCriteria =
            PMSPipelineServiceHelper.buildCriteriaWithRepoUrlAndHarnessVersionAndStoreType(
                gitRepoDetails.getRepoUrl(), HarnessYamlVersion.V1, StoreType.REMOTE);

        List<PipelineEntity> pipelineEntities = pmsPipelineRepository.find(getV1PipelineEntityCriteria);

        // Iterating through all pipeline entities and at the end only those pipelines will remain whose file will
        // be present in given branch provided that Pipeline ON condition is satisfied.
        for (PipelineEntity pipeline : pipelineEntities) {
          triggerExecutionHelper.setPrincipal(triggerWebhookEvent);
          try {
            String yaml = pmsPipelineServiceHelper.fetchYamlFromRemote(true, pipeline, gitRepoDetails);
            pipeline.setYaml(yaml);
            if (isPipelineOnConditionValid(yaml, webhookDTO, gitRepoDetails.getBranch())) {
              pipelineEntityList.add(pipeline);
            }
          } catch (Exception e) {
            log.error("Error while fetching remote entity in path {} and branch {}. Skipping processing of pipeline "
                    + "entity for V1 trigger",
                pipeline.getFilePath(), gitRepoDetails.getBranch(), e);
          }
        }
        triggerPipelineExecutionForV1(pipelineEntityList, triggerWebhookEvent);
      } catch (Exception e) {
        log.error("Error while processing webhook event for V1 triggers", e);
      }
      stopWatch.stop();
      try (PmsMetricContextGuard metricContext = new PmsMetricContextGuard(
               ImmutableMap.<String, String>builder()
                   .put(PmsEventMonitoringConstants.ACCOUNT_ID, triggerWebhookEvent.getAccountId())
                   .build())) {
        metricService.recordMetric(
            TRIGGER_ACTIVATION_TRIGGER_EXECUTION_V1_PROCESS_TIME, stopWatch.getTotalTimeMillis());
      }
    }

    // Branch in "ON" condition can be a wildcard string. This method checks if the branch matches the pattern.
    private boolean matchesString(String pattern, String str) {
      String regex = "^" + pattern.replace("*", ".*") + "$";
      Pattern compiledPattern = Pattern.compile(regex);
      Matcher matcher = compiledPattern.matcher(str);
      return matcher.matches();
    }

    private void setupGitContext(GitRepoDetails gitRepoDetails) {
      GitAwareContextHelper.populateGitDetails(
          io.harness.gitsync.interceptor.GitEntityInfo.builder().branch(gitRepoDetails.getBranch()).build());
    }

    private void processTriggers(TriggerMappingRequestData mappingRequestData,
        WebhookEventMappingResponse webhookEventMappingResponse, TriggerWebhookEvent triggerWebhookEvent,
        List<TriggerEventResponse> eventResponses, TriggerNotificationDataBuilder triggerNotificationDataBuilder) {
      for (TriggerDetails triggerDetails : webhookEventMappingResponse.getTriggers()) {
        NGTriggerEntity triggerEntity = triggerDetails.getNgTriggerEntity();
        try (TracingUtils.TracingContext tracingContext = triggerEntity != null
                ? TriggerExecutionHelper.generateTraceIdAndStartSpan(tracer, triggerEntity)
                : null) {
          if (triggerEntity == null) {
            log.error("Trigger Entity is empty, This should not happen, please check");
            continue;
          }
          processSingleTrigger(mappingRequestData, webhookEventMappingResponse, triggerWebhookEvent, triggerDetails,
              eventResponses, triggerNotificationDataBuilder);
        } catch (Exception e) {
          handleWebhookTriggerException(e, triggerEntity, triggerDetails, triggerWebhookEvent, eventResponses);
        }
      }
    }

    private void processSingleTrigger(TriggerMappingRequestData mappingRequestData,
        WebhookEventMappingResponse webhookEventMappingResponse, TriggerWebhookEvent triggerWebhookEvent,
        TriggerDetails triggerDetails, List<TriggerEventResponse> eventResponses,
        TriggerNotificationDataBuilder triggerNotificationDataBuilder) {
      if (triggerDetails.getNgTriggerEntity() == null) {
        log.error("Trigger Entity is empty, This should not happen, please check");
        return;
      }

      boolean isParentIdQueryingEnabled = true;
      ScopeInfo scopeInfo = isParentIdQueryingEnabled
          ? scopeResolutionHelper.getScopeInfo(triggerDetails.getNgTriggerEntity().getAccountId(),
                triggerDetails.getNgTriggerEntity().getParentUniqueId())
          : null;

      if (shouldSkipTrigger(triggerDetails, webhookEventMappingResponse, triggerWebhookEvent, eventResponses, scopeInfo,
              isParentIdQueryingEnabled)) {
        return;
      }

      // Fast path: keeps a time sensitive cancel off the redis round trip publishAsyncTriggerExecution would otherwise
      // add. The same handling is repeated in triggerPipelineExecution, which covers the legacy and async consumer
      // paths that never reach this method.
      ParseWebhookResponse parseWebhookResponse = webhookEventMappingResponse.getParseWebhookResponse();
      if (parseWebhookResponse != null && parseWebhookResponse.hasMergeQueue()
          && Action.CHECKS_CANCELED.equals(parseWebhookResponse.getMergeQueue().getAction())) {
        eventResponses.add(abortForMergeQueueChecksCanceled(triggerWebhookEvent, triggerDetails,
            parseWebhookResponse.getMergeQueue(), scopeInfo, isParentIdQueryingEnabled));
        return;
      }

      if (mappingRequestData.getWebhookDTO() != null) {
        publishAsyncTriggerExecution(
            mappingRequestData, webhookEventMappingResponse, triggerDetails, scopeInfo, isParentIdQueryingEnabled);
      } else {
        updateWebhookRegistrationStatusAndTriggerPipelineExecution(
            webhookEventMappingResponse.getParseWebhookResponse(), triggerWebhookEvent, eventResponses, triggerDetails,
            webhookEventMappingResponse.getChangedFiles(), triggerNotificationDataBuilder, true);
      }
    }

    private boolean shouldSkipTrigger(TriggerDetails triggerDetails,
        WebhookEventMappingResponse webhookEventMappingResponse, TriggerWebhookEvent triggerWebhookEvent,
        List<TriggerEventResponse> eventResponses, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
      if (triggerDetails.getAuthenticated() != null && !triggerDetails.getAuthenticated()) {
        eventResponses.add(generateEventHistoryForAuthenticationError(triggerWebhookEvent, triggerDetails,
            triggerDetails.getNgTriggerEntity(), scopeInfo, isParentIdQueryingEnabled));
        return true;
      }

      if (webhookEventMappingResponse.getWebhookEventResponse() != null
          && webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus() == SKIPPED) {
        eventResponses.add(TriggerEventResponseHelper.toResponse(SKIPPED, triggerWebhookEvent, null,
            triggerDetails.getNgTriggerEntity(), "Trigger event was skipped.", null));
        return true;
      }

      return false;
    }

    private void handleWebhookTriggerException(Exception e, NGTriggerEntity triggerEntity,
        TriggerDetails triggerDetails, TriggerWebhookEvent triggerWebhookEvent,
        List<TriggerEventResponse> eventResponses) {
      log.error(String.format("Error while processing webhook trigger: %s",
                    triggerEntity != null ? TriggerHelper.getTriggerRef(triggerEntity) : "unknown"),
          e);
      if (triggerEntity != null) {
        TargetExecutionSummary targetExecutionSummary =
            TriggerEventResponseHelper.prepareTargetExecutionSummary((PlanExecution) null, triggerDetails, null);
        eventResponses.add(TriggerEventResponseHelper.toResponse(EXCEPTION_WHILE_PROCESSING, triggerWebhookEvent, null,
            triggerEntity, e.getMessage() != null ? e.getMessage() : "Exception while processing trigger",
            targetExecutionSummary));
      }
    }

    private void publishAsyncTriggerExecution(TriggerMappingRequestData mappingRequestData,
        WebhookEventMappingResponse webhookEventMappingResponse, TriggerDetails triggerDetails, ScopeInfo scopeInfo,
        boolean isParentIdQueryingEnabled) {
      WebhookDTO webhookDTO = mappingRequestData.getWebhookDTO();
      ParseWebhookResponse parseWebhookResponse = webhookEventMappingResponse.getParseWebhookResponse();
      if (parseWebhookResponse != null) {
        webhookDTO = webhookDTO.toBuilder().setParsedResponse(parseWebhookResponse).build();
      }

      TriggerExecutionDTO triggerExecutionDTO =
          TriggerExecutionDTO.newBuilder()
              .setWebhookDto(webhookDTO)
              .setAccountId(triggerDetails.getNgTriggerEntity().getAccountId())
              .setOrgIdentifier(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier()
                                                          : triggerDetails.getNgTriggerEntity().getOrgIdentifier())
              .setProjectIdentifier(isParentIdQueryingEnabled
                      ? scopeInfo.getProjectIdentifier()
                      : triggerDetails.getNgTriggerEntity().getProjectIdentifier())
              .setParentUniqueId(emptyIfNull(triggerDetails.getNgTriggerEntity().getParentUniqueId()))
              .setTargetIdentifier(triggerDetails.getNgTriggerEntity().getTargetIdentifier())
              .setTriggerIdentifier(triggerDetails.getNgTriggerEntity().getIdentifier())
              .setAuthenticated(
                  triggerDetails.getAuthenticated() != null ? triggerDetails.getAuthenticated() : Boolean.TRUE)
              .addAllChangedFiles(webhookEventMappingResponse.getChangedFiles())
              .build();
      triggerWebhookEventPublisher.publishTriggerWebhookEvent(triggerExecutionDTO);
    }
  }

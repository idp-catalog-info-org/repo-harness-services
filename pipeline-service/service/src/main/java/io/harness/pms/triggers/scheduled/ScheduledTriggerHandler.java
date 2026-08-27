/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.scheduled;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.iterator.pojos.SchedulingType.IRREGULAR_SKIP_MISSED;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.UUIDGenerator;
import io.harness.execution.PlanExecution;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData.TriggerNotificationDataBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryBuilder;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.response.TriggerEventStatus;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.instrumentation.TriggerTelemetryHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.notification.helper.TriggerFailureNotificationHelper;
import io.harness.pms.triggers.TriggerExecutionHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.tracing.TracingUtils;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.StopWatch;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class ScheduledTriggerHandler implements Handler<NGTriggerEntity> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private TriggerExecutionHelper ngTriggerExecutionHelper;
  @Inject private TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Inject private TriggerExecutionHelper triggerExecutionHelper;
  @Inject private TriggerEventExecutionHelper triggerEventExecutionHelper;
  @Inject private TriggerFailureNotificationHelper triggerFailureNotificationHelper;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;

  @Inject private TriggerTelemetryHelper triggerTelemetryHelper;
  @Inject private MetricService metricService;
  public static final Tracer tracer =
      GlobalOpenTelemetry.getTracer("io.harness.pms.triggers.scheduled.ScheduledTriggerHandler");

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createLoopIteratorWithDedicatedThreadPoolNoRecoverAfterPause(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("ScheduledTriggerProcessor")
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .build(),
        ScheduledTriggerHandler.class,
        MongoPersistenceIterator.<NGTriggerEntity, SpringFilterExpander>builder()
            .unsorted(true)
            .clazz(NGTriggerEntity.class)
            .fieldName(NGTriggerEntityKeys.nextIterations)
            .targetInterval(ofMinutes(5))
            .acceptableExecutionTime(ofMinutes(1))
            .acceptableNoAlertDelay(ofSeconds(30))
            .maximumDelayForCheck(ofSeconds(30))
            .handler(this)
            .filterExpander(query
                -> query.addCriteria(new Criteria()
                                         .and(NGTriggerEntityKeys.nextIterations)
                                         .exists(true)
                                         .and(NGTriggerEntityKeys.type)
                                         .is(NGTriggerType.SCHEDULED)
                                         .and(NGTriggerEntityKeys.enabled)
                                         .is(true)))
            .schedulingType(IRREGULAR_SKIP_MISSED)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  public void handle(NGTriggerEntity entity) {
    try (TracingUtils.TracingContext tracingContext =
             TriggerExecutionHelper.generateTraceIdAndStartSpan(tracer, entity)) {
      boolean isParentIdQueryingEnabled = true;
      ScopeInfo scopeInfo = isParentIdQueryingEnabled
          ? scopeResolutionHelper.getScopeInfo(entity.getAccountId(), entity.getParentUniqueId())
          : null;

      try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext("webhookId", entity.getWebhookId(),
               entity.getIdentifier(), entity.getTargetIdentifier(),
               isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : entity.getProjectIdentifier(),
               isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : entity.getOrgIdentifier(),
               entity.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
        String runtimeInputYaml = null;
        TriggerNotificationDataBuilder triggerNotificationDataBuilder = TriggerNotificationData.builder();
        StopWatch triggerActivationWatch = new StopWatch();
        triggerActivationWatch.start();
        try {
          triggerExecutionHelper.setPrincipal(null, entity);

          TriggerDetails triggerDetails = TriggerDetails.builder()
                                              .ngTriggerEntity(entity)
                                              .ngTriggerConfigV2(ngTriggerElementMapper.toTriggerConfigV2(
                                                  entity, scopeInfo, isParentIdQueryingEnabled))
                                              .build();
          TriggerWebhookEvent triggerWebhookEvent =
              TriggerWebhookEvent.builder()
                  .accountId(entity.getAccountId())
                  .orgIdentifier(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : entity.getOrgIdentifier())
                  .projectIdentifier(
                      isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : entity.getProjectIdentifier())
                  .triggerIdentifier(entity.getIdentifier())
                  .uuid("Cron_" + UUIDGenerator.generateUuid())
                  .build();

          List<String> inputSetRefs = triggerExecutionHelper.getInputSetRefs(
              triggerDetails.getNgTriggerConfigV2().getInputSetRefs(), triggerWebhookEvent);

          if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName()) && isEmpty(inputSetRefs)) {
            runtimeInputYaml = triggerDetails.getNgTriggerConfigV2().getInputYaml();
          } else {
            triggerExecutionHelper.setPrincipal(null, entity);
            runtimeInputYaml = triggerExecutionHelper.fetchInputSetYAML(
                triggerDetails, null, inputSetRefs, null, scopeInfo, isParentIdQueryingEnabled);
          }

          PlanExecution response = ngTriggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionRequest(triggerDetails,
              TriggerPayload.newBuilder().setType(Type.SCHEDULED).build(), triggerWebhookEvent, null, null,
              runtimeInputYaml, scopeInfo, isParentIdQueryingEnabled);
          triggerActivationWatch.stop();
          triggerEventExecutionHelper.recordTriggerActivationTime(triggerActivationWatch, entity);
          TriggerEventHistory triggerEventHistory = toHistoryRecord(entity, "TARGET_EXECUTION_REQUESTED",
              "Pipeline execution was requested successfully", false, response, runtimeInputYaml);
          triggerEventExecutionHelper.validateUniqueIdAndParentUniqueId(triggerEventHistory);
          triggerEventHistoryRepository.save(triggerEventHistory);
          log.info("Execution started for cron trigger: " + entity.getIdentifier()
              + " with planExecutionId: " + response.getPlanId());
          triggerTelemetryHelper.sendTriggersExecutionEvent(
              entity, triggerDetails, TriggerEventStatus.FinalResponse.SUCCESS, scopeInfo, isParentIdQueryingEnabled);
        } catch (Exception e) {
          TriggerEventHistory triggerEventHistory = toHistoryRecord(entity, "EXCEPTION_WHILE_PROCESSING",
              TriggerEventResponseHelper.extractErrorMessage(e), true, null, runtimeInputYaml);
          triggerFailureNotificationHelper.sendTriggerNotification(triggerEventHistory,
              TriggerEventResponse.FinalStatus.EXCEPTION_WHILE_PROCESSING, triggerNotificationDataBuilder);
          triggerEventExecutionHelper.validateUniqueIdAndParentUniqueId(triggerEventHistory);
          triggerEventHistoryRepository.save(triggerEventHistory);
          log.warn("Exception while triggering cron. Please check", e);
        }
      }
    }
  }
  private TriggerEventHistory toHistoryRecord(NGTriggerEntity entity, String finalStatus, String message,
      boolean exceptionOccurred, PlanExecution planExecution, String runtimeInputYaml) {
    // trigger projectIdentifier will not be needed once triggerEventHistory projectIdentifer and orgIdentifer is
    // removed
    TriggerEventHistoryBuilder triggerEventHistoryBuilder = TriggerEventHistory.builder()
                                                                .accountId(entity.getAccountId())
                                                                .orgIdentifier(entity.getOrgIdentifier())
                                                                .projectIdentifier(entity.getProjectIdentifier())
                                                                .parentUniqueId(entity.getParentUniqueId())
                                                                .targetIdentifier(entity.getTargetIdentifier())
                                                                .eventCreatedAt(System.currentTimeMillis())
                                                                .finalStatus(finalStatus)
                                                                .message(message)
                                                                .ngTriggerType(entity.getType())
                                                                .exceptionOccurred(exceptionOccurred)
                                                                .triggerIdentifier(entity.getIdentifier())
                                                                .triggerName(entity.getName());

    if (entity.getMetadata().getCron() != null) {
      triggerEventHistoryBuilder.triggerSubType(entity.getMetadata().getCron().getType());
    }

    if (planExecution != null) {
      triggerEventHistoryBuilder.targetExecutionSummary(TargetExecutionSummary.builder()
                                                            .runtimeInput(runtimeInputYaml)
                                                            .planExecutionId(planExecution.getUuid())
                                                            .startTs(planExecution.getStartTs())
                                                            .triggerId(entity.getIdentifier())
                                                            .executionStatus(planExecution.getStatus().name())
                                                            .targetId(entity.getTargetIdentifier())
                                                            .runSequence(planExecution.getMetadata().getRunSequence())
                                                            .build());
    }

    return triggerEventHistoryBuilder.build();
  }
}

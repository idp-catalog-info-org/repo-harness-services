/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.aggregation.rules.events.AggregationRuleComputeInitiatedEvent.AGGREGATION_RULE_COMPUTE_INITIATED;
import static io.harness.idp.aggregation.rules.events.AggregationRuleCreateEvent.AGGREGATION_RULE_CREATED;
import static io.harness.idp.aggregation.rules.events.AggregationRuleDeleteEvent.AGGREGATION_RULE_DELETED;
import static io.harness.idp.aggregation.rules.events.AggregationRuleUpdateEvent.AGGREGATION_RULE_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.aggregation.rules.events.AggregationRuleComputeInitiatedEvent;
import io.harness.idp.aggregation.rules.events.AggregationRuleCreateEvent;
import io.harness.idp.aggregation.rules.events.AggregationRuleDeleteEvent;
import io.harness.idp.aggregation.rules.events.AggregationRuleUpdateEvent;
import io.harness.idp.aggregation.rules.service.AggregationRulesService;
import io.harness.idp.audittrails.eventhandlers.dtos.AggregationRuleDTO;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AggregationRuleEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;
  private final AggregationRulesService aggregationRulesService;
  private final ExecutorService executorService;

  @Inject
  public AggregationRuleEventHandler(AuditClientService auditClientService,
      AggregationRulesService aggregationRulesService,
      @Named("AggregationRuleComputeExecutor") ExecutorService executorService) {
    this.auditClientService = auditClientService;
    this.aggregationRulesService = aggregationRulesService;
    this.executorService = executorService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case AGGREGATION_RULE_CREATED:
          return handleAggregationRuleCreateEvent(outboxEvent);
        case AGGREGATION_RULE_UPDATED:
          return handleAggregationRuleUpdateEvent(outboxEvent);
        case AGGREGATION_RULE_DELETED:
          return handleAggregationRuleDeleteEvent(outboxEvent);
        case AGGREGATION_RULE_COMPUTE_INITIATED:
          return handleAggregationRuleComputeInitiatedEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException e) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", e);
      return false;
    }
  }

  private boolean handleAggregationRuleCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    AggregationRuleCreateEvent aggregationRuleCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), AggregationRuleCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.IDP)
            .newYaml(NGYamlUtils.getYamlString(
                AggregationRuleDTO.builder()
                    .aggregationRule(aggregationRuleCreateEvent.getNewAggregationRuleDetailsResponse())
                    .build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    auditClientService.publishAudit(auditEntry, globalContext);
    CompletableFuture.runAsync(
        ()
            -> aggregationRulesService.compute(aggregationRuleCreateEvent.getAccountIdentifier(),
                aggregationRuleCreateEvent.getNewAggregationRuleDetailsResponse().getAggregationRule().getIdentifier()),
        executorService);
    return true;
  }

  private boolean handleAggregationRuleUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    AggregationRuleUpdateEvent aggregationRuleUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), AggregationRuleUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.IDP)
            .newYaml(NGYamlUtils.getYamlString(
                AggregationRuleDTO.builder()
                    .aggregationRule(aggregationRuleUpdateEvent.getNewAggregationRuleDetailsResponse())
                    .build(),
                objectMapper))
            .oldYaml(NGYamlUtils.getYamlString(
                AggregationRuleDTO.builder()
                    .aggregationRule(aggregationRuleUpdateEvent.getOldAggregationRuleDetailsResponse())
                    .build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    auditClientService.publishAudit(auditEntry, globalContext);
    CompletableFuture.runAsync(
        ()
            -> aggregationRulesService.compute(aggregationRuleUpdateEvent.getAccountIdentifier(),
                aggregationRuleUpdateEvent.getOldAggregationRuleDetailsResponse().getAggregationRule(),
                aggregationRuleUpdateEvent.getNewAggregationRuleDetailsResponse().getAggregationRule()),
        executorService);
    return true;
  }

  private boolean handleAggregationRuleDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    AggregationRuleDeleteEvent aggregationRuleDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), AggregationRuleDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.IDP)
            .oldYaml(NGYamlUtils.getYamlString(
                AggregationRuleDTO.builder()
                    .aggregationRule(aggregationRuleDeleteEvent.getOldAggregationRuleDetailsResponse())
                    .build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    auditClientService.publishAudit(auditEntry, globalContext);
    CompletableFuture.runAsync(
        ()
            -> aggregationRulesService.deleteRuleFieldsFromHierarchicalEntities(
                aggregationRuleDeleteEvent.getAccountIdentifier(),
                aggregationRuleDeleteEvent.getOldAggregationRuleDetailsResponse().getAggregationRule()),
        executorService);
    return true;
  }

  private boolean handleAggregationRuleComputeInitiatedEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    AggregationRuleComputeInitiatedEvent aggregationRuleComputeInitiatedEvent =
        objectMapper.readValue(outboxEvent.getEventData(), AggregationRuleComputeInitiatedEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.RERUN)
                                .module(ModuleType.IDP)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    auditClientService.publishAudit(auditEntry, globalContext);
    CompletableFuture.runAsync(
        ()
            -> aggregationRulesService.compute(aggregationRuleComputeInitiatedEvent.getAccountIdentifier(),
                aggregationRuleComputeInitiatedEvent.getAggregationRuleIdentifier()),
        executorService);
    return true;
  }
}

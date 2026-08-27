/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.deleteGraph;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.GraphDeleteEvent;
import io.harness.beans.GraphDeleteEvent.GraphDeleteEventsKeys;
import io.harness.beans.ScopeInfo;
import io.harness.elasticsearch.ElasticSearchStream;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.search.helper.PipelineSearchHelper;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;
import io.harness.search.entity.beans.PipelineSearchReadExecutionSummaryDTO;
import io.harness.search.service.PipelineSearchService;
import io.harness.service.GraphGenerationService;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class GraphDeleteServiceHelper
    extends IteratorLoopModeHandler implements MongoPersistenceIterator.Handler<GraphDeleteEvent> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private GraphGenerationService graphGenerationService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private PipelineSearchService pipelineSearchService;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;

  @Override
  protected void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "GraphDeleteEventProcessor";
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    // do nothing
    log.error("createAndStartIterator is not overridden");
  }

  @Override
  public void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<GraphDeleteEvent, SpringFilterExpander>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       GraphDeleteEvent.class,
                       MongoPersistenceIterator.<GraphDeleteEvent, SpringFilterExpander>builder()
                           .clazz(GraphDeleteEvent.class)
                           .fieldName(GraphDeleteEventsKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ofSeconds(30))
                           .acceptableExecutionTime(ofMinutes(2))
                           .handler(this)
                           .schedulingType(REGULAR)
                           .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate)));
  }

  @Override
  public void handle(GraphDeleteEvent entity) {
    long updatedCount = 0L;
    try {
      ScopeInfo scopeInfo =
          scopeResolutionHelper.getScopeInfo(entity.getAccountId(), entity.getOrgId(), entity.getProjectId());
      if (pmsFeatureFlagService.isEnabled(entity.getAccountId(), FeatureName.PIPE_ENABLE_ELASTIC_SEARCH)) {
        co.elastic.clients.elasticsearch._types.query_dsl.Query query =
            PipelineSearchHelper.formQueryWithScopeAndPipelineIdentifierAndCreatedAt(entity.getAccountId(), scopeInfo,
                entity.getPipelineIdentifier(), entity.getStartTs(), entity.getEndTs());
        try (ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> elasticSearchStream =
                 pipelineSearchService.fetchPipelineSearchReadExecutionSummaryDTO(entity.getAccountId(), query,
                     Set.of(PipelineSearchExecutionSummaryDTOKeys.status,
                         PipelineSearchExecutionSummaryDTOKeys.planExecutionId,
                         PipelineSearchExecutionSummaryDTOKeys.endTs))) {
          Iterator<PipelineSearchReadExecutionSummaryDTO> iterator = elasticSearchStream.iterator();
          while (iterator.hasNext()) {
            PipelineSearchReadExecutionSummaryDTO pipelineSearchReadExecutionSummaryDTO = iterator.next();
            String planExecutionId = pipelineSearchReadExecutionSummaryDTO.getPlanExecutionId();
            graphGenerationService.deleteOutputsForStepInGraph(entity.getAccountId(), planExecutionId,
                entity.getStepType(), pipelineSearchReadExecutionSummaryDTO.getEndTs(),
                ExecutionStatus.valueOf(pipelineSearchReadExecutionSummaryDTO.getStatus()).getEngineStatus());
            updatedCount++;
          }
        }
      } else {
        Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.accountId)
                                .is(entity.getAccountId())
                                .and(PlanExecutionSummaryKeys.pipelineIdentifier)
                                .is(entity.getPipelineIdentifier())
                                .and(PlanExecutionSummaryKeys.createdAt)
                                .gt(entity.getStartTs())
                                .lt(entity.getEndTs())
                                .and(PlanExecutionSummaryKeys.parentUniqueId)
                                .is(scopeInfo.getUniqueId());

        Stream<PipelineExecutionSummaryEntity> stream =
            pmsExecutionSummaryRepository.findAllWithRequiredProjectionUsingAnalyticsNode(criteria,
                List.of(PlanExecutionSummaryKeys.planExecutionId, PlanExecutionSummaryKeys.endTs,
                    PlanExecutionSummaryKeys.status));
        Iterator<PipelineExecutionSummaryEntity> iterator = stream.iterator();
        while (iterator.hasNext()) {
          PipelineExecutionSummaryEntity summaryEntity = iterator.next();
          String planExecutionId = summaryEntity.getPlanExecutionId();
          graphGenerationService.deleteOutputsForStepInGraph(entity.getAccountId(), planExecutionId,
              entity.getStepType(), summaryEntity.getEndTs(), summaryEntity.getStatus().getEngineStatus());
          updatedCount++;
        }
      }
    } catch (Exception e) {
      log.error("Exception while deleting outputs from graph for executions with unmasked secrets failed", e);
    } finally {
      Query query = new Query(Criteria.where(GraphDeleteEventsKeys.uuid).is(entity.getUuid()));
      mongoTemplate.remove(query, GraphDeleteEvent.class);
      log.info("Entity with uuid {} and accountId {} deleted successfully post updating {} graphs", entity.getUuid(),
          entity.getAccountId(), updatedCount);
    }
  }
}

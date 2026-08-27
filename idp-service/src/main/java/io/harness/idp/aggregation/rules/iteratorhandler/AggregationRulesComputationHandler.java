/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.iteratorhandler;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.service.AggregationRulesService;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = HarnessModuleComponent.IDP_SERVICE)
public class AggregationRulesComputationHandler implements MongoPersistenceIterator.Handler<AggregationRuleEntity> {
  private PersistenceIteratorFactory persistenceIteratorFactory;
  private MongoTemplate mongoTemplate;
  private static final String AGGREGATION_RULES_COMPUTATION_HANDLER = "AggregationRulesComputationHandler";
  @Inject AggregationRulesService aggregationRulesService;
  @Inject private IdpIteratorMetricRecorder idpIteratorMetricRecorder;

  @Override
  public void handle(AggregationRuleEntity entity) {
    String accountIdentifier = entity.getAccountIdentifier();
    String identifier = entity.getIdentifier();
    log.info("Started Aggregation Rules Computation for account - {} identifier - {}", accountIdentifier, identifier);
    try {
      aggregationRulesService.compute(entity);
      idpIteratorMetricRecorder.recordSuccess(AGGREGATION_RULES_COMPUTATION_HANDLER, accountIdentifier);
    } catch (Exception exception) {
      idpIteratorMetricRecorder.recordFailure(AGGREGATION_RULES_COMPUTATION_HANDLER, accountIdentifier);
      throw exception;
    }
    log.info("Completed Aggregation Rules Computation for account - {} identifier - {}", accountIdentifier, identifier);
  }

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(AGGREGATION_RULES_COMPUTATION_HANDLER)
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(15))
            .build(),
        AggregationRulesComputationHandler.class,
        MongoPersistenceIterator.<AggregationRuleEntity, SpringFilterExpander>builder()
            .clazz(AggregationRuleEntity.class)
            .fieldName(AggregationRuleEntity.AggregationRuleKeys.nextIteration)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableExecutionTime(ofSeconds(360))
            .acceptableNoAlertDelay(ofSeconds(60))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }
}

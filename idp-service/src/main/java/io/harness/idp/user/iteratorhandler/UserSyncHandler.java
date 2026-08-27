/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.user.iteratorhandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.user.beans.entity.UserEventEntity;
import io.harness.idp.user.repositories.UserEventRepository;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = HarnessModuleComponent.IDP_SERVICE)
public class UserSyncHandler implements MongoPersistenceIterator.Handler<IteratorEntity> {
  private PersistenceIteratorFactory persistenceIteratorFactory;
  private MongoTemplate mongoTemplate;
  private IdpCommonService idpCommonService;
  private UserEventRepository userEventRepository;
  private BackstageResourceClient backstageResourceClient;
  private NamespaceService namespaceService;
  private static final String USER_SYNC_Handler = "UserSyncHandler";

  @Override
  public void handle(IteratorEntity entity) {
    log.info("User sync iterator started");
    List<UserEventEntity> userEventEntities = userEventRepository.findAllByHasEvent(true);
    List<String> activeAccounts = namespaceService.getAccountIds();
    if (isEmpty(activeAccounts)) {
      return;
    }
    userEventEntities.forEach(userEventEntity -> {
      String accountIdentifier = userEventEntity.getAccountIdentifier();
      if (!activeAccounts.contains(accountIdentifier)) {
        return;
      }

      if (!idpCommonService.idpV2Enabled(accountIdentifier)) {
        String userGroupIdentifier = userEventEntity.getUserGroupIdentifier();
        try {
          log.info("Processing event for account {} userGroup {}", accountIdentifier, userGroupIdentifier);
          getGeneralResponse(backstageResourceClient.providerRefresh(accountIdentifier, userGroupIdentifier));
        } catch (Exception e) {
          log.error("Could not sync users  for account {} userGroup {}", accountIdentifier, userGroupIdentifier, e);
          return;
        }
      }
      userEventEntity.setHasEvent(false);
      userEventRepository.saveOrUpdate(userEventEntity);
    });

    log.info("User sync iterator completed");
  }

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(USER_SYNC_Handler)
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds() / 2))
            .build(),
        UserSyncHandler.class,
        MongoPersistenceIterator.<IteratorEntity, SpringFilterExpander>builder()
            .clazz(IteratorEntity.class)
            .filterExpander(query -> query.addCriteria(where(IteratorEntity.IteratorsKeys.name).is(USER_SYNC_Handler)))
            .fieldName(IteratorEntity.IteratorsKeys.nextIteration)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableExecutionTime(ofSeconds(60))
            .acceptableNoAlertDelay(ofSeconds(60))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }
}

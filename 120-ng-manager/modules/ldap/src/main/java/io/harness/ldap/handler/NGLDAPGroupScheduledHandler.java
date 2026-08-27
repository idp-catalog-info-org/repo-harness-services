/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.handler;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.mongo.iterator.pojos.SchedulingType.IRREGULAR_SKIP_MISSED;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.interfaces.PersistenceIterator;
import io.harness.ldap.entity.NGLdapSettings;
import io.harness.ldap.service.NGLdapSettingsService;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.sso.entity.SSOSettings.NgSsoSettingsKeys;

import software.wings.beans.sso.SSOType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NGLDAPGroupScheduledHandler implements MongoPersistenceIterator.Handler<NGLdapSettings> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private NGLdapSettingsService ngLdapService;
  @Inject private FeatureFlagService featureFlagService;
  MongoPersistenceIterator<NGLdapSettings, SpringFilterExpander> iterator;
  private static final int BATCH_SIZE_MULTIPLY_FACTOR = 2;
  private static final String DEBUG_MESSAGE = "NGLDAPGroupScheduledHandler: ";

  public void registerIterators(int threadPoolSize) {
    int redisBatchSize = BATCH_SIZE_MULTIPLY_FACTOR * threadPoolSize;

    PersistenceIteratorFactory.PumpExecutorOptions executorOptions =
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("NGLDAPGroupScheduledHandler")
            .poolSize(threadPoolSize)
            .interval(ofSeconds(45))
            .build();

    iterator =
        (MongoPersistenceIterator<NGLdapSettings, SpringFilterExpander>)
            persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                NGLDAPGroupScheduledHandler.class,
                MongoPersistenceIterator.<NGLdapSettings, SpringFilterExpander>builder()
                    .mode(PersistenceIterator.ProcessMode.REDIS_BATCH)
                    .clazz(NGLdapSettings.class)
                    .fieldName(NgSsoSettingsKeys.nextIterations)
                    .acceptableNoAlertDelay(ofSeconds(60))
                    .targetInterval(ofSeconds(15))
                    .semaphore(new Semaphore(10))
                    .handler(this)
                    .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
                    .filterExpander(q
                        -> q.addCriteria(where(NgSsoSettingsKeys.type).is(SSOType.LDAP))
                               .addCriteria(where(NgSsoSettingsKeys.nextIterations).nin(null, Collections.emptyList())))
                    .schedulingType(IRREGULAR_SKIP_MISSED));
  }

  @Override
  public void handle(NGLdapSettings ngldapSettings) {
    if (featureFlagService.isEnabled(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS, ngldapSettings.getAccountIdentifier())
        && ngldapSettings.getConnectionSettings().getPasswordRef() != null) {
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
        log.info(DEBUG_MESSAGE + "Setting SecurityContext completed.");
        ngLdapService.syncUserGroupsJob(ngldapSettings.getAccountIdentifier());
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE + " unexpected error occurred while Setting SecurityContext", ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
        log.info(DEBUG_MESSAGE + "Unsetting SecurityContext completed.");
      }
    }
  }
}

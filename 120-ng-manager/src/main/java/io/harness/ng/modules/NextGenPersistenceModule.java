/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.modules;

import static io.harness.mongo.helper.MongoConstants.SECONDARY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.notification.NotificationChannelPersistenceConfig;
import io.harness.springdata.BucketRegistry;
import io.harness.springdata.HTransactionTemplate;
import io.harness.springdata.QueryBudgetBindings;
import io.harness.springdata.SpringPersistenceModule;
import io.harness.waiter.persistence.WaitNotifyMongoTemplateFactory;
import io.harness.waiter.persistence.WaitNotifySpringPersistenceConfig;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.util.Modules;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.PL)
public class NextGenPersistenceModule extends AbstractModule {
  @Override
  protected void configure() {
    install(Modules
                .override(new SpringPersistenceModule() {
                  @Override
                  protected Class<?>[] getConfigClasses() {
                    return NextGenPersistenceModule.this.getConfigClasses();
                  }
                })
                .with(new AbstractModule() {
                  @Provides
                  @Singleton
                  @Named(SECONDARY)
                  MongoTemplate getSecondaryPreferredMongoTemplate(MongoTemplate mongoTemplate,
                      MongoConfig primaryMongoConfig, Injector injector, BucketRegistry bucketRegistry) {
                    return WaitNotifyMongoTemplateFactory.createSecondaryPreferred(
                        mongoTemplate.getMongoDatabaseFactory(), mongoTemplate.getConverter(), primaryMongoConfig,
                        bucketRegistry, QueryBudgetBindings.resolveQueryBudgetMetrics(injector));
                  }
                }));
  }

  Class<?>[] getConfigClasses() {
    return new Class<?>[] {WaitNotifySpringPersistenceConfig.class, NotificationChannelPersistenceConfig.class};
  }

  @Provides
  @Singleton
  protected TransactionTemplate getTransactionTemplate(
      MongoTransactionManager mongoTransactionManager, MongoConfig mongoConfig) {
    return new HTransactionTemplate(mongoTransactionManager, mongoConfig.isTransactionsEnabled());
  }
}

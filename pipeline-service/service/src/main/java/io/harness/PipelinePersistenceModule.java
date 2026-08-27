/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.mongo.helper.MongoConstants.SECONDARY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.persistance.GitSyncablePersistenceConfig;
import io.harness.mongo.MongoConfig;
import io.harness.notification.NotificationChannelPersistenceConfig;
import io.harness.pms.mongo.PipelineBucket;
import io.harness.pms.outbox.PipelineOutboxPersistenceConfig;
import io.harness.springdata.BucketRegistry;
import io.harness.springdata.HMongoTemplate;
import io.harness.springdata.HTransactionTemplate;
import io.harness.springdata.QueryBucket;
import io.harness.springdata.QueryBudgetBindings;
import io.harness.springdata.QueryBudgetMetrics;
import io.harness.springdata.SpringPersistenceModule;
import io.harness.waiter.persistence.WaitNotifyMongoTemplateFactory;
import io.harness.waiter.persistence.WaitNotifySpringPersistenceConfig;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.util.Modules;
import com.mongodb.ReadPreference;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PIPELINE)
public class PipelinePersistenceModule extends AbstractModule {
  @Override
  protected void configure() {
    install(Modules
                .override(new SpringPersistenceModule() {
                  @Override
                  protected void configure() {
                    super.configure();
                    // Opt pipeline-service into the Mongo query-budget framework (PIPE-35957): the bucket enum defines
                    // the known keys, QueryBudgetMetrics turns violation counters on. Both are resolved via
                    // Injector#getExistingBinding in QueryBudgetBindings, so metrics must be bound explicitly (JIT
                    // bindings are invisible to getExistingBinding).
                    bind(new TypeLiteral<Class<? extends QueryBucket>>() {}).toInstance(PipelineBucket.class);
                    bind(QueryBudgetMetrics.class).in(Singleton.class);
                  }

                  @Override
                  protected Class<?>[] getConfigClasses() {
                    return PipelinePersistenceModule.this.getConfigClasses();
                  }
                })
                .with(new AbstractModule() {
                  // Override only to route wait-notify entities through WaitNotifyMongoTemplateFactory
                  // (collection prefix isolation); other @Named(SECONDARY) consumers keep default behavior.
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
    return new Class[] {WaitNotifySpringPersistenceConfig.class, NotificationChannelPersistenceConfig.class,
        GitSyncablePersistenceConfig.class, PipelineOutboxPersistenceConfig.class};
  }

  @Provides
  @Singleton
  protected TransactionTemplate getTransactionTemplate(
      MongoTransactionManager mongoTransactionManager, MongoConfig mongoConfig) {
    return new HTransactionTemplate(mongoTransactionManager, mongoConfig.isTransactionsEnabled());
  }

  @Provides
  @Singleton
  @Named("secondary-mongo")
  protected MongoTemplate getSecondaryMongoTemplate(
      MongoTemplate mongoTemplate, MongoConfig primaryMongoConfig, Injector injector, BucketRegistry bucketRegistry) {
    HMongoTemplate template =
        WaitNotifyMongoTemplateFactory.create(mongoTemplate.getMongoDatabaseFactory(), mongoTemplate.getConverter(),
            primaryMongoConfig, bucketRegistry, QueryBudgetBindings.resolveQueryBudgetMetrics(injector));
    template.setReadPreference(ReadPreference.secondary());
    return template;
  }
}

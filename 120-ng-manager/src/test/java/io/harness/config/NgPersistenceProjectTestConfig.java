/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.config;
import static com.google.inject.Key.get;
import static com.google.inject.name.Names.named;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.springdata.HMongoTemplate;
import io.harness.springdata.SpringPersistenceConfig;

import com.google.inject.Injector;
import dev.morphia.AdvancedDatastore;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.guice.annotation.GuiceModule;

@OwnedBy(HarnessTeam.PL)
@Configuration
@GuiceModule
@EnableMongoRepositories(basePackages = {"io.harness.repositories.ProjectRepository"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class), mongoTemplateRef = "primary")

public class NgPersistenceProjectTestConfig extends SpringPersistenceConfig {
  protected final AdvancedDatastore advancedDatastore;

  public NgPersistenceProjectTestConfig(Injector injector, List<Class<? extends Converter<?, ?>>> springConverters) {
    super(injector, springConverters);
    this.advancedDatastore = injector.getProvider(get(AdvancedDatastore.class, named("primaryDatastore"))).get();
  }

  @Override
  protected String getDatabaseName() {
    return advancedDatastore.getDatabase().getName();
  }

  @Override
  protected boolean autoIndexCreation() {
    return true;
  }

  @NotNull
  @Bean(name = "primary")
  @Primary
  public MongoTemplate mongoTemplate(
      @NotNull MongoDatabaseFactory databaseFactory, @NotNull MappingMongoConverter converter) {
    return new HMongoTemplate(databaseFactory, converter, MongoConfig.builder().build());
  }
}
/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.config;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.springdata.SpringPersistenceConfig;

import com.google.inject.Injector;
import java.util.List;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.guice.annotation.GuiceModule;

@OwnedBy(HarnessTeam.PL)
@Configuration
@GuiceModule
@EnableMongoRepositories(basePackages = {"io.harness.repositories"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class), mongoTemplateRef = "primary")
@EnableMongoAuditing
public class NgSecretPersistenceTestConfig extends SpringPersistenceConfig {
  public NgSecretPersistenceTestConfig(Injector injector, List<Class<? extends Converter<?, ?>>> springConverters) {
    super(injector, springConverters);
  }

  @Override
  protected String getDatabaseName() {
    return "harness_test_db";
  }

  @Override
  protected boolean autoIndexCreation() {
    return true;
  }
}

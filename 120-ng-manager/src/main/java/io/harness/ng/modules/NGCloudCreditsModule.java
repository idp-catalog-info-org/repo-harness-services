/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.modules;

import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.event.service.LicenseUsageHourlyDailyHandler;
import io.harness.ng.core.event.service.LicenseUsageMonthlyYearlyHandler;
import io.harness.ng.core.event.service.LicenseUsageScheduler;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class NGCloudCreditsModule extends AbstractModule {
  long cloudCreditsHourlyJobSchedule;
  long cloudCreditsBatchSize;

  public NGCloudCreditsModule(NextGenConfiguration appConfig) {
    if (appConfig.getCloudCreditsHourlyRollUpBatchSize() == 0) {
      cloudCreditsBatchSize = 100;
    } else {
      cloudCreditsBatchSize = appConfig.getCloudCreditsHourlyRollUpBatchSize();
    }

    if (appConfig.getCloudCreditsHourlyRollUpJobSchedule() == 0) {
      cloudCreditsHourlyJobSchedule = 2;
    } else {
      cloudCreditsHourlyJobSchedule = appConfig.getCloudCreditsHourlyRollUpJobSchedule();
    }
  }

  @Override
  protected void configure() {
    bind(LicenseUsageScheduler.class).toInstance(new LicenseUsageScheduler(cloudCreditsHourlyJobSchedule));
    bind(LicenseUsageHourlyDailyHandler.class).toInstance(new LicenseUsageHourlyDailyHandler(cloudCreditsBatchSize));
    bind(LicenseUsageMonthlyYearlyHandler.class).in(Scopes.SINGLETON);
  }
}

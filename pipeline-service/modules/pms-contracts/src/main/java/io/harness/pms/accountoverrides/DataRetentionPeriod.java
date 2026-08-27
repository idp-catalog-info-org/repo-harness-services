/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.accountoverrides;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.Getter;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(HarnessTeam.PIPELINE)
@Getter
public enum DataRetentionPeriod {
  DATA_RETENTION_PERIOD_6_MONTHS("DATA_RETENTION_PERIOD_6_MONTHS", 6),
  DATA_RETENTION_PERIOD_12_MONTHS("DATA_RETENTION_PERIOD_12_MONTHS", 12),
  DATA_RETENTION_PERIOD_24_MONTHS("DATA_RETENTION_PERIOD_24_MONTHS", 24),
  DATA_RETENTION_PERIOD_7_YEARS("DATA_RETENTION_PERIOD_7_YEARS", 7 * 12);

  private final String name;
  private final int dataRetentionPeriodInMonths;

  DataRetentionPeriod(String name, int dataRetentionPeriodInMonths) {
    this.name = name;
    this.dataRetentionPeriodInMonths = dataRetentionPeriodInMonths;
  }
}

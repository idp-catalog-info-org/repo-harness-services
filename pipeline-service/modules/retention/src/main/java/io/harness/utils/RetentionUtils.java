/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DATA_RETENTION, HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class RetentionUtils {
  public PipelineSearchIndexRetentionPeriods convertDataRetentionPeriodToSearchIndexPeriod(
      DataRetentionPeriod dataRetentionPeriod) {
    switch (dataRetentionPeriod) {
      case DATA_RETENTION_PERIOD_6_MONTHS -> {
        return PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_6_MONTHS;
      }
      case DATA_RETENTION_PERIOD_12_MONTHS -> {
        return PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_12_MONTHS;
      }
      case DATA_RETENTION_PERIOD_24_MONTHS, DATA_RETENTION_PERIOD_7_YEARS -> {
        return PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_24_MONTHS;
      }
      default -> throw new InvalidRequestException(String.format("Provided data retention period is not supported: %s", dataRetentionPeriod));
    }
  }
}

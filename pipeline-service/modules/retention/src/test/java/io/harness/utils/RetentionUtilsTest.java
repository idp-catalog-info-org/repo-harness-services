/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.rule.Owner;
import io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class RetentionUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testConvertDataRetentionPeriodToSearchIndexPeriod() {
    assertThat(RetentionUtils.convertDataRetentionPeriodToSearchIndexPeriod(
                   DataRetentionPeriod.DATA_RETENTION_PERIOD_6_MONTHS))
        .isEqualTo(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_6_MONTHS);
    assertThat(
        RetentionUtils.convertDataRetentionPeriodToSearchIndexPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_7_YEARS))
        .isEqualTo(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_24_MONTHS);
    assertThat(RetentionUtils.convertDataRetentionPeriodToSearchIndexPeriod(
                   DataRetentionPeriod.DATA_RETENTION_PERIOD_24_MONTHS))
        .isEqualTo(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_24_MONTHS);
    assertThat(RetentionUtils.convertDataRetentionPeriodToSearchIndexPeriod(
                   DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS))
        .isEqualTo(PipelineSearchIndexRetentionPeriods.ACCOUNT_RETENTION_12_MONTHS);
  }
}

/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention;

import static io.harness.rule.OwnerRule.MEENA;

import static junit.framework.TestCase.assertTrue;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineRetentionHelperTest extends OrchestrationTestBase {
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  private static final int retentionPeriodInMonths = 6;
  private static final long VALID_UNTIL_6M_LB = 15500000;
  private static final long VALID_UNTIL_6M_UB = 16000000;

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetValidUntilAsDate() {
    Date validUntil = PipelineRetentionHelper.getValidUntilAsDate(retentionPeriodInMonths);
    long validUntilSeconds = Duration.between(LocalDateTime.now(), convertDateToLocalDateTime(validUntil)).getSeconds();
    assertTrue(
        "ValidUntil is within range", validUntilSeconds <= VALID_UNTIL_6M_UB && validUntilSeconds >= VALID_UNTIL_6M_LB);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetValidUntilAsDuration() {
    Duration validUntil = PipelineRetentionHelper.getValidUntilAsDuration(retentionPeriodInMonths);
    long validUntilSeconds = validUntil.getSeconds();
    assertTrue(
        "ValidUntil is within range", validUntilSeconds <= VALID_UNTIL_6M_UB && validUntilSeconds >= VALID_UNTIL_6M_LB);
  }

  private static LocalDateTime convertDateToLocalDateTime(Date date) {
    Instant instant = date.toInstant();
    ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
    return zonedDateTime.toLocalDateTime();
  }
}

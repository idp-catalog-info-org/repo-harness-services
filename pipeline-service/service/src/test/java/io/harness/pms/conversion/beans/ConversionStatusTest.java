/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.beans;

import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConversionStatusTest extends CategoryTest {
  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testIsFinalStatusForFinalStatuses() {
    assertThat(ConversionStatus.isFinalStatus(ConversionStatus.SUCCESS)).isTrue();
    assertThat(ConversionStatus.isFinalStatus(ConversionStatus.FAILED)).isTrue();
    assertThat(ConversionStatus.isFinalStatus(ConversionStatus.PARTIAL_SUCCESS)).isTrue();
    assertThat(ConversionStatus.isFinalStatus(ConversionStatus.SKIPPED)).isTrue();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testIsFinalStatusForNonFinalStatuses() {
    assertThat(ConversionStatus.isFinalStatus(ConversionStatus.QUEUED)).isFalse();
    assertThat(ConversionStatus.isFinalStatus(ConversionStatus.IN_PROGRESS)).isFalse();
  }
}

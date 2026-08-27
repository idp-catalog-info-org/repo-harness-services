/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CIPipelineUtilsTest extends CIExecutionTestBase {
  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void humanReadableByteCountBin_BytesLessThan1024() {
    String result = CIPipelineUtils.humanReadableByteCountBin(512);
    assertThat(result).isEqualTo("512 B");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void humanReadableByteCountBin_Kilobytes() {
    String result = CIPipelineUtils.humanReadableByteCountBin(1024);
    assertThat(result).isEqualTo("1.0 KiB");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void humanReadableByteCountBin_Megabytes() {
    String result = CIPipelineUtils.humanReadableByteCountBin(1048576);
    assertThat(result).isEqualTo("1.0 MiB");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void humanReadableByteCountBin_Gigabytes() {
    String result = CIPipelineUtils.humanReadableByteCountBin(1073741824);
    assertThat(result).isEqualTo("1.0 GiB");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void humanReadableByteCountBin_Terabytes() {
    String result = CIPipelineUtils.humanReadableByteCountBin(1099511627776L);
    assertThat(result).isEqualTo("1.0 TiB");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void humanReadableByteCountBin_Petabytes() {
    String result = CIPipelineUtils.humanReadableByteCountBin(1125899906842624L);
    assertThat(result).isEqualTo("1.0 PiB");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void humanReadableByteCountBin_Exabytes() {
    String result = CIPipelineUtils.humanReadableByteCountBin(1152921504606846976L);
    assertThat(result).isEqualTo("1.0 EiB");
  }
}

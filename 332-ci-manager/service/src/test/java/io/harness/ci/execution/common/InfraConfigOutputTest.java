/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InfraConfigOutputTest {
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testInfraConfigOutputIsExecutionSweepingOutput() {
    InfraConfigOutput output = InfraConfigOutput.builder().build();
    assertThat(output).isInstanceOf(ExecutionSweepingOutput.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testInfraConfigOutputMapOperations() {
    InfraConfigOutput output = InfraConfigOutput.builder().build();
    output.put("connectorRef", "my-connector");
    output.put("namespace", "default");
    assertThat(output).containsKey("connectorRef");
    assertThat(output.get("namespace")).isEqualTo("default");
    assertThat(output).hasSize(2);
  }
}

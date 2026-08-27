/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.category.element.UnitTests;
import io.harness.ci.stdvars.BuildStandardVariables;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CIPipelineStandardVariablesUtilsTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFetchBuildStandardVariables_shouldReturnNonNullResult() {
    CIExecutionArgs ciExecutionArgs = CIExecutionArgs.builder().runSequence("1").build();

    BuildStandardVariables result = CIPipelineStandardVariablesUtils.fetchBuildStandardVariables(ciExecutionArgs);

    assertThat(result).as("Should return a non-null BuildStandardVariables instance").isNotNull();
    assertThat(result.getNumber()).as("Number should be null in the default builder output").isNull();
    assertThat(result.getGit()).as("Git should be null in the default builder output").isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testFetchBuildStandardVariables_withNullInput_shouldReturnNonNullResult() {
    BuildStandardVariables result = CIPipelineStandardVariablesUtils.fetchBuildStandardVariables(null);

    assertThat(result).as("Should return a non-null BuildStandardVariables even with null input").isNotNull();
    assertThat(result.getNumber()).as("Number should be null in the default builder output").isNull();
    assertThat(result.getGit()).as("Git should be null in the default builder output").isNull();
  }
}

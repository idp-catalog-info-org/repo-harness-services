/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ErrorCodeEnumTest extends CategoryTest {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testErrorCodesInApiEnum() {
    Set<String> errorCodeSet = Arrays.stream(ErrorCode.values()).map(Enum::toString).collect(Collectors.toSet());
    Set<String> errorCodeApiSet = Arrays.stream(io.harness.spec.server.pipeline.v1.model.ErrorCode.values())
                                      .map(Enum::toString)
                                      .collect(Collectors.toSet());
    assertEquals(errorCodeApiSet, errorCodeSet);
    for (ErrorCode s : ErrorCode.values()) {
      assertEquals(PipelineExecutionDetailsApiUtils.toCodeV1(s).toString(), s.toString());
    }
  }
}

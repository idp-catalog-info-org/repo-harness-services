/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.FailureType;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FailureTypeEnumTest extends CategoryTest {
  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testFailureTypesInApiEnum() {
    Set<String> failureTypeSet = Arrays.stream(FailureType.values()).map(Enum::toString).collect(Collectors.toSet());
    Set<String> failureTypeApiSet = Arrays.stream(io.harness.spec.server.pipeline.v1.model.FailureType.values())
                                        .map(Enum::toString)
                                        .collect(Collectors.toSet());
    assertEquals(failureTypeApiSet, failureTypeSet);
    for (FailureType failureType : FailureType.values()) {
      assertEquals(PipelineExecutionDetailsApiUtils.toFailureTypeV1(failureType).toString(), failureType.toString());
    }
  }
}

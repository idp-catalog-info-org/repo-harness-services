/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static junit.framework.TestCase.assertEquals;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@OwnedBy(HarnessTeam.CI)
public class AnnotationUtils {
  public static void assertParameterCounts(Method method, int expectedCount, Class<? extends Annotation> annotation) {
    Parameter[] parameters = method.getParameters();
    int count = 0;
    for (Parameter parameter : parameters) {
      if (parameter.isAnnotationPresent(annotation)) {
        count++;
      }
    }
    assertEquals(expectedCount, count);
  }
}

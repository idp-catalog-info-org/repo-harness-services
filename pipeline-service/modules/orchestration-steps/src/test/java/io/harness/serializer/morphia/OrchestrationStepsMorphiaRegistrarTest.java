/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.rule.OwnerRule.GONZALO;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class OrchestrationStepsMorphiaRegistrarTest extends CategoryTest {
  @Test
  @Owner(developers = GONZALO)
  @Category(UnitTests.class)
  public void testRegisterClasses() {
    OrchestrationStepsMorphiaRegistrar registrar = new OrchestrationStepsMorphiaRegistrar();
    Set<Class> registeredClasses = new HashSet<>();
    registrar.registerClasses(registeredClasses);
    assertThat(registeredClasses).hasSize(27);
  }
}

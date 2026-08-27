/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.utils;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ConstantsTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConstantsValues() {
    assertThat(Constants.USERNAME_AND_TOKEN).isEqualTo("UsernameToken");
    assertThat(Constants.USERNAME_PASSWORD).isEqualTo("UsernamePassword");
    assertThat(Constants.GITHUB_APP).isEqualTo("GitHubApp");
    assertThat(Constants.MANAGED_TOKEN).isEqualTo("ManagedToken");

    assertThat(Constants.IDP_GIT_INTEGRATION_MANAGED_HCR).isEqualTo("IDP_GIT_INTEGRATION_MANAGED_HCR");
    assertThat(Constants.IDP_MANAGED_HCR_WRITE).isEqualTo("IDP_MANAGED_HCR_WRITE");
    assertThat(Constants.HCR_CONNECTOR_IDENTIFIER).isEqualTo("__hcr__");

    assertThat(Constants.HARNESS_CD_CATALOG_INTEGRATION).isEqualTo("_harness_cd");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAllConstantsAreFinalAndStatic() throws IllegalAccessException {
    Field[] fields = Constants.class.getDeclaredFields();

    for (Field field : fields) {
      int modifiers = field.getModifiers();

      if (field.getName().startsWith("$")) {
        continue;
      }

      assertThat(Modifier.isPublic(modifiers)).as("Field %s should be public", field.getName()).isTrue();
      assertThat(Modifier.isStatic(modifiers)).as("Field %s should be static", field.getName()).isTrue();
      assertThat(Modifier.isFinal(modifiers)).as("Field %s should be final", field.getName()).isTrue();

      field.setAccessible(true);
      Object value = field.get(null);
      assertThat(value).as("Field %s should not be null", field.getName()).isNotNull();
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUtilityClassIsNotInstantiable() {
    Constructor<?>[] constructors = Constants.class.getDeclaredConstructors();

    assertThat(constructors).hasSize(1);
    Constructor<?> constructor = constructors[0];
    assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

    constructor.setAccessible(true);
    try {
      constructor.newInstance();
      assertThat(false).as("Should not be able to instantiate utility class").isTrue();
    } catch (Exception e) {
      assertThat(e.getCause()).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConstantNaming() {
    Field[] fields = Constants.class.getDeclaredFields();

    for (Field field : fields) {
      if (field.getName().startsWith("$")) {
        continue;
      }

      String fieldName = field.getName();
      assertThat(fieldName).as("Field %s should be in UPPER_SNAKE_CASE", fieldName).matches("^[A-Z][A-Z0-9_]*$");
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConstantsAreString() {
    Field[] fields = Constants.class.getDeclaredFields();

    for (Field field : fields) {
      if (field.getName().startsWith("$")) {
        continue;
      }

      assertThat(field.getType()).as("Field %s should be of type String", field.getName()).isEqualTo(String.class);
    }
  }
}

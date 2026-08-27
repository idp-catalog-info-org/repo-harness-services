/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.common;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.BaseIntegrationResponse;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.IDP)
public class CommonIntegrationServiceTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGenericTypeParameters() {
    Type[] typeParameters = CommonIntegrationService.class.getTypeParameters();
    assertThat(typeParameters).hasSize(2);

    assertThat(typeParameters[0].toString()).contains("T");
    assertThat(typeParameters[1].toString()).contains("U");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSaveMethodExists() {
    Method[] methods = CommonIntegrationService.class.getDeclaredMethods();

    boolean saveMethodFound = Arrays.stream(methods).anyMatch(m
        -> m.getName().equals("save") && m.getParameterCount() == 4 && m.getParameterTypes()[0].equals(String.class)
            && m.getParameterTypes()[1].equals(BaseIntegrationRequest.class)
            && m.getParameterTypes()[2].equals(boolean.class) && m.getParameterTypes()[3].equals(boolean.class));

    assertThat(saveMethodFound).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateMethodExists() {
    Method[] methods = CommonIntegrationService.class.getDeclaredMethods();

    boolean updateMethodFound = Arrays.stream(methods).anyMatch(m
        -> m.getName().equals("update") && m.getParameterCount() == 4 && m.getParameterTypes()[0].equals(String.class)
            && m.getParameterTypes()[1].equals(String.class)
            && m.getParameterTypes()[2].equals(BaseIntegrationRequest.class)
            && m.getParameterTypes()[3].equals(boolean.class));

    assertThat(updateMethodFound).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSaveOrUpdateMethodExists() {
    Method[] methods = CommonIntegrationService.class.getDeclaredMethods();

    boolean saveOrUpdateMethodFound = Arrays.stream(methods).anyMatch(m
        -> m.getName().equals("saveOrUpdate") && m.getParameterCount() == 2
            && m.getParameterTypes()[0].equals(String.class)
            && m.getParameterTypes()[1].equals(BaseIntegrationRequest.class));

    assertThat(saveOrUpdateMethodFound).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetMethodsExist() {
    Method[] methods = CommonIntegrationService.class.getDeclaredMethods();
    boolean getWithPaginationFound = Arrays.stream(methods).anyMatch(m
        -> m.getName().equals("get") && m.getParameterCount() == 3 && m.getParameterTypes()[0].equals(String.class)
            && m.getParameterTypes()[1].equals(Pageable.class) && m.getParameterTypes()[2].equals(String.class)
            && m.getReturnType().equals(List.class));

    boolean getWithIdentifierFound = Arrays.stream(methods).anyMatch(m
        -> m.getName().equals("get") && m.getParameterCount() == 2 && m.getParameterTypes()[0].equals(String.class)
            && m.getParameterTypes()[1].equals(String.class)
            && m.getReturnType().equals(BaseIntegrationResponse.class));

    assertThat(getWithPaginationFound).isTrue();
    assertThat(getWithIdentifierFound).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDeleteMethodsExist() {
    Method[] methods = CommonIntegrationService.class.getDeclaredMethods();

    boolean deleteWithIdentifierFound = Arrays.stream(methods).anyMatch(m
        -> m.getName().equals("delete") && m.getParameterCount() == 3 && m.getParameterTypes()[0].equals(String.class)
            && m.getParameterTypes()[1].equals(String.class) && m.getParameterTypes()[2].equals(boolean.class)
            && m.getReturnType().equals(void.class));

    boolean deleteAccountFound = Arrays.stream(methods).anyMatch(m
        -> m.getName().equals("delete") && m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(String.class)
            && m.getReturnType().equals(void.class));

    assertThat(deleteWithIdentifierFound).isTrue();
    assertThat(deleteAccountFound).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReturnTypes() {
    Method[] methods = CommonIntegrationService.class.getDeclaredMethods();

    for (Method method : methods) {
      switch (method.getName()) {
        case "save":
        case "update":
        case "saveOrUpdate":
          if (method.getParameterCount() != 3 || !method.getParameterTypes()[1].equals(Pageable.class)) {
            assertThat(method.getReturnType())
                .as("Method %s should return BaseIntegrationResponse", method.getName())
                .isEqualTo(BaseIntegrationResponse.class);
          }
          break;
        case "get":
          if (method.getParameterTypes().length > 2 && method.getParameterTypes()[1].equals(Pageable.class)) {
            assertThat(method.getReturnType())
                .as("Method get with Pageable should return List", method.getName())
                .isEqualTo(List.class);
          } else {
            assertThat(method.getReturnType())
                .as("Method get with identifier should return BaseIntegrationResponse", method.getName())
                .isEqualTo(BaseIntegrationResponse.class);
          }
          break;
        case "delete":
          assertThat(method.getReturnType()).as("Method %s should return void", method.getName()).isEqualTo(void.class);
          break;
      }
    }
  }
}

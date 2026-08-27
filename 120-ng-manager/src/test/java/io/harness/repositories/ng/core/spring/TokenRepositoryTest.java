/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ng.core.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.repositories.ng.core.custom.TokenCustomRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

public class TokenRepositoryTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void testTokenRepositoryExtendsRequiredInterfaces() {
    assertThat(PagingAndSortingRepository.class.isAssignableFrom(TokenRepository.class)).isTrue();
    assertThat(CrudRepository.class.isAssignableFrom(TokenRepository.class)).isTrue();
    assertThat(TokenCustomRepository.class.isAssignableFrom(TokenRepository.class)).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void testDeleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierMethodExists()
      throws NoSuchMethodException {
    Method method =
        TokenRepository.class.getMethod("deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifier",
            String.class, String.class, io.harness.ng.core.common.beans.ApiKeyType.class, String.class);
    assertThat(method).isNotNull();
    assertThat(method.getReturnType()).isEqualTo(long.class);
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void
  testDeleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierMethodExists()
      throws NoSuchMethodException {
    Method method = TokenRepository.class.getMethod(
        "deleteAllByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifier",
        String.class, String.class, io.harness.ng.core.common.beans.ApiKeyType.class, String.class, String.class);
    assertThat(method).isNotNull();
    assertThat(method.getReturnType()).isEqualTo(long.class);
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void
  testFindByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifierMethodExists()
      throws NoSuchMethodException {
    Method method = TokenRepository.class.getMethod(
        "findByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier",
        String.class, String.class, io.harness.ng.core.common.beans.ApiKeyType.class, String.class, String.class,
        String.class);
    assertThat(method).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void
  testCountByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierMethodExists()
      throws NoSuchMethodException {
    Method method = TokenRepository.class.getMethod(
        "countByAccountIdentifierAndParentUniqueIdAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifier", String.class,
        String.class, io.harness.ng.core.common.beans.ApiKeyType.class, String.class, String.class);
    assertThat(method).isNotNull();
    assertThat(method.getReturnType()).isEqualTo(long.class);
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void testCountByAccountIdentifierMethodExists() throws NoSuchMethodException {
    Method method = TokenRepository.class.getMethod("countByAccountIdentifier", String.class);
    assertThat(method).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifierMethodExists()
      throws NoSuchMethodException {
    Method method =
        TokenRepository.class.getMethod("findByAccountIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier",
            String.class, io.harness.ng.core.common.beans.ApiKeyType.class, String.class, String.class);
    assertThat(method).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void
  testFindByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifierMethodExists()
      throws NoSuchMethodException {
    Method method = TokenRepository.class.getMethod(
        "findByAccountIdentifierAndParentUniqueIdAndParentIdentifierAndApiKeyIdentifierAndIdentifier", String.class,
        String.class, String.class, String.class, String.class);
    assertThat(method).isNotNull();
  }
}

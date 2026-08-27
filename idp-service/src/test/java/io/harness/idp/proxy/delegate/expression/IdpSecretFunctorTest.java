/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate.expression;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class IdpSecretFunctorTest extends CategoryTest {
  private static final long EXPRESSION_FUNCTOR_TOKEN = 12345L;
  private static final String SECRET_IDENTIFIER = "test-secret";

  IdpSecretFunctor idpSecretFunctor;

  @Before
  public void setUp() {
    idpSecretFunctor = new IdpSecretFunctor(EXPRESSION_FUNCTOR_TOKEN);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetValueWithValidSecretIdentifier() {
    Object result = idpSecretFunctor.getValue(SECRET_IDENTIFIER);
    assertNotNull(result);
    String secretExpression = result.toString();
    assertEquals(true, secretExpression.contains(SECRET_IDENTIFIER));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetValueWithEmptySecretIdentifier() {
    idpSecretFunctor.getValue("");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetValueWithNullSecretIdentifier() {
    idpSecretFunctor.getValue(null);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetValueWithDifferentSecretIdentifiers() {
    String secret1 = "secret-1";
    String secret2 = "secret-2";

    Object result1 = idpSecretFunctor.getValue(secret1);
    Object result2 = idpSecretFunctor.getValue(secret2);

    assertNotNull(result1);
    assertNotNull(result2);
    assertEquals(true, result1.toString().contains(secret1));
    assertEquals(true, result2.toString().contains(secret2));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testGetValueWithSpecialCharacters() {
    String secretWithSpecialChars = "test-secret_123";
    Object result = idpSecretFunctor.getValue(secretWithSpecialChars);
    assertNotNull(result);
    assertEquals(true, result.toString().contains(secretWithSpecialChars));
  }
}

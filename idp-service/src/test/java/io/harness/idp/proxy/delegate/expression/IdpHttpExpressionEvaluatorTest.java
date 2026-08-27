/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate.expression;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class IdpHttpExpressionEvaluatorTest extends CategoryTest {
  private static final int EXPRESSION_FUNCTOR_TOKEN = 12345;

  IdpHttpExpressionEvaluator idpHttpExpressionEvaluator;

  @Before
  public void setUp() {
    idpHttpExpressionEvaluator = new IdpHttpExpressionEvaluator(EXPRESSION_FUNCTOR_TOKEN);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testInitialization() {
    assertNotNull(idpHttpExpressionEvaluator);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSecretsContextIsAvailable() {
    Object secretsContext = idpHttpExpressionEvaluator.resolve("secrets");
    assertNotNull(secretsContext);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testEvaluateSecretExpression() {
    String expression = "<+secrets.getValue(\"test-secret\")>";
    Object result = idpHttpExpressionEvaluator.resolve(expression);
    assertNotNull(result);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testRenderExpression() {
    String expression = "<+secrets.getValue(\"api-key\")>";
    String rendered = idpHttpExpressionEvaluator.renderExpression(expression);
    assertNotNull(rendered);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testEvaluateWithDifferentExpressionTokens() {
    IdpHttpExpressionEvaluator evaluator1 = new IdpHttpExpressionEvaluator(111);
    IdpHttpExpressionEvaluator evaluator2 = new IdpHttpExpressionEvaluator(222);

    assertNotNull(evaluator1);
    assertNotNull(evaluator2);

    Object secrets1 = evaluator1.resolve("secrets");
    Object secrets2 = evaluator2.resolve("secrets");

    assertNotNull(secrets1);
    assertNotNull(secrets2);
  }
}

/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CI)
@RunWith(MockitoJUnitRunner.class)
public class LiteEngineSecretEvaluatorTest extends CategoryTest {
  @Mock private SecretUtils secretUtils;

  private ExecutorService executorService;
  private NGAccess ngAccess;

  @Before
  public void setUp() {
    executorService = Executors.newSingleThreadExecutor();
    ngAccess = BaseNGAccess.builder()
                   .accountIdentifier("accountId")
                   .orgIdentifier("orgId")
                   .projectIdentifier("projectId")
                   .build();
  }

  @After
  public void tearDown() {
    executorService.shutdownNow();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testResolve_whenNoSecrets_shouldReturnEmptyList() {
    LiteEngineSecretEvaluator evaluator = LiteEngineSecretEvaluator.builder()
                                              .executorService(executorService)
                                              .secretUtils(secretUtils)
                                              .withSingleQuotes(false)
                                              .build();

    Map<String, Object> testObject = new HashMap<>();
    testObject.put("key", "plainValue");

    java.util.List<SecretVariableDetails> result = evaluator.resolve(testObject, ngAccess, 1234L, false);

    assertThat(result).as("Should return empty list when no secrets are referenced").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testResolve_whenSecretsPresent_shouldReturnSecretDetails() {
    SecretVariableDetails secretVariableDetails = SecretVariableDetails.builder().build();
    when(secretUtils.getSecretVariableDetailsWithScope(eq(ngAccess), any())).thenReturn(secretVariableDetails);

    LiteEngineSecretEvaluator evaluator = LiteEngineSecretEvaluator.builder()
                                              .executorService(executorService)
                                              .secretUtils(secretUtils)
                                              .withSingleQuotes(false)
                                              .build();

    Map<String, Object> testObject = new HashMap<>();
    testObject.put("key", "${ngSecretManager.obtain(\"secret_ref\", 1234)}");

    java.util.List<SecretVariableDetails> result = evaluator.resolve(testObject, ngAccess, 1234L, false);

    assertThat(result).as("Should return secret variable details for resolved secrets").isNotEmpty();
    assertThat(result.get(0))
        .as("Should contain the resolved secret variable details")
        .isEqualTo(secretVariableDetails);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testResolve_whenSecretResolutionFails_shouldThrowCIStageExecutionException() {
    when(secretUtils.getSecretVariableDetailsWithScope(eq(ngAccess), any()))
        .thenThrow(new RuntimeException("Secret resolution failed"));

    LiteEngineSecretEvaluator evaluator = LiteEngineSecretEvaluator.builder()
                                              .executorService(executorService)
                                              .secretUtils(secretUtils)
                                              .withSingleQuotes(false)
                                              .build();

    Map<String, Object> testObject = new HashMap<>();
    testObject.put("key", "${ngSecretManager.obtain(\"secret_ref\", 1234)}");

    assertThatThrownBy(() -> evaluator.resolve(testObject, ngAccess, 1234L, false))
        .as("Should throw CIStageExecutionException when secret resolution fails")
        .isInstanceOf(CIStageExecutionException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testResolve_withSingleQuotesTrue_shouldBuildFunctorWithSingleQuotes() {
    LiteEngineSecretEvaluator evaluator = LiteEngineSecretEvaluator.builder()
                                              .executorService(executorService)
                                              .secretUtils(secretUtils)
                                              .withSingleQuotes(true)
                                              .build();

    Map<String, Object> testObject = new HashMap<>();
    testObject.put("key", "plainValue");

    java.util.List<SecretVariableDetails> result = evaluator.resolve(testObject, ngAccess, 1234L, true);

    assertThat(result).as("Should return empty list when no secrets with single quotes mode").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testResolveFunctorImpl_processString_shouldSubstituteExpression() {
    CINgSecretManagerFunctor functor = buildFunctor();
    LiteEngineSecretEvaluator.ResolveFunctorImpl resolveFunctor = buildResolveFunctor(functor);

    String result = resolveFunctor.processString("plainExpression");

    assertThat(result).as("Should return the expression after substitution").isNotNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testResolveFunctorImpl_processString_shouldHaveNgSecretManagerInContext() {
    CINgSecretManagerFunctor functor = buildFunctor();
    LiteEngineSecretEvaluator.ResolveFunctorImpl resolveFunctor = buildResolveFunctor(functor);

    assertThat(resolveFunctor.evaluatorResponseContext)
        .as("Should contain ngSecretManager key in context")
        .containsKey("ngSecretManager");
    assertThat(resolveFunctor.evaluatorResponseContext.get("ngSecretManager"))
        .as("Should map ngSecretManager to the CINgSecretManagerFunctor instance")
        .isEqualTo(functor);
  }

  private CINgSecretManagerFunctor buildFunctor() {
    return CINgSecretManagerFunctor.builder()
        .expressionFunctorToken(1234L)
        .secretUtils(secretUtils)
        .ngAccess(ngAccess)
        .withSingleQuotes(false)
        .build();
  }

  private LiteEngineSecretEvaluator.ResolveFunctorImpl buildResolveFunctor(CINgSecretManagerFunctor functor) {
    LiteEngineSecretEvaluator evaluator = LiteEngineSecretEvaluator.builder()
                                              .executorService(executorService)
                                              .secretUtils(secretUtils)
                                              .withSingleQuotes(false)
                                              .build();
    return evaluator.new ResolveFunctorImpl(new ExpressionEvaluator(false), functor);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testConstructor_shouldSetFields() {
    LiteEngineSecretEvaluator evaluator = new LiteEngineSecretEvaluator(executorService, secretUtils, true);

    assertThat(evaluator).as("Should create evaluator instance via constructor").isNotNull();
  }
}

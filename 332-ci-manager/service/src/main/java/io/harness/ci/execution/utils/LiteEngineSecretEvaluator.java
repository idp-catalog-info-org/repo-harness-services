/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.context.GlobalContext;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.ExpressionResolveFunctor;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.NGAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class LiteEngineSecretEvaluator extends ExpressionEvaluator {
  private ExecutorService executorService;
  private SecretUtils secretUtils;
  private boolean withSingleQuotes;
  public List<SecretVariableDetails> resolve(
      Object o, NGAccess ngAccess, long token, boolean skipNonExpressionEvaluation) {
    CINgSecretManagerFunctor ciNgSecretManagerFunctor = CINgSecretManagerFunctor.builder()
                                                            .expressionFunctorToken(token)
                                                            .secretUtils(secretUtils)
                                                            .ngAccess(ngAccess)
                                                            .withSingleQuotes(withSingleQuotes)
                                                            .build();

    ResolveFunctorImpl resolveFunctor =
        new ResolveFunctorImpl(new ExpressionEvaluator(skipNonExpressionEvaluation), ciNgSecretManagerFunctor);

    ExpressionEvaluatorUtils.updateExpressions(o, resolveFunctor);

    // Capture context from main thread for OIDC propagation
    GlobalContext globalContext = GlobalContextManager.obtainGlobalContext();
    CompletableFutures<SecretVariableDetails> completableFutures = new CompletableFutures<>(executorService);
    if (globalContext == null) {
      log.debug("OIDC context not set - Vault granular claims may not be propagated");
    }
    ciNgSecretManagerFunctor.getSecretNGVariableDetails().forEach(v -> completableFutures.supplyAsync(() -> {
      // Restore context in executor thread
      try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.initGlobalContextGuard(globalContext)) {
        return secretUtils.getSecretVariableDetailsWithScope(ngAccess, v);
      }
    }));
    try {
      return new ArrayList<>(completableFutures.allOf().get(5, TimeUnit.MINUTES));
    } catch (Exception e) {
      throw new CIStageExecutionException(e.getMessage());
    }
  }

  public LiteEngineSecretEvaluator(ExecutorService executorService, SecretUtils secretUtils, boolean withSingleQuotes) {
    this.executorService = executorService;
    this.secretUtils = secretUtils;
    this.withSingleQuotes = withSingleQuotes;
  }

  public class ResolveFunctorImpl implements ExpressionResolveFunctor {
    private final ExpressionEvaluator expressionEvaluator;
    final Map<String, Object> evaluatorResponseContext = new HashMap<>(1);

    public ResolveFunctorImpl(
        ExpressionEvaluator expressionEvaluator, CINgSecretManagerFunctor ciNgSecretManagerFunctor) {
      this.expressionEvaluator = expressionEvaluator;
      evaluatorResponseContext.put("ngSecretManager", ciNgSecretManagerFunctor);
    }

    @Override
    public String processString(String expression) {
      return expressionEvaluator.substitute(expression, evaluatorResponseContext);
    }
  }
}

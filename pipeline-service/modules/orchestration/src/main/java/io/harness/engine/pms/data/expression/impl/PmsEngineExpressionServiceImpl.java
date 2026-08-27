/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data.expression.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.expressions.functors.type.NodeExecutionEntityType;
import io.harness.engine.expressions.provider.ExpressionEvaluatorProvider;
import io.harness.engine.expressions.usages.expressiontracker.AutoCloseablePipelineExpressionTracker;
import io.harness.engine.expressions.usages.service.ExecutionExpressionUsageService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.expression.AutoCloseableExpressionTracker;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.VariableResolverTracker;
import io.harness.expression.common.ExpressionConfig;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PmsEngineExpressionServiceImpl implements PmsEngineExpressionService {
  @Inject private ExpressionEvaluatorProvider expressionEvaluatorProvider;
  @Inject private Injector injector;
  @Inject private ExecutionExpressionUsageService expressionUsageService;
  @Inject @Named("ExpressionUsageExecutorService") ExecutorService executorService;
  @Inject private ScopeInfoClient scopeInfoClient;

  @Override
  public String renderExpression(Ambiance ambiance, String expression, boolean skipUnresolvedExpressionsCheck) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance, false);
      String resolvedExpressionWithJexl = null;
      try {
        resolvedExpressionWithJexl = evaluator.renderExpression(expression, skipUnresolvedExpressionsCheck);
      } catch (Exception ex) {
        log.error(String.format("Error while resolving expression %s with jexl", expression), ex);
      }
      evaluator = prepareExpressionEvaluator(ambiance, true);
      if (resolvedExpressionWithJexl != null) {
        return evaluator.renderExpression(resolvedExpressionWithJexl, skipUnresolvedExpressionsCheck);
      }
      return evaluator.renderExpression(expression, skipUnresolvedExpressionsCheck);
    }
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance);
    return evaluator.renderExpression(expression, skipUnresolvedExpressionsCheck);
  }

  @Override
  public String renderExpression(Ambiance ambiance, String expression, ExpressionMode expressionMode) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance, false);
      String resolvedExpressionWithJexl = null;
      try {
        resolvedExpressionWithJexl = evaluator.renderExpression(expression, expressionMode);
      } catch (Exception ex) {
        log.error(String.format("Error while resolving expression %s with jexl", expression), ex);
      }
      evaluator = prepareExpressionEvaluator(ambiance, true);
      if (resolvedExpressionWithJexl != null) {
        return evaluator.renderExpression(resolvedExpressionWithJexl, expressionMode);
      }
      return evaluator.renderExpression(expression, expressionMode);
    }
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance);
    return evaluator.renderExpression(expression, expressionMode);
  }

  @Override
  @Deprecated
  public Object resolve(Ambiance ambiance, Object o, boolean skipUnresolvedExpressionsCheck) {
    return resolve(ambiance, o, EngineExpressionEvaluator.calculateExpressionMode(skipUnresolvedExpressionsCheck));
  }

  @Override
  public Object resolve(Ambiance ambiance, Object o, ExpressionMode expressionMode) {
    ExpressionConfig getExpressionConfig = getExpressionConfig(ambiance);
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance, false);
      Object resolvedExpressionWithJexl = null;
      try {
        resolvedExpressionWithJexl = evaluator.resolve(o, expressionMode, getExpressionConfig);
      } catch (Exception ex) {
        log.error(String.format("Error while resolving expression %s with jexl", o), ex);
      }
      evaluator = prepareExpressionEvaluator(ambiance, true);
      if (resolvedExpressionWithJexl != null) {
        return evaluator.resolve(resolvedExpressionWithJexl, expressionMode, getExpressionConfig);
      }
      return evaluator.resolve(o, expressionMode, getExpressionConfig);
    }
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance);
    return evaluator.resolve(o, expressionMode, getExpressionConfig);
  }

  private ExpressionConfig getExpressionConfig(Ambiance ambiance) {
    return ExpressionConfig.builder().useFallbackForConcurrentModificationException(true).build();
  }

  @Override
  public Object resolve(Ambiance ambiance, Object o, ExpressionMode expressionMode, Map<String, Object> contextMap) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance, contextMap, null, false);
      Object resolvedExpressionWithJexl = null;
      try {
        resolvedExpressionWithJexl = evaluator.resolve(o, expressionMode);
      } catch (Exception ex) {
        log.error(String.format("Error while resolving expression %s with jexl", o), ex);
      }
      evaluator = prepareExpressionEvaluator(ambiance, contextMap, null, true);
      if (resolvedExpressionWithJexl != null) {
        return evaluator.resolve(resolvedExpressionWithJexl, expressionMode);
      }
      return evaluator.resolve(o, expressionMode);
    }
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance, contextMap, null, false);
    return evaluator.resolve(o, expressionMode);
  }

  @Override
  public Object evaluateExpression(
      Ambiance ambiance, String expression, ExpressionMode expressionMode, Map<String, Object> contextMap) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance, contextMap, null, false);
      Object resolvedExpressionWithJexl = null;
      try {
        resolvedExpressionWithJexl = evaluator.resolve(expression, expressionMode);
      } catch (Exception ex) {
        log.error(String.format("Error while resolving expression %s with jexl", expression), ex);
      }
      evaluator = prepareExpressionEvaluator(ambiance, contextMap, null, true);
      if (resolvedExpressionWithJexl != null) {
        return evaluator.resolve(resolvedExpressionWithJexl, expressionMode);
      }
      return evaluator.resolve(expression, expressionMode);
    }
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance, contextMap, null, false);
    return evaluator.evaluateExpression(expression, expressionMode);
  }

  @Override
  public EngineExpressionEvaluator prepareExpressionEvaluator(
      Ambiance ambiance, Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, boolean isCel) {
    return prepareExpressionEvaluator(ambiance, null, entityTypes, refObjectSpecific, isCel);
  }

  @Override
  public EngineExpressionEvaluator prepareExpressionEvaluator(
      Ambiance ambiance, Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific) {
    return prepareExpressionEvaluator(ambiance, null, entityTypes, refObjectSpecific, false);
  }

  private EngineExpressionEvaluator prepareExpressionEvaluator(
      Ambiance ambiance, Map<String, Object> contextMap, Set<NodeExecutionEntityType> entityTypes, boolean isCel) {
    return prepareExpressionEvaluator(ambiance, contextMap, entityTypes, false, isCel);
  }

  private EngineExpressionEvaluator prepareExpressionEvaluator(Ambiance ambiance, Map<String, Object> contextMap,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, boolean isCel) {
    contextMap = addFFToContext(ambiance, contextMap);

    AutoCloseableExpressionTracker expressionTracker;
    if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.CDS_SAVE_EXECUTION_EXPRESSIONS.toString())) {
      expressionTracker = new AutoCloseablePipelineExpressionTracker(
          ambiance, new VariableResolverTracker(), expressionUsageService, executorService, scopeInfoClient);
    } else {
      expressionTracker = new AutoCloseableExpressionTracker(null);
    }
    EngineExpressionEvaluator engineExpressionEvaluator =
        expressionEvaluatorProvider.get(ambiance, entityTypes, refObjectSpecific, contextMap, expressionTracker, isCel);
    injector.injectMembers(engineExpressionEvaluator);
    return engineExpressionEvaluator;
  }

  private Map<String, Object> addFFToContext(Ambiance ambiance, Map<String, Object> contextMap) {
    List<String> enabledFeatureFlags = AmbianceUtils.getEnabledFeatureFlags(ambiance);
    if (contextMap != null) {
      String enabledFFsString = (String) contextMap.get(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY);
      if (isNotEmpty(enabledFFsString)) {
        enabledFeatureFlags.addAll(List.of(enabledFFsString.split(",")));
      }
    }
    if (AmbianceUtils.shouldUseExpressionEngineV2(ambiance)) {
      enabledFeatureFlags.add(EngineExpressionEvaluator.PIE_EXECUTION_JSON_SUPPORT);
    }
    if (isNotEmpty(enabledFeatureFlags)) {
      if (contextMap == null) {
        contextMap = new HashMap<>();
      }
      contextMap.put(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY, String.join(",", enabledFeatureFlags));
    }
    return contextMap;
  }
}

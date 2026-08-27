/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.usages.expressiontracker;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.expressions.constants.OrchestrationConstants;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity;
import io.harness.engine.expressions.usages.beans.ExpressionValueMetadata;
import io.harness.engine.expressions.usages.service.ExecutionExpressionUsageService;
import io.harness.exception.InternalServerErrorException;
import io.harness.expression.AutoCloseableExpressionTracker;
import io.harness.expression.VariableResolverTracker;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class AutoCloseablePipelineExpressionTracker extends AutoCloseableExpressionTracker {
  private final ExecutorService executorService;
  private final ExecutionExpressionUsageService expressionUsageService;
  private final Ambiance ambiance;
  private final ScopeInfoClient scopeInfoClient;
  public AutoCloseablePipelineExpressionTracker(Ambiance ambiance, VariableResolverTracker variableResolverTracker,
      ExecutionExpressionUsageService expressionUsageService, ExecutorService executorService,
      ScopeInfoClient scopeInfoClient) {
    super(variableResolverTracker);
    this.ambiance = ambiance;
    this.scopeInfoClient = scopeInfoClient;
    if (executorService == null) {
      throw new InternalServerErrorException("ExecutorService cannot be null");
    }
    this.executorService = executorService;
    this.expressionUsageService = expressionUsageService;
  }

  @Override
  public void close() {
    try {
      executorService.submit(() -> expressionUsageService.saveExpressions(getExpressionObjects()));
    } catch (Exception e) {
      log.error(String.format("Unable to save expressions to DB with error - %s", e.getMessage()), e);
    }
  }

  private List<ExecutionExpressionUsagesEntity> getExpressionObjects() {
    List<ExecutionExpressionUsagesEntity> result = new LinkedList<>();

    String parentUniqueId = null;
    if (AmbianceUtils.getParentUniqueIdentifier(ambiance) != null) {
      parentUniqueId = AmbianceUtils.getParentUniqueIdentifier(ambiance);
    } else {
      log.error("ParentUniqueId is not set in the ambiance");
      ScopeInfo scopeInfo = NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(AmbianceUtils.getAccountId(ambiance),
          AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance)));
      parentUniqueId = scopeInfo.getUniqueId();
    }
    for (Map.Entry<String, Map<Object, Integer>> entry : getVariableResolverTracker().getUsage().entrySet()) {
      try {
        Set<Object> expressionValueSet = entry.getValue().keySet();
        String expression = entry.getKey();
        if (ignoreExpressions(expression)) {
          continue;
        }
        Optional<Object> optionalValue = Optional.empty();
        for (Object value : expressionValueSet) {
          if (value != null) {
            optionalValue = Optional.of(value);
            break;
          }
        }
        Object expressionValue = null;
        if (optionalValue.isPresent()) {
          expressionValue = optionalValue.get();
        }
        if (expressionValueSizeAboveLimits(expressionValue)) {
          expressionValue = "REFER_ORIGINAL_VALUE";
        }
        if (ignoreSensitiveExpressionValue(expressionValue)) {
          continue;
        }
        result.add(getAnExpressionObject(
            expression, expressionValue, entry.getValue().get(expressionValue), false, parentUniqueId));
      } catch (Exception e) {
        log.error(String.format("Unable to fetch expression objects for given expression - %s", entry.getKey()), e);
      }
    }

    // add Errored Expressions
    for (String failedExpression : getVariableResolverTracker().getFailedExpressions()) {
      result.add(getAnExpressionObject(failedExpression, null, 1, true, parentUniqueId));
    }

    return result;
  }

  private boolean ignoreExpressions(String expression) {
    Set<String> whenConditions =
        Set.of(OrchestrationConstants.PIPELINE_SUCCESS, OrchestrationConstants.PIPELINE_FAILURE,
            OrchestrationConstants.STAGE_SUCCESS, OrchestrationConstants.STAGE_FAILURE, OrchestrationConstants.ALWAYS);
    return whenConditions.contains(expression);
  }

  private boolean ignoreSensitiveExpressionValue(Object value) {
    if (value instanceof String) {
      Set<String> secretExpressions = Set.of("${ngSecretManager", "${sweepingOutputSecrets");
      for (String secretExpression : secretExpressions) {
        if (((String) value).contains(secretExpression)) {
          return true;
        }
      }
      return false;
    }
    return false;
  }

  private boolean expressionValueSizeAboveLimits(Object value) {
    if (value instanceof String) {
      return ((String) value).length() > 1000L;
    }
    return false;
  }

  private ExecutionExpressionUsagesEntity getAnExpressionObject(
      String expression, Object expressionValue, Integer count, boolean isError, String parentUniqueId) {
    String fqnUsingLevels = AmbianceUtils.getFQNUsingLevels(ambiance.getLevelsList());

    return ExecutionExpressionUsagesEntity.builder()
        .uuid(UUIDGenerator.generateUuid())
        .planExecutionId(AmbianceUtils.getPlanExecutionIdForExecutionMode(ambiance))
        .nodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .expression(expression)
        .expressionValue(expressionValue)
        .count(count)
        .isError(isError)
        .createdAt(System.currentTimeMillis())
        .metadata(
            ExpressionValueMetadata.builder()
                .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
                .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
                .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
                .pipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
                .nodeFQN(fqnUsingLevels)
                .nodeFQNHash(Hashing.murmur3_32_fixed().hashString(fqnUsingLevels, StandardCharsets.UTF_8).asInt())
                .expressionHash(Hashing.murmur3_32_fixed().hashString(expression, StandardCharsets.UTF_8).asInt())
                .parentUniqueId(parentUniqueId)
                .build())
        .build();
  }
}

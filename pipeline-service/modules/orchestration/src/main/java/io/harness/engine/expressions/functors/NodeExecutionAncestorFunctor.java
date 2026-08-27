/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.functors.type.NodeExecutionEntityType;
import io.harness.engine.expressions.metadata.ExecutionSweepingOutputMetadata;
import io.harness.engine.expressions.metadata.OutcomeMetadata;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.execution.NodeExecution;
import io.harness.expression.HarnessJexlEngine;
import io.harness.expression.LateBindingMap;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Value
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NodeExecutionAncestorFunctor extends LateBindingMap {
  transient NodeExecutionsCache nodeExecutionsCache;
  transient PmsOutcomeService pmsOutcomeService;
  transient PmsSweepingOutputService pmsSweepingOutputService;
  transient NodeExecutionInfoService nodeExecutionInfoService;
  transient Ambiance ambiance;
  transient Set<NodeExecutionEntityType> entityTypes;
  transient Map<String, String> groupAliases;
  transient HarnessJexlEngine harnessJexlEngine;
  transient ExecutionSweepingOutputMetadata outputMetadata;
  transient OutcomeMetadata outcomeMetadata;
  transient PlanExecutionMetadataService planExecutionMetadataService;

  transient ExecutionInputService executionInputService;
  transient boolean isCel;
  transient PlanExecutionService planExecutionService;
  transient MetricService metricService;

  @Override
  public synchronized Object get(Object key) {
    Instant start = Instant.now();
    String result = ExpressionFunctorMetricsHelper.RESULT_MISS;
    try {
      if (!(key instanceof String)) {
        return null;
      }

      NodeExecution startNodeExecution = findStartNodeExecution((String) key);
      Object value = startNodeExecution == null ? null
                                                : NodeExecutionValue.builder()
                                                      .nodeExecutionsCache(nodeExecutionsCache)
                                                      .pmsOutcomeService(pmsOutcomeService)
                                                      .pmsSweepingOutputService(pmsSweepingOutputService)
                                                      .nodeExecutionInfoService(nodeExecutionInfoService)
                                                      .ambiance(ambiance)
                                                      .startNodeExecution(startNodeExecution)
                                                      .entityTypes(entityTypes)
                                                      .harnessJexlEngine(harnessJexlEngine)
                                                      .outcomeMetadata(outcomeMetadata)
                                                      .executionInputService(executionInputService)
                                                      .outputMetadata(outputMetadata)
                                                      .planExecutionMetadataService(planExecutionMetadataService)
                                                      .planExecutionService(planExecutionService)
                                                      .isCel(isCel)
                                                      .build()
                                                      .bind();
      if (value != null) {
        result = ExpressionFunctorMetricsHelper.RESULT_HIT;
      }
      return value;
    } catch (Exception e) {
      result = ExpressionFunctorMetricsHelper.RESULT_ERROR;
      throw e;
    } finally {
      ExpressionFunctorMetricsHelper.recordMetrics(
          metricService, ExpressionFunctorMetricsHelper.FUNCTOR_ANCESTOR, result, start);
    }
  }

  private NodeExecution findStartNodeExecution(String key) {
    if (groupAliases != null && groupAliases.containsKey(key)) {
      return findStartNodeExecutionByGroup(groupAliases.get(key));
    }

    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    if (nodeExecutionId == null) {
      return null;
    }

    int i = ambiance.getLevelsCount() - 1;
    while (i >= 0) {
      Level currentLevel = ambiance.getLevels(i);
      if (!currentLevel.getSkipExpressionChain() && key.equals(currentLevel.getIdentifier())) {
        return nodeExecutionsCache.fetch(currentLevel.getRuntimeId());
      }
      i--;
    }

    return null;
  }

  private NodeExecution findStartNodeExecutionByGroup(String groupName) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    if (nodeExecutionId == null) {
      return null;
    }

    int n = ambiance.getLevelsCount();
    int i = n - 1;
    while (i >= 0) {
      Level currentLevel = ambiance.getLevels(i);
      if (groupName.equals(currentLevel.getGroup())) {
        return nodeExecutionsCache.fetch(currentLevel.getRuntimeId());
      }
      i--;
    }

    return null;
  }

  // This is required for CEL because CEL first calls the containsKey method and only if is true does it call get method
  // where we have our logic. That's why we are returning true here so that it can go to the get method.
  @Override
  public boolean containsKey(Object key) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      return true;
    } else {
      return super.containsKey(key);
    }
  }
}

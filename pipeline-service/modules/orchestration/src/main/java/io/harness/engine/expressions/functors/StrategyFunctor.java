/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.constants.OrchestrationConstants;
import io.harness.expression.LateBindingMap;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.yaml.utils.FunctorUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public class StrategyFunctor extends LateBindingMap {
  Ambiance ambiance;
  NodeExecutionsCache nodeExecutionsCache;
  NodeExecutionInfoService nodeExecutionInfoService;
  MetricService metricService;
  public static final String NODE_KEY = "node";

  public StrategyFunctor(Ambiance ambiance, NodeExecutionsCache nodeExecutionsCache,
      NodeExecutionInfoService nodeExecutionInfoService, MetricService metricService) {
    this.ambiance = ambiance;
    this.nodeExecutionsCache = nodeExecutionsCache;
    this.nodeExecutionInfoService = nodeExecutionInfoService;
    this.metricService = metricService;
  }

  @Override
  public synchronized Object get(Object key) {
    Instant start = Instant.now();
    String result = ExpressionFunctorMetricsHelper.RESULT_MISS;
    try {
      Object value = FunctorUtils.fetchFirst(
          Arrays.asList(this::getCurrentStatus, this::getStrategyNodeForCurrentStatus, this::getStrategyParams),
          (String) key);
      if (value != null) {
        result = ExpressionFunctorMetricsHelper.RESULT_HIT;
      }
      return value;
    } catch (Exception e) {
      result = ExpressionFunctorMetricsHelper.RESULT_ERROR;
      throw e;
    } finally {
      ExpressionFunctorMetricsHelper.recordMetrics(
          metricService, ExpressionFunctorMetricsHelper.FUNCTOR_STRATEGY, result, start);
    }
  }

  public Optional<Object> getStrategyParams(String key) {
    List<Level> levelsWithStrategyMetadata =
        ambiance.getLevelsList().stream().filter(AmbianceUtils::hasStrategyMetadata).collect(Collectors.toList());
    Map<String, Object> map = nodeExecutionInfoService.fetchStrategyObjectMap(levelsWithStrategyMetadata);
    return Optional.of(map.get(key));
  }

  private Optional<Object> getCurrentStatus(String key) {
    if (!OrchestrationConstants.CURRENT_STATUS.equals(key)) {
      return Optional.empty();
    }
    return Optional.of(StrategyNodeFunctor.builder()
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .ambiance(ambiance)
                           .build()
                           .getCurrentStatus());
  }
  private Optional<Object> getStrategyNodeForCurrentStatus(String key) {
    if (!NODE_KEY.equals(key)) {
      return Optional.empty();
    }
    return Optional.of(StrategyNodeFunctor.builder()
                           .nodeExecutionInfoService(nodeExecutionInfoService)
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .ambiance(ambiance)
                           .build());
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

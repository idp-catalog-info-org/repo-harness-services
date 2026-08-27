/*
 * Copyright 2020 Harness Inc. All rights reserved.
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
import io.harness.expression.LateBindingValue;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Value
@Builder
public class NodeExecutionChildFunctor implements LateBindingValue {
  NodeExecutionsCache nodeExecutionsCache;
  PmsOutcomeService pmsOutcomeService;
  PlanExecutionMetadataService planExecutionMetadataService;
  PmsSweepingOutputService pmsSweepingOutputService;
  NodeExecutionInfoService nodeExecutionInfoService;
  Ambiance ambiance;
  Set<NodeExecutionEntityType> entityTypes;
  HarnessJexlEngine harnessJexlEngine;
  ExecutionSweepingOutputMetadata outputMetadata;
  OutcomeMetadata outcomeMetadata;
  ExecutionInputService executionInputService;
  MetricService metricService;
  PlanExecutionService planExecutionService;
  boolean isCel;

  @Override
  public Object bind() {
    Instant start = Instant.now();
    String result = ExpressionFunctorMetricsHelper.RESULT_MISS;
    try {
      String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
      if (nodeExecutionId == null) {
        return null;
      }

      NodeExecution nodeExecution = nodeExecutionsCache.fetch(nodeExecutionId);
      if (nodeExecution == null) {
        return null;
      }

      Object value = NodeExecutionValue.builder()
                         .nodeExecutionsCache(nodeExecutionsCache)
                         .pmsOutcomeService(pmsOutcomeService)
                         .pmsSweepingOutputService(pmsSweepingOutputService)
                         .nodeExecutionInfoService(nodeExecutionInfoService)
                         .ambiance(ambiance)
                         .startNodeExecution(nodeExecution)
                         .entityTypes(entityTypes)
                         .harnessJexlEngine(harnessJexlEngine)
                         .executionInputService(executionInputService)
                         .outcomeMetadata(outcomeMetadata)
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
          metricService, ExpressionFunctorMetricsHelper.FUNCTOR_CHILD, result, start);
    }
  }
}

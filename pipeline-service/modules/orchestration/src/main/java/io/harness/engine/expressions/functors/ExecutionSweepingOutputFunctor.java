/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.metadata.ExecutionSweepingOutputMetadata;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.engine.pms.data.SweepingOutputException;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.execution.NodeExecution;
import io.harness.expression.LateBindingMap;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.yaml.HarnessYamlVersion;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Builder
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class ExecutionSweepingOutputFunctor extends LateBindingMap {
  transient PmsSweepingOutputService pmsSweepingOutputService;
  transient Ambiance ambiance;
  transient ExecutionSweepingOutputMetadata outputMetadata;
  transient NodeExecutionsCache nodeExecutionsCache;
  transient MetricService metricService;

  private static final List<String> KEYS_TO_BE_LOGGED =
      List.of("artifacts", "configFiles", "manifests", "hooks", "gitopsEnvOutcome");

  @Override
  public synchronized Object get(Object key) {
    Instant start = Instant.now();
    String result = ExpressionFunctorMetricsHelper.RESULT_MISS;
    try {
      if (!isNull(key) && KEYS_TO_BE_LOGGED.contains((String) key)) {
        log.warn("Unexpected key {} used in ExecutionSweepingOutputFunctor", key);
      }

      if (EmptyPredicate.isNotEmpty((String) key)
          && (outputMetadata.getExistingOutputNames().contains((String) key)
              || outputMetadata.getOutputNamesForRollbackMode().contains((String) key))) {
        Object value = resolvePmsSweepingOutputService(key);
        if (value != null) {
          result = ExpressionFunctorMetricsHelper.RESULT_HIT;
        }
        return value;
      }
      throw new SweepingOutputException(format("Could not resolve sweeping output with name '%s'", key));
    } catch (Exception e) {
      result = ExpressionFunctorMetricsHelper.RESULT_ERROR;
      throw e;
    } finally {
      ExpressionFunctorMetricsHelper.recordMetrics(
          metricService, ExpressionFunctorMetricsHelper.FUNCTOR_SWEEPING_OUTPUT, result, start);
    }
  }

  private Object resolvePmsSweepingOutputService(Object key) {
    try {
      String json = pmsSweepingOutputService.resolve(ambiance, RefObjectUtils.getSweepingOutputRefObject((String) key));
      return json == null ? getSweepingOutputFromOriginalExecution(key)
                          : NodeExecutionUtils.extractAndProcessObject(json);
    } catch (SweepingOutputException ex) {
      return getSweepingOutputFromOriginalExecution(key);
    }
  }

  private Object getSweepingOutputFromOriginalExecution(Object key) {
    if (AmbianceUtils.isRollbackModeExecution(ambiance)) {
      Ambiance originalExecutionAmbiance = getOriginalExecutionAmbiance();
      if (originalExecutionAmbiance != null) {
        RawOptionalSweepingOutput rawOptionalSweepingOutput = pmsSweepingOutputService.resolveOptional(
            originalExecutionAmbiance, RefObjectUtils.getSweepingOutputRefObject((String) key));
        if (rawOptionalSweepingOutput.isFound()) {
          String json = rawOptionalSweepingOutput.getOutput();
          return NodeExecutionUtils.extractAndProcessObject(json);
        }
      }
    }
    return null;
  }

  private Ambiance getOriginalExecutionAmbiance() {
    Level lastIdentityNodeTypeLevel = getLastIdentityNodeTypeLevel();
    if (lastIdentityNodeTypeLevel != null && EmptyPredicate.isNotEmpty(lastIdentityNodeTypeLevel.getRuntimeId())) {
      String runTimeId = lastIdentityNodeTypeLevel.getRuntimeId();
      NodeExecution nodeExecution = nodeExecutionsCache.fetch(runTimeId);
      if (nodeExecution != null && nodeExecution.getOriginalNodeExecutionId() != null) {
        NodeExecution originalNodeExecution = nodeExecutionsCache.getWithFieldsIncluded(
            nodeExecution.getOriginalNodeExecutionId(), NodeProjectionUtils.withAmbiance);
        return nodeExecutionsCache.getAmbiance(originalNodeExecution.getUuid());
      }
    }
    return null;
  }

  private Level getLastIdentityNodeTypeLevel() {
    List<Level> levels = ambiance.getLevelsList();
    if (EmptyPredicate.isNotEmpty(levels)) {
      for (int i = levels.size() - 1; i >= 0; i--) {
        Level level = levels.get(i);
        if (level.getNodeType().equals("IDENTITY_PLAN_NODE")) {
          return level;
        }
      }
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

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.expressions.metadata.OutcomeMetadata;
import io.harness.engine.pms.data.OutcomeException;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.expression.LateBindingMap;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.yaml.HarnessYamlVersion;

import java.time.Instant;
import lombok.Builder;
import lombok.EqualsAndHashCode;

@OwnedBy(CDC)
@Builder
@EqualsAndHashCode(callSuper = true)
public class OutcomeFunctor extends LateBindingMap {
  transient PmsOutcomeService pmsOutcomeService;
  transient Ambiance ambiance;
  transient OutcomeMetadata outcomeMetadata;
  transient MetricService metricService;

  @Override
  public synchronized Object get(Object key) {
    Instant start = Instant.now();
    String result = ExpressionFunctorMetricsHelper.RESULT_MISS;
    try {
      if (EmptyPredicate.isNotEmpty((String) key) && outcomeMetadata.existsOutcomeName((String) key)) {
        String resolveJson = pmsOutcomeService.resolve(ambiance, RefObjectUtils.getOutcomeRefObject((String) key));
        Object value = resolveJson == null ? null : NodeExecutionUtils.extractAndProcessObject(resolveJson);
        if (value != null) {
          result = ExpressionFunctorMetricsHelper.RESULT_HIT;
        }
        return value;
      }
      throw new OutcomeException(String.format("Could not resolve outcome with name %s", key));
    } catch (Exception e) {
      result = ExpressionFunctorMetricsHelper.RESULT_ERROR;
      throw e;
    } finally {
      ExpressionFunctorMetricsHelper.recordMetrics(
          metricService, ExpressionFunctorMetricsHelper.FUNCTOR_OUTCOME, result, start);
    }
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

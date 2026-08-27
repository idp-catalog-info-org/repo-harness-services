/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.eventlistener.evaluation;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.HeaderConfig;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.ngtriggers.expressions.functors.payload.PayloadFunctor;

import java.util.List;
import java.util.stream.Collectors;

public class EventListenerStepInstanceExpressionEvaluator extends EngineExpressionEvaluator {
  private final String payload;
  private final List<HeaderConfig> headerConfigs;

  public EventListenerStepInstanceExpressionEvaluator(String payload, List<HeaderConfig> headerConfigs) {
    super(null);
    this.payload = payload;
    this.headerConfigs = headerConfigs;
  }

  @Override
  protected void initialize() {
    super.initialize();
    if (isNotEmpty(payload)) {
      addToContext("event.payload", new PayloadFunctor(payload));
    }
    if (headerConfigs != null) {
      addToContext("event.header",
          headerConfigs.stream().collect(
              Collectors.toMap(HeaderConfig::getKey, hc -> String.join(",", hc.getValues()))));
    }
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2024/10/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.expression.EngineExpressionEvaluator.hasExpressions;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.celcustomfunctor.WithResolutionCheck;
import io.harness.expression.functors.ExpressionFunctor;

import lombok.Builder;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, components = HarnessModuleComponent.CDS_PIPELINE, unitCoverageRequired = false)
@Builder
public class ExpressionResolvedCheckFunctor implements ExpressionFunctor, WithResolutionCheck {
  @Override
  public Object isUnresolved(Object value) {
    return !(Boolean) isResolved(value);
  }

  @Override
  public synchronized Object isResolved(Object value) {
    return value != null && !(value instanceof String && hasExpressions((String) value));
  }
}

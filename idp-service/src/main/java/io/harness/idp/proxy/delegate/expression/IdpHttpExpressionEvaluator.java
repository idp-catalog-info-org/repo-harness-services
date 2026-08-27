/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate.expression;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.EngineExpressionEvaluator;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IdpHttpExpressionEvaluator extends EngineExpressionEvaluator {
  int expressionFunctorToken;
  public IdpHttpExpressionEvaluator(int expressionFunctorToken) {
    super(null);
    this.expressionFunctorToken = expressionFunctorToken;
  }

  @Override
  protected void initialize() {
    super.initialize();
    this.addToContext("secrets", new IdpSecretFunctor(this.expressionFunctorToken));
  }
}

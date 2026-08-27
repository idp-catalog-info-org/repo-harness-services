/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.expression;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.EngineExpressionEvaluator;

import java.util.Map;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class IdpVariableExpressionEvaluator extends EngineExpressionEvaluator {
  final Object systemVariables;
  final Map<String, String> accountLevelVariables;

  public IdpVariableExpressionEvaluator(Object systemVariables, Map<String, String> accountLevelVariables) {
    super(null);
    this.systemVariables = systemVariables;
    this.accountLevelVariables = accountLevelVariables;
  }

  @Override
  protected void initialize() {
    super.initialize();
    addToContext("account", systemVariables);
    addToContext("variable", Map.of("account", accountLevelVariables));
  }
}

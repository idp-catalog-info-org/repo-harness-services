/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.engine.expressions.VariableFunctorProcessor;
import io.harness.ng.core.variable.expressions.functors.VariableFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;

// This implementation implements VariableFunctorProcessor from the ShellScriptYamlExpressionEvaluator level and reuses
// the existing VariableFunctor from the same 120-ng-manager level.

@OwnedBy(PL)
public class VariableFunctorAdapter implements VariableFunctorProcessor {
  @Inject VariableFunctor variableFunctor;

  @Override
  public Object get(ScopeInfo scopeInfo, String identifier) {
    return variableFunctor.get(getAmbianceFromScopeInfo(scopeInfo), identifier);
  }

  @VisibleForTesting
  protected Ambiance getAmbianceFromScopeInfo(ScopeInfo scopeInfo) {
    Map<String, String> scopeMap = new HashMap<>();
    scopeMap.put(SetupAbstractionKeys.accountId, scopeInfo.getAccountIdentifier());
    if (isNotEmpty(scopeInfo.getOrgIdentifier())) {
      scopeMap.put(SetupAbstractionKeys.orgIdentifier, scopeInfo.getOrgIdentifier());
    }
    if (isNotEmpty(scopeInfo.getProjectIdentifier())) {
      scopeMap.put(SetupAbstractionKeys.projectIdentifier, scopeInfo.getProjectIdentifier());
    }
    return Ambiance.newBuilder().putAllSetupAbstractions(scopeMap).build();
  }
}
/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa.entities.variable;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.variable.dto.VariableDTO;

@OwnedBy(PL)

public interface VariableOpaService {
  GovernanceMetadata evaluatePoliciesWithEntity(
      ScopeInfo scopeInfo, VariableDTO variableDTO, String action, String identifier);
}

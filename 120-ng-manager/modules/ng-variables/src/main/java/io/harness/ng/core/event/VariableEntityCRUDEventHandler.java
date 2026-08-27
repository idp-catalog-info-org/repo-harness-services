/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.services.VariableService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
public class VariableEntityCRUDEventHandler {
  private final VariableService variableService;

  @Inject
  public VariableEntityCRUDEventHandler(VariableService variableService) {
    this.variableService = variableService;
  }

  public boolean deleteAssociatedVariables(ScopeInfo scopeInfo) {
    List<String> variableIdentifiersList = fetchAllVariablesInGivenScope(scopeInfo);
    variableService.deleteBatch(scopeInfo, variableIdentifiersList);
    return true;
  }

  private List<String> fetchAllVariablesInGivenScope(ScopeInfo scopeInfo) {
    List<VariableDTO> variablesList = variableService.list(scopeInfo);
    return variablesList.stream().map(VariableDTO::getIdentifier).collect(Collectors.toList());
  }
}

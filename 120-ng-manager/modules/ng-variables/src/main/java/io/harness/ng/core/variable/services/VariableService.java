/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.services;

import io.harness.beans.ScopeInfo;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableListRequestDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface VariableService {
  VariableDTO create(ScopeInfo scopeInfo, VariableDTO variableDTO);
  PageResponse<VariableResponseDTO> list(ScopeInfo scopeInfo, VariableListRequestDTO variableListRequestDTO,
      String searchTerm, boolean includeVariablesFromEverySubScope, Pageable pageable);
  List<VariableDTO> list(ScopeInfo scopeInfo);
  Optional<VariableResponseDTO> get(ScopeInfo scopeInfo, String identifier);
  VariableDTO update(ScopeInfo scopeInfo, VariableDTO variableDTO);
  boolean delete(ScopeInfo scopeInfo, String variableIdentifier);
  void deleteBatch(ScopeInfo scopeInfo, List<String> variableIdentifiersList);

  List<String> getExpressions(ScopeInfo scopeInfo);
  Long countVariables(String accountIdentifier);
  List<VariableResponseDTO> getPermitted(List<VariableResponseDTO> variables, ScopeInfo scopeInfo);
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.mappers;

import io.harness.beans.ScopeInfo;
import io.harness.exception.UnsupportedOperationException;
import io.harness.ng.core.variable.dto.StringVariableConfigDTO;
import io.harness.ng.core.variable.dto.VariableConfigDTO;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.ng.core.variable.entity.StringVariable;
import io.harness.ng.core.variable.entity.Variable;

public class VariableMapper {
  public Variable toVariable(ScopeInfo scopeInfo, VariableDTO variableDTO) {
    Variable variable = getVariableByType(variableDTO);
    variable.setIdentifier(variableDTO.getIdentifier());
    variable.setAccountIdentifier(scopeInfo.getAccountIdentifier());
    variable.setOrgIdentifier(scopeInfo.getOrgIdentifier());
    variable.setProjectIdentifier(scopeInfo.getProjectIdentifier());
    variable.setName(variableDTO.getName());
    variable.setDescription(variableDTO.getDescription());
    variable.setType(variableDTO.getType());
    variable.setValueType(variableDTO.getVariableConfig().getValueType());
    variable.setParentUniqueId(scopeInfo.getUniqueId());
    return variable;
  }

  private Variable getVariableByType(VariableDTO variableDTO) {
    switch (variableDTO.getType()) {
      case STRING:
        return StringVariableDTOtoEntityMapper.toVariableEntity(
            (StringVariableConfigDTO) variableDTO.getVariableConfig());
      default:
        throw new UnsupportedOperationException(
            String.format("Variable of type %s is not supported", variableDTO.getType()));
    }
  }

  public VariableDTO writeDTO(ScopeInfo scopeInfo, Variable variable) {
    VariableConfigDTO variableConfigDTO = getVariableConfigDTO(variable);
    return VariableDTO.builder()
        .identifier(variable.getIdentifier())
        .name(variable.getName())
        .description(variable.getDescription())
        .orgIdentifier(scopeInfo.getOrgIdentifier())
        .projectIdentifier(scopeInfo.getProjectIdentifier())
        .type(variable.getType())
        .variableConfig(variableConfigDTO)
        .uniqueId(variable.getUniqueId())
        .parentUniqueId(scopeInfo.getUniqueId())
        .build();
  }

  private VariableConfigDTO getVariableConfigDTO(Variable variable) {
    switch (variable.getType()) {
      case STRING:
        return StringVariableEntityToDTOMapper.createVariableDTO((StringVariable) variable);
      default:
        throw new UnsupportedOperationException(
            String.format("Variable of type %s is not supported", variable.getType()));
    }
  }

  public VariableResponseDTO toResponseWrapper(ScopeInfo scopeInfo, Variable variable) {
    return VariableResponseDTO.builder()
        .variable(writeDTO(scopeInfo, variable))
        .createdAt(variable.getCreatedAt())
        .lastModifiedAt(variable.getLastModifiedAt())
        .build();
  }

  public VariableResponseDTO toResponseWrapper(VariableDTO variableDTO) {
    return VariableResponseDTO.builder().variable(variableDTO).build();
  }
}

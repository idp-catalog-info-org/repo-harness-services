/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.variable.VariablePermissions.VARIABLE_DELETE_PERMISSION;
import static io.harness.ng.core.variable.VariablePermissions.VARIABLE_EDIT_PERMISSION;
import static io.harness.ng.core.variable.VariablePermissions.VARIABLE_RESOURCE_TYPE;
import static io.harness.ng.core.variable.VariablePermissions.VARIABLE_VIEW_PERMISSION;
import static io.harness.utils.PageUtils.getPageRequest;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.SortOrder;
import io.harness.engine.expressions.VariableFunctorProcessor;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableListRequestDTO;
import io.harness.ng.core.variable.dto.VariableRequestDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.ng.core.variable.entity.Variable.VariableKeys;
import io.harness.ng.core.variable.mappers.VariableMapper;
import io.harness.ng.core.variable.services.VariableService;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class VariableResourceImpl implements VariableResource {
  private final VariableService variableService;
  private final VariableMapper variableMapper;
  private final AccessControlClient accessControlClient;
  private final ScopeInfoService scopeResolverService;
  private final VariableFunctorProcessor variableFunctorProcessor;

  @Override
  public ResponseDTO<VariableResponseDTO> get(String identifier, @AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(VARIABLE_RESOURCE_TYPE, identifier), VARIABLE_VIEW_PERMISSION);
    Optional<VariableResponseDTO> variable = variableService.get(scopeInfo, identifier);
    if (!variable.isPresent()) {
      throw new NotFoundException(String.format("Variable with identifier [%s] in project [%s] and org [%s] not found",
          identifier, projectIdentifier, orgIdentifier));
    }
    return ResponseDTO.newResponse(variable.get());
  }

  @Override
  public ResponseDTO<PageResponse<VariableResponseDTO>> listV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, boolean includeVariablesFromEverySubScope,
      VariableListRequestDTO variableListRequestDTO, PageRequest pageRequest, ScopeInfo scopeInfo) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(VariableKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(List.of(order));
    }
    boolean isPrincipalHavingViewPermissionOnAllVariables =
        accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(VARIABLE_RESOURCE_TYPE, null), VARIABLE_VIEW_PERMISSION);
    if (isPrincipalHavingViewPermissionOnAllVariables && !includeVariablesFromEverySubScope) {
      return ResponseDTO.newResponse(
          variableService.list(scopeInfo, variableListRequestDTO, searchTerm, false, getPageRequest(pageRequest)));
    }
    Pageable pageable = Pageable.ofSize(90000);
    PageResponse<VariableResponseDTO> allVariables =
        variableService.list(scopeInfo, null, searchTerm, includeVariablesFromEverySubScope, pageable);
    List<VariableResponseDTO> permittedVariables = variableService.getPermitted(allVariables.getContent(), scopeInfo);

    List<String> permittedVariableIds =
        permittedVariables.stream()
            .map(variableResponseDTO -> variableResponseDTO.getVariable().getIdentifier())
            .collect(Collectors.toList());
    return ResponseDTO.newResponse(
        variableService.list(scopeInfo, VariableListRequestDTO.builder().identifiers(permittedVariableIds).build(),
            searchTerm, includeVariablesFromEverySubScope, getPageRequest(pageRequest)));
  }

  @Override
  public ResponseDTO<VariableResponseDTO> create(String accountIdentifier, VariableRequestDTO variableRequestDTO) {
    if (variableRequestDTO.getVariable().getOrgIdentifier() == null
        && variableRequestDTO.getVariable().getProjectIdentifier() != null) {
      throw new InvalidRequestException(String.format(
          "Project %s specified without the org Identifier", variableRequestDTO.getVariable().getProjectIdentifier()));
    }
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier,
        variableRequestDTO.getVariable().getOrgIdentifier(), variableRequestDTO.getVariable().getProjectIdentifier());

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                                  scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(VARIABLE_RESOURCE_TYPE, null), VARIABLE_EDIT_PERMISSION);

    VariableDTO createdVariable = variableService.create(scopeInfo, variableRequestDTO.getVariable());
    return ResponseDTO.newResponse(variableMapper.toResponseWrapper(createdVariable));
  }

  @Override
  public ResponseDTO<PageResponse<VariableResponseDTO>> list(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, String searchTerm,
      boolean includeVariablesFromEverySubScope, PageRequest pageRequest, ScopeInfo scopeInfo) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(VariableKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(List.of(order));
    }
    boolean isPrincipalHavingViewPermissionOnAllVariables =
        accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(VARIABLE_RESOURCE_TYPE, null), VARIABLE_VIEW_PERMISSION);
    if (isPrincipalHavingViewPermissionOnAllVariables && !includeVariablesFromEverySubScope) {
      return ResponseDTO.newResponse(
          variableService.list(scopeInfo, null, searchTerm, false, getPageRequest(pageRequest)));
    }
    Pageable pageable = Pageable.ofSize(90000);
    PageResponse<VariableResponseDTO> allVariables =
        variableService.list(scopeInfo, null, searchTerm, includeVariablesFromEverySubScope, pageable);
    List<VariableResponseDTO> permittedVariables = variableService.getPermitted(allVariables.getContent(), scopeInfo);

    List<String> permittedVariableIds =
        permittedVariables.stream()
            .map(variableResponseDTO -> variableResponseDTO.getVariable().getIdentifier())
            .collect(Collectors.toList());
    return ResponseDTO.newResponse(
        variableService.list(scopeInfo, VariableListRequestDTO.builder().identifiers(permittedVariableIds).build(),
            searchTerm, includeVariablesFromEverySubScope, getPageRequest(pageRequest)));
  }

  @Override
  public ResponseDTO<VariableResponseDTO> update(String accountIdentifier, VariableRequestDTO variableRequestDTO) {
    if (variableRequestDTO.getVariable().getOrgIdentifier() == null
        && variableRequestDTO.getVariable().getProjectIdentifier() != null) {
      throw new InvalidRequestException(String.format(
          "Project %s specified without the org Identifier", variableRequestDTO.getVariable().getProjectIdentifier()));
    }
    ScopeInfo scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier,
        variableRequestDTO.getVariable().getOrgIdentifier(), variableRequestDTO.getVariable().getProjectIdentifier());
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, variableRequestDTO.getVariable().getOrgIdentifier(),
            variableRequestDTO.getVariable().getProjectIdentifier()),
        Resource.of(VARIABLE_RESOURCE_TYPE, variableRequestDTO.getVariable().getIdentifier()),
        VARIABLE_EDIT_PERMISSION);
    VariableDTO updatedVariable = variableService.update(scopeInfo, variableRequestDTO.getVariable());
    return ResponseDTO.newResponse(variableMapper.toResponseWrapper(updatedVariable));
  }

  @Override
  public ResponseDTO<Boolean> delete(@AccountIdentifier String accountIdentifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, String variableIdentifier, ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(VARIABLE_RESOURCE_TYPE, variableIdentifier), VARIABLE_DELETE_PERMISSION);
    boolean deleted = variableService.delete(scopeInfo, variableIdentifier);
    return ResponseDTO.newResponse(deleted);
  }

  @Override
  public ResponseDTO<List<String>> expressions(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    return ResponseDTO.newResponse(variableService.getExpressions(scopeInfo));
  }

  @Override
  public ResponseDTO<Object> getVariableFunctorValue(String identifier, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo) {
    // This internal API exposes VariableFunctor#get logic for remote services
    // Delegates to VariableFunctorProcessor which handles all the logic including:
    Object result = variableFunctorProcessor.get(scopeInfo, identifier);
    return ResponseDTO.newResponse(result);
  }
}

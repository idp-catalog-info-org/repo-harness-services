/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.enforcement.constants.FeatureRestrictionName.MULTIPLE_VARIABLES;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ng.core.variable.VariablePermissions.VARIABLE_RESOURCE_TYPE;
import static io.harness.ng.core.variable.VariablePermissions.VARIABLE_VIEW_PERMISSION;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.data.structure.EmptyPredicate;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.EntityScopeInfo;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.events.VariableCreateEvent;
import io.harness.ng.core.events.VariableDeleteEvent;
import io.harness.ng.core.events.VariableUpdateEvent;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.variable.dto.VariableConfigDTO;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableListRequestDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.ng.core.variable.entity.Variable;
import io.harness.ng.core.variable.entity.Variable.VariableKeys;
import io.harness.ng.core.variable.mappers.VariableMapper;
import io.harness.ng.core.variable.services.VariableService;
import io.harness.ng.opa.entities.variable.VariableOpaService;
import io.harness.opaclient.model.OpaConstants;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.variable.spring.VariableRepository;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Slf4j
public class VariableServiceImpl implements VariableService {
  private final VariableRepository variableRepository;
  private final VariableMapper variableMapper;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;
  private final ProjectService projectService;
  private final OrganizationService organizationService;
  private final ScopeInfoService scopeInfoService;
  private final AccessControlClient accessControlClient;
  private final VariableOpaService variableOpaService;
  private final NGFeatureFlagHelperService featureFlagHelperService;

  @Inject
  public VariableServiceImpl(VariableRepository variableRepository, VariableMapper variableMapper,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService,
      ProjectService projectService, OrganizationService organizationService, ScopeInfoService scopeInfoService,
      AccessControlClient accessControlClient, VariableOpaService variableOpaService,
      NGFeatureFlagHelperService featureFlagHelperService) {
    this.variableRepository = variableRepository;
    this.variableMapper = variableMapper;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
    this.projectService = projectService;
    this.organizationService = organizationService;
    this.scopeInfoService = scopeInfoService;
    this.accessControlClient = accessControlClient;
    this.variableOpaService = variableOpaService;
    this.featureFlagHelperService = featureFlagHelperService;
  }

  @Override
  @FeatureRestrictionCheck(MULTIPLE_VARIABLES)
  public VariableDTO create(ScopeInfo scopeInfo, VariableDTO variableDTO) {
    if (null == variableDTO.getVariableConfig()) {
      throw new InvalidRequestException("Variable config cannot be null");
    }
    variableDTO.getVariableConfig().validate();
    try {
      Variable variable = variableMapper.toVariable(scopeInfo, variableDTO);

      VariableDTO opaValidationResponse = evaluateVariableForOPAPolicies(scopeInfo, variableDTO);
      if (opaValidationResponse.getGovernanceMetadata() != null
          && OpaConstants.OPA_STATUS_ERROR.equals(opaValidationResponse.getGovernanceMetadata().getStatus())) {
        return opaValidationResponse;
      }

      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        Variable savedVariable = variableRepository.save(variable);
        VariableDTO savedVariableDTO = variableMapper.writeDTO(scopeInfo, savedVariable);
        outboxService.save(new VariableCreateEvent(scopeInfo.getAccountIdentifier(), savedVariableDTO));
        return savedVariableDTO;
      }));
    } catch (DuplicateKeyException de) {
      throw new DuplicateFieldException(
          String.format("Variable with identifier [%s] already exists in this scope.", variableDTO.getIdentifier()));
    }
  }

  private VariableDTO evaluateVariableForOPAPolicies(ScopeInfo scopeInfo, VariableDTO variableDTO) {
    if (featureFlagHelperService.isEnabled(scopeInfo.getAccountIdentifier(), FeatureName.PL_ENABLE_OPA_FOR_VARIABLES)) {
      GovernanceMetadata governanceMetadata = variableOpaService.evaluatePoliciesWithEntity(
          scopeInfo, variableDTO, OpaConstants.OPA_EVALUATION_ACTION_SAVE, variableDTO.getIdentifier());
      VariableDTO opaErrorResponse = VariableDTO.builder().build();
      opaErrorResponse.setGovernanceMetadata(governanceMetadata);
      return opaErrorResponse;
    }
    return variableDTO;
  }

  @Override
  public PageResponse<VariableResponseDTO> list(ScopeInfo scopeInfo, VariableListRequestDTO variableListRequestDTO,
      String searchTerm, boolean includeVariablesFromEverySubScope, Pageable pageable) {
    Criteria criteria =
        getCriteriaForVariableList(scopeInfo, searchTerm, includeVariablesFromEverySubScope, variableListRequestDTO);
    Page<Variable> variables = variableRepository.findAllWithCollation(criteria, pageable);
    Map<String, Optional<ScopeInfo>> scopeInfoList;
    Set<String> uniqueIds = variables.stream().map(Variable::getParentUniqueId).collect(Collectors.toSet());
    if (uniqueIds.size() == 1 && scopeInfo.getUniqueId().equals(uniqueIds.stream().findFirst().get())) {
      scopeInfoList = Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    } else {
      scopeInfoList = scopeInfoService.getScopeInfo(scopeInfo.getAccountIdentifier(), uniqueIds);
    }
    return PageUtils.getNGPageResponse(variables,
        variables.getContent()
            .stream()
            .map(variable -> {
              ScopeInfo currentScopeInfo = scopeInfoList.get(variable.getParentUniqueId()).orElseThrow();
              return variableMapper.toResponseWrapper(currentScopeInfo, variable);
            })
            .collect(Collectors.toList()));
  }

  private Criteria getCriteriaForVariableList(ScopeInfo scopeInfo, String searchTerm,
      boolean includeVariablesFromEverySubScope, VariableListRequestDTO variableListRequestDTO) {
    Criteria criteria = Criteria.where(VariableKeys.accountIdentifier).is(scopeInfo.getAccountIdentifier());
    if (!includeVariablesFromEverySubScope) {
      criteria.and(VariableKeys.parentUniqueId).is(scopeInfo.getUniqueId());
    } else {
      if (ScopeLevel.ORGANIZATION.equals(scopeInfo.getScopeType())) {
        Criteria orgCriteria = Criteria.where(VariableKeys.parentUniqueId).is(scopeInfo.getUniqueId());

        Criteria projectCriteria = Criteria.where(ProjectKeys.parentUniqueId)
                                       .is(scopeInfo.getUniqueId())
                                       .and(ProjectKeys.deleted)
                                       .ne(Boolean.TRUE);
        List<Project> childrenProjects = projectService.list(projectCriteria);

        List<String> childrenProjectUniqueIds =
            childrenProjects.stream().map(Project::getUniqueId).collect(Collectors.toList());
        if (EmptyPredicate.isNotEmpty(childrenProjects)) {
          Criteria childrenProjectCriteria = Criteria.where(VariableKeys.parentUniqueId).in(childrenProjectUniqueIds);
          criteria.orOperator(childrenProjectCriteria, orgCriteria);
        } else {
          criteria.orOperator(orgCriteria);
        }
      } else if (ScopeLevel.PROJECT.equals(scopeInfo.getScopeType())) {
        criteria.and(VariableKeys.parentUniqueId).is(scopeInfo.getUniqueId());
      }
    }
    if (!StringUtils.isEmpty(searchTerm)) {
      criteria = criteria.orOperator(
          Criteria.where(VariableKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          Criteria.where(VariableKeys.identifier)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }

    if (variableListRequestDTO != null && !isEmpty(variableListRequestDTO.getIdentifiers())) {
      criteria.and(VariableKeys.identifier).in(variableListRequestDTO.getIdentifiers());
    }
    return criteria;
  }

  @Override
  public List<VariableDTO> list(ScopeInfo scopeInfo) {
    List<Variable> variables = variableRepository.findAllByAccountIdentifierAndParentUniqueId(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId());
    return variables.stream().map(var -> variableMapper.writeDTO(scopeInfo, var)).collect(Collectors.toList());
  }

  @Override
  public Optional<VariableResponseDTO> get(ScopeInfo scopeInfo, String identifier) {
    Optional<Variable> variable = variableRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), identifier);
    return variable.map(var -> variableMapper.toResponseWrapper(scopeInfo, var));
  }

  @Override
  public VariableDTO update(ScopeInfo scopeInfo, VariableDTO variableDTO) {
    VariableConfigDTO variableConfigDTO = variableDTO.getVariableConfig();
    variableConfigDTO.validate();
    Optional<Variable> existingVariable = variableRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), variableDTO.getIdentifier());
    validateTheUpdateRequestIsValid(scopeInfo.getAccountIdentifier(), variableDTO, existingVariable);

    VariableDTO opaValidationResponse = evaluateVariableForOPAPolicies(scopeInfo, variableDTO);
    if (opaValidationResponse.getGovernanceMetadata() != null
        && OpaConstants.OPA_STATUS_ERROR.equals(opaValidationResponse.getGovernanceMetadata().getStatus())) {
      return opaValidationResponse;
    }

    try {
      Variable newVariable = variableMapper.toVariable(scopeInfo, variableDTO);
      newVariable.setLastModifiedAt(System.currentTimeMillis());
      newVariable.setCreatedAt(existingVariable.get().getCreatedAt());
      newVariable.setId(existingVariable.get().getId());
      newVariable.setUniqueId(existingVariable.get().getUniqueId());
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        Variable updatedVariable = variableRepository.save(newVariable);
        VariableDTO updatedVariableDTO = variableMapper.writeDTO(scopeInfo, updatedVariable);
        outboxService.save(new VariableUpdateEvent(scopeInfo.getAccountIdentifier(), updatedVariableDTO,
            variableMapper.writeDTO(scopeInfo, existingVariable.get())));
        return updatedVariableDTO;
      }));
    } catch (DuplicateKeyException de) {
      throw new DuplicateFieldException(
          String.format(
              "A variable with identifier [%s] and orgIdentifier [%s] and projectIdentifier [%s] already present.",
              variableDTO.getIdentifier(), variableDTO.getOrgIdentifier(), variableDTO.getProjectIdentifier()),
          USER_SRE, de);
    }
  }

  @Override
  public boolean delete(ScopeInfo scopeInfo, String variableIdentifier) {
    Optional<Variable> existingVariable = variableRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), variableIdentifier);
    if (existingVariable.isPresent()) {
      variableRepository.delete(existingVariable.get());
      outboxService.save(new VariableDeleteEvent(
          scopeInfo.getAccountIdentifier(), variableMapper.writeDTO(scopeInfo, existingVariable.get())));
    } else {
      throw new NotFoundException(
          String.format("Variable [%s] Not Found with orgIdentifier- [%s], projectIdentifier- [%s]", variableIdentifier,
              scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()));
    }
    return false;
  }

  @Override
  public void deleteBatch(ScopeInfo scopeInfo, List<String> variableIdentifiersList) {
    for (String variableIdentifier : variableIdentifiersList) {
      try {
        delete(scopeInfo, variableIdentifier);
      } catch (NotFoundException ex) {
        log.error(String.format("Unable to delete Variable. No Variable found with orgIdentifier- [%s], "
                + "projectIdentifier- [%s] and variableIdentifier- [%s]",
            scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), variableIdentifier));
      }
    }
  }

  private Criteria getCriteriaForVariableExpressions(ScopeInfo scopeInfo) {
    Criteria criteria = new Criteria();
    Criteria accountCriteria = Criteria.where(VariableKeys.parentUniqueId).is(scopeInfo.getAccountIdentifier());

    if (ScopeLevel.PROJECT.equals(scopeInfo.getScopeType())) {
      Criteria projectCriteria = Criteria.where(VariableKeys.parentUniqueId).is(scopeInfo.getUniqueId());

      Optional<Project> project = projectService.get(scopeInfo.getUniqueId());
      Criteria orgCriteria = Criteria.where(VariableKeys.parentUniqueId).is(project.orElseThrow().getParentUniqueId());

      criteria.orOperator(projectCriteria, orgCriteria, accountCriteria);
    } else if (ScopeLevel.ORGANIZATION.equals(scopeInfo.getScopeType())) {
      Criteria orgCriteria = Criteria.where(VariableKeys.parentUniqueId).is(scopeInfo.getUniqueId());
      criteria.orOperator(orgCriteria, accountCriteria);
    } else {
      criteria.orOperator(accountCriteria);
    }
    return criteria;
  }

  @Override
  public List<String> getExpressions(ScopeInfo scopeInfo) {
    Criteria criteria = getCriteriaForVariableExpressions(scopeInfo);
    List<Variable> variables = variableRepository.findAll(criteria);
    Map<String, Optional<ScopeInfo>> scopeInfoList;
    Set<String> uniqueIds = variables.stream().map(Variable::getParentUniqueId).collect(Collectors.toSet());
    if (uniqueIds.size() == 1 && scopeInfo.getUniqueId().equals(uniqueIds.stream().findFirst().get())) {
      scopeInfoList = Map.of(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    } else {
      scopeInfoList = scopeInfoService.getScopeInfo(scopeInfo.getAccountIdentifier(), uniqueIds);
    }
    return variables.stream()
        .map(entity -> {
          ScopeInfo currentScopeInfo = scopeInfoList.get(entity.getParentUniqueId()).orElseThrow();
          return entity.getExpression(currentScopeInfo.getScopeType());
        })
        .collect(Collectors.toList());
  }

  @Override
  public Long countVariables(String accountIdentifier) {
    return variableRepository.countByAccountIdentifier(accountIdentifier);
  }

  public void validateTheUpdateRequestIsValid(
      String accountIdentifier, VariableDTO variableDTO, Optional<Variable> existingVariable) {
    if (!existingVariable.isPresent()) {
      throw new NotFoundException(
          String.format("Variable [%s] Not Found with orgIdentifier- [%s], projectIdentifier- [%s]",
              variableDTO.getIdentifier(), variableDTO.getOrgIdentifier(), variableDTO.getProjectIdentifier()));
    }
    validateImmutableFieldsAreNotChanged(variableDTO, existingVariable.get());
  }

  public void validateImmutableFieldsAreNotChanged(VariableDTO variableDTO, Variable existingVariable) {
    if (!Objects.equals(variableDTO.getType(), existingVariable.getType())) {
      throw new InvalidRequestException("Variable Type cannot be changed");
    }
    if (!Objects.equals(variableDTO.getVariableConfig().getValueType(), existingVariable.getValueType())) {
      throw new InvalidRequestException("Variable Value Type cannot be changed");
    }
  }

  public List<VariableResponseDTO> getPermitted(List<VariableResponseDTO> variables, ScopeInfo scopeInfo) {
    Map<EntityScopeInfo, List<VariableResponseDTO>> variablesMap = variables.stream().collect(groupingBy(
        variableResponseDTO -> getEntityScopeInfoFromVariableDTO(variableResponseDTO.getVariable(), scopeInfo)));
    List<PermissionCheckDTO> permissionChecks =
        variables.stream()
            .map(variableResponseDTO
                -> PermissionCheckDTO.builder()
                       .permission(VARIABLE_VIEW_PERMISSION)
                       .resourceIdentifier(variableResponseDTO.getVariable().getIdentifier())
                       .resourceScope(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                           variableResponseDTO.getVariable().getOrgIdentifier(),
                           variableResponseDTO.getVariable().getProjectIdentifier()))
                       .resourceType(VARIABLE_RESOURCE_TYPE)
                       .build())
            .collect(Collectors.toList());
    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);

    List<VariableResponseDTO> permittedVariables = new ArrayList<>();
    for (AccessControlDTO accessControlDTO : accessCheckResponse.getAccessControlList()) {
      if (accessControlDTO.isPermitted()) {
        permittedVariables.add(variablesMap.get(getEntityScopeInfoFromAccessControlDTO(accessControlDTO)).get(0));
      }
    }
    return permittedVariables;
  }

  private static EntityScopeInfo getEntityScopeInfoFromVariableDTO(VariableDTO variableDTO, ScopeInfo scopeInfo) {
    return EntityScopeInfo.builder()
        .accountIdentifier(scopeInfo.getAccountIdentifier())
        .orgIdentifier(isBlank(variableDTO.getOrgIdentifier()) ? null : variableDTO.getOrgIdentifier())
        .projectIdentifier(isBlank(variableDTO.getProjectIdentifier()) ? null : variableDTO.getProjectIdentifier())
        .identifier(variableDTO.getIdentifier())
        .build();
  }

  private static EntityScopeInfo getEntityScopeInfoFromAccessControlDTO(AccessControlDTO accessControlDTO) {
    return EntityScopeInfo.builder()
        .accountIdentifier(accessControlDTO.getResourceScope().getAccountIdentifier())
        .orgIdentifier(isBlank(accessControlDTO.getResourceScope().getOrgIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getOrgIdentifier())
        .projectIdentifier(isBlank(accessControlDTO.getResourceScope().getProjectIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getProjectIdentifier())
        .identifier(accessControlDTO.getResourceIdentifier())
        .build();
  }
}
